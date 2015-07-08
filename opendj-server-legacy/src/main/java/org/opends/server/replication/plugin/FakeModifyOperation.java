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
 *      Portions copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;

/**
 * This class if used to build fake Modify Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 */
public class FakeModifyOperation extends FakeOperation
{
  private List<Modification> mods = new ArrayList<>();
  private DN dn;
  private String entryuuid;

  /**
   * Creates a new ModifyFakeOperation with the provided information.
   *
   * @param dn The DN on which the Operation was applied.
   * @param csn The CSN of the operation.
   * @param entryuuid The unique ID of the entry on which the Operation applies.
   */
  public FakeModifyOperation(DN dn, CSN csn, String entryuuid)
  {
    super(csn);
    this.dn = dn;
    this.entryuuid = entryuuid;
  }

  /**
   * Add a modification to the list of modification included
   * in this fake operation.
   *
   * @param mod A modification that must be added to the list of modifications
   *            included in this fake operation.
   */
  public void addModification(Modification mod)
  {
    mods.add(mod);
  }

  /** {@inheritDoc} */
  @Override
  public ReplicationMsg generateMessage()
  {
    return new ModifyMsg(getCSN(), dn, mods, entryuuid);
  }
}
