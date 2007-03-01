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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import java.net.Socket;



/**
 * This class defines a thread that will establish a connection to a server and
 * send it a specified data set.
 */
public class SocketWriteThread
       extends Thread
{
  // The data to write to the server.
  private byte[] data;

  // The port to use to connect to the server.
  private int serverPort;

  // The socket to use to communicate with the server.
  private Socket socket;



  /**
   * Creates a new instance of this write thread that will send data to the
   * specified server port.
   *
   * @param  testCaseName  The name of the test case with which this thread is
   *                       associated.
   * @param  serverPort    The port to use to connect to the server.
   * @param  data          The data to write.
   */
  public SocketWriteThread(String testCaseName, int serverPort, byte[] data)
  {
    setName("Socket Write Thread -- " + testCaseName);
    setDaemon(true);

    this.serverPort = serverPort;
    this.data       = data;
  }



  /**
   * Accepts a single connection and consumes anything written on that
   * connection.
   */
  public void run()
  {
    try
    {
      socket = new Socket("127.0.0.1", serverPort);
      socket.getOutputStream().write(data);
    }
    catch (Exception e)
    {
      // FIXME -- What to do here?
    }
  }



  /**
   * Closes the connection to the server.
   */
  public void close()
  {
    try
    {
      socket.close();
    } catch (Exception e) {}
  }
}

