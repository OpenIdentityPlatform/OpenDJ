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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.CLIException;
import org.opends.messages.Message;

/**
 * Class used by Launcher to start a CLI application.
 *
 */
public class QuickSetupCli {

  /** Arguments passed in the command line. */
  protected Launcher launcher;

  private CliApplication cliApp;

  private UserData userData;

  /**
   * Creates a QuickSetupCli instance.
   * @param cliApp the application to be run
   * @param launcher that launched the app
   */
  public QuickSetupCli(CliApplication cliApp, Launcher launcher) {
    this.cliApp = cliApp;
    this.launcher = launcher;
  }

  /**
   * Gets the user data this application will use when running.
   * @return UserData to use when running
   */
  public UserData getUserData() {
    return this.userData;
  }

  /**
   * Parses the user data and prompts the user for data if required.  If the
   * user provides all the required data it launches the Uninstaller.
   *
   * @return the return code (SUCCESSFUL, CANCELLED, USER_DATA_ERROR,
   * ERROR_ACCESSING_FILE_SYSTEM, ERROR_STOPPING_SERVER or BUG.
   */
  public ReturnCode run()
  {
    ReturnCode returnValue;
    // Parse the arguments
    try
    {
      ProgressMessageFormatter formatter =
        new PlainTextProgressMessageFormatter();
      cliApp.setProgressMessageFormatter(formatter);
      userData = cliApp.createUserData(launcher);
      if (userData != null)
      {
        cliApp.setUserData(userData);
        if (!userData.isQuiet()) {
          cliApp.addProgressUpdateListener(
                  new ProgressUpdateListener() {
                    public void progressUpdate(ProgressUpdateEvent ev) {
                      Message newLogs = ev.getNewLogs();
                      if (newLogs != null) {
                        System.out.print(
                                StaticUtils.wrapText(
                                        newLogs,
                                        Utils.getCommandLineMaxLineWidth()));
                      }
                    }
                  });
        }
        Thread appThread = new Thread(cliApp, "CLI Application");
        appThread.start();
        while (!Thread.State.TERMINATED.equals(appThread.getState())) {
          try {
            Thread.sleep(100);
          } catch (Exception ex) {
            // do nothing;
          }
        }
        returnValue = cliApp.getReturnCode();
        if (returnValue == null) {
          ApplicationException ue = cliApp.getRunError();
          if (ue != null)
          {
            returnValue = ue.getType();
          }
          else
          {
            returnValue = ReturnCode.SUCCESSFUL;
          }
        }
      }
      else
      {
        // User cancelled operation.
        returnValue = ReturnCode.CANCELLED;
      }
    }
    catch (UserDataException uude)
    {
      System.err.println();
      System.err.println(StaticUtils.wrapText(uude.getLocalizedMessage(),
              Utils.getCommandLineMaxLineWidth()));
      System.err.println();
      if (uude.getCause() instanceof CLIException)
      {
        returnValue = ReturnCode.USER_INPUT_ERROR;
      }
      else
      {
        returnValue = ReturnCode.USER_DATA_ERROR;
      }
    }
    return returnValue;
  }

}
