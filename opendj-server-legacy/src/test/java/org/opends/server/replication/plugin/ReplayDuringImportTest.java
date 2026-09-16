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

import java.util.ArrayList;
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
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the replay of a change while this replica is the target of a total update.
 * <p>
 * The import of a total update streams over the session of the domain, on its listener
 * thread, and the backend it replaces is deregistered for the length of it. A change which
 * was queued for replay before the {@code InitializeTargetMsg} arrived is replayed into no
 * backend: whatever such a replay decides is about to be overwritten by the import, and the
 * one thing it must not do is stop the session the import is reading (issue #956). The same
 * holds from the moment the total update is asked for: the answer to the request arrives
 * over that session, so a replay which fails while it is on its way must not restart it.
 * A restart asked for before the total update took the session, and left standing for the
 * length of it, is not run once it is over either: the change it was asked for is gone with
 * the ServerState the import replaced.
 * <p>
 * The exporter is a broker of this test, so that the test says when the entries arrive: the
 * change is replayed while the import is waiting for them - or, for the request, while the
 * exporter is holding the answer.
 */
@SuppressWarnings("javadoc")
public class ReplayDuringImportTest extends ReplicationTestCase
{
  /**
   * The memory backend of {@code o=test} loses its data when it is disabled and enabled
   * back, which is what an import does to the backend it replaces: a total update needs a
   * backend which keeps what was imported into it.
   */
  private static final String EXAMPLE_DN = "dc=example,dc=com";
  private static final int RS_ID = 611;
  private static final int DS_ID = 1;
  private static final int EXPORTER_ID = 2;
  private static final int INIT_WINDOW = 100;
  private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);
  /** An entry of the exporter's data, and its entryUUID. */
  private static final String IMPORTED_ENTRY_DN = "cn=imported,ou=People," + EXAMPLE_DN;
  private static final String IMPORTED_ENTRY_UUID = "21111111-1111-1111-1111-111111111113";

  private DN baseDN;
  private ReplicationServer replicationServer;
  private LDAPReplicationDomain domain;
  private TestSynchronousReplayQueue queue;
  private ReplicationBroker exporter;
  private CSNGenerator gen;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(EXAMPLE_DN);
    TestCaseUtils.clearBackend("userRoot", EXAMPLE_DN);

    final int rsPort = TestCaseUtils.findFreePort();
    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        rsPort, "replayDuringImportTestDb", 0, RS_ID, 0, 100, new TreeSet<String>()));

    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    final DomainFakeCfg conf = new DomainFakeCfg(baseDN, DS_ID, replServers);
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
    queue = new TestSynchronousReplayQueue();
    domain = MultimasterReplication.createNewDomain(conf, queue);
    domain.start();
    assertTrue(domain.isConnected(), "the domain did not connect to the replication server");

    exporter = openReplicationSession(baseDN, EXPORTER_ID, 100, rsPort, 10000);
    gen = new CSNGenerator(201, 0);
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    try
    {
      stop(exporter);
      MultimasterReplication.deleteDomain(baseDN);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * A change replayed while the import streams must leave the session to the import.
   * <p>
   * The change is given back at the top of its first attempt: the data it would be applied
   * to is being replaced, so nothing is attempted into the backend the import took away,
   * nothing is reported, and the session is left to the import - which streams every entry
   * to its end. Without the hold-off the operation is refused with NO_SUCH_OBJECT - nothing
   * serves the base DN - and the entryUUID search conflict resolution reads the data with
   * can not run either: the attempts in place are spent into no backend and the exit
   * reports the change; without the owner the total update is, the session is then
   * restarted for the change to be delivered again, which stops the broker the import is
   * reading, and the import ends on the entries which had arrived with nothing to say it.
   */
  @Test(timeOut = 120_000)
  public void aReplayDuringTheImportLeavesTheSessionToTheImport() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());
    final String[] exported = exportedEntries();
    startImportInto(exported.length);

    // Queued before the InitializeTargetMsg arrived, replayed into no backend.
    final CSN csn = gen.newCSN();
    replayMsg(new ModifyMsg(csn, DN.valueOf("cn=movedAway," + EXAMPLE_DN),
        generatemods("description", "replayed during the import"), entryUUID));

    finishImport(exported);

    for (String ldif : exported)
    {
      final DN dn = dnOf(ldif);
      assertTrue(entryExists(dn), "the import ended before " + dn
          + " arrived: the session it streams over was stopped from under it");
    }
    /*
     * The two roads which leave the session to the import are told apart here: the
     * hold-off gives the change back before an attempt is made, the guard on the restart
     * after the attempts are spent. The exhaustion exit is the one thing the first road
     * leaves no record of.
     */
    assertThat(errorLogRecordsOf(ERR_ERROR_REPLAYING_OPERATION.ordinal(), csn))
        .as("the change was attempted into no backend instead of being given back at once")
        .isEmpty();
    assertThat(errorLogRecordsOf(WARN_REPLAY_RETRYING_CHANGE.ordinal(), csn))
        .as("the change was asked for again, which restarts the session the import streams over")
        .isEmpty();
  }

  /**
   * A change given back while the import ran must not hold the ServerState back once the
   * import has replaced the data.
   * <p>
   * A change which is given back stays listed as pending and uncommitted - that is what
   * has the replication server send it again - and a commit advances the ServerState no
   * further than the oldest uncommitted change. The state the import loads is the
   * exporter's, which covers the change already, so nothing sends it again: left listed,
   * it would stop the ServerState of this replica for good.
   */
  @Test(timeOut = 120_000)
  public void aChangeGivenBackDuringTheImportDoesNotHoldTheServerStateBack() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());
    final String[] exported = exportedEntries();
    startImportInto(exported.length);
    replayMsg(new ModifyMsg(gen.newCSN(), DN.valueOf("cn=movedAway," + EXAMPLE_DN),
        generatemods("description", "replayed during the import"), entryUUID));
    finishImport(exported);

    // A change on an entry the import brought, replayed once the import is over.
    final DN importedDN = DN.valueOf(IMPORTED_ENTRY_DN);
    final CSN csn = gen.newCSN();
    replayMsg(new ModifyMsg(csn, importedDN,
        generatemods("description", "replayed after the import"), IMPORTED_ENTRY_UUID));

    assertThat(DirectoryServer.getEntry(importedDN).getAllAttributes("description"))
        .as("a change replayed after the import was not applied").isNotEmpty();
    assertTrue(domain.getServerState().cover(csn), "a change applied after the import was not"
        + " recorded: the change given back during the import is still listed and holds the"
        + " ServerState back");
  }

  /**
   * A total update this replica asked for owns the session from the request, not from the
   * first entry: the {@code InitializeTargetMsg} which answers the request arrives over
   * that session, and a restart made while the answer is on its way loses it.
   * <p>
   * The backend is live for the length of the request - nothing has been taken away yet -
   * so the change is attempted, every attempt ends on an entryUUID search which does not
   * run, and the exhaustion exit reports it: what is refused is the restart which would
   * have followed, and the retry warning which goes with it. The exporter then answers the
   * request, and its entries stream to their end over the session which was left alone.
   */
  @Test(timeOut = 120_000)
  public void aRequestOnItsWayOwnsTheSessionTheAnswerArrivesOver() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());
    final String[] exported = exportedEntries();

    // The request is out, and the exporter holds it until the change below has been replayed.
    domain.initializeFromRemote(EXPORTER_ID, null);
    assertNotNull(waitForSpecificMsg(exporter, InitializeRequestMsg.class));

    final CSN csn = gen.newCSN();
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replayMsg(new ModifyMsg(csn, DN.valueOf("cn=movedAway," + EXAMPLE_DN),
          generatemods("description", "replayed while the request was on its way"), entryUUID));
      assertTrue(ShortCircuitPlugin.getShortCircuitCount(OperationType.SEARCH, "PreParse")
              >= LDAPReplicationDomain.IN_PLACE_REPLAY_ATTEMPTS,
          "every attempt in place must have made its search: the backend is live while the"
              + " request is on its way, so nothing holds the replay off");
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertThat(errorLogRecordsOf(ERR_ERROR_REPLAYING_OPERATION.ordinal(), csn))
        .as("the attempts in place were spent, which the exhaustion exit reports").isNotEmpty();
    assertThat(errorLogRecordsOf(WARN_REPLAY_RETRYING_CHANGE.ordinal(), csn))
        .as("the change was asked for again, which restarts the session the answer to the"
            + " request arrives over")
        .isEmpty();
    assertTrue(domain.isConnected(), "the session the request was made over was stopped");

    answerImportRequest(exported.length);
    finishImport(exported);
    for (String ldif : exported)
    {
      final DN dn = dnOf(ldif);
      assertTrue(entryExists(dn), "the import ended before " + dn
          + " arrived: the answer to the request was lost with the session it was made over");
    }
  }

  /**
   * A session restart which stood while the import ran was asked for by a replay thread
   * for a change given back before the total update owned the session, and that change is
   * forgotten with the pending changes when the imported data replaces the ServerState:
   * the session started back at the end of the import asks for everything the imported
   * state does not cover. Run, the request would stop that session once for a delivery
   * which can not come. The request is made here by hand, in the place of one made
   * between a replay thread's read of the owner and the import claiming the session.
   * <p>
   * The restart is the state checkpointer's to run, within its first tick after the total
   * update has released the session, so the pin is that the failure it would meet is never
   * spent: a restart which ran would have spent it, and would have left the session it
   * stopped down.
   */
  @Test(timeOut = 120_000)
  public void aRequestWhichStoodWhileTheImportRanIsNotRunOnceItIsOver() throws Exception
  {
    final String[] exported = exportedEntries();
    startImportInto(exported.length);
    domain.requestSessionRestart();
    domain.failNextSessionRestarts(1);
    try
    {
      finishImport(exported);

      // Two ticks of the checkpointer: a request standing when the import ends is run on the first.
      Thread.sleep(2000);
      assertEquals(domain.getSessionRestartFailuresLeft(), 1, "the request which stood while"
          + " the import ran was run against the session started back at its end");
      assertTrue(domain.isConnected(), "the session started back at the end of the import"
          + " was stopped for a request made before it");
    }
    finally
    {
      domain.failNextSessionRestarts(0);
    }
  }

  /**
   * Has the exporter start a total update into this replica, and returns once the backend
   * of the domain is deregistered for it: from then on the import is reading the session,
   * and a change replayed here is replayed into no backend.
   */
  private void startImportInto(int entryCount) throws Exception
  {
    exporter.publish(new InitializeTargetMsg(
        baseDN, EXPORTER_ID, DS_ID, EXPORTER_ID, entryCount, INIT_WINDOW));
    final long deadline = System.currentTimeMillis() + 30_000;
    while (getServerContext().getBackendConfigManager().findLocalBackendForEntry(baseDN) != null)
    {
      assertTrue(System.currentTimeMillis() < deadline,
          "the import did not deregister the backend of the domain");
      Thread.sleep(20);
    }
  }

  /**
   * Has the exporter answer the total update this replica asked for: the requestor of the
   * {@code InitializeTargetMsg} is this replica, so the import runs in the context the
   * request acquired.
   */
  private void answerImportRequest(int entryCount) throws Exception
  {
    exporter.publish(new InitializeTargetMsg(
        baseDN, EXPORTER_ID, DS_ID, DS_ID, entryCount, INIT_WINDOW));
  }

  /** Has the exporter send the entries of the total update, and waits for the import to end. */
  private void finishImport(String... ldifEntries) throws Exception
  {
    int msgId = 0;
    for (String ldif : ldifEntries)
    {
      exporter.publish(new EntryMsg(EXPORTER_ID, DS_ID, ldif.getBytes(UTF_8), ++msgId));
    }
    exporter.publish(new DoneMsg(EXPORTER_ID, DS_ID));
    final long deadline = System.currentTimeMillis() + 60_000;
    while (domain.ieRunning())
    {
      assertTrue(System.currentTimeMillis() < deadline, "the import did not end");
      Thread.sleep(50);
    }
  }

  /** The data of the exporter: the base entry and two entries below it. */
  private static String[] exportedEntries()
  {
    return new String[] {
      "dn: " + EXAMPLE_DN + "\n"
          + "objectClass: top\n"
          + "objectClass: domain\n"
          + "dc: example\n"
          + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
          + "\n",
      "dn: ou=People," + EXAMPLE_DN + "\n"
          + "objectClass: top\n"
          + "objectClass: organizationalUnit\n"
          + "ou: People\n"
          + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
          + "\n",
      "dn: " + IMPORTED_ENTRY_DN + "\n"
          + "objectClass: top\n"
          + "objectClass: person\n"
          + "cn: imported\n"
          + "sn: imported\n"
          + "entryUUID: " + IMPORTED_ENTRY_UUID + "\n"
          + "\n",
    };
  }

  private static DN dnOf(String ldif)
  {
    return DN.valueOf(ldif.substring("dn: ".length(), ldif.indexOf('\n')));
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

  private void replayMsg(UpdateMsg updateMsg) throws InterruptedException
  {
    domain.processUpdate(updateMsg);
    final LDAPUpdateMsg ldapUpdate = queue.take().getUpdateMessage();
    domain.markInProgress(ldapUpdate);
    domain.replay(ldapUpdate, SHUTDOWN);
  }
}
