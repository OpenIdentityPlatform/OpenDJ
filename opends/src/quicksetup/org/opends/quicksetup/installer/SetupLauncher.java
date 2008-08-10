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

package org.opends.quicksetup.installer;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.util.logging.Logger;

import org.opends.quicksetup.Constants;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.installer.offline.OfflineInstaller;
import org.opends.quicksetup.util.IncompatibleVersionException;
import org.opends.quicksetup.util.Utils;
import org.opends.messages.Message;
import org.opends.server.tools.InstallDS;
import org.opends.server.tools.InstallDSArgumentParser;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;

/**
 * This class is called by the setup command line to launch the setup
 * of the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based setup much be launched.
 */
public class SetupLauncher extends Launcher {

  static private final Logger LOG =
          Logger.getLogger(SetupLauncher.class.getName());

  /**
   * The main method which is called by the setup command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(Constants.LOG_FILE_PREFIX,
                  Constants.LOG_FILE_SUFFIX),
              "org.opends.quicksetup.installer");
      QuickSetupLog.disableConsoleLogging();

    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }
    new SetupLauncher(args).launch();
  }

  private InstallDSArgumentParser argParser;

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public SetupLauncher(String[] args) {
    super(args);
    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_SETUP_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_SETUP_FILE_NAME;
    }
    if (System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME) == null)
    {
      System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    }
    initializeParser();
  }

  /**
   * Initialize the contents of the argument parser.
   */
  protected void initializeParser()
  {
    argParser = new InstallDSArgumentParser(InstallDS.class.getName());
    try
    {
      argParser.initializeArguments();
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.out.println(message);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void launch() {
    try
    {
      argParser.parseArguments(args);

      if (argParser.isVersionArgumentPresent())
      {
        System.exit(ReturnCode.PRINT_VERSION.getReturnCode());
      }
      // The second condition is required when the user specifies '?'
      else if (argParser.isUsageArgumentPresent() ||
          argParser.usageOrVersionDisplayed())
      {
        System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
      }
      else if (isCli())
      {
        Utils.checkJavaVersion();
        System.exit(InstallDS.mainCLI(args));
      }
      else
      {
        willLaunchGui();
        // The java version is checked in the launchGui code to be sure
        // that if there is a problem with the java version the message
        // (if possible) is displayed graphically.
        int exitCode = launchGui(args);
        if (exitCode != 0) {
          File logFile = QuickSetupLog.getLogFile();
          if (logFile != null)
          {
            guiLaunchFailed(logFile.toString());
          }
          else
          {
            guiLaunchFailed(null);
          }
          Utils.checkJavaVersion();
          System.exit(InstallDS.mainCLI(args));
        }
      }
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      System.err.println(message);
      System.err.println();
      System.err.println(argParser.getUsage());

      System.exit(ReturnCode.USER_DATA_ERROR.getReturnCode());
    }
    catch (IncompatibleVersionException ive)
    {
      System.err.println(ive.getMessageObject());
      System.exit(ReturnCode.JAVA_VERSION_INCOMPATIBLE.getReturnCode());
    }
  }

  /**
   * {@inheritDoc}
   */
  public ArgumentParser getArgumentParser() {
    return this.argParser;
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFileName) {
    if (logFileName != null)
    {
      System.err.println(INFO_SETUP_LAUNCHER_GUI_LAUNCHED_FAILED_DETAILS.get(
              logFileName));
    }
    else
    {
      System.err.println(INFO_SETUP_LAUNCHER_GUI_LAUNCHED_FAILED.get());
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void willLaunchGui() {
    System.out.println(INFO_SETUP_LAUNCHER_LAUNCHING_GUI.get());
    System.setProperty("org.opends.quicksetup.Application.class",
            OfflineInstaller.class.getName());
  }

  /**
   * {@inheritDoc}
   */
  protected Message getFrameTitle() {
    return INFO_FRAME_INSTALL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isCli() {
    return argParser.isCli();
  }
}
