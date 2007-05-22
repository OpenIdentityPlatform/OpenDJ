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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import org.opends.server.api.OrderingMatchingRule;

import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class defines a data structure that may be used as a sort key.
 * It includes an attribute type and a boolean value that indicates
 * whether the sort should be ascending or descending.  It may also
 * contain a specific ordering matching rule that should be used for
 * the sorting process, although if none is provided it will use the
 * default ordering matching rule for the attribute type.
 */
public class SortKey
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The attribute type for this sort key.
  private AttributeType attributeType;

  // The indication of whether the sort should be ascending.
  private boolean ascending;

  // The ordering matching rule to use with this sort key.
  private OrderingMatchingRule orderingRule;



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
  public SortKey(AttributeType attributeType, boolean ascending,
                 OrderingMatchingRule orderingRule)
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
  public OrderingMatchingRule getOrderingRule()
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
  public int compareValues(AttributeValue value1,
                           AttributeValue value2)
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


    // Use the ordering matching rule if one is provided.  Otherwise,
    // fall back on the default ordering rule for the attribute type.
    if (orderingRule == null)
    {
      try
      {
        OrderingMatchingRule rule =
             attributeType.getOrderingMatchingRule();
        if (rule == null)
        {
          return 0;
        }

        if (ascending)
        {
          return rule.compareValues(value1.getNormalizedValue(),
                                    value2.getNormalizedValue());
        }
        else
        {
          return rule.compareValues(value2.getNormalizedValue(),
                                    value1.getNormalizedValue());
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        return 0;
      }
    }
    else
    {
      try
      {
        if (ascending)
        {
          return orderingRule.compareValues(
                      orderingRule.normalizeValue(value1.getValue()),
                      orderingRule.normalizeValue(value2.getValue()));
        }
        else
        {
          return orderingRule.compareValues(
                      orderingRule.normalizeValue(value2.getValue()),
                      orderingRule.normalizeValue(value1.getValue()));
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        return 0;
      }
    }
  }



  /**
   * Retrieves a string representation of this sort key.
   *
   * @return  A string representation of this sort key.
   */
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
}

