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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import org.forgerock.util.Reject;

import java.util.EnumSet;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * Attribute type property definition.
 */
public final class AttributeTypePropertyDefinition extends PropertyDefinition<AttributeType> {

    /**
     * An interface for incrementally constructing attribute type property
     * definitions.
     */
    public static final class Builder extends AbstractBuilder<AttributeType, AttributeTypePropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitAttributeType(this, p);
    }

    /** {@inheritDoc} */
    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, AttributeType value, P p) {
        return v.visitAttributeType(this, value, p);
    }

    /** {@inheritDoc} */
    @Override
    public int compare(AttributeType o1, AttributeType o2) {
        return o1.getNameOrOID().compareToIgnoreCase(o2.getNameOrOID());
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public String encodeValue(AttributeType value) {
        return value.getNameOrOID();
    }

    /** {@inheritDoc} */
    @Override
    public void validateValue(AttributeType value) {
        Reject.ifNull(value);

        // No implementation required.
    }
}
