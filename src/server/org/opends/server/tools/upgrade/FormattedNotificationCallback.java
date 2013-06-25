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
 * A formatted notification callback for display title and more...
 */
public class FormattedNotificationCallback extends TextOutputCallback
{
  /**
   * Serial version UID.
   */
  private static final long serialVersionUID = 1L;

  static final int TITLE_CALLBACK = 5;

  static final int SUBTITLE_CALLBACK = 6;

  static final int NOTICE_CALLBACK = 7;

  static final int ERROR_CALLBACK = 8;

  static final int BREAKLINE = 9;

  /**
   * An integer representing the message's sub-type.
   */
  private int messageSubType;

  /**
   * A progress notification constructor.
   *
   * @param message
   *          The message to display
   * @param messageSubType
   *          An integer representing the sub-type of this message.
   */
  public FormattedNotificationCallback(final Message message,
      final int messageSubType)
  {
    super(TextOutputCallback.INFORMATION, message.toString());
    this.messageSubType = messageSubType;
  }

  /**
   * Returns an integer which represents the message's sub-type.
   *
   * @return An integer which represents the message's sub-type.
   */
  public int getMessageSubType()
  {
    return messageSubType;
  }

  /**
   * Sets the message's sub-type.
   *
   * @param messageSubType
   *          The message's sub-type.
   */
  public void setMessageSubType(int messageSubType)
  {
    this.messageSubType = messageSubType;
  }
}
