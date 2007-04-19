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

package org.opends.server.admin;



import static org.opends.server.util.Validator.ensureNotNull;

import java.util.EnumSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;



/**
 * Skeleton property definition implementation.
 *
 * @param <T>
 *          The data-type of values of the property.
 */
public abstract class AbstractPropertyDefinition<T> implements
    PropertyDefinition<T> {

  /**
   * An interface for incrementally constructing property definitions.
   *
   * @param <T>
   *          The data-type of values of the property.
   * @param <D>
   *          The type of property definition constructed by this builder.
   */
  protected abstract static class AbstractBuilder<T,
      D extends PropertyDefinition<T>> {

    //  The abstract managed object
    private final AbstractManagedObjectDefinition<?, ?> definition;

    // The name of this property definition.
    private final String propertyName;

    // The options applicable to this definition.
    private final EnumSet<PropertyOption> options;

    // The default behavior provider.
    private DefaultBehaviorProvider<T> defaultBehavior;



    /**
     * Create a property definition builder.
     *
     * @param d
     *          The managed object definition associated with this
     *          property definition.
     * @param propertyName
     *          The property name.
     */
    protected AbstractBuilder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      this.definition = d;
      this.propertyName = propertyName;
      this.options = EnumSet.noneOf(PropertyOption.class);
      this.defaultBehavior = new UndefinedDefaultBehaviorProvider<T>();
    }



    /**
     * Construct a property definition based on the properties of this builder.
     *
     * @return The new property definition.
     */
    public final D getInstance() {
      return buildInstance(definition, propertyName, options, defaultBehavior);
    }



    /**
     * Set the default behavior provider.
     *
     * @param defaultBehavior
     *          The default behavior provider.
     */
    public final void setDefaultBehaviorProvider(
        DefaultBehaviorProvider<T> defaultBehavior) {
      ensureNotNull(defaultBehavior);
      this.defaultBehavior = defaultBehavior;
    }



    /**
     * Add a property definition option.
     *
     * @param option
     *          The property option.
     */
    public final void setOption(PropertyOption option) {
      ensureNotNull(option);
      options.add(option);
    }



    /**
     * Build a property definition based on the properties of this
     * builder.
     *
     * @param d
     *          The managed object definition associated with this
     *          property definition.
     * @param propertyName
     *          The property name.
     * @param options
     *          Options applicable to this definition.
     * @param defaultBehavior
     *          The default behavior provider.
     * @return The new property definition.
     */
    protected abstract D buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<T> defaultBehavior);
  }

  // The property name.
  private final String propertyName;

  // The property value class.
  private final Class<T> theClass;

  // Options applicable to this definition.
  private final Set<PropertyOption> options;

  // The default behavior provider.
  private final DefaultBehaviorProvider<T> defaultBehavior;

  // The abstract managed object
  private final AbstractManagedObjectDefinition<?, ?> definition;



  /**
   * Create a property definition.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param theClass
   *          The property value class.
   * @param propertyName
   *          The property name.
   * @param options
   *          Options applicable to this definition.
   * @param defaultBehavior
   *          The default behavior provider.
   */
  protected AbstractPropertyDefinition(AbstractManagedObjectDefinition<?,?> d,
      Class<T> theClass, String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<T> defaultBehavior) {
    ensureNotNull(d, theClass, propertyName);
    ensureNotNull(options, defaultBehavior);

    this.definition = d;
    this.theClass = theClass;
    this.propertyName = propertyName;
    this.options = EnumSet.copyOf(options);
    this.defaultBehavior = defaultBehavior;
  }



  /**
   * {@inheritDoc}
   */
  public abstract <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p);



  /**
   * {@inheritDoc}
   */
  public final T castValue(Object object) throws ClassCastException {
    return theClass.cast(object);
  }



  /**
   * {@inheritDoc}
   * <p>
   * This default implementation normalizes both values using
   * {@link #normalizeValue(Object)} and then performs a case-sensitive string
   * comparison.
   */
  public int compare(T o1, T o2) {
    ensureNotNull(o1, o2);

    String s1 = normalizeValue(o1);
    String s2 = normalizeValue(o2);

    return s1.compareTo(s2);
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(PropertyDefinition<?> o) {
    int rc = getName().compareTo(o.getName());
    if (rc == 0) {
      // TODO: see comment in equals().
      rc = getClass().getName().compareTo(o.getClass().getName());
    }
    return rc;
  }



  /**
   * {@inheritDoc}
   */
  public abstract T decodeValue(String value)
      throws IllegalPropertyValueStringException;



  /**
   * {@inheritDoc}
   * <p>
   * This default implementation simply returns invokes the
   * {@link Object#toString()} method on the provided value.
   */
  public String encodeValue(T value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    return value.toString();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof PropertyDefinition) {
      PropertyDefinition other = (PropertyDefinition) obj;
      if (getName().equals(other.getName())) {
        // TODO: this isn't quite right - should be comparing the value types
        // not the definition type. It's ok for now though.
        if (getClass().equals(other.getClass())) {
          return true;
        }
      }
      return false;
    } else {
      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  public DefaultBehaviorProvider<T> getDefaultBehaviorProvider() {
    return defaultBehavior;
  }



  /**
   * {@inheritDoc}
   */
  public final String getDescription() {
    return getDescription(Locale.getDefault());
  }



  /**
   * {@inheritDoc}
   */
  public final String getDescription(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + propertyName + ".description";
    try {
      return resource.getMessage(definition, property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * Gets the managed object definition associated with this property
   * definition.
   *
   * @return Returns the managed object definition associated with
   *         this property definition.
   */
  public final AbstractManagedObjectDefinition<?, ?>
      getManagedObjectDefinition() {
    return definition;
  }



  /**
   * {@inheritDoc}
   */
  public final String getName() {
    return propertyName;
  }



  /**
   * {@inheritDoc}
   */
  public final String getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * {@inheritDoc}
   */
  public final String getSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + propertyName + ".synopsis";
    try {
      return resource.getMessage(definition, property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    // TODO: see comment in equals().
    int rc = 17 + getName().hashCode();
    return 37 * rc + getClass().hashCode();
  }



  /**
   * {@inheritDoc}
   */
  public final boolean hasOption(PropertyOption option) {
    return options.contains(option);
  }



  /**
   * {@inheritDoc}
   * <p>
   * This default implementation simply returns the string representation of the
   * provided value. Sub-classes might want to override this method if this
   * behavior is insufficient (for example, a string property definition might
   * strip white-space and convert characters to lower-case).
   */
  public String normalizeValue(T value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    return encodeValue(value);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder);
    return builder.toString();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This simple implementation just outputs the propertyName of the property
   * definition. Sub-classes should override this method to provide more
   * complete string representations.
   */
  public void toString(StringBuilder builder) {
    builder.append(propertyName);
  }



  /**
   * {@inheritDoc}
   */
  public abstract void validateValue(T value)
      throws IllegalPropertyValueException;

}
