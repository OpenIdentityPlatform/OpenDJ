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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.*;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.testng.Assert.*;

/**
 * Test the client part (plugin) of the assured feature in both safe data and
 * safe read modes. We use a fake RS and control the behaviour of the client
 * DS (timeout, wait for acks, error handling...)
 * Also check for monitoring values for assured replication
 */
public class AssuredReplicationPluginTest extends ReplicationTestCase
{

  /** The port of the replicationServer. */
  private int replServerPort;
  private final int RS_SERVER_ID = 90;
  /** Sleep time of the RS, before sending an ack */
  private static final long NO_TIMEOUT_RS_SLEEP_TIME = 2000;
  private final String testName = getClass().getSimpleName();

  /**
   * Create two distinct base dns, one for safe data replication, the other one
   * for safe read replication
   */
  private final String SAFE_DATA_DN = "ou=safe-data," + TEST_ROOT_DN_STRING;
  private final String SAFE_READ_DN = "ou=safe-read," + TEST_ROOT_DN_STRING;
  private final String NOT_ASSURED_DN = "ou=not-assured," + TEST_ROOT_DN_STRING;
  private Entry safeDataDomainCfgEntry = null;
  private Entry safeReadDomainCfgEntry = null;
  private Entry notAssuredDomainCfgEntry = null;

  /** The fake replication server */
  private FakeReplicationServer replicationServer = null;

  // Definitions for the scenario the RS supports
  private static final int NOT_ASSURED_SCENARIO = 1;
  private static final int TIMEOUT_SCENARIO = 2;
  private static final int NO_TIMEOUT_SCENARIO = 3;
  private static final int SAFE_READ_MANY_ERRORS = 4;
  private static final int SAFE_DATA_MANY_ERRORS = 5;
  private static final int NO_READ = 6;

  /** The tracer object for the debug logger */
  private static final DebugTracer TRACER = getTracer();

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

    // Find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // Create base dns for each tested modes
    String topEntry = "dn: " + SAFE_DATA_DN + "\n" + "objectClass: top\n" +
      "objectClass: organizationalUnit\n";
    addEntry(TestCaseUtils.entryFromLdifString(topEntry));
    topEntry = "dn: " + SAFE_READ_DN + "\n" + "objectClass: top\n" +
      "objectClass: organizationalUnit\n";
    addEntry(TestCaseUtils.entryFromLdifString(topEntry));
    topEntry = "dn: " + NOT_ASSURED_DN + "\n" + "objectClass: top\n" +
      "objectClass: organizationalUnit\n";
    addEntry(TestCaseUtils.entryFromLdifString(topEntry));
  }

  /**
   * Add an entry in the database
   */
  private void addEntry(Entry entry) throws Exception
  {
    debugInfo("AddEntry " + entry.getDN());
    AddOperationBasis addOp = new AddOperationBasis(connection,
      InternalClientConnection.nextOperationID(), InternalClientConnection.
      nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
      entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    waitOpResult(addOp, ResultCode.SUCCESS);
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  /**
   * Creates a domain using the passed assured settings.
   * Returns the matching config entry added to the config backend.
   */
  private Entry createAssuredDomain(AssuredMode assuredMode, int safeDataLevel,
    long assuredTimeout) throws Exception
  {
    String baseDn = null;
    switch (assuredMode)
    {
      case SAFE_READ_MODE:
        baseDn = SAFE_READ_DN;
        break;
      case SAFE_DATA_MODE:
        baseDn = SAFE_DATA_DN;
        break;
      default:
        fail("Unexpected assured level.");
    }
    // Create an assured config entry ldif, matching passed assured settings
    String prefixLdif = "dn: cn=" + testName + ", cn=domains, " +
      SYNCHRO_PLUGIN_DN + "\n" + "objectClass: top\n" +
      "objectClass: ds-cfg-replication-domain\n" + "cn: " + testName + "\n" +
      "ds-cfg-base-dn: " + baseDn + "\n" +
      "ds-cfg-replication-server: localhost:" + replServerPort + "\n" +
      "ds-cfg-server-id: 1\n" + "ds-cfg-receive-status: true\n" +
      // heartbeat = 10 min so no need to emulate heartbeat in fake RS: session
      // not closed by client
      "ds-cfg-heartbeat-interval: 600000ms\n" +
      // heartbeat = 10 min so no need to emulate heartbeat in fake RS: session
      // not closed by client
     "ds-cfg-changetime-heartbeat-interval: 0ms\n";

    String configEntryLdif = null;
    switch (assuredMode)
    {
      case SAFE_READ_MODE:
        configEntryLdif = prefixLdif + "ds-cfg-assured-type: safe-read\n" +
          "ds-cfg-assured-timeout: " + assuredTimeout + "ms\n";
        break;
      case SAFE_DATA_MODE:
        configEntryLdif = prefixLdif + "ds-cfg-assured-type: safe-data\n" +
          "ds-cfg-assured-sd-level: " + safeDataLevel + "\n" +
          "ds-cfg-assured-timeout: " + assuredTimeout + "ms\n";
        break;
      default:
        fail("Unexpected assured level.");
    }
    Entry domainCfgEntry = TestCaseUtils.entryFromLdifString(configEntryLdif);

    // Add the config entry to create the replicated domain
    DirectoryServer.getConfigHandler().addEntry(domainCfgEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(domainCfgEntry.getDN()),
      "Unable to add the domain config entry: " + configEntryLdif);

    return domainCfgEntry;
  }

  /**
   * Creates a domain without assured mode
   * Returns the matching config entry added to the config backend.
   */
  private Entry createNotAssuredDomain() throws Exception
  {

    // Create a not assured config entry ldif
    String configEntryLdif = "dn: cn=" + testName + ", cn=domains, " +
      SYNCHRO_PLUGIN_DN + "\n" + "objectClass: top\n" +
      "objectClass: ds-cfg-replication-domain\n" + "cn: " + testName + "\n" +
      "ds-cfg-base-dn: " + NOT_ASSURED_DN + "\n" +
      "ds-cfg-replication-server: localhost:" + replServerPort + "\n" +
      "ds-cfg-server-id: 1\n" + "ds-cfg-receive-status: true\n" +
      // heartbeat = 10 min so no need to emulate heartbeat in fake RS: session
      // not closed by client
      "ds-cfg-heartbeat-interval: 600000ms\n" +
      "ds-cfg-changetime-heartbeat-interval: 0ms\n";

    Entry domainCfgEntry = TestCaseUtils.entryFromLdifString(configEntryLdif);

    // Add the config entry to create the replicated domain
    DirectoryServer.getConfigHandler().addEntry(domainCfgEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(domainCfgEntry.getDN()),
      "Unable to add the domain config entry: " + configEntryLdif);

    return domainCfgEntry;
  }

  /**
   * The fake replication server used to emulate RS behaviour the way we want
   * for assured features test.
   * This fake replication server is able to receive a DS connection only.
   * According to the configured scenario, it will answer to updates with acks
   * as the scenario is requesting.
   */
  private class FakeReplicationServer extends Thread
  {

    private ServerSocket listenSocket;
    private boolean shutdown = false;
    private Session session = null;

    // Parameters given at constructor time
    private final int port;
    private int serverId = -1;
    boolean isAssured = false; // Default value for config
    AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE; // Default value for config
    byte safeDataLevel = (byte) 1; // Default value for config

    // Predefined config parameters
    private final int degradedStatusThreshold = 5000;

    // Parameters set with received server start message
    private String baseDn = null;
    private long generationId = -1L;
    private ServerState serverState = null;
    private int windowSize = -1;
    private byte groupId = (byte) -1;
    private boolean sslEncryption = false;
    // The scenario this RS is expecting
    private int scenario = -1;

    // parameters at handshake are ok
    private boolean handshakeOk = false;
    // signal that the current scenario the RS must execute reached the point
    // where the main code can perform test assertion
    private boolean scenarioExecuted = false;

    private ChangeNumberGenerator gen = null;
    private String testcase;

    // Constructor for RS receiving updates in SR assured mode or not assured
    // The assured boolean means:
    // - true: SR mode
    // - false: not assured
    public FakeReplicationServer(byte groupId, int port, int serverId, boolean assured,
        String testcase)
    {

      this.groupId = groupId;
      this.port = port;
      this.serverId = serverId;
      this.testcase = testcase;

      if (assured)
      {
        this.isAssured = true;
      }
      this.assuredMode = AssuredMode.SAFE_READ_MODE;
    }

    // Constructor for RS receiving updates in SD assured mode
    public FakeReplicationServer(byte groupId, int port, int serverId, int safeDataLevel,
        String testcase)
    {
      this.groupId = groupId;
      this.port = port;
      this.serverId = serverId;

      this.isAssured = true;
      this.assuredMode = AssuredMode.SAFE_DATA_MODE;
      this.safeDataLevel = (byte) safeDataLevel;
      this.testcase = testcase;
    }

    public void setAssured(boolean assured)
    {
      this.isAssured = assured;
    }

    /**
     * Starts the fake RS, expecting and testing the passed scenario.
     */
    public void start(int scenario)
    {

      gen = new ChangeNumberGenerator(3, 0L);

      // Store expected test case
      this.scenario = scenario;

      // Start listening
      start();
    }

    /**
     * Wait for DS connections
     */
    @Override
    public void run()
    {

      // Create server socket
      try
      {
        listenSocket = new ServerSocket();
        listenSocket.bind(new InetSocketAddress(port));
      } catch (IOException e)
      {
        fail("Fake replication server could not bind to port:" + port);
      }

      Socket newSocket = null;
      // Loop waiting for DS connections
      while (!shutdown)
      {
        try
        {
          newSocket = listenSocket.accept();
          newSocket.setTcpNoDelay(true);
          newSocket.setKeepAlive(true);
          // Create client session
          ReplSessionSecurity replSessionSecurity = new ReplSessionSecurity();
          int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
          session = replSessionSecurity.createServerSession(newSocket,
              timeoutMS);
          if (session == null) // Error, go back to accept
          {
            continue;
          }
          // Ok, now call connection handling code + special code for the
          // configured test
          handleClientConnection();
        } catch (Exception e)
        {
          // The socket has probably been closed as part of the
          // shutdown
        }
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

      // Shutdown the listener thread
      try
      {
        if (listenSocket != null)
        {
          listenSocket.close();
        }
      } catch (IOException e)
      {
        // replication Server service is closing anyway.
      }

      /*
       * Shutdown any current client handling code
       */
      if (session != null)
      {
        session.close();
      }

      try
      {
        join();
      } catch (InterruptedException ie)
      {
      }
    }

    /**
     * Handle the handshake processing with the connecting DS
     * returns true if handshake was performed without errors
     */
    private boolean performHandshake()
    {

      try
      {
        // Receive server start
        ServerStartMsg serverStartMsg = (ServerStartMsg) session.receive();

        baseDn = serverStartMsg.getBaseDn();
        serverState = serverStartMsg.getServerState();
        generationId = serverStartMsg.getGenerationId();
        windowSize = serverStartMsg.getWindowSize();
        sslEncryption = serverStartMsg.getSSLEncryption();

        // Send replication server start
        String serverURL = ("localhost:" + port);
        ReplServerStartMsg replServerStartMsg = new ReplServerStartMsg(serverId,
          serverURL, baseDn, windowSize, serverState,
          generationId, sslEncryption,
          groupId, degradedStatusThreshold);
        session.publish(replServerStartMsg);

        if (!sslEncryption)
        {
          session.stopEncryption();
        }

        // Read start session or stop
        ReplicationMsg msg = session.receive();
        if (msg instanceof StopMsg){
          // Disconnection of DS looking for best server
          return false;
        }

        StartSessionMsg startSessionMsg = (StartSessionMsg)msg;

        // Sanity checking for assured parameters
        boolean receivedIsAssured = startSessionMsg.isAssured();
        assertEquals(receivedIsAssured, isAssured);
        if (isAssured)
        {
          AssuredMode receivedAssuredMode = startSessionMsg.getAssuredMode();
          assertEquals(receivedAssuredMode, assuredMode);
          byte receivedSafeDataLevel = startSessionMsg.getSafeDataLevel();
          assertEquals(receivedSafeDataLevel, safeDataLevel);
        }
        ServerStatus receivedServerStatus = startSessionMsg.getStatus();
        assertEquals(receivedServerStatus, ServerStatus.NORMAL_STATUS);
        List<String> receivedReferralsURLs = startSessionMsg.getReferralsURLs();
        assertEquals(receivedReferralsURLs.size(), 0);

        debugInfo("Received start session assured parameters are ok.");

        // Send topo view
        List<RSInfo> rsList = new ArrayList<RSInfo>();
        RSInfo rsInfo = new RSInfo(serverId, "localhost:" + port, generationId, groupId, 1);
        rsList.add(rsInfo);
        TopologyMsg topologyMsg = new TopologyMsg(new ArrayList<DSInfo>(),
          rsList);
        session.publish(topologyMsg);

      } catch (IOException e)
      {
        fail("Unexpected io exception in fake replication server handshake " +
          "processing: " + e);
        return false;
      } catch (Exception e)
      {
        fail("Unexpected exception in fake replication server handshake " +
          "processing: " + e);
        return false;
      }
      return true;
    }

    /**
     * Tells if the received handshake parameters regarding assured config were
     * ok and handshake phase is terminated.
     */
    public boolean isHandshakeOk()
    {

      return handshakeOk;
    }

    /**
     * Tells the main code that the fake RS executed enough of the expected
     * scenario and can perform test assertion
     */
    public boolean isScenarioExecuted()
    {

      return scenarioExecuted;
    }

    /**
     * Handle client connection then call code specific to configured test
     */
    private void handleClientConnection()
    {
      debugInfo("handleClientConnection " + testcase + " " + scenario);
      // Handle DS connexion
      if (!performHandshake())
      {
        session.close();
        return;
      }
      // If we come here, assured parameters sent by DS are as expected and
      // handshake phase is terminated
      handshakeOk = true;

      // Now execute the requested scenario
      switch (scenario)
      {
        case NOT_ASSURED_SCENARIO:
          executeNotAssuredScenario();
          break;
        case TIMEOUT_SCENARIO:
          executeTimeoutScenario();
          break;
        case NO_TIMEOUT_SCENARIO:
          executeNoTimeoutScenario();
          break;
        case SAFE_READ_MANY_ERRORS:
          executeSafeReadManyErrorsScenario();
          break;
        case SAFE_DATA_MANY_ERRORS:
          executeSafeDataManyErrorsScenario();
          break;
        case NO_READ:
          // Nothing to execute, just let session opne. This scenario used to
          // send updates from the RS to the DS (reply test cases)
          while (!shutdown)
          {
            try
            {
              sleep(5000);
            } catch (InterruptedException ex)
            {
              // Going shutdown ?
              break;
            }
          }
          break;
        default:
          fail("Unknown scenario: " + scenario);
      }
      debugInfo("handleClientConnection " + testcase + " " + scenario + " done");
    }

    /*
     * Make the RS send an add message with the passed entry and return the ack
     * message it receives from the DS
     */
    private AckMsg sendAssuredAddMsg(Entry entry, String parentUid) throws SocketTimeoutException
    {
      try
      {
        // Create add message
        AddMsg addMsg =
          new AddMsg(gen.newChangeNumber(), entry.getDN().toString(), UUID.randomUUID().toString(),
                     parentUid,
                     entry.getObjectClassAttribute(),
                     entry.getAttributes(), null );

        // Send add message in assured mode
        addMsg.setAssured(isAssured);
        addMsg.setAssuredMode(assuredMode);
        addMsg.setSafeDataLevel(safeDataLevel);
        session.publish(addMsg);

        // Read and return matching ack
        return (AckMsg)session.receive();

      } catch(SocketTimeoutException e)
      {
        throw e;
      } catch (Throwable t)
      {
        fail("Unexpected exception in fake replication server sendAddUpdate " +
          "processing: " + t);
        return null;
      }
    }

    /**
     * Read the coming update and check parameters are not assured
     */
    private void executeNotAssuredScenario()
    {

      try
      {
        UpdateMsg updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        scenarioExecuted = true;
      } catch (Exception e)
      {
        fail("Unexpected exception in fake replication server executeNotAssuredScenario " +
          "processing: " + e);
      }
    }

    /**
     * Read the coming update and make the client time out by not sending back
     * the ack
     */
    private void executeTimeoutScenario()
    {

      try
      {
        UpdateMsg updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        scenarioExecuted = true;

        // We do not send back an ack and the client code is expected to be
        // blocked at least for the programmed timeout time.

      } catch (Exception e)
      {
        fail("Unexpected exception in fake replication server executeTimeoutScenario " +
          "processing: " + e + " testcase= " + testcase +
          " groupId=" + groupId);
      }
    }

    /**
     * Read the coming update, sleep some time then send back an ack
     */
    private void executeNoTimeoutScenario()
    {

      try
      {
        UpdateMsg updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // Sleep before sending back the ack
        sleep(NO_TIMEOUT_RS_SLEEP_TIME);

        // Send the ack without errors
        AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber());
        session.publish(ackMsg);

        scenarioExecuted = true;

      } catch (Exception e)
      {
        fail("Unexpected exception in fake replication server executeNoTimeoutScenario " +
          "processing: " + e);
      }
    }

    /**
     * Check that received update assured parameters are as defined at RS start
     */
    private void checkUpdateAssuredParameters(UpdateMsg updateMsg)
    {
      assertEquals(updateMsg.isAssured(), isAssured,
          "msg=" + ((updateMsg instanceof AddMsg)?
              ((AddMsg)updateMsg).getDn():updateMsg.getChangeNumber()));
      if (isAssured)
      {
        assertEquals(updateMsg.getAssuredMode(), assuredMode);
        assertEquals(updateMsg.getSafeDataLevel(), safeDataLevel);
      }
      debugInfo("Received update assured parameters are ok.");
    }

    /**
     * Read the coming safe read mode updates and send back acks with errors
     */
    private void executeSafeReadManyErrorsScenario()
    {

      try
      {
        // Read first update
        UpdateMsg updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // Sleep before sending back the ack
        sleep(NO_TIMEOUT_RS_SLEEP_TIME);

        // Send an ack with errors:
        // - replay error
        // - server 10 error, server 20 error
        List<Integer> serversInError = new ArrayList<Integer>();
        serversInError.add(10);
        serversInError.add(20);
        AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber(), false, false, true, serversInError);
        session.publish(ackMsg);

        // Read second update
        updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // Sleep before sending back the ack
        sleep(NO_TIMEOUT_RS_SLEEP_TIME);

        // Send an ack with errors:
        // - timeout error
        // - wrong status error
        // - replay error
        // - server 10 error, server 20 error, server 30 error
        serversInError = new ArrayList<Integer>();
        serversInError.add(10);
        serversInError.add(20);
        serversInError.add(30);
        ackMsg = new AckMsg(updateMsg.getChangeNumber(), true, true, true, serversInError);
        session.publish(ackMsg);

        // Read third update
        updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // let timeout occur

        scenarioExecuted = true;

      } catch (Exception e)
      {
        fail("Unexpected exception in fake replication server executeSafeReadManyErrorsScenario " +
          "processing: " + e);
      }
    }

    /**
     * Read the coming seaf data mode updates and send back acks with errors
     */
    private void executeSafeDataManyErrorsScenario()
    {

      try
      {
        // Read first update
        UpdateMsg updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // Sleep before sending back the ack
        sleep(NO_TIMEOUT_RS_SLEEP_TIME);

        // Send an ack with errors:
        // - timeout error
        // - server 10 error
        List<Integer> serversInError = new ArrayList<Integer>();
        serversInError.add(10);
        AckMsg ackMsg = new AckMsg(updateMsg.getChangeNumber(), true, false, false, serversInError);
        session.publish(ackMsg);

        // Read second update
        updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // Sleep before sending back the ack
        sleep(NO_TIMEOUT_RS_SLEEP_TIME);

        // Send an ack with errors:
        // - timeout error
        // - server 10 error, server 20 error
        serversInError = new ArrayList<Integer>();
        serversInError.add(10);
        serversInError.add(20);
        ackMsg = new AckMsg(updateMsg.getChangeNumber(), true, false, false, serversInError);
        session.publish(ackMsg);

        // Read third update
        updateMsg = (UpdateMsg) session.receive();
        checkUpdateAssuredParameters(updateMsg);

        // let timeout occur

        scenarioExecuted = true;

      } catch (Exception e)
      {
        fail("Unexpected exception in fake replication server executeSafeDataManyErrorsScenario " +
          "processing: " + e);
      }
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
   * Return various group id values
   */
  @DataProvider(name = "rsGroupIdProvider")
  private Object[][] rsGroupIdProvider()
  {
    return new Object[][]
    {
    { (byte)1 },
    { (byte)2 }
    };
  }

  /**
   * Tests that a DS performing a modification in safe data mode waits for
   * the ack of the RS for the configured timeout time, then times out.
   * If the RS group id is not the same as the DS one, this must not time out
   * and return immediately.
   */
  @Test(dataProvider = "rsGroupIdProvider")
  public void testSafeDataModeTimeout(byte rsGroupId) throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeDataModeTimeout" + rsGroupId;
    try
    {
      // Create and start a RS expecting clients in safe data assured mode with
      // safe data level 2
      replicationServer = new FakeReplicationServer(rsGroupId, replServerPort, RS_SERVER_ID,
        1, testcase);
      if (rsGroupId != (byte)1)
        replicationServer.setAssured(false);
      replicationServer.start(TIMEOUT_SCENARIO);

      long startTime;
      // Create a safe data assured domain
      if (rsGroupId == (byte)1)
      {
        safeDataDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_DATA_MODE, 1,
        TIMEOUT);
        // Wait for connection of domain to RS
        waitForConnectionToRs(testcase, replicationServer);

        // Make an LDAP update (add an entry)
        startTime = System.currentTimeMillis(); // Time the update has been initiated
        String entry = "dn: ou=assured-sd-timeout-entry" + rsGroupId + "," + SAFE_DATA_DN + "\n" +
          "objectClass: top\n" +
          "objectClass: organizationalUnit\n";
        addEntry(TestCaseUtils.entryFromLdifString(entry));
      }
      else
      {
        safeDataDomainCfgEntry = createNotAssuredDomain();
        // Wait for connection of domain to RS
        waitForConnectionToRs(testcase, replicationServer);

        // Make an LDAP update (add an entry)
        startTime = System.currentTimeMillis(); // Time the update has been initiated
        String entry = "dn: ou=assured-sd-timeout-entry" + rsGroupId + "," + NOT_ASSURED_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
        addEntry(TestCaseUtils.entryFromLdifString(entry));
      }
      long endTime = System.currentTimeMillis();

      waitForScenarioExecutedOnRs(testcase, replicationServer);

      if (rsGroupId == (byte)1)
      {
        // RS has same group id as DS
        // In this scenario, the fake RS will not send back an ack so we expect
        // the add entry code (LDAP client code emulation) to be blocked for the
        // timeout value at least. If the time we have slept is lower, timeout
        // handling code is not working...
        assertTrue((endTime - startTime) >= TIMEOUT);
        assertTrue(replicationServer.isScenarioExecuted());

        // Check monitoring values
        sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
        DN baseDn = DN.decode(SAFE_DATA_DN);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
        Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
        assertTrue(errorsByServer.isEmpty());
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 1);
        errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
        //  errors by server list for sd mode should be [[rsId:1]]
        assertEquals(errorsByServer.size(), 1);
        Integer nError = errorsByServer.get(RS_SERVER_ID);
        assertNotNull(nError);
        assertEquals(nError.intValue(), 1);
      } else
      {
        // RS has a different group id, addEntry should have returned quickly
        assertTrue((endTime - startTime) < 3000);

        // No error should be seen in monitoring and update should have not been
        // sent in assured mode
        sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
        DN baseDn = DN.decode(NOT_ASSURED_DN);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
        Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
        assertTrue(errorsByServer.isEmpty());
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
        errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
        assertTrue(errorsByServer.isEmpty());
      }
    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Tests that a DS performing a modification in safe read mode waits for
   * the ack of the RS for the configured timeout time, then times out.
   * If the RS group id is not the same as the DS one, this must not time out
   * and return immediately.
   */
  @Test(dataProvider = "rsGroupIdProvider")
  public void testSafeReadModeTimeout(byte rsGroupId) throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeReadModeTimeout" + rsGroupId;
    try
    {
      // Create and start a RS expecting clients in safe read assured mode
      replicationServer = new FakeReplicationServer(rsGroupId, replServerPort, RS_SERVER_ID,
        true, testcase);
      if (rsGroupId != (byte)1)
        replicationServer.setAssured(false);
      replicationServer.start(TIMEOUT_SCENARIO);

      long startTime;

      // Create a safe data assured domain
      if (rsGroupId == (byte)1)
      {
        // Create a safe read assured domain
        safeReadDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_READ_MODE, 0,
            TIMEOUT);
        // Wait for connection of domain to RS
        waitForConnectionToRs(testcase, replicationServer);

        // Make an LDAP update (add an entry)
        startTime = System.currentTimeMillis(); // Time the update has been initiated
        String entry = "dn: ou=assured-sr-timeout-entry" + rsGroupId + "," + SAFE_READ_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
        addEntry(TestCaseUtils.entryFromLdifString(entry));
      }
      else
      {
        safeReadDomainCfgEntry = createNotAssuredDomain();
        // Wait for connection of domain to RS
        waitForConnectionToRs(testcase, replicationServer);

        // Make an LDAP update (add an entry)
        startTime = System.currentTimeMillis(); // Time the update has been initiated
        String entry = "dn: ou=assured-sr-timeout-entry" + rsGroupId + "," + NOT_ASSURED_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
        addEntry(TestCaseUtils.entryFromLdifString(entry));
      }
      long endTime = System.currentTimeMillis();

      waitForScenarioExecutedOnRs(testcase, replicationServer);

      if (rsGroupId == (byte)1)
      {
        // RS has same group id as DS
        // In this scenario, the fake RS will not send back an ack so we expect
        // the add entry code (LDAP client code emulation) to be blocked for the
        // timeout value at least. If the time we have slept is lower, timeout
        // handling code is not working...
        assertTrue((endTime - startTime) >= TIMEOUT);
        assertTrue(replicationServer.isScenarioExecuted());

        // Check monitoring values
        sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
        DN baseDn = DN.decode(SAFE_READ_DN);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
        Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
        //  errors by server list for sr mode should be [[rsId:1]]
        assertEquals(errorsByServer.size(), 1);
        Integer nError = errorsByServer.get(RS_SERVER_ID);
        assertNotNull(nError);
        assertEquals(nError.intValue(), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
        errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
        assertTrue(errorsByServer.isEmpty());
      } else
      {
        // RS has a different group id, addEntry should have returned quickly
        assertTrue((endTime - startTime) < 3000);

        // No error should be seen in monitoring and update should have not been
        // sent in assured mode
        sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
        DN baseDn = DN.decode(NOT_ASSURED_DN);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
        Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
        assertTrue(errorsByServer.isEmpty());
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
        errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
        assertTrue(errorsByServer.isEmpty());
      }
    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Tests parameters sent in session handshake and updates, when not using
   * assured replication
   */
  @Test
  public void testNotAssuredSession() throws Exception
  {

    String testcase = "testNotAssuredSession";
    try
    {
      // Create and start a RS expecting not assured clients
      replicationServer = new FakeReplicationServer((byte)1, replServerPort, RS_SERVER_ID,
        false, testcase);
      replicationServer.start(NOT_ASSURED_SCENARIO);

      // Create a not assured domain
      notAssuredDomainCfgEntry = createNotAssuredDomain();
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      // Make an LDAP update (add an entry)
      String entry = "dn: ou=not-assured-entry," + NOT_ASSURED_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // Wait for entry received by RS
      waitForScenarioExecutedOnRs(testcase, replicationServer);

      // No more test to do here
    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Wait for connection to the fake replication server or times out with error
   * after some seconds
   */
  private void waitForConnectionToRs(String testCase, FakeReplicationServer rs)
  {
    int nsec = -1;
    do
    {
      nsec++;
      if (nsec == 10) // 10 seconds timeout
        fail(testCase + ": timeout waiting for domain connection to fake RS after " + nsec + " seconds.");
      sleep(1000);
    } while (!rs.isHandshakeOk());
  }

  /**
   * Wait for the scenario to be executed by the fake replication server or
   * times out with error after some seconds
   */
  private void waitForScenarioExecutedOnRs(String testCase, FakeReplicationServer rs)
  {
    int nsec = -1;
    do
    {
      nsec++;
      if (nsec == 10) // 10 seconds timeout
        fail(testCase + ": timeout waiting for scenario to be exectued on fake RS after " + nsec + " seconds.");
      sleep(1000);
    } while (!rs.isScenarioExecuted());
  }

  private void endTest(String testcase)
  {
    debugInfo("Ending test " + testcase);
    if (replicationServer != null)
    {
      replicationServer.shutdown();
    }

    if (safeDataDomainCfgEntry != null)
    {
      removeDomain(safeDataDomainCfgEntry);
    }

    if (safeReadDomainCfgEntry != null)
    {
      removeDomain(safeReadDomainCfgEntry);
    }

    if (notAssuredDomainCfgEntry != null)
    {
      removeDomain(notAssuredDomainCfgEntry);
    }
  }

  /**
   * Tests that a DS performing a modification in safe data mode receives the RS
   * ack and does not return before returning it.
   */
  @Test
  public void testSafeDataModeAck() throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeDataModeAck";
    try
    {
      // Create and start a RS expecting clients in safe data assured mode with
      // safe data level 2
      replicationServer = new FakeReplicationServer((byte)1, replServerPort, RS_SERVER_ID,
        2, testcase);
      replicationServer.start(NO_TIMEOUT_SCENARIO);

      // Create a safe data assured domain
      safeDataDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_DATA_MODE, 2,
        TIMEOUT);
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      // Make an LDAP update (add an entry)
      long startTime = System.currentTimeMillis(); // Time the update has been initiated
      String entry = "dn: ou=assured-sd-no-timeout-entry," + SAFE_DATA_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // In this scenario, the fake RS will send back an ack after NO_TIMEOUT_RS_SLEEP_TIME
      // seconds, so we expect the add entry code (LDAP client code emulation) to be blocked
      // for more than NO_TIMEOUT_RS_SLEEP_TIME seconds but no more than the timeout value.
      long endTime = System.currentTimeMillis();
      long callTime = endTime - startTime;
      assertTrue( (callTime >= NO_TIMEOUT_RS_SLEEP_TIME) && (callTime <= TIMEOUT));
      assertTrue(replicationServer.isScenarioExecuted());

      // Check monitoring values
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      DN baseDn = DN.decode(SAFE_DATA_DN);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
      Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      assertTrue(errorsByServer.isEmpty());
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      assertTrue(errorsByServer.isEmpty());

    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Tests that a DS performing a modification in safe read mode receives the RS
   * ack and does not return before returning it.
   */
  @Test
  public void testSafeReadModeAck() throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeReadModeAck";
    try
    {
      // Create and start a RS expecting clients in safe read assured mode
      replicationServer = new FakeReplicationServer((byte)1, replServerPort, RS_SERVER_ID,
        true, testcase);
      replicationServer.start(NO_TIMEOUT_SCENARIO);

      // Create a safe read assured domain
      safeReadDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_READ_MODE, 0,
        TIMEOUT);
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      // Make an LDAP update (add an entry)
      long startTime = System.currentTimeMillis(); // Time the update has been initiated
      String entry = "dn: ou=assured-sr-no-timeout-entry," + SAFE_READ_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // In this scenario, the fake RS will send back an ack after NO_TIMEOUT_RS_SLEEP_TIME
      // seconds, so we expect the add entry code (LDAP client code emulation) to be blocked
      // for more than NO_TIMEOUT_RS_SLEEP_TIME seconds but no more than the timeout value.
      long endTime = System.currentTimeMillis();
      long callTime = endTime - startTime;
      assertTrue( (callTime >= NO_TIMEOUT_RS_SLEEP_TIME) && (callTime <= TIMEOUT));
      assertTrue(replicationServer.isScenarioExecuted());

      // Check monitoring values
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      DN baseDn = DN.decode(SAFE_READ_DN);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
      Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      assertTrue(errorsByServer.isEmpty());
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      assertTrue(errorsByServer.isEmpty());

    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Tests that a DS receiving an update from a RS in safe read mode effectively
   * sends an ack back (with or without error)
   */
  @Test(dataProvider = "rsGroupIdProvider", groups = "slow")
  public void testSafeReadModeReply(byte rsGroupId) throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeReadModeReply";
    try
    {
      // Create and start a RS expecting clients in safe read assured mode
      replicationServer = new FakeReplicationServer(rsGroupId, replServerPort, RS_SERVER_ID,
        true, testcase);
      replicationServer.start(NO_READ);

      // Create a safe read assured domain
      safeReadDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_READ_MODE, 0,
        TIMEOUT);
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      /*
       *  Send an update from the RS and get the ack
       */

      // Make the RS send an assured add message
      String entryStr = "dn: ou=assured-sr-reply-entry," + SAFE_READ_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryStr);
      String parentUid = getEntryUUID(DN.decode(SAFE_READ_DN));

      try {
        AckMsg ackMsg = replicationServer.sendAssuredAddMsg(entry, parentUid);

         if (rsGroupId == (byte)2)
           fail("Should only go here for RS with same group id as DS");

        // Ack received, replay has occurred
        assertNotNull(DirectoryServer.getEntry(entry.getDN()));

        // Check that DS replied an ack without errors anyway
        assertFalse(ackMsg.hasTimeout());
        assertFalse(ackMsg.hasReplayError());
        assertFalse(ackMsg.hasWrongStatus());
        assertEquals(ackMsg.getFailedServers().size(), 0);

        // Check for monitoring data
        DN baseDn = DN.decode(SAFE_READ_DN);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
        Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
        assertTrue(errorsByServer.isEmpty());
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 1);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
        assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
        errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
        assertTrue(errorsByServer.isEmpty());
      } catch (SocketTimeoutException e)
      {
        // Expected
        if (rsGroupId == (byte)1)
           fail("Should only go here for RS with group id different from DS one");

        return;
      }

      /*
       * Send un update with error from the RS and get the ack with error
       */

      // Make the RS send a not possible assured add message

      // TODO: make the domain return an error: use a plugin ?
      // The resolution code does not generate any error so we need to find a
      // way to have the replay not working to test this...

      // Check that DS replied an ack with errors
//      assertFalse(ackMsg.hasTimeout());
//      assertTrue(ackMsg.hasReplayError());
//      assertFalse(ackMsg.hasWrongStatus());
//      List<Integer> failedServers = ackMsg.getFailedServers();
//      assertEquals(failedServers.size(), 1);
//      assertEquals((integer)failedServers.get(0), (integer)1);
    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Tests that a DS receiving an update from a RS in safe data mode does not
   * send back and ack (only safe read is taken into account in DS replay)
   */
  @Test(dataProvider = "rsGroupIdProvider", groups = "slow")
  public void testSafeDataModeReply(byte rsGroupId) throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeDataModeReply";
    try
    {
      // Create and start a RS expecting clients in safe data assured mode
      replicationServer = new FakeReplicationServer(rsGroupId, replServerPort, RS_SERVER_ID,
        4, testcase);
      replicationServer.start(NO_READ);

      // Create a safe data assured domain
      safeDataDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_DATA_MODE, 4,
        TIMEOUT);
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      // Make the RS send an assured add message: we expect a read timeout as
      // safe data should be ignored by DS
      String entryStr = "dn: ou=assured-sd-reply-entry," + SAFE_DATA_DN + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      Entry entry = TestCaseUtils.entryFromLdifString(entryStr);
      String parentUid = getEntryUUID(DN.decode(SAFE_DATA_DN));

      AckMsg ackMsg;
      try
      {
        ackMsg = replicationServer.sendAssuredAddMsg(entry, parentUid);
      } catch (SocketTimeoutException e)
      {
        // Expected
        return;
      }

      fail("DS should not reply an ack in safe data mode, however, it replied: " +
        ackMsg);
    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * DS performs many successive modifications in safe data mode and receives RS
   * acks with various errors. Check for monitoring right errors
   */
  @Test(groups = "slow")
  public void testSafeDataManyErrors() throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeDataManyErrors";
    try
    {
      // Create and start a RS expecting clients in safe data assured mode with
      // safe data level 3
      replicationServer = new FakeReplicationServer((byte)1, replServerPort, RS_SERVER_ID,
        3, testcase);
      replicationServer.start(SAFE_DATA_MANY_ERRORS);

      // Create a safe data assured domain
      safeDataDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_DATA_MODE, 3,
        TIMEOUT);
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      // Make a first LDAP update (add an entry)
      long startTime = System.currentTimeMillis(); // Time the update has been initiated
      String entryDn = "ou=assured-sd-many-errors-entry," + SAFE_DATA_DN;
      String entry = "dn: " + entryDn + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // In this scenario, the fake RS will send back an ack after NO_TIMEOUT_RS_SLEEP_TIME
      // seconds, so we expect the add entry code (LDAP client code emulation) to be blocked
      // for more than NO_TIMEOUT_RS_SLEEP_TIME seconds but no more than the timeout value.
      long endTime = System.currentTimeMillis();
      long callTime = endTime - startTime;
      assertTrue( (callTime >= NO_TIMEOUT_RS_SLEEP_TIME) && (callTime <= TIMEOUT));

      // Check monitoring values
      // The expected ack for the first update is:
      // - timeout error
      // - server 10 error
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      DN baseDn = DN.decode(SAFE_DATA_DN);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
      Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      assertTrue(errorsByServer.isEmpty());
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 1);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      //  errors by server list for sd mode should be [[10:1]]
      assertEquals(errorsByServer.size(), 1);
      Integer nError = errorsByServer.get(10);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);

      // Make a second LDAP update (delete the entry)
      startTime = System.currentTimeMillis(); // Time the update has been initiated
      deleteEntry(entryDn);

      // In this scenario, the fake RS will send back an ack after NO_TIMEOUT_RS_SLEEP_TIME
      // seconds, so we expect the delete entry code (LDAP client code emulation) to be blocked
      // for more than NO_TIMEOUT_RS_SLEEP_TIME seconds but no more than the timeout value.
      endTime = System.currentTimeMillis();
      callTime = endTime - startTime;
      assertTrue( (callTime >= NO_TIMEOUT_RS_SLEEP_TIME) && (callTime <= TIMEOUT));

      // Check monitoring values
      // The expected ack for the second update is:
      // - timeout error
      // - server 10 error, server 20 error
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      baseDn = DN.decode(SAFE_DATA_DN);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      assertTrue(errorsByServer.isEmpty());
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 2);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 2);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      //  errors by server list for sd mode should be [[10:2],[20:1]]
      assertEquals(errorsByServer.size(), 2);
      nError = errorsByServer.get(10);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 2);
      nError = errorsByServer.get(20);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);

      // Make a third LDAP update (re-add the entry)
      startTime = System.currentTimeMillis(); // Time the update has been initiated
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // In this scenario, the fake RS will not send back an ack so we expect
      // the add entry code (LDAP client code emulation) to be blocked for the
      // timeout value at least. If the time we have slept is lower, timeout
      // handling code is not working...
      endTime = System.currentTimeMillis();
      assertTrue((endTime - startTime) >= TIMEOUT);
      assertTrue(replicationServer.isScenarioExecuted());

      // Check monitoring values
      // No ack should have comen back, so timeout incremented (flag and error for rs)
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      baseDn = DN.decode(SAFE_DATA_DN);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      assertTrue(errorsByServer.isEmpty());
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 3);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 3);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      //  errors by server list for sd mode should be [[10:2],[20:1],[rsId:1]]
      assertEquals(errorsByServer.size(), 3);
      nError = errorsByServer.get(10);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 2);
      nError = errorsByServer.get(20);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);
      nError = errorsByServer.get(RS_SERVER_ID);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);

    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * DS performs many successive modifications in safe read mode and receives RS
   * acks with various errors. Check for monitoring right errors
   */
  @Test(groups = "slow")
  public void testSafeReadManyErrors() throws Exception
  {

    int TIMEOUT = 5000;
    String testcase = "testSafeReadManyErrors";
    try
    {
      // Create and start a RS expecting clients in safe read assured mode
      replicationServer = new FakeReplicationServer((byte)1, replServerPort, RS_SERVER_ID,
        true, testcase);
      replicationServer.start(SAFE_READ_MANY_ERRORS);

      // Create a safe read assured domain
      safeReadDomainCfgEntry = createAssuredDomain(AssuredMode.SAFE_READ_MODE, 0,
        TIMEOUT);
      // Wait for connection of domain to RS
      waitForConnectionToRs(testcase, replicationServer);

      // Make a first LDAP update (add an entry)
      long startTime = System.currentTimeMillis(); // Time the update has been initiated
      String entryDn = "ou=assured-sr-many-errors-entry," + SAFE_READ_DN;
      String entry = "dn: " + entryDn + "\n" +
        "objectClass: top\n" +
        "objectClass: organizationalUnit\n";
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // In this scenario, the fake RS will send back an ack after NO_TIMEOUT_RS_SLEEP_TIME
      // seconds, so we expect the add entry code (LDAP client code emulation) to be blocked
      // for more than NO_TIMEOUT_RS_SLEEP_TIME seconds but no more than the timeout value.
      long endTime = System.currentTimeMillis();
      long callTime = endTime - startTime;
      assertTrue( (callTime >= NO_TIMEOUT_RS_SLEEP_TIME) && (callTime <= TIMEOUT));

      // Check monitoring values
      // The expected ack for the first update is:
      // - replay error
      // - server 10 error, server 20 error
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      DN baseDn = DN.decode(SAFE_READ_DN);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 1);
      Map<Integer, Integer> errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      //  errors by server list for sr mode should be [[10:1],[20:1]]
      assertEquals(errorsByServer.size(), 2);
      Integer nError = errorsByServer.get(10);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);
      nError = errorsByServer.get(20);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      assertTrue(errorsByServer.isEmpty());

      // Make a second LDAP update (delete the entry)
      startTime = System.currentTimeMillis(); // Time the update has been initiated
      deleteEntry(entryDn);

      // In this scenario, the fake RS will send back an ack after NO_TIMEOUT_RS_SLEEP_TIME
      // seconds, so we expect the delete entry code (LDAP client code emulation) to be blocked
      // for more than NO_TIMEOUT_RS_SLEEP_TIME seconds but no more than the timeout value.
      endTime = System.currentTimeMillis();
      callTime = endTime - startTime;
      assertTrue( (callTime >= NO_TIMEOUT_RS_SLEEP_TIME) && (callTime <= TIMEOUT));

      // Check monitoring values
      // The expected ack for the second update is:
      // - timeout error
      // - wrong status error
      // - replay error
      // - server 10 error, server 20 error, server 30 error
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 2);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 2);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 2);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      //  errors by server list for sr mode should be [[10:2],[20:2],[30:1]]
      assertEquals(errorsByServer.size(), 3);
      nError = errorsByServer.get(10);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 2);
      nError = errorsByServer.get(20);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 2);
      nError = errorsByServer.get(30);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      assertTrue(errorsByServer.isEmpty());

      // Make a third LDAP update (re-add the entry)
      startTime = System.currentTimeMillis(); // Time the update has been initiated
      addEntry(TestCaseUtils.entryFromLdifString(entry));

      // In this scenario, the fake RS will not send back an ack so we expect
      // the add entry code (LDAP client code emulation) to be blocked for the
      // timeout value at least. If the time we have slept is lower, timeout
      // handling code is not working...
      endTime = System.currentTimeMillis();
      assertTrue((endTime - startTime) >= TIMEOUT);
      assertTrue(replicationServer.isScenarioExecuted());

      // Check monitoring values
      // No ack should have comen back, so timeout incremented (flag and error for rs)
      sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-sent-updates"), 3);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-not-acknowledged-updates"), 3);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-timeout-updates"), 2);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-wrong-status-updates"), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-replay-error-updates"), 2);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_READ_MODE);
      //  errors by server list for sr mode should be [[10:2],[20:2],[30:1],[rsId:1]]
      assertEquals(errorsByServer.size(), 4);
      nError = errorsByServer.get(10);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 2);
      nError = errorsByServer.get(20);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 2);
      nError = errorsByServer.get(30);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);
      nError = errorsByServer.get(RS_SERVER_ID);
      assertNotNull(nError);
      assertEquals(nError.intValue(), 1);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sr-received-updates-not-acked"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-sent-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-acknowledged-updates"), 0);
      assertEquals(getMonitorAttrValue(baseDn, "assured-sd-timeout-updates"), 0);
      errorsByServer = getErrorsByServers(baseDn, AssuredMode.SAFE_DATA_MODE);
      assertTrue(errorsByServer.isEmpty());

    } finally
    {
      endTest(testcase);
    }
  }

  /**
   * Delete an entry from the database
   */
  private void deleteEntry(String dn) throws Exception
  {
    DN realDn = DN.decode(dn);
    DeleteOperationBasis delOp = new DeleteOperationBasis(connection,
      InternalClientConnection.nextOperationID(), InternalClientConnection.
      nextMessageID(), null, realDn);
    delOp.setInternalOperation(true);
    delOp.run();
    waitOpResult(delOp, ResultCode.SUCCESS);
    assertNull(DirectoryServer.getEntry(realDn));
  }

  /**
   * Get the errors by servers from cn=monitor, according to the requested base dn
   * and the requested mode
   * This corresponds to the values for multi valued attributes:
   * - assured-sr-server-not-acknowledged-updates in SR mode
   * - assured-sd-server-timeout-updates in SD mode
   */
  protected Map<Integer,Integer> getErrorsByServers(DN baseDn,
    AssuredMode assuredMode) throws Exception
  {
    /*
     * Find monitoring entry for requested base DN
     */
    String monitorFilter =
         "(&(cn=Directory server*)(domain-name=" + baseDn + "))";

    InternalSearchOperation op;
    int count = 0;
    do
    {
      if (count++>0)
        Thread.sleep(100);
      op = connection.processSearch(
                                    ByteString.valueOf("cn=replication,cn=monitor"),
                                    SearchScope.WHOLE_SUBTREE,
                                    LDAPFilter.decode(monitorFilter));
    }
    while (op.getSearchEntries().isEmpty() && (count<100));
    if (op.getSearchEntries().isEmpty())
      throw new Exception("Could not read monitoring information");

    SearchResultEntry entry = op.getSearchEntries().getFirst();

    if (entry == null)
      throw new Exception("Could not find monitoring entry");

    /*
     * Find the multi valued attribute matching the requested assured mode
     */
    String assuredAttr;
    switch(assuredMode)
    {
      case SAFE_READ_MODE:
        assuredAttr = "assured-sr-server-not-acknowledged-updates";
        break;
      case SAFE_DATA_MODE:
        assuredAttr = "assured-sd-server-timeout-updates";
        break;
      default:
        throw new Exception("Unknown assured type");
    }

    List<Attribute> attrs = entry.getAttribute(assuredAttr);

    Map<Integer,Integer> resultMap = new HashMap<Integer,Integer>();
    if ( (attrs == null) || (attrs.isEmpty()) )
      return resultMap; // Empty map

    Attribute attr = attrs.get(0);
    // Parse and store values
    for (AttributeValue val : attr) {
      String srvStr = val.toString();
      StringTokenizer strtok = new StringTokenizer(srvStr, ":");
      String token = strtok.nextToken();
      if (token != null) {
        int serverId = Integer.valueOf(token);
        token = strtok.nextToken();
        if (token != null) {
          Integer nerrors = Integer.valueOf(token);
          resultMap.put(serverId, nerrors);
        }
      }
    }

    return resultMap;
  }

  private void waitOpResult(Operation operation, ResultCode expectedResult)
  {
    int ii=0;
    while((operation.getResultCode()==ResultCode.UNDEFINED) ||
        (operation.getResultCode()!=expectedResult))
    {
      sleep(50);
      ii++;
      if (ii>10)
        assertEquals(operation.getResultCode(), expectedResult,
            operation.getErrorMessage().toString());
    }
  }
}

