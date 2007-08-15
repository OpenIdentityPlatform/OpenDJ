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
import org.opends.messages.Message;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.PROTOCOL_ERROR;
import static org.opends.server.util.ServerConstants.OID_PAGED_RESULTS_CONTROL;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;

import java.util.ArrayList;

/**
 * This class represents a paged results control value as defined in
 * RFC 2696.
 *
 * The searchControlValue is an OCTET STRING wrapping the BER-encoded
 * version of the following SEQUENCE:
 *
 * realSearchControlValue ::= SEQUENCE {
 *         size            INTEGER (0..maxInt),
 *                                 -- requested page size from client
 *                                 -- result set size estimate from server
 *         cookie          OCTET STRING
 * }
 *
 */
public class PagedResultsControl extends Control
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The control value size element, which is either the requested page size
   * from the client, or the result set size estimate from the server.
   */
  private int size;


  /**
   * The control value cookie element.
   */
  private ASN1OctetString cookie;


  /**
   * Creates a new paged results control with the specified information.
   *
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical in processing the request.
   * @param  size        The size element.
   * @param  cookie      The cookie element.
   */
  public PagedResultsControl(boolean isCritical, int size,
                             ASN1OctetString cookie)
  {
    super(OID_PAGED_RESULTS_CONTROL, isCritical);


    this.size   = size;
    this.cookie = cookie;

    this.setValue(encode());
  }


  /**
   * Creates a new paged results control by decoding the given information.
   *
   * @param  isCritical  Indicates whether the control is considered
   *                     critical in processing the request.
   * @param  value       The value of the control.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided information as a paged results control.
   */
  public PagedResultsControl(boolean isCritical, ASN1OctetString value)
       throws LDAPException
  {
    super(OID_PAGED_RESULTS_CONTROL, isCritical, value);

    if (value == null)
    {
      Message message = ERR_LDAP_PAGED_RESULTS_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    ArrayList<ASN1Element> elements;
    try
    {
      ASN1Element sequence = ASN1Element.decode(value.value());
      elements = sequence.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int numElements = elements.size();
    if (numElements != 2)
    {
      Message message =
          ERR_LDAP_PAGED_RESULTS_DECODE_INVALID_ELEMENT_COUNT.get(numElements);
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    try
    {
      size = elements.get(0).decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_PAGED_RESULTS_DECODE_SIZE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      cookie = elements.get(1).decodeAsOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_PAGED_RESULTS_DECODE_COOKIE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }
  }


  /**
   * Encodes this control value to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded control value.
   */
  public ASN1OctetString encode()
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Integer(size));
    elements.add(cookie);

    ASN1Sequence sequence = new ASN1Sequence(elements);
    return new ASN1OctetString(sequence.encode());
  }


  /**
   * Get the control value size element, which is either the requested page size
   * from the client, or the result set size estimate from the server.
   * @return The control value size element.
   */
  public int getSize()
  {
    return size;
  }



  /**
   * Get the control value cookie element.
   * @return The control value cookie element.
   */
  public ASN1OctetString getCookie()
  {
    return cookie;
  }
}
