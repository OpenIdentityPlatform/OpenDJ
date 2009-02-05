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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import org.opends.messages.Message;


import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the virtual list view request controls as defined in
 * draft-ietf-ldapext-ldapv3-vlv.  The ASN.1 description for the control value
 * is:
 * <BR><BR>
 * <PRE>
 * VirtualListViewRequest ::= SEQUENCE {
 *       beforeCount    INTEGER (0..maxInt),
 *       afterCount     INTEGER (0..maxInt),
 *       target       CHOICE {
 *                      byOffset        [0] SEQUENCE {
 *                           offset          INTEGER (1 .. maxInt),
 *                           contentCount    INTEGER (0 .. maxInt) },
 *                      greaterThanOrEqual [1] AssertionValue },
 *       contextID     OCTET STRING OPTIONAL }
 * </PRE>
 */
public class VLVRequestControl
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<VLVRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public VLVRequestControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = INFO_VLVREQ_CONTROL_NO_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();

        int beforeCount = (int)reader.readInteger();
        int afterCount  = (int)reader.readInteger();

        int offset = 0;
        int contentCount = 0;
        ByteString greaterThanOrEqual = null;
        byte targetType = reader.peekType();
        switch (targetType)
        {
          case TYPE_TARGET_BYOFFSET:
            reader.readStartSequence();
            offset = (int)reader.readInteger();
            contentCount = (int)reader.readInteger();
            reader.readEndSequence();
            break;

          case TYPE_TARGET_GREATERTHANOREQUAL:
            greaterThanOrEqual = reader.readOctetString();
            break;

          default:
            Message message = INFO_VLVREQ_CONTROL_INVALID_TARGET_TYPE.get(
                byteToHex(targetType));
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
        }

        ByteString contextID = null;
        if (reader.hasNextElement())
        {
          contextID = reader.readOctetString();
        }

        if(targetType == TYPE_TARGET_BYOFFSET)
        {
          return new VLVRequestControl(isCritical, beforeCount,
              afterCount, offset, contentCount, contextID);
        }

        return new VLVRequestControl(isCritical, beforeCount,
            afterCount, greaterThanOrEqual, contextID);
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        Message message =
            INFO_VLVREQ_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }
    }

    public String getOID()
    {
      return OID_VLV_REQUEST_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<VLVRequestControl> DECODER =
    new Decoder();

  /**
   * The BER type to use when encoding the byOffset target element.
   */
  public static final byte TYPE_TARGET_BYOFFSET = (byte) 0xA0;



  /**
   * The BER type to use when encoding the greaterThanOrEqual target element.
   */
  public static final byte TYPE_TARGET_GREATERTHANOREQUAL = (byte) 0x81;



  // The target type for this VLV request control.
  private byte targetType;

  // The context ID for this VLV request control.
  private ByteString contextID;

  // The greaterThanOrEqual target assertion value for this VLV request control.
  private ByteString greaterThanOrEqual;

  // The after count for this VLV request control.
  private int afterCount;

  // The before count for this VLV request control.
  private int beforeCount;

  // The content count for the byOffset target of this VLV request control.
  private int contentCount;

  // The offset for the byOffset target of this VLV request control.
  private int offset;



  /**
   * Creates a new VLV request control with the provided information.
   *
   * @param  beforeCount   The number of entries before the target offset to
   *                       retrieve in the results page.
   * @param  afterCount    The number of entries after the target offset to
   *                       retrieve in the results page.
   * @param  offset        The offset in the result set to target for the
   *                       beginning of the page of results.
   * @param  contentCount  The content count returned by the server in the last
   *                       phase of the VLV request, or zero for a new VLV
   *                       request session.
   */
  public VLVRequestControl(int beforeCount, int afterCount, int offset,
                           int contentCount)
  {
    this(false, beforeCount, afterCount, offset, contentCount, null);
  }



  /**
   * Creates a new VLV request control with the provided information.
   *
   * @param  isCritical    Indicates whether or not the control is critical.
   * @param  beforeCount   The number of entries before the target offset to
   *                       retrieve in the results page.
   * @param  afterCount    The number of entries after the target offset to
   *                       retrieve in the results page.
   * @param  offset        The offset in the result set to target for the
   *                       beginning of the page of results.
   * @param  contentCount  The content count returned by the server in the last
   *                       phase of the VLV request, or zero for a new VLV
   *                       request session.
   * @param  contextID     The context ID provided by the server in the last
   *                       VLV response for the same set of criteria, or
   *                       {@code null} if there was no previous VLV response or
   *                       the server did not include a context ID in the
   *                       last response.
   */
  public VLVRequestControl(boolean isCritical, int beforeCount, int afterCount,
                           int offset, int contentCount, ByteString contextID)
  {
    super(OID_VLV_REQUEST_CONTROL, isCritical);

    this.beforeCount  = beforeCount;
    this.afterCount   = afterCount;
    this.offset       = offset;
    this.contentCount = contentCount;
    this.contextID    = contextID;

    targetType = TYPE_TARGET_BYOFFSET;
  }



  /**
   * Creates a new VLV request control with the provided information.
   *
   * @param  beforeCount         The number of entries before the target offset
   *                             to retrieve in the results page.
   * @param  afterCount          The number of entries after the target offset
   *                             to retrieve in the results page.
   * @param  greaterThanOrEqual  The greaterThanOrEqual target assertion value
   *                             that indicates where to start the page of
   *                             results.
   */
  public VLVRequestControl(int beforeCount, int afterCount,
                           ByteString greaterThanOrEqual)
  {
    this(false, beforeCount, afterCount, greaterThanOrEqual, null);
  }



  /**
   * Creates a new VLV request control with the provided information.
   *
   * @param  isCritical          Indicates whether the control should be
   *                             considered critical.
   * @param  beforeCount         The number of entries before the target
   *                             assertion value.
   * @param  afterCount          The number of entries after the target
   *                             assertion value.
   * @param  greaterThanOrEqual  The greaterThanOrEqual target assertion value
   *                             that indicates where to start the page of
   *                             results.
   * @param  contextID           The context ID provided by the server in the
   *                             last VLV response for the same set of criteria,
   *                             or {@code null} if there was no previous VLV
   *                             response or the server did not include a
   *                             context ID in the last response.
   */
  public VLVRequestControl(boolean isCritical, int beforeCount, int afterCount,
                           ByteString greaterThanOrEqual,
                           ByteString contextID)
  {
    super(OID_VLV_REQUEST_CONTROL, isCritical);

    this.beforeCount        = beforeCount;
    this.afterCount         = afterCount;
    this.greaterThanOrEqual = greaterThanOrEqual;
    this.contextID          = contextID;

    targetType = TYPE_TARGET_GREATERTHANOREQUAL;
  }



  /**
   * Retrieves the number of entries before the target offset or assertion value
   * to include in the results page.
   *
   * @return  The number of entries before the target offset to include in the
   *          results page.
   */
  public int getBeforeCount()
  {
    return beforeCount;
  }



  /**
   * Retrieves the number of entries after the target offset or assertion value
   * to include in the results page.
   *
   * @return  The number of entries after the target offset to include in the
   *          results page.
   */
  public int getAfterCount()
  {
    return afterCount;
  }



  /**
   * Retrieves the BER type for the target that specifies the beginning of the
   * results page.
   *
   * @return  {@code TYPE_TARGET_BYOFFSET} if the beginning of the results page
   *          should be specified as a nuemric offset, or
   *          {@code TYPE_TARGET_GREATERTHANOREQUAL} if it should be specified
   *          by an assertion value.
   */
  public byte getTargetType()
  {
    return targetType;
  }



  /**
   * Retrieves the offset that indicates the beginning of the results page.  The
   * return value will only be applicable if the {@code getTargetType} method
   * returns {@code TYPE_TARGET_BYOFFSET}.
   *
   * @return  The offset that indicates the beginning of the results page.
   */
  public int getOffset()
  {
    return offset;
  }



  /**
   * Retrieves the content count indicating the estimated number of entries in
   * the complete result set.  The return value will only be applicable if the
   * {@code getTargetType} method returns {@code TYPE_TARGET_BYOFFSET}.
   *
   * @return  The content count indicating the estimated number of entries in
   *          the complete result set.
   */
  public int getContentCount()
  {
    return contentCount;
  }



  /**
   * Retrieves the assertion value that will be used to locate the beginning of
   * the results page.  This will only be applicable if the
   * {@code getTargetType} method returns
   * {@code TYPE_TARGET_GREATERTHANOREQUAL}.
   *
   * @return  The assertion value that will be used to locate the beginning of
   *          the results page, or {@code null} if the beginning of the results
   *          page is to be specified using an offset.
   */
  public ByteString getGreaterThanOrEqualAssertion()
  {
    return greaterThanOrEqual;
  }



  /**
   * Retrieves a context ID value that should be used to resume a previous VLV
   * results session.
   *
   * @return  A context ID value that should be used to resume a previous VLV
   *          results session, or {@code null} if none is available.
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
    writer.writeInteger(beforeCount);
    writer.writeInteger(afterCount);
    if(targetType == TYPE_TARGET_BYOFFSET)
    {
      writer.writeStartSequence(TYPE_TARGET_BYOFFSET);
      writer.writeInteger(offset);
      writer.writeInteger(contentCount);
      writer.writeEndSequence();
    }
    else
    {
      writer.writeOctetString(TYPE_TARGET_GREATERTHANOREQUAL,
          greaterThanOrEqual);
    }
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
    buffer.append("VLVRequestControl(beforeCount=");
    buffer.append(beforeCount);
    buffer.append(", afterCount=");
    buffer.append(afterCount);

    if (targetType == TYPE_TARGET_BYOFFSET)
    {
      buffer.append(", offset=");
      buffer.append(offset);
      buffer.append(", contentCount=");
      buffer.append(contentCount);
    }
    else
    {
      buffer.append(", greaterThanOrEqual=");
      buffer.append(greaterThanOrEqual);
    }

    if (contextID != null)
    {
      buffer.append(", contextID=");
      buffer.append(contextID);
    }

    buffer.append(")");
  }
}

