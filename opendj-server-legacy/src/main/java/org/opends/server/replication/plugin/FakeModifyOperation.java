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
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.forgerock.opendj.ldap.DN;
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
