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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.fail;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

/**
 * Test Server part of the assured feature in both safe data and
 * safe read modes.
 */
public class AssuredReplicationServerTest
  extends ReplicationTestCase
{

  private String testName = this.getClass().getSimpleName();
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();
  private int rs1Port = -1;
  private int rs2Port = -1;
  private int rs3Port = -1;
  private int rs4Port = -1;
  private static final short FDS1_ID = 1;
  private static final short FDS2_ID = 2;
  private static final short FDS3_ID = 3;
  private static final short FDS4_ID = 4;
  private static final short FDS5_ID = 5;
  private static final short FDS6_ID = 6;
  private static final short FDS7_ID = 7;
  private static final short FDS8_ID = 8;
  private static final short FDS9_ID = 9;
  private static final short FDS10_ID = 10;
  private static final short FDS11_ID = 11;
  private static final short FDS12_ID = 12;
  private static final short FRS1_ID = 51;
  private static final short FRS2_ID = 52;
  private static final short FRS3_ID = 53;
  private static final short DS_FRS2_ID = FRS2_ID + 10;
  private static final short RS1_ID = 101;
  private static final short RS2_ID = 102;
  private static final short RS3_ID = 103;
  private static final short RS4_ID = 104;
  private FakeReplicationDomain fakeRd1 = null;
  private FakeReplicationDomain fakeRd2 = null;
  private FakeReplicationDomain fakeRd3 = null;
  private FakeReplicationDomain fakeRd4 = null;
  private FakeReplicationDomain fakeRd5 = null;
  private FakeReplicationDomain fakeRd6 = null;
  private FakeReplicationDomain fakeRd7 = null;
  private FakeReplicationDomain fakeRd8 = null;
  private FakeReplicationDomain fakeRd9 = null;
  private FakeReplicationDomain fakeRd10 = null;
  private FakeReplicationDomain fakeRd11 = null;
  private FakeReplicationDomain fakeRd12 = null;
  private FakeReplicationServer fakeRs1 = null;
  private FakeReplicationServer fakeRs2 = null;
  private FakeReplicationServer fakeRs3 = null;
  private ReplicationServer rs1 = null;
  private ReplicationServer rs2 = null;
  private ReplicationServer rs3 = null;
  private ReplicationServer rs4 = null;

  // Small assured timeout value (timeout to be used in first RS receiving an
  // assured update from a DS)
  private static final int SMALL_TIMEOUT = 3000;
  // Long assured timeout value (timeout to use in DS when sending an assured
  // update)
  private static final int LONG_TIMEOUT = 5000;
  // Expected max time for sending an assured update and receive its ack
  // (without errors)
  private static final int MAX_SEND_UPDATE_TIME = 2000;

  // Default group id
  private static final int DEFAULT_GID = 1;
  // Other group ids
  private static final int OTHER_GID = 2;
  private static final int OTHER_GID_BIS = 3;

  // Default generation id
  private static long DEFAULT_GENID = EMPTY_DN_GENID;
  // Other generation id
  private static long OTHER_GENID = 500L;

  /*
   * Definitions for the scenario of the fake DS
   */
  // DS receives updates and replies acks with no errors to every updates
  private static final int REPLY_OK_DS_SCENARIO = 1;
  // DS receives updates but does not respond (makes timeouts)
  private static final int TIMEOUT_DS_SCENARIO = 2;
  // DS receives updates and replies ack with replay error flags
  private static final int REPLAY_ERROR_DS_SCENARIO = 3;

  /*
   * Definitions for the scenario of the fake RS
   */
  // RS receives updates and replies acks with no errors to every updates
  private static final int REPLY_OK_RS_SCENARIO = 11;
  // RS receives updates but does not respond (makes timeouts)
  private static final int TIMEOUT_RS_SCENARIO = 12;
  // RS is used for sending updates (with sendNewFakeUpdate()) and receive acks, synchronously
  private static final int SENDER_RS_SCENARIO = 13;  
  //   Scenarios only used in safe read tests:
  // RS receives updates and replies ack error as if a DS was connected to it and timed out
  private static final int DS_TIMEOUT_RS_SCENARIO_SAFE_READ = 14;
  // RS receives updates and replies ack error as if a DS was connected to it and was wrong status
  private static final int DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ = 15;
  // RS receives updates and replies ack error as if a DS was connected to it and had a replay error
  private static final int DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ = 16;

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }

  /**
   * Before starting the tests configure some stuff
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    // Find  a free port for the replication servers
    ServerSocket socket1 = TestCaseUtils.bindFreePort();
    ServerSocket socket2 = TestCaseUtils.bindFreePort();
    ServerSocket socket3 = TestCaseUtils.bindFreePort();
    ServerSocket socket4 = TestCaseUtils.bindFreePort();
    rs1Port = socket1.getLocalPort();
    rs2Port = socket2.getLocalPort();
    rs3Port = socket3.getLocalPort();
    rs4Port = socket4.getLocalPort();
    socket1.close();
    socket2.close();
    socket3.close();
    socket4.close();
  }

  private void initTest()
  {
    fakeRd1 = null;
    fakeRd2 = null;
    fakeRd3 = null;
    fakeRd4 = null;
    fakeRd5 = null;
    fakeRd6 = null;
    fakeRd7 = null;
    fakeRd8 = null;
    fakeRd9 = null;
    fakeRd10 = null;
    fakeRd11 = null;
    fakeRd12 = null;
    fakeRs1 = null;
    fakeRs2 = null;
    fakeRs3 = null;
    rs1 = null;
    rs2 = null;
    rs3 = null;
    rs4 = null;
  }

  private void endTest()
  {
    // Shutdown fake DSs

    if (fakeRd1 != null)
    {
      fakeRd1.disableService();
      fakeRd1 = null;
    }

    if (fakeRd2 != null)
    {
      fakeRd2.disableService();
      fakeRd2 = null;
    }

    if (fakeRd3 != null)
    {
      fakeRd3.disableService();
      fakeRd3 = null;
    }

    if (fakeRd4 != null)
    {
      fakeRd4.disableService();
      fakeRd4 = null;
    }

    if (fakeRd5 != null)
    {
      fakeRd5.disableService();
      fakeRd5 = null;
    }

    if (fakeRd6 != null)
    {
      fakeRd6.disableService();
      fakeRd6 = null;
    }

    if (fakeRd7 != null)
    {
      fakeRd7.disableService();
      fakeRd7 = null;
    }

    if (fakeRd8 != null)
    {
      fakeRd8.disableService();
      fakeRd8 = null;
    }

    if (fakeRd9 != null)
    {
      fakeRd9.disableService();
      fakeRd9 = null;
    }

    if (fakeRd10 != null)
    {
      fakeRd10.disableService();
      fakeRd10 = null;
    }

    if (fakeRd11 != null)
    {
      fakeRd11.disableService();
      fakeRd11 = null;
    }

    if (fakeRd12 != null)
    {
      fakeRd12.disableService();
      fakeRd12 = null;
    }

    // Shutdown fake RSs

    if (fakeRs1 != null)
    {
      fakeRs1.shutdown();
      fakeRs1 = null;
    }

    if (fakeRs2 != null)
    {
      fakeRs2.shutdown();
      fakeRs2 = null;
    }

    if (fakeRs3 != null)
    {
      fakeRs3.shutdown();
      fakeRs3 = null;
    }

    // Shutdown RSs

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
    if (rs4 != null)
    {
      rs4.clearDb();
      rs4.remove();
      rs4 = null;
    }
  }
  /**
   * Creates and connects a new fake replication domain, using the passed scenario
   * (no server state constructor version)
   */
  private FakeReplicationDomain createFakeReplicationDomain(short serverId,
    int groupId, short rsId, long generationId, boolean assured,
    AssuredMode assuredMode, int safeDataLevel, long assuredTimeout,
    int scenario)
  {
    return createFakeReplicationDomain(serverId, groupId, rsId, generationId, assured,
      assuredMode, safeDataLevel, assuredTimeout, scenario, new ServerState(), true, 100);
  }

  /**
   * Creates and connects a new fake replication domain, using the passed scenario.
   */
  private FakeReplicationDomain createFakeReplicationDomain(short serverId,
    int groupId, short rsId, long generationId, boolean assured,
    AssuredMode assuredMode, int safeDataLevel, long assuredTimeout,
    int scenario, ServerState serverState)
  {
   return createFakeReplicationDomain(serverId, groupId, rsId, generationId, assured,
    assuredMode, safeDataLevel, assuredTimeout, scenario, serverState, true, 100);
  }

  /**
   * Creates a new fake replication domain, using the passed scenario.
   * If connect = true , we start both publish and listen service and publish
   * service uses the default window value. If false, we only start publish
   * service and use the passed window value
   */
  private FakeReplicationDomain createFakeReplicationDomain(short serverId,
    int groupId, short rsId, long generationId, boolean assured,
    AssuredMode assuredMode, int safeDataLevel, long assuredTimeout,
    int scenario, ServerState serverState, boolean startListen, int window)
  {
    try
    {
      // Set port to right real RS according to its id
      int rsPort = -1;
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
        case RS4_ID:
          rsPort = rs4Port;
          break;
        default:
          fail("Unknown RS id: " + rsId);
      }

      FakeReplicationDomain fakeReplicationDomain = new FakeReplicationDomain(
        TEST_ROOT_DN_STRING, serverId, generationId,
        (byte)groupId, assured, assuredMode, (byte)safeDataLevel, assuredTimeout,
        scenario, serverState);

      List<String> replicationServers = new ArrayList<String>();
      replicationServers.add("localhost:" + rsPort);
      fakeReplicationDomain.startPublishService(replicationServers, window, 1000);
      if (startListen)
        fakeReplicationDomain.startListenService();

      // Test connection
      assertTrue(fakeReplicationDomain.isConnected());
      int rdPort = -1;
      // Check connected server port
      String serverStr = fakeReplicationDomain.getReplicationServer();
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
      assertEquals(rdPort, rsPort);

      return fakeReplicationDomain;
    } catch (Exception e)
    {
      fail("createFakeReplicationDomain " + e.getMessage());
    }
    return null;
  }

  /**
   * Creates and connects a new fake replication server, using the passed scenario.
   */
  private FakeReplicationServer createFakeReplicationServer(short serverId,
    int groupId, short rsId, long generationId, boolean assured,
    AssuredMode assuredMode, int safeDataLevel, ServerState serverState, int scenario)
  {
    try
    {
      // Set port to right real RS according to its id
      int rsPort = -1;
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
          fail("Unknown RS id: " + rsId);
      }

      FakeReplicationServer fakeReplicationServer = new FakeReplicationServer(
        rsPort, serverId, assured, assuredMode, (byte)safeDataLevel, (byte)groupId,
        TEST_ROOT_DN_STRING, generationId);

      // Connect fake RS to the real RS
      assertTrue(fakeReplicationServer.connect(serverState));

      // Start wished scenario
      fakeReplicationServer.start(scenario);

      return fakeReplicationServer;
    } catch (Exception e)
    {
      fail("createFakeReplicationServer " + e.getMessage());
    }
    return null;
  }

  /**
   * Creates a new real replication server (one which is to be tested).
   */
  private ReplicationServer createReplicationServer(short serverId,
    int groupId, long assuredTimeout, String testCase)
  {
    SortedSet<String> replServers = new TreeSet<String>();
    try
    {
      int port = -1;
      if (serverId == RS1_ID)
      {
        port = rs1Port;
        if (testCase.equals("testSafeDataManyRealRSs") || testCase.equals("testSafeReadManyRSsAndDSs"))
        {
          // Every 3 RSs connected together
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);
          if (testCase.equals("testSafeReadManyRSsAndDSs"))
          {
           // Every 4 RSs connected together
           replServers.add("localhost:" + rs4Port);
          }
        } else if (testCase.equals("testSafeReadMultiGroups") || testCase.equals("testSafeReadTwoRSs"))
        {
          // Every 2 RSs connected together
          replServers.add("localhost:" + rs2Port);
        } else
        {
          // Let this server alone
        }
      } else if (serverId == RS2_ID)
      {
        port = rs2Port;
        if (testCase.equals("testSafeDataManyRealRSs") || testCase.equals("testSafeReadManyRSsAndDSs"))
        {
          // Every 3 RSs connected together
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs3Port);
          if (testCase.equals("testSafeReadManyRSsAndDSs"))
          {
           // Every 4 RSs connected together
           replServers.add("localhost:" + rs4Port);
          }
        } else if (testCase.equals("testSafeReadMultiGroups") || testCase.equals("testSafeReadTwoRSs"))
        {
          // Every 2 RSs connected together
          replServers.add("localhost:" + rs1Port);
        } else
        {
          // Let this server alone
        }
      } else if (serverId == RS3_ID)
      {
        port = rs3Port;
        if (testCase.equals("testSafeDataManyRealRSs") || testCase.equals("testSafeReadManyRSsAndDSs"))
        {
          // Every 3 RSs connected together
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs2Port);
          if (testCase.equals("testSafeReadManyRSsAndDSs"))
          {
           // Every 4 RSs connected together
           replServers.add("localhost:" + rs4Port);
          }
        } else
        {
          // Let this server alone
        }
      } else if (serverId == RS4_ID)
      {
        port = rs4Port;
        if (testCase.equals("testSafeReadManyRSsAndDSs"))
        {
          // Every 4 RSs connected together
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);
        } else
        {
          // Let this server alone
        }
      } else
      {
        fail("Unknown replication server id.");
      }

      String dir = testName + serverId + testCase + "Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(port, dir, 0, serverId, 0, 100,
        replServers, groupId, assuredTimeout, 5000);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      return replicationServer;

    } catch (Exception e)
    {
      fail("createReplicationServer " + e.getMessage());
    }
    return null;
  }

  /**
   * Fake replication domain implementation to test the replication server
   * regarding the assured feature.
   * According to the configured scenario, it will answer to updates with acks
   * as the scenario is requesting.
   */
  public class FakeReplicationDomain extends ReplicationDomain
  {
    // The scenario this DS is expecting

    private int scenario = -1;
    private long generationId = -1;
    private ProtocolSession session = null;

    private ChangeNumberGenerator gen = null;

    // False if a received update had assured parameters not as expected
    private boolean everyUpdatesAreOk = true;
    // Number of received updates
    private int nReceivedUpdates = 0;

    private boolean sameGidAsRs = true;

    private int nWrongReceivedUpdates = 0;

    /**
     * Creates a fake replication domain (DS)
     * @param serviceID The base dn used at connection to RS
     * @param serverID our server id
     * @param replicationServer the URS of the RS we will connect to
     * @param generationId the generation id we use at connection to real RS
     * @param groupId our group id
     * @param assured do we expect incoming assured updates (also used for outgoing updates)
     * @param assuredMode the expected assured mode of the incoming updates (also used for outgoing updates)
     * @param safeDataLevel the expected safe data level of the incoming updates (also used for outgoing updates)
     * @param assuredTimeout the assured timeout used when sending updates
     * @param scenario the scenario we are creating for (implies particular
     * behavior upon reception of updates)
     * @throws org.opends.server.config.ConfigException
     */
    public FakeReplicationDomain(
      String serviceID,
      short serverID,
      long generationId,
      byte groupId,
      boolean assured,
      AssuredMode assuredMode,
      byte safeDataLevel,
      long assuredTimeout,
      int scenario,
      ServerState serverState) throws ConfigException
    {
      super(serviceID, serverID, serverState);
      this.generationId = generationId;
      setGroupId(groupId);
      setAssured(assured);
      setAssuredMode(assuredMode);
      setAssuredSdLevel(safeDataLevel);
      setAssuredTimeout(assuredTimeout);
      this.scenario = scenario;

      gen = new ChangeNumberGenerator(serverID, 0L);
    }

    public boolean receivedUpdatesOk()
    {
      return everyUpdatesAreOk;
    }

    public int getReceivedUpdates()
    {
      return nReceivedUpdates;
    }

    public int getWrongReceivedUpdates()
    {
      return nWrongReceivedUpdates;
    }

    /**
     * To get the session reference to be able to send our own acks
     */
    @Override
    public void sessionInitiated(
      ServerStatus initStatus,
      ServerState replicationServerState,
      long generationId,
      ProtocolSession session)
    {
      super.sessionInitiated(initStatus, replicationServerState, generationId, session);
      this.session = session;
    }

    @Override
    public long countEntries() throws DirectoryException
    {
      // Not needed for this test
      return -1;
    }

    @Override
    protected void exportBackend(OutputStream output) throws DirectoryException
    {
      // Not needed for this test
    }

    @Override
    public long getGenerationID()
    {
      return generationId;
    }

    @Override
    protected void importBackend(InputStream input) throws DirectoryException
    {
      // Not needed for this test
    }

    @Override
    public boolean processUpdate(UpdateMsg updateMsg)
    {

      checkUpdateAssuredParameters(updateMsg);
      nReceivedUpdates++;

      // Now execute the requested scenario
      switch (scenario)
      {
        case REPLY_OK_DS_SCENARIO:
          // Send the ack without errors
          // Call processUpdateDone and update the server state is what needs to
          // be done when using asynchronous process update mechanism
          // (see processUpdate javadoc)
          processUpdateDone(updateMsg, null);
          getServerState().update(updateMsg.getChangeNumber());
          break;
        case TIMEOUT_DS_SCENARIO:
          // Let timeout occur
          break;
        case REPLAY_ERROR_DS_SCENARIO:
          // Send the ack with replay error
          // Call processUpdateDone and update the server state is what needs to
          // be done when using asynchronous process update mechanism
          // (see processUpdate javadoc)
          processUpdateDone(updateMsg, "This is the replay error message generated from fake DS " +
            getServerId() + " for update with change number " + updateMsg.
            getChangeNumber());
          getServerState().update(updateMsg.getChangeNumber());
          break;
        default:
          fail("Unknown scenario: " + scenario);
      }
      // IMPORTANT: return false so that we use the asynchronous processUpdate mechanism
      // (see processUpdate javadoc)
      return false;
    }

    /**
     * Check that received update assured parameters are as defined at DS start
     */
    private void checkUpdateAssuredParameters(UpdateMsg updateMsg)
    {
      boolean ok = true;
      if (updateMsg.isAssured() != isAssured())
      {
        debugInfo("Fake DS " + getServerId() + " received update assured flag is wrong: " + updateMsg);
        ok = false;
      }
      if (updateMsg.getAssuredMode() !=  getAssuredMode())
      {
        debugInfo("Fake DS " + getServerId() + " received update assured mode is wrong: " + updateMsg);
        ok = false;
      }
      if (updateMsg.getSafeDataLevel() != getAssuredSdLevel())
      {
        debugInfo("Fake DS " + getServerId() + " received update assured sd level is wrong: " + updateMsg);
        ok = false;
      }

      if (ok)
        debugInfo("Fake DS " + getServerId() + " received update assured parameters are ok: " + updateMsg);
      else
      {
        everyUpdatesAreOk = false;
        nWrongReceivedUpdates++;
      }
    }

    /**
     * Sends a new update from this DS
     * @throws TimeoutException If timeout waiting for an assured ack
     */
    public void sendNewFakeUpdate() throws TimeoutException
    {
      sendNewFakeUpdate(true);
    }

    /**
     * Sends a new update from this DS using configured assured parameters or not
     * @throws TimeoutException If timeout waiting for an assured ack
     */
    public void sendNewFakeUpdate(boolean useAssured) throws TimeoutException
    {

      // Create a new delete update message (the simplest to create)
      DeleteMsg delMsg = new DeleteMsg(getServiceID(), gen.newChangeNumber(),
        UUID.randomUUID().toString());

      // Send it (this uses the defined assured conf at constructor time)
      if (useAssured)
        prepareWaitForAckIfAssuredEnabled(delMsg);
      publish(delMsg);
      if (useAssured)
        waitForAckIfAssuredEnabled(delMsg);
    }
  }

  /**
   * The fake replication server used to emulate RS behavior the way we want
   * for assured features test.
   * This fake replication server is able to receive another RS connection only.
   * According to the configured scenario, it will answer to updates with acks
   * as the scenario is requesting.
   */
  private static int fakePort = 0;

  private class FakeReplicationServer extends Thread
  {

    private boolean shutdown = false;
    private ProtocolSession session = null;

    // Parameters given at constructor time
    private int port;
    private short serverId = -1;
    boolean isAssured = false; // Default value for config
    AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE; // Default value for config
    byte safeDataLevel = (byte) 1; // Default value for config
    private String baseDn = null;
    private long generationId = -1L;
    private byte groupId = (byte) -1;
    private boolean sslEncryption = false;
    // The scenario this RS is expecting
    private int scenario = -1;

    private ChangeNumberGenerator gen = null;

    // False if a received update had assured parameters not as expected
    private boolean everyUpdatesAreOk = true;
    // Number of received updates
    private int nReceivedUpdates = 0;

    // True if an ack has been replied to a received assured update (in assured mode of course)
    // used in reply scenario
    private boolean ackReplied = false;

    /**
     * Creates a fake replication server
     * @param port port of the real RS we will connect to
     * @param serverId our server id
     * @param assured do we expect incoming assured updates (also used for outgoing updates)
     * @param assuredMode the expected assured mode of the incoming updates (also used for outgoing updates)
     * @param safeDataLevel the expected safe data level of the incoming updates (also used for outgoing updates)
     * @param groupId our group id
     * @param baseDn the basedn we connect with, to the real RS
     * @param generationId the generation id we use at connection to real RS
     */
    public FakeReplicationServer(int port, short serverId, boolean assured,
      AssuredMode assuredMode, int safeDataLevel,
      byte groupId, String baseDn, long generationId)
    {
      this.port = port;
      this.serverId = serverId;
      this.baseDn = baseDn;
      this.generationId = generationId;
      this.groupId = groupId;
      this.isAssured = assured;
      this.assuredMode = assuredMode;
      this.safeDataLevel = (byte) safeDataLevel;

      gen = new ChangeNumberGenerator((short)(serverId + 10), 0L);
    }

    /*
     * Make the RS send an assured message and return the ack
     * message it receives from the RS
     */
    public AckMsg sendNewFakeUpdate() throws SocketTimeoutException
    {
      try
      {

        // Create a new delete update message (the simplest to create)
        DeleteMsg delMsg = new DeleteMsg(baseDn, gen.newChangeNumber(),
        UUID.randomUUID().toString());

        // Send del message in assured mode
        delMsg.setAssured(isAssured);
        delMsg.setAssuredMode(assuredMode);
        delMsg.setSafeDataLevel(safeDataLevel);
        session.publish(delMsg);

        // Read and return matching ack
        AckMsg ackMsg = null;
        ReplicationMsg replMsg = session.receive();
        if (replMsg instanceof ErrorMsg)
        {
          // Support for connection done with bad gen id : we receive an error
          // message that we must throw away before reading our ack.
          replMsg = session.receive();
        }
        ackMsg = (AckMsg)replMsg;

        return ackMsg;

      } catch(SocketTimeoutException e)
      {
        throw e;
      } catch (Throwable t)
      {
        fail("Unexpected exception in fake replication server sendNewFakeUpdate " +
          "processing: " + t);
        return null;
      }
    }

    /**
     * Connect to RS
     * Returns true if connection was made successfully
     */
    public boolean connect(ServerState serverState)
    {
      try
      {
        // Create and connect socket
        InetSocketAddress serverAddr =
          new InetSocketAddress("localhost", port);
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(serverAddr, 500);

        // Create client session
        fakePort++;
        String fakeUrl = "localhost:" + fakePort;
        ReplSessionSecurity replSessionSecurity = new ReplSessionSecurity();
        session = replSessionSecurity.createClientSession(fakeUrl, socket,
          ReplSessionSecurity.HANDSHAKE_TIMEOUT);

        // Send our repl server start msg
        ReplServerStartMsg replServerStartMsg = new ReplServerStartMsg(serverId,
          fakeUrl, baseDn, 100, serverState,
          ProtocolVersion.getCurrentVersion(), generationId, sslEncryption,
          groupId, 5000);
        session.publish(replServerStartMsg);

        // Read repl server start msg
        ReplServerStartMsg inReplServerStartMsg = (ReplServerStartMsg) session.
          receive();

        sslEncryption = inReplServerStartMsg.getSSLEncryption();
        if (!sslEncryption)
        {
          session.stopEncryption();
        }

        // Send our topo mesg
        RSInfo rsInfo = new RSInfo(serverId, generationId, groupId);
        List<RSInfo> rsInfos = new ArrayList<RSInfo>();
        rsInfos.add(rsInfo);
        TopologyMsg topoMsg = new TopologyMsg(null, rsInfos);
        session.publish(topoMsg);

        // Read topo msg
        TopologyMsg inTopoMsg = (TopologyMsg) session.receive();
        debugInfo("Fake RS " + serverId + " handshake received the following info:" + inTopoMsg);

      } catch (Throwable ex)
      {
        fail("Could not connect to replication server. Error in RS " + serverId +
          " :" + ex.getMessage());
        return false;
      }
      return true;
    }

    /**
     * Starts the fake RS, expecting and testing the passed scenario.
     */
    public void start(int scenario)
    {

      // Store expected test case
      this.scenario = scenario;

      if (scenario == SENDER_RS_SCENARIO)
      {
        // Do not start the listening thread and let the main thread receive
        // receive acks in sendNewFakeUpdate()
        return;
      }

      // Start listening
      start();
    }

    /**
     * Wait for DS connections
     */
    @Override
    public void run()
    {
      try
      {
        // Loop receiving and treating updates
        while (!shutdown)
        {
          try
          {
            ReplicationMsg replicationMsg = session.receive();

            if (!(replicationMsg instanceof UpdateMsg))
            {
              debugInfo("Fake RS " + serverId + " received non update message: " +
                replicationMsg);
              continue;
            }

            UpdateMsg updateMsg = (UpdateMsg) replicationMsg;
            checkUpdateAssuredParameters(updateMsg);
            nReceivedUpdates++;

            // Now execute the requested scenario
            switch (scenario)
            {
              case REPLY_OK_RS_SCENARIO:
                if (updateMsg.isAssured())
                {
                  // Send the ack without errors
                  AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber());
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              case TIMEOUT_RS_SCENARIO:
                // Let timeout occur
                break;
              case DS_TIMEOUT_RS_SCENARIO_SAFE_READ:
                if (updateMsg.isAssured())
                {
                  // Emulate RS waiting for virtual DS ack
                  sleep(MAX_SEND_UPDATE_TIME);
                  // Send the ack with timeout error from a virtual DS with id (ours + 10)
                  AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber());
                  ackMsg.setHasTimeout(true);
                  List<Short> failedServers = new ArrayList<Short>();
                  failedServers.add((short)(serverId + 10));
                  ackMsg.setFailedServers(failedServers);
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              case DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ:
                if (updateMsg.isAssured())
                {
                  // Send the ack with wrong status error from a virtual DS with id (ours + 10)
                  AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber());
                  ackMsg.setHasWrongStatus(true);
                  List<Short> failedServers = new ArrayList<Short>();
                  failedServers.add((short)(serverId + 10));
                  ackMsg.setFailedServers(failedServers);
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              case DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ:
                if (updateMsg.isAssured())
                {
                  // Send the ack with replay error from a virtual DS with id (ours + 10)
                  AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber());
                  ackMsg.setHasReplayError(true);
                  List<Short> failedServers = new ArrayList<Short>();
                  failedServers.add((short)(serverId + 10));
                  ackMsg.setFailedServers(failedServers);
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              default:
                fail("Unknown scenario: " + scenario);
            }
          } catch (SocketTimeoutException toe)
          {
            // We may timeout reading, in this case just re-read
            debugInfo("Fake RS " + serverId + " : " + toe.
              getMessage() + " (this is normal)");
          }
        }
      } catch (Throwable th)
      {
        debugInfo("Terminating thread of fake RS " + serverId + " :" + th.
          getMessage());
      // Probably thread closure from main thread
      }
    }

    /**
     * Shutdown the Replication Server service and all its connections.
     */
    public void shutdown()
    {
      if (shutdown)
      {
        return;
      }

      shutdown = true;

      /*
       * Shutdown any current client handling code
       */
      try
      {
        if (session != null)
        {
          session.close();
        }
      } catch (IOException e)
      {
        // ignore.
      }

      try
      {
        join();
      } catch (InterruptedException ie)
      {
      }
    }

    /**
     * Check that received update assured parameters are as defined at RS start
     */
    private void checkUpdateAssuredParameters(UpdateMsg updateMsg)
    {
      boolean ok = true;
      if (updateMsg.isAssured() != isAssured)
      {
        debugInfo("Fake RS " + serverId + " received update assured flag is wrong: " + updateMsg);
        ok = false;
      }
      if (updateMsg.getAssuredMode() !=  assuredMode)
      {
        debugInfo("Fake RS " + serverId + " received update assured mode is wrong: " + updateMsg);
        ok = false;
      }
      if (updateMsg.getSafeDataLevel() != safeDataLevel)
      {
        debugInfo("Fake RS " + serverId + " received update assured sd level is wrong: " + updateMsg);
        ok = false;
      }

      if (ok)
        debugInfo("Fake RS " + serverId + " received update assured parameters are ok: " + updateMsg);
      else
        everyUpdatesAreOk = false;
    }

    public boolean receivedUpdatesOk()
    {
      return everyUpdatesAreOk;
    }

    public int getReceivedUpdates()
    {
      return nReceivedUpdates;
    }

    /**
     * Test if the last received updates was acknowledged (ack sent with or without errors)
     * WARNING: this must be called once per update as it also immediatly resets the status
     * for a new test for the next update
     * @return True if acknowledged
     */
    public boolean ackReplied()
    {
      boolean result = ackReplied;
      // reset ack replied status
      ackReplied = false;
      return result;
    }
  }

  /**
   * Sleep a while
   */
  private void sleep(long time)
  {
    try
    {
      Thread.sleep(time);
    } catch (InterruptedException ex)
    {
      fail("Error sleeping " + ex);
    }
  }

  /**
   * See testSafeDataLevelOne comment.
   * This is a facility to run the testSafeDataLevelOne in precommit in simplest
   * case, so that precommit run test something and is not long.
   * testSafeDataLevelOne will run in nightly tests (groups = "slow")
   */
  @Test(enabled = true)
  public void testSafeDataLevelOnePrecommit() throws Exception
  {
    testSafeDataLevelOne(DEFAULT_GID, false, false, DEFAULT_GID, DEFAULT_GID);
  }

  /**
   * Returns possible combinations of parameters for testSafeDataLevelOne test
   */
  @DataProvider(name = "testSafeDataLevelOneProvider")
  private Object[][] testSafeDataLevelOneProvider()
  {
    return new Object[][]
    {
    { DEFAULT_GID, false, false, DEFAULT_GID, DEFAULT_GID},
    { DEFAULT_GID, false, false, OTHER_GID, DEFAULT_GID},
    { DEFAULT_GID, false, false, DEFAULT_GID, OTHER_GID},
    { DEFAULT_GID, false, false, OTHER_GID, OTHER_GID},
    { DEFAULT_GID, true, false, DEFAULT_GID, DEFAULT_GID},
    { DEFAULT_GID, true, false, OTHER_GID, DEFAULT_GID},
    { DEFAULT_GID, true, false, DEFAULT_GID, OTHER_GID},
    { DEFAULT_GID, true, false, OTHER_GID, OTHER_GID},
    { DEFAULT_GID, false, true, DEFAULT_GID, DEFAULT_GID},
    { DEFAULT_GID, false, true, OTHER_GID, DEFAULT_GID},
    { DEFAULT_GID, false, true, DEFAULT_GID, OTHER_GID},
    { DEFAULT_GID, false, true, OTHER_GID, OTHER_GID},
    { DEFAULT_GID, true, true, DEFAULT_GID, DEFAULT_GID},
    { DEFAULT_GID, true, true, OTHER_GID, DEFAULT_GID},
    { DEFAULT_GID, true, true, DEFAULT_GID, OTHER_GID},
    { DEFAULT_GID, true, true, OTHER_GID, OTHER_GID},
    { OTHER_GID, false, false, DEFAULT_GID, DEFAULT_GID},
    { OTHER_GID, false, false, OTHER_GID, DEFAULT_GID},
    { OTHER_GID, false, false, DEFAULT_GID, OTHER_GID},
    { OTHER_GID, false, false, OTHER_GID, OTHER_GID},
    { OTHER_GID, true, false, DEFAULT_GID, DEFAULT_GID},
    { OTHER_GID, true, false, OTHER_GID, DEFAULT_GID},
    { OTHER_GID, true, false, DEFAULT_GID, OTHER_GID},
    { OTHER_GID, true, false, OTHER_GID, OTHER_GID},
    { OTHER_GID, false, true, DEFAULT_GID, DEFAULT_GID},
    { OTHER_GID, false, true, OTHER_GID, DEFAULT_GID},
    { OTHER_GID, false, true, DEFAULT_GID, OTHER_GID},
    { OTHER_GID, false, true, OTHER_GID, OTHER_GID},
    { OTHER_GID, true, true, DEFAULT_GID, DEFAULT_GID},
    { OTHER_GID, true, true, OTHER_GID, DEFAULT_GID},
    { OTHER_GID, true, true, DEFAULT_GID, OTHER_GID},
    { OTHER_GID, true, true, OTHER_GID, OTHER_GID}
    };
  }

  /**
   * Test that the RS is able to acknowledge SD updates sent by SD, with level 1.
   * - 1 main fake DS connected to 1 RS, with same GID as RS or not
   * - 1 optional other fake DS connected to RS, with same GID as RS or not
   * - 1 optional other fake RS connected to RS, with same GID as RS or not
   * All possible combinations tested thanks to the provider
   */
  @Test(dataProvider = "testSafeDataLevelOneProvider", groups = "slow", enabled = true)
  public void testSafeDataLevelOne(int mainDsGid, boolean otherFakeDS, boolean fakeRS, int otherFakeDsGid, int fakeRsGid) throws Exception
  {
    String testCase = "testSafeDataLevelOne";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start real RS (the one to be tested)
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      /*
       * Start main DS (the one which sends updates)
       */

      // Create and connect fake domain 1 to RS 1
      // Assured mode: SD, level 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, mainDsGid, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_DATA_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      /*
       * Start one other fake DS
       */

      // Put another fake domain connected to real RS ?
      if (otherFakeDS)
      {
        // Assured set to false as RS should forward change without assured requested
        // Timeout scenario used so that no reply is made if however the real RS
        // by mistake sends an assured error and expects an ack from this DS:
        // this would timeout. If main DS group id is not the same as the real RS one,
        // the update will even not come to real RS as asured
        fakeRd2 = createFakeReplicationDomain(FDS2_ID, otherFakeDsGid, RS1_ID,
          DEFAULT_GENID, false, AssuredMode.SAFE_DATA_MODE, 1, LONG_TIMEOUT,
          TIMEOUT_DS_SCENARIO);
        assertNotNull(fakeRd2);
      }

      /*
       * Start 1 fake Rs
       */

      // Put a fake RS connected to real RS ?
      if (fakeRS)
      {
        // Assured set to false as RS should forward change without assured requested
        // Timeout scenario used so that no reply is made if however the real RS
        // by mistake sends an assured error and expects an ack from this fake RS:
        // this would timeout. If main DS group id is not the same as the real RS one,
        // the update will even not come to real RS as asured
        fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRsGid, RS1_ID,
          DEFAULT_GENID, false, AssuredMode.SAFE_DATA_MODE, 1, new ServerState(), TIMEOUT_RS_SCENARIO);
        assertNotNull(fakeRs1);
      }

      // Send update from DS 1
      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }

      // Check call time (should have last a lot less than long timeout)
      // (ack received if group id of DS and real RS are the same, no ack requested
      // otherwise)
      long sendUpdateTime = System.currentTimeMillis() - startTime;
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      if (mainDsGid == DEFAULT_GID)
      {
        // Check monitoring values (check that ack has been correctly received)
        assertEquals(fakeRd1.getAssuredSdSentUpdates(), 1);
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), 1);
        assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), 0);
        assertEquals(fakeRd1.getAssuredSdServerTimeoutUpdates().size(), 0);
      } else
      {
        // Check monitoring values (DS group id (OTHER_GID) is not the same as RS one
        // (DEFAULT_GID) so update should have been sent in normal mode
        assertEquals(fakeRd1.getAssuredSdSentUpdates(), 0);
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), 0);
        assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), 0);
        assertEquals(fakeRd1.getAssuredSdServerTimeoutUpdates().size(), 0);
      }

      // Sanity check
      sleep(500); // Let time to update to reach other servers
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());
      if (otherFakeDS)
      {
        assertEquals(fakeRd2.getReceivedUpdates(), 1);
        assertTrue(fakeRd2.receivedUpdatesOk());
      }
      if (fakeRS)
      {
        assertEquals(fakeRs1.getReceivedUpdates(), 1);
        assertTrue(fakeRs1.receivedUpdatesOk());
      }
    } finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeDataLevelHighPrecommit test
   */
  @DataProvider(name = "testSafeDataLevelHighPrecommitProvider")
  private Object[][] testSafeDataLevelHighPrecommitProvider()
  {
    return new Object[][]
    {

      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO}
    };
  }

  /**
   * See testSafeDataLevelHigh comment.
   */
  @Test(dataProvider = "testSafeDataLevelHighPrecommitProvider", groups = "slow", enabled = true)
  public void testSafeDataLevelHighPrecommit(int sdLevel, boolean otherFakeDS, int otherFakeDsGid, long otherFakeDsGenId,
    int fakeRs1Gid, long fakeRs1GenId, int fakeRs1Scen, int fakeRs2Gid, long fakeRs2GenId, int fakeRs2Scen,
    int fakeRs3Gid, long fakeRs3GenId, int fakeRs3Scen) throws Exception
  {
    testSafeDataLevelHigh(sdLevel, otherFakeDS, otherFakeDsGid, otherFakeDsGenId,
    fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, fakeRs2Gid, fakeRs2GenId, fakeRs2Scen,
    fakeRs3Gid, fakeRs3GenId, fakeRs3Scen);
  }

  /**
   * Returns possible combinations of parameters for testSafeDataLevelHighNightly test
   */
  @DataProvider(name = "testSafeDataLevelHighNightlyProvider")
  private Object[][] testSafeDataLevelHighNightlyProvider()
  {
    return new Object[][]
    {
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, TIMEOUT_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 2, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, TIMEOUT_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, OTHER_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, OTHER_GID, OTHER_GENID, TIMEOUT_RS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      { 3, true, DEFAULT_GID, DEFAULT_GENID, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, OTHER_GENID, REPLY_OK_RS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO}

    };
  }

  /**
   * See testSafeDataLevelHigh comment.
   */
  @Test(dataProvider = "testSafeDataLevelHighNightlyProvider", groups = "slow", enabled = true)
  public void testSafeDataLevelHighNightly(int sdLevel, boolean otherFakeDS, int otherFakeDsGid, long otherFakeDsGenId,
    int fakeRs1Gid, long fakeRs1GenId, int fakeRs1Scen, int fakeRs2Gid, long fakeRs2GenId, int fakeRs2Scen,
    int fakeRs3Gid, long fakeRs3GenId, int fakeRs3Scen) throws Exception
  {
    testSafeDataLevelHigh(sdLevel, otherFakeDS, otherFakeDsGid, otherFakeDsGenId,
    fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, fakeRs2Gid, fakeRs2GenId, fakeRs2Scen,
    fakeRs3Gid, fakeRs3GenId, fakeRs3Scen);
  }

  /**
   * Returns possible combinations of parameters for testSafeDataLevelHigh test
   */
  @DataProvider(name = "testSafeDataLevelHighProvider")
  private Object[][] testSafeDataLevelHighProvider()
  {
    // Constrcut all possible combinations of parameters
    List<List<Object>> objectArrayList = new ArrayList<List<Object>>();

    // Safe Data Level
    objectArrayList = addPossibleParameters(objectArrayList, 2, 3);
    // Other fake DS
    objectArrayList = addPossibleParameters(objectArrayList, true, false);
    // Other fake DS group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Other fake DS generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Fake RS 1 group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Fake RS 1 generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Fake RS 1 scenario
    objectArrayList = addPossibleParameters(objectArrayList, REPLY_OK_RS_SCENARIO, TIMEOUT_RS_SCENARIO);
    // Fake RS 2 group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Fake RS 2 generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Fake RS 2 scenario
    objectArrayList = addPossibleParameters(objectArrayList, REPLY_OK_RS_SCENARIO, TIMEOUT_RS_SCENARIO);
    // Fake RS 3 group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Fake RS 3 generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Fake RS 3 scenario
    objectArrayList = addPossibleParameters(objectArrayList, REPLY_OK_RS_SCENARIO, TIMEOUT_RS_SCENARIO);

    Object[][] result = new Object[objectArrayList.size()][];
    int i = 0;
    for (List<Object> objectArray : objectArrayList)
    {
      result[i] = objectArray.toArray();
      i++;
    }

    debugInfo("testSafeDataLevelHighProvider: number of possible parameter combinations : " + i);

    return result;
  }

  // Helper for providers:
  // Modify the passed object array list adding to each already contained object array each
  // passed possible values.
  // Example: to create all possible parameter combinations for a test method which has 2 parameters:
  // one boolean then an integer, with both 2 possible values: {true|false} and {10|100}:
  //
  // List<List<Object>> objectArrayList = new ArrayList<List<Object>>();
  // // Possible bolean values
  // objectArrayList = addPossibleParameters(objectArrayList, true, false);
  // // Possible integer values
  // objectArrayList = addPossibleParameters(objectArrayList, 10, 100);
  // Object[][] result = new Object[objectArrayList.size()][];
  //    int i = 0;
  //    for (List<Object> objectArray : objectArrayList)
  //    {
  //      result[i] = objectArray.toArray();
  //      i++;
  //    }
  // return result;
  //
  // The provider will return the equivalent following Object[][]:
  // new Object[][]
  // {
  //   { true, 10},
  //   { true, 100},
  //   { false, 10},
  //   { false, 100}
  // };
  private List<List<Object>> addPossibleParameters(List<List<Object>> objectArrayList, Object... possibleParameters)
  {
    List<List<Object>> newObjectArrayList = new ArrayList<List<Object>>();

    if (objectArrayList.size() == 0)
    {
      // First time we add some parameters, create first object arrays
      // Add each possible parameter as initial parameter lists
      for (Object possibleParameter : possibleParameters)
      {
        // Create new empty list
        List<Object> newObjectArray = new ArrayList<Object>();
        // Add the new possible parameter
        newObjectArray.add(possibleParameter);
        // Store the new object array in the result list
        newObjectArrayList.add(newObjectArray);
      }
      return newObjectArrayList;
    }

    for (List<Object> objectArray : objectArrayList)
    {
      // Add each possible parameter to the already existing list
      for (Object possibleParameter : possibleParameters)
      {
        // Clone the existing object array
        List<Object> newObjectArray = new ArrayList<Object>();
        for (Object object : objectArray)
        {
          newObjectArray.add(object);
        }
        // Add the new possible parameter
        newObjectArray.add(possibleParameter);
        // Store the new object array in the result list
        newObjectArrayList.add(newObjectArray);
      }
    }

    return newObjectArrayList;
  }

  /**
   * Test that the RS is able to acknowledge SD updates with level higher than 1
   * and also to return errors is some servers timeout.
   * - 1 main fake DS connected to 1 RS
   * - 1 optional other fake DS connected to RS, with same GID as RS or not and same GENID as RS or not
   * - 3 optional other fake RSs connected to RS, with same GID as RS or not and same GENID as RS or not
   * All possible combinations tested thanks to the provider.
   * Fake RSs shutting down 1 after 1 to go from 3 available servers to 0. One update sent at each step.
   *
   * NOTE: the following unit test is disabled by default as its testSafeDataLevelHighProvider provider
   * provides every possible combinations of parameters. This test runs then for hours. We keep this provider
   * for occasional testing but we disable it.
   * A simpler set of parameters is instead used in enabled test methods (which run this method in fact):
   * - testSafeDataLevelHighPrecommit which is used for precommit and runs fast
   * - testSafeDataLevelHighNightly which is used in nightly tests and takes more time to execute
   */
  @Test(dataProvider = "testSafeDataLevelHighProvider", enabled = false)
  public void testSafeDataLevelHigh(int sdLevel, boolean otherFakeDS, int otherFakeDsGid, long otherFakeDsGenId,
    int fakeRs1Gid, long fakeRs1GenId, int fakeRs1Scen, int fakeRs2Gid, long fakeRs2GenId, int fakeRs2Scen,
    int fakeRs3Gid, long fakeRs3GenId, int fakeRs3Scen) throws Exception
  {
    String testCase = "testSafeDataLevelHigh";

    debugInfo("Starting " + testCase);

    assertTrue(sdLevel > 1);
    int nWishedServers = sdLevel - 1; // Number of fake RSs we want an ack from

    initTest();

    try
    {
      /*
       * Start real RS (the one to be tested)
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      /*
       * Start main DS (the one which sends updates)
       */

      // Create and connect fake domain 1 to RS 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_DATA_MODE, sdLevel, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      /*
       * Start one other fake DS
       */

      // Put another fake domain connected to real RS ?
      if (otherFakeDS)
      {
        fakeRd2 = createFakeReplicationDomain(FDS2_ID, otherFakeDsGid, RS1_ID,
          otherFakeDsGenId, false, AssuredMode.SAFE_DATA_MODE, sdLevel, LONG_TIMEOUT,
          TIMEOUT_DS_SCENARIO);
        assertNotNull(fakeRd2);
      }

      /*
       * Start 3 fake Rss
       */

      // Put a fake RS 1 connected to real RS
      fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRs1Gid, RS1_ID,
        fakeRs1GenId, ((fakeRs1Gid == DEFAULT_GID) ? true : false), AssuredMode.SAFE_DATA_MODE, sdLevel,
        new ServerState(), fakeRs1Scen);
      assertNotNull(fakeRs1);

      // Put a fake RS 2 connected to real RS
      fakeRs2 = createFakeReplicationServer(FRS2_ID, fakeRs2Gid, RS1_ID,
        fakeRs2GenId, ((fakeRs2Gid == DEFAULT_GID) ? true : false), AssuredMode.SAFE_DATA_MODE, sdLevel,
        new ServerState(), fakeRs2Scen);
      assertNotNull(fakeRs2);

      // Put a fake RS 3 connected to real RS
      fakeRs3 = createFakeReplicationServer(FRS3_ID, fakeRs3Gid, RS1_ID,
        fakeRs3GenId, ((fakeRs3Gid == DEFAULT_GID) ? true : false), AssuredMode.SAFE_DATA_MODE, sdLevel,
        new ServerState(), fakeRs3Scen);
      assertNotNull(fakeRs3);

      // Wait for connections to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 4);

      /***********************************************************************
       * Send update from DS 1 (3 fake RSs available) and check what happened
       ***********************************************************************/

      // Keep track of monitoring values for incremental test step
      int acknowledgedUpdates = fakeRd1.getAssuredSdAcknowledgedUpdates();
      int timeoutUpdates = fakeRd1.getAssuredSdTimeoutUpdates();
      Map<Short,Integer> serverErrors = fakeRd1.getAssuredSdServerTimeoutUpdates();
      // Compute the list of servers that are elligible for receiving an assured update
      List<Short> elligibleServers = computeElligibleServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs2Gid, fakeRs2GenId, fakeRs3Gid, fakeRs3GenId);
      // Compute the list of servers that are elligible for receiving an assured update and that are expected to effectively ack the update
      List<Short> expectedServers = computeExpectedServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, fakeRs2Gid, fakeRs2GenId, fakeRs2Scen, fakeRs3Gid, fakeRs3GenId, fakeRs3Scen);

      // Send update
      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(1, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceivedSafeData(1, otherFakeDS, otherFakeDsGenId, fakeRs1GenId, fakeRs2GenId, fakeRs3GenId, expectedServers);

      /***********************************************************************
       * Send update from DS 1 (2 fake RSs available) and check what happened
       ***********************************************************************/

      // Shutdown fake RS 3
      fakeRs3.shutdown();
      fakeRs3 = null;

      // Wait for disconnection to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 3);

      // Keep track of monitoring values for incremental test step
      acknowledgedUpdates = fakeRd1.getAssuredSdAcknowledgedUpdates();
      timeoutUpdates = fakeRd1.getAssuredSdTimeoutUpdates();
      serverErrors = fakeRd1.getAssuredSdServerTimeoutUpdates();
      // Compute the list of servers that are elligible for receiving an assured update
      elligibleServers = computeElligibleServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs2Gid, fakeRs2GenId, -1, -1L);
      // Compute the list of servers that are elligible for receiving an assured update and that are expected to effectively ack the update
      expectedServers = computeExpectedServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, fakeRs2Gid, fakeRs2GenId, fakeRs2Scen, -1, -1L, -1);

      // Send update
      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(2, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceivedSafeData(2, otherFakeDS, otherFakeDsGenId, fakeRs1GenId, fakeRs2GenId, -1L, expectedServers);

      /***********************************************************************
       * Send update from DS 1 (1 fake RS available) and check what happened
       ***********************************************************************/

      // Shutdown fake RS 2
      fakeRs2.shutdown();
      fakeRs2 = null;

      // Wait for disconnection to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 2);

      // Keep track of monitoring values for incremental test step
      acknowledgedUpdates = fakeRd1.getAssuredSdAcknowledgedUpdates();
      timeoutUpdates = fakeRd1.getAssuredSdTimeoutUpdates();
      serverErrors = fakeRd1.getAssuredSdServerTimeoutUpdates();
      // Compute the list of servers that are elligible for receiving an assured update
      elligibleServers = computeElligibleServersSafeData(fakeRs1Gid, fakeRs1GenId, -1, -1L, -1, -1L);
      // Compute the list of servers that are elligible for receiving an assured update and that are expected to effectively ack the update
      expectedServers = computeExpectedServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, -1, -1L, -1, -1, -1L, -1);

      // Send update
      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(3, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceivedSafeData(3, otherFakeDS, otherFakeDsGenId, fakeRs1GenId, -1L, -1L, expectedServers);

      /***********************************************************************
       * Send update from DS 1 (no fake RS available) and check what happened
       ***********************************************************************/

      // Shutdown fake RS 1
      fakeRs1.shutdown();
      fakeRs1 = null;

      // Wait for disconnection to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 1);

      // Keep track of monitoring values for incremental test step
      acknowledgedUpdates = fakeRd1.getAssuredSdAcknowledgedUpdates();
      timeoutUpdates = fakeRd1.getAssuredSdTimeoutUpdates();
      serverErrors = fakeRd1.getAssuredSdServerTimeoutUpdates();
      // Compute the list of servers that are elligible for receiving an assured update
      elligibleServers = computeElligibleServersSafeData(-1, -1L, -1, -1L, -1, -1L);
      // Compute the list of servers that are elligible for receiving an assured update and that are expected to effectively ack the update
      expectedServers = computeExpectedServersSafeData(-1, -1L, -1, -1, -1L, -1, -1, -1L, -1);

      // Send update
      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(4, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceivedSafeData(4, otherFakeDS, otherFakeDsGenId, -1L, -1L, -1L, expectedServers);
    } finally
    {
      endTest();
    }
  }

  // Check that the DSs and the fake RSs of the topology have received/acked what is expected according to the
  // test step (the number of updates)
  // -1 for a gen id means no need to test the matching fake RS
  private void checkWhatHasBeenReceivedSafeData(int nSentUpdates, boolean otherFakeDS, long otherFakeDsGenId, long fakeRs1GenId, long fakeRs2GenId, long fakeRs3GenId, List<Short> expectedServers)
  {

    // We should not receive our own update
    assertEquals(fakeRd1.getReceivedUpdates(), 0);
    assertTrue(fakeRd1.receivedUpdatesOk());

    // Check what received other fake DS
    if (otherFakeDS)
    {
      if (otherFakeDsGenId == DEFAULT_GENID)
      {
        // Update should have been received
        assertEquals(fakeRd2.getReceivedUpdates(), nSentUpdates);
        assertTrue(fakeRd2.receivedUpdatesOk());
      } else
      {
        assertEquals(fakeRd2.getReceivedUpdates(), 0);
        assertTrue(fakeRd2.receivedUpdatesOk());
      }
    }

    // Check what received/did fake Rss

    if (nSentUpdates < 4)  // Fake RS 3 is stopped after 3 updates sent
    {
      if (fakeRs1GenId != DEFAULT_GENID)
        assertEquals(fakeRs1.getReceivedUpdates(), 0);
      else
        assertEquals(fakeRs1.getReceivedUpdates(), nSentUpdates);
      assertTrue(fakeRs1.receivedUpdatesOk());
      if (expectedServers.contains(FRS1_ID))
        assertTrue(fakeRs1.ackReplied());
      else
        assertFalse(fakeRs1.ackReplied());
    }

    if (nSentUpdates < 3)  // Fake RS 3 is stopped after 2 updates sent
    {
      if (fakeRs2GenId != DEFAULT_GENID)
        assertEquals(fakeRs2.getReceivedUpdates(), 0);
      else
        assertEquals(fakeRs2.getReceivedUpdates(), nSentUpdates);
      assertTrue(fakeRs2.receivedUpdatesOk());
      if (expectedServers.contains(FRS2_ID))
        assertTrue(fakeRs2.ackReplied());
      else
        assertFalse(fakeRs2.ackReplied());
    }

    if (nSentUpdates < 2) // Fake RS 3 is stopped after 1 update sent
    {
      if (fakeRs3GenId != DEFAULT_GENID)
        assertEquals(fakeRs3.getReceivedUpdates(), 0);
      else
        assertEquals(fakeRs3.getReceivedUpdates(), nSentUpdates);
      assertTrue(fakeRs3.receivedUpdatesOk());
      if (expectedServers.contains(FRS3_ID))
        assertTrue(fakeRs3.ackReplied());
      else
        assertFalse(fakeRs3.ackReplied());
    }
  }

  /**
   * Check the time the sending of the safe data assured update took and the monitoring
   * values according to the test configuration
   */
  private void checkTimeAndMonitoringSafeData(int nSentUpdates, int prevNAckUpdates, int prevNTimeoutUpdates, Map<Short,Integer> prevNServerErrors, long sendUpdateTime,
    int nWishedServers, List<Short> elligibleServers, List<Short> expectedServers)
  {
    assertEquals(fakeRd1.getAssuredSdSentUpdates(), nSentUpdates);
    if (elligibleServers.size() >= nWishedServers) // Enough elligible servers
    {
      if (expectedServers.size() >= nWishedServers) // Enough servers should ack
      {
        // Enough server ok for acking: ack should come back quickly
        assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);
        // Check monitoring values (check that ack has been correctly received)
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates + 1);
        assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates);
        checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, null); // Should have same value as previous one
      } else
      {
        // Not enough expected servers: should have timed out in RS timeout
        // (SMALL_TIMEOUT)
        assertTrue((SMALL_TIMEOUT <= sendUpdateTime) && (sendUpdateTime <=
          LONG_TIMEOUT));
        // Check monitoring values (check that timeout occured)
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates);
        assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates + 1);
        // Check that the servers that are elligible but not expected have been added in the error by server list
        List<Short> expectedServersInError = computeExpectedServersInError(elligibleServers, expectedServers);
        checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, expectedServersInError);
      }
    } else // Not enough elligible servers
    {
      if (elligibleServers.size() > 0) // Some elligible servers anyway
      {
        if (expectedServers.size() == elligibleServers.size()) // All elligible servers should respond in time
        {
          // Enough server ok for acking: ack should come back quickly
          assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);
          // Check monitoring values (check that ack has been correctly received)
          assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates + 1);
          assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates);
          checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, null); // Should have same value as previous one
        } else
        { // Some elligible servers should fail
          // Not enough expected servers: should have timed out in RS timeout
          // (SMALL_TIMEOUT)
          assertTrue((SMALL_TIMEOUT <= sendUpdateTime) && (sendUpdateTime <=
            LONG_TIMEOUT));
          // Check monitoring values (check that timeout occured)
          assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates);
          assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates + 1);
          // Check that the servers that are elligible but not expected have been added in the error by server list
          List<Short> expectedServersInError = computeExpectedServersInError(elligibleServers, expectedServers);
          checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, expectedServersInError);
        }
      } else
      {
        // No elligible servers at all, RS should not wait for any ack and immediately ack the update
        assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);
        // Check monitoring values (check that ack has been correctly received)
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates + 1);
        assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates);
        checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, null); // Should have same value as previous one
      }
    }
  }

  // Compute a list of servers that are elligibles but that are not able to return an ack
  // (those in elligibleServers that are not in expectedServers). Result may of course be an empty list
  private List<Short> computeExpectedServersInError(List<Short> elligibleServers, List<Short> expectedServers)
  {
    List<Short> expectedServersInError = new ArrayList<Short>();
    for (Short serverId : elligibleServers)
    {
      if (!expectedServers.contains(serverId))
        expectedServersInError.add(serverId);
    }
    return expectedServersInError;
  }

  // Check that the passed list of errors by server ids is as expected.
  // - if expectedServersInError is not null and not empty, each server id in measuredServerErrors should have the value it has
  // in prevServerErrors + 1, or 1 if it was not in prevServerErrors
  // - if expectedServersInError is null or empty, both map should be equal
  private void checkServerErrors(Map<Short,Integer> measuredServerErrors, Map<Short,Integer> prevServerErrors, List<Short> expectedServersInError)
  {
    if (expectedServersInError != null)
    {
      // Adding an error to each server in expectedServersInError, with prevServerErrors as basis, should give the
      // same map as measuredServerErrors
      for (Short serverId : expectedServersInError)
      {
        Integer prevInt = prevServerErrors.get(serverId);
        if (prevInt == null)
        {
          // Add this server to the list of servers in error
          prevServerErrors.put(serverId, 1);
        } else
        {
          // Already errors for this server, increment the value
          int newVal = prevInt.intValue() + 1;
          prevServerErrors.put(serverId, newVal);
        }
      }
    }

    // Maps should be the same
    assertEquals(measuredServerErrors.size(), prevServerErrors.size());
    Set<Short> measuredKeySet = measuredServerErrors.keySet();
    for (Short serverId : measuredKeySet)
    {
      Integer measuredInt = measuredServerErrors.get(serverId);
      assertNotNull(measuredInt);
      assertTrue(measuredInt.intValue() != 0);
      Integer prevInt = prevServerErrors.get(serverId);
      assertNotNull(prevInt);
      assertTrue(prevInt.intValue() != 0);
      assertEquals(measuredInt, prevInt);
    }
  }

  /**
   * Wait until number of fake DSs and fake RSs are available in the topo view of the passed
   * fake DS or throw an assertion if timeout waiting.
   */
  private void waitForStableTopo(FakeReplicationDomain fakeRd, int expectedDs, int expectedRs)
  {
    int nSec = 30;
    int nDs = 0;
    int nRs = 0;
    List<DSInfo> dsInfo = null;
    List<RSInfo> rsInfo = null;
    while(nSec > 0)
    {
      dsInfo = fakeRd.getDsList();
      rsInfo = fakeRd.getRsList();
      nDs = dsInfo.size();
      nRs = rsInfo.size();
      if ( (nDs == expectedDs) && (nRs == expectedRs) ) // Must include real RS so '+1'
      {
        debugInfo("waitForStableTopo: expected topo obtained after " + (30-nSec) + " second(s).");
        return;
      }
      sleep(1000);
      nSec--;
    }
    fail("Did not reach expected topo view in time: expected " + expectedDs +
      " DSs (had " + dsInfo +") and " + expectedRs + " RSs (had " + rsInfo +").");
  }

  // Compute the list of servers that are elligible for receiving a safe data assured update
  // according to their group id and generation id. If -1 is used, the server is out of scope
  private List<Short> computeElligibleServersSafeData(int fakeRs1Gid, long fakeRs1GenId, int fakeRs2Gid, long fakeRs2GenId, int fakeRs3Gid, long fakeRs3GenId)
  {
    List<Short> elligibleServers = new ArrayList<Short>();
    if (areGroupAndGenerationIdOk(fakeRs1Gid, fakeRs1GenId))
    {
      elligibleServers.add(FRS1_ID);
    }
    if (areGroupAndGenerationIdOk(fakeRs2Gid, fakeRs2GenId))
    {
      elligibleServers.add(FRS2_ID);
    }
    if (areGroupAndGenerationIdOk(fakeRs3Gid, fakeRs3GenId))
    {
      elligibleServers.add(FRS3_ID);
    }
    return elligibleServers;
  }

  // Are group id and generation id ok for being an elligible RS for assured update ?
  private boolean areGroupAndGenerationIdOk(int fakeRsGid, long fakeRsGenId)
  {
    if ((fakeRsGid != -1) && (fakeRsGenId != -1L))
    {
      return ( (fakeRsGid == DEFAULT_GID) && (fakeRsGenId == DEFAULT_GENID) );
    }
    return false;
  }

  // Compute the list of servers that are elligible for receiving a safe data assured update and that are expected to effectively ack the update
  // If -1 is used, the server is out of scope
  private List<Short> computeExpectedServersSafeData(int fakeRs1Gid, long fakeRs1GenId, int fakeRs1Scen, int fakeRs2Gid, long fakeRs2GenId, int fakeRs2Scen, int fakeRs3Gid, long fakeRs3GenId, int fakeRs3Scen)
  {
    List<Short> exptectedServers = new ArrayList<Short>();
    if (areGroupAndGenerationIdOk(fakeRs1Gid, fakeRs1GenId))
    {
      if (fakeRs1Scen == REPLY_OK_RS_SCENARIO)
      {
        exptectedServers.add(FRS1_ID);
      } else if (fakeRs1Scen != TIMEOUT_RS_SCENARIO)
      {
        fail("No other scenario should be used here");
        return null;
      }
    }
    if (areGroupAndGenerationIdOk(fakeRs2Gid, fakeRs2GenId))
    {
      if (fakeRs2Scen == REPLY_OK_RS_SCENARIO)
      {
        exptectedServers.add(FRS2_ID);
      } else if (fakeRs2Scen != TIMEOUT_RS_SCENARIO)
      {
        fail("No other scenario should be used here");
        return null;
      }
    }
    if (areGroupAndGenerationIdOk(fakeRs3Gid, fakeRs3GenId))
    {
      if (fakeRs3Scen == REPLY_OK_RS_SCENARIO)
      {
        exptectedServers.add(FRS3_ID);
      } else if (fakeRs3Scen != TIMEOUT_RS_SCENARIO)
      {
        fail("No other scenario should be used here");
        return null;
      }
    }
    return exptectedServers;
  }

  /**
   * Returns possible combinations of parameters for testSafeDataFromRS test
   */
  @DataProvider(name = "testSafeDataFromRSProvider")
  private Object[][] testSafeDataFromRSProvider()
  {
    List<List<Object>> objectArrayList = new ArrayList<List<Object>>();

    // Safe Data Level
    objectArrayList = addPossibleParameters(objectArrayList, 1, 2, 3);
    // Fake RS group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Fake RS generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Fake RS sends update in assured mode
    objectArrayList = addPossibleParameters(objectArrayList, true, false);

    Object[][] result = new Object[objectArrayList.size()][];
    int i = 0;
    for (List<Object> objectArray : objectArrayList)
    {
      result[i] = objectArray.toArray();
      i++;
    }
    return result;
  }

  /**
   * Test that the RS is acking or not acking a safe data update sent from another
   * (fake) RS according to passed parameters
   */
  @Test(dataProvider = "testSafeDataFromRSProvider", groups = "slow", enabled = true)
  public void testSafeDataFromRS(int sdLevel, int fakeRsGid, long fakeRsGenId, boolean sendInAssured) throws Exception
  {
    String testCase = "testSafeDataFromRS";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start real RS (the one to be tested)
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      /*
       * Start fake RS to make the RS have the default generation id
       */

      // Put a fake RS 2 connected to real RS
      fakeRs2 = createFakeReplicationServer(FRS2_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_DATA_MODE, 10,
        new ServerState(), TIMEOUT_RS_SCENARIO);
      assertNotNull(fakeRs2);

      /*
       * Start fake RS to send updates
       */

      // Put a fake RS 1 connected to real RS
      fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRsGid, RS1_ID,
        fakeRsGenId, sendInAssured, AssuredMode.SAFE_DATA_MODE, sdLevel,
        new ServerState(), SENDER_RS_SCENARIO);
      assertNotNull(fakeRs1);

      /*
       * Send an assured update using configured assured parameters
       */

      long startTime = System.currentTimeMillis();
      AckMsg ackMsg = null;
      boolean timeout = false;
      try
      {
        ackMsg = fakeRs1.sendNewFakeUpdate();
      } catch (SocketTimeoutException e)
      {
        debugInfo("testSafeDataFromRS: timeout waiting for update ack");
        timeout = true;
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;
      debugInfo("testSafeDataFromRS: send update call time: " + sendUpdateTime);

      /*
       * Now check timeout or not according to test configuration parameters
       */
      if ( (sdLevel == 1) || (fakeRsGid != DEFAULT_GID) ||
        (fakeRsGenId != DEFAULT_GENID) || (!sendInAssured) )
      {
        // Should have timed out (no ack)
        assertTrue(timeout);
        assertNull(ackMsg);
      } else
      {
        // Ack should have been received
        assertFalse(timeout);
        assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);
        assertNotNull(ackMsg);
        assertFalse(ackMsg.hasTimeout());
        assertFalse(ackMsg.hasReplayError());
        assertFalse(ackMsg.hasWrongStatus());
        assertEquals(ackMsg.getFailedServers().size(), 0);
      }

   } finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeDataManyRealRSs test
   */
  @DataProvider(name = "testSafeDataManyRealRSsProvider")
  private Object[][] testSafeDataManyRealRSsProvider()
  {
    return new Object[][]
    {
      {1},
      {2},
      {3},
      {4}
    };
  }

  /**
   * Test topo of 3 real RSs.
   * One assured safe data update sent with different safe data level.
   * Update should always be acked
   */
  @Test(dataProvider = "testSafeDataManyRealRSsProvider", enabled = true)
  public void testSafeDataManyRealRSs(int sdLevel) throws Exception
  {
    String testCase = "testSafeDataManyRealRSs";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /*
       * Start 3 real RSs
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      // Create real RS 2
      rs2 = createReplicationServer(RS2_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs2);

      // Create real RS 3
      rs3 = createReplicationServer(RS3_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs3);

      /*
       * Start DS that will send updates
       */

      // Wait for RSs to connect together
      // Create and connect fake domain 1 to RS 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_DATA_MODE, sdLevel, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      // Wait for RSs connections to be finished
      // DS must see expected numbers of RSs
      waitForStableTopo(fakeRd1, 0, 3);

      /*
       * Send update from DS 1 and check result
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSdSentUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSdServerTimeoutUpdates().size(), 0);
    } finally
    {
      endTest();
    }
  }

  /**
   * Test safe read mode with only one real RS deployment. One fake DS sends
   * assured messages to one other fake DS connected to the RS a fake RS
   * connected to the real RS is also expected to send the ack
   */
  @Test(enabled = true)
  public void testSafeReadOneRSBasic() throws Exception
  {
    String testCase = "testSafeReadOneRSBasic";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*******************
       * Start real RS (the one to be tested)
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      /*******************
       * Start main DS 1 (the one which sends updates)
       */

      // Create and connect DS 1 to RS 1
      // Assured mode: SR
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      /*
       * Send a first assured safe read update
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      /*******************
       * Start another fake DS 2 connected to RS
       */

      // Create and connect DS 2 to RS 1
      // Assured mode: SR
      ServerState serverState = fakeRd1.getServerState();
      fakeRd2 = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO, serverState);
      assertNotNull(fakeRd2);

      // Wait for connections to be established
      waitForStableTopo(fakeRd1, 1, 1);

      /*
       * Send a second assured safe read update
       */

      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 2);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 2);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 1);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 1);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      assertEquals(fakeRd2.getReceivedUpdates(), 1);
      assertTrue(fakeRd2.receivedUpdatesOk());

      /*******************
       * Start a fake RS 1 connected to RS
       */

      fakeRs1 = createFakeReplicationServer(FRS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1,
        fakeRd1.getServerState(), REPLY_OK_RS_SCENARIO);
      assertNotNull(fakeRs1);

      // Wait for connections to be established
      waitForStableTopo(fakeRd1, 1, 2);

      /*
       * Send a third assured safe read update
       */

      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 3);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 3);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 2);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 2);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      assertEquals(fakeRd2.getReceivedUpdates(), 2);
      assertTrue(fakeRd2.receivedUpdatesOk());

      assertEquals(fakeRs1.getReceivedUpdates(), 1);
      assertTrue(fakeRs1.receivedUpdatesOk());

      /*******************
       * Shutdown fake DS 2
       */

      // Shutdown fake DS 2
      fakeRd2.disableService();
      fakeRd2 = null;

      // Wait for disconnection to be finished
      waitForStableTopo(fakeRd1, 0, 2);

      /*
       * Send a fourth assured safe read update
       */

      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      assertEquals(fakeRs1.getReceivedUpdates(), 2);
      assertTrue(fakeRs1.receivedUpdatesOk());

      /*******************
       * Shutdown fake RS 1
       */

      // Shutdown fake RS 1
      fakeRs1.shutdown();
      fakeRs1 = null;

      // Wait for disconnection to be finished
      waitForStableTopo(fakeRd1, 0, 1);

      /*
       * Send a fifth assured safe read update
       */

      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 5);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 5);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());
    } finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeReadOneRSComplexPrecommit test
   */
  @DataProvider(name = "testSafeReadOneRSComplexPrecommitProvider")
  private Object[][] testSafeReadOneRSComplexPrecommitProvider()
  {
    return new Object[][]
    {
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, TIMEOUT_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, REPLAY_ERROR_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, TIMEOUT_RS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, DS_TIMEOUT_RS_SCENARIO_SAFE_READ},
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ},
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ},
      {OTHER_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, DEFAULT_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO, OTHER_GID, DEFAULT_GENID, REPLY_OK_RS_SCENARIO}
    };
  }

  /**
   * See testSafeReadOneRSComplex comment.
   */
  @Test(dataProvider = "testSafeReadOneRSComplexPrecommitProvider", groups = "slow", enabled = true)
  public void testSafeReadOneRSComplexPrecommit(int otherFakeDsGid, long otherFakeDsGenId, int otherFakeDsScen,
    int otherFakeRsGid, long otherFakeRsGenId, int otherFakeRsScen) throws Exception
  {
    testSafeReadOneRSComplex(otherFakeDsGid, otherFakeDsGenId, otherFakeDsScen,
    otherFakeRsGid, otherFakeRsGenId, otherFakeRsScen);
  }

  /**
   * Returns possible combinations of parameters for testSafeReadOneRSComplex test
   */
  @DataProvider(name = "testSafeReadOneRSComplexProvider")
  private Object[][] testSafeReadOneRSComplexProvider()
  {
    List<List<Object>> objectArrayList = new ArrayList<List<Object>>();

    // Other additional DS group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Other additional DS generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Other additional DS scenario
    objectArrayList = addPossibleParameters(objectArrayList, REPLY_OK_DS_SCENARIO, TIMEOUT_DS_SCENARIO, REPLAY_ERROR_DS_SCENARIO);
    // Other additional RS group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Other additional RS generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Other additional RS scenario
    objectArrayList = addPossibleParameters(objectArrayList, REPLY_OK_RS_SCENARIO, TIMEOUT_RS_SCENARIO, DS_TIMEOUT_RS_SCENARIO_SAFE_READ, DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ, DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ);

    Object[][] result = new Object[objectArrayList.size()][];
    int i = 0;
    for (List<Object> objectArray : objectArrayList)
    {
      result[i] = objectArray.toArray();
      i++;
    }
    return result;
  }

  /**
   * Test safe read mode with only one real RS deployment.
   * Test that the RS is able to acknowledge SR updates with level higher than 1
   * and also to return errors is some errors occur.
   * - 1 main fake DS connected to the RS
   * - 1 other fake DS connected to the RS, with same GID as RS and same GENID as RS and always acking without error
   * - 1 other fake DS connected to the RS, with GID, GENID, scenario...changed through the provider
   * - 1 fake RS connected to the RS (emulating one fake DS connected to it), with same GID as RS and always acking without error
   * - 1 other fake RS connected to the RS (emulating one fake DS connected to it), with GID scenario...changed through the provider
   *
   * All possible combinations tested thanks to the provider.
   */
  @Test(dataProvider = "testSafeReadOneRSComplexProvider", groups = "slow", enabled = false) // Working but disabled as 17.5 minutes to run
  public void testSafeReadOneRSComplex(int otherFakeDsGid, long otherFakeDsGenId, int otherFakeDsScen,
    int otherFakeRsGid, long otherFakeRsGenId, int otherFakeRsScen) throws Exception
  {
    String testCase = "testSafeReadOneRSComplex";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start real RS (the one to be tested)
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      /*
       * Start main DS 1 (the one which sends updates)
       */

      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      /*
       * Start another fake DS 2 connected to RS
       */
   
      fakeRd2 = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd2);
     
      /*
       * Start another fake DS 3 connected to RS
       */
      
      fakeRd3 = createFakeReplicationDomain(FDS3_ID, otherFakeDsGid, RS1_ID,
        otherFakeDsGenId, ((otherFakeDsGid == DEFAULT_GID) ? true : false),
        AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        otherFakeDsScen);
      assertNotNull(fakeRd3);
      
      /*
       * Start fake RS (RS 1) connected to RS
       */
      
      fakeRs1 = createFakeReplicationServer(FRS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1,
        new ServerState(), REPLY_OK_RS_SCENARIO);
      assertNotNull(fakeRs1);
      
      /*
       * Start another fake RS (RS 2) connected to RS
       */
      
      fakeRs2 = createFakeReplicationServer(FRS2_ID, otherFakeRsGid, RS1_ID,
        otherFakeRsGenId, ((otherFakeRsGid == DEFAULT_GID) ? true : false),
        AssuredMode.SAFE_READ_MODE, 1, new ServerState(), otherFakeRsScen);
      assertNotNull(fakeRs2);

      // Wait for connections to be established
      waitForStableTopo(fakeRd1, 2, 3);

      /*
       * Send an assured safe read update
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Compute some thing that will help determine what to check according to
      // the current test configurarion: compute if DS and RS subject to conf
      // change are elligible and expected for safe read assured
      // elligible: the server should receive the ack request
      // expected: the server should send back an ack (with or without error)
      boolean dsIsEligible = areGroupAndGenerationIdOk(otherFakeDsGid, otherFakeDsGenId);
      boolean rsIsEligible = areGroupAndGenerationIdOk(otherFakeRsGid, otherFakeRsGenId);
      boolean dsIsExpected = false;
      boolean rsIsExpected = false;
      // Booleans to tell if we expect to see the timeout, wrong status and replay error flags
      boolean shouldSeeTimeout = false;
      boolean shouldSeeWrongStatus = false;
      boolean shouldSeeReplayError = false;
      // Booleans to tell if we expect to see the ds, rs and virtual ds connected to fake rs in server id error list
      boolean shouldSeeDsIdInError = false;
      boolean shouldSeeRsIdInError = false;
      boolean shouldSeeDsRsIdInError = false;
      if (dsIsEligible)
      {
        switch (otherFakeDsScen)
        {
          case REPLY_OK_DS_SCENARIO:
            dsIsExpected = true;
            break;
          case TIMEOUT_DS_SCENARIO:
            shouldSeeDsIdInError = true;
            shouldSeeTimeout = true;
            break;
          case REPLAY_ERROR_DS_SCENARIO:
            shouldSeeDsIdInError = true;
            shouldSeeReplayError = true;
            break;
          default:
            fail("No other scenario should be used here");
        }
      }
      if (rsIsEligible)
      {
        switch (otherFakeRsScen)
        {
          case REPLY_OK_RS_SCENARIO:
            rsIsExpected = true;
            break;
          case TIMEOUT_RS_SCENARIO:
            shouldSeeRsIdInError = true;
            shouldSeeTimeout = true;
            break;
          case DS_TIMEOUT_RS_SCENARIO_SAFE_READ:
            shouldSeeDsRsIdInError = true;
            shouldSeeTimeout = true;
            break;
          case DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ:
            shouldSeeDsRsIdInError = true;
            shouldSeeReplayError = true;
            break;
          case DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ:
            shouldSeeDsRsIdInError = true;
            shouldSeeWrongStatus = true;
            break;
          default:
            fail("No other scenario should be used here");
        }
      }

      if (!shouldSeeTimeout)
      {
        // Call time should have been short
        assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);
      } else // Timeout
      {
        if (shouldSeeDsRsIdInError) // Virtual DS timeout
        {
          // Should have timed out
          assertTrue((MAX_SEND_UPDATE_TIME <= sendUpdateTime) && (sendUpdateTime <=
            LONG_TIMEOUT));
        } else // Normal rimeout case
        {
          // Should have timed out
          assertTrue((SMALL_TIMEOUT <= sendUpdateTime) && (sendUpdateTime <=
            LONG_TIMEOUT));
        }
      }

      // Sleep a while as counters are updated just after sending thread is unblocked
      sleep(500);

      // Check monitoring values in DS 1
      //
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 1);
      if (( (otherFakeDsGid == DEFAULT_GID) && (otherFakeDsGenId == DEFAULT_GENID) && (otherFakeDsScen != REPLY_OK_DS_SCENARIO) )
         || ( (otherFakeRsGid == DEFAULT_GID) && (otherFakeRsGenId == DEFAULT_GENID) && (otherFakeRsScen != REPLY_OK_RS_SCENARIO) ))
      {
        assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 0);
        assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 1);
      }
      else
      {
        assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 1);
        assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 0);
      }


      if (shouldSeeTimeout)
        assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 1);
      else
        assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
      if (shouldSeeWrongStatus)
        assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 1);
      else
        assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      if (shouldSeeReplayError)
        assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 1);
      else
        assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);

      // Check for servers in error list
      Map<Short, Integer> expectedErrors = new HashMap<Short, Integer>();
      if (shouldSeeDsIdInError)
        expectedErrors.put(FDS3_ID, 1);
      if (shouldSeeRsIdInError)
        expectedErrors.put(FRS2_ID, 1);
      if (shouldSeeDsRsIdInError)
        expectedErrors.put(DS_FRS2_ID, 1);
      checkServerErrorListsAreEqual(fakeRd1.getAssuredSrServerNotAcknowledgedUpdates(), expectedErrors);
      
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Check monitoring values in DS 2
      //
      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 1);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 1);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      // Check monitoring values in DS 3
      //
      assertEquals(fakeRd3.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      if (dsIsEligible)
      {
        assertEquals(fakeRd3.getAssuredSrReceivedUpdates(), 1);
        if (dsIsExpected)
        {
          assertEquals(fakeRd3.getAssuredSrReceivedUpdatesAcked(), 1);
          assertEquals(fakeRd3.getAssuredSrReceivedUpdatesNotAcked(), 0);
        } else
        {
          if (shouldSeeReplayError && (otherFakeDsScen == REPLAY_ERROR_DS_SCENARIO))
          {
            // Replay error for the other DS
            assertEquals(fakeRd3.getAssuredSrReceivedUpdatesAcked(), 0);
            assertEquals(fakeRd3.getAssuredSrReceivedUpdatesNotAcked(), 1);
          } else
          {
            assertEquals(fakeRd3.getAssuredSrReceivedUpdatesAcked(), 0);
            assertEquals(fakeRd3.getAssuredSrReceivedUpdatesNotAcked(), 0);
          }
        }
      }
      else
      {
        assertEquals(fakeRd3.getAssuredSrReceivedUpdates(), 0);
        assertEquals(fakeRd3.getAssuredSrReceivedUpdatesAcked(), 0);
        assertEquals(fakeRd3.getAssuredSrReceivedUpdatesNotAcked(), 0);
      }

      // Sanity check
      //
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      assertEquals(fakeRd2.getReceivedUpdates(), 1);
      assertTrue(fakeRd2.receivedUpdatesOk());

      if (otherFakeDsGenId == DEFAULT_GENID)
        assertEquals(fakeRd3.getReceivedUpdates(), 1);
      else
        assertEquals(fakeRd3.getReceivedUpdates(), 0);
      assertTrue(fakeRd3.receivedUpdatesOk());

      assertEquals(fakeRs1.getReceivedUpdates(), 1);
      assertTrue(fakeRs1.receivedUpdatesOk());

      if (otherFakeRsGenId == DEFAULT_GENID)
        assertEquals(fakeRs2.getReceivedUpdates(), 1);
      else
        assertEquals(fakeRs2.getReceivedUpdates(), 0);
      assertTrue(fakeRs2.receivedUpdatesOk());

    } finally
    {
      endTest();
    }
  }

  /**
   * Check that the passed server error lists are equivalent
   */
  private void checkServerErrorListsAreEqual(Map<Short, Integer> list1, Map<Short, Integer> list2)
  {
    assertNotNull(list1);
    assertNotNull(list2);
    assertEquals(list1.size(), list2.size());
    for (Short s : list1.keySet())
    {
      assertEquals(list1.get(s), list2.get(s));
    }
  }

  /**
   * Test safe read mode with some real RSs and some fake DSs connected to each one of them.
   * Every other fake DSs should receive and ack the update sent from the main fake DS
   * Includes some RSs and DSs with wrong group id or gen id that should not receive
   * an assured version of the update
   * Topology:
   * - 4 real RSs (RS1,RS2,RS3 with same GID and RS4 with different GID 2), connected together
   * - + 1 fake RS1 connected to RS1 with different GENID
   * - + 1 fake RS2 connected to RS1 with different GID 2
   * - connected to RS1:
   *   - fake DS1 (main one that will send the assured update)
   *   - fake DS2
   *   - fake DS6 with different GID
   *   - fake DS10 with different GENID
   * - connected to RS2:
   *   - fake DS3
   *   - fake DS7 with different GID
   *   - fake DS11 with different GENID
   * - connected to RS3:
   *   - fake DS4
   *   - fake DS5
   *   - fake DS8 with different GID
   *   - fake DS12 with different GENID
   * - connected to RS4:
   *   - fake DS9 with different GID 2
   */
  @Test(enabled = true)
  public void testSafeReadManyRSsAndDSs() throws Exception
  {
    String testCase = "testSafeReadManyRSsAndDSs";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start 4 real RSs
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      // Create real RS 2
      rs2 = createReplicationServer(RS2_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs2);

      // Create real RS 3
      rs3 = createReplicationServer(RS3_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs3);

      // Create real RS 4 (different GID 2)
      rs4 = createReplicationServer(RS4_ID, OTHER_GID_BIS, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs4);

      /*
       * Start DS 1 that will send assured updates
       */

      // Wait for RSs to connect together
      // Create and connect fake domain 1 to RS 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      // -> if everybody is connected we are sure a GENID is set in every RSs and
      // we can connect the fake RS with a different GENID
      waitForStableTopo(fakeRd1, 0, 4);

      /*
       * Start 2 fake RSs
       */

      // Put a fake RS 1 connected to real RS 2 (different GENID)
      fakeRs1 = createFakeReplicationServer(FRS1_ID, DEFAULT_GID, RS1_ID,
        OTHER_GENID, false, AssuredMode.SAFE_READ_MODE, 1, new ServerState(),
        TIMEOUT_RS_SCENARIO);
      assertNotNull(fakeRs1);

      // Put a fake RS 2 connected to real RS 3 (different GID 2)
      fakeRs2 = createFakeReplicationServer(FRS2_ID, OTHER_GID_BIS, RS1_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, new ServerState(),
        TIMEOUT_RS_SCENARIO);
      assertNotNull(fakeRs2);

      /*
       * Start DSs that will receive and ack the updates from DS 1
       */

      // DS 2 connected to RS 1
      fakeRd2 = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd2);

      // DS 3 connected to RS 2
      fakeRd3 = createFakeReplicationDomain(FDS3_ID, DEFAULT_GID, RS2_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd3);

      // DS 4 connected to RS 3
      fakeRd4 = createFakeReplicationDomain(FDS4_ID, DEFAULT_GID, RS3_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd4);

      // DS 5 connected to RS 3
      fakeRd5 = createFakeReplicationDomain(FDS5_ID, DEFAULT_GID, RS3_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd5);

      /*
       * Start DSs that will not receive updates from DS 1 as assured because
       * they have different GID
       */

      // DS 6 connected to RS 1
      fakeRd6 = createFakeReplicationDomain(FDS6_ID, OTHER_GID, RS1_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd6);

      // DS 7 connected to RS 2
      fakeRd7 = createFakeReplicationDomain(FDS7_ID, OTHER_GID, RS2_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd7);

      // DS 8 connected to RS 3
      fakeRd8 = createFakeReplicationDomain(FDS8_ID, OTHER_GID, RS3_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd8);

      // DS 9 (GID 2) connected to RS 4
      fakeRd9 = createFakeReplicationDomain(FDS9_ID, OTHER_GID_BIS, RS4_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd9);

      /*
       * Start DSs that will not receive updates from DS 1 because
       * they have different GENID
       */

      // DS 10 connected to RS 1
      fakeRd10 = createFakeReplicationDomain(FDS10_ID, DEFAULT_GID, RS1_ID,
        OTHER_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd10);

      // DS 11 connected to RS 2
      fakeRd11 = createFakeReplicationDomain(FDS11_ID, DEFAULT_GID, RS2_ID,
        OTHER_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd11);

      // DS 12 connected to RS 3
      fakeRd12 = createFakeReplicationDomain(FDS12_ID, DEFAULT_GID, RS3_ID,
        OTHER_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd12);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      waitForStableTopo(fakeRd1, 11, 6);

      /*
       * Send update from DS 1 and check result
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 1);

      //   normal DSs
      checkDSReceivedAndAcked(fakeRd2, 1);
      checkDSReceivedAndAcked(fakeRd3, 1);
      checkDSReceivedAndAcked(fakeRd4, 1);
      checkDSReceivedAndAcked(fakeRd5, 1);

      //   different GID DSs
      checkDSReceivedAndAcked(fakeRd6, 0);
      checkDSReceivedAndAcked(fakeRd7, 0);
      checkDSReceivedAndAcked(fakeRd8, 0);
      checkDSReceivedAndAcked(fakeRd9, 0);

      //   different GENID DSs
      checkDSReceivedAndAcked(fakeRd10, 0);
      checkDSReceivedAndAcked(fakeRd11, 0);
      checkDSReceivedAndAcked(fakeRd12, 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      //   normal DSs
      assertEquals(fakeRd2.getReceivedUpdates(), 1);
      assertTrue(fakeRd2.receivedUpdatesOk());
      assertEquals(fakeRd3.getReceivedUpdates(), 1);
      assertTrue(fakeRd3.receivedUpdatesOk());
      assertEquals(fakeRd4.getReceivedUpdates(), 1);
      assertTrue(fakeRd4.receivedUpdatesOk());
      assertEquals(fakeRd5.getReceivedUpdates(), 1);
      assertTrue(fakeRd5.receivedUpdatesOk());

      //   different GID DSs
      assertEquals(fakeRd6.getReceivedUpdates(), 1);
      assertTrue(fakeRd6.receivedUpdatesOk());
      assertEquals(fakeRd7.getReceivedUpdates(), 1);
      assertTrue(fakeRd7.receivedUpdatesOk());
      assertEquals(fakeRd8.getReceivedUpdates(), 1);
      assertTrue(fakeRd8.receivedUpdatesOk());
      assertEquals(fakeRd9.getReceivedUpdates(), 1);
      assertTrue(fakeRd9.receivedUpdatesOk());

      //   different GENID DSs
      assertEquals(fakeRd10.getReceivedUpdates(), 0);
      assertTrue(fakeRd10.receivedUpdatesOk());
      assertEquals(fakeRd11.getReceivedUpdates(), 0);
      assertTrue(fakeRd11.receivedUpdatesOk());
      assertEquals(fakeRd12.getReceivedUpdates(), 0);
      assertTrue(fakeRd12.receivedUpdatesOk());

      //   fake RSs
      assertEquals(fakeRs1.getReceivedUpdates(), 0);
      assertTrue(fakeRs1.receivedUpdatesOk());
      assertFalse(fakeRs1.ackReplied());
      assertEquals(fakeRs2.getReceivedUpdates(), 1);
      assertTrue(fakeRs2.receivedUpdatesOk());
      assertFalse(fakeRs2.ackReplied());

      /*
       * Send a second update from DS 1 and check result
       */

      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 2);

      //   normal DSs
      checkDSReceivedAndAcked(fakeRd2, 2);
      checkDSReceivedAndAcked(fakeRd3, 2);
      checkDSReceivedAndAcked(fakeRd4, 2);
      checkDSReceivedAndAcked(fakeRd5, 2);

      //   different GID DSs
      checkDSReceivedAndAcked(fakeRd6, 0);
      checkDSReceivedAndAcked(fakeRd7, 0);
      checkDSReceivedAndAcked(fakeRd8, 0);
      checkDSReceivedAndAcked(fakeRd9, 0);

      //   different GENID DSs
      checkDSReceivedAndAcked(fakeRd10, 0);
      checkDSReceivedAndAcked(fakeRd11, 0);
      checkDSReceivedAndAcked(fakeRd12, 0);

      // Sanity check
      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());

      //   normal DSs
      assertEquals(fakeRd2.getReceivedUpdates(), 2);
      assertTrue(fakeRd2.receivedUpdatesOk());
      assertEquals(fakeRd3.getReceivedUpdates(), 2);
      assertTrue(fakeRd3.receivedUpdatesOk());
      assertEquals(fakeRd4.getReceivedUpdates(), 2);
      assertTrue(fakeRd4.receivedUpdatesOk());
      assertEquals(fakeRd5.getReceivedUpdates(), 2);
      assertTrue(fakeRd5.receivedUpdatesOk());

      //   different GID DSs
      assertEquals(fakeRd6.getReceivedUpdates(), 2);
      assertTrue(fakeRd6.receivedUpdatesOk());
      assertEquals(fakeRd7.getReceivedUpdates(), 2);
      assertTrue(fakeRd7.receivedUpdatesOk());
      assertEquals(fakeRd8.getReceivedUpdates(), 2);
      assertTrue(fakeRd8.receivedUpdatesOk());
      assertEquals(fakeRd9.getReceivedUpdates(), 2);
      assertTrue(fakeRd9.receivedUpdatesOk());

      //   different GENID DSs
      assertEquals(fakeRd10.getReceivedUpdates(), 0);
      assertTrue(fakeRd10.receivedUpdatesOk());
      assertEquals(fakeRd11.getReceivedUpdates(), 0);
      assertTrue(fakeRd11.receivedUpdatesOk());
      assertEquals(fakeRd12.getReceivedUpdates(), 0);
      assertTrue(fakeRd12.receivedUpdatesOk());

      //   fake RSs
      assertEquals(fakeRs1.getReceivedUpdates(), 0);
      assertTrue(fakeRs1.receivedUpdatesOk());
      assertFalse(fakeRs1.ackReplied());
      assertEquals(fakeRs2.getReceivedUpdates(), 2);
      assertTrue(fakeRs2.receivedUpdatesOk());
      assertFalse(fakeRs2.ackReplied());
    } finally
    {
      endTest();
    }
  }

  // Helper method for some safe read test methods
  private void checkDSReceivedAndAcked(FakeReplicationDomain fakeRd, int nPacket)
  {
    assertEquals(fakeRd.getAssuredSrSentUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrAcknowledgedUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrNotAcknowledgedUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrTimeoutUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrWrongStatusUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrReplayErrorUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
    assertEquals(fakeRd.getAssuredSrReceivedUpdates(), nPacket);
    assertEquals(fakeRd.getAssuredSrReceivedUpdatesAcked(), nPacket);
    assertEquals(fakeRd.getAssuredSrReceivedUpdatesNotAcked(), 0);
  }

  // Helper method for some safe read test methods
  private void checkDSSentAndAcked(FakeReplicationDomain fakeRd, int nPacket)
  {
    assertEquals(fakeRd.getAssuredSrSentUpdates(), nPacket);
    assertEquals(fakeRd.getAssuredSrAcknowledgedUpdates(), nPacket);
    assertEquals(fakeRd.getAssuredSrNotAcknowledgedUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrTimeoutUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrWrongStatusUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrReplayErrorUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
    assertEquals(fakeRd.getAssuredSrReceivedUpdates(), 0);
    assertEquals(fakeRd.getAssuredSrReceivedUpdatesAcked(), 0);
    assertEquals(fakeRd.getAssuredSrReceivedUpdatesNotAcked(), 0);
  }

  /**
   * Test that a safe read update does not cross different group id topologies
   * in assured mode.
   * Topology:
   * DS1(GID=1)---RS1(GID=1)---RS2(GID=2)---DS3(GID=2)
   * DS2(GID=1)---/                     \---DS4(GID=2)
   *
   */
  @Test(enabled = true)
  public void testSafeReadMultiGroups() throws Exception
  {
    String testCase = "testSafeReadMultiGroups";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start 2 real RSs
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs1);

      // Create real RS 2
      rs2 = createReplicationServer(RS2_ID, OTHER_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs2);

      /*
       * Start DSs with GID=DEFAULT_GID, connected to RS1
       */

      // DS 1 connected to RS 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      // DS 2 connected to RS 1
      fakeRd2 = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd2);

      /*
       * Start DSs with GID=OTHER_GID, connected to RS2
       */

      // DS 3 connected to RS 2
      fakeRd3 = createFakeReplicationDomain(FDS3_ID, OTHER_GID, RS2_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd3);

      // DS 4 connected to RS 3
      fakeRd4 = createFakeReplicationDomain(FDS4_ID, OTHER_GID, RS2_ID,
        DEFAULT_GENID, false, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO);
      assertNotNull(fakeRd4);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      waitForStableTopo(fakeRd1, 3, 2);

      /*
       * Send update from DS 1 and check result
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 1);

      checkDSReceivedAndAcked(fakeRd2, 1);

      assertEquals(fakeRd3.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd3.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd3.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd3.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd4.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd4.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd4.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd4.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());
      assertEquals(fakeRd2.getReceivedUpdates(), 1);
      assertTrue(fakeRd2.receivedUpdatesOk());
      assertEquals(fakeRd3.getReceivedUpdates(), 1);
      assertTrue(fakeRd3.receivedUpdatesOk());
      assertEquals(fakeRd4.getReceivedUpdates(), 1);
      assertTrue(fakeRd4.receivedUpdatesOk());
      } finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeReadTwoRSsProvider test
   */
  @DataProvider(name = "testSafeReadTwoRSsProvider")
  private Object[][] testSafeReadTwoRSsProvider()
  {
    return new Object[][]
    {
      {DEFAULT_GID, DEFAULT_GENID, REPLY_OK_DS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, TIMEOUT_DS_SCENARIO},
      {DEFAULT_GID, DEFAULT_GENID, REPLAY_ERROR_DS_SCENARIO},
      {OTHER_GID, DEFAULT_GENID, TIMEOUT_DS_SCENARIO},
      {DEFAULT_GID, OTHER_GENID, TIMEOUT_DS_SCENARIO}
    };
  }

  /**
   * Test that a safe read update is correctly handled on a DS located on
   * another RS and according to the remote DS configuration
   * Topology:
   * DS1---RS1---RS2---DS2 (DS2 with changing configuration)
   *
   */
  @Test(dataProvider = "testSafeReadTwoRSsProvider", groups = "slow", enabled = true)
  public void testSafeReadTwoRSs(int fakeDsGid, long fakeDsGenId, int fakeDsScen) throws Exception
  {
    String testCase = "testSafeReadTwoRSs";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start 2 real RSs
       */

      // Create real RS 1
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT + 1000, // Be sure DS2 timeout is seen from DS1
        testCase);
      assertNotNull(rs1);

      // Create real RS 2
      rs2 = createReplicationServer(RS2_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase);
      assertNotNull(rs2);

      /*
       * Start 2 fake DSs
       */

      // DS 1 connected to RS 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      // DS 2 connected to RS 2
      fakeRd2 = createFakeReplicationDomain(FDS2_ID, fakeDsGid, RS2_ID,
        fakeDsGenId, (fakeDsGid == DEFAULT_GID ? true : false),
        AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, fakeDsScen);
      assertNotNull(fakeRd2);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      waitForStableTopo(fakeRd1, 1, 2);

      /*
       * Send update from DS 1 and check result
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      boolean fakeDsIsEligible = areGroupAndGenerationIdOk(fakeDsGid,
        fakeDsGenId);

      // Check call time
      if (fakeDsIsEligible && (fakeDsScen == TIMEOUT_DS_SCENARIO))
        assertTrue((SMALL_TIMEOUT <= sendUpdateTime) && (sendUpdateTime <=
          (SMALL_TIMEOUT + 1000)));
      else
        assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      if (fakeDsIsEligible)
      {
        switch (fakeDsScen)
        {
          case REPLY_OK_DS_SCENARIO:
            checkDSSentAndAcked(fakeRd1, 1);
            checkDSReceivedAndAcked(fakeRd2, 1);
            break;
          case TIMEOUT_DS_SCENARIO:
            assertEquals(fakeRd1.getAssuredSrSentUpdates(), 1);
            assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 1);
            assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 1);
            assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
            Map<Short, Integer> failedServer = fakeRd1.getAssuredSrServerNotAcknowledgedUpdates();
            assertEquals(failedServer.size(), 1);
            Integer nError = failedServer.get(FDS2_ID);
            assertNotNull(nError);
            assertEquals(nError.intValue(), 1);
            assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
            assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

            assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
            assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 1);
            assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 0);
            assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);
            break;
          case REPLAY_ERROR_DS_SCENARIO:
            assertEquals(fakeRd1.getAssuredSrSentUpdates(), 1);
            assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 1);
            assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 1);
            failedServer = fakeRd1.getAssuredSrServerNotAcknowledgedUpdates();
            assertEquals(failedServer.size(), 1);
            nError = failedServer.get(FDS2_ID);
            assertNotNull(nError);
            assertEquals(nError.intValue(), 1);
            assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
            assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
            assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

            assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
            assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
            assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 1);
            assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 0);
            assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 1);
            break;
          default:
            fail("Unknown scenario: " + fakeDsScen);
        }
      } else
      {
        checkDSSentAndAcked(fakeRd1, 1);
        checkDSReceivedAndAcked(fakeRd2, 0);
      }

      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());
      if (fakeDsGenId == DEFAULT_GENID)
        assertEquals(fakeRd2.getReceivedUpdates(), 1);
      else
        assertEquals(fakeRd2.getReceivedUpdates(), 0);
      assertTrue(fakeRd2.receivedUpdatesOk());
    } finally
    {
      endTest();
    }
  }

  /**
   * Test that a DS is no more eligible for safe read assured updates when it
   * is degraded (has wrong status)
   * Topology:
   * DS1---RS1---DS2 (DS2 going degraded)
   *
   */
  @Test(groups = "slow", enabled = true)
  public void testSafeReadWrongStatus() throws Exception
  {
    String testCase = "testSafeReadWrongStatus";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /*
       * Start 1 real RS with threshold value 1 to easily put DS2 in DEGRADED status
       */
      try
      {
        // Create real RS
        String dir = testName + RS1_ID + testCase + "Db";
        ReplServerFakeConfiguration conf =
          new ReplServerFakeConfiguration(rs1Port, dir, 0, RS1_ID, 0, 100,
          new TreeSet<String>(), DEFAULT_GID, SMALL_TIMEOUT, 1);
        rs1 = new ReplicationServer(conf);
      } catch (Exception e)
      {
        fail("createReplicationServer " + e.getMessage());
      }

      /*
       * Start 2 fake DSs
       */

      // DS 1 connected to RS 1
      fakeRd1 = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        TIMEOUT_DS_SCENARIO);
      assertNotNull(fakeRd1);

      // DS 2 connected to RS 1 with low window to easily put it in DEGRADED status
      fakeRd2 = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
        REPLY_OK_DS_SCENARIO, new ServerState(), false, 2);
      assertNotNull(fakeRd2);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      waitForStableTopo(fakeRd1, 1, 1);
      List<DSInfo> dsInfos = fakeRd1.getDsList();
      DSInfo dsInfo = dsInfos.get(0);
      assertEquals(dsInfo.getDsId(), FDS2_ID);
      assertEquals(dsInfo.getStatus(), ServerStatus.NORMAL_STATUS);

      /*
       * Put DS2 in degraded status sending 4 safe read assured updates from DS1
       * - 3 for window being full
       * - 1 that is enqueued and makes the threshold value (1) reached and thus
       * DS2 go into degraded status
       */

      for (int i=1 ; i<=4 ; i++)
      {
        long startTime = System.currentTimeMillis();
        try
        {
          fakeRd1.sendNewFakeUpdate();
        } catch (TimeoutException e)
        {
          fail("No timeout is expected here");
        }
        long sendUpdateTime = System.currentTimeMillis() - startTime;
        // RS should timeout as no listener in DS2
        assertTrue((SMALL_TIMEOUT <= sendUpdateTime) && (sendUpdateTime <=
          LONG_TIMEOUT));
      }

      // Check DS2 is degraded
      sleep(7000);
      dsInfos = fakeRd1.getDsList();
      dsInfo = dsInfos.get(0);
      assertEquals(dsInfo.getDsId(), FDS2_ID);
      assertEquals(dsInfo.getStatus(), ServerStatus.DEGRADED_STATUS);

      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      Map<Short, Integer> failedServer = fakeRd1.getAssuredSrServerNotAcknowledgedUpdates();
      assertEquals(failedServer.size(), 1);
      Integer nError = failedServer.get(FDS2_ID);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 4);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertEquals(fakeRd1.getWrongReceivedUpdates(), 0);

      assertEquals(fakeRd2.getReceivedUpdates(), 0);
      assertEquals(fakeRd2.getWrongReceivedUpdates(), 0);
      assertTrue(fakeRd2.receivedUpdatesOk());

      /*
       * Send an assured update from DS 1 : should be acked as DS2 is degraded
       * and RS should not consider it as eligible for assured
       */

      long startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      long sendUpdateTime = System.currentTimeMillis() - startTime;
      // RS should ack quickly as DS2 degraded and not eligible for assured
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 5);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      failedServer = fakeRd1.getAssuredSrServerNotAcknowledgedUpdates();
      assertEquals(failedServer.size(), 1);
      nError = failedServer.get(FDS2_ID);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 4);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertEquals(fakeRd1.getWrongReceivedUpdates(), 0);

      assertEquals(fakeRd2.getReceivedUpdates(), 0);
      assertEquals(fakeRd2.getWrongReceivedUpdates(), 0);
      assertTrue(fakeRd2.receivedUpdatesOk());

      /*
       * Put DS2 in normal status again (start listen service)
       */

      fakeRd2.startListenService();

      // Wait for DS2 being degraded
      boolean error = true;
      for (int count = 0; count < 12; count++)
      {
        dsInfos = fakeRd1.getDsList();
        if (dsInfos == null)
          continue;
        if (dsInfos.size() == 0)
          continue;
        dsInfo = dsInfos.get(0);
        if ( (dsInfo.getDsId() == FDS2_ID) &&
            (dsInfo.getStatus() == ServerStatus.NORMAL_STATUS) )
        {
          error = false;
          break;
        }
        else
        {
          sleep(1000);
        }
      }
      if (error)
        fail("DS2 not back to normal status");

      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      // DS2 should also change status so reset its assured monitoring data so no received sr updates
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 5);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      failedServer = fakeRd1.getAssuredSrServerNotAcknowledgedUpdates();
      assertEquals(failedServer.size(), 1);
      nError = failedServer.get(FDS2_ID);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 4);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 0); // status changed to normal so reset of monitoring data
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertEquals(fakeRd1.getWrongReceivedUpdates(), 0);

      // DS2 should have received the 5 updates (one with not assured)
      assertEquals(fakeRd2.getReceivedUpdates(), 5);
      assertEquals(fakeRd2.getWrongReceivedUpdates(), 1);
      assertFalse(fakeRd2.receivedUpdatesOk());

      /*
       * Send again an assured update, DS2 should be taken into account for ack
       */

      startTime = System.currentTimeMillis();
      try
      {
        fakeRd1.sendNewFakeUpdate();
      } catch (TimeoutException e)
      {
        fail("No timeout is expected here");
      }
      sendUpdateTime = System.currentTimeMillis() - startTime;
      // RS should ack quickly as DS2 degraded and not eligible for assured
      assertTrue(sendUpdateTime < MAX_SEND_UPDATE_TIME);

      sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(fakeRd1.getAssuredSrSentUpdates(), 6);
      assertEquals(fakeRd1.getAssuredSrAcknowledgedUpdates(), 2);
      assertEquals(fakeRd1.getAssuredSrNotAcknowledgedUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrTimeoutUpdates(), 4);
      assertEquals(fakeRd1.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReplayErrorUpdates(), 0);
      failedServer = fakeRd1.getAssuredSrServerNotAcknowledgedUpdates();
      assertEquals(failedServer.size(), 1);
      nError = failedServer.get(FDS2_ID);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 4);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesAcked(), 0);
      assertEquals(fakeRd1.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd2.getAssuredSrSentUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrNotAcknowledgedUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrTimeoutUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrWrongStatusUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrReplayErrorUpdates(), 0);
      assertEquals(fakeRd2.getAssuredSrServerNotAcknowledgedUpdates().size(), 0);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdates(), 1);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesAcked(), 1);
      assertEquals(fakeRd2.getAssuredSrReceivedUpdatesNotAcked(), 0);

      assertEquals(fakeRd1.getReceivedUpdates(), 0);
      assertEquals(fakeRd1.getWrongReceivedUpdates(), 0);

      assertEquals(fakeRd2.getReceivedUpdates(), 6);
      assertEquals(fakeRd2.getWrongReceivedUpdates(), 1);
      assertFalse(fakeRd2.receivedUpdatesOk());
    } finally
    {
      endTest();
    }
  }
}

