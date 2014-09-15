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
 *      Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.forgerock.opendj.ldap.spi.AbstractLDAPFutureResultImpl;
import org.forgerock.opendj.ldap.spi.LDAPCompareFutureResultImpl;
import org.forgerock.opendj.ldap.spi.LDAPExtendedFutureResultImpl;
import org.forgerock.opendj.ldap.spi.LDAPFutureResultImpl;
import org.forgerock.opendj.ldap.spi.LDAPSearchFutureResultImpl;
import org.forgerock.util.Reject;

/**
 * This class defines a pseudo-connection object that can be used for performing
 * internal operations directly against a {@code ServerConnection}
 * implementation.
 */
final class InternalConnection extends AbstractAsynchronousConnection {
    private static final class InternalBindFutureResultImpl extends
            AbstractLDAPFutureResultImpl<BindResult> {
        private final BindRequest bindRequest;

        InternalBindFutureResultImpl(final int messageID, final BindRequest bindRequest,
                final IntermediateResponseHandler intermediateResponseHandler,
                final Connection connection) {
            super(messageID, intermediateResponseHandler, connection);
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
        protected BindResult newErrorResult(final ResultCode resultCode, final String diagnosticMessage,
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
    InternalConnection(final ServerConnection<Integer> serverConnection) {
        this.serverConnection = serverConnection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Void> abandonAsync(final AbandonRequest request) {
        final int i = messageID.getAndIncrement();
        serverConnection.handleAbandon(i, request);
        return FutureResultWrapper.newSuccessfulFutureResult((Void) null, i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request, intermediateResponseHandler, this);
        serverConnection.handleAdd(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final InternalBindFutureResultImpl future = new InternalBindFutureResultImpl(i, request,
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
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPCompareFutureResultImpl future = new LDAPCompareFutureResultImpl(i, request,
                intermediateResponseHandler, this);
        serverConnection.handleCompare(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request, intermediateResponseHandler, this);
        serverConnection.handleDelete(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPExtendedFutureResultImpl<R> future = new LDAPExtendedFutureResultImpl<R>(i, request,
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
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request, intermediateResponseHandler, this);
        serverConnection.handleModify(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request, intermediateResponseHandler, this);
        serverConnection.handleModifyDN(i, request, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
        final int i = messageID.getAndIncrement();
        final LDAPSearchFutureResultImpl future = new LDAPSearchFutureResultImpl(i, request, entryHandler,
                intermediateResponseHandler, this);
        serverConnection.handleSearch(i, request, future, future, future);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("InternalConnection(");
        builder.append(String.valueOf(serverConnection));
        builder.append(')');
        return builder.toString();
    }

}
