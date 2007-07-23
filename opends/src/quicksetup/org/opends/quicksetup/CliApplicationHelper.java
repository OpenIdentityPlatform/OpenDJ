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

package org.opends.quicksetup;

import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.StaticUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Set;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Helper class containing useful methods for processing input and output
 * for a CliApplication.
 */
public class CliApplicationHelper {

  static private final Logger LOG =
          Logger.getLogger(CliApplication.class.getName());

  /** Short form of the option for specifying a noninteractive session. */
  static public final Character SILENT_OPTION_SHORT = 's';

  /** Long form of the option for specifying a noninteractive session. */
  static public final String SILENT_OPTION_LONG = "silent";

  /** Short form of the option for specifying a noninteractive session. */
  static public final Character INTERACTIVE_OPTION_SHORT = 'i';

  /** Long form of the option for specifying a noninteractive session. */
  static public final String INTERACTIVE_OPTION_LONG = "interactive";

  private BooleanArgument interactiveArg = null;

  private BooleanArgument silentArg = null;

  /**
   * Interactively prompts (on standard output) the user to provide a string
   * value.  Any non-empty string will be allowed (the empty string will
   * indicate that the default should be used).  The method will display the
   * message until the user provides one of the values in the validValues
   * parameter.
   *
   * @param  formatKey     Key for access the prompts format string from the
   *                       bundle
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value returned if the user clicks enter.
   * @param  validValues   The valid values that can be accepted as user input.
   *
   * @return The string value read from the user.
   */
  protected String promptConfirm(String formatKey, String prompt,
                                 String defaultValue,
                                 String[] validValues) {

    System.out.println();

    boolean isValid = false;
    String response = null;
    while (!isValid)
    {
      String msg = getMsg(formatKey,
          new String[] {prompt, defaultValue});

      System.out.print(msg);
      System.out.flush();

      response = readLine();
      if (response.equals(""))
      {
        response = defaultValue;
      }
      for (int i=0; i<validValues.length && !isValid; i++)
      {
        isValid = validValues[i].equalsIgnoreCase(response);
      }
    }
    return response;
  }

  /**
   * Interactively prompts (on standard output) the user to provide a string
   * value.  Any non-empty string will be allowed (the empty string will
   * indicate that the default should be used, if there is one).
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @return  The string value read from the user.
   */
  protected String promptForString(String prompt, String defaultValue) {
    System.out.println();
    String wrappedPrompt = StaticUtils.wrapText(prompt,
            Utils.getCommandLineMaxLineWidth());

    while (true) {
      System.out.println(wrappedPrompt);

      if (defaultValue == null) {
        System.out.print(": ");
      } else {
        System.out.print("[");
        System.out.print(defaultValue);
        System.out.print("]: ");
      }

      System.out.flush();

      String response = readLine();
      if (response.equals("")) {
        if (defaultValue == null) {
          String message = getMsg("error-empty-response");
          System.err.println(StaticUtils.wrapText(message,
                  Utils.getCommandLineMaxLineWidth()));
        } else {
          return defaultValue;
        }
      } else {
        return response;
      }
    }
  }


  /**
   * Reads a line of text from standard input.
   * @return  The line of text read from standard input, or <CODE>null</CODE>
   *          if the end of the stream is reached or an error occurs while
   *          attempting to read the response.
   */
  public String readLine() {
    return readLine(System.in, System.err);
  }

  /**
   * Reads a line of text from standard input.
   * @param   in InputSteam from which line will be read
   * @param   err PrintSteam where any errors will be printed
   * @return  The line of text read from standard input, or <CODE>null</CODE>
   *          if the end of the stream is reached or an error occurs while
   *          attempting to read the response.
   */
  public String readLine(InputStream in, PrintStream err)
  {
    try
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      while (true)
      {
        int b = in.read();
        if ((b < 0) || (b == '\n'))
        {
          break;
        }
        else if (b == '\r')
        {
          int b2 = in.read();
          if (b2 == '\n')
          {
            break;
          }
          else
          {
            baos.write(b);
            baos.write(b2);
          }
        }
        else
        {
          baos.write(b);
        }
      }

      return new String(baos.toByteArray(), "UTF-8");
    }
    catch (Exception e)
    {
      err.println(getMsg("cli-uninstall-error-reading-stdin"));

      return null;
    }
  }

  /**
   * Returns <CODE>true</CODE> if this is a silent uninstall and
   * <CODE>false</CODE> otherwise.
   * @param args the arguments passed in the command line.
   * @return <CODE>true</CODE> if this is a silent uninstall and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean isSilent(String[] args)
  {
    boolean isSilent = false;
    for (int i=0; i<args.length && !isSilent; i++)
    {
      if (args[i].equalsIgnoreCase("--silentUninstall") ||
          args[i].equalsIgnoreCase("-s"))
      {
        isSilent = true;
      }
    }
    return isSilent;
  }

  /**
   * Commodity method used to validate the arguments provided by the user in
   * the command line and updating the UserData object accordingly.
   * @param userData the UserData object to be updated.
   * @param args the arguments passed in the command line.
   * @param validArgs arguments that are acceptable by this application.
   * @throws org.opends.quicksetup.UserDataException if there is an error with
   * the data provided by the user.
   */
  protected void validateArguments(UserData userData,
                                 String[] args,
                                 Set<String> validArgs) throws UserDataException
  {
    ArrayList<String> errors = new ArrayList<String>();

    for (String arg1 : args) {
      if (validArgs.contains(arg1)) {
        // Ignore
      } else {
        String[] arg = {arg1};
        errors.add(getMsg("cli-uninstall-unknown-argument", arg));
      }
    }

    if (errors.size() > 0)
    {
      String msg = Utils.getStringFromCollection(errors,
          Constants.LINE_SEPARATOR+Constants.LINE_SEPARATOR);
      throw new UserDataException(null, msg);
    }
  }

  /**
   * Returns <CODE>true</CODE> if this is a silent session and
   * <CODE>false</CODE> otherwise.  This method relies on the a previous
   * call to createArgumentParser having been made and the parser
   * having been used to parse the arguments.
   * @return <CODE>true</CODE> if this is a silent uninstall and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean isSilent() {
    return silentArg != null && silentArg.isPresent();
  }

  /**
   * Returns <CODE>true</CODE> if this is a noninteractive sessions and
   * <CODE>false</CODE> otherwise.  This method relies on the a previous
   * call to createArgumentParser having been made and the parser
   * having been used to parse the arguments.
   * @return <CODE>true</CODE> if this is a noninteractive session and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean isInteractive() {
    return interactiveArg != null && interactiveArg.isPresent();
  }

  /**
   * Creates an argument parser having common arguments.
   * @param mainClass class of the tool
   * @param description localized description of the tool
   * @param caseSensitive whether long args are case sensitive
   * @return ArgumentParser ready for app specific customization
   * @see org.opends.server.util.args.ArgumentParser#ArgumentParser(
          String, String, boolean)
   */
  protected ArgumentParser createArgumentParser(String mainClass,
                                                String description,
                                                boolean caseSensitive) {

    // TODO: get rid of this method and user launcher.getArgumentParser

    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser =
         new ArgumentParser(mainClass, description, caseSensitive);

    // Initialize all the common command-line argument types and register
    // them with the parser.
    try {
      interactiveArg =
           new BooleanArgument("noninteractive session",
                   INTERACTIVE_OPTION_SHORT,
                   INTERACTIVE_OPTION_LONG,
                   0);
      argParser.addArgument(interactiveArg);

      silentArg =
           new BooleanArgument("silent session",
                   SILENT_OPTION_SHORT,
                   SILENT_OPTION_LONG,
                   0);
      argParser.addArgument(silentArg);

    } catch (ArgumentException e) {
      LOG.log(Level.INFO, "error", e);
    }

    return argParser;
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   * @param key String key
   * @return String message
   */
  protected static String getMsg(String key)
  {
    return org.opends.server.util.StaticUtils.wrapText(getI18n().getMsg(key),
        Utils.getCommandLineMaxLineWidth());
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   * @param key String key
   * @param args String[] args
   * @return String message
   */
  protected static String getMsg(String key, String... args)
  {
    return org.opends.server.util.StaticUtils.wrapText(
        getI18n().getMsg(key, args), Utils.getCommandLineMaxLineWidth());
  }

  /**
   * Gets the resource provider instance.
   * @return ResourceProvider instance
   */
  protected static ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
