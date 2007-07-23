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

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.ProgressStep;

/**
   * Steps during the upgrade process.
 */
enum UpgradeProgressStep implements ProgressStep {

  NOT_STARTED("summary-upgrade-not-started", 0),

  DOWNLOADING("summary-upgrade-downloading", 10),

  EXTRACTING("summary-upgrade-extracting", 20),

  INITIALIZING("summary-upgrade-initializing", 30),

  CHECK_SERVER_HEALTH("summary-upgrade-check-server-health", 35),

  CALCULATING_SCHEMA_CUSTOMIZATIONS(
          "summary-upgrade-calculating-schema-customization", 40),

  CALCULATING_CONFIGURATION_CUSTOMIZATIONS(
          "summary-upgrade-calculating-config-customization", 48),

  BACKING_UP_DATABASES("summary-upgrade-backing-up-db", 50),

  BACKING_UP_FILESYSTEM("summary-upgrade-backing-up-files",52),

  UPGRADING_COMPONENTS("summary-upgrade-upgrading-components", 60),

  PREPARING_CUSTOMIZATIONS("summary-upgrade-preparing-customizations", 65),

  APPLYING_SCHEMA_CUSTOMIZATIONS(
          "summary-upgrade-applying-schema-customization", 70),

  APPLYING_CONFIGURATION_CUSTOMIZATIONS(
          "summary-upgrade-applying-config-customization", 75),

  VERIFYING("summary-upgrade-verifying", 80),

  STARTING_SERVER("summary-starting", 90),

  STOPPING_SERVER("summary-stopping", 90),

  RECORDING_HISTORY("summary-upgrade-history", 97),

  CLEANUP("summary-upgrade-cleanup", 99),

  ABORT("summary-upgrade-abort", 99),

  FINISHED_WITH_ERRORS("summary-upgrade-finished-with-errors", 100),

  FINISHED_WITH_WARNINGS("summary-upgrade-finished-with-warnings", 100),

  FINISHED_CANCELED("summary-upgrade-finished-canceled", 100),

  FINISHED("summary-upgrade-finished-successfully", 100);

  private String summaryMsgKey;
  private int progress;

  private UpgradeProgressStep(String summaryMsgKey, int progress) {
    this.summaryMsgKey = summaryMsgKey;
    this.progress = progress;
  }

  /**
   * Return a key for access a summary message.
   *
   * @return String representing key for access summary in resource bundle
   */
  public String getSummaryMesssageKey() {
    return summaryMsgKey;
  }

  /**
   * Gets the amount of progress to show in the progress meter for this step.
   * @return int representing progress
   */
  public int getProgress() {
    return this.progress;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isLast() {
    return this == FINISHED ||
            this == FINISHED_WITH_ERRORS ||
            this == FINISHED_WITH_WARNINGS ||
            this == FINISHED_CANCELED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isError() {
    return this == FINISHED_WITH_ERRORS;
  }
}
