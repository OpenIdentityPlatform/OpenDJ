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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl.newControl;
import static org.forgerock.opendj.rest2ldap.authz.Utils.close;
import static org.forgerock.opendj.rest2ldap.authz.Utils.handleConnectionFailure;
import static org.forgerock.services.context.SecurityContext.AUTHZID_DN;
import static org.forgerock.services.context.SecurityContext.AUTHZID_ID;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Inject an {@link AuthenticatedConnectionContext} following the information provided in the {@link SecurityContext}.
 * This connection will add a {@link ProxiedAuthV2RequestControl} to each LDAP requests.
 */
final class ProxiedAuthV2Filter implements Filter {

    private final ConnectionFactory connectionFactory;

    /**
     * Create a new ProxyAuthzFilter. The {@link Connection} contained in the injected
     * {@link AuthenticatedConnectionContext} will use a {@link ProxiedAuthV2RequestControl} to perform authorization.
     * The authorizationID of this control is computed for each request by resolving the authzTemplate against the
     * {@link SecurityContext}.
     *
     * @param connectionFactory
     *            Factory used to get the {@link Connection}
     * @throws NullPointerException
     *             If a parameter is null
     */
    ProxiedAuthV2Filter(final ConnectionFactory connectionFactory) {
        this.connectionFactory = checkNotNull(connectionFactory, "connectionFactory cannot be null");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
            final Handler next) {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        return connectionFactory
                .getConnectionAsync()
                .then(new Function<Connection, Connection, LdapException>() {
                    @Override
                    public Connection apply(Connection connection) throws LdapException {
                        connectionHolder.set(connection);
                        final Connection proxiedConnection = newProxiedConnection(
                                connection, resolveAuthorizationId(context.asContext(SecurityContext.class)));
                        connectionHolder.set(proxiedConnection);
                        return proxiedConnection;
                    }
                })
                .thenAsync(new AsyncFunction<Connection, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(Connection connection) {
                        return next.handle(new AuthenticatedConnectionContext(context, connection), request);
                    }
                }, handleConnectionFailure())
                .thenFinally(close(connectionHolder));
    }

    private String resolveAuthorizationId(SecurityContext securityContext) throws LdapException {
        Object candidate;
        candidate = securityContext.getAuthorization().get(AUTHZID_DN);
        if (candidate != null) {
            return "dn:" + candidate;
        }
        candidate = securityContext.getAuthorization().get(AUTHZID_ID);
        if (candidate != null) {
            return "u:" + candidate;
        }
        throw LdapException.newLdapException(ResultCode.AUTH_METHOD_NOT_SUPPORTED);
    }

    private Connection newProxiedConnection(Connection baseConnection, String authzId) {
        return new CachedReadConnectionDecorator(
                new ProxiedAuthConnectionDecorator(baseConnection, newControl(authzId)));
    }

    private static final class ProxiedAuthConnectionDecorator extends AbstractAsynchronousConnectionDecorator {

        private final Control proxiedAuthzControl;

        ProxiedAuthConnectionDecorator(Connection delegate, Control proxiedAuthzControl) {
            super(delegate);
            this.proxiedAuthzControl = proxiedAuthzControl;
        }

        @Override
        public LdapPromise<Result> addAsync(AddRequest request,
                IntermediateResponseHandler intermediateResponseHandler) {
            return delegate.addAsync(request.addControl(proxiedAuthzControl), intermediateResponseHandler);
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return delegate.compareAsync(request.addControl(proxiedAuthzControl), intermediateResponseHandler);
        }

        @Override
        public LdapPromise<Result> deleteAsync(final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return delegate.deleteAsync(request.addControl(proxiedAuthzControl), intermediateResponseHandler);
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return delegate.extendedRequestAsync(request.addControl(proxiedAuthzControl),
                    intermediateResponseHandler);
        }

        @Override
        public LdapPromise<Result> modifyAsync(final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return delegate.modifyAsync(request.addControl(proxiedAuthzControl), intermediateResponseHandler);
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return delegate.modifyDNAsync(request.addControl(proxiedAuthzControl), intermediateResponseHandler);
        }

        @Override
        public LdapPromise<Result> searchAsync(final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
            return delegate.searchAsync(request.addControl(proxiedAuthzControl), intermediateResponseHandler,
                    entryHandler);
        }
    }
}
