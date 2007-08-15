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
    this(beforeCount, afterCount, offset, contentCount, null);
  }



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
   * @param  contextID     The context ID provided by the server in the last
   *                       VLV response for the same set of criteria, or
   *                       {@code null} if there was no previous VLV response or
   *                       the server did not include a context ID in the
   *                       last response.
   */
  public VLVRequestControl(int beforeCount, int afterCount, int offset,
                           int contentCount, ByteString contextID)
  {
    super(OID_VLV_REQUEST_CONTROL, false,
          encodeControlValue(beforeCount, afterCount, offset, contentCount,
                             contextID));

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
    this(beforeCount, afterCount, greaterThanOrEqual, null);
  }



  /**
   * Creates a new VLV request control with the provided information.
   *
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
  public VLVRequestControl(int beforeCount, int afterCount,
                           ByteString greaterThanOrEqual,
                           ByteString contextID)
  {
    super(OID_VLV_REQUEST_CONTROL, false,
          encodeControlValue(beforeCount, afterCount, greaterThanOrEqual,
                             contextID));

    this.beforeCount        = beforeCount;
    this.afterCount         = afterCount;
    this.greaterThanOrEqual = greaterThanOrEqual;
    this.contextID          = contextID;

    targetType = TYPE_TARGET_GREATERTHANOREQUAL;
  }



  /**
   * Creates a new VLV request control with the provided information.
   *
   * @param  oid                 The OID for the control.
   * @param  isCritical          Indicates whether the control should be
   *                             considered critical.
   * @param  controlValue        The pre-encoded value for the control.
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
  private VLVRequestControl(String oid, boolean isCritical,
                            ASN1OctetString controlValue, int beforeCount,
                            int afterCount, byte targetType,
                            int offset, int contentCount,
                            ByteString greaterThanOrEqual,
                            ByteString contextID)
  {
    super(oid, isCritical, controlValue);

    this.beforeCount        = beforeCount;
    this.afterCount         = afterCount;
    this.targetType         = targetType;
    this.offset             = offset;
    this.contentCount       = contentCount;
    this.greaterThanOrEqual = greaterThanOrEqual;
    this.contextID          = contextID;
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
   * Encodes the provided information in a manner suitable for use as the value
   * of this control.
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
   * @param  contextID     The context ID provided by the server in the last
   *                       VLV response for the same set of criteria, or
   *                       {@code null} if there was no previous VLV response or
   *                       the server did not include a context ID in the
   *                       last response.
   *
   * @return  The ASN.1 octet string containing the encoded sort order.
   */
  private static ASN1OctetString encodeControlValue(int beforeCount,
                                      int afterCount, int offset,
                                      int contentCount, ByteString contextID)
  {
    ArrayList<ASN1Element> vlvElements = new ArrayList<ASN1Element>(4);
    vlvElements.add(new ASN1Integer(beforeCount));
    vlvElements.add(new ASN1Integer(afterCount));

    ArrayList<ASN1Element> targetElements = new ArrayList<ASN1Element>(2);
    targetElements.add(new ASN1Integer(offset));
    targetElements.add(new ASN1Integer(contentCount));
    vlvElements.add(new ASN1Sequence(TYPE_TARGET_BYOFFSET, targetElements));

    if (contextID != null)
    {
      vlvElements.add(contextID.toASN1OctetString());
    }

    return new ASN1OctetString(new ASN1Sequence(vlvElements).encode());
  }



  /**
   * Encodes the provided information in a manner suitable for use as the value
   * of this control.
   *
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
   *
   * @return  The ASN.1 octet string containing the encoded sort order.
   */
  private static ASN1OctetString encodeControlValue(int beforeCount,
                                      int afterCount,
                                      ByteString greaterThanOrEqual,
                                      ByteString contextID)
  {
    ArrayList<ASN1Element> vlvElements = new ArrayList<ASN1Element>(4);
    vlvElements.add(new ASN1Integer(beforeCount));
    vlvElements.add(new ASN1Integer(afterCount));

    vlvElements.add(new ASN1OctetString(TYPE_TARGET_GREATERTHANOREQUAL,
                                        greaterThanOrEqual.value()));

    if (contextID != null)
    {
      vlvElements.add(contextID.toASN1OctetString());
    }

    return new ASN1OctetString(new ASN1Sequence(vlvElements).encode());
  }



  /**
   * Creates a new VLV request control from the contents of the provided
   * control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this VLV request control.  It must not be
   *                  {@code null}.
   *
   * @return  The VLV request control decoded from the provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid VLV
   *                         request control.
   */
  public static VLVRequestControl decodeControl(Control control)
         throws LDAPException
  {
    ASN1OctetString controlValue = control.getValue();
    if (controlValue == null)
    {
      Message message = INFO_VLVREQ_CONTROL_NO_VALUE.get();
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
            INFO_VLVREQ_CONTROL_INVALID_ELEMENT_COUNT.get(elements.size());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
      }

      int beforeCount = elements.get(0).decodeAsInteger().intValue();
      int afterCount  = elements.get(1).decodeAsInteger().intValue();

      ASN1Element targetElement = elements.get(2);
      int offset = 0;
      int contentCount = 0;
      ASN1OctetString greaterThanOrEqual = null;
      byte targetType = targetElement.getType();
      switch (targetType)
      {
        case TYPE_TARGET_BYOFFSET:
          ArrayList<ASN1Element> targetElements =
               targetElement.decodeAsSequence().elements();
          offset = targetElements.get(0).decodeAsInteger().intValue();
          contentCount = targetElements.get(1).decodeAsInteger().intValue();
          break;

        case TYPE_TARGET_GREATERTHANOREQUAL:
          greaterThanOrEqual = targetElement.decodeAsOctetString();
          break;

        default:
          Message message = INFO_VLVREQ_CONTROL_INVALID_TARGET_TYPE.get(
              byteToHex(targetType));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
      }

      ASN1OctetString contextID = null;
      if (elements.size() == 4)
      {
        contextID = elements.get(3).decodeAsOctetString();
      }

      return new VLVRequestControl(control.getOID(), control.isCritical(),
                                   controlValue, beforeCount, afterCount,
                                   targetType, offset, contentCount,
                                   greaterThanOrEqual, contextID);
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      Message message =
          INFO_VLVREQ_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
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

