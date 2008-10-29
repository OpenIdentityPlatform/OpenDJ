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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import static org.opends.messages.QuickSetupMessages.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.ReturnCode;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;

import java.util.logging.Logger;
import java.io.File;

/**
 * This class is called by the upgrade and upgrade.bat
 * command line utilities to launch an upgrade process.
 */
public class UpgradeSvr4Launcher extends UpgradeLauncher {

  static private final Logger LOG =
          Logger.getLogger(UpgradeSvr4Launcher.class.getName());

  /**
   * The main method which is called by the setup command lines.
   *
   * @param args the arguments passed by the command lines.
   */
  public static void main(String[] args) {
    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX,
                      QuickSetupLog.LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println(INFO_ERROR_INITIALIZING_LOG.get());
      t.printStackTrace();
    }
    new UpgradeSvr4Launcher(args).launch();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected CliApplication createCliApplication() {
   return new UpgraderSvr4();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void willLaunchGui() {
    System.out.println(INFO_UPGRADE_LAUNCHER_LAUNCHING_GUI.get());
    System.setProperty("org.opends.quicksetup.Application.class",
            "org.opends.quicksetup.upgrader.UpgraderSvr4");
  }

  private ArgumentParser argParser;

  private BooleanArgument showUsage;
  private BooleanArgument quiet;
  private BooleanArgument noPrompt;
  private BooleanArgument verbose;
  private BooleanArgument revertMostRecent;
  private StringArgument reversionArchive;


  /**
   * Creates an instance.
   *
   * @param args specified on command line
   */
  protected UpgradeSvr4Launcher(String[] args) {
    super(args);

    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_UPGRADE_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_UPGRADE_FILE_NAME;
    }
    if (System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME) == null)
    {
      System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    }

    argParser = new ArgumentParser(getClass().getName(),
        INFO_UPGRADE_LAUNCHER_USAGE_DESCRIPTION.get(), false);
    try
    {
      revertMostRecent = new BooleanArgument(
              REVERT_MOST_RECENT_OPTION_LONG,
              REVERT_MOST_RECENT_OPTION_SHORT,
              REVERT_MOST_RECENT_OPTION_LONG,
              INFO_REVERT_DESCRIPTION_RECENT.get());
      argParser.addArgument(revertMostRecent);

      reversionArchive = new StringArgument(
              REVERT_ARCHIVE_OPTION_LONG,
              REVERT_ARCHIVE_OPTION_SHORT,
              REVERT_ARCHIVE_OPTION_LONG,
              false, false, true,
              INFO_DIRECTORY_PLACEHOLDER.get(),
              null, null, INFO_REVERT_DESCRIPTION_DIRECTORY.get());
      argParser.addArgument(reversionArchive);

      noPrompt = new BooleanArgument(
              OPTION_LONG_NO_PROMPT,
              OPTION_SHORT_NO_PROMPT,
              OPTION_LONG_NO_PROMPT,
              INFO_UPGRADE_DESCRIPTION_NO_PROMPT.get());
      argParser.addArgument(noPrompt);

      quiet = new BooleanArgument(
              OPTION_LONG_QUIET,
              OPTION_SHORT_QUIET,
              OPTION_LONG_QUIET,
              INFO_UPGRADE_DESCRIPTION_SILENT.get());
      argParser.addArgument(quiet);

      verbose = new BooleanArgument(OPTION_LONG_VERBOSE, OPTION_SHORT_VERBOSE,
          OPTION_LONG_VERBOSE, INFO_DESCRIPTION_VERBOSE.get());
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument(
              "showusage",
              OPTION_SHORT_HELP,
              OPTION_LONG_HELP,
              INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);

      try {
        argParser.parseArguments(args);

        // Set fields indicating reversion or upgrade.  This may change
        // later if interaction is required to make the determination.
        isReversion =
                reversionArchive.isPresent() || revertMostRecent.isPresent();
        isUpgrade = !isReversion;

        if (argParser.usageOrVersionDisplayed()) {
          System.exit(ReturnCode.PRINT_USAGE.getReturnCode());
        }
      } catch (ArgumentException ae) {
        System.err.println(ae.getMessageObject());
        printUsage(false);
        System.exit(ReturnCode.
                APPLICATION_ERROR.getReturnCode());
      }
    }
    catch (Throwable t)
    {
      System.out.println("ERROR: "+t);
      t.printStackTrace();
    }

  }

}
