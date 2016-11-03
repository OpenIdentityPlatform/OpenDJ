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

/** {@link Completable} is used to communicates a terminated operation which doesn't produce a result. */
public interface Completable extends Publisher<Void> {

    /** Emitter is used to notify when the operation has been completed, successfully or not. */
    public interface Subscriber {
        /** Notify that this {@link Completable} is now completed. */
        void onComplete();

        /**
         * Notify that this {@link Completable} cannot be completed because of an error.
         *
         * @param error
         *            The error preventing this {@link Completable} to complete
         */
        void onError(Throwable error);
    }

    /** Adapts the streaming api to a callback one. */
    public interface Emitter {
        /**
         * Called when the streaming api has been subscribed.
         *
         * @param e
         *            The {@link Subscriber} to use to communicate the completeness of this {@link Completable}
         */
        void subscribe(Subscriber e);
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
     * Creates a {@link Single} which will emit the specified value when this {@link Completable} complete.
     *
     * @param <V>
     *            Type of the value to emit
     * @param value
     *            The value to emit on completion
     * @return A new {@link Single}
     */
    <V> Single<V> toSingle(V value);
}
