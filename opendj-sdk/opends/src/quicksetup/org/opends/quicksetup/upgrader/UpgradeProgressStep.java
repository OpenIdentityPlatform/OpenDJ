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

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.ProgressStep;

/**
   * Steps during the upgrade process.
 */
enum UpgradeProgressStep implements ProgressStep {

  NOT_STARTED(INFO_SUMMARY_UPGRADE_NOT_STARTED.get(), 0),

  DOWNLOADING(INFO_SUMMARY_UPGRADE_DOWNLOADING.get(), 10),

  EXTRACTING(INFO_SUMMARY_UPGRADE_EXTRACTING.get(), 20),

  INITIALIZING(INFO_SUMMARY_UPGRADE_INITIALIZING.get(), 30),

  CHECK_SERVER_HEALTH(INFO_SUMMARY_UPGRADE_CHECK_SERVER_HEALTH.get(), 35),

  CALCULATING_SCHEMA_CUSTOMIZATIONS(
          INFO_SUMMARY_UPGRADE_CALCULATING_SCHEMA_CUSTOMIZATION.get(), 40),

  CALCULATING_CONFIGURATION_CUSTOMIZATIONS(
          INFO_SUMMARY_UPGRADE_CALCULATING_CONFIG_CUSTOMIZATION.get(), 48),

  BACKING_UP_DATABASES(INFO_SUMMARY_UPGRADE_BACKING_UP_DB.get(), 50),

  BACKING_UP_FILESYSTEM(INFO_SUMMARY_UPGRADE_BACKING_UP_FILES.get(), 52),

  UPGRADING_COMPONENTS(INFO_SUMMARY_UPGRADE_UPGRADING_COMPONENTS.get(), 60),

  PREPARING_CUSTOMIZATIONS(
          INFO_SUMMARY_UPGRADE_PREPARING_CUSTOMIZATIONS.get(), 65),

  APPLYING_SCHEMA_CUSTOMIZATIONS(
          INFO_SUMMARY_UPGRADE_APPLYING_SCHEMA_CUSTOMIZATION.get(), 70),

  APPLYING_CONFIGURATION_CUSTOMIZATIONS(
          INFO_SUMMARY_UPGRADE_APPLYING_CONFIG_CUSTOMIZATION.get(), 75),

  VERIFYING(INFO_SUMMARY_UPGRADE_VERIFYING.get(), 80),

  STARTING_SERVER(INFO_SUMMARY_STARTING.get(), 90),

  STOPPING_SERVER(INFO_SUMMARY_STOPPING.get(), 90),

  RECORDING_HISTORY(INFO_SUMMARY_UPGRADE_HISTORY.get(), 97),

  CLEANUP(INFO_SUMMARY_UPGRADE_CLEANUP.get(), 99),

  ABORT(INFO_SUMMARY_UPGRADE_ABORT.get(), 99),

  FINISHED_WITH_ERRORS(INFO_SUMMARY_UPGRADE_FINISHED_WITH_ERRORS.get(), 100),

  FINISHED_WITH_WARNINGS(
          INFO_SUMMARY_UPGRADE_FINISHED_WITH_WARNINGS.get(), 100),

  FINISHED_CANCELED(INFO_SUMMARY_UPGRADE_FINISHED_CANCELED.get(), 100),

  FINISHED(INFO_SUMMARY_UPGRADE_FINISHED_SUCCESSFULLY.get("",""), 100);

  private Message summaryMsg;
  private int progress;

  private UpgradeProgressStep(Message summaryMsg, int progress) {
    this.summaryMsg = summaryMsg;
    this.progress = progress;
  }

  /**
   * Return a key for access a summary message.
   *
   * @return String representing key for access summary in resource bundle
   */
  public Message getSummaryMesssage() {
    return summaryMsg;
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
