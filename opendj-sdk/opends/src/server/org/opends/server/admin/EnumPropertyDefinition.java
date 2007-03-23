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
import java.util.HashMap;
import java.util.Map;



/**
 * Enumeration property definition.
 *
 * @param <E>
 *          The enumeration that should be used for values of this
 *          property definition.
 */
public final class EnumPropertyDefinition<E extends Enum<E>> extends
    AbstractPropertyDefinition<E> {

  /**
   * An interface for incrementally constructing enumeration property
   * definitions.
   *
   * @param <E>
   *          The enumeration that should be used for values of this
   *          property definition.
   */
  public static class Builder<E extends Enum<E>> extends
      AbstractBuilder<E, EnumPropertyDefinition<E>> {

    // The enumeration class.
    private Class<E> enumClass;



    // Private constructor
    private Builder(String propertyName) {
      super(propertyName);
      this.enumClass = null;
    }



    /**
     * Set the enumeration class which should be used for values of
     * this property definition.
     *
     * @param enumClass
     *          The enumeration class which should be used for values
     *          of this property definition.
     */
    public final void setEnumClass(Class<E> enumClass) {
      this.enumClass = enumClass;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected EnumPropertyDefinition<E> buildInstance(
        String propertyName, EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<E> defaultBehavior) {
      // Make sure that the enumeration class has been defined.
      if (enumClass == null) {
        throw new IllegalStateException("Enumeration class undefined");
      }

      return new EnumPropertyDefinition<E>(propertyName, options,
          defaultBehavior, enumClass);
    }
  }

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 338458138694686844L;



  /**
   * Create an enumeration property definition builder.
   *
   * @param <E>
   *          The enumeration that should be used for values of this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new enumeration property definition builder.
   */
  public static <E extends Enum<E>> Builder<E> createBuilder(
      String propertyName) {
    return new Builder<E>(propertyName);
  }

  // The enumeration class.
  private final Class<E> enumClass;

  // Map used for decoding values.
  private final Map<String, E> decodeMap;



  // Private constructor.
  private EnumPropertyDefinition(String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<E> defaultBehavior, Class<E> enumClass) {
    super(enumClass, propertyName, options, defaultBehavior);
    this.enumClass = enumClass;

    // Initialize the decoding map.
    this.decodeMap = new HashMap<String, E>();
    for (E value : EnumSet.<E> allOf(enumClass)) {
      String s = value.toString().trim().toLowerCase();
      this.decodeMap.put(s, value);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitEnum(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public E decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    String nvalue = value.trim().toLowerCase();
    E eValue = decodeMap.get(nvalue);
    if (eValue == null) {
      throw new IllegalPropertyValueStringException(this, value);
    } else {
      return eValue;
    }
  }



  /**
   * Get the enumeration class used for values of this property.
   *
   * @return Returns the enumeration class used for values of this
   *         property.
   */
  public Class<E> getEnumClass() {
    return enumClass;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String normalizeValue(E value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    return value.toString().trim().toLowerCase();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(E value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    // No additional validation required.
  }
}
