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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.sdk.tools;



import static com.sun.opends.sdk.messages.Messages.*;
import static org.opends.sdk.tools.Utils.*;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.opends.sdk.util.Message;



/**
 * This class provides an abstract base class which can be used as the
 * basis of a console-based application.
 */
abstract class ConsoleApplication
{
  private static final class NullOutputStream extends OutputStream
  {
    /**
     * The singleton instance for this class.
     */
    private static final NullOutputStream instance = new NullOutputStream();

    /**
     * The singleton print stream tied to the null output stream.
     */
    private static final PrintStream printStream = new PrintStream(
        instance);



    /**
     * Retrieves a print stream using this null output stream.
     *
     * @return A print stream using this null output stream.
     */
    static PrintStream printStream()
    {
      return printStream;
    }



    /**
     * Creates a new instance of this null output stream.
     */
    private NullOutputStream()
    {
      // No implementation is required.
    }



    /**
     * Closes the output stream. This has no effect.
     */
    public void close()
    {
      // No implementation is required.
    }



    /**
     * Flushes the output stream. This has no effect.
     */
    public void flush()
    {
      // No implementation is required.
    }



    /**
     * Writes the provided data to this output stream. This has no
     * effect.
     *
     * @param b
     *          The byte array containing the data to be written.
     */
    public void write(byte[] b)
    {
      // No implementation is required.
    }



    /**
     * Writes the provided data to this output stream. This has no
     * effect.
     *
     * @param b
     *          The byte array containing the data to be written.
     * @param off
     *          The offset at which the real data begins.
     * @param len
     *          The number of bytes to be written.
     */
    public void write(byte[] b, int off, int len)
    {
      // No implementation is required.
    }



    /**
     * Writes the provided byte to this output stream. This has no
     * effect.
     *
     * @param b
     *          The byte to be written.
     */
    public void write(int b)
    {
      // No implementation is required.
    }
  }



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



  // The error stream which this application should use.
  private final PrintStream err;

  // The input stream reader which this application should use.
  private final BufferedReader reader;

  private final InputStream in;

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
  ConsoleApplication(InputStream in, OutputStream out, OutputStream err)
  {
    this.in = in;
    if (in != null)
    {
      this.reader = new BufferedReader(new InputStreamReader(in));
    }
    else
    {
      this.reader = new BufferedReader(new NullReader());
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
   * Gets the application error stream.
   *
   * @return Returns the application error stream.
   */
  final PrintStream getErrorStream()
  {
    return err;
  }



  /**
   * Gets the application input stream reader.
   *
   * @return Returns the application input stream.
   */
  final BufferedReader getInputReader()
  {
    return reader;
  }



  /**
   * Gets the application input stream.
   *
   * @return Returns the application input stream.
   */
  final InputStream getInputStream()
  {
    return in;
  }



  /**
   * Gets the application output stream.
   *
   * @return Returns the application output stream.
   */
  final PrintStream getOutputStream()
  {
    return out;
  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested
   *         advanced mode.
   */
  abstract boolean isAdvancedMode();



  /**
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  abstract boolean isInteractive();



  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should
   * go to the error stream or not. In addition, it may also dictate
   * whether or not sub-menus should display a cancel option as well as
   * a quit option.
   *
   * @return Returns <code>true</code> if this console application is
   *         running in its menu-driven mode.
   */
  abstract boolean isMenuDrivenMode();



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet
   *         output.
   */
  abstract boolean isQuiet();



  /**
   * Indicates whether or not the user has requested script-friendly
   * output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         script-friendly output.
   */
  abstract boolean isScriptFriendly();



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose
   *         output.
   */
  abstract boolean isVerbose();



  /**
   * Interactively prompts the user to press return to continue. This
   * method should be called in situations where a user needs to be
   * given a chance to read some documentation before continuing
   * (continuing may cause the documentation to be scrolled out of
   * view).
   */
  final void pressReturnToContinue()
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
  final void println()
  {
    err.println();
  }



  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  final void println(Message msg)
  {
    err.println(wrapText(msg, MAX_LINE_WIDTH));
  }



  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  final void print(Message msg)
  {
    err.print(wrapText(msg, MAX_LINE_WIDTH));
  }



  /**
   * Displays a blank line to the output stream if we are not in quiet
   * mode.
   */
  final void printlnProgress()
  {
    if (!isQuiet())
    {
      out.println();
    }
  }



  /**
   * Displays a message to the output stream if we are not in quiet
   * mode.
   *
   * @param msg
   *          The message.
   */
  final void printProgress(Message msg)
  {
    if (!isQuiet())
    {
      out.print(msg);
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
  final void println(Message msg, int indent)
  {
    err.println(wrapText(msg, MAX_LINE_WIDTH, indent));
  }



  /**
   * Displays a message to the error stream if verbose mode is enabled.
   *
   * @param msg
   *          The verbose message.
   */
  final void printVerboseMessage(Message msg)
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
   * @return Returns the line of input, or <code>null</code> if the end
   *         of input has been reached.
   * @throws CLIException
   *           If the line of input could not be retrieved for some
   *           reason.
   */
  final String readLineOfInput(Message prompt) throws CLIException
  {
    if (prompt != null)
    {
      err.print(wrapText(prompt, MAX_LINE_WIDTH));
      err.print(" ");
    }
    try
    {
      String s = reader.readLine();
      if (s == null)
      {
        throw CLIException.adaptInputException(new EOFException(
            "End of input"));
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
   * Commodity method that interactively prompts (on error output) the
   * user to provide a string value. Any non-empty string will be
   * allowed (the empty string will indicate that the default should be
   * used, if there is one).
   *
   * @param prompt
   *          The prompt to present to the user.
   * @param defaultValue
   *          The default value to assume if the user presses ENTER
   *          without typing anything, or <CODE>null</CODE> if there
   *          should not be a default and the user must explicitly
   *          provide a value.
   * @throws CLIException
   *           If the line of input could not be retrieved for some
   *           reason.
   * @return The string value read from the user.
   */
  final String readInput(Message prompt, String defaultValue)
      throws CLIException
  {
    while (true)
    {
      if (defaultValue != null)
      {
        prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(),
            defaultValue);
      }
      String response = readLineOfInput(prompt);

      if ("".equals(response))
      {
        if (defaultValue == null)
        {
          print(INFO_ERROR_EMPTY_RESPONSE.get());
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
   * Commodity method that interactively prompts (on error output) the
   * user to provide a string value. Any non-empty string will be
   * allowed (the empty string will indicate that the default should be
   * used, if there is one). If an error occurs a message will be logged
   * to the provided logger.
   *
   * @param prompt
   *          The prompt to present to the user.
   * @param defaultValue
   *          The default value to assume if the user presses ENTER
   *          without typing anything, or <CODE>null</CODE> if there
   *          should not be a default and the user must explicitly
   *          provide a value.
   * @param logger
   *          the Logger to be used to log the error message.
   * @return The string value read from the user.
   */
  final String readInput(Message prompt, String defaultValue,
      Logger logger)
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

}
