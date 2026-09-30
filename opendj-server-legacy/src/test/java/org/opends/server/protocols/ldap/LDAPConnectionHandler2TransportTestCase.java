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

import static org.opends.messages.CoreMessages.INFO_CONNHANDLER_CLOSED_BY_SHUTDOWN;
import static org.testng.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.tools.LDAPReader;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * {@link LDAPConnectionHandler2} serves its connections with a transport of its own, built from its configuration:
 * {@code use-tcp-keep-alive} and {@code use-tcp-no-delay} reach every accepted socket, {@code buffer-size} is the write
 * buffer of every connection, {@code allow-tcp-reuse-address} reaches the listen socket and {@code num-request-handlers}
 * is the number of selector threads. Stopping the handler leaves its connections open until they are closed, or until
 * the server shuts down, which ends them with a notice of disconnection; then its threads stop.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class LDAPConnectionHandler2TransportTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Stopped by the transport test.");
  private static final String NOTICE_OF_DISCONNECTION_OID = "1.3.6.1.4.1.1466.20036";
  private static final String SELECTORS_PROPERTY = "org.forgerock.opendj.transport.selectors";
  /** Unlike any socket buffer size a system would choose. */
  private static final int WRITE_BUFFER_SIZE = 12345;
  private static final long TIMEOUT_MS = 10000;
  /** How long the connection of a stopped handler must stay open. */
  private static final long KEEPS_OPEN_MS = 3000;
  /** What is written at a time to fill the socket buffers of both ends and then the write queue of the server. */
  private static final int UNREAD_CHUNK = 64 * 1024;
  /** Far more than any socket buffers hold. */
  private static final int MAX_UNREAD_BYTES = 64 * 1024 * 1024;
  /**
   * How soon a drain ends once its last connection is closed: well below the 2 s it waits at most for connections to
   * close, which the notice queued behind unread data takes about 1.3 s to reach.
   */
  private static final long CLOSED_DRAIN_ENDS_MS = 1000;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @DataProvider
  public Object[][] socketOptions()
  {
    return new Object[][] { { true, true }, { true, false }, { false, true }, { false, false } };
  }

  @Test(dataProvider = "socketOptions")
  public void acceptedSocketUsesTheConfiguredOptions(boolean keepAlive, boolean noDelay) throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, keepAlive, noDelay, true, 2));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      final Socket accepted = ((SocketChannel) acceptedConnection(handler).getChannel()).socket();
      assertEquals(accepted.getKeepAlive(), keepAlive, "SO_KEEPALIVE");
      assertEquals(accepted.getTcpNoDelay(), noDelay, "TCP_NODELAY");
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void connectionIsServedByTheTransportOfItsHandlerWithTheConfiguredWriteBuffer() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    try (Socket client = new Socket("127.0.0.1", port))
    {
      final TCPNIOConnection accepted = acceptedConnection(handler);
      assertSame(accepted.getTransport(), transport(handler), "the connection is not served by its handler's transport");
      assertEquals(accepted.getWriteBufferSize(), WRITE_BUFFER_SIZE);
    }
    finally
    {
      stop(handler);
    }
  }

  @DataProvider
  public Object[][] reuseAddress()
  {
    return new Object[][] { { true }, { false } };
  }

  @Test(dataProvider = "reuseAddress")
  public void listenSocketUsesTheConfiguredReuseAddress(boolean reuseAddress) throws Exception
  {
    final LDAPConnectionHandler2 handler =
        start(configuration(freePort(reuseAddress), true, true, reuseAddress, 2));
    try
    {
      final Collection<?> serverConnections = (Collection<?>) field(field(handler, "listener"), "impl", "serverConnections");
      assertFalse(serverConnections.isEmpty(), "the handler does not listen");
      for (Object serverConnection : serverConnections)
      {
        final ServerSocketChannel channel = (ServerSocketChannel) ((TCPNIOServerConnection) serverConnection).getChannel();
        assertEquals(channel.socket().getReuseAddress(), reuseAddress, "SO_REUSEADDR");
      }
    }
    finally
    {
      stop(handler);
    }
  }

  @Test
  public void selectorThreadsFollowNumRequestHandlers() throws Exception
  {
    assertSelectorThreads(configuration(TestCaseUtils.findFreePort(), true, true, true, 3), 3);
  }

  /** The system property that sized the transport shared by every listener still applies when nothing is set. */
  @Test
  public void selectorThreadsFollowTheSelectorsPropertyWhenNumRequestHandlersIsUnset() throws Exception
  {
    final String saved = System.getProperty(SELECTORS_PROPERTY);
    System.setProperty(SELECTORS_PROPERTY, "13");
    try
    {
      assertSelectorThreads(configuration(TestCaseUtils.findFreePort(), true, true, true, null), 13);
    }
    finally
    {
      restore(saved);
    }
  }

  @Test
  public void numRequestHandlersTakesPrecedenceOverTheSelectorsProperty() throws Exception
  {
    final String saved = System.getProperty(SELECTORS_PROPERTY);
    System.setProperty(SELECTORS_PROPERTY, "13");
    try
    {
      assertSelectorThreads(configuration(TestCaseUtils.findFreePort(), true, true, true, 3), 3);
    }
    finally
    {
      restore(saved);
    }
  }

  /**
   * Decisive only on hosts with 6 processors or more: below that the count is 2, which a constant would give too.
   */
  @Test
  public void selectorThreadsAreChosenFromTheProcessorsWhenNothingIsSet() throws Exception
  {
    final String saved = System.getProperty(SELECTORS_PROPERTY);
    System.clearProperty(SELECTORS_PROPERTY);
    try
    {
      assertSelectorThreads(configuration(TestCaseUtils.findFreePort(), true, true, true, null),
          Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }
    finally
    {
      restore(saved);
    }
  }

  /**
   * Stopping the handler, as disabling, deleting or restarting it does, leaves the connections it has accepted open:
   * its transport keeps serving them, and is shut down once the last of them is closed.
   */
  @Test
  public void stoppedHandlerKeepsItsConnectionsUntilTheyAreClosed() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    final String threadPrefix = selectorThreadPrefix(handler);
    try (Socket client = new Socket("127.0.0.1", port))
    {
      acceptedConnection(handler);
      stop(handler);

      client.setSoTimeout((int) KEEPS_OPEN_MS);
      try
      {
        final int read = client.getInputStream().read();
        fail("the connection of the stopped handler was " + (read == -1 ? "closed" : "written to"));
      }
      catch (SocketTimeoutException expected)
      {
        // still open
      }
      assertFalse(threadsNamed(threadPrefix).isEmpty(), "the transport stopped while a connection was open");
    }
    finally
    {
      stop(handler);
    }
    assertThreadsStop(threadPrefix);
  }

  /** The server shutting down ends the connections a stopped handler left open, and shuts its transport down. */
  @Test
  public void serverShutdownEndsTheConnectionsOfAStoppedHandler() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    final String threadPrefix = selectorThreadPrefix(handler);
    try (Socket client = new Socket("127.0.0.1", port))
    {
      acceptedConnection(handler);
      stop(handler);
      final ServerShutdownListener drain = onlyDrain(handler);
      assertTrue(((Collection<?>) field(DirectoryServer.getInstance(), "shutdownListeners")).contains(drain),
          "the drain is not registered as a shutdown listener");

      drain.processServerShutdown(STOP_REASON);

      assertNoticeOfDisconnection(client, STOP_REASON);
    }
    finally
    {
      stop(handler);
    }
    assertThreadsStop(threadPrefix);
  }

  /**
   * A notice of disconnection queued behind data the client has not read yet still reaches the client: the transport
   * is not shut down before the client has read up to it.
   */
  @Test
  public void serverShutdownDeliversANoticeQueuedBehindUnreadData() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    try (Socket client = new Socket())
    {
      // Keeps what the client side holds small, so that the backlog is quick to read once the client reads.
      client.setReceiveBufferSize(8 * 1024);
      client.connect(new InetSocketAddress("127.0.0.1", port));
      final TCPNIOConnection accepted = acceptedConnection(handler);
      final TCPNIOTransport transport = transport(handler);
      // Written below the LDAP filters, as raw bytes the client will skip, until the socket buffers are full and some
      // are left waiting in the write queue.
      final byte[] chunk = new byte[UNREAD_CHUNK];
      int unread = 0;
      while (accepted.getAsyncWriteQueue().spaceInBytes() == 0)
      {
        assertTrue(unread < MAX_UNREAD_BYTES, "the socket buffers took " + unread + " bytes");
        transport.getAsyncQueueIO().getWriter().write(accepted, Buffers.wrap(transport.getMemoryManager(), chunk), null);
        unread += chunk.length;
        // Let the selector move what it can into the socket.
        Thread.sleep(50);
      }
      stop(handler);
      final ServerShutdownListener drain = onlyDrain(handler);

      final Thread shutdown = new Thread(() -> drain.processServerShutdown(STOP_REASON));
      shutdown.start();
      // Let the notice join the queue while the client reads nothing.
      Thread.sleep(300);

      client.setSoTimeout((int) TIMEOUT_MS);
      final InputStream in = client.getInputStream();
      final byte[] buffer = new byte[64 * 1024];
      for (int left = unread; left > 0;)
      {
        final int read = in.read(buffer, 0, Math.min(buffer.length, left));
        assertTrue(read > 0, "the connection ended with " + left + " unread bytes still queued");
        left -= read;
      }
      assertNoticeOfDisconnection(client, STOP_REASON);
      // The client has read everything and the server has closed: the drain must not wait out its bound.
      shutdown.join(CLOSED_DRAIN_ENDS_MS);
      assertFalse(shutdown.isAlive(), "the drain is still waiting for a closed connection");
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * The server shutting down, here for an in-core restart, ends the connections of a handler that is still listening
   * with a notice of disconnection. The handler stops its listener on its own thread, which may wake before or after
   * the next server instance is current: {@link #drainEndsTheConnectionsWhenTheServerOfItsHandlerIsShuttingDown()}
   * covers the latter.
   */
  @Test
  public void serverRestartEndsTheConnectionsOfAListeningHandler() throws Exception
  {
    try (Socket client = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort()))
    {
      awaitServerConnection(client.getLocalPort());
      // Read while the server restarts: macOS resets a closed loopback connection after net.inet.tcp.fin_timeout
      // (60 s), and drops what the client has not read yet.
      final ExecutorService reader = Executors.newSingleThreadExecutor();
      try
      {
        final Future<?> notice = reader.submit(() -> {
          assertNoticeOfDisconnection(client, INFO_CONNHANDLER_CLOSED_BY_SHUTDOWN.get());
          return null;
        });
        TestCaseUtils.restartServer();
        notice.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      }
      finally
      {
        reader.shutdownNow();
      }
    }
  }

  /**
   * A handler finalized while its server runs, as disabling or deleting it does, may stop its listener after that
   * server has begun shutting down, and even after the next server instance is current. The drain then ends the
   * connections with a notice of disconnection as the server shutdown would have, and registers with no server.
   */
  @Test
  public void drainEndsTheConnectionsWhenTheServerOfItsHandlerIsShuttingDown() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    final String threadPrefix = selectorThreadPrefix(handler);
    try (Socket client = new Socket("127.0.0.1", port))
    {
      acceptedConnection(handler);
      // The server of the handler is shutting down, and the current instance is another one that is not.
      final Constructor<DirectoryServer> newServer = DirectoryServer.class.getDeclaredConstructor();
      newServer.setAccessible(true);
      final DirectoryServer previous = newServer.newInstance();
      setField(previous, "shuttingDown", true);
      setField(handler, "server", previous);

      stop(handler);

      assertNoticeOfDisconnection(client, INFO_CONNHANDLER_CLOSED_BY_SHUTDOWN.get());
      assertTrue(((Collection<?>) field(handler, "drains")).isEmpty(), "the drain is still serving the connections");
      for (Object listener : (Collection<?>) field(DirectoryServer.getInstance(), "shutdownListeners"))
      {
        assertNotEquals(((ServerShutdownListener) listener).getShutdownListenerName(),
            "Transport drain of " + handler.getConnectionHandlerName(), "the drain registered with the current server");
      }
    }
    finally
    {
      stop(handler);
    }
    assertThreadsStop(threadPrefix);
  }

  /**
   * A handler that stops listening and starts again, as it does when an SSL change it cannot use is applied and then
   * undone, runs two transports. The drained one ends with its own connections, whatever the other one serves.
   */
  @Test
  public void drainEndsWithTheConnectionsOfItsOwnTransport() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    try (Socket first = new Socket("127.0.0.1", port))
    {
      acceptedConnection(handler);
      final TCPNIOTransport drained = listenAgain(handler);
      final TCPNIOTransport live = transport(handler);
      try (Socket second = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 2);

        first.close();

        awaitStopped(drained);
        assertFalse(live.isStopped(), "the transport of the listening handler stopped");
      }
    }
    finally
    {
      stop(handler);
    }
  }

  /** The server shutting down ends, through a drain, only the connections of the drained transport. */
  @Test
  public void drainEndsAtServerShutdownOnlyTheConnectionsOfItsOwnTransport() throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final LDAPConnectionHandler2 handler = start(configuration(port, true, true, true, 2));
    try (Socket first = new Socket("127.0.0.1", port))
    {
      acceptedConnection(handler);
      final TCPNIOTransport drained = listenAgain(handler);
      try (Socket second = new Socket("127.0.0.1", port))
      {
        awaitClientConnections(handler, 2);

        onlyDrain(handler).processServerShutdown(STOP_REASON);

        assertNoticeOfDisconnection(first, STOP_REASON);
        awaitStopped(drained);
        second.setSoTimeout((int) KEEPS_OPEN_MS);
        try
        {
          final int read = second.getInputStream().read();
          fail("the connection of the listening transport was " + (read == -1 ? "closed" : "written to"));
        }
        catch (SocketTimeoutException expected)
        {
          // still open
        }
      }
    }
    finally
    {
      stop(handler);
    }
  }

  /** A listener that cannot bind leaves no transport behind: the transport it started for it is shut down. */
  @Test
  public void failedListenLeavesNoSelectorThreads() throws Exception
  {
    final int port = freePort(false);
    final LDAPConnectionHandler2 handler = new LDAPConnectionHandler2();
    // Both initialization and the configuration check verify the port first: take it only afterwards.
    handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(),
        configuration(port, true, true, false, 2));
    try (ServerSocket taken = new ServerSocket())
    {
      taken.bind(new InetSocketAddress("127.0.0.1", port));
      handler.start();
      final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
      while ((Boolean) field(handler, "enabled"))
      {
        assertTrue(System.currentTimeMillis() < deadline, "the handler did not give up listening");
        Thread.sleep(50);
      }
      assertThreadsStop(handler.getConnectionHandlerName() + " Request Handler");
    }
    finally
    {
      stop(handler);
    }
  }

  /**
   * Returns a free port that a listen socket with the given SO_REUSEADDR setting can bind to on 127.0.0.1.
   * {@link TestCaseUtils#findFreePort()} checks its ports with SO_REUSEADDR only, and every test class counts them down
   * from the same number in a JVM of its own: a port can still carry a connection a previous class left in TIME_WAIT,
   * which refuses only a socket without SO_REUSEADDR.
   */
  private static int freePort(boolean reuseAddress) throws IOException
  {
    while (true)
    {
      final int port = TestCaseUtils.findFreePort();
      if (reuseAddress)
      {
        return port;
      }
      try (ServerSocket probe = new ServerSocket())
      {
        probe.setReuseAddress(false);
        probe.bind(new InetSocketAddress("127.0.0.1", port));
        return port;
      }
      catch (BindException inUse)
      {
        // Try the next one: findFreePort() hands out each port once, and throws when none is left.
      }
    }
  }

  /**
   * Makes the handler stop listening and start again in the same instance, and returns the transport it stopped with.
   */
  private static TCPNIOTransport listenAgain(LDAPConnectionHandler2 handler) throws Exception
  {
    final TCPNIOTransport stopped = transport(handler);
    // What the handler does to itself when it cannot use an SSL change: its configuration still enables it.
    setField(handler, "enabled", false);
    awaitDrains(handler, 1);
    setField(handler, "enabled", true);
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    // The transport starts before the listener binds: only the listener tells that the port accepts connections.
    while (field(handler, "listener") == null)
    {
      assertTrue(System.currentTimeMillis() < deadline, "the handler did not listen again");
      Thread.sleep(50);
    }
    return stopped;
  }

  private static ServerShutdownListener onlyDrain(LDAPConnectionHandler2 handler) throws Exception
  {
    final Collection<?> drains = (Collection<?>) field(handler, "drains");
    assertEquals(drains.size(), 1, "no transport is left serving the connections of the stopped listener");
    return (ServerShutdownListener) drains.iterator().next();
  }

  private static void awaitDrains(LDAPConnectionHandler2 handler, int expected) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (((Collection<?>) field(handler, "drains")).size() != expected)
    {
      assertTrue(System.currentTimeMillis() < deadline, "the handler did not stop listening");
      Thread.sleep(50);
    }
  }

  private static void awaitClientConnections(LDAPConnectionHandler2 handler, int expected) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (handler.getClientConnections().size() != expected)
    {
      assertTrue(System.currentTimeMillis() < deadline, "the handler did not accept the connections");
      Thread.sleep(50);
    }
  }

  /** Waits for a connection handler of the server to accept the connection from the given client port. */
  private static void awaitServerConnection(int clientPort) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (true)
    {
      for (ConnectionHandler<?> connectionHandler : DirectoryServer.getConnectionHandlers())
      {
        for (ClientConnection connection : connectionHandler.getClientConnections())
        {
          if (connection.getClientPort() == clientPort)
          {
            return;
          }
        }
      }
      assertTrue(System.currentTimeMillis() < deadline, "the server did not accept the connection");
      Thread.sleep(50);
    }
  }

  private static void awaitStopped(TCPNIOTransport transport) throws InterruptedException
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (!transport.isStopped())
    {
      assertTrue(System.currentTimeMillis() < deadline, "the drained transport is still running");
      Thread.sleep(50);
    }
  }

  /** Reads the next message from the client, checks it is the expected notice of disconnection, then the end. */
  private static void assertNoticeOfDisconnection(Socket client, LocalizableMessage reason) throws Exception
  {
    client.setSoTimeout((int) TIMEOUT_MS);
    final LDAPMessage message = new LDAPReader(client).readMessage();
    assertNotNull(message, "the connection was closed without a notice of disconnection");
    final ExtendedResponseProtocolOp notice = message.getExtendedResponseProtocolOp();
    assertEquals(notice.getOID(), NOTICE_OF_DISCONNECTION_OID);
    assertEquals(notice.getResultCode(), LDAPResultCode.UNAVAILABLE, "result code");
    assertEquals(String.valueOf(notice.getErrorMessage()), reason.toString(), "diagnostic message");
    assertEquals(client.getInputStream().read(), -1, "the connection stayed open after the notice");
  }

  /** Returns the prefix of the names of the selector threads of the handler, after checking that some run. */
  private static String selectorThreadPrefix(LDAPConnectionHandler2 handler)
  {
    final String prefix = handler.getConnectionHandlerName() + " Request Handler";
    assertFalse(threadsNamed(prefix).isEmpty(), "no thread is named after the handler: " + prefix);
    return prefix;
  }

  private static void assertThreadsStop(String prefix) throws InterruptedException
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    List<String> left;
    while (!(left = threadsNamed(prefix)).isEmpty())
    {
      assertTrue(System.currentTimeMillis() < deadline, "threads of the stopped handler are still alive: " + left);
      Thread.sleep(100);
    }
  }

  private static void assertSelectorThreads(LDAPConnectionHandlerCfg config, int expected) throws Exception
  {
    final LDAPConnectionHandler2 handler = start(config);
    final String threadPrefix = selectorThreadPrefix(handler);
    try
    {
      final TCPNIOTransport transport = transport(handler);
      assertEquals(transport.getSelectorRunnersCount(), expected, "selector runners");
      assertEquals(transport.getKernelThreadPoolConfig().getMaxPoolSize(), expected, "selector threads");
    }
    finally
    {
      stop(handler);
    }
    assertThreadsStop(threadPrefix);
  }

  private static void restore(String selectors)
  {
    if (selectors != null)
    {
      System.setProperty(SELECTORS_PROPERTY, selectors);
    }
    else
    {
      System.clearProperty(SELECTORS_PROPERTY);
    }
  }

  private static List<String> threadsNamed(String prefix)
  {
    final List<String> names = new ArrayList<>();
    for (Thread thread : Thread.getAllStackTraces().keySet())
    {
      if (thread.isAlive() && thread.getName().startsWith(prefix))
      {
        names.add(thread.getName());
      }
    }
    return names;
  }

  private static TCPNIOTransport transport(LDAPConnectionHandler2 handler) throws Exception
  {
    final TCPNIOTransport transport = (TCPNIOTransport) field(handler, "transport");
    assertNotNull(transport, "the handler has no transport");
    return transport;
  }

  /** Waits for the handler to accept a connection and returns the Grizzly connection behind it. */
  private static TCPNIOConnection acceptedConnection(LDAPConnectionHandler2 handler) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (handler.getClientConnections().isEmpty())
    {
      assertTrue(System.currentTimeMillis() < deadline, "the handler did not accept the connection");
      Thread.sleep(50);
    }
    final ClientConnection connection = handler.getClientConnections().iterator().next();
    return (TCPNIOConnection) field(connection, "clientContext", "connection");
  }

  /** Follows a chain of fields declared by the class of each object met. */
  private static Object field(Object target, String... names) throws Exception
  {
    Object value = target;
    for (String name : names)
    {
      final Field field = value.getClass().getDeclaredField(name);
      field.setAccessible(true);
      value = field.get(value);
    }
    return value;
  }

  private static void setField(Object target, String name, Object value) throws Exception
  {
    final Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static LDAPConnectionHandlerCfg configuration(int port, boolean keepAlive, boolean noDelay,
      boolean reuseAddress, Integer numRequestHandlers) throws Exception
  {
    final List<String> lines = new ArrayList<>();
    lines.add("dn: cn=Transport Test Handler,cn=Connection Handlers,cn=config");
    lines.add("objectClass: top");
    lines.add("objectClass: ds-cfg-connection-handler");
    lines.add("objectClass: ds-cfg-ldap-connection-handler");
    lines.add("cn: Transport Test Handler");
    lines.add("ds-cfg-java-class: " + LDAPConnectionHandler2.class.getName());
    lines.add("ds-cfg-enabled: true");
    lines.add("ds-cfg-listen-address: 127.0.0.1");
    lines.add("ds-cfg-listen-port: " + port);
    lines.add("ds-cfg-accept-backlog: 128");
    lines.add("ds-cfg-keep-stats: false");
    lines.add("ds-cfg-use-tcp-keep-alive: " + keepAlive);
    lines.add("ds-cfg-use-tcp-no-delay: " + noDelay);
    lines.add("ds-cfg-allow-tcp-reuse-address: " + reuseAddress);
    lines.add("ds-cfg-buffer-size: " + WRITE_BUFFER_SIZE + " bytes");
    lines.add("ds-cfg-use-ssl: false");
    lines.add("ds-cfg-allow-start-tls: false");
    lines.add("ds-cfg-allow-ldap-v2: false");
    lines.add("ds-cfg-send-rejection-notice: true");
    if (numRequestHandlers != null)
    {
      lines.add("ds-cfg-num-request-handlers: " + numRequestHandlers);
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
