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
 * Steps during the reversion process.
 */
enum ReversionProgressStep implements ProgressStep {

  NOT_STARTED(INFO_SUMMARY_REVERT_NOT_STARTED.get(), 0),

  INITIALIZING(INFO_SUMMARY_REVERT_INITIALIZING.get(), 20),

  STOPPING_SERVER(INFO_SUMMARY_STOPPING.get(), 40),

  REVERTING_FILESYSTEM(INFO_SUMMARY_REVERT_REVERTING_COMPONENTS.get(), 60),

  VERIFYING(INFO_SUMMARY_REVERT_VERIFYING.get(), 80),

  RECORDING_HISTORY(INFO_SUMMARY_REVERT_HISTORY.get(), 90),

  CLEANUP(INFO_SUMMARY_REVERT_CLEANUP.get(), 95),

  ABORT(INFO_SUMMARY_REVERT_ABORT.get(), 99),

  FINISHED_WITH_ERRORS(INFO_SUMMARY_REVERT_FINISHED_WITH_ERRORS_CLI.get(), 100),

  FINISHED_WITH_WARNINGS(
          INFO_SUMMARY_REVERT_FINISHED_WITH_WARNINGS_CLI.get(), 100),

  FINISHED_CANCELED(INFO_SUMMARY_REVERT_FINISHED_CANCELED_CLI.get(), 100),

  FINISHED(INFO_SUMMARY_REVERT_FINISHED_SUCCESSFULLY_CLI.get("",""), 100);

  private Message summaryMsg;
  private int progress;

  private ReversionProgressStep(Message summaryMsgKey, int progress) {
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
