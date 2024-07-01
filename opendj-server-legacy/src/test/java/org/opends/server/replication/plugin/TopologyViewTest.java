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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.testng.annotations.Test;

/**
 * Some tests to know if at any time the view DSs and RSs have of the current
 * topology is accurate, even after some connections, disconnections and
 * re-connections events.
 */
public class TopologyViewTest extends ReplicationTestCase
{
  /** Server id definitions. */
  private static final int DS1_ID = 1;
  private static final int DS2_ID = 2;
  private static final int DS3_ID = 3;
  private static final int DS4_ID = 4;
  private static final int DS5_ID = 5;
  private static final int DS6_ID = 6;
  private static final int RS1_ID = 51;
  private static final int RS2_ID = 52;
  private static final int RS3_ID = 53;

  /** Group id definitions. */
  private static final int DS1_GID = 1;
  private static final int DS2_GID = 1;
  private static final int DS3_GID = 2;
  private static final int DS4_GID = 2;
  private static final int DS5_GID = 3;
  private static final int DS6_GID = 3;
  private static final int RS1_GID = 1;
  private static final int RS2_GID = 2;
  private static final int RS3_GID = 3;

  /** Assured conf definitions. */
  private static final AssuredType DS1_AT = AssuredType.NOT_ASSURED;
  private static final int DS1_SDL = -1;
  private static SortedSet<String> DS1_RU = new TreeSet<>();

  private static final AssuredType DS2_AT = AssuredType.SAFE_READ;
  private static final int DS2_SDL = -1;
  private static SortedSet<String> DS2_RU = new TreeSet<>();

  private static final AssuredType DS3_AT = AssuredType.SAFE_DATA;
  private static final int DS3_SDL = 1;
  private static SortedSet<String> DS3_RU = new TreeSet<>();

  private static final AssuredType DS4_AT = AssuredType.SAFE_READ;
  private static final int DS4_SDL = -1;
  private static SortedSet<String> DS4_RU = new TreeSet<>();

  private static final AssuredType DS5_AT = AssuredType.SAFE_DATA;
  private static final int DS5_SDL = 2;
  private static SortedSet<String> DS5_RU = new TreeSet<>();

  private static final AssuredType DS6_AT = AssuredType.SAFE_READ;
  private static final int DS6_SDL = -1;
  private static SortedSet<String> DS6_RU = new TreeSet<>();

  private static String LOCAL_HOST_NAME;

  static
  {
    DS2_RU.add("ldap://fake_url_for_ds2");

    DS6_RU.add("ldap://fake_url_for_ds6_A");
    DS6_RU.add("ldap://fake_url_for_ds6_B");

    try
    {
      LOCAL_HOST_NAME = InetAddress.getLocalHost().getHostName();
    }
    catch (UnknownHostException e)
    {
      fail("Unable to resolve local host name", e);
    }
  }

  private int rs1Port = -1;
  private int rs2Port = -1;
  private int rs3Port = -1;
  private LDAPReplicationDomain rd1;
  private LDAPReplicationDomain rd2;
  private LDAPReplicationDomain rd3;
  private LDAPReplicationDomain rd4;
  private LDAPReplicationDomain rd5;
  private LDAPReplicationDomain rd6;
  private ReplicationServer rs1;
  private ReplicationServer rs2;
  private ReplicationServer rs3;

  /** The tracer object for the debug logger. */
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
    rd3 = null;
    rd4 = null;
    rd5 = null;
    rd6 = null;
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
  private void checkConnection(int dsId, int rsId) throws Exception
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

    waitConnected(dsId, rsId, rsPort, rd, "");
  }

  /** Find needed free TCP ports. */
  private void findFreePorts() throws Exception
  {
    int[] ports = TestCaseUtils.findFreePorts(3);
    int i = 0;
    rs1Port = ports[i++];
    rs2Port = ports[i++];
    rs3Port = ports[i++];
  }

  /**
   * Creates the list of servers to represent the RS topology excluding the
   * RS whose id is passed.
   */
  private SortedSet<String> createRSListExceptOne(int rsIdToExclude)
  {
    SortedSet<String> replServers = new TreeSet<>();

    if (rsIdToExclude != RS1_ID)
    {
      replServers.add(getHostPort(rs1Port));
    }
    if (rsIdToExclude != RS2_ID)
    {
      replServers.add(getHostPort(rs2Port));
    }
    if (rsIdToExclude != RS3_ID)
    {
      replServers.add(getHostPort(rs3Port));
    }

    return replServers;
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(int rsId, String testCase)
      throws ConfigException
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
        new ReplServerFakeConfiguration(rsPort, dir, 0, rsId, 0,
            100, replServers, groupId, 1000, 5000);
    return new ReplicationServer(conf);
  }

  /**
   * Creates and starts a new ReplicationDomain with the correct list of
   * know RSs according to DS id.
   */
  private LDAPReplicationDomain createReplicationDomain(int dsId)
      throws Exception
  {
    SortedSet<String> replServers = new TreeSet<>();
    int groupId = -1;
    AssuredType assuredType = null;
    int assuredSdLevel = -100;
    SortedSet<String> refUrls = null;

    // Fill rs list according to defined scenario (see testTopologyChanges
    // comment)
    switch (dsId)
    {
    case DS1_ID:
      replServers.add(getHostPort(rs1Port));
      replServers.add(getHostPort(rs2Port));
      replServers.add(getHostPort(rs3Port));

      groupId = DS1_GID;
      assuredType = DS1_AT;
      assuredSdLevel = DS1_SDL;
      refUrls = DS1_RU;
      break;
    case DS2_ID:
      replServers.add(getHostPort(rs1Port));
      replServers.add(getHostPort(rs2Port));
      replServers.add(getHostPort(rs3Port));

      groupId = DS2_GID;
      assuredType = DS2_AT;
      assuredSdLevel = DS2_SDL;
      refUrls = DS2_RU;
      break;
    case DS3_ID:
      replServers.add(getHostPort(rs2Port));

      groupId = DS3_GID;
      assuredType = DS3_AT;
      assuredSdLevel = DS3_SDL;
      refUrls = DS3_RU;
      break;
    case DS4_ID:
      replServers.add(getHostPort(rs2Port));

      groupId = DS4_GID;
      assuredType = DS4_AT;
      assuredSdLevel = DS4_SDL;
      refUrls = DS4_RU;
      break;
    case DS5_ID:
      replServers.add(getHostPort(rs2Port));
      replServers.add(getHostPort(rs3Port));

      groupId = DS5_GID;
      assuredType = DS5_AT;
      assuredSdLevel = DS5_SDL;
      refUrls = DS5_RU;
      break;
    case DS6_ID:
      replServers.add(getHostPort(rs2Port));
      replServers.add(getHostPort(rs3Port));

      groupId = DS6_GID;
      assuredType = DS6_AT;
      assuredSdLevel = DS6_SDL;
      refUrls = DS6_RU;
      break;
    default:
      fail("Unknown replication domain server id.");
    }

    DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, dsId, replServers, assuredType,
            assuredSdLevel, groupId, 0, refUrls);
    LDAPReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);
    replicationDomain.start();

    return replicationDomain;
  }

  /** Definitions of steps for the test case. */
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
      checkConnection(DS1_ID, RS1_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_1);
      checkTopoView(new int[] {DS1_ID}, theoricalTopoView);

      /**
       * DS2 starts and connects to RS1 (check topo view in DS1,DS2)
       */
      debugInfo("*** STEP 2 ***");
      rd2 = createReplicationDomain(DS2_ID);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_2);
      checkTopoView(new int[] {DS1_ID, DS2_ID}, theoricalTopoView);

      /**
       * RS2 starts (check topo view in DS1,DS2)
       */
      debugInfo("*** STEP 3 ***");
      rs2 = createReplicationServer(RS2_ID, testCase);
      Thread.sleep(1000); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_3);
      checkTopoView(new int[] {DS1_ID, DS2_ID}, theoricalTopoView);

      /**
       * DS3 starts and connects to RS2 (check topo view in DS1,DS2,DS3)
       */
      debugInfo("*** STEP 4 ***");
      rd3 = createReplicationDomain(DS3_ID);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_4);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID}, theoricalTopoView);

      /**
       * DS4 starts and connects to RS2 (check topo view in DS1,DS2,DS3,DS4)
       */
      debugInfo("*** STEP 5 ***");
      rd4 = createReplicationDomain(DS4_ID);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_5);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID},
        theoricalTopoView);

      /**
       * DS5 starts and connects to RS2 (check topo view in DS1,DS2,DS3,DS4,DS5)
       */
      debugInfo("*** STEP 6 ***");
      rd5 = createReplicationDomain(DS5_ID);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_6);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID},
        theoricalTopoView);

      /**
       * RS3 starts. DS5 should reconnect to RS3 (as RS with
       * same GID becomes available) (check topo view in DS1,DS2,DS3,DS4,DS5)
       */
      debugInfo("*** STEP 7 ***");
      rs3 = createReplicationServer(RS3_ID, testCase);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_7);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID},
        theoricalTopoView);


      /**
       * DS6 starts and connects to RS3 (check topo view in DS1,DS2,DS3,DS4,DS5,
       * DS6)
       */
      debugInfo("*** STEP 8 ***");
      rd6 = createReplicationDomain(DS6_ID);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS3_ID);
      checkConnection(DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_8);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * DS6 stops (check topo view in DS1,DS2,DS3,DS4,DS5)
       */
      debugInfo("*** STEP 9 ***");
      rd6.disable();
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS3_ID);
      assertFalse(rd6.isConnected());
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_9);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID},
        theoricalTopoView);


      /**
       * DS6 starts and connects to RS3 (check topo view in DS1,DS2,DS3,DS4,DS5,
       * DS6)
       */
      debugInfo("*** STEP 10 ***");
      rd6.enable();
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS3_ID);
      checkConnection(DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_10);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * RS3 stops. DS5 and DS6 should failover to RS2 as do not know RS1 (check
       * topo view in DS1,DS2,DS3,DS4,DS5,DS6)
       */
      debugInfo("*** STEP 11 ***");
      rs3.remove();
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS2_ID);
      checkConnection(DS6_ID, RS2_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_11);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * RS3 starts. DS5 and DS6 should reconnect to RS3 (as RS with same GID
       * becomes available) (check topo view in DS1,DS2,DS3,DS4,DS5,DS6)
       */
      debugInfo("*** STEP 12 ***");
      rs3 = createReplicationServer(RS3_ID, testCase);
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS3_ID, RS2_ID);
      checkConnection(DS4_ID, RS2_ID);
      checkConnection(DS5_ID, RS3_ID);
      checkConnection(DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_12);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS3_ID, DS4_ID, DS5_ID, DS6_ID},
        theoricalTopoView);


      /**
       * RS2 stops. DS3 and DS4 do not reconnect to any RS as do not know RS1 and
       * RS3. This emulates a full RS with his connected DSs crash. (check topo
       * view in DS1,DS2,DS5,DS6)
       */
      debugInfo("*** STEP 13 ***");
      rs2.remove();
      Thread.sleep(500); // Let time to topo msgs being propagated through the network
      checkConnection(DS1_ID, RS1_ID);
      checkConnection(DS2_ID, RS1_ID);
      checkConnection(DS5_ID, RS3_ID);
      checkConnection(DS6_ID, RS3_ID);
      theoricalTopoView = createTheoreticalTopoViewForStep(STEP_13);
      checkTopoView(new int[] {DS1_ID, DS2_ID, DS5_ID, DS6_ID},
        theoricalTopoView);

    } finally
    {
      endTest();
    }
  }

  /**
   * Creates RSInfo for the passed RS.
   */
  private RSInfo createRSInfo(int rsId)
  {
    int groupId = -1;
    String serverUrl = null;
    switch (rsId)
    {
      case RS1_ID:
        groupId = RS1_GID;
        serverUrl = getHostPort(rs1Port);
        break;
      case RS2_ID:
        groupId = RS2_GID;
        serverUrl = getHostPort(rs2Port);
        break;
      case RS3_ID:
        groupId = RS3_GID;
        serverUrl = getHostPort(rs3Port);
        break;
      default:
        fail("Unknown replication server id.");
    }

    return new RSInfo(rsId, serverUrl, TEST_DN_WITH_ROOT_ENTRY_GENID, (byte)groupId, 1);
  }

  /**
   * Creates DSInfo for the passed DS, connected to the passed RS.
   */
  private DSInfo createDSInfo(int dsId, int rsId)
  {
    ServerStatus status = ServerStatus.NORMAL_STATUS;

    byte groupId = -1;
    AssuredType assuredType = null;
    int assuredSdLevel = -100;
    SortedSet<String> refUrls = null;
    Set<String> eclIncludes = new HashSet<>();
    short protocolVersion = ProtocolVersion.getCurrentVersion();

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
    boolean assuredFlag = assuredType != AssuredType.NOT_ASSURED;
    AssuredMode assMode = assuredType == AssuredType.SAFE_READ
        ? AssuredMode.SAFE_READ_MODE
        : AssuredMode.SAFE_DATA_MODE;

    return new DSInfo(dsId, "dummy:1234", rsId, TEST_DN_WITH_ROOT_ENTRY_GENID, status, assuredFlag, assMode,
       (byte)assuredSdLevel, groupId, refUrls, eclIncludes, eclIncludes, protocolVersion);
  }

  /**
   * Creates the topo view to be checked at each step of the test (view that
   * every concerned DS should have).
   */
  private TopoView createTheoreticalTopoViewForStep(int step)
  {
     List<DSInfo> dsList = new ArrayList<>();
     List<RSInfo> rsList = new ArrayList<>();

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
  private void checkTopoView(int[] dsIdList, TopoView theoricalTopoView)
      throws Exception
  {
   Thread.sleep(500);
   for(int currentDsId : dsIdList)
   {
     LDAPReplicationDomain rd = null;

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
     // Add info for DS itself:
     // we need to clone the list as we don't want to modify the list kept
     // inside the DS.
      final DSInfo dsInfo = new DSInfo(rd.getServerId(), "dummy:1234", rd.getRsServerId(),
          TEST_DN_WITH_ROOT_ENTRY_GENID,
          rd.getStatus(),
          rd.isAssured(), rd.getAssuredMode(), rd.getAssuredSdLevel(),
          rd.getGroupId(), rd.getRefUrls(),
          rd.getEclIncludes(), rd.getEclIncludesForDeletes(),
          ProtocolVersion.getCurrentVersion());
      final List<DSInfo> dsList = new ArrayList<>(rd.getReplicaInfos().values());
      dsList.add(dsInfo);

     TopoView dsTopoView = new TopoView(dsList, rd.getRsInfos());
     assertEquals(dsTopoView, theoricalTopoView, " in DSid=" + currentDsId);
   }
  }

  /**
   * Bag class representing a view of the topology at a given time
   * (who is connected to who, what are the config parameters...)
   */
  private class TopoView
  {
    private List<DSInfo> dsList;
    private List<RSInfo> rsList;

    public TopoView(List<DSInfo> dsList, List<RSInfo> rsList)
    {
      assertNotNull(dsList);
      assertNotNull(rsList);

      this.dsList = dsList;
      this.rsList = rsList;
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj == null || getClass() != obj.getClass())
      {
        return false;
      }
      TopoView other = (TopoView) obj;
      return checkLists(dsList, other.dsList)
          && checkLists(rsList, other.rsList);
    }

    private boolean checkLists(List<?> list, List<?> otherList)
    {
      if (otherList.size() != list.size())
      {
        return false;
      }
      for (Object otherObj : otherList)
      {
        int found = 0;
        for (Object thisObj : list)
        {
          if (thisObj.equals(otherObj))
          {
            found++;
          }
        }
        // Not found
        if (found == 0)
        {
          return false;
        }
        // Should never see twice as dsInfo structure in a dsList
        assertFalse(found > 1);
      // Ok, found exactly once in the list, examine next structure
      }
      return true;
    }

    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder("TopoView:");
      sb.append("\n----------------------------\n");
      sb.append("CONNECTED DS SERVERS:\n");
      for (DSInfo dsInfo : dsList)
      {
        sb.append(dsInfo).append("\n----------------------------\n");
      }
      sb.append("CONNECTED RS SERVERS:\n");
      for (RSInfo rsInfo : rsList)
      {
        sb.append(rsInfo).append("\n----------------------------\n");
      }
      return sb.toString();
    }
  }

  private String getHostPort(int port)
  {
    return LOCAL_HOST_NAME + ":" + port;
  }
}
