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

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;
import static java.util.Collections.newSetFromMap;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * Checks connection for pending requests that have timed out.
 */
final class TimeoutChecker {
    /**
     * Global reference counted instance.
     */
    static final ReferenceCountedObject<TimeoutChecker> TIMEOUT_CHECKER =
            new ReferenceCountedObject<TimeoutChecker>() {
                @Override
                protected void destroyInstance(final TimeoutChecker instance) {
                    instance.shutdown();
                }

                @Override
                protected TimeoutChecker newInstance() {
                    return new TimeoutChecker();
                }
            };

    /**
     * Condition variable used for coordinating the timeout thread.
     */
    private final Object available = new Object();

    /**
     * The connection set must be safe from CMEs because expiring requests can
     * cause the connection to be closed.
     */
    private final Set<LDAPConnection> connections =
            newSetFromMap(new ConcurrentHashMap<LDAPConnection, Boolean>());

    /**
     * Used to signal thread shutdown.
     */
    private volatile boolean shutdownRequested = false;

    private TimeoutChecker() {
        final Thread checkerThread = new Thread("OpenDJ LDAP SDK Connection Timeout Checker") {
            @Override
            public void run() {
                DEBUG_LOG.fine("Timeout Checker Starting");
                while (!shutdownRequested) {
                    final long currentTime = System.currentTimeMillis();
                    long delay = 0;

                    for (final LDAPConnection connection : connections) {
                        if (DEBUG_LOG.isLoggable(Level.FINER)) {
                            DEBUG_LOG.finer("Checking connection " + connection + " delay = "
                                    + delay);
                        }

                        // May update the connections set.
                        final long newDelay = connection.cancelExpiredRequests(currentTime);
                        if (newDelay > 0) {
                            if (delay > 0) {
                                delay = Math.min(newDelay, delay);
                            } else {
                                delay = newDelay;
                            }
                        }
                    }

                    try {
                        synchronized (available) {
                            if (delay <= 0) {
                                available.wait();
                            } else {
                                available.wait(delay);
                            }
                        }
                    } catch (final InterruptedException e) {
                        shutdownRequested = true;
                    }
                }
            }
        };

        checkerThread.setDaemon(true);
        checkerThread.start();
    }

    void addConnection(final LDAPConnection connection) {
        connections.add(connection);
        signal();
    }

    void removeConnection(final LDAPConnection connection) {
        connections.remove(connection);
        // No need to signal.
    }

    private void shutdown() {
        shutdownRequested = true;
        signal();
    }

    // Wakes the timeout checker if it is sleeping.
    private void signal() {
        synchronized (available) {
            available.notifyAll();
        }
    }
}
