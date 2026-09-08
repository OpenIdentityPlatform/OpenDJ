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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.WindowMsg;
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
 * long. {@link #thePeerReceivesTheReplicaOfflineMsgBeforeTheShutdownReturns()} and
 * {@link #theShutdownWaitsForEveryPeerToBeToldTheReplicaWentOffline()} pin the outcome those
 * waits exist for, on peers connected through the real handshake.
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
  /** The peer replication server whose writer is held back by a full send window. */
  private static final int HELD_BACK_RS_ID = 95;
  /** The peer replication server whose handshake is aborted while the message is pushed. */
  private static final int ABORTED_RS_ID = 96;
  /** Send window a peer advertises when nothing has to hold its writer back. */
  private static final int PEER_WINDOW = 100;
  /**
   * Send window of the peer which is held back: one change fills it, and the message which
   * follows stays with its writer until the peer gives it credit again.
   */
  private static final int HELD_BACK_PEER_WINDOW = 1;
  /** Time given to the forwarding thread before it releases the shutdown. */
  private static final long FORWARD_DELAY = 500;
  /**
   * Time given to a writer to reach the message it was handed once its send window is opened,
   * well short of the grace period so that a writer which never gets there is reported as such
   * rather than as a shutdown which waited.
   */
  private static final long WRITER_REACTION_TIMEOUT_MS = 10000;
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
      waitForConnectedReplicationServer(domain, REMOTE_RS_ID);
      final Future<ReplicaOfflineMsg> received = peer.receive(ReplicaOfflineMsg.class);

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
              + "read ended with: %s", peer.failure())
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
   * The grace period covers every peer replication server, not only the fastest of them. The
   * message is queued for each of them and published by its own writer, and the shutdown clears
   * the queue and closes the session of whoever has not published it yet: a peer whose writer is
   * held back would be left unaware that the replica went offline, and its change number indexer
   * would keep the medium consistency point pinned to the last change of that replica.
   */
  @Test
  public void theShutdownWaitsForEveryPeerToBeToldTheReplicaWentOffline() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final RecordingShutdownSync shutdownSync = new RecordingShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    FakePeerReplicationServer peer = null;
    FakePeerReplicationServer heldBackPeer = null;
    Thread windowOpener = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer(shutdownSync, "shutdownSyncEveryPeerDb", 8229, replicationPort);
      broker =
          openReplicationSession(baseDN, LOCAL_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      peer = new FakePeerReplicationServer(
          replicationPort, REMOTE_RS_ID, baseDN, EMPTY_DN_GENID, PEER_WINDOW);
      heldBackPeer = new FakePeerReplicationServer(
          replicationPort, HELD_BACK_RS_ID, baseDN, EMPTY_DN_GENID, HELD_BACK_PEER_WINDOW);

      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForConnectedReplicationServer(domain, REMOTE_RS_ID);
      waitForConnectedReplicationServer(domain, HELD_BACK_RS_ID);

      /*
       * One change fills the send window of the held back peer: its writer publishes that one and
       * then blocks on the permit of the next message, so the ReplicaOfflineMsg stays with it
       * while its neighbour forwards the same message right away.
       */
      final CSNGenerator csns = new CSNGenerator(LOCAL_DS_ID, 0);
      final Future<DeleteMsg> windowFiller = heldBackPeer.receive(DeleteMsg.class);
      broker.publish(new DeleteMsg(DN.valueOf("uid=offline," + TEST_ROOT_DN_STRING),
          csns.newCSN(), "offline-entry-uuid"));
      assertThat(windowFiller.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the send window of the held back peer was never filled, its exchange ended "
              + "with: %s", heldBackPeer.failure())
          .isNotNull();

      final Future<ReplicaOfflineMsg> received = peer.receive(ReplicaOfflineMsg.class);
      final Future<ReplicaOfflineMsg> receivedWhenHeldBack =
          heldBackPeer.receive(ReplicaOfflineMsg.class);
      final CSN offlineCSN = csns.newCSN();
      shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
      broker.publish(new ReplicaOfflineMsg(offlineCSN));
      windowOpener = newWindowOpenerThread(heldBackPeer);

      final long startTime = System.nanoTime();
      windowOpener.start();
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      /*
       * The barrier first, because it is the one the shutdown is made of and it cannot race:
       * the wait ends when both writers have reported, or when the grace period runs out, and
       * the duration below tells the two apart.
       */
      assertThat(shutdownSync.forwardedBy())
          .as("the wait ended on the first peer forwarding the message, without the writer of "
              + "the peer which was held back ever reporting one")
          .contains(REMOTE_RS_ID, HELD_BACK_RS_ID);
      assertThat(elapsed).isGreaterThanOrEqualTo(FORWARD_DELAY)
          .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      assertThat(received.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the peer which was not held back never received the message, its read ended "
              + "with: %s", peer.failure())
          .isNotNull();
      /*
       * The forward asserted above proves the message reached the Session, not the wire: close()
       * discards whatever is still in its send queue without draining it, which is the
       * limitation issue #919 recorded. If this is the only assertion which fails, that window
       * is the explanation rather than the granularity of the barrier.
       */
      assertThat(receivedWhenHeldBack.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the peer which was held back never learned that the replica went offline, "
              + "although its writer reported the forward, its exchange ended with: %s",
              heldBackPeer.failure())
          .isNotNull();
    }
    finally
    {
      joinQuietly(windowOpener);
      closeQuietly(heldBackPeer);
      closeQuietly(peer);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * A message its writer drops on the way out will never be forwarded, so the shutdown must stop
   * waiting for the peer it was queued for. The filter of the writer is wider than the one
   * ReplicationServerDomain.put() applies when it queues the message - it drops anything for a
   * peer whose generation id is unknown as well - so a peer can be given a message which is then
   * dropped, and nothing would ever report a forward for it.
   */
  @Test
  public void theShutdownStopsWaitingForAPeerWhoseMessageTheWriterDropped() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final RecordingShutdownSync shutdownSync = new RecordingShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    FakePeerReplicationServer peer = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer(shutdownSync, "shutdownSyncDroppedMsgDb", 8230, replicationPort);
      broker =
          openReplicationSession(baseDN, LOCAL_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      peer = new FakePeerReplicationServer(
          replicationPort, REMOTE_RS_ID, baseDN, EMPTY_DN_GENID, HELD_BACK_PEER_WINDOW);

      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForConnectedReplicationServer(domain, REMOTE_RS_ID);
      final ReplicationServerHandler rsHandler = domain.getConnectedRSs().get(REMOTE_RS_ID);

      /*
       * One change fills the send window of the peer, so the message which follows stays with
       * its writer until the window is opened again. The writer takes the message off the queue
       * before it blocks on the permit and evaluates its filter only once it has it, which is
       * what makes the generation id below take effect on a message already queued.
       */
      final CSNGenerator csns = new CSNGenerator(LOCAL_DS_ID, 0);
      final Future<DeleteMsg> windowFiller = peer.receive(DeleteMsg.class);
      broker.publish(new DeleteMsg(DN.valueOf("uid=offline," + TEST_ROOT_DN_STRING),
          csns.newCSN(), "offline-entry-uuid"));
      assertThat(windowFiller.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the send window of the peer was never filled, its exchange ended with: %s",
              peer.failure())
          .isNotNull();

      final CSN offlineCSN = csns.newCSN();
      shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
      broker.publish(new ReplicaOfflineMsg(offlineCSN));
      shutdownSync.awaitDispatch();
      assertThat(shutdownSync.dispatchedTo())
          .as("the message was never queued for the peer, so its writer has nothing to drop")
          .contains(REMOTE_RS_ID);

      // the peer no longer shares the generation id of the domain, so its writer drops what was
      // queued for it before that - a filter wider than the one put() applied
      rsHandler.setGenerationId(domain.getGenerationId() + 1);
      peer.openSendWindow(PEER_WINDOW);
      awaitGiveUpOn(shutdownSync, REMOTE_RS_ID,
          "the writer dropped the message without telling the shutdown to stop waiting for the "
              + "peer it was queued for");

      final long startTime = System.nanoTime();
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      assertThat(elapsed)
          .as("the shutdown kept waiting for a forward its own writer had already dropped")
          .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      closeQuietly(peer);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * A peer whose handshake is aborted while the message is being pushed must not be waited for.
   * <p>
   * put() reads the peers of the domain, records them as the recipients of the message and only
   * then queues it for each of them. unregisterFailedHandshake() runs on the handshake thread
   * and takes no domain lock, so it can land in between: its own give-up then finds nothing
   * recorded yet and does nothing. Nothing else would ever strike that peer off - the reader and
   * writer threads whose death reaches stopServer() were never started for a handshake which was
   * aborted - so the shutdown waits out its whole grace period for a forward which cannot come.
   */
  @Test
  public void theShutdownStopsWaitingForAPeerWhoseHandshakeWasAborted() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final RecordingShutdownSync shutdownSync = new RecordingShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    FakePeerReplicationServer peer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer(
          shutdownSync, "shutdownSyncAbortedHandshakeDb", 8231, replicationPort);
      broker =
          openReplicationSession(baseDN, LOCAL_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      peer = new FakePeerReplicationServer(replicationPort, REMOTE_RS_ID, baseDN, EMPTY_DN_GENID);

      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForConnectedReplicationServer(domain, REMOTE_RS_ID);

      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        // a second peer, registered as a handshake does just before it fails
        final ReplicationServerHandler aborting = registerConnectedReplicationServer(
            replicationServer, baseDN, session, ABORTED_RS_ID);
        aborting.setGenerationId(domain.getGenerationId());
        shutdownSync.runWhileDispatching(new Runnable()
        {
          @Override
          public void run()
          {
            domain.unregisterFailedHandshake(aborting);
          }
        });

        final Future<ReplicaOfflineMsg> received = peer.receive(ReplicaOfflineMsg.class);
        final CSN offlineCSN = newOfflineCSN();
        shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
        broker.publish(new ReplicaOfflineMsg(offlineCSN));
        shutdownSync.awaitDispatch();

        final long startTime = System.nanoTime();
        replicationServer.shutdown();
        final long elapsed = elapsedMillis(startTime);

        assertThat(shutdownSync.dispatchedTo())
            .as("the peer whose handshake was aborted was not recorded among the recipients, so "
                + "this test never reproduced the window it is about")
            .contains(REMOTE_RS_ID, ABORTED_RS_ID);
        assertThat(elapsed)
            .as("the shutdown waited for a peer whose handshake had been aborted, and which "
                + "nothing else will ever strike off")
            .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
        assertThat(received.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            .as("the peer which was still connected never learned that the replica went "
                + "offline, its read ended with: %s", peer.failure())
            .isNotNull();
      }
    }
    finally
    {
      closeQuietly(peer);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * A peer which disconnects during the grace period can no longer forward what it was given -
   * its session is closed under its writer - so stopServer() must strike it off rather than let
   * the shutdown wait out the rest of its window for a peer which is already gone.
   */
  @Test
  public void theShutdownStopsWaitingForAPeerWhichDisconnected() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final RecordingShutdownSync shutdownSync = new RecordingShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    FakePeerReplicationServer peer = null;
    FakePeerReplicationServer heldBackPeer = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer(
          shutdownSync, "shutdownSyncDisconnectedPeerDb", 8232, replicationPort);
      broker =
          openReplicationSession(baseDN, LOCAL_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      peer = new FakePeerReplicationServer(
          replicationPort, REMOTE_RS_ID, baseDN, EMPTY_DN_GENID, PEER_WINDOW);
      heldBackPeer = new FakePeerReplicationServer(
          replicationPort, HELD_BACK_RS_ID, baseDN, EMPTY_DN_GENID, HELD_BACK_PEER_WINDOW);

      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForConnectedReplicationServer(domain, REMOTE_RS_ID);
      waitForConnectedReplicationServer(domain, HELD_BACK_RS_ID);

      // the peer which disconnects is held back by a full send window, so that it cannot have
      // forwarded the message before it goes: what ends the wait must be the give-up
      final CSNGenerator csns = new CSNGenerator(LOCAL_DS_ID, 0);
      final Future<DeleteMsg> windowFiller = heldBackPeer.receive(DeleteMsg.class);
      broker.publish(new DeleteMsg(DN.valueOf("uid=offline," + TEST_ROOT_DN_STRING),
          csns.newCSN(), "offline-entry-uuid"));
      assertThat(windowFiller.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the send window of the held back peer was never filled, its exchange ended "
              + "with: %s", heldBackPeer.failure())
          .isNotNull();

      final Future<ReplicaOfflineMsg> received = peer.receive(ReplicaOfflineMsg.class);
      final CSN offlineCSN = csns.newCSN();
      shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
      broker.publish(new ReplicaOfflineMsg(offlineCSN));
      shutdownSync.awaitDispatch();
      heldBackPeer.close();

      final long startTime = System.nanoTime();
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      assertThat(shutdownSync.dispatchedTo())
          .as("the message was not queued for the peer which then disconnected, so this test "
              + "never reproduced what it is about")
          .contains(REMOTE_RS_ID, HELD_BACK_RS_ID);
      assertThat(elapsed)
          .as("the shutdown kept waiting for a forward from a peer which had disconnected")
          .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      assertThat(received.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the peer which stayed connected never received the message, its read ended "
              + "with: %s", peer.failure())
          .isNotNull();
    }
    finally
    {
      closeQuietly(heldBackPeer);
      closeQuietly(peer);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * A peer which does not share the generation id of the domain is not given the message, so it
   * must not be recorded among the peers the shutdown waits for. The caller side check on
   * getConnectedRSs() does not cover this: such a peer is connected, and would make the domain
   * spend its grace period on a message it was never queued.
   */
  @Test
  public void theMessageIsNotQueuedForAPeerWhoseGenerationIdDiffers() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final RecordingShutdownSync shutdownSync = new RecordingShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    FakePeerReplicationServer peer = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer(
          shutdownSync, "shutdownSyncOtherGenerationIdDb", 8233, replicationPort);
      broker =
          openReplicationSession(baseDN, LOCAL_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);
      peer = new FakePeerReplicationServer(replicationPort, REMOTE_RS_ID, baseDN, EMPTY_DN_GENID);

      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForConnectedReplicationServer(domain, REMOTE_RS_ID);
      domain.getConnectedRSs().get(REMOTE_RS_ID).setGenerationId(domain.getGenerationId() + 1);

      final CSN offlineCSN = newOfflineCSN();
      shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
      broker.publish(new ReplicaOfflineMsg(offlineCSN));
      shutdownSync.awaitDispatch();

      final long startTime = System.nanoTime();
      replicationServer.shutdown();
      final long elapsed = elapsedMillis(startTime);

      assertThat(shutdownSync.dispatchedTo())
          .as("the message was recorded as queued for a peer which does not share the "
              + "generation id of the domain, and which it was never queued for")
          .isEmpty();
      assertThat(elapsed)
          .as("the shutdown waited for a peer the message was not queued for")
          .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      closeQuietly(peer);
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
  private ReplicationServerHandler registerConnectedReplicationServer(
      ReplicationServer replicationServer, DN baseDN, Session session) throws Exception
  {
    return registerConnectedReplicationServer(replicationServer, baseDN, session, REMOTE_RS_ID);
  }

  private ReplicationServerHandler registerConnectedReplicationServer(
      ReplicationServer replicationServer, DN baseDN, Session session, int serverId)
      throws Exception
  {
    final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);
    final ReplicationServerHandler rsHandler =
        new ReplicationServerHandler(session, 100, replicationServer, 100);
    rsHandler.serverId = serverId;
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
    return rsHandler;
  }

  /** Waits for the barrier to have been told that this peer will not forward the message. */
  private void awaitGiveUpOn(final RecordingShutdownSync shutdownSync, final int serverId,
      final String reason) throws Exception
  {
    new TestTimer.Builder()
        .maxSleep(WRITER_REACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .sleepTimes(10, TimeUnit.MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(new TestTimer.CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertThat(shutdownSync.gaveUpOn()).as(reason).contains(serverId);
          }
        });
  }

  private void waitForConnectedReplicationServer(
      final ReplicationServerDomain domain, final int serverId) throws Exception
  {
    newConnectionTimer().repeatUntilSuccess(new TestTimer.CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertThat(domain.getConnectedRSs())
            .as("the peer replication server %s never connected", serverId).containsKey(serverId);
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
        shutdownSync.replicaOfflineMsgForwarded(baseDN, offlineCSN, REMOTE_RS_ID);
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

  /**
   * Gives the held back peer credit to receive again, once the shutdown has had time to end on
   * the forward of the peer which was not held back.
   */
  private Thread newWindowOpenerThread(final FakePeerReplicationServer heldBackPeer)
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
        heldBackPeer.openSendWindow(PEER_WINDOW);
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
   * A synchronization object which records what the production code reports to it, so that a
   * test can assert on the barrier the shutdown is made of rather than only on how long it took,
   * and can interleave a teardown with the dispatch of a message.
   */
  private static final class RecordingShutdownSync extends DSRSShutdownSync
  {
    private final List<Integer> dispatchedTo = new CopyOnWriteArrayList<>();
    private final List<Integer> forwardedBy = new CopyOnWriteArrayList<>();
    private final List<Integer> gaveUpOn = new CopyOnWriteArrayList<>();
    private final CountDownLatch dispatched = new CountDownLatch(1);
    /** Runs inside the next dispatch, before the recipients are recorded. */
    private final AtomicReference<Runnable> whileDispatching = new AtomicReference<>();

    /**
     * Runs the provided action inside the next dispatch, before the recipients are recorded:
     * the window in which put() has read the peers of the domain and nothing knows yet which of
     * them the message is for.
     */
    void runWhileDispatching(Runnable action)
    {
      whileDispatching.set(action);
    }

    @Override
    public void replicaOfflineMsgDispatched(
        DN baseDN, CSN offlineCSN, Collection<Integer> replicationServerIds)
    {
      final Runnable action = whileDispatching.getAndSet(null);
      if (action != null)
      {
        action.run();
      }
      dispatchedTo.addAll(replicationServerIds);
      super.replicaOfflineMsgDispatched(baseDN, offlineCSN, replicationServerIds);
      dispatched.countDown();
    }

    @Override
    public void replicaOfflineMsgForwarded(DN baseDN, CSN forwardedCSN, int replicationServerId)
    {
      forwardedBy.add(replicationServerId);
      super.replicaOfflineMsgForwarded(baseDN, forwardedCSN, replicationServerId);
    }

    @Override
    public void replicaOfflineMsgNotForwarded(DN baseDN, int replicationServerId)
    {
      gaveUpOn.add(replicationServerId);
      super.replicaOfflineMsgNotForwarded(baseDN, replicationServerId);
    }

    /** Waits for put() to have pushed a ReplicaOfflineMsg to the peers of its domain. */
    void awaitDispatch() throws InterruptedException
    {
      assertThat(dispatched.await(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("no ReplicaOfflineMsg was ever pushed to the peers of the domain")
          .isTrue();
    }

    /** The peers the message was recorded as queued for. */
    List<Integer> dispatchedTo()
    {
      return dispatchedTo;
    }

    /** The peers whose writer reported having forwarded the message. */
    List<Integer> forwardedBy()
    {
      return forwardedBy;
    }

    /** The peers the shutdown was told to stop waiting for. */
    List<Integer> gaveUpOn()
    {
      return gaveUpOn;
    }
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
    /**
     * What ended the exchange with the replication server, so that a message which never arrived
     * can be told from an exchange which failed. The reader and the thread which opens the send
     * window both report here, and the first failure is the one kept: it is the one which
     * explains the rest.
     */
    private final AtomicReference<Exception> failure = new AtomicReference<>();

    FakePeerReplicationServer(int replicationPort, int serverId, DN baseDN, long generationId)
        throws Exception
    {
      this(replicationPort, serverId, baseDN, generationId, PEER_WINDOW);
    }

    FakePeerReplicationServer(int replicationPort, int serverId, DN baseDN, long generationId,
        int windowSize) throws Exception
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
        newSession.publish(new ReplServerStartMsg(serverId, serverURL, baseDN, windowSize,
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

    /**
     * Returns the first message of the given type this peer receives, or null if its session
     * ends first.
     */
    <T extends ReplicationMsg> Future<T> receive(final Class<T> msgClass)
    {
      return reader.submit(new Callable<T>()
      {
        @Override
        public T call()
        {
          try
          {
            while (true)
            {
              final ReplicationMsg msg = session.receive();
              if (msgClass.isInstance(msg))
              {
                return msgClass.cast(msg);
              }
            }
          }
          catch (Exception e)
          {
            // The session is closed when the replication server completes its shutdown: whatever
            // has not arrived by then never will.
            failed(e);
            return null;
          }
        }
      });
    }

    /** Gives the replication server credit to publish again, as a peer which keeps up does. */
    void openSendWindow(int credits)
    {
      try
      {
        session.publish(new WindowMsg(credits));
      }
      catch (IOException e)
      {
        failed(e);
      }
    }

    private void failed(Exception e)
    {
      failure.compareAndSet(null, e);
    }

    /** Returns what ended the exchange with this peer, null if nothing did. */
    Exception failure()
    {
      return failure.get();
    }

    void close()
    {
      reader.shutdownNow();
      session.close();
    }
  }
}
