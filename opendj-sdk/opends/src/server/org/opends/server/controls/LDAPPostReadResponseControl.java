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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import org.opends.messages.Message;



import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.types.Control;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the post-read response control as defined in RFC 4527.
 * This control holds the search result entry representing the state of the
 * entry immediately before an add, modify, or modify DN operation.
 */
public class LDAPPostReadResponseControl
       extends Control
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The search result entry to include in the response control.
  private SearchResultEntry searchEntry;



  /**
   * Creates a new instance of this LDAP post-read response control with the
   * provided information.
   *
   * @param  searchEntry  The search result entry to include in the response
   *                      control.
   */
  public LDAPPostReadResponseControl(SearchResultEntry searchEntry)
  {
    super(OID_LDAP_READENTRY_POSTREAD, false,
          encodeEntry(searchEntry));


    this.searchEntry = searchEntry;
  }



  /**
   * Creates a new instance of this LDAP post-read response control with the
   * provided information.
   *
   * @param  oid          The OID to use for this control.
   * @param  isCritical   Indicates whether support for this control should be
   *                      considered a critical part of the server processing.
   * @param  searchEntry  The search result entry to include in the response
   *                      control.
   */
  public LDAPPostReadResponseControl(String oid, boolean isCritical,
                                    SearchResultEntry searchEntry)
  {
    super(oid, isCritical, encodeEntry(searchEntry));


    this.searchEntry = searchEntry;
  }



  /**
   * Creates a new instance of this LDAP post-read response control with the
   * provided information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the server processing.
   * @param  searchEntry   The search result entry to include in the response
   *                       control.
   * @param  encodedValue  The pre-encoded value for this control.
   */
  private LDAPPostReadResponseControl(String oid, boolean isCritical,
                                      SearchResultEntry searchEntry,
                                      ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.searchEntry = searchEntry;
  }



  /**
   * Creates a new LDAP post-read response control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this LDAP post-read response control.
   *
   * @return  The LDAP post-read response control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid LDAP
   *                         post-read response control.
   */
  public static LDAPPostReadResponseControl decodeControl(Control control)
         throws LDAPException
  {
    if (! control.hasValue())
    {
      Message message = ERR_POSTREADRESP_NO_CONTROL_VALUE.get();
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }


    ASN1OctetString controlValue = control.getValue();
    SearchResultEntry searchEntry;
    try
    {
      ASN1Element element = ASN1Element.decode(controlValue.value());
      SearchResultEntryProtocolOp searchResultEntryProtocolOp =
           SearchResultEntryProtocolOp.decodeSearchEntry(element);
      searchEntry = searchResultEntryProtocolOp.toSearchResultEntry();
    }
    catch (ASN1Exception ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }

      Message message =
          ERR_POSTREADRESP_CANNOT_DECODE_VALUE.get(ae.getMessage());
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message,
                              ae);
    }
    catch (LDAPException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }

      Message message =
          ERR_POSTREADRESP_CANNOT_DECODE_VALUE.get(le.getMessage());
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message,
                              le);
    }

    return new LDAPPostReadResponseControl(control.getOID(),
                                           control.isCritical(), searchEntry,
                                           controlValue);
  }



  /**
   * Encodes the provided search result entry for use as an attribute value.
   *
   * @param  searchEntry  The search result entry to be encoded.
   *
   * @return  The ASN.1 octet string containing the encoded control value.
   */
  private static ASN1OctetString encodeEntry(SearchResultEntry searchEntry)
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(searchEntry);
    return new ASN1OctetString(protocolOp.encode().encode());
  }



  /**
   * Retrieves the search result entry associated with this post-read response
   * control.
   *
   * @return  The search result entry associated with this post-read response
   *          control.
   */
  public SearchResultEntry getSearchEntry()
  {
    return searchEntry;
  }



  /**
   * Specifies the search result entry for use with this post-read response
   * control.
   *
   * @param  searchEntry  The search result entry for use with this post-read
   *                      response control.
   */
  public void setSearchEntry(SearchResultEntry searchEntry)
  {
    this.searchEntry = searchEntry;
    setValue(encodeEntry(searchEntry));
  }



  /**
   * Retrieves a string representation of this LDAP post-read response control.
   *
   * @return  A string representation of this LDAP post-read response control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this LDAP post-read response control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPPostReadResponseControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",entry=");
    searchEntry.toSingleLineString(buffer);
    buffer.append(")");
  }
}

