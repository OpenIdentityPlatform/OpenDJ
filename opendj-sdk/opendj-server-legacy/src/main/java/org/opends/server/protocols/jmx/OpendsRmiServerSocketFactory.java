/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;


/**
 * The RMI connector class starts and stops the JMX RMI connector server.
 * There are 2 different connector servers
 * <ul>
 * <li> the RMI Client connector server, supporting TLS-encrypted.
 * communication, server authentication by certificate and client
 * authentication by providing appropriate LDAP credentials through
 * SASL/PLAIN.
 * <li> the RMI client connector server, supporting TLS-encrypted
 * communication, server authentication by certificate, client
 * authentication by certificate and identity assertion through SASL/PLAIN.
 * </ul>
 * <p>
 * Each connector is registered into the JMX MBean server.
 */

/**
 * An implementation of the socketServer.
 */
public class OpendsRmiServerSocketFactory implements RMIServerSocketFactory
{
  /** The address to listen on, which could be INADDR_ANY. */
  private final InetAddress listenAddress;

  /** The Created ServerSocket. */
  ServerSocket serverSocket;

  /**
   * Create a new socket factory which will listen on the specified address.
   *
   * @param listenAddress The address to listen on.
   */
  public OpendsRmiServerSocketFactory(InetAddress listenAddress)
  {
    this.listenAddress = listenAddress;
  }

  /**
   *  Create a server socket on the specified port, listening on the address
   *  passed in the constructor. (port 0 indicates an anonymous port).
   *
   *  @param port the port number
   *  @return the server socket on the specified port
   *  @throws IOException if an I/O error occurs during server socket creation
   */
  public ServerSocket createServerSocket(int port)  throws IOException
  {
    return new ServerSocket(port, 100, listenAddress);
  }

  /**
   * Close the underlying socket.
   *
   * @throws IOException If an I/O error occurs when closing the socket.
   */
  protected void close() throws IOException
  {
    serverSocket.close();
  }
}
