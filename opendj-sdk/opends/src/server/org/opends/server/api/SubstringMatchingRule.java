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



import java.util.List;

import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for substring matching.
 */
public abstract class SubstringMatchingRule
       extends MatchingRule
{
  /**
   * Normalizes the provided value fragment into a form that can be
   * used to efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DirectoryException  If the provided value fragment is
   *                              not acceptable according to the
   *                              associated syntax.
   */
  public abstract ByteString normalizeSubstring(ByteString substring)
         throws DirectoryException;



  /**
   * Determines whether the provided value matches the given substring
   * filter components.  Note that any of the substring filter
   * components may be <CODE>null</CODE> but at least one of them must
   * be non-<CODE>null</CODE>.
   *
   * @param  value           The normalized value against which to
   *                         compare the substring components.
   * @param  subInitial      The normalized substring value fragment
   *                         that should appear at the beginning of
   *                         the target value.
   * @param  subAnyElements  The normalized substring value fragments
   *                         that should appear in the middle of the
   *                         target value.
   * @param  subFinal        The normalized substring value fragment
   *                         that should appear at the end of the
   *                         target value.
   *
   * @return  <CODE>true</CODE> if the provided value does match the
   *          given substring components, or <CODE>false</CODE> if
   *          not.
   */
  public abstract boolean valueMatchesSubstring(
                               ByteString value,
                               ByteString subInitial,
                               List<ByteString> subAnyElements,
                               ByteString subFinal);



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.
   * <BR><BR>
   * Note that substring matching rules by default do not support
   * extensible matching, and therefore this method will always return
   * <CODE>UNDEFINED</CODE>.  If a substring matching rule does
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
    return ConditionResult.UNDEFINED;
  }
}

