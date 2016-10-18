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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.security.sasl.SaslServer;

import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * An LDAP client which has connected to a {@link ServerConnectionFactory}. An
 * LDAP client context can be used to query information about the client's
 * connection such as their network address, as well as managing the state of
 * the connection.
 */
public interface LDAPClientContext {

    /** Listens for disconnection event. */
    public interface DisconnectListener {
        /**
         * Invoked when the connection has been closed as a result of a client disconnect, a fatal connection error, or
         * a server-side {@link #disconnect}.
         *
         * @param context
         *            The {@link LDAPClientContext} which has been disconnected
         * @param resultCode
         *            The {@link ResultCode} of the notification sent, or null
         * @param message
         *            The message of the notification sent, or null
         */
        void connectionDisconnected(final LDAPClientContext context, final ResultCode resultCode, final String message);
    }

    /**
     * Register a listener which will be notified when this {@link LDAPClientContext} is disconnected.
     *
     * @param listener The {@link DisconnectListener} to register.
     */
    void onDisconnect(final DisconnectListener listener);

    /**
     * Disconnects the client without sending a disconnect notification.
     * <p>
     * <b>Server connections:</b> invoking this method causes
     * {@link ServerConnection#handleConnectionDisconnected
     * handleConnectionDisconnected} to be called before this method returns.
     */
    void disconnect();

    /**
     * Disconnects the client and sends a disconnect notification, if possible,
     * containing the provided result code and diagnostic message.
     * <p>
     * <b>Server connections:</b> invoking this method causes
     * {@link ServerConnection#handleConnectionDisconnected
     * handleConnectionDisconnected} to be called before this method returns.
     *
     * @param resultCode
     *            The result code which should be included with the disconnect
     *            notification.
     * @param message
     *            The diagnostic message, which may be empty or {@code null}
     *            indicating that none was provided.
     */
    void disconnect(final ResultCode resultCode, final String message);

    /**
     * Returns the {@code InetSocketAddress} associated with the local system.
     *
     * @return The {@code InetSocketAddress} associated with the local system.
     */
    InetSocketAddress getLocalAddress();

    /**
     * Returns the {@code InetSocketAddress} associated with the remote system.
     *
     * @return The {@code InetSocketAddress} associated with the remote system.
     */
    InetSocketAddress getPeerAddress();

    /**
     * Returns the cipher strength, in bits, currently in use by the underlying
     * connection. This value is analogous to the
     * {@code javax.servlet.request.key_size} property defined in the Servlet
     * specification (section 3.8 "SSL Attributes"). It provides no indication
     * of the relative strength of different cipher algorithms, their known
     * weaknesses, nor the strength of other cryptographic information used
     * during SSL/TLS negotiation.
     *
     * @return The cipher strength, in bits, currently in use by the underlying
     *         connection.
     */
    int getSecurityStrengthFactor();

    /**
     * Returns the SSL session currently in use by the underlying connection, or
     * {@code null} if SSL/TLS is not enabled.
     *
     * @return The SSL session currently in use by the underlying connection, or
     *         {@code null} if SSL/TLS is not enabled.
     */
    SSLSession getSSLSession();

    /**
     * Returns {@code true} if the underlying connection has been closed as a
     * result of a client disconnect, a fatal connection error, or a server-side
     * {@link #disconnect}.
     * <p>
     * This method provides a polling mechanism which can be used by synchronous
     * request handler implementations to detect connection termination.
     * <p>
     * <b>Server connections:</b> this method will always return {@code true}
     * when called from within {@link ServerConnection#handleConnectionClosed
     * handleConnectionClosed},
     * {@link ServerConnection#handleConnectionDisconnected
     * handleConnectionDisconnected}, or
     * {@link ServerConnection#handleConnectionError handleConnectionError}.
     *
     * @return {@code true} if the underlying connection has been closed.
     */
    boolean isClosed();

    /**
     * Sends an unsolicited notification to the client.
     *
     * @param notification
     *            The notification to send.
     */
    void sendUnsolicitedNotification(ExtendedResult notification);

    /**
     * Installs the TLS/SSL security layer on the underlying connection. The
     * TLS/SSL security layer will be installed beneath any existing connection
     * security layers and can only be installed at most once.
     *
     * @param sslEngine
     *            The {@code SSLEngine} which should be used to secure the conneciton.
     */
    void enableTLS(SSLEngine sslEngine);

    /**
     * Installs the SASL security layer on the underlying connection.
     *
     * @param saslServer
     *            The {@code SaslServer} which should be used to secure the conneciton.
     */
    void enableSASL(SaslServer saslServer);
}
