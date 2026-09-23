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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jeb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.forgerock.opendj.ldap.ByteString.valueOfUtf8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.server.util.CollectionUtils.newTreeSet;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockTimeoutException;

/**
 * Tests what a {@link JEStorage} takes as it opens and gives back when the open fails - the twin
 * of the same cases on {@code PDBStorageTest} - and the replay of a {@link JEStorage#write} whose
 * transaction JE ends with a {@link LockConflictException}.
 * <p>
 * The conflicts are the engine's own. A deadlock is made by two writers locking two records in opposite order,
 * which JE resolves by throwing at a random victim; a conflict on every attempt is made by a transaction which
 * keeps a record locked while the storage runs with a {@code je.lock.timeout} - the shipped configuration sets
 * none, so a writer waits for a lock rather than times out, but an operator can set one through
 * {@code ds-cfg-je-property}, and JE then reports plain contention as a {@link LockTimeoutException}, which it
 * documents as "abort and retry" just like the deadlock. A {@code DeadlockException} cannot be built by a test:
 * its constructor needs the internal locker it invalidates.
 */
@SuppressWarnings("javadoc")
public class JEStorageTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "JEStorageTest";
  /**
   * A parent directory under which the storage's own directory is a regular file, so that the open
   * fails once its configuration is built - the memory reserved - and before the environment is:
   * what a backend whose directory the server cannot use meets.
   */
  private static final String BLOCKED_DB_DIRECTORY = BACKEND_ID + "-blocked";
  /** A window no run of replays can spend, so that a test of the attempt cap is only ever ended by the cap. */
  private static final long UNREACHABLE_RETRY_WINDOW_NANOS = 300L * 1000L * 1000L * 1000L; //5 min
  /** A window a single attempt outlasts, so that a test of the window reaches it without seconds of build time. */
  private static final long SHORT_RETRY_WINDOW_NANOS = 200L * 1000L * 1000L; //200 ms
  /** A lock wait shorter than any bound, so that a conflict is reported promptly on every attempt. */
  private static final String SHORT_LOCK_TIMEOUT = "20 ms";
  /** A lock wait longer than {@link #SHORT_RETRY_WINDOW_NANOS}, so that one attempt outlasts the window on its own. */
  private static final String LOCK_TIMEOUT_LONGER_THAN_SHORT_WINDOW = "300 ms";
  /** How long a test waits for a thread it started, in seconds; well past any bound the tests configure. */
  private static final long WAIT_SECONDS = 60;

  private final TreeName treeName = new TreeName("dc=test", "test");
  private ServerContext serverContext;
  private JEStorage storage;

  @BeforeClass
  public static void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new JEStorage(createBackendCfg(), serverContext);
    // the environment is removed on the way in as well as on the way out: a build whose JVM died never ran
    // tearDown(), and this class shares a fixed db-directory across methods and across builds
    storage.removeStorageFiles();
    storage.open(AccessMode.READ_WRITE);
    createTreeWithTwoRecords();
  }

  @AfterMethod
  public void tearDown()
  {
    closeAndRemove(storage);
  }

  /**
   * Closes the storage and removes its environment, keeping whichever of the two failed first. Removing it
   * from a finally would let a removal failure replace the close() failure (JLS 14.20.2) - and a close() that
   * throws is exactly the case the removal is here for.
   */
  private static void closeAndRemove(JEStorage storage)
  {
    RuntimeException failure = null;
    try
    {
      storage.close();
    }
    catch (RuntimeException e)
    {
      failure = e;
    }
    try
    {
      storage.removeStorageFiles();
    }
    catch (RuntimeException e)
    {
      if (failure == null)
      {
        failure = e;
      }
      else
      {
        failure.addSuppressed(e);
      }
    }
    if (failure != null)
    {
      throw failure;
    }
  }

  /**
   * An open which fails gives back what it took before it failed: the memory it reserved for the
   * cache, and the listener the constructor registered on the backend configuration. Nothing else
   * will - a root container does not close a storage which did not open - and a backend whose
   * directory the server cannot use is enabled again and again, each attempt draining one cache
   * size.
   */
  @Test
  public void aStorageWhoseOpenFailedGivesBackWhatItTook() throws Exception
  {
    final JEBackendCfg cfg = createBackendCfg();
    final JEStorage second = blockedStorage(cfg);
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open over a directory which is a file");
    }
    catch (ConfigException expected)
    {
      // What a backend directory the server cannot use does.
    }
    finally
    {
      unblock(second);
    }

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
    verify(cfg).removeJEChangeListener(second);
  }

  /**
   * A storage whose open failed has given everything back already, so closing it afterwards takes
   * nothing more - {@code BackendImpl.importLDIF} closes the storage of its root container however
   * the import ended - and does not fail on what the open never got to.
   */
  @Test
  public void closingAStorageWhoseOpenFailedTakesNothingMore() throws Exception
  {
    final JEStorage second = blockedStorage(createBackendCfg());
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open over a directory which is a file");
    }
    catch (ConfigException expected)
    {
      // What a backend directory the server cannot use does.
    }
    finally
    {
      unblock(second);
    }

    second.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /**
   * A storage which is open refuses to open again before it takes anything, and what it holds is
   * left as it is: the refusal is a guard against a programming error, not a failed open with
   * something to give back.
   */
  @Test
  public void openingAnOpenStorageIsRefusedAndTakesNothing() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      storage.open(AccessMode.READ_WRITE);
      fail("a storage which is open was expected to refuse to open again");
    }
    catch (IllegalStateException expected)
    {
      // The guard against a double open.
    }

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
    // Still open: a read reaches the environment.
    assertThat(read("missing")).isNull();
  }

  /** A storage whose directory is a regular file, which no open of it can use. */
  private JEStorage blockedStorage(JEBackendCfg cfg) throws Exception
  {
    when(cfg.getDBDirectory()).thenReturn(BLOCKED_DB_DIRECTORY);
    final JEStorage blocked = new JEStorage(cfg, serverContext);
    final File directory = blocked.getDirectory();
    directory.getParentFile().mkdirs();
    if (!directory.isFile())
    {
      assertThat(directory.createNewFile()).as("the file in the way of %s", directory).isTrue();
    }
    return blocked;
  }

  /** Removes the file in the way of the given storage's directory, and the directory it was made in. */
  private static void unblock(JEStorage blocked)
  {
    final File directory = blocked.getDirectory();
    directory.delete();
    directory.getParentFile().delete();
  }

  /**
   * Replaces the storage under test with one bounded by the given values and, when a lock timeout is given, one
   * whose lock waits end in a {@link LockTimeoutException} after that long, the way an operator's
   * {@code ds-cfg-je-property: je.lock.timeout=...} makes them end. Bounding the loop stops the two bounds racing
   * each other: with the shipped values a run of replays spends a random share of the window on backoff alone,
   * so an attempt cap test can be ended by the ten second window instead, and a window test has to make every
   * attempt outlast seconds of that window to reach it.
   */
  private void reopenWithReplayBounds(int maxRetries, long retryWindowNanos, String lockTimeout) throws Exception
  {
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(lockTimeout), serverContext, maxRetries, retryWindowNanos);
    storage.open(AccessMode.READ_WRITE);
    createTreeWithTwoRecords();
  }

  /**
   * Two writers which lock the two records in opposite order, each holding its first record's write lock before
   * asking for the other's. JE detects the cycle and ends one of the two transactions - chosen at random - with a
   * {@code DeadlockException}; the other is granted its lock once the victim has aborted.
   */
  private final class OpposedWriter implements Runnable
  {
    private final String name;
    private final String first;
    private final String second;
    private final CyclicBarrier bothHoldTheirFirstLock;
    private final boolean swallowTheConflict;
    final AtomicInteger attempts = new AtomicInteger();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final Thread thread;

    OpposedWriter(String name, String first, String second, CyclicBarrier bothHoldTheirFirstLock,
        boolean swallowTheConflict)
    {
      this.name = name;
      this.first = first;
      this.second = second;
      this.bothHoldTheirFirstLock = bothHoldTheirFirstLock;
      this.swallowTheConflict = swallowTheConflict;
      this.thread = new Thread(this, name);
    }

    @Override
    public void run()
    {
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            final int attempt = attempts.incrementAndGet();
            txn.put(treeName, valueOfUtf8(first), valueOfUtf8(name + attempt));
            if (attempt == 1)
            {
              // only the first attempt meets the other writer there: a replay would wait on the barrier forever
              bothHoldTheirFirstLock.await(WAIT_SECONDS, TimeUnit.SECONDS);
            }
            try
            {
              txn.put(treeName, valueOfUtf8(second), valueOfUtf8(name + attempt));
            }
            catch (StorageRuntimeException e)
            {
              if (!swallowTheConflict)
              {
                throw e;
              }
              // an operation which catches what the transaction raised, the way DN2URI.targetEntryReferrals does
            }
          }
        });
      }
      catch (Throwable e)
      {
        failure.set(e);
      }
    }

    void startAndJoin(OpposedWriter other) throws InterruptedException
    {
      thread.start();
      other.thread.start();
      thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
      other.thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
      assertThat(thread.isAlive()).as(name + " finished").isFalse();
      assertThat(other.thread.isAlive()).as(other.name + " finished").isFalse();
    }
  }

  /**
   * Both writers commit, and the victim's transaction was rolled back whole and replayed. How many times is
   * JE's to decide: the abort of the victim hands the survivor the lock it waited for, but on the record version
   * the abort undoes, so the survivor has to lock the version put back, and a replay which wins that race forms
   * the deadlock again with the roles drawn afresh - the backoff makes that rare, not impossible.
   */
  private void assertDeadlockVictimReplayedAndBothCommitted(OpposedWriter ab, OpposedWriter ba) throws Exception
  {
    assertThat(ab.failure.get()).as("ab").isNull();
    assertThat(ba.failure.get()).as("ba").isNull();
    assertThat(ab.attempts.get() + ba.attempts.get()).as("at least one of the two was replayed")
        .isGreaterThanOrEqualTo(3);
    assertThat(Math.max(ab.attempts.get(), ba.attempts.get())).isLessThanOrEqualTo(JEStorage.MAX_RETRIES);
    // whichever committed last wrote both records with the number of the attempt which committed, so the
    // records agree - which they would not, had a victim's first record survived its abort
    final ByteString a = read("a");
    assertThat(a).isEqualTo(read("b"));
    final OpposedWriter last = a.toString().startsWith("ab") ? ab : ba;
    assertThat(a).isEqualTo(valueOfUtf8(last.name + last.attempts.get()));
  }

  @Test
  public void testDeadlockVictimIsReplayed() throws Exception
  {
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final OpposedWriter ab = new OpposedWriter("ab", "a", "b", barrier, false);
    final OpposedWriter ba = new OpposedWriter("ba", "b", "a", barrier, false);

    ab.startAndJoin(ba);

    assertDeadlockVictimReplayedAndBothCommitted(ab, ba);
  }

  /**
   * JE raises a lock conflict from inside the operation, and an operation may catch it there. The transaction is
   * then abort-only, and it is {@code commit()} which raises the conflict again - so the loop has to treat a
   * conflict raised by the commit as one to replay, not only one raised by the operation. Unlike PersistIt, an
   * attempt which swallowed its conflict therefore commits nothing.
   */
  @Test
  public void testConflictSwallowedInsideTheOperationIsRaisedAgainByCommitAndReplayed() throws Exception
  {
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final OpposedWriter ab = new OpposedWriter("ab", "a", "b", barrier, true);
    final OpposedWriter ba = new OpposedWriter("ba", "b", "a", barrier, true);

    ab.startAndJoin(ba);

    assertDeadlockVictimReplayedAndBothCommitted(ab, ba);
  }

  /**
   * A transaction of its own which keeps a record write-locked until released, so that every attempt of a write
   * asking for that record ends the way the storage's lock timeout ends it.
   */
  private final class LockHolder implements Runnable
  {
    private final String key;
    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Thread thread = new Thread(this, "lock holder");

    LockHolder(String key)
    {
      this.key = key;
    }

    @Override
    public void run()
    {
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            txn.put(treeName, valueOfUtf8(key), valueOfUtf8("held"));
            held.countDown();
            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
          }
        });
      }
      catch (Throwable e)
      {
        failure.set(e);
      }
    }

    LockHolder start() throws InterruptedException
    {
      thread.start();
      assertThat(held.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("the holder took the lock").isTrue();
      return this;
    }

    void releaseAndJoin() throws InterruptedException
    {
      release.countDown();
      thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
      assertThat(thread.isAlive()).as("the holder finished").isFalse();
      assertThat(failure.get()).as("the holder's own write").isNull();
    }
  }

  @Test
  public void testWriteGivesUpAfterTheAttemptCap() throws Exception
  {
    // the message is the same at any cap, so this one is spent in two backoffs rather than in the shipped ladder
    final int maxRetries = 3;
    reopenWithReplayBounds(maxRetries, UNREACHABLE_RETRY_WINDOW_NANOS, SHORT_LOCK_TIMEOUT);
    final LockHolder holder = new LockHolder("a").start();
    try
    {
      final AtomicInteger attempts = new AtomicInteger();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            attempts.incrementAndGet();
            txn.put(treeName, valueOfUtf8("a"), valueOfUtf8("abandoned"));
          }
        });
        failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
      }
      catch (StorageRuntimeException e)
      {
        assertThat(e.getMessage()).contains("JEStorageTest").contains(maxRetries + " attempts");
        // and which of the two bounds ran out, since the attempt count alone does not say
        assertThat(e.getMessage()).contains("attempt cap");
        // write() unwraps a StorageRuntimeException that carries a cause, which would replace this message with
        // the bare conflict, and it is the message the config change paths report
        assertThat(e.getCause()).isNull();
        assertThat(e.getSuppressed()).hasSize(1);
        assertThat(e.getSuppressed()[0]).isInstanceOf(LockTimeoutException.class);
      }
      assertThat(attempts.get()).isEqualTo(maxRetries);
    }
    finally
    {
      holder.releaseAndJoin();
    }
    assertThat(read("a")).isEqualTo(valueOfUtf8("held"));
  }

  @Test
  public void testWriteIsReplayedUntilTheConflictClears() throws Exception
  {
    reopenWithReplayBounds(JEStorage.MAX_RETRIES, UNREACHABLE_RETRY_WINDOW_NANOS, SHORT_LOCK_TIMEOUT);
    final LockHolder holder = new LockHolder("a").start();
    try
    {
      final AtomicInteger attempts = new AtomicInteger();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          if (attempts.incrementAndGet() == 4)
          {
            // released, and committed, before the lock is asked for, so that this attempt is the one which
            // gets it rather than the one which times out on the holder's commit
            holder.releaseAndJoin();
          }
          txn.put(treeName, valueOfUtf8("a"), valueOfUtf8("applied"));
        }
      });
      assertThat(attempts.get()).isEqualTo(4);
    }
    finally
    {
      holder.releaseAndJoin();
    }
    assertThat(read("a")).isEqualTo(valueOfUtf8("applied"));
  }

  /**
   * With a lock timeout longer than the window, a single attempt outlasts the whole window. Giving up on the
   * window alone would then replay nothing, in the very case where the replay is likeliest to succeed: the
   * transaction that was blocking this one has just finished.
   */
  @Test
  public void testWriteIsReplayedOnceWhenTheFirstAttemptOutlastsTheWindow() throws Exception
  {
    reopenWithReplayBounds(JEStorage.MAX_RETRIES, SHORT_RETRY_WINDOW_NANOS, LOCK_TIMEOUT_LONGER_THAN_SHORT_WINDOW);
    final LockHolder holder = new LockHolder("a").start();
    try
    {
      final AtomicInteger attempts = new AtomicInteger();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          if (attempts.incrementAndGet() == 2)
          {
            holder.releaseAndJoin();
          }
          txn.put(treeName, valueOfUtf8("a"), valueOfUtf8("outlasted"));
        }
      });
      assertThat(attempts.get()).isEqualTo(2);
    }
    finally
    {
      holder.releaseAndJoin();
    }
    assertThat(read("a")).isEqualTo(valueOfUtf8("outlasted"));
  }

  @Test
  public void testWriteGivesUpOnTheWindowWhenAttemptsAreSlow() throws Exception
  {
    reopenWithReplayBounds(JEStorage.MAX_RETRIES, SHORT_RETRY_WINDOW_NANOS, LOCK_TIMEOUT_LONGER_THAN_SHORT_WINDOW);
    final LockHolder holder = new LockHolder("a").start();
    try
    {
      final AtomicInteger attempts = new AtomicInteger();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            attempts.incrementAndGet();
            // a conflict this slow to report spends the wall clock window long before the attempt cap
            txn.put(treeName, valueOfUtf8("a"), valueOfUtf8("abandoned"));
          }
        });
        failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
      }
      catch (StorageRuntimeException e)
      {
        // the window is what ended it, and it says so: an assertion on the attempt count alone would also pass
        // for a give up on attempt 1, which is the regression the attempt > 1 exemption exists to prevent
        assertThat(e.getMessage()).contains("retry window");
      }
      // one attempt beyond the first: the first spends the window, the exemption grants the replay, and the
      // check after that replay is the one that gives up
      assertThat(attempts.get()).isEqualTo(2);
    }
    finally
    {
      holder.releaseAndJoin();
    }
    assertThat(read("a")).isEqualTo(valueOfUtf8("held"));
  }

  /**
   * An interrupt reaches the loop in its backoff sleep alone, and it must not reach JE afterwards: a thread
   * carrying the interrupt flag invalidates the whole environment on its next call - the transaction registry's
   * latch is acquired interruptibly - which is why the loop leaves the flag as the sleep cleared it, and reports
   * the interrupt with the conflict instead. Delivering the interrupt to the sleep alone takes a write which makes
   * no call of JE at all while the flag is set: this one runs on the storage's import environment, whose writes
   * open no transaction, with an operation which raises a conflict JE raised earlier rather than asking JE for a
   * new one - a transaction aborted with the flag set would take the environment down before the sleep is reached.
   */
  @Test
  public void testInterruptedWriteReportsTheConflictItWasReplaying() throws Exception
  {
    final LockConflictException conflict = aConflictOfJEsOwn();
    // the storage that raised it gave up at its first attempt; this one is bounded as shipped
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(), serverContext);
    storage.startImport();

    final AtomicInteger attempts = new AtomicInteger();
    final boolean interruptedAfterwards;
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          attempts.incrementAndGet();
          Thread.currentThread().interrupt();
          throw new StorageRuntimeException(conflict);
        }
      });
      failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
      return;
    }
    catch (StorageRuntimeException e)
    {
      interruptedAfterwards = Thread.interrupted();
      // the conflict, not the interrupt, is what the caller is told about - but through the same shape the
      // exhausted loop uses, since a bare conflict reaches every caller as its own class name
      assertThat(e.getMessage()).contains("JEStorageTest").contains("interrupted");
      assertThat(e.getSuppressed()).contains(conflict).hasAtLeastOneElementOfType(InterruptedException.class);
      assertThat(e.getCause()).isNull();
    }
    finally
    {
      Thread.interrupted();
    }
    // the flag the sleep cleared stays clear: restored, it would end the environment on the caller's next call
    assertThat(interruptedAfterwards).as("interrupt flag after the write").isFalse();
    // one attempt even though the first backoff is a random 0-49 ms and so is sometimes 0: Thread.sleep() checks
    // the interrupt flag before it checks for a zero duration, so the replay is never reached
    assertThat(attempts.get()).isEqualTo(1);
  }

  /** A conflict raised by JE itself: the lock timeout a write gave up on at its first attempt. */
  private LockConflictException aConflictOfJEsOwn() throws Exception
  {
    reopenWithReplayBounds(1, UNREACHABLE_RETRY_WINDOW_NANOS, SHORT_LOCK_TIMEOUT);
    final LockHolder holder = new LockHolder("a").start();
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          txn.put(treeName, valueOfUtf8("a"), valueOfUtf8("abandoned"));
        }
      });
      throw new AssertionError("the write was applied although its record was held");
    }
    catch (StorageRuntimeException e)
    {
      assertThat(e.getSuppressed()).hasSize(1);
      return (LockConflictException) e.getSuppressed()[0];
    }
    finally
    {
      holder.releaseAndJoin();
    }
  }

  /**
   * The delay grows with the attempt and stays under the cap, so that a contention the first delays did not
   * outlast still has a chance to clear without the replays overrunning the window on sleep alone.
   */
  @Test
  public void testRetryDelayGrowsAndStaysBounded()
  {
    long previousBound = 0;
    for (int attempt = 1; attempt <= JEStorage.MAX_RETRIES; attempt++)
    {
      long bound = 0;
      for (int i = 0; i < 100; i++)
      {
        final long delay = JEStorage.retryDelayMillis(attempt);
        assertThat(delay).as("attempt %d", attempt).isGreaterThanOrEqualTo(0).isLessThan(1000);
        bound = Math.max(bound, delay);
      }
      if (attempt == 1)
      {
        assertThat(bound).as("attempt 1 delays past the first tier").isLessThan(50);
      }
      assertThat(bound).as("attempt %d did not grow past attempt %d", attempt, attempt - 1)
          .isGreaterThanOrEqualTo(previousBound / 2);
      previousBound = bound;
    }
    // and the growth is real rather than a delay that never leaves the first tier
    long grown = 0;
    for (int i = 0; i < 100; i++)
    {
      grown = Math.max(grown, JEStorage.retryDelayMillis(JEStorage.MAX_RETRIES));
    }
    assertThat(grown).as("the last attempts still sleep within the first attempt's bound").isGreaterThan(500);
  }

  private void createTreeWithTwoRecords() throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(treeName, true);
        txn.put(treeName, valueOfUtf8("a"), valueOfUtf8("0"));
        txn.put(treeName, valueOfUtf8("b"), valueOfUtf8("0"));
      }
    });
  }

  private ByteString read(final String key) throws Exception
  {
    return storage.read(new ReadOperation<ByteString>()
    {
      @Override
      public ByteString run(ReadableTransaction txn) throws Exception
      {
        return txn.read(treeName, valueOfUtf8(key));
      }
    });
  }

  private static JEBackendCfg createBackendCfg()
  {
    final JEBackendCfg backendCfg = mockCfg(JEBackendCfg.class);
    when(backendCfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(backendCfg.getBackendId()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBDirectory()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getDBNumCleanerThreads()).thenReturn(2);
    when(backendCfg.getDBNumLockTables()).thenReturn(63);
    return backendCfg;
  }

  /**
   * The configuration of the storage under test, with the lock timeout an operator would set through
   * {@code ds-cfg-je-property}, or the shipped one - none - when null.
   */
  private static JEBackendCfg createBackendCfg(String lockTimeout)
  {
    final JEBackendCfg backendCfg = createBackendCfg();
    if (lockTimeout != null)
    {
      when(backendCfg.getJEProperty()).thenReturn(newTreeSet("je.lock.timeout=" + lockTimeout));
    }
    return backendCfg;
  }
}
