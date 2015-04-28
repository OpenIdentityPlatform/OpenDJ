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

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static java.util.Arrays.*;

import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

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
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.util.Reject;

/**
 * This class defines a DIT content rule, which defines the set of allowed,
 * required, and prohibited attributes for entries with a given structural
 * objectclass, and also indicates which auxiliary classes may be included in
 * the entry.
 */
public final class DITContentRule extends SchemaElement {

    /** A fluent API for incrementally constructing DIT content rule. */
    public static final class Builder extends SchemaElementBuilder<Builder> {
        private String structuralClassOID;
        private final List<String> names = new LinkedList<>();
        private boolean isObsolete;
        private final Set<String> auxiliaryClassOIDs = new LinkedHashSet<>();
        private final Set<String> optionalAttributeOIDs = new LinkedHashSet<>();
        private final Set<String> prohibitedAttributeOIDs = new LinkedHashSet<>();
        private final Set<String> requiredAttributeOIDs = new LinkedHashSet<>();

        Builder(final DITContentRule contentRule, final SchemaBuilder schemaBuilder) {
            super(schemaBuilder, contentRule);
            structuralClassOID = contentRule.structuralClassOID;
            names.addAll(contentRule.getNames());
            isObsolete = contentRule.isObsolete;
            auxiliaryClassOIDs.addAll(contentRule.auxiliaryClassOIDs);
            optionalAttributeOIDs.addAll(contentRule.optionalAttributeOIDs);
            prohibitedAttributeOIDs.addAll(contentRule.prohibitedAttributeOIDs);
            requiredAttributeOIDs.addAll(contentRule.requiredAttributeOIDs);
        }

        Builder(final String structuralClassOID, final SchemaBuilder builder) {
            super(builder);
            this.structuralClassOID = structuralClassOID;
        }

        /**
         * Adds this DIT content rule to the schema, throwing a
         * {@code  ConflictingSchemaElementException} if there is an existing DIT
         * content rule with the same structural object class OID.
         *
         * @return The parent schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing DIT content rule with the same
         *             structural object class OID.
         */
        public SchemaBuilder addToSchema() {
            return getSchemaBuilder().addDITContentRule(new DITContentRule(this), false);
        }

        /**
         * Adds this DIT content rule to the schema overwriting any existing
         * content rule with the same structural class OID.
         *
         * @return The parent schema builder.
         */
        public SchemaBuilder addToSchemaOverwrite() {
            return getSchemaBuilder().addDITContentRule(new DITContentRule(this), true);
        }

        /**
         * Adds the provided auxiliary classes to the list of auxiliary object
         * classes that entries subject to this DIT content rule may belong to.
         *
         * @param objectClassNamesOrOIDs
         *            The list of auxiliary class names or OIDs.
         * @return This builder.
         */
        public Builder auxiliaryObjectClasses(final Collection<String> objectClassNamesOrOIDs) {
            this.auxiliaryClassOIDs.addAll(objectClassNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided auxiliary classes to the list of auxiliary object
         * classes that entries subject to this DIT content rule may belong to.
         *
         * @param objectClassNamesOrOIDs
         *            The list of auxiliary class names or OIDs.
         * @return This builder.
         */
        public Builder auxiliaryObjectClasses(String... objectClassNamesOrOIDs) {
            this.auxiliaryClassOIDs.addAll(asList(objectClassNamesOrOIDs));
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
         *            {@code true} if this schema element is obsolete (default
         *            is {@code false}).
         * @return This builder.
         */
        public Builder obsolete(final boolean isObsolete) {
            this.isObsolete = isObsolete;
            return this;
        }

        /**
         * Adds the provided optional attributes to the list of attribute types
         * that entries subject to this DIT content rule may contain.
         *
         * @param attributeNamesOrOIDs
         *            The list of optional attribute names or OIDs.
         * @return This builder.
         */
        public Builder optionalAttributes(final Collection<String> attributeNamesOrOIDs) {
            this.optionalAttributeOIDs.addAll(attributeNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided optional attributes to the list of attribute types
         * that entries subject to this DIT content rule may contain.
         *
         * @param attributeNamesOrOIDs
         *            The list of optional attribute names or OIDs.
         * @return This builder.
         */
        public Builder optionalAttributes(final String... attributeNamesOrOIDs) {
            this.optionalAttributeOIDs.addAll(asList(attributeNamesOrOIDs));
            return this;
        }

        /**
         * Adds the provided prohibited attributes to the list of attribute types
         * that entries subject to this DIT content rule must not contain.
         *
         * @param attributeNamesOrOIDs
         *            The list of prohibited attribute names or OIDs.
         * @return This builder.
         */
        public Builder prohibitedAttributes(final Collection<String> attributeNamesOrOIDs) {
            this.prohibitedAttributeOIDs.addAll(attributeNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided prohibited attributes to the list of attribute types
         * that entries subject to this DIT content rule must not contain.
         *
         * @param attributeNamesOrOIDs
         *            The list of prohibited attribute names or OIDs.
         * @return This builder.
         */
        public Builder prohibitedAttributes(final String... attributeNamesOrOIDs) {
            this.prohibitedAttributeOIDs.addAll(asList(attributeNamesOrOIDs));
            return this;
        }

        /**
         * Clears the list of auxiliary object classes that entries subject to
         * this DIT content rule may belong to.
         *
         * @return This builder.
         */
        public Builder removeAllAuxiliaryObjectClasses() {
            this.auxiliaryClassOIDs.clear();
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
         * Clears the list of attribute types that entries subject to this DIT
         * content rule may contain.
         *
         * @return This builder.
         */
        public Builder removeAllOptionalAttributes() {
            this.optionalAttributeOIDs.clear();
            return this;
        }

        /**
         * Clears the list of attribute types that entries subject to this DIT
         * content rule must not contain.
         *
         * @return This builder.
         */
        public Builder removeAllProhibitedAttributes() {
            this.prohibitedAttributeOIDs.clear();
            return this;
        }

        /**
         * Clears the list of attribute types that entries subject to this DIT
         * content rule must contain.
         *
         * @return This builder.
         */
        public Builder removeAllRequiredAttributes() {
            this.requiredAttributeOIDs.clear();
            return this;
        }

        /**
         * Removes the provided object class in the list of auxiliary object classes that entries subject to
         * this DIT content rule may belong to.
         *
         * @param objectClassNameOrOID
         *            The auxiliary object class name or OID to be removed.
         * @return This builder.
         */
        public Builder removeAuxiliaryObjectClass(String objectClassNameOrOID) {
            this.auxiliaryClassOIDs.remove(objectClassNameOrOID);
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

        /**
         * Removes the provided optional attribute in the list of attribute
         * types that entries subject to this DIT content rule may contain.
         *
         * @param attributeNameOrOID
         *            The optional attribute name or OID to be removed.
         * @return This builder.
         */
        public Builder removeOptionalAttribute(String attributeNameOrOID) {
            this.optionalAttributeOIDs.remove(attributeNameOrOID);
            return this;
        }

        /**
         * Removes the provided prohibited attribute in the list of attribute
         * types that entries subject to this DIT content rule must not contain.
         *
         * @param attributeNameOrOID
         *            The prohibited attribute name or OID to be removed.
         * @return This builder.
         */
        public Builder removeProhibitedAttribute(String attributeNameOrOID) {
            this.prohibitedAttributeOIDs.remove(attributeNameOrOID);
            return this;
        }

        /**
         * Removes the provided required attribute in the list of attribute
         * types that entries subject to this DIT content rule must contain.
         *
         * @param attributeNameOrOID
         *            The provided required attribute name or OID to be removed.
         * @return This builder.
         */
        public Builder removeRequiredAttribute(String attributeNameOrOID) {
            this.requiredAttributeOIDs.remove(attributeNameOrOID);
            return this;
        }

        /**
         * Adds the provided attribute to the list of attribute types that
         * entries subject to this DIT content rule must contain.
         *
         * @param attributeNamesOrOIDs
         *            The list of required attribute names or OIDs.
         * @return This builder.
         */
        public Builder requiredAttributes(final Collection<String> attributeNamesOrOIDs) {
            this.requiredAttributeOIDs.addAll(attributeNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided attribute to the list of attribute types that
         * entries subject to this DIT content rule must contain.
         *
         * @param attributeNamesOrOIDs
         *            The list of required attribute names or OIDs.
         * @return This builder.
         */
        public Builder requiredAttributes(final String... attributeNamesOrOIDs) {
            this.requiredAttributeOIDs.addAll(asList(attributeNamesOrOIDs));
            return this;
        }

        /**
         * Sets the structural class OID which uniquely identifies this DIT
         * content rule.
         *
         * @param strucuralClassOID
         *            The numeric OID.
         * @return This builder.
         */
        public Builder structuralClassOID(String strucuralClassOID) {
            this.structuralClassOID = strucuralClassOID;
            return this;
        }

    }

    /** The structural objectclass for this DIT content rule. */
    private final String structuralClassOID;

    /** The set of user defined names for this definition. */
    private final List<String> names;

    /** Indicates whether this definition is declared "obsolete". */
    private final boolean isObsolete;

    /**
     * The set of auxiliary objectclasses that entries with this content
     * rule may contain, in a mapping between the objectclass and the
     * user-defined name for that class.
     */
    private final Set<String> auxiliaryClassOIDs;

    /** The set of optional attribute types for this DIT content rule. */
    private final Set<String> optionalAttributeOIDs;

    /** The set of prohibited attribute types for this DIT content rule. */
    private final Set<String> prohibitedAttributeOIDs;

    /** The set of required attribute types for this DIT content rule. */
    private final Set<String> requiredAttributeOIDs;

    private ObjectClass structuralClass;
    private Set<ObjectClass> auxiliaryClasses = Collections.emptySet();
    private Set<AttributeType> optionalAttributes = Collections.emptySet();
    private Set<AttributeType> prohibitedAttributes = Collections.emptySet();
    private Set<AttributeType> requiredAttributes = Collections.emptySet();

    private DITContentRule(final Builder builder) {
        super(builder);
        Reject.ifNull(builder.structuralClassOID);

        structuralClassOID = builder.structuralClassOID;
        names = unmodifiableCopyOfList(builder.names);
        isObsolete = builder.isObsolete;
        auxiliaryClassOIDs = unmodifiableCopyOfSet(builder.auxiliaryClassOIDs);
        optionalAttributeOIDs = unmodifiableCopyOfSet(builder.optionalAttributeOIDs);
        prohibitedAttributeOIDs = unmodifiableCopyOfSet(builder.prohibitedAttributeOIDs);
        requiredAttributeOIDs = unmodifiableCopyOfSet(builder.requiredAttributeOIDs);
    }

    /**
     * Returns {@code true} if the provided object is a DIT content rule having
     * the same structural object class OID as this DIT content rule.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is a DIT content rule having
     *         the same numeric OID as this DIT content rule.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof DITContentRule) {
            final DITContentRule other = (DITContentRule) o;
            return structuralClassOID.equals(other.structuralClassOID);
        } else {
            return false;
        }
    }

    /**
     * Returns an unmodifiable set containing the auxiliary objectclasses that
     * may be used for entries associated with this DIT content rule.
     *
     * @return An unmodifiable set containing the auxiliary objectclasses that
     *         may be used for entries associated with this DIT content rule.
     */
    public Set<ObjectClass> getAuxiliaryClasses() {
        return auxiliaryClasses;
    }

    /**
     * Returns the name or structural class OID for this schema definition. If
     * it has one or more names, then the primary name will be returned. If it
     * does not have any names, then the OID will be returned.
     *
     * @return The name or OID for this schema definition.
     */
    public String getNameOrOID() {
        if (names.isEmpty()) {
            return structuralClassOID;
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
     * Returns an unmodifiable set containing the optional attributes for this
     * DIT content rule.
     *
     * @return An unmodifiable set containing the optional attributes for this
     *         DIT content rule.
     */
    public Set<AttributeType> getOptionalAttributes() {
        return optionalAttributes;
    }

    /**
     * Returns an unmodifiable set containing the prohibited attributes for this
     * DIT content rule.
     *
     * @return An unmodifiable set containing the prohibited attributes for this
     *         DIT content rule.
     */
    public Set<AttributeType> getProhibitedAttributes() {
        return prohibitedAttributes;
    }

    /**
     * Returns an unmodifiable set containing the required attributes for this
     * DIT content rule.
     *
     * @return An unmodifiable set containing the required attributes for this
     *         DIT content rule.
     */
    public Set<AttributeType> getRequiredAttributes() {
        return requiredAttributes;
    }

    /**
     * Returns the structural objectclass for this DIT content rule.
     *
     * @return The structural objectclass for this DIT content rule.
     */
    public ObjectClass getStructuralClass() {
        return structuralClass;
    }

    /**
     * Returns the structural class OID for this schema definition.
     *
     * @return The structural class OID for this schema definition.
     */
    public String getStructuralClassOID() {
        return structuralClassOID;
    }

    /**
     * Returns the hash code for this DIT content rule. It will be calculated as
     * the hash code of the structural object class OID.
     *
     * @return The hash code for this DIT content rule.
     */
    @Override
    public int hashCode() {
        return structuralClassOID.hashCode();
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
     * structural class OID.
     *
     * @param value
     *            The value for which to make the determination.
     * @return <code>true</code> if the provided value matches the OID or one of
     *         the names assigned to this schema definition, or
     *         <code>false</code> if not.
     */
    public boolean hasNameOrOID(final String value) {
        return hasName(value) || structuralClassOID.equals(value);
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
     * Indicates whether the provided attribute type is included in the optional
     * attribute list for this DIT content rule.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is optional for
     *         this DIT content rule, or <code>false</code> if not.
     */
    public boolean isOptional(final AttributeType attributeType) {
        return optionalAttributes.contains(attributeType);
    }

    /**
     * Indicates whether the provided attribute type is included in the required
     * attribute list for this DIT content rule.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is required by
     *         this DIT content rule, or <code>false</code> if not.
     */
    public boolean isRequired(final AttributeType attributeType) {
        return requiredAttributes.contains(attributeType);
    }

    /**
     * Indicates whether the provided attribute type is in the list of required
     * or optional attributes for this DIT content rule.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is required or
     *         allowed for this DIT content rule, or <code>false</code> if it is
     *         not.
     */
    public boolean isRequiredOrOptional(final AttributeType attributeType) {
        return isRequired(attributeType) || isOptional(attributeType);
    }

    @Override
    void toStringContent(final StringBuilder buffer) {
        buffer.append(structuralClassOID);

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

        if (!auxiliaryClassOIDs.isEmpty()) {
            final Iterator<String> iterator = auxiliaryClassOIDs.iterator();

            final String firstClass = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" AUX (");
                buffer.append(firstClass);

                while (iterator.hasNext()) {
                    buffer.append(" $ ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" AUX ");
                buffer.append(firstClass);
            }
        }

        if (!requiredAttributeOIDs.isEmpty()) {
            final Iterator<String> iterator = requiredAttributeOIDs.iterator();

            final String firstName = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" MUST ( ");
                buffer.append(firstName);

                while (iterator.hasNext()) {
                    buffer.append(" $ ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" MUST ");
                buffer.append(firstName);
            }
        }

        if (!optionalAttributeOIDs.isEmpty()) {
            final Iterator<String> iterator = optionalAttributeOIDs.iterator();

            final String firstName = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" MAY ( ");
                buffer.append(firstName);

                while (iterator.hasNext()) {
                    buffer.append(" $ ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" MAY ");
                buffer.append(firstName);
            }
        }

        if (!prohibitedAttributeOIDs.isEmpty()) {
            final Iterator<String> iterator = prohibitedAttributeOIDs.iterator();

            final String firstName = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" NOT ( ");
                buffer.append(firstName);

                while (iterator.hasNext()) {
                    buffer.append(" $ ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" NOT ");
                buffer.append(firstName);
            }
        }
    }

    void validate(final Schema schema, final List<LocalizableMessage> warnings)
            throws SchemaException {
        // Get the objectclass with the specified OID. If it does not exist
        // or is not structural, then fail.
        if (structuralClassOID != null) {
            try {
                structuralClass = schema.getObjectClass(structuralClassOID);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_DCR_UNKNOWN_STRUCTURAL_CLASS1.get(getNameOrOID(),
                                structuralClassOID);
                throw new SchemaException(message, e);
            }
            if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_DCR_STRUCTURAL_CLASS_NOT_STRUCTURAL1.get(getNameOrOID(),
                                structuralClass.getNameOrOID(), structuralClass
                                        .getObjectClassType().toString());
                warnings.add(message);
            }
        }

        if (!auxiliaryClassOIDs.isEmpty()) {
            auxiliaryClasses = new HashSet<>(auxiliaryClassOIDs.size());
            ObjectClass objectClass;
            for (final String oid : auxiliaryClassOIDs) {
                try {
                    objectClass = schema.getObjectClass(oid);
                } catch (final UnknownSchemaElementException e) {
                    // This isn't good because it is an unknown auxiliary class.
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS1.get(getNameOrOID(), oid);
                    throw new SchemaException(message, e);
                }
                if (objectClass.getObjectClassType() != ObjectClassType.AUXILIARY) {
                    // This isn't good because it isn't an auxiliary class.
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY1.get(getNameOrOID(),
                                    structuralClass.getOID(), structuralClass.getObjectClassType()
                                            .toString());
                    throw new SchemaException(message);
                }
                auxiliaryClasses.add(objectClass);
            }
        }

        if (!requiredAttributeOIDs.isEmpty()) {
            requiredAttributes =
                getAttributeTypes(schema, requiredAttributeOIDs, ERR_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR1);
        }

        if (!optionalAttributeOIDs.isEmpty()) {
            optionalAttributes =
                getAttributeTypes(schema, optionalAttributeOIDs, ERR_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR1);
        }

        if (!prohibitedAttributeOIDs.isEmpty()) {
            prohibitedAttributes =
                getAttributeTypes(schema, prohibitedAttributeOIDs, ERR_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR1);
        }

        // Make sure that none of the prohibited attributes is required by
        // the structural or any of the auxiliary classes.
        for (final AttributeType t : prohibitedAttributes) {
            if (structuralClass.isRequired(t)) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_DCR_PROHIBITED_REQUIRED_BY_STRUCTURAL.get(getNameOrOID(), t
                                .getNameOrOID(), structuralClass.getNameOrOID());
                throw new SchemaException(message);
            }

            for (final ObjectClass oc : auxiliaryClasses) {
                if (oc.isRequired(t)) {
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DCR_PROHIBITED_REQUIRED_BY_AUXILIARY.get(
                                    getNameOrOID(), t.getNameOrOID(), oc.getNameOrOID());
                    throw new SchemaException(message);
                }
            }
        }

        auxiliaryClasses = Collections.unmodifiableSet(auxiliaryClasses);
        optionalAttributes = Collections.unmodifiableSet(optionalAttributes);
        prohibitedAttributes = Collections.unmodifiableSet(prohibitedAttributes);
        requiredAttributes = Collections.unmodifiableSet(requiredAttributes);
    }

    private Set<AttributeType> getAttributeTypes(final Schema schema, Set<String> oids, Arg2<Object, Object> errorMsg)
            throws SchemaException {
        Set<AttributeType> attrTypes = new HashSet<>(oids.size());
        for (final String oid : oids) {
            try {
                attrTypes.add(schema.getAttributeType(oid));
            } catch (final UnknownSchemaElementException e) {
                // This isn't good because it means that the DIT content rule
                // requires an attribute type that we don't know anything about.
                throw new SchemaException(errorMsg.get(getNameOrOID(), oid), e);
            }
        }
        return attrTypes;
    }
}
