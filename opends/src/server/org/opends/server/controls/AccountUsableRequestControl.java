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



import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the Sun-defined account usable request control.  The
 * OID for this control is 1.3.6.1.4.1.42.2.27.9.5.8, and it does not have a
 * value.
 */
public class AccountUsableRequestControl
       extends Control
{



  /**
   * Creates a new instance of the account usable request control with the
   * default settings.
   */
  public AccountUsableRequestControl()
  {
    super(OID_ACCOUNT_USABLE_CONTROL, false);

  }



  /**
   * Creates a new instance of the account usable request control with the
   * provided information.
   *
   * @param  oid         The OID to use for this control.
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the client processing.
   */
  public AccountUsableRequestControl(String oid, boolean isCritical)
  {
    super(oid, isCritical);

  }



  /**
   * Creates a new account usable request control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this account usable request control.
   *
   * @return  The account usable request control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         account usable request control.
   */
  public static AccountUsableRequestControl decodeControl(Control control)
         throws LDAPException
  {
    if (control.hasValue())
    {
      int    msgID   = MSGID_ACCTUSABLEREQ_CONTROL_HAS_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    return new AccountUsableRequestControl(control.getOID(),
                                           control.isCritical());
  }



  /**
   * Retrieves a string representation of this account usable request control.
   *
   * @return  A string representation of this account usable request control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this account usable request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AccountUsableRequestControl()");
  }
}

