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
package org.opends.server.api;



import java.util.Collection;

import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;



/**
 * This interface defines the set of methods that must be implemented
 * by a Directory Server module that implements a matching rule.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public interface MatchingRule
{
  /**
   * Retrieves the common name for this matching rule.
   *
   * @return The common name for this matching rule, or {@code null}
   *         if it does not have a name.
   */
  String getName();



  /**
   * Retrieves all names for this matching rule.
   *
   * @return All names for this matching rule.
   */
  Collection<String> getAllNames();



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return The OID for this matching rule.
   */
  String getOID();



  /**
   * Retrieves the normalized form of the provided assertion value,
   * which is best suite for efficiently performing matching
   * operations on that value.
   *
   * @param value
   *          The assertion value to be normalized.
   * @return The normalized version of the provided value.
   * @throws DirectoryException
   *           If the provided value is invalid according to the
   *           associated attribute syntax.
   */
  ByteString normalizeAssertionValue(ByteSequence value)
      throws DirectoryException;



  /**
   * Retrieves the name or OID for this matching rule. If it has a
   * name, then it will be returned. Otherwise, the OID will be
   * returned.
   *
   * @return The name or OID for this matching rule.
   */
  String getNameOrOID();



  /**
   * Retrieves the description for this matching rule.
   *
   * @return The description for this matching rule, or {@code null}
   *         if there is none.
   */
  String getDescription();



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return The OID of the syntax with which this matching rule is
   *         associated.
   */
  String getSyntaxOID();



  /**
   * Indicates whether this matching rule is declared "OBSOLETE". The
   * default implementation will always return {@code false}. If that
   * is not acceptable for a particular matching rule implementation,
   * then it should override this method and perform the appropriate
   * processing to return the correct value.
   *
   * @return {@code true} if this matching rule is declared
   *         "OBSOLETE", or {@code false} if not.
   */
  boolean isObsolete();



  /**
   * Retrieves the normalized form of the provided value, which is
   * best suite for efficiently performing matching operations on
   * that value.
   *
   * @param value
   *          The value to be normalized.
   * @return The normalized version of the provided value.
   * @throws DirectoryException
   *           If the provided value is invalid according to the
   *           associated attribute syntax.
   */
  ByteString normalizeValue(ByteSequence value)
      throws DirectoryException;



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value. This will only
   * be used for the purpose of extensible matching. Subclasses
   * should define more specific methods that are appropriate to the
   * matching rule type.
   *
   * @param attributeValue
   *          The attribute value in a form that has been normalized
   *          according to this matching rule.
   * @param assertionValue
   *          The assertion value in a form that has been normalized
   *          according to this matching rule.
   * @return {@code TRUE} if the attribute value should be considered
   *         a match for the provided assertion value, {@code FALSE}
   *         if it does not match, or {@code UNDEFINED} if the result
   *         is undefined.
   */
  ConditionResult valuesMatch(
      ByteSequence attributeValue, ByteSequence assertionValue);



  /**
   * Appends a string representation of this matching rule in the
   * format defined in RFC 2252 to the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  void toString(StringBuilder buffer);
}
