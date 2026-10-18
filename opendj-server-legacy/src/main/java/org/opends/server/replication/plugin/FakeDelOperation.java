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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.forgerock.opendj.ldap.DN;

/**
 * This class if used to build pseudo DEL Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 */
public class FakeDelOperation extends FakeOperation
{
  private final DN dn;
  private final String entryUUID;
  /** Whether a replication server has taken the delete: set by the threads which publish it. */
  private volatile boolean published;

  /**
   * Creates a new FakeDelOperation from the provided information.
   *
   * @param dn             The dn of the entry that was deleted.
   * @param csn   The CSN of the operation.
   * @param entryUUID      The Unique ID of the deleted entry.
   */
  public FakeDelOperation(DN dn, CSN csn, String entryUUID)
  {
    super(csn);
    this.dn = dn;
    this.entryUUID = entryUUID;
  }


  /** {@inheritDoc} */
  @Override
  public ReplicationMsg generateMessage()
  {
    return new DeleteMsg(dn, getCSN(), entryUUID);
  }

  /**
   * Retrieves the Unique ID of the entry that was deleted with this operation.
   *
   * @return  The Unique ID of the entry that was deleted with this operation.
   */
  public String getEntryUUID()
  {
    return entryUUID;
  }

  /**
   * Retrieves the DN of the entry that was deleted with this operation.
   *
   * @return  The DN of the entry that was deleted with this operation.
   */
  DN getDN()
  {
    return dn;
  }

  /** Records that a replication server has taken the delete: the broker wrote it to a session. */
  void published()
  {
    published = true;
  }

  /**
   * Tells whether a replication server has taken the delete: one which none has is published by
   * nobody once it is dropped from the deletes the domain keeps aside.
   *
   * @return {@code true} if the broker wrote the delete to a session
   */
  boolean isPublished()
  {
    return published;
  }
}
