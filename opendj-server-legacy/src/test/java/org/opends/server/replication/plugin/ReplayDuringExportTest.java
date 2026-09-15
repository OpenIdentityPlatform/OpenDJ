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

import static java.nio.charset.StandardCharsets.*;
import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.testng.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.InitializeRcvAckMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the replay of a change while this replica is the source of a total update.
 * <p>
 * The export of a total update publishes its entries over the session of the domain, from a
 * thread of the export pool, while the replay of the domain keeps running. A change which can
 * not be replayed meanwhile is given back for the replication server to send again, and that
 * takes a session restart. Run by the thread which released the change, the restart stops the
 * broker the export publishes over, and {@code exportLDIFEntry()} gives the export up on it: the
 * replica being initialized is left to be initialized again, for a change which would have
 * waited (issue #1048). The restart has to wait for the export instead, and the state
 * checkpointer runs it once the export is over.
 * <p>
 * The importer is a broker of this test, so that the test says when the export moves: the
 * exporter publishes no more than the initialization window ahead of the importer's
 * acknowledgements, and the change is replayed while the export waits for one.
 */
@SuppressWarnings("javadoc")
public class ReplayDuringExportTest extends ReplicationTestCase
{
  /**
   * A total update needs a backend which keeps its data across the export, and one which
   * the exporter can lock: the {@code userRoot} backend, as for the import direction.
   */
  private static final String EXAMPLE_DN = "dc=example,dc=com";
  private static final int RS_ID = 612;
  private static final int DS_ID = 1;
  private static final int IMPORTER_ID = 2;
  /** How many entry messages the exporter publishes ahead of the importer's acknowledgements. */
  private static final int INIT_WINDOW = 2;
  /**
   * An entry message carries a buffer of the export stream rather than one entry, so the data
   * has to outgrow the window by that much before the exporter waits for an acknowledgement.
   */
  private static final int ENTRY_MSG_BYTES = 8192;
  private static final int BULK_ENTRY_BYTES = 4096;
  private static final int BULK_ENTRIES = 2 * (INIT_WINDOW + 2);
  private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);
  /**
   * How long the export is given to release its context once the importer has left the full
   * update status - the exporter waits for that status to go, and releases it then.
   * <p>
   * Short enough that the receive of the stream and this wait fit inside the timeout of the
   * case with room to spare: a case which ends on the timeout prints none of the messages
   * which say what went wrong.
   */
  private static final long EXPORT_END_BOUND_IN_MS = 30_000;
  /**
   * How long the session is given to come back once a restart has been run for a change which
   * was given back: the wait that restart is owed, and the start of the session.
   */
  private static final long SESSION_BACK_BOUND_IN_MS = 30_000;

  private DN baseDN;
  private ReplicationServer replicationServer;
  private LDAPReplicationDomain domain;
  private TestSynchronousReplayQueue queue;
  private ReplicationBroker importer;
  private CSNGenerator gen;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(EXAMPLE_DN);
    TestCaseUtils.clearBackend("userRoot", EXAMPLE_DN);

    final int rsPort = TestCaseUtils.findFreePort();
    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        rsPort, "replayDuringExportTestDb", 0, RS_ID, 0, 100, new TreeSet<String>()));

    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    final DomainFakeCfg conf = new DomainFakeCfg(baseDN, DS_ID, replServers);
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
    queue = new TestSynchronousReplayQueue();
    domain = MultimasterReplication.createNewDomain(conf, queue);
    domain.start();
    assertTrue(domain.isConnected(), "the domain did not connect to the replication server");

    // A short socket timeout: the test bounds its own waits, and receive() returns to it on it.
    importer = openReplicationSession(baseDN, IMPORTER_ID, 100, rsPort, 2000);
    gen = new CSNGenerator(IMPORTER_ID, 0);
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    try
    {
      stop(importer);
      MultimasterReplication.deleteDomain(baseDN);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * A change which can not be replayed while the export streams must leave the session to
   * the export, and be delivered again once the export is over.
   * <p>
   * The attempts in place are spent - the backend is live, an export takes nothing away - and
   * the change is given back and asked for again, as it is when nothing else is going on: what
   * waits is the session restart that takes. The restart stands as a request for as long as
   * the export runs, and the state checkpointer, which holds its own restarts back for the
   * same reason, runs it when the export is over. Without the hold the replay thread stops the
   * broker the exporter publishes over: the export ends on the entries which had been
   * published, with {@code ERR_INIT_RS_DISCONNECTION_DURING_EXPORT}, the rest never reaches
   * the importer, and the importer has to be initialized again.
   */
  @Test(timeOut = 120_000)
  public void aReplayWhichFailsDuringTheExportLeavesTheSessionToTheExport() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());
    final Entry foldedInto = addPersonEntry("folded");
    addEntriesWorthMoreThanTheWindow();
    final long exportedEntries = countEntriesOfTheDomain();

    /*
     * The change goes through the replication server, which is what has it to deliver again
     * once the session has been restarted for it; the replay queue of the domain is the
     * test's, so the change is replayed when the test says, which is during the export.
     */
    final CSN csn = gen.newCSN();
    importer.publish(new ModifyMsg(csn, DN.valueOf("cn=movedAway," + EXAMPLE_DN),
        generatemods("description", "replayed during the export"), entryUUID));
    final LDAPUpdateMsg delivered = awaitDelivery(csn, 30_000, "the change was not delivered");

    startExport();
    final List<EntryMsg> held = receiveEntryMsgsWithoutAcknowledging(INIT_WINDOW);
    assertTrue(domain.ieRunning(), "the export is not being processed");

    // Replayed while the exporter waits for an acknowledgement: every attempt in place ends on
    // an entryUUID search which does not run, and the change is given back.
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replay(delivered);
      assertTrue(ShortCircuitPlugin.getShortCircuitCount(OperationType.SEARCH, "PreParse")
              >= LDAPReplicationDomain.IN_PLACE_REPLAY_ATTEMPTS,
          "every attempt in place must have made its search: the backend is live while the"
              + " export runs, so nothing holds the replay off");
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertThat(errorLogRecordsOf(WARN_REPLAY_RETRYING_CHANGE.ordinal(), csn))
        .as("the change was not asked for again: an export is not a total update into this"
            + " replica, whose state would cover the change once it is loaded")
        .isNotEmpty();

    /*
     * A second change failing during the same export is warned about by the count the next
     * warning carries and not by a line of its own, and the line which says its restart is
     * held is folded with the warning it qualifies: a backend which fails every delivery of
     * a long export would otherwise write one of them per delivery, which is the repetition
     * the throttle is there to fold.
     */
    final CSN folded = failAReplayOf(foldedInto);
    assertThat(errorLogRecordsOf(WARN_REPLAY_RETRYING_CHANGE.ordinal(), folded))
        .as("the warning of the second change was written: the throttle must fold it into the"
            + " count the next warning carries")
        .isEmpty();
    assertThat(errorLogRecordsOf(NOTE_REPLAY_SESSION_RESTART_HELD_BY_TOTAL_UPDATE.ordinal(),
        folded))
        .as("the hold was reported for a change whose warning was folded: the line qualifies"
            + " that warning, so it is folded with it")
        .isEmpty();

    /*
     * The export is held across a tick of the state checkpointer, which comes for every
     * restart left standing once a second: the request is standing now, and whichever thread
     * comes for it while the export runs has to leave it standing.
     */
    Thread.sleep(1500);

    finishExport(held, exportedEntries);

    assertThat(errorLogRecordsOf(NOTE_REPLAY_SESSION_RESTART_HELD_BY_TOTAL_UPDATE.ordinal(), csn))
        .as("the restart the change was asked for again with was not reported as held")
        .isNotEmpty();

    // The change is delivered again once the export is over, and applied.
    final LDAPUpdateMsg again = awaitDelivery(csn, 30_000, "the change was not delivered again"
        + " once the export was over: the session restart it was asked for again with was"
        + " not run");
    replay(again);
    assertThat(DirectoryServer.getEntry(entry.getName()).getAllAttributes("description"))
        .as("the change delivered again after the export was not applied").isNotEmpty();
    assertTrue(domain.getServerState().cover(csn),
        "the change delivered again after the export was applied and not recorded");
    /*
     * Read only now: until the first change was committed the ServerState could cover
     * nothing newer from this server, whatever became of the second one. It was handed to
     * the domain rather than published, so nothing delivers it again, and it holds the state
     * back where it stands.
     */
    assertFalse(domain.getServerState().cover(folded),
        "the second change was recorded as replayed: its replay failed, so it must stay listed"
            + " as one which is not in the data");
  }

  /**
   * The changes a replay which was unwound had parked are given back while the export streams,
   * and the restart they are handed back with waits for the export the way the restart a failed
   * replay asks for does: the line which says so names them, and the export streams to its end.
   * <p>
   * The replay is unwound by the ack of a change it had applied, which is the road the give-back
   * of the parked changes is reached from (issue #954): what that road hands back is reported as
   * given back to a replication server "which still owns it and sends it again", and during an
   * export it does not send it yet. These changes are handed to the domain rather than published,
   * so the redelivery the restart brings is the case above's to assert; what is asserted here is
   * that the export is not cut for them and that their wait is reported.
   * <p>
   * The same road is walked once before the export, which is the negative arm of the line: the
   * restart of that give-back runs, so nothing of it waits and nothing says it does.
   * <p>
   * The fixture is the shape {@code ParkedChangeGiveBackTest} gives that road - a change parked
   * behind one whose operation is refused, and a replay unwound by an ack - over the backend
   * this case exports, and with a total update running over the session.
   */
  @Test(timeOut = 120_000)
  public void theParkedChangesGivenBackDuringTheExportAreReportedAsHeld() throws Exception
  {
    final Entry waitedOn = addPersonEntry("waitedOn");
    final Entry unwoundBeforeTheExport = addPersonEntry("unwoundBefore");
    final Entry unwoundDuringTheExport = addPersonEntry("unwoundDuring");
    final CSN parkedBeforeTheExport = parkAChangeBehindABarrier(waitedOn);

    final long generationBefore = sessionGeneration();
    unwindTheReplayOf(unwoundBeforeTheExport);
    /*
     * Read the moment the replay returns: the restart of this give-back is run by this thread
     * before the replay returns, and one left standing would be run by the state checkpointer
     * within its tick, where awaitConnected() below could not tell the two apart - it would
     * find the session up either way, restarted or never stopped.
     */
    assertThat(sessionGeneration())
        .as("the thread which gave the parked change back did not run the restart it asked"
            + " for: no total update is being processed, so nothing holds it")
        .isGreaterThan(generationBefore);

    assertThat(errorLogRecordsOf(
        NOTE_REPLAY_PARKED_CHANGE_GIVEN_BACK.ordinal(), parkedBeforeTheExport))
        .as("the change parked by the replay which was unwound was not given back")
        .isNotEmpty();
    assertThat(errorLogRecordsOf(
        NOTE_REPLAY_SESSION_RESTART_HELD_BY_TOTAL_UPDATE.ordinal(), parkedBeforeTheExport))
        .as("the restart of the give-back was reported as held while no total update was being"
            + " processed: it ran")
        .isEmpty();
    awaitConnected("the session was not brought back by the restart the give-back ran");

    final CSN parked = parkAChangeBehind(waitedOn);
    addEntriesWorthMoreThanTheWindow();
    final long exportedEntries = countEntriesOfTheDomain();

    startExport();
    final List<EntryMsg> held = receiveEntryMsgsWithoutAcknowledging(INIT_WINDOW);
    assertTrue(domain.ieRunning(), "the export is not being processed");

    unwindTheReplayOf(unwoundDuringTheExport);

    assertThat(errorLogRecordsOf(NOTE_REPLAY_PARKED_CHANGE_GIVEN_BACK.ordinal(), parked))
        .as("the change parked by the replay which was unwound was not given back")
        .isNotEmpty();
    assertThat(errorLogRecordsOf(NOTE_REPLAY_SESSION_RESTART_HELD_BY_TOTAL_UPDATE.ordinal(), parked))
        .as("the restart the parked change was given back with was not reported as held: the"
            + " change waits for the export with nothing said about it")
        .isNotEmpty();

    // The export was not cut by the give-back, and streams to its end.
    finishExport(held, exportedEntries);
  }

  /**
   * Replays a change handed to the domain rather than published, whose every attempt in place
   * ends on an entryUUID search which does not run: it is given back and asked for again, the
   * way the published change of the case above is.
   * <p>
   * The DN it carries is not in the data and its entryUUID is the provided entry's, which is
   * what has the replay look the entry up by that UUID - the search which is short-circuited.
   *
   * @return the CSN of the change whose replay failed
   */
  private CSN failAReplayOf(Entry entry) throws Exception
  {
    final CSN csn = gen.newCSN();
    // The registration counts the searches it refuses from zero, so the count below is this
    // replay's own.
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replayHandedOver(new ModifyMsg(csn, DN.valueOf("cn=alsoMovedAway," + EXAMPLE_DN),
          generatemods("description", "replayed during the export as well"),
          getEntryUUID(entry.getName())));
      // Read before the short circuit is deregistered, which forgets the count with it.
      assertThat(ShortCircuitPlugin.getShortCircuitCount(OperationType.SEARCH, "PreParse"))
          .as("the replay of this change must have failed: every attempt in place makes the"
              + " entryUUID search which does not run")
          .isGreaterThanOrEqualTo(LDAPReplicationDomain.IN_PLACE_REPLAY_ATTEMPTS);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }
    return csn;
  }



  private static Entry addPersonEntry(String cn) throws Exception
  {
    return TestCaseUtils.addEntry(
        "dn: cn=" + cn + "," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: " + cn,
        "sn: " + cn);
  }

  /**
   * Replays, on the thread of this test, a change whose operation is refused - it stays listed
   * as one which is not in the data, and every change on that entry waits for it - and then a
   * change which is parked behind it.
   *
   * @return the CSN of the change which is left parked
   */
  private CSN parkAChangeBehindABarrier(Entry entry) throws Exception
  {
    final CSN failing = gen.newCSN();
    replayHandedOver(new ModifyMsgWhoseOperationRefusesAControl(failing, entry.getName(),
        generatemods("description", "the replay of this change fails"),
        getEntryUUID(entry.getName())));
    assertFalse(domain.getServerState().cover(failing),
        "the change whose replay fails must stay listed as one which is not in the data");
    awaitConnected("the session was not brought back for the change whose replay failed");
    return parkAChangeBehind(entry);
  }

  /**
   * Replays, on the thread of this test, a change on an entry whose barrier is still missing
   * from the data: it is parked as waiting for that one and owned by this thread from then on,
   * since nothing hands a parked change out again while what it waits for is missing
   * (issue #954).
   *
   * @return the CSN of the change which is left parked
   */
  private CSN parkAChangeBehind(Entry entry) throws Exception
  {
    final CSN parked = gen.newCSN();
    replayHandedOver(new ModifyMsg(parked, entry.getName(),
        generatemods("description", "the change which waits for the one which failed"),
        getEntryUUID(entry.getName())));
    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 1,
        "a change which waits for one that is not in the data must be parked");
    return parked;
  }

  /**
   * Reads the generation of the session of the domain, which every stop and start of it bumps,
   * under {@code serviceStateLock}, as {@code getSessionGeneration()} asks.
   */
  private long sessionGeneration() throws Exception
  {
    final Field lockField = ReplicationDomain.class.getDeclaredField("serviceStateLock");
    lockField.setAccessible(true);
    final Method getSessionGeneration =
        ReplicationDomain.class.getDeclaredMethod("getSessionGeneration");
    getSessionGeneration.setAccessible(true);
    synchronized (lockField.get(domain))
    {
      return (Long) getSessionGeneration.invoke(domain);
    }
  }

  /** Waits for the session of the domain to be up, which a restart leaves it. */
  private void awaitConnected(String orElse) throws Exception
  {
    final long deadline = System.currentTimeMillis() + SESSION_BACK_BOUND_IN_MS;
    while (!domain.isConnected())
    {
      assertTrue(System.currentTimeMillis() < deadline, orElse);
      Thread.sleep(50);
    }
  }

  /**
   * Replays, on the thread of this test, a change whose ack runs out of memory once it is
   * applied: the replay is unwound with that change in the data and owned by nobody, so what
   * the give-back on the way out of {@code replay()} has to hand back is what this thread
   * parked.
   */
  private void unwindTheReplayOf(Entry entry) throws Exception
  {
    try
    {
      replayHandedOver(new ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied(gen.newCSN(),
          entry.getName(), generatemods("description", "the replay of this change is unwound"),
          getEntryUUID(entry.getName())));
    }
    catch (OutOfMemoryError unwound)
    {
      // The error is the fixture's own, and this is the thread it would have ended.
      return;
    }
    throw new AssertionError(
        "the replay was not unwound: the ack of the delivery must run out of memory");
  }

  /**
   * Hands a change to the domain rather than publishing it, and replays it on the thread of
   * this test: what the replication server has to deliver again is the published change of the
   * case above, and these are the changes whose give-back this one is about.
   */
  private void replayHandedOver(UpdateMsg msg) throws Exception
  {
    domain.processUpdate(msg);
    replay(queue.take().getUpdateMessage());
  }

  /** Adds entries whose export outgrows the initialization window, so that the exporter waits. */
  private void addEntriesWorthMoreThanTheWindow() throws Exception
  {
    assertThat(BULK_ENTRIES * BULK_ENTRY_BYTES)
        .as("the data must outgrow the window for the exporter to wait for an acknowledgement")
        .isGreaterThan((INIT_WINDOW + 1) * ENTRY_MSG_BYTES);
    final char[] padding = new char[BULK_ENTRY_BYTES];
    Arrays.fill(padding, 'x');
    for (int i = 0; i < BULK_ENTRIES; i++)
    {
      TestCaseUtils.addEntry(
          "dn: cn=bulk" + i + "," + EXAMPLE_DN,
          "objectClass: top",
          "objectClass: person",
          "cn: bulk" + i,
          "sn: bulk" + i,
          "description: " + new String(padding));
    }
  }

  private long countEntriesOfTheDomain() throws Exception
  {
    return getServerContext().getBackendConfigManager().findLocalBackendForEntry(baseDN)
        .getNumberOfEntriesInBaseDN(baseDN);
  }

  /**
   * Has the importer ask this replica for a total update, and returns once the export has
   * begun: the {@code InitializeTargetMsg} which starts it has arrived.
   */
  private void startExport() throws Exception
  {
    // The export is refused while this replica does not see the importer in its topology.
    final long deadline = System.currentTimeMillis() + 30_000;
    while (!domain.getReplicaInfos().containsKey(IMPORTER_ID))
    {
      assertTrue(System.currentTimeMillis() < deadline,
          "the domain did not see the importer in its topology");
      Thread.sleep(20);
    }
    importer.publish(new InitializeRequestMsg(baseDN, IMPORTER_ID, DS_ID, INIT_WINDOW));
    // The exporter waits for the importer to be in the full update status before it streams.
    importer.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);
    final ReplicationMsg msg = receiveTotalUpdateMsg(30_000);
    assertThat(msg).as("the total update did not begin").isInstanceOf(InitializeTargetMsg.class);
  }

  /**
   * Receives entry messages up to the window and acknowledges none of them: the exporter
   * publishes no more than the window ahead of the last acknowledgement, so its next entry
   * message waits for one from now on.
   */
  private List<EntryMsg> receiveEntryMsgsWithoutAcknowledging(int window) throws Exception
  {
    final List<EntryMsg> received = new ArrayList<>();
    while (received.size() < window)
    {
      final ReplicationMsg msg = receiveTotalUpdateMsg(30_000);
      assertThat(msg).as("the export did not stream up to the window").isInstanceOf(EntryMsg.class);
      received.add((EntryMsg) msg);
    }
    return received;
  }

  /**
   * Acknowledges what arrived while the export was held and everything after it as it
   * arrives, up to the {@code DoneMsg}, and checks that every entry of the domain arrived. A
   * total update which was cut streams no further: the rest of its entries never arrives, or
   * an {@code ErrorMsg} arrives in their place, and either fails here. The importer then
   * leaves the full update status, which the exporter waits for before it releases its
   * context - and it leaves it whatever happened, or the export never ends.
   * <p>
   * An export which does not end all the same is printed rather than asserted: it is what
   * the assertions which follow this call wait for - the change the restart brings back once
   * the export is over - and a throw out of this {@code finally} would replace the failure
   * of the stream above it, which is the one worth reading.
   */
  private void finishExport(List<EntryMsg> held, long exportedEntries) throws Exception
  {
    try
    {
      final StringBuilder ldif = new StringBuilder();
      int lastMsgId = 0;
      for (EntryMsg entryMsg : held)
      {
        ldif.append(new String(entryMsg.getEntryBytes(), UTF_8));
        lastMsgId = entryMsg.getMsgId();
      }
      importer.publish(new InitializeRcvAckMsg(IMPORTER_ID, DS_ID, lastMsgId));
      final int heldAt = lastMsgId;
      while (true)
      {
        final ReplicationMsg msg = receiveTotalUpdateMsg(60_000);
        if (msg instanceof DoneMsg)
        {
          break;
        }
        assertThat(msg).as("the export was cut instead of streaming to its end")
            .isInstanceOf(EntryMsg.class);
        final EntryMsg entryMsg = (EntryMsg) msg;
        ldif.append(new String(entryMsg.getEntryBytes(), UTF_8));
        lastMsgId = entryMsg.getMsgId();
        importer.publish(new InitializeRcvAckMsg(IMPORTER_ID, DS_ID, lastMsgId));
      }
      assertThat(lastMsgId).as("the export did not stream past the window it was held at")
          .isGreaterThan(heldAt);
      assertThat(countEntries(ldif)).as("the export did not stream every entry of the domain")
          .isEqualTo(exportedEntries);
    }
    finally
    {
      leaveTheFullUpdateStatus();
      final long deadline = System.currentTimeMillis() + EXPORT_END_BOUND_IN_MS;
      while (domain.ieRunning() && System.currentTimeMillis() < deadline)
      {
        Thread.sleep(50);
      }
      if (domain.ieRunning())
      {
        System.err.println("the export of " + baseDN + " did not end within "
            + EXPORT_END_BOUND_IN_MS + " ms");
      }
    }
  }

  /** Counts the entries of an LDIF stream by the blank line which separates them. */
  private static long countEntries(CharSequence ldif)
  {
    long count = 0;
    for (int i = ldif.length() - 1; i > 0; i--)
    {
      if (ldif.charAt(i) == '\n' && ldif.charAt(i - 1) == '\n')
      {
        count++;
      }
    }
    return count;
  }

  /**
   * The importer reconnects once its import is over - it comes back with the generation ID of
   * the data it loaded, which is the one it was opened with here - and the exporter waits for
   * the importer to leave the full update status before it releases its context.
   */
  private void leaveTheFullUpdateStatus()
  {
    importer.reStart(true);
  }

  /**
   * Receives the next message of the total update on the importer: the updates of this
   * replica's own and the topology are not it.
   */
  private ReplicationMsg receiveTotalUpdateMsg(long timeoutMs) throws Exception
  {
    final long deadline = System.currentTimeMillis() + timeoutMs;
    final List<ReplicationMsg> others = new ArrayList<>();
    while (System.currentTimeMillis() < deadline)
    {
      final ReplicationMsg msg;
      try
      {
        msg = importer.receive();
      }
      catch (SocketTimeoutException e)
      {
        continue;
      }
      if (msg instanceof InitializeTargetMsg || msg instanceof EntryMsg
          || msg instanceof DoneMsg)
      {
        return msg;
      }
      if (msg instanceof ErrorMsg)
      {
        Assert.fail("the total update was given up: " + ((ErrorMsg) msg).getDetails());
      }
      others.add(msg);
    }
    Assert.fail("nothing of the total update arrived within " + timeoutMs + " ms; received "
        + others);
    return null;
  }

  /**
   * Waits for the replication server to deliver the change to this replica: the listener
   * thread of the domain puts it in the replay queue of the test, which takes it out.
   */
  private LDAPUpdateMsg awaitDelivery(CSN csn, long timeoutMs, String orElse) throws Exception
  {
    final long deadline = System.currentTimeMillis() + timeoutMs;
    while (queue.peek() == null)
    {
      assertTrue(System.currentTimeMillis() < deadline, orElse + " within " + timeoutMs + " ms");
      Thread.sleep(50);
    }
    final LDAPUpdateMsg msg = queue.take().getUpdateMessage();
    assertEquals(msg.getCSN(), csn, "another change than the one published was delivered");
    return msg;
  }

  /** The records of the error log which carry the provided message id and the provided CSN. */
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

  private void replay(LDAPUpdateMsg ldapUpdate)
  {
    domain.markInProgress(ldapUpdate);
    domain.replay(ldapUpdate, SHUTDOWN);
  }
}
