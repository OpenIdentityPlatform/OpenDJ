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
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;

import static org.forgerock.opendj.ldap.Assertion.*;
import static com.forgerock.opendj.util.StaticUtils.*;

/**
 * This class implements a default equality or approximate matching rule that
 * matches normalized values in byte order.
 */
abstract class AbstractMatchingRuleImpl implements MatchingRuleImpl {

    static DefaultAssertion named(final String indexID, final ByteSequence normalizedAssertionValue) {
        return new DefaultAssertion(indexID, normalizedAssertionValue);
    }

    private static final class DefaultAssertion implements Assertion {
        /** The ID of the DB index to use with this assertion.*/
        private final String indexID;
        private final ByteSequence normalizedAssertionValue;

        private DefaultAssertion(final String indexID, final ByteSequence normalizedAssertionValue) {
            this.indexID = indexID;
            this.normalizedAssertionValue = normalizedAssertionValue;
        }

        @Override
        public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
            return ConditionResult.valueOf(normalizedAssertionValue.equals(normalizedAttributeValue));
        }

        @Override
        public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
            return factory.createExactMatchQuery(indexID, normalizedAssertionValue);
        }
    }

    final class DefaultIndexer implements Indexer {
        /** The ID of the DB index to use with this indexer.*/
        private final String indexID;

        DefaultIndexer(String indexID) {
            this.indexID = indexID;
        }

        @Override
        public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException {
            keys.add(normalizeAttributeValue(schema, value));
        }

        @Override
        public String keyToHumanReadableString(ByteSequence key) {
            return AbstractMatchingRuleImpl.this.keyToHumanReadableString(key);
        }

        @Override
        public String getIndexID() {
            return indexID;
        }
    }

    String keyToHumanReadableString(ByteSequence key) {
        return key.toByteString().toHexString();
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    @Override
    public Assertion getSubstringAssertion(final Schema schema, final ByteSequence subInitial,
            final List<? extends ByteSequence> subAnyElements, final ByteSequence subFinal)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    @Override
    public Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    @Override
    public Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }
}
