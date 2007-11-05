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
package org.opends.server.util.cli;



import static org.opends.messages.QuickSetupMessages.INFO_ERROR_EMPTY_RESPONSE;
import static org.opends.messages.UtilityMessages.*;
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

import org.opends.messages.Message;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.PasswordReader;


/**
 * This class provides an abstract base class which can be used as the
 * basis of a console-based application.
 */
public abstract class ConsoleApplication {

  /**
   * A null reader.
   */
  private static final class NullReader extends Reader {

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
  }

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
  protected ConsoleApplication(BufferedReader in, PrintStream out,
      PrintStream err) {
    if (in != null) {
      this.in = in;
    } else {
      this.in = new BufferedReader(new NullReader());
    }

    if (out != null) {
      this.out = out;
    } else {
      this.out = NullOutputStream.printStream();
    }

    if (err != null) {
      this.err = out;
    } else {
      this.err = NullOutputStream.printStream();
    }
  }



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
      this.in = new BufferedReader(new NullReader());
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
   * action. If the application is non-interactive, then the provided
   * default is returned automatically.
   *
   * @param prompt
   *          The prompt describing the action.
   * @param defaultValue
   *          The default value for the confirmation message. This
   *          will be returned if the application is non-interactive
   *          or if the user just presses return.
   * @return Returns <code>true</code> if the user wishes the action
   *         to be performed, or <code>false</code> if they refused,
   *         or if an exception occurred.
   * @throws CLIException
   *           If the user's response could not be read from the
   *           console for some reason.
   */
  public final boolean confirmAction(Message prompt, final boolean defaultValue)
      throws CLIException {
    if (!isInteractive()) {
      return defaultValue;
    }

    final Message yes = INFO_GENERAL_YES.get();
    final Message no = INFO_GENERAL_NO.get();
    final Message errMsg = ERR_CONSOLE_APP_CONFIRM.get(yes, no);
    prompt = INFO_MENU_PROMPT_CONFIRM.get(prompt, yes, no, defaultValue ? yes
        : no);

    ValidationCallback<Boolean> validator = new ValidationCallback<Boolean>() {

      public Boolean validate(ConsoleApplication app, String input) {
        String ninput = input.toLowerCase().trim();
        if (ninput.length() == 0) {
          return defaultValue;
        } else if (no.toString().startsWith(ninput)) {
          return false;
        } else if (yes.toString().startsWith(ninput)) {
          return true;
        } else {
          // Try again...
          app.println();
          app.println(errMsg);
          app.println();
        }

        return null;
      }
    };

    try {
      return readValidatedInput(prompt, validator);
    } catch (CLIException e) {
      // Should never happen.
      throw new RuntimeException(e);
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
   * Gets the application output stream.
   *
   * @return Returns the application output stream.
   */
  public final PrintStream getOutputStream() {
    return out;
  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested
   *         advanced mode.
   */
  public abstract boolean isAdvancedMode();



  /**
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  public abstract boolean isInteractive();



  /**
   * Indicates whether or not this console application is running in
   * its menu-driven mode. This can be used to dictate whether output
   * should go to the error stream or not. In addition, it may also
   * dictate whether or not sub-menus should display a cancel option
   * as well as a quit option.
   *
   * @return Returns <code>true</code> if this console application
   *         is running in its menu-driven mode.
   */
  public abstract boolean isMenuDrivenMode();



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
   * Interactively prompts the user to press return to continue. This
   * method should be called in situations where a user needs to be
   * given a chance to read some documentation before continuing
   * (continuing may cause the documentation to be scrolled out of
   * view).
   */
  public final void pressReturnToContinue() {
    Message msg = INFO_MENU_PROMPT_RETURN_TO_CONTINUE.get();
    try {
      readLineOfInput(msg);
    } catch (CLIException e) {
      // Ignore the exception - applications don't care.
    }
  }



  /**
   * Displays a blank line to the error stream.
   */
  public final void println() {
    err.println();
  }



  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void println(Message msg) {
    err.println(wrapText(msg, MAX_LINE_WIDTH));
  }


  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void print(Message msg) {
    err.print(wrapText(msg, MAX_LINE_WIDTH));
  }

  /**
   * Displays a blank line to the output stream if we are not in quiet mode.
   */
  public final void printlnProgress() {
    if (!isQuiet())
    {
      out.println();
    }
  }


  /**
   * Displays a message to the output stream if we are not in quiet mode.
   *
   * @param msg
   *          The message.
   */
  public final void printProgress(Message msg) {
    if (!isQuiet())
    {
      out.print(wrapText(msg, MAX_LINE_WIDTH));
    }
  }


  /**
   * Displays a message to the error stream indented by the specified
   * number of columns.
   *
   * @param msg
   *          The message.
   * @param indent
   *          The number of columns to indent.
   */
  public final void println(Message msg, int indent) {
    err.println(wrapText(msg, MAX_LINE_WIDTH, indent));
  }



  /**
   * Displays a message to the error stream if verbose mode is
   * enabled.
   *
   * @param msg
   *          The verbose message.
   */
  public final void printVerboseMessage(Message msg) {
    if (isVerbose() || isInteractive()) {
      err.println(wrapText(msg, MAX_LINE_WIDTH));
    }
  }



  /**
   * Interactively retrieves a line of input from the console.
   *
   * @param prompt
   *          The prompt.
   * @return Returns the line of input, or <code>null</code> if the
   *         end of input has been reached.
   * @throws CLIException
   *           If the line of input could not be retrieved for some
   *           reason.
   */
  public final String readLineOfInput(Message prompt) throws CLIException {
    err.print(wrapText(prompt + " ", MAX_LINE_WIDTH));
    try {
      String s = in.readLine();
      if (s == null) {
        throw CLIException
            .adaptInputException(new EOFException("End of input"));
      } else {
        return s;
      }
    } catch (IOException e) {
      throw CLIException.adaptInputException(e);
    }
  }

  /**
   * Commodity method that interactively prompts (on error output) the user to
   * provide a string value.  Any non-empty string will be allowed (the empty
   * string will indicate that the default should be used, if there is one).
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @throws CLIException
   *           If the line of input could not be retrieved for some
   *           reason.
   * @return  The string value read from the user.
   */
  public String readInput(Message prompt, String defaultValue)
  throws CLIException {
    while (true) {
      if (defaultValue != null) {
        prompt = Message.raw(prompt.toString()+EOL+"["+defaultValue+"]:");
      }
      String response = readLineOfInput(prompt);

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
   * Interactively retrieves a password from the console.
   *
   * @param prompt
   *          The password prompt.
   * @return Returns the password.
   * @throws CLIException
   *           If the password could not be retrieved for some reason.
   */
  public final String readPassword(Message prompt) throws CLIException {
    err.print(wrapText(prompt + " ", MAX_LINE_WIDTH));
    char[] pwChars;
    try {
      pwChars = PasswordReader.readPassword();
    } catch (Exception e) {
      throw CLIException.adaptInputException(e);
    }
    return new String(pwChars);
  }

  /**
   * Interactively retrieves a port value from the console.
   *
   * @param prompt
   *          The port prompt.
   * @param defaultValue
   *          The port default value.
   * @return Returns the port.
   * @throws CLIException
   *           If the port could not be retrieved for some reason.
   */
  public final int readPort(Message prompt, final int defaultValue)
  throws CLIException
  {
    ValidationCallback<Integer> callback = new ValidationCallback<Integer>()
    {
      public Integer validate(ConsoleApplication app, String input)
          throws CLIException
      {
        String ninput = input.trim();
        if (ninput.length() == 0)
        {
          return defaultValue;
        }
        else
        {
          try
          {
            int i = Integer.parseInt(ninput);
            if (i < 1 || i > 65535)
            {
              throw new NumberFormatException();
            }
            return i;
          }
          catch (NumberFormatException e)
          {
            // Try again...
            app.println();
            app.println(ERR_LDAP_CONN_BAD_PORT_NUMBER.get(ninput));
            app.println();
            return null;
          }
        }
      }

    };

    println();
    return readValidatedInput(INFO_LDAP_CONN_PROMPT_PORT_NUMBER
        .get(defaultValue), callback);
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
   * @throws CLIException
   *           If an unexpected error occurred which prevented
   *           validation.
   */
  public final <T> T readValidatedInput(Message prompt,
      ValidationCallback<T> validator) throws CLIException {
    while (true) {
      String response = readLineOfInput(prompt);
      T value = validator.validate(this, response);
      if (value != null) {
        return value;
      }
    }
  }
}
