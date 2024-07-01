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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import org.forgerock.util.Reject;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** Boolean property definition. */
public final class BooleanPropertyDefinition extends PropertyDefinition<Boolean> {

    /**
     * Mapping used for parsing boolean values. This mapping is more flexible
     * than the standard boolean string parser and supports common true/false
     * synonyms used in configuration.
     */
    private static final Map<String, Boolean> VALUE_MAP = new HashMap<>();
    static {
        // We could have more possibilities but decided against in issue 1960.
        VALUE_MAP.put("false", Boolean.FALSE);
        VALUE_MAP.put("true", Boolean.TRUE);
    }

    /** An interface for incrementally constructing boolean property definitions. */
    public static final class Builder extends AbstractBuilder<Boolean, BooleanPropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        @Override
        protected BooleanPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<Boolean> defaultBehavior) {
            return new BooleanPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior);
        }

    }

    /**
     * Create a boolean property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new boolean property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Private constructor. */
    private BooleanPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<Boolean> defaultBehavior) {
        super(d, Boolean.class, propertyName, options, adminAction, defaultBehavior);
    }

    @Override
    public void validateValue(Boolean value) {
        Reject.ifNull(value);

        // No additional validation required.
    }

    @Override
    public Boolean decodeValue(String value) {
        Reject.ifNull(value);

        String nvalue = value.trim().toLowerCase();
        Boolean b = VALUE_MAP.get(nvalue);

        if (b == null) {
            throw PropertyException.illegalPropertyValueException(this, value);
        } else {
            return b;
        }
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitBoolean(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, Boolean value, P p) {
        return v.visitBoolean(this, value, p);
    }

    @Override
    public int compare(Boolean o1, Boolean o2) {
        return o1.compareTo(o2);
    }
}
