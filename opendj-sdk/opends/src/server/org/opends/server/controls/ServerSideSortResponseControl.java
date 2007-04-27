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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the server-side sort response control as defined in RFC
 * 2891 section 1.2.  The ASN.1 description for the control value is:
 * <BR><BR>
 * <PRE>
 * SortResult ::= SEQUENCE {
 *    sortResult  ENUMERATED {
 *        success                   (0), -- results are sorted
 *        operationsError           (1), -- server internal failure
 *        timeLimitExceeded         (3), -- timelimit reached before
 *                                       -- sorting was completed
 *        strongAuthRequired        (8), -- refused to return sorted
 *                                       -- results via insecure
 *                                       -- protocol
 *        adminLimitExceeded       (11), -- too many matching entries
 *                                       -- for the server to sort
 *        noSuchAttribute          (16), -- unrecognized attribute
 *                                       -- type in sort key
 *        inappropriateMatching    (18), -- unrecognized or
 *                                       -- inappropriate matching
 *                                       -- rule in sort key
 *        insufficientAccessRights (50), -- refused to return sorted
 *                                       -- results to this client
 *        busy                     (51), -- too busy to process
 *        unwillingToPerform       (53), -- unable to sort
 *        other                    (80)
 *        },
 *  attributeType [0] AttributeDescription OPTIONAL }
 * </PRE>
 */
public class ServerSideSortResponseControl
       extends Control
{
  /**
   * The BER type to use when encoding the attribute type element.
   */
  private static final byte TYPE_ATTRIBUTE_TYPE = (byte) 0x80;



  // The result code for the sort result.
  private int resultCode;

  // The attribute type for the sort result.
  private String attributeType;



  /**
   * Creates a new server-side sort response control based on the provided
   * result code and attribute type.
   *
   * @param  resultCode     The result code for the sort result.
   * @param  attributeType  The attribute type for the sort result (or
   *                        {@code null} if there is none).
   */
  public ServerSideSortResponseControl(int resultCode, String attributeType)
  {
    super(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL, false,
          encodeControlValue(resultCode, attributeType));

    this.resultCode    = resultCode;
    this.attributeType = attributeType;
  }



  /**
   * Creates a new server-side sort response control with the provided
   * information.
   *
   * @param  oid            The OID to use for this control.
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  controlValue   The encoded value for this control.
   * @param  resultCode     The result code for the sort result.
   * @param  attributeType  The attribute type for the sort result.
   */
  private ServerSideSortResponseControl(String oid, boolean isCritical,
                                        ASN1OctetString controlValue,
                                        int resultCode,
                                        String attributeType)
  {
    super(oid, isCritical, controlValue);

    this.resultCode    = resultCode;
    this.attributeType = attributeType;
  }



  /**
   * Retrieves the result code for this sort result.
   *
   * @return  The result code for this sort result.
   */
  public int getResultCode()
  {
    return resultCode;
  }



  /**
   * Retrieves the attribute type for this sort result.
   *
   * @return  The attribute type for this sort result, or {@code null} if there
   *          is none.
   */
  public String getAttributeType()
  {
    return attributeType;
  }



  /**
   * Encodes the provided set of result codes and attribute types in a manner
   * suitable for use as the value of this control.
   *
   * @param  resultCode     The result code for the sort result.
   * @param  attributeType  The attribute type for the sort result, or
   *                        {@code null} if there is none.
   *
   * @return  The ASN.1 octet string containing the encoded sort result.
   */
  private static ASN1OctetString encodeControlValue(int resultCode,
                                                    String attributeType)
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(resultCode));

    if (attributeType != null)
    {
      elements.add(new ASN1OctetString(TYPE_ATTRIBUTE_TYPE, attributeType));
    }

    return new ASN1OctetString(new ASN1Sequence(elements).encode());
  }



  /**
   * Creates a new server-side sort response control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this server-side sort response control.  It must
   *                  not be {@code null}.
   *
   * @return  The server-side sort response control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         server-side sort response control.
   */
  public static ServerSideSortResponseControl decodeControl(Control control)
         throws LDAPException
  {
    ASN1OctetString controlValue = control.getValue();
    if (controlValue == null)
    {
      int    msgID   = MSGID_SORTRES_CONTROL_NO_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    try
    {
      ArrayList<ASN1Element> elements =
           ASN1Sequence.decodeAsSequence(control.getValue().value()).elements();
      int resultCode = elements.get(0).decodeAsEnumerated().intValue();

      String attributeType = null;
      if (elements.size() > 1)
      {
        attributeType = elements.get(1).decodeAsOctetString().stringValue();
      }

      return new ServerSideSortResponseControl(control.getOID(),
                                               control.isCritical(),
                                               control.getValue(), resultCode,
                                               attributeType);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_SORTRES_CONTROL_CANNOT_DECODE_VALUE;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message, e);
    }
  }



  /**
   * Retrieves a string representation of this server-side sort response
   * control.
   *
   * @return  A string representation of this server-side sort response control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this server-side sort response control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ServerSideSortResponseControl(resultCode=");
    buffer.append(resultCode);

    if (attributeType != null)
    {
      buffer.append(", attributeType=");
      buffer.append(attributeType);
    }

    buffer.append(")");
  }
}

