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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.*;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Arrays.*;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/**
 * Test Server part of the assured feature in both safe data and
 * safe read modes.
 */
@SuppressWarnings("javadoc")
public class AssuredReplicationServerTest
  extends ReplicationTestCase
{

  private String testName = this.getClass().getSimpleName();
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private int[] rsPorts;
  private static final int FDS1_ID = 1;
  private static final int FDS2_ID = 2;
  private static final int FDS3_ID = 3;
  private static final int FDS4_ID = 4;
  private static final int FDS5_ID = 5;
  private static final int FDS6_ID = 6;
  private static final int FDS7_ID = 7;
  private static final int FDS8_ID = 8;
  private static final int FDS9_ID = 9;
  private static final int FDS10_ID = 10;
  private static final int FDS11_ID = 11;
  private static final int FDS12_ID = 12;
  private static final int FRS1_ID = 51;
  private static final int FRS2_ID = 52;
  private static final int FRS3_ID = 53;
  private static final int DS_FRS2_ID = FRS2_ID + 10;
  private static final int RS1_ID = 101;
  private static final int RS2_ID = 102;
  private static final int RS3_ID = 103;
  private static final int RS4_ID = 104;
  /**
   * We don't use index 0 to stay consistent with what old code was doing
   * <code>fakeRd1 == fakeRDs[1]</code>, etc.
   */
  private FakeReplicationDomain[] fakeRDs;
  private FakeReplicationServer fakeRs1;
  private FakeReplicationServer fakeRs2;
  private FakeReplicationServer fakeRs3;
  private ReplicationServer rs1;
  private ReplicationServer rs2;
  private ReplicationServer rs3;
  private ReplicationServer rs4;

  /**
   * Small assured timeout value (timeout to be used in first RS receiving an
   * assured update from a DS).
   */
  private static final int SMALL_TIMEOUT = 3000;
  /**
   * Long assured timeout value (timeout to use in DS when sending an assured
   * update).
   */
  private static final int LONG_TIMEOUT = 5000;
  /**
   * Expected max time for sending an assured update and receive its ack
   * (without errors).
   */
  private static final int MAX_SEND_UPDATE_TIME = 2000;

  /** Default group id. */
  private static final int DEFAULT_GID = 1;
  /** Other group ids. */
  private static final int OTHER_GID = 2;
  private static final int OTHER_GID_BIS = 3;

  /** Default generation id. */
  private static final long DEFAULT_GENID = EMPTY_DN_GENID;
  /** Other generation id. */
  private static final long OTHER_GENID = 500L;

  /*
   * Definitions for the scenario of the fake DS
   */
  /** DS receives updates and replies acks with no errors to every updates. */
  private static final int REPLY_OK_DS_SCENARIO = 1;
  /** DS receives updates but does not respond (makes timeouts). */
  private static final int TIMEOUT_DS_SCENARIO = 2;
  /** DS receives updates and replies ack with replay error flags. */
  private static final int REPLAY_ERROR_DS_SCENARIO = 3;

  /*
   * Definitions for the scenario of the fake RS
   */
  /** RS receives updates and replies acks with no errors to every updates. */
  private static final int REPLY_OK_RS_SCENARIO = 11;
  /** RS receives updates but does not respond (makes timeouts). */
  private static final int TIMEOUT_RS_SCENARIO = 12;
  /**
   * RS is used for sending updates (with sendNewFakeUpdate()) and receive acks,
   * synchronously.
   */
  private static final int SENDER_RS_SCENARIO = 13;
  //   Scenarios only used in safe read tests:
  /**
   * RS receives updates and replies ack error as if a DS was connected to it
   * and timed out.
   */
  private static final int DS_TIMEOUT_RS_SCENARIO_SAFE_READ = 14;
  /**
   * RS receives updates and replies ack error as if a DS was connected to it
   * and was wrong status.
   */
  private static final int DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ = 15;
  /**
   * RS receives updates and replies ack error as if a DS was connected to it
   * and had a replay error.
   */
  private static final int DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ = 16;

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  /**
   * Before starting the tests configure some stuff.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    rsPorts = TestCaseUtils.findFreePorts(4);
  }

  private void initTest()
  {
    fakeRDs = new FakeReplicationDomain[13];
    fakeRs1 = fakeRs2 = fakeRs3 = null;
    rs1 = rs2 = rs3 = rs4 = null;
  }

  private void endTest() throws Exception
  {
    disableService(fakeRDs);
    Arrays.fill(fakeRDs, null);
    shutdown(fakeRs1, fakeRs2, fakeRs3);
    fakeRs1 = fakeRs2 = fakeRs3 = null;
    remove(rs1, rs2, rs3, rs4);
    rs1 = rs2 = rs3 = rs4 = null;
  }

  private void disableService(FakeReplicationDomain... fakeRDs)
  {
    for (FakeReplicationDomain fakeRd : fakeRDs)
    {
      if (fakeRd != null)
      {
        fakeRd.disableService();
      }
    }
  }

  private void shutdown(FakeReplicationServer... fakeRSs)
  {
    for (FakeReplicationServer fakeRs : fakeRSs)
    {
      if (fakeRs != null)
      {
        fakeRs.shutdown();
      }
    }
  }

  /**
   * Creates and connects a new fake replication domain, using the passed scenario
   * (no server state constructor version).
   */
  private FakeReplicationDomain createFakeReplicationDomain(int serverId,
      int groupId, int rsId, long generationId, AssuredMode assuredMode,
      int safeDataLevel, long assuredTimeout, int scenario) throws Exception
  {
    return createFakeReplicationDomain(serverId, groupId, rsId, generationId,
        assuredMode, safeDataLevel, assuredTimeout, scenario,
        new ServerState(), true);
  }

  private int getRsPort(int rsId)
  {
    return rsPorts[rsId - 101];
  }

  /**
   * Creates a new fake replication domain, using the passed scenario.
   *
   * @param groupId
   *          The group ID for the replication domain.
   * @param rsId
   *          The replication server ID.
   * @param generationId
   *          The generationID associated with data in the domain.
   * @param assured
   *          Is this domain using assured replication.
   * @param assuredMode
   *          The mode if assured replication is enabled.
   * @param safeDataLevel
   *          The
   * @param assuredTimeout
   *          The timeout for acks in assured mode.
   * @param scenario
   *          The scenario identifier
   * @param serverState
   *          The state of the server to start with
   * @param startListen
   *          If true, we start the listen service. In all cases, the publish
   *          service gets started.
   * @return
   *          The FakeReplicationDomain, a mock-up of a Replication Domain
   *          for tests
   * @throws Exception
   */
  private FakeReplicationDomain createFakeReplicationDomain(
      int serverId, int groupId, int rsId, long generationId,
      AssuredMode assuredMode, int safeDataLevel,
      long assuredTimeout, int scenario, ServerState serverState,
      boolean startListen) throws Exception
  {
    final DomainFakeCfg config = newDomainConfig(serverId, groupId, rsId,
        assuredMode, safeDataLevel, assuredTimeout);
    return createFakeReplicationDomain(config, rsId, generationId, scenario,
        serverState, startListen);
  }

  private FakeReplicationDomain createFakeReplicationDomain(
      ReplicationDomainCfg config, int rsId, long generationId, int scenario,
      ServerState serverState, boolean startListen) throws Exception
  {
    FakeReplicationDomain fakeReplicationDomain =
        new FakeReplicationDomain(config, generationId, scenario, serverState);

    fakeReplicationDomain.startPublishService();
    if (startListen)
    {
      fakeReplicationDomain.startListenService();
    }

    // Test connection
    assertTrue(fakeReplicationDomain.isConnected());
    // Check connected server port
    HostPort rd = HostPort.valueOf(fakeReplicationDomain.getReplicationServer());
    assertEquals(rd.getPort(), getRsPort(rsId));

    return fakeReplicationDomain;
  }

  private DomainFakeCfg newDomainConfig(int serverId, int groupId, int rsId,
      AssuredMode assuredMode, int safeDataLevel, long assuredTimeout)
      throws DirectoryException
  {
    final int rsPort = getRsPort(rsId);
    final DomainFakeCfg fakeCfg = new DomainFakeCfg(
        DN.valueOf(TEST_ROOT_DN_STRING), serverId, newTreeSet("localhost:" + rsPort),
        getAssuredType(assuredMode),
        safeDataLevel, groupId, assuredTimeout, new TreeSet<String>());
    fakeCfg.setHeartbeatInterval(1000);
    fakeCfg.setChangetimeHeartbeatInterval(500);
    return fakeCfg;
  }

  private AssuredType getAssuredType(AssuredMode assuredMode)
  {
    if (assuredMode == null)
    {
      return AssuredType.NOT_ASSURED;
    }

    switch (assuredMode)
    {
    case SAFE_READ_MODE:
      return AssuredType.SAFE_READ;
    case SAFE_DATA_MODE:
      return AssuredType.SAFE_DATA;
    }
    throw new RuntimeException("Not implemented for " + assuredMode);
  }

  private FakeReplicationServer createFakeReplicationServer(int serverId,
      int groupId, long generationId, boolean assured,
      AssuredMode assuredMode, int safeDataLevel, int scenario)
      throws Exception
  {
    return createFakeReplicationServer(serverId, groupId, RS1_ID, generationId,
        assured, assuredMode, safeDataLevel, new ServerState(), scenario);
  }

  /**
   * Creates and connects a new fake replication server, using the passed scenario.
   */
  private FakeReplicationServer createFakeReplicationServer(int serverId,
      int groupId, int rsId, long generationId, boolean assured,
      AssuredMode assuredMode, int safeDataLevel, ServerState serverState,
      int scenario) throws Exception
  {
      // Set port to right real RS according to its id
      int rsPort = getRsPort(rsId);

      FakeReplicationServer fakeReplicationServer = new FakeReplicationServer(
        rsPort, serverId, assured, assuredMode, safeDataLevel, groupId,
        DN.valueOf(TEST_ROOT_DN_STRING), generationId);

      // Connect fake RS to the real RS
      fakeReplicationServer.connect(serverState);

      // Start wished scenario
      fakeReplicationServer.start(scenario);

      return fakeReplicationServer;
  }

  /**
   * Creates a new real replication server (one which is to be tested).
   */
  private ReplicationServer createReplicationServer(int serverId, int groupId,
      long assuredTimeout, String testCase, int nbRS) throws ConfigException
  {
    int port = getRsPort(serverId);
    SortedSet<String> otherRsUrls =
        generateOtherReplicationServerUrls(port, nbRS);

    String dir = testName + serverId + testCase + "Db";
    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(port, dir, replicationDbImplementation, 0, serverId, 0,
            100, otherRsUrls, groupId, assuredTimeout, 5000);
    // No monitoring publisher to not interfere with some SocketTimeoutException
    // expected at some points in these tests
    conf.setMonitoringPeriod(0L);
    return new ReplicationServer(conf);
  }

  /**
   * Returns a Set<String> containing the URLs for the real Replication Servers
   * (RS for short) for the specified number of RSs excluding the URL for the
   * excludedRsPort. The returned Set size is nbRS - 1 (for the excluded port).
   *
   * @param excludedRsPort
   *          the RS port to exclude
   * @param totalNbRS
   *          the total number of real RSs that will be part of the topology.
   * @return a SortedSet<String> containing the RS URLs.
   */
  private SortedSet<String> generateOtherReplicationServerUrls(
      int excludedRsPort, int totalNbRS)
  {
    SortedSet<String> replServers = new TreeSet<>();
    if (totalNbRS >= 2)
    {
      addIfNotSame(replServers, rsPorts[0], excludedRsPort);
      addIfNotSame(replServers, rsPorts[1], excludedRsPort);
      if (totalNbRS >= 3)
      {
        addIfNotSame(replServers, rsPorts[2], excludedRsPort);
        if (totalNbRS >= 4)
        {
          addIfNotSame(replServers, rsPorts[3], excludedRsPort);
        }
      }
    }
    return replServers;
  }

  private void addIfNotSame(Set<String> replServers, int rsPort,
      int excludedRsPort)
  {
    if (rsPort != excludedRsPort)
    {
      replServers.add("localhost:" + rsPort);
    }
  }

  /**
   * Fake replication domain implementation to test the replication server
   * regarding the assured feature.
   * According to the configured scenario, it will answer to updates with acks
   * as the scenario is requesting.
   */
  private class FakeReplicationDomain extends ReplicationDomain
  {
    /** The scenario this DS is expecting. */
    private final int scenario;

    private final CSNGenerator gen;

    /** Number of received updates. */
    private int nReceivedUpdates;
    private int nWrongReceivedUpdates;

    /**
     * Creates a fake replication domain (DS)
     * @param baseDN The base dn used at connection to RS
     * @param serverID our server id
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
    private FakeReplicationDomain(ReplicationDomainCfg config,
        long generationId, int scenario, ServerState serverState)
        throws ConfigException
    {
      super(config, generationId, serverState);
      this.scenario = scenario;

      gen = new CSNGenerator(config.getServerId(), 0L);
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
        case TIMEOUT_DS_SCENARIO:
          // Let timeout occur
          break;
        case REPLY_OK_DS_SCENARIO:
          // Send the ack without errors
          // Call processUpdateDone and update the server state is what needs to
          // be done when using asynchronous process update mechanism
          // (see processUpdate javadoc)
          processUpdateDone(updateMsg, null);
          getServerState().update(updateMsg.getCSN());
          break;
        case REPLAY_ERROR_DS_SCENARIO:
          // Send the ack with replay error
          // Call processUpdateDone and update the server state is what needs to
          // be done when using asynchronous process update mechanism
          // (see processUpdate javadoc)
          processUpdateDone(updateMsg, "This is the replay error message generated from fake DS " +
            getServerId() + " for update with CSN " + updateMsg.getCSN());
          getServerState().update(updateMsg.getCSN());
          break;
        default:
          Assert.fail("Unknown scenario: " + scenario);
      }
      // IMPORTANT: return false so that we use the asynchronous processUpdate mechanism
      // (see processUpdate javadoc)
      return false;
    }

    /**
     * Check that received update assured parameters are as defined at DS start.
     */
    private void checkUpdateAssuredParameters(UpdateMsg updateMsg)
    {
      boolean ok = true;
      if (updateMsg.isAssured() != isAssured())
      {
        debugInfo("Fake DS " + getServerId() + " received update assured flag is wrong: " + updateMsg);
        ok = false;
      }
      if (isAssured() && updateMsg.getAssuredMode() != getAssuredMode())
      { // it is meaningless to have different assured mode when UpdateMsg is not assured
        debugInfo("Fake DS " + getServerId() + " received update assured mode is wrong: " + updateMsg);
        ok = false;
      }
      if (updateMsg.getSafeDataLevel() != getAssuredSdLevel())
      {
        debugInfo("Fake DS " + getServerId() + " received update assured sd level is wrong: " + updateMsg);
        ok = false;
      }

      if (ok)
      {
        debugInfo("Fake DS " + getServerId() + " received update assured parameters are ok: " + updateMsg);
      }
      else
      {
        nWrongReceivedUpdates++;
      }
    }

    /**
     * Sends a new update from this DS.
     * @throws TimeoutException If timeout waiting for an assured ack
     */
    private void sendNewFakeUpdate() throws TimeoutException
    {
      // Create a new delete update message (the simplest to create)
      DeleteMsg delMsg = new DeleteMsg(getBaseDN(), gen.newCSN(), UUID.randomUUID().toString());

      // Send it (this uses the defined assured conf at constructor time)
      prepareWaitForAckIfAssuredEnabled(delMsg);
      publish(delMsg);
      waitForAckIfAssuredEnabled(delMsg);
    }

    private void assertReceivedWrongUpdates(int expectedNbUpdates, int expectedNbWrongUpdates)
    {
      final SoftAssertions softly = new SoftAssertions();
      softly.assertThat(nReceivedUpdates).as("receivedUpdates").isEqualTo(expectedNbUpdates);
      softly.assertThat(nWrongReceivedUpdates).as("wrongReceivedUpdates").isEqualTo(expectedNbWrongUpdates);
      softly.assertAll();
    }

    private void assertReceivedUpdates(int expectedNbUpdates)
    {
      assertReceivedWrongUpdates(expectedNbUpdates, 0);
    }

    public SafeReadAssertions newSafeReadAssertions()
    {
      return new SafeReadAssertions(this);
    }
  }

  private static class SafeReadAssertions
  {
    private ReplicationDomain domain;
    private int sentUpdates;
    private int acknowledgedUpdates;
    private int notAcknowledgedUpdates;
    private int timeoutUpdates;
    private int wrongStatusUpdates;
    private int replayErrorUpdates;
    private final Map<Integer, Integer> serverNotAcknowledgedUpdates = new HashMap<>();
    private int receivedUpdates;
    private int receivedUpdatesAcked;
    private int receivedUpdatesNotAcked;

    public SafeReadAssertions(FakeReplicationDomain domain)
    {
      this.domain = domain;
    }

    public void runAsserts()
    {
      final SoftAssertions softly = new SoftAssertions();
      softly.assertThat(domain.getAssuredSrSentUpdates()).as("sentUpdates").isEqualTo(sentUpdates);
      softly.assertThat(domain.getAssuredSrAcknowledgedUpdates()).as("acknowledgedUpdates").isEqualTo(acknowledgedUpdates);
      softly.assertThat(domain.getAssuredSrNotAcknowledgedUpdates()).as("notAcknowledgedUpdates").isEqualTo(notAcknowledgedUpdates);
      softly.assertThat(domain.getAssuredSrTimeoutUpdates()).as("timeoutUpdates").isEqualTo(timeoutUpdates);
      softly.assertThat(domain.getAssuredSrWrongStatusUpdates()).as("wrongStatusUpdates").isEqualTo(wrongStatusUpdates);
      softly.assertThat(domain.getAssuredSrReplayErrorUpdates()).as("replayErrorUpdates").isEqualTo(replayErrorUpdates);
      softly.assertThat(domain.getAssuredSrServerNotAcknowledgedUpdates()).as("serverNotAcknowledgedUpdates").isEqualTo(
          serverNotAcknowledgedUpdates);
      softly.assertThat(domain.getAssuredSrReceivedUpdates()).as("receivedUpdates").isEqualTo(receivedUpdates);
      softly.assertThat(domain.getAssuredSrReceivedUpdatesAcked()).as("receivedUpdatesAcked").isEqualTo(receivedUpdatesAcked);
      softly.assertThat(domain.getAssuredSrReceivedUpdatesNotAcked()).as("receivedUpdatesNotAcked").isEqualTo(receivedUpdatesNotAcked);
      softly.assertAll();
    }

    public SafeReadAssertions sentUpdates(int value)
    {
      sentUpdates = value;
      return this;
    }

    public SafeReadAssertions acknowledgedUpdates(int value)
    {
      acknowledgedUpdates = value;
      return this;
    }

    public SafeReadAssertions notAcknowledgedUpdates(int value)
    {
      notAcknowledgedUpdates = value;
      return this;
    }

    public SafeReadAssertions timeoutUpdates(int value)
    {
      timeoutUpdates = value;
      return this;
    }

    public SafeReadAssertions wrongStatusUpdates(int value)
    {
      wrongStatusUpdates = value;
      return this;
    }

    public SafeReadAssertions replayErrorUpdates(int value)
    {
      replayErrorUpdates = value;
      return this;
    }

    public SafeReadAssertions serverNotAcknowledgedUpdates(int key, int value)
    {
      serverNotAcknowledgedUpdates.put(key, value);
      return this;
    }

    public SafeReadAssertions receivedUpdates(int value)
    {
      receivedUpdates = value;
      return this;
    }

    public SafeReadAssertions receivedUpdatesAcked(int value)
    {
      receivedUpdatesAcked = value;
      return this;
    }

    public SafeReadAssertions receivedUpdatesNotAcked(int value)
    {
      receivedUpdatesNotAcked = value;
      return this;
    }
  }

  /**
   * The fake replication server used to emulate RS behavior the way we want
   * for assured features test.
   * This fake replication server is able to receive another RS connection only.
   * According to the configured scenario, it will answer to updates with acks
   * as the scenario is requesting.
   */
  private static int fakePort;

  private class FakeReplicationServer extends Thread
  {

    private boolean shutdown;
    private Session session;

    /** Parameters given at constructor time. */
    private int port;
    private int serverId = -1;
    /** Default value for config. */
    private boolean isAssured;
    /** Default value for config. */
    private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
    /** Default value for config. */
    private byte safeDataLevel = 1;
    private DN baseDN;
    private long generationId = -1L;
    private byte groupId = -1;
    private boolean sslEncryption;
    /** The scenario this RS is expecting. */
    private int scenario = -1;

    private CSNGenerator gen;

    /** False if a received update had assured parameters not as expected. */
    private boolean everyUpdatesAreOk = true;
    /** Number of received updates. */
    private int nReceivedUpdates;

    /**
     * True if an ack has been replied to a received assured update (in assured
     * mode of course) used in reply scenario.
     */
    private boolean ackReplied;

    /**
     * Creates a fake replication server.
     * @param port port of the real RS we will connect to
     * @param serverId our server id
     * @param assured do we expect incoming assured updates (also used for outgoing updates)
     * @param assuredMode the expected assured mode of the incoming updates (also used for outgoing updates)
     * @param safeDataLevel the expected safe data level of the incoming updates (also used for outgoing updates)
     * @param groupId our group id
     * @param baseDN the baseDN we connect with, to the real RS
     * @param generationId the generation id we use at connection to real RS
     */
    public FakeReplicationServer(int port, int serverId, boolean assured,
      AssuredMode assuredMode, int safeDataLevel,
      int groupId, DN baseDN, long generationId)
    {
      this.port = port;
      this.serverId = serverId;
      this.baseDN = baseDN;
      this.generationId = generationId;
      this.groupId = (byte) groupId;
      this.isAssured = assured;
      this.assuredMode = assuredMode;
      this.safeDataLevel = (byte) safeDataLevel;

      gen = new CSNGenerator(serverId + 10, 0L);
    }

    /**
     * Make the RS send an assured message and return the ack message it
     * receives from the RS.
     */
    public AckMsg sendNewFakeUpdate() throws Exception
    {
        // Create a new delete update message (the simplest to create)
        DeleteMsg delMsg = new DeleteMsg(baseDN, gen.newCSN(),
        UUID.randomUUID().toString());

        // Send del message in assured mode
        delMsg.setAssured(isAssured);
        delMsg.setAssuredMode(assuredMode);
        delMsg.setSafeDataLevel(safeDataLevel);
        session.publish(delMsg);

        // Read and return matching ack
        ReplicationMsg replMsg = session.receive();
        if (replMsg instanceof ErrorMsg)
        {
        // Support for connection done with bad gen id : we receive an error
          // message that we must throw away before reading our ack.
          replMsg = session.receive();
        }
        return (AckMsg)replMsg;
    }

    /**
     * Connect to RS.
     */
    public void connect(ServerState serverState) throws Exception
    {
        // Create and connect socket
        InetSocketAddress serverAddr =
          new InetSocketAddress("localhost", port);
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
        socket.connect(serverAddr, timeoutMS);

        // Create client session
        fakePort++;
        String fakeUrl = "localhost:" + fakePort;
        ReplSessionSecurity replSessionSecurity = new ReplSessionSecurity();
        session = replSessionSecurity.createClientSession(socket, timeoutMS);

        // Send our repl server start msg
        ReplServerStartMsg replServerStartMsg = new ReplServerStartMsg(serverId,
          fakeUrl, baseDN, 100, serverState,
          generationId, sslEncryption, groupId, 5000);
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
        RSInfo rsInfo = new RSInfo(serverId, fakeUrl, generationId, groupId, 1);
        session.publish(new TopologyMsg(null, newArrayList(rsInfo)));

        // Read topo msg
        TopologyMsg inTopoMsg = (TopologyMsg) session.receive();
        debugInfo("Fake RS " + serverId + " handshake received the following info:" + inTopoMsg);
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
     * Wait for DS connections.
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
                  AckMsg ackMsg = new AckMsg(updateMsg.getCSN());
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
                  AckMsg ackMsg = new AckMsg(updateMsg.getCSN());
                  ackMsg.setHasTimeout(true);
                  ackMsg.setFailedServers(newArrayList(serverId + 10));
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              case DS_WRONG_STATUS_RS_SCENARIO_SAFE_READ:
                if (updateMsg.isAssured())
                {
                  // Send the ack with wrong status error from a virtual DS with id (ours + 10)
                  AckMsg ackMsg = new AckMsg(updateMsg.getCSN());
                  ackMsg.setHasWrongStatus(true);
                  ackMsg.setFailedServers(newArrayList(serverId + 10));
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              case DS_REPLAY_ERROR_RS_SCENARIO_SAFE_READ:
                if (updateMsg.isAssured())
                {
                  // Send the ack with replay error from a virtual DS with id (ours + 10)
                  AckMsg ackMsg = new AckMsg(updateMsg.getCSN());
                  ackMsg.setHasReplayError(true);
                  ackMsg.setFailedServers(newArrayList(serverId + 10));
                  session.publish(ackMsg);
                  ackReplied = true;
                }
                break;
              default:
                Assert.fail("Unknown scenario: " + scenario);
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

      // Shutdown any current client handling code
      if (session != null)
      {
        session.close();
      }

      try
      {
        join();
      } catch (InterruptedException ignored)
      {
      }
    }

    /**
     * Check that received update assured parameters are as defined at RS start.
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
      {
        debugInfo("Fake RS " + serverId + " received update assured parameters are ok: " + updateMsg);
      }
      else
      {
        everyUpdatesAreOk = false;
      }
    }

    /**
     * Test if the last received updates was acknowledged (ack sent with or
     * without errors).
     * <p>
     * WARNING: this must be called once per update as it also immediately
     * resets the status for a new test for the next update
     * </p>
     *
     * @return True if acknowledged
     */
    public boolean ackReplied()
    {
      boolean result = ackReplied;
      // reset ack replied status
      ackReplied = false;
      return result;
    }

    private void assertReceivedUpdates(int expectedNbUpdates)
    {
      assertEquals(nReceivedUpdates, expectedNbUpdates);
      assertTrue(everyUpdatesAreOk);
    }

    private void assertReceivedAckedUpdates(final int expectedNbUpdates,
        final boolean expectingAckReplied)
    {
      assertReceivedUpdates(expectedNbUpdates);
      assertEquals(ackReplied(), expectingAckReplied);
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
   * Returns possible combinations of parameters for testSafeDataLevelOne test.
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
  @Test(dataProvider = "testSafeDataLevelOneProvider",
        groups = { "slow", "opendj-256" },
        enabled = true)
  public void testSafeDataLevelOne(
      int mainDsGid, boolean otherFakeDS, boolean fakeRS,
      int otherFakeDsGid, int fakeRsGid) throws Exception
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
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT, testCase, 0);

      /*
       * Start main DS (the one which sends updates)
       */

      // Create and connect fake domain 1 to RS 1
      // Assured mode: SD, level 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, mainDsGid, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_DATA_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

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
        // the update will even not come to real RS as assured
        fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, otherFakeDsGid, RS1_ID,
            DEFAULT_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);
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
        // the update will even not come to real RS as assured
        fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRsGid, DEFAULT_GENID,
          false, AssuredMode.SAFE_DATA_MODE, 1, TIMEOUT_RS_SCENARIO);
      }

      // Send update from DS 1
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();

      // Check call time (should have last a lot less than long timeout)
      // (ack received if group id of DS and real RS are the same, no ack requested
      // otherwise)
      long sendUpdateTime = System.currentTimeMillis() - startTime;
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      if (mainDsGid == DEFAULT_GID)
      {
        // Check monitoring values (check that ack has been correctly received)
        assertEquals(fakeRd1.getAssuredSdSentUpdates(), 1);
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), 1);
      }
      else
      {
        // Check monitoring values (DS group id (OTHER_GID) is not the same as RS one
        // (DEFAULT_GID) so update should have been sent in normal mode
        assertEquals(fakeRd1.getAssuredSdSentUpdates(), 0);
        assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), 0);
      }
      assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), 0);
      assertEquals(fakeRd1.getAssuredSdServerTimeoutUpdates().size(), 0);

      // Sanity check
      Thread.sleep(500);           // Let time to update to reach other servers
      fakeRd1.assertReceivedUpdates(0);
      if (otherFakeDS)
      {
        fakeRDs[2].assertReceivedUpdates(1);
      }
      if (fakeRS)
      {
        fakeRs1.assertReceivedUpdates(1);
      }
    } finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeDataLevelHighPrecommit test.
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
   * Returns possible combinations of parameters for testSafeDataLevelHighNightly test.
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
   * Returns possible combinations of parameters for testSafeDataLevelHigh test.
   */
  @DataProvider(name = "testSafeDataLevelHighProvider")
  private Object[][] testSafeDataLevelHighProvider()
  {
    // Construct all possible combinations of parameters
    List<List<Object>> objectArrayList = new ArrayList<>();

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

    debugInfo("testSafeDataLevelHighProvider: number of possible parameter combinations : "
        + objectArrayList.size());
    return toDataProvider(objectArrayList);
  }

  /**
   * Helper for providers: Modify the passed object array list adding to each
   * already contained object array each passed possible values.
   * <p>
   * Example: to create all possible parameter combinations for a test method
   * which has 2 parameters: one boolean then an integer, with both 2 possible
   * values: {true|false} and {10|100}:
   *
   * <pre>
   * List&lt;List&lt;Object&gt;&gt; objectArrayList = new ArrayList&lt;List&lt;Object&gt;&gt;();
   * // Possible boolean values
   * objectArrayList = addPossibleParameters(objectArrayList, true, false);
   * // Possible integer values
   * objectArrayList = addPossibleParameters(objectArrayList, 10, 100);
   * Object[][] result = new Object[objectArrayList.size()][];
   * int i = 0;
   * for (List&lt;Object&gt; objectArray : objectArrayList)
   * {
   *   result[i] = objectArray.toArray();
   *   i++;
   * }
   * return result;
   * </pre>
   *
   * The provider will return the equivalent following Object[][]:
   *
   * <pre>
   * new Object[][]
   * {
   *   { true, 10},
   *   { true, 100},
   *   { false, 10},
   *   { false, 100}
   * };
   * </pre>
   *
   * </p>
   */
  private List<List<Object>> addPossibleParameters(List<List<Object>> objectArrayList, Object... possibleParameters)
  {
    List<List<Object>> newObjectArrayList = new ArrayList<>();

    if (objectArrayList.isEmpty())
    {
      // First time we add some parameters, create first object arrays
      // Add each possible parameter as initial parameter lists
      for (Object possibleParameter : possibleParameters)
      {
        newObjectArrayList.add(newArrayList(possibleParameter));
      }
      return newObjectArrayList;
    }

    for (List<Object> objectArray : objectArrayList)
    {
      // Add each possible parameter to the already existing list
      for (Object possibleParameter : possibleParameters)
      {
        final List<Object> newObjectArray = new ArrayList<>(objectArray);
        newObjectArray.add(possibleParameter);
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
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT, testCase, 0);

      /*
       * Start main DS (the one which sends updates)
       */

      // Create and connect fake domain 1 to RS 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_DATA_MODE, sdLevel, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      /*
       * Start one other fake DS
       */

      // Put another fake domain connected to real RS ?
      if (otherFakeDS)
      {
        fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, otherFakeDsGid, RS1_ID,
            otherFakeDsGenId, null, sdLevel, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);
      }

      /*
       * Start 3 fake Rss
       */

      // Put a fake RS 1 connected to real RS
      fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRs1Gid, fakeRs1GenId,
        fakeRs1Gid == DEFAULT_GID, AssuredMode.SAFE_DATA_MODE, sdLevel, fakeRs1Scen);

      // Put a fake RS 2 connected to real RS
      fakeRs2 = createFakeReplicationServer(FRS2_ID, fakeRs2Gid, fakeRs2GenId,
        fakeRs2Gid == DEFAULT_GID, AssuredMode.SAFE_DATA_MODE, sdLevel, fakeRs2Scen);

      // Put a fake RS 3 connected to real RS
      fakeRs3 = createFakeReplicationServer(FRS3_ID, fakeRs3Gid, fakeRs3GenId,
        fakeRs3Gid == DEFAULT_GID, AssuredMode.SAFE_DATA_MODE, sdLevel, fakeRs3Scen);

      // Wait for connections to be finished
      // DS must see expected numbers of fake DSs and RSs
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      waitForStableTopo(fakeRd1, (otherFakeDS ? 1 : 0), 4);

      /***********************************************************************
       * Send update from DS 1 (3 fake RSs available) and check what happened
       ***********************************************************************/

      // Keep track of monitoring values for incremental test step
      int acknowledgedUpdates = fakeRd1.getAssuredSdAcknowledgedUpdates();
      int timeoutUpdates = fakeRd1.getAssuredSdTimeoutUpdates();
      Map<Integer,Integer> serverErrors = fakeRd1.getAssuredSdServerTimeoutUpdates();
      // Compute the list of servers that are eligible for receiving an assured update
      List<Integer> eligibleServers = computeEligibleServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs2Gid, fakeRs2GenId, fakeRs3Gid, fakeRs3GenId);
      // Compute the list of servers that are eligible for receiving an assured update and that are expected to effectively ack the update
      List<Integer> expectedServers = computeExpectedServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, fakeRs2Gid, fakeRs2GenId, fakeRs2Scen, fakeRs3Gid, fakeRs3GenId, fakeRs3Scen);

      // Send update
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(1, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, eligibleServers, expectedServers);
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
      // Compute the list of servers that are eligible for receiving an assured update
      eligibleServers = computeEligibleServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs2Gid, fakeRs2GenId, -1, -1L);
      // Compute the list of servers that are eligible for receiving an assured update and that are expected to effectively ack the update
      expectedServers = computeExpectedServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, fakeRs2Gid, fakeRs2GenId, fakeRs2Scen, -1, -1L, -1);

      // Send update
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(2, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, eligibleServers, expectedServers);
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
      // Compute the list of servers that are eligible for receiving an assured update
      eligibleServers = computeEligibleServersSafeData(fakeRs1Gid, fakeRs1GenId, -1, -1L, -1, -1L);
      // Compute the list of servers that are eligible for receiving an assured update and that are expected to effectively ack the update
      expectedServers = computeExpectedServersSafeData(fakeRs1Gid, fakeRs1GenId, fakeRs1Scen, -1, -1L, -1, -1, -1L, -1);

      // Send update
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(3, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, eligibleServers, expectedServers);
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
      // Compute the list of servers that are eligible for receiving an assured update
      eligibleServers = computeEligibleServersSafeData(-1, -1L, -1, -1L, -1, -1L);
      // Compute the list of servers that are eligible for receiving an assured update and that are expected to effectively ack the update
      expectedServers = computeExpectedServersSafeData(-1, -1L, -1, -1, -1L, -1, -1, -1L, -1);

      // Send update
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked and let time the update to reach other servers
      checkTimeAndMonitoringSafeData(4, acknowledgedUpdates, timeoutUpdates, serverErrors, sendUpdateTime, nWishedServers, eligibleServers, expectedServers);
      checkWhatHasBeenReceivedSafeData(4, otherFakeDS, otherFakeDsGenId, -1L, -1L, -1L, expectedServers);
    } finally
    {
      endTest();
    }
  }

  /**
   * Check that the DSs and the fake RSs of the topology have received/acked
   * what is expected according to the test step (the number of updates).
   * -1 for a gen id means no need to test the matching fake RS
   */
  private void checkWhatHasBeenReceivedSafeData(int nSentUpdates, boolean otherFakeDS, long otherFakeDsGenId, long fakeRs1GenId, long fakeRs2GenId, long fakeRs3GenId, List<Integer> expectedServers)
  {
    final FakeReplicationDomain fakeRd1 = fakeRDs[1];
    final FakeReplicationDomain fakeRd2 = fakeRDs[2];

    // We should not receive our own update
    fakeRd1.assertReceivedUpdates(0);

    // Check what received other fake DS
    if (otherFakeDS)
    {
      fakeRd2.assertReceivedUpdates(otherFakeDsGenId == DEFAULT_GENID ? nSentUpdates : 0);
    }

    // Check what received/did fake Rss
    if (nSentUpdates < 4)  // Fake RS 3 is stopped after 3 updates sent
    {
      final int expectedNb = fakeRs1GenId == DEFAULT_GENID ? nSentUpdates : 0;
      fakeRs1.assertReceivedAckedUpdates(expectedNb, expectedServers.contains(FRS1_ID));
    }

    if (nSentUpdates < 3)  // Fake RS 3 is stopped after 2 updates sent
    {
      final int expectedNb = fakeRs2GenId == DEFAULT_GENID ? nSentUpdates : 0;
      fakeRs2.assertReceivedAckedUpdates(expectedNb, expectedServers.contains(FRS2_ID));
    }

    if (nSentUpdates < 2) // Fake RS 3 is stopped after 1 update sent
    {
      final int expectedNb = fakeRs3GenId == DEFAULT_GENID ? nSentUpdates : 0;
      fakeRs3.assertReceivedAckedUpdates(expectedNb, expectedServers.contains(FRS3_ID));
    }
  }

  /**
   * Check the time the sending of the safe data assured update took and the monitoring
   * values according to the test configuration.
   */
  private void checkTimeAndMonitoringSafeData(int nSentUpdates, int prevNAckUpdates, int prevNTimeoutUpdates, Map<Integer,Integer> prevNServerErrors, long sendUpdateTime,
    int nWishedServers, List<Integer> eligibleServers, List<Integer> expectedServers)
  {
    final FakeReplicationDomain fakeRd1 = fakeRDs[1];
    assertEquals(fakeRd1.getAssuredSdSentUpdates(), nSentUpdates);
    if (eligibleServers.size() >= nWishedServers) // Enough eligible servers
    {
      if (expectedServers.size() >= nWishedServers) // Enough servers should ack
      {
        checkAckOccured(sendUpdateTime, prevNAckUpdates, prevNTimeoutUpdates, prevNServerErrors);
      } else
      {
        Set<Integer> serversInError = computeExpectedServersInError(eligibleServers, expectedServers);
        checkTimeOutOccured(sendUpdateTime, prevNAckUpdates, prevNTimeoutUpdates, prevNServerErrors, serversInError);
      }
    }
    else
    { // Not enough eligible servers
      if (!eligibleServers.isEmpty()) // Some eligible servers anyway
      {
        if (expectedServers.size() == eligibleServers.size()) // All eligible servers should respond in time
        {
          checkAckOccured(sendUpdateTime, prevNAckUpdates, prevNTimeoutUpdates, prevNServerErrors);
        } else
        { // Some eligible servers should fail
          // Not enough expected servers: should have timed out in RS timeout (SMALL_TIMEOUT)
          Set<Integer> serversInError = computeExpectedServersInError(eligibleServers, expectedServers);
          checkTimeOutOccured(sendUpdateTime, prevNAckUpdates, prevNTimeoutUpdates, prevNServerErrors, serversInError);
        }
      } else
      {
        // No eligible servers at all, RS should not wait for any ack and immediately ack the update
        checkAckOccured(sendUpdateTime, prevNAckUpdates, prevNTimeoutUpdates, prevNServerErrors);
      }
    }
  }

  private void checkAckOccured(long sendUpdateTime, int prevNAckUpdates,
      int prevNTimeoutUpdates, Map<Integer, Integer> prevNServerErrors)
  {
    final FakeReplicationDomain fakeRd1 = fakeRDs[1];
    assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);
    // Check monitoring values (check that ack has been correctly received)
    assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates + 1);
    assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates);
    checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, null); // Should have same value as previous one
  }

  private void checkTimeOutOccured(long sendUpdateTime, int prevNAckUpdates,
      int prevNTimeoutUpdates, Map<Integer, Integer> prevNServerErrors, Set<Integer> expectedServersInError)
  {
    final FakeReplicationDomain fakeRd1 = fakeRDs[1];
    assertBetweenInclusive(sendUpdateTime, SMALL_TIMEOUT, LONG_TIMEOUT);
    // Check monitoring values (check that timeout occurred)
    assertEquals(fakeRd1.getAssuredSdAcknowledgedUpdates(), prevNAckUpdates);
    assertEquals(fakeRd1.getAssuredSdTimeoutUpdates(), prevNTimeoutUpdates + 1);
    // Check that the servers that are eligible but not expected have been added in the error by server list
    checkServerErrors(fakeRd1.getAssuredSdServerTimeoutUpdates(), prevNServerErrors, expectedServersInError);
  }

  /**
   * Compute a list of servers that are eligibles but that are not able to
   * return an ack (those in eligibleServers that are not in expectedServers).
   * Result may of course be an empty list
   */
  private Set<Integer> computeExpectedServersInError(List<Integer> eligibleServers, List<Integer> expectedServers)
  {
    Set<Integer> expectedServersInError = new HashSet<>(eligibleServers);
    expectedServersInError.removeAll(expectedServers);
    return expectedServersInError;
  }

  /**
   * Check that the passed list of errors by server ids is as expected.
   * <ul>
   * <li>if expectedServersInError is not null and not empty, each server id in
   * measuredServerErrors should have the value it has in prevServerErrors + 1,
   * or 1 if it was not in prevServerErrors</li>
   * <li>if expectedServersInError is null or empty, both map should be equal</li>
   * </ul>
   */
  private void checkServerErrors(Map<Integer,Integer> measuredServerErrors, Map<Integer,Integer> prevServerErrors, Set<Integer> expectedServersInError)
  {
    if (expectedServersInError != null)
    {
      // Adding an error to each server in expectedServersInError, with prevServerErrors as basis, should give the
      // same map as measuredServerErrors
      for (Integer serverId : expectedServersInError)
      {
        Integer prevInt = prevServerErrors.get(serverId);
        if (prevInt == null)
        {
          // Add this server to the list of servers in error
          prevServerErrors.put(serverId, 1);
        } else
        {
          // Already errors for this server, increment the value
          prevServerErrors.put(serverId, prevInt + 1);
        }
      }
    }

    assertThat(measuredServerErrors).isEqualTo(prevServerErrors);
  }

  /**
   * Wait until number of fake DSs and fake RSs are available in the topo view of the passed
   * fake DS or throw an assertion if timeout waiting.
   */
  private void waitForStableTopo(FakeReplicationDomain fakeRd, int expectedDs,
      int expectedRs) throws Exception
  {
    Map<Integer, DSInfo> dsInfo = null;
    List<RSInfo> rsInfo = null;
    long nSec = 0;
    long startTime = System.currentTimeMillis();
    do
    {
      dsInfo = fakeRd.getReplicaInfos();
      rsInfo = fakeRd.getRsInfos();
      if (dsInfo.size() == expectedDs && rsInfo.size() == expectedRs)
      {
        debugInfo("waitForStableTopo: expected topo obtained after " + nSec + " second(s).");
        return;
      }
      Thread.sleep(100);
      nSec = (System.currentTimeMillis() - startTime) / 1000;
    }
    while (nSec < 30);
    Assert.fail("Did not reach expected topo view in time: expected " + expectedDs +
      " DSs (had " + dsInfo +") and " + expectedRs + " RSs (had " + rsInfo +").");
  }

  /**
   * Compute the list of servers that are eligible for receiving a safe data
   * assured update according to their group id and generation id. If -1 is
   * used, the server is out of scope
   */
  private List<Integer> computeEligibleServersSafeData(int fakeRs1Gid, long fakeRs1GenId, int fakeRs2Gid, long fakeRs2GenId, int fakeRs3Gid, long fakeRs3GenId)
  {
    List<Integer> eligibleServers = new ArrayList<>(3);
    if (areGroupAndGenerationIdOk(fakeRs1Gid, fakeRs1GenId))
    {
      eligibleServers.add(FRS1_ID);
    }
    if (areGroupAndGenerationIdOk(fakeRs2Gid, fakeRs2GenId))
    {
      eligibleServers.add(FRS2_ID);
    }
    if (areGroupAndGenerationIdOk(fakeRs3Gid, fakeRs3GenId))
    {
      eligibleServers.add(FRS3_ID);
    }
    return eligibleServers;
  }

  /**
   * Are group id and generation id ok for being an eligible RS for assured
   * update ?
   */
  private boolean areGroupAndGenerationIdOk(int fakeRsGid, long fakeRsGenId)
  {
    return fakeRsGid != -1
        && fakeRsGenId != -1L
        && fakeRsGid == DEFAULT_GID
        && fakeRsGenId == DEFAULT_GENID;
  }

  /**
   * Compute the list of fake servers that are eligible for receiving a safe
   * data assured update and that are expected to effectively ack the update. If
   * -1 is used, the server is out of scope
   */
  private List<Integer> computeExpectedServersSafeData(
      int rs1Gid, long rs1GenId, int rs1Scen,
      int rs2Gid, long rs2GenId, int rs2Scen,
      int rs3Gid, long rs3GenId, int rs3Scen)
  {
    List<Integer> expectedServers = new ArrayList<>();
    assertRSExpectations(expectedServers, rs1Gid, rs1GenId, rs1Scen, FRS1_ID);
    assertRSExpectations(expectedServers, rs2Gid, rs2GenId, rs2Scen, FRS2_ID);
    assertRSExpectations(expectedServers, rs3Gid, rs3GenId, rs3Scen, FRS3_ID);
    return expectedServers;
  }

  /**
   * @param expectedServers
   *          the RS expected to reply ok in the given test
   */
  private void assertRSExpectations(List<Integer> expectedServers, int groupId,
      long generationId, int expectedScenario, int rsId)
  {
    if (areGroupAndGenerationIdOk(groupId, generationId))
    {
      List<Integer> acceptableScenarios =
          Arrays.asList(REPLY_OK_RS_SCENARIO, TIMEOUT_RS_SCENARIO);
      assertTrue(acceptableScenarios.contains(expectedScenario),
          "No other scenario should be used here than " + acceptableScenarios);
      if (expectedScenario == REPLY_OK_RS_SCENARIO)
      {
        expectedServers.add(rsId);
      }
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeDataFromRS test.
   */
  @DataProvider(name = "testSafeDataFromRSProvider")
  private Object[][] testSafeDataFromRSProvider()
  {
    List<List<Object>> objectArrayList = new ArrayList<>();

    // Safe Data Level
    objectArrayList = addPossibleParameters(objectArrayList, 1, 2, 3);
    // Fake RS group id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GID, OTHER_GID);
    // Fake RS generation id
    objectArrayList = addPossibleParameters(objectArrayList, DEFAULT_GENID, OTHER_GENID);
    // Fake RS sends update in assured mode
    objectArrayList = addPossibleParameters(objectArrayList, true, false);

    return toDataProvider(objectArrayList);
  }

  private Object[][] toDataProvider(List<List<Object>> listOfList)
  {
    Object[][] result = new Object[listOfList.size()][];
    int i = 0;
    for (List<Object> list : listOfList)
    {
      result[i] = list.toArray();
      i++;
    }
    return result;
  }

  /**
   * Test that the RS is acking or not acking a safe data update sent from another
   * (fake) RS according to passed parameters.
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
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT, testCase, 0);

      /*
       * Start fake RS to make the RS have the default generation id
       */

      // Put a fake RS 2 connected to real RS
      fakeRs2 = createFakeReplicationServer(FRS2_ID, DEFAULT_GID, DEFAULT_GENID,
        false, AssuredMode.SAFE_DATA_MODE, 10, TIMEOUT_RS_SCENARIO);

      /*
       * Start fake RS to send updates
       */

      // Put a fake RS 1 connected to real RS
      fakeRs1 = createFakeReplicationServer(FRS1_ID, fakeRsGid, fakeRsGenId,
        sendInAssured, AssuredMode.SAFE_DATA_MODE, sdLevel, SENDER_RS_SCENARIO);

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
        assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);
        assertNotNull(ackMsg);
        assertFalse(ackMsg.hasTimeout());
        assertFalse(ackMsg.hasReplayError());
        assertFalse(ackMsg.hasWrongStatus());
        assertEquals(ackMsg.getFailedServers().size(), 0);
      }
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeDataManyRealRSs test.
   */
  @DataProvider(name = "testSafeDataManyRealRSsProvider")
  private Object[][] testSafeDataManyRealRSsProvider()
  {
    return new Object[][]
    {
      {1}, {2}, {3}, {4}
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
      int numberOfRealRSs = 3;

      // Create real RS 1, 2, 3
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);
      rs2 = createReplicationServer(RS2_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);
      rs3 = createReplicationServer(RS3_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);

      /*
       * Start DS that will send updates
       */

      // Wait for RSs to connect together
      // Create and connect fake domain 1 to RS 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_DATA_MODE, sdLevel, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // Wait for RSs connections to be finished
      // DS must see expected numbers of RSs
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      waitForStableTopo(fakeRd1, 0, 3);

      /*
       * Send update from DS 1 and check result
       */
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
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
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT, testCase, 0);

      /*******************
       * Start main DS 1 (the one which sends updates)
       */

      // Create and connect DS 1 to RS 1
      // Assured mode: SR
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      /*
       * Send a first assured safe read update
       */
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 1);
      fakeRd1.assertReceivedUpdates(0);

      /*******************
       * Start another fake DS 2 connected to RS
       */

      // Create and connect DS 2 to RS 1
      // Assured mode: SR
      ServerState serverState = fakeRd1.getServerState();
      fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT,
          REPLY_OK_DS_SCENARIO, serverState, true);

      // Wait for connections to be established
      waitForStableTopo(fakeRd1, 1, 1);

      /*
       * Send a second assured safe read update
       */
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      final FakeReplicationDomain fakeRd2 = fakeRDs[2];
      checkDSSentAndAcked(fakeRd1, 2);
      fakeRd1.assertReceivedUpdates(0);
      checkDSReceivedAndAcked(fakeRd2, 1);
      fakeRd2.assertReceivedUpdates(1);

      /*******************
       * Start a fake RS 1 connected to RS
       */

      fakeRs1 = createFakeReplicationServer(FRS1_ID, DEFAULT_GID, RS1_ID,
        DEFAULT_GENID, true, AssuredMode.SAFE_READ_MODE, 1,
        fakeRd1.getServerState(), REPLY_OK_RS_SCENARIO);

      // Wait for connections to be established
      waitForStableTopo(fakeRd1, 1, 2);

      /*
       * Send a third assured safe read update
       */
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      checkDSSentAndAcked(fakeRd1, 3);
      checkDSReceivedAndAcked(fakeRd2, 2);

      // Sanity check
      fakeRd1.assertReceivedUpdates(0);
      fakeRd2.assertReceivedUpdates(2);
      fakeRs1.assertReceivedUpdates(1);

      /*******************
       * Shutdown fake DS 2
       */

      // Shutdown fake DS 2
      fakeRd2.disableService();

      // Wait for disconnection to be finished
      waitForStableTopo(fakeRd1, 0, 2);

      /*
       * Send a fourth assured safe read update
       */
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      checkDSSentAndAcked(fakeRd1, 4);

      // Sanity check
      fakeRd1.assertReceivedUpdates(0);
      fakeRs1.assertReceivedUpdates(2);

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
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time (should be short as RS should have acked)
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 5);
      fakeRd1.assertReceivedUpdates(0);
    } finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeReadOneRSComplexPrecommit test.
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
   * Returns possible combinations of parameters for testSafeReadOneRSComplex test.
   */
  @DataProvider(name = "testSafeReadOneRSComplexProvider")
  private Object[][] testSafeReadOneRSComplexProvider()
  {
    List<List<Object>> objectArrayList = new ArrayList<>();

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

    return toDataProvider(objectArrayList);
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
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT, testCase, 0);

      /*
       * Start main DS 1 (the one which sends updates)
       */
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      /*
       * Start another fake DS 2 connected to RS
       */
      fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      /*
       * Start another fake DS 3 connected to RS
       */
      fakeRDs[3] = createFakeReplicationDomain(FDS3_ID, otherFakeDsGid, RS1_ID,
          otherFakeDsGenId, otherFakeDsGid == DEFAULT_GID ? AssuredMode.SAFE_READ_MODE : null,
          1, LONG_TIMEOUT, otherFakeDsScen);

      /*
       * Start fake RS (RS 1) connected to RS
       */
      fakeRs1 = createFakeReplicationServer(FRS1_ID, DEFAULT_GID, DEFAULT_GENID,
        true, AssuredMode.SAFE_READ_MODE, 1, REPLY_OK_RS_SCENARIO);

      /*
       * Start another fake RS (RS 2) connected to RS
       */
      fakeRs2 = createFakeReplicationServer(FRS2_ID, otherFakeRsGid, otherFakeRsGenId,
        otherFakeRsGid == DEFAULT_GID, AssuredMode.SAFE_READ_MODE, 1, otherFakeRsScen);

      // Wait for connections to be established
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      waitForStableTopo(fakeRd1, 2, 3);

      /*
       * Send an assured safe read update
       */
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Compute some thing that will help determine what to check according to
      // the current test configuration: compute if DS and RS subject to conf
      // change are eligible and expected for safe read assured
      // eligible: the server should receive the ack request
      // expected: the server should send back an ack (with or without error)
      boolean dsIsEligible = areGroupAndGenerationIdOk(otherFakeDsGid, otherFakeDsGenId);
      boolean rsIsEligible = areGroupAndGenerationIdOk(otherFakeRsGid, otherFakeRsGenId);
      boolean dsIsExpected = false;
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
            Assert.fail("No other scenario should be used here");
        }
      }
      if (rsIsEligible)
      {
        switch (otherFakeRsScen)
        {
          case REPLY_OK_RS_SCENARIO:
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
            Assert.fail("No other scenario should be used here");
        }
      }

      if (!shouldSeeTimeout)
      {
        // Call time should have been short
        assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);
      }
      else if (shouldSeeDsRsIdInError) // Virtual DS timeout
      {
        assertBetweenInclusive(sendUpdateTime, MAX_SEND_UPDATE_TIME, LONG_TIMEOUT);
      }
      else // Normal timeout case
      {
        assertBetweenInclusive(sendUpdateTime, SMALL_TIMEOUT, LONG_TIMEOUT);
      }

      // Sleep a while as counters are updated just after sending thread is unblocked
      Thread.sleep(500);

      // Check monitoring values in DS 1
      final SafeReadAssertions srAssertsRD1 = fakeRd1.newSafeReadAssertions().sentUpdates(1);
      if (( (otherFakeDsGid == DEFAULT_GID) && (otherFakeDsGenId == DEFAULT_GENID) && (otherFakeDsScen != REPLY_OK_DS_SCENARIO) )
         || ( (otherFakeRsGid == DEFAULT_GID) && (otherFakeRsGenId == DEFAULT_GENID) && (otherFakeRsScen != REPLY_OK_RS_SCENARIO) ))
      {
        srAssertsRD1.notAcknowledgedUpdates(1);
      }
      else
      {
        srAssertsRD1.acknowledgedUpdates(1);
      }

      srAssertsRD1.timeoutUpdates(shouldSeeTimeout ? 1 : 0)
               .wrongStatusUpdates(shouldSeeWrongStatus ? 1 : 0)
               .replayErrorUpdates(shouldSeeReplayError ? 1 : 0);
      addExpectedErrors(srAssertsRD1, shouldSeeDsIdInError, shouldSeeRsIdInError, shouldSeeDsRsIdInError);
      srAssertsRD1.runAsserts();

      // Check monitoring values in DS 2
      checkDSReceivedAndAcked(fakeRDs[2], 1);

      // Check monitoring values in DS 3
      final SafeReadAssertions srAssertsRD3 = fakeRDs[3].newSafeReadAssertions();
      if (dsIsEligible)
      {
        srAssertsRD3.receivedUpdates(1);
        if (dsIsExpected)
        {
          srAssertsRD3.receivedUpdatesAcked(1);
        }
        else if (shouldSeeReplayError
            && otherFakeDsScen == REPLAY_ERROR_DS_SCENARIO)
        {
          // Replay error for the other DS
          srAssertsRD3.receivedUpdatesNotAcked(1);
        }
      }
      srAssertsRD3.runAsserts();

      // Sanity check
      fakeRDs[1].assertReceivedUpdates(0);
      fakeRDs[2].assertReceivedUpdates(1);
      fakeRDs[3].assertReceivedUpdates(otherFakeDsGenId == DEFAULT_GENID ? 1 : 0);

      fakeRs1.assertReceivedUpdates(1);
      fakeRs2.assertReceivedUpdates(otherFakeRsGenId == DEFAULT_GENID ? 1 : 0);
    } finally
    {
      endTest();
    }
  }

  private void assertBetweenInclusive(long value, int lowerBound, int upperBound)
  {
    assertTrue(lowerBound <= value && value <= upperBound, "Expected <" + value
        + "> to be between <" + lowerBound + "> and <" + upperBound
        + "> inclusive");
  }

  private void addExpectedErrors(SafeReadAssertions srAsserts, boolean dsInError, boolean rsInError, boolean dsRsInError)
  {
    if (dsInError)
    {
      srAsserts.serverNotAcknowledgedUpdates(FDS3_ID, 1);
    }
    if (rsInError)
    {
      srAsserts.serverNotAcknowledgedUpdates(FRS2_ID, 1);
    }
    if (dsRsInError)
    {
      srAsserts.serverNotAcknowledgedUpdates(DS_FRS2_ID, 1);
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
      int numberOfRealRSs = 4;

      // Create real RS 1, 2, 3
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);
      rs2 = createReplicationServer(RS2_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);
      rs3 = createReplicationServer(RS3_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);

      // Create real RS 4 (different GID 2)
      rs4 = createReplicationServer(RS4_ID, OTHER_GID_BIS, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);

      /*
       * Start DS 1 that will send assured updates
       */

      // Wait for RSs to connect together
      // Create and connect fake domain 1 to RS 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      // -> if everybody is connected we are sure a GENID is set in every RSs and
      // we can connect the fake RS with a different GENID
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      waitForStableTopo(fakeRd1, 0, 4);

      /*
       * Start 2 fake RSs
       */

      // Put a fake RS 1 connected to real RS 2 (different GENID)
      fakeRs1 = createFakeReplicationServer(FRS1_ID, DEFAULT_GID, OTHER_GENID,
        false, AssuredMode.SAFE_READ_MODE, 1, TIMEOUT_RS_SCENARIO);

      // Put a fake RS 2 connected to real RS 3 (different GID 2)
      fakeRs2 = createFakeReplicationServer(FRS2_ID, OTHER_GID_BIS, DEFAULT_GENID,
        false, AssuredMode.SAFE_READ_MODE, 1, TIMEOUT_RS_SCENARIO);

      /*
       * Start DSs that will receive and ack the updates from DS 1
       */

      // DS 2 connected to RS 1
      fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      // DS 3 connected to RS 2
      fakeRDs[3] = createFakeReplicationDomain(FDS3_ID, DEFAULT_GID, RS2_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      // DS 4 connected to RS 3
      fakeRDs[4] = createFakeReplicationDomain(FDS4_ID, DEFAULT_GID, RS3_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      // DS 5 connected to RS 3
      fakeRDs[5] = createFakeReplicationDomain(FDS5_ID, DEFAULT_GID, RS3_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      /*
       * Start DSs that will not receive updates from DS 1 as assured because
       * they have different GID
       */

      // DS 6 connected to RS 1
      fakeRDs[6] = createFakeReplicationDomain(FDS6_ID, OTHER_GID, RS1_ID,
          DEFAULT_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 7 connected to RS 2
      fakeRDs[7] = createFakeReplicationDomain(FDS7_ID, OTHER_GID, RS2_ID,
          DEFAULT_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 8 connected to RS 3
      fakeRDs[8] = createFakeReplicationDomain(FDS8_ID, OTHER_GID, RS3_ID,
          DEFAULT_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 9 (GID 2) connected to RS 4
      fakeRDs[9] = createFakeReplicationDomain(FDS9_ID, OTHER_GID_BIS, RS4_ID,
          DEFAULT_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      /*
       * Start DSs that will not receive updates from DS 1 because
       * they have different GENID
       */

      // DS 10 connected to RS 1
      fakeRDs[10] = createFakeReplicationDomain(FDS10_ID, DEFAULT_GID, RS1_ID,
          OTHER_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 11 connected to RS 2
      fakeRDs[11] = createFakeReplicationDomain(FDS11_ID, DEFAULT_GID, RS2_ID,
          OTHER_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 12 connected to RS 3
      fakeRDs[12] = createFakeReplicationDomain(FDS12_ID, DEFAULT_GID, RS3_ID,
          OTHER_GENID, null, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      waitForStableTopo(fakeRd1, 11, 6);

      /*
       * Send update from DS 1 and check result
       */
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 1);

      assertFakeDSReceivedAndAcked(1, asList(2, 3, 4, 5)); // normal DSs
      assertFakeDSReceivedAndAcked(0, asList(6, 7, 8, 9)); // different GID DSs
      assertFakeDSReceivedAndAcked(0, asList(10, 11, 12)); // different GENID DSs

      // Sanity check
      fakeRd1.assertReceivedUpdates(0);

      assertFakeRDNbReceivedUpdates(1, asList(2, 3, 4, 5)); // normal DSs
      assertFakeRDNbReceivedUpdates(1, asList(6, 7, 8, 9)); // different GID DSs
      assertFakeRDNbReceivedUpdates(0, asList(10, 11, 12)); // different GENID DSs

      fakeRs1.assertReceivedAckedUpdates(0, false);
      fakeRs2.assertReceivedAckedUpdates(1, false);

      /*
       * Send a second update from DS 1 and check result
       */
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(1000); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRd1, 2);

      assertFakeDSReceivedAndAcked(2, asList(2, 3, 4, 5)); // normal DSs
      assertFakeDSReceivedAndAcked(0, asList(6, 7, 8, 9)); // different GID DSs
      assertFakeDSReceivedAndAcked(0, asList(10, 11, 12)); // different GENID DSs

      // Sanity check
      fakeRd1.assertReceivedUpdates(0);

      assertFakeRDNbReceivedUpdates(2, asList(2, 3, 4, 5)); // normal DSs
      assertFakeRDNbReceivedUpdates(2, asList(6, 7, 8, 9)); // different GID DSs
      assertFakeRDNbReceivedUpdates(0, asList(10, 11, 12)); // different GENID DSs

      fakeRs1.assertReceivedAckedUpdates(0, false);
      fakeRs2.assertReceivedAckedUpdates(2, false);
    } finally
    {
      endTest();
    }
  }

  private void assertFakeDSReceivedAndAcked(int nPacket, List<Integer> fakeDSIndexes)
  {
    for (int i : fakeDSIndexes)
    {
      checkDSReceivedAndAcked(fakeRDs[i], nPacket);
    }
  }

  private void assertFakeRDNbReceivedUpdates(int expectedNbReceived, List<Integer> fakeDSIndexes)
  {
    for (int i : fakeDSIndexes)
    {
      fakeRDs[i].assertReceivedUpdates(expectedNbReceived);
    }
  }

  /** Helper method for some safe read test methods. */
  private void checkDSReceivedAndAcked(FakeReplicationDomain fakeRd, int nPacket)
  {
    fakeRd.newSafeReadAssertions()
        .receivedUpdates(nPacket)
        .receivedUpdatesAcked(nPacket)
        .runAsserts();
  }

  /** Helper method for some safe read test methods. */
  private void checkDSSentAndAcked(FakeReplicationDomain fakeRd, int nPacket)
  {
    fakeRd.newSafeReadAssertions()
        .sentUpdates(nPacket)
        .acknowledgedUpdates(nPacket)
        .runAsserts();
  }

  private void checkDSNothingReceivedOrSent(final FakeReplicationDomain fakeRd)
  {
    fakeRd.newSafeReadAssertions().runAsserts();
  }

  /**
   * Test that a safe read update does not cross different group id topologies
   * in assured mode.
   * Topology:
   * DS1(GID=1)---RS1(GID=1)---RS2(GID=2)---DS3(GID=2)
   * DS2(GID=1)---/                     \---DS4(GID=2)
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
      int numberOfRealRSs = 2;

      // Create real RS 1, 2
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);
      rs2 = createReplicationServer(RS2_ID, OTHER_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);

      /*
       * Start DSs with GID=DEFAULT_GID, connected to RS1
       */

      // DS 1 connected to RS 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 2 connected to RS 1
      fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      /*
       * Start DSs with GID=OTHER_GID, connected to RS2
       */

      // DS 3 connected to RS 2
      fakeRDs[3] = createFakeReplicationDomain(FDS3_ID, OTHER_GID, RS2_ID,
          DEFAULT_GENID, null, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      // DS 4 connected to RS 3
      fakeRDs[4] = createFakeReplicationDomain(FDS4_ID, OTHER_GID, RS2_ID,
          DEFAULT_GENID, null, 1, LONG_TIMEOUT, REPLY_OK_DS_SCENARIO);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      waitForStableTopo(fakeRDs[1], 3, 2);

      /*
       * Send update from DS 1 and check result
       */
      long startTime = System.currentTimeMillis();
      fakeRDs[1].sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      // Check call time
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      checkDSSentAndAcked(fakeRDs[1], 1);
      checkDSReceivedAndAcked(fakeRDs[2], 1);
      checkDSNothingReceivedOrSent(fakeRDs[3]);
      checkDSNothingReceivedOrSent(fakeRDs[4]);

      fakeRDs[1].assertReceivedUpdates(0);
      fakeRDs[2].assertReceivedUpdates(1);
      fakeRDs[3].assertReceivedUpdates(1);
      fakeRDs[4].assertReceivedUpdates(1);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Returns possible combinations of parameters for testSafeReadTwoRSsProvider test.
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
      int numberOfRealRSs = 2;

      // Create real RS 1, 2
      rs1 = createReplicationServer(RS1_ID, DEFAULT_GID, SMALL_TIMEOUT + 1000, // Be sure DS2 timeout is seen from DS1
        testCase, numberOfRealRSs);
      rs2 = createReplicationServer(RS2_ID, DEFAULT_GID, SMALL_TIMEOUT,
        testCase, numberOfRealRSs);

      /*
       * Start 2 fake DSs
       */

      // DS 1 connected to RS 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 2 connected to RS 2
      fakeRDs[2] = createFakeReplicationDomain(FDS2_ID, fakeDsGid, RS2_ID,
          fakeDsGenId, fakeDsGid == DEFAULT_GID ? AssuredMode.SAFE_READ_MODE : null,
          1, LONG_TIMEOUT, fakeDsScen);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      waitForStableTopo(fakeRd1, 1, 2);

      /*
       * Send update from DS 1 and check result
       */
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;

      boolean fakeDsIsEligible = areGroupAndGenerationIdOk(fakeDsGid, fakeDsGenId);

      // Check call time
      if (fakeDsIsEligible && (fakeDsScen == TIMEOUT_DS_SCENARIO))
      {
        assertBetweenInclusive(sendUpdateTime, SMALL_TIMEOUT, SMALL_TIMEOUT + 1000);
      }
      else
      {
        assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);
      }

      // Check monitoring values (check that ack has been correctly received)
      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked

      final FakeReplicationDomain fakeRd2 = fakeRDs[2];
      if (fakeDsIsEligible)
      {
        switch (fakeDsScen)
        {
          case REPLY_OK_DS_SCENARIO:
            checkDSSentAndAcked(fakeRd1, 1);
            checkDSReceivedAndAcked(fakeRd2, 1);
            break;
          case TIMEOUT_DS_SCENARIO:
            fakeRd1.newSafeReadAssertions()
                .sentUpdates(1)
                .notAcknowledgedUpdates(1)
                .timeoutUpdates(1)
                .serverNotAcknowledgedUpdates(FDS2_ID, 1)
                .runAsserts();

            fakeRd2.newSafeReadAssertions().receivedUpdates(1).runAsserts();
            break;
          case REPLAY_ERROR_DS_SCENARIO:
            fakeRd1.newSafeReadAssertions()
                .sentUpdates(1)
                .notAcknowledgedUpdates(1)
                .replayErrorUpdates(1)
                .serverNotAcknowledgedUpdates(FDS2_ID, 1)
                .runAsserts();

            fakeRd2.newSafeReadAssertions()
                .receivedUpdates(1)
                .receivedUpdatesNotAcked(1)
                .runAsserts();
            break;
          default:
            Assert.fail("Unknown scenario: " + fakeDsScen);
        }
      } else
      {
        checkDSSentAndAcked(fakeRd1, 1);
        checkDSReceivedAndAcked(fakeRd2, 0);
      }

      fakeRd1.assertReceivedUpdates(0);
      fakeRd2.assertReceivedUpdates(fakeDsGenId == DEFAULT_GENID ? 1 : 0);
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
      // Create real RS
      String dir = testName + RS1_ID + testCase + "Db";
      ReplServerFakeConfiguration conf =
          new ReplServerFakeConfiguration(rsPorts[0], dir, replicationDbImplementation, 0, RS1_ID, 0,
              100, new TreeSet<String>(), DEFAULT_GID, SMALL_TIMEOUT, 1);
      rs1 = new ReplicationServer(conf);

      /*
       * Start 2 fake DSs
       */

      // DS 1 connected to RS 1
      fakeRDs[1] = createFakeReplicationDomain(FDS1_ID, DEFAULT_GID, RS1_ID,
          DEFAULT_GENID, AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT, TIMEOUT_DS_SCENARIO);

      // DS 2 connected to RS 1 with low window to easily put it in DEGRADED status
      final DomainFakeCfg config = newDomainConfig(FDS2_ID, DEFAULT_GID, RS1_ID,
          AssuredMode.SAFE_READ_MODE, 1, LONG_TIMEOUT);
      config.setWindowSize(2);
      fakeRDs[2] = createFakeReplicationDomain(config, RS1_ID, DEFAULT_GENID,
          REPLY_OK_DS_SCENARIO, new ServerState(), false);

      // Wait for connections to be finished
      // DS must see expected numbers of DSs/RSs
      final FakeReplicationDomain fakeRd1 = fakeRDs[1];
      waitForStableTopo(fakeRd1, 1, 1);
      DSInfo dsInfo = fakeRd1.getReplicaInfos().get(FDS2_ID);
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
        fakeRd1.sendNewFakeUpdate();
        long sendUpdateTime = System.currentTimeMillis() - startTime;
        assertBetweenInclusive(sendUpdateTime, SMALL_TIMEOUT, LONG_TIMEOUT);
      }

      // Wait for DS2 being degraded
      expectStatusForDS(fakeRd1, ServerStatus.DEGRADED_STATUS, FDS2_ID);

      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      fakeRd1.newSafeReadAssertions()
          .sentUpdates(4)
          .notAcknowledgedUpdates(4)
          .timeoutUpdates(4)
          .serverNotAcknowledgedUpdates(FDS2_ID, 4)
          .runAsserts();

      final FakeReplicationDomain fakeRd2 = fakeRDs[2];
      checkDSNothingReceivedOrSent(fakeRd2);

      fakeRd1.assertReceivedUpdates(0);
      fakeRd2.assertReceivedUpdates(0);

      /*
       * Send an assured update from DS 1 : should be acked as DS2 is degraded
       * and RS should not consider it as eligible for assured
       */
      long startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      long sendUpdateTime = System.currentTimeMillis() - startTime;
      // RS should ack quickly as DS2 degraded and not eligible for assured
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      fakeRd1.newSafeReadAssertions()
          .sentUpdates(5)
          .acknowledgedUpdates(1)
          .notAcknowledgedUpdates(4)
          .timeoutUpdates(4)
          .serverNotAcknowledgedUpdates(FDS2_ID, 4)
          .runAsserts();

      checkDSNothingReceivedOrSent(fakeRd2);

      fakeRd1.assertReceivedUpdates(0);
      fakeRd2.assertReceivedUpdates(0);

      /*
       * Put DS2 in normal status again (start listen service)
       */

      fakeRd2.startListenService();

      // Wait for DS2 being back to normal
      expectStatusForDS(fakeRd1, ServerStatus.NORMAL_STATUS, FDS2_ID);

      // DS2 should also change status so reset its assured monitoring data so no received sr updates
      fakeRd1.newSafeReadAssertions()
          .sentUpdates(5)
          .acknowledgedUpdates(1)
          .notAcknowledgedUpdates(4)
          .timeoutUpdates(4)
          .serverNotAcknowledgedUpdates(FDS2_ID, 4)
          .runAsserts();

      fakeRd2.newSafeReadAssertions()
          .receivedUpdates(4)
          .receivedUpdatesAcked(4)
          .runAsserts();

      fakeRd1.assertReceivedUpdates(0);

      // DS2 should have received the 5 updates (one with not assured)
      fakeRd2.assertReceivedWrongUpdates(5, 1);

      /*
       * Send again an assured update, DS2 should be taken into account for ack
       */
      startTime = System.currentTimeMillis();
      fakeRd1.sendNewFakeUpdate();
      sendUpdateTime = System.currentTimeMillis() - startTime;
      // RS should ack quickly as DS2 degraded and not eligible for assured
      assertThat(sendUpdateTime).isLessThan(MAX_SEND_UPDATE_TIME);

      Thread.sleep(500); // Sleep a while as counters are updated just after sending thread is unblocked
      fakeRd1.newSafeReadAssertions()
          .sentUpdates(6)
          .acknowledgedUpdates(2)
          .notAcknowledgedUpdates(4)
          .timeoutUpdates(4)
          .serverNotAcknowledgedUpdates(FDS2_ID, 4)
          .runAsserts();

      fakeRd2.newSafeReadAssertions()
          .receivedUpdates(5)
          .receivedUpdatesAcked(5)
          .runAsserts();

      fakeRd1.assertReceivedUpdates(0);
      fakeRd2.assertReceivedWrongUpdates(6, 1);
    }
    finally
    {
      endTest();
    }
  }

  private void expectStatusForDS(final ReplicationDomain domain,
      ServerStatus expectedStatus, int dsId) throws InterruptedException
  {
    for (int count = 0; count < 12; count++)
    {
      final DSInfo dsInfo = domain.getReplicaInfos().get(dsId);
      if (dsInfo != null && dsInfo.getStatus() == expectedStatus)
      {
        return;
      }
      Thread.sleep(1000);
    }
    Assert.fail("DS(" + dsId + ") did not have expected status "
        + expectedStatus + " after 12 seconds");
  }
}
