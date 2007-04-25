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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.types.Operation;

/**
 * This class is use to store the list of operations currently
 * in progress and not yet committed in the database.
 */
public class PendingChange
{
  private ChangeNumber changeNumber;
  private boolean committed;
  private UpdateMessage msg;
  private Operation op;

  /**
   * Construct a new PendingChange.
   * @param changeNumber the ChangeNumber of use
   * @param op the operation to use
   * @param msg the message to use (can be null for local operations)
   */
  public PendingChange(ChangeNumber changeNumber,
                       Operation op,
                       UpdateMessage msg)
  {
    this.changeNumber = changeNumber;
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
   * Get the ChangeNumber associated to this PendingChange.
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Get the message associated to this PendingChange.
   * @return the message if operation was a replication operation
   * null if the operation was a local operation
   */
  public UpdateMessage getMsg()
  {
    return msg;
  }

  /**
   * Set the message associated to the PendingChange.
   * @param msg the message
   */
  public void setMsg(UpdateMessage msg)
  {
    this.msg = msg;
  }

  /**
   * Get the operation associated to the PendingChange.
   * @return the operation
   */
  public Operation getOp()
  {
    return this.op;
  }

  /**
   * Set the operation asociated to this PendingChange.
   * @param op The operation associated to this PendingChange.
   */
  public void setOp(Operation op)
  {
    this.op = op;
  }

}
