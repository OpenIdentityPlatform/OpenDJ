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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap;

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
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.BindResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.ConnectionState;
import org.forgerock.opendj.ldap.spi.ExtendedResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.ResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.SearchResultLdapPromiseImpl;
import org.forgerock.util.Reject;

import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

/**
 * This class defines a pseudo-connection object that can be used for performing
 * internal operations directly against a {@code ServerConnection}
 * implementation.
 */
final class InternalConnection extends AbstractAsynchronousConnection {
    private final ServerConnection<Integer> serverConnection;
    /**
     * Tracks whether this connection has been closed and notifies the registered
     * connection event listeners when it is.
     * <p>
     * An internal connection has no transport, so it can never fail nor receive
     * an unsolicited notification: closure by the application is the only event
     * its listeners can observe.
     */
    private final ConnectionState state = new ConnectionState();
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

    @Override
    public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
        final int i = messageID.getAndIncrement();
        serverConnection.handleAbandon(i, request);
        return newSuccessfulLdapPromise((Void) null, i);
    }

    @Override
    public LdapPromise<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<AddRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleAdd(i, request, promise, promise);
        return promise;
    }

    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        state.addConnectionEventListener(listener);
    }

    @Override
    public LdapPromise<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final BindResultLdapPromiseImpl promise =
                newBindLdapPromise(i, request, null, intermediateResponseHandler);
        serverConnection.handleBind(i, 3, request, promise, promise);
        return promise;
    }

    @Override
    public void close(final UnbindRequest request, final String reason) {
        // Closing an already closed connection has no effect.
        if (state.notifyConnectionClosed()) {
            final int i = messageID.getAndIncrement();
            serverConnection.handleConnectionClosed(i, request);
        }
    }

    @Override
    public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<CompareRequest, CompareResult> promise =
                newCompareLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleCompare(i, request, promise, promise);
        return promise;
    }

    @Override
    public LdapPromise<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<DeleteRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleDelete(i, request, promise, promise);
        return promise;
    }

    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ExtendedResultLdapPromiseImpl<R> promise = newExtendedLdapPromise(i, request, intermediateResponseHandler,
                this);
        serverConnection.handleExtendedRequest(i, request, promise, promise);
        return promise;
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
    public LdapPromise<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<ModifyRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleModify(i, request, promise, promise);
        return promise;
    }

    @Override
    public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<ModifyDNRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleModifyDN(i, request, promise, promise);
        return promise;
    }

    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        state.removeConnectionEventListener(listener);
    }

    @Override
    public LdapPromise<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
        final int i = messageID.getAndIncrement();
        final SearchResultLdapPromiseImpl promise =
                newSearchLdapPromise(i, request, entryHandler, intermediateResponseHandler, this);
        serverConnection.handleSearch(i, request, promise, promise, promise);
        return promise;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + serverConnection + ')';
    }
}
