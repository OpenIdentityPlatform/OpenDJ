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
   * about it either - a parent it did not report is not a parent which was deleted.
   * <p>
   * Reading them that way renames the entry under the base DN as a conflicting entry:
   * a divergence which is left for an administrator to repair by hand, where the entry
   * only had to be added again once the storage served the searches.
   */
  @Test
  public void addIsRetriedWhileTheEntryUUIDSearchesCanNotRun() throws Exception
  {
    final Entry parent = TestCaseUtils.addEntry(
        "dn: ou=addWhoseSearchesCanNotRun," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: addWhoseSearchesCanNotRun");
    final String parentUUID = getEntryUUID(parent.getName());

    final Entry child = TestCaseUtils.makeEntry(
        "dn: cn=addedWhileTheSearchesFailed," + parent.getName(),
        "objectClass: top",
        "objectClass: person",
        "cn: addedWhileTheSearchesFailed",
        "sn: Amar");
    final CSN csn = gen.newCSN();

    /*
     * Every search this attempt makes fails: the one which checks whether the Add was
     * replayed here already, the one which checks that the parent is still the one the
     * change was made under, and the one conflict resolution reads the data with once
     * the Add failed. The next attempt has them all served again.
     */
    ShortCircuitPlugin.registerShortCircuit(
        OperationType.SEARCH, "PreParse", ResultCode.UNAVAILABLE.intValue(), 3);
    try
    {
      replayMsg(addMsg(child, csn, parentUUID, "0b2b1b3c-1a4d-4a8e-9f6b-2c4d5e6f7a8b"));
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.SEARCH, "PreParse");
    }

    assertTrue(entryExists(child.getName()),
        "the entry was not added under its parent: searches which could not run were read "
            + "as a parent which is gone");
    assertTrue(domain.getServerState().cover(csn),
        "a change which was applied must be recorded as replayed");
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
