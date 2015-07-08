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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.DN;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Test if the replication domain is able to switch of replication server
 * if there is some replication server failure.
 */
@Test(sequential = true)
public class ReplicationServerFailoverTest extends ReplicationTestCase
{
  private static final int DS1_ID = 1;
  private static final int DS2_ID = 2;
  private static final int RS1_ID = 31;
  private static final int RS2_ID = 32;
  private int rs1Port = -1;
  private int rs2Port = -1;
  private LDAPReplicationDomain rd1;
  private LDAPReplicationDomain rd2;
  private ReplicationServer rs1;
  private ReplicationServer rs2;

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("*** TEST *** " + s);
    }
  }

  private void initTest() throws IOException
  {
    rs1Port = rs2Port = -1;
    rd1 = rd2 = null;
    rs1 = rs2 = null;
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
    remove(rs1, rs2);
    rs1 = rs2 = null;
    rs1Port = rs2Port = -1;
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
      DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
      rd1 = createReplicationDomain(baseDn, DS1_ID);

      // Wait a bit so that connections are performed
      Thread.sleep(2000);

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
      DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
      rd1 = createReplicationDomain(baseDn, DS1_ID);
      // Start DS2
      rd2 = createReplicationDomain(baseDn, DS2_ID);

      // Wait a bit so that connections are performed
      Thread.sleep(3000);

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

  /**
   * Check connection of the provided replication domain to the provided
   * replication server. Waits for connection to be ok up to secTimeout seconds
   * before failing.
   */
  private void checkConnection(int secTimeout, int dsId, int rsId, String msg)
      throws Exception
  {
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

    int rsPort = -1;
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
        rdPort = HostPort.valueOf(serverStr).getPort();
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
    int[] ports = TestCaseUtils.findFreePorts(2);
    rs1Port = ports[0];
    rs2Port = ports[1];
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(int serverId, String suffix)
      throws ConfigException
  {
    SortedSet<String> replServers = new TreeSet<>();
    int port = -1;
    if (serverId == RS1_ID)
    {
      port = rs1Port;
      replServers.add("localhost:" + rs2Port);
    }
    else if (serverId == RS2_ID)
    {
      port = rs2Port;
      replServers.add("localhost:" + rs1Port);
    }
    else
    {
      fail("Unknown replication server id.");
    }

    String dir = "replicationServerFailoverTest" + serverId + suffix + "Db";
    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(port, dir, replicationDbImplementation, 0, serverId, 0,
            100, replServers);
    return new ReplicationServer(conf);
  }

  /**
   * Creates a new ReplicationDomain.
   */
  private LDAPReplicationDomain createReplicationDomain(DN baseDn, int serverId)
      throws Exception
  {
    SortedSet<String> replServers = new TreeSet<>();

    // Create a domain with two replication servers
    replServers.add("localhost:" + rs1Port);
    replServers.add("localhost:" + rs2Port);

    DomainFakeCfg domainConf = new DomainFakeCfg(baseDn, serverId, replServers);
    // domainConf.setHeartbeatInterval(500);
    LDAPReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);
    replicationDomain.start();

    return replicationDomain;
  }

  private int findReplServerConnected(LDAPReplicationDomain rd)
  {
    // First check that the Replication domain is connected
    if (!rd.isConnected())
      return -1;

    String serverStr = rd.getReplicationServer();
    return HostPort.valueOf(serverStr).getPort();
  }
}
