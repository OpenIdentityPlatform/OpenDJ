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

import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
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
        txn.put(treeName, valueOfUtf8("4mb"), valueOfBytes(new byte[4 * MB]));
        txn.put(treeName, valueOfUtf8("32mb"), valueOfBytes(new byte[32 * MB]));
        // 64Mb is the maximum allowed for value size. But Persistit has header reducing the payload.
        txn.put(treeName, valueOfUtf8("64mb"), valueOfBytes(new byte[63 * MB]));
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
    PDBBackendCfg backendCfg = mockCfg(PDBBackendCfg.class);
    when(backendCfg.getBackendId()).thenReturn("PDBStorageTest");
    when(backendCfg.getDBDirectory()).thenReturn("PDBStorageTest");
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    return backendCfg;
  }

}
