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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXConnector;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.JMXConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.JMXConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * The JMX connection handler listens on its listen address only: with both its RMI registry, on
 * the listen port, and its RMI connector, on the RMI port. A JMX client reaches it there.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jmx" }, sequential = true)
public class JmxListenAddressTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Don't need a reason.");

  private static final String USER_DN = "cn=JMX Reader,o=test";
  private static final String USER_PASSWORD = "password";

  private static final String RMI_SERVER_HOSTNAME = "java.rmi.server.hostname";

  /** The address the handlers listen on. */
  private InetAddress listenAddress;
  /** An address of the host the handlers do not listen on. */
  private InetAddress otherAddress;
  /** The default SSL context of the JVM before this test replaced it. */
  private SSLContext defaultSSLContext;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
        "dn: " + USER_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: JMX Reader",
        "sn: Reader",
        "userPassword: " + USER_PASSWORD,
        "ds-privilege-name: jmx-read",
        "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy,cn=Password Policies,cn=config");

    // The stub of the RMI connector advertises the local host, unless told otherwise: listen on
    // the address that is not the local host's, or a session would get through even with a stub
    // that does not advertise the listen address. When the local host is exactly 127.0.0.1, the
    // JDK learns its host from the connector's own binding in the registry instead, and no
    // choice of address makes that difference visible.
    final InetAddress loopback = InetAddress.getByName("127.0.0.1");
    final InetAddress external = getNonLoopbackAddress();
    final boolean localHostIsLoopback =
        loopback.getHostAddress().equals(InetAddress.getLocalHost().getHostAddress());
    listenAddress = localHostIsLoopback ? external : loopback;
    otherAddress = localHostIsLoopback ? loopback : external;

    // The stub of an SSL connector carries an SslRMIClientSocketFactory, which takes the default
    // SSL context of the JVM the first time anything uses it, and keeps it: that happens as soon as
    // the connector starts, before any client of this test connects.
    defaultSSLContext = SSLContext.getDefault();
    final SSLContext blindContext = SSLContext.getInstance("TLS");
    blindContext.init(null, new TrustManager[] { new BlindExtendedTrustManager() }, null);
    SSLContext.setDefault(blindContext);
  }

  /**
   * Trusts any server, whatever host name it is reached by: the factory checks the host name of
   * the server when the JDK asks for it, and a trust manager that is not an extended one gets that
   * check added by the JDK. The test certificate names no host.
   */
  private static final class BlindExtendedTrustManager extends X509ExtendedTrustManager
  {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
    {
      // Trusts any client.
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
    {
      // Trusts any client.
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
    {
      // Trusts any client.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
    {
      // Trusts any server.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
    {
      // Trusts any server.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
    {
      // Trusts any server.
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
      return new X509Certificate[0];
    }
  }

  @AfterClass(alwaysRun = true)
  public void restoreDefaultSSLContext()
  {
    if (defaultSSLContext != null)
    {
      SSLContext.setDefault(defaultSSLContext);
    }
  }

  @DataProvider
  public Object[][] useSSL()
  {
    return new Object[][] { { false }, { true } };
  }

  @Test(dataProvider = "useSSL")
  public void listensOnlyOnTheConfiguredListenAddress(boolean useSSL) throws Exception
  {
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final int listenPort = ports[0];
    final int rmiPort = ports[1];

    JmxConnectionHandler handler = newHandler(listenAddress, listenPort, rmiPort, useSSL);
    try
    {
      // Starts the RMI registry and the RMI connector in this thread.
      handler.run();
      assertNotNull(handler.getRMIConnector().jmxRmiConnectorNoClientCertificate, "the RMI connector did not start");

      assertListensOnlyOn(listenAddress, listenPort, rmiPort);
      assertThat(handler.getListeners()).containsExactly(new HostPort(listenAddress.getHostAddress(), listenPort));
      assertOpensASession(listenAddress, listenPort);
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
    }
  }

  /** A change that restarts the RMI connector keeps the listen address that is reported. */
  @Test
  public void reportsTheListenAddressAfterAChangeOfTheRmiPort() throws Exception
  {
    final int[] ports = TestCaseUtils.findFreePorts(3);
    final int listenPort = ports[0];

    JmxConnectionHandler handler = newHandler(listenAddress, listenPort, ports[1], false);
    try
    {
      handler.run();
      applyConfigurationChange(handler, newConfig(listenAddress, listenPort, ports[2], false));

      assertListensOnlyOn(listenAddress, listenPort, ports[2]);
      assertThat(handler.getListeners()).containsExactly(new HostPort(listenAddress.getHostAddress(), listenPort));
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
    }
  }

  /**
   * A change of listen-address takes effect when the handler restarts: until then, the RMI
   * registry, the RMI connector restarted by another change and the listeners stay on the address
   * the handler was initialized with.
   */
  @Test
  public void keepsItsListenAddressUntilItRestarts() throws Exception
  {
    final int[] ports = TestCaseUtils.findFreePorts(3);
    final int listenPort = ports[0];

    JmxConnectionHandler handler = newHandler(listenAddress, listenPort, ports[1], false);
    try
    {
      handler.run();
      applyConfigurationChange(handler, newConfig(otherAddress, listenPort, ports[2], false));

      assertListensOnlyOn(listenAddress, listenPort, ports[2]);
      assertThat(handler.getListeners()).containsExactly(new HostPort(listenAddress.getHostAddress(), listenPort));
      assertOpensASession(listenAddress, listenPort);
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
    }
  }

  /**
   * A handler restarted on the wildcard address no longer sends clients to the loopback address a
   * previous one listened on.
   */
  @Test
  public void stopsAdvertisingTheLoopbackAddressOnTheWildcardAddress() throws Exception
  {
    if (InetAddress.getLocalHost().isLoopbackAddress())
    {
      throw new SkipException("the JDK advertises a loopback address anyway");
    }
    final int[] ports = TestCaseUtils.findFreePorts(4);
    JmxConnectionHandler handler = newHandler(InetAddress.getByName("127.0.0.1"), ports[0], ports[1], false);
    try
    {
      handler.run();
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
    }

    handler = newHandler(InetAddress.getByName("0.0.0.0"), ports[2], ports[3], false);
    try
    {
      handler.run();

      final Registry registry = LocateRegistry.getRegistry("127.0.0.1", ports[2]);
      final String[] names = registry.list();
      assertThat(names).isNotEmpty();
      for (String name : names)
      {
        assertThat(registry.lookup(name).toString()).doesNotContain("[127.0.0.1:").doesNotContain("[0.0.0.0:");
      }
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
    }
  }

  /**
   * The host that the operator gives the stubs to advertise, behind a NAT for instance, is kept.
   * <p>
   * The name is one the server never records itself, which advertises addresses only. It is
   * reachable, since the registry calls the stubs bound in it. Runs last: once cleared, the property
   * leaves the JDK advertising the host it last read.
   */
  @Test(priority = 1)
  public void keepsTheOperatorsRmiServerHostname() throws Exception
  {
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final String operatorsHostname = "localhost";
    System.setProperty(RMI_SERVER_HOSTNAME, operatorsHostname);
    JmxConnectionHandler handler = newHandler(listenAddress, ports[0], ports[1], false);
    try
    {
      handler.run();
      assertNotNull(handler.getRMIConnector().jmxRmiConnectorNoClientCertificate, "the RMI connector did not start");

      assertEquals(System.getProperty(RMI_SERVER_HOSTNAME), operatorsHostname);
    }
    finally
    {
      handler.finalizeConnectionHandler(STOP_REASON);
      System.clearProperty(RMI_SERVER_HOSTNAME);
    }
  }

  private void assertListensOnlyOn(InetAddress address, int listenPort, int rmiPort) throws IOException
  {
    final InetAddress other = address.equals(listenAddress) ? otherAddress : listenAddress;
    assertReachable(other);

    assertTrue(isAcceptingConnections(address, listenPort),
        "the RMI registry does not listen on " + address.getHostAddress());
    assertFalse(isAcceptingConnections(other, listenPort),
        "the RMI registry answers on " + other.getHostAddress() + ", which is not the listen address");
    assertTrue(isAcceptingConnections(address, rmiPort),
        "the RMI connector does not listen on " + address.getHostAddress());
    assertFalse(isAcceptingConnections(other, rmiPort),
        "the RMI connector answers on " + other.getHostAddress() + ", which is not the listen address");
  }

  /** A refusal on an address proves something only if a listener on every address answers there. */
  private static void assertReachable(InetAddress address) throws IOException
  {
    try (ServerSocket control = new ServerSocket(0))
    {
      assertTrue(isAcceptingConnections(address, control.getLocalPort()),
          address.getHostAddress() + " is not reachable from this host, so a refusal on it proves nothing");
    }
  }

  /**
   * Opens a JMX session through the RMI registry, then calls the RMI connector: the call goes to
   * the address that the stub of the connector advertises.
   */
  private static void assertOpensASession(InetAddress address, int listenPort) throws Exception
  {
    final Map<String, Object> env = new HashMap<>();
    env.put(JMXConnector.CREDENTIALS, new String[] { USER_DN, USER_PASSWORD });
    env.put("jmx.remote.x.client.connection.check.period", 0);

    try (OpendsJmxConnector connector = new OpendsJmxConnector(address.getHostAddress(), listenPort, env))
    {
      connector.connect();
      assertThat(connector.getMBeanServerConnection().getMBeanCount()).isPositive();
    }
  }

  private static void applyConfigurationChange(JmxConnectionHandler handler, JMXConnectionHandlerCfg config)
  {
    final ConfigChangeResult result = handler.applyConfigurationChange(config);
    assertEquals(result.getResultCode(), ResultCode.SUCCESS, String.valueOf(result.getMessages()));
  }

  private static JmxConnectionHandler newHandler(InetAddress listenAddress, int listenPort, int rmiPort,
      boolean useSSL) throws Exception
  {
    JmxConnectionHandler handler = new JmxConnectionHandler();
    handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(),
        newConfig(listenAddress, listenPort, rmiPort, useSSL));
    return handler;
  }

  private static JMXConnectionHandlerCfg newConfig(InetAddress listenAddress, int listenPort, int rmiPort,
      boolean useSSL) throws Exception
  {
    Entry handlerEntry = TestCaseUtils.makeEntry(
        "dn: cn=Listen Address JMX Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-jmx-connection-handler",
        "cn: Listen Address JMX Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: " + listenAddress.getHostAddress(),
        "ds-cfg-listen-port: " + listenPort,
        "ds-cfg-rmi-port: " + rmiPort,
        "ds-cfg-use-ssl: " + useSSL,
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-ssl-cert-nickname: server-cert");
    return InitializationUtils.getConfiguration(JMXConnectionHandlerCfgDefn.getInstance(), handlerEntry);
  }
}
