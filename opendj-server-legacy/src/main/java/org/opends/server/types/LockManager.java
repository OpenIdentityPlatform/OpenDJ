/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.Reject;

/**
 * A lock manager coordinates directory update operations so that the DIT structure remains in a
 * consistent state, as well as providing repeatable read isolation. When accessing entries
 * components need to ensure that they have the appropriate lock:
 * <ul>
 * <li>repeatable reads: repeatable read isolation is rarely needed in practice, since all backend
 * reads are guaranteed to be performed with read-committed isolation, which is normally sufficient.
 * Specifically, read-only operations such as compare and search do not require any additional
 * locking. If repeatable read isolation is required then lock the entry using
 * {@link #tryReadLockEntry(DN)}
 * <li>modifying an entry: acquire an entry write-lock for the target entry using
 * {@link #tryWriteLockEntry(DN)}. Updates are typically performed using a read-modify-write cycle,
 * so the write lock should be acquired before performing the initial read in order to ensure
 * consistency
 * <li>adding an entry: client code must acquire an entry write-lock for the target entry using
 * {@link #tryWriteLockEntry(DN)}. The parent entry will automatically be protected from deletion by
 * an implicit subtree read lock on the parent
 * <li>deleting an entry: client code must acquire a subtree write lock for the target entry using
 * {@link #tryWriteLockSubtree(DN)}
 * <li>renaming an entry: client code must acquire a subtree write lock for the old entry, and a
 * subtree write lock for the new entry using {@link #tryWriteLockSubtree(DN)}. Care should be taken
 * to avoid deadlocks, e.g. by locking the DN which sorts first.
 * </ul>
 * In addition, backend implementations may choose to use their own lock manager for enforcing
 * atomicity and isolation. This is typically the case for backends which cannot take advantage of
 * atomicity guarantees provided by an underlying DB (the task backend is one such example).
 * <p>
 * <b>Implementation Notes</b>
 * <p>
 * The lock table is conceptually a cache of locks keyed on DN, i.e. a {@code Map<DN, DNLock>}.
 * Locks must be kept in the cache while they are locked, but may be removed once they are no longer
 * locked by any threads. Locks are represented using a pair of read-write locks: the first lock is
 * the "subtree" lock and the second is the "entry" lock.
 * <p>
 * In order to lock an entry for read or write a <b>subtree</b> read lock is first acquired on each
 * of the parent entries from the root DN down to the immediate parent of the entry to be locked.
 * Then the appropriate read or write <b>entry</b> lock is acquired for the target entry. Subtree
 * write locking is performed by acquiring a <b>subtree</b> read lock on each of the parent entries
 * from the root DN down to the immediate parent of the subtree to be locked. Then a <b>subtree</b>
 * write lock is acquired for the target subtree.
 * <p>
 * The lock table itself is not represented using a {@code ConcurrentHashMap} because the JDK6/7
 * APIs do not provide the ability to atomically add-and-lock or unlock-and-remove locks (this
 * capability is provided in JDK8). Instead, we provide our own implementation comprising of a fixed
 * number of buckets, a bucket being a {@code LinkedList} of {@code DNLock}s. In addition, it is
 * important to be able to efficiently iterate up and down a chain of hierarchically related locks,
 * so each lock maintains a reference to its parent lock. Modern directories tend to have a flat
 * structure so it is also important to avoid contention on "hot" parent DNs. Typically, a lock
 * attempt against a DN will involve a cache miss for the target DN and a cache hit for the parent,
 * but the parent will be the same parent for all lock requests, resulting in a lot of contention on
 * the same lock bucket. To avoid this the lock manager maintains a small-thread local cache of
 * locks, so that parent locks can be acquired using a lock-free algorithm.
 * <p>
 * Since the thread local cache may reference locks which are not actively locked by anyone, a
 * reference counting mechanism is used in order to prevent cached locks from being removed from the
 * underlying lock table. The reference counting mechanism is also used for references between a
 * lock and its parent lock. To summarize, locking a DN involves the following steps:
 * <ul>
 * <li>get the lock from the thread local cache. If the lock was not in the thread local cache then
 * try fetching it from the lock table:
 * <ul>
 * <li><i>found</i> - store it in the thread local cache and bump the reference count
 * <li><i>not found</i> - create a new lock. First fetch the parent lock using the same process,
 * i.e. looking in the thread local cache, etc. Then create a new lock referencing the parent lock
 * (bumps the reference count for the parent lock), and store it in the lock table and the thread
 * local cache with a reference count of 1.
 * </ul>
 * <li>return the lock to the application and increment its reference count since the application
 * now also has a reference to the lock.
 * </ul>
 * Locks are dereferenced when they are unlocked, when they are evicted from a thread local cache,
 * and when a child lock's reference count reaches zero. A lock is completely removed from the lock
 * table once its reference count reaches zero.
 */
@org.opends.server.types.PublicAPI(stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = false, mayExtend = false, mayInvoke = true)
public final class LockManager
{
  /** A lock on an entry or subtree. A lock can only be unlocked once. */
  public final class DNLock
  {
    private final DNLockHolder lock;
    private final Lock subtreeLock;
    private final Lock entryLock;
    private boolean isLocked = true;

    private DNLock(final DNLockHolder lock, final Lock subtreeLock, final Lock entryLock)
    {
      this.lock = lock;
      this.subtreeLock = subtreeLock;
      this.entryLock = entryLock;
    }

    @Override
    public String toString()
    {
      return lock.toString();
    }

    /**
     * Unlocks this lock and releases any blocked threads.
     *
     * @throws IllegalStateException
     *           If this lock has already been unlocked.
     */
    public void unlock()
    {
      if (!isLocked)
      {
        throw new IllegalStateException("Already unlocked");
      }
      lock.releaseParentSubtreeReadLock();
      subtreeLock.unlock();
      entryLock.unlock();
      dereference(lock);
      isLocked = false;
    }

    /** For unit testing. */
    int refCount()
    {
      return lock.refCount.get();
    }
  }

  /** Lock implementation. */
  private final class DNLockHolder
  {
    private final AtomicInteger refCount = new AtomicInteger();
    private final DNLockHolder parent;
    private final DN dn;
    private final int dnHashCode;
    private final ReentrantReadWriteLock subtreeLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock entryLock = new ReentrantReadWriteLock();

    DNLockHolder(final DNLockHolder parent, final DN dn, final int dnHashCode)
    {
      this.parent = parent;
      this.dn = dn;
      this.dnHashCode = dnHashCode;
    }

    @Override
    public String toString()
    {
      return "\"" + dn + "\" : " + refCount;
    }

    /** Unlocks the subtree read lock from the parent of this lock up to the root. */
    void releaseParentSubtreeReadLock()
    {
      for (DNLockHolder lock = parent; lock != null; lock = lock.parent)
      {
        lock.subtreeLock.readLock().unlock();
      }
    }

    DNLock tryReadLockEntry()
    {
      return tryLock(subtreeLock.readLock(), entryLock.readLock());
    }

    DNLock tryWriteLockEntry()
    {
      return tryLock(subtreeLock.readLock(), entryLock.writeLock());
    }

    DNLock tryWriteLockSubtree()
    {
      return tryLock(subtreeLock.writeLock(), entryLock.writeLock());
    }

    /** Locks the subtree read lock from the root down to the parent of this lock. */
    private boolean tryAcquireParentSubtreeReadLock()
    {
      // First lock the parents of the parent.
      if (parent == null)
      {
        return true;
      }

      if (!parent.tryAcquireParentSubtreeReadLock())
      {
        return false;
      }

      // Then lock the parent of this lock
      if (tryLockWithTimeout(parent.subtreeLock.readLock()))
      {
        return true;
      }

      // Failed to grab the parent lock within the timeout, so roll-back the other locks.
      releaseParentSubtreeReadLock();
      return false;
    }

    private DNLock tryLock(final Lock subtreeLock, final Lock entryLock)
    {
      if (tryAcquireParentSubtreeReadLock())
      {
        if (tryLockWithTimeout(subtreeLock))
        {
          if (tryLockWithTimeout(entryLock))
          {
            return new DNLock(this, subtreeLock, entryLock);
          }
          subtreeLock.unlock();
        }
        releaseParentSubtreeReadLock();
      }
      // Failed to acquire all the necessary locks within the time out.
      dereference(this);
      return null;
    }

    private boolean tryLockWithTimeout(final Lock lock)
    {
      try
      {
        return lock.tryLock(lockTimeout, lockTimeoutUnits);
      }
      catch (final InterruptedException e)
      {
        // Unable to handle interrupts here.
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  private static final long DEFAULT_LOCK_TIMEOUT = 9;
  private static final TimeUnit DEFAULT_LOCK_TIMEOUT_UNITS = TimeUnit.SECONDS;
  private static final int MINIMUM_NUMBER_OF_BUCKETS = 64;
  private static final int THREAD_LOCAL_CACHE_SIZE = 8;

  private final int numberOfBuckets;
  private final LinkedList<DNLockHolder>[] lockTable;
  private final long lockTimeout;
  private final TimeUnit lockTimeoutUnits;

  /** Avoid sub-classing in order to workaround class leaks in app servers. */
  private final ThreadLocal<LinkedList<DNLockHolder>> threadLocalCache = new ThreadLocal<>();

  /**
   * Creates a new lock manager with a lock timeout of 9 seconds and an automatically chosen number
   * of lock table buckets based on the number of processors.
   */
  public LockManager()
  {
    this(DEFAULT_LOCK_TIMEOUT, DEFAULT_LOCK_TIMEOUT_UNITS);
  }

  /**
   * Creates a new lock manager with the specified lock timeout and an automatically chosen number
   * of lock table buckets based on the number of processors.
   *
   * @param lockTimeout
   *          The lock timeout.
   * @param lockTimeoutUnit
   *          The lock timeout units.
   */
  public LockManager(final long lockTimeout, final TimeUnit lockTimeoutUnit)
  {
    this(lockTimeout, lockTimeoutUnit, Runtime.getRuntime().availableProcessors() * 8);
  }

  /**
   * Creates a new lock manager with the provided configuration.
   *
   * @param lockTimeout
   *          The lock timeout.
   * @param lockTimeoutUnit
   *          The lock timeout units.
   * @param numberOfBuckets
   *          The number of buckets to use in the lock table. The minimum number of buckets is 64.
   */
  @SuppressWarnings("unchecked")
  private LockManager(final long lockTimeout, final TimeUnit lockTimeoutUnit, final int numberOfBuckets)
  {
    Reject.ifFalse(lockTimeout >= 0, "lockTimeout must be a non-negative integer");
    Reject.ifNull(lockTimeoutUnit, "lockTimeoutUnit must be non-null");
    Reject.ifFalse(numberOfBuckets > 0, "numberOfBuckets must be a positive integer");

    this.lockTimeout = lockTimeout;
    this.lockTimeoutUnits = lockTimeoutUnit;
    this.numberOfBuckets = getNumberOfBuckets(numberOfBuckets);
    this.lockTable = new LinkedList[this.numberOfBuckets];
    for (int i = 0; i < this.numberOfBuckets; i++)
    {
      this.lockTable[i] = new LinkedList<>();
    }
  }

  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < numberOfBuckets; i++)
    {
      final LinkedList<DNLockHolder> bucket = lockTable[i];
      synchronized (bucket)
      {
        for (final DNLockHolder lock : bucket)
        {
          builder.append(lock);
          builder.append('\n');
        }
      }
    }
    return builder.toString();
  }

  /**
   * Acquires the read lock for the specified entry. This method will block if the entry is already
   * write locked or if the entry, or any of its parents, have the subtree write lock taken.
   *
   * @param entry
   *          The entry whose read lock is required.
   * @return The lock, or {@code null} if the lock attempt timed out.
   */
  public DNLock tryReadLockEntry(final DN entry)
  {
    return acquireLockFromCache(entry).tryReadLockEntry();
  }

  /**
   * Acquires the write lock for the specified entry. This method will block if the entry is already
   * read or write locked or if the entry, or any of its parents, have the subtree write lock taken.
   *
   * @param entry
   *          The entry whose write lock is required.
   * @return The lock, or {@code null} if the lock attempt timed out.
   */
  public DNLock tryWriteLockEntry(final DN entry)
  {
    return acquireLockFromCache(entry).tryWriteLockEntry();
  }

  /**
   * Acquires the write lock for the specified subtree. This method will block if any entry or
   * subtree within the subtree is already read or write locked or if any of the parent entries of
   * the subtree have the subtree write lock taken.
   *
   * @param subtree
   *          The subtree whose write lock is required.
   * @return The lock, or {@code null} if the lock attempt timed out.
   */
  public DNLock tryWriteLockSubtree(final DN subtree)
  {
    return acquireLockFromCache(subtree).tryWriteLockSubtree();
  }

  /** For unit testing. */
  int getLockTableRefCountFor(final DN dn)
  {
    final int dnHashCode = dn.hashCode();
    final LinkedList<DNLockHolder> bucket = getBucket(dnHashCode);
    synchronized (bucket)
    {
      for (final DNLockHolder lock : bucket)
      {
        if (lock.dnHashCode == dnHashCode && lock.dn.equals(dn))
        {
          return lock.refCount.get();
        }
      }
      return -1;
    }
  }

  /** For unit testing. */
  int getThreadLocalCacheRefCountFor(final DN dn)
  {
    final LinkedList<DNLockHolder> cache = threadLocalCache.get();
    if (cache == null)
    {
      return -1;
    }
    final int dnHashCode = dn.hashCode();
    for (final DNLockHolder lock : cache)
    {
      if (lock.dnHashCode == dnHashCode && lock.dn.equals(dn))
      {
        return lock.refCount.get();
      }
    }
    return -1;
  }

  private DNLockHolder acquireLockFromCache(final DN dn)
  {
    LinkedList<DNLockHolder> cache = threadLocalCache.get();
    if (cache == null)
    {
      cache = new LinkedList<>();
      threadLocalCache.set(cache);
    }
    return acquireLockFromCache0(dn, cache);
  }

  private DNLockHolder acquireLockFromCache0(final DN dn, final LinkedList<DNLockHolder> cache)
  {
    final int dnHashCode = dn.hashCode();
    DNLockHolder lock = removeLock(cache, dn, dnHashCode);
    if (lock == null)
    {
      lock = acquireLockFromLockTable(dn, dnHashCode, cache);
      if (cache.size() >= THREAD_LOCAL_CACHE_SIZE)
      {
        // Cache too big: evict oldest entry.
        dereference(cache.removeLast());
      }
    }
    cache.addFirst(lock); // optimize for LRU
    lock.refCount.incrementAndGet();
    return lock;
  }

  private DNLockHolder acquireLockFromLockTable(final DN dn, final int dnHashCode, final LinkedList<DNLockHolder> cache)
  {
    /*
     * The lock doesn't exist yet so we'll have to create a new one referencing its parent lock. The
     * parent lock may not yet exist in the lock table either so acquire it before locking the
     * bucket in order to avoid deadlocks resulting from reentrant bucket locks. Note that we
     * pre-emptively fetch the parent lock because experiments show that the requested child lock is
     * almost never in the lock-table. Specifically, this method is only called if we are already on
     * the slow path due to a cache miss in the thread-local cache.
     */
    final DN parentDN = dn.parent();
    final DNLockHolder parentLock = parentDN != null ? acquireLockFromCache0(parentDN, cache) : null;
    boolean parentLockWasUsed = false;
    try
    {
      final LinkedList<DNLockHolder> bucket = getBucket(dnHashCode);
      synchronized (bucket)
      {
        DNLockHolder lock = removeLock(bucket, dn, dnHashCode);
        if (lock == null)
        {
          lock = new DNLockHolder(parentLock, dn, dnHashCode);
          parentLockWasUsed = true;
        }
        bucket.addFirst(lock); // optimize for LRU
        lock.refCount.incrementAndGet();
        return lock;
      }
    }
    finally
    {
      if (!parentLockWasUsed && parentLock != null)
      {
        dereference(parentLock);
      }
    }
  }

  private void dereference(final DNLockHolder lock)
  {
    if (lock.refCount.decrementAndGet() <= 0)
    {
      final LinkedList<DNLockHolder> bucket = getBucket(lock.dnHashCode);
      boolean lockWasRemoved = false;
      synchronized (bucket)
      {
        // Double check: another thread could have acquired the lock since we decremented it to zero.
        if (lock.refCount.get() <= 0)
        {
          removeLock(bucket, lock.dn, lock.dnHashCode);
          lockWasRemoved = true;
        }
      }

      /*
       * Dereference the parent outside of the bucket lock to avoid potential deadlocks due to
       * reentrant bucket locks.
       */
      if (lockWasRemoved && lock.parent != null)
      {
        dereference(lock.parent);
      }
    }
  }

  private LinkedList<DNLockHolder> getBucket(final int dnHashCode)
  {
    return lockTable[dnHashCode & numberOfBuckets - 1];
  }

  /**
   * Ensure that the number of buckets is a power of 2 in order to make it easier to map hash codes
   * to bucket indexes.
   */
  private int getNumberOfBuckets(final int buckets)
  {
    final int roundedNumberOfBuckets = Math.min(buckets, MINIMUM_NUMBER_OF_BUCKETS);
    int powerOf2 = 1;
    while (powerOf2 < roundedNumberOfBuckets)
    {
      powerOf2 <<= 1;
    }
    return powerOf2;
  }

  private DNLockHolder removeLock(final LinkedList<DNLockHolder> lockList, final DN dn, final int dnHashCode)
  {
    final Iterator<DNLockHolder> iterator = lockList.iterator();
    while (iterator.hasNext())
    {
      final DNLockHolder lock = iterator.next();
      if (lock.dnHashCode == dnHashCode && lock.dn.equals(dn))
      {
        // Found: remove the lock because it will be moved to the front of the list.
        iterator.remove();
        return lock;
      }
    }
    return null;
  }
}
