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
 * Memory size property definition.
 * <p>
 * All memory size property values are represented in bytes using longs.
 * <p>
 * All values must be zero or positive and within the lower/upper limit
 * constraints. Support is provided for "unlimited" memory sizes. These are
 * represented using a negative memory size value or using the string
 * "unlimited".
 */
public final class SizePropertyDefinition extends PropertyDefinition<Long> {

  // String used to represent unlimited memory sizes.
  private static final String UNLIMITED = "unlimited";

  // The lower limit of the property value in bytes.
  private final long lowerLimit;

  // The optional upper limit of the property value in bytes.
  private final Long upperLimit;

  // Indicates whether this property allows the use of the "unlimited" memory
  // size value (represented using a -1L or the string "unlimited").
  private final boolean allowUnlimited;



  /**
   * An interface for incrementally constructing memory size property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<Long, SizePropertyDefinition> {

    // The lower limit of the property value in bytes.
    private long lowerLimit = 0L;

    // The optional upper limit of the property value in bytes.
    private Long upperLimit = null;

    // Indicates whether this property allows the use of the "unlimited" memory
    // size value (represented using a -1L or the string "unlimited").
    private boolean allowUnlimited = false;



    // Private constructor
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /**
     * Set the lower limit in bytes.
     *
     * @param lowerLimit
     *          The new lower limit (must be >= 0) in bytes.
     * @throws IllegalArgumentException
     *           If a negative lower limit was specified, or if the lower limit
     *           is greater than the upper limit.
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
     *
     * @param lowerLimit
     *          The string representation of the new lower limit.
     * @throws IllegalArgumentException
     *           If the lower limit could not be parsed, or if a negative lower
     *           limit was specified, or the lower limit is greater than the
     *           upper limit.
     */
    public final void setLowerLimit(String lowerLimit)
        throws IllegalArgumentException {
      setLowerLimit(SizeUnit.parseValue(lowerLimit, SizeUnit.BYTES));
    }



    /**
     * Set the upper limit in bytes.
     *
     * @param upperLimit
     *          The new upper limit in bytes or <code>null</code> if there is
     *          no upper limit.
     * @throws IllegalArgumentException
     *           If the lower limit is greater than the upper limit.
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
      }
      this.upperLimit = upperLimit;
    }



    /**
     * Set the upper limit using a string representation of the limit.
     *
     * @param upperLimit
     *          The string representation of the new upper limit, or
     *          <code>null</code> if there is no upper limit.
     * @throws IllegalArgumentException
     *           If the upper limit could not be parsed, or if the lower limit
     *           is greater than the upper limit.
     */
    public final void setUpperLimit(String upperLimit)
        throws IllegalArgumentException {
      if (upperLimit == null) {
        setUpperLimit((Long) null);
      } else {
        setUpperLimit(SizeUnit.parseValue(upperLimit, SizeUnit.BYTES));
      }
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
    protected SizePropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<Long> defaultBehavior) {
      return new SizePropertyDefinition(d, propertyName, options, adminAction,
          defaultBehavior, lowerLimit, upperLimit, allowUnlimited);
    }

  }



  /**
   * Create an memory size property definition builder.
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
  private SizePropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<Long> defaultBehavior, Long lowerLimit,
      Long upperLimit, boolean allowUnlimited) {
    super(d, Long.class, propertyName, options, adminAction,
        defaultBehavior);
    this.lowerLimit = lowerLimit;
    this.upperLimit = upperLimit;
    this.allowUnlimited = allowUnlimited;
  }



  /**
   * Get the lower limit in bytes.
   *
   * @return Returns the lower limit in bytes.
   */
  public long getLowerLimit() {
    return lowerLimit;
  }



  /**
   * Get the upper limit in bytes.
   *
   * @return Returns the upper limit in bytes or <code>null</code> if there is
   *         no upper limit.
   */
  public Long getUpperLimit() {
    return upperLimit;
  }



  /**
   * Determine whether this property allows unlimited memory sizes.
   *
   * @return Returns <code>true</code> if this this property allows unlimited
   *         memory sizes.
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
  public String encodeValue(Long value) throws IllegalPropertyValueException {
    ensureNotNull(value);

    // Make sure that we correctly encode negative values as "unlimited".
    if (allowUnlimited) {
      if (value < 0) {
        return UNLIMITED;
      }
    }

    // Encode the size value using the best-fit unit.
    StringBuilder builder = new StringBuilder();
    SizeUnit unit = SizeUnit.getBestFitUnitExact(value);

    // Cast to a long to remove fractional part (which should not be there
    // anyway as the best-fit unit should result in an exact conversion).
    builder.append((long) unit.fromBytes(value));
    builder.append(' ');
    builder.append(unit.toString());
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

    // Decode the value.
    Long i;
    try {
      i = SizeUnit.parseValue(value, SizeUnit.BYTES);
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
    return v.visitSize(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, Long value, P p) {
    return v.visitSize(this, value, p);
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
  public int compare(Long o1, Long o2) {
    return o1.compareTo(o2);
  }

}
