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

import java.io.File;
import java.io.IOException;

import org.opends.quicksetup.util.Utils;

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
  public static final String CONFIG_UPGRADE_PATH = "upgrade";

  /**
   * Relative path to the change log database directory.
   */
  public static final String CHANGELOG_PATH_RELATIVE = "changelogDb";

  /**
   * Relative path to the locks directory.
   */
  public static final String LOCKS_PATH_RELATIVE = "locks";

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

  private File rootDirectory;

  private Status status;

  private Configuration configuration;

  private Configuration baseConfiguration;

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
   * @throws NullPointerException if root directory is null
   */
  public void setRootDirectory(File rootDirectory) throws NullPointerException {
    if (rootDirectory == null) {
      throw new NullPointerException("install root cannot be null");
    }
    this.rootDirectory = rootDirectory;
  }

  /**
   * Gets the Configuration object representing this file.  The
   * current configuration is stored in config/config.ldif.
   *
   * @return Configuration representing the current configuration.
   */
  public Configuration getCurrentConfiguration() {
    if (configuration == null) {
      configuration = new Configuration(getCurrentConfigurationFile());
    }
    return configuration;
  }

  /**
   * Gets the Configuration object representing this file.  The base
   * configuration is stored in config/upgrade/config.ldif.[svn rev].
   *
   * @return Configuration object representing the base configuration.
   * @throws QuickSetupException if there was a problem determining the
   * svn rev number.
   */
  public Configuration getBaseConfiguration() throws QuickSetupException {
    if (baseConfiguration == null) {
      baseConfiguration = new Configuration(getBaseConfigurationFile());
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
   * @throws QuickSetupException if there was a problem determining the
   *                             svn revision number
   */
  public File getBaseSchemaFile() throws QuickSetupException {
    return new File(getConfigurationUpgradeDirectory(),
                  "config/upgrade/schema.ldif." + getSvnRev().toString());
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current
   * which the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   * @throws QuickSetupException if there was a problem determining the
   *                             svn revision number
   */
  public File getBaseConfigurationFile() throws QuickSetupException {
    return new File(getConfigurationUpgradeDirectory(),
            BASE_CONFIG_FILE_PREFIX + getSvnRev().toString());
  }

  /**
   * Gets the SVN revision number of the build by looking for the 'base'
   * configuration file config/upgrade/config.ldif.[svn rev #].
   *
   * @return Integer representing the svn number
   * @throws QuickSetupException if for some reason the number could not
   *                             be determined
   */
  public Integer getSvnRev() throws QuickSetupException {
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
      // TODO: i18n
      throw new QuickSetupException(QuickSetupException.Type.FILE_SYSTEM_ERROR,
              "Could not determine SVN rev", null);
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
   * Returns the path to the LDIF files under the install path.
   *
   * @return the path to the LDIF files under the install path.
   */
  public File geLdifDirectory() {
    return new File(getRootDirectory(), LDIFS_PATH_RELATIVE);
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
   * @throws IOException if an error occured creating the directory.
   */
  public File createHistoryBackupDirectory() throws IOException {
    File backupDirectory =
            new File(getHistoryDirectory(),
                    "upgrade-" + System.currentTimeMillis());
    if (backupDirectory.exists()) {
      backupDirectory.delete();
    }
    if (!backupDirectory.mkdirs()) {
      // TODO: i18n
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
    return new File(getConfigurationDirectory(), CONFIG_UPGRADE_PATH);
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

}
