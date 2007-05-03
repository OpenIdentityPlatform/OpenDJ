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
package org.opends.server.core;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.api.EntryCache;
import org.opends.server.extensions.DefaultEntryCache;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.config.ConfigException;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;


import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.meta.EntryCacheCfgDefn;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the Directory Server entry cache.  Only a single entry cache may be
 * defined, but if it is absent or disabled, then a default cache will be used.
 */
public class EntryCacheConfigManager
       implements
          ConfigurationChangeListener <EntryCacheCfg>,
          ConfigurationAddListener    <EntryCacheCfg>,
          ConfigurationDeleteListener <EntryCacheCfg>
{
  // The current entry cache registered in the server
  private EntryCache _entryCache = null;

  // The default entry cache to use when no entry cache has been configured
  // or when the configured entry cache could not be initialized.
  private EntryCache _defaultEntryCache = null;


  /**
   * Creates a new instance of this entry cache config manager.
   */
  public EntryCacheConfigManager()
  {
    // No implementation is required.
  }


  /**
   * Initializes the configuration associated with the Directory Server entry
   * cache.  This should only be called at Directory Server startup.  If an
   * error occurs, then a message will be logged and the default entry cache
   * will be installed.
   *
   * @throws  ConfigException  If a configuration problem causes the entry
   *                           cache initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   install the default entry cache.
   */
  public void initializeEntryCache()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
      ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
      managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root configuration so we
    // can be notified if any entry cache entry is added or removed.
    // If entry cache configuration is using a one-to-zero-or-one relation
    // then uncomment the lines below (see issue #1558).
    /*
    // rootConfiguration.addEntryCacheAddListener(this);
    // rootConfiguration.addEntryCacheDeleteListener(this);
    */

    // First, install a default entry cache so that there will be one even if
    // we encounter a problem later.
    try
    {
      DefaultEntryCache defaultCache = new DefaultEntryCache();
      defaultCache.initializeEntryCache(null);
      DirectoryServer.setEntryCache(defaultCache);
      _defaultEntryCache = defaultCache;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTALL_DEFAULT_CACHE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    // If the entry cache configuration is not present then keep the
    // default entry cache already installed.
    // If entry cache configuration is using a one-to-zero-or-one relation
    // then uncomment the lines below (see issue #1558).
    /*
    //    if (!rootConfiguration.hasEntryCache())
    //    {
    //      logError(
    //          ErrorLogCategory.CONFIGURATION,
    //          ErrorLogSeverity.SEVERE_WARNING,
    //          MSGID_CONFIG_ENTRYCACHE_NO_CONFIG_ENTRY
    //          );
    //      return;
    //    }
    */

    // Get the entry cache configuration.
    EntryCacheCfg configuration = rootConfiguration.getEntryCache();

    // At this point, we have a configuration entry. Register a change
    // listener with it so we can be notified of changes to it over time.
    configuration.addChangeListener(this);

    // Initialize the entry cache.
    if (configuration.isEnabled())
    {
      // Load the entry cache implementation class and install the entry
      // cache with the server.
      String className = configuration.getEntryCacheClass();
      try
      {
        loadAndInstallEntryCache (className, configuration);
      }
      catch (InitializationException ie)
      {
        logError(
            ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.SEVERE_ERROR,
            ie.getMessage(),
            ie.getMessageID());
      }
    }
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      EntryCacheCfg configuration,
      List<String>  unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // entry cache.
      String className = configuration.getEntryCacheClass();
      try
      {
        // Load the class but don't initialize it.
        loadEntryCache(className, null);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessage());
        status = false;
      }
    }

    return status;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      EntryCacheCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    // If the new configuration has the entry cache disabled, then install
    // the default entry cache with the server.
    if (! configuration.isEnabled())
    {
      DirectoryServer.setEntryCache (_defaultEntryCache);

      // If an entry cache was installed then clean it.
      if (_entryCache != null)
      {
        _entryCache.finalizeEntryCache();
        _entryCache = null;
      }
      return changeResult;
    }

    // At this point, new configuration is enabled...
    // If the current entry cache is already enabled then we don't do
    // anything unless the class has changed in which case we should
    // indicate that administrative action is required.
    String newClassName = configuration.getEntryCacheClass();
    if (_entryCache !=null)
    {
      String curClassName = _entryCache.getClass().getName();
      boolean classIsNew = (! newClassName.equals (curClassName));
      if (classIsNew)
      {
        changeResult.setAdminActionRequired (true);
      }
      return changeResult;
    }

    // New entry cache is enabled and there were no previous one.
    // Instantiate the new class and initalize it.
    try
    {
      loadAndInstallEntryCache (newClassName, configuration);
    }
    catch (InitializationException ie)
    {
      changeResult.addMessage (ie.getMessage());
      changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
      return changeResult;
    }

    return changeResult;
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      EntryCacheCfg configuration,
      List<String>  unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // an entry cache.
      String className = configuration.getEntryCacheClass();
      try
      {
        // Load the class but don't initialize it.
        loadEntryCache(className, null);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessage());
        status = false;
      }
    }

    return status;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      EntryCacheCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    // Register a change listener with it so we can be notified of changes
    // to it over time.
    configuration.addChangeListener(this);

    if (configuration.isEnabled())
    {
      // Instantiate the class as an entry cache and initialize it.
      String className = configuration.getEntryCacheClass();
      try
      {
        loadAndInstallEntryCache (className, configuration);
      }
      catch (InitializationException ie)
      {
        changeResult.addMessage (ie.getMessage());
        changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
        return changeResult;
      }
    }

    return changeResult;
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      EntryCacheCfg configuration,
      List<String>  unacceptableReasons
      )
  {
    // NYI

    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration, then
    // the entry cache itself will make that determination.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      EntryCacheCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    // If the entry cache was installed then replace it with the
    // default entry cache, and clean it.
    if (_entryCache != null)
    {
      DirectoryServer.setEntryCache (_defaultEntryCache);
      _entryCache.finalizeEntryCache();
      _entryCache = null;
    }

    return changeResult;
  }


  /**
   * Loads the specified class, instantiates it as an entry cache,
   * and optionally initializes that instance. Any initialize entry
   * cache is registered in the server.
   *
   * @param  className      The fully-qualified name of the entry cache
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        entry cache, or {@code null} if the
   *                        entry cache should not be initialized.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the entry cache.
   */
  private void loadAndInstallEntryCache(
    String        className,
    EntryCacheCfg configuration
    )
    throws InitializationException
  {
    // Load the entry cache class...
    EntryCache entryCache = loadEntryCache (className, configuration);

    // ... and install the entry cache in the server.
    DirectoryServer.setEntryCache(entryCache);
    _entryCache = entryCache;
  }


  /**
   * Loads the specified class, instantiates it as an entry cache,
   * and optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the entry cache
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        entry cache, or {@code null} if the
   *                        entry cache should not be initialized.
   *
   * @return  The possibly initialized entry cache.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the entry cache.
   */
  private EntryCache<? extends EntryCacheCfg> loadEntryCache(
    String        className,
    EntryCacheCfg configuration
    )
    throws InitializationException
  {
    try
    {
      EntryCacheCfgDefn                   definition;
      ClassPropertyDefinition             propertyDefinition;
      Class<? extends EntryCache>         cacheClass;
      EntryCache<? extends EntryCacheCfg> cache;

      definition = EntryCacheCfgDefn.getInstance();
      propertyDefinition = definition.getEntryCacheClassPropertyDefinition();
      cacheClass = propertyDefinition.loadClass(className, EntryCache.class);
      cache = (EntryCache<? extends EntryCacheCfg>) cacheClass.newInstance();

      if (configuration != null)
      {
        Method method = cache.getClass().getMethod(
            "initializeEntryCache",
            configuration.definition().getServerConfigurationClass()
            );
        method.invoke(cache, configuration);
      }

      return cache;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_ENTRYCACHE_CANNOT_INITIALIZE_CACHE;
      String message = getMessage(
          msgID, className,
          String.valueOf(configuration.dn()),
          stackTraceToSingleLineString(e)
          );
      throw new InitializationException(msgID, message, e);
    }
  }

}
