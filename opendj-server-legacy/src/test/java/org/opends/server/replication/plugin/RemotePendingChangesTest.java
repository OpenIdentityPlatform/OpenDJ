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

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
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

  private DeleteMsg deleteMsg(CSN csn, String entryUUID) throws Exception
  {
    return new DeleteMsg(DN.valueOf("cn=" + entryUUID + ",dc=example,dc=com"), csn, entryUUID);
  }
}
