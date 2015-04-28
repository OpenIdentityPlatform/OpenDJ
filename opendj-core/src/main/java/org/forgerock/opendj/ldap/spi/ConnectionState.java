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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * This class can be used to manage the internal state of a connection, ensuring
 * valid and atomic state transitions, as well as connection event listener
 * notification. There are 4 states:
 * <ul>
 * <li>connection is <b>valid</b> (isClosed()=false, isFailed()=false): can fail
 * or be closed
 * <li>connection has failed due to an <b>error</b> (isClosed()=false,
 * isFailed()=true): can be closed
 * <li>connection has been <b>closed</b> by the application (isClosed()=true,
 * isFailed()=false): terminal state
 * <li>connection has failed due to an <b>error</b> and has been <b>closed</b>
 * by the application (isClosed()=true, isFailed()=true): terminal state
 * </ul>
 * All methods are synchronized and container classes may also synchronize on
 * the state where needed. The state transition methods,
 * {@link #notifyConnectionClosed()} and
 * {@link #notifyConnectionError(boolean, LdapException)}, correspond to
 * methods in the {@link ConnectionEventListener} interface except that they
 * return a boolean indicating whether the transition was successful or not.
 */
public final class ConnectionState {
    /*
     * FIXME: The synchronization in this class has been kept simple for now.
     * However, ideally we should notify listeners without synchronizing on the
     * state in case a listener takes a long time to complete.
     */

    /*
     * FIXME: This class should be used by connection pool and ldap connection
     * implementations as well.
     */

    /**
     * Use the State design pattern to manage state transitions.
     */
    private enum State {

        /**
         * Connection has not encountered an error nor has it been closed
         * (initial state).
         */
        VALID() {
            @Override
            void addConnectionEventListener(final ConnectionState cs,
                    final ConnectionEventListener listener) {
                cs.listeners.add(listener);
            }

            @Override
            boolean isClosed() {
                return false;
            }

            @Override
            boolean isFailed() {
                return false;
            }

            @Override
            boolean isValid() {
                return true;
            }

            @Override
            boolean notifyConnectionClosed(final ConnectionState cs) {
                cs.state = CLOSED;
                for (final ConnectionEventListener listener : cs.listeners) {
                    listener.handleConnectionClosed();
                }
                return true;
            }

            @Override
            boolean notifyConnectionError(final ConnectionState cs,
                    final boolean isDisconnectNotification, final LdapException error) {
                // Transition from valid to error state.
                cs.failedDueToDisconnect = isDisconnectNotification;
                cs.connectionError = error;
                cs.state = ERROR;
                /*
                 * FIXME: a re-entrant close will invoke close listeners before
                 * error notification has completed.
                 */
                for (final ConnectionEventListener listener : cs.listeners) {
                    // Use the reason provided in the disconnect notification.
                    listener.handleConnectionError(isDisconnectNotification, error);
                }
                return true;
            }

            @Override
            void notifyUnsolicitedNotification(final ConnectionState cs,
                    final ExtendedResult notification) {
                for (final ConnectionEventListener listener : cs.listeners) {
                    listener.handleUnsolicitedNotification(notification);
                }
            }
        },

        /**
         * Connection has encountered an error, but has not been closed.
         */
        ERROR() {
            @Override
            void addConnectionEventListener(final ConnectionState cs,
                    final ConnectionEventListener listener) {
                listener.handleConnectionError(cs.failedDueToDisconnect, cs.connectionError);
                cs.listeners.add(listener);
            }

            @Override
            boolean isClosed() {
                return false;
            }

            @Override
            boolean isFailed() {
                return true;
            }

            @Override
            boolean isValid() {
                return false;
            }

            @Override
            boolean notifyConnectionClosed(final ConnectionState cs) {
                cs.state = ERROR_CLOSED;
                for (final ConnectionEventListener listener : cs.listeners) {
                    listener.handleConnectionClosed();
                }
                return true;
            }
        },

        /**
         * Connection has been closed (terminal state).
         */
        CLOSED() {
            @Override
            void addConnectionEventListener(final ConnectionState cs,
                    final ConnectionEventListener listener) {
                listener.handleConnectionClosed();
            }

            @Override
            boolean isClosed() {
                return true;
            }

            @Override
            boolean isFailed() {
                return false;
            }

            @Override
            boolean isValid() {
                return false;
            }
        },

        /**
         * Connection has encountered an error and has been closed (terminal
         * state).
         */
        ERROR_CLOSED() {
            @Override
            void addConnectionEventListener(final ConnectionState cs,
                    final ConnectionEventListener listener) {
                listener.handleConnectionError(cs.failedDueToDisconnect, cs.connectionError);
                listener.handleConnectionClosed();
            }

            @Override
            boolean isClosed() {
                return true;
            }

            @Override
            boolean isFailed() {
                return true;
            }

            @Override
            boolean isValid() {
                return false;
            }
        };

        abstract void addConnectionEventListener(ConnectionState cs,
                final ConnectionEventListener listener);

        abstract boolean isClosed();

        abstract boolean isFailed();

        abstract boolean isValid();

        boolean notifyConnectionClosed(final ConnectionState cs) {
            return false;
        }

        boolean notifyConnectionError(final ConnectionState cs,
                final boolean isDisconnectNotification, final LdapException error) {
            return false;
        }

        void notifyUnsolicitedNotification(final ConnectionState cs,
                final ExtendedResult notification) {
            // Do nothing by default.
        }
    }

    /**
     * Non-{@code null} once the connection has failed due to a connection
     * error. Volatile so that it can be read without synchronization.
     */
    private volatile LdapException connectionError;

    /** Whether the connection has failed due to a disconnect notification. */
    private boolean failedDueToDisconnect;

    /** Registered event listeners. */
    private final List<ConnectionEventListener> listeners = new LinkedList<>();

    /** Internal state implementation. */
    private volatile State state = State.VALID;

    /** Creates a new connection state which is initially valid. */
    public ConnectionState() {
        // Nothing to do.
    }

    /**
     * Registers the provided connection event listener so that it will be
     * notified when this connection is closed by the application, receives an
     * unsolicited notification, or experiences a fatal error.
     *
     * @param listener
     *            The listener which wants to be notified when events occur on
     *            this connection.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code listener} was {@code null}.
     */
    public synchronized void addConnectionEventListener(final ConnectionEventListener listener) {
        state.addConnectionEventListener(this, listener);
    }

    /**
     * Returns the error that caused the connection to fail, or {@code null} if
     * the connection has not failed.
     *
     * @return The error that caused the connection to fail, or {@code null} if
     *         the connection has not failed.
     */
    public LdapException getConnectionError() {
        return connectionError;
    }

    /**
     * Indicates whether or not this connection has been explicitly closed by
     * calling {@code close}. This method will not return {@code true} if a
     * fatal error has occurred on the connection unless {@code close} has been
     * called.
     *
     * @return {@code true} if this connection has been explicitly closed by
     *         calling {@code close}, or {@code false} otherwise.
     */
    public boolean isClosed() {
        return state.isClosed();
    }

    /**
     * Returns {@code true} if the associated connection has not been closed and
     * no fatal errors have been detected.
     *
     * @return {@code true} if this connection is valid, {@code false}
     *         otherwise.
     */
    public boolean isValid() {
        return state.isValid();
    }

    /**
     * Attempts to transition this connection state to closed and invokes event
     * listeners if successful.
     *
     * @return {@code true} if the state changed to closed, or {@code false} if
     *         the state was already closed.
     * @see ConnectionEventListener#handleConnectionClosed()
     */
    public synchronized boolean notifyConnectionClosed() {
        return state.notifyConnectionClosed(this);
    }

    /**
     * Attempts to transition this connection state to error and invokes event
     * listeners if successful.
     *
     * @param isDisconnectNotification
     *            {@code true} if the error was triggered by a disconnect
     *            notification sent by the server, otherwise {@code false}.
     * @param error
     *            The exception that is about to be thrown to the application.
     * @return {@code true} if the state changed to error, or {@code false} if
     *         the state was already error or closed.
     * @see ConnectionEventListener#handleConnectionError(boolean,
     *      LdapException)
     */
    public synchronized boolean notifyConnectionError(final boolean isDisconnectNotification,
            final LdapException error) {
        return state.notifyConnectionError(this, isDisconnectNotification, error);
    }

    /**
     * Notifies event listeners of the provided unsolicited notification if the
     * state is valid.
     *
     * @param notification
     *            The unsolicited notification.
     * @see ConnectionEventListener#handleUnsolicitedNotification(ExtendedResult)
     */
    public synchronized void notifyUnsolicitedNotification(final ExtendedResult notification) {
        state.notifyUnsolicitedNotification(this, notification);
    }

    /**
     * Removes the provided connection event listener from this connection so
     * that it will no longer be notified when this connection is closed by the
     * application, receives an unsolicited notification, or experiences a fatal
     * error.
     *
     * @param listener
     *            The listener which no longer wants to be notified when events
     *            occur on this connection.
     * @throws NullPointerException
     *             If the {@code listener} was {@code null}.
     */
    public synchronized void removeConnectionEventListener(final ConnectionEventListener listener) {
        listeners.remove(listener);
    }

}
