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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;




/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for approximate matching.
 */
public abstract class ApproximateMatchingRule
       extends MatchingRule
{



  /**
   * Indicates whether the two provided normalized values are
   * approximately equal to each other.
   *
   * @param  value1  The normalized form of the first value to
   *                 compare.
   * @param  value2  The normalized form of the second value to
   *                 compare.
   *
   * @return  <CODE>true</CODE> if the provided values are
   *          approximately equal, or <CODE>false</CODE> if not.
   */
  public abstract boolean approximatelyMatch(ByteString value1,
                                             ByteString value2);



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.  Other forms of
   * matching against approximate matching rules should use the
   * <CODE>areEqual</CODE> method.
   *
   * @param  attributeValue  The attribute value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   * @param  assertionValue  The assertion value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   *
   * @return  <CODE>TRUE</CODE> if the attribute value should be
   *          considered a match for the provided assertion value,
   *          <CODE>FALSE</CODE> if it does not match, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   */
  public ConditionResult valuesMatch(ByteString attributeValue,
                                     ByteString assertionValue)
  {
    if (approximatelyMatch(attributeValue, assertionValue))
    {
      return ConditionResult.TRUE;
    }
    else
    {
      return ConditionResult.FALSE;
    }
  }
}

