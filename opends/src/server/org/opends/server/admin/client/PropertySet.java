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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client;



import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;



/**
 * A set of properties. Instances of this class can be used as the
 * core of a managed object implementation.
 */
public final class PropertySet implements PropertyProvider {

  /**
   * Internal property implementation.
   *
   * @param <T>
   *          The type of the property.
   */
  private static final class MyProperty<T> implements Property<T> {

    // The active set of values.
    private final SortedSet<T> activeValues;

    // The definition associated with this property.
    private final PropertyDefinition<T> d;

    // The default set of values (read-only).
    private final SortedSet<T> defaultValues;

    // The pending set of values.
    private final SortedSet<T> pendingValues;



    /**
     * Create a property with the provided sets of pre-validated
     * default and active values.
     *
     * @param pd
     *          The property definition.
     * @param defaultValues
     *          The set of default values for the property.
     * @param activeValues
     *          The set of active values for the property.
     */
    public MyProperty(PropertyDefinition<T> pd, Collection<T> defaultValues,
        Collection<T> activeValues) {
      this.d = pd;

      SortedSet<T> sortedDefaultValues = new TreeSet<T>(pd);
      sortedDefaultValues.addAll(defaultValues);
      this.defaultValues = Collections
          .unmodifiableSortedSet(sortedDefaultValues);

      this.activeValues = new TreeSet<T>(pd);
      this.activeValues.addAll(activeValues);

      // Initially the pending values is the same as the active
      // values.
      this.pendingValues = new TreeSet<T>(this.activeValues);
    }



    /**
     * Makes the pending values active.
     */
    public void commit() {
      activeValues.clear();
      activeValues.addAll(pendingValues);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> getActiveValues() {
      return Collections.unmodifiableSortedSet(activeValues);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> getDefaultValues() {
      return defaultValues;
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> getEffectiveValues() {
      SortedSet<T> values = getPendingValues();

      if (values.isEmpty()) {
        values = getDefaultValues();
      }

      return values;
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> getPendingValues() {
      return Collections.unmodifiableSortedSet(pendingValues);
    }



    /**
     * {@inheritDoc}
     */
    public PropertyDefinition<T> getPropertyDefinition() {
      return d;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
      return pendingValues.isEmpty();
    }



    /**
     * {@inheritDoc}
     */
    public boolean isModified() {
      if (activeValues.size() == pendingValues.size()
          && activeValues.containsAll(pendingValues)) {
        return false;
      }
      return true;
    }



    /**
     * Replace all pending values of this property with the provided
     * values.
     *
     * @param c
     *          The new set of pending property values.
     */
    public void setPendingValues(Collection<T> c) {
      pendingValues.clear();
      pendingValues.addAll(c);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return getEffectiveValues().toString();
    }



    /**
     * {@inheritDoc}
     */
    public boolean wasEmpty() {
      return activeValues.isEmpty();
    }
  }

  // The properties.
  private final Map<PropertyDefinition, MyProperty> properties;



  /**
   * Creates a new empty property set.
   */
  public PropertySet() {
    this.properties = new HashMap<PropertyDefinition, MyProperty>();
  }



  /**
   * Creates a property with the provided sets of pre-validated
   * default and active values.
   *
   * @param <T>
   *          The type of the property.
   * @param pd
   *          The property definition.
   * @param defaultValues
   *          The set of default values for the property.
   * @param activeValues
   *          The set of active values for the property.
   */
  public <T> void addProperty(PropertyDefinition<T> pd,
      Collection<T> defaultValues, Collection<T> activeValues) {
    MyProperty<T> p = new MyProperty<T>(pd, defaultValues, activeValues);
    properties.put(pd, p);
  }



  /**
   * Makes all pending values active.
   */
  public void commit() {
    for (MyProperty<?> p : properties.values()) {
      p.commit();
    }
  }



  /**
   * Get the property associated with the specified property
   * definition.
   *
   * @param <T>
   *          The underlying type of the property.
   * @param d
   *          The Property definition.
   * @return Returns the property associated with the specified
   *         property definition.
   * @throws IllegalArgumentException
   *           If this property provider does not recognise the
   *           requested property definition.
   */
  @SuppressWarnings("unchecked")
  public <T> Property<T> getProperty(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    if (!properties.containsKey(d)) {
      throw new IllegalArgumentException("Unknown property " + d.getName());
    }

    return properties.get(d);
  }



  /**
   * Get the effective value of the specified property.
   * <p>
   * See the class description for more information about how the
   * effective property value is derived.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective value, or
   *         <code>null</code> if there is no effective value
   *         defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  public <T> T getPropertyValue(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    Set<T> values = getPropertyValues(d);
    if (values.isEmpty()) {
      return null;
    } else {
      return values.iterator().next();
    }
  }



  /**
   * Get the effective values of the specified property.
   * <p>
   * See the class description for more information about how the
   * effective property values are derived.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective values, or an empty set
   *         if there are no effective values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    Property<T> property = getProperty(d);
    return new TreeSet<T>(property.getEffectiveValues());
  }



  /**
   * Set a new pending value for the specified property.
   * <p>
   * See the class description for more information regarding pending
   * values.
   *
   * @param <T>
   *          The type of the property to be modified.
   * @param d
   *          The property to be modified.
   * @param value
   *          The new pending value for the property, or
   *          <code>null</code> if the property should be reset to
   *          its default behavior.
   * @throws IllegalPropertyValueException
   *           If the new pending value is deemed to be invalid
   *           according to the property definition.
   * @throws PropertyIsMandatoryException
   *           If an attempt was made to remove a mandatory property.
   * @throws IllegalArgumentException
   *           If the specified property definition is not associated
   *           with this managed object.
   */
  public <T> void setPropertyValue(PropertyDefinition<T> d, T value)
      throws IllegalPropertyValueException, PropertyIsMandatoryException,
      IllegalArgumentException {
    if (value == null) {
      setPropertyValues(d, Collections.<T> emptySet());
    } else {
      setPropertyValues(d, Collections.singleton(value));
    }
  }



  /**
   * Set a new pending values for the specified property.
   * <p>
   * See the class description for more information regarding pending
   * values.
   *
   * @param <T>
   *          The type of the property to be modified.
   * @param d
   *          The property to be modified.
   * @param values
   *          A non-<code>null</code> set of new pending values for
   *          the property (an empty set indicates that the property
   *          should be reset to its default behavior). The set will
   *          not be referenced by this managed object.
   * @throws IllegalPropertyValueException
   *           If a new pending value is deemed to be invalid
   *           according to the property definition.
   * @throws PropertyIsSingleValuedException
   *           If an attempt was made to add multiple pending values
   *           to a single-valued property.
   * @throws PropertyIsMandatoryException
   *           If an attempt was made to remove a mandatory property.
   * @throws IllegalArgumentException
   *           If the specified property definition is not associated
   *           with this managed object.
   */
  public <T> void setPropertyValues(PropertyDefinition<T> d,
      Collection<T> values) throws IllegalPropertyValueException,
      PropertyIsSingleValuedException, PropertyIsMandatoryException,
      IllegalArgumentException {
    MyProperty<T> property = (MyProperty<T>) getProperty(d);

    if (values.size() > 1 && !d.hasOption(PropertyOption.MULTI_VALUED)) {
      throw new PropertyIsSingleValuedException(d);
    }

    if (values.isEmpty() && d.hasOption(PropertyOption.MANDATORY)) {
      // But only if there are no default values.
      if (property.getDefaultValues().isEmpty()) {
        throw new PropertyIsMandatoryException(d);
      }
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



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append('{');
    for (Map.Entry<PropertyDefinition, MyProperty> entry : properties
        .entrySet()) {
      builder.append(entry.getKey().getName());
      builder.append('=');
      builder.append(entry.getValue().toString());
      builder.append(' ');
    }
    builder.append('}');
    return builder.toString();
  }
}
