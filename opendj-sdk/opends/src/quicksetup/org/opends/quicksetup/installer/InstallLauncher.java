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
package org.opends.quicksetup.installer;

import java.util.ArrayList;

import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;

/**
 * This class is called by the setup command lines to launch the installation of
 * the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based setup much be launched.
 */
public class InstallLauncher extends Launcher {

  /**
   * The main method which is called by the setup command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    System.exit(new InstallLauncher(args).launch());
  }

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public InstallLauncher(String[] args) {
    super(args);
  }

  /**
   * {@inheritDoc}
   */
  protected boolean shouldPrintUsage() {
    boolean displayUsage = false;
    if ((args != null) && (args.length > 0)) {
      if (!isCli()) {
        if (args.length > 0) {
          if (args.length == 2) {
            /*
             * Just ignore the -P argument that is passed by the setup command
             * line.
             */
            if (!args[0].equals("-P")) {
              displayUsage = true;
            }
          } else {
            displayUsage = true;
          }
        }
      }
    }
    return displayUsage;
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed() {
    System.err.println(getMsg("setup-launcher-gui-launched-failed"));
  }

  /**
   * {@inheritDoc}
   */
  protected void willLaunchGui() {
    System.out.println(getMsg("setup-launcher-launching-gui"));
    System.setProperty("org.opends.quicksetup.Application.class",
            "org.opends.quicksetup.installer.offline.OfflineInstaller");
  }

  /**
   * {@inheritDoc}
   */
  protected String getFrameTitle() {
    return getMsg("frame-install-title");
  }

  /**
   * {@inheritDoc}
   */
  public void printUsage() {
    String arg;
    if (Utils.isWindows()) {
      arg = Installation.WINDOWS_SETUP_FILE_NAME;
    } else {
      arg = Installation.UNIX_SETUP_FILE_NAME;
    }
    /*
     * This is required because the usage message contains '{' characters that
     * mess up the MessageFormat.format method.
     */
    String msg;

    if (Utils.isWindows()) {
      msg = getMsg("setup-launcher-usage-windows");
    } else {
      msg = getMsg("setup-launcher-usage-unix");
    }
    msg = msg.replace("{0}", arg);
    printUsage(msg);
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    // Setup currently has no implemented CliApplication
    // but rather relies on InstallDS from the server
    // package.  Note that launchCli is overloaded
    // to run this application instead of the default
    // behavior in Launcher
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected int launchCli(String[] args, CliApplication cliApp) {
    System.setProperty("org.opends.quicksetup.cli", "true");

    if (Utils.isWindows()) {
      System.setProperty("org.opends.server.scriptName",
              Installation.WINDOWS_SETUP_FILE_NAME);
    } else {
      System.setProperty("org.opends.server.scriptName",
              Installation.UNIX_SETUP_FILE_NAME);
    }
    ArrayList<String> newArgList = new ArrayList<String>();
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        if (!args[i].equalsIgnoreCase("--cli")) {
          newArgList.add(args[i]);
        }
      }
    }
    newArgList.add("--configClass");
    newArgList.add("org.opends.server.extensions.ConfigFileHandler");
    newArgList.add("--configFile");
    Installation installation =
            new Installation(Utils.getInstallPathFromClasspath());
    newArgList.add(Utils.getPath(installation.getCurrentConfigurationFile()));

    String[] newArgs = new String[newArgList.size()];
    newArgList.toArray(newArgs);

    return org.opends.server.tools.InstallDS.installMain(newArgs);
  }

}
