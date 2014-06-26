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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class is use to store an operation currently
 * in progress and not yet committed in the database.
 */
class PendingChange implements Comparable<PendingChange>
{
  private final CSN csn;
  private boolean committed;
  private UpdateMsg msg;
  private final PluginOperation op;
  private ServerState dependencyState;

  /**
   * Construct a new PendingChange.
   * @param csn the CSN of use
   * @param op the operation to use
   * @param msg the message to use (can be null for local operations)
   */
  PendingChange(CSN csn, PluginOperation op, UpdateMsg msg)
  {
    this.csn = csn;
    this.committed = false;
    this.op = op;
    this.msg = msg;
  }

  /**
   * Check if a Change is already committed to the database.
   * @return true if change is already committed to the database.
   */
  public boolean isCommitted()
  {
    return committed;
  }

  /**
   * Set the committed status of a Pending Change.
   * @param committed status that must be set
   */
  public void setCommitted(boolean committed)
  {
    this.committed = committed;
  }

  /**
   * Get the CSN associated to this PendingChange.
   * @return the CSN
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Get the message associated to this PendingChange.
   * @return the message if operation was a replication operation
   * null if the operation was a local operation
   */
  public UpdateMsg getMsg()
  {
    return msg;
  }

  /**
   * Get the LDAPUpdateMsg associated to this PendingChange.
   *
   * @return the LDAPUpdateMsg if operation was a replication operation, null
   *         otherwise
   */
  public LDAPUpdateMsg getLDAPUpdateMsg()
  {
    if (msg instanceof LDAPUpdateMsg)
    {
      return (LDAPUpdateMsg) msg;
    }
    return null;
  }

  /**
   * Set the message associated to the PendingChange.
   * @param msg the message
   */
  public void setMsg(LDAPUpdateMsg msg)
  {
    this.msg = msg;
  }

  /**
   * Get the operation associated to the PendingChange.
   * @return the operation
   */
  public PluginOperation getOp()
  {
    return this.op;
  }

  /**
   * Add the given CSN to the list of dependencies of this PendingChange.
   *
   * @param csn
   *          The CSN to add to the list of dependencies of this PendingChange.
   */
  public void addDependency(CSN csn)
  {
    if (dependencyState == null)
    {
      dependencyState = new ServerState();
    }
    dependencyState.update(csn);
  }

  /**
   * Check if the given ServerState covers the dependencies of this
   * PendingChange.
   *
   * @param state The ServerState for which dependencies must be checked,
   *
   * @return A boolean indicating if the given ServerState covers the
   *         dependencies of this PendingChange.
   */
  public boolean dependenciesIsCovered(ServerState state)
  {
    return state.cover(dependencyState);
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(PendingChange o)
  {
    return csn.compareTo(o.csn);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + " committed=" + committed
        + ", csn=" + csn.toStringUI()
        + ", msg=[" + msg
        + "], isOperationSynchronized="
        + (op != null ? op.isSynchronizationOperation() : "false")
        + ", dependencyState="
        + (dependencyState != null ? dependencyState : "");
  }
}
