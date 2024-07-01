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
package org.forgerock.opendj.grizzly;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.ssl.SSLFilter;

/**
 * Implements server-side flow of StartTLS by replacing itself with a {@link SSLFilter} atomically once the first
 * message has been written. This first message is supposed to be the response of the StartTLS request which must be
 * written in clear-text mode.
 */
final class StartTLSFilter extends BaseFilter {
    private final SSLFilter sslFilter;

    StartTLSFilter(final SSLFilter sslFilter) {
        this.sslFilter = sslFilter;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        ctx.getFilterChain().set(ctx.getFilterIdx(), sslFilter);
        return ctx.getInvokeAction();
    }
}
