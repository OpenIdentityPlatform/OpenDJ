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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.uninstaller;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Set;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.event.UninstallProgressUpdateEvent;
import org.opends.quicksetup.event.UninstallProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

/**
 * The class used to provide some CLI interface in the uninstall.
 *
 * This class basically is in charge of parsing the data provided by the user
 * in the command line and displaying messages asking the user for information.
 *
 * Once the user has provided all the required information it calls Uninstaller
 * and launches it.
 *
 */
class UninstallCli
{
  /**
   * Return code: Uninstall successful.
   */
  static int SUCCESSFUL = 0;
  /**
   * Return code: User Cancelled uninstall.
   */
  static int CANCELLED = 1;
  /**
   * Return code: User provided invalid data.
   */
  static int USER_DATA_ERROR = 2;
  /**
   * Return code: Error accessing file system (reading/writing).
   */
  static int ERROR_ACCESSING_FILE_SYSTEM = 3;
  /**
   * Return code: Error stopping server.
   */
  static int ERROR_STOPPING_SERVER = 4;
  /**
   * Return code: Bug.
   */
  static int BUG = 5;

  private static String LINE_SEPARATOR = System.getProperty("line.separator");

  private String[] args;

  /**
   * The constructor for this object.
   * @param args the arguments of the uninstall command line.
   */
  UninstallCli(String[] args)
  {
    this.args = args;
  }

  /**
   * Parses the user data and prompts the user for data if required.  If the
   * user provides all the required data it launches the Uninstaller.
   *
   * @return the return code (SUCCESSFUL, CANCELLED, USER_DATA_ERROR,
   * ERROR_ACCESSING_FILE_SYSTEM, ERROR_STOPPING_SERVER or BUG.
   */
  int run()
  {
    int returnValue;

    System.out.println(getMsg("uninstall-launcher-launching-cli"));
    // Parse the arguments
    try
    {
      CurrentInstallStatus installStatus = new CurrentInstallStatus();
      UserUninstallData userData = getUserUninstallData(args, installStatus);
      if (userData != null)
      {
        Uninstaller uninstaller = new Uninstaller(userData,
            new PlainTextProgressMessageFormatter());
        uninstaller.addProgressUpdateListener(
            new UninstallProgressUpdateListener()
            {
              /**
               * UninstallProgressUpdateListener implementation.
               * @param ev the UninstallProgressUpdateEvent we receive.
               *
               */
              public void progressUpdate(UninstallProgressUpdateEvent ev)
              {
                System.out.print(
                    org.opends.server.util.StaticUtils.wrapText(ev.getNewLogs(),
                        Utils.getCommandLineMaxLineWidth()));
              }
            });
        uninstaller.start();
        while (!uninstaller.isFinished())
        {
          try
          {
            Thread.sleep(100);
          }
          catch (Exception ex)
          {
          }
        }

        UninstallException ue = uninstaller.getException();
        if (ue != null)
        {
          switch (ue.getType())
          {
          case FILE_SYSTEM_ERROR:
            returnValue = ERROR_ACCESSING_FILE_SYSTEM;
            break;

          case STOP_ERROR:
            returnValue = ERROR_STOPPING_SERVER;
            break;

          case BUG:
            returnValue = BUG;
            break;

            default:
              throw new IllegalStateException(
                  "Unknown UninstallException type: "+ue.getType());
          }
        }
        else
        {
          returnValue = SUCCESSFUL;
        }
      }
      else
      {
        // User cancelled installation.
        returnValue = CANCELLED;
      }
    }
    catch (UserUninstallDataException uude)
    {
      System.err.println(LINE_SEPARATOR+uude.getLocalizedMessage()+
          LINE_SEPARATOR);
      returnValue = USER_DATA_ERROR;
    }
    return returnValue;
  }

  /**
   * Creates a UserUninstallData based in the arguments provided.  It asks
   * user for additional information if what is provided in the arguments is not
   * enough.
   * @param args the arguments provided in the command line.
   * @param installStatus the current install status.
   * @return the UserUninstallData object with what the user wants to uninstall
   * and null if the user cancels the uninstallation.
   * @throws UserUninstallDataException if there is an error parsing the data
   * in the arguments.
   */
  private UserUninstallData getUserUninstallData(String[] args,
      CurrentInstallStatus installStatus) throws UserUninstallDataException
  {
    UserUninstallData userData = new UserUninstallData();

    boolean silentUninstall = false;
    boolean isCancelled = false;

    /* Step 1: validate the arguments
     */
    validateArguments(userData, args);

    silentUninstall = isSilent(args);


    /* Step 2: If this is not a silent install ask for confirmation to delete
     * the different parts of the installation
     */
    Set<String> outsideDbs = getOutsideDbs(installStatus);
    Set<String> outsideLogs = getOutsideLogs(installStatus);

    if (silentUninstall)
    {
      userData.setRemoveBackups(true);
      userData.setRemoveConfigurationAndSchema(true);
      userData.setRemoveDatabases(true);
      userData.setRemoveLDIFs(true);
      userData.setRemoveLibrariesAndTools(true);
      userData.setRemoveLogs(true);

      userData.setExternalDbsToRemove(outsideDbs);
      userData.setExternalLogsToRemove(outsideLogs);
    }
    else
    {
      isCancelled = askWhatToDelete(userData, outsideDbs, outsideLogs);
    }

    /*
     * Step 3: check if server is running.  Depending if it is running and the
     * OS we are running, ask for authentication information.
     */
    if (!isCancelled)
    {
      isCancelled = askConfirmationToStop(userData, installStatus,
          silentUninstall);
    }

    if (isCancelled)
    {
      userData = null;
    }

    return userData;
  }

  /**
   * Interactively prompts (on standard output) the user to provide a string
   * value.  Any non-empty string will be allowed (the empty string will
   * indicate that the default should be used).  The method will display the
   * message until the user provides one of the values in the validValues
   * parameter.
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value returned if the user clicks enter.
   * @param  validValues   The valid values that can be accepted as user input.
   *
   * @return  The string value read from the user.
   */
  private String promptConfirm(String prompt, String defaultValue,
      String[] validValues)
  {
    System.out.println();

    boolean isValid = false;
    String response = null;
    while (!isValid)
    {
      String msg = getMsg("cli-uninstall-confirm-prompt",
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
   * indicate that the default should be used).
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value returned if the user clicks enter.
   *
   * @return  The string value read from the user.
   */
  private String promptForString(String prompt, String defaultValue)
  {
    System.out.println();

    String response = null;
    String msg = getMsg("cli-uninstall-string-prompt",
        new String[] {prompt, defaultValue});

    System.out.print(msg);
    System.out.flush();

    response = readLine();
    if (response.equals(""))
    {
      response = defaultValue;
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
   * Returns a Set of relative paths containing the db paths outside the
   * installation.
   * @param installStatus the Current Install Status object.
   * @return a Set of relative paths containing the db paths outside the
   * installation.
   */
  private Set<String> getOutsideDbs(CurrentInstallStatus installStatus)
  {
    return Utils.getOutsideDbs(installStatus);
  }

  /**
   * Returns a Set of relative paths containing the log paths outside the
   * installation.
   * @param installStatus the Current Install Status object.
   * @return a Set of relative paths containing the log paths outside the
   * installation.
   */
  private Set<String> getOutsideLogs(CurrentInstallStatus installStatus)
  {
    return Utils.getOutsideLogs(installStatus);
  }

  /**
   * Commodity method used to ask the user to confirm the deletion of certain
   * parts of the server.  It updates the provided UserUninstallData object
   * accordingly.  Returns <CODE>true</CODE> if the user cancels and <CODE>
   * false</CODE> otherwise.
   * @param userData the UserUninstallData object to be updated.
   * @param outsideDbs the set of relative paths of databases located outside
   * the installation path of the server.
   * @param outsideLogs the set of relative paths of log files located outside
   * the installation path of the server.
   * @returns <CODE>true</CODE> if the user cancels and <CODE>false</CODE>
   * otherwise.
   */
  private boolean askWhatToDelete(UserUninstallData userData,
      Set<String> outsideDbs, Set<String> outsideLogs)
  {
    boolean cancelled = false;

    String answer = promptConfirm(getMsg("cli-uninstall-what-to-delete"),
        "1", new String[] {"1", "2", "3"});
    if ("3".equals(answer))
    {
      cancelled = true;
    }
    else if ("1".equals(answer))
    {
      userData.setRemoveBackups(true);
      userData.setRemoveConfigurationAndSchema(true);
      userData.setRemoveDatabases(true);
      userData.setRemoveLDIFs(true);
      userData.setRemoveLibrariesAndTools(true);
      userData.setRemoveLogs(true);

      userData.setExternalDbsToRemove(outsideDbs);
      userData.setExternalLogsToRemove(outsideLogs);
    }
    else
    {
      boolean somethingSelected = false;
      while (!somethingSelected)
      {
//      Ask for confirmation for the different items
        String[] keys = {
            "cli-uninstall-confirm-libraries-binaries",
            "cli-uninstall-confirm-databases",
            "cli-uninstall-confirm-logs",
            "cli-uninstall-confirm-configuration-schema",
            "cli-uninstall-confirm-backups",
            "cli-uninstall-confirm-ldifs",
            "cli-uninstall-confirm-outsidedbs",
            "cli-uninstall-confirm-outsidelogs"
        };

        String[] validValues = {
            getMsg("cli-uninstall-yes-long"), getMsg("cli-uninstall-no-long"),
            getMsg("cli-uninstall-yes-short"), getMsg("cli-uninstall-no-short")
        };
        boolean[] answers = new boolean[keys.length];
        for (int i=0; i<keys.length; i++)
        {
          boolean ignore = ((i == 6) && (outsideDbs.size() == 0)) ||
          ((i == 7) && (outsideLogs.size() == 0));
          if (!ignore)
          {
            String msg;
            if (i == 6)
            {
              String[] arg = {Utils.getStringFromCollection(outsideDbs,
                  LINE_SEPARATOR)};
              msg = getMsg(keys[i], arg);
            }
            else if (i == 7)
            {
              String[] arg = {Utils.getStringFromCollection(outsideLogs,
                  LINE_SEPARATOR)};
              msg = getMsg(keys[i], arg);
            }
            else
            {
              msg = getMsg(keys[i]);
            }
            answer = promptConfirm(msg, getMsg("cli-uninstall-yes-long"),
                validValues);

            if (getMsg("cli-uninstall-yes-long").equalsIgnoreCase(answer) ||
                getMsg("cli-uninstall-yes-short").equalsIgnoreCase(answer))
            {
              answers[i] = true;
            }
            else
            {
              answers[i] = false;
            }
          }
          else
          {
            answers[i] = false;
          }
        }

        for (int i=0; i<answers.length; i++)
        {
          switch (i)
          {
          case 0:
            userData.setRemoveLibrariesAndTools(answers[i]);
            break;

          case 1:
            userData.setRemoveDatabases(answers[i]);
            break;

          case 2:
            userData.setRemoveLogs(answers[i]);
            break;

          case 3:
            userData.setRemoveConfigurationAndSchema(answers[i]);
            break;

          case 4:
            userData.setRemoveBackups(answers[i]);
            break;

          case 5:
            userData.setRemoveLDIFs(answers[i]);
            break;

          case 6:
            if (answers[i])
            {
              userData.setExternalDbsToRemove(outsideDbs);
            }
            break;

          case 7:
            if (answers[i])
            {
              userData.setExternalLogsToRemove(outsideLogs);
            }
            break;
          }
        }
        if ((userData.getExternalDbsToRemove().size() == 0) &&
            (userData.getExternalLogsToRemove().size() == 0) &&
            !userData.getRemoveLibrariesAndTools() &&
            !userData.getRemoveDatabases() &&
            !userData.getRemoveConfigurationAndSchema() &&
            !userData.getRemoveBackups() &&
            !userData.getRemoveLDIFs() &&
            !userData.getRemoveLogs())
        {
          somethingSelected = false;
          System.out.println(LINE_SEPARATOR+
              getMsg("cli-uninstall-nothing-to-be-uninstalled"));
        }
        else
        {
          somethingSelected = true;
        }
      }
    }

    return cancelled;
  }

  /**
   * Commodity method used to ask the user (when necessary) if the server must
   * be stopped or not.  If required it also asks the user authentication to
   * be able to shut down the server in Windows.
   * @param userData the UserUninstallData object to be updated with the
   * authentication of the user.
   * @param installStatus the CurrentInstallStatus object.
   * @param silentUninstall boolean telling whether this is a silent uninstall
   * or not.
   * @return <CODE>true</CODE> if the user wants to continue with uninstall and
   * <CODE>false</CODE> otherwise.
   * @throws UserUninstallDataException if there is a problem with the data
   * provided by the user (in the particular case where we are on silent
   * uninstall and some data is missing or not valid).
   */
  private boolean askConfirmationToStop(UserUninstallData userData,
      CurrentInstallStatus installStatus, boolean silentUninstall)
  throws UserUninstallDataException
  {
    boolean cancelled = false;

    if (installStatus.isServerRunning())
    {
        if (!silentUninstall)
        {
            /* Ask for confirmation to stop server */
            cancelled = !confirmToStopServer();
        }

        if (!cancelled)
        {
            /* During all the confirmations, the server might be stopped. */
            userData.setStopServer(installStatus.isServerRunning());
        }
    }
    else
    {
      userData.setStopServer(false);
      if (!silentUninstall)
      {
        /* Ask for confirmation to delete files */
        cancelled = !confirmDeleteFiles();
      }
    }
    return cancelled;
  }


  /**
   * Returns <CODE>true</CODE> if this is a silent uninstall and
   * <CODE>false</CODE> otherwise.
   * @param args the arguments passed in the command line.
   * @return <CODE>true</CODE> if this is a silent uninstall and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isSilent(String[] args)
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
   * the command line and updating the UserUninstallData object accordingly.
   * @param userData the UserUninstallData object to be updated.
   * @param args the arguments passed in the command line.
   * @throws UserUninstallDataException if there is an error with the data
   * provided by the user.
   */
  private void validateArguments(UserUninstallData userData,
      String[] args) throws UserUninstallDataException
  {
    String directoryManagerPwd = null;
    String directoryManagerPwdFile = null;

    ArrayList<String> errors = new ArrayList<String>();

    for (int i=0; i<args.length; i++)
    {
      if (args[i].equalsIgnoreCase("--cli") ||
          args[i].equalsIgnoreCase("-H") ||
          args[i].equalsIgnoreCase("--help") ||
          args[i].equalsIgnoreCase("--silentUninstall") ||
          args[i].equalsIgnoreCase("-s"))
      {
        // Ignore
      }
      else
      {
        String[] arg = {args[i]};
        errors.add(getMsg("cli-uninstall-unknown-argument", arg));
      }
    }

    if (errors.size() > 0)
    {
      String msg = Utils.getStringFromCollection(errors,
          LINE_SEPARATOR+LINE_SEPARATOR);
      throw new UserUninstallDataException(null, msg);
    }
  }

  /**
   * Interactively prompts (on standard output) the user to provide a string
   * value.
   *
   * @param  prompt  The prompt to present to the user.
   *
   * @return  The string value read from the user.
   */
  private String promptForPassword(String prompt)
  {
    char[] password = null;
    while ((password == null) || (password.length == 0))
    {
      System.out.println();
      System.out.print(prompt);
      System.out.flush();

      password = org.opends.server.util.PasswordReader.readPassword();
    }

    return new String(password);
  }

  /**
   * Returns the password stored in a file.  Returns <CODE>null</CODE> if no
   * password is found.
   * @param path the path of the file containing the password.
   * @return the password stored in a file.  Returns <CODE>null</CODE> if no
   * password is found.
   */
  private String readPwdFromFile(String path)
  {
    String pwd = null;
    BufferedReader reader = null;
    try
    {
      reader = new BufferedReader(new FileReader(path));
      pwd = reader.readLine();
    }
    catch (Exception e)
    {
    }
    finally
    {
      try
      {
        if (reader != null)
        {
          reader.close();
        }
      } catch (Exception e) {}
    }
    return pwd;
  }

  /**
   * Method used to know if we can connect as administrator in a server with a
   * given password and dn.
   * @param ldapUrl the ldap URL of the server.
   * @param dn the dn to be used.
   * @param pwd the password to be used.
   * @return <CODE>true</CODE> if we can connect and read the configuration and
   * <CODE>false</CODE> otherwise.
   */
  private boolean canConnectAsAdministrativeUser(String ldapUrl, String dn,
      String pwd)
  {
    return Utils.canConnectAsAdministrativeUser(ldapUrl, dn, pwd);
  }


  /**
   *  Ask for confirmation to stop server.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmToStopServer()
  {
    boolean confirm = true;
    String[] validValues = {
        getMsg("cli-uninstall-yes-short"),
        getMsg("cli-uninstall-no-short"),
        getMsg("cli-uninstall-yes-long"),
        getMsg("cli-uninstall-no-long")
    };
    String answer = promptConfirm(
        getMsg("cli-uninstall-confirm-stop"),
        getMsg("cli-uninstall-yes-long"), validValues);

    if (getMsg("cli-uninstall-no-short").equalsIgnoreCase(answer) ||
        getMsg("cli-uninstall-no-long").equalsIgnoreCase(answer))
    {
      confirm = false;
    }
    return confirm;
  }

  /**
   *  Ask for confirmation to delete files.
   *  @return <CODE>true</CODE> if the user wants to continue and delete the
   *  files.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmDeleteFiles()
  {
    boolean confirm = true;
    String[] validValues = {
        getMsg("cli-uninstall-yes-short"),
        getMsg("cli-uninstall-no-short"),
        getMsg("cli-uninstall-yes-long"),
        getMsg("cli-uninstall-no-long")
    };
    String answer = promptConfirm(
        getMsg("cli-uninstall-confirm-delete-files"),
        getMsg("cli-uninstall-yes-long"), validValues);

    if (getMsg("cli-uninstall-no-short").equalsIgnoreCase(answer) ||
        getMsg("cli-uninstall-no-long").equalsIgnoreCase(answer))
    {
      confirm = false;
    }
    return confirm;
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private static String getMsg(String key)
  {
    return org.opends.server.util.StaticUtils.wrapText(getI18n().getMsg(key),
        Utils.getCommandLineMaxLineWidth());
  }

  private static String getMsg(String key, String[] args)
  {
    return org.opends.server.util.StaticUtils.wrapText(
        getI18n().getMsg(key, args), Utils.getCommandLineMaxLineWidth());
  }

  private static ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
