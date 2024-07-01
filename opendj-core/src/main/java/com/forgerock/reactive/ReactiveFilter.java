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
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Filters and/or transforms the request and/or response of an exchange.
 *
 *                +--------+              +---------+
 *   ---> I1 ---> |        | ---> I2 ---> |         |
 *                | Filter |              | Handler |
 *   <--- O1 --<  |        | <--- O2 <--- |         |
 *                +--------+              +---------+
 *
 *                +--------+              +---------+              +---------+
 *   ---> I1 ---> |  One   | ---> I2 ---> | AndThen | ---> I3 ---> |         |
 *                | Filter |              | Filter  |              | Handler |
 *   <--- O1 --<  |        | <--- O2 <--- |         | <--- O3 ---< |         |
 *                +--------+              +---------+              +---------+
 *
 * @param <C>
 *            Context in which the filter will be evaluated
 * @param <I1>
 *            Type of the request received by this filter
 * @param <O1>
 *            Type of the response answered by this filter
 * @param <I2>
 *            Type of the request emitted to the handler/next filter
 * @param <O2>
 *            Type of the response emitted by the handler/next filter
 */
public abstract class ReactiveFilter<C, I1, O1, I2, O2> {

    /**
     * A simple {@link ReactiveHandler} performing simple filtering without type transformation.
     *
     * @param <C>
     *            Context in which the filter will be evaluated
     * @param <I>
     *            Type of the filtered request
     * @param <O>
     *            Type of the filtered response
     */
    public static abstract class SimpleReactiveFilter<C, I, O> extends ReactiveFilter<C, I, O, I, O> {
    }

    /**
     * Optionally concatenate a new filter to this filter.
     *
     * @param condition
     *            If true, next will be concatenated to this filter
     * @param next
     *            The filter to optionally concatenate with this one
     * @return If condition is true, returns the concatenation of this filter and next. Otherwise, return this filter.
     */
    public ReactiveFilter<C, I1, O1, I2, O2> andThen(final boolean condition,
            final ReactiveFilter<C, I2, O2, I2, O2> next) {
        if (!condition) {
            return ReactiveFilter.this;
        }
        return new ConcatenatedFilter<C, I1, O1, I2, O2>(
                new Function<ReactiveHandler<C, I2, O2>, ReactiveHandler<C, I1, O1>, NeverThrowsException>() {
                    @Override
                    public ReactiveHandler<C, I1, O1> apply(ReactiveHandler<C, I2, O2> handler) {
                        return andThen(next.andThen(handler));
                    }
                });
    }

    /**
     * Concatenate a transformer filter to this filter.
     *
     * @param <I3>
     *            Target type of the request transformed by the filter
     * @param <O3>
     *            Source type of the response transformed by the filter
     * @param next
     *            The transformer filter to add after this filter.
     * @return A new {@link ReactiveFilter} results of the concatenation of this filter and the transformer filter
     */
    public <I3, O3> ReactiveFilter<C, I1, O1, I3, O3> andThen(final ReactiveFilter<C, I2, O2, I3, O3> next) {
        return new ConcatenatedFilter<C, I1, O1, I3, O3>(
                new Function<ReactiveHandler<C, I3, O3>, ReactiveHandler<C, I1, O1>, NeverThrowsException>() {
                    @Override
                    public ReactiveHandler<C, I1, O1> apply(ReactiveHandler<C, I3, O3> handler) {
                        return andThen(next.andThen(handler));
                    }
                });
    }

    /**
     * Add a new filtering step after this one.
     *
     * @param next
     *            The filter to add after this one
     * @return A new {@link ReactiveFilter} result of the concatenation of this filter and next
     */
    public ReactiveFilter<C, I1, O1, I2, O2> andThen(final SimpleReactiveFilter<C, I2, O2> next) {
        return new ConcatenatedFilter<C, I1, O1, I2, O2>(
                new Function<ReactiveHandler<C, I2, O2>, ReactiveHandler<C, I1, O1>, NeverThrowsException>() {
                    @Override
                    public ReactiveHandler<C, I1, O1> apply(ReactiveHandler<C, I2, O2> handler) {
                        return andThen(next.andThen(handler));
                    }
                });
    }

    /**
     * Terminate the filter chain with the specified handler.
     *
     * @param handler
     *            {@link ReactiveHandler} in charge of processing the request
     * @return A new {@link ReactiveHandler} results of the concatenation of this filter and handler
     */
    public ReactiveHandler<C, I1, O1> andThen(final ReactiveHandler<C, I2, O2> handler) {
        final ReactiveFilter<C, I1, O1, I2, O2> parent = this;
        return new ReactiveHandler<C, I1, O1>() {
            @Override
            public O1 handle(final C context, final I1 request) throws Exception {
                return parent.filter(context, request, handler);
            }
        };
    }

    /**
     * Perform the request and/or response filtering and/or transformation.
     *
     * @param context
     *            Context of the filtering/transformation
     * @param request
     *            Request to filter
     * @param next
     *            {@link ReactiveHandler} to call once the filtering/transformation is done
     * @return The filtered and/or transformed response
     * @throws Exception
     *             If the operation cannot be done
     */
    public abstract O1 filter(final C context, final I1 request, final ReactiveHandler<C, I2, O2> next)
            throws Exception;

    private static final class ConcatenatedFilter<C, I1, O1, I2, O2> extends ReactiveFilter<C, I1, O1, I2, O2> {
        private final Function<ReactiveHandler<C, I2, O2>, ReactiveHandler<C, I1, O1>, NeverThrowsException> converter;

        ConcatenatedFilter(
                Function<ReactiveHandler<C, I2, O2>, ReactiveHandler<C, I1, O1>, NeverThrowsException> converter) {
            this.converter = converter;
        }

        @Override
        public <I3, O3> ReactiveFilter<C, I1, O1, I3, O3> andThen(final ReactiveFilter<C, I2, O2, I3, O3> nextFilter) {
            return new ConcatenatedFilter<C, I1, O1, I3, O3>(
                    new Function<ReactiveHandler<C, I3, O3>, ReactiveHandler<C, I1, O1>, NeverThrowsException>() {
                        @Override
                        public ReactiveHandler<C, I1, O1> apply(ReactiveHandler<C, I3, O3> handler) {
                            return converter.apply(nextFilter.andThen(handler));
                        }
                    });
        }

        @Override
        public ReactiveHandler<C, I1, O1> andThen(ReactiveHandler<C, I2, O2> handler) {
            return converter.apply(handler);
        }

        @Override
        public O1 filter(C context, I1 request, ReactiveHandler<C, I2, O2> handler) throws Exception {
            return converter.apply(handler).handle(context, request);
        }
    }
}
