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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.EntryCacheCfgDefn;
import org.forgerock.opendj.server.config.server.EntryCacheCfg;
import org.forgerock.opendj.server.config.server.EntryCacheMonitorProviderCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.Entry;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.extensions.DefaultEntryCache;
import org.opends.server.monitors.EntryCacheMonitorProvider;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the configuration
 * for the Directory Server entry cache.  The default entry cache is always
 * enabled.
 */
public class EntryCacheConfigManager
       implements
          ConfigurationChangeListener <EntryCacheCfg>,
          ConfigurationAddListener    <EntryCacheCfg>,
          ConfigurationDeleteListener <EntryCacheCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The default entry cache. */
  private DefaultEntryCache _defaultEntryCache;

  /** The entry cache order map sorted by the cache level. */
  @SuppressWarnings("rawtypes")
  private SortedMap<Integer, EntryCache> cacheOrderMap = new TreeMap<>();

  /** The entry cache to level map. */
  private Map<DN,Integer> cacheNameToLevelMap = new HashMap<>();

  /** Global entry cache monitor provider name. */
  private static final String
    DEFAULT_ENTRY_CACHE_MONITOR_PROVIDER = "Entry Caches";

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this entry cache config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public EntryCacheConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes the default entry cache.
   * This should only be called at Directory Server startup.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   install the default entry cache.
   */
  public void initializeDefaultEntryCache()
         throws InitializationException
  {
    try
    {
      DefaultEntryCache defaultCache = new DefaultEntryCache();
      defaultCache.initializeEntryCache(null);
      DirectoryServer.setEntryCache(defaultCache);
      _defaultEntryCache = defaultCache;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_ENTRYCACHE_CANNOT_INSTALL_DEFAULT_CACHE.get(
          stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }

  /**
   * Initializes the configuration associated with the Directory Server entry
   * cache.  This should only be called at Directory Server startup.  If an
   * error occurs, then a message will be logged for each entry cache that is
   * failed to initialize.
   *
   * @throws  ConfigException  If a configuration problem causes the entry
   *                           cache initialization process to fail.
   */
  public void initializeEntryCache()
         throws ConfigException
  {
    // Default entry cache should be already installed with
    // <CODE>initializeDefaultEntryCache()</CODE> method so
    // that there will be one even if we encounter a problem later.

    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addEntryCacheAddListener(this);
    rootConfiguration.addEntryCacheDeleteListener(this);

    // Get the base entry cache configuration entry.
    Entry entryCacheBase;
    try {
      DN configEntryDN = DN.valueOf(ConfigConstants.DN_ENTRY_CACHE_BASE);
      entryCacheBase   = DirectoryServer.getConfigEntry(configEntryDN);
    } catch (Exception e) {
      logger.traceException(e);

      logger.warn(WARN_CONFIG_ENTRYCACHE_NO_CONFIG_ENTRY);
      return;
    }

    // If the configuration base entry is null, then assume it doesn't exist.
    // At least that entry must exist in the configuration, even if there are
    // no entry cache defined below it.
    if (entryCacheBase == null)
    {
      logger.error(WARN_CONFIG_ENTRYCACHE_NO_CONFIG_ENTRY);
      return;
    }

    // Initialize every entry cache configured.
    for (String cacheName : rootConfiguration.listEntryCaches())
    {
      // Get the entry cache configuration.
      EntryCacheCfg configuration = rootConfiguration.getEntryCache(cacheName);

      // At this point, we have a configuration entry. Register a change
      // listener with it so we can be notified of changes to it over time.
      configuration.addChangeListener(this);

      // Check if there is another entry cache installed at the same level.
      if (!cacheOrderMap.isEmpty()
          && cacheOrderMap.containsKey(configuration.getCacheLevel()))
      {
        // Log error and skip this cache.
        logger.error(ERR_CONFIG_ENTRYCACHE_CONFIG_LEVEL_NOT_ACCEPTABLE,
            configuration.dn(), configuration.getCacheLevel());
        continue;
      }

      // Initialize the entry cache.
      if (configuration.isEnabled()) {
        // Load the entry cache implementation class and install the entry
        // cache with the server.
        String className = configuration.getJavaClass();
        try {
          loadAndInstallEntryCache(className, configuration);
        } catch (InitializationException ie) {
          logger.error(ie.getMessageObject());
        }
      }
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      EntryCacheCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    // Get the name of the class and make sure we can instantiate it as an
    // entry cache.
    String className = configuration.getJavaClass();
    try {
      // Load the class but don't initialize it.
      loadEntryCache(className, configuration, false);
    } catch (InitializationException ie) {
      unacceptableReasons.add(ie.getMessageObject());
      status = false;
    }

    if (!cacheOrderMap.isEmpty() && !cacheNameToLevelMap.isEmpty())
    {
      final ByteString normDN = configuration.dn().toNormalizedByteString();
      if (cacheNameToLevelMap.containsKey(normDN)) {
        int currentCacheLevel = cacheNameToLevelMap.get(normDN);

        // Check if there any existing cache at the same level.
        if (currentCacheLevel != configuration.getCacheLevel() &&
          cacheOrderMap.containsKey(configuration.getCacheLevel())) {
          unacceptableReasons.add(
            ERR_CONFIG_ENTRYCACHE_CONFIG_LEVEL_NOT_ACCEPTABLE.get(
              configuration.dn(), configuration.getCacheLevel()));
          status = false;
        }
      }
    }

    return status;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      EntryCacheCfg configuration
      )
  {
    EntryCache<? extends EntryCacheCfg> entryCache = null;

    // If we this entry cache is already installed and active it
    // should be present in the cache maps, if so use it.
    if (!cacheOrderMap.isEmpty() && !cacheNameToLevelMap.isEmpty()) {
      final DN dn = configuration.dn();
      if (cacheNameToLevelMap.containsKey(dn))
      {
        int currentCacheLevel = cacheNameToLevelMap.get(dn);
        entryCache = cacheOrderMap.get(currentCacheLevel);

        // Check if the existing cache just shifted its level.
        if (currentCacheLevel != configuration.getCacheLevel()) {
          // Update the maps then.
          cacheOrderMap.remove(currentCacheLevel);
          cacheOrderMap.put(configuration.getCacheLevel(), entryCache);
          cacheNameToLevelMap.put(dn, configuration.getCacheLevel());
        }
      }
    }

    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // If an entry cache was installed then remove it.
    if (!configuration.isEnabled())
    {
      configuration.getCacheLevel();
      if (entryCache != null)
      {
        EntryCacheMonitorProvider monitor = entryCache.getEntryCacheMonitor();
        if (monitor != null)
        {
          DirectoryServer.deregisterMonitorProvider(monitor);
          monitor.finalizeMonitorProvider();
          entryCache.setEntryCacheMonitor(null);
        }
        entryCache.finalizeEntryCache();
        cacheOrderMap.remove(configuration.getCacheLevel());
        entryCache = null;
      }
      return changeResult;
    }

    // Push any changes made to the cache order map.
    setCacheOrder(cacheOrderMap);

    // At this point, new configuration is enabled...
    // If the current entry cache is already enabled then we don't do
    // anything unless the class has changed in which case we should
    // indicate that administrative action is required.
    String newClassName = configuration.getJavaClass();
    if ( entryCache != null)
    {
      String curClassName = entryCache.getClass().getName();
      boolean classIsNew = !newClassName.equals(curClassName);
      if (classIsNew)
      {
        changeResult.setAdminActionRequired (true);
      }
      return changeResult;
    }

    // New entry cache is enabled and there were no previous one.
    // Instantiate the new class and initialize it.
    try
    {
      loadAndInstallEntryCache (newClassName, configuration);
    }
    catch (InitializationException ie)
    {
      changeResult.addMessage (ie.getMessageObject());
      changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
      return changeResult;
    }

    return changeResult;
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      EntryCacheCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    // Check if there is another entry cache installed at the same level.
    if (!cacheOrderMap.isEmpty()
        && cacheOrderMap.containsKey(configuration.getCacheLevel()))
    {
      unacceptableReasons.add(ERR_CONFIG_ENTRYCACHE_CONFIG_LEVEL_NOT_ACCEPTABLE.get(
          configuration.dn(), configuration.getCacheLevel()));
      return false;
    }

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // an entry cache.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadEntryCache(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        return false;
      }
    }

    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(EntryCacheCfg configuration)
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // Register a change listener with it so we can be notified of changes
    // to it over time.
    configuration.addChangeListener(this);

    if (configuration.isEnabled())
    {
      // Instantiate the class as an entry cache and initialize it.
      String className = configuration.getJavaClass();
      try
      {
        loadAndInstallEntryCache (className, configuration);
      }
      catch (InitializationException ie)
      {
        changeResult.addMessage (ie.getMessageObject());
        changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
        return changeResult;
      }
    }

    return changeResult;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
      EntryCacheCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration, then
    // the entry cache itself will make that determination.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
      EntryCacheCfg configuration
      )
  {
    EntryCache<? extends EntryCacheCfg> entryCache = null;

    // If we this entry cache is already installed and active it
    // should be present in the current cache order map, use it.
    if (!cacheOrderMap.isEmpty()) {
      entryCache = cacheOrderMap.get(configuration.getCacheLevel());
    }

    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // If the entry cache was installed then remove it.
    if (entryCache != null)
    {
      EntryCacheMonitorProvider monitor = entryCache.getEntryCacheMonitor();
      if (monitor != null)
      {
        DirectoryServer.deregisterMonitorProvider(monitor);
        monitor.finalizeMonitorProvider();
        entryCache.setEntryCacheMonitor(null);
      }
      entryCache.finalizeEntryCache();
      cacheOrderMap.remove(configuration.getCacheLevel());
      cacheNameToLevelMap.remove(configuration.dn().toNormalizedByteString());

      // Push any changes made to the cache order map.
      setCacheOrder(cacheOrderMap);

      entryCache = null;
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
    EntryCache<? extends EntryCacheCfg> entryCache =
      loadEntryCache (className, configuration, true);

    // ... and install the entry cache in the server.

    // Add this entry cache to the current cache config maps.
    cacheOrderMap.put(configuration.getCacheLevel(), entryCache);
    cacheNameToLevelMap.put(configuration.dn(), configuration.getCacheLevel());

    // Push any changes made to the cache order map.
    setCacheOrder(cacheOrderMap);

    // Install and register the monitor for this cache.
    EntryCacheMonitorProvider monitor =
        new EntryCacheMonitorProvider(configuration.dn().
            rdn().getFirstAVA().getAttributeValue().toString(), entryCache);
    try {
      RootCfg rootConfiguration = serverContext.getRootConfig();
      monitor.initializeMonitorProvider((EntryCacheMonitorProviderCfg)
        rootConfiguration.getMonitorProvider(DEFAULT_ENTRY_CACHE_MONITOR_PROVIDER));
    } catch (ConfigException ce) {
      // ConfigException here means that either the entry cache monitor
      // config entry is not present or the monitor is not enabled. In
      // either case that means no monitor provider for this cache.
      return;
    }
    entryCache.setEntryCacheMonitor(monitor);
    DirectoryServer.registerMonitorProvider(monitor);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void setCacheOrder(SortedMap<Integer, EntryCache> cacheOrderMap)
  {
    _defaultEntryCache.setCacheOrder((SortedMap) cacheOrderMap);
  }

  /**
   * Loads the specified class, instantiates it as an entry cache, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the entry cache class
   *                        to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the entry
   *                        cache.  It must not be {@code null}.
   * @param  initialize     Indicates whether the entry cache instance should be
   *                        initialized.
   *
   * @return  The possibly initialized entry cache.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the entry cache.
   */
  private <T extends EntryCacheCfg> EntryCache<T> loadEntryCache(
    String        className,
    T configuration,
    boolean initialize
    )
    throws InitializationException
  {
    // If we this entry cache is already installed and active it
    // should be present in the current cache order map, use it.
    EntryCache<T> entryCache = null;
    if (!cacheOrderMap.isEmpty()) {
      entryCache = cacheOrderMap.get(configuration.getCacheLevel());
    }

    try
    {
      EntryCacheCfgDefn definition = EntryCacheCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition = definition
          .getJavaClassPropertyDefinition();
      @SuppressWarnings("unchecked")
      Class<? extends EntryCache<T>> cacheClass =
          (Class<? extends EntryCache<T>>) propertyDefinition
              .loadClass(className, EntryCache.class);

      // If there is some entry cache instance already initialized work with
      // it instead of creating a new one unless explicit init is requested.
      EntryCache<T> cache;
      if (initialize || entryCache == null) {
        cache = cacheClass.newInstance();
      } else {
        cache = entryCache;
      }

      if (initialize)
      {
        cache.initializeEntryCache(configuration);
      }
      // This will check if configuration is acceptable on disabled
      // and uninitialized cache instance that has no "acceptable"
      // change listener registered to invoke and verify on its own.
      else if (!configuration.isEnabled())
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!cache.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String buffer = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_ENTRYCACHE_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), buffer));
        }
      }

      return cache;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      if (!initialize) {
        if (e instanceof InitializationException) {
          throw (InitializationException) e;
        } else {
          LocalizableMessage message = ERR_CONFIG_ENTRYCACHE_CONFIG_NOT_ACCEPTABLE.get(
            configuration.dn(), e.getCause() != null ?
              e.getCause().getMessage() : stackTraceToSingleLineString(e));
          throw new InitializationException(message);
        }
      }
      LocalizableMessage message = ERR_CONFIG_ENTRYCACHE_CANNOT_INITIALIZE_CACHE.get(
        className, e.getCause() != null ? e.getCause().getMessage() :
          stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}
