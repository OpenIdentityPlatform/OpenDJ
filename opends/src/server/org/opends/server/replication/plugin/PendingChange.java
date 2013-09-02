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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class is use to store an operation currently
 * in progress and not yet committed in the database.
 */
public class PendingChange implements Comparable<PendingChange>
{
  private CSN csn;
  private boolean committed;
  private LDAPUpdateMsg msg;
  private PluginOperation op;
  private ServerState dependencyState;
  private DN targetDN;

  /**
   * Construct a new PendingChange.
   * @param csn the CSN of use
   * @param op the operation to use
   * @param msg the message to use (can be null for local operations)
   */
  public PendingChange(CSN csn, PluginOperation op, LDAPUpdateMsg msg)
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
  public LDAPUpdateMsg getMsg()
  {
    return msg;
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
   * Set the operation associated to this PendingChange.
   * @param op The operation associated to this PendingChange.
   */
  public void setOp(PluginOperation op)
  {
    this.op = op;
  }

  /**
   * Add the given ChangeNumber in the list of dependencies of this
   * PendingChange.
   *
   * @param changeNumber The ChangeNumber to add in the list of dependencies
   *                     of this PendingChange.
   */
  public void addDependency(CSN changeNumber)
  {
    if (dependencyState == null)
    {
      dependencyState = new ServerState();
    }
    dependencyState.update(changeNumber);
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

  /**
   * Get the Target DN of this message.
   *
   * @return The target DN of this message.
   */
  public DN getTargetDN()
  {
    synchronized (this)
    {
      if (targetDN != null)
      {
        return targetDN;
      }
      try
      {
        targetDN = DN.decode(msg.getDn());
      }
      catch (DirectoryException e)
      {
      }
      return targetDN;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int compareTo(PendingChange o)
  {
    return getCSN().compareTo(o.getCSN());
  }
}
