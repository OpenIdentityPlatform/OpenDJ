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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.*;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.DN;
import org.opends.server.types.Operation;

/**
 * This class is used to store the list of remote changes received
 * from a replication server and that are either currently being replayed
 * or that are waiting for being replayed.
 *
 * It is used to know when the ServerState must be updated and to compute
 * the dependencies between operations.
 *
 * One of this object is instantiated for each ReplicationDomain.
 */
final class RemotePendingChanges
{
  /** A map used to store the pending changes. */
  private final SortedMap<CSN, PendingChange> pendingChanges = new TreeMap<>();

  /**
   * A sorted set containing the list of PendingChanges that have
   * not been replayed correctly because they are dependent on
   * another change to be completed.
   */
  private final SortedSet<PendingChange> dependentChanges = new TreeSet<>();

  /** The ServerState that will be updated when LDAPUpdateMsg are fully replayed. */
  private final ServerState state;

  /**
   * Creates a new RemotePendingChanges using the provided ServerState.
   *
   * @param state   The ServerState that will be updated when LDAPUpdateMsg
   *                have been fully replayed.
   */
  public RemotePendingChanges(ServerState state)
  {
    this.state = state;
  }

  /**
   * Returns the number of changes currently in this list.
   *
   * @return The number of changes currently in this list.
   */
  public synchronized int getQueueSize()
  {
    return pendingChanges.size();
  }

  /**
   * Add a new LDAPUpdateMsg that was received from the replication server
   * to the pendingList.
   *
   * @param update The LDAPUpdateMsg that was received from the replication
   *               server and that will be added to the pending list.
   * @return {@code false} if the update was already registered in the pending
   *         changes.
   */
  public synchronized boolean putRemoteUpdate(LDAPUpdateMsg update)
  {
    CSN csn = update.getCSN();
    return pendingChanges.put(csn,
        new PendingChange(csn, null, update)) == null;
  }

  /**
   * Mark an update message as committed.
   *
   * @param csn
   *          The CSN of the update message that must be set as committed.
   */
  public synchronized void commit(CSN csn)
  {
    PendingChange curChange = pendingChanges.get(csn);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);

    CSN firstCSN = pendingChanges.firstKey();
    PendingChange firstChange = pendingChanges.get(firstCSN);

    while (firstChange != null && firstChange.isCommitted())
    {
      if (firstChange.getMsg().contributesToDomainState())
      {
        state.update(firstCSN);
      }
      pendingChanges.remove(firstCSN);

      if (pendingChanges.isEmpty())
      {
        firstChange = null;
      }
      else
      {
        firstCSN = pendingChanges.firstKey();
        firstChange = pendingChanges.get(firstCSN);
      }
    }
  }

  /**
   * Get the first update in the list that have some dependencies cleared.
   *
   * @return The LDAPUpdateMsg to be handled.
   */
  public synchronized LDAPUpdateMsg getNextUpdate()
  {
    /*
     * Parse the list of Update with dependencies and check if the dependencies
     * are now cleared until an Update without dependencies is found.
     */
    for (PendingChange change : dependentChanges)
    {
      if (change.dependenciesIsCovered(state))
      {
        dependentChanges.remove(change);
        return change.getLDAPUpdateMsg();
      }
    }
    return null;
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
    dependentChange.addDependency(pendingChange.getCSN());
    dependentChanges.add(dependentChange);
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
    final DN targetDN = op.getEntryDN();
    final CSN csn = OperationContext.getCSN(op);
    final PendingChange change = pendingChanges.get(csn);
    if (change == null)
    {
      return false;
    }

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getCSN().isOlderThan(csn))
      {
        final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            /*
             * Check is the operation to be run is a deleteOperation on the
             * same DN.
             */
            if (pendingMsg.getDN().equals(targetDN))
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
            if (pendingMsg.getDN().isAncestorOf(targetDN))
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
            if (pendingMsg.getDN().equals(targetDN))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
            else
            {
              final ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingMsg;
              if (pendingModDn.newDNIsParent(targetDN))
              {
                hasDependencies = true;
                addDependency(change, pendingChange);
              }
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
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
    final DN targetDN = op.getEntryDN();
    final CSN csn = OperationContext.getCSN(op);
    final PendingChange change = pendingChanges.get(csn);
    if (change == null)
    {
      return false;
    }

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getCSN().isOlderThan(csn))
      {
        final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
        if (pendingMsg instanceof AddMsg)
        {
          // Check if the operation to be run is an addOperation on a same DN.
          if (pendingMsg.getDN().equals(targetDN))
          {
            hasDependencies = true;
            addDependency(change, pendingChange);
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
      }
    }
    return hasDependencies;
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
  private synchronized boolean checkDependencies(ModifyDNMsg msg)
  {
    boolean hasDependencies = false;
    final CSN csn = msg.getCSN();
    final PendingChange change = pendingChanges.get(csn);
    if (change == null)
    {
      return false;
    }

    final DN targetDN = change.getLDAPUpdateMsg().getDN();

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getCSN().isOlderThan(csn))
      {
        final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            // Check if the target of the Delete is the same
            // as the new DN of this ModifyDN
            if (msg.newDNIsEqual(pendingMsg.getDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            // Check if the Add Operation was done on the new parent of
            // the MODDN  operation
            if (msg.newParentIsEqual(pendingMsg.getDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
            // Check if the AddOperation was done on the same DN as the
            // target DN of the MODDN operation
            if (pendingMsg.getDN().equals(targetDN))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            // Check if the ModifyDNOperation was done from the new DN of
            // the MODDN operation
            if (msg.newDNIsEqual(pendingMsg.getDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
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
    final DN targetDN = op.getEntryDN();
    final CSN csn = OperationContext.getCSN(op);
    final PendingChange change = pendingChanges.get(csn);
    if (change == null)
    {
      return false;
    }

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getCSN().isOlderThan(csn))
      {
        final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            /*
             * Check if the operation to be run is a deleteOperation on a
             * children of the current DeleteOperation.
             */
            if (pendingMsg.getDN().isDescendantOf(targetDN))
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
            if (pendingMsg.getDN().equals(targetDN))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            final ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingMsg;
            /*
             * Check if the operation to be run is an ModifyDNOperation
             * on a children of the current DeleteOperation
             */
            if (pendingMsg.getDN().isDescendantOf(targetDN)
                || pendingModDn.newDNIsParent(targetDN))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
      }
    }
    return hasDependencies;
  }

  /**
   * Check the dependencies of a given Operation/UpdateMsg.
   *
   * @param op   The Operation for which dependencies must be checked.
   * @param msg  The LocalizableMessage for which dependencies must be checked.
   * @return     A boolean indicating if an operation cannot be replayed
   *             because of dependencies.
   */
  public boolean checkDependencies(Operation op, LDAPUpdateMsg msg)
  {
    if (op instanceof ModifyOperation)
    {
      ModifyOperation newOp = (ModifyOperation) op;
      return checkDependencies(newOp);

    } else if (op instanceof DeleteOperation)
    {
      DeleteOperation newOp = (DeleteOperation) op;
      return checkDependencies(newOp);

    } else if (op instanceof AddOperation)
    {
      AddOperation newOp = (AddOperation) op;
      return checkDependencies(newOp);
    } else if (op instanceof ModifyDNOperationBasis)
    {
      ModifyDNMsg newMsg = (ModifyDNMsg) msg;
      return checkDependencies(newMsg);
    } else
    {
      return true;  // unknown type of operation ?!
    }
  }
}
