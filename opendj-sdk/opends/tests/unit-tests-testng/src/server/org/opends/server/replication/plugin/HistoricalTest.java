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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */

package org.opends.server.replication.plugin;


import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.opends.server.TestCaseUtils.*;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Tests the Historical class.
 */
public class HistoricalTest
     extends ReplicationTestCase
{
  private int replServerPort;
  String testName = "historicalTest";

  /**
   * Set up replication on the test backend.
   * @throws Exception If an error occurs.
   */
  @BeforeClass
  @Override
  public void setUp()
       throws Exception
  {
    super.setUp();

    // Create an internal connection.
    connection = InternalClientConnection.getRootConnection();

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // The replication server.
    String replServerStringDN = "cn=Replication Server, " + SYNCHRO_PLUGIN_DN;
    String replServerLdif = "dn: " + replServerStringDN + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-replication-server\n"
         + "cn: replication Server\n"
         + "ds-cfg-replication-port: " + replServerPort + "\n"
         + "ds-cfg-replication-db-directory: HistoricalTest\n"
         + "ds-cfg-replication-server-id: 102\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // The suffix to be synchronized.
    String synchroServerStringDN = "cn=" + testName + ", cn=domains, " +
      SYNCHRO_PLUGIN_DN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-replication-domain\n"
         + "cn: " + testName + "\n"
         + "ds-cfg-base-dn: " + TEST_ROOT_DN_STRING + "\n"
         + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
         + "ds-cfg-server-id: 1\n"
         + "ds-cfg-receive-status: true\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    configureReplication();
  }

  /**
   * Tests that the attribute modification history is correctly read from
   * and written to an operational attribute of the entry.
   * Also test that historical is purged according to the purge delay that
   * is provided.
   * @throws Exception If the test fails.
   */
  @Test(enabled=true)
  public void testEncodingAndPurge()
       throws Exception
  {
    //  Add a test entry.
    TestCaseUtils.addEntry(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "cn: Aaccf Amar",
        "sn: Amar",
        "givenName: Aaccf",
        "userPassword: password",
        "description: Initial description",
        "displayName: 1"
    );

    // Modify the test entry to give it some history.
    // Test both single and multi-valued attributes.

    String path = TestCaseUtils.createTempFile(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "changetype: modify",
        "add: cn;lang-en",
        "cn;lang-en: Aaccf Amar",
        "cn;lang-en: Aaccf A Amar",
        "-",
        "replace: description",
        "description: replaced description",
        "-",
        "add: displayName",
        "displayName: 2",
        "-",
        "delete: displayName",
        "displayName: 1",
        "-"
    );

    String[] args =
    {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    args[9] = TestCaseUtils.createTempFile(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "changetype: modify",
        "replace: displayName",
        "displayName: 2",
        "-"
    );

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    // Read the entry back to get its history operational attribute.
    DN dn = DN.decode("uid=user.1," + TEST_ROOT_DN_STRING);
    Entry entry = DirectoryServer.getEntry(dn);

    List<Attribute> attrs = EntryHistorical.getHistoricalAttr(entry);
    Attribute before = attrs.get(0);

    // Check that encoding and decoding preserves the history information.
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute after = hist.encodeAndPurge();

    assertEquals(hist.getLastPurgedValuesCount(),0);
    assertEquals(after, before);

    LDAPReplicationDomain domain = MultimasterReplication.findDomain(
        DN.decode("uid=user.1," + TEST_ROOT_DN_STRING), null);
    Thread.sleep(1000);

    args[9] = TestCaseUtils.createTempFile(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "changetype: modify",
        "replace: displayName",
        "displayName: 3",
        "-"
    );
    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    long testPurgeDelayInMillisec = 1000; // 1 sec

    // Read the entry back to get its history operational attribute.
    entry = DirectoryServer.getEntry(dn);
    hist = EntryHistorical.newInstanceFromEntry(entry);
    hist.setPurgeDelay(testPurgeDelayInMillisec);
    after = hist.encodeAndPurge();

    // The purge time is not done so the hist attribute should be not empty
    assertTrue(!after.isEmpty());

    // Now wait for the purge time to be done
    Thread.sleep(testPurgeDelayInMillisec + 200);

    // Read the entry back to get its history operational attribute.
    // The hist attribute should now be empty since purged
    entry = DirectoryServer.getEntry(dn);
    hist = EntryHistorical.newInstanceFromEntry(entry);
    hist.setPurgeDelay(testPurgeDelayInMillisec);
    after = hist.encodeAndPurge();
    assertTrue(after.isEmpty());
    assertEquals(hist.getLastPurgedValuesCount(),11);
  }

  /**
   * The scenario for this test case is that two modify operations occur at
   * two different servers at nearly the same time, each operation adding a
   * different value for a single-valued attribute.  Replication then
   * replays the operations and we expect the conflict to be resolved on both
   * servers by keeping whichever value was actually added first.
   * For the unit test, we employ a single directory server.  We use the
   * broker API to simulate the ordering that would happen on the first server
   * on one entry, and the reverse ordering that would happen on the
   * second server on a different entry.  Confused yet?
   * @throws Exception If the test fails.
   */
  @Test(enabled=true, groups="slow")
  public void conflictSingleValue()
       throws Exception
  {
    final DN dn1 = DN.decode("cn=test1," + TEST_ROOT_DN_STRING);
    final DN dn2 = DN.decode("cn=test2," + TEST_ROOT_DN_STRING);
    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
    final AttributeType attrType =
         DirectoryServer.getAttributeType("displayname");
    final AttributeType entryuuidType =
         DirectoryServer.getAttributeType("entryuuid");

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ReplicationBroker broker =
      openReplicationSession(baseDn, 2, 100, replServerPort, 1000, true);


    // Clear the backend and create top entrye
    TestCaseUtils.initializeTestBackend(true);

    // Add the first test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test1," + TEST_ROOT_DN_STRING,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test1",
         "sn: test"
       );

    // Read the entry back to get its UUID.
    Entry entry = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs = entry.getAttribute(entryuuidType);
    String entryuuid =
         attrs.get(0).iterator().next().getValue().toString();

    // Add the second test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test2," + TEST_ROOT_DN_STRING,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test2",
         "sn: test",
         "description: Description"
       );

    // Read the entry back to get its UUID.
    entry = DirectoryServer.getEntry(dn2);
    attrs = entry.getAttribute(entryuuidType);
    String entryuuid2 =
         attrs.get(0).iterator().next().getValue().toString();

    long now = System.currentTimeMillis();
    // A change on a first server.
    ChangeNumber t1 = new ChangeNumber(now,  0,  3);

    // A change on a second server.
    ChangeNumber t2 = new ChangeNumber(now+1,  0,  4);

    // Simulate the ordering t1:add:A followed by t2:add:B that would
    // happen on one server.

    // Replay an add of a value A at time t1 on a first server.
    Attribute attr = Attributes.create(attrType.getNormalizedPrimaryName(), "A");
    Modification mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t1, dn1, entryuuid, mod);

    // It would be nice to avoid these sleeps.
    // We need to preserve the replay order but the order could be changed
    // due to the multi-threaded nature of the replication replay.
    // Putting a sentinel value in the modification is not foolproof since
    // the operation might not get replayed at all.
    Thread.sleep(2000);

    // Replay an add of a value B at time t2 on a second server.
    attr = Attributes.create(attrType.getNormalizedPrimaryName(), "B");
    mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t2, dn1, entryuuid, mod);

    // Simulate the reverse ordering t2:add:B followed by t1:add:A that
    // would happen on the other server.

    t1 = new ChangeNumber(now+3,  0,  3);
    t2 = new ChangeNumber(now+4,  0,  4);

    // Replay an add of a value B at time t2 on a second server.
    attr = Attributes.create(attrType.getNormalizedPrimaryName(), "B");
    mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t2, dn2, entryuuid2, mod);

    Thread.sleep(2000);

    // Replay an add of a value A at time t1 on a first server.
    attr = Attributes.create(attrType.getNormalizedPrimaryName(), "A");
    mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t1, dn2, entryuuid2, mod);

    Thread.sleep(2000);

    // Read the first entry to see how the conflict was resolved.
    entry = DirectoryServer.getEntry(dn1);
    attrs = entry.getAttribute(attrType);
    String attrValue1 =
         attrs.get(0).iterator().next().getValue().toString();

    // Read the second entry to see how the conflict was resolved.
    entry = DirectoryServer.getEntry(dn2);
    attrs = entry.getAttribute(attrType);
    String attrValue2 =
         attrs.get(0).iterator().next().getValue().toString();

    // The two values should be the first value added.
    assertEquals(attrValue1, "A");
    assertEquals(attrValue2, "A");

    TestCaseUtils.deleteEntry(DN.decode("cn=test1," + TEST_ROOT_DN_STRING));
    TestCaseUtils.deleteEntry(DN.decode("cn=test2," + TEST_ROOT_DN_STRING));
}

  private static
  void publishModify(ReplicationBroker broker, ChangeNumber changeNum,
                     DN dn, String entryuuid, Modification mod)
  {
    List<Modification> mods = new ArrayList<Modification>(1);
    mods.add(mod);
    ModifyMsg modMsg = new ModifyMsg(changeNum, dn, mods, entryuuid);
    broker.publish(modMsg);
  }

  /**
   * Test that historical information is correctly added when performaing ADD,
   * MOD and MODDN operations.
   */
  @Test()
  public void historicalAdd() throws Exception
  {
    final DN dn1 = DN.decode("cn=testHistoricalAdd,o=test");

    // Clear the backend.
    TestCaseUtils.initializeTestBackend(true);

    // Add the first test entry.
    TestCaseUtils.addEntry(
        "dn: " + dn1,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Read the entry that was just added.
    Entry entry = DirectoryServer.getEntry(dn1);

    // Check that we can build an Add operation from this entry.
    // This will ensure both that the Add historical information is
    // correctly added and also that the code that rebuild operation
    // from this historical information is working.
    Iterable<FakeOperation> ops = EntryHistorical.generateFakeOperations(entry);

    // Perform a few check on the Operation to see that it
    // was correctly generated.
    assertFakeOperations(dn1, entry, ops, 1);

    // Now apply a modifications to the entry and check that the
    // ADD historical information has been preserved.
    TestCaseUtils.applyModifications(false,
        "dn: " + dn1,
        "changetype: modify",
        "add: description",
    "description: foo");

    // Read the modified entry.
    entry = DirectoryServer.getEntry(dn1);

    // use historical information to generate new list of operations
    // equivalent to the operations that have been applied to this entry.
    ops = EntryHistorical.generateFakeOperations(entry);

    // Perform a few check on the operation list to see that it
    // was correctly generated.
    assertFakeOperations(dn1, entry, ops, 2);

    // rename the entry.
    TestCaseUtils.applyModifications(false,
        "dn: " + dn1,
        "changetype: moddn",
        "newrdn: cn=test2",
    "deleteoldrdn: 1");

    // Read the modified entry.
    final DN dn2 = DN.decode("cn=test2,o=test");
    entry = DirectoryServer.getEntry(dn2);

    // use historical information to generate new list of operations
    // equivalent to the operations that have been applied to this entry.
    ops = EntryHistorical.generateFakeOperations(entry);

    // Perform a few check on the operation list to see that it
    // was correctly generated.
    assertFakeOperations(dn2, entry, ops, 3);

    // Now clear the backend and try to run the generated operations
    // to check that applying them do lead to an equivalent result.
    TestCaseUtils.initializeTestBackend(true);

    for (FakeOperation fake : ops)
    {
      LDAPUpdateMsg msg = (LDAPUpdateMsg) fake.generateMessage();
      Operation op =
        msg.createOperation(InternalClientConnection.getRootConnection());
      op.setInternalOperation(true);
      op.setSynchronizationOperation(true);
      op.run();
    }

    Entry newEntry = DirectoryServer.getEntry(dn2);
    assertEquals(entry.getDN(), newEntry.getDN());
  }

  /**
   * Performs a few check on the provided ADD operations, particularly
   * that a ADDmsg can be created from it with valid values for fields
   * DN, entryuid, ...)
   */
  private void assertFakeOperations(final DN dn1, Entry entry,
      Iterable<FakeOperation> ops, int assertCount) throws Exception
  {
    int count = 0;
    for (FakeOperation op : ops)
    {
      count++;
      if (op instanceof FakeAddOperation)
      {
        // perform a few check on the Operation to see that it
        // was correctly generated :
        // - the dn should be dn1,
        // - the entry id and the parent id should match the ids from the entry
        FakeAddOperation addOp = (FakeAddOperation) op;
        assertTrue(addOp.getChangeNumber() != null);
        AddMsg addmsg = addOp.generateMessage();
        assertTrue(dn1.equals(DN.decode(addmsg.getDn())));
        assertTrue(addmsg.getEntryUUID().equals(EntryHistorical.getEntryUUID(entry)));
        String parentId = LDAPReplicationDomain.findEntryUUID(dn1.getParent());
        assertTrue(addmsg.getParentEntryUUID().equals(parentId));
        addmsg.createOperation(InternalClientConnection.getRootConnection());
      } else
      {
        if (count == 1)
        {
          // The first operation should be an ADD operation.
          fail("FakeAddOperation was not correctly generated"
              + " from historical information");
        }
      }
    }

      assertEquals(count, assertCount);
    }

  /**
   * Test the task that purges the replication historical stored in the user
   * entry.
   * Steps :
   * - creates entry containing historical
   * - wait for the pruge delay
   * - lauch the purge task
   * - verify that all historical has been purged
   *
   * TODO: another test should be written that configures the task no NOT have
   * the time to purge everything in 1 run .. and thus to relauch it to finish
   * the purge. And verify that the second run starts on the changeNumber where
   * the previous task run had stopped.
   *
   * @throws Exception If the test fails.
   */
  @Test(enabled=true)
  public void testRecurringPurgeIn1Run()
  throws Exception
  {
    int entryCnt = 10;

    addEntriesWithHistorical(1, entryCnt);

    // set the purge delay to 1 sec
    TestCaseUtils.dsconfig(
        "set-replication-domain-prop",
        "--provider-name","Multimaster Synchronization",
        "--domain-name",testName,
        "--set","conflicts-historical-purge-delay:1m");

    Thread.sleep(60*1000);

    // launch the purge
    Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-purge-conflicts-historical",
        "ds-task-class-name: org.opends.server.tasks.PurgeConflictsHistoricalTask",
        "ds-task-purge-conflicts-historical-domain-dn: "+TEST_ROOT_DN_STRING,
    "ds-task-purge-conflicts-historical-maximum-duration: 120"); // 120 sec

    addTask(taskInit, ResultCode.SUCCESS, null);

    // every entry should be purged from its hist
    try
    {
      // Search for matching entries in config backend
      InternalSearchOperation op = connection.processSearch(
          ByteString.valueOf(TEST_ROOT_DN_STRING),
          SearchScope.WHOLE_SUBTREE,
          LDAPFilter.decode("(ds-sync-hist=*)"));
      assertEquals(op.getResultCode(), ResultCode.SUCCESS,
          op.getErrorMessage().toString());

      // Check that no entries have been found
      LinkedList<SearchResultEntry> entries = op.getSearchEntries();
      assertTrue(entries != null);
      assertEquals(entries.size(), 0);
    } catch (Exception e)
    {
      fail("assertNoConfigEntriesWithFilter: could not search config backend" + e.getMessage());
    }
  }

  /**
   * Add a provided number of generated entries containing historical.
   * @param dnSuffix A suffix to be added to the dn
   * @param entryCnt The number of entries to create
   * @throws Exception
   */
  private void addEntriesWithHistorical(int dnSuffix, int entryCnt)
  throws Exception
  {
    for (int i=0; i<entryCnt;i++)
    {
      String sdn =  "dn: uid=user"+i+dnSuffix+"," + TEST_ROOT_DN_STRING;

        //  Add a test entry.
        TestCaseUtils.addEntry(
            sdn,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user"+i,
            "cn: Aaccf Amar",
            "sn: Amar",
            "givenName: Aaccf",
            "userPassword: password",
            "description: Initial description",
            "displayName: 1"
        );

      // Modify the test entry to give it some history.
      // Test both single and multi-valued attributes.

      String path = TestCaseUtils.createTempFile(
          sdn,
          "changetype: modify",
          "add: cn;lang-en",
          "cn;lang-en: Aaccf Amar",
          "cn;lang-en: Aaccf A Amar",
          "-",
          "replace: givenName",
          "givenName: new given",
          "-",
          "replace: userPassword",
          "userPassword: new pass",
          "-",
          "replace: description",
          "description: replaced description",
          "-",
          "replace: sn",
          "sn: replaced sn",
          "-",
          "add: displayName",
          "displayName: 2",
          "-",
          "delete: displayName",
          "displayName: 1",
          "-"
      );

      String[] args =
      {
          "-h", "127.0.0.1",
          "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D", "cn=Directory Manager",
          "-w", "password",
          "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

      args[9] = TestCaseUtils.createTempFile(
          sdn,
          "changetype: modify",
          "replace: displayName",
          "displayName: 2",
          "-"
      );

      assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    }
  }
}
