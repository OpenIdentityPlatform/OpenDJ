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
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test if the replication domain is able to switch of replication rerver
 * if there is some replication server failure.
 */
@Test(sequential = true)
public class ReplicationServerFailoverTest extends ReplicationTestCase
{

  private static final String BASEDN_STRING = "dc=example,dc=com";
  private static final short DS1_ID = 1;
  private static final short DS2_ID = 2;
  private static final short RS1_ID = 11;
  private static final short RS2_ID = 12;
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

    if (rs1 != null)
    {
      rs1.shutdown();
      rs1 = null;
    }

    if (rs2 != null)
    {
      rs2.shutdown();
      rs2 = null;
    }
    rs1Port = -1;
    rs2Port = -1;
  }

  /**
   * Test the failover feature when one RS fails:
   * 1 DS (DS1) and 2 RS (RS1 and RS2) in topology.
   * DS1 connected to RS1 (DS1<->RS1)
   * Both RS are connected together (RS1<->RS2)
   * RS1 fails, DS1 should be connected to RS2
   *
   * @throws Exception If a problem occured
   */
  @Test
  public void testFailOverSingle() throws Exception
  {
    String testCase = "testFailOverSingle";

    debugInfo("Starting " + testCase);

    initTest();

    // Start RS1
    rs1 = createReplicationServer(RS1_ID, testCase);
    // Start RS2
    rs2 = createReplicationServer(RS2_ID, testCase);

    // Start DS1
    DN baseDn = DN.decode(BASEDN_STRING);
    rd1 = createReplicationDomain(baseDn, DS1_ID, testCase);

    // DS1 connected to RS1 ?
    String msg = "Before " + RS1_ID + " failure";
    checkConnection(DS1_ID, RS1_ID, msg);

    // Simulate RS1 failure
    rs1.shutdown();
    // Let time for failover to happen
    sleep(5000);

    // DS1 connected to RS2 ?
    msg = "After " + RS1_ID + " failure";
    checkConnection(DS1_ID, RS2_ID, msg);

    endTest();
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
   * @throws Exception If a problem occured
   */
  @Test(enabled = false)
  // This test to be run in standalone, not in precommit
  // because the timing is important as we restart servers after they fail
  // and thus cannot warrenty that the recovering server is the right one if
  // the sleep time is not enough with regard to thread scheduling in heavy
  // precommit environment
  public void testFailOverMulti() throws Exception
  {
    String testCase = "testFailOverMulti";

    debugInfo("Starting " + testCase);

    initTest();

    // Start RS1
    rs1 = createReplicationServer(RS1_ID, testCase);
    // Start RS2
    rs2 = createReplicationServer(RS2_ID, testCase);

    // Start DS1
    DN baseDn = DN.decode(BASEDN_STRING);
    rd1 = createReplicationDomain(baseDn, DS1_ID, testCase);
    // Start DS2
    rd2 = createReplicationDomain(baseDn, DS2_ID, testCase);

    // DS1 connected to RS1 ?
    String msg = "Before " + RS1_ID + " failure";
    checkConnection(DS1_ID, RS1_ID, msg);
    // DS2 connected to RS2 ?
    checkConnection(DS2_ID, RS2_ID, msg);

    // Simulate RS1 failure
    rs1.shutdown();
    // Let time for failover to happen
    sleep(5000);

    // DS1 connected to RS2 ?
    msg = "After " + RS1_ID + " failure";
    checkConnection(DS1_ID, RS2_ID, msg);
    // DS2 connected to RS2 ?
    checkConnection(DS2_ID, RS2_ID, msg);

    // Restart RS1
    rs1 = createReplicationServer(RS1_ID, testCase);
    // Let time for RS1 to restart
    sleep(5000);

    // DS1 connected to RS2 ?
    msg = "Before " + RS2_ID + " failure";
    checkConnection(DS1_ID, RS2_ID, msg);
    // DS2 connected to RS2 ?
    checkConnection(DS2_ID, RS2_ID, msg);

    // Simulate RS2 failure
    rs2.shutdown();
    // Let time for failover to happen
    sleep(5000);

    // DS1 connected to RS1 ?
    msg = "After " + RS2_ID + " failure";
    checkConnection(DS1_ID, RS1_ID, msg);
    // DS2 connected to RS1 ?
    checkConnection(DS2_ID, RS1_ID, msg);

    // Restart RS2
    rs2 = createReplicationServer(RS2_ID, testCase);
    // Let time for RS2 to restart
    sleep(5000);

    // DS1 connected to RS1 ?
    msg = "After " + RS2_ID + " restart";
    checkConnection(DS1_ID, RS1_ID, msg);
    // DS2 connected to RS1 ?
    checkConnection(DS2_ID, RS1_ID, msg);

    endTest();
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
   * replication server.
   */
  private void checkConnection(short dsId, short rsId, String msg)
  {

    int rsPort = -1;
    ReplicationDomain rd = null;
    if (dsId == DS1_ID)
    {
      rd = rd1;
    } else if (dsId == DS2_ID)
    {
      rd = rd2;
    } else
    {
      fail("Unknown replication domain server id.");
    }

    if (rsId == RS1_ID)
    {
      rsPort = rs1Port;
    } else if (rsId == RS2_ID)
    {
      rsPort = rs2Port;
    } else
    {
      fail("Unknown replication server id.");
    }

    // Connected ?
    assertEquals(rd.isConnected(), true,
      "Replication domain " + dsId +
      " is not connected to a replication server (" + msg + ")");
    // Right port ?
    String serverStr = rd.getReplicationServer();
    int index = serverStr.lastIndexOf(':');
    if ((index == -1) || (index >= serverStr.length()))
      fail("Enable to find port number in: " + serverStr);
    String rdPortStr = serverStr.substring(index + 1);
    int rdPort = -1;
    try
    {
      rdPort = (new Integer(rdPortStr)).intValue();
    } catch (Exception e)
    {
      fail("Enable to get an int from: " + rdPortStr);
    }
    assertEquals(rdPort, rsPort,
      "Replication domain " + dsId +
      " is not connected to right replication server port (" +
      rdPort + ") was expecting " + rsPort +
      " (" + msg + ")");
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

      String dir = "genid" + serverId + suffix + "Db";
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
  private ReplicationDomain createReplicationDomain(DN baseDn, short serverId,
    String suffix)
  {

    SortedSet<String> replServers = new TreeSet<String>();
    try
    {
      if (serverId == DS1_ID)
      {
        replServers.add("localhost:" + rs1Port);
      } else if (serverId == DS2_ID)
      {
        replServers.add("localhost:" + rs2Port);
      } else
      {
        fail("Unknown replication domain server id.");
      }

      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);
      //domainConf.setHeartbeatInterval(500);
      ReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);

      // Add other server (doing that after connection insure we connect to
      // the right server)
      // WARNING: only works because for the moment, applying changes to conf
      // does not force reconnection in replication domain
      // when it is coded, the reconnect may 1 of both servers and we can not
      // guaranty anymore that we reach the server we want at the beginning.
      if (serverId == DS1_ID)
      {
        replServers.add("localhost:" + rs2Port);
      } else if (serverId == DS2_ID)
      {
        replServers.add("localhost:" + rs1Port);
      } else
      {
        fail("Unknown replication domain server id.");
      }
      domainConf = new DomainFakeCfg(baseDn, serverId, replServers);
      ConfigChangeResult chgRes =
        replicationDomain.applyConfigurationChange(domainConf);
      if ((chgRes == null) ||
        (!chgRes.getResultCode().equals(ResultCode.SUCCESS)))
      {
        fail("Could not change replication domain config" +
          " (add some replication servers).");
      }

      return replicationDomain;

    } catch (Exception e)
    {
      fail("createReplicationDomain " + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Set up the environment.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
  // In case we need to extend
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    super.classCleanUp();
  // In case we need it extend
  }
}
