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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.guitools.replicationcli;

import java.util.LinkedList;

/**
 * This class is used to store the information provided by the user in the
 * replication command line.  It is required because when we are in interactive
 * mode the ReplicationCliArgumentParser is not enough.
 *
 */
abstract class ReplicationUserData
{
  private LinkedList<String> baseDNs = new LinkedList<String>();
  private String adminUid;
  private String adminPwd;

  /**
   * Returns the Global Administrator password.
   * @return the Global Administrator password.
   */
  String getAdminPwd()
  {
    return adminPwd;
  }

  /**
   * Sets the Global Administrator password.
   * @param adminPwd the Global Administrator password.
   */
  void setAdminPwd(String adminPwd)
  {
    this.adminPwd = adminPwd;
  }

  /**
   * Returns the Global Administrator UID.
   * @return the Global Administrator UID.
   */
  String getAdminUid()
  {
    return adminUid;
  }

  /**
   * Sets the Global Administrator UID.
   * @param adminUid the Global Administrator UID.
   */
  void setAdminUid(String adminUid)
  {
    this.adminUid = adminUid;
  }

  /**
   * Returns the Base DNs to replicate.
   * @return the Base DNs to replicate.
   */
  LinkedList<String> getBaseDNs()
  {
    return new LinkedList<String>(baseDNs);
  }

  /**
   * Sets the Base DNs to replicate.
   * @param baseDNs the Base DNs to replicate.
   */
  void setBaseDNs(LinkedList<String> baseDNs)
  {
    this.baseDNs.clear();
    this.baseDNs.addAll(baseDNs);
  }
}
