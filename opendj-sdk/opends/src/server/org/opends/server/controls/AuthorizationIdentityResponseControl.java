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
import org.opends.server.types.DN;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the authorization identity response control as defined
 * in RFC 3829.  It may be included in a bind response message to provide the
 * authorization ID resulting for a client after the bind operation as
 * completed.
 */
public class AuthorizationIdentityResponseControl
       extends Control
{



  // The authorization ID for this control.
  private String authorizationID;



  /**
   * Creates a new authorization identity response control using the default
   * settings to indicate an anonymous authentication.
   */
  public AuthorizationIdentityResponseControl()
  {
    super(OID_AUTHZID_RESPONSE, false, new ASN1OctetString());

  }



  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  authorizationID  The authorization ID for this control.
   */
  public AuthorizationIdentityResponseControl(String authorizationID)
  {
    super(OID_AUTHZID_RESPONSE, false, encodeValue(authorizationID));


    this.authorizationID = authorizationID;
  }



  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  authorizationDN  The authorization DN for this control.
   */
  public AuthorizationIdentityResponseControl(DN authorizationDN)
  {
    super(OID_AUTHZID_RESPONSE, false, encodeValue(authorizationDN));


    if (authorizationDN == null)
    {
      this.authorizationID = "dn:";
    }
    else
    {
      this.authorizationID = "dn:" + authorizationDN.toString();
    }
  }



  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  oid              The OID to use for this control.
   * @param  isCritical       Indicates whether this control should be
   *                          considered a critical part of the response
   *                          processing.
   * @param  authorizationID  The authorization ID for this control.
   */
  public AuthorizationIdentityResponseControl(String oid, boolean isCritical,
                                              String authorizationID)
  {
    super(oid, isCritical, encodeValue(authorizationID));


    this.authorizationID = authorizationID;
  }



  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  oid              The OID to use for this control.
   * @param  isCritical       Indicates whether this control should be
   *                          considered a critical part of the response
   *                          processing.
   * @param  authorizationDN  The authorization DN for this control.
   */
  public AuthorizationIdentityResponseControl(String oid, boolean isCritical,
                                              DN authorizationDN)
  {
    super(oid, isCritical, encodeValue(authorizationDN));


    if (authorizationDN == null)
    {
      this.authorizationID = "dn:";
    }
    else
    {
      this.authorizationID = "dn:" + authorizationDN.toString();
    }
  }



  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  oid              The OID to use for this control.
   * @param  isCritical       Indicates whether this control should be
   *                          considered a critical part of the response
   *                          processing.
   * @param  authorizationID  The authorization ID for this control.
   * @param  encodedValue     The encoded value for the control.
   */
  private AuthorizationIdentityResponseControl(String oid, boolean isCritical,
                                               String authorizationID,
                                               ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.authorizationID = authorizationID;
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the control value.
   *
   * @param  authorizationID  The authorization ID for this authorization ID
   *                          response control.
   *
   * @return  An ASN.1 octet string containing the encoded information.
   */
  private static ASN1OctetString encodeValue(String authorizationID)
  {
    return new ASN1OctetString(authorizationID);
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the control value.
   *
   * @param  authorizationDN  The authorization DN for this authorization ID
   *                          response control.
   *
   * @return  An ASN.1 octet string containing the encoded information.
   */
  private static ASN1OctetString encodeValue(DN authorizationDN)
  {
    if (authorizationDN == null)
    {
      return new ASN1OctetString("dn:");
    }
    else
    {
      return new ASN1OctetString("dn:" + authorizationDN.toString());
    }
  }



  /**
   * Creates a new authorization identity response control from the contents of
   * the provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this authorization identity response control.
   *
   * @return  The authorization identity response control decoded from the
   *          provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         authorization identity response control.
   */
  public static AuthorizationIdentityResponseControl decodeControl(
                                                          Control control)
         throws LDAPException
  {
    if (! control.hasValue())
    {
      int    msgID   = MSGID_AUTHZIDRESP_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    return new AuthorizationIdentityResponseControl(control.getOID(),
                    control.isCritical(), control.getValue().stringValue(),
                    control.getValue());
  }



  /**
   * Retrieves the authorization ID for this authorization identity response
   * control.
   *
   * @return  The authorization ID for this authorization identity response
   *          control.
   */
  public String getAuthorizationID()
  {
    return authorizationID;
  }



  /**
   * Specifies the authorization ID for this authorization identity response
   * control.
   *
   * @param  authorizationID  The authorization ID for this authorization
   *                          identity response control.
   */
  public void setAuthorizationID(String authorizationID)
  {
    this.authorizationID = authorizationID;
    setValue(encodeValue(authorizationID));
  }



  /**
   * Retrieves a string representation of this authorization identity response
   * control.
   *
   * @return  A string representation of this authorization identity response
   *          control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this authorization identity response
   * control to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AuthorizationIdentityResponseControl(authzID=\"");
    buffer.append(authorizationID);
    buffer.append("\")");
  }
}

