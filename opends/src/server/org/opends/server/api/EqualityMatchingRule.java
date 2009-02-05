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



import org.opends.server.types.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for equality matching.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class EqualityMatchingRule extends MatchingRule
{
  /**
   * Indicates whether the two provided normalized values are equal to
   * each other.
   *
   * @param  value1  The normalized form of the first value to
   *                 compare.
   * @param  value2  The normalized form of the second value to
   *                 compare.
   *
   * @return  {@code true} if the provided values are equal, or
   *          {@code false} if not.
   */
  public boolean areEqual(ByteSequence value1, ByteSequence value2)
  {
    return value1.equals(value2);
  }

  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.  Other forms of
   * matching against equality matching rules should use the
   * {@code areEqual} method.
   *
   * @param  attributeValue  The attribute value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   * @param  assertionValue  The assertion value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   *
   * @return  {@code true} if the attribute value should be considered
   *          a match for the provided assertion value, or
   *          {@code false} if not.
   */
  @Override
  public ConditionResult valuesMatch(ByteSequence attributeValue,
                                     ByteSequence assertionValue)
  {
    if (areEqual(attributeValue, assertionValue))
    {
      return ConditionResult.TRUE;
    }
    else
    {
      return ConditionResult.FALSE;
    }
  }



  /**
   * Generates a hash code for the provided attribute value.  This
   * version of the method will simply create a hash code from the
   * normalized form of the attribute value.  For matching rules
   * explicitly designed to work in cases where byte-for-byte
   * comparisons of normalized values is not sufficient for
   * determining equality (e.g., if the associated attribute syntax is
   * based on hashed or encrypted values), then this method must be
   * overridden to provide an appropriate implementation for that
   * case.
   *
   * @param  attributeValue  The attribute value for which to generate
   *                         the hash code.
   *
   * @return  The hash code generated for the provided attribute
   *          value.
   */
  public int generateHashCode(ByteSequence attributeValue)
  {
    return attributeValue.hashCode();
  }
}

