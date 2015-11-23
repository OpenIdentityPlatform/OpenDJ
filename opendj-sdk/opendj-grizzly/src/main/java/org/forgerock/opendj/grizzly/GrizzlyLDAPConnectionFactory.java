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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.grizzly;

import static com.forgerock.opendj.grizzly.GrizzlyMessages.LDAP_CONNECTION_CONNECT_TIMEOUT;
import static org.forgerock.opendj.grizzly.DefaultTCPNIOTransport.DEFAULT_TRANSPORT;
import static org.forgerock.opendj.grizzly.GrizzlyUtils.buildFilterChain;
import static org.forgerock.opendj.grizzly.GrizzlyUtils.configureConnection;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.CONNECT_TIMEOUT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.LDAP_DECODE_OPTIONS;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.TimeoutChecker.TIMEOUT_CHECKER;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TimeoutChecker;
import org.forgerock.opendj.ldap.TimeoutEventListener;
import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.LDAPConnectionImpl;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.time.Duration;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * LDAP connection factory implementation using Grizzly for transport.
 */
public final class GrizzlyLDAPConnectionFactory implements LDAPConnectionFactoryImpl {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Adapts a Grizzly connection completion handler to an LDAP connection promise.
     */
    @SuppressWarnings("rawtypes")
    private final class CompletionHandlerAdapter implements CompletionHandler<Connection>, TimeoutEventListener {
        private final PromiseImpl<LDAPConnectionImpl, LdapException> promise;
        private final long timeoutEndTime;

        private CompletionHandlerAdapter(final PromiseImpl<LDAPConnectionImpl, LdapException> promise) {
            this.promise = promise;
            final long timeoutMS = getTimeout();
            this.timeoutEndTime = timeoutMS > 0 ? System.currentTimeMillis() + timeoutMS : 0;
            timeoutChecker.get().addListener(this);
        }

        @Override
        public void cancelled() {
            // Ignore this.
        }

        @Override
        public void completed(final Connection result) {
            // Adapt the connection.
            final GrizzlyLDAPConnection connection = adaptConnection(result);
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
            promise.handleException(adaptConnectionException(throwable));
            releaseTransportAndTimeoutChecker();
        }

        @Override
        public void updated(final Connection result) {
            // Ignore this.
        }

        private GrizzlyLDAPConnection adaptConnection(final Connection<?> connection) {
            configureConnection(connection, logger, options);

            final GrizzlyLDAPConnection ldapConnection =
                    new GrizzlyLDAPConnection(connection, GrizzlyLDAPConnectionFactory.this);
            timeoutChecker.get().addListener(ldapConnection);
            clientFilter.registerConnection(connection, ldapConnection);
            return ldapConnection;
        }

        private LdapException adaptConnectionException(Throwable t) {
            if (!(t instanceof LdapException) && t instanceof ExecutionException) {
                t = t.getCause() != null ? t.getCause() : t;
            }
            if (t instanceof LdapException) {
                return (LdapException) t;
            } else {
                return newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR, t.getMessage(), t);
            }
        }

        @Override
        public long handleTimeout(final long currentTime) {
            if (timeoutEndTime == 0) {
                return 0;
            } else if (timeoutEndTime > currentTime) {
                return timeoutEndTime - currentTime;
            } else {
                promise.handleException(newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                        LDAP_CONNECTION_CONNECT_TIMEOUT.get(getSocketAddress(), getTimeout()).toString()));
                return 0;
            }
        }

        @Override
        public long getTimeout() {
            final Duration duration = options.get(CONNECT_TIMEOUT);
            return duration.isUnlimited() ? 0L : duration.to(TimeUnit.MILLISECONDS);
        }
    }

    private final LDAPClientFilter clientFilter;
    private final FilterChain defaultFilterChain;
    private final Options options;
    private final String host;
    private final int port;

    /**
     * Prevents the transport and timeoutChecker being released when there are
     * remaining references (this factory or any connections). It is initially
     * set to 1 because this factory has a reference.
     */
    private final AtomicInteger referenceCount = new AtomicInteger(1);

    /**
     * Indicates whether this factory has been closed or not.
     */
    private final AtomicBoolean isClosed = new AtomicBoolean();

    private final ReferenceCountedObject<TCPNIOTransport>.Reference transport;
    private final ReferenceCountedObject<TimeoutChecker>.Reference timeoutChecker = TIMEOUT_CHECKER.acquire();

    /**
     * Grizzly TCP Transport NIO implementation to use for connections. If {@code null}, default transport will be
     * used.
     */
    public static final Option<TCPNIOTransport> GRIZZLY_TRANSPORT = Option.of(TCPNIOTransport.class, null);

    /**
     * Creates a new LDAP connection factory based on Grizzly which can be used to create connections to the Directory
     * Server at the provided host and port address using provided connection options.
     *
     * @param host
     *         The hostname of the Directory Server to connect to.
     * @param port
     *         The port number of the Directory Server to connect to.
     * @param options
     *         The LDAP connection options to use when creating connections.
     */
    public GrizzlyLDAPConnectionFactory(final String host, final int port, final Options options) {
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(options.get(GRIZZLY_TRANSPORT));
        this.host = host;
        this.port = port;
        this.options = options;
        this.clientFilter = new LDAPClientFilter(options.get(LDAP_DECODE_OPTIONS), 0);
        this.defaultFilterChain = buildFilterChain(this.transport.get().getProcessor(), clientFilter);
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            releaseTransportAndTimeoutChecker();
        }
    }

    @Override
    public Promise<LDAPConnectionImpl, LdapException> getConnectionAsync() {
        acquireTransportAndTimeoutChecker(); // Protect resources.
        final SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(transport.get())
                                                                              .processor(defaultFilterChain)
                                                                              .build();
        final PromiseImpl<LDAPConnectionImpl, LdapException> promise = PromiseImpl.create();
        connectorHandler.connect(getSocketAddress(), new CompletionHandlerAdapter(promise));
        return promise;
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public String getHostName() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    TimeoutChecker getTimeoutChecker() {
        return timeoutChecker.get();
    }

    Options getLDAPOptions() {
        return options;
    }

    void releaseTransportAndTimeoutChecker() {
        if (referenceCount.decrementAndGet() == 0) {
            transport.release();
            timeoutChecker.release();
        }
    }

    private void acquireTransportAndTimeoutChecker() {
        /*
         * If the factory is not closed then we need to prevent the resources
         * (transport, timeout checker) from being released while the connection
         * attempt is in progress.
         */
        referenceCount.incrementAndGet();
        if (isClosed.get()) {
            releaseTransportAndTimeoutChecker();
            throw new IllegalStateException("Attempted to get a connection after factory close");
        }
    }
}
