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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.SynchronizationMessage;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;

/**
 * This class if used to build fake Modify Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is usefull when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them
 *
 * @author Gilles Bellaton
 */
public class ModifyFakeOperation extends FakeOperation
{
  private ArrayList<Modification> mods = new ArrayList<Modification>();
  private DN dn;
  private String entryuuid;

  /**
   * Creates a new ModifyFakeOperation with the provided information.
   *
   * @param dn The dn on which the Operation was applied.
   * @param changenumber The ChangeNumber of the operation.
   * @param entryuuid The unique ID of the entry on which the Operation applies.
   */
  public ModifyFakeOperation(DN dn, ChangeNumber changenumber, String entryuuid)
  {
    super(changenumber);
    this.dn = dn;
    this.entryuuid = entryuuid;
  }

  /**
   * Add a modification to the list of modification included
   * in this fake operation.
   *
   * @param mod A modification that must be adde to the list of modifications
   *            included in this fake operation.
   */
  public void addModification(Modification mod)
  {
    mods.add(mod);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationMessage generateMessage()
  {
    return new ModifyMsg(super.getChangeNumber(), dn, mods, entryuuid);
  }
}
