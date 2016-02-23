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
 * Copyright 2014-2015 ForgeRock AS.
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
