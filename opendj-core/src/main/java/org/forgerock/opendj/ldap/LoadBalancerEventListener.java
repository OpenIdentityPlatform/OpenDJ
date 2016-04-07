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
 * Copyright 2013-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.LOAD_BALANCER_EVENT_LISTENER_LOG_OFFLINE;
import static com.forgerock.opendj.ldap.CoreMessages.LOAD_BALANCER_EVENT_LISTENER_LOG_ONLINE;

import java.util.EventListener;

import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * An object that registers to be notified when a connection factory associated
 * with a load-balancer changes state from offline to online or vice-versa.
 * <p>
 * <b>NOTE:</b> load-balancer implementations must ensure that only one event is
 * sent at a time. Event listener implementations should not need to be thread
 * safe.
 *
 * @see Connections#LOAD_BALANCER_EVENT_LISTENER
 */
public interface LoadBalancerEventListener extends EventListener {
    /**
     * An event listener implementation which logs events to the LoadBalancingAlgorithm logger. This event listener is
     * the default implementation configured using the {@link Connections#LOAD_BALANCER_EVENT_LISTENER}
     * option.
     */
    LoadBalancerEventListener LOG_EVENTS = new LoadBalancerEventListener() {
        private final LocalizedLogger logger = LocalizedLogger.getLocalizedLogger(LoadBalancer.class);

        @Override
        public void handleConnectionFactoryOnline(final ConnectionFactory factory) {
            logger.info(LOAD_BALANCER_EVENT_LISTENER_LOG_ONLINE.get(factory));
        }

        @Override
        public void handleConnectionFactoryOffline(final ConnectionFactory factory, final LdapException error) {
            logger.warn(LOAD_BALANCER_EVENT_LISTENER_LOG_OFFLINE.get(factory, error.getMessage()));
        }
    };

    /** An event listener implementation which ignores all events. */
    LoadBalancerEventListener NO_OP = new LoadBalancerEventListener() {
        @Override
        public void handleConnectionFactoryOnline(final ConnectionFactory factory) {
            // Do nothing.
        }

        @Override
        public void handleConnectionFactoryOffline(final ConnectionFactory factory, final LdapException error) {
            // Do nothing.
        }
    };

    /**
     * Invoked when the load-balancer is unable to obtain a connection from the
     * specified connection factory. The connection factory will be removed from
     * the load-balancer and monitored periodically in order to determine when
     * it is available again, at which point an online notification event will
     * occur.
     *
     * @param factory
     *            The connection factory which has failed.
     * @param error
     *            The last error that occurred.
     */
    void handleConnectionFactoryOffline(ConnectionFactory factory, LdapException error);

    /**
     * Invoked when the load-balancer detects that a previously offline
     * connection factory is available for use again.
     *
     * @param factory
     *            The connection factory which is now available for use.
     */
    void handleConnectionFactoryOnline(ConnectionFactory factory);
}
