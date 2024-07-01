/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import static com.forgerock.opendj.cli.Utils.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

import com.forgerock.opendj.cli.ClientException;

/** Class used by Launcher to start a CLI application. */
public class QuickSetupCli {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Arguments passed in the command line. */
  private final Launcher launcher;
  private final CliApplication cliApp;
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
   * user provides all the required data it launches the application.
   *
   * @return the return code (SUCCESSFUL, CANCELLED, USER_DATA_ERROR,
   * ERROR_ACCESSING_FILE_SYSTEM, ERROR_STOPPING_SERVER or BUG.
   */
  ReturnCode run()
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
                    @Override
                    public void progressUpdate(ProgressUpdateEvent ev) {
                      LocalizableMessage newLogs = ev.getNewLogs();
                      if (newLogs != null) {
                        System.out.print(
                                wrapText(newLogs, Utils.getCommandLineMaxLineWidth()));
                      }
                    }
                  });
        }
        Thread appThread = new Thread(cliApp, "CLI Application");
        logger.info(LocalizableMessage.raw("Launching application"));
        appThread.start();
        while (!Thread.State.TERMINATED.equals(appThread.getState())) {
          try {
            Thread.sleep(100);
          } catch (Exception ex) {
            // do nothing;
          }
        }
        returnValue = cliApp.getReturnCode();
        logger.info(LocalizableMessage.raw("Application returnValue: "+returnValue));
        if (returnValue == null) {
          ApplicationException ue = cliApp.getRunError();
          if (ue != null)
          {
            logger.info(LocalizableMessage.raw("Application run error: "+ue, ue));
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
        returnValue = ReturnCode.CANCELED;
      }
    }
    catch (UserDataException uude)
    {
      logger.error(LocalizableMessage.raw("UserDataException: "+uude, uude));
      System.err.println();
      System.err.println(wrapText(uude.getLocalizedMessage(),
              Utils.getCommandLineMaxLineWidth()));
      System.err.println();
      if (uude.getCause() instanceof ClientException)
      {
        returnValue = ReturnCode.USER_INPUT_ERROR;
      }
      else
      {
        returnValue = ReturnCode.USER_DATA_ERROR;
      }
    }
    catch (ApplicationException ae)
    {
      logger.error(LocalizableMessage.raw("ApplicationException: "+ae, ae));
      System.err.println();
      System.err.println(ae.getLocalizedMessage());
      System.err.println();
      returnValue = ae.getType();
    }
    catch (Throwable t)
    {
      logger.error(LocalizableMessage.raw("Unexpected error: "+t, t));
      returnValue = ReturnCode.UNKNOWN;
    }
    logger.info(LocalizableMessage.raw("returnValue: "+returnValue.getReturnCode()));
    return returnValue;
  }

}
