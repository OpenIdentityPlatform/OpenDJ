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

import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A fail-over load balancing algorithm provides fault tolerance across multiple
 * underlying connection factories.
 * <p>
 * This algorithm is typically used for load-balancing <i>between</i> data
 * centers, where there is preference to always forward connection requests to
 * the <i>closest available</i> data center. This algorithm contrasts with the
 * {@link RoundRobinLoadBalancingAlgorithm} which is used for load-balancing
 * <i>within</i> a data center.
 * <p>
 * This algorithm selects connection factories based on the order in which they
 * were provided during construction. More specifically, an attempt to obtain a
 * connection factory will always return the <i>first operational</i> connection
 * factory in the list. Applications should, therefore, organize the connection
 * factories such that the <i>preferred</i> (usually the closest) connection
 * factories appear before those which are less preferred.
 * <p>
 * If a problem occurs that temporarily prevents connections from being obtained
 * for one of the connection factories, then this algorithm automatically
 * "fails over" to the next operational connection factory in the list. If none
 * of the connection factories are operational then a
 * {@code ConnectionException} is returned to the client.
 * <p>
 * The implementation periodically attempts to connect to failed connection
 * factories in order to determine if they have become available again.
 *
 * @see RoundRobinLoadBalancingAlgorithm
 * @see Connections#newLoadBalancer(LoadBalancingAlgorithm)
 */
public final class FailoverLoadBalancingAlgorithm extends AbstractLoadBalancingAlgorithm {

    /**
     * Creates a new fail-over load balancing algorithm which will monitor
     * offline connection factories every 1 second using the default scheduler.
     *
     * @param factories
     *            The ordered collection of connection factories.
     */
    public FailoverLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories) {
        this(factories, null, 1, TimeUnit.SECONDS, null);
    }

    /**
     * Creates a new fail-over load balancing algorithm which will monitor
     * offline connection factories every 1 second using the default scheduler.
     *
     * @param factories
     *            The ordered collection of connection factories.
     * @param listener
     *            The event listener which should be notified whenever a
     *            connection factory changes state from online to offline or
     *            vice-versa.
     */
    public FailoverLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories,
            final LoadBalancerEventListener listener) {
        this(factories, listener, 1, TimeUnit.SECONDS, null);
    }

    /**
     * Creates a new fail-over load balancing algorithm which will monitor
     * offline connection factories using the specified frequency using the
     * default scheduler.
     *
     * @param factories
     *            The connection factories.
     * @param listener
     *            The event listener which should be notified whenever a
     *            connection factory changes state from online to offline or
     *            vice-versa.
     * @param interval
     *            The interval between attempts to poll offline factories.
     * @param unit
     *            The time unit for the interval between attempts to poll
     *            offline factories.
     */
    public FailoverLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories,
            final LoadBalancerEventListener listener, final long interval, final TimeUnit unit) {
        this(factories, listener, interval, unit, null);
    }

    /**
     * Creates a new fail-over load balancing algorithm which will monitor
     * offline connection factories using the specified frequency and scheduler.
     *
     * @param factories
     *            The connection factories.
     * @param listener
     *            The event listener which should be notified whenever a
     *            connection factory changes state from online to offline or
     *            vice-versa.
     * @param interval
     *            The interval between attempts to poll offline factories.
     * @param unit
     *            The time unit for the interval between attempts to poll
     *            offline factories.
     * @param scheduler
     *            The scheduler which should for periodically monitoring dead
     *            connection factories to see if they are usable again.
     */
    public FailoverLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories,
            final LoadBalancerEventListener listener, final long interval, final TimeUnit unit,
            final ScheduledExecutorService scheduler) {
        super(factories, listener, interval, unit, scheduler);
    }

    /**
     * Creates a new fail-over load balancing algorithm which will monitor
     * offline connection factories using the specified frequency using the
     * default scheduler.
     *
     * @param factories
     *            The connection factories.
     * @param interval
     *            The interval between attempts to poll offline factories.
     * @param unit
     *            The time unit for the interval between attempts to poll
     *            offline factories.
     */
    public FailoverLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories,
            final long interval, final TimeUnit unit) {
        this(factories, null, interval, unit, null);
    }

    /**
     * Creates a new fail-over load balancing algorithm which will monitor
     * offline connection factories using the specified frequency and scheduler.
     *
     * @param factories
     *            The connection factories.
     * @param interval
     *            The interval between attempts to poll offline factories.
     * @param unit
     *            The time unit for the interval between attempts to poll
     *            offline factories.
     * @param scheduler
     *            The scheduler which should for periodically monitoring dead
     *            connection factories to see if they are usable again.
     */
    public FailoverLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories,
            final long interval, final TimeUnit unit, final ScheduledExecutorService scheduler) {
        this(factories, null, interval, unit, scheduler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getAlgorithmName() {
        return "Failover";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getInitialConnectionFactoryIndex() {
        // Always start with the first connection factory.
        return 0;
    }

}
