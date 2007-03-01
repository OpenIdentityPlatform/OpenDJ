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



import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;



/**
 * This class defines a thread that will create a server socket, read data from
 * it, and make that data available in a byte array.
 */
public class SocketReadThread
       extends Thread
{
  // The server socket that we will use to accept the connection.
  private ServerSocket serverSocket;

  // The client socket accepted by this thread.
  private Socket clientSocket;



  /**
   * Creates a new server socket on an arbitrarily-selected available port.
   *
   * @param  testCaseName  The name of the test case with which this thread is
   *                       associated.
   *
   * @throws  Exception  If a problem occurs while creating the server socket.
   */
  public SocketReadThread(String testCaseName)
         throws Exception
  {
    setName("Socket Read Thread -- " + testCaseName);
    setDaemon(true);

    serverSocket = new ServerSocket();
    serverSocket.setReuseAddress(true);
    serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
  }



  /**
   * Retrieves the port on which the server socket is listening.
   *
   * @return  The port on which the server socket is listening.
   */
  public int getListenPort()
  {
    return serverSocket.getLocalPort();
  }



  /**
   * Accepts a single connection and consumes anything written on that
   * connection.
   */
  public void run()
  {
    try
    {
      clientSocket = serverSocket.accept();
    }
    catch (Exception e)
    {
      // FIXME -- What to do here?
    }
  }



  /**
   * Retrieves the data read from the socket and clears the output stream.
   *
   * @param  length  The number of bytes to read.
   *
   * @return  The data read from the socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public byte[] getDataRead(int length)
         throws Exception
  {
    while (clientSocket == null)
    {
      Thread.sleep(1);
    }


    byte[] buffer = new byte[length];
    int pos = 0;
    while (pos < length)
    {
      int bytesRead = clientSocket.getInputStream().read(buffer, pos,
                                                         length-pos);
      if (bytesRead < 0)
      {
        throw new Exception("Hit the end of the stream");
      }

      pos += bytesRead;
    }

    return buffer;
  }



  /**
   * Closes the client and server sockets.
   */
  public void close()
  {
    try
    {
      clientSocket.close();
    } catch (Exception e) {}

    try
    {
      serverSocket.close();
    } catch (Exception e) {}
  }
}

