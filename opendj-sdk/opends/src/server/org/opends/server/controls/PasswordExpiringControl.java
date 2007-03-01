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



import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the Netscape password expiring control, which serves as
 * a warning to clients that the user's password is about to expire. The only
 * element contained in the control value is a string representation of the
 * number of seconds until expiration.
 */
public class PasswordExpiringControl
       extends Control
{



  // The length of time in seconds until the password actually expires.
  private int secondsUntilExpiration;



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   */
  public PasswordExpiringControl(int secondsUntilExpiration)
  {
    super(OID_NS_PASSWORD_EXPIRING, false,
          new ASN1OctetString(String.valueOf(secondsUntilExpiration)));


    this.secondsUntilExpiration = secondsUntilExpiration;
  }



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  oid                     The OID to use for this control.
   * @param  isCritical              Indicates whether support for this control
   *                                 should be considered a critical part of the
   *                                 client processing.
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   */
  public PasswordExpiringControl(String oid, boolean isCritical,
                                 int secondsUntilExpiration)
  {
    super(oid, isCritical,
          new ASN1OctetString(String.valueOf(secondsUntilExpiration)));


    this.secondsUntilExpiration = secondsUntilExpiration;
  }



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  oid                     The OID to use for this control.
   * @param  isCritical              Indicates whether support for this control
   *                                 should be considered a critical part of the
   *                                 client processing.
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   * @param  encodedValue            The pre-encoded value for this control.
   */
  private PasswordExpiringControl(String oid, boolean isCritical,
                                  int secondsUntilExpiration,
                                  ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.secondsUntilExpiration = secondsUntilExpiration;
  }



  /**
   * Creates a new password expiring control from the contents of the provided
   * control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this password expiring control.
   *
   * @return  The password expiring control decoded from the provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         password expiring control.
   */
  public static PasswordExpiringControl decodeControl(Control control)
         throws LDAPException
  {
    if (! control.hasValue())
    {
      int    msgID   = MSGID_PWEXPIRING_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    int secondsUntilExpiration;
    try
    {
      secondsUntilExpiration =
           Integer.parseInt(control.getValue().stringValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    return new PasswordExpiringControl(control.getOID(), control.isCritical(),
                                       secondsUntilExpiration,
                                       control.getValue());
  }



  /**
   * Retrieves the length of time in seconds until the password actually
   * expires.
   *
   * @return  The length of time in seconds until the password actually expires.
   */
  public int getSecondsUntilExpiration()
  {
    return secondsUntilExpiration;
  }



  /**
   * Retrieves a string representation of this password expiring control.
   *
   * @return  A string representation of this password expiring control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this password expiring control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordExpiringControl(secondsUntilExpiration=");
    buffer.append(secondsUntilExpiration);
    buffer.append(")");
  }
}

