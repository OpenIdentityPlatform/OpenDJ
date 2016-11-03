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

/**
 * Handle the processing of a request in a given context and return the resulting response.
 *
 * @param <CTX>
 *            Type of the context
 * @param <REQ>
 *            Type of the request to process
 * @param <REP>
 *            Type of the response
 */
public interface ReactiveHandler<CTX, REQ, REP> {
    /**
     * Process the request given the context.
     *
     * @param context
     *            Context in which the request must be processed
     * @param request
     *            The response to process
     * @return A {@link Single} response.
     * @throws Exception
     *             if the request cannot be processed
     */
    REP handle(final CTX context, final REQ request) throws Exception;
}
