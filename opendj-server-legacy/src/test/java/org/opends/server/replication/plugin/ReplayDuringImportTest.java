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
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Entry;
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
 * one thing it must not do is stop the session the import is reading (issue #956).
 * <p>
 * The exporter is a broker of this test, so that the test says when the entries arrive: the
 * change is replayed while the import is waiting for them.
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
   * The operation is refused with NO_SUCH_OBJECT - nothing serves the base DN - and the
   * entryUUID search conflict resolution reads the data with can not run either. A failure
   * of the server is what that is, and once the attempts in place are spent the change is
   * given back and the session restarted for it to be delivered again: that restart stops
   * the broker the import is reading, the import ends on the entries which had arrived, and
   * nothing reports it - the stream ends the way a finished one does.
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
