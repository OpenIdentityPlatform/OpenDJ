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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
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
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;

/** This class implements the control used to browse the external changelog. */
public class ExternalChangelogRequestControl
       extends Control
{
  private MultiDomainServerState cookie;

  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<ExternalChangelogRequestControl>
  {
    @Override
    public ExternalChangelogRequestControl decode(boolean isCritical, ByteString value) throws DirectoryException
    {
      return new ExternalChangelogRequestControl(isCritical, decodeCookie(value));
    }

    private MultiDomainServerState decodeCookie(ByteString value) throws DirectoryException
    {
      if (value == null)
      {
        return new MultiDomainServerState();
      }

      ASN1Reader reader = ASN1.getReader(value);
      String mdssValue = null;
      try
      {
        mdssValue = reader.readOctetStringAsString();
        return new MultiDomainServerState(mdssValue);
      }
      catch (Exception e)
      {
        try
        {
          mdssValue = value.toString();
          return new MultiDomainServerState(mdssValue);
        }
        catch (Exception e2)
        {
          LocalizableMessage message = ERR_CANNOT_DECODE_CONTROL_VALUE.get(
              getOID() + " x=" + value.toHexString() + " v=" + mdssValue, getExceptionMessage(e));
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
        }
      }
    }

    @Override
    public String getOID()
    {
      return OID_ECL_COOKIE_EXCHANGE_CONTROL;
    }
  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<ExternalChangelogRequestControl> DECODER = new Decoder();

  /**
   * Create a new external change log request control to contain the cookie.
   * @param isCritical Specifies whether the control is critical.
   * @param cookie Specifies the cookie value.
   */
  public ExternalChangelogRequestControl(boolean isCritical, MultiDomainServerState cookie)
  {
    super(OID_ECL_COOKIE_EXCHANGE_CONTROL, isCritical);
    this.cookie = cookie;
  }

  /**
   * Returns a copy of the cookie value.
   *
   * @return a copy of the cookie value
   */
  public MultiDomainServerState getCookie()
  {
    return new MultiDomainServerState(cookie);
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ExternalChangelogRequestControl(cookie=");
    this.cookie.toString(buffer);
    buffer.append(")");
  }

  @Override
  protected void writeValue(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeOctetString(this.cookie.toString());
    writer.writeEndSequence();
  }
}
