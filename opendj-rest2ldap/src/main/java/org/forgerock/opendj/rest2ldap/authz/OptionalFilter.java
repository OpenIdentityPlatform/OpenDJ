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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap.authz;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/** Encapsulate a {@link Filter} which can be conditionally applied. */
public final class OptionalFilter implements Filter {

    /** Condition which have to be fulfilled in order to apply the {@link Filter}. */
    public interface Condition {
        /**
         * Check if a {@link Filter} must be executed or not.
         *
         * @param context
         *            Current {@link Context} of the request processing.
         * @param request
         *            the {@link Request} currently processed.
         * @return true if the filter must be applied.
         */
        boolean canApplyFilter(Context context, Request request);
    }

    /** A {@link Filter} which can be conditionally applied. */
    public interface ConditionalFilter extends Filter, Condition {
    }

    private final Filter delegate;
    private final Condition condition;

    /**
     * Make {@link Filter} optional.
     *
     * @param delegate
     *            {@link Filter} which will be conditionally applied;
     * @param condition
     *            The {@link Condition} which have to be fulfilled in order to apply the filter.
     */
    public OptionalFilter(Filter delegate, Condition condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        if (condition.canApplyFilter(context, request)) {
            return delegate.filter(context, request, next);
        }
        return next.handle(context, request);
    }
}
