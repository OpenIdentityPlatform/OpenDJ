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



import java.util.ArrayList;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
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
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<MatchedValuesControl>
  {
    /**
     * {@inheritDoc}
     */
    public MatchedValuesControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      ArrayList<MatchedValuesFilter> filters;
      if (value == null)
      {
        Message message = ERR_MATCHEDVALUES_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        if (!reader.hasNextElement())
        {
          Message message = ERR_MATCHEDVALUES_NO_FILTERS.get();
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
        }

        filters = new ArrayList<MatchedValuesFilter>();
        while(reader.hasNextElement())
        {
          filters.add(MatchedValuesFilter.decode(reader));
        }
        reader.readEndSequence();
      }
      catch (DirectoryException e)
      {
        throw e;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_MATCHEDVALUES_CANNOT_DECODE_VALUE_AS_SEQUENCE.get(
            getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      return new MatchedValuesControl(isCritical,filters);
    }


    public String getOID()
    {
      return OID_MATCHED_VALUES;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<MatchedValuesControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The set of matched values filters for this control.
  private final ArrayList<MatchedValuesFilter> filters;



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
    super(OID_MATCHED_VALUES, isCritical);


    this.filters = filters;
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    for (MatchedValuesFilter f : filters)
    {
      f.encode(writer);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    return false;
  }



  /**
   * Appends a string representation of this authorization identity response
   * control to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
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

