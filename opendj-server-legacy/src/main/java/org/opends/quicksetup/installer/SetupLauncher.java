/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.util.IncompatibleVersionException;
import org.opends.quicksetup.util.Utils;
import org.opends.server.tools.InstallDS;
import org.opends.server.tools.InstallDSArgumentParser;
import org.opends.server.util.DynamicConstants;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;

/**
 * This class is called by the setup command line to launch the setup
 * of the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based setup much be launched.
 */
public class SetupLauncher extends Launcher {

  private static final String LOG_FILE_PREFIX = "opendj-setup-";

  /**
   * The main method which is called by the setup command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    new SetupLauncher(args).launch();
  }

  private InstallDSArgumentParser argParser;

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public SetupLauncher(String[] args) {
    super(args, LOG_FILE_PREFIX);
    if (System.getProperty(PROPERTY_SCRIPT_NAME) == null)
    {
      System.setProperty(PROPERTY_SCRIPT_NAME, Installation.getSetupFileName());
    }
    initializeParser();
  }

  /** Initialize the contents of the argument parser. */
  protected void initializeParser()
  {
    argParser = new InstallDSArgumentParser(InstallDS.class.getName());
    try
    {
      argParser.initializeArguments();
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.out.println(message);
    }
  }

  @Override
  public void launch() {
    try
    {
      argParser.parseArguments(args);

      if (argParser.isVersionArgumentPresent())
      {
        System.exit(ReturnCode.PRINT_VERSION.getReturnCode());
      }
      // The second condition is required when the user specifies '?'
      else if (argParser.isUsageArgumentPresent() ||
          argParser.usageOrVersionDisplayed())
      {
        System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
      }
      else if (isCli())
      {
        Utils.checkJavaVersion();
        System.exit(InstallDS.mainCLI(args, tempLogFile));
      }
      else
      {
        willLaunchGui();
        // The java version is checked in the launchGui code to be sure
        // that if there is a problem with the java version the message
        // (if possible) is displayed graphically.
        int exitCode = launchGui(args);
        if (exitCode != 0) {
          guiLaunchFailed();
          Utils.checkJavaVersion();
          System.exit(InstallDS.mainCLI(args, tempLogFile));
        }
      }
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(System.err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      System.exit(ReturnCode.USER_DATA_ERROR.getReturnCode());
    }
    catch (IncompatibleVersionException ive)
    {
      System.err.println(ive.getMessageObject());
      System.exit(ReturnCode.JAVA_VERSION_INCOMPATIBLE.getReturnCode());
    }
  }

  @Override
  public ArgumentParser getArgumentParser() {
    return this.argParser;
  }

  @Override
  protected void guiLaunchFailed() {
      System.err.println(
          tempLogFile.isEnabled() ? INFO_SETUP_LAUNCHER_GUI_LAUNCHED_FAILED_DETAILS.get(tempLogFile.getPath())
                                  : INFO_SETUP_LAUNCHER_GUI_LAUNCHED_FAILED.get());
  }

  @Override
  protected void willLaunchGui() {
    System.out.println(INFO_SETUP_LAUNCHER_LAUNCHING_GUI.get());
    System.setProperty("org.opends.quicksetup.Application.class", Installer.class.getName());
  }

  @Override
  protected LocalizableMessage getFrameTitle() {
    return Utils.getCustomizedObject("INFO_FRAME_INSTALL_TITLE",
        INFO_FRAME_INSTALL_TITLE.get(DynamicConstants.PRODUCT_NAME),
        LocalizableMessage.class);
  }

  @Override
  protected CliApplication createCliApplication() {
    return null;
  }

  @Override
  protected boolean isCli() {
    return argParser.isCli();
  }
}
