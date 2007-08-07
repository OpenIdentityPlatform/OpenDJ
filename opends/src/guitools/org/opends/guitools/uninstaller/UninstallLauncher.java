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

import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.File;
import java.util.logging.Logger;

import org.opends.guitools.i18n.ResourceProvider;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;

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
   * The main method which is called by the setup command lines.
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

  private ArgumentParser argParser;

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

    argParser = new ArgumentParser(getClass().getName(),
        getI18n().getMsg("uninstall-launcher-usage-description"), false);
    BooleanArgument cli;
    BooleanArgument silent;
    BooleanArgument showUsage;
    try
    {
      cli = new BooleanArgument("cli", 'c', "cli",
          MSGID_UNINSTALLDS_DESCRIPTION_CLI);
      argParser.addArgument(cli);
      silent = new BooleanArgument("silent", 's', "silent",
          MSGID_UNINSTALLDS_DESCRIPTION_SILENT);
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

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFilePath) {
    if (logFilePath != null)
    {
      System.err.println(getMsg(
          "uninstall-launcher-gui-launched-failed-details", logFilePath));
    }
    else
    {
      System.err.println(getMsg("uninstall-launcher-gui-launched-failed"));
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
    System.out.println(getMsg("uninstall-launcher-launching-gui"));
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
  protected String getFrameTitle() {
    return getI18n().getMsg("frame-uninstall-title");
  }

  /**
   * {@inheritDoc}
   */
  protected ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
