/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.tools;

import static org.opends.server.protocols.ldap.LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests the plain (neither SSL nor StartTLS) connection path of
 * {@link LDAPConnection}, which is the one used by the DSML gateway and by the
 * tools built on {@code LDAPConnectionArgumentParser}. Those are the only
 * callers reaching {@code createSocket()}: a caller which installs an
 * {@code SSLConnectionFactory} goes through {@code createSSLSocket()} instead.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "tools" }, sequential = true)
public class LDAPConnectionTestCase extends DirectoryServerTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * The socket must be connected to the directory server: binding it to the
   * server address instead makes every plain connection fail with
   * "Address already in use".
   */
  @Test
  public void testConnectToHostConnectsThePlainSocket() throws Exception
  {
    LDAPConnection connection = new LDAPConnection(
        InetAddress.getLoopbackAddress().getHostAddress(),
        TestCaseUtils.getServerLdapPort(), new LDAPConnectionOptions());
    try
    {
      connection.connectToHost("cn=Directory Manager", "password");
      assertNotNull(connection.getLDAPReader(), "the connection was not established");
      assertNotNull(connection.getLDAPWriter(), "the connection was not established");
    }
    finally
    {
      connection.close(new AtomicInteger(1));
    }
  }

  /**
   * A port with nothing behind it must be reported as a connect error: it is
   * the {@code ConnectException} of each candidate address which drives the
   * failover of {@code createSocket()}.
   */
  @Test
  public void testConnectToClosedPortIsAConnectError() throws Exception
  {
    LDAPConnection connection = new LDAPConnection(
        InetAddress.getLoopbackAddress().getHostAddress(),
        TestCaseUtils.findFreePort(), new LDAPConnectionOptions());
    try
    {
      connection.connectToHost("cn=Directory Manager", "password");
      fail("connecting to a closed port should have failed");
    }
    catch (LDAPConnectionException e)
    {
      assertEquals(e.getResultCode(), CLIENT_SIDE_CONNECT_ERROR, String.valueOf(e));
    }
    finally
    {
      connection.close(new AtomicInteger(1));
    }
  }
}
