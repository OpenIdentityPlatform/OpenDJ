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



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.admin.std.server.FIFOEntryCacheCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.CacheEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.LockType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
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
   * The set of time units that will be used for expressing the task retention
   * time.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
       new LinkedHashMap<String,Double>();



  // The DN of the configuration entry for this entry cache.
  private DN configEntryDN;

  // The mapping between entry backends/IDs and entries.
  private HashMap<Backend,HashMap<Long,CacheEntry>> idMap;

  // The set of filters that define the entries that should be excluded from the
  // cache.
  private HashSet<SearchFilter> excludeFilters;

  // The set of filters that define the entries that should be included in the
  // cache.
  private HashSet<SearchFilter> includeFilters;

  // The maximum percentage of JVM memory that should be used by the cache.
  private int maxMemoryPercent;

  // The mapping between DNs and entries.
  private LinkedHashMap<DN,CacheEntry> dnMap;

  // The lock used to provide threadsafe access when changing the contents of
  // the cache.
  private Lock cacheLock;

  // The maximum length of time to try to obtain a lock before giving up.
  private long lockTimeout;

  // The maximum amount of memory in bytes that the JVM will be allowed to use
  // before we need to start purging entries.
  private long maxAllowedMemory;

  // The maximum number of entries that may be held in the cache.
  private long maxEntries;

  // The reference to the Java runtime to use to determine the amount of memory
  // currently in use.
  private Runtime runtime;



  static
  {
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000D);
  }



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
    configuration.addFIFOChangeListener (this);
    configEntryDN = configuration.dn();

    // Initialize the cache structures.
    idMap     = new HashMap<Backend,HashMap<Long,CacheEntry>>();
    dnMap     = new LinkedHashMap<DN,CacheEntry>();
    cacheLock = new ReentrantLock();


    // Read configuration and apply changes.
    boolean applyChanges = true;
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_INIT, null, null
          );
    processEntryCacheConfig (configuration, applyChanges, errorHandler);
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeEntryCache()
  {
    // Release all memory currently in use by this cache.
    cacheLock.lock();

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
        debugCaught(DebugLogLevel.ERROR, e);
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
      return null;
    }
    else
    {
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
  public Entry getEntry(DN entryDN, LockType lockType, List<Lock> lockList)
  {
    // Get the entry from the DN map if it is present.  If not, then return
    // null.
    CacheEntry entry = dnMap.get(entryDN);
    if (entry == null)
    {
      return null;
    }


    // Obtain a lock for the entry as appropriate.  If an error occurs, then
    // make sure no lock is held and return null.  Otherwise, return the entry.
    switch (lockType)
    {
      case READ:
        // Try to obtain a read lock for the entry.
        Lock readLock = LockManager.lockRead(entryDN, lockTimeout);
        if (readLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(readLock);
            return entry.getEntry();
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, readLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case WRITE:
        // Try to obtain a write lock for the entry.
        Lock writeLock = LockManager.lockWrite(entryDN, lockTimeout);
        if (writeLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(writeLock);
            return entry.getEntry();
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, writeLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case NONE:
        // We don't need to obtain a lock, so just return the entry.
        return entry.getEntry();

      default:
        // This is an unknown type of lock, so we'll return null.
        return null;
    }
  }


  /**
   * {@inheritDoc}
   */
  public Entry getEntry(Backend backend, long entryID, LockType lockType,
                        List<Lock> lockList)
  {
    // Get the hash map for the provided backend.  If it isn't present, then
    // return null.
    HashMap<Long,CacheEntry> map = idMap.get(backend);
    if (map == null)
    {
      return null;
    }


    // Get the entry from the map by its ID.  If it isn't present, then return
    // null.
    CacheEntry cacheEntry = map.get(entryID);
    if (cacheEntry == null)
    {
      return null;
    }


    // Obtain a lock for the entry as appropriate.  If an error occurs, then
    // make sure no lock is held and return null.  Otherwise, return the entry.
    Entry entry = cacheEntry.getEntry();
    switch (lockType)
    {
      case READ:
        // Try to obtain a read lock for the entry.
        Lock readLock = LockManager.lockRead(entry.getDN(), lockTimeout);
        if (readLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(readLock);
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entry.getDN(), readLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case WRITE:
        // Try to obtain a write lock for the entry.
        Lock writeLock = LockManager.lockWrite(entry.getDN(), lockTimeout);
        if (writeLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(writeLock);
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entry.getDN(), writeLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case NONE:
        // We don't need to obtain a lock, so just return the entry.
        return entry;

      default:
        // This is an unknown type of lock, so we'll return null.
        return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  public void putEntry(Entry entry, Backend backend, long entryID)
  {
    // If there is a set of exclude filters, then make sure that the provided
    // entry doesn't match any of them.
    if (! excludeFilters.isEmpty())
    {
      for (SearchFilter f : excludeFilters)
      {
        try
        {
          if (f.matchesEntry(entry))
          {
            return;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does then we can't be sure whether
          // the entry should be excluded, so we will by default.
          return;
        }
      }
    }


    // If there is a set of include filters, then make sure that the provided
    // entry matches at least one of them.
    if (! includeFilters.isEmpty())
    {
      boolean matchFound = false;
      for (SearchFilter f : includeFilters)
      {
        try
        {
          if (f.matchesEntry(entry))
          {
            matchFound = true;
            break;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does, then just ignore it.
        }
      }

      if (! matchFound)
      {
        return;
      }
    }


    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backend, entryID);


    // Obtain a lock on the cache.  If this fails, then don't do anything.
    try
    {
      if (! cacheLock.tryLock(lockTimeout, TimeUnit.MILLISECONDS))
      {
        return;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
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
        debugCaught(DebugLogLevel.ERROR, e);
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
    // If there is a set of exclude filters, then make sure that the provided
    // entry doesn't match any of them.
    if (! excludeFilters.isEmpty())
    {
      for (SearchFilter f : excludeFilters)
      {
        try
        {
          if (f.matchesEntry(entry))
          {
            return true;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does then we can't be sure whether
          // the entry should be excluded, so we will by default.
          return false;
        }
      }
    }


    // If there is a set of include filters, then make sure that the provided
    // entry matches at least one of them.
    if (! includeFilters.isEmpty())
    {
      boolean matchFound = false;
      for (SearchFilter f : includeFilters)
      {
        try
        {
          if (f.matchesEntry(entry))
          {
            matchFound = true;
            break;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does, then just ignore it.
        }
      }

      if (! matchFound)
      {
        return true;
      }
    }


    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backend, entryID);


    // Obtain a lock on the cache.  If this fails, then don't do anything.
    try
    {
      if (! cacheLock.tryLock(lockTimeout, TimeUnit.MILLISECONDS))
      {
        // We can't rule out the possibility of a conflict, so return false.
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
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
        debugCaught(DebugLogLevel.ERROR, e);
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


      // Try to remove the entry from the ID list as well.
      Map<Long,CacheEntry> map = idMap.get(entry.getBackend());
      if (map == null)
      {
        // This should't happen, but the entry isn't cached in the ID map so
        // we can return.
        return;
      }

      map.remove(entry.getEntryID());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
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
        debugCaught(DebugLogLevel.ERROR, e);
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
        debugCaught(DebugLogLevel.ERROR, e);
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
        debugCaught(DebugLogLevel.ERROR, e);
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();


    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctAttr =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
                                    getMessage(msgID), true, false, false, true,
                                    1, true, 100, maxMemoryPercent);
    attrList.add(maxMemoryPctAttr);


    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesAttr =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0, maxEntries);
    attrList.add(maxEntriesAttr);


    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutAttr =
         new IntegerWithUnitConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0, lockTimeout,
                                            TIME_UNIT_MILLISECONDS_FULL);
    attrList.add(lockTimeoutAttr);


    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    ArrayList<String> includeStrings =
         new ArrayList<String>(includeFilters.size());
    for (SearchFilter f : includeFilters)
    {
      includeStrings.add(f.toString());
    }
    StringConfigAttribute includeAttr =
         new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
                                   getMessage(msgID), false, true, false,
                                   includeStrings);
    attrList.add(includeAttr);


    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    ArrayList<String> excludeStrings =
         new ArrayList<String>(excludeFilters.size());
    for (SearchFilter f : excludeFilters)
    {
      excludeStrings.add(f.toString());
    }
    StringConfigAttribute excludeAttr =
         new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
                                   getMessage(msgID), false, true, false,
                                   excludeStrings);
    attrList.add(excludeAttr);


    return attrList;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FIFOEntryCacheCfg configuration,
      List<String>      unacceptableReasons
      )
  {
    // Make sure that we can process the defined character sets.  If so, then
    // we'll accept the new configuration.
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
    // Make sure that we can process the defined character sets.  If so, then
    // activate the new configuration.
    boolean applyChanges = false;
    ArrayList<String> errorMessages = new ArrayList<String>();
    EntryCacheCommon.ConfigErrorHandler errorHandler =
      EntryCacheCommon.getConfigErrorHandler (
          EntryCacheCommon.ConfigPhase.PHASE_APPLY, null, errorMessages
          );
    processEntryCacheConfig (configuration, applyChanges, errorHandler);


    boolean adminActionRequired = false;
    ConfigChangeResult changeResult = new ConfigChangeResult(
        errorHandler.getResultCode(),
        adminActionRequired,
        errorHandler.getErrorMessages()
        );

    return changeResult;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configuration    The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(
      FIFOEntryCacheCfg configuration,
      boolean           detailedResults
      )
  {
    // Store the current value to detect changes.
    long                  prevLockTimeout      = lockTimeout;
    long                  prevMaxEntries       = maxEntries;
    int                   prevMaxMemoryPercent = maxMemoryPercent;
    HashSet<SearchFilter> prevIncludeFilters   = includeFilters;
    HashSet<SearchFilter> prevExcludeFilters   = excludeFilters;

    // Activate the new configuration.
    ConfigChangeResult changeResult = applyConfigurationChange(configuration);

    // Add detailed messages if needed.
    ResultCode resultCode = changeResult.getResultCode();
    boolean configIsAcceptable = (resultCode == ResultCode.SUCCESS);
    if (detailedResults && configIsAcceptable)
    {
      if (maxMemoryPercent != prevMaxMemoryPercent)
      {
        changeResult.addMessage(
            getMessage(
                MSGID_FIFOCACHE_UPDATED_MAX_MEMORY_PCT,
                maxMemoryPercent,
                maxAllowedMemory));
      }


      if (maxEntries != prevMaxEntries)
      {
        changeResult.addMessage(
            getMessage (MSGID_FIFOCACHE_UPDATED_MAX_ENTRIES, maxEntries));
      }

      if (lockTimeout != prevLockTimeout)
      {
        changeResult.addMessage(
            getMessage (MSGID_FIFOCACHE_UPDATED_LOCK_TIMEOUT, lockTimeout));
      }

      if (!includeFilters.equals(prevIncludeFilters))
      {
        changeResult.addMessage(
            getMessage (MSGID_FIFOCACHE_UPDATED_INCLUDE_FILTERS));
      }

      if (!excludeFilters.equals(prevExcludeFilters))
      {
        changeResult.addMessage(
            getMessage (MSGID_FIFOCACHE_UPDATED_EXCLUDE_FILTERS));
      }
    }

    return changeResult;
  }



  /**
   * Parses the provided configuration and configure the entry cache.
   *
   * @param configuration  The new configuration containing the changes.
   * @param applyChanges   If true then take into account the new configuration.
   * @param errorHandler   An handler used to report errors.
   *
   * @return  The mapping between strings of character set values and the
   *          minimum number of characters required from those sets.
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
    newMaxMemoryPercent = (int) configuration.getMaxMemoryPercent();
    long maxJvmHeapSize = Runtime.getRuntime().maxMemory();
    newMaxAllowedMemory = (maxJvmHeapSize / 100) * newMaxMemoryPercent;

    // Get include and exclude filters.
    switch (errorHandler.getConfigPhase())
    {
    case PHASE_INIT:
      newIncludeFilters = EntryCacheCommon.getFilters (
          configuration.getIncludeFilter(),
          MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER,
          MSGID_FIFOCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
          errorHandler,
          configEntryDN
          );
      newExcludeFilters = EntryCacheCommon.getFilters (
          configuration.getExcludeFilter(),
          MSGID_FIFOCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
          MSGID_FIFOCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
          errorHandler,
          configEntryDN
          );
      break;
    case PHASE_ACCEPTABLE:  // acceptable and apply are using the same
    case PHASE_APPLY:       // error ID codes
      newIncludeFilters = EntryCacheCommon.getFilters (
          configuration.getIncludeFilter(),
          MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER,
          0,
          errorHandler,
          configEntryDN
          );
      newExcludeFilters = EntryCacheCommon.getFilters (
          configuration.getExcludeFilter(),
          MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER,
          0,
          errorHandler,
          configEntryDN
          );
      break;
    }

    if (applyChanges && errorHandler.getIsAcceptable())
    {
      configEntryDN    = newConfigEntryDN;
      lockTimeout      = newLockTimeout;
      maxEntries       = newMaxEntries;
      maxMemoryPercent = newMaxMemoryPercent;
      maxAllowedMemory = newMaxAllowedMemory;
      includeFilters   = newIncludeFilters;
      excludeFilters   = newExcludeFilters;
    }

    return errorHandler.getIsAcceptable();
  }

}

