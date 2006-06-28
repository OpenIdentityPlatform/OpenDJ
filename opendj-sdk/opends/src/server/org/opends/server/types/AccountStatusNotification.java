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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data type for storing information associated
 * with an account status notification.
 */
public class AccountStatusNotification
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.AccountStatusNotification";



  // The notification type for this account status notification.
  private AccountStatusNotificationType notificationType;

  // The message ID for the account status notification message.
  private int messageID;

  // A message that provides additional information for this account
  // status notification.
  private String message;



  /**
   * Creates a new account status notification object with the
   * provided information.
   *
   * @param  notificationType  The type for this account status
   *                           notification.
   * @param  messageID         The unique ID for this notification.
   * @param  message           The human-readable message for this
   *                           notification.
   */
  public AccountStatusNotification(
              AccountStatusNotificationType notificationType,
              int messageID, String message)
  {
    assert debugEnter(CLASS_NAME, String.valueOf(notificationType),
                      String.valueOf(messageID),
                      String.valueOf(message));

    this.notificationType = notificationType;
    this.messageID        = messageID;
    this.message          = message;
  }



  /**
   * Retrieves the notification type for this account status
   * notification.
   *
   * @return  The notification type for this account status
   *          notification.
   */
  public AccountStatusNotificationType getNotificationType()
  {
    assert debugEnter(CLASS_NAME, "getNotificationType");

    return notificationType;
  }



  /**
   * Retrieves the message ID for the account status notification
   * message.
   *
   * @return  The message ID for the account status notification
   *          message.
   */
  public int getMessageID()
  {
    assert debugEnter(CLASS_NAME, "getMessageID");

    return messageID;
  }



  /**
   * Retrieves a message that provides additional information for this
   * account status notification.
   *
   * @return  A message that provides additional information for this
   *          account status notification.
   */
  public String getMessage()
  {
    assert debugEnter(CLASS_NAME, "getMessage");

    return message;
  }



  /**
   * Retrieves a string representation of this account status
   * notification.
   *
   * @return  A string representation of this account status
   *          notification.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    return "AccountStatusNotification(type=" +
           String.valueOf(notificationType) + ",id=" + messageID +
           ",message=" + message + ")";
  }
}

