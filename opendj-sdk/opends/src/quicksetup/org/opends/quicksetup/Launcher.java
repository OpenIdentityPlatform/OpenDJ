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

package org.opends.quicksetup;

import org.opends.messages.Message;
import org.opends.server.util.args.ArgumentParser;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.DynamicConstants.PRINTABLE_VERSION_STRING;

import org.opends.quicksetup.util.Utils;

import java.io.PrintStream;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for providing initial evaluation of command line arguments
 * and determining whether to launch a CLI, GUI, or print a usage statement.
 */
public abstract class Launcher {

  static private final Logger LOG = Logger.getLogger(Launcher.class.getName());

  /** Arguments with which this launcher was invoked. */
  protected String[] args;

  /**
   * Creates a Launcher.
   * @param args String[] of argument passes from the command line
   */
  public Launcher(String[] args) {
    if (args == null) {
      throw new IllegalArgumentException("args cannot be null");
    }
    this.args = args;

  }

  /**
   * Gets the arguments with which this launcher was invoked.
   * @return String[] args from the CLI invocation
   */
  public String[] getArguments() {
    return this.args;
  }

  /**
   * Gets an argument parser appropriate for this CLI launcher.
   *
   * @return ArgumentParser for parsing args
   */
  public abstract ArgumentParser getArgumentParser();

  /**
   * Indicates whether or not the launcher should print a usage
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean shouldPrintUsage() {
    boolean printUsage = false;
    if ((args != null) && (args.length > 0)) {
      for (String arg : args) {
        if (arg.equals("-?") ||
          arg.equalsIgnoreCase("-H") ||
          arg.equalsIgnoreCase("--help")) {
          printUsage = true;
        }
      }
    }
    return printUsage;
  }

  /**
   * Indicates whether or not the launcher should print a usage
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean isQuiet() {
    boolean printUsage = false;
    if ((args != null) && (args.length > 0)) {
      for (String arg : args) {
        if (arg.equals("-?") ||
          arg.equalsIgnoreCase("-Q") ||
          arg.equalsIgnoreCase("--quiet")) {
          printUsage = true;
        }
      }
    }
    return printUsage;
  }

  /**
   * Indicates whether or not the launcher should print a usage
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates usage should be printed
   */
  protected boolean isNoPrompt() {
    boolean printUsage = false;
    if ((args != null) && (args.length > 0)) {
      for (String arg : args) {
        if (arg.equals("-?") ||
          arg.equalsIgnoreCase("-n") ||
          arg.equalsIgnoreCase("--no-prompt")) {
          printUsage = true;
        }
      }
    }
    return printUsage;
  }

  /**
   * Indicates whether or not the launcher should print a version
   * statement based on the content of the arguments passed into
   * the constructor.
   * @return boolean where true indicates version should be printed
   */
  protected boolean shouldPrintVersion() {
    boolean printVersion = false;
    if ((args != null) && (args.length > 0))
    {
      for (String arg : args)
      {
        if (arg.equalsIgnoreCase("-V") || arg.equalsIgnoreCase("--version"))
        {
          printVersion = true;
        }
      }
    }
    return printVersion;
  }

  /**
   * Indicates whether the launcher will launch a command line versus
   * a graphical application based on the contents of the arguments
   * passed into the constructor.
   *
   * @return boolean where true indicates that a CLI application
   *         should be launched
   */
  protected boolean isCli() {
    boolean isCli = false;
    for (String arg : args) {
      if (arg.equalsIgnoreCase("--"+OPTION_LONG_CLI) ||
          arg.equalsIgnoreCase("-"+OPTION_SHORT_CLI)) {
        isCli = true;
        break;
      }
    }
    return isCli;
  }

  /**
   * Prints a usage message to the terminal.
   * @param i18nMsg localized user message that will be printed to the terminal.
   * @param toStdErr whether the message must be printed to the standard error
   * or the standard output.
   */
  protected void printUsage(String i18nMsg, boolean toStdErr) {
    if (toStdErr)
    {
      System.err.println(i18nMsg);
    }
    else
    {
      System.out.println(i18nMsg);
    }
  }

  /**
   * Launches the graphical uninstall. The graphical uninstall is launched in a
   * different thread that the main thread because if we have a problem with the
   * graphical system (for instance the DISPLAY environment variable is not
   * correctly set) the native libraries will call exit. However if we launch
   * this from another thread, the thread will just be killed.
   *
   * This code also assumes that if the call to SplashWindow.main worked (and
   * the splash screen was displayed) we will never get out of it (we will call
   * a System.exit() when we close the graphical uninstall dialog).
   *
   * @param args String[] the arguments used to call the SplashWindow main
   *         method
   * @return 0 if everything worked fine, or 1 if we could not display properly
   *         the SplashWindow.
   */
  protected int launchGui(final String[] args)
  {
    final int[] returnValue =
      { -1 };
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          // Setup MacOSX native menu bar before AWT is loaded.
          Utils.setMacOSXMenuBar(getFrameTitle());
          SplashScreen.main(args);
          returnValue[0] = 0;
        }
        catch (Throwable t)
        {
          if (QuickSetupLog.isInitialized())
          {
            LOG.log(Level.WARNING, "Error launching GUI: "+t);
            StringBuilder buf = new StringBuilder();
            while (t != null)
            {
              StackTraceElement[] stack = t.getStackTrace();
              for (StackTraceElement aStack : stack) {
                buf.append(aStack.toString()).append("\n");
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

  /**
   * Gets the frame title of the GUI application that will be used
   * in some operating systems.
   * @return internationalized String representing the frame title
   */
  abstract protected Message getFrameTitle();

  /**
   * Launches the command line based uninstall.
   *
   * @param cliApp the CLI application to launch
   * @return 0 if everything worked fine, and an error code if something wrong
   *         occurred.
   */
  protected int launchCli(CliApplication cliApp)
  {
    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");
    QuickSetupCli cli = new QuickSetupCli(cliApp, this);
    ReturnCode returnValue = cli.run();
    if (returnValue.equals(ReturnCode.USER_DATA_ERROR))
    {
      printUsage(true);
      System.exit(ReturnCode.USER_DATA_ERROR.getReturnCode());
    }
    return returnValue.getReturnCode();
  }

  /**
   * Prints the version statement to standard output terminal.
   */
  protected void printVersion()
  {
    System.out.print(PRINTABLE_VERSION_STRING);
  }

  /**
   * Prints a usage statement to terminal and exits with an error
   * code.
   * @param toStdErr whether the message must be printed to the standard error
   * or the standard output.
   */
  protected void printUsage(boolean toStdErr) {
    try
    {
      ArgumentParser argParser = getArgumentParser();
      if (argParser != null) {
        String msg = argParser.getUsage();
        printUsage(msg, toStdErr);
      }
    }
    catch (Throwable t)
    {
      System.out.println("ERROR: "+t);
      t.printStackTrace();
    }
  }

  /**
   * Creates a CLI application that will be run if the
   * launcher needs to launch a CLI application.
   * @return CliApplication that will be run
   */
  protected abstract CliApplication createCliApplication();

  /**
   * Called before the launcher launches the GUI.  Here
   * subclasses can do any application specific things
   * like set system properties of print status messages
   * that need to be done before the GUI launches.
   */
  protected abstract void willLaunchGui();

  /**
   * Called if launching of the GUI failed.  Here
   * subclasses can so application specific things
   * like print a message.
   * @param logFileName the log file containing more information about why
   * the launch failed.
   */
  protected abstract void guiLaunchFailed(String logFileName);

  /**
   * The main method which is called by the command lines.
   */
  public void launch() {
    if (shouldPrintVersion()) {
      ArgumentParser parser = getArgumentParser();
      if (parser == null || !parser.usageOrVersionDisplayed()) {
        printVersion();
      }
      System.exit(ReturnCode.PRINT_VERSION.getReturnCode());
    }
    else if (shouldPrintUsage()) {
      ArgumentParser parser = getArgumentParser();
      if (parser == null || !parser.usageOrVersionDisplayed()) {
        printUsage(false);
      }
      System.exit(ReturnCode.SUCCESSFUL.getReturnCode());
    } else if (isCli()) {
      CliApplication cliApp = createCliApplication();
      int exitCode = launchCli(cliApp);
      preExit(cliApp);
      System.exit(exitCode);
    } else {
      willLaunchGui();
      int exitCode = launchGui(args);
      if (exitCode != 0) {
        File logFile = QuickSetupLog.getLogFile();
        if (logFile != null)
        {
          guiLaunchFailed(logFile.toString());
        }
        else
        {
          guiLaunchFailed(null);
        }
        CliApplication cliApp = createCliApplication();
        exitCode = launchCli(cliApp);
        if (exitCode != 0) {
          preExit(cliApp);
          System.exit(exitCode);
        }
      }
    }
  }

  private void preExit(CliApplication cliApp) {
    if (cliApp != null) {
      UserData ud = cliApp.getUserData();
      if (ud != null && !ud.isQuiet()) {

        // Add an extra space systematically
        System.out.println();

        File logFile = QuickSetupLog.getLogFile();
        if (logFile != null) {
          System.out.println(INFO_GENERAL_SEE_FOR_DETAILS.get(
                  QuickSetupLog.getLogFile().getPath()));
        }
      }
    }
  }
}
