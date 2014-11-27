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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.grizzly;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TimeoutChecker;
import org.forgerock.opendj.ldap.TimeoutEventListener;
import org.forgerock.opendj.ldap.spi.AbstractLdapConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.AbstractLdapConnectionImpl;
import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.ReferenceCountedObject;

import static org.forgerock.opendj.grizzly.DefaultTCPNIOTransport.*;
import static org.forgerock.opendj.grizzly.GrizzlyUtils.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.TimeoutChecker.*;

import static com.forgerock.opendj.grizzly.GrizzlyMessages.*;

/**
 * LDAP connection factory implementation using Grizzly for transport.
 */
public final class GrizzlyLDAPConnectionFactory extends AbstractLdapConnectionFactoryImpl implements
        LDAPConnectionFactoryImpl {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Adapts a Grizzly connection completion handler to an LDAP connection
     * promise.
     */
    @SuppressWarnings("rawtypes")
    private final class CompletionHandlerAdapter extends EmptyCompletionHandler<org.glassfish.grizzly.Connection>
            implements TimeoutEventListener {
        private final PromiseImpl<org.glassfish.grizzly.Connection, LdapException> promise;
        private final long timeoutEndTime;

        private CompletionHandlerAdapter(final PromiseImpl<org.glassfish.grizzly.Connection, LdapException> promise) {
            this.promise = promise;
            final long timeoutMS = getTimeout();
            this.timeoutEndTime = timeoutMS > 0 ? System.currentTimeMillis() + timeoutMS : 0;
            timeoutChecker.get().addListener(this);
        }

        @Override
        public void completed(final org.glassfish.grizzly.Connection connection) {
            timeoutChecker.get().removeListener(this);
            if (!promise.tryHandleResult(connection)) {
                // The connection has been either cancelled or it has timed out.
                connection.close();
            }
        }

        @Override
        public void failed(final Throwable throwable) {
            // Adapt and forward.
            timeoutChecker.get().removeListener(this);
            promise.handleError(adaptConnectionException(throwable));
        }

        @Override
        public long handleTimeout(final long currentTime) {
            if (timeoutEndTime == 0) {
                return 0;
            } else if (timeoutEndTime > currentTime) {
                return timeoutEndTime - currentTime;
            } else {
                promise.handleError(newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                        LDAP_CONNECTION_CONNECT_TIMEOUT.get(getSocketAddress(), getTimeout()).toString()));
                return 0;
            }
        }

        @Override
        public long getTimeout() {
            return options.getConnectTimeout(TimeUnit.MILLISECONDS);
        }
    }

    private final LDAPClientFilter clientFilter;
    private final FilterChain defaultFilterChain;
    private final ReferenceCountedObject<TCPNIOTransport>.Reference transport;
    private final ReferenceCountedObject<TimeoutChecker>.Reference timeoutChecker = TIMEOUT_CHECKER.acquire();

    @SuppressWarnings("rawtypes")
    private final Function<org.glassfish.grizzly.Connection, AbstractLdapConnectionImpl<?>, LdapException>
    convertToLDAPConnection =
        new Function<org.glassfish.grizzly.Connection, AbstractLdapConnectionImpl<?>, LdapException>() {
            @Override
            public GrizzlyLDAPConnection apply(org.glassfish.grizzly.Connection connection) throws LdapException {
                configureConnection(connection, options.isTCPNoDelay(), options.isKeepAlive(),
                    options.isReuseAddress(), options.getLinger(), logger);
                final GrizzlyLDAPConnection ldapConnection =
                    new GrizzlyLDAPConnection(connection, GrizzlyLDAPConnectionFactory.this);
                timeoutChecker.get().addListener(ldapConnection);
                clientFilter.registerConnection(connection, ldapConnection);
                return ldapConnection;
            }
        };

    /**
     * Creates a new LDAP connection factory based on Grizzly which can be used
     * to create connections to the Directory Server at the provided host and
     * port address using provided connection options.
     *
     * @param host
     *            The hostname of the Directory Server to connect to.
     * @param port
     *            The port number of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     */
    public GrizzlyLDAPConnectionFactory(final String host, final int port, final LDAPOptions options) {
        this(host, port, options, null);

    }

    private LdapException adaptConnectionException(Throwable t) {
        if (t instanceof LdapException) {
            return (LdapException) t;
        }
        t = t instanceof ExecutionException && t.getCause() != null ? t.getCause() : t;
        return newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR, t.getMessage(), t);
    }

    /**
     * Creates a new LDAP connection factory based on Grizzly which can be used
     * to create connections to the Directory Server at the provided host and
     * port address using provided connection options and provided TCP
     * transport.
     *
     * @param host
     *            The hostname of the Directory Server to connect to.
     * @param port
     *            The port number of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     * @param transport
     *            Grizzly TCP Transport NIO implementation to use for
     *            connections. If {@code null}, default transport will be used.
     */
    public GrizzlyLDAPConnectionFactory(final String host, final int port, final LDAPOptions options,
            final TCPNIOTransport transport) {
        super(host, port, options);
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(transport);
        this.clientFilter = new LDAPClientFilter(options.getDecodeOptions(), 0);
        this.defaultFilterChain = buildFilterChain(this.transport.get().getProcessor(), clientFilter);
    }

    TimeoutChecker getTimeoutChecker() {
        return timeoutChecker.get();
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Promise<AbstractLdapConnectionImpl<?>, LdapException> getConnectionAsync0() {
        final SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(transport.get())
                .processor(defaultFilterChain).build();
        final PromiseImpl<org.glassfish.grizzly.Connection, LdapException> promise = PromiseImpl.create();
        connectorHandler.connect(getSocketAddress(), new CompletionHandlerAdapter(promise));

        return promise.then(convertToLDAPConnection);
    }

    @Override
    protected Promise<Void, LdapException> installSecureLayer(final Connection connection) {
        final PromiseImpl<Void, LdapException> sslHandshakePromise = PromiseImpl.create();
        try {
            final GrizzlyLDAPConnection grizzlyConnection = (GrizzlyLDAPConnection) connection;
            grizzlyConnection.startTLS(options.getSSLContext(), options.getEnabledProtocols(),
                    options.getEnabledCipherSuites(), new EmptyCompletionHandler<SSLEngine>() {
                        @Override
                        public void completed(final SSLEngine result) {
                            if (!sslHandshakePromise.tryHandleResult(null)) {
                                // The connection has been either cancelled or
                                // it has timed out.
                                connection.close();
                            }
                        }

                        @Override
                        public void failed(final Throwable throwable) {
                            sslHandshakePromise.handleError(adaptConnectionException(throwable));
                        }
                    });
        } catch (final IOException e) {
            sslHandshakePromise.handleError(adaptConnectionException(e));
        }

        return sslHandshakePromise;
    }

    @Override
    protected void releaseImplResources() {
        transport.release();
        timeoutChecker.release();
    }

}
