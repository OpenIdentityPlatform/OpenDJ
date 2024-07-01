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

/**
 * {@link Completable} is used to communicates a terminated operation which doesn't produce a result. It extends
 * {@link Publisher} by providing additional reactive methods allowing to act on it. The goal of this interface is to
 * decouple ourself from reactive framework (RxJava/Reactor).
 */
public interface Completable extends Publisher<Void> {
    /** Subscriber is notified when the operation has been completed, successfully or not. */
    interface Subscriber {
        /** Called once this {@link Completable} is completed. */
        void onComplete();

        /**
         * Called when this {@link Completable} cannot be completed because of an error.
         *
         * @param error
         *            The error preventing this {@link Completable} to complete
         */
        void onError(Throwable error);
    }

    /** Adapts the streaming api to a callback one. */
    interface Emitter {
        /**
         * Called when the streaming api has been subscribed.
         *
         * @param s
         *            The {@link Subscriber} to use to communicate the completeness of this {@link Completable}
         * @throws Exception
         *             on error
         */
        void subscribe(Subscriber s) throws Exception;
    }

    /**
     * When an error occurs in this completable, continue the processing with the new {@link Completable} provided by
     * the function.
     *
     * @param function
     *            Generates the stream which must will used to resume operation when this {@link Completable} failed.
     * @return A new {@link Completable}
     */
    Completable onErrorResumeWith(Function<Throwable, Completable, Exception> function);

    /**
     * Returns a {@link Completable} instance that calls the given onTerminate callback after this Completable completes
     * normally or with an exception.
     *
     * @param onAfterTerminate
     *            the callback to call after this {@link Completable} terminates
     * @return the new {@link Completable} instance
     */
    Completable doAfterTerminate(Action onAfterTerminate);

    /**
     * Returns a {@link Single} which will complete with the provided value once this {@link Completable} completes.
     *
     * @param <V>
     *            Type of the value to emit
     * @param value
     *            The value to emit on completion
     * @return A new {@link Single}
     */
    <V> Single<V> toSingle(V value);

    /**
     * Subscribe to the result of this {@link Completable}.
     *
     * @param completeAction
     *            An {@link Action} which will be invoked on successful completion
     * @param errorConsumer
     *            A {@link Consumer} which will be invoked on error
     */
    void subscribe(Action completeAction, Consumer<Throwable> errorConsumer);

    /**
     * Subscribes to this {@link Completable}.
     */
    void subscribe();
}
