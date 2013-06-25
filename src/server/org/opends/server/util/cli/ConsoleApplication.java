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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2013 ForgeRock AS
 */
package org.opends.server.util.cli;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.DSConfigMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.OpendsCertificateException;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.SetupUtils;


/**
 * This class provides an abstract base class which can be used as the basis of
 * a console-based application.
 */
public abstract class ConsoleApplication
{

  /**
   * A null reader.
   */
  private static final class NullReader extends Reader
  {

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
      // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException
    {
      return -1;
    }
  }

  /**
   * Defines the different line styles for output.
   */
  public enum Style {
    /**
     * Defines a title.
     */
    TITLE,
    /**
     * Defines a subtitle.
     */
    SUBTITLE,
    /**
     * Defines a notice.
     */
    NOTICE,
    /**
     * Defines a normal line.
     */
    NORMAL,
    /**
     * Defines an error.
     */
    ERROR,
    /**
     * Defines a breakline.
     */
    BREAKLINE,
  }

  // The error stream which this application should use.
  private final PrintStream err;

  // The input stream reader which this application should use.
  private final BufferedReader in;

  // The output stream which this application should use.
  private final PrintStream out;

  /**
   * The maximum number of times we try to confirm.
   */
  protected final static int CONFIRMATION_MAX_TRIES = 5;

  private static final String COMMENT_SHELL_UNIX = "# ";
  private static final String COMMENT_BATCH_WINDOWS = "rem ";

  /**
   * The String used to write comments in a shell (or batch) script.
   */
  protected static final String SHELL_COMMENT_SEPARATOR = SetupUtils
      .isWindows() ? COMMENT_BATCH_WINDOWS : COMMENT_SHELL_UNIX;

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
      PrintStream err)
  {
    if (in != null)
    {
      this.in = in;
    }
    else
    {
      this.in = new BufferedReader(new NullReader());
    }

    if (out != null)
    {
      this.out = out;
    }
    else
    {
      this.out = NullOutputStream.printStream();
    }

    if (err != null)
    {
      this.err = out;
    }
    else
    {
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
      OutputStream err)
  {
    if (in != null)
    {
      this.in = new BufferedReader(new InputStreamReader(in));
    }
    else
    {
      this.in = new BufferedReader(new NullReader());
    }

    if (out != null)
    {
      this.out = new PrintStream(out);
    }
    else
    {
      this.out = NullOutputStream.printStream();
    }

    if (err != null)
    {
      this.err = new PrintStream(err);
    }
    else
    {
      this.err = NullOutputStream.printStream();
    }
  }

  /**
   * Interactively confirms whether a user wishes to perform an action. If the
   * application is non-interactive, then the provided default is returned
   * automatically.
   *
   * @param prompt
   *          The prompt describing the action.
   * @param defaultValue
   *          The default value for the confirmation message. This will be
   *          returned if the application is non-interactive or if the user just
   *          presses return.
   * @return Returns <code>true</code> if the user wishes the action to be
   *         performed, or <code>false</code> if they refused, or if an
   *         exception occurred.
   * @throws CLIException
   *           If the user's response could not be read from the console for
   *           some reason.
   */
  public final boolean confirmAction(Message prompt, final boolean defaultValue)
      throws CLIException
  {
    if (!isInteractive())
    {
      return defaultValue;
    }

    final Message yes = INFO_GENERAL_YES.get();
    final Message no = INFO_GENERAL_NO.get();
    final Message errMsg = ERR_CONSOLE_APP_CONFIRM.get(yes, no);
    prompt =
        INFO_MENU_PROMPT_CONFIRM.get(prompt, yes, no, defaultValue ? yes : no);

    ValidationCallback<Boolean> validator = new ValidationCallback<Boolean>()
    {

      @Override
      public Boolean validate(ConsoleApplication app, String input)
      {
        String ninput = input.toLowerCase().trim();
        if (ninput.length() == 0)
        {
          return defaultValue;
        }
        else if (no.toString().toLowerCase().startsWith(ninput))
        {
          return false;
        }
        else if (yes.toString().toLowerCase().startsWith(ninput))
        {
          return true;
        }
        else
        {
          // Try again...
          app.println();
          app.println(errMsg);
          app.println();
        }

        return null;
      }
    };

    return readValidatedInput(prompt, validator, CONFIRMATION_MAX_TRIES);
  }

  /**
   * Gets the application error stream.
   *
   * @return Returns the application error stream.
   */
  public final PrintStream getErrorStream()
  {
    return err;
  }

  /**
   * Gets the application input stream.
   *
   * @return Returns the application input stream.
   */
  public final BufferedReader getInputStream()
  {
    return in;
  }

  /**
   * Gets the application output stream.
   *
   * @return Returns the application output stream.
   */
  public final PrintStream getOutputStream()
  {
    return out;
  }

  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested advanced mode.
   */
  public abstract boolean isAdvancedMode();

  /**
   * Indicates whether or not the user has requested interactive behavior.
   *
   * @return Returns <code>true</code> if the user has requested interactive
   *         behavior.
   */
  public abstract boolean isInteractive();

  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should go to
   * the error stream or not. In addition, it may also dictate whether or not
   * sub-menus should display a cancel option as well as a quit option.
   *
   * @return Returns <code>true</code> if this console application is running in
   *         its menu-driven mode.
   */
  public abstract boolean isMenuDrivenMode();

  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet output.
   */
  public abstract boolean isQuiet();

  /**
   * Indicates whether or not the user has requested script-friendly output.
   *
   * @return Returns <code>true</code> if the user has requested script-friendly
   *         output.
   */
  public abstract boolean isScriptFriendly();

  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose output.
   */
  public abstract boolean isVerbose();

  /**
   * Interactively prompts the user to press return to continue. This method
   * should be called in situations where a user needs to be given a chance to
   * read some documentation before continuing (continuing may cause the
   * documentation to be scrolled out of view).
   */
  public final void pressReturnToContinue()
  {
    Message msg = INFO_MENU_PROMPT_RETURN_TO_CONTINUE.get();
    try
    {
      readLineOfInput(msg);
    }
    catch (CLIException e)
    {
      // Ignore the exception - applications don't care.
    }
  }

  /**
   * Displays a blank line to the error stream.
   */
  public final void println()
  {
    err.println();
  }

  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void println(Message msg)
  {
    err.println(wrapText(msg, MAX_LINE_WIDTH));
  }

  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void print(Message msg)
  {
    err.print(wrapText(msg, MAX_LINE_WIDTH));
  }

  /**
   * Print a line with EOL in the output stream.
   *
   * @param msg
   *          The message to display in normal mode.
   * @param indent
   *          The indentation.
   */
  public final void println(final Message msg, final int indent)
  {
    println(Style.NORMAL, msg, indent);
  }

  /**
   * Print a line with EOL in the output stream.
   *
   * @param msgStyle
   *          The type of formatted output desired.
   * @param msg
   *          The message to display in normal mode.
   * @param indent
   *          The indentation.
   */
  public final void println(final Style msgStyle, final Message msg,
      final int indent)
  {
    if (!isQuiet())
    {
      switch (msgStyle)
      {
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
        break;
      case BREAKLINE:
        out.println();
        break;
      default:
        out.println(wrapText(msg, MAX_LINE_WIDTH, indent));
        break;
      }
    }
  }

  /**
   * Displays a blank line to the output stream if we are not in quiet mode.
   */
  public final void printlnProgress()
  {
    if (!isQuiet())
    {
      out.println();
    }
  }

  /**
   * Displays a message to the output stream if we are not in quiet mode.
   * Message is wrap to max line width.
   *
   * @param msg
   *          The message.
   */
  public final void printlnProgress(Message msg)
  {
    if (!isQuiet())
    {
      out.println(wrapText(msg, MAX_LINE_WIDTH));
    }
  }

  /**
   * Displays a message to the output stream if we are not in quiet mode.
   *
   * @param msg
   *          The message.
   */
  public final void printProgress(final Message msg)
  {
    if (!isQuiet())
    {
      out.print(msg);
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
   *          The progress bar starts at this position on the line.
   * @param progress
   *          The current percentage progress to print.
   */
  public final void printProgressBar(final int linePos, final int progress)
  {
    if (!isQuiet())
    {
      final int spacesLeft = MAX_LINE_WIDTH - linePos - 10;
      StringBuilder bar = new StringBuilder();
      if (progress != 0)
      {
        for (int i = 0; i < 50; i++)
        {
          if ((i < (Math.abs(progress) / 2)) && (bar.length() < spacesLeft))
          {
            bar.append(".");
          }
        }
      }
      bar.append(".   ");
      if(progress >= 0) {
        bar.append(progress).append("%     ");
      } else {
        bar.append("FAIL");
      }
      final int endBuilder = linePos + bar.length();
      for (int i = 0; i < endBuilder; i++)
      {
        bar.append("\b");
      }
      if (progress >= 100 || progress < 0)
      {
        bar.append(EOL);
      }
      out.print(bar.toString());
    }
  }

  /**
   * Display the batch progress string to the error stream, if we are not in
   * quiet mode.
   *
   * @param s
   *          The string to display
   */
  public final void printlnBatchProgress(String s)
  {
    if (!isQuiet())
    {
      err.println(s);
    }
  }

  /**
   * Displays a message to the error stream indented by the specified number of
   * columns.
   *
   * @param msg
   *          The message.
   * @param indent
   *          The number of columns to indent.
   */
  public final void printErrln(Message msg, int indent)
  {
    err.println(wrapText(msg, MAX_LINE_WIDTH, indent));
  }

  /**
   * Displays a message to the error stream if verbose mode is enabled.
   *
   * @param msg
   *          The verbose message.
   */
  public final void printVerboseMessage(Message msg)
  {
    if (isVerbose() || isInteractive())
    {
      err.println(wrapText(msg, MAX_LINE_WIDTH));
    }
  }

  /**
   * Interactively retrieves a line of input from the console.
   *
   * @param prompt
   *          The prompt.
   * @return Returns the line of input, or <code>null</code> if the end of input
   *         has been reached.
   * @throws CLIException
   *           If the line of input could not be retrieved for some reason.
   */
  public final String readLineOfInput(Message prompt) throws CLIException
  {
    if (prompt != null)
    {
      err.print(wrapText(prompt, MAX_LINE_WIDTH));
      err.print(" ");
    }
    try
    {
      String s = in.readLine();
      if (s == null)
      {
        throw CLIException
            .adaptInputException(new EOFException("End of input"));
      }
      else
      {
        return s;
      }
    }
    catch (IOException e)
    {
      throw CLIException.adaptInputException(e);
    }
  }

  /**
   * Displays a message and read the user's input from output.
   *
   * @param prompt
   *          The message to display.
   * @param defaultValue
   *          The default answer by default.
   * @param msgStyle
   *          The formatted style chosen.
   * @return The user's input as a string.
   * @throws CLIException
   *           If an Exception occurs during the process.
   */
  public final String readInput(final Message prompt,
      final String defaultValue, final Style msgStyle)
      throws CLIException
  {
    String answer = null;
    final Message messageToDisplay =
        INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(), defaultValue);
    if (msgStyle == Style.TITLE)
    {
      println();
    }
    print(messageToDisplay);
    out.print(" ");

    try
    {
      // Reads the user input.
      answer = in.readLine();
    }
    catch (IOException e)
    {
      throw CLIException.adaptInputException(e);
    }

    if (msgStyle == Style.TITLE
        || msgStyle == Style.SUBTITLE)
    {
      println();
    }

    if ("".equals(answer))
    {
      if (defaultValue == null)
      {
        println(INFO_ERROR_EMPTY_RESPONSE.get());
      }
      else
      {
        return defaultValue;
      }
    }
    return answer;
  }

  /**
   * Commodity method that interactively prompts (on error output) the user to
   * provide a string value. Any non-empty string will be allowed (the empty
   * string will indicate that the default should be used, if there is one).
   *
   * @param prompt
   *          The prompt to present to the user.
   * @param defaultValue
   *          The default value to assume if the user presses ENTER without
   *          typing anything, or <CODE>null</CODE> if there should not be a
   *          default and the user must explicitly provide a value.
   * @throws CLIException
   *           If the line of input could not be retrieved for some reason.
   * @return The string value read from the user.
   */
  public String readInput(Message prompt, String defaultValue)
      throws CLIException
  {
    while (true)
    {
      if (defaultValue != null)
      {
        prompt =
            INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(), defaultValue);
      }
      String response = readLineOfInput(prompt);

      if ("".equals(response))
      {
        if (defaultValue == null)
        {
          println(INFO_ERROR_EMPTY_RESPONSE.get());
        }
        else
        {
          return defaultValue;
        }
      }
      else
      {
        return response;
      }
    }
  }

  /**
   * Commodity method that interactively prompts (on error output) the user to
   * provide a string value. Any non-empty string will be allowed (the empty
   * string will indicate that the default should be used, if there is one). If
   * an error occurs a message will be logged to the provided logger.
   *
   * @param prompt
   *          The prompt to present to the user.
   * @param defaultValue
   *          The default value to assume if the user presses ENTER without
   *          typing anything, or <CODE>null</CODE> if there should not be a
   *          default and the user must explicitly provide a value.
   * @param logger
   *          the Logger to be used to log the error message.
   * @return The string value read from the user.
   */
  public String readInput(Message prompt, String defaultValue, Logger logger)
  {
    String s = defaultValue;
    try
    {
      s = readInput(prompt, defaultValue);
    }
    catch (CLIException ce)
    {
      logger.log(Level.WARNING, "Error reading input: " + ce, ce);
    }
    return s;
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
  public final String readPassword(Message prompt) throws CLIException
  {
    err.print(wrapText(prompt + " ", MAX_LINE_WIDTH));
    char[] pwChars;
    try
    {
      pwChars = PasswordReader.readPassword();
    }
    catch (Exception e)
    {
      throw CLIException.adaptInputException(e);
    }
    return new String(pwChars);
  }

  /**
   * Commodity method that interactively retrieves a password from the console.
   * If there is an error an error message is logged to the provided Logger and
   * <CODE>null</CODE> is returned.
   *
   * @param prompt
   *          The password prompt.
   * @param logger
   *          the Logger to be used to log the error message.
   * @return Returns the password.
   */
  protected final String readPassword(Message prompt, Logger logger)
  {
    String pwd = null;
    try
    {
      pwd = readPassword(prompt);
    }
    catch (CLIException ce)
    {
      logger.log(Level.WARNING, "Error reading input: " + ce, ce);
    }
    return pwd;
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
      @Override
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

    if (defaultValue != -1)
    {
      prompt =
          INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(), String
              .valueOf(defaultValue));
    }

    return readValidatedInput(prompt, callback, CONFIRMATION_MAX_TRIES);
  }

  /**
   * Returns a message object for the given NamingException.
   *
   * @param ne
   *          the NamingException.
   * @param hostPort
   *          the hostPort representation of the server we were contacting when
   *          the NamingException occurred.
   * @return a message object for the given NamingException.
   */
  protected Message getMessageForException(NamingException ne, String hostPort)
  {
    return Utils.getMessageForException(ne, hostPort);
  }

  /**
   * Commodity method used to repeatidly ask the user to provide a port value.
   *
   * @param prompt
   *          the prompt message.
   * @param defaultValue
   *          the default value of the port to be proposed to the user.
   * @param logger
   *          the logger where the errors will be written.
   * @return the port value provided by the user.
   */
  protected int askPort(Message prompt, int defaultValue, Logger logger)
  {
    int port = -1;
    while (port == -1)
    {
      try
      {
        port = readPort(prompt, defaultValue);
      }
      catch (CLIException ce)
      {
        port = -1;
        logger.log(Level.WARNING, "Error reading input: " + ce, ce);
      }
    }
    return port;
  }

  /**
   * Interactively prompts for user input and continues until valid input is
   * provided.
   *
   * @param <T>
   *          The type of decoded user input.
   * @param prompt
   *          The interactive prompt which should be displayed on each input
   *          attempt.
   * @param validator
   *          An input validator responsible for validating and decoding the
   *          user's response.
   * @return Returns the decoded user's response.
   * @throws CLIException
   *           If an unexpected error occurred which prevented validation.
   */
  public final <T> T readValidatedInput(Message prompt,
      ValidationCallback<T> validator) throws CLIException
  {
    while (true)
    {
      String response = readLineOfInput(prompt);
      T value = validator.validate(this, response);
      if (value != null)
      {
        return value;
      }
    }
  }

  /**
   * Interactively prompts for user input and continues until valid input is
   * provided.
   *
   * @param <T>
   *          The type of decoded user input.
   * @param prompt
   *          The interactive prompt which should be displayed on each input
   *          attempt.
   * @param validator
   *          An input validator responsible for validating and decoding the
   *          user's response.
   * @param maxTries
   *          The maximum number of tries that we can make.
   * @return Returns the decoded user's response.
   * @throws CLIException
   *           If an unexpected error occurred which prevented validation or if
   *           the maximum number of tries was reached.
   */
  public final <T> T readValidatedInput(Message prompt,
      ValidationCallback<T> validator, int maxTries) throws CLIException
  {
    int nTries = 0;
    while (nTries < maxTries)
    {
      String response = readLineOfInput(prompt);
      T value = validator.validate(this, response);
      if (value != null)
      {
        return value;
      }
      nTries++;
    }
    throw new CLIException(ERR_TRIES_LIMIT_REACHED.get(maxTries));
  }

  /**
   * Commodity method that interactively confirms whether a user wishes to
   * perform an action. If the application is non-interactive, then the provided
   * default is returned automatically. If there is an error an error message is
   * logged to the provided Logger and the defaul value is returned.
   *
   * @param prompt
   *          The prompt describing the action.
   * @param defaultValue
   *          The default value for the confirmation message. This will be
   *          returned if the application is non-interactive or if the user just
   *          presses return.
   * @param logger
   *          the Logger to be used to log the error message.
   * @return Returns <code>true</code> if the user wishes the action to be
   *         performed, or <code>false</code> if they refused.
   * @throws CLIException
   *           if the user did not provide valid answer after a certain number
   *           of tries (ConsoleApplication.CONFIRMATION_MAX_TRIES)
   */
  protected final boolean askConfirmation(Message prompt, boolean defaultValue,
      Logger logger) throws CLIException
  {
    boolean v = defaultValue;

    boolean done = false;
    int nTries = 0;

    while (!done && (nTries < CONFIRMATION_MAX_TRIES))
    {
      nTries++;
      try
      {
        v = confirmAction(prompt, defaultValue);
        done = true;
      }
      catch (CLIException ce)
      {
        if (ce.getMessageObject().getDescriptor().equals(
            ERR_CONFIRMATION_TRIES_LIMIT_REACHED)
            || ce.getMessageObject().getDescriptor().equals(
                ERR_TRIES_LIMIT_REACHED))
        {
          throw ce;
        }
        logger.log(Level.WARNING, "Error reading input: " + ce, ce);
        //      Try again...
        println();
      }
    }

    if (!done)
    {
      // This means we reached the maximum number of tries
      throw new CLIException(ERR_CONFIRMATION_TRIES_LIMIT_REACHED
          .get(CONFIRMATION_MAX_TRIES));
    }
    return v;
  }

  /**
   * Returns an InitialLdapContext using the provided parameters. We try to
   * guarantee that the connection is able to read the configuration.
   *
   * @param host
   *          the host name.
   * @param port
   *          the port to connect.
   * @param useSSL
   *          whether to use SSL or not.
   * @param useStartTLS
   *          whether to use StartTLS or not.
   * @param bindDn
   *          the bind dn to be used.
   * @param pwd
   *          the password.
   * @param connectTimeout
   *          the timeout in milliseconds to connect to the server.
   * @param trustManager
   *          the trust manager.
   * @return an InitialLdapContext connected.
   * @throws NamingException
   *           if there was an error establishing the connection.
   */
  protected InitialLdapContext createAdministrativeContext(String host,
      int port, boolean useSSL, boolean useStartTLS, String bindDn, String pwd,
      int connectTimeout, ApplicationTrustManager trustManager)
      throws NamingException
  {
    InitialLdapContext ctx;
    String ldapUrl = ConnectionUtils.getLDAPUrl(host, port, useSSL);
    if (useSSL)
    {
      ctx =
          Utils.createLdapsContext(ldapUrl, bindDn, pwd, connectTimeout, null,
              trustManager);
    }
    else if (useStartTLS)
    {
      ctx =
          Utils.createStartTLSContext(ldapUrl, bindDn, pwd, connectTimeout,
              null, trustManager, null);
    }
    else
    {
      ctx = Utils.createLdapContext(ldapUrl, bindDn, pwd, connectTimeout, null);
    }
    if (!ConnectionUtils.connectedAsAdministrativeUser(ctx))
    {
      throw new NoPermissionException(ERR_NOT_ADMINISTRATIVE_USER.get()
          .toString());
    }
    return ctx;
  }

  /**
   * Creates an Initial LDAP Context interacting with the user if the
   * application is interactive.
   *
   * @param ci
   *          the LDAPConnectionConsoleInteraction object that is assumed to
   *          have been already run.
   * @return the initial LDAP context or <CODE>null</CODE> if the user did not
   *         accept to trust the certificates.
   * @throws ClientException
   *           if there was an error establishing the connection.
   */
  protected InitialLdapContext createInitialLdapContextInteracting(
      LDAPConnectionConsoleInteraction ci) throws ClientException
  {
    return createInitialLdapContextInteracting(ci, isInteractive()
        && ci.isTrustStoreInMemory());
  }

  /**
   * Creates an Initial LDAP Context interacting with the user if the
   * application is interactive.
   *
   * @param ci
   *          the LDAPConnectionConsoleInteraction object that is assumed to
   *          have been already run.
   * @param promptForCertificate
   *          whether we should prompt for the certificate or not.
   * @return the initial LDAP context or <CODE>null</CODE> if the user did not
   *         accept to trust the certificates.
   * @throws ClientException
   *           if there was an error establishing the connection.
   */
  protected InitialLdapContext createInitialLdapContextInteracting(
      LDAPConnectionConsoleInteraction ci, boolean promptForCertificate)
      throws ClientException
  {
    // Interact with the user though the console to get
    // LDAP connection information
    String hostName = ConnectionUtils.getHostNameForLdapUrl(ci.getHostName());
    Integer portNumber = ci.getPortNumber();
    String bindDN = ci.getBindDN();
    String bindPassword = ci.getBindPassword();
    TrustManager trustManager = ci.getTrustManager();
    KeyManager keyManager = ci.getKeyManager();

    InitialLdapContext ctx;

    if (ci.useSSL())
    {
      String ldapsUrl = "ldaps://" + hostName + ":" + portNumber;
      while (true)
      {
        try
        {
          ctx =
              ConnectionUtils.createLdapsContext(ldapsUrl, bindDN,
                  bindPassword, ci.getConnectTimeout(), null, trustManager,
                  keyManager);
          ctx.reconnect(null);
          break;
        }
        catch (NamingException e)
        {
          if (promptForCertificate)
          {
            OpendsCertificateException oce = getCertificateRootException(e);
            if (oce != null)
            {
              String authType = null;
              if (trustManager instanceof ApplicationTrustManager)
              {
                ApplicationTrustManager appTrustManager =
                    (ApplicationTrustManager) trustManager;
                authType = appTrustManager.getLastRefusedAuthType();
              }
              if (ci.checkServerCertificate(oce.getChain(), authType, hostName))
              {
                // If the certificate is trusted, update the trust manager.
                trustManager = ci.getTrustManager();

                // Try to connect again.
                continue;
              }
              else
              {
                // Assume user canceled.
                return null;
              }
            }
          }
          if (e.getCause() != null)
          {
            if (!isInteractive() && !ci.isTrustAll())
            {
              if (getCertificateRootException(e) != null
                  || (e.getCause() instanceof SSLHandshakeException))
              {
                Message message =
                    ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT_NOT_TRUSTED.get(
                        hostName, String.valueOf(portNumber));
                throw new ClientException(
                    LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
              }
            }
            if (e.getCause() instanceof SSLException)
            {
              Message message =
                  ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT_WRONG_PORT.get(
                      hostName, String.valueOf(portNumber));
              throw new ClientException(
                  LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
          }
          String hostPort =
              ServerDescriptor.getServerRepresentation(hostName, portNumber);
          Message message = Utils.getMessageForException(e, hostPort);
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message);
        }
      }
    }
    else if (ci.useStartTLS())
    {
      String ldapUrl = "ldap://" + hostName + ":" + portNumber;
      while (true)
      {
        try
        {
          ctx =
              ConnectionUtils.createStartTLSContext(ldapUrl, bindDN,
                  bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
                  trustManager, keyManager, null);
          ctx.reconnect(null);
          break;
        }
        catch (NamingException e)
        {
          if (promptForCertificate)
          {
            OpendsCertificateException oce = getCertificateRootException(e);
            if (oce != null)
            {
              String authType = null;
              if (trustManager instanceof ApplicationTrustManager)
              {
                ApplicationTrustManager appTrustManager =
                    (ApplicationTrustManager) trustManager;
                authType = appTrustManager.getLastRefusedAuthType();
              }

              if (ci.checkServerCertificate(oce.getChain(), authType, hostName))
              {
                // If the certificate is trusted, update the trust manager.
                trustManager = ci.getTrustManager();

                // Try to connect again.
                continue;
              }
              else
              {
                // Assume user cancelled.
                return null;
              }
            }
            else
            {
              Message message =
                  ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, String
                      .valueOf(portNumber));
              throw new ClientException(
                  LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
          }
          Message message =
              ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, String
                  .valueOf(portNumber));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message);
        }
      }
    }
    else
    {
      String ldapUrl = "ldap://" + hostName + ":" + portNumber;
      while (true)
      {
        try
        {
          ctx =
              ConnectionUtils.createLdapContext(ldapUrl, bindDN, bindPassword,
                  ConnectionUtils.getDefaultLDAPTimeout(), null);
          ctx.reconnect(null);
          break;
        }
        catch (NamingException e)
        {
          Message message =
              ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, String
                  .valueOf(portNumber));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message);
        }
      }
    }
    return ctx;
  }

  /**
   * Returns the message to be displayed in the file with the equivalent
   * command-line with information about the current time.
   *
   * @return the message to be displayed in the file with the equivalent
   *         command-line with information about the current time.
   */
  protected String getCurrentOperationDateMessage()
  {
    String date = formatDateTimeStringForEquivalentCommand(new Date());
    return INFO_OPERATION_START_TIME_MESSAGE.get(date).toString();
  }

  /**
   * Formats a Date to String representation in "dd/MMM/yyyy:HH:mm:ss Z".
   *
   * @param date
   *          to format; null if <code>date</code> is null
   * @return string representation of the date
   */
  protected String formatDateTimeStringForEquivalentCommand(Date date)
  {
    String timeStr = null;
    if (date != null)
    {
      SimpleDateFormat dateFormat =
          new SimpleDateFormat(DATE_FORMAT_LOCAL_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      timeStr = dateFormat.format(date);
    }
    return timeStr;
  }

  /**
   * Prompts the user to give the Global Administrator UID.
   *
   * @param defaultValue
   *          the default value that will be proposed in the prompt message.
   * @param logger
   *          the Logger to be used to log the error message.
   * @return the Global Administrator UID as provided by the user.
   */
  protected String askForAdministratorUID(String defaultValue, Logger logger)
  {
    String s = defaultValue;
    try
    {
      s = readInput(INFO_ADMINISTRATOR_UID_PROMPT.get(), defaultValue);
    }
    catch (CLIException ce)
    {
      logger.log(Level.WARNING, "Error reading input: " + ce, ce);
    }
    return s;
  }

  /**
   * Prompts the user to give the Global Administrator password.
   *
   * @param logger
   *          the Logger to be used to log the error message.
   * @return the Global Administrator password as provided by the user.
   */
  protected String askForAdministratorPwd(Logger logger)
  {
    String pwd = readPassword(INFO_ADMINISTRATOR_PWD_PROMPT.get(), logger);
    return pwd;
  }

  private OpendsCertificateException getCertificateRootException(Throwable t)
  {
    OpendsCertificateException oce = null;
    while (t != null && oce == null)
    {
      t = t.getCause();
      if (t instanceof OpendsCertificateException)
      {
        oce = (OpendsCertificateException) t;
      }
    }
    return oce;
  }

  /**
   * Commodity method used to repeatidly ask the user to provide an integer
   * value.
   *
   * @param prompt
   *          the prompt message.
   * @param defaultValue
   *          the default value to be proposed to the user.
   * @param logger
   *          the logger where the errors will be written.
   * @return the value provided by the user.
   */
  protected int askInteger(Message prompt, int defaultValue, Logger logger)
  {
    int newInt = -1;
    while (newInt == -1)
    {
      try
      {
        newInt = readInteger(prompt, defaultValue);
      }
      catch (CLIException ce)
      {
        newInt = -1;
        logger.log(Level.WARNING, "Error reading input: " + ce, ce);
      }
    }
    return newInt;
  }

  /**
   * Interactively retrieves an integer value from the console.
   *
   * @param prompt
   *          The message prompt.
   * @param defaultValue
   *          The default value.
   * @return Returns the value.
   * @throws CLIException
   *           If the value could not be retrieved for some reason.
   */
  public final int readInteger(Message prompt, final int defaultValue)
      throws CLIException
  {
    ValidationCallback<Integer> callback = new ValidationCallback<Integer>()
    {
      @Override
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
            if (i < 1)
            {
              throw new NumberFormatException();
            }
            return i;
          }
          catch (NumberFormatException e)
          {
            // Try again...
            app.println();
            app.println(ERR_LDAP_CONN_BAD_INTEGER.get(ninput));
            app.println();
            return null;
          }
        }
      }

    };

    if (defaultValue != -1)
    {
      prompt =
          INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(), String
              .valueOf(defaultValue));
    }

    return readValidatedInput(prompt, callback, CONFIRMATION_MAX_TRIES);
  }
}
