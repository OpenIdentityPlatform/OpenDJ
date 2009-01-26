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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools.configurator;

import java.io.File;
import org.opends.quicksetup.ReturnCode;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opends.messages.Message;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.args.BooleanArgument;
import org.opends.quicksetup.QuickSetupLog;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is called by the unconfigure command line to move the default
 * Directory Server instance.
 */
public class Unconfigurator extends Launcher {

  static private final Logger LOG = Logger.getLogger(
    Unconfigurator.class.getName());

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-unconfigure-";

  private String installRootFromSystem;

  private ArgumentParser argParser;
  private BooleanArgument showUsage;
  private BooleanArgument checkOptions;

  /**
   * The main method which is called by the unconfigure command lines.
   *
   * @param args the arguments passed by the command line.
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

    try {
      Unconfigurator unconfigurator;

      unconfigurator = new Unconfigurator(args);
      unconfigurator.parseArgs(args);
      unconfigurator.unconfigure();

    } catch (ApplicationException ae) {
      LOG.log(Level.SEVERE, "Error during unconfig: " + ae.getMessageObject());
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }
  }

  private void parseArgs(String[] args) {

    try {
      argParser.parseArguments(args);

      if (argParser.usageOrVersionDisplayed()) {
        System.exit(ReturnCode.PRINT_USAGE.getReturnCode());
      }
      if (checkOptions.isPresent()) {
        System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
      }

   } catch (ArgumentException ae) {
      System.err.println(ae.getMessageObject());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }

  }

  private void unconfigure() throws ApplicationException {
      /* Delete instance.loc */
      File instanceLoc = new File(Installation.INSTANCE_LOCATION_PATH);
      boolean res = instanceLoc.delete();
      if (!res) {
        System.err.println("Unable to delete: " +
                Installation.INSTANCE_LOCATION_PATH);
        System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
      }
  }

  private Unconfigurator(String[] args) {
    super(args);

    String scriptName = "unconfigure";

    if (Utils.isWindows()) {
      System.err.println("Not supported platform: Windows");
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    } else {
      scriptName = Installation.UNIX_CONFIGURE_FILE_NAME;
    }
    if (System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME) == null) {
      System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    }

    installRootFromSystem = System.getProperty("INSTALL_ROOT");

    if (installRootFromSystem == null) {
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }

   argParser = new ArgumentParser(getClass().getName(),
                 INFO_UNCONFIGURE_USAGE_DESCRIPTION.get(), false);


    try {
      checkOptions = new BooleanArgument("checkOptions", null,
                              "checkOptions",
                              INFO_DESCRIPTION_CHECK_OPTIONS.get());
      checkOptions.setHidden(true);
      argParser.addArgument(checkOptions);

      showUsage = new BooleanArgument(
        "showusage",
        OPTION_SHORT_HELP,
        OPTION_LONG_HELP,
        INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);

    } catch (ArgumentException ae) {
      System.err.println(ae.getMessageObject());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void willLaunchGui() {
    return;
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFilePath) {
    return;
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
  protected Message getFrameTitle() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public ArgumentParser getArgumentParser() {
    return argParser;
  }
}

