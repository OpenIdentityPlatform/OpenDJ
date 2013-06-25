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

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.OID_PAGED_RESULTS_CONTROL;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.*;

import java.io.IOException;

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
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<PagedResultsControl>
  {
    /**
     * {@inheritDoc}
     */
    public PagedResultsControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_LDAP_PAGED_RESULTS_DECODE_NULL.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(String.valueOf(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      int size;
      try
      {
        size = (int)reader.readInteger();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_LDAP_PAGED_RESULTS_DECODE_SIZE.get(String.valueOf(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      ByteString cookie;
      try
      {
        cookie = reader.readOctetString();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_LDAP_PAGED_RESULTS_DECODE_COOKIE.get(String.valueOf(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      try
      {
        reader.readEndSequence();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(String.valueOf(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      return new PagedResultsControl(isCritical, size, cookie);
    }

    public String getOID()
    {
      return OID_PAGED_RESULTS_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final  ControlDecoder<PagedResultsControl> DECODER =
    new Decoder();

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
  private ByteString cookie;


  /**
   * Creates a new paged results control with the specified information.
   *
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical in processing the request.
   * @param  size        The size element.
   * @param  cookie      The cookie element.
   */
  public PagedResultsControl(boolean isCritical, int size,
                             ByteString cookie)
  {
    super(OID_PAGED_RESULTS_CONTROL, isCritical);


    this.size   = size;
    if(cookie == null)
        this.cookie=ByteString.empty();
    else
        this.cookie = cookie;
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
    writer.writeInteger(size);
    writer.writeOctetString(cookie);
    writer.writeEndSequence();

    writer.writeEndSequence();
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
  public ByteString getCookie()
  {
    return cookie;
  }
}
