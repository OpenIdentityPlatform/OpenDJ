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



import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements an enumeration that holds the possible event
 * types that can trigger an account status notification.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum AccountStatusNotificationType
{
  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been temporarily locked after too
   * many failed attempts.
   */
  ACCOUNT_TEMPORARILY_LOCKED("account-temporarily-locked"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been permanently locked after too
   * many failed attempts.
   */
  ACCOUNT_PERMANENTLY_LOCKED(
       "account-permanently-locked"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been unlocked by an administrator.
   */
  ACCOUNT_UNLOCKED("account-unlocked"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been locked because it was idle for
   * too long.
   */
  ACCOUNT_IDLE_LOCKED("account-idle-locked"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been locked because it the password
   * had been reset by an administrator but not changed by the user
   * within the required interval.
   */
  ACCOUNT_RESET_LOCKED("account-reset-locked"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been disabled by an administrator.
   */
  ACCOUNT_DISABLED("account-disabled"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user account has been enabled by an administrator.
   */
  ACCOUNT_ENABLED("account-enabled"),



  /**
   * Indicates that an account status message should be generated
   * whenever a user authentication has failed because the account
   * has expired.
   */
  ACCOUNT_EXPIRED("account-expired"),



  /**
   * Indicates that an account status notification message should be
   * generated whenever a user authentication has failed because the
   * password has expired.
   */
  PASSWORD_EXPIRED("password-expired"),




  /**
   * Indicates that an account status notification message should be
   * generated the first time that a password expiration warning is
   * encountered for a user password.
   */
  PASSWORD_EXPIRING("password-expiring"),



  /**
   * Indicates that an account status notification message should be
   * generated whenever a user's password is reset by an
   * administrator.
   */
  PASSWORD_RESET("password-reset"),



  /**
   * Indicates whether an account status notification message should
   * be generated whenever a user changes his/her own password.
   */
  PASSWORD_CHANGED("password-changed");



  // The notification type name.
  private String name;



  /**
   * Creates a new account status notification type with the provided
   * name.
   *
   * @param  name  The name for this account status notification type.
   */
  private AccountStatusNotificationType(String name)
  {
    this.name = name;
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
    if (lowerName.equals("account-temporarily-locked"))
    {
      return ACCOUNT_TEMPORARILY_LOCKED;
    }
    else if (lowerName.equals("account-permanently-locked"))
    {
      return ACCOUNT_PERMANENTLY_LOCKED;
    }
    else if (lowerName.equals("account-unlocked"))
    {
      return ACCOUNT_UNLOCKED;
    }
    else if (lowerName.equals("account-idle-locked"))
    {
      return ACCOUNT_IDLE_LOCKED;
    }
    else if (lowerName.equals("account-reset-locked"))
    {
      return ACCOUNT_RESET_LOCKED;
    }
    else if (lowerName.equals("account-disabled"))
    {
      return ACCOUNT_DISABLED;
    }
    else if (lowerName.equals("account-enabled"))
    {
      return ACCOUNT_ENABLED;
    }
    else if (lowerName.equals("account-expired"))
    {
      return ACCOUNT_EXPIRED;
    }
    else if (lowerName.equals("password-expired"))
    {
      return PASSWORD_EXPIRED;
    }
    else if (lowerName.equals("password-expiring"))
    {
      return PASSWORD_EXPIRING;
    }
    else if (lowerName.equals("password-reset"))
    {
      return PASSWORD_RESET;
    }
    else if (lowerName.equals("password-changed"))
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
  public String getName()
  {
    return name;
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
    return name;
  }
}

