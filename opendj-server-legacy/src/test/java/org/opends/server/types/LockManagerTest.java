/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.LockManager.DNLock;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test(timeOut = 20000, sequential = true)
@SuppressWarnings("javadoc")
public class LockManagerTest extends TypesTestCase
{
  private enum LockType
  {
    READ_ENTRY
    {
      @Override
      DNLock lock(final LockManager lockManager, final DN dn)
      {
        return lockManager.tryReadLockEntry(dn);
      }
    },
    WRITE_ENTRY
    {
      @Override
      DNLock lock(final LockManager lockManager, final DN dn)
      {
        return lockManager.tryWriteLockEntry(dn);
      }
    },
    WRITE_SUBTREE
    {
      @Override
      DNLock lock(final LockManager lockManager, final DN dn)
      {
        return lockManager.tryWriteLockSubtree(dn);
      }
    };

    abstract DNLock lock(LockManager lockManager, DN dn);
  }

  private DN dnA;
  private DN dnAB;
  private DN dnABC;
  private DN dnABD;
  private final ExecutorService thread1 = Executors.newSingleThreadExecutor();
  private final ExecutorService thread2 = Executors.newSingleThreadExecutor();

  @BeforeClass
  private void setup() throws Exception
  {
    TestCaseUtils.startServer();
    dnA = DN.valueOf("dc=a");
    dnAB = DN.valueOf("dc=b,dc=a");
    dnABC = DN.valueOf("dc=c,dc=b,dc=a");
    dnABD = DN.valueOf("dc=d,dc=b,dc=a");
  }

  @Test
  public void testLockTimeout() throws Exception
  {
    final LockManager lockManager = new LockManager(100, TimeUnit.MILLISECONDS);
    DNLock lock1 = lockUsingThread(thread1, lockManager, LockType.WRITE_ENTRY, dnABC).get();
    DNLock lock2 = lockUsingThread(thread2, lockManager, LockType.WRITE_ENTRY, dnABC).get();
    assertThat(lock1).isNotNull();
    assertThat(lock2).isNull(); // Timed out.
    unlockUsingThread(thread1, lock1);
  }

  @DataProvider
  private Object[][] multiThreadedLockCombinationsWhichShouldBlock()
  {
    // @formatter:off
    return new Object[][] {
      { LockType.READ_ENTRY,    dnA,    LockType.WRITE_ENTRY,   dnA },
      { LockType.WRITE_ENTRY,   dnA,    LockType.READ_ENTRY,    dnA },
      { LockType.WRITE_ENTRY,   dnA,    LockType.WRITE_ENTRY,   dnA },

      { LockType.WRITE_SUBTREE, dnA,    LockType.READ_ENTRY,    dnA },
      { LockType.READ_ENTRY,    dnA,    LockType.WRITE_SUBTREE, dnA },
      { LockType.WRITE_SUBTREE, dnA,    LockType.WRITE_ENTRY,   dnA },
      { LockType.WRITE_ENTRY,   dnA,    LockType.WRITE_SUBTREE, dnA },
      { LockType.WRITE_SUBTREE, dnA,    LockType.WRITE_SUBTREE, dnA },

      { LockType.WRITE_SUBTREE, dnA,    LockType.READ_ENTRY,    dnAB },
      { LockType.READ_ENTRY,    dnAB,   LockType.WRITE_SUBTREE, dnA },
      { LockType.WRITE_SUBTREE, dnA,    LockType.WRITE_ENTRY,   dnAB },
      { LockType.WRITE_ENTRY,   dnAB,   LockType.WRITE_SUBTREE, dnA },
      { LockType.WRITE_SUBTREE, dnA,    LockType.WRITE_SUBTREE, dnAB },
      { LockType.WRITE_SUBTREE, dnAB,   LockType.WRITE_SUBTREE, dnA },
    };
    // @formatter:on
  }

  @Test(dataProvider = "multiThreadedLockCombinationsWhichShouldBlock")
  public void testMultiThreadedLockCombinationsWhichShouldBlock(final LockType lock1Type, final DN dn1,
      final LockType lock2Type, final DN dn2) throws Exception
  {
    final LockManager lockManager = new LockManager();
    final DNLock lock1 = lockUsingThread(thread1, lockManager, lock1Type, dn1).get();
    final Future<DNLock> lock2Future = lockUsingThread(thread2, lockManager, lock2Type, dn2);

    try
    {
      lock2Future.get(10, TimeUnit.MILLISECONDS);
    }
    catch (final TimeoutException e)
    {
      // Ignore: we'll check the state of the future instead.
    }
    assertThat(lock2Future.isDone()).isFalse();
    unlockUsingThread(thread1, lock1);
    final DNLock lock2 = lock2Future.get();
    unlockUsingThread(thread2, lock2);

    assertThat(getThreadLocalLockRefCountFor(thread1, lockManager, dn1)).isGreaterThan(0);
    assertThat(getThreadLocalLockRefCountFor(thread2, lockManager, dn2)).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn1)).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn2)).isGreaterThan(0);
  }

  @DataProvider
  private Object[][] multiThreadedLockCombinationsWhichShouldNotBlock()
  {
    // @formatter:off
    return new Object[][] {
      { LockType.READ_ENTRY,    dnA,    LockType.READ_ENTRY,    dnA },

      { LockType.READ_ENTRY,    dnA,    LockType.WRITE_ENTRY,   dnAB },
      { LockType.WRITE_ENTRY,   dnAB,   LockType.READ_ENTRY,    dnA },
      { LockType.WRITE_ENTRY,   dnA,    LockType.WRITE_ENTRY,   dnAB },
      { LockType.WRITE_ENTRY,   dnAB,   LockType.WRITE_ENTRY,   dnA },

      { LockType.READ_ENTRY,    dnA,    LockType.WRITE_SUBTREE, dnAB },
      { LockType.WRITE_SUBTREE, dnAB,   LockType.READ_ENTRY,    dnA },

      { LockType.WRITE_ENTRY,   dnA,    LockType.WRITE_SUBTREE, dnAB },
      { LockType.WRITE_SUBTREE, dnAB,   LockType.WRITE_ENTRY,   dnA },

      { LockType.WRITE_SUBTREE, dnABC,  LockType.WRITE_SUBTREE, dnABD },
    };
    // @formatter:on
  }

  @Test(dataProvider = "multiThreadedLockCombinationsWhichShouldNotBlock")
  public void testMultiThreadedLockCombinationsWhichShouldNotBlock(final LockType lock1Type, final DN dn1,
      final LockType lock2Type, final DN dn2) throws Exception
  {
    final LockManager lockManager = new LockManager();
    final DNLock lock1 = lockUsingThread(thread1, lockManager, lock1Type, dn1).get();
    final DNLock lock2 = lockUsingThread(thread2, lockManager, lock2Type, dn2).get();

    assertThat(lock1).isNotSameAs(lock2);
    unlockUsingThread(thread1, lock1);
    unlockUsingThread(thread2, lock2);

    assertThat(getThreadLocalLockRefCountFor(thread1, lockManager, dn1)).isGreaterThan(0);
    assertThat(getThreadLocalLockRefCountFor(thread2, lockManager, dn2)).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn1)).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn2)).isGreaterThan(0);
  }

  @DataProvider
  private LockType[][] reentrantLockCombinationsWhichShouldNotBlock()
  {
    // @formatter:off
    return new LockType[][] {
      { LockType.READ_ENTRY,    LockType.READ_ENTRY },

      { LockType.WRITE_ENTRY,   LockType.READ_ENTRY },
      { LockType.WRITE_ENTRY,   LockType.WRITE_ENTRY },

      { LockType.WRITE_SUBTREE, LockType.READ_ENTRY },
      { LockType.WRITE_SUBTREE, LockType.WRITE_ENTRY },
      { LockType.WRITE_SUBTREE, LockType.WRITE_SUBTREE },
    };
    // @formatter:on
  }

  @Test(dataProvider = "reentrantLockCombinationsWhichShouldNotBlock")
  public void testReentrantLockCombinationsWhichShouldNotBlock(final LockType lock1Type, final LockType lock2Type)
  {
    final LockManager lockManager = new LockManager();
    final DNLock lock1 = lock1Type.lock(lockManager, dnA);
    final DNLock lock2 = lock2Type.lock(lockManager, dnA);

    assertThat(lock1).isNotSameAs(lock2);
    assertThat(lock1.refCount()).isEqualTo(3); // +1 for thread local cache
    assertThat(lock2.refCount()).isEqualTo(3);

    lock1.unlock();
    assertThat(lock1.refCount()).isEqualTo(2);
    assertThat(lock2.refCount()).isEqualTo(2);

    lock2.unlock();
    assertThat(lock1.refCount()).isEqualTo(1);
    assertThat(lock2.refCount()).isEqualTo(1);

    assertThat(lockManager.getThreadLocalCacheRefCountFor(dnA)).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dnA)).isGreaterThan(0);
  }

  @Test
  public void testThreadLocalCacheEviction() throws Exception
  {
    final LockManager lockManager = new LockManager();

    // Acquire 100 different locks. The first few locks should be evicted from the cache.
    final LinkedList<DNLock> locks = new LinkedList<DNLock>();
    for (int i = 0; i < 100; i++)
    {
      locks.add(lockManager.tryWriteLockEntry(dn(i)));
    }

    // The first lock should have been evicted from the cache, but still in the lock table because it is locked.
    assertThat(locks.getFirst().refCount()).isEqualTo(1);
    assertThat(lockManager.getThreadLocalCacheRefCountFor(dn(0))).isLessThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn(0))).isGreaterThan(0);

    // The last lock should still be in the cache and the lock table.
    assertThat(locks.getLast().refCount()).isEqualTo(2);
    assertThat(lockManager.getThreadLocalCacheRefCountFor(dn(99))).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn(99))).isGreaterThan(0);

    for (final DNLock lock : locks)
    {
      lock.unlock();
    }

    // The first lock should not be in the cache or the lock table.
    assertThat(locks.getFirst().refCount()).isEqualTo(0);
    assertThat(lockManager.getThreadLocalCacheRefCountFor(dn(0))).isLessThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn(0))).isLessThan(0);

    // The last lock should still be in the cache and the lock table.
    assertThat(locks.getLast().refCount()).isEqualTo(1);
    assertThat(lockManager.getThreadLocalCacheRefCountFor(dn(99))).isGreaterThan(0);
    assertThat(lockManager.getLockTableRefCountFor(dn(99))).isGreaterThan(0);
  }

  @Test(description = "OPENDJ-1984")
  public void stressTestForDeadlocks() throws Exception
  {
    final LockManager lockManager = new LockManager();
    final int threadCount = Runtime.getRuntime().availableProcessors();
    final ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++)
    {
      threadPool.submit(new Runnable()
      {
        @Override
        public void run()
        {
          final Random rng = new Random();
          for (int j = 0; j < 1000000; j++)
          {
            try
            {
              /*
               * Lock a DN whose parent is different each time in order to prevent the thread local
               * cache being used for retrieving the parent DN lock.
               */
              final int uid = rng.nextInt();
              final int deviceId = rng.nextInt();
              final DN dn = DN.valueOf("uid=" + deviceId + ",uid=" + uid + ",dc=example,dc=com");
              lockManager.tryWriteLockEntry(dn).unlock();
            }
            catch (DirectoryException e)
            {
              throw new RuntimeException(e);
            }
          }
        }
      });
    }

    threadPool.shutdown();
    assertThat(threadPool.awaitTermination(60, TimeUnit.SECONDS)).as("Deadlock detected during stress test").isTrue();
  }

  private DN dn(final int i) throws DirectoryException
  {
    return DN.valueOf(String.format("uid=user.%d,ou=people,dc=example,dc=com", i));
  }

  private Future<DNLock> lockUsingThread(final ExecutorService thread, final LockManager lockManager,
      final LockType lockType, final DN dn) throws Exception
  {
    return thread.submit(new Callable<DNLock>()
    {
      @Override
      public DNLock call() throws Exception
      {
        return lockType.lock(lockManager, dn);
      }
    });
  }

  private int getThreadLocalLockRefCountFor(final ExecutorService thread, final LockManager lockManager,
      final DN dn) throws Exception
  {
    return thread.submit(new Callable<Integer>()
    {
      @Override
      public Integer call() throws Exception
      {
        return lockManager.getThreadLocalCacheRefCountFor(dn);
      }
    }).get();
  }

  private void unlockUsingThread(final ExecutorService thread, final DNLock lock) throws Exception
  {
    thread.submit(new Runnable()
    {
      @Override
      public void run()
      {
        lock.unlock();
      }
    }).get();
  }
}
