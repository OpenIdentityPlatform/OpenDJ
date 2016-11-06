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
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.TestCaseUtils.loopbackWithDynamicPort;
import static org.forgerock.opendj.ldap.LDAPListener.LDAP_DECODE_OPTIONS;

import java.io.IOException;
import java.util.Collections;

import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.Options;

import com.forgerock.reactive.ServerConnectionFactoryAdapter;

/** Mocked request handler sketelon for tools test cases. */
class ToolLdapServer implements ServerConnectionFactory<LDAPClientContext, Integer> {

    static final String DIRECTORY_MANAGER = "cn=Directory Manager";

    static class ToolLdapServerConnection implements ServerConnection<Integer> {

        @Override
        public void handleAbandon(final Integer requestContext, final AbandonRequest request) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleBind(final Integer requestContext,
                               final int version,
                               final BindRequest request,
                               final IntermediateResponseHandler intermediateResponseHandler,
                               final LdapResultHandler<BindResult> resultHandler) {
            assertThat(request.getName()).isEqualTo(DIRECTORY_MANAGER);
            resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
        }

        @Override
        public void handleConnectionClosed(final Integer requestContext, final UnbindRequest request) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleConnectionDisconnected(final ResultCode resultCode, final String message) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleConnectionError(final Throwable error) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleAdd(final Integer requestContext,
                              final AddRequest request,
                              final IntermediateResponseHandler intermediateResponseHandler,
                              final LdapResultHandler<Result> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleCompare(final Integer requestContext,
                                  final CompareRequest request,
                                  final IntermediateResponseHandler intermediateResponseHandler,
                                  final LdapResultHandler<CompareResult> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleDelete(final Integer requestContext,
                                 final DeleteRequest request,
                                 final IntermediateResponseHandler intermediateResponseHandler,
                                 final LdapResultHandler<Result> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                                                                     final ExtendedRequest<R> request,
                                                                     final IntermediateResponseHandler responseHandler,
                                                                     final LdapResultHandler<R> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleModify(final Integer requestContext,
                                 final ModifyRequest request,
                                 final IntermediateResponseHandler intermediateResponseHandler,
                                 final LdapResultHandler<Result> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleModifyDN(final Integer requestContext,
                                   final ModifyDNRequest request,
                                   final IntermediateResponseHandler intermediateResponseHandler,
                                   final LdapResultHandler<Result> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }

        @Override
        public void handleSearch(final Integer requestContext,
                                 final SearchRequest request,
                                 final IntermediateResponseHandler intermediateResponseHandler,
                                 final SearchResultHandler entryHandler,
                                 final LdapResultHandler<Result> resultHandler) {
            throw new UnsupportedOperationException("This method should not have been called in the test.");
        }
    }

    private LDAPListener listener;

    @Override
    public ServerConnection<Integer> handleAccept(final LDAPClientContext clientContext) throws LdapException {
        return newServerConnection();
    }

    /** Must be overriding by client code. */
    ToolLdapServerConnection newServerConnection() {
        return new ToolLdapServerConnection();
    }

    void start() throws IOException {
        listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS), this));
    }

    void stop() {
        listener.close();
    }

    String getHostName() {
        return listener.getSocketAddresses().iterator().next().getHostName();
    }

    String getPort() {
        return Integer.toString(listener.getSocketAddresses().iterator().next().getPort());
    }
}
