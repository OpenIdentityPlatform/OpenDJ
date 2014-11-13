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
 *      Portions copyright 2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap;


import org.forgerock.opendj.ldap.spi.IndexQueryFactory;

/**
 * A compiled attribute value assertion.
 */
public interface Assertion {

    /** An assertion that always return UNDEFINED for matches and that creates a match all query. */
    Assertion UNDEFINED_ASSERTION = new Assertion() {
        @Override
        public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
            return ConditionResult.UNDEFINED;
        }

        @Override
        public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
            // Subclassing this class will always work, albeit inefficiently.
            // This is better than throwing an exception for no good reason.
            return factory.createMatchAllQuery();
        }
    };

    /**
     * Indicates whether the provided attribute value should be considered a
     * match for this assertion value according to the matching rule.
     *
     * @param normalizedAttributeValue
     *            The normalized attribute value.
     * @return {@code TRUE} if the attribute value should be considered a match
     *         for the provided assertion value, {@code FALSE} if it does not
     *         match, or {@code UNDEFINED} if the result is undefined.
     */
    ConditionResult matches(ByteSequence normalizedAttributeValue);

    /**
     * Returns an index query appropriate for the provided attribute
     * value assertion.
     *
     * @param <T>
     *          The type of index query created by the {@code factory}.
     * @param factory
     *          The index query factory which should be used to
     *          construct the index query.
     * @return The index query appropriate for the provided attribute
     *         value assertion.
     * @throws DecodeException
     *           If an error occurs while generating the index query.
     */
    <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException;

}
