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
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
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
      final long deadline = System.currentTimeMillis() + 60_000;
      while (domain.ieRunning())
      {
        assertTrue(System.currentTimeMillis() < deadline, "the export did not end");
        Thread.sleep(50);
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
