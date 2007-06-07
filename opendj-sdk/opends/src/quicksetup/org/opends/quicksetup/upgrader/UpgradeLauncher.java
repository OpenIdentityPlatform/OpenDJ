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

import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;

import java.util.logging.Logger;
import java.io.File;

/**
 * This class is called by the upgrade and upgrade.bat
 * command line utilities to launch an upgrade process.
 */
public class UpgradeLauncher extends Launcher {

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-upgrade-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  static private final Logger LOG =
          Logger.getLogger(UpgradeLauncher.class.getName());

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
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println(
              ResourceProvider.getInstance().getMsg("error-initializing-log"));
      t.printStackTrace();
    }
    new UpgradeLauncher(args).launch();
  }

  /**
   * {@inheritDoc}
   */
  protected String getFrameTitle() {
    return getMsg("frame-upgrade-title");
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
  protected void printUsage() {
    String arg;
    if (Utils.isWindows()) {
      arg = Installation.WINDOWS_UPGRADE_FILE_NAME;
    } else {
      arg = Installation.UNIX_UPGRADE_FILE_NAME;
    }
    String msg = getMsg("upgrade-launcher-usage");
    /*
     * This is required because the usage message contains '{' characters that
     * mess up the MessageFormat.format method.
     */
    msg = msg.replace("{0}", arg);
    printUsage(msg);
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

  private UpgradeLauncher(String[] args) {
    super(args);
  }

}
