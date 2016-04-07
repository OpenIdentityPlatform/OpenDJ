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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.forgerock.util.promise.Promises.newExceptionPromise;

import java.util.Collection;

import org.forgerock.util.Options;
import org.forgerock.util.promise.Promise;

/**
 * An abstract connection based load balancer. Load balancing is performed when the application attempts to obtain a
 * connection.
 * <p>
 * Implementations should override the method {@code getInitialConnectionFactoryIndex()} in order to provide the policy
 * for selecting the first connection factory to use for each connection request.
 */
abstract class ConnectionLoadBalancer extends LoadBalancer {
    ConnectionLoadBalancer(final String loadBalancerName,
                           final Collection<? extends ConnectionFactory> factories,
                           final Options options) {
        super(loadBalancerName, factories, options);
    }

    @Override
    public final Connection getConnection() throws LdapException {
        return getMonitoredConnectionFactory(getInitialConnectionFactoryIndex()).getConnection();
    }

    @Override
    public final Promise<Connection, LdapException> getConnectionAsync() {
        try {
            return getMonitoredConnectionFactory(getInitialConnectionFactoryIndex()).getConnectionAsync();
        } catch (final LdapException e) {
            return newExceptionPromise(e);
        }
    }

    /**
     * Returns the index of the first connection factory which should be used in order to satisfy the next connection
     * request.
     *
     * @return The index of the first connection factory which should be used in order to satisfy the next connection
     * request.
     */
    abstract int getInitialConnectionFactoryIndex();
}
