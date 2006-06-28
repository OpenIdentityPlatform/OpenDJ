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
package org.opends.server.api;



import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.InitializationException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockType;



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
 */
public abstract class EntryCache
{
  /**
   * Initializes this entry cache implementation so that it will be
   * available for storing and retrieving entries.
   *
   * @param  configEntry  The configuration entry containing the
   *                      settings to use for this entry cache.
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
  public abstract void initializeEntryCache(ConfigEntry configEntry)
         throws ConfigException, InitializationException;



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
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public abstract Entry getEntry(DN entryDN);



  /**
   * Retrieves the entry ID for the entry with the specified DN from
   * the cache.  The caller should have already acquired a read or
   * write lock for the entry if such protection is needed.
   *
   * @param  entryDN  The DN of the entry for which to retrieve the
   *                  entry ID.
   *
   * @return  The entry ID for the requested entry, or -1 if it is not
   *          present in the cache.
   */
  public abstract long getEntryID(DN entryDN);



  /**
   * Retrieves the entry with the specified DN from the cache,
   * obtaining a lock on the entry before it is returned.  If the
   * entry is present in the cache, then a lock will be obtained for
   * that entry and appended to the provided list before the entry is
   * returned.  If the entry is not present, then no lock will be
   * obtained.
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
  public abstract Entry getEntry(DN entryDN, LockType lockType,
                                 List<Lock> lockList);



  /**
   * Retrieves the requested entry if it is present in the cache,
   * obtaining a lock on the entry before it is returned.  If the
   * entry is present in the cache, then a lock  will be obtained for
   * that entry and appended to the provided list before the entry is
   * returned.  If the entry is not present, then no lock will be
   * obtained.
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
  public abstract Entry getEntry(Backend backend, long entryID,
                                 LockType lockType,
                                 List<Lock> lockList);



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
}

