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
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.forgerock.util.time.Duration.duration;

import java.io.Closeable;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.util.Option;
import org.forgerock.util.time.Duration;

/**
 * A load balancing algorithm distributes connection requests across one or more underlying connection factories in an
 * implementation defined manner.
 *
 * @see Connections#newLoadBalancer(LoadBalancingAlgorithm)
 */
public interface LoadBalancingAlgorithm extends Closeable {
    /**
     * Specifies the interval between successive attempts to reconnect to offline load-balanced connection factories.
     * The default configuration is to attempt to reconnect every second.
     */
    Option<Duration> LOAD_BALANCER_MONITORING_INTERVAL = Option.withDefault(duration("1 seconds"));

    /**
     * Specifies the event listener which should be notified whenever a load-balanced connection factory changes state
     * from online to offline or vice-versa. By default events will be logged to the {@code LoadBalancingAlgorithm}
     * logger using the {@link LoadBalancerEventListener#LOG_EVENTS} listener.
     */
    Option<LoadBalancerEventListener> LOAD_BALANCER_EVENT_LISTENER =
            Option.of(LoadBalancerEventListener.class, LoadBalancerEventListener.LOG_EVENTS);

    /**
     * Specifies the scheduler which will be used for periodically reconnecting to offline connection factories. A
     * system-wide scheduler will be used by default.
     */
    Option<ScheduledExecutorService> LOAD_BALANCER_SCHEDULER = Option.of(ScheduledExecutorService.class, null);

    /**
     * Releases any resources associated with this algorithm, including any associated connection factories.
     */
    @Override
    void close();

    /**
     * Returns a connection factory which should be used in order to satisfy the next connection request.
     *
     * @return The connection factory.
     * @throws LdapException
     *         If no connection factories are available for use.
     */
    ConnectionFactory getConnectionFactory() throws LdapException;
}
