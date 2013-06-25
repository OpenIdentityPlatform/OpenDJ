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
import org.opends.messages.Message;

import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements Subentries Control as defined in RFC 3672. It makes
 * it possible to control the visibility of entries and subentries which are
 * within scope of specific operation.
 */
public class SubentriesControl
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<SubentriesControl>
  {
    /**
     * {@inheritDoc}
     */
    public SubentriesControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_SUBENTRIES_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      boolean visibility;
      try
      {
        visibility = reader.readBoolean();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_SUBENTRIES_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      return new SubentriesControl(isCritical, visibility);
    }

    public String getOID()
    {
      return OID_LDAP_SUBENTRIES;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<SubentriesControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The visibility from the control value.
  private boolean visibility = false;

  /**
   * Creates a new instance of the Subentries Control with the provided
   * information.
   *
   * @param  isCritical       Indicates whether support for this control
   *                          should be considered a critical part of the
   *                          server processing.
   * @param  visibility       The visibility flag from the control value.
   */
  public SubentriesControl(boolean isCritical, boolean visibility)
  {
    super(OID_LDAP_SUBENTRIES, isCritical);
    this.visibility = visibility;
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeBoolean(visibility);
    writer.writeEndSequence();
  }

  /**
   * Retrieves the visibility for this Subentries Control.
   *
   * @return  The visibility for this Subentries Control.
   */
  public boolean getVisibility()
  {
    return visibility;
  }

  /**
   * Appends a string representation of this Subentries Control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SubentriesControl(visibility=\"");
    buffer.append(Boolean.toString(visibility));
    buffer.append("\")");
  }
}
