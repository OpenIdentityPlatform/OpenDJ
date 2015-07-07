/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.server.admin;
import org.forgerock.i18n.LocalizableMessage;



import static org.forgerock.util.Reject.ifNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;



/**
 * Enumeration property definition.
 *
 * @param <E>
 *          The enumeration that should be used for values of this
 *          property definition.
 */
public final class EnumPropertyDefinition<E extends Enum<E>> extends
    PropertyDefinition<E> {

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

    /** The enumeration class. */
    private Class<E> enumClass;



    /** Private constructor. */
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
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



    /** {@inheritDoc} */
    @Override
    protected EnumPropertyDefinition<E> buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<E> defaultBehavior) {
      // Make sure that the enumeration class has been defined.
      if (enumClass == null) {
        throw new IllegalStateException("Enumeration class undefined");
      }

      return new EnumPropertyDefinition<>(d, propertyName, options,
          adminAction, defaultBehavior, enumClass);
    }
  }



  /**
   * Create an enumeration property definition builder.
   *
   * @param <E>
   *          The enumeration that should be used for values of this
   *          property definition.
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new enumeration property definition builder.
   */
  public static <E extends Enum<E>> Builder<E> createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder<>(d, propertyName);
  }

  /** The enumeration class. */
  private final Class<E> enumClass;

  /** Map used for decoding values. */
  private final Map<String, E> decodeMap;



  /** Private constructor. */
  private EnumPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d,
      String propertyName, EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<E> defaultBehavior, Class<E> enumClass) {
    super(d, enumClass, propertyName, options, adminAction, defaultBehavior);
    this.enumClass = enumClass;

    // Initialize the decoding map.
    this.decodeMap = new HashMap<>();
    for (E value : EnumSet.<E> allOf(enumClass)) {
      String s = value.toString().trim().toLowerCase();
      this.decodeMap.put(s, value);
    }
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitEnum(this, p);
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, E value, P p) {
    return v.visitEnum(this, value, p);
  }



  /** {@inheritDoc} */
  @Override
  public E decodeValue(String value)
      throws PropertyException {
    ifNull(value);

    String nvalue = value.trim().toLowerCase();
    E eValue = decodeMap.get(nvalue);
    if (eValue == null) {
      throw PropertyException.illegalPropertyValueException(this, value);
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
   * Gets the synopsis of the specified enumeration value of this
   * enumeration property definition in the default locale.
   *
   * @param value
   *          The enumeration value.
   * @return Returns the synopsis of the specified enumeration value
   *         of this enumeration property definition in the default
   *         locale.
   */
  public final LocalizableMessage getValueSynopsis(E value) {
    return getValueSynopsis(Locale.getDefault(), value);
  }



  /**
   * Gets the synopsis of the specified enumeration value of this
   * enumeration property definition in the specified locale.
   *
   * @param value
   *          The enumeration value.
   * @param locale
   *          The locale.
   * @return Returns the synopsis of the specified enumeration value
   *         of this enumeration property definition in the specified
   *         locale.
   */
  public final LocalizableMessage getValueSynopsis(Locale locale, E value) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + getName()
        + ".syntax.enumeration.value." + value
        + ".synopsis";
    try {
      return resource.getMessage(getManagedObjectDefinition(),
          property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /** {@inheritDoc} */
  @Override
  public String normalizeValue(E value)
      throws PropertyException {
    ifNull(value);

    return value.toString().trim().toLowerCase();
  }



  /** {@inheritDoc} */
  @Override
  public void validateValue(E value)
      throws PropertyException {
    ifNull(value);

    // No additional validation required.
  }
}
