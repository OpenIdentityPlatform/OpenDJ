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
 * Portions copyright 2012-2013 ForgeRock AS.
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
