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
 * This class is used to describe the context attached to a Delete Operation.
 */
public class DeleteContext extends OperationContext
{
  /**
   * Creates a new DeleteContext with the provided information.
   *
   * @param csn The CSN of the Delete Operation.
   * @param entryUUID The unique Id of the deleted entry.
   */
  public DeleteContext(CSN csn, String entryUUID)
  {
    super(csn, entryUUID);
  }
}
