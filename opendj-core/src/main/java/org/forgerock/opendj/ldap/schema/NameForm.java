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

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL1;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR1;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR1;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS1;

import java.util.Arrays;
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

/**
 * This class defines a data structure for storing and interacting with a name
 * form, which defines the attribute type(s) that must and/or may be used in the
 * RDN of an entry with a given structural objectclass.
 */
public final class NameForm extends SchemaElement {

    /** A fluent API for incrementally constructing name forms. */
    public static final class Builder extends SchemaElementBuilder<Builder> {
        private boolean isObsolete;
        private final List<String> names = new LinkedList<>();
        private String oid;
        private final Set<String> optionalAttributes = new LinkedHashSet<>();
        private final Set<String> requiredAttributes = new LinkedHashSet<>();
        private String structuralObjectClassOID;

        Builder(final NameForm nf, final SchemaBuilder builder) {
            super(builder, nf);
            this.oid = nf.oid;
            this.structuralObjectClassOID = nf.structuralClassOID;
            this.isObsolete = nf.isObsolete;
            this.names.addAll(nf.names);
            this.requiredAttributes.addAll(nf.requiredAttributeOIDs);
            this.optionalAttributes.addAll(nf.optionalAttributeOIDs);
        }

        Builder(final String oid, final SchemaBuilder builder) {
            super(builder);
            oid(oid);
        }

        /**
         * Adds this name form to the schema, throwing a
         * {@code ConflictingSchemaElementException} if there is an existing
         * name form with the same numeric OID.
         *
         * @return The parent schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing name form with the same numeric
         *             OID.
         */
        public SchemaBuilder addToSchema() {
            return getSchemaBuilder().addNameForm(new NameForm(this), false);
        }

        /**
         * Adds this name form to the schema overwriting any existing name form
         * with the same numeric OID.
         *
         * @return The parent schema builder.
         */
        public SchemaBuilder addToSchemaOverwrite() {
            return getSchemaBuilder().addNameForm(new NameForm(this), true);
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
            return names(Arrays.asList(names));
        }

        /**
         * Specifies whether or not this schema element is obsolete.
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
         * Sets the numeric OID which uniquely identifies this name form.
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
         * @param nameOrOIDs
         *            The list of optional attributes.
         * @return This builder.
         */
        public Builder optionalAttributes(final Collection<String> nameOrOIDs) {
            this.optionalAttributes.addAll(nameOrOIDs);
            return this;
        }

        /**
         * Adds the provided optional attributes.
         *
         * @param nameOrOIDs
         *            The list of optional attributes.
         * @return This builder.
         */
        public Builder optionalAttributes(final String... nameOrOIDs) {
            return optionalAttributes(Arrays.asList(nameOrOIDs));
        }

        @Override
        public Builder removeAllExtraProperties() {
            return removeAllExtraProperties0();
        }

        /**
         * Removes all user friendly names.
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

        @Override
        public Builder removeExtraProperty(final String extensionName,
                final String... extensionValues) {
            return removeExtraProperty0(extensionName, extensionValues);
        }

        /**
         * Removes the provided user friendly name.
         *
         * @param name
         *            The user friendly name to be removed.
         * @return This builder.
         */
        public Builder removeName(final String name) {
            names.remove(name);
            return this;
        }

        /**
         * Removes the specified optional attribute.
         *
         * @param nameOrOID
         *            The optional attribute to be removed.
         * @return This builder.
         */
        public Builder removeOptionalAttribute(final String nameOrOID) {
            this.optionalAttributes.remove(nameOrOID);
            return this;
        }

        /**
         * Removes the specified required attribute.
         *
         * @param nameOrOID
         *            The required attribute to be removed.
         * @return This builder.
         */
        public Builder removeRequiredAttribute(final String nameOrOID) {
            this.requiredAttributes.remove(nameOrOID);
            return this;
        }

        /**
         * Adds the provided required attributes.
         *
         * @param nameOrOIDs
         *            The list of required attributes.
         * @return This builder.
         */
        public Builder requiredAttributes(final Collection<String> nameOrOIDs) {
            this.requiredAttributes.addAll(nameOrOIDs);
            return this;
        }

        /**
         * Adds the provided required attributes.
         *
         * @param nameOrOIDs
         *            The list of required attributes.
         * @return This builder.
         */
        public Builder requiredAttributes(final String... nameOrOIDs) {
            return requiredAttributes(Arrays.asList(nameOrOIDs));
        }

        /**
         * Sets the structural object class.
         *
         * @param nameOrOID
         *            The structural object class.
         * @return This builder.
         */
        public Builder structuralObjectClassOID(final String nameOrOID) {
            this.structuralObjectClassOID = nameOrOID;
            return this;
        }

        @Override
        Builder getThis() {
            return this;
        }
    }

    /** Indicates whether this definition is declared "obsolete". */
    private final boolean isObsolete;

    /** The set of user defined names for this definition. */
    private final List<String> names;

    /** The OID that may be used to reference this definition. */
    private final String oid;

    /** The set of optional attribute types for this name form. */
    private final Set<String> optionalAttributeOIDs;
    private Set<AttributeType> optionalAttributes = Collections.emptySet();

    /** The set of required attribute types for this name form. */
    private final Set<String> requiredAttributeOIDs;
    private Set<AttributeType> requiredAttributes = Collections.emptySet();

    /** The reference to the structural objectclass for this name form. */
    private ObjectClass structuralClass;
    private final String structuralClassOID;

    private NameForm(final Builder builder) {
        super(builder);

        // Checks for required attributes.
        if (builder.oid == null || builder.oid.isEmpty()) {
            throw new IllegalArgumentException("An OID must be specified.");
        }
        if (builder.structuralObjectClassOID == null || builder.structuralObjectClassOID.isEmpty()) {
            throw new IllegalArgumentException("A structural class OID must be specified.");
        }
        if (builder.requiredAttributes == null || builder.requiredAttributes.isEmpty()) {
            throw new IllegalArgumentException("Required attribute must be specified.");
        }

        oid = builder.oid;
        structuralClassOID = builder.structuralObjectClassOID;
        names = SchemaUtils.unmodifiableCopyOfList(builder.names);
        requiredAttributeOIDs = SchemaUtils.unmodifiableCopyOfSet(builder.requiredAttributes);
        optionalAttributeOIDs = SchemaUtils.unmodifiableCopyOfSet(builder.optionalAttributes);
        isObsolete = builder.isObsolete;
    }

    /**
     * Returns {@code true} if the provided object is a name form having the
     * same numeric OID as this name form.
     *
     * @param o
     *            The object to be compared.
     * @return {@code true} if the provided object is a name form having the
     *         same numeric OID as this name form.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof NameForm) {
            final NameForm other = (NameForm) o;
            return oid.equals(other.oid);
        } else {
            return false;
        }
    }

    /**
     * Returns the name or numeric OID of this name form. If it has one or more
     * names, then the primary name will be returned. If it does not have any
     * names, then the numeric OID will be returned.
     *
     * @return The name or numeric OID of this name form.
     */
    public String getNameOrOID() {
        if (names.isEmpty()) {
            return oid;
        }
        return names.get(0);
    }

    /**
     * Returns an unmodifiable list containing the user-friendly names that may
     * be used to reference this name form.
     *
     * @return An unmodifiable list containing the user-friendly names that may
     *         be used to reference this name form.
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Returns the numeric OID of this name form.
     *
     * @return The numeric OID of this name form.
     */
    public String getOID() {
        return oid;
    }

    /**
     * Returns an unmodifiable set containing the optional attributes of this
     * name form.
     *
     * @return An unmodifiable set containing the optional attributes of this
     *         name form.
     */
    public Set<AttributeType> getOptionalAttributes() {
        return optionalAttributes;
    }

    /**
     * Returns an unmodifiable set containing the required attributes of this
     * name form.
     *
     * @return An unmodifiable set containing the required attributes of this
     *         name form.
     */
    public Set<AttributeType> getRequiredAttributes() {
        return requiredAttributes;
    }

    /**
     * Returns the structural objectclass of this name form.
     *
     * @return The structural objectclass of this name form.
     */
    public ObjectClass getStructuralClass() {
        return structuralClass;
    }

    /**
     * Returns the hash code for this name form. It will be calculated as the
     * hash code of the numeric OID.
     *
     * @return The hash code for this name form.
     */
    @Override
    public int hashCode() {
        return oid.hashCode();
    }

    /**
     * Returns {@code true} if this name form has the specified user-friendly
     * name.
     *
     * @param name
     *            The name.
     * @return {@code true} if this name form has the specified user-friendly
     *         name.
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
     * Returns {@code true} if this name form has the specified user-friendly
     * name or numeric OID.
     *
     * @param nameOrOID
     *            The name or numeric OID.
     * @return {@code true} if this name form has the specified user-friendly
     *         name or numeric OID.
     */
    public boolean hasNameOrOID(final String nameOrOID) {
        return hasName(nameOrOID) || getOID().equals(nameOrOID);
    }

    /**
     * Returns {@code true} if this name form is "obsolete".
     *
     * @return {@code true} if this name form is "obsolete".
     */
    public boolean isObsolete() {
        return isObsolete;
    }

    /**
     * Returns {@code true} if the provided attribute type is included in the
     * list of optional attributes for this name form.
     *
     * @param attributeType
     *            The attribute type.
     * @return {@code true} if the provided attribute type is included in the
     *         list of optional attributes for this name form.
     */
    public boolean isOptional(final AttributeType attributeType) {
        return optionalAttributes.contains(attributeType);
    }

    /**
     * Returns {@code true} if the provided attribute type is included in the
     * list of required attributes for this name form.
     *
     * @param attributeType
     *            The attribute type.
     * @return {@code true} if the provided attribute type is included in the
     *         list of required attributes for this name form.
     */
    public boolean isRequired(final AttributeType attributeType) {
        return requiredAttributes.contains(attributeType);
    }

    /**
     * Returns {@code true} if the provided attribute type is included in the
     * list of optional or required attributes for this name form.
     *
     * @param attributeType
     *            The attribute type.
     * @return {@code true} if the provided attribute type is included in the
     *         list of optional or required attributes for this name form.
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

        buffer.append(" OC ");
        buffer.append(structuralClassOID);

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

    void validate(final Schema schema, final List<LocalizableMessage> warnings) throws SchemaException {
        try {
            structuralClass = schema.getObjectClass(structuralClassOID);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS1.get(getNameOrOID(),
                            structuralClassOID);
            throw new SchemaException(message, e);
        }
        if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL) {
            // This is bad because the associated structural class type is not structural.
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL1.get(getNameOrOID(),
                            structuralClass.getNameOrOID(), structuralClass.getObjectClassType());
            throw new SchemaException(message);
        }

        requiredAttributes =
              getAttributeTypes(schema, requiredAttributeOIDs, ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR1);

        if (!optionalAttributeOIDs.isEmpty()) {
            optionalAttributes =
                    getAttributeTypes(schema, optionalAttributeOIDs, ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR1);
        }

        optionalAttributes = Collections.unmodifiableSet(optionalAttributes);
        requiredAttributes = Collections.unmodifiableSet(requiredAttributes);
    }

    private Set<AttributeType> getAttributeTypes(final Schema schema, Set<String> oids, Arg2<Object, Object> errorMsg)
            throws SchemaException {
        Set<AttributeType> attrTypes = new HashSet<>(oids.size());
        for (final String oid : oids) {
            try {
                attrTypes.add(schema.getAttributeType(oid));
            } catch (final UnknownSchemaElementException e) {
                // This isn't good because it means that the name form requires
                // an attribute type that we don't know anything about.
                throw new SchemaException(errorMsg.get(getNameOrOID(), oid), e);
            }
        }
        return attrTypes;
    }
}
