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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;




/**
 * This class defines a data type for storing information associated
 * with an account status notification.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class AccountStatusNotification
{
  // The notification type for this account status notification.
  private AccountStatusNotificationType notificationType;

  // The DN of the user entry to which this notification applies.
  private DN userDN;

  // A message that provides additional information for this account
  // status notification.
  private Message message;



  /**
   * Creates a new account status notification object with the
   * provided information.
   *
   * @param  notificationType  The type for this account status
   *                           notification.
   * @param  userDN            The DN of the user entry to which
   *                           this notification applies.
   * @param  message           The human-readable message for this
   *                           notification.
   */
  public AccountStatusNotification(
              AccountStatusNotificationType notificationType,
              DN userDN, Message message)
  {
    this.notificationType = notificationType;
    this.userDN           = userDN;
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
    return notificationType;
  }



  /**
   * Retrieves the DN of the user entry to which this notification
   * applies.
   *
   * @return  The DN of the user entry to which this notification
   *          applies.
   */
  public DN getUserDN()
  {
    return userDN;
  }



  /**
   * Retrieves a message that provides additional information for this
   * account status notification.
   *
   * @return  A message that provides additional information for this
   *          account status notification.
   */
  public Message getMessage()
  {
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
    return "AccountStatusNotification(type=" +
           notificationType.getNotificationName() + ",dn=" +
           userDN + ",message=" + message + ")";
  }
}

