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

import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import org.opends.quicksetup.CliApplicationHelper;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;

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
      System.err.println(
              ResourceProvider.getInstance().getMsg("error-initializing-log"));
      t.printStackTrace();
    }
    new UpgradeLauncher(args).launch();
  }

  private ArgumentParser argParser;

  /**
   * {@inheritDoc}
   */
  protected String getFrameTitle() {
    return getI18n().getMsg("frame-upgrade-title");
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
    System.out.println(getMsg("upgrade-launcher-launching-gui"));
    System.setProperty("org.opends.quicksetup.Application.class",
            "org.opends.quicksetup.upgrader.Upgrader");
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFilePath) {
    if (logFilePath != null)
    {
      System.err.println(getMsg("upgrade-launcher-gui-launched-failed-details",
          logFilePath));
    }
    else
    {
      System.err.println(getMsg("upgrade-launcher-gui-launched-failed"));
    }
  }

  /**
   * {@inheritDoc}
   */
  public ArgumentParser getArgumentParser() {
    return argParser;
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
        getI18n().getMsg("upgrade-launcher-usage-description"), false);
    BooleanArgument showUsage;
    FileBasedArgument file;
    BooleanArgument silent;
    BooleanArgument interactive;
    try
    {
      file = new FileBasedArgument(
              "file",
              FILE_OPTION_SHORT,
              FILE_OPTION_LONG,
              false, false,
              "{file}",
              null, null, MSGID_UPGRADE_DESCRIPTION_FILE);
      argParser.addArgument(file);
      interactive = new BooleanArgument(
          CliApplicationHelper.INTERACTIVE_OPTION_LONG,
          CliApplicationHelper.INTERACTIVE_OPTION_SHORT,
          CliApplicationHelper.INTERACTIVE_OPTION_LONG,
          MSGID_UPGRADE_DESCRIPTION_INTERACTIVE);
      argParser.addArgument(interactive);
      silent = new BooleanArgument(
          CliApplicationHelper.SILENT_OPTION_LONG,
          CliApplicationHelper.SILENT_OPTION_SHORT,
          CliApplicationHelper.SILENT_OPTION_LONG,
          MSGID_UPGRADE_DESCRIPTION_SILENT);
      argParser.addArgument(silent);
      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
        OPTION_LONG_HELP,
        MSGID_DESCRIPTION_USAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (Throwable t)
    {
      System.out.println("ERROR: "+t);
      t.printStackTrace();
    }

  }

}
