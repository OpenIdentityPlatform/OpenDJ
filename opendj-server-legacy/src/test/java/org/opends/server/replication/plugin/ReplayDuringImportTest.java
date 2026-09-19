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
import java.util.concurrent.CountDownLatch;
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
import org.opends.server.replication.protocol.DeleteMsg;
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
import org.testng.Assert;
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
 * A restart asked for while the total update owns the session is left standing for the
 * length of it, and is not run once it is over: the change it was asked for is gone with the
 * ServerState the import replaced. The changes a replay which is unwound had parked as
 * waiting for another one are released on the same terms (issue #954). A total update which
 * is asked for and never begins - the request is refused, or gives up waiting for its answer
 * - replaces nothing, and the request left standing under it is what has the changes
 * released under it delivered again (issue #1061).
 * <p>
 * The exporter is a broker of this test, so that the test says when the entries arrive: the
 * change is replayed while the import is waiting for them - or, for the request, while the
 * exporter is holding the answer, which it may never give. A change which has to be delivered
 * again is published through the replication server, which is what has it to send again;
 * the replay queue of the domain is the test's, so every delivery is replayed when, and on
 * the thread, the test says.
 * <p>
 * The {@code timeOut} each case declares is what it is expected to take at the most; it is
 * not what bounds it. {@code TestListener} sets the timeout of every test method from the
 * {@code org.opends.test.timeout} property, ten minutes under Maven and none outside it.
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
   * The changes a replay which is unwound had parked as waiting for another change are
   * released while a total update owns the session, and the session is left to the owner
   * (issue #954): the restart asked for them is not run - it is refused where it runs, and
   * the request would be spent on it - and they are neither reported as changes the
   * replication server sends again, which it does not before the total update has let go
   * of the session, nor counted as processed. That is the road a change a stopping replay
   * thread abandons takes on this domain, and the give-back of the parked changes takes it
   * too.
   * <p>
   * Pinned on the import road because it is the one road with an owner which a test holds
   * open for as long as it needs: the request is on its way until the exporter answers it,
   * and the backend is live meanwhile, so the change which is parked and the replay which
   * is unwound run as they would on any domain. The domain going away, or being disabled,
   * forgets its pending changes a moment after it takes the session and clears every
   * request and every count on its way, so a give-back on that road is a race with the
   * forgetting and leaves nothing to read.
   * <p>
   * The replay is unwound on the thread of this test - it applied its change, and the ack
   * of its delivery runs out of memory - so the parked change is this thread's to give
   * back, and the error which ends a replay thread is caught here instead.
   */
  @Test(timeOut = 120_000)
  public void aParkedChangeGivenBackWhileTheRequestIsOnItsWayLeavesTheSessionToTheOwner() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());
    final String[] exported = exportedEntries();

    // The request is out, and the exporter holds it until the give-back below has run.
    domain.initializeFromRemote(EXPORTER_ID, null);
    assertNotNull(waitForSpecificMsg(exporter, InitializeRequestMsg.class));

    /*
     * The barrier: a change whose replay fails stays listed and uncommitted - the attempts
     * in place end on an entryUUID search which does not run, the way they do in the case
     * above - and stays among the changes the newer ones are checked against, so a change
     * which follows it on the same entry has to wait for it. The restart which would have
     * followed is refused, the total update owning the session, and the search is let
     * through again before anything below reads a monitor.
     */
    final DN movedAway = DN.valueOf("cn=movedAway," + EXAMPLE_DN);
    final CSN failing = gen.newCSN();
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replayMsg(new ModifyMsg(failing, movedAway,
          generatemods("description", "the replay of this change fails"), entryUUID));
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }
    assertFalse(domain.getServerState().cover(failing),
        "the change whose replay fails must stay listed as one which is not in the data");

    // Parked as waiting for it by this thread, which owns it from here on.
    final CSN parked = gen.newCSN();
    replayMsg(new ModifyMsg(parked, movedAway,
        generatemods("description", "the change which was parked as a dependency"), entryUUID));
    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 1,
        "a change which waits for one that is not in the data must be parked");

    /*
     * The replay which is unwound while this thread still holds the parked change: its own
     * change is applied and committed, so the give-back on the way out finds the parked
     * change alone. The count is read once the parked change is listed, since a parked
     * change publishes no ack and is not counted until the delivery which replays it is.
     */
    final long processed = getMonitorAttrValue(baseDN, "replayed-updates");
    final CSN unwound = gen.newCSN();
    try
    {
      replayMsg(new ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied(unwound, entry.getName(),
          generatemods("description", "the replay of this change is unwound once it is applied"),
          entryUUID));
      Assert.fail("the replay was not unwound: the ack of the delivery must run out of memory");
    }
    catch (OutOfMemoryError unwinding)
    {
      // The error is the fixture's own, and this is the thread it would have ended.
    }

    assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 0,
        "the change parked by the replay which was unwound must be given back");
    assertEquals(getMonitorAttrValue(baseDN, "replayed-updates"), processed,
        "a change released while a total update owns the session must not be counted as"
            + " processed: no session sends it again before the total update lets go of it");
    assertThat(errorLogRecordsOf(NOTE_REPLAY_PARKED_CHANGE_GIVEN_BACK.ordinal(), parked))
        .as("the change was reported as one the replication server sends again, which it does"
            + " not before the total update lets go of the session")
        .isEmpty();
    assertTrue(domain.isConnected(), "the session the answer to the request arrives over was stopped");

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
   * A change released while a total update which never begins owns the session is asked for
   * again under the owner, and delivered again over the session the state checkpointer
   * restarts once the owner is gone (issue #1061).
   * <p>
   * A total update this replica asked for owns the session from the request on, and a change
   * whose replay fails meanwhile is released and left to the owner: the domain forgets its
   * pending changes on its way down, and the import forgets them at its end - but a request
   * which is refused, or which gives up waiting for its answer, replaces nothing and forgets
   * nothing. Released and asked for by nobody, the change would stay listed and uncommitted
   * until the next failed replay of this domain restarted the session, and the ServerState -
   * which a commit moves no further than the oldest uncommitted change - would stop at it
   * with everything behind it. So the restart is asked for under the owner as well, and the
   * state checkpointer, which holds every request for as long as the total update owns the
   * session, runs it within its tick of the owner letting go.
   * <p>
   * The road pinned here is the one a change whose attempts in place are spent takes: every
   * attempt ends on an entryUUID search which does not run. The request gives up through
   * the watchdog of the initialize task, which is the one road out of an unanswered request
   * a test can take at a time of its choosing.
   */
  @Test(timeOut = 120_000)
  public void aChangeReleasedUnderARequestWhichIsNeverAnsweredIsDeliveredAgain() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());

    // The request is out, and the exporter never answers it.
    domain.initializeFromRemote(EXPORTER_ID, null);
    assertNotNull(waitForSpecificMsg(exporter, InitializeRequestMsg.class));

    final CSN csn = gen.newCSN();
    final LDAPUpdateMsg delivered = publishAndAwaitDelivery(new ModifyMsg(csn,
        DN.valueOf("cn=movedAway," + EXAMPLE_DN),
        generatemods("description", "released while the request was on its way"), entryUUID));
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replay(delivered);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }
    assertFalse(domain.getServerState().cover(csn),
        "the change whose replay fails must stay listed as one which is not in the data");
    assertThat(errorLogRecordsOf(WARN_REPLAY_RETRYING_CHANGE.ordinal(), csn))
        .as("the change was warned about as one the replication server sends again, which it"
            + " does not while the total update owns the session")
        .isEmpty();
    assertTrue(domain.isConnected(),
        "the session the answer to the request would arrive over was stopped under the owner");

    giveUpTheRequest();

    final LDAPUpdateMsg again = awaitDelivery(csn, 30_000, "the change released under the"
        + " request was not delivered again once the request gave up: nothing asked for the"
        + " session restart which has the replication server send it again");
    replay(again);
    assertThat(DirectoryServer.getEntry(entry.getName()).getAllAttributes("description"))
        .as("the change delivered again was not applied").isNotEmpty();
    assertTrue(domain.getServerState().cover(csn),
        "the change delivered again was applied and not recorded: it is still listed");
  }

  /**
   * A change a stopping replay thread abandons while a total update which never begins owns
   * the session takes the same road (issue #1061): abandoned at the top of its first attempt
   * without being counted against its budget, released, asked for again under the owner,
   * and delivered again once the owner is gone.
   */
  @Test(timeOut = 120_000)
  public void aChangeAbandonedUnderARequestWhichIsNeverAnsweredIsDeliveredAgain() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());

    domain.initializeFromRemote(EXPORTER_ID, null);
    assertNotNull(waitForSpecificMsg(exporter, InitializeRequestMsg.class));

    final CSN csn = gen.newCSN();
    final LDAPUpdateMsg delivered = publishAndAwaitDelivery(new ModifyMsg(csn, entry.getName(),
        generatemods("description", "abandoned while the request was on its way"), entryUUID));
    // The thread of this test is one which is stopping: the change is abandoned unapplied.
    domain.markInProgress(delivered);
    domain.replay(delivered, new AtomicBoolean(true));
    assertThat(DirectoryServer.getEntry(entry.getName()).getAllAttributes("description"))
        .as("a change abandoned by a stopping thread was applied").isEmpty();
    assertThat(errorLogRecordsOf(NOTE_REPLAY_ABANDONED_CHANGE.ordinal(), csn))
        .as("the change was reported as one the replication server sends again, which it"
            + " does not while the total update owns the session")
        .isEmpty();
    assertTrue(domain.isConnected(),
        "the session the answer to the request would arrive over was stopped under the owner");

    giveUpTheRequest();

    final LDAPUpdateMsg again = awaitDelivery(csn, 30_000, "the change abandoned under the"
        + " request was not delivered again once the request gave up: nothing asked for the"
        + " session restart which has the replication server send it again");
    replay(again);
    assertTrue(domain.getServerState().cover(csn),
        "the change delivered again was applied and not recorded: it is still listed");
  }

  /**
   * A change the give-back released while a total update which never begins owns the session
   * is asked for again under the owner by the give-back itself, and the request is left
   * standing rather than spent (issue #1061).
   * <p>
   * The change it waited for is one another thread is replaying, held before its operation
   * is built: nothing has failed, so no road but the give-back has asked for anything, and
   * the request found standing once the owner is gone is the give-back's own. The parked
   * road of {@code replay()} runs what is requested when the session has no owner; run under
   * the owner, the restart would be refused where it runs and the request spent on the
   * refusal, with nothing left for the state checkpointer to run - so the same case pins
   * that the run is held back under the owner the give-back asked under.
   * <p>
   * The replay which is unwound is this thread's, as in the case above: its change is
   * applied, and the ack of its delivery runs out of memory.
   */
  @Test(timeOut = 120_000)
  public void aParkedChangeGivenBackUnderARequestWhichIsNeverAnsweredIsDeliveredAgain() throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: cn=renamedSince," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: renamedSince",
        "sn: renamedSince");
    final String entryUUID = getEntryUUID(entry.getName());
    final Entry other = TestCaseUtils.addEntry(
        "dn: cn=unwound," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: unwound",
        "sn: unwound");
    final String otherUUID = getEntryUUID(other.getName());

    domain.initializeFromRemote(EXPORTER_ID, null);
    assertNotNull(waitForSpecificMsg(exporter, InitializeRequestMsg.class));

    /*
     * The change the parked one waits for: replayed by a thread of the test which is held
     * before the operation is built, so the change is being replayed - listed, uncommitted,
     * owned - for as long as the latch holds, and nothing has failed.
     */
    final CountDownLatch letGo = new CountDownLatch(1);
    final CSN held = gen.newCSN();
    domain.processUpdate(new ModifyMsgWhoseOperationWaitsToBeBuilt(held, entry.getName(),
        generatemods("description", "the change being replayed by another thread"), entryUUID,
        letGo));
    final LDAPUpdateMsg heldMsg = queue.take().getUpdateMessage();
    final Thread otherThread = new Thread(() ->
    {
      assertTrue(domain.markInProgress(heldMsg), "the held change must be the one listed");
      domain.replay(heldMsg, SHUTDOWN);
    }, "ReplayDuringImportTest replay held before its operation is built");
    otherThread.start();
    try
    {
      // Parked as waiting for the held change by this thread, which owns it from here on.
      final CSN parked = gen.newCSN();
      final LDAPUpdateMsg parkedDelivery = publishAndAwaitDelivery(new ModifyMsg(parked,
          entry.getName(),
          generatemods("description", "the change which was parked as a dependency"), entryUUID));
      replay(parkedDelivery);
      assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 1,
          "a change which waits for one being replayed by another thread must be parked");

      // The replay which is unwound while this thread still holds the parked change.
      final CSN unwound = gen.newCSN();
      try
      {
        replayMsg(new ModifyMsgWhoseAckRunsOutOfMemoryOnceApplied(unwound, other.getName(),
            generatemods("description", "the replay of this change is unwound once it is applied"),
            otherUUID));
        Assert.fail("the replay was not unwound: the ack of the delivery must run out of memory");
      }
      catch (OutOfMemoryError unwinding)
      {
        // The error is the fixture's own, and this is the thread it would have ended.
      }
      assertEquals(getMonitorAttrValue(baseDN, "dependent-changes-size"), 0,
          "the change parked by the replay which was unwound must be given back");
      assertTrue(domain.isConnected(),
          "the session the answer to the request would arrive over was stopped under the owner");

      // The held change runs to its end: nothing fails, nothing asks for a restart.
      letGo.countDown();
      otherThread.join(30_000);
      assertFalse(otherThread.isAlive(), "the held replay did not end once let go");
      assertTrue(domain.getServerState().cover(held), "the held change was not recorded");
      assertFalse(domain.getServerState().cover(parked),
          "the parked change must stay listed as one which is not in the data");

      giveUpTheRequest();

      final LDAPUpdateMsg again = awaitDelivery(parked, 30_000, "the parked change given back"
          + " under the request was not delivered again once the request gave up: the session"
          + " restart the give-back asked for under the owner was not run, or was spent");
      replay(again);
      assertTrue(domain.getServerState().cover(parked),
          "the parked change delivered again was applied and not recorded: it is still listed");
    }
    finally
    {
      letGo.countDown();
      otherThread.join(30_000);
    }
  }

  /**
   * A session restart which stood while the import ran was asked for by a replay thread
   * for a change it gave back - before the total update owned the session, or under the
   * owner - and that change is forgotten with the pending changes when the imported data
   * replaces the ServerState: the session started back at the end of the import asks for
   * everything the imported state does not cover. Run, the request would stop that session
   * once for a delivery which can not come. The request is made here by hand, in the place
   * of the one a failed replay makes.
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
   * A total update forgets the deliveries which were folded into no warning, along with
   * the changes they were deliveries of (issue #942).
   * <p>
   * The changes listed as pending do not outlive the ServerState the import replaces, and
   * the recovery from a failed replay goes with them - the session restart backoff, and the
   * count the next warning about a change being asked for again says it stands for. The
   * first warning over the imported data must not count the deliveries of a change which
   * is not listed anymore.
   * <p>
   * Nothing sends a change of this test again - the exporter never had it - so the count
   * is fed by two changes failing within one interval rather than by one change delivered
   * twice: the first is warned about, the second is folded into no warning. The changes
   * are deletes: a short circuit on the modifies would be tripped by the ServerState being
   * saved to the base entry and by the import disabling the backend it replaces.
   */
  @Test(timeOut = 120_000)
  public void aWarningAfterTheImportDoesNotCountTheDeliveriesBefore() throws Exception
  {
    final Entry warnedAbout = TestCaseUtils.addEntry(
        "dn: cn=warnedAbout," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: warnedAbout",
        "sn: warnedAbout");
    final Entry folded = TestCaseUtils.addEntry(
        "dn: cn=folded," + EXAMPLE_DN,
        "objectClass: top",
        "objectClass: person",
        "cn: folded",
        "sn: folded");
    final String warnedAboutUUID = getEntryUUID(warnedAbout.getName());
    final String foldedUUID = getEntryUUID(folded.getName());
    final String[] exported = exportedEntries();

    ShortCircuitPlugin.registerShortCircuit(
        OperationType.DELETE, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replayMsg(new DeleteMsg(warnedAbout.getName(), gen.newCSN(), warnedAboutUUID));
      replayMsg(new DeleteMsg(folded.getName(), gen.newCSN(), foldedUUID));

      startImportInto(exported.length);
      finishImport(exported);

      /*
       * Only the timestamp of the throttle is put back, so that the failure over the
       * imported data is warned about straight away: the count is the domain's to keep or
       * to forget.
       */
      domain.resetReplayRetryWarningThrottle();
      final CSN csn = gen.newCSN();
      replayMsg(new DeleteMsg(DN.valueOf(IMPORTED_ENTRY_DN), csn, IMPORTED_ENTRY_UUID));
      final List<String> warnings = errorLogRecordsOf(WARN_REPLAY_RETRYING_CHANGE.ordinal(), csn);
      assertThat(warnings).as("the change which fails over the imported data must be warned about")
          .isNotEmpty();
      assertThat(warnings.get(0))
          .as("the first warning after the import must not count the deliveries before it")
          .contains(" 0 further deliveries");
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
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
    replay(queue.take().getUpdateMessage());
  }

  /** Replays a delivery on the thread of this test, as a replay thread would. */
  private void replay(LDAPUpdateMsg delivery)
  {
    assertTrue(domain.markInProgress(delivery), "the delivery is not the one listed: " + delivery);
    domain.replay(delivery, SHUTDOWN);
  }

  /**
   * Publishes a change through the replication server, which is what has it to deliver again
   * once the session is restarted for it, and waits for the delivery to this replica.
   */
  private LDAPUpdateMsg publishAndAwaitDelivery(LDAPUpdateMsg msg) throws Exception
  {
    exporter.publish(msg);
    return awaitDelivery(msg.getCSN(), 30_000, "the change published was not delivered");
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
    assertEquals(msg.getCSN(), csn, "another change than the one awaited was delivered");
    return msg;
  }

  /**
   * Has the total update this replica asked for give up on its request, the way the
   * watchdog of the initialize task does once the request has waited two minutes for an
   * answer: the total update never begins, and its context is released with nothing
   * replaced, so the session has no owner anymore.
   */
  private void giveUpTheRequest()
  {
    assertTrue(domain.abortStalledInitializeFromRemote(0),
        "the request was not the one waiting for an answer");
    assertFalse(domain.ieRunning(), "the total update was given up and is still being processed");
  }
}
