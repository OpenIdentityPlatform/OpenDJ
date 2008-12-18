/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;


import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class is used to store the list of local operations currently
 * in progress and not yet committed in the database.
 *
 * It is used to make sure that operations are sent to the Replication
 * Server in the order defined by their ChangeNumber.
 * It is also used to update the ServerState at the appropriate time.
 *
 * On object of this class is instantiated for each ReplicationDomain.
 */
public class PendingChanges
{
  /**
   * A map used to store the pending changes.
   */
  private SortedMap<ChangeNumber, PendingChange> pendingChanges =
    new TreeMap<ChangeNumber, PendingChange>();

  /**
   * The ChangeNumberGenerator to use to create new unique ChangeNumbers
   * for each operation done on the replication domain.
   */
  private ChangeNumberGenerator changeNumberGenerator;

  /**
   * The ReplicationDomain that will be used to send UpdateMsg.
   */
  private ReplicationDomain domain;

  /**
   * Creates a new PendingChanges using the provided ChangeNumberGenerator.
   *
   * @param changeNumberGenerator The ChangeNumberGenerator to use to create
   *                               new unique ChangeNumbers.
   * @param domain  The ReplicationDomain that will be used to send
   *                UpdateMsg.
   */
  public PendingChanges(
      ChangeNumberGenerator changeNumberGenerator, ReplicationDomain domain)
  {
    this.changeNumberGenerator = changeNumberGenerator;
    this.domain = domain;
  }

  /**
   * Remove and return an update form the pending changes list.
   *
   * @param changeNumber The ChangeNumber of the update to remove.
   *
   * @return The UpdateMsg that was just removed.
   */
  public synchronized LDAPUpdateMsg remove(ChangeNumber changeNumber)
  {
    return pendingChanges.remove(changeNumber).getMsg();
  }

  /**
   * Returns the number of update currently in the list.
   *
   * @return The number of update currently in the list.
   */
  public int size()
  {
    return pendingChanges.size();
  }

  /**
   * Mark an update message as committed.
   *
   * @param changeNumber The ChangeNumber of the update message that must be
   *                     set as committed.
   * @param msg          The message associated to the update.
   */
  public synchronized void commit(ChangeNumber changeNumber,
      LDAPUpdateMsg msg)
  {
    PendingChange curChange = pendingChanges.get(changeNumber);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);

    curChange.setMsg(msg);
  }

  /**
   * Mark an update message as committed.
   *
   * @param changeNumber The ChangeNumber of the update message that must be
   *                     set as committed.
   */
  public synchronized void commit(ChangeNumber changeNumber)
  {
    PendingChange curChange = pendingChanges.get(changeNumber);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);
  }

  /**
   * Add a new UpdateMsg to the pending list from the provided local
   * operation.
   *
   * @param operation The local operation for which an UpdateMsg must
   *                  be added in the pending list.
   * @return The ChangeNumber now associated to the operation.
   */
  public synchronized ChangeNumber putLocalOperation(PluginOperation operation)
  {
    ChangeNumber changeNumber;

    changeNumber = changeNumberGenerator.newChangeNumber();
    PendingChange change = new PendingChange(changeNumber, operation, null);
    pendingChanges.put(changeNumber, change);
    return changeNumber;

  }

  /**
   * Push all committed local changes to the replicationServer service.
   *
   * @return The number of pushed updates.
   */
  public synchronized int pushCommittedChanges()
  {
    int numSentUpdates = 0;
    if (pendingChanges.isEmpty())
      return numSentUpdates;

    ChangeNumber firstChangeNumber = pendingChanges.firstKey();
    PendingChange firstChange = pendingChanges.get(firstChangeNumber);

    while ((firstChange != null) && firstChange.isCommitted())
    {
      if ((firstChange.getOp() != null ) &&
          (firstChange.getOp().isSynchronizationOperation() == false))
      {
        numSentUpdates++;
        LDAPUpdateMsg updateMsg = firstChange.getMsg();
        domain.publish(updateMsg);
      }
      pendingChanges.remove(firstChangeNumber);

      if (pendingChanges.isEmpty())
      {
        firstChange = null;
      }
      else
      {
        firstChangeNumber = pendingChanges.firstKey();
        firstChange = pendingChanges.get(firstChangeNumber);
      }
    }
    return numSentUpdates;
  }
}
