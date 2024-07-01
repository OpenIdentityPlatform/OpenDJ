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
 * This class describe the replication context that is attached to
 * Modify operation.
 */
public class ModifyContext extends OperationContext
{

  /**
   * Creates a new Modify Context with the provided parameters.
   *
   * @param csn The CSN of the operation.
   * @param entryUUID the unique Id of the modified entry.
   */
  public ModifyContext(CSN csn, String entryUUID)
  {
    super(csn, entryUUID);
  }
}
