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
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SCHEMA_PROPERTY_APPROX_RULE;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * This class defines a data structure for storing and interacting with an
 * attribute type, which contains information about the format of an attribute
 * and the syntax and matching rules that should be used when interacting with
 * it.
 * <p>
 * Where ordered sets of names, or extra properties are provided, the ordering
 * will be preserved when the associated fields are accessed via their getters
 * or via the {@link #toString()} methods.
 */
public final class AttributeType extends SchemaElement implements Comparable<AttributeType> {

    // The approximate matching rule for this attribute type.
    private final String approximateMatchingRuleOID;

    // The attribute usage for this attribute type.
    private final AttributeUsage attributeUsage;

    // The definition string used to create this objectclass.
    private final String definition;

    // The equality matching rule for this attribute type.
    private final String equalityMatchingRuleOID;

    // Indicates whether this attribute type is declared "collective".
    private final boolean isCollective;

    // Indicates whether this attribute type is declared
    // "no-user-modification".
    private final boolean isNoUserModification;

    // Indicates whether this definition is declared "obsolete".
    private final boolean isObsolete;

    // Indicates whether this definition is a temporary place-holder.
    private final boolean isPlaceHolder;

    // Indicates whether this attribute type is declared "single-value".
    private final boolean isSingleValue;

    // The set of user defined names for this definition.
    private final List<String> names;

    // The OID that may be used to reference this definition.
    private final String oid;

    // The ordering matching rule for this attribute type.
    private final String orderingMatchingRuleOID;

    // The substring matching rule for this attribute type.
    private final String substringMatchingRuleOID;

    // The superior attribute type from which this attribute type
    // inherits.
    private final String superiorTypeOID;

    // The syntax for this attribute type.
    private final String syntaxOID;

    // True if this type has OID 2.5.4.0.
    private final boolean isObjectClassType;

    // The normalized name of this attribute type.
    private final String normalizedName;

    // The superior attribute type from which this attribute type
    // inherits.
    private AttributeType superiorType;

    // The equality matching rule for this attribute type.
    private MatchingRule equalityMatchingRule;

    // The ordering matching rule for this attribute type.
    private MatchingRule orderingMatchingRule;

    // The substring matching rule for this attribute type.
    private MatchingRule substringMatchingRule;

    // The approximate matching rule for this attribute type.
    private MatchingRule approximateMatchingRule;

    // The syntax for this attribute type.
    private Syntax syntax;

    // Indicates whether or not validation has been performed.
    private boolean needsValidating = true;

    // The indicates whether or not validation failed.
    private boolean isValid = false;

    AttributeType(final String oid, final List<String> names, final String description,
            final boolean obsolete, final String superiorType, final String equalityMatchingRule,
            final String orderingMatchingRule, final String substringMatchingRule,
            final String approximateMatchingRule, final String syntax, final boolean singleValue,
            final boolean collective, final boolean noUserModification,
            final AttributeUsage attributeUsage, final Map<String, List<String>> extraProperties,
            final String definition) {
        super(description, extraProperties);

        Validator.ensureNotNull(oid, names, description, attributeUsage);
        Validator.ensureTrue(superiorType != null || syntax != null,
                "superiorType and/or syntax must not be null");
        Validator.ensureNotNull(extraProperties);

        this.oid = oid;
        this.names = names;
        this.isObsolete = obsolete;
        this.superiorTypeOID = superiorType;
        this.equalityMatchingRuleOID = equalityMatchingRule;
        this.orderingMatchingRuleOID = orderingMatchingRule;
        this.substringMatchingRuleOID = substringMatchingRule;
        this.approximateMatchingRuleOID = approximateMatchingRule;
        this.syntaxOID = syntax;
        this.isSingleValue = singleValue;
        this.isCollective = collective;
        this.isNoUserModification = noUserModification;
        this.attributeUsage = attributeUsage;

        if (definition != null) {
            this.definition = definition;
        } else {
            this.definition = buildDefinition();
        }

        this.isObjectClassType = oid.equals("2.5.4.0");
        this.isPlaceHolder = false;
        this.normalizedName = StaticUtils.toLowerCase(getNameOrOID());
    }

    /**
     * Creates a new place-holder attribute type having the specified name,
     * default syntax, and default matching rule. The OID of the place-holder
     * attribute will be the normalized attribute type name followed by the
     * suffix "-oid".
     *
     * @param name
     *            The name of the place-holder attribute type.
     */
    AttributeType(final String name) {
        super("", Collections.<String, List<String>> emptyMap());

        final StringBuilder builder = new StringBuilder(name.length() + 4);
        StaticUtils.toLowerCase(name, builder);
        builder.append("-oid");

        this.oid = builder.toString();
        this.names = Collections.singletonList(name);
        this.isObsolete = false;
        this.superiorTypeOID = null;
        this.superiorType = null;
        this.equalityMatchingRule = Schema.getDefaultMatchingRule();
        this.equalityMatchingRuleOID = equalityMatchingRule.getOID();
        this.orderingMatchingRuleOID = null;
        this.substringMatchingRuleOID = null;
        this.approximateMatchingRuleOID = null;
        this.syntax = Schema.getDefaultSyntax();
        this.syntaxOID = syntax.getOID();
        this.isSingleValue = false;
        this.isCollective = false;
        this.isNoUserModification = false;
        this.attributeUsage = AttributeUsage.USER_APPLICATIONS;
        this.definition = buildDefinition();
        this.isObjectClassType = false;
        this.isPlaceHolder = true;
        this.normalizedName = StaticUtils.toLowerCase(getNameOrOID());
    }

    /**
     * Compares this attribute type to the provided attribute type. The
     * sort-order is defined as follows:
     * <ul>
     * <li>The {@code objectClass} attribute is less than all other attribute
     * types.
     * <li>User attributes are less than operational attributes.
     * <li>Lexicographic comparison of the primary name and then, if equal, the
     * OID.
     * </ul>
     *
     * @param type
     *            The attribute type to be compared.
     * @return A negative integer, zero, or a positive integer as this attribute
     *         type is less than, equal to, or greater than the specified
     *         attribute type.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    public int compareTo(final AttributeType type) {
        if (isObjectClassType) {
            return type.isObjectClassType ? 0 : -1;
        } else if (type.isObjectClassType) {
            return 1;
        } else {
            final boolean isOperational = getUsage().isOperational();
            final boolean typeIsOperational = type.getUsage().isOperational();
            if (isOperational == typeIsOperational) {
                final int tmp = normalizedName.compareTo(type.normalizedName);
                if (tmp == 0) {
                    return oid.compareTo(type.oid);
                } else {
                    return tmp;
                }
            } else {
                return isOperational ? 1 : -1;
            }
        }
    }

    /**
     * Returns {@code true} if the provided object is an attribute type having
     * the same numeric OID as this attribute type.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is an attribute type having
     *         the same numeric OID as this attribute type.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof AttributeType) {
            final AttributeType other = (AttributeType) o;
            return oid.equals(other.oid);
        } else {
            return false;
        }
    }

    /**
     * Returns the matching rule that should be used for approximate matching
     * with this attribute type.
     *
     * @return The matching rule that should be used for approximate matching
     *         with this attribute type.
     */
    public MatchingRule getApproximateMatchingRule() {
        return approximateMatchingRule;
    }

    /**
     * Returns the matching rule that should be used for equality matching with
     * this attribute type.
     *
     * @return The matching rule that should be used for equality matching with
     *         this attribute type.
     */
    public MatchingRule getEqualityMatchingRule() {
        return equalityMatchingRule;
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
     * Returns the matching rule that should be used for ordering with this
     * attribute type.
     *
     * @return The matching rule that should be used for ordering with this
     *         attribute type.
     */
    public MatchingRule getOrderingMatchingRule() {
        return orderingMatchingRule;
    }

    /**
     * Returns the matching rule that should be used for substring matching with
     * this attribute type.
     *
     * @return The matching rule that should be used for substring matching with
     *         this attribute type.
     */
    public MatchingRule getSubstringMatchingRule() {
        return substringMatchingRule;
    }

    /**
     * Returns the superior type for this attribute type.
     *
     * @return The superior type for this attribute type, or <CODE>null</CODE>
     *         if it does not have one.
     */
    public AttributeType getSuperiorType() {
        return superiorType;
    }

    /**
     * Returns the syntax for this attribute type.
     *
     * @return The syntax for this attribute type.
     */
    public Syntax getSyntax() {
        return syntax;
    }

    /**
     * Returns the usage indicator for this attribute type.
     *
     * @return The usage indicator for this attribute type.
     */
    public AttributeUsage getUsage() {
        return attributeUsage;
    }

    /**
     * Returns the hash code for this attribute type. It will be calculated as
     * the hash code of the numeric OID.
     *
     * @return The hash code for this attribute type.
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
     * @return {@code true} if the specified name is assigned to this schema
     *         definition, or {@code false} if not.
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
     * @return {@code true} if the provided value matches the OID or one of the
     *         names assigned to this schema definition, or {@code false} if
     *         not.
     */
    public boolean hasNameOrOID(final String value) {
        return hasName(value) || getOID().equals(value);
    }

    /**
     * Indicates whether this attribute type is declared "collective".
     *
     * @return {@code true} if this attribute type is declared "collective", or
     *         {@code false} if not.
     */
    public boolean isCollective() {
        return isCollective;
    }

    /**
     * Indicates whether this attribute type is declared "no-user-modification".
     *
     * @return {@code true} if this attribute type is declared
     *         "no-user-modification", or {@code false} if not.
     */
    public boolean isNoUserModification() {
        return isNoUserModification;
    }

    /**
     * Indicates whether or not this attribute type is the {@code objectClass}
     * attribute type having the OID 2.5.4.0.
     *
     * @return {@code true} if this attribute type is the {@code objectClass}
     *         attribute type, or {@code false} if not.
     */
    public boolean isObjectClass() {
        return isObjectClassType;
    }

    /**
     * Indicates whether this schema definition is declared "obsolete".
     *
     * @return {@code true} if this schema definition is declared "obsolete", or
     *         {@code false} if not.
     */
    public boolean isObsolete() {
        return isObsolete;
    }

    /**
     * Indicates whether this is an operational attribute. An operational
     * attribute is one with a usage of "directoryOperation",
     * "distributedOperation", or "dSAOperation" (i.e., only userApplications is
     * not operational).
     *
     * @return {@code true} if this is an operational attribute, or
     *         {@code false} if not.
     */
    public boolean isOperational() {
        return attributeUsage.isOperational();
    }

    /**
     * Indicates whether this attribute type is a temporary place-holder
     * allocated dynamically by a non-strict schema when no registered attribute
     * type was found.
     * <p>
     * Place holder attribute types have an OID which is the normalized
     * attribute name with the string {@code -oid} appended. In addition, they
     * will use the directory string syntax and case ignore matching rule.
     *
     * @return {@code true} if this is a temporary place-holder attribute type
     *         allocated dynamically by a non-strict schema when no registered
     *         attribute type was found.
     * @see Schema#getAttributeType(String)
     */
    public boolean isPlaceHolder() {
        return isPlaceHolder;
    }

    /**
     * Indicates whether this attribute type is declared "single-value".
     *
     * @return {@code true} if this attribute type is declared "single-value",
     *         or {@code false} if not.
     */
    public boolean isSingleValue() {
        return isSingleValue;
    }

    /**
     * Indicates whether or not this attribute type is a sub-type of the
     * provided attribute type.
     *
     * @param type
     *            The attribute type for which to make the determination.
     * @return {@code true} if this attribute type is a sub-type of the provided
     *         attribute type, or {@code false} if not.
     * @throws NullPointerException
     *             If {@code type} was {@code null}.
     */
    public boolean isSubTypeOf(final AttributeType type) {
        AttributeType tmp = this;
        do {
            if (tmp.matches(type)) {
                return true;
            }
            tmp = tmp.getSuperiorType();
        } while (tmp != null);
        return false;
    }

    /**
     * Indicates whether or not this attribute type is a super-type of the
     * provided attribute type.
     *
     * @param type
     *            The attribute type for which to make the determination.
     * @return {@code true} if this attribute type is a super-type of the
     *         provided attribute type, or {@code false} if not.
     * @throws NullPointerException
     *             If {@code type} was {@code null}.
     */
    public boolean isSuperTypeOf(final AttributeType type) {
        return type.isSubTypeOf(this);
    }

    /**
     * Implements a place-holder tolerant version of {@link #equals}. This
     * method returns {@code true} in the following cases:
     * <ul>
     * <li>this attribute type is equal to the provided attribute type as
     * specified by {@link #equals}
     * <li>this attribute type is a place-holder and the provided attribute type
     * has a name which matches the name of this attribute type
     * <li>the provided attribute type is a place-holder and this attribute type
     * has a name which matches the name of the provided attribute type.
     * </ul>
     *
     * @param type
     *            The attribute type for which to make the determination.
     * @return {@code true} if the provided attribute type matches this
     *         attribute type.
     */
    public boolean matches(final AttributeType type) {
        if (this == type) {
            return true;
        } else if (oid.equals(type.oid)) {
            return true;
        } else if (isPlaceHolder != type.isPlaceHolder) {
            return isPlaceHolder ? type.hasName(normalizedName) : hasName(type.normalizedName);
        } else {
            return false;
        }
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

    AttributeType duplicate() {
        return new AttributeType(oid, names, description, isObsolete, superiorTypeOID,
                equalityMatchingRuleOID, orderingMatchingRuleOID, substringMatchingRuleOID,
                approximateMatchingRuleOID, syntaxOID, isSingleValue, isCollective,
                isNoUserModification, attributeUsage, extraProperties, definition);
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

        if (superiorTypeOID != null) {
            buffer.append(" SUP ");
            buffer.append(superiorTypeOID);
        }

        if (equalityMatchingRuleOID != null) {
            buffer.append(" EQUALITY ");
            buffer.append(equalityMatchingRuleOID);
        }

        if (orderingMatchingRuleOID != null) {
            buffer.append(" ORDERING ");
            buffer.append(orderingMatchingRuleOID);
        }

        if (substringMatchingRuleOID != null) {
            buffer.append(" SUBSTR ");
            buffer.append(substringMatchingRuleOID);
        }

        if (syntaxOID != null) {
            buffer.append(" SYNTAX ");
            buffer.append(syntaxOID);
        }

        if (isSingleValue()) {
            buffer.append(" SINGLE-VALUE");
        }

        if (isCollective()) {
            buffer.append(" COLLECTIVE");
        }

        if (isNoUserModification()) {
            buffer.append(" NO-USER-MODIFICATION");
        }

        if (attributeUsage != null) {
            buffer.append(" USAGE ");
            buffer.append(attributeUsage.toString());
        }

        if (approximateMatchingRuleOID != null) {
            buffer.append(" ");
            buffer.append(SCHEMA_PROPERTY_APPROX_RULE);
            buffer.append(" '");
            buffer.append(approximateMatchingRuleOID);
            buffer.append("'");
        }
    }

    boolean validate(final Schema schema, final List<AttributeType> invalidSchemaElements,
            final List<LocalizableMessage> warnings) {
        // Avoid validating this schema element more than once. This may occur
        // if multiple attributes specify the same superior.
        if (!needsValidating) {
            return isValid;
        }

        // Prevent re-validation.
        needsValidating = false;

        if (superiorTypeOID != null) {
            try {
                superiorType = schema.getAttributeType(superiorTypeOID);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUPERIOR_TYPE1.get(getNameOrOID(),
                                superiorTypeOID);
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }

            // First ensure that the superior has been validated and fail if it
            // is invalid.
            if (!superiorType.validate(schema, invalidSchemaElements, warnings)) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_SUPERIOR_TYPE.get(getNameOrOID(),
                                superiorTypeOID);
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }

            // If there is a superior type, then it must have the same usage
            // as the subordinate type. Also, if the superior type is
            // collective, then so must the subordinate type be collective.
            if (superiorType.getUsage() != getUsage()) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_SUPERIOR_USAGE.get(getNameOrOID(),
                                getUsage().toString(), superiorType.getNameOrOID());
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }

            if (superiorType.isCollective() != isCollective()) {
                if (!isCollective()) {
                    LocalizableMessage message =
                            WARN_ATTR_SYNTAX_ATTRTYPE_NONCOLLECTIVE_FROM_COLLECTIVE.get(
                                    getNameOrOID(), superiorType.getNameOrOID());
                    failValidation(invalidSchemaElements, warnings, message);
                    return false;
                }
            }
        }

        if (syntaxOID != null) {
            if (!schema.hasSyntax(syntaxOID)) {
                // Try substituting a syntax from the core schema. This will
                // never fail since the core schema is non-strict and will
                // substitute the syntax if required.
                syntax = Schema.getCoreSchema().getSyntax(syntaxOID);
                final LocalizableMessage message =
                        WARN_ATTR_TYPE_NOT_DEFINED1.get(getNameOrOID(), syntaxOID, syntax.getOID());
                warnings.add(message);
            } else {
                syntax = schema.getSyntax(syntaxOID);
            }
        } else if (getSuperiorType() != null && getSuperiorType().getSyntax() != null) {
            // Try to inherit the syntax from the superior type if possible
            syntax = getSuperiorType().getSyntax();
        }

        if (equalityMatchingRuleOID != null) {
            // Use explicitly defined matching rule first.
            try {
                equalityMatchingRule = schema.getMatchingRule(equalityMatchingRuleOID);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_EQUALITY_MR1.get(getNameOrOID(),
                                equalityMatchingRuleOID);
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }
        } else if (getSuperiorType() != null && getSuperiorType().getEqualityMatchingRule() != null) {
            // Inherit matching rule from superior type if possible
            equalityMatchingRule = getSuperiorType().getEqualityMatchingRule();
        } else if (getSyntax() != null && getSyntax().getEqualityMatchingRule() != null) {
            // Use default for syntax
            equalityMatchingRule = getSyntax().getEqualityMatchingRule();
        }

        if (orderingMatchingRuleOID != null) {
            // Use explicitly defined matching rule first.
            try {
                orderingMatchingRule = schema.getMatchingRule(orderingMatchingRuleOID);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_ORDERING_MR1.get(getNameOrOID(),
                                orderingMatchingRuleOID);
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }
        } else if (getSuperiorType() != null && getSuperiorType().getOrderingMatchingRule() != null) {
            // Inherit matching rule from superior type if possible
            orderingMatchingRule = getSuperiorType().getOrderingMatchingRule();
        } else if (getSyntax() != null && getSyntax().getOrderingMatchingRule() != null) {
            // Use default for syntax
            orderingMatchingRule = getSyntax().getOrderingMatchingRule();
        }

        if (substringMatchingRuleOID != null) {
            // Use explicitly defined matching rule first.
            try {
                substringMatchingRule = schema.getMatchingRule(substringMatchingRuleOID);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUBSTRING_MR1.get(getNameOrOID(),
                                substringMatchingRuleOID);
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }
        } else if (getSuperiorType() != null
                && getSuperiorType().getSubstringMatchingRule() != null) {
            // Inherit matching rule from superior type if possible
            substringMatchingRule = getSuperiorType().getSubstringMatchingRule();
        } else if (getSyntax() != null && getSyntax().getSubstringMatchingRule() != null) {
            // Use default for syntax
            substringMatchingRule = getSyntax().getSubstringMatchingRule();
        }

        if (approximateMatchingRuleOID != null) {
            // Use explicitly defined matching rule first.
            try {
                approximateMatchingRule = schema.getMatchingRule(approximateMatchingRuleOID);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_APPROXIMATE_MR1.get(getNameOrOID(),
                                approximateMatchingRuleOID);
                failValidation(invalidSchemaElements, warnings, message);
                return false;
            }
        } else if (getSuperiorType() != null
                && getSuperiorType().getApproximateMatchingRule() != null) {
            // Inherit matching rule from superior type if possible
            approximateMatchingRule = getSuperiorType().getApproximateMatchingRule();
        } else if (getSyntax() != null && getSyntax().getApproximateMatchingRule() != null) {
            // Use default for syntax
            approximateMatchingRule = getSyntax().getApproximateMatchingRule();
        }

        // If the attribute type is COLLECTIVE, then it must have a usage of
        // userApplications.
        if (isCollective() && getUsage() != AttributeUsage.USER_APPLICATIONS) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_ATTRTYPE_COLLECTIVE_IS_OPERATIONAL.get(getNameOrOID());
            warnings.add(message);
        }

        // If the attribute type is NO-USER-MODIFICATION, then it must not
        // have a usage of userApplications.
        if (isNoUserModification() && getUsage() == AttributeUsage.USER_APPLICATIONS) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_ATTRTYPE_NO_USER_MOD_NOT_OPERATIONAL.get(getNameOrOID());
            warnings.add(message);
        }

        return (isValid = true);
    }

    private void failValidation(final List<AttributeType> invalidSchemaElements,
            final List<LocalizableMessage> warnings, final LocalizableMessage message) {
        invalidSchemaElements.add(this);
        warnings.add(ERR_ATTR_TYPE_VALIDATION_FAIL.get(toString(), message));
    }
}
