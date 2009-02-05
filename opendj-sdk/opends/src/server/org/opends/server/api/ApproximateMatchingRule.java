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
package org.opends.server.api;



import org.opends.server.types.ConditionResult;
import org.opends.server.types.ByteSequence;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for approximate matching.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
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
   * @return  {@code true} if the provided values are approximately
   *          equal, or {@code false} if not.
   */
  public abstract boolean approximatelyMatch(ByteSequence value1,
                                             ByteSequence value2);



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.  Other forms of
   * matching against approximate matching rules should use the
   * {@code areEqual} method.
   *
   * @param  attributeValue  The attribute value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   * @param  assertionValue  The assertion value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   *
   * @return  {@code TRUE} if the attribute value should be considered
   *          a match for the provided assertion value, {@code FALSE}
   *          if it does not match, or {@code UNDEFINED} if the result
   *          is undefined.
   */
  public ConditionResult valuesMatch(ByteSequence attributeValue,
                                     ByteSequence assertionValue)
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

