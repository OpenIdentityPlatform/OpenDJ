/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_CONSOLE_INPUT_ERROR;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Thrown to indicate that a problem occurred when interacting with the client.
 * For example, if input provided by the client was invalid.
 */
@SuppressWarnings("serial")
final class CLIException extends Exception implements LocalizableException {

    /**
     * Adapts any exception that may have occurred whilst reading input from the
     * console.
     *
     * @param cause
     *            The exception that occurred whilst reading input from the
     *            console.
     * @return Returns a new CLI exception describing a problem that occurred
     *         whilst reading input from the console.
     */
    static CLIException adaptInputException(final Throwable cause) {
        return new CLIException(ERR_CONSOLE_INPUT_ERROR.get(cause.getMessage()), cause);
    }

    private final LocalizableMessage message;

    /**
     * Creates a new CLI exception with the provided message.
     *
     * @param message
     *            The message explaining the problem that occurred.
     */
    CLIException(final LocalizableMessage message) {
        super(message.toString());
        this.message = message;
    }

    /**
     * Creates a new CLI exception with the provided message and cause.
     *
     * @param message
     *            The message explaining the problem that occurred.
     * @param cause
     *            The cause of this exception.
     */
    CLIException(final LocalizableMessage message, final Throwable cause) {
        super(message.toString(), cause);
        this.message = message;
    }

    public LocalizableMessage getMessageObject() {
        return message;
    }

}
