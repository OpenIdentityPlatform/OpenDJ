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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * An LDAP client which has connected to a {@link ServerConnectionFactory}. An
 * LDAP client context can be used to query information about the client's
 * connection such as their network address, as well as managing the state of
 * the connection.
 */
public interface LDAPClientContext {

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
    void disconnect(ResultCode resultCode, String message);

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
     * Installs the provided connection security layer to the underlying
     * connection. This may be used to add a SASL integrity and/or
     * confidentiality protection layer after SASL authentication has completed,
     * but could also be used to add other layers such as compression. Multiple
     * layers may be installed.
     *
     * @param layer
     *            The negotiated bind context that can be used to encode and
     *            decode data on the connection.
     */
    void enableConnectionSecurityLayer(ConnectionSecurityLayer layer);

    /**
     * Installs the TLS/SSL security layer on the underlying connection. The
     * TLS/SSL security layer will be installed beneath any existing connection
     * security layers and can only be installed at most once.
     *
     * @param sslContext
     *            The {@code SSLContext} which should be used to secure the
     * @param protocols
     *            Names of all the protocols to enable or {@code null} to use
     *            the default protocols.
     * @param suites
     *            Names of all the suites to enable or {@code null} to use the
     *            default cipher suites.
     * @param wantClientAuth
     *            Set to {@code true} if client authentication is requested, or
     *            {@code false} if no client authentication is desired.
     * @param needClientAuth
     *            Set to {@code true} if client authentication is required, or
     *            {@code false} if no client authentication is desired.
     * @throws IllegalStateException
     *             If the TLS/SSL security layer has already been installed.
     */
    void enableTLS(SSLContext sslContext, String[] protocols, String[] suites,
            boolean wantClientAuth, boolean needClientAuth);
}
