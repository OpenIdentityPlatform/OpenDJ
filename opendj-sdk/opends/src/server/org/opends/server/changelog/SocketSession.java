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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.changelog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.zip.DataFormatException;

import org.opends.server.synchronization.SynchronizationMessage;

/**
 * This class Implement a protocol session using a basic socket and relying on
 * the innate encoding/decoding capabilities of the SynchronizationMessage
 * by using the getBytes() and generateMsg() methods of those classes.
 *
 * TODO : should have some versioning in the packets so that
 *        the futur versions can evolve while still
 *        being able to understand the older versions.
 */
public class SocketSession implements ProtocolSession
{
  private Socket socket;
  private InputStream input;
  private OutputStream output;
  byte[] rcvLengthBuf = new byte[8];

  /**
   * Creates a new SocketSession based on the provided socket.
   *
   * @param socket The Socket on which the SocketSession will be based.
   * @throws IOException When an IException happens on the socket.
   */
  public SocketSession(Socket socket) throws IOException
  {
    this.socket = socket;
    input = socket.getInputStream();
    output = socket.getOutputStream();
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException
  {
    socket.close();
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void publish(SynchronizationMessage msg)
         throws IOException
  {
    byte[] buffer = msg.getBytes();
    String str = String.format("%08x", buffer.length);
    byte[] sendLengthBuf = str.getBytes();

    output.write(sendLengthBuf);
    output.write(buffer);
    output.flush();
  }

  /**
   * {@inheritDoc}
   */
  public SynchronizationMessage receive() throws IOException,
      ClassNotFoundException, DataFormatException
  {
    /* Read the first 8 bytes containing the packet length */
    int length = 0;

    while (length<8)
    {
      int read = input.read(rcvLengthBuf, length, 8-length);
      if (read == -1)
        throw new IOException("no more data");
      else
        length += read;
    }

    int totalLength = Integer.parseInt(new String(rcvLengthBuf), 16);

    try
    {
      length = 0;
      byte[] buffer = new byte[totalLength];
      while (length < totalLength)
        length += input.read(buffer, length, totalLength - length);
      return SynchronizationMessage.generateMsg(buffer);
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
}
