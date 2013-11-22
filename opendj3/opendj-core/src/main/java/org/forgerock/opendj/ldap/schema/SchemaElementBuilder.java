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
 *      Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class defines the set of methods and structures that must be implemented
 * to define a schema element builder.
 *
 * @param <T>
 *            Builder could be : AttributeTypeBuilder, NameFormBuilder,
 *            DITContentRuleBuilder, DITStructureRuleBuilder,
 *            MatchingRuleBuilder, ObjectClassBuilder, ...
 */
abstract class SchemaElementBuilder<T extends SchemaElementBuilder<T>> {
    // List of attributes in common / required attributes used by builders.
    String description = "";
    Map<String, List<String>> extraProperties;
    private SchemaBuilder schemaBuilder = null;

    /**
     * Creates a new abstract schema element builder implementation.
     */
    SchemaElementBuilder() {
        extraProperties = new LinkedHashMap<String, List<String>>();
    }

    /**
     * Defines the schemThe builder.
     *
     * @param sc
     *            The schemThe builder.
     * @return A builder
     */
    T schemaBuilder(final SchemaBuilder sc) {
        this.schemaBuilder = sc;
        return getThis();
    }

    /**
     * Returns the schemThe builder.
     *
     * @return The schemThe builder.
     */
    SchemaBuilder getSchemaBuilder() {
        return schemaBuilder;
    }

    abstract T getThis();

    /**
     * The description of the schema element.
     *
     * @param description
     *            a string containing the description of the schema element.
     * @return <T> The builder.
     */
    public T description(final String description) {
        this.description = description;
        return getThis();
    }

    /**
     * A map containing additional properties associated with the schema element
     * definition.
     * <p>
     * cf. RFC 4512 : extensions WSP RPAREN ; extensions
     *
     * @param extraProperties
     *            Additional properties.
     * @return The builder.
     */
    public T extraProperties(final Map<String, List<String>> extraProperties) {
        this.extraProperties.putAll(extraProperties);
        return getThis();
    }

    /**
     * Additional properties associated with the schema element definition.
     * <p>
     * cf. RFC 4512 : extensions WSP RPAREN ; extensions
     *
     * @param key
     *            like X-ORIGIN
     * @param extensions
     *            e.g : 'RFC 2252'
     * @return The builder.
     */
    public T extraProperties(final String key, final String... extensions) {
        if (this.extraProperties.get(key) != null) {
            List<String> tempExtraProperties = new ArrayList<String>(this.extraProperties.get(key));
            tempExtraProperties.addAll(Arrays.asList(extensions));
            this.extraProperties.put(key, tempExtraProperties);
        } else {
            this.extraProperties.put(key, Arrays.asList(extensions));
        }
        return getThis();
    }

    /**
     * Removes an extra property.
     *
     * @param key
     *            The key to remove.
     * @param extensions
     *            The extension to remove. Can be null.
     * @return The builder.
     */
    public T removeExtraProperties(final String key, final String extensions) {
        if (this.extraProperties.get(key) != null && extensions != null) {
            List<String> tempExtraProperties = new ArrayList<String>(this.extraProperties.get(key));
            tempExtraProperties.remove(tempExtraProperties.indexOf(extensions));
            this.extraProperties.put(key, tempExtraProperties);
        } else if (this.extraProperties.get(key) != null) {
            this.extraProperties.remove(key);
        }
        return getThis();
    }

    /**
     * Clears all extra properties.
     *
     * @return The builder.
     */
    public T clearExtraProperties() {
        this.extraProperties.clear();
        return getThis();
    }
}
