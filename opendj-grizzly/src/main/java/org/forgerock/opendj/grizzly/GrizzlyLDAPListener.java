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
 * Portions copyright 2011-2016 ForgeRock AS.
 * Portions copyright 2025 3A Systems, LLC.
 */
package org.forgerock.opendj.grizzly;

import static org.forgerock.opendj.grizzly.ServerTCPNIOTransport.SERVER_TRANSPORT;
import static org.forgerock.opendj.ldap.LDAPListener.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.nio.transport.TCPNIOBindingHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.ReferenceCountedObject;
import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Stream;

/**
 * LDAP listener implementation using Grizzly for transport.
 */
public final class GrizzlyLDAPListener implements LDAPListenerImpl {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private final ReferenceCountedObject<TCPNIOTransport>.Reference transport;
    private final Collection<TCPNIOServerConnection> serverConnections;
    private final AtomicBoolean isClosed = new AtomicBoolean();
    private final Set<InetSocketAddress> socketAddresses;
    private final Options options;

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP client connections using the provided
     * address and connection options.
     *
     * @param addresses
     *            The addresses to listen on.
     * @param options
     *            The LDAP listener options.
     * @param requestHandlerFactory
     *            The server connection factory which will be used to create server connections.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided address.
     */
    public GrizzlyLDAPListener(final Set<InetSocketAddress> addresses, final Options options,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> requestHandlerFactory) throws IOException {
        this(addresses, requestHandlerFactory, options, null);
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP client connections using the provided
     * address, connection options and provided TCP transport.
     *
     * @param addresses
     *            The addresses to listen on.
     * @param requestHandlerFactory
     *            The server connection factory which will be used to create server connections.
     * @param options
     *            The LDAP listener options.
     * @param transport
     *            Grizzly TCP Transport NIO implementation to use for connections. If {@code null}, default transport
     *            will be used.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided address.
     */
    public GrizzlyLDAPListener(final Set<InetSocketAddress> addresses,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> requestHandlerFactory,
            final Options options, TCPNIOTransport transport) throws IOException {

        this.transport = SERVER_TRANSPORT.acquireIfNull(transport);
        this.options = Options.copyOf(options);
        final LDAPServerFilter serverFilter = new LDAPServerFilter(requestHandlerFactory, options,
                options.get(LDAP_DECODE_OPTIONS), options.get(MAX_CONCURRENT_REQUESTS));
        final FilterChain ldapChain = GrizzlyUtils.buildFilterChain(this.transport.get().getProcessor(), serverFilter);
        final TCPNIOBindingHandler bindingHandler = TCPNIOBindingHandler.builder(this.transport.get())
                .processor(ldapChain).build();
        this.serverConnections = new ArrayList<>(addresses.size());
        this.socketAddresses = new HashSet<>(addresses.size());
        for (final InetSocketAddress address : addresses) {
            final TCPNIOServerConnection bound = bindingHandler.bind(address, options.get(CONNECT_MAX_BACKLOG));
            serverConnections.add(bound);
            socketAddresses.add((InetSocketAddress) bound.getLocalAddress());
        }
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            for (TCPNIOConnection serverConnection : serverConnections) {
                try {
                    serverConnection.close().get(5, TimeUnit.SECONDS);
                } catch (final Exception e) {
                    // TODO: I18N
                    logger.warn(LocalizableMessage.raw("Exception occurred while closing listener", e));
                }
            }
            transport.release();
        }
    }

    @Override
    public Set<InetSocketAddress> getSocketAddresses() {
        return socketAddresses;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPListener(");
        builder.append(socketAddresses);
        builder.append(')');
        return builder.toString();
    }

    Options getLDAPListenerOptions() {
        return options;
    }
}
