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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import static org.opends.messages.ProtocolMessages.*;

import java.util.HashMap;
import java.util.Map;

import org.opends.messages.Message;



/**
 * This enumeration defines the set of password policy errors that may be
 * included in the password policy response control defined in
 * draft-behera-ldap-password-policy.
 */
public enum PasswordPolicyErrorType
{
  /**
   * The error type that will be used to indicate that the user's password is
   * expired.
   */
  PASSWORD_EXPIRED(0,
                   INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_EXPIRED.get()),



  /**
   * The error type that will be used to indicate that the user's account is
   * locked.
   */
  ACCOUNT_LOCKED(1,
                 INFO_PWPERRTYPE_DESCRIPTION_ACCOUNT_LOCKED.get()),



  /**
   * The error type that will be used to indicate that the user's password must
   * be changed because it has been administratively reset.
   */
  CHANGE_AFTER_RESET(2,
                     INFO_PWPERRTYPE_DESCRIPTION_CHANGE_AFTER_RESET.get()),



  /**
   * The error type that will be used to indicate that user password changes are
   * not allowed.
   */
  PASSWORD_MOD_NOT_ALLOWED(
       3,
       INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_MOD_NOT_ALLOWED.get()),



  /**
   * The error type that will be used to indicate that the user's current
   * password must be provided in order to choose a new password.
   */
  MUST_SUPPLY_OLD_PASSWORD(
       4,
       INFO_PWPERRTYPE_DESCRIPTION_MUST_SUPPLY_OLD_PASSWORD.get()),



  /**
   * The error type that will be used to indicate that the provided password is
   * not acceptable according to the configured password validators.
   */
  INSUFFICIENT_PASSWORD_QUALITY(
       5,
       INFO_PWPERRTYPE_DESCRIPTION_INSUFFICIENT_PASSWORD_QUALITY.get()),



  /**
   * The error type that will be used to indicate that the provided password is
   * too short.
   */
  PASSWORD_TOO_SHORT(6,
                     INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_SHORT.get()),



  /**
   * The error type that will be used to indicate that the user's password is
   * too young (i.e., it was changed too recently to allow it to be changed
   * again).
   */
  PASSWORD_TOO_YOUNG(7,
                     INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_YOUNG.get()),



  /**
   * The error type that will be used to indicate that the provided password is
   * in the user's password history.
   */
  PASSWORD_IN_HISTORY(8,
                      INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_IN_HISTORY.get());



  // A lookup table for resolving an error type from its integer value.
  private static final Map<Integer, PasswordPolicyErrorType> TABLE;
  static
  {
    TABLE = new HashMap<Integer, PasswordPolicyErrorType>();

    for (PasswordPolicyErrorType type : PasswordPolicyErrorType
        .values())
    {
      TABLE.put(type.value, type);
      TABLE.put(type.value, type);
    }
  }



  // The integer value associated with the error type to use in the associated
  // enumerated element in the password policy response control.
  private int value;

  // The message ID for the description of this password policy error type.
  private Message description;



  /**
   * Creates a new instance of a password policy error type with the provided
   * value.
   *
   * @param  value          The integer value associated with the error type to
   *                        use in the associated enumerated element in the
   *                        password policy response control.
   * @param  description    The message for the description of this password
   *                        policy error type.
   */
  private PasswordPolicyErrorType(int value, Message description)
  {
    this.value         = value;
    this.description   = description;
  }



  /**
   * Retrieves the integer value associated with the error type to use in the
   * associated enumerated element in the password policy response control.
   *
   * @return  The integer value associated with the error type to use in the
   *          associated enumerated element in the password policy response
   *          control.
   */
  public int intValue()
  {
    return value;
  }



  /**
   * Retrieves the password policy error type for the provided integer value.
   *
   * @param  value  The value for which to retrieve the corresponding error
   *                type.
   *
   * @return  The requested password policy error type, or <CODE>null</CODE> if
   *          the provided value does not match any error types.
   */
  public static PasswordPolicyErrorType valueOf(int value)
  {
    return TABLE.get(Integer.valueOf(value));
  }



  /**
   * Retrieves a string representation of this password policy error type.
   *
   * @return  A string representation of this password policy error type.
   */
  @Override
  public String toString()
  {
    return description.toString();
  }
}

