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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.types.DirectoryException;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.DN;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.opends.server.TestCaseUtils.*;

/**
 * Some real connections from clients that should end up with a server with
 * the right groupid if available.
 */
public class GroupIdHandshakeTest extends ReplicationTestCase
{
  private static final short DS1_ID = 1;
  private static final short DS2_ID = 2;
  private static final short RS1_ID = 61;
  private static final short RS2_ID = 62;
  private static final short RS3_ID = 63;
  private int rs1Port = -1;
  private int rs2Port = -1;
  private int rs3Port = -1;
  private LDAPReplicationDomain rd1 = null;
  private LDAPReplicationDomain rd2 = null;
  private ReplicationServer rs1 = null;
  private ReplicationServer rs2 = null;
  private ReplicationServer rs3 = null;

  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }

  private void debugInfo(String message, Exception e)
  {
    debugInfo(message + stackTraceToSingleLineString(e));
  }

  private void initTest()
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

  private void endTest()
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

    try
    {
      // Clear any reference to a domain in synchro plugin
      MultimasterReplication.deleteDomain(DN.decode(TEST_ROOT_DN_STRING));
    } catch (DirectoryException ex)
    {
      fail("Error deleting reference to domain: " + TEST_ROOT_DN_STRING);
    }

    if (rs1 != null)
    {
      rs1.clearDb();
      rs1.remove();
      rs1 = null;
    }

    if (rs2 != null)
    {
      rs2.clearDb();
      rs2.remove();
      rs2 = null;
    }
    if (rs3 != null)
    {
      rs3.clearDb();
      rs3.remove();
      rs3 = null;
    }
    rs1Port = -1;
    rs2Port = -1;
    rs3Port = -1;
  }


  /**
   * Check connection of the provided replication domain to the provided
   * replication server. Waits for connection to be ok up to secTimeout seconds
   * before failing.
   */
  private void checkConnection(int secTimeout, short dsId, short rsId, String msg)
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
        int index = serverStr.lastIndexOf(':');
        if ((index == -1) || (index >= serverStr.length()))
          fail("Enable to find port number in: " + serverStr);
        String rdPortStr = serverStr.substring(index + 1);
        try
        {
          rdPort = (new Integer(rdPortStr)).intValue();
        } catch (Exception e)
        {
          fail("Enable to get an int from: " + rdPortStr);
        }
        if (rdPort == rsPort)
          rightPort = true;
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
      try
      {
        Thread.sleep(1000);
      } catch (InterruptedException ex)
      {
        fail("Error sleeping " + stackTraceToSingleLineString(ex));
      }
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
  private void findFreePorts()
  {
    try
    {
      ServerSocket socket1 = TestCaseUtils.bindFreePort();
      ServerSocket socket2 = TestCaseUtils.bindFreePort();
      ServerSocket socket3 = TestCaseUtils.bindFreePort();
      rs1Port = socket1.getLocalPort();
      rs2Port = socket2.getLocalPort();
      rs3Port = socket3.getLocalPort();
      socket1.close();
      socket2.close();
      socket3.close();
    } catch (IOException e)
    {
      fail("Unable to determinate some free ports " +
        stackTraceToSingleLineString(e));
    }
  }

  /**
   * Creates the list of servers to represent the RS topology matching the
   * passed test case.
   */
  private SortedSet<String> createRSListForTestCase(String testCase)
  {
    SortedSet<String> replServers = new TreeSet<String>();

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
    } else
      fail("Unknown test case: " + testCase);

    return replServers;
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(short serverId,
    int groupId, String testCase)
  {
    SortedSet<String> replServers = new TreeSet<String>();
    try
    {
      int port = -1;
      if (serverId == RS1_ID)
      {
        port = rs1Port;
        if (testCase.equals("testRSWithSameGroupIds"))
        {
          // 2 servers used for this test case.
          replServers.add("localhost:" + rs2Port);
        } else if (testCase.equals("testRSWithManyGroupIds"))
        {
          // 3 servers used for this test case.
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);
        } else
          fail("Unknown test case: " + testCase);
      } else if (serverId == RS2_ID)
      {
        port = rs2Port;
        if (testCase.equals("testRSWithSameGroupIds"))
        {
          // 2 servers used for this test case.
          replServers.add("localhost:" + rs1Port);
        } else if (testCase.equals("testRSWithManyGroupIds"))
        {
          // 3 servers used for this test case.
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs3Port);
        } else
          fail("Unknown test case: " + testCase);
      } else if (serverId == RS3_ID)
      {
        port = rs3Port;
        if (testCase.equals("testRSWithManyGroupIds"))
        {
          // 3 servers used for this test case.
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);
        } else
          fail("Invalid test case: " + testCase);
      } else
      {
        fail("Unknown replication server id.");
      }

      String dir = "groupIdHandshakeTest" + serverId + testCase + "Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(port, dir, 0, serverId, 0, 100,
        replServers, groupId, 1000, 5000);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      return replicationServer;

    } catch (Exception e)
    {
      fail("createReplicationServer " + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Creates a new ReplicationDomain.
   */
  private LDAPReplicationDomain createReplicationDomain(short serverId,
    int groupId, String testCase)
  {

    SortedSet<String> replServers = null;
    try
    {
      replServers = createRSListForTestCase(testCase);
      DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers, groupId);
      LDAPReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);
      replicationDomain.start();
      return replicationDomain;

    } catch (Exception e)
    {
      fail("createReplicationDomain " + stackTraceToSingleLineString(e));
    }
    return null;
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
      DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
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
      SortedSet<String> otherReplServers = new TreeSet<String>();
      otherReplServers.add("localhost:" + rs1Port);
      otherReplServers.add("localhost:" + rs2Port);
      String dir = "groupIdHandshakeTest" + RS3_ID + testCase + "Db";
      ReplServerFakeConfiguration rsConfWithNewGid =
        new ReplServerFakeConfiguration(rs3Port, dir, 0, RS3_ID, 0, 100,
        otherReplServers, 1, 1000, 5000);
      rs3.applyConfigurationChange(rsConfWithNewGid);

      /**
       * Change group id of RS1 to 3: DS1 and DS2 should reconnect to RS3
       */
      otherReplServers = new TreeSet<String>();
      otherReplServers.add("localhost:" + rs2Port);
      otherReplServers.add("localhost:" + rs3Port);
      dir = "groupIdHandshakeTest" + RS1_ID + testCase + "Db";
      rsConfWithNewGid = new ReplServerFakeConfiguration(rs1Port, dir, 0, RS1_ID,
        0, 100, otherReplServers, 3, 1000, 5000);
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
