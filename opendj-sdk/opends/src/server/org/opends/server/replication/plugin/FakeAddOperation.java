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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.types.Entry;

/**
 * This class if used to build fake ADD Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 *
 */
public class FakeAddOperation extends FakeOperation
{
  private Entry entry;

  /**
   * Creates a new AddFakeOperations.
   *
   * @param cn     The ChangeNumber when the entry was created.
   * @param entry  The entry that the ADD operation will create.
   */
  public FakeAddOperation(ChangeNumber cn, Entry entry)
  {
    super(cn);
    this.entry = entry;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AddMsg generateMessage()
  {
    return new AddMsg(getChangeNumber(), entry.getDN().toString(),
               Historical.getEntryUuid(entry),
               ReplicationDomain.findEntryId(
                   entry.getDN().getParentDNInSuffix()),
               entry.getObjectClasses(),
               entry.getUserAttributes(), entry.getOperationalAttributes());
  }
}