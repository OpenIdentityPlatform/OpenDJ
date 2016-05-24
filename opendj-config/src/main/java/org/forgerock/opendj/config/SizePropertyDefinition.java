/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import org.forgerock.util.Reject;

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

    /** String used to represent unlimited memory sizes. */
    private static final String UNLIMITED = "unlimited";

    /** The lower limit of the property value in bytes. */
    private final long lowerLimit;

    /** The optional upper limit of the property value in bytes. */
    private final Long upperLimit;

    /**
     * Indicates whether this property allows the use of the "unlimited" memory
     * size value (represented using a -1L or the string "unlimited").
     */
    private final boolean allowUnlimited;

    /** An interface for incrementally constructing memory size property definitions. */
    public static final class Builder extends AbstractBuilder<Long, SizePropertyDefinition> {

        /** The lower limit of the property value in bytes. */
        private long lowerLimit;

        /** The optional upper limit of the property value in bytes. */
        private Long upperLimit;

        /**
         * Indicates whether this property allows the use of the "unlimited" memory
         * size value (represented using a -1L or the string "unlimited").
         */
        private boolean allowUnlimited;

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /**
         * Set the lower limit in bytes.
         *
         * @param lowerLimit
         *            The new lower limit (must be >= 0) in bytes.
         * @throws IllegalArgumentException
         *             If a negative lower limit was specified, or if the lower
         *             limit is greater than the upper limit.
         */
        public final void setLowerLimit(long lowerLimit) {
            if (lowerLimit < 0) {
                throw new IllegalArgumentException("Negative lower limit");
            }
            if (upperLimit != null && lowerLimit > upperLimit) {
                throw new IllegalArgumentException("Lower limit greater than upper limit");
            }
            this.lowerLimit = lowerLimit;
        }

        /**
         * Set the lower limit using a string representation of the limit.
         *
         * @param lowerLimit
         *            The string representation of the new lower limit.
         * @throws IllegalArgumentException
         *             If the lower limit could not be parsed, or if a negative
         *             lower limit was specified, or the lower limit is greater
         *             than the upper limit.
         */
        public final void setLowerLimit(String lowerLimit) {
            setLowerLimit(SizeUnit.parseValue(lowerLimit, SizeUnit.BYTES));
        }

        /**
         * Set the upper limit in bytes.
         *
         * @param upperLimit
         *            The new upper limit in bytes or <code>null</code> if there
         *            is no upper limit.
         * @throws IllegalArgumentException
         *             If the lower limit is greater than the upper limit.
         */
        public final void setUpperLimit(Long upperLimit) {
            if (upperLimit != null) {
                if (upperLimit < 0) {
                    throw new IllegalArgumentException("Negative upper limit");
                }
                if (lowerLimit > upperLimit) {
                    throw new IllegalArgumentException("Lower limit greater than upper limit");
                }
            }
            this.upperLimit = upperLimit;
        }

        /**
         * Set the upper limit using a string representation of the limit.
         *
         * @param upperLimit
         *            The string representation of the new upper limit, or
         *            <code>null</code> if there is no upper limit.
         * @throws IllegalArgumentException
         *             If the upper limit could not be parsed, or if the lower
         *             limit is greater than the upper limit.
         */
        public final void setUpperLimit(String upperLimit) {
            setUpperLimit(upperLimit != null ? SizeUnit.parseValue(upperLimit, SizeUnit.BYTES) : null);
        }

        /**
         * Specify whether this property definition will allow unlimited
         * values (default is false).
         *
         * @param allowUnlimited
         *            <code>true</code> if the property will allow unlimited
         *            values, or <code>false</code> otherwise.
         */
        public final void setAllowUnlimited(boolean allowUnlimited) {
            this.allowUnlimited = allowUnlimited;
        }

        @Override
        protected SizePropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
            EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<Long> defaultBehavior) {
            return new SizePropertyDefinition(d, propertyName, options, adminAction, defaultBehavior, lowerLimit,
                upperLimit, allowUnlimited);
        }

    }

    /**
     * Create an memory size property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new integer property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Private constructor. */
    private SizePropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<Long> defaultBehavior, Long lowerLimit, Long upperLimit, boolean allowUnlimited) {
        super(d, Long.class, propertyName, options, adminAction, defaultBehavior);
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

    @Override
    public void validateValue(Long value) {
        Reject.ifNull(value);

        if (!allowUnlimited && value < lowerLimit) {
            throw PropertyException.illegalPropertyValueException(this, value);

            // unlimited allowed
        } else if (value >= 0 && value < lowerLimit) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }

        if (upperLimit != null && value > upperLimit) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    @Override
    public String encodeValue(Long value) {
        Reject.ifNull(value);

        // Make sure that we correctly encode negative values as "unlimited".
        if (allowUnlimited && value < 0) {
            return UNLIMITED;
        }

        // Encode the size value using the best-fit unit.
        StringBuilder builder = new StringBuilder();
        SizeUnit unit = SizeUnit.getBestFitUnitExact(value);

        // Cast to a long to remove fractional part (which should not be there
        // anyway as the best-fit unit should result in an exact conversion).
        builder.append((long) unit.fromBytes(value));
        builder.append(' ');
        builder.append(unit);
        return builder.toString();
    }

    @Override
    public Long decodeValue(String value) {
        Reject.ifNull(value);

        // First check for the special "unlimited" value when necessary.
        if (allowUnlimited && UNLIMITED.equalsIgnoreCase(value.trim())) {
            return -1L;
        }

        // Decode the value.
        Long i;
        try {
            i = SizeUnit.parseValue(value, SizeUnit.BYTES);
        } catch (NumberFormatException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }

        try {
            validateValue(i);
        } catch (PropertyException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
        return i;
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitSize(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, Long value, P p) {
        return v.visitSize(this, value, p);
    }

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

    @Override
    public int compare(Long o1, Long o2) {
        return o1.compareTo(o2);
    }

}
