/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.types.*;
import org.testng.annotations.Test;

/**
 * Test the naming conflict resolution code.
 */
@SuppressWarnings("javadoc")
public class NamingConflictTest extends ReplicationTestCase
{

  private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

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
  @Test(enabled=true)
  public void simultaneousModrdnConflict() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    try
    {
      /*
       * Create a Change number generator to generate new ChangeNumbers
       * when we need to send operations messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(201, 0);

      String parentUUID = getEntryUUID(DN.decode(TEST_ROOT_DN_STRING));

      Entry entry = TestCaseUtils.entryFromLdifString(
          "dn: cn=simultaneousModrdnConflict, "+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n" + "objectClass: person\n"
          + "objectClass: organizationalPerson\n"
          + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
          + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
          + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
          + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
          + "cn: Aaccf Amar\n" + "l: Rockford\n"
          + "street: 17984 Thirteenth Street\n"
          + "employeeNumber: 1\n"
          + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
          + "userPassword: password\n" + "initials: AA\n");

      TestCaseUtils.addEntry(entry);
      String entryUUID = getEntryUUID(entry.getDN());

      // generate two consecutive ChangeNumber that will be used in backward order
      ChangeNumber cn1 = gen.newChangeNumber();
      ChangeNumber cn2 = gen.newChangeNumber();

      ModifyDNMsg  modDnMsg = new ModifyDNMsg(
          entry.getDN().toNormalizedString(), cn2,
          entryUUID, parentUUID, false,
          TEST_ROOT_DN_STRING,
      "uid=simultaneous2");

      // Put the message in the replay queue
      domain.processUpdate(modDnMsg, SHUTDOWN);

      // Make the domain replay the change from the replay queue
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // This MODIFY DN uses an older DN and should therefore be cancelled
      // at replay time.
      modDnMsg = new ModifyDNMsg(
          entry.getDN().toNormalizedString(), cn1,
          entryUUID, parentUUID, false,
          TEST_ROOT_DN_STRING,
      "uid=simulatneouswrong");

      // Put the message in the replay queue
      domain.processUpdate(modDnMsg, SHUTDOWN);

      // Make the domain replay the change from the replay queue
      // and resolve conflict
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // Expect the conflict resolution
      assertFalse(DirectoryServer.entryExists(entry.getDN()),
      "The modDN conflict was not resolved as expected.");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
    }
  }

  /**
   * Test that when a previous conflict is resolved because
   * a delete operation has removed one of the conflicting entries
   * the other conflicting entry is correctly renamed to its
   * original name.
   */
  @Test(enabled=true)
  public void conflictCleaningDelete() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    try
    {
      /*
       * Create a Change number generator to generate new ChangeNumbers
       * when we need to send operations messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(201, 0);

      String entryldif =
        "dn: cn=conflictCleaningDelete, "+ TEST_ROOT_DN_STRING + "\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar\n" + "l: Rockford\n"
        + "street: 17984 Thirteenth Street\n"
        + "employeeNumber: 1\n"
        + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);

      // Add the first entry
      TestCaseUtils.addEntry(entry);
      String parentUUID = getEntryUUID(DN.decode(TEST_ROOT_DN_STRING));

      ChangeNumber cn1 = gen.newChangeNumber();

      // Now try to add the same entry with same DN but a different
      // unique ID though the replication
      AddMsg  addMsg =
        new AddMsg(cn1,
            entry.getDN().toNormalizedString(),
            "c9cb8c3c-615a-4122-865d-50323aaaed48", parentUUID,
            entry.getObjectClasses(), entry.getUserAttributes(),
            null);

      // Put the message in the replay queue
      domain.processUpdate(addMsg, SHUTDOWN);

      // Make the domain replay the change from the replay queue
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // Now delete the first entry that was added at the beginning
      TestCaseUtils.deleteEntry(entry.getDN());

      // Expect the conflict resolution : the second entry should now
      // have been renamed with the original DN.
      Entry resultEntry = DirectoryServer.getEntry(entry.getDN());
      assertNotNull(resultEntry, "The conflict was not cleared");
      assertEquals(getEntryUUID(resultEntry.getDN()),
          "c9cb8c3c-615a-4122-865d-50323aaaed48",
          "The wrong entry has been renamed");
      assertNull(resultEntry.getAttribute(LDAPReplicationDomain.DS_SYNC_CONFLICT));
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
    }
  }

  /**
   * Test that when a previous conflict is resolved because
   * a MODDN operation has removed one of the conflicting entries
   * the other conflicting entry is correctly renamed to its
   * original name.
   *
   * @throws Exception if the test fails.
   */
  @Test(enabled=true)
  public void conflictCleaningMODDN() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    try
    {
      /*
       * Create a Change number generator to generate new ChangeNumbers
       * when we need to send operations messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(201, 0);

      String entryldif =
        "dn: cn=conflictCleaningDelete, "+ TEST_ROOT_DN_STRING + "\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar\n" + "l: Rockford\n"
        + "street: 17984 Thirteenth Street\n"
        + "employeeNumber: 1\n"
        + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);

      // Add the first entry
      TestCaseUtils.addEntry(entry);
      String parentUUID = getEntryUUID(DN.decode(TEST_ROOT_DN_STRING));

      ChangeNumber cn1 = gen.newChangeNumber();

      // Now try to add the same entry with same DN but a different
      // unique ID though the replication
      AddMsg  addMsg =
        new AddMsg(cn1,
            entry.getDN().toNormalizedString(),
            "c9cb8c3c-615a-4122-865d-50323aaaed48", parentUUID,
            entry.getObjectClasses(), entry.getUserAttributes(),
            null);

      // Put the message in the replay queue
      domain.processUpdate(addMsg, SHUTDOWN);

      // Make the domain replay the change from the replay queue
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // Now delete the first entry that was added at the beginning
      InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

      ModifyDNOperation modDNOperation =
        conn.processModifyDN(entry.getDN(), RDN.decode("cn=foo"), false);
      assertEquals(modDNOperation.getResultCode(), ResultCode.SUCCESS);

      // Expect the conflict resolution : the second entry should now
      // have been renamed with the original DN.
      Entry resultEntry = DirectoryServer.getEntry(entry.getDN());
      assertNotNull(resultEntry, "The conflict was not cleared");
      assertEquals(getEntryUUID(resultEntry.getDN()),
          "c9cb8c3c-615a-4122-865d-50323aaaed48",
          "The wrong entry has been renamed");
      assertNull(resultEntry.getAttribute(LDAPReplicationDomain.DS_SYNC_CONFLICT));
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
    }
  }

  /**
   * Tests for issue 3891
   *       S1                                       S2
   *       ADD uid=xx,ou=parent,...          [SUBTREE] DEL ou=parent, ...
   *
   * 1/ removeParentConflict1 (on S1)
   *    - t1(cn1) ADD uid=xx,ou=parent,...
   *         - t2(cn2) replay SUBTREE DEL ou=parent, ....
   *    => No conflict : expect the parent entry & subtree to be deleted
   *
   * 2/ removeParentConflict2 (on S1)
   *    - t1(cn1) ADD uid=xx,ou=parent,...
   *             - replay t2(cn2) DEL ou=parent, ....
   *    => Conflict and no automatic resolution: expect
   *         - the child entry to be renamed under root entry
   *         - the parent entry to be deleted
   *
   * 3/ removeParentConflict3 (on S2)
   *                         - t2(cn2) DEL or SUBTREE DEL ou=parent, ....
   *                         - t1(cn1) replay ADD uid=xx,ou=parent,...
   *                        => Conflict and no automatic resolution: expect
   *                           - the child entry to be renamed under root entry
   *
   */
  @Test(enabled=true)
  public void removeParentConflict1() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    try
    {
      /*
       * Create a Change number generator to generate new ChangeNumbers
       * when we need to send operations messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(201, 0);

      Entry parentEntry = TestCaseUtils.entryFromLdifString(
          "dn: ou=rpConflict, "+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: organizationalUnit\n");

      Entry childEntry = TestCaseUtils.entryFromLdifString(
          "dn: cn=child, ou=rpConflict,"+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: person\n"
          + "objectClass: organizationalPerson\n"
          + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
          + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
          + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
          + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
          + "cn: Aaccf Amar\n" + "l: Rockford\n"
          + "street: 17984 Thirteenth Street\n"
          + "employeeNumber: 1\n"
          + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
          + "userPassword: password\n" + "initials: AA\n");

      TestCaseUtils.addEntry(parentEntry);
      TestCaseUtils.addEntry(childEntry);

      String parentUUID = getEntryUUID(parentEntry.getDN());

      ChangeNumber cn2 = gen.newChangeNumber();

      DeleteMsg  delMsg = new DeleteMsg(
          parentEntry.getDN().toNormalizedString(),
          cn2,
          parentUUID);
      delMsg.setSubtreeDelete(true);

      // Put the message in the replay queue
      domain.processUpdate(delMsg, SHUTDOWN);
      // Make the domain replay the change from the replay queue
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // Expect the subtree to be deleted and no conflict entry created
      assertFalse(DirectoryServer.entryExists(parentEntry.getDN()),
      "DEL subtree on parent was not processed as expected.");
      assertFalse(DirectoryServer.entryExists(parentEntry.getDN()),
      "DEL subtree on parent was not processed as expected.");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
    }
  }

  @Test(enabled=true)
  public void removeParentConflict2() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    try
    {
      /*
       * Create a Change number generator to generate new ChangeNumbers
       * when we need to send operations messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(201, 0);

      Entry parentEntry = TestCaseUtils.entryFromLdifString(
          "dn: ou=rpConflict, "+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: organizationalUnit\n");

      Entry childEntry = TestCaseUtils.entryFromLdifString(
          "dn: cn=child, ou=rpConflict,"+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: person\n"
          + "objectClass: organizationalPerson\n"
          + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
          + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
          + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
          + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
          + "cn: Aaccf Amar\n" + "l: Rockford\n"
          + "street: 17984 Thirteenth Street\n"
          + "employeeNumber: 1\n"
          + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
          + "userPassword: password\n" + "initials: AA\n");

      TestCaseUtils.addEntry(parentEntry);
      TestCaseUtils.addEntry(childEntry);

      assertTrue(DirectoryServer.entryExists(parentEntry.getDN()),
      "Parent entry expected to exist.");
      assertTrue(DirectoryServer.entryExists(childEntry.getDN()),
      "Child  entry expected to be exist.");

      String parentUUID = getEntryUUID(parentEntry.getDN());
      String childUUID = getEntryUUID(childEntry.getDN());

      ChangeNumber cn2 = gen.newChangeNumber();

      DeleteMsg  delMsg = new DeleteMsg(
          parentEntry.getDN().toNormalizedString(),
          cn2,
          parentUUID);
      // NOT SUBTREE

      // Put the message in the replay queue
      domain.processUpdate(delMsg, SHUTDOWN);
      // Make the domain replay the change from the replay queue
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // Expect the parent entry to be deleted
      assertTrue(!DirectoryServer.entryExists(parentEntry.getDN()),
          "Parent entry expected to be deleted : " + parentEntry.getDN());

      // Expect the child entry to be moved as conflict entry under the root
      // entry of the suffix
      DN childDN = DN.decode("entryuuid="+childUUID+
          "+cn=child,o=test");
      assertTrue(DirectoryServer.entryExists(childDN),
          "Child entry conflict exist with DN="+childDN);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
    }
  }

  @Test(enabled=true)
  public void removeParentConflict3() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    try
    {
      /*
       * Create a Change number generator to generate new ChangeNumbers
       * when we need to send operations messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(201, 0);

      Entry parentEntry = TestCaseUtils.entryFromLdifString(
          "dn: ou=rpConflict, "+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: organizationalUnit\n");

      Entry childEntry = TestCaseUtils.entryFromLdifString(
          "dn: cn=child, ou=rpConflict,"+ TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: person\n"
          + "objectClass: organizationalPerson\n"
          + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
          + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
          + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
          + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
          + "cn: Aaccf Amar\n" + "l: Rockford\n"
          + "street: 17984 Thirteenth Street\n"
          + "employeeNumber: 1\n"
          + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
          + "userPassword: password\n" + "initials: AA\n");


      TestCaseUtils.addEntry(parentEntry);
      String parentUUID = getEntryUUID(parentEntry.getDN());
      TestCaseUtils.deleteEntry(parentEntry);

      ChangeNumber cn1 = gen.newChangeNumber();

      // Create and publish an update message to add the child entry.
      String childUUID = "44444444-4444-4444-4444-444444444444";
      AddMsg addMsg = new AddMsg(
          cn1,
          childEntry.getDN().toString(),
          childUUID,
          parentUUID,
          childEntry.getObjectClassAttribute(),
          childEntry.getAttributes(),
          new ArrayList<Attribute>());

      // Put the message in the replay queue
      domain.processUpdate(addMsg, SHUTDOWN);
      // Make the domain replay the change from the replay queue
      domain.replay(queue.take().getUpdateMessage(), SHUTDOWN);

      // Expect the parent entry to be deleted
      assertFalse(DirectoryServer.entryExists(parentEntry.getDN()),
          "Parent entry exists ");

      // Expect the child entry to be moved as conflict entry under the root
      // entry of the suffix
      DN childDN = DN.decode("entryuuid="+childUUID+
          "+cn=child,o=test");
      assertTrue(DirectoryServer.entryExists(childDN),
          "Child entry conflict exist with DN="+childDN);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
    }
  }
}
