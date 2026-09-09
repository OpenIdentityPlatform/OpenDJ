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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.types.Modification;

/**
 * A ModifyMsg whose ack runs out of memory on the way out of a replay which applied it.
 * <p>
 * The change is committed before the ack of its delivery is published, and the ack is
 * where the JVM runs out of memory: the one throw from there which is not caught, so the
 * replay is unwound with the change it was replaying in the data and owned by nobody -
 * {@code commit()} cleared the owner - and the thread ends on the error. What the give-back
 * on the way out of {@code replay()} has left to give back is the changes this thread
 * parked as waiting for another one, and the restart it asks for them is the one which is
 * run: the road a failed replay takes to ask for its own change again is not on the way.
 * <p>
 * Given a flag, the ack sets it before it throws. Handed the flag the replay reads as "this
 * thread is stopping", that stops the thread while its change is being acknowledged: applied
 * and committed under a running thread, unwound under a stopping one, the way a thread of
 * the pool is stopped when their number is changed while its ack is on its way out. A flag
 * set before the replay would not reach here with the change applied: the replay abandons
 * the change unapplied at the top of its first attempt, still owned by this thread, and the
 * abandon road asks for a restart of its own next to the one the give-back asks for.
 * <p>
 * Nothing on the way in reads what throws here: a message handed to the domain rather than
 * published is not one this server acknowledges to anybody. The twin of the fixture
 * {@code UpdateOperationTest} unwinds a replay thread with.
 */
final class ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied extends ModifyMsg
{
  /** The flag the ack sets before it throws, or {@code null} for none. */
  private final AtomicBoolean setByTheAck;

  ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied(
      CSN csn, DN dn, List<Modification> mods, String entryUUID)
  {
    this(csn, dn, mods, entryUUID, null);
  }

  /**
   * @param setByTheAck the flag the ack sets before it throws: the one the replay reads as
   *          "this thread is stopping", to stop the thread while its applied change is
   *          being acknowledged
   */
  ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied(
      CSN csn, DN dn, List<Modification> mods, String entryUUID, AtomicBoolean setByTheAck)
  {
    super(csn, dn, mods, entryUUID);
    this.setByTheAck = setByTheAck;
  }

  @Override
  public boolean isAssured()
  {
    // Read first thing by processUpdateDone(), which is what publishes the ack.
    if (setByTheAck != null)
    {
      setByTheAck.set(true);
    }
    throw new OutOfMemoryError("the ack of this applied delivery runs out of memory");
  }
}
