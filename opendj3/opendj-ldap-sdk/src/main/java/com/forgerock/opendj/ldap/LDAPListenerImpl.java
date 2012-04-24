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
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package com.forgerock.opendj.ldap;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.logging.Level;

import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.StaticUtils;

/**
 * LDAP listener implementation.
 */
public final class LDAPListenerImpl implements Closeable {
    private final TCPNIOTransport transport;
    private final FilterChain defaultFilterChain;
    private final ServerConnectionFactory<LDAPClientContext, Integer> connectionFactory;
    private final TCPNIOServerConnection serverConnection;

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections using the provided address and connection options.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     */
    public LDAPListenerImpl(final SocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final LDAPListenerOptions options) throws IOException {
        if (options.getTCPNIOTransport() == null) {
            this.transport = DefaultTCPNIOTransport.getInstance();
        } else {
            this.transport = options.getTCPNIOTransport();
        }
        this.connectionFactory = factory;

        final DecodeOptions decodeOptions = new DecodeOptions(options.getDecodeOptions());
        this.defaultFilterChain =
                FilterChainBuilder.stateless().add(new TransportFilter()).add(
                        new LDAPServerFilter(this, new LDAPReader(decodeOptions), 0)).build();

        this.serverConnection = transport.bind(address, options.getBacklog());
        this.serverConnection.setProcessor(defaultFilterChain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            serverConnection.close().get();
        } catch (final InterruptedException e) {
            // Cannot handle here.
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            // Ignore the exception.
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING)) {
                StaticUtils.DEBUG_LOG.log(Level.WARNING,
                        "Exception occurred while closing listener:" + e.getMessage(), e);
            }
        }
    }

    /**
     * Returns the address that this LDAP listener is listening on.
     *
     * @return The address that this LDAP listener is listening on.
     */
    public SocketAddress getSocketAddress() {
        return serverConnection.getLocalAddress();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPListener(");
        builder.append(getSocketAddress().toString());
        builder.append(')');
        return builder.toString();
    }

    ServerConnectionFactory<LDAPClientContext, Integer> getConnectionFactory() {
        return connectionFactory;
    }

    FilterChain getDefaultFilterChain() {
        return defaultFilterChain;
    }
}
