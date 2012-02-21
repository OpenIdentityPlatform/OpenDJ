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
 *      Portions Copyright 2011-2012 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.io.File;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

/**
 * Test the usage of the historical data of the replication.
 */
public class HistoricalCsnOrderingTest
       extends ReplicationTestCase
{
  final int serverId = 123;

  public class TestBroker extends ReplicationBroker
  {
    LinkedList<ReplicationMsg> list = null;

    public TestBroker(LinkedList<ReplicationMsg> list)
    {
      super(null, null, null, 0, 0, (long) 0, (long) 0, null, (byte) 0, (long) 0);
      this.list = list;
    }

    public void publishRecovery(ReplicationMsg msg)
    {
      list.add(msg);
    }


  }

  /**
   * Check the basic comparator on the HistoricalCsnOrderingMatchingRule
   */
  @Test()
  public void basicRuleTest()
  throws Exception
  {
    // Creates a rule
    HistoricalCsnOrderingMatchingRule r =
      new HistoricalCsnOrderingMatchingRule();

    ChangeNumber del1 = new ChangeNumber(1,  0,  1);
    ChangeNumber del2 = new ChangeNumber(1,  1,  1);

    ByteString v1 = ByteString.valueOf("a"+":"+del1.toString());
    ByteString v2 = ByteString.valueOf("a"+":"+del2.toString());

    int cmp = r.compareValues(v1, v1);
    assertTrue(cmp == 0);

    cmp = r.compareValues(v1, v2);
    assertTrue(cmp == -1);

    cmp = r.compareValues(v2, v1);
    assertTrue(cmp == 1);
  }

  /**
   * Test that we can retrieve the entries that were missed by
   * a replication server and can  re-build operations from the historical
   * informations.
   */
  @Test()
  public void buildAndPublishMissingChangesOneEntryTest()
  throws Exception
  {
    final int serverId = 123;
    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    // Create Replication Server and Domain
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);

    try
    {
      long startTime = TimeThread.getTime();
    final DN dn1 = DN.decode("cn=test1," + baseDn.toString());
    final AttributeType histType =
      DirectoryServer.getAttributeType(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);

    logError(Message.raw(Category.SYNC, Severity.INFORMATION,
    "Starting replication test : changesCmpTest"));

    // Add the first test entry.
    TestCaseUtils.addEntry(
        "dn: cn=test1," + baseDn.toString(),
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Perform a first modification to update the historical attribute
    int resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDn.toString(),
        "changetype: modify",
        "add: description",
    "description: foo");
    assertEquals(resultCode, 0);

    // Read the entry back to get its historical and included changeNumber
    Entry entry = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs1 = entry.getAttribute(histType);

    assertTrue(attrs1 != null);
    assertTrue(attrs1.isEmpty() != true);

    String histValue =
      attrs1.get(0).iterator().next().getValue().toString();

    logError(Message.raw(Category.SYNC, Severity.INFORMATION,
        "First historical value:" + histValue));

    // Perform a 2nd modification to update the hist attribute with
    // a second value
    resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDn.toString(),
        "changetype: modify",
        "add: description",
    "description: bar");
    assertEquals(resultCode, 0);

    Entry entry2 = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs2 = entry2.getAttribute(histType);

    assertTrue(attrs2 != null);
    assertTrue(attrs2.isEmpty() != true);

    for (AttributeValue av : attrs2.get(0)) {
      logError(Message.raw(Category.SYNC, Severity.INFORMATION,
          "Second historical value:" + av.getValue().toString()));
    }

    LinkedList<ReplicationMsg> opList = new LinkedList<ReplicationMsg>();
    TestBroker session = new TestBroker(opList);

    boolean result =
      rd1.buildAndPublishMissingChanges(
          new ChangeNumber(startTime, 0, serverId),
          session);
    assertTrue(result, "buildAndPublishMissingChanges has failed");
    assertEquals(opList.size(), 3, "buildAndPublishMissingChanges should return 3 operations");
    assertTrue(opList.getFirst().getClass().equals(AddMsg.class));


    // Build a change number from the first modification
    String hv[] = histValue.split(":");
    logError(Message.raw(Category.SYNC, Severity.INFORMATION, hv[1]));
    ChangeNumber fromChangeNumber = new ChangeNumber(hv[1]);

    opList = new LinkedList<ReplicationMsg>();
    session = new TestBroker(opList);

    result =
      rd1.buildAndPublishMissingChanges(
          fromChangeNumber,
          session);
    assertTrue(result, "buildAndPublishMissingChanges has failed");
    assertEquals(opList.size(), 1, "buildAndPublishMissingChanges should return 1 operation");
    assertTrue(opList.getFirst().getClass().equals(ModifyMsg.class));
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
      rs.remove();
      StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 rs.getDbDirName()));
    }
  }

  /**
   * Test that we can retrieve the entries that were missed by
   * a replication server and can  re-build operations from the historical
   * informations.
   */
  @Test()
  public void buildAndPublishMissingChangesSeveralEntriesTest()
  throws Exception
  {
    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    // Create Replication Server and Domain
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    long startTime = TimeThread.getTime();

    try
    {
    logError(Message.raw(Category.SYNC, Severity.INFORMATION,
    "Starting replication test : changesCmpTest"));

    // Add 3 entries.
    String dnTest1 = "cn=test1," + baseDn.toString();
    String dnTest2 = "cn=test2," + baseDn.toString();
    String dnTest3 = "cn=test3," + baseDn.toString();
    TestCaseUtils.addEntry(
        "dn: " + dnTest3,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );
    TestCaseUtils.addEntry(
        "dn: " + dnTest1,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );
    TestCaseUtils.deleteEntry(DN.decode(dnTest3));
    TestCaseUtils.addEntry(
        "dn: " + dnTest2,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Perform modifications on the 2 entries
    int resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test2," + baseDn.toString(),
        "changetype: modify",
        "add: description",
    "description: foo");
    resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDn.toString(),
        "changetype: modify",
        "add: description",
    "description: foo");
    assertEquals(resultCode, 0);

    LinkedList<ReplicationMsg> opList = new LinkedList<ReplicationMsg>();
    TestBroker session = new TestBroker(opList);

    // Call the buildAndPublishMissingChanges and check that this method
    // correctly generates the 4 operations in the correct order.
    boolean result =
      rd1.buildAndPublishMissingChanges(
          new ChangeNumber(startTime, 0, serverId),
          session);
    assertTrue(result, "buildAndPublishMissingChanges has failed");
    assertEquals(opList.size(), 5, "buildAndPublishMissingChanges should return 5 operations");
    ReplicationMsg msg = opList.removeFirst();
    assertTrue(msg.getClass().equals(AddMsg.class));
    assertEquals(((LDAPUpdateMsg) msg).getDn(), dnTest1);
    msg = opList.removeFirst();
    assertTrue(msg.getClass().equals(DeleteMsg.class));
    assertEquals(((LDAPUpdateMsg) msg).getDn(), dnTest3);
    msg = opList.removeFirst();
    assertTrue(msg.getClass().equals(AddMsg.class));
    assertEquals(((LDAPUpdateMsg) msg).getDn(), dnTest2);
    msg = opList.removeFirst();
    assertTrue(msg.getClass().equals(ModifyMsg.class));
    assertEquals(((LDAPUpdateMsg) msg).getDn(), dnTest2);
    msg = opList.removeFirst();
    assertTrue(msg.getClass().equals(ModifyMsg.class));
    assertEquals(((LDAPUpdateMsg) msg).getDn(), dnTest1);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDn);
      rs.remove();
    }
  }

  SortedSet<String> replServers = new TreeSet<String>();
  private ReplicationServer createReplicationServer() throws ConfigException
  {
    int rsPort;
    try
    {
      ServerSocket socket1 = TestCaseUtils.bindFreePort();
      rsPort = socket1.getLocalPort();
      socket1.close();
      replServers.add("localhost:" + rsPort);


      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(rsPort, "HistoricalCsnOrdering",
            0, 1, 0, 100, replServers, 1, 1000, 5000);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      replicationServer.clearDb();
      return replicationServer;
    }
    catch (IOException e)
    {
      fail("Unable to determinate some free ports " +
          stackTraceToSingleLineString(e));
      return null;
    }
  }

  private LDAPReplicationDomain createReplicationDomain(int dsId)
          throws DirectoryException, ConfigException
  {
    DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
    DomainFakeCfg domainConf =
      new DomainFakeCfg(baseDn, dsId, replServers, AssuredType.NOT_ASSURED,
      2, 1, 0, null);
    LDAPReplicationDomain replicationDomain =
      MultimasterReplication.createNewDomain(domainConf);
    replicationDomain.start();

    return replicationDomain;
  }
}
