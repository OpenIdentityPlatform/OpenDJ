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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.EntryCacheCfg;
import org.forgerock.opendj.server.config.server.SoftReferenceEntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.EntryCache;
import org.opends.server.api.MonitorData;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.CacheEntry;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.ServerConstants;

/**
 * This class defines a Directory Server entry cache that uses soft references
 * to manage objects in a way that will allow them to be freed if the JVM is
 * running low on memory.
 */
public class SoftReferenceEntryCache
    extends EntryCache <SoftReferenceEntryCacheCfg>
    implements
        ConfigurationChangeListener<SoftReferenceEntryCacheCfg>,
        Runnable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The mapping between entry DNs and their corresponding entries. */
  private ConcurrentMap<DN, Reference<CacheEntry>> dnMap;

  /** The mapping between backend+ID and their corresponding entries. */
  private ConcurrentMap<String, ConcurrentMap<Long, Reference<CacheEntry>>> idMap;

  /** The reference queue that will be used to notify us whenever a soft reference is freed. */
  private ReferenceQueue<CacheEntry> referenceQueue;

  /** Currently registered configuration object. */
  private SoftReferenceEntryCacheCfg registeredConfiguration;

  private Thread cleanerThread;
  private volatile boolean shutdown;

  /**
   * Creates a new instance of this soft reference entry cache.  All
   * initialization should be performed in the <CODE>initializeEntryCache</CODE>
   * method.
   */
  public SoftReferenceEntryCache()
  {
    super();

    dnMap = new ConcurrentHashMap<>();
    idMap = new ConcurrentHashMap<>();

    setExcludeFilters(new HashSet<SearchFilter>());
    setIncludeFilters(new HashSet<SearchFilter>());
    referenceQueue = new ReferenceQueue<>();
  }

  @Override
  public void initializeEntryCache(
      SoftReferenceEntryCacheCfg configuration
      )
      throws ConfigException, InitializationException
  {
    cleanerThread = new DirectoryThread(this,
        "Soft Reference Entry Cache Cleaner");
    cleanerThread.setDaemon(true);
    cleanerThread.start();

    registeredConfiguration = configuration;
    configuration.addSoftReferenceChangeListener (this);

    dnMap.clear();
    idMap.clear();

    // Read configuration and apply changes.
    boolean applyChanges = true;
    List<LocalizableMessage> errorMessages = new ArrayList<>();
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_INIT, null, errorMessages
          );
    if (!processEntryCacheConfig(configuration, applyChanges, errorHandler)) {
      String buffer = Utils.joinAsString(".  ", errorMessages);
      throw new ConfigException(ERR_SOFTREFCACHE_CANNOT_INITIALIZE.get(buffer));
    }
  }

  @Override
  public synchronized void finalizeEntryCache()
  {
    registeredConfiguration.removeSoftReferenceChangeListener (this);

    shutdown = true;

    dnMap.clear();
    idMap.clear();
    if (cleanerThread != null) {
      for (int i = 0; cleanerThread.isAlive() && i < 5; i++) {
        cleanerThread.interrupt();
        try {
          cleanerThread.join(10);
        } catch (InterruptedException e) {
          // We'll exit eventually.
        }
      }
      cleanerThread = null;
    }
  }

  @Override
  public boolean containsEntry(DN entryDN)
  {
    return entryDN != null && dnMap.containsKey(entryDN);
  }

  @Override
  public Entry getEntry(DN entryDN)
  {
    Reference<CacheEntry> ref = dnMap.get(entryDN);
    if (ref == null)
    {
      // Indicate cache miss.
      cacheMisses.getAndIncrement();
      return null;
    }
    CacheEntry cacheEntry = ref.get();
    if (cacheEntry == null)
    {
      // Indicate cache miss.
      cacheMisses.getAndIncrement();
      return null;
    }
    // Indicate cache hit.
    cacheHits.getAndIncrement();
    return cacheEntry.getEntry();
  }

  @Override
  public long getEntryID(DN entryDN)
  {
    Reference<CacheEntry> ref = dnMap.get(entryDN);
    if (ref != null)
    {
      CacheEntry cacheEntry = ref.get();
      return cacheEntry != null ? cacheEntry.getEntryID() : -1;
    }
    return -1;
  }

  @Override
  public DN getEntryDN(String backendID, long entryID)
  {
    // Locate specific backend map and return the entry DN by ID.
    ConcurrentMap<Long, Reference<CacheEntry>> backendMap = idMap.get(backendID);
    if (backendMap != null) {
      Reference<CacheEntry> ref = backendMap.get(entryID);
      if (ref != null) {
        CacheEntry cacheEntry = ref.get();
        if (cacheEntry != null) {
          return cacheEntry.getDN();
        }
      }
    }
    return null;
  }

  @Override
  public void putEntry(Entry entry, String backendID, long entryID)
  {
    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backendID, entryID);
    Reference<CacheEntry> ref = new SoftReference<>(cacheEntry, referenceQueue);

    Reference<CacheEntry> oldRef = dnMap.put(entry.getName(), ref);
    if (oldRef != null)
    {
      oldRef.clear();
    }

    ConcurrentMap<Long,Reference<CacheEntry>> map = idMap.get(backendID);
    if (map == null)
    {
      map = new ConcurrentHashMap<>();
      map.put(entryID, ref);
      idMap.put(backendID, map);
    }
    else
    {
      oldRef = map.put(entryID, ref);
      if (oldRef != null)
      {
        oldRef.clear();
      }
    }
  }

  @Override
  public boolean putEntryIfAbsent(Entry entry, String backendID, long entryID)
  {
    // See if the entry already exists.  If so, then return false.
    if (dnMap.containsKey(entry.getName()))
    {
      return false;
    }

    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backendID, entryID);
    Reference<CacheEntry> ref = new SoftReference<>(cacheEntry, referenceQueue);

    dnMap.put(entry.getName(), ref);

    ConcurrentMap<Long,Reference<CacheEntry>> map = idMap.get(backendID);
    if (map == null)
    {
      map = new ConcurrentHashMap<>();
      map.put(entryID, ref);
      idMap.put(backendID, map);
    }
    else
    {
      map.put(entryID, ref);
    }

    return true;
  }

  @Override
  public void removeEntry(DN entryDN)
  {
    Reference<CacheEntry> ref = dnMap.remove(entryDN);
    if (ref != null)
    {
      ref.clear();

      CacheEntry cacheEntry = ref.get();
      if (cacheEntry != null)
      {
        final String backendID = cacheEntry.getBackendID();

        ConcurrentMap<Long, Reference<CacheEntry>> map = idMap.get(backendID);
        if (map != null)
        {
          ref = map.remove(cacheEntry.getEntryID());
          if (ref != null)
          {
            ref.clear();
          }
          // If this backend becomes empty now remove
          // it from the idMap map.
          if (map.isEmpty())
          {
            idMap.remove(backendID);
          }
        }
      }
    }
  }

  @Override
  public void clear()
  {
    dnMap.clear();
    idMap.clear();
  }

  @Override
  public void clearBackend(String backendID)
  {
    // FIXME -- Would it be better just to dump everything?
    final ConcurrentMap<Long, Reference<CacheEntry>> map = idMap.remove(backendID);
    if (map != null)
    {
      for (Reference<CacheEntry> ref : map.values())
      {
        final CacheEntry cacheEntry = ref.get();
        if (cacheEntry != null)
        {
          dnMap.remove(cacheEntry.getDN());
        }

        ref.clear();
      }

      map.clear();
    }
  }

  @Override
  public void clearSubtree(DN baseDN)
  {
    // Determine the backend used to hold the specified base DN and clear it.
    Backend<?> backend = DirectoryServer.getBackend(baseDN);
    if (backend == null)
    {
      // FIXME -- Should we clear everything just to be safe?
    }
    else
    {
      clearBackend(backend.getBackendID());
    }
  }

  @Override
  public void handleLowMemory()
  {
    // This function should automatically be taken care of by the nature of the
    // soft references used in this cache.
    // FIXME -- Do we need to do anything at all here?
  }

  @Override
  public boolean isConfigurationAcceptable(EntryCacheCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    SoftReferenceEntryCacheCfg config =
         (SoftReferenceEntryCacheCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      SoftReferenceEntryCacheCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    boolean applyChanges = false;
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_ACCEPTABLE,
          unacceptableReasons,
          null
        );
    processEntryCacheConfig (configuration, applyChanges, errorHandler);

    return errorHandler.getIsAcceptable();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(SoftReferenceEntryCacheCfg configuration)
  {
    boolean applyChanges = true;
    List<LocalizableMessage> errorMessages = new ArrayList<>();
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_APPLY, null, errorMessages
          );
    // Do not apply changes unless this cache is enabled.
    if (configuration.isEnabled()) {
      processEntryCacheConfig (configuration, applyChanges, errorHandler);
    }

    final ConfigChangeResult changeResult = new ConfigChangeResult();
    changeResult.setResultCode(errorHandler.getResultCode());
    changeResult.setAdminActionRequired(errorHandler.getIsAdminActionRequired());
    changeResult.getMessages().addAll(errorHandler.getErrorMessages());
    return changeResult;
  }

  /**
   * Parses the provided configuration and configure the entry cache.
   *
   * @param configuration  The new configuration containing the changes.
   * @param applyChanges   If true then take into account the new configuration.
   * @param errorHandler   An handler used to report errors.
   *
   * @return  <CODE>true</CODE> if configuration is acceptable,
   *          or <CODE>false</CODE> otherwise.
   */
  public boolean processEntryCacheConfig(
      SoftReferenceEntryCacheCfg          configuration,
      boolean                             applyChanges,
      EntryCacheCommon.ConfigErrorHandler errorHandler
      )
  {
    // Local variables to read configuration.
    DN newConfigEntryDN;
    Set<SearchFilter> newIncludeFilters = null;
    Set<SearchFilter> newExcludeFilters = null;

    // Read configuration.
    newConfigEntryDN = configuration.dn();

    // Get include and exclude filters.
    switch (errorHandler.getConfigPhase())
    {
    case PHASE_INIT:
    case PHASE_ACCEPTABLE:
    case PHASE_APPLY:
      newIncludeFilters = EntryCacheCommon.getFilters (
          configuration.getIncludeFilter(),
          ERR_CACHE_INVALID_INCLUDE_FILTER,
          errorHandler,
          newConfigEntryDN
          );
      newExcludeFilters = EntryCacheCommon.getFilters (
          configuration.getExcludeFilter(),
          ERR_CACHE_INVALID_EXCLUDE_FILTER,
          errorHandler,
          newConfigEntryDN
          );
      break;
    }

    if (applyChanges && errorHandler.getIsAcceptable())
    {
      setIncludeFilters(newIncludeFilters);
      setExcludeFilters(newExcludeFilters);

      registeredConfiguration = configuration;
    }

    return errorHandler.getIsAcceptable();
  }

  /**
   * Operate in a loop, receiving notification of soft references that have been
   * freed and removing the corresponding entries from the cache.
   */
  @Override
  public void run()
  {
    while (!shutdown)
    {
      try
      {
        CacheEntry freedEntry = referenceQueue.remove().get();

        if (freedEntry != null)
        {
          Reference<CacheEntry> ref = dnMap.remove(freedEntry.getDN());

          if (ref != null)
          {
            // Note that the entry is there, but it could be a newer version of
            // the entry so we want to make sure it's the same one.
            CacheEntry removedEntry = ref.get();
            if (removedEntry != freedEntry)
            {
              dnMap.putIfAbsent(freedEntry.getDN(), ref);
            }
            else
            {
              ref.clear();

              final String backendID = freedEntry.getBackendID();
              final ConcurrentMap<Long, Reference<CacheEntry>> map = idMap.get(backendID);
              if (map != null)
              {
                ref = map.remove(freedEntry.getEntryID());
                if (ref != null)
                {
                  ref.clear();
                }
                // If this backend becomes empty now remove
                // it from the idMap map.
                if (map.isEmpty()) {
                  idMap.remove(backendID);
                }
              }
            }
          }
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  @Override
  public MonitorData getMonitorData()
  {
    try {
      return EntryCacheCommon.getGenericMonitorData(
        cacheHits.longValue(),
        // If cache misses is maintained by default cache
        // get it from there and if not point to itself.
        DirectoryServer.getEntryCache().getCacheMisses(),
        null,
        null,
        Long.valueOf(dnMap.size()),
        null
        );
    } catch (Exception e) {
      logger.traceException(e);
      return new MonitorData(0);
    }
  }

  @Override
  public Long getCacheCount()
  {
    return Long.valueOf(dnMap.size());
  }

  @Override
  public String toVerboseString()
  {
    StringBuilder sb = new StringBuilder();

    // There're no locks in this cache to keep dnMap and idMap in sync.
    // Examine dnMap only since its more likely to be up to date than idMap.
    // Do not bother with copies either since this
    // is SoftReference based implementation.
    for(Reference<CacheEntry> ce : dnMap.values()) {
      sb.append(ce.get().getDN());
      sb.append(":");
      sb.append(ce.get().getEntryID());
      sb.append(":");
      sb.append(ce.get().getBackendID());
      sb.append(ServerConstants.EOL);
    }

    String verboseString = sb.toString();
    return verboseString.length() > 0 ? verboseString : null;
  }
}
