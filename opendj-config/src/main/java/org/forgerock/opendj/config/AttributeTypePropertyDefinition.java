/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import org.forgerock.util.Reject;

import java.util.EnumSet;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;

/** Attribute type property definition. */
public final class AttributeTypePropertyDefinition extends PropertyDefinition<AttributeType> {

    /** An interface for incrementally constructing attribute type property definitions. */
    public static final class Builder extends AbstractBuilder<AttributeType, AttributeTypePropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        @Override
        protected AttributeTypePropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<AttributeType> defaultBehavior) {
            return new AttributeTypePropertyDefinition(d, propertyName, options, adminAction, defaultBehavior);
        }
    }

    /**
     * Create a attribute type property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new attribute type property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Private constructor. */
    private AttributeTypePropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<AttributeType> defaultBehavior) {
        super(d, AttributeType.class, propertyName, options, adminAction, defaultBehavior);
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitAttributeType(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, AttributeType value, P p) {
        return v.visitAttributeType(this, value, p);
    }

    @Override
    public int compare(AttributeType o1, AttributeType o2) {
        return o1.getNameOrOID().compareToIgnoreCase(o2.getNameOrOID());
    }

    @Override
    public AttributeType decodeValue(String value) {
        Reject.ifNull(value);

        final String name = value.trim();
        if (!ConfigurationFramework.getInstance().isClient()
                && !Schema.getDefaultSchema().hasAttributeType(name)) {
            // If this is the server then the attribute type must be defined.
            throw PropertyException.illegalPropertyValueException(this, value);
        }
        final AttributeType type =
                Schema.getDefaultSchema().asNonStrictSchema().getAttributeType(name);
        try {
            validateValue(type);
            return type;
        } catch (PropertyException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    @Override
    public String encodeValue(AttributeType value) {
        return value.getNameOrOID();
    }

    @Override
    public void validateValue(AttributeType value) {
        Reject.ifNull(value);

        // No implementation required.
    }
}
