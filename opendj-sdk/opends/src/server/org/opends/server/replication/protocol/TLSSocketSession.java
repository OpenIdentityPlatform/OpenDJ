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

import javax.net.ssl.SSLSocket;

import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class implements a protocol session using TLS.
 */
public final class TLSSocketSession implements ProtocolSession
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private final Socket plainSocket;
  private final SSLSocket secureSocket;
  private final InputStream plainInput;
  private final OutputStream plainOutput;
  private final byte[] rcvLengthBuf = new byte[8];
  private final String readableRemoteAddress;
  private final String remoteAddress;

  /**
   * The time the last message published to this session.
   */
  private volatile long lastPublishTime = 0;

  /**
   * The time the last message was received on this session.
   */
  private volatile long lastReceiveTime = 0;

  // Close and error guarded by stateLock: use a different lock to publish since
  // publishing can block, and we don't want to block while closing failed
  // connections.
  private final Object stateLock = new Object();
  private boolean closeInitiated = false;
  private Throwable sessionError = null;

  // Publish guarded by publishLock: use a full lock here so that we can
  // optionally publish StopMsg during close.
  private final Lock publishLock = new ReentrantLock();

  // Does not need protecting: updated only during single threaded handshake.
  private short protocolVersion = ProtocolVersion.getCurrentVersion();
  private InputStream input;
  private OutputStream output;



  /**
   * Creates a new TLSSocketSession.
   *
   * @param socket
   *          The regular Socket on which the SocketSession will be based.
   * @param secureSocket
   *          The secure Socket on which the SocketSession will be based.
   * @throws IOException
   *           When an IException happens on the socket.
   */
  public TLSSocketSession(final Socket socket,
      final SSLSocket secureSocket) throws IOException
  {
    if (debugEnabled())
    {
      TRACER.debugInfo(
          "Creating TLSSocketSession from %s to %s in %s",
          socket.getLocalSocketAddress(),
          socket.getRemoteSocketAddress(),
          stackTraceToSingleLineString(new Exception()));
    }

    this.plainSocket = socket;
    this.secureSocket = secureSocket;
    this.plainInput = plainSocket.getInputStream();
    this.plainOutput = plainSocket.getOutputStream();
    this.input = secureSocket.getInputStream();
    this.output = secureSocket.getOutputStream();
    this.readableRemoteAddress = plainSocket.getRemoteSocketAddress()
        .toString();
    this.remoteAddress = plainSocket.getInetAddress()
        .getHostAddress();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    Throwable localSessionError;

    synchronized (stateLock)
    {
      if (closeInitiated)
      {
        return;
      }

      localSessionError = sessionError;
      closeInitiated = true;
    }

    // Perform close outside of critical section.
    if (debugEnabled())
    {
      if (localSessionError == null)
      {
        TRACER.debugInfo(
            "Closing TLSSocketSession from %s to %s in %s",
            plainSocket.getLocalSocketAddress(),
            plainSocket.getRemoteSocketAddress(),
            stackTraceToSingleLineString(new Exception()));
      }
      else
      {
        TRACER.debugInfo(
            "Aborting TLSSocketSession from %s to %s in %s due to the "
                + "following error: %s",
            plainSocket.getLocalSocketAddress(),
            plainSocket.getRemoteSocketAddress(),
            stackTraceToSingleLineString(new Exception()),
            stackTraceToSingleLineString(localSessionError));
      }
    }

    // V4 protocol introduces a StopMsg to properly end communications.
    if (localSessionError == null)
    {
      if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
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
    }

    try
    {
      plainSocket.close();
    }
    catch (final IOException ignored)
    {
      // Ignore errors on close.
    }

    try
    {
      secureSocket.close();
    }
    catch (final IOException ignored)
    {
      // Ignore errors on close.
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean closeInitiated()
  {
    synchronized (stateLock)
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
    return readableRemoteAddress;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getRemoteAddress()
  {
    return remoteAddress;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEncrypted()
  {
    return input != plainInput;
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
    catch (final IOException e)
    {
      setSessionError(e);
      throw e;
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
      DataFormatException, NotSupportedOldVersionPDUException
  {
    try
    {
      // Read the first 8 bytes containing the packet length.
      int length = 0;

      // Let's start the stop-watch before waiting on read for the heartbeat
      // check
      // to be operational.
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

      final int totalLength = Integer.parseInt(new String(
          rcvLengthBuf), 16);

      try
      {
        length = 0;
        final byte[] buffer = new byte[totalLength];
        while (length < totalLength)
        {
          final int read = input.read(buffer, length, totalLength
              - length);
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
    catch (final IOException e)
    {
      setSessionError(e);
      throw e;
    }
    catch (final DataFormatException e)
    {
      setSessionError(e);
      throw e;
    }
    catch (final NotSupportedOldVersionPDUException e)
    {
      setSessionError(e);
      throw e;
    }
    catch (final RuntimeException e)
    {
      setSessionError(e);
      throw e;
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
    plainSocket.setSoTimeout(timeout);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void stopEncryption()
  {
    // The secure socket has been configured not to auto close the underlying
    // plain socket. We should close it here and properly tear down the SSL
    // session, but this is not compatible with the existing protocol.
    if (false)
    {
      try
      {
        secureSocket.close();
      }
      catch (IOException ignored)
      {
        // Ignore.
      }
    }

    input = plainInput;
    output = plainOutput;
  }



  private void setSessionError(final Exception e)
  {
    synchronized (stateLock)
    {
      if (sessionError == null)
      {
        sessionError = e;
      }
    }
  }
}
