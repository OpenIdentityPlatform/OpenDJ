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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.util.*;

import org.assertj.core.api.Assertions;
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
import org.opends.server.replication.server.changelog.je.JEChangeNumberIndexDB;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.externalchangelog.ECLSearchOperation;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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

  private final int brokerSessionTimeout = 5000;
  private final int maxWindow = 100;

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
          replicationDbImplementation, 0, 71, 0, maxWindow, null);
    conf1.setComputeChangeNumber(true);

    replicationServer = new ReplicationServer(conf1);
    debugInfo("configure", "ReplicationServer created"+replicationServer);
  }

  @Test(enabled = true, dependsOnMethods = { "TestECLIsNotASupportedSuffix" })
  public void PrimaryTest() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);
    // let's enable ECl manually now that we tested that ECl is not available
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

  @Test(enabled=true, dependsOnMethods = { "PrimaryTest"})
  public void TestWithAndWithoutControl() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);
    // Write changes and read ECL from start
    ECLCompatWriteReadAllOps(1);

    ECLCompatNoControl(1);

    // Write additional changes and read ECL from a provided change number
    ECLCompatWriteReadAllOps(5);
  }

  @Test(enabled=false, dependsOnMethods = { "PrimaryTest"})
  public void PrimaryFullTest() throws Exception
  {
    // ***********************************************
    // First set of test are in the cookie mode
    // ***********************************************

    // Test that private backend is excluded from ECL
    ECLOnPrivateBackend();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestRemoteAPIWithEmptyECL() throws Exception
  {
    // Test remote API (ECL through replication protocol) with empty ECL
    ECLRemoteEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestWithEmptyECL() throws Exception
  {
    // Test with empty changelog
    ECLEmpty();
  }

  @Test(enabled=false, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestPrimaryPlusOperationAttributesNotVisible() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);
    // Test all types of ops.
    ECLAllOps(); // Do not clean the db for the next test

    // Test after this one will test access in RootDSE. This one checks in data.
    TestECLOperationalAttributesNotVisibleOutsideRootDSE();

    // First and last should be ok whenever a request has been done or not
    // in compat mode.
    ECLCompatTestLimits(1, 4, true);
  }

  @Test(enabled=false, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestRemoteAPIWithNonEmptyECL() throws Exception
  {
    // Test remote API (ECL through replication protocol) with NON empty ECL
    ECLRemoteNonEmpty();
  }

  /** Persistent search with changesOnly request */
  @Test(enabled=false, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestPersistentSearchWithChangesOnlyRequest() throws Exception
  {
    ECLPsearch(true, false);
  }

  /** Persistent search with init values request */
  @Test(enabled=false, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestPersistentSearchWithInitValuesRequest() throws Exception
  {
    ECLPsearch(false, false);
  }

  // TODO:ECL Test SEARCH abandon and check everything shutdown and cleaned
  // TODO:ECL Test PSEARCH abandon and check everything shutdown and cleaned
  // TODO:ECL Test invalid DN in cookie returns UNWILLING + message
  // TODO:ECL Test the attributes list and values returned in ECL entries
  // TODO:ECL Test search -s base, -s one

  @Test(enabled=true, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestFilters() throws Exception
  {
    // Test the different forms of filter that are parsed in order to
    // optimize the request.
    ECLFilterTest();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestDraftCompatModeWithEmptyECL() throws Exception
  {
    // ***********************************************
    // Second set of test are in the draft compat mode
    // ***********************************************
    // Empty replication changelog
    ECLCompatEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void FullTestRequestFromInvalidChangeNumber() throws Exception
  {
    // Request from an invalid change number
    ECLCompatBadSeqnum();
  }

  @Test(enabled=false, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void ECLReplicationServerFullTest15() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);
    // Write 4 changes and read ECL from start
    ECLCompatWriteReadAllOps(1);

    // Write 4 additional changes and read ECL from a provided change number
    CSN csn = ECLCompatWriteReadAllOps(5);

    // Test request from a provided change number - read 6
    ECLCompatReadFrom(6, csn);

    // Test request from a provided change number interval - read 5-7
    ECLCompatReadFromTo(5,7);

    // Test first and last change number
    ECLCompatTestLimits(1,8, true);

    // Test first and last change number, add a new change, do not
    // search again the ECL, but search for first and last
    ECLCompatTestLimitsAndAdd(1, 8, 4);

    // Test CNIndexDB is purged when replication change log is purged
    final JEChangeNumberIndexDB cnIndexDB = getCNIndexDB();
    cnIndexDB.purgeUpTo(new CSN(Long.MAX_VALUE, 0, 0));
    assertTrue(cnIndexDB.isEmpty());
    ECLPurgeCNIndexDBAfterChangelogClear();

    // Test first and last are updated
    ECLCompatTestLimits(0,0, true);

    // Persistent search in changesOnly mode
    ECLPsearch(true, true);
  }

  @Test(enabled=false, groups="slow", dependsOnMethods = { "PrimaryTest"})
  public void ECLReplicationServerFullTest16() throws Exception
  {
    // Persistent search in init + changes mode
    CSN csn = ECLPsearch(false, true);

    // Test Filter on replication csn
    // TODO: test with optimization when code done.
    ECLFilterOnReplicationCSN(csn);
  }

  /**
   * Verifies that is not possible to read the changelog without the changelog-read privilege
   */
  @Test(enabled=true, dependsOnMethods = { "PrimaryTest"})
  public void ECLChangelogReadPrivilegeTest() throws Exception
  {
    AuthenticationInfo nonPrivilegedUser = new AuthenticationInfo();

    InternalClientConnection conn = new InternalClientConnection(nonPrivilegedUser);
    InternalSearchOperation ico = conn.processSearch("cn=changelog", SearchScope.WHOLE_SUBTREE, "(objectclass=*)");

    assertEquals(ico.getResultCode(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    assertEquals(ico.getErrorMessage().toMessage(), NOTE_SEARCH_CHANGELOG_INSUFFICIENT_PRIVILEGES.get());
  }

  /** No RSDomain created yet => RS only case => ECL is not a supported. */
  @Test(enabled = true)
  public void TestECLIsNotASupportedSuffix() throws Exception
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
      final DN changelogDN = DN.decode("cn=changelog");
      brokers[0] = openReplicationSession(
          changelogDN, 1111, 100, replicationServerPort, brokerSessionTimeout);
      assertTrue(brokers[0].isConnected());
      brokers[1] = openReplicationSession(
          changelogDN, 2222, 100, replicationServerPort, brokerSessionTimeout);
      assertTrue(brokers[1].isConnected());
      brokers[2] = openReplicationSession(
          changelogDN, 3333, 100, replicationServerPort, brokerSessionTimeout);
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
   * Objectives:
   * <ul>
   * <li>Test that everything is ok with changes on 2 suffixes</li>
   * </ul>
   * Procedure:
   * <ul>
   * <li>From 1 remote ECL session,</li>
   * <li>Test simple update to be received from 2 suffixes</li>
   * </ul>
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
          100, replicationServerPort, brokerSessionTimeout);

      server02 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, EMPTY_DN_GENID);

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
      Thread.sleep(500);

      // open ECL broker
      serverECL = openReplicationSession(
          DN.decode("cn=changelog"), 10, 100, replicationServerPort, brokerSessionTimeout);
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

    // root entry returned
    searchOnChangelog("(objectclass=*)", Collections.<String> emptySet(), createCookieControl(""),
        1, ResultCode.SUCCESS, tn);

    debugInfo(tn, "Ending test successfully");
  }

  /**
   * Build a list of controls including the cookie provided.
   * @param cookie The provided cookie.
   * @return The built list of controls.
   */
  private List<Control> createCookieControl(String cookie) throws DirectoryException
  {
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    final Control cookieControl = new ExternalChangelogRequestControl(true, state);
    return newList(cookieControl);
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
    waitOpResult(connection.processAdd(entry), ResultCode.SUCCESS);
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  private void ECLOnPrivateBackend() throws Exception
  {
    String tn = "ECLOnPrivateBackend";
    debugInfo(tn, "Starting test");

    ReplicationBroker server01 = null;
    LDAPReplicationDomain domain = null;
    LDAPReplicationDomain domain2 = null;
    Backend backend2 = null;

    // Use different values than other tests to avoid test interactions in concurrent test runs
    final String backendId2 = tn + 2;
    final DN baseDN2 = DN.decode("o=" + backendId2);
    try
    {
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);
      DomainFakeCfg domainConf = newFakeCfg(TEST_ROOT_DN, SERVER_ID_1, replicationServerPort);
      domain = startNewDomain(domainConf, null, null);

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
      SortedSet<String> replServers = newSortedSet("localhost:" + replicationServerPort);

      DomainFakeCfg domainConf2 = new DomainFakeCfg(baseDN2, 1602, replServers);
      domain2 = startNewDomain(domainConf2, null, null);

      Thread.sleep(1000);
      addEntry(createEntry(baseDN2));

      // Search on ECL from start on all suffixes
      // Expect root entry returned
      String cookie = "";
      searchOnCookieChangelog("(targetDN=*)", cookie, 2, tn, SUCCESS);

      ExternalChangelogDomainCfg eclCfg = new ExternalChangelogDomainFakeCfg(false, null, null);
      domainConf2.setExternalChangelogDomain(eclCfg);
      domain2.applyConfigurationChange(domainConf2);

      // Expect only entry from o=test returned
      searchOnCookieChangelog("(targetDN=*)", cookie, 1, tn, SUCCESS);

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
      remove(domain, domain2);
      removeTestBackend(backend2);
      stop(server01);
    }
    debugInfo(tn, "Ending test successfully");
  }

  /**
   * From embedded ECL Search ECL with 4 messages on 2 suffixes from 2 brokers.
   * Test with a mix of domains, a mix of DSes.
   */
  @Test(enabled=false, dependsOnMethods = { "PrimaryTest"})
  public void TestECLWithTwoDomains() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);


    String tn = "TestECLWithTwoDomains";
    debugInfo(tn, "Starting test");

    ReplicationBroker s1test = null;
    ReplicationBroker s1test2 = null;
    ReplicationBroker s2test = null;
    ReplicationBroker s2test2 = null;

    Backend backend2 = null;
    LDAPReplicationDomain domain1 = null;
    LDAPReplicationDomain domain2 = null;
    try
    {
      backend2 = initializeTestBackend(true, TEST_BACKEND_ID2);

      s1test = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);
      s2test2 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, EMPTY_DN_GENID);
      DomainFakeCfg domainConf1 = newFakeCfg(TEST_ROOT_DN, SERVER_ID_1, replicationServerPort);
      domain1 = startNewDomain(domainConf1, null, null);
      DomainFakeCfg domainConf2 = newFakeCfg(TEST_ROOT_DN2, SERVER_ID_2, replicationServerPort);
      domain2 = startNewDomain(domainConf2, null, null);
      Thread.sleep(500);

      // Produce updates
      long time = TimeThread.getTime();
      int ts = 1;
      CSN csn1 = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn1, tn, 1);

      CSN csn2 = new CSN(time, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest2(s2test2, csn2, tn, 2);

      CSN csn3 = new CSN(time, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest2(s2test2, csn3, tn, 3);

      CSN csn4 = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn4, tn, 4);

      // Changes are :
      //               s1          s2
      // o=test       msg1/msg4
      // o=test2                 msg2/msg2

      // search on 'cn=changelog'
      LDIFWriter ldifWriter = getLDIFWriter();
      String cookie = "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*" + tn + "*)", cookie, 4, tn, SUCCESS);
      cookie = readCookie(searchOp.getSearchEntries(), 2);

      // Now start from last cookie and expect to get ONLY the 4th change
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*)", cookie, 1, tn, SUCCESS);
      cookie = assertContainsAndReadCookie(tn, searchOp.getSearchEntries(), ldifWriter, csn4);

      // Now publishes a new change and search from the previous cookie
      CSN csn5 = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn5, tn, 5);

      // Changes are :
      //               s1         s2
      // o=test       msg1,msg5   msg4
      // o=test2      msg3        msg2

      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*)", cookie, 1,tn, SUCCESS);
      cookie = assertContainsAndReadCookie(tn, searchOp.getSearchEntries(), ldifWriter, csn5);

      cookie = "";
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", cookie, 3, tn, SUCCESS);
      // we expect msg1 + msg4 + msg5
      cookie = assertContainsAndReadCookie(tn, searchOp.getSearchEntries(), ldifWriter, csn1, csn4, csn5);

      // Test startState ("first cookie") of the ECL
      // --
      s1test2 = openReplicationSession(TEST_ROOT_DN2,  1203,
          100, replicationServerPort, brokerSessionTimeout, EMPTY_DN_GENID);
      s2test = openReplicationSession(TEST_ROOT_DN,  1204,
          100, replicationServerPort, brokerSessionTimeout);
      Thread.sleep(500);

      time = TimeThread.getTime();
      CSN csn6 = new CSN(time, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn6, tn, 6);

      CSN csn7 = new CSN(time, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn7, tn, 7);

      CSN csn8 = new CSN(time, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn8, tn, 8);

      CSN csn9 = new CSN(time, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn9, tn, 9);
      Thread.sleep(500);

      final ServerState oldestState = getDomainOldestState(TEST_ROOT_DN);
      assertEquals(oldestState.getCSN(s1test.getServerId()), csn1);
      assertEquals(oldestState.getCSN(s2test.getServerId()), csn7);

      final ServerState oldestState2 = getDomainOldestState(TEST_ROOT_DN2);
      assertEquals(oldestState2.getCSN(s2test2.getServerId()), csn2);
      assertEquals(oldestState2.getCSN(s1test2.getServerId()), csn6);

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
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", cookie, 0, tn,
              PROTOCOL_ERROR);
      final String cookieStr = new MultiDomainServerState(cookie).toString();
      Assertions.assertThat(searchOp.getErrorMessage().toString()).startsWith(
          ERR_INVALID_COOKIE_SYNTAX.get(cookieStr).toString());

      // Test unknown domain in provided cookie
      // This case seems to be very hard to obtain in the real life
      // (how to remove a domain from a RS topology ?)
      // let's do a very quick test here.
      String newCookie = lastCookie + "o=test6:";
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", newCookie, 0,
              tn, UNWILLING_TO_PERFORM);

      // Test missing domain in provided cookie
      newCookie = lastCookie.substring(lastCookie.indexOf(';')+1);
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = searchOnCookieChangelog("(targetDN=*" + tn + "*,o=test)", newCookie, 0,
              tn, UNWILLING_TO_PERFORM);
      String expectedError = ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE
          .get("o=test:;","<"+ newCookie + "o=test:;>").toString();
      assertThat(searchOp.getErrorMessage().toString()).isEqualToIgnoringCase(expectedError);
    }
    finally
    {
      remove(domain1, domain2);
      removeTestBackend(backend2);
      stop(s1test2, s2test, s1test, s2test2);
    }
    debugInfo(tn, "Ending test successfully");
  }

  private String readCookie(List<SearchResultEntry> entries, int i)
  {
    SearchResultEntry entry = entries.get(i);
    return entry.getAttribute("changelogcookie").get(0).iterator().next().toString();
  }

  private ServerState getDomainOldestState(DN baseDN)
  {
    return replicationServer.getReplicationServerDomain(baseDN).getOldestState();
  }

  private String assertContainsAndReadCookie(String tn, List<SearchResultEntry> entries,
      LDIFWriter ldifWriter, CSN... csns) throws Exception
  {
    assertThat(getCSNs(entries)).containsExactly(csns);
    debugAndWriteEntries(ldifWriter, entries, tn);
    return readCookie(entries, csns.length - 1);
  }

  private List<CSN> getCSNs(List<SearchResultEntry> entries)
  {
    List<CSN> results = new ArrayList<CSN>(entries.size());
    for (SearchResultEntry entry : entries)
    {
      results.add(new CSN(getAttributeValue(entry, "replicationCSN")));
    }
    return results;
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
      String cookie, int expectedNbEntries, String testName, ResultCode expectedResultCode)
      throws Exception
  {
    debugInfo(testName, "Search with cookie=[" + cookie + "] filter=[" + filterString + "]");
    return searchOnChangelog(filterString, ALL_ATTRIBUTES, createCookieControl(cookie),
        expectedNbEntries, expectedResultCode, testName);
  }

  private InternalSearchOperation searchOnChangelog(String filterString,
      int expectedNbEntries, String testName, ResultCode expectedResultCode)
      throws Exception
  {
    debugInfo(testName, " Search: " + filterString);
    return searchOnChangelog(filterString, ALL_ATTRIBUTES, NO_CONTROL,
        expectedNbEntries, expectedResultCode, testName);
  }

  private InternalSearchOperation searchOnChangelog(String filterString,
      Set<String> attributes, List<Control> controls, int expectedNbEntries,
      ResultCode expectedResultCode, String testName) throws Exception
  {
    InternalSearchOperation op = null;
    int cnt = 0;
    do
    {
      Thread.sleep(10);
      op = connection.processSearch(
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
      cnt++;
    }
    while (cnt < 300 // wait at most 3s
        && op.getSearchEntries().size() != expectedNbEntries);
    final List<SearchResultEntry> entries = op.getSearchEntries();
    assertThat(entries).hasSize(expectedNbEntries);
    debugAndWriteEntries(getLDIFWriter(), entries, testName);
    waitOpResult(op, expectedResultCode);
    return op;
  }

  /** Test ECL content after replication changelogDB trimming */
  @Test(enabled=false, dependsOnMethods = { "PrimaryTest"})
  public void testECLAfterChangelogTrim() throws Exception
  {
    String testName = "testECLAfterChangelogTrim";
    debugInfo(testName, "Starting test");

    ReplicationBroker server01 = null;
    try
    {
      // ---
      // 1. Populate the changelog and read the cookie

      // Creates broker on o=test
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);

      final CSN[] csns = generateCSNs(3, SERVER_ID_1);
      publishDeleteMsgInOTest(server01, csns[0], testName, 1);

      Thread.sleep(1000);

      // Test that last cookie has been updated
      String cookieNotEmpty = readLastCookie();
      debugInfo(testName, "Store cookie not empty=\"" + cookieNotEmpty + "\"");

      publishDeleteMsgInOTest(server01, csns[1], testName, 2);
      publishDeleteMsgInOTest(server01, csns[2], testName, 3);

      // ---
      // 2. Now set up a very short purge delay on the replication changelogs
      // so that this test can play with a trimmed changelog.
      replicationServer.getChangelogDB().setPurgeDelay(1);

      // ---
      // 3. Assert that a request with an empty cookie returns nothing
      // since replication changelog has been trimmed
      String cookie= "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*)", cookie, 0, testName, SUCCESS);

      // ---
      // 4. Assert that a request with the current last cookie returns nothing
      // since replication changelog has been trimmed
      cookie = readLastCookie();
      debugInfo(testName, "2. Search with last cookie=" + cookie + "\"");
      searchOp = searchOnCookieChangelog("(targetDN=*)", cookie, 0, testName, SUCCESS);

      // ---
      // 5. Assert that a request with an "old" cookie - one that refers to
      //    changes that have been removed by the replication changelog trimming
      //    returns the appropriate error.
      debugInfo(testName, "d1 trimdate" + getDomainOldestState(TEST_ROOT_DN));
      debugInfo(testName, "d2 trimdate" + getDomainOldestState(TEST_ROOT_DN2));
      searchOp = searchOnCookieChangelog("(targetDN=*)", cookieNotEmpty, 0, testName, UNWILLING_TO_PERFORM);
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
    debugInfo(testName, "Ending test successfully");
  }

  /** Test ECL content after a domain has been removed. */
  @Test(enabled=true, dependsOnMethods = { "PrimaryTest"})
  public void testECLAfterDomainIsRemoved() throws Exception
  {
    String testName = "testECLAfterDomainIsRemoved";
    debugInfo(testName, "Starting test");

    ReplicationBroker server01 = null;
    try
    {
      // ---
      // 1. Populate the changelog and read the cookie

      // Creates server broker on o=test
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1, 100, replicationServerPort, brokerSessionTimeout);

      final CSN[] csns = generateCSNs(3, SERVER_ID_1);
      publishDeleteMsgInOTest(server01, csns[0], testName, 1);

      Thread.sleep(1000);

      // Test that last cookie has been updated
      String cookieNotEmpty = readLastCookie();
      debugInfo(testName, "Store cookie not empty=\"" + cookieNotEmpty + "\"");

      publishDeleteMsgInOTest(server01, csns[1], testName, 2);
      publishDeleteMsgInOTest(server01, csns[2], testName, 3);

      // ---
      // 2. Now remove the domain by sending a reset message
      ResetGenerationIdMsg msg = new ResetGenerationIdMsg(23657);
      server01.publish(msg);

      // ---
      // 3. Assert that a request with an empty cookie returns nothing
      // since replication changelog has been cleared
      String cookie= "";
      InternalSearchOperation searchOp = null;
      searchOnCookieChangelog("(targetDN=*)", cookie, 0, testName, SUCCESS);

      // ---
      // 4. Assert that a request with the current last cookie returns nothing
      // since replication changelog has been cleared
      cookie = readLastCookie();
      debugInfo(testName, "2. Search with last cookie=" + cookie + "\"");
      searchOp = searchOnCookieChangelog("(targetDN=*)", cookie, 0, testName, SUCCESS);

      // ---
      // 5. Assert that a request with an "old" cookie - one that refers to
      //    changes that have been removed by the replication changelog clearing
      //    returns the appropriate error.
      debugInfo(testName, "d1 trimdate" + getDomainOldestState(TEST_ROOT_DN));
      debugInfo(testName, "d2 trimdate" + getDomainOldestState(TEST_ROOT_DN2));
      searchOp = searchOnCookieChangelog("(targetDN=*)", cookieNotEmpty, 0, testName, UNWILLING_TO_PERFORM);
      assertThat(searchOp.getErrorMessage().toString()).contains("unknown replicated domain", TEST_ROOT_DN_STRING.toString());
    }
    finally
    {
      stop(server01);
    }
    debugInfo(testName, "Ending test successfully");
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
        cookie = getAttributeValue(resultEntry, "lastexternalchangelogcookie");
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
    LDAPReplicationDomain domain = null;
    try
    {
      // Creates brokers on o=test and o=test2
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);
      server02 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout);

      DomainFakeCfg domainConf = newFakeCfg(TEST_ROOT_DN, SERVER_ID_1, replicationServerPort);
      domain = startNewDomain(domainConf, null, null);

      String user1entryUUID = "11111111-1111-1111-1111-111111111111";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";

      final int expectedNbEntries = 4;
      CSN[] csns = generateCSNs(expectedNbEntries, SERVER_ID_1);

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
          Collections.<Attribute> emptyList());
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

      String cookie= "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetdn=*" + tn + "*,o=test)", cookie, expectedNbEntries, tn, SUCCESS);

      // test 4 entries returned
      final String[] cookies = new String[expectedNbEntries];
      for (int j = 0; j < cookies.length; j++)
      {
        cookies[j] = "o=test:" + csns[j] + ";";
      }

      int i=0;
      for (SearchResultEntry resultEntry : searchOp.getSearchEntries())
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
          checkLDIF(resultEntry, "changes",
              "objectClass: domain",
              "objectClass: top",
              "entryUUID: 11111111-1111-1111-1111-111111111111");
          checkValue(resultEntry,"targetentryuuid",user1entryUUID);
        } else if (i==3)
        {
          // check the MOD entry has the right content
          checkValue(resultEntry, "changetype", "modify");
          checkLDIF(resultEntry, "changes",
              "replace: description",
              "description: new value",
              "-");
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
      assertThat(getControls(result)).containsExactly(cookies);
    }
    finally
    {
      remove(domain);
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

  private List<String> getControls(String resultString) throws Exception
  {
    final BufferedReader br = new BufferedReader(new StringReader(resultString));
    final List<String> ctrlList = new ArrayList<String>();
    while (true)
    {
      final String s = br.readLine();
      if (s == null)
      {
        break;
      }
      if (!s.startsWith("#"))
      {
        continue;
      }
      final String[] a = s.split(": ");
      if (a.length != 2)
      {
        break;
      }
      ctrlList.add(a[1]);
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
    assertEquals(0, retVal, "Returned error: " + eStream);
    return oStream.toString();
  }

  private static void checkValue(Entry entry, String attrName, String expectedValue)
  {
    assertFalse(expectedValue.contains("\n"),
        "Use checkLDIF() method for asserting on value: \"" + expectedValue + "\"");
    final String actualValue = getAttributeValue(entry, attrName);
    assertThat(actualValue)
        .as("In entry " + entry + " incorrect value for attr '" + attrName + "'")
        .isEqualToIgnoringCase(expectedValue);
  }

  /**
   * Asserts the attribute value as LDIF to ignore lines ordering.
   */
  private static void checkLDIF(Entry entry, String attrName, String... expectedLDIFLines)
  {
    final String actualVal = getAttributeValue(entry, attrName);
    final Set<Set<String>> actual = toLDIFEntries(actualVal.split("\n"));
    final Set<Set<String>> expected = toLDIFEntries(expectedLDIFLines);
    assertThat(actual)
        .as("In entry " + entry + " incorrect value for attr '" + attrName + "'")
        .isEqualTo(expected);
  }

  /**
   * Returns a data structure allowing to compare arbitrary LDIF lines. The
   * algorithm splits LDIF entries on lines containing only a dash ("-"). It
   * then returns LDIF entries and lines in an LDIF entry in ordering
   * insensitive data structures.
   * <p>
   * Note: a last line with only a dash ("-") is significant. i.e.:
   *
   * <pre>
   * <code>
   * boolean b = toLDIFEntries("-").equals(toLDIFEntries()));
   * System.out.println(b); // prints "false"
   * </code>
   * </pre>
   */
  private static Set<Set<String>> toLDIFEntries(String... ldifLines)
  {
    final Set<Set<String>> results = new HashSet<Set<String>>();
    Set<String> ldifEntryLines = new HashSet<String>();
    for (String ldifLine : ldifLines)
    {
      if (!"-".equals(ldifLine))
      {
        // same entry keep adding
        ldifEntryLines.add(ldifLine);
      }
      else
      {
        // this is a new entry
        results.add(ldifEntryLines);
        ldifEntryLines = new HashSet<String>();
      }
    }
    results.add(ldifEntryLines);
    return results;
  }

  private static String getAttributeValue(Entry entry, String attrName)
  {
    List<Attribute> attrs = entry.getAttribute(attrName.toLowerCase());
    if (attrs == null)
    {
      return null;
    }
    Attribute a = attrs.iterator().next();
    AttributeValue av = a.iterator().next();
    return av.toString();
  }

  private static void checkValues(Entry entry, String attrName,
      Set<String> expectedValues)
  {
    final Set<String> values = new HashSet<String>();
    for (Attribute a : entry.getAttribute(attrName))
    {
      for (AttributeValue av : a)
      {
        values.add(av.toString());
      }
    }
    assertThat(values)
      .as("In entry " + entry + " incorrect values for attr '" + attrName + "'")
      .isEqualTo(expectedValues);
  }

  /**
   * Test persistent search
   */
  private CSN ECLPsearch(boolean changesOnly, boolean compatMode) throws Exception
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

    try
    {
      // Create broker on suffix
      ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);

      CSN[] csns = generateCSNs(2, SERVER_ID_1);

      // Produce update on this suffix
      DeleteMsg delMsg =
          newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csns[0],
            "11111111-1112-1113-1114-111111111114");
      debugInfo(tn, " publishing " + delMsg.getCSN());
      server01.publish(delMsg);
      Thread.sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      String cookie = "";
      List<Control> controls = createCookieControl(cookie);
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
      LDAPMessage message = new LDAPMessage(2, searchRequest, controls);
      w.writeMessage(message);
      Thread.sleep(500);

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
              assertSuccessful(message);
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
      Thread.sleep(1000);

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
          assertSuccessful(message);
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      Thread.sleep(1000);

      // Check we received change 2
      checkAttributeValue(searchResultEntry, "targetDN", expectedDn);
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
        message = new LDAPMessage(2, searchRequest, createCookieControl(""));
        w.writeMessage(message);

        searchesDone=0;
        searchEntries = 0;
        while ((searchesDone==0) && (message = r.readMessage()) != null)
        {
          debugInfo(tn, "ACI test : message returned " +
              message.getProtocolOpType() + message);
          switch (message.getProtocolOpType())
          {
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
            //assertTrue(false, "Unexpected entry returned in ACI test of " + tn + searchResultEntry);
            searchEntries++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
            searchReferences++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
            assertSuccessful(message);
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
      while (!s.isClosed())
        Thread.sleep(100);

      return csn;
    }
    finally
    {
      debugInfo(tn, "Ends test successfully");
    }
  }

  private void checkAttributeValue(SearchResultEntryProtocolOp entry,
      String attrType, String expectedDN)
  {
    for (LDAPAttribute a : entry.getAttributes())
    {
      if (attrType.equalsIgnoreCase(a.getAttributeType()))
      {
        for (ByteString av : a.getValues())
        {
          assertThat(av.toString())
              .as("Wrong entry returned by psearch")
              .isEqualToIgnoringCase(expectedDN);
          return;
        }
      }
    }
    fail();
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
   * Test parallel simultaneous persistent search with different filters.
   */
  @Test(enabled = false, groups = "slow", dependsOnMethods = { "PrimaryTest" })
  public void FullTestSimultaneousPersistentSearches() throws Exception
  {
    String tn = "FullTestSimultaneousPersistentSearches";
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
      DomainFakeCfg config1 = newFakeCfg(TEST_ROOT_DN, SERVER_ID_1, replicationServerPort);
      config1.setChangetimeHeartbeatInterval(100); // ms
      server01 = openReplicationSession(config1, replicationServerPort,
              brokerSessionTimeout, getGenerationId(TEST_ROOT_DN));

      // Create broker on o=test2
      DomainFakeCfg config2 = newFakeCfg(TEST_ROOT_DN2, SERVER_ID_2, replicationServerPort);
      config2.setChangetimeHeartbeatInterval(100); //ms
      server02 = openReplicationSession(config2, replicationServerPort,
              brokerSessionTimeout, EMPTY_DN_GENID);

      int ts = 1;
      // Produce update 1
      CSN csn1 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_1);
      DeleteMsg delMsg1 =
        newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, csn1,
            "11111111-1111-1111-1111-111111111111");
      debugInfo(tn, " publishing " + delMsg1);
      server01.publish(delMsg1);
      Thread.sleep(500); // let's be sure the message is in the RS

      // Produce update 2
      CSN csn2 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_2);
      DeleteMsg delMsg2 =
        newDeleteMsg("uid=" + tn + "2," + TEST_ROOT_DN_STRING2, csn2,
            "22222222-2222-2222-2222-222222222222");
      debugInfo(tn, " publishing " + delMsg2);
      server02.publish(delMsg2);
      Thread.sleep(500); // let's be sure the message is in the RS

      // Produce update 3
      CSN csn3 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_2);
      DeleteMsg delMsg3 =
        newDeleteMsg("uid=" + tn + "3," + TEST_ROOT_DN_STRING2, csn3,
            "33333333-3333-3333-3333-333333333333");
      debugInfo(tn, " publishing " + delMsg3);
      server02.publish(delMsg3);
      Thread.sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      String cookie = "";
      List<Control> controls = createCookieControl(cookie);
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
      Thread.sleep(500);

      message = new LDAPMessage(2, searchRequest2, controls);
      w2.writeMessage(message);
      Thread.sleep(500);

      message = new LDAPMessage(2, searchRequest3, controls);
      w3.writeMessage(message);
      Thread.sleep(500);

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
              SearchResultEntryProtocolOp searchResultEntry =
                  message.getSearchResultEntryProtocolOp();
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
              assertSuccessful(message);
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
              SearchResultEntryProtocolOp searchResultEntry =
                  message.getSearchResultEntryProtocolOp();
              searchEntries++;
              checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                  (compatMode?"10":"0"));
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              assertSuccessful(message);
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
              searchEntries++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              assertSuccessful(message);
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
      Thread.sleep(500);
      debugInfo(tn, delMsg11.getCSN() + " published additionally ");

      // Produces additional change
      CSN csn12 = new CSN(TimeThread.getTime(), 12, SERVER_ID_2);
      String expectedDn12 = "uid=" + tn + "12," +  TEST_ROOT_DN_STRING2;
      DeleteMsg delMsg12 = newDeleteMsg(expectedDn12, csn12,
         "55555555-5555-5555-5555-555555555555");
      debugInfo(tn, " publishing " + delMsg12 );
      server02.publish(delMsg12);
      Thread.sleep(500);
      debugInfo(tn, delMsg12.getCSN()  + " published additionally ");

      // Produces additional change
      CSN csn13 = new CSN(TimeThread.getTime(), 13, SERVER_ID_2);
      String expectedDn13 = "uid=" + tn + "13," +  TEST_ROOT_DN_STRING2;
      DeleteMsg delMsg13 = newDeleteMsg(expectedDn13, csn13,
         "66666666-6666-6666-6666-666666666666");
      debugInfo(tn, " publishing " + delMsg13);
      server02.publish(delMsg13);
      Thread.sleep(500);
      debugInfo(tn, delMsg13.getCSN()  + " published additionally ");

      // wait 11
      searchEntries = 0;
      message = null;
      while (searchEntries < 1 && (message = r1.readMessage()) != null)
      {
        debugInfo(tn, "Search 11 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          assertSuccessful(message);
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      Thread.sleep(1000);
      debugInfo(tn, "Search 1 successfully receives additional changes");

      // wait 12 & 13
      searchEntries = 0;
      message = null;
      while (searchEntries < 2 && (message = r2.readMessage()) != null)
      {
        debugInfo(tn, "psearch search 12 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          assertSuccessful(message);
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      Thread.sleep(1000);
      debugInfo(tn, "Search 2 successfully receives additional changes");

      // wait 11 & 12 & 13
      searchEntries = 0;
      SearchResultEntryProtocolOp searchResultEntry = null;
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
          assertSuccessful(message);
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      Thread.sleep(1000);

      // Check we received change 13
      checkAttributeValue(searchResultEntry, "targetDN", expectedDn13);
      debugInfo(tn, "Search 3 successfully receives additional changes");
    }
    finally
    {
      stop(server01, server02);
      waitForClose(s1, s2, s3);
    }
    debugInfo(tn, "Ends test successfully");
  }

  private void assertSuccessful(LDAPMessage message)
  {
    SearchResultDoneProtocolOp doneOp = message.getSearchResultDoneProtocolOp();
    assertEquals(doneOp.getResultCode(), ResultCode.SUCCESS.getIntValue(),
        doneOp.getErrorMessage().toString());
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
          Thread.sleep(100);
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
  public void clearReplicationDb() throws Exception
  {
    clearChangelogDB(replicationServer);
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

  /**
   * FIXME this test actually tests nothing: there are no asserts.
   */
  @Test(enabled = true, dependsOnMethods = { "PrimaryTest" })
  public void testChangeTimeHeartbeat() throws Exception
  {
    String tn = "testChangeTimeHeartbeat";
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
          100, replicationServerPort, brokerSessionTimeout);
      s2test2 = openReplicationSession(TEST_ROOT_DN2, SERVER_ID_2,
          100, replicationServerPort, brokerSessionTimeout, EMPTY_DN_GENID);
      Thread.sleep(500);

      // Produce updates
      long time = TimeThread.getTime();
      int ts = 1;
      CSN csn1 = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn1, tn, 1);

      CSN csn2 = new CSN(time, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest(s2test2, csn2, tn, 2);

      CSN csn3 = new CSN(time, ts++, s2test2.getServerId());
      publishDeleteMsgInOTest(s2test2, csn3, tn, 3);

      CSN csn4 = new CSN(time, ts++, s1test.getServerId());
      publishDeleteMsgInOTest(s1test, csn4, tn, 4);
      Thread.sleep(500);

      // --
      s1test2 = openReplicationSession(TEST_ROOT_DN2,  1203,
          100, replicationServerPort, brokerSessionTimeout, EMPTY_DN_GENID);
      s2test = openReplicationSession(TEST_ROOT_DN,  1204,
          100, replicationServerPort, brokerSessionTimeout);
      Thread.sleep(500);

      // Test startState ("first cookie") of the ECL
      time = TimeThread.getTime();
      CSN csn6 = new CSN(time, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn6, tn, 6);

      CSN csn7 = new CSN(time, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn7, tn, 7);

      CSN csn8 = new CSN(time, ts++, s1test2.getServerId());
      publishDeleteMsgInOTest2(s1test2, csn8, tn, 8);

      CSN csn9 = new CSN(time, ts++, s2test.getServerId());
      publishDeleteMsgInOTest(s2test, csn9, tn, 9);
      Thread.sleep(500);

      ReplicationServerDomain rsd1 = replicationServer.getReplicationServerDomain(TEST_ROOT_DN);
			debugInfo(tn, rsd1.getBaseDN() + " LatestServerState=" + rsd1.getLatestServerState());
      // FIXME:ECL Enable this test by adding an assert on the right value

      ReplicationServerDomain rsd2 = replicationServer.getReplicationServerDomain(TEST_ROOT_DN2);
			debugInfo(tn, rsd2.getBaseDN() + " LatestServerState=" + rsd2.getLatestServerState());
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

    final InternalSearchOperation op = connection.processSearch(
        "cn=changelog", SearchScope.WHOLE_SUBTREE, "(objectclass=*)");
    assertEquals(op.getResultCode(), ResultCode.SUCCESS, op.getErrorMessage().toString());
    assertEquals(op.getEntriesSent(), 1, "The root entry should have been returned");
    debugInfo(tn, "Ending test successfully");
  }

  private CSN ECLCompatWriteReadAllOps(long firstChangeNumber) throws Exception
  {
    String tn = "ECLCompatWriteReadAllOps/" + firstChangeNumber;
    debugInfo(tn, "Starting test\n\n");
    LDAPReplicationDomain domain = null;
    try
    {
      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);

      DomainFakeCfg domainConf = newFakeCfg(TEST_ROOT_DN, SERVER_ID_1, replicationServerPort);
      domain = startNewDomain(domainConf, null, null);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";

      CSN[] csns = generateCSNs(4, SERVER_ID_1);

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
          csns[1],
          entry.getDN(),
          user1entryUUID,
          baseUUID,
          entry.getObjectClassAttribute(),
          entry.getAttributes(),
          Collections.<Attribute> emptyList());
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

      String filter = "(targetdn=*" + tn + "*,o=test)";
      InternalSearchOperation searchOp = searchOnChangelog(filter, 4, tn, SUCCESS);

      // test 4 entries returned
      final LDIFWriter ldifWriter = getLDIFWriter();
      assertFourEntries(searchOp.getSearchEntries(), firstChangeNumber, tn,
          ldifWriter, user1entryUUID, csns);

      stop(server01);

      // Test with filter on change number
      filter =
          "(&(targetdn=*" + tn + "*,o=test)"
            + "(&(changenumber>=" + firstChangeNumber + ")"
              + "(changenumber<=" + (firstChangeNumber + 3) + ")))";
      searchOp = searchOnChangelog(filter, 4, tn, SUCCESS);

      assertFourEntries(searchOp.getSearchEntries(), firstChangeNumber, tn,
          ldifWriter, user1entryUUID, csns);
      assertThat(searchOp.getSearchEntries()).hasSize(csns.length);
      return csns[1];
    }
    finally
    {
      remove(domain);
      debugInfo(tn, "Ending test with success");
    }
  }

  private void assertFourEntries(List<SearchResultEntry> entries,
      long firstChangeNumber, String tn, LDIFWriter ldifWriter,
      String user1entryUUID, CSN... csns) throws Exception
  {
    debugAndWriteEntries(ldifWriter, entries, tn);
    assertThat(entries).hasSize(4);

    int i = -1;
    // check the DEL entry has the right content
    final SearchResultEntry delEntry = entries.get(++i);
    checkValue(delEntry, "changetype", "delete");
    commonAssert(delEntry, user1entryUUID, firstChangeNumber, i, tn, csns[i]);
    checkValue(delEntry, "targetuniqueid", user1entryUUID);

    // check the ADD entry has the right content
    final SearchResultEntry addEntry = entries.get(++i);
    checkValue(addEntry, "changetype", "add");
    commonAssert(addEntry, user1entryUUID, firstChangeNumber, i, tn, csns[i]);
    checkLDIF(addEntry, "changes",
        "objectClass: domain",
        "objectClass: top",
        "entryUUID: " + user1entryUUID);

    // check the MOD entry has the right content
    final SearchResultEntry modEntry = entries.get(++i);
    checkValue(modEntry, "changetype", "modify");
    commonAssert(modEntry, user1entryUUID, firstChangeNumber, i, tn, csns[i]);
    checkLDIF(modEntry, "changes",
        "replace: description",
        "description: new value",
        "-");

    // check the MODDN entry has the right content
    final SearchResultEntry moddnEntry = entries.get(++i);
    checkValue(moddnEntry, "changetype", "modrdn");
    commonAssert(moddnEntry, user1entryUUID, firstChangeNumber, i, tn, csns[i]);
    checkValue(moddnEntry, "newrdn", "uid=" + tn + "new4");
    checkValue(moddnEntry, "newsuperior", TEST_ROOT_DN_STRING2);
    checkValue(moddnEntry, "deleteoldrdn", "true");
  }

  private void commonAssert(SearchResultEntry resultEntry, String entryUUID,
      long firstChangeNumber, int i, String tn, CSN csn)
  {
    final long changeNumber = firstChangeNumber + i;
    final String targetDN = "uid=" + tn + (i + 1) + "," + TEST_ROOT_DN_STRING;

    assertDNEquals(resultEntry, changeNumber);
    checkValue(resultEntry, "changenumber", String.valueOf(changeNumber));
    checkValue(resultEntry, "targetentryuuid", entryUUID);
    checkValue(resultEntry, "replicaidentifier", String.valueOf(SERVER_ID_1));
    checkValue(resultEntry, "replicationcsn", csn.toString());
    checkValue(resultEntry, "changelogcookie", "o=test:" + csn + ";");
    checkValue(resultEntry, "targetdn", targetDN);
  }

  private void assertDNEquals(SearchResultEntry resultEntry, long changeNumber)
  {
    String actualDN = resultEntry.getDN().toNormalizedString();
    String expectedDN = "changenumber=" + changeNumber + ",cn=changelog";
    assertThat(actualDN).isEqualToIgnoringCase(expectedDN);
  }

  private void ECLCompatReadFrom(long firstChangeNumber, Object csn) throws Exception
  {
    String tn = "ECLCompatReadFrom/" + firstChangeNumber;
    debugInfo(tn, "Starting test\n\n");

    // Creates broker on o=test
    ReplicationBroker server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
            100, replicationServerPort, brokerSessionTimeout);

    String user1entryUUID = "11111111-1112-1113-1114-111111111115";

    String filter = "(changenumber=" + firstChangeNumber + ")";
    InternalSearchOperation searchOp = searchOnChangelog(filter, 1, tn, SUCCESS);

    // check the entry has the right content
    SearchResultEntry resultEntry = searchOp.getSearchEntries().get(0);
    assertTrue("changenumber=6,cn=changelog".equalsIgnoreCase(resultEntry.getDN().toNormalizedString()));
    checkValue(resultEntry, "replicationcsn", csn.toString());
    checkValue(resultEntry, "replicaidentifier", String.valueOf(SERVER_ID_1));
    checkValue(resultEntry, "changetype", "add");
    checkValue(resultEntry, "changelogcookie", "o=test:" + csn + ";");
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
            replicationServerPort, brokerSessionTimeout);

    String filter = "(changenumber=" + firstChangeNumber + ")";
    InternalSearchOperation searchOp = searchOnChangelog(filter, 1, tn, SUCCESS);

    // Just verify that no entry contains the ChangeLogCookie control
    List<Control> controls = searchOp.getSearchEntries().get(0).getControls();
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
  private void ECLCompatReadFromTo(int firstChangeNumber, int lastChangeNumber) throws Exception
  {
    String tn = "ECLCompatReadFromTo/" + firstChangeNumber + "/" + lastChangeNumber;
    debugInfo(tn, "Starting test\n\n");

    String filter =
        "(&(changenumber>=" + firstChangeNumber + ")" + "(changenumber<=" + lastChangeNumber + "))";
    final int expectedNbEntries = lastChangeNumber - firstChangeNumber + 1;
    searchOnChangelog(filter, expectedNbEntries, tn, SUCCESS);

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode providing an unknown change number.
   */
  private void ECLCompatBadSeqnum() throws Exception
  {
    String tn = "ECLCompatBadSeqnum";
    debugInfo(tn, "Starting test\n\n");

    searchOnChangelog("(changenumber=1000)", 0, tn, SUCCESS);

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode providing an unknown change number.
   */
  private void ECLFilterOnReplicationCSN(CSN csn) throws Exception
  {
    String tn = "ECLFilterOnReplicationCsn";
    debugInfo(tn, "Starting test\n\n");

    String filter = "(replicationcsn=" + csn + ")";
    InternalSearchOperation searchOp = searchOnChangelog(filter, 1, tn, SUCCESS);

    // check the DEL entry has the right content
    SearchResultEntry resultEntry = searchOp.getSearchEntries().get(0);
    checkValue(resultEntry, "replicationcsn", csn.toString());
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
   * Put a short purge delay to the CNIndexDB, clear the changelogDB, expect the
   * CNIndexDB to be purged accordingly.
   */
  private void ECLPurgeCNIndexDBAfterChangelogClear() throws Exception
  {
    String tn = "ECLPurgeCNIndexDBAfterChangelogClear";
    debugInfo(tn, "Starting test\n\n");

    final JEChangeNumberIndexDB cnIndexDB = getCNIndexDB();
    assertEquals(cnIndexDB.count(), 8);
    replicationServer.getChangelogDB().setPurgeDelay(1000);

    clearChangelogDB(replicationServer);

    // Expect changes purged from the changelog db to be sometimes
    // also purged from the CNIndexDB.
    while (!cnIndexDB.isEmpty())
    {
      debugInfo(tn, "cnIndexDB.count=" + cnIndexDB.count());
      Thread.sleep(200);
    }

    debugInfo(tn, "Ending test with success");
  }

  /**
   * Test that ECL Operational, virtual attributes are not visible outside rootDSE.
   */
  @Test(enabled = true, dependsOnMethods = { "PrimaryTest" })
  public void TestECLOperationalAttributesNotVisibleOutsideRootDSE() throws Exception
  {
    String tn = "TestECLOperationalAttributesNotVisibleOutsideRootDSE";
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
            attributes);
    waitOpResult(searchOp, ResultCode.SUCCESS);

    final List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertThat(entries).hasSize(1);
    debugAndWriteEntries(null, entries, tn);
    for (SearchResultEntry resultEntry : entries)
    {
      assertNull(getAttributeValue(resultEntry, "firstchangenumber"));
      assertNull(getAttributeValue(resultEntry, "lastchangenumber"));
      assertNull(getAttributeValue(resultEntry, "changelog"));
      assertNull(getAttributeValue(resultEntry, "lastExternalChangelogCookie"));
    }

    debugInfo(tn, "Ending test with success");
  }

  private void ECLCompatTestLimits(int expectedFirst, int expectedLast,
      boolean eclEnabled) throws Exception
  {
    String tn = "ECLCompatTestLimits";
    debugInfo(tn, "Starting test\n\n");
    debugInfo(tn, " Search: rootDSE");

    final List<SearchResultEntry> entries =
        assertECLLimits(eclEnabled, expectedFirst, expectedLast);

    debugAndWriteEntries(getLDIFWriter(), entries, tn);
    debugInfo(tn, "Ending test with success");
  }

  private List<SearchResultEntry> assertECLLimits(
      boolean eclEnabled, int expectedFirst, int expectedLast) throws Exception
  {
    AssertionError e = null;

    int count = 0;
    while (count < 30)
    {
      count++;

      try
      {
        final Set<String> attributes = new LinkedHashSet<String>();
        if (expectedFirst > 0)
          attributes.add("firstchangenumber");
        attributes.add("lastchangenumber");
        attributes.add("changelog");
        attributes.add("lastExternalChangelogCookie");

        final InternalSearchOperation searchOp = searchOnRootDSE(attributes);
        final List<SearchResultEntry> entries = searchOp.getSearchEntries();
        assertThat(entries).hasSize(1);

        final SearchResultEntry resultEntry = entries.get(0);
        if (eclEnabled)
        {
          if (expectedFirst > 0)
            checkValue(resultEntry, "firstchangenumber", String.valueOf(expectedFirst));
          checkValue(resultEntry, "lastchangenumber", String.valueOf(expectedLast));
          checkValue(resultEntry, "changelog", String.valueOf("cn=changelog"));
          assertNotNull(getAttributeValue(resultEntry, "lastExternalChangelogCookie"));
        }
        else
        {
          if (expectedFirst > 0)
            assertNull(getAttributeValue(resultEntry, "firstchangenumber"));
          assertNull(getAttributeValue(resultEntry, "lastchangenumber"));
          assertNull(getAttributeValue(resultEntry, "changelog"));
          assertNull(getAttributeValue(resultEntry, "lastExternalChangelogCookie"));
        }
        return entries;
      }
      catch (AssertionError ae)
      {
        // try again to see if changes have been persisted
        e = ae;
      }

      Thread.sleep(100);
    }
    assertNotNull(e);
    throw e;
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
        attributes);
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
    ReplicationBroker server01 = null;
    LDAPReplicationDomain domain = null;
    try
    {
      server01 = openReplicationSession(TEST_ROOT_DN, SERVER_ID_1,
          100, replicationServerPort, brokerSessionTimeout);

      DomainFakeCfg domainConf = newFakeCfg(TEST_ROOT_DN, SERVER_ID_1, replicationServerPort);
      domain = startNewDomain(domainConf, null, null);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";

      // Publish DEL
      CSN csn1 = new CSN(TimeThread.getTime(), ts++, SERVER_ID_1);
      DeleteMsg delMsg = newDeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING,
          csn1, user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getCSN());
      Thread.sleep(500);
    }
    finally
    {
      remove(domain);
      stop(server01);
    }

    ECLCompatTestLimits(expectedFirst, expectedLast + 1, true);

    debugInfo(tn, "Ending test with success");
  }

  private JEChangeNumberIndexDB getCNIndexDB()
  {
    return (JEChangeNumberIndexDB) replicationServer.getChangeNumberIndexDB();
  }

  /**
   * Test ECl entry attributes, and their configuration.
   */
  @Test(enabled = true, dependsOnMethods = { "TestWithAndWithoutControl" })
  public void TestECLWithIncludeAttributes() throws Exception
  {
    String tn = "TestECLWithIncludeAttributes";
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

      SortedSet<String> replServers = newSortedSet("localhost:" + replicationServerPort);

      // on o=test2,sid=1702 include attrs set to : 'sn'
      SortedSet<String> eclInclude = newSortedSet("sn", "roomnumber");

      DomainFakeCfg domainConf = new DomainFakeCfg(TEST_ROOT_DN2, 1702, replServers);
      domain2 = startNewDomain(domainConf, eclInclude, eclInclude);

      backend3 = initializeTestBackend(false, backendId3);

      // on o=test3,sid=1703 include attrs set to : 'objectclass'
      eclInclude = newSortedSet("objectclass");

      SortedSet<String> eclIncludeForDeletes = newSortedSet("*");

      domainConf = new DomainFakeCfg(baseDN3, 1703, replServers);
      domain3 = startNewDomain(domainConf, eclInclude, eclIncludeForDeletes);

      // on o=test2,sid=1704 include attrs set to : 'cn'
      eclInclude = newSortedSet("cn");

      domainConf = new DomainFakeCfg(TEST_ROOT_DN2, 1704, replServers);
      domain21 = startNewDomain(domainConf, eclInclude, eclInclude);

      Thread.sleep(1000);

      addEntry(createEntry(TEST_ROOT_DN2));
      addEntry(createEntry(baseDN3));

      Entry uentry1 = TestCaseUtils.entryFromLdifString(
          "dn: cn=Fiona Jensen," + TEST_ROOT_DN_STRING2 + "\n"
          + "objectclass: top\n"
          + "objectclass: person\n"
          + "objectclass: organizationalPerson\n"
          + "objectclass: inetOrgPerson\n"
          + "cn: Fiona Jensen\n"
          + "sn: Jensen\n"
          + "uid: fiona\n"
          + "telephonenumber: 12121212");
      addEntry(uentry1); // add fiona in o=test2

      Entry uentry2 = TestCaseUtils.entryFromLdifString(
          "dn: cn=Robert Hue," + baseDN3 + "\n"
          + "objectclass: top\n"
          + "objectclass: person\n"
          + "objectclass: organizationalPerson\n"
          + "objectclass: inetOrgPerson\n"
          + "cn: Robert Hue\n"
          + "sn: Robby\n"
          + "uid: robert\n"
          + "telephonenumber: 131313");
      addEntry(uentry2); // add robert in o=test3

      // mod 'sn' of fiona (o=test2) with 'sn' configured as ecl-incl-att
      final ModifyOperation modOp1 = connection.processModify(
          uentry1.getDN(), createMods("sn", "newsn"));
      waitOpResult(modOp1, ResultCode.SUCCESS);

      // mod 'telephonenumber' of robert (o=test3)
      final ModifyOperation modOp2 = connection.processModify(
          uentry2.getDN(), createMods("telephonenumber", "555555"));
      waitOpResult(modOp2, ResultCode.SUCCESS);

      // moddn robert (o=test3) to robert2 (o=test3)
      ModifyDNOperation modDNOp = connection.processModifyDN(
          DN.decode("cn=Robert Hue," + baseDN3),
          RDN.decode("cn=Robert Hue2"), true,
          baseDN3);
      waitOpResult(modDNOp, ResultCode.SUCCESS);

      // del robert (o=test3)
      final DeleteOperation delOp = connection.processDelete(DN.decode("cn=Robert Hue2," + baseDN3));
      waitOpResult(delOp, ResultCode.SUCCESS);

      // Search on ECL from start on all suffixes
      String cookie = "";
      InternalSearchOperation searchOp =
          searchOnCookieChangelog("(targetDN=*)", cookie, 8, tn, SUCCESS);

      for (SearchResultEntry resultEntry : searchOp.getSearchEntries())
      {
        String targetdn = getAttributeValue(resultEntry, "targetdn");

        if (targetdn.endsWith("cn=robert hue,o=test3")
            || targetdn.endsWith("cn=robert hue2,o=test3"))
        {
          Entry targetEntry = parseIncludedAttributes(resultEntry, targetdn);

          Set<String> eoc = newSet("person", "inetOrgPerson", "organizationalPerson", "top");
          checkValues(targetEntry, "objectclass", eoc);

          String changeType = getAttributeValue(resultEntry, "changetype");
          if ("delete".equals(changeType))
          {
            // We are using "*" for deletes so should get back 4 attributes.
            assertThat(targetEntry.getAttributes()).hasSize(4);
            checkValue(targetEntry, "uid", "robert");
            checkValue(targetEntry, "cn", "Robert Hue2");
            checkValue(targetEntry, "telephonenumber", "555555");
            checkValue(targetEntry, "sn", "Robby");
          }
          else
          {
            assertThat(targetEntry.getAttributes()).isEmpty();
          }
        }
        else if (targetdn.endsWith("cn=fiona jensen,o=test2"))
        {
          Entry targetEntry = parseIncludedAttributes(resultEntry, targetdn);

          assertThat(targetEntry.getAttributes()).hasSize(2);
          checkValue(targetEntry,"sn","jensen");
          checkValue(targetEntry,"cn","Fiona Jensen");
        }
        checkValue(resultEntry,"changeinitiatorsname", "cn=Internal Client,cn=Root DNs,cn=config");
      }
    }
    finally
    {
      final DN fionaDN = DN.decode("cn=Fiona Jensen," + TEST_ROOT_DN_STRING2);
      waitOpResult(connection.processDelete(fionaDN), ResultCode.SUCCESS);
      waitOpResult(connection.processDelete(TEST_ROOT_DN2), ResultCode.SUCCESS);
      waitOpResult(connection.processDelete(baseDN3), ResultCode.SUCCESS);

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

  private List<Modification> createMods(String attributeName, String valueString)
  {
    Attribute attr = Attributes.create(attributeName, valueString);
    return newList(new Modification(ModificationType.REPLACE, attr));
  }

  private Entry parseIncludedAttributes(SearchResultEntry resultEntry,
      String targetdn) throws Exception
  {
    // Parse includedAttributes as an entry.
    String includedAttributes = getAttributeValue(resultEntry, "includedattributes");
    String[] ldifAttributeLines = includedAttributes.split("\\n");
    String[] ldif = new String[ldifAttributeLines.length + 1];
    System.arraycopy(ldifAttributeLines, 0, ldif, 1, ldifAttributeLines.length);
    ldif[0] = "dn: " + targetdn;
    return TestCaseUtils.makeEntry(ldif);
  }

  private void waitOpResult(Operation operation, ResultCode expectedResult) throws Exception
  {
    int i = 0;
    while (operation.getResultCode() == ResultCode.UNDEFINED
        || operation.getResultCode() != expectedResult)
    {
      Thread.sleep(50);
      i++;
      if (i > 10)
      {
        assertEquals(operation.getResultCode(), expectedResult,
            operation.getErrorMessage().toString());
      }
    }
  }
}
