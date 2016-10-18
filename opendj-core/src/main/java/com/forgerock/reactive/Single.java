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
 * Single is a reactive-streams compatible promise.
 *
 * @param <V>
 *            Type of the datum emitted
 */
public interface Single<V> extends Publisher<V> {

    /** Emitter is used to notify when the operation has been completed, successfully or not. */
    public interface Emitter<V> {
        /**
         * Signal a success value.
         *
         * @param t
         *            the value, not null
         */
        void onSuccess(V t);

        /**
         * Signal an exception.
         *
         * @param t
         *            the exception, not null
         */
        void onError(Throwable t);
    }

    /** Adapts the streaming api to a callback one. */
    public interface OnSubscribe<V> {
        /**
         * Called for each SingleObserver that subscribes.
         * @param e the safe emitter instance, never null
         * @throws Exception on error
         */
        void onSubscribe(Emitter<V> e) throws Exception;
    }

    /**
     * Transform this {@link Single} into a {@link Stream}.
     *
     * @return A new {@link Stream}
     */
    Stream<V> toStream();

    /**
     * Transforms the value emitted by this single.
     *
     * @param <O>
     *            Type of the datum emitted after transformation
     * @param function
     *            Function to apply to the result of this single.
     * @return A new {@link Single} with the transformed value.
     */
    <O> Single<O> map(Function<V, O, Exception> function);

    /**
     * Transforms asynchronously the value emitted by this single into another.
     *
     * @param <O>
     *            Type of the datum emitted after transformation
     * @param function
     *            Function to apply to perform the asynchronous transformation
     * @return A new {@link Single} transforming the datum emitted by this {@link Single}.
     */
    <O> Single<O> flatMap(Function<V, Publisher<O>, Exception> function);

    /**
     * Subscribe to the single value emitted by this {@link Single}.
     *
     * @param resultConsumer
     *            A {@link Consumer} which will be invoked by the value emitted by this single
     * @param errorConsumer
     *            A {@link Consumer} which will be invoked with the error emitted by this single
     */
    void subscribe(Consumer<? super V> resultConsumer, Consumer<Throwable> errorConsumer);
}
