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
package org.opends.quicksetup.installer;

import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

/**
 * This class is called by the setup command lines to launch the installation of
 * the Directory Server. It just checks the command line arguments and the
 * environment and determines whether the graphical or the command line
 * based setup much be launched.
 */
public class InstallLauncher extends Launcher {

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-setup-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  static private final Logger LOG =
          Logger.getLogger(InstallLauncher.class.getName());


  /**
   * The main method which is called by the setup command lines.
   *
   * @param args the arguments passed by the command lines.  In the case
   * we want to launch the cli setup they are basically the arguments that we
   * will pass to the org.opends.server.tools.InstallDS class.
   */
  public static void main(String[] args) {
    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }
    new InstallLauncher(args).launch();
  }

  /**
   * Creates a launcher.
   *
   * @param args the arguments passed by the command lines.
   */
  public InstallLauncher(String[] args) {
    super(args);
  }

  /**
   * {@inheritDoc}
   */
  protected boolean shouldPrintUsage() {
    boolean displayUsage = false;
    if ((args != null) && (args.length > 0)) {
      if (!isCli()) {
        if (args.length > 0) {
          if (args.length == 2) {
            /*
             * Just ignore the -P argument that is passed by the setup command
             * line.
             */
            if (!args[0].equals("-P")) {
              displayUsage = true;
            }
          } else {
            displayUsage = true;
          }
        }
      }
    }
    return displayUsage;
  }

  /**
   * {@inheritDoc}
   */
  protected void guiLaunchFailed(String logFileName) {
    if (logFileName != null)
    {
      System.err.println(getMsg("setup-launcher-gui-launched-failed-details",
          logFileName));
    }
    else
    {
      System.err.println(getMsg("setup-launcher-gui-launched-failed"));
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void willLaunchGui() {
    System.out.println(getMsg("setup-launcher-launching-gui"));
    System.setProperty("org.opends.quicksetup.Application.class",
            "org.opends.quicksetup.installer.offline.OfflineInstaller");
  }

  /**
   * {@inheritDoc}
   */
  protected String getFrameTitle() {
    return getI18n().getMsg("frame-install-title");
  }

  /**
   * {@inheritDoc}
   */
  public void printUsage() {
    String scriptName;
    if (Utils.isWindows()) {
      scriptName = Installation.WINDOWS_SETUP_FILE_NAME;
    } else {
      scriptName = Installation.UNIX_SETUP_FILE_NAME;
    }
    System.setProperty(ServerConstants.PROPERTY_SCRIPT_NAME, scriptName);
    ArgumentParser argParser = new ArgumentParser(getClass().getName(),
        getI18n().getMsg("setup-launcher-usage-description"),
        false);
    BooleanArgument   addBaseEntry;
    BooleanArgument   cliMode;
    BooleanArgument   showUsage;
    BooleanArgument   silentInstall;
    BooleanArgument   skipPortCheck;
    BooleanArgument   noWindowsService;
    FileBasedArgument rootPWFile;
    IntegerArgument   ldapPort;
    IntegerArgument   jmxPort;
    IntegerArgument   sampleData;
    StringArgument    baseDN;
    StringArgument    importLDIF;
    StringArgument    rootDN;
    StringArgument    rootPWString;

    try
    {
      cliMode = new BooleanArgument("cli", null, OPTION_LONG_CLI,
          MSGID_INSTALLDS_DESCRIPTION_CLI);
      argParser.addArgument(cliMode);

      silentInstall = new BooleanArgument("silent", 's', "silentInstall",
          MSGID_INSTALLDS_DESCRIPTION_SILENT);
      argParser.addArgument(silentInstall);

      baseDN = new StringArgument("basedn", OPTION_SHORT_BASEDN,
          OPTION_LONG_BASEDN, false, true, true,
          OPTION_VALUE_BASEDN,
          "dc=example,dc=com", null,
          MSGID_INSTALLDS_DESCRIPTION_BASEDN);
      argParser.addArgument(baseDN);

      addBaseEntry = new BooleanArgument("addbase", 'a', "addBaseEntry",
          MSGID_INSTALLDS_DESCRIPTION_ADDBASE);
      argParser.addArgument(addBaseEntry);

      importLDIF = new StringArgument("importldif", OPTION_SHORT_LDIF_FILE,
          OPTION_LONG_LDIF_FILE, false,
          true, true, OPTION_VALUE_LDIF_FILE,
          null, null,
          MSGID_INSTALLDS_DESCRIPTION_IMPORTLDIF);
      argParser.addArgument(importLDIF);

      sampleData = new IntegerArgument("sampledata", 'd', "sampleData", false,
          false, true, "{numEntries}", 0, null,
          true, 0, false, 0,
          MSGID_INSTALLDS_DESCRIPTION_SAMPLE_DATA);
      argParser.addArgument(sampleData);

      ldapPort = new IntegerArgument("ldapport", OPTION_SHORT_PORT,
          "ldapPort", false, false,
          true, OPTION_VALUE_PORT, 389,
          null, true, 1, true, 65535,
          MSGID_INSTALLDS_DESCRIPTION_LDAPPORT);
      argParser.addArgument(ldapPort);

      jmxPort = new IntegerArgument("jmxport", 'x', "jmxPort", false, false,
          true, "{jmxPort}",
          SetupUtils.getDefaultJMXPort(), null, true,
          1, true, 65535,
          MSGID_INSTALLDS_DESCRIPTION_JMXPORT);
      argParser.addArgument(jmxPort);

      skipPortCheck = new BooleanArgument("skipportcheck", 'S', "skipPortCheck",
          MSGID_INSTALLDS_DESCRIPTION_SKIPPORT);
      argParser.addArgument(skipPortCheck);

      rootDN = new StringArgument("rootdn",OPTION_SHORT_ROOT_USER_DN,
          OPTION_LONG_ROOT_USER_DN, false, true,
          true, OPTION_VALUE_ROOT_USER_DN,
          "cn=Directory Manager",
          null, MSGID_INSTALLDS_DESCRIPTION_ROOTDN);
      argParser.addArgument(rootDN);

      rootPWString = new StringArgument("rootpwstring", OPTION_SHORT_BINDPWD,
          "rootUserPassword",
          false, false, true,
          "{password}", null,
          null,
          MSGID_INSTALLDS_DESCRIPTION_ROOTPW);
      argParser.addArgument(rootPWString);

      rootPWFile = new FileBasedArgument("rootpwfile",
          OPTION_SHORT_BINDPWD_FILE,
          "rootUserPasswordFile", false, false,
          OPTION_VALUE_BINDPWD_FILE,
          null, null, MSGID_INSTALLDS_DESCRIPTION_ROOTPWFILE);
      argParser.addArgument(rootPWFile);

      noWindowsService = new BooleanArgument("nowindowsservice", 'n',
          "noWindowsService",
          MSGID_INSTALLDS_DESCRIPTION_NO_WINDOWS_SERVICE);
      if (SetupUtils.isWindows())
      {
        argParser.addArgument(noWindowsService);
      }

      showUsage = new BooleanArgument("help", OPTION_SHORT_HELP,
          OPTION_LONG_HELP,
          MSGID_INSTALLDS_DESCRIPTION_HELP);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
      String msg = argParser.getUsage();
      printUsage(msg);
    }
    catch (Throwable t)
    {
      System.out.println("ERROR: "+t);
      t.printStackTrace();
    }
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    // Setup currently has no implemented CliApplication
    // but rather relies on InstallDS from the server
    // package.  Note that launchCli is overloaded
    // to run this application instead of the default
    // behavior in Launcher
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected int launchCli(String[] args, CliApplication cliApp) {
    System.setProperty("org.opends.quicksetup.cli", "true");

    if (Utils.isWindows()) {
      System.setProperty("org.opends.server.scriptName",
              Installation.WINDOWS_SETUP_FILE_NAME);
    } else {
      System.setProperty("org.opends.server.scriptName",
              Installation.UNIX_SETUP_FILE_NAME);
    }
    ArrayList<String> newArgList = new ArrayList<String>();
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        if (!args[i].equalsIgnoreCase("--cli")) {
          newArgList.add(args[i]);
        }
      }
    }
    newArgList.add("--configClass");
    newArgList.add("org.opends.server.extensions.ConfigFileHandler");
    newArgList.add("--configFile");
    Installation installation =
            new Installation(Utils.getInstallPathFromClasspath());
    newArgList.add(Utils.getPath(installation.getCurrentConfigurationFile()));

    String[] newArgs = new String[newArgList.size()];
    newArgList.toArray(newArgs);

    return org.opends.server.tools.InstallDS.installMain(newArgs);
  }
}
