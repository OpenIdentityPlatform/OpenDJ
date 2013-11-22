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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL1;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR1;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR1;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS1;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class defines a data structure for storing and interacting with a name
 * form, which defines the attribute type(s) that must and/or may be used in the
 * RDN of an entry with a given structural objectclass.
 */
public final class NameForm extends SchemaElement {

    // The OID that may be used to reference this definition.
    private final String oid;

    // The set of user defined names for this definition.
    private final List<String> names;

    // Indicates whether this definition is declared "obsolete".
    private final boolean isObsolete;

    // The reference to the structural objectclass for this name form.
    private final String structuralClassOID;

    // The set of optional attribute types for this name form.
    private final Set<String> optionalAttributeOIDs;

    // The set of required attribute types for this name form.
    private final Set<String> requiredAttributeOIDs;

    // The definition string used to create this objectclass.
    private final String definition;

    private ObjectClass structuralClass;
    private Set<AttributeType> optionalAttributes = Collections.emptySet();
    private Set<AttributeType> requiredAttributes = Collections.emptySet();

    /**
     * The name form builder.
     */
    public static class Builder extends SchemaElementBuilder<Builder> {

        // Required attributes
        private String oid;
        private String structuralObjectClassOID;
        private Set<String> requiredAttribute = new LinkedHashSet<String>();

        // Optional attributes - initialized to default values.
        private List<String> names = new LinkedList<String>();
        private Set<String> optionalAttributes = new LinkedHashSet<String>();
        private String definition;
        private boolean isObsolete = false;

        /**
         * Sets the OID of the name form definition.
         * <p>
         * RFC 4512 : numericoid ; object identifier.
         *
         * @param oid
         *            Like 1.3.6.1.4.1.1466.115.121.1.35.
         * @return This name form builder.
         */
        public Builder oid(final String oid) {
            this.oid = oid;
            return this;
        }

        /**
         * Sets the structural object class OID.
         * <p>
         * e.g : OC person.
         *
         * @param oid
         *            = SP "OC" SP oid (RFC 4512).
         * @return This name form builder.
         */
        public Builder structuralObjectClassOID(final String oid) {
            this.structuralObjectClassOID = oid;
            return this;
        }

        /**
         * Sets the user defined names for this definition.
         * <p>
         * RFC 4512 : [ SP "NAME" SP qdescrs ] ; short names (descriptors).
         *
         * @param names
         *            Contains a collection of strings.
         * @return This name form builder.
         */
        public Builder names(final Collection<String> names) {
            this.names.addAll(names);
            return this;
        }

        /**
         * Sets the user defined names for this definition.
         * <p>
         * RFC 4512 : [ SP "NAME" SP qdescrs ] ; short names (descriptors).
         *
         * @param names
         *            Contains a series of strings.
         * @return This name form builder.
         */
        public Builder names(final String... names) {
            return names(Arrays.asList(names));
        }

        /**
         * Erases all the names.
         *
         * @return This name form builder.
         */
        public Builder removeAllNames() {
            this.names.clear();
            return this;
        }

        /**
         * Removes the defined name.
         *
         * @param name
         *            The name to remove.
         * @return This name form builder.
         */
        public Builder removeName(String name) {
            names.remove(name);
            return this;
        }

        /**
         * Specifies which attributes are required by this name form.
         * <p>
         * RFC 4512 : SP "MUST" SP oids ; attribute types.
         *
         * @param oids
         *            The OIDs of the required attributes.
         * @return This name form builder.
         */
        public Builder requiredAttributes(final String... oids) {
            return requiredAttributes(Arrays.asList(oids));
        }

        /**
         * Specifies which attributes are required by this name form.
         * <p>
         * RFC 4512 : SP "MUST" SP oids ; attribute types.
         *
         * @param oids
         *            The OIDs of the required attributes.
         * @return This name form builder.
         */
        public Builder requiredAttributes(final Collection<String> oids) {
            this.requiredAttribute.addAll(oids);
            return this;
        }

        /**
         * Removes the specified required attribute.
         *
         * @param oid
         *            The OID of the required attributes.
         * @return This name form builder.
         */
        public Builder removeRequiredAttribute(final String oid) {
            this.requiredAttribute.remove(oid);
            return this;
        }

        /**
         * Removes all the required attributes.
         *
         * @return This name form builder.
         */
        public Builder removeAllRequiredAttributes() {
            this.requiredAttribute.clear();
            return this;
        }

        /**
         * Sets the optional attribute OIDs.
         * <p>
         * RFC 4512 : [ SP "MAY" SP oids ] ; attribute types.
         *
         * @param oids
         *            The OIDs of the optional attributes.
         * @return This name form builder.
         */
        public Builder optionalAttributes(final String... oids) {
            return optionalAttributes(Arrays.asList(oids));
        }

        /**
         * Sets the optional attributes.
         * <p>
         * RFC 4512 : [ SP "MAY" SP oids ] ; attribute types.
         *
         * @param oids
         *            The OIDs of the optional attributes.
         * @return This name form builder.
         */
        public Builder optionalAttributes(final Collection<String> oids) {
            this.optionalAttributes.addAll(oids);
            return this;
        }

        /**
         * Removes the specified attributes.
         *
         * @param oid
         *            The OID of the optional attributes.
         * @return This name form builder.
         */
        public Builder removeOptionalAttribute(final String oid) {
            this.optionalAttributes.remove(oid);
            return this;
        }

        /**
         * Removes all the optional attributes.
         *
         * @return This name form builder.
         */
        public Builder removeAllOptionalAttributes() {
            this.optionalAttributes.clear();
            return this;
        }

        /**
         * {@code true} if the object class definition is obsolete, otherwise
         * {@code false}.
         * <p>
         * RFC 4512 : [ SP "OBSOLETE" ] ; not active.
         *
         * @param isObsolete
         *            default is {@code false}.
         * @return This name form builder.
         */
        public Builder obsolete(final boolean isObsolete) {
            this.isObsolete = isObsolete;
            return this;
        }

        /**
         * Sets the definition string used to create this object class.
         *
         * @param definition
         *            The definition to set.
         * @return This name form builder.
         */
        Builder definition(final String definition) {
            this.definition = definition;
            return this;
        }

        /**
         * Returns the builder.
         *
         * @return This name form builder.
         */
        @Override
        Builder getThis() {
            return this;
        }

        /**
         * Creates a new name form builder implementation.
         *
         * @param oid
         *            The OID of the name form definition.
         * @param builder
         *            The schema builder linked.
         */
        Builder(final String oid, final SchemaBuilder builder) {
            this.oid(oid);
            this.schemaBuilder(builder);
        }

        /**
         * Duplicates an existing name form builder.
         *
         * @param nf
         *            The name form to duplicate.
         * @param builder
         *            The schema builder where to adds this new name form
         * @throws ConflictingSchemaElementException
         *             If {@code overwrite} was {@code false} and a conflicting
         *             schema element was found.
         */
        Builder(final NameForm nf, final SchemaBuilder builder) {
            this.oid = nf.oid;
            this.definition = nf.buildDefinition();
            this.description(nf.description);
            this.structuralObjectClassOID = nf.structuralClassOID;
            this.isObsolete = nf.isObsolete;
            this.names = new ArrayList<String>(nf.names);
            this.extraProperties(new LinkedHashMap<String, List<String>>(nf.extraProperties));
            this.requiredAttribute = new LinkedHashSet<String>(nf.requiredAttributeOIDs);
            this.optionalAttributes = new LinkedHashSet<String>(nf.optionalAttributeOIDs);
            this.schemaBuilder(builder);
        }

        /**
         * Adds the name form to the builder overwriting any existing name form
         * with the same OID.
         *
         * @return A schema builder.
         */
        public SchemaBuilder addToSchema() {
            return this.getSchemaBuilder().addNameForm(new NameForm(this), true);
        }

        /**
         * Adds the name form to the builder throwing an
         * ConflictingSchemaElementException if there is an existing name form
         * with the same OID.
         *
         * @return A schema builder.
         * @throws ConflictingSchemaElementException
         *             If there is an existing name form with the same OID.
         */
        public SchemaBuilder addNoOverwriteToSchema() {
            return this.getSchemaBuilder().addNameForm(new NameForm(this), false);
        }
    }

    private NameForm(final Builder builder) {
        super(builder.description, builder.extraProperties);
        // Checks for required attributes.
        if (builder.oid == null || builder.oid.isEmpty()) {
            throw new IllegalArgumentException("An OID must be specified.");
        }
        if (builder.structuralObjectClassOID == null || builder.structuralObjectClassOID.isEmpty()) {
            throw new IllegalArgumentException("A structural class OID must be specified.");
        }
        if (builder.requiredAttribute == null || builder.requiredAttribute.isEmpty()) {
            throw new IllegalArgumentException("Required attribute must be specified.");
        }

        oid = builder.oid;
        structuralClassOID = builder.structuralObjectClassOID;
        names = SchemaUtils.unmodifiableCopyOfList(builder.names);
        requiredAttributeOIDs = SchemaUtils.unmodifiableCopyOfSet(builder.requiredAttribute);
        optionalAttributeOIDs = SchemaUtils.unmodifiableCopyOfSet(builder.optionalAttributes);
        isObsolete = builder.isObsolete;

        definition = buildDefinition();

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
     * Returns an unmodifiable set containing the optional attributes for this
     * name form.
     *
     * @return An unmodifiable set containing the optional attributes for this
     *         name form.
     */
    public Set<AttributeType> getOptionalAttributes() {
        return optionalAttributes;
    }

    /**
     * Returns an unmodifiable set containing the required attributes for this
     * name form.
     *
     * @return An unmodifiable set containing the required attributes for this
     *         name form.
     */
    public Set<AttributeType> getRequiredAttributes() {
        return requiredAttributes;
    }

    /**
     * Returns the reference to the structural objectclass for this name form.
     *
     * @return The reference to the structural objectclass for this name form.
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
     * Indicates whether the provided attribute type is included in the optional
     * attribute list for this name form.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is optional for
     *         this name form, or <code>false</code> if not.
     */
    public boolean isOptional(final AttributeType attributeType) {
        return optionalAttributes.contains(attributeType);
    }

    /**
     * Indicates whether the provided attribute type is included in the required
     * attribute list for this name form.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is required by
     *         this name form, or <code>false</code> if not.
     */
    public boolean isRequired(final AttributeType attributeType) {
        return requiredAttributes.contains(attributeType);
    }

    /**
     * Indicates whether the provided attribute type is in the list of required
     * or optional attributes for this name form.
     *
     * @param attributeType
     *            The attribute type for which to make the determination.
     * @return <code>true</code> if the provided attribute type is required or
     *         allowed for this name form, or <code>false</code> if it is not.
     */
    public boolean isRequiredOrOptional(final AttributeType attributeType) {
        return isRequired(attributeType) || isOptional(attributeType);
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

    void validate(final Schema schema, final List<LocalizableMessage> warnings)
            throws SchemaException {
        try {
            structuralClass = schema.getObjectClass(structuralClassOID);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS1.get(getNameOrOID(),
                            structuralClassOID);
            throw new SchemaException(message, e);
        }
        if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL) {
            // This is bad because the associated structural class type is not
            // structural.
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL1.get(getNameOrOID(),
                            structuralClass.getNameOrOID(), String.valueOf(structuralClass
                                    .getObjectClassType()));
            throw new SchemaException(message);
        }

        requiredAttributes = new HashSet<AttributeType>(requiredAttributeOIDs.size());
        AttributeType attributeType;
        for (final String oid : requiredAttributeOIDs) {
            try {
                attributeType = schema.getAttributeType(oid);
            } catch (final UnknownSchemaElementException e) {
                // This isn't good because it means that the name form requires
                // an attribute type that we don't know anything about.
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR1.get(getNameOrOID(), oid);
                throw new SchemaException(message, e);
            }
            requiredAttributes.add(attributeType);
        }

        if (!optionalAttributeOIDs.isEmpty()) {
            optionalAttributes = new HashSet<AttributeType>(optionalAttributeOIDs.size());
            for (final String oid : optionalAttributeOIDs) {
                try {
                    attributeType = schema.getAttributeType(oid);
                } catch (final UnknownSchemaElementException e) {
                    // This isn't good because it means that the name form
                    // requires an attribute type that we don't know anything
                    // about.
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR1.get(getNameOrOID(),
                                    oid);
                    throw new SchemaException(message, e);
                }
                optionalAttributes.add(attributeType);
            }
        }

        optionalAttributes = Collections.unmodifiableSet(optionalAttributes);
        requiredAttributes = Collections.unmodifiableSet(requiredAttributes);
    }
}
