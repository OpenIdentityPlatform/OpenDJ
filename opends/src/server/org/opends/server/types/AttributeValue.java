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
package org.opends.server.types;



import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a data structure that holds information about a
 * single value of an attribute.  It will always store the value in
 * user-provided form, and will also store either a reference to the
 * associated attribute type or the normalized form of the value.  The
 * normalized form of the value should only be used in cases where
 * equality matching between two values can be performed with
 * byte-for-byte comparisons of the normalized values.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class AttributeValue
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The normalized form of this value.
  private ByteString normalizedValue;

  // The user-provided form of this value.
  private final ByteString value;

  // The attribute type with which this value is associated.
  private final AttributeType attributeType;



  /**
   * Creates a new attribute value with the provided information.
   *
   * @param  attributeType  The attribute type for this attribute
   *                        value.  It must not be {@code null}.
   * @param  value          The value in user-provided form for this
   *                        attribute value.  It must not be
   *                        {@code null}.
   */
  public AttributeValue(AttributeType attributeType, ByteString value)
  {
    ensureNotNull(attributeType, value);

    this.attributeType = attributeType;
    this.value         = value;

    normalizedValue = null;
  }


  /**
   * Creates a new attribute value with the provided information.
   *
   * @param  attributeType  The attribute type for this attribute
   *                        value.  It must not be {@code null}.
   * @param  value          The value in user-provided form for this
   *                        attribute value.  It must not be
   *                        {@code null}.
   */
  public AttributeValue(AttributeType attributeType, String value)
  {
    ensureNotNull(attributeType, value);

    this.attributeType = attributeType;
    this.value         = new ASN1OctetString(value);

    normalizedValue = null;
  }


  /**
   * Creates a new attribute value with the provided information.
   * Note that this version of the constructor should only be used for
   * attribute types in which equality matching can be performed by
   * byte-for-byte comparison of normalized values.
   *
   * @param  value            The user-provided form of this value.
   *                          It must not be {@code null}.
   * @param  normalizedValue  The normalized form of this value.  It
   *                          must not be {@code null}.
   */
  public AttributeValue(ByteString value, ByteString normalizedValue)
  {
    ensureNotNull(value, normalizedValue);

    this.value           = value;
    this.normalizedValue = normalizedValue;

    attributeType = null;
  }



  /**
   * Retrieves the user-defined form of this attribute value.
   *
   * @return  The user-defined form of this attribute value.
   */
  public ByteString getValue()
  {
    return value;
  }



  /**
   * Retrieves the raw bytes that make up this attribute value.
   *
   * @return  The raw bytes that make up this attribute value.
   */
  public byte[] getValueBytes()
  {
    return value.value();
  }



  /**
   * Retrieves a string representation of the user-defined form of
   * this attribute value.
   *
   * @return  The string representation of the user-defined form of
   *          this attribute value.
   */
  public String getStringValue()
  {
    return value.stringValue();
  }



  /**
   * Retrieves the normalized form of this attribute value.
   *
   * @return  The normalized form of this attribute value.
   *
   * @throws  DirectoryException  If an error occurs while trying to
   *                              normalize the value (e.g., if it is
   *                              not acceptable for use with the
   *                              associated equality matching rule).
   */
  public ByteString getNormalizedValue()
         throws DirectoryException
  {
    if (normalizedValue == null)
    {
      normalizedValue = attributeType.normalize(value);
    }

    return normalizedValue;
  }



  /**
   * Retrieves the bytes that make up the normalized form of this
   * value.
   *
   * @return  The bytes that make up the normalized form of this
   *          value.
   *
   * @throws  DirectoryException  If an error occurs while trying to
   *                              normalize the value (e.g., if it is
   *                              not acceptable for use with the
   *                              associated equality matching rule).
   */
  public byte[] getNormalizedValueBytes()
         throws DirectoryException
  {
    return getNormalizedValue().value();
  }



  /**
   * Retrieves a string representation of the normalized form of this
   * attribute value.
   *
   * @return  The string representation of the normalized form of this
   *          attribute value.
   *
   * @throws  DirectoryException  If an error occurs while trying to
   *                              normalize the value (e.g., if it is
   *                              not acceptable for use with the
   *                              associated equality matching rule).
   */
  public String getNormalizedStringValue()
         throws DirectoryException
  {
    if (normalizedValue == null)
    {
      normalizedValue = attributeType.normalize(value);
    }

    return normalizedValue.stringValue();
  }



  /**
   * Retrieves a string representation of the user-defined form of
   * this attribute value in a form suitable for use in a DN.
   *
   * @return  A string representation of the user-defined form of this
   *          attribute value in a form suitable for use in a DN.
   */
  public String getDNStringValue()
  {
    return getDNValue(getStringValue());
  }



  /**
   * Retrieves a string representation of the normalized form of this
   * attribute value in a form suitable for use in a DN.
   *
   * @return  A string representation of the normalized form of this
   *          attribute value in a form suitable for use in a DN.
   *
   * @throws  DirectoryException  If an error occurs while trying to
   *                              normalize the value (e.g., if it is
   *                              not acceptable for use with the
   *                              associated equality matching rule).
   */
  public String getNormalizedDNStringValue()
         throws DirectoryException
  {
    return getDNValue(getNormalizedStringValue());
  }



  /**
   * Retrieves a version of the provided value in a form that is
   * properly escaped for use in a DN or RDN.
   *
   * @param  value  The value to be represented in a DN-safe form.
   *
   * @return  A version of the provided value in a form that is
   *          properly escaped for use in a DN or RDN.
   */
  private static String getDNValue(String value)
  {
    if ((value == null) || (value.length() == 0))
    {
      return "";
    }

    StringBuilder buffer = new StringBuilder(value);

    int length = buffer.length();
    for (int i=0; i < length; i++)
    {
      char c = buffer.charAt(i);

      if ((c < ' ') || (c > '~'))
      {
        buffer.deleteCharAt(i);
        length -= 1;

        for (byte b : getBytes(String.valueOf(c)))
        {
          buffer.insert(i++, "\\");
          buffer.insert(i++, byteToLowerHex(b));
          i++;

          length += 3;
        }

        i -= 1;
      }
      else
      {
        switch (buffer.charAt(i))
        {
          case ',':
          case '+':
          case '"':
          case '\\':
          case '<':
          case '>':
          case ';':
            buffer.insert(i++, '\\');
            length++;
        }
      }
    }

    char c = buffer.charAt(0);
    if ((c == ' ') || (c == '#'))
    {
      buffer.insert(0, '\\');
      length++;
    }

    if (buffer.charAt(length-1) == ' ')
    {
      buffer.insert(length-1, '\\');
      length++;
    }

    return buffer.toString();
  }




  /**
   * Determines whether this attribute value is equal to the provided
   * object.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this attribute value is equal to
   *          the provided object, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o != null) && (o instanceof AttributeValue))
    {
      AttributeValue attrValue = (AttributeValue) o;


      try
      {
        if (attributeType != null)
        {
          EqualityMatchingRule matchingRule =
               attributeType.getEqualityMatchingRule();
          if (matchingRule == null)
          {
            return getNormalizedValue().equals(
                        attrValue.getNormalizedValue());
          }
          else
          {
            return (matchingRule.valuesMatch(getNormalizedValue(),
                         attrValue.getNormalizedValue()) ==
                    ConditionResult.TRUE);
          }
        }
        else if (attrValue.attributeType != null)
        {
          EqualityMatchingRule matchingRule =
               attrValue.attributeType.getEqualityMatchingRule();
          if (matchingRule == null)
          {
            return getNormalizedValue().equals(
                        attrValue.getNormalizedValue());
          }
          else
          {
            return (matchingRule.valuesMatch(getNormalizedValue(),
                         attrValue.getNormalizedValue()) ==
                    ConditionResult.TRUE);
          }
        }
        else
        {
          return normalizedValue.equals(
                      attrValue.getNormalizedValue());
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        return value.equals(attrValue.getValue());
      }
    }

    return false;
  }



  /**
   * Retrieves the hash code for this attribute value.  It will be
   * calculated as the sum of the first two bytes in the value, or the
   * value of a single-byte value, or zero for an empty value.
   *
   * @return  A hash code for this attribute value.
   */
  public int hashCode()
  {
    try
    {
      if (attributeType == null)
      {
        if (normalizedValue != null)
        {
          return normalizedValue.hashCode();
        }
        else
        {
          return value.hashCode();
        }
      }
      else
      {
        return attributeType.generateHashCode(this);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return value.hashCode();
    }
  }



  /**
   * Retrieves a string representation of this attribute value.
   *
   * @return  A string representation of this attribute value.
   */
  public String toString()
  {
    if (value == null)
    {
      return "null";
    }
    else
    {
      return value.stringValue();
    }
  }



  /**
   * Appends a string representation of this attribute value to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(value.toString());
  }
}

