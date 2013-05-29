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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.RoundRobinLoadBalancingAlgorithm;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CancelExtendedRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * An LDAP load balancing proxy which forwards requests to one or more remote
 * Directory Servers. This is implementation is very simple and is only intended
 * as an example:
 * <ul>
 * <li>It does not support SSL connections
 * <li>It does not support StartTLS
 * <li>It does not support Abandon or Cancel requests
 * <li>Very basic authentication and authorization support.
 * </ul>
 * This example takes the following command line parameters:
 *
 * <pre>
 *  &lt;listenAddress> &lt;listenPort> &lt;proxyDN> &ltproxyPassword> &lt;remoteAddress1> &lt;remotePort1>
 *      [&lt;remoteAddress2> &lt;remotePort2> ...]
 * </pre>
 */
public final class Proxy {
    private static final class ProxyBackend implements RequestHandler<RequestContext> {
        private final ConnectionFactory factory;
        private final ConnectionFactory bindFactory;

        private ProxyBackend(final ConnectionFactory factory, final ConnectionFactory bindFactory) {
            this.factory = factory;
            this.bindFactory = bindFactory;
        }

        private abstract class AbstractRequestCompletionHandler
                <R extends Result, H extends ResultHandler<R>>
                implements ResultHandler<R> {
            final H resultHandler;
            final Connection connection;

            AbstractRequestCompletionHandler(final Connection connection, final H resultHandler) {
                this.connection = connection;
                this.resultHandler = resultHandler;
            }

            @Override
            public final void handleErrorResult(final ErrorResultException error) {
                connection.close();
                resultHandler.handleErrorResult(error);
            }

            @Override
            public final void handleResult(final R result) {
                connection.close();
                resultHandler.handleResult(result);
            }

        }

        private abstract class ConnectionCompletionHandler<R extends Result> implements
                ResultHandler<Connection> {
            private final ResultHandler<R> resultHandler;

            ConnectionCompletionHandler(final ResultHandler<R> resultHandler) {
                this.resultHandler = resultHandler;
            }

            @Override
            public final void handleErrorResult(final ErrorResultException error) {
                resultHandler.handleErrorResult(error);
            }

            @Override
            public abstract void handleResult(Connection connection);

        }

        private final class RequestCompletionHandler<R extends Result> extends
                AbstractRequestCompletionHandler<R, ResultHandler<R>> {
            RequestCompletionHandler(final Connection connection,
                    final ResultHandler<R> resultHandler) {
                super(connection, resultHandler);
            }
        }

        private final class SearchRequestCompletionHandler extends
                AbstractRequestCompletionHandler<Result, SearchResultHandler> implements
                SearchResultHandler {

            SearchRequestCompletionHandler(final Connection connection,
                    final SearchResultHandler resultHandler) {
                super(connection, resultHandler);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public final boolean handleEntry(final SearchResultEntry entry) {
                return resultHandler.handleEntry(entry);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public final boolean handleReference(final SearchResultReference reference) {
                return resultHandler.handleReference(reference);
            }

        }

        private volatile ProxiedAuthV2RequestControl proxiedAuthControl = null;

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleAdd(final RequestContext requestContext, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.addAsync(request, intermediateResponseHandler, innerHandler);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleBind(final RequestContext requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<BindResult> resultHandler) {

            if (request.getAuthenticationType() != BindRequest.AUTHENTICATION_TYPE_SIMPLE) {
                // TODO: SASL authentication not implemented.
                resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "non-SIMPLE authentication not supported: "
                                + request.getAuthenticationType()));
            } else {
                // Authenticate using a separate bind connection pool, because
                // we don't want to change the state of the pooled connection.
                final ConnectionCompletionHandler<BindResult> outerHandler =
                        new ConnectionCompletionHandler<BindResult>(resultHandler) {

                            @Override
                            public void handleResult(final Connection connection) {
                                final ResultHandler<BindResult> innerHandler =
                                        new ResultHandler<BindResult>() {

                                            @Override
                                            public final void handleErrorResult(
                                                    final ErrorResultException error) {
                                                connection.close();
                                                resultHandler.handleErrorResult(error);
                                            }

                                            @Override
                                            public final void handleResult(final BindResult result) {
                                                connection.close();
                                                proxiedAuthControl =
                                                        ProxiedAuthV2RequestControl
                                                                .newControl("dn:"
                                                                        + request.getName());
                                                resultHandler.handleResult(result);
                                            }
                                        };
                                connection.bindAsync(request, intermediateResponseHandler,
                                        innerHandler);
                            }

                        };

                proxiedAuthControl = null;
                bindFactory.getConnectionAsync(outerHandler);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleCompare(final RequestContext requestContext,
                final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<CompareResult> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<CompareResult> outerHandler =
                    new ConnectionCompletionHandler<CompareResult>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<CompareResult> innerHandler =
                                    new RequestCompletionHandler<CompareResult>(connection,
                                            resultHandler);
                            connection.compareAsync(request, intermediateResponseHandler,
                                    innerHandler);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.deleteAsync(request, intermediateResponseHandler,
                                    innerHandler);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(
                final RequestContext requestContext, final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<R> resultHandler) {
            if (request.getOID().equals(CancelExtendedRequest.OID)) {
                // TODO: not implemented.
                resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "Cancel extended request operation not supported"));
            } else if (request.getOID().equals(StartTLSExtendedRequest.OID)) {
                // TODO: not implemented.
                resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "StartTLS extended request operation not supported"));
            } else {
                // Forward all other extended operations.
                addProxiedAuthControl(request);

                final ConnectionCompletionHandler<R> outerHandler =
                        new ConnectionCompletionHandler<R>(resultHandler) {

                            @Override
                            public void handleResult(final Connection connection) {
                                final RequestCompletionHandler<R> innerHandler =
                                        new RequestCompletionHandler<R>(connection, resultHandler);
                                connection.extendedRequestAsync(request,
                                        intermediateResponseHandler, innerHandler);
                            }

                        };

                factory.getConnectionAsync(outerHandler);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModify(final RequestContext requestContext, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.modifyAsync(request, intermediateResponseHandler,
                                    innerHandler);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModifyDN(final RequestContext requestContext,
                final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.modifyDNAsync(request, intermediateResponseHandler,
                                    innerHandler);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleSearch(final RequestContext requestContext, final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final SearchRequestCompletionHandler innerHandler =
                                    new SearchRequestCompletionHandler(connection, resultHandler);
                            connection.searchAsync(request, intermediateResponseHandler,
                                    innerHandler);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        private void addProxiedAuthControl(final Request request) {
            final ProxiedAuthV2RequestControl control = proxiedAuthControl;
            if (control != null) {
                request.addControl(control);
            }
        }

    }

    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: listen address, listen port,
     *            remote address1, remote port1, remote address2, remote port2,
     *            ...
     */
    public static void main(final String[] args) {
        if (args.length < 6 || args.length % 2 != 0) {
            System.err.println("Usage: listenAddress listenPort "
                    + "proxyDN proxyPassword remoteAddress1 remotePort1 "
                    + "remoteAddress2 remotePort2 ...");
            System.exit(1);
        }

        // Parse command line arguments.
        final String localAddress = args[0];
        final int localPort = Integer.parseInt(args[1]);

        final String proxyDN = args[2];
        final String proxyPassword = args[3];

        // Create load balancer.
        final List<ConnectionFactory> factories = new LinkedList<ConnectionFactory>();
        final List<ConnectionFactory> bindFactories = new LinkedList<ConnectionFactory>();
        for (int i = 4; i < args.length; i += 2) {
            final String remoteAddress = args[i];
            final int remotePort = Integer.parseInt(args[i + 1]);

            factories.add(Connections.newFixedConnectionPool(Connections
                    .newAuthenticatedConnectionFactory(Connections
                            .newHeartBeatConnectionFactory(new LDAPConnectionFactory(remoteAddress,
                                    remotePort)), Requests.newSimpleBindRequest(proxyDN,
                            proxyPassword.toCharArray())), Integer.MAX_VALUE));
            bindFactories.add(Connections.newFixedConnectionPool(Connections
                    .newHeartBeatConnectionFactory(new LDAPConnectionFactory(remoteAddress,
                            remotePort)), Integer.MAX_VALUE));
        }
        final RoundRobinLoadBalancingAlgorithm algorithm =
                new RoundRobinLoadBalancingAlgorithm(factories);
        final RoundRobinLoadBalancingAlgorithm bindAlgorithm =
                new RoundRobinLoadBalancingAlgorithm(bindFactories);
        final ConnectionFactory factory = Connections.newLoadBalancer(algorithm);
        final ConnectionFactory bindFactory = Connections.newLoadBalancer(bindAlgorithm);

        // Create a server connection adapter.
        final ProxyBackend backend = new ProxyBackend(factory, bindFactory);
        final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                Connections.newServerConnectionFactory(backend);

        // Create listener.
        final LDAPListenerOptions options = new LDAPListenerOptions().setBacklog(4096);
        LDAPListener listener = null;
        try {
            listener = new LDAPListener(localAddress, localPort, connectionHandler, options);
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } catch (final IOException e) {
            System.out.println("Error listening on " + localAddress + ":" + localPort);
            e.printStackTrace();
        } finally {
            if (listener != null) {
                listener.close();
            }
        }
    }

    private Proxy() {
        // Not used.
    }
}
