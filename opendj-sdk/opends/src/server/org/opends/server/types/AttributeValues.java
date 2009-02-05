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



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import org.opends.messages.Message;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class contains various methods for manipulating
 * {@link AttributeValue}s as well as static factory methods for
 * facilitating common {@link AttributeValue} construction use-cases.
 * <p>
 * Of particular interest are the following three factory methods:
 *
 * <pre>
 * create(AttributeType, ByteString);
 * create(ByteString, ByteString);
 * </pre>
 *
 * These are provided in order to facilitate construction of delayed
 * normalization using AttributeType and pre-normalized attributes
 * values respectively.
 */
public final class AttributeValues
{

  /**
   * Prevent instantiation.
   */
  private AttributeValues()
  {
    // Do nothing.
  }



  /**
   * Creates an AttributeValue where the value will the normalized on
   * demand using the matching rules of the provided AttributeType.
   * Equality matching involving this AttributeValue will be based on
   * the matching rules of the provided AttributeType.
   *
   * @param attributeType
   *          The AttributeType to use.
   * @param value
   *          The attribute value as a ByteString.
   * @return The newly created AttributeValue.
   */
  public static AttributeValue create(AttributeType attributeType,
      ByteString value)
  {
    return new DelayedNormalizationValue(attributeType, value);
  }



  /**
   * Creates an AttributeValue where the UTF-8 encoded value of the
   * string will the normalized on demand sing the matching rules of
   * the provided AttributeType. Equality matching involving this
   * AttributeValue will be based on the matching rules of the
   * provided AttributeType.
   *
   * @param attributeType
   *          The AttributeType to use.
   * @param value
   *          The attribute value as a String.
   * @return The newly created AttributeValue.
   */
  public static AttributeValue create(AttributeType attributeType,
                                      String value)
  {
    return new DelayedNormalizationValue(attributeType, ByteString
        .valueOf(value));
  }



  /**
   * Creates an AttributeValue where the value is pre-normalized.
   * Equality matching will be based on a byte-by-byte comparison.
   *
   * @param value
   *          The attribute value as a ByteString.
   * @param normalizedValue
   *          The normalized attribute value as a ByteString.
   * @return The newly created AttributeValue.
   */
  public static AttributeValue create(ByteString value,
      ByteString normalizedValue)
  {
    return new PreNormalizedValue(value, normalizedValue);
  }



  /**
   * This attribute value implementation will always store the value
   * in user-provided form, and a reference to the associated
   * attribute type. The normalized form of the value will be
   * initialized upon first request. The normalized form of the value
   * should only be used in cases where equality matching between two
   * values can be performed with byte-for-byte comparisons of the
   * normalized values.
   */
  private static final class DelayedNormalizationValue implements
      AttributeValue
  {

    /**
     * The tracer object for the debug logger.
     */
    private static final DebugTracer TRACER = getTracer();

    private final AttributeType attributeType;

    private final ByteString value;

    private ByteString normalizedValue;


    /**
     * Construct a new DelayedNormalizationValue.
     *
     * @param attributeType The attribute type.
     * @param value The value of the attriute.
     */
    private DelayedNormalizationValue(
        AttributeType attributeType, ByteString value)
    {
      this.attributeType = attributeType;
      this.value = value;
      this.normalizedValue = null;
    }



    /**
     * {@inheritDoc}
     */
    public ByteString getNormalizedValue() throws DirectoryException
    {
      if (normalizedValue == null)
      {
        EqualityMatchingRule equalityMatchingRule = attributeType
            .getEqualityMatchingRule();
        if (equalityMatchingRule == null)
        {
          Message message = ERR_ATTR_TYPE_NORMALIZE_NO_MR.get(String
              .valueOf(value), attributeType.getNameOrOID());
          throw new DirectoryException(
              ResultCode.INAPPROPRIATE_MATCHING, message);
        }

        normalizedValue = equalityMatchingRule.normalizeValue(value);
      }

      return normalizedValue;
    }



    /**
     * {@inheritDoc}
     */
    public ByteString getValue()
    {
      return value;
    }



    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o)
    {
      if (this == o)
      {
        return true;
      }
      else if (o instanceof AttributeValue)
      {
        AttributeValue attrValue = (AttributeValue) o;
        try
        {
          return getNormalizedValue().equals(
              attrValue.getNormalizedValue());
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
     * {@inheritDoc}
     */
    public int hashCode()
    {
      EqualityMatchingRule equalityMatchingRule = attributeType
          .getEqualityMatchingRule();

      ByteString valueToHash;
      try
      {
        valueToHash = getNormalizedValue();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        valueToHash = value;
      }

      if (equalityMatchingRule != null)
      {
        return equalityMatchingRule.generateHashCode(valueToHash);
      }
      else
      {
        return valueToHash.hashCode();
      }
    }



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      if (value == null)
      {
        return "null";
      }
      else
      {
        return value.toString();
      }
    }



    /**
     * {@inheritDoc}
     */
    public void toString(StringBuilder buffer)
    {
      buffer.append(value.toString());
    }
  }



  /**
   * This attribute value implementation will always store the value
   * in user-provided form, and the normalized form. The normalized
   * form of the value should only be used in cases where equality
   * matching between two values can be performed with byte-for-byte
   * comparisons of the normalized values.
   */
  private static final class PreNormalizedValue
      implements AttributeValue
  {

    /**
     * The tracer object for the debug logger.
     */
    private static final DebugTracer TRACER = getTracer();

    private final ByteString value;

    private final ByteString normalizedValue;


    /**
     * Construct a new PreNormalizedValue.
     * @param value The user provided value of the attribute.
     * @param normalizedValue The normalized value of the attribute.
     */
    private PreNormalizedValue(ByteString value,
                               ByteString normalizedValue)
    {
      this.value = value;
      this.normalizedValue = normalizedValue;
    }



    /**
     * {@inheritDoc}
     */
    public ByteString getNormalizedValue()
    {
      return normalizedValue;
    }



    /**
     * {@inheritDoc}
     */
    public ByteString getValue()
    {
      return value;
    }



    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o)
    {
      if (this == o)
      {
        return true;
      }
      else if (o instanceof AttributeValue)
      {
        AttributeValue attrValue = (AttributeValue) o;
        try
        {
          return normalizedValue.equals(
              attrValue.getNormalizedValue());
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
     * {@inheritDoc}
     */
    public int hashCode()
    {
      return normalizedValue.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      if (value == null)
      {
        return "null";
      }
      else
      {
        return value.toString();
      }
    }



    /**
     * {@inheritDoc}
     */
    public void toString(StringBuilder buffer)
    {
      buffer.append(value.toString());
    }
  }
}
