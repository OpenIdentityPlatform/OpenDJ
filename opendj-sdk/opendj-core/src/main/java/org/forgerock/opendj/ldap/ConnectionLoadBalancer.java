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
 *      Copyright 2016 ForgeRock AS.
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
