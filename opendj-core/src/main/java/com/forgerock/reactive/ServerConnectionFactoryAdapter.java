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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.reactive;

import static com.forgerock.reactive.RxJavaStreams.streamFromPublisher;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;

import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.AbstractLDAPMessageHandler;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPClientContextEventListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Function;
import org.forgerock.util.promise.RuntimeExceptionHandler;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;

/**
 * Adapt a {@link ServerConnectionFactory} to a {@link Function} compatible with
 * {@link TransportProvider#getLDAPListener(java.util.Set, Function, org.forgerock.util.Options)}.
 */
@Deprecated
public final class ServerConnectionFactoryAdapter implements
        Function<LDAPClientContext,
                 ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                 LdapException> {

    private final ServerConnectionFactory<LDAPClientContext, Integer> adaptee;
    private final DecodeOptions decodeOptions;

    /**
     * Creates a new adapter.
     *
     * @param decodeOptions
     *            {@link DecodeOptions} used during request deserialization
     * @param serverConnectionFactory
     *            the {@link ServerConnectionFactory} to adapt
     */
    public ServerConnectionFactoryAdapter(final DecodeOptions decodeOptions,
            final ServerConnectionFactory<LDAPClientContext, Integer> serverConnectionFactory) {
        this.decodeOptions = checkNotNull(decodeOptions, "decodeOptions must not be null");
        this.adaptee = checkNotNull(serverConnectionFactory, "serverConnectionFactory must not be null");
    }

    @Override
    public ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>> apply(
            final LDAPClientContext clientContext) throws LdapException {
        return new ServerConnectionAdapter(clientContext, decodeOptions, adaptee.handleAccept(clientContext));
    }

    /**
     * Adapt a {@link ServerConnection} to a {@link Function} compatible with
     * {@link TransportProvider#getLDAPListener(java.util.Set, Function, org.forgerock.util.Options)}.
     */
    public static final class ServerConnectionAdapter
            implements ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>> {

        private final ServerConnection<Integer> adaptee;
        private final DecodeOptions decodeOptions;

        /**
         * Creates a new adapter.
         *
         * @param clientContext
         *            The {@link LDAPClientContext} which represents the client connection
         * @param decodeOptions
         *            The {@link DecodeOptions} to use during request deserialization
         * @param serverConnection
         *            the {@link ServerConnection} to adapt
         */
        public ServerConnectionAdapter(final LDAPClientContext clientContext, final DecodeOptions decodeOptions,
                final ServerConnection<Integer> serverConnection) {
            this.decodeOptions = checkNotNull(decodeOptions, "decodeOptions must not be null");
            this.adaptee = checkNotNull(serverConnection, "serverConnection must not be null");
            clientContext.addListener(new LDAPClientContextEventListener() {
                @Override
                public void handleConnectionError(final LDAPClientContext context, final Throwable error) {
                    adaptee.handleConnectionError(error);
                }

                @Override
                public void handleConnectionClosed(final LDAPClientContext context, final UnbindRequest unbindRequest) {
                    if (unbindRequest == null) {
                        adaptee.handleConnectionClosed(null, null);
                    } else {
                        adaptee.handleConnectionClosed(0, unbindRequest);
                    }
                }

                @Override
                public void handleConnectionDisconnected(final LDAPClientContext context, final ResultCode resultCode,
                        final String diagnosticMessage) {
                    adaptee.handleConnectionDisconnected(resultCode, diagnosticMessage);
                }
            });
        }

        @Override
        public Stream<Response> handle(final LDAPClientContext context, final LdapRequestEnvelope request)
                throws Exception {
            final LDAPReader<ASN1Reader> reader = LDAP.getReader(request.getContent(), decodeOptions);
            return streamFromPublisher(Flowable.create(new FlowableOnSubscribe<Response>() {
                @Override
                public void subscribe(final FlowableEmitter<Response> emitter) throws Exception {
                    reader.readMessage(new AbstractLDAPMessageHandler() {
                        @Override
                        public void abandonRequest(int messageID, AbandonRequest request)
                                throws DecodeException, IOException {
                            handleAbandon(messageID, request, emitter);
                        }

                        @Override
                        public void addRequest(int messageID, AddRequest request) throws DecodeException, IOException {
                            handleAdd(messageID, request, emitter);
                        }

                        @Override
                        public void deleteRequest(final int messageID, final DeleteRequest request)
                                throws DecodeException, IOException {
                            handleDelete(messageID, request, emitter);
                        }

                        @Override
                        public void bindRequest(int messageID, int version, GenericBindRequest request)
                                throws DecodeException, IOException {
                            handleBind(messageID, version, request, emitter);
                        }

                        @Override
                        public void compareRequest(int messageID, CompareRequest request)
                                throws DecodeException, IOException {
                            handleCompare(messageID, request, emitter);
                        }

                        @Override
                        public <R extends ExtendedResult> void extendedRequest(int messageID,
                                ExtendedRequest<R> request) throws DecodeException, IOException {
                            handleExtendedRequest(messageID, request, emitter);
                        }

                        @Override
                        public void modifyDNRequest(int messageID, ModifyDNRequest request)
                                throws DecodeException, IOException {
                            handleModifyDN(messageID, request, emitter);
                        }

                        @Override
                        public void modifyRequest(int messageID, ModifyRequest request)
                                throws DecodeException, IOException {
                            handleModify(messageID, request, emitter);
                        }

                        @Override
                        public void searchRequest(int messageID, SearchRequest request)
                                throws DecodeException, IOException {
                            handleSearch(messageID, request, emitter);
                        }

                        @Override
                        public void unbindRequest(int messageID, UnbindRequest request)
                                throws DecodeException, IOException {
                            // Unbind request are received through ConnectionEventListener only
                        }
                    });
                }
            }, BackpressureStrategy.ERROR));
        }

        void handleAdd(final int requestId, final AddRequest request, final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<Result> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleAdd(requestId, request, resultAdapter, resultAdapter);
        }

        void handleBind(final int requestId, final int version, final BindRequest request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<BindResult> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleBind(requestId, version, request, resultAdapter, resultAdapter);
        }

        void handleCompare(final int requestId, final CompareRequest request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<CompareResult> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleCompare(requestId, request, resultAdapter, resultAdapter);
        }

        void handleDelete(final int requestId, final DeleteRequest request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<Result> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleDelete(requestId, request, resultAdapter, resultAdapter);
        }

        <R extends ExtendedResult> void handleExtendedRequest(final int requestId, final ExtendedRequest<R> request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<R> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleExtendedRequest(requestId, request, resultAdapter, resultAdapter);
        }

        void handleModify(final int requestId, final ModifyRequest request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<Result> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleModify(requestId, request, resultAdapter, resultAdapter);
        }

        void handleModifyDN(final int requestId, final ModifyDNRequest request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<Result> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleModifyDN(requestId, request, resultAdapter, resultAdapter);
        }

        void handleSearch(final int requestId, final SearchRequest request,
                final FlowableEmitter<Response> responseEmitter) {
            final ResultHandlerAdapter<Result> resultAdapter = new ResultHandlerAdapter<>(responseEmitter);
            adaptee.handleSearch(requestId, request, resultAdapter, resultAdapter, resultAdapter);
        }

        void handleAbandon(int requestId, final AbandonRequest request, final FlowableEmitter<Response> emitter) {
            adaptee.handleAbandon(requestId, request);
        }

        /** Forward all responses received from handler to a {@link LdapResponse}. */
        private static final class ResultHandlerAdapter<R extends Response> implements IntermediateResponseHandler,
                SearchResultHandler, LdapResultHandler<R>, RuntimeExceptionHandler {

            private final FlowableEmitter<Response> adaptee;

            ResultHandlerAdapter(final FlowableEmitter<Response> emitter) {
                this.adaptee = emitter;
            }

            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                adaptee.onNext(entry);
                return true;
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                adaptee.onNext(reference);
                return true;
            }

            @Override
            public boolean handleIntermediateResponse(final IntermediateResponse intermediateResponse) {
                adaptee.onNext(intermediateResponse);
                return true;
            }

            @Override
            public void handleResult(R result) {
                if (result != null) {
                    adaptee.onNext(result);
                }
                adaptee.onComplete();
            }

            @Override
            public void handleRuntimeException(RuntimeException exception) {
                adaptee.onError(exception);
            }

            @Override
            public void handleException(LdapException exception) {
                adaptee.onError(exception);
            }
        }
    }
}
