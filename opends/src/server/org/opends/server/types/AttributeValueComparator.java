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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Comparator;

import org.opends.server.api.OrderingMatchingRule;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a <CODE>Comparator</CODE> object that may be
 * used to compare attribute values, particularly for inclusion in a
 * sorted list.  The values must share the same attribute type, and if
 * possible the ordering matching rule for that attribute type will be
 * used to make the determination.  If there is no ordering matching
 * rule, then a bytewise comparison of the normalized values (from
 * left to right) will be used.
 */
public class AttributeValueComparator
       implements Comparator<AttributeValue>
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.AttributeValueComparator";



  // The attribute type with which this comparator is associated.
  private AttributeType attributeType;

  // The ordering matching rule for the attribute type, if any.
  private OrderingMatchingRule matchingRule;



  /**
   * Creates a new instance of this attribute value comparator that
   * may be used with values of the provided attribute type.
   *
   * @param  attributeType  The attribute type that should be used for
   *                        this attribute value comparator.
   */
  public AttributeValueComparator(AttributeType attributeType)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(attributeType));

    this.attributeType = attributeType;
    this.matchingRule  = attributeType.getOrderingMatchingRule();
  }



  /**
   * Compares the provided attribute values and returns an integer
   * value that reflects the relative order between them.
   *
   * @param  value1  The first attribute value to compare.
   * @param  value2  The second attribute value to compare.
   *
   * @return  A negative value if the first value should come before
   *          the second in an ordered list, a positive value if they
   *          first value should come after the second in an ordered
   *          list, or zero if there is no difference between their
   *          order (i.e., the values are equal).
   */
  public int compare(AttributeValue value1, AttributeValue value2)
  {
    assert debugEnter(CLASS_NAME, "compare", String.valueOf(value1),
                      String.valueOf(value2));

    try
    {
      if (matchingRule == null)
      {
        return compareValues(value1.getNormalizedValue(),
                             value2.getNormalizedValue());
      }
      else
      {
        return matchingRule.compare(value1.getNormalizedValueBytes(),
                                    value2.getNormalizedValueBytes());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "compare", e);

      // Just get the raw values and do a comparison between them.
      return compareValues(value1.getValue(), value2.getValue());
    }
  }



  /**
   * Compares the provided values using a simple bytewise comparison
   * and returns an integer value that reflects the relative order
   * between them.
   *
   * @param  value1  The first byte string to compare.
   * @param  value2  The second byte string to compare.
   *
   * @return  A negative value if the first byte string should come
   *          before the second in an ordered list, a positive value
   *          if the first byte string should come after the second in
   *          an ordered list, or zero if there is no difference
   *          between them (i.e., the values are equal).
   */
  public static int compareValues(ByteString value1,
                                  ByteString value2)
  {
    assert debugEnter(CLASS_NAME, "compareValues",
                      String.valueOf(value1), String.valueOf(value2));

    byte[] value1Bytes = value1.value();
    byte[] value2Bytes = value2.value();

    int index = 0;
    while (true)
    {
      if (index < value1Bytes.length)
      {
        if (index < value2Bytes.length)
        {
          int value = value1Bytes[index] - value2Bytes[index];
          if (value != 0)
          {
            return value;
          }
        }
        else
        {
          return 1;
        }
      }
      else if (index < value2Bytes.length)
      {
        return -1;
      }
      else
      {
        return 0;
      }

      index++;
    }
  }
}

