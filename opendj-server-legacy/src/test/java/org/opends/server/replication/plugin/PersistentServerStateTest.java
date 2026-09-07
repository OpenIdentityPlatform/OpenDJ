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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Test the PersistentServerState class.
 */
@SuppressWarnings("javadoc")
public class PersistentServerStateTest extends ReplicationTestCase
{
  /**
   * The suffix for which we want to test the PersistentServerState class.
   */
  @DataProvider(name = "suffix")
  public Object[][] suffixData() {
    return new Object[][] {
       {TEST_ROOT_DN_STRING},
       {"cn=schema"}
    };
  }

  /**
   * Test that the PersistentServerState class is able to store and
   * retrieve ServerState to persistent storage.
   */
  @Test(dataProvider = "suffix")
  public void persistentServerStateTest(String dn) throws Exception
  {
    /*
     * Create a new PersistentServerState,
     * update it with 2 new csns with 2 different server Ids
     * save it
     *
     * Then creates a new PersistentServerState and check that the
     * 2 csns have been saved in this new PersistentServerState.
     */
    DN baseDn = DN.valueOf(dn);
    ServerState origState = new ServerState();
    PersistentServerState state =
        new PersistentServerState(baseDn,  1, origState);
    CSNGenerator gen1 = new CSNGenerator(1, origState);
    CSNGenerator gen2 = new CSNGenerator(2, origState);

    CSN csn1 = gen1.newCSN();
    CSN csn2 = gen2.newCSN();

    assertEquals(state.update(csn1), true);
    assertEquals(state.update(csn2), true);
    state.save();

    PersistentServerState stateSaved =
        new PersistentServerState(baseDn, 1, new ServerState());
    CSN csn1Saved = stateSaved.getMaxCSN(1);
    CSN csn2Saved = stateSaved.getMaxCSN(2);

    assertEquals(csn1Saved, csn1,
        "csn1 has not been saved or loaded correctly for " + dn);
    assertEquals(csn2Saved, csn2,
        "csn2 has not been saved or loaded correctly for " + dn);

    state.clear();
    stateSaved = new PersistentServerState(baseDn, 1, new ServerState());
    csn1Saved = stateSaved.getMaxCSN(1);
    assertNull(csn1Saved, "csn1 has not been saved after clear for " + dn);
  }

  /**
   * An update landing while the state is being written cannot be part of that
   * write, so it must leave the state unsaved and be written by the next save.
   * Marking the state as saved on behalf of a write that does not carry the
   * update strands it on a quiet domain until some later change comes along.
   */
  @Test(dataProvider = "suffix")
  public void updateLandingDuringSaveIsWrittenByTheNextSave(String dn) throws Exception
  {
    final DN baseDn = DN.valueOf(dn);
    final ServerState serverState = new ServerState();
    final AtomicReference<CSN> racing = new AtomicReference<>();

    // the update in racing lands inside the write, once its snapshot was taken
    final PersistentServerState state = new HookedWrite(baseDn, 1, serverState, null, () -> {
      final CSN racingCSN = racing.getAndSet(null);
      if (racingCSN != null)
      {
        serverState.update(racingCSN);
      }
    });
    try
    {
      // seeded from the state the constructor above loaded, so that both CSNs
      // are newer than whatever the entry already holds
      final CSNGenerator gen = new CSNGenerator(1, serverState);
      final CSN writtenCSN = gen.newCSN();
      final CSN racingCSN = gen.newCSN();
      racing.set(racingCSN);

      assertTrue(state.update(writtenCSN));

      state.save();
      assertEquals(loadMaxCSN(baseDn, 1), writtenCSN,
          "the racing CSN cannot be part of the write it raced with");
      assertFalse(serverState.isSaved(),
          "an update that landed during the write must leave the state unsaved");

      // the next tick of the checkpointer
      state.save();
      assertTrue(serverState.isSaved());
      assertEquals(loadMaxCSN(baseDn, 1), racingCSN,
          "the racing CSN must be written by the next save");
    }
    finally
    {
      state.clear();
    }
  }

  /**
   * A state that already holds CSNs of its own when it is loaded has not been
   * written by this object: loading only merges in what the backend holds, so
   * whatever came in with the state is still owed to persistent storage.
   */
  @Test
  public void stateLoadedOverCSNsOfItsOwnIsNotConsideredSaved() throws Exception
  {
    final DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    final ServerState serverState = new ServerState();
    final CSN ownCSN = new CSNGenerator(1, serverState).newCSN();
    assertTrue(serverState.update(ownCSN));
    // as handed over by a caller that believes it to be persisted
    serverState.setSaved(true);

    final PersistentServerState state = new PersistentServerState(baseDn, 1, serverState);
    try
    {
      assertFalse(serverState.isSaved(),
          "a state loaded over CSNs that are not known to be on disk must not look saved");

      state.save();
      assertEquals(loadMaxCSN(baseDn, 1), ownCSN, "the CSN the state came with must reach the backend");
    }
    finally
    {
      state.clear();
    }
  }

  /**
   * A write reporting a failure must leave the state unsaved: the state is
   * marked as saved before the write, and nothing else would clear that flag.
   */
  @Test
  public void writeThatFailsLeavesTheStateUnsaved() throws Exception
  {
    final DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    final ServerState serverState = new ServerState();
    final PersistentServerState state =
        new HookedWrite(baseDn, 1, serverState, ResultCode.UNWILLING_TO_PERFORM, null);
    try
    {
      assertTrue(state.update(new CSNGenerator(1, serverState).newCSN()));

      state.save();
      assertFalse(serverState.isSaved(), "a write that failed must leave the state unsaved");
    }
    finally
    {
      new PersistentServerState(baseDn, 1, new ServerState()).clear();
    }
  }

  /**
   * A write blowing up on its way to the backend must leave the state unsaved,
   * for the same reason a write reporting a failure must.
   */
  @Test
  public void writeThatThrowsLeavesTheStateUnsaved() throws Exception
  {
    final DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    final ServerState serverState = new ServerState();
    final IllegalStateException blowUp = new IllegalStateException("the write blows up");
    final PersistentServerState state = new HookedWrite(baseDn, 1, serverState, null, () -> {
      throw blowUp;
    });
    try
    {
      assertTrue(state.update(new CSNGenerator(1, serverState).newCSN()));

      try
      {
        state.save();
        fail("the write was expected to blow up");
      }
      catch (IllegalStateException e)
      {
        assertSame(e, blowUp, "save() threw something other than the failure of the write");
      }
      assertFalse(serverState.isSaved(), "a write that blew up must leave the state unsaved");
    }
    finally
    {
      new PersistentServerState(baseDn, 1, new ServerState()).clear();
    }
  }

  /**
   * Two saves must not write the state at the same time: the write of the older
   * snapshot could otherwise land last, leaving a state on disk that is both
   * stale and marked as saved.
   */
  @Test
  public void concurrentSavesDoNotWriteTheStateAtTheSameTime() throws Exception
  {
    final DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    final ServerState serverState = new ServerState();

    final AtomicInteger writersInside = new AtomicInteger();
    final AtomicInteger mostWritersInside = new AtomicInteger();
    final AtomicBoolean secondWriteGotIn = new AtomicBoolean();
    final AtomicBoolean firstWrite = new AtomicBoolean(true);
    final CountDownLatch firstWriteStarted = new CountDownLatch(1);
    final CountDownLatch secondWriteStarted = new CountDownLatch(1);

    final PersistentServerState state = new HookedWrite(baseDn, 1, serverState, null, () -> {
      mostWritersInside.accumulateAndGet(writersInside.incrementAndGet(), Math::max);
      if (firstWrite.compareAndSet(true, false))
      {
        firstWriteStarted.countDown();
        try
        {
          // The second save, if nothing keeps it out, reaches this same hook
          // and counts the latch down while this write is still in flight. The
          // wait is what gives it the chance; it is expected to time out.
          secondWriteGotIn.set(secondWriteStarted.await(2, SECONDS));
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new AssertionError("interrupted while holding the first write", e);
        }
      }
      else
      {
        secondWriteStarted.countDown();
      }
      writersInside.decrementAndGet();
    });

    final AtomicReference<Throwable> checkpointerFailure = new AtomicReference<>();
    final Thread checkpointer = new Thread(state::save, "test state checkpointer");
    checkpointer.setDaemon(true);
    checkpointer.setUncaughtExceptionHandler((t, e) -> checkpointerFailure.set(e));
    try
    {
      final CSNGenerator gen = new CSNGenerator(1, serverState);
      assertTrue(state.update(gen.newCSN()));
      checkpointer.start();
      assertTrue(firstWriteStarted.await(10, SECONDS), "the first save never reached its write");

      // dirty the state so that the second save has something to write
      assertTrue(state.update(gen.newCSN()));
      state.save();

      checkpointer.join(SECONDS.toMillis(30));
      assertFalse(checkpointer.isAlive(), "the first save never completed");
      assertNull(checkpointerFailure.get(), "the first save failed: " + checkpointerFailure.get());
      assertFalse(secondWriteGotIn.get(),
          "the second save reached its write while the first one was still writing");
      assertEquals(mostWritersInside.get(), 1, "two saves wrote the state at the same time");
    }
    finally
    {
      // Only touch the state once the other thread is out of it: clear() saves,
      // and a save waiting on a wedged one would hang instead of reporting.
      checkpointer.join(SECONDS.toMillis(30));
      if (!checkpointer.isAlive())
      {
        state.clear();
      }
    }
  }

  private CSN loadMaxCSN(DN baseDn, int serverId)
  {
    return new PersistentServerState(baseDn, serverId, new ServerState()).getMaxCSN(serverId);
  }

  /**
   * A PersistentServerState whose write can be steered from the test, at the
   * point where the snapshot that goes to the backend has already been taken.
   */
  private static final class HookedWrite extends PersistentServerState
  {
    /** Optional: when set, is returned instead of running the modify. */
    private final ResultCode failure;
    /** Optional: when set, runs inside the write, before the modify. */
    private final Runnable insideWrite;

    HookedWrite(DN baseDN, int serverId, ServerState state, ResultCode failure, Runnable insideWrite)
    {
      super(baseDN, serverId, state);
      this.failure = failure;
      this.insideWrite = insideWrite;
    }

    @Override
    ResultCode runModify(ModifyOperationBasis op)
    {
      if (insideWrite != null)
      {
        insideWrite.run();
      }
      return failure != null ? failure : super.runModify(op);
    }
  }
}
