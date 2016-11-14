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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import static com.forgerock.reactive.RxJavaStreams.*;
import static org.forgerock.opendj.grizzly.GrizzlyUtils.configureConnection;
import static org.forgerock.opendj.io.LDAP.*;
import static org.forgerock.opendj.ldap.spi.LdapMessages.newResponseMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.AbstractLDAPMessageHandler;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapResponseMessage;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLUtils;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.forgerock.reactive.Action;
import com.forgerock.reactive.Completable;
import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Stream;

import io.reactivex.internal.util.BackpressureHelper;

/**
 * Grizzly filter implementation for decoding LDAP requests and handling server side logic for SSL and SASL operations
 * over LDAP.
 */
final class LDAPServerFilter extends BaseFilter {

    private final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> connectionHandlerFactory;

    /**
     * Map of cipher phrases to effective key size (bits). Taken from the
     * following RFCs: 5289, 4346, 3268,4132 and 4162.
     */
    private static final Object[][] CIPHER_KEY_SIZES = {
        { "_WITH_AES_256_CBC_",      256 },
        { "_WITH_CAMELLIA_256_CBC_", 256 },
        { "_WITH_AES_256_GCM_",      256 },
        { "_WITH_3DES_EDE_CBC_",     112 },
        { "_WITH_AES_128_GCM_",      128 },
        { "_WITH_SEED_CBC_",         128 },
        { "_WITH_CAMELLIA_128_CBC_", 128 },
        { "_WITH_AES_128_CBC_",      128 },
        { "_WITH_IDEA_CBC_",         128 },
        { "_WITH_RC4_128_",          128 },
        { "_WITH_FORTEZZA_CBC_",     96 },
        { "_WITH_DES_CBC_",          56 },
        { "_WITH_RC4_56_",           56 },
        { "_WITH_RC2_CBC_40_",       40 },
        { "_WITH_DES_CBC_40_",       40 },
        { "_WITH_RC4_40_",           40 },
        { "_WITH_DES40_CBC_",        40 },
        { "_WITH_NULL_",             0 },
    };

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    private final int maxConcurrentRequests;
    private final Options connectionOptions;

    private DecodeOptions decodeOptions;

    /**
     * Creates a server filter with provided listener, options and max size of ASN1 element.
     *
     * @param listener
     *            listen for incoming connections
     * @param options
     *            control how to decode requests and responses
     * @param maxASN1ElementSize
     *            The maximum BER element size, or <code>0</code> to indicate that there is no limit.
     */
    LDAPServerFilter(
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> connectionHandlerFactory,
            final Options connectionOptions, final DecodeOptions options, final int maxPendingRequests) {
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.connectionOptions = connectionOptions;
        this.decodeOptions = options;
        this.maxConcurrentRequests = maxPendingRequests;
    }

    @Override
    public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        configureConnection(connection, logger, connectionOptions);
        connection.configureBlocking(false);

        final ClientConnectionImpl clientContext = new ClientConnectionImpl(connection);
        connection.setProcessor(FilterChainBuilder
                .stateless()
                .addAll((FilterChain) connection.getProcessor())
                .add(new LdapCodec(connectionOptions.get(LDAPListener.REQUEST_MAX_SIZE_IN_BYTES), decodeOptions) {
                    @Override
                    protected void onLdapCodecError(final FilterChainContext ctx, final Throwable error) {
                        clientContext.exceptionOccurred(ctx, error);
                    }
                })
                .add(clientContext)
                .build());

        final ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>> requestHandler =
                connectionHandlerFactory.apply(clientContext);
        clientContext.read().flatMap(new Function<LdapRequestEnvelope, Publisher<Void>, Exception>() {
            @Override
            public Publisher<Void> apply(final LdapRequestEnvelope rawRequest) throws Exception {
                if (rawRequest.getMessageType() == OP_TYPE_UNBIND_REQUEST) {
                    clientContext.notifyConnectionClosed(rawRequest);
                    return emptyStream();
                }
                Stream<Response> response;
                try {
                    response = requestHandler.handle(clientContext, rawRequest);
                } catch (Exception e) {
                    response = streamError(e);
                }
                return clientContext
                        .write(response.map(toLdapResponseMessage(rawRequest)))
                        .onErrorResumeWith(new Function<Throwable, Completable, Exception>() {
                            @Override
                            public Completable apply(final Throwable error) throws Exception {
                                if (!(error instanceof LdapException)) {
                                    // Unexpected error, propagate it.
                                    return completableError(error);
                                }
                                final LdapException exception = (LdapException) error;
                                return clientContext
                                        .write(singleFrom(toLdapResponseMessage(rawRequest, exception.getResult())));
                            }
                        });
            }
        }, maxConcurrentRequests).onErrorResumeWith(new Function<Throwable, Publisher<Void>, Exception>() {
            @Override
            public Publisher<Void> apply(Throwable error) throws Exception {
                clientContext.notifyErrorAndCloseSilently(error);
                // Swallow the error to prevent the subscribe() below to report it on the console.
                return emptyStream();
            }
        }).onComplete(new Action() {
            @Override
            public void run() throws Exception {
                clientContext.notifyConnectionClosed(null);
            }
        }).subscribe();
        return ctx.getStopAction();
    }

    private final LdapResponseMessage toLdapResponseMessage(final LdapRequestEnvelope rawRequest, final Result result) {
        final byte resultType = OP_TO_RESULT_TYPE[rawRequest.getMessageType()];
        if (resultType == 0) {
            throw new IllegalArgumentException("Unknown request: " + rawRequest.getMessageType());
        }
        return newResponseMessage(resultType, rawRequest.getMessageId(), result);
    }

    private Function<Response, LdapResponseMessage, Exception> toLdapResponseMessage(
            final LdapRequestEnvelope rawRequest) {
        return new Function<Response, LdapResponseMessage, Exception>() {
            @Override
            public LdapResponseMessage apply(final Response response) {
                if (response instanceof Result) {
                    return toLdapResponseMessage(rawRequest, (Result) response);
                }
                if (response instanceof IntermediateResponse) {
                    return newResponseMessage(OP_TYPE_INTERMEDIATE_RESPONSE, rawRequest.getMessageId(), response);
                }
                if (response instanceof SearchResultEntry) {
                    return newResponseMessage(OP_TYPE_SEARCH_RESULT_ENTRY, rawRequest.getMessageId(), response);
                }
                if (response instanceof SearchResultReference) {
                    return newResponseMessage(OP_TYPE_SEARCH_RESULT_REFERENCE, rawRequest.getMessageId(), response);
                }
                throw new IllegalArgumentException(
                        "Not implemented for a response of type " + (response != null ? response.getClass() : null));
            }
        };
    }

    final class ClientConnectionImpl extends BaseFilter implements LDAPClientContext {

        final class GrizzlyBackpressureSubscription implements Subscription {
            private final AtomicLong pendingRequests = new AtomicLong();
            private final Subscriber<? super LdapRequestEnvelope> subscriber;
            private FilterChainContext suspendedCtx;

            GrizzlyBackpressureSubscription(final Subscriber<? super LdapRequestEnvelope> subscriber) {
                this.subscriber = subscriber;
                subscriber.onSubscribe(this);
            }

            NextAction handleRead(final FilterChainContext ctx) {
                if (pendingRequests.get() == 1) {
                    subscriber.onNext((LdapRequestEnvelope) ctx.getMessage());
                    // This synchronized ensure that the context suspend is done atomically with the fact that
                    // pendingRequests is set to 0
                    synchronized (this) {
                        // Another request() might have add some pendingRequests in the mean time
                        if (pendingRequests.compareAndSet(1, 0)) {
                            ctx.suspend();
                            this.suspendedCtx = ctx;
                            return ctx.getSuspendAction();
                        }
                    }
                    return ctx.getStopAction();
                }
                if (BackpressureHelper.producedCancel(pendingRequests, 1) == Long.MIN_VALUE) {
                    ctx.suspend();
                    return ctx.getSuspendAction();
                }
                subscriber.onNext((LdapRequestEnvelope) ctx.getMessage());
                return ctx.getStopAction();
            }

            @Override
            public void request(final long n) {
                if (BackpressureHelper.addCancel(pendingRequests, n) == 0) {
                    // This synchronized, coupled to the previous one, ensure the atomicity by "waiting" until the
                    // context has been suspended
                    synchronized (this) {
                        // On startup, pendingRequests = 0 and suspendedCtx is null
                        if (suspendedCtx != null) {
                            suspendedCtx.resume();
                            suspendedCtx = null;
                        }
                    }
                }
            }

            public void onError(final Throwable error) {
                if (pendingRequests.getAndSet(Long.MIN_VALUE) != Long.MIN_VALUE) {
                    subscriber.onError(error);
                }
            }

            public void onComplete() {
                if (pendingRequests.getAndSet(Long.MIN_VALUE) != Long.MIN_VALUE) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                pendingRequests.set(Long.MIN_VALUE);
            }
        }

        private final Connection<?> connection;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);
        private final List<ConnectionEventListener> listeners = new LinkedList<>();
        private SaslServer saslServer;
        private GrizzlyBackpressureSubscription downstream;

        private ClientConnectionImpl(final Connection<?> connection) {
            this.connection = connection;
        }

        @Override
        public NextAction handleRead(final FilterChainContext ctx)  {
            final GrizzlyBackpressureSubscription immutableRef = downstream;
            if (immutableRef != null) {
                return immutableRef.handleRead(ctx);
            }
            ctx.suspend();
            return ctx.getSuspendAction();
        }

        @Override
        public void exceptionOccurred(final FilterChainContext ctx, final Throwable error) {
            final GrizzlyBackpressureSubscription immutableRef = downstream;
            if (immutableRef == null) {
                ctx.getConnection().closeSilently();
            } else {
                immutableRef.onError(error);
            }
        }

        @Override
        public NextAction handleClose(final FilterChainContext ctx) {
            if (isClosed.compareAndSet(false, true)) {
                final GrizzlyBackpressureSubscription immutableRef = downstream;
                if (immutableRef != null) {
                    downstream.onComplete();
                }
            }
            return ctx.getStopAction();
        }

        Stream<LdapRequestEnvelope> read() {
            return streamFromPublisher(new Publisher<LdapRequestEnvelope>() {
                @Override
                public void subscribe(final Subscriber<? super LdapRequestEnvelope> subscriber) {
                    if (downstream != null) {
                        return;
                    }
                    downstream = new GrizzlyBackpressureSubscription(subscriber);
                }
            });
        }

        Completable write(final Publisher<LdapResponseMessage> messages) {
            return newCompletable(new Completable.Emitter() {
                @Override
                public void subscribe(Completable.Subscriber e) {
                    messages.subscribe(new LdapResponseMessageWriter(connection, e));
                }
            });
        }

        @Override
        public boolean enableTLS(final SSLEngine sslEngine, final boolean startTls) {
            Reject.ifNull(sslEngine, "sslEngine must not be null");
            synchronized (this) {
                if (filterExists(SSLFilter.class)) {
                    return false;
                }
                SSLUtils.setSSLEngine(connection, sslEngine);
                installFilter(startTls ? new StartTLSFilter(new SSLFilter()) : new SSLFilter());
                return true;
            }
        }

        @Override
        public SSLSession getSSLSession() {
            final SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
            return sslEngine != null ? sslEngine.getSession() : null;
        }

        @Override
        public void enableSASL(final SaslServer saslServer) {
            Reject.ifNull(saslServer, "saslServer must not be null");
            synchronized (this) {
                if (filterExists(SaslFilter.class)) {
                    // FIXME: The current saslServer must be replaced with the new one
                    return;
                }
                this.saslServer = saslServer;
                installFilter(new SaslFilter(saslServer));
            }
        }

        @Override
        public SaslServer getSASLServer() {
            return saslServer;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return (InetSocketAddress) connection.getLocalAddress();
        }

        @Override
        public InetSocketAddress getPeerAddress() {
            return (InetSocketAddress) connection.getPeerAddress();
        }

        @Override
        public int getSecurityStrengthFactor() {
            return Math.max(getSSLSecurityStrengthFactor(), getSaslSecurityStrengthFactor());
        }

        private int getSSLSecurityStrengthFactor() {
            final SSLSession sslSession = getSSLSession();
            if (sslSession != null) {
                final String cipherString = sslSession.getCipherSuite();
                for (final Object[] cipher : CIPHER_KEY_SIZES) {
                    if (cipherString.contains((String) cipher[0])) {
                        return (Integer) cipher[1];
                    }
                }
            }
            return 0;
        }

        private int getSaslSecurityStrengthFactor() {
            if (saslServer == null) {
                return 0;
            }
            int ssf = 0;
            final String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
            if (SaslFilter.SASL_AUTH_INTEGRITY.equalsIgnoreCase(qop)) {
                return 1;
            } else if (SaslFilter.SASL_AUTH_CONFIDENTIALITY.equalsIgnoreCase(qop)) {
                final String negStrength = (String) saslServer.getNegotiatedProperty(Sasl.STRENGTH);
                if ("low".equalsIgnoreCase(negStrength)) {
                    return 40;
                } else if ("medium".equalsIgnoreCase(negStrength)) {
                    return 56;
                } else if ("high".equalsIgnoreCase(negStrength)) {
                    return 128;
                }
                /*
                 * Treat anything else as if not security is provided and keep the server running
                 */
            }
            return ssf;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("LDAPClientContext(");
            builder.append(getLocalAddress());
            builder.append(',');
            builder.append(getPeerAddress());
            builder.append(')');
            return builder.toString();
        }

        /**
         * Installs a new Grizzly filter (e.g. SSL/SASL) beneath the top-level LDAP filter.
         *
         * @param filter
         *            The filter to be installed.
         */
        private void installFilter(final Filter filter) {
            GrizzlyUtils.addFilterToConnection(filter, connection);
        }

        private boolean filterExists(Class<?> filterKlass) {
            synchronized (this) {
                final FilterChain currentFilterChain = (FilterChain) connection.getProcessor();
                for (final Filter filter : currentFilterChain) {
                    if (filterKlass.isAssignableFrom(filter.getClass())) {
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener) {
            Reject.ifNull(listener, "listener must not be null");
            listeners.add(listener);
        }

        @Override
        public void disconnect() {
            if (isClosed.compareAndSet(false, true)) {
                try {
                    for (ConnectionEventListener listener : listeners) {
                        listener.handleConnectionDisconnected(this, null, null);
                    }
                } finally {
                    closeConnection();
                }
            }
        }

        private void closeConnection() {
            if (downstream != null) {
                downstream.cancel();
                downstream = null;
            }
            connection.closeSilently();
        }

        @Override
        public void disconnect(final ResultCode resultCode, final String diagnosticMessage) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                sendUnsolicitedNotification(Responses.newGenericExtendedResult(resultCode)
                                                     .setOID(OID_NOTICE_OF_DISCONNECTION)
                                                     .setDiagnosticMessage(diagnosticMessage));
                try {
                    for (ConnectionEventListener listener : listeners) {
                        listener.handleConnectionDisconnected(this, resultCode, diagnosticMessage);
                    }
                } finally {
                    closeConnection();
                }
            }
        }

        private void notifyConnectionClosed(final LdapRequestEnvelope unbindRequest) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                if (unbindRequest == null) {
                    notifySilentlyConnectionClosed(null);
                } else {
                    try {
                        LDAP.getReader(unbindRequest.getContent(), new DecodeOptions())
                                .readMessage(new AbstractLDAPMessageHandler() {
                                    @Override
                                    public void unbindRequest(int messageID, UnbindRequest unbindRequest)
                                            throws DecodeException, IOException {
                                        notifySilentlyConnectionClosed(unbindRequest);
                                    }
                                });
                    } catch (Exception e) {
                        notifySilentlyConnectionClosed(null);
                    }
                }
            }
        }

        private void notifySilentlyConnectionClosed(final UnbindRequest unbindRequest) {
            try {
                for (final ConnectionEventListener listener : listeners) {
                    try {
                        listener.handleConnectionClosed(this, unbindRequest);
                    } catch (Exception e) {
                        logger.traceException(e);
                    }
                }
            } finally {
                closeConnection();
            }
        }

        private void notifyErrorAndCloseSilently(final Throwable error) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                try {
                    for (final ConnectionEventListener listener : listeners) {
                        try {
                            listener.handleConnectionError(this, error);
                        } catch (Exception e) {
                            logger.traceException(e);
                        }
                    }
                } finally {
                    closeConnection();
                }
            }
        }

        @Override
        public boolean isClosed() {
            return isClosed.get();
        }

        @Override
        public void sendUnsolicitedNotification(final ExtendedResult notification) {
            connection.write(newResponseMessage(OP_TYPE_EXTENDED_RESPONSE, 0, notification));
        }
    }
}
