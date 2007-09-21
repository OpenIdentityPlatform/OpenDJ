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

package org.opends.quicksetup.upgrader;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import org.opends.quicksetup.Launcher;
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
public class UpgradeLauncher extends Launcher {

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-upgrade-";

  static private final Logger LOG =
          Logger.getLogger(UpgradeLauncher.class.getName());

  /** Short form of the option for specifying the installation package file. */
  static public final Character FILE_OPTION_SHORT = 'f';

  /** Long form of the option for specifying the installation package file. */
  static public final String FILE_OPTION_LONG = "file";

  /** Short form of the option for specifying the reversion files directory. */
  static public final Character REVERT_ARCHIVE_OPTION_SHORT = 'a';

  /** Long form of the option for specifying the reversion files directory. */
  static public final String REVERT_ARCHIVE_OPTION_LONG = "reversionArchive";

  /** Short form of the option for specifying the 'most recent' option. */
  static public final Character REVERT_MOST_RECENT_OPTION_SHORT = 'r';

  /** Long form of the option for specifying the 'most recent' option. */
  static public final String REVERT_MOST_RECENT_OPTION_LONG ="revertMostRecent";

  /** Indicates that this operation is an upgrade as opposed to reversion. */
  protected boolean isUpgrade;

  /** Indicates that this operation is a reversion as opposed to an upgrade. */
  protected boolean isReversion;

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
    new UpgradeLauncher(args).launch();
  }

  private ArgumentParser argParser;

  private BooleanArgument showUsage;
  private StringArgument file;
  private BooleanArgument quiet;
  private BooleanArgument noPrompt;
  private BooleanArgument revertMostRecent;
  private StringArgument reversionArchive;


  /**
   * {@inheritDoc}
   */
  protected Message getFrameTitle() {
    return INFO_FRAME_UPGRADE_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isCli() {
    // for now only CLI is supported via command line
    return true;
  }

  /**
   * {@inheritDoc}
   */
  protected void printUsage(boolean toStdErr) {
    try
    {
      ArgumentParser argParser = getArgumentParser();
      if (argParser != null) {
        String msg = argParser.getUsage();
        printUsage(msg, toStdErr);
      }
    }
    catch (Throwable t)
    {
      System.out.println("ERROR: "+t);
      t.printStackTrace();
    }
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    return new Upgrader();
  }

  /**
   * {@inheritDoc}
   */
  protected void willLaunchGui() {
    System.out.println(INFO_UPGRADE_LAUNCHER_LAUNCHING_GUI.get());
    System.setProperty("org.opends.quicksetup.Application.class",
            "org.opends.quicksetup.upgrader.Upgrader");
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFilePath) {
    if (logFilePath != null)
    {
      System.err.println(
              INFO_UPGRADE_LAUNCHER_GUI_LAUNCHED_FAILED_DETAILS.get(
                      logFilePath));
    }
    else
    {
      System.err.println(INFO_UPGRADE_LAUNCHER_GUI_LAUNCHED_FAILED.get());
    }
  }

  /**
   * {@inheritDoc}
   */
  public ArgumentParser getArgumentParser() {
    return argParser;
  }

  /**
   * Indicates whether or not this operation is silent.
   * @return boolean where true indicates silence
   */
  public boolean isQuiet() {
    return quiet.isPresent();
  }

  /**
   * Indicates whether or not this operation is interactive.
   * @return boolean where true indicates noninteractive
   */
  public boolean isNoPrompt() {
    return noPrompt.isPresent();
  }

  /**
   * Indicates whether this invocation is intended to upgrade the current
   * build as opposed to revert.
   * @return boolean where true indicates upgrade
   */
  public boolean isUpgrade() {
    return isUpgrade;
  }

  /**
   * Indicates whether this invocation is intended to revert the current
   * build as opposed to upgrade.
   * @return boolean where true indicates revert
   */
  public boolean isReversion() {
    return isReversion;
  }

  /**
   * Indicates that none of the options that indicate an upgrade
   * or reversion where specified on the command line so we are going
   * to have to prompt for the information or fail.
   *
   * @return boolean where true means this application needs to ask
   *         the user which operation they would like to perform; false
   *         means no further input is required
   */
  public boolean isInteractive() {
    return !file.isPresent() &&
            !reversionArchive.isPresent() &&
            !revertMostRecent.isPresent();
  }

  /**
   * Gets the name of the file to be used for upgrade.
   * @return name of the upgrade file
   */
  public String getUpgradeFileName() {
    return file.getValue();
  }

  /**
   * Gets the name of the directory to be used for reversion.
   * @return name of the reversion directory
   */
  public String getReversionArchiveDirectoryName() {
    return reversionArchive.getValue();
  }


  /**
   * Gets the file's directory if specified on the command line.
   * @return File representing the directory where the reversion files are
   * stored.
   */
  public File getReversionArchiveDirectory() {
    File f = null;
    String s = reversionArchive.getValue();
    if (s != null) {
      f = new File(s);
    }
    return f;
  }

  /**
   * Indicates whether the user has specified the 'mostRecent' option.
   * @return boolean where true indicates use the most recent upgrade backup
   */
  public boolean isRevertMostRecent() {
    return revertMostRecent.isPresent();
  }

  /**
   * Creates an instance.
   *
   * @param args specified on command line
   */
  protected UpgradeLauncher(String[] args) {
    super(args);

    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_UPGRADE_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_UPGRADE_FILE_NAME;
    }
    System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);

    argParser = new ArgumentParser(getClass().getName(),
        INFO_UPGRADE_LAUNCHER_USAGE_DESCRIPTION.get(), false);
    try
    {
      file = new StringArgument(
              FILE_OPTION_LONG,
              FILE_OPTION_SHORT,
              FILE_OPTION_LONG,
              false, false, true,
              "{file}",
              null, null, INFO_UPGRADE_DESCRIPTION_FILE.get());
      argParser.addArgument(file);

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
              "{directory}",
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
        isUpgrade = file.isPresent();
        isReversion =
                reversionArchive.isPresent() || revertMostRecent.isPresent();

        if (argParser.usageOrVersionDisplayed()) {
          System.exit(ReturnCode.PRINT_USAGE.getReturnCode());
        } else if (isUpgrade) {
          if (reversionArchive.isPresent()) {
            System.err.println(ERR_UPGRADE_INCOMPATIBLE_ARGS.get(
                    file.getName(), reversionArchive.getName()));
            System.exit(ReturnCode.
                    APPLICATION_ERROR.getReturnCode());
          } else if (revertMostRecent.isPresent()) {
            System.err.println(ERR_UPGRADE_INCOMPATIBLE_ARGS.get(
                    file.getName(), revertMostRecent.getName()));
            System.exit(ReturnCode.
                    APPLICATION_ERROR.getReturnCode());

          }
        }
      } catch (ArgumentException ae) {
        System.err.println(ae.getMessageObject());
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
