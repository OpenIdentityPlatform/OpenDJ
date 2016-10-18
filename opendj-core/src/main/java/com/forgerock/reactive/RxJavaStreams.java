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

import org.forgerock.util.Function;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.Flowable;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;

/**
 * {@link Stream} and {@link Single} implementations based on RxJava.
 */
public final class RxJavaStreams {

    private RxJavaStreams() {
        // Hide
    }

    /**
     * Create a new {@link Stream} from the given {@link Publisher}.
     *
     * @param <V>
     *            Type of data emitted
     * @param publisher
     *            The {@link Publisher} to convert
     * @return A new {@link Stream}
     */
    public static <V> Stream<V> streamFromPublisher(final Publisher<V> publisher) {
        return new RxJavaStream<>(Flowable.fromPublisher(publisher));
    }

    /**
     * Create a new {@link Stream} composed only of the given value.
     *
     * @param <V>
     *            Type of data emitted
     * @param value
     *            The value emitted by this stream
     * @return A new {@link Stream}
     */
    public static <V> Stream<V> streamFrom(final V value) {
        return new RxJavaStream<>(Flowable.just(value));
    }

    /**
     * Create a new {@link Stream} composed only of the given error.
     *
     * @param <V>
     *            Type of data emitted
     * @param error
     *            The error emitted by this stream
     * @return A new {@link Stream}
     */
    public static <V> Stream<V> streamError(final Throwable error) {
        return new RxJavaStream<>(Flowable.<V> error(error));
    }

    /**
     * Create a new empty {@link Stream}.
     *
     * @param <V>
     *            Type of data emitted
     * @return An empty {@link Stream}
     */
    public static <V> Stream<V> emptyStream() {
        return new RxJavaStream<>(Flowable.<V> empty());
    }

    /**
     * Create a new {@link Single} from the given {@link Publisher}.
     *
     * @param <V>
     *            Type of the datum emitted
     * @param publisher
     *            The {@link Publisher} to convert
     * @return A new {@link Stream}
     */
    public static <V> Single<V> singleFromPublisher(final Publisher<V> publisher) {
        return new RxJavaSingle<>(io.reactivex.Single.fromPublisher(publisher));
    }

    /**
     * Create a new {@link Single} from the given value.
     *
     * @param <V>
     *            Type of the datum emitted
     * @param value
     *            The value contained by this {@link Single}
     * @return A new {@link Single}
     */
    public static <V> Single<V> singleFrom(final V value) {
        return new RxJavaSingle<>(io.reactivex.Single.just(value));
    }

    /**
     * Creates a bridge from callback world to {@link Single}.
     *
     * @param <V>
     *            Type of the datum emitted
     * @param onSubscribe
     *            Action to perform once this {@link Single} has been subscribed to.
     * @return A new {@link Single}
     */
    public static <V> Single<V> newSingle(final Single.OnSubscribe<V> onSubscribe) {
        return new RxJavaSingle<>(io.reactivex.Single.create(new SingleOnSubscribe<V>() {
            @Override
            public void subscribe(final SingleEmitter<V> e) throws Exception {
                onSubscribe.onSubscribe(new Single.Emitter<V>() {
                    @Override
                    public void onSuccess(V value) {
                        e.onSuccess(value);
                    }

                    @Override
                    public void onError(Throwable error) {
                        e.onError(error);
                    }
                });
            }
        }));
    }

    /**
     * Creates a bridge from callback world to {@link Completable}.
     *
     * @param onSubscribe
     *            Action to perform once this {@link Completable} has been subscribed to.
     * @return A new {@link Completable}
     */
    public static Completable newCompletable(final Completable.OnSubscribe onSubscribe) {
        return new RxJavaCompletable(io.reactivex.Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter e) throws Exception {
                onSubscribe.onSubscribe(new Completable.Emitter() {
                    @Override
                    public void complete() {
                        e.onComplete();
                    }
                    @Override
                    public void onError(Throwable t) {
                        e.onError(t);
                    }
                });
            }
        }));
    }

    private static final class RxJavaStream<V> implements Stream<V> {

        private Flowable<V> impl;

        private RxJavaStream(final Flowable<V> impl) {
            this.impl = impl;
        }

        @Override
        public void subscribe(Subscriber<? super V> s) {
            impl.subscribe(s);
        }

        @Override
        public <O> Stream<O> map(final Function<V, O, Exception> function) {
            return new RxJavaStream<>(impl.map(new io.reactivex.functions.Function<V, O>() {
                @Override
                public O apply(V t) throws Exception {
                    return function.apply(t);
                }
            }));
        }

        @Override
        public <O> Stream<O> flatMap(final Function<? super V, ? extends Publisher<? extends O>, Exception> function,
                int maxConcurrency) {
            return new RxJavaStream<>(impl.flatMap(new io.reactivex.functions.Function<V, Publisher<? extends O>>() {
                @Override
                public Publisher<? extends O> apply(V t) throws Exception {
                    return function.apply(t);
                }
            }, maxConcurrency));
        }

        @Override
        public void subscribe(final Consumer<V> onResult, final Consumer<Throwable> onError, final Action onComplete) {
            impl.subscribe(new io.reactivex.functions.Consumer<V>() {
                @Override
                public void accept(V t) throws Exception {
                    onResult.accept(t);
                }
            }, new io.reactivex.functions.Consumer<Throwable>() {
                @Override
                public void accept(Throwable t) throws Exception {
                    onError.accept(t);
                }
            }, new io.reactivex.functions.Action() {
                @Override
                public void run() throws Exception {
                    onComplete.run();
                }
            });
        }

        @Override
        public Stream<V> onErrorResumeWith(final Function<Throwable, Publisher<V>, Exception> function) {
            return new RxJavaStream<>(
                    impl.onErrorResumeNext(new io.reactivex.functions.Function<Throwable, Publisher<? extends V>>() {
                        @Override
                        public Publisher<? extends V> apply(Throwable t) throws Exception {
                            return function.apply(t);
                        }
                    }));
        }

        @Override
        public void subscribe() {
            impl.subscribe();
        }
    }

    private static final class RxJavaSingle<V> implements Single<V> {

        private final io.reactivex.Single<V> impl;

        private RxJavaSingle(io.reactivex.Single<V> impl) {
            this.impl = impl;
        }

        @Override
        public Stream<V> toStream() {
            return new RxJavaStream<>(impl.toFlowable());
        }

        @Override
        public <O> Single<O> map(final Function<V, O, Exception> function) {
            return new RxJavaSingle<>(impl.map(new io.reactivex.functions.Function<V, O>() {
                @Override
                public O apply(V t) throws Exception {
                    return function.apply(t);
                }
            }));
        }

        @Override
        public void subscribe(Subscriber<? super V> s) {
            impl.toFlowable().subscribe(s);
        }

        @Override
        public void subscribe(final Consumer<? super V> resultConsumer, final Consumer<Throwable> errorConsumer) {
            impl.subscribe(new io.reactivex.functions.Consumer<V>() {
                @Override
                public void accept(V t) throws Exception {
                    resultConsumer.accept(t);
                }
            }, new io.reactivex.functions.Consumer<Throwable>() {
                @Override
                public void accept(Throwable t) throws Exception {
                    errorConsumer.accept(t);
                }
            });
        }

        @Override
        public <O> Single<O> flatMap(final Function<V, Publisher<O>, Exception> function) {
            return new RxJavaSingle<>(impl.flatMap(new io.reactivex.functions.Function<V, SingleSource<O>>() {
                @Override
                public SingleSource<O> apply(V t) throws Exception {
                    return io.reactivex.Single.fromPublisher(function.apply(t));
                }
            }));
        }
    }

    private static final class RxJavaCompletable implements Completable {

        private final io.reactivex.Completable impl;

        RxJavaCompletable(io.reactivex.Completable impl) {
            this.impl = impl;
        }

        @Override
        public <V> Single<V> toSingle(V value) {
            return new RxJavaSingle<>(impl.toSingleDefault(value));
        }

        @Override
        public void subscribe(Subscriber<? super Void> s) {
            impl.<Void>toFlowable().subscribe(s);
        }
    }
}
