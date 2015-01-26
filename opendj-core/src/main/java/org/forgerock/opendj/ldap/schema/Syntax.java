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
 *      Portions copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.util.Reject;

/**
 * This class defines a data structure for storing and interacting with an LDAP
 * syntaxes, which constrain the structure of attribute values stored in an LDAP
 * directory, and determine the representation of attribute and assertion values
 * transferred in the LDAP protocol.
 * <p>
 * Syntax implementations must extend the {@link SyntaxImpl} interface so they
 * can be used by OpenDJ to validate attribute values.
 * <p>
 * Where ordered sets of names, or extra properties are provided, the ordering
 * will be preserved when the associated fields are accessed via their getters
 * or via the {@link #toString()} methods.
 */
public final class Syntax extends SchemaElement {

    /** A fluent API for incrementally constructing syntaxes. */
    public static final class Builder extends SchemaElementBuilder<Builder> {

        private String oid;
        private SyntaxImpl impl;

        Builder(final Syntax syntax, final SchemaBuilder builder) {
            super(builder, syntax);
            this.oid = syntax.oid;
            this.impl = syntax.impl;
        }

        Builder(final String oid, final SchemaBuilder builder) {
            super(builder);
            oid(oid);
        }

        /**
         * Adds this syntax to the schema, throwing a
         * {@code ConflictingSchemaElementException} if there is an existing
         * syntax with the same numeric OID.
         *
         * @return The parent schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing syntax with the same numeric OID.
         */
        public SchemaBuilder addToSchema() {
            return getSchemaBuilder().addSyntax(new Syntax(this), false);
        }

        /**
         * Adds this syntax to the schema overwriting any existing syntax with the same numeric OID.
         *
         * @return The parent schema builder.
         */
        public SchemaBuilder addToSchemaOverwrite() {
            return getSchemaBuilder().addSyntax(new Syntax(this), true);
        }

        /**
         * Adds this syntax to the schema - overwriting any existing syntax with the same numeric OID
         * if the overwrite parameter is set to {@code true}.
         *
         * @param overwrite
         *            {@code true} if any syntax with the same OID should be overwritten.
         * @return The parent schema builder.
         */
        SchemaBuilder addToSchema(final boolean overwrite) {
            if (overwrite) {
                return addToSchemaOverwrite();
            }
            return addToSchema();
        }

        @Override
        public Builder description(final String description) {
            return description0(description);
        }

        @Override
        public Builder extraProperties(final Map<String, List<String>> extraProperties) {
            return extraProperties0(extraProperties);
        }

        @Override
        public Builder extraProperties(final String extensionName, final String... extensionValues) {
            return extraProperties0(extensionName, extensionValues);
        }

        /**
         * Sets the numeric OID which uniquely identifies this syntax.
         *
         * @param oid
         *            The numeric OID.
         * @return This builder.
         */
        public Builder oid(final String oid) {
            this.oid = oid;
            return this;
        }

        @Override
        public Builder removeAllExtraProperties() {
            return removeAllExtraProperties0();
        }

        @Override
        public Builder removeExtraProperty(final String extensionName, final String... extensionValues) {
            return removeExtraProperty0(extensionName, extensionValues);
        }

        /**
         * Sets the syntax implementation.
         *
         * @param implementation
         *            The syntax implementation.
         * @return This builder.
         */
        public Builder implementation(final SyntaxImpl implementation) {
            this.impl = implementation;
            return this;
        }

        @Override
        Builder getThis() {
            return this;
        }
    }

    private final String oid;
    private MatchingRule equalityMatchingRule;
    private MatchingRule orderingMatchingRule;
    private MatchingRule substringMatchingRule;
    private MatchingRule approximateMatchingRule;
    private Schema schema;
    private SyntaxImpl impl;

    private Syntax(final Builder builder) {
        super(builder);

        // Checks for required attributes.
        if (builder.oid == null || builder.oid.isEmpty()) {
            throw new IllegalArgumentException("An OID must be specified.");
        }

        oid = builder.oid;
        impl = builder.impl;
    }

    /**
     * Creates a syntax representing an unrecognized syntax and whose
     * implementation is substituted by the schema's default syntax.
     *
     * @param schema
     *            The parent schema.
     * @param oid
     *            The numeric OID of the unrecognized syntax.
     */
    Syntax(final Schema schema, final String oid) {
        super("", Collections.singletonMap("X-SUBST", Collections.singletonList(schema.getDefaultSyntax().getOID())),
                null);

        Reject.ifNull(oid);
        this.oid = oid;
        this.schema = schema;

        final Syntax defaultSyntax = schema.getDefaultSyntax();
        this.impl = defaultSyntax.impl;
        this.approximateMatchingRule = defaultSyntax.getApproximateMatchingRule();
        this.equalityMatchingRule = defaultSyntax.getEqualityMatchingRule();
        this.orderingMatchingRule = defaultSyntax.getOrderingMatchingRule();
        this.substringMatchingRule = defaultSyntax.getSubstringMatchingRule();
    }

    /**
     * Returns {@code true} if the provided object is an attribute syntax having
     * the same numeric OID as this attribute syntax.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is an attribute syntax having
     *         the same numeric OID as this attribute syntax.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof Syntax) {
            final Syntax other = (Syntax) o;
            return oid.equals(other.oid);
        } else {
            return false;
        }
    }

    /**
     * Retrieves the default approximate matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default approximate matching rule that will be used for
     *         attributes with this syntax, or {@code null} if approximate
     *         matches will not be allowed for this type by default.
     */
    public MatchingRule getApproximateMatchingRule() {
        return approximateMatchingRule;
    }

    /**
     * Retrieves the default equality matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default equality matching rule that will be used for
     *         attributes with this syntax, or {@code null} if equality matches
     *         will not be allowed for this type by default.
     */
    public MatchingRule getEqualityMatchingRule() {
        return equalityMatchingRule;
    }

    /**
     * Retrieves the OID for this attribute syntax.
     *
     * @return The OID for this attribute syntax.
     */
    public String getOID() {
        return oid;
    }

    /**
     * Retrieves the name for this attribute syntax.
     *
     * @return The name for this attribute syntax.
     */
    public String getName() {
        return impl.getName();
    }

    /**
     * Retrieves the default ordering matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default ordering matching rule that will be used for
     *         attributes with this syntax, or {@code null} if ordering matches
     *         will not be allowed for this type by default.
     */
    public MatchingRule getOrderingMatchingRule() {
        return orderingMatchingRule;
    }

    /**
     * Retrieves the default substring matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default substring matching rule that will be used for
     *         attributes with this syntax, or {@code null} if substring matches
     *         will not be allowed for this type by default.
     */
    public MatchingRule getSubstringMatchingRule() {
        return substringMatchingRule;
    }

    /**
     * Returns the hash code for this attribute syntax. It will be calculated as
     * the hash code of the numeric OID.
     *
     * @return The hash code for this attribute syntax.
     */
    @Override
    public int hashCode() {
        return oid.hashCode();
    }

    /**
     * Indicates whether this attribute syntax requires that values must be
     * encoded using the Basic Encoding Rules (BER) used by X.500 directories
     * and always include the {@code binary} attribute description option.
     *
     * @return {@code true} this attribute syntax requires that values must be
     *         BER encoded and always include the {@code binary} attribute
     *         description option, or {@code false} if not.
     * @see <a href="http://tools.ietf.org/html/rfc4522">RFC 4522 - Lightweight
     *      Directory Access Protocol (LDAP): The Binary Encoding Option </a>
     */
    public boolean isBEREncodingRequired() {
        return impl.isBEREncodingRequired();
    }

    /**
     * Indicates whether this attribute syntax would likely be a human readable
     * string.
     *
     * @return {@code true} if this attribute syntax would likely be a human
     *         readable string or {@code false} if not.
     */
    public boolean isHumanReadable() {
        return impl.isHumanReadable();
    }

    /**
     * Indicates whether the provided value is acceptable for use in an
     * attribute with this syntax. If it is not, then the reason may be appended
     * to the provided buffer.
     *
     * @param value
     *            The value for which to make the determination.
     * @param invalidReason
     *            The buffer to which the invalid reason should be appended.
     * @return {@code true} if the provided value is acceptable for use with
     *         this syntax, or {@code false} if not.
     */
    public boolean valueIsAcceptable(final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        return impl.valueIsAcceptable(schema, value, invalidReason);
    }

    @Override
    void toStringContent(final StringBuilder buffer) {
        buffer.append(oid);
        appendDescription(buffer);
    }

    void validate(final Schema schema, final List<LocalizableMessage> warnings)
            throws SchemaException {
        this.schema = schema;
        if (impl == null) {
            // See if we need to override the implementation of the syntax
            for (final Map.Entry<String, List<String>> property : getExtraProperties().entrySet()) {
                // Enums are handled in the schema builder.
                if ("x-subst".equalsIgnoreCase(property.getKey())) {
                    /**
                     * One unimplemented syntax can be substituted by another
                     * defined syntax. A substitution syntax is an
                     * LDAPSyntaxDescriptionSyntax with X-SUBST extension.
                     */
                    final Iterator<String> values = property.getValue().iterator();
                    if (values.hasNext()) {
                        final String value = values.next();
                        if (value.equals(oid)) {
                            throw new SchemaException(ERR_ATTR_SYNTAX_CYCLIC_SUB_SYNTAX.get(oid));
                        }
                        if (!schema.hasSyntax(value)) {
                            throw new SchemaException(ERR_ATTR_SYNTAX_UNKNOWN_SUB_SYNTAX.get(oid, value));
                        }
                        final Syntax subSyntax = schema.getSyntax(value);
                        if (subSyntax.impl == null) {
                            // The substitution syntax was never validated.
                            subSyntax.validate(schema, warnings);
                        }
                        impl = subSyntax.impl;
                    }
                } else if ("x-pattern".equalsIgnoreCase(property.getKey())) {
                    final Iterator<String> values = property.getValue().iterator();
                    if (values.hasNext()) {
                        final String value = values.next();
                        try {
                            final Pattern pattern = Pattern.compile(value);
                            impl = new RegexSyntaxImpl(pattern);
                        } catch (final Exception e) {
                            throw new SchemaException(
                                    WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_PATTERN.get(oid, value));
                        }
                    }
                }
            }

            // Try to find an implementation in the core schema
            if (impl == null && Schema.getDefaultSchema().hasSyntax(oid)) {
                impl = Schema.getDefaultSchema().getSyntax(oid).impl;
            }
            if (impl == null && Schema.getCoreSchema().hasSyntax(oid)) {
                impl = Schema.getCoreSchema().getSyntax(oid).impl;
            }

            if (impl == null) {
                final Syntax defaultSyntax = schema.getDefaultSyntax();
                if (defaultSyntax.impl == null) {
                    // The default syntax was never validated.
                    defaultSyntax.validate(schema, warnings);
                }
                impl = defaultSyntax.impl;
                final LocalizableMessage message = WARN_ATTR_SYNTAX_NOT_IMPLEMENTED1.get(getDescription(), oid, schema
                        .getDefaultSyntax().getOID());
                warnings.add(message);
            }
        }

        // Get references to the default matching rules. It will be ok
        // if we can't find some. Just warn.
        if (impl.getEqualityMatchingRule() != null) {
            if (schema.hasMatchingRule(impl.getEqualityMatchingRule())) {
                equalityMatchingRule = schema.getMatchingRule(impl.getEqualityMatchingRule());
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(impl
                                .getEqualityMatchingRule(), impl.getName());
                warnings.add(message);
            }
        }

        if (impl.getOrderingMatchingRule() != null) {
            if (schema.hasMatchingRule(impl.getOrderingMatchingRule())) {
                orderingMatchingRule = schema.getMatchingRule(impl.getOrderingMatchingRule());
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(impl
                                .getOrderingMatchingRule(), impl.getName());
                warnings.add(message);
            }
        }

        if (impl.getSubstringMatchingRule() != null) {
            if (schema.hasMatchingRule(impl.getSubstringMatchingRule())) {
                substringMatchingRule = schema.getMatchingRule(impl.getSubstringMatchingRule());
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(impl
                                .getSubstringMatchingRule(), impl.getName());
                warnings.add(message);
            }
        }

        if (impl.getApproximateMatchingRule() != null) {
            if (schema.hasMatchingRule(impl.getApproximateMatchingRule())) {
                approximateMatchingRule = schema.getMatchingRule(impl.getApproximateMatchingRule());
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE.get(impl
                                .getApproximateMatchingRule(), impl.getName());
                warnings.add(message);
            }
        }
    }

    /**
     * Indicates if the syntax has been validated, which means it has a non-null
     * schema.
     *
     * @return {@code true} if and only if this syntax has been validated
     */
    boolean isValidated() {
        return schema != null;
    }
}
