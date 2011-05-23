/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap.tools;



import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.testng.annotations.Test;



/**
 * Tests PerfToolTCPNIOTransportFactoryTestCase class.
 */
public class PerfToolTCPNIOTransportFactoryTestCase extends ToolsTestCase
{
  /**
   * Finds a free server socket port on the local host.
   *
   * @return The free port.
   */
  public static int findFreePort()
  {
    int port;
    try
    {
      ServerSocket serverLdapSocket = new ServerSocket();
      serverLdapSocket.setReuseAddress(true);
      serverLdapSocket.bind(new InetSocketAddress("127.0.0.1", 0));
      port = serverLdapSocket.getLocalPort();
      serverLdapSocket.close();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
    return port;
  }

  /**
   * Tests the transport factory.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test()
  public void testGetInstance() throws Exception
  {
    // Create a transport.
    final TransportFactory factory = new PerfToolTCPNIOTransportFactory();
    final TCPNIOTransport transport = factory.createTCPTransport();
    final int port = findFreePort();
    transport.bind(port);
    // Establish a socket connection to see if the transport factory works.
    final Socket socket = new Socket("localhost", port);
    // Successfully connected if there is no exception.
    assertTrue(socket.isConnected());
    // Don't stop the transport because it is shared with the ldap server.
  }
}
