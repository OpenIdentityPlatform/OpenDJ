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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
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
  /**
   * Written when the delivery which owns a remote change is taken over by the one which
   * follows it, and read by the dependency checks without the pending changes lock.
   */
  private volatile UpdateMsg msg;
  /**
   * Whether a replay thread owns this change: it is being replayed, or it waits for the
   * change it depends on. A remote change which no thread owns is one whose replay
   * failed and which the replication server is expected to deliver again.
   */
  private boolean owned;
  /**
   * How many times in a row the replay of this change failed, and when the first of
   * those failures happened - on a clock which only moves forward.
   * <p>
   * They live here, on the change which stays listed as the barrier holding the
   * ServerState back, rather than in a map on the side: a bound on such a map would have
   * a change evicted between two of its own failures and its give-up budget restarted,
   * so a replica failing more changes than the bound would never give up on any of them
   * (issue #889).
   */
  private int replayFailures;
  private long firstReplayFailureTimeMs;
  private final PluginOperation op;

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
   * Returns whether a replay thread owns this change.
   *
   * @return {@code true} if a replay thread is replaying this change or waiting for the
   *         change it depends on
   */
  public boolean isOwned()
  {
    return owned;
  }

  /**
   * Sets whether a replay thread owns this change.
   *
   * @param owned {@code true} when a replay thread takes the change over, {@code false}
   *              when its replay failed and the change must be delivered again
   */
  public void setOwned(boolean owned)
  {
    this.owned = owned;
  }

  /**
   * Records that the replay of this change failed once more.
   *
   * @param nowMs
   *          when it failed, on a clock which only moves forward
   */
  public void recordReplayFailure(long nowMs)
  {
    if (replayFailures == 0)
    {
      firstReplayFailureTimeMs = nowMs;
    }
    replayFailures++;
  }

  /**
   * Returns how many times in a row the replay of this change failed.
   *
   * @return the number of failures, 0 when its replay never failed
   */
  public int getReplayFailures()
  {
    return replayFailures;
  }

  /**
   * Returns how long the replay of this change has been failing.
   *
   * @param nowMs
   *          the current time, on the clock {@link #recordReplayFailure(long)} was given
   * @return the duration in milliseconds, 0 when its replay never failed
   */
  public long getReplayFailingForMs(long nowMs)
  {
    return replayFailures == 0 ? 0 : nowMs - firstReplayFailureTimeMs;
  }

  /**
   * Get the operation associated to the PendingChange.
   * @return the operation
   */
  public PluginOperation getOp()
  {
    return this.op;
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
        + (op != null ? op.isSynchronizationOperation() : "false");
  }
}
