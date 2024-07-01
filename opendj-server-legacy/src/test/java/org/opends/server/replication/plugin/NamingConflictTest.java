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
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.Entry;
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
    MultimasterReplication.deleteDomain(baseDN);
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
