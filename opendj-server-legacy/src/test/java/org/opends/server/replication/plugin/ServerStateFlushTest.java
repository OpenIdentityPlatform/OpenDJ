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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static java.util.concurrent.TimeUnit.*;

import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.util.SortedSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the state checkpointer of a {@link LDAPReplicationDomain} against a backend which
 * fails the write of the replication state.
 */
@SuppressWarnings("javadoc")
public class ServerStateFlushTest extends ReplicationTestCase
{
  private static final String BACKEND_ID = "serverStateFlushTest";
  private static final String BASE_DN_STRING = "o=serverStateFlushTest";
  private static final int DS_ID = 1;
  private static final int RS_ID = 601;

  /** How long a test waits for something the checkpointer does on its own, in seconds. */
  private static final int CHECKPOINT_TIMEOUT_IN_SECS = 20;

  private DN baseDN;
  private StateWriteFailureBackend backend;
  private ReplicationServer replicationServer;
  private LDAPReplicationDomain domain;
  private Thread checkpointer;
  private boolean domainDeleted;

  /** What the backend does with a write of the replication state instead of writing it. */
  private enum StateWrite
  {
    /** Write it, the way the backend normally would. */
    SUCCEEDS,
    /** Throw a {@link RuntimeException} the way an unwrapped driver failure would. */
    THROWS_RUNTIME_EXCEPTION,
    /** Throw an {@link Error}, which no {@code catch} of the checkpointer can be expected to hold. */
    THROWS_ERROR,
    /** Block until the test releases it, the way a write to an unresponsive storage would. */
    BLOCKS;
  }

  /** A memory backend whose write of the replication state fails on demand. */
  private static final class StateWriteFailureBackend extends MemoryBackend
  {
    private final DN stateEntryDN;
    private volatile StateWrite stateWrite = StateWrite.SUCCEEDS;
    private final AtomicInteger failedStateWrites = new AtomicInteger();
    private final CountDownLatch blockedWriteStarted = new CountDownLatch(1);
    private final CountDownLatch blockedWriteReleased = new CountDownLatch(1);

    private StateWriteFailureBackend(DN stateEntryDN)
    {
      this.stateEntryDN = stateEntryDN;
    }

    /**
     * Not synchronized, unlike the method it overrides: a write which blocks must not hold
     * the monitor of the backend, or every other operation on it would block with it.
     */
    @Override
    public void replaceEntry(Entry oldEntry, Entry newEntry, ModifyOperation modifyOperation)
        throws DirectoryException
    {
      if (stateEntryDN.equals(newEntry.getName()))
      {
        switch (stateWrite)
        {
        case THROWS_RUNTIME_EXCEPTION:
          failedStateWrites.incrementAndGet();
          throw new IllegalStateException("injected failure of a replication state write");
        case THROWS_ERROR:
          failedStateWrites.incrementAndGet();
          throw new OutOfMemoryError("injected failure of a replication state write");
        case BLOCKS:
          blockedWriteStarted.countDown();
          try
          {
            blockedWriteReleased.await();
          }
          catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
            return;
          }
          break;
        default:
          break;
        }
      }
      super.replaceEntry(oldEntry, newEntry, modifyOperation);
    }
  }

  @BeforeMethod
  public void setUpDomain() throws Exception
  {
    baseDN = DN.valueOf(BASE_DN_STRING);

    backend = new StateWriteFailureBackend(baseDN);
    backend.setBackendID(BACKEND_ID);
    backend.setBaseDNs(baseDN);
    backend.configureBackend(null, TestCaseUtils.getServerContext());
    backend.openBackend();
    TestCaseUtils.getServerContext().getBackendConfigManager().registerLocalBackend(backend);
    backend.addEntry(createEntry(baseDN), null);

    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        TestCaseUtils.findFreePort(), "serverStateFlushTestDb", 0, RS_ID, 0, 100, null));

    final SortedSet<String> replServers = newTreeSet("localhost:" + replicationServer.getReplicationPort());
    domain = MultimasterReplication.createNewDomain(new DomainFakeCfg(baseDN, DS_ID, replServers));
    domainDeleted = false;
    domain.start();

    checkpointer = checkpointerOf(domain);
    assertNotNull(checkpointer, "the state checkpointer of the domain was not started");
    assertTrue(checkpointer.isAlive(), "the state checkpointer of the domain is not running");
  }

  /**
   * Takes back what {@link #setUpDomain()} put in place, one resource at a time: a setup
   * which failed halfway through must not leave a backend or a domain behind for the next
   * test of the class to trip over.
   */
  @AfterMethod
  public void tearDownDomain() throws Exception
  {
    if (backend != null)
    {
      // Let go of a write the test left blocked, and of the checkpointer waiting on it,
      // before the backend it is writing to is taken away.
      backend.stateWrite = StateWrite.SUCCEEDS;
      backend.blockedWriteReleased.countDown();
    }
    if (checkpointer != null)
    {
      checkpointer.join(SECONDS.toMillis(CHECKPOINT_TIMEOUT_IN_SECS));
      checkpointer = null;
    }
    if (domain != null && !domainDeleted)
    {
      deleteDomain();
    }
    domain = null;
    if (replicationServer != null)
    {
      remove(replicationServer);
      replicationServer = null;
    }
    if (backend != null)
    {
      backend.finalizeBackend();
      TestCaseUtils.getServerContext().getBackendConfigManager().deregisterLocalBackend(backend);
      backend = null;
    }
  }

  /**
   * A write of the state which throws must not stop the checkpointer: the state stays unsaved,
   * and the next checkpoint writes it.
   */
  @Test(timeOut = 120000)
  public void checkpointerKeepsCheckpointingAfterAStateWriteThatThrows() throws Exception
  {
    backend.stateWrite = StateWrite.THROWS_RUNTIME_EXCEPTION;
    final CSN csn = newCSN();
    domain.getServerState().update(csn);

    waitForFailedStateWrites(1);

    backend.stateWrite = StateWrite.SUCCEEDS;
    checkEntryHasAttributeValue(baseDN, "ds-sync-state", csn.toString(), CHECKPOINT_TIMEOUT_IN_SECS,
        "the checkpointer did not write the state after a write which threw");
  }

  /** Shutting the domain down must not wait forever for a checkpointer whose writes all throw. */
  @Test(timeOut = 120000)
  public void shutdownCompletesWhenEveryStateWriteThrows() throws Exception
  {
    backend.stateWrite = StateWrite.THROWS_RUNTIME_EXCEPTION;
    domain.getServerState().update(newCSN());

    waitForFailedStateWrites(1);

    deleteDomain();

    assertFalse(checkpointer.isAlive(), "the state checkpointer is still running after the shutdown");
  }

  /**
   * Shutting the domain down must not wait forever for a checkpointer which is gone: an
   * {@link Error} kills the thread whatever it catches.
   */
  @Test(timeOut = 120000)
  public void shutdownCompletesWhenTheCheckpointerDiedOfAnError() throws Exception
  {
    backend.stateWrite = StateWrite.THROWS_ERROR;
    domain.getServerState().update(newCSN());

    waitForCheckpointerToDie();

    deleteDomain();
  }

  /**
   * Shutting the domain down must not wait forever for a checkpointer whose write does not
   * come back.
   */
  @Test(timeOut = 120000)
  public void shutdownCompletesWhileAStateWriteIsStuck() throws Exception
  {
    backend.stateWrite = StateWrite.BLOCKS;
    domain.getServerState().update(newCSN());

    assertTrue(backend.blockedWriteStarted.await(CHECKPOINT_TIMEOUT_IN_SECS, SECONDS),
        "the checkpointer did not start the state write the test blocks on");

    deleteDomain();
  }

  private CSN newCSN()
  {
    return new CSN(System.currentTimeMillis(), 1, DS_ID);
  }

  private void deleteDomain()
  {
    domainDeleted = true;
    MultimasterReplication.deleteDomain(baseDN);
  }

  private void waitForFailedStateWrites(final int count) throws Exception
  {
    newTimer().repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertTrue(backend.failedStateWrites.get() >= count,
            "the state write of the checkpointer did not fail " + count + " time(s)");
      }
    });
  }

  private void waitForCheckpointerToDie() throws Exception
  {
    newTimer().repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertFalse(checkpointer.isAlive(), "the state checkpointer did not die of the injected error");
      }
    });
  }

  private TestTimer newTimer()
  {
    return new TestTimer.Builder()
        .maxSleep(CHECKPOINT_TIMEOUT_IN_SECS, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer();
  }

  private static Thread checkpointerOf(LDAPReplicationDomain domain)
  {
    final String name =
        "Replica DS(" + domain.getServerId() + ") state checkpointer for domain \"" + domain.getBaseDN() + "\"";
    final ThreadGroup group = DirectoryThread.DIRECTORY_THREAD_GROUP;
    final Thread[] threads = new Thread[group.activeCount() * 2 + 10];
    final int count = group.enumerate(threads, true);
    for (int i = 0; i < count; i++)
    {
      if (name.equals(threads[i].getName()))
      {
        return threads[i];
      }
    }
    return null;
  }
}
