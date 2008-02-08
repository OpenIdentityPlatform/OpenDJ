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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements an enumeration that holds the possible set of
 * additional properties that can be included in an account status
 * notification.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum AccountStatusNotificationProperty
{
  /**
   * The property whose value will be the string representation of the
   * DN of the password policy for the target user.  This will be
   * available for all notification types.
   */
  PASSWORD_POLICY_DN("password-policy-dn"),



  /**
   * The property whose value will be a generalized time
   * representation of the time at which the user's account will be
   * unlocked.  This will be available for the
   * {@code ACCOUNT_TEMPORARILY_LOCKED} notification type.
   */
  ACCOUNT_UNLOCK_TIME("account-unlock-time"),



  /**
   * The property whose value will be the number of seconds until the
   * user's account is unlocked.  This will be available for the
   * {@code ACCOUNT_TEMPORARILY_LOCKED} notification type.
   */
  SECONDS_UNTIL_UNLOCK("seconds-until-unlock"),



  /**
   * The property whose value will be a localized, human-readable
   * representation of the length of time until the user's account is
   * unlocked.  This will be available for the
   * {@code ACCOUNT_TEMPORARILY_LOCKED} notification type.
   */
  TIME_UNTIL_UNLOCK("time-until-unlock"),



  /**
   * The property whose value will be the generalized time
   * representation of the time that the user's password will expire.
   * This will be available for the {@code PASSWORD_EXPIRING}
   * notification type.
   */
  PASSWORD_EXPIRATION_TIME("password-expiration-time"),



  /**
   * The property whose value will be the number of seconds until the
   * user's password expires.  This will be available for the
   * {@code PASSWORD_EXPIRING} notification type.
   */
  SECONDS_UNTIL_EXPIRATION("seconds-until-expiration"),



  /**
   * The property whose value will be a localized, human-readable
   * representation of the length of time until the user's password
   * expires.  This will be available for the
   * {@code PASSWORD_EXPIRING} notification type.
   */
  TIME_UNTIL_EXPIRATION("time-unti-expiration"),



  /**
   * The property whose value will be a clear-text representation of
   * the user's old password.  This may be available for the
   * {@code PASSWORD_RESET} and {@code PASSWORD_CHANGED} notification
   * types.
   */
  OLD_PASSWORD("old-password"),



  /**
   * The property whose value will be a clear-text representation of
   * the user's new password.  This may be available for the
   * {@code PASSWORD_RESET} and {@code PASSWORD_CHANGED} notification
   * types.
   */
  NEW_PASSWORD("new-password");



  // The notification type name.
  private String name;



  /**
   * Creates a new account status notification property with the
   * provided name.
   *
   * @param  name  The name for this account status notification
   *               property.
   */
  private AccountStatusNotificationProperty(String name)
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
  public static AccountStatusNotificationProperty forName(String name)
  {
    String lowerName = toLowerCase(name);
    if (lowerName.equals("password-policy-dn"))
    {
      return PASSWORD_POLICY_DN;
    }
    else if (lowerName.equals("account-unlock-time"))
    {
      return ACCOUNT_UNLOCK_TIME;
    }
    else if (lowerName.equals("seconds-until-unlock"))
    {
      return SECONDS_UNTIL_UNLOCK;
    }
    else if (lowerName.equals("time-until-unlock"))
    {
      return TIME_UNTIL_UNLOCK;
    }
    else if (lowerName.equals("password-expiration-time"))
    {
      return PASSWORD_EXPIRATION_TIME;
    }
    else if (lowerName.equals("seconds-until-expiration"))
    {
      return SECONDS_UNTIL_EXPIRATION;
    }
    else if (lowerName.equals("time-until-expiration"))
    {
      return TIME_UNTIL_EXPIRATION;
    }
    else if (lowerName.equals("old-password"))
    {
      return OLD_PASSWORD;
    }
    else if (lowerName.equals("new-password"))
    {
      return NEW_PASSWORD;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves the name for this account status notification property.
   *
   * @return  The name for this account status notification property.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves a string representation of this account status
   * notification property.
   *
   * @return  A string representation of this account status
   *          notification property.
   */
  public String toString()
  {
    return name;
  }
}

