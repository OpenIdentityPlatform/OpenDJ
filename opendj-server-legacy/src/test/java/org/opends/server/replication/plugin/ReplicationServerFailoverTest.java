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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.testng.annotations.Test;

/**
 * Test if the replication domain is able to switch of replication server
 * if there is some replication server failure.
 */
@Test(singleThreaded = true)
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
      Thread.sleep(5000);

      // DS1 connected to RS1 ?
      // Check which replication server is connected to this LDAP server
      rsPort = rd1.getReplicationServer().getPort();

      if (rsPort == rs1Port)
      {
        // Simulate RS1 failure
        String msg = "Before " + RS1_ID + " failure";
        debugInfo(msg);
        rs1.remove();
        // Let time for failover to happen
        // DS1 connected to RS2 ?
        msg = "After " + RS1_ID + " failure";
        checkConnection(DS1_ID, RS2_ID, msg);
      }
      else if (rsPort == rs2Port)
      { // Simulate RS2 failure
        String msg = "Before " + RS2_ID + " failure";
        debugInfo(msg);
        rs2.remove();
        // DS1 connected to RS1 ?
        msg = "After " + RS2_ID + " failure";
        checkConnection(DS1_ID, RS1_ID, msg);
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
      Thread.sleep(5000);

      // Simulate RS1 failure
      rs1.remove();

      // DS1 connected to RS2 ?
      String msg = "After " + RS1_ID + " failure";
      checkConnection(DS1_ID, RS2_ID, msg);
      // DS2 connected to RS2 ?
      checkConnection(DS2_ID, RS2_ID, msg);

      // Restart RS1
      rs1 = createReplicationServer(RS1_ID, testCase);

      // DS1 connected to RS2 ?
      msg = "Before " + RS2_ID + " failure";
      checkConnection(DS1_ID, RS2_ID, msg);
      // DS2 connected to RS2 ?
      checkConnection(DS2_ID, RS2_ID, msg);

      // Simulate RS2 failure
      rs2.remove();

      // DS1 connected to RS1 ?
      msg = "After " + RS2_ID + " failure";
      checkConnection(DS1_ID, RS1_ID, msg);
      // DS2 connected to RS1 ?
      checkConnection(DS2_ID, RS1_ID, msg);

      // Restart RS2
      rs2 = createReplicationServer(RS2_ID, testCase);

      // DS1 connected to RS1 ?
      msg = "After " + RS2_ID + " restart";
      checkConnection(DS1_ID, RS1_ID, msg);
      // DS2 connected to RS1 ?
      checkConnection(DS2_ID, RS1_ID, msg);

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
  private void checkConnection(int dsId, int rsId, String msg)
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

    waitConnected(dsId, rsId, rsPort, rd, msg);
  }

  /** Find needed free TCP ports. */
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
    return new ReplicationServer(new ReplServerFakeConfiguration(port, dir, 0, serverId, 0, 100, replServers));
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
}
