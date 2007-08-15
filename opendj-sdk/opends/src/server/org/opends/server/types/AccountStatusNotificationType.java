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



import org.opends.messages.MessageDescriptor;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements an enumeration that holds the possible event
 * types that can trigger an account status notification.
 */
public enum AccountStatusNotificationType
{
  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been temporarily locked after too
   * many failed attempts.
   */
  ACCOUNT_TEMPORARILY_LOCKED(
       INFO_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been permanently locked after too
   * many failed attempts.
   */
  ACCOUNT_PERMANENTLY_LOCKED(
       INFO_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been unlocked by an administrator.
   */
  ACCOUNT_UNLOCKED(INFO_ACCTNOTTYPE_ACCOUNT_UNLOCKED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been locked because it was idle for
   * too long.
   */
  ACCOUNT_IDLE_LOCKED(INFO_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been locked because it the password
   * had been reset by an administrator but not changed by the user
   * within the required interval.
   */
  ACCOUNT_RESET_LOCKED(INFO_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been disabled by an administrator.
   */
  ACCOUNT_DISABLED(INFO_ACCTNOTTYPE_ACCOUNT_DISABLED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been enabled by an administrator.
   */
  ACCOUNT_ENABLED(INFO_ACCTNOTTYPE_ACCOUNT_ENABLED.get()),



  /**
   * Indicates that an account status message should be generated
   * whenever a user authentication has failed because the account
   * has expired.
   */
  ACCOUNT_EXPIRED(INFO_ACCTNOTTYPE_ACCOUNT_EXPIRED.get()),



  /**
   * Indicates that an account status notification message should be
   * generated whenever a user authentication has failed because the
   * password has expired.
   */
  PASSWORD_EXPIRED(INFO_ACCTNOTTYPE_PASSWORD_EXPIRED.get()),




  /**
   * Indicates that an account status notification message should be
   * generated the first time that a password expiration warning is
   * encountered for a user password.
   */
  PASSWORD_EXPIRING(INFO_ACCTNOTTYPE_PASSWORD_EXPIRING.get()),



  /**
   * Indicates that an account status notification message should be
   * generated whenever a user's password is reset by an
   * administrator.
   */
  PASSWORD_RESET(INFO_ACCTNOTTYPE_PASSWORD_RESET.get()),



  /**
   * Indicates whether an account status notification message should
   * be generated whenever a user changes his/her own password.
   */
  PASSWORD_CHANGED(INFO_ACCTNOTTYPE_PASSWORD_CHANGED.get());



  // The notification type message.
  private Message notificationName;



  /**
   * Creates a new account status notification type with the provided
   * notification type ID.
   *
   * @param  notification      The notification message for
   *                           this account status notification.
   */
  private AccountStatusNotificationType(Message notification)
  {
    this.notificationName = notification;
  }



  /**
   * Retrieves the account status notification type with the specified
   * notification type identifier.
   *
   * @param  notification    The notification type message for
   *                         the notification type to retrieve.
   *
   * @return  The requested account status notification type, or
   *          <CODE>null</CODE> if there is no type for the given
   *          notification type identifier.
   */
  public static AccountStatusNotificationType
                     typeForMessage(Message notification)
  {
    MessageDescriptor md = notification.getDescriptor();
    if (INFO_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED.equals(md)) {
      return ACCOUNT_TEMPORARILY_LOCKED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED
            .equals(md)) {
      return ACCOUNT_PERMANENTLY_LOCKED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_UNLOCKED.equals(md)) {
      return ACCOUNT_UNLOCKED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED.equals(md)) {
      return ACCOUNT_IDLE_LOCKED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED.equals(md)) {
      return ACCOUNT_RESET_LOCKED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_DISABLED.equals(md)) {
      return ACCOUNT_DISABLED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_ENABLED.equals(md)) {
      return ACCOUNT_ENABLED;
    } else if (INFO_ACCTNOTTYPE_ACCOUNT_EXPIRED.equals(md)) {
      return ACCOUNT_EXPIRED;
    } else if (INFO_ACCTNOTTYPE_PASSWORD_EXPIRED.equals(md)) {
      return PASSWORD_EXPIRED;
    } else if (INFO_ACCTNOTTYPE_PASSWORD_EXPIRING.equals(md)) {
      return PASSWORD_EXPIRING;
    } else if (INFO_ACCTNOTTYPE_PASSWORD_RESET.equals(md)) {
      return PASSWORD_RESET;
    } else if (INFO_ACCTNOTTYPE_PASSWORD_CHANGED.equals(md)) {
      return PASSWORD_CHANGED;
    } else {
      return null;
    }
  }



  /**
   * Retrieves the account status notification type with the specified
   * name.
   *
   * @param  name  The name for the account status notification type
   *               to retrieve.
   *
   * @return  The requested account status notification type, or
   *          <CODE>null</CODE> if there is no type with the given
   *          name.
   */
  public static AccountStatusNotificationType typeForName(String name)
  {
    Message lowerName = Message.raw(toLowerCase(name));

    if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED.get()))
    {
      return ACCOUNT_TEMPORARILY_LOCKED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED.get()))
    {
      return ACCOUNT_PERMANENTLY_LOCKED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_UNLOCKED.get()))
    {
      return ACCOUNT_UNLOCKED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED.get()))
    {
      return ACCOUNT_IDLE_LOCKED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED.get()))
    {
      return ACCOUNT_RESET_LOCKED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_DISABLED.get()))
    {
      return ACCOUNT_DISABLED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_ENABLED.get()))
    {
      return ACCOUNT_ENABLED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_ACCOUNT_EXPIRED.get()))
    {
      return ACCOUNT_EXPIRED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_PASSWORD_EXPIRED.get()))
    {
      return PASSWORD_EXPIRED;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_PASSWORD_EXPIRING.get()))
    {
      return PASSWORD_EXPIRING;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_PASSWORD_RESET.get()))
    {
      return PASSWORD_RESET;
    }
    else if (lowerName.equals(
            INFO_ACCTNOTTYPE_PASSWORD_CHANGED.get()))
    {
      return PASSWORD_CHANGED;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves the name for this account status notification type.
   *
   * @return  The name for this account status notification type.
   */
  public Message getNotificationName()
  {
    return notificationName;
  }



  /**
   * Retrieves a string representation of this account status
   * notification type.
   *
   * @return  A string representation of this account status
   *          notification type.
   */
  public String toString()
  {
    return String.valueOf(getNotificationName());
  }
}

