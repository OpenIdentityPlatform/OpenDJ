/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.core.DirectoryServer;
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
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

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
    int AddSequenceLength = 30;


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

      String entryldif =
        "dn:" + TEST_ROOT_DN_STRING + "\n"
         + "objectClass: top\n"
         + "objectClass: organization\n"
         + "entryuuid: " + stringUID(1) + "\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");

      int replServerPort = TestCaseUtils.findFreePort();

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "dependencyTestAddModDelDependencyTestDb",
                                        replicationDbImplementation, 0, replServerId,
                                        0, AddSequenceLength*5+100, null);
      replServer = new ReplicationServer(conf);

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 1000, replServerPort, 1000, CLEAN_DB_GENERATION_ID);

      Thread.sleep(2000);
      // send a sequence of add operation

      DN addDN = TEST_ROOT_DN;
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);

      int sequence;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        entry.removeAttribute(uidType);
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)),
                           new LinkedList<ByteString>());
        addDN = DN.valueOf("dc=dependency" + sequence + "," + addDN);
        AddMsg addMsg =
          new AddMsg(gen.newCSN(), addDN, stringUID(sequence+1),
                     stringUID(sequence),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);

        ModifyMsg modifyMsg =
          new ModifyMsg(gen.newCSN(), addDN,
                        generatemods("description", "test"),
                        stringUID(sequence+1));
        broker.publish(modifyMsg);
      }

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      SortedSet<String> replServers = new TreeSet<>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf = new DomainFakeCfg(baseDN, serverId, replServers);
      domainConf.setHeartbeatInterval(100000);

      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      // check that last entry in sequence got added.
      Entry lastEntry = getEntry(addDN, 30000, true);
      assertNotNull(lastEntry,
                    "The last entry of the ADD sequence was not added.");

      // Check that all the modify have been replayed
      // (all the entries should have a description).
      addDN = TEST_ROOT_DN;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=dependency" + sequence + "," + addDN);

        boolean found = checkEntryHasAttribute(addDN, "description", "test", 10000, true);
        assertTrue(found, "The modification was not replayed on entry " + addDN);
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
      Thread.sleep(2000);  // necesary because disable does not wait
                           // for full termination of all threads. (issue 1571)

      DN deleteDN = addDN;
      while (sequence-->1)
      {
        DeleteMsg delMsg = new DeleteMsg(deleteDN, gen.newCSN(), stringUID(sequence + 1));
        broker.publish(delMsg);
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
      String entryldif = "dn:" + TEST_ROOT_DN_STRING + "\n"
      + "objectClass: top\n"
      + "objectClass: organization\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);
      int renamedEntryUuid = 100;

      int replServerPort = TestCaseUtils.findFreePort();

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "dependencyTestModdnDelDependencyTestDb",
                                        replicationDbImplementation, 0, replServerId,
                                        0, 200, null);
      replServer = new ReplicationServer(conf);

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      SortedSet<String> replServers = new TreeSet<>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf = new DomainFakeCfg(baseDN, serverId, replServers);
      domainConf.setHeartbeatInterval(100000);

      Thread.sleep(2000);
      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 1000, replServerPort, 1000, CLEAN_DB_GENERATION_ID);

      // add an entry to play with.
      entry.removeAttribute(uidType);
      entry.addAttribute(Attributes.create("entryuuid",
                         stringUID(renamedEntryUuid)),
                         new LinkedList<ByteString>());
      DN addDN = DN.valueOf("dc=moddndel" + "," + TEST_ROOT_DN_STRING);
      AddMsg addMsg =
          new AddMsg(gen.newCSN(), addDN, stringUID(renamedEntryUuid),
                   stringUID(1),
                   entry.getObjectClassAttribute(),
                   entry.getAttributes(), null );

      broker.publish(addMsg);

      // check that the entry was correctly added
      boolean found = checkEntryHasAttribute(addDN, "entryuuid", stringUID(renamedEntryUuid), 30000, true);
      assertTrue(found, "The initial entry add failed");


      // disable the domain to make sure that the messages are
      // all sent in a row.
      domain.disable();

      // rename and delete the entry.
      ModifyDNMsg moddnMsg =
          new ModifyDNMsg(addDN, gen.newCSN(),
                        stringUID(renamedEntryUuid),
                        stringUID(1), true, null, "dc=new_name");
      broker.publish(moddnMsg);
      DeleteMsg delMsg =
        new DeleteMsg(DN.valueOf("dc=new_name" + "," + TEST_ROOT_DN_STRING),
                      gen.newCSN(), stringUID(renamedEntryUuid));
      broker.publish(delMsg);

      // enable back the domain to trigger message replay.
      domain.enable();

      // check that entry does not exist anymore.
      Thread.sleep(10000);
      found = checkEntryHasAttribute(DN.valueOf("dc=new_name" + "," + TEST_ROOT_DN_STRING),
                                     "entryuuid",
                                     stringUID(renamedEntryUuid),
                                     30000, false);

      assertFalse(found, "The delete dependencies was not correctly enforced");
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
    String baseentryldif =
      "dn:" + TEST_ROOT_DN_STRING + "\n"
       + "objectClass: top\n"
       + "objectClass: organization\n"
       + "o: test\n"
       + "entryuuid: " + stringUID(1) + "\n";
    Entry topEntry = TestCaseUtils.entryFromLdifString(baseentryldif);

    MemoryBackend memoryBackend =
      (MemoryBackend) DirectoryServer.getBackend(TEST_BACKEND_ID);
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
    int AddSequenceLength = 30;

    cleanDB();

    try
    {
      String entryldif = "dn:" + TEST_ROOT_DN_STRING + "\n"
      + "objectClass: top\n"
      + "objectClass: organization\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");

      int replServerPort = TestCaseUtils.findFreePort();

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "dependencyTestAddDelAddDependencyTestDb", replicationDbImplementation,
                                        0, replServerId, 0, 5*AddSequenceLength+100, null);
      replServer = new ReplicationServer(conf);

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 100, replServerPort, 1000, CLEAN_DB_GENERATION_ID);

      // send a sequence of add/del/add operations
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);

      int sequence;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        // add the entry a first time
        entry.removeAttribute(uidType);
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)),
                           new LinkedList<ByteString>());
        DN addDN = DN.valueOf("dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING);
        AddMsg addMsg =
          new AddMsg(gen.newCSN(), addDN, stringUID(sequence+1),
                     stringUID(1),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);

        // delete the entry
        DeleteMsg delMsg = new DeleteMsg(addDN, gen.newCSN(),
                                         stringUID(sequence+1));
        broker.publish(delMsg);

        // add again the entry with a new entryuuid.
        entry.removeAttribute(uidType);
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1025)),
                           new LinkedList<ByteString>());
        addMsg =
          new AddMsg(gen.newCSN(), addDN, stringUID(sequence+1025),
                     stringUID(1),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);
      }

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      SortedSet<String> replServers = new TreeSet<>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf = new DomainFakeCfg(baseDN, serverId, replServers);
      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      // check that all entries have been deleted and added
      // again by checking that they do have the correct entryuuid
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        String addDn = "dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING;

        boolean found =
          checkEntryHasAttribute(DN.valueOf(addDn), "entryuuid",
                                 stringUID(sequence+1025),
                                 30000, true);
        if (!found)
        {
          fail("The second add was not replayed on entry " + addDn);
        }
      }

      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        DN deleteDN = DN.valueOf("dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING);
        DeleteMsg delMsg = new DeleteMsg(deleteDN,
                                         gen.newCSN(),
                                         stringUID(sequence + 1025));
        broker.publish(delMsg);
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
    int AddSequenceLength = 30;

    cleanDB();


    try
    {
      String entryldif = "dn:" + TEST_ROOT_DN_STRING + "\n"
      + "objectClass: top\n"
      + "objectClass: organization\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");

      int replServerPort = TestCaseUtils.findFreePort();

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "dependencyTestAddModdnDependencyTestDb", replicationDbImplementation,
                                        0, replServerId, 0, 5*AddSequenceLength+100, null);
      replServer = new ReplicationServer(conf);

      ReplicationBroker broker = openReplicationSession(
          baseDN, brokerId, 100, replServerPort, 1000, CLEAN_DB_GENERATION_ID);


      DN addDN = TEST_ROOT_DN;
      CSNGenerator gen = new CSNGenerator(brokerId, 0L);

      // send a sequence of add/modrdn operations
      int sequence;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        // add the entry
        entry.removeAttribute(uidType);
        entry.addAttribute(Attributes.create("entryuuid", stringUID(sequence+1)),
                           new LinkedList<ByteString>());
        addDN = DN.valueOf("dc=dependency" + sequence + "," + TEST_ROOT_DN_STRING);
        AddMsg addMsg =
          new AddMsg(gen.newCSN(), addDN, stringUID(sequence+1),
                     stringUID(1),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);

        // rename the entry
        ModifyDNMsg moddnMsg =
          new ModifyDNMsg(addDN, gen.newCSN(), stringUID(sequence+1),
                          stringUID(1), true, null, "dc=new_dep" + sequence);
        broker.publish(moddnMsg);
      }

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      SortedSet<String> replServers = new TreeSet<>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf = new DomainFakeCfg(baseDN, serverId, replServers);
      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      // check that all entries have been renamed
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=new_dep" + sequence + "," + TEST_ROOT_DN_STRING);
        Entry baseEntry = getEntry(addDN, 30000, true);
        assertNotNull(baseEntry,
          "The rename was not applied correctly on :" + addDN);
      }

      // delete the entries to clean the database.
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDN = DN.valueOf("dc=new_dep" + sequence + "," + TEST_ROOT_DN_STRING);
        DeleteMsg delMsg = new DeleteMsg(addDN, gen.newCSN(), stringUID(sequence + 1));
        broker.publish(delMsg);
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

}
