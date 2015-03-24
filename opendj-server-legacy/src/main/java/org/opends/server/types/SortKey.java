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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.MatchingRule;

/**
 * This class defines a data structure that may be used as a sort key.
 * It includes an attribute type and a boolean value that indicates
 * whether the sort should be ascending or descending.  It may also
 * contain a specific ordering matching rule that should be used for
 * the sorting process, although if none is provided it will use the
 * default ordering matching rule for the attribute type.
 * <p>
 * FIXME: replace with the equivalent SDK type.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class SortKey
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The attribute type for this sort key. */
  private AttributeType attributeType;

  /** The indication of whether the sort should be ascending. */
  private boolean ascending;

  /** The ordering matching rule to use with this sort key. */
  private MatchingRule orderingRule;



  /**
   * Creates a new sort key with the provided information.
   *
   * @param  attributeType  The attribute type for this sort key.
   * @param  ascending      Indicates whether the sort should be in
   *                        ascending order rather than descending.
   */
  public SortKey(AttributeType attributeType, boolean ascending)
  {
    this.attributeType = attributeType;
    this.ascending     = ascending;

    orderingRule = null;
  }



  /**
   * Creates a new sort key with the provided information.
   *
   * @param  attributeType  The attribute type for this sort key.
   * @param  ascending      Indicates whether the sort should be in
   *                        ascending order rather than descending.
   * @param  orderingRule   The ordering matching rule to use with
   *                        this sort key.
   */
  public SortKey(AttributeType attributeType, boolean ascending, MatchingRule orderingRule)
  {
    this.attributeType = attributeType;
    this.ascending     = ascending;
    this.orderingRule  = orderingRule;
  }



  /**
   * Retrieves the attribute type for this sort key.
   *
   * @return  The attribute type for this sort key.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Indicates whether the specified attribute should be sorted in
   * ascending order.
   *
   * @return  {@code true} if the attribute should be sorted in
   *          ascending order, or {@code false} if it should be sorted
   *          in descending order.
   */
  public boolean ascending()
  {
    return ascending;
  }



  /**
   * Retrieves the ordering matching rule to use with this sort key.
   *
   * @return  The ordering matching rule to use with this sort key.
   */
  public MatchingRule getOrderingRule()
  {
    return orderingRule;
  }



  /**
   * Compares the provided values using this sort key.
   *
   * @param  value1  The first value to be compared.
   * @param  value2  The second value to be compared.
   *
   * @return  A negative value if the first value should come before
   *          the second in a sorted list, a positive value if the
   *          first value should come after the second in a sorted
   *          list, or zero if there is no relative difference between
   *          the values.
   */
  public int compareValues(ByteString value1, ByteString value2)
  {
    // A null value will always come after a non-null value.
    if (value1 == null)
    {
      if (value2 == null)
      {
        return 0;
      }
      else
      {
        return 1;
      }
    }
    else if (value2 == null)
    {
      return -1;
    }


    // Use the ordering matching rule if one is provided.
    // Otherwise, fall back on the default ordering rule for the attribute type.
    if (orderingRule != null)
    {
      return compareValues(orderingRule, value1, value2);
    }
    final MatchingRule rule = attributeType.getOrderingMatchingRule();
    if (rule != null)
    {
      return compareValues(rule, value1, value2);
    }
    return 0;
  }

  private int compareValues(MatchingRule rule, ByteString value1,
      ByteString value2)
  {
    try
    {
      final ByteString val1 = rule.normalizeAttributeValue(value1);
      final ByteString val2 = rule.normalizeAttributeValue(value2);
      return ascending ? val1.compareTo(val2) : val2.compareTo(val1);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return 0;
    }
  }



  /**
   * Retrieves a string representation of this sort key.
   *
   * @return  A string representation of this sort key.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this sort key to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SortKey(");
    if (ascending)
    {
      buffer.append("+");
    }
    else
    {
      buffer.append("-");
    }
    buffer.append(attributeType.getNameOrOID());

    if (orderingRule != null)
    {
      buffer.append(":");
      buffer.append(orderingRule.getNameOrOID());
    }

    buffer.append(")");
  }

  /**
   * Retrieves the hash code for this sort key.
   *
   * @return  The hash code for this sort key.
   */
  @Override
  public int hashCode()
  {
    int hashCode = 0;

    if(ascending)
    {
      hashCode += 1;
    }

    hashCode += attributeType.hashCode();

    if(orderingRule != null)
    {
      hashCode += orderingRule.hashCode();
    }

    return hashCode;
  }

  /**
   * Indicates whether this sort key is equal to the provided
   * object.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provide object is equal to this
   *          sort key, or <CODE>false</CODE> if it is not.
   */
  @Override
  public boolean equals(Object o)
  {
    if(o == null)
    {
      return false;
    }

    if (o == this)
    {
      return true;
    }

    if (! (o instanceof SortKey))
    {
      return false;
    }

    SortKey s = (SortKey) o;

    if(ascending != s.ascending)
    {
      return false;
    }

    if(!attributeType.equals(s.attributeType))
    {
      return false;
    }

    if(orderingRule != null)
    {
      if(s.orderingRule != null)
      {
        if(!orderingRule.equals(s.orderingRule))
        {
          return false;
        }
      }
      else if(!orderingRule.equals(
          s.attributeType.getOrderingMatchingRule()))
      {
        return false;
      }
    }
    else if(s.orderingRule != null)
    {
      if(!attributeType.getOrderingMatchingRule().equals(
          s.orderingRule))
      {
        return false;
      }
    }

    return true;
  }
}

