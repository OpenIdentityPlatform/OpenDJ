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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_MR_UNKNOWN_SYNTAX1;
import static org.forgerock.opendj.ldap.CoreMessages.WARN_MATCHING_RULE_NOT_IMPLEMENTED1;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.Validator;

/**
 * This class defines a data structure for storing and interacting with matching
 * rules, which are used by servers to compare attribute values against
 * assertion values when performing Search and Compare operations. They are also
 * used to identify the value to be added or deleted when modifying entries, and
 * are used when comparing a purported distinguished name with the name of an
 * entry.
 * <p>
 * Matching rule implementations must extend the
 * <code>MatchingRuleImplementation</code> class so they can be used by OpenDJ.
 * <p>
 * Where ordered sets of names, or extra properties are provided, the ordering
 * will be preserved when the associated fields are accessed via their getters
 * or via the {@link #toString()} methods.
 */
public final class MatchingRule extends SchemaElement {
    private final String oid;
    private final List<String> names;
    private final boolean isObsolete;
    private final String syntaxOID;
    private final String definition;
    private MatchingRuleImpl impl;
    private Syntax syntax;
    private Schema schema;

    MatchingRule(final String oid, final List<String> names, final String description,
            final boolean obsolete, final String syntax,
            final Map<String, List<String>> extraProperties, final String definition,
            final MatchingRuleImpl implementation) {
        super(description, extraProperties);

        Validator.ensureNotNull(oid, names, description, syntax);
        Validator.ensureNotNull(extraProperties);
        this.oid = oid;
        this.names = names;
        this.isObsolete = obsolete;
        this.syntaxOID = syntax;

        if (definition != null) {
            this.definition = definition;
        } else {
            this.definition = buildDefinition();
        }
        this.impl = implementation;
    }

    /**
     * Get a comparator that can be used to compare the attribute values
     * normalized by this matching rule.
     *
     * @return A comparator that can be used to compare the attribute values
     *         normalized by this matching rule.
     */
    public Comparator<ByteSequence> comparator() {
        return impl.comparator(schema);
    }

    /**
     * Returns {@code true} if the provided object is a matching rule having the
     * same numeric OID as this matching rule.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is a matching rule having the
     *         same numeric OID as this matching rule.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof MatchingRule) {
            final MatchingRule other = (MatchingRule) o;
            return oid.equals(other.oid);
        } else {
            return false;
        }
    }

    /**
     * Returns the normalized form of the provided assertion value, which is
     * best suite for efficiently performing matching operations on that value.
     * The assertion value is guarenteed to be valid against this matching
     * rule's assertion syntax.
     *
     * @param value
     *            The syntax checked assertion value to be normalized.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if the syntax of the value is not valid.
     */
    public Assertion getAssertion(final ByteSequence value) throws DecodeException {
        return impl.getAssertion(schema, value);
    }

    /**
     * Returns the normalized form of the provided assertion substring values,
     * which is best suite for efficiently performing matching operations on
     * that value.
     *
     * @param subInitial
     *            The normalized substring value fragment that should appear at
     *            the beginning of the target value.
     * @param subAnyElements
     *            The normalized substring value fragments that should appear in
     *            the middle of the target value.
     * @param subFinal
     *            The normalized substring value fragment that should appear at
     *            the end of the target value.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if the syntax of the value is not valid.
     */
    public Assertion getAssertion(final ByteSequence subInitial,
            final List<? extends ByteSequence> subAnyElements, final ByteSequence subFinal)
            throws DecodeException {
        return impl.getAssertion(schema, subInitial, subAnyElements, subFinal);
    }

    /**
     * Returns the normalized form of the provided assertion value, which is
     * best suite for efficiently performing greater than or equal ordering
     * matching operations on that value. The assertion value is guarenteed to
     * be valid against this matching rule's assertion syntax.
     *
     * @param value
     *            The syntax checked assertion value to be normalized.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if the syntax of the value is not valid.
     */
    public Assertion getGreaterOrEqualAssertion(final ByteSequence value) throws DecodeException {
        return impl.getGreaterOrEqualAssertion(schema, value);
    }

    /**
     * Returns the normalized form of the provided assertion value, which is
     * best suite for efficiently performing greater than or equal ordering
     * matching operations on that value. The assertion value is guarenteed to
     * be valid against this matching rule's assertion syntax.
     *
     * @param value
     *            The syntax checked assertion value to be normalized.
     * @return The normalized version of the provided assertion value.
     * @throws DecodeException
     *             if the syntax of the value is not valid.
     */
    public Assertion getLessOrEqualAssertion(final ByteSequence value) throws DecodeException {
        return impl.getLessOrEqualAssertion(schema, value);
    }

    /**
     * Returns the name or OID for this schema definition. If it has one or more
     * names, then the primary name will be returned. If it does not have any
     * names, then the OID will be returned.
     *
     * @return The name or OID for this schema definition.
     */
    public String getNameOrOID() {
        if (names.isEmpty()) {
            return oid;
        }
        return names.get(0);
    }

    /**
     * Returns an unmodifiable list containing the user-defined names that may
     * be used to reference this schema definition.
     *
     * @return Returns an unmodifiable list containing the user-defined names
     *         that may be used to reference this schema definition.
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Returns the OID for this schema definition.
     *
     * @return The OID for this schema definition.
     */
    public String getOID() {

        return oid;
    }

    /**
     * Returns the OID of the assertion value syntax with which this matching
     * rule is associated.
     *
     * @return The OID of the assertion value syntax with which this matching
     *         rule is associated.
     */
    public Syntax getSyntax() {
        return syntax;
    }

    /**
     * Returns the hash code for this matching rule. It will be calculated as
     * the hash code of the numeric OID.
     *
     * @return The hash code for this matching rule.
     */
    @Override
    public int hashCode() {
        return oid.hashCode();
    }

    /**
     * Indicates whether this schema definition has the specified name.
     *
     * @param name
     *            The name for which to make the determination.
     * @return <code>true</code> if the specified name is assigned to this
     *         schema definition, or <code>false</code> if not.
     */
    public boolean hasName(final String name) {
        for (final String n : names) {
            if (n.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates whether this schema definition has the specified name or OID.
     *
     * @param value
     *            The value for which to make the determination.
     * @return <code>true</code> if the provided value matches the OID or one of
     *         the names assigned to this schema definition, or
     *         <code>false</code> if not.
     */
    public boolean hasNameOrOID(final String value) {
        return hasName(value) || getOID().equals(value);
    }

    /**
     * Indicates whether this schema definition is declared "obsolete".
     *
     * @return <code>true</code> if this schema definition is declared
     *         "obsolete", or <code>false</code> if not.
     */
    public boolean isObsolete() {
        return isObsolete;
    }

    /**
     * Returns the normalized form of the provided attribute value, which is
     * best suite for efficiently performing matching operations on that value.
     *
     * @param value
     *            The attribute value to be normalized.
     * @return The normalized version of the provided attribute value.
     * @throws DecodeException
     *             if the syntax of the value is not valid.
     */
    public ByteString normalizeAttributeValue(final ByteSequence value) throws DecodeException {
        return impl.normalizeAttributeValue(schema, value);
    }

    /**
     * Returns the string representation of this schema definition in the form
     * specified in RFC 2252.
     *
     * @return The string representation of this schema definition in the form
     *         specified in RFC 2252.
     */
    @Override
    public String toString() {
        return definition;
    }

    MatchingRule duplicate() {
        return new MatchingRule(oid, names, description, isObsolete, syntaxOID, extraProperties,
                definition, impl);
    }

    @Override
    void toStringContent(final StringBuilder buffer) {
        buffer.append(oid);

        if (!names.isEmpty()) {
            final Iterator<String> iterator = names.iterator();

            final String firstName = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" NAME ( '");
                buffer.append(firstName);

                while (iterator.hasNext()) {
                    buffer.append("' '");
                    buffer.append(iterator.next());
                }

                buffer.append("' )");
            } else {
                buffer.append(" NAME '");
                buffer.append(firstName);
                buffer.append("'");
            }
        }

        if (description != null && description.length() > 0) {
            buffer.append(" DESC '");
            buffer.append(description);
            buffer.append("'");
        }

        if (isObsolete) {
            buffer.append(" OBSOLETE");
        }

        buffer.append(" SYNTAX ");
        buffer.append(syntaxOID);
    }

    void validate(final Schema schema, final List<LocalizableMessage> warnings)
            throws SchemaException {
        // Try finding an implementation in the core schema
        if (impl == null && Schema.getDefaultSchema().hasMatchingRule(oid)) {
            impl = Schema.getDefaultSchema().getMatchingRule(oid).impl;
        }
        if (impl == null && Schema.getCoreSchema().hasMatchingRule(oid)) {
            impl = Schema.getCoreSchema().getMatchingRule(oid).impl;
        }

        if (impl == null) {
            final MatchingRule defaultMatchingRule = schema.getDefaultMatchingRule();
            if (defaultMatchingRule.impl == null) {
                // The default matching rule was never validated.
                defaultMatchingRule.validate(schema, warnings);
            }
            impl = defaultMatchingRule.impl;
            final LocalizableMessage message =
                    WARN_MATCHING_RULE_NOT_IMPLEMENTED1.get(getNameOrOID(), schema
                            .getDefaultMatchingRule().getOID());
            warnings.add(message);
        }

        try {
            // Make sure the specific syntax is defined in this schema.
            syntax = schema.getSyntax(syntaxOID);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_MR_UNKNOWN_SYNTAX1.get(getNameOrOID(), syntaxOID);
            throw new SchemaException(message, e);
        }

        this.schema = schema;
    }
}
