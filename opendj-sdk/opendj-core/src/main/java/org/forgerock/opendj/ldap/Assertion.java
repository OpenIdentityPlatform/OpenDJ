/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2014 ForgeRock AS.
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
