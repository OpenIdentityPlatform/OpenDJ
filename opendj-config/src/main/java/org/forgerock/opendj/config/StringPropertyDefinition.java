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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.forgerock.i18n.LocalizableMessage;

/** String property definition. */
public final class StringPropertyDefinition extends PropertyDefinition<String> {

    /** An interface for incrementally constructing string property definitions. */
    public static final class Builder extends AbstractBuilder<String, StringPropertyDefinition> {

        /** Flag indicating whether values of this property are case-insensitive. */
        private boolean isCaseInsensitive = true;

        /** Optional pattern which values of this property must match. */
        private Pattern pattern;

        /** Pattern usage which provides a user-friendly summary of the pattern if present. */
        private String patternUsage;

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /**
         * Set a flag indicating whether values of this property are
         * case-insensitive.
         *
         * @param value
         *            <code>true</code> if values are case-insensitive, or
         *            <code>false</code> otherwise.
         */
        public final void setCaseInsensitive(boolean value) {
            isCaseInsensitive = value;
        }

        /**
         * Set the regular expression pattern which values of this property must
         * match. By default there is no pattern defined.
         *
         * @param pattern
         *            The regular expression pattern string, or
         *            <code>null</code> if there is no pattern.
         * @param patternUsage
         *            A user-friendly usage string representing the pattern
         *            which can be used in error messages and help (e.g. for
         *            patterns which match a host/port combination, the usage
         *            string "HOST:PORT" would be appropriate).
         * @throws PatternSyntaxException
         *             If the provided regular expression pattern has an invalid
         *             syntax.
         */
        public final void setPattern(String pattern, String patternUsage) {
            if (pattern == null) {
                this.pattern = null;
                this.patternUsage = null;
            } else {
                this.pattern = Pattern.compile(pattern);
                this.patternUsage = patternUsage;
            }
        }

        @Override
        protected StringPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<String> defaultBehavior) {
            return new StringPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior,
                isCaseInsensitive, pattern, patternUsage);
        }

    }

    /**
     * Create a string property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new string property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Flag indicating whether values of this property are case-insensitive. */
    private final boolean isCaseInsensitive;

    /** Optional pattern which values of this property must match. */
    private final Pattern pattern;

    /** Pattern usage which provides a user-friendly summary of the pattern if present. */
    private final String patternUsage;

    /** Private constructor. */
    private StringPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<String> defaultBehavior, boolean isCaseInsensitive, Pattern pattern,
        String patternUsage) {
        super(d, String.class, propertyName, options, adminAction, defaultBehavior);
        this.isCaseInsensitive = isCaseInsensitive;
        this.pattern = pattern;
        this.patternUsage = patternUsage;
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitString(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, String value, P p) {
        return v.visitString(this, value, p);
    }

    @Override
    public String decodeValue(String value) {
        Reject.ifNull(value);

        try {
            validateValue(value);
        } catch (PropertyException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }

        return value;
    }

    /**
     * Gets the optional regular expression pattern which values of this
     * property must match.
     *
     * @return Returns the optional regular expression pattern which values of
     *         this property must match, or <code>null</code> if there is no
     *         pattern.
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * Gets the pattern synopsis of this string property definition in the
     * default locale.
     *
     * @return Returns the pattern synopsis of this string property definition
     *         in the default locale, or <code>null</code> if there is no
     *         pattern synopsis (which is the case when there is no pattern
     *         matching defined for this string property definition).
     */
    public LocalizableMessage getPatternSynopsis() {
        return getPatternSynopsis(Locale.getDefault());
    }

    /**
     * Gets the optional pattern synopsis of this string property definition in
     * the specified locale.
     *
     * @param locale
     *            The locale.
     * @return Returns the pattern synopsis of this string property definition
     *         in the specified locale, or <code>null</code> if there is no
     *         pattern synopsis (which is the case when there is no pattern
     *         matching defined for this string property definition).
     */
    public LocalizableMessage getPatternSynopsis(Locale locale) {
        ManagedObjectDefinitionI18NResource resource = ManagedObjectDefinitionI18NResource.getInstance();
        String property = "property." + getName() + ".syntax.string.pattern.synopsis";
        try {
            return resource.getMessage(getManagedObjectDefinition(), property, locale);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Gets a user-friendly usage string representing the pattern which can be
     * used in error messages and help (e.g. for patterns which match a
     * host/port combination, the usage string "HOST:PORT" would be
     * appropriate).
     *
     * @return Returns the user-friendly pattern usage string, or
     *         <code>null</code> if there is no pattern.
     */
    public String getPatternUsage() {
        return patternUsage;
    }

    /**
     * Query whether values of this property are case-insensitive.
     *
     * @return Returns <code>true</code> if values are case-insensitive, or
     *         <code>false</code> otherwise.
     */
    public boolean isCaseInsensitive() {
        return isCaseInsensitive;
    }

    @Override
    public String normalizeValue(String value) {
        Reject.ifNull(value);

        if (isCaseInsensitive()) {
            return value.trim().toLowerCase();
        } else {
            return value.trim();
        }
    }

    @Override
    public void validateValue(String value) {
        Reject.ifNull(value);

        if (pattern != null) {
            Matcher matcher = pattern.matcher(value);
            if (!matcher.matches()) {
                throw PropertyException.illegalPropertyValueException(this, value);
            }
        }
    }
}
