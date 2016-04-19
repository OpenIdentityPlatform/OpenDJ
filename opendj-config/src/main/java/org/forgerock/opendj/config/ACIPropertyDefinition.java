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

import java.util.EnumSet;
import java.util.regex.Pattern;

import org.forgerock.util.Reject;

/** ACI property definition. */
public final class ACIPropertyDefinition extends PropertyDefinition<String> {

    /** An interface for incrementally constructing ACI property definitions. */
    public static final class Builder extends AbstractBuilder<String, ACIPropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

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

    @Override
    public void validateValue(String value) {
        Reject.ifNull(value);

        // No additional validation required.
    }

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

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitACI(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, String value, P p) {
        return v.visitACI(this, value, p);
    }

    @Override
    public int compare(String o1, String o2) {
        return o1.compareTo(o2);
    }
}
