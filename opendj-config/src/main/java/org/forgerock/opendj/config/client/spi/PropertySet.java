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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config.client.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyOption;

/**
 * A set of properties. Instances of this class can be used as the core of a
 * managed object implementation.
 */
public final class PropertySet {

    /**
     * Internal property implementation.
     *
     * @param <T>
     *            The type of the property.
     */
    private static final class MyProperty<T> implements Property<T> {

        /** The active set of values. */
        private final SortedSet<T> activeValues;

        /** The definition associated with this property. */
        private final PropertyDefinition<T> d;

        /** The default set of values (read-only). */
        private final SortedSet<T> defaultValues;

        /** The pending set of values. */
        private final SortedSet<T> pendingValues;

        /**
         * Create a property with the provided sets of pre-validated default and
         * active values.
         *
         * @param pd
         *            The property definition.
         * @param defaultValues
         *            The set of default values for the property.
         * @param activeValues
         *            The set of active values for the property.
         */
        public MyProperty(PropertyDefinition<T> pd, Collection<T> defaultValues, Collection<T> activeValues) {
            this.d = pd;

            SortedSet<T> sortedDefaultValues = new TreeSet<>(pd);
            sortedDefaultValues.addAll(defaultValues);
            this.defaultValues = Collections.unmodifiableSortedSet(sortedDefaultValues);

            this.activeValues = new TreeSet<>(pd);
            this.activeValues.addAll(activeValues);

            // Initially the pending values is the same as the active values.
            this.pendingValues = new TreeSet<>(this.activeValues);
        }

        /** Makes the pending values active. */
        public void commit() {
            activeValues.clear();
            activeValues.addAll(pendingValues);
        }

        @Override
        public SortedSet<T> getActiveValues() {
            return Collections.unmodifiableSortedSet(activeValues);
        }

        @Override
        public SortedSet<T> getDefaultValues() {
            return defaultValues;
        }

        @Override
        public SortedSet<T> getEffectiveValues() {
            SortedSet<T> values = getPendingValues();

            if (values.isEmpty()) {
                values = getDefaultValues();
            }

            return values;
        }

        @Override
        public SortedSet<T> getPendingValues() {
            return Collections.unmodifiableSortedSet(pendingValues);
        }

        @Override
        public PropertyDefinition<T> getPropertyDefinition() {
            return d;
        }

        @Override
        public boolean isEmpty() {
            return pendingValues.isEmpty();
        }

        @Override
        public boolean isModified() {
            return activeValues.size() != pendingValues.size()
                    || !activeValues.containsAll(pendingValues);
        }

        /**
         * Replace all pending values of this property with the provided values.
         *
         * @param c
         *            The new set of pending property values.
         */
        public void setPendingValues(Collection<T> c) {
            pendingValues.clear();
            pendingValues.addAll(c);
        }

        @Override
        public String toString() {
            return getEffectiveValues().toString();
        }

        @Override
        public boolean wasEmpty() {
            return activeValues.isEmpty();
        }
    }

    /** The properties. */
    private final Map<PropertyDefinition<?>, MyProperty<?>> properties = new HashMap<>();

    /** Creates a new empty property set. */
    public PropertySet() {
    }

    /**
     * Creates a property with the provided sets of pre-validated default and
     * active values.
     *
     * @param <T>
     *            The type of the property.
     * @param pd
     *            The property definition.
     * @param defaultValues
     *            The set of default values for the property.
     * @param activeValues
     *            The set of active values for the property.
     */
    public <T> void addProperty(PropertyDefinition<T> pd, Collection<T> defaultValues, Collection<T> activeValues) {
        MyProperty<T> p = new MyProperty<>(pd, defaultValues, activeValues);
        properties.put(pd, p);
    }

    /**
     * Get the property associated with the specified property definition.
     *
     * @param <T>
     *            The underlying type of the property.
     * @param d
     *            The Property definition.
     * @return Returns the property associated with the specified property
     *         definition.
     * @throws IllegalArgumentException
     *             If this property provider does not recognise the requested
     *             property definition.
     */
    @SuppressWarnings("unchecked")
    public <T> Property<T> getProperty(PropertyDefinition<T> d) {
        if (!properties.containsKey(d)) {
            throw new IllegalArgumentException("Unknown property " + d.getName());
        }

        return (Property<T>) properties.get(d);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        for (Map.Entry<PropertyDefinition<?>, MyProperty<?>> entry : properties.entrySet()) {
            builder.append(entry.getKey().getName());
            builder.append('=');
            builder.append(entry.getValue());
            builder.append(' ');
        }
        builder.append('}');
        return builder.toString();
    }

    /** Makes all pending values active. */
    void commit() {
        for (MyProperty<?> p : properties.values()) {
            p.commit();
        }
    }

    /**
     * Set a new pending values for the specified property.
     * <p>
     * See the class description for more information regarding pending values.
     *
     * @param <T>
     *            The type of the property to be modified.
     * @param d
     *            The property to be modified.
     * @param values
     *            A non-<code>null</code> set of new pending values for the
     *            property (an empty set indicates that the property should be
     *            reset to its default behavior). The set will not be referenced
     *            by this managed object.
     * @throws PropertyException
     *             If a new pending value is deemed to be invalid according to
     *             the property definition.
     * @throws PropertyException
     *             If an attempt was made to add multiple pending values to a
     *             single-valued property.
     * @throws PropertyException
     *             If an attempt was made to remove a mandatory property.
     * @throws IllegalArgumentException
     *             If the specified property definition is not associated with
     *             this managed object.
     */
    <T> void setPropertyValues(PropertyDefinition<T> d, Collection<T> values) {
        MyProperty<T> property = (MyProperty<T>) getProperty(d);

        if (values.size() > 1 && !d.hasOption(PropertyOption.MULTI_VALUED)) {
            throw PropertyException.propertyIsSingleValuedException(d);
        }

        if (values.isEmpty() && d.hasOption(PropertyOption.MANDATORY) && property.getDefaultValues().isEmpty()) {
            throw PropertyException.propertyIsMandatoryException(d);
        }

        // Validate each value.
        for (T e : values) {
            if (e == null) {
                throw new NullPointerException();
            }

            d.validateValue(e);
        }

        // Update the property.
        property.setPendingValues(values);
    }
}
