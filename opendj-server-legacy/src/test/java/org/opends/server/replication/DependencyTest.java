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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test that the dependencies are computed correctly when replaying
 * sequences of operations that requires to follow a given order
 * such as : ADD an entry, ADD a children entry.
 */
@SuppressWarnings("javadoc")
public class DependencyTest extends ReplicationTestCase
{

  private final long CLEAN_DB_GENERATION_ID = 7883L;
  private DN TEST_ROOT_DN;


  @BeforeClass
  public void setup() throws Exception
  {
     TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
  }

  /**
   * Check that a sequence of dependents adds and mods is correctly ordered:
   * Using a deep dit :
   *    TEST_ROOT_DN_STRING
   *       |
   *    dc=dependency1
   *       |
   *    dc=dependency2
   *       |
   *    dc=dependency3
   *       |
   *
   *       |
   *    dc=dependencyN
   * This test sends a sequence of interleaved ADD operations and MODIFY
   * operations to build such a dit.
   *
   * Then test that the sequence of Delete necessary to remove
   * all those entries is also correctly ordered.
   */
  @Test(enabled=true, groups="slow")
  public void addModDelDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    LDAPReplicationDomain domain = null;
    DN baseDN = TEST_ROOT_DN;
    int brokerId = 2;
    int serverId = 1;
    int replServerId = 81;
    int addSequenceLength = 30;


    cleanDB();

    try
    {
      /*
       * FIRST PART :
       * Check that a sequence of dependent ADD is correctly ordered.
       *
       * - Create replication server
       * - Send sequence of ADD messages to the replication server
       * - Configure replication server
       * - check that the last entry has been correctly added
       */
      Entry entry = TestCaseUtils.makeEntry(
          "dn:" + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: organization",
          "entryuuid: " + stringUID(1));

      replServer = newReplicationServer(
          replServerId, addSequenceLength * 5 + 100, "dependencyTestAddModDelDependencyTestDb");

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 1000, replServer.getReplicationPort(), 1000, CLEAN_DB_GENERATION_ID);

      Thread.sleep(2000);

      // send a sequence of add operation
      DN addDN = TEST_ROOT_DN;
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);

      int sequence;
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        entry.removeAttribute(getEntryUUIDAttributeType());
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)),
                           new LinkedList<ByteString>());
        addDN = DN.valueOf("dc=dependency" + sequence + "," + addDN);
        broker.publish(addMsg(addDN, entry, sequence + 1, sequence, gen));
        broker.publish(modifyMsg(addDN, sequence + 1, generatemods("description", "test"), gen));
      }

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      domain = startNewLDAPReplicationDomain(replServer, baseDN, serverId, 100000);

      // check that last entry in sequence got added.
      Entry lastEntry = getEntry(addDN, 30000, true);
      assertNotNull(lastEntry,
                    "The last entry of the ADD sequence was not added.");

      // Check that all the modify have been replayed
      // (all the entries should have a description).
      addDN = TEST_ROOT_DN;
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=dependency" + sequence + "," + addDN);
        checkEntryHasAttributeValue(
            addDN, "description", "test", 10, "The modification was not replayed on entry " + addDN);
      }

      /*
       * SECOND PART
       *
       * Now check that the dependencies between delete are correctly
       * managed.
       *
       * disable the domain while we publish the delete message to
       * to replication server so that when we enable it it receives the
       * delete operation in bulk.
       */

      domain.disable();
      Thread.sleep(2000);  // necessary because disable does not wait
                           // for full termination of all threads. (issue 1571)

      DN deleteDN = addDN;
      while (sequence-->1)
      {
        broker.publish(delMsg(deleteDN, sequence + 1, gen));
        deleteDN = deleteDN.parent();
      }

      domain.enable();

      // check that entry just below the base entry was deleted.
      // (we can't delete the base entry because some other tests might
      // have added other children)
      DN node1 = DN.valueOf("dc=dependency1," + TEST_ROOT_DN_STRING);
      Entry baseEntry = getEntry(node1, 30000, false);
      assertNull(baseEntry,
                 "The last entry of the DEL sequence was not deleted.");
    }
    finally
    {
      remove(replServer);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
  }

  private AddMsg addMsg(DN addDN, Entry entry, int uniqueId, int parentId, CSNGenerator gen)
  {
    return new AddMsg(gen.newCSN(), addDN, stringUID(uniqueId), stringUID(parentId),
        entry.getObjectClassAttribute(), entry.getAllAttributes(), null);
  }

  private ModifyMsg modifyMsg(DN dn, int entryUUID, List<Modification> mods, CSNGenerator gen)
  {
    return new ModifyMsg(gen.newCSN(), dn, mods, stringUID(entryUUID));
  }

  private DeleteMsg delMsg(DN delDN, int entryUUID, CSNGenerator gen)
  {
    return new DeleteMsg(delDN, gen.newCSN(), stringUID(entryUUID));
  }

  private ModifyDNMsg modDNMsg(DN dn, String newRDN, int entryUUID, int newSuperiorEntryUUID, CSNGenerator gen)
  {
    return new ModifyDNMsg(dn, gen.newCSN(), stringUID(entryUUID), stringUID(newSuperiorEntryUUID), true, null, newRDN);
  }

  /**
   * Check the dependency between moddn and delete operation
   * when an entry is renamed to a new dn and then deleted.
   * Disabled: need investigations to fix random failures
   */
  @Test(enabled=false)
  public void moddnDelDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    LDAPReplicationDomain domain = null;
    DN baseDN = TEST_ROOT_DN;
    int brokerId = 2;
    int serverId = 1;
    int replServerId = 82;

    cleanDB();

    try
    {
      // Create replication server, replication domain and broker.
      Entry entry = TestCaseUtils.makeEntry(
          "dn:" + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: organization");

      CSNGenerator gen = new CSNGenerator(brokerId, 0L);
      int renamedEntryUuid = 100;

      replServer = newReplicationServer(replServerId, 200, "dependencyTestModdnDelDependencyTestDb");

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      Thread.sleep(2000);
      domain = startNewLDAPReplicationDomain(replServer, baseDN, serverId, 100000);

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 1000, replServer.getReplicationPort(), 1000, CLEAN_DB_GENERATION_ID);

      // add an entry to play with.
      entry.removeAttribute(getEntryUUIDAttributeType());
      entry.addAttribute(Attributes.create("entryuuid",
                         stringUID(renamedEntryUuid)),
                         new LinkedList<ByteString>());
      DN addDN = DN.valueOf("dc=moddndel" + "," + TEST_ROOT_DN_STRING);
      broker.publish(addMsg(addDN, entry, renamedEntryUuid, 1, gen));

      // check that the entry was correctly added
      checkEntryHasAttributeValue(addDN, "entryuuid", stringUID(renamedEntryUuid), 30, "The initial entry add failed");

      // disable the domain to make sure that the messages are all sent in a row.
      domain.disable();

      // rename and delete the entry.
      broker.publish(modDNMsg(addDN, "dc=new_name", renamedEntryUuid, 1, gen));
      DN delDN = DN.valueOf("dc=new_name" + "," + TEST_ROOT_DN_STRING);
      broker.publish(delMsg(delDN, renamedEntryUuid, gen));

      // enable back the domain to trigger message replay.
      domain.enable();

      // check that entry does not exist anymore.
      checkEntryHasNoSuchAttributeValue(DN.valueOf("dc=new_name" + "," + TEST_ROOT_DN_STRING), "entryuuid",
          stringUID(renamedEntryUuid), 30, "The delete dependencies was not correctly enforced");
    }
    finally
    {
      remove(replServer);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
  }

  /**
   * Clean the database and replace with a single entry.
   *
   * @throws FileNotFoundException
   * @throws IOException
   * @throws Exception
   */
  private void cleanDB() throws FileNotFoundException, IOException, Exception
  {
    // Clear backend
    TestCaseUtils.initializeTestBackend(false);

    // Create top entry with uuid
    Entry topEntry = TestCaseUtils.makeEntry(
        "dn:" + TEST_ROOT_DN_STRING,
         "objectClass: top",
         "objectClass: organization",
         "o: test",
         "entryuuid: " + stringUID(1));

    MemoryBackend memoryBackend =
        (MemoryBackend) TestCaseUtils.getServerContext().getBackendConfigManager().getLocalBackendById(TEST_BACKEND_ID);
    memoryBackend.addEntry(topEntry, null);
  }


  /**
   * Check that after a sequence of add/del/add done on the same DN
   * the second entry is in the database.
   * The unique id of the entry is used to check that the correct entry
   * has been added.
   * To increase the risks of failures a loop of add/del/add is done.
   */
  @Test(enabled=true, groups="slow")
  public void addDelAddDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    LDAPReplicationDomain domain = null;
    DN baseDN = TEST_ROOT_DN;
    int brokerId = 2;
    int serverId = 1;
    int replServerId = 83;
    int addSequenceLength = 30;

    cleanDB();

    try
    {
      Entry entry = TestCaseUtils.makeEntry(
          "dn:" + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: organization");

      replServer = newReplicationServer(
          replServerId, 5 * addSequenceLength + 100, "dependencyTestAddDelAddDependencyTestDb");

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 100, replServer.getReplicationPort(), 1000, CLEAN_DB_GENERATION_ID);

      // send a sequence of add/del/add operations
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);

      int sequence;
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        // add the entry a first time
        entry.removeAttribute(getEntryUUIDAttributeType());
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)),
                           new LinkedList<ByteString>());
        DN addDN = DN.valueOf("dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING);
        broker.publish(addMsg(addDN, entry, sequence + 1, 1, gen));
        broker.publish(delMsg(addDN, sequence + 1, gen));

        // add again the entry with a new entryuuid.
        entry.removeAttribute(getEntryUUIDAttributeType());
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1025)),
                           new LinkedList<ByteString>());
        broker.publish(addMsg(addDN, entry, sequence + 1025, 1, gen));
      }

      domain = startNewLDAPReplicationDomain(replServer, baseDN, serverId, -1);

      // check that all entries have been deleted and added
      // again by checking that they do have the correct entryuuid
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        String addDn = "dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING;
        checkEntryHasAttributeValue(DN.valueOf(addDn), "entryuuid", stringUID(sequence + 1025), 30,
            "The second add was not replayed on entry " + addDn);
      }

      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        DN deleteDN = DN.valueOf("dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING);
        broker.publish(delMsg(deleteDN, sequence + 1025, gen));
      }

      // check that the database was cleaned successfully
      DN node1 = DN.valueOf("dc=dependency1," + TEST_ROOT_DN_STRING);
      Entry baseEntry = getEntry(node1, 30000, false);
      assertNull(baseEntry,
        "The entry were not removed succesfully after test completion.");
    }
    finally
    {
      remove(replServer);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
  }

  private ReplicationServer newReplicationServer(int replServerId, int windowSize, String dirName) throws Exception
  {
    int replServerPort = TestCaseUtils.findFreePort();
    return new ReplicationServer(new ReplServerFakeConfiguration(
        replServerPort, dirName, 0, replServerId, 0, windowSize, null));
  }

  private LDAPReplicationDomain startNewLDAPReplicationDomain(ReplicationServer replServer, DN baseDN, int serverId,
      int heartBeatInterval) throws ConfigException
  {
    SortedSet<String> replServers = newTreeSet("localhost:" + replServer.getReplicationPort());
    DomainFakeCfg domainConf = new DomainFakeCfg(baseDN, serverId, replServers);
    if (heartBeatInterval > 0)
    {
      domainConf.setHeartbeatInterval(heartBeatInterval);
    }
    LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(domainConf);
    domain.start();
    return domain;
  }

  /**
   * Check that the dependency of moddn operation are working by
   * issuing a set of Add operation followed by a modrdn of the added entry.
   */
  @Test(enabled=true, groups="slow")
  public void addModdnDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    LDAPReplicationDomain domain = null;
    DN baseDN = TEST_ROOT_DN;
    int brokerId = 2;
    int serverId = 1;
    int replServerId = 84;
    int addSequenceLength = 51 * 10; //exceed late queue threshold in org.opends.server.replication.server.MessageHandler

    cleanDB();

    try
    {
      Entry entry = TestCaseUtils.makeEntry(
          "dn:" + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: organization");

      replServer = newReplicationServer(
          replServerId, 5 * addSequenceLength + 100, "dependencyTestAddModdnDependencyTestDb");

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 100, replServer.getReplicationPort(), 1000, CLEAN_DB_GENERATION_ID);


      DN addDN = TEST_ROOT_DN;
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);

      // send a sequence of add/modrdn operations
      int sequence;
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        // add the entry
        entry.removeAttribute(getEntryUUIDAttributeType());
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)),
                           new LinkedList<ByteString>());
        addDN = DN.valueOf("dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING);
        broker.publish(addMsg(addDN, entry, sequence + 1, 1, gen));

        // rename the entry
        broker.publish(modDNMsg(addDN, "dc=new_dep" + sequence, sequence + 1, 1, gen));
      }

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      domain = startNewLDAPReplicationDomain(replServer, baseDN, serverId, -1);

      // check that all entries have been renamed
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=new_dep" + sequence + "," + TEST_ROOT_DN_STRING);
        Entry baseEntry = getEntry(addDN, 30000, true);
        assertNotNull(baseEntry,
          "The rename was not applied correctly on :" + addDN);
      }

      // delete the entries to clean the database.
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=new_dep" + sequence + "," + TEST_ROOT_DN_STRING);
        broker.publish(delMsg(addDN, sequence + 1, gen));
      }
    }
    finally
    {
      remove(replServer);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
  }

  /**
   * Builds and return a uuid from an integer.
   * This methods assume that unique integers are used and does not make any
   * unicity checks. It is only responsible for generating a uid with a
   * correct syntax.
   */
  private String stringUID(int i)
  {
    return String.format("11111111-1111-1111-1111-%012x", i);
  }

  /**
   * Check that a sequence of dependents adds and mods is correctly ordered:
   * Using a deep dit :
   *    TEST_ROOT_DN_STRING
   *       |
   *    dc=dependency1
   *       |
   *    dc=dependency2
   *       |
   *    dc=dependency3
   *       |
   *
   *       |
   *    dc=dependencyN
   *
   * This test sends a sequence of ADD operations to build such a dit.
   * But every 4 entry is sent as if from a different ReplicaID.
   * It expects all entries under each other (no conflict due to missing parent).
   * This test was built to exercise OPENDJ-3343
   *
   */
  @Test(enabled=false)
  public void addMultipleServersDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    LDAPReplicationDomain domain = null;
    DN baseDN = TEST_ROOT_DN;
    int brokerId = 2;
    int serverId = 1;
    int replServerId = 81;
    int addSequenceLength = 30;


    cleanDB();

    try
    {
      /*
       * Check that a sequence of dependent ADD is correctly ordered.
       *
       * - Create replication server
       * - Send sequence of ADD messages to the replication server
       * - Configure replication server
       * - check that the last entry has been correctly added
       */
      Entry entry = TestCaseUtils.makeEntry(
              "dn:" + TEST_ROOT_DN_STRING,
              "objectClass: top",
              "objectClass: organization",
              "entryuuid: " + stringUID(1));

      replServer = newReplicationServer(
              replServerId, addSequenceLength * 5 + 100, "dependencyTestAddModDelDependencyTestDb");

      ReplicationBroker broker = openReplicationSession(
              baseDN, brokerId, 1000, replServer.getReplicationPort(), 1000, CLEAN_DB_GENERATION_ID);

      Thread.sleep(2000);

      // send a sequence of add operation
      DN addDN = TEST_ROOT_DN;
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);
      CSNGenerator gen2 = new CSNGenerator(brokerId + 10, 0L);
      ArrayList<ByteString> dupValues = new ArrayList<>();

      int sequence;
      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        entry.removeAttribute(getEntryUUIDAttributeType());
        entry.removeAttribute(getDescriptionAttributeType());
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)), (Collection<ByteString>)dupValues);
        entry.addAttribute(Attributes.create("description", "test" + sequence), (Collection<ByteString>)dupValues);
        addDN = DN.valueOf("dc=dependency" + sequence + "," + addDN);
        // Every 4 entry has a CSN from a different ID
        broker.publish(addMsg(addDN, entry, sequence + 1, sequence, sequence %4 == 0 ? gen2 : gen));
      }

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      domain = startNewLDAPReplicationDomain(replServer, baseDN, serverId, 100000);

      sleep(10000);

      // Check that all the entries have been replayed properly (no conflict)
      // (all the entries should have a description)
      addDN = TEST_ROOT_DN;

      // This is just debugging output to visualize the problem
      dumpAllEntries(addDN);

      for (sequence = 1; sequence<=addSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=dependency" + sequence + "," + addDN);
        Entry e = getEntry(addDN, 10000, true);
        assertNotNull(e, "The following entry was not added properly: " + addDN);
        // checkEntryHasAttributeValue(
        //        addDN, "description", "test" + sequence, 10, "Replication Error with entry " + addDN);
      }
    }
    finally
    {
      remove(replServer);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
  }

  private void dumpAllEntries(DN aDN) throws DirectoryException
  {
    final SearchRequest request = newSearchRequest(aDN, SearchScope.WHOLE_SUBTREE, "&");
    InternalSearchOperation searchOperation = connection.processSearch(request);
    ResultCode res = searchOperation.getResultCode();
    assertEquals(res, ResultCode.SUCCESS, "Error while searching all entries for " + aDN);
    for (Entry entry : searchOperation.getSearchEntries())
    {
      System.out.println(entry.toString());
    }
  }
}
