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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;
import static org.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.ldap.AbstractAsynchronousConnection;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
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
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;

import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * LDAP connection implementation.
 */
final class LDAPConnection extends AbstractAsynchronousConnection implements Connection {
    /**
     * A dummy SSL client engine configurator as SSLFilter only needs client
     * config. This prevents Grizzly from needlessly using JVM defaults which
     * may be incorrectly configured.
     */
    private static final SSLEngineConfigurator DUMMY_SSL_ENGINE_CONFIGURATOR;
    static {
        try {
            DUMMY_SSL_ENGINE_CONFIGURATOR =
                    new SSLEngineConfigurator(new SSLContextBuilder().setTrustManager(
                            TrustManagers.distrustAll()).getSSLContext());
        } catch (GeneralSecurityException e) {
            // This should never happen.
            throw new IllegalStateException("Unable to create Dummy SSL Engine Configurator", e);
        }
    }

    private final AtomicBoolean bindOrStartTLSInProgress = new AtomicBoolean(false);
    private final org.glassfish.grizzly.Connection<?> connection;
    private final LDAPWriter ldapWriter = new LDAPWriter();
    private final AtomicInteger nextMsgID = new AtomicInteger(1);
    private final LDAPConnectionFactoryImpl factory;
    private final ConcurrentHashMap<Integer, AbstractLDAPFutureResultImpl<?>> pendingRequests =
            new ConcurrentHashMap<Integer, AbstractLDAPFutureResultImpl<?>>();
    private final Object stateLock = new Object();
    // Guarded by stateLock
    private Result connectionInvalidReason;
    private boolean failedDueToDisconnect = false;
    private boolean isClosed = false;
    private boolean isFailed = false;
    private List<ConnectionEventListener> listeners = null;

    LDAPConnection(final org.glassfish.grizzly.Connection<?> connection,
            final LDAPConnectionFactoryImpl factory) {
        this.connection = connection;
        this.factory = factory;
    }

    @Override
    public FutureResult<Void> abandonAsync(final AbandonRequest request) {
        /*
         * Need to be careful here since both abandonAsync and Future.cancel can
         * be called separately by the client application. Therefore
         * future.cancel() should abandon the request, and abandonAsync should
         * cancel the future. In addition, bind or StartTLS requests cannot be
         * abandoned.
         */
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                /*
                 * If there is a bind or startTLS in progress then it must be
                 * this request which is being abandoned. The following check
                 * will prevent it from happening.
                 */
                checkBindOrStartTLSInProgress();
            }
        } catch (final ErrorResultException e) {
            return new CompletedFutureResult<Void>(e);
        }

        // Remove the future associated with the request to be abandoned.
        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                pendingRequests.remove(request.getRequestID());
        if (pendingRequest == null) {
            /*
             * There has never been a request with the specified message ID or
             * the response has already been received and handled. We can ignore
             * this abandon request.
             */
            return new CompletedFutureResult<Void>((Void) null);
        }

        /*
         * This will cancel the future, but will also recursively invoke this
         * method. Since the pending request has been removed, there is no risk
         * of an infinite loop.
         */
        pendingRequest.cancel(false);

        /*
         * FIXME: there's a potential race condition here if a bind or startTLS
         * is initiated just after we removed the pending request.
         */
        return sendAbandonRequest(request);
    }

    private FutureResult<Void> sendAbandonRequest(final AbandonRequest request) {
        final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
        try {
            final int messageID = nextMsgID.getAndIncrement();
            ldapWriter.abandonRequest(asn1Writer, messageID, request);
            connection.write(asn1Writer.getBuffer(), null);
            return new CompletedFutureResult<Void>((Void) null, messageID);
        } catch (final IOException e) {
            return new CompletedFutureResult<Void>(adaptRequestIOException(e));
        } finally {
            asn1Writer.recycle();
        }
    }

    @Override
    public FutureResult<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.addRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        Validator.ensureNotNull(listener);
        final boolean notifyClose;
        final boolean notifyErrorOccurred;
        synchronized (stateLock) {
            notifyClose = isClosed;
            notifyErrorOccurred = isFailed;
            if (!isClosed) {
                if (listeners == null) {
                    listeners = new CopyOnWriteArrayList<ConnectionEventListener>();
                }
                listeners.add(listener);
            }
        }
        if (notifyErrorOccurred) {
            // Use the reason provided in the disconnect notification.
            listener.handleConnectionError(failedDueToDisconnect,
                    newErrorResult(connectionInvalidReason));
        }
        if (notifyClose) {
            listener.handleConnectionClosed();
        }
    }

    @Override
    public FutureResult<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super BindResult> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final BindClient context;
        try {
            context = request.createBindClient(StaticUtils.getHostName(factory.getSocketAddress()));
        } catch (final Exception e) {
            // FIXME: I18N need to have a better error message.
            // FIXME: Is this the best result code?
            final Result errorResult =
                    Responses.newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                            "An error occurred while creating a bind context").setCause(e);
            final ErrorResultException error = ErrorResultException.newErrorResult(errorResult);
            if (resultHandler != null) {
                resultHandler.handleErrorResult(error);
            }
            return new CompletedFutureResult<BindResult>(error, messageID);
        }

        final LDAPBindFutureResultImpl future =
                new LDAPBindFutureResultImpl(messageID, context, resultHandler,
                        intermediateResponseHandler, this);

        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                if (!pendingRequests.isEmpty()) {
                    future.setResultOrError(Responses.newBindResult(ResultCode.OPERATIONS_ERROR)
                            .setDiagnosticMessage(
                                    "There are other operations pending on this connection"));
                    return future;
                }
                if (!bindOrStartTLSInProgress.compareAndSet(false, true)) {
                    future.setResultOrError(Responses.newBindResult(ResultCode.OPERATIONS_ERROR)
                            .setDiagnosticMessage("Bind or Start TLS operation in progress"));
                    return future;
                }
                pendingRequests.put(messageID, future);
            }

            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    // Use the bind client to get the initial request instead of
                    // using the bind request passed to this method.
                    final GenericBindRequest initialRequest = context.nextBindRequest();
                    ldapWriter.bindRequest(asn1Writer, messageID, 3, initialRequest);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                bindOrStartTLSInProgress.set(false);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }

        return future;
    }

    @Override
    public void close(final UnbindRequest request, final String reason) {
        // FIXME: I18N need to internationalize this message.
        Validator.ensureNotNull(request);
        close(request, false, Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED)
                .setDiagnosticMessage(
                        "Connection closed by client" + (reason != null ? ": " + reason : "")));
    }

    @Override
    public FutureResult<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super CompareResult> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPCompareFutureResultImpl future =
                new LDAPCompareFutureResultImpl(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.compareRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public FutureResult<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.deleteRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
            final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super R> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPExtendedFutureResultImpl<R> future =
                new LDAPExtendedFutureResultImpl<R>(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                if (request.getOID().equals(StartTLSExtendedRequest.OID)) {
                    if (!pendingRequests.isEmpty()) {
                        future.setResultOrError(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.OPERATIONS_ERROR, "",
                                "There are pending operations on this connection"));
                        return future;
                    } else if (isTLSEnabled()) {
                        future.setResultOrError(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.OPERATIONS_ERROR, "",
                                "This connection is already TLS enabled"));
                        return future;
                    } else if (!bindOrStartTLSInProgress.compareAndSet(false, true)) {
                        future.setResultOrError(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.OPERATIONS_ERROR, "",
                                "Bind or Start TLS operation in progress"));
                        return future;
                    }
                } else {
                    checkBindOrStartTLSInProgress();
                }
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.extendedRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                bindOrStartTLSInProgress.set(false);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public boolean isClosed() {
        synchronized (stateLock) {
            return isClosed;
        }
    }

    @Override
    public boolean isValid() {
        synchronized (stateLock) {
            return isValid0();
        }
    }

    @Override
    public FutureResult<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.modifyRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPFutureResultImpl future =
                new LDAPFutureResultImpl(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.modifyDNRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        Validator.ensureNotNull(listener);
        synchronized (stateLock) {
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
    }

    @Override
    public FutureResult<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final SearchResultHandler resultHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final LDAPSearchFutureResultImpl future =
                new LDAPSearchFutureResultImpl(messageID, request, resultHandler,
                        intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, future);
            }
            try {
                final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
                try {
                    ldapWriter.searchRequest(asn1Writer, messageID, request);
                    connection.write(asn1Writer.getBuffer(), null);
                } finally {
                    asn1Writer.recycle();
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final ErrorResultException e) {
            future.adaptErrorResult(e.getResult());
        }
        return future;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPConnection(");
        builder.append(connection.getLocalAddress());
        builder.append(',');
        builder.append(connection.getPeerAddress());
        builder.append(')');
        return builder.toString();
    }

    long cancelExpiredRequests(final long currentTime) {
        final long timeout = factory.getLDAPOptions().getTimeout(TimeUnit.MILLISECONDS);
        if (timeout <= 0) {
            return 0;
        }

        long delay = timeout;
        for (final AbstractLDAPFutureResultImpl<?> future : pendingRequests.values()) {
            if (future == null || !future.checkForTimeout()) {
                continue;
            }
            final long diff = (future.getTimestamp() + timeout) - currentTime;
            if (diff > 0) {
                // Will expire in diff milliseconds.
                delay = Math.min(delay, diff);
            } else if (pendingRequests.remove(future.getRequestID()) == null) {
                // Result arrived at the same time.
                continue;
            } else if (future.isBindOrStartTLS()) {
                /*
                 * No other operations can be performed while a bind or StartTLS
                 * request is active, so we cannot time out the request. We
                 * therefore have a choice: either ignore timeouts for these
                 * operations, or enforce them but doing so requires
                 * invalidating the connection. We'll do the latter, since
                 * ignoring timeouts could cause the application to hang.
                 */
                DEBUG_LOG.fine("Failing bind or StartTLS request due to timeout "
                        + "(connection will be invalidated): " + future);
                final Result result =
                        Responses.newResult(ResultCode.CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                                LDAP_CONNECTION_BIND_OR_START_TLS_REQUEST_TIMEOUT.get(timeout)
                                        .toString());
                future.adaptErrorResult(result);

                // Fail the connection.
                final Result errorResult =
                        Responses.newResult(ResultCode.CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                                LDAP_CONNECTION_BIND_OR_START_TLS_CONNECTION_TIMEOUT.get(timeout)
                                        .toString());
                connectionErrorOccurred(errorResult);
            } else {
                DEBUG_LOG.fine("Failing request due to timeout: " + future);
                final Result result =
                        Responses.newResult(ResultCode.CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                                LDAP_CONNECTION_REQUEST_TIMEOUT.get(timeout).toString());
                future.adaptErrorResult(result);

                /*
                 * FIXME: there's a potential race condition here if a bind or
                 * startTLS is initiated just after we check the boolean. It
                 * seems potentially even more dangerous to send the abandon
                 * request while holding the state lock, since a blocking write
                 * could hang the application.
                 */
//                if (!bindOrStartTLSInProgress.get()) {
//                    sendAbandonRequest(newAbandonRequest(future.getRequestID()));
//                }
            }
        }
        return delay;
    }

    /**
     * Closes this connection, invoking event listeners as needed.
     *
     * @param unbindRequest
     *            The client provided unbind request if this is a client
     *            initiated close, or {@code null} if the connection has failed.
     * @param isDisconnectNotification
     *            {@code true} if this is a connection failure signalled by a
     *            server disconnect notification.
     * @param reason
     *            The result indicating why the connection was closed.
     */
    void close(final UnbindRequest unbindRequest, final boolean isDisconnectNotification,
            final Result reason) {
        final boolean notifyClose;
        final boolean notifyErrorOccurred;
        final List<ConnectionEventListener> tmpListeners;
        synchronized (stateLock) {
            if (isClosed) {
                // Already closed locally.
                return;
            } else if (unbindRequest != null) {
                // Local close.
                notifyClose = true;
                notifyErrorOccurred = false;
                isClosed = true;
                tmpListeners = listeners;
                listeners = null; // Prevent future invocations.
                if (connectionInvalidReason == null) {
                    connectionInvalidReason = reason;
                }
            } else if (isFailed) {
                // Already failed.
                return;
            } else {
                // Connection has failed and this is the first indication.
                notifyClose = false;
                notifyErrorOccurred = true;
                isFailed = true;
                failedDueToDisconnect = isDisconnectNotification;
                connectionInvalidReason = reason;
                tmpListeners = listeners; // Keep list for client close.
            }
        }

        // First abort all outstanding requests.
        for (final int requestID : pendingRequests.keySet()) {
            final AbstractLDAPFutureResultImpl<?> future = pendingRequests.remove(requestID);
            if (future != null) {
                future.adaptErrorResult(connectionInvalidReason);
            }
        }

        /*
         * If this is the final client initiated close then release close the
         * connection and release resources.
         */
        if (notifyClose) {
            final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
            try {
                ldapWriter.unbindRequest(asn1Writer, nextMsgID.getAndIncrement(), unbindRequest);
                connection.write(asn1Writer.getBuffer(), null);
            } catch (final Exception ignore) {
                /*
                 * Underlying channel probably blown up. Ignore all errors,
                 * including possibly runtime exceptions (see OPENDJ-672).
                 */
            } finally {
                asn1Writer.recycle();
            }
            factory.getTimeoutChecker().removeConnection(this);
            connection.closeSilently();
            factory.releaseTransportAndTimeoutChecker();
        }

        // Notify listeners.
        if (tmpListeners != null) {
            if (notifyErrorOccurred) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    // Use the reason provided in the disconnect notification.
                    listener.handleConnectionError(isDisconnectNotification, newErrorResult(reason));
                }
            }
            if (notifyClose) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    listener.handleConnectionClosed();
                }
            }
        }
    }

    int continuePendingBindRequest(final LDAPBindFutureResultImpl future)
            throws ErrorResultException {
        final int newMsgID = nextMsgID.getAndIncrement();
        synchronized (stateLock) {
            checkConnectionIsValid();
            pendingRequests.put(newMsgID, future);
        }
        return newMsgID;
    }

    LDAPOptions getLDAPOptions() {
        return factory.getLDAPOptions();
    }

    AbstractLDAPFutureResultImpl<?> getPendingRequest(final Integer messageID) {
        return pendingRequests.get(messageID);
    }

    void handleUnsolicitedNotification(final ExtendedResult result) {
        final List<ConnectionEventListener> tmpListeners;
        synchronized (stateLock) {
            tmpListeners = listeners;
        }
        if (tmpListeners != null) {
            for (final ConnectionEventListener listener : tmpListeners) {
                listener.handleUnsolicitedNotification(result);
            }
        }
    }

    /**
     * Installs a new Grizzly filter (e.g. SSL/SASL) beneath the top-level LDAP
     * filter.
     *
     * @param filter
     *            The filter to be installed.
     */
    void installFilter(final Filter filter) {
        synchronized (stateLock) {
            // Determine the index where the filter should be added.
            final FilterChain oldFilterChain = (FilterChain) connection.getProcessor();
            int filterIndex = oldFilterChain.size() - 1;
            if (filter instanceof SSLFilter) {
                // Beneath any ConnectionSecurityLayerFilters if present,
                // otherwise beneath the LDAP filter.
                for (int i = oldFilterChain.size() - 2; i >= 0; i--) {
                    if (!(oldFilterChain.get(i) instanceof ConnectionSecurityLayerFilter)) {
                        filterIndex = i + 1;
                        break;
                    }
                }
            }

            // Create the new filter chain.
            final FilterChain newFilterChain =
                    FilterChainBuilder.stateless().addAll(oldFilterChain).add(filterIndex, filter)
                            .build();
            connection.setProcessor(newFilterChain);
        }
    }

    /**
     * Indicates whether or not TLS is enabled on this connection.
     *
     * @return {@code true} if TLS is enabled on this connection, otherwise
     *         {@code false}.
     */
    boolean isTLSEnabled() {
        synchronized (stateLock) {
            final FilterChain currentFilterChain = (FilterChain) connection.getProcessor();
            for (final Filter filter : currentFilterChain) {
                if (filter instanceof SSLFilter) {
                    return true;
                }
            }
            return false;
        }
    }

    AbstractLDAPFutureResultImpl<?> removePendingRequest(final Integer messageID) {
        return pendingRequests.remove(messageID);
    }

    void setBindOrStartTLSInProgress(final boolean state) {
        bindOrStartTLSInProgress.set(state);
    }

    void startTLS(final SSLContext sslContext, final List<String> protocols,
            final List<String> cipherSuites, final CompletionHandler<SSLEngine> completionHandler)
            throws IOException {
        synchronized (stateLock) {
            if (isTLSEnabled()) {
                throw new IllegalStateException("TLS already enabled");
            }

            final SSLEngineConfigurator sslEngineConfigurator =
                    new SSLEngineConfigurator(sslContext, true, false, false);
            sslEngineConfigurator.setEnabledProtocols(protocols.isEmpty() ? null : protocols
                    .toArray(new String[protocols.size()]));
            sslEngineConfigurator.setEnabledCipherSuites(cipherSuites.isEmpty() ? null
                    : cipherSuites.toArray(new String[cipherSuites.size()]));
            final SSLFilter sslFilter =
                    new SSLFilter(DUMMY_SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
            installFilter(sslFilter);
            sslFilter.handshake(connection, completionHandler);
        }
    }

    private ErrorResultException adaptRequestIOException(final IOException e) {
        // FIXME: what other sort of IOExceptions can be thrown?
        // FIXME: Is this the best result code?
        final Result errorResult =
                Responses.newResult(ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
        connectionErrorOccurred(errorResult);
        return newErrorResult(errorResult);
    }

    private void checkBindOrStartTLSInProgress() throws ErrorResultException {
        if (bindOrStartTLSInProgress.get()) {
            throw newErrorResult(ResultCode.OPERATIONS_ERROR,
                    "Bind or Start TLS operation in progress");
        }
    }

    private void checkConnectionIsValid() throws ErrorResultException {
        if (!isValid0()) {
            if (failedDueToDisconnect) {
                /*
                 * Connection termination was triggered remotely. We don't want
                 * to blindly pass on the result code to requests since it could
                 * be confused for a genuine response. For example, if the
                 * disconnect contained the invalidCredentials result code then
                 * this could be misinterpreted as a genuine authentication
                 * failure for subsequent bind requests.
                 */
                throw newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN,
                        "Connection closed by server");
            } else {
                throw newErrorResult(connectionInvalidReason);
            }
        }
    }

    private void connectionErrorOccurred(final Result reason) {
        close(null, false, reason);
    }

    private boolean isValid0() {
        return !isFailed && !isClosed;
    }
}
