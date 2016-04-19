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
 * Portions copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AddressMask;
import org.forgerock.util.Reject;

import java.util.EnumSet;

/** IP address mask property definition. */
public final class IPAddressMaskPropertyDefinition extends PropertyDefinition<AddressMask> {

    /** An interface for incrementally constructing IP address mask property definitions. */
    public static final class Builder extends AbstractBuilder<AddressMask, IPAddressMaskPropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        @Override
        protected IPAddressMaskPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<AddressMask> defaultBehavior) {
            return new IPAddressMaskPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior);
        }

    }

    /**
     * Create a IP address mask property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new IP address mask property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Private constructor. */
    private IPAddressMaskPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<AddressMask> defaultBehavior) {
        super(d, AddressMask.class, propertyName, options, adminAction, defaultBehavior);
    }

    @Override
    public void validateValue(AddressMask value) {
        Reject.ifNull(value);

        // No additional validation required.
    }

    @Override
    public AddressMask decodeValue(String value) {
        Reject.ifNull(value);

        try {
            return AddressMask.valueOf(value);
        } catch (LocalizedIllegalArgumentException e) {
            // TODO: it would be nice to throw the cause.
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitIPAddressMask(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, AddressMask value, P p) {
        return v.visitIPAddressMask(this, value, p);
    }

    @Override
    public int compare(AddressMask o1, AddressMask o2) {
        return o1.toString().compareTo(o2.toString());
    }
}
