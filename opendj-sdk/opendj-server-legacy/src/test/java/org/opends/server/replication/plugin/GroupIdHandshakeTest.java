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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationServerCfgDefn.ReplicationDBImplementation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.DN;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Some real connections from clients that should end up with a server with
 * the right groupId if available.
 */
public class GroupIdHandshakeTest extends ReplicationTestCase
{
  private static final int DS1_ID = 1;
  private static final int DS2_ID = 2;
  private static final int RS1_ID = 61;
  private static final int RS2_ID = 62;
  private static final int RS3_ID = 63;
  private int rs1Port = -1;
  private int rs2Port = -1;
  private int rs3Port = -1;
  private LDAPReplicationDomain rd1;
  private LDAPReplicationDomain rd2;
  private ReplicationServer rs1;
  private ReplicationServer rs2;
  private ReplicationServer rs3;

  // The tracer object for the debug logger
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  private void initTest() throws Exception
  {
    rs1Port = -1;
    rs2Port = -1;
    rs3Port = -1;
    rd1 = null;
    rd2 = null;
    rs1 = null;
    rs2 = null;
    rs3 = null;
    findFreePorts();
  }

  private void endTest() throws Exception
  {
    if (rd1 != null)
    {
      rd1.shutdown();
      rd1 = null;
    }

    if (rd2 != null)
    {
      rd2.shutdown();
      rd2 = null;
    }

    // Clear any reference to a domain in synchro plugin
    MultimasterReplication.deleteDomain(DN.valueOf(TEST_ROOT_DN_STRING));
    remove(rs1, rs2, rs3);
    rs1 = rs2 = rs3 = null;
    rs1Port = rs2Port = rs3Port = -1;
  }

  /**
   * Check connection of the provided replication domain to the provided
   * replication server. Waits for connection to be ok up to secTimeout seconds
   * before failing.
   */
  private void checkConnection(int secTimeout, int dsId, int rsId, String msg)
      throws Exception
  {

    int rsPort = -1;
    LDAPReplicationDomain rd = null;
    switch (dsId)
    {
      case DS1_ID:
        rd = rd1;
        break;
      case DS2_ID:
        rd = rd2;
        break;
      default:
        fail("Unknown replication domain server id.");
    }

    switch (rsId)
    {
      case RS1_ID:
        rsPort = rs1Port;
        break;
      case RS2_ID:
        rsPort = rs2Port;
        break;
      case RS3_ID:
        rsPort = rs3Port;
        break;
      default:
        fail("Unknown replication server id.");
    }

    int nSec = 0;

    // Go out of the loop only if connection is verified or if timeout occurs
    while (true)
    {
      // Test connection
      boolean connected = rd.isConnected();
      int rdPort = -1;
      boolean rightPort = false;
      if (connected)
      {
        String serverStr = rd.getReplicationServer();
        rdPort = HostPort.valueOf(serverStr).getPort();
        if (rdPort == rsPort)
        {
          rightPort = true;
        }
      }
      if (connected && rightPort)
      {
        // Connection verified
        debugInfo("checkConnection: connection from domain " + dsId + " to" +
          " replication server " + rsId + " obtained after "
          + nSec + " seconds.");
        return;
      }

      // Sleep 1 second
      Thread.sleep(1000);
      nSec++;

      if (nSec > secTimeout)
      {
        // Timeout reached, end with error
        fail("checkConnection: could not verify connection from domain " + dsId
          + " to replication server " + rsId + " after " + secTimeout + " seconds."
          + " Domain connected: " + connected + ", connection port: " + rdPort
          + " (should be: " + rsPort + "). [" + msg + "]");
      }
    }
  }

  /**
   * Find needed free TCP ports.
   */
  private void findFreePorts() throws IOException
  {
    int[] ports = TestCaseUtils.findFreePorts(3);
    int i = 0;
    rs1Port = ports[i++];
    rs2Port = ports[i++];
    rs3Port = ports[i++];
  }

  /**
   * Creates the list of servers to represent the RS topology matching the
   * passed test case.
   */
  private SortedSet<String> createRSListForTestCase(String testCase)
  {
    SortedSet<String> replServers = new TreeSet<>();

    if (testCase.equals("testRSWithSameGroupIds"))
    {
      // 2 servers used for this test case.
      replServers.add("localhost:" + rs1Port);
      replServers.add("localhost:" + rs2Port);
    } else if (testCase.equals("testRSWithManyGroupIds"))
    {
      // 3 servers used for this test case.
      replServers.add("localhost:" + rs1Port);
      replServers.add("localhost:" + rs2Port);
      replServers.add("localhost:" + rs3Port);
    }
    else
    {
      fail("Unknown test case: " + testCase);
    }

    return replServers;
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(int serverId, int groupId,
      String testCase) throws Exception
  {
    SortedSet<String> replServers = new TreeSet<>();
    int port = -1;
    if (serverId == RS1_ID)
    {
      port = rs1Port;
      if (testCase.equals("testRSWithSameGroupIds"))
      {
        // 2 servers used for this test case.
        replServers.add("localhost:" + rs2Port);
      }
      else if (testCase.equals("testRSWithManyGroupIds"))
      {
        // 3 servers used for this test case.
        replServers.add("localhost:" + rs2Port);
        replServers.add("localhost:" + rs3Port);
      }
      else
      {
        fail("Unknown test case: " + testCase);
      }
    }
    else if (serverId == RS2_ID)
    {
      port = rs2Port;
      if (testCase.equals("testRSWithSameGroupIds"))
      {
        // 2 servers used for this test case.
        replServers.add("localhost:" + rs1Port);
      }
      else if (testCase.equals("testRSWithManyGroupIds"))
      {
        // 3 servers used for this test case.
        replServers.add("localhost:" + rs1Port);
        replServers.add("localhost:" + rs3Port);
      }
      else
      {
        fail("Unknown test case: " + testCase);
      }
    }
    else if (serverId == RS3_ID)
    {
      port = rs3Port;
      if (testCase.equals("testRSWithManyGroupIds"))
      {
        // 3 servers used for this test case.
        replServers.add("localhost:" + rs2Port);
        replServers.add("localhost:" + rs3Port);
      }
      else
      {
        fail("Invalid test case: " + testCase);
      }
    }
    else
    {
      fail("Unknown replication server id.");
    }

    String dir = "groupIdHandshakeTest" + serverId + testCase + "Db";
    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(port, dir, replicationDbImplementation, 0, serverId, 0,
            100, replServers, groupId, 1000, 5000);
    return new ReplicationServer(conf);
  }

  /**
   * Creates a new ReplicationDomain.
   */
  private LDAPReplicationDomain createReplicationDomain(int serverId,
      int groupId, String testCase) throws Exception
  {
    SortedSet<String> replServers = createRSListForTestCase(testCase);
    DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers, groupId);
    LDAPReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);
    replicationDomain.start();
    return replicationDomain;
  }

  /**
   * Connections with RSs that have the same group ids:
   *
   * Full topo is:
   * - RS1 with GID=1
   * - RS2 with GID=1
   * - DS1 with GID=1
   * - DS2 with GID=2
   * Scenario is:
   * - Start RS1 and RS2 with both GID=1
   * - Start DS1 with GID=1 (should connect to a RS with his GID)
   * - Start DS2 with GID=2 (should connect with a RS with wrong GID as only
   * GID=1 is available)
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testRSWithSameGroupIds() throws Exception
  {
    String testCase = "testRSWithSameGroupIds";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /**
       * Start RS1 and RS2 with both GID=1
       */
      // Create and start RS1
      rs1 = createReplicationServer(RS1_ID, 1, testCase);
      // Create and start RS2
      rs2 = createReplicationServer(RS2_ID, 1, testCase);

      /**
       * Start DS1 with GID=1 (should connect to a RS with his GID)
       */
      // Start DS1
      rd1 = createReplicationDomain(DS1_ID, 1, testCase);
      assertTrue(rd1.isConnected());

      /**
       * Start DS2 with GID=2 (should connect with a RS with wrong GID as only
       * GID=1 is available
       */
      // Start DS2
      rd2 = createReplicationDomain(DS2_ID, 2, testCase);
      assertTrue(rd2.isConnected());

    } finally
    {
      endTest();
    }
  }

  /**
   * Test connection algorithm, focusing on group ids. Also test the mechanism
   * that polls replication servers to know if a RS with the right group id
   * becomes available.
   *
   * Full topo is:
   * - RS1 with GID=1
   * - RS2 with GID=2
   * - RS3 with GID=3
   * - DS1 with GID=2
   * - DS2 with GID=3
   * Scenario is:
   * - Start RS1 with GID=1 and RS2 with GID=2
   * - Start DS1 with GID=2, should connect to RS2 with GID=2
   * - Start DS2 with GID=3, should connect to either RS1 or RS2 (no GID=3
   * available)
   * - Start RS3 with GID=3, DS2 with GID=3 should detect server with his GID
   * and connect to RS3
   * - Stop RS2 and RS3, both DS1 and DS2 should failover to RS1 with GID=1
   * (not their group id)
   * - Restart RS2 and RS3, DS1 should reconnect to RS2 (with GID=2, his GID)
   * and DS2 should connect to RS3 (with GID=3, his GID)
   * - Change group id of DS1 and DS2 to 1 : they should reconnect to RS1
   * - Change group id of RS3 to 1
   * - Change group id of RS1 to 3: DS1 and DS2 should reconnect to RS3
   * - Change group id of DS1 and DS2 to 3 : they should reconnect to RS1
   * @throws Exception If a problem occurred
   */
  @Test (groups = "slow")
  public void testRSWithManyGroupIds() throws Exception
  {
    String testCase = "testRSWithManyGroupIds";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /**
       * Start RS1 with GID=1 and RS2 with GID=2
       */
      // Create and start RS1
      rs1 = createReplicationServer(RS1_ID, 1, testCase);
      // Create and start RS2
      rs2 = createReplicationServer(RS2_ID, 2, testCase);

      /**
       * Start DS1 with GID=2, should connect to RS2 with GID=2
       */
      // Start DS1
      rd1 = createReplicationDomain(DS1_ID, 2, testCase);
      checkConnection(30, DS1_ID, RS2_ID,
        "Start DS1 with GID=2, should connect to RS2 with GID=2");

      /**
       * Start DS2 with GID=3, should connect to either RS1 or RS2 (no GID=3
       * available)
       */
      // Start DS2
      rd2 = createReplicationDomain(DS2_ID, 3, testCase);
      assertTrue(rd2.isConnected());

      /**
       * Start RS3 with GID=3, DS2 with GID=3 should detect server with his GID
       * and connect to RS3
       */
      // Create and start RS3
      rs3 = createReplicationServer(RS3_ID, 3, testCase);
      // Sleep to insure start is done and DS2 has time to detect to server
      // arrival and reconnect
      checkConnection(30, DS2_ID, RS3_ID,
        "Start RS3 with GID=3, DS2 with GID=3 should detect server with his GID and connect to RS3");


      /**
       * Stop RS2 and RS3, both DS1 and DS2 should failover to RS1 with GID=1
       * (not their group id)
       */
      // Simulate RS2 failure
      rs2.remove();
      // Simulate RS3 failure
      rs3.remove();
      // Sleep to insure shutdowns are ok and DS1 and DS2 reconnect to RS1
      checkConnection(30, DS1_ID, RS1_ID,
        "Stop RS2 and RS3, DS1 should failover to RS1 with GID=1");
      checkConnection(30, DS2_ID, RS1_ID,
        "Stop RS2 and RS3, DS2 should failover to RS1 with GID=1");

      /**
       * Restart RS2 and RS3, DS1 should reconnect to RS2 (with GID=2, his GID)
       * and DS2 should reconnect to RS3 (with GID=3, his GID)
       */
      // RS2 restart
      rs2 = createReplicationServer(RS2_ID, 2, testCase);
      // RS3 restart
      rs3 = createReplicationServer(RS3_ID, 3, testCase);
      // Sleep to insure restarts are ok and DS1 and DS2 reconnect to the RS with
      // their group id
      checkConnection(30, DS1_ID, RS2_ID,
        "Restart RS2 and RS3, DS1 should reconnect to RS2 (with GID=2, his GID)");
      checkConnection(30, DS2_ID, RS3_ID,
        "Restart RS2 and RS3, DS2 should reconnect to RS3 (with GID=3, his GID)");

      //
      // ENTERING CHANGE CONFIG TEST PART
      //

      /**
       * Change group id of DS1 and DS2 to 1 and see them reconnect to RS1
       */
      SortedSet<String> replServers = createRSListForTestCase(testCase);
      DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
      DomainFakeCfg domainConfWithNewGid =  new DomainFakeCfg(baseDn, DS1_ID, replServers, 1);
      rd1.applyConfigurationChange(domainConfWithNewGid);
      domainConfWithNewGid = new DomainFakeCfg(baseDn, DS2_ID, replServers, 1);
      rd2.applyConfigurationChange(domainConfWithNewGid);
      checkConnection(30, DS1_ID, RS1_ID,
        "Change GID of DS1 to 1, it should reconnect to RS1 with GID=1");
      checkConnection(30, DS2_ID, RS1_ID,
        "Change GID of DS2 to 1, it should reconnect to RS1 with GID=1");

      /**
       * Change group id of RS3 to 1
       */
      SortedSet<String> otherReplServers = new TreeSet<>();
      otherReplServers.add("localhost:" + rs1Port);
      otherReplServers.add("localhost:" + rs2Port);
      String dir = "groupIdHandshakeTest" + RS3_ID + testCase + "Db";
      ReplServerFakeConfiguration rsConfWithNewGid =
        new ReplServerFakeConfiguration(rs3Port, dir, replicationDbImplementation, 0, RS3_ID, 0,
        100, otherReplServers, 1, 1000, 5000);
      rs3.applyConfigurationChange(rsConfWithNewGid);

      /**
       * Change group id of RS1 to 3: DS1 and DS2 should reconnect to RS3
       */
      otherReplServers = new TreeSet<>();
      otherReplServers.add("localhost:" + rs2Port);
      otherReplServers.add("localhost:" + rs3Port);
      dir = "groupIdHandshakeTest" + RS1_ID + testCase + "Db";
      rsConfWithNewGid = new ReplServerFakeConfiguration(rs1Port, dir, ReplicationDBImplementation.JE, 0,
        RS1_ID, 0, 100, otherReplServers, 3, 1000, 5000);
      rs1.applyConfigurationChange(rsConfWithNewGid);
      checkConnection(30, DS1_ID, RS3_ID,
        "Change GID of RS3 to 1 and RS1 to 3, DS1 should reconnect to RS3 with GID=1");
      checkConnection(30, DS2_ID, RS3_ID,
        "Change GID of RS3 to 1 and RS1 to 3, DS2 should reconnect to RS3 with GID=1");

      /**
       * Change group id of DS1 and DS2 to 3 : they should reconnect to RS1
       */
      domainConfWithNewGid = new DomainFakeCfg(baseDn, DS1_ID, replServers, 3);
      rd1.applyConfigurationChange(domainConfWithNewGid);
      domainConfWithNewGid = new DomainFakeCfg(baseDn, DS2_ID, replServers, 3);
      rd2.applyConfigurationChange(domainConfWithNewGid);
      checkConnection(30, DS1_ID, RS1_ID,
        "Change GID of DS1 to 3, it should reconnect to RS1 with GID=3");
      checkConnection(30, DS2_ID, RS1_ID,
        "Change GID of DS2 to 3, it should reconnect to RS1 with GID=3");

    } finally
    {
      endTest();
    }
  }
}
