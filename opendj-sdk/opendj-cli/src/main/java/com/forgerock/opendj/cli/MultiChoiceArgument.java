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

import static com.forgerock.opendj.cli.CliMessages.ERR_MCARG_VALUE_NOT_ALLOWED;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class defines an argument type that will only accept one or more of a
 * specific set of string values.
 *
 * @param <T>
 *            The type of values returned by this argument.
 */
public final class MultiChoiceArgument<T> extends Argument {
    /**
     * Indicates whether argument values should be treated in a
     * case-sensitive manner.
     */
    private final boolean caseSensitive;

    /** The set of values that will be allowed for use with this argument. */
    private final Collection<T> allowedValues;

    /**
     * Creates a new string argument with the provided information.
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
     * @param isRequired
     *            Indicates whether this argument must be specified on the
     *            command line.
     * @param isMultiValued
     *            Indicates whether this argument may be specified more than
     *            once to provide multiple values.
     * @param needsValue
     *            Indicates whether this argument requires a value.
     * @param valuePlaceholder
     *            The placeholder for the argument value that will be displayed
     *            in usage information, or <CODE>null</CODE> if this argument
     *            does not require a value.
     * @param defaultValue
     *            The default value that should be used for this argument if
     *            none is provided in a properties file or on the command line.
     *            This may be <CODE>null</CODE> if there is no generic default.
     * @param propertyName
     *            The name of the property in a property file that may be used
     *            to override the default value but will be overridden by a
     *            command-line argument.
     * @param allowedValues
     *            The set of values that are allowed for use for this argument.
     *            If they are not to be treated in a case-sensitive value then
     *            they should all be formatted in lowercase.
     * @param caseSensitive
     *            Indicates whether the set of allowed values should be treated
     *            in a case-sensitive manner.
     * @param description
     *            LocalizableMessage for the description of this argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to
     *             create this argument.
     */
    public MultiChoiceArgument(final String name, final Character shortIdentifier,
            final String longIdentifier, final boolean isRequired, final boolean isMultiValued,
            final boolean needsValue, final LocalizableMessage valuePlaceholder,
            final String defaultValue, final String propertyName,
            final Collection<T> allowedValues, final boolean caseSensitive,
            final LocalizableMessage description) throws ArgumentException {
        super(name, shortIdentifier, longIdentifier, isRequired, isMultiValued, needsValue,
                valuePlaceholder, defaultValue, propertyName, description);

        this.allowedValues = allowedValues;
        this.caseSensitive = caseSensitive;
    }

    /**
     * Creates a new string argument with the provided information.
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
     * @param isRequired
     *            Indicates whether this argument must be specified on the
     *            command line.
     * @param needsValue
     *            Indicates whether this argument requires a value.
     * @param valuePlaceholder
     *            The placeholder for the argument value that will be displayed
     *            in usage information, or <CODE>null</CODE> if this argument
     *            does not require a value.
     * @param allowedValues
     *            The set of values that are allowed for use for this argument.
     *            If they are not to be treated in a case-sensitive value then
     *            they should all be formatted in lowercase.
     * @param caseSensitive
     *            Indicates whether the set of allowed values should be treated
     *            in a case-sensitive manner.
     * @param description
     *            LocalizableMessage for the description of this argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to
     *             create this argument.
     */
    public MultiChoiceArgument(final String name, final Character shortIdentifier,
            final String longIdentifier, final boolean isRequired, final boolean needsValue,
            final LocalizableMessage valuePlaceholder, final Collection<T> allowedValues,
            final boolean caseSensitive, final LocalizableMessage description)
            throws ArgumentException {
        super(name, shortIdentifier, longIdentifier, isRequired, false, needsValue,
                valuePlaceholder, null, null, description);

        this.allowedValues = allowedValues;
        this.caseSensitive = caseSensitive;
    }

    /**
     * Retrieves the set of allowed values for this argument. The contents of
     * this set must not be altered by the caller.
     *
     * @return The set of allowed values for this argument.
     */
    public Collection<T> getAllowedValues() {
        return allowedValues;
    }

    /**
     * Retrieves the string vale for this argument. If it has multiple values,
     * then the first will be returned. If it does not have any values, then the
     * default value will be returned.
     *
     * @return The string value for this argument, or <CODE>null</CODE> if there
     *         are no values and no default value has been given.
     * @throws ArgumentException
     *             The value cannot be parsed.
     */
    public T getTypedValue() throws ArgumentException {
        final String v = super.getValue();
        if (v == null) {
            return null;
        }
        for (final T o : allowedValues) {
            if ((caseSensitive && o.toString().equals(v)) || o.toString().equalsIgnoreCase(v)) {
                return o;
            }
        }
        // TODO: Some message
        throw new ArgumentException(null);
    }

    /**
     * Indicates whether the set of allowed values for this argument should be
     * treated in a case-sensitive manner.
     *
     * @return <CODE>true</CODE> if the values are to be treated in a
     *         case-sensitive manner, or <CODE>false</CODE> if not.
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * Specifies the default value that will be used for this argument if it is
     * not specified on the command line and it is not set from a properties
     * file.
     *
     * @param defaultValue
     *            The default value that will be used for this argument if it is
     *            not specified on the command line and it is not set from a
     *            properties file.
     */
    public void setDefaultValue(final T defaultValue) {
        super.setDefaultValue(defaultValue.toString());
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
        for (final T o : allowedValues) {
            if ((caseSensitive && o.toString().equals(valueString))
                    || o.toString().equalsIgnoreCase(valueString)) {
                return true;
            }
        }
        invalidReason.append(ERR_MCARG_VALUE_NOT_ALLOWED.get(getName(), valueString));

        return false;
    }
}
