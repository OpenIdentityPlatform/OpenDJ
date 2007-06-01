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



import static org.opends.server.util.Validator.*;

import java.util.EnumSet;



/**
 * Duration property definition.
 * <p>
 * A duration property definition comprises of:
 * <ul>
 * <li>a <i>base unit</i> - specifies the minimum granularity which
 * can be used to specify duration property values. For example, if
 * the base unit is in seconds then values represented in milliseconds
 * will not be permitted. The default base unit is seconds
 * <li>an optional <i>maximum unit</i> - specifies the biggest
 * duration unit which can be used to specify duration property
 * values. Values presented in units greater than this unit will not
 * be permitted. There is no default maximum unit
 * <li><i>lower limit</i> - specifies the smallest duration
 * permitted by the property. The default lower limit is 0 and can
 * never be less than 0
 * <li>an optional <i>upper limit</i> - specifies the biggest
 * duration permitted by the property. By default, there is no upper
 * limit
 * <li>support for <i>unlimited</i> durations - when permitted users
 * can specify "unlimited" durations. These are represented using the
 * decoded value, -1, or the encoded string value "unlimited". By
 * default, unlimited durations are not permitted. In addition, it is
 * not possible to define an upper limit and support unlimited values.
 * </ul>
 * Decoded values are represented using <code>long</code> values in
 * the base unit defined for the duration property definition.
 */
public final class DurationPropertyDefinition extends
    AbstractPropertyDefinition<Long> {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -1491050690542547075L;

  // String used to represent unlimited durations.
  private static final String UNLIMITED = "unlimited";

  // The base unit for this property definition.
  private final DurationUnit baseUnit;

  // The optional maximum unit for this property definition.
  private final DurationUnit maximumUnit;

  // The lower limit of the property value in milli-seconds.
  private final long lowerLimit;

  // The optional upper limit of the property value in milli-seconds.
  private final Long upperLimit;

  // Indicates whether this property allows the use of the "unlimited"
  // duration value (represented using a -1L or the string
  // "unlimited").
  private final boolean allowUnlimited;



  /**
   * An interface for incrementally constructing duration property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<Long, DurationPropertyDefinition> {

    // The base unit for this property definition.
    private DurationUnit baseUnit = DurationUnit.SECONDS;

    // The optional maximum unit for this property definition.
    private DurationUnit maximumUnit = null;

    // The lower limit of the property value in milli-seconds.
    private long lowerLimit = 0L;

    // The optional upper limit of the property value in
    // milli-seconds.
    private Long upperLimit = null;

    // Indicates whether this property allows the use of the
    // "unlimited" duration value (represented using a -1L or the
    // string "unlimited").
    private boolean allowUnlimited = false;



    // Private constructor
    private Builder(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName) {
      super(d, propertyName);
    }



    /**
     * Set the base unit for this property definition (values
     * including limits are specified in this unit). By default a
     * duration property definition uses seconds.
     *
     * @param unit
     *          The string representation of the base unit (must not
     *          be <code>null</code>).
     * @throws IllegalArgumentException
     *           If the provided unit name did not correspond to a
     *           known duration unit, or if the base unit is bigger
     *           than the maximum unit.
     */
    public final void setBaseUnit(String unit) throws IllegalArgumentException {
      ensureNotNull(unit);

      setBaseUnit(DurationUnit.getUnit(unit));
    }



    /**
     * Set the base unit for this property definition (values
     * including limits are specified in this unit). By default a
     * duration property definition uses seconds.
     *
     * @param unit
     *          The base unit (must not be <code>null</code>).
     * @throws IllegalArgumentException
     *           If the provided base unit is bigger than the maximum
     *           unit.
     */
    public final void setBaseUnit(DurationUnit unit)
        throws IllegalArgumentException {
      ensureNotNull(unit);

      // Make sure that the base unit is not bigger than the maximum
      // unit.
      if (maximumUnit != null) {
        if (unit.getDuration() > maximumUnit.getDuration()) {
          throw new IllegalArgumentException(
              "Base unit greater than maximum unit");
        }
      }

      this.baseUnit = unit;
    }



    /**
     * Set the maximum unit for this property definition. By default
     * there is no maximum unit.
     *
     * @param unit
     *          The string representation of the maximum unit, or
     *          <code>null</code> if there should not be a maximum
     *          unit.
     * @throws IllegalArgumentException
     *           If the provided unit name did not correspond to a
     *           known duration unit, or if the maximum unit is
     *           smaller than the base unit.
     */
    public final void setMaximumUnit(String unit)
        throws IllegalArgumentException {
      if (unit == null) {
        setMaximumUnit((DurationUnit) null);
      } else {
        setMaximumUnit(DurationUnit.getUnit(unit));
      }
    }



    /**
     * Set the maximum unit for this property definition. By default
     * there is no maximum unit.
     *
     * @param unit
     *          The maximum unit, or <code>null</code> if there
     *          should not be a maximum unit.
     * @throws IllegalArgumentException
     *           If the provided maximum unit is smaller than the base
     *           unit.
     */
    public final void setMaximumUnit(DurationUnit unit)
        throws IllegalArgumentException {
      if (unit != null) {
        // Make sure that the maximum unit is not smaller than the
        // base unit.
        if (unit.getDuration() < baseUnit.getDuration()) {
          throw new IllegalArgumentException(
              "Maximum unit smaller than base unit");
        }
      }

      this.maximumUnit = unit;
    }



    /**
     * Set the lower limit in milli-seconds.
     *
     * @param lowerLimit
     *          The new lower limit (must be >= 0) in milli-seconds.
     * @throws IllegalArgumentException
     *           If a negative lower limit was specified, or the lower
     *           limit is greater than the upper limit.
     */
    public final void setLowerLimit(long lowerLimit)
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
     * Set the lower limit using a string representation of the limit.
     * If the string does not specify a unit, the current base unit
     * will be used.
     *
     * @param lowerLimit
     *          The string representation of the new lower limit.
     * @throws IllegalArgumentException
     *           If the lower limit could not be parsed, or if a
     *           negative lower limit was specified, or the lower
     *           limit is greater than the upper limit.
     */
    public final void setLowerLimit(String lowerLimit)
        throws IllegalArgumentException {
      setLowerLimit(DurationUnit.parseValue(lowerLimit, baseUnit));
    }



    /**
     * Set the upper limit in milli-seconds.
     *
     * @param upperLimit
     *          The new upper limit in milli-seconds, or
     *          <code>null</code> if there is no upper limit.
     * @throws IllegalArgumentException
     *           If a negative upper limit was specified, or the lower
     *           limit is greater than the upper limit or unlimited
     *           durations are permitted.
     */
    public final void setUpperLimit(Long upperLimit)
        throws IllegalArgumentException {
      if (upperLimit != null) {
        if (upperLimit < 0) {
          throw new IllegalArgumentException("Negative upper limit");
        }

        if (lowerLimit > upperLimit) {
          throw new IllegalArgumentException(
              "Lower limit greater than upper limit");
        }

        if (allowUnlimited) {
          throw new IllegalArgumentException(
              "Upper limit specified when unlimited durations are permitted");
        }
      }

      this.upperLimit = upperLimit;
    }



    /**
     * Set the upper limit using a string representation of the limit.
     * If the string does not specify a unit, the current base unit
     * will be used.
     *
     * @param upperLimit
     *          The string representation of the new upper limit, or
     *          <code>null</code> if there is no upper limit.
     * @throws IllegalArgumentException
     *           If the upper limit could not be parsed, or if the
     *           lower limit is greater than the upper limit.
     */
    public final void setUpperLimit(String upperLimit)
        throws IllegalArgumentException {
      if (upperLimit == null) {
        setUpperLimit((Long) null);
      } else {
        setUpperLimit(DurationUnit.parseValue(upperLimit, baseUnit));
      }
    }



    /**
     * Specify whether or not this property definition will allow
     * unlimited values (default is false).
     *
     * @param allowUnlimited
     *          <code>true</code> if the property will allow
     *          unlimited values, or <code>false</code> otherwise.
     * @throws IllegalArgumentException
     *           If unlimited values are to be permitted but there is
     *           an upper limit specified.
     */
    public final void setAllowUnlimited(boolean allowUnlimited)
        throws IllegalArgumentException {
      if (allowUnlimited && upperLimit != null) {
        throw new IllegalArgumentException(
            "Upper limit specified when unlimited durations are permitted");
      }

      this.allowUnlimited = allowUnlimited;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected DurationPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<Long> defaultBehavior) {
      return new DurationPropertyDefinition(d, propertyName, options,
          defaultBehavior, baseUnit, maximumUnit, lowerLimit, upperLimit,
          allowUnlimited);
    }
  }



  /**
   * Create a duration property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new integer property definition builder.
   */
  public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d,
      String propertyName) {
    return new Builder(d, propertyName);
  }



  // Private constructor.
  private DurationPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d,
      String propertyName, EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<Long> defaultBehavior, DurationUnit baseUnit,
      DurationUnit maximumUnit, Long lowerLimit, Long upperLimit,
      boolean allowUnlimited) {
    super(d, Long.class, propertyName, options, defaultBehavior);
    this.baseUnit = baseUnit;
    this.maximumUnit = maximumUnit;
    this.lowerLimit = lowerLimit;
    this.upperLimit = upperLimit;
    this.allowUnlimited = allowUnlimited;
  }



  /**
   * Get the base unit for this property definition (values including
   * limits are specified in this unit).
   *
   * @return Returns the base unit for this property definition
   *         (values including limits are specified in this unit).
   */
  public DurationUnit getBaseUnit() {
    return baseUnit;
  }



  /**
   * Get the maximum unit for this property definition if specified.
   *
   * @return Returns the maximum unit for this property definition, or
   *         <code>null</code> if there is no maximum unit.
   */
  public DurationUnit getMaximumUnit() {
    return maximumUnit;
  }



  /**
   * Get the lower limit in milli-seconds.
   *
   * @return Returns the lower limit in milli-seconds.
   */
  public long getLowerLimit() {
    return lowerLimit;
  }



  /**
   * Get the upper limit in milli-seconds.
   *
   * @return Returns the upper limit in milli-seconds, or
   *         <code>null</code> if there is no upper limit.
   */
  public Long getUpperLimit() {
    return upperLimit;
  }



  /**
   * Determine whether this property allows unlimited durations.
   *
   * @return Returns <code>true</code> if this this property allows
   *         unlimited durations.
   */
  public boolean isAllowUnlimited() {
    return allowUnlimited;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(Long value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    long nvalue = baseUnit.toMilliSeconds(value);
    if (!allowUnlimited && nvalue < lowerLimit) {
      throw new IllegalPropertyValueException(this, value);

      // unlimited allowed
    } else if (nvalue >= 0 && nvalue < lowerLimit) {
      throw new IllegalPropertyValueException(this, value);
    }

    if ((upperLimit != null) && (nvalue > upperLimit)) {
      throw new IllegalPropertyValueException(this, value);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String encodeValue(Long value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    // Make sure that we correctly encode negative values as
    // "unlimited".
    if (allowUnlimited) {
      if (value < 0) {
        return UNLIMITED;
      }
    }

    // Encode the size value using the base unit.
    StringBuilder builder = new StringBuilder();
    builder.append(value);
    builder.append(baseUnit.toString());
    return builder.toString();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Long decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    // First check for the special "unlimited" value when necessary.
    if (allowUnlimited) {
      if (value.trim().equalsIgnoreCase(UNLIMITED)) {
        return -1L;
      }
    }

    // Parse the string representation.
    long ms;
    try {
      ms = DurationUnit.parseValue(value);
    } catch (NumberFormatException e) {
      throw new IllegalPropertyValueStringException(this, value);
    }

    // Check the unit is in range - values must not be more granular
    // than the base unit.
    if ((ms % baseUnit.getDuration()) != 0) {
      throw new IllegalPropertyValueStringException(this, value);
    }

    // Convert the value a long in the property's required unit.
    Long i = (long) baseUnit.fromMilliSeconds(ms);
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
    return v.visitDuration(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder builder) {
    super.toString(builder);

    builder.append(" baseUnit=");
    builder.append(baseUnit);

    if (maximumUnit != null) {
      builder.append(" maximumUnit=");
      builder.append(maximumUnit);
    }

    builder.append(" lowerLimit=");
    builder.append(lowerLimit);
    builder.append("ms");

    if (upperLimit != null) {
      builder.append(" upperLimit=");
      builder.append(upperLimit);
      builder.append("ms");
    }

    builder.append(" allowUnlimited=");
    builder.append(allowUnlimited);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(Long o1, Long o2) {
    return o1.compareTo(o2);
  }

}
