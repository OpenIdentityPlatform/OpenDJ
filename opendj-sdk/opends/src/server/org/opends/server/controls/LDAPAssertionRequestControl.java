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



import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the LDAP assertion request control as defined in RFC
 * 4528.  This control makes it possible to conditionally perform an operation
 * if a given assertion is true.  In particular, the associated operation should
 * only be processed if the target entry matches the filter contained in this
 * control.
 */
public class LDAPAssertionRequestControl
       extends Control
{



  // The unparsed LDAP search filter contained in the request from the client.
  private LDAPFilter rawFilter;

  // The processed search filter
  private SearchFilter filter;



  /**
   * Creates a new instance of this LDAP assertion request control with the
   * provided information.
   *
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the server processing.
   * @param  rawFilter   The unparsed LDAP search filter contained in the
   *                     request from the client.
   */
  public LDAPAssertionRequestControl(boolean isCritical, LDAPFilter rawFilter)
  {
    super(OID_LDAP_ASSERTION, isCritical,
          new ASN1OctetString(rawFilter.encode().encode()));


    this.rawFilter = rawFilter;

    filter = null;
  }



  /**
   * Creates a new instance of this LDAP assertion request control with the
   * provided information.
   *
   * @param  oid         The OID to use for this control.
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the server processing.
   * @param  rawFilter   The unparsed LDAP search filter contained in the
   *                     request from the client.
   */
  public LDAPAssertionRequestControl(String oid, boolean isCritical,
                                     LDAPFilter rawFilter)
  {
    super(oid, isCritical, new ASN1OctetString(rawFilter.encode().encode()));


    this.rawFilter = rawFilter;

    filter = null;
  }



  /**
   * Creates a new instance of this LDAP assertion request control with the
   * provided information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the server processing.
   * @param  rawFilter     The unparsed LDAP search filter contained in the
   *                       request from the client.
   * @param  encodedValue  The pre-encoded value for this control.
   */
  private LDAPAssertionRequestControl(String oid, boolean isCritical,
                                      LDAPFilter rawFilter,
                                      ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.rawFilter = rawFilter;

    filter = null;
  }



  /**
   * Creates a new LDAP assertion request control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this LDAP assertion request control.
   *
   * @return  The LDAP assertion control decoded from the provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid LDAP
   *                         assertion control.
   */
  public static LDAPAssertionRequestControl decodeControl(Control control)
         throws LDAPException
  {
    if (! control.hasValue())
    {
      int    msgID   = MSGID_LDAPASSERT_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    ASN1Element valueElement;
    try
    {
      valueElement = ASN1Element.decode(control.getValue().value());
    }
    catch (ASN1Exception ae)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ae);
      }

      int    msgID   = MSGID_LDAPASSERT_INVALID_CONTROL_VALUE;
      String message = getMessage(msgID, ae.getMessage());
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message,
                              ae);
    }


    return new LDAPAssertionRequestControl(control.getOID(),
                                           control.isCritical(),
                                           LDAPFilter.decode(valueElement),
                                           control.getValue());
  }



  /**
   * Retrieves the raw, unparsed filter from the request control.
   *
   * @return  The raw, unparsed filter from the request control.
   */
  public LDAPFilter getRawFilter()
  {
    return rawFilter;
  }



  /**
   * Sets the raw, unparsed filter from the request control.  This method should
   * only be called by pre-parse plugins.
   *
   * @param  rawFilter  The raw, unparsed filter from the request control.
   */
  public void setRawFilter(LDAPFilter rawFilter)
  {
    this.rawFilter = rawFilter;
    this.filter    = null;

    setValue(new ASN1OctetString(rawFilter.encode().encode()));
  }



  /**
   * Retrieves the processed search filter for this control.
   *
   * @return  The processed search filter for this control.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              process the search filter.
   */
  public SearchFilter getSearchFilter()
         throws DirectoryException
  {
    if (filter == null)
    {
      filter = rawFilter.toSearchFilter();
    }

    return filter;
  }



  /**
   * Retrieves a string representation of this LDAP assertion request control.
   *
   * @return  A string representation of this LDAP assertion request control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this LDAP assertion request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPAssertionRequestControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",filter=\"");
    rawFilter.toString(buffer);
    buffer.append("\")");
  }
}

