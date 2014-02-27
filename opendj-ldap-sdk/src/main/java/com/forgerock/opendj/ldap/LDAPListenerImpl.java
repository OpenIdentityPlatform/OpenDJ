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
 *      Portions copyright 2011-2014 ForgeRock AS
 */

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.DefaultTCPNIOTransport.DEFAULT_TRANSPORT;
import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOBindingHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.ReferenceCountedObject;
import com.forgerock.opendj.util.StaticUtils;

/**
 * LDAP listener implementation.
 */
public final class LDAPListenerImpl implements Closeable {
    private final ReferenceCountedObject<TCPNIOTransport>.Reference transport;
    private final FilterChain defaultFilterChain;
    private final ServerConnectionFactory<LDAPClientContext, Integer> connectionFactory;
    private final TCPNIOServerConnection serverConnection;
    private final AtomicBoolean isClosed = new AtomicBoolean();
    private final InetSocketAddress socketAddress;
    private final LDAPListenerOptions options;

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
    public LDAPListenerImpl(final InetSocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final LDAPListenerOptions options) throws IOException {
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(options.getTCPNIOTransport());
        this.connectionFactory = factory;
        this.options = new LDAPListenerOptions(options);
        final LDAPServerFilter serverFilter =
                new LDAPServerFilter(this, new LDAPReader(this.options.getDecodeOptions()),
                        this.options.getMaxRequestSize());
        this.defaultFilterChain =
                FilterChainBuilder.stateless().add(new TransportFilter()).add(serverFilter).build();
        final TCPNIOBindingHandler bindingHandler =
                TCPNIOBindingHandler.builder(transport.get()).processor(defaultFilterChain).build();
        this.serverConnection = bindingHandler.bind(address, options.getBacklog());

        /*
         * Get the socket address now, ensuring that the host is the same as the
         * one provided in the constructor. The port will have changed if 0 was
         * passed in.
         */
        final int port = ((InetSocketAddress) serverConnection.getLocalAddress()).getPort();
        socketAddress = new InetSocketAddress(StaticUtils.getHostName(address), port);
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
                DEBUG_LOG.log(Level.WARNING, "Exception occurred while closing listener", e);
            }
            transport.release();
        }
    }

    /**
     * Returns the address that this LDAP listener is listening on.
     *
     * @return The address that this LDAP listener is listening on.
     */
    public InetSocketAddress getSocketAddress() {
        return socketAddress;
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

    FilterChain getDefaultFilterChain() {
        return defaultFilterChain;
    }

    LDAPListenerOptions getLDAPListenerOptions() {
        return options;
    }
}
