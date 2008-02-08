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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.api;
import org.opends.messages.Message;



import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.atomic.AtomicLong;

import org.opends.server.config.ConfigException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockType;
import org.opends.server.types.LockManager;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.monitors.EntryCacheMonitorProvider;
import org.opends.server.types.Attribute;
import static org.opends.server.loggers.debug.DebugLogger.*;



/**
 * This class defines the set of methods that must be implemented by a
 * Directory Server entry cache.  Note that components accessing the
 * entry cache must not depend on any particular behavior.  For
 * example, if a call is made to {@code putEntry} to store an entry in
 * the cache, there is no guarantee that immediately calling
 * {@code getEntry} will be able to retrieve it.  There are several
 * potential reasons for this, including:
 * <UL>
 *   <LI>The entry may have been deleted or replaced by another thread
 *       between the {@code putEntry} and {@code getEntry} calls.</LI>
 *   <LI>The entry cache may implement a purging mechanism and the
 *       entry added may have been purged between the
 *       {@code putEntry} and {@code getEntry} calls.</LI>
 *   <LI>The entry cache may implement some kind of filtering
 *       mechanism to determine which entries to store, and entries
 *       not matching the appropriate criteria may not be stored.</LI>
 *   <LI>The entry cache may not actually store any entries (this is
 *       the behavior of the default cache if no implementation
 *       specific entry cache is available).</LI>
 * </UL>
 *
 * @param  <T>  The type of configuration handled by this entry
 *              cache.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=true,
     notes="Entry cache methods may only be invoked by backends")
public abstract class EntryCache
       <T extends EntryCacheCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of filters that define the entries that should be
  // excluded from the cache.
  private Set<SearchFilter> excludeFilters =
       new HashSet<SearchFilter>(0);

  // The set of filters that define the entries that should be
  // included in the cache.
  private Set<SearchFilter> includeFilters =
       new HashSet<SearchFilter>(0);

  // The maximum length of time to try to obtain a lock before giving
  // up.
  private long lockTimeout = LockManager.DEFAULT_TIMEOUT;

  /**
   * Arbitrary number of cache hits for monitoring.
   */
  protected AtomicLong cacheHits = new AtomicLong(0);

  /**
   * Arbitrary number of cache misses for monitoring.
   */
  protected AtomicLong cacheMisses = new AtomicLong(0);

  // The monitor associated with this entry cache.
  private EntryCacheMonitorProvider entryCacheMonitor = null;


  /**
   * Default constructor which is implicitly called from all entry
   * cache implementations.
   */
  public EntryCache()
  {
    // No implementation required.
  }



  /**
   * Initializes this entry cache implementation so that it will be
   * available for storing and retrieving entries.
   *
   * @param  configuration  The configuration to use to initialize
   *                        the entry cache.
   *
   * @throws  ConfigException  If there is a problem with the provided
   *                           configuration entry that would prevent
   *                           this entry cache from being used.
   *
   * @throws  InitializationException  If a problem occurs during the
   *                                   initialization process that is
   *                                   not related to the
   *                                   configuration.
   */
  public abstract void initializeEntryCache(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this entry cache.  It should be possible to call this method on
   * an uninitialized entry cache instance in order to determine
   * whether the entry cache would be able to use the provided
   * configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The entry cache configuration for
   *                              which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this entry cache, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      EntryCacheCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by entry cache
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any necessary cleanup work (e.g., flushing all cached
   * entries and releasing any other held resources) that should be
   * performed when the server is to be shut down or the entry cache
   * destroyed or replaced.
   */
  public abstract void finalizeEntryCache();



  /**
   * Indicates whether the entry cache currently contains the entry
   * with the specified DN.  This method may be called without holding
   * any locks if a point-in-time check is all that is required.
   * Note that this method is called from @see #getEntry(DN entryDN,
   * LockType lockType, List lockList)
   *
   * @param  entryDN  The DN for which to make the determination.
   *
   * @return  {@code true} if the entry cache currently contains the
   *          entry with the specified DN, or {@code false} if not.
   */
  public abstract boolean containsEntry(DN entryDN);



  /**
   * Retrieves the entry with the specified DN from the cache.  The
   * caller should have already acquired a read or write lock for the
   * entry if such protection is needed.
   * Note that this method is called from @see #getEntry(DN entryDN,
   * LockType lockType, List lockList)
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry if it is present in the cache, or
   *          {@code null} if it is not present.
   */
  public abstract Entry getEntry(DN entryDN);



  /**
   * Retrieves the entry with the specified DN from the cache,
   * obtaining a lock on the entry before it is returned.  If the
   * entry is present in the cache, then a lock will be obtained for
   * that entry and appended to the provided list before the entry is
   * returned.  If the entry is not present, then no lock will be
   * obtained.  Note that although this method is declared non-final
   * it is not recommended for subclasses to implement this method.
   *
   * @param  entryDN   The DN of the entry to retrieve.
   * @param  lockType  The type of lock to obtain (it may be
   *                   {@code NONE}).
   * @param  lockList  The list to which the obtained lock will be
   *                   added (note that no lock will be added if the
   *                   lock type was {@code NONE}).
   *
   * @return  The requested entry if it is present in the cache, or
   *          {@code null} if it is not present.
   */
  public Entry getEntry(DN entryDN,
                        LockType lockType,
                        List<Lock> lockList) {

    if (!containsEntry(entryDN)) {
      // Indicate cache miss.
      cacheMisses.getAndIncrement();

      return null;
    }

    // Obtain a lock for the entry before actually retrieving the
    // entry itself thus preventing any stale entries being returned,
    // see Issue #1589 for more details.  If an error occurs, then
    // make sure no lock is held and return null. Otherwise, return
    // the entry.
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
            // and load.
            Entry entry = getEntry(entryDN);
            if (entry == null)
            {
              lockList.remove(readLock);
              LockManager.unlock(entryDN, readLock);
              return null;
            }
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed,
            // so we need to release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, readLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e2);
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
            // and load.
            Entry entry = getEntry(entryDN);
            if (entry == null)
            {
              lockList.remove(writeLock);
              LockManager.unlock(entryDN, writeLock);
              return null;
            }
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed,
            // so we need to release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, writeLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case NONE:
        // We don't need to obtain a lock, so just return the entry.
        Entry entry = getEntry(entryDN);
        if (entry == null)
        {
          return null;
        }
        return entry;

      default:
        // This is an unknown type of lock, so we'll return null.
        return null;
    }
  }



  /**
   * Retrieves the requested entry if it is present in the cache,
   * obtaining a lock on the entry before it is returned.  If the
   * entry is present in the cache, then a lock  will be obtained for
   * that entry and appended to the provided list before the entry is
   * returned.  If the entry is not present, then no lock will be
   * obtained.  Note that although this method is declared non-final
   * it is not recommended for subclasses to implement this method.
   *
   * @param  backend   The backend associated with the entry to
   *                   retrieve.
   * @param  entryID   The entry ID within the provided backend for
   *                   the specified entry.
   * @param  lockType  The type of lock to obtain (it may be
   *                   {@code NONE}).
   * @param  lockList  The list to which the obtained lock will be
   *                   added (note that no lock will be added if the
   *                   lock type was {@code NONE}).
   *
   * @return  The requested entry if it is present in the cache, or
   *          {@code null} if it is not present.
   */
  public Entry getEntry(Backend backend, long entryID,
                                 LockType lockType,
                                 List<Lock> lockList) {

    // Translate given backend/entryID pair to entryDN.
    DN entryDN = getEntryDN(backend, entryID);
    if (entryDN == null) {
      // Indicate cache miss.
      cacheMisses.getAndIncrement();

      return null;
    }

    // Delegate to by DN lock and load method.
    return getEntry(entryDN, lockType, lockList);
  }



  /**
   * Retrieves the entry ID for the entry with the specified DN from
   * the cache.  The caller should have already acquired a read or
   * write lock for the entry if such protection is needed.
   *
   * @param  entryDN  The DN of the entry for which to retrieve the
   *                  entry ID.
   *
   * @return  The entry ID for the requested entry, or -1 if it is
   *          not present in the cache.
   */
  public abstract long getEntryID(DN entryDN);



  /**
   * Retrieves the entry DN for the entry with the specified ID on
   * the specific backend from the cache.  The caller should have
   * already acquired a read or write lock for the entry if such
   * protection is needed.
   * Note that this method is called from @see #getEntry(Backend
   * backend, long entryID, LockType lockType, List lockList)
   *
   * @param  backend  The backend associated with the entry for
   *                  which to retrieve the entry DN.
   * @param  entryID  The entry ID within the provided backend
   *                  for which to retrieve the entry DN.
   *
   * @return  The entry DN for the requested entry, or
   *          {@code null} if it is not present in the cache.
   */
  public abstract DN getEntryDN(Backend backend, long entryID);



  /**
   * Stores the provided entry in the cache.  Note that the mechanism
   * that it uses to achieve this is implementation-dependent, and it
   * is acceptable for the entry to not actually be stored in any
   * cache.
   *
   * @param  entry    The entry to store in the cache.
   * @param  backend  The backend with which the entry is associated.
   * @param  entryID  The entry ID within the provided backend that
   *                  uniquely identifies the specified entry.
   */
  public abstract void putEntry(Entry entry, Backend backend,
                                long entryID);



  /**
   * Stores the provided entry in the cache only if it does not
   * conflict with an entry that already exists.  Note that the
   * mechanism that it uses to achieve this is
   * implementation-dependent, and it is acceptable for the entry to
   * not actually be stored in any cache.  However, this method must
   * not overwrite an existing version of the entry.
   *
   * @param  entry    The entry to store in the cache.
   * @param  backend  The backend with which the entry is associated.
   * @param  entryID  The entry ID within the provided backend that
   *                  uniquely identifies the specified entry.
   *
   * @return  {@code false} if an existing entry or some other problem
   *          prevented the method from completing successfully, or
   *          {@code true} if there was no conflict and the entry was
   *          either stored or the cache determined that this entry
   *          should never be cached for some reason.
   */
  public abstract boolean putEntryIfAbsent(Entry entry,
                                           Backend backend,
                                           long entryID);



  /**
   * Removes the specified entry from the cache.
   *
   * @param  entryDN  The DN of the entry to remove from the cache.
   */
  public abstract void removeEntry(DN entryDN);



  /**
   * Removes all entries from the cache.  The cache should still be
   * available for future use.
   */
  public abstract void clear();



  /**
   * Removes all entries from the cache that are associated with the
   * provided backend.
   *
   * @param  backend  The backend for which to flush the associated
   *                  entries.
   */
  public abstract void clearBackend(Backend backend);



  /**
   * Removes all entries from the cache that are below the provided
   * DN.
   *
   * @param  baseDN  The base DN below which all entries should be
   *                 flushed.
   */
  public abstract void clearSubtree(DN baseDN);



  /**
   * Attempts to react to a scenario in which it is determined that
   * the system is running low on available memory.  In this case, the
   * entry cache should attempt to free some memory if possible to try
   * to avoid out of memory errors.
   */
  public abstract void handleLowMemory();



  /**
   * Retrieves the monitor that is associated with this entry
   * cache.
   *
   * @return  The monitor that is associated with this entry
   *          cache, or {@code null} if none has been assigned.
   */
  public final EntryCacheMonitorProvider getEntryCacheMonitor()
  {
    return entryCacheMonitor;
  }



  /**
   * Sets the monitor for this entry cache.
   *
   * @param  entryCacheMonitor  The monitor for this entry cache.
   */
  public final void setEntryCacheMonitor(
    EntryCacheMonitorProvider entryCacheMonitor)
  {
    this.entryCacheMonitor = entryCacheMonitor;
  }



  /**
   * Retrieves a set of attributes containing monitor data that should
   * be returned to the client if the corresponding monitor entry is
   * requested.
   *
   * @return  A set of attributes containing monitor data that should
   *          be returned to the client if the corresponding monitor
   *          entry is requested.
   */
  public abstract ArrayList<Attribute> getMonitorData();



  /**
   * Retrieves the current number of entries stored within the cache.
   *
   * @return  The current number of entries stored within the cache.
   */
  public abstract Long getCacheCount();



  /**
   * Retrieves the current number of cache hits for this cache.
   *
   * @return  The current number of cache hits for this cache.
   */
  public Long getCacheHits()
  {
    return new Long(cacheHits.longValue());
  }



  /**
   * Retrieves the current number of cache misses for this cache.
   *
   * @return  The current number of cache misses for this cache.
   */
  public Long getCacheMisses()
  {
    return new Long(cacheMisses.longValue());
  }



  /**
   * Retrieves the maximum length of time in milliseconds to wait for
   * a lock before giving up.
   *
   * @return  The maximum length of time in milliseconds to wait for a
   *          lock before giving up.
   */
  public long getLockTimeout()
  {
    return lockTimeout;
  }



  /**
   * Specifies the maximum length of time in milliseconds to wait for
   * a lock before giving up.
   *
   * @param  lockTimeout  The maximum length of time in milliseconds
   *                      to wait for a lock before giving up.
   */
  public void setLockTimeout(long lockTimeout)
  {
    this.lockTimeout = lockTimeout;
  }



  /**
   * Retrieves the set of search filters that may be used to determine
   * whether an entry should be excluded from the cache.
   *
   * @return  The set of search filters that may be used to determine
   *          whether an entry should be excluded from the cache.
   */
  public Set<SearchFilter> getExcludeFilters()
  {
    return excludeFilters;
  }



  /**
   * Specifies the set of search filters that may be used to determine
   * whether an entry should be excluded from the cache.
   *
   * @param  excludeFilters  The set of search filters that may be
   *                         used to determine whether an entry should
   *                         be excluded from the cache.
   */
  public void setExcludeFilters(Set<SearchFilter> excludeFilters)
  {
    if (excludeFilters == null)
    {
      this.excludeFilters = new HashSet<SearchFilter>(0);
    }
    else
    {
      this.excludeFilters = excludeFilters;
    }
  }



  /**
   * Retrieves the set of search filters that may be used to determine
   * whether an entry should be included in the cache.
   *
   * @return  The set of search filters that may be used to determine
   *          whether an entry should be included in the cache.
   */
  public Set<SearchFilter> getIncludeFilters()
  {
    return includeFilters;
  }



  /**
   * Specifies the set of search filters that may be used to determine
   * whether an entry should be included in the cache.
   *
   * @param  includeFilters  The set of search filters that may be
   *                         used to determine whether an entry should
   *                         be included in the cache.
   */
  public void setIncludeFilters(Set<SearchFilter> includeFilters)
  {
    if (includeFilters == null)
    {
      this.includeFilters = new HashSet<SearchFilter>(0);
    }
    else
    {
      this.includeFilters = includeFilters;
    }
  }



  /**
   * Indicates whether the current set of exclude and include filters
   * allow caching of the specified entry.
   *
   * @param  entry  The entry to evaluate against exclude and include
   *                filter sets.
   *
   * @return  {@code true} if current set of filters allow caching the
   *          entry and {@code false} otherwise.
   */
  public boolean filtersAllowCaching(Entry entry)
  {
    // If there is a set of exclude filters, then make sure that the
    // provided entry doesn't match any of them.
    if (! excludeFilters.isEmpty())
    {
      for (SearchFilter f : excludeFilters)
      {
        try
        {
          if (f.matchesEntry(entry))
          {
            return false;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does then we can't be
          // sure whether the entry should be excluded, so we will
          // by default.
          return false;
        }
      }
    }

    // If there is a set of include filters, then make sure that the
    // provided entry matches at least one of them.
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does, then
          // just ignore it.
        }
      }

      if (! matchFound)
      {
        return false;
      }
    }

    return true;
  }
}
