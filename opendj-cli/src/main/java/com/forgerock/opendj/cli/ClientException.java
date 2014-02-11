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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.ERR_CONSOLE_INPUT_ERROR;
import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * This class defines an exception that may be thrown if a local problem occurs in a Directory Server client.
 */
public class ClientException extends Exception implements LocalizableException {
    /**
     * The serial version identifier required to satisfy the compiler because this class extends
     * <CODE>java.lang.Exception</CODE>, which implements the <CODE>java.io.Serializable</CODE> interface. This value
     * was generated using the <CODE>serialver</CODE> command-line utility included with the Java SDK.
     */
    private static final long serialVersionUID = 1384120263337669664L;

    /** The return code. */
    private ReturnCode returnCode;

    /** The message linked to that exception. */
    private final LocalizableMessage message;

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
    public static ClientException adaptInputException(final Throwable cause) {
        return new ClientException(ReturnCode.ERROR_USER_DATA, ERR_CONSOLE_INPUT_ERROR.get(cause.getMessage()), cause);
    }

    /**
     * Creates a new client exception with the provided message.
     *
     * @param exitCode
     *            The exit code that may be used if the client considers this to be a fatal problem.
     * @param message
     *            The message that explains the problem that occurred.
     */
    public ClientException(ReturnCode exitCode, LocalizableMessage message) {
        super(message.toString());
        this.returnCode = exitCode;
        this.message = message;
    }

    /**
     * Creates a new client exception with the provided message and root cause.
     *
     * @param exitCode
     *            The exit code that may be used if the client considers this to be a fatal problem.
     * @param message
     *            The message that explains the problem that occurred.
     * @param cause
     *            The exception that was caught to trigger this exception.
     */
    public ClientException(ReturnCode exitCode, LocalizableMessage message, Throwable cause) {
        super(message.toString(), cause);
        this.returnCode = exitCode;
        this.message = message;
    }

    /**
     * Retrieves the exit code that the client may use if it considers this to be a fatal problem.
     *
     * @return The exit code that the client may use if it considers this to be a fatal problem.
     */
    public int getReturnCode() {
        return returnCode.get();
    }

    @Override
    public LocalizableMessage getMessageObject() {
        return message;
    }

}
