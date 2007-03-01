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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;



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
  PASSWORD_EXPIRED(PasswordPolicyErrorType.TYPE_PASSWORD_EXPIRED,
                   MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_EXPIRED),



  /**
   * The error type that will be used to indicate that the user's account is
   * locked.
   */
  ACCOUNT_LOCKED(PasswordPolicyErrorType.TYPE_ACCOUNT_LOCKED,
                 MSGID_PWPERRTYPE_DESCRIPTION_ACCOUNT_LOCKED),



  /**
   * The error type that will be used to indicate that the user's password must
   * be changed because it has been administratively reset.
   */
  CHANGE_AFTER_RESET(PasswordPolicyErrorType.TYPE_CHANGE_AFTER_RESET,
                     MSGID_PWPERRTYPE_DESCRIPTION_CHANGE_AFTER_RESET),



  /**
   * The error type that will be used to indicate that user password changes are
   * not allowed.
   */
  PASSWORD_MOD_NOT_ALLOWED(
       PasswordPolicyErrorType.TYPE_PASSWORD_MOD_NOT_ALLOWED,
       MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_MOD_NOT_ALLOWED),



  /**
   * The error type that will be used to indicate that the user's current
   * password must be provided in order to choose a new password.
   */
  MUST_SUPPLY_OLD_PASSWORD(
       PasswordPolicyErrorType.TYPE_MUST_SUPPLY_OLD_PASSWORD,
       MSGID_PWPERRTYPE_DESCRIPTION_MUST_SUPPLY_OLD_PASSWORD),



  /**
   * The error type that will be used to indicate that the provided password is
   * not acceptable according to the configured password validators.
   */
  INSUFFICIENT_PASSWORD_QUALITY(
       PasswordPolicyErrorType.TYPE_INSUFFICIENT_PASSWORD_QUALITY,
       MSGID_PWPERRTYPE_DESCRIPTION_INSUFFICIENT_PASSWORD_QUALITY),



  /**
   * The error type that will be used to indicate that the provided password is
   * too short.
   */
  PASSWORD_TOO_SHORT(PasswordPolicyErrorType.TYPE_PASSWORD_TOO_SHORT,
                     MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_SHORT),



  /**
   * The error type that will be used to indicate that the user's password is
   * too young (i.e., it was changed too recently to allow it to be changed
   * again).
   */
  PASSWORD_TOO_YOUNG(PasswordPolicyErrorType.TYPE_PASSWORD_TOO_YOUNG,
                     MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_YOUNG),



  /**
   * The error type that will be used to indicate that the provided password is
   * in the user's password history.
   */
  PASSWORD_IN_HISTORY(PasswordPolicyErrorType.TYPE_PASSWORD_IN_HISTORY,
                      MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_IN_HISTORY);



  /**
   * The value that will be used for the passwordExpired type.
   */
  public static final int TYPE_PASSWORD_EXPIRED = 0;



  /**
   * The value that will be used for the accountLocked type.
   */
  public static final int TYPE_ACCOUNT_LOCKED = 1;



  /**
   * The value that will be used for the changeAfterReset type.
   */
  public static final int TYPE_CHANGE_AFTER_RESET = 2;



  /**
   * The value that will be used for the passwordModNotAllowed type.
   */
  public static final int TYPE_PASSWORD_MOD_NOT_ALLOWED = 3;



  /**
   * The value that will be used for the mustSupplyOldPassword type.
   */
  public static final int TYPE_MUST_SUPPLY_OLD_PASSWORD = 4;



  /**
   * The value that will be used for the insufficientPasswordQuality type.
   */
  public static final int TYPE_INSUFFICIENT_PASSWORD_QUALITY = 5;



  /**
   * The value that will be used for the passwordTooShort type.
   */
  public static final int TYPE_PASSWORD_TOO_SHORT = 6;



  /**
   * The value that will be used for the passwordTooYoung type.
   */
  public static final int TYPE_PASSWORD_TOO_YOUNG = 7;



  /**
   * The value that will be used for the passwordInHistory type.
   */
  public static final int TYPE_PASSWORD_IN_HISTORY = 8;



  // The integer value associated with the error type to use in the associated
  // enumerated element in the password policy response control.
  private int value;

  // The message ID for the description of this password policy error type.
  private int descriptionID;



  /**
   * Creates a new instance of a password policy error type with the provided
   * value.
   *
   * @param  value          The integer value associated with the error type to
   *                        use in the associated enumerated element in the
   *                        password policy response control.
   * @param  descriptionID  The message ID for the description of this password
   *                        policy error type.
   */
  private PasswordPolicyErrorType(int value, int descriptionID)
  {
    this.value         = value;
    this.descriptionID = descriptionID;
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
    switch (value)
    {
      case TYPE_PASSWORD_EXPIRED:
        return PasswordPolicyErrorType.PASSWORD_EXPIRED;
      case TYPE_ACCOUNT_LOCKED:
        return PasswordPolicyErrorType.ACCOUNT_LOCKED;
      case TYPE_CHANGE_AFTER_RESET:
        return PasswordPolicyErrorType.CHANGE_AFTER_RESET;
      case TYPE_PASSWORD_MOD_NOT_ALLOWED:
        return PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      case TYPE_MUST_SUPPLY_OLD_PASSWORD:
        return PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD;
      case TYPE_INSUFFICIENT_PASSWORD_QUALITY:
        return PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
      case TYPE_PASSWORD_TOO_SHORT:
        return PasswordPolicyErrorType.PASSWORD_TOO_SHORT;
      case TYPE_PASSWORD_TOO_YOUNG:
        return PasswordPolicyErrorType.PASSWORD_TOO_YOUNG;
      case TYPE_PASSWORD_IN_HISTORY:
        return PasswordPolicyErrorType.PASSWORD_IN_HISTORY;
      default:
        return null;
    }
  }



  /**
   * Retrieves a string representation of this password policy error type.
   *
   * @return  A string representation of this password policy error type.
   */
  public String toString()
  {
    return getMessage(descriptionID);
  }
}

