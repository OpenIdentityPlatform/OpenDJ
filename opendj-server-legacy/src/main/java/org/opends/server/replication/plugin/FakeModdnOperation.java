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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.types.DN;
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
