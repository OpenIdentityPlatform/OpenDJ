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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
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
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.LockManager;
import org.opends.server.types.CacheEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LockType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



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
       extends EntryCache
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.FIFOEntryCache";



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



  /**
   * Creates a new instance of this FIFO entry cache.
   */
  public FIFOEntryCache()
  {
    super();

    assert debugConstructor(CLASS_NAME);

    // All initialization should be performed in the initializeEntryCache.
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
    assert debugEnter(CLASS_NAME, "initializeEntryCache",
                      String.valueOf(configEntry));

    configEntryDN = configEntry.getDN();


    // Initialize the cache structures.
    idMap     = new HashMap<Backend,HashMap<Long,CacheEntry>>();
    dnMap     = new LinkedHashMap<DN,CacheEntry>();
    cacheLock = new ReentrantLock();
    runtime   = Runtime.getRuntime();


    // Determine the maximum memory usage as a percentage of the total JVM
    // memory.
    maxMemoryPercent = DEFAULT_FIFOCACHE_MAX_MEMORY_PCT;
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
                                    getMessage(msgID), true, false, false, true,
                                    1, true, 100);
    try
    {
      IntegerConfigAttribute maxMemoryPctAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxMemoryPctStub);
      if (maxMemoryPctAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        maxMemoryPercent = maxMemoryPctAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_MEMORY_PCT,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e),
               maxMemoryPercent);
    }

    maxAllowedMemory = runtime.maxMemory() / 100 * maxMemoryPercent;


    // Determine the maximum number of entries that we will allow in the cache.
    maxEntries = DEFAULT_FIFOCACHE_MAX_ENTRIES;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0);
    try
    {
      IntegerConfigAttribute maxEntriesAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxEntriesStub);
      if (maxEntriesAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        maxEntries = maxEntriesAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_ENTRIES,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }


    // Determine the lock timeout to use when interacting with the lock manager.
    lockTimeout = DEFAULT_FIFOCACHE_LOCK_TIMEOUT;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerConfigAttribute lockTimeoutStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0);
    try
    {
      IntegerConfigAttribute lockTimeoutAttr =
             (IntegerConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
      if (lockTimeoutAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        lockTimeout = lockTimeoutAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_FIFOCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e),
               lockTimeout);
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    includeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
         new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
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
              assert debugException(CLASS_NAME, "initializeEntryCache", e);

              // We couldn't decode this filter.  Log a warning and continue.
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING,
                       MSGID_FIFOCACHE_CANNOT_DECODE_INCLUDE_FILTER,
                       String.valueOf(configEntryDN), filterString,
                       stackTraceToSingleLineString(e));
            }
          }

          if (includeFilters.isEmpty())
          {
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_FIFOCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
                     String.valueOf(configEntryDN));
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_FIFOCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be excluded from the cache.
    excludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
         new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
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
              assert debugException(CLASS_NAME, "initializeEntryCache", e);

              // We couldn't decode this filter.  Log a warning and continue.
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING,
                       MSGID_FIFOCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
                       String.valueOf(configEntryDN), filterString,
                       stackTraceToSingleLineString(e));
            }
          }

          if (excludeFilters.isEmpty())
          {
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_FIFOCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
                     String.valueOf(configEntryDN));
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_FIFOCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS,
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
    assert debugEnter(CLASS_NAME, "finalizeEntryCache");


    // Release all memory currently in use by this cache.
    cacheLock.lock();

    try
    {
      idMap.clear();
      dnMap.clear();

      // FIXME -- Should we do this?
      runtime.gc();
    }
    catch (Exception e)
    {
      // This should never happen.
      assert debugException(CLASS_NAME, "finalizeEntryCache", e);
    }
    finally
    {
      cacheLock.unlock();
    }
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
    assert debugEnter(CLASS_NAME, "containsEntry", String.valueOf(entryDN));

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
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN));


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
    assert debugEnter(CLASS_NAME, "getEntryID", String.valueOf(entryDN));

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
  public Entry getEntry(DN entryDN, LockType lockType, List<Lock> lockList)
  {
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN),
                      String.valueOf(lockType), "java.util.List<Lock>");


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
            assert debugException(CLASS_NAME, "getEntry", e);

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, readLock);
            }
            catch (Exception e2)
            {
              assert debugException(CLASS_NAME, "getEntry", e2);
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
            assert debugException(CLASS_NAME, "getEntry", e);

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, writeLock);
            }
            catch (Exception e2)
            {
              assert debugException(CLASS_NAME, "getEntry", e2);
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
  public Entry getEntry(Backend backend, long entryID, LockType lockType,
                        List<Lock> lockList)
  {
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(backend),
                      String.valueOf(entryID), String.valueOf(lockType),
                      "java.util.List<Lock>");

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
            assert debugException(CLASS_NAME, "getEntry", e);

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entry.getDN(), readLock);
            }
            catch (Exception e2)
            {
              assert debugException(CLASS_NAME, "getEntry", e2);
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
            assert debugException(CLASS_NAME, "getEntry", e);

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entry.getDN(), writeLock);
            }
            catch (Exception e2)
            {
              assert debugException(CLASS_NAME, "getEntry", e2);
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
    assert debugEnter(CLASS_NAME, "putEntry", String.valueOf(entry),
                      String.valueOf(backend), String.valueOf(entryID));


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
          assert debugException(CLASS_NAME, "putEntry", e);

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
          assert debugException(CLASS_NAME, "putEntry", e);

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
      assert debugException(CLASS_NAME, "putEntry", e);

      return;
    }


    // At this point, we hold the lock.  No matter what, we must release the
    // lock before leaving this method, so do that in a finally block.
    try
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


      // See if we need to free memory to bring the usage limits in line.
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      while (usedMemory > maxAllowedMemory)
      {
        // Dump 1% of the entries and check again.
        int numEntries = entryCount / 100;
        Iterator<CacheEntry> iterator = dnMap.values().iterator();
        while (iterator.hasNext() && (numEntries > 0))
        {
          CacheEntry ce = iterator.next();
          iterator.remove();

          HashMap<Long,CacheEntry> m = idMap.get(ce.getBackend());
          if (m != null)
          {
            m.remove(ce.getEntryID());
          }

          numEntries--;
        }

        // FIXME -- Is there a better way to free the memory?
        runtime.gc();

        usedMemory = runtime.totalMemory() - runtime.freeMemory();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "putEntry", e);

      return;
    }
    finally
    {
      cacheLock.unlock();
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
  public boolean putEntryIfAbsent(Entry entry, Backend backend, long entryID)
  {
    assert debugEnter(CLASS_NAME, "putEntryIfAbsent", String.valueOf(entry),
                      String.valueOf(backend), String.valueOf(entryID));


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
          assert debugException(CLASS_NAME, "putEntry", e);

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
          assert debugException(CLASS_NAME, "putEntry", e);

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
      assert debugException(CLASS_NAME, "putEntry", e);

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


      // See if we need to free memory to bring the usage limits in line.
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      while (usedMemory > maxAllowedMemory)
      {
        // Dump 1% of the entries and check again.
        int numEntries = entryCount / 100;
        Iterator<CacheEntry> iterator = dnMap.values().iterator();
        while (iterator.hasNext() && (numEntries > 0))
        {
          CacheEntry ce = iterator.next();
          iterator.remove();

          HashMap<Long,CacheEntry> m = idMap.get(ce.getBackend());
          if (m != null)
          {
            m.remove(ce.getEntryID());
          }

          numEntries--;
        }

        // FIXME -- Is there a better way to free the memory?
        runtime.gc();

        usedMemory = runtime.totalMemory() - runtime.freeMemory();
      }


      return true;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "putEntry", e);

      // We can't be sure there wasn't a conflict, so return false.
      return false;
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * Removes the specified entry from the cache.
   *
   * @param  entryDN  The DN of the entry to remove from the cache.
   */
  public void removeEntry(DN entryDN)
  {
    assert debugEnter(CLASS_NAME, "removeEntry", String.valueOf(entryDN));


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
      assert debugException(CLASS_NAME, "removeEntry", e);

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * Removes all entries from the cache.  The cache should still be available
   * for future use.
   */
  public void clear()
  {
    assert debugEnter(CLASS_NAME, "clear");

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
      assert debugException(CLASS_NAME, "clear", e);

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * Removes all entries from the cache that are associated with the provided
   * backend.
   *
   * @param  backend  The backend for which to flush the associated entries.
   */
  public void clearBackend(Backend backend)
  {
    assert debugEnter(CLASS_NAME, "clearBackend", String.valueOf(backend));

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
      assert debugException(CLASS_NAME, "clearBackend", e);

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * Removes all entries from the cache that are below the provided DN.
   *
   * @param  baseDN  The base DN below which all entries should be flushed.
   */
  public void clearSubtree(DN baseDN)
  {
    assert debugEnter(CLASS_NAME, "clearSubtree", String.valueOf(baseDN));


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
      assert debugException(CLASS_NAME, "clearBackend", e);

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheLock.unlock();
    }
  }



  /**
   * Clears all entries at or below the specified base DN that are associated
   * with the given backend.  The caller must already hold the cache lock.
   *
   * @param  baseDN   The base DN below which all entries should be flushed.
   * @param  backend  The backend for which to remove the appropriate entries.
   */
  private void clearSubtree(DN baseDN, Backend backend)
  {
    assert debugEnter(CLASS_NAME, "clearSubtree", String.valueOf(baseDN),
                      String.valueOf(backend));


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
      if ((entriesExamined % 1000)  == 0)
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
   * Attempts to react to a scenario in which it is determined that the system
   * is running low on available memory.  In this case, the entry cache should
   * attempt to free some memory if possible to try to avoid out of memory
   * errors.
   */
  public void handleLowMemory()
  {
    assert debugEnter(CLASS_NAME, "handleLowMemory");


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
      assert debugException(CLASS_NAME, "handleLowMemory", e);

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
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

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
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

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
    IntegerConfigAttribute lockTimeoutAttr =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0, lockTimeout);
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
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    // Start out assuming that the configuration is valid.
    boolean configIsAcceptable = true;


    // Determine the maximum memory usage as a percentage of the total JVM
    // memory.
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
                                    getMessage(msgID), true, false, false, true,
                                    1, true, 100);
    try
    {
      IntegerConfigAttribute maxMemoryPctAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxMemoryPctStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_MEMORY_PCT;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    // Determine the maximum number of entries that we will allow in the cache.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0);
    try
    {
      IntegerConfigAttribute maxEntriesAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxEntriesStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_ENTRIES;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    // Determine the lock timeout to use when interacting with the lock manager.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerConfigAttribute lockTimeoutStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0);
    try
    {
      IntegerConfigAttribute lockTimeoutAttr =
             (IntegerConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_LOCK_TIMEOUT;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
         new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
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
              assert debugException(CLASS_NAME, "initializeEntryCache", e);

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER;
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
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    // Determine the set of cache filters that can be used to control the
    // entries that should be excluded from the cache.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
         new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
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
              assert debugException(CLASS_NAME, "initializeEntryCache", e);

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER;
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
      assert debugException(CLASS_NAME, "initializeEntryCache", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTERS;
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
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    // Create a set of variables to use for the result.
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();
    boolean           configIsAcceptable  = true;


    // Determine the maximum memory usage as a percentage of the total JVM
    // memory.
    int newMaxMemoryPercent = DEFAULT_FIFOCACHE_MAX_MEMORY_PCT;
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
                                    getMessage(msgID), true, false, false, true,
                                    1, true, 100);
    try
    {
      IntegerConfigAttribute maxMemoryPctAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxMemoryPctStub);
      if (maxMemoryPctAttr != null)
      {
        newMaxMemoryPercent = maxMemoryPctAttr.pendingIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_MEMORY_PCT;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = ResultCode.CONSTRAINT_VIOLATION;
      configIsAcceptable = false;
    }


    // Determine the maximum number of entries that we will allow in the cache.
    long newMaxEntries = DEFAULT_FIFOCACHE_MAX_ENTRIES;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0);
    try
    {
      IntegerConfigAttribute maxEntriesAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxEntriesStub);
      if (maxEntriesAttr != null)
      {
        newMaxEntries = maxEntriesAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_ENTRIES;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      configIsAcceptable = false;
    }


    // Determine the lock timeout to use when interacting with the lock manager.
    long newLockTimeout = DEFAULT_FIFOCACHE_LOCK_TIMEOUT;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerConfigAttribute lockTimeoutStub =
         new IntegerConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                    getMessage(msgID), true, false, false,
                                    true, 0, false, 0);
    try
    {
      IntegerConfigAttribute lockTimeoutAttr =
             (IntegerConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
      if (lockTimeoutAttr != null)
      {
        newLockTimeout = lockTimeoutAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_LOCK_TIMEOUT;
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
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
         new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
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
              assert debugException(CLASS_NAME, "applyNewConfiguration", e);

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER;
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
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTERS;
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
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
         new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
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
              assert debugException(CLASS_NAME, "applyNewConfiguration", e);

              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER;
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
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTERS;
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
      if (maxMemoryPercent != newMaxMemoryPercent)
      {
        maxMemoryPercent = newMaxMemoryPercent;
        maxAllowedMemory = runtime.maxMemory() / 100 * maxMemoryPercent;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_MAX_MEMORY_PCT,
                                  maxMemoryPercent, maxAllowedMemory));
        }
      }

      if (maxEntries != newMaxEntries)
      {
        maxEntries = newMaxEntries;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_MAX_ENTRIES,
                                  maxEntries));
        }
      }

      if (lockTimeout != newLockTimeout)
      {
        lockTimeout = newLockTimeout;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_LOCK_TIMEOUT,
                                  lockTimeout));
        }
      }

      if (setsDiffer(includeFilters, newIncludeFilters))
      {
        includeFilters = newIncludeFilters;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_INCLUDE_FILTERS));
        }
      }

      if (setsDiffer(excludeFilters, newExcludeFilters))
      {
        excludeFilters = newExcludeFilters;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_EXCLUDE_FILTERS));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

