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
package org.opends.server.api;



import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockType;
import org.opends.server.types.LockManager;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.*;



/**
 * This class defines the set of methods that must be implemented by a
 * Directory Server entry cache.  Note that components accessing the
 * entry cache must not depend on any particular behavior.  For
 * example, if a call is made to <CODE>putEntry</CODE> to store an
 * entry in the cache, there is no guarantee that immediately calling
 * <CODE>getEntry</CODE> will be able to retrieve it.  There are
 * several potential reasons for this, including:
 * <UL>
 *   <LI>The entry may have been deleted or replaced by another thread
 *       between the <CODE>putEntry</CODE> and <CODE>getEntry</CODE>
 *       calls.</LI>
 *   <LI>The entry cache may implement a purging mechanism and the
 *       entry added may have been purged between the
 *       <CODE>putEntry</CODE> and <CODE>getEntry</CODE> calls.</LI>
 *   <LI>The entry cache may implement some kind of filtering
 *       mechanism to determine which entries to store, and entries
 *       not matching the appropriate criteria may not be stored.</LI>
 *   <LI>The entry cache may not actually store any entries (this is
 *       the behavior of the default cache that will be used if none
 *       is configured).</LI>
 * </UL>
 *
 * @param  <T>  The type of configuration handled by this entry
 *              cache.
 */
public abstract class EntryCache
       <T extends EntryCacheCfg>
       implements BackendInitializationListener
{
  /**
   * Default constructor which is implicitly called from all entry
   * cache implementations.
   */
  public EntryCache()
  {
    // Register with backend initialization listener to clear cache
    // entries belonging to given backend that about to go offline.
    DirectoryServer.registerBackendInitializationListener(this);
  }



  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The maximum length of time to try to obtain a lock
   * before giving up.
   */
  protected long lockTimeout;



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
                      List<String> unacceptableReasons)
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
   * @return  <CODE>true</CODE> if the entry cache currently contains
   *          the entry with the specified DN, or <CODE>false</CODE>
   *          if not.
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
   *          <CODE>null</CODE> if it is not present.
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
   *                   <CODE>NONE</CODE>).
   * @param  lockList  The list to which the obtained lock will be
   *                   added (note that no lock will be added if the
   *                   lock type was <CODE>NONE</CODE>).
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(DN entryDN,
                        LockType lockType,
                        List<Lock> lockList) {

    if (!containsEntry(entryDN)) {
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
   *                   <CODE>NONE</CODE>).
   * @param  lockList  The list to which the obtained lock will be
   *                   added (note that no lock will be added if the
   *                   lock type was <CODE>NONE</CODE>).
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(Backend backend, long entryID,
                                 LockType lockType,
                                 List<Lock> lockList) {

    // Translate given backend/entryID pair to entryDN.
    DN entryDN = getEntryDN(backend, entryID);
    if (entryDN == null) {
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
   *          <CODE>null</CODE> if it is not present in the cache.
   */
  protected abstract DN getEntryDN(Backend backend, long entryID);



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
   * @return  <CODE>false</CODE> if an existing entry or some other
   *          problem prevented the method from completing
   *          successfully, or <CODE>true</CODE> if there was no
   *          conflict and the entry was either stored or the cache
   *          determined that this entry should never be cached for
   *          some reason.
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
   * {@inheritDoc}
   */
  public void performBackendInitializationProcessing(Backend backend)
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void performBackendFinalizationProcessing(Backend backend)
  {
    // Do not clear any backends if the server is shutting down.
    if ( !(DirectoryServer.getInstance().isShuttingDown()) ) {
      clearBackend(backend);
    }
  }
}
