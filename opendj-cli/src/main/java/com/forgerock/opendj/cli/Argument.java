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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.util.Reject;

/**
 * This class defines a generic argument that may be used in the argument list
 * for an application. This is an abstract class that must be subclassed in
 * order to provide specific functionality.
 */
public abstract class Argument implements DocDescriptionSupplement {

    /**
     * An abstract base class to build a generic {@link Argument}.
     *
     * @param <B>
     *         The concrete {@link ArgumentBuilder} subclass.
     * @param <T>
     *         The default value type of the {@link Argument}.
     * @param <A>
     *         The concrete {@link Argument} type to build.
     */
    static abstract class ArgumentBuilder<B extends ArgumentBuilder<B, T, A>, T, A extends Argument> {
        T defaultValue;
        LocalizableMessage description;
        LocalizableMessage docDescriptionSupplement;
        boolean hidden;
        final String longIdentifier;
        boolean multiValued;
        boolean needsValue = true;
        boolean required;
        Character shortIdentifier;
        LocalizableMessage valuePlaceholder;

        ArgumentBuilder(final String longIdentifier) {
            Reject.ifNull(longIdentifier, "An argument must have a long identifier");
            this.longIdentifier = longIdentifier;
        }

        abstract B getThis();

        /**
         * Build the argument.
         *
         * @return The argument built.
         * @throws ArgumentException
         *         If there is a problem with any of the parameters used to
         *         create this argument.
         */
        public abstract A buildArgument() throws ArgumentException;

        /**
         * Build the argument and add it to the provided {@link ArgumentParser}.
         *
         * @param parser
         *         The argument parser.
         * @return The argument built.
         * @throws ArgumentException
         *         If there is a problem with any of the parameters used to
         *         create this argument.
         */
        public A buildAndAddToParser(final ArgumentParser parser) throws ArgumentException {
            final A arg = buildArgument();
            parser.addArgument(arg);
            return arg;
        }

        /**
         * Build the argument and add it to the provided {@link SubCommand}.
         *
         * @param subCommand
         *         The sub command.
         * @return The argument built.
         * @throws ArgumentException
         *         If there is a problem with any of the parameters used to
         *         create this argument.
         */
        public A buildAndAddToSubCommand(final SubCommand subCommand) throws ArgumentException {
            final A arg = buildArgument();
            subCommand.addArgument(arg);
            return arg;
        }

        /**
         * Sets this argument default value.
         *
         * @param defaultValue
         *         The default value.
         * @return This builder.
         */
        public B defaultValue(final T defaultValue) {
            this.defaultValue = defaultValue;
            return getThis();
        }

        /**
         * Sets this argument description.
         *
         * @param description
         *         The localized description.
         * @return This builder.
         */
        public B description(final LocalizableMessage description) {
            this.description = description;
            return getThis();
        }

        /**
         * Sets a supplement to the description intended for use in generated reference documentation.
         *
         * @param docDescriptionSupplement
         *         The supplement to the description for use in generated reference documentation.
         * @return This builder.
         */
        public B docDescriptionSupplement(final LocalizableMessage docDescriptionSupplement) {
            this.docDescriptionSupplement = docDescriptionSupplement;
            return getThis();
        }

        /**
         * Specifies that this argument is hidden.
         *
         * @return This builder.
         */
        public B hidden() {
            this.hidden = true;
            return getThis();
        }

        /**
         * Specifies that this argument may have multiple values.
         *
         * @return This builder.
         */
        public B multiValued() {
            this.multiValued = true;
            return getThis();
        }

        /**
         * Specifies that this argument is required.
         *
         * @return This builder.
         */
        public B required() {
            this.required = true;
            return getThis();
        }

        /**
         * Sets this argument single-character identifier.
         *
         * @param shortIdentifier
         *         The single-character identifier.
         * @return This builder.
         */
        public B shortIdentifier(final Character shortIdentifier) {
            this.shortIdentifier = shortIdentifier;
            return getThis();
        }

        /**
         * Sets this argument value placeholder, which will be used in usage information.
         *
         * @param valuePlaceholder
         *         The localized value placeholder.
         * @return This builder.
         */
        public B valuePlaceholder(final LocalizableMessage valuePlaceholder) {
            this.valuePlaceholder = valuePlaceholder;
            return getThis();
        }
    }

    /** The long identifier for this argument. */
    final String longIdentifier;

    /** The single-character identifier for this argument. */
    private final Character shortIdentifier;
    /** The unique ID of the description for this argument. */
    private final LocalizableMessage description;
    /** Indicates whether this argument should be hidden in the usage information. */
    private final boolean isHidden;
    /** Indicates whether this argument may be specified more than once for multiple values. */
    private final boolean isMultiValued;
    /** Indicates whether this argument is required to have a value. */
    private final boolean isRequired;
    /** Indicates whether this argument requires a value. */
    private final boolean needsValue;
    /** The default value for the argument if none other is provided. */
    private final String defaultValue;
    /** The value placeholder for this argument, which will be used in usage information. */
    private final LocalizableMessage valuePlaceholder;

    /** The set of values for this argument. */
    private final LinkedList<String> values = new LinkedList<>();

    /** Indicates whether this argument was provided in the set of command-line arguments. */
    private boolean isPresent;

    /**
     * Indicates whether this argument was provided in the set of
     * properties found in a properties file.
     */
    private boolean isValueSetByProperty;

    <B extends ArgumentBuilder<B, T, A>, T, A extends Argument> Argument(final ArgumentBuilder<B, T, A> builder)
            throws ArgumentException {
        this.shortIdentifier = builder.shortIdentifier;
        this.longIdentifier = builder.longIdentifier;
        this.isRequired = builder.required;
        this.isMultiValued = builder.multiValued;
        this.needsValue = builder.needsValue;
        this.valuePlaceholder = builder.valuePlaceholder;
        this.defaultValue = builder.defaultValue != null ? String.valueOf(builder.defaultValue) : null;
        this.description = builder.description;
        this.isHidden = builder.hidden;
        this.docDescriptionSupplement = builder.docDescriptionSupplement;

        if (needsValue && valuePlaceholder == null) {
            throw new ArgumentException(ERR_ARG_NO_VALUE_PLACEHOLDER.get(longIdentifier));
        }

        isPresent = false;
    }

    /**
     * Adds a value to the set of values for this argument. This should only be
     * called if the value is allowed by the <CODE>valueIsAcceptable</CODE>
     * method.
     *
     * @param valueString
     *            The string representation of the value to add to this
     *            argument.
     */
    public void addValue(final String valueString) {
        if (!isMultiValued) {
            clearValues();
        }
        values.add(valueString);
    }

    /**
     * Clears the set of values assigned to this argument.
     */
    public void clearValues() {
        values.clear();
    }

    /**
     * Retrieves the default value that will be used for this argument if it is
     * not specified on the command line and it is not set from a properties
     * file.
     *
     * @return The default value that will be used for this argument if it is
     *         not specified on the command line and it is not set from a
     *         properties file, or <CODE>null</CODE> if there is no default
     *         value.
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Retrieves the human-readable description for this argument.
     *
     * @return The human-readable description for this argument.
     */
    public LocalizableMessage getDescription() {
        return description != null ? description : LocalizableMessage.EMPTY;
    }

    /** A supplement to the description intended for use in generated reference documentation. */
    private LocalizableMessage docDescriptionSupplement;

    @Override
    public LocalizableMessage getDocDescriptionSupplement() {
        return docDescriptionSupplement != null ? docDescriptionSupplement : LocalizableMessage.EMPTY;
    }

    /**
     * Retrieves the value of this argument as an integer.
     *
     * @return The value of this argument as an integer.
     * @throws ArgumentException
     *             If there are multiple values, or the value cannot be parsed
     *             as an integer.
     */
    public int getIntValue() throws ArgumentException {
        if (values.isEmpty()) {
            throw new ArgumentException(ERR_ARG_NO_INT_VALUE.get(longIdentifier));
        }

        final Iterator<String> iterator = values.iterator();
        final String valueString = iterator.next();
        if (iterator.hasNext()) {
            throw new ArgumentException(ERR_ARG_INT_MULTIPLE_VALUES.get(longIdentifier));
        }

        try {
            return Integer.parseInt(valueString);
        } catch (final Exception e) {
            throw new ArgumentException(ERR_ARG_CANNOT_DECODE_AS_INT.get(valueString, longIdentifier), e);
        }
    }

    /**
     * Retrieves the long (multi-character) identifier that may be used to
     * specify the value of this argument.
     *
     * @return The long (multi-character) identifier that may be used to specify
     *         the value of this argument.
     */
    public String getLongIdentifier() {
        return longIdentifier;
    }

    /**
     * Retrieves the single-character identifier that may be used to specify the
     * value of this argument.
     *
     * @return The single-character identifier that may be used to specify the
     *         value of this argument, or <CODE>null</CODE> if there is none.
     */
    public Character getShortIdentifier() {
        return shortIdentifier;
    }

    /**
     * Retrieves the string vale for this argument. If it has multiple values,
     * then the first will be returned. If it does not have any values, then the
     * default value will be returned.
     *
     * @return The string value for this argument, or <CODE>null</CODE> if there
     *         are no values and no default value has been given.
     */
    public String getValue() {
        return !values.isEmpty() ? values.getFirst() : defaultValue;
    }

    /**
     * Retrieves the value placeholder that will be displayed for this argument
     * in the generated usage information.
     *
     * @return The value placeholder that will be displayed for this argument in
     *         the generated usage information, or <CODE>null</CODE> if there is none.
     */
    public LocalizableMessage getValuePlaceholder() {
        return valuePlaceholder;
    }

    /**
     * Retrieves the set of string values for this argument.
     *
     * @return The set of string values for this argument.
     */
    public List<String> getValues() {
        return values;
    }

    /**
     * Indicates whether this argument has at least one value.
     *
     * @return <CODE>true</CODE> if this argument has at least one value, or
     *         <CODE>false</CODE> if it does not have any values.
     */
    public boolean hasValue() {
        return !values.isEmpty();
    }

    /**
     * Indicates whether this argument should be hidden from the usage
     * information.
     *
     * @return <CODE>true</CODE> if this argument should be hidden from the
     *         usage information, or <CODE>false</CODE> if not.
     */
    public boolean isHidden() {
        return isHidden;
    }

    /**
     * Indicates whether this argument may be provided more than once on the
     * command line to specify multiple values.
     *
     * @return <CODE>true</CODE> if this argument may be provided more than once
     *         on the command line to specify multiple values, or
     *         <CODE>false</CODE> if it may have at most one value.
     */
    public boolean isMultiValued() {
        return isMultiValued;
    }

    /**
     * Indicates whether this argument is present in the parsed set of
     * command-line arguments.
     *
     * @return <CODE>true</CODE> if this argument is present in the parsed set
     *         of command-line arguments, or <CODE>false</CODE> if not.
     */
    public boolean isPresent() {
        return isPresent;
    }

    /**
     * Indicates whether this argument is required to have at least one value.
     *
     * @return <CODE>true</CODE> if this argument is required to have at least
     *         one value, or <CODE>false</CODE> if it does not need to have a value.
     */
    public boolean isRequired() {
        return isRequired;
    }

    /**
     * Indicates whether this argument was provided in the set of properties
     * found is a properties file.
     *
     * @return <CODE>true</CODE> if this argument was provided in the set of
     *         properties found is a properties file, or <CODE>false</CODE> if not.
     */
    public boolean isValueSetByProperty() {
        return isValueSetByProperty;
    }

    /**
     * Indicates whether a value must be provided with this argument if it is
     * present.
     *
     * @return <CODE>true</CODE> if a value must be provided with the argument
     *         if it is present, or <CODE>false</CODE> if the argument does not
     *         take a value and the presence of the argument identifier itself
     *         is sufficient to convey the necessary information.
     */
    public boolean needsValue() {
        return needsValue;
    }

    /**
     * Specifies whether this argument is present in the parsed set of
     * command-line arguments.
     *
     * @param isPresent
     *            Indicates whether this argument is present in the set of
     *            command-line arguments.
     */
    public void setPresent(final boolean isPresent) {
        this.isPresent = isPresent;
    }

    void valueSetByProperty() {
        isValueSetByProperty = true;
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
    public abstract boolean valueIsAcceptable(String valueString, LocalizableMessageBuilder invalidReason);

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("(");
        if (longIdentifier != null) {
            sb.append("longID=");
            sb.append(longIdentifier);
        }
        if (shortIdentifier != null) {
            if (longIdentifier != null) {
                sb.append(", ");
            }
            sb.append("shortID=");
            sb.append(shortIdentifier);
        }
        sb.append(", values=").append(values);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(final Object arg) {
        return this == arg || (arg instanceof Argument && ((Argument) arg).longIdentifier.equals(this.longIdentifier));
    }

    @Override
    public int hashCode() {
        return longIdentifier.hashCode();
    }
}
