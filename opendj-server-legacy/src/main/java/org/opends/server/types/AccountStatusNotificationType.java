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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
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
  ACCOUNT_PERMANENTLY_LOCKED("account-permanently-locked"),
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

  /** The notification type name. */
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
    switch (toLowerCase(name))
    {
    case "account-temporarily-locked":
      return ACCOUNT_TEMPORARILY_LOCKED;
    case "account-permanently-locked":
      return ACCOUNT_PERMANENTLY_LOCKED;
    case "account-unlocked":
      return ACCOUNT_UNLOCKED;
    case "account-idle-locked":
      return ACCOUNT_IDLE_LOCKED;
    case "account-reset-locked":
      return ACCOUNT_RESET_LOCKED;
    case "account-disabled":
      return ACCOUNT_DISABLED;
    case "account-enabled":
      return ACCOUNT_ENABLED;
    case "account-expired":
      return ACCOUNT_EXPIRED;
    case "password-expired":
      return PASSWORD_EXPIRED;
    case "password-expiring":
      return PASSWORD_EXPIRING;
    case "password-reset":
      return PASSWORD_RESET;
    case "password-changed":
      return PASSWORD_CHANGED;
    default:
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

  @Override
  public String toString()
  {
    return name;
  }
}
