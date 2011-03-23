/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.protocol;



import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DataFormatException;

import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class Implement a protocol session using a basic socket and relying on
 * the innate encoding/decoding capabilities of the ReplicationMsg by using the
 * getBytes() and generateMsg() methods of those classes.
 */
public final class SocketSession implements ProtocolSession
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private final Socket socket;
  private final InputStream input;
  private final OutputStream output;
  private final byte[] rcvLengthBuf = new byte[8];

  /**
   * The time the last message published to this session.
   */
  private volatile long lastPublishTime = 0;

  /**
   * The time the last message was received on this session.
   */
  private volatile long lastReceiveTime = 0;

  // Close guarded by closeLock: use a different lock to publish since
  // publishing can block, and we don't want to block while closing failed
  // connections.
  private final Object closeLock = new Object();
  private boolean closeInitiated = false;

  // Publish guarded by publishLock: use a full lock here so that we can
  // optionally publish StopMsg during close.
  private final Lock publishLock = new ReentrantLock();

  // Does not need protecting: updated only during single threaded handshake.
  private short protocolVersion = ProtocolVersion.getCurrentVersion();



  /**
   * Creates a new SocketSession based on the provided socket.
   *
   * @param socket
   *          The Socket on which the SocketSession will be based.
   * @throws IOException
   *           When an IException happens on the socket.
   */
  public SocketSession(final Socket socket) throws IOException
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Creating SocketSession to %s from %s", socket
          .getRemoteSocketAddress().toString(),
          stackTraceToSingleLineString(new Exception()));
    }

    this.socket = socket;
    this.input = socket.getInputStream();
    this.output = socket.getOutputStream();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    synchronized (closeLock)
    {
      if (closeInitiated)
      {
        return;
      }

      closeInitiated = true;
    }

    // Perform close outside of critical section.
    if (debugEnabled())
    {
      TRACER.debugInfo("Closing SocketSession to %s from %s", socket
          .getRemoteSocketAddress().toString(),
          stackTraceToSingleLineString(new Exception()));
    }

    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      // V4 protocol introduces a StopMsg to properly end communications.
      if (publishLock.tryLock())
      {
        try
        {
          publish(new StopMsg());
        }
        catch (final IOException ignored)
        {
          // Ignore errors on close.
        }
        finally
        {
          publishLock.unlock();
        }
      }
    }

    if (socket != null && !socket.isClosed())
    {
      try
      {
        socket.close();
      }
      catch (final IOException ignored)
      {
        // Ignore errors on close.
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean closeInitiated()
  {
    synchronized (closeLock)
    {
      return closeInitiated;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getLastPublishTime()
  {
    return lastPublishTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getLastReceiveTime()
  {
    if (lastReceiveTime == 0)
    {
      return System.currentTimeMillis();
    }
    return lastReceiveTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getReadableRemoteAddress()
  {
    return socket.getRemoteSocketAddress().toString();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getRemoteAddress()
  {
    return socket.getInetAddress().getHostAddress();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEncrypted()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(final ReplicationMsg msg) throws IOException
  {
    publish(msg, ProtocolVersion.getCurrentVersion());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(final ReplicationMsg msg,
      final short reqProtocolVersion) throws IOException
  {
    final byte[] buffer = msg.getBytes(reqProtocolVersion);
    final String str = String.format("%08x", buffer.length);
    final byte[] sendLengthBuf = str.getBytes();

    publishLock.lock();
    try
    {
      output.write(sendLengthBuf);
      output.write(buffer);
      output.flush();
    }
    finally
    {
      publishLock.unlock();
    }

    lastPublishTime = System.currentTimeMillis();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ReplicationMsg receive() throws IOException,
      ClassNotFoundException, DataFormatException,
      NotSupportedOldVersionPDUException
  {
    // Read the first 8 bytes containing the packet length
    int length = 0;

    // Let's start the stop-watch before waiting on read for the heartbeat check
    // to be operational
    lastReceiveTime = System.currentTimeMillis();

    while (length < 8)
    {
      final int read = input.read(rcvLengthBuf, length, 8 - length);
      if (read == -1)
      {
        lastReceiveTime = 0;
        throw new IOException("no more data");
      }
      else
      {
        length += read;
      }
    }

    final int totalLength = Integer.parseInt(
        new String(rcvLengthBuf), 16);

    try
    {
      length = 0;
      final byte[] buffer = new byte[totalLength];
      while (length < totalLength)
      {
        length += input.read(buffer, length, totalLength - length);
      }
      // We do not want the heartbeat to close the session when we are
      // processing a message even a time consuming one.
      lastReceiveTime = 0;
      return ReplicationMsg.generateMsg(buffer, protocolVersion);
    }
    catch (final OutOfMemoryError e)
    {
      throw new IOException("Packet too large, can't allocate "
          + totalLength + " bytes.");
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void setProtocolVersion(final short version)
  {
    protocolVersion = version;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void setSoTimeout(final int timeout) throws SocketException
  {
    socket.setSoTimeout(timeout);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void stopEncryption()
  {
    // There is no security layer.
  }
}
