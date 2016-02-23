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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.controls;
import org.forgerock.i18n.LocalizableMessage;


import org.forgerock.opendj.io.*;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;


/**
 * This class implements the LDAP assertion request control as defined in RFC
 * 4528.  This control makes it possible to conditionally perform an operation
 * if a given assertion is true.  In particular, the associated operation should
 * only be processed if the target entry matches the filter contained in this
 * control.
 */
public class LDAPAssertionRequestControl
    extends Control
{
  /**
   * ControlDecoder implementation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<LDAPAssertionRequestControl>
  {
    /** {@inheritDoc} */
    public LDAPAssertionRequestControl decode(boolean isCritical,
                                              ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_LDAPASSERT_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(reader);
      }
      catch (LDAPException e)
      {
        throw new DirectoryException(ResultCode.valueOf(e.getResultCode()), e
            .getMessageObject(), e.getCause());
      }

      return new LDAPAssertionRequestControl(isCritical, filter);
    }

    public String getOID()
    {
      return OID_LDAP_ASSERTION;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<LDAPAssertionRequestControl> DECODER =
    new Decoder();



  /** The unparsed LDAP search filter contained in the request from the client. */
  private LDAPFilter rawFilter;

  /** The processed search filter. */
  private SearchFilter filter;



  /**
   * Creates a new instance of this LDAP assertion request control with the
   * provided information.
   *
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the server processing.
   * @param  rawFilter   The unparsed LDAP search filter contained in the
   *                     request from the client.
   */
  public LDAPAssertionRequestControl(boolean isCritical, LDAPFilter rawFilter)
  {
    super(OID_LDAP_ASSERTION, isCritical);


    this.rawFilter = rawFilter;

    filter = null;
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
    rawFilter.write(writer);
    writer.writeEndSequence();
  }



  /**
   * Retrieves the raw, unparsed filter from the request control.
   *
   * @return  The raw, unparsed filter from the request control.
   */
  public LDAPFilter getRawFilter()
  {
    return rawFilter;
  }


  /**
   * Retrieves the processed search filter for this control.
   *
   * @return  The processed search filter for this control.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              process the search filter.
   */
  public SearchFilter getSearchFilter()
         throws DirectoryException
  {
    if (filter == null)
    {
      filter = rawFilter.toSearchFilter();
    }

    return filter;
  }



  /**
   * Appends a string representation of this LDAP assertion request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPAssertionRequestControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",filter=\"");
    rawFilter.toString(buffer);
    buffer.append("\")");
  }
}

