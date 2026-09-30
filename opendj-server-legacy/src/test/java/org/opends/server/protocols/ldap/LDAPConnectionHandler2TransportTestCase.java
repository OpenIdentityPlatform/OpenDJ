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

import static org.testng.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
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
        start(configuration(TestCaseUtils.findFreePort(), true, true, reuseAddress, 2));
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
      final Collection<?> drains = (Collection<?>) field(handler, "drains");
      assertEquals(drains.size(), 1, "no transport is left serving the connection of the stopped handler");

      ((ServerShutdownListener) drains.iterator().next()).processServerShutdown(STOP_REASON);

      client.setSoTimeout((int) TIMEOUT_MS);
      final ByteArrayOutputStream received = new ByteArrayOutputStream();
      final InputStream in = client.getInputStream();
      final byte[] buffer = new byte[256];
      for (int read; (read = in.read(buffer)) != -1;)
      {
        received.write(buffer, 0, read);
      }
      assertTrue(new String(received.toByteArray(), StandardCharsets.ISO_8859_1).contains(NOTICE_OF_DISCONNECTION_OID),
          "the connection was closed without a notice of disconnection");
    }
    finally
    {
      stop(handler);
    }
    assertThreadsStop(threadPrefix);
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
