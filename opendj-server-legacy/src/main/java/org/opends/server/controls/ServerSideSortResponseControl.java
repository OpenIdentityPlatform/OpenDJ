/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.controls;
import org.forgerock.i18n.LocalizableMessage;


import java.io.IOException;

import org.forgerock.opendj.io.*;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.ProtocolMessages.*;
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
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<ServerSideSortResponseControl>
  {
    @Override
    public ServerSideSortResponseControl decode(boolean isCritical,
                                                ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = INFO_SORTRES_CONTROL_NO_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        int resultCode = (int)reader.readInteger();

        String attributeType = null;
        if(reader.hasNextElement())
        {
          attributeType = reader.readOctetStringAsString();
        }

        return new ServerSideSortResponseControl(isCritical,
            resultCode,
            attributeType);
      }
      catch (Exception e)
      {
        LocalizableMessage message =
            INFO_SORTRES_CONTROL_CANNOT_DECODE_VALUE.get(
                getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }
    }

    @Override
    public String getOID()
    {
      return OID_SERVER_SIDE_SORT_RESPONSE_CONTROL;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<ServerSideSortResponseControl> DECODER =
    new Decoder();

  /** The BER type to use when encoding the attribute type element. */
  private static final byte TYPE_ATTRIBUTE_TYPE = (byte) 0x80;



  /** The result code for the sort result. */
  private int resultCode;

  /** The attribute type for the sort result. */
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
    this(false, resultCode, attributeType);
  }



  /**
   * Creates a new server-side sort response control with the provided
   * information.
   *
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  resultCode     The result code for the sort result.
   * @param  attributeType  The attribute type for the sort result.
   */
  public ServerSideSortResponseControl(boolean isCritical,
                                       int resultCode,
                                       String attributeType)
  {
    super(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL, isCritical);

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
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    writer.writeEnumerated(resultCode);
    if (attributeType != null)
    {
      writer.writeOctetString(TYPE_ATTRIBUTE_TYPE, attributeType);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }



  /**
   * Appends a string representation of this server-side sort response control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
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

