/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import javax.security.auth.callback.TextOutputCallback;

import org.forgerock.i18n.LocalizableMessage;

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
      final LocalizableMessage message, final int progress)
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
