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
import java.util.Locale;
import java.util.MissingResourceException;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Integer property definition.
 * <p>
 * All values must be zero or positive and within the lower/upper limit
 * constraints. Support is provided for "unlimited" values. These are
 * represented using a negative value or using the string "unlimited".
 */
public final class IntegerPropertyDefinition extends PropertyDefinition<Integer> {

    /** String used to represent unlimited. */
    private static final String UNLIMITED = "unlimited";

    /** The lower limit of the property value. */
    private final int lowerLimit;

    /** The optional upper limit of the property value. */
    private final Integer upperLimit;

    /**
     * Indicates whether this property allows the use of the "unlimited" value
     * (represented using a -1 or the string "unlimited").
     */
    private final boolean allowUnlimited;

    /** An interface for incrementally constructing integer property definitions. */
    public static final class Builder extends AbstractBuilder<Integer, IntegerPropertyDefinition> {

        /** The lower limit of the property value. */
        private int lowerLimit;

        /** The optional upper limit of the property value. */
        private Integer upperLimit;

        /**
         * Indicates whether this property allows the use of the "unlimited" value
         * (represented using a -1 or the string "unlimited").
         */
        private boolean allowUnlimited;

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /**
         * Set the lower limit.
         *
         * @param lowerLimit
         *            The new lower limit (must be >= 0).
         * @throws IllegalArgumentException
         *             If a negative lower limit was specified or the lower
         *             limit is greater than the upper limit.
         */
        public final void setLowerLimit(int lowerLimit) {
            if (lowerLimit < 0) {
                throw new IllegalArgumentException("Negative lower limit");
            }
            if (upperLimit != null && lowerLimit > upperLimit) {
                throw new IllegalArgumentException("Lower limit greater than upper limit");
            }
            this.lowerLimit = lowerLimit;
        }

        /**
         * Set the upper limit.
         *
         * @param upperLimit
         *            The new upper limit or <code>null</code> if there is no
         *            upper limit.
         */
        public final void setUpperLimit(Integer upperLimit) {
            if (upperLimit != null) {
                if (upperLimit < 0) {
                    throw new IllegalArgumentException("Negative lower limit");
                }
                if (lowerLimit > upperLimit) {
                    throw new IllegalArgumentException("Lower limit greater than upper limit");
                }
            }
            this.upperLimit = upperLimit;
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
        protected IntegerPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<Integer> defaultBehavior) {
            return new IntegerPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior, lowerLimit,
                upperLimit, allowUnlimited);
        }

    }

    /**
     * Create an integer property definition builder.
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
    private IntegerPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<Integer> defaultBehavior, int lowerLimit, Integer upperLimit, boolean allowUnlimited) {
        super(d, Integer.class, propertyName, options, adminAction, defaultBehavior);
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
     * Gets the optional unit synopsis of this integer property definition in
     * the default locale.
     *
     * @return Returns the unit synopsis of this integer property definition in
     *         the default locale, or <code>null</code> if there is no unit
     *         synopsis.
     */
    public LocalizableMessage getUnitSynopsis() {
        return getUnitSynopsis(Locale.getDefault());
    }

    /**
     * Gets the optional unit synopsis of this integer property definition in
     * the specified locale.
     *
     * @param locale
     *            The locale.
     * @return Returns the unit synopsis of this integer property definition in
     *         the specified locale, or <code>null</code> if there is no unit
     *         synopsis.
     */
    public LocalizableMessage getUnitSynopsis(Locale locale) {
        ManagedObjectDefinitionI18NResource resource = ManagedObjectDefinitionI18NResource.getInstance();
        String property = "property." + getName() + ".syntax.integer.unit-synopsis";
        try {
            return resource.getMessage(getManagedObjectDefinition(), property, locale);
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

    @Override
    public void validateValue(Integer value) {
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
    public String encodeValue(Integer value) {
        Reject.ifNull(value);

        // Make sure that we correctly encode negative values as "unlimited".
        if (allowUnlimited && value < 0) {
            return UNLIMITED;
        }

        return value.toString();
    }

    @Override
    public Integer decodeValue(String value) {
        Reject.ifNull(value);

        if (allowUnlimited && UNLIMITED.equalsIgnoreCase(value.trim())) {
            return -1;
        }

        Integer i;
        try {
            i = Integer.valueOf(value);
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
        return v.visitInteger(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, Integer value, P p) {
        return v.visitInteger(this, value, p);
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
    public int compare(Integer o1, Integer o2) {
        return o1.compareTo(o2);
    }

}
