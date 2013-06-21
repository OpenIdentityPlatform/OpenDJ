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
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.io.Closeable;

/**
 * A load balancing algorithm distributes connection requests across one or more
 * underlying connection factories in an implementation defined manner.
 *
 * @see Connections#newLoadBalancer(LoadBalancingAlgorithm) newLoadBalancer
 */
public interface LoadBalancingAlgorithm extends Closeable {

    /**
     * Releases any resources associated with this algorithm, including any
     * associated connection factories.
     */
    @Override
    public void close();

    /**
     * Returns a connection factory which should be used in order to satisfy the
     * next connection request.
     *
     * @return The connection factory.
     * @throws ErrorResultException
     *             If no connection factories are available for use.
     */
    ConnectionFactory getConnectionFactory() throws ErrorResultException;
}
