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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.uninstaller;

/**
 * This class is used to describe the current state of the uninstallation.
 * It contains the step in which the uninstallation is, the current progress
 * ratio, the progress bar message and the details message (the logs).
 *
 * This class is used directly by the ProgressPanel to update its content
 * and has been designed to match the layout of that panel.  However as it
 * does not contain any dependency in terms of code with any Swing or UI package
 * component it has been decided to leave it on the uninstaller package.
 *
 * In general the progress bar message and the details messages (log) are in
 * HTML form (but this class is independent of the format we use for the
 * messages).
 *
 */
public class UninstallProgressDescriptor
{
  private UninstallProgressStep step;

  private Integer progressBarRatio;

  private String progressBarMsg;

  private String detailsMsg;

  /**
   * Constructor for the UninstallProgressDescriptor.
   * @param step the current uninstall step.
   * @param progressBarRatio the completed progress ratio (in percentage).
   * @param progressBarMsg the message to be displayed in the progress bar.
   * @param detailsMsg the logs.
   */
  public UninstallProgressDescriptor(UninstallProgressStep step,
      Integer progressBarRatio, String progressBarMsg, String detailsMsg)
  {
    this.step = step;
    this.progressBarRatio = progressBarRatio;
    this.progressBarMsg = progressBarMsg;
    this.detailsMsg = detailsMsg;
  }

  /**
   * Returns the details message (the log message) of the uninstall.
   * @return the details message (the log message) of the uninstall.
   */
  public String getDetailsMsg()
  {
    return detailsMsg;
  }

  /**
   * Returns the progress bar message.
   * @return the progress bar message.
   */
  public String getProgressBarMsg()
  {
    return progressBarMsg;
  }

  /**
   * Returns the progress bar ratio (the percentage of the uninstall that is
   * completed).
   * @return the progress bar ratio (the percentage of the uninstall that is
   * completed).
   */
  public Integer getProgressBarRatio()
  {
    return progressBarRatio;
  }

  /**
   * Returns the step of the uninstall on which we are.
   * @return the step of the uninstall on which we are.
   */
  public UninstallProgressStep getProgressStep()
  {
    return step;
  }
}
