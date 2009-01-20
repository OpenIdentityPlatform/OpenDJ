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

package org.opends.quicksetup;

import org.opends.admin.ads.ADSContext;
import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.SetupUtils;

/**
 * This class represents the physical state of an OpenDS installation.
 * All the operations are dependent upon the root directory that is
 * specified in the constructor.
 */
public class Installation {

  /**
   * Relative path to OpenDS jar files.
   */
  public static final String[] OPEN_DS_JAR_RELATIVE_PATHS =
          {"lib/quicksetup.jar", "lib/OpenDS.jar", "lib/je.jar"};

  /**
   * The relative path where all the Windows binaries (batch files) are.
   */
  public static final String WINDOWS_BINARIES_PATH_RELATIVE = "bat";

  /**
   * The relative path where all the UNIX binaries (scripts) are.
   */
  public static final String UNIX_BINARIES_PATH_RELATIVE = "bin";

  /**
   * The relative path where all the MacOS X Applications are.
   */
  public static final String MAC_APPLICATIONS_PATH_RELATIVE = "bin";

  /**
   * The relative path where all the libraries (jar files) are.
   */
  public static final String LIBRARIES_PATH_RELATIVE =
    SetupUtils.LIBRARIES_PATH_RELATIVE;

  /**
   * The relative path where customer classes are.
   */
  public static final String CLASSES_PATH_RELATIVE = "classes";

  /**
   * The relative path where the database files are.
   */
  public static final String DATABASES_PATH_RELATIVE = "db";

  /**
   * The relative path where the log files are.
   */
  public static final String LOGS_PATH_RELATIVE = "logs";

  /**
   * The relative path where the LDIF files are.
   */
  public static final String LDIFS_PATH_RELATIVE = "ldif";

  /**
   * The relative path where the backup files are.
   */
  public static final String BACKUPS_PATH_RELATIVE = "bak";

  /**
   * The relative path where the config files are.
   */
  public static final String CONFIG_PATH_RELATIVE = "config";

  /**
   * The relative path where the config files are.
   */
  public static final String HISTORY_PATH_RELATIVE = "history";

  /**
   * Path to the config/upgrade directory where upgrade base files are stored.
   */
  public static final String UPGRADE_PATH = "upgrade";

  /**
   * Relative path to the change log database directory.
   */
  public static final String CHANGELOG_PATH_RELATIVE = "changelogDb";

  /**
   * Relative path to the locks directory.
   */
  public static final String LOCKS_PATH_RELATIVE = "locks";

  /**
   * Relative path to the locks directory.
   */
  public static final String TMP_PATH_RELATIVE = "tmp";

  /**
   * The relative path to the current Configuration LDIF file.
   */
  public static final String CURRENT_CONFIG_FILE_NAME = "config.ldif";

  /**
   * The relative path to the current Configuration LDIF file.
   */
  public static final String BASE_CONFIG_FILE_PREFIX ="config.ldif.";

  /**
   * The relative path to the tools.properties file.
   */
  public static final String TOOLS_PROPERTIES =
    CONFIG_PATH_RELATIVE+File.separator+"tools.properties";

  /**
   * The path to the default instance.
   */
  public static final String DEFAULT_INSTANCE_PATH = "/var/opends";

  /**
   * The relative path to the instance.loc file.
   */
  public static final String INSTANCE_LOCATION_PATH_RELATIVE =
    "instance.loc";

  /**
   * The path to the instance.loc file.
   */
  public static final String INSTANCE_LOCATION_PATH = "/etc/opends/" +
    INSTANCE_LOCATION_PATH_RELATIVE;

  /**
   * The relative path to tmpl_instance.
   */
  public static final String TMPL_INSTANCE_RELATIVE_PATH = "tmpl_instance";

  /**
   * The relative path to buildinfo file.
   */
  public static final String BUILDINFO_RELATIVE_PATH = "buildinfo";

  /**
   * The UNIX setup script file name.
   */
  public static final String UNIX_SETUP_FILE_NAME = "setup";

  /**
   * The Windows setup batch file name.
   */
  public static final String WINDOWS_SETUP_FILE_NAME = "setup.bat";

  /**
   * The UNIX uninstall script file name.
   */
  public static final String UNIX_UNINSTALL_FILE_NAME = "uninstall";

  /**
   * The Windows uninstall batch file name.
   */
  public static final String WINDOWS_UNINSTALL_FILE_NAME = "uninstall.bat";

  /**
   * The UNIX upgrade script file name.
   */
  public static final String UNIX_UPGRADE_FILE_NAME = "upgrade";

  /**
   * The Windows upgrade batch file name.
   */
  public static final String WINDOWS_UPGRADE_FILE_NAME = "upgrade.bat";

  /**
   * The UNIX configure script file name.
   */
  public static final String UNIX_CONFIGURE_FILE_NAME = "configure";

  /**
   * Newly upgraded Windows upgrade batch file name.  When the upgrade
   * batch file requires upgrade it is not done during execution of the
   * upgrade utility itself since replacing a running script on Windows
   * with a different version leads to unpredictable results.  Instead
   * this new name is used for the upgraded version and the user is
   * expected to manually rename the file following the upgrade.
   */
  public static final String WINDOWS_UPGRADE_FILE_NAME_NEW = "upgrade.bat.NEW";

  /**
   * The UNIX start script file name.
   */
  public static final String UNIX_START_FILE_NAME = "start-ds";

  /**
   * The Windows start batch file name.
   */
  public static final String WINDOWS_START_FILE_NAME = "start-ds.bat";

  /**
   * The UNIX stop script file name.
   */
  public static final String UNIX_STOP_FILE_NAME = "stop-ds";

  /**
   * The Windows stop batch file name.
   */
  public static final String WINDOWS_STOP_FILE_NAME = "stop-ds.bat";

  /**
   * The UNIX control panel script file name.
   */
  public static final String UNIX_CONTROLPANEL_FILE_NAME = "control-panel";

  /**
   * The Windows control panel batch file name.
   */
  public static final String WINDOWS_CONTROLPANEL_FILE_NAME =
    "control-panel.bat";

  /**
   * The UNIX dsjavaproperties script file name.
   */
  public static final String UNIX_DSJAVAPROPERTIES_FILE_NAME =
    "dsjavaproperties";

  /**
   * The Windows dsjavaproperties batch file name.
   */
  public static final String WINDOWS_DSJAVAPROPERTIES_FILE_NAME =
    "dsjavaproperties.bat";

  /**
   * The MacOS X Java application stub name.
   */
  public static final String MAC_JAVA_APP_STUB_NAME = "JavaApplicationStub";

  /**
   * The MacOS X control panel application bundle name.
   */
  public static final String MAC_CONTROLPANEL_FILE_NAME = "ControlPanel.app";

  /**
   * The UNIX status command line script file name.
   */
  public static final String UNIX_STATUSCLI_FILE_NAME = "status";

  /**
   * The Windows status command line batch file name.
   */
  public static final String WINDOWS_STATUSCLI_FILE_NAME = "status.bat";

  /**
   * Name of the file kept in the histoy directory containing logs
   * of upgrade and reversions.
   */
  public static final String HISTORY_LOG_FILE_NAME = "log";

  /**
   * The name of the directory in an upgrade backup directory (child
   * of the 'history' directory) that contains the install files from a
   * previous version.
   */
  public static final String HISTORY_BACKUP_FILES_DIR_INSTALL = "install";

  /**
   * The name of the directory in an upgrade backup directory (child
   * of the 'history' directory) that contains the instance files from a
   * previous version.
   */

  public static final String HISTORY_BACKUP_FILES_DIR_INSTANCE = "instance";
  /**
   * The name of the directory in an upgrade backup directory (child
   * of the 'history' directory) that contains the files from a
   * previous version.
   */
  public static final String HISTORY_BACKUP_FILES_DIR_NAME = "files";

  /**
   * Generic name for the backup tool.
   */
  public static final String BACKUP = "backup";

  /**
   * Generic name for the ldif-diff tool.
   */
  public static final String LDIF_DIFF = "ldif-diff";

  /**
   * The default java properties file.
   */
  public static final String DEFAULT_JAVA_PROPERTIES_FILE = "java.properties";

  /**
   * The default java properties file relative path.
   */
  public static final String RELATIVE_JAVA_PROPERTIES_FILE =
    CONFIG_PATH_RELATIVE+File.separator+"java.properties";

  /**
   * The set java home and arguments properties file for Windows.
   */
  public static final String SET_JAVA_PROPERTIES_FILE_WINDOWS =
    "set-java-home.bat";

  /**
   * script utils file for UNIX systems.
   */
  public static final String SCRIPT_UTIL_FILE_UNIX = "_script-util.sh";

  /**
   * script utils file for Windows.
   */
  public static final String SCRIPT_UTIL_FILE_WINDOWS = "_script-util.bat";

  /**
   * The set java home and arguments properties file for UNIX systems.
   */
  public static final String SET_JAVA_PROPERTIES_FILE_UNIX =
    "set-java-home";

  /**
   * Directories required to be present for this installation
   * to be considered valid.
   */
  public static final String[] REQUIRED_DIRECTORIES =
    new String[] {
                CONFIG_PATH_RELATIVE,
                DATABASES_PATH_RELATIVE,
                LIBRARIES_PATH_RELATIVE
    };

  /**
   * Performs validation on the specified file to make sure that it is
   * an actual OpenDS installation.
   * @param rootDirectory File directory candidate
   * @throws IllegalArgumentException if root directory does not appear to
   *         be an OpenDS installation root.  The thrown exception contains
   *         a localized message indicating the reason why
   *         <code>rootDirectory</code> is not a valid OpenDS install root.
   */
  static public void validateRootDirectory(File rootDirectory)
          throws IllegalArgumentException {
    Message failureReason = null;
    if (rootDirectory == null) {
      failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NULL.get();
    } else if (!rootDirectory.exists()) {
      failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NO_EXIST.get(
              Utils.getPath(rootDirectory));
    } else if (!rootDirectory.isDirectory()) {
      failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NOT_DIR.get(
              Utils.getPath(rootDirectory));
    } else {
      String[] children = rootDirectory.list();
      if (children != null) {
        Set<String> childrenSet = new HashSet<String>(Arrays.asList(children));
        for (String dir : REQUIRED_DIRECTORIES) {
          if (!childrenSet.contains(dir)) {
            failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NO_DIR.get(
                    Utils.getPath(rootDirectory), dir);
          }
        }
      } else {
        failureReason = INFO_ERROR_INSTALL_ROOT_DIR_EMPTY.get(
                Utils.getPath(rootDirectory));
      }
    }
    if (failureReason != null) {
      throw new IllegalArgumentException(failureReason.toString());
    }
  }

  static private Installation local;

  /**
   * Obtains the installation by reading the classpath of the running
   * JVM to determine the location of the jars and determine the
   * installation root.
   * @return Installation obtained by reading the classpath
   */
  static public Installation getLocal() {
    if (local == null) {

      // This allows testing of configuration components when the OpenDS.jar
      // in the classpath does not necessarily point to the server's
      String installRoot = System.getProperty("org.opends.quicksetup.Root");
      String instanceRoot = System
          .getProperty("org.opends.quicksetup.instance");

      if (installRoot == null) {
        installRoot = Utils.getInstallPathFromClasspath();
      }
      if (instanceRoot == null) {
        instanceRoot = Utils.getInstancePathFromClasspath(installRoot);
      }
      local = new Installation(installRoot, instanceRoot);
    }
    return local;
  }

  static private final Logger LOG =
          Logger.getLogger(Installation.class.getName());

  private File rootDirectory;

  private File instanceDirectory;

  private Status status;

  private Configuration configuration;

  private Configuration baseConfiguration;

  private BuildInformation buildInformation;

  private BuildInformation instanceInformation;

  /**
   * Indicates if the install and instance are in the same directory.
   * @return true if the install and instance are in the same directory.
   */
  private boolean instanceAndInstallInSameDir;

  /**
   * Creates a new instance from a root directory specified as a string.
   *
   * @param rootDirectory of this installation
   * @param instanceRootDirectory The instance root directory
   */
  public Installation(String rootDirectory, String instanceRootDirectory) {
    this(new File(rootDirectory),new File(instanceRootDirectory));
  }

  /**
   * Creates a new instance from a root directory specified as a File.
   *
   * @param rootDirectory of this installation
   *
   * @param instanceDirectory of the instance
   */
  public Installation(File rootDirectory, File instanceDirectory) {
    setRootDirectory(rootDirectory);
    setInstanceDirectory(instanceDirectory);
    if (rootDirectory.getAbsolutePath().
        equals(instanceDirectory.getAbsolutePath()))
    {
      instanceAndInstallInSameDir = true ;
    }
    else
    {
      instanceAndInstallInSameDir = false;
    }
  }

  /**
   * Indicates if the install and instance are in the same directory.
   * @return true if the install and instance are in the same directory.
   */
  public boolean instanceAndInstallInSameDir()
  {
    return instanceAndInstallInSameDir;
  }

  /**
   * Gets the top level directory of an OpenDS installation.
   *
   * @return File object representing the top level directory of
   *         and OpenDS installation
   */
  public File getRootDirectory() {
    return this.rootDirectory;
  }

  /**
   * Gets the top level directory of an OpenDS instance.
   *
   * @return File object representing the top level directory of
   *         and OpenDS installation
   */
  public File getInstanceDirectory() {
    return this.instanceDirectory;
  }

  /**
   * Gets the directory of the OpenDS template instance.
   *
   * @return File object representing the top level directory of
   *         and OpenDS installation
   */
  public File getTmplInstanceDirectory() {
    File f = new File(getRootDirectory().getAbsolutePath() +
             File.separator + TMPL_INSTANCE_RELATIVE_PATH );
    if (f.exists())
        return f;
    else
        return getInstanceDirectory();
  }

  /**
   * Sets the root directory of this installation.
   *
   * @param rootDirectory File of this installation
   */
  public void setRootDirectory(File rootDirectory) {

    // Hold off on doing validation of rootDirectory since
    // some applications (like the Installer) create an Installation
    // before the actual bits have been laid down on the file system.
    this.rootDirectory = rootDirectory;

    // Obtaining build information is a fairly time consuming operation.
    // Try to get a head start if possible.
    if (isValid(rootDirectory)) {
      try {
        BuildInformation bi = getBuildInformation();
        LOG.log(Level.INFO, "build info for " + rootDirectory.getName() +
                ": " + bi);
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "error determining build information", e);
      }
    }
  }

  /**
   * Sets the root directory of this instance.
   *
   * @param instanceDirectory File of this instance
   */
  public void setInstanceDirectory(File instanceDirectory) {

    // Hold off on doing validation of rootDirectory since
    // some applications (like the Installer) create an Installation
    // before the actual bits have been laid down on the filesyste.
    this.instanceDirectory = instanceDirectory;

    // Obtaining build information is a fairly time consuming operation.
    // Try to get a head start if possible.
    if (isValid(instanceDirectory)) {
      try {
        BuildInformation bi = getBuildInformation();
        LOG.log(Level.INFO, "build info for " + instanceDirectory.getName() +
                ": " + bi);
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "error determining build information", e);
      }
    }
  }

  /**
   * Indicates whether or not this installation appears to be an actual
   * OpenDS installation.
   * @param file The root directory
   * @return boolean where true indicates that this does indeed appear to be
   * a valid OpenDS installation; false otherwise
   */
  public boolean isValid(File file) {
    boolean valid = true;
    try {
      validateRootDirectory(file);
    } catch (IllegalArgumentException e) {
      valid = false;
    }
    return valid;
  }

  /**
   * Creates a string explaining why this is not a legitimate OpenDS
   * installation.  Null if this is in fact a vaild installation.
   * @return localized message indicating the reason this is not an
   * OpenDS installation
   */
  public String getInvalidityReason() {
    String reason = null;
    try {
      validateRootDirectory(rootDirectory);
    } catch (IllegalArgumentException e) {
      reason = e.getLocalizedMessage();
    }
    return reason;
  }

  /**
   * Gets the Configuration object representing this file.  The
   * current configuration is stored in config/config.ldif.
   *
   * @return Configuration representing the current configuration.
   */
  public Configuration getCurrentConfiguration() {
    if (configuration == null) {
      configuration = new Configuration(this, getCurrentConfigurationFile());
    }
    return configuration;
  }

  /**
   * Gets the Configuration object representing this file.  The base
   * configuration is stored in config/upgrade/config.ldif.[svn rev].
   *
   * @return Configuration object representing the base configuration.
   * @throws ApplicationException if there was a problem determining the
   * svn rev number.
   */
  public Configuration getBaseConfiguration() throws ApplicationException {
    if (baseConfiguration == null) {
      baseConfiguration = new Configuration(this, getBaseConfigurationFile());
    }
    return baseConfiguration;
  }

  /**
   * Gets the current status of this installation.
   * @return Status object representing the state of this installation.
   */
  public Status getStatus() {
    if (status == null) {
      status = new Status(this);
    }
    return status;
  }

  /**
   * Returns the path to the libraries.
   *
   * @return the path to the libraries.
   */
  public File getLibrariesDirectory() {
    return new File(getRootDirectory(), LIBRARIES_PATH_RELATIVE);
  }

  /**
   * Returns the path to the tools properties file.
   *
   * @return the path to the tools properties file.
   */
  public File getToolsPropertiesFile() {
    return new File(getTmplInstanceDirectory(), TOOLS_PROPERTIES);
  }

  /**
   * Returns the path to the set-java-home file.
   *
   * @return the path to the set-java-home file.
   */
  public File getSetJavaHomeFile() {
    return new File(getInstanceDirectory().getAbsolutePath() + File.separator +
            LIBRARIES_PATH_RELATIVE,
        Utils.isWindows()?SET_JAVA_PROPERTIES_FILE_WINDOWS :
          SET_JAVA_PROPERTIES_FILE_UNIX);
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current
   * which the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   */
  public File getSchemaConcatFile() {
    return new File(getConfigurationUpgradeDirectory(),
                    "schema.ldif.current");
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current
   * which the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   * @throws ApplicationException if there was a problem determining the
   *                             svn revision number
   */
  public File getBaseSchemaFile() throws ApplicationException {
    return new File(getConfigurationUpgradeDirectory(),
                  "schema.ldif." + getInstanceSvnRev().toString());
  }

  /**
   * Creates a File object representing
   * tmpl_instance/config/upgrade/schema.ldif.current.
   *
   * @return File object representing
   *  tmpl_instance/config/upgrade/schema.ldif.current
   * @throws ApplicationException if there was a problem determining the
   *                             svn revision number
   */
  public File getTemplSchemaFile() throws ApplicationException {
    return new File(getTmplInstanceDirectory().getAbsolutePath() +
                  File.separator + CONFIG_PATH_RELATIVE +
                  File.separator + UPGRADE_PATH,
                  "schema.ldif." + getSvnRev().toString());
  }

  /**
   * Creates a File object representing
   * tmpl_instance/config/upgrade/config.ldif.current.
   *
   * @return File object representing
   *  tmpl_instance/config/upgrade/config.ldif.current
   * @throws ApplicationException if there was a problem determining the
   *                             svn revision number
   */
  public File getTemplConfigFile() throws ApplicationException {
    return new File(getTmplInstanceDirectory().getAbsolutePath() +
                  File.separator + CONFIG_PATH_RELATIVE +
                  File.separator + UPGRADE_PATH,
                  BASE_CONFIG_FILE_PREFIX + getSvnRev().toString());
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current
   * which the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   * @throws ApplicationException if there was a problem determining the
   *                             svn revision number
   */
  public File getBaseConfigurationFile() throws ApplicationException {
    return new File(getConfigurationUpgradeDirectory(),
            BASE_CONFIG_FILE_PREFIX + getInstanceSvnRev().toString());
  }

  /**
   * Gets the SVN revision number of the build.
   *
   * @return Integer representing the svn number
   * @throws ApplicationException if for some reason the number could not
   *                             be determined
   */
  public Integer getSvnRev() throws ApplicationException {
    BuildInformation bi = getBuildInformation();
    return bi.getRevisionNumber();
  }

  /**
   * Gets the SVN revision number of the instance.
   *
   * @return Integer representing the svn number
   * @throws ApplicationException if for some reason the number could not
   *                             be determined
   */
  public Integer getInstanceSvnRev() throws ApplicationException {
    BuildInformation bi = getInstanceBuildInformation();
    return bi.getRevisionNumber();
  }

  /**
   * Returns the path to the configuration file of the directory server.  Note
   * that this method assumes that this code is being run locally.
   *
   * @return the path of the configuration file of the directory server.
   */
  public File getCurrentConfigurationFile() {
    return new File(getConfigurationDirectory(), CURRENT_CONFIG_FILE_NAME);
  }

  /**
   * Returns the path to the ADS file of the directory server.  Note
   * that this method assumes that this code is being run locally.
   *
   * @return the path of the ADS file of the directory server.
   */
  public File getADSBackendFile() {
    return new File(getTmplInstanceDirectory(), ADSContext.getAdminLDIFFile());
  }

  /**
   * Returns the relative path of the directory containing the binaries/scripts
   * of the Open DS installation.  The path is relative to the installation
   * path.
   *
   * @return the relative path of the directory containing the binaries/scripts
   *         of the Open DS installation.
   */
  public File getBinariesDirectory() {
    File binPath;
    if (Utils.isWindows()) {
      binPath = new File(getRootDirectory(), WINDOWS_BINARIES_PATH_RELATIVE);
    } else {
      binPath = new File(getRootDirectory(), UNIX_BINARIES_PATH_RELATIVE);
    }
    return binPath;
  }

  /**
   * Returns the path to the database files under the install path.
   *
   * @return the path to the database files under the install path.
   */
  public File getDatabasesDirectory() {
    return new File(getInstanceDirectory(), DATABASES_PATH_RELATIVE);
  }

  /**
   * Returns the path to the backup files under the install path.
   *
   * @return the path to the backup files under the install path.
   */
  public File getBackupDirectory() {
    return new File(getInstanceDirectory(), BACKUPS_PATH_RELATIVE);
  }

  /**
   * Returns the path to the config files under the install path.
   *
   * @return the path to the config files under the install path.
   */
  public File getConfigurationDirectory() {
    return new File(getInstanceDirectory(), CONFIG_PATH_RELATIVE);
  }

  /**
   * Returns the path to the config files under the instance path.
   *
   * @return the path to the config files under the instance path.
   */
  public File getInstallConfigurationDirectory() {
    return new File(getRootDirectory(), CONFIG_PATH_RELATIVE);
  }

  /**
   * Returns the path to the log files under the install path.
   *
   * @return the path to the log files under the install path.
   */
  public File getLogsDirectory() {
    return new File(getInstanceDirectory(), LOGS_PATH_RELATIVE);
  }

  /**
   * Returns the directory where the lock files are stored.
   *
   * @return the path to the lock files.
   */
  public File getLocksDirectory() {
    return new File(getInstanceDirectory(), LOCKS_PATH_RELATIVE);
  }

  /**
   * Gets the directory used to store files temporarily.
   * @return File temporary directory
   */
  public File getTemporaryDirectory() {
    return new File(getInstanceDirectory(), TMP_PATH_RELATIVE);
  }

  /**
   * Returns the directory where the lock files are stored.
   *
   * @return the path to the lock files.
   */
  public File getHistoryDirectory() {
    return new File(getInstanceDirectory(), HISTORY_PATH_RELATIVE);
  }

  /**
   * Creates a new directory in the history directory appropriate
   * for backing up an installation during an upgrade.
   * @return File representing a new backup directory.  The directory
   * can be assumed to exist if this method returns cleanly.
   * @throws IOException if an error occurred creating the directory.
   */
  public File createHistoryBackupDirectory() throws IOException {
    File backupDirectory =
            new File(getHistoryDirectory(),
                     Long.toString(System.currentTimeMillis()));
    if (backupDirectory.exists()) {
      backupDirectory.delete();
    }
    if (!backupDirectory.mkdirs()) {
      throw new IOException("failed to create history backup directory");
    }
    return backupDirectory;
  }

  /**
   * Gets the log file where the history of upgrades and reversions is kept.
   * @return File containing upgrade/reversion history.
   */
  public File getHistoryLogFile() {
    return new File(getHistoryDirectory(), HISTORY_LOG_FILE_NAME);
  }

  /**
   * Gets the directory config/upgrade.
   * @return File representing the config/upgrade directory
   */
  public File getConfigurationUpgradeDirectory() {
    return new File(getConfigurationDirectory(), UPGRADE_PATH);
  }

  /**
   * Gets the directory where the upgrader stores files temporarily.
   * @return File representing the upgrader's temporary directory
   */
  public File getTemporaryUpgradeDirectory() {
    return new File(getTemporaryDirectory(), UPGRADE_PATH);
  }

  /**
   * Gets the file for invoking a particular command appropriate for
   * the current operating system.
   * @param command namd of the command
   * @return File representing the command
   */
  public File getCommandFile(String command) {
    File commandFile;
    if (Utils.isWindows()) {
      commandFile = new File(getBinariesDirectory(),
              command + ".bat");
    } else {
      commandFile = new File(getBinariesDirectory(),
              command);
    }
    return commandFile;
  }

  /**
   * Gets the file responsible for stopping the server appropriate
   * for the current operating system.
   * @return File representing the stop command
   */
  public File getServerStartCommandFile() {
    File startCommandFile;
    if (Utils.isWindows()) {
      startCommandFile = new File(getBinariesDirectory(),
              WINDOWS_START_FILE_NAME);
    } else {
      startCommandFile = new File(getBinariesDirectory(),
              UNIX_START_FILE_NAME);
    }
    return startCommandFile;
  }

  /**
   * Gets the file responsible for stopping the server appropriate
   * for the current operating system.
   * @return File representing the stop command
   */
  public File getServerStopCommandFile() {
    File stopCommandFile;
    if (Utils.isWindows()) {
      stopCommandFile = new File(getBinariesDirectory(),
              WINDOWS_STOP_FILE_NAME);
    } else {
      stopCommandFile = new File(getBinariesDirectory(),
              UNIX_STOP_FILE_NAME);
    }
    return stopCommandFile;
  }

  /**
   * Returns the 'ldif' directory.
   *
   * @return the 'ldif' directory.
   */
  public File getLdifDirectory() {
    return new File(getRootDirectory(), LDIFS_PATH_RELATIVE);
  }

  /**
   * Returns the path to the quicksetup jar file.
   *
   * @return the path to the quicksetup jar file.
   */
  public File getQuicksetupJarFile() {
    return new File(getLibrariesDirectory(), "quicksetup.jar");
  }

  /**
   * Returns the path to the opends jar file.
   *
   * @return the path to the opends jar file.
   */
  public File getOpenDSJarFile() {
    return new File(getLibrariesDirectory(), "OpenDS.jar");
  }

  /**
   * Returns the path to the uninstall.bat file.
   *
   * @return the path to the uninstall.bat file.
   */
  public File getUninstallBatFile() {
    return new File(getRootDirectory(), "uninstall.bat");
  }

  /**
   * Gets the control panel command file appropriate for the current operating
   * system.
   * @return File object representing the control panel command
   */
  public File getControlPanelCommandFile() {
    File controlPanelCommandFile;
    if (Utils.isWindows()) {
      controlPanelCommandFile = new File(getBinariesDirectory(),
              WINDOWS_CONTROLPANEL_FILE_NAME);
    } else if (Utils.isMacOS()) {
      controlPanelCommandFile = new File(getRootDirectory() +
        File.separator + MAC_APPLICATIONS_PATH_RELATIVE,
        MAC_CONTROLPANEL_FILE_NAME);
    } else {
      controlPanelCommandFile = new File(getBinariesDirectory(),
              UNIX_CONTROLPANEL_FILE_NAME);
    }
    return controlPanelCommandFile;
  }

  /**
   * Gets the status command file appropriate for the current operating
   * system.
   * @return File object representing the status command
   */
  public File getStatusCommandFile() {
    File statusPanelCommandFile;
    if (Utils.isWindows()) {
      statusPanelCommandFile = new File(getBinariesDirectory(),
              WINDOWS_STATUSCLI_FILE_NAME);
    } else {
      statusPanelCommandFile = new File(getBinariesDirectory(),
              UNIX_STATUSCLI_FILE_NAME);
    }
    return statusPanelCommandFile;
  }

  /**
   * Gets the dsjavaproperties file appropriate for the current operating
   * system.
   * @return File object representing the dsjavaproperties command
   */
  public File getJavaPropertiesCommandFile() {
    File javaPropertiesCommandFile;
    if (Utils.isWindows()) {
      javaPropertiesCommandFile = new File(getBinariesDirectory(),
          WINDOWS_DSJAVAPROPERTIES_FILE_NAME);
    } else {
      javaPropertiesCommandFile = new File(getBinariesDirectory(),
          UNIX_DSJAVAPROPERTIES_FILE_NAME);
    }
    return javaPropertiesCommandFile;
  }

  /**
   * Gets information about the build that was used to produce the bits
   * for this installation.
   * @return BuildInformation object describing this installation
   * @throws ApplicationException if there is a problem obtaining the
   * build information
   */
  public BuildInformation getBuildInformation() throws ApplicationException {
    return getBuildInformation(true);
  }

  /**
   * Gets information about the build that was used to produce the bits
   * for this installation.
   * @param useCachedVersion where true indicates that a potentially cached
   * version of the build information is acceptable for use; false indicates
   * the the build information will be created from scratch which is potentially
   * time consuming
   * @return BuildInformation object describing this installation
   * @throws ApplicationException if there is a problem obtaining the
   * build information
   */
  public BuildInformation getBuildInformation(boolean useCachedVersion)
          throws ApplicationException
  {
    if (buildInformation == null || !useCachedVersion) {
      FutureTask<BuildInformation> ft = new FutureTask<BuildInformation>(
              new Callable<BuildInformation>() {
                public BuildInformation call() throws ApplicationException {
                  return BuildInformation.create(Installation.this);
                }
              });
      new Thread(ft).start();
      try {
        buildInformation = ft.get();
      } catch (InterruptedException e) {
        LOG.log(Level.INFO, "interrupted trying to get build information", e);
      } catch (ExecutionException e) {
        throw (ApplicationException)e.getCause();
      }
    }
    return buildInformation;
  }

  /**
   * Gets information about the build that was used to produce the
   * instance.
   * @return BuildInformation object describing this instance
   */
  public BuildInformation getInstanceBuildInformation() {
    return getInstanceBuildInformation(true);
  }

  /**
   * Gets information about the build that was used to produce the
   * instance.
   * @param useCachedVersion where true indicates that a potentially cached
   * version of the build information is acceptable for use; false indicates
   * the build information will be created from scratch which is potentially
   * time consuming
   * @return BuildInformation object describing this instance
   */
  public BuildInformation
          getInstanceBuildInformation(boolean useCachedVersion) {
    if (instanceInformation == null || !useCachedVersion) {
      try {
        File bif = new File(getConfigurationDirectory(),
          BUILDINFO_RELATIVE_PATH);

        if (bif.exists()) {
          BufferedReader reader = new BufferedReader(new FileReader(bif));

          // Read the first line and close the file.
          String line;
          try {
            line = reader.readLine();
            instanceInformation = BuildInformation.fromBuildString(line);
          } finally {
            try {
              reader.close();
            } catch (Exception e) {
            }
          }
        } else {
          return getBuildInformation();
        }
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "error getting build information for " +
                "current instance", e);
      }
    }
    return instanceInformation;

  }

  }
