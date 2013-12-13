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
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

import com.forgerock.opendj.util.Validator;

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
    private final String oid;
    private final String definition;
    private MatchingRule equalityMatchingRule;
    private MatchingRule orderingMatchingRule;
    private MatchingRule substringMatchingRule;
    private MatchingRule approximateMatchingRule;
    private Schema schema;
    private SyntaxImpl impl;

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
        super("", Collections.singletonMap("X-SUBST", Collections.singletonList(schema
                .getDefaultSyntax().getOID())));

        Validator.ensureNotNull(oid);
        this.oid = oid;
        this.definition = buildDefinition();
        this.schema = schema;
        this.impl = schema.getDefaultSyntax().impl;
    }

    Syntax(final String oid, final String description,
            final Map<String, List<String>> extraProperties, final String definition,
            final SyntaxImpl implementation) {
        super(description, extraProperties);

        Validator.ensureNotNull(oid);
        this.oid = oid;

        if (definition != null) {
            this.definition = definition;
        } else {
            this.definition = buildDefinition();
        }
        this.impl = implementation;
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
     * Retrieves a string representation of this attribute syntax in the format
     * defined in RFC 2252.
     *
     * @return A string representation of this attribute syntax in the format
     *         defined in RFC 2252.
     */
    @Override
    public String toString() {
        return definition;
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

    Syntax duplicate() {
        return new Syntax(oid, description, extraProperties, definition, impl);
    }

    @Override
    void toStringContent(final StringBuilder buffer) {
        buffer.append(oid);

        if (description != null && description.length() > 0) {
            buffer.append(" DESC '");
            buffer.append(description);
            buffer.append("'");
        }
    }

    void validate(final Schema schema, final List<LocalizableMessage> warnings)
            throws SchemaException {
        this.schema = schema;
        if (impl == null) {
            // See if we need to override the implementation of the syntax
            for (final Map.Entry<String, List<String>> property : extraProperties.entrySet()) {
                // Enums are handled in the schema builder.
                if (property.getKey().equalsIgnoreCase("x-subst")) {
                    /**
                     * One unimplemented syntax can be substituted by another
                     * defined syntax. A substitution syntax is an
                     * LDAPSyntaxDescriptionSyntax with X-SUBST extension.
                     */
                    final Iterator<String> values = property.getValue().iterator();
                    if (values.hasNext()) {
                        final String value = values.next();
                        if (value.equals(oid)) {
                            final LocalizableMessage message =
                                    ERR_ATTR_SYNTAX_CYCLIC_SUB_SYNTAX.get(oid);
                            throw new SchemaException(message);
                        }
                        if (!schema.hasSyntax(value)) {
                            final LocalizableMessage message =
                                    ERR_ATTR_SYNTAX_UNKNOWN_SUB_SYNTAX.get(oid, value);
                            throw new SchemaException(message);
                        }
                        final Syntax subSyntax = schema.getSyntax(value);
                        if (subSyntax.impl == null) {
                            // The substitution syntax was never validated.
                            subSyntax.validate(schema, warnings);
                        }
                        impl = subSyntax.impl;
                    }
                } else if (property.getKey().equalsIgnoreCase("x-pattern")) {
                    final Iterator<String> values = property.getValue().iterator();
                    if (values.hasNext()) {
                        final String value = values.next();
                        try {
                            final Pattern pattern = Pattern.compile(value);
                            impl = new RegexSyntaxImpl(pattern);
                        } catch (final Exception e) {
                            final LocalizableMessage message =
                                    WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_PATTERN.get(oid,
                                            value);
                            throw new SchemaException(message);
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
                final LocalizableMessage message = WARN_ATTR_SYNTAX_NOT_IMPLEMENTED1
                        .get(getDescription(), oid, schema.getDefaultSyntax()
                                .getOID());
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
}
