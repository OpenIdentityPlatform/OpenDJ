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
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.mockito.invocation.InvocationOnMock;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.MonitorRequestMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.Session;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Monitor requests racing with local cleanup must not report ERROR 108 (#1265). */
@SuppressWarnings("javadoc")
public class ReplicationDomainMonitorRequestTest extends ReplicationTestCase
{
  private static final int TIMEOUT_MS = 10000;
  private static final DN BASE_DN = DN.valueOf("dc=example,dc=com");

  @DataProvider
  public Object[][] failedSends()
  {
    return new Object[][] {
      // Local close, handler shutdown, ERROR 108 records.
      { true, false, 0 },
      { true, true, 0 },
      { false, false, 1 },
      { false, true, 0 },
    };
  }

  @Test(dataProvider = "failedSends")
  public void failedRequestChecksLocalCloseAndHandlerShutdown(final boolean locallyClosed,
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
        // An unstarted publisher sends synchronously. Break the socket without marking local close.
        closeSocketsUnder(session);
      }
      assertThat(session.closeInitiated()).isEqualTo(locallyClosed);
      final Requesting requesting = new Requesting(session, shuttingDown, 9302);
      final List<String> logged = requesting.recompute();

      requesting.assertFailedWithoutWaiting(logged);
      assertRequestErrors(logged, expectedErrors);
      if (expectedErrors != 0)
      {
        assertThat(logged).filteredOn(record -> record.contains("severity=ERROR msgID=108 "))
            .allMatch(record -> record.contains(requesting.failure.get().getMessage()));
      }
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void requestAfterConcurrentLocalCloseIsNotReported() throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Session session = pair[1];
      session.start();
      session.waitForStartup();
      final Requesting requesting = new Requesting(session, false, 9302);
      final CountDownLatch sending = new CountDownLatch(1);
      final CountDownLatch closed = new CountDownLatch(1);
      doAnswer(invocation ->
      {
        // Hold send after handler selection until the other thread completes local close.
        sending.countDown();
        assertThat(closed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).as("local close completed").isTrue();
        return requesting.send(invocation);
      }).when(requesting.handler).send(any(MonitorRequestMsg.class));

      final ExecutorService executor = Executors.newSingleThreadExecutor();
      try
      {
        final Future<List<String>> sender = executor.submit(requesting::recompute);
        assertThat(sending.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).as("handler selected").isTrue();
        assertThat(session.closeInitiated()).isFalse();
        session.close();
        assertThat(session.closeInitiated()).isTrue();
        closed.countDown();
        final List<String> logged = sender.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        requesting.assertFailedWithoutWaiting(logged);
        assertRequestErrors(logged, 0);
      }
      finally
      {
        closed.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS)).as("sender stopped").isTrue();
      }
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void requestAfterHandlerShutdownAtSelectionIsNotReported() throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Session session = pair[1];
      closeSocketsUnder(session);
      final Requesting requesting = new Requesting(session, false, 9302);
      doAnswer(invocation ->
      {
        // Model stopServer() engaging shutdown after selection, before the real send fails.
        requesting.shutdown.set(true);
        return requesting.send(invocation);
      }).when(requesting.handler).send(any(MonitorRequestMsg.class));

      final List<String> logged = requesting.recompute();
      assertThat(session.closeInitiated()).isFalse();
      requesting.assertFailedWithoutWaiting(logged);
      assertRequestErrors(logged, 0);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @DataProvider
  public Object[][] precedingFailure()
  {
    return new Object[][] { { false }, { true } };
  }

  @Test(dataProvider = "precedingFailure")
  public void deliveredRequestExpectsAResponseEvenAfterAnotherSendFails(final boolean precedingFailure)
      throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Requesting requesting = new Requesting(pair[1], false, 9302);
      // A separate closed session must not prevent the next peer from receiving its request.
      final Session[] broken = connectSessionPair();
      try
      {
        broken[1].close();
        final Requesting failed = new Requesting(broken[1], false, 9303);
        if (precedingFailure)
        {
          final Map<Integer, ReplicationServerHandler> peers = new LinkedHashMap<>();
          peers.put(9303, failed.handler);
          peers.put(9302, requesting.handler);
          when(requesting.domain.getConnectedRSs()).thenReturn(peers);
        }
        final List<String> logged = requesting.recompute();

        final MonitorRequestMsg received = (MonitorRequestMsg) pair[0].receive();
        assertThat(received.getSenderID()).isEqualTo(9301);
        assertThat(received.getDestination()).isEqualTo(9302);
        assertThat(requesting.failure.get()).isNull();
        assertRequestErrors(logged, 0);
        // The peer deliberately stays silent: only a successfully sent request expects a response.
        assertThat(logged).filteredOn(record -> record.contains("category=SYNC severity=WARNING msgID=106 "))
            .hasSize(1).allMatch(record -> record.contains("RS(9302)"));
        if (precedingFailure)
        {
          assertThat(failed.failure.get()).as("preceding real send failed").isNotNull();
        }
      }
      finally
      {
        StaticUtils.close(broken);
      }
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  private static void assertRequestErrors(final List<String> logged, final int count)
  {
    assertThat(logged).filteredOn(record -> record.contains("category=SYNC severity=ERROR msgID=108 "))
        .hasSize(count).allMatch(record -> record.contains("domain \"dc=example,dc=com\" to replication server RS(9302)"));
  }

  /** Real monitor recomputation and handler send, without starting a replication topology. */
  private static final class Requesting
  {
    private final ReplicationServerHandler handler = mock(ReplicationServerHandler.class);
    private final ReplicationServerDomain domain = mock(ReplicationServerDomain.class);
    private final ReplicationDomainMonitor monitor;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicReference<IOException> failure = new AtomicReference<>();

    private Requesting(final Session session, final boolean shuttingDown, final int peerId) throws Exception
    {
      setField(ServerHandler.class, handler, "session", session);
      setField(MessageHandler.class, handler, "replicationServerDomain", domain);
      shutdown.set(shuttingDown);
      when(handler.shuttingDown()).thenAnswer(invocation -> shutdown.get());
      when(handler.getServerId()).thenReturn(peerId);
      doAnswer(invocation -> send(invocation)).when(handler).send(any(MonitorRequestMsg.class));
      when(domain.getBaseDN()).thenReturn(BASE_DN);
      when(domain.getLocalRSServerId()).thenReturn(9301);
      when(domain.getConnectedDSs()).thenReturn(Collections.emptyMap());
      when(domain.getConnectedRSs()).thenReturn(Collections.singletonMap(peerId, handler));
      when(domain.getLatestServerState()).thenReturn(new ServerState());
      monitor = new ReplicationDomainMonitor(domain);
    }

    private List<String> recompute() throws Exception
    {
      return errorLogRecordsOf(() ->
      {
        final ReplicationDomainMonitorData data = monitor.recomputeMonitorData();
        assertThat(data).isSameAs(monitor.getMonitorData());
        assertThat(data.getRSStates(9301)).as("best-effort local monitor data").isNotNull();
        return null;
      });
    }

    /** Retain the real send failure so skipping send cannot pass a suppression case. */
    private Object send(final InvocationOnMock invocation) throws Throwable
    {
      try
      {
        return invocation.callRealMethod();
      }
      catch (IOException e)
      {
        failure.set(e);
        throw e;
      }
    }

    private void assertFailedWithoutWaiting(final List<String> logged)
    {
      assertThat(failure.get()).as("real send failure").isNotNull();
      assertThat(logged).noneMatch(record -> record.contains("severity=WARNING msgID=106 "));
      verify(domain, never()).stopServer(any(ServerHandler.class), anyBoolean());
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
