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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Operation;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class describe the replication context that is attached
 * to each Operation using the SYNCHROCONTEXT key.
 */
public abstract class OperationContext
{
  /**
   * The identifier used to attach the context to operations.
   */
  public static final String SYNCHROCONTEXT = "replicationContext";

  /**
   * The change Number of the Operation.
   */
  private ChangeNumber changeNumber;

  /**
   * The unique Id of the entry that was modified in the original operation.
   */
  private String entryUUID;

  /**
   * Create a new OperationContext.
   * @param changeNumber The change number of the operation.
   * @param entryUUID The unique Identifier of the modified entry.
   */
  protected OperationContext(ChangeNumber changeNumber, String entryUUID)
  {
    this.changeNumber = changeNumber;
    this.entryUUID = entryUUID;
  }

  /**
   * Gets The change number of the Operation.
   *
   * @return The change number of the Operation.
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Get the unique Identifier of the modified entry.
   *
   * @return the unique Identifier of the modified entry.
   */
  public String getEntryUUID()
  {
    return entryUUID;
  }

  /**
   * Get the change number of an operation.
   *
   * @param  op The operation.
   *
   * @return The change number of the provided operation, or null if there is
   *         no change number associated with the operation.
   */
  public static ChangeNumber getChangeNumber(Operation op)
  {
    OperationContext ctx = (OperationContext)op.getAttachment(SYNCHROCONTEXT);
    if (ctx == null)
    {
      return null;
    }
    return ctx.changeNumber;
  }

  /**
   * Get the change number of an operation from the synchronization context
   * attached to the provided operation.
   *
   * @param  op The operation.
   *
   * @return The change number of the provided operation, or null if there is
   *         no change number associated with the operation.
   */
  public static ChangeNumber getChangeNumber(PluginOperation op)
  {
    OperationContext ctx = (OperationContext)op.getAttachment(SYNCHROCONTEXT);
    if (ctx == null)
    {
      return null;
    }
    return ctx.changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof OperationContext)
    {
      OperationContext ctx = (OperationContext) obj;
      return ((this.changeNumber.equals(ctx.getChangeNumber()) &&
          (this.entryUUID.equals(ctx.getEntryUUID()))));
    }
    else
      return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return changeNumber.hashCode() + entryUUID.hashCode();
  }


}
