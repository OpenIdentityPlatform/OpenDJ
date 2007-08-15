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
package org.opends.guitools.statuspanel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.Installation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

/**
 * This class is called by the control panel command lines to launch the
 * control panel of the Directory Server.
 *
 */
public class StatusPanelLauncher
{
  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-status-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  static private final Logger LOG =
          Logger.getLogger(StatusPanelLauncher.class.getName());

  /**
   * The main method which is called by the control panel command lines.
   * @param args the arguments passed by the command lines.
   */
  public static void main(String[] args)
  {
    try {
      StatusLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }
    boolean printUsage = false;
    boolean printVersion = false;
    if ((args != null) && (args.length > 4))
    {
      printUsage = true;
    }
    for (int i=0; i<args.length; i++)
    {
      if (args[i].equalsIgnoreCase("-H") ||
          args[i].equalsIgnoreCase("--help") ||
          args[i].equalsIgnoreCase("-?"))
      {
        printUsage = true;
      }
      else
      if (args[i].equalsIgnoreCase("-" + OPTION_SHORT_PRODUCT_VERSION) ||
          args[i].equalsIgnoreCase("--" + OPTION_LONG_PRODUCT_VERSION))
      {
        printVersion = true;
      }
    }
    // We first check if we have to print the version
    if(printVersion)
    {
      try
      {
        DirectoryServer.printVersion(System.out);
      }
      catch (IOException e)
      {
        // TODO Auto-generated catch block
      }
      System.exit(1);
    }
    else
    if (printUsage)
    {
      printUsage(System.out);
      System.exit(1);

    } else
    {
      int exitCode = launchGuiStatusPanel(args);
      if (exitCode != 0)
      {
        String logFileName = null;
        if (StatusLog.getLogFile() != null)
        {
          logFileName = StatusLog.getLogFile().toString();
        }
        if (logFileName != null)
        {
          System.err.println(StaticUtils.wrapText(
                  INFO_STATUS_PANEL_LAUNCHER_GUI_LAUNCH_FAILED_DETAILS.get(
                          logFileName),
                  Utils.getCommandLineMaxLineWidth()));
        }
        else
        {
          System.err.println(StaticUtils.wrapText(
                  INFO_STATUS_PANEL_LAUNCHER_GUI_LAUNCH_FAILED.get(),
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
   * This code also assumes that if the call to SplashWindow.main worked (and
   * the splash screen was displayed) we will never get out of it (we will call
   * a System.exit() when we close the graphical status dialog).
   *
   * @params String[] args the arguments used to call the SplashWindow main
   *         method
   * @return 0 if everything worked fine, or 1 if we could not display properly
   *         the SplashWindow.
   */
  private static int launchGuiStatusPanel(final String[] args)
  {
    final int[] returnValue = { -1 };
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          // Setup MacOSX native menu bar before AWT is loaded.
          Utils.setMacOSXMenuBar(INFO_STATUSPANEL_DIALOG_TITLE.get());
          SplashScreen.main(args);
          returnValue[0] = 0;
        }
        catch (Throwable t)
        {
          if (StatusLog.isInitialized())
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
    System.setErr(new EmptyPrintStream());
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

  private static void printUsage(PrintStream stream)
  {
    ArgumentParser argParser =
      new ArgumentParser(StatusPanelLauncher.class.getName(),
        INFO_STATUS_PANEL_LAUNCHER_USAGE_DESCRIPTION.get(), false);
    BooleanArgument showUsage;
    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_STATUSPANEL_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_STATUSPANEL_FILE_NAME;
    }
    System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    try
    {
      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
        OPTION_LONG_HELP,
        INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);

      String msg = argParser.getUsage();
      stream.println(msg);
    }
    catch (Throwable t)
    {
      System.out.println("ERROR: "+t);
      t.printStackTrace();
    }
  }

  /**
   * This class is used to avoid displaying the error message related to display
   * problems that we might have when trying to display the SplashWindow.
   *
   */
  static class EmptyPrintStream extends PrintStream
  {
    /**
     * Default constructor.
     *
     */
    public EmptyPrintStream()
    {
      super(new ByteArrayOutputStream(), true);
    }

    /**
     * {@inheritDoc}
     */
    public void println(String msg)
    {
    }
  }
}

