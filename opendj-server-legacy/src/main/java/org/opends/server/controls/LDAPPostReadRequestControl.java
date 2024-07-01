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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.controls;



import org.forgerock.i18n.LocalizableMessage;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.io.IOException;

import org.forgerock.opendj.io.*;

import static org.opends.server.plugins.LDAPADListPlugin.*;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the post-read request control as defined in RFC 4527.
 * This control makes it possible to retrieve an entry in the state that it held
 * immediately after an add, modify, or modify DN operation. It may specify a
 * specific set of attributes that should be included in that entry. The entry
 * will be encoded in a corresponding response control.
 */
public class LDAPPostReadRequestControl extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder implements
      ControlDecoder<LDAPPostReadRequestControl>
  {
    @Override
    public LDAPPostReadRequestControl decode(boolean isCritical,
        ByteString value) throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_POSTREADREQ_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      LinkedHashSet<String> rawAttributes = new LinkedHashSet<>();
      try
      {
        reader.readStartSequence();
        while (reader.hasNextElement())
        {
          rawAttributes.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
      catch (Exception ae)
      {
        logger.traceException(ae);

        LocalizableMessage message = ERR_POSTREADREQ_CANNOT_DECODE_VALUE.get(ae
            .getMessage());
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, ae);
      }

      return new LDAPPostReadRequestControl(isCritical, rawAttributes);
    }



    @Override
    public String getOID()
    {
      return OID_LDAP_READENTRY_POSTREAD;
    }

  }



  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<LDAPPostReadRequestControl> DECODER =
      new Decoder();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of raw attributes to return in the entry. */
  private Set<String> rawAttributes;

  /** The set of processed attributes to return in the entry. */
  private Set<String> requestedAttributes;



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param isCritical
   *          Indicates whether support for this control should be considered a
   *          critical part of the server processing.
   * @param rawAttributes
   *          The set of raw attributes to return in the entry. A null or empty
   *          set will indicates that all user attributes should be returned.
   */
  public LDAPPostReadRequestControl(boolean isCritical,
      Set<String> rawAttributes)
  {
    super(OID_LDAP_READENTRY_POSTREAD, isCritical);
    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }
    requestedAttributes = null;
  }



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param oid
   *          The OID to use for this control.
   * @param isCritical
   *          Indicates whether support for this control should be considered a
   *          critical part of the server processing.
   * @param rawAttributes
   *          The set of raw attributes to return in the entry. A null or empty
   *          set will indicates that all user attributes should be returned.
   */
  public LDAPPostReadRequestControl(String oid, boolean isCritical,
      Set<String> rawAttributes)
  {
    super(oid, isCritical);
    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }
    requestedAttributes = null;
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer
   *          The ASN.1 output stream to write to.
   * @throws IOException
   *           If a problem occurs while writing to the stream.
   */
  @Override
  public void writeValue(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
    {
      writer.writeStartSequence();
      if (rawAttributes != null)
      {
        for (String attr : rawAttributes)
        {
          writer.writeOctetString(attr);
        }
      }
      writer.writeEndSequence();
    }
    writer.writeEndSequence();
  }



  /**
   * Retrieves the raw, unprocessed set of requested attributes. It must not be
   * altered by the caller without calling <CODE>setRawAttributes</CODE> with
   * the updated set.
   *
   * @return The raw, unprocessed set of attributes.
   */
  public Set<String> getRawAttributes()
  {
    return rawAttributes;
  }



  /**
   * Retrieves the set of processed attributes that have been requested for
   * inclusion in the entry that is returned.
   *
   * @return The set of processed attributes that have been requested for
   *         inclusion in the entry that is returned.
   */
  public Set<String> getRequestedAttributes()
  {
    if (requestedAttributes == null)
    {
      requestedAttributes = normalizedObjectClasses(rawAttributes);
    }
    return requestedAttributes;
  }



  /**
   * Appends a string representation of this LDAP post-read request control to
   * the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPPostReadRequestControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",attrs=\"");

    if (!rawAttributes.isEmpty())
    {
      Iterator<String> iterator = rawAttributes.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }
    }

    buffer.append("\")");
  }
}
