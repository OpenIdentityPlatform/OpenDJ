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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opends.messages.ReplicationMessages.ERR_DS_BADLY_DISCONNECTED;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.replication.common.ServerStatus.DEGRADED_STATUS;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.HeartbeatMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StopMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Exercises coordinated close through real sessions and the production ServerReader. */
@SuppressWarnings("javadoc")
public class ServerSessionCloseTest extends ReplicationTestCase
{
  private static final int TIMEOUT_MS = 15000;

  @DataProvider(name = "encryption")
  public Object[][] encryption()
  {
    return new Object[][] { { false }, { true } };
  }

  @DataProvider(name = "stopProtocol")
  public Object[][] stopProtocol()
  {
    return new Object[][] {
      { false, ProtocolVersion.REPLICATION_PROTOCOL_V4 },
      { true, ProtocolVersion.REPLICATION_PROTOCOL_V4 },
      { false, ProtocolVersion.getCurrentVersion() },
      { true, ProtocolVersion.getCurrentVersion() }
    };
  }

  @Test(dataProvider = "stopProtocol", timeOut = 30000)
  public void closeWaitsForThePeerAndKeepsProcessingIncomingChanges(boolean encrypted, short protocol) throws Exception
  {
    final CountDownLatch firstReceived = new CountDownLatch(1);
    final CountDownLatch releaseFirst = new CountDownLatch(1);
    final CountDownLatch bothReceived = new CountDownLatch(2);
    final List<CSN> received = new CopyOnWriteArrayList<>();
    try (Pair pair = new Pair(encrypted))
    {
      pair.server.setProtocolVersion(protocol);
      pair.peer.setProtocolVersion(protocol);
      pair.startReader(update -> {
        if (received.isEmpty())
        {
          firstReceived.countDown();
          if (!releaseFirst.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
          {
            throw new IOException("the test did not release the first incoming update");
          }
        }
        // A real handler may give receive-window credit while it processes an update.
        // After our StopMsg, that reply must be refused without terminating this reader.
        final CountDownLatch replied = new CountDownLatch(1);
        assertThat(pair.server.publish(new WindowMsg(1), replied::countDown)).isFalse();
        assertThat(replied.getCount()).isEqualTo(1);
        received.add(update.getCSN());
        bothReceived.countDown();
      });
      final CSNGenerator csns = new CSNGenerator(9302, 0);
      final DeleteMsg first = change(csns);
      final DeleteMsg second = change(csns);
      pair.peer.publish(first);
      assertThat(firstReceived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
      pair.peer.publish(second);
      final Future<?> close = pair.executor.submit(pair.server::close);
      try
      {
        assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
        assertThatThrownBy(() -> close.get(200, TimeUnit.MILLISECONDS))
            .as("the socket closed before the peer responded to StopMsg")
            .isInstanceOf(TimeoutException.class);
        assertThatThrownBy(() -> pair.server.publish(new HeartbeatMsg()))
            .as("a message was written after StopMsg")
            .isInstanceOf(IOException.class);
        releaseFirst.countDown();
        assertThat(bothReceived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            .as("the existing reader stopped processing incoming changes during close")
            .isTrue();
        pair.peer.close();
        close.get(2000, TimeUnit.MILLISECONDS);
        pair.awaitReader();
        assertThat(received).containsExactly(first.getCSN(), second.getCSN());
      }
      finally
      {
        releaseFirst.countDown();
      }
    }
  }

  @Test(timeOut = 30000)
  public void abortReleasesACloseWaitingForThePeer() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      final Future<?> close = pair.executor.submit(pair.server::close);
      assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
      assertThatThrownBy(() -> close.get(200, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      final Future<?> abort = pair.executor.submit(() -> pair.server.abort(new IOException("test abort")));
      abort.get(2000, TimeUnit.MILLISECONDS);
      close.get(2000, TimeUnit.MILLISECONDS);
      pair.awaitReader();
    }
  }

  @Test(timeOut = 30000)
  public void aSilentPeerCannotHoldClosePastItsDeadline() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      final List<String> logged = errorLogRecordsOf(() -> {
        final Future<?> close = pair.executor.submit(pair.server::close);
        assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
        assertThatThrownBy(() -> close.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        close.get(8000, TimeUnit.MILLISECONDS);
        pair.awaitReader();
        return null;
      });
      assertThat(logged).filteredOn(line -> line.contains("stop exchange did not complete"))
          .as("the close deadline was not reported")
          .hasSize(1);
    }
  }

  @Test(timeOut = 30000)
  public void theDeadlineAlsoEndsAPublisherBlockedBeforeStopMsg() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      final CountDownLatch written = new CountDownLatch(1);
      pair.server.publish(new EntryMsg(9301, 9302, new byte[4 * 1024 * 1024], 1), written::countDown);
      final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
      while (pair.peerSocket.getInputStream().available() == 0)
      {
        assertThat(System.nanoTime() < deadline).as("the outgoing write did not start").isTrue();
        Thread.sleep(10);
      }
      assertThat(written.await(200, TimeUnit.MILLISECONDS)).as("the outgoing write did not block").isFalse();
      final Future<?> close = pair.executor.submit(pair.server::close);
      close.get(8000, TimeUnit.MILLISECONDS);
      pair.server.join(2000);
      assertThat(pair.server.isAlive()).as("the blocked publisher outlived the deadline").isFalse();
      assertThat(written.getCount()).as("a failed write ran its completion callback").isEqualTo(1);
      pair.awaitReader();
    }
  }

  @Test(timeOut = 30000)
  public void theDeadlineAlsoEndsAWriterHoldingTheSynchronousOutputLock() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      // A publisher interrupted outside close leaves later publishes on the synchronous path.
      pair.server.interrupt();
      pair.server.join(TIMEOUT_MS);
      assertThat(pair.server.isAlive()).isFalse();
      final CountDownLatch written = new CountDownLatch(1);
      final Future<?> writer = pair.executor.submit(() -> {
        pair.server.publish(new EntryMsg(9301, 9302, new byte[4 * 1024 * 1024], 1), written::countDown);
        return null;
      });
      final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
      while (pair.peerSocket.getInputStream().available() == 0)
      {
        assertThat(System.nanoTime() < deadline).as("the synchronous write did not start").isTrue();
        Thread.sleep(10);
      }
      assertThat(written.await(200, TimeUnit.MILLISECONDS)).isFalse();
      final Future<?> close = pair.executor.submit(pair.server::close);
      close.get(8000, TimeUnit.MILLISECONDS);
      assertThatThrownBy(() -> writer.get(2000, TimeUnit.MILLISECONDS))
          .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IOException.class);
      assertThat(written.getCount()).isEqualTo(1);
      pair.awaitReader();
    }
  }

  @Test(dataProvider = "encryption", timeOut = 30000)
  public void aKilledPeerIsStillReportedAsBadlyDisconnected(boolean encrypted) throws Exception
  {
    try (Pair pair = new Pair(encrypted))
    {
      pair.startReader(update -> { });
      final List<String> logged = errorLogRecordsOf(() -> {
        pair.peer.abort(new IOException("the test kills the peer"));
        pair.awaitReader();
        return null;
      });
      assertThat(logged).filteredOn(line -> line.contains("category=SYNC severity=ERROR msgID=211 "))
          .hasSize(1);
    }
  }

  @Test(timeOut = 30000)
  public void peerFailureReleasesACloseWaitingForStopMsg() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      final Future<?> close = pair.executor.submit(pair.server::close);
      assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
      pair.peer.abort(new IOException("the test kills the peer before its reply"));
      close.get(2000, TimeUnit.MILLISECONDS);
      pair.awaitReader();
    }
  }

  @Test(timeOut = 30000)
  public void simultaneousCloseDoesNotWaitForEitherReaderToCloseItself() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      pair.startReader(pair.peer, pair.server, update -> { });
      final CountDownLatch together = new CountDownLatch(1);
      final Future<?> first = pair.executor.submit(() -> {
        together.await();
        pair.server.close();
        return null;
      });
      final Future<?> second = pair.executor.submit(() -> {
        together.await();
        pair.peer.close();
        return null;
      });
      together.countDown();
      first.get(2000, TimeUnit.MILLISECONDS);
      second.get(2000, TimeUnit.MILLISECONDS);
      pair.awaitReader();
    }
  }

  @Test(timeOut = 30000)
  public void aPreV4PeerIsNotAskedForStopMsg() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      pair.server.setProtocolVersion(ProtocolVersion.REPLICATION_PROTOCOL_V3);
      pair.peer.setProtocolVersion(ProtocolVersion.REPLICATION_PROTOCOL_V3);
      final Future<?> close = pair.executor.submit(pair.server::close);
      close.get(2000, TimeUnit.MILLISECONDS);
      assertThatThrownBy(pair.peer::receive).isInstanceOf(IOException.class);
      pair.awaitReader();
    }
  }

  @Test(timeOut = 30000)
  public void anInterruptedCloseDoesNotWaitForThePeer() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { });
      final AtomicReference<Thread> owner = new AtomicReference<>();
      final Future<Boolean> close = pair.executor.submit(() -> {
        owner.set(Thread.currentThread());
        pair.server.close();
        return Thread.currentThread().isInterrupted();
      });
      assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
      owner.get().interrupt();
      assertThat(close.get(2000, TimeUnit.MILLISECONDS)).as("close lost its interrupt").isTrue();
      pair.awaitReader();
    }
  }

  @Test(timeOut = 30000)
  public void aReaderStoppingAfterProcessingFailureDoesNotWaitForItself() throws Exception
  {
    try (Pair pair = new Pair(false))
    {
      pair.startReader(update -> { throw new IOException("the test stops update processing"); });
      pair.peer.publish(change(new CSNGenerator(9302, 0)));
      assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
      final ServerReader reader = pair.readers.get(0);
      reader.join(2000);
      assertThat(reader.isAlive()).as("the reader waits for its own StopMsg response").isFalse();
    }
  }

  @Test(timeOut = 30000)
  public void aRealServerShutdownIsAnOrderlyDisconnectForItsBroker() throws Exception
  {
    final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
      final Thread thread = new Thread(runnable, "ServerSessionCloseTest integration");
      thread.setDaemon(true);
      return thread;
    });
    final AtomicBoolean listening = new AtomicBoolean(true);
    ReplicationServer rs = null;
    ReplicationBroker broker = null;
    Future<?> listener = null;
    try
    {
      final int port = TestCaseUtils.findFreePort();
      rs = new ReplicationServer(new ReplServerFakeConfiguration(
          port, "gracefulStopExchangeDb", 0, 9301, 0, 100, new TreeSet<String>()));
      final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
      broker = openReplicationSession(baseDN, 9302, 100, port, 0, EMPTY_DN_GENID);
      final ReplicationBroker connectedBroker = broker;
      listener = executor.submit(() -> {
        while (listening.get())
        {
          connectedBroker.receive();
        }
        return null;
      });
      final DeleteMsg update = change(new CSNGenerator(9302, 0));
      broker.publish(update);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN);
      final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
      while (!domain.getLatestServerState().cover(update.getCSN()))
      {
        assertThat(System.nanoTime() < deadline).as("the real RS did not store the update").isTrue();
        Thread.sleep(10);
      }
      final ReplicationServer closingServer = rs;
      final List<String> logged = errorLogRecordsOf(() -> {
        executor.submit(closingServer::shutdown).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return null;
      });
      assertThat(logged).filteredOn(line -> line.contains("category=SYNC severity=WARNING msgID=63 ")
          && line.contains("DS(9302)") && line.contains(TEST_ROOT_DN_STRING))
          .as("the broker did not receive the server's StopMsg")
          .hasSize(1);
      assertThat(logged).filteredOn(line -> line.contains("category=SYNC severity=ERROR")
          || line.contains("stop exchange did not complete"))
          .as("the real shutdown failed its stop exchange")
          .isEmpty();
    }
    finally
    {
      listening.set(false);
      stop(broker);
      if (listener != null)
      {
        listener.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      }
      executor.shutdownNow();
      remove(rs);
    }
  }

  @Test(timeOut = 30000)
  public void stoppingAHandlerDoesNotHoldTheDomainLockWhileItsReaderDrains() throws Exception
  {
    final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
      final Thread thread = new Thread(runnable, "ServerSessionCloseTest domain stop");
      thread.setDaemon(true);
      return thread;
    });
    final AtomicReference<Thread> owner = new AtomicReference<>();
    ReplicationServer rs = null;
    ReplicationBroker broker = null;
    Future<?> stopping = null;
    try
    {
      final int port = TestCaseUtils.findFreePort();
      rs = new ReplicationServer(new ReplServerFakeConfiguration(
          port, "gracefulStopDomainLockDb", 0, 9301, 0, 100, new TreeSet<String>()));
      final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
      broker = openReplicationSession(baseDN, 9302, 100, port, 0, EMPTY_DN_GENID);
      final Session peer = sessionOf(broker);
      peer.setSoTimeout(TIMEOUT_MS);
      final DeleteMsg update = change(new CSNGenerator(9302, 0));
      broker.publish(update);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN);
      final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
      while (!domain.getLatestServerState().cover(update.getCSN()))
      {
        assertThat(System.nanoTime() < deadline).as("the RS did not receive its first update").isTrue();
        Thread.sleep(10);
      }
      final DataServerHandler handler = domain.getConnectedDSs().get(9302);
      assertThat(handler).isNotNull();
      stopping = executor.submit(() -> {
        owner.set(Thread.currentThread());
        domain.stopServer(handler, false);
      });
      while (!(peer.receive() instanceof StopMsg))
      {
        // Ignore monitoring/topology frames which preceded the close.
      }
      final List<String> logged = errorLogRecordsOf(() -> {
        domain.lock();
        try
        {
          // Model a DS which was degraded when close began. Set it after receipt of StopMsg
          // so the background analyzer cannot win the setup race and normalize it beforehand.
          final Field status = DataServerHandler.class.getDeclaredField("status");
          status.setAccessible(true);
          status.set(handler, DEGRADED_STATUS);
          domain.checkDSDegradedStatus();
          assertThat(handler.getStatus()).isEqualTo(DEGRADED_STATUS);
        }
        finally
        {
          domain.release();
        }
        return null;
      });
      assertThat(logged).filteredOn(line -> line.contains("category=SYNC severity=ERROR"))
          .as("the status analyzer tried to change the status of a closing handler").isEmpty();
      // This message precedes the peer's StopMsg and needs the domain lock in the reader.
      peer.publish(new ResetGenerationIdMsg(domain.getGenerationId()));
      peer.close();
      stopping.get(2000, TimeUnit.MILLISECONDS);
      assertThat(domain.getConnectedDSs()).doesNotContainKey(9302);
    }
    finally
    {
      if (owner.get() != null)
      {
        owner.get().interrupt();
      }
      if (stopping != null)
      {
        stopping.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      }
      stop(broker);
      executor.shutdownNow();
      remove(rs);
    }
  }

  @Test(timeOut = 30000)
  public void anInterruptedShutdownStillUnregistersItsClosedHandler() throws Exception
  {
    ReplicationServer rs = null;
    ReplicationBroker broker = null;
    try
    {
      final int port = TestCaseUtils.findFreePort();
      rs = new ReplicationServer(new ReplServerFakeConfiguration(
          port, "gracefulStopInterruptedDb", 0, 9301, 0, 100, new TreeSet<String>()));
      final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
      broker = openReplicationSession(baseDN, 9302, 100, port, 0, EMPTY_DN_GENID);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN);
      final DataServerHandler original = domain.getConnectedDSs().get(9302);
      assertThat(original).isNotNull();
      final DataServerHandler handler = spy(original);
      domain.lock();
      try
      {
        domain.register(handler);
      }
      finally
      {
        domain.release();
      }
      doAnswer(invocation -> {
        // Fail the real transport so shutdown does not need a listener in this metadata test.
        original.session.abort(new IOException("the test ends the peer transport"));
        invocation.callRealMethod();
        Thread.currentThread().interrupt();
        return null;
      }).when(handler).shutdown();
      domain.stopServer(handler, false);
      assertThat(domain.getConnectedDSs()).doesNotContainKey(9302);
      assertThat(Thread.currentThread().isInterrupted()).as("cleanup lost the pending interrupt").isTrue();
    }
    finally
    {
      Thread.interrupted();
      stop(broker);
      remove(rs);
    }
  }

  private static Session sessionOf(ReplicationBroker broker) throws Exception
  {
    final Field field = ReplicationBroker.class.getDeclaredField("connectedRS");
    field.setAccessible(true);
    final Object rs = ((AtomicReference<?>) field.get(broker)).get();
    final Field session = rs.getClass().getDeclaredField("session");
    session.setAccessible(true);
    return (Session) session.get(rs);
  }

  @Test(timeOut = 30000)
  public void aStartedReaderIsKnownBeforeItsRunMethodIsScheduled() throws Exception
  {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch run = new CountDownLatch(1);
    try (Pair pair = new Pair(false))
    {
      final DataServerHandler handler = pair.handler(pair.server, update -> { }, new CountDownLatch(0));
      final ServerReader reader = new ServerReader(pair.server, handler) {
        @Override
        public void run()
        {
          entered.countDown();
          try
          {
            run.await();
          }
          catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
          }
          super.run();
        }
      };
      pair.readers.add(reader);
      reader.setDaemon(true);
      reader.start();
      assertThat(entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
      final Future<?> close = pair.executor.submit(pair.server::close);
      try
      {
        assertThat(pair.peer.receive()).isInstanceOf(StopMsg.class);
        assertThatThrownBy(() -> close.get(200, TimeUnit.MILLISECONDS))
            .as("close did not know the reader because run had not been scheduled")
            .isInstanceOf(TimeoutException.class);
        run.countDown();
        pair.peer.close();
        close.get(2000, TimeUnit.MILLISECONDS);
        pair.awaitReader();
      }
      finally
      {
        run.countDown();
      }
    }
  }

  @Test(timeOut = 30000)
  public void shutdownWaitsForARegisteredHandshakeToFinishFinalization() throws Exception
  {
    ReplicationServer rs = null;
    Future<?> stopping = null;
    final CountDownLatch shutdownEntered = new CountDownLatch(1);
    final AtomicBoolean finalized = new AtomicBoolean();
    try (Pair pair = new Pair(false, false))
    {
      try
      {
        rs = new ReplicationServer(new ReplServerFakeConfiguration(TestCaseUtils.findFreePort(),
            "gracefulStopFinalizationDb", 0, 9301, 0, 100, new TreeSet<String>()));
        final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
        final DataServerHandler handler = spy(new DataServerHandler(pair.server, 100, rs, 100));
        handler.serverId = 9302;
        handler.serverURL = "127.0.0.1:1636";
        handler.setBaseDNAndDomain(baseDN, true);
        handler.setConsumerActive(true);
        final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
        doAnswer(invocation -> {
          shutdownEntered.countDown();
          assertThat(finalized.get()).as("shutdown ran before handshake finalization").isTrue();
          invocation.callRealMethod();
          return null;
        }).when(handler).shutdown();
        domain.lock();
        try
        {
          domain.register(handler);
          stopping = pair.executor.submit(() -> domain.stopServer(handler, false));
          final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
          while (!handler.shuttingDown())
          {
            assertThat(System.nanoTime() < deadline).as("stop did not begin").isTrue();
            Thread.sleep(10);
          }
          assertThat(shutdownEntered.await(200, TimeUnit.MILLISECONDS))
              .as("shutdown overtook the handshake holding the domain lock").isFalse();
          handler.finalizeStart();
          finalized.set(true);
        }
        finally
        {
          domain.release();
        }
        while (!(pair.peer.receive() instanceof StopMsg))
        {
          // Ignore monitoring frames preceding the stop.
        }
        pair.peer.close();
        stopping.get(2000, TimeUnit.MILLISECONDS);
        assertThat(domain.getConnectedDSs()).doesNotContainKey(9302);
        assertThat(DirectoryServer.getMonitorProviders()).doesNotContainValue(handler);
      }
      finally
      {
        if (stopping != null && !stopping.isDone())
        {
          pair.executor.shutdownNow();
        }
        remove(rs);
      }
    }
  }

  private static DeleteMsg change(CSNGenerator csns) throws Exception
  {
    return new DeleteMsg(DN.valueOf("uid=in-flight," + TEST_ROOT_DN_STRING), csns.newCSN(),
        "00000000-0000-0000-0000-000000000000");
  }

  private interface UpdateConsumer
  {
    void accept(UpdateMsg update) throws Exception;
  }

  private static final class Pair implements Closeable
  {
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
      final Thread thread = new Thread(runnable, "ServerSessionCloseTest worker");
      thread.setDaemon(true);
      return thread;
    });
    private Session server;
    private Session peer;
    private final List<ServerReader> readers = new CopyOnWriteArrayList<>();
    private Socket peerSocket;

    private Pair(boolean encrypted) throws Exception
    {
      this(encrypted, true);
    }

    private Pair(boolean encrypted, boolean startPublisher) throws Exception
    {
      final ReplSessionSecurity security = getReplSessionSecurity();
      peerSocket = new Socket();
      peerSocket.setReceiveBufferSize(8192);
      Socket serverSocket = null;
      boolean connected = false;
      try (ServerSocket listen = TestCaseUtils.bindFreePort())
      {
        listen.setSoTimeout(TIMEOUT_MS);
        peerSocket.connect(new InetSocketAddress("127.0.0.1", listen.getLocalPort()), TIMEOUT_MS);
        final Future<Session> client = executor.submit(() -> security.createClientSession(peerSocket, TIMEOUT_MS));
        serverSocket = listen.accept();
        serverSocket.setSendBufferSize(8192);
        server = security.createServerSession(serverSocket, TIMEOUT_MS);
        peer = client.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(server).isNotNull();
        assertThat(peer).isNotNull();
        server.setProtocolVersion(ProtocolVersion.getCurrentVersion());
        peer.setProtocolVersion(ProtocolVersion.getCurrentVersion());
        // Consume the TLS records of the handshake before switching to plain replication.
        server.publish(new HeartbeatMsg());
        peer.receive();
        peer.publish(new HeartbeatMsg());
        server.receive();
        if (!encrypted)
        {
          server.stopEncryption();
          peer.stopEncryption();
        }
        server.setSoTimeout(0);
        peer.setSoTimeout(TIMEOUT_MS);
        if (startPublisher)
        {
          server.start();
          server.waitForStartup();
        }
        connected = true;
      }
      finally
      {
        if (!connected)
        {
          StaticUtils.close(serverSocket, peerSocket);
          close();
        }
      }
    }

    private void startReader(UpdateConsumer consumer) throws Exception
    {
      startReader(server, peer, consumer);
    }

    private void startReader(Session side, Session other, UpdateConsumer consumer) throws Exception
    {
      final CountDownLatch ready = new CountDownLatch(1);
      final DataServerHandler handler = handler(side, consumer, ready);
      final ServerReader reader = new ServerReader(side, handler);
      readers.add(reader);
      reader.setDaemon(true);
      reader.start();
      other.publish(new WindowMsg(1));
      assertThat(ready.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the production reader did not receive the readiness message")
          .isTrue();
    }

    private DataServerHandler handler(Session side, UpdateConsumer consumer, CountDownLatch ready) throws Exception
    {
      final DataServerHandler handler = mock(DataServerHandler.class);
      final ReplicationServer rs = mock(ReplicationServer.class);
      when(rs.getServerId()).thenReturn(9301);
      final Field field = MessageHandler.class.getDeclaredField("replicationServer");
      field.setAccessible(true);
      field.set(handler, rs);
      when(handler.isDataServer()).thenReturn(true);
      when(handler.getServerId()).thenReturn(9302);
      // Socket errors caused by local close are not a disconnection of this peer.
      when(handler.shuttingDown()).thenAnswer(invocation -> side.closeInitiated());
      when(handler.getBadlyDisconnectedErrorMessage()).thenReturn(ERR_DS_BADLY_DISCONNECTED.get(
          9301, 9302, side.getReadableRemoteAddress(), DN.valueOf(TEST_ROOT_DN_STRING)));
      doAnswer(invocation -> {
        consumer.accept((UpdateMsg) invocation.getArguments()[0]);
        return null;
      }).when(handler).put(any(UpdateMsg.class));
      doAnswer(invocation -> {
        ready.countDown();
        return null;
      }).when(handler).updateWindow(any(WindowMsg.class));
      return handler;
    }

    private void awaitReader() throws Exception
    {
      for (ServerReader reader : readers)
      {
        reader.join(TIMEOUT_MS);
        assertThat(reader.isAlive()).as("the reader outlived the close").isFalse();
      }
    }

    @Override
    public void close()
    {
      if (peer != null)
      {
        peer.abort(new IOException("test cleanup"));
      }
      if (server != null)
      {
        server.abort(new IOException("test cleanup"));
      }
      executor.shutdownNow();
    }
  }
}
