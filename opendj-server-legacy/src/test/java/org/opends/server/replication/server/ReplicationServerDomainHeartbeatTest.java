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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ChangeTimeHeartbeatMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.Session;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Heartbeats racing with a local close must not report an unexpected replication failure (#1259). */
@SuppressWarnings("javadoc")
public class ReplicationServerDomainHeartbeatTest extends ReplicationTestCase
{
  private static final int TIMEOUT_MS = 10000;

  @DataProvider
  public Object[][] failedSends()
  {
    return new Object[][] {
      // Local close, handler shutdown, ERROR 66 records.
      { true, false, 0 },
      { true, true, 0 },
      { false, false, 1 },
      { false, true, 0 },
    };
  }

  @Test(dataProvider = "failedSends")
  public void failedHeartbeatChecksLocalCloseAndHandlerShutdown(final boolean locallyClosed,
      final boolean shuttingDown, final int expectedErrors) throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Session session = pair[1];
      if (locallyClosed)
      {
        session.start();
        session.waitForStartup();
        session.close();
      }
      else
      {
        /*
         * Leave the publisher unstarted so the real send fails synchronously, without marking
         * a local close. A running publisher handles its own write failures instead of throwing
         * them back to the heartbeat sender. This pins the diagnostic for an otherwise open
         * handler, independently of the close flag.
         */
        closeSocketsUnder(session);
      }
      assertThat(session.closeInitiated()).isEqualTo(locallyClosed);
      final Forwarding forwarding = new Forwarding(session, shuttingDown);

      final List<String> logged = errorLogRecordsOf(() ->
      {
        forwarding.domain.sendPendingStatusMessages();
        return null;
      });

      // Reaching cleanup proves the real send threw; a skipped or successful send cannot pass.
      verify(forwarding.handler).send(forwarding.heartbeat);
      verify(forwarding.domain).stopServer(forwarding.handler, false);
      assertHeartbeatErrors(logged, expectedErrors);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void heartbeatAfterConcurrentLocalCloseIsNotReported() throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Session session = pair[1];
      session.start();
      session.waitForStartup();
      final Forwarding forwarding = new Forwarding(session, false);
      final CountDownLatch sending = new CountDownLatch(1);
      final CountDownLatch closed = new CountDownLatch(1);
      doAnswer(invocation ->
      {
        // The domain has selected this handler and entered send while its session is still open.
        sending.countDown();
        assertThat(closed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).as("local close completed").isTrue();
        return invocation.callRealMethod();
      }).when(forwarding.handler).send(forwarding.heartbeat);

      final List<String> logged = errorLogRecordsOf(() ->
      {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try
        {
          final Future<?> sender = executor.submit(forwarding.domain::sendPendingStatusMessages);
          assertThat(sending.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).as("handler selected").isTrue();
          assertThat(session.closeInitiated()).isFalse();
          session.close();
          assertThat(session.closeInitiated()).isTrue();
          closed.countDown();
          sender.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        finally
        {
          closed.countDown();
          executor.shutdownNow();
          assertThat(executor.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS))
              .as("sender stopped before log capture is removed").isTrue();
        }
        return null;
      });

      verify(forwarding.handler).send(forwarding.heartbeat);
      verify(forwarding.domain).stopServer(forwarding.handler, false);
      assertHeartbeatErrors(logged, 0);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void heartbeatIsDeliveredToAnOpenSession() throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Forwarding forwarding = new Forwarding(pair[1], false);
      final List<String> logged = errorLogRecordsOf(() ->
      {
        forwarding.domain.sendPendingStatusMessages();
        return null;
      });

      final ChangeTimeHeartbeatMsg received = (ChangeTimeHeartbeatMsg) pair[0].receive();
      assertThat(received.getCSN()).isEqualTo(forwarding.heartbeat.getCSN());
      verify(forwarding.domain, never()).stopServer(any(ServerHandler.class), anyBoolean());
      assertHeartbeatErrors(logged, 0);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  private static void assertHeartbeatErrors(final List<String> logged, final int count)
  {
    // Keep duplicates: two identical reports in the same second must count as two errors.
    assertThat(logged).filteredOn(record -> record.contains("category=SYNC severity=ERROR msgID=66 "))
        .hasSize(count)
        .allMatch(record -> record.contains("sending a Message to Replication Server 8989 cn=schema 9301."));
  }

  /** Real status-message forwarding and handler send, without a replication topology or teardown. */
  private static final class Forwarding
  {
    private final ChangeTimeHeartbeatMsg heartbeat = new ChangeTimeHeartbeatMsg(new CSN(1, 1, 9311));
    private final ReplicationServerHandler handler = mock(ReplicationServerHandler.class);
    private final ReplicationServerDomain domain = mock(ReplicationServerDomain.class);

    private Forwarding(final Session session, final boolean shuttingDown) throws Exception
    {
      final ReplicationServer local = mock(ReplicationServer.class);
      when(local.getServerId()).thenReturn(9301);
      when(local.getReplicationPort()).thenReturn(8989);
      setField(ServerHandler.class, handler, "session", session);
      setField(MessageHandler.class, handler, "replicationServerDomain", domain);
      when(handler.getProtocolVersion()).thenReturn(ProtocolVersion.getCurrentVersion());
      final AtomicBoolean shutdown = new AtomicBoolean(shuttingDown);
      when(handler.shuttingDown()).thenAnswer(invocation -> shutdown.get());
      // The real stopServer() engages shutdown before doing any other cleanup.
      doAnswer(invocation ->
      {
        shutdown.set(true);
        return null;
      }).when(domain).stopServer(handler, false);
      doCallRealMethod().when(handler).send(heartbeat);

      final Class<?> pendingClass = Class.forName(ReplicationServerDomain.class.getName() + "$PendingStatusMessages");
      final Constructor<?> constructor = pendingClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      final Object pending = constructor.newInstance();
      setField(pendingClass, pending, "pendingHeartbeats", Collections.singletonMap(9311, heartbeat));
      setField(ReplicationServerDomain.class, domain, "pendingStatusMessages", pending);
      setField(ReplicationServerDomain.class, domain, "pendingStatusMessagesLock", new Object());
      setField(ReplicationServerDomain.class, domain, "connectedRSs", Collections.singletonMap(9302, handler));
      setField(ReplicationServerDomain.class, domain, "baseDN", DN.valueOf("cn=schema"));
      setField(ReplicationServerDomain.class, domain, "localReplicationServer", local);
      doCallRealMethod().when(domain).sendPendingStatusMessages();
    }
  }

  private static void setField(final Class<?> owner, final Object target, final String name, final Object value)
      throws Exception
  {
    final Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  /** Break the socket without calling Session.close(), keeping closeInitiated false. */
  private static void closeSocketsUnder(final Session session) throws Exception
  {
    for (final String name : new String[] { "plainSocket", "secureSocket" })
    {
      final Field field = Session.class.getDeclaredField(name);
      field.setAccessible(true);
      StaticUtils.close((Closeable) field.get(session));
    }
  }

  /** A real TLS session pair, client first, with bounded reads and neither publisher started. */
  private static Session[] connectSessionPair() throws Exception
  {
    final ReplSessionSecurity security = getReplSessionSecurity();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try (ServerSocket listener = new ServerSocket(0))
    {
      final Socket clientSocket = new Socket("127.0.0.1", listener.getLocalPort());
      Socket serverSocket = null;
      Session serverEnd = null;
      boolean connected = false;
      try
      {
        final Future<Session> clientEnd = executor.submit(() -> security.createClientSession(clientSocket, TIMEOUT_MS));
        serverSocket = listener.accept();
        serverEnd = security.createServerSession(serverSocket, TIMEOUT_MS);
        final Session[] pair = { clientEnd.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), serverEnd };
        for (final Session session : pair)
        {
          session.setProtocolVersion(ProtocolVersion.getCurrentVersion());
        }
        connected = true;
        return pair;
      }
      finally
      {
        if (!connected)
        {
          // Closing the sockets also releases a client handshake still running in the executor.
          StaticUtils.close(clientSocket, serverSocket, serverEnd);
        }
      }
    }
    finally
    {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS)).as("session setup stopped").isTrue();
    }
  }
}
