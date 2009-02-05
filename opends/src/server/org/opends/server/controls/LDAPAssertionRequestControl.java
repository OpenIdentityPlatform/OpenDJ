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


import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.*;

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
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<LDAPAssertionRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public LDAPAssertionRequestControl decode(boolean isCritical,
                                              ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_LDAPASSERT_NO_CONTROL_VALUE.get();
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



  // The unparsed LDAP search filter contained in the request from the client.
  private LDAPFilter rawFilter;

  // The processed search filter
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
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);
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

