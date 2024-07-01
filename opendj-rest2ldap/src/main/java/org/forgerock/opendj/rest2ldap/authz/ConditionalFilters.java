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

import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/** Encapsulate a {@link Condition} which must be fulfilled in order to apply the Filter. */
public final class ConditionalFilters {

    /** Encapsulate a {@link Filter} which will be processed only if the attached {@link Condition} is true. */
    public interface ConditionalFilter {
        /**
         * Get the filter which must be processed if the {@link Condition} evaluates to true.
         *
         * @return The filter to process.
         */
        Filter getFilter();

        /**
         * Get the {@link Condition} to evaluate.
         *
         * @return the {@link Condition} to evaluate.
         */
        Condition getCondition();
    }

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

    /** {@link Condition} which always returns true. */
    public static final Condition ALWAYS_TRUE = new Condition() {
        @Override
        public boolean canApplyFilter(Context context, Request request) {
            return true;
        }
    };

    /** {@link Condition} which always returns false. */
    public static final Condition ALWAYS_FALSE = new Condition() {
        @Override
        public boolean canApplyFilter(Context context, Request request) {
            return false;
        }
    };

    /** {@link ConditionalFilter} with an ALWAYS_FALSE {@link Condition}. */
    public static final ConditionalFilter NEVER_APPLICABLE = newConditionalFilter(new Filter() {
        @Override
        public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
            return Response.newResponsePromise(new Response(Status.NOT_IMPLEMENTED));
        }
    }, ALWAYS_FALSE);

    private ConditionalFilters() {
    }

    /**
     * Wrap a {@link Filter} into a {@link ConditionalFilter} with an ALWAYS_TRUE condition.
     *
     * @param filter
     *            The {@link Filter} to wrap.
     * @return a new {@link ConditionalFilter}
     * @throws NullPointerException
     *             if filter is null
     */
    public static ConditionalFilter asConditionalFilter(final Filter filter) {
        return newConditionalFilter(filter, ALWAYS_TRUE);
    }

    /**
     * Create a {@link ConditionalFilter} from a {@link Filter} and a {@link Condition}.
     *
     * @param filter
     *            {@link Filter} which must be processed if the condition is true.
     * @param condition
     *            {@link Condition} to evaluate.
     * @return a new {@link ConditionalFilter}
     * @throws NullPointerException
     *             if a parameter is null
     */
    public static ConditionalFilter newConditionalFilter(final Filter filter, final Condition condition) {
        checkNotNull(filter, "filter cannot be null");
        checkNotNull(condition, "condition cannot be null");
        return new ConditionalFilter() {
            @Override
            public Filter getFilter() {
                return filter;
            }

            @Override
            public Condition getCondition() {
                return condition;
            }
        };
    }
}
