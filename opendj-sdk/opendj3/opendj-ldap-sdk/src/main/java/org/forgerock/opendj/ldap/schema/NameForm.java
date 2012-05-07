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
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL1;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR1;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR1;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS1;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;

import com.forgerock.opendj.util.Validator;

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

    NameForm(final String oid, final List<String> names, final String description,
            final boolean obsolete, final String structuralClassOID,
            final Set<String> requiredAttributeOIDs, final Set<String> optionalAttributeOIDs,
            final Map<String, List<String>> extraProperties, final String definition) {
        super(description, extraProperties);

        Validator.ensureNotNull(oid, names);
        Validator.ensureNotNull(structuralClassOID, requiredAttributeOIDs, optionalAttributeOIDs);
        Validator.ensureTrue(requiredAttributeOIDs.size() > 0, "required attribute is empty");
        this.oid = oid;
        this.names = names;
        this.isObsolete = obsolete;
        this.structuralClassOID = structuralClassOID;
        this.requiredAttributeOIDs = requiredAttributeOIDs;
        this.optionalAttributeOIDs = optionalAttributeOIDs;

        if (definition != null) {
            this.definition = definition;
        } else {
            this.definition = buildDefinition();
        }
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

    NameForm duplicate() {
        return new NameForm(oid, names, description, isObsolete, structuralClassOID,
                requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, definition);
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
