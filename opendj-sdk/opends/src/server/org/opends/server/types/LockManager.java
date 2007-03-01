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
package org.opends.server.types;



import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static
    org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugError;
import static
    org.opends.server.loggers.debug.DebugLogger.debugWarning;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server component that can keep track
 * of all locks needed throughout the Directory Server.  It is
 * intended primarily for entry locking but support for other types of
 * objects might be added in the future.
 */
public class LockManager
{



  /**
   * The number of buckets into which the set of global DN locks will
   * be broken.
   */
  public static final int NUM_GLOBAL_DN_LOCKS =
       (10 * Runtime.getRuntime().availableProcessors());



  /**
   * The initial capacity to use for the DN lock hashtable.
   */
  public static final int DN_TABLE_INITIAL_SIZE = 50;



  /**
   * The load factor to use for the DN lock hashtable.
   */
  public static final float DN_TABLE_LOAD_FACTOR = 0.75F;



  /**
   * The default length of time in milliseconds to wait while
   * attempting to acquire a read or write lock.
   */
  public static final long DEFAULT_TIMEOUT = 3000;



  // The set of global DN locks that we need to ensure thread safety
  // for all of the other operations.
  private static ReentrantLock[] globalDNLocks;

  // The set of entry locks that the server knows about.
  private static ConcurrentHashMap<DN,ReentrantReadWriteLock>
                      entryLocks;



  // Initialize all of the lock variables.
  static
  {
    // Create the set of global DN locks.
    globalDNLocks = new ReentrantLock[NUM_GLOBAL_DN_LOCKS];
    for (int i=0; i < NUM_GLOBAL_DN_LOCKS; i++)
    {
      globalDNLocks[i] = new ReentrantLock();
    }


    // Create an empty table for holding the entry locks.
    entryLocks = new ConcurrentHashMap<DN,ReentrantReadWriteLock>(
         DN_TABLE_INITIAL_SIZE, DN_TABLE_LOAD_FACTOR,
         NUM_GLOBAL_DN_LOCKS);
  }



  /**
   * Attempts to acquire a read lock on the specified entry.  It will
   * succeed only if the lock is not already held.  If any blocking is
   * required, then this call will fail rather than block.
   *
   * @param  entryDN  The DN of the entry for which to obtain the read
   *                  lock.
   *
   * @return  The read lock that was acquired, or <CODE>null</CODE> if
   *          it was not possible to obtain a read lock for some
   *          reason.
   */
  public static Lock tryLockRead(DN entryDN)
  {
    int hashCode = (entryDN.hashCode() & 0x7FFFFFFF);


    // Get the hash code for the provided entry DN and determine which
    // global lock to acquire.  This will ensure that no two threads
    // will be allowed to lock or unlock the same entry at any given
    // time, but should allow other entries with different hash codes
    // to be processed.
    ReentrantLock globalLock;
    try
    {
      globalLock = globalDNLocks[hashCode % NUM_GLOBAL_DN_LOCKS];
      if (! globalLock.tryLock())
      {
        return null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);

        // This is not fine.  Some unexpected error occurred.
        debugError(
            "Unexpected exception while trying to obtain the " +
                "global lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }



    // At this point we have the global lock for this bucket.  We must
    // use a try/catch/finally block to ensure that the global lock is
    // always released no matter what.
    try
    {
      // Now check to see if the entry is already in the lock table.
      ReentrantReadWriteLock entryLock = entryLocks.get(entryDN);
      if (entryLock == null)
      {
        // No lock exists for the entry.  Create one and put it in the
        // table.
        entryLock = new ReentrantReadWriteLock();
        if (entryLock.readLock().tryLock())
        {
          entryLocks.put(entryDN, entryLock);
          return entryLock.readLock();
        }
        else
        {
          // This should never happen since we just created the lock.
          if (debugEnabled())
          {
            debugError(
                "Unable to acquire read lock on newly-created lock " +
                "for entry %s", entryDN);
          }
          return null;
        }
      }
      else
      {
        // There is already a lock for the entry.  Try to get its read
        // lock.
        if (entryLock.readLock().tryLock())
        {
          // We got the read lock.  We don't need to do anything else.
          return entryLock.readLock();
        }
        else
        {
          // We couldn't get the read lock.  Write a debug message.
          if (debugEnabled())
          {
            debugWarning(
                "Unable to acquire a read lock for entry %s that " +
                "was already present in the lock table.", entryDN);
          }
          return null;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);

        // This is not fine.  Some unexpected error occurred.
        debugError(
            "Unexpected exception while trying to obtain a read " +
                "lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }
    finally
    {
      // This will always be called even after a return.
      globalLock.unlock();
    }
  }



  /**
   * Attempts to acquire a read lock for the specified entry.
   * Multiple threads can hold the read lock concurrently for an entry
   * as long as the write lock is held.  If the write lock is held,
   * then no other read or write locks will be allowed for that entry
   * until the write lock is released.  A default timeout will be used
   * for the lock.
   *
   * @param  entryDN  The DN of the entry for which to obtain the read
   *                  lock.
   *
   * @return  The read lock that was acquired, or <CODE>null</CODE> if
   *          it was not possible to obtain a read lock for some
   *          reason.
   */
  public static Lock lockRead(DN entryDN)
  {
    return lockRead(entryDN, DEFAULT_TIMEOUT);
  }



  /**
   * Attempts to acquire a read lock for the specified entry.
   * Multiple threads can hold the read lock concurrently for an entry
   * as long as the write lock is held.  If the write lock is held,
   * then no other read or write locks will be allowed for that entry
   * until the write lock is released.
   *
   * @param  entryDN  The DN of the entry for which to obtain the read
   *                  lock.
   * @param  timeout  The maximum length of time in milliseconds to
   *                  wait for the lock before timing out.
   *
   * @return  The read lock that was acquired, or <CODE>null</CODE> if
   *          it was not possible to obtain a read lock for some
   *          reason.
   */
  public static Lock lockRead(DN entryDN, long timeout)
  {
    int hashCode = (entryDN.hashCode() & 0x7FFFFFFF);


    // Get the hash code for the provided entry DN and determine which
    // global lock to acquire.  This will ensure that no two threads
    // will be allowed to lock or unlock the same entry at any given
    // time, but should allow other entries with different hash codes
    // to be processed.
    ReentrantLock globalLock;
    try
    {
      globalLock = globalDNLocks[hashCode % NUM_GLOBAL_DN_LOCKS];
      if (! globalLock.tryLock(timeout, TimeUnit.MILLISECONDS))
      {
        return null;
      }
    }
    catch (InterruptedException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      // This is fine.  The thread trying to acquire the lock was
      // interrupted.
      return null;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This is not fine.  Some unexpected error occurred.
      if (debugEnabled())
      {
        debugError(
            "Unexpected exception while trying to obtain the " +
                "global lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }



    // At this point we have the global lock for this bucket.  We must
    // use a try/catch/finally block to ensure that the global lock is
    // always released no matter what.
    try
    {
      // Now check to see if the entry is already in the lock table.
      ReentrantReadWriteLock entryLock = entryLocks.get(entryDN);
      if (entryLock == null)
      {
        // No lock exists for the entry.  Create one and put it in the
        // table.
        entryLock = new ReentrantReadWriteLock();
        if (entryLock.readLock().tryLock(timeout,
                                         TimeUnit.MILLISECONDS))
        {
          entryLocks.put(entryDN, entryLock);
          return entryLock.readLock();
        }
        else
        {
          // This should never happen since we just created the lock.
          if (debugEnabled())
          {
            debugError(
                "Unable to acquire read lock on newly-created lock " +
                "for entry %s", entryDN);
          }
          return null;
        }
      }
      else
      {
        // There is already a lock for the entry.  Try to get its read
        // lock.
        if (entryLock.readLock().tryLock(timeout,
                                         TimeUnit.MILLISECONDS))
        {
          // We got the read lock.  We don't need to do anything else.
          return entryLock.readLock();
        }
        else
        {
          // We couldn't get the read lock.  Write a debug message.
          if (debugEnabled())
          {
            debugWarning(
                "Unable to acquire a read lock for entry %s that " +
                "was already present in the lock table.", entryDN);
          }
          return null;
        }
      }
    }
    catch (InterruptedException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      // This is fine.  The thread trying to acquire the lock was
      // interrupted.
      return null;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This is not fine.  Some unexpected error occurred.
      if (debugEnabled())
      {
        debugError(
            "Unexpected exception while trying to obtain a read " +
                "lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }
    finally
    {
      // This will always be called even after a return.
      globalLock.unlock();
    }
  }



  /**
   * Attempts to acquire a write lock on the specified entry.  It will
   * succeed only if the lock is not already held.  If any blocking is
   * required, then this call will fail rather than block.
   *
   * @param  entryDN  The DN of the entry for which to obtain the
   *                  write lock.
   *
   * @return  The write lock that was acquired, or <CODE>null</CODE>
   *          if it was not possible to obtain a write lock for some
   *          reason.
   */
  public static Lock tryLockWrite(DN entryDN)
  {
    int hashCode = (entryDN.hashCode() & 0x7FFFFFFF);


    // Get the hash code for the provided entry DN and determine which
    // global lock to acquire.  This will ensure that no two threads
    // will be allowed to lock or unlock the same entry at any given
    // time, but should allow other entries with different hash codes
    // to be processed.
    ReentrantLock globalLock;
    try
    {
      globalLock = globalDNLocks[hashCode % NUM_GLOBAL_DN_LOCKS];
      if (! globalLock.tryLock())
      {
        return null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);

        // This is not fine.  Some unexpected error occurred.
        debugError(
            "Unexpected exception while trying to obtain the " +
                "global lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }



    // At this point we have the global lock for this bucket.  We must
    // use a try/catch/finally block to ensure that the global lock is
    // always released no matter what.
    try
    {
      // Now check to see if the entry is already in the lock table.
      ReentrantReadWriteLock entryLock = entryLocks.get(entryDN);
      if (entryLock == null)
      {
        // No lock exists for the entry.  Create one and put it in the
        // table.
        entryLock = new ReentrantReadWriteLock();
        if (entryLock.writeLock().tryLock())
        {
          entryLocks.put(entryDN, entryLock);
          return entryLock.writeLock();
        }
        else
        {
          // This should never happen since we just created the lock.
          if (debugEnabled())
          {
            debugError(
                "Unable to acquire write lock on newly-created " +
                    "lock for entry %s", entryDN);
          }
          return null;
        }
      }
      else
      {
        // There is already a lock for the entry.  Try to get its
        // write lock.
        if (entryLock.writeLock().tryLock())
        {
          // We got the write lock.  We don't need to do anything
          // else.
          return entryLock.writeLock();
        }
        else
        {
          // We couldn't get the write lock.  Write a debug message.
          if (debugEnabled())
          {
            debugWarning(
                "Unable to acquire the write lock for entry %s " +
                    "that was already present in the lock table.",
                entryDN);
          }
          return null;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);

        // This is not fine.  Some unexpected error occurred.
        debugError(
            "Unexpected exception while trying to obtain the write " +
                "lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }
    finally
    {
      // This will always be called even after a return.
      globalLock.unlock();
    }
  }



  /**
   * Attempts to acquire the write lock for the specified entry.  Only
   * a single thread may hold the write lock for an entry at any given
   * time, and during that time no read locks may be held for it.  A
   * default timeout will be used for the lock.
   *
   * @param  entryDN  The DN of the entry for which to obtain the
   *                  write lock.
   *
   * @return  The write lock that was acquired, or <CODE>null</CODE>
   *          if it was not possible to obtain a write lock for some
   *          reason.
   */
  public static Lock lockWrite(DN entryDN)
  {
    return lockWrite(entryDN, DEFAULT_TIMEOUT);
  }



  /**
   * Attempts to acquire the write lock for the specified entry.  Only
   * a single thread may hold the write lock for an entry at any given
   * time, and during that time no read locks may be held for it.
   *
   * @param  entryDN  The DN of the entry for which to obtain the
   *                  write lock.
   * @param  timeout  The maximum length of time in milliseconds to
   *                  wait for the lock before timing out.
   *
   * @return  The write lock that was acquired, or <CODE>null</CODE>
   *          if it was not possible to obtain a read lock for some
   *          reason.
   */
  public static Lock lockWrite(DN entryDN, long timeout)
  {
    int hashCode = (entryDN.hashCode() & 0x7FFFFFFF);


    // Get the hash code for the provided entry DN and determine which
    // global lock to acquire.  This will ensure that no two threads
    // will be allowed to lock or unlock the same entry at any given
    // time, but should allow other entries with different hash codes
    // to be processed.
    ReentrantLock globalLock;
    try
    {
      globalLock = globalDNLocks[hashCode % NUM_GLOBAL_DN_LOCKS];
      if (! globalLock.tryLock(timeout, TimeUnit.MILLISECONDS))
      {
        return null;
      }
    }
    catch (InterruptedException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      // This is fine.  The thread trying to acquire the lock was
      // interrupted.
      return null;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This is not fine.  Some unexpected error occurred.
      if (debugEnabled())
      {
        debugError(
            "Unexpected exception while trying to obtain the " +
                "global lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }



    // At this point we have the global lock for this bucket.  We must
    // use a try/catch/finally block to ensure that the global lock is
    // always released no matter what.
    try
    {
      // Now check to see if the entry is already in the lock table.
      ReentrantReadWriteLock entryLock = entryLocks.get(entryDN);
      if (entryLock == null)
      {
        // No lock exists for the entry.  Create one and put it in the
        // table.
        entryLock = new ReentrantReadWriteLock();
        if (entryLock.writeLock().tryLock(timeout,
                                          TimeUnit.MILLISECONDS))
        {
          entryLocks.put(entryDN, entryLock);
          return entryLock.writeLock();
        }
        else
        {
          // This should never happen since we just created the lock.
          if (debugEnabled())
          {
            debugError(
                "Unable to acquire write lock on newly-created " +
                    "lock for entry %s", entryDN.toString());
          }
          return null;
        }
      }
      else
      {
        // There is already a lock for the entry.  Try to get its
        // write lock.
        if (entryLock.writeLock().tryLock(timeout,
                                          TimeUnit.MILLISECONDS))
        {
          // We got the write lock.  We don't need to do anything
          // else.
          return entryLock.writeLock();
        }
        else
        {
          // We couldn't get the write lock.  Write a debug message.
          if (debugEnabled())
          {
            debugWarning(
                "Unable to acquire the write lock for entry %s " +
                    "that was already present in the lock table.",
                entryDN);
          }
          return null;
        }
      }
    }
    catch (InterruptedException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      // This is fine.  The thread trying to acquire the lock was
      // interrupted.
      return null;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);

        // This is not fine.  Some unexpected error occurred.
        debugError(
            "Unexpected exception while trying to obtain the write " +
            "lock for entry %s: %s",
            entryDN, stackTraceToSingleLineString(e));
      }
      return null;
    }
    finally
    {
      // This will always be called even after a return.
      globalLock.unlock();
    }
  }



  /**
   * Releases a read or write lock held on the specified entry.
   *
   * @param  entryDN  The DN of the entry for which to release the
   *                  lock.
   * @param  lock     The read or write lock held for the entry.
   */
  public static void unlock(DN entryDN, Lock lock)
  {
    // Unlock the entry without grabbing any additional locks.
    try
    {
      lock.unlock();
    }
    catch (Exception e)
    {
      // This should never happen.  However, if it does, then just
      // capture the exception and continue because it may still be
      // necessary to remove the lock for the entry from the table.
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }


    int hashCode = (entryDN.hashCode() & 0x7FFFFFFF);


    // Now grab the global lock for the entry and check to see if we
    // can remove it from the table.
    ReentrantLock globalLock;
    try
    {
      globalLock = globalDNLocks[hashCode % NUM_GLOBAL_DN_LOCKS];

      // This will block until it acquires the lock or until it is
      // interrupted.
      globalLock.lockInterruptibly();
    }
    catch (InterruptedException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      // The lock trying to acquire the lock was interrupted.  In this
      // case, we'll just return.  The worst that could happen here is
      // that a lock that isn't held by anything is still in the table
      // which will just consume a little memory.
      return;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This is not fine.  Some unexpected error occurred.  But
      // again, the worst that could happen is that we may not clean
      // up an unheld lock, which isn't really that big a deal unless
      // it happens too often.
      debugError(
          "Unexpected exception while trying to obtain the global " +
              "lock for entry %s: %s",
          entryDN, stackTraceToSingleLineString(e));
      return;
    }


    // At this point we have the global lock for this bucket.  We must
    // use a try/catch/finally block to ensure that the global lock is
    // always released no matter what.
    try
    {
      ReentrantReadWriteLock entryLock = entryLocks.get(entryDN);
      if ((entryLock != null) &&
          (entryLock.getReadLockCount() == 0) &&
          (! entryLock.isWriteLocked()))
      {
        // This lock isn't held so we can remove it from the table.
        entryLocks.remove(entryDN);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This should never happen.
      debugError(
          "Unexpected exception while trying to determine whether " +
              "the lock for entry %s can be removed: %s",
          entryDN, stackTraceToSingleLineString(e));
    }
    finally
    {
      globalLock.unlock();
    }
  }



  /**
   * Removes any reference to the specified entry from the lock table.
   * This may be helpful if there is a case where a lock has been
   * orphaned somehow and must be removed before other threads may
   * acquire it.
   *
   * @param  entryDN  The DN of the entry for which to remove the lock
   *                  from the table.
   *
   * @return  The read write lock that was removed from the table, or
   *          <CODE>null</CODE> if nothing was in the table for the
   *          specified entry.  If a lock object is returned, it may
   *          be possible to get information about who was holding it.
   */
  public static ReentrantReadWriteLock destroyLock(DN entryDN)
  {
    return entryLocks.remove(entryDN);
  }



  /**
   * Retrieves the number of entries currently held in the lock table.
   * Note that this may be an expensive operation.
   *
   * @return  The number of entries currently held in the lock table.
   */
  public static int lockTableSize()
  {
    return entryLocks.size();
  }
}

