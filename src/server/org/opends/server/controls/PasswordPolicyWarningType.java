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
  TIME_BEFORE_EXPIRATION((byte) 0x80,
                     INFO_PWPWARNTYPE_DESCRIPTION_TIME_BEFORE_EXPIRATION.get()),



  /**
   * The warning type that will be used to indicate that the user is
   * authenticating using a grace login and to provide the number of grace
   * logins that the user has left.
   */
  GRACE_LOGINS_REMAINING((byte) 0x81,
                     INFO_PWPWARNTYPE_DESCRIPTION_GRACE_LOGINS_REMAINING.get());



  // A lookup table for resolving a warning type from its BER type.
  private static final Map<Byte, PasswordPolicyWarningType> TABLE;
  static
  {
    TABLE = new HashMap<Byte, PasswordPolicyWarningType>();

    for (PasswordPolicyWarningType value : PasswordPolicyWarningType
        .values())
    {
      TABLE.put(value.type, value);
      TABLE.put(value.type, value);
    }
  }



  // The BER type to use for the associated element in the password policy
  // control.
  private final byte type;

  // The message ID for the description of this password policy error type.
  private final Message description;



  /**
   * Creates a new instance of a password policy warning type with the provided
   * BER type.
   *
   * @param  type           The BER type to use for the associated element in
   *                        the password policy control.
   * @param  description    The message for the description of this password
   *                        policy error type.
   */
  private PasswordPolicyWarningType(byte type, Message description)
  {
    this.type          = type;
    this.description   = description;
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
    return TABLE.get(Byte.valueOf(type));
  }



  /**
   * Retrieves a string representation of this password policy warning type.
   *
   * @return  A string representation of this password policy warning type.
   */
  @Override
  public String toString()
  {
    return Message.toString(description);
  }
}

