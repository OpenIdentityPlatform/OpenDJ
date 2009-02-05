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


import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.io.IOException;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the post-read request control as defined in RFC 4527.
 * This control makes it possible to retrieve an entry in the state that it held
 * immediately after an add, modify, or modify DN operation.  It may specify a
 * specific set of attributes that should be included in that entry.  The entry
 * will be encoded in a corresponding response control.
 */
public class LDAPPostReadRequestControl
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<LDAPPostReadRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public LDAPPostReadRequestControl decode(boolean isCritical,
                                             ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_POSTREADREQ_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      ASN1Reader reader = ASN1.getReader(value);
      LinkedHashSet<String> rawAttributes = new LinkedHashSet<String>();
      try
      {
        reader.readStartSequence();
        while(reader.hasNextElement())
        {
          rawAttributes.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
      catch (Exception ae)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ae);
        }

        Message message =
            ERR_POSTREADREQ_CANNOT_DECODE_VALUE.get(ae.getMessage());
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message,
            ae);
      }


      return new LDAPPostReadRequestControl(isCritical,
          rawAttributes);
    }

    public String getOID()
    {
      return OID_LDAP_READENTRY_POSTREAD;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<LDAPPostReadRequestControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // Indicates whether the request indicates that all operational attributes
  // should be returned.
  private boolean returnAllOperationalAttrs;

  // Indicates whether the request indicates that all user attributes should be
  // returned.
  private boolean returnAllUserAttrs;

  // The set of raw attributes to return in the entry.
  private Set<String> rawAttributes;

  // The set of processed attributes to return in the entry.
  private Set<AttributeType> requestedAttributes;



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  rawAttributes  The set of raw attributes to return in the entry.
   *                        A null or empty set will indicates that all user
   *                        attributes should be returned.
   */
  public LDAPPostReadRequestControl(boolean isCritical,
                                    Set<String> rawAttributes)
  {
    super(OID_LDAP_READENTRY_POSTREAD, isCritical);


    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }

    requestedAttributes       = null;
    returnAllOperationalAttrs = false;
    returnAllUserAttrs        = false;
  }



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param  oid            The OID to use for this control.
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  rawAttributes  The set of raw attributes to return in the entry.
   *                        A null or empty set will indicates that all user
   *                        attributes should be returned.
   */
  public LDAPPostReadRequestControl(String oid, boolean isCritical,
                                    Set<String> rawAttributes)
  {
    super(oid, isCritical);


    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }

    requestedAttributes       = null;
    returnAllOperationalAttrs = false;
    returnAllUserAttrs        = false;
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
    if (rawAttributes != null)
    {
      for (String attr : rawAttributes)
      {
        writer.writeOctetString(attr);
      }
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }


  /**
   * Retrieves the raw, unprocessed set of requested attributes.  It must not
   * be altered by the caller without calling <CODE>setRawAttributes</CODE> with
   * the updated set.
   *
   * @return  The raw, unprocessed set of attributes.
   */
  public Set<String> getRawAttributes()
  {
    return rawAttributes;
  }


  /**
   * Retrieves the set of processed attributes that have been requested for
   * inclusion in the entry that is returned.
   *
   * @return  The set of processed attributes that have been requested for
   *          inclusion in the entry that is returned.
   */
  public Set<AttributeType> getRequestedAttributes()
  {
    if (requestedAttributes == null)
    {
      returnAllOperationalAttrs = false;
      returnAllUserAttrs        = (rawAttributes.size() == 0);

      requestedAttributes =
           new LinkedHashSet<AttributeType>(rawAttributes.size());
      for (String attr : rawAttributes)
      {
        attr = attr.toLowerCase();

        if (attr.equals("*"))
        {
          returnAllUserAttrs = true;
        }
        else if (attr.equals("+"))
        {
          returnAllOperationalAttrs = true;
        }
        else if (attr.startsWith("@"))
        {
          String ocName = attr.substring(1);
          ObjectClass oc = DirectoryServer.getObjectClass(ocName);
          if (oc != null)
          {
            requestedAttributes.addAll(oc.getOptionalAttributeChain());
            requestedAttributes.addAll(oc.getRequiredAttributeChain());
          }
        }
        else
        {
          AttributeType at = DirectoryServer.getAttributeType(attr);
          if (at == null)
          {
            at = DirectoryServer.getDefaultAttributeType(attr);
          }

          requestedAttributes.add(at);
        }
      }
    }

    return requestedAttributes;
  }



  /**
   * Indicates whether the entry returned should include all user attributes
   * that the requester has permission to see.
   *
   * @return  <CODE>true</CODE> if the entry returned should include all user
   *          attributes that the requester has permission to see, or
   *          <CODE>false</CODE> if it should only include user attributes that
   *          have been explicitly included in the requested attribute list.
   */
  public boolean returnAllUserAttributes()
  {
    if (requestedAttributes == null)
    {
      getRequestedAttributes();
    }

    return returnAllUserAttrs;
  }



  /**
   * Indicates whether the entry returned should include all operational
   * attributes that the requester has permission to see.
   *
   * @return  <CODE>true</CODE> if the entry returned should include all
   *          operational attributes that the requester has permission to see,
   *          or <CODE>false</CODE> if it should only include user attributes
   *          that have been explicitly included in the requested attribute
   *          list.
   */
  public boolean returnAllOperationalAttributes()
  {
    if (requestedAttributes == null)
    {
      getRequestedAttributes();
    }

    return returnAllOperationalAttrs;
  }



  /**
   * Indicates whether the specified attribute type should be included in the
   * entry for the corresponding response control.
   *
   * @param  attrType  The attribute type for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the specified attribute type should be
   *          included in the entry for the corresponding response control, or
   *          <CODE>false</CODE> if not.
   */
  public boolean allowsAttribute(AttributeType attrType)
  {
    if (requestedAttributes == null)
    {
      getRequestedAttributes();
    }

    if (requestedAttributes.contains(attrType))
    {
      return true;
    }

    if (attrType.isOperational())
    {
      return returnAllOperationalAttrs;
    }
    else
    {
      return returnAllUserAttrs;
    }
  }



  /**
   * Appends a string representation of this LDAP post-read request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPPostReadRequestControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",attrs=\"");

    if (! rawAttributes.isEmpty())
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

