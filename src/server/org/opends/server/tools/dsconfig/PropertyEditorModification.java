/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.tools.dsconfig;

import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.PropertyDefinition;

/**
 * This class is a data structure that can be used as an interface between
 * PropertyValueEditor and the different handlers that call it.
 * Since PropertyValueEditor is not aware of the different options used by the
 * handlers it cannot directly construct a CommandBuilder, but
 * PropertyValueEditor knows about the different changes (set, reset, delete)
 * that can be performed against the ManagedObject and these changes can be
 * used to generate a CommandBuilder.
 *
 * @param <T> The type of the underlying property associated with the
 * modification.
 */
final class PropertyEditorModification<T>
{
  /**
   * The enumeration that describes the different types of modifications that
   * we can have.
   */
  enum Type
  {
    /** The user chose to set values. */
    SET,
    /** The user chose to reset values. */
    RESET,
    /** The user chose to add values. */
    ADD,
    /** The user chose to delete values. */
    REMOVE
  };

  private PropertyDefinition<T> propertyDefinition;
  private Type type;
  private SortedSet<T> values;
  private SortedSet<T> originalValues;

  /**
   * The private constructor of the PropertyEditorModification.
   * @param propertyDefinition the property definition associated with the
   * modification.
   * @param type the type of the modification.
   * @param values the values associated with the modifications.
   * @param originalValues the original values of the property we are modifying.
   */
  private PropertyEditorModification(PropertyDefinition<T> propertyDefinition,
      Type type, SortedSet<T> values, SortedSet<T> originalValues)
  {
    this.propertyDefinition = propertyDefinition;
    this.type = type;
    this.values = new TreeSet<T>(values);
    this.originalValues = new TreeSet<T>(originalValues);
  }

  /**
   * Creates a reset modification.
   * @param <T> The type of the underlying property.
   * @param propertyDefinition the property that is modified.
   * @param originalValues the original values of the property.
   * @return a reset modification for a given property.
   */
  static <T> PropertyEditorModification<T> createResetModification(
      PropertyDefinition<T> propertyDefinition, SortedSet<T> originalValues)
  {
    return new PropertyEditorModification<T>(propertyDefinition, Type.RESET,
        new TreeSet<T>(propertyDefinition), originalValues);
  }

  /**
   * Creates an add modification.
   * @param <T> The type of the underlying property.
   * @param propertyDefinition the property that is modified.
   * @param addedValues the values that are added in this modification.
   * @param originalValues the original values of the property.
   * @return a reset modification for a given property.
   */
  static <T> PropertyEditorModification<T> createAddModification(
      PropertyDefinition<T> propertyDefinition,
      SortedSet<T> addedValues, SortedSet<T> originalValues)
  {
    return new PropertyEditorModification<T>(propertyDefinition, Type.ADD,
        addedValues, originalValues);
  }

  /**
   * Creates a set modification.
   * @param <T> The type of the underlying property.
   * @param propertyDefinition the property that is modified.
   * @param newValues the new values for the property.
   * @param originalValues the original values of the property.
   * @return a reset modification for a given property.
   */
  static <T> PropertyEditorModification<T> createSetModification(
      PropertyDefinition<T> propertyDefinition,
      SortedSet<T> newValues, SortedSet<T> originalValues)
  {
    return new PropertyEditorModification<T>(propertyDefinition, Type.SET,
        newValues, originalValues);
  }

  /**
   * Creates a remove modification.
   * @param <T> The type of the underlying property.
   * @param propertyDefinition the property that is modified.
   * @param removedValues the values that are removed in this modification.
   * @param originalValues the original values of the property.
   * @return a reset modification for a given property.
   */
  static <T> PropertyEditorModification<T> createRemoveModification(
      PropertyDefinition<T> propertyDefinition, SortedSet<T> removedValues,
      SortedSet<T> originalValues)
  {
    return new PropertyEditorModification<T>(propertyDefinition, Type.REMOVE,
        removedValues, originalValues);
  }

  /**
   * Retuns the property definition associated with this modification.
   * @return the property definition associated with this modification.
   */
  PropertyDefinition<T> getPropertyDefinition()
  {
    return propertyDefinition;
  }

  /**
   * Returns the type of the modification.
   * @return the type of the modification.
   */
  Type getType()
  {
    return type;
  }

  /**
   * Returns the specific values associated with the modification.
   * @return the specific values associated with the modification.
   */
  SortedSet<T> getModificationValues()
  {
    return values;
  }

  /**
   * Returns the original values associated with the property.
   * @return the original values associated with the property.
   */
  SortedSet<T> getOriginalValues()
  {
    return originalValues;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "Property name: "+getPropertyDefinition()+
        "\nMod type: "+getType()+
        "\nMod values: "+getModificationValues()+
        "\nOriginal values: "+getOriginalValues();
  }
}
