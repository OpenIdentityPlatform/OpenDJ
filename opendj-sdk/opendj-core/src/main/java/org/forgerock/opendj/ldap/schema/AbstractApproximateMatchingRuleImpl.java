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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * This class implements an approximate matching rule that matches normalized
 * values in byte order.
 */
abstract class AbstractApproximateMatchingRuleImpl extends AbstractMatchingRuleImpl {

    private final Indexer indexer;

    AbstractApproximateMatchingRuleImpl(String indexID) {
        indexer = new DefaultIndexer(indexID);
    }

    @Override
    public final Assertion getAssertion(final Schema schema, final ByteSequence assertionValue)
            throws DecodeException {
        return named(indexer.getIndexID(), normalizeAttributeValue(schema, assertionValue));
    }

    /** {@inheritDoc} */
    @Override
    public final Collection<? extends Indexer> createIndexers(IndexingOptions options) {
        return Collections.singleton(indexer);
    }
}
