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



import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;

import static
    org.opends.server.loggers.debug.DebugLogger.debugCought;
import static
    org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for equality matching.
 */
public abstract class EqualityMatchingRule
       extends MatchingRule
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
   * @return  <CODE>true</CODE> if the provided values are equal, or
   *          <CODE>false</CODE> if not.
   */
  public abstract boolean areEqual(ByteString value1,
                                   ByteString value2);



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.  Other forms of
   * matching against equality matching rules should use the
   * <CODE>areEqual</CODE> method.
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
  public int generateHashCode(AttributeValue attributeValue)
  {
    try
    {
      return attributeValue.getNormalizedValue().hashCode();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      try
      {
        return attributeValue.getValue().hashCode();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e2);
        }

        return 0;
      }
    }
  }
}

