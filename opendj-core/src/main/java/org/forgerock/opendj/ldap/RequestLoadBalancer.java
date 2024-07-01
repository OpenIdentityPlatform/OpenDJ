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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.spi.LdapPromises.newFailedLdapPromise;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.ConnectionState;
import org.forgerock.opendj.ldap.spi.LdapPromises;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * A request based load balancer which load balances individual requests based on properties of the request, such as
 * the target DN.
 * <p>
 * Implementations should override the method {@code getInitialConnectionFactoryIndex()} in order to provide the policy
 * for selecting the first connection factory to use for each request.
 */
final class RequestLoadBalancer extends LoadBalancer {
    /**
     * A function which returns the index of the first connection factory which should be used in order to satisfy the
     * next request. Implementations may base the decision on properties of the provided request, such as the target DN,
     * whether the request is a read or update request, etc.
     * Additionally a new request is returned with the index, because some modifications may be needed (removal of
     * a control for example) but the original request should not be modified. The new request must be used
     * for the actual LDAP operation.
     */
    private final Function<Request, PartitionedRequest, NeverThrowsException> nextFactoryFunction;
    /** A function which is called after a request is terminated. */
    private final Function<Integer, Void, NeverThrowsException> endOfRequestFunction;

    RequestLoadBalancer(final String loadBalancerName,
                        final Collection<? extends ConnectionFactory> factories,
                        final Options options,
                        final Function<Request, PartitionedRequest, NeverThrowsException> nextFactoryFunction,
                        final Function<Integer, Void, NeverThrowsException> endOfRequestFunction) {
        super(loadBalancerName, factories, options);
        this.nextFactoryFunction = nextFactoryFunction;
        this.endOfRequestFunction = endOfRequestFunction;
    }

    @Override
    public final Connection getConnection() throws LdapException {
        return new ConnectionImpl();
    }

    @Override
    public final Promise<Connection, LdapException> getConnectionAsync() {
        return newResultPromise((Connection) new ConnectionImpl());
    }

    private class ConnectionImpl extends AbstractAsynchronousConnection {
        private final ConnectionState state = new ConnectionState();

        @Override
        public String toString() {
            return getLoadBalancerName() + "Connection";
        }

        @Override
        public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
            // We cannot possibly route these correctly, so just drop them.
            return LdapPromises.newSuccessfulLdapPromise(null);
        }

        @Override
        public LdapPromise<Result> addAsync(
                final AddRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, Result, LdapException>() {
                        @Override
                        public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                            return connection.addAsync((AddRequest) connectionContext.getRequest(),
                                                       intermediateResponseHandler);
                        }
                    });
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            state.addConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<BindResult> bindAsync(
                final BindRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext, new AsyncFunction<Connection, BindResult, LdapException>() {
                @Override
                public Promise<BindResult, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.bindAsync((BindRequest) connectionContext.getRequest(),
                                                intermediateResponseHandler);
                }
            });
        }

        @Override
        public void close(final UnbindRequest request, final String reason) {
            state.notifyConnectionClosed();
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(
                final CompareRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, CompareResult, LdapException>() {
                        @Override
                        public Promise<CompareResult, LdapException> apply(final Connection connection)
                                throws LdapException {
                            return connection.compareAsync((CompareRequest) connectionContext.getRequest(),
                                                           intermediateResponseHandler);
                        }
                    });
        }

        @Override
        public LdapPromise<Result> deleteAsync(
                final DeleteRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, Result, LdapException>() {
                        @Override
                        public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                            return connection.deleteAsync((DeleteRequest) connectionContext.getRequest(),
                                                          intermediateResponseHandler);
                        }
                    });
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(
                final ExtendedRequest<R> request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, R, LdapException>() {
                        @Override
                        public Promise<R, LdapException> apply(final Connection connection) throws LdapException {
                            return connection.extendedRequestAsync((ExtendedRequest<R>) connectionContext.getRequest(),
                                                                   intermediateResponseHandler);
                        }
                    });
        }

        @Override
        public boolean isClosed() {
            return state.isClosed();
        }

        @Override
        public boolean isValid() {
            return state.isValid();
        }

        @Override
        public LdapPromise<Result> modifyAsync(
                final ModifyRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, Result, LdapException>() {
                        @Override
                        public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                            return connection.modifyAsync((ModifyRequest) connectionContext.getRequest(),
                                                          intermediateResponseHandler);
                        }
                    });
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(
                final ModifyDNRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, Result, LdapException>() {
                        @Override
                        public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                            return connection.modifyDNAsync((ModifyDNRequest) connectionContext.getRequest(),
                                                            intermediateResponseHandler);
                        }
                    });
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            state.removeConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<Result> searchAsync(
                final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler entryHandler) {
            final ConnectionContext connectionContext = getConnection(request);
            return executeRequest(connectionContext,
                    new AsyncFunction<Connection, Result, LdapException>() {
                        @Override
                        public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                            return connection.searchAsync((SearchRequest) connectionContext.getRequest(),
                                                          intermediateResponseHandler,
                                                          entryHandler);
                        }
                    });
        }

        private ConnectionContext getConnection(final Request request) {
            if (state.isClosed()) {
                throw new IllegalStateException();
            }
            try {
                final PartitionedRequest partitionedRequest = nextFactoryFunction.apply(request);
                final ConnectionFactory factory = getMonitoredConnectionFactory(partitionedRequest.getServerIndex());
                return new ConnectionContext(
                        LdapPromises.asPromise(factory.getConnectionAsync()
                                .thenOnException(new ExceptionHandler<LdapException>() {
                                    @Override
                                    public void handleException(final LdapException e) {
                                        state.notifyConnectionError(false, e);
                                    }
                                })), partitionedRequest);
            } catch (final LdapException e) {
                state.notifyConnectionError(false, e);
                LdapPromise<Connection> failedLdapPromise = newFailedLdapPromise(e);
                return new ConnectionContext(failedLdapPromise, new PartitionedRequest(request, -1));
            }
        }

        private <R> LdapPromise<R> executeRequest(final ConnectionContext connectionContext,
                final AsyncFunction<Connection, R, LdapException> requestSender) {
            return connectionContext.getConnectionPromise()
                    .thenOnResult(new ResultHandler<Connection>() {
                        @Override
                        public void handleResult(final Connection connection) {
                            connectionContext.setConnection(connection);
                        }
                    })
                    .thenAsync(requestSender)
                    .thenFinally(new Runnable() {
                        @Override
                        public void run() {
                            closeSilently(connectionContext.getConnection());
                            endOfRequestFunction.apply(connectionContext.getServerIndex());
                        }
                    });
        }
    }

    /** Utility class for a request and a server index. */
    static class PartitionedRequest {
        private final Request request;
        /** The index of server chosen for the connection. */
        private final int serverIndex;

        PartitionedRequest(Request request, int serverIndex) {
            this.serverIndex = serverIndex;
            this.request = request;
        }

        Request getRequest() {
            return request;
        }

        int getServerIndex() {
            return serverIndex;
        }
    }

    /** Utility class to hold together parameters for a request and the connection used to perform it. */
    private static class ConnectionContext {
        private final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        private final LdapPromise<Connection> connectionPromise;
        private final PartitionedRequest partitionedRequest;

        ConnectionContext(LdapPromise<Connection> connectionPromise, PartitionedRequest partitionedRequest) {
            this.partitionedRequest = partitionedRequest;
            this.connectionPromise = connectionPromise;
        }

        Connection getConnection() {
            return connectionHolder.get();
        }

        void setConnection(Connection connection) {
            connectionHolder.set(connection);
        }

        LdapPromise<Connection> getConnectionPromise() {
            return connectionPromise;
        }

        int getServerIndex() {
            return partitionedRequest.getServerIndex();
        }

        Request getRequest() {
            return partitionedRequest.getRequest();
        }
    }
}
