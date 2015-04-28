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
 * This class defines a DIT structure rule, which is used to indicate the types
 * of children that entries may have.
 */
public final class DITStructureRule extends SchemaElement {

    /** A fluent API for incrementally constructing DIT structure rules. */
    public static final class Builder extends SchemaElementBuilder<Builder> {
        private int ruleID;
        private final List<String> names = new LinkedList<>();
        private boolean isObsolete;
        private String nameFormOID;
        private final Set<Integer> superiorRuleIDs = new LinkedHashSet<>();

        Builder(final DITStructureRule structureRule, final SchemaBuilder builder) {
            super(builder);
            this.ruleID = structureRule.ruleID;
            this.names.addAll(structureRule.names);
            this.isObsolete = structureRule.isObsolete;
            this.nameFormOID = structureRule.nameFormOID;
            this.superiorRuleIDs.addAll(structureRule.superiorRuleIDs);
        }

        Builder(final Integer ruleID, final SchemaBuilder schemaBuilder) {
            super(schemaBuilder);
            this.ruleID = ruleID;
        }

        /**
         * Adds this DIT structure rule to the schema, throwing a
         * {@code  ConflictingSchemaElementException} if there is an existing DIT
         * structure rule with the same numeric ID.
         *
         * @return The parent schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing structure rule with the same
         *             numeric ID.
         */
        public SchemaBuilder addToSchema() {
            return getSchemaBuilder().addDITStructureRule(new DITStructureRule(this), false);
        }

        /**
         * Adds this DIT structure rule to the schema overwriting any existing
         * DIT structure rule with the same numeric ID.
         *
         * @return The parent schema builder.
         */
        public SchemaBuilder addToSchemaOverwrite() {
            return getSchemaBuilder().addDITStructureRule(new DITStructureRule(this), true);
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
         * Sets the name form associated with the DIT structure rule.
         *
         * @param nameFormOID
         *            The name form numeric OID.
         * @return This builder.
         */
        public Builder nameForm(final String nameFormOID) {
            this.nameFormOID = nameFormOID;
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
         * Removes all superior rules.
         *
         * @return This builder.
         */
        public Builder removeAllSuperiorRules() {
            this.superiorRuleIDs.clear();
            return this;
        }

        @Override
        public Builder removeExtraProperty(final String extensionName, final String... extensionValues) {
            return removeExtraProperty0(extensionName, extensionValues);
        }

        /**
         * Removes the provided user defined name.
         *
         * @param name
         *            The user defined name to be removed.
         * @return This builder.
         */
        public Builder removeName(final String name) {
            this.names.remove(name);
            return this;
        }

        /**
         * Removes the provided superior rule.
         *
         * @param superiorRuleID
         *            The superior rule ID to be removed.
         * @return This builder.
         */
        public Builder removeSuperiorRule(final int superiorRuleID) {
            this.superiorRuleIDs.remove(superiorRuleID);
            return this;
        }

        /**
         * Sets the the numeric ID which uniquely identifies this structure rule.
         *
         * @param ruleID
         *            The numeric ID.
         * @return This builder.
         */
        public Builder ruleID(final int ruleID) {
            this.ruleID = ruleID;
            return this;
        }

        /**
         * Adds the provided superior rule identifiers.
         *
         * @param superiorRuleIDs
         *            Structure rule identifiers.
         * @return This builder.
         */
        public Builder superiorRules(final int... superiorRuleIDs) {
            for (int ruleID : superiorRuleIDs) {
                this.superiorRuleIDs.add(ruleID);
            }
            return this;
        }

        Builder superiorRules(final Collection<Integer> superiorRuleIDs) {
            this.superiorRuleIDs.addAll(superiorRuleIDs);
            return this;
        }

    }

    /** The rule ID for this DIT structure rule. */
    private final Integer ruleID;

    /** The set of user defined names for this definition. */
    private final List<String> names;

    /** Indicates whether this definition is declared "obsolete". */
    private final boolean isObsolete;

    /** The name form for this DIT structure rule. */
    private final String nameFormOID;

    /** The set of superior DIT structure rules. */
    private final Set<Integer> superiorRuleIDs;

    private NameForm nameForm;
    private Set<DITStructureRule> superiorRules = Collections.emptySet();

    /** Indicates whether or not validation has been performed. */
    private boolean needsValidating = true;

    /** The indicates whether or not validation failed. */
    private boolean isValid;

    DITStructureRule(final Builder builder) {
        super(builder);
        Reject.ifNull(builder.nameFormOID);

        this.ruleID = builder.ruleID;
        this.names = unmodifiableCopyOfList(builder.names);
        this.isObsolete = builder.isObsolete;
        this.nameFormOID = builder.nameFormOID;
        this.superiorRuleIDs = unmodifiableCopyOfSet(builder.superiorRuleIDs);
    }

    /**
     * Returns {@code true} if the provided object is a DIT structure rule
     * having the same rule ID as this DIT structure rule.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is a DIT structure rule
     *         having the same rule ID as this DIT structure rule.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof DITStructureRule) {
            final DITStructureRule other = (DITStructureRule) o;
            return ruleID.equals(other.ruleID);
        } else {
            return false;
        }
    }

    /**
     * Retrieves the name form for this DIT structure rule.
     *
     * @return The name form for this DIT structure rule.
     */
    public NameForm getNameForm() {
        return nameForm;
    }

    /**
     * Retrieves the name or rule ID for this schema definition. If it has one
     * or more names, then the primary name will be returned. If it does not
     * have any names, then the OID will be returned.
     *
     * @return The name or OID for this schema definition.
     */
    public String getNameOrRuleID() {
        if (names.isEmpty()) {
            return ruleID.toString();
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
     * Retrieves the rule ID for this DIT structure rule.
     *
     * @return The rule ID for this DIT structure rule.
     */
    public Integer getRuleID() {
        return ruleID;
    }

    /**
     * Returns an unmodifiable set containing the superior rules for this DIT
     * structure rule.
     *
     * @return An unmodifiable set containing the superior rules for this DIT
     *         structure rule.
     */
    public Set<DITStructureRule> getSuperiorRules() {
        return superiorRules;
    }

    /**
     * Returns the hash code for this DIT structure rule. It will be calculated
     * as the hash code of the rule ID.
     *
     * @return The hash code for this DIT structure rule.
     */
    @Override
    public int hashCode() {
        return ruleID.hashCode();
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
        buffer.append(ruleID);

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

        buffer.append(" FORM ");
        buffer.append(nameFormOID);

        if (superiorRuleIDs != null && !superiorRuleIDs.isEmpty()) {
            final Iterator<Integer> iterator = superiorRuleIDs.iterator();

            final Integer firstRule = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" SUP ( ");
                buffer.append(firstRule);

                while (iterator.hasNext()) {
                    buffer.append(" ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" SUP ");
                buffer.append(firstRule);
            }
        }
    }

    boolean validate(final Schema schema, final List<DITStructureRule> invalidSchemaElements,
            final List<LocalizableMessage> warnings) {
        // Avoid validating this schema element more than once. This may occur
        // if
        // multiple rules specify the same superior.
        if (!needsValidating) {
            return isValid;
        }

        // Prevent re-validation.
        needsValidating = false;

        try {
            nameForm = schema.getNameForm(nameFormOID);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DSR_UNKNOWN_NAME_FORM.get(getNameOrRuleID(), nameFormOID);
            failValidation(invalidSchemaElements, warnings, message);
            return false;
        }

        if (!superiorRuleIDs.isEmpty()) {
            superiorRules = new HashSet<>(superiorRuleIDs.size());
            for (final Integer id : superiorRuleIDs) {
                try {
                    superiorRules.add(schema.getDITStructureRule(id));
                } catch (final UnknownSchemaElementException e) {
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID.get(getNameOrRuleID(), id);
                    failValidation(invalidSchemaElements, warnings, message);
                    return false;
                }
            }
        }
        superiorRules = Collections.unmodifiableSet(superiorRules);

        return isValid = true;
    }

    private void failValidation(final List<DITStructureRule> invalidSchemaElements,
            final List<LocalizableMessage> warnings, final LocalizableMessage message) {
        invalidSchemaElements.add(this);
        warnings.add(ERR_DSR_VALIDATION_FAIL.get(toString(), message));
    }
}
