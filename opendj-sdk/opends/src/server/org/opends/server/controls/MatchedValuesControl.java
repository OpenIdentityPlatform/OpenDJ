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



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the matched values control as defined in RFC 3876.  It
 * may be included in a search request to indicate that only attribute values
 * matching one or more filters contained in the matched values control should
 * be returned to the client.
 */
public class MatchedValuesControl
       extends Control
{



  // The set of matched values filters for this control.
  ArrayList<MatchedValuesFilter> filters;



  /**
   * Creates a new matched values control using the default OID and the provided
   * criticality and set of filters.
   *
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical to the operation processing.
   * @param  filters     The set of filters to use to determine which values to
   *                     return.
   */
  public MatchedValuesControl(boolean isCritical,
                              ArrayList<MatchedValuesFilter> filters)
  {
    super(OID_MATCHED_VALUES, isCritical, encodeValue(filters));


    this.filters = filters;
  }



  /**
   * Creates a new matched values control using the default OID and the provided
   * criticality and set of filters.
   *
   * @param  oid         The OID for this matched values control.
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical to the operation processing.
   * @param  filters     The set of filters to use to determine which values to
   *                     return.
   */
  public MatchedValuesControl(String oid, boolean isCritical,
                              ArrayList<MatchedValuesFilter> filters)
  {
    super(oid, isCritical, encodeValue(filters));


    this.filters = filters;
  }



  /**
   * Creates a new matched values control using the default OID and the provided
   * criticality and set of filters.
   *
   * @param  oid           The OID for this matched values control.
   * @param  isCritical    Indicates whether this control should be considered
   *                       critical to the operation processing.
   * @param  filters       The set of filters to use to determine which values
   *                       to return.
   * @param  encodedValue  The pre-encoded value for this matched values
   *                       control.
   */
  private MatchedValuesControl(String oid, boolean isCritical,
                               ArrayList<MatchedValuesFilter> filters,
                               ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.filters = filters;
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the control value.
   *
   * @param  filters  The set of filters to include in the control value.
   *
   * @return  An ASN.1 octet string containing the encoded information.
   */
  private static ASN1OctetString
                      encodeValue(ArrayList<MatchedValuesFilter> filters)
  {


    ArrayList<ASN1Element> elements =
         new ArrayList<ASN1Element>(filters.size());
    for (MatchedValuesFilter f : filters)
    {
      elements.add(f.encode());
    }


    return new ASN1OctetString(new ASN1Sequence(elements).encode());
  }



  /**
   * Creates a new matched values control from the contents of the provided
   * control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this matched values control.
   *
   * @return  The matched values control decoded from the provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         matched values control.
   */
  public static MatchedValuesControl decodeControl(Control control)
         throws LDAPException
  {


    if (! control.hasValue())
    {
      int    msgID   = MSGID_MATCHEDVALUES_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    ArrayList<ASN1Element> elements;
    try
    {
      elements =
           ASN1Sequence.decodeAsSequence(control.getValue().value()).elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_MATCHEDVALUES_CANNOT_DECODE_VALUE_AS_SEQUENCE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    if (elements.isEmpty())
    {
      int    msgID   = MSGID_MATCHEDVALUES_NO_FILTERS;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    ArrayList<MatchedValuesFilter> filters =
         new ArrayList<MatchedValuesFilter>(elements.size());
    for (ASN1Element e : elements)
    {
      filters.add(MatchedValuesFilter.decode(e));
    }


    return new MatchedValuesControl(control.getOID(), control.isCritical(),
                                    filters, control.getValue());
  }



  /**
   * Retrieves the set of filters associated with this matched values control.
   *
   * @return  The set of filters associated with this matched values control.
   */
  public ArrayList<MatchedValuesFilter> getFilters()
  {

    return filters;
  }



  /**
   * Indicates whether any of the filters associated with this matched values
   * control matches the provided attribute type/value.
   *
   * @param  type   The attribute type with which the value is associated.
   * @param  value  The attribute value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if at least one of the filters associated with
   *          this matched values control does match the provided attribute
   *          value, or <CODE>false</CODE> if none of the filters match.
   */
  public boolean valueMatches(AttributeType type, AttributeValue value)
  {

    for (MatchedValuesFilter f : filters)
    {
      try
      {
        if (f.valueMatches(type, value))
        {
          return true;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }

    return false;
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

    if (filters.size() == 1)
    {
      buffer.append("MatchedValuesControl(filter=\"");
      filters.get(0).toString(buffer);
      buffer.append("\")");
    }
    else
    {
      buffer.append("MatchedValuesControl(filters=\"(");

      for (MatchedValuesFilter f : filters)
      {
        f.toString(buffer);
      }

      buffer.append(")\")");
    }
  }
}

