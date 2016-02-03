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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;

/**
 * This class if used to build fake MODDN Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 */
public class FakeModdnOperation extends FakeOperation
{
  private Entry entry;

  /**
   * Creates a new FakeModdnOperation.
   *
   * @param csn     The CSN when the entry was last renamed.
   * @param entry   The entry that the MODDN operation will rename.
   */
  public FakeModdnOperation(CSN csn, Entry entry)
  {
    super(csn);
    this.entry = entry;
  }

  /** {@inheritDoc} */
  @Override
  public ReplicationMsg generateMessage()
  {
    DN dn = entry.getName();
    return new ModifyDNMsg(dn, getCSN(),
        EntryHistorical.getEntryUUID(entry),
        LDAPReplicationDomain.findEntryUUID(dn.parent()),
        false, dn.parent().toString(), dn.rdn().toString());
  }
}
