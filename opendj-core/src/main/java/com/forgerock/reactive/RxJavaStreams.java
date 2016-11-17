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

import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.CompletableSource;
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
     * Create a new {@link Single} from the given {@link Publisher}. If the {@link Publisher} produce more than one
     * result, they'll be dropped and the inner {@link org.reactivestreams.Subscription Subscription} cancelled.
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
     * Create a new {@link Single} from the given error.
     *
     * @param <V>
     *            Type of the datum emitted
     * @param error
     *            The error emitted by this {@link Single}
     * @return A new {@link Single}
     */
    public static <V> Single<V> singleError(final Throwable error) {
        return new RxJavaSingle<>(io.reactivex.Single.<V>error(error));
    }

    /**
     * Creates a bridge from callback world to {@link Single}.
     *
     * @param <V>
     *            Type of the datum emitted
     * @param emitter
     *            Action to perform once this {@link Single} has been subscribed to.
     * @return A new {@link Single}
     */
    public static <V> Single<V> newSingle(final Single.Emitter<V> emitter) {
        return new RxJavaSingle<>(io.reactivex.Single.create(new SingleOnSubscribe<V>() {
            @Override
            public void subscribe(final SingleEmitter<V> e) throws Exception {
                emitter.subscribe(new Single.Subscriber<V>() {
                    @Override
                    public void onComplete(V value) {
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
    public static Completable newCompletable(final Completable.Emitter onSubscribe) {
        return new RxJavaCompletable(io.reactivex.Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter e) throws Exception {
                onSubscribe.subscribe(new Completable.Subscriber() {
                    @Override
                    public void onComplete() {
                        e.onComplete();
                    }
                    @Override
                    public void onError(final Throwable error) {
                        e.onError(error);
                    }
                });
            }
        }));
    }

    /**
     * Create a new {@link Completable} from the given error.
     *
     * @param error
     *            The error emitted by this {@link Completable}
     * @return A new {@link Completable}
     */
    public static Completable completableError(final Throwable error) {
        return new RxJavaCompletable(io.reactivex.Completable.error(error));
    }

    private static final class RxJavaStream<V> implements Stream<V> {

        private final Flowable<V> impl;

        private RxJavaStream(final Flowable<V> impl) {
            this.impl = impl;
        }

        @Override
        public void subscribe(org.reactivestreams.Subscriber<? super V> s) {
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
        public Stream<V> onNext(final Consumer<V> onNext) {
            return new RxJavaStream<>(impl.doOnNext(new io.reactivex.functions.Consumer<V>() {
                @Override
                public void accept(V value) throws Exception {
                    onNext.accept(value);
                }
            }));
        }

        @Override
        public Stream<V> onError(final Consumer<Throwable> onError) {
            return new RxJavaStream<>(impl.doOnError(new io.reactivex.functions.Consumer<Throwable>() {
                @Override
                public void accept(Throwable t) throws Exception {
                    onError.accept(t);
                }
            }));
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
        public Stream<V> onComplete(final Action action) {
            return new RxJavaStream<>(impl.doOnComplete(new io.reactivex.functions.Action() {
                @Override
                public void run() throws Exception {
                    action.run();
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
        public void subscribe(org.reactivestreams.Subscriber<? super V> s) {
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
        public <O> Single<O> flatMap(final Function<V, Single<O>, Exception> function) {
            return new RxJavaSingle<>(impl.flatMap(new io.reactivex.functions.Function<V, SingleSource<O>>() {
                @Override
                public SingleSource<O> apply(V t) throws Exception {
                    return io.reactivex.Single.fromPublisher(function.apply(t));
                }
            }));
        }

        @Override
        public Single<V> onErrorResumeWith(final Function<Throwable, Single<V>, Exception> function) {
            return new RxJavaSingle<>(
                    impl.onErrorResumeNext(new io.reactivex.functions.Function<Throwable, SingleSource<V>>() {
                        @Override
                        public SingleSource<V> apply(Throwable error) throws Exception {
                            return io.reactivex.Single.fromPublisher(function.apply(error));
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
        public void subscribe(org.reactivestreams.Subscriber<? super Void> s) {
            impl.<Void>toFlowable().subscribe(s);
        }

        @Override
        public Completable onErrorResumeWith(final Function<Throwable, Completable, Exception> function) {
            return new RxJavaCompletable(
                    impl.onErrorResumeNext(new io.reactivex.functions.Function<Throwable, CompletableSource>() {
                        @Override
                        public CompletableSource apply(Throwable error) throws Exception {
                            return io.reactivex.Completable.fromPublisher(function.apply(error));
                        }
                    }));
        }

        @Override
        public void subscribe(final Action completeAction, final Consumer<Throwable> errorConsumer) {
            impl.subscribe(new io.reactivex.functions.Action() {
                @Override
                public void run() throws Exception {
                    completeAction.run();
                }
            }, new io.reactivex.functions.Consumer<Throwable>() {
                @Override
                public void accept(final Throwable error) throws Exception {
                    errorConsumer.accept(error);
                }
            });
        }

        @Override
        public Completable doAfterTerminate(final Action onTerminate) {
            return new RxJavaCompletable(impl.doAfterTerminate(new io.reactivex.functions.Action() {
                @Override
                public void run() throws Exception {
                    onTerminate.run();
                }
            }));
        }

        @Override
        public void subscribe() {
            impl.subscribe();
        }
    }
}
