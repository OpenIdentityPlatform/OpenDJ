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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.controls;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.replication.common.MultiDomainServerState;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * This class implements the control used to browse the external changelog.
 */
public class ExternalChangelogRequestControl
       extends Control
{
  private MultiDomainServerState cookie;

  /**
   * ControlDecoder implementation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<ExternalChangelogRequestControl>
  {
    /** {@inheritDoc} */
    public ExternalChangelogRequestControl decode(boolean isCritical,
        ByteString value)
    throws DirectoryException
    {
      MultiDomainServerState mdss;
      if (value == null)
      {
        mdss = new MultiDomainServerState();
      } else {

      ASN1Reader reader = ASN1.getReader(value);
      String mdssValue = null;
      try
      {
        mdssValue = reader.readOctetStringAsString();
        mdss = new MultiDomainServerState(mdssValue);
      }
      catch (Exception e)
      {
        try
        {
          mdssValue = value.toString();
          mdss = new MultiDomainServerState(mdssValue);
        }
        catch (Exception e2)
        {
          LocalizableMessage message =
            ERR_CANNOT_DECODE_CONTROL_VALUE.get(
                getOID() + " x=" + value.toHexString() + " v="
                + mdssValue , getExceptionMessage(e));
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
        }
      }
      }
      return new ExternalChangelogRequestControl(isCritical, mdss);
    }

    public String getOID()
    {
      return OID_ECL_COOKIE_EXCHANGE_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ExternalChangelogRequestControl> DECODER =
    new Decoder();

  /**
   * Create a new external change log request control to contain the cookie.
   * @param isCritical Specifies whether the control is critical.
   * @param cookie Specifies the cookie value.
   */
  public ExternalChangelogRequestControl(boolean isCritical,
      MultiDomainServerState cookie)
  {
    super(OID_ECL_COOKIE_EXCHANGE_CONTROL, isCritical);
    this.cookie = cookie;
  }

  /**
   * Returns the cookie value.
   * @return The cookie value.
   */
  public MultiDomainServerState getCookie()
  {
    return this.cookie;
  }

  /**
   * Dump a string representation of this object to the provided bufer.
   * @param buffer The provided buffer.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ExternalChangelogRequestControl(cookie=");
    this.cookie.toString(buffer);
    buffer.append(")");
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value
   * (if any) must be written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the
   *                     stream.
   */
  protected void writeValue(ASN1Writer writer)
      throws IOException
  {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeOctetString(this.cookie.toString());
    writer.writeEndSequence();
  }
}

