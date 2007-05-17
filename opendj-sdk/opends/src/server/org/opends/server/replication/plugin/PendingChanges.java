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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.types.DN;
import org.opends.server.types.Operation;

/**
 *
 * This class is use to store the list of operations currently
 * in progress and not yet committed in the database.
 */
public class PendingChanges
{
  /**
   * A map used to store the pending changes.
   */
  private SortedMap<ChangeNumber, PendingChange> pendingChanges =
    new TreeMap<ChangeNumber, PendingChange>();

  /**
   * A sorted set containing the list of PendingChanges that have
   * not been replayed correctly because they are dependent on
   * another change to be completed.
   */
  private SortedSet<PendingChange> dependentChanges =
    new TreeSet<PendingChange>();

  /**
   * The ChangeNumberGenerator to use to create new unique ChangeNumbers
   * for each operation done on the replication domain.
   */
  private ChangeNumberGenerator changeNumberGenerator;

  /**
   * The Replicationbroker that will be used to send UpdateMessage.
   */
  private ReplicationBroker broker;

  /**
   * The ServerState that will be updated when UpdateMessage are committed.
   */
  private ServerState state;

  /**
   * Creates a new PendingChanges using the provided ChangeNumberGenerator.
   *
   * @param changeNumberGenerator The ChangeNumberGenerator to use to create
   *                               new unique ChangeNumbers.
   * @param broker  The Replicationbroker that will be used to send
   *                UpdateMessage.
   * @param state   The ServerState that will be updated when UpdateMessage
   *                are committed.
   */
  public PendingChanges(
      ChangeNumberGenerator changeNumberGenerator, ReplicationBroker broker,
      ServerState state)
  {
    this.changeNumberGenerator = changeNumberGenerator;
    this.broker = broker;
    this.state = state;
  }

  /**
   * Remove and return an update form the pending changes list.
   *
   * @param changeNumber The ChangeNumber of the update to remove.
   *
   * @return The UpdateMessage that was just removed.
   */
  public synchronized UpdateMessage remove(ChangeNumber changeNumber)
  {
    return pendingChanges.remove(changeNumber).getMsg();
  }

  /**
   * Returns the number of update currently in the list.
   *
   * @return The number of update currently in the list.
   */
  public synchronized int size()
  {
    return pendingChanges.size();
  }

  /**
   * Mark an update message as committed.
   *
   * @param changeNumber The ChangeNumber of the update message that must be
   *                     set as committed.
   * @param op           The Operation associated of the update when the
   *                     update is a local update.
   * @param msg          The message associated to the update when the update
   *                     was received from a replication server.
   */
  public synchronized void commit(ChangeNumber changeNumber,
      Operation op, UpdateMessage msg)
  {
    PendingChange curChange = pendingChanges.get(changeNumber);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);

    if (op.isSynchronizationOperation())
      curChange.setOp(op);
    else
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
   * Add a new UpdateMessage to the pending list from the provided local
   * operation.
   *
   * @param operation The local operation for which an UpdateMessage mus
   *                  be added in the pending list.
   * @return The ChangeNumber now associated to the operation.
   */
  public synchronized ChangeNumber putLocalOperation(Operation operation)
  {
    ChangeNumber changeNumber;

    changeNumber = changeNumberGenerator.newChangeNumber();
    PendingChange change = new PendingChange(changeNumber, operation, null);
    pendingChanges.put(changeNumber, change);
    return changeNumber;

  }

  /**
   * Add a new UpdateMessage that was received from the replication server
   * to the pendingList.
   *
   * @param update The UpdateMessage that was received from the replication
   *               server and that will be added to the pending list.
   */
  public synchronized void putRemoteUpdate(UpdateMessage update)
  {
    ChangeNumber changeNumber = update.getChangeNumber();
    changeNumberGenerator.adjust(changeNumber);
    pendingChanges.put(changeNumber, new PendingChange(changeNumber, null,
                                                        update));
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
        broker.publish(firstChange.getMsg());
      }
      state.update(firstChangeNumber);
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

  /**
   * Get the first update in the list that have some dependencies cleared.
   *
   * @return The UpdateMessage to be handled.
   */
  public synchronized UpdateMessage getNextUpdate()
  {
    /*
     * Parse the list of Update with dependencies and check if the dependencies
     * are now cleared until an Update withour dependencies is found.
     */
    for (PendingChange change : dependentChanges)
    {
      if (change.dependenciesIsCovered(state))
      {
        dependentChanges.remove(change);
        return change.getMsg();
      }
    }
    return null;
  }


  /**
   * Check if the given AddOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   * AddOperation depends on
   *
   * - DeleteOperation done on the same DN
   * - ModifyDnOperation with the same target DN as the ADD DN
   * - ModifyDnOperation with new DN equals to the ADD DN parent
   * - AddOperation done on the parent DN of the ADD DN
   *
   * @param op The AddOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(AddOperation op)
  {
    boolean hasDependencies = false;
    DN targetDn = op.getEntryDN();
    ChangeNumber changeNumber = OperationContext.getChangeNumber(op);
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        UpdateMessage pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            /*
             * Check is the operation to be run is a deleteOperation on the
             * same DN.
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            /*
             * Check if the operation to be run is an addOperation on a
             * parent of the current AddOperation.
             */
            if (pendingChange.getTargetDN().isAncestorOf(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            /*
             * Check if the operation to be run is ModifyDnOperation with
             * the same target DN as the ADD DN
             * or a ModifyDnOperation with new DN equals to the ADD DN parent
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
            else
            {
              ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingChange.getMsg();
              if (pendingModDn.newDNIsParent(targetDn))
              {
                hasDependencies = true;
                addDependency(change, pendingChange);
              }
            }
          }
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given ModifyOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * ModifyOperation depends on
   * - AddOperation done on the same DN
   *
   * @param op The ModifyOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(ModifyOperation op)
  {
    boolean hasDependencies = false;
    DN targetDn = op.getEntryDN();
    ChangeNumber changeNumber = OperationContext.getChangeNumber(op);
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        UpdateMessage pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof AddMsg)
          {
            /*
             * Check if the operation to be run is an addOperation on a
             * same DN.
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Mark the first pendingChange as dependent on the second PendingChange.
   * @param dependentChange The PendingChange that depend on the second
   *                        PendingChange.
   * @param pendingChange   The PendingChange on which the first PendingChange
   *                        is dependent.
   */
  private void addDependency(
      PendingChange dependentChange, PendingChange pendingChange)
  {
    dependentChange.addDependency(pendingChange.getChangeNumber());
    dependentChanges.add(dependentChange);
  }

  /**
   * Check if the given ModifyDNMsg has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * Modify DN Operation depends on
   * - AddOperation done on the same DN as the target DN of the MODDN operation
   * - AddOperation done on the new parent of the MODDN  operation
   * - DeleteOperation done on the new DN of the MODDN operation
   * - ModifyDNOperation done from the new DN of the MODDN operation
   *
   * @param msg The ModifyDNMsg to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(ModifyDNMsg msg)
  {
    boolean hasDependencies = false;
    ChangeNumber changeNumber = msg.getChangeNumber();
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    DN targetDn = change.getTargetDN();


    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        UpdateMessage pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            // Check if the target of the Delete is the same
            // as the new DN of this ModifyDN
            if (msg.newDNIsEqual(pendingChange.getTargetDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            // Check if the Add Operation was done on the new parent of
            // the MODDN  operation
            if (msg.newParentIsEqual(pendingChange.getTargetDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
            // Check if the AddOperation was done on the same DN as the
            // target DN of the MODDN operation
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            // Check if the ModifyDNOperation was done from the new DN of
            // the MODDN operation
            if (msg.newDNIsEqual(pendingChange.getTargetDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given DeleteOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * DeleteOperation depends on
   * - DeleteOperation done on children DN
   * - ModifyDnOperation with target DN that are children of the DEL DN
   * - AddOperation done on the same DN
   *
   *
   * @param op The DeleteOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(DeleteOperation op)
  {
    boolean hasDependencies = false;
    DN targetDn = op.getEntryDN();
    ChangeNumber changeNumber = OperationContext.getChangeNumber(op);
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        UpdateMessage pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            /*
             * Check if the operation to be run is a deleteOperation on a
             * children of the current DeleteOperation.
             */
            if (pendingChange.getTargetDN().isDescendantOf(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            /*
             * Check if the operation to be run is an addOperation on a
             * parent of the current DeleteOperation.
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            /*
             * Check if the operation to be run is an ModifyDNOperation
             * on a children of the current DeleteOperation
             */
            if (pendingChange.getTargetDN().isDescendantOf(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
    }
    return hasDependencies;
  }
}
