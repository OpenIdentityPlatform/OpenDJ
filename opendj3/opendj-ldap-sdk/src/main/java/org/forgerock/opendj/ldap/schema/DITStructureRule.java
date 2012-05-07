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
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_DSR_UNKNOWN_NAME_FORM;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_DSR_VALIDATION_FAIL;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;

import com.forgerock.opendj.util.Validator;

/**
 * This class defines a DIT structure rule, which is used to indicate the types
 * of children that entries may have.
 */
public final class DITStructureRule extends SchemaElement {
    // The rule ID for this DIT structure rule.
    private final Integer ruleID;

    // The set of user defined names for this definition.
    private final List<String> names;

    // Indicates whether this definition is declared "obsolete".
    private final boolean isObsolete;

    // The name form for this DIT structure rule.
    private final String nameFormOID;

    // The set of superior DIT structure rules.
    private final Set<Integer> superiorRuleIDs;

    // The definition string used to create this objectclass.
    private final String definition;

    private NameForm nameForm;
    private Set<DITStructureRule> superiorRules = Collections.emptySet();

    // Indicates whether or not validation has been performed.
    private boolean needsValidating = true;

    // The indicates whether or not validation failed.
    private boolean isValid = false;

    DITStructureRule(final Integer ruleID, final List<String> names, final String description,
            final boolean obsolete, final String nameFormOID, final Set<Integer> superiorRuleIDs,
            final Map<String, List<String>> extraProperties, final String definition) {
        super(description, extraProperties);

        Validator.ensureNotNull(ruleID, nameFormOID, superiorRuleIDs);
        this.ruleID = ruleID;
        this.names = names;
        this.isObsolete = obsolete;
        this.nameFormOID = nameFormOID;
        this.superiorRuleIDs = superiorRuleIDs;

        if (definition != null) {
            this.definition = definition;
        } else {
            this.definition = buildDefinition();
        }
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

    /**
     * Retrieves the string representation of this schema definition in the form
     * specified in RFC 2252.
     *
     * @return The string representation of this schema definition in the form
     *         specified in RFC 2252.
     */
    @Override
    public String toString() {
        return definition;
    }

    DITStructureRule duplicate() {
        return new DITStructureRule(ruleID, names, description, isObsolete, nameFormOID,
                superiorRuleIDs, extraProperties, definition);
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

        if (description != null && description.length() > 0) {
            buffer.append(" DESC '");
            buffer.append(description);
            buffer.append("'");
        }

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
            superiorRules = new HashSet<DITStructureRule>(superiorRuleIDs.size());
            DITStructureRule rule;
            for (final Integer id : superiorRuleIDs) {
                try {
                    rule = schema.getDITStructureRule(id);
                } catch (final UnknownSchemaElementException e) {
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID.get(getNameOrRuleID(), id);
                    failValidation(invalidSchemaElements, warnings, message);
                    return false;
                }
                superiorRules.add(rule);
            }
        }
        superiorRules = Collections.unmodifiableSet(superiorRules);

        return (isValid = true);
    }

    private void failValidation(final List<DITStructureRule> invalidSchemaElements,
            final List<LocalizableMessage> warnings, final LocalizableMessage message) {
        invalidSchemaElements.add(this);
        warnings.add(ERR_DSR_VALIDATION_FAIL.get(toString(), message));
    }
}
