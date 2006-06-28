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
package org.opends.server.api;



import java.io.Serializable;
import java.util.Comparator;

import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for determining the correct order of values when sorting
 * or processing range filters.
 */
public abstract class OrderingMatchingRule
       extends MatchingRule
       implements Comparator<byte[]>, Serializable
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.OrderingMatchingRule";



  /**
   * The serial version identifier required to satisfy the compiler
   * because this class implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was
   * generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -5322529685787024597L;



  /**
   * Compares the first value to the second and returns a value that
   * indicates their relative order.
   *
   * @param  value1  The normalized form of the first value to
   *                 compare.
   * @param  value2  The normalized form of the second value to
   *                 compare.
   *
   * @return  A negative integer if <CODE>value1</CODE> should come
   *          before <CODE>value2</CODE> in ascending order, a
   *          positive integer if <CODE>value1</CODE> should come
   *          after <CODE>value2</CODE> in ascending order, or zero if
   *          there is no difference between the values with regard to
   *          ordering.
   */
  public abstract int compareValues(ByteString value1,
                                    ByteString value2);



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.
   * <BR><BR>
   * Note that ordering matching rules by default do not support
   * extensible matching, and therefore this method will always return
   * <CODE>UNDEFINED</CODE>.  If an ordering matching rule does
   * support extensible matching operations, then it should override
   * this method and provide an appropriate implementation.
   *
   * @param  attributeValue  The attribute value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   * @param  assertionValue  The assertion value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   *
   * @return  <CODE>true</CODE> if the attribute value should be
   *          considered a match for the provided assertion value, or
   *          <CODE>false</CODE> if not.
   */
  public ConditionResult valuesMatch(ByteString attributeValue,
                                     ByteString assertionValue)
  {
    assert debugEnter(CLASS_NAME, "valuesMatch",
                      String.valueOf(attributeValue),
                      String.valueOf(assertionValue));

    return ConditionResult.UNDEFINED;
  }
}

