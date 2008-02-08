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

package org.opends.quicksetup.upgrader;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.ProgressStep;

/**
 * Steps during the reversion process.
 */
enum ReversionProgressStep implements ProgressStep {

  NOT_STARTED(INFO_SUMMARY_REVERT_NOT_STARTED.get(), null, null, 0, false),

  INITIALIZING(INFO_SUMMARY_REVERT_INITIALIZING.get(),
      INFO_PROGRESS_REVERT_INITIALIZING.get(),
      INFO_PROGRESS_REVERT_INITIALIZING.get(),
      20, true),

  STOPPING_SERVER(INFO_SUMMARY_STOPPING.get(), null, null, 40, false),

  REVERTING_FILESYSTEM(INFO_SUMMARY_REVERT_REVERTING_COMPONENTS.get(),
      INFO_PROGRESS_REVERT_REVERTING_COMPONENTS.get(),
      INFO_PROGRESS_REVERT_REVERTING_COMPONENTS.get(), 60, true),

  VERIFYING(INFO_SUMMARY_REVERT_VERIFYING.get(),
      INFO_PROGRESS_REVERT_VERIFYING.get(),
      INFO_PROGRESS_REVERT_VERIFYING.get(), 70, true),

  STARTING_SERVER(INFO_SUMMARY_STARTING.get(), null, null, 80, false),

  RECORDING_HISTORY(INFO_SUMMARY_REVERT_HISTORY.get(),
      INFO_PROGRESS_REVERT_HISTORY.get(),
      INFO_PROGRESS_REVERT_HISTORY.get(), 90, true),

  CLEANUP(INFO_SUMMARY_REVERT_CLEANUP.get(),
      INFO_PROGRESS_REVERT_CLEANUP.get(),
      INFO_PROGRESS_REVERT_CLEANUP.get(), 95, true),

  ABORT(INFO_SUMMARY_REVERT_ABORT.get(),
      INFO_PROGRESS_REVERT_ABORT.get(),
      INFO_PROGRESS_REVERT_ABORT.get(), 99, true),

  FINISHED_WITH_ERRORS(INFO_SUMMARY_REVERT_FINISHED_WITH_ERRORS_CLI.get(),
      null, null, 100, false),

  FINISHED_WITH_WARNINGS(
          INFO_SUMMARY_REVERT_FINISHED_WITH_WARNINGS_CLI.get(), null, null,
          100, false),

  FINISHED_CANCELED(INFO_SUMMARY_REVERT_FINISHED_CANCELED_CLI.get(), null,
      null, 100, false),

  FINISHED(INFO_SUMMARY_REVERT_FINISHED_SUCCESSFULLY_CLI.get("",""), null, null,
      100, false);

  private Message summaryMsg;
  private Message logMsg;
  private Message logMsgVerbose;
  private int progress;
  private boolean logWithPoints;

  private ReversionProgressStep(Message summaryMsg, Message logMsg,
      Message logMsgVerbose, int progress, boolean logWithPoints) {
    this.summaryMsg = summaryMsg;
    this.logMsg = logMsg;
    this.logMsgVerbose = logMsgVerbose;
    this.progress = progress;
    this.logWithPoints = logWithPoints;
  }

  /**
   * Return the summary message for the step.
   *
   * @return the summary message for the step.
   */
  public Message getSummaryMessage() {
    return summaryMsg;
  }

  /**
   * Return the log message for the step.
   * @param isVerbose whether we are running in verbose mode or not.
   *
   * @return the log message for the step.
   */
  public Message getLogMsg(boolean isVerbose) {
    Message msg;
    if (isVerbose)
    {
      msg = logMsgVerbose;
    }
    else
    {
      msg = logMsg;
    }
    return msg;
  }

  /**
   * Return whether we must add points to the log message or not.
   * @param isVerbose whether we are running in verbose mode or not.
   *
   * @return <CODE>true</CODE> if we must add points to the log message and
   * <CODE>false</CODE> otherwise.
   */
  public boolean logRequiresPoints(boolean isVerbose) {
    boolean returnValue;
    if (logWithPoints)
    {
      if (isVerbose)
      {
        returnValue = logMsgVerbose == logMsg;
      }
      else
      {
        returnValue = true;
      }
    }
    else
    {
      returnValue = false;
    }
    return returnValue;
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
