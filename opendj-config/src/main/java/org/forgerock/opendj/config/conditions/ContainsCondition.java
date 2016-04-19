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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.conditions;

import java.util.SortedSet;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Reject;

/**
 * A condition which evaluates to <code>true</code> if and only if a property
 * contains a particular value.
 */
public final class ContainsCondition implements Condition {

    /**
     * The strongly typed underlying implementation.
     *
     * @param <T>
     *            The type of the property value being tested.
     */
    private static final class Impl<T> implements Condition {

        /** The property. */
        final PropertyDefinition<T> pd;

        /** The required property value. */
        final T value;

        /** Private constructor. */
        private Impl(PropertyDefinition<T> pd, T value) {
            this.pd = pd;
            this.value = value;
        }

        @Override
        public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
            SortedSet<T> values = managedObject.getPropertyValues(pd);
            return values.contains(value);
        }

        @Override
        public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
            SortedSet<T> values = managedObject.getPropertyValues(pd);
            return values.contains(value);
        }

        @Override
        public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
            // Not used.
        }

        /** Private implementation of fix() method. */
        private void setPropertyValue(ManagedObject<?> managedObject) {
            managedObject.setPropertyValue(pd, value);
        }

    }

    /** The strongly typed private implementation. */
    private Impl<?> impl;

    /** The property name. */
    private final String propertyName;

    /** The string representation of the required property value. */
    private final String propertyStringValue;

    /**
     * Creates a new contains value condition.
     *
     * @param propertyName
     *            The property name.
     * @param stringValue
     *            The string representation of the required property value.
     */
    public ContainsCondition(String propertyName, String stringValue) {
        Reject.ifNull(propertyName, stringValue);
        this.propertyName = propertyName;
        this.propertyStringValue = stringValue;
    }

    @Override
    public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
        return impl.evaluate(context, managedObject);
    }

    @Override
    public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
        return impl.evaluate(managedObject);
    }

    /**
     * Modifies the provided managed object so that it has the property value
     * associated with this condition.
     *
     * @param managedObject
     *            The managed object.
     */
    public void setPropertyValue(ManagedObject<?> managedObject) {
        impl.setPropertyValue(managedObject);
    }

    @Override
    public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
        // Decode the property.
        buildImpl(d.getPropertyDefinition(propertyName));
    }

    /** Creates the new private implementation. */
    private <T> void buildImpl(PropertyDefinition<T> pd) {
        T value = pd.decodeValue(propertyStringValue);
        this.impl = new Impl<>(pd, value);
    }

    /**
     * Returns the property definition associated with this condition.
     *
     * @return the property definition associated with this condition.
     */
    public PropertyDefinition<?> getPropertyDefinition() {
        return impl.pd;
    }

    /**
     * Returns the value that must be set for this condition to be fulfilled.
     *
     * @return the value that must be set for this condition to be fulfilled.
     */
    public Object getValue() {
        return impl.value;
    }
}
