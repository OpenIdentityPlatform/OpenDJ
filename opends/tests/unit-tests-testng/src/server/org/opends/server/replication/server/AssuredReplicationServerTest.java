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
package org.opends.server.replication.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
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
  private static final short FDS1_ID = 1;
  private static final short FDS2_ID = 2;
  private static final short FDS3_ID = 3;
  private static final short FRS1_ID = 11;
  private static final short FRS2_ID = 12;
  private static final short FRS3_ID = 13;
  private static final short RS1_ID = 101;
  private static final short RS2_ID = 102;
  private static final short RS3_ID = 103;
  private FakeReplicationDomain fakeRd1 = null;
  private FakeReplicationDomain fakeRd2 = null;
  private FakeReplicationDomain fakeRd3 = null;
  private FakeReplicationServer fakeRs1 = null;
  private FakeReplicationServer fakeRs2 = null;
  private FakeReplicationServer fakeRs3 = null;
  private ReplicationServer rs1 = null;
  private ReplicationServer rs2 = null;
  private ReplicationServer rs3 = null;

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
  // Other group id
  private static final int OTHER_GID = 2;

  // Default generation id
  private static long DEFAULT_GENID = EMPTY_DN_GENID;
  // Other generation id
  private static long OTHER_GENID = 500L;

  /*
   * Definitions for the scenario of the fake DS
   */
  // DS receives updates and replies acks with no errors to every updates
  private static final int REPLY_OK_DS_SCENARIO = 1;
  // DS receives acks but does not respond (makes timeouts)
  private static final int TIMEOUT_DS_SCENARIO = 2;
  // DS receives updates and replies ack with replay error flags
  private static final int REPLAY_ERROR_DS_SCENARIO = 3;

  /*
   * Definitions for the scenario of the fake RS
   */
  // RS receives updates and replies acks with no errors to every updates
  private static final int REPLY_OK_RS_SCENARIO = 11;
  // RS receives acks but does not respond (makes timeouts)
  private static final int TIMEOUT_RS_SCENARIO = 12;
  // RS is used for sending updates (with sendNewFakeUpdate()) and receive acks, synchronously
  private static final int SENDER_RS_SCENARIO = 13;

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
    rs1Port = socket1.getLocalPort();
    rs2Port = socket2.getLocalPort();
    rs3Port = socket3.getLocalPort();
    socket1.close();
    socket2.close();
    socket3.close();
  }

  private void initTest()
  {
    fakeRd1 = null;
    fakeRd2 = null;
    fakeRd3 = null;
    fakeRs1 = null;
    fakeRs2 = null;
    fakeRs3 = null;
    rs1 = null;
    rs2 = null;
    rs3 = null;
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
  }

  /**
   * Creates and connects a new fake replication domain, using the passed scenario.
   */
  private FakeReplicationDomain createFakeReplicationDomain(short serverId,
    int groupId, short rsId, long generationId, boolean assured,
    AssuredMode assuredMode, int safeDataLevel, long assuredTimeout,
    int scenario)
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

      FakeReplicationDomain fakeReplicationDomain = new FakeReplicationDomain(
        TEST_ROOT_DN_STRING, serverId, "localhost:" + rsPort, generationId,
        (byte)groupId, assured, assuredMode, (byte)safeDataLevel, assuredTimeout, scenario);

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
    AssuredMode assuredMode, int safeDataLevel, int scenario)
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
      assertTrue(fakeReplicationServer.connect());

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
        if (testCase.equals("testSafeDataManyRealRSs"))
        {
          // Every 3 RSs connected together
          replServers.add("localhost:" + rs2Port);
          replServers.add("localhost:" + rs3Port);
        } else
        {
          // Let this server alone
        }
      } else if (serverId == RS2_ID)
      {
        port = rs2Port;
        if (testCase.equals("testSafeDataManyRealRSs"))
        {
          // Every 3 RSs connected together
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs3Port);
        } else
        {
          // Let this server alone
        }
      } else if (serverId == RS3_ID)
      {
        port = rs3Port;
        if (testCase.equals("testSafeDataManyRealRSs"))
        {          
          // Every 3 RSs connected together
          replServers.add("localhost:" + rs1Port);
          replServers.add("localhost:" + rs2Port);
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
     * behaviour upon reception of updates)
     * @throws org.opends.server.config.ConfigException
     */
    public FakeReplicationDomain(
      String serviceID,
      short serverID,
      String replicationServer,
      long generationId,
      byte groupId,
      boolean assured,
      AssuredMode assuredMode,
      byte safeDataLevel,
      long assuredTimeout,
      int scenario) throws ConfigException
    {
      super(serviceID, serverID);
      List<String> replicationServers = new ArrayList<String>();
      replicationServers.add(replicationServer);
      this.generationId = generationId;
      setGroupId(groupId);
      setAssured(assured);
      setAssuredMode(assuredMode);
      setAssuredSdLevel(safeDataLevel);
      setAssuredTimeout(assuredTimeout);
      this.scenario = scenario;

      gen = new ChangeNumberGenerator(serverID, 0L);

      startPublishService(replicationServers, 100, 1000);
      startListenService();
    }

    public boolean receivedUpdatesOk()
    {
      return everyUpdatesAreOk;
    }

    public int nReceivedUpdates()
    {
      return nReceivedUpdates;
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
      try
      {
        checkUpdateAssuredParameters(updateMsg);
        nReceivedUpdates++;

        // Now execute the requested scenario
        AckMsg ackMsg = null;
        switch (scenario)
        {
          case REPLY_OK_DS_SCENARIO:
            // Send the ack without errors
            ackMsg = new AckMsg(updateMsg.getChangeNumber());
            session.publish(ackMsg);
            break;
          case TIMEOUT_DS_SCENARIO:
            // Let timeout occur
            break;
          case REPLAY_ERROR_DS_SCENARIO:
            // Send the ack with replay error
            ackMsg = new AckMsg(updateMsg.getChangeNumber());
            ackMsg.setHasReplayError(true);
            List<Short> failedServers = new ArrayList<Short>();
            failedServers.add(getServerId());
            ackMsg.setFailedServers(failedServers);
            session.publish(ackMsg);
            break;
          default:
            fail("Unknown scenario: " + scenario);
        }
        return true;
      } catch (IOException ex)
      {
        fail("IOException in fake replication domain " + getServerId() + " :" +
          ex.getMessage());
        return false;
      }
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
        everyUpdatesAreOk = false;
    }

    /**
     * Sends a new update from this DS
     * @throws TimeoutException If timeout waiting for an assured ack
     */
    public void sendNewFakeUpdate() throws TimeoutException
    {

      // Create a new delete update message (the simplest to create)
      DeleteMsg delMsg = new DeleteMsg(getServiceID(), gen.newChangeNumber(),
        UUID.randomUUID().toString());

      // Send it (this uses the defined assured conf at constructor time)
      prepareWaitForAckIfAssuredEnabled(delMsg);
      publish(delMsg);
      waitForAckIfAssuredEnabled(delMsg);
    }
  }

  /**
   * The fake replication server used to emulate RS behaviour the way we want
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

    // True is an ack has been replied to a received assured update (in assured mode of course)
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
     * Returns true if connection was made successfuly
     */
    public boolean connect()
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
          fakeUrl, baseDn, 100, new ServerState(),
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
     * Check that received update assured parameters are as defined at DS start
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
    
    public int nReceivedUpdates()
    {
      return nReceivedUpdates;
    }

    /**
     * Test if the last received updates was acknowledged
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
          DEFAULT_GENID, false, AssuredMode.SAFE_DATA_MODE, 1, TIMEOUT_RS_SCENARIO);
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

      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
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
      sleep(1000); // Let time to update to reach other servers
      assertEquals(fakeRd1.nReceivedUpdates(), 0);
      assertTrue(fakeRd1.receivedUpdatesOk());
      if (otherFakeDS)
      {
        assertEquals(fakeRd2.nReceivedUpdates(), 1);
        assertTrue(fakeRd2.receivedUpdatesOk());
      }
      if (fakeRS)
      {
        assertEquals(fakeRs1.nReceivedUpdates(), 1);
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
  @Test(dataProvider = "testSafeDataLevelHighPrecommitProvider", enabled = true)
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
        fakeRs1Scen);
      assertNotNull(fakeRs1);

      // Put a fake RS 2 connected to real RS
      fakeRs2 = createFakeReplicationServer(FRS2_ID, fakeRs2Gid, RS1_ID,
        fakeRs2GenId, ((fakeRs2Gid == DEFAULT_GID) ? true : false), AssuredMode.SAFE_DATA_MODE, sdLevel,
        fakeRs2Scen);
      assertNotNull(fakeRs2);

      // Put a fake RS 3 connected to real RS
      fakeRs3 = createFakeReplicationServer(FRS3_ID, fakeRs3Gid, RS1_ID,
        fakeRs3GenId, ((fakeRs3Gid == DEFAULT_GID) ? true : false), AssuredMode.SAFE_DATA_MODE, sdLevel,
        fakeRs3Scen);
      assertNotNull(fakeRs3);
      
      // Wait for connections to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 3);

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
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(1, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceived(1, otherFakeDS, otherFakeDsGenId, fakeRs1GenId, fakeRs2GenId, fakeRs3GenId, expectedServers);

      /***********************************************************************
       * Send update from DS 1 (2 fake RSs available) and check what happened
       ***********************************************************************/

      // Shutdown fake RS 3
      fakeRs3.shutdown();
      fakeRs3 = null;

      // Wait for disconnection to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 2);

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
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(2, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceived(2, otherFakeDS, otherFakeDsGenId, fakeRs1GenId, fakeRs2GenId, -1L, expectedServers);

      /***********************************************************************
       * Send update from DS 1 (1 fake RS available) and check what happened
       ***********************************************************************/

      // Shutdown fake RS 2
      fakeRs2.shutdown();
      fakeRs2 = null;

      // Wait for disconnection to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 1);

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
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(3, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceived(3, otherFakeDS, otherFakeDsGenId, fakeRs1GenId, -1L, -1L, expectedServers);

      /***********************************************************************
       * Send update from DS 1 (no fake RS available) and check what happened
       ***********************************************************************/

      // Shutdown fake RS 1
      fakeRs1.shutdown();
      fakeRs1 = null;

      // Wait for disconnection to be finished
      // DS must see expected numbers of fake DSs and RSs
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 0);

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
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(4, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, elligibleServers, expectedServers);
      checkWhatHasBeenReceived(4, otherFakeDS, otherFakeDsGenId, -1L, -1L, -1L, expectedServers);
    } finally
    {
      endTest();
    }
  }

  // Check that the DSs and the fake RSs of the topology have received/acked what is expected according to the
  // test step (the number of updates)
  // -1 for a gen id means no need to test the matching fake RS
  private void checkWhatHasBeenReceived(int nSentUpdates, boolean otherFakeDS, long otherFakeDsGenId, long fakeRs1GenId, long fakeRs2GenId, long fakeRs3GenId, List<Short> expectedServers)
  {

    // We should not receive our own update
    assertEquals(fakeRd1.nReceivedUpdates(), 0);
    assertTrue(fakeRd1.receivedUpdatesOk());

    // Check what received other fake DS
    if (otherFakeDS)
    {
      if (otherFakeDsGenId == DEFAULT_GENID)
      {
        // Update should have been received
        assertEquals(fakeRd2.nReceivedUpdates(), nSentUpdates);
        assertTrue(fakeRd2.receivedUpdatesOk());
      } else
      {
        assertEquals(fakeRd2.nReceivedUpdates(), 0);
        assertTrue(fakeRd2.receivedUpdatesOk());
      }
    }

    // Check what received/did fake Rss

    if (nSentUpdates < 4)  // Fake RS 3 is stopped after 3 updates sent
    {
      if (fakeRs1GenId != DEFAULT_GENID)
        assertEquals(fakeRs1.nReceivedUpdates(), 0);
      else
        assertEquals(fakeRs1.nReceivedUpdates(), nSentUpdates);
      assertTrue(fakeRs1.receivedUpdatesOk());
      if (expectedServers.contains(FRS1_ID))
        assertTrue(fakeRs1.ackReplied());
      else
        assertFalse(fakeRs1.ackReplied());
    }

    if (nSentUpdates < 3)  // Fake RS 3 is stopped after 2 updates sent
    {
      if (fakeRs2GenId != DEFAULT_GENID)
        assertEquals(fakeRs2.nReceivedUpdates(), 0);
      else
        assertEquals(fakeRs2.nReceivedUpdates(), nSentUpdates);
      assertTrue(fakeRs2.receivedUpdatesOk());
      if (expectedServers.contains(FRS2_ID))
        assertTrue(fakeRs2.ackReplied());
      else
        assertFalse(fakeRs2.ackReplied());
    }

    if (nSentUpdates < 2) // Fake RS 3 is stopped after 1 update sent
    {
      if (fakeRs3GenId != DEFAULT_GENID)
        assertEquals(fakeRs3.nReceivedUpdates(), 0);
      else
        assertEquals(fakeRs3.nReceivedUpdates(), nSentUpdates);
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
      if ( (nDs == expectedDs) && (nRs == (expectedRs+1)) ) // Must include real RS so '+1'
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

  // Compute the list of servers that are elligible for receiving an assured update
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

  // Compute the list of servers that are elligible for receiving an assured update and that are expected to effectively ack the update
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
        TIMEOUT_RS_SCENARIO);
      assertNotNull(fakeRs2);

      /*
       * Start fake RS to send updates
       */

      // Put a fake RS 1 connected to real RS
      fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRsGid, RS1_ID,
        fakeRsGenId, sendInAssured, AssuredMode.SAFE_DATA_MODE, sdLevel,
        SENDER_RS_SCENARIO);
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
      waitForStableTopo(fakeRd1, 0, 2);

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
      assertEquals(fakeRd1.getAssuredSdSentUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), 1);
      assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSdServerTimeoutUpdates().size(), 0);
    } finally
    {
      endTest();
    }
  }
}

