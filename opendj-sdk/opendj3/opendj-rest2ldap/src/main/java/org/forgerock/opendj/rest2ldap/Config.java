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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * Common configuration options.
 */
final class Config {

    private final Filter falseFilter;
    private final DecodeOptions options;
    private final ReadOnUpdatePolicy readOnUpdatePolicy;
    private final Schema schema;
    private final Filter trueFilter;

    Config(final Filter trueFilter, final Filter falseFilter,
            final ReadOnUpdatePolicy readOnUpdatePolicy, final Schema schema) {
        this.trueFilter = trueFilter;
        this.falseFilter = falseFilter;
        this.readOnUpdatePolicy = readOnUpdatePolicy;
        this.schema = schema;
        this.options = new DecodeOptions().setSchema(schema);
    }

    /**
     * Returns the decoding options which should be used when decoding controls
     * in responses.
     *
     * @return The decoding options which should be used when decoding controls
     *         in responses.
     */
    public DecodeOptions decodeOptions() {
        return options;
    }

    /**
     * Returns the absolute false filter which should be used when querying the
     * LDAP server.
     *
     * @return The absolute false filter.
     */
    public Filter falseFilter() {
        return falseFilter;
    }

    /**
     * Returns the policy which should be used in order to read an entry before
     * it is deleted, or after it is added or modified.
     *
     * @return The policy which should be used in order to read an entry before
     *         it is deleted, or after it is added or modified.
     */
    public ReadOnUpdatePolicy readOnUpdatePolicy() {
        return readOnUpdatePolicy;
    }

    /**
     * Returns the schema which should be used when attribute types and
     * controls.
     *
     * @return The schema which should be used when attribute types and
     *         controls.
     */
    public Schema schema() {
        return schema;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("trueFilter=");
        builder.append(trueFilter);
        builder.append(", falseFilter=");
        builder.append(falseFilter);
        builder.append(", readOnUpdatePolicy=");
        builder.append(readOnUpdatePolicy);
        return builder.toString();
    }

    /**
     * Returns the absolute true filter which should be used when querying the
     * LDAP server.
     *
     * @return The absolute true filter.
     */
    public Filter trueFilter() {
        return trueFilter;
    }
}
