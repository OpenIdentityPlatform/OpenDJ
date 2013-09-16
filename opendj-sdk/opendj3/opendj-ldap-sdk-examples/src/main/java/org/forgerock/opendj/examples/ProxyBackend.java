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

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
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
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * A simple proxy back-end which forwards requests to a connection factory using
 * proxy authorization. Simple bind requests are performed on a separate
 * connection factory dedicated for authentication.
 * <p>
 * This is implementation is very simple and is only intended as an example:
 * <ul>
 * <li>It does not support SSL connections
 * <li>It does not support StartTLS
 * <li>It does not support Abandon or Cancel requests
 * <li>Very basic authentication and authorization support.
 * </ul>
 * <b>NOTE:</b> a proxy back-end is stateful due to its use of proxy
 * authorization. Therefore, a proxy backend must be created for each inbound
 * client connection. The following code illustrates how this may be achieved:
 *
 * <pre>
 * final RequestHandlerFactory&lt;LDAPClientContext, RequestContext&gt; proxyFactory =
 *         new RequestHandlerFactory&lt;LDAPClientContext, RequestContext&gt;() {
 *             &#064;Override
 *             public ProxyBackend handleAccept(LDAPClientContext clientContext)
 *                     throws ErrorResultException {
 *                 return new ProxyBackend(factory, bindFactory);
 *             }
 *         };
 * final ServerConnectionFactory&lt;LDAPClientContext, Integer&gt; connectionHandler = Connections
 *         .newServerConnectionFactory(proxyFactory);
 * </pre>
 */
final class ProxyBackend implements RequestHandler<RequestContext> {
    private abstract class AbstractRequestCompletionHandler<R extends Result, H extends ResultHandler<R>>
            implements ResultHandler<R> {
        final Connection connection;
        final H resultHandler;

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
        RequestCompletionHandler(final Connection connection, final ResultHandler<R> resultHandler) {
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

    private final ConnectionFactory bindFactory;
    private final ConnectionFactory factory;
    private volatile ProxiedAuthV2RequestControl proxiedAuthControl = null;

    ProxyBackend(final ConnectionFactory factory, final ConnectionFactory bindFactory) {
        this.factory = factory;
        this.bindFactory = bindFactory;
    }

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
                    "non-SIMPLE authentication not supported: " + request.getAuthenticationType()));
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
                                                    ProxiedAuthV2RequestControl.newControl("dn:"
                                                            + request.getName());
                                            resultHandler.handleResult(result);
                                        }
                                    };
                            connection
                                    .bindAsync(request, intermediateResponseHandler, innerHandler);
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
    public void handleCompare(final RequestContext requestContext, final CompareRequest request,
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
                        connection.compareAsync(request, intermediateResponseHandler, innerHandler);
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
                        connection.deleteAsync(request, intermediateResponseHandler, innerHandler);
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
                            connection.extendedRequestAsync(request, intermediateResponseHandler,
                                    innerHandler);
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
                        connection.modifyAsync(request, intermediateResponseHandler, innerHandler);
                    }

                };

        factory.getConnectionAsync(outerHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleModifyDN(final RequestContext requestContext, final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<Result> resultHandler) {
        addProxiedAuthControl(request);
        final ConnectionCompletionHandler<Result> outerHandler =
                new ConnectionCompletionHandler<Result>(resultHandler) {

                    @Override
                    public void handleResult(final Connection connection) {
                        final RequestCompletionHandler<Result> innerHandler =
                                new RequestCompletionHandler<Result>(connection, resultHandler);
                        connection
                                .modifyDNAsync(request, intermediateResponseHandler, innerHandler);
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
                        connection.searchAsync(request, intermediateResponseHandler, innerHandler);
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
