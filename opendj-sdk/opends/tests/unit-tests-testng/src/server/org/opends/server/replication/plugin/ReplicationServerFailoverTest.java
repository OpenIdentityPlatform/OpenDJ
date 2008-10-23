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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.*;

import java.net.ServerSocket;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Test if the replication domain is able to switch of replication server
 * if there is some replication server failure.
 */
@Test(sequential = true)
public class ReplicationServerFailoverTest extends ReplicationTestCase
{
  private static final short DS1_ID = 1;
  private static final short DS2_ID = 2;
  private static final short RS1_ID = 31;
  private static final short RS2_ID = 32;
  private int rs1Port = -1;
  private int rs2Port = -1;
  private ReplicationDomain rd1 = null;
  private ReplicationDomain rd2 = null;
  private ReplicationServer rs1 = null;
  private ReplicationServer rs2 = null;

  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("*** TEST *** " + s);
    }
  }

  private void initTest()
  {
    rs1Port = -1;
    rs2Port = -1;
    rd1 = null;
    rd2 = null;
    rs1 = null;
    rs2 = null;
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
    rs1Port = -1;
    rs2Port = -1;
  }

  /**
   * Test the failover feature when one RS fails:
   * 1 DS (DS1) and 2 RS (RS1 and RS2) in topology.
   * DS1 connected to one RS
   * Both RS are connected together (RS1<->RS2)
   * The RS connected to DS1 fails, DS1 should be connected
   * to the other RS
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testFailOverSingle() throws Exception
  {
    String testCase = "testFailOverSingle";
    int rsPort = -1;
    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      // Start RS1
      rs1 = createReplicationServer(RS1_ID, testCase);
      // Start RS2
      rs2 = createReplicationServer(RS2_ID, testCase);

      // Start DS1
      DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
      rd1 = createReplicationDomain(baseDn, DS1_ID);
      
      // Wait a bit so that connections are performed
      sleep(2000);

      // DS1 connected to RS1 ?
      // Check which replication server is connected to this LDAP server
      rsPort = findReplServerConnected(rd1);

      if (rsPort == rs1Port)
      {
        // Simulate RS1 failure
        String msg = "Before " + RS1_ID + " failure";
        debugInfo(msg);
        rs1.remove();
        // Let time for failover to happen
        // DS1 connected to RS2 ?
        msg = "After " + RS1_ID + " failure";
        checkConnection(30, DS1_ID, RS2_ID, msg);
      }
      else if (rsPort == rs2Port)
      { // Simulate RS2 failure
        String msg = "Before " + RS2_ID + " failure";
        debugInfo(msg);
        rs2.remove();
        // DS1 connected to RS1 ?
        msg = "After " + RS2_ID + " failure";
        checkConnection(30, DS1_ID, RS1_ID, msg);
      }
      else {
        fail("DS1 is not connected to a RS");
      }

      debugInfo(testCase + " successfully ended.");
    } finally
    {
      endTest();
    }
  }

  /**
   * Test the failover feature when one RS fails:
   * 2 DS (DS1 and DS2) and 2 RS (RS1 and RS2) in topology.
   * Each DS connected to its own RS (DS1<->RS1, DS2<->RS2)
   * Both RS are connected together (RS1<->RS2)
   * RS1 fails, DS1 and DS2 should be both connected to RS2
   * RS1 comes back (no change)
   * RS2 fails, DS1 and DS2 should be both connected to RS1
   *
   * @throws Exception If a problem occurred
   */
  @Test(groups="slow")
  public void testFailOverMulti() throws Exception
  {
    String testCase = "testFailOverMulti";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      // Start RS1
      rs1 = createReplicationServer(RS1_ID, testCase);
      // Start RS2
      rs2 = createReplicationServer(RS2_ID, testCase);

      // Start DS1
      DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
      rd1 = createReplicationDomain(baseDn, DS1_ID);
      // Start DS2
      rd2 = createReplicationDomain(baseDn, DS2_ID);
      
      // Wait a bit so that connections are performed
      sleep(3000);

      // Simulate RS1 failure
      rs1.remove();

      // DS1 connected to RS2 ?
      String msg = "After " + RS1_ID + " failure";
      checkConnection(30, DS1_ID, RS2_ID, msg);
      // DS2 connected to RS2 ?
      checkConnection(30, DS2_ID, RS2_ID, msg);

      // Restart RS1
      rs1 = createReplicationServer(RS1_ID, testCase);

      // DS1 connected to RS2 ?
      msg = "Before " + RS2_ID + " failure";
      checkConnection(30, DS1_ID, RS2_ID, msg);
      // DS2 connected to RS2 ?
      checkConnection(30, DS2_ID, RS2_ID, msg);

      // Simulate RS2 failure
      rs2.remove();

      // DS1 connected to RS1 ?
      msg = "After " + RS2_ID + " failure";
      checkConnection(30, DS1_ID, RS1_ID, msg);
      // DS2 connected to RS1 ?
      checkConnection(30, DS2_ID, RS1_ID, msg);

      // Restart RS2
      rs2 = createReplicationServer(RS2_ID, testCase);

      // DS1 connected to RS1 ?
      msg = "After " + RS2_ID + " restart";
      checkConnection(30, DS1_ID, RS1_ID, msg);
      // DS2 connected to RS1 ?
      checkConnection(30, DS2_ID, RS1_ID, msg);

      debugInfo(testCase + " successfully ended.");
    } finally
    {
      endTest();
    }
  }

  private void sleep(long time)
  {
    try
    {
      Thread.sleep(time);
    } catch (InterruptedException ex)
    {
      fail("Error sleeping " + stackTraceToSingleLineString(ex));
    }
  }

  /**
   * Check connection of the provided replication domain to the provided
   * replication server. Waits for connection to be ok up to secTimeout seconds
   * before failing.
   */
  private void checkConnection(int secTimeout, short dsId, short rsId, String msg)
  {

    int rsPort = -1;
    ReplicationDomain rd = null;
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
      rs1Port = socket1.getLocalPort();
      rs2Port = socket2.getLocalPort();
      socket1.close();
      socket2.close();
    } catch (IOException e)
    {
      fail("Unable to determinate some free ports " +
        stackTraceToSingleLineString(e));
    }
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(short serverId,
    String suffix)
  {
    SortedSet<String> replServers = new TreeSet<String>();
    try
    {
      int port = -1;
      if (serverId == RS1_ID)
      {
        port = rs1Port;
        replServers.add("localhost:" + rs2Port);
      } else if (serverId == RS2_ID)
      {
        port = rs2Port;
        replServers.add("localhost:" + rs1Port);
      } else
      {
        fail("Unknown replication server id.");
      }

      String dir = "replicationServerFailoverTest" + serverId + suffix + "Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(port, dir, 0, serverId, 0, 100,
        replServers);
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
  private ReplicationDomain createReplicationDomain(DN baseDn, short serverId)
  {

    SortedSet<String> replServers = new TreeSet<String>();
    try
    {
      // Create a domain with two replication servers
      replServers.add("localhost:" + rs1Port);
      replServers.add("localhost:" + rs2Port);

      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);
      //domainConf.setHeartbeatInterval(500);
      ReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);
      replicationDomain.start();

      return replicationDomain;

    } catch (Exception e)
    {
      fail("createReplicationDomain " + stackTraceToSingleLineString(e));
    }
    return null;
  }

  private int findReplServerConnected(ReplicationDomain rd)
  {
    int rsPort = -1;

    // First check that the Replication domain is connected
    if (!rd.isConnected())
      return rsPort;

    String serverStr = rd.getReplicationServer();
    int index = serverStr.lastIndexOf(':');
    if ((index == -1) || (index >= serverStr.length()))
      fail("Enable to find port number in: " + serverStr);
    rsPort = (new Integer(serverStr.substring(index + 1)));

      return rsPort;
  }
}
