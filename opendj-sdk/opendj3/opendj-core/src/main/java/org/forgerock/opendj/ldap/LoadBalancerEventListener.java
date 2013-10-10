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
 *      Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.EventListener;

/**
 * An object that registers to be notified when a connection factory associated
 * with a load-balancer changes state from offline to online or vice-versa.
 * <p>
 * <b>NOTE:</b> load-balancer implementations must ensure that only one event is
 * sent at a time. Event listener implementations should not need to be thread
 * safe.
 */
public interface LoadBalancerEventListener extends EventListener {

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
    void handleConnectionFactoryOffline(ConnectionFactory factory, ErrorResultException error);

    /**
     * Invoked when the load-balancer detects that a previously offline
     * connection factory is available for use again.
     *
     * @param factory
     *            The connection factory which is now available for use.
     */
    void handleConnectionFactoryOnline(ConnectionFactory factory);
}
