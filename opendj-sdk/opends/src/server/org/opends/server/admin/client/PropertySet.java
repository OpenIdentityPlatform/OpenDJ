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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.DefaultBehaviorPropertyValueException;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InheritedDefaultValueException;
import org.opends.server.admin.InheritedDefaultValueProvider;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OperationsException;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.StringPropertyProvider;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;



/**
 * A set of properties. Instances of this class can be used as the core of a
 * managed object implementation.
 */
public final class PropertySet implements PropertyProvider {

  /**
   * Internal property implementation.
   *
   * @param <T>
   *          The type of the property.
   */
  private static final class MyProperty<T> implements Property<T> {

    // The definition associated with this property.
    private final PropertyDefinition<T> d;

    // The default set of values (read-only).
    private final SortedSet<T> defaultValues;

    // The active set of values (read-only).
    private final SortedSet<T> activeValues;

    // The pending set of values.
    private final SortedSet<T> pendingValues;



    /**
     * Create a property with the provided sets of pre-validated default and
     * active values.
     * <p>
     * This constructor takes ownership of the provided value sets.
     *
     * @param d
     *          The property definition.
     * @param defaultValues
     *          The set of default values for the property.
     * @param activeValues
     *          The set of active values for the property.
     */
    public MyProperty(PropertyDefinition<T> d, SortedSet<T> defaultValues,
        SortedSet<T> activeValues) {
      this.d = d;
      this.defaultValues = Collections.unmodifiableSortedSet(defaultValues);
      this.activeValues = Collections.unmodifiableSortedSet(activeValues);

      // Initially the pending values is the same as the active values.
      this.pendingValues = new TreeSet<T>(this.activeValues);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> getActiveValues() {
      return activeValues;
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
      if (activeValues == pendingValues) {
        return false;
      } else if (activeValues.size() != pendingValues.size()) {
        return true;
      } else if (activeValues.containsAll(pendingValues)) {
        return false;
      } else {
        return true;
      }
    }



    /**
     * Replace all pending values of this property with the provided values.
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
    public boolean wasEmpty() {
      return activeValues.isEmpty();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return getEffectiveValues().toString();
    }
  }



  /**
   * Internal default behavior visitor implementation.
   *
   * @param <T>
   *          The type of the default property values.
   */
  private static final class DefaultVisitor<T> implements
      DefaultBehaviorProviderVisitor<T, SortedSet<T>,
      Collection<PropertyException>> {

    // The property definition.
    private final PropertyDefinition<T> pd;

    // Used to retrieve inherited properties.
    private final InheritedDefaultValueProvider provider;



    // Private constructor.
    private DefaultVisitor(PropertyDefinition<T> pd,
        InheritedDefaultValueProvider provider) {
      this.pd = pd;
      this.provider = provider;
    }



    // Cast a set of objects to the required type.
    private Collection<T> castValues(Collection<?> values,
        Collection<PropertyException> exceptions) {
      List<T> castValues = new LinkedList<T>();
      for (Object value : values) {
        try {
          castValues.add(pd.castValue(value));
        } catch (ClassCastException e) {
          exceptions.add(new IllegalPropertyValueException(pd, value));
        }
      }
      return castValues;
    }



    // Build set of default values and validate them.
    private SortedSet<T> validateStrings(Collection<String> values,
        Collection<PropertyException> exceptions) {
      TreeSet<T> defaultValues = new TreeSet<T>(pd);
      for (String value : values) {
        try {
          defaultValues.add(pd.decodeValue(value));
        } catch (IllegalPropertyValueStringException e) {
          exceptions.add(new DefaultBehaviorPropertyValueException(pd, e));
        }
      }

      if (!pd.hasOption(PropertyOption.MULTI_VALUED)) {
        if (defaultValues.size() > 1) {
          PropertyException e = new PropertyIsSingleValuedException(pd);
          exceptions.add(new DefaultBehaviorPropertyValueException(pd, e));
        }
      }

      return defaultValues;
    }



    // Build set of default values and validate them.
    private SortedSet<T> validate(Collection<T> values,
        Collection<PropertyException> exceptions) {
      TreeSet<T> defaultValues = new TreeSet<T>(pd);
      for (T value : values) {
        try {
          pd.validateValue(value);
          defaultValues.add(value);
        } catch (IllegalPropertyValueException e) {
          exceptions.add(new DefaultBehaviorPropertyValueException(pd, e));
        }
      }

      if (!pd.hasOption(PropertyOption.MULTI_VALUED)) {
        if (defaultValues.size() > 1) {
          PropertyException e = new PropertyIsSingleValuedException(pd);
          exceptions.add(new DefaultBehaviorPropertyValueException(pd, e));
        }
      }

      return defaultValues;
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> visitAbsoluteInherited(
        AbsoluteInheritedDefaultBehaviorProvider<T> d,
        Collection<PropertyException> p) {
      // Get the values from the managed object at the specified path.
      try {
        // Get the property values/defaults.
        ManagedObjectPath path = d.getManagedObjectPath();
        Collection<?> values = provider.getDefaultPropertyValues(path, d
            .getPropertyName());
        return validate(castValues(values, p), p);
      } catch (OperationsException e) {
        p.add(new InheritedDefaultValueException(pd, e));
        return new TreeSet<T>(pd);
      }
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> visitAlias(AliasDefaultBehaviorProvider<T> d,
        Collection<PropertyException> p) {
      // No values applicable - just return the empty set.
      return new TreeSet<T>(pd);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> visitDefined(DefinedDefaultBehaviorProvider<T> d,
        Collection<PropertyException> p) {
      return validateStrings(d.getDefaultValues(), p);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> visitRelativeInherited(
        RelativeInheritedDefaultBehaviorProvider<T> d,
        Collection<PropertyException> p) {
      if (d.getRelativeOffset() == 0) {
        // TODO: we're inheriting default values from another property in this
        // property set. Logging is a good use-case: there is a general logging
        // level for all categories and then category specific levels which can
        // override. Should the default values be determined dynamically every
        // time they are accessed? If dynamically, how will decoding errors be
        // handled? Dynamically: we could return a SortedSet<T> which is lazily
        // computed.
        return new TreeSet<T>(pd);
      } else {
        // Inheriting default values from a parent managed object.
        try {
          ManagedObjectPath base = provider.getManagedObjectPath();
          ManagedObjectPath path = d.getManagedObjectPath(base);
          Collection<?> values = provider.getDefaultPropertyValues(path, d
              .getPropertyName());
          return validate(castValues(values, p), p);
        } catch (OperationsException e) {
          p.add(new InheritedDefaultValueException(pd, e));
          return new TreeSet<T>(pd);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
        Collection<PropertyException> p) {
      // No values applicable - just return the empty set.
      return new TreeSet<T>(pd);
    }
  }



  /**
   * Create a new property set using a property provider to supply the active
   * property values. This constructor takes care of validation of the property
   * values and retrieval of any default values.
   * <p>
   * Any exceptions that occurred whilst processing the properties will be
   * placed in the provided exception collection. Properties that caused the
   * exceptions will be created with an empty set of values (note that this
   * could mean that the resulting property set might contain empty mandatory
   * properties).
   *
   * @param d
   *          The managed object definition.
   * @param p
   *          The property provider.
   * @param i
   *          An inherited managed object provider for retrieving inherited
   *          properties.
   * @param exceptions
   *          A collection in which any property exceptions can be placed.
   * @return Returns the new property set.
   */
  public static PropertySet create(ManagedObjectDefinition<?, ?> d,
      PropertyProvider p, InheritedDefaultValueProvider i,
      Collection<PropertyException> exceptions) {
    Map<PropertyDefinition, MyProperty> properties =
      new HashMap<PropertyDefinition, MyProperty>();

    // Copy the properties from the provider.
    for (PropertyDefinition<?> pd : d.getPropertyDefinitions()) {
      createProperty(pd, p, i, properties, exceptions);
    }

    return new PropertySet(properties);
  }



  /**
   * Create a new property set using a string property provider to supply the
   * active property values. This constructor takes care of validation of the
   * property values and retrieval of any default values.
   * <p>
   * Any exceptions that occurred whilst processing the properties will be
   * placed in the provided exception collection. Properties that caused the
   * exceptions will be created with an empty set of values (note that this
   * could mean that the resulting property set might contain empty mandatory
   * properties).
   *
   * @param d
   *          The managed object definition.
   * @param p
   *          The string property provider.
   * @param i
   *          An inherited managed object provider for retrieving inherited
   *          properties.
   * @param exceptions
   *          A collection in which any property exceptions can be placed.
   * @return Returns the new property set.
   */
  public static PropertySet create(ManagedObjectDefinition<?, ?> d,
      StringPropertyProvider p, InheritedDefaultValueProvider i,
      Collection<PropertyException> exceptions) {
    Map<PropertyDefinition, MyProperty> properties =
      new HashMap<PropertyDefinition, MyProperty>();

    // Copy the properties from the provider.
    for (PropertyDefinition<?> pd : d.getPropertyDefinitions()) {
      createProperty(pd, p, i, properties, exceptions);
    }

    return new PropertySet(properties);
  }



  // Create new property using string values taken from a property provider.
  private static <T> void createProperty(PropertyDefinition<T> pd,
      StringPropertyProvider p, InheritedDefaultValueProvider i,
      Map<PropertyDefinition, MyProperty> properties,
      Collection<PropertyException> exceptions) {

    // Get the active values for this property.
    Collection<String> activeStringValues;

    try {
      activeStringValues = p.getPropertyValues(pd);
    } catch (IllegalArgumentException e) {
      // Default to empty set of values.
      activeStringValues = Collections.<String> emptySet();
    }

    SortedSet<T> activeValues = new TreeSet<T>(pd);
    boolean gotException = false;
    for (String stringValue : activeStringValues) {
      try {
        activeValues.add(pd.decodeValue(stringValue));
      } catch (IllegalPropertyValueStringException e) {
        exceptions.add(e);
        gotException = true;
      }
    }

    if (gotException == false) {
      if (pd.hasOption(PropertyOption.MANDATORY)) {
        if (activeValues.isEmpty()) {
          exceptions.add(new PropertyIsMandatoryException(pd));
        }
      }
    }

    createProperty(pd, activeValues, i, properties, exceptions);
  }



  // Create new property using values taken from a property provider.
  private static <T> void createProperty(PropertyDefinition<T> pd,
      PropertyProvider p, InheritedDefaultValueProvider i,
      Map<PropertyDefinition, MyProperty> properties,
      Collection<PropertyException> exceptions) {
    // Get the active values for this property.
    Collection<T> activeValues;

    try {
      activeValues = p.getPropertyValues(pd);
    } catch (IllegalArgumentException e) {
      // Default to empty set of values.
      activeValues = Collections.<T> emptySet();
    }

    SortedSet<T> validActiveValues = new TreeSet<T>(pd);
    boolean gotException = false;
    for (T value : activeValues) {
      try {
        pd.validateValue(value);
        validActiveValues.add(value);
      } catch (IllegalPropertyValueException e) {
        exceptions.add(e);
        gotException = true;
      }
    }

    if (gotException == false) {
      if (pd.hasOption(PropertyOption.MANDATORY)) {
        if (validActiveValues.isEmpty()) {
          exceptions.add(new PropertyIsMandatoryException(pd));
        }
      }
    }

    createProperty(pd, validActiveValues, i, properties, exceptions);
  }



  // Create new property using the provided validated values.
  private static <T> void createProperty(PropertyDefinition<T> pd,
      SortedSet<T> activeValues, InheritedDefaultValueProvider i,
      Map<PropertyDefinition, MyProperty> properties,
      Collection<PropertyException> exceptions) {
    // Do remaining validation of active values.
    if (!pd.hasOption(PropertyOption.MULTI_VALUED)) {
      if (activeValues.size() > 1) {
        exceptions.add(new PropertyIsSingleValuedException(pd));
      }
    }

    // Get the default values for this property.
    DefaultVisitor<T> visitor = new DefaultVisitor<T>(pd, i);
    SortedSet<T> defaultValues = pd.getDefaultBehaviorProvider().accept(
        visitor, exceptions);

    // Create the property.
    properties.put(pd, new MyProperty<T>(pd, defaultValues, activeValues));
  }

  // The properties.
  private final Map<PropertyDefinition, MyProperty> properties;



  // Private constructor.
  private PropertySet(Map<PropertyDefinition, MyProperty> properties) {
    this.properties = properties;
  }



  /**
   * Get the property associated with the specified property definition.
   *
   * @param <T>
   *          The underlying type of the property.
   * @param d
   *          The Property definition.
   * @return Returns the property associated with the specified property
   *         definition.
   * @throws IllegalArgumentException
   *           If this property provider does not recognise the requested
   *           property definition.
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
   * See the class description for more information about how the effective
   * property value is derived.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective value, or <code>null</code> if
   *         there is no effective value defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this managed
   *           object's definition.
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
   * See the class description for more information about how the effective
   * property values are derived.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective values, or an empty set if there
   *         are no effective values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this managed
   *           object's definition.
   */
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    Property<T> property = getProperty(d);
    return new TreeSet<T>(property.getEffectiveValues());
  }



  /**
   * Set a new pending value for the specified property.
   * <p>
   * See the class description for more information regarding pending values.
   *
   * @param <T>
   *          The type of the property to be modified.
   * @param d
   *          The property to be modified.
   * @param value
   *          The new pending value for the property, or <code>null</code> if
   *          the property should be reset to its default behavior.
   * @throws IllegalPropertyValueException
   *           If the new pending value is deemed to be invalid according to the
   *           property definition.
   * @throws PropertyIsReadOnlyException
   *           If an attempt was made to modify a read-only property.
   * @throws PropertyIsMandatoryException
   *           If an attempt was made to remove a mandatory property.
   * @throws IllegalArgumentException
   *           If the specified property definition is not associated with this
   *           managed object, or if the property is read-only.
   */
  public <T> void setPropertyValue(PropertyDefinition<T> d, T value)
      throws IllegalPropertyValueException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    if (value == null) {
      setPropertyValues(d, Collections.<T> emptySet());
    } else {
      setPropertyValues(d, Collections.singleton(value));
    }
  }



  /**
   * Set a new pending values for the specified property.
   * <p>
   * See the class description for more information regarding pending values.
   *
   * @param <T>
   *          The type of the property to be modified.
   * @param d
   *          The property to be modified.
   * @param values
   *          A non-<code>null</code> set of new pending values for the
   *          property (an empty set indicates that the property should be reset
   *          to its default behavior). The set will not be referenced by this
   *          managed object.
   * @throws IllegalPropertyValueException
   *           If a new pending value is deemed to be invalid according to the
   *           property definition.
   * @throws PropertyIsSingleValuedException
   *           If an attempt was made to add multiple pending values to a
   *           single-valued property.
   * @throws PropertyIsReadOnlyException
   *           If an attempt was made to modify a read-only property.
   * @throws PropertyIsMandatoryException
   *           If an attempt was made to remove a mandatory property.
   * @throws IllegalArgumentException
   *           If the specified property definition is not associated with this
   *           managed object, or if the property is read-only.
   */
  public <T> void setPropertyValues(PropertyDefinition<T> d,
      Collection<T> values) throws IllegalPropertyValueException,
      PropertyIsSingleValuedException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    MyProperty<T> property = (MyProperty<T>) getProperty(d);

    if (d.hasOption(PropertyOption.READ_ONLY)) {
      throw new PropertyIsReadOnlyException(d);
    }

    if (!d.hasOption(PropertyOption.MULTI_VALUED)) {
      if (values.size() > 1) {
        throw new PropertyIsSingleValuedException(d);
      }
    }

    if (d.hasOption(PropertyOption.MANDATORY)) {
      if (values.isEmpty()) {
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
