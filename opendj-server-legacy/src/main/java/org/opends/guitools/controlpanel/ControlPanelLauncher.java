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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel;

import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.io.PrintStream;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.guitools.controlpanel.util.ControlPanelLog;
import org.opends.messages.AdminToolMessages;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.InitializationException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.DynamicConstants;

import com.forgerock.opendj.cli.ArgumentException;

/**
 * The class that is invoked directly by the control-panel command-line.  This
 * class basically displays a splash screen and then calls the methods in
 * ControlPanel.  It also is in charge of detecting whether there are issues
 * with the
 */
public class ControlPanelLauncher
{
  private static ControlPanelArgumentParser  argParser;

  /** Prefix for log files. */
  public static final String LOG_FILE_PREFIX = "opendj-control-panel-";

  /** Suffix for log files. */
  public static final String LOG_FILE_SUFFIX = ".log";

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Main method invoked by the control-panel script.
   * @param args the arguments.
   */
  public static void main(String[] args)
  {
    try {
      ControlPanelLog.initLogFileHandler(
          File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }

    argParser = new ControlPanelArgumentParser(
        ControlPanelLauncher.class.getName(),
        INFO_CONTROL_PANEL_LAUNCHER_USAGE_DESCRIPTION.get());
    //  Validate user provided data
    try
    {
      argParser.initializeArguments();
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(System.err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      System.exit(ErrorReturnCode.ERROR_PARSING_ARGS.getReturnCode());
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      ControlPanelLog.closeAndDeleteLogFile();
      System.exit(ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode());
    }

    // Checks the version - if upgrade required, the tool is unusable
    if (!argParser.isRemote())
    {
      try
      {
        BuildVersion.checkVersionMismatch();
      }
      catch (InitializationException e)
      {
        System.err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
        System.exit(ErrorReturnCode.ERROR_UNEXPECTED.getReturnCode());
      }
    }

    if (!argParser.usageOrVersionDisplayed())
    {
      int exitCode = launchControlPanel(args);
      if (exitCode != 0)
      {
        String logFileName = null;
        if (ControlPanelLog.getLogFile() != null)
        {
          logFileName = ControlPanelLog.getLogFile().toString();
        }
        if (logFileName != null)
        {
          System.err.println(wrapText(
              ERR_CONTROL_PANEL_LAUNCHER_GUI_LAUNCH_FAILED_DETAILS.get(
                  logFileName),
                  Utils.getCommandLineMaxLineWidth()));
        }
        else
        {
          System.err.println(wrapText(
              ERR_CONTROL_PANEL_LAUNCHER_GUI_LAUNCH_FAILED.get(),
              Utils.getCommandLineMaxLineWidth()));
        }
        System.exit(exitCode);
      }
    }
    ControlPanelLog.closeAndDeleteLogFile();
  }

  /**
   * Launches the graphical status panel. It is launched in a
   * different thread that the main thread because if we have a problem with the
   * graphical system (for instance the DISPLAY environment variable is not
   * correctly set) the native libraries will call exit. However if we launch
   * this from another thread, the thread will just be killed.
   *
   * This code also assumes that if the call to ControlPanelSplashScreen.main
   * worked (and the splash screen was displayed) we will never get out of it
   * (we will call a System.exit() when we close the graphical status dialog).
   *
   * @param  args the arguments used to call the
   *         ControlPanelSplashScreen main method.
   * @return 0 if everything worked fine, or 1 if we could not display properly
   *         the ControlPanelSplashScreen.
   */
  private static int launchControlPanel(final String[] args)
  {
    final int[] returnValue = { -1 };
    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          try
          {
            initLookAndFeel();
          }
          catch (Throwable t)
          {
            logger.warn(LocalizableMessage.raw("Error setting look and feel: "+t, t));
          }

          ControlPanelSplashScreen.main(args);
          returnValue[0] = 0;
        }
        catch (Throwable t)
        {
          if (ControlPanelLog.isInitialized())
          {
            logger.warn(LocalizableMessage.raw("Error launching GUI: "+t));
            StringBuilder buf = new StringBuilder();
            while (t != null)
            {
              for (StackTraceElement aStack : t.getStackTrace()) {
                buf.append(aStack).append("\n");
              }

              t = t.getCause();
              if (t != null)
              {
                buf.append("Root cause:\n");
              }
            }
            logger.warn(LocalizableMessage.raw(buf));
          }
        }
      }
    });
    /*
     * This is done to avoid displaying the stack that might occur if there are
     * problems with the display environment.
     */
    PrintStream printStream = System.err;
    System.setErr(Utils.getEmptyPrintStream());
    t.start();
    try
    {
      t.join();
    }
    catch (InterruptedException ie)
    {
      /* An error occurred, so the return value will be -1. We got nothing to do with this exception. */
    }
    System.setErr(printStream);
    return returnValue[0];
  }

  private static void initLookAndFeel() throws Throwable
  {
//  Setup MacOSX native menu bar before AWT is loaded.
    LocalizableMessage title = Utils.getCustomizedObject("INFO_CONTROL_PANEL_TITLE",
        AdminToolMessages.INFO_CONTROL_PANEL_TITLE.get(
        DynamicConstants.PRODUCT_NAME), LocalizableMessage.class);
    Utils.setMacOSXMenuBar(title);
    UIFactory.initializeLookAndFeel();
  }
}


/** The enumeration containing the different return codes that the command-line can have. */
enum ErrorReturnCode
{
  /** Successful display of the status. */
  SUCCESSFUL(0),
  /** We did no have an error but the status was not displayed (displayed version or usage). */
  SUCCESSFUL_NOP(0),
  /** Unexpected error (potential bug). */
  ERROR_UNEXPECTED(1),
  /** Cannot parse arguments. */
  ERROR_PARSING_ARGS(2),
  /**
   * User cancelled (for instance not accepting the certificate proposed) or
   * could not use the provided connection parameters in interactive mode.
   */
  USER_CANCELLED_OR_DATA_ERROR(3),
  /** This occurs for instance when the authentication provided by the user is not valid. */
  ERROR_READING_CONFIGURATION_WITH_LDAP(4);

  private final int returnCode;
  private ErrorReturnCode(int returnCode)
  {
    this.returnCode = returnCode;
  }

  /**
   * Returns the corresponding return code value.
   * @return the corresponding return code value.
   */
  public int getReturnCode()
  {
    return returnCode;
  }
}

/** The splash screen for the control panel. */
class ControlPanelSplashScreen extends org.opends.quicksetup.SplashScreen
{
  private static final long serialVersionUID = 4472839063380302713L;

  private static ControlPanel controlPanel;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The main method for this class.
   * It can be called from the event thread and outside the event thread.
   * @param args arguments to be passed to the method ControlPanel.initialize
   */
  public static void main(String[] args)
  {
    ControlPanelSplashScreen screen = new ControlPanelSplashScreen();
    screen.display(args);
  }


  /**
   * This methods constructs the ControlPanel object.
   * This method assumes that is being called outside the event thread.
   * @param args arguments to be passed to the method ControlPanel.initialize.
   */
  @Override
  protected void constructApplication(String[] args)
  {
    try
    {
      controlPanel = new ControlPanel();
      controlPanel.initialize(args);
    } catch (Throwable t)
    {
      if (ControlPanelLog.isInitialized())
      {
        logger.error(LocalizableMessage.raw("Error launching GUI: "+t, t));
      }
      InternalError error =
        new InternalError("Failed to invoke initialize method");
      error.initCause(t);
      throw error;
    }
  }

  /**
   * This method displays the StatusPanel dialog.
   * @see org.opends.guitools.controlpanel.ControlPanel#createAndDisplayGUI()
   * This method assumes that is being called outside the event thread.
   */
  @Override
  protected void displayApplication()
  {
    Runnable runnable = new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          logger.info(LocalizableMessage.raw("going to call createAndDisplayGUI."));
          controlPanel.createAndDisplayGUI();
          logger.info(LocalizableMessage.raw("called createAndDisplayGUI."));
        } catch (Throwable t)
        {
          logger.error(LocalizableMessage.raw("Error displaying GUI: "+t, t));
          InternalError error =
            new InternalError("Failed to invoke display method");
          error.initCause(t);
          throw error;
        }
      }
    };
    if (SwingUtilities.isEventDispatchThread())
    {
      runnable.run();
    }
    else
    {
      try
      {
        SwingUtilities.invokeAndWait(runnable);
      }
      catch (Throwable t)
      {
        logger.error(LocalizableMessage.raw("Error calling SwingUtilities.invokeAndWait: "+t,
            t));
        InternalError error =
          new InternalError(
              "Failed to invoke SwingUtilities.invokeAndWait method");
        error.initCause(t);
        throw error;
      }
    }
  }
}


