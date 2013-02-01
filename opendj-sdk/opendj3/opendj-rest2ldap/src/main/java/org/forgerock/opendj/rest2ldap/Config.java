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

import static org.forgerock.opendj.rest2ldap.Config.ReadOnUpdatePolicy.USE_READ_ENTRY_CONTROLS;
import static org.forgerock.opendj.rest2ldap.Utils.ensureNotNull;

import org.forgerock.opendj.ldap.Filter;

/**
 * Common configuration options.
 */
public final class Config {

    /**
     * An interface for incrementally constructing common configuration options.
     */
    public static final class Builder {
        private Filter falseFilter;
        private ReadOnUpdatePolicy readOnUpdatePolicy;
        private Filter trueFilter;

        private Builder() {
            // Nothing to do.
        }

        /**
         * Returns a new configuration based on the current state of this
         * builder.
         *
         * @return A new configuration based on the current state of this
         *         builder.
         */
        public Config build() {
            return new Config(trueFilter, falseFilter, readOnUpdatePolicy);
        }

        /**
         * Sets the absolute false filter which should be used when querying the
         * LDAP server.
         *
         * @param filter
         *            The absolute false filter.
         * @return A reference to this builder.
         */
        public Builder falseFilter(final Filter filter) {
            this.trueFilter = ensureNotNull(filter);
            return this;
        }

        /**
         * Sets the policy which should be used in order to read an entry before
         * it is deleted, or after it is added or modified.
         *
         * @param policy
         *            The policy which should be used in order to read an entry
         *            before it is deleted, or after it is added or modified.
         * @return A reference to this builder.
         */
        public Builder readOnUpdatePolicy(final ReadOnUpdatePolicy policy) {
            this.readOnUpdatePolicy = ensureNotNull(policy);
            return this;
        }

        /**
         * Sets the absolute true filter which should be used when querying the
         * LDAP server.
         *
         * @param filter
         *            The absolute true filter.
         * @return A reference to this builder.
         */
        public Builder trueFilter(final Filter filter) {
            this.trueFilter = ensureNotNull(filter);
            return this;
        }
    };

    /**
     * The policy which should be used in order to read an entry before it is
     * deleted, or after it is added or modified.
     */
    public static enum ReadOnUpdatePolicy {
        /**
         * The LDAP entry will not be read when an update is performed. More
         * specifically, the REST resource will not be returned as part of a
         * create, delete, patch, or update request.
         */
        DISABLED,

        /**
         * The LDAP entry will be read atomically using the RFC 4527 read-entry
         * controls. More specifically, the REST resource will be returned as
         * part of a create, delete, patch, or update request, and it will
         * reflect the state of the resource at the time the update was
         * performed. This policy requires that the LDAP server supports RFC
         * 4527.
         */
        USE_READ_ENTRY_CONTROLS,

        /**
         * The LDAP entry will be read non-atomically using an LDAP search when
         * an update is performed. More specifically, the REST resource will be
         * returned as part of a create, delete, patch, or update request, but
         * it may not reflect the state of the resource at the time the update
         * was performed.
         */
        USE_SEARCH;
    }

    private static final Config DEFAULT = new Builder().trueFilter(Filter.objectClassPresent())
            .falseFilter(Filter.present("1.1")).readOnUpdatePolicy(USE_READ_ENTRY_CONTROLS).build();

    /**
     * Returns a new builder which can be used for incrementally constructing
     * common configuration options. The builder will initially have
     * {@link #defaultConfig() default} settings.
     *
     * @return The new builder.
     */
    public static Builder builder() {
        return builder(DEFAULT);
    }

    /**
     * Returns a new builder which can be used for incrementally constructing
     * common configuration options. The builder will initially have the same
     * settings as the provided configuration.
     *
     * @param config
     *            The initial settings.
     * @return The new builder.
     */
    public static Builder builder(final Config config) {
        return new Builder().trueFilter(config.trueFilter()).falseFilter(config.falseFilter())
                .readOnUpdatePolicy(config.readOnUpdatePolicy());
    }

    /**
     * Returns the default configuration having the following settings:
     * <ul>
     * <li>the absolute true filter {@code (objectClass=*)}
     * <li>the absolute false filter {@code (1.1=*)}
     * <li>the read on update policy
     * {@link ReadOnUpdatePolicy#USE_READ_ENTRY_CONTROLS}.
     * </ul>
     *
     * @return The default configuration.
     */
    public static Config defaultConfig() {
        return DEFAULT;
    }

    private final Filter falseFilter;

    private final ReadOnUpdatePolicy readOnUpdatePolicy;
    private final Filter trueFilter;

    private Config(final Filter trueFilter, final Filter falseFilter,
            final ReadOnUpdatePolicy readOnUpdatePolicy) {
        this.trueFilter = trueFilter;
        this.falseFilter = falseFilter;
        this.readOnUpdatePolicy = readOnUpdatePolicy;
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
