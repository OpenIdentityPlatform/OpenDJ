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

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * This class implements a default ordering matching rule that matches
 * normalized values in byte order.
 * <p>
 * The getXXXAssertion() methods are default implementations which assume that
 * the assertion syntax is the same as the attribute syntax. Override them if
 * this assumption does not hold true.
 */
abstract class AbstractOrderingMatchingRuleImpl extends AbstractMatchingRuleImpl {
    private final Indexer indexer;

    /** Constructor for non-default matching rules. */
    AbstractOrderingMatchingRuleImpl(String indexId) {
        this.indexer = new DefaultIndexer(indexId);
    }

    /** {@inheritDoc} */
    @Override
    public final Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final ByteString normAssertion = normalizeAttributeValue(schema, value);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence attributeValue) {
                return ConditionResult.valueOf(attributeValue.compareTo(normAssertion) < 0);
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createRangeMatchQuery(indexer.getIndexID(), ByteString.empty(), normAssertion, false,
                        false);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public final Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final ByteString normAssertion = normalizeAttributeValue(schema, value);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                return ConditionResult.valueOf(normalizedAttributeValue.compareTo(normAssertion) >= 0);
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createRangeMatchQuery(indexer.getIndexID(), normAssertion, ByteString.empty(), true,
                        false);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public final Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final ByteString normAssertion = normalizeAttributeValue(schema, value);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                return ConditionResult.valueOf(normalizedAttributeValue.compareTo(normAssertion) <= 0);
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createRangeMatchQuery(indexer.getIndexID(), ByteString.empty(), normAssertion, false,
                        true);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public final Collection<? extends Indexer> createIndexers(IndexingOptions options) {
        return Collections.singleton(indexer);
    }

}
