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
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forgerock.opendj.util.Validator;

/**
 * An abstract base class for LDAP schema definitions which contain an
 * description, and an optional set of extra properties.
 * <p>
 * This class defines common properties and behaviour of the various types of
 * schema definitions (e.g. object class definitions, and attribute type
 * definitions).
 */
abstract class SchemaElement {
    // The description for this definition.
    final String description;

    // The set of additional name-value pairs.
    final Map<String, List<String>> extraProperties;

    SchemaElement(final String description, final Map<String, List<String>> extraProperties) {
        Validator.ensureNotNull(description, extraProperties);
        this.description = description;

        // Assumes caller has made the map unmodifiable.
        this.extraProperties = extraProperties;
    }

    /**
     * {@inheritDoc}
     */
    public abstract boolean equals(Object obj);

    /**
     * Returns the description of this schema definition.
     *
     * @return The description of this schema definition.
     */
    public final String getDescription() {

        return description;
    }

    /**
     * Returns an unmodifiable list containing the values of the named "extra"
     * property for this schema definition.
     *
     * @param name
     *            The name of the "extra" property whose values are to be
     *            returned.
     * @return Returns an unmodifiable list containing the values of the named
     *         "extra" property for this schema definition, which may be empty
     *         if no such property is defined.
     */
    public final List<String> getExtraProperty(final String name) {

        final List<String> values = extraProperties.get(name);
        return values != null ? values : Collections.<String> emptyList();
    }

    /**
     * Returns an unmodifiable set containing the names of the "extra"
     * properties associated with this schema definition.
     *
     * @return Returns an unmodifiable set containing the names of the "extra"
     *         properties associated with this schema definition.
     */
    public final Set<String> getExtraPropertyNames() {

        return extraProperties.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public abstract int hashCode();

    /**
     * {@inheritDoc}
     */
    public abstract String toString();

    /**
     * Builds a string representation of this schema definition in the form
     * specified in RFC 2252.
     *
     * @return The string representation of this schema definition in the form
     *         specified in RFC 2252.
     */
    final String buildDefinition() {
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

    /**
     * Appends a string representation of this schema definition's non-generic
     * properties to the provided buffer.
     *
     * @param buffer
     *            The buffer to which the information should be appended.
     */
    abstract void toStringContent(StringBuilder buffer);
}
