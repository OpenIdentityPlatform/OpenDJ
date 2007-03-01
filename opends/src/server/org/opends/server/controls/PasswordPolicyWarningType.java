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
package org.opends.server.controls;



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;



/**
 * This enumeration defines the set of password policy warnings that may be
 * included in the password policy response control defined in
 * draft-behera-ldap-password-policy.
 */
public enum PasswordPolicyWarningType
{
  /**
   * The warning type that will be used to indicate that the password will
   * expire in the near future and to provide the length of time in seconds
   * until expiration.
   */
  TIME_BEFORE_EXPIRATION(PasswordPolicyWarningType.TYPE_TIME_BEFORE_EXPIRATION,
                         MSGID_PWPWARNTYPE_DESCRIPTION_TIME_BEFORE_EXPIRATION),



  /**
   * The warning type that will be used to indicate that the user is
   * authenticating using a grace login and to provide the number of grace
   * logins that the user has left.
   */
  GRACE_LOGINS_REMAINING(PasswordPolicyWarningType.TYPE_GRACE_LOGINS_REMAINING,
                         MSGID_PWPWARNTYPE_DESCRIPTION_GRACE_LOGINS_REMAINING);



  /**
   * The BER type that will be used for the time before expiration type.
   */
  public static final byte TYPE_TIME_BEFORE_EXPIRATION = (byte) 0x80;



  /**
   * The BER type that will be used for the grace logins remaining type.
   */
  public static final byte TYPE_GRACE_LOGINS_REMAINING = (byte) 0x81;



  // The BER type to use for the associated element in the password policy
  // control.
  private byte type;

  // The message ID for the description of this password policy error type.
  private int descriptionID;



  /**
   * Creates a new instance of a password policy warning type with the provided
   * BER type.
   *
   * @param  type           The BER type to use for the associated element in
   *                        the password policy control.
   * @param  descriptionID  The message ID for the description of this password
   *                        policy error type.
   */
  private PasswordPolicyWarningType(byte type, int descriptionID)
  {
    this.type          = type;
    this.descriptionID = descriptionID;
  }



  /**
   * Retrieves the BER type to use for the associated element in the password
   * policy control.
   *
   * @return  The BER type to use for the associated element in the password
   *          policy control.
   */
  public byte getType()
  {
    return type;
  }



  /**
   * Retrieves the password policy warning type for the provided BER type.
   *
   * @param  type  The BER type for which to retrieve the corresponding password
   *               policy warning type.
   *
   * @return  The requested password policy warning type, or <CODE>null</CODE>
   *          if none of the defined warning types have the provided BER type.
   */
  public static PasswordPolicyWarningType valueOf(byte type)
  {
    switch (type)
    {
      case TYPE_TIME_BEFORE_EXPIRATION:
        return PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION;
      case TYPE_GRACE_LOGINS_REMAINING:
        return PasswordPolicyWarningType.GRACE_LOGINS_REMAINING;
      default:
        return null;
    }
  }



  /**
   * Retrieves a string representation of this password policy warning type.
   *
   * @return  A string representation of this password policy warning type.
   */
  public String toString()
  {
    return getMessage(descriptionID);
  }
}

