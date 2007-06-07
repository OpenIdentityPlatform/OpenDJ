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


import org.opends.quicksetup.*;
import org.opends.quicksetup.util.Utils;

import java.util.HashSet;
import java.util.Set;

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
class UninstallCliHelper extends CliApplicationHelper {

  static private String FORMAT_KEY = "cli-uninstall-confirm-prompt";

  /**
   * Creates a UserData based in the arguments provided.  It asks
   * user for additional information if what is provided in the arguments is not
   * enough.
   * @param args the arguments provided in the command line.
   * @param installStatus the current install status.
   * @return the UserData object with what the user wants to uninstall
   * and null if the user cancels the uninstallation.
   * @throws UserDataException if there is an error parsing the data
   * in the arguments.
   */
  public UninstallUserData createUserData(String[] args,
      CurrentInstallStatus installStatus) throws UserDataException
  {
    UninstallUserData userData = new UninstallUserData();

    boolean silentUninstall;
    boolean isCancelled = false;

    /* Step 1: validate the arguments
     */
    Set<String> validArgs = new HashSet<String>();
    validArgs.add("--cli");
    validArgs.add("-H");
    validArgs.add("--help");
    validArgs.add("--silentUninstall");
    validArgs.add("-s");
    validateArguments(userData, args, validArgs);

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
      isCancelled = askConfirmationToStop(userData, silentUninstall);
    }

    if (isCancelled)
    {
      userData = null;
    }

    return userData;
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
   * parts of the server.  It updates the provided UserData object
   * accordingly.  Returns <CODE>true</CODE> if the user cancels and <CODE>
   * false</CODE> otherwise.
   * @param userData the UserData object to be updated.
   * @param outsideDbs the set of relative paths of databases located outside
   * the installation path of the server.
   * @param outsideLogs the set of relative paths of log files located outside
   * the installation path of the server.
   * @return <CODE>true</CODE> if the user cancels and <CODE>false</CODE>
   * otherwise.
   */
  private boolean askWhatToDelete(UninstallUserData userData,
      Set<String> outsideDbs, Set<String> outsideLogs)
  {
    boolean cancelled = false;

    String answer = promptConfirm(FORMAT_KEY,
            getMsg("cli-uninstall-what-to-delete"),
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
                  Constants.LINE_SEPARATOR)};
              msg = getMsg(keys[i], arg);
            }
            else if (i == 7)
            {
              String[] arg = {Utils.getStringFromCollection(outsideLogs,
                  Constants.LINE_SEPARATOR)};
              msg = getMsg(keys[i], arg);
            }
            else
            {
              msg = getMsg(keys[i]);
            }
            answer = promptConfirm(FORMAT_KEY,
                msg, getMsg("cli-uninstall-yes-long"),
                validValues);

            answers[i] =
                    getMsg("cli-uninstall-yes-long").equalsIgnoreCase(answer) ||
                    getMsg("cli-uninstall-yes-short").equalsIgnoreCase(answer);
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
          System.out.println(Constants.LINE_SEPARATOR+
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
   * @param userData the UserData object to be updated with the
   * authentication of the user.
   * @param silentUninstall boolean telling whether this is a silent uninstall
   * or not.
   * @return <CODE>true</CODE> if the user wants to continue with uninstall and
   * <CODE>false</CODE> otherwise.
   * @throws UserDataException if there is a problem with the data
   * provided by the user (in the particular case where we are on silent
   * uninstall and some data is missing or not valid).
   */
  private boolean askConfirmationToStop(UserData userData,
                                        boolean silentUninstall)
  throws UserDataException
  {
    boolean cancelled = false;

    if (CurrentInstallStatus.isServerRunning())
    {
        if (!silentUninstall)
        {
            /* Ask for confirmation to stop server */
            cancelled = !confirmToStopServer();
        }

        if (!cancelled)
        {
            /* During all the confirmations, the server might be stopped. */
            userData.setStopServer(CurrentInstallStatus.isServerRunning());
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
    String answer = promptConfirm(FORMAT_KEY,
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
    String answer = promptConfirm(FORMAT_KEY,
        getMsg("cli-uninstall-confirm-delete-files"),
        getMsg("cli-uninstall-yes-long"), validValues);

    if (getMsg("cli-uninstall-no-short").equalsIgnoreCase(answer) ||
        getMsg("cli-uninstall-no-long").equalsIgnoreCase(answer))
    {
      confirm = false;
    }
    return confirm;
  }

}
