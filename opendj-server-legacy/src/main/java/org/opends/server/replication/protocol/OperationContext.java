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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import org.opends.server.replication.common.CSN;
import org.opends.server.types.Operation;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class describe the replication context that is attached
 * to each Operation using the SYNCHROCONTEXT key.
 */
public abstract class OperationContext
{
  /** The identifier used to attach the context to operations. */
  public static final String SYNCHROCONTEXT = "replicationContext";

  /** The CSN of the Operation. */
  private CSN csn;

  /**
   * The unique Id of the entry that was modified in the original operation.
   */
  private String entryUUID;

  /**
   * Create a new OperationContext.
   * @param csn The CSN of the operation.
   * @param entryUUID The unique Identifier of the modified entry.
   */
  protected OperationContext(CSN csn, String entryUUID)
  {
    this.csn = csn;
    this.entryUUID = entryUUID;
  }

  /**
   * Gets the CSN of the Operation.
   *
   * @return The CSN of the Operation.
   */
  public CSN getCSN()
  {
    return csn;
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
   * Get the CSN of an operation.
   *
   * @param  op The operation.
   *
   * @return The CSN of the provided operation, or null if there is
   *         no CSN associated with the operation.
   */
  public static CSN getCSN(Operation op)
  {
    OperationContext ctx = (OperationContext)op.getAttachment(SYNCHROCONTEXT);
    if (ctx == null)
    {
      return null;
    }
    return ctx.csn;
  }

  /**
   * Get the CSN of an operation from the synchronization context
   * attached to the provided operation.
   *
   * @param  op The operation.
   *
   * @return The CSN of the provided operation, or null if there is
   *         no CSN associated with the operation.
   */
  public static CSN getCSN(PluginOperation op)
  {
    OperationContext ctx = (OperationContext)op.getAttachment(SYNCHROCONTEXT);
    if (ctx == null)
    {
      return null;
    }
    return ctx.csn;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof OperationContext)
    {
      OperationContext ctx = (OperationContext) obj;
      return this.csn.equals(ctx.getCSN())
          && this.entryUUID.equals(ctx.getEntryUUID());
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return csn.hashCode() + entryUUID.hashCode();
  }


}
