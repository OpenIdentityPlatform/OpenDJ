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
 * Portions copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.schema.Schema;

import org.forgerock.util.Reject;

/**
 * Decode options allow applications to control how requests and responses are
 * decoded. In particular:
 * <ul>
 * <li>The strategy for selecting which {@code Schema} should be used for
 * decoding distinguished names, attribute descriptions, and other objects which
 * require a schema in order to be decoded.
 * <li>The {@code Attribute} implementation which should be used when decoding
 * attributes.
 * <li>The {@code Entry} implementation which should be used when decoding
 * entries or entry like objects.
 * </ul>
 */
public final class DecodeOptions {
    private static final class FixedSchemaResolver implements SchemaResolver {
        private final Schema schema;

        private FixedSchemaResolver(final Schema schema) {
            this.schema = schema;
        }

        @Override
        public Schema resolveSchema(final String dn) {
            return schema;
        }
    }

    private SchemaResolver schemaResolver;
    private EntryFactory entryFactory;
    private AttributeFactory attributeFactory;

    /**
     * Creates a new set of decode options which will always use the default
     * schema returned by {@link Schema#getDefaultSchema()},
     * {@link LinkedAttribute}, and {@link LinkedHashMapEntry}.
     */
    public DecodeOptions() {
        this.attributeFactory = LinkedAttribute.FACTORY;
        this.entryFactory = LinkedHashMapEntry.FACTORY;
        this.schemaResolver = SchemaResolver.DEFAULT;
    }

    /**
     * Creates a new set of decode options having the same initial set of
     * options as the provided set of decode options.
     *
     * @param options
     *            The set of decode options to be copied.
     */
    public DecodeOptions(final DecodeOptions options) {
        this.attributeFactory = options.attributeFactory;
        this.entryFactory = options.entryFactory;
        this.schemaResolver = options.schemaResolver;
    }

    /**
     * Returns the {@code AttributeFactory} which will be used for creating new
     * {@code Attribute} instances when decoding attributes.
     *
     * @return The {@code AttributeFactory} which will be used for creating new
     *         {@code Attribute} instances when decoding attributes.
     */
    public final AttributeFactory getAttributeFactory() {
        return attributeFactory;
    }

    /**
     * Returns the {@code EntryFactory} which will be used for creating new
     * {@code Entry} instances when decoding entries.
     *
     * @return The {@code EntryFactory} which will be used for creating new
     *         {@code Entry} instances when decoding entries.
     */
    public final EntryFactory getEntryFactory() {
        return entryFactory;
    }

    /**
     * Returns the strategy for selecting which {@code Schema} should be used
     * for decoding distinguished names, attribute descriptions, and other
     * objects which require a {@code Schema} in order to be decoded.
     *
     * @return The schema resolver which will be used for decoding.
     */
    public final SchemaResolver getSchemaResolver() {
        return schemaResolver;
    }

    /**
     * Sets the {@code AttributeFactory} which will be used for creating new
     * {@code Attribute} instances when decoding attributes.
     *
     * @param factory
     *            The {@code AttributeFactory} which will be used for creating
     *            new {@code Attribute} instances when decoding attributes.
     * @return A reference to this set of decode options.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public final DecodeOptions setAttributeFactory(final AttributeFactory factory) {
        Reject.ifNull(factory);
        this.attributeFactory = factory;
        return this;
    }

    /**
     * Sets the {@code EntryFactory} which will be used for creating new
     * {@code Entry} instances when decoding entries.
     *
     * @param factory
     *            The {@code EntryFactory} which will be used for creating new
     *            {@code Entry} instances when decoding entries.
     * @return A reference to this set of decode options.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public final DecodeOptions setEntryFactory(final EntryFactory factory) {
        Reject.ifNull(factory);
        this.entryFactory = factory;
        return this;
    }

    /**
     * Sets the {@code Schema} which will be used for decoding distinguished
     * names, attribute descriptions, and other objects which require a schema
     * in order to be decoded. This setting overrides the currently active
     * schema resolver set using {@link #setSchemaResolver}.
     *
     * @param schema
     *            The {@code Schema} which will be used for decoding.
     * @return A reference to this set of decode options.
     * @throws NullPointerException
     *             If {@code schema} was {@code null}.
     */
    public final DecodeOptions setSchema(final Schema schema) {
        Reject.ifNull(schema);
        this.schemaResolver = new FixedSchemaResolver(schema);
        return this;
    }

    /**
     * Sets the strategy for selecting which {@code Schema} should be used for
     * decoding distinguished names, attribute descriptions, and other objects
     * which require a {@code Schema} in order to be decoded. This setting
     * overrides the currently active schema set using {@link #setSchema}.
     *
     * @param resolver
     *            The {@code SchemaResolver} which will be used for decoding.
     * @return A reference to this set of decode options.
     * @throws NullPointerException
     *             If {@code resolver} was {@code null}.
     */
    public final DecodeOptions setSchemaResolver(final SchemaResolver resolver) {
        Reject.ifNull(resolver);
        this.schemaResolver = resolver;
        return this;
    }
}
