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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Syntax;

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
   * Retrieves all names for this matching rule.
   *
   * @return All names for this matching rule.
   */
  Collection<String> getNames();



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return The OID for this matching rule.
   */
  String getOID();

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
   * Whole class to be replaced by the equivalent SDK class.
   *
   * @return SDK syntax
   */
  Syntax getSyntax();

  /**
   * Whole class to be replaced by the equivalent SDK class.
   *
   * @param assertionValue
   *          the value
   * @return SDK syntax
   * @throws DecodeException
   *           if problem
   */
  Assertion getAssertion(ByteSequence assertionValue) throws DecodeException;

  /**
   * Returns the normalized form of the provided assertion value, which is
   * best suited for efficiently performing greater than or equal ordering
   * matching operations on that value. The assertion value is guaranteed to
   * be valid against this matching rule's assertion syntax.
   *
   * @param assertionValue
   *            The syntax checked assertion value to be normalized.
   * @return The normalized version of the provided assertion value.
   * @throws DecodeException
   *             if the syntax of the value is not valid.
   */
  Assertion getGreaterOrEqualAssertion(ByteSequence assertionValue) throws DecodeException;

  /**
   * Returns the normalized form of the provided assertion value, which is
   * best suited for efficiently performing greater than or equal ordering
   * matching operations on that value. The assertion value is guaranteed to
   * be valid against this matching rule's assertion syntax.
   *
   * @param assertionValue
   *            The syntax checked assertion value to be normalized.
   * @return The normalized version of the provided assertion value.
   * @throws DecodeException
   *             if the syntax of the value is not valid.
   */
  Assertion getLessOrEqualAssertion(ByteSequence assertionValue) throws DecodeException;

  /**
   * Returns the normalized form of the provided assertion substring values,
   * which is best suited for efficiently performing matching operations on
   * that value.
   *
   * @param subInitial
   *            The normalized substring value fragment that should appear at
   *            the beginning of the target value.
   * @param subAnyElements
   *            The normalized substring value fragments that should appear in
   *            the middle of the target value.
   * @param subFinal
   *            The normalized substring value fragment that should appear at
   *            the end of the target value.
   * @return The normalized version of the provided assertion value.
   * @throws DecodeException
   *             if the syntax of the value is not valid.
   */
  Assertion getSubstringAssertion(ByteSequence subInitial, List<? extends ByteSequence> subAnyElements,
      ByteSequence subFinal) throws DecodeException;

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
   * @throws DecodeException
   *           If the provided value is invalid according to the
   *           associated attribute syntax.
   */
  ByteString normalizeAttributeValue(ByteSequence value)
      throws DecodeException;

  /**
   * Appends a string representation of this matching rule in the
   * format defined in RFC 2252 to the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  void toString(StringBuilder buffer);

  /**
   * Get a comparator that can be used to compare the attribute values
   * normalized by this matching rule.
   *
   * @return A comparator that can be used to compare the attribute values
   *         normalized by this matching rule.
   */
  Comparator<ByteSequence> comparator();
}
