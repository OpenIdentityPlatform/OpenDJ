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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Test the naming conflict resolution code. */
@SuppressWarnings("javadoc")
public class NamingConflictTest extends ReplicationTestCase
{
  private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

  /** The monitor attributes which count the naming conflicts a domain solved, and did not. */
  private static final String RESOLVED_NAMING_CONFLICTS = "resolved-naming-conflicts";
  private static final String UNRESOLVED_NAMING_CONFLICTS = "unresolved-naming-conflicts";

  private DN baseDN;
  private LDAPReplicationDomain domain;
  private CSNGenerator gen;

  private TestSynchronousReplayQueue queue;

  /**
   * The result code to put back in {@code ds-cfg-server-error-result-code}, or
   * {@code null} when this test did not change it.
   * <p>
   * The setting is server-wide, so it is put back by {@link #tearDown()} rather than by
   * the test which changed it: a method the harness kills on its timeout, or interrupts
   * inside a replay, would otherwise leave every later method of this class replaying
   * against a result code it never asked for.
   */
  private Integer serverErrorResultCodeToRestore;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(TEST_ROOT_DN_STRING);

    TestCaseUtils.initializeTestBackend(true);

    queue = new TestSynchronousReplayQueue();

    final DomainFakeCfg conf = new DomainFakeCfg(baseDN, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
    domain = MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    gen = new CSNGenerator(201, 0);
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    try
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
    finally
    {
      if (serverErrorResultCodeToRestore != null)
      {
        final int resultCode = serverErrorResultCodeToRestore;
        serverErrorResultCodeToRestore = null;
        setServerErrorResultCode(resultCode);
      }
    }
  }

  /**
   * Test for issue 3402 : test, that a modrdn that is older than an other
   * modrdn but that is applied later is ignored.
   *
   * In this test, the local server act both as an LDAP server and
   * a replicationServer that are inter-connected.
   *
   * The test creates an other session to the replicationServer using
   * directly the ReplicationBroker API.
   * It then uses this session to simulate conflicts and therefore
   * test the naming conflict resolution code.
   */
  @Test
  public void simultaneousModrdnConflict() throws Exception
  {
    String parentUUID = getEntryUUID(baseDN);

    Entry entry = createAndAddEntry("simultaneousModrdnConflict");
    String entryUUID = getEntryUUID(entry.getName());

    // generate two consecutive CSN that will be used in backward order
    CSN csn1 = gen.newCSN();
    CSN csn2 = gen.newCSN();

    replayMsg(modDnMsg(entry, entryUUID, parentUUID, csn2, "uid=simultaneous2"));

    // This MODIFY DN uses an older DN and should therefore be cancelled at replay time.
    replayMsg(modDnMsg(entry, entryUUID, parentUUID, csn1, "uid=simulatneouswrong"));

    // Expect the conflict resolution
    assertFalse(entryExists(entry.getName()), "The modDN conflict was not resolved as expected.");
  }

  private ModifyDNMsg modDnMsg(Entry entry, String entryUUID, String parentUUID, CSN csn, String newRDN)
      throws Exception
  {
    return new ModifyDNMsg(entry.getName(), csn, entryUUID, parentUUID, false, TEST_ROOT_DN_STRING, newRDN);
  }

  /**
   * Test case for [Issue 910]: a naming conflict which
   * {@code solveNamingConflict(ModifyDNOperation)} solves must still be solved while
   * {@code ds-cfg-server-error-result-code} is set to one of the result codes conflict
   * resolution owns.
   * <p>
   * That setting is a plain integer which is not validated as a result code, so it can be
   * one of them. Here it is {@code UNWILLING_TO_PERFORM}, which is what a ModifyDN whose
   * new superior is - on this replica - a subordinate of the entry being moved comes back
   * with, and only conflict resolution can turn such a change into an operation which
   * applies: it resolves both DNs again from the entryUUIDs the message carries. Reading
   * the code as a failure of the server would take the change away from it - the message
   * would never be rewritten, so no attempt would apply any better than the first - and
   * the change would be retried in place, delivered again and finally given up on, with
   * the entry left where it was.
   * <p>
   * {@code UpdateOperationTest.changeConflictResolutionCanNotSolveOnTheServerErrorCodeIsRetried}
   * covers the other half: a change which fails with that same code and which conflict
   * resolution can not solve is retried as the failure of the server it is.
   */
  @Test
  public void modifyDnConflictIsSolvedWhileTheServerErrorCodeIsOneOfTheConflictCodes() throws Exception
  {
    final Entry entry = createAndAddEntry("modDnOnConflictingServerErrorCode");
    final String entryUUID = getEntryUUID(entry.getName());

    final Entry newParent = TestCaseUtils.addEntry(
        "dn: ou=newParent," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: newParent");
    final String newParentUUID = getEntryUUID(newParent.getName());

    // Remembered for tearDown() rather than restored here: the code which is in force,
    // which is the defined default unless the suite configured another one, and never a
    // value hardcoded by this test.
    serverErrorResultCodeToRestore =
        getServerContext().getCoreConfigManager().getServerErrorResultCode().intValue();
    setServerErrorResultCode(ResultCode.UNWILLING_TO_PERFORM.intValue());

    /*
     * The new superior as the master knew it: a DN which, here, is a subordinate of the
     * entry being moved - as it would be after that parent was renamed on this replica.
     * The operation reports UNWILLING_TO_PERFORM for as long as the message carries that
     * DN - ERR_MODDN_NEW_SUPERIOR_IN_SUBTREE, and the memory backend answers the same on
     * a new superior which is not in it - so the change is applied only if conflict
     * resolution gets to rewrite the message with the DNs the entryUUIDs resolve to here.
     */
    final String staleNewSuperior = "ou=newParent," + entry.getName();
    final CSN csn = gen.newCSN();
    replayMsg(new ModifyDNMsg(entry.getName(), csn, entryUUID, newParentUUID, false,
        staleNewSuperior, entry.getName().rdn().toString()));

    final DN resolvedDN = newParent.getName().child(entry.getName().rdn());
    assertTrue(entryExists(resolvedDN),
        "the naming conflict was not solved: no entry at " + resolvedDN);
    assertFalse(entryExists(entry.getName()), "the entry was not moved by the replayed ModifyDN");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
  }

  /**
   * Test case for [Issue 955]: a ModifyDN whose entry and whose new superior are both
   * gone from this replica is a conflict between a delete and this ModifyDN, and it is
   * solved as such rather than left to be delivered again until this replica gives up on
   * it.
   * <p>
   * Neither entryUUID the message carries resolves to a DN here, so conflict resolution
   * has no new superior to move the entry under - and no entry to move either. The entry
   * having been deleted settles what the ModifyDN was trying to do, which is what makes
   * the change resolved: an entry which is not in the database can not be marked as
   * conflicting, and marking it is what used to be attempted first, on the DN of an entry
   * which is not there.
   */
  @Test
  public void modifyDnOnAnEntryAndANewSuperiorWhichAreBothGone() throws Exception
  {
    final Entry entry = createAndAddEntry("modDnOnEntryAndNewSuperiorBothGone");
    final String entryUUID = getEntryUUID(entry.getName());

    final Entry newSuperior = TestCaseUtils.addEntry(
        "dn: ou=newSuperiorBothGone," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: newSuperiorBothGone");
    final String newSuperiorUUID = getEntryUUID(newSuperior.getName());

    // Both entries are deleted on this replica while the ModifyDN is on its way.
    TestCaseUtils.deleteEntry(newSuperior.getName());
    TestCaseUtils.deleteEntry(entry.getName());

    final CSN csn = gen.newCSN();
    replayMsg(new ModifyDNMsg(entry.getName(), csn, entryUUID, newSuperiorUUID, false,
        newSuperior.getName().toString(), entry.getName().rdn().toString()));

    assertFalse(entryExists(entry.getName()),
        "the deleted entry was brought back by the replayed ModifyDN");
    assertTrue(domain.getServerState().cover(csn),
        "a ModifyDN which the delete of its entry has settled must be recorded as replayed");
  }

  /**
   * Test case for [Issue 956]: conflict resolution reads the data with a search of the
   * entryUUID, and a search which did not run is no evidence about the data - the entry
   * it did not report is not an entry which was deleted.
   * <p>
   * The change replayed here was made on the master under a DN this replica does not
   * have: the entry lives under another one, the way it does after a rename which was
   * replayed first, and only the entryUUID search finds it. So the change is applied
   * only if that search is given another chance once the storage serves it again.
   * Reading its failure as "the entry has been deleted" answers NOTHING_TO_DO, which
   * records the change as replayed and loses it for good - the replication server never
   * sends a change this replica reports itself past.
   */
  @Test
  public void modifyIsRetriedWhileTheEntryUUIDSearchCanNotRun() throws Exception
  {
    final Entry entry = createAndAddEntry("modifyWhoseSearchCanNotRun");
    final String entryUUID = getEntryUUID(entry.getName());
    final String phoneNumber = "01 02 45";

    // The DN the change carries is the one the entry had on the master. Here the entry
    // is the one which was just added, which only the entryUUID search finds.
    final DN staleDN = DN.valueOf("cn=movedAway," + TEST_ROOT_DN_STRING);
    final CSN csn = gen.newCSN();

    /*
     * The storage does not serve the search for the first attempts and serves it after
     * them: a failure which lasts less than the attempts made in place, the way a
     * backend which is being rebuilt or a connection which was lost does. The short
     * circuit is put in force right before the replay and dropped right after it - it
     * applies to every search of this server while it is registered, and the replay
     * here runs on this thread.
     */
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 2);
    try
    {
      replayMsg(new ModifyMsg(csn, staleDN, generatemods("telephonenumber", phoneNumber), entryUUID));
      assertShortCircuitSpentBy(2);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    final Entry replayedEntry = DirectoryServer.getEntry(entry.getName());
    assertEquals(replayedEntry.parseAttribute("telephonenumber").asString(), phoneNumber,
        "the change was not applied: a search which could not run was read as a deleted entry");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
  }

  /**
   * Test case for [Issue 956]: the entryUUID searches which check a replayed Add for a
   * conflict read the data the same way, and a search which did not run is no evidence
   * about it either. The first of them checks whether the Add was replayed here already.
   * <p>
   * The Add is delivered a second time - the replication server sends again what a
   * replica does not report itself past - and the entry was renamed here since it was
   * added: only the entryUUID search finds it. An entry that search did not report is
   * not an entry which is not there: reading it that way adds the entry a second time,
   * under its former DN, and the data holds one entryUUID twice.
   */
  @Test
  public void addIsNotReplayedTwiceWhileTheEntryUUIDSearchCanNotRun() throws Exception
  {
    final Entry entry = createAndAddEntry("addWhoseSearchCanNotRun");
    final String entryUUID = getEntryUUID(entry.getName());
    final RDN renamedRDN = RDN.valueOf("cn=renamedAfterTheAdd");
    final ModifyDNOperation rename =
        getRootConnection().processModifyDN(entry.getName(), renamedRDN, true);
    assertEquals(rename.getResultCode(), ResultCode.SUCCESS);
    final CSN csn = gen.newCSN();

    // The first attempt fails its first search; the second attempt has it served.
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 1);
    try
    {
      replayMsg(addMsg(entry, csn, getEntryUUID(baseDN), entryUUID));
      assertShortCircuitSpentBy(1);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertFalse(entryExists(entry.getName()),
        "the entry was added a second time: a search which could not run was read as an "
            + "Add which was not replayed here yet");
    assertTrue(entryExists(baseDN.child(renamedRDN)), "the renamed entry is gone");
    assertTrue(domain.getServerState().cover(csn),
        "a change which is in the data must be recorded as replayed");
  }

  /**
   * Test case for [Issue 956]: the search which checks that the parent of a replayed
   * Add is still the one the change was made under fails, and the one before it - the
   * check that the Add was not replayed here already - ran.
   * <p>
   * A parent that search did not report is not a parent which was deleted: the Add is
   * attempted again once the storage serves the search, and no naming conflict is
   * counted for a search which read nothing. Reading it as a parent which is gone hands
   * the Add to conflict resolution as the naming conflict it is not - and renames the
   * entry under the base DN as a conflicting entry, a divergence which is left for an
   * administrator to repair by hand, when the search conflict resolution makes fails as
   * well.
   */
  @Test
  public void addIsRetriedWhileTheParentEntryUUIDSearchCanNotRun() throws Exception
  {
    final Entry parent = addParentEntry("addWhoseParentSearchCanNotRun");
    final String parentUUID = getEntryUUID(parent.getName());
    final Entry child = makeChildEntry("addedWhileTheParentSearchFailed", parent.getName());
    final CSN csn = gen.newCSN();
    final long resolvedConflicts = getMonitorAttrValue(baseDN, RESOLVED_NAMING_CONFLICTS);
    final long unresolvedConflicts = getMonitorAttrValue(baseDN, UNRESOLVED_NAMING_CONFLICTS);

    /*
     * The first search of the first attempt - the check for an Add replayed already - is
     * let through, the parent check right after it fails, and every search after that
     * one is served. A parent search read as a parent which is gone hands the Add to
     * conflict resolution, whose own search finds the parent where it was and rewrites
     * the message to the DN the Add already carries: the entry lands where it should
     * either way, and what tells the two apart is the naming conflict counted for a
     * search which read nothing.
     */
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 1, 1);
    try
    {
      replayMsg(addMsg(child, csn, parentUUID, "1c3c2c4d-2b5e-4b9f-8a7c-3d5e6f7a8b9c"));
      assertShortCircuitSpentBy(2);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertTrue(entryExists(child.getName()),
        "the entry was not added under its parent: a parent search which could not run "
            + "was read as a parent which is gone");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
    assertEquals(getMonitorAttrValue(baseDN, RESOLVED_NAMING_CONFLICTS), resolvedConflicts,
        "a search which did not run is not a naming conflict which was resolved");
    assertEquals(getMonitorAttrValue(baseDN, UNRESOLVED_NAMING_CONFLICTS), unresolvedConflicts,
        "a search which did not run is not a naming conflict which could not be resolved");
  }

  /**
   * Test case for [Issue 956]: the search conflict resolution reads the data with once a
   * replayed Add failed on a genuine conflict fails, and the checks before the Add ran.
   * <p>
   * The parent of the entry was renamed here, so the Add carries a DN which is not
   * where the parent is anymore: a conflict which is solved by adding the entry under
   * the parent's current DN, once the search which finds that DN runs. A search which
   * did not run is not a parent which is gone, and the conflict is counted once, when
   * it is solved - not for the search which read nothing.
   */
  @Test
  public void addIsRetriedWhileTheConflictResolutionSearchCanNotRun() throws Exception
  {
    final Entry parent = addParentEntry("addWhoseConflictSearchCanNotRun");
    final String parentUUID = getEntryUUID(parent.getName());
    final Entry child = makeChildEntry("addedWhileTheConflictSearchFailed", parent.getName());
    final CSN csn = gen.newCSN();

    final RDN renamedParentRDN = RDN.valueOf("ou=renamedBeforeTheAdd");
    final ModifyDNOperation renameParent =
        getRootConnection().processModifyDN(parent.getName(), renamedParentRDN, true);
    assertEquals(renameParent.getResultCode(), ResultCode.SUCCESS);
    final DN expectedDN = baseDN.child(renamedParentRDN).child(child.getName().rdn());
    final long resolvedConflicts = getMonitorAttrValue(baseDN, RESOLVED_NAMING_CONFLICTS);
    final long unresolvedConflicts = getMonitorAttrValue(baseDN, UNRESOLVED_NAMING_CONFLICTS);

    /*
     * The two searches which check the Add before it runs are let through - they find
     * the parent under its new DN, which is what fails the Add on a conflict - and the
     * search conflict resolution then reads the data with is the one which fails. The
     * next attempt has every search served.
     */
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 2, 1);
    try
    {
      replayMsg(addMsg(child, csn, parentUUID, "2d4d3d5e-3c6f-4ca0-9b8d-4e6f7a8b9cad"));
      assertShortCircuitSpentBy(3);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertTrue(entryExists(expectedDN),
        "the entry was not added under the current DN of its parent: a search which could "
            + "not run was read as a parent which is gone");
    assertFalse(entryExists(DN.valueOf(child.getName().rdn() + "," + TEST_ROOT_DN_STRING)),
        "the entry was renamed under the base DN as a conflicting entry");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
    assertEquals(getMonitorAttrValue(baseDN, RESOLVED_NAMING_CONFLICTS), resolvedConflicts + 1,
        "the renamed parent is one naming conflict, solved once the search ran");
    assertEquals(getMonitorAttrValue(baseDN, UNRESOLVED_NAMING_CONFLICTS), unresolvedConflicts,
        "a search which did not run is not a naming conflict which could not be resolved");
  }

  /**
   * Test case for [Issue 956]: a replayed Delete reads the data with the same search
   * once it failed on a conflict, and rides on the same retry.
   * <p>
   * The entry was renamed here, so the Delete carries a DN which is not the entry's
   * anymore, and only the entryUUID search finds it. Reading the search's failure as an
   * entry which was deleted already answers NOTHING_TO_DO: the entry stays, and the
   * change is recorded as replayed.
   */
  @Test
  public void deleteIsRetriedWhileTheEntryUUIDSearchCanNotRun() throws Exception
  {
    final Entry entry = createAndAddEntry("deleteWhoseSearchCanNotRun");
    final String entryUUID = getEntryUUID(entry.getName());
    final DN staleDN = DN.valueOf("cn=movedAway," + TEST_ROOT_DN_STRING);
    final CSN csn = gen.newCSN();

    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 2);
    try
    {
      replayMsg(new DeleteMsg(staleDN, csn, entryUUID));
      assertShortCircuitSpentBy(2);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertFalse(entryExists(entry.getName()),
        "the entry was not deleted: a search which could not run was read as an entry "
            + "which was deleted already");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
  }

  /**
   * Test case for [Issue 956]: a replayed Modify DN reads the data with the same search
   * once it failed on a conflict, and rides on the same retry.
   * <p>
   * {@code solveNamingConflict(ModifyDNOperation)} declares {@code throws Exception}
   * rather than the search failure alone, so this is the one path where the failure
   * reaches the replay loop through a declaration which does not name it.
   */
  @Test
  public void modifyDnIsRetriedWhileTheEntryUUIDSearchCanNotRun() throws Exception
  {
    final Entry entry = createAndAddEntry("modifyDnWhoseSearchCanNotRun");
    final String entryUUID = getEntryUUID(entry.getName());
    final DN staleDN = DN.valueOf("cn=movedAway," + TEST_ROOT_DN_STRING);
    final RDN newRDN = RDN.valueOf("cn=renamedWhileTheSearchFailed");
    final CSN csn = gen.newCSN();

    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 2);
    try
    {
      replayMsg(new ModifyDNMsg(staleDN, csn, entryUUID, null, false, null, newRDN.toString()));
      assertShortCircuitSpentBy(2);
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertTrue(entryExists(baseDN.child(newRDN)),
        "the entry was not renamed: a search which could not run was read as an entry "
            + "which is not in the data anymore");
    assertFalse(entryExists(entry.getName()), "the entry kept its former DN");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
  }

  /**
   * Test case for [Issue 956], the half which closes [Issue 889] for this path: a
   * change whose entryUUID search never runs is left out of the ServerState once the
   * attempts in place are spent, so that the replication server sends it again.
   * <p>
   * The result code of every attempt is the conflict the operation failed on, which the
   * exhaustion exit does not read as a failure of the server: without the attempt itself
   * telling that its search did not run, the exit reads the attempts as conflict
   * resolution rewriting an operation which keeps failing, and skips the change - the
   * CSN is committed, and a change which is not in the data is never asked for again.
   */
  @Test
  public void modifyIsLeftOutOfTheServerStateWhenTheEntryUUIDSearchNeverRuns() throws Exception
  {
    final Entry entry = createAndAddEntry("modifyWhoseSearchNeverRuns");
    final String entryUUID = getEntryUUID(entry.getName());
    final DN staleDN = DN.valueOf("cn=movedAway," + TEST_ROOT_DN_STRING);
    final CSN csn = gen.newCSN();

    // No number of times: every attempt in place fails its search.
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue());
    try
    {
      replayMsg(new ModifyMsg(csn, staleDN, generatemods("telephonenumber", "01 02 45"), entryUUID));
      assertTrue(ShortCircuitPlugin.getShortCircuitCount(OperationType.SEARCH, "PreParse")
              >= LDAPReplicationDomain.IN_PLACE_REPLAY_ATTEMPTS,
          "every attempt in place must have made its search");
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertFalse(domain.getServerState().cover(csn),
        "a change whose search never ran is not in the data and must not advance the ServerState");
    assertThat(DirectoryServer.getEntry(entry.getName()).getAllAttributes("telephonenumber"))
        .as("the change was applied to the entry the searches never found").isEmpty();
  }

  /**
   * Test case for [Issue 956]: the base entry of the domain replayed into a replica
   * which has none.
   * <p>
   * Two empty replicas share the generation ID of an empty backend, so no initialization
   * is needed and the first change replayed is the base entry itself. The searches which
   * check that Add for a conflict run under the base DN, which the backend serves and
   * has no entry for: they answer NO_SUCH_OBJECT, which is the answer of a backend which
   * is offline as well. Here the search ran and nothing is below a base entry which is
   * not there, so the Add must go through rather than be retried until the give-up
   * budget skips it as a change this replica can not apply.
   */
  @Test
  public void baseEntryIsAddedToAnEmptyReplica() throws Exception
  {
    // The backend, without its base entry.
    TestCaseUtils.initializeTestBackend(false);
    final Entry base = TestCaseUtils.makeEntry(
        "dn: " + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: organization",
        "o: test");
    final CSN csn = gen.newCSN();

    replayMsg(addMsg(base, csn, null, "7c1a0d2e-4b6f-4c8a-9e1d-3f5b7a9c1e2d"));

    assertTrue(entryExists(base.getName()),
        "the base entry of an empty replica must land: its searches found nothing, they did not fail");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
  }

  /**
   * Test case for [Issue 956]: the entryUUID a change carries is looked up as a value,
   * not read as a filter.
   * <p>
   * The entryUUID comes off the wire and nothing validates it as one. Built into a
   * filter string, a value which does not parse as a filter is a search which never
   * runs - a permanent condition retried as a transient one, for as long as the change
   * is asked for, until the give-up budget skips it and raises an alert. Looked up as a
   * value, such an entryUUID names no entry, which is what a search which ran and found
   * nothing says: the change is resolved as one on an entry which is not in the data.
   */
  @Test
  public void anEntryUUIDWhichIsNotOneNamesNoEntry() throws Exception
  {
    final Entry entry = createAndAddEntry("modifyWhoseEntryUUIDIsNotOne");
    // A value no filter string parses: the backslash escapes nothing.
    final String entryUUID = getEntryUUID(entry.getName()) + "\\";
    final DN staleDN = DN.valueOf("cn=movedAway," + TEST_ROOT_DN_STRING);
    final CSN csn = gen.newCSN();

    replayMsg(new ModifyMsg(csn, staleDN, generatemods("telephonenumber", "01 02 45"), entryUUID));

    assertTrue(domain.getServerState().cover(csn),
        "a change on an entry which is not in the data is a conflict which is resolved, and recorded");
    assertThat(DirectoryServer.getEntry(entry.getName()).getAllAttributes("telephonenumber"))
        .as("a change on an entryUUID which is not one was applied to an entry").isEmpty();
  }

  /**
   * Asserts that the searches a short circuit was registered over were made - the ones
   * let through before it and the ones it applied to - and that the search after them
   * was made as well.
   * <p>
   * The count includes the searches let through once the short circuit was spent, so a
   * count past what it was registered over says that the budget was used and that the
   * search after it ran. Read before the short circuit is deregistered, which drops the
   * count with it.
   *
   * @param searchesRegisteredOver the searches let through before the short circuit plus
   *                               the ones it applied to
   */
  private void assertShortCircuitSpentBy(int searchesRegisteredOver)
  {
    assertTrue(
        ShortCircuitPlugin.getShortCircuitCount(OperationType.SEARCH, "PreParse") > searchesRegisteredOver,
        "the short circuit must have been spent by the attempts in place");
  }

  private Entry addParentEntry(String ou) throws Exception
  {
    return TestCaseUtils.addEntry(
        "dn: ou=" + ou + "," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: " + ou);
  }

  private Entry makeChildEntry(String cn, DN parentDN) throws Exception
  {
    return TestCaseUtils.makeEntry(
        "dn: cn=" + cn + "," + parentDN,
        "objectClass: top",
        "objectClass: person",
        "cn: " + cn,
        "sn: Amar");
  }

  /**
   * Test that when a previous conflict is resolved because
   * a delete operation has removed one of the conflicting entries
   * the other conflicting entry is correctly renamed to its original name.
   */
  @Test
  public void conflictCleaningDelete() throws Exception
  {
    Entry entry = createAndAddEntry("conflictCleaningDelete");

    // Add the first entry
    String parentUUID = getEntryUUID(baseDN);

    CSN csn1 = gen.newCSN();

    // Now try to add the same entry with same DN but a different
    // unique ID though the replication
    replayMsg(addMsg(entry, csn1, parentUUID, "c9cb8c3c-615a-4122-865d-50323aaaed48"));

    // Now delete the first entry that was added at the beginning
    TestCaseUtils.deleteEntry(entry.getName());

    // Expect the conflict resolution : the second entry should now
    // have been renamed with the original DN.
    Entry resultEntry = DirectoryServer.getEntry(entry.getName());
    assertNotNull(resultEntry, "The conflict was not cleared");
    assertEquals(getEntryUUID(resultEntry.getName()),
        "c9cb8c3c-615a-4122-865d-50323aaaed48",
        "The wrong entry has been renamed");
    assertThat(resultEntry.getAllAttributes(LDAPReplicationDomain.DS_SYNC_CONFLICT)).isEmpty();
  }

  private AddMsg addMsg(Entry entry, CSN csn, String parentUUID, String childUUID)
  {
    return new AddMsg(csn,
          entry.getName(),
          childUUID, parentUUID,
          entry.getObjectClasses(), entry.getUserAttributes(),
          null);
  }

  /**
   * Test that when a previous conflict is resolved because
   * a MODDN operation has removed one of the conflicting entries
   * the other conflicting entry is correctly renamed to its original name.
   */
  @Test
  public void conflictCleaningMODDN() throws Exception
  {
    Entry entry = createAndAddEntry("conflictCleaningDelete");
    String parentUUID = getEntryUUID(baseDN);

    CSN csn1 = gen.newCSN();

    // Now try to add the same entry with same DN but a different
    // unique ID though the replication
    replayMsg(addMsg(entry, csn1, parentUUID, "c9cb8c3c-615a-4122-865d-50323aaaed48"));

    // Now delete the first entry that was added at the beginning
    ModifyDNOperation modDNOperation =
        getRootConnection().processModifyDN(entry.getName(), RDN.valueOf("cn=foo"), false);
    assertEquals(modDNOperation.getResultCode(), ResultCode.SUCCESS);

    // Expect the conflict resolution : the second entry should now
    // have been renamed with the original DN.
    Entry resultEntry = DirectoryServer.getEntry(entry.getName());
    assertNotNull(resultEntry, "The conflict was not cleared");
    assertEquals(getEntryUUID(resultEntry.getName()),
        "c9cb8c3c-615a-4122-865d-50323aaaed48",
        "The wrong entry has been renamed");
    assertThat(resultEntry.getAllAttributes(LDAPReplicationDomain.DS_SYNC_CONFLICT)).isEmpty();
  }

  private Entry createAndAddEntry(String commonName) throws Exception
  {
    // @formatter:off
    return TestCaseUtils.addEntry(
        "dn: cn=" + commonName + ", " + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street$Rockford, NC  85762",
        "mail: user.1@example.com",
        "cn: Aaccf Amar",
        "l: Rockford",
        "street: 17984 Thirteenth Street",
        "employeeNumber: 1",
        "sn: Amar",
        "givenName: Aaccf",
        "postalCode: 85762",
        "userPassword: password",
        "initials: AA");
    // @formatter:on
  }

  /**
   * Tests for issue 3891
   *       S1                                       S2
   *       ADD uid=xx,ou=parent,...          [SUBTREE] DEL ou=parent, ...
   *
   * 1/ removeParentConflict1 (on S1)
   *    - t1(csn1) ADD uid=xx,ou=parent,...
   *         - t2(csn2) replay SUBTREE DEL ou=parent, ....
   *    => No conflict : expect the parent entry & subtree to be deleted
   *
   * 2/ removeParentConflict2 (on S1)
   *    - t1(csn1) ADD uid=xx,ou=parent,...
   *             - replay t2(csn2) DEL ou=parent, ....
   *    => Conflict and no automatic resolution: expect
   *         - the child entry to be renamed under root entry
   *         - the parent entry to be deleted
   *
   * 3/ removeParentConflict3 (on S2)
   *                         - t2(csn2) DEL or SUBTREE DEL ou=parent, ....
   *                         - t1(csn1) replay ADD uid=xx,ou=parent,...
   *                        => Conflict and no automatic resolution: expect
   *                           - the child entry to be renamed under root entry
   *
   */
  @Test
  public void removeParentConflict1() throws Exception
  {
    Entry parentEntry = createParentEntry();
    Entry childEntry = createChildEntry();

    TestCaseUtils.addEntry(parentEntry);
    TestCaseUtils.addEntry(childEntry);

    String parentUUID = getEntryUUID(parentEntry.getName());

    CSN csn2 = gen.newCSN();
    DeleteMsg  delMsg = new DeleteMsg(parentEntry.getName(), csn2, parentUUID);
    delMsg.setSubtreeDelete(true);

    replayMsg(delMsg);

    // Expect the subtree to be deleted and no conflict entry created
    assertFalse(entryExists(parentEntry.getName()), "DEL subtree on parent was not processed as expected.");
    assertFalse(entryExists(parentEntry.getName()), "DEL subtree on parent was not processed as expected.");
  }

  @Test
  public void removeParentConflict2() throws Exception
  {
    Entry parentEntry = createParentEntry();
    Entry childEntry = createChildEntry();

    TestCaseUtils.addEntry(parentEntry);
    TestCaseUtils.addEntry(childEntry);

    String parentUUID = getEntryUUID(parentEntry.getName());
    String childUUID = getEntryUUID(childEntry.getName());

    CSN csn2 = gen.newCSN();
    DeleteMsg  delMsg = new DeleteMsg(parentEntry.getName(), csn2, parentUUID);
    // NOT SUBTREE

    replayMsg(delMsg);

    // Expect the parent entry to be deleted
    assertFalse(entryExists(parentEntry.getName()), "Parent entry expected to be deleted : " + parentEntry.getName());

    // Expect the child entry to be moved as conflict entry under the root
    // entry of the suffix
    DN childDN = DN.valueOf("entryuuid=" + childUUID + "+cn=child,o=test");
    assertTrue(entryExists(childDN), "Child entry conflict exist with DN=" + childDN);
  }

  @Test
  public void removeParentConflict3() throws Exception
  {
    Entry parentEntry = createParentEntry();
    Entry childEntry = createChildEntry();

    TestCaseUtils.addEntry(parentEntry);
    String parentUUID = getEntryUUID(parentEntry.getName());
    TestCaseUtils.deleteEntry(parentEntry);

    CSN csn1 = gen.newCSN();

    // Create and publish an update message to add the child entry.
    String childUUID = "44444444-4444-4444-4444-444444444444";
    AddMsg addMsg = new AddMsg(
        csn1,
        childEntry.getName(),
        childUUID,
        parentUUID,
        childEntry.getObjectClassAttribute(),
        childEntry.getAllAttributes(), null);

    // Put the message in the replay queue
    replayMsg(addMsg);

    // Expect the parent entry to be deleted
    assertFalse(entryExists(parentEntry.getName()), "Parent entry exists ");

    // Expect the child entry to be moved as conflict entry under the root
    // entry of the suffix
    DN childDN = DN.valueOf("entryuuid=" + childUUID + "+cn=child,o=test");
    assertTrue(entryExists(childDN), "Child entry conflict exist with DN=" + childDN);
  }

  private Entry createParentEntry() throws Exception
  {
    return TestCaseUtils.makeEntry(
        "dn: ou=rpConflict, "+ TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: organizationalUnit");
  }

  private Entry createChildEntry() throws Exception
  {
    // @formatter:off
    return TestCaseUtils.makeEntry(
        "dn: cn=child, ou=rpConflict,"+ TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street$Rockford, NC  85762",
        "mail: user.1@example.com",
        "cn: Aaccf Amar",
        "l: Rockford",
        "street: 17984 Thirteenth Street",
        "employeeNumber: 1",
        "sn: Amar",
        "givenName: Aaccf",
        "postalCode: 85762",
        "userPassword: password",
        "initials: AA");
    // @formatter:on
  }

  private void replayMsg(UpdateMsg updateMsg) throws InterruptedException
  {
    domain.processUpdate(updateMsg);
    LDAPUpdateMsg ldapUpdate = queue.take().getUpdateMessage();
    domain.markInProgress(ldapUpdate);
    domain.replay(ldapUpdate, SHUTDOWN);
  }
}
