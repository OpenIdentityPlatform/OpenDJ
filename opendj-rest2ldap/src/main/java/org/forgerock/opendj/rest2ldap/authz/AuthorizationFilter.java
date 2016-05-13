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
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This {@link Filter} is in charge of injecting an {@link AuthenticatedConnectionContext}. It tries each of the
 * provided filters until one can apply. If no filter can be applied, the last filter in the list will be applied
 * allowing it to formulate a valid, implementation specific, error response.
 */
final class AuthorizationFilter implements Filter {
    private static final Filter FORBIDDEN = new Filter() {
        @Override
        public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
            return Response.newResponsePromise(new Response(Status.FORBIDDEN));
        }
    };
    private final Iterable<? extends ConditionalFilter> filters;

    AuthorizationFilter(Iterable<? extends ConditionalFilter> filters) {
        this.filters = checkNotNull(filters, "filters cannot be null");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        Filter lastFilter = FORBIDDEN;
        for (ConditionalFilter filter : filters) {
            if (filter.getCondition().canApplyFilter(context, request)) {
                return filter.getFilter().filter(context, request, next);
            }
            lastFilter = filter.getFilter();
        }
        // Let the last filter in the chain formulate a valid error response.
        return lastFilter.filter(context, request, next);
    }
}
