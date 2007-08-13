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
package org.opends.server.types;



import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.opends.server.api.AccessLogPublisher;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides a set of properties that may control various
 * aspects of the server environment.  Note that these properties may
 * only be altered before the Directory Server is started.  Any
 * attempt to change an environment configuration property while the
 * server is running will be rejected.
 */
public final class DirectoryEnvironmentConfig
{
  // The set of access loggers that should be put in place before the
  // server is started.
  private final ArrayList<AccessLogPublisher> accessLoggers;

  // The set of debug loggers that should be put in place before the
  // server is started.
  private final ArrayList<DebugLogPublisher> debugLoggers;

  // The set of error loggers that should be put in place before the
  // server is started.
  private final ArrayList<ErrorLogPublisher> errorLoggers;

  // The set of properties for the environment config.
  private final HashMap<String,String> configProperties;



  /**
   * Creates a new directory environment configuration initialized
   * from the system properties defined in the JVM.
   */
  public DirectoryEnvironmentConfig()
  {
    this(System.getProperties());
  }



  /**
   * Creates a new directory environment configuration initialized
   * with a copy of the provided set of properties.
   *
   * @param  properties  The properties to use when initializing this
   *                     environment configuration, or {@code null}
   *                     to use an empty set of properties.
   */
  public DirectoryEnvironmentConfig(Properties properties)
  {
    configProperties = new HashMap<String,String>();
    if (properties != null)
    {
      Enumeration propertyNames = properties.propertyNames();
      while (propertyNames.hasMoreElements())
      {
        Object o = propertyNames.nextElement();
        configProperties.put(String.valueOf(o),
                             String.valueOf(properties.get(o)));
      }
    }

    accessLoggers = new ArrayList<AccessLogPublisher>();
    debugLoggers  = new ArrayList<DebugLogPublisher>();
    errorLoggers  = new ArrayList<ErrorLogPublisher>();
  }



  /**
   * Creates a new directory environment configuration initialized
   * with a copy of the provided set of properties.
   *
   * @param  properties  The properties to use when initializing this
   *                     environment configuration, or {@code null}
   *                     to use an empty set of properties.
   */
  public DirectoryEnvironmentConfig(Map<String,String> properties)
  {
    if (properties == null)
    {
      configProperties = new HashMap<String,String>();
    }
    else
    {
      configProperties = new HashMap<String,String>(properties);
    }

    accessLoggers = new ArrayList<AccessLogPublisher>();
    debugLoggers  = new ArrayList<DebugLogPublisher>();
    errorLoggers  = new ArrayList<ErrorLogPublisher>();
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
  public String getProperty(String name)
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
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

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
   * Retrieves the directory that should be considered the server
   * root.  The determination will first be based on the properties
   * defined in this config object.  If no value is found there, then
   * the JVM system properties will be checked, followed by an
   * environment variable.
   *
   * @return  The directory that should be considered the server root,
   *          or {@code null} if it is not defined.
   */
  public File getServerRoot()
  {
    String serverRootPath = getProperty(PROPERTY_SERVER_ROOT);
    if (serverRootPath == null)
    {
      serverRootPath = System.getenv(ENV_VAR_INSTANCE_ROOT);
    }

    if (serverRootPath == null)
    {
      return null;
    }
    else
    {
      return new File(serverRootPath);
    }
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
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if ((! serverRoot.exists()) || (! serverRoot.isDirectory()))
    {
      int    msgID   = MSGID_DIRCFG_INVALID_SERVER_ROOT;
      String message = getMessage(msgID,
                                  serverRoot.getAbsolutePath());
      throw new InitializationException(msgID, message);
    }

    String serverRootPath;
    try
    {
      serverRootPath = serverRoot.getCanonicalPath();
    }
    catch (Exception e)
    {
      serverRootPath = serverRoot.getAbsolutePath();
    }

    String oldRootPath = setProperty(PROPERTY_SERVER_ROOT,
                                     serverRootPath);
    if (oldRootPath == null)
    {
      return null;
    }
    else
    {
      return new File(oldRootPath);
    }
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
    if (configFilePath == null)
    {
      File serverRoot = getServerRoot();
      if (serverRoot != null)
      {
        File configDir = new File(serverRoot, CONFIG_DIR_NAME);
        File configFile = new File(configDir, CONFIG_FILE_NAME);
        if (configFile.exists())
        {
          return configFile;
        }
      }

      return null;
    }
    else
    {
      return new File(configFilePath);
    }
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
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if ((! configFile.exists()) || (! configFile.isFile()))
    {
      int    msgID   = MSGID_DIRCFG_INVALID_CONFIG_FILE;
      String message = getMessage(msgID,
                                  configFile.getAbsolutePath());
      throw new InitializationException(msgID, message);
    }

    String configFilePath;
    try
    {
      configFilePath = configFile.getCanonicalPath();
    }
    catch (Exception e)
    {
      configFilePath = configFile.getAbsolutePath();
    }

    String oldConfigFilePath = setProperty(PROPERTY_CONFIG_FILE,
                                           configFilePath);
    if (oldConfigFilePath == null)
    {
      return null;
    }
    else
    {
      return new File(oldConfigFilePath);
    }
  }



  /**
   * Retrieves the class that provides the Directory Server
   * configuration handler implementation.  If no config handler class
   * is defined, or if a problem occurs while attempting to determine
   * the config handler class, then a default class of
   * org.opends.server.extensions.ConfigFileHandler will be returned.
   *
   * @return  The class that provides the Directory Server
   *          configuration handler implementation.
   */
  public Class getConfigClass()
  {
    String className = getProperty(PROPERTY_CONFIG_CLASS);
    if (className == null)
    {
      return ConfigFileHandler.class;
    }
    else
    {
      try
      {
        return Class.forName(className);
      }
      catch (Exception e)
      {
        return ConfigFileHandler.class;
      }
    }
  }



  /**
   * Specifies the class that provides the Directory Server
   * configuration handler implementation.  The class must be a
   * subclass of the org.opends.server.api.ConfigHandler superclass.
   *
   * @param  configClass  The class that proviedes the Directory
   *                      Server configuration handler implementation.
   *
   * @return  The class that was previously configured to provide the
   *          Directory Server configuration handler implementation,
   *          or {@code null} if none was defined.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   config handler class.
   */
  public Class setConfigClass(Class configClass)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if (! (ConfigHandler.class.isAssignableFrom(configClass)))
    {
      int    msgID   = MSGID_DIRCFG_INVALID_CONFIG_CLASS;
      String message = getMessage(msgID, configClass.getName());
      throw new InitializationException(msgID, message);
    }

    String oldClassName = setProperty(PROPERTY_CONFIG_CLASS,
                                      configClass.getName());
    if (oldClassName == null)
    {
      return null;
    }
    else
    {
      try
      {
        return Class.forName(oldClassName);
      }
      catch (Exception e)
      {
        return null;
      }
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
    String schemaDirectoryPath =
         getProperty(PROPERTY_SCHEMA_DIRECTORY);
    if (schemaDirectoryPath == null)
    {
      File serverRoot = getServerRoot();
      if (serverRoot != null)
      {
        File schemaDir = new File(serverRoot.getAbsolutePath() +
                                  File.separator + PATH_SCHEMA_DIR);
        if (schemaDir.exists() && schemaDir.isDirectory())
        {
          return schemaDir;
        }
      }

      return null;
    }
    else
    {
      return new File(schemaDirectoryPath);
    }
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
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if ((! schemaDirectory.exists()) ||
        (! schemaDirectory.isDirectory()))
    {
      int    msgID   = MSGID_DIRCFG_INVALID_SCHEMA_DIRECTORY;
      String message = getMessage(msgID,
                                  schemaDirectory.getAbsolutePath());
      throw new InitializationException(msgID, message);
    }

    String schemaDirectoryPath;
    try
    {
      schemaDirectoryPath = schemaDirectory.getCanonicalPath();
    }
    catch (Exception e)
    {
      schemaDirectoryPath = schemaDirectory.getAbsolutePath();
    }

    String oldSchemaDir = setProperty(PROPERTY_SCHEMA_DIRECTORY,
                                     schemaDirectoryPath);
    if (oldSchemaDir == null)
    {
      return null;
    }
    else
    {
      return new File(oldSchemaDir);
    }
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
    if (lockFilePath == null)
    {
      File serverRoot = getServerRoot();
      if (serverRoot == null)
      {
        return null;
      }
      else
      {
        return new File(serverRoot, LOCKS_DIRECTORY);
      }
    }
    else
    {
      return new File(lockFilePath);
    }
  }



  /**
   * Specifies the directory that should be used to hold the server
   * lock files.  If the specified path already exists, then it must
   * be a directory and its contents must be writable by the server.
   * If it does not exist, then its parent directory must exist and
   * the server should have permission to create a new subdirectory in
   * it.
   *
   * @param  lockDirectory  The directory that should be used to hold
   *                        the server lock files.
   *
   * @return  The previously-defined lock directory, or {@code null}
   *          if none was defined.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided lock
   *                                   directory.
   */
  public File setLockDirectory(File lockDirectory)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if (lockDirectory.exists())
    {
      if (! lockDirectory.isDirectory())
      {
        int    msgID   = MSGID_DIRCFG_INVALID_LOCK_DIRECTORY;
        String message = getMessage(msgID,
                                    lockDirectory.getAbsolutePath());
        throw new InitializationException(msgID, message);
      }
    }
    else
    {
      File parentFile = lockDirectory.getParentFile();
      if (! (parentFile.exists() && parentFile.isDirectory()))
      {
        int    msgID   = MSGID_DIRCFG_INVALID_LOCK_DIRECTORY;
        String message = getMessage(msgID,
                                    lockDirectory.getAbsolutePath());
        throw new InitializationException(msgID, message);
      }
    }

    String lockDirectoryPath;
    try
    {
      lockDirectoryPath = lockDirectory.getCanonicalPath();
    }
    catch (Exception e)
    {
      lockDirectoryPath = lockDirectory.getAbsolutePath();
    }

    String oldLockDir = setProperty(PROPERTY_LOCK_DIRECTORY,
                                    lockDirectoryPath);
    if (oldLockDir == null)
    {
      return null;
    }
    else
    {
      return new File(oldLockDir);
    }
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
    String disableStr =
         getProperty(PROPERTY_DISABLE_CONNECTION_HANDLERS);
    if (disableStr == null)
    {
      return false;
    }

    return disableStr.equalsIgnoreCase("true");
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
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    String oldDisableStr =
         setProperty(PROPERTY_DISABLE_CONNECTION_HANDLERS,
                     String.valueOf(disableConnectionHandlers));
    if (oldDisableStr == null)
    {
      return false;
    }
    else
    {
      return oldDisableStr.equalsIgnoreCase("true");
    }
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
    String forceDaemonStr =
         getProperty(PROPERTY_FORCE_DAEMON_THREADS);
    if (forceDaemonStr == null)
    {
      return false;
    }
    else
    {
      return forceDaemonStr.equalsIgnoreCase("true");
    }
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
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    String oldForceDaemonStr =
         setProperty(PROPERTY_FORCE_DAEMON_THREADS,
                     String.valueOf(forceDaemonThreads));
    if (oldForceDaemonStr == null)
    {
      return false;
    }
    else
    {
      return oldForceDaemonStr.equalsIgnoreCase("true");
    }
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
    String disableStr = getProperty(PROPERTY_DISABLE_EXEC);
    if (disableStr == null)
    {
      return false;
    }
    else
    {
      return disableStr.equalsIgnoreCase("true");
    }
  }



  /**
   * Specifies whether the Directory Server should be allowed to use
   * the {@code Runtime.exec()} method to be able to launch external
   * commands on the underlying system.
   *
   * @param  disableExec  Indicates whether the Directory Server
   *                      should be allowed to launch external
   *                      commands on the underlying system.
   *
   * @return  The previous setting for this configuration option.  If
   *          no previous value was specified, then {@code false} will
   *          be returned.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public boolean setDisableExec(boolean disableExec)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    String oldDisableStr = setProperty(PROPERTY_DISABLE_EXEC,
                     String.valueOf(disableExec));
    if (oldDisableStr == null)
    {
      return false;
    }
    else
    {
      return oldDisableStr.equalsIgnoreCase("true");
    }
  }



  /**
   * Retrieves the concurrency level for the Directory Server lock
   * table.
   *
   * @return  The concurrency level for the Directory Server lock
   *          table.
   */
  public int getLockManagerConcurrencyLevel()
  {
    String levelStr =
         getProperty(PROPERTY_LOCK_MANAGER_CONCURRENCY_LEVEL);
    if (levelStr == null)
    {
      return LockManager.DEFAULT_CONCURRENCY_LEVEL;
    }

    int concurrencyLevel = -1;
    try
    {
      concurrencyLevel = Integer.parseInt(levelStr);
    }
    catch (Exception e)
    {
      return LockManager.DEFAULT_CONCURRENCY_LEVEL;
    }

    if (concurrencyLevel <= 0)
    {
      return LockManager.DEFAULT_CONCURRENCY_LEVEL;
    }
    else
    {
      return concurrencyLevel;
    }
  }



  /**
   * Specifies the concurrency level for the Directory Server lock
   * table.  This should be set to the maximum number of threads that
   * could attempt to interact with the lock table at any given time.
   *
   * @param  concurrencyLevel  The concurrency level for the Directory
   *                           Server lock manager.
   *
   * @return  The previously-configured concurrency level.  If there
   *          was no previously-configured value, then the default
   *          concurrency level will be returned.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   concurrency level value.
   */
  public int setLockManagerConcurrencyLevel(int concurrencyLevel)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if (concurrencyLevel <= 0)
    {
        int    msgID   = MSGID_DIRCFG_INVALID_CONCURRENCY_LEVEL;
        String message = getMessage(msgID, concurrencyLevel);
        throw new InitializationException(msgID, message);
    }

    String concurrencyStr =
         setProperty(PROPERTY_LOCK_MANAGER_CONCURRENCY_LEVEL,
                     String.valueOf(concurrencyLevel));
    if (concurrencyStr == null)
    {
      return LockManager.DEFAULT_CONCURRENCY_LEVEL;
    }
    else
    {
      try
      {
        return Integer.parseInt(concurrencyStr);
      }
      catch (Exception e)
      {
        return LockManager.DEFAULT_CONCURRENCY_LEVEL;
      }
    }
  }



  /**
   * Retrieves the initial table size for the server lock table.  This
   * can be used to ensure that the lock table has the appropriate
   * size for the expected number of locks that will be held at any
   * given time.
   *
   * @return  The initial table size for the server lock table.
   */
  public int getLockManagerTableSize()
  {
    String sizeStr = getProperty(PROPERTY_LOCK_MANAGER_TABLE_SIZE);
    if (sizeStr == null)
    {
      return LockManager.DEFAULT_INITIAL_TABLE_SIZE;
    }
    else
    {
      try
      {
        return Integer.parseInt(sizeStr);
      }
      catch (Exception e)
      {
        return LockManager.DEFAULT_INITIAL_TABLE_SIZE;
      }
    }
  }



  /**
   * Specifies the initial table size for the server lock table.  This
   * can be used to ensure taht the lock table has the appropriate
   * size for the expected number of locks that will be held at any
   * given time.
   *
   * @param  lockTableSize  The initial table size for the server lock
   *                        table.
   *
   * @return  The previously-configured initial lock table size.  If
   *          there was no previously-configured value, then the
   *          default initial table size will be returned.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running or there is a
   *                                   problem with the provided
   *                                   initial table size.
   */
  public int setLockManagerTableSize(int lockTableSize)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    if (lockTableSize <= 0)
    {
        int    msgID   = MSGID_DIRCFG_INVALID_LOCK_TABLE_SIZE;
        String message = getMessage(msgID, lockTableSize);
        throw new InitializationException(msgID, message);
    }

    String concurrencyStr =
         setProperty(PROPERTY_LOCK_MANAGER_TABLE_SIZE,
                     String.valueOf(lockTableSize));
    if (concurrencyStr == null)
    {
      return LockManager.DEFAULT_CONCURRENCY_LEVEL;
    }
    else
    {
      try
      {
        return Integer.parseInt(concurrencyStr);
      }
      catch (Exception e)
      {
        return LockManager.DEFAULT_CONCURRENCY_LEVEL;
      }
    }
  }



  /**
   * Retrieves the list of access loggers that should be enabled in
   * the server during the startup process.  Note that these loggers
   * will not be automatically disabled when startup is complete, so
   * if they are no longer needed then they should be manually removed
   * from the server using the
   * {@code AccessLogger.removeAccessLogPublisher} method.
   *
   * @return  The list of access loggers that should be enabled in the
   *          server during the startup process.
   */
  public List<AccessLogPublisher> getAccessLoggers()
  {
    return accessLoggers;
  }



  /**
   * Adds the provided access logger to the set of loggers that should
   * be enabled in the server during the startup process.
   *
   * @param  accessLogger  The access logger that should be added to
   *                       the set of loggers enabled in the server
   *                       during the startup process.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public void addAccessLogger(AccessLogPublisher accessLogger)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    accessLoggers.add(accessLogger);
  }



  /**
   * Retrieves the list of error loggers that should be enabled in
   * the server during the startup process.  Note that these loggers
   * will not be automatically disabled when startup is complete, so
   * if they are no longer needed then they should be manually removed
   * from the server using the
   * {@code ErrorLogger.removeErrorLogPublisher} method.
   *
   * @return  The list of error loggers that should be enabled in the
   *          server during the startup process.
   */
  public List<ErrorLogPublisher> getErrorLoggers()
  {
    return errorLoggers;
  }



  /**
   * Adds the provided error logger to the set of loggers that should
   * be enabled in the server during the startup process.
   *
   * @param  errorLogger  The error logger that should be added to the
   *                      set of loggers enabled in the server during
   *                      the startup process.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public void addErrorLogger(ErrorLogPublisher errorLogger)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    errorLoggers.add(errorLogger);
  }



  /**
   * Retrieves the list of debug loggers that should be enabled in
   * the server during the startup process.  Note that these loggers
   * will not be automatically disabled when startup is complete, so
   * if they are no longer needed then they should be manually removed
   * from the server using the
   * {@code DebugLogger.removeDebugLogPublisher} method.
   *
   * @return  The list of debug loggers that should be enabled in the
   *          server during the startup process.
   */
  public List<DebugLogPublisher> getDebugLoggers()
  {
    return debugLoggers;
  }



  /**
   * Adds the provided debug logger to the set of loggers that should
   * be enabled in the server during the startup process.
   *
   * @param  debugLogger  The debug logger that should be added to
   *                      the set of loggers enabled in the server
   *                      during the startup process.
   *
   * @throws  InitializationException  If the Directory Server is
   *                                   already running.
   */
  public void addDebugLogger(DebugLogPublisher debugLogger)
         throws InitializationException
  {
    if (DirectoryServer.isRunning())
    {
      int    msgID   = MSGID_DIRCFG_SERVER_ALREADY_RUNNING;
      String message = getMessage(msgID);
      throw new InitializationException(msgID, message);
    }

    debugLoggers.add(debugLogger);
  }
}

