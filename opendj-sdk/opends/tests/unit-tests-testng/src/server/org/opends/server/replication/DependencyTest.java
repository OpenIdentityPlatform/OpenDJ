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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import static org.testng.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.TimeThread;
import org.testng.annotations.*;

/**
 * Test that the dependencies are computed correctly when replaying
 * sequences of operations that requires to follow a given order
 * such as : ADD an entry, ADD a children entry.
 */
public class DependencyTest extends ReplicationTestCase
{
  private static final String BASEDN_STRING = "dc=example,dc=com";

  /**
   * Check that a sequence of dependents adds and mods is correctly ordered:
   * Using a deep dit :
   * dc=example,dc=com
   *       |
   *    dc=dependency1
   *       |
   *    dc=dependency2
   *       |
   *    dc=dependency2
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
  @SuppressWarnings("unchecked")
  @Test(groups="slow")
  public void addModDelDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    ReplicationDomain domain = null;
    DN baseDn = DN.decode(BASEDN_STRING);
    SynchronizationProvider replicationPlugin = null;
    short brokerId = 2;
    short serverId = 1;
    short replServerId = 1;
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
        "dn:" + BASEDN_STRING + "\n"
         + "objectClass: top\n"
         + "objectClass: domain\n"
         + "entryuuid: " + stringUID(1) + "\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");

      // find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort = socket.getLocalPort();
      socket.close();

      replicationPlugin = new MultimasterReplication();
      DirectoryServer.registerSynchronizationProvider(replicationPlugin);
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "addModDeldependency",
                                        0, replServerId, 0,
                                        AddSequenceLength*5+100, null);
      replServer = new ReplicationServer(conf);

      ReplicationBroker broker =
        openReplicationSession(baseDn, brokerId, 1000, replServerPort, 1000,
                               false);

      TimeThread.sleep(2000);
      // send a sequence of add operation

      String addDn = BASEDN_STRING;
      ChangeNumberGenerator gen = new ChangeNumberGenerator(brokerId, 0L);

      int sequence;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        entry.removeAttribute(uidType);
        entry.addAttribute(new Attribute("entryuuid", stringUID(sequence+1)),
                           new LinkedList<AttributeValue>());
        addDn = "dc=dependency" + sequence + "," + addDn;
        AddMsg addMsg =
          new AddMsg(gen.newChangeNumber(), addDn, stringUID(sequence+1),
                     stringUID(sequence),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);

        ModifyMsg modifyMsg =
          new ModifyMsg(gen.newChangeNumber(), DN.decode(addDn),
                        generatemods("description", "test"),
                        stringUID(sequence+1));
        broker.publish(modifyMsg);
      }

      // configure and start replication of dc=example,dc=com on the server
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);
      domainConf.setHeartbeatInterval(100000);

      domain = MultimasterReplication.createNewDomain(domainConf);

      // check that last entry in sequence got added.
      Entry lastEntry = getEntry(DN.decode(addDn), 30000, true);
      assertNotNull(lastEntry,
                    "The last entry of the ADD sequence was not added.");

      // Check that all the modify have been replayed
      // (all the entries should have a description).
      addDn = BASEDN_STRING;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDn = "dc=dependency" + sequence + "," + addDn;

        boolean found =
          checkEntryHasAttribute(DN.decode(addDn), "description", "test",
                                 10000, true);
        if (!found)
        {
          fail("The modification was not replayed on entry " + addDn);
        }
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

      DN deleteDN = DN.decode(addDn);
      while (sequence-->1)
      {
        DeleteMsg delMsg = new DeleteMsg(deleteDN.toString(),
                                         gen.newChangeNumber(),
                                         stringUID(sequence + 1));
        broker.publish(delMsg);
        deleteDN = deleteDN.getParent();
      }

      domain.enable();

      // check that entry just below the base entry was deleted.
      // (we can't delete the base entry because some other tests might
      // have added other children)
      DN node1 = DN.decode("dc=dependency1," + BASEDN_STRING);
      Entry baseEntry = getEntry(node1, 30000, false);
      assertNull(baseEntry,
                 "The last entry of the DEL sequence was not deleted.");
    }
    finally
    {
      if (replServer != null)
        replServer.shutdown();

      if (domain != null)
        MultimasterReplication.deleteDomain(baseDn);

      if (replicationPlugin != null)
        DirectoryServer.deregisterSynchronizationProvider(replicationPlugin);
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
    String baseentryldif =
      "dn:" + BASEDN_STRING + "\n"
       + "objectClass: top\n"
       + "objectClass: domain\n"
       + "dc: example\n"
       + "entryuuid: " + stringUID(1) + "\n";


      // Initialization :
      // Load the database with a single entry :
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = buildRoot + File.separator + "build" +
                    File.separator + "unit-tests" + File.separator +
                    "package"+ File.separator + "addModDelDependencyTest";
      OutputStream out = new FileOutputStream(new File(path));
      out.write(baseentryldif.getBytes());

      task("dn: ds-task-id=" + UUID.randomUUID()
          + ",cn=Scheduled Tasks,cn=Tasks\n"
          + "objectclass: top\n"
          + "objectclass: ds-task\n"
          + "objectclass: ds-task-import\n"
          + "ds-task-class-name: org.opends.server.tasks.ImportTask\n"
          + "ds-task-import-backend-id: userRoot\n"
          + "ds-task-import-ldif-file: " + path + "\n"
          + "ds-task-import-reject-file: " + path + "reject\n");
  }

  /**
   * Check that after a sequence of add/del/add done on the same DN
   * the second entry is in the database.
   * The unique id of the entry is used to check that the correct entry
   * has been added.
   * To increase the risks of failures a loop of add/del/add is done.
   */
  @SuppressWarnings("unchecked")
  @Test(groups="slow")
  public void addDelAddDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    ReplicationDomain domain = null;
    DN baseDn = DN.decode(BASEDN_STRING);
    SynchronizationProvider replicationPlugin = null;
    short brokerId = 2;
    short serverId = 1;
    short replServerId = 1;
    int AddSequenceLength = 30;

    cleanDB();

    try
    {
      String entryldif = "dn:" + BASEDN_STRING + "\n"
      + "objectClass: top\n"
      + "objectClass: domain\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");

      // find a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort = socket.getLocalPort();
      socket.close();

      replicationPlugin = new MultimasterReplication();
      DirectoryServer.registerSynchronizationProvider(replicationPlugin);
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "addDelAdddependency", 0,
                                        replServerId,
                                        0, 5*AddSequenceLength+100, null);
      replServer = new ReplicationServer(conf);

      ReplicationBroker broker =
        openReplicationSession(baseDn, brokerId, 100, replServerPort, 1000,
                               false);

      // send a sequence of add/del/add operations

      String addDn = BASEDN_STRING;
      ChangeNumberGenerator gen = new ChangeNumberGenerator(brokerId, 0L);

      int sequence;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        // add the entry a first time
        entry.removeAttribute(uidType);
        entry.addAttribute(new Attribute("entryuuid", stringUID(sequence+1)),
                           new LinkedList<AttributeValue>());
        addDn = "dc=dependency" + sequence + "," + addDn;
        AddMsg addMsg =
          new AddMsg(gen.newChangeNumber(), addDn, stringUID(sequence+1),
                     stringUID(sequence == 1 ? sequence : sequence +1000),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);

        // delete the entry
        DeleteMsg delMsg = new DeleteMsg(addDn, gen.newChangeNumber(),
                                         stringUID(sequence+1));
        broker.publish(delMsg);

        // add again the entry with a new entryuuid.
        entry.removeAttribute(uidType);
        entry.addAttribute(new Attribute("entryuuid", stringUID(sequence+1001)),
                           new LinkedList<AttributeValue>());
        addMsg =
          new AddMsg(gen.newChangeNumber(), addDn, stringUID(sequence+1001),
                     stringUID(sequence == 1 ? sequence : sequence +1000),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);
      }

      // configure and start replication of dc=example,dc=com on the server
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);

      domain = MultimasterReplication.createNewDomain(domainConf);

      // check that all entries have been deleted and added
      // again by checking that they do have the correct entryuuid
      addDn = BASEDN_STRING;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDn = "dc=dependency" + sequence + "," + addDn;

        boolean found =
          checkEntryHasAttribute(DN.decode(addDn), "entryuuid",
                                 stringUID(sequence+1001),
                                 30000, true);
        if (!found)
        {
          fail("The second add was not replayed on entry " + addDn);
        }
      }

      DN deleteDN = DN.decode(addDn);
      while (sequence-->1)
      {
        DeleteMsg delMsg = new DeleteMsg(deleteDN.toString(),
                                         gen.newChangeNumber(),
                                         stringUID(sequence + 1001));
        broker.publish(delMsg);
        deleteDN = deleteDN.getParent();
      }

      // check that the database was cleaned successfully
      DN node1 = DN.decode("dc=dependency1," + BASEDN_STRING);
      Entry baseEntry = getEntry(node1, 30000, false);
      assertNull(baseEntry,
        "The entry were not removed succesfully after test completion.");

    }
    finally
    {
      if (replServer != null)
        replServer.shutdown();

      if (domain != null)
        MultimasterReplication.deleteDomain(baseDn);

      if (replicationPlugin != null)
        DirectoryServer.deregisterSynchronizationProvider(replicationPlugin);
    }
  }

  /**
   * Check that the dependency of moddn operation are working by
   * issuing a set of Add operation followed by a modrdn of the added entry.
   */
  @SuppressWarnings("unchecked")
  @Test(groups="slow")
  public void addModdnDependencyTest() throws Exception
  {
    ReplicationServer replServer = null;
    ReplicationDomain domain = null;
    DN baseDn = DN.decode(BASEDN_STRING);
    SynchronizationProvider replicationPlugin = null;
    short brokerId = 2;
    short serverId = 1;
    short replServerId = 1;
    int AddSequenceLength = 30;

    cleanDB();

    try
    {
      String entryldif = "dn:" + BASEDN_STRING + "\n"
      + "objectClass: top\n"
      + "objectClass: domain\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryldif);
      AttributeType uidType =
        DirectoryServer.getSchema().getAttributeType("entryuuid");

      // find a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort = socket.getLocalPort();
      socket.close();

      replicationPlugin = new MultimasterReplication();
      DirectoryServer.registerSynchronizationProvider(replicationPlugin);
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, "addModdndependency", 0,
                                        replServerId,
                                        0, 5*AddSequenceLength+100, null);
      replServer = new ReplicationServer(conf);

      ReplicationBroker broker =
        openReplicationSession(baseDn, brokerId, 100, replServerPort, 1000,
                               false);


      String addDn = BASEDN_STRING;
      ChangeNumberGenerator gen = new ChangeNumberGenerator(brokerId, 0L);

      // send a sequence of add/modrdn operations
      int sequence;
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        // add the entry
        entry.removeAttribute(uidType);
        entry.addAttribute(new Attribute("entryuuid", stringUID(sequence+1)),
                           new LinkedList<AttributeValue>());
        addDn = "dc=dependency" + sequence + "," + BASEDN_STRING;
        AddMsg addMsg =
          new AddMsg(gen.newChangeNumber(), addDn, stringUID(sequence+1),
                     stringUID(1),
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );
        broker.publish(addMsg);

        // rename the entry
        ModifyDNMsg moddnMsg =
          new ModifyDNMsg(addDn, gen.newChangeNumber(), stringUID(sequence+1),
                          stringUID(1), true, null, "dc=new_dep" + sequence);
        broker.publish(moddnMsg);
      }

      // configure and start replication of dc=example,dc=com on the server
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:"+replServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);

      domain = MultimasterReplication.createNewDomain(domainConf);

      // check that all entries have been renamed
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDn = "dc=new_dep" + sequence + "," + BASEDN_STRING;

        Entry baseEntry = getEntry(DN.decode(addDn), 30000, true);
        assertNotNull(baseEntry,
          "The rename was not applied correctly on :" + addDn);
      }

      // delete the entries to clean the database.
      for (sequence = 1; sequence<=AddSequenceLength; sequence ++)
      {
        addDn = "dc=new_dep" + sequence + "," + BASEDN_STRING;
        DeleteMsg delMsg = new DeleteMsg(addDn.toString(),
                                         gen.newChangeNumber(),
                                         stringUID(sequence + 1001));
        broker.publish(delMsg);
      }
    }
    finally
    {
      if (replServer != null)
        replServer.shutdown();

      if (domain != null)
        MultimasterReplication.deleteDomain(baseDn);

      if (replicationPlugin != null)
        DirectoryServer.deregisterSynchronizationProvider(replicationPlugin);
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
