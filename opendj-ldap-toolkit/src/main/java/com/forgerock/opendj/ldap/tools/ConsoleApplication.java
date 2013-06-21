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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 *      Portions copyright 2011 Nemanja Lukić
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_ERROR_EMPTY_RESPONSE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_MENU_PROMPT_RETURN_TO_CONTINUE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_PROMPT_SINGLE_DEFAULT;
import static com.forgerock.opendj.ldap.tools.Utils.MAX_LINE_WIDTH;
import static com.forgerock.opendj.ldap.tools.Utils.wrapText;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.Console;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class provides an abstract base class which can be used as the basis of
 * a console-based application.
 */
abstract class ConsoleApplication {
    private final PrintStream err = new PrintStream(System.err);

    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    private final InputStream in = System.in;

    private final PrintStream out = new PrintStream(System.out);

    private final Console console = System.console();

    /**
     * Creates a new console application instance.
     */
    ConsoleApplication() {
        // Nothing to do.
    }

    /**
     * Closes the provided {@code Closeable}s if they are not {@code null}.
     *
     * @param closeables
     *          The closeables to be closed.
     */
    final void closeIfNotNull(Closeable... closeables) {
        if (closeables == null) {
            return;
        }
        for (Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // Do nothing.
                }
            }
        }
    }

    /**
     * Returns the application error stream.
     *
     * @return The application error stream.
     */
    final PrintStream getErrorStream() {
        return err;
    }

    /**
     * Returns the application input stream.
     *
     * @return The application input stream.
     */
    final InputStream getInputStream() {
        return in;
    }

    /**
     * Returns the application output stream.
     *
     * @return The application output stream.
     */
    final PrintStream getOutputStream() {
        return out;
    }

    /**
     * Indicates whether or not the user has requested interactive behavior. The
     * default implementation returns {@code true}.
     *
     * @return {@code true} if the user has requested interactive behavior.
     */
    boolean isInteractive() {
        return true;
    }

    /**
     * Indicates whether or not the user has requested quiet output. The default
     * implementation returns {@code false}.
     *
     * @return {@code true} if the user has requested quiet output.
     */
    boolean isQuiet() {
        return false;
    }

    /**
     * Indicates whether or not the user has requested script-friendly output.
     * The default implementation returns {@code false}.
     *
     * @return {@code true} if the user has requested script-friendly output.
     */
    boolean isScriptFriendly() {
        return false;
    }

    /**
     * Indicates whether or not the user has requested verbose output. The
     * default implementation returns {@code false}.
     *
     * @return {@code true} if the user has requested verbose output.
     */
    boolean isVerbose() {
        return false;
    }

    /**
     * Interactively prompts the user to press return to continue. This method
     * should be called in situations where a user needs to be given a chance to
     * read some documentation before continuing (continuing may cause the
     * documentation to be scrolled out of view).
     */
    final void pressReturnToContinue() {
        final LocalizableMessage msg = INFO_MENU_PROMPT_RETURN_TO_CONTINUE.get();
        try {
            readLineOfInput(msg);
        } catch (final CLIException e) {
            // Ignore the exception - applications don't care.
        }
    }

    /**
     * Displays a message to the error stream.
     *
     * @param msg
     *            The message.
     */
    final void print(final LocalizableMessage msg) {
        err.print(wrapText(msg, MAX_LINE_WIDTH));
    }

    /**
     * Displays a blank line to the error stream.
     */
    final void println() {
        err.println();
    }

    /**
     * Displays a message to the error stream.
     *
     * @param msg
     *            The message.
     */
    final void println(final LocalizableMessage msg) {
        err.println(wrapText(msg, MAX_LINE_WIDTH));
    }

    /**
     * Displays a message to the error stream indented by the specified number
     * of columns.
     *
     * @param msg
     *            The message.
     * @param indent
     *            The number of columns to indent.
     */
    final void println(final LocalizableMessage msg, final int indent) {
        err.println(wrapText(msg, MAX_LINE_WIDTH, indent));
    }

    /**
     * Displays a message to the error stream if verbose mode is enabled.
     *
     * @param msg
     *            The verbose message.
     */
    final void printVerboseMessage(final LocalizableMessage msg) {
        if (isVerbose() || isInteractive()) {
            err.println(wrapText(msg, MAX_LINE_WIDTH));
        }
    }

    /**
     * Interactively prompts (on error output) the user to provide a string
     * value. Any non-empty string will be allowed (the empty string will
     * indicate that the default should be used, if there is one).
     *
     * @param prompt
     *            The prompt to present to the user.
     * @param defaultValue
     *            The default value to assume if the user presses ENTER without
     *            typing anything, or {@code null} if there should not be a
     *            default and the user must explicitly provide a value.
     * @throws CLIException
     *             If the line of input could not be retrieved for some reason.
     * @return The string value read from the user.
     */
    final String readInput(LocalizableMessage prompt, final String defaultValue)
            throws CLIException {
        while (true) {
            if (defaultValue != null) {
                prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(), defaultValue);
            }
            final String response = readLineOfInput(prompt);

            if ("".equals(response)) {
                if (defaultValue == null) {
                    print(INFO_ERROR_EMPTY_RESPONSE.get());
                } else {
                    return defaultValue;
                }
            } else {
                return response;
            }
        }
    }

    /**
     * Interactively reads a password from the console.
     *
     * @param prompt
     *            The password prompt.
     * @return The password.
     * @throws CLIException
     *             If the password could not be retrieved for some reason.
     */
    final char[] readPassword(final LocalizableMessage prompt) throws CLIException {
        if (console != null) {
            if (prompt != null) {
                err.print(wrapText(prompt, MAX_LINE_WIDTH));
                err.print(" ");
            }
            try {
                final char[] password = console.readPassword();
                if (password == null) {
                    throw new EOFException("End of input");
                }
                return password;
            } catch (final Throwable e) {
                throw CLIException.adaptInputException(e);
            }
        } else {
            // FIXME: should go direct to char[] and avoid the String.
            return readLineOfInput(prompt).toCharArray();
        }
    }

    /**
     * Interactively retrieves a line of input from the console.
     *
     * @param prompt
     *            The prompt.
     * @return The line of input.
     * @throws CLIException
     *             If the line of input could not be retrieved for some reason.
     */
    private final String readLineOfInput(final LocalizableMessage prompt) throws CLIException {
        if (prompt != null) {
            err.print(wrapText(prompt, MAX_LINE_WIDTH));
            err.print(" ");
        }
        try {
            final String s = reader.readLine();
            if (s == null) {
                throw CLIException.adaptInputException(new EOFException("End of input"));
            } else {
                return s;
            }
        } catch (final IOException e) {
            throw CLIException.adaptInputException(e);
        }
    }

}
