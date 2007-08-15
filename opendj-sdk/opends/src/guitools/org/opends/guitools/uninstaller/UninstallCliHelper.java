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

package org.opends.guitools.uninstaller;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.quicksetup.*;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;

import java.util.Set;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

import org.opends.messages.Message;
import static org.opends.messages.AdminToolMessages.*;


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

  static private final Logger LOG =
          Logger.getLogger(UninstallCliHelper.class.getName());

  /**
   * Creates a UserData based in the arguments provided.  It asks
   * user for additional information if what is provided in the arguments is not
   * enough.
   * @param args the ArgumentParser with the allowed arguments of the command
   * line.
   * @param rawArguments the arguments provided in the command line.
   * @param trustManager the Application Trust Manager to be used to connect
   * to the remote servers.
   * @return the UserData object with what the user wants to uninstall
   * and null if the user cancels the uninstallation.
   * @throws UserDataException if there is an error parsing the data
   * in the arguments.
   */
  public UninstallUserData createUserData(ArgumentParser args,
      String[] rawArguments, ApplicationTrustManager trustManager)
  throws UserDataException
  {
    UninstallUserData userData = new UninstallUserData();

    boolean isInteractive;
    boolean isSilent;
    boolean isCancelled = false;

    /* Step 1: analyze the arguments.  We assume that the arguments have
     * already been parsed.
     */
    try
    {
      args.parseArguments(rawArguments);
    }
    catch (ArgumentException ae)
    {
      throw new UserDataException(null, ae.getMessageObject());
    }

    Argument interactive = args.getArgumentForLongID(INTERACTIVE_OPTION_LONG);
    isInteractive = interactive != null && interactive.isPresent();

    Argument silent = args.getArgumentForLongID(SILENT_OPTION_LONG);
    isSilent = silent != null && silent.isPresent();

    userData.setSilent(isSilent);

    /* Step 2: If this is an interactive uninstall ask for confirmation to
     * delete the different parts of the installation.
     */
    Set<String> outsideDbs;
    Set<String> outsideLogs;
    Configuration config =
            Installation.getLocal().getCurrentConfiguration();
    try {
      outsideDbs = config.getOutsideDbs();
    } catch (IOException ioe) {
      outsideDbs = Collections.emptySet();
      LOG.log(Level.INFO, "error determining outside databases", ioe);
    }

    try {
      outsideLogs = config.getOutsideLogs();
    } catch (IOException ioe) {
      outsideLogs = Collections.emptySet();
      LOG.log(Level.INFO, "error determining outside logs", ioe);
    }

    if (!isInteractive)
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
      isCancelled = askConfirmationToStop(userData, isInteractive);
    }

    if (isCancelled)
    {
      userData = null;
    }

    return userData;
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
    Message[] options = new Message[] {
      Message.raw("1"),
      Message.raw("2"),
      Message.raw("3")
    };
    Message answer = promptConfirm(
            INFO_CLI_UNINSTALL_WHAT_TO_DELETE.get(),
            options[0], options);
    if (options[2].equals(answer))
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
        Message[] keys = {
                INFO_CLI_UNINSTALL_CONFIRM_LIBRARIES_BINARIES.get(),
                INFO_CLI_UNINSTALL_CONFIRM_DATABASES.get(),
                INFO_CLI_UNINSTALL_CONFIRM_LOGS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_CONFIGURATION_SCHEMA.get(),
                INFO_CLI_UNINSTALL_CONFIRM_BACKUPS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_LDIFS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_OUTSIDEDBS.get(
                        Utils.getStringFromCollection(outsideDbs,
                                Constants.LINE_SEPARATOR)),
                INFO_CLI_UNINSTALL_CONFIRM_OUTSIDELOGS.get(
                        Utils.getStringFromCollection(outsideLogs,
                                Constants.LINE_SEPARATOR)
                )
        };

        Message[] validValues = {
                INFO_CLI_UNINSTALL_YES_LONG.get(),
                INFO_CLI_UNINSTALL_NO_LONG.get(),
                INFO_CLI_UNINSTALL_YES_SHORT.get(),
                INFO_CLI_UNINSTALL_NO_SHORT.get()
        };
        boolean[] answers = new boolean[keys.length];
        for (int i=0; i<keys.length; i++)
        {
          boolean ignore = ((i == 6) && (outsideDbs.size() == 0)) ||
          ((i == 7) && (outsideLogs.size() == 0));
          if (!ignore)
          {
            Message msg = keys[i];
            answer = promptConfirm(
                    msg, INFO_CLI_UNINSTALL_YES_LONG.get(),
                    validValues);

            answers[i] =
                    INFO_CLI_UNINSTALL_YES_LONG.get().toString().
                            equalsIgnoreCase(answer.toString()) ||
                            INFO_CLI_UNINSTALL_YES_SHORT.get().toString().
                                    equalsIgnoreCase(answer.toString());
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
              INFO_CLI_UNINSTALL_NOTHING_TO_BE_UNINSTALLED.get());
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
   * @param interactive boolean telling whether this is an interactive uninstall
   * or not.
   * @return <CODE>true</CODE> if the user wants to continue with uninstall and
   * <CODE>false</CODE> otherwise.
   * @throws UserDataException if there is a problem with the data
   * provided by the user (in the particular case where we are on silent
   * uninstall and some data is missing or not valid).
   */
  private boolean askConfirmationToStop(UserData userData,
                                        boolean interactive)
  throws UserDataException
  {
    boolean cancelled = false;
    Status status = Installation.getLocal().getStatus();
    if (status.isServerRunning())
    {
        if (interactive)
        {
            /* Ask for confirmation to stop server */
            cancelled = !confirmToStopServer();
        }

        if (!cancelled)
        {
            /* During all the confirmations, the server might be stopped. */
            userData.setStopServer(status.isServerRunning());
        }
    }
    else
    {
      userData.setStopServer(false);
      if (interactive)
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
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_STOP.get());
  }

  /**
   *  Ask for confirmation to delete files.
   *  @return <CODE>true</CODE> if the user wants to continue and delete the
   *  files.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmDeleteFiles()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_DELETE_FILES.get());
  }

  private boolean confirm(Message msg) {
    boolean confirm = true;
    Message[] validValues = {
        INFO_CLI_UNINSTALL_YES_SHORT.get(),
        INFO_CLI_UNINSTALL_NO_SHORT.get(),
        INFO_CLI_UNINSTALL_YES_LONG.get(),
        INFO_CLI_UNINSTALL_NO_LONG.get(),
    };
    Message answer = promptConfirm(msg, validValues[2], validValues);

    if (INFO_CLI_UNINSTALL_NO_SHORT.get().toString()
            .equalsIgnoreCase(answer.toString()) ||
        INFO_CLI_UNINSTALL_NO_LONG.get().toString()
                .equalsIgnoreCase(answer.toString()))
    {
      confirm = false;
    }
    return confirm;
  }
}
