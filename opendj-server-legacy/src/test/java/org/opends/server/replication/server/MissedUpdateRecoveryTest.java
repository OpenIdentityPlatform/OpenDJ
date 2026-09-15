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
import static org.opends.messages.ReplicationMessages.WARN_CHANGELOG_READ_AGAIN_FOR_MISSING_CHANGES;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

/**
 * A replica which is served from the in-memory message queue of its handler - it is "following" -
 * is given every change newer than its state by {@code ReplicationServerDomain.put()}. When that
 * does not happen, the handler must read the changelog again instead of waiting forever for a
 * delivery which is not coming: a change missed once would otherwise never be sent again, and the
 * replication server would consider the replica up to date - see issue #963.
 * <p>
 * The handler notices it is behind on the ticks of its 500 ms wait on an empty queue, and only
 * concludes anything about a change it has seen the domain hold over a whole tick: the state of
 * the domain is advanced slightly before the change is queued, and a change seen on one tick only
 * may simply be on its way. The watch windows below are sized in ticks accordingly, and a test
 * which holds a change on its way hands it to the queue once a tick has seen it in the changelog:
 * the check has then run with the change on its way, by construction rather than by wall clock.
 */
@SuppressWarnings("javadoc")
public class MissedUpdateRecoveryTest extends ReplicationTestCase
{
  private static final int RS_ID = 105;
  private static final int PEER_RS_ID = 109;
  private static final int THIRD_RS_ID = 110;
  private static final int DS_ID = 106;
  /** The replica whose generation id does not match the one of the domain. */
  private static final int BAD_GENID_DS_ID = 107;
  /** The replica the changes of this test come from. */
  private static final int PUBLISHER_DS_ID = 108;
  /**
   * A second source of changes, for the tests which hold a change of one replica missing while
   * the changes of another are delivered: a state holds one CSN per replica, so a newer change of
   * the same replica would cover the missing one.
   */
  private static final int OTHER_PUBLISHER_DS_ID = 111;
  private static final int WINDOW_SIZE = 100;
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /**
   * How long a change published straight into the changelog is given to reach the replica. The
   * handler notices it is behind on the next tick of its 500 ms wait on an empty queue.
   */
  private static final long DELIVERY_TIMEOUT_MS = 10000;
  /** How long a handler which must not read the changelog again is watched for: five ticks. */
  private static final long NO_DELIVERY_WATCH_MS = 2500;
  /** How many changes are handed to the queue late by the tests which do so. */
  private static final int CHANGES_ON_THEIR_WAY = 5;

  /**
   * The regression this test pins: the change is in the changelog and the state of the replica is
   * behind it, so the replica must be sent the change, whatever kept it out of the message queue
   * of its handler. The warning which reports it names the state of the replica and the state of
   * the domain, and is written once.
   */
  @Test
  public void aFollowingReplicaIsSentAChangeItsQueueNeverReceived() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    Receiver receiver = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateRecoveryDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final DataServerHandler handler = waitForFollowingDirectoryServer(domain, DS_ID);
      // a change of the replica itself, so that its state is not empty in the warning
      final CSN ownCSN = new CSN(TimeThread.getTime(), 1, DS_ID);
      broker.publish(newDeleteMsg(baseDN, "cn=own", ownCSN));
      waitForServerState(handler, ownCSN);
      receiver = new Receiver(broker);

      final CSN csn = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, csn);

      final UpdateMsg receivedMsg = receiver.next(DELIVERY_TIMEOUT_MS);
      assertThat(receivedMsg)
          .as("the replica was never sent the change the changelog holds for it").isNotNull();
      assertThat(receivedMsg.getCSN()).isEqualTo(csn);

      final List<String> warnings = warningsFor(handler, csn);
      assertThat(new HashSet<>(warnings))
          .as("the changelog is read again once for a change which is missing once").hasSize(1);
      assertThat(warnings.get(0))
          .as("the warning names the state of the replica and the state of the domain")
          .contains(ownCSN.toString())
          .contains(csn.toString());
      assertThat(handler.isFollowing())
          .as("the replica is served from the queue again once it has been sent the change")
          .isTrue();
    }
    finally
    {
      stop(broker);
      stop(receiver);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The domain does not hand its updates to a replica whose generation id does not match, and the
   * changelog must not be read on behalf of such a replica either: the writer would drop every
   * change it read while the state of the handler moved past it, and the changes would then be
   * missing from the delivery which follows the reinitialization of the replica.
   */
  @Test
  public void aReplicaTheDomainDoesNotFeedIsLeftAlone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    ReplicationBroker badGenIdBroker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateBadGenIdDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      // a change the domain holds and must not hand to the bad-generation-id replica opened below
      broker.publish(newDeleteMsg(baseDN, "cn=first", new CSN(TimeThread.getTime(), 1, DS_ID)));
      badGenIdBroker = openReplicationSession(
          baseDN, BAD_GENID_DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID + 1);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForFollowingDirectoryServer(domain, DS_ID);
      final DataServerHandler badGenIdHandler = waitForFollowingDirectoryServer(domain, BAD_GENID_DS_ID);
      assertThat(badGenIdHandler.getStatus())
          .as("the second replica was expected to be refused the changes of the domain")
          .isEqualTo(ServerStatus.BAD_GEN_ID_STATUS);

      final CSN csn = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, csn);

      assertLeftAlone(badGenIdHandler, csn);
    }
    finally
    {
      stop(badGenIdBroker);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The mirror of {@link #aReplicaTheDomainDoesNotFeedIsLeftAlone()} for the other status the
   * domain filters out: a replica which is being initialized is not sent the changes of the
   * domain, and the changelog must not be read on its behalf either.
   */
  @Test
  public void aReplicaBeingInitializedIsLeftAlone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateFullUpdateDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final DataServerHandler handler = waitForFollowingDirectoryServer(domain, DS_ID);
      broker.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);
      waitForStatus(handler, ServerStatus.FULL_UPDATE_STATUS);

      final CSN csn = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, csn);

      assertLeftAlone(handler, csn);
    }
    finally
    {
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The state of the domain is advanced slightly before the change is queued, so a change seen
   * on one tick of the wait only is on its way and not missing: the changelog is not read again
   * for it, and it reaches the replica once, from the queue.
   */
  @Test
  public void aChangeOnItsWayToTheQueueIsNotReadAgain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    Receiver receiver = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateOnItsWayDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final DataServerHandler handler = waitForFollowingDirectoryServer(domain, DS_ID);
      receiver = new Receiver(broker);

      final List<CSN> csns = new ArrayList<>();
      for (int i = 1; i <= CHANGES_ON_THEIR_WAY; i++)
      {
        final CSN csn = new CSN(TimeThread.getTime(), i, PUBLISHER_DS_ID);
        csns.add(csn);
        publishThenQueueLate(replicationServer, handler, baseDN, csn);
        final UpdateMsg receivedMsg = receiver.next(DELIVERY_TIMEOUT_MS);
        assertThat(receivedMsg).as("the replica was not sent change " + i).isNotNull();
        assertThat(receivedMsg.getCSN()).isEqualTo(csn);
      }

      assertThat(receiver.next(NO_DELIVERY_WATCH_MS))
          .as("the replica was sent a change a second time").isNull();
      for (CSN csn : csns)
      {
        assertThat(warningsFor(handler, csn))
            .as("the changelog was read again for a change which was on its way").isEmpty();
      }
      assertThat(handler.isFollowing()).isTrue();
    }
    finally
    {
      stop(broker);
      stop(receiver);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The ordinary path: a change the domain hands to the queue is sent from the queue, and the
   * replica stays served from it - nothing is read again and nothing is reported.
   */
  @Test
  public void aChangeTheDomainQueuesKeepsTheReplicaFollowing() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    ReplicationBroker publisher = null;
    Receiver receiver = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateQueuedDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      publisher = openReplicationSession(
          baseDN, PUBLISHER_DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final DataServerHandler handler = waitForFollowingDirectoryServer(domain, DS_ID);
      receiver = new Receiver(broker);

      final CSN csn = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publisher.publish(newDeleteMsg(baseDN, "cn=queued", csn));

      final UpdateMsg receivedMsg = receiver.next(DELIVERY_TIMEOUT_MS);
      assertThat(receivedMsg).as("the replica was not sent the change").isNotNull();
      assertThat(receivedMsg.getCSN()).isEqualTo(csn);
      assertThat(receiver.next(NO_DELIVERY_WATCH_MS))
          .as("the replica was sent the change a second time").isNull();
      assertThat(warningsFor(handler, csn))
          .as("the changelog was read again for a change the queue delivered").isEmpty();
      assertThat(handler.isFollowing()).isTrue();
    }
    finally
    {
      stop(broker, publisher);
      stop(receiver);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The changelog is read again once per advance of the state of the domain. A gap it was already
   * read for is not read for again while the domain holds nothing new, whatever the state of the
   * replica says - and it is read for again as soon as the domain receives something.
   * <p>
   * The gap is reopened by hand: a changelog which holds a change and cannot yield it cannot be
   * built from outside, so what is pinned here is that the handler does not open a cursor twice
   * for one state of the domain.
   */
  @Test
  public void aGapAlreadyReadForIsNotReadAgainUntilTheDomainAdvances() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    Receiver receiver = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateThrottleDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final DataServerHandler handler = waitForFollowingDirectoryServer(domain, DS_ID);
      receiver = new Receiver(broker);

      final CSN missed = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, missed);
      assertThat(receiver.next(DELIVERY_TIMEOUT_MS)).isNotNull();
      waitForFollowing(handler);
      final int warningsBefore = warningsFor(handler, missed).size();

      // the same gap again, with the state of the domain as it was when the changelog was read
      reopenGap(handler, missed);
      assertThat(receiver.next(NO_DELIVERY_WATCH_MS))
          .as("the changelog was read again for a state it was already read for").isNull();
      assertThat(warningsFor(handler, missed)).hasSize(warningsBefore);
      assertThat(handler.isFollowing()).isTrue();

      final CSN next = new CSN(TimeThread.getTime(), 2, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, next);
      final List<CSN> received = new ArrayList<>();
      for (int i = 0; i < 2; i++)
      {
        final UpdateMsg receivedMsg = receiver.next(DELIVERY_TIMEOUT_MS);
        assertThat(receivedMsg)
            .as("the changelog was not read again once the domain received something new")
            .isNotNull();
        received.add(receivedMsg.getCSN());
      }
      assertThat(received).containsExactly(missed, next);
    }
    finally
    {
      stop(broker);
      stop(receiver);
      removeQuietly(replicationServer);
    }
  }

  /**
   * Next to a gap the changelog was already read for, a change on its way to the queue is still
   * on its way: the state of the domain is ahead on every tick because of the gap, and that must
   * not turn the tick which sees the new change into the one which reports it. The report which
   * follows the advance of the domain comes once the change has been queued and sent, and names
   * a state of the replica which holds it.
   */
  @Test
  public void aChangeOnItsWayIsNotReportedMissingNextToAGapAlreadyReadFor() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    Receiver receiver = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateGapOnItsWayDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final DataServerHandler handler = waitForFollowingDirectoryServer(domain, DS_ID);
      receiver = new Receiver(broker);

      final CSN missed = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, missed);
      assertThat(receiver.next(DELIVERY_TIMEOUT_MS)).isNotNull();

      for (int i = 1; i <= CHANGES_ON_THEIR_WAY; i++)
      {
        waitForFollowing(handler);
        reopenGap(handler, missed);
        // seen over a tick or two: the gap is throttled, nothing is read again for it
        Thread.sleep(NO_DELIVERY_WATCH_MS);

        final CSN csn = new CSN(TimeThread.getTime(), i, OTHER_PUBLISHER_DS_ID);
        publishThenQueueLate(replicationServer, handler, baseDN, csn);
        final List<CSN> received = new ArrayList<>();
        for (int j = 0; j < 2; j++)
        {
          final UpdateMsg receivedMsg = receiver.next(DELIVERY_TIMEOUT_MS);
          assertThat(receivedMsg).as("delivery " + j + " of round " + i).isNotNull();
          received.add(receivedMsg.getCSN());
        }
        assertThat(received)
            .as("round " + i + ": the queued change is sent from the queue, the gap is read again after")
            .containsExactly(csn, missed);
        final List<String> warnings = warningsFor(handler, csn);
        assertThat(warnings).as("round " + i).isNotEmpty();
        for (String warning : warnings)
        {
          assertThat(stateOfTheReplicaIn(warning))
              .as("round " + i + ": a change on its way was reported missing")
              .contains(csn.toString());
        }
      }
    }
    finally
    {
      stop(broker);
      stop(receiver);
      removeQuietly(replicationServer);
    }
  }

  /**
   * A peer replication server is handed only the changes of the directory servers connected to
   * this one - what a third replication server relayed never reaches its handler, nor its state,
   * so the domain is always ahead of that handler. That is not a miss: in a mesh of three, the
   * handler of one peer must not be sent again what the other peer already sent both.
   */
  @Test
  public void aPeerReplicationServerIsLeftAlone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final List<ReplicationServer> replicationServers = new ArrayList<>();
    ReplicationBroker broker = null;
    try
    {
      final int[] ports = { TestCaseUtils.findFreePort(), TestCaseUtils.findFreePort(), TestCaseUtils.findFreePort() };
      // the replica writes through the third replication server: the two others relay nothing
      // of what it writes to each other. It connects first, so that the domain has a generation
      // id by the time the two others are told about it - a replication server presenting none
      // is not relayed anything
      final ReplicationServer thirdReplicationServer =
          newReplicationServer("missedUpdateMeshDb3", ports[2], THIRD_RS_ID, ports[0], ports[1]);
      replicationServers.add(thirdReplicationServer);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, ports[2], SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      waitForGenerationId(thirdReplicationServer, baseDN);
      replicationServers.add(newReplicationServer("missedUpdateMeshDb1", ports[0], RS_ID, ports[1], ports[2]));
      replicationServers.add(newReplicationServer("missedUpdateMeshDb2", ports[1], PEER_RS_ID, ports[0], ports[2]));
      final ReplicationServerDomain domain = waitForMesh(replicationServers.get(1), baseDN);
      final ReplicationServerHandler peerHandler = domain.getConnectedRSs().get(PEER_RS_ID);
      waitForFollowing(peerHandler);

      final CSN csn = new CSN(TimeThread.getTime(), 1, DS_ID);
      broker.publish(newDeleteMsg(baseDN, "cn=relayed", csn));
      waitForDomainState(domain, csn);

      Thread.sleep(NO_DELIVERY_WATCH_MS);
      assertThat(warningsFor(peerHandler, csn))
          .as("the changelog was read again for a peer replication server").isEmpty();
      assertThat(peerHandler.isFollowing()).isTrue();
    }
    finally
    {
      stop(broker);
      for (ReplicationServer replicationServer : replicationServers)
      {
        removeQuietly(replicationServer);
      }
    }
  }

  /**
   * Writes a change into the changelog the way {@code ReplicationServerDomain.put()} does, minus
   * the copy it hands to the message queue of every connected handler. This is the state the
   * replication server is left in by a change which reached the changelog but not the queue of a
   * handler.
   */
  private void publishToChangelogOnly(ReplicationServer replicationServer, DN baseDN, CSN csn)
      throws Exception
  {
    publishToChangelogOnly(replicationServer, baseDN, newDeleteMsg(baseDN, "cn=missed", csn));
  }

  private void publishToChangelogOnly(ReplicationServer replicationServer, DN baseDN, UpdateMsg msg)
      throws Exception
  {
    replicationServer.getChangelogDB().getReplicationDomainDB().publishUpdateMsg(baseDN, msg);
  }

  /**
   * Writes a change into the changelog and hands it to the queue of the handler once a tick has
   * seen the domain hold it: the change is on its way for exactly one check, and that check
   * compared it with a state of the domain seen at a tick before it was published - the previous
   * tick is waited for first, since a delivery from the queue leaves the handler with no state to
   * compare with until it has waited on an empty queue again.
   */
  private void publishThenQueueLate(
      ReplicationServer replicationServer, MessageHandler handler, DN baseDN, CSN csn) throws Exception
  {
    final UpdateMsg msg = newDeleteMsg(baseDN, "cn=late", csn);
    waitForATickOnAnEmptyQueue(handler);
    publishToChangelogOnly(replicationServer, baseDN, msg);
    waitForATickWhichSaw(handler, csn);
    handler.add(msg);
  }

  /**
   * Takes a change the handler was sent back out of its state, so that the domain is ahead of the
   * handler by that change again while holding nothing it did not hold when the changelog was
   * last read for it.
   */
  private void reopenGap(MessageHandler handler, CSN csn)
  {
    assertThat(handler.getServerState().removeCSN(csn))
        .as("the handler was expected to hold " + csn).isTrue();
  }

  private DeleteMsg newDeleteMsg(DN baseDN, String rdn, CSN csn) throws Exception
  {
    return new DeleteMsg(DN.valueOf(rdn + "," + baseDN), csn, "uuid");
  }

  /**
   * The handler of a replica the domain does not feed keeps its state where it is and reads
   * nothing: a change it read would be dropped by the writer while its state moved past it.
   */
  private void assertLeftAlone(DataServerHandler handler, CSN csn) throws Exception
  {
    final long deadline = System.currentTimeMillis() + NO_DELIVERY_WATCH_MS;
    while (System.currentTimeMillis() < deadline)
    {
      assertThat(handler.getServerState().cover(csn))
          .as("the change was recorded as sent to a replica which is not being sent anything")
          .isFalse();
      Thread.sleep(100);
    }
    assertThat(warningsFor(handler, csn))
        .as("the changelog was read again for a replica the domain does not feed").isEmpty();
  }

  /**
   * The records of {@code WARN_CHANGELOG_READ_AGAIN_FOR_MISSING_CHANGES} in the error log which
   * name the given handler and the given change, without their timestamp. The test harness
   * registers two error log publishers on the same writer, so every record is there twice:
   * compare sizes with each other, and count distinct records to count warnings.
   */
  private static List<String> warningsFor(MessageHandler handler, CSN csn)
  {
    final String msgId = "msgID=" + WARN_CHANGELOG_READ_AGAIN_FOR_MISSING_CHANGES.ordinal() + " ";
    final String handlerName = handler.getMonitorInstanceName();
    final List<String> warnings = new ArrayList<>();
    for (String record : TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
    {
      if (record.contains(msgId) && record.contains(handlerName) && record.contains(csn.toString()))
      {
        warnings.add(record.substring(record.indexOf(" msg=")));
      }
    }
    return warnings;
  }

  /** The state of the replica as the warning names it: what it says comes before the domain. */
  private static String stateOfTheReplicaIn(String warning)
  {
    final int domainState = warning.indexOf(" is behind the state ");
    assertThat(domainState).as("the warning names both states: " + warning).isPositive();
    return warning.substring(0, domainState);
  }

  private DataServerHandler waitForFollowingDirectoryServer(
      final ReplicationServerDomain domain, final int serverId) throws Exception
  {
    return timer().repeatUntilSuccess(new Callable<DataServerHandler>()
    {
      @Override
      public DataServerHandler call() throws Exception
      {
        final DataServerHandler handler = domain.getConnectedDSs().get(serverId);
        assertThat(handler).as("the directory server never connected").isNotNull();
        assertThat(handler.isFollowing())
            .as("the directory server never caught up with the changelog").isTrue();
        return handler;
      }
    });
  }

  private void waitForFollowing(final MessageHandler handler) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertThat(handler.isFollowing()).as("the handler never went back to its queue").isTrue();
        return null;
      }
    });
  }

  /** Waits for a tick of the handler on an empty queue: the next check has a state to compare with. */
  private void waitForATickOnAnEmptyQueue(final MessageHandler handler) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertThat(handler.getDomainStateAtPreviousWait())
            .as("the handler never waited on an empty queue").isNotNull();
        return null;
      }
    });
  }

  /** Waits for a tick of the handler which saw the domain hold the given change. */
  private void waitForATickWhichSaw(final MessageHandler handler, final CSN csn) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        final ServerState seen = handler.getDomainStateAtPreviousWait();
        assertThat(seen != null && seen.cover(csn))
            .as("no wait of the handler saw the domain hold " + csn).isTrue();
        return null;
      }
    });
  }

  private void waitForServerState(final MessageHandler handler, final CSN csn) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertThat(handler.getServerState().cover(csn)).as("the handler never saw " + csn).isTrue();
        return null;
      }
    });
  }

  private void waitForStatus(final DataServerHandler handler, final ServerStatus status) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertThat(handler.getStatus()).isEqualTo(status);
        return null;
      }
    });
  }

  private void waitForDomainState(final ReplicationServerDomain domain, final CSN csn) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertThat(domain.getLatestServerState().cover(csn))
            .as("the change never reached the changelog of RS(" + domain.getLocalRSServerId() + ")").isTrue();
        return null;
      }
    });
  }

  private void waitForGenerationId(final ReplicationServer replicationServer, final DN baseDN) throws Exception
  {
    timer().repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, false);
        assertThat(domain).as("the replica never connected").isNotNull();
        assertThat(domain.getGenerationId()).as("the domain never took a generation id").isPositive();
        return null;
      }
    });
  }

  /** Waits for the domain of the replication server to be connected to the two other ones. */
  private ReplicationServerDomain waitForMesh(final ReplicationServer replicationServer, final DN baseDN)
      throws Exception
  {
    return timer().repeatUntilSuccess(new Callable<ReplicationServerDomain>()
    {
      @Override
      public ReplicationServerDomain call() throws Exception
      {
        final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, false);
        assertThat(domain).as("the domain never reached this replication server").isNotNull();
        assertThat(domain.getConnectedRSs().keySet())
            .as("the replication servers never all connected to each other")
            .containsOnly(PEER_RS_ID, THIRD_RS_ID);
        return domain;
      }
    });
  }

  private TestTimer timer()
  {
    return new TestTimer.Builder()
        .maxSleep(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .sleepTimes(10, TimeUnit.MILLISECONDS)
        .toTimer();
  }

  private ReplicationServer newReplicationServer(String dbDirName, int replicationPort)
      throws Exception
  {
    return newReplicationServer(dbDirName, replicationPort, RS_ID);
  }

  private ReplicationServer newReplicationServer(
      String dbDirName, int replicationPort, int serverId, int... peerPorts) throws Exception
  {
    final SortedSet<String> peers = new TreeSet<>();
    for (int peerPort : peerPorts)
    {
      peers.add("localhost:" + peerPort);
    }
    return new ReplicationServer(new ReplServerFakeConfiguration(
        replicationPort, dbDirName, 0, serverId, 0, WINDOW_SIZE, peers));
  }

  private void removeQuietly(ReplicationServer replicationServer)
  {
    try
    {
      remove(replicationServer);
    }
    catch (Exception ignored)
    {
      // the test has already reported what matters
    }
  }

  private static void stop(Receiver receiver)
  {
    if (receiver != null)
    {
      receiver.stop();
    }
  }

  /**
   * Collects the update messages a broker is sent, on a thread of its own. The thread leaves when
   * the broker is stopped - {@code receive()} returns null from then on, without blocking - or
   * when it is interrupted.
   */
  private static final class Receiver implements Runnable
  {
    private final ReplicationBroker broker;
    private final BlockingQueue<UpdateMsg> received = new LinkedBlockingQueue<>();
    private final Thread thread;

    Receiver(ReplicationBroker broker)
    {
      this.broker = broker;
      this.thread = new Thread(this, "MissedUpdateRecoveryTest receiver for DS(" + broker.getServerId() + ")");
      this.thread.setDaemon(true);
      this.thread.start();
    }

    @Override
    public void run()
    {
      while (!Thread.currentThread().isInterrupted())
      {
        try
        {
          final ReplicationMsg msg = broker.receive();
          if (msg == null)
          {
            return; // the broker was stopped
          }
          if (msg instanceof UpdateMsg)
          {
            received.add((UpdateMsg) msg);
          }
        }
        catch (SocketTimeoutException ignored)
        {
          // nothing was sent to this replica within the socket timeout, keep reading
        }
      }
    }

    /** The next update message the broker was sent, or null when none came within the timeout. */
    UpdateMsg next(long timeoutInMillis) throws InterruptedException
    {
      return received.poll(timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    void stop()
    {
      thread.interrupt();
    }
  }
}
