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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

import static org.forgerock.util.promise.Promises.*;

/**
 * A load balancing connection factory allocates connections using the provided
 * algorithm.
 */
final class LoadBalancer implements ConnectionFactory {
    private final LoadBalancingAlgorithm algorithm;

    LoadBalancer(final LoadBalancingAlgorithm algorithm) {
        Reject.ifNull(algorithm);
        this.algorithm = algorithm;
    }

    @Override
    public void close() {
        // Delegate to the algorithm.
        algorithm.close();
    }

    @Override
    public Connection getConnection() throws LdapException {
        return algorithm.getConnectionFactory().getConnection();
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        final ConnectionFactory factory;

        try {
            factory = algorithm.getConnectionFactory();
        } catch (final LdapException e) {
            return newExceptionPromise(e);
        }

        return factory.getConnectionAsync();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LoadBalancer(");
        builder.append(algorithm);
        builder.append(')');
        return builder.toString();
    }
}
