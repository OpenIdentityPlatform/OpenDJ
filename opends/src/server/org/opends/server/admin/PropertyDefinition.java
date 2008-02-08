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

package org.opends.server.admin;



import static org.opends.server.util.Validator.*;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;

import org.opends.messages.Message;



/**
 * An interface for querying generic property definition features.
 * <p>
 * Property definitions are analogous to ConfigAttributes in the
 * current model and will play a similar role. Eventually these will
 * replace them.
 * <p>
 * Implementations <b>must</b> take care to implement the various
 * comparison methods.
 *
 * @param <T>
 *          The data-type of values of the property.
 */
public abstract class PropertyDefinition<T> implements Comparator<T>,
    Comparable<PropertyDefinition<?>> {

  /**
   * An interface for incrementally constructing property definitions.
   *
   * @param <T>
   *          The data-type of values of the property.
   * @param <D>
   *          The type of property definition constructed by this
   *          builder.
   */
  protected abstract static class AbstractBuilder
      <T, D extends PropertyDefinition<T>> {

    // The administrator action.
    private AdministratorAction adminAction;

    // The default behavior provider.
    private DefaultBehaviorProvider<T> defaultBehavior;

    // The abstract managed object
    private final AbstractManagedObjectDefinition<?, ?> definition;

    // The options applicable to this definition.
    private final EnumSet<PropertyOption> options;

    // The name of this property definition.
    private final String propertyName;



    /**
     * Create a property definition builder.
     *
     * @param d
     *          The managed object definition associated with this
     *          property definition.
     * @param propertyName
     *          The property name.
     */
    protected AbstractBuilder(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName) {
      this.definition = d;
      this.propertyName = propertyName;
      this.options = EnumSet.noneOf(PropertyOption.class);
      this.adminAction = new AdministratorAction(AdministratorAction.Type.NONE,
          d, propertyName);
      this.defaultBehavior = new UndefinedDefaultBehaviorProvider<T>();
    }



    /**
     * Construct a property definition based on the properties of this
     * builder.
     *
     * @return The new property definition.
     */
    public final D getInstance() {
      return buildInstance(definition, propertyName, options, adminAction,
          defaultBehavior);
    }



    /**
     * Set the administrator action.
     *
     * @param adminAction
     *          The administrator action.
     */
    public final void setAdministratorAction(AdministratorAction adminAction) {
      ensureNotNull(adminAction);
      this.adminAction = adminAction;
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
     * @param adminAction
     *          The administrator action.
     * @param defaultBehavior
     *          The default behavior provider.
     * @return The new property definition.
     */
    protected abstract D buildInstance(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName, EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<T> defaultBehavior);
  }

  // The administrator action.
  private final AdministratorAction adminAction;

  // The default behavior provider.
  private final DefaultBehaviorProvider<T> defaultBehavior;

  // The abstract managed object
  private final AbstractManagedObjectDefinition<?, ?> definition;

  // Options applicable to this definition.
  private final Set<PropertyOption> options;

  // The property name.
  private final String propertyName;

  // The property value class.
  private final Class<T> theClass;



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
   * @param adminAction
   *          The administrator action.
   * @param defaultBehavior
   *          The default behavior provider.
   */
  protected PropertyDefinition(AbstractManagedObjectDefinition<?, ?> d,
      Class<T> theClass, String propertyName, EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<T> defaultBehavior) {
    ensureNotNull(d, theClass, propertyName);
    ensureNotNull(options, adminAction, defaultBehavior);

    this.definition = d;
    this.theClass = theClass;
    this.propertyName = propertyName;
    this.options = EnumSet.copyOf(options);
    this.adminAction = adminAction;
    this.defaultBehavior = defaultBehavior;
  }



  /**
   * Apply a visitor to this property definition.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's
   *          methods.
   * @param v
   *          The property definition visitor.
   * @param p
   *          Optional additional visitor parameter.
   * @return Returns a result as specified by the visitor.
   */
  public abstract <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p);



  /**
   * Apply a visitor to a property value associated with this property
   * definition.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's
   *          methods.
   * @param v
   *          The property value visitor.
   * @param value
   *          The property value.
   * @param p
   *          Optional additional visitor parameter.
   * @return Returns a result as specified by the visitor.
   */
  public abstract <R, P> R accept(PropertyValueVisitor<R, P> v, T value, P p);



  /**
   * Cast the provided value to the type associated with this property
   * definition.
   * <p>
   * This method only casts the object to the required type; it does
   * not validate the value once it has been cast. Subsequent
   * validation should be performed using the method
   * {@link #validateValue(Object)}.
   * <p>
   * This method guarantees the following expression is always
   * <code>true</code>:
   *
   * <pre>
   *  PropertyDefinition d;
   *  x == d.cast(x);
   * </pre>
   *
   * @param object
   *          The property value to be cast (can be <code>null</code>).
   * @return Returns the property value cast to the correct type.
   * @throws ClassCastException
   *           If the provided property value did not have the correct
   *           type.
   */
  public final T castValue(Object object) throws ClassCastException {
    return theClass.cast(object);
  }



  /**
   * Compares two property values for order. Returns a negative
   * integer, zero, or a positive integer as the first argument is
   * less than, equal to, or greater than the second.
   * <p>
   * This default implementation normalizes both values using
   * {@link #normalizeValue(Object)} and then performs a
   * case-sensitive string comparison.
   *
   * @param o1
   *          the first object to be compared.
   * @param o2
   *          the second object to be compared.
   * @return a negative integer, zero, or a positive integer as the
   *         first argument is less than, equal to, or greater than
   *         the second.
   */
  public int compare(T o1, T o2) {
    ensureNotNull(o1, o2);

    String s1 = normalizeValue(o1);
    String s2 = normalizeValue(o2);

    return s1.compareTo(s2);
  }



  /**
   * Compares this property definition with the specified property
   * definition for order. Returns a negative integer, zero, or a
   * positive integer if this property definition is less than, equal
   * to, or greater than the specified property definition.
   * <p>
   * The ordering must be determined first from the property name and
   * then base on the underlying value type.
   *
   * @param o
   *          The reference property definition with which to compare.
   * @return Returns a negative integer, zero, or a positive integer
   *         if this property definition is less than, equal to, or
   *         greater than the specified property definition.
   */
  public final int compareTo(PropertyDefinition<?> o) {
    int rc = propertyName.compareTo(o.propertyName);
    if (rc == 0) {
      rc = theClass.getName().compareTo(o.theClass.getName());
    }
    return rc;
  }



  /**
   * Parse and validate a string representation of a property value.
   *
   * @param value
   *          The property string value (must not be <code>null</code>).
   * @return Returns the decoded property value.
   * @throws IllegalPropertyValueStringException
   *           If the property value string is invalid.
   */
  public abstract T decodeValue(String value)
      throws IllegalPropertyValueStringException;



  /**
   * Encode the provided property value into its string
   * representation.
   * <p>
   * This default implementation simply returns invokes the
   * {@link Object#toString()} method on the provided value.
   *
   * @param value
   *          The property value (must not be <code>null</code>).
   * @return Returns the encoded property string value.
   * @throws IllegalPropertyValueException
   *           If the property value is invalid.
   */
  public String encodeValue(T value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    return value.toString();
  }



  /**
   * Indicates whether some other object is &quot;equal to&quot; this
   * property definition. This method must obey the general contract
   * of <tt>Object.equals(Object)</tt>. Additionally, this method
   * can return <tt>true</tt> <i>only</i> if the specified Object
   * is also a property definition and it has the same name, as
   * returned by {@link #getName()}, and also is deemed to be
   * &quot;compatible&quot; with this property definition.
   * Compatibility means that the two property definitions share the
   * same underlying value type and provide similar comparator
   * implementations.
   *
   * @param o
   *          The reference object with which to compare.
   * @return Returns <code>true</code> only if the specified object
   *         is also a property definition and it has the same name
   *         and is compatible with this property definition.
   * @see java.lang.Object#equals(java.lang.Object)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof PropertyDefinition) {
      PropertyDefinition<?> other = (PropertyDefinition<?>) o;
      if (propertyName.equals(other.propertyName)) {
        if (theClass.equals(other.theClass)) {
          return true;
        }
      }
      return false;
    } else {
      return false;
    }
  }



  /**
   * Get the administrator action associated with this property
   * definition. The administrator action describes any action which
   * the administrator must perform in order for changes to this
   * property to take effect.
   *
   * @return Returns the administrator action associated with this
   *         property definition.
   */
  public final AdministratorAction getAdministratorAction() {
    return adminAction;
  }



  /**
   * Get the default behavior provider associated with this property
   * definition.
   *
   * @return Returns the default behavior provider associated with
   *         this property definition.
   */
  public final DefaultBehaviorProvider<T> getDefaultBehaviorProvider() {
    return defaultBehavior;
  }



  /**
   * Gets the optional description of this property definition in the
   * default locale.
   *
   * @return Returns the description of this property definition in
   *         the default locale, or <code>null</code> if there is no
   *         description.
   */
  public final Message getDescription() {
    return getDescription(Locale.getDefault());
  }



  /**
   * Gets the optional description of this property definition in the
   * specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the description of this property definition in
   *         the specified locale, or <code>null</code> if there is
   *         no description.
   */
  public final Message getDescription(Locale locale) {
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
   * Get the name of the property.
   *
   * @return Returns the name of the property.
   */
  public final String getName() {
    return propertyName;
  }



  /**
   * Gets the synopsis of this property definition in the default
   * locale.
   *
   * @return Returns the synopsis of this property definition in the
   *         default locale.
   */
  public final Message getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * Gets the synopsis of this property definition in the specified
   * locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this property definition in the
   *         specified locale.
   */
  public final Message getSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + propertyName + ".synopsis";
    return resource.getMessage(definition, property, locale);
  }



  /**
   * Returns a hash code value for this property definition. The hash
   * code should be derived from the property name and the type of
   * values handled by this property definition.
   *
   * @return Returns the hash code value for this property definition.
   */
  @Override
  public final int hashCode() {
    int rc = 17 + propertyName.hashCode();
    return 37 * rc + theClass.hashCode();
  }



  /**
   * Check if the specified option is set for this property
   * definition.
   *
   * @param option
   *          The option to test.
   * @return Returns <code>true</code> if the option is set, or
   *         <code>false</code> otherwise.
   */
  public final boolean hasOption(PropertyOption option) {
    return options.contains(option);
  }



  /**
   * Get a normalized string representation of a property value. This
   * can then be used for comparisons and for generating hash-codes.
   * <p>
   * This method may throw an exception if the provided value is
   * invalid. However, applications should not assume that
   * implementations of this method will always validate a value. This
   * task is the responsibility of {@link #validateValue(Object)}.
   * <p>
   * This default implementation simply returns the string
   * representation of the provided value. Sub-classes might want to
   * override this method if this behavior is insufficient (for
   * example, a string property definition might strip white-space and
   * convert characters to lower-case).
   *
   * @param value
   *          The property value to be normalized.
   * @return Returns the normalized property value.
   * @throws IllegalPropertyValueException
   *           If the property value is invalid.
   */
  public String normalizeValue(T value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    return encodeValue(value);
  }



  /**
   * Returns a string representation of this property definition.
   *
   * @return Returns a string representation of this property
   *         definition.
   * @see Object#toString()
   */
  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder);
    return builder.toString();
  }



  /**
   * Append a string representation of the property definition to the
   * provided string builder.
   * <p>
   * This simple implementation just outputs the propertyName of the
   * property definition. Sub-classes should override this method to
   * provide more complete string representations.
   *
   * @param builder
   *          The string builder where the string representation
   *          should be appended.
   */
  public void toString(StringBuilder builder) {
    builder.append(propertyName);
  }



  /**
   * Determine if the provided property value is valid according to
   * this property definition.
   *
   * @param value
   *          The property value (must not be <code>null</code>).
   * @throws IllegalPropertyValueException
   *           If the property value is invalid.
   */
  public abstract void validateValue(T value)
      throws IllegalPropertyValueException;



  /**
   * Performs any run-time initialization required by this property
   * definition. This may include resolving managed object paths and
   * property names.
   *
   * @throws Exception
   *           If this property definition could not be initialized.
   */
  protected void initialize() throws Exception {
    // No implementation required.
  }
}
