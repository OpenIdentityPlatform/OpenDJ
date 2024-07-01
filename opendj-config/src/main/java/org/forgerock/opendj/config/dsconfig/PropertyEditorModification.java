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
package org.forgerock.opendj.config.dsconfig;

import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.PropertyDefinition;

/**
 * This class is a data structure that can be used as an interface between PropertyValueEditor and the different
 * handlers that call it. Since PropertyValueEditor is not aware of the different options used by the handlers it cannot
 * directly construct a CommandBuilder, but PropertyValueEditor knows about the different changes (set, reset, delete)
 * that can be performed against the ManagedObject and these changes can be used to generate a CommandBuilder.
 *
 * @param <T>
 *            The type of the underlying property associated with the modification.
 */
final class PropertyEditorModification<T> {
    /** The enumeration that describes the different types of modifications that we can have. */
    enum Type {
        /** The user chose to set values. */
        SET,
        /** The user chose to reset values. */
        RESET,
        /** The user chose to add values. */
        ADD,
        /** The user chose to delete values. */
        REMOVE
    }

    private final PropertyDefinition<T> propertyDefinition;
    private final Type type;
    private final SortedSet<T> values;
    private final SortedSet<T> originalValues;

    /**
     * The private constructor of the PropertyEditorModification.
     *
     * @param propertyDefinition
     *            the property definition associated with the modification.
     * @param type
     *            the type of the modification.
     * @param values
     *            the values associated with the modifications.
     * @param originalValues
     *            the original values of the property we are modifying.
     */
    private PropertyEditorModification(PropertyDefinition<T> propertyDefinition, Type type, SortedSet<T> values,
            SortedSet<T> originalValues) {
        this.propertyDefinition = propertyDefinition;
        this.type = type;
        this.values = new TreeSet<>(values);
        this.originalValues = new TreeSet<>(originalValues);
    }

    /**
     * Creates a reset modification.
     *
     * @param <T>
     *            The type of the underlying property.
     * @param propertyDefinition
     *            the property that is modified.
     * @param originalValues
     *            the original values of the property.
     * @return a reset modification for a given property.
     */
    static <T> PropertyEditorModification<T> createResetModification(PropertyDefinition<T> propertyDefinition,
            SortedSet<T> originalValues) {
        return new PropertyEditorModification<>(propertyDefinition, Type.RESET, new TreeSet<T>(propertyDefinition),
                originalValues);
    }

    /**
     * Creates an add modification.
     *
     * @param <T>
     *            The type of the underlying property.
     * @param propertyDefinition
     *            the property that is modified.
     * @param addedValues
     *            the values that are added in this modification.
     * @param originalValues
     *            the original values of the property.
     * @return a reset modification for a given property.
     */
    static <T> PropertyEditorModification<T> createAddModification(PropertyDefinition<T> propertyDefinition,
            SortedSet<T> addedValues, SortedSet<T> originalValues) {
        return new PropertyEditorModification<>(propertyDefinition, Type.ADD, addedValues, originalValues);
    }

    /**
     * Creates a set modification.
     *
     * @param <T>
     *            The type of the underlying property.
     * @param propertyDefinition
     *            the property that is modified.
     * @param newValues
     *            the new values for the property.
     * @param originalValues
     *            the original values of the property.
     * @return a reset modification for a given property.
     */
    static <T> PropertyEditorModification<T> createSetModification(PropertyDefinition<T> propertyDefinition,
            SortedSet<T> newValues, SortedSet<T> originalValues) {
        return new PropertyEditorModification<>(propertyDefinition, Type.SET, newValues, originalValues);
    }

    /**
     * Creates a remove modification.
     *
     * @param <T>
     *            The type of the underlying property.
     * @param propertyDefinition
     *            the property that is modified.
     * @param removedValues
     *            the values that are removed in this modification.
     * @param originalValues
     *            the original values of the property.
     * @return a reset modification for a given property.
     */
    static <T> PropertyEditorModification<T> createRemoveModification(PropertyDefinition<T> propertyDefinition,
            SortedSet<T> removedValues, SortedSet<T> originalValues) {
        return new PropertyEditorModification<>(propertyDefinition, Type.REMOVE, removedValues, originalValues);
    }

    /**
     * Returns the property definition associated with this modification.
     *
     * @return the property definition associated with this modification.
     */
    PropertyDefinition<T> getPropertyDefinition() {
        return propertyDefinition;
    }

    /**
     * Returns the type of the modification.
     *
     * @return the type of the modification.
     */
    Type getType() {
        return type;
    }

    /**
     * Returns the specific values associated with the modification.
     *
     * @return the specific values associated with the modification.
     */
    SortedSet<T> getModificationValues() {
        return values;
    }

    /**
     * Returns the original values associated with the property.
     *
     * @return the original values associated with the property.
     */
    SortedSet<T> getOriginalValues() {
        return originalValues;
    }

    @Override
    public String toString() {
        return "Property name: " + getPropertyDefinition() + "\nMod type: " + getType() + "\nMod values: "
                + getModificationValues() + "\nOriginal values: " + getOriginalValues();
    }
}
