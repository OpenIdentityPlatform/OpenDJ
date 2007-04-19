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



import java.io.Serializable;
import java.util.Comparator;
import java.util.Locale;



/**
 * An interface for querying generic property definition features.
 * <p>
 * Property definitions are analogous to ConfigAttributes in the current model
 * and will play a similar role. Eventually these will replace them.
 * <p>
 * Implementations <b>must</b> take care to implement the various comparison
 * methods.
 * <p>
 * Implementations of this interface must be serializable. This is required so
 * that management applications can query property meta-information remotely.
 * <p>
 * TODO: define other call-backs (e.g. initial values).
 *
 * @param <T>
 *          The data-type of values of the property.
 */
public interface PropertyDefinition<T> extends Comparator<T>,
    Comparable<PropertyDefinition<?>>, Serializable {

  /**
   * Apply a visitor to this property definition.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's methods.
   * @param v
   *          The property definition visitor.
   * @param p
   *          Optional additional visitor parameter.
   * @return Returns a result as specified by the visitor.
   */
  <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p);



  /**
   * Cast the provided value to the type associated with this property
   * definition.
   * <p>
   * This method only casts the object to the required type; it does not
   * validate the value once it has been cast. Subsequent validation should be
   * performed using the method {@link #validateValue(Object)}.
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
   *           If the provided property value did not have the correct type.
   */
  T castValue(Object object) throws ClassCastException;



  /**
   * Compares this property definition with the specified property definition
   * for order. Returns a negative integer, zero, or a positive integer if this
   * property definition is less than, equal to, or greater than the specified
   * property definition.
   * <p>
   * The ordering must be determined first from the property name and then base
   * on the underlying value type.
   *
   * @param o
   *          The reference property definition with which to compare.
   * @return Returns a negative integer, zero, or a positive integer if this
   *         property definition is less than, equal to, or greater than the
   *         specified property definition.
   */
  int compareTo(PropertyDefinition<?> o);



  /**
   * Parse and validate a string representation of a property value.
   *
   * @param value
   *          The property string value (must not be <code>null</code>).
   * @return Returns the decoded property value.
   * @throws IllegalPropertyValueStringException
   *           If the property value string is invalid.
   */
  T decodeValue(String value) throws IllegalPropertyValueStringException;



  /**
   * Encode the provided property value into its string representation.
   * <p>
   * TODO: change name to avoid confusion with toString()?
   *
   * @param value
   *          The property value (must not be <code>null</code>).
   * @return Returns the encoded property string value.
   * @throws IllegalPropertyValueException
   *           If the property value is invalid.
   */
  String encodeValue(T value) throws IllegalPropertyValueException;



  /**
   * Indicates whether some other object is &quot;equal to&quot; this property
   * definition. This method must obey the general contract of
   * <tt>Object.equals(Object)</tt>. Additionally, this method can return
   * <tt>true</tt> <i>only</i> if the specified Object is also a property
   * definition and it has the same name, as returned by {@link #getName()},
   * and also is deemed to be &quot;compatible&quot; with this property
   * definition. Compatibility means that the two property definitions share the
   * same underlying value type and provide similar comparator implementations.
   *
   * @param o
   *          The reference object with which to compare.
   * @return Returns <code>true</code> only if the specified object is also a
   *         property definition and it has the same name and is compatible with
   *         this property definition.
   * @see java.lang.Object#equals(java.lang.Object)
   * @see java.lang.Object#hashCode()
   */
  boolean equals(Object o);



  /**
   * Get the default behavior provider associated with this property
   * definition.
   *
   * @return Returns the default behavior provider associated with this
   *         property definition.
   */
  DefaultBehaviorProvider<T> getDefaultBehaviorProvider();



  /**
   * Gets the optional description of this property definition in the
   * default locale.
   *
   * @return Returns the description of this property definition in
   *         the default locale, or <code>null</code> if there is no
   *         description.
   */
  String getDescription();



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
  String getDescription(Locale locale);



  /**
   * Get the name of the property.
   *
   * @return Returns the name of the property.
   */
  String getName();



  /**
   * Gets the synopsis of this property definition in the default
   * locale.
   *
   * @return Returns the synopsis of this property definition in the
   *         default locale.
   */
  String getSynopsis();



  /**
   * Gets the synopsis of this property definition in the specified
   * locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this property definition in the
   *         specified locale.
   */
  String getSynopsis(Locale locale);



  /**
   * Returns a hash code value for this property definition. The hash code
   * should be derived from the property name.
   *
   * @return Returns the hash code value for this property definition.
   */
  int hashCode();



  /**
   * Check if the specified option is set for this property definition.
   *
   * @param option
   *          The option to test.
   * @return Returns <code>true</code> if the option is set, or
   *         <code>false</code> otherwise.
   */
  boolean hasOption(PropertyOption option);



  /**
   * Get a normalized string representation of a property value. This can then
   * be used for comparisons and for generating hash-codes.
   * <p>
   * This method may throw an exception if the provided value is invalid.
   * However, applications should not assume that implementations of this method
   * will always validate a value. This task is the responsibility of
   * {@link #validateValue(Object)}.
   *
   * @param value
   *          The property value to be normalized.
   * @return Returns the normalized property value.
   * @throws IllegalPropertyValueException
   *           If the property value is invalid.
   */
  String normalizeValue(T value) throws IllegalPropertyValueException;



  /**
   * Append a string representation of the property definition to the provided
   * string builder.
   *
   * @param builder
   *          The string builder where the string representation should be
   *          appended.
   */
  void toString(StringBuilder builder);



  /**
   * Determine if the provided property value is valid according to this
   * property definition.
   *
   * @param value
   *          The property value (must not be <code>null</code>).
   * @throws IllegalPropertyValueException
   *           If the property value is invalid.
   */
  void validateValue(T value) throws IllegalPropertyValueException;
}
