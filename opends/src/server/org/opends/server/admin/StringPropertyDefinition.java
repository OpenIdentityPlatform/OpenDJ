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



/**
 * String property definition.
 * <p>
 * TODO: pattern matching.
 */
public final class StringPropertyDefinition extends
    AbstractPropertyDefinition<String> {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -2739105900274621416L;

  // Flag indicating whether values of this property are
  // case-insensitive.
  private final boolean isCaseInsensitive;



  /**
   * An interface for incrementally constructing string property definitions.
   */
  public static class Builder extends
      AbstractBuilder<String, StringPropertyDefinition> {

    // Flag indicating whether values of this property are
    // case-insensitive.
    private boolean isCaseInsensitive = true;



    // Private constructor
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /**
     * Set a flag indicating whether values of this property are
     * case-insensitive.
     *
     * @param value
     *          <code>true</code> if values are case-insensitive, or
     *          <code>false</code> otherwise.
     */
    public final void setCaseInsensitive(boolean value) {
      isCaseInsensitive = value;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected StringPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<String> defaultBehavior) {
      return new StringPropertyDefinition(d, propertyName, options,
          defaultBehavior, isCaseInsensitive);
    }

  }



  /**
   * Create a string property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new string property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }



  // Private constructor.
  private StringPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<String> defaultBehavior,
      boolean isCaseInsensitive) {
    super(d, String.class, propertyName, options, defaultBehavior);
    this.isCaseInsensitive = isCaseInsensitive;
  }



  /**
   * Gets the pattern synopsis of this string property definition in
   * the default locale.
   *
   * @return Returns the pattern synopsis of this string property
   *         definition in the default locale, or <code>null</code>
   *         if there is no pattern synopsis (which is the case when
   *         there is no pattern matching defined for this string
   *         property definition).
   */
  public String getPatternSynopsis() {
    return getPatternSynopsis(Locale.getDefault());
  }



  /**
   * Gets the optional pattern synopsis of this string property
   * definition in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the pattern synopsis of this string property
   *         definition in the specified locale, or <code>null</code>
   *         if there is no pattern synopsis (which is the case when
   *         there is no pattern matching defined for this string
   *         property definition).
   */
  public String getPatternSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + getName()
        + ".syntax.string.pattern.synopsis";
    try {
      return resource.getMessage(getManagedObjectDefinition(),
          property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * Query whether values of this property are case-insensitive.
   *
   * @return Returns <code>true</code> if values are case-insensitive, or
   *         <code>false</code> otherwise.
   */
  public boolean isCaseInsensitive() {
    return isCaseInsensitive;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String normalizeValue(String value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    if (isCaseInsensitive()) {
      return value.trim().toLowerCase();
    } else {
      return value.trim();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(String value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    // No additional validation required for now (might do pattern
    // matching in future).
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    return value;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitString(this, p);
  }
}
