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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.File;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.opends.guitools.controlpanel.util.ControlPanelLog;
import org.opends.messages.AdminToolMessages;
import org.opends.messages.Message;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;

/**
 * The class that is invoked directly by the control-panel command-line.  This
 * class basically displays a splash screen and then calls the methods in
 * ControlPanel.  It also is in charge of detecting whether there are issues
 * with the
 *
 */
public class ControlPanelLauncher
{
  static private ArgumentParser argParser;

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-control-panel-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  static private final Logger LOG =
    Logger.getLogger(ControlPanelLauncher.class.getName());

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

    argParser = new ArgumentParser(ControlPanelLauncher.class.getName(),
        INFO_CONTROL_PANEL_LAUNCHER_USAGE_DESCRIPTION.get(), false);
    BooleanArgument showUsage;
    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_CONTROLPANEL_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_CONTROLPANEL_FILE_NAME;
    }
    if (System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME) == null)
    {
      System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    }
    try
    {
      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
          OPTION_LONG_HELP,
          INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (Throwable t)
    {
      System.err.println("ERROR: "+t);
      t.printStackTrace();
    }

//  Validate user provided data
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      System.err.println(message);
      System.out.println(Message.raw(argParser.getUsage()));

      System.exit(ErrorReturnCode.ERROR_PARSING_ARGS.getReturnCode());
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
          System.err.println(StaticUtils.wrapText(
              ERR_CONTROL_PANEL_LAUNCHER_GUI_LAUNCH_FAILED_DETAILS.get(
                  logFileName),
                  Utils.getCommandLineMaxLineWidth()));
        }
        else
        {
          System.err.println(StaticUtils.wrapText(
              ERR_CONTROL_PANEL_LAUNCHER_GUI_LAUNCH_FAILED.get(),
              Utils.getCommandLineMaxLineWidth()));
        }
        System.exit(exitCode);
      }
    }
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
   * @params String[] args the arguments used to call the
   *         ControlPanelSplashScreen main method.
   * @return 0 if everything worked fine, or 1 if we could not display properly
   *         the ControlPanelSplashScreen.
   */
  private static int launchControlPanel(final String[] args)
  {
    final int[] returnValue = { -1 };
    Thread t = new Thread(new Runnable()
    {
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
            LOG.log(Level.WARNING, "Error setting look and feel: "+t, t);
          }

          ControlPanelSplashScreen.main(args);
          returnValue[0] = 0;
        }
        catch (Throwable t)
        {
          if (ControlPanelLog.isInitialized())
          {
            LOG.log(Level.WARNING, "Error launching GUI: "+t);
            StringBuilder buf = new StringBuilder();
            while (t != null)
            {
              StackTraceElement[] stack = t.getStackTrace();
              for (int i = 0; i < stack.length; i++)
              {
                buf.append(stack[i].toString()+"\n");
              }

              t = t.getCause();
              if (t != null)
              {
                buf.append("Root cause:\n");
              }
            }
            LOG.log(Level.WARNING, buf.toString());
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
      /* An error occurred, so the return value will be -1.  We got nothing to
      do with this exception. */
    }
    System.setErr(printStream);
    return returnValue[0];
  }

  private static void initLookAndFeel() throws Throwable
  {
//  Setup MacOSX native menu bar before AWT is loaded.
    Utils.setMacOSXMenuBar(
        AdminToolMessages.INFO_CONTROL_PANEL_TITLE.get());

    if (SwingUtilities.isEventDispatchThread())
    {
      UIManager.setLookAndFeel(
          UIManager.getSystemLookAndFeelClassName());
    }
    else
    {
      final Throwable[] ts = {null};
      SwingUtilities.invokeAndWait(new Runnable()
      {
        public void run()
        {
          try
          {
            UIManager.setLookAndFeel(
                UIManager.getSystemLookAndFeelClassName());
          }
          catch (Throwable t)
          {
            ts[0] = t;
          }
        }
      });
      if (ts[0] != null)
      {
        throw ts[0];
      }
    }
  }
}


/**
 * The enumeration containing the different return codes that the command-line
 * can have.
 *
 */
enum ErrorReturnCode
{
  /**
   * Successful display of the status.
   */
  SUCCESSFUL(0),
  /**
   * We did no have an error but the status was not displayed (displayed
   * version or usage).
   */
  SUCCESSFUL_NOP(0),
  /**
   * Unexpected error (potential bug).
   */
  ERROR_UNEXPECTED(1),
  /**
   * Cannot parse arguments.
   */
  ERROR_PARSING_ARGS(2),
  /**
   * User cancelled (for instance not accepting the certificate proposed) or
   * could not use the provided connection parameters in interactive mode.
   */
  USER_CANCELLED_OR_DATA_ERROR(3),
  /**
   * This occurs for instance when the authentication provided by the user is
   * not valid.
   */
  ERROR_READING_CONFIGURATION_WITH_LDAP(4);

  private int returnCode;
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
};

/**
 * The splash screen for the control panel.
 *
 */
class ControlPanelSplashScreen extends org.opends.quicksetup.SplashScreen
{
  private static final long serialVersionUID = 4472839063380302713L;

  private static ControlPanel controlPanel;

  private static final Logger LOG =
    Logger.getLogger(ControlPanelLauncher.class.getName());

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
        LOG.log(Level.SEVERE, "Error launching GUI: "+t, t);
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
  protected void displayApplication()
  {
    Runnable runnable = new Runnable()
    {
      public void run()
      {
        try
        {
          LOG.log(Level.INFO, "going to call createAndDisplayGUI.");
          controlPanel.createAndDisplayGUI();
          LOG.log(Level.INFO, "called createAndDisplayGUI.");
        } catch (Throwable t)
        {
          LOG.log(Level.SEVERE, "Error displaying GUI: "+t, t);
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
        LOG.log(Level.SEVERE, "Error calling SwingUtilities.invokeAndWait: "+t,
            t);
        InternalError error =
          new InternalError(
              "Failed to invoke SwingUtilities.invokeAndWait method");
        error.initCause(t);
        throw error;
      }
    }
  }
}


