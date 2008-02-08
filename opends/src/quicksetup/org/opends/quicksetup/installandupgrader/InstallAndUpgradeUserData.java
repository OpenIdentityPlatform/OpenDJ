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

package org.opends.quicksetup.installandupgrader;

import org.opends.quicksetup.upgrader.UpgradeUserData;

/**
 * User data specific to the upgrade and install merged application.
 */
public class InstallAndUpgradeUserData extends UpgradeUserData
{
  private boolean isUpgrade;

  /**
   * Returns <CODE>true</CODE> if we are doing an upgrade and <CODE>false</CODE>
   * if not.
   * @return <CODE>true</CODE> if we are doing an upgrade and <CODE>false</CODE>
   * if not.
   */
  public boolean isUpgrade() {
    return isUpgrade;
  }

  /**
   * Sets whether we want to make an upgrade.
   * @param isUpgrade the boolean telling whether we want to do an upgrade or
   * not.
   */
  public void setUpgrade(boolean isUpgrade) {
    this.isUpgrade = isUpgrade;
  }
}
