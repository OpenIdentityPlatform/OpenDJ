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
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server component that can keep track
 * of all locks needed throughout the Directory Server.  It is
 * intended primarily for entry locking but support for other types of
 * objects might be added in the future.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class LockManager
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The default setting for the use of fair ordering locks.
   */
  public static final boolean DEFAULT_FAIR_ORDERING = true;

  /**
   * The default initial size to use for the lock table.
   */
  public static final int DEFAULT_INITIAL_TABLE_SIZE = 64;



  /**
   * The default concurrency level to use for the lock table.
   */
  public static final int DEFAULT_CONCURRENCY_LEVEL = 32;



  /**
   * The default load factor to use for the lock table.
   */
  public static final float DEFAULT_LOAD_FACTOR = 0.75F;



  /**
   * The default length of time in milliseconds to wait while
   * attempting to acquire a read or write lock.
   */
  public static final long DEFAULT_TIMEOUT = 3000;



  // The set of entry locks that the server knows about.
  private static
       ConcurrentHashMap<DN,ReentrantReadWriteLock> lockTable;

  // Whether fair ordering should be used on the locks.
  private static boolean fair;



  // Initialize the lock table.
  static
  {
    DirectoryEnvironmentConfig environmentConfig =
         DirectoryServer.getEnvironmentConfig();
    lockTable = new ConcurrentHashMap<DN,ReentrantReadWriteLock>(
         environmentConfig.getLockManagerTableSize(),
         DEFAULT_LOAD_FACTOR,
         environmentConfig.getLockManagerConcurrencyLevel());
    fair = environmentConfig.getLockManagerFairOrdering();
  }



  /**
   * Recreates the lock table.  This should be called only in the
   * case that the Directory Server is in the process of an in-core
   * restart because it will destroy the existing lock table.
   */
  public synchronized static void reinitializeLockTable()
  {
    ConcurrentHashMap<DN,ReentrantReadWriteLock> oldTable = lockTable;

    DirectoryEnvironmentConfig environmentConfig =
         DirectoryServer.getEnvironmentConfig();
    lockTable = new ConcurrentHashMap<DN,ReentrantReadWriteLock>(
         environmentConfig.getLockManagerTableSize(),
         DEFAULT_LOAD_FACTOR,
         environmentConfig.getLockManagerConcurrencyLevel());

    if  (! oldTable.isEmpty())
    {
      for (DN dn : oldTable.keySet())
      {
        try
        {
          ReentrantReadWriteLock lock = oldTable.get(dn);
          if (lock.isWriteLocked())
          {
            TRACER.debugWarning("Found stale write lock on " +
                                dn.toString());
          }
          else if (lock.getReadLockCount() > 0)
          {
            TRACER.debugWarning("Found stale read lock on " +
                                dn.toString());
          }
          else
          {
            TRACER.debugWarning("Found stale unheld lock on " +
                                dn.toString());
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

      oldTable.clear();
    }

    fair = environmentConfig.getLockManagerFairOrdering();
  }



  /**
   * Attempts to acquire a read lock on the specified entry.  It will
   * succeed only if the write lock is not already held.  If any
   * blocking is required, then this call will fail rather than block.
   *
   * @param  entryDN  The DN of the entry for which to obtain the read
   *                  lock.
   *
   * @return  The read lock that was acquired, or {@code null} if it
   *          was not possible to obtain a read lock for some reason.
   */
  public static Lock tryLockRead(DN entryDN)
  {
    ReentrantReadWriteLock entryLock =
        new ReentrantReadWriteLock(fair);
    Lock readLock = entryLock.readLock();
    readLock.lock();

    ReentrantReadWriteLock existingLock =
         lockTable.putIfAbsent(entryDN, entryLock);
    if (existingLock == null)
    {
      return readLock;
    }
    else
    {
      // There's a lock in the table, but it could potentially be
      // unheld.  We'll do an unsafe check to see whether it might be
      // held and if so then fail to acquire the lock.
      if (existingLock.isWriteLocked())
      {
        readLock.unlock();
        return null;
      }

      // We will never remove a lock from the table without holding
      // its monitor.  Since there's already a lock in the table, then
      // get its monitor and try to acquire the lock.  This should
      // prevent the owner from releasing the lock and removing it
      // from the table before it can be acquired by another thread.
      synchronized (existingLock)
      {
        ReentrantReadWriteLock existingLock2 =
             lockTable.putIfAbsent(entryDN, entryLock);
        if (existingLock2 == null)
        {
          return readLock;
        }
        else if (existingLock == existingLock2)
        {
          // We were able to synchronize on the lock's monitor while
          // the lock was still in the table.  Try to acquire it now
          // (which will succeed if the lock isn't held by anything)
          // and either return it or return null.
          readLock.unlock();
          readLock = existingLock.readLock();

          try
          {
            if (readLock.tryLock(0, TimeUnit.SECONDS))
            {
              return readLock;
            }
            else
            {
              return null;
            }
          }
          catch(InterruptedException ie)
          {
            // This should never happen. Just return null
            return null;
          }
        }
        else
        {
          // If this happens, then it means that while we were waiting
          // the existing lock was unlocked and removed from the table
          // and a new one was created and added to the table.  This
          // is more trouble than it's worth, so return null.
          readLock.unlock();
          return null;
        }
      }
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
   * @return  The read lock that was acquired, or {@code null} if it
   *          was not possible to obtain a read lock for some reason.
   */
  public static Lock lockRead(DN entryDN)
  {
    return lockRead(entryDN, DEFAULT_TIMEOUT);
  }



  /**
   * Attempts to acquire a read lock for the specified entry.
   * Multiple threads can hold the read lock concurrently for an entry
   * as long as the write lock is not held.  If the write lock is
   * held, then no other read or write locks will be allowed for that
   * entry until the write lock is released.
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
    // First, try to get the lock without blocking.
    Lock readLock = tryLockRead(entryDN);
    if (readLock != null)
    {
      return readLock;
    }

    ReentrantReadWriteLock entryLock =
        new ReentrantReadWriteLock(fair);
    readLock = entryLock.readLock();
    readLock.lock();

    ReentrantReadWriteLock existingLock =
         lockTable.putIfAbsent(entryDN, entryLock);
    if (existingLock == null)
    {
      return readLock;
    }

    long surrenderTime = System.currentTimeMillis() + timeout;
    readLock.unlock();
    readLock = existingLock.readLock();

    while (true)
    {
      try
      {
        // See if we can acquire the lock while it's still in the
        // table within the given timeout.
        if (readLock.tryLock(timeout, TimeUnit.MILLISECONDS))
        {
          synchronized (existingLock)
          {
            if (lockTable.get(entryDN) == existingLock)
            {
              // We acquired the lock within the timeout and it's
              // still in the lock table, so we're good to go.
              return readLock;
            }
            else
            {
              ReentrantReadWriteLock existingLock2 =
                   lockTable.putIfAbsent(entryDN, existingLock);
              if (existingLock2 == null)
              {
                // The lock had already been removed from the table,
                // but nothing had replaced it before we put it back,
                // so we're good to go.
                return readLock;
              }
              else
              {
                readLock.unlock();
                existingLock = existingLock2;
                readLock     = existingLock.readLock();
              }
            }
          }
        }
        else
        {
          // We couldn't acquire the lock before the timeout occurred,
          // so we have to fail.
          return null;
        }
      } catch (InterruptedException ie) {}


      // There are only two reasons we should be here:
      // - If the attempt to acquire the lock was interrupted.
      // - If we acquired the lock but it had already been removed
      //   from the table and another one had replaced it before we
      //   could put it back.
      // Our only recourse is to try again, but we need to reduce the
      // timeout to account for the time we've already waited.
      timeout = surrenderTime - System.currentTimeMillis();
      if (timeout <= 0)
      {
        return null;
      }
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
    ReentrantReadWriteLock entryLock =
        new ReentrantReadWriteLock(fair);
    Lock writeLock = entryLock.writeLock();
    writeLock.lock();

    ReentrantReadWriteLock existingLock =
         lockTable.putIfAbsent(entryDN, entryLock);
    if (existingLock == null)
    {
      return writeLock;
    }
    else
    {
      // There's a lock in the table, but it could potentially be
      // unheld.  We'll do an unsafe check to see whether it might be
      // held and if so then fail to acquire the lock.
      if ((existingLock.getReadLockCount() > 0) ||
          (existingLock.isWriteLocked()))
      {
        writeLock.unlock();
        return null;
      }

      // We will never remove a lock from the table without holding
      // its monitor.  Since there's already a lock in the table, then
      // get its monitor and try to acquire the lock.  This should
      // prevent the owner from releasing the lock and removing it
      // from the table before it can be acquired by another thread.
      synchronized (existingLock)
      {
        ReentrantReadWriteLock existingLock2 =
             lockTable.putIfAbsent(entryDN, entryLock);
        if (existingLock2 == null)
        {
          return writeLock;
        }
        else if (existingLock == existingLock2)
        {
          // We were able to synchronize on the lock's monitor while
          // the lock was still in the table.  Try to acquire it now
          // (which will succeed if the lock isn't held by anything)
          // and either return it or return null.
          writeLock.unlock();
          writeLock = existingLock.writeLock();
          try
          {
            if (writeLock.tryLock(0, TimeUnit.SECONDS))
            {
              return writeLock;
            }
            else
            {
              return null;
            }
          }
          catch(InterruptedException ie)
          {
            // This should never happen. Just return null
            return null;
          }
        }
        else
        {
          // If this happens, then it means that while we were waiting
          // the existing lock was unlocked and removed from the table
          // and a new one was created and added to the table.  This
          // is more trouble than it's worth, so return null.
          writeLock.unlock();
          return null;
        }
      }
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
    // First, try to get the lock without blocking.
    Lock writeLock = tryLockWrite(entryDN);
    if (writeLock != null)
    {
      return writeLock;
    }

    ReentrantReadWriteLock entryLock =
        new ReentrantReadWriteLock(fair);
    writeLock = entryLock.writeLock();
    writeLock.lock();

    ReentrantReadWriteLock existingLock =
         lockTable.putIfAbsent(entryDN, entryLock);
    if (existingLock == null)
    {
      return writeLock;
    }

    long surrenderTime = System.currentTimeMillis() + timeout;
    writeLock.unlock();
    writeLock = existingLock.writeLock();

    while (true)
    {
      try
      {
        // See if we can acquire the lock while it's still in the
        // table within the given timeout.
        if (writeLock.tryLock(timeout, TimeUnit.MILLISECONDS))
        {
          synchronized (existingLock)
          {
            if (lockTable.get(entryDN) == existingLock)
            {
              // We acquired the lock within the timeout and it's
              // still in the lock table, so we're good to go.
              return writeLock;
            }
            else
            {
              ReentrantReadWriteLock existingLock2 =
                   lockTable.putIfAbsent(entryDN, existingLock);
              if (existingLock2 == null)
              {
                // The lock had already been removed from the table,
                // but nothing had replaced it before we put it back,
                // so we're good to go.
                return writeLock;
              }
              else
              {
                writeLock.unlock();
                existingLock  = existingLock2;
                writeLock     = existingLock.writeLock();
              }
            }
          }
        }
        else
        {
          // We couldn't acquire the lock before the timeout occurred,
          // so we have to fail.
          return null;
        }
      } catch (InterruptedException ie) {}


      // There are only two reasons we should be here:
      // - If the attempt to acquire the lock was interrupted.
      // - If we acquired the lock but it had already been removed
      //   from the table and another one had replaced it before we
      //   could put it back.
      // Our only recourse is to try again, but we need to reduce the
      // timeout to account for the time we've already waited.
      timeout = surrenderTime - System.currentTimeMillis();
      if (timeout <= 0)
      {
        return null;
      }
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
    // Get the corresponding read-write lock from the lock table.
    ReentrantReadWriteLock existingLock = lockTable.get(entryDN);
    if (existingLock == null)
    {
      // This shouldn't happen, but if it does then all we can do is
      // release the lock and return.
      lock.unlock();
      return;
    }

    // See if there's anything waiting on the lock.  If so, then we
    // can't remove it from the table when we unlock it.
    if (existingLock.hasQueuedThreads() ||
        (existingLock.getReadLockCount() > 1))
    {
      lock.unlock();
      return;
    }
    else
    {
      lock.unlock();
      synchronized (existingLock)
      {
        if ((! existingLock.isWriteLocked()) &&
            (existingLock.getReadLockCount() == 0))
        {
          lockTable.remove(entryDN, existingLock);
        }
      }
      return;
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
   *          {@code null} if nothing was in the table for the
   *          specified entry.  If a lock object is returned, it may
   *          be possible to get information about who was holding it.
   */
  public static ReentrantReadWriteLock destroyLock(DN entryDN)
  {
    return lockTable.remove(entryDN);
  }



  /**
   * Retrieves the number of entries currently held in the lock table.
   * Note that this may be an expensive operation.
   *
   * @return  The number of entries currently held in the lock table.
   */
  public static int lockTableSize()
  {
    return lockTable.size();
  }
}

