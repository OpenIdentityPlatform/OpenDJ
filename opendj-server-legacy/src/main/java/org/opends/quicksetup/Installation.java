/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.SetupUtils;

/**
 * This class represents the physical state of an OpenDJ installation. All the
 * operations are dependent upon the root directory that is specified in the
 * constructor.
 */
public final class Installation
{
  /** Relative path to bootstrap OpenDJ jar file. */
  public static final String OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH = "lib/bootstrap.jar";
  /** Relative path to bootstrap-client OpenDJ jar file. */
  public static final String OPENDJ_BOOTSTRAP_CLIENT_JAR_RELATIVE_PATH = "lib/bootstrap-client.jar";

  /** The relative path where all the Windows binaries (batch files) are. */
  public static final String WINDOWS_BINARIES_PATH_RELATIVE = "bat";
  /** The relative path where all the UNIX binaries (scripts) are. */
  public static final String UNIX_BINARIES_PATH_RELATIVE = "bin";
  /** The relative path where all the MacOS X Applications are. */
  private static final String MAC_APPLICATIONS_PATH_RELATIVE = "bin";
  /** The relative path where all the libraries (jar files) are. */
  public static final String LIBRARIES_PATH_RELATIVE = SetupUtils.LIBRARIES_PATH_RELATIVE;
  /** The relative path where the resources directory (to customize the product) is. */
  private static final String RESOURCES_PATH_RELATIVE = "resources";
  /** The relative path where customer classes are. */
  private static final String CLASSES_PATH_RELATIVE = "classes";
  /** The relative path where the database files are. */
  private static final String DATABASES_PATH_RELATIVE = "db";
  /** The relative path where the log files are. */
  private static final String LOGS_PATH_RELATIVE = "logs";
  /** The relative path where the LDIF files are. */
  private static final String LDIFS_PATH_RELATIVE = "ldif";
  /** The relative path where the backup files are. */
  public static final String BACKUPS_PATH_RELATIVE = "bak";
  /** The relative path where the config files are. */
  public static final String CONFIG_PATH_RELATIVE = "config";
  /** The relative path where the config files are. */
  private static final String HISTORY_PATH_RELATIVE = "history";
  /** Path to the config/upgrade directory where upgrade base files are stored. */
  private static final String UPGRADE_PATH = "upgrade";
  /** Relative path to the locks directory. */
  public static final String LOCKS_PATH_RELATIVE = "locks";
  /** Relative path to the locks directory. */
  private static final String TMP_PATH_RELATIVE = "tmp";
  /** The relative path to the current Configuration LDIF file. */
  private static final String CURRENT_CONFIG_FILE_NAME = "config.ldif";
  /** The relative path to the current Configuration LDIF file. */
  private static final String BASE_CONFIG_FILE_PREFIX = "config.ldif.";
  /** The relative path to the instance.loc file. */
  public static final String INSTANCE_LOCATION_PATH_RELATIVE = "instance.loc";
  /** The path to the instance.loc file. */
  public static final String INSTANCE_LOCATION_PATH = "/etc/opendj/"
      + INSTANCE_LOCATION_PATH_RELATIVE;
  /** The relative path to tmpl_instance. */
  private static final String TEMPLATE_RELATIVE_PATH = "template";
  /** The relative path to buildinfo file. */
  private static final String BUILDINFO_RELATIVE_PATH = "buildinfo";
  /** The UNIX setup script file name. */
  public static final String UNIX_SETUP_FILE_NAME = "setup";
  /** The Windows setup batch file name. */
  private static final String WINDOWS_SETUP_FILE_NAME = "setup.bat";
  /** The UNIX uninstall script file name. */
  public static final String UNIX_UNINSTALL_FILE_NAME = "uninstall";
  /** The Windows uninstall batch file name. */
  public static final String WINDOWS_UNINSTALL_FILE_NAME = "uninstall.bat";
  /** The UNIX upgrade script file name. */
  public static final String UNIX_UPGRADE_FILE_NAME = "upgrade";
  /** The UNIX start script file name. */
  public static final String UNIX_START_FILE_NAME = "start-ds";
  /** The Windows start batch file name. */
  public static final String WINDOWS_START_FILE_NAME = "start-ds.bat";
  /** The UNIX stop script file name. */
  public static final String UNIX_STOP_FILE_NAME = "stop-ds";
  /** The Windows stop batch file name. */
  public static final String WINDOWS_STOP_FILE_NAME = "stop-ds.bat";
  /** The UNIX control panel script file name. */
  public static final String UNIX_CONTROLPANEL_FILE_NAME = "control-panel";
  /** The Windows control panel batch file name. */
  public static final String WINDOWS_CONTROLPANEL_FILE_NAME = "control-panel.bat";
  /** The MacOS X Java application stub name. */
  public static final String MAC_JAVA_APP_STUB_NAME = "universalJavaApplicationStub";
  /** The MacOS X control panel application bundle name. */
  public static final String MAC_CONTROLPANEL_FILE_NAME = "ControlPanel.app";
  /** The UNIX status command line script file name. */
  public static final String UNIX_STATUSCLI_FILE_NAME = "status";
  /** The Windows status command line batch file name. */
  public static final String WINDOWS_STATUSCLI_FILE_NAME = "status.bat";
  /** The UNIX import LDIF script file name. */
  public static final String UNIX_IMPORT_LDIF = "import-ldif";
  /** The Windows import LDIF batch file name. */
  public static final String WINDOWS_IMPORT_LDIF = "import-ldif.bat";

  /** Name of the file kept in the history directory containing logs of upgrade and reversions. */
  public static final String HISTORY_LOG_FILE_NAME = "log";
  /** The default java properties file. */
  public static final String DEFAULT_JAVA_PROPERTIES_FILE = "java.properties";
  /** The default java properties file relative path. */
  public static final String RELATIVE_JAVA_PROPERTIES_FILE =
      CONFIG_PATH_RELATIVE + File.separator + "java.properties";
  /** The set java home and arguments properties file for Windows. */
  public static final String SET_JAVA_PROPERTIES_FILE_WINDOWS = "set-java-home.bat";
  /** Script utils file for UNIX systems. */
  public static final String SCRIPT_UTIL_FILE_UNIX = "_script-util.sh";
  /** Script utils file for Windows. */
  public static final String SCRIPT_UTIL_FILE_WINDOWS = "_script-util.bat";
  /** The set java home and arguments properties file for UNIX systems. */
  public static final String SET_JAVA_PROPERTIES_FILE_UNIX = "set-java-home";

  /** Directories required to be present for this installation to be considered valid. */
  public static final String[] REQUIRED_DIRECTORIES = new String[] {
      CONFIG_PATH_RELATIVE, DATABASES_PATH_RELATIVE, LIBRARIES_PATH_RELATIVE };

  /** The default base DN prompted to user in setup interactive mode. */
  public static final String DEFAULT_INTERACTIVE_BASE_DN = "dc=example,dc=com";

  /**
   * Performs validation on the specified file to make sure that it is an actual
   * OpenDJ installation.
   *
   * @param rootDirectory
   *          File directory candidate
   * @throws IllegalArgumentException
   *           if root directory does not appear to be an OpenDJ installation
   *           root. The thrown exception contains a localized message
   *           indicating the reason why <code>rootDirectory</code> is not a
   *           valid OpenDJ install root.
   */
  public static void validateRootDirectory(File rootDirectory)
      throws IllegalArgumentException
  {
    LocalizableMessage failureReason = null;
    if (rootDirectory == null)
    {
      failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NULL.get();
    }
    else if (!rootDirectory.exists())
    {
      failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NO_EXIST.get(Utils
          .getPath(rootDirectory));
    }
    else if (!rootDirectory.isDirectory())
    {
      failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NOT_DIR.get(Utils
          .getPath(rootDirectory));
    }
    else
    {
      String[] children = rootDirectory.list();
      if (children != null)
      {
        Set<String> childrenSet = CollectionUtils.newHashSet(children);
        for (String dir : REQUIRED_DIRECTORIES)
        {
          if (!childrenSet.contains(dir))
          {
            failureReason = INFO_ERROR_INSTALL_ROOT_DIR_NO_DIR.get(
                Utils.getPath(rootDirectory), dir);
          }
        }
      }
      else
      {
        failureReason = INFO_ERROR_INSTALL_ROOT_DIR_EMPTY.get(Utils
            .getPath(rootDirectory));
      }
    }
    if (failureReason != null)
    {
      throw new IllegalArgumentException(failureReason.toString());
    }
  }

  private static Installation local;

  /**
   * Obtains the installation by reading the classpath of the running JVM to
   * determine the location of the jars and determine the installation root.
   *
   * @return Installation obtained by reading the classpath
   */
  public static Installation getLocal()
  {
    if (local == null)
    {
      // This allows testing of configuration components when the OpenDJ.jar
      // in the classpath does not necessarily point to the server's
      String installRoot = System.getProperty("org.opends.quicksetup.Root");
      String instanceRoot = System
          .getProperty("org.opends.quicksetup.instance");

      if (installRoot == null)
      {
        installRoot = Utils.getInstallPathFromClasspath();
      }
      if (instanceRoot == null)
      {
        instanceRoot = Utils.getInstancePathFromInstallPath(installRoot);
      }
      local = new Installation(installRoot, instanceRoot);
    }
    return local;
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private File rootDirectory;
  private File instanceDirectory;

  private Status status;

  private Configuration configuration;
  private Configuration baseConfiguration;

  private BuildInformation buildInformation;
  private BuildInformation instanceInformation;

  /**
   * Creates a new instance from a root directory specified as a string.
   *
   * @param rootDirectory
   *          of this installation
   * @param instanceRootDirectory
   *          The instance root directory
   */
  public Installation(String rootDirectory, String instanceRootDirectory)
  {
    this(new File(rootDirectory), new File(instanceRootDirectory));
  }

  /**
   * Creates a new instance from a root directory specified as a File.
   *
   * @param rootDirectory
   *          of this installation
   * @param instanceDirectory
   *          of the instance
   */
  public Installation(File rootDirectory, File instanceDirectory)
  {
    setRootDirectory(rootDirectory);
    setInstanceDirectory(instanceDirectory);
  }

  /**
   * Gets the top level directory of an OpenDJ installation.
   *
   * @return File object representing the top level directory of and OpenDJ
   *         installation
   */
  public File getRootDirectory()
  {
    return this.rootDirectory;
  }

  /**
   * Gets the top level directory of an OpenDJ instance.
   *
   * @return File object representing the top level directory of and OpenDK
   *         installation
   */
  public File getInstanceDirectory()
  {
    return this.instanceDirectory;
  }

  /**
   * Sets the root directory of this installation.
   *
   * @param rootDirectory
   *          File of this installation
   */
  public void setRootDirectory(File rootDirectory)
  {
    // Hold off on doing validation of rootDirectory since
    // some applications (like the Installer) create an Installation
    // before the actual bits have been laid down on the file system.
    this.rootDirectory = rootDirectory;

    // Obtaining build information is a fairly time consuming operation.
    // Try to get a head start if possible.
    if (isValid(rootDirectory))
    {
      try
      {
        BuildInformation bi = getBuildInformation();
        logger.info(LocalizableMessage.raw("build info for " + rootDirectory.getName() + ": "
            + bi));
      }
      catch (ApplicationException e)
      {
        logger.info(LocalizableMessage.raw("error determining build information", e));
      }
    }
  }

  /**
   * Sets the root directory of this instance.
   *
   * @param instanceDirectory
   *          File of this instance
   */
  public void setInstanceDirectory(File instanceDirectory)
  {
    // Hold off on doing validation of rootDirectory since
    // some applications (like the Installer) create an Installation
    // before the actual bits have been laid down on the filesystem.
    this.instanceDirectory = instanceDirectory;

    // Obtaining build information is a fairly time consuming operation.
    // Try to get a head start if possible.
    if (isValid(instanceDirectory))
    {
      try
      {
        BuildInformation bi = getBuildInformation();
        logger.info(LocalizableMessage.raw("build info for " + instanceDirectory.getName()
            + ": " + bi));
      }
      catch (ApplicationException e)
      {
        logger.info(LocalizableMessage.raw("error determining build information", e));
      }
    }
  }

  /**
   * Indicates whether this installation appears to be an actual OpenDJ
   * installation.
   *
   * @param file
   *          The root directory
   * @return boolean where true indicates that this does indeed appear to be a
   *         valid OpenDJ installation; false otherwise
   */
  public boolean isValid(File file)
  {
    try
    {
      validateRootDirectory(file);
      return true;
    }
    catch (IllegalArgumentException e)
    {
      return false;
    }
  }

  /**
   * Creates a string explaining why this is not a legitimate OpenDJ
   * installation. Null if this is in fact a valid installation.
   *
   * @return localized message indicating the reason this is not an OpenDJ
   *         installation
   */
  public String getInvalidityReason()
  {
    try
    {
      validateRootDirectory(rootDirectory);
      return null;
    }
    catch (IllegalArgumentException e)
    {
      return e.getLocalizedMessage();
    }
  }

  /**
   * Gets the Configuration object representing this file. The current
   * configuration is stored in config/config.ldif.
   *
   * @return Configuration representing the current configuration.
   */
  public Configuration getCurrentConfiguration()
  {
    if (configuration == null)
    {
      configuration = new Configuration(this, getCurrentConfigurationFile());
    }
    return configuration;
  }

  /**
   * Gets the Configuration object representing this file. The base
   * configuration is stored in config/upgrade/config.ldif.[svn rev].
   *
   * @return Configuration object representing the base configuration.
   * @throws ApplicationException
   *           if there was a problem determining the svn rev number.
   */
  public Configuration getBaseConfiguration() throws ApplicationException
  {
    if (baseConfiguration == null)
    {
      baseConfiguration = new Configuration(this, getBaseConfigurationFile());
    }
    return baseConfiguration;
  }

  /**
   * Gets the current status of this installation.
   *
   * @return Status object representing the state of this installation.
   */
  public Status getStatus()
  {
    if (status == null)
    {
      status = new Status(this);
    }
    return status;
  }

  /**
   * Returns the path to the libraries.
   *
   * @return the path to the libraries.
   */
  public File getLibrariesDirectory()
  {
    return new File(getRootDirectory(), LIBRARIES_PATH_RELATIVE);
  }

  /**
   * Returns the path to the resources directory.
   *
   * @return the path to the resources directory.
   */
  public File getResourcesDirectory()
  {
    return new File(getRootDirectory(), RESOURCES_PATH_RELATIVE);
  }

  /**
   * Returns the path to the classes directory.
   *
   * @return the path to the classes directory.
   */
  public File getClassesDirectory()
  {
    return new File(getRootDirectory(), CLASSES_PATH_RELATIVE);
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current which
   * the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   */
  public File getSchemaConcatFile()
  {
    return new File(getConfigurationUpgradeDirectory(), SCHEMA_CONCAT_FILE_NAME);
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current which
   * the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   * @throws ApplicationException
   *           if there was a problem determining the svn revision number
   */
  public File getBaseSchemaFile() throws ApplicationException
  {
    return new File(getConfigurationUpgradeDirectory(), "schema.ldif." + getInstanceVCSRevision());
  }

  /**
   * Creates a File object representing config/upgrade/schema.ldif.current which
   * the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return File object with no
   * @throws ApplicationException
   *           if there was a problem determining the svn revision number
   */
  public File getBaseConfigurationFile() throws ApplicationException
  {
    return new File(getConfigurationUpgradeDirectory(), BASE_CONFIG_FILE_PREFIX + getInstanceVCSRevision());
  }

  /**
   * Gets the VCS revision of the build.
   *
   * @return String representing the VCS revision
   * @throws ApplicationException
   *           if for some reason the number could not be determined
   */
  public String getVCSRevision() throws ApplicationException
  {
    return getBuildInformation().getRevision();
  }

  /**
   * Gets the VCS revision of the instance.
   *
   * @return Integer representing the svn number
   * @throws ApplicationException
   *           if for some reason the number could not be determined
   */
  public String getInstanceVCSRevision() throws ApplicationException
  {
    return getInstanceBuildInformation().getRevision();
  }

  /**
   * Returns the path to the configuration file of the directory server. Note
   * that this method assumes that this code is being run locally.
   *
   * @return the path of the configuration file of the directory server.
   */
  public File getCurrentConfigurationFile()
  {
    return new File(getConfigurationDirectory(), CURRENT_CONFIG_FILE_NAME);
  }

  /**
   * Returns the relative path of the directory containing the binaries/scripts
   * of the Open DS installation. The path is relative to the installation path.
   *
   * @return the relative path of the directory containing the binaries/scripts
   *         of the Open DS installation.
   */
  public File getBinariesDirectory()
  {
    String binDir = isWindows() ? WINDOWS_BINARIES_PATH_RELATIVE : UNIX_BINARIES_PATH_RELATIVE;
    return new File(getRootDirectory(), binDir);
  }

  /**
   * Returns the path to the database files under the install path.
   *
   * @return the path to the database files under the install path.
   */
  public File getDatabasesDirectory()
  {
    return new File(getInstanceDirectory(), DATABASES_PATH_RELATIVE);
  }

  /**
   * Returns the path to the backup files under the install path.
   *
   * @return the path to the backup files under the install path.
   */
  public File getBackupDirectory()
  {
    return new File(getInstanceDirectory(), BACKUPS_PATH_RELATIVE);
  }

  /**
   * Returns the path to the config files under the install path.
   *
   * @return the path to the config files under the install path.
   */
  public File getConfigurationDirectory()
  {
    return new File(getInstanceDirectory(), CONFIG_PATH_RELATIVE);
  }

  /**
   * Returns the path to the log files under the install path.
   *
   * @return the path to the log files under the install path.
   */
  public File getLogsDirectory()
  {
    return new File(getInstanceDirectory(), LOGS_PATH_RELATIVE);
  }

  /**
   * Returns the directory where the lock files are stored.
   *
   * @return the path to the lock files.
   */
  public File getLocksDirectory()
  {
    return new File(getInstanceDirectory(), LOCKS_PATH_RELATIVE);
  }

  /**
   * Gets the directory used to store the template configuration.
   *
   * @return The directory used to store the template configuration.
   */
  public File getTemplateDirectory()
  {
    return new File(getRootDirectory(), TEMPLATE_RELATIVE_PATH);
  }

  /**
   * Gets the directory used to store files temporarily.
   *
   * @return File temporary directory
   */
  public File getTemporaryDirectory()
  {
    return new File(getInstanceDirectory(), TMP_PATH_RELATIVE);
  }

  /**
   * Returns the directory where the lock files are stored.
   *
   * @return the path to the lock files.
   */
  public File getHistoryDirectory()
  {
    return new File(getInstanceDirectory(), HISTORY_PATH_RELATIVE);
  }

  /**
   * Creates a new directory in the history directory appropriate for backing up
   * an installation during an upgrade.
   *
   * @return File representing a new backup directory. The directory can be
   *         assumed to exist if this method returns cleanly.
   * @throws IOException
   *           if an error occurred creating the directory.
   */
  public File createHistoryBackupDirectory() throws IOException
  {
    File backupDirectory = new File(getHistoryDirectory(), Long.toString(System
        .currentTimeMillis()));
    if (backupDirectory.exists())
    {
      backupDirectory.delete();
    }
    if (!backupDirectory.mkdirs())
    {
      throw new IOException("failed to create history backup directory");
    }
    return backupDirectory;
  }

  /**
   * Gets the log file where the history of upgrades and reversions is kept.
   *
   * @return File containing upgrade/reversion history.
   */
  public File getHistoryLogFile()
  {
    return new File(getHistoryDirectory(), HISTORY_LOG_FILE_NAME);
  }

  /**
   * Gets the directory config/upgrade.
   *
   * @return File representing the config/upgrade directory
   */
  public File getConfigurationUpgradeDirectory()
  {
    return new File(getConfigurationDirectory(), UPGRADE_PATH);
  }

  /**
   * Gets the directory where the upgrader stores files temporarily.
   *
   * @return File representing the upgrader's temporary directory
   */
  public File getTemporaryUpgradeDirectory()
  {
    return new File(getTemporaryDirectory(), UPGRADE_PATH);
  }

  /**
   * Gets the file for invoking a particular command appropriate for the current
   * operating system.
   *
   * @param command
   *          name of the command
   * @return File representing the command
   */
  public File getCommandFile(String command)
  {
    String filename = isWindows() ? command + ".bat" : command;
    return new File(getBinariesDirectory(), filename);
  }

  /**
   * Gets the file responsible for stopping the server appropriate for the
   * current operating system.
   *
   * @return File representing the stop command
   */
  public File getServerStartCommandFile()
  {
    return getCommandFile(UNIX_START_FILE_NAME);
  }

  /**
   * Gets the file responsible for stopping the server appropriate for the
   * current operating system.
   *
   * @return File representing the stop command
   */
  public File getServerStopCommandFile()
  {
    return getCommandFile(UNIX_STOP_FILE_NAME);
  }

  /**
   * Returns the setup file name to use with the current operating system.
   *
   * @return the setup file name to use with the current operating system.
   */
  public static String getSetupFileName()
  {
    return isWindows() ? WINDOWS_SETUP_FILE_NAME : UNIX_SETUP_FILE_NAME;
  }

  /**
   * Returns the 'ldif' directory.
   *
   * @return the 'ldif' directory.
   */
  public File getLdifDirectory()
  {
    return new File(getRootDirectory(), LDIFS_PATH_RELATIVE);
  }

  /**
   * Returns the path to the quicksetup jar file.
   *
   * @return the path to the quicksetup jar file.
   */
  public File getQuicksetupJarFile()
  {
    return new File(getLibrariesDirectory(), "quicksetup.jar");
  }

  /**
   * Returns the path to the opends jar file.
   *
   * @return the path to the opends jar file.
   */
  public File getOpenDSJarFile()
  {
    return new File(getLibrariesDirectory(), "OpenDJ.jar");
  }

  /**
   * Returns the path to the uninstall.bat file.
   *
   * @return the path to the uninstall.bat file.
   */
  public File getUninstallBatFile()
  {
    return new File(getRootDirectory(), "uninstall.bat");
  }

  /**
   * Gets the control panel command file appropriate for the current operating
   * system.
   *
   * @return File object representing the control panel command
   */
  public File getControlPanelCommandFile()
  {
    if (isMacOS())
    {
      String binDir = getRootDirectory() + File.separator + MAC_APPLICATIONS_PATH_RELATIVE;
      return new File(binDir, MAC_CONTROLPANEL_FILE_NAME);
    }
    return getCommandFile(UNIX_CONTROLPANEL_FILE_NAME);
  }

   /**
   * Gets information about the build that was used to produce the bits for this
   * installation.
   *
   * @return BuildInformation object describing this installation
   * @throws ApplicationException
   *           if there is a problem obtaining the build information
   */
  public BuildInformation getBuildInformation() throws ApplicationException
  {
    return getBuildInformation(true);
  }

  /**
   * Gets information about the build that was used to produce the bits for this
   * installation.
   *
   * @param useCachedVersion
   *          where true indicates that a potentially cached version of the
   *          build information is acceptable for use; false indicates the the
   *          build information will be created from scratch which is
   *          potentially time consuming
   * @return BuildInformation object describing this installation
   * @throws ApplicationException
   *           if there is a problem obtaining the build information
   */
  public BuildInformation getBuildInformation(boolean useCachedVersion)
      throws ApplicationException
  {
    if (buildInformation == null || !useCachedVersion)
    {
      FutureTask<BuildInformation> ft = new FutureTask<>(
          new Callable<BuildInformation>()
          {
            @Override
            public BuildInformation call() throws ApplicationException
            {
              return BuildInformation.create(Installation.this);
            }
          });
      new Thread(ft).start();
      try
      {
        buildInformation = ft.get();
      }
      catch (InterruptedException e)
      {
        logger.info(LocalizableMessage.raw("interrupted trying to get build information", e));
      }
      catch (ExecutionException e)
      {
        throw (ApplicationException) e.getCause();
      }
    }
    return buildInformation;
  }

  /**
   * Gets information about the build that was used to produce the instance.
   *
   * @return BuildInformation object describing this instance
   */
  public BuildInformation getInstanceBuildInformation()
  {
    return getInstanceBuildInformation(true);
  }

  /**
   * Gets information about the build that was used to produce the instance.
   *
   * @param useCachedVersion
   *          where true indicates that a potentially cached version of the
   *          build information is acceptable for use; false indicates the build
   *          information will be created from scratch which is potentially time
   *          consuming
   * @return BuildInformation object describing this instance
   */
  private BuildInformation getInstanceBuildInformation(boolean useCachedVersion)
  {
    if (instanceInformation == null || !useCachedVersion)
    {
      try
      {
        File bif = new File(getConfigurationDirectory(), BUILDINFO_RELATIVE_PATH);
        if (bif.exists())
        {
          // Read the first line and close the file.
          try (BufferedReader reader = new BufferedReader(new FileReader(bif)))
          {
            instanceInformation = BuildInformation.fromBuildString(reader.readLine());
          }
        }
        else
        {
          return getBuildInformation();
        }
      }
      catch (Exception e)
      {
        logger.error(LocalizableMessage.raw("error getting build information for current instance", e));
      }
    }
    return instanceInformation;
  }
}
