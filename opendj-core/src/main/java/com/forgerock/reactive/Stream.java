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
 * Stream is a reactive-streams compliant way to chain operations and transformation on a stream of data.
 *
 * @param <V>
 *            Type of data emitted
 */
public interface Stream<V> extends Publisher<V> {

    /**
     * Transform the data emitted by this stream.
     *
     * @param <O>
     *            Type of data emitted after transformation
     * @param function
     *            The function to apply to each data of this stream
     * @return a new {@link Stream} emitting transformed data
     */
    <O> Stream<O> map(Function<V, O, Exception> function);

    /**
     * Transform each data emitted by this stream into a new stream of data. All these streams are then merged together.
     *
     * @param <O>
     *            Type of data emitted after transformation
     * @param function
     *            The function to transform each data into a new stream
     * @param maxConcurrency
     *            Maximum number of output stream which can be merged. Once this number is reached, the {@link Stream}
     *            will stop requesting data from this stream.
     * @return A new {@link Stream} performing the transformation
     */
    <O> Stream<O> flatMap(Function<? super V, ? extends Publisher<? extends O>, Exception> function,
            int maxConcurrency);

    /**
     * Subscribe to the data emitted by this {@link Stream}.
     *
     * @param onResult
     *            The {@link Consumer} invoked for each data of this stream
     * @param onError
     *            The {@link Consumer} invoked on error in this stream.
     * @param onComplete
     *            The {@link Action} invoked once this {@link Stream} is completed.
     */
    void subscribe(Consumer<V> onResult, Consumer<Throwable> onError, Action onComplete);

    /**
     * When an error occurs in this stream, continue the processing with the new {@link Stream} provided by the
     * function.
     *
     * @param function
     *            Generates the stream which must will used to resume operation when this {@link Stream} failed.
     * @return A new {@link Stream}
     */
    Stream<V> onErrorResumeWith(Function<Throwable, Publisher<V>, Exception> function);

    /**
     * Subscribe to this stream and drop all data produced by it.
     */
    void subscribe();
}
