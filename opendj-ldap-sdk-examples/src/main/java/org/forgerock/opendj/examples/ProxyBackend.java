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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.examples;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
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
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.util.Utils.*;

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
 *     {@code
 * final RequestHandlerFactory<LDAPClientContext, RequestContext> proxyFactory =
 *     new RequestHandlerFactory<LDAPClientContext, RequestContext>() {
 *         @Override
 *         public ProxyBackend handleAccept(LDAPClientContext clientContext) throws LdapException {
 *             return new ProxyBackend(factory, bindFactory);
 *         }
 *     };
 * final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler = Connections
 *     .newServerConnectionFactory(proxyFactory);}
 * </pre>
 */
final class ProxyBackend implements RequestHandler<RequestContext> {

    private final ConnectionFactory bindFactory;
    private final ConnectionFactory factory;
    private volatile ProxiedAuthV2RequestControl proxiedAuthControl;

    ProxyBackend(final ConnectionFactory factory, final ConnectionFactory bindFactory) {
        this.factory = factory;
        this.bindFactory = bindFactory;
    }

    /** {@inheritDoc} */
    @Override
    public void handleAdd(final RequestContext requestContext, final AddRequest request,
        final IntermediateResponseHandler intermediateResponseHandler, final LdapResultHandler<Result> resultHandler) {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        addProxiedAuthControl(request);

        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection.addAsync(request, intermediateResponseHandler);
            }
        }).thenOnResult(resultHandler).thenOnException(resultHandler).thenAlways(close(connectionHolder));
    }

    /** {@inheritDoc} */
    @Override
    public void handleBind(final RequestContext requestContext, final int version, final BindRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final LdapResultHandler<BindResult> resultHandler) {

        if (request.getAuthenticationType() != BindRequest.AUTHENTICATION_TYPE_SIMPLE) {
            // TODO: SASL authentication not implemented.
            resultHandler.handleException(newLdapException(ResultCode.PROTOCOL_ERROR,
                    "non-SIMPLE authentication not supported: " + request.getAuthenticationType()));
        } else {
            // Authenticate using a separate bind connection pool, because
            // we don't want to change the state of the pooled connection.
            final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
            proxiedAuthControl = null;
            bindFactory.getConnectionAsync()
                    .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                        @Override
                        public Promise<BindResult, LdapException> apply(Connection connection) throws LdapException {
                            connectionHolder.set(connection);
                            return connection.bindAsync(request, intermediateResponseHandler);
                        }
                    }).thenOnResult(new ResultHandler<BindResult>() {
                        @Override
                        public final void handleResult(final BindResult result) {
                            proxiedAuthControl = ProxiedAuthV2RequestControl.newControl("dn:" + request.getName());
                            resultHandler.handleResult(result);
                        }
                    }).thenOnException(resultHandler).thenAlways(close(connectionHolder));
        }

    }

    /** {@inheritDoc} */
    @Override
    public void handleCompare(final RequestContext requestContext, final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LdapResultHandler<CompareResult> resultHandler) {
        addProxiedAuthControl(request);

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, CompareResult, LdapException>() {
            @Override
            public Promise<CompareResult, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection.compareAsync(request, intermediateResponseHandler);
            }
        }).thenOnResult(resultHandler).thenOnException(resultHandler).thenAlways(close(connectionHolder));
    }

    /** {@inheritDoc} */
    @Override
    public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LdapResultHandler<Result> resultHandler) {
        addProxiedAuthControl(request);

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection.deleteAsync(request, intermediateResponseHandler);
            }
        }).thenOnResult(resultHandler).thenOnException(resultHandler).thenAlways(close(connectionHolder));
    }

    /** {@inheritDoc} */
    @Override
    public <R extends ExtendedResult> void handleExtendedRequest(final RequestContext requestContext,
        final ExtendedRequest<R> request, final IntermediateResponseHandler intermediateResponseHandler,
        final LdapResultHandler<R> resultHandler) {
        if (CancelExtendedRequest.OID.equals(request.getOID())) {
            // TODO: not implemented.
            resultHandler.handleException(newLdapException(ResultCode.PROTOCOL_ERROR,
                "Cancel extended request operation not supported"));
        } else if (StartTLSExtendedRequest.OID.equals(request.getOID())) {
            // TODO: not implemented.
            resultHandler.handleException(newLdapException(ResultCode.PROTOCOL_ERROR,
                "StartTLS extended request operation not supported"));
        } else {
            // Forward all other extended operations.
            addProxiedAuthControl(request);

            final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
            factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, R, LdapException>() {
                @Override
                public Promise<R, LdapException> apply(Connection connection) throws LdapException {
                    connectionHolder.set(connection);
                    return connection.extendedRequestAsync(request, intermediateResponseHandler);
                }
            }).thenOnResult(resultHandler).thenOnException(resultHandler)
                .thenAlways(close(connectionHolder));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void handleModify(final RequestContext requestContext, final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LdapResultHandler<Result> resultHandler) {
        addProxiedAuthControl(request);

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection.modifyAsync(request, intermediateResponseHandler);
            }
        }).thenOnResult(resultHandler).thenOnException(resultHandler).thenAlways(close(connectionHolder));
    }

    /** {@inheritDoc} */
    @Override
    public void handleModifyDN(final RequestContext requestContext, final ModifyDNRequest request,
        final IntermediateResponseHandler intermediateResponseHandler, final LdapResultHandler<Result> resultHandler) {
        addProxiedAuthControl(request);

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection.modifyDNAsync(request, intermediateResponseHandler);
            }
        }).thenOnResult(resultHandler).thenOnException(resultHandler).thenAlways(close(connectionHolder));
    }

    /** {@inheritDoc} */
    @Override
    public void handleSearch(final RequestContext requestContext, final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler,
            final LdapResultHandler<Result> resultHandler) {
        addProxiedAuthControl(request);

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection.searchAsync(request, intermediateResponseHandler, entryHandler);
            }
        }).thenOnResult(resultHandler).thenOnException(resultHandler).thenAlways(close(connectionHolder));
    }

    private void addProxiedAuthControl(final Request request) {
        final ProxiedAuthV2RequestControl control = proxiedAuthControl;
        if (control != null) {
            request.addControl(control);
        }
    }

    private Runnable close(final AtomicReference<Connection> c) {
        return new Runnable() {
            @Override
            public void run() {
                closeSilently(c.get());
            }
        };
    }
}
