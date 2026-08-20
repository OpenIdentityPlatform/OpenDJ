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
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
import org.testng.annotations.Test;

/**
 * Tests the bookkeeping a replica does on the changes it received from a replication
 * server: a change reaches the ServerState only once it really has been replayed.
 */
@SuppressWarnings("javadoc")
public class RemotePendingChangesTest extends ReplicationTestCase
{
  private static final int SERVER_ID = 42;

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
   * Restarting the session forgets the pending changes without recording them, so that
   * the ones the replication server sends again are replayed rather than discarded as
   * duplicates.
   */
  @Test
  public void clearForgetsThePendingChangesWithoutRecordingThem() throws Exception
  {
    final ServerState state = new ServerState();
    final RemotePendingChanges pendingChanges = new RemotePendingChanges(state);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();

    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")));
    assertFalse(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")), "a duplicate must be discarded");

    pendingChanges.clear();

    assertEquals(pendingChanges.getQueueSize(), 0);
    assertTrue(state.isEmpty(), "clearing the pending changes must not record them as replayed");
    assertTrue(pendingChanges.putRemoteUpdate(deleteMsg(csn, "uuid-1")),
        "the change must be accepted again once the session has been restarted");
  }

  private DeleteMsg deleteMsg(CSN csn, String entryUUID) throws Exception
  {
    return new DeleteMsg(DN.valueOf("cn=" + entryUUID + ",dc=example,dc=com"), csn, entryUUID);
  }
}
