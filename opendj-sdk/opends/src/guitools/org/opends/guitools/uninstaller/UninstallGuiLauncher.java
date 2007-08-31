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

package org.opends.guitools.uninstaller;

import static org.opends.messages.ToolMessages.INFO_DESCRIPTION_SHOWUSAGE;
import static org.opends.server.tools.ToolConstants.OPTION_LONG_HELP;
import static org.opends.server.tools.ToolConstants.OPTION_SHORT_HELP;

import java.io.File;
import java.util.logging.Logger;

import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.util.Utils;
import org.opends.messages.AdminToolMessages;
import org.opends.messages.ToolMessages;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;

/**
 * This class is called by the uninstall command lines to launch the uninstall
 * of the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based uninstall much be launched.
 */
public class UninstallGuiLauncher extends UninstallLauncher {

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-uninstall-gui-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  static private final Logger LOG =
          Logger.getLogger(UninstallGuiLauncher.class.getName());

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
    new UninstallGuiLauncher(args).launch();
  }

  private ArgumentParser argParser;

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public UninstallGuiLauncher(String[] args) {
    super(args);

    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_UNINSTALL_GUI_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_UNINSTALL_GUI_FILE_NAME;
    }
    System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
  }

  /**
   * Initialize the contents of the argument parser.
   */
  protected void initializeParser()
  {
    argParser = new ArgumentParser(getClass().getName(),
        AdminToolMessages.INFO_UNINSTALL_LAUNCHER_USAGE_DESCRIPTION.get(),
        false);
    try
    {
      BooleanArgument showUsageArg = new BooleanArgument("showUsage",
          OPTION_SHORT_HELP,
          OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsageArg);
      argParser.setUsageArgument(showUsageArg);
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      System.err.println(org.opends.server.util.StaticUtils.wrapText(
          ToolMessages.ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()),
          Utils.getCommandLineMaxLineWidth()));
    }
  }

  /**
   * {@inheritDoc}
   */
  public void launch() {
    if (shouldPrintVersion())
    {
      if (!argParser.usageOrVersionDisplayed())
      {
        printVersion();
      }
      System.exit(ReturnCode.PRINT_VERSION.getReturnCode());
    }
    else if (shouldPrintUsage()) {
      if (!argParser.usageOrVersionDisplayed())
      {
        printUsage(false);
      }
      System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
    } else {
      willLaunchGui();
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
        System.exit(exitCode);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isCli() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public ArgumentParser getArgumentParser() {
    return this.argParser;
  }
}