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
import org.opends.messages.Message;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the virtual list view response controls as defined in
 * draft-ietf-ldapext-ldapv3-vlv.  The ASN.1 description for the control value
 * is:
 * <BR><BR>
 * <PRE>
 * VirtualListViewResponse ::= SEQUENCE {
 *       targetPosition    INTEGER (0 .. maxInt),
 *       contentCount     INTEGER (0 .. maxInt),
 *       virtualListViewResult ENUMERATED {
 *            success (0),
 *            operationsError (1),
 *            protocolError (3),
 *            unwillingToPerform (53),
 *            insufficientAccessRights (50),
 *            timeLimitExceeded (3),
 *            adminLimitExceeded (11),
 *            innapropriateMatching (18),
 *            sortControlMissing (60),
 *            offsetRangeError (61),
 *            other(80),
 *            ... },
 *       contextID     OCTET STRING OPTIONAL }
 * </PRE>
 */
public class VLVResponseControl
       extends Control
{
  // The context ID for this VLV response control.
  private ByteString contextID;

  // The content count estimating the total number of entries in the result set.
  private int contentCount;

  // The offset of the target entry in the result set.
  private int targetPosition;

  // The result code for the VLV operation.
  private int vlvResultCode;



  /**
   * Creates a new VLV response control with the provided information.
   *
   * @param  targetPosition  The position of the target entry in the result set.
   * @param  contentCount    The content count estimating the total number of
   *                         entries in the result set.
   * @param  vlvResultCode   The result code for the VLV operation.
   */
  public VLVResponseControl(int targetPosition, int contentCount,
                            int vlvResultCode)
  {
    this(targetPosition, contentCount, vlvResultCode, null);
  }



  /**
   * Creates a new VLV response control with the provided information.
   *
   * @param  targetPosition  The position of the target entry in the result set.
   * @param  contentCount    The content count estimating the total number of
   *                         entries in the result set.
   * @param  vlvResultCode   The result code for the VLV operation.
   * @param  contextID       The context ID for this VLV response control.
   */
  public VLVResponseControl(int targetPosition, int contentCount,
                            int vlvResultCode, ByteString contextID)
  {
    super(OID_VLV_RESPONSE_CONTROL, false,
          encodeControlValue(targetPosition, contentCount, vlvResultCode,
                             contextID));

    this.targetPosition = targetPosition;
    this.contentCount   = contentCount;
    this.vlvResultCode  = vlvResultCode;
    this.contextID      = contextID;
  }



  /**
   * Creates a new VLV response control with the provided information.
   *
   * @param  oid             The OID for the control.
   * @param  isCritical      Indicates whether the control should be considered
   *                         critical.
   * @param  controlValue    The pre-encoded value for the control.
   * @param  targetPosition  The position of the target entry in the result set.
   * @param  contentCount    The content count estimating the total number of
   *                         entries in the result set.
   * @param  vlvResultCode   The result code for the VLV operation.
   * @param  contextID       The context ID for this VLV response control.
   */
  private VLVResponseControl(String oid, boolean isCritical,
                             ASN1OctetString controlValue, int targetPosition,
                             int contentCount, int vlvResultCode,
                             ByteString contextID)
  {
    super(oid, isCritical, controlValue);

    this.targetPosition = targetPosition;
    this.contentCount   = contentCount;
    this.vlvResultCode  = vlvResultCode;
    this.contextID      = contextID;
  }



  /**
   * Retrieves the position of the target entry in the result set.
   *
   * @return  The position of the target entry in the result set.
   */
  public int getTargetPosition()
  {
    return targetPosition;
  }



  /**
   * Retrieves the estimated total number of entries in the result set.
   *
   * @return  The estimated total number of entries in the result set.
   */
  public int getContentCount()
  {
    return contentCount;
  }



  /**
   * Retrieves the result code for the VLV operation.
   *
   * @return  The result code for the VLV operation.
   */
  public int getVLVResultCode()
  {
    return vlvResultCode;
  }



  /**
   * Retrieves a context ID value that should be included in the next request
   * to retrieve a page of the same result set.
   *
   * @return  A context ID value that should be included in the next request to
   *          retrieve a page of the same result set, or {@code null} if there
   *          is no context ID.
   */
  public ByteString getContextID()
  {
    return contextID;
  }



  /**
   * Encodes the provided information in a manner suitable for use as the value
   * of this control.
   *
   * @param  targetPosition  The position of the target entry in the result set.
   * @param  contentCount    The content count estimating the total number of
   *                         entries in the result set.
   * @param  vlvResultCode   The result code for the VLV operation.
   * @param  contextID       The context ID for this VLV response control.
   *
   * @return  The ASN.1 octet string containing the encoded sort order.
   */
  private static ASN1OctetString encodeControlValue(int targetPosition,
                                      int contentCount, int vlvResultCode,
                                      ByteString contextID)
  {
    ArrayList<ASN1Element> vlvElements = new ArrayList<ASN1Element>(4);
    vlvElements.add(new ASN1Integer(targetPosition));
    vlvElements.add(new ASN1Integer(contentCount));
    vlvElements.add(new ASN1Enumerated(vlvResultCode));

    if (contextID != null)
    {
      vlvElements.add(contextID.toASN1OctetString());
    }

    return new ASN1OctetString(new ASN1Sequence(vlvElements).encode());
  }



  /**
   * Creates a new VLV response control from the contents of the provided
   * control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this VLV response control.  It must not be
   *                  {@code null}.
   *
   * @return  The VLV response control decoded from the provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid VLV
   *                         response control.
   */
  public static VLVResponseControl decodeControl(Control control)
         throws LDAPException
  {
    ASN1OctetString controlValue = control.getValue();
    if (controlValue == null)
    {
      Message message = INFO_VLVRES_CONTROL_NO_VALUE.get();
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }

    try
    {
      ASN1Sequence vlvSequence =
           ASN1Sequence.decodeAsSequence(controlValue.value());
      ArrayList<ASN1Element> elements = vlvSequence.elements();

      if ((elements.size() < 3) || (elements.size() > 4))
      {
        Message message =
            INFO_VLVRES_CONTROL_INVALID_ELEMENT_COUNT.get(elements.size());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
      }

      int targetPosition = elements.get(0).decodeAsInteger().intValue();
      int contentCount   = elements.get(1).decodeAsInteger().intValue();
      int vlvResultCode  = elements.get(2).decodeAsEnumerated().intValue();

      ASN1OctetString contextID = null;
      if (elements.size() == 4)
      {
        contextID = elements.get(3).decodeAsOctetString();
      }

      return new VLVResponseControl(control.getOID(), control.isCritical(),
                                    controlValue, targetPosition, contentCount,
                                    vlvResultCode, contextID);
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      Message message =
          INFO_VLVRES_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message, e);
    }
  }



  /**
   * Retrieves a string representation of this VLV request control.
   *
   * @return  A string representation of this VLV request control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this VLV request control to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("VLVResponseControl(targetPosition=");
    buffer.append(targetPosition);
    buffer.append(", contentCount=");
    buffer.append(contentCount);
    buffer.append(", vlvResultCode=");
    buffer.append(vlvResultCode);

    if (contextID != null)
    {
      buffer.append(", contextID=");
      buffer.append(contextID);
    }

    buffer.append(")");
  }
}

