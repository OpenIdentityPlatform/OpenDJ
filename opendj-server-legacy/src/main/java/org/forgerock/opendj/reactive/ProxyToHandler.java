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
package org.forgerock.opendj.reactive;

import static com.forgerock.reactive.RxJavaStreams.*;
import static org.forgerock.util.Utils.closeSilently;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;

import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Single;
import com.forgerock.reactive.Stream;

import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableEmitter.BackpressureMode;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.functions.Action;

/** Forward {@link Request} to another server reached through the {@link LDAPConnectionFactory}. */
final class ProxyToHandler implements ReactiveHandler<LDAPClientConnection2, Request, Stream<Response>> {

    private final LDAPConnectionFactory connectionFactory;

    ProxyToHandler(final LDAPConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Single<Stream<Response>> handle(final LDAPClientConnection2 context, final Request request) {
        return singleFromPublisher(Flowable.create(new FlowableOnSubscribe<Stream<Response>>() {
            @Override
            public void subscribe(final FlowableEmitter<Stream<Response>> emitter) throws Exception {
                final AtomicReference<Connection> connectionHolder = new AtomicReference<Connection>();

                connectionFactory.getConnectionAsync().thenOnResult(new ResultHandler<Connection>() {
                    @Override
                    public void handleResult(final Connection connection) {
                        connectionHolder.set(connection);
                        emitter.onNext(executeRequest(connection, request));
                        emitter.onComplete();
                    }
                }).thenOnException(new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(LdapException exception) {
                        emitter.onError(exception);
                    }
                }).thenOnRuntimeException(new RuntimeExceptionHandler() {
                    @Override
                    public void handleRuntimeException(RuntimeException exception) {
                        emitter.onError(exception);
                    }
                });
            }
        }, BackpressureMode.ERROR));
    }

    private Stream<Response> executeRequest(final Connection connection, final Request request) {
        return streamFromPublisher(Flowable.create(new FlowableOnSubscribe<Response>() {
            @Override
            public void subscribe(FlowableEmitter<Response> emitter) throws Exception {
                // add/delete/modifyDN/modifyReq
                final PublisherAdaptor adapter = new PublisherAdaptor(emitter);
                if (request instanceof ChangeRecord) {
                    connection.applyChangeAsync((ChangeRecord) request, adapter).thenOnResult(adapter)
                            .thenOnException(adapter).thenOnRuntimeException(adapter);
                } else if (request instanceof CompareRequest) {
                    connection.compareAsync((CompareRequest) request, adapter).thenOnResult(adapter)
                            .thenOnException(adapter).thenOnRuntimeException(adapter);
                } else if (request instanceof ExtendedRequest) {
                    connection.compareAsync((CompareRequest) request, adapter).thenOnResult(adapter)
                            .thenOnException(adapter).thenOnRuntimeException(adapter);
                } else if (request instanceof SearchRequest) {
                    connection.searchAsync((SearchRequest) request, adapter).thenOnResult(adapter)
                            .thenOnException(adapter).thenOnRuntimeException(adapter);
                } else {
                    emitter.onError(new IllegalArgumentException("Unsupported request type"));
                }
            }
        }, BackpressureMode.ERROR).doAfterTerminate(new Action() {
            @Override
            public void run() throws Exception {
                closeSilently(connection);
            }
        }));
    }

    /** Adaptor forwarding events from the SDK handler to the emitter. */
    private final class PublisherAdaptor implements IntermediateResponseHandler, SearchResultHandler,
            ResultHandler<Result>, ExceptionHandler<Exception>, RuntimeExceptionHandler {

        private final FlowableEmitter<Response> emitter;

        PublisherAdaptor(final FlowableEmitter<Response> emitter) {
            this.emitter = emitter;
        }

        private boolean handle(final Response response) {
            emitter.onNext(response);
            return true;
        }

        @Override
        public boolean handleEntry(SearchResultEntry entry) {
            return handle(entry);
        }

        @Override
        public boolean handleReference(SearchResultReference reference) {
            return handle(reference);
        }

        @Override
        public boolean handleIntermediateResponse(IntermediateResponse response) {
            return handle(response);
        }

        @Override
        public void handleResult(Result result) {
            if (result != null) {
                handle(result);
            }
            emitter.onComplete();
        }

        @Override
        public void handleRuntimeException(RuntimeException exception) {
            emitter.onError(exception);
        }

        @Override
        public void handleException(Exception exception) {
            emitter.onError(exception);
        }
    }
}
