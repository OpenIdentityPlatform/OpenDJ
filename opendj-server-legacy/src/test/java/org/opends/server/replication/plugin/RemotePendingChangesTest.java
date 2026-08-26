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

  private DeleteMsg deleteMsg(CSN csn, String entryUUID) throws Exception
  {
    return new DeleteMsg(DN.valueOf("cn=" + entryUUID + ",dc=example,dc=com"), csn, entryUUID);
  }
}
