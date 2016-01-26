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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2014-2016 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class defines an argument type that will only accept integer values, and
 * potentially only those in a given range.
 */
public final class IntegerArgument extends Argument {

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * {@link IntegerArgument}.
     *
     * @param name
     *         The generic name that will be used to refer to this argument.
     * @return A builder to continue building the {@link IntegerArgument}.
     */
    public static Builder builder(final String name) {
        return new Builder(name);
    }

    /** A fluent API for incrementally constructing {@link IntegerArgument}. */
    public static final class Builder extends ArgumentBuilder<Builder, Integer, IntegerArgument> {
        private int lowerBound = Integer.MIN_VALUE;
        private int upperBound = Integer.MAX_VALUE;

        private Builder(final String name) {
            super(name);
        }

        @Override
        Builder getThis() {
            return this;
        }

        /**
         * Sets the lower bound of this {@link IntegerArgument}.
         *
         * @param lowerBound
         *         The lower bound value.
         * @return This builder.
         */
        public Builder lowerBound(final int lowerBound) {
            this.lowerBound = lowerBound;
            return getThis();
        }

        /**
         * Sets the range of this {@link IntegerArgument}.
         *
         * @param lowerBound
         *          The range lower bound value.
         * @param upperBound
         *          The range upper bound value.
         * @return This builder.
         */
        public Builder range(final int lowerBound, final int upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            return getThis();
        }

        @Override
        public IntegerArgument buildArgument() throws ArgumentException {
            return new IntegerArgument(this, lowerBound, upperBound);
        }
    }

    /** The lower bound that will be enforced for this argument. */
    private final int lowerBound;
    /** The upper bound that will be enforced for this argument. */
    private final int upperBound;

    private IntegerArgument(final Builder builder, final int lowerBound, final int upperBound)
            throws ArgumentException {
        super(builder);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;

        if (lowerBound > upperBound) {
            final LocalizableMessage message =
                    ERR_INTARG_LOWER_BOUND_ABOVE_UPPER_BOUND.get(builder.longIdentifier, lowerBound, upperBound);
            throw new ArgumentException(message);
        }
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
        try {
            final int intValue = Integer.parseInt(valueString);
            if (intValue < lowerBound) {
                invalidReason.append(ERR_INTARG_VALUE_BELOW_LOWER_BOUND.get(longIdentifier, intValue, lowerBound));
                return false;
            }

            if (intValue > upperBound) {
                invalidReason.append(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get(longIdentifier, intValue, upperBound));
                return false;
            }

            return true;
        } catch (final NumberFormatException e) {
            invalidReason.append(ERR_ARG_CANNOT_DECODE_AS_INT.get(valueString, longIdentifier));
            return false;
        }
    }
}
