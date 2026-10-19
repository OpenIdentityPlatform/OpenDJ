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
import static org.forgerock.opendj.ldap.ModificationType.REPLACE;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.util.CollectionUtils.newArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.Attributes;
import org.opends.server.types.Modification;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

/**
 * A handler which is behind reads the changes for its consumer from the changelog, and goes back
 * to its in-memory queue once the queue holds what the consumer lacks - see issue #1206. The queue
 * is ordered by CSN and drops its oldest changes when it is full, so holding the oldest change the
 * consumer lacks does not mean holding all of them. A change which reaches the queue late with an
 * older CSN - relayed by another replication server, or published again by a recovery - can be
 * the oldest change the consumer lacks while the queue lacks newer changes of another replica: the
 * ones it dropped before that change came, or the ones which reached the domain before the handler
 * did. Served from the queue then, the consumer would be given the changes of that replica which
 * the queue still holds, and its state would move past the ones in between for good.
 * <p>
 * Each test drives a handler which no domain feeds, from a single thread, as the writer of a
 * session does. A change "arrives" the way {@code ReplicationServerDomain.put()} hands it over to
 * a registered handler: written to the changelog, then given to the queue. A change written to the
 * changelog only stands for one which reached the domain before the handler was registered.
 * Replica {@link #A} stands for a directory server connected to the replication server, replica
 * {@link #B} for one whose changes another replication server relays late: the changes of B carry
 * older CSNs than the changes of A which arrive with them.
 */
@SuppressWarnings("javadoc")
public class MessageHandlerCatchUpTest extends ReplicationTestCase
{
  private static final int RS_ID = 120;
  private static final int A = 121;
  private static final int B = 122;
  private static final int C = 123;
  private static final int WINDOW_SIZE = 100;
  /** A queue no test fills. */
  private static final int LARGE_QUEUE_SIZE = 100;
  /** How long the handler is given to come up with a change it has: it reads them from memory. */
  private static final long NEXT_MESSAGE_TIMEOUT_MS = 5000;

  /**
   * The case of the description of #1206, on a handler connected for long: the queue, full, has
   * dropped a change of A the consumer lacks, and a change of B with an older CSN has come in
   * after, into the room the drop left. The change of B is the oldest change the consumer lacks,
   * and it is in the queue - but the change of A is not, and must still be sent.
   */
  @Test
  public void aChangeTheFullQueueDroppedIsSentWhenAnOlderOneCameInAfterIt() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerDroppedDb");
      final DroppingQueue queue = new DroppingQueue(baseDN, TimeThread.getTime() - 60000, A, true);
      final MessageHandler handler = newHandler(replicationServer, baseDN, queue.size, queue.consumerState);

      queue.arriveIn(replicationServer, handler);

      assertThat(csnsOf(nextMessages(handler, queue.changesTheConsumerLacks.size())))
          .as("the consumer was not sent every change it lacks, in order")
          .containsExactlyElementsOf(csnsOf(queue.changesTheConsumerLacks));
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The case of the reconnections of #1206: a new handler, whose queue does not hold the changes
   * of A which reached the domain before it did. A change of B with an older CSN then arrives with
   * a change of A: the change of B is the oldest change the consumer lacks and it is in the queue,
   * but the earlier changes of A are not, and must still be sent.
   */
  @Test
  public void aChangeWhichReachedTheDomainBeforeTheHandlerIsSentWhenAnOlderOneIsQueued() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerBeforeDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg b0 = newDeleteMsg(baseDN, "b0", new CSN(t, 0, B));
      final DeleteMsg b1 = newDeleteMsg(baseDN, "b1", new CSN(t + 15, 1, B));
      final List<UpdateMsg> earlierChangesOfA = changesOfA(baseDN, t, 1, 3);
      final DeleteMsg a4 = newDeleteMsg(baseDN, "a4", new CSN(t + 24, 4, A));
      // the consumer has the changes of B up to b0, and none of A
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, stateOf(b0));

      publishToChangelogOnly(replicationServer, baseDN, b0);
      publishToChangelogOnly(replicationServer, baseDN, earlierChangesOfA);
      arrive(replicationServer, handler, a4);
      arrive(replicationServer, handler, b1);

      final List<UpdateMsg> expected = new ArrayList<>();
      expected.add(b1);
      expected.addAll(earlierChangesOfA);
      expected.add(a4);
      assertThat(csnsOf(nextMessages(handler, expected.size())))
          .as("the consumer was not sent every change it lacks, in order")
          .containsExactlyElementsOf(csnsOf(expected));
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The same as {@link #aChangeWhichReachedTheDomainBeforeTheHandlerIsSentWhenAnOlderOneIsQueued()}
   * for a replica which writes nothing more once the handler is there: the queue is handed changes
   * of B only, never one of A, and the earlier changes of A must still be sent.
   */
  @Test
  public void aChangeOfAReplicaTheQueueIsNeverHandedIsSentWhenAnOlderOneIsQueued() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerQuietDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg b0 = newDeleteMsg(baseDN, "b0", new CSN(t, 0, B));
      final DeleteMsg b1 = newDeleteMsg(baseDN, "b1", new CSN(t + 15, 1, B));
      final List<UpdateMsg> earlierChangesOfA = changesOfA(baseDN, t, 1, 3);
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, stateOf(b0));

      publishToChangelogOnly(replicationServer, baseDN, earlierChangesOfA);
      arrive(replicationServer, handler, b0);
      arrive(replicationServer, handler, b1);

      final List<UpdateMsg> expected = new ArrayList<>();
      expected.add(b1);
      expected.addAll(earlierChangesOfA);
      assertThat(csnsOf(nextMessages(handler, expected.size())))
          .as("the consumer was not sent every change it lacks, in order")
          .containsExactlyElementsOf(csnsOf(expected));
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * A change of B with an older CSN arrives while the handler reads the changelog, too late for the
   * read: the first change read is a change of A, and it is in the queue - but the change of B
   * before it is not one the consumer has, and must still be sent.
   */
  @Test
  public void aChangeWhichArrivedWhileTheChangelogWasReadIsSent() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ChangeAfterTheRead domain = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerWhileReadDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg b0 = newDeleteMsg(baseDN, "b0", new CSN(t, 0, B));
      final DeleteMsg a0 = newDeleteMsg(baseDN, "a0", new CSN(t + 10, 0, A));
      final DeleteMsg b1 = newDeleteMsg(baseDN, "b1", new CSN(t + 15, 1, B));
      final List<UpdateMsg> changesOfA = changesOfA(baseDN, t, 1, 2);
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, stateOf(b0, a0));
      domain = new ChangeAfterTheRead(replicationServer, handler, b1);

      arrive(replicationServer, handler, b0);
      arrive(replicationServer, handler, a0);
      for (UpdateMsg msg : changesOfA)
      {
        arrive(replicationServer, handler, msg);
      }

      final List<UpdateMsg> expected = new ArrayList<>(changesOfA);
      expected.add(b1);
      assertThat(csnsOf(nextMessages(handler, expected.size())))
          .as("the consumer was not sent every change it lacks")
          .containsExactlyElementsOf(csnsOf(expected));
    }
    finally
    {
      deregister(domain);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The same as {@link #aChangeWhichArrivedWhileTheChangelogWasReadIsSent()} for a handler whose
   * consumer has everything the changelog held: the read gives nothing, and the change of B, which
   * arrives after it, is the oldest change of the full queue, which drops it at once. The queue
   * holds nothing the consumer lacks, but the change of B must still be sent.
   */
  @Test
  public void aChangeTheFullQueueDroppedWhileTheChangelogWasReadIsSent() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ChangeAfterTheRead domain = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerDroppedWhileReadDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg b0 = newDeleteMsg(baseDN, "b0", new CSN(t, 0, B));
      final DeleteMsg b1 = newDeleteMsg(baseDN, "b1", new CSN(t + 15, 1, B));
      final List<UpdateMsg> changesOfA = changesOfA(baseDN, t, 1, 5);
      final ServerState consumerState = stateOf(b0, changesOfA.get(changesOfA.size() - 1));
      // full with the changes of A: one more change makes it drop its oldest one
      final MessageHandler handler = newHandler(replicationServer, baseDN, changesOfA.size(), consumerState);
      domain = new ChangeAfterTheRead(replicationServer, handler, b1);

      for (UpdateMsg msg : changesOfA)
      {
        arrive(replicationServer, handler, msg);
      }

      assertThat(csnsOf(nextMessages(handler, 1)))
          .as("the consumer was not sent the change the queue dropped")
          .containsExactly(b1.getCSN());
    }
    finally
    {
      deregister(domain);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The same queue as {@link #aChangeTheFullQueueDroppedIsSentWhenAnOlderOneCameInAfterIt()}, for
   * a consumer which already has the change of A the queue dropped: the queue holds everything the
   * consumer lacks, and the handler serves it from the queue again, as soon as it finds there the
   * oldest change the consumer lacks.
   */
  @Test
  public void aHandlerWhoseQueueHoldsWhatTheConsumerLacksFollowsTheQueueAgain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerFollowsDb");
      final DroppingQueue queue = new DroppingQueue(baseDN, TimeThread.getTime() - 60000, A, true);
      final ServerState consumerState = queue.consumerState.duplicate();
      consumerState.update(queue.dropped.getCSN());
      final MessageHandler handler = newHandler(replicationServer, baseDN, queue.size, consumerState);

      queue.arriveIn(replicationServer, handler);

      final List<UpdateMsg> expected = new ArrayList<>(queue.changesTheConsumerLacks);
      expected.remove(queue.dropped);
      assertServedFromTheQueueAfterTheFirst(handler, expected);
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The queue dropped a change the changelog no longer holds, as after the changelog was cleared
   * for a new generation id, of a replica which writes nothing more: nothing can send it to the
   * consumer, and it must not keep the handler from its queue, which holds everything else the
   * consumer lacks.
   */
  @Test
  public void aDroppedChangeTheChangelogNoLongerHoldsDoesNotHoldTheHandlerBack() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerNoLongerHeldDb");
      final DroppingQueue queue = new DroppingQueue(baseDN, TimeThread.getTime() - 60000, C, false);
      final MessageHandler handler = newHandler(replicationServer, baseDN, queue.size, queue.consumerState);

      queue.arriveIn(replicationServer, handler);

      final List<UpdateMsg> expected = new ArrayList<>(queue.changesTheConsumerLacks);
      expected.remove(queue.dropped);
      assertServedFromTheQueueAfterTheFirst(handler, expected);
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * A replica offline message carries the CSN of no change, and no state of a consumer ever holds
   * it: one in the queue before the first change the consumer lacks must not keep the handler from
   * its queue, even once its replica, back online, has written a change the consumer lacks.
   */
  @Test
  public void aReplicaOfflineMessageDoesNotHoldTheHandlerBack() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerOfflineDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg c0 = newDeleteMsg(baseDN, "c0", new CSN(t, 0, C));
      final DeleteMsg a1 = newDeleteMsg(baseDN, "a1", new CSN(t + 1, 1, A));
      final DeleteMsg a2 = newDeleteMsg(baseDN, "a2", new CSN(t + 3, 2, A));
      final DeleteMsg c3 = newDeleteMsg(baseDN, "c3", new CSN(t + 4, 3, C));
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, stateOf(c0, a1));

      arrive(replicationServer, handler, c0);
      arrive(replicationServer, handler, a1);
      // a replica offline message is not written to the changelog, its CSN goes to the state of
      // the replica instead: ReplicationServerDomain.publishUpdateMsg()
      handler.add(new ReplicaOfflineMsg(new CSN(t + 2, 0, C)));
      arrive(replicationServer, handler, a2);
      arrive(replicationServer, handler, c3);

      final List<UpdateMsg> expected = new ArrayList<>();
      expected.add(a2);
      expected.add(c3);
      assertServedFromTheQueueAfterTheFirst(handler, expected);
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The queue still holds a change the changelog no longer holds, as after the changelog was
   * cleared for a new generation id, and the changelog gives the handler nothing but the replica
   * offline message of a replica which wrote since: no read of the changelog can give the consumer
   * that change, and the handler must not read the changelog again and again for it. It goes back
   * to its queue, leaving out what the queue holds before the offline message, that change too.
   */
  @Test
  public void aQueuedChangeTheChangelogNoLongerHoldsDoesNotHoldTheHandlerBack() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerQueuedNoLongerHeldDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg c0 = newDeleteMsg(baseDN, "c0", new CSN(t, 0, C));
      final DeleteMsg a1 = newDeleteMsg(baseDN, "a1", new CSN(t + 1, 1, A));
      final CSN offlineC = new CSN(t + 2, 0, C);
      final DeleteMsg a2 = newDeleteMsg(baseDN, "a2", new CSN(t + 3, 2, A));
      // the consumer has every change the changelog holds
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, stateOf(c0));

      publishToChangelogOnly(replicationServer, baseDN, c0);
      // the change of a changelog cleared since
      handler.add(a1);
      // every read of the changelog gives it, as no state of a consumer ever holds its CSN
      replicationServer.getChangelogDB().getReplicationDomainDB().notifyReplicaOffline(baseDN, offlineC);

      assertThat(csnsOf(nextMessages(handler, 1)))
          .as("the handler kept reading the changelog for a change no read can give")
          .containsExactly(offlineC);
      assertThat(handler.isFollowing()).isTrue();

      arrive(replicationServer, handler, a2);
      final List<Sent> sent = nextMessages(handler, 1);
      assertThat(csnsOf(sent)).containsExactly(a2.getCSN());
      assertThat(sent.get(0).fromTheChangelog).isFalse();
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The full queue of a handler drops a replica offline message, which the domain hands to the
   * handler of a peer replication server: the consumer has every change of that replica the
   * changelog holds, and no state of a consumer ever holds the CSN of the message, so it must not
   * keep the handler from its queue, which holds every change the consumer lacks.
   */
  @Test
  public void aDroppedReplicaOfflineMessageDoesNotHoldTheHandlerBack() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerDroppedOfflineDb");
      final long t = TimeThread.getTime() - 60000;
      final DeleteMsg b0 = newDeleteMsg(baseDN, "b0", new CSN(t, 0, B));
      final List<UpdateMsg> changesOfA = changesOfA(baseDN, t, 1, 5);
      // full with the changes of A: they make it drop the offline message, its oldest one
      final MessageHandler handler = newHandler(replicationServer, baseDN, changesOfA.size(), stateOf(b0));

      publishToChangelogOnly(replicationServer, baseDN, b0);
      handler.add(new ReplicaOfflineMsg(new CSN(t + 5, 0, B)));
      for (UpdateMsg msg : changesOfA)
      {
        arrive(replicationServer, handler, msg);
      }

      assertServedFromTheQueueAfterTheFirst(handler, changesOfA);
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * A handler which has been handed a change catches up from the changelog until a read gives
   * nothing, and goes back to its queue: the change handed to it after that read is served from
   * the queue.
   */
  @Test
  public void aHandlerWhichCaughtUpFromTheChangelogFollowsTheQueueAgain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ChangeAfterTheRead domain = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerCaughtUpDb");
      final long t = TimeThread.getTime() - 60000;
      final List<UpdateMsg> changesOfA = changesOfA(baseDN, t, 1, 3);
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, new ServerState());
      domain = new ChangeAfterTheRead(replicationServer, handler, changesOfA.get(2), true);

      publishToChangelogOnly(replicationServer, baseDN, changesOfA.get(0));
      arrive(replicationServer, handler, changesOfA.get(1));

      assertServedFromTheQueueAfterAReadGaveNothing(handler, changesOfA);
    }
    finally
    {
      deregister(domain);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The same as {@link #aHandlerWhichCaughtUpFromTheChangelogFollowsTheQueueAgain()} for a handler
   * which is never handed the changes of a replica, as the handler of a peer replication server is
   * never handed the changes of the replicas connected to other replication servers: the domain held
   * a change of B when it handed the queue its first change, so the handler reads it from the
   * changelog, although it finds in its queue the first change it reads, then goes back to its queue.
   */
  @Test
  public void aHandlerWhichReadTheChangesOfAReplicaItIsNeverHandedFollowsTheQueueAgain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ChangeAfterTheRead domain = null;
    try
    {
      replicationServer = newReplicationServer("messageHandlerNeverHandedDb");
      final long t = TimeThread.getTime() - 60000;
      final List<UpdateMsg> changesOfA = changesOfA(baseDN, t, 1, 2);
      final DeleteMsg b1 = newDeleteMsg(baseDN, "b1", new CSN(t + 30, 1, B));
      final MessageHandler handler = newHandler(replicationServer, baseDN, LARGE_QUEUE_SIZE, new ServerState());
      domain = new ChangeAfterTheRead(replicationServer, handler, changesOfA.get(1), true);

      publishToChangelogOnly(replicationServer, baseDN, b1);
      arrive(replicationServer, handler, changesOfA.get(0));

      final List<UpdateMsg> expected = new ArrayList<>();
      expected.add(changesOfA.get(0));
      expected.add(b1);
      expected.add(changesOfA.get(1));
      assertServedFromTheQueueAfterAReadGaveNothing(handler, expected);
    }
    finally
    {
      deregister(domain);
      removeQuietly(replicationServer);
    }
  }

  /**
   * A queue of {@link #size} changes which drops a change the consumer lacks, then takes a change
   * of B with an older CSN in the room the drop left - which only a queue bounded by its size in
   * bytes leaves, as it drops whole changes: one large change, dropped, leaves room for a small one.
   * <p>
   * The queue is not full until it holds 5 changes. Changes {@code b0} and {@code a0}, which the
   * consumer has, show that the handler has been handed changes for long; the large change pushes
   * the queue over its size with the fifth change, so the queue drops {@code b0}, then {@code a0},
   * then the large change. The changes after it are small, so {@code b1} comes into the room the
   * large change left: the queue ends up holding {@code b1} and {@code a2} .. {@code a6}.
   */
  private final class DroppingQueue
  {
    private final List<UpdateMsg> arrivals = new ArrayList<>();
    /** The changes the consumer lacks, in the order it must be sent them. */
    private final List<UpdateMsg> changesTheConsumerLacks = new ArrayList<>();
    private final ServerState consumerState;
    private final UpdateMsg dropped;
    private final boolean droppedInTheChangelog;
    private final int size;

    /**
     * @param droppedReplica
     *          the replica of the change the queue drops
     * @param droppedInTheChangelog
     *          whether the changelog holds the change the queue drops
     */
    private DroppingQueue(DN baseDN, long t, int droppedReplica, boolean droppedInTheChangelog)
        throws Exception
    {
      final DeleteMsg b0 = newDeleteMsg(baseDN, "b0", new CSN(t, 0, B));
      final DeleteMsg a0 = newDeleteMsg(baseDN, "a0", new CSN(t + 10, 0, A));
      final DeleteMsg b1 = newDeleteMsg(baseDN, "b1", new CSN(t + 15, 1, B));
      // a replace of a large value: the size of a delete does not count its DN
      dropped = new ModifyMsg(new CSN(t + 21, 1, droppedReplica), DN.valueOf("cn=large," + baseDN),
          newArrayList(new Modification(REPLACE, Attributes.create("description", "a1".repeat(1000)))),
          "uuid");
      this.droppedInTheChangelog = droppedInTheChangelog;
      final List<UpdateMsg> keptChangesOfA = changesOfA(baseDN, t, 2, 6);

      arrivals.add(b0);
      arrivals.add(a0);
      arrivals.add(dropped);
      arrivals.addAll(keptChangesOfA);
      arrivals.add(b1);
      changesTheConsumerLacks.add(b1);
      changesTheConsumerLacks.add(dropped);
      changesTheConsumerLacks.addAll(keptChangesOfA);
      consumerState = stateOf(b0, a0);

      // room for b1 and a2 .. a6 in bytes, as many changes as the queue ever holds, and not for the
      // large change
      int keptBytes = b1.size();
      for (UpdateMsg msg : keptChangesOfA)
      {
        keptBytes += msg.size();
      }
      size = Math.max(arrivals.size(), (keptBytes + 99) / 100);
      assertThat(dropped.size())
          .as("the large change must push the queue over its size in bytes on its own")
          .isGreaterThan(size * 100);
    }

    private void arriveIn(ReplicationServer replicationServer, MessageHandler handler) throws Exception
    {
      for (UpdateMsg msg : arrivals)
      {
        if (msg != dropped || droppedInTheChangelog)
        {
          arrive(replicationServer, handler, msg);
        }
        else
        {
          handler.add(msg);
        }
      }
    }
  }

  /**
   * The domain of a handler, which hands it a change once it has read the changelog for the first
   * time - or once a read of the changelog gave nothing - before it looks at its queue: the domain
   * writes the changes it receives and hands them over on threads of their own, and nothing keeps
   * one from coming in between. The change comes the way {@code ReplicationServerDomain.put()}
   * hands it over - see {@link #arrive}.
   */
  private final class ChangeAfterTheRead extends ReplicationServerDomain
  {
    private final ReplicationServer replicationServer;
    private final MessageHandler handler;
    private final boolean onceAReadGivesNothing;
    private UpdateMsg change;

    private ChangeAfterTheRead(ReplicationServer replicationServer, MessageHandler handler, UpdateMsg change)
    {
      this(replicationServer, handler, change, false);
    }

    /**
     * @param onceAReadGivesNothing
     *          whether the change comes once a read of the changelog gave nothing, rather than once
     *          the changelog was read for the first time
     */
    private ChangeAfterTheRead(ReplicationServer replicationServer, MessageHandler handler, UpdateMsg change,
        boolean onceAReadGivesNothing)
    {
      super(handler.getBaseDN(), replicationServer);
      this.replicationServer = replicationServer;
      this.handler = handler;
      this.change = change;
      this.onceAReadGivesNothing = onceAReadGivesNothing;
      handler.replicationServerDomain = this;
    }

    @Override
    public String getMonitorInstanceName()
    {
      // apart from the one of the domain of the replication server, which it would replace
      return super.getMonitorInstanceName() + " for " + MessageHandlerCatchUpTest.class.getSimpleName();
    }

    @Override
    public DBCursor<UpdateMsg> getCursorFrom(ServerState startAfterServerState) throws ChangelogException
    {
      final DBCursor<UpdateMsg> read = super.getCursorFrom(startAfterServerState);
      return new DBCursor<UpdateMsg>()
      {
        private boolean gaveSomething;

        @Override
        public UpdateMsg getRecord()
        {
          return read.getRecord();
        }

        @Override
        public boolean next() throws ChangelogException
        {
          final boolean hasNext = read.next();
          gaveSomething |= hasNext;
          return hasNext;
        }

        @Override
        public void close()
        {
          read.close();
          if (change != null && (!onceAReadGivesNothing || !gaveSomething))
          {
            final UpdateMsg msg = change;
            change = null;
            try
            {
              arrive(replicationServer, handler, msg);
            }
            catch (Exception e)
            {
              throw new RuntimeException(e);
            }
          }
        }
      };
    }
  }

  private static void deregister(ReplicationServerDomain domain)
  {
    if (domain != null)
    {
      DirectoryServer.deregisterMonitorProvider(domain);
    }
  }

  /** A message the handler came up with, and whether it read it from the changelog. */
  private static final class Sent
  {
    private final UpdateMsg msg;
    private final boolean fromTheChangelog;

    private Sent(UpdateMsg msg, boolean fromTheChangelog)
    {
      this.msg = msg;
      this.fromTheChangelog = fromTheChangelog;
    }
  }

  /**
   * The handler sends the given changes, in order, and serves them from its queue from the second
   * one on: it finds the first one in its queue and goes back to it.
   */
  private void assertServedFromTheQueueAfterTheFirst(MessageHandler handler, List<UpdateMsg> expected)
      throws Exception
  {
    final List<Sent> sent = nextMessages(handler, expected.size());
    assertThat(csnsOf(sent)).containsExactlyElementsOf(csnsOf(expected));
    assertThat(sent.get(1).fromTheChangelog)
        .as("the handler did not go back to its queue, which holds every change the consumer lacks")
        .isFalse();
    assertThat(handler.isFollowing()).isTrue();
  }

  /**
   * The handler sends the given changes, in order, and serves the last one from its queue: it is
   * handed to the queue once a read of the changelog gave nothing, see {@link ChangeAfterTheRead}.
   */
  private void assertServedFromTheQueueAfterAReadGaveNothing(MessageHandler handler, List<UpdateMsg> expected)
      throws Exception
  {
    final List<Sent> sent = nextMessages(handler, expected.size());
    assertThat(csnsOf(sent)).containsExactlyElementsOf(csnsOf(expected));
    assertThat(sent.get(sent.size() - 1).fromTheChangelog)
        .as("the handler did not go back to its queue once the changelog had nothing more for the consumer")
        .isFalse();
    assertThat(handler.isFollowing()).isTrue();
  }

  /**
   * The next messages the handler comes up with for its consumer, at most {@code count}: fewer
   * when it has none to come up with. It is asked from one thread, as the writer of a session asks.
   */
  private List<Sent> nextMessages(final MessageHandler handler, int count) throws Exception
  {
    final ExecutorService writer = Executors.newSingleThreadExecutor();
    try
    {
      final List<Sent> sent = new ArrayList<>();
      for (int i = 0; i < count; i++)
      {
        final Future<Sent> next = writer.submit(new Callable<Sent>()
        {
          @Override
          public Sent call() throws Exception
          {
            final UpdateMsg msg = handler.getNextMessage();
            return msg != null ? new Sent(msg, handler.isLastMessageFromLateQueue()) : null;
          }
        });
        final Sent nextSent;
        try
        {
          nextSent = next.get(NEXT_MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
          // it waits for a change to reach its queue: it has nothing more for the consumer
          handler.setConsumerActive(false);
          break;
        }
        if (nextSent == null)
        {
          break;
        }
        sent.add(nextSent);
      }
      return sent;
    }
    finally
    {
      writer.shutdownNow();
    }
  }

  private MessageHandler newHandler(ReplicationServer replicationServer, DN baseDN, int queueSize,
      ServerState consumerState) throws Exception
  {
    final MessageHandler handler = new MessageHandler(queueSize, replicationServer);
    handler.setBaseDNAndDomain(baseDN, false);
    handler.setInitialServerState(consumerState);
    return handler;
  }

  /** Hands a change over the way {@code ReplicationServerDomain.put()} does. */
  private void arrive(ReplicationServer replicationServer, MessageHandler handler, UpdateMsg msg)
      throws Exception
  {
    publishToChangelogOnly(replicationServer, handler.getBaseDN(), msg);
    handler.add(msg);
  }

  private void publishToChangelogOnly(ReplicationServer replicationServer, DN baseDN, List<UpdateMsg> msgs)
      throws Exception
  {
    for (UpdateMsg msg : msgs)
    {
      publishToChangelogOnly(replicationServer, baseDN, msg);
    }
  }

  private void publishToChangelogOnly(ReplicationServer replicationServer, DN baseDN, UpdateMsg msg)
      throws Exception
  {
    replicationServer.getChangelogDB().getReplicationDomainDB().publishUpdateMsg(baseDN, msg);
  }

  /** Changes {@code a<first>} .. {@code a<last>} of A, a second apart, the first 20 s after B's first one. */
  private List<UpdateMsg> changesOfA(DN baseDN, long t, int first, int last) throws Exception
  {
    final List<UpdateMsg> changes = new ArrayList<>();
    for (int i = first; i <= last; i++)
    {
      changes.add(newDeleteMsg(baseDN, "a" + i, new CSN(t + 20 + i, i, A)));
    }
    return changes;
  }

  private static ServerState stateOf(UpdateMsg... msgs)
  {
    final ServerState state = new ServerState();
    for (UpdateMsg msg : msgs)
    {
      state.update(msg.getCSN());
    }
    return state;
  }

  private static List<CSN> csnsOf(List<?> msgs)
  {
    final List<CSN> csns = new ArrayList<>();
    for (Object msg : msgs)
    {
      csns.add(msg instanceof Sent ? ((Sent) msg).msg.getCSN() : ((UpdateMsg) msg).getCSN());
    }
    return csns;
  }

  private DeleteMsg newDeleteMsg(DN baseDN, String rdnValue, CSN csn) throws Exception
  {
    return new DeleteMsg(DN.valueOf("cn=" + rdnValue + "," + baseDN), csn, "uuid");
  }

  private ReplicationServer newReplicationServer(String dbDirName) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        TestCaseUtils.findFreePort(), dbDirName, 0, RS_ID, 0, WINDOW_SIZE, new TreeSet<String>()));
  }

  private void removeQuietly(ReplicationServer replicationServer)
  {
    try
    {
      if (replicationServer != null)
      {
        remove(replicationServer);
      }
    }
    catch (Exception ignored)
    {
      // the test has already reported what matters
    }
  }
}
