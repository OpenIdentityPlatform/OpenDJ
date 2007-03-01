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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockType;




/**
 * This class defines the default entry cache that will be used in the server if
 * none is configured.  It does not actually store any entries, so all calls to
 * <CODE>getEntry</CODE> will return <CODE>null</CODE>, and all calls to
 * <CODE>putEntry</CODE> will return immediately without doing anything.
 */
public class DefaultEntryCache
       extends EntryCache
{



  /**
   * Creates a new instance of this default entry cache.
   */
  public DefaultEntryCache()
  {
    super();

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

    // No implementation required.
  }



  /**
   * Performs any necessary cleanup work (e.g., flushing all cached entries and
   * releasing any other held resources) that should be performed when the
   * server is to be shut down or the entry cache destroyed or replaced.
   */
  public void finalizeEntryCache()
  {

    // No implementation required.
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

    // This implementation does not store any entries.
    return false;
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

    // This implementation does not store any entries.
    return null;
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

    // This implementation does not store any entries.
    return -1;
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

    // This implementation does not store entries.
    return null;
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

    // This implementation does not store entries.
    return null;
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

    // This implementation does not store entries.
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

    // This implementation does not store entries, so we will never have a
    // conflict.
    return true;
  }



  /**
   * Removes the specified entry from the cache.
   *
   * @param  entryDN  The DN of the entry to remove from the cache.
   */
  public void removeEntry(DN entryDN)
  {

    // This implementation does not store entries.
  }



  /**
   * Removes all entries from the cache.  The cache should still be available
   * for future use.
   */
  public void clear()
  {

    // This implementation does not store entries.
  }



  /**
   * Removes all entries from the cache that are associated with the provided
   * backend.
   *
   * @param  backend  The backend for which to flush the associated entries.
   */
  public void clearBackend(Backend backend)
  {

    // This implementation does not store entries.
  }



  /**
   * Removes all entries from the cache that are below the provided DN.
   *
   * @param  baseDN  The base DN below which all entries should be flushed.
   */
  public void clearSubtree(DN baseDN)
  {

    // This implementation does not store entries.
  }



  /**
   * Attempts to react to a scenario in which it is determined that the system
   * is running low on available memory.  In this case, the entry cache should
   * attempt to free some memory if possible to try to avoid out of memory
   * errors.
   */
  public void handleLowMemory()
  {

    // This implementation does not store entries, so there are no resources
    // that it can free.
  }
}

