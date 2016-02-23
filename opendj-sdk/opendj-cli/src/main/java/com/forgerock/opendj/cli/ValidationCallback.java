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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

/**
 * An interface for validating user input.
 *
 * @param <T>
 *            The type of the decoded input.
 */
public interface ValidationCallback<T> {

    /**
     * Validates and decodes the user-provided input. Implementations must validate
     * <code>input</code> and return the decoded value if the input is acceptable.
     * If the input is unacceptable, implementations must return
     * <code>null</code> and output a user friendly error message to the provided
     * application console.
     *
     * @param app
     *            The console application.
     * @param input
     *            The user input to be validated.
     * @return Returns the decoded input if the input is valid, or <code>null</code> if it is not.
     * @throws ClientException
     *             If an unexpected error occurred which prevented validation.
     */
    T validate(ConsoleApplication app, String input) throws ClientException;
}
