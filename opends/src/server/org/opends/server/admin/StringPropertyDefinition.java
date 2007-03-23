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
    private Builder(String propertyName) {
      super(propertyName);
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
    protected StringPropertyDefinition buildInstance(String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<String> defaultBehavior) {
      return new StringPropertyDefinition(propertyName, options,
          defaultBehavior, isCaseInsensitive);
    }

  }



  /**
   * Create a string property definition builder.
   *
   * @param propertyName
   *          The property name.
   * @return Returns the new string property definition builder.
   */
  public static Builder createBuilder(String propertyName) {
    return new Builder(propertyName);
  }



  // Private constructor.
  private StringPropertyDefinition(String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<String> defaultBehavior,
      boolean isCaseInsensitive) {
    super(String.class, propertyName, options, defaultBehavior);
    this.isCaseInsensitive = isCaseInsensitive;
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
