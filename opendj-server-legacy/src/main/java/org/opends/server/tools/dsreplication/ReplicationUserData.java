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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.DN;

/**
 * This class is used to store the information provided by the user in the
 * replication command line.  It is required because when we are in interactive
 * mode the ReplicationCliArgumentParser is not enough.
 */
public abstract class ReplicationUserData
{
  private final LinkedList<DN> baseDNs = new LinkedList<>();
  private String adminUid;
  private String adminPwd;

  /**
   * Returns the Global Administrator password.
   * @return the Global Administrator password.
   */
  public String getAdminPwd()
  {
    return adminPwd;
  }

  /**
   * Sets the Global Administrator password.
   * @param adminPwd the Global Administrator password.
   */
  public void setAdminPwd(String adminPwd)
  {
    this.adminPwd = adminPwd;
  }

  /**
   * Returns the Global Administrator UID.
   * @return the Global Administrator UID.
   */
  public String getAdminUid()
  {
    return adminUid;
  }

  /**
   * Sets the Global Administrator UID.
   * @param adminUid the Global Administrator UID.
   */
  public void setAdminUid(String adminUid)
  {
    this.adminUid = adminUid;
  }

  /**
   * Returns the Base DNs to replicate.
   * @return the Base DNs to replicate.
   */
  public List<DN> getBaseDNs()
  {
    return new LinkedList<>(baseDNs);
  }

  /**
   * Sets the Base DNs to replicate.
   * @param baseDNs the Base DNs to replicate.
   */
  public void setBaseDNs(List<DN> baseDNs)
  {
    this.baseDNs.clear();
    this.baseDNs.addAll(baseDNs);
  }

  /**
   * Adds the provided base DN to the base DNs to replicate if not already present.
   *
   * @param baseDN
   *          the new base DN to replicate.
   */
  public void addBaseDN(DN baseDN)
  {
    if (!baseDNs.contains(baseDN))
    {
      baseDNs.add(baseDN);
    }
  }

  @Override
  public String toString()
  {
    return "ReplicationUserData(" + fieldsToString() + ")";
  }

  String fieldsToString()
  {
    // do not add password to avoid accidental logging
    return "baseDNs=" + baseDNs + ", adminUid=" + adminUid;
  }
}
