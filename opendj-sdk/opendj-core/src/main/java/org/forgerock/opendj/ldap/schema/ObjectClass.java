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
 *      Portions copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

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

import static java.util.Arrays.*;
import static java.util.Collections.*;

import static org.forgerock.opendj.ldap.schema.ObjectClassType.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;

/**
 * This class defines a data structure for storing and interacting with an
 * objectclass, which contains a collection of attributes that must and/or may
 * be present in an entry with that objectclass.
 * <p>
 * Where ordered sets of names, attribute types, or extra properties are
 * provided, the ordering will be preserved when the associated fields are
 * accessed via their getters or via the {@link #toString()} methods.
 */
public final class ObjectClass extends SchemaElement {

    /** A fluent API for incrementally constructing object classes. */
    public static final class Builder extends SchemaElementBuilder<Builder> {
        private boolean isObsolete;
        private final List<String> names = new LinkedList<>();
        private String oid;
        private final Set<String> optionalAttributes = new LinkedHashSet<>();
        private final Set<String> requiredAttributes = new LinkedHashSet<>();
        private final Set<String> superiorClasses = new LinkedHashSet<>();
        private ObjectClassType type;

        Builder(final ObjectClass oc, final SchemaBuilder builder) {
            super(builder, oc);
            this.oid = oc.oid;
            this.names.addAll(oc.names);
            this.isObsolete = oc.isObsolete;
            this.type = oc.objectClassType;
            this.superiorClasses.addAll(oc.superiorClassOIDs);
            this.requiredAttributes.addAll(oc.requiredAttributeOIDs);
            this.optionalAttributes.addAll(optionalAttributes);
        }

        Builder(final String oid, final SchemaBuilder builder) {
            super(builder);
            this.oid = oid;
        }

        /**
         * Adds this object class to the schema, throwing a
         * {@code ConflictingSchemaElementException} if there is an existing
         * object class with the same numeric OID.
         *
         * @return The parent schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing object class with the same numeric
         *             OID.
         */
        public SchemaBuilder addToSchema() {
            return getSchemaBuilder().addObjectClass(new ObjectClass(this), false);
        }

        /**
         * Adds this object class to the schema overwriting any existing object class
         * with the same numeric OID.
         *
         * @return The parent schema builder.
         */
        public SchemaBuilder addToSchemaOverwrite() {
            return getSchemaBuilder().addObjectClass(new ObjectClass(this), true);
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
         * Sets the numeric OID which uniquely identifies this object class.
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
         * Adds the provided optional attributes.
         *
         * @param attributeNamesOrOIDs
         *      The list of optional attribute names or OIDs.
         * @return This builder.
         */
        public Builder optionalAttributes(final Collection<String> attributeNamesOrOIDs) {
            this.optionalAttributes.addAll(attributeNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided optional attributes.
         *
         * @param attributeNamesOrOIDs
         *      The list of optional attribute names or OIDs.
         * @return This builder.
         */
        public Builder optionalAttributes(final String... attributeNamesOrOIDs) {
            this.optionalAttributes.addAll(asList(attributeNamesOrOIDs));
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
         * Removes all user defined names.
         *
         * @return This builder.
         */
        public Builder removeAllNames() {
            this.names.clear();
            return this;
        }

        /**
         * Removes all optional attributes.
         *
         * @return This builder.
         */
        public Builder removeAllOptionalAttributes() {
            this.optionalAttributes.clear();
            return this;
        }

        /**
         * Removes all required attributes.
         *
         * @return This builder.
         */
        public Builder removeAllRequiredAttributes() {
            this.requiredAttributes.clear();
            return this;
        }

        /**
         * Removes all superior object class.
         *
         * @return This builder.
         */
        public Builder removeAllSuperiorObjectClass() {
            this.superiorClasses.clear();
            return this;
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
         * Removes the provided optional attribute.
         *
         * @param attributeNameOrOID
         *            The optional attribute name or OID to be removed.
         * @return This builder.
         */
        public Builder removeOptionalAttribute(String attributeNameOrOID) {
            this.optionalAttributes.remove(attributeNameOrOID);
            return this;
        }

        /**
         * Removes the provided required attribute.
         *
         * @param attributeNameOrOID
         *            The provided required attribute name or OID to be removed.
         * @return This builder.
         */
        public Builder removeRequiredAttribute(String attributeNameOrOID) {
            this.requiredAttributes.remove(attributeNameOrOID);
            return this;
        }

        /**
         * Removes the provided superior object class.
         *
         * @param objectClassNameOrOID
         *            The superior object class name or OID to be removed.
         * @return This builder.
         */
        public Builder removeSuperiorObjectClass(String objectClassNameOrOID) {
            this.superiorClasses.remove(objectClassNameOrOID);
            return this;
        }

        /**
         * Adds the provided required attributes.
         *
         * @param attributeNamesOrOIDs
         *      The list of required attribute names or OIDs.
         * @return This builder.
         */
        public Builder requiredAttributes(final Collection<String> attributeNamesOrOIDs) {
            this.requiredAttributes.addAll(attributeNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided required attributes.
         *
         * @param attributeNamesOrOIDs
         *      The list of required attribute names or OIDs.
         * @return This builder.
         */
        public Builder requiredAttributes(final String... attributeNamesOrOIDs) {
            this.requiredAttributes.addAll(asList(attributeNamesOrOIDs));
            return this;
        }

        /**
         * Adds the provided superior object classes.
         *
         * @param objectClassNamesOrOIDs
         *      The list of superior object classes names or OIDs.
         * @return This builder.
         */
        public Builder superiorObjectClasses(final Collection<String> objectClassNamesOrOIDs) {
            this.superiorClasses.addAll(objectClassNamesOrOIDs);
            return this;
        }

        /**
         * Adds the provided superior object classes.
         *
         * @param objectClassNamesOrOIDs
         *      The list of superior object classes names or OIDs.
         * @return This builder.
         */
        public Builder superiorObjectClasses(final String... objectClassNamesOrOIDs) {
            this.superiorClasses.addAll(asList(objectClassNamesOrOIDs));
            return this;
        }

        /**
         * Sets the type of this object class.
         *
         * @param type
         *      The object class type.
         * @return This builder.
         */
        public Builder type(final ObjectClassType type) {
            this.type = type;
            return this;
        }
    }

    /** The OID that may be used to reference this definition. */
    private final String oid;

    /** The set of user defined names for this definition. */
    private final List<String> names;

    /** Indicates whether this definition is declared "obsolete". */
    private final boolean isObsolete;

    /** The reference to the superior objectclasses. */
    private final Set<String> superiorClassOIDs;

    /** The objectclass type for this objectclass. */
    private final ObjectClassType objectClassType;

    /** The set of required attribute types for this objectclass. */
    private final Set<String> requiredAttributeOIDs;

    /** The set of optional attribute types for this objectclass. */
    private final Set<String> optionalAttributeOIDs;

    private Set<ObjectClass> superiorClasses = emptySet();
    private Set<AttributeType> declaredRequiredAttributes = emptySet();
    private Set<AttributeType> requiredAttributes = emptySet();
    private Set<AttributeType> declaredOptionalAttributes = emptySet();
    private Set<AttributeType> optionalAttributes = emptySet();

    /** Indicates whether or not validation has been performed. */
    private boolean needsValidating = true;

    /** The indicates whether or not validation failed. */
    private boolean isValid;

    /**
     * Construct a extensibleObject object class where the set of allowed
     * attribute types of this object class is implicitly the set of all
     * attribute types of userApplications usage.
     *
     * @param description
     *            The description for this schema definition
     * @param extraProperties
     *            The map of "extra" properties for this schema definition
     */
    static ObjectClass newExtensibleObjectObjectClass(final String description,
        final Map<String, List<String>> extraProperties, final SchemaBuilder builder) {
        return new ObjectClass(new Builder(EXTENSIBLE_OBJECT_OBJECTCLASS_OID, builder)
               .description(description)
               .extraProperties(extraProperties)
               .names(EXTENSIBLE_OBJECT_OBJECTCLASS_NAME)
               .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
               .type(AUXILIARY));
    }


    private ObjectClass(final Builder builder) {
        super(builder);

        if (builder.oid == null || builder.oid.isEmpty()) {
            throw new IllegalArgumentException("An OID must be specified.");
        }

        this.oid = builder.oid;
        this.names = unmodifiableCopyOfList(builder.names);
        this.isObsolete = builder.isObsolete;
        this.superiorClassOIDs = unmodifiableCopyOfSet(builder.superiorClasses);
        this.objectClassType = builder.type;
        this.requiredAttributeOIDs = unmodifiableCopyOfSet(builder.requiredAttributes);
        this.optionalAttributeOIDs = unmodifiableCopyOfSet(builder.optionalAttributes);
    }

    /**
     * Returns {@code true} if the provided object is an object class having the
     * same numeric OID as this object class.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is a object class having the
     *         same numeric OID as this object class.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof ObjectClass) {
            final ObjectClass other = (ObjectClass) o;
            return oid.equals(other.oid);
        } else {
            return false;
        }
    }

    /**
     * Returns an unmodifiable set containing the optional attributes for this
     * object class. Note that this set will not automatically include any
     * optional attributes for superior object classes.
     *
     * @return An unmodifiable set containing the optional attributes for this
     *         object class.
     */
    public Set<AttributeType> getDeclaredOptionalAttributes() {
        return declaredOptionalAttributes;
    }

    /**
     * Returns an unmodifiable set containing the required attributes for this
     * object class. Note that this set will not automatically include any
     * required attributes for superior object classes.
     *
     * @return An unmodifiable set containing the required attributes for this
     *         object class.
     */
    public Set<AttributeType> getDeclaredRequiredAttributes() {
        return declaredRequiredAttributes;
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
     * Returns the objectclass type for this objectclass.
     *
     * @return The objectclass type for this objectclass.
     */
    public ObjectClassType getObjectClassType() {
        return objectClassType != null ? objectClassType : STRUCTURAL;
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
     * Returns an unmodifiable set containing the optional attributes for this
     * object class and any superior object classes that it might have.
     *
     * @return An unmodifiable set containing the optional attributes for this
     *         object class and any superior object classes that it might have.
     */
    public Set<AttributeType> getOptionalAttributes() {
        return optionalAttributes;
    }

    /**
     * Returns an unmodifiable set containing the required attributes for this
     * object class and any superior object classes that it might have.
     *
     * @return An unmodifiable set containing the required attributes for this
     *         object class and any superior object classes that it might have.
     */
    public Set<AttributeType> getRequiredAttributes() {
        return requiredAttributes;
    }

    /**
     * Returns an unmodifiable set containing the superior classes for this
     * object class.
     *
     * @return An unmodifiable set containing the superior classes for this
     *         object class.
     */
    public Set<ObjectClass> getSuperiorClasses() {
        return superiorClasses;
    }

    /**
     * Returns the hash code for this object class. It will be calculated as the
     * hash code of the numeric OID.
     *
     * @return The hash code for this object class.
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
     * Indicates whether this objectclass is a descendant of the provided class.
     *
     * @param objectClass
     *            The objectClass for which to make the determination.
     * @return <code>true</code> if this objectclass is a descendant of the
     *         provided class, or <code>false</code> if not.
     */
    public boolean isDescendantOf(final ObjectClass objectClass) {
        for (final ObjectClass sup : superiorClasses) {
            if (sup.equals(objectClass) || sup.isDescendantOf(objectClass)) {
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
     * Indicates whether the provided attribute type is included in the optional
     * attribute list for this or any of its superior objectclasses.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is optional for
     *         this objectclass or any of its superior classes, or
     *         <code>false</code> if not.
     */
    public boolean isOptional(final AttributeType attributeType) {
        return optionalAttributes.contains(attributeType);
    }

    /**
     * Indicates whether the provided attribute type is included in the required
     * attribute list for this or any of its superior objectclasses.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is required by
     *         this objectclass or any of its superior classes, or
     *         <code>false</code> if not.
     */
    public boolean isRequired(final AttributeType attributeType) {
        return requiredAttributes.contains(attributeType);
    }

    /**
     * Indicates whether the provided attribute type is in the list of required
     * or optional attributes for this objectclass or any of its superior
     * classes.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is required or
     *         allowed for this objectclass or any of its superior classes, or
     *         <code>false</code> if it is not.
     */
    public boolean isRequiredOrOptional(final AttributeType attributeType) {
        return isRequired(attributeType) || isOptional(attributeType);
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

        if (!superiorClassOIDs.isEmpty()) {
            final Iterator<String> iterator = superiorClassOIDs.iterator();

            final String firstName = iterator.next();
            if (iterator.hasNext()) {
                buffer.append(" SUP ( ");
                buffer.append(firstName);

                while (iterator.hasNext()) {
                    buffer.append(" $ ");
                    buffer.append(iterator.next());
                }

                buffer.append(" )");
            } else {
                buffer.append(" SUP ");
                buffer.append(firstName);
            }
        }

        if (objectClassType != null) {
            buffer.append(" ");
            buffer.append(objectClassType);
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
    }

    boolean validate(final Schema schema, final List<ObjectClass> invalidSchemaElements,
            final List<LocalizableMessage> warnings) {
        // Avoid validating this schema element more than once.
        // This may occur if multiple object classes specify the same superior.
        if (!needsValidating) {
            return isValid;
        }

        // Prevent re-validation.
        needsValidating = false;

        // Init a flag to check to inheritance from top (only needed for
        // structural object classes) per RFC 4512
        boolean derivesTop = getObjectClassType() != ObjectClassType.STRUCTURAL;

        if (!superiorClassOIDs.isEmpty()) {
            superiorClasses = new HashSet<>(superiorClassOIDs.size());
            ObjectClass superiorClass;
            for (final String superClassOid : superiorClassOIDs) {
                try {
                    superiorClass = schema.getObjectClass(superClassOid);
                } catch (final UnknownSchemaElementException e) {
                    final LocalizableMessage message =
                            WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_SUPERIOR_CLASS1.get(
                                    getNameOrOID(), superClassOid);
                    failValidation(invalidSchemaElements, warnings, message);
                    return false;
                }

                // Make sure that the inheritance configuration is acceptable.
                final ObjectClassType superiorType = superiorClass.getObjectClassType();
                final ObjectClassType type = getObjectClassType();
                switch (type) {
                case ABSTRACT:
                    // Abstract classes may only inherit from other abstract classes.
                    if (superiorType != ObjectClassType.ABSTRACT) {
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE1.get(
                                        getNameOrOID(), type.toString(), superiorType
                                                .toString(), superiorClass.getNameOrOID());
                        failValidation(invalidSchemaElements, warnings, message);
                        return false;
                    }
                    break;

                case AUXILIARY:
                    // Auxiliary classes may only inherit from abstract classes
                    // or other auxiliary classes.
                    if (superiorType != ObjectClassType.ABSTRACT
                            && superiorType != ObjectClassType.AUXILIARY) {
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE1.get(
                                        getNameOrOID(), type.toString(), superiorType
                                                .toString(), superiorClass.getNameOrOID());
                        failValidation(invalidSchemaElements, warnings, message);
                        return false;
                    }
                    break;

                case STRUCTURAL:
                    // Structural classes may only inherit from abstract classes
                    // or other structural classes.
                    if (superiorType != ObjectClassType.ABSTRACT
                            && superiorType != ObjectClassType.STRUCTURAL) {
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE1.get(
                                        getNameOrOID(), type.toString(), superiorType
                                                .toString(), superiorClass.getNameOrOID());
                        failValidation(invalidSchemaElements, warnings, message);
                        return false;
                    }
                    break;
                }

                // All existing structural object classes defined in this schema
                // are implicitly guaranteed to inherit from top.
                if (!derivesTop && superiorType == ObjectClassType.STRUCTURAL) {
                    derivesTop = true;
                }

                // First ensure that the superior has been validated and fail if
                // it is invalid.
                if (!superiorClass.validate(schema, invalidSchemaElements, warnings)) {
                    final LocalizableMessage message =
                            WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_CLASS.get(getNameOrOID(),
                                    superClassOid);
                    failValidation(invalidSchemaElements, warnings, message);
                    return false;
                }

                // Inherit all required attributes from superior class.
                Iterator<AttributeType> i = superiorClass.getRequiredAttributes().iterator();
                if (i.hasNext() && requiredAttributes == Collections.EMPTY_SET) {
                    requiredAttributes = new HashSet<>();
                }
                while (i.hasNext()) {
                    requiredAttributes.add(i.next());
                }

                // Inherit all optional attributes from superior class.
                i = superiorClass.getRequiredAttributes().iterator();
                if (i.hasNext() && requiredAttributes == Collections.EMPTY_SET) {
                    requiredAttributes = new HashSet<>();
                }
                while (i.hasNext()) {
                    requiredAttributes.add(i.next());
                }

                superiorClasses.add(superiorClass);
            }
        }

        if (!derivesTop) {
            derivesTop = isDescendantOf(schema.getObjectClass("2.5.6.0"));
        }

        // Structural classes must have the "top" objectclass somewhere
        // in the superior chain.
        if (!derivesTop) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_OBJECTCLASS_STRUCTURAL_SUPERIOR_NOT_TOP1.get(getNameOrOID());
            failValidation(invalidSchemaElements, warnings, message);
            return false;
        }

        if (oid.equals(EXTENSIBLE_OBJECT_OBJECTCLASS_OID)) {
            declaredOptionalAttributes = new HashSet<>(requiredAttributeOIDs.size());
            for (final AttributeType attributeType : schema.getAttributeTypes()) {
                if (attributeType.getUsage() == AttributeUsage.USER_APPLICATIONS) {
                    declaredOptionalAttributes.add(attributeType);
                }
            }
            optionalAttributes = declaredRequiredAttributes;
        } else {
            if (!requiredAttributeOIDs.isEmpty()) {
                declaredRequiredAttributes = new HashSet<>(requiredAttributeOIDs.size());
                AttributeType attributeType;
                for (final String requiredAttribute : requiredAttributeOIDs) {
                    try {
                        attributeType = schema.getAttributeType(requiredAttribute);
                    } catch (final UnknownSchemaElementException e) {
                        // This isn't good because it means that the objectclass
                        // requires an attribute type that we don't know anything about.
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_REQUIRED_ATTR1.get(
                                        getNameOrOID(), requiredAttribute);
                        failValidation(invalidSchemaElements, warnings, message);
                        return false;
                    }
                    declaredRequiredAttributes.add(attributeType);
                }
                if (requiredAttributes == Collections.EMPTY_SET) {
                    requiredAttributes = declaredRequiredAttributes;
                } else {
                    requiredAttributes.addAll(declaredRequiredAttributes);
                }
            }

            if (!optionalAttributeOIDs.isEmpty()) {
                declaredOptionalAttributes = new HashSet<>(optionalAttributeOIDs.size());
                AttributeType attributeType;
                for (final String optionalAttribute : optionalAttributeOIDs) {
                    try {
                        attributeType = schema.getAttributeType(optionalAttribute);
                    } catch (final UnknownSchemaElementException e) {
                        // This isn't good because it means that the objectclass
                        // requires an attribute type that we don't know anything about.
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_OPTIONAL_ATTR1.get(
                                        getNameOrOID(), optionalAttribute);
                        failValidation(invalidSchemaElements, warnings, message);
                        return false;
                    }
                    declaredOptionalAttributes.add(attributeType);
                }
                if (optionalAttributes == Collections.EMPTY_SET) {
                    optionalAttributes = declaredOptionalAttributes;
                } else {
                    optionalAttributes.addAll(declaredOptionalAttributes);
                }
            }
        }

        declaredOptionalAttributes = Collections.unmodifiableSet(declaredOptionalAttributes);
        declaredRequiredAttributes = Collections.unmodifiableSet(declaredRequiredAttributes);
        optionalAttributes = Collections.unmodifiableSet(optionalAttributes);
        requiredAttributes = Collections.unmodifiableSet(requiredAttributes);
        superiorClasses = Collections.unmodifiableSet(superiorClasses);

        return isValid = true;
    }

    private void failValidation(final List<ObjectClass> invalidSchemaElements,
            final List<LocalizableMessage> warnings, final LocalizableMessage message) {
        invalidSchemaElements.add(this);
        warnings.add(ERR_OC_VALIDATION_FAIL.get(toString(), message));
    }
}
