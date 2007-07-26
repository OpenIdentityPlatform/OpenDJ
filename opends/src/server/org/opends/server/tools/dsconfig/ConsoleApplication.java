/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.List;

import org.opends.server.admin.client.ManagementContext;
import org.opends.server.tools.ClientException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.Validator;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;



/**
 * This class provides an abstract base class which can be used as the
 * basis of a console-based application.
 */
public abstract class ConsoleApplication {

  // The error stream which this application should use.
  private final PrintStream err;

  // The input stream reader which this application should use.
  private final BufferedReader in;

  // The output stream which this application should use.
  private final PrintStream out;



  /**
   * Creates a new console application instance.
   *
   * @param in
   *          The application input stream.
   * @param out
   *          The application output stream.
   * @param err
   *          The application error stream.
   */
  protected ConsoleApplication(InputStream in, OutputStream out,
      OutputStream err) {
    if (in != null) {
      this.in = new BufferedReader(new InputStreamReader(in));
    } else {
      this.in = new BufferedReader(new Reader() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
          // Do nothing.
        }



        /**
         * {@inheritDoc}
         */
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
          return -1;
        }

      });
    }

    if (out != null) {
      this.out = new PrintStream(out);
    } else {
      this.out = NullOutputStream.printStream();
    }

    if (err != null) {
      this.err = new PrintStream(err);
    } else {
      this.err = NullOutputStream.printStream();
    }
  }



  /**
   * Interactively confirms whether a user wishes to perform an
   * action. If the application is non-interactive, then the action is
   * granted by default.
   *
   * @param prompt
   *          The prompt describing the action.
   * @return Returns <code>true</code> if the user wishes the action
   *         to be performed, or <code>false</code> if they refused,
   *         or if an exception occurred.
   * @throws ArgumentException
   *           If the user's response could not be read from the
   *           console for some reason.
   */
  public final boolean confirmAction(String prompt) throws ArgumentException {
    if (!isInteractive()) {
      return true;
    }

    final String yes = getMessage(MSGID_DSCFG_GENERAL_CONFIRM_YES);
    final String no = getMessage(MSGID_DSCFG_GENERAL_CONFIRM_NO);
    final String errMsg =
      getMessage(MSGID_DSCFG_ERROR_GENERAL_CONFIRM, yes, no);
    prompt = prompt + String.format(" (%s / %s): ", yes, no);

    ValidationCallback<Boolean> validator = new ValidationCallback<Boolean>() {

      public Boolean validate(ConsoleApplication app, String input) {
        String ninput = input.toLowerCase().trim();
        if (ninput.length() == 0) {
          // Empty input.
          app.printMessage(errMsg);
        } else if (no.startsWith(ninput)) {
          return false;
        } else if (yes.startsWith(ninput)) {
          return true;
        } else {
          // Try again...
          app.printMessage(errMsg);
        }

        return null;
      }
    };

    try {
      return readValidatedInput(prompt, validator);
    } catch (ClientException e) {
      // Should never happen.
      throw new RuntimeException(e);
    }
  }



  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void printMessage(String msg) {
    err.println(wrapText(msg, MAX_LINE_WIDTH));
  }



  /**
   * Displays a blank line to the error stream.
   */
  public final void println() {
    err.println();
  }



  /**
   * Displays a message to the error stream if verbose mode is
   * enabled.
   *
   * @param msg
   *          The verbose message.
   */
  public final void printVerboseMessage(String msg) {
    if (isVerbose()) {
      err.println(wrapText(msg, MAX_LINE_WIDTH));
    }
  }



  /**
   * Gets the application error stream.
   *
   * @return Returns the application error stream.
   */
  public final PrintStream getErrorStream() {
    return err;
  }



  /**
   * Gets the application input stream.
   *
   * @return Returns the application input stream.
   */
  public final BufferedReader getInputStream() {
    return in;
  }



  /**
   * Gets the management context which sub-commands should use in
   * order to manage the directory server.
   *
   * @return Returns the management context which sub-commands should
   *         use in order to manage the directory server.
   * @throws ArgumentException
   *           If a management context related argument could not be
   *           parsed successfully.
   * @throws ClientException
   *           If the management context could not be created.
   */
  public abstract ManagementContext getManagementContext()
      throws ArgumentException, ClientException;



  /**
   * Gets the application output stream.
   *
   * @return Returns the application output stream.
   */
  public final PrintStream getOutputStream() {
    return out;
  }



  /**
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  public abstract boolean isInteractive();



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         quiet output.
   */
  public abstract boolean isQuiet();



  /**
   * Indicates whether or not the user has requested script-friendly
   * output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         script-friendly output.
   */
  public abstract boolean isScriptFriendly();



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         verbose output.
   */
  public abstract boolean isVerbose();



  /**
   * Interactively prompts the user to select from a choice of
   * options.
   *
   * @param <T>
   *          The type of the values represented by each choice.
   * @param prompt
   *          The prompt which should appear before the list of
   *          choices.
   * @param descriptions
   *          The descriptions of each choice.
   * @param values
   *          The choices.
   * @param helpCallback
   *          An optional help call-back which can be used to display
   *          additional help.
   * @return Returns the selected value.
   * @throws ArgumentException
   *           If the user input could not be retrieved for some
   *           reason.
   */
  public final <T> T readChoice(final String prompt, List<String> descriptions,
      List<T> values, final HelpCallback helpCallback)
      throws ArgumentException {
    Validator.ensureTrue(descriptions.size() == values.size());

    // Output main prompt.
    println();
    printMessage(prompt);
    println();

    // Build the table of choices.
    final TableBuilder builder = new TableBuilder();
    final int size = descriptions.size();
    for (int i = 0; i < size; i++) {
      builder.startRow();
      builder.appendCell("[" + (i + 1) + "]");
      builder.appendCell(descriptions.get(i));
    }

    // Display the table of choices.
    final TextTablePrinter printer = new TextTablePrinter(err);
    printer.setDisplayHeadings(false);
    printer.setColumnWidth(1, 0);
    builder.print(printer);

    // Get the user input.
    String promptMsg;
    if (helpCallback != null) {
      promptMsg = getMessage(MSGID_DSCFG_GENERAL_CHOICE_PROMPT_HELP, size);
    } else {
      promptMsg = getMessage(MSGID_DSCFG_GENERAL_CHOICE_PROMPT_NOHELP, size);
    }

    ValidationCallback<Integer> validator = new ValidationCallback<Integer>() {

      public Integer validate(ConsoleApplication app, String input) {
        String ninput = input.trim();

        if (ninput.equals("?") && helpCallback != null) {
          app.println();
          helpCallback.display(app);
          app.println();

          // Output main prompt.
          printMessage(prompt);
          println();
          builder.print(printer);
          println();

          return null;
        } else {
          try {
            int i = Integer.parseInt(ninput);
            if (i < 1 || i > size) {
              throw new NumberFormatException();
            }
            return i;
          } catch (NumberFormatException e) {
            app.println();
            String errMsg = getMessage(MSGID_DSCFG_ERROR_GENERAL_CHOICE, size);
            app.printMessage(errMsg);
            app.println();
            return null;
          }
        }
      }
    };

    // Get the choice.
    int choice;
    try {
      choice = readValidatedInput(promptMsg, validator);
    } catch (ClientException e) {
      // Should never happen.
      throw new RuntimeException(e);
    }
    return values.get(choice - 1);
  }



  /**
   * Interactively retrieves a line of input from the console.
   *
   * @param prompt
   *          The prompt.
   * @return Returns the line of input, or <code>null</code> if the
   *         end of input has been reached.
   * @throws ArgumentException
   *           If the line of input could not be retrieved for some
   *           reason.
   */
  public final String readLineOfInput(String prompt) throws ArgumentException {
    err.println();
    err.print(wrapText(prompt.trim() + " ", MAX_LINE_WIDTH));
    try {
      return in.readLine();
    } catch (IOException e) {
      throw ArgumentExceptionFactory.unableToReadConsoleInput(e);
    }
  }



  /**
   * Interactively retrieves a password from the console.
   *
   * @param prompt
   *          The password prompt.
   * @return Returns the password.
   * @throws ArgumentException
   *           If the password could not be retrieved for some reason.
   */
  public final String readPassword(String prompt) throws ArgumentException {
    err.print(wrapText(prompt + " ", MAX_LINE_WIDTH));
    char[] pwChars;
    try {
      pwChars = PasswordReader.readPassword();
    } catch (Exception e) {
      throw ArgumentExceptionFactory.unableToReadConsoleInput(e);
    }
    return new String(pwChars);
  }



  /**
   * Interactively prompts for user input and continues until valid
   * input is provided.
   *
   * @param <T>
   *          The type of decoded user input.
   * @param prompt
   *          The interactive prompt which should be displayed on each
   *          input attempt.
   * @param validator
   *          An input validator responsible for validating and
   *          decoding the user's response.
   * @return Returns the decoded user's response.
   * @throws ArgumentException
   *           If the line of input could not be retrieved for some
   *           reason.
   * @throws ClientException
   *           If an unexpected error occurred which prevented
   *           validation.
   */
  public final <T> T readValidatedInput(String prompt,
      ValidationCallback<T> validator) throws ArgumentException,
      ClientException {
    while (true) {
      String response = readLineOfInput(prompt);

      if (response == null) {
        throw ArgumentExceptionFactory
            .unableToReadConsoleInput(new EOFException("End of input"));
      }

      T value = validator.validate(this, response);
      if (value != null) {
        return value;
      }
    }
  }
}
