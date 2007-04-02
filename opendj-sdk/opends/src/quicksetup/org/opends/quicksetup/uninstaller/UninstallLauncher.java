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

package org.opends.quicksetup.uninstaller;

import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.util.Utils;

/**
 * This class is called by the uninstall command lines to launch the uninstall
 * of the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based uninstall much be launched.
 */
public class UninstallLauncher extends Launcher {

  /**
   * The main method which is called by the setup command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    new UninstallLauncher(args).launch();
  }

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public UninstallLauncher(String[] args) {
    super(args);
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed() {
    System.err.println(getMsg("uninstall-launcher-gui-launched-failed"));
  }

  /**
   * {@inheritDoc}
   */
  protected void willLaunchGui() {
    System.out.println(getMsg("uninstall-launcher-launching-gui"));
    System.setProperty("org.opends.quicksetup.Application.class",
            "org.opends.quicksetup.uninstaller.Uninstaller");
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
    return getMsg("frame-uninstall-title");
  }

  /**
   * {@inheritDoc}
   */
  protected void printUsage() {
    String arg;
    if (Utils.isWindows()) {
      arg = Utils.getWindowsUninstallFileName();
    } else {
      arg = Utils.getUnixUninstallFileName();
    }
    String msg = getMsg("uninstall-launcher-usage");
    /*
     * This is required because the usage message contains '{' characters that
     * mess up the MessageFormat.format method.
     */
    msg = msg.replace("{0}", arg);
    printUsage(msg);
  }

}
