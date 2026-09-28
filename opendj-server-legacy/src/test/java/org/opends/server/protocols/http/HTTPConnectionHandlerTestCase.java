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
package org.opends.server.protocols.http;

import static java.util.concurrent.TimeUnit.*;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.meta.HTTPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.HTTPConnectionHandlerCfg;
import org.opends.admin.ads.util.BlindTrustManager;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.DummyAlertHandler;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.opends.server.util.TestTimer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "http" }, sequential = true)
public class HTTPConnectionHandlerTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Don't need a reason.");

  /** An address of TEST-NET-1 (RFC 5737), which is never assigned to an interface of the host. */
  private static final String UNASSIGNED_ADDRESS = "192.0.2.1";

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available, so we'll start the server.
    TestCaseUtils.startServer();
  }

  /**
   * The start method must not return before the handler thread has attempted to start the embedded
   * HTTP server, otherwise a client connecting right after the handler has been enabled through
   * dsconfig can be refused.
   *
   * @throws Exception
   *           if the handler cannot be instantiated or started.
   */
  @Test
  public void testStartWaitsForListenPort() throws Exception
  {
    final int listenPort = TestCaseUtils.findFreePort();
    HTTPConnectionHandler handler = newHandler(listenPort, "127.0.0.1");
    try
    {
      handler.start();

      // No retry loop here on purpose: once start() has returned, the port must already be open.
      TestCaseUtils.assertPortIsAcceptingConnections(listenPort);
    }
    finally
    {
      stop(handler);
    }
  }

  /** A handler restricted to the loopback address must not answer on the other addresses of the host. */
  @Test
  public void listensOnlyOnTheConfiguredListenAddress() throws Exception
  {
    final InetAddress external = getNonLoopbackAddress();
    final int listenPort = TestCaseUtils.findFreePort();
    HTTPConnectionHandler handler = newHandler(listenPort, "127.0.0.1");
    try
    {
      handler.start();

      assertTrue(isAcceptingConnections(loopback(), listenPort), "the configured address is not listened on");
      assertFalse(isAcceptingConnections(external, listenPort),
          "the handler answers on " + external.getHostAddress() + ", which is not one of its listen addresses");
      assertThat(handler.getListeners()).containsExactly(new HostPort("127.0.0.1", listenPort));
    }
    finally
    {
      stop(handler);
    }
  }

  @DataProvider
  public Object[][] useSSL()
  {
    return new Object[][] { { false }, { true } };
  }

  /** Each of several listen addresses gets its own listener, with the handler's SSL settings. */
  @Test(dataProvider = "useSSL")
  public void listensOnEveryConfiguredListenAddress(boolean useSSL) throws Exception
  {
    final InetAddress external = getNonLoopbackAddress();
    final int listenPort = TestCaseUtils.findFreePort();
    HTTPConnectionHandler handler = newHandler(useSSL, listenPort, "127.0.0.1", external.getHostAddress());
    try
    {
      handler.start();

      assertAnswers(loopback(), listenPort, useSSL);
      assertAnswers(external, listenPort, useSSL);
      assertThat(handler.getListeners()).containsOnly(
          new HostPort("127.0.0.1", listenPort), new HostPort(external.getHostAddress(), listenPort));
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * When one listen address cannot be bound, the handler does not start, and must not keep the
   * addresses it has already bound: nothing would ever release them.
   * <p>
   * The listener of 127.0.0.1 starts before the one of {@value #UNASSIGNED_ADDRESS}: the embedded
   * server starts its listeners in the order of a hash map of their names.
   */
  @Test
  public void releasesTheBoundListenAddressesWhenAnotherCannotBeBound() throws Exception
  {
    final int listenPort = TestCaseUtils.findFreePort();
    HTTPConnectionHandler handler = newHandler(listenPort, "127.0.0.1", UNASSIGNED_ADDRESS);
    try
    {
      handler.start();

      // The handler thread tries twice, then disables the handler: wait for it to give up.
      final InetAddress loopback = loopback();
      new TestTimer.Builder().maxSleep(10, SECONDS).sleepTimes(100, MILLISECONDS).toTimer()
          .repeatUntilSuccess(new TestTimer.CallableVoid()
          {
            @Override
            public void call() throws Exception
            {
              assertFalse(isAcceptingConnections(loopback, listenPort),
                  "127.0.0.1 is still listened on although the handler could not start");
            }
          });
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * A handler that could not start starts again after a change of its listen address, and then
   * reports the address it listens on, not the one it was initialized with.
   */
  @Test
  public void reportsTheListenAddressItStartsWithAfterAChange() throws Exception
  {
    final int listenPort = TestCaseUtils.findFreePort();
    final int givenUp = DummyAlertHandler.getAlertCount(ALERT_TYPE_HTTP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES);
    HTTPConnectionHandler handler = newHandler(listenPort, UNASSIGNED_ADDRESS);
    try
    {
      handler.start();

      // The handler thread tries twice, then disables the handler: a change applied before it gives up is undone.
      final TestTimer timer = new TestTimer.Builder().maxSleep(10, SECONDS).sleepTimes(100, MILLISECONDS).toTimer();
      timer.repeatUntilSuccess(new TestTimer.CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          assertThat(DummyAlertHandler.getAlertCount(ALERT_TYPE_HTTP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES))
              .as("the handler has not given up starting").isGreaterThan(givenUp);
        }
      });
      handler.applyConfigurationChange(newConfig(false, listenPort, "127.0.0.1"));

      final InetAddress loopback = loopback();
      timer.repeatUntilSuccess(new TestTimer.CallableVoid()
          {
            @Override
            public void call() throws Exception
            {
              assertTrue(isAcceptingConnections(loopback, listenPort), "the new listen address is not listened on");
            }
          });
      assertThat(handler.getListeners()).containsExactly(new HostPort("127.0.0.1", listenPort));
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * Asserts that a listener answers on the address: over TLS, a completed handshake, which a
   * listener without the handler's SSL settings cannot give.
   */
  private static void assertAnswers(InetAddress address, int port, boolean useSSL) throws Exception
  {
    if (!useSSL)
    {
      assertTrue(isAcceptingConnections(address, port), address.getHostAddress() + " is not listened on");
      return;
    }
    final SSLContext client = SSLContext.getInstance("TLS");
    client.init(null, new TrustManager[] { new BlindTrustManager() }, null);
    try (SSLSocket socket = (SSLSocket) client.getSocketFactory().createSocket(address, port))
    {
      socket.setSoTimeout(10000);
      socket.startHandshake();
    }
    catch (IOException e)
    {
      throw new AssertionError(address.getHostAddress() + " does not complete a TLS handshake", e);
    }
  }

  private static HTTPConnectionHandler newHandler(int listenPort, String... listenAddresses) throws Exception
  {
    return newHandler(false, listenPort, listenAddresses);
  }

  private static HTTPConnectionHandler newHandler(boolean useSSL, int listenPort, String... listenAddresses)
      throws Exception
  {
    HTTPConnectionHandler handler = new HTTPConnectionHandler();
    handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(),
        newConfig(useSSL, listenPort, listenAddresses));
    return handler;
  }

  private static HTTPConnectionHandlerCfg newConfig(boolean useSSL, int listenPort, String... listenAddresses)
      throws Exception
  {
    final List<String> ldif = new ArrayList<>();
    Collections.addAll(ldif,
        "dn: cn=HTTP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-http-connection-handler",
        "cn: HTTP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.http.HTTPConnectionHandler",
        "ds-cfg-enabled: true");
    for (String listenAddress : listenAddresses)
    {
      ldif.add("ds-cfg-listen-address: " + listenAddress);
    }
    Collections.addAll(ldif,
        "ds-cfg-listen-port: " + listenPort,
        "ds-cfg-accept-backlog: 128",
        "ds-cfg-keep-stats: false",
        "ds-cfg-use-tcp-keep-alive: true",
        "ds-cfg-use-tcp-no-delay: true",
        "ds-cfg-allow-tcp-reuse-address: true",
        "ds-cfg-max-request-size: 5 megabytes",
        "ds-cfg-buffer-size: 4096 bytes",
        "ds-cfg-max-blocked-write-time-limit: 2 minutes",
        "ds-cfg-use-ssl: " + useSSL,
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-ssl-client-auth-policy: optional",
        "ds-cfg-ssl-cert-nickname: server-cert");
    Entry handlerEntry = TestCaseUtils.makeEntry(ldif.toArray(new String[0]));
    return InitializationUtils.getConfiguration(HTTPConnectionHandlerCfgDefn.getInstance(), handlerEntry);
  }

  private static InetAddress loopback() throws UnknownHostException
  {
    return InetAddress.getByName("127.0.0.1");
  }

  private static void stop(HTTPConnectionHandler handler) throws InterruptedException
  {
    handler.processServerShutdown(STOP_REASON);
    handler.finalizeConnectionHandler(STOP_REASON);
    handler.join(10000);
    assertFalse(handler.isAlive(), "the connection handler thread is still running");
  }
}
