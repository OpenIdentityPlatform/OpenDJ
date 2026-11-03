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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
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
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.invocation.InvocationOnMock;
import org.opends.server.loggers.ConsoleDebugLogPublisher;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.TraceSettings;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.Session;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Monitor replies racing with a local close must not report an unexpected replication failure (#1262). */
@SuppressWarnings("javadoc")
public class ReplicationServerDomainMonitorTest extends ReplicationTestCase
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
  public void failedMonitorChecksLocalCloseAndHandlerShutdown(final boolean locallyClosed,
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
         * them back to the monitor sender. This pins the diagnostic for an otherwise open
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

      verify(forwarding.handler).send(forwarding.monitor);
      forwarding.assertFailedWithoutStopping();
      assertMonitorErrors(logged, expectedErrors);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void monitorAfterConcurrentLocalCloseIsNotReported() throws Exception
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
        return forwarding.send(invocation);
      }).when(forwarding.handler).send(forwarding.monitor);

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

      verify(forwarding.handler).send(forwarding.monitor);
      forwarding.assertFailedWithoutStopping();
      assertMonitorErrors(logged, 0);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void monitorAfterConcurrentHandlerShutdownIsNotReported() throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Session session = pair[1];
      closeSocketsUnder(session);
      final Forwarding forwarding = new Forwarding(session, false);
      doAnswer(invocation ->
      {
        // Model another stopServer() engaging shutdown after the domain selected this handler.
        forwarding.shutdown.set(true);
        return forwarding.send(invocation);
      }).when(forwarding.handler).send(forwarding.monitor);

      final List<String> logged = errorLogRecordsOf(() ->
      {
        forwarding.domain.sendPendingStatusMessages();
        return null;
      });

      assertThat(session.closeInitiated()).isFalse();
      verify(forwarding.handler).send(forwarding.monitor);
      forwarding.assertFailedWithoutStopping();
      assertMonitorErrors(logged, 0);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void suppressedMonitorFailureIsStillTraced() throws Exception
  {
    final Session[] pair = connectSessionPair();
    try
    {
      final Session session = pair[1];
      session.start();
      session.waitForStartup();
      session.close();
      final Forwarding forwarding = new Forwarding(session, false);

      final ByteArrayOutputStream trace = new ByteArrayOutputStream();
      try (PrintStream output = new PrintStream(trace, true, "UTF-8"))
      {
        // Capture only this forwarding method, independently of global test trace settings.
        final ConsoleDebugLogPublisher capture = new ConsoleDebugLogPublisher(output);
        capture.addTraceSettings(ReplicationServerDomain.class.getName() + "#sendPendingMonitorMsgs",
            new TraceSettings());
        DebugLogger.getInstance().addLogPublisher(capture);
        try
        {
          final List<String> logged = errorLogRecordsOf(() ->
          {
            forwarding.domain.sendPendingStatusMessages();
            return null;
          });

          forwarding.assertFailedWithoutStopping();
          assertMonitorErrors(logged, 0);
          assertThat(trace.toString("UTF-8"))
              .contains("caught={" + forwarding.failure.get() + "}", "sendPendingMonitorMsgs()");
        }
        finally
        {
          DebugLogger.getInstance().removeLogPublisher(capture);
        }
      }
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  @Test
  public void monitorIsDeliveredToAnOpenSession() throws Exception
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

      final MonitorMsg received = (MonitorMsg) pair[0].receive();
      assertThat(received.getSenderID()).isEqualTo(9301);
      assertThat(received.getDestination()).isEqualTo(9302);
      assertThat(received.getReplServerDbState().isEmpty()).isTrue();
      assertThat(forwarding.failure.get()).isNull();
      verify(forwarding.domain, never()).stopServer(any(ServerHandler.class), anyBoolean());
      assertMonitorErrors(logged, 0);
    }
    finally
    {
      StaticUtils.close(pair);
    }
  }

  private static void assertMonitorErrors(final List<String> logged, final int count)
  {
    // Keep duplicates: two identical reports in the same second must count as two errors.
    assertThat(logged).filteredOn(record -> record.contains("category=SYNC severity=ERROR msgID=66 "))
        .hasSize(count)
        .allMatch(record -> record.contains("sending a Message to 9302."));
  }

  /** Real status-message forwarding and handler send, without a replication topology or teardown. */
  private static final class Forwarding
  {
    private final MonitorMsg monitor = new MonitorMsg(9301, 9302);
    private final ReplicationServerHandler handler = mock(ReplicationServerHandler.class);
    private final ReplicationServerDomain domain = mock(ReplicationServerDomain.class);
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicReference<IOException> failure = new AtomicReference<>();

    private Forwarding(final Session session, final boolean shuttingDown) throws Exception
    {
      monitor.setReplServerDbState(new ServerState());
      setField(ServerHandler.class, handler, "session", session);
      setField(MessageHandler.class, handler, "replicationServerDomain", domain);
      shutdown.set(shuttingDown);
      when(handler.shuttingDown()).thenAnswer(invocation -> shutdown.get());
      doAnswer(invocation -> send(invocation)).when(handler).send(monitor);

      final Class<?> pendingClass = Class.forName(ReplicationServerDomain.class.getName() + "$PendingStatusMessages");
      final Constructor<?> constructor = pendingClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      final Object pending = constructor.newInstance();
      setField(pendingClass, pending, "pendingRSMonitorMsgs", Collections.singletonMap(9302, monitor));
      setField(ReplicationServerDomain.class, domain, "pendingStatusMessages", pending);
      setField(ReplicationServerDomain.class, domain, "pendingStatusMessagesLock", new Object());
      setField(ReplicationServerDomain.class, domain, "connectedRSs", Collections.singletonMap(9302, handler));
      doCallRealMethod().when(domain).sendPendingStatusMessages();
    }

    /** Call the real send and retain its IOException so a skipped send cannot pass a suppression case. */
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

    private void assertFailedWithoutStopping()
    {
      assertThat(failure.get()).as("real send failure").isNotNull();
      // Monitor replies leave failure detection and cleanup to the requester/reader.
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
