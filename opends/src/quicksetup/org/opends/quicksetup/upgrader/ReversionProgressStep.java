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
 * Steps during the reversion process.
 */
enum ReversionProgressStep implements ProgressStep {

  NOT_STARTED("summary-revert-not-started", 0),

  INITIALIZING("summary-revert-initializing", 20),

  STOPPING_SERVER("summary-stopping", 40),

  REVERTING_FILESYSTEM("summary-revert-reverting-components", 60),

  VERIFYING("summary-revert-verifying", 80),

  RECORDING_HISTORY("summary-revert-history", 90),

  CLEANUP("summary-revert-cleanup", 95),

  ABORT("summary-revert-abort", 99),

  FINISHED_WITH_ERRORS("summary-revert-finished-with-errors-cli", 100),

  FINISHED_WITH_WARNINGS("summary-revert-finished-with-warnings-cli", 100),

  FINISHED_CANCELED("summary-revert-finished-canceled-cli", 100),

  FINISHED("summary-revert-finished-successfully-cli", 100);

  private String summaryMsgKey;
  private int progress;

  private ReversionProgressStep(String summaryMsgKey, int progress) {
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
   *
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
