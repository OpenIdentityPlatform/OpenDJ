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
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ReplicationMsg;

/**
 * This class if used to build fake Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 */
public abstract class FakeOperation
{
  private CSN csn;

  /**
   * Creates a new FakeOperation using the provided CSN.
   *
   * @param csn The CSN to use to build the FakeOperation.
   */
  public FakeOperation(CSN csn)
  {
    this.csn = csn;
  }

  /**
   * Get the CSN.
   *
   * @return Returns the CSN.
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Generate a ReplicationMsg from this fake operation.
   * The ReplicationMsg is used to send the informations about
   * this operation to the other servers.
   *
   * @return A ReplicationMsg that can be used to send information
   *         about this operation to remote servers.
   */
  public abstract ReplicationMsg generateMessage();
}
