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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2014-2016 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.ERR_MCARG_VALUE_NOT_ALLOWED;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class defines an argument type that will only accept one or more of a
 * specific set of string values.
 *
 * @param <V>
 *            The type of values returned by this argument.
 */
public final class MultiChoiceArgument<V> extends Argument {

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * {@link MultiChoiceArgument<V>}.
     *
     * @param <V>
     *         The type of values returned by this argument.
     * @param longIdentifier
     *         The generic long identifier that will be used to refer to this argument.
     * @return A builder to continue building the {@link MultiChoiceArgument}.
     */
    public static <V> Builder<V> builder(final String longIdentifier) {
        return new Builder<>(longIdentifier);
    }

    /** A fluent API for incrementally constructing {@link MultiChoiceArgument<V>}. */
    public static final class Builder<V> extends ArgumentBuilder<Builder<V>, V, MultiChoiceArgument<V>> {
        private final List<V> allowedValues = new LinkedList<>();

        private Builder(final String longIdentifier) {
            super(longIdentifier);
        }

        @Override
        Builder<V> getThis() {
            return this;
        }

        /**
         * Specifies the set of values that are allowed for the {@link MultiChoiceArgument<V>}.
         *
         * @param allowedValues
         *         The {@link MultiChoiceArgument<V>} allowed values.
         * @return This builder.
         */
        public Builder<V> allowedValues(final Collection<V> allowedValues) {
            this.allowedValues.addAll(allowedValues);
            return getThis();
        }

        /**
         * Specifies the set of values that are allowed for the {@link MultiChoiceArgument<V>}.
         *
         * @param allowedValues
         *         The {@link MultiChoiceArgument<V>} allowed values.
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        public final Builder<V> allowedValues(final V... allowedValues) {
            this.allowedValues.addAll(Arrays.asList(allowedValues));
            return getThis();
        }

        @Override
        public MultiChoiceArgument<V> buildArgument() throws ArgumentException {
            return new MultiChoiceArgument<>(this, allowedValues);
        }
    }

    /** The set of values that will be allowed for use with this argument. */
    private final Collection<V> allowedValues;

    private <V1> MultiChoiceArgument(final Builder<V1> builder, final Collection<V> allowedValues)
            throws ArgumentException {
        super(builder);
        this.allowedValues = allowedValues;
    }

    /**
     * Retrieves the string value for this argument. If it has multiple values,
     * then the first will be returned. If it does not have any values, then the
     * default value will be returned.
     *
     * @return The string value for this argument, or <CODE>null</CODE> if there
     *         are no values and no default value has been given.
     * @throws ArgumentException
     *             The value cannot be parsed.
     */
    public V getTypedValue() throws ArgumentException {
        final String v = super.getValue();
        if (v == null) {
            return null;
        }
        for (final V allowedValue : allowedValues) {
            if (allowedValue.toString().equalsIgnoreCase(v)) {
                return allowedValue;
            }
        }
        throw new IllegalStateException("This MultiChoiceArgument value is not part of the allowed values.");
    }

    /**
     * Indicates whether the provided value is acceptable for use in this
     * argument.
     *
     * @param valueString
     *            The value for which to make the determination.
     * @param invalidReason
     *            A buffer into which the invalid reason may be written if the
     *            value is not acceptable.
     * @return <CODE>true</CODE> if the value is acceptable, or
     *         <CODE>false</CODE> if it is not.
     */
    @Override
    public boolean valueIsAcceptable(final String valueString, final LocalizableMessageBuilder invalidReason) {
        for (final V allowedValue : allowedValues) {
            if (allowedValue.toString().equalsIgnoreCase(valueString)) {
                return true;
            }
        }
        invalidReason.append(ERR_MCARG_VALUE_NOT_ALLOWED.get(longIdentifier, valueString));

        return false;
    }
}
