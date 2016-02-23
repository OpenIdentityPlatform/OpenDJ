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
     */
    private final Function<Request, Integer, NeverThrowsException> nextFactoryFunction;

    RequestLoadBalancer(final String loadBalancerName,
                        final Collection<? extends ConnectionFactory> factories,
                        final Options options,
                        final Function<Request, Integer, NeverThrowsException> nextFactoryFunction) {
        super(loadBalancerName, factories, options);
        this.nextFactoryFunction = nextFactoryFunction;
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
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.addAsync(request, intermediateResponseHandler);
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
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, BindResult, LdapException>() {
                @Override
                public Promise<BindResult, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.bindAsync(request, intermediateResponseHandler);
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
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, CompareResult, LdapException>() {
                @Override
                public Promise<CompareResult, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.compareAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public LdapPromise<Result> deleteAsync(
                final DeleteRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.deleteAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(
                final ExtendedRequest<R> request, final IntermediateResponseHandler intermediateResponseHandler) {
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, R, LdapException>() {
                @Override
                public Promise<R, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.extendedRequestAsync(request, intermediateResponseHandler);
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
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.modifyAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(
                final ModifyDNRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.modifyDNAsync(request, intermediateResponseHandler);
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
            return getConnectionAndSendRequest(request, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.searchAsync(request, intermediateResponseHandler, entryHandler);
                }
            });
        }

        private <R> LdapPromise<R> getConnectionAndSendRequest(
                final Request request, final AsyncFunction<Connection, R, LdapException> sendRequest) {
            if (state.isClosed()) {
                throw new IllegalStateException();
            }
            final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
            return getConnectionAsync(request)
                    .thenOnResult(new ResultHandler<Connection>() {
                        @Override
                        public void handleResult(final Connection connection) {
                            connectionHolder.set(connection);
                        }
                    })
                    .thenAsync(sendRequest)
                    .thenFinally(new Runnable() {
                        @Override
                        public void run() {
                            closeSilently(connectionHolder.get());
                        }
                    });
        }

        private LdapPromise<Connection> getConnectionAsync(final Request request) {
            try {
                final int index = nextFactoryFunction.apply(request);
                final ConnectionFactory factory = getMonitoredConnectionFactory(index);
                return LdapPromises.asPromise(factory.getConnectionAsync()
                                                     .thenOnException(new ExceptionHandler<LdapException>() {
                                                         @Override
                                                         public void handleException(final LdapException e) {
                                                             state.notifyConnectionError(false, e);
                                                         }
                                                     }));
            } catch (final LdapException e) {
                state.notifyConnectionError(false, e);
                return newFailedLdapPromise(e);
            }
        }
    }
}
