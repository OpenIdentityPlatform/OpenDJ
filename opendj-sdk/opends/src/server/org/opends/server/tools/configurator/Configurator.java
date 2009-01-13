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
import java.io.FileWriter;
import java.io.IOException;
import org.opends.quicksetup.ReturnCode;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.StringArgument;
import java.io.InputStreamReader;
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
 * This class is called by the configure command line to move the default
 * Directory Server instance.
 */
public class Configurator extends Launcher {

  static private final Logger LOG = Logger.getLogger(
    Configurator.class.getName());

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-configure-";

  private String installRootFromSystem;

  /**
   * The path where to create the instance.
   */
  public static final String IPATH_OPTION_LONG = "instancePath";
  /**
   * The value for the short option 'instancePath'.
   */
  public static final Character IPATH_OPTION_SHORT = null;
  /**
   * The name of the owner of the instance.
   */
  public static final String USERNAME_OPTION_LONG = "userName";
  /**
   * The value for the short option 'userName'.
   */
  public static final Character USERNAME_OPTION_SHORT = null;
  /**
   * The group of the owner of the instance.
   */
  public static final String GROUPNAME_OPTION_LONG = "groupName";
  /**
   * The value for the short option 'groupName'.
   */
  public static final Character GROUPNAME_OPTION_SHORT = null;
  private ArgumentParser argParser;
  private StringArgument iPath;
  private StringArgument username;
  private StringArgument groupname;
  private BooleanArgument showUsage;
  private String user;
  private String group;
  private String ipath;

  /**
   * The main method which is called by the configure command lines.
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
      Configurator configurator;

      configurator = new Configurator(args);
      configurator.parseArgs(args);
      configurator.configure();

    } catch (ApplicationException ae) {
      LOG.log(Level.SEVERE, "Error during config: " + ae.getMessageObject());
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }
  }

  private void parseArgs(String[] args) {

    String cmd = null;
    Process proc = null;
    int exit = 0;

    try {
      argParser.parseArguments(args);

      if (argParser.usageOrVersionDisplayed()) {
        System.exit(ReturnCode.PRINT_USAGE.getReturnCode());
      }

      /* Check instancePath */
      if (iPath.hasValue()) {
        ipath  = iPath.getValue();
      } else {
        ipath = Installation.DEFAULT_INSTANCE_PATH;
      }
      File f = new File(ipath);
      if (f.exists()) {
        if (!f.isDirectory()) {
          System.err.println(
            ERR_CONFIGURE_NOT_DIRECTORY.get(ipath));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
        File[] l = f.listFiles();
        if (l.length != 0) {
          System.err.println(
            ERR_CONFIGURE_DIRECTORY_NOT_EMPTY.get(ipath));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
        if (!f.canWrite()) {
          System.err.println(
            ERR_CONFIGURE_DIRECTORY_NOT_WRITABLE.get(ipath));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }

      } else {
        File parent = f;

        while ((parent != null) && !parent.exists()) {
          parent = parent.getParentFile();
        }
        if (parent != null) {
          /* Checks f is writable */
          if (!parent.canWrite()) {
            System.err.println(
              ERR_CONFIGURE_DIRECTORY_NOT_WRITABLE.get(parent.getName()));
            System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
          }
        }
        /* Create subdirs */
        f.mkdirs();
      }

      /* Check userName/groupName by creating a temporary file and try to
       * set the owner
       */
      File temp = File.createTempFile(Configurator.class.getName(), null);
      if (username.hasValue()) {
        user = username.getValue();
        if (! Character.isLetter(user.charAt(0))) {
          System.err.println(ERR_CONFIGURE_BAD_USER_NAME.get(user));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
        cmd = "id " + user;
        proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        exit = proc.exitValue();
        if (exit != 0) {
          LOG.log(Level.SEVERE, "[" + cmd + "] returns " + exit);
          System.err.println(ERR_CONFIGURE_USER_NOT_EXIST.get(user));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
      } else {
        user = "ldap";
        cmd = "id " + user;
        proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        exit = proc.exitValue();
        if (exit != 0) {
          LOG.log(Level.SEVERE, "[" + cmd + "] returns " + exit);
          System.err.println(ERR_CONFIGURE_LDAPUSER_NOT_EXIST.get(user));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
      }

      if (!groupname.hasValue()) {
        InputStreamReader reader = null;
        int c;
        StringBuffer sb = new StringBuffer();
        cmd = "groups " + user;
        proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        reader = new InputStreamReader(proc.getInputStream());
        while (((c = reader.read()) != -1) && (c != ' ')) {
          sb.append((char) c);
        }
        exit = proc.exitValue();
        if (exit != 0) {
          LOG.log(Level.SEVERE, "[" + cmd + "] returns " + exit);
          System.err.println(ERR_CONFIGURE_GET_GROUP_ERROR.get(user, user));
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
        group = sb.toString();
      } else {
        group = groupname.getValue();
      }

      cmd = "chown " + user + ":" + group + " " + temp.getPath();
      proc = Runtime.getRuntime().exec(cmd);
      proc.waitFor();
      exit = proc.exitValue();
      if (exit != 0) {
        LOG.log(Level.SEVERE, "[" + cmd + "] returns " + exit);
        System.err.println(ERR_CONFIGURE_CHMOD_ERROR.get(user, group,
          user, group));
        System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
      }
      temp.delete();

    } catch (InterruptedException ex) {
      System.err.println(ex.getLocalizedMessage());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    } catch (IOException ex) {
      System.err.println(ex.getLocalizedMessage());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    } catch (ArgumentException ae) {
      System.err.println(ae.getMessageObject());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }

  }

  private void configure() throws ApplicationException {
    File templ_dir = new File(installRootFromSystem + "/" +
            Installation.TMPL_INSTANCE_RELATIVE_PATH);

    String cmd = null;
    Process proc = null;
    int exit = 0;
    try {
      /* Copy template instance */
      File[] l = templ_dir.listFiles();
      for (int i = 0; i < l.length; i++) {
        File subf = l[i];

        if (subf.isDirectory()) {
          cmd = "cp -R " + l[i].getAbsolutePath() + " " + ipath;
        } else {
          cmd = "cp " + l[i].getAbsolutePath() + " " + ipath;
        }
        proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        exit = proc.exitValue();
        if (exit != 0) {
          LOG.log(Level.SEVERE, "[" + cmd + "] returns " + exit);
          System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
        }
      }

      /* Change owner */
      cmd = "chown -R " + user + ":" + group + " " + ipath;
      proc = Runtime.getRuntime().exec(cmd);
      proc.waitFor();
      exit = proc.exitValue();
      if (exit != 0) {
        LOG.log(Level.SEVERE, "[" + cmd + "] returns " + exit);
        System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
      }

      /* Create instance.loc */
      File iloc = new File(Installation.INSTANCE_LOCATION_PATH);
      iloc.getParentFile().mkdirs();
      iloc.createNewFile();
      FileWriter instanceLoc = new FileWriter(iloc);
      instanceLoc.write(ipath);
      instanceLoc.close();

    } catch (IOException ex) {
      System.err.println(ex.getLocalizedMessage());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    } catch (InterruptedException ex) {
      System.err.println(ex.getLocalizedMessage());
      printUsage(false);
      System.exit(ReturnCode.APPLICATION_ERROR.getReturnCode());
    }

  }

  private Configurator(String[] args) {
    super(args);

    String scriptName = "configure";

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
                 INFO_CONFIGURE_USAGE_DESCRIPTION.get(), false);


    try {
      iPath = new StringArgument(
        IPATH_OPTION_LONG,
        IPATH_OPTION_SHORT,
        IPATH_OPTION_LONG,
        false, true,
        INFO_IPATH_PLACEHOLDER.get(),
        INFO_CONFIGURE_DESCRIPTION_IPATH.get());
      argParser.addArgument(iPath);

      username =
        new StringArgument(
          USERNAME_OPTION_LONG,
          USERNAME_OPTION_SHORT,
          USERNAME_OPTION_LONG,
          false, true,
          INFO_USER_NAME_PLACEHOLDER.get(),
          INFO_CONFIGURE_DESCRIPTION_USERNAME.get());
      argParser.addArgument(username);

      groupname =
        new StringArgument(
          GROUPNAME_OPTION_LONG,
          GROUPNAME_OPTION_SHORT,
          GROUPNAME_OPTION_LONG,
          false, true,
          INFO_GROUPNAME_PLACEHOLDER.get(),
          INFO_CONFIGURE_DESCRIPTION_GROUPNAME.get());
      argParser.addArgument(groupname);

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

