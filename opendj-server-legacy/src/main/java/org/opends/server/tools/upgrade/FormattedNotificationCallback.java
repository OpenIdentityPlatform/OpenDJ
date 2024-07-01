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
 * A formatted notification callback for display title and more...
 */
public class FormattedNotificationCallback extends TextOutputCallback
{
  /**
   * Serial version UID.
   */
  private static final long serialVersionUID = 1L;

  /** Output a title. */
  public static final int TITLE_CALLBACK = 5;

  /** Output a sub-title. */
  public static final int SUBTITLE_CALLBACK = 6;

  /** Output a notice message. */
  public static final int NOTICE_CALLBACK = 7;

  /** Output an error. */
  public static final int ERROR_CALLBACK = 8;

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
  FormattedNotificationCallback(final LocalizableMessage message,
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
}
