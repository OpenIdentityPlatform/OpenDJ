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
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests who runs the session restart asked for the changes a replay which is unwound had
 * parked as waiting for another change (issue #954).
 * <p>
 * The thread which gave them back runs it itself, so that the delivery they wait for is
 * not left to the next tick of the state checkpointer - unless that thread is stopping, in
 * which case it leaves the request standing the way a stopping thread leaves the request it
 * makes for the change it abandons: the threads of the pool are stopped one after the
 * other and joined, and each running a restart on its way out would have the configuration
 * change which is stopping them wait for one restart per thread.
 * <p>
 * The replay runs on the thread of the test, which is what says whether that thread is
 * stopping, and the error which unwinds it is caught here rather than ending a replay
 * thread. The thread which is stopping is stopped by the ack of the change it applied, and
 * the case asserts that the change was applied: stopped before the replay, it abandons the
 * change unapplied at the top of its first attempt, and the abandon road asks for a restart
 * of its own next to the give-back's, which would then be pinned by nothing - that road is
 * the third case's, which pins the abandon arm of the catch. The restart is asked to fail
 * once, so that the thread which ran it is the one which met the failure: a replay thread
 * carries the failure out with the error it is ending on, the state checkpointer reports
 * it. The restart is asked for at once, without the backoff, and the count of the restarts
 * in a row says so: only the restart run again after the failure waits its backoff out.
 */
@SuppressWarnings("javadoc")
public class ParkedChangeGiveBackTest extends ReplicationTestCase
{
  private static final int RS_ID = 612;
  private static final int DS_ID = 1;
  private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

  /**
   * How long the state checkpointer is given to run a request left standing and report
   * the failure it was asked to meet: its next tick, at most a second away, and the
   * report.
   */
  private static final long CHECKPOINTER_BOUND_IN_MS = 5000;
  /**
   * How long the session is given to come back once a restart failed: the backoff a
   * restart which follows a failed one is owed - the second in a row here, two seconds -
   * and the tick of the state checkpointer which runs it.
   */
  private static final long RESTART_BOUND_IN_MS = 10000;

  private DN baseDN;
  private ReplicationServer replicationServer;
  private LDAPReplicationDomain domain;
  private TestSynchronousReplayQueue queue;
  private CSNGenerator gen;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);

    final int rsPort = TestCaseUtils.findFreePort();
    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        rsPort, "parkedChangeGiveBackTestDb", 0, RS_ID, 0, 100, new TreeSet<String>()));

    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    final DomainFakeCfg conf = new DomainFakeCfg(baseDN, DS_ID, replServers);
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
    queue = new TestSynchronousReplayQueue();
    domain = MultimasterReplication.createNewDomain(conf, queue);
    domain.start();
    assertTrue(domain.isConnected(), "the domain did not connect to the replication server");
    gen = new CSNGenerator(201, 0);
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    try
    {
      domain.failNextSessionRestarts(0);
      MultimasterReplication.deleteDomain(baseDN);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * The thread which gave a parked change back runs the restart it asked for, and meets
   * the failure that restart was asked to meet: the failure comes out with the error which
   * unwound the replay, as one it suppressed, and the restart which is run again after it
   * brings the session back before the replay returns. The restart it asked for is run at
   * once: the one run again after the failure is the one which waits its backoff out, and
   * that is the one move of the count of the restarts in a row.
   */
  @Test(timeOut = 120_000)
  public void theThreadWhichGaveBackAParkedChangeRunsTheRestartItAskedFor() throws Exception
  {
    parkAChangeBehindABarrier(addEntry("waitedOn"));
    final Entry other = addEntry("other");
    final int restartsBefore = domain.getConsecutiveSessionRestarts();
    domain.failNextSessionRestarts(1);

    final OutOfMemoryError unwinding = unwindAReplay(other, Stopped.NEVER);

    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 0,
        "the change parked by the replay which was unwound must be given back");
    assertThat(injectedRestartFailuresAmong(unwinding.getSuppressed()))
        .as("the thread which gave the parked change back must have run the restart it"
            + " asked for, and met the failure that restart was asked to meet")
        .hasSize(1);
    assertEquals(domain.getSessionRestartFailuresLeft(), 0,
        "the restart which was asked to fail never ran");
    awaitConnected(RESTART_BOUND_IN_MS, "the restart run again after the one which failed did"
        + " not bring the session back");
    assertEquals(domain.getConsecutiveSessionRestarts(), restartsBefore + 1,
        "the restart the give-back asked for must be run at once, without the backoff: the one"
            + " run again after the failure is the one which waits it out");
  }

  /**
   * A thread which is stopping leaves the restart it asked for standing, and the state
   * checkpointer runs it: the failure that restart was asked to meet is the checkpointer's
   * to report, and nothing of it comes out with the error which unwound the replay.
   * <p>
   * The thread is stopped by the ack of the change it applied, so the change is committed
   * and owned by nobody by the time the replay is unwound, and the request the give-back
   * makes for the parked change is the one request which stands for the checkpointer. The
   * case asserts that road: a thread stopped before the replay hands its change back on a
   * road of its own, which asks for a restart next to the give-back's and would stand in
   * for it.
   */
  @Test(timeOut = 120_000)
  public void aStoppingThreadLeavesTheRestartItAskedForToTheStateCheckpointer() throws Exception
  {
    parkAChangeBehindABarrier(addEntry("waitedOn"));
    final Entry other = addEntry("other");
    final int reportedBefore = restartFailureReports().size();
    final long appliedBefore = getMonitorAttrValue(baseDN, "replayed-updates-ok");
    final int restartsBefore = domain.getConsecutiveSessionRestarts();
    domain.failNextSessionRestarts(1);

    final OutOfMemoryError unwinding = unwindAReplay(other, Stopped.BY_THE_ACK);

    assertEquals(getMonitorAttrValue(baseDN, "replayed-updates-ok"), appliedBefore + 1,
        "the change must be applied and its replay unwound by the ack, not abandoned unapplied");
    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 0,
        "the change parked by the replay which was unwound must be given back");
    assertThat(injectedRestartFailuresAmong(unwinding.getSuppressed()))
        .as("a thread which is stopping must not run the restart it asked for")
        .isEmpty();

    final long deadline = System.currentTimeMillis() + CHECKPOINTER_BOUND_IN_MS;
    while (restartFailureReports().size() == reportedBefore)
    {
      assertTrue(System.currentTimeMillis() < deadline, "the state checkpointer did not run"
          + " the restart the stopping thread left standing: the failure that restart was"
          + " asked to meet was never reported");
      Thread.sleep(50);
    }
    awaitConnected(RESTART_BOUND_IN_MS,
        "the session was not brought back after the restart which failed");
    assertEquals(domain.getConsecutiveSessionRestarts(), restartsBefore + 1,
        "the restart the give-back asked for must be run at once, without the backoff: the one"
            + " run again after the failure is the one which waits it out");
  }

  /**
   * A thread stopped before its first attempt hands back the change it did not apply, the
   * way a stopping thread abandons a replay: the ack of an abandoned change is published
   * all the same, and it is what runs out of memory here, so the abandon road taken is the
   * one of the catch - the change is still this thread's when the replay is unwound.
   */
  @Test(timeOut = 120_000)
  public void aStoppingThreadHandsBackTheChangeItDidNotApply() throws Exception
  {
    parkAChangeBehindABarrier(addEntry("waitedOn"));
    final Entry other = addEntry("other");
    final long appliedBefore = getMonitorAttrValue(baseDN, "replayed-updates-ok");
    final CSN abandoned = gen.newCSN();

    unwindAReplay(other, abandoned, Stopped.BEFORE_THE_REPLAY);

    assertEquals(getMonitorAttrValue(baseDN, "replayed-updates-ok"), appliedBefore,
        "a thread stopped before the replay must not apply the change");
    assertThat(errorLogRecordsOf(NOTE_REPLAY_ABANDONED_CHANGE.ordinal(), abandoned))
        .as("a stopping thread must hand back the change it did not apply")
        .isNotEmpty();
    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 0,
        "the change parked by the thread which is stopping must be given back");
  }

  private void awaitConnected(long boundInMs, String message) throws Exception
  {
    final long deadline = System.currentTimeMillis() + boundInMs;
    while (!domain.isConnected())
    {
      assertTrue(System.currentTimeMillis() < deadline, message);
      Thread.sleep(50);
    }
  }

  private Entry addEntry(String cn) throws Exception
  {
    return TestCaseUtils.addEntry(
        "dn: cn=" + cn + "," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: person",
        "cn: " + cn,
        "sn: " + cn);
  }

  /**
   * Replays, on the thread of this test, a change whose replay fails and stays listed - its
   * operation is built and then refused, so it is asked for again rather than stepped over -
   * and then a change on the same entry, which is parked as waiting for it and owned by this
   * thread from then on. The restart the failed change asks for is run on this thread as
   * well, so the session is back once this returns.
   */
  private void parkAChangeBehindABarrier(Entry entry) throws Exception
  {
    final String entryUUID = getEntryUUID(entry.getName());
    final CSN failing = gen.newCSN();
    replayMsg(new ModifyMsgWhoseOperationRefusesAControl(failing, entry.getName(),
        generatemods("description", "the replay of this change fails"), entryUUID), RUNNING);
    assertFalse(domain.getServerState().cover(failing),
        "the change whose replay fails must stay listed as one which is not in the data");
    awaitConnected(RESTART_BOUND_IN_MS,
        "the session was not brought back for the change whose replay failed");

    replayMsg(new ModifyMsg(gen.newCSN(), entry.getName(),
        generatemods("description", "the change which was parked as a dependency"), entryUUID),
        RUNNING);
    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 1,
        "a change which waits for one that is not in the data must be parked");
  }

  /** When the thread a replay runs on is stopped, if it is. */
  private enum Stopped
  {
    /** It is running throughout. */
    NEVER,
    /** By the ack of the change it applied: the change is committed, and the replay is unwound. */
    BY_THE_ACK,
    /**
     * Before the replay: the change is abandoned unapplied at the top of its first attempt,
     * and the ack which says so is what runs out of memory.
     */
    BEFORE_THE_REPLAY
  }

  private OutOfMemoryError unwindAReplay(Entry entry, Stopped stopped) throws Exception
  {
    return unwindAReplay(entry, gen.newCSN(), stopped);
  }

  /**
   * Replays, on the thread of this test, a change whose ack runs out of memory, and returns
   * the error the replay was unwound on.
   *
   * @param stopped when the thread the replay runs on is stopped: running until the ack,
   *          the change is applied rather than abandoned at the top of its first attempt
   */
  private OutOfMemoryError unwindAReplay(Entry entry, CSN csn, Stopped stopped) throws Exception
  {
    // Fresh for every replay: the ack is what sets it, and nothing puts it back.
    final AtomicBoolean stopping = new AtomicBoolean(stopped == Stopped.BEFORE_THE_REPLAY);
    try
    {
      replayMsg(new ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied(csn, entry.getName(),
          generatemods("description", "the replay of this change is unwound by its ack"),
          getEntryUUID(entry.getName()), stopped == Stopped.BY_THE_ACK ? stopping : null), stopping);
    }
    catch (OutOfMemoryError unwinding)
    {
      // The error is the fixture's own, and this is the thread it would have ended.
      return unwinding;
    }
    throw new AssertionError("the replay was not unwound: the ack of the delivery must run out of memory");
  }

  /** The throwables among the provided ones which a restart threw because a test asked it to. */
  private static List<Throwable> injectedRestartFailuresAmong(Throwable[] suppressed)
  {
    final List<Throwable> injected = new ArrayList<>();
    for (Throwable t : suppressed)
    {
      if (t instanceof IllegalStateException && t.getMessage().contains("as a test asked"))
      {
        injected.add(t);
      }
    }
    return injected;
  }

  /** The records of the error log which report a session restart of this domain that threw. */
  private List<String> restartFailureReports()
  {
    final List<String> records = new ArrayList<>();
    for (String record : TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
    {
      if (record.contains("msgID=" + ERR_REPLAY_SESSION_RESTART_FAILED.ordinal())
          && record.contains(baseDN.toString()))
      {
        records.add(record);
      }
    }
    return records;
  }

  /** The records of the error log which carry the provided message for the provided change. */
  private static List<String> errorLogRecordsOf(int msgId, CSN csn)
  {
    final List<String> records = new ArrayList<>();
    for (String record : TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
    {
      if (record.contains("msgID=" + msgId) && record.contains(csn.toString()))
      {
        records.add(record);
      }
    }
    return records;
  }

  private void replayMsg(UpdateMsg updateMsg, AtomicBoolean stopping) throws InterruptedException
  {
    domain.processUpdate(updateMsg);
    final LDAPUpdateMsg ldapUpdate = queue.take().getUpdateMessage();
    domain.markInProgress(ldapUpdate);
    domain.replay(ldapUpdate, stopping);
  }
}
