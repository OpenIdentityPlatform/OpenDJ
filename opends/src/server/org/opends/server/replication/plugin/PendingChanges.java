/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class is used to store the list of local operations currently
 * in progress and not yet committed in the database.
 *
 * It is used to make sure that operations are sent to the Replication
 * Server in the order defined by their CSN.
 * It is also used to update the ServerState at the appropriate time.
 *
 * On object of this class is instantiated for each ReplicationDomain.
 */
class PendingChanges
{
  /**
   * A map used to store the pending changes.
   */
  private SortedMap<CSN, PendingChange> pendingChanges =
    new TreeMap<CSN, PendingChange>();

  /**
   * The {@link CSNGenerator} to use to create new unique CSNs
   * for each operation done on the replication domain.
   */
  private CSNGenerator csnGenerator;

  /**
   * The ReplicationDomain that will be used to send UpdateMsg.
   */
  private ReplicationDomain domain;

  private boolean recoveringOldChanges = false;

  /**
   * Creates a new PendingChanges using the provided CSNGenerator.
   *
   * @param csnGenerator The CSNGenerator to use to create new unique CSNs.
   * @param domain  The ReplicationDomain that will be used to send UpdateMsg.
   */
  PendingChanges(CSNGenerator csnGenerator, ReplicationDomain domain)
  {
    this.csnGenerator = csnGenerator;
    this.domain = domain;
  }

  /**
   * Remove and return an update form the pending changes list.
   *
   * @param csn
   *          The CSN of the update to remove.
   */
  synchronized void remove(CSN csn)
  {
    pendingChanges.remove(csn);
  }

  /**
   * Returns the number of update currently in the list.
   *
   * @return The number of update currently in the list.
   */
  int size()
  {
    return pendingChanges.size();
  }

  /**
   * Mark an update message as committed.
   *
   * @param csn The CSN of the update message that must be set as committed.
   * @param msg          The message associated to the update.
   */
  private synchronized void commit(CSN csn, LDAPUpdateMsg msg)
  {
    final PendingChange curChange = pendingChanges.get(csn);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);
    curChange.setMsg(msg);
  }

  /**
   * Add a new UpdateMsg to the pending list from the provided local
   * operation.
   *
   * @param operation The local operation for which an UpdateMsg must
   *                  be added in the pending list.
   * @return The CSN now associated to the operation.
   */
  synchronized CSN putLocalOperation(PluginOperation operation)
  {
    final CSN csn = csnGenerator.newCSN();
    final PendingChange change = new PendingChange(csn, operation, null);
    pendingChanges.put(csn, change);
    return csn;
  }

  /**
   * Push all committed local changes to the replicationServer service.
   *
   * @return The number of pushed updates.
   */
  synchronized int pushCommittedChanges()
  {
    int numSentUpdates = 0;
    if (pendingChanges.isEmpty())
    {
      return numSentUpdates;
    }

    // peek the oldest CSN
    CSN firstCSN = pendingChanges.firstKey();
    PendingChange firstChange = pendingChanges.get(firstCSN);

    while (firstChange != null && firstChange.isCommitted())
    {
      final PluginOperation op = firstChange.getOp();
      if (op != null && !op.isSynchronizationOperation())
      {
        numSentUpdates++;
        final LDAPUpdateMsg updateMsg = firstChange.getMsg();
        if (!recoveringOldChanges)
        {
          domain.publish(updateMsg);
        }
        else
        {
          // do not push updates until the RS catches up.
          // @see #setRecovering(boolean)
          domain.getServerState().update(updateMsg.getCSN());
        }
      }
      pendingChanges.remove(firstCSN);

      if (pendingChanges.isEmpty())
      {
        firstChange = null;
      }
      else
      {
        // peek the oldest CSN
        firstCSN = pendingChanges.firstKey();
        firstChange = pendingChanges.get(firstCSN);
      }
    }
    return numSentUpdates;
  }

  /**
   * Mark an update message as committed, then
   * push all committed local changes to the replicationServer service
   * in a single atomic operation.
   *
   *
   * @param csn The CSN of the update message that must be set as committed.
   * @param msg          The message associated to the update.
   *
   * @return The number of pushed updates.
   */
  synchronized int commitAndPushCommittedChanges(CSN csn, LDAPUpdateMsg msg)
  {
    commit(csn, msg);
    return pushCommittedChanges();
  }

  /**
   * Set the PendingChangesList structure in a mode where it is waiting for the
   * RS to receive all the previous changes to be sent before starting to
   * process the changes normally. In this mode, The Domain does not publish the
   * changes from the pendingChanges because there are older changes that need
   * to be published before.
   *
   * @param recovering
   *          The recovering status that must be set.
   */
  public void setRecovering(boolean recovering)
  {
    recoveringOldChanges = recovering;
  }

  /**
   * Allows to update the recovery situation by comparing the CSN of the last
   * change that was sent to the ReplicationServer with the CSN of the last
   * operation that was taken out of the PendingChanges list. If the two match
   * then the recovery is completed and normal procedure can restart. Otherwise
   * the RSUpdate thread must continue to look for older changes and no changes
   * can be committed from the pendingChanges list.
   *
   * @param recovered
   *          The CSN of the last change that was published to the
   *          ReplicationServer.
   * @return A boolean indicating if the recovery is completed (false) or must
   *         continue (true).
   */
  synchronized boolean recoveryUntil(CSN recovered)
  {
    final CSN lastLocalChange = domain.getLastLocalChange();
    if (recovered != null && recovered.isNewerThanOrEqualTo(lastLocalChange))
    {
      recoveringOldChanges = false;
    }
    return recoveringOldChanges;
  }
}
