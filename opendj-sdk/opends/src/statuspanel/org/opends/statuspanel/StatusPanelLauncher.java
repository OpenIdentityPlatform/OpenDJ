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
package org.opends.statuspanel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.Installation;
import org.opends.server.core.DirectoryServer;
import org.opends.statuspanel.i18n.ResourceProvider;
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
    // We first check if we have to pribt the version
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
          System.err.println(getMsg(
              "status-panel-launcher-gui-launch-failed-details", logFileName));
        }
        else
        {
          System.err.println(getMsg("status-panel-launcher-gui-launch-failed"));
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
          Utils.setMacOSXMenuBar(getMsg("statuspanel-dialog-title"));
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
    String arg;
    if (Utils.isWindows())
    {
      arg = Installation.WINDOWS_STATUSPANEL_FILE_NAME;
    } else
    {
      arg = Installation.UNIX_STATUSPANEL_FILE_NAME;
    }
    /*
     * This is required because the usage message contains '{' characters that
     * mess up the MessageFormat.format method.
     */
    String msg = getMsg("status-panel-launcher-usage");
    msg = msg.replace("{0}", arg);
    stream.println(msg);
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private static String getMsg(String key)
  {
    return org.opends.server.util.StaticUtils.wrapText(getI18n().getMsg(key),
        Utils.getCommandLineMaxLineWidth());
  }

  /**
   * Creates an internationaized message based on the input key and
   * properly formatted for the terminal.
   * @param key for the message in the bundle
   * @param args String... arguments for the message
   * @return String message properly formatted for the terminal
   */
  private static String getMsg(String key, String... args)
  {
    return org.opends.server.util.StaticUtils.wrapText(
        getI18n().getMsg(key, args),
        Utils.getCommandLineMaxLineWidth());
  }

  private static ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
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

