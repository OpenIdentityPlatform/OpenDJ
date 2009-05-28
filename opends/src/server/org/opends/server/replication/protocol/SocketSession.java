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
 */
package org.opends.server.replication.protocol;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.zip.DataFormatException;

import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

/**
 * This class Implement a protocol session using a basic socket and relying on
 * the innate encoding/decoding capabilities of the ReplicationMsg
 * by using the getBytes() and generateMsg() methods of those classes.
 */
public class SocketSession implements ProtocolSession
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private Socket socket;
  private InputStream input;
  private OutputStream output;
  byte[] rcvLengthBuf = new byte[8];

  /**
   * The time the last message published to this session.
   */
  private long lastPublishTime = 0;


  /**
   * The time the last message was received on this session.
   */
  private long lastReceiveTime = 0;

  private boolean closeInitiated = false;


  /**
   * Creates a new SocketSession based on the provided socket.
   *
   * @param socket The Socket on which the SocketSession will be based.
   * @throws IOException When an IException happens on the socket.
   */
  public SocketSession(Socket socket) throws IOException
  {
    this.socket = socket;
    /*
     * Use a window instead of the TCP flow control.
     * Therefore set a very large value for send and receive buffer sizes.
     */
    input = socket.getInputStream();
    output = socket.getOutputStream();
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException
  {
    closeInitiated = true;

    if (debugEnabled())
    {
      TRACER.debugInfo("Closing SocketSession."
          + stackTraceToSingleLineString(new Exception()));
    }
    socket.close();
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void publish(ReplicationMsg msg)
         throws IOException
  {
    publish(msg, ProtocolVersion.getCurrentVersion());
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void publish(ReplicationMsg msg, short reqProtocolVersion)
         throws IOException
  {
    byte[] buffer = msg.getBytes(reqProtocolVersion);
    String str = String.format("%08x", buffer.length);

    if (debugEnabled())
    {
      TRACER.debugInfo("SocketSession publish <" + str + ">");
    }

    byte[] sendLengthBuf = str.getBytes();

    output.write(sendLengthBuf);
    output.write(buffer);
    output.flush();

    lastPublishTime = System.currentTimeMillis();
  }

  /**
   * {@inheritDoc}
   */
  public ReplicationMsg receive() throws IOException,
      ClassNotFoundException, DataFormatException,
      NotSupportedOldVersionPDUException
  {
    /* Read the first 8 bytes containing the packet length */
    int length = 0;

    /* Let's start the stop-watch before waiting on read */
    /* for the heartbeat check to be operationnal        */
    lastReceiveTime = System.currentTimeMillis();

    while (length<8)
    {
      int read = input.read(rcvLengthBuf, length, 8-length);
      if (read == -1)
      {
        lastReceiveTime=0;
        throw new IOException("no more data");
      }
      else
      {
        length += read;
      }
    }

    int totalLength = Integer.parseInt(new String(rcvLengthBuf), 16);

    try
    {
      length = 0;
      byte[] buffer = new byte[totalLength];
      while (length < totalLength)
      {
        length += input.read(buffer, length, totalLength - length);
      }
      /* We do not want the heartbeat to close the session when */
      /* we are processing a message even a time consuming one. */
      lastReceiveTime=0;
      return ReplicationMsg.generateMsg(buffer);
    }
    catch (OutOfMemoryError e)
    {
      throw new IOException("Packet too large, can't allocate "
                            + totalLength + " bytes.");
    }
  }

  /**
   * {@inheritDoc}
   */
  public void stopEncryption()
  {
    // There is no security layer.
  }

  /**
   * {@inheritDoc}
   */
  public boolean isEncrypted()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public long getLastPublishTime()
  {
    return lastPublishTime;
  }

  /**
   * {@inheritDoc}
   */
  public long getLastReceiveTime()
  {
    if (lastReceiveTime==0)
    {
      return System.currentTimeMillis();
    }
    return lastReceiveTime;
  }

  /**
   * {@inheritDoc}
   */
  public String getRemoteAddress()
  {
    return socket.getInetAddress().getHostAddress();
  }

  /**
   * {@inheritDoc}
   */
  public void setSoTimeout(int timeout) throws SocketException
  {
    socket.setSoTimeout(timeout);
  }

  /**
   * {@inheritDoc}
   */
  public boolean closeInitiated()
  {
    return closeInitiated;
  }
}
