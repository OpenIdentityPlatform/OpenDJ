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
package com.forgerock.opendj.grizzly;

import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.promise.RuntimeExceptionHandler;

import io.reactivex.FlowableEmitter;

final class ServerConnectionAdaptor<C> {

    private final ServerConnection<C> adaptee;

    public ServerConnectionAdaptor(final ServerConnection<C> handler) {
        this.adaptee = checkNotNull(handler, "handler must not be null");
    }

    public void handleAdd(final C requestContext, final AddRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<Result> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleAdd(requestContext, request, resultAdapter, resultAdapter);
    }

    public void handleBind(final C requestContext, final int version, final BindRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<BindResult> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleBind(requestContext, version, request, resultAdapter, resultAdapter);
    }

    public void handleCompare(final C requestContext, final CompareRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<CompareResult> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleCompare(requestContext, request, resultAdapter, resultAdapter);
    }

    public void handleDelete(final C requestContext, final DeleteRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<Result> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleDelete(requestContext, request, resultAdapter, resultAdapter);
    }

    public <R extends ExtendedResult> void handleExtendedRequest(final C requestContext,
            final ExtendedRequest<R> request, final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<R> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleExtendedRequest(requestContext, request, resultAdapter, resultAdapter);
    }

    public void handleModify(final C requestContext, final ModifyRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<Result> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleModify(requestContext, request, resultAdapter, resultAdapter);
    }

    public void handleModifyDN(final C requestContext, final ModifyDNRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<Result> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleModifyDN(requestContext, request, resultAdapter, resultAdapter);
    }

    public void handleSearch(final C requestContext, final SearchRequest request,
            final FlowableEmitter<Response> response) {
        final ResultHandlerAdaptor<Result> resultAdapter = new ResultHandlerAdaptor<>(response);
        adaptee.handleSearch(requestContext, request, resultAdapter, resultAdapter, resultAdapter);
    }

    public void handleAbandon(C requestContext, final AbandonRequest request, final FlowableEmitter<Response> out) {
        adaptee.handleAbandon(requestContext, request);
    }

    /**
     * Forward all response received from handler to a {@link LdapResponse}.
     */
    private static final class ResultHandlerAdaptor<R extends Response>
            implements IntermediateResponseHandler, SearchResultHandler, LdapResultHandler<R>, RuntimeExceptionHandler {

        private final FlowableEmitter<Response> adaptee;

        ResultHandlerAdaptor(final FlowableEmitter<Response> emitter) {
            this.adaptee = emitter;
        }

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            adaptee.onNext(entry);
            return true;
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            adaptee.onNext(reference);
            return true;
        }

        @Override
        public boolean handleIntermediateResponse(final IntermediateResponse intermediateResponse) {
            adaptee.onNext(intermediateResponse);
            return true;
        }

        @Override
        public void handleResult(R result) {
            if (result != null) {
                adaptee.onNext(result);
            }
            adaptee.onComplete();
        }

        @Override
        public void handleRuntimeException(RuntimeException exception) {
            adaptee.onError(exception);
        }

        @Override
        public void handleException(LdapException exception) {
            adaptee.onError(exception);
        }
    }
}
