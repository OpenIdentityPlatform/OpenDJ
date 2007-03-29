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

import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Helper class containing useful methods for processing input and output
 * for a CliApplication.
 */
public class CliApplicationHelper {

  static private final Logger LOG =
          Logger.getLogger(CliApplication.class.getName());

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
   * Reads a line of text from standard input.
   *
   * @return  The line of text read from standard input, or <CODE>null</CODE>
   *          if the end of the stream is reached or an error occurs while
   *          attempting to read the response.
   */
  private String readLine()
  {
    try
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      while (true)
      {
        int b = System.in.read();
        if ((b < 0) || (b == '\n'))
        {
          break;
        }
        else if (b == '\r')
        {
          int b2 = System.in.read();
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
      System.err.println(getMsg("cli-uninstall-error-reading-stdin"));

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
          QuickSetupCli.LINE_SEPARATOR+QuickSetupCli.LINE_SEPARATOR);
      throw new UserDataException(null, msg);
    }
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
  protected static String getMsg(String key, String[] args)
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
