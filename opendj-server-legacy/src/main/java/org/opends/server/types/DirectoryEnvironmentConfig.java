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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.util.Utils;
import org.opends.server.core.DirectoryServer;

/**
 * This class provides a set of properties that may control various
 * aspects of the server environment.  Note that these properties may
 * only be altered before the Directory Server is started.  Any
 * attempt to change an environment configuration property while the
 * server is running will be rejected.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class DirectoryEnvironmentConfig
{
  /** The set of properties for the environment config. */
  private final Map<String, String> configProperties;

  private final boolean checkIfServerIsRunning;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new directory environment configuration initialized
   * from the system properties defined in the JVM.
   */
  public DirectoryEnvironmentConfig()
  {
    this(true);
  }

  /**
   * Creates a new directory environment configuration initialized from the
   * system properties defined in the JVM.
   *
   * @param checkIfServerIsRunning
   *          If {@code true}, prevent any change when server is running.
   */
  public DirectoryEnvironmentConfig(boolean checkIfServerIsRunning)
  {
    this(System.getProperties(), checkIfServerIsRunning);
  }



  /**
   * Creates a new directory environment configuration initialized
   * with a copy of the provided set of properties.
   *
   * @param  properties  The properties to use when initializing this
   *                     environment configuration, or {@code null}
   *                     to use an empty set of properties.
   * @param checkIfServerIsRunning
   *            If {@code true}, prevent any change when server is running.
   */
  private DirectoryEnvironmentConfig(Properties properties, boolean checkIfServerIsRunning)
  {
    this.checkIfServerIsRunning = checkIfServerIsRunning;
    configProperties = new HashMap<>();
    if (properties != null)
    {
      Enumeration<?> propertyNames = properties.propertyNames();
      while (propertyNames.hasMoreElements())
      {
        Object o = propertyNames.nextElement();
        configProperties.put(String.valueOf(o),
                             String.valueOf(properties.get(o)));
      }
    }
  }

  /**
   * Retrieves the property with the specified name.  The check will
   * first be made in the local config properties, but if no value is
   * found then the JVM system properties will be checked.
   *
   * @param  name  The name of the property to retrieve.
   *
   * @return  The property with the specified name, or {@code null} if
   *          no such property is defined.
   */
  private String getProperty(String name)
  {
    String value = configProperties.get(name);
    if (value == null)
    {
      value = System.getProperty(name);
    }
    return value;
  }



  /**
   * Specifies a property with the given name and value.  If a
   * property is already defined with the given name, then its value
   * will be replaced with the provided value, or the property will be
   * removed if the given value is {@code null}.
   *
   * @param  name   The name of the property to set.
   * @param  value  The value of the property to set, or {@code null}
   *                if the property is to be removed.
   *
   * @return  The previous value held for the property, or
   *          {@code null} if it was not previously set.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public String setProperty(String name, String value)
         throws InitializationException
  {
    checkServerIsRunning();

    if (value == null)
    {
      return configProperties.remove(name);
    }
    else
    {
      return configProperties.put(name, value);
    }
  }

  /**
   * Retrieves the directory that should be considered the server root.
   * <p>
   * The determination will first be based on the properties defined in this
   * object. If no value is found there, then the JVM system properties will be
   * checked, followed by an environment variable. If there is still no value,
   * then the location of the config file, if available, is used to determine
   * the root.
   *
   * @return The directory that should be considered the server root, or
   *         {@code null} if it can't be determined.
   */
  public File getServerRoot()
  {
    File rootFile = null;
    try
    {
      String serverRootPath = getProperty(PROPERTY_SERVER_ROOT);
      if (serverRootPath == null)
      {
        serverRootPath = System.getenv(ENV_VAR_INSTALL_ROOT);
      }
      if (serverRootPath != null)
      {
        rootFile = new File(serverRootPath);
        rootFile = forceNonRelativeFile(rootFile);
      }
      else
      {
        // Try to figure out root from the location of the configuration file
        // Check for property first to avoid infinite loop with getConfigFile()
        final String configFilePath = getProperty(PROPERTY_CONFIG_FILE);
        if (configFilePath != null)
        {
          final File configDirFile = getConfigFile().getParentFile();
          if (configDirFile != null
              && CONFIG_DIR_NAME.equals(configDirFile.getName()))
          {
            File parent = configDirFile.getParentFile();
            rootFile = forceNonRelativeFile(parent);
          }
        }
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_CONFIG_CANNOT_DETERMINE_SERVER_ROOT,
          ENV_VAR_INSTALL_ROOT, e);
    }
    if (rootFile == null)
    {
      logger.error(ERR_CONFIG_CANNOT_DETERMINE_SERVER_ROOT,
          ENV_VAR_INSTALL_ROOT);
    }
    return rootFile;
  }

  /**
   * Retrieves the path of the directory that should be considered the server
   * root.
   * <p>
   * This method uses the same rules than {@code getServerRoot} method, but
   * never returns {@code null}. If no directory can be found it returns as a
   * last resort the value of "user.dir" system property.
   *
   * @return the path of the directory that should be considered the server
   *         root.
   */
  public String getServerRootAsString() {
    File serverRoot = getServerRoot();
    if (serverRoot != null)
    {
      return serverRoot.getAbsolutePath();
    }
    // We don't know where the server root is, so we'll have to assume it's
    // the current working directory.
    return System.getProperty("user.dir");
  }

  /**
   * Retrieves the directory that should be considered the instance
   * root.
   *
   * @return  The directory that should be considered the instance
   *          root or {@code null} if it can't be determined.
   */
  public File getInstanceRoot() {
    File serverRoot = getServerRoot();
    if (serverRoot != null)
    {
      File instanceRoot = new File(Utils.getInstancePathFromInstallPath(getServerRoot().getAbsolutePath()));
      return forceNonRelativeFile(instanceRoot);
    }
    return null;
  }

  /**
   * Retrieves the path of the directory that should be considered the instance
   * root.
   * <p>
   * This method uses the same rules than {@code getInstanceRoot} method, but
   * never returns {@code null}. If no directory can be found it returns as a
   * last resort the value of "user.dir" system property.
   *
   * @return the path of the directory that should be considered the instance
   *         root.
   */
  public String getInstanceRootAsString()
  {
    File instanceRoot = getInstanceRoot();
    if (instanceRoot != null)
    {
      return instanceRoot.getAbsolutePath();
    }

    // We don't know where the instance root is, so we'll have to assume it's
    // the current working directory.
    return System.getProperty("user.dir");
  }

  private File forceNonRelativeFile(File file) {
    // Do a best effort to avoid having a relative representation
    // (for instance to avoid having ../../../).
    try
    {
      return file.getCanonicalFile();
    }
    catch (IOException ioe)
    {
      return file.getAbsoluteFile();
    }
  }

  /**
   * Retrieves the directory that should be considered the instance
   * root.  The determination will first be based on the properties
   * defined in this config object.  If no value is found there, then
   * the JVM system properties will be checked, followed by an
   * environment variable.
   *
   * @param serverRoot the server Root
   *
   * @return  The directory that should be considered the instance
   *          root, or {@code null} if it is not defined.
   */
  private static File getInstanceRootFromServerRoot(File serverRoot)
  {
    return new File(Utils.getInstancePathFromInstallPath(serverRoot.getAbsolutePath()));
  }



  /**
   * Specifies the directory that should be considered the server
   * root.  Any relative path used in the server should be considered
   * relative to the server root.
   *
   * @param  serverRoot  The directory that should be considered the
   *                     server root.
   *
   * @return  The previous server root, or {@code null} if there was
   *          none.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   server root.
   */
  public File setServerRoot(File serverRoot)
         throws InitializationException
  {
    checkServerIsRunning();

    if (!serverRoot.exists() || !serverRoot.isDirectory())
    {
      throw new InitializationException(
              ERR_DIRCFG_INVALID_SERVER_ROOT.get(
                      serverRoot.getAbsolutePath()));
    }

    return setPathProperty(PROPERTY_SERVER_ROOT, serverRoot);
  }

  /**
   * Sets a path property.
   *
   * @param propertyName
   *          The property name to set.
   * @param newPath
   *          The path to set on the property.
   * @return The previous property value, or {@code null} if there was none.
   * @throws InitializationException
   *           If the Directory Server is already running or there is a problem
   *           with the provided server root.
   */
  private File setPathProperty(String propertyName, File newPath)
      throws InitializationException
  {
    String normalizedNewPath;
    try
    {
      normalizedNewPath = newPath.getCanonicalPath();
    }
    catch (Exception e)
    {
      normalizedNewPath = newPath.getAbsolutePath();
    }

    String oldPath = setProperty(propertyName, normalizedNewPath);
    if (oldPath != null)
    {
      return new File(oldPath);
    }
    return null;
  }

  /**
   * Specifies the directory that should be considered the instance
   * root.  Any relative path used in the server should be considered
   * relative to the instance root.
   *
   * @param  instanceRoot  The directory that should be considered the
   *                     instanceRoot root.
   *
   * @return  The previous server root, or {@code null} if there was
   *          none.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   server root.
   */
  public File setInstanceRoot(File instanceRoot)
         throws InitializationException
  {
    checkServerIsRunning();

    if (!instanceRoot.exists() || !instanceRoot.isDirectory())
    {
      throw new InitializationException(
              ERR_DIRCFG_INVALID_SERVER_ROOT.get(
                  instanceRoot.getAbsolutePath()));
    }

    return setPathProperty(PROPERTY_INSTANCE_ROOT, instanceRoot);
  }


  /**
   * Retrieves the configuration file that should be used to
   * initialize the Directory Server config handler.  If no default
   * configuration file is specified, then the server will attempt to
   * use "config/config.ldif" below the server root if it exists.
   *
   * @return  The configuration file that should be used to initialize
   *          the Directory Server config handler, or {@code null} if
   *          no configuration file is defined.
   */
  public File getConfigFile()
  {
    String configFilePath = getProperty(PROPERTY_CONFIG_FILE);
    if (configFilePath != null)
    {
      return new File(configFilePath);
    }

    File serverRoot = getServerRoot();
    if (serverRoot != null)
    {
      File instanceRoot = getInstanceRootFromServerRoot(serverRoot);
      File configDir = new File(instanceRoot, CONFIG_DIR_NAME);
      File configFile = new File(configDir, CONFIG_FILE_NAME);
      if (configFile.exists())
      {
        return configFile;
      }
    }
    return null;
  }



  /**
   * Specifies the configuration file that should be used to
   * initialize the Directory Server config handler.
   *
   * @param  configFile  The configuration file that should be used to
   *                     initialize the Directory Server config
   *                     handler.
   *
   * @return  The previously-defined configuration file, or
   *          {@code null} if none was defined.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   configuration file.
   */
  public File setConfigFile(File configFile)
         throws InitializationException
  {
    checkServerIsRunning();

    if (!configFile.exists() || !configFile.isFile())
    {
      throw new InitializationException(
              ERR_DIRCFG_INVALID_CONFIG_FILE.get(
                      configFile.getAbsolutePath()));
    }

    return setPathProperty(PROPERTY_CONFIG_FILE, configFile);
  }

  /**
   * Indicates whether the Directory Server should attempt to start
   * with the "last known good" configuration rather than the current
   * active configuration file.  Note that if there is no "last known
   * good" configuration file available, then the server should try to
   * start using the current, active configuration file.  If no
   * explicit value is defined, then a default result of {@code false}
   * will be returned.
   *
   * @return  {@code true} if the Directory Server should attempt to
   *          start using the "last known good" configuration, or
   *          {@code false} if it should try to start using the
   *          active configuration.
   */
  public boolean useLastKnownGoodConfiguration()
  {
    return isPropertyTrue(PROPERTY_USE_LAST_KNOWN_GOOD_CONFIG);
  }

  /**
   * Indicates whether the property value is set and equal to "true" for the
   * supplied property name.
   *
   * @param propertyName
   *          the name of the property to be checked
   * @return {@code true} if the property is set and the property value is
   *         <code>"true"</code>, {@code false} otherwise .
   */
  private boolean isPropertyTrue(String propertyName)
  {
    return "true".equalsIgnoreCase(getProperty(propertyName));
  }

  /**
   * Indicates whether the Directory Server should maintain an archive
   * of previous configurations.  If no explicit value is defined,
   * then a default result of {@code true} will be returned.
   *
   * @return  {@code true} if the Directory Server should maintain an
   *          archive of previous configurations, or {@code false} if
   *          not.
   */
  public boolean maintainConfigArchive()
  {
    String maintainArchiveStr =
         getProperty(PROPERTY_MAINTAIN_CONFIG_ARCHIVE);
    return maintainArchiveStr == null
        || !"false".equalsIgnoreCase(maintainArchiveStr);
  }



  /**
   * Specifies whether the Directory Server should maintain an archive
   * of previous configurations.
   *
   * @param  maintainConfigArchive  Indicates whether the Directory
   *                                Server should maintain an archive
   *                                of previous configurations.
   *
   * @return  The previous setting for this configuration option.  If
   *          no previous value was specified, then {@code true} will
   *          be returned.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public boolean setMaintainConfigArchive(
                      boolean maintainConfigArchive)
         throws InitializationException
  {
    checkServerIsRunning();

    String oldMaintainStr =
         setProperty(PROPERTY_MAINTAIN_CONFIG_ARCHIVE,
                     String.valueOf(maintainConfigArchive));
    return oldMaintainStr == null || !"false".equalsIgnoreCase(oldMaintainStr);
  }



  /**
   * Retrieves the maximum number of archived configurations that the
   * Directory Server should maintain.  If no value is defined, then a
   * value of zero will be returned.
   *
   * @return  The maximum number of archived configurations that the
   *          Directory Server should maintain, or zero if there
   *          should not be any limit.
   */
  public int getMaxConfigArchiveSize()
  {
    String maxSizeStr =
         getProperty(PROPERTY_MAX_CONFIG_ARCHIVE_SIZE);
    if (maxSizeStr == null)
    {
      return 0;
    }

    try
    {
      int maxSize = Integer.parseInt(maxSizeStr);
      return maxSize > 0 ? maxSize : 0;
    }
    catch (Exception e)
    {
      return 0;
    }
  }

  /**
   * Retrieves the directory that contains the server schema
   * configuration files.  If no value is defined, but a default
   * directory of "config/schema" exists below the server root, then
   * that will be returned.
   *
   * @return  The directory that contains the server schema
   *          configuration files, or {@code null} if none is defined.
   */
  public File getSchemaDirectory()
  {
    String schemaDirectoryPath = getProperty(PROPERTY_SCHEMA_DIRECTORY);
    if (schemaDirectoryPath != null)
    {
      return new File(schemaDirectoryPath);
    }

    File serverRoot = getServerRoot();
    if (serverRoot != null)
    {
      File instanceRoot = getInstanceRootFromServerRoot(serverRoot);
      File schemaDir = new File(instanceRoot.getAbsolutePath() + File.separator + PATH_SCHEMA_DIR);
      if (schemaDir.exists() && schemaDir.isDirectory())
      {
        return schemaDir;
      }
    }
    return null;
  }



  /**
   * Specifies the directory that should contain the server schema
   * configuration files.  It must exist and must be a directory.
   *
   * @param  schemaDirectory  The directory that should contain the
   *                          server schema configuration files.
   *
   * @return  The previously-defined schema configuration directory,
   *          or {@code null} if none was defined.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   schema directory.
   */
  public File setSchemaDirectory(File schemaDirectory)
         throws InitializationException
  {
    checkServerIsRunning();

    if (!schemaDirectory.exists() || !schemaDirectory.isDirectory())
    {
      throw new InitializationException(
              ERR_DIRCFG_INVALID_SCHEMA_DIRECTORY.get(
                      schemaDirectory.getAbsolutePath()));
    }

    return setPathProperty(PROPERTY_SCHEMA_DIRECTORY, schemaDirectory);
  }



  /**
   * Retrieves the directory that should be used to hold the server
   * lock files.  If no value is defined, then the server will attempt
   * to use a default directory of "locks" below the server root.
   *
   * @return  The directory that should be used to hold the server
   *          lock files, or {@code null} if it cannot be determined.
   */
  public File getLockDirectory()
  {
    String lockFilePath = getProperty(PROPERTY_LOCK_DIRECTORY);
    if (lockFilePath != null)
    {
      return new File(lockFilePath);
    }

    File serverRoot = getServerRoot();
    if (serverRoot != null)
    {
      File instanceRoot = getInstanceRootFromServerRoot(serverRoot);
      return new File(instanceRoot, LOCKS_DIRECTORY);
    }
    return null;
  }

  /**
   * Indicates whether the Directory Server startup process should
   * skip the connection handler creation and initialization phases.
   *
   * @return  {@code true} if the Directory Server should not start
   *          its connection handlers, or {@code false} if the
   *          connection handlers should be enabled.
   */
  public boolean disableConnectionHandlers()
  {
    return isPropertyTrue(PROPERTY_DISABLE_CONNECTION_HANDLERS);
  }

  /**
   * Indicates whether the Directory Server startup process should
   * skip the synchronization provider creation and initialization
   * phases.
   *
   * @return  {@code true} if the Directory Server should not start
   *          its synchronization provider, or {@code false} if the
   *          synchronization provider should be enabled.
   */
  public boolean disableSynchronization()
  {
    return isPropertyTrue(PROPERTY_DISABLE_SYNCHRONIZATION);
  }

  /**
   * Indicates whether the Directory Server startup process should
   * skip the synchronization between admin data and the
   * configuration.
   *
   * @return  {@code true} if the Directory Server should start
   *          synchronization between admin data and the
   *          configuration.
   */
  public boolean disableAdminDataSynchronization()
  {
    return isPropertyTrue(PROPERTY_DISABLE_ADMIN_DATA_SYNCHRONIZATION);
  }

  /**
   * Specifies whether the Directory Server startup process should
   * skip the connection handler creation and initialization phases.
   *
   * @param  disableConnectionHandlers  Indicates whether the
   *                                    Directory Server should skip
   *                                    the connection handler
   *                                    creation and initialization
   *                                    phases.
   *
   * @return  The previous setting for this configuration option.  If
   *          no previous value was specified, then {@code false} will
   *          be returned.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public boolean setDisableConnectionHandlers(
                      boolean disableConnectionHandlers)
         throws InitializationException
  {
    return setBooleanProperty(PROPERTY_DISABLE_CONNECTION_HANDLERS,
        disableConnectionHandlers);
  }

  /**
   * Sets a boolean property.
   *
   * @param propertyName
   *          the property name to set
   * @param newValue
   *          the new value to set for the property
   * @return The previous setting for this configuration option. If no previous
   *         value was specified, then {@code false} will be returned.
   * @throws InitializationException
   *           If the Directory Server is already running or there is a problem
   *           with the provided server root.
   */
  private boolean setBooleanProperty(String propertyName, boolean newValue)
      throws InitializationException
  {
    checkServerIsRunning();

    final String oldValue = setProperty(propertyName, String.valueOf(newValue));
    return "true".equalsIgnoreCase(oldValue);
  }

  /**
   * Indicates whether all threads created by the Directory Server
   * should be created as daemon threads.
   *
   * @return  {@code true} if all threads created by the Directory
   *          Server should be created as daemon threads, or
   *          {@code false} if not.
   */
  public boolean forceDaemonThreads()
  {
    return isPropertyTrue(PROPERTY_FORCE_DAEMON_THREADS);
  }



  /**
   * Specifies whether all threads created by the Directory Server
   * should be created as daemon threads.
   *
   * @param  forceDaemonThreads  Indicates whether all threads created
   *                             by the Directory Server should be
   *                             created as daemon threads.
   *
   * @return  The previous setting for this configuration option.  If
   *          no previous value was specified, then {@code false} will
   *          be returned.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public boolean setForceDaemonThreads(boolean forceDaemonThreads)
         throws InitializationException
  {
    return setBooleanProperty(PROPERTY_FORCE_DAEMON_THREADS,
        forceDaemonThreads);
  }



  /**
   * Indicates whether the Directory Server should be allowed to use
   * the {@code Runtime.exec()} method to be able to launch external
   * commands on the underlying system.
   *
   * @return  {@code true} if the Directory Server should be allowed
   *          to use {@code Runtime.exec()}, or {@code false} if not.
   */
  public boolean disableExec()
  {
    return isPropertyTrue(PROPERTY_DISABLE_EXEC);
  }

  /** Throws an exception if server is running and it is not allowed. */
  private void checkServerIsRunning() throws InitializationException
  {
    if (checkIfServerIsRunning && DirectoryServer.isRunning())
    {
      throw new InitializationException(
              ERR_DIRCFG_SERVER_ALREADY_RUNNING.get());
    }
  }
}
