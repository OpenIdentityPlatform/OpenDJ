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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.uninstaller;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.ERR_ERROR_PARSING_ARGS;
import static com.forgerock.opendj.util.OperatingSystem.isWindows;
import static com.forgerock.opendj.cli.Utils.wrapText;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.messages.ToolMessages;

import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.ServerConstants;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;

/**
 * This class is called by the uninstall command lines to launch the uninstall
 * of the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based uninstall much be launched.
 */
public class UninstallLauncher extends Launcher {

  /** Prefix for log files. */
  public static final String LOG_FILE_PREFIX = "opendj-uninstall-";

  /**
   * The main method which is called by the uninstall command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    new UninstallLauncher(args).launch();
  }

  private UninstallerArgumentParser argParser;

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public UninstallLauncher(String[] args) {
    super(args, LOG_FILE_PREFIX);

    String scriptName;
    if (isWindows()) {
      scriptName = Installation.WINDOWS_UNINSTALL_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_UNINSTALL_FILE_NAME;
    }
    if (System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME) == null)
    {
      System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    }

    initializeParser();
  }

  @Override
  public void launch() {
    //  Validate user provided data
    try
    {
      argParser.parseArguments(args);
      if (argParser.isVersionArgumentPresent())
      {
        System.exit(ReturnCode.PRINT_VERSION.getReturnCode());
      }
      else if (argParser.usageOrVersionDisplayed())
      {
        // If there was no problem parsing arguments, this means that the user
        // asked to display the usage.
        System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
      }
      else
      {
        super.launch();
      }
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(System.err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      System.exit(ReturnCode.USER_DATA_ERROR.getReturnCode());
    }
  }

  /** Initialize the contents of the argument parser. */
  protected void initializeParser()
  {
    argParser = new UninstallerArgumentParser(getClass().getName(),
        INFO_UNINSTALL_LAUNCHER_USAGE_DESCRIPTION.get(), false);
    try
    {
      argParser.initializeGlobalArguments(System.out);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message =
        ToolMessages.ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(wrapText(message,
          Utils.getCommandLineMaxLineWidth()));
    }
  }

  @Override
  protected void guiLaunchFailed() {
      System.err.println(
          tempLogFile.isEnabled() ? ERR_UNINSTALL_LAUNCHER_GUI_LAUNCHED_FAILED_DETAILS.get(tempLogFile.getPath())
                                  : ERR_UNINSTALL_LAUNCHER_GUI_LAUNCHED_FAILED.get());
  }

  @Override
  public ArgumentParser getArgumentParser() {
    return this.argParser;
  }

  @Override
  protected void willLaunchGui() {
    System.out.println(INFO_UNINSTALL_LAUNCHER_LAUNCHING_GUI.get());
    System.setProperty("org.opends.quicksetup.Application.class",
            org.opends.guitools.uninstaller.Uninstaller.class.getName());
  }

  @Override
  protected CliApplication createCliApplication() {
    return new Uninstaller();
  }

  @Override
  protected LocalizableMessage getFrameTitle() {
    return Utils.getCustomizedObject("INFO_FRAME_UNINSTALL_TITLE",
        INFO_FRAME_UNINSTALL_TITLE.get(DynamicConstants.PRODUCT_NAME),
        LocalizableMessage.class);
  }

  @Override
  protected boolean shouldPrintUsage() {
    return argParser.isUsageArgumentPresent() &&
    !argParser.usageOrVersionDisplayed();
  }

  @Override
  protected boolean isQuiet() {
    return argParser.isQuiet();
  }

  /**
   * Indicates whether the launcher should print a usage statement
   * based on the content of the arguments passed into the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean isNoPrompt() {
    return !argParser.isInteractive();
  }

  @Override
  protected boolean shouldPrintVersion() {
    return argParser.isVersionArgumentPresent() &&
    !argParser.usageOrVersionDisplayed();
  }

  @Override
  protected boolean isCli() {
    return argParser.isCli();
  }
}
