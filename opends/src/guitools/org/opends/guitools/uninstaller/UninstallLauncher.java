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

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.ERR_ERROR_PARSING_ARGS;

import org.opends.messages.Message;
import org.opends.messages.ToolMessages;

import java.io.File;
import java.util.logging.Logger;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;

/**
 * This class is called by the uninstall command lines to launch the uninstall
 * of the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based uninstall much be launched.
 */
public class UninstallLauncher extends Launcher {

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-uninstall-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  static private final Logger LOG =
          Logger.getLogger(UninstallLauncher.class.getName());

  /**
   * The main method which is called by the uninstall command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX),
              "org.opends.guitools.uninstaller");

    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }
    new UninstallLauncher(args).launch();
  }

  private UninstallerArgumentParser argParser;

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public UninstallLauncher(String[] args) {
    super(args);

    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_UNINSTALL_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_UNINSTALL_FILE_NAME;
    }
    System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);

    initializeParser();
  }

  /**
   * {@inheritDoc}
   */
  public void launch() {
    //  Validate user provided data
    try
    {
      argParser.parseArguments(args);
      if (argParser.isVersionArgumentPresent())
      {
        System.exit(ReturnCode.PRINT_VERSION.getReturnCode());
      }
      else if (argParser.isUsageArgumentPresent())
      {
        System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
      }
      else
      {
        super.launch();
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
  }

  /**
   * Initialize the contents of the argument parser.
   */
  protected void initializeParser()
  {
    argParser = new UninstallerArgumentParser(getClass().getName(),
        INFO_UNINSTALL_LAUNCHER_USAGE_DESCRIPTION.get(), false);
    try
    {
      argParser.initializeGlobalArguments(System.err);
    }
    catch (ArgumentException ae)
    {
      Message message =
        ToolMessages.ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(org.opends.server.util.StaticUtils.wrapText(message,
          Utils.getCommandLineMaxLineWidth()));
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFilePath) {
    if (logFilePath != null)
    {
      System.err.println(ERR_UNINSTALL_LAUNCHER_GUI_LAUNCHED_FAILED_DETAILS
              .get(logFilePath));
    }
    else
    {
      System.err.println(ERR_UNINSTALL_LAUNCHER_GUI_LAUNCHED_FAILED.get());
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
  protected void willLaunchGui() {
    System.out.println(INFO_UNINSTALL_LAUNCHER_LAUNCHING_GUI.get());
    System.setProperty("org.opends.quicksetup.Application.class",
            org.opends.guitools.uninstaller.Uninstaller.class.getName());
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    return new Uninstaller();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getFrameTitle() {
    return INFO_FRAME_UNINSTALL_TITLE.get();
  }

  /**
   * Indicates whether or not the launcher should print a usage
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean shouldPrintUsage() {
    return argParser.isUsageArgumentPresent() &&
    !argParser.usageOrVersionDisplayed();
  }

  /**
   * Indicates whether or not the launcher should print a usage
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean isQuiet() {
    return argParser.isQuiet();
  }

  /**
   * Indicates whether or not the launcher should print a usage
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean isNoPrompt() {
    return !argParser.isInteractive();
  }

  /**
   * Indicates whether or not the launcher should print a version
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates version should be printed
   */
  protected boolean shouldPrintVersion() {
    return argParser.isVersionArgumentPresent() &&
    !argParser.usageOrVersionDisplayed();
  }

  /**
   * Indicates whether the launcher will launch a command line versus
   * a graphical application based on the contents of the arguments
   * passed into the constructor.
   *
   * @return boolean where true indicates that a CLI application
   *         should be launched
   */
  protected boolean isCli() {
    return argParser.isCli();
  }

}
