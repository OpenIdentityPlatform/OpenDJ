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
 * Handle the processing of an input in a given context and return the resulting output.
 *
 * @param <C>
 *            Type of the context
 * @param <I>
 *            Type of the input to process
 * @param <O>
 *            Type of the output as a result of the process
 */
public interface ReactiveHandler<C, I, O> {
    /**
     * Process an input given the context.
     *
     * @param context
     *            Context in which the input must be processed
     * @param input
     *            The input to process
     * @return An output.
     * @throws Exception
     *             if the request cannot be processed
     */
    O handle(final C context, final I input) throws Exception;
}
