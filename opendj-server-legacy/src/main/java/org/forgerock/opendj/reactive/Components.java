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
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.forgerock.opendj.io.AbstractLDAPMessageHandler;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRawMessage;
import org.forgerock.util.Function;
import org.reactivestreams.Publisher;

import com.forgerock.reactive.ReactiveFilter;
import com.forgerock.reactive.ReactiveFilter.SimpleReactiveFilter;
import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.RxJavaStreams;
import com.forgerock.reactive.Single;
import com.forgerock.reactive.Stream;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

/** Maintains a set of standard {@link ReactiveHandler} / {@link ReactiveFilter} which can be used in ldap endpoint. */
public final class Components {

    /**
     * Routes incoming request to dedicated handler.
     *
     * @param <CTX>
     *            Context type in which request are processed
     * @param <REQ>
     *            Type of routed request
     * @param <REP>
     *            Type of routed response
     * @param routes
     *            {@link Route} where request will be forwarded to
     * @param defaultRoute
     *            If no route can be applied for a specific request, it'll be forwarded to the defaultRoute
     * @return A new {@link ReactiveHandler} routing incoming requests
     */
    public static <CTX, REQ, REP> ReactiveHandler<CTX, REQ, REP> routeTo(final Iterable<Route<CTX, REQ, REP>> routes,
            final ReactiveHandler<CTX, REQ, REP> defaultRoute) {
        /** Routes requests. */
        final class RouterHandler implements ReactiveHandler<CTX, REQ, REP> {
            @Override
            public Single<REP> handle(final CTX context, final REQ request) throws Exception {
                for (final Route<CTX, REQ, REP> route : routes) {
                    if (route.predicate.matches(request, context)) {
                        return route.handler.handle(context, request);
                    }
                }
                return defaultRoute.handle(context, request);
            }
        }
        return new RouterHandler();
    }

    /**
     * Allows to transform all aspects of a {@link Request}.
     *
     * @param <CTX>
     *            Context type in which request are processed
     * @param requestTransformer
     *            Function in charge of performing the {@link Request} transformation
     * @param responseTransformer
     *            Function in charge of performing the {@link Response} transformation
     * @return A new policy performing the {@link Request} and {@link Response} transformation.
     */
    public static <CTX> SimpleReactiveFilter<CTX, Request, Stream<Response>> transform(
            final Function<Request, Request, Exception> requestTransformer,
            final Function<Response, Response, Exception> responseTransformer) {
        /** Transforms {@link Request} and {@link Response}. */
        final class TransformFilter extends SimpleReactiveFilter<CTX, Request, Stream<Response>> {
            @Override
            public Single<Stream<Response>> filter(final CTX context, final Request request,
                    final ReactiveHandler<CTX, Request, Stream<Response>> next) throws Exception {
                return next.handle(context, requestTransformer.apply(request))
                        .map(new Function<Stream<Response>, Stream<Response>, Exception>() {
                            @Override
                            public Stream<Response> apply(Stream<Response> responses) throws Exception {
                                return responses.map(new Function<Response, Response, Exception>() {
                                    @Override
                                    public Response apply(Response value) throws Exception {
                                        return responseTransformer.apply(value);
                                    }
                                });
                            }
                        });
            }
        }
        return new TransformFilter();
    }

    /**
     * Forward {@link Request} to the provided {@link LDAPConnectionFactory}.
     *
     * @param connectionFactory
     *            The {@link LDAPConnectionFactory} used to send the forwarded {@link Request}
     * @return a {@link ReactiveHandler} Forwarding {@link Request} to the {@link LDAPConnectionFactory}
     */
    public static ReactiveHandler<LDAPClientConnection2, Request, Stream<Response>> proxyTo(
            LDAPConnectionFactory connectionFactory) {
        return new ProxyToHandler(connectionFactory);
    }

    /**
     * {@link ReactiveHandler} responding with the provided Response for all incoming requests.
     *
     * @param <CTX>
     *            Context type in which request are processed
     * @param response
     *            The {@link Response} to send as response for all {@link Request}
     * @return a {@link ReactiveHandler} replying {@link Response} for all {@link Request}
     */
    public static <CTX> ReactiveHandler<CTX, Request, Stream<Response>> respondWith(final Response response) {
        return new ReactiveHandler<CTX, Request, Stream<Response>>() {
            @Override
            public Single<Stream<Response>> handle(final CTX context, final Request request) {
                if (request instanceof UnbindRequest) {
                    return singleFrom(RxJavaStreams.<Response>emptyStream());
                }
                return singleFrom(streamFrom(response));
            }
        };
    }

    /**
     * Decodes incoming {@link LdapRawMessage} into a {@link Request}.
     *
     * @param decodeOptions
     *            {@link DecodeOptions} used during {@link Request} decoding
     * @return a {@link ReactiveFilter} decoding {@link LdapRawMessage} into {@link Request}
     */
    public static ReactiveFilter<LDAPClientConnection2, LdapRawMessage, Stream<Response>,
                                                        Request, Stream<Response>>
        decodeRequest(final DecodeOptions decodeOptions) {
        return new ReactiveFilter<LDAPClientConnection2, LdapRawMessage, Stream<Response>,
                                                         Request, Stream<Response>>() {
            @Override
            public Single<Stream<Response>> filter(final LDAPClientConnection2 context,
                    final LdapRawMessage encodedRequestMessage,
                    final ReactiveHandler<LDAPClientConnection2, Request, Stream<Response>> next) throws Exception {
                return newSingle(new Single.Emitter<Request>() {
                    @Override
                    public void subscribe(final Single.Subscriber<Request> subscriber) throws Exception {
                        LDAP.getReader(encodedRequestMessage.getContent(), decodeOptions)
                                .readMessage(new AbstractLDAPMessageHandler() {
                            @Override
                            public void abandonRequest(final int messageID, final AbandonRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }

                            @Override
                            public void addRequest(int messageID, AddRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }

                            @Override
                            public void bindRequest(int messageID, int version, GenericBindRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }

                            @Override
                            public void modifyDNRequest(int messageID, ModifyDNRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }

                            @Override
                            public void modifyRequest(int messageID, ModifyRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }

                            @Override
                            public void searchRequest(int messageID, SearchRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }

                            @Override
                            public void unbindRequest(int messageID, UnbindRequest request)
                                    throws DecodeException, IOException {
                                subscriber.onComplete(request);
                            }
                        });
                    }
                }).flatMap(new Function<Request, Single<Stream<Response>>, Exception>() {
                    @Override
                    public Single<Stream<Response>> apply(final Request request) throws Exception {
                        return next.handle(context, request);
                    }
                });
            }
        };
    }

    /**
     * Invoke the following {@link ReactiveFilter} from the given {@link Executor}.
     *
     * @param <CTX>
     *            Context type in which request are processed
     * @param <REQ>
     *            Type of dispatched request
     * @param <REP>
     *            Type of dispatched response
     * @param executor
     *            The {@link Executor} used to forward the request
     * @return A {@link ReactiveFilter} fowarding {@link Request} through the {@link Executor}
     */
    public static <CTX, REQ, REP> ReactiveFilter<CTX, REQ, REP, REQ, REP> dispatch(final Executor executor) {
        /** Dispatches request into an {@link Executor}. */
        final class DispatchFilter extends SimpleReactiveFilter<CTX, REQ, REP> {
            private final Scheduler executor;

            DispatchFilter(final Executor executor) {
                this.executor = Schedulers.from(checkNotNull(executor, "executor must not be null"));
            }

            @Override
            public Single<REP> filter(final CTX context, final REQ request,
                    final ReactiveHandler<CTX, REQ, REP> next) {
                return singleFromPublisher(Flowable.defer(new Callable<Publisher<REP>>() {
                    @Override
                    public Publisher<REP> call() throws Exception {
                        return next.handle(context, request);
                    }
                }).subscribeOn(executor));
            }
        }
        return new DispatchFilter(executor);
    }

    private Components() {
        // Prevent instantiation
    }
}
