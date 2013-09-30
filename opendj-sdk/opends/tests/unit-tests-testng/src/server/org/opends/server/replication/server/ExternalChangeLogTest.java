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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.*;
import java.net.Socket;
import java.util.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.*;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.ExternalChangelogDomainFakeCfg;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.je.DraftCNDbHandler;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.externalchangelog.ECLSearchOperation;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.Assert;
import org.testng.annotations.*;

import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.controls.PersistentSearchChangeType.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.types.ResultCode.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Tests for the replicationServer code.
 */
@SuppressWarnings("javadoc")
public class ExternalChangeLogTest extends ReplicationTestCase
{

  private static final int SERVER_ID_1 = 1201;
  private static final int SERVER_ID_2 = 1202;

  /** The tracer object for the debug logger */
  private static final DebugTracer TRACER = getTracer();

  /** The replicationServer that will be used in this test. */
  private ReplicationServer replicationServer;

  /** The port of the replicationServer. */
  private int replicationServerPort;

  /** base DN for "o=test" */
  private static DN TEST_ROOT_DN;
  /** base DN for "o=test2" */
  private static DN TEST_ROOT_DN2;

  private static final String TEST_BACKEND_ID2 = "test2";
  private static final String TEST_ROOT_DN_STRING2 = "o=" + TEST_BACKEND_ID2;

  /** The LDAPStatistics object associated with the LDAP connection handler. */
  private LDAPStatistics ldapStatistics;

  private CSN gblCSN;

  private int brokerSessionTimeout = 5000;
  private int maxWindow = 100;

  /**
   * When used in a search operation, it includes all attributes (user and
   * operational)
   */
  private static final Set<String> ALL_ATTRIBUTES = newSet("*", "+");
  private static final List<Control> NO_CONTROL = null;

  /**
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
    TEST_ROOT_DN = DN.decode(TEST_ROOT_DN_STRING);
    TEST_ROOT_DN2 = DN.decode(TEST_ROOT_DN_STRING2);

    // This test suite depends on having the schema available.
    configure();
  }

  /**
   * Utility : configure a replicationServer.
   */
  private void configure() throws Exception
  {
    replicationServerPort = TestCaseUtils.findFreePort();

    ReplServerFakeConfiguration conf1 =
      new ReplServerFakeConfiguration(
          replicationServerPort, "ExternalChangeLogTestDb",
          0, 71, 0, maxWindow, null);

    replicationServer = new ReplicationServer(conf1);
    debugInfo("configure", "ReplicationServer created"+replicationServer);
  }

  /**
   * Launcher.
   */
  @Test(enabled=true)
  public void ECLReplicationServerPreTest() throws Exception
  {
    // No RSDomain created yet => RS only case => ECL is not a supported
    ECLIsNotASupportedSuffix();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerPreTest"})
  public void ECLReplicationServerTest() throws Exception
  {
    // Following test does not create RSDomain (only broker) but want to test
    // ECL .. so let's enable ECl manually
    // Now that we tested that ECl is not available
    ECLWorkflowElement wfe =
        (ECLWorkflowElement) DirectoryServer
        .getWorkflowElement(ECLWorkflowElement.ECL_WORKFLOW_ELEMENT);
    if (wfe != null)
    {
      wfe.getReplicationServer().enableECL();
    }

    // Test all types of ops.
    ECLAllOps(); // Do not clean the db for the next test

    // First and last should be ok whenever a request has been done or not
    // in compat mode.
    ECLCompatTestLimits(1,4,true);
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest1() throws Exception
  {
    // Test with a mix of domains, a mix of DSes
    ECLTwoDomains();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest2() throws Exception
  {
    // Test ECL after changelog trimming
    ECLAfterChangelogTrim();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest3() throws Exception
  {
    // Write changes and read ECL from start
    ECLCompatWriteReadAllOps(1);

    ECLCompatNoControl(1);

    // Write additional changes and read ECL from a provided change number
    ECLCompatWriteReadAllOps(5);
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest4() throws Exception
  {
    ECLIncludeAttributes();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest5() throws Exception
  {
    ChangeTimeHeartbeatTest();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest6() throws Exception
  {
    // Test that ECL Operational, virtual attributes are not visible
    // outside rootDSE. Next test will test access in RootDSE.
    // This one checks in data.
    ECLOperationalAttributesFailTest();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest() throws Exception
  {
    // ***********************************************
    // First set of test are in the cookie mode
    // ***********************************************

    // Test that private backend is excluded from ECL
    ECLOnPrivateBackend();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest1() throws Exception
  {
    // Test remote API (ECL through replication protocol) with empty ECL
    ECLRemoteEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest2() throws Exception
  {
    // Test with empty changelog
    ECLEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest3() throws Exception
  {
    // Test all types of ops.
    ECLAllOps(); // Do not clean the db for the next test

    // Test that ECL Operational, virtual attributes are not visible
    // outside rootDSE. Next test will test access in RootDSE.
    // This one checks in data.
    ECLOperationalAttributesFailTest();

    // First and last should be ok whenever a request has been done or not
    // in compat mode.
    ECLCompatTestLimits(1, 4, true);
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest4() throws Exception
  {
    // Test remote API (ECL through replication protocol) with NON empty ECL
    ECLRemoteNonEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest7() throws Exception
  {
    // Persistent search with changesOnly request
    ECLPsearch(true, false);
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest8() throws Exception
  {
    // Persistent search with init values request
    ECLPsearch(false, false);
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest9() throws Exception
  {
    // Simultaneous psearches
    ECLSimultaneousPsearches();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest10() throws Exception
  {
    // Test eligible count method.
    ECLGetEligibleCountTest();
  }

  // TODO:ECL Test SEARCH abandon and check everything shutdown and cleaned
  // TODO:ECL Test PSEARCH abandon and check everything shutdown and cleaned
  // TODO:ECL Test invalid DN in cookie returns UNWILLING + message
  // TODO:ECL Test the attributes list and values returned in ECL entries
  // TODO:ECL Test search -s base, -s one

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest11() throws Exception
  {
    // Test directly from the java object that the changeTimeHeartbeatState
    // stored are ok.
    ChangeTimeHeartbeatTest();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest12() throws Exception
  {
    // Test the different forms of filter that are parsed in order to
    // optimize the request.
    ECLFilterTest();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest13() throws Exception
  {
    // ***********************************************
    // Second set of test are in the draft compat mode
    // ***********************************************
    // Empty replication changelog
    ECLCompatEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest14() throws Exception
  {
    // Request from an invalid change number
    ECLCompatBadSeqnum();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest15() throws Exception
  {
    // Write 4 changes and read ECL from start
    ECLCompatWriteReadAllOps(1);

    // Write 4 additional changes and read ECL from a provided change number
    int ts = ECLCompatWriteReadAllOps(5);

    // Test request from a provided change number - read 6
    ECLCompatReadFrom(6);

    // Test request from a provided change number interval - read 5-7
    ECLCompatReadFromTo(5,7);

    // Test first and last change number
    ECLCompatTestLimits(1,8, true);

    // Test first and last change number, add a new change, do not
    // search again the ECL, but search for first and last
    ECLCompatTestLimitsAndAdd(1,8, ts);

    // Test DraftCNDb is purged when replication change log is purged
    ECLPurgeDraftCNDbAfterChangelogClear();

    // Test first and last are updated
    ECLCompatTestLimits(0,0, true);

    // Persistent search in changesOnly mode
    ECLPsearch(true, true);
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest16() throws Exception
  {
    // Persistent search in init + changes mode
    ECLPsearch(false, true);

    // Test Filter on replication csn
    // TODO: test with optimization when code done.
    ECLFilterOnReplicationCsn();
  }

  private void ECLIsNotASupportedSuffix() throws Exception
  {
    ECLCompatTestLimits(0,0, false);
  }

  /**
   * Objectives
   *   - Test that everything is ok with no changes
   * Procedure
   *   - Does a SEARCH from 3 different remote ECL session,
   *   - Verify DoneMsg is received on each session.
   */
  private void ECLRemoteEmpty() throws Exception
  {
    String tn = "ECLRemoteEmpty";
    debugInfo(tn, "Starting test\n\n");

    ReplicationBroker[] brokers = new ReplicationBroker[3];

    try
    {
      // Create 3 ECL broker
      brokers[0] = openReplicationSession(
          DN.decode("cn=changelog"), 1111,
          100, replicationServerPort, brokerSessionTimeout, false);
      assertTrue(brokers[0].isConnected());
      brokers[1] = openReplicationSession(
          DN.decode("cn=changelog"), 2222,
          100, replicationServerPort,brokerSessionTimeout, false);
      assertTrue(brokers[1].isConnected());
      brokers[2] = openReplicationSession(
          DN.decode("cn=changelog"), 3333,
          100, replicationServerPort,brokerSessionTimeout, false);
      assertTrue(brokers[2].isConnected());

      assertOnlyDoneMsgReceived(tn, brokers[0]);
      assertOnlyDoneMsgReceived(tn, brokers[1]);
      assertOnlyDoneMsgReceived(tn, brokers[2]);
      debugInfo(tn, "Ending test successfully\n\n");
    }
    finally
    {
      stop(brokers);
    }
  }

  private void assertOnlyDoneMsgReceived(String tn, ReplicationBroker server)
      throws Exception
  {
    ReplicationMsg msg;
    int msgc = 0;
    do
    {
      msg = server.receive();
      msgc++;
    }
    while (!(msg instanceof DoneMsg));
    final String className = msg.getClass().getCanonicalName();
    assertEquals(msgc, 1, "Ending " + tn + " with incorrect message number :" + className);
  }

  /**
   * Objectives
   *   - Test that everything is ok with changes on 2 suffixes
   * Procedure
   *   - From 1 remote ECL session,
   *   - Test simple update to be received from 2 suffixes
   */
  private void ECLRemoteNonEmpty() throws Exception
  {
    String tn = "ECLRemoteNonEmpty";
    debugInfo(tn, "Starting test\n\n");

    ReplicationBroker server01 = null;
    ReplicationBroker server02 = null;
    ReplicationBroker serverECL = null;

    try
    {
      // create 2 regular brokers on the 2 suffixes
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      server02 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, true, EMPTY_DN_GENID);

      // create and publish 1 change on each suffix
      long time = TimeThread.getTime();
      int ts = 1;
      CSN csn1 = new CSN(time, ts++, SERVER_ID_1);
      DeleteMsg delMsg1 = newDeleteMsg("o=" + tn + "1," + TEST_ROOT_DN_STRING, csn1, "ECLBasicMsg1uid");
      server01.publish(delMsg1);
      debugInfo(tn, "publishes:" + delMsg1);

      CSN csn2 = new CSN(time, ts++, SERVER_ID_2);
      DeleteMsg delMsg2 = newDeleteMsg("o=" + tn + "2," + TEST_ROOT_DN_STRING2, csn2, "ECLBasicMsg2uid");
      server02.publish(delMsg2);
      debugInfo(tn, "publishes:" + delMsg2);

      // wait for the server to take these changes into account
      sleep(500);

      // open ECL broker
      serverECL = openReplicationSession(
          DN.decode("cn=changelog"), 10,
          100, replicationServerPort, brokerSessionTimeout, false);
      assertTrue(serverECL.isConnected());

      // receive change 1 from suffix 1
      ReplicationMsg msg;
      msg = serverECL.receive();
      ECLUpdateMsg eclu = (ECLUpdateMsg)msg;
      UpdateMsg u = eclu.getUpdateMsg();
      debugInfo(tn, "RESULT:" + u.getCSN() + " " + eclu.getCookie());
      assertTrue(u.getCSN().equals(csn1), "RESULT:" + u.getCSN());
      assertTrue(eclu.getCookie().equalsTo(new MultiDomainServerState(
          "o=test:"+delMsg1.getCSN()+";o=test2:;")));

      // receive change 2 from suffix 2
      msg = serverECL.receive();
      eclu = (ECLUpdateMsg)msg;
      u = eclu.getUpdateMsg();
      debugInfo(tn, "RESULT:" + u.getCSN());
      assertTrue(u.getCSN().equals(csn2), "RESULT:" + u.getCSN());
      assertTrue(eclu.getCookie().equalsTo(new MultiDomainServerState(
          "o=test2:"+delMsg2.getCSN()+";"+
          "o=test:"+delMsg1.getCSN()+";")));

      // receive Done
      msg = serverECL.receive();
      debugInfo(tn, "RESULT:" + msg);
      assertTrue(msg instanceof DoneMsg, "RESULT:" + msg);

      debugInfo(tn, "Ending test successfully");
    }
    finally
    {
      stop(serverECL, server01, server02);
    }
  }

  /**
   * From embedded ECL (no remote session)
   * With empty RS, simple search should return only root entry.
   */
  private void ECLEmpty() throws Exception
  {
    String tn = "ECLEmpty";
    debugInfo(tn, "Starting test\n\n");

    InternalSearchOperation op2 =
        searchOnChangelog("(objectclass=*)", new LinkedHashSet<String>(0),
            createControls(""));
    waitOpResult(op2, ResultCode.SUCCESS);

    // root entry returned
    assertEquals(op2.getEntriesSent(), 1);
    debugInfo(tn, "Ending test successfully");
  }

  /**
   * Build a list of controls including the cookie provided.
   * @param cookie The provided cookie.
   * @return The built list of controls.
   */
  private List<Control> createControls(String cookie) throws DirectoryException
  {
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    final List<Control> controls = new ArrayList<Control>(1);
    controls.add(new ExternalChangelogRequestControl(true, state));
    return controls;
  }

  /**
   * Utility - creates an LDIFWriter to dump result entries.
   */
  private static LDIFWriter getLDIFWriter() throws Exception
  {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LDIFExportConfig exportConfig = new LDIFExportConfig(stream);
    return new LDIFWriter(exportConfig);
  }

  /** Add an entry in the database */
  private void addEntry(Entry entry) throws Exception
  {
    AddOperation addOp = new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
        .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    waitOpResult(addOp, ResultCode.SUCCESS);
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  private void ECLOnPrivateBackend() throws Exception
  {
    String tn = "ECLOnPrivateBackend";
    debugInfo(tn, "Starting test");

    ReplicationBroker server01 = null;
    LDAPReplicationDomain domain2 = null;
    Backend backend2 = null;

    // Use different values than other tests to avoid test interactions in concurrent test runs
    final String backendId2 = tn + 2;
    final DN baseDN2 = DN.decode("o=" + backendId2);
    try
    {
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      // create and publish 1 change on each suffix
      long time = TimeThread.getTime();

      CSN csn1 = new CSN(time, 1, SERVER_ID_1);
      DeleteMsg delMsg1 = newDeleteMsg("o=" + tn + "1," + TEST_ROOT_DN_STRING, csn1, "ECLBasicMsg1uid");
      server01.publish(delMsg1);
      debugInfo(tn, "publishes:" + delMsg1);

      // Configure replication on this backend
      // Add the root entry in the backend
      backend2 = initializeTestBackend(false, backendId2);
      backend2.setPrivateBackend(true);
      SortedSet<String> replServers = newSet("localhost:" + replicationServerPort);

      DomainFakeCfg domainConf = new DomainFakeCfg(baseDN2, 1602, replServers);
      domain2 = startNewDomain(domainConf, null,null);

      sleep(1000);
      addEntry(createEntry(baseDN2));
      sleep(2000);

      // Search on ECL from start on all suffixes
      String cookie = "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*)", cookie, tn, SUCCESS);

      // Expect root entry returned
      List<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertThat(entries).hasSize(2);
      debugAndWriteEntries(null, entries, tn);

      ExternalChangelogDomainCfg eclCfg = new ExternalChangelogDomainFakeCfg(false, null, null);
      domainConf.setExternalChangelogDomain(eclCfg);
      domain2.applyConfigurationChange(domainConf);

      searchOp = searchOnCookieChangelog("(targetDN=*)", cookie, tn, SUCCESS);

      // Expect only entry from o=test returned
      entries = searchOp.getSearchEntries();
      assertThat(entries).hasSize(1);
      debugAndWriteEntries(null, entries, tn);

      // Test lastExternalChangelogCookie attribute of the ECL
      // (does only refer to non private backend)
      MultiDomainServerState expectedLastCookie =
          new MultiDomainServerState("o=test:" + csn1 + ";");

      String lastCookie = readLastCookie();
      assertTrue(expectedLastCookie.equalsTo(new MultiDomainServerState(lastCookie)),
          " Expected last cookie attribute value:" + expectedLastCookie +
          " Read from server: " + lastCookie + " are equal :");
    }
    finally
    {
      remove(domain2);
      removeTestBackend(backend2);
      stop(server01);
    }
    debugInfo(tn, "Ending test successfully");
  }

  /**
   * From embedded ECL Search ECL with 4 messages on 2 suffixes from 2 brokers
   */
  private void ECLTwoDomains() throws Exception
  {
    String tn = "ECLTwoDomains";
    debugInfo(tn, "Starting test");

    ReplicationBroker s1test = null;
    ReplicationBroker s1test2 = null;
    ReplicationBroker s2test = null;
    ReplicationBroker s2test2 = null;

    Backend backend2 = null;
    try
    {
      backend2 = initializeTestBackend(true, TEST_BACKEND_ID2);

      LDIFWriter ldifWriter = getLDIFWriter();

      s1test = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      s2test2 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, true, EMPTY_DN_GENID);
      sleep(500);

      // Produce updates
      long time = TimeThread.getTime();
      int ts = 1;
      CSN csn = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn, tn, 1);

      csn = new CSN(time++, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest2(s2test2, csn, tn, 2);

      CSN csn3 = new CSN(time++, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest2(s2test2, csn3, tn, 3);

      csn = new CSN(time++, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn, tn, 4);
      sleep(1500);

      // Changes are :
      //               s1          s2
      // o=test       msg1/msg4
      // o=test2                 msg2/msg2

      // search on 'cn=changelog'
      String cookie = "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*" + tn + "*)", cookie, tn, SUCCESS);

      cookie="";
      List<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertThat(entries).hasSize(4);
      debugAndWriteEntries(ldifWriter, entries, tn);
      int i = 0;
      for (SearchResultEntry entry : entries)
      {
        if (i++ == 2)
        {
          // Store the cookie returned with the 3rd ECL entry returned to use
          // it in the test below.
          cookie = entry.getAttribute("changelogcookie").get(0).iterator().next().toString();
        }
      }

      // Now start from last cookie and expect to get ONLY the 4th change
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*)", cookie, tn, SUCCESS);

      // We expect the 4th change
      cookie = "";
      cookie = getCookie(searchOp.getSearchEntries(), 1, tn, ldifWriter, cookie);

      // Now publishes a new change and search from the previous cookie
      CSN csn5 = new CSN(time++, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn5, tn, 5);
      sleep(500);

      // Changes are :
      //               s1         s2
      // o=test       msg1,msg5   msg4
      // o=test2      msg3        msg2

      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*)", cookie, tn, SUCCESS);
      cookie = getCookie(searchOp.getSearchEntries(), 1, tn, ldifWriter, cookie);

      cookie="";
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", cookie, tn, SUCCESS);
      // we expect msg1 + msg4 + msg5
      cookie = getCookie(searchOp.getSearchEntries(), 3, tn, ldifWriter, cookie);

      // Test startState ("first cookie") of the ECL
      // --
      s1test2 = openReplicationSession(TEST_ROOT_DN2,  1203,
          100, replicationServerPort, brokerSessionTimeout, true, EMPTY_DN_GENID);

      s2test = openReplicationSession(TEST_ROOT_DN,  1204,
          100, replicationServerPort, brokerSessionTimeout, true);
      sleep(500);

      time = TimeThread.getTime();
      csn = new CSN(time++, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn, tn, 6);

      csn = new CSN(time++, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn, tn, 7);

      CSN csn8 = new CSN(time++, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn8, tn, 8);

      CSN csn9 = new CSN(time++, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn9, tn, 9);
      sleep(500);

      ServerState startState = getReplicationDomainStartState(TEST_ROOT_DN);
      assertEquals(startState.getCSN(s1test.getServerId()).getSeqnum(), 1);
      assertTrue(startState.getCSN(s2test.getServerId()) != null);
      assertEquals(startState.getCSN(s2test.getServerId()).getSeqnum(), 7);

      startState = getReplicationDomainStartState(TEST_ROOT_DN2);
      assertEquals(startState.getCSN(s2test2.getServerId()).getSeqnum(), 2);
      assertEquals(startState.getCSN(s1test2.getServerId()).getSeqnum(), 6);

      // Test lastExternalChangelogCookie attribute of the ECL
      MultiDomainServerState expectedLastCookie =
          new MultiDomainServerState("o=test:" + csn5 + " " + csn9
              + ";o=test2:" + csn3 + " " + csn8 + ";");

      String lastCookie = readLastCookie();

      assertTrue(expectedLastCookie.equalsTo(new MultiDomainServerState(lastCookie)),
          " Expected last cookie attribute value:" + expectedLastCookie +
          " Read from server: " + lastCookie + " are equal :");

      // Test invalid cookie
      cookie += ";o=test6:";
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", cookie, tn,
              PROTOCOL_ERROR);
      assertEquals(searchOp.getSearchEntries().size(), 0);
      assertTrue(searchOp.getErrorMessage().toString().equals(
          ERR_INVALID_COOKIE_SYNTAX.get().toString()),
          searchOp.getErrorMessage().toString());

      // Test unknown domain in provided cookie
      // This case seems to be very hard to obtain in the real life
      // (how to remove a domain from a RS topology ?)
      // let's do a very quick test here.
      String newCookie = lastCookie + "o=test6:";
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", newCookie,
              tn, UNWILLING_TO_PERFORM);
      assertEquals(searchOp.getSearchEntries().size(), 0);

      // Test missing domain in provided cookie
      newCookie = lastCookie.substring(lastCookie.indexOf(';')+1);
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", newCookie,
              tn, UNWILLING_TO_PERFORM);
      assertEquals(searchOp.getSearchEntries().size(), 0);
      String expectedError = ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE
          .get("o=test:;","<"+ newCookie + "o=test:;>").toString();
      assertTrue(searchOp.getErrorMessage().toString().equalsIgnoreCase(expectedError),
          "Expected: " + expectedError + "Server output:" +
          searchOp.getErrorMessage());
    }
    finally
    {
      removeTestBackend(backend2);
      stop(s1test2, s2test, s1test, s2test2);
    }
    debugInfo(tn, "Ending test successfully");
  }

  private ServerState getReplicationDomainStartState(DN baseDN)
  {
    return replicationServer.getReplicationServerDomain(baseDN).getStartState();
  }

  private String getCookie(List<SearchResultEntry> entries,
      int expectedNbEntries, String tn, LDIFWriter ldifWriter, String cookie)
      throws Exception
  {
    assertThat(entries).hasSize(expectedNbEntries);
    debugAndWriteEntries(ldifWriter, entries, tn);

    for (SearchResultEntry entry : entries)
    {
      try
      {
        // Store the cookie returned with the 4rd ECL entry returned to use
        // it in the test below.
        List<Attribute> l = entry.getAttribute("changelogcookie");
        cookie = l.get(0).iterator().next().toString();
      }
      catch (NullPointerException e)
      {
      }
    }
    return cookie;
  }

  private void publishDeleteMsgInOTest(ReplicationBroker broker, CSN csn,
      String tn, int i) throws DirectoryException
  {
    publishDeleteMsg(broker, csn, tn, i, TEST_ROOT_DN_STRING);
  }

  private void publishDeleteMsgInOTest2(ReplicationBroker broker, CSN csn,
      String tn, int i) throws DirectoryException
  {
    publishDeleteMsg(broker, csn, tn, i, TEST_ROOT_DN_STRING2);
  }

  private void publishDeleteMsg(ReplicationBroker broker, CSN csn, String tn,
      int i, String baseDn) throws DirectoryException
  {
    String dn = "uid=" + tn + i + "," + baseDn;
    DeleteMsg delMsg = newDeleteMsg(dn, csn, tn + "uuid" + i);
    broker.publish(delMsg);
    debugInfo(tn, " publishes " + delMsg.getCSN());
  }

  private DeleteMsg newDeleteMsg(String dn, CSN csn, String entryUUID) throws DirectoryException
  {
    return new DeleteMsg(DN.decode(dn), csn, entryUUID);
  }

  private InternalSearchOperation searchOnCookieChangelog(String filterString,
      String cookie, String testName, ResultCode expectedResultCode)
      throws Exception
  {
    debugInfo(testName, "Search with cookie=[" + cookie + "] filter=["
        + filterString + "]");
    InternalSearchOperation searchOp =
        searchOnChangelog(filterString, ALL_ATTRIBUTES, createControls(cookie));
    waitOpResult(searchOp, expectedResultCode);
    return searchOp;
  }

  private InternalSearchOperation searchOnChangelog(String filterString,
      String testName, ResultCode expectedResultCode) throws Exception
  {
    debugInfo(testName, " Search: " + filterString);
    InternalSearchOperation searchOp =
        searchOnChangelog(filterString, ALL_ATTRIBUTES, NO_CONTROL);
    waitOpResult(searchOp, expectedResultCode);
    return searchOp;
  }

  private InternalSearchOperation searchOnChangelog(String filterString,
      Set<String> attributes, List<Control> controls) throws Exception
  {
    return connection.processSearch(
        "cn=changelog",
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0, // Size limit
        0, // Time limit
        false, // Types only
        filterString,
        attributes,
        controls,
        null);
  }

  /** Test ECL content after replication changelogDB trimming */
  private void ECLAfterChangelogTrim() throws Exception
  {
    String tn = "ECLAfterChangelogTrim";
    debugInfo(tn, "Starting test");

    ReplicationBroker server01 = null;

    try
    {
      // ---
      // 1. Populate the changelog and read the cookie

      // Creates broker on o=test
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      final CSN[] csns = generateCSNs(4, SERVER_ID_1);
      publishDeleteMsgInOTest(server01, csns[0], tn, 1);

      Thread.sleep(1000);

      // Test that last cookie has been updated
      String cookieNotEmpty = readLastCookie();
      debugInfo(tn, "Store cookie not empty=\"" + cookieNotEmpty + "\"");

      publishDeleteMsgInOTest(server01, csns[1], tn, 2);
      publishDeleteMsgInOTest(server01, csns[2], tn, 3);

      // Sleep longer than this delay - the changelog will be trimmed
      Thread.sleep(1000);

      // ---
      // 2. Now set up a very short purge delay on the replication changelogs
      // so that this test can play with a trimmed changelog.
      replicationServer.getChangelogDB().setPurgeDelay(1);

      // Sleep longer than this delay - so that the changelog is trimmed
      Thread.sleep(1000);
      LDIFWriter ldifWriter = getLDIFWriter();

      // ---
      // 3. Assert that a request with an empty cookie returns nothing
      String cookie= "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*)", cookie, tn, SUCCESS);

      List<SearchResultEntry> entries = searchOp.getSearchEntries();
      // Assert ECL is empty since replication changelog has been trimmed
      assertThat(entries).hasSize(0);
      debugAndWriteEntries(ldifWriter, entries, tn);

      // 4. Assert that a request with the current last cookie returns nothing
      cookie = readLastCookie();
      debugInfo(tn, "2. Search with last cookie=" + cookie + "\"");
      searchOp = searchOnCookieChangelog("(targetDN=*)", cookie, tn, SUCCESS);

      entries = searchOp.getSearchEntries();
      // Assert ECL is empty since replication changelog has been trimmed
      assertThat(entries).hasSize(0);
      debugAndWriteEntries(ldifWriter, entries, tn);


      // ---
      // 5. Assert that a request with an "old" cookie - one that refers to
      //    changes that have been removed by the replication changelog trimming
      //    returns the appropriate error.
      publishDeleteMsgInOTest(server01, csns[3], tn, 1);

      debugInfo(tn, "d1 trimdate" + getReplicationDomainStartState(TEST_ROOT_DN));
      debugInfo(tn, "d2 trimdate" + getReplicationDomainStartState(TEST_ROOT_DN2));
      searchOp = searchOnCookieChangelog("(targetDN=*)", cookieNotEmpty, tn, UNWILLING_TO_PERFORM);
      assertEquals(searchOp.getSearchEntries().size(), 0);
      assertTrue(searchOp.getErrorMessage().toString().startsWith(
          ERR_RESYNC_REQUIRED_TOO_OLD_DOMAIN_IN_PROVIDED_COOKIE.get(TEST_ROOT_DN_STRING).toString()),
          searchOp.getErrorMessage().toString());
    }
    finally
    {
      stop(server01);
      // And reset changelog purge delay for the other tests.
      replicationServer.getChangelogDB().setPurgeDelay(15 * 1000);
    }
    debugInfo(tn, "Ending test successfully");
  }

  private void debugAndWriteEntries(LDIFWriter ldifWriter,
      List<SearchResultEntry> entries, String tn) throws Exception
  {
    if (entries != null)
    {
      for (SearchResultEntry entry : entries)
      {
        // Can use entry.toSingleLineString()
        debugInfo(tn, " RESULT entry returned:" + entry.toLDIFString());
        if (ldifWriter != null)
        {
          ldifWriter.writeEntry(entry);
        }
      }
    }
  }

  private String readLastCookie() throws Exception
  {
    String cookie = "";
    LDIFWriter ldifWriter = getLDIFWriter();

    Set<String> lastcookieattribute = newSet("lastExternalChangelogCookie");

    InternalSearchOperation searchOp = searchOnRootDSE(lastcookieattribute);
    List<SearchResultEntry> entries = searchOp.getSearchEntries();
    if (entries != null)
    {
      for (SearchResultEntry resultEntry : entries)
      {
        ldifWriter.writeEntry(resultEntry);
        try
        {
          List<Attribute> l = resultEntry.getAttribute("lastexternalchangelogcookie");
          cookie = l.get(0).iterator().next().toString();
        }
        catch (NullPointerException e)
        {
        }
      }
    }
    return cookie;
  }

  /** simple update to be received*/
  private void ECLAllOps() throws Exception
  {
    String tn = "ECLAllOps";
    debugInfo(tn, "Starting test\n\n");
    ReplicationBroker server01 = null;
    ReplicationBroker server02 = null;
    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // Creates broker on o=test
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      // Creates broker on o=test2
      server02 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1111-1111-1111-111111111111";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";

      CSN[] csns = generateCSNs(4, SERVER_ID_1);

      // Publish DEL
      int csnCounter = 0;
      publishDeleteMsgInOTest(server01, csns[csnCounter], tn, csnCounter + 1);

      // Publish ADD
      csnCounter++;
      String lentry = "dn: uid="+tn+"2," + TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n" + "objectClass: domain\n"
          + "entryUUID: "+user1entryUUID+"\n";
      Entry entry = TestCaseUtils.entryFromLdifString(lentry);
      AddMsg addMsg = new AddMsg(
          csns[csnCounter],
          DN.decode("uid="+tn+"2," + TEST_ROOT_DN_STRING),
          user1entryUUID,
          baseUUID,
          entry.getObjectClassAttribute(),
          entry.getAttributes(),
          new ArrayList<Attribute>());
      server01.publish(addMsg);
      debugInfo(tn, " publishes " + addMsg.getCSN());

      // Publish MOD
      csnCounter++;
      DN baseDN = DN.decode("uid=" + tn + "3," + TEST_ROOT_DN_STRING);
      List<Modification> mods = createMods("description", "new value");
      ModifyMsg modMsg = new ModifyMsg(csns[csnCounter], baseDN, mods, tn + "uuid3");
      server01.publish(modMsg);
      debugInfo(tn, " publishes " + modMsg.getCSN());

      // Publish modDN
      csnCounter++;
      final DN newSuperior = TEST_ROOT_DN2;
      ModifyDNOperation op = new ModifyDNOperationBasis(connection, 1, 1, null,
          DN.decode("uid="+tn+"4," + TEST_ROOT_DN_STRING), // entryDN
          RDN.decode("uid="+tn+"new4"), // new rdn
          true,  // deleteoldrdn
          newSuperior);
      op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(csns[csnCounter],
          tn + "uuid4", "newparentId"));
      LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
      ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
      server01.publish(modDNMsg);
      debugInfo(tn, " publishes " + modDNMsg.getCSN());
      sleep(1000);

      String cookie= "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetdn=*" + tn + "*,o=test)", cookie, tn, SUCCESS);

      // test 4 entries returned
      final String[] cookies = new String[4];
      for (int j = 0; j < cookies.length; j++)
      {
        cookies[j] = "o=test:" + csns[j] + ";";
      }

      assertEquals(searchOp.getSearchEntries().size(), 4);
      List<SearchResultEntry> entries = searchOp.getSearchEntries();
      debugAndWriteEntries(ldifWriter, entries, tn);
      int i=0;
      for (SearchResultEntry resultEntry : entries)
      {
        i++;
        checkDn(csns[i - 1], resultEntry);
        checkValue(resultEntry, "targetdn", "uid=" + tn + i + "," + TEST_ROOT_DN_STRING);
        checkValue(resultEntry, "replicationcsn", csns[i - 1].toString());
        checkValue(resultEntry, "replicaidentifier", String.valueOf(SERVER_ID_1));
        checkValue(resultEntry, "changelogcookie", cookies[i - 1]);
        checkValue(resultEntry, "changenumber", "0");

        if (i==1)
        {
          checkValue(resultEntry, "changetype", "delete");
          checkValue(resultEntry,"targetentryuuid",tn+"uuid1");
        } else if (i==2)
        {
          checkValue(resultEntry, "changetype", "add");
          String expectedValue1 = "objectClass: domain\nobjectClass: top\n"
              + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
          String expectedValue2 = "entryUUID: 11111111-1111-1111-1111-111111111111\n"
              + "objectClass: domain\nobjectClass: top\n";
          checkPossibleValues(resultEntry,"changes",expectedValue1, expectedValue2);
          checkValue(resultEntry,"targetentryuuid",user1entryUUID);
        } else if (i==3)
        {
          // check the MOD entry has the right content
          checkValue(resultEntry, "changetype", "modify");
          String expectedValue =
              "replace: description\n" + "description: new value\n-\n";
          checkValue(resultEntry,"changes",expectedValue);
          checkValue(resultEntry,"targetentryuuid",tn+"uuid3");
        } else if (i==4)
        {
          checkValue(resultEntry,"changetype","modrdn");
          checkValue(resultEntry,"targetentryuuid",tn+"uuid4");
          checkValue(resultEntry,"newrdn","uid=ECLAllOpsnew4");
          if (newSuperior != null)
          {
            checkValue(resultEntry, "newsuperior", TEST_ROOT_DN_STRING2);
          }
          checkValue(resultEntry,"deleteoldrdn","true");
        }
      }

      // Test the response control with ldapsearch tool
      String result = ldapsearch("cn=changelog");
      debugInfo(tn, "Entries:" + result);

      List<String> ctrlList = getControls(result);
      assertThat(ctrlList).containsExactly(cookies);

    }
    finally {
      stop(server01, server02);
    }
    debugInfo(tn, "Ending test with success");
  }

  private CSN[] generateCSNs(int nb, int serverId)
  {
    long startTime = TimeThread.getTime();

    CSN[] csns = new CSN[nb];
    for (int i = 0; i < nb; i++)
    {
      // seqNum must be greater than 0, so start at 1
      csns[i] = new CSN(startTime + i, i + 1, serverId);
    }
    return csns;
  }

  private void checkDn(CSN csn, SearchResultEntry resultEntry)
  {
    String actualDN = resultEntry.getDN().toNormalizedString();
    String expectedDN =
        "replicationcsn=" + csn + "," + TEST_ROOT_DN_STRING + ",cn=changelog";
    assertThat(actualDN).isEqualToIgnoringCase(expectedDN);
  }

  private List<String> getControls(String resultString)
  {
    StringReader r=new StringReader(resultString);
    BufferedReader br=new BufferedReader(r);
    List<String> ctrlList = new ArrayList<String>();
    try {
      while(true) {
        String s = br.readLine();
        if(s == null)
        {
          break;
        }
        if(!s.startsWith("#"))
        {
          continue;
        }
        String[] a=s.split(": ");
        if(a.length != 2)
        {
          break;
        }
        ctrlList.add(a[1]);
      }
    } catch (IOException e) {
      Assert.assertEquals(0, 1,  e.getMessage());
    }
    return ctrlList;
  }

  private static final ByteArrayOutputStream oStream = new ByteArrayOutputStream();
  private static final ByteArrayOutputStream eStream = new ByteArrayOutputStream();

  private String ldapsearch(String baseDN)
  {
    // test search as directory manager returns content
    String[] args3 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", baseDN,
      "-s", "sub",
      "--control", "1.3.6.1.4.1.26027.1.5.4:false:;",
      "(objectclass=*)"
    };

    oStream.reset();
    eStream.reset();
    int retVal = LDAPSearch.mainSearch(args3, false, oStream, eStream);
    Assert.assertEquals(0, retVal, "Returned error: " + eStream);
    return oStream.toString();
  }

  private static void checkValue(Entry entry, String attrName, String expectedValue)
  {
    String encodedValue = getAttributeValue(entry, attrName);
    assertTrue(encodedValue.equalsIgnoreCase(expectedValue), "In entry "
        + entry + " attr <" + attrName + "> equals " + encodedValue
        + " instead of expected value " + expectedValue);
  }


  private static String getAttributeValueOrNull(Entry entry, String attrName)
  {
    try
    {
      return getAttributeValue(entry, attrName);
    }
    catch(Exception e)
    {
    }
    return null;
  }

  private static String getAttributeValue(Entry entry, String attrName)
  {
    List<Attribute> attrs = entry.getAttribute(attrName);
    Attribute a = attrs.iterator().next();
    AttributeValue av = a.iterator().next();
    return av.toString();
  }

  private static void checkPossibleValues(Entry entry, String attrName,
      String expectedValue1, String expectedValue2)
  {
    String encodedValue = getAttributeValue(entry, attrName);
    assertTrue(
        (encodedValue.equalsIgnoreCase(expectedValue1)
            || encodedValue.equalsIgnoreCase(expectedValue2)),
        "In entry " + entry + " attr <" + attrName + "> equals " + encodedValue
        + " instead of one of the expected values " + expectedValue1 + " or "
        + expectedValue2);
  }

  private static void checkValues(Entry entry, String attrName,
      Set<String> expectedValues)
  {
    for (Attribute a : entry.getAttribute(attrName))
    {
      for (AttributeValue av : a)
      {
        String encodedValue = av.toString();
        assertTrue(expectedValues.contains(encodedValue), "In entry " + entry
            + " attr <" + attrName + "> equals " + av
            + " instead of one of the expected values " + expectedValues);
      }
    }
  }

  /**
   * Test persistent search
   */
  private void ECLPsearch(boolean changesOnly, boolean compatMode) throws Exception
  {
    String tn = "ECLPsearch_" + changesOnly + "_" + compatMode;
    debugInfo(tn, "Starting test \n\n");
    Socket s;

    // create stats
    for (ConnectionHandler<?> ch : DirectoryServer.getConnectionHandlers())
    {
      if (ch instanceof LDAPConnectionHandler)
      {
        LDAPConnectionHandler lch = (LDAPConnectionHandler) ch;
        if (!lch.useSSL())
        {
          ldapStatistics = lch.getStatTracker();
        }
      }
    }
    assertNotNull(ldapStatistics);

    {
      // Create broker on suffix
      ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      CSN[] csns = generateCSNs(2, SERVER_ID_1);

      // Produce update on this suffix
      DeleteMsg delMsg =
          newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csns[0],
            "11111111-1112-1113-1114-111111111114");
      debugInfo(tn, " publishing " + delMsg.getCSN());
      server01.publish(delMsg);
      sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      String cookie = "";
      List<Control> controls = createControls(cookie);
      if (compatMode)
      {
        cookie = null;
        controls = new ArrayList<Control>(0);
      }

      // Creates psearch control
      Set<PersistentSearchChangeType> changeTypes =
          EnumSet.of(ADD, DELETE, MODIFY, MODIFY_DN);
      controls.add(new PersistentSearchControl(changeTypes, changesOnly, true));

      SearchRequestProtocolOp searchRequest =
          createSearchRequest("(targetDN=*" + tn + "*,o=test)", null);

      // Connects and bind
      debugInfo(tn, "Search with cookie=" + cookie + "\"");
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
      LDAPWriter w = new LDAPWriter(s);
      s.setSoTimeout(5000);
      bindAsManager(w, r);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));

      InvocationCounterPlugin.resetAllCounters();

      long searchEntries;
      long searchReferences = ldapStatistics.getSearchResultReferences();
      long searchesDone     = ldapStatistics.getSearchResultsDone();

      debugInfo(tn, "Search Persistent filter=(targetDN=*"+tn+"*,o=test)");
      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeMessage(message);
      sleep(500);

      SearchResultDoneProtocolOp searchResultDone;

      if (!changesOnly)
      {
        // Wait for change 1
        debugInfo(tn, "Waiting for init search expected to return change 1");
        searchEntries = 0;
        {
          while (searchEntries < 1 && (message = r.readMessage()) != null)
          {
            debugInfo(tn, "Init search Result=" +
                message.getProtocolOpType() + message + " " + searchEntries);
            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              SearchResultEntryProtocolOp searchResultEntry =
                  message.getSearchResultEntryProtocolOp();
              searchEntries++;
              // FIXME:ECL Double check 1 is really the valid value here.
              checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                  (compatMode?"1":"0"));
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              searchReferences++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(),
                  ResultCode.SUCCESS.getIntValue(),
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        debugInfo(tn, "INIT search done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);
      }

      // Produces change 2
      final CSN csn = csns[1];
      String expectedDn = "uid=" + tn + "2," +  TEST_ROOT_DN_STRING;
      delMsg = newDeleteMsg(expectedDn, csn,
         "11111111-1112-1113-1114-111111111115");
      debugInfo(tn, " publishing " + delMsg.getCSN());
      server01.publish(delMsg);
      this.gblCSN = csn;
      sleep(1000);

      debugInfo(tn, delMsg.getCSN() +
      " published , psearch will now wait for new entries");

      // wait for the 1 new entry
      searchEntries = 0;
      SearchResultEntryProtocolOp searchResultEntry = null;
      while (searchEntries < 1 && (message = r.readMessage()) != null)
      {
        debugInfo(tn, "psearch search  Result=" +
            message.getProtocolOpType() + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          searchReferences++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(),
              ResultCode.SUCCESS.getIntValue(),
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);

      // Check we received change 2
      for (LDAPAttribute a : searchResultEntry.getAttributes())
      {
        if ("targetDN".equalsIgnoreCase(a.getAttributeType()))
        {
          for (ByteString av : a.getValues())
          {
            assertTrue(av.toString().equalsIgnoreCase(expectedDn),
                "Entry returned by psearch is " + av +
                " when expected is " + expectedDn);
          }
        }
      }
      debugInfo(tn, "Second search done successfully : " + searchResultEntry);

      stop(server01);
      waitForClose(s);

      // TODO:  Testing ACI is disabled because it is currently failing when
      // ran in the precommit target while it works well when running alone.
      // anonymous search returns entries from cn=changelog whereas it
      // should not. Probably a previous test in the nightlytests suite is
      // removing/modifying some ACIs...
      // When problem found, we have to re-enable this test.
      if (false)
      {
        // ACI step
        debugInfo(tn, "Starting ACI step");
        s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
        r = new org.opends.server.tools.LDAPReader(s);
        w = new LDAPWriter(s);
        s.setSoTimeout(5000);
        bindAsWhoEver(w, r, "toto", "tutu", LDAPResultCode.OPERATIONS_ERROR);

        searchRequest =
            createSearchRequest("(targetDN=*directpsearch*,o=test)", null);

        debugInfo(tn, "ACI test : sending search");
        message = new LDAPMessage(2, searchRequest, createControls(""));
        w.writeMessage(message);

        searchesDone=0;
        searchEntries = 0;
        searchResultEntry = null;
        searchResultDone = null;
        while ((searchesDone==0) && (message = r.readMessage()) != null)
        {
          debugInfo(tn, "ACI test : message returned " +
              message.getProtocolOpType() + message);
          switch (message.getProtocolOpType())
          {
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
            searchResultEntry = message.getSearchResultEntryProtocolOp();
            //assertTrue(false, "Unexpected entry returned in ACI test of " + tn + searchResultEntry);
            searchEntries++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
            searchReferences++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
            searchResultDone = message.getSearchResultDoneProtocolOp();
            assertEquals(searchResultDone.getResultCode(),
                ResultCode.SUCCESS.getIntValue());
//          assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
            searchesDone++;
            break;
          }
        }
        // search should end with success
        assertEquals(searchesDone, 1);
        // but returning no entry
        assertEquals(searchEntries,0, "Bad search entry# in ACI test of " + tn);
      }

      close(s);
      while (!s.isClosed()) sleep(100);
    }
    debugInfo(tn, "Ends test successfully");
  }

  private SearchRequestProtocolOp createSearchRequest(String filterString,
      final Set<String> attributes) throws LDAPException
  {
    return new SearchRequestProtocolOp(
        ByteString.valueOf("cn=changelog"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        false,
        LDAPFilter.decode(filterString),
        attributes);
  }

  /**
   * Test parallel simultaneous psearch with different filters.
   */
  private void ECLSimultaneousPsearches() throws Exception
  {
    String tn = "ECLSimultaneousPsearches";
    debugInfo(tn, "Starting test \n\n");
    Socket s1 = null, s2 = null, s3 = null;
    ReplicationBroker server01 = null;
    ReplicationBroker server02 = null;
    boolean compatMode = false;
    boolean changesOnly = false;

    // create stats
    for (ConnectionHandler<?> ch : DirectoryServer.getConnectionHandlers())
    {
      if (ch instanceof LDAPConnectionHandler)
      {
        LDAPConnectionHandler lch = (LDAPConnectionHandler) ch;
        if (!lch.useSSL())
        {
          ldapStatistics = lch.getStatTracker();
        }
      }
    }
    assertNotNull(ldapStatistics);

    try
    {
      // Create broker on o=test
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);
      server01.setChangeTimeHeartbeatInterval(100); //ms

      // Create broker on o=test2
      server02 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, true, EMPTY_DN_GENID);
      server02.setChangeTimeHeartbeatInterval(100); //ms

      int ts = 1;
      // Produce update 1
      CSN csn1 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_1);
      DeleteMsg delMsg1 =
        newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csn1,
            "11111111-1111-1111-1111-111111111111");
      debugInfo(tn, " publishing " + delMsg1);
      server01.publish(delMsg1);
      sleep(500); // let's be sure the message is in the RS

      // Produce update 2
      CSN csn2 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_2);
      DeleteMsg delMsg2 =
        newDeleteMsg("uid=" + tn + "2," + TEST_ROOT_DN_STRING2, csn2,
            "22222222-2222-2222-2222-222222222222");
      debugInfo(tn, " publishing " + delMsg2);
      server02.publish(delMsg2);
      sleep(500); // let's be sure the message is in the RS

      // Produce update 3
      CSN csn3 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_2);
      DeleteMsg delMsg3 =
        newDeleteMsg("uid=" + tn + "3," + TEST_ROOT_DN_STRING2, csn3,
            "33333333-3333-3333-3333-333333333333");
      debugInfo(tn, " publishing " + delMsg3);
      server02.publish(delMsg3);
      sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      String cookie = "";
      List<Control> controls = createControls(cookie);
      if (compatMode)
      {
        cookie = null;
        controls = new ArrayList<Control>(0);
      }

      // Creates psearch control
      Set<PersistentSearchChangeType> changeTypes =
          EnumSet.of(ADD, DELETE, MODIFY, MODIFY_DN);
      controls.add(new PersistentSearchControl(changeTypes, changesOnly, true));

      final Set<String> attributes = ALL_ATTRIBUTES;
      SearchRequestProtocolOp searchRequest1 = createSearchRequest("(targetDN=*"+tn+"*,o=test)", attributes);
      SearchRequestProtocolOp searchRequest2 = createSearchRequest("(targetDN=*"+tn+"*,o=test2)", attributes);
      SearchRequestProtocolOp searchRequest3 = createSearchRequest("objectclass=*", attributes);

      // Connects and bind
      s1 = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r1 = new org.opends.server.tools.LDAPReader(s1);
      LDAPWriter w1 = new LDAPWriter(s1);
      s1.setSoTimeout(15000);
      bindAsManager(w1, r1);

      // Connects and bind
      s2 = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r2 = new org.opends.server.tools.LDAPReader(s2);
      LDAPWriter w2 = new LDAPWriter(s2);
      s2.setSoTimeout(30000);
      bindAsManager(w2, r2);

      // Connects and bind
      s3 = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r3 = new org.opends.server.tools.LDAPReader(s3);
      LDAPWriter w3 = new LDAPWriter(s3);
      s3.setSoTimeout(15000);
      bindAsManager(w3, r3);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));

      InvocationCounterPlugin.resetAllCounters();

      ldapStatistics.getSearchRequests();
      long searchEntries    = ldapStatistics.getSearchResultEntries();
      ldapStatistics.getSearchResultReferences();
      long searchesDone     = ldapStatistics.getSearchResultsDone();

      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest1, controls);
      w1.writeMessage(message);
      sleep(500);

      message = new LDAPMessage(2, searchRequest2, controls);
      w2.writeMessage(message);
      sleep(500);

      message = new LDAPMessage(2, searchRequest3, controls);
      w3.writeMessage(message);
      sleep(500);

      SearchResultEntryProtocolOp searchResultEntry = null;
      SearchResultDoneProtocolOp searchResultDone = null;

      if (!changesOnly)
      {
        debugInfo(tn, "Search1  Persistent filter=" + searchRequest1.getFilter()
                  + " expected to return change " + csn1);
        searchEntries = 0;
        message = null;

        {
          while (searchEntries < 1 && (message = r1.readMessage()) != null)
          {
            debugInfo(tn, "Search1 Result=" +
                message.getProtocolOpType() + " " + message);
            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              if (searchEntries==1)
              {
                checkValue(searchResultEntry.toSearchResultEntry(),"replicationcsn",csn1.toString());
                checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                    (compatMode?"10":"0"));
              }
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(),
                  ResultCode.SUCCESS.getIntValue(),
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        debugInfo(tn, "Search1 done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);

        searchEntries = 0;
        message = null;
        {
          debugInfo(tn, "Search 2  Persistent filter=" + searchRequest2.getFilter()
              + " expected to return change " + csn2 + " & " + csn3);
          while (searchEntries < 2 && (message = r2.readMessage()) != null)
          {
            debugInfo(tn, "Search 2 Result=" +
                message.getProtocolOpType() + message);
            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                  (compatMode?"10":"0"));
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(),
                  ResultCode.SUCCESS.getIntValue(),
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        debugInfo(tn, "Search2 done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);


        searchEntries = 0;
        message = null;
        {
          debugInfo(tn, "Search3  Persistent filter=" + searchRequest3.getFilter()
              + " expected to return change top + " + csn1 + " & " + csn2 + " & " + csn3);
          while (searchEntries < 4 && (message = r3.readMessage()) != null)
          {
            debugInfo(tn, "Search3 Result=" +
                message.getProtocolOpType() + " " + message);

            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(),
                  ResultCode.SUCCESS.getIntValue(),
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        debugInfo(tn, "Search3 done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);

      }

      // Produces additional change
      CSN csn11 = new CSN(TimeThread.getTime(), 11, SERVER_ID_1);
      String expectedDn11 = "uid=" + tn + "11," +  TEST_ROOT_DN_STRING;
      DeleteMsg delMsg11 = newDeleteMsg(expectedDn11, csn11,
         "44444444-4444-4444-4444-444444444444");
      debugInfo(tn, " publishing " + delMsg11);
      server01.publish(delMsg11);
      sleep(500);
      debugInfo(tn, delMsg11.getCSN() + " published additionally ");

      // Produces additional change
      CSN csn12 = new CSN(TimeThread.getTime(), 12, SERVER_ID_2);
      String expectedDn12 = "uid=" + tn + "12," +  TEST_ROOT_DN_STRING2;
      DeleteMsg delMsg12 = newDeleteMsg(expectedDn12, csn12,
         "55555555-5555-5555-5555-555555555555");
      debugInfo(tn, " publishing " + delMsg12 );
      server02.publish(delMsg12);
      sleep(500);
      debugInfo(tn, delMsg12.getCSN()  + " published additionally ");

      // Produces additional change
      CSN csn13 = new CSN(TimeThread.getTime(), 13, SERVER_ID_2);
      String expectedDn13 = "uid=" + tn + "13," +  TEST_ROOT_DN_STRING2;
      DeleteMsg delMsg13 = newDeleteMsg(expectedDn13, csn13,
         "66666666-6666-6666-6666-666666666666");
      debugInfo(tn, " publishing " + delMsg13);
      server02.publish(delMsg13);
      sleep(500);
      debugInfo(tn, delMsg13.getCSN()  + " published additionally ");

      // wait 11
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while (searchEntries < 1 && (message = r1.readMessage()) != null)
      {
        debugInfo(tn, "Search 11 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(),
              ResultCode.SUCCESS.getIntValue(),
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);
      debugInfo(tn, "Search 1 successfully receives additional changes");

      // wait 12 & 13
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while (searchEntries < 2 && (message = r2.readMessage()) != null)
      {
        debugInfo(tn, "psearch search 12 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(),
              ResultCode.SUCCESS.getIntValue(),
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);
      debugInfo(tn, "Search 2 successfully receives additional changes");

      // wait 11 & 12 & 13
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while (searchEntries < 3 && (message = r3.readMessage()) != null)
      {
        debugInfo(tn, "psearch search 13 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(),
              ResultCode.SUCCESS.getIntValue(),
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);

      // Check we received change 13
      for (LDAPAttribute a : searchResultEntry.getAttributes())
      {
        if ("targetDN".equalsIgnoreCase(a.getAttributeType()))
        {
          for (ByteString av : a.getValues())
          {
            assertTrue(av.toString().equalsIgnoreCase(expectedDn13),
                "Entry returned by psearch 13 is " + av +
                " when expected is " + expectedDn13);
          }
        }
      }
      debugInfo(tn, "Search 3 successfully receives additional changes");
    }
    finally
    {
      stop(server01, server02);
      waitForClose(s1, s2, s3);
    }
    debugInfo(tn, "Ends test successfully");
  }

  private void waitForClose(Socket... sockets) throws InterruptedException
  {
    for (Socket s : sockets)
    {
      if (s != null)
      {
        close(s);
        while (!s.isClosed())
        {
          sleep(100);
        }
      }
    }
  }

  /** utility - bind as required */
  private void bindAsManager(LDAPWriter w, org.opends.server.tools.LDAPReader r)
      throws Exception
  {
    bindAsWhoEver(w, r,
        "cn=Directory Manager", "password", LDAPResultCode.SUCCESS);
  }

  /** utility - bind as required */
  private void bindAsWhoEver(LDAPWriter w, org.opends.server.tools.LDAPReader r,
      String bindDN, String password,  int expected) throws Exception
  {
//  Since we are going to be watching the post-response count, we need to
//  wait for the server to become idle before kicking off the next request to
//  ensure that any remaining post-response processing from the previous
//  operation has completed.
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


    InvocationCounterPlugin.resetAllCounters();
    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
          ByteString.valueOf(bindDN),
          3, ByteString.valueOf(password));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
//  assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
    assertEquals(bindResponse.getResultCode(), expected);
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @Override
  @AfterClass
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    shutdown();

    paranoiaCheck();
  }

  @AfterMethod
  public void clearReplicationDb()
  {
    replicationServer.clearDb();
  }

  /**
   * After the tests stop the replicationServer.
   */
  private void shutdown() throws Exception
  {
    remove(replicationServer);
    replicationServer = null;
  }

  /**
   * Utility - sleeping as long as required
   */
  private void sleep(long time) throws InterruptedException
  {
    Thread.sleep(time);
  }

  /**
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String testName, String message)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST " + testName + " ** " + message);
    }
  }

  /**
   * Utility - create a second backend in order to test ECL with 2 suffixes.
   */
  private static Backend initializeTestBackend(boolean createBaseEntry,
      String backendId) throws Exception
  {
    DN baseDN = DN.decode("o=" + backendId);

    //  Retrieve backend. Warning: it is important to perform this each time,
    //  because a test may have disabled then enabled the backend (i.e a test
    //  performing an import task). As it is a memory backend, when the backend
    //  is re-enabled, a new backend object is in fact created and old reference
    //  to memory backend must be invalidated. So to prevent this problem, we
    //  retrieve the memory backend reference each time before cleaning it.
    MemoryBackend memoryBackend =
      (MemoryBackend)DirectoryServer.getBackend(backendId);

    if (memoryBackend == null)
    {
      memoryBackend = new MemoryBackend();
      memoryBackend.setBackendID(backendId);
      memoryBackend.setBaseDNs(new DN[] {baseDN});
      memoryBackend.initializeBackend();
      DirectoryServer.registerBackend(memoryBackend);
    }

    memoryBackend.clearMemoryBackend();

    if (createBaseEntry)
    {
      memoryBackend.addEntry(createEntry(baseDN), null);
    }
    return memoryBackend;
  }

  private static void removeTestBackend(Backend... backends)
  {
    for (Backend backend : backends)
    {
      if (backend != null)
      {
        MemoryBackend memoryBackend = (MemoryBackend) backend;
        memoryBackend.clearMemoryBackend();
        memoryBackend.finalizeBackend();
        DirectoryServer.deregisterBackend(memoryBackend);
      }
    }
  }

  private void ChangeTimeHeartbeatTest() throws Exception
  {
    String tn = "ChangeTimeHeartbeatTest";
    debugInfo(tn, "Starting test");
    ReplicationBroker s1test = null;
    ReplicationBroker s2test = null;
    ReplicationBroker s1test2 = null;
    ReplicationBroker s2test2 = null;
    Backend backend2 = null;

    try
    {
      backend2 = initializeTestBackend(true, TEST_BACKEND_ID2);

      // --
      s1test = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      s2test2 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, true, EMPTY_DN_GENID);
      sleep(500);

      // Produce updates
      long time = TimeThread.getTime();
      int ts = 1;
      CSN csn = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn, tn, 1);

      csn = new CSN(time++, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest(s2test2, csn, tn, 2);

      CSN csn3 = new CSN(time++, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest(s2test2, csn3, tn, 3);

      csn = new CSN(time++, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn, tn, 4);
      sleep(500);

      // --
      s1test2 = openReplicationSession(TEST_ROOT_DN2,  1203,
          100, replicationServerPort, brokerSessionTimeout, true, EMPTY_DN_GENID);

      s2test = openReplicationSession(TEST_ROOT_DN,  1204,
          100, replicationServerPort, brokerSessionTimeout, true);
      sleep(500);

      // Test startState ("first cookie") of the ECL
      time = TimeThread.getTime();
      csn = new CSN(time++, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn, tn, 6);

      csn = new CSN(time++, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn, tn, 7);

      CSN csn8 = new CSN(time++, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn8, tn, 8);

      CSN csn9 = new CSN(time++, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn9, tn, 9);
      sleep(500);

      ReplicationServerDomain rsd1 = replicationServer.getReplicationServerDomain(TEST_ROOT_DN);
      rsd1.getLatestServerState();
      rsd1.getChangeTimeHeartbeatState();
      debugInfo(tn, rsd1.getBaseDN()
          + " LatestServerState=" + rsd1.getLatestServerState()
          + " ChangeTimeHeartBeatState=" + rsd1.getChangeTimeHeartbeatState()
          + " eligibleCSN=" + rsd1.getEligibleCSN()
          + " rs eligibleCSN=" + replicationServer.getEligibleCSN(null));
      // FIXME:ECL Enable this test by adding an assert on the right value

      ReplicationServerDomain rsd2 = replicationServer.getReplicationServerDomain(TEST_ROOT_DN2);
      rsd2.getLatestServerState();
      rsd2.getChangeTimeHeartbeatState();
      debugInfo(tn, rsd2.getBaseDN()
          + " LatestServerState=" + rsd2.getLatestServerState()
          + " ChangeTimeHeartBeatState=" + rsd2.getChangeTimeHeartbeatState()
          + " eligibleCSN=" + rsd2.getEligibleCSN()
          + " rs eligibleCSN=" + replicationServer.getEligibleCSN(null));
      // FIXME:ECL Enable this test by adding an assert on the right value
    }
    finally
    {
      stop(s1test2, s2test2, s1test, s2test);
      removeTestBackend(backend2);
    }
    debugInfo(tn, "Ending test successfully");
  }

  /**
   * From embedded ECL (no remote session)
   * With empty RS, simple search should return only root entry.
   */
  private void ECLCompatEmpty() throws Exception
  {
    String tn = "ECLCompatEmpty";
    debugInfo(tn, "Starting test\n\n");

    // search on 'cn=changelog'
    String filter = "(objectclass=*)";
    debugInfo(tn, " Search: " + filter);
    InternalSearchOperation op = connection.processSearch(
        "cn=changelog",
        SearchScope.WHOLE_SUBTREE,
        filter);

    // success
    assertEquals(op.getResultCode(), ResultCode.SUCCESS, op.getErrorMessage().toString());

    // root entry returned
    assertEquals(op.getEntriesSent(), 1);
    debugInfo(tn, "Ending test successfully");
  }

  private int ECLCompatWriteReadAllOps(long firstChangeNumber) throws Exception
  {
    String tn = "ECLCompatWriteReadAllOps/" + firstChangeNumber;
    debugInfo(tn, "Starting test\n\n");
    final int nbChanges = 4;

    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";

      CSN[] csns = generateCSNs(nbChanges, SERVER_ID_1);
      gblCSN = csns[1];

      // Publish DEL
      DeleteMsg delMsg = newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csns[0], user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getCSN());

      // Publish ADD
      String lentry =
          "dn: uid="+tn+"2," + TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: domain\n"
          + "entryUUID: "+user1entryUUID+"\n";
      Entry entry = TestCaseUtils.entryFromLdifString(lentry);
      AddMsg addMsg = new AddMsg(
          gblCSN,
          DN.decode("uid="+tn+"2," + TEST_ROOT_DN_STRING),
          user1entryUUID,
          baseUUID,
          entry.getObjectClassAttribute(),
          entry.getAttributes(),
          new ArrayList<Attribute>());
      server01.publish(addMsg);
      debugInfo(tn, " publishes " + addMsg.getCSN());

      // Publish MOD
      DN baseDN = DN.decode("uid="+tn+"3," + TEST_ROOT_DN_STRING);
      List<Modification> mods = createMods("description", "new value");
      ModifyMsg modMsg = new ModifyMsg(csns[2], baseDN, mods, user1entryUUID);
      server01.publish(modMsg);
      debugInfo(tn, " publishes " + modMsg.getCSN());

      // Publish modDN
      ModifyDNOperation op = new ModifyDNOperationBasis(connection, 1, 1, null,
          DN.decode("uid="+tn+"4," + TEST_ROOT_DN_STRING), // entryDN
          RDN.decode("uid="+tn+"new4"), // new rdn
          true,  // deleteoldrdn
          TEST_ROOT_DN2); // new superior
      op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(csns[3], user1entryUUID, "newparentId"));
      LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
      ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
      server01.publish(modDNMsg);
      debugInfo(tn, " publishes " + modDNMsg.getCSN());
      sleep(1000);

      String filter = "(targetdn=*"+tn.toLowerCase()+"*,o=test)";
      InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);

      // test 4 entries returned
      assertEntries(searchOp.getSearchEntries(), firstChangeNumber, tn,
          ldifWriter, user1entryUUID, csns[0], gblCSN, csns[2], csns[3]);

      stop(server01);

      // Test with filter on change number
      filter =
          "(&(targetdn=*" + tn.toLowerCase() + "*,o=test)" +
          		"(&(changenumber>=" + firstChangeNumber + ")" +
          				"(changenumber<=" + (firstChangeNumber + 3) + ")))";
      searchOp = searchOnChangelog(filter, tn, SUCCESS);

      assertEntries(searchOp.getSearchEntries(), firstChangeNumber, tn,
          ldifWriter, user1entryUUID, csns[0], gblCSN, csns[2], csns[3]);
      assertEquals(searchOp.getSearchEntries().size(), nbChanges);
    }
    debugInfo(tn, "Ending test with success");
    return nbChanges;
  }

  private void assertEntries(List<SearchResultEntry> entries,
      long firstChangeNumber, String tn, LDIFWriter ldifWriter,
      String user1entryUUID, CSN... csns) throws Exception
  {
    debugAndWriteEntries(ldifWriter, entries, tn);
    assertEquals(entries.size(), 4);

    int i=0;
    for (SearchResultEntry resultEntry : entries)
    {
      i++;

      assertDnEquals(resultEntry, firstChangeNumber, i - 1);
      checkValue(resultEntry, "changenumber", String.valueOf(firstChangeNumber + i - 1));
      checkValue(resultEntry, "targetentryuuid", user1entryUUID);
      checkValue(resultEntry, "replicaidentifier", String.valueOf(SERVER_ID_1));
      final CSN csn = csns[i - 1];
      checkValue(resultEntry, "replicationcsn", csn.toString());
      checkValue(resultEntry, "changelogcookie", "o=test:" + csn + ";");
      checkValue(resultEntry, "targetdn", "uid=" + tn + i + "," + TEST_ROOT_DN_STRING);

      if (i==1)
      {
        // check the DEL entry has the right content
        checkValue(resultEntry,"changetype","delete");
        checkValue(resultEntry,"targetuniqueid",user1entryUUID);
      } else if (i==2)
      {
        // check the ADD entry has the right content
        checkValue(resultEntry, "changetype", "add");
        String expectedValue1 = "objectClass: domain\nobjectClass: top\n"
            + "entryUUID: " + user1entryUUID + "\n";
        String expectedValue2 = "entryUUID: " + user1entryUUID + "\n"
            + "objectClass: domain\nobjectClass: top\n";
        checkPossibleValues(resultEntry,"changes",expectedValue1, expectedValue2);
      } else if (i==3)
      {
        // check the MOD entry has the right content
        checkValue(resultEntry, "changetype", "modify");
        final String expectedValue = "replace: description\n" + "description: new value\n-\n";
        checkValue(resultEntry,"changes",expectedValue);
      } else if (i==4)
      {
        // check the MODDN entry has the right content
        checkValue(resultEntry, "changetype", "modrdn");
        checkValue(resultEntry,"newrdn","uid="+tn+"new4");
        checkValue(resultEntry,"newsuperior",TEST_ROOT_DN_STRING2);
        checkValue(resultEntry,"deleteoldrdn","true");
      }
    }
  }

  private void assertDnEquals(SearchResultEntry resultEntry, long changeNumber, int i)
  {
    String actualDN = resultEntry.getDN().toNormalizedString();
    String expectedDN = "changenumber=" + (changeNumber + i) + ",cn=changelog";
    assertThat(actualDN).isEqualToIgnoringCase(expectedDN);
  }

  private void ECLCompatReadFrom(long firstChangeNumber) throws Exception
  {
    String tn = "ECLCompatReadFrom/" + firstChangeNumber;
    debugInfo(tn, "Starting test\n\n");

    LDIFWriter ldifWriter = getLDIFWriter();

    // Creates broker on o=test
    ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
            100, replicationServerPort, brokerSessionTimeout, true);

    String user1entryUUID = "11111111-1112-1113-1114-111111111115";

    String filter = "(changenumber=" + firstChangeNumber + ")";
    InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);

    List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertEquals(entries.size(), 1);
    debugAndWriteEntries(ldifWriter, entries, tn);

    // check the entry has the right content
    SearchResultEntry resultEntry = entries.get(0);
    assertTrue("changenumber=6,cn=changelog".equalsIgnoreCase(resultEntry.getDN().toNormalizedString()));
    checkValue(resultEntry, "replicationcsn", gblCSN.toString());
    checkValue(resultEntry, "replicaidentifier", String.valueOf(SERVER_ID_1));
    checkValue(resultEntry, "changetype", "add");
    checkValue(resultEntry, "changelogcookie", "o=test:" + gblCSN + ";");
    checkValue(resultEntry, "targetentryuuid", user1entryUUID);
    checkValue(resultEntry, "changenumber", "6");

    stop(server01);

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Process similar search as but only check that there's no control returned
   * as part of the entry.
   */
  private void ECLCompatNoControl(long firstChangeNumber) throws Exception
  {
    String tn = "ECLCompatNoControl/" + firstChangeNumber;
    debugInfo(tn, "Starting test\n\n");

    // Creates broker on o=test
    ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1, 100,
            replicationServerPort, brokerSessionTimeout, true);

    String filter = "(changenumber=" + firstChangeNumber + ")";
    InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);

    List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertEquals(entries.size(), 1);
    // Just verify that no entry contains the ChangeLogCookie control
    List<Control> controls = entries.get(0).getControls();
    assertTrue(controls.isEmpty());

    stop(server01);

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode from firstChangeNumber and to lastChangeNumber.
   *
   * @param firstChangeNumber
   *          the lower limit
   * @param lastChangeNumber
   *          the higher limit
   */
  private void ECLCompatReadFromTo(long firstChangeNumber, long lastChangeNumber) throws Exception
  {
    String tn = "ECLCompatReadFromTo/" + firstChangeNumber + "/" + lastChangeNumber;
    debugInfo(tn, "Starting test\n\n");

    String filter =
        "(&(changenumber>=" + firstChangeNumber + ")" + "(changenumber<=" + lastChangeNumber + "))";
    InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);
    assertEquals(searchOp.getSearchEntries().size(), lastChangeNumber - firstChangeNumber + 1);
    debugAndWriteEntries(null, searchOp.getSearchEntries(), tn);

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode providing an unknown change number.
   */
  private void ECLCompatBadSeqnum() throws Exception
  {
    String tn = "ECLCompatBadSeqnum";
    debugInfo(tn, "Starting test\n\n");

    String filter = "(changenumber=1000)";
    InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);
    assertEquals(searchOp.getSearchEntries().size(), 0);

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode providing an unknown change number.
   */
  private void ECLFilterOnReplicationCsn() throws Exception
  {
    String tn = "ECLFilterOnReplicationCsn";
    debugInfo(tn, "Starting test\n\n");

    LDIFWriter ldifWriter = getLDIFWriter();

    String filter = "(replicationcsn=" + this.gblCSN + ")";
    InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);
    assertEquals(searchOp.getSearchEntries().size(), 1);

    List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertEquals(entries.size(), 1);
    debugAndWriteEntries(ldifWriter, entries, tn);

    // check the DEL entry has the right content
    SearchResultEntry resultEntry = entries.get(0);
    checkValue(resultEntry, "replicationcsn", gblCSN.toString());
    // TODO:ECL check values of the other attributes

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Test that different values of filter are correctly decoded to find if the
   * search op on the ECL can be optimized regarding the change numbers.
   */
  private void ECLFilterTest() throws Exception
  {
    String tn = "ECLFilterTest";
    debugInfo(tn, "Starting test\n\n");

    {
      DN baseDN = DN.decode("cn=changelog");

      evaluateSearchParameters(baseDN, -1, -1, "(objectclass=*)");
      evaluateSearchParameters(baseDN, 2, -1, "(changenumber>=2)");
      evaluateSearchParameters(baseDN, 2, 5, "(&(changenumber>=2)(changenumber<=5))");

      try
      {
        final StartECLSessionMsg startCLmsg = new StartECLSessionMsg();
        ECLSearchOperation.evaluateSearchParameters(startCLmsg,
            baseDN, SearchFilter.createFilterFromString("(&(changenumber>=2)(changenumber<+5))"));
        assertEquals(startCLmsg.getFirstChangeNumber(), 1);
      }
      catch (DirectoryException expected)
      {
      }

      evaluateSearchParameters(baseDN, 2, 5,
          "(&(dc=x)(&(changenumber>=2)(changenumber<=5)))");
      evaluateSearchParameters(baseDN, 3, 4,
          "(&(&(changenumber>=3)(changenumber<=4))(&(|(dc=y)(dc=x))(&(changenumber>=2)(changenumber<=5))))");
      evaluateSearchParameters(baseDN, -1, -1,
          "(|(objectclass=*)(&(changenumber>=2)(changenumber<=5)))");
      evaluateSearchParameters(baseDN, 8, 8, "(changenumber=8)");

      //
      CSN csn = new CSNGenerator(1, 0).newCSN();
      final StartECLSessionMsg startCLmsg =
          evaluateSearchParameters(baseDN, -1, -1, "(replicationcsn=" + csn + ")");
      assertEquals(startCLmsg.getCSN(), csn);

      // Use change number as base object.
      baseDN = DN.decode("changeNumber=8,cn=changelog");

      //
      evaluateSearchParameters(baseDN, 8, 8, "(objectclass=*)");

      // The base DN should take preference.
      evaluateSearchParameters(baseDN, 8, 8, "(changenumber>=2)");
    }
    debugInfo(tn, "Ending test with success");
  }

  private StartECLSessionMsg evaluateSearchParameters(DN baseDN,
      long firstChangeNumber, long lastChangeNumber, String filterString) throws Exception
  {
    final StartECLSessionMsg startCLmsg = new StartECLSessionMsg();
    ECLSearchOperation.evaluateSearchParameters(startCLmsg, baseDN,
        SearchFilter.createFilterFromString(filterString));
    assertEquals(startCLmsg.getFirstChangeNumber(), firstChangeNumber);
    assertEquals(startCLmsg.getLastChangeNumber(), lastChangeNumber);
    return startCLmsg;
  }

  /**
   * Put a short purge delay to the draftCNDB, clear the changelogDB,
   * expect the draftCNDb to be purged accordingly.
   */
  private void ECLPurgeDraftCNDbAfterChangelogClear() throws Exception
  {
    String tn = "ECLPurgeDraftCNDbAfterChangelogClear";
    debugInfo(tn, "Starting test\n\n");

    DraftCNDbHandler draftdb =
        (DraftCNDbHandler) replicationServer.getChangeNumberIndexDB();
    assertEquals(draftdb.count(), 8);
    draftdb.setPurgeDelay(1000);

    // Now clear the changelog db
    this.replicationServer.clearDb();

    // Expect changes purged from the changelog db to be sometimes
    // also purged from the DraftCNDb.
    while (!draftdb.isEmpty())
    {
      debugInfo(tn, "draftdb.count=" + draftdb.count());
      sleep(200);
    }

    debugInfo(tn, "Ending test with success");
  }

  private void ECLOperationalAttributesFailTest() throws Exception
  {
    String tn = "ECLOperationalAttributesFailTest";
    // The goal is to verify that the Changelog attributes are not
    // available in other entries. We u
    debugInfo(tn, "Starting test \n\n");

    Set<String> attributes = newSet("firstchangenumber", "lastchangenumber",
        "changelog", "lastExternalChangelogCookie");

    debugInfo(tn, " Search: " + TEST_ROOT_DN_STRING);
    InternalSearchOperation searchOp = connection.processSearch(
            TEST_ROOT_DN_STRING,
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            "(objectclass=*)",
            attributes,
            NO_CONTROL,
            null);
    waitOpResult(searchOp, ResultCode.SUCCESS);
    assertEquals(searchOp.getSearchEntries().size(), 1);

    List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertEquals(entries.size(), 1);
    debugAndWriteEntries(null, entries, tn);
    for (SearchResultEntry resultEntry : entries)
    {
      assertEquals(getAttributeValueOrNull(resultEntry, "firstchangenumber"), null);
      assertEquals(getAttributeValueOrNull(resultEntry, "lastchangenumber"), null);
      assertEquals(getAttributeValueOrNull(resultEntry, "changelog"), null);
      assertEquals(getAttributeValueOrNull(resultEntry, "lastExternalChangelogCookie"), null);
    }

    debugInfo(tn, "Ending test with success");
  }

  private void ECLCompatTestLimits(int expectedFirst, int expectedLast,
      boolean eclEnabled) throws Exception
  {
    String tn = "ECLCompatTestLimits";
    debugInfo(tn, "Starting test\n\n");

    LDIFWriter ldifWriter = getLDIFWriter();

    // search on 'cn=changelog'
    Set<String> attributes = new LinkedHashSet<String>();
    if (expectedFirst > 0)
      attributes.add("firstchangenumber");
    attributes.add("lastchangenumber");
    attributes.add("changelog");
    attributes.add("lastExternalChangelogCookie");

    debugInfo(tn, " Search: rootDSE");
    InternalSearchOperation searchOp = searchOnRootDSE(attributes);
    List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertEquals(entries.size(), 1);
    SearchResultEntry resultEntry = entries.get(0);
    debugAndWriteEntries(ldifWriter, entries, tn);

    if (eclEnabled)
    {
      if (expectedFirst > 0)
        checkValue(resultEntry, "firstchangenumber", String.valueOf(expectedFirst));
      checkValue(resultEntry, "lastchangenumber", String.valueOf(expectedLast));
      checkValue(resultEntry, "changelog", String.valueOf("cn=changelog"));
    }
    else
    {
      if (expectedFirst > 0)
        assertEquals(getAttributeValueOrNull(resultEntry, "firstchangenumber"), null);
      assertEquals(getAttributeValueOrNull(resultEntry, "lastchangenumber"), null);
      assertEquals(getAttributeValueOrNull(resultEntry, "changelog"), null);
      assertEquals(getAttributeValueOrNull(resultEntry, "lastExternalChangelogCookie"), null);
    }

    debugInfo(tn, "Ending test with success");
  }

  private InternalSearchOperation searchOnRootDSE(Set<String> attributes)
      throws Exception
  {
    final InternalSearchOperation searchOp = connection.processSearch(
        "",
        SearchScope.BASE_OBJECT,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0, // Size limit
        0, // Time limit
        false, // Types only
        "(objectclass=*)",
        attributes,
        NO_CONTROL,
        null);
    waitOpResult(searchOp, ResultCode.SUCCESS);
    return searchOp;
  }

  private void ECLCompatTestLimitsAndAdd(int expectedFirst, int expectedLast,
      int ts) throws Exception
  {
    String tn = "ECLCompatTestLimitsAndAdd";
    debugInfo(tn, "Starting test\n\n");

    ECLCompatTestLimits(expectedFirst, expectedLast, true);

    // Creates broker on o=test
    ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1, 100,
            replicationServerPort, brokerSessionTimeout, true);

    String user1entryUUID = "11111111-1112-1113-1114-111111111115";

    // Publish DEL
    CSN csn1 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_1);
    DeleteMsg delMsg = newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING,
        csn1, user1entryUUID);
    server01.publish(delMsg);
    debugInfo(tn, " publishes " + delMsg.getCSN());
    sleep(500);

    stop(server01);

    ECLCompatTestLimits(expectedFirst, expectedLast + 1, true);

    debugInfo(tn, "Ending test with success");
  }

  private void ECLGetEligibleCountTest() throws Exception
  {
    String tn = "ECLGetEligibleCountTest";
    debugInfo(tn, "Starting test\n\n");
    String user1entryUUID = "11111111-1112-1113-1114-111111111115";

    final CSN[] csns = generateCSNs(4, SERVER_ID_1);
    final CSN csn1 = csns[0];
    final CSN csn2 = csns[1];
    final CSN csn3 = csns[2];

    ReplicationServerDomain rsdtest = replicationServer.getReplicationServerDomain(TEST_ROOT_DN);
    // this empty state will force to count from the start of the DB
    final ServerState fromStart = new ServerState();

    // The replication changelog is empty
    assertEquals(rsdtest.getEligibleCount(fromStart, csns[0]), 0);

    // Creates broker on o=test
    ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
        1000, replicationServerPort, brokerSessionTimeout, true);

    // Publish one first message
    DeleteMsg delMsg = newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csn1, user1entryUUID);
    server01.publish(delMsg);
    debugInfo(tn, " publishes " + delMsg.getCSN());
    sleep(300);

    // From begin to now : 1 change
    assertEquals(rsdtest.getEligibleCount(fromStart, now()), 1);

    // Publish one second message
    delMsg = newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csn2, user1entryUUID);
    server01.publish(delMsg);
    debugInfo(tn, " publishes " + delMsg.getCSN());
    sleep(300);

    // From begin to now : 2 changes
    assertEquals(rsdtest.getEligibleCount(fromStart, now()), 2);

    // From begin to first change (inclusive) : 1 change = csn1
    assertEquals(rsdtest.getEligibleCount(fromStart, csn1), 1);

    final ServerState fromStateBeforeCSN1 = new ServerState();
    fromStateBeforeCSN1.update(csn1);

    // From state/csn1(exclusive) to csn1 (inclusive) : 0 change
    assertEquals(rsdtest.getEligibleCount(fromStateBeforeCSN1, csn1), 0);

    // From state/csn1(exclusive) to csn2 (inclusive) : 1 change = csn2
    assertEquals(rsdtest.getEligibleCount(fromStateBeforeCSN1, csn2), 1);

    final ServerState fromStateBeforeCSN2 = new ServerState();
    fromStateBeforeCSN2.update(csn2);

    // From state/csn2(exclusive) to now (inclusive) : 0 change
    assertEquals(rsdtest.getEligibleCount(fromStateBeforeCSN2, now()), 0);

    // Publish one third message
    delMsg = newDeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, csn3, user1entryUUID);
    server01.publish(delMsg);
    debugInfo(tn, " publishes " + delMsg.getCSN());
    sleep(300);

    fromStateBeforeCSN2.update(csn2);

    // From state/csn2(exclusive) to now : 1 change = csn3
    assertEquals(rsdtest.getEligibleCount(fromStateBeforeCSN2, now()), 1);

    boolean perfs=false;
    if (perfs)
    {
      // number of msgs used by the test
      int maxMsg = 999999;

      // We need an RS configured with a window size bigger than the number
      // of msg used by the test.
      assertTrue(maxMsg<maxWindow);
      debugInfo(tn, "Perf test in compat mode - will generate " + maxMsg + " msgs.");
      for (int i=4; i<=maxMsg; i++)
      {
        CSN csnx = new CSN(TimeThread.getTime(), i, SERVER_ID_1);
        delMsg = newDeleteMsg("uid="+tn+i+"," + TEST_ROOT_DN_STRING, csnx, user1entryUUID);
        server01.publish(delMsg);
      }
      sleep(1000);
      debugInfo(tn, "Perfs test in compat - search lastChangeNumber");
      Set<String> excludedDomains = MultimasterReplication.getECLDisabledDomains();
      excludedDomains.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);

      long t1 = TimeThread.getTime();
      long[] limits = replicationServer.getECLChangeNumberLimits(
          replicationServer.getEligibleCSN(excludedDomains), excludedDomains);
      assertEquals(limits[1], maxMsg);
      long t2 = TimeThread.getTime();
      debugInfo(tn, "Perfs - " + maxMsg + " counted in (ms):" + (t2 - t1));

      String filter = "(changenumber>=" + maxMsg + ")";
      InternalSearchOperation searchOp = searchOnChangelog(filter, tn, SUCCESS);
      long t3 = TimeThread.getTime();
      assertEquals(searchOp.getSearchEntries().size(), 1);
      debugInfo(tn, "Perfs - last change searched in (ms):" + (t3 - t2));

      filter = "(changenumber>=" + maxMsg + ")";
      searchOp = searchOnChangelog(filter, tn, SUCCESS);
      long t4 = TimeThread.getTime();
      assertEquals(searchOp.getSearchEntries().size(), 1);
      debugInfo(tn, "Perfs - last change searched in (ms):" + (t4 - t3));

      filter = "(changenumber>=" + (maxMsg - 2) + ")";
      searchOp = searchOnChangelog(filter, tn, SUCCESS);
      long t5 = TimeThread.getTime();
      assertEquals(searchOp.getSearchEntries().size(), 3);
      debugInfo(tn, "Perfs - last 3 changes searched in (ms):" + (t5 - t4));
      debugAndWriteEntries(null, searchOp.getSearchEntries(), tn);
    }
    stop(server01);
    debugInfo(tn, "Ending test with success");
  }

  private CSN now()
  {
    return new CSN(TimeThread.getTime(), 1, SERVER_ID_1);
  }

  /**
   * Test ECl entry attributes, and there configuration.
   */
  private void ECLIncludeAttributes() throws Exception
  {
    String tn = "ECLIncludeAttributes";
    debugInfo(tn, "Starting test\n\n");

    final String backendId3 = "test3";
    final DN baseDN3 = DN.decode("o=" + backendId3);
    Backend backend2 = null;
    Backend backend3 = null;
    LDAPReplicationDomain domain2 = null;
    LDAPReplicationDomain domain3 = null;
    LDAPReplicationDomain domain21 = null;
    try
    {
      // Configure replication on this backend
      // Add the root entry in the backend
      backend2 = initializeTestBackend(false, TEST_BACKEND_ID2);

      SortedSet<String> replServers = newSet("localhost:" + replicationServerPort);

      // on o=test2,sid=1702 include attrs set to : 'sn'
      SortedSet<String> eclInclude = newSet("sn", "roomnumber");

      DomainFakeCfg domainConf = new DomainFakeCfg(TEST_ROOT_DN2, 1702, replServers);
      domain2 = startNewDomain(domainConf, eclInclude, eclInclude);

      backend3 = initializeTestBackend(false, backendId3);

      // on o=test3,sid=1703 include attrs set to : 'objectclass'
      eclInclude = newSet("objectclass");

      SortedSet<String> eclIncludeForDeletes = newSet("*");

      domainConf = new DomainFakeCfg(baseDN3, 1703, replServers);
      domain3 = startNewDomain(domainConf, eclInclude, eclIncludeForDeletes);

      // on o=test2,sid=1704 include attrs set to : 'cn'
      eclInclude = newSet("cn");

      domainConf = new DomainFakeCfg(TEST_ROOT_DN2, 1704, replServers);
      domain21 = startNewDomain(domainConf, eclInclude, eclInclude);

      sleep(1000);

      addEntry(createEntry(TEST_ROOT_DN2));
      addEntry(createEntry(baseDN3));

      String lentry =
          "dn: cn=Fiona Jensen," + TEST_ROOT_DN_STRING2 + "\n"
          + "objectclass: top\n"
          + "objectclass: person\n"
          + "objectclass: organizationalPerson\n"
          + "objectclass: inetOrgPerson\n"
          + "cn: Fiona Jensen\n"
          + "sn: Jensen\n"
          + "uid: fiona\n"
          + "telephonenumber: 12121212";

      Entry uentry1 = TestCaseUtils.entryFromLdifString(lentry);
      addEntry(uentry1); // add fiona in o=test2

      lentry =
          "dn: cn=Robert Hue," + baseDN3 + "\n"
          + "objectclass: top\n"
          + "objectclass: person\n"
          + "objectclass: organizationalPerson\n"
          + "objectclass: inetOrgPerson\n"
          + "cn: Robert Hue\n"
          + "sn: Robby\n"
          + "uid: robert\n"
          + "telephonenumber: 131313";
      Entry uentry2 = TestCaseUtils.entryFromLdifString(lentry);
      addEntry(uentry2); // add robert in o=test3

      // mod 'sn' of fiona (o=test2) with 'sn' configured as ecl-incl-att
      runModifyOperation(uentry1, createMods("sn", "newsn"));

      // mod 'telephonenumber' of robert (o=test3)
      runModifyOperation(uentry2, createMods("telephonenumber", "555555"));

      // moddn robert (o=test3) to robert2 (o=test3)
      ModifyDNOperation modDNOp = new ModifyDNOperationBasis(connection,
          InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(),
          null,
          DN.decode("cn=Robert Hue," + baseDN3),
          RDN.decode("cn=Robert Hue2"), true,
          baseDN3);
      modDNOp.run();
      waitOpResult(modDNOp, ResultCode.SUCCESS);

      // del robert (o=test3)
      runDeleteOperation("cn=Robert Hue2," + baseDN3);
      sleep(1000);

      // Search on ECL from start on all suffixes
      String cookie = "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*)", cookie, tn, SUCCESS);
      final List<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertThat(entries).hasSize(8);
      debugAndWriteEntries(null, entries, tn);

      sleep(2000);

      for (SearchResultEntry resultEntry : entries)
      {
        String targetdn = getAttributeValueOrNull(resultEntry, "targetdn");

        if (targetdn.endsWith("cn=robert hue,o=test3")
            || targetdn.endsWith("cn=robert hue2,o=test3"))
        {
          Entry targetEntry = parseIncludedAttributes(resultEntry, targetdn);

          Set<String> eoc = newSet("person", "inetOrgPerson", "organizationalPerson", "top");
          checkValues(targetEntry, "objectclass", eoc);

          String changeType = getAttributeValueOrNull(resultEntry, "changetype");
          if ("delete".equals(changeType))
          {
            // We are using "*" for deletes so should get back 4 attributes.
            assertEquals(targetEntry.getAttributes().size(), 4);
            checkValue(targetEntry, "uid", "robert");
            checkValue(targetEntry, "cn", "Robert Hue2");
            checkValue(targetEntry, "telephonenumber", "555555");
            checkValue(targetEntry, "sn", "Robby");
          }
          else
          {
            assertEquals(targetEntry.getAttributes().size(), 0);
          }
        }
        else if (targetdn.endsWith("cn=fiona jensen,o=test2"))
        {
          Entry targetEntry = parseIncludedAttributes(resultEntry, targetdn);

          assertEquals(targetEntry.getAttributes().size(), 2);
          checkValue(targetEntry,"sn","jensen");
          checkValue(targetEntry,"cn","Fiona Jensen");
        }
        checkValue(resultEntry,"changeinitiatorsname", "cn=Internal Client,cn=Root DNs,cn=config");
      }
    }
    finally
    {
      runDeleteOperation("cn=Fiona Jensen," + TEST_ROOT_DN_STRING2);
      runDeleteOperation(TEST_ROOT_DN_STRING2);
      runDeleteOperation(baseDN3.toString());

      remove(domain21, domain2, domain3);
      removeTestBackend(backend2, backend3);
    }
    debugInfo(tn, "Ending test with success");
  }

  private void remove(LDAPReplicationDomain... domains)
  {
    for (LDAPReplicationDomain domain : domains)
    {
      if (domain != null)
      {
        domain.shutdown();
        MultimasterReplication.deleteDomain(domain.getBaseDN());
      }
    }
  }

  private static SortedSet<String> newSet(String... values)
  {
    return new TreeSet<String>(Arrays.asList(values));
  }

  private LDAPReplicationDomain startNewDomain(DomainFakeCfg domainConf,
      SortedSet<String> eclInclude, SortedSet<String> eclIncludeForDeletes)
      throws Exception
  {
    domainConf.setExternalChangelogDomain(
        new ExternalChangelogDomainFakeCfg(true, eclInclude, eclIncludeForDeletes));
    // Set a Changetime heartbeat interval low enough (less than default value
    // that is 1000 ms) for the test to be sure to consider all changes as eligible.
    domainConf.setChangetimeHeartbeatInterval(10);
    LDAPReplicationDomain newDomain = MultimasterReplication.createNewDomain(domainConf);
    newDomain.start();
    return newDomain;
  }

  private void runModifyOperation(Entry entry, List<Modification> mods)
      throws Exception
  {
    final ModifyOperation operation =
        new ModifyOperationBasis(connection, 1, 1, null, entry.getDN(), mods);
    operation.run();
    waitOpResult(operation, ResultCode.SUCCESS);
  }

  private void runDeleteOperation(String dn) throws Exception
  {
    final DeleteOperation delOp = new DeleteOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        DN.decode(dn));
    delOp.run();
    waitOpResult(delOp, ResultCode.SUCCESS);
  }

  private List<Modification> createMods(String attributeName, String valueString)
  {
    Attribute attr = Attributes.create(attributeName, valueString);
    List<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE, attr));
    return mods;
  }

  private Entry parseIncludedAttributes(SearchResultEntry resultEntry,
      String targetdn) throws Exception
  {
    // Parse includedAttributes as an entry.
    String includedAttributes = getAttributeValueOrNull(resultEntry, "includedattributes");
    String[] ldifAttributeLines = includedAttributes.split("\\n");
    String[] ldif = new String[ldifAttributeLines.length + 1];
    System.arraycopy(ldifAttributeLines, 0, ldif, 1, ldifAttributeLines.length);
    ldif[0] = "dn: " + targetdn;
    return TestCaseUtils.makeEntry(ldif);
  }

  private void waitOpResult(Operation operation, ResultCode expectedResult)
      throws Exception
  {
    int i = 0;
    while (operation.getResultCode() == ResultCode.UNDEFINED
        || operation.getResultCode() != expectedResult)
    {
      sleep(50);
      i++;
      if (i > 10)
      {
        assertEquals(operation.getResultCode(), expectedResult,
            operation.getErrorMessage().toString());
      }
    }
  }
}
