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
 *      Portions copyright 2011-2015 ForgeRock AS.
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
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.BindResultLdapPromiseImpl;
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
    private final List<ConnectionEventListener> listeners = new CopyOnWriteArrayList<>();
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

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
        final int i = messageID.getAndIncrement();
        serverConnection.handleAbandon(i, request);
        return newSuccessfulLdapPromise((Void) null, i);
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<AddRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleAdd(i, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        listeners.add(listener);
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final BindResultLdapPromiseImpl promise =
                newBindLdapPromise(i, request, null, intermediateResponseHandler, this);
        serverConnection.handleBind(i, 3, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public void close(final UnbindRequest request, final String reason) {
        final int i = messageID.getAndIncrement();
        serverConnection.handleConnectionClosed(i, request);
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<CompareRequest, CompareResult> promise =
                newCompareLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleCompare(i, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<DeleteRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleDelete(i, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ExtendedResultLdapPromiseImpl<R> promise = newExtendedLdapPromise(i, request, intermediateResponseHandler,
                this);
        serverConnection.handleExtendedRequest(i, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isClosed() {
        // FIXME: this should be true after close has been called.
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isValid() {
        // FIXME: this should be false if this connection is disconnected.
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<ModifyRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleModify(i, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int i = messageID.getAndIncrement();
        final ResultLdapPromiseImpl<ModifyDNRequest, Result> promise =
                newResultLdapPromise(i, request, intermediateResponseHandler, this);
        serverConnection.handleModifyDN(i, request, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        listeners.remove(listener);
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
        final int i = messageID.getAndIncrement();
        final SearchResultLdapPromiseImpl promise =
                newSearchLdapPromise(i, request, entryHandler, intermediateResponseHandler, this);
        serverConnection.handleSearch(i, request, promise, promise, promise);
        return promise;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("InternalConnection(");
        builder.append(serverConnection);
        builder.append(')');
        return builder.toString();
    }

}
