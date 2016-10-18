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

import static org.forgerock.util.Reject.checkNotNull;

import com.forgerock.opendj.util.Predicate;
import com.forgerock.reactive.ReactiveHandler;

/**
 * Contains a routing predicate and a handler. If the predicate matches, the handler will be invoked.
 *
 * @param <CTX>
 *            Type of context in which request are processed
 * @param <REQ>
 *            Type of routed request
 * @param <REP>
 *            Type of routed response
 */
public final class Route<CTX, REQ, REP> {
    final Predicate<REQ, CTX> predicate;
    final ReactiveHandler<CTX, REQ, REP> handler;

    /**
     * Creates a new route.
     *
     * @param <CTX>
     *            Type of context in which request are processed
     * @param <REQ>
     *            Type of routed request
     * @param <REP>
     *            Type of routed response
     * @param predicate
     *            The {@link Predicate} which must be fulfilled to apply this route
     * @param target
     *            The {@link ReactiveHandler} to invoke when this route is applied
     * @return a new {@link Route}
     */
    public static <CTX, REQ, REP> Route<CTX, REQ, REP> newRoute(final Predicate<REQ, CTX> predicate,
            final ReactiveHandler<CTX, REQ, REP> target) {
        return new Route<>(predicate, target);
    }

    private Route(final Predicate<REQ, CTX> predicate, final ReactiveHandler<CTX, REQ, REP> target) {
        this.predicate = checkNotNull(predicate, "predicate must not be null");
        this.handler = checkNotNull(target, "handler must not be null");
    }
}
