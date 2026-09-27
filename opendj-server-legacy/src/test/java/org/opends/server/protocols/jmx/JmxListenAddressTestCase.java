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
package org.opends.server.protocols.jmx;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.net.InetAddress;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.meta.JMXConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.JMXConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * The JMX connection handler listens on its listen address only: with both its RMI registry, on
 * the listen port, and its RMI connector, on the RMI port.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jmx" }, sequential = true)
public class JmxListenAddressTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Don't need a reason.");

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @DataProvider
  public Object[][] useSSL()
  {
    return new Object[][] { { false }, { true } };
  }

  @Test(dataProvider = "useSSL")
  public void listensOnlyOnTheConfiguredListenAddress(boolean useSSL) throws Exception
  {
    final InetAddress external = getNonLoopbackAddress();
    final InetAddress loopback = InetAddress.getByName("127.0.0.1");
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final int listenPort = ports[0];
    final int rmiPort = ports[1];

    Entry handlerEntry = TestCaseUtils.makeEntry(
        "dn: cn=Listen Address JMX Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-jmx-connection-handler",
        "cn: Listen Address JMX Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 127.0.0.1",
        "ds-cfg-listen-port: " + listenPort,
        "ds-cfg-rmi-port: " + rmiPort,
        "ds-cfg-use-ssl: " + useSSL,
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-ssl-cert-nickname: server-cert");
    JMXConnectionHandlerCfg config =
        InitializationUtils.getConfiguration(JMXConnectionHandlerCfgDefn.getInstance(), handlerEntry);

    JmxConnectionHandler handler = new JmxConnectionHandler();
    handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(), config);
    try
    {
      // Starts the RMI registry and the RMI connector in this thread.
      handler.run();
      assertNotNull(handler.getRMIConnector().jmxRmiConnectorNoClientCertificate, "the RMI connector did not start");

      assertTrue(isAcceptingConnections(loopback, listenPort), "the RMI registry does not listen on 127.0.0.1");
      assertFalse(isAcceptingConnections(external, listenPort),
          "the RMI registry answers on " + external.getHostAddress() + ", which is not the listen address");
      assertTrue(isAcceptingConnections(loopback, rmiPort), "the RMI connector does not listen on 127.0.0.1");
      assertFalse(isAcceptingConnections(external, rmiPort),
          "the RMI connector answers on " + external.getHostAddress() + ", which is not the listen address");
      assertThat(handler.getListeners()).containsExactly(new HostPort("127.0.0.1", listenPort));
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
    }
  }
}
