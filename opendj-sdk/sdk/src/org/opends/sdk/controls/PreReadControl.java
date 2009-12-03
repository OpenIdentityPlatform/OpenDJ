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

package org.opends.sdk.controls;



import static com.sun.opends.sdk.util.Messages.ERR_PREREADREQ_CANNOT_DECODE_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_PREREADREQ_NO_CONTROL_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_PREREADRESP_CANNOT_DECODE_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_PREREADRESP_NO_CONTROL_VALUE;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.ldap.LDAPUtils;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.ByteStringBuilder;
import org.opends.sdk.util.StaticUtils;
import org.opends.sdk.util.Validator;



/**
 * This class implements the pre-read request control as defined in RFC
 * 4527. This control makes it possible to retrieve an entry in the
 * state that it held immediately before a modify, delete, or modify DN
 * operation. It may specify a specific set of attributes that should be
 * included in that entry.
 */
public class PreReadControl
{
  /**
   * The IANA-assigned OID for the LDAP readentry control used for
   * retrieving an
   * entry in the state it had immediately before an update was applied.
   */
  public static final String OID_LDAP_READENTRY_PREREAD = "1.3.6.1.1.13.1";



  /**
   * This class implements the pre-read request control as defined in
   * RFC 4527. This control makes it possible to retrieve an entry in
   * the state that it held immediately before a modify, delete, or
   * modify DN operation. It may specify a specific set of attributes
   * that should be included in that entry. The entry will be encoded in
   * a corresponding response control.
   */
  public static class Request extends Control
  {
    // The set of raw attributes to return in the entry.
    private final Set<String> attributes;



    /**
     * Creates a new pre-read request control with the provided
     * information.
     *
     * @param isCritical
     *          Indicates whether support for this control should be
     *          considered a critical part of the server processing.
     * @param attributeDescriptions
     *          The names of the attributes to be included with the
     *          response control.
     */
    public Request(boolean isCritical, String... attributeDescriptions)
    {
      super(OID_LDAP_READENTRY_PREREAD, isCritical);

      this.attributes = new LinkedHashSet<String>();
      if (attributeDescriptions != null)
      {
        this.attributes.addAll(Arrays.asList(attributeDescriptions));
      }
    }



    private Request(boolean isCritical, Set<String> attributes)
    {
      super(OID_LDAP_READENTRY_PREREAD, isCritical);

      this.attributes = attributes;
    }



    /**
     * Adds the provided attribute name to the list of attributes to be
     * included in the request control. Attributes that are sub-types
     * of listed attributes are implicitly included.
     *
     * @param attributeDescription
     *          The name of the attribute to be included in the response
     *          control.
     * @return This post-read control.
     * @throws NullPointerException
     *           If {@code attributeDescription} was {@code null}.
     */
    public Request addAttribute(String attributeDescription)
        throws NullPointerException
    {
      Validator.ensureNotNull(attributeDescription);
      attributes.add(attributeDescription);
      return this;
    }



    /**
     * Returns an {@code Iterable} containing the list of attributes to
     * be included with the response control. Attributes that are
     * sub-types of listed attributes are implicitly included.
     *
     * @return An {@code Iterable} containing the list of attributes.
     */
    public Iterable<String> getAttributes()
    {
      return attributes;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        writer.writeStartSequence();
        if (attributes != null)
        {
          for (String attr : attributes)
          {
            writer.writeOctetString(attr);
          }
        }
        writer.writeEndSequence();
        return buffer.toByteString();
      }
      catch (IOException ioe)
      {
        // This should never happen unless there is a bug somewhere.
        throw new RuntimeException(ioe);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasValue()
    {
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("PreReadRequestControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", attributes=");
      buffer.append(attributes);
      buffer.append(")");
    }
  }



  /**
   * This class implements the pre-read response control as defined in
   * RFC 4527. This control holds the search result entry representing
   * the state of the entry immediately before an add, modify, or modify
   * DN operation.
   */
  public static class Response extends Control
  {
    private final SearchResultEntry entry;



    /**
     * Creates a new pre-read response control with the provided
     * information.
     *
     * @param isCritical
     *          Indicates whether support for this control should be
     *          considered a critical part of the server processing.
     * @param searchEntry
     *          The search result entry to include in the response
     *          control.
     */
    public Response(boolean isCritical, SearchResultEntry searchEntry)
    {
      super(OID_LDAP_READENTRY_PREREAD, isCritical);

      this.entry = searchEntry;
    }



    /**
     * Creates a new pre-read response control with the provided
     * information.
     *
     * @param searchEntry
     *          The search result entry to include in the response
     *          control.
     */
    public Response(SearchResultEntry searchEntry)
    {
      this(false, searchEntry);
    }



    /**
     * Returns the search result entry associated with this post-read
     * response control.
     *
     * @return The search result entry associated with this post-read
     *         response control.
     */
    public SearchResultEntry getSearchEntry()
    {
      return entry;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        LDAPUtils.encodeSearchResultEntry(writer, entry);
        return buffer.toByteString();
      }
      catch (IOException ioe)
      {
        // This should never happen unless there is a bug somewhere.
        throw new RuntimeException(ioe);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasValue()
    {
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("PreReadResponseControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", entry=");
      buffer.append(entry);
      buffer.append(")");
    }
  }



  /**
   * Decodes a pre-read request control from a byte string.
   */
  private final static class RequestDecoder implements
      ControlDecoder<Request>
  {
    /**
     * {@inheritDoc}
     */
    public Request decode(boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_PREREADREQ_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      try
      {
        reader.readStartSequence();
        while (reader.hasNextElement())
        {
          attributes.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
      catch (Exception ae)
      {
        StaticUtils.DEBUG_LOG.throwing("PreReadControl.RequestDecoder",
            "decode", ae);

        Message message = ERR_PREREADREQ_CANNOT_DECODE_VALUE.get(ae
            .getMessage());
        throw DecodeException.error(message, ae);
      }

      return new Request(isCritical, attributes);
    }



    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return OID_LDAP_READENTRY_PREREAD;
    }
  }



  /**
   * Decodes a pre-read response control from a byte string.
   */
  private final static class ResponseDecoder implements
      ControlDecoder<Response>
  {
    /**
     * {@inheritDoc}
     */
    public Response decode(boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_PREREADRESP_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      SearchResultEntry searchEntry;
      try
      {
        searchEntry = LDAPUtils.decodeSearchResultEntry(reader, schema);
      }
      catch (IOException le)
      {
        StaticUtils.DEBUG_LOG.throwing(
            "PersistentSearchControl.ResponseDecoder", "decode", le);

        Message message = ERR_PREREADRESP_CANNOT_DECODE_VALUE.get(le
            .getMessage());
        throw DecodeException.error(message, le);
      }

      return new Response(isCritical, searchEntry);
    }



    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return OID_LDAP_READENTRY_PREREAD;
    }

  }



  /**
   * A control decoder which can be used to decode pre-read request
   * controls.
   */
  public static final ControlDecoder<Request> REQUEST_DECODER = new RequestDecoder();

  /**
   * A control decoder which can be used to decode pre-read respoens
   * controls.
   */
  public static final ControlDecoder<Response> RESPONSE_DECODER = new ResponseDecoder();
}
