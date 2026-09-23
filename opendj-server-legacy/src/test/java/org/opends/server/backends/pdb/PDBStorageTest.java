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
 * Copyright 2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pdb;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.forgerock.opendj.config.ConfigurationMock.*;
import static org.opends.server.util.StaticUtils.*;
import static org.forgerock.opendj.ldap.ByteString.*;
import static org.opends.messages.BackendMessages.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageInUseException;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.persistit.Exchange;
import com.persistit.exception.RollbackException;

public class PDBStorageTest extends DirectoryServerTestCase
{
  /** A window no run of replays can spend, so that a test of the attempt cap is only ever ended by the cap. */
  private static final long UNREACHABLE_RETRY_WINDOW_NANOS = 300L * 1000L * 1000L * 1000L; //5 min
  /** A window a single attempt outlasts, so that a test of the window reaches it without seconds of build time. */
  private static final long SHORT_RETRY_WINDOW_NANOS = 200L * 1000L * 1000L; //200 ms
  /** An attempt long enough to outlast {@link #SHORT_RETRY_WINDOW_NANOS} on its own, in milliseconds. */
  private static final long ATTEMPT_LONGER_THAN_SHORT_WINDOW_MS = 300;

  private final TreeName treeName = new TreeName("dc=test", "test");
  private ServerContext serverContext;
  private PDBStorage storage;

  /** A cache size the quota of the test JVM grants several times over, in bytes. */
  private static final long SMALL_CACHE = 64L * MB;

  @BeforeClass
  public static void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws ConfigException
  {
    serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PDBStorage(createBackendCfg(), serverContext);
    // the volume is removed on the way in as well as on the way out: a build whose JVM died never ran tearDown(),
    // and this class shares a fixed db-directory across methods and across builds, so what that run left behind
    // would still be here to answer this method's reads
    storage.removeStorageFiles();
    storage.open(AccessMode.READ_WRITE);
  }

  @AfterMethod
  public void tearDown()
  {
    closeAndRemove(storage);
  }

  /**
   * Closes the storage and removes its volume, keeping whichever of the two failed first. Removing it from a
   * finally would let a removal failure replace the close() failure (JLS 14.20.2) - and a close() that throws is
   * exactly the case the removal is here for.
   */
  private static void closeAndRemove(PDBStorage storage)
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
   * Replaces the storage under test with one bounded by the given values, so that the bound a test is about is
   * the one that ends its replays. With the shipped values the two race: the nine backoffs of a full ladder draw
   * from 50+100+200+400+800+1000x4, so an attempt cap test can be ended by the ten second window instead, and a
   * window test has to make every attempt outlast seconds of that window to reach it.
   */
  private void reopenWithReplayBounds(int maxRetries, long retryWindowNanos) throws Exception
  {
    closeAndRemove(storage);
    storage = new PDBStorage(createBackendCfg(), serverContext, maxRetries, retryWindowNanos);
    storage.open(AccessMode.READ_WRITE);
  }

  /**
   * The sources are wrapped rather than copied: a value on its way into Persistit is copied twice more -
   * {@code toByteArray()} and the value buffer of the exchange, which doubles up to 64 MB - so with a copy
   * here as well the 63 MB value had four copies of itself live at once, on top of the buffer pool and the
   * server: about 310 MB left after a collection, in a JVM of 512 MB, which one CI leg ran out of. Wrapped,
   * about 165 MB. The three values stay in one transaction on purpose: the value buffer the 32 MB one
   * grew fits the 63 MB one without growing again.
   */
  @Test
  public void testCanAddLargeValues() throws Exception
  {
    storage.write(new WriteOperation()
    {
      private final TreeName treeName = new TreeName("dc=test", "test");

      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(treeName, true);
        txn.put(treeName, valueOfUtf8("4mb"), wrap(new byte[4 * MB]));
        txn.put(treeName, valueOfUtf8("32mb"), wrap(new byte[32 * MB]));
        // 64Mb is the maximum allowed for value size. But Persistit has header reducing the payload.
        txn.put(treeName, valueOfUtf8("64mb"), wrap(new byte[63 * MB]));
      }
    });
  }

  @Test
  public void testExchangeWithSmallValuesAreReleasedToPool() throws Exception
  {
    final Exchange initial = storage.getNewExchange(treeName, true);
    storage.releaseExchange(initial);

    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.put(treeName, valueOfUtf8("small"), valueOfBytes(new byte[512 * KB]));
      }
    });

    assertThat(storage.getNewExchange(treeName, true)).isSameAs(initial);
  }

  @Test
  public void testExchangeWithLargeValuesAreNotReleasedToPool() throws Exception
  {
    final Exchange initial = storage.getNewExchange(treeName, true);
    storage.releaseExchange(initial);

    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.put(treeName, valueOfUtf8("small"), valueOfBytes(new byte[16 * MB]));
      }
    });

    assertThat(storage.getNewExchange(treeName, true)).isNotSameAs(initial);
  }

  @Test
  public void testWriteGivesUpAfterTheAttemptCap() throws Exception
  {
    // the shipped cap, against a window the ladder of backoffs cannot reach: on the shipped window those nine
    // backoffs draw from up to 5550 ms, so a loaded machine ends this loop on the window and the cap goes untested
    reopenWithReplayBounds(PDBStorage.MAX_RETRIES, UNREACHABLE_RETRY_WINDOW_NANOS);
    createTree();

    final RollbackException conflict = new RollbackException();
    final AtomicInteger attempts = new AtomicInteger();
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          attempts.incrementAndGet();
          txn.put(treeName, valueOfUtf8("abandoned"), valueOfUtf8("value"));
          throw conflict;
        }
      });
      failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
    }
    catch (StorageRuntimeException e)
    {
      assertThat(e.getSuppressed()).contains(conflict);
    }
    assertThat(attempts.get()).isEqualTo(PDBStorage.MAX_RETRIES);
    assertThat(read("abandoned")).isNull();
  }

  @Test
  public void testWriteIsReplayedUntilTheConflictClears() throws Exception
  {
    createTree();

    final AtomicInteger attempts = new AtomicInteger();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        if (attempts.incrementAndGet() <= 3)
        {
          throw new RollbackException();
        }
        txn.put(treeName, valueOfUtf8("applied"), valueOfUtf8("value"));
      }
    });

    assertThat(attempts.get()).isEqualTo(4);
    assertThat(read("applied")).isEqualTo(valueOfUtf8("value"));
  }

  /**
   * PersistIt reports a write-write conflict only once it has waited on it - up to
   * {@code SharedResource.DEFAULT_MAX_WAIT_TIME}, a minute, which this backend never lowers - so a single attempt
   * can outlast the whole window. Giving up on the window alone would then replay nothing, in the very case where
   * the replay is likeliest to succeed: the transaction that was blocking this one has just finished.
   */
  @Test
  public void testWriteIsReplayedOnceWhenTheFirstAttemptOutlastsTheWindow() throws Exception
  {
    reopenWithReplayBounds(PDBStorage.MAX_RETRIES, SHORT_RETRY_WINDOW_NANOS);
    createTree();

    final AtomicInteger attempts = new AtomicInteger();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        if (attempts.incrementAndGet() == 1)
        {
          Thread.sleep(ATTEMPT_LONGER_THAN_SHORT_WINDOW_MS);
          throw new RollbackException();
        }
        txn.put(treeName, valueOfUtf8("outlasted"), valueOfUtf8("written"));
      }
    });

    assertThat(attempts.get()).isEqualTo(2);
    assertThat(read("outlasted")).isEqualTo(valueOfUtf8("written"));
  }

  @Test
  public void testExhaustedWriteNamesTheAttemptsItSpent() throws Exception
  {
    // the message is the same at any cap, so this one is spent in two backoffs rather than in the shipped ladder
    final int maxRetries = 3;
    reopenWithReplayBounds(maxRetries, UNREACHABLE_RETRY_WINDOW_NANOS);
    createTree();

    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          throw new RollbackException();
        }
      });
      failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
    }
    catch (StorageRuntimeException e)
    {
      assertThat(e.getMessage()).contains("PDBStorageTest").contains(maxRetries + " attempts");
      // and which of the two bounds ran out, since the attempt count alone does not say
      assertThat(e.getMessage()).contains("attempt cap");
      // write() unwraps a StorageRuntimeException that carries a cause, which would replace this message with
      // the bare RollbackException, and it is the message the config change paths report
      assertThat(e.getCause()).isNull();
    }
  }

  @Test
  public void testWriteGivesUpOnTheWindowWhenAttemptsAreSlow() throws Exception
  {
    reopenWithReplayBounds(PDBStorage.MAX_RETRIES, SHORT_RETRY_WINDOW_NANOS);
    createTree();

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
          Thread.sleep(ATTEMPT_LONGER_THAN_SHORT_WINDOW_MS);
          throw new RollbackException();
        }
      });
      failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
    }
    catch (StorageRuntimeException e)
    {
      // the window is what ended it, and it says so: an assertion on the attempt count alone would also pass for
      // a give up on attempt 1, which is the regression the attempt > 1 exemption exists to prevent
      assertThat(e.getMessage()).contains("retry window");
    }
    // one attempt beyond the first: the first spends the window, the exemption grants the replay, and the check
    // after that replay is the one that gives up
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  public void testInterruptedWriteReportsTheConflictItWasReplaying() throws Exception
  {
    createTree();

    final RollbackException conflict = new RollbackException();
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
          // interrupted here rather than before the write, where the transaction this attempt begins would
          // report the interrupt itself and the loop would never reach the backoff being tested
          Thread.currentThread().interrupt();
          throw conflict;
        }
      });
      failBecauseExceptionWasNotThrown(StorageRuntimeException.class);
      return;
    }
    catch (StorageRuntimeException e)
    {
      interruptedAfterwards = Thread.interrupted();
      // the conflict, not the interrupt, is what the caller is told about - but through the same shape the
      // exhausted loop uses, since a bare RollbackException reaches every caller as its own class name
      assertThat(e.getMessage()).contains("PDBStorageTest").contains("interrupted");
      assertThat(e.getSuppressed()).contains(conflict).hasAtLeastOneElementOfType(InterruptedException.class);
      assertThat(e.getCause()).isNull();
    }
    finally
    {
      Thread.interrupted();
    }
    // sleep() cleared the flag, so the caller only learns of the interrupt if the loop restores it
    assertThat(interruptedAfterwards).isTrue();
    // one attempt even though the first backoff is a random 0-49 ms and so is sometimes 0: Thread.sleep() checks
    // the interrupt flag before it checks for a zero duration, so the replay is never reached
    assertThat(attempts.get()).isEqualTo(1);
  }

  /**
   * The delay grows with the attempt and stays under the cap, so that a contention the first delays did not
   * outlast still has a chance to clear without the replays overrunning the window on sleep alone.
   */
  @Test
  public void testRetryDelayGrowsAndStaysBounded()
  {
    long previousBound = 0;
    for (int attempt = 1; attempt <= PDBStorage.MAX_RETRIES; attempt++)
    {
      long bound = 0;
      for (int i = 0; i < 100; i++)
      {
        final long delay = PDBStorage.retryDelayMillis(attempt);
        assertThat(delay).as("attempt %d", attempt).isGreaterThanOrEqualTo(0).isLessThan(1000);
        bound = Math.max(bound, delay);
      }
      if (attempt == 1)
      {
        // the flat sleep this loop took before it was bounded, unchanged: only the later attempts back off
        assertThat(bound).as("attempt 1 delays past the sleep this loop always took").isLessThan(50);
      }
      assertThat(bound).as("attempt %d did not grow past attempt %d", attempt, attempt - 1)
          .isGreaterThanOrEqualTo(previousBound / 2);
      previousBound = bound;
    }
    // and the growth is real rather than a delay that never leaves the first tier
    long grown = 0;
    for (int i = 0; i < 100; i++)
    {
      grown = Math.max(grown, PDBStorage.retryDelayMillis(PDBStorage.MAX_RETRIES));
    }
    assertThat(grown).as("the last attempts still sleep within the first attempt's bound").isGreaterThan(500);
  }

  /**
   * An open which fails gives back what it took before it failed: the memory it reserved for the
   * cache, and the listener the constructor registered on the backend configuration. Nothing else
   * will - a root container does not close a storage which did not open - and a backend whose
   * volume another storage holds is enabled again and again, each attempt draining one cache size.
   */
  @Test
  public void aStorageWhoseOpenFailedGivesBackWhatItTook() throws Exception
  {
    final PDBBackendCfg cfg = createBackendCfg();
    // Over the volume the storage of setUp() holds: what a second attempt to enable the backend meets.
    final PDBStorage second = new PDBStorage(cfg, serverContext);
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open over a volume another storage holds");
    }
    catch (StorageInUseException expected)
    {
      // What the lock on the volume file does.
    }

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
    verify(cfg).removePDBChangeListener(second);
  }

  /**
   * A storage whose open failed has given everything back already, so closing it afterwards takes
   * nothing more - {@code BackendImpl.importLDIF} closes the storage of its root container however
   * the import ended - and does not fail on what the open never got to.
   */
  @Test
  public void closingAStorageWhoseOpenFailedTakesNothingMore() throws Exception
  {
    final PDBStorage second = new PDBStorage(createBackendCfg(), serverContext);
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open over a volume another storage holds");
    }
    catch (StorageInUseException expected)
    {
      // What the lock on the volume file does.
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
    createTree();
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
    // Still open: a read reaches the database.
    assertThat(read("missing")).isNull();
  }

  /**
   * An open which fails once the database is open gives the database back with the rest: the
   * volume, or no later open of the backend can take it, and the monitor the open registered. The
   * disk monitor is the one thing past the database open that a test can refuse.
   */
  @Test
  public void aStorageWhoseOpenFailedAfterItsDatabaseOpenedGivesTheDatabaseBack() throws Exception
  {
    // The volume of setUp() is given up first: held, it fails the open before the database is built.
    closeAndRemove(storage);
    final DiskSpaceMonitor refusing = mock(DiskSpaceMonitor.class);
    doThrow(new IllegalStateException("the directory cannot be monitored"))
        .when(refusing).registerMonitoredDirectory(anyString(), any(File.class), anyLong(), anyLong(), any());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(refusing);
    final PDBBackendCfg cfg = createBackendCfg();
    final PDBStorage second = new PDBStorage(cfg, serverContext);
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open when its directory cannot be monitored");
    }
    catch (IllegalStateException expected)
    {
      // What the failure past the database open does.
    }

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
    verify(cfg).removePDBChangeListener(second);
    assertThat(DirectoryServer.getMonitorProviders()).doesNotContainKey("pdbstoragetest pdb database");
    // The volume was given back: a storage over the same directory opens.
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));
    storage = new PDBStorage(createBackendCfg(), serverContext);
    storage.open(AccessMode.READ_WRITE);
  }

  /**
   * A cache size changed while the storage is open is given back as it was taken: the close
   * releases what the open reserved, not what the configuration says by then. Read from the
   * configuration at both ends, a change in between drifts the quota by the difference for the
   * life of the JVM - the open which follows reserves the new size and pays nothing back.
   */
  @Test
  public void aCacheGrownWhileOpenIsGivenBackAsItWasTaken() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    final long availableBefore = quota.getAvailableMemory();
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore - SMALL_CACHE);

    storage.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));
    storage.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /** The shrink is the same drift the other way: the difference stays reserved by nobody. */
  @Test
  public void aCacheShrunkWhileOpenIsGivenBackAsItWasTaken() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    final long availableBefore = quota.getAvailableMemory();
    storage = new PDBStorage(createBackendCfg(2 * SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);

    storage.applyConfigurationChange(createBackendCfg(SMALL_CACHE));
    storage.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /**
   * The buffer pool is sized when the database opens and PersistIt has no way to resize it, so a
   * change of the cache size is applied by the next open of the backend - and the operator is told
   * so, rather than that the change applied.
   */
  @Test
  public void aCacheSizeChangedWhileOpenAsksForARestart() throws Exception
  {
    closeAndRemove(storage);
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);

    final ConfigChangeResult ccr = storage.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).hasSize(1);
    assertThat(ccr.getMessages().get(0).ordinal()).isEqualTo(NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.ordinal());
    assertThat(ccr.getMessages().get(0).toString()).isEqualTo(
        NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.get("PDBStorageTest", SMALL_CACHE, 2 * SMALL_CACHE).toString());

    // The pool still runs at the size it was opened with, whatever the change before said: back to
    // that size, there is nothing left to restart for.
    final ConfigChangeResult back = storage.applyConfigurationChange(createBackendCfg(SMALL_CACHE));
    assertThat(back.adminActionRequired()).isFalse();
    assertThat(back.getMessages()).isEmpty();
  }

  /**
   * The default cache is sized by db-cache-percent, db-cache-size left at 0: the restart is asked
   * for by the size the percentage comes to, not by db-cache-size, which does not move.
   */
  @Test
  public void aCacheSizedByPercentAsksForARestartOnlyWhenThePercentChanges() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    storage = new PDBStorage(createBackendCfg(0L, 10), serverContext);
    storage.open(AccessMode.READ_WRITE);
    final PDBBackendCfg unchangedCache = createBackendCfg(0L, 10);
    when(unchangedCache.isDBTxnNoSync()).thenReturn(true);

    final ConfigChangeResult unchanged = storage.applyConfigurationChange(unchangedCache);
    assertThat(unchanged.adminActionRequired()).isFalse();
    assertThat(unchanged.getMessages()).isEmpty();

    final ConfigChangeResult ccr = storage.applyConfigurationChange(createBackendCfg(0L, 20));
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).hasSize(1);
    assertThat(ccr.getMessages().get(0).toString()).isEqualTo(NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.get(
        "PDBStorageTest", quota.memPercentToBytes(10), quota.memPercentToBytes(20)).toString());
  }

  /**
   * A storage which has not opened runs no cache to restart: a change of the cache size is picked
   * up by the open, and asks for nothing. The listener is registered by the constructor already.
   */
  @Test
  public void aStorageWhichIsNotOpenAsksForNoRestart() throws Exception
  {
    final PDBStorage unopened = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    try
    {
      final ConfigChangeResult ccr = unopened.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));

      assertThat(ccr.adminActionRequired()).isFalse();
      for (LocalizableMessage message : ccr.getMessages())
      {
        assertThat(message.ordinal()).isNotEqualTo(NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.ordinal());
      }
    }
    finally
    {
      unopened.close();
    }
  }

  /** A change which leaves the cache size alone asks for nothing, as before. */
  @Test
  public void aChangeWhichLeavesTheCacheSizeAloneAsksForNothing() throws Exception
  {
    closeAndRemove(storage);
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    final PDBBackendCfg unchangedCache = createBackendCfg(SMALL_CACHE);
    when(unchangedCache.isDBTxnNoSync()).thenReturn(true);

    final ConfigChangeResult ccr = storage.applyConfigurationChange(unchangedCache);

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isFalse();
    assertThat(ccr.getMessages()).isEmpty();
  }

  /**
   * A change of the cache size is admitted against what the storage holds of the quota, which is
   * what the next open has to add to. Once a change has been admitted but not applied, the
   * configuration says the new size while the reservation is still the old one, and a check
   * against the configuration would admit a second change the server has no memory for.
   */
  @Test
  public void aCacheSizeChangeIsAdmittedAgainstWhatTheStorageHolds() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    storage.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));
    // Room for two caches and a bit: the difference to the configured size, not to the reserved one.
    assertThat(quota.acquireMemory(quota.getAvailableMemory() - 2 * SMALL_CACHE - MB)).isTrue();

    final List<LocalizableMessage> reasons = new ArrayList<>();
    assertThat(storage.isConfigurationChangeAcceptable(createBackendCfg(4 * SMALL_CACHE), reasons))
        .as("four caches, with one reserved and two and a bit free").isFalse();
    assertThat(storage.isConfigurationChangeAcceptable(createBackendCfg(3 * SMALL_CACHE), reasons))
        .as("three caches, with one reserved and two and a bit free").isTrue();
  }

  /**
   * A reservation the quota refused is not given back on close. The open goes ahead without it -
   * the quota is a budget, not a lock - but a close which released what was never taken would
   * hand the quota memory the server does not have.
   */
  @Test
  public void aReservationTheQuotaRefusedIsNotGivenBackOnClose() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    // Half a cache left in the quota: the reservation of a whole one is refused.
    assertThat(quota.acquireMemory(quota.getAvailableMemory() - SMALL_CACHE / 2)).isTrue();
    final long availableBefore = quota.getAvailableMemory();
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);

    storage.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /**
   * After an open the quota refused, the storage holds nothing of the quota, and a change which
   * leaves the cache size alone - any other property, the disable an online import makes - still
   * asks the quota for nothing: every change of the backend entry is put to this storage.
   */
  @Test
  public void aChangeWhichLeavesTheCacheSizeAloneIsAdmittedAfterARefusedReservation() throws Exception
  {
    openWithTheReservationRefused();
    final PDBBackendCfg unchangedCache = createBackendCfg(SMALL_CACHE);
    when(unchangedCache.isDBTxnNoSync()).thenReturn(true);

    assertThat(storage.isConfigurationChangeAcceptable(unchangedCache, new ArrayList<LocalizableMessage>()))
        .isTrue();
  }

  /**
   * A growth after an open the quota refused is measured against what the storage holds, which is
   * nothing: a quarter of a cache more than configured is a cache and a quarter more than held.
   */
  @Test
  public void aGrowthAfterARefusedReservationIsMeasuredAgainstNothingHeld() throws Exception
  {
    openWithTheReservationRefused();

    assertThat(storage.isConfigurationChangeAcceptable(
        createBackendCfg(SMALL_CACHE + SMALL_CACHE / 4), new ArrayList<LocalizableMessage>()))
        .as("a cache and a quarter, with nothing held and half a cache free").isFalse();
  }

  /** A shrink asks the quota for nothing, even with none of it left. */
  @Test
  public void aShrinkIsAdmittedWithTheQuotaExhausted() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    assertThat(quota.acquireMemory(quota.getAvailableMemory())).isTrue();

    assertThat(storage.isConfigurationChangeAcceptable(
        createBackendCfg(SMALL_CACHE / 2), new ArrayList<LocalizableMessage>())).isTrue();
  }

  /** Opens a storage of one cache with half a cache left in the quota, so that its reservation is refused. */
  private void openWithTheReservationRefused() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    assertThat(quota.acquireMemory(quota.getAvailableMemory() - SMALL_CACHE / 2)).isTrue();
    final long availableBefore = quota.getAvailableMemory();
    storage = new PDBStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  private void createTree() throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(treeName, true);
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

  protected PDBBackendCfg createBackendCfg()
  {
    return createBackendCfg(0L);
  }

  /** A configuration whose cache is the given size in bytes, or a fifth of the quota when it is zero. */
  private static PDBBackendCfg createBackendCfg(long cacheSize)
  {
    return createBackendCfg(cacheSize, 20);
  }

  /** A configuration whose cache is the given size in bytes, or the given percent of the quota when it is zero. */
  private static PDBBackendCfg createBackendCfg(long cacheSize, int cachePercent)
  {
    PDBBackendCfg backendCfg = mockCfg(PDBBackendCfg.class);
    when(backendCfg.getBackendId()).thenReturn("PDBStorageTest");
    when(backendCfg.getDBDirectory()).thenReturn("PDBStorageTest");
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(cacheSize);
    when(backendCfg.getDBCachePercent()).thenReturn(cachePercent);
    return backendCfg;
  }

}
