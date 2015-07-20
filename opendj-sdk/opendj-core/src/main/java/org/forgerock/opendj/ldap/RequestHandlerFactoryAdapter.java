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
 *      Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.INFO_CANCELED_BY_ABANDON_REQUEST;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_CANCELED_BY_CANCEL_REQUEST;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_CANCELED_BY_CLIENT_DISCONNECT;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_CANCELED_BY_CLIENT_ERROR;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_CANCELED_BY_SERVER_DISCONNECT;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_CLIENT_CONNECTION_CLOSING;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_CLIENT_DUPLICATE_MESSAGE_ID;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CancelExtendedRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

import org.forgerock.util.Reject;

/**
 * An adapter which converts a {@code RequestHandlerFactory} into a
 * {@code ServerConnectionFactory}.
 *
 * @param <C>
 *            The type of client context.
 */
final class RequestHandlerFactoryAdapter<C> implements ServerConnectionFactory<C, Integer> {
    /**
     * Request context implementation.
     */
    private static class RequestContextImpl<S extends Result, H extends LdapResultHandler<S>>
            implements RequestContext, LdapResultHandler<S> {

        /**
         * Adapter class which invokes cancel result handlers with correct
         * result type.
         */
        private static final class ExtendedResultHandlerHolder<R extends ExtendedResult> {
            private final ExtendedRequest<R> request;
            private final LdapResultHandler<R> resultHandler;

            private ExtendedResultHandlerHolder(final ExtendedRequest<R> request,
                    final LdapResultHandler<R> resultHandler) {
                this.request = request;
                this.resultHandler = resultHandler;
            }

            private void handleSuccess() {
                final R cancelResult =
                        request.getResultDecoder().newExtendedErrorResult(ResultCode.SUCCESS, "",
                                "");
                resultHandler.handleResult(cancelResult);
            }

            private void handleTooLate() {
                final R cancelResult =
                        request.getResultDecoder().newExtendedErrorResult(ResultCode.TOO_LATE, "",
                                "");
                resultHandler.handleException(newLdapException(cancelResult));
            }
        }

        private static enum RequestState {
            /** Request active, cancel requested. */
            CANCEL_REQUESTED,

            /** Result sent, was cancelled. */
            CANCELLED,

            /** Request active. */
            PENDING,

            /** Result sent, not cancelled. */
            RESULT_SENT,

            /** Request active, too late to cancel. */
            TOO_LATE;
        }

        protected final H resultHandler;
        /** These should be notified when a cancel request arrives, at most once. */
        private List<CancelRequestListener> cancelRequestListeners;
        private LocalizableMessage cancelRequestReason;
        /** These should be notified when the result is set. */
        private List<ExtendedResultHandlerHolder<?>> cancelResultHandlers;
        private final ServerConnectionImpl clientConnection;
        private final boolean isCancelSupported;
        private final int messageID;
        private boolean sendResult = true;
        private RequestState state = RequestState.PENDING;
        /** Cancellation state guarded by lock. */
        private final Object stateLock = new Object();

        protected RequestContextImpl(final ServerConnectionImpl clientConnection,
                final H resultHandler, final int messageID, final boolean isCancelSupported) {
            this.clientConnection = clientConnection;
            this.resultHandler = resultHandler;
            this.messageID = messageID;
            this.isCancelSupported = isCancelSupported;
        }

        /** {@inheritDoc} */
        @Override
        public void addCancelRequestListener(final CancelRequestListener listener) {
            Reject.ifNull(listener);

            boolean invokeImmediately = false;
            synchronized (stateLock) {
                switch (state) {
                case PENDING:
                    if (cancelRequestListeners == null) {
                        cancelRequestListeners = new LinkedList<>();
                    }
                    cancelRequestListeners.add(listener);
                    break;
                case CANCEL_REQUESTED:
                    // Signal immediately outside lock.
                    invokeImmediately = true;
                    break;
                case TOO_LATE:
                case RESULT_SENT:
                case CANCELLED:
                    /*
                     * No point in registering the callback since the request
                     * can never be cancelled now.
                     */
                    break;
                }
            }

            if (invokeImmediately) {
                listener.handleCancelRequest(cancelRequestReason);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void checkIfCancelled(final boolean signalTooLate) throws CancelledResultException {
            synchronized (stateLock) {
                switch (state) {
                case PENDING:
                    /* No cancel request, so no handlers, just switch state. */
                    if (signalTooLate) {
                        cancelRequestListeners = null;
                        state = RequestState.TOO_LATE;
                    }
                    break;
                case CANCEL_REQUESTED:
                    /*
                     * Don't change state: let the handler ack the cancellation
                     * request.
                     */
                    throw (CancelledResultException) newLdapException(ResultCode.CANCELLED,
                            cancelRequestReason.toString());
                case TOO_LATE:
                    /* Already too late. Nothing to do. */
                    break;
                case RESULT_SENT:
                case CANCELLED:
                    /*
                     * This should not happen - could throw an illegal state
                     * exception?
                     */
                    break;
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public int getMessageID() {
            return messageID;
        }

        /** {@inheritDoc} */
        @Override
        public void handleException(final LdapException error) {
            if (clientConnection.removePendingRequest(this)) {
                if (setResult(error.getResult())) {
                    /*
                     * FIXME: we must invoke the result handler even when
                     * abandoned so that chained result handlers may clean up,
                     * log, etc. We really need to signal that the result must
                     * not be sent to the client.
                     */
                }
                resultHandler.handleException(error);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleResult(final S result) {
            if (clientConnection.removePendingRequest(this)) {
                if (setResult(result)) {
                    /*
                     * FIXME: we must invoke the result handler even when
                     * abandoned so that chained result handlers may clean up,
                     * log, etc. We really need to signal that the result must
                     * not be sent to the client.
                     */
                }
                resultHandler.handleResult(result);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void removeCancelRequestListener(final CancelRequestListener listener) {
            Reject.ifNull(listener);

            synchronized (stateLock) {
                if (cancelRequestListeners != null) {
                    cancelRequestListeners.remove(listener);
                }
            }
        }

        private <R extends ExtendedResult> void cancel(final LocalizableMessage reason,
                final ExtendedRequest<R> cancelRequest, final LdapResultHandler<R> cancelResultHandler,
                final boolean sendResult) {
            Reject.ifNull(reason);

            if (!isCancelSupported) {
                if (cancelResultHandler != null) {
                    final Result result =
                            Responses.newGenericExtendedResult(ResultCode.CANNOT_CANCEL);
                    cancelResultHandler.handleException(newLdapException(result));
                }
                return;
            }

            List<CancelRequestListener> tmpListeners = null;
            boolean invokeResultHandler = false;
            boolean resultHandlerIsSuccess = false;

            synchronized (stateLock) {
                switch (state) {
                case PENDING:
                    /* Switch to CANCEL_REQUESTED state. */
                    cancelRequestReason = reason;
                    if (cancelResultHandler != null) {
                        cancelResultHandlers = new LinkedList<>();
                        cancelResultHandlers.add(new ExtendedResultHandlerHolder<R>(cancelRequest,
                                cancelResultHandler));
                    }
                    tmpListeners = cancelRequestListeners;
                    cancelRequestListeners = null;
                    state = RequestState.CANCEL_REQUESTED;
                    this.sendResult &= sendResult;
                    break;
                case CANCEL_REQUESTED:
                    /*
                     * Cancel already request so listeners already invoked.
                     */
                    if (cancelResultHandler != null) {
                        if (cancelResultHandlers == null) {
                            cancelResultHandlers = new LinkedList<>();
                        }
                        cancelResultHandlers.add(new ExtendedResultHandlerHolder<R>(cancelRequest,
                                cancelResultHandler));
                    }
                    break;
                case TOO_LATE:
                case RESULT_SENT:
                    /*
                     * Cannot cancel, so invoke result handler immediately
                     * outside of lock.
                     */
                    if (cancelResultHandler != null) {
                        invokeResultHandler = true;
                        resultHandlerIsSuccess = false;
                    }
                    break;
                case CANCELLED:
                    /*
                     * Multiple cancellation attempts. Clients should not do
                     * this, but the cancel will effectively succeed
                     * immediately, so invoke result handler immediately outside
                     * of lock.
                     */
                    if (cancelResultHandler != null) {
                        invokeResultHandler = true;
                        resultHandlerIsSuccess = true;
                    }
                    break;
                }
            }

            /* Invoke listeners outside of lock. */
            if (tmpListeners != null) {
                for (final CancelRequestListener listener : tmpListeners) {
                    listener.handleCancelRequest(reason);
                }
            }

            if (invokeResultHandler) {
                if (resultHandlerIsSuccess) {
                    final R result =
                            cancelRequest.getResultDecoder().newExtendedErrorResult(
                                    ResultCode.SUCCESS, "", "");
                    cancelResultHandler.handleResult(result);
                } else {
                    final Result result = Responses.newGenericExtendedResult(ResultCode.TOO_LATE);
                    cancelResultHandler.handleException(newLdapException(result));
                }
            }
        }

        /**
         * Sets the result associated with this request context and updates the
         * state accordingly.
         *
         * @param result
         *            The result.
         */
        private boolean setResult(final Result result) {
            List<ExtendedResultHandlerHolder<?>> tmpHandlers = null;
            boolean isCancelled = false;
            boolean maySendResult;

            synchronized (stateLock) {
                maySendResult = sendResult;

                switch (state) {
                case PENDING:
                case TOO_LATE:
                    /* Switch to appropriate final state. */
                    if (!result.getResultCode().equals(ResultCode.CANCELLED)) {
                        state = RequestState.RESULT_SENT;
                    } else {
                        state = RequestState.CANCELLED;
                    }
                    break;
                case CANCEL_REQUESTED:
                    /*
                     * Switch to appropriate final state and invoke any cancel
                     * request handlers.
                     */
                    if (!result.getResultCode().equals(ResultCode.CANCELLED)) {
                        state = RequestState.RESULT_SENT;
                    } else {
                        state = RequestState.CANCELLED;
                    }

                    isCancelled = (state == RequestState.CANCELLED);
                    tmpHandlers = cancelResultHandlers;
                    cancelResultHandlers = null;
                    break;
                case RESULT_SENT:
                case CANCELLED:
                    /*
                     * This should not happen - could throw an illegal state
                     * exception?
                     */
                    maySendResult = false; // Prevent sending multiple results.
                    break;
                }
            }

            /* Invoke handlers outside of lock. */
            if (tmpHandlers != null) {
                for (final ExtendedResultHandlerHolder<?> handler : tmpHandlers) {
                    if (isCancelled) {
                        handler.handleSuccess();
                    } else {
                        handler.handleTooLate();
                    }
                }
            }

            return maySendResult;
        }
    }

    /**
     * Search request context implementation.
     */
    private static final class SearchRequestContextImpl extends RequestContextImpl<Result, LdapResultHandler<Result>>
        implements SearchResultHandler {

        private final SearchResultHandler entryHandler;

        private SearchRequestContextImpl(final ServerConnectionImpl clientConnection,
            final SearchResultHandler entryHandler, final LdapResultHandler<Result> resultHandler, final int messageID,
            final boolean isCancelSupported) {
            super(clientConnection, resultHandler, messageID, isCancelSupported);
            this.entryHandler = entryHandler;
        }

        /** {@inheritDoc} */
        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            return entryHandler.handleEntry(entry);
        }

        /** {@inheritDoc} */
        @Override
        public boolean handleReference(final SearchResultReference reference) {
            return entryHandler.handleReference(reference);
        }
    }

    private static final class ServerConnectionImpl implements ServerConnection<Integer> {
        private final AtomicBoolean isClosed = new AtomicBoolean();
        private final ConcurrentHashMap<Integer, RequestContextImpl<?, ?>> pendingRequests = new ConcurrentHashMap<>();
        private final RequestHandler<RequestContext> requestHandler;

        private ServerConnectionImpl(final RequestHandler<RequestContext> requestHandler) {
            this.requestHandler = requestHandler;
        }

        /** {@inheritDoc} */
        @Override
        public void handleAbandon(final Integer messageID, final AbandonRequest request) {
            final RequestContextImpl<?, ?> abandonedRequest =
                    getPendingRequest(request.getRequestID());
            if (abandonedRequest != null) {
                final LocalizableMessage abandonReason =
                        INFO_CANCELED_BY_ABANDON_REQUEST.get(messageID);
                abandonedRequest.cancel(abandonReason, null, null, false);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleAdd(final Integer messageID, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            final RequestContextImpl<Result, LdapResultHandler<Result>> requestContext =
                    new RequestContextImpl<>(this, resultHandler, messageID, true);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleAdd(requestContext, request, intermediateResponseHandler,
                        requestContext);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleBind(final Integer messageID, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<BindResult> resultHandler) {
            final RequestContextImpl<BindResult, LdapResultHandler<BindResult>> requestContext =
                    new RequestContextImpl<>(this, resultHandler, messageID, false);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleBind(requestContext, version, request,
                        intermediateResponseHandler, requestContext);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleCompare(final Integer messageID, final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<CompareResult> resultHandler) {
            final RequestContextImpl<CompareResult, LdapResultHandler<CompareResult>> requestContext =
                    new RequestContextImpl<>(this, resultHandler, messageID, true);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleCompare(requestContext, request, intermediateResponseHandler,
                        requestContext);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleConnectionClosed(final Integer messageID, final UnbindRequest request) {
            final LocalizableMessage cancelReason = INFO_CANCELED_BY_CLIENT_DISCONNECT.get();
            doClose(cancelReason);
        }

        /** {@inheritDoc} */
        @Override
        public void handleConnectionDisconnected(final ResultCode resultCode, final String message) {
            final LocalizableMessage cancelReason = INFO_CANCELED_BY_SERVER_DISCONNECT.get();
            doClose(cancelReason);
        }

        /** {@inheritDoc} */
        @Override
        public void handleConnectionError(final Throwable error) {
            final LocalizableMessage cancelReason = INFO_CANCELED_BY_CLIENT_ERROR.get();
            doClose(cancelReason);
        }

        /** {@inheritDoc} */
        @Override
        public void handleDelete(final Integer messageID, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            final RequestContextImpl<Result, LdapResultHandler<Result>> requestContext =
                    new RequestContextImpl<>(this, resultHandler, messageID, true);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleDelete(requestContext, request, intermediateResponseHandler,
                        requestContext);
            }
        }

        /** {@inheritDoc} */
        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final Integer messageID,
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<R> resultHandler) {
            if (request.getOID().equals(CancelExtendedRequest.OID)) {
                // Decode the request as a cancel request.
                CancelExtendedRequest cancelRequest;
                try {
                    cancelRequest =
                            CancelExtendedRequest.DECODER.decodeExtendedRequest(request,
                                    new DecodeOptions());
                } catch (final DecodeException e) {
                    // Couldn't decode a cancel request.
                    resultHandler.handleException(newLdapException(ResultCode.PROTOCOL_ERROR, e
                            .getLocalizedMessage()));
                    return;
                }

                /*
                 * Register the request in the pending requests table. Even
                 * though this request cannot be cancelled, it is important to
                 * do this in order to monitor the number of pending operations.
                 */
                final RequestContextImpl<R, LdapResultHandler<R>> requestContext =
                        new RequestContextImpl<>(this, resultHandler, messageID, false);
                if (addPendingRequest(requestContext)) {
                    // Find and cancel the request.
                    final RequestContextImpl<?, ?> cancelledRequest =
                            getPendingRequest(cancelRequest.getRequestID());
                    if (cancelledRequest != null) {
                        final LocalizableMessage cancelReason =
                                INFO_CANCELED_BY_CANCEL_REQUEST.get(messageID);
                        cancelledRequest.cancel(cancelReason, request, requestContext, true);
                    } else {
                        /*
                         * Couldn't find the request. Invoke on context in order
                         * to remove pending request.
                         */
                        requestContext.handleException(newLdapException(ResultCode.NO_SUCH_OPERATION));
                    }
                }
            } else {
                // StartTLS requests cannot be cancelled.
                boolean isCancelSupported = !request.getOID().equals(StartTLSExtendedRequest.OID);
                final RequestContextImpl<R, LdapResultHandler<R>> requestContext =
                        new RequestContextImpl<>(this, resultHandler, messageID, isCancelSupported);

                if (addPendingRequest(requestContext)) {
                    requestHandler.handleExtendedRequest(requestContext, request,
                            intermediateResponseHandler, requestContext);
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleModify(final Integer messageID, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            final RequestContextImpl<Result, LdapResultHandler<Result>> requestContext =
                    new RequestContextImpl<>(this, resultHandler, messageID, true);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleModify(requestContext, request, intermediateResponseHandler,
                        requestContext);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleModifyDN(final Integer messageID, final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            final RequestContextImpl<Result, LdapResultHandler<Result>> requestContext =
                    new RequestContextImpl<>(this, resultHandler, messageID, true);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleModifyDN(requestContext, request, intermediateResponseHandler,
                        requestContext);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void handleSearch(final Integer messageID, final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler,
            final LdapResultHandler<Result> resultHandler) {
            final SearchRequestContextImpl requestContext =
                new SearchRequestContextImpl(this, entryHandler, resultHandler, messageID, true);
            if (addPendingRequest(requestContext)) {
                requestHandler.handleSearch(requestContext, request, intermediateResponseHandler, entryHandler,
                    requestContext);
            }
        }

        private boolean addPendingRequest(final RequestContextImpl<?, ?> requestContext) {
            final Integer messageID = requestContext.getMessageID();

            if (isClosed.get()) {
                final LocalizableMessage message = INFO_CLIENT_CONNECTION_CLOSING.get();
                requestContext.handleException(newLdapException(ResultCode.UNWILLING_TO_PERFORM,
                        message.toString()));
                return false;
            } else if (pendingRequests.putIfAbsent(messageID, requestContext) != null) {
                final LocalizableMessage message =
                        WARN_CLIENT_DUPLICATE_MESSAGE_ID.get(requestContext.getMessageID());
                requestContext.handleException(newLdapException(ResultCode.PROTOCOL_ERROR, message.toString()));
                return false;
            } else if (isClosed.get()) {
                /*
                 * A concurrent close may have already removed the pending
                 * request but it will have only been notified for cancellation.
                 */
                pendingRequests.remove(messageID);

                final LocalizableMessage message = INFO_CLIENT_CONNECTION_CLOSING.get();
                requestContext.handleException(newLdapException(ResultCode.UNWILLING_TO_PERFORM, message.toString()));
                return false;
            } else {
                /*
                 * If the connection is closed now then we just have to pay the
                 * cost of invoking the request in the request handler.
                 */
                return true;
            }
        }

        private void doClose(final LocalizableMessage cancelReason) {
            if (!isClosed.getAndSet(true)) {
                /*
                 * At this point if any pending requests are added then we may
                 * end up cancelling them, but this does not matter since
                 * addPendingRequest will fail the request immediately.
                 */
                final Iterator<RequestContextImpl<?, ?>> iterator =
                        pendingRequests.values().iterator();
                while (iterator.hasNext()) {
                    final RequestContextImpl<?, ?> pendingRequest = iterator.next();
                    pendingRequest.cancel(cancelReason, null, null, false);
                    iterator.remove();
                }
            }
        }

        /**
         * Returns the pending request context having the specified message ID.
         *
         * @param messageID
         *            The message ID associated with the request context.
         * @return The pending request context.
         */
        private RequestContextImpl<?, ?> getPendingRequest(final Integer messageID) {
            return pendingRequests.get(messageID);
        }

        /**
         * Deregister a request context once it has completed.
         *
         * @param requestContext
         *            The request context.
         * @return {@code true} if the request context was found and removed.
         */
        private boolean removePendingRequest(final RequestContextImpl<?, ?> requestContext) {
            return pendingRequests.remove(requestContext.getMessageID()) != null;
        }

    }

    /**
     * Adapts the provided request handler as a {@code ServerConnection}.
     *
     * @param requestHandler
     *            The request handler.
     * @return The server connection which will forward requests to the provided
     *         request handler.
     */
    static ServerConnection<Integer> adaptRequestHandler(
            final RequestHandler<RequestContext> requestHandler) {
        return new ServerConnectionImpl(requestHandler);
    }

    private final RequestHandlerFactory<C, RequestContext> factory;

    /**
     * Creates a new server connection factory using the provided request
     * handler factory.
     *
     * @param factory
     *            The request handler factory to be adapted into a server
     *            connection factory.
     */
    RequestHandlerFactoryAdapter(final RequestHandlerFactory<C, RequestContext> factory) {
        this.factory = factory;
    }

    /** {@inheritDoc} */
    @Override
    public ServerConnection<Integer> handleAccept(final C clientContext) throws LdapException {
        return adaptRequestHandler(factory.handleAccept(clientContext));
    }

}
