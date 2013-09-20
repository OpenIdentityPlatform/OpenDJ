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

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.DefaultTCPNIOTransport.DEFAULT_TRANSPORT;
import static com.forgerock.opendj.ldap.TimeoutChecker.TIMEOUT_CHECKER;
import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * LDAP connection factory implementation.
 */
public final class LDAPConnectionFactoryImpl implements ConnectionFactory {
    /**
     * Adapts a Grizzly connection completion handler to an LDAP connection
     * asynchronous future result.
     */
    @SuppressWarnings("rawtypes")
    private final class CompletionHandlerAdapter implements
            CompletionHandler<org.glassfish.grizzly.Connection> {

        private final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future;

        private CompletionHandlerAdapter(
                final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future) {
            this.future = future;
        }

        @Override
        public void cancelled() {
            // Ignore this.
        }

        @Override
        public void completed(final org.glassfish.grizzly.Connection result) {
            // Adapt the connection.
            final LDAPConnection connection = adaptConnection(result);

            // Plain connection.
            if (options.getSSLContext() == null) {
                onSuccess(connection);
                return;
            }

            // Start TLS or install SSL layer asynchronously.

            // Give up immediately if the future has been cancelled.
            if (future.isCancelled()) {
                connection.close();
                releaseTransportAndTimeoutChecker();
                return;
            }

            if (options.useStartTLS()) {
                // Chain StartTLS extended request.
                final StartTLSExtendedRequest startTLS =
                        Requests.newStartTLSExtendedRequest(options.getSSLContext());
                startTLS.addEnabledCipherSuite(options.getEnabledCipherSuites().toArray(
                        new String[options.getEnabledCipherSuites().size()]));
                startTLS.addEnabledProtocol(options.getEnabledProtocols().toArray(
                        new String[options.getEnabledProtocols().size()]));
                final ResultHandler<ExtendedResult> handler = new ResultHandler<ExtendedResult>() {
                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        onFailure(connection, error);
                    }

                    @Override
                    public void handleResult(final ExtendedResult result) {
                        onSuccess(connection);
                    }
                };
                connection.extendedRequestAsync(startTLS, null, handler);
            } else {
                // Install SSL/TLS layer.
                try {
                    connection.startTLS(options.getSSLContext(), options.getEnabledProtocols(),
                            options.getEnabledCipherSuites(),
                            new EmptyCompletionHandler<SSLEngine>() {
                                @Override
                                public void completed(final SSLEngine result) {
                                    onSuccess(connection);
                                }

                                @Override
                                public void failed(final Throwable throwable) {
                                    onFailure(connection, throwable);
                                }
                            });
                } catch (final IOException e) {
                    onFailure(connection, e);
                }
            }
        }

        @Override
        public void failed(final Throwable throwable) {
            // Adapt and forward.
            future.handleErrorResult(adaptConnectionException(throwable));
            releaseTransportAndTimeoutChecker();
        }

        @Override
        public void updated(final org.glassfish.grizzly.Connection result) {
            // Ignore this.
        }

        private LDAPConnection adaptConnection(final org.glassfish.grizzly.Connection<?> connection) {
            /*
             * Test shows that its much faster with non block writes but risk
             * running out of memory if the server is slow.
             */
            connection.configureBlocking(true);
            final LDAPConnection ldapConnection =
                    new LDAPConnection(connection, LDAPConnectionFactoryImpl.this);
            timeoutChecker.get().addConnection(ldapConnection);
            clientFilter.registerConnection(connection, ldapConnection);
            return ldapConnection;
        }

        private ErrorResultException adaptConnectionException(Throwable t) {
            if (!(t instanceof ErrorResultException) && t instanceof ExecutionException) {
                t = t.getCause() != null ? t.getCause() : t;
            }

            if (t instanceof ErrorResultException) {
                return (ErrorResultException) t;
            } else {
                return newErrorResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR, t.getMessage(), t);
            }
        }

        private void onFailure(final LDAPConnection connection, final Throwable t) {
            // Abort connection attempt due to error.
            connection.close();
            future.handleErrorResult(adaptConnectionException(t));
            releaseTransportAndTimeoutChecker();
        }

        private void onSuccess(final LDAPConnection connection) {
            future.handleResult(connection);

            // Close the connection if the future was cancelled.
            if (future.isCancelled()) {
                connection.close();
                releaseTransportAndTimeoutChecker();
            }
        }
    }

    private final LDAPClientFilter clientFilter;
    private final FilterChain defaultFilterChain;
    private final LDAPOptions options;
    private final SocketAddress socketAddress;

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
    private final ReferenceCountedObject<TimeoutChecker>.Reference timeoutChecker = TIMEOUT_CHECKER
            .acquire();

    /**
     * Creates a new LDAP connection factory implementation which can be used to
     * create connections to the Directory Server at the provided host and port
     * address using provided connection options.
     *
     * @param address
     *            The address of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     */
    public LDAPConnectionFactoryImpl(final SocketAddress address, final LDAPOptions options) {
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(options.getTCPNIOTransport());
        this.socketAddress = address;
        this.options = new LDAPOptions(options);
        this.clientFilter =
                new LDAPClientFilter(new LDAPReader(this.options.getDecodeOptions()), 0);
        this.defaultFilterChain =
                FilterChainBuilder.stateless().add(new TransportFilter()).add(clientFilter).build();
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            releaseTransportAndTimeoutChecker();
        }
    }

    @Override
    public Connection getConnection() throws ErrorResultException {
        try {
            return getConnectionAsync(null).get();
        } catch (final InterruptedException e) {
            throw newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
        }
    }

    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        acquireTransportAndTimeoutChecker(); // Protect resources.
        final SocketConnectorHandler connectorHandler =
                TCPNIOConnectorHandler.builder(transport.get()).processor(defaultFilterChain)
                        .build();
        final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future =
                new AsynchronousFutureResult<Connection, ResultHandler<? super Connection>>(handler);
        final CompletionHandlerAdapter cha = new CompletionHandlerAdapter(future);
        connectorHandler.connect(socketAddress, cha);
        return future;
    }

    /**
     * Returns the address of the Directory Server.
     *
     * @return The address of the Directory Server.
     */
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPConnectionFactory(");
        builder.append(getSocketAddress().toString());
        builder.append(')');
        return builder.toString();
    }

    TimeoutChecker getTimeoutChecker() {
        return timeoutChecker.get();
    }

    LDAPOptions getLDAPOptions() {
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
