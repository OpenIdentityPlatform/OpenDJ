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
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package com.forgerock.opendj.ldap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.AbstractAsynchronousConnection;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
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
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;

import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.Validator;

/**
 * This class defines a pseudo-connection object that can be used for performing
 * internal operations directly against a {@code ServerConnection}
 * implementation.
 */
public final class InternalConnection extends AbstractAsynchronousConnection {
    private static final class InternalBindFutureResultImpl extends
            AbstractLDAPFutureResultImpl<BindResult> {
        private final BindRequest bindRequest;

        InternalBindFutureResultImpl(final int messageID, final BindRequest bindRequest,
                final ResultHandler<? super BindResult> resultHandler,
                final IntermediateResponseHandler intermediateResponseHandler,
                final Connection connection) {
            super(messageID, resultHandler, intermediateResponseHandler, connection);
            this.bindRequest = bindRequest;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("InternalBindFutureResultImpl(");
            sb.append("bindRequest = ");
            sb.append(bindRequest);
            super.toString(sb);
            sb.append(")");
            return sb.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        BindResult newErrorResult(final ResultCode resultCode, final String diagnosticMessage,
                final Throwable cause) {
            return Responses.newBindResult(resultCode).setDiagnosticMessage(diagnosticMessage)
                    .setCause(cause);
        }
    }

    private final ServerConnection<Integer> serverConnection;
    private final List<ConnectionEventListener> listeners =
            new CopyOnWriteArrayList<ConnectionEventListener>();
    private final AtomicInteger messageID = new AtomicInteger();

    /**
     * Sets the server connection associated with this internal connection.
     *
     * @param serverConnection
     *            The server connection.
     */
    public InternalConnection(final ServerConnection<Integer> serverConnection) {
        this.serverConnection = serverConnection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Void> abandonAsync(final AbandonRequest request) {
        final int i = messageID.getAndIncrement();
        serverConnection.handleAbandon(i, request);
        return new CompletedFutureResult<Void>((Void) null, i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(i, request, resultHandler, intermediateResponseHandler,
                        this);
        serverConnection.handleAdd(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        Validator.ensureNotNull(listener);
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super BindResult> resultHandler) {
        final int i = messageID.getAndIncrement();
        final InternalBindFutureResultImpl future =
                new InternalBindFutureResultImpl(i, request, resultHandler,
                        intermediateResponseHandler, this);
        serverConnection.handleBind(i, 3, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close(final UnbindRequest request, final String reason) {
        final int i = messageID.getAndIncrement();
        serverConnection.handleConnectionClosed(i, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super CompareResult> resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPCompareFutureResultImpl future =
                new LDAPCompareFutureResultImpl(i, request, resultHandler,
                        intermediateResponseHandler, this);
        serverConnection.handleCompare(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(i, request, resultHandler, intermediateResponseHandler,
                        this);
        serverConnection.handleDelete(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
            final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super R> resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPExtendedFutureResultImpl<R> future =
                new LDAPExtendedFutureResultImpl<R>(i, request, resultHandler,
                        intermediateResponseHandler, this);
        serverConnection.handleExtendedRequest(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        // FIXME: this should be true after close has been called.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid() {
        // FIXME: this should be false if this connection is disconnected.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(i, request, resultHandler, intermediateResponseHandler,
                        this);
        serverConnection.handleModify(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(i, request, resultHandler, intermediateResponseHandler,
                        this);
        serverConnection.handleModifyDN(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        Validator.ensureNotNull(listener);
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final SearchResultHandler resultHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPSearchFutureResultImpl future =
                new LDAPSearchFutureResultImpl(i, request, resultHandler,
                        intermediateResponseHandler, this);
        serverConnection.handleSearch(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("InternalConnection(");
        builder.append(String.valueOf(serverConnection));
        builder.append(')');
        return builder.toString();
    }

}
