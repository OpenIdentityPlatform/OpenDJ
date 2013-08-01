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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import javax.security.auth.callback.TextOutputCallback;

import org.opends.messages.Message;

/**
 * A progress notification callback.
 */
public class ProgressNotificationCallback extends TextOutputCallback
{
  /**
   * The serial version uid.
   */
  private static final long serialVersionUID = 55L;

  /**
   * An integer representing the percentage of the task's process.
   */
  private int progress;

  /**
   * A progress notification constructor.
   *
   * @param messageType
   *          The type of the message, usually INFORMATION.
   * @param message
   *          The message to display
   * @param progress
   *          An integer representing the percentage of the task's progress.
   */
  ProgressNotificationCallback(final int messageType,
      final Message message, final int progress)
  {
    super(messageType, message.toString());
    this.progress = progress;
  }

  /**
   * Returns an integer which represents the task's progress percentage.
   *
   * @return An integer which represents the task's progress percentage.
   */
  public int getProgress()
  {
    return progress;
  }

  /**
   * Change the progress on an existing progress notification callback.
   *
   * @param progress
   *          The new value of the progress.
   * @return A progress Notification Callback
   */
  ProgressNotificationCallback setProgress(int progress)
  {
    this.progress = progress;
    return this;
  }

}
