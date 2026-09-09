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
package org.opends.server.replication.plugin;

import static org.testng.Assert.*;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests the bookkeeping a replica does on the changes it received from a replication
 * server: a change reaches the ServerState only once it really has been replayed.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "replication" }, sequential = true)
public class RemotePendingChangesTest extends DirectoryServerTestCase
{
  private static final int SERVER_ID = 42;

  @BeforeClass
  public void startServer() throws Exception
  {
    // The messages these tests are built from carry DNs, which need the schema.
    TestCaseUtils.startServer();
  }

  @Test
  public void committedChangeIsPushedToTheServerState() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")));
    assertEquals(pendingChanges.getQueueSize(), 1);

    pendingChanges.commit(csn);

    assertTrue(state.cover(csn));
    assertEquals(pendingChanges.getQueueSize(), 0);
  }

  /**
   * A change which is not committed must hold back the ServerState, even when the
   * changes which follow it have been replayed: the replication server resumes from the
   * ServerState, so anything it covers is never sent again.
   */
  @Test
  public void uncommittedChangeHoldsBackTheChangesWhichFollowIt() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN failed = generator.newCSN();
    final CSN next = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(failed, "uuid-1")));
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(next, "uuid-2")));

    // The replay of the first change failed, the second one went through.
    pendingChanges.commit(next);

    assertFalse(state.cover(failed), "a change which was not replayed must not be covered");
    assertFalse(state.cover(next), "the changes which follow a failed one must not be covered either");
    assertEquals(pendingChanges.getQueueSize(), 2);

    // The first change finally made it: both are now recorded as replayed.
    pendingChanges.commit(failed);

    assertTrue(state.cover(failed));
    assertTrue(state.cover(next));
    assertEquals(pendingChanges.getQueueSize(), 0);
  }

  /**
   * A change whose replay failed stays listed and uncommitted - it is the barrier which
   * holds the ServerState back - but no replay thread owns it anymore, so the delivery
   * the replication server makes over the restarted session takes over from the one
   * which failed.
   */
  @Test
  public void replayFailedLetsTheNextDeliveryTakeOverTheChange() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg failedDelivery = deleteMsg(csn, "uuid-1");
    final DeleteMsg nextDelivery = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(failedDelivery));
    assertTrue(pendingChanges.markInProgress(failedDelivery));
    assertFalse(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")),
        "a change a replay thread owns must not be taken over (OPENDJ-1115)");

    pendingChanges.replayFailed(csn);

    assertEquals(pendingChanges.getQueueSize(), 1, "the change must stay listed as pending");
    assertTrue(state.isEmpty(), "a change which was not replayed must not be recorded as replayed");
    assertEquals(pendingChanges.changesInProgressSize(), 1,
        "a change which is not in the data yet must stay a dependency of the changes which follow it");

    assertTrue(pendingChanges.putRemoteUpdate(nextDelivery), "the next delivery must be replayed");
    assertFalse(pendingChanges.markInProgress(failedDelivery),
        "the delivery whose replay failed must not be replayed again");
    assertTrue(pendingChanges.markInProgress(nextDelivery));

    pendingChanges.commit(csn);

    assertTrue(state.cover(csn));
    assertEquals(pendingChanges.getQueueSize(), 0);
  }

  /**
   * The change whose replay failed must keep holding back the changes which follow it,
   * including the ones which are replayed while it is being asked for again: the
   * ServerState is a watermark, so recording any of them would record the failed change
   * with them (issue #889).
   */
  @Test
  public void replayFailedKeepsTheChangeAsABarrier() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN failed = generator.newCSN();
    final CSN inProgress = generator.newCSN();
    final DeleteMsg failedMsg = deleteMsg(failed, "uuid-1");
    final DeleteMsg inProgressMsg = deleteMsg(inProgress, "uuid-2");

    assertTrue(pendingChanges.putRemoteUpdate(failedMsg));
    assertTrue(pendingChanges.putRemoteUpdate(inProgressMsg));
    assertTrue(pendingChanges.markInProgress(failedMsg));
    assertTrue(pendingChanges.markInProgress(inProgressMsg));

    // The replay of the first change failed while the second one is still being applied.
    pendingChanges.replayFailed(failed);

    // The change which was being applied when the session was restarted commits as usual.
    pendingChanges.commit(inProgress);

    assertFalse(state.cover(failed), "the change which failed must not be recorded as replayed");
    assertFalse(state.cover(inProgress), "the failed change must hold back the ones which follow it");
    assertEquals(pendingChanges.getQueueSize(), 2);

    // The change which failed is sent again and made it this time.
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(failed, "uuid-1")));
    pendingChanges.commit(failed);

    assertTrue(state.cover(failed));
    assertTrue(state.cover(inProgress));
    assertEquals(pendingChanges.getQueueSize(), 0);
  }

  /**
   * The replication server sends the change again over the new session while the message
   * of the previous delivery may still be waiting in the replay queue: only the delivery
   * which is listed as pending is replayed, or the same change would be applied twice.
   */
  @Test
  public void markInProgressRejectsThePreviousDeliveryOfAChange() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg previousDelivery = deleteMsg(csn, "uuid-1");
    final DeleteMsg newDelivery = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(previousDelivery));
    pendingChanges.replayFailed(csn);
    assertTrue(pendingChanges.putRemoteUpdate(newDelivery), "the next delivery must be replayed");

    assertFalse(pendingChanges.markInProgress(previousDelivery),
        "the message of the previous delivery must not be replayed");
    assertTrue(pendingChanges.markInProgress(newDelivery));
  }

  /**
   * A message which was waiting in the replay queue while the domain was disabled must be
   * reported as not pending anymore rather than replayed against a bookkeeping which does
   * not list its change.
   */
  @Test
  public void markInProgressReportsAChangeWhichIsNotPendingAnymore() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg msg = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(msg));
    assertTrue(pendingChanges.markInProgress(msg));
    assertEquals(pendingChanges.changesInProgressSize(), 1);

    pendingChanges.clear();

    assertEquals(pendingChanges.changesInProgressSize(), 0);
    assertFalse(pendingChanges.markInProgress(msg),
        "a message whose change was forgotten must not be replayed");
  }

  /**
   * A disabled domain saves its ServerState and loads it again when it is enabled back,
   * so the changes listed as pending must not outlive it: one which stayed would be
   * discarded as a duplicate and nothing would ever replay it.
   */
  @Test
  public void clearForgetsEveryChange() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN failed = generator.newCSN();
    final CSN replayed = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(failed, "uuid-1")));
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(replayed, "uuid-2")));
    pendingChanges.commit(replayed);

    pendingChanges.clear();

    assertEquals(pendingChanges.getQueueSize(), 0);
    assertEquals(pendingChanges.getDependentChangesSize(), 0);
    assertTrue(state.isEmpty(), "forgetting the pending changes must not record them as replayed");
    assertNull(pendingChanges.getNextUpdate());
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(failed, "uuid-1")),
        "the changes must be accepted again once the domain is enabled back");
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(replayed, "uuid-2")));
  }

  /**
   * A backend which is failing fails every change in flight: the failures of one change
   * must not reset the ones of another, or the replica would never give up on any of
   * them.
   */
  @Test
  public void replayFailuresAreCountedPerChange() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN first = generator.newCSN();
    final CSN second = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(first, "uuid-1")));
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(second, "uuid-2")));

    assertEquals(pendingChanges.recordReplayFailure(first, 1000).getAttempts(), 1);
    assertEquals(pendingChanges.recordReplayFailure(second, 1100).getAttempts(), 1);
    assertEquals(pendingChanges.recordReplayFailure(first, 1200).getAttempts(), 2);
    assertEquals(pendingChanges.recordReplayFailure(second, 1300).getAttempts(), 2);

    assertEquals(pendingChanges.recordReplayFailure(first, 1400).getFailingForMs(), 400);
    assertEquals(pendingChanges.recordReplayFailure(second, 1500).getFailingForMs(), 400);
  }

  /**
   * The failures belong to the change, which stays listed until it is applied, so they
   * are kept across the deliveries which take over from one another: they are the budget
   * this replica gives a change before it gives up on it, and a delivery which resets it
   * is a replica which never gives up (issue #889).
   */
  @Test
  public void replayFailuresSurviveTheDeliveryTakingOver() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg failedDelivery = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(failedDelivery));
    assertTrue(pendingChanges.markInProgress(failedDelivery));
    assertEquals(pendingChanges.recordReplayFailure(csn, 1000).getAttempts(), 1);
    pendingChanges.replayFailed(csn);

    // The replication server delivers the change again over the restarted session.
    final DeleteMsg nextDelivery = deleteMsg(csn, "uuid-1");
    assertTrue(pendingChanges.putRemoteUpdate(nextDelivery));
    assertTrue(pendingChanges.markInProgress(nextDelivery));

    final RemotePendingChanges.ReplayFailure failure = pendingChanges.recordReplayFailure(csn, 301000);
    assertEquals(failure.getAttempts(), 2);
    assertEquals(failure.getFailingForMs(), 300000,
        "the budget of a change must be measured from its first failure, whichever delivery failed");
  }

  /**
   * However long a single delivery takes to fail - the replay is attempted in place
   * several times and each attempt waits on the storage - the failures belong to the same
   * run: a change which stops failing is applied or given up on, and it takes its
   * failures with it.
   */
  @Test
  public void replayFailuresFarApartStillBelongToTheSameRun() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")));
    pendingChanges.recordReplayFailure(csn, 1000);

    final RemotePendingChanges.ReplayFailure failure = pendingChanges.recordReplayFailure(csn, 601000);
    assertEquals(failure.getAttempts(), 2);
    assertEquals(failure.getFailingForMs(), 600000,
        "a change must be given up on however long its deliveries take to fail");
  }

  /**
   * A change which was replayed, or which the domain forgot on its way down, has no
   * failures left to record: there is nothing left here to give up on.
   */
  @Test
  public void replayFailuresGoAwayWithTheChange() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN committed = generator.newCSN();
    final CSN forgotten = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(committed, "uuid-1")));
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(forgotten, "uuid-2")));
    assertEquals(pendingChanges.recordReplayFailure(committed, 1000).getAttempts(), 1);
    assertEquals(pendingChanges.recordReplayFailure(forgotten, 1000).getAttempts(), 1);

    pendingChanges.commit(committed);
    assertNull(pendingChanges.recordReplayFailure(committed, 1100),
        "a change which was replayed must not be given up on");

    pendingChanges.clear();
    assertNull(pendingChanges.recordReplayFailure(forgotten, 1100),
        "a change a disabled domain forgot must not be given up on");

    // The replication server sends it again once the domain is enabled back.
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(forgotten, "uuid-2")));
    final RemotePendingChanges.ReplayFailure failure =
        pendingChanges.recordReplayFailure(forgotten, 301100);
    assertEquals(failure.getAttempts(), 1);
    assertEquals(failure.getFailingForMs(), 0,
        "a change which was forgotten must not be given up on straight away");
  }

  /**
   * An outage fails every change in flight, and there are more of those than any bound a
   * side map of failures could carry: a change must keep the budget it has been failing
   * for however many other changes are failing with it.
   */
  @Test
  public void aFailingChangeKeepsItsBudgetHoweverManyOtherChangesAreFailing() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN oldest = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(oldest, "uuid-0")));
    assertEquals(pendingChanges.recordReplayFailure(oldest, 1000).getAttempts(), 1);

    // Every other change in flight fails in between, in the order they were delivered.
    final int changesInFlight = 1500;
    final CSN[] others = new CSN[changesInFlight];
    for (int i = 0; i < changesInFlight; i++)
    {
      others[i] = generator.newCSN();
      assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(others[i], "uuid-" + (i + 1))));
      pendingChanges.recordReplayFailure(others[i], 1000 + i);
    }

    final RemotePendingChanges.ReplayFailure failure = pendingChanges.recordReplayFailure(oldest, 301000);
    assertEquals(failure.getAttempts(), 2);
    assertEquals(failure.getFailingForMs(), 300000,
        "the change which has been failing the longest must be the one given up on");
    assertEquals(pendingChanges.recordReplayFailure(others[0], 301000).getAttempts(), 2,
        "the failures of a change must not be dropped to make room for another change");
  }

  /**
   * The changes which are replayed around a change which keeps failing must not report
   * that nothing is failing anymore: a change which can never be applied here fails
   * alone, among changes which replay perfectly well, and the session restart backoff
   * reads this to tell that apart from a backend which is serving again (issue #889).
   */
  @Test
  public void aChangeKeepsFailingWhileTheChangesAroundItAreReplayed() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN failing = generator.newCSN();
    final CSN replayed = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(failing, "uuid-1")));
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(replayed, "uuid-2")));
    assertFalse(pendingChanges.hasFailingChanges(),
        "no change has failed yet");

    pendingChanges.recordReplayFailure(failing, 1000);
    assertTrue(pendingChanges.hasFailingChanges(),
        "the change whose replay failed must be reported as failing");

    // The change which follows it is applied while the older one is still failing. It
    // stays listed, since the ServerState can not move past the change which failed.
    pendingChanges.commit(replayed);
    assertTrue(pendingChanges.hasFailingChanges(),
        "a change which was replayed must not report that the one which is failing stopped");

    // Only the failing change being applied says that this backend is serving again.
    // Both changes leave the list here: the failing one, which is the head, and the one
    // which was waiting behind it for the ServerState to be allowed past.
    pendingChanges.commit(failing);
    assertFalse(pendingChanges.hasFailingChanges(),
        "the change which was failing was applied, so nothing is failing anymore");

    /*
     * The count must be back to none, not below it: a change which is drained without
     * ever having failed must not be counted out. A counter which went negative reads as
     * "nothing is failing" for as long as it takes the next failures to bring it back to
     * zero, which is what has the session restarts start over from their shortest wait
     * while a change is failing - the very loop this counter is here to stop.
     */
    final CSN failingAgain = generator.newCSN();
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(failingAgain, "uuid-3")));
    pendingChanges.recordReplayFailure(failingAgain, 2000);
    assertTrue(pendingChanges.hasFailingChanges(),
        "the change which is failing now must be reported, whatever was drained before it");
  }

  /**
   * The failures a repeatedly failing change accumulates are counted once, and they go
   * away with the change however it leaves - applied, or forgotten by a disabled domain.
   */
  @Test
  public void failingChangesAreCountedOnceAndForgottenWithTheChange() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN csn = generator.newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")));
    pendingChanges.recordReplayFailure(csn, 1000);
    pendingChanges.recordReplayFailure(csn, 2000);
    assertTrue(pendingChanges.hasFailingChanges());

    // Failing twice must not have this change counted twice, or it would still be
    // reported as failing once it is gone.
    pendingChanges.commit(csn);
    assertFalse(pendingChanges.hasFailingChanges(),
        "a change which failed several times must stop being reported as failing once");

    final CSN forgotten = generator.newCSN();
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(forgotten, "uuid-2")));
    pendingChanges.recordReplayFailure(forgotten, 3000);
    assertTrue(pendingChanges.hasFailingChanges());

    pendingChanges.clear();
    assertFalse(pendingChanges.hasFailingChanges(),
        "a disabled domain forgot the change, so nothing is failing here anymore");
  }

  /**
   * A change is given back by the replay thread which owns it and by nobody else.
   * <p>
   * The release is what lets the next delivery of a change be replayed, so a release
   * which arrives from a thread which does not own the change hands a change which is
   * being replayed right now to a second thread - the double replay the ownership is
   * there to prevent (OPENDJ-1115). It happens when a thread reports a failure on a
   * change which has been taken over since, which is every road out of a replay that is
   * not the one which failed: an Error unwinding the replay thread, or an exception on
   * the way to the ack (issue #922).
   */
  @Test
  public void replayFailedIsIgnoredForAThreadWhichDoesNotOwnTheChange() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg delivery = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(delivery));
    assertTrue(pendingChanges.markInProgress(delivery));

    runAndJoin(new Runnable()
    {
      @Override
      public void run()
      {
        pendingChanges.replayFailed(csn);
      }
    });

    assertFalse(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")),
        "a change another thread is replaying must not be taken over");
    assertEquals(pendingChanges.getQueueSize(), 1);

    // The thread which owns the change is still the one which decides its fate.
    pendingChanges.replayFailed(csn);
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")),
        "the change its owner gave back must be taken over by the next delivery");
  }

  /**
   * A change which was parked because it depends on another one is handed to whichever
   * replay thread clears the change it was waiting for, rather than replayed by the
   * thread which parked it. The thread it is handed to is the one which owns it from
   * then on: the failure of the replay it is about to be given is reported by that
   * thread, and a give-back which comes from a thread the change was never handed to is
   * ignored (issue #922).
   */
  @Test
  public void aChangeTakenAsADependencyIsOwnedByTheThreadWhichTakesIt() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN deleted = generator.newCSN();
    final CSN renamed = generator.newCSN();

    final DeleteMsg delete = deleteMsg(deleted, "uuid-1");
    assertTrue(pendingChanges.putRemoteUpdate(delete));
    assertTrue(pendingChanges.markInProgress(delete));

    // A rename into the DN that delete is on: it can only be replayed once the delete has been.
    final ModifyDNMsg rename = renameIntoDeletedEntry(renamed);
    assertTrue(pendingChanges.putRemoteUpdate(rename));
    assertTrue(pendingChanges.markInProgress(rename));
    assertTrue(pendingChanges.checkDependencies(rename),
        "the rename must wait for the delete of the entry it renames into");

    // The delete has been replayed, so the rename is handed to the thread which replayed it.
    pendingChanges.commit(deleted);

    final AtomicReference<LDAPUpdateMsg> taken = new AtomicReference<>();
    runAndJoin(new Runnable()
    {
      @Override
      public void run()
      {
        taken.set(pendingChanges.getNextUpdate());
        // ... and the replay it was taken for failed.
        pendingChanges.replayFailed(renamed);
      }
    });

    assertSame(taken.get(), rename, "the change which was waiting must be handed out");
    assertTrue(pendingChanges.putRemoteUpdate(renameIntoDeletedEntry(renamed)),
        "the change the thread which took it gave back must be taken over by the next delivery");
  }

  /**
   * A thread which reports on a change it gave back a moment ago must not record it as
   * replayed: the delivery which took the change over is being applied right now, so the
   * ServerState would move past a change which is not in the data yet (issue #889), and
   * the thread which is applying it would find nothing left to commit.
   * <p>
   * The give-back on the way out of an unwound replay is what makes this reachable: it
   * runs wherever the replay was left, so the decision a thread carries and the record it
   * makes of it can be a delivery apart (issue #922).
   */
  @Test
  public void commitIsRefusedForAThreadWhichDoesNotOwnTheChange() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg delivery = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(delivery));
    assertTrue(pendingChanges.markInProgress(delivery));

    // The thread which gave up on an earlier delivery of this change records it as
    // replayed while this delivery is being applied.
    runAndJoin(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          pendingChanges.commit(csn);
          fail("a change another replay thread owns must not be recorded as replayed");
        }
        catch (NoSuchElementException expected)
        {
          // There is no change here for that thread to record, which is what its caller
          // reports as ERR_OPERATION_NOT_FOUND_IN_PENDING.
        }
      }
    });

    assertTrue(state.isEmpty(),
        "a change which is being applied must not be recorded in the ServerState");
    assertEquals(pendingChanges.getQueueSize(), 1, "the change must stay listed as pending");

    // The thread which owns the change records it once it really has been applied.
    pendingChanges.commit(csn);

    assertTrue(state.cover(csn));
    assertEquals(pendingChanges.getQueueSize(), 0);
  }

  /**
   * The same holds for the failures which decide when this replica gives up on a change:
   * a thread which does not own the change reports none, so the give-up budget of the
   * delivery which took it over is left alone rather than spent by the one before it
   * (issue #922).
   */
  @Test
  public void recordReplayFailureIsIgnoredForAThreadWhichDoesNotOwnTheChange() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();
    final DeleteMsg delivery = deleteMsg(csn, "uuid-1");

    assertTrue(pendingChanges.putRemoteUpdate(delivery));
    assertTrue(pendingChanges.markInProgress(delivery));

    runAndJoin(new Runnable()
    {
      @Override
      public void run()
      {
        assertNull(pendingChanges.recordReplayFailure(csn, 1000),
            "a change another replay thread owns is not this one's to give up on");
      }
    });

    assertEquals(pendingChanges.recordReplayFailure(csn, 2000).getAttempts(), 1,
        "only the failures of the delivery which owns the change must be counted");
  }

  /**
   * The change a thread parked as waiting for another one is not the change it is
   * replaying: it is handed to whichever thread clears what it waits for, so a give-back on
   * the way out of an unwound replay must leave it alone. Releasing it without taking it out
   * of the changes which are waiting would have the same change handed to two threads
   * (issue #922).
   */
  @Test
  public void theChangesParkedAsDependenciesAreNotOwnedByTheThreadWhichParkedThem()
      throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN deleted = generator.newCSN();
    final CSN renamed = generator.newCSN();

    final DeleteMsg delete = deleteMsg(deleted, "uuid-1");
    assertTrue(pendingChanges.putRemoteUpdate(delete));
    assertTrue(pendingChanges.markInProgress(delete));

    // A rename into the DN that delete is on, parked by this very thread.
    final ModifyDNMsg rename = renameIntoDeletedEntry(renamed);
    assertTrue(pendingChanges.putRemoteUpdate(rename));
    assertTrue(pendingChanges.markInProgress(rename));
    assertTrue(pendingChanges.checkDependencies(rename),
        "the rename must wait for the delete of the entry it renames into");

    assertEquals(pendingChanges.getChangeOwnedByCurrentThread(), deleted,
        "the change this thread is replaying is the one it must give back, not the one it"
            + " parked as waiting for it");
  }

  /**
   * A change which is in the data is not one to give back either, and it stays listed for as
   * long as an older change holds the ServerState back: a give-back on the way out of an
   * unwound replay must not ask for a change this replica has already applied (issue #922).
   * <p>
   * What this pins is that behaviour rather than one of the two conditions which hold it:
   * {@code commit()} marks the change and clears its owner in the same write-locked step, so
   * a committed change is never an owned one and the {@code isCommitted} arm of
   * {@code getChangeOwnedByCurrentThread()} can not be told from the owner check by any test.
   */
  @Test
  public void aChangeWhichWasAppliedIsNotOwnedAnymore() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN held = generator.newCSN();
    final CSN applied = generator.newCSN();

    // An older change nobody has replayed holds the ServerState back, so the change this
    // thread applies stays listed here once it has been committed.
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(held, "uuid-1")));
    final DeleteMsg delivery = deleteMsg(applied, "uuid-2");
    assertTrue(pendingChanges.putRemoteUpdate(delivery));
    assertTrue(pendingChanges.markInProgress(delivery));

    assertEquals(pendingChanges.getChangeOwnedByCurrentThread(), applied,
        "the change this thread is replaying must be the one it owns");

    pendingChanges.commit(applied);

    assertEquals(pendingChanges.getQueueSize(), 2,
        "the change which was applied is held back by the one before it");
    assertNull(pendingChanges.getChangeOwnedByCurrentThread(),
        "a change which is in the data must not be given back on the way out of a replay");
  }

  /**
   * A change which was waiting is handed out once: the thread it is handed to owns it from
   * then on, and the threads which ask next are told there is nothing to take. Two threads
   * handed the same change would replay it twice, which is what the ownership is there to
   * prevent (OPENDJ-1115).
   */
  @Test
  public void aChangeWhichWasWaitingIsHandedOutOnce() throws Exception
  {
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(new ServerState());
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN deleted = generator.newCSN();
    final CSN renamed = generator.newCSN();

    final DeleteMsg delete = deleteMsg(deleted, "uuid-1");
    assertTrue(pendingChanges.putRemoteUpdate(delete));
    assertTrue(pendingChanges.markInProgress(delete));

    final ModifyDNMsg rename = renameIntoDeletedEntry(renamed);
    assertTrue(pendingChanges.putRemoteUpdate(rename));
    assertTrue(pendingChanges.markInProgress(rename));
    assertTrue(pendingChanges.checkDependencies(rename),
        "the rename must wait for the delete of the entry it renames into");

    assertNull(pendingChanges.getNextUpdate(),
        "a change whose dependency still stands must not be handed out");

    pendingChanges.commit(deleted);

    assertSame(pendingChanges.getNextUpdate(), rename,
        "the change which was waiting must be handed to the thread which cleared it");
    assertNull(pendingChanges.getNextUpdate(),
        "a change which has been handed out must not be handed out again");
  }

  /** A rename of an entry into the DN {@code deleteMsg(csn, "uuid-1")} deletes. */
  private ModifyDNMsg renameIntoDeletedEntry(CSN csn) throws Exception
  {
    return new ModifyDNMsg(DN.valueOf("cn=uuid-2,dc=example,dc=com"), csn, "uuid-2",
        null, false, null, "cn=uuid-1");
  }

  /**
   * Runs the provided work on a thread of its own, waits for it and reports what it
   * threw.
   * <p>
   * What the work throws has to be carried back here: an assertion which fails on another
   * thread is lost to a bare {@link Thread#join()}, and every assertion these tests make
   * about what a thread which does not own a change is answered is made on that thread.
   */
  private static void runAndJoin(Runnable runnable) throws Exception
  {
    final FutureTask<Void> task = new FutureTask<>(runnable, null);
    final Thread thread = new Thread(task, "another replay thread");
    thread.start();
    thread.join();
    try
    {
      task.get();
    }
    catch (ExecutionException e)
    {
      // Report what the work threw rather than the wrapper this task put around it: an
      // assertion which failed is an Error, and it is the failure worth reading.
      final Throwable cause = e.getCause();
      if (cause instanceof Error)
      {
        throw (Error) cause;
      }
      if (cause instanceof Exception)
      {
        throw (Exception) cause;
      }
      throw e;
    }
  }

  private DeleteMsg deleteMsg(CSN csn, String entryUUID) throws Exception
  {
    return new DeleteMsg(DN.valueOf("cn=" + entryUUID + ",dc=example,dc=com"), csn, entryUUID);
  }
}
