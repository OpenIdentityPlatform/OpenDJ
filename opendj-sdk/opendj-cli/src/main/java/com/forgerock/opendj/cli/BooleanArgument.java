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

import static com.forgerock.opendj.cli.CliMessages.ERR_BOOLEANARG_NO_VALUE_ALLOWED;

import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class defines an argument type that will be used to represent Boolean
 * values. These arguments will never take values from the command line but and
 * will never be required. If the argument is provided, then it will be
 * considered true, and if not then it will be considered false. As such, the
 * default value will always be "false".
 */
public final class BooleanArgument extends Argument {

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * {@link BooleanArgument}.
     *
     * @param longIdentifier
     *         The long identifier that will be used to refer to this argument.
     * @return A builder to continue building the {@link BooleanArgument}.
     */
    public static Builder builder(final String longIdentifier) {
        return new Builder(longIdentifier);
    }

    /** A fluent API for incrementally constructing {@link BooleanArgument}. */
    public static final class Builder extends ArgumentBuilder<Builder, Boolean, BooleanArgument> {
        private Builder(final String longIdentifier) {
            super(longIdentifier);
            this.needsValue = false;
            this.defaultValue = false;
        }

        @Override
        Builder getThis() {
            return this;
        }

        @Override
        public BooleanArgument buildArgument() throws ArgumentException {
            return new BooleanArgument(this);
        }
    }

    private BooleanArgument(final Builder builder) throws ArgumentException {
        super(builder);
    }

    @Override
    public final void addValue(final String valueString) {
        if (valueString != null) {
            clearValues();
            super.addValue(valueString);
            super.setPresent(Boolean.valueOf(valueString));
        }
    }

    @Override
    public final void setPresent(final boolean isPresent) {
        addValue(String.valueOf(isPresent));
    }

    @Override
    public boolean valueIsAcceptable(final String valueString, final LocalizableMessageBuilder invalidReason) {
        // This argument type should never have a value, so any value
        // provided will be unacceptable.
        invalidReason.append(ERR_BOOLEANARG_NO_VALUE_ALLOWED.get(longIdentifier));

        return false;
    }
}
