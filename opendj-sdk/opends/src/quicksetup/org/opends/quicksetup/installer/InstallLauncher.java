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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.quicksetup.installer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import org.opends.quicksetup.SplashScreen;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;

/**
 * This class is called by the setup command lines to launch the installation of
 * the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based setup much be launched.
 *
 */
public class InstallLauncher
{
  private static String COMMAND_NAME_WINDOWS = "setup.bat";

  private static String COMMAND_NAME_UNIX = "setup";

  /**
   * The main method which is called by the setup command lines.
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args)
  {
    boolean displayUsage = false;
    boolean useCli = false;
    if ((args != null) && (args.length > 0))
    {
      for (int i = 0; i < args.length; i++)
      {
        if (args[i].equals("--cli"))
        {
          useCli = true;
        }
      }

      if (!useCli)
      {
        if (args.length > 0)
        {
          if (args.length == 2)
          {
            /*
             * Just ignore the -P argument that is passed by the setup command
             * line.
             */
            if (!args[0].equals("-P"))
            {
              displayUsage = true;
            }
          } else
          {
            displayUsage = true;
          }
        }
      }
    }
    if (displayUsage)
    {
      String arg;
      if (Utils.isWindows())
      {
        arg = COMMAND_NAME_WINDOWS;
      } else
      {
        arg = COMMAND_NAME_UNIX;
      }
      /*
       * This is required because the usage message contains '{' characters that
       * mess up the MessageFormat.format method.
       */
      String msg = getMsg("setup-launcher-usage");
      msg = msg.replace("{0}", arg);
      System.err.println(msg);
      System.exit(1);
    } else if (useCli)
    {
      int exitCode = launchCliSetup(args);
      if (exitCode != 0)
      {
        System.exit(exitCode);
      }
    } else
    {
      int exitCode = launchGuiSetup(args);
      if (exitCode != 0)
      {
        System.err.println(getMsg("setup-launcher-gui-launched-failed"));
        exitCode = launchCliSetup(args);
        if (exitCode != 0)
        {
          System.exit(exitCode);
        }
      }
    }
  }

  /**
   * Launches the command line based setup.
   * @param args the arguments passed
   * @return 0 if everything worked fine, and an error code if something wrong
   * occurred (as specified in org.opends.server.tools.InstallDS).
   * @see org.opends.server.tools.InstallDS
   */
  private static int launchCliSetup(String[] args)
  {
    System.setProperty("org.opends.quicksetup.cli", "true");

    if (Utils.isWindows())
    {
      System.setProperty("org.opends.server.scriptName",
          COMMAND_NAME_WINDOWS);
    } else
    {
      System.setProperty("org.opends.server.scriptName",
          COMMAND_NAME_UNIX);
    }
    ArrayList<String> newArgList = new ArrayList<String>();
    if (args != null)
    {
      for (int i = 0; i < args.length; i++)
      {
        if (!args[i].equalsIgnoreCase("--cli"))
        {
          newArgList.add(args[i]);
        }
      }
    }
    newArgList.add("--configClass");
    newArgList.add("org.opends.server.extensions.ConfigFileHandler");
    newArgList.add("--configFile");
    newArgList.add(Utils.getConfigFileFromClasspath());

    String[] newArgs = new String[newArgList.size()];
    newArgList.toArray(newArgs);

    return org.opends.server.tools.InstallDS.installMain(newArgs);
  }

  /**
   * Launches the graphical setup. The graphical setup is launched in a
   * different thread that the main thread because if we have a problem with the
   * graphical system (for instance the DISPLAY environment variable is not
   * correctly set) the native libraries will call exit. However if we launch
   * this from another thread, the thread will just be killed.
   *
   * This code also assumes that if the call to SplashWindow.main worked (and
   * the splash screen was displayed) we will never get out of it (we will call
   * a System.exit() when we close the graphical setup dialog).
   *
   * @params String[] args the arguments used to call the SplashWindow main
   *         method
   * @return 0 if everything worked fine, or 1 if we could not display properly
   *         the SplashWindow.
   */
  private static int launchGuiSetup(final String[] args)
  {
    System.out.println(getMsg("setup-launcher-launching-gui"));
    final int[] returnValue =
      { -1 };
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        // Setup MacOSX native menu bar before AWT is loaded.
        Utils.setMacOSXMenuBar(getMsg("frame-install-title"));
        SplashScreen.main(args);
        returnValue[0] = 0;
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

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private static String getMsg(String key)
  {
    return getI18n().getMsg(key);
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
