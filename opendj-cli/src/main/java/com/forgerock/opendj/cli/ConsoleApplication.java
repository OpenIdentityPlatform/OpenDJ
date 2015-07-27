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
 *      Portions copyright 2011-2015 ForgeRock AS
 *      Portions copyright 2011 Nemanja Lukić
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.Console;
import java.io.EOFException;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * This class provides an abstract base class which can be used as the basis of a console-based application.
 */
public abstract class ConsoleApplication {

    private static final int PROGRESS_LINE = 70;

    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private final InputStream in = System.in;
    private final PrintStream out;
    private final PrintStream err;
    private final Console console = System.console();

    private boolean isProgressSuite;

    /** Defines the different line styles for output. */
    public enum Style {
        /** Defines a title. */
        TITLE,
        /** Defines a subtitle. */
        SUBTITLE,
        /** Defines a notice. */
        NOTICE,
        /** Defines a normal line. */
        NORMAL,
        /** Defines an error. */
        ERROR,
        /** Defines a warning. */
        WARNING
    }

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
     * Indicates whether or not the user has requested advanced mode.
     *
     * @return Returns <code>true</code> if the user has requested advanced mode.
     */
    public boolean isAdvancedMode() {
        return false;
    }

    /**
     * Indicates whether or not this console application is running in its menu-driven mode. This can be used to dictate
     * whether output should go to the error stream or not. In addition, it may also dictate whether or not sub-menus
     * should display a cancel option as well as a quit option.
     *
     * @return Returns <code>true</code> if this console application is running in its menu-driven mode.
     */
    public boolean isMenuDrivenMode() {
        return false;
    }

    /**
     * Interactively prompts the user to press return to continue. This method should be called in situations where a
     * user needs to be given a chance to read some documentation before continuing (continuing may cause the
     * documentation to be scrolled out of view).
     */
    public final void pressReturnToContinue() {
        try {
            readLineOfInput(INFO_MENU_PROMPT_RETURN_TO_CONTINUE.get());
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
        if (!isQuiet()) {
            out.print(wrap(msg));
        }
    }

    /**
     * Displays a blank line to the output stream.
     */
    public final void println() {
        if (!isQuiet()) {
            out.println();
        }
    }

    /**
     * Displays a message to the output stream.
     *
     * @param msg
     *            The message.
     */
    public final void println(final LocalizableMessage msg) {
        if (!isQuiet()) {
            out.println(wrap(msg));
        }
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
        if (!isQuiet()) {
            out.println(wrapText(msg, MAX_LINE_WIDTH, indent));
        }
    }

    /**
     * Prints a progress bar on the same output stream line if not in quiet mode.
     *
     * <pre>
     * Like
     *   msg......   50%
     *   if progress is up to 100 :
     *   msg.....................  100%
     *   if progress is < 0 :
     *   msg....  FAIL
     *   msg.....................  FAIL
     * </pre>
     *
     * @param linePos
     *            The progress bar starts at this position on the line.
     * @param progress
     *            The current percentage progress to print.
     */
    private final void printProgressBar(final int linePos, final int progress) {
        if (!isQuiet()) {
            final int spacesLeft = MAX_LINE_WIDTH - linePos - 10;
            StringBuilder bar = new StringBuilder();
            if (progress != 0) {
                for (int i = 0; i < PROGRESS_LINE; i++) {
                    if (i < (Math.abs(progress) * spacesLeft) / 100 && bar.length() < spacesLeft) {
                        bar.append(".");
                    }
                }
            }
            bar.append(".   ");
            if (progress >= 0) {
                bar.append(progress).append("%     ");
            } else {
                bar.append("FAIL");
                isProgressSuite = false;
            }
            final int endBuilder = linePos + bar.length();
            for (int i = 0; i < endBuilder; i++) {
                bar.append("\b");
            }
            if (progress >= 100 || progress < 0) {
                bar.append(EOL);
                isProgressSuite = false;
            }
            out.print(bar);
        }
    }

    /**
     * Prints a progress bar on the same output stream line if not in quiet mode.
     * If the line's length is upper than the limit, the message is wrapped and the progress
     * bar is affected to the last one.
     * e.g.
     * <pre>
     *   Changing matching rule for 'userCertificate' and 'caCertificate' to
     *   CertificateExactMatch...............................................   100%
     * </pre>
     *
     * @param msg
     *            The message to display before the progress line.
     * @param progress
     *            The current percentage progress to print.
     * @param indent
     *            Indentation of the message.
     */
    public final void printProgressBar(String msg, final int progress, final int indent) {
        if (!isQuiet()) {
            String msgToDisplay = wrapText(msg, PROGRESS_LINE, indent);
            if (msgToDisplay.length() > PROGRESS_LINE) {
                final String[] msgWrapped = msgToDisplay.split(LINE_SEPARATOR);
                if (!isProgressSuite) {
                    for (int pos = 0; pos < msgWrapped.length - 1; pos++) {
                        println(LocalizableMessage.raw(msgWrapped[pos]));
                    }
                    isProgressSuite = true;
                }
                msgToDisplay = msgWrapped[msgWrapped.length - 1];
            }
            print(LocalizableMessage.raw(msgToDisplay));
            printProgressBar(msgToDisplay.length(), progress);
        }
    }

    /**
     * Print a line with EOL in the output stream.
     *
     * @param msgStyle
     *            The type of formatted output desired.
     * @param msg
     *            The message to display in normal mode.
     * @param indent
     *            The indentation.
     */
    public final void println(final Style msgStyle, final LocalizableMessage msg, final int indent) {
        if (!isQuiet()) {
            switch (msgStyle) {
            case TITLE:
                out.println();
                out.println(">>>> " + wrapText(msg, MAX_LINE_WIDTH, indent));
                out.println();
                break;
            case SUBTITLE:
                out.println(wrapText(msg, MAX_LINE_WIDTH, indent));
                out.println();
                break;
            case NOTICE:
                out.println(wrapText("* " + msg, MAX_LINE_WIDTH, indent));
                break;
            case ERROR:
                out.println();
                out.println(wrapText("** " + msg, MAX_LINE_WIDTH, indent));
                out.println();
                break;
            case WARNING:
                out.println(wrapText("[!] " + msg, MAX_LINE_WIDTH, indent));
                break;
            default:
                out.println(wrapText(msg, MAX_LINE_WIDTH, indent));
                break;
            }
        }
    }

    /**
     * Displays a message to the output stream if verbose mode is enabled.
     *
     * @param msg
     *            The verbose message.
     */
    public final void printVerboseMessage(final LocalizableMessage msg) {
        if (isVerbose()) {
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
        return readInput(prompt, defaultValue, null);
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
     * @param msgStyle
     *            The formatted style chosen.
     * @throws ClientException
     *             If the line of input could not be retrieved for some reason.
     * @return The string value read from the user.
     */
    public final String readInput(LocalizableMessage prompt, final String defaultValue, final Style msgStyle)
            throws ClientException {
        if (msgStyle != null && msgStyle == Style.TITLE) {
            println();
        }
        while (true) {
            if (defaultValue != null) {
                prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt, defaultValue);
            }
            final String response = readLineOfInput(prompt);

            if (msgStyle != null && (msgStyle == Style.TITLE || msgStyle == Style.SUBTITLE)) {
                println();
            }

            if ("".equals(response)) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                println(INFO_ERROR_EMPTY_RESPONSE.get());
            }
            return response;
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
                out.print(wrap(prompt));
                out.print(" ");
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
     * Reads a password from the console without echoing it to the client.
     * FIXME This method should disappear when all
     * the tools will extend to ConsoleApplication.
     *
     * @return The password as an array of characters.
     * @throws ClientException
     *             If an error occurs when reading the password.
     */
    public static char[] readPassword() throws ClientException {
        try {
            return System.console().readPassword();
        } catch (IOError e) {
            throw ClientException.adaptInputException(e);
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
    public final String readLineOfInput(final LocalizableMessage prompt) throws ClientException {
        if (prompt != null) {
            out.print(wrap(prompt));
            out.print(" ");
        }
        try {
            final String s = reader.readLine();
            if (s == null) {
                throw ClientException.adaptInputException(new EOFException("End of input"));
            }
            return s;
        } catch (final IOException e) {
            throw ClientException.adaptInputException(e);
        }
    }

    /**
     * Interactively retrieves a port value from the console.
     *
     * @param prompt
     *            The port prompt.
     * @param defaultValue
     *            The port default value.
     * @return Returns the port.
     * @throws ClientException
     *             If the port could not be retrieved for some reason.
     */
    public final int readPort(LocalizableMessage prompt, final int defaultValue) throws ClientException {
        final ValidationCallback<Integer> callback = new ValidationCallback<Integer>() {
            @Override
            public Integer validate(ConsoleApplication app, String input) throws ClientException {
                final String ninput = input.trim();
                if (ninput.length() == 0) {
                    return defaultValue;
                }

                try {
                    int i = Integer.parseInt(ninput);
                    if (i < 1 || i > 65535) {
                        throw new NumberFormatException();
                    }
                    return i;
                } catch (NumberFormatException e) {
                    // Try again...
                    app.println();
                    app.println(ERR_BAD_PORT_NUMBER.get(ninput));
                    app.println();
                    return null;
                }
            }

        };
        if (defaultValue != -1) {
            prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt, defaultValue);
        }

        return readValidatedInput(prompt, callback, CONFIRMATION_MAX_TRIES);
    }

    /**
     * Interactively prompts for user input and continues until valid input is provided.
     *
     * @param <T>
     *            The type of decoded user input.
     * @param prompt
     *            The interactive prompt which should be displayed on each input attempt.
     * @param validator
     *            An input validator responsible for validating and decoding the user's response.
     * @return Returns the decoded user's response.
     * @throws ClientException
     *             If an unexpected error occurred which prevented validation.
     */
    public final <T> T readValidatedInput(final LocalizableMessage prompt, final ValidationCallback<T> validator)
            throws ClientException {
        while (true) {
            final String response = readLineOfInput(prompt);
            final T value = validator.validate(this, response);
            if (value != null) {
                return value;
            }
        }
    }

    /**
     * Interactively prompts for user input and continues until valid input is provided.
     *
     * @param <T>
     *            The type of decoded user input.
     * @param prompt
     *            The interactive prompt which should be displayed on each input attempt.
     * @param validator
     *            An input validator responsible for validating and decoding the user's response.
     * @param maxTries
     *            The maximum number of tries that we can make.
     * @return Returns the decoded user's response.
     * @throws ClientException
     *             If an unexpected error occurred which prevented validation or if the maximum number of tries was
     *             reached.
     */
    public final <T> T readValidatedInput(final LocalizableMessage prompt, final ValidationCallback<T> validator,
            final int maxTries) throws ClientException {
        int nTries = 0;
        while (nTries < maxTries) {
            final String response = readLineOfInput(prompt);
            final T value = validator.validate(this, response);
            if (value != null) {
                return value;
            }
            nTries++;
        }
        throw new ClientException(ReturnCode.ERROR_USER_DATA, ERR_TRIES_LIMIT_REACHED.get(maxTries));
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
     * be written in the STDout.
     *
     * @return The error stream that should be used with this application.
     */
    protected PrintStream getErrStream() {
        if (isInteractive()) {
            return out;
        }
        return err;
    }

    /**
     * Commodity method that interactively confirms whether a user wishes to perform an action. If
     * the application is non-interactive, then the provided default is returned automatically. If there is an error an
     * error message is logged to the provided Logger and the default value is returned.
     *
     * @param prompt
     *            The prompt describing the action.
     * @param defaultValue
     *            The default value for the confirmation message. This will be returned if the application is
     *            non-interactive or if the user just presses return.
     * @param logger
     *            the Logger to be used to log the error message.
     * @return Returns <code>true</code> if the user wishes the action to be performed, or <code>false</code> if they
     *         refused.
     * @throws ClientException
     *             if the user did not provide valid answer after a certain number of tries
     *             (ConsoleApplication.CONFIRMATION_MAX_TRIES)
     */
    protected final boolean askConfirmation(LocalizableMessage prompt, boolean defaultValue, LocalizedLogger logger)
            throws ClientException {
        int nTries = 0;
        while (nTries < CONFIRMATION_MAX_TRIES) {
            nTries++;
            try {
                return confirmAction(prompt, defaultValue);
            } catch (ClientException ce) {
                if (ce.getMessageObject().toString().contains(ERR_CONFIRMATION_TRIES_LIMIT_REACHED.get(nTries))) {
                    throw ce;
                }
                logger.warn(LocalizableMessage.raw("Error reading input: " + ce, ce));
                // Try again...
                println();
            }
        }

        throw new ClientException(ReturnCode.ERROR_USER_DATA,
            ERR_CONFIRMATION_TRIES_LIMIT_REACHED.get(CONFIRMATION_MAX_TRIES));
    }

    /**
     * Interactively confirms whether a user wishes to perform an action.
     * If the application is non-interactive, then the provided default is returned automatically.
     *
     * @param prompt
     *            The prompt describing the action.
     * @param defaultValue
     *            The default value for the confirmation message. This will be returned if the application is
     *            non-interactive or if the user just presses return.
     * @return Returns <code>true</code> if the user wishes the action to be performed, or <code>false</code> if they
     *         refused, or if an exception occurred.
     * @throws ClientException
     *             If the user's response could not be read from the console for some reason.
     */
    public final boolean confirmAction(LocalizableMessage prompt, final boolean defaultValue) throws ClientException {
        if (!isInteractive()) {
            return defaultValue;
        }

        final LocalizableMessage yes = INFO_GENERAL_YES.get();
        final LocalizableMessage no = INFO_GENERAL_NO.get();
        final LocalizableMessage errMsg = ERR_CONSOLE_APP_CONFIRM.get(yes, no);
        prompt = INFO_MENU_PROMPT_CONFIRM.get(prompt, yes, no, defaultValue ? yes : no);

        ValidationCallback<Boolean> validator = new ValidationCallback<Boolean>() {

            @Override
            public Boolean validate(ConsoleApplication app, String input) {
                String ninput = input.toLowerCase().trim();
                if (ninput.length() == 0) {
                    return defaultValue;
                } else if (no.toString().toLowerCase().startsWith(ninput)) {
                    return false;
                } else if (yes.toString().toLowerCase().startsWith(ninput)) {
                    return true;
                } else {
                    // Try again...
                    app.println();
                    app.println(errMsg);
                    app.println();
                    return null;
                }
            }
        };

        return readValidatedInput(prompt, validator, CONFIRMATION_MAX_TRIES);
    }

    /**
     * Commodity method used to repeatedly ask the user to provide a port value.
     *
     * @param prompt
     *            the prompt message.
     * @param defaultValue
     *            the default value of the port to be proposed to the user.
     * @param logger
     *            the logger where the errors will be written.
     * @return the port value provided by the user.
     */
    protected int askPort(LocalizableMessage prompt, int defaultValue, LocalizedLogger logger) {
        while (true) {
            try {
                int port = readPort(prompt, defaultValue);
                if (port != -1) {
                    return port;
                }
            } catch (ClientException ce) {
                logger.warn(LocalizableMessage.raw("Error reading input: " + ce, ce));
            }
        }
    }

    /**
     * Prints a header in the console application.
     *
     * @param header
     *            The message to display as a header.
     */
    void printHeader(final LocalizableMessage header) {
        println();
        println();
        println(header);
        println();
    }
}
