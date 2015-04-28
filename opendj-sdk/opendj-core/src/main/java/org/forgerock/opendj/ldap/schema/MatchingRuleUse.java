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
 *      Portions copyright 2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import static java.util.Arrays.*;

import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.util.Reject;

/**
 * This class defines a data structure for storing and interacting with a
 * matching rule use definition, which may be used to restrict the set of
 * attribute types that may be used for a given matching rule.
 */
public final class MatchingRuleUse extends SchemaElement {

    /** A fluent API for incrementally constructing matching rule uses. */
    public static final class Builder extends SchemaElementBuilder<Builder> {
        private String oid;
        private final List<String> names = new LinkedList<>();
        private boolean isObsolete;
        private final Set<String> attributeOIDs = new LinkedHashSet<>();

        Builder(MatchingRuleUse mru, SchemaBuilder builder) {
            super(builder, mru);
            this.oid = mru.oid;
            this.names.addAll(mru.names);
            this.isObsolete = mru.isObsolete;
            this.attributeOIDs.addAll(mru.attributeOIDs);
        }

        Builder(final String oid, final SchemaBuilder builder) {
            super(builder);
            this.oid = oid;
        }

        /**
         * Adds this matching rule use definition to the schema, throwing a
         * {@code  ConflictingSchemaElementException} if there is an existing
         * matching rule definition with the same numeric OID.
         *
         * @return The parent schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing matching rule use definition with
         *             the same numeric OID.
         */
        public SchemaBuilder addToSchema() {
            return getSchemaBuilder().addMatchingRuleUse(new MatchingRuleUse(this), false);
        }

        /**
         * Adds this matching rule use definition to the schema overwriting any
         * existing matching rule use definition with the same numeric OID.
         *
         * @return The parent schema builder.
         */
        public SchemaBuilder addToSchemaOverwrite() {
            return getSchemaBuilder().addMatchingRuleUse(new MatchingRuleUse(this), true);
        }

        /**
         * Adds the provided list of attribute types to the list of attribute
         * type the matching rule applies to.
         *
         * @param attributeOIDs
         *            The list of attribute type numeric OIDs.
         * @return This builder.
         */
        public Builder attributes(Collection<String> attributeOIDs) {
            this.attributeOIDs.addAll(attributeOIDs);
            return this;
        }

        /**
         * Adds the provided list of attribute types to the list of attribute
         * type the matching rule applies to.
         *
         * @param attributeOIDs
         *            The list of attribute type numeric OIDs.
         * @return This builder.
         */
        public Builder attributes(String... attributeOIDs) {
            this.attributeOIDs.addAll(asList(attributeOIDs));
            return this;
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

        @Override
        Builder getThis() {
            return this;
        }

        /**
         * Adds the provided user friendly names.
         *
         * @param names
         *            The user friendly names.
         * @return This builder.
         */
        public Builder names(final Collection<String> names) {
            this.names.addAll(names);
            return this;
        }

        /**
         * Adds the provided user friendly names.
         *
         * @param names
         *            The user friendly names.
         * @return This builder.
         */
        public Builder names(final String... names) {
            return names(asList(names));
        }

        /**
         * Specifies whether this schema element is obsolete.
         *
         * @param isObsolete
         *            {@code true} if this schema element is obsolete
         *            (default is {@code false}).
         * @return This builder.
         */
        public Builder obsolete(final boolean isObsolete) {
            this.isObsolete = isObsolete;
            return this;
        }

        /**
         * Sets the numeric OID which uniquely identifies this matching rule use
         * definition.
         *
         * @param oid
         *            The numeric OID.
         * @return This builder.
         */
        public Builder oid(final String oid) {
            this.oid = oid;
            return this;
        }

        /**
         * Removes all attribute types the matching rule applies to.
         *
         * @return This builder.
         */
        public Builder removeAllAttributes() {
            this.attributeOIDs.clear();
            return this;
        }

        @Override
        public Builder removeAllExtraProperties() {
            return removeAllExtraProperties0();
        }

        /**
         * Removes all user defined names.
         *
         * @return This builder.
         */
        public Builder removeAllNames() {
            this.names.clear();
            return this;
        }

        /**
         * Removes the provided attribute type.
         *
         * @param attributeOID
         *            The attribute type OID to be removed.
         * @return This builder.
         */
        public Builder removeAttribute(String attributeOID) {
            this.attributeOIDs.remove(attributeOID);
            return this;
        }

        @Override
        public Builder removeExtraProperty(String extensionName, String... extensionValues) {
            return removeExtraProperty0(extensionName, extensionValues);
        }

        /**
         * Removes the provided user defined name.
         *
         * @param name
         *            The user defined name to be removed.
         * @return This builder.
         */
        public Builder removeName(String name) {
            this.names.remove(name);
            return this;
        }

    }

    /**
     * The OID of the matching rule associated with this matching rule
     * use definition.
     */
    private final String oid;

    /** The set of user defined names for this definition. */
    private final List<String> names;

    /** Indicates whether this definition is declared "obsolete". */
    private final boolean isObsolete;

    /**
     * The set of attribute types with which this matching rule use is
     * associated.
     */
    private final Set<String> attributeOIDs;

    private MatchingRule matchingRule;
    private Set<AttributeType> attributes = Collections.emptySet();

    private MatchingRuleUse(final Builder builder) {
        super(builder);
        Reject.ifNull(builder.oid);

        this.oid = builder.oid;
        this.names = unmodifiableCopyOfList(builder.names);
        this.isObsolete = builder.isObsolete;
        this.attributeOIDs = unmodifiableCopyOfSet(builder.attributeOIDs);
    }

    /**
     * Returns {@code true} if the provided object is a matching rule use having
     * the same numeric OID as this matching rule use.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is a matching rule use having
     *         the same numeric OID as this matching rule use.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof MatchingRuleUse) {
            final MatchingRuleUse other = (MatchingRuleUse) o;
            return oid.equals(other.oid);
        } else {
            return false;
        }
    }

    /**
     * Returns an unmodifiable set containing the attributes associated with
     * this matching rule use.
     *
     * @return An unmodifiable set containing the attributes associated with
     *         this matching rule use.
     */
    public Set<AttributeType> getAttributes() {
        return attributes;
    }

    /**
     * Returns the matching rule for this matching rule use.
     *
     * @return The matching rule for this matching rule use.
     */
    public MatchingRule getMatchingRule() {
        return matchingRule;
    }

    /**
     * Returns the matching rule OID for this schema definition.
     *
     * @return The OID for this schema definition.
     */
    public String getMatchingRuleOID() {
        return oid;
    }

    /**
     * Returns the name or matching rule OID for this schema definition. If it
     * has one or more names, then the primary name will be returned. If it does
     * not have any names, then the OID will be returned.
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
     * Indicates whether the provided attribute type is referenced by this
     * matching rule use.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return {@code true} if the provided attribute type is referenced by this
     *         matching rule use, or {@code false} if it is not.
     */
    public boolean hasAttribute(final AttributeType attributeType) {
        return attributes.contains(attributeType);
    }

    /**
     * Returns the hash code for this matching rule use. It will be calculated
     * as the hash code of the numeric OID.
     *
     * @return The hash code for this matching rule use.
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
     * Indicates whether this schema definition has the specified name or
     * matching rule OID.
     *
     * @param value
     *            The value for which to make the determination.
     * @return <code>true</code> if the provided value matches the OID or one of
     *         the names assigned to this schema definition, or
     *         <code>false</code> if not.
     */
    public boolean hasNameOrOID(final String value) {
        return hasName(value) || oid.equals(value);
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

        appendDescription(buffer);

        if (isObsolete) {
            buffer.append(" OBSOLETE");
        }

        if (!attributeOIDs.isEmpty()) {
            final Iterator<String> iterator = attributeOIDs.iterator();

            final String firstName = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" APPLIES ( ");
                buffer.append(firstName);

                while (iterator.hasNext()) {
                    buffer.append(" $ ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" APPLIES ");
                buffer.append(firstName);
            }
        }
    }

    void validate(final Schema schema, final List<LocalizableMessage> warnings)
            throws SchemaException {
        try {
            matchingRule = schema.getMatchingRule(oid);
        } catch (final UnknownSchemaElementException e) {
            // This is bad because the matching rule use is associated with a
            // matching rule that we don't know anything about.
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_MRUSE_UNKNOWN_MATCHING_RULE1.get(getNameOrOID(), oid);
            throw new SchemaException(message, e);
        }

        attributes = new HashSet<>(attributeOIDs.size());
        for (final String attribute : attributeOIDs) {
            try {
                attributes.add(schema.getAttributeType(attribute));
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_MRUSE_UNKNOWN_ATTR1.get(getNameOrOID(), attribute);
                throw new SchemaException(message, e);
            }
        }
        attributes = Collections.unmodifiableSet(attributes);
    }
}
