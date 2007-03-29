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

import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.i18n.ResourceProvider;

/**
 * Controller for managing the execution of a CliApplication.
 */
public class QuickSetupCli {

  /**
   * Return code: Uninstall successful.
   */
  static public int SUCCESSFUL = 0;

  /**
   * Return code: User Cancelled uninstall.
   */
  static public int CANCELLED = 1;

  /**
   * Return code: User provided invalid data.
   */
  static public int USER_DATA_ERROR = 2;

  /**
   * Return code: Error accessing file system (reading/writing).
   */
  static public int ERROR_ACCESSING_FILE_SYSTEM = 3;

  /**
   * Return code: Error stopping server.
   */
  static public int ERROR_STOPPING_SERVER = 4;

  /**
   * Return code: Bug.
   */
  static public int BUG = 5;

  /** Platform dependent filesystem separator. */
  public static String LINE_SEPARATOR = System.getProperty("line.separator");

  /** Arguments passed in the command line. */
  protected String[] args;

  private CliApplication cliApp;

  /**
   * Creates a QuickSetupCli instance.
   * @param cliApp the application to be run
   * @param args arguments passed in from the command line
   */
  public QuickSetupCli(CliApplication cliApp, String[] args) {
    this.cliApp = cliApp;
    this.args = args;
  }

  /**
   * Parses the user data and prompts the user for data if required.  If the
   * user provides all the required data it launches the Uninstaller.
   *
   * @return the return code (SUCCESSFUL, CANCELLED, USER_DATA_ERROR,
   * ERROR_ACCESSING_FILE_SYSTEM, ERROR_STOPPING_SERVER or BUG.
   */
  public int run()
  {
    int returnValue;
    // Parse the arguments
    try
    {
      CurrentInstallStatus installStatus = new CurrentInstallStatus();
      UserData userData = cliApp.createUserData(args, installStatus);
      if (userData != null)
      {
        ProgressMessageFormatter formatter =
                new PlainTextProgressMessageFormatter();
        cliApp.setUserData(userData);
        cliApp.setProgressMessageFormatter(formatter);
        cliApp.addProgressUpdateListener(
            new ProgressUpdateListener()
            {
              /**
               * ProgressUpdateListener implementation.
               * @param ev the ProgressUpdateEvent we receive.
               *
               */
              public void progressUpdate(ProgressUpdateEvent ev)
              {
                System.out.print(
                    org.opends.server.util.StaticUtils.wrapText(ev.getNewLogs(),
                        Utils.getCommandLineMaxLineWidth()));
              }
            });
        new Thread(cliApp).start();
        while (!cliApp.isFinished())
        {
          try
          {
            Thread.sleep(100);
          }
          catch (Exception ex)
          {
          }
        }

        ApplicationException ue = cliApp.getException();
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
                  "Unknown ApplicationException type: "+ue.getType());
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
    catch (UserDataException uude)
    {
      System.err.println(LINE_SEPARATOR+uude.getLocalizedMessage()+
          LINE_SEPARATOR);
      returnValue = USER_DATA_ERROR;
    }
    return returnValue;
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

  private static ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
