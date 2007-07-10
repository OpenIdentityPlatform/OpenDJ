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

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.SoftReferenceEntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CacheEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.LockType;
import org.opends.server.types.SearchFilter;


import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The set of time units that will be used for expressing the task retention
  // time.
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
   * {@inheritDoc}
   */
  public void initializeEntryCache(
      SoftReferenceEntryCacheCfg configuration
      )
      throws ConfigException, InitializationException
  {
    configuration.addSoftReferenceChangeListener (this);
    configEntryDN = configuration.dn();

    dnMap.clear();
    idMap.clear();

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
    dnMap.clear();
    idMap.clear();
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
   * {@inheritDoc}
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
   * {@inheritDoc}
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
                  TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
                    TRACER.debugCaught(DebugLogLevel.ERROR, e2);
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
                  TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
                    TRACER.debugCaught(DebugLogLevel.ERROR, e2);
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
   * {@inheritDoc}
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
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
                TRACER.debugCaught(DebugLogLevel.ERROR, e2);
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
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
                TRACER.debugCaught(DebugLogLevel.ERROR, e2);
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
   * {@inheritDoc}
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
   * {@inheritDoc}
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
   * {@inheritDoc}
   */
  public void clear()
  {
    dnMap.clear();
    idMap.clear();
  }



  /**
   * {@inheritDoc}
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
   * {@inheritDoc}
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
   * {@inheritDoc}
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
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(EntryCacheCfg configuration,
                                           List<String> unacceptableReasons)
  {
    SoftReferenceEntryCacheCfg config =
         (SoftReferenceEntryCacheCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      SoftReferenceEntryCacheCfg configuration,
      List<String>               unacceptableReasons)
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
      SoftReferenceEntryCacheCfg configuration
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
      SoftReferenceEntryCacheCfg          configuration,
      boolean                             applyChanges,
      EntryCacheCommon.ConfigErrorHandler errorHandler
      )
  {
    // Local variables to read configuration.
    DN                    newConfigEntryDN;
    long                  newLockTimeout;
    HashSet<SearchFilter> newIncludeFilters = null;
    HashSet<SearchFilter> newExcludeFilters = null;

    DN configEntryDN = configuration.dn();

    // Read configuration.
    newConfigEntryDN = configuration.dn();
    newLockTimeout   = configuration.getLockTimeout();

    // Get include and exclude filters.
    switch (errorHandler.getConfigPhase())
    {
    case PHASE_INIT:
      newIncludeFilters = EntryCacheCommon.getFilters (
          configuration.getIncludeFilter(),
          MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTER,
          MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
          errorHandler,
          configEntryDN
          );
      newExcludeFilters = EntryCacheCommon.getFilters (
          configuration.getExcludeFilter(),
          MSGID_SOFTREFCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
          MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
          errorHandler,
          configEntryDN
          );
      break;
    case PHASE_ACCEPTABLE:  // acceptable and apply are using the same
    case PHASE_APPLY:       // error ID codes
      newIncludeFilters = EntryCacheCommon.getFilters (
          configuration.getIncludeFilter(),
          MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTER,
          0,
          errorHandler,
          configEntryDN
          );
      newExcludeFilters = EntryCacheCommon.getFilters (
          configuration.getExcludeFilter(),
          MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTER,
          0,
          errorHandler,
          configEntryDN
          );
      break;
    }

    if (applyChanges && errorHandler.getIsAcceptable())
    {
      configEntryDN  = newConfigEntryDN;
      lockTimeout    = newLockTimeout;
      includeFilters = newIncludeFilters;
      excludeFilters = newExcludeFilters;
    }

    return errorHandler.getIsAcceptable();
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}

