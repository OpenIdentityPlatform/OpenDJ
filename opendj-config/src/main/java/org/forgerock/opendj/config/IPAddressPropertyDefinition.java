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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;

/** IP address property definition. */
public final class IPAddressPropertyDefinition extends PropertyDefinition<InetAddress> {

    /** An interface for incrementally constructing IP address property definitions. */
    public static final class Builder extends AbstractBuilder<InetAddress, IPAddressPropertyDefinition> {

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        @Override
        protected IPAddressPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<InetAddress> defaultBehavior) {
            return new IPAddressPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior);
        }

    }

    /**
     * Create a IP address property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new IP address property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Private constructor. */
    private IPAddressPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<InetAddress> defaultBehavior) {
        super(d, InetAddress.class, propertyName, options, adminAction, defaultBehavior);
    }

    @Override
    public void validateValue(InetAddress value) {
        Reject.ifNull(value);

        // No additional validation required.
    }

    @Override
    public InetAddress decodeValue(String value) {
        Reject.ifNull(value);

        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            // TODO: it would be nice to throw the cause.
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    @Override
    public String encodeValue(InetAddress value) {
        // We should return the host name if it is available, or the IP
        // address if not.

        // Unforunately, there is no InetAddress method for doing this, so
        // we have to resort to hacking at the toString() encoding.
        String s = value.toString();
        int i = s.indexOf('/');
        if (i > 0) {
            // Host address is before the forward slash.
            return s.substring(0, i);
        } else {
            return value.getHostAddress();
        }
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitIPAddress(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, InetAddress value, P p) {
        return v.visitIPAddress(this, value, p);
    }

    @Override
    public int compare(InetAddress o1, InetAddress o2) {
        return o1.getHostAddress().compareTo(o2.getHostAddress());
    }
}
