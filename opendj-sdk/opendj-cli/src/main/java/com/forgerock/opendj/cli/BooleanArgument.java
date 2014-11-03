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
 *      Portions copyright 2014 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.ERR_BOOLEANARG_NO_VALUE_ALLOWED;

import org.forgerock.i18n.LocalizableMessage;
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
     * Creates a new Boolean argument with the provided information.
     *
     * @param name
     *            The generic name that should be used to refer to this
     *            argument.
     * @param shortIdentifier
     *            The single-character identifier for this argument, or
     *            <CODE>null</CODE> if there is none.
     * @param longIdentifier
     *            The long identifier for this argument, or <CODE>null</CODE> if
     *            there is none.
     * @param description
     *            LocalizableMessage for the description of this argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to
     *             create this argument.
     */
    public BooleanArgument(final String name, final Character shortIdentifier,
            final String longIdentifier, final LocalizableMessage description)
            throws ArgumentException {
        super(name, shortIdentifier, longIdentifier, false, false, false, null, String
                .valueOf(false), null, description);
    }

    /** {@inheritDoc} */
    @Override
    public final void addValue(final String valueString) {
        if (valueString != null) {
            clearValues();
            super.addValue(valueString);
            super.setPresent(Boolean.valueOf(valueString));
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void setPresent(final boolean isPresent) {
        addValue(String.valueOf(isPresent));
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
    public boolean valueIsAcceptable(final String valueString,
            final LocalizableMessageBuilder invalidReason) {
        // This argument type should never have a value, so any value
        // provided will be unacceptable.
        invalidReason.append(ERR_BOOLEANARG_NO_VALUE_ALLOWED.get(getName()));

        return false;
    }
}
