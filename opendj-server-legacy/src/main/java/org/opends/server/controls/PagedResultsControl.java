/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.controls;

import org.forgerock.i18n.LocalizableMessage;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.OID_PAGED_RESULTS_CONTROL;

import org.forgerock.opendj.io.*;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import java.io.IOException;

/**
 * This class represents a paged results control value as defined in
 * RFC 2696.
 *
 * The searchControlValue is an OCTET STRING wrapping the BER-encoded
 * version of the following SEQUENCE:
 *
 * <pre>
 * realSearchControlValue ::= SEQUENCE {
 *         size            INTEGER (0..maxInt),
 *                                 -- requested page size from client
 *                                 -- result set size estimate from server
 *         cookie          OCTET STRING
 * }
 * </pre>
 */
public class PagedResultsControl extends Control
{
  /**
   * ControlDecoder implementation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<PagedResultsControl>
  {
    /** {@inheritDoc} */
    public PagedResultsControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_LDAP_PAGED_RESULTS_DECODE_NULL.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(e);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      int size;
      try
      {
        size = (int)reader.readInteger();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_LDAP_PAGED_RESULTS_DECODE_SIZE.get(e);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      ByteString cookie;
      try
      {
        cookie = reader.readOctetString();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_LDAP_PAGED_RESULTS_DECODE_COOKIE.get(e);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      try
      {
        reader.readEndSequence();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(e);
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



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
    {
      this.cookie=ByteString.empty();
    }
    else
    {
      this.cookie = cookie;
    }
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
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

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
