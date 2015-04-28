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
 *      Portions copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaUtils.unmodifiableCopyOfExtraProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.util.Reject;

/**
 * An abstract base class for LDAP schema definitions which contain an
 * description, and an optional set of extra properties.
 * <p>
 * This class defines common properties and behaviour of the various types of
 * schema definitions (e.g. object class definitions, and attribute type
 * definitions).
 */
abstract class SchemaElement {
    static abstract class SchemaElementBuilder<T extends SchemaElementBuilder<T>> {
        private String definition;
        private String description;
        private final Map<String, List<String>> extraProperties;
        private final SchemaBuilder schemaBuilder;

        SchemaElementBuilder(final SchemaBuilder schemaBuilder) {
            this.schemaBuilder = schemaBuilder;
            this.description = "";
            this.extraProperties = new LinkedHashMap<>(1);
        }

        SchemaElementBuilder(final SchemaBuilder schemaBuilder, final SchemaElement copy) {
            this.schemaBuilder = schemaBuilder;
            this.description = copy.description;
            this.extraProperties = new LinkedHashMap<>(copy.extraProperties);
        }

        /*
         * The abstract methods in this class are required in order to obtain
         * meaningful Javadoc. If the methods were defined in this class then
         * the resulting Javadoc in sub-class is invalid. The only workaround is
         * to make the methods abstract, provide "xxx0" implementations, and
         * override the abstract methods in sub-classes as delegates to the
         * "xxx0" methods. Ghastly! Thanks Javadoc.
         */

        /**
         * Sets the description.
         *
         * @param description
         *            The description, which may be {@code null} in which case
         *            the empty string will be used.
         * @return This builder.
         */
        public abstract T description(final String description);

        /**
         * Adds the provided collection of extended properties.
         *
         * @param extraProperties
         *            The collection of extended properties.
         * @return This builder.
         */
        public abstract T extraProperties(final Map<String, List<String>> extraProperties);

        /**
         * Adds the provided extended property.
         *
         * @param extensionName
         *            The name of the extended property.
         * @param extensionValues
         *            The optional list of values for the extended property.
         * @return This builder.
         */
        public abstract T extraProperties(final String extensionName, final String... extensionValues);

        /**
         * Adds the provided extended property.
         *
         * @param extensionName
         *            The name of the extended property.
         * @param extensionValues
         *            The optional list of values for the extended property.
         * @return This builder.
         */
        public T extraProperties(final String extensionName, final List<String> extensionValues) {
            return extraProperties(extensionName, extensionValues.toArray(new String[extensionValues.size()]));
        }

        /**
         * Removes all extra properties.
         *
         * @return This builder.
         */
        public abstract T removeAllExtraProperties();

        /**
         * Removes the specified extended property.
         *
         * @param extensionName
         *            The name of the extended property.
         * @param extensionValues
         *            The optional list of values for the extended property,
         *            which may be empty indicating that the entire property
         *            should be removed.
         * @return This builder.
         */
        public abstract T removeExtraProperty(final String extensionName,
                final String... extensionValues);

        T definition(final String definition) {
            this.definition = definition;
            return getThis();
        }

        T description0(final String description) {
            this.description = description == null ? "" : description;
            return getThis();
        }

        T extraProperties0(final Map<String, List<String>> extraProperties) {
            this.extraProperties.putAll(extraProperties);
            return getThis();
        }

        T extraProperties0(final String extensionName, final String... extensionValues) {
            if (this.extraProperties.get(extensionName) != null) {
                final List<String> tempExtraProperties =
                        new ArrayList<>(this.extraProperties.get(extensionName));
                tempExtraProperties.addAll(Arrays.asList(extensionValues));
                this.extraProperties.put(extensionName, tempExtraProperties);
            } else {
                this.extraProperties.put(extensionName, Arrays.asList(extensionValues));
            }
            return getThis();
        }

        String getDescription() {
            return description;
        }

        Map<String, List<String>> getExtraProperties() {
            return extraProperties;
        }

        SchemaBuilder getSchemaBuilder() {
            return schemaBuilder;
        }

        abstract T getThis();

        T removeAllExtraProperties0() {
            this.extraProperties.clear();
            return getThis();
        }

        T removeExtraProperty0(final String extensionName, final String... extensionValues) {
            if (this.extraProperties.get(extensionName) != null && extensionValues.length > 0) {
                final List<String> tempExtraProperties =
                        new ArrayList<>(this.extraProperties.get(extensionName));
                tempExtraProperties.removeAll(Arrays.asList(extensionValues));
                this.extraProperties.put(extensionName, tempExtraProperties);
            } else if (this.extraProperties.get(extensionName) != null) {
                this.extraProperties.remove(extensionName);
            }
            return getThis();
        }
    }

    /**
     * Lazily created string representation.
     */
    private String definition;

    /** The description for this definition. */
    private final String description;

    /** The set of additional name-value pairs. */
    private final Map<String, List<String>> extraProperties;

    SchemaElement() {
        this.description = "";
        this.extraProperties = Collections.<String, List<String>> emptyMap();
        this.definition = null;
    }

    SchemaElement(final SchemaElementBuilder<?> builder) {
        this.description = builder.description;
        this.extraProperties = unmodifiableCopyOfExtraProperties(builder.extraProperties);
        this.definition = builder.definition;
    }

    SchemaElement(final String description, final Map<String, List<String>> extraProperties,
            final String definition) {
        Reject.ifNull(description, extraProperties);
        this.description = description;
        this.extraProperties = extraProperties; // Should already be unmodifiable.
        this.definition = definition;
    }

    /** {@inheritDoc} */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Returns the description of this schema element, or the empty string if it
     * does not have a description.
     *
     * @return The description of this schema element, or the empty string if it
     *         does not have a description.
     */
    public final String getDescription() {
        return description;
    }

    /**
     * Returns an unmodifiable map containing all of the extra properties
     * associated with this schema element.
     *
     * @return An unmodifiable map containing all of the extra properties
     *         associated with this schema element.
     */
    public final Map<String, List<String>> getExtraProperties() {
        return extraProperties;
    }

    /** {@inheritDoc} */
    @Override
    public abstract int hashCode();

    /**
     * Returns the string representation of this schema element as defined in
     * RFC 2252.
     *
     * @return The string representation of this schema element as defined in
     *         RFC 2252.
     */
    @Override
    public final String toString() {
        if (definition == null) {
            definition = buildDefinition();
        }
        return definition;
    }

    final void appendDescription(final StringBuilder buffer) {
        if (description != null && description.length() > 0) {
            buffer.append(" DESC '");
            buffer.append(description);
            buffer.append("'");
        }
    }

    abstract void toStringContent(StringBuilder buffer);

    private final String buildDefinition() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("( ");
        toStringContent(buffer);
        if (!extraProperties.isEmpty()) {
            for (final Map.Entry<String, List<String>> e : extraProperties.entrySet()) {
                final String property = e.getKey();
                final List<String> valueList = e.getValue();
                buffer.append(" ");
                buffer.append(property);
                if (valueList.size() == 1) {
                    buffer.append(" '");
                    buffer.append(valueList.get(0));
                    buffer.append("'");
                } else {
                    buffer.append(" ( ");
                    for (final String value : valueList) {
                        buffer.append("'");
                        buffer.append(value);
                        buffer.append("' ");
                    }
                    buffer.append(")");
                }
            }
        }
        buffer.append(" )");
        return buffer.toString();
    }
}
