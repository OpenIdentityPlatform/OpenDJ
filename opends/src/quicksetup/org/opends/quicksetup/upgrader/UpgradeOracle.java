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

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.BuildInformation;

/**
 * This class can answer questions important upgrade/reversion questions
 * like 'can I upgrade from verion X to version Y?' and 'if not then why?'.
 */
public class UpgradeOracle {

  private BuildInformation currentBuildInfo;
  private BuildInformation newBuildInfo;

  /**
   * Creates a new instance that can analyze a hypothetical upgrade/reversion
   * operation from one version to another.
   * @param current BuildInformation representing the current version
   * @param neu BuildInformation representing the proposed next version
   */
  public UpgradeOracle(BuildInformation current, BuildInformation neu) {
    this.currentBuildInfo = current;
    this.newBuildInfo = neu;
  }

  /**
   * Indicates whether or not this operation would be considered an
   * upgrade (as opposed to a reversion).
   * @return boolean where true indicates that this would be an upgrade;
   *         false indicates that this would be a reversion.
   */
  public boolean isUpgrade() {
    return currentBuildInfo.compareTo(newBuildInfo) < 0;
  }

  /**
   * Indicates whether or not this operation would be considered an
   * reversion (as opposed to an upgrade).
   * @return boolean where true indicates that this would be a reversion;
   *         false indicates that this would be an upgrade.
   */
  public boolean isReversion() {
    return currentBuildInfo.compareTo(newBuildInfo) < 0;
  }

  /**
   * Indicates whether or not this hypothetical operation should be allowed
   * to happen.
   * @return boolean where true indicates that we are confident that such
   * an operation will succeed
   */
  public boolean isSupported() {
    return !isReversion();
  }

  /**
   * Creates a string summarizing a hypothetical upgrade/reversion
   * from <code>currentVersion</code> to <code>newVersion</code> giving
   * reasons why such an attempt would not be successful.
   * @return String representing a localized message giving a summary of
   * this hypothetical operation.
   */
  public String getSummaryMessage() {
    String msg;
    String[] args = { currentBuildInfo.toString(),
            currentBuildInfo.toString() };
    ResourceProvider rp = ResourceProvider.getInstance();
    if (isSupported()) {
      if (isUpgrade()) {
        msg = rp.getMsg("upgrade-hypothetical-upgrade-success", args);
      } else if (isReversion()) {
        msg = rp.getMsg("upgrade-hypothetical-reversion-success", args);
      } else {
        msg = rp.getMsg("upgrade-hypothetical-versions-the-same", args);
      }
    } else {
      if (isUpgrade()) {
        msg = rp.getMsg("upgrade-hypothetical-upgrade-failure", args);
      } else if (isReversion()) {
        msg = rp.getMsg("upgrade-hypothetical-reversion-failure", args);
      } else {
        msg = rp.getMsg("upgrade-hypothetical-versions-the-same", args);
      }
    }
    return msg;
  }



}
