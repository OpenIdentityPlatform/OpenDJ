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
import org.forgerock.opendj.ldap.spi.LdapMessages;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRawMessage;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapResponseMessage;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
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

    private static final Attribute<ClientConnectionImpl> LDAP_CONNECTION_ATTR = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute("LDAPServerConnection");

    private final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>>,
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
                           ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>>,
                           LdapException> connectionHandlerFactory,
            final Options connectionOptions, final DecodeOptions options, final int maxPendingRequests) {
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.connectionOptions = connectionOptions;
        this.maxConcurrentRequests = maxPendingRequests;
    }

    @Override
    public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        configureConnection(connection, logger, connectionOptions);
        connection.configureBlocking(false);

        final ClientConnectionImpl clientContext = new ClientConnectionImpl(connection);
        LDAP_CONNECTION_ATTR.set(connection, clientContext);
        final ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>> requestHandler =
                connectionHandlerFactory.apply(clientContext);

        clientContext.read().flatMap(new Function<LdapRawMessage, Publisher<Void>, Exception>() {
            @Override
            public Publisher<Void> apply(final LdapRawMessage rawRequest) throws Exception {
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
        }).onCompleteDo(new Action() {
            @Override
            public void run() throws Exception {
                clientContext.notifyConnectionClosed(null);
            }
        }).subscribe();
        return ctx.getStopAction();
    }

    private final LdapResponseMessage toLdapResponseMessage(final LdapRawMessage rawRequest, final Result result) {
        switch (rawRequest.getMessageType()) {
        case OP_TYPE_ADD_REQUEST:
            return newResponseMessage(OP_TYPE_ADD_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_BIND_REQUEST:
            return newResponseMessage(OP_TYPE_BIND_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_COMPARE_REQUEST:
            return newResponseMessage(OP_TYPE_COMPARE_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_DELETE_REQUEST:
            return newResponseMessage(OP_TYPE_DELETE_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_EXTENDED_REQUEST:
            return newResponseMessage(OP_TYPE_EXTENDED_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_MODIFY_DN_REQUEST:
            return newResponseMessage(OP_TYPE_MODIFY_DN_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_MODIFY_REQUEST:
            return newResponseMessage(OP_TYPE_MODIFY_RESPONSE, rawRequest.getMessageId(), result);
        case OP_TYPE_SEARCH_REQUEST:
            return newResponseMessage(OP_TYPE_SEARCH_RESULT_DONE, rawRequest.getMessageId(), result);
        default:
            throw new IllegalArgumentException("Unknown request: " + rawRequest.getMessageType());
        }
    }

    private Function<Response, LdapResponseMessage, Exception> toLdapResponseMessage(final LdapRawMessage rawRequest) {
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
                throw new IllegalArgumentException();
            }
        };
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final ClientConnectionImpl clientContext = LDAP_CONNECTION_ATTR.get(ctx.getConnection());
        if (clientContext != null) {
            return clientContext.handleRead(ctx);
        }
        ctx.suspend();
        return ctx.getSuspendAction();
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx, final Throwable error) {
        final ClientConnectionImpl clientContext = LDAP_CONNECTION_ATTR.remove(ctx.getConnection());
        if (clientContext != null) {
            clientContext.exceptionOccurred(ctx, error);
        }
    }

    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        final ClientConnectionImpl clientContext = LDAP_CONNECTION_ATTR.remove(ctx.getConnection());
        if (clientContext != null) {
            clientContext.handleClose(ctx);
        }
        return ctx.getStopAction();
    }

    final class ClientConnectionImpl implements LDAPClientContext {

        final class GrizzlyBackpressureSubscription implements Subscription {
            private final AtomicLong pendingRequests = new AtomicLong();
            private Subscriber<? super LdapRawMessage> subscriber;
            volatile FilterChainContext ctx;

            GrizzlyBackpressureSubscription(Subscriber<? super LdapRawMessage> subscriber) {
                this.subscriber = subscriber;
                subscriber.onSubscribe(this);
            }

            NextAction handleRead(final FilterChainContext ctx) {
                final Subscriber<? super LdapRawMessage> sub = subscriber;
                if (sub == null) {
                    // Subscription cancelled. Stop reading
                    ctx.suspend();
                    return ctx.getSuspendAction();
                }
                sub.onNext((LdapRawMessage) ctx.getMessage());
                if (BackpressureHelper.produced(pendingRequests, 1) > 0) {
                    return ctx.getStopAction();
                }
                this.ctx = ctx;
                ctx.suspend();
                return ctx.getSuspendAction();
            }

            @Override
            public void request(long n) {
                if (BackpressureHelper.add(pendingRequests, n) == 0 && ctx != null) {
                    ctx.resumeNext();
                    ctx = null;
                }
            }

            public void onError(final Throwable error) {
                final Subscriber<? super LdapRawMessage> sub = subscriber;
                if (sub != null) {
                    subscriber = null;
                    sub.onError(error);
                }
            }

            public void onComplete() {
                final Subscriber<? super LdapRawMessage> sub = subscriber;
                if (sub != null) {
                    subscriber = null;
                    sub.onComplete();
                }
            }

            @Override
            public void cancel() {
                subscriber = null;
            }
        }

        private final Connection<?> connection;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);
        private final List<DisconnectListener> listeners = new LinkedList<>();
        private SaslServer saslServer;
        GrizzlyBackpressureSubscription upstream;

        private ClientConnectionImpl(final Connection<?> connection) {
            this.connection = connection;
        }

        NextAction handleRead(final FilterChainContext ctx)  {
            final GrizzlyBackpressureSubscription immutableRef = upstream;
            if (immutableRef != null) {
                return immutableRef.handleRead(ctx);
            }
            ctx.suspend();
            return ctx.getSuspendAction();
        }

        void exceptionOccurred(final FilterChainContext ctx, final Throwable error) {
            final GrizzlyBackpressureSubscription immutableRef = upstream;
            if (immutableRef != null) {
                immutableRef.onError(error);
            }
        }

        NextAction handleClose(final FilterChainContext ctx) {
            final GrizzlyBackpressureSubscription immutableRef = upstream;
            if (immutableRef != null) {
                immutableRef.onComplete();
            }
            return ctx.getStopAction();
        }

        Stream<LdapRawMessage> read() {
            return streamFromPublisher(new Publisher<LdapRawMessage>() {
                @Override
                public void subscribe(final Subscriber<? super LdapRawMessage> subscriber) {
                    if (upstream != null) {
                        return;
                    }
                    upstream = new GrizzlyBackpressureSubscription(subscriber);
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
                if (isFilterExists(SSLFilter.class)) {
                    return false;
                }
                SSLUtils.setSSLEngine(connection, sslEngine);
                installFilter(startTls ? new StartTLSFilter(new SSLFilter()) : new SSLFilter());
                return true;
            }
        }

        @Override
        public void enableSASL(final SaslServer saslServer) {
            Reject.ifNull(saslServer, "saslServer must not be null");
            synchronized (this) {
                if (isFilterExists(SaslFilter.class)) {
                    return;
                }
                this.saslServer = saslServer;
                installFilter(new SaslFilter(saslServer));
            }
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
                ssf = 1;
            } else if (SaslFilter.SASL_AUTH_CONFIDENTIALITY.equalsIgnoreCase(qop)) {
                final String negStrength = (String) saslServer.getNegotiatedProperty(Sasl.STRENGTH);
                if ("low".equalsIgnoreCase(negStrength)) {
                    ssf = 40;
                } else if ("medium".equalsIgnoreCase(negStrength)) {
                    ssf = 56;
                } else if ("high".equalsIgnoreCase(negStrength)) {
                    ssf = 128;
                }
                /*
                 * Treat anything else as if not security is provided and keep the server running
                 */
            }
            return ssf;
        }

        @Override
        public SSLSession getSSLSession() {
            final SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
            return sslEngine != null ? sslEngine.getSession() : null;
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

        private boolean isFilterExists(Class<?> filterKlass) {
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
        public void onDisconnect(DisconnectListener listener) {
            Reject.ifNull(listener, "listener must not be null");
            listeners.add(listener);
        }

        @Override
        public void disconnect() {
            if (isClosed.compareAndSet(false, true)) {
                try {
                    for (DisconnectListener listener : listeners) {
                        listener.connectionDisconnected(this, null, null);
                    }
                } finally {
                    closeConnection();
                }
            }
        }

        private void closeConnection() {
            if (upstream != null) {
                upstream.cancel();
                upstream = null;
            }
            connection.closeSilently();
        }

        @Override
        public void disconnect(final ResultCode resultCode, final String diagnosticMessage) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                sendUnsolicitedNotification(Responses.newGenericExtendedResult(resultCode)
                                                     .setOID(LDAP.OID_NOTICE_OF_DISCONNECTION)
                                                     .setDiagnosticMessage(diagnosticMessage));
                try {
                    for (DisconnectListener listener : listeners) {
                        listener.connectionDisconnected(this, resultCode, diagnosticMessage);
                    }
                } finally {
                    closeConnection();
                }
            }
        }

        private void notifyConnectionClosed(final LdapRawMessage unbindRequest) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                if (unbindRequest == null) {
                    doNotifySilentlyConnectionClosed(null);
                } else {
                    try {
                        LDAP.getReader(unbindRequest.getContent(), new DecodeOptions())
                                .readMessage(new AbstractLDAPMessageHandler() {
                                    @Override
                                    public void unbindRequest(int messageID, UnbindRequest unbindRequest)
                                            throws DecodeException, IOException {
                                        doNotifySilentlyConnectionClosed(unbindRequest);
                                    }
                                });
                    } catch (Exception e) {
                        doNotifySilentlyConnectionClosed(null);
                    }
                }
            }
        }

        private void doNotifySilentlyConnectionClosed(final UnbindRequest unbindRequest) {
            try {
                for (final DisconnectListener listener : listeners) {
                    try {
                        listener.connectionClosed(this, unbindRequest);
                    } catch (Exception e) {
                        // TODO: Log as a warning ?
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
                    for (final DisconnectListener listener : listeners) {
                        try {
                            listener.exceptionOccurred(this, error);
                        } catch (Exception e) {
                            // TODO: Log as a warning ?
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
            connection.write(LdapMessages.newResponseMessage(LDAP.OP_TYPE_EXTENDED_RESPONSE, 0, notification));
        }
    }
}
