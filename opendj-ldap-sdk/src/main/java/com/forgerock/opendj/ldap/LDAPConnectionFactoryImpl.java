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
import static com.forgerock.opendj.ldap.GrizzlyUtils.configureConnection;
import static com.forgerock.opendj.ldap.TimeoutChecker.TIMEOUT_CHECKER;
import static org.forgerock.opendj.ldap.CoreMessages.LDAP_CONNECTION_CONNECT_TIMEOUT;
import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
            CompletionHandler<org.glassfish.grizzly.Connection>, TimeoutEventListener {
        private final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future;
        private final long timeoutEndTime;

        private CompletionHandlerAdapter(
                final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future) {
            this.future = future;
            final long timeoutMS = getTimeout();
            this.timeoutEndTime = timeoutMS > 0 ? System.currentTimeMillis() + timeoutMS : 0;
            timeoutChecker.get().addListener(this);
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

            // Give up immediately if the future has been cancelled or timed out.
            if (future.isDone()) {
                timeoutChecker.get().removeListener(this);
                connection.close();
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
            timeoutChecker.get().removeListener(this);
            future.handleErrorResult(adaptConnectionException(throwable));
            releaseTransportAndTimeoutChecker();
        }

        @Override
        public void updated(final org.glassfish.grizzly.Connection result) {
            // Ignore this.
        }

        private LDAPConnection adaptConnection(final org.glassfish.grizzly.Connection<?> connection) {
            configureConnection(connection, options.isTCPNoDelay(), options.isKeepAlive(), options
                    .isReuseAddress(), options.getLinger());
            final LDAPConnection ldapConnection =
                    new LDAPConnection(connection, LDAPConnectionFactoryImpl.this);
            timeoutChecker.get().addListener(ldapConnection);
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
            timeoutChecker.get().removeListener(this);
            future.handleErrorResult(adaptConnectionException(t));
            connection.close();
        }

        private void onSuccess(final LDAPConnection connection) {
            timeoutChecker.get().removeListener(this);
            if (!future.tryHandleResult(connection)) {
                // The connection has been either cancelled or it has timed out.
                connection.close();
            }
        }

        @Override
        public long handleTimeout(final long currentTime) {
            if (timeoutEndTime == 0) {
                return 0;
            } else if (timeoutEndTime > currentTime) {
                return timeoutEndTime - currentTime;
            } else {
                future.handleErrorResult(newErrorResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                        LDAP_CONNECTION_CONNECT_TIMEOUT.get(getSocketAddress().toString(),
                                getTimeout()).toString()));
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
    private final LDAPOptions options;
    private final InetSocketAddress socketAddress;
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
    private final ReferenceCountedObject<TimeoutChecker>.Reference timeoutChecker = TIMEOUT_CHECKER
            .acquire();

    /**
     * Creates a new LDAP connection factory implementation which can be used to
     * create connections to the Directory Server at the provided address using
     * provided connection options.
     *
     * @param address
     *            The address of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     */
    public LDAPConnectionFactoryImpl(final InetSocketAddress address, final LDAPOptions options) {
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(options.getTCPNIOTransport());
        this.socketAddress = address;
        this.host = null;
        this.port = -1;
        this.options = new LDAPOptions(options);
        this.clientFilter =
                new LDAPClientFilter(new LDAPReader(this.options.getDecodeOptions()), 0);
        this.defaultFilterChain =
                FilterChainBuilder.stateless().add(new TransportFilter()).add(clientFilter).build();
    }

    /**
     * Creates a new LDAP connection factory implementation which can be used to
     * create connections to the Directory Server at the provided host and port
     * number using provided connection options.
     *
     * @param host
     *            The host name of the Directory Server to connect to.
     * @param port
     *            The port number of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     */
    public LDAPConnectionFactoryImpl(final String host, final int port, final LDAPOptions options) {
        this.transport = DEFAULT_TRANSPORT.acquireIfNull(options.getTCPNIOTransport());
        this.socketAddress = null;
        this.host = host;
        this.port = port;
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
        connectorHandler.connect(getSocketAddress(), new CompletionHandlerAdapter(future));
        return future;
    }

    /**
     * Returns the address of the Directory Server.
     *
     * @return The address of the Directory Server.
     */
    public InetSocketAddress getSocketAddress() {
        if (socketAddress != null) {
            return socketAddress;
        }
        return new InetSocketAddress(host, port);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPConnectionFactory(");
        if (socketAddress != null) {
            builder.append(socketAddress.toString());
        } else {
            builder.append(host);
            builder.append(':');
            builder.append(port);
        }
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
