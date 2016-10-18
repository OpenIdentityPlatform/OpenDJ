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
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
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
import org.glassfish.grizzly.CloseReason;
import org.glassfish.grizzly.CompletionHandler;
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

import com.forgerock.reactive.Completable;
import com.forgerock.reactive.Completable.Emitter;
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
                           LdapException> connectionHandler;

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
            Function<LDAPClientContext,
                     ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>>,
                     LdapException> connectionHandler,
            final Options connectionOptions, final DecodeOptions options, final int maxPendingRequests) {
        this.connectionHandler = connectionHandler;
        this.connectionOptions = connectionOptions;
        this.maxConcurrentRequests = maxPendingRequests;
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx, final Throwable error) {
        LDAP_CONNECTION_ATTR.remove(ctx.getConnection()).upstream.onError(error);
    }

    @Override
    public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        configureConnection(connection, logger, connectionOptions);
        final ClientConnectionImpl clientContext = new ClientConnectionImpl(connection);
        LDAP_CONNECTION_ATTR.set(connection, clientContext);
        final ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>> handler = connectionHandler
                .apply(clientContext);

        streamFromPublisher(clientContext).flatMap(new Function<LdapRawMessage, Publisher<Integer>, Exception>() {
            @Override
            public Publisher<Integer> apply(final LdapRawMessage rawRequest) throws Exception {
                return handler
                        .handle(clientContext, rawRequest)
                        .flatMap(new Function<Stream<Response>, Publisher<Integer>, Exception>() {
                            @Override
                            public Publisher<Integer> apply(final Stream<Response> response) {
                                return clientContext.write(response.onErrorResumeWith(toErrorMessage(rawRequest))
                                                                   .map(toResponseMessage(rawRequest))).toSingle(1);
                            }
                        });
            }
        }, maxConcurrentRequests).subscribe();
        return ctx.getStopAction();
    }

    private Function<Throwable, Publisher<Response>, Exception> toErrorMessage(final LdapRawMessage rawRequest) {
        return new Function<Throwable, Publisher<Response>, Exception>() {
            @Override
            public Publisher<Response> apply(Throwable error) throws Exception {
                if (!(error instanceof LdapException)) {
                    // Propagate error
                    return streamError(error);
                }

                final Result result = ((LdapException) error).getResult();
                switch (rawRequest.getMessageType()) {
                case OP_TYPE_BIND_REQUEST:
                    if (result instanceof BindResult) {
                        return streamFrom((Response) result);
                    }
                    return streamFrom((Response) populateNewResultFromResult(
                            Responses.newBindResult(result.getResultCode()), result));
                case OP_TYPE_COMPARE_REQUEST:
                    if (result instanceof CompareResult) {
                        return streamFrom((Response) result);
                    }
                    return streamFrom((Response) populateNewResultFromResult(
                            Responses.newCompareResult(result.getResultCode()), result));
                case OP_TYPE_EXTENDED_REQUEST:
                    if (result instanceof ExtendedResult) {
                        return streamFrom((Response) result);
                    }
                    return streamFrom((Response) populateNewResultFromResult(
                            Responses.newGenericExtendedResult(result.getResultCode()), result));
                default:
                    return streamFrom((Response) result);
                }
            }
        };
    }

    private Result populateNewResultFromResult(final Result newResult,
            final Result result) {
        newResult.setDiagnosticMessage(result.getDiagnosticMessage());
        newResult.setMatchedDN(result.getMatchedDN());
        newResult.setCause(result.getCause());
        for (final Control control : result.getControls()) {
            newResult.addControl(control);
        }
        return newResult;
    }

    private Function<Response, LdapResponseMessage, Exception> toResponseMessage(final LdapRawMessage rawRequest) {
        return new Function<Response, LdapResponseMessage, Exception>() {
            @Override
            public LdapResponseMessage apply(final Response response) {
                if (response instanceof Result) {
                    switch (rawRequest.getMessageType()) {
                    case OP_TYPE_ADD_REQUEST:
                        return newResponseMessage(OP_TYPE_ADD_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_BIND_REQUEST:
                        return newResponseMessage(OP_TYPE_BIND_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_COMPARE_REQUEST:
                        return newResponseMessage(OP_TYPE_COMPARE_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_DELETE_REQUEST:
                        return newResponseMessage(OP_TYPE_DELETE_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_EXTENDED_REQUEST:
                        return newResponseMessage(OP_TYPE_EXTENDED_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_MODIFY_DN_REQUEST:
                        return newResponseMessage(OP_TYPE_MODIFY_DN_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_MODIFY_REQUEST:
                        return newResponseMessage(OP_TYPE_MODIFY_RESPONSE, rawRequest.getMessageId(), response);
                    case OP_TYPE_SEARCH_REQUEST:
                        return newResponseMessage(OP_TYPE_SEARCH_RESULT_DONE, rawRequest.getMessageId(), response);
                    default:
                        throw new IllegalArgumentException("Unknown request: " + rawRequest.getMessageType());
                    }
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
        final ClientConnectionImpl sub = LDAP_CONNECTION_ATTR.get(ctx.getConnection());
        return sub.upstream.handleRead(ctx);
    }

    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        final ClientConnectionImpl clientContext = LDAP_CONNECTION_ATTR.remove(ctx.getConnection());
        if (clientContext != null && clientContext.upstream != null) {
            clientContext.upstream.onComplete();
        }
        return ctx.getStopAction();
    }

    final class ClientConnectionImpl implements LDAPClientContext, Publisher<LdapRawMessage> {

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

            public void onError(Throwable t) {
                final Subscriber<? super LdapRawMessage> sub = subscriber;
                if (sub != null) {
                    subscriber = null;
                    sub.onError(t);
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
        GrizzlyBackpressureSubscription upstream;

        private ClientConnectionImpl(final Connection<?> connection) {
            this.connection = connection;
            connection.closeFuture().addCompletionHandler(new CompletionHandler<CloseReason>() {
                @Override
                public void completed(CloseReason result) {
                    disconnect0(null, null);
                }

                @Override
                public void updated(CloseReason result) {
                }

                @Override
                public void failed(Throwable throwable) {
                }

                @Override
                public void cancelled() {
                }
            });
        }

        @Override
        public void subscribe(final Subscriber<? super LdapRawMessage> s) {
            if (upstream != null) {
                return;
            }
            this.upstream = new GrizzlyBackpressureSubscription(s);
        }

        Completable write(final Publisher<LdapResponseMessage> messages) {
            return newCompletable(new Completable.OnSubscribe() {
                @Override
                public void onSubscribe(Emitter e) {
                    messages.subscribe(new LdapResponseMessageWriter(connection, e));
                }
            });
        }

        @Override
        public void enableTLS(final SSLEngine sslEngine) {
            Reject.ifNull(sslEngine);
            synchronized (this) {
                if (isTLSEnabled()) {
                    throw new IllegalStateException("TLS already enabled");
                }
                SSLUtils.setSSLEngine(connection, sslEngine);
                installFilter(new SSLFilter());
            }
        }

        @Override
        public void enableSASL(final SaslServer saslServer) {
            SaslUtils.setSaslServer(connection, saslServer);
            installFilter(new SaslFilter());
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
            final SaslServer saslServer = SaslUtils.getSaslServer(connection);
            if (saslServer == null) {
                return 0;
            }
            int ssf = 0;
            final String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
            if (SaslUtils.SASL_AUTH_INTEGRITY.equalsIgnoreCase(qop)) {
                ssf = 1;
            } else if (SaslUtils.SASL_AUTH_CONFIDENTIALITY.equalsIgnoreCase(qop)) {
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

        /**
         * Indicates whether TLS is enabled this provided connection.
         *
         * @return {@code true} if TLS is enabled on this connection, otherwise {@code false}.
         */
        private boolean isTLSEnabled() {
            synchronized (this) {
                final FilterChain currentFilterChain = (FilterChain) connection.getProcessor();
                for (final Filter filter : currentFilterChain) {
                    if (filter instanceof SSLFilter) {
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        public void onDisconnect(DisconnectListener listener) {
            listeners.add(listener);
        }

        @Override
        public void disconnect() {
            disconnect0(null, null);
        }

        @Override
        public void disconnect(final ResultCode resultCode, final String message) {
            sendUnsolicitedNotification(
                    Responses.newGenericExtendedResult(resultCode)
                             .setOID(LDAP.OID_NOTICE_OF_DISCONNECTION)
                             .setDiagnosticMessage(message));
            disconnect0(resultCode, message);
        }

        private void disconnect0(final ResultCode resultCode, final String message) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                try {
                    // Notify the server connection: it may be null if disconnect is
                    // invoked during accept.
                    for (DisconnectListener listener : listeners) {
                        listener.connectionDisconnected(this, resultCode, message);
                    }
                } finally {
                    // Close the connection.
                    connection.closeSilently();
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
