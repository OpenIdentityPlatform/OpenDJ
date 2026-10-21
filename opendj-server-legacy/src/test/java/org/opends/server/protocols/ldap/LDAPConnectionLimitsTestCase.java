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
package org.opends.server.protocols.ldap;

import static org.opends.messages.ProtocolMessages.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.reactive.LDAPClientConnection2;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.tools.LDAPReader;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The limits which keep unauthenticated clients from holding every connection of the server: an LDAP connection
 * handler closes a connection on which no complete request arrives within {@code first-request-time-limit}, and rejects
 * a connection beyond {@code max-client-connections-per-address} from one address; the administration connector stays
 * reachable when the regular connections reach {@code max-allowed-client-connections}; and a new server ships with both
 * server-wide limits set.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class LDAPConnectionLimitsTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Stopped by the limits test.");
  private static final String NOTICE_OF_DISCONNECTION_OID = "1.3.6.1.4.1.1466.20036";
  private static final long TIMEOUT_MS = 10000;
  /** How long a connection which must stay open is watched. */
  private static final int STAYS_OPEN_MS = 2500;
  private static final long FIRST_REQUEST_TIME_LIMIT_MS = 1000;
  /** An anonymous simple bind request, message ID 1. */
  private static final byte[] ANONYMOUS_BIND = {
    0x30, 0x0c, 0x02, 0x01, 0x01, 0x60, 0x07, 0x02, 0x01, 0x03, 0x04, 0x00, (byte) 0x80, 0x00 };

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @Test
  public void silentConnectionIsClosedOnceTheFirstRequestTimeLimitIsSpent() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 1 seconds"));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      final long connected = System.currentTimeMillis();
      assertNoticeOfDisconnection(
          client, INFO_CONNHANDLER_FIRST_REQUEST_TIME_LIMIT_EXCEEDED.get(FIRST_REQUEST_TIME_LIMIT_MS));
      assertTrue(System.currentTimeMillis() - connected >= FIRST_REQUEST_TIME_LIMIT_MS - 100,
          "the connection was closed before the limit was spent");
      awaitClientConnections(handler, 0);
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void partOfARequestIsNotAFirstRequest() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 1 seconds"));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      final OutputStream out = client.getOutputStream();
      out.write(ANONYMOUS_BIND, 0, ANONYMOUS_BIND.length - 3);
      out.flush();
      assertNoticeOfDisconnection(
          client, INFO_CONNHANDLER_FIRST_REQUEST_TIME_LIMIT_EXCEEDED.get(FIRST_REQUEST_TIME_LIMIT_MS));
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void connectionWhichSentARequestOutlivesTheFirstRequestTimeLimit() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 1 seconds"));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      bindAnonymously(client);
      assertStaysOpen(client);
      assertEquals(handler.getClientConnections().size(), 1);
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void zeroFirstRequestTimeLimitKeepsASilentConnection() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 0 seconds"));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      awaitClientConnections(handler, 1);
      assertStaysOpen(client);
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * A first request time limit still pending when the handler is finalized must not keep its finalizer thread, which
   * the server shutdown waits for, alive until the limit is spent.
   */
  @Test
  public void pendingFirstRequestTimeLimitDoesNotOutliveTheHandler() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 60 seconds"));
    final Thread finalizer = finalizerThread(handler);
    try (Socket client = new Socket("127.0.0.1", port))
    {
      awaitClientConnections(handler, 1);
      awaitFirstRequestTimer(finalizerQueue(handler));
      stop(handler);
      finalizer.join(TIMEOUT_MS);
      assertFalse(finalizer.isAlive(), "the finalizer thread waits for the first request time limit");
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * The first request time limit of a connection leaves the finalizer queue as soon as the first request arrives or the
   * connection ends, rather than holding the connection until the limit is spent.
   */
  @Test
  public void firstRequestTimeLimitIsCancelledByTheFirstRequestAndByTheEndOfTheConnection() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 60 seconds"));
    try
    {
      final Queue<Runnable> queue = finalizerQueue(handler);
      try (Socket client = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 1);
        final RunnableScheduledFuture<?> timer = awaitFirstRequestTimer(queue);
        bindAnonymously(client);
        awaitTimerCancelled(queue, timer, "the first request");
      }
      awaitClientConnections(handler, 0);
      final RunnableScheduledFuture<?> timer;
      try (Socket client = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 1);
        timer = awaitFirstRequestTimer(queue);
      }
      awaitTimerCancelled(queue, timer, "the end of the connection");
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * A connection accepted after the handler is finalized, before its listener stops, is still released when it
   * closes: the handler has no finalizer left to time it.
   */
  @Test
  public void connectionAcceptedWhileTheHandlerIsFinalizedIsReleasedWhenItCloses() throws Exception
  {
    for (int attempt = 0; attempt < 5; attempt++)
    {
      final int port = TestCaseUtils.findFreePort();
      final LDAPConnectionHandler2 handler = start(configuration(port));
      try
      {
        // The listener stops only when run() next wakes up, up to a second later.
        handler.finalizeConnectionHandler(STOP_REASON);
        try (Socket client = new Socket("127.0.0.1", port))
        {
          if (!awaitClientConnectionsQuietly(handler, 1))
          {
            continue;
          }
        }
        catch (IOException listenerAlreadyStopped)
        {
          continue;
        }
        awaitClientConnections(handler, 0);
        return;
      }
      finally
      {
        handler.join(TIMEOUT_MS);
      }
    }
    throw new SkipException("the listener stopped before a connection could reach the finalized handler");
  }

  /**
   * A finalized handler has no finalizer left: a connection it accepts before its listener stops gets no first request
   * time limit, rather than failing its accept.
   */
  @Test
  public void finalizedHandlerSetsNoFirstRequestTimeLimit() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-first-request-time-limit: 60 seconds"));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      awaitClientConnections(handler, 1);
      final LDAPClientConnection2 connection =
          (LDAPClientConnection2) handler.getClientConnections().iterator().next();
      handler.finalizeConnectionHandler(STOP_REASON);
      final Method limit =
          LDAPConnectionHandler2.class.getDeclaredMethod("limitTimeToFirstRequest", LDAPClientConnection2.class);
      limit.setAccessible(true);
      limit.invoke(handler, connection);
    }
    finally
    {
      handler.join(TIMEOUT_MS);
    }
  }

  @Test
  public void handlerLimitsDefaultToAMinuteForTheFirstRequestAnd1024ConnectionsPerAddress() throws Exception
  {
    final LDAPConnectionHandlerCfg config = configuration(TestCaseUtils.findFreePort());
    assertEquals(config.getFirstRequestTimeLimit(), 60000L);
    assertEquals(config.getMaxClientConnectionsPerAddress(), 1024);
  }

  /**
   * The administration connector is not bounded by max-allowed-client-connections, so its own limits are what keep a
   * client from holding it.
   */
  @Test
  public void administrationConnectorLimitsAMinuteForTheFirstRequestAnd1024ConnectionsPerAddress() throws Exception
  {
    final LDAPConnectionHandlerCfg config = currentConfig(administrationConnectionHandler());
    assertEquals(config.getFirstRequestTimeLimit(), 60000L);
    assertEquals(config.getMaxClientConnectionsPerAddress(), 1024);
  }

  @Test
  public void connectionBeyondTheLimitOfItsAddressIsRejected() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 2"));
    try (Socket first = new Socket("127.0.0.1", port);
        Socket second = new Socket("127.0.0.1", port))
    {
      awaitClientConnections(handler, 2);
      try (Socket third = new Socket("127.0.0.1", port))
      {
        assertNoticeOfDisconnection(third, ERR_CONNHANDLER_CLIENT_ADDRESS_LIMIT_REACHED.get(
            "127.0.0.1:" + third.getLocalPort(), "127.0.0.1:" + port, 2));
      }
      assertEquals(handler.getClientConnections().size(), 2);
      assertStaysOpen(first);
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void closedConnectionGivesBackItsPlaceToItsAddress() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 2"));
    try (Socket first = new Socket("127.0.0.1", port))
    {
      try (Socket second = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 2);
      }
      awaitClientConnections(handler, 1);
      // The closed connection leaves the handler before it gives back its place.
      awaitPlacesHeld(handler, 1);
      try (Socket third = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 2);
        bindAnonymously(third);
      }
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void rejectedConnectionDoesNotTakeAPlaceOfItsAddress() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 1"));
    try
    {
      try (Socket first = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 1);
        for (int i = 0; i < 3; i++)
        {
          try (Socket rejected = new Socket("127.0.0.1", port))
          {
            rejected.setSoTimeout((int) TIMEOUT_MS);
            assertNotNull(new LDAPReader(rejected).readMessage(), "the rejected connection got no notice");
          }
        }
      }
      // Only the first connection held a place: once it is closed, a new one is accepted.
      awaitClientConnections(handler, 0);
      try (Socket next = new Socket("127.0.0.1", port))
      {
        bindAnonymously(next);
      }
    }
    finally
    {
      stop(handler);
    }
  }

  /** A connection which takes a place and is then rejected by a post-connect plugin gives the place back. */
  @Test
  public void connectionRejectedAfterTakingAPlaceGivesItBack() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 1"));
    final LocalizableMessage rejection = LocalizableMessage.raw("Rejected by the limits test plugin.");
    final PostConnectPlugin plugin = new PostConnectPlugin(handler,
        connection -> PluginResult.PostConnect.disconnectClient(DisconnectReason.CLOSED_BY_PLUGIN, true, rejection));
    DirectoryServer.registerInternalPlugin(plugin);
    try
    {
      for (int i = 0; i < 2; i++)
      {
        try (Socket rejected = new Socket("127.0.0.1", port))
        {
          assertNoticeOfDisconnection(rejected, rejection);
        }
        // The notice goes out before the rejection unwinds to the place being given back.
        awaitPlacesHeld(handler, 0);
      }
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(plugin);
      stop(handler);
    }
  }

  /** An error, not only an exception, thrown once the connection took its place gives the place back. */
  @Test
  public void connectionFailingWithAnErrorGivesBackItsPlace() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 1"));
    final PostConnectPlugin plugin = new PostConnectPlugin(handler, connection -> {
      throw new NoClassDefFoundError("Thrown by the limits test plugin.");
    });
    DirectoryServer.registerInternalPlugin(plugin);
    try
    {
      try (Socket failed = new Socket("127.0.0.1", port))
      {
        failed.setSoTimeout((int) TIMEOUT_MS);
        assertEquals(failed.getInputStream().read(), -1, "the failed connection stayed open");
      }
      awaitPlacesHeld(handler, 0);
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(plugin);
      stop(handler);
    }
  }

  /**
   * A connection which a post-connect plugin ends, and still lets through, ends before the handler listens to it: it
   * still leaves the handler and gives back its place.
   */
  @Test
  public void connectionEndedBeforeTheHandlerListensToItGivesBackItsPlace() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 1"));
    final PostConnectPlugin plugin = new PostConnectPlugin(handler, connection -> {
      connection.disconnect(DisconnectReason.CLOSED_BY_PLUGIN, false, null);
      return PluginResult.PostConnect.continueConnectProcessing();
    });
    DirectoryServer.registerInternalPlugin(plugin);
    try
    {
      try (Socket ended = new Socket("127.0.0.1", port))
      {
        ended.setSoTimeout((int) TIMEOUT_MS);
        assertEquals(ended.getInputStream().read(), -1, "the ended connection stayed open");
      }
      awaitClientConnections(handler, 0);
      awaitPlacesHeld(handler, 0);
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(plugin);
      stop(handler);
    }
  }

  /** A first request time limit set on a connection which has ended already is cancelled at once. */
  @Test
  public void firstRequestTimeLimitSetOnAnEndedConnectionIsCancelled() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port));
    try
    {
      final LDAPClientConnection2 connection;
      try (Socket client = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 1);
        connection = (LDAPClientConnection2) handler.getClientConnections().iterator().next();
      }
      awaitClientConnections(handler, 0);
      final Method setTimer = LDAPClientConnection2.class.getDeclaredMethod("setFirstRequestTimer", Future.class);
      setTimer.setAccessible(true);
      final CompletableFuture<Void> timer = new CompletableFuture<>();
      setTimer.invoke(connection, timer);
      assertTrue(timer.isCancelled(), "a time limit set on an ended connection was not cancelled");
    }
    finally
    {
      stop(handler);
    }
  }

  /** Connections are counted whatever the limit, so a limit set on a running handler covers those already open. */
  @Test
  public void limitPerAddressSetAtRuntimeCoversTheOpenConnections() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 0"));
    try (Socket first = new Socket("127.0.0.1", port);
        Socket second = new Socket("127.0.0.1", port))
    {
      awaitClientConnections(handler, 2);
      handler.applyConfigurationChange(configuration(port, "ds-cfg-max-client-connections-per-address: 2"));
      try (Socket third = new Socket("127.0.0.1", port))
      {
        assertNoticeOfDisconnection(third, ERR_CONNHANDLER_CLIENT_ADDRESS_LIMIT_REACHED.get(
            "127.0.0.1:" + third.getLocalPort(), "127.0.0.1:" + port, 2));
      }
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void zeroLimitPerAddressAcceptsEveryConnection() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler =
        start(configuration(port, "ds-cfg-max-client-connections-per-address: 0"));
    final List<Socket> clients = new ArrayList<>();
    try
    {
      for (int i = 0; i < 5; i++)
      {
        clients.add(new Socket("127.0.0.1", port));
      }
      awaitClientConnections(handler, 5);
    }
    finally
    {
      for (Socket client : clients)
      {
        client.close();
      }
      stop(handler);
    }
  }

  /**
   * Once the regular connections reach max-allowed-client-connections, the administrator must still be able to
   * connect to the administration connector, or nothing short of a restart can raise the limit.
   */
  @Test
  public void administrationConnectorIsReachableWhenTheConnectionLimitIsReached() throws Exception
  {
    final List<Socket> clients = new ArrayList<>();
    // The limit the server runs with, 0 when it has none, to set back afterwards.
    final long configured = Math.max(DirectoryServer.getCoreConfigManager().getMaxAllowedConnections(), 0);
    final long before = DirectoryServer.getCurrentConnections();
    final long limit = before + 3;
    TestCaseUtils.dsconfig("set-global-configuration-prop", "--set", "max-allowed-client-connections:" + limit);
    try
    {
      // The connection of dsconfig, like any connection still closing, is uncounted asynchronously: fill the limit
      // only once it is gone, or its uncounting would leave a place for the connection which must be rejected.
      awaitCurrentConnectionsAtMost(before);
      while (DirectoryServer.getCurrentConnections() < limit)
      {
        final Socket client = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
        clients.add(client);
        bindAnonymously(client);
      }
      try (Socket rejected = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort()))
      {
        rejected.setSoTimeout((int) TIMEOUT_MS);
        final LDAPMessage notice = new LDAPReader(rejected).readMessage();
        assertNotNull(notice, "the connection beyond the limit got no notice");
        assertEquals(notice.getExtendedResponseProtocolOp().getOID(), NOTICE_OF_DISCONNECTION_OID,
            "the regular connections did not reach the limit");
      }

      try (SSLSocket admin = (SSLSocket) trustAll().getSocketFactory().createSocket(
          "127.0.0.1", TestCaseUtils.getServerAdminPort()))
      {
        admin.startHandshake();
        bindAnonymously(admin);
        // Exempt from the limit, but counted.
        assertEquals(DirectoryServer.getCurrentConnections(), limit + 1, "connections counted with the administrator's");
      }
      catch (IOException e)
      {
        fail("the administration connector refused the connection once the limit was reached", e);
      }
    }
    finally
    {
      for (Socket client : clients)
      {
        client.close();
      }
      final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
      while (DirectoryServer.getCurrentConnections() >= limit && System.currentTimeMillis() < deadline)
      {
        Thread.sleep(50);
      }
      TestCaseUtils.dsconfig("set-global-configuration-prop", "--set", "max-allowed-client-connections:" + configured);
    }
  }

  @Test
  public void newServerLimitsTheConnectionsAndTheirIdleTime() throws Exception
  {
    final File template = new File(TestCaseUtils.getBuildRoot(), "resource/config/config.ldif");
    final List<String> lines = Files.readAllLines(template.toPath(), StandardCharsets.UTF_8);
    assertTrue(lines.contains("ds-cfg-max-allowed-client-connections: 4096"),
        "max-allowed-client-connections of " + template);
    assertTrue(lines.contains("ds-cfg-idle-time-limit: 600 seconds"), "idle-time-limit of " + template);
  }

  /** A server without a value, for example after {@code dsconfig --reset}, falls back to the same limits. */
  @Test
  public void serverWithoutAValueLimitsTheConnectionsAndTheirIdleTime() throws Exception
  {
    final GlobalCfgDefn global = GlobalCfgDefn.getInstance();
    assertEquals(defaultValues(global.getMaxAllowedClientConnectionsPropertyDefinition()), Arrays.asList("4096"));
    assertEquals(defaultValues(global.getIdleTimeLimitPropertyDefinition()), Arrays.asList("600 seconds"));
  }

  private static List<String> defaultValues(PropertyDefinition<?> property)
  {
    return new ArrayList<>(((DefinedDefaultBehaviorProvider<?>) property.getDefaultBehaviorProvider())
        .getDefaultValues());
  }

  /** Runs the given post-connect phase on every connection to the given handler. */
  private static final class PostConnectPlugin extends InternalDirectoryServerPlugin
  {
    private final LDAPConnectionHandler2 handler;
    private final Function<ClientConnection, PluginResult.PostConnect> postConnect;

    private PostConnectPlugin(LDAPConnectionHandler2 handler,
        Function<ClientConnection, PluginResult.PostConnect> postConnect)
    {
      super(DN.valueOf("cn=Limits Test Post Connect Plugin"), EnumSet.of(PluginType.POST_CONNECT), false);
      this.handler = handler;
      this.postConnect = postConnect;
    }

    @Override
    public PluginResult.PostConnect doPostConnect(ClientConnection clientConnection)
    {
      if (clientConnection.getConnectionHandler() == handler)
      {
        return postConnect.apply(clientConnection);
      }
      return PluginResult.PostConnect.continueConnectProcessing();
    }
  }

  private static LDAPConnectionHandler2 administrationConnectionHandler()
  {
    for (ConnectionHandler<?> handler : DirectoryServer.getConnectionHandlers())
    {
      if (handler.isAdminConnectionHandler())
      {
        return (LDAPConnectionHandler2) handler;
      }
    }
    throw new AssertionError("no administration connector");
  }

  private static LDAPConnectionHandlerCfg currentConfig(LDAPConnectionHandler2 handler) throws Exception
  {
    final Field field = LDAPConnectionHandler2.class.getDeclaredField("currentConfig");
    field.setAccessible(true);
    return (LDAPConnectionHandlerCfg) field.get(handler);
  }

  private static Queue<Runnable> finalizerQueue(LDAPConnectionHandler2 handler) throws Exception
  {
    return ((ThreadPoolExecutor) finalizer(handler)).getQueue();
  }

  /** Returns the thread of the connection finalizer, which runs its single-threaded executor. */
  private static Thread finalizerThread(LDAPConnectionHandler2 handler) throws Exception
  {
    return finalizer(handler).submit(Thread::currentThread).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  private static ExecutorService finalizer(LDAPConnectionHandler2 handler) throws Exception
  {
    final Field field = LDAPConnectionHandler2.class.getDeclaredField("connectionFinalizer");
    field.setAccessible(true);
    return (ExecutorService) field.get(handler);
  }

  /** Waits until the addresses of the clients hold the given number of places in all. */
  private static void awaitPlacesHeld(LDAPConnectionHandler2 handler, int expected) throws Exception
  {
    final Field field = LDAPConnectionHandler2.class.getDeclaredField("connectionsPerAddress");
    field.setAccessible(true);
    final Map<?, ?> connectionsPerAddress = (Map<?, ?>) field.get(handler);
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (connectionsPerAddress.values().stream().mapToInt(held -> (Integer) held).sum() != expected)
    {
      assertTrue(System.currentTimeMillis() < deadline,
          "places held: " + connectionsPerAddress + ", not " + expected + " in all");
      Thread.sleep(50);
    }
  }

  /**
   * Waits for the first request time limit of the only connection in the finalizer queue. The queue cannot be counted
   * instead: it does not hold the periodic task of the finalizer while that task runs.
   */
  private static RunnableScheduledFuture<?> awaitFirstRequestTimer(Queue<Runnable> queue) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (true)
    {
      for (Runnable task : queue)
      {
        final RunnableScheduledFuture<?> scheduled = (RunnableScheduledFuture<?>) task;
        if (!scheduled.isPeriodic())
        {
          return scheduled;
        }
      }
      assertTrue(System.currentTimeMillis() < deadline, "no first request time limit in the finalizer queue");
      Thread.sleep(50);
    }
  }

  /** Waits until the given first request time limit is cancelled and out of the finalizer queue. */
  private static void awaitTimerCancelled(Queue<Runnable> queue, RunnableScheduledFuture<?> timer, String by)
      throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (!timer.isCancelled() || queue.contains(timer))
    {
      assertTrue(System.currentTimeMillis() < deadline, by + " did not cancel the first request time limit, or "
          + "left it in the finalizer queue: cancelled " + timer.isCancelled());
      Thread.sleep(50);
    }
  }

  private static void awaitCurrentConnectionsAtMost(long expected) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (DirectoryServer.getCurrentConnections() > expected)
    {
      assertTrue(System.currentTimeMillis() < deadline,
          "the server counts " + DirectoryServer.getCurrentConnections() + " connections, more than " + expected);
      Thread.sleep(10);
    }
  }

  private static void bindAnonymously(Socket client) throws Exception
  {
    final OutputStream out = client.getOutputStream();
    out.write(ANONYMOUS_BIND);
    out.flush();
    client.setSoTimeout((int) TIMEOUT_MS);
    final LDAPMessage response = new LDAPReader(client).readMessage();
    assertNotNull(response, "the connection was closed instead of answering the bind");
    assertEquals(response.getBindResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS, "bind result");
  }

  /** Checks that the server neither writes to the connection nor closes it for a while. */
  private static void assertStaysOpen(Socket client) throws Exception
  {
    client.setSoTimeout(STAYS_OPEN_MS);
    try
    {
      final int read = client.getInputStream().read();
      fail("the connection did not stay open: read " + read);
    }
    catch (SocketTimeoutException expected)
    {
      // Neither data nor the end of the stream within the time.
    }
  }

  /** Reads the next message from the client, checks it is the expected notice of disconnection, then the end. */
  private static void assertNoticeOfDisconnection(Socket client, LocalizableMessage reason) throws Exception
  {
    client.setSoTimeout((int) TIMEOUT_MS);
    final LDAPMessage message;
    try
    {
      message = new LDAPReader(client).readMessage();
    }
    catch (SocketTimeoutException e)
    {
      throw new AssertionError("the connection is still open", e);
    }
    assertNotNull(message, "the connection was closed without a notice of disconnection");
    final ExtendedResponseProtocolOp notice = message.getExtendedResponseProtocolOp();
    assertEquals(notice.getOID(), NOTICE_OF_DISCONNECTION_OID);
    assertEquals(String.valueOf(notice.getErrorMessage()), reason.toString(), "diagnostic message");
    assertEquals(client.getInputStream().read(), -1, "the connection stayed open after the notice");
  }

  private static boolean awaitClientConnectionsQuietly(LDAPConnectionHandler2 handler, int expected)
      throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (handler.getClientConnections().size() != expected)
    {
      if (System.currentTimeMillis() >= deadline)
      {
        return false;
      }
      Thread.sleep(50);
    }
    return true;
  }

  private static void awaitClientConnections(LDAPConnectionHandler2 handler, int expected) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (handler.getClientConnections().size() != expected)
    {
      assertTrue(System.currentTimeMillis() < deadline,
          "the handler holds " + handler.getClientConnections().size() + " connections, not " + expected);
      Thread.sleep(50);
    }
  }

  private static SSLContext trustAll() throws Exception
  {
    final SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, new TrustManager[] { new X509TrustManager()
    {
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
      {
        // The test trusts the self-signed certificate of the administration connector.
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
      {
        // The test trusts the self-signed certificate of the administration connector.
      }

      @Override
      public X509Certificate[] getAcceptedIssuers()
      {
        return new X509Certificate[0];
      }
    } }, null);
    return context;
  }

  private static LDAPConnectionHandlerCfg configuration(int port, String... limits) throws Exception
  {
    final List<String> lines = new ArrayList<>();
    lines.add("dn: cn=Limits Test Handler,cn=Connection Handlers,cn=config");
    lines.add("objectClass: top");
    lines.add("objectClass: ds-cfg-connection-handler");
    lines.add("objectClass: ds-cfg-ldap-connection-handler");
    lines.add("cn: Limits Test Handler");
    lines.add("ds-cfg-java-class: " + LDAPConnectionHandler2.class.getName());
    lines.add("ds-cfg-enabled: true");
    lines.add("ds-cfg-listen-address: 127.0.0.1");
    lines.add("ds-cfg-listen-port: " + port);
    lines.add("ds-cfg-keep-stats: false");
    lines.add("ds-cfg-use-ssl: false");
    lines.add("ds-cfg-allow-start-tls: false");
    lines.add("ds-cfg-allow-ldap-v2: false");
    lines.add("ds-cfg-send-rejection-notice: true");
    for (String limit : limits)
    {
      lines.add(limit);
    }
    final Entry entry = TestCaseUtils.makeEntry(lines.toArray(new String[0]));
    return InitializationUtils.getConfiguration(LDAPConnectionHandlerCfgDefn.getInstance(), entry);
  }

  private static LDAPConnectionHandler2 start(LDAPConnectionHandlerCfg config) throws Exception
  {
    final LDAPConnectionHandler2 handler = new LDAPConnectionHandler2();
    handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(), config);
    handler.start();
    return handler;
  }

  private static void stop(LDAPConnectionHandler2 handler) throws InterruptedException
  {
    if (!handler.isAlive())
    {
      return;
    }
    handler.processServerShutdown(STOP_REASON);
    handler.finalizeConnectionHandler(STOP_REASON);
    handler.join(TIMEOUT_MS);
    assertFalse(handler.isAlive(), "the connection handler thread is still running");
  }
}
