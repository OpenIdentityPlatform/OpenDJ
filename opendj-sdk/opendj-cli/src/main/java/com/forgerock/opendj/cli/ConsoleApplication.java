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
 *      Portions copyright 2011-2014 ForgeRock AS
 *      Portions copyright 2011 Nemanja Lukić
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.INFO_ERROR_EMPTY_RESPONSE;
import static com.forgerock.opendj.cli.CliMessages.INFO_MENU_PROMPT_RETURN_TO_CONTINUE;
import static com.forgerock.opendj.cli.CliMessages.INFO_PROMPT_SINGLE_DEFAULT;
import static com.forgerock.opendj.cli.Utils.MAX_LINE_WIDTH;
import static com.forgerock.opendj.cli.Utils.wrapText;

import java.io.BufferedReader;
import java.io.Console;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class provides an abstract base class which can be used as the basis of a console-based application.
 */
public abstract class ConsoleApplication {

    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    private final InputStream in = System.in;

    private final PrintStream out;

    private final PrintStream err;

    private final Console console = System.console();

    /**
     * Creates a new console application instance.
     */
    public ConsoleApplication() {
        this(System.out, System.err);
    }

    /**
     * Creates a new console application instance with provided standard and error out streams.
     *
     * @param out
     *            The output stream.
     * @param err
     *            The error stream.
     */
    public ConsoleApplication(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Returns the application error stream.
     *
     * @return The application error stream.
     */
    public final PrintStream getErrorStream() {
        return err;
    }

    /**
     * Returns the application input stream.
     *
     * @return The application input stream.
     */
    public final InputStream getInputStream() {
        return in;
    }

    /**
     * Returns the application output stream.
     *
     * @return The application output stream.
     */
    public final PrintStream getOutputStream() {
        return out;
    }

    /**
     * Indicates whether or not the user has requested interactive behavior. The default implementation returns
     * {@code true}.
     *
     * @return {@code true} if the user has requested interactive behavior.
     */
    public boolean isInteractive() {
        return true;
    }

    /**
     * Indicates whether or not the user has requested quiet output. The default implementation returns {@code false}.
     *
     * @return {@code true} if the user has requested quiet output.
     */
    public boolean isQuiet() {
        return false;
    }

    /**
     * Indicates whether or not the user has requested script-friendly output. The default implementation returns
     * {@code false}.
     *
     * @return {@code true} if the user has requested script-friendly output.
     */
    public boolean isScriptFriendly() {
        return false;
    }

    /**
     * Indicates whether or not the user has requested verbose output. The default implementation returns {@code false}.
     *
     * @return {@code true} if the user has requested verbose output.
     */
    public boolean isVerbose() {
        return false;
    }

    /**
     * Interactively prompts the user to press return to continue. This method should be called in situations where a
     * user needs to be given a chance to read some documentation before continuing (continuing may cause the
     * documentation to be scrolled out of view).
     */
    public final void pressReturnToContinue() {
        final LocalizableMessage msg = INFO_MENU_PROMPT_RETURN_TO_CONTINUE.get();
        try {
            readLineOfInput(msg);
        } catch (final ClientException e) {
            // Ignore the exception - applications don't care.
        }
    }

    /**
     * Displays a message to the error stream.
     *
     * @param msg
     *            The message.
     */
    public final void errPrint(final LocalizableMessage msg) {
        getErrStream().print(wrap(msg));
    }

    /**
     * Displays a blank line to the error stream.
     */
    public final void errPrintln() {
        getErrStream().println();
    }

    /**
     * Displays a message to the error stream.
     *
     * @param msg
     *            The message.
     */
    public final void errPrintln(final LocalizableMessage msg) {
        getErrStream().println(wrap(msg));
    }

    /**
     * Displays a message to the error stream indented by the specified number of columns.
     *
     * @param msg
     *            The message.
     * @param indent
     *            The number of columns to indent.
     */
    public final void errPrintln(final LocalizableMessage msg, final int indent) {
        getErrStream().println(wrapText(msg, MAX_LINE_WIDTH, indent));
    }

    /**
     * Displays a message to the error stream if verbose mode is enabled.
     *
     * @param msg
     *            The verbose message.
     */
    public final void errPrintVerboseMessage(final LocalizableMessage msg) {
        if (isVerbose()) {
            getErrStream().println(wrap(msg));
        }
    }

    /**
     * Displays a message to the output stream.
     *
     * @param msg
     *            The message.
     */
    public final void print(final LocalizableMessage msg) {
        out.print(wrap(msg));
    }

    /**
     * Displays a blank line to the output stream.
     */
    public final void println() {
        out.println();
    }

    /**
     * Displays a message to the output stream.
     *
     * @param msg
     *            The message.
     */
    public final void println(final LocalizableMessage msg) {
        out.println(wrap(msg));
    }

    /**
     * Displays a message to the output stream indented by the specified number of columns.
     *
     * @param msg
     *            The message.
     * @param indent
     *            The number of columns to indent.
     */
    public final void println(final LocalizableMessage msg, final int indent) {
        out.println(wrapText(msg, MAX_LINE_WIDTH, indent));
    }

    /**
     * Displays a message to the output stream if verbose mode is enabled.
     *
     * @param msg
     *            The verbose message.
     */
    public final void printVerboseMessage(final LocalizableMessage msg) {
        if (isVerbose() || isInteractive()) {
            out.println(wrap(msg));
        }
    }

    /**
     * Interactively prompts (on error output) the user to provide a string value. Any non-empty string will be allowed
     * (the empty string will indicate that the default should be used, if there is one).
     *
     * @param prompt
     *            The prompt to present to the user.
     * @param defaultValue
     *            The default value to assume if the user presses ENTER without typing anything, or {@code null} if
     *            there should not be a default and the user must explicitly provide a value.
     * @throws ClientException
     *             If the line of input could not be retrieved for some reason.
     * @return The string value read from the user.
     */
    public final String readInput(LocalizableMessage prompt, final String defaultValue) throws ClientException {
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
     * @throws ClientException
     *             If the password could not be retrieved for some reason.
     */
    public final char[] readPassword(final LocalizableMessage prompt) throws ClientException {
        if (console != null) {
            if (prompt != null) {
                err.print(wrap(prompt));
                err.print(" ");
            }
            try {
                final char[] password = console.readPassword();
                if (password == null) {
                    throw new EOFException("End of input");
                }
                return password;
            } catch (final Throwable e) {
                throw ClientException.adaptInputException(e);
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
     * @throws ClientException
     *             If the line of input could not be retrieved for some reason.
     */
    private final String readLineOfInput(final LocalizableMessage prompt) throws ClientException {
        if (prompt != null) {
            err.print(wrap(prompt));
            err.print(" ");
        }
        try {
            final String s = reader.readLine();
            if (s == null) {
                throw ClientException.adaptInputException(new EOFException("End of input"));
            } else {
                return s;
            }
        } catch (final IOException e) {
            throw ClientException.adaptInputException(e);
        }
    }

    /**
     * Inserts line breaks into the provided buffer to wrap text at no more than the specified column width (80).
     *
     * @param msg
     *            The message to wrap.
     * @return The wrapped message.
     */
    private String wrap(final LocalizableMessage msg) {
        return wrapText(msg, MAX_LINE_WIDTH);
    }

    /**
     * Returns the error stream. Effectively, when an application is in "interactive mode" all the informations should
     * be written in the stdout.
     *
     * @return The error stream that should be used with this application.
     */
    private PrintStream getErrStream() {
        if (isInteractive()) {
            return out;
        } else {
            return err;
        }
    }

}
