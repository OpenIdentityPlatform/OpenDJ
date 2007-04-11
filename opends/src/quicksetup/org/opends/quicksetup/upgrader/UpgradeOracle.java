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

/**
 * This class can answer questions important upgrade/reversion questions
 * like 'can I upgrade from verion X to version Y?' and 'if not then why?'.
 */
public class UpgradeOracle {

  private Integer currentVersion;
  private Integer newVersion;

  /**
   * Creates a new instance that can analyze a hypothetical upgrade/reversion
   * operation from one version to another.
   * @param currentVersion Integer representing the current version
   * @param newVersion Integer representing the proposed next version
   */
  public UpgradeOracle(Integer currentVersion, Integer newVersion) {
    this.currentVersion = currentVersion;
    this.newVersion = newVersion;
  }

  /**
   * Indicates whether or not this operation would be considered an
   * upgrade (as opposed to a reversion).
   * @return boolean where true indicates that this would be an upgrade;
   *         false indicates that this would be a reversion.
   */
  public boolean isUpgrade() {
    return newVersion > currentVersion;
  }

  /**
   * Indicates whether or not this operation would be considered an
   * reversion (as opposed to an upgrade).
   * @return boolean where true indicates that this would be a reversion;
   *         false indicates that this would be an upgrade.
   */
  public boolean isReversion() {
    return newVersion < currentVersion;
  }

  /**
   * Indicates whether or not this hypothetical operation should be allowed
   * to happen.
   * @return boolean where true indicates that we are confident that such
   * an operation will succeed
   */
  public boolean isSupported() {
    boolean supported;
    if (// newVersion.equals(currentVersion) || // support this for reinstall?
        newVersion < 1565) {
      supported = false;
    }else {
      supported = true;
    }
    return supported;
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
    String[] args = { currentVersion.toString(),
            newVersion.toString() };
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
      } else {
        msg = rp.getMsg("upgrade-hypothetical-reversion-failure", args);
      }
    }
    return msg;
  }



}
