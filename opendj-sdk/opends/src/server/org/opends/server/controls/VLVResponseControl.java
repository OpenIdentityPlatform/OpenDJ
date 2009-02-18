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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import org.opends.messages.Message;


import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.*;

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
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<VLVResponseControl>
  {
    /**
     * {@inheritDoc}
     */
    public VLVResponseControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = INFO_VLVRES_CONTROL_NO_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();

        int targetPosition = (int)reader.readInteger();
        int contentCount   = (int)reader.readInteger();
        int vlvResultCode  = (int)reader.readInteger();

        ByteString contextID = null;
        if (reader.hasNextElement())
        {
          contextID = reader.readOctetString();
        }

        return new VLVResponseControl(isCritical, targetPosition,
            contentCount, vlvResultCode, contextID);
      }
      catch (Exception e)
      {
        Message message =
            INFO_VLVRES_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }
    }

    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return OID_VLV_RESPONSE_CONTROL;
    }
  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<VLVResponseControl> DECODER =
    new Decoder();

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
    this(false, targetPosition, contentCount, vlvResultCode, null);
  }



  /**
   * Creates a new VLV response control with the provided information.
   *
   * @param  isCritical      Indicates whether the control should be considered
   *                         critical.
   * @param  targetPosition  The position of the target entry in the result set.
   * @param  contentCount    The content count estimating the total number of
   *                         entries in the result set.
   * @param  vlvResultCode   The result code for the VLV operation.
   * @param  contextID       The context ID for this VLV response control.
   */
  public VLVResponseControl(boolean isCritical, int targetPosition,
                             int contentCount, int vlvResultCode,
                             ByteString contextID)
  {
    super(OID_VLV_RESPONSE_CONTROL, isCritical);

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
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    writer.writeInteger(targetPosition);
    writer.writeInteger(contentCount);
    writer.writeEnumerated(vlvResultCode);
    if (contextID != null)
    {
      writer.writeOctetString(contextID);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }



  /**
   * Appends a string representation of this VLV request control to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
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

