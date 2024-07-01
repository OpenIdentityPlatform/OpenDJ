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
 * Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.Assertion.*;

import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;

/**
 * This class implements a default equality or approximate matching rule that
 * matches normalized values in byte order.
 */
abstract class AbstractMatchingRuleImpl implements MatchingRuleImpl {

    static DefaultAssertion named(final String indexID, final ByteSequence normalizedAssertionValue) {
        return new DefaultAssertion(indexID, normalizedAssertionValue);
    }

    private static final class DefaultAssertion implements Assertion {
        /** The ID of the DB index to use with this assertion. */
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
        /** The ID of the DB index to use with this indexer. */
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
