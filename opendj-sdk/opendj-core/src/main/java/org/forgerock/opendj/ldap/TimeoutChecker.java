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
 *      Portions copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static java.util.Collections.newSetFromMap;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * Checks {@code TimeoutEventListener listeners} for events that have timed out.
 * <p>
 * All listeners registered with the {@code #addListener()} method are called
 * back with {@code TimeoutEventListener#handleTimeout()} to be able to handle
 * the timeout.
 */
public final class TimeoutChecker {
    /**
     * Global reference on the timeout checker.
     */
    public static final ReferenceCountedObject<TimeoutChecker> TIMEOUT_CHECKER =
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

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Condition variable used for coordinating the timeout thread.
     */
    private final Object stateLock = new Object();

    /**
     * The listener set must be safe from CMEs. For example, if the listener is
     * a connection, expiring requests can cause the connection to be closed.
     */
    private final Set<TimeoutEventListener> listeners =
            newSetFromMap(new ConcurrentHashMap<TimeoutEventListener, Boolean>());

    /**
     * Used to signal thread shutdown.
     */
    private volatile boolean shutdownRequested;

    /**
     * Contains the minimum delay for listeners which were added while the
     * timeout check was not sleeping (i.e. while it was processing listeners).
     */
    private volatile long pendingListenerMinDelay = Long.MAX_VALUE;

    private TimeoutChecker() {
        final Thread checkerThread = new Thread("OpenDJ LDAP SDK Timeout Checker") {
            @Override
            public void run() {
                logger.debug(LocalizableMessage.raw("Timeout Checker Starting"));
                while (!shutdownRequested) {
                    /*
                     * New listeners may be added during iteration and may not
                     * be included in the computation of the new delay. This
                     * could potentially result in the timeout checker waiting
                     * longer than it should, or even forever (e.g. if the new
                     * listener is the first).
                     */
                    final long currentTime = System.currentTimeMillis();
                    long delay = Long.MAX_VALUE;
                    pendingListenerMinDelay = Long.MAX_VALUE;
                    for (final TimeoutEventListener listener : listeners) {
                        logger.trace(LocalizableMessage.raw("Checking connection %s delay = %d", listener, delay));

                        // May update the connections set.
                        final long newDelay = listener.handleTimeout(currentTime);
                        if (newDelay > 0) {
                            delay = Math.min(newDelay, delay);
                        }
                    }

                    try {
                        synchronized (stateLock) {
                            // Include any pending listener delays.
                            delay = Math.min(pendingListenerMinDelay, delay);
                            if (shutdownRequested) {
                                // Stop immediately.
                                break;
                            } else if (delay <= 0) {
                                /*
                                 * If there is at least one connection then the
                                 * delay should be > 0.
                                 */
                                stateLock.wait();
                            } else {
                                stateLock.wait(delay);
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

    /**
     * Registers a timeout event listener for timeout notification.
     *
     * @param listener
     *            The timeout event listener.
     */
    public void addListener(final TimeoutEventListener listener) {
        /*
         * Only add the listener if it has a non-zero timeout. This assumes that
         * the timeout is fixed.
         */
        final long timeout = listener.getTimeout();
        if (timeout > 0) {
            listeners.add(listener);
            synchronized (stateLock) {
                pendingListenerMinDelay = Math.min(pendingListenerMinDelay, timeout);
                stateLock.notifyAll();
            }
        }
    }

    /**
     * Deregisters a timeout event listener for timeout notification.
     *
     * @param listener
     *            The timeout event listener.
     */
    public void removeListener(final TimeoutEventListener listener) {
        listeners.remove(listener);
        // No need to signal.
    }

    private void shutdown() {
        synchronized (stateLock) {
            shutdownRequested = true;
            stateLock.notifyAll();
        }
    }
}
