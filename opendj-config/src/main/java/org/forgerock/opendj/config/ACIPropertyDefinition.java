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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import java.util.EnumSet;
import java.util.regex.Pattern;

import org.forgerock.util.Reject;

/**
 * ACI property definition.
 */
public final class ACIPropertyDefinition extends PropertyDefinition<String> {

    /**
     * An interface for incrementally constructing ACI property definitions.
     */
    public static final class Builder extends AbstractBuilder<String, ACIPropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /** {@inheritDoc} */
        @Override
        protected ACIPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
                String propertyName, EnumSet<PropertyOption> options,
                AdministratorAction adminAction, DefaultBehaviorProvider<String> defaultBehavior) {
            return new ACIPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior);
        }
    }

    /**
     * Create a ACI property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new ACI property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /**
     * Pattern used for performing basic ACI syntax validation. Taken from the
     * Aci class in the server.
     */
    private static final Pattern ACI_REGEX =
            Pattern.compile("^\\s*(\\(\\s*(\\w+)\\s*(!?=)\\s*\"([^\"]+)\"\\s*\\)\\s*)*\\s*\\"
                    + "(\\s*(?i)version(?-i)\\s*(\\d\\.\\d)\\s*;\\s*(?i)acl(?-i)\\s*\"([^\"]*)"
                    + "\"\\s*;\\s*\\s*(\\w+)\\s*\\(([^()]+)\\)\\s*(.+?\"[)]*)\\s*;\\s*\\s*\\)\\s*$");

    /** Private constructor. */
    private ACIPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
            EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<String> defaultBehavior) {
        super(d, String.class, propertyName, options, adminAction, defaultBehavior);
    }

    /** {@inheritDoc} */
    @Override
    public void validateValue(String value) {
        Reject.ifNull(value);

        // No additional validation required.
    }

    /** {@inheritDoc} */
    @Override
    public String decodeValue(String value) {
        Reject.ifNull(value);

        /*
         * We don't have access to the ACI class from the server so do
         * best-effort using regular expressions. TODO: is it worth improving on
         * this? We could use reflection to get the appropriate parser which
         * would allow us to use full validation in OpenDJ whilst remaining
         * decoupled in other applications.
         */
        if (ACI_REGEX.matcher(value).matches()) {
            return value;
        }
        throw PropertyException.illegalPropertyValueException(this, value);
    }

    /** {@inheritDoc} */
    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitACI(this, p);
    }

    /** {@inheritDoc} */
    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, String value, P p) {
        return v.visitACI(this, value, p);
    }

    /** {@inheritDoc} */
    @Override
    public int compare(String o1, String o2) {
        return o1.compareTo(o2);
    }
}
