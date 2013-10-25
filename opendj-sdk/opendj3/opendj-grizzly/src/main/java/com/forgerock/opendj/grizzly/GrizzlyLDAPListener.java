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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package com.forgerock.opendj.grizzly;

import static com.forgerock.opendj.grizzly.DefaultTCPNIOTransport.DEFAULT_TRANSPORT;
import static com.forgerock.opendj.util.StaticUtils.DEFAULT_LOG;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.nio.transport.TCPNIOBindingHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * LDAP listener implementation using Grizzly for transport.
 */
public final class GrizzlyLDAPListener implements LDAPListenerImpl {
    private final ReferenceCountedObject<TCPNIOTransport>.Reference transport;
    private final ServerConnectionFactory<LDAPClientContext, Integer> connectionFactory;
    private final TCPNIOServerConnection serverConnection;
    private final AtomicBoolean isClosed = new AtomicBoolean();

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
    public GrizzlyLDAPListener(final SocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final LDAPListenerOptions options) throws IOException {
        this(address, factory, options, null);
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections using the provided address, connection options and
     * provided TCP transport.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @param transport
     *            Grizzly TCP Transport NIO implementation to use for
     *            connections. If {@code null}, default transport will be used.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     */
    public GrizzlyLDAPListener(final SocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final LDAPListenerOptions options, TCPNIOTransport transport) throws IOException {
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(transport);
        this.connectionFactory = factory;

        final DecodeOptions decodeOptions = new DecodeOptions(options.getDecodeOptions());
        final LDAPServerFilter serverFilter = new LDAPServerFilter(this, decodeOptions, options
                .getMaxRequestSize());
        final FilterChain ldapChain = GrizzlyUtils.buildFilterChain(this.transport.get().getProcessor(), serverFilter);
        final TCPNIOBindingHandler bindingHandler =
                TCPNIOBindingHandler.builder(this.transport.get()).processor(ldapChain).build();
        this.serverConnection = bindingHandler.bind(address, options.getBacklog());
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                serverConnection.close().get();
            } catch (final InterruptedException e) {
                // Cannot handle here.
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                // TODO: I18N
                DEFAULT_LOG.warn("Exception occurred while closing listener", e);
            }
            transport.release();
        }
    }

    /** {@inheritDoc} */
    @Override
    public SocketAddress getSocketAddress() {
        return serverConnection.getLocalAddress();
    }

    @Override
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
}
