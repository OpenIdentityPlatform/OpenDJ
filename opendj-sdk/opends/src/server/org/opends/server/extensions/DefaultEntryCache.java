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
package org.opends.server.extensions;
import java.lang.reflect.Method;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.locks.Lock;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockType;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class defines the default entry cache which acts as an arbiter for
 * every entry cache implementation configured and installed within the
 * Directory Server or acts an an empty cache if no implementation specific
 * entry cache is configured.  It does not actually store any entries, so
 * all calls to the entry cache public API are routed to underlying entry
 * cache according to the current configuration order and preferences.
 */
public class DefaultEntryCache
       extends EntryCache<EntryCacheCfg>
       implements ConfigurationChangeListener<EntryCacheCfg>,
       BackendInitializationListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  // The entry cache order array reflects all currently configured and
  // active entry cache implementations in cache level specific order.
  private static EntryCache<? extends EntryCacheCfg>[] cacheOrder =
    new EntryCache<?>[0];


  /**
   * Creates a new instance of this default entry cache.
   */
  public DefaultEntryCache()
  {
    super();

    // Register with backend initialization listener to clear cache
    // entries belonging to given backend that about to go offline.
    DirectoryServer.registerBackendInitializationListener(this);
  }


  /**
   * {@inheritDoc}
   */
  public void initializeEntryCache(EntryCacheCfg configEntry)
         throws ConfigException, InitializationException
  {
    // No implementation required.
  }


  /**
   * {@inheritDoc}
   */
  public void finalizeEntryCache()
  {
    // No implementation required.
  }


  /**
   * {@inheritDoc}
   */
  public boolean containsEntry(DN entryDN)
  {
    if (entryDN == null) {
      return false;
    }

    for (EntryCache entryCache : cacheOrder) {
      if (entryCache.containsEntry(entryDN)) {
        return true;
      }
    }

    return false;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getEntry(DN entryDN,
                        LockType lockType,
                        List<Lock> lockList)
  {
    Entry entry = null;

    for (EntryCache<? extends EntryCacheCfg> entryCache : cacheOrder) {
      entry = entryCache.getEntry(entryDN, lockType, lockList);
      if (entry != null) {
        break;
      }
    }

    // Indicate global cache miss.
    if ((entry == null) && (cacheOrder.length != 0)) {
      cacheMisses.getAndIncrement();
    }

    return entry;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getEntry(Backend backend, long entryID,
                                 LockType lockType,
                                 List<Lock> lockList)
  {
    Entry entry = null;

    for (EntryCache<? extends EntryCacheCfg> entryCache : cacheOrder) {
      entry = entryCache.getEntry(backend, entryID, lockType,
          lockList);
      if (entry != null) {
        break;
      }
    }

    // Indicate global cache miss.
    if ((entry == null) && (cacheOrder.length != 0)) {
      cacheMisses.getAndIncrement();
    }

    return entry;
  }


  /**
   * {@inheritDoc}
   */
  public Entry getEntry(DN entryDN)
  {
    Entry entry = null;

    for (EntryCache entryCache : cacheOrder) {
      entry = entryCache.getEntry(entryDN);
      if (entry != null) {
        break;
      }
    }

    // Indicate global cache miss.
    if ((entry == null) && (cacheOrder.length != 0)) {
      cacheMisses.getAndIncrement();
    }

    return entry;
  }



  /**
   * {@inheritDoc}
   */
  public long getEntryID(DN entryDN)
  {
    long entryID = -1;

    for (EntryCache entryCache : cacheOrder) {
      entryID = entryCache.getEntryID(entryDN);
      if (entryID != -1) {
        break;
      }
    }

    return entryID;
  }



  /**
   * {@inheritDoc}
   */
  public DN getEntryDN(Backend backend, long entryID)
  {
    DN entryDN = null;

    for (EntryCache entryCache : cacheOrder) {
      entryDN = entryCache.getEntryDN(backend, entryID);
      if (entryDN != null) {
        break;
      }
    }

    return entryDN;
  }



  /**
   * {@inheritDoc}
   */
  public void putEntry(Entry entry, Backend backend, long entryID)
  {
    for (EntryCache entryCache : cacheOrder) {
      // The first cache in the order which can take this entry
      // gets it.
      if (entryCache.filtersAllowCaching(entry)) {
        entryCache.putEntry(entry, backend, entryID);
        break;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean putEntryIfAbsent(Entry entry, Backend backend, long entryID)
  {
    for (EntryCache entryCache : cacheOrder) {
      // The first cache in the order which can take this entry
      // gets it.
      if (entryCache.filtersAllowCaching(entry)) {
        return entryCache.putEntryIfAbsent(entry, backend, entryID);
      }
    }

    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void removeEntry(DN entryDN)
  {
    for (EntryCache entryCache : cacheOrder) {
      if (entryCache.containsEntry(entryDN)) {
        entryCache.removeEntry(entryDN);
        break;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public void clear()
  {
    for (EntryCache entryCache : cacheOrder) {
      entryCache.clear();
    }
  }



  /**
   * {@inheritDoc}
   */
  public void clearBackend(Backend backend)
  {
    for (EntryCache entryCache : cacheOrder) {
      entryCache.clearBackend(backend);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void clearSubtree(DN baseDN)
  {
    for (EntryCache entryCache : cacheOrder) {
      entryCache.clearSubtree(baseDN);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void handleLowMemory()
  {
    for (EntryCache entryCache : cacheOrder) {
      entryCache.handleLowMemory();
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      EntryCacheCfg configuration,
      List<Message> unacceptableReasons
      )
  {
    // No implementation required.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      EntryCacheCfg configuration
      )
  {
    // No implementation required.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>();

    // The sum of cache hits of all active entry cache
    // implementations.
    Long entryCacheHits = new Long(0);
    // Common for all active entry cache implementations.
    Long entryCacheMisses = new Long(cacheMisses.longValue());
    // The sum of cache counts of all active entry cache
    // implementations.
    Long currentEntryCacheCount = new Long(0);

    for (EntryCache entryCache : cacheOrder) {
      // Get cache hits and counts from every active cache.
      entryCacheHits += entryCache.getCacheHits();
      currentEntryCacheCount += entryCache.getCacheCount();
    }

    try {
      attrs = EntryCacheCommon.getGenericMonitorData(
        entryCacheHits,
        entryCacheMisses,
        null,
        null,
        currentEntryCacheCount,
        null
        );
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return attrs;
  }



  /**
   * {@inheritDoc}
   */
  public Long getCacheCount()
  {
    Long cacheCount = new Long(0);

    for (EntryCache entryCache : cacheOrder) {
      cacheCount += entryCache.getCacheCount();
    }

    return cacheCount;
  }



  /**
   * Return a verbose string representation of the current cache maps.
   * This is useful primary for debugging and diagnostic purposes such
   * as in the entry cache unit tests.
   * @return String verbose string representation of the current cache
   *                maps in the following format: dn:id:backend
   *                one cache entry map representation per line
   *                or <CODE>null</CODE> if all maps are empty.
   */
  private String toVerboseString()
  {
    String verboseString = new String();
    StringBuilder sb = new StringBuilder();

    for (EntryCache entryCache : cacheOrder) {
      final Method[] cacheMethods =
        entryCache.getClass().getDeclaredMethods();
      for (int i = 0; i < cacheMethods.length; ++i) {
        if (cacheMethods[i].getName().equals("toVerboseString")) {
          cacheMethods[i].setAccessible(true);
          try {
            Object cacheVerboseString =
              cacheMethods[i].invoke(entryCache, (Object[]) null);
            if (cacheVerboseString != null) {
              sb.append((String) cacheVerboseString);
            }
          } catch (Exception e) {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }

    verboseString = sb.toString();

    return (verboseString.length() > 0 ? verboseString : null);
  }



  /**
   * Retrieves the current cache order array.
   *
   * @return  The current cache order array.
   */
  public final EntryCache<? extends EntryCacheCfg>[] getCacheOrder()
  {
    return this.cacheOrder;
  }



  /**
   * Sets the current cache order array.
   *
   * @param  cacheOrderMap  The current cache order array.
   */
  public final void setCacheOrder(
    SortedMap<Integer,
    EntryCache<? extends EntryCacheCfg>> cacheOrderMap)
  {
    this.cacheOrder =
      cacheOrderMap.values().toArray(new EntryCache<?>[0]);
  }



  /**
   * Performs any processing that may be required whenever a backend
   * is initialized for use in the Directory Server.  This method will
   * be invoked after the backend has been initialized but before it
   * has been put into service.
   *
   * @param  backend  The backend that has been initialized and is
   *                  about to be put into service.
   */
  public void performBackendInitializationProcessing(Backend backend)
  {
    // Do nothing.
  }



  /**
   * Performs any processing that may be required whenever a backend
   * is finalized.  This method will be invoked after the backend has
   * been taken out of service but before it has been finalized.
   *
   * @param  backend  The backend that has been taken out of service
   *                  and is about to be finalized.
   */
  public void performBackendFinalizationProcessing(Backend backend)
  {
    // Do not clear any backends if the server is shutting down.
    if ( !(DirectoryServer.getInstance().isShuttingDown()) ) {
      clearBackend(backend);
    }
  }
}

