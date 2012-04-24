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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Comparator;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This interface defines the set of methods that must be implemented to define
 * a new matching rule.
 */
public interface MatchingRuleImpl {
    /**
     * Get a comparator that can be used to compare the attribute values
     * normalized by this matching rule.
     *
     * @param schema
     *            The schema in which this matching rule is defined.
     * @return A comparator that can be used to compare the attribute values
     *         normalized by this matching rule.
     */
    public Comparator<ByteSequence> comparator(Schema schema);

    /**
     * Retrieves the normalized form of the provided assertion value, which is
     * best suite for efficiently performing matching operations on that value.
     * The assertion value is guarenteed to be valid against this matching
     * rule's assertion syntax.
     *
     * @param schema
     *            The schema in which this matching rule is defined.
     * @param value
     *            The syntax checked assertion value to be normalized.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if an syntax error occured while parsing the value.
     */
    public Assertion getAssertion(Schema schema, ByteSequence value) throws DecodeException;

    /**
     * Retrieves the normalized form of the provided assertion substring values,
     * which is best suite for efficiently performing matching operations on
     * that value.
     *
     * @param schema
     *            The schema in which this matching rule is defined.
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
     *             if an syntax error occured while parsing the value.
     */
    public Assertion getAssertion(Schema schema, ByteSequence subInitial,
            List<? extends ByteSequence> subAnyElements, ByteSequence subFinal)
            throws DecodeException;

    /**
     * Retrieves the normalized form of the provided assertion value, which is
     * best suite for efficiently performing greater than or equal matching
     * operations on that value. The assertion value is guarenteed to be valid
     * against this matching rule's assertion syntax.
     *
     * @param schema
     *            The schema in which this matching rule is defined.
     * @param value
     *            The syntax checked assertion value to be normalized.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if an syntax error occured while parsing the value.
     */
    public Assertion getGreaterOrEqualAssertion(Schema schema, ByteSequence value)
            throws DecodeException;

    /**
     * Retrieves the normalized form of the provided assertion value, which is
     * best suite for efficiently performing greater than or equal matching
     * operations on that value. The assertion value is guarenteed to be valid
     * against this matching rule's assertion syntax.
     *
     * @param schema
     *            The schema in which this matching rule is defined.
     * @param value
     *            The syntax checked assertion value to be normalized.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if an syntax error occured while parsing the value.
     */
    public Assertion getLessOrEqualAssertion(Schema schema, ByteSequence value)
            throws DecodeException;

    /**
     * Retrieves the normalized form of the provided attribute value, which is
     * best suite for efficiently performing matching operations on that value.
     *
     * @param schema
     *            The schema in which this matching rule is defined.
     * @param value
     *            The attribute value to be normalized.
     * @return The normalized version of the provided attribute value.
     * @throws DecodeException
     *             if an syntax error occured while parsing the value.
     */
    public ByteString normalizeAttributeValue(Schema schema, ByteSequence value)
            throws DecodeException;
}
