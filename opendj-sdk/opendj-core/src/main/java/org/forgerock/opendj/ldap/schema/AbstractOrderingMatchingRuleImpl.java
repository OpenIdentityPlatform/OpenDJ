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

/**
 * This class implements a default ordering matching rule that matches
 * normalized values in byte order.
 * <p>
 * The getXXXAssertion() methods are default implementations which assume that
 * the assertion syntax is the same as the attribute syntax. Override them if
 * this assumption does not hold true.
 */
abstract class AbstractOrderingMatchingRuleImpl extends AbstractMatchingRuleImpl {
    private final Collection<? extends Indexer> indexers;
    private final String indexId;

    /** Constructor for default matching rules. */
    AbstractOrderingMatchingRuleImpl() {
        this("ordering");
    }

    /** Constructor for non-default matching rules. */
    AbstractOrderingMatchingRuleImpl(String indexId) {
        this.indexId = indexId;
        this.indexers = Collections.singleton(new DefaultIndexer(indexId));
    }

    /** {@inheritDoc} */
    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final ByteString normAssertion = normalizeAttributeValue(schema, value);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence attributeValue) {
                return ConditionResult.valueOf(attributeValue.compareTo(normAssertion) < 0);
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createRangeMatchQuery(indexId, ByteString.empty(), normAssertion, false, false);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
        final ByteString normAssertion = normalizeAttributeValue(schema, value);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                return ConditionResult.valueOf(normalizedAttributeValue.compareTo(normAssertion) >= 0);
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createRangeMatchQuery(indexId, normAssertion, ByteString.empty(), true, false);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final ByteString normAssertion = normalizeAttributeValue(schema, value);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                return ConditionResult.valueOf(normalizedAttributeValue.compareTo(normAssertion) <= 0);
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createRangeMatchQuery(indexId, ByteString.empty(), normAssertion, false, true);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public Collection<? extends Indexer> getIndexers() {
        return indexers;
    }

    final String getIndexId() {
        return indexId;
    }
}
