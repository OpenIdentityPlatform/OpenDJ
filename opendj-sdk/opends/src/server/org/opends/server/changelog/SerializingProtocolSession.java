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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import org.opends.server.synchronization.SynchronizationMessage;

/**
 * This class use serialization to implement the ProtocolSession interface.
 * It was done to speed up the development of the sycnhronization feature
 * because it delegate most of its job to the object serialization but
 * will be replaced by a more appropriate mechanism.
 */
public class SerializingProtocolSession implements ProtocolSession
{
  Socket socket;
  ObjectOutputStream socketOutput = null;
  ObjectInputStream socketInput = null;
  private int count = 0;

  /**
   * Creates a new SerializingProtocolSession based on the provided
   * socket.
   * @param socket The socket that will be used to create the
   *               SerializingProtocolSession
   * @throws IOException When an IO error happen using the provided socket.
   */
  public SerializingProtocolSession(Socket socket) throws IOException
  {
    this.socket = socket;
    socketOutput = new ObjectOutputStream(socket.getOutputStream());
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
            throws IOException, SocketException
  {
    socketOutput.writeObject(msg);
    socketOutput.flush();
    /*
     * ObjectOutputStream class keep a cache of objects that have already
     * been sent across this stream. This improve performances by avoiding
     * sending several times the same object but unfortunately can cause
     * memory growth and in our case severe performance degradations.
     * We therefore free those resources by reseting the cache
     * every so often
     */
    if (count++ >= 5000)
    {
      socketOutput.reset();
      count = 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  public SynchronizationMessage receive()
            throws IOException, ClassNotFoundException
  {
    if (socketInput == null)
    {
      socketInput = new ObjectInputStream(socket.getInputStream());
    }
    return (SynchronizationMessage) socketInput.readObject();
  }

  /**
   * {@inheritDoc}
   */
  public String getRemoteAddress()
  {
    return socket.getInetAddress().getHostAddress();
  }
}
