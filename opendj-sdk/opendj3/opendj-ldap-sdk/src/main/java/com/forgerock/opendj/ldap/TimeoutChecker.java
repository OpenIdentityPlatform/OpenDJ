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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * Checks connection for pending requests that have timed out.
 */
final class TimeoutChecker {
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

    private final Condition available;
    private final List<LDAPConnection> connections;
    private final ReentrantLock lock;
    private boolean shutdownRequested = false;

    private TimeoutChecker() {
        this.connections = new LinkedList<LDAPConnection>();
        this.lock = new ReentrantLock();
        this.available = lock.newCondition();

        final Thread checkerThread = new Thread("OpenDJ LDAP SDK Connection Timeout Checker") {
            @Override
            public void run() {
                DEBUG_LOG.fine("Timeout Checker Starting");
                lock.lock();
                try {
                    while (!shutdownRequested) {
                        final long currentTime = System.currentTimeMillis();
                        long delay = 0;

                        for (final LDAPConnection connection : connections) {
                            if (DEBUG_LOG.isLoggable(Level.FINER)) {
                                DEBUG_LOG.finer("Checking connection " + connection + " delay = "
                                        + delay);
                            }
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
                            if (delay <= 0) {
                                DEBUG_LOG.finer("There are no connections with "
                                        + "timeout specified. Sleeping");
                                available.await();
                            } else {
                                if (DEBUG_LOG.isLoggable(Level.FINER)) {
                                    DEBUG_LOG.log(Level.FINER, "Sleeping for " + delay + " ms");
                                }
                                available.await(delay, TimeUnit.MILLISECONDS);
                            }
                        } catch (final InterruptedException e) {
                            shutdownRequested = true;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        };

        checkerThread.setDaemon(true);
        checkerThread.start();
    }

    void addConnection(final LDAPConnection connection) {
        lock.lock();
        try {
            connections.add(connection);
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void removeConnection(final LDAPConnection connection) {
        lock.lock();
        try {
            connections.remove(connection);
        } finally {
            lock.unlock();
        }
    }

    private void shutdown() {
        lock.lock();
        try {
            shutdownRequested = true;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
