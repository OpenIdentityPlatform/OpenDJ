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
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.util.CollectionUtils.newArrayList;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TestTimer;
import org.testng.annotations.Test;

/**
 * The shutdown of a replication server must let a ReplicaOfflineMsg sent by a collocated
 * directory server be forwarded to the other replication servers of the topology before the
 * server handlers are stopped - stopping them deactivates their consumer, clears their message
 * queue and closes their session, after which the message can no longer be sent.
 * <p>
 * Most tests drive {@link DSRSShutdownSync} directly rather than through a collocated directory
 * server: the contract they pin is when the shutdown of the replication server waits, and how
 * long. {@link #thePeerReceivesTheReplicaOfflineMsgBeforeTheShutdownReturns()} pins the outcome
 * those waits exist for, on a peer connected through the real handshake.
 */
@SuppressWarnings("javadoc")
public class ReplicationServerShutdownSyncTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /** A session end nobody owns is discarded, so its cleanup waits far less than a live one. */
  private static final int DISCARDED_SESSION_TIMEOUT_MS = 2000;
  private static final int REMOTE_RS_ID = 92;
  private static final int REMOTE_DS_ID = 93;
  /** The collocated replica whose ReplicaOfflineMsg the shutdown waits for. */
  private static final int LOCAL_DS_ID = 94;
  /** Time given to the forwarding thread before it releases the shutdown. */
  private static final long FORWARD_DELAY = 500;
  /** How often the domains of {@link #theGracePeriodIsSharedByAllTheDomainsOfOneShutdown()}
   * announce themselves offline again while the shutdown is waiting for them. */
  private static final long REANNOUNCE_INTERVAL = 200;

  @Test
  public void shutdownWaitsForTheReplicaOfflineMsgToBeForwarded() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncWaitDb", 8221);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN, session);

        final long startTime = System.nanoTime();
        shutdownSync.replicaOfflineMsgSent(baseDN, newOfflineCSN());
        replicationServer.shutdown();
        final long elapsed = elapsedMillis(startTime);

        assertThat(elapsed).isGreaterThanOrEqualTo(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void shutdownResumesAsSoonAsTheReplicaOfflineMsgIsForwarded() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    Thread forwarder = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncForwardDb", 8222);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN, session);
        final CSN offlineCSN = newOfflineCSN();
        forwarder = newForwarderThread(shutdownSync, baseDN, offlineCSN);
        shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);

        final long startTime = System.nanoTime();
        forwarder.start();
        replicationServer.shutdown();
        final long elapsed = elapsedMillis(startTime);

        assertThat(elapsed).isGreaterThanOrEqualTo(FORWARD_DELAY)
            .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      joinQuietly(forwarder);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The outcome the grace period exists for, end to end: a peer replication server connected
   * through the real handshake has received the ReplicaOfflineMsg of the collocated replica by
   * the time the shutdown returns.
   * <p>
   * The waiting tests above measure durations only, so they stay green if the wait is moved
   * after the handlers are stopped - which reintroduces OPENDJ-1453 and loses the message. This
   * one fails in that case.
   */
  @Test
  public void thePeerReceivesTheReplicaOfflineMsgBeforeTheShutdownReturns() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    FakePeerReplicationServer peer = null;
    Thread publisher = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer(shutdownSync, "shutdownSyncDeliveryDb", 8226, replicationPort);
      broker = openReplicationSession(baseDN, LOCAL_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      peer = new FakePeerReplicationServer(replicationPort, REMOTE_RS_ID, baseDN, EMPTY_DN_GENID);

      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForConnectedReplicationServer(domain);
      final Future<ReplicaOfflineMsg> received = peer.receiveReplicaOfflineMsg();

      /*
       * The replica announces itself offline once the shutdown of the replication server is
       * already waiting for the message, which is the ordering the grace period exists for.
       */
      final CSN offlineCSN = newOfflineCSN();
      shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
      publisher = newPublisherThread(broker, offlineCSN);

      final long startTime = System.nanoTime();
      publisher.start();
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      final ReplicaOfflineMsg forwarded = received.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(forwarded)
          .as("the peer replication server was never told that the replica went offline, its "
              + "read ended with: %s", peer.readerFailure())
          .isNotNull();
      assertThat(forwarded.getCSN().getServerId()).isEqualTo(LOCAL_DS_ID);
      assertThat(elapsed).isGreaterThanOrEqualTo(FORWARD_DELAY)
          .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      joinQuietly(publisher);
      closeQuietly(peer);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * Only a peer replication server learning about the offline replica ends the wait.
   * ReplicationServerDomain.put() never queues a ReplicaOfflineMsg for a directory server, but
   * the changelog cursor of a directory server which is catching up synthesizes one from the
   * offline CSN of the replica, so the writer serving a directory server can publish it - and
   * the peer replication servers would still know nothing.
   */
  @Test
  public void theForwardToADirectoryServerDoesNotEndTheWait() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer(shutdownSync, "shutdownSyncDataServerForwardDb", 8227, replicationPort);
      broker = openReplicationSession(baseDN, REMOTE_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        // a peer replication server, so that the shutdown does wait for the message: what this
        // test pins is that the directory server receiving it is not what ends that wait
        registerConnectedReplicationServer(replicationServer, baseDN, session);
        final ReplicationServerDomain domain =
            replicationServer.getReplicationServerDomain(baseDN, true);
        final DataServerHandler dsHandler = waitForConnectedDirectoryServer(domain);

        final CSN offlineCSN = newOfflineCSN();
        final long startTime = System.nanoTime();
        shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
        // the very message the shutdown waits for, so only the guard of the writer can save it
        dsHandler.add(new ReplicaOfflineMsg(offlineCSN));

        // the directory server did receive it, so its writer went through the forwarding code
        assertThat(waitForSpecificMsg(broker, ReplicaOfflineMsg.class).getCSN().getServerId())
            .isEqualTo(LOCAL_DS_ID);
        assertThat(elapsedMillis(startTime))
            .as("the fixture must deliver the message well inside the grace period, otherwise "
                + "the wait asserted below cannot be told apart from a slow delivery")
            .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD / 2);

        replicationServer.shutdown();
        final long elapsed = elapsedMillis(startTime);

        assertThat(elapsed)
            .as("the message published to a directory server ended the wait of the shutdown")
            .isGreaterThanOrEqualTo(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * With no other replication server connected there is nobody to forward the message to, so
   * waiting would only delay the shutdown of a standalone server by the whole grace period.
   */
  @Test
  public void shutdownIsNotDelayedWhenNoOtherReplicationServerCanForwardTheMessage() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncAloneDb", 8223);
      replicationServer.getReplicationServerDomain(baseDN, true);

      final long startTime = System.nanoTime();
      shutdownSync.replicaOfflineMsgSent(baseDN, newOfflineCSN());
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      assertThat(elapsed).isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The writer serving a directory server must not hold back the shutdown either: it used to
   * loop on the pending message until the grace period expired, although its handler had already
   * been shut down - which deactivates its consumer and leaves the loop nothing to take.
   */
  @Test
  public void shutdownIsNotDelayedByTheWriterServingADirectoryServer() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer(shutdownSync, "shutdownSyncDataServerDb", 8225, replicationPort);
      broker = openReplicationSession(baseDN, REMOTE_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);

      final long startTime = System.nanoTime();
      shutdownSync.replicaOfflineMsgSent(baseDN, newOfflineCSN());
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      assertThat(elapsed).isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void shutdownIsNotDelayedWhenNoReplicaOfflineMsgIsPending() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncNoMsgDb", 8224);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN, session);

        final long startTime = System.nanoTime();
        replicationServer.shutdown();
        final long elapsed = elapsedMillis(startTime);

        assertThat(elapsed).isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The domains of a replication server are shut down one after the other, so the grace period
   * must bound the whole shutdown and not each of its domains: a process with several base DNs
   * would otherwise pay it once per domain.
   * <p>
   * Both domains keep announcing themselves offline while the shutdown is running, so neither
   * wait can be ended by a forward and each of them runs to its bound - one grace period in
   * total if it is shared, one per domain otherwise.
   */
  @Test
  public void theGracePeriodIsSharedByAllTheDomainsOfOneShutdown() throws Exception
  {
    final DN baseDN1 = DN.valueOf(TEST_ROOT_DN_STRING);
    final DN baseDN2 = DN.valueOf("dc=world,dc=company");
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    final AtomicBoolean stopped = new AtomicBoolean();
    ReplicationServer replicationServer = null;
    Thread reAnnouncer = null;
    try (ServerSocket listen1 = TestCaseUtils.bindFreePort();
        ServerSocket listen2 = TestCaseUtils.bindFreePort())
    {
      listen1.setSoTimeout(SOCKET_TIMEOUT_MS);
      listen2.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncSharedDeadlineDb", 8228);
      final Session[] sessionPair1 = connectSessionPair(listen1, getReplSessionSecurity());
      final Session[] sessionPair2 = connectSessionPair(listen2, getReplSessionSecurity());
      try (Session remoteEnd1 = sessionPair1[0];
          Session session1 = sessionPair1[1];
          Session remoteEnd2 = sessionPair2[0];
          Session session2 = sessionPair2[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN1, session1);
        registerConnectedReplicationServer(replicationServer, baseDN2, session2);
        /*
         * Announce both domains offline here rather than leaving it to the thread below: the
         * wait of the shutdown must be armed whatever that thread has had time to run.
         */
        final CSNGenerator csns = new CSNGenerator(LOCAL_DS_ID, 0);
        shutdownSync.replicaOfflineMsgSent(baseDN1, csns.newCSN());
        shutdownSync.replicaOfflineMsgSent(baseDN2, csns.newCSN());
        reAnnouncer = newReAnnouncerThread(shutdownSync, baseDN1, baseDN2, stopped);
        reAnnouncer.start();

        final long startTime = System.nanoTime();
        replicationServer.shutdown();
        final long elapsed = elapsedMillis(startTime);

        assertThat(elapsed).isGreaterThanOrEqualTo(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
        assertThat(elapsed)
            .as("each domain waited its own grace period instead of sharing one deadline")
            .isLessThan(2 * DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      stopped.set(true);
      joinQuietly(reAnnouncer);
      removeQuietly(replicationServer);
    }
  }

  private ReplicationServer newReplicationServer(DSRSShutdownSync shutdownSync, String dbDirName,
      int serverId) throws Exception
  {
    return newReplicationServer(shutdownSync, dbDirName, serverId, TestCaseUtils.findFreePort());
  }

  private ReplicationServer newReplicationServer(DSRSShutdownSync shutdownSync, String dbDirName,
      int serverId, int replicationPort) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        replicationPort, dbDirName, 0, serverId, 0, 100, new TreeSet<String>()), shutdownSync);
  }

  /**
   * Registers a peer replication server on the domain as the handshake does, but without the
   * protocol exchange: the handler this leaves behind has no writer, which is enough for the
   * tests which only need a domain with a connected peer.
   */
  private void registerConnectedReplicationServer(
      ReplicationServer replicationServer, DN baseDN, Session session) throws Exception
  {
    final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);
    final ReplicationServerHandler rsHandler =
        new ReplicationServerHandler(session, 100, replicationServer, 100);
    rsHandler.serverId = REMOTE_RS_ID;
    rsHandler.serverURL = "127.0.0.1:1636";
    rsHandler.setBaseDNAndDomain(baseDN, false);
    domain.lock();
    try
    {
      domain.register(rsHandler);
    }
    finally
    {
      domain.release();
    }
  }

  private void waitForConnectedReplicationServer(final ReplicationServerDomain domain)
      throws Exception
  {
    newConnectionTimer().repeatUntilSuccess(new TestTimer.CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertThat(domain.getConnectedRSs())
            .as("the peer replication server never connected").containsKey(REMOTE_RS_ID);
      }
    });
  }

  private DataServerHandler waitForConnectedDirectoryServer(final ReplicationServerDomain domain)
      throws Exception
  {
    return newConnectionTimer().repeatUntilSuccess(new Callable<DataServerHandler>()
    {
      @Override
      public DataServerHandler call() throws Exception
      {
        final DataServerHandler dsHandler = domain.getConnectedDSs().get(REMOTE_DS_ID);
        assertThat(dsHandler).as("the directory server never connected").isNotNull();
        return dsHandler;
      }
    });
  }

  private static TestTimer newConnectionTimer()
  {
    return new TestTimer.Builder()
        .maxSleep(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .sleepTimes(10, TimeUnit.MILLISECONDS)
        .toTimer();
  }

  private Thread newForwarderThread(final DSRSShutdownSync shutdownSync, final DN baseDN,
      final CSN offlineCSN)
  {
    return new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        if (!sleepQuietly(FORWARD_DELAY))
        {
          return;
        }
        shutdownSync.replicaOfflineMsgForwarded(baseDN, offlineCSN);
      }
    });
  }

  private Thread newPublisherThread(final ReplicationBroker broker, final CSN offlineCSN)
  {
    return new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        if (!sleepQuietly(FORWARD_DELAY))
        {
          return;
        }
        broker.publish(new ReplicaOfflineMsg(offlineCSN));
      }
    });
  }

  private Thread newReAnnouncerThread(final DSRSShutdownSync shutdownSync, final DN baseDN1,
      final DN baseDN2, final AtomicBoolean stopped)
  {
    return new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        final CSNGenerator csns = new CSNGenerator(LOCAL_DS_ID, 0);
        while (!stopped.get())
        {
          shutdownSync.replicaOfflineMsgSent(baseDN1, csns.newCSN());
          shutdownSync.replicaOfflineMsgSent(baseDN2, csns.newCSN());
          if (!sleepQuietly(REANNOUNCE_INTERVAL))
          {
            return;
          }
        }
      }
    });
  }

  /** Milliseconds elapsed since a {@link System#nanoTime()} reading, the clock the waits use. */
  private static long elapsedMillis(long startTime)
  {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
  }

  /** The CSN of a message the collocated replica announces, as PendingChanges generates it. */
  private static CSN newOfflineCSN()
  {
    return new CSNGenerator(LOCAL_DS_ID, 0).newCSN();
  }

  private static boolean sleepQuietly(long millis)
  {
    try
    {
      Thread.sleep(millis);
      return true;
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Teardown must never mask the primary assertion failure. */
  private void removeQuietly(ReplicationServer replicationServer)
  {
    try
    {
      remove(replicationServer);
    }
    catch (Exception ignored)
    {
    }
  }

  private void joinQuietly(Thread thread)
  {
    if (thread != null)
    {
      try
      {
        thread.join(SOCKET_TIMEOUT_MS);
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void closeQuietly(FakePeerReplicationServer peer)
  {
    if (peer != null)
    {
      peer.close();
    }
  }

  /**
   * Establishes a connected session pair over the given listen socket, as a remote server
   * connecting to the RS would. The TLS negotiation performed by the session factories needs both
   * ends handshaking at the same time, so the client end runs on its own thread.
   *
   * @return the two sessions: the remote (client) end first, then the local (server) end to hand
   *         to the handler under test
   */
  private Session[] connectSessionPair(ServerSocket listenSocket, final ReplSessionSecurity security)
      throws Exception
  {
    final Socket clientSocket = new Socket("127.0.0.1", listenSocket.getLocalPort());
    clientSocket.setTcpNoDelay(true);
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Session> clientEnd = null;
    Socket serverSocket = null;
    Session serverEnd = null;
    boolean connected = false;
    try
    {
      clientEnd = executor.submit(new Callable<Session>()
      {
        @Override
        public Session call() throws Exception
        {
          return security.createClientSession(clientSocket, SOCKET_TIMEOUT_MS);
        }
      });

      serverSocket = listenSocket.accept();
      serverSocket.setTcpNoDelay(true);
      serverEnd = security.createServerSession(serverSocket, SOCKET_TIMEOUT_MS);
      assertThat(serverEnd).as("could not create a session for the handler under test").isNotNull();

      final Session[] sessionPair =
          new Session[] { clientEnd.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS), serverEnd };
      connected = true;
      return sessionPair;
    }
    finally
    {
      if (!connected)
      {
        // Nobody owns either end yet: close whatever they managed to create.
        closeClientEndQuietly(clientEnd, clientSocket);
        closeServerEndQuietly(serverEnd, serverSocket);
      }
      executor.shutdown();
    }
  }

  private void closeServerEndQuietly(Session serverEnd, Socket serverSocket)
  {
    if (serverEnd != null)
    {
      serverEnd.close();
    }
    else
    {
      StaticUtils.close(serverSocket);
    }
  }

  private void closeClientEndQuietly(Future<Session> clientEnd, Socket clientSocket)
  {
    if (clientEnd != null)
    {
      try
      {
        final Session session = clientEnd.get(DISCARDED_SESSION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (session != null)
        {
          session.close();
        }
      }
      catch (Exception ignored)
      {
        clientEnd.cancel(true);
      }
    }
    StaticUtils.close(clientSocket);
  }

  /**
   * A peer replication server which connects to the replication server under test and completes
   * the handshake, so that the handler it leaves behind on the domain has a real writer and can
   * actually forward what the domain pushes to it.
   */
  private static final class FakePeerReplicationServer
  {
    private final Session session;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    /** Why the peer stopped reading, so that a missing message can be told from a failed one. */
    private volatile Exception readerFailure;

    FakePeerReplicationServer(int replicationPort, int serverId, DN baseDN, long generationId)
        throws Exception
    {
      final Socket socket = new Socket();
      Session newSession = null;
      boolean handshaken = false;
      try
      {
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress("127.0.0.1", replicationPort), SOCKET_TIMEOUT_MS);
        newSession = getReplSessionSecurity().createClientSession(socket, SOCKET_TIMEOUT_MS);

        final String serverURL = "127.0.0.1:" + socket.getLocalPort();
        final byte groupId = (byte) 1;
        newSession.publish(new ReplServerStartMsg(serverId, serverURL, baseDN, 100,
            new ServerState(), generationId, false, groupId, 5000));
        final ReplServerStartMsg inStartMsg =
            waitForSpecificMsg(newSession, ReplServerStartMsg.class);
        if (!inStartMsg.getSSLEncryption())
        {
          newSession.stopEncryption();
        }
        newSession.publish(new TopologyMsg(null,
            newArrayList(new RSInfo(serverId, serverURL, generationId, groupId, 1))));
        waitForSpecificMsg(newSession, TopologyMsg.class);
        handshaken = true;
      }
      finally
      {
        if (!handshaken)
        {
          // The caller has no handle on this peer yet, so nothing else would close it.
          reader.shutdownNow();
          if (newSession != null)
          {
            newSession.close();
          }
          else
          {
            StaticUtils.close(socket);
          }
        }
      }
      session = newSession;
    }

    /** Returns the first ReplicaOfflineMsg this peer receives, or null if its session ends first. */
    Future<ReplicaOfflineMsg> receiveReplicaOfflineMsg()
    {
      return reader.submit(new Callable<ReplicaOfflineMsg>()
      {
        @Override
        public ReplicaOfflineMsg call()
        {
          try
          {
            while (true)
            {
              final ReplicationMsg msg = session.receive();
              if (msg instanceof ReplicaOfflineMsg)
              {
                return (ReplicaOfflineMsg) msg;
              }
            }
          }
          catch (Exception e)
          {
            // The session is closed when the replication server completes its shutdown: whatever
            // has not arrived by then never will.
            readerFailure = e;
            return null;
          }
        }
      });
    }

    /** Returns what ended the read of this peer, null if nothing did. */
    Exception readerFailure()
    {
      return readerFailure;
    }

    void close()
    {
      reader.shutdownNow();
      session.close();
    }
  }
}
