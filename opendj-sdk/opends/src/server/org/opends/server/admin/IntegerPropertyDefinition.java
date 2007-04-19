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
 * Integer property definition.
 * <p>
 * All values must be zero or positive and within the lower/upper limit
 * constraints. Support is provided for "unlimited" values. These are
 * represented using a negative value or using the string "unlimited".
 */
public final class IntegerPropertyDefinition extends
    AbstractPropertyDefinition<Integer> {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 2819904868308720588L;

  // String used to represent unlimited.
  private static final String UNLIMITED = "unlimited";

  // The lower limit of the property value.
  private final int lowerLimit;

  // The optional upper limit of the property value.
  private final Integer upperLimit;

  // Indicates whether this property allows the use of the "unlimited" value
  // (represented using a -1 or the string "unlimited").
  private final boolean allowUnlimited;



  /**
   * An interface for incrementally constructing integer property definitions.
   */
  public static class Builder extends
      AbstractBuilder<Integer, IntegerPropertyDefinition> {

    // The lower limit of the property value.
    private int lowerLimit = 0;

    // The optional upper limit of the property value.
    private Integer upperLimit = null;

    // Indicates whether this property allows the use of the "unlimited" value
    // (represented using a -1 or the string "unlimited").
    private boolean allowUnlimited = false;



    // Private constructor
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /**
     * Set the lower limit.
     *
     * @param lowerLimit
     *          The new lower limit (must be >= 0).
     * @throws IllegalArgumentException
     *           If a negative lower limit was specified or the lower limit is
     *           greater than the upper limit.
     */
    public final void setLowerLimit(int lowerLimit)
        throws IllegalArgumentException {
      if (lowerLimit < 0) {
        throw new IllegalArgumentException("Negative lower limit");
      }
      if (upperLimit != null && lowerLimit > upperLimit) {
        throw new IllegalArgumentException(
            "Lower limit greater than upper limit");
      }
      this.lowerLimit = lowerLimit;
    }



    /**
     * Set the upper limit.
     *
     * @param upperLimit
     *          The new upper limit or <code>null</code> if there is no upper
     *          limit.
     */
    public final void setUpperLimit(Integer upperLimit) {
      if (upperLimit != null) {
        if (upperLimit < 0) {
          throw new IllegalArgumentException("Negative lower limit");
        }
        if (lowerLimit > upperLimit) {
          throw new IllegalArgumentException(
              "Lower limit greater than upper limit");
        }
      }
      this.upperLimit = upperLimit;
    }



    /**
     * Specify whether or not this property definition will allow unlimited
     * values (default is false).
     *
     * @param allowUnlimited
     *          <code>true</code> if the property will allow unlimited values,
     *          or <code>false</code> otherwise.
     */
    public final void setAllowUnlimited(boolean allowUnlimited) {
      this.allowUnlimited = allowUnlimited;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected IntegerPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<Integer> defaultBehavior) {
      return new IntegerPropertyDefinition(d, propertyName, options,
          defaultBehavior, lowerLimit, upperLimit, allowUnlimited);
    }

  }



  /**
   * Create an integer property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new integer property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }



  // Private constructor.
  private IntegerPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<Integer> defaultBehavior, int lowerLimit,
      Integer upperLimit, boolean allowUnlimited) {
    super(d, Integer.class, propertyName, options, defaultBehavior);
    this.lowerLimit = lowerLimit;
    this.upperLimit = upperLimit;
    this.allowUnlimited = allowUnlimited;
  }



  /**
   * Get the lower limit.
   *
   * @return Returns the lower limit.
   */
  public int getLowerLimit() {
    return lowerLimit;
  }



  /**
   * Get the upper limit.
   *
   * @return Returns the upper limit or <code>null</code> if there is no upper
   *         limit.
   */
  public Integer getUpperLimit() {
    return upperLimit;
  }



  /**
   * Gets the optional unit synopsis of this integer property
   * definition in the default locale.
   *
   * @return Returns the unit synopsis of this integer property
   *         definition in the default locale, or <code>null</code>
   *         if there is no unit synopsis.
   */
  public String getUnitSynopsis() {
    return getUnitSynopsis(Locale.getDefault());
  }



  /**
   * Gets the optional unit synopsis of this integer property
   * definition in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the unit synopsis of this integer property
   *         definition in the specified locale, or <code>null</code>
   *         if there is no unit synopsis.
   */
  public String getUnitSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + getName() + ".syntax.integer.unit-synopsis";
    try {
      return resource.getMessage(getManagedObjectDefinition(),
          property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * Determine whether this property allows unlimited values.
   *
   * @return Returns <code>true</code> if this this property allows unlimited
   *         values.
   */
  public boolean isAllowUnlimited() {
    return allowUnlimited;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(Integer value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    if (!allowUnlimited && value < lowerLimit) {
      throw new IllegalPropertyValueException(this, value);

    // unlimited allowed
    } else if (value >= 0 && value < lowerLimit) {
      throw new IllegalPropertyValueException(this, value);
    }

    if ((upperLimit != null) && (value > upperLimit)) {
      throw new IllegalPropertyValueException(this, value);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String encodeValue(Integer value)
          throws IllegalPropertyValueException {
    ensureNotNull(value);

    // Make sure that we correctly encode negative values as "unlimited".
    if (allowUnlimited) {
      if (value < 0) {
        return UNLIMITED;
      }
    }

    return value.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Integer decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    if (allowUnlimited) {
      if (value.trim().equalsIgnoreCase(UNLIMITED)) {
        return -1;
      }
    }

    Integer i;
    try {
      i = Integer.valueOf(value);
    } catch (NumberFormatException e) {
      throw new IllegalPropertyValueStringException(this, value);
    }

    try {
      validateValue(i);
    } catch (IllegalPropertyValueException e) {
      throw new IllegalPropertyValueStringException(this, value);
    }

    return i;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitInteger(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder builder) {
    super.toString(builder);

    builder.append(" lowerLimit=");
    builder.append(lowerLimit);

    if (upperLimit != null) {
      builder.append(" upperLimit=");
      builder.append(upperLimit);
    }

    builder.append(" allowUnlimited=");
    builder.append(allowUnlimited);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(Integer o1, Integer o2) {
    return o1.compareTo(o2);
  }

}
