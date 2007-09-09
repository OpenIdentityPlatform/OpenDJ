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
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.FIFOEntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CacheEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.Attribute;
import org.opends.server.util.ServerConstants;
import org.opends.messages.MessageBuilder;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a Directory Server entry cache that uses a FIFO to keep
 * track of the entries.  Entries that have been in the cache the longest are
 * the most likely candidates for purging if space is needed.  In contrast to
 * other cache structures, the selection of entries to purge is not based on
 * how frequently or recently the entries have been accessed.  This requires
 * significantly less locking (it will only be required when an entry is added
 * or removed from the cache, rather than each time an entry is accessed).
 * <BR><BR>
 * Cache sizing is based on the percentage of free memory within the JVM, such
 * that if enough memory is free, then adding an entry to the cache will not
 * require purging, but if more than a specified percentage of the available
 * memory within the JVM is already consumed, then one or more entries will need
 * to be removed in order to make room for a new entry.  It is also possible to
 * configure a maximum number of entries for the cache.  If this is specified,
 * then the number of entries will not be allowed to exceed this value, but it
 * may not be possible to hold this many entries if the available memory fills
 * up first.
 * <BR><BR>
 * Other configurable parameters for this cache include the maximum length of
 * time to block while waiting to acquire a lock, and a set of filters that may
 * be used to define criteria for determining which entries are stored in the
 * cache.  If a filter list is provided, then only entries matching at least one
 * of the given filters will be stored in the cache.
 */
public class FIFOEntryCache
       extends EntryCache <FIFOEntryCacheCfg>
       implements ConfigurationChangeListener<FIFOEntryCacheCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The reference to the Java runtime used to determine the amount of memory
   * currently in use.
   */
  private static final Runtime runtime = Runtime.getRuntime();

  // The mapping between entry backends/IDs and entries.
  private HashMap<Backend,HashMap<Long,CacheEntry>> idMap;

  // The mapping between DNs and entries.
  private LinkedHashMap<DN,CacheEntry> dnMap;

  // The lock used to provide threadsafe access when changing the contents of
  // the cache.
  private Lock cacheLock;

  // The maximum amount of memory in bytes that the JVM will be allowed to use
  // before we need to start purging entries.
  private long maxAllowedMemory;

  // The maximum number of entries that may be held in the cache.
  private long maxEntries;

  // Currently registered configuration object.
  private FIFOEntryCacheCfg registeredConfiguration;



  /**
   * Creates a new instance of this FIFO entry cache.
   */
  public FIFOEntryCache()
  {
    super();


    // All initialization should be performed in the initializeEntryCache.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeEntryCache(
      FIFOEntryCacheCfg configuration
      )
      throws ConfigException, InitializationException
  {
    registeredConfiguration = configuration;
    configuration.addFIFOChangeListener (this);

    // Initialize the cache structures.
    idMap     = new HashMap<Backend,HashMap<Long,CacheEntry>>();
    dnMap     = new LinkedHashMap<DN,CacheEntry>();
    cacheLock = new ReentrantLock();


    // Read configuration and apply changes.
    boolean applyChanges = true;
    ArrayList<Message> errorMessages = new ArrayList<Message>();
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_INIT, null, errorMessages
          );
    if (!processEntryCacheConfig(configuration, applyChanges, errorHandler)) {
      MessageBuilder buffer = new MessageBuilder();
      if (!errorMessages.isEmpty()) {
        Iterator<Message> iterator = errorMessages.iterator();
        buffer.append(iterator.next());
        while (iterator.hasNext()) {
          buffer.append(".  ");
          buffer.append(iterator.next());
        }
      }
      Message message = ERR_FIFOCACHE_CANNOT_INITIALIZE.get(buffer.toString());
      throw new ConfigException(message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeEntryCache()
  {
    cacheLock.lock();

    registeredConfiguration.removeFIFOChangeListener (this);

    // Release all memory currently in use by this cache.
    try
    {
      idMap.clear();
      dnMap.clear();
    }
    catch (Exception e)
    {
      // This should never happen.
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsEntry(DN entryDN)
  {
    // Indicate whether the DN map contains the specified DN.
    return dnMap.containsKey(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  public Entry getEntry(DN entryDN)
  {
    // Simply return the entry from the DN map.
    CacheEntry e = dnMap.get(entryDN);
    if (e == null)
    {
      // Indicate cache miss.
      cacheMisses.set(cacheMisses.incrementAndGet());
      return null;
    }
    else
    {
      // Indicate cache hit.
      cacheHits.set(cacheHits.incrementAndGet());
      return e.getEntry();
    }
  }



  /**
   * {@inheritDoc}
   */
  public long getEntryID(DN entryDN)
  {
    // Simply return the ID from the DN map.
    CacheEntry e = dnMap.get(entryDN);
    if (e == null)
    {
      return -1;
    }
    else
    {
      return e.getEntryID();
    }
  }



  /**
   * {@inheritDoc}
   */
  protected DN getEntryDN(Backend backend, long entryID)
  {
    // Locate specific backend map and return the entry DN by ID.
    HashMap<Long,CacheEntry> backendMap = idMap.get(backend);
    if (backendMap != null) {
      CacheEntry e = backendMap.get(entryID);
      if (e != null) {
        return e.getDN();
      }
    }
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public void putEntry(Entry entry, Backend backend, long entryID)
  {
    // Check exclude and include filters first.
    if (!filtersAllowCaching(entry)) {
      return;
    }

    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backend, entryID);


    // Obtain a lock on the cache.  If this fails, then don't do anything.
    try
    {
      if (! cacheLock.tryLock(getLockTimeout(), TimeUnit.MILLISECONDS))
      {
        return;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return;
    }


    // At this point, we hold the lock.  No matter what, we must release the
    // lock before leaving this method, so do that in a finally block.
    try
    {
      // See if the current memory usage is within acceptable constraints.  If
      // so, then add the entry to the cache (or replace it if it is already
      // present).  If not, then remove an existing entry and don't add the new
      // entry.
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      if (usedMemory > maxAllowedMemory)
      {
        Iterator<CacheEntry> iterator = dnMap.values().iterator();
        if (iterator.hasNext())
        {
          CacheEntry ce = iterator.next();
          iterator.remove();

          HashMap<Long,CacheEntry> m = idMap.get(ce.getBackend());
          if (m != null)
          {
            m.remove(ce.getEntryID());
          }
        }
      }
      else
      {
        // Add the entry to the cache.  This will replace it if it is already
        // present and add it if it isn't.
        dnMap.put(entry.getDN(), cacheEntry);

        HashMap<Long,CacheEntry> map = idMap.get(backend);
        if (map == null)
        {
          map = new HashMap<Long,CacheEntry>();
          map.put(entryID, cacheEntry);
          idMap.put(backend, map);
        }
        else
        {
          map.put(entryID, cacheEntry);
        }


        // See if a cap has been placed on the maximum number of entries in the
        // cache.  If so, then see if we have exceeded it and we need to purge
        // entries until we're within the limit.
        int entryCount = dnMap.size();
        if ((maxEntries > 0) && (entryCount > maxEntries))
        {
          Iterator<CacheEntry> iterator = dnMap.values().iterator();
          while (iterator.hasNext() && (entryCount > maxEntries))
          {
            CacheEntry ce = iterator.next();
            iterator.remove();

            HashMap<Long,CacheEntry> m = idMap.get(ce.getBackend());
            if (m != null)
            {
              m.remove(ce.getEntryID());
            }

            entryCount--;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return;
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean putEntryIfAbsent(Entry entry, Backend backend, long entryID)
  {
    // Check exclude and include filters first.
    if (!filtersAllowCaching(entry)) {
      return true;
    }

    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backend, entryID);


    // Obtain a lock on the cache.  If this fails, then don't do anything.
    try
    {
      if (! cacheLock.tryLock(getLockTimeout(), TimeUnit.MILLISECONDS))
      {
        // We can't rule out the possibility of a conflict, so return false.
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // We can't rule out the possibility of a conflict, so return false.
      return false;
    }


    // At this point, we hold the lock.  No matter what, we must release the
    // lock before leaving this method, so do that in a finally block.
    try
    {
      // See if the entry already exists in the cache.  If it does, then we will
      // fail and not actually store the entry.
      if (dnMap.containsKey(entry.getDN()))
      {
        return false;
      }

      // See if the current memory usage is within acceptable constraints.  If
      // so, then add the entry to the cache (or replace it if it is already
      // present).  If not, then remove an existing entry and don't add the new
      // entry.
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      if (usedMemory > maxAllowedMemory)
      {
        Iterator<CacheEntry> iterator = dnMap.values().iterator();
        if (iterator.hasNext())
        {
          CacheEntry ce = iterator.next();
          iterator.remove();

          HashMap<Long,CacheEntry> m = idMap.get(ce.getBackend());
          if (m != null)
          {
            m.remove(ce.getEntryID());
          }
        }
      }
      else
      {
        // Add the entry to the cache.  This will replace it if it is already
        // present and add it if it isn't.
        dnMap.put(entry.getDN(), cacheEntry);

        HashMap<Long,CacheEntry> map = idMap.get(backend);
        if (map == null)
        {
          map = new HashMap<Long,CacheEntry>();
          map.put(entryID, cacheEntry);
          idMap.put(backend, map);
        }
        else
        {
          map.put(entryID, cacheEntry);
        }


        // See if a cap has been placed on the maximum number of entries in the
        // cache.  If so, then see if we have exceeded it and we need to purge
        // entries until we're within the limit.
        int entryCount = dnMap.size();
        if ((maxEntries > 0) && (entryCount > maxEntries))
        {
          Iterator<CacheEntry> iterator = dnMap.values().iterator();
          while (iterator.hasNext() && (entryCount > maxEntries))
          {
            CacheEntry ce = iterator.next();
            iterator.remove();

            HashMap<Long,CacheEntry> m = idMap.get(ce.getBackend());
            if (m != null)
            {
              m.remove(ce.getEntryID());
            }

            entryCount--;
          }
        }
      }


      // We'll always return true in this case, even if we didn't actually add
      // the entry due to memory constraints.
      return true;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // We can't be sure there wasn't a conflict, so return false.
      return false;
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  public void removeEntry(DN entryDN)
  {
    // Acquire the lock on the cache.  We should not return until the entry is
    // removed, so we will block until we can obtain the lock.
    // FIXME -- An alternate approach could be to block for a maximum length of
    // time and then if it fails then put it in a queue for processing by some
    // other thread before it releases the lock.
    cacheLock.lock();


    // At this point, it is absolutely critical that we always release the lock
    // before leaving this method, so do so in a finally block.
    try
    {
      // Check the DN cache to see if the entry exists.  If not, then don't do
      // anything.
      CacheEntry entry = dnMap.remove(entryDN);
      if (entry == null)
      {
        return;
      }

      Backend backend = entry.getBackend();

      // Try to remove the entry from the ID list as well.
      Map<Long,CacheEntry> map = idMap.get(backend);
      if (map == null)
      {
        // This should't happen, but the entry isn't cached in the ID map so
        // we can return.
        return;
      }

      map.remove(entry.getEntryID());

      // If this backend becomes empty now remove it from the idMap map.
      if (map.isEmpty())
      {
        idMap.remove(backend);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  public void clear()
  {
    // Acquire a lock on the cache.  We should not return until the cache has
    // been cleared, so we will block until we can obtain the lock.
    cacheLock.lock();


    // At this point, it is absolutely critical that we always release the lock
    // before leaving this method, so do so in a finally block.
    try
    {
      // Clear the DN cache.
      dnMap.clear();

      // Clear the ID cache.
      idMap.clear();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  public void clearBackend(Backend backend)
  {
    // Acquire a lock on the cache.  We should not return until the cache has
    // been cleared, so we will block until we can obtain the lock.
    cacheLock.lock();


    // At this point, it is absolutely critical that we always release the lock
    // before leaving this method, so do so in a finally block.
    try
    {
      // Remove all references to entries for this backend from the ID cache.
      HashMap<Long,CacheEntry> map = idMap.remove(backend);
      if (map == null)
      {
        // No entries were in the cache for this backend, so we can return
        // without doing anything.
        return;
      }


      // Unfortunately, there is no good way to dump the entries from the DN
      // cache based on their backend, so we will need to iterate through the
      // entries in the ID map and do it manually.  Since this could take a
      // while, we'll periodically release and re-acquire the lock in case
      // anyone else is waiting on it so this doesn't become a stop-the-world
      // event as far as the cache is concerned.
      int entriesDeleted = 0;
      for (CacheEntry e : map.values())
      {
        dnMap.remove(e.getEntry().getDN());
        entriesDeleted++;

        if ((entriesDeleted % 1000)  == 0)
        {
          cacheLock.unlock();
          Thread.currentThread().yield();
          cacheLock.lock();
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  public void clearSubtree(DN baseDN)
  {
    // Determine which backend should be used for the provided base DN.  If
    // there is none, then we don't need to do anything.
    Backend backend = DirectoryServer.getBackend(baseDN);
    if (backend == null)
    {
      return;
    }


    // Acquire a lock on the cache.  We should not return until the cache has
    // been cleared, so we will block until we can obtain the lock.
    cacheLock.lock();


    // At this point, it is absolutely critical that we always release the lock
    // before leaving this method, so do so in a finally block.
    try
    {
      clearSubtree(baseDN, backend);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  private void clearSubtree(DN baseDN, Backend backend)
  {
    // See if there are any entries for the provided backend in the cache.  If
    // not, then return.
    HashMap<Long,CacheEntry> map = idMap.get(backend);
    if (map == null)
    {
      // No entries were in the cache for this backend, so we can return without
      // doing anything.
      return;
    }


    // Since the provided base DN could hold a subset of the information in the
    // specified backend, we will have to do this by iterating through all the
    // entries for that backend.  Since this could take a while, we'll
    // periodically release and re-acquire the lock in case anyone else is
    // waiting on it so this doesn't become a stop-the-world event as far as the
    // cache is concerned.
    int entriesExamined = 0;
    Iterator<CacheEntry> iterator = map.values().iterator();
    while (iterator.hasNext())
    {
      CacheEntry e = iterator.next();
      DN entryDN = e.getEntry().getDN();
      if (entryDN.isDescendantOf(baseDN))
      {
        iterator.remove();
        dnMap.remove(entryDN);
      }

      entriesExamined++;
      if ((entriesExamined % 1000) == 0)
      {
        cacheLock.unlock();
        Thread.currentThread().yield();
        cacheLock.lock();
      }
    }


    // See if the backend has any subordinate backends.  If so, then process
    // them recursively.
    for (Backend subBackend : backend.getSubordinateBackends())
    {
      boolean isAppropriate = false;
      for (DN subBase : subBackend.getBaseDNs())
      {
        if (subBase.isDescendantOf(baseDN))
        {
          isAppropriate = true;
          break;
        }
      }

      if (isAppropriate)
      {
        clearSubtree(baseDN, subBackend);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public void handleLowMemory()
  {
    // Grab the lock on the cache and wait until we have it.
    cacheLock.lock();


    // At this point, it is absolutely critical that we always release the lock
    // before leaving this method, so do so in a finally block.
    try
    {
      // See how many entries are in the cache.  If there are less than 1000,
      // then we'll dump all of them.  Otherwise, we'll dump 10% of the entries.
      int numEntries = dnMap.size();
      if (numEntries < 1000)
      {
        dnMap.clear();
        idMap.clear();
      }
      else
      {
        int numToDrop = numEntries / 10;
        Iterator<CacheEntry> iterator = dnMap.values().iterator();
        while (iterator.hasNext() && (numToDrop > 0))
        {
          CacheEntry entry = iterator.next();
          iterator.remove();

          HashMap<Long,CacheEntry> m = idMap.get(entry.getBackend());
          if (m != null)
          {
            m.remove(entry.getEntryID());
          }

          numToDrop--;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(EntryCacheCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    FIFOEntryCacheCfg config = (FIFOEntryCacheCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FIFOEntryCacheCfg configuration,
      List<Message> unacceptableReasons
      )
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



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      FIFOEntryCacheCfg configuration
      )
  {
    boolean applyChanges = true;
    ArrayList<Message> errorMessages = new ArrayList<Message>();
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_APPLY, null, errorMessages
          );

    // Do not apply changes unless this cache is enabled.
    if (configuration.isEnabled()) {
      processEntryCacheConfig (configuration, applyChanges, errorHandler);
    }

    boolean adminActionRequired = errorHandler.getIsAdminActionRequired();
    ConfigChangeResult changeResult = new ConfigChangeResult(
        errorHandler.getResultCode(),
        adminActionRequired,
        errorHandler.getErrorMessages()
        );

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
      FIFOEntryCacheCfg                   configuration,
      boolean                             applyChanges,
      EntryCacheCommon.ConfigErrorHandler errorHandler
      )
  {
    // Local variables to read configuration.
    DN                    newConfigEntryDN;
    long                  newLockTimeout;
    long                  newMaxEntries;
    int                   newMaxMemoryPercent;
    long                  newMaxAllowedMemory;
    HashSet<SearchFilter> newIncludeFilters = null;
    HashSet<SearchFilter> newExcludeFilters = null;

    // Read configuration.
    newConfigEntryDN = configuration.dn();
    newLockTimeout   = configuration.getLockTimeout();
    newMaxEntries    = configuration.getMaxEntries();

    // Maximum memory the cache can use.
    newMaxMemoryPercent = configuration.getMaxMemoryPercent();
    long maxJvmHeapSize = Runtime.getRuntime().maxMemory();
    newMaxAllowedMemory = (maxJvmHeapSize / 100) * newMaxMemoryPercent;

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
      maxEntries       = newMaxEntries;
      maxAllowedMemory = newMaxAllowedMemory;

      setLockTimeout(newLockTimeout);
      setIncludeFilters(newIncludeFilters);
      setExcludeFilters(newExcludeFilters);

      registeredConfiguration = configuration;
    }

    return errorHandler.getIsAcceptable();
  }



  /**
   * {@inheritDoc}
   */
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>();

    try {
      attrs = EntryCacheCommon.getGenericMonitorData(
        new Long(cacheHits.longValue()),
        new Long(cacheMisses.longValue()),
        null,
        new Long(maxAllowedMemory),
        new Long(dnMap.size()),
        (((maxEntries != Integer.MAX_VALUE) &&
          (maxEntries != Long.MAX_VALUE)) ?
           new Long(maxEntries) : new Long(0))
        );
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return attrs;
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

    Map<DN,CacheEntry> dnMapCopy;
    Map<Backend,HashMap<Long,CacheEntry>> idMapCopy;

    // Grab cache lock to prevent any modifications
    // to the cache maps until a snapshot is taken.
    cacheLock.lock();
    try {
      // Examining the real maps will hold the lock and can cause map
      // modifications in case of any access order maps, make copies
      // instead.
      dnMapCopy = new LinkedHashMap<DN,CacheEntry>(dnMap);
      idMapCopy = new HashMap<Backend,HashMap<Long,CacheEntry>>(idMap);
    } finally {
      cacheLock.unlock();
    }

    // Check dnMap first.
    for (DN dn : dnMapCopy.keySet()) {
      sb.append(dn.toString());
      sb.append(":");
      sb.append((dnMapCopy.get(dn) != null ?
          Long.toString(dnMapCopy.get(dn).getEntryID()) : null));
      sb.append(":");
      sb.append((dnMapCopy.get(dn) != null ?
          dnMapCopy.get(dn).getBackend().getBackendID() : null));
      sb.append(ServerConstants.EOL);
    }

    // See if there is anything on idMap that isnt reflected on
    // dnMap in case maps went out of sync.
    for (Backend backend : idMapCopy.keySet()) {
      for (Long id : idMapCopy.get(backend).keySet()) {
        if ((idMapCopy.get(backend).get(id) == null) ||
            !dnMapCopy.containsKey(
              idMapCopy.get(backend).get(id).getDN())) {
          sb.append((idMapCopy.get(backend).get(id) != null ?
              idMapCopy.get(backend).get(id).getDN().toString() : null));
          sb.append(":");
          sb.append(id.toString());
          sb.append(":");
          sb.append(backend.getBackendID());
          sb.append(ServerConstants.EOL);
        }
      }
    }

    verboseString = sb.toString();

    return (verboseString.length() > 0 ? verboseString : null);
  }

}

