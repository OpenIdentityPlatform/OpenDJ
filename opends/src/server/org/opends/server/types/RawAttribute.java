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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.LDAPAttribute;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a raw attribute, which has a type (which may
 * include zero or more options), and zero or more values.  It is an
 * unprocessed element, so it will not have undergone any syntax or
 * other forms of validity checking.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public abstract class RawAttribute
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new raw attribute with the provided type and no values.
   *
   * @param  attributeType  The attribute type for this attribute.  It
   *                        must not be {@code null}.
   *
   * @return  The created raw attribute.
   */
  public static RawAttribute create(String attributeType)
  {
    ensureNotNull(attributeType);

    return new LDAPAttribute(attributeType);
  }



  /**
   * Creates a new raw attribute with the provided type value.
   *
   * @param  attributeType  The attribute type for this attribute.  It
   *                        must not be {@code null}.
   * @param  value          The value to use for this attribute.  It
   *                        must not be {@code null}.
   *
   * @return  The created raw attribute.
   */
  public static RawAttribute create(String attributeType,
                                    String value)
  {
    ensureNotNull(attributeType, value);

    return new LDAPAttribute(attributeType, value);
  }



  /**
   * Creates a new raw attribute with the provided type value.
   *
   * @param  attributeType  The attribute type for this attribute.  It
   *                        must not be {@code null}.
   * @param  value          The value to use for this attribute.  It
   *                        must not be {@code null}.
   *
   * @return  The created raw attribute.
   */
  public static RawAttribute create(String attributeType,
                                    ByteString value)
  {
    ensureNotNull(attributeType);

    return new LDAPAttribute(attributeType, value);
  }



  /**
   * Creates a new raw attribute with the provided type and values.
   *
   * @param  attributeType  The attribute type for this attribute.  It
   *                        must not be {@code null}.
   * @param  values         The set of values for this attribute.
   *
   * @return  The created raw attribute.
   */
  public static RawAttribute create(String attributeType,
                                    ArrayList<ByteString> values)
  {
    ensureNotNull(attributeType);

    return new LDAPAttribute(attributeType, values);
  }



  /**
   * Creates a new raw attribute from the provided attribute.
   *
   * @param  attribute  The attribute to use to create this raw
   *                    attribute.  It must not be {@code null}.
   *
   * @return  The created raw attribute.
   */
  public static RawAttribute create(Attribute attribute)
  {
    ensureNotNull(attribute);

    return new LDAPAttribute(attribute);
  }



  /**
   * Retrieves the attribute type for this attribute.
   *
   * @return  The attribute type for this attribute.
   */
  public abstract String getAttributeType();



  /**
   * Specifies the attribute type for this attribute.
   *
   * @param  attributeType  The attribute type for this attribute.
   */
  public abstract void setAttributeType(String attributeType);



  /**
   * Retrieves the set of values for this attribute.  The returned
   * list may be modified by the caller.
   *
   * @return  The set of values for this attribute.
   */
  public abstract ArrayList<ByteString> getValues();



  /**
   * Retrieves a core attribute containing the information for this
   * raw attribute.
   *
   * @return  A core attribute containing the information for this raw
   *          attribute.
   *
   * @throws  LDAPException  If the provided value is invalid
   *                         according to the attribute syntax.
   */
  public abstract Attribute toAttribute()
         throws LDAPException;

  /**
   * Writes this attribute to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the
   *                     stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence();
    stream.writeOctetString(getAttributeType());

    stream.writeStartSet();
    ArrayList<ByteString> values = getValues();
    if ((values != null))
    {
      for(ByteString value : values)
      {
        stream.writeOctetString(value);
      }
    }
    stream.writeEndSequence();

    stream.writeEndSequence();
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an
   * LDAP attribute.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP attribute.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw attribute.
   */
  public static LDAPAttribute decode(ASN1Reader reader)
         throws LDAPException
  {
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
          ERR_LDAP_ATTRIBUTE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    String attributeType;
    try
    {
      attributeType = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_ATTRIBUTE_DECODE_TYPE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ArrayList<ByteString> values;
    try
    {
      reader.readStartSequence();
      values = new ArrayList<ByteString>();
      while(reader.hasNextElement())
      {
        values.add(reader.readOctetString());
      }
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_ATTRIBUTE_DECODE_VALUES.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
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
          ERR_LDAP_ATTRIBUTE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new LDAPAttribute(attributeType, values);
  }



  /**
   * Retrieves a string representation of this attribute.
   *
   * @return  A string representation of this attribute.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this attribute to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public abstract void toString(StringBuilder buffer);



  /**
   * Appends a multi-line string representation of this LDAP attribute
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   * @param  indent  The number of spaces from the margin that the
   *                 lines should be indented.
   */
  public abstract void toString(StringBuilder buffer, int indent);
}

