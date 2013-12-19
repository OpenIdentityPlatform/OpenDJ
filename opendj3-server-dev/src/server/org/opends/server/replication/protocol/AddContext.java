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
 *      Portions copyright 2012-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import org.opends.server.replication.common.CSN;

/**
 * This class describes the context that is attached to Add Operation.
 */
public class AddContext extends OperationContext
{
  /**
   * The Unique Id of the parent entry of the added entry.
   */
  private String parentEntryUUID;

  /**
   * Creates a new AddContext with the provided information.
   *
   * @param csn The CSN of the add operation.
   * @param entryUUID the Unique Id of the added entry.
   * @param parentEntryUUID The unique Id of the parent of the added entry.
   */
  public AddContext(CSN csn, String entryUUID, String parentEntryUUID)
  {
    super(csn, entryUUID);
    this.parentEntryUUID = parentEntryUUID;
  }

  /**
   * Get the Unique Id of the parent of the added entry.
   *
   * @return Returns the Unique Id of the parent of the added entry.
   */
  public String getParentEntryUUID()
  {
    return parentEntryUUID;
  }
}
