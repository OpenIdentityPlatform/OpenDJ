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

package org.opends.quicksetup;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.i18n.ResourceProvider;

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
   * The relative path where all the libraries (jar files) are.
   */
  public static final String LIBRARIES_PATH_RELATIVE = "lib";

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
   * The UNIX uninstall script file name.
   */
  public static final String UNIX_UPGRADE_FILE_NAME = "upgrade";

  /**
   * The Windows uninstall batch file name.
   */
  public static final String WINDOWS_UPGRADE_FILE_NAME = "upgrade.bat";

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
   * The UNIX status panel script file name.
   */
  public static final String UNIX_STATUSPANEL_FILE_NAME = "status-panel";

  /**
   * The Windows status panel batch file name.
   */
  public static final String WINDOWS_STATUSPANEL_FILE_NAME = "status-panel.bat";

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
   * Generic name for the backup tool.
   */
  public static final String BACKUP = "backup";

  /**
   * Generic name for the ldif-diff tool.
   */
  public static final String LDIF_DIFF = "ldif-diff";

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
    String failureReason = null;
    if (rootDirectory == null) {
      failureReason = getMsg("error-install-root-dir-null");
    } else if (!rootDirectory.exists()) {
      failureReason = getMsg("error-install-root-dir-no-exist",
              Utils.getPath(rootDirectory));
    } else if (!rootDirectory.isDirectory()) {
      failureReason = getMsg("error-install-root-dir-not-dir",
              Utils.getPath(rootDirectory));
    } else {
      String[] children = rootDirectory.list();
      if (children != null) {
        Set<String> childrenSet = new HashSet<String>(Arrays.asList(children));
        for (String dir : REQUIRED_DIRECTORIES) {
          if (!childrenSet.contains(dir)) {
            failureReason = getMsg("error-install-root-dir-no-dir",
                    Utils.getPath(rootDirectory), dir);
          }
        }
      } else {
        failureReason = getMsg("error-install-root-dir-empty",
                    Utils.getPath(rootDirectory));
      }
    }
    if (failureReason != null) {
      throw new IllegalArgumentException(failureReason);
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

      if (installRoot == null) {
        installRoot = Utils.getInstallPathFromClasspath();
      }
      local = new Installation(installRoot);
    }
    return local;
  }

  static private final Logger LOG =
          Logger.getLogger(Installation.class.getName());

  private File rootDirectory;

  private Status status;

  private Configuration configuration;

  private Configuration baseConfiguration;

  private BuildInformation buildInformation;

  /**
   * Creates a new instance from a root directory specified as a string.
   *
   * @param rootDirectory of this installation
   */
  public Installation(String rootDirectory) {
    this(new File(rootDirectory));
  }

  /**
   * Creates a new instance from a root directory specified as a File.
   *
   * @param rootDirectory of this installation
   */
  public Installation(File rootDirectory) {
    setRootDirectory(rootDirectory);
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
   * Sets the root directory of this installation.
   *
   * @param rootDirectory File of this installation
   */
  public void setRootDirectory(File rootDirectory) {

    // Hold off on doing validation of rootDirectory since
    // some applications (like the Installer) create an Installation
    // before the actual bits have been laid down on the filesyste.
    this.rootDirectory = rootDirectory;

    // Obtaining build information is a fairly time consuming operation.
    // Try to get a head start if possible.
    if (isValid()) {
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
   * Indicates whether or not this installation appears to be an actual
   * OpenDS installation.
   * @return boolean where true indicates that this does indeed appear to be
   * a valid OpenDS installation; false otherwise
   */
  public boolean isValid() {
    boolean valid = true;
    try {
      validateRootDirectory(rootDirectory);
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
                  "schema.ldif." + getSvnRev().toString());
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
            BASE_CONFIG_FILE_PREFIX + getSvnRev().toString());
  }

  /**
   * Gets the SVN revision number of the build by looking for the 'base'
   * configuration file config/upgrade/config.ldif.[svn rev #].
   *
   * @return Integer representing the svn number
   * @throws ApplicationException if for some reason the number could not
   *                             be determined
   */
  public Integer getSvnRev() throws ApplicationException {
    Integer rev = null;
    File configUpgradeDir = getConfigurationUpgradeDirectory();
    if (configUpgradeDir.exists()) {
      String[] upgradeFileNames = configUpgradeDir.list();
      for (String upgradeFileName : upgradeFileNames) {

        // This code assumes that there will only be one file
        // in the config/upgrade directory having the prefix
        // config.ldif. and that it will end with the SVN
        // revision number of the build of the current
        // installation.
        if (upgradeFileName.startsWith(BASE_CONFIG_FILE_PREFIX)) {
          String svnRefString = upgradeFileName.substring(
                  BASE_CONFIG_FILE_PREFIX.length());
          try {
            rev = new Integer(svnRefString);
          } catch (NumberFormatException nfe) {
            // ignore for now; try other files
          }
          if (rev != null) {
            break;
          }
        }
      }
    }
    if (rev == null) {
      throw new ApplicationException(
          ApplicationException.Type.FILE_SYSTEM_ERROR,
          getMsg("error-determining-svn-rev"), null);
    }
    return rev;
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
    return new File(getRootDirectory(), DATABASES_PATH_RELATIVE);
  }

  /**
   * Returns the path to the backup files under the install path.
   *
   * @return the path to the backup files under the install path.
   */
  public File getBackupDirectory() {
    return new File(getRootDirectory(), BACKUPS_PATH_RELATIVE);
  }

  /**
   * Returns the path to the config files under the install path.
   *
   * @return the path to the config files under the install path.
   */
  public File getConfigurationDirectory() {
    return new File(getRootDirectory(), CONFIG_PATH_RELATIVE);
  }

  /**
   * Returns the path to the log files under the install path.
   *
   * @return the path to the log files under the install path.
   */
  public File getLogsDirectory() {
    return new File(getRootDirectory(), LOGS_PATH_RELATIVE);
  }

  /**
   * Returns the directory where the lock files are stored.
   *
   * @return the path to the lock files.
   */
  public File getLocksDirectory() {
    return new File(getRootDirectory(), LOCKS_PATH_RELATIVE);
  }

  /**
   * Gets the directory used to store files temporarily.
   * @return File temporary directory
   */
  public File getTemporaryDirectory() {
    return new File(getRootDirectory(), TMP_PATH_RELATIVE);
  }

  /**
   * Returns the directory where the lock files are stored.
   *
   * @return the path to the lock files.
   */
  public File getHistoryDirectory() {
    return new File(getRootDirectory(), HISTORY_PATH_RELATIVE);
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
   * Gets the status panel command file appropriate for the current operating
   * system.
   * @return File object representing the status panel command
   */
  public File getStatusPanelCommandFile() {
    File statusPanelCommandFile;
    if (Utils.isWindows()) {
      statusPanelCommandFile = new File(getBinariesDirectory(),
              WINDOWS_STATUSPANEL_FILE_NAME);
    } else {
      statusPanelCommandFile = new File(getBinariesDirectory(),
              UNIX_STATUSPANEL_FILE_NAME);
    }
    return statusPanelCommandFile;
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
   * {@inheritDoc}
   */
  public String toString() {
    return Utils.getPath(rootDirectory);
  }

  static private String getMsg(String key) {
    return ResourceProvider.getInstance().getMsg(key);
  }

  static private String getMsg(String key, String... args) {
    return ResourceProvider.getInstance().getMsg(key, args);
  }

}