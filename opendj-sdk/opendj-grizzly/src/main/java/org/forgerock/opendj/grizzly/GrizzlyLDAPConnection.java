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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.grizzly;

import static com.forgerock.opendj.grizzly.GrizzlyMessages.LDAP_CONNECTION_BIND_OR_START_TLS_CONNECTION_TIMEOUT;
import static com.forgerock.opendj.grizzly.GrizzlyMessages.LDAP_CONNECTION_BIND_OR_START_TLS_REQUEST_TIMEOUT;
import static com.forgerock.opendj.grizzly.GrizzlyMessages.LDAP_CONNECTION_REQUEST_TIMEOUT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.REQUEST_TIMEOUT;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.ResultCode.CLIENT_SIDE_LOCAL_ERROR;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.TimeoutEventListener;
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
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.BindResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.ExtendedResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.LDAPConnectionImpl;
import org.forgerock.opendj.ldap.spi.ResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.SearchResultLdapPromiseImpl;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.time.Duration;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;

/** LDAP connection implementation. */
final class GrizzlyLDAPConnection implements LDAPConnectionImpl, TimeoutEventListener {
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

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private final AtomicBoolean bindOrStartTLSInProgress = new AtomicBoolean(false);
    private final org.glassfish.grizzly.Connection<?> connection;
    private final AtomicInteger nextMsgID = new AtomicInteger(1);
    private final GrizzlyLDAPConnectionFactory factory;
    private final ConcurrentHashMap<Integer, ResultLdapPromiseImpl<?, ?>> pendingRequests = new ConcurrentHashMap<>();
    private final long requestTimeoutMS;
    private final Object stateLock = new Object();
    /** Guarded by stateLock. */
    private Result connectionInvalidReason;
    private boolean failedDueToDisconnect;
    private boolean isClosed;
    private boolean isFailed;
    private List<ConnectionEventListener> listeners;

    /**
     * Create a LDAP Connection with provided Grizzly connection and LDAP
     * connection factory.
     *
     * @param connection
     *            actual connection
     * @param factory
     *            factory that provides LDAP connections
     */
    GrizzlyLDAPConnection(final org.glassfish.grizzly.Connection<?> connection,
            final GrizzlyLDAPConnectionFactory factory) {
        this.connection = connection;
        this.factory = factory;
        final Duration requestTimeout = factory.getLDAPOptions().get(REQUEST_TIMEOUT);
        this.requestTimeoutMS = requestTimeout.isUnlimited() ? 0 : requestTimeout.to(TimeUnit.MILLISECONDS);
    }

    @Override
    public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
        /*
         * Need to be careful here since both abandonAsync and Promise.cancel can
         * be called separately by the client application. Therefore
         * promise.cancel() should abandon the request, and abandonAsync should
         * cancel the promise. In addition, bind or StartTLS requests cannot be
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
        } catch (final LdapException e) {
            return newFailedLdapPromise(e);
        }

        // Remove the promise associated with the request to be abandoned.
        final ResultLdapPromiseImpl<?, ?> pendingRequest = pendingRequests.remove(request.getRequestID());
        if (pendingRequest == null) {
            /*
             * There has never been a request with the specified message ID or
             * the response has already been received and handled. We can ignore
             * this abandon request.
             */
            return newSuccessfulLdapPromise((Void) null);
        }

        /*
         * This will cancel the promise, but will also recursively invoke this
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

    private LdapPromise<Void> sendAbandonRequest(final AbandonRequest request) {
        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
        try {
            final int messageID = nextMsgID.getAndIncrement();
            writer.writeAbandonRequest(messageID, request);
            connection.write(writer.getASN1Writer().getBuffer(), null);
            return newSuccessfulLdapPromise((Void) null, messageID);
        } catch (final IOException e) {
            return newFailedLdapPromise(adaptRequestIOException(e));
        } finally {
            GrizzlyUtils.recycleWriter(writer);
        }
    }

    @Override
    public LdapPromise<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final ResultLdapPromiseImpl<AddRequest, Result> promise =
                newResultLdapPromise(messageID, request, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeAddRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
    }

    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        final boolean notifyClose;
        final boolean notifyErrorOccurred;
        synchronized (stateLock) {
            notifyClose = isClosed;
            notifyErrorOccurred = isFailed;
            if (!isClosed) {
                if (listeners == null) {
                    listeners = new CopyOnWriteArrayList<>();
                }
                listeners.add(listener);
            }
        }
        if (notifyErrorOccurred) {
            // Use the reason provided in the disconnect notification.
            listener.handleConnectionError(failedDueToDisconnect,
                    newLdapException(connectionInvalidReason));
        }
        if (notifyClose) {
            listener.handleConnectionClosed();
        }
    }

    @Override
    public LdapPromise<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final BindClient context;
        try {
            context = request.createBindClient(Connections.getHostString(factory.getSocketAddress()));
        } catch (final LdapException e) {
            return newFailedLdapPromise(e, messageID);
        }

        final BindResultLdapPromiseImpl promise =
                newBindLdapPromise(messageID, request, context, intermediateResponseHandler, this);

        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                if (!pendingRequests.isEmpty()) {
                    promise.setResultOrError(Responses.newBindResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                            "There are other operations pending on this connection"));
                    return promise;
                }
                if (!bindOrStartTLSInProgress.compareAndSet(false, true)) {
                    promise.setResultOrError(Responses.newBindResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                            "Bind or Start TLS operation in progress"));
                    return promise;
                }
                pendingRequests.put(messageID, promise);
            }

            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    // Use the bind client to get the initial request instead of
                    // using the bind request passed to this method.
                    final GenericBindRequest initialRequest = context.nextBindRequest();
                    writer.writeBindRequest(messageID, 3, initialRequest);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                bindOrStartTLSInProgress.set(false);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }

        return promise;
    }

    @Override
    public void close() {
        close(Requests.newUnbindRequest(), null);
    }

    @Override
    public void close(final UnbindRequest request, final String reason) {
        // FIXME: I18N need to internationalize this message.
        Reject.ifNull(request);
        close(request, false, Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED)
                .setDiagnosticMessage(reason != null ? reason : "Connection closed by client"));
    }

    @Override
    public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final ResultLdapPromiseImpl<CompareRequest, CompareResult> promise =
                newCompareLdapPromise(messageID, request, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeCompareRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
    }

    @Override
    public LdapPromise<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final ResultLdapPromiseImpl<DeleteRequest, Result> promise =
                newResultLdapPromise(messageID, request, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeDeleteRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
    }

    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final ExtendedResultLdapPromiseImpl<R> promise =
                newExtendedLdapPromise(messageID, request, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                if (StartTLSExtendedRequest.OID.equals(request.getOID())) {
                    if (!pendingRequests.isEmpty()) {
                        promise.setResultOrError(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.OPERATIONS_ERROR, "", "There are pending operations on this connection"));
                        return promise;
                    } else if (isTLSEnabled()) {
                        promise.setResultOrError(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.OPERATIONS_ERROR, "", "This connection is already TLS enabled"));
                        return promise;
                    } else if (!bindOrStartTLSInProgress.compareAndSet(false, true)) {
                        promise.setResultOrError(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.OPERATIONS_ERROR, "", "Bind or Start TLS operation in progress"));
                        return promise;
                    }
                } else {
                    checkBindOrStartTLSInProgress();
                }
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeExtendedRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                bindOrStartTLSInProgress.set(false);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
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
    public LdapPromise<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final ResultLdapPromiseImpl<ModifyRequest, Result> promise =
                newResultLdapPromise(messageID, request, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeModifyRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
    }

    @Override
    public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final ResultLdapPromiseImpl<ModifyDNRequest, Result> promise =
                newResultLdapPromise(messageID, request, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeModifyDNRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
    }

    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        Reject.ifNull(listener);
        synchronized (stateLock) {
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public LdapPromise<Result> searchAsync(final SearchRequest request,
        final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
        final int messageID = nextMsgID.getAndIncrement();
        final SearchResultLdapPromiseImpl promise =
                newSearchLdapPromise(messageID, request, entryHandler, intermediateResponseHandler, this);
        try {
            synchronized (stateLock) {
                checkConnectionIsValid();
                checkBindOrStartTLSInProgress();
                pendingRequests.put(messageID, promise);
            }
            try {
                final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
                try {
                    writer.writeSearchRequest(messageID, request);
                    connection.write(writer.getASN1Writer().getBuffer(), null);
                } finally {
                    GrizzlyUtils.recycleWriter(writer);
                }
            } catch (final IOException e) {
                pendingRequests.remove(messageID);
                throw adaptRequestIOException(e);
            }
        } catch (final LdapException e) {
            promise.adaptErrorResult(e.getResult());
        }
        return promise;
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

    @Override
    public long handleTimeout(final long currentTime) {
        if (requestTimeoutMS <= 0) {
            return 0;
        }

        long delay = requestTimeoutMS;
        for (final ResultLdapPromiseImpl<?, ?> promise : pendingRequests.values()) {
            if (promise == null || !promise.checkForTimeout()) {
                continue;
            }
            final long diff = (promise.getTimestamp() + requestTimeoutMS) - currentTime;
            if (diff > 0) {
                // Will expire in diff milliseconds.
                delay = Math.min(delay, diff);
            } else if (pendingRequests.remove(promise.getRequestID()) == null) {
                // Result arrived at the same time.
                continue;
            } else if (promise.isBindOrStartTLS()) {
                /*
                 * No other operations can be performed while a bind or StartTLS
                 * request is active, so we cannot time out the request. We
                 * therefore have a choice: either ignore timeouts for these
                 * operations, or enforce them but doing so requires
                 * invalidating the connection. We'll do the latter, since
                 * ignoring timeouts could cause the application to hang.
                 */
                logger.debug(LocalizableMessage.raw("Failing bind or StartTLS request due to timeout %s"
                        + "(connection will be invalidated): ", promise));
                final Result result = Responses.newResult(ResultCode.CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                        LDAP_CONNECTION_BIND_OR_START_TLS_REQUEST_TIMEOUT.get(requestTimeoutMS).toString());
                promise.adaptErrorResult(result);

                // Fail the connection.
                final Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                        LDAP_CONNECTION_BIND_OR_START_TLS_CONNECTION_TIMEOUT.get(requestTimeoutMS).toString());
                connectionErrorOccurred(errorResult);
            } else {
                logger.debug(LocalizableMessage.raw("Failing request due to timeout: %s", promise));
                final Result result = Responses.newResult(ResultCode.CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                        LDAP_CONNECTION_REQUEST_TIMEOUT.get(requestTimeoutMS).toString());
                promise.adaptErrorResult(result);

                /*
                 * FIXME: there's a potential race condition here if a bind or
                 * startTLS is initiated just after we check the boolean. It
                 * seems potentially even more dangerous to send the abandon
                 * request while holding the state lock, since a blocking write
                 * could hang the application.
                 */
                // if (!bindOrStartTLSInProgress.get()) {
                // sendAbandonRequest(newAbandonRequest(promise.getRequestID()));
                // }
            }
        }
        return delay;
    }

    @Override
    public long getTimeout() {
        return requestTimeoutMS;
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
            final ResultLdapPromiseImpl<?, ?> promise = pendingRequests.remove(requestID);
            if (promise != null) {
                promise.adaptErrorResult(connectionInvalidReason);
            }
        }

        /*
         * If this is the final client initiated close then release close the
         * connection and release resources.
         */
        if (notifyClose) {
            final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
            try {
                writer.writeUnbindRequest(nextMsgID.getAndIncrement(), unbindRequest);
                connection.write(writer.getASN1Writer().getBuffer(), null);
            } catch (final Exception ignore) {
                /*
                 * Underlying channel probably blown up. Ignore all errors,
                 * including possibly runtime exceptions (see OPENDJ-672).
                 */
            } finally {
                GrizzlyUtils.recycleWriter(writer);
            }
            factory.getTimeoutChecker().removeListener(this);
            connection.closeSilently();
            factory.releaseTransportAndTimeoutChecker();
        }

        // Notify listeners.
        if (tmpListeners != null) {
            if (notifyErrorOccurred) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    // Use the reason provided in the disconnect notification.
                    listener.handleConnectionError(isDisconnectNotification, newLdapException(reason));
                }
            }
            if (notifyClose) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    listener.handleConnectionClosed();
                }
            }
        }
    }

    int continuePendingBindRequest(final BindResultLdapPromiseImpl promise) throws LdapException {
        final int newMsgID = nextMsgID.getAndIncrement();
        synchronized (stateLock) {
            checkConnectionIsValid();
            pendingRequests.put(newMsgID, promise);
        }
        return newMsgID;
    }

    Options getLDAPOptions() {
        return factory.getLDAPOptions();
    }

    ResultLdapPromiseImpl<?, ?> getPendingRequest(final Integer messageID) {
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
            GrizzlyUtils.addFilterToConnection(filter, connection);
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

    ResultLdapPromiseImpl<?, ?> removePendingRequest(final Integer messageID) {
        return pendingRequests.remove(messageID);
    }

    void setBindOrStartTLSInProgress(final boolean state) {
        bindOrStartTLSInProgress.set(state);
    }

    @Override
    public Promise<Void, LdapException> enableTLS(
            final SSLContext sslContext,
            final List<String> sslEnabledProtocols,
            final List<String> sslEnabledCipherSuites) {
        final PromiseImpl<Void, LdapException> promise = PromiseImpl.create();
        final EmptyCompletionHandler<SSLEngine> completionHandler = new EmptyCompletionHandler<SSLEngine>() {
            @Override
            public void completed(final SSLEngine result) {
                promise.handleResult(null);
            }

            @Override
            public void failed(final Throwable throwable) {
                final Result errorResult = newResult(CLIENT_SIDE_LOCAL_ERROR)
                        .setCause(throwable).setDiagnosticMessage("SSL handshake failed");
                connectionErrorOccurred(errorResult);
                promise.handleException(newLdapException(errorResult));
            }
        };

        try {
            startTLS(sslContext, sslEnabledProtocols, sslEnabledCipherSuites, completionHandler);
        } catch (final IOException e) {
            completionHandler.failed(e);
        }
        return promise;
    }

    void startTLS(final SSLContext sslContext, final List<String> protocols, final List<String> cipherSuites,
                  final CompletionHandler<SSLEngine> completionHandler) throws IOException {
        synchronized (stateLock) {
            if (isTLSEnabled()) {
                throw new IllegalStateException("TLS already enabled");
            }

            final SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(sslContext, true, false,
                    false);
            sslEngineConfigurator.setEnabledProtocols(protocols.isEmpty() ? null : protocols
                    .toArray(new String[protocols.size()]));
            sslEngineConfigurator.setEnabledCipherSuites(cipherSuites.isEmpty() ? null : cipherSuites
                    .toArray(new String[cipherSuites.size()]));
            final SSLFilter sslFilter = new SSLFilter(DUMMY_SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
            installFilter(sslFilter);
            sslFilter.handshake(connection, completionHandler);
        }
    }

    private LdapException adaptRequestIOException(final IOException e) {
        // FIXME: what other sort of IOExceptions can be thrown?
        // FIXME: Is this the best result code?
        final Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
        connectionErrorOccurred(errorResult);
        return newLdapException(errorResult);
    }

    private void checkBindOrStartTLSInProgress() throws LdapException {
        if (bindOrStartTLSInProgress.get()) {
            throw newLdapException(ResultCode.OPERATIONS_ERROR, "Bind or Start TLS operation in progress");
        }
    }

    private void checkConnectionIsValid() throws LdapException {
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
                throw newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN, "Connection closed by server");
            } else {
                throw newLdapException(connectionInvalidReason);
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
