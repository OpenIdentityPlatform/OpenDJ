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



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;
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
       MSGID_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been permanently locked after too
   * many failed attempts.
   */
  ACCOUNT_PERMANENTLY_LOCKED(
       MSGID_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been unlocked by an administrator.
   */
  ACCOUNT_UNLOCKED(MSGID_ACCTNOTTYPE_ACCOUNT_UNLOCKED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been locked because it was idle for
   * too long.
   */
  ACCOUNT_IDLE_LOCKED(MSGID_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been locked because it the password
   * had been reset by an administrator but not changed by the user
   * within the required interval.
   */
  ACCOUNT_RESET_LOCKED(MSGID_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been disabled by an administrator.
   */
  ACCOUNT_DISABLED(MSGID_ACCTNOTTYPE_ACCOUNT_DISABLED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been enabled by an administrator.
   */
  ACCOUNT_ENABLED(MSGID_ACCTNOTTYPE_ACCOUNT_ENABLED),



  /**
   * Indicates that an account status message should be generated
   * whenever a user authentication has failed because the account
   * has expired.
   */
  ACCOUNT_EXPIRED(MSGID_ACCTNOTTYPE_ACCOUNT_EXPIRED),



  /**
   * Indicates that an account status notification message should be
   * generated whenever a user authentication has failed because the
   * password has expired.
   */
  PASSWORD_EXPIRED(MSGID_ACCTNOTTYPE_PASSWORD_EXPIRED),




  /**
   * Indicates that an account status notification message should be
   * generated the first time that a password expiration warning is
   * encountered for a user password.
   */
  PASSWORD_EXPIRING(MSGID_ACCTNOTTYPE_PASSWORD_EXPIRING),



  /**
   * Indicates that an account status notification message should be
   * generated whenever a user's password is reset by an
   * administrator.
   */
  PASSWORD_RESET(MSGID_ACCTNOTTYPE_PASSWORD_RESET),



  /**
   * Indicates whether an account status notification message should
   * be generated whenever a user changes his/her own password.
   */
  PASSWORD_CHANGED(MSGID_ACCTNOTTYPE_PASSWORD_CHANGED);



  // The notification type identifier.
  private int notificationTypeID;



  /**
   * Creates a new account status notification type with the provided
   * notification type ID.
   *
   * @param  notificationTypeID  The notification type identifier for
   *                             this account status notification.
   */
  private AccountStatusNotificationType(int notificationTypeID)
  {
    this.notificationTypeID = notificationTypeID;
  }



  /**
   * Retrieves the account status notification type with the specified
   * notification type identifier.
   *
   * @param  notificationTypeID  The notification type identifier for
   *                             the notification type to retrieve.
   *
   * @return  The requested account status notification type, or
   *          <CODE>null</CODE> if there is no type for the given
   *          notification type identifier.
   */
  public static AccountStatusNotificationType
                     typeForID(int notificationTypeID)
  {
    switch(notificationTypeID)
    {
      case MSGID_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED:
        return ACCOUNT_TEMPORARILY_LOCKED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED:
        return ACCOUNT_PERMANENTLY_LOCKED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_UNLOCKED:
        return ACCOUNT_UNLOCKED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED:
        return ACCOUNT_IDLE_LOCKED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED:
        return ACCOUNT_RESET_LOCKED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_DISABLED:
        return ACCOUNT_DISABLED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_ENABLED:
        return ACCOUNT_ENABLED;
      case MSGID_ACCTNOTTYPE_ACCOUNT_EXPIRED:
        return ACCOUNT_EXPIRED;
      case MSGID_ACCTNOTTYPE_PASSWORD_EXPIRED:
        return PASSWORD_EXPIRED;
      case MSGID_ACCTNOTTYPE_PASSWORD_EXPIRING:
        return PASSWORD_EXPIRING;
      case MSGID_ACCTNOTTYPE_PASSWORD_RESET:
        return PASSWORD_RESET;
      case MSGID_ACCTNOTTYPE_PASSWORD_CHANGED:
        return PASSWORD_CHANGED;
      default:
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
    String lowerName = toLowerCase(name);

    if (lowerName.equals(getMessage(
             MSGID_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED)))
    {
      return ACCOUNT_TEMPORARILY_LOCKED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED)))
    {
      return ACCOUNT_PERMANENTLY_LOCKED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_UNLOCKED)))
    {
      return ACCOUNT_UNLOCKED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED)))
    {
      return ACCOUNT_IDLE_LOCKED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED)))
    {
      return ACCOUNT_RESET_LOCKED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_DISABLED)))
    {
      return ACCOUNT_DISABLED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_ENABLED)))
    {
      return ACCOUNT_ENABLED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_ACCOUNT_EXPIRED)))
    {
      return ACCOUNT_EXPIRED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_PASSWORD_EXPIRED)))
    {
      return PASSWORD_EXPIRED;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_PASSWORD_EXPIRING)))
    {
      return PASSWORD_EXPIRING;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_PASSWORD_RESET)))
    {
      return PASSWORD_RESET;
    }
    else if (lowerName.equals(getMessage(
                  MSGID_ACCTNOTTYPE_PASSWORD_CHANGED)))
    {
      return PASSWORD_CHANGED;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves the notification type identifier for this account
   * status notification type.
   *
   * @return  The notification type identifier for this account
   *          status notification type.
   */
  public int getNotificationTypeID()
  {
    return notificationTypeID;
  }



  /**
   * Retrieves the name for this account status notification type.
   *
   * @return  The name for this account status notification type.
   */
  public String getNotificationTypeName()
  {
    return getMessage(notificationTypeID);
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
    return getMessage(notificationTypeID);
  }
}

