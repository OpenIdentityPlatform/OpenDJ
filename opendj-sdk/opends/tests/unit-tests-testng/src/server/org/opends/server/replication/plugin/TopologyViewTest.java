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
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import static org.opends.server.replication.plugin.ReplicationBroker.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.opends.server.TestCaseUtils.*;

/**
 * Some tests to know if at any time the view DSs and RSs have of the current
 * topology is accurate, even after some connections, disconnections and
 * re-connections events.
 */
public class TopologyViewTest extends ReplicationTestCase
{
  // Server id definitions
  private static final short DS1_ID = 1;
  private static final short DS2_ID = 2;
  private static final short DS3_ID = 3;
  private static final short DS4_ID = 4;
  private static final short DS5_ID = 5;
  private static final short DS6_ID = 6;
  private static final short RS1_ID = 51;
  private static final short RS2_ID = 52;
  private static final short RS3_ID = 53;

  // Group id definitions
  private static final int DS1_GID = 1;
  private static final int DS2_GID = 1;
  private static final int DS3_GID = 2;
  private static final int DS4_GID = 2;
  private static final int DS5_GID = 3;
  private static final int DS6_GID = 3;
  private static final int RS1_GID = 1;
  private static final int RS2_GID = 2;
  private static final int RS3_GID = 3;

  // Assured conf definitions
  private static final AssuredType DS1_AT = AssuredType.NOT_ASSURED;
  private static final int DS1_SDL = -1;
  private static SortedSet<String> DS1_RU = new TreeSet<String>();

  private static final AssuredType DS2_AT = AssuredType.SAFE_READ;
  private static final int DS2_SDL = -1;
  private static SortedSet<String> DS2_RU = new TreeSet<String>();

  private static final AssuredType DS3_AT = AssuredType.SAFE_DATA;
  private static final int DS3_SDL = 1;
  private static SortedSet<String> DS3_RU = new TreeSet<String>();

  private static final AssuredType DS4_AT = AssuredType.SAFE_READ;
  private static final int DS4_SDL = -1;
  private static SortedSet<String> DS4_RU = new TreeSet<String>();

  private static final AssuredType DS5_AT = AssuredType.SAFE_DATA;
  private static final int DS5_SDL = 2;
  private static SortedSet<String> DS5_RU = new TreeSet<String>();

  private static final AssuredType DS6_AT = AssuredType.SAFE_READ;
  private static final int DS6_SDL = -1;
  private static SortedSet<String> DS6_RU = new TreeSet<String>();

  static
  {
    DS2_RU.add("ldap://fake_url_for_ds2");
    
    DS6_RU.add("ldap://fake_url_for_ds6_A");
    DS6_RU.add("ldap://fake_url_for_ds6_B");
  }
  
  private int rs1Port = -1;
  private int rs2Port = -1;
  private int rs3Port = -1;
  private ReplicationDomain rd1 = null;
  private ReplicationDomain rd2 = null;
  private ReplicationDomain rd3 = null;
  private ReplicationDomain rd4 = null;
  private ReplicationDomain rd5 = null;
  private ReplicationDomain rd6 = null;
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
    rd3 = null;
    rd4 = null;
    rd5 = null;
    rd6 = null;
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

    if (rd3 != null)
    {
      rd3.shutdown();
      rd3 = null;
    }

    if (rd4 != null)
    {
      rd4.shutdown();
      rd4 = null;
    }

    if (rd5 != null)
    {
      rd5.shutdown();
      rd5 = null;
    }

    if (rd6 != null)
    {
      rd6.shutdown();
      rd6 = null;
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
  private void checkConnection(int secTimeout, short dsId, short rsId)
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
      case DS3_ID:
        rd = rd3;
        break;
      case DS4_ID:
        rd = rd4;
        break;
      case DS5_ID:
        rd = rd5;
        break;
      case DS6_ID:
        rd = rd6;
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
          + " (should be: " + rsPort + ")");
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
   * Creates the list of servers to represent the RS topology excluding the
   * RS whose id is passed.
   */
  private SortedSet<String> createRSListExceptOne(short rsIdToExclude)
  {
    SortedSet<String> replServers = new TreeSet<String>();

    if (rsIdToExclude != RS1_ID)
    {
      replServers.add("localhost:" + rs1Port);
    }
    if (rsIdToExclude != RS2_ID)
    {
      replServers.add("localhost:" + rs2Port);
    }
    if (rsIdToExclude != RS3_ID)
    {
      replServers.add("localhost:" + rs3Port);
    }

    return replServers;
  }   

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(short rsId, String testCase)
  {
    try
    {
      SortedSet<String> replServers = createRSListExceptOne(rsId);

      int rsPort = -1;
      int groupId = -1;
      switch (rsId)
      {
        case RS1_ID:
          rsPort = rs1Port;
          groupId = RS1_GID;
          break;
        case RS2_ID:
          rsPort = rs2Port;
          groupId = RS2_GID;
          break;
        case RS3_ID:
          rsPort = rs3Port;
          groupId = RS3_GID;
          break;
        default:
          fail("Unknown replication server id.");
      }

      String dir = "topologyViewTest" + rsId + testCase + "Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(rsPort, dir, 0, rsId, 0, 100,
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
   * Creates and starts a new ReplicationDomain with the correct list of
   * know RSs according to DS id
   */
  private ReplicationDomain createReplicationDomain(short dsId)
  {
    try
    {
      SortedSet<String> replServers = new TreeSet<String>();
      int groupId = -1;
      AssuredType assuredType = null;
      int assuredSdLevel = -100;
      SortedSet<String> refUrls = null;

      // Fill rs list according to defined scenario (see testTopologyChanges
      // comment)
      switch (dsId)
      {
        case DS1_ID:
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);

          groupId = DS1_GID;
          assuredType = DS1_AT;
          assuredSdLevel = DS1_SDL;
          refUrls = DS1_RU;
          break;
        case DS2_ID:
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);

          groupId = DS2_GID;
          assuredType = DS2_AT;
          assuredSdLevel = DS2_SDL;
          refUrls = DS2_RU;
          break;
        case DS3_ID:
          replServers.add("localhost:" + rs2Port);

          groupId = DS3_GID;
          assuredType = DS3_AT;
          assuredSdLevel = DS3_SDL;
          refUrls = DS3_RU;
          break;
        case DS4_ID:
          replServers.add("localhost:" + rs2Port);

          groupId = DS4_GID;
          assuredType = DS4_AT;
          assuredSdLevel = DS4_SDL;
          refUrls = DS4_RU;
          break;
        case DS5_ID:
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);

          groupId = DS5_GID;
          assuredType = DS5_AT;
          assuredSdLevel = DS5_SDL;
          refUrls = DS5_RU;
          break;
        case DS6_ID:
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);

          groupId = DS6_GID;
          assuredType = DS6_AT;
          assuredSdLevel = DS6_SDL;
          refUrls = DS6_RU;
          break;
        default:
          fail("Unknown replication domain server id.");
      }

      DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, dsId, replServers, assuredType,
        assuredSdLevel, groupId, 0, refUrls);
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

  // Definitions of steps for the test case
  private static final int STEP_1 = 1;
  private static final int STEP_2 = 2;
  private static final int STEP_3 = 3;
  private static final int STEP_4 = 4;
  private static final int STEP_5 = 5;
  private static final int STEP_6 = 6;
  private static final int STEP_7 = 7;
  private static final int STEP_8 = 8;
  private static final int STEP_9 = 9;
  private static final int STEP_10 = 10;
  private static final int STEP_11 = 11;
  private static final int STEP_12 = 12;
  private static final int STEP_13 = 13;
  /**
   * Perform connections/disconnections of DS/RS. Uses various config parameters
   * that are embedded in topo info to check if they are well transported.
   * This tests:
   * - if topo msgs are exchanged when needed and reflect topo reality (who is
   * connected to who)
   * - if topo msgs transport config params in terms of assured replication,
   * group id... that reflect what is configured in the DSs.
   *
   * Full topo is:
   * (GID=group id, A=assured, NA=not assured, SR=safe read, SD=safe data,
   * SDL=safe data level, RUF=ref urls filled, RUE=ref urls empty)
   * Except if otherwise stated, RSs and DSs are aware of every others existence
   * - RS1 with GID=1
   * - RS2 with GID=2
   * - RS3 with GID=3
   * - DS1 with GID=1, NA
   * - DS2 with GID=1, A, SR, RUF
   * - DS3 with GID=2, A, SD, SDL=1 (DS3 does not know RS1 and RS3)
   * - DS4 with GID=2, A, SR, RUE (DS4 does not know RS1 and RS3)
   * - DS5 with GID=3, A, SD, SDL=2 (DS5 does not know RS1)
   * - DS6 with GID=3, A, SR, RUF (DS6 does not know RS1)
   * Scenario is:
   * - RS1 starts
   * - DS1 starts and connects to RS1 (check topo view in DS1)
   * - DS2 starts and connects to RS1 (check topo view in DS1,DS2)
   * - RS2 starts (check topo view in DS1,DS2)
   * - DS3 starts and connects to RS2 (check topo view in DS1,DS2,DS3)
   * - DS4 starts and connects to RS2 (check topo view in DS1,DS2,DS3,DS4)
   * - DS5 starts and connects to RS2 (check topo view in DS1,DS2,DS3,DS4,DS5)
   * - RS3 starts (check topo view in DS1,DS2,DS3,DS4,DS5)
   * - DS6 starts and connects to RS3. DS5 should reconnect to RS3 (as RS with
   * same GID becomes available) (check topo view in DS1,DS2,DS3,DS4,DS5,DS6)
   * - DS6 stops (check topo view in DS1,DS2,DS3,DS4,DS5)
   * - DS6 starts and connects to RS3 (check topo view in DS1,DS2,DS3,DS4,DS5,
   * DS6)
   * - RS3 stops. DS5 and DS6 should failover to RS2 as do not know RS1 (check
   * topo view in DS1,DS2,DS3,DS4,DS5,DS6)
   * - RS3 starts. DS5 and DS6 should reconnect to RS3 (as RS with same GID
   * becomes available) (check topo view in DS1,DS2,DS3,DS4,DS5,DS6)
   * - RS2 stops. DS3 and DS4 do not reconnect to any RS as do not know RS1 and
   * RS3. This emulates a full RS with his connected DSs crash. (check topo view
   * in DS1,DS2,DS5,DS6)
   * @throws Exception If a problem occurred
   */
  @Test(enabled = true, groups = "slow")
  public void testTopologyChanges() throws Exception
  {
    String testCase = "testTopologyChanges";

    debugInfo("Starting " + testCase);

    initTest();

    TopoView theoricalTopoView = null;

    try
    {

      /**
       * RS1 starts
       */
      debugInfo("*** STEP 0 ***");
      rs1 = createReplicationServer(RS1_ID, testCase);

      /**
       * DS1 starts and connects to RS1 (check topo view in DS1)
       */
      debugInfo("*** STEP 1 ***");
      rd1 = createReplicationDomain(DS1_ID);
      checkConnection(30, DS1_ID, RS1_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_1);
      checkTopoView(new short[] {DS1_ID}, theoricalTopoView);

      /**
       * DS2 starts and connects to RS1 (check topo view in DS1,DS2)
       */
      debugInfo("*** STEP 2 ***");
      rd2 = createReplicationDomain(DS2_ID);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_2);
      checkTopoView(new short[] {DS1_ID, DS2_ID}, theoricalTopoView);

      /**
       * RS2 starts (check topo view in DS1,DS2)
       */
      debugInfo("*** STEP 3 ***");
      rs2 = createReplicationServer(RS2_ID, testCase);
      sleep(1000); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_3);
      checkTopoView(new short[] {DS1_ID, DS2_ID}, theoricalTopoView);

      /**
       * DS3 starts and connects to RS2 (check topo view in DS1,DS2,DS3)
       */
      debugInfo("*** STEP 4 ***");
      rd3 = createReplicationDomain(DS3_ID);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_4);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID}, theoricalTopoView);

      /**
       * DS4 starts and connects to RS2 (check topo view in DS1,DS2,DS3,DS4)
       */
      debugInfo("*** STEP 5 ***");
      rd4 = createReplicationDomain(DS4_ID);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_5);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID},
        theoricalTopoView);

      /**
       * DS5 starts and connects to RS2 (check topo view in DS1,DS2,DS3,DS4,DS5)
       */
      debugInfo("*** STEP 6 ***");
      rd5 = createReplicationDomain(DS5_ID);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_6);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID},
        theoricalTopoView);

      /**
       * RS3 starts. DS5 should reconnect to RS3 (as RS with
       * same GID becomes available) (check topo view in DS1,DS2,DS3,DS4,DS5)
       */
      debugInfo("*** STEP 7 ***");
      rs3 = createReplicationServer(RS3_ID, testCase);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_7);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID},
        theoricalTopoView);


      /**
       * DS6 starts and connects to RS3 (check topo view in DS1,DS2,DS3,DS4,DS5,
       * DS6)
       */
      debugInfo("*** STEP 8 ***");
      rd6 = createReplicationDomain(DS6_ID);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS3_ID);
      checkConnection(30, DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_8);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * DS6 stops (check topo view in DS1,DS2,DS3,DS4,DS5)
       */
      debugInfo("*** STEP 9 ***");
      rd6.disable();
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS3_ID);
      assertFalse(rd6.isConnected());
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_9);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID},
        theoricalTopoView);


      /**
       * DS6 starts and connects to RS3 (check topo view in DS1,DS2,DS3,DS4,DS5,
       * DS6)
       */
      debugInfo("*** STEP 10 ***");
      rd6.enable();
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS3_ID);
      checkConnection(30, DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_10);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * RS3 stops. DS5 and DS6 should failover to RS2 as do not know RS1 (check
       * topo view in DS1,DS2,DS3,DS4,DS5,DS6)
       */
      debugInfo("*** STEP 11 ***");
      rs3.remove();
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS2_ID);
      checkConnection(30, DS6_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_11);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * RS3 starts. DS5 and DS6 should reconnect to RS3 (as RS with same GID
       * becomes available) (check topo view in DS1,DS2,DS3,DS4,DS5,DS6)
       */
      debugInfo("*** STEP 12 ***");
      rs3 = createReplicationServer(RS3_ID, testCase);
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS3_ID, RS2_ID);
      checkConnection(30, DS4_ID, RS2_ID);
      checkConnection(30, DS5_ID, RS3_ID);
      checkConnection(30, DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_12);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * RS2 stops. DS3 and DS4 do not reconnect to any RS as do not know RS1 and
       * RS3. This emulates a full RS with his connected DSs crash. (check topo
       * view in DS1,DS2,DS5,DS6)
       */
      debugInfo("*** STEP 13 ***");
      rs2.remove();
      sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(30, DS1_ID, RS1_ID);
      checkConnection(30, DS2_ID, RS1_ID);
      checkConnection(30, DS5_ID, RS3_ID);
      checkConnection(30, DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_13);
      checkTopoView(new short[] {DS1_ID, DS2_ID, DS5_ID, DS6_ID},
        theoricalTopoView);

    } finally
    {
      endTest();
    }
  }

  /**
   * Creates RSInfo for the passed RS
   */
  private RSInfo createRSInfo(short rsId)
  {
    int groupId = -1;
    switch (rsId)
    {
      case RS1_ID:
        groupId = RS1_GID;
        break;
      case RS2_ID:
        groupId = RS2_GID;
        break;
      case RS3_ID:
        groupId = RS3_GID;
        break;
      default:
        fail("Unknown replication server id.");
    }

    return new RSInfo(rsId, TEST_DN_WITH_ROOT_ENTRY_GENID, (byte)groupId);
  }

  /**
   * Creates DSInfo for the passed DS, connected to the passed RS
   */
  private DSInfo createDSInfo(short dsId, short rsId)
  {
    ServerStatus status = ServerStatus.NORMAL_STATUS;

    byte groupId = -1;
    AssuredType assuredType = null;
    int assuredSdLevel = -100;
    SortedSet<String> refUrls = null;

    switch (dsId)
      {
        case DS1_ID:
          groupId = DS1_GID;
          assuredType = DS1_AT;
          assuredSdLevel = DS1_SDL;
          refUrls = DS1_RU;
          break;
        case DS2_ID:
          groupId = DS2_GID;
          assuredType = DS2_AT;
          assuredSdLevel = DS2_SDL;
          refUrls = DS2_RU;
          break;
        case DS3_ID:
          groupId = DS3_GID;
          assuredType = DS3_AT;
          assuredSdLevel = DS3_SDL;
          refUrls = DS3_RU;
          break;
        case DS4_ID:
          groupId = DS4_GID;
          assuredType = DS4_AT;
          assuredSdLevel = DS4_SDL;
          refUrls = DS4_RU;
          break;
        case DS5_ID:
          groupId = DS5_GID;
          assuredType = DS5_AT;
          assuredSdLevel = DS5_SDL;
          refUrls = DS5_RU;
          break;
        case DS6_ID:
          groupId = DS6_GID;
          assuredType = DS6_AT;
          assuredSdLevel = DS6_SDL;
          refUrls = DS6_RU;
          break;
        default:
          fail("Unknown replication domain server id.");
      }

    // Perform necessary conversions
    boolean assuredFlag = (assuredType != AssuredType.NOT_ASSURED);
    AssuredMode assMode = ( (assuredType == AssuredType.SAFE_READ) ?
      AssuredMode.SAFE_READ_MODE : AssuredMode.SAFE_DATA_MODE);
    List<String> urls = new ArrayList<String>();
    for(String str : refUrls)
    {
      urls.add(str);
    }

    return new DSInfo(dsId, rsId, TEST_DN_WITH_ROOT_ENTRY_GENID, status, assuredFlag, assMode,
       (byte)assuredSdLevel, groupId, urls);
  }

  /**
   * Creates the topo view to be checked at each step of the test (view that
   * every concerned DS should have)
   */
  private TopoView createTheoreticalTopoViewForStep(int step)
  {
     List<DSInfo> dsList = new ArrayList<DSInfo>();
     List<RSInfo> rsList = new ArrayList<RSInfo>();

    switch (step)
    {
      case STEP_1:
        rsList.add(createRSInfo(RS1_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        break;
      case STEP_2:
        rsList.add(createRSInfo(RS1_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        break;
      case STEP_3:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        break;
      case STEP_4:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        break;
      case STEP_5:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        break;
      case STEP_6:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS2_ID));
        break;
      case STEP_7:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));
        rsList.add(createRSInfo(RS3_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS3_ID));
        break;
      case STEP_8:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));
        rsList.add(createRSInfo(RS3_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS3_ID));
        dsList.add(createDSInfo(DS6_ID, RS3_ID));
        break;
      case STEP_9:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));
        rsList.add(createRSInfo(RS3_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS3_ID));
        break;
      case STEP_10:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));
        rsList.add(createRSInfo(RS3_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS3_ID));
        dsList.add(createDSInfo(DS6_ID, RS3_ID));
        break;
      case STEP_11:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS2_ID));
        dsList.add(createDSInfo(DS6_ID, RS2_ID));
        break;
      case STEP_12:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS2_ID));
        rsList.add(createRSInfo(RS3_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS3_ID, RS2_ID));
        dsList.add(createDSInfo(DS4_ID, RS2_ID));
        dsList.add(createDSInfo(DS5_ID, RS3_ID));
        dsList.add(createDSInfo(DS6_ID, RS3_ID));
        break;
      case STEP_13:
        rsList.add(createRSInfo(RS1_ID));
        rsList.add(createRSInfo(RS3_ID));

        dsList.add(createDSInfo(DS1_ID, RS1_ID));
        dsList.add(createDSInfo(DS2_ID, RS1_ID));
        dsList.add(createDSInfo(DS5_ID, RS3_ID));
        dsList.add(createDSInfo(DS6_ID, RS3_ID));
        break;
      default:
        fail("Unknown test step: " + step);
    }

    return new TopoView(dsList, rsList);
  }
  
  /**
   * Get the topo view each DS in the provided ds list has and compares it
   * with the theoretical topology view that every body should have at the time
   * this method is called.
   */
  private void checkTopoView(short[] dsIdList, TopoView theoricalTopoView)
  {
   for(short currentDsId : dsIdList)
   {
     ReplicationDomain rd = null;
     
     switch (currentDsId)
     {
       case DS1_ID:
         rd = rd1;
         break;
       case DS2_ID:
         rd = rd2;
         break;
       case DS3_ID:
         rd = rd3;
         break;
       case DS4_ID:
         rd = rd4;
         break;
       case DS5_ID:
         rd = rd5;
         break;
       case DS6_ID:
         rd = rd6;
         break;
       default:
         fail("Unknown replication domain server id.");
     }
   
     /**
      * Get the topo view of the current analyzed DS
      */
     List<DSInfo> internalDsList = rd.getDsList();
     // Add info for DS itself:
     // we need to clone the list as we don't want to modify the list kept
     // inside the DS.
     List<DSInfo> dsList = new ArrayList<DSInfo>();
     for (DSInfo aDsInfo : internalDsList)
     {
       dsList.add(aDsInfo);
     }
     short dsId = rd.getServerId();
     short rsId = rd.getBroker().getRsServerId();
     ServerStatus status = rd.getStatus();
     boolean assuredFlag = rd.isAssured();
     AssuredMode assuredMode = rd.getAssuredMode();
     byte safeDataLevel = rd.getAssuredSdLevel();
     byte groupId = rd.getGroupId();
     List<String> refUrls = rd.getRefUrls();
     DSInfo dsInfo = new DSInfo(dsId, rsId, TEST_DN_WITH_ROOT_ENTRY_GENID, status, assuredFlag, assuredMode,
       safeDataLevel, groupId, refUrls);
     dsList.add(dsInfo);
     
     TopoView dsTopoView = new TopoView(dsList, rd.getRsList());
     
     /**
      * Compare to what is the expected view
      */
     
     assertEquals(dsTopoView, theoricalTopoView);
   }
  }
  
  /**
   * Bag class representing a view of the topology at a given time
   * (who is connected to who, what are the config parameters...)
   */
  private class TopoView
  {
    private List<DSInfo> dsList = null;
    private List<RSInfo> rsList = null;    
    
    public TopoView(List<DSInfo> dsList, List<RSInfo> rsList)
    {
      assertNotNull(dsList);
      assertNotNull(rsList);
        
      this.dsList = dsList;
      this.rsList = rsList;
    }
    
    public boolean equals(Object obj)
    {
      assertNotNull(obj);
      assertFalse(obj.getClass() != this.getClass());
        
      TopoView topoView = (TopoView) obj;

      // Check dsList
      if (topoView.dsList.size() != dsList.size())
        return false;
      for (DSInfo dsInfo : topoView.dsList)
      {
        int found = 0;
        for (DSInfo thisDsInfo : dsList)
        {
          if (thisDsInfo.equals(dsInfo))
            found++;
        }
        // Not found
        if (found == 0)
          return false;
        // Should never see twice as dsInfo structure in a dsList
        assertFalse(found > 1);
      // Ok, found exactly once in the list, examine next structure
      }

      // Check rsList
      if (topoView.rsList.size() != rsList.size())
        return false;
      for (RSInfo rsInfo : topoView.rsList)
      {
        int found = 0;
        for (RSInfo thisRsInfo : rsList)
        {
          if (thisRsInfo.equals(rsInfo))
            found++;
        }
        // Not found
        if (found == 0)
          return false;
        // Should never see twice as rsInfo structure in a dsList
        assertFalse(found > 1);
      // Ok, found exactly once in the list, examine next structure
      }

      return true;
    }

    public String toString()
    {
      String dsStr = "";
      for (DSInfo dsInfo : dsList)
      {
        dsStr += dsInfo.toString() + "\n----------------------------\n";
      }

      String rsStr = "";
      for (RSInfo rsInfo : rsList)
      {
        rsStr += rsInfo.toString() + "\n----------------------------\n";
      }

      return ("TopoView:" +
        "\n----------------------------\n" + "CONNECTED DS SERVERS:\n" + dsStr +
        "CONNECTED RS SERVERS:\n" + rsStr);
    }
  }
}
