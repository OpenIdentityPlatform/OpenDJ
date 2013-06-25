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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import static org.opends.messages.ProtocolMessages.ERR_ECLN_CANNOT_DECODE_VALUE;
import static org.opends.messages.ProtocolMessages.ERR_ECLN_NO_CONTROL_VALUE;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;



/**
 * This class implements the ECL cookie control.
 * It may be included in entries returned in response to a search or
 * persistent search operation.
 */
public class EntryChangelogNotificationControl
       extends Control
       {
  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();

  // The cookie value - payload of this control.
  private String cookie;

  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
  implements ControlDecoder<EntryChangelogNotificationControl>
  {
    /**
     * {@inheritDoc}
     */
    public EntryChangelogNotificationControl decode(
        boolean isCritical, ByteString value)
    throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_ECLN_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      String cookie = null;
      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        cookie = reader.readOctetStringAsString();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
          ERR_ECLN_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }
      return new EntryChangelogNotificationControl(isCritical, cookie);
    }

    public String getOID()
    {
      return OID_ECL_COOKIE_EXCHANGE_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<EntryChangelogNotificationControl>
  DECODER = new Decoder();

  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param  cookie      The provided cookie value.
   */
  public EntryChangelogNotificationControl(boolean isCritical,
      String cookie)
  {
    super(OID_ECL_COOKIE_EXCHANGE_CONTROL, isCritical);
    this.cookie = cookie;
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any)
   * must be written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeStartSequence();
    writer.writeOctetString(cookie.toString());
    writer.writeEndSequence();
    writer.writeEndSequence();
  }



  /**
   * Retrieves the change type for this entry change notification control.
   *
   * @return  The change type for this entry change notification control.
   */
  public String getCookie()
  {
    return cookie;
  }

  /**
   * Appends a string representation of this entry change notification control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("EntryChangelogNotificationControl(cookie=");
    buffer.append(cookie.toString());
    buffer.append(")");
  }
}

