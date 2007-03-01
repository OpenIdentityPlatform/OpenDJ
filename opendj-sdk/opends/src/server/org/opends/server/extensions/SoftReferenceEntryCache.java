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



import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.Backend;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.CacheEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.LockType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server entry cache that uses soft references
 * to manage objects in a way that will allow them to be freed if the JVM is
 * running low on memory.
 */
public class SoftReferenceEntryCache
       extends EntryCache
       implements ConfigurableComponent, Runnable
{



  /**
   * The set of time units that will be used for expressing the task retention
   * time.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
       new LinkedHashMap<String,Double>();



  // The mapping between entry DNs and their corresponding entries.
  private ConcurrentHashMap<DN,SoftReference<CacheEntry>> dnMap;

  // The mapping between backend+ID and their corresponding entries.
  private ConcurrentHashMap<Backend,
               ConcurrentHashMap<Long,SoftReference<CacheEntry>>> idMap;

  // The DN of the configuration entry for this entry cache implementation.
  private DN configEntryDN;

  // The set of filters that define the entries that should be excluded from the
  // cache.
  private HashSet<SearchFilter> excludeFilters;

  // The set of filters that define the entries that should be included in the
  // cache.
  private HashSet<SearchFilter> includeFilters;

  // The maximum length of time that we will wait while trying to obtain a lock
  // on an entry.
  private long lockTimeout;

  // The reference queue that will be used to notify us whenever a soft
  // reference is freed.
  private ReferenceQueue<CacheEntry> referenceQueue;



  static
  {
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000D);
  }



  /**
   * Creates a new instance of this soft reference entry cache.  All
   * initialization should be performed in the <CODE>initializeEntryCache</CODE>
   * method.
   */
  public SoftReferenceEntryCache()
  {
    super();


    dnMap = new ConcurrentHashMap<DN,SoftReference<CacheEntry>>();
    idMap = new ConcurrentHashMap<Backend,
                     ConcurrentHashMap<Long,SoftReference<CacheEntry>>>();

    excludeFilters = new HashSet<SearchFilter>();
    includeFilters = new HashSet<SearchFilter>();
    lockTimeout    = LockManager.DEFAULT_TIMEOUT;
    referenceQueue = new ReferenceQueue<CacheEntry>();

    Thread cleanerThread =
         new Thread(this, "Soft Reference Entry Cache Cleaner");
    cleanerThread.setDaemon(true);
    cleanerThread.start();
  }



  /**
   * Initializes this entry cache implementation so that it will be available
   * for storing and retrieving entries.
   *
   * @param  configEntry  The configuration entry containing the settings to use
   *                      for this entry cache.
   *
   * @throws  ConfigException  If there is a problem with the provided
   *                           configuration entry that would prevent this
   *                           entry cache from being used.
   *
   * @throws  InitializationException  If a problem occurs during the
   *                                   initialization process that is not
   *                                   related to the configuration.
   */
  public void initializeEntryCache(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {

    dnMap.clear();
    idMap.clear();


    configEntryDN = configEntry.getDN();


    // Determine the lock timeout to use when interacting with the lock manager.
    int msgID = MSGID_SOFTREFCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutStub =
         new IntegerWithUnitConfigAttribute(ATTR_SOFTREFCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute lockTimeoutAttr =
             (IntegerWithUnitConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
      if (lockTimeoutAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        lockTimeout = lockTimeoutAttr.activeCalculatedValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_SOFTREFCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e),
               lockTimeout);
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    includeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_INCLUDE_FILTER,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute includeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(includeStub);
      if (includeAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        List<String> filterStrings = includeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty())
        {
          // There are no include filters, so we'll allow anything by default.
        }
        else
        {
          for (String filterString : filterStrings)
          {
            try
            {
              includeFilters.add(
                   SearchFilter.createFilterFromString(filterString));
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }

              // We couldn't decode this filter.  Log a warning and continue.
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING,
                       MSGID_SOFTREFCACHE_CANNOT_DECODE_INCLUDE_FILTER,
                       String.valueOf(configEntryDN), filterString,
                       stackTraceToSingleLineString(e));
            }
          }

          if (includeFilters.isEmpty())
          {
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
                     String.valueOf(configEntryDN));
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_SOFTREFCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be excluded from the cache.
    excludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_EXCLUDE_FILTER,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute excludeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(excludeStub);
      if (excludeAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        List<String> filterStrings = excludeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty())
        {
          // There are no exclude filters, so we'll allow anything by default.
        }
        else
        {
          for (String filterString : filterStrings)
          {
            try
            {
              excludeFilters.add(
                   SearchFilter.createFilterFromString(filterString));
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }

              // We couldn't decode this filter.  Log a warning and continue.
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING,
                       MSGID_SOFTREFCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
                       String.valueOf(configEntryDN), filterString,
                       stackTraceToSingleLineString(e));
            }
          }

          if (excludeFilters.isEmpty())
          {
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
                     String.valueOf(configEntryDN));
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_SOFTREFCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }
  }



  /**
   * Performs any necessary cleanup work (e.g., flushing all cached entries and
   * releasing any other held resources) that should be performed when the
   * server is to be shut down or the entry cache destroyed or replaced.
   */
  public void finalizeEntryCache()
  {

    dnMap.clear();
    idMap.clear();
  }



  /**
   * Indicates whether the entry cache currently contains the entry with the
   * specified DN.  This method may be called without holding any locks if a
   * point-in-time check is all that is required.
   *
   * @param  entryDN  The DN for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the entry cache currently contains the entry
   *          with the specified DN, or <CODE>false</CODE> if not.
   */
  public boolean containsEntry(DN entryDN)
  {

    // Indicate whether the DN map contains the specified DN.
    return dnMap.containsKey(entryDN);
  }



  /**
   * Retrieves the entry with the specified DN from the cache.  The caller
   * should have already acquired a read or write lock for the entry if such
   * protection is needed.
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(DN entryDN)
  {

    SoftReference<CacheEntry> ref = dnMap.get(entryDN);
    if (ref == null)
    {
      return null;
    }
    else
    {
      CacheEntry cacheEntry = ref.get();
      if (cacheEntry == null)
      {
        return null;
      }
      else
      {
        return cacheEntry.getEntry();
      }
    }
  }



  /**
   * Retrieves the entry ID for the entry with the specified DN from the cache.
   * The caller should have already acquired a read or write lock for the entry
   * if such protection is needed.
   *
   * @param  entryDN  The DN of the entry for which to retrieve the entry ID.
   *
   * @return  The entry ID for the requested entry, or -1 if it is not present
   *          in the cache.
   */
  public long getEntryID(DN entryDN)
  {

    SoftReference<CacheEntry> ref = dnMap.get(entryDN);
    if (ref == null)
    {
      return -1;
    }
    else
    {
      CacheEntry cacheEntry = ref.get();
      if (cacheEntry == null)
      {
        return -1;
      }
      else
      {
        return cacheEntry.getEntryID();
      }
    }
  }



  /**
   * Retrieves the entry with the specified DN from the cache, obtaining a lock
   * on the entry before it is returned.  If the entry is present in the cache,
   * then a lock will be obtained for that entry and appended to the provided
   * list before the entry is returned.  If the entry is not present, then no
   * lock will be obtained.
   *
   * @param  entryDN   The DN of the entry to retrieve.
   * @param  lockType  The type of lock to obtain (it may be <CODE>NONE</CODE>).
   * @param  lockList  The list to which the obtained lock will be added (note
   *                   that no lock will be added if the lock type was
   *                   <CODE>NONE</CODE>).
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(DN entryDN, LockType lockType,
                        List<Lock> lockList)
  {

    SoftReference<CacheEntry> ref = dnMap.get(entryDN);
    if (ref == null)
    {
      return null;
    }
    else
    {
      CacheEntry cacheEntry = ref.get();
      if (cacheEntry == null)
      {
        return null;
      }
      else
      {
        switch (lockType)
        {
          case READ:
            // Try to obtain a read lock for the entry, but don't wait too long
            // so only try once.
            Lock readLock = LockManager.lockRead(entryDN);
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
                return cacheEntry.getEntry();
              }
              catch (Exception e)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, e);
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
                    debugCought(DebugLogLevel.ERROR, e2);
                  }
                }

                return null;
              }
            }

          case WRITE:
            // Try to obtain a write lock for the entry, but don't wait too long
            // so only try once.
            Lock writeLock = LockManager.lockWrite(entryDN);
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
                return cacheEntry.getEntry();
              }
              catch (Exception e)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, e);
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
                    debugCought(DebugLogLevel.ERROR, e2);
                  }
                }

                return null;
              }
            }

          case NONE:
            // There is no lock required, so we can just return the entry.
            return cacheEntry.getEntry();

          default:
            // This is an unknown type of lock, so we can't provide it.
            return null;
        }
      }
    }
  }



  /**
   * Retrieves the requested entry if it is present in the cache, obtaining a
   * lock on the entry before it is returned.  If the entry is present in the
   * cache, then a lock  will be obtained for that entry and appended to the
   * provided list before the entry is returned.  If the entry is not present,
   * then no lock will be obtained.
   *
   * @param  backend   The backend associated with the entry to retrieve.
   * @param  entryID   The entry ID within the provided backend for the
   *                   specified entry.
   * @param  lockType  The type of lock to obtain (it may be <CODE>NONE</CODE>).
   * @param  lockList  The list to which the obtained lock will be added (note
   *                   that no lock will be added if the lock type was
   *                   <CODE>NONE</CODE>).
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(Backend backend, long entryID,
                        LockType lockType, List<Lock> lockList)
  {

    ConcurrentHashMap<Long,SoftReference<CacheEntry>> map = idMap.get(backend);
    if (map == null)
    {
      return null;
    }

    SoftReference<CacheEntry> ref = map.get(entryID);
    if (ref == null)
    {
      return null;
    }

    CacheEntry cacheEntry = ref.get();
    if (cacheEntry == null)
    {
      return null;
    }

    switch (lockType)
    {
      case READ:
        // Try to obtain a read lock for the entry, but don't wait too long so
        // only try once.
        Lock readLock = LockManager.lockRead(cacheEntry.getDN());
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
            return cacheEntry.getEntry();
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(cacheEntry.getDN(), readLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case WRITE:
        // Try to obtain a write lock for the entry, but don't wait too long so
        // only try once.
        Lock writeLock = LockManager.lockWrite(cacheEntry.getDN());
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
            return cacheEntry.getEntry();
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(cacheEntry.getDN(), writeLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case NONE:
        // There is no lock required, so we can just return the entry.
        return cacheEntry.getEntry();

      default:
        // This is an unknown type of lock, so we can't provide it.
        return null;
    }
  }



  /**
   * Stores the provided entry in the cache.  Note that the mechanism that it
   * uses to achieve this is implementation-dependent, and it is acceptable for
   * the entry to not actually be stored in any cache.
   *
   * @param  entry    The entry to store in the cache.
   * @param  backend  The backend with which the entry is associated.
   * @param  entryID  The entry ID within the provided backend that uniquely
   *                  identifies the specified entry.
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
            debugCought(DebugLogLevel.ERROR, e);
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
            debugCought(DebugLogLevel.ERROR, e);
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
    SoftReference<CacheEntry> ref =
         new SoftReference<CacheEntry>(cacheEntry, referenceQueue);

    SoftReference<CacheEntry> oldRef = dnMap.put(entry.getDN(), ref);
    if (oldRef != null)
    {
      oldRef.clear();
    }

    ConcurrentHashMap<Long,SoftReference<CacheEntry>> map = idMap.get(backend);
    if (map == null)
    {
      map = new ConcurrentHashMap<Long,SoftReference<CacheEntry>>();
      map.put(entryID, ref);
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



  /**
   * Stores the provided entry in the cache only if it does not conflict with an
   * entry that already exists.  Note that the mechanism that it uses to achieve
   * this is implementation-dependent, and it is acceptable for the entry to not
   * actually be stored in any cache.  However, this method must not overwrite
   * an existing version of the entry.
   *
   * @param  entry    The entry to store in the cache.
   * @param  backend  The backend with which the entry is associated.
   * @param  entryID  The entry ID within the provided backend that uniquely
   *                  identifies the specified entry.
   *
   * @return  <CODE>false</CODE> if an existing entry or some other problem
   *          prevented the method from completing successfully, or
   *          <CODE>true</CODE> if there was no conflict and the entry was
   *          either stored or the cache determined that this entry should never
   *          be cached for some reason.
   */
  public boolean putEntryIfAbsent(Entry entry, Backend backend,
                                  long entryID)
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
            debugCought(DebugLogLevel.ERROR, e);
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
            debugCought(DebugLogLevel.ERROR, e);
          }

          // This shouldn't happen, but if it does, then just ignore it.
        }
      }

      if (! matchFound)
      {
        return true;
      }
    }


    // See if the entry already exists.  If so, then return false.
    if (dnMap.containsKey(entry.getDN()))
    {
      return false;
    }


    // Create the cache entry based on the provided information.
    CacheEntry cacheEntry = new CacheEntry(entry, backend, entryID);
    SoftReference<CacheEntry> ref =
         new SoftReference<CacheEntry>(cacheEntry, referenceQueue);

    dnMap.put(entry.getDN(), ref);

    ConcurrentHashMap<Long,SoftReference<CacheEntry>> map = idMap.get(backend);
    if (map == null)
    {
      map = new ConcurrentHashMap<Long,SoftReference<CacheEntry>>();
      map.put(entryID, ref);
      idMap.put(backend, map);
    }
    else
    {
      map.put(entryID, ref);
    }

    return true;
  }



  /**
   * Removes the specified entry from the cache.
   *
   * @param  entryDN  The DN of the entry to remove from the cache.
   */
  public void removeEntry(DN entryDN)
  {


    SoftReference<CacheEntry> ref = dnMap.remove(entryDN);
    if (ref != null)
    {
      ref.clear();

      CacheEntry cacheEntry = ref.get();
      if (cacheEntry != null)
      {
        Backend backend = cacheEntry.getBackend();

        ConcurrentHashMap<Long,SoftReference<CacheEntry>> map =
             idMap.get(backend);
        if (map != null)
        {
          ref = map.remove(cacheEntry.getEntryID());
          if (ref != null)
          {
            ref.clear();
          }
        }
      }
    }
  }



  /**
   * Removes all entries from the cache.  The cache should still be available
   * for future use.
   */
  public void clear()
  {

    dnMap.clear();
    idMap.clear();
  }



  /**
   * Removes all entries from the cache that are associated with the provided
   * backend.
   *
   * @param  backend  The backend for which to flush the associated entries.
   */
  public void clearBackend(Backend backend)
  {


    // FIXME -- Would it be better just to dump everything?
    ConcurrentHashMap<Long,SoftReference<CacheEntry>> map =
         idMap.remove(backend);
    if (map != null)
    {
      for (SoftReference<CacheEntry> ref : map.values())
      {
        CacheEntry cacheEntry = ref.get();
        if (cacheEntry != null)
        {
          dnMap.remove(cacheEntry.getDN());
        }

        ref.clear();
      }

      map.clear();
    }
  }



  /**
   * Removes all entries from the cache that are below the provided DN.
   *
   * @param  baseDN  The base DN below which all entries should be flushed.
   */
  public void clearSubtree(DN baseDN)
  {


    // Determine the backend used to hold the specified base DN and clear it.
    Backend backend = DirectoryServer.getBackend(baseDN);
    if (backend == null)
    {
      // FIXME -- Should we clear everything just to be safe?
      return;
    }
    else
    {
      clearBackend(backend);
    }
  }



  /**
   * Attempts to react to a scenario in which it is determined that the system
   * is running low on available memory.  In this case, the entry cache should
   * attempt to free some memory if possible to try to avoid out of memory
   * errors.
   */
  public void handleLowMemory()
  {


    // This function should automatically be taken care of by the nature of the
    // soft references used in this cache.
    // FIXME -- Do we need to do anything at all here?
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


    int msgID = MSGID_SOFTREFCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutAttr =
         new IntegerWithUnitConfigAttribute(ATTR_SOFTREFCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0, lockTimeout,
                                            TIME_UNIT_MILLISECONDS_FULL);
    attrList.add(lockTimeoutAttr);


    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_INCLUDE_FILTERS;
    ArrayList<String> includeStrings =
         new ArrayList<String>(includeFilters.size());
    for (SearchFilter f : includeFilters)
    {
      includeStrings.add(f.toString());
    }
    StringConfigAttribute includeAttr =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_INCLUDE_FILTER,
                                   getMessage(msgID), false, true, false,
                                   includeStrings);
    attrList.add(includeAttr);


    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    ArrayList<String> excludeStrings =
         new ArrayList<String>(excludeFilters.size());
    for (SearchFilter f : excludeFilters)
    {
      excludeStrings.add(f.toString());
    }
    StringConfigAttribute excludeAttr =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_EXCLUDE_FILTER,
                                   getMessage(msgID), false, true, false,
                                   excludeStrings);
    attrList.add(excludeAttr);


    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {


    // Start out assuming that the configuration is valid.
    boolean configIsAcceptable = true;


    // Determine the lock timeout to use when interacting with the lock manager.
    int msgID = MSGID_SOFTREFCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutStub =
         new IntegerWithUnitConfigAttribute(ATTR_SOFTREFCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute lockTimeoutAttr =
             (IntegerWithUnitConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_SOFTREFCACHE_INVALID_LOCK_TIMEOUT;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_INCLUDE_FILTER,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute includeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(includeStub);
      if (includeAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        List<String> filterStrings = includeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty())
        {
          // There are no include filters, so we'll allow anything by default.
        }
        else
        {
          for (String filterString : filterStrings)
          {
            try
            {
              SearchFilter.createFilterFromString(filterString);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTER;
              unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN),
                                            filterString,
                                            stackTraceToSingleLineString(e)));
              configIsAcceptable = false;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be excluded from the cache.
    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_EXCLUDE_FILTER,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute excludeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(excludeStub);
      if (excludeAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        List<String> filterStrings = excludeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty())
        {
          // There are no exclude filters, so we'll allow anything by default.
        }
        else
        {
          for (String filterString : filterStrings)
          {
            try
            {
              SearchFilter.createFilterFromString(filterString);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTER;
              unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN),
                                            filterString,
                                            stackTraceToSingleLineString(e)));
              configIsAcceptable = false;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    return configIsAcceptable;
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
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {


    // Create a set of variables to use for the result.
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();
    boolean           configIsAcceptable  = true;


    // Determine the lock timeout to use when interacting with the lock manager.
    long newLockTimeout = LockManager.DEFAULT_TIMEOUT;
    int msgID = MSGID_SOFTREFCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutStub =
         new IntegerWithUnitConfigAttribute(ATTR_SOFTREFCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute lockTimeoutAttr =
             (IntegerWithUnitConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
      if (lockTimeoutAttr != null)
      {
        newLockTimeout = lockTimeoutAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_SOFTREFCACHE_INVALID_LOCK_TIMEOUT;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      configIsAcceptable = false;
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    HashSet<SearchFilter> newIncludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_INCLUDE_FILTER,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute includeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(includeStub);
      if (includeAttr != null)
      {
        List<String> filterStrings = includeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty())
        {
          // There are no include filters, so we'll allow anything by default.
        }
        else
        {
          for (String filterString : filterStrings)
          {
            try
            {
              newIncludeFilters.add(
                   SearchFilter.createFilterFromString(filterString));
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTER;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                      filterString,
                                      stackTraceToSingleLineString(e)));

              if (resultCode == ResultCode.SUCCESS)
              {
                resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
              }

              configIsAcceptable = false;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTERS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      configIsAcceptable = false;
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be exclude from the cache.
    HashSet<SearchFilter> newExcludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_SOFTREFCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
         new StringConfigAttribute(ATTR_SOFTREFCACHE_EXCLUDE_FILTER,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute excludeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(excludeStub);
      if (excludeAttr != null)
      {
        List<String> filterStrings = excludeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty())
        {
          // There are no exclude filters, so we'll allow anything by default.
        }
        else
        {
          for (String filterString : filterStrings)
          {
            try
            {
              newExcludeFilters.add(
                   SearchFilter.createFilterFromString(filterString));
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTER;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                      filterString,
                                      stackTraceToSingleLineString(e)));

              if (resultCode == ResultCode.SUCCESS)
              {
                resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
              }

              configIsAcceptable = false;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTERS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      configIsAcceptable = false;
    }


    if (configIsAcceptable)
    {
      if (lockTimeout != newLockTimeout)
      {
        lockTimeout = newLockTimeout;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_SOFTREFCACHE_UPDATED_LOCK_TIMEOUT,
                                  lockTimeout));
        }
      }

      if (!includeFilters.equals(newIncludeFilters))
      {
        includeFilters = newIncludeFilters;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_SOFTREFCACHE_UPDATED_INCLUDE_FILTERS));
        }
      }

      if (!excludeFilters.equals(newExcludeFilters))
      {
        excludeFilters = newExcludeFilters;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_SOFTREFCACHE_UPDATED_EXCLUDE_FILTERS));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Operate in a loop, receiving notification of soft references that have been
   * freed and removing the corresponding entries from the cache.
   */
  public void run()
  {
    while (true)
    {
      try
      {
        CacheEntry freedEntry = referenceQueue.remove().get();

        if (freedEntry != null)
        {
          SoftReference<CacheEntry> ref = dnMap.remove(freedEntry.getDN());

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

              ConcurrentHashMap<Long,SoftReference<CacheEntry>> map =
                   idMap.get(freedEntry.getBackend());
              if (map != null)
              {
                ref = map.remove(freedEntry.getEntryID());
                if (ref != null)
                {
                  ref.clear();
                }
              }
            }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}

