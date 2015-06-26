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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Pair;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.opends.server.api.Backend;
import org.opends.server.backends.ChangelogBackend.ChangeNumberRange;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.ExternalChangelogDomainFakeCfg;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.file.ECLEnabledDomainPredicate;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ChangelogBackendTestCase extends ReplicationTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String USER1_ENTRY_UUID = "11111111-1111-1111-1111-111111111111";
  private static final long CHANGENUMBER_ZERO = 0L;

  private static final int SERVER_ID_1 = 1201;
  private static final int SERVER_ID_2 = 1202;

  private static final String TEST_BACKEND_ID2 = "test2";
  private static final String TEST_BACKEND_ID3 = "test3";
  private static final String TEST_ROOT_DN_STRING2 = "o=" + TEST_BACKEND_ID2;
  private static final String TEST_ROOT_DN_STRING3 = "o=" + TEST_BACKEND_ID3;
  private static DN DN_OTEST;
  private static DN DN_OTEST2;
  private static DN DN_OTEST3;

  private final int brokerSessionTimeout = 5000;
  private final int maxWindow = 100;

  /** The replicationServer that will be used in this test. */
  private ReplicationServer replicationServer;

  /** The port of the replicationServer. */
  private int replicationServerPort;

  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
    DN_OTEST = DN.valueOf(TEST_ROOT_DN_STRING);
    DN_OTEST2 = DN.valueOf(TEST_ROOT_DN_STRING2);
    DN_OTEST3 = DN.valueOf(TEST_ROOT_DN_STRING3);

    // This test suite depends on having the schema available.
    configureReplicationServer();
  }

  @Override
  @AfterClass
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    remove(replicationServer);
    replicationServer = null;

    paranoiaCheck();
  }

  @AfterMethod
  public void clearReplicationDb() throws Exception
  {
    clearChangelogDB(replicationServer);
  }

  /** Configure a replicationServer for test. */
  private void configureReplicationServer() throws Exception
  {
    replicationServerPort = findFreePort();

    ReplServerFakeConfiguration config = new ReplServerFakeConfiguration(
          replicationServerPort,
          "ChangelogBackendTestDB",
          replicationDbImplementation,
          0,         // purge delay
          71,        // server id
          0,         // queue size
          maxWindow, // window size
          null       // servers
    );
    config.setComputeChangeNumber(true);
    replicationServer = new ReplicationServer(config, new DSRSShutdownSync(), new ECLEnabledDomainPredicate()
    {
      @Override
      public boolean isECLEnabledDomain(DN baseDN)
      {
        return baseDN.toString().startsWith("o=test");
      }
    });
    debugInfo("configure", "ReplicationServer created:" + replicationServer);
  }

  /** Enable replication on provided domain DN and serverid, using provided port. */
  private Pair<ReplicationBroker, LDAPReplicationDomain> enableReplication(DN domainDN, int serverId,
      int replicationPort, int timeout) throws Exception
  {
    ReplicationBroker broker = openReplicationSession(domainDN, serverId, 100, replicationPort, timeout);
    DomainFakeCfg domainConf = newFakeCfg(domainDN, serverId, replicationPort);
    LDAPReplicationDomain replicationDomain = startNewReplicationDomain(domainConf, null, null);
    return Pair.of(broker, replicationDomain);
  }

  /** Start a new replication domain on the directory server side. */
  private LDAPReplicationDomain startNewReplicationDomain(
      DomainFakeCfg domainConf, SortedSet<String> eclInclude, SortedSet<String> eclIncludeForDeletes) throws Exception
  {
    domainConf.setExternalChangelogDomain(new ExternalChangelogDomainFakeCfg(true, eclInclude, eclIncludeForDeletes));
    // Set a Changetime heartbeat interval low enough
    // (less than default value that is 1000 ms)
    // for the test to be sure to consider all changes as eligible.
    domainConf.setChangetimeHeartbeatInterval(10);
    LDAPReplicationDomain newDomain = MultimasterReplication.createNewDomain(domainConf);
    newDomain.start();
    return newDomain;
  }

  private void removeReplicationDomains(LDAPReplicationDomain... domains)
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

  @Test
  public void searchInCookieModeOnOneSuffixUsingEmptyCookie() throws Exception
  {
    String test = "EmptyCookie";
    debugInfo(test, "Starting test\n\n");

    final CSN[] csns = generateAndPublishUpdateMsgForEachOperationType(test, true);
    final String[] cookies = buildCookiesFromCsns(csns);

    int nbEntries = 4;
    String cookie = "";
    InternalSearchOperation searchOp =
        searchChangelogUsingCookie("(targetdn=*" + test + "*,o=test)", cookie, SUCCESS, nbEntries, test);

    final List<SearchResultEntry> searchEntries = searchOp.getSearchEntries();
    assertDelEntry(searchEntries.get(0), test + 1, test + "uuid1", CHANGENUMBER_ZERO, csns[0], cookies[0]);
    assertAddEntry(searchEntries.get(1), test + 2, USER1_ENTRY_UUID, CHANGENUMBER_ZERO, csns[1], cookies[1]);
    assertModEntry(searchEntries.get(2), test + 3, test + "uuid3", CHANGENUMBER_ZERO, csns[2], cookies[2]);
    assertModDNEntry(searchEntries.get(3), test + 4, test + "new4", test+"uuid4", CHANGENUMBER_ZERO,
        csns[3], cookies[3]);
    assertResultsContainCookieControl(searchOp, cookies);

    assertChangelogAttributesInRootDSE(true, 1, 4);

    debugInfo(test, "Ending search with success");
  }

  @Test
  public void searchInCookieModeOnOneSuffix() throws Exception
  {
    String test = "CookieOneSuffix";
    debugInfo(test, "Starting test\n\n");
    InternalSearchOperation searchOp = null;

    final CSN[] csns = generateAndPublishUpdateMsgForEachOperationType(test, true);
    final String[] cookies = buildCookiesFromCsns(csns);

    // check querying with cookie of delete entry : should return  3 entries
    int nbEntries = 3;
    searchOp = searchChangelogUsingCookie("(targetdn=*" + test + "*,o=test)", cookies[0], SUCCESS, nbEntries, test);

    List<SearchResultEntry> searchEntries = searchOp.getSearchEntries();
    assertAddEntry(searchEntries.get(0), test + 2, USER1_ENTRY_UUID, CHANGENUMBER_ZERO, csns[1], cookies[1]);
    assertModEntry(searchEntries.get(1), test + 3, test + "uuid3", CHANGENUMBER_ZERO, csns[2], cookies[2]);
    assertModDNEntry(searchEntries.get(2), test + 4, test + "new4", test+"uuid4", CHANGENUMBER_ZERO,
        csns[3], cookies[3]);

    // check querying with cookie of add entry : should return 2 entries
    nbEntries = 2;
    searchOp = searchChangelogUsingCookie("(targetdn=*" + test + "*,o=test)", cookies[1], SUCCESS, nbEntries, test);

    // check querying with cookie of mod entry : should return 1 entry
    nbEntries = 1;
    searchOp = searchChangelogUsingCookie("(targetdn=*" + test + "*,o=test)", cookies[2], SUCCESS, nbEntries, test);

    searchEntries = searchOp.getSearchEntries();
    assertModDNEntry(searchEntries.get(0), test + 4, test + "new4", test+"uuid4", CHANGENUMBER_ZERO,
        csns[3], cookies[3]);

    // check querying with cookie of mod dn entry : should return 0 entry
    nbEntries = 0;
    searchOp = searchChangelogUsingCookie("(targetdn=*" + test + "*,o=test)", cookies[3], SUCCESS, nbEntries, test);

    debugInfo(test, "Ending search with success");
  }

  @Test
  public void searchInCookieModeAfterDomainIsRemoved() throws Exception
  {
    String test = "CookieAfterDomainIsRemoved";
    debugInfo(test, "Starting test");

    final CSN[] csns = generateCSNs(3, SERVER_ID_1);

    publishUpdateMessagesInOTest(test, true,
      generateDeleteMsg(DN_OTEST, csns[0], test, 1),
      generateDeleteMsg(DN_OTEST, csns[1], test, 2),
      generateDeleteMsg(DN_OTEST, csns[2], test, 3));

    InternalSearchOperation searchOp = searchChangelogUsingCookie("(targetDN=*)", "", SUCCESS, 3, test);
    String firstCookie = readCookieFromNthEntry(searchOp.getSearchEntries(), 0);

    // remove the domain by sending a reset message
    publishUpdateMessages(test, DN_OTEST, SERVER_ID_1, false, new ResetGenerationIdMsg(23657));

    // replication changelog must have been cleared
    String cookie= "";
    searchChangelogUsingCookie("(targetDN=*)", cookie, SUCCESS, 0, test);

    cookie = readLastCookieFromRootDSE();
    searchChangelogUsingCookie("(targetDN=*)", cookie, SUCCESS, 0, test);

    // search with an old cookie
    searchOp = searchChangelogUsingCookie("(targetDN=*)", firstCookie, UNWILLING_TO_PERFORM, 0, test);
    assertThat(searchOp.getErrorMessage().toString()).
      contains("unknown replicated domain", DN_OTEST.toString());

    debugInfo(test, "Ending test successfully");
  }

  /**
   * This test enables a second suffix. It will break all tests using search on
   * one suffix if run before them, so it is necessary to add them as
   * dependencies.
   */
  @Test(enabled=true, dependsOnMethods = {
    "searchInCookieModeOnOneSuffixUsingEmptyCookie",
    "searchInCookieModeOnOneSuffix",
    "searchInCookieModeAfterDomainIsRemoved",
    "searchInChangeNumberModeOnOneSuffixMultipleTimes",
    "searchInChangeNumberModeOnOneSuffix",
    "searchInChangeNumberModeWithInvalidChangeNumber" })
  public void searchInCookieModeOnTwoSuffixes() throws Exception
  {
    String test = "CookieTwoSuffixes";
    debugInfo(test, "Starting test\n\n");
    Backend<?> backendForSecondSuffix = null;
    try
    {
      backendForSecondSuffix = initializeMemoryBackend(true, TEST_BACKEND_ID2);

      // publish 4 changes (2 on each suffix)
      long time = TimeThread.getTime();
      int seqNum = 1;
      CSN csn1 = new CSN(time, seqNum++, SERVER_ID_1);
      CSN csn2 = new CSN(time, seqNum++, SERVER_ID_2);
      CSN csn3 = new CSN(time, seqNum++, SERVER_ID_2);
      CSN csn4 = new CSN(time, seqNum++, SERVER_ID_1);

      publishUpdateMessagesInOTest(test, false, generateDeleteMsg(DN_OTEST, csn1, test, 1));

      publishUpdateMessagesInOTest2(test, false,
        generateDeleteMsg(DN_OTEST2, csn2, test, 2),
        generateDeleteMsg(DN_OTEST2, csn3, test, 3));

      publishUpdateMessagesInOTest(test, false, generateDeleteMsg(DN_OTEST, csn4, test, 4));

      // search on all suffixes using empty cookie
      String cookie = "";
      InternalSearchOperation searchOp =
          searchChangelogUsingCookie("(targetDN=*" + test + "*)", cookie, SUCCESS, 4, test);
      cookie = readCookieFromNthEntry(searchOp.getSearchEntries(), 2);

      // search using previous cookie and expect to get ONLY the 4th change
      LDIFWriter ldifWriter = getLDIFWriter();
      searchOp = searchChangelogUsingCookie("(targetDN=*" + test + "*)", cookie, SUCCESS, 1, test);
      cookie = assertEntriesContainsCSNsAndReadLastCookie(test, searchOp.getSearchEntries(), ldifWriter, csn4);

      // publish a new change on first suffix
      CSN csn5 = new CSN(time, seqNum++, SERVER_ID_1);
      publishUpdateMessagesInOTest(test, false, generateDeleteMsg(DN_OTEST, csn5, test, 5));

      // search using last cookie and expect to get the last change
      searchOp = searchChangelogUsingCookie("(targetDN=*" + test + "*)", cookie, SUCCESS, 1, test);
      assertEntriesContainsCSNsAndReadLastCookie(test, searchOp.getSearchEntries(), ldifWriter, csn5);

      // search on first suffix only, with empty cookie
      cookie = "";
      searchOp = searchChangelogUsingCookie("(targetDN=*" + test + "*,o=test)", cookie, SUCCESS, 3, test);
      cookie = assertEntriesContainsCSNsAndReadLastCookie(test, searchOp.getSearchEntries(), ldifWriter,
          csn1, csn4, csn5);

      // publish 4 more changes (2 on each suffix, on different server ids)
      time = TimeThread.getTime();
      seqNum = 6;
      int serverId11 = 1203;
      int serverId22 = 1204;
      CSN csn6 = new CSN(time, seqNum++, serverId11);
      CSN csn7 = new CSN(time, seqNum++, serverId22);
      CSN csn8 = new CSN(time, seqNum++, serverId11);
      CSN csn9 = new CSN(time, seqNum++, serverId22);

      publishUpdateMessages(test, DN_OTEST2, serverId11, false,
        generateDeleteMsg(DN_OTEST2, csn6, test, 6));

      publishUpdateMessages(test, DN_OTEST, serverId22, false,
        generateDeleteMsg(DN_OTEST, csn7, test, 7));

      publishUpdateMessages(test, DN_OTEST2, serverId11, false,
        generateDeleteMsg(DN_OTEST2, csn8, test, 8));

      publishUpdateMessages(test, DN_OTEST, serverId22, false,
        generateDeleteMsg(DN_OTEST, csn9, test, 9));

      // ensure oldest state is correct for each suffix and for each server id
      isOldestCSNForReplica(DN_OTEST, csn1);
      isOldestCSNForReplica(DN_OTEST, csn7);

      isOldestCSNForReplica(DN_OTEST2, csn2);
      isOldestCSNForReplica(DN_OTEST2, csn6);

      // test last cookie on root DSE
      MultiDomainServerState expectedLastCookie =
          new MultiDomainServerState("o=test:" + csn5 + " " + csn9 + ";o=test2:" + csn3 + " " + csn8 + ";");
      final String lastCookie = readLastCookieFromRootDSE();
      assertThat(lastCookie).isEqualTo(expectedLastCookie.toString());

      // test unknown domain in provided cookie
      // This case seems to be very hard to obtain in the real life
      // (how to remove a domain from a RS topology ?)
      final String cookie2 = lastCookie + "o=test6:";
      debugInfo(test, "Search with bad domain in cookie=" + cookie);
      searchOp = searchChangelogUsingCookie("(targetDN=*" + test + "*,o=test)", cookie2, UNWILLING_TO_PERFORM, 0, test);
      // the last cookie value may not match due to order of domain dn which is not guaranteed, so do not test it
      String expectedError = ERR_RESYNC_REQUIRED_UNKNOWN_DOMAIN_IN_PROVIDED_COOKIE.get("[o=test6]", "")
          .toString().replaceAll("<>", "");
      assertThat(searchOp.getErrorMessage().toString()).startsWith(expectedError);

      // test missing domain in provided cookie
      final String cookie3 = lastCookie.substring(lastCookie.indexOf(';')+1);
      debugInfo(test, "Search with bad domain in cookie=" + cookie);
      searchOp = searchChangelogUsingCookie("(targetDN=*" + test + "*,o=test)", cookie3, UNWILLING_TO_PERFORM, 0, test);
      expectedError = ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE
          .get("o=test:;","<"+ cookie3 + "o=test:;>").toString();
      assertThat(searchOp.getErrorMessage().toString()).isEqualToIgnoringCase(expectedError);
    }
    finally
    {
      removeBackend(backendForSecondSuffix);
    }
  }

  private void isOldestCSNForReplica(DN baseDN, CSN csn) throws Exception
  {
    final ReplicationDomainDB domainDB = replicationServer.getChangelogDB().getReplicationDomainDB();
    CursorOptions options = new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY);
    try (DBCursor<UpdateMsg> cursor = domainDB.getCursorFrom(baseDN, csn.getServerId(), csn, options))
    {
      assertTrue(cursor.next(),
          "Expected to be to find at least one change in replicaDB(" + baseDN + " " + csn.getServerId() + ")");
      assertEquals(cursor.getRecord().getCSN(), csn);
    }
  }

  @Test(enabled=true, dependsOnMethods = { "searchInCookieModeOnTwoSuffixes" })
  public void searchInCookieModeOnTwoSuffixesWithPrivateBackend() throws Exception
  {
      String test = "CookiePrivateBackend";
      debugInfo(test, "Starting test");

      // Use o=test3 to avoid collision with o=test2 already used by a previous test
      Backend<?> backend3 = null;
      Pair<ReplicationBroker,LDAPReplicationDomain> replication1 = null;
      LDAPReplicationDomain domain2 = null;
      try {
        replication1 = enableReplication(DN_OTEST, SERVER_ID_1, replicationServerPort, brokerSessionTimeout);

        // create and publish 1 change on each suffix
        long time = TimeThread.getTime();
        CSN csn1 = new CSN(time, 1, SERVER_ID_1);
        ReplicationBroker broker = replication1.getFirst();
        broker.publish(generateDeleteMsg(DN_OTEST, csn1, test, 1));

        // create backend and configure replication for it
        backend3 = initializeMemoryBackend(false, TEST_BACKEND_ID3);
        backend3.setPrivateBackend(true);
        DomainFakeCfg domainConf2 = new DomainFakeCfg(DN_OTEST3, 1602,
            newTreeSet("localhost:" + replicationServerPort));
        domain2 = startNewReplicationDomain(domainConf2, null, null);

        // add a root entry to the backend
        Thread.sleep(1000);
        addEntry(createEntry(DN_OTEST3));

        // expect entry from o=test2 to be returned
        String cookie = "";
        searchChangelogUsingCookie("(targetDN=*)", cookie, SUCCESS, 2, test);

        ExternalChangelogDomainCfg eclCfg = new ExternalChangelogDomainFakeCfg(false, null, null);
        domainConf2.setExternalChangelogDomain(eclCfg);
        domain2.applyConfigurationChange(domainConf2);

        // expect only entry from o=test returned
        searchChangelogUsingCookie("(targetDN=*)", cookie, SUCCESS, 1, test);

        // test the lastExternalChangelogCookie attribute of the ECL
        // (does only refer to non private backend)
        String expectedLastCookie = "o=test:" + csn1 + ";";
        String lastCookie = readLastCookieFromRootDSE();
        assertThat(expectedLastCookie).isEqualTo(lastCookie);
      }
      finally
      {
        removeReplicationDomains(replication1.getSecond(), domain2);
        removeBackend(backend3);
        stop(replication1.getFirst());
      }
      debugInfo(test, "Ending test successfully");
  }

  @Test
  public void searchInChangeNumberModeWithInvalidChangeNumber() throws Exception
  {
    String testName = "UnknownChangeNumber";
    debugInfo(testName, "Starting test\n\n");

    searchChangelog("(changenumber=1000)", 0, SUCCESS, testName);

    debugInfo(testName, "Ending test with success");
  }

  @Test
  public void searchInChangeNumberModeOnOneSuffix() throws Exception
  {
    long firstChangeNumber = 1;
    String testName = "FourChanges/" + firstChangeNumber;
    debugInfo(testName, "Starting test\n\n");

    CSN[] csns = generateAndPublishUpdateMsgForEachOperationType(testName, false);
    searchChangesForEachOperationTypeUsingChangeNumberMode(firstChangeNumber, csns, testName);

    assertChangelogAttributesInRootDSE(true, 1, 4);

    debugInfo(testName, "Ending search with success");
  }

  @Test
  public void searchInChangeNumberModeOnOneSuffixMultipleTimes() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);

    // write 4 changes starting from changenumber 1, and search them
    String testName = "Multiple/1";
    CSN[] csns = generateAndPublishUpdateMsgForEachOperationType(testName, false);
    searchChangesForEachOperationTypeUsingChangeNumberMode(1, csns, testName);

    // write 4 more changes starting from changenumber 5, and search them
    testName = "Multiple/5";
    csns = generateAndPublishUpdateMsgForEachOperationType(testName, false);
    searchChangesForEachOperationTypeUsingChangeNumberMode(5, csns, testName);

    // search from the provided change number: 6 (should be the add msg)
    CSN csnOfLastAddMsg = csns[1];
    searchChangelogForOneChangeNumber(6, csnOfLastAddMsg);

    // search from a provided change number interval: 5-7
    searchChangelogFromToChangeNumber(5,7);

    // check first and last change number
    assertChangelogAttributesInRootDSE(true, 1, 8);

    // add a new change, then check again first and last change number without previous search
    CSN csn = new CSN(TimeThread.getTime(), 10, SERVER_ID_1);
    publishUpdateMessagesInOTest(testName, false, generateDeleteMsg(DN_OTEST, csn, testName, 1));

    assertChangelogAttributesInRootDSE(true, 1, 9);
  }

  /** Verifies that is not possible to read the changelog without the changelog-read privilege. */
  @Test
  public void searchingWithoutPrivilegeShouldFail() throws Exception
  {
    AuthenticationInfo nonPrivilegedUser = new AuthenticationInfo();
    InternalClientConnection conn = new InternalClientConnection(nonPrivilegedUser);

    SearchRequest request = Requests.newSearchRequest(DN.valueOf("cn=changelog"), SearchScope.WHOLE_SUBTREE);
    InternalSearchOperation op = conn.processSearch(request);

    assertEquals(op.getResultCode(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    assertEquals(op.getErrorMessage().toMessage(), NOTE_SEARCH_CHANGELOG_INSUFFICIENT_PRIVILEGES.get());
  }

  @Test(enabled=true, dependsOnMethods = { "searchInCookieModeOnTwoSuffixesWithPrivateBackend"})
  public void searchInCookieModeUseOfIncludeAttributes() throws Exception
  {
    String test = "IncludeAttributes";
    debugInfo(test, "Starting test\n\n");

    // Use o=test4 and o=test5 to avoid collision with existing suffixes already used by previous test
    final String backendId4 = "test4";
    final DN baseDN4 = DN.valueOf("o=" + backendId4);
    final String backendId5 = "test5";
    final DN baseDN5 = DN.valueOf("o=" + backendId5);
    Backend<?> backend4 = null;
    Backend<?> backend5 = null;
    LDAPReplicationDomain domain4 = null;
    LDAPReplicationDomain domain5 = null;
    LDAPReplicationDomain domain41 = null;
    try
    {
      SortedSet<String> replServers = newTreeSet("localhost:" + replicationServerPort);

      // backend4 and domain4
      backend4 = initializeMemoryBackend(false, backendId4);
      DomainFakeCfg domainConf = new DomainFakeCfg(baseDN4, 1702, replServers);
      SortedSet<String> eclInclude = newTreeSet("sn", "roomnumber");
      domain4 = startNewReplicationDomain(domainConf, eclInclude, eclInclude);

      // backend5 and domain5
      backend5 = initializeMemoryBackend(false, backendId5);
      domainConf = new DomainFakeCfg(baseDN5, 1703, replServers);
      eclInclude = newTreeSet("objectclass");
      SortedSet<String> eclIncludeForDeletes = newTreeSet("*");
      domain5 = startNewReplicationDomain(domainConf, eclInclude, eclIncludeForDeletes);

      // domain41
      domainConf = new DomainFakeCfg(baseDN4, 1704, replServers);
      eclInclude = newTreeSet("cn");
      domain41 = startNewReplicationDomain(domainConf, eclInclude, eclInclude);

      Thread.sleep(1000);

      addEntry(createEntry(baseDN4));
      addEntry(createEntry(baseDN5));

      Entry uentry1 = addEntry(
          "dn: cn=Fiona Jensen,o=" + backendId4,
          "objectclass: top",
          "objectclass: person",
          "objectclass: organizationalPerson",
          "objectclass: inetOrgPerson",
          "cn: Fiona Jensen",
          "sn: Jensen",
          "uid: fiona",
          "telephonenumber: 12121212");

      Entry uentry2 = addEntry(
          "dn: cn=Robert Hue,o=" + backendId5,
          "objectclass: top",
          "objectclass: person",
          "objectclass: organizationalPerson",
          "objectclass: inetOrgPerson",
          "cn: Robert Hue",
          "sn: Robby",
          "uid: robert",
          "telephonenumber: 131313");

      // mod 'sn' of fiona with 'sn' configured as ecl-incl-att
      final ModifyOperation modOp1 = connection.processModify(uentry1.getName(), createAttributeModif("sn", "newsn"));
      waitForSearchOpResult(modOp1, ResultCode.SUCCESS);

      // mod 'telephonenumber' of robert
      final ModifyOperation modOp2 = connection.processModify(uentry2.getName(),
          createAttributeModif("telephonenumber", "555555"));
      waitForSearchOpResult(modOp2, ResultCode.SUCCESS);

      // moddn robert to robert2
      ModifyDNOperation modDNOp = connection.processModifyDN(
          DN.valueOf("cn=Robert Hue," + baseDN5),
          RDN.decode("cn=Robert Hue2"), true,
          baseDN5);
      waitForSearchOpResult(modDNOp, ResultCode.SUCCESS);

      // del robert
      final DeleteOperation delOp = connection.processDelete(DN.valueOf("cn=Robert Hue2," + baseDN5));
      waitForSearchOpResult(delOp, ResultCode.SUCCESS);

      // Search on all suffixes
      String cookie = "";
      InternalSearchOperation searchOp = searchChangelogUsingCookie("(targetDN=*)", cookie, SUCCESS, 8, test);

      for (SearchResultEntry resultEntry : searchOp.getSearchEntries())
      {
        String targetdn = getAttributeValue(resultEntry, "targetdn");

        if (targetdn.endsWith("cn=robert hue,o=" + backendId5)
            || targetdn.endsWith("cn=robert hue2,o="  + backendId5))
        {
          Entry targetEntry = parseIncludedAttributes(resultEntry, targetdn);

          Set<String> eoc = newSet("person", "inetOrgPerson", "organizationalPerson", "top");
          assertAttributeValues(targetEntry, "objectclass", eoc);

          String changeType = getAttributeValue(resultEntry, "changetype");
          if ("delete".equals(changeType))
          {
            // We are using "*" for deletes so should get back 4 attributes.
            assertThat(targetEntry.getAttributes()).hasSize(4);
            assertAttributeValue(targetEntry, "uid", "robert");
            assertAttributeValue(targetEntry, "cn", "Robert Hue2");
            assertAttributeValue(targetEntry, "telephonenumber", "555555");
            assertAttributeValue(targetEntry, "sn", "Robby");
          }
          else
          {
            assertThat(targetEntry.getAttributes()).isEmpty();
          }
        }
        else if (targetdn.endsWith("cn=fiona jensen,o=" + backendId4))
        {
          Entry targetEntry = parseIncludedAttributes(resultEntry, targetdn);

          assertThat(targetEntry.getAttributes()).hasSize(2);
          assertAttributeValue(targetEntry,"sn","jensen");
          assertAttributeValue(targetEntry,"cn","Fiona Jensen");
        }
        assertAttributeValue(resultEntry,"changeinitiatorsname", "cn=Internal Client,cn=Root DNs,cn=config");
      }
    }
    finally
    {
      final DN fionaDN = DN.valueOf("cn=Fiona Jensen,o=" + backendId4);
      waitForSearchOpResult(connection.processDelete(fionaDN), ResultCode.SUCCESS);
      waitForSearchOpResult(connection.processDelete(baseDN4), ResultCode.SUCCESS);
      waitForSearchOpResult(connection.processDelete(baseDN5), ResultCode.SUCCESS);

      removeReplicationDomains(domain41, domain4, domain5);
      removeBackend(backend4, backend5);
    }
    debugInfo(test, "Ending test with success");
  }

  /** With an empty RS, a search should return only root entry. */
  @Test
  public void searchWhenNoChangesShouldReturnRootEntryOnly() throws Exception
  {
    String testName = "EmptyRS";
    debugInfo(testName, "Starting test\n\n");

    searchChangelog("(objectclass=*)", 1, SUCCESS, testName);

    debugInfo(testName, "Ending test successfully");
  }

  @Test
  public void operationalAndVirtualAttributesShouldNotBeVisibleOutsideRootDSE() throws Exception
  {
    String testName = "attributesVisibleOutsideRootDSE";
    debugInfo(testName, "Starting test \n\n");

    Set<String> attributes =
        newSet("firstchangenumber", "lastchangenumber", "changelog", "lastExternalChangelogCookie");

    InternalSearchOperation searchOp = searchDNWithBaseScope(DN_OTEST, attributes);
    waitForSearchOpResult(searchOp, ResultCode.SUCCESS);

    final List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertThat(entries).hasSize(1);
    debugAndWriteEntries(null, entries, testName);
    SearchResultEntry entry = entries.get(0);
    assertNull(getAttributeValue(entry, "firstchangenumber"));
    assertNull(getAttributeValue(entry, "lastchangenumber"));
    assertNull(getAttributeValue(entry, "changelog"));
    assertNull(getAttributeValue(entry, "lastExternalChangelogCookie"));

    debugInfo(testName, "Ending test with success");
  }

  @DataProvider
  Object[][] getFilters()
  {
    return new Object[][] {
      // base DN, filter, expected first change number, expected last change number
      { "cn=changelog", "(objectclass=*)", -1, -1 },
      { "cn=changelog", "(changenumber>=2)", 2, -1 },
      { "cn=changelog", "(&(changenumber>=2)(changenumber<=5))", 2, 5 },
      { "cn=changelog", "(&(dc=x)(&(changenumber>=2)(changenumber<=5)))", 2, 5 },
      { "cn=changelog",
          "(&(&(changenumber>=3)(changenumber<=4))(&(|(dc=y)(dc=x))(&(changenumber>=2)(changenumber<=5))))", 3, 4 },
      { "cn=changelog", "(|(objectclass=*)(&(changenumber>=2)(changenumber<=5)))", -1, -1 },
      { "cn=changelog", "(changenumber=8)", 8, 8 },

      { "changeNumber=8,cn=changelog", "(objectclass=*)", 8, 8 },
      { "changeNumber=8,cn=changelog", "(changenumber>=2)", 8, 8 },
      { "changeNumber=8,cn=changelog", "(&(changenumber>=2)(changenumber<=5))", 8, 8 },
    };
  }

  @Test(dataProvider="getFilters")
  public void optimizeFiltersWithChangeNumber(String dn, String filterString, long expectedFirstCN, long expectedLastCN)
      throws Exception
  {
    final ChangelogBackend backend = new ChangelogBackend(null, null);
    final DN baseDN = DN.valueOf(dn);
    final SearchFilter filter = SearchFilter.createFilterFromString(filterString);
    final ChangeNumberRange range = backend.optimizeSearch(baseDN, filter);

    assertChangeNumberRange(range, expectedFirstCN, expectedLastCN);
  }

  @Test
  public void optimizeFiltersWithReplicationCsn() throws Exception
  {
    final ChangelogBackend backend = new ChangelogBackend(null, null);
    final DN baseDN = DN.valueOf("cn=changelog");
    final CSN csn = new CSNGenerator(1, 0).newCSN();
    SearchFilter filter = SearchFilter.createFilterFromString("(replicationcsn=" + csn + ")");
    final ChangeNumberRange range = backend.optimizeSearch(baseDN, filter);

    assertChangeNumberRange(range, -1, -1);
  }

  private List<SearchResultEntry> assertChangelogAttributesInRootDSE(boolean isECLEnabled,
      int expectedFirstChangeNumber, int expectedLastChangeNumber) throws Exception
  {
    AssertionError error = null;
    for (int count = 0 ; count < 30; count++)
    {
      try
      {
        final Set<String> attributes = new LinkedHashSet<>();
        if (expectedFirstChangeNumber > 0)
        {
          attributes.add("firstchangenumber");
        }
        attributes.add("lastchangenumber");
        attributes.add("changelog");
        attributes.add("lastExternalChangelogCookie");

        final InternalSearchOperation searchOp = searchDNWithBaseScope(DN.rootDN(), attributes);
        final List<SearchResultEntry> entries = searchOp.getSearchEntries();
        assertThat(entries).hasSize(1);

        final SearchResultEntry entry = entries.get(0);
        if (isECLEnabled)
        {
          if (expectedFirstChangeNumber > 0)
          {
            assertAttributeValue(entry, "firstchangenumber", expectedFirstChangeNumber);
          }
          assertAttributeValue(entry, "lastchangenumber", expectedLastChangeNumber);
          assertAttributeValue(entry, "changelog", "cn=changelog");
          assertNotNull(getAttributeValue(entry, "lastExternalChangelogCookie"));
        }
        else
        {
          if (expectedFirstChangeNumber > 0) {
            assertNull(getAttributeValue(entry, "firstchangenumber"));
          }
          assertNull(getAttributeValue(entry, "lastchangenumber"));
          assertNull(getAttributeValue(entry, "changelog"));
          assertNull(getAttributeValue(entry, "lastExternalChangelogCookie"));
        }
        return entries;
      }
      catch (AssertionError ae)
      {
        // try again to see if changes have been persisted
        error = ae;
      }
      Thread.sleep(100);
    }
    assertNotNull(error);
    throw error;
  }

  private String readLastCookieFromRootDSE() throws Exception
  {
    String cookie = "";
    LDIFWriter ldifWriter = getLDIFWriter();

    InternalSearchOperation searchOp = searchDNWithBaseScope(DN.rootDN(), newSet("lastExternalChangelogCookie"));
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

  private String assertLastCookieDifferentThanLastValue(final String lastCookie) throws Exception
  {
    int count = 0;
    while (count < 100)
    {
      final String newCookie = readLastCookieFromRootDSE();
      if (!newCookie.equals(lastCookie))
      {
        return newCookie;
      }
      count++;
      Thread.sleep(10);
    }
    Assertions.fail("Expected last cookie should have been updated, but it always stayed at value '" + lastCookie + "'");
    return null;// dead code
  }

  private String readCookieFromNthEntry(List<SearchResultEntry> entries, int i)
  {
    SearchResultEntry entry = entries.get(i);
    return entry.getAttribute("changelogcookie").get(0).iterator().next().toString();
  }

  private String assertEntriesContainsCSNsAndReadLastCookie(String test, List<SearchResultEntry> entries,
      LDIFWriter ldifWriter, CSN... csns) throws Exception
  {
    assertThat(getCSNsFromEntries(entries)).containsExactly(csns);
    debugAndWriteEntries(ldifWriter, entries, test);
    return readCookieFromNthEntry(entries, csns.length - 1);
  }

  private List<CSN> getCSNsFromEntries(List<SearchResultEntry> entries)
  {
    List<CSN> results = new ArrayList<>(entries.size());
    for (SearchResultEntry entry : entries)
    {
      results.add(new CSN(getAttributeValue(entry, "replicationCSN")));
    }
    return results;
  }

  private void assertChangeNumberRange(ChangeNumberRange range, long firstChangeNumber, long lastChangeNumber)
      throws Exception
  {
    assertEquals(range.getLowerBound(), firstChangeNumber);
    assertEquals(range.getUpperBound(), lastChangeNumber);
  }

  private CSN[] generateAndPublishUpdateMsgForEachOperationType(String testName, boolean checkLastCookie)
      throws Exception
  {
    CSN[] csns = generateCSNs(4, SERVER_ID_1);
    publishUpdateMessagesInOTest(testName, checkLastCookie,
      generateDeleteMsg(DN_OTEST, csns[0], testName, 1),
      generateAddMsg(DN_OTEST, csns[1], USER1_ENTRY_UUID, testName),
      generateModMsg(DN_OTEST, csns[2], testName),
      generateModDNMsg(DN_OTEST, csns[3], testName));
    return csns;
  }

  /** Shortcut method for default base DN and server id used in tests. */
  private void publishUpdateMessagesInOTest(String testName, boolean checkLastCookie, UpdateMsg...messages)
      throws Exception
  {
    publishUpdateMessages(testName, DN_OTEST, SERVER_ID_1, checkLastCookie, messages);
  }

  private void publishUpdateMessagesInOTest2(String testName, boolean checkLastCookie, UpdateMsg...messages)
      throws Exception
  {
    publishUpdateMessages(testName, DN_OTEST2, SERVER_ID_2, checkLastCookie, messages);
  }

  /**
   * Publish a list of update messages to the replication broker corresponding to given baseDN and server id.
   *
   * @param checkLastCookie if true, checks that last cookie is update after each message publication
   */
  private void publishUpdateMessages(String testName, DN baseDN, int serverId, boolean checkLastCookie,
      ReplicationMsg...messages) throws Exception
  {
    Pair<ReplicationBroker, LDAPReplicationDomain> replicationObjects = null;
    try
    {
      replicationObjects = enableReplication(baseDN, serverId, replicationServerPort, brokerSessionTimeout);
      ReplicationBroker broker = replicationObjects.getFirst();
      String cookie = "";
      for (ReplicationMsg msg : messages)
      {
        if (msg instanceof UpdateMsg)
        {
          debugInfo(testName, " publishes " + ((UpdateMsg) msg).getCSN());
        }

        broker.publish(msg);

        if (checkLastCookie)
        {
          cookie = assertLastCookieDifferentThanLastValue(cookie);
        }
      }
    }
    finally
    {
      if (replicationObjects != null)
      {
        removeReplicationDomains(replicationObjects.getSecond());
        stop(replicationObjects.getFirst());
      }
    }
  }

  private String[] buildCookiesFromCsns(CSN[] csns)
  {
    final String[] cookies = new String[csns.length];
    for (int j = 0; j < cookies.length; j++)
    {
      cookies[j] = "o=test:" + csns[j] + ";";
    }
    return cookies;
  }

  private void searchChangesForEachOperationTypeUsingChangeNumberMode(long firstChangeNumber, CSN[] csns,
      String testName) throws Exception
  {
    // Search the changelog and check 4 entries are returned
    String filter = "(targetdn=*" + testName + "*,o=test)";
    InternalSearchOperation searchOp = searchChangelog(filter, 4, SUCCESS, testName);

    assertContainsNoControl(searchOp);
    assertEntriesForEachOperationType(searchOp.getSearchEntries(), firstChangeNumber, testName, USER1_ENTRY_UUID, csns);

    // Search the changelog with filter on change number and check 4 entries are returned
    filter =
        "(&(targetdn=*" + testName + "*,o=test)"
          + "(&(changenumber>=" + firstChangeNumber + ")"
            + "(changenumber<=" + (firstChangeNumber + 3) + ")))";
    searchOp = searchChangelog(filter, 4, SUCCESS, testName);

    assertContainsNoControl(searchOp);
    assertEntriesForEachOperationType(searchOp.getSearchEntries(), firstChangeNumber, testName, USER1_ENTRY_UUID, csns);
  }

  /**
   * Search on the provided change number and check the result.
   *
   * @param changeNumber
   *          Change number to search
   * @param expectedCsn
   *          Expected CSN in the entry corresponding to the change number
   */
  private void searchChangelogForOneChangeNumber(long changeNumber, CSN expectedCsn) throws Exception
  {
    String testName = "searchOneChangeNumber/" + changeNumber;
    debugInfo(testName, "Starting search\n\n");

    InternalSearchOperation searchOp =
        searchChangelog("(changenumber=" + changeNumber + ")", 1, SUCCESS, testName);

    SearchResultEntry entry = searchOp.getSearchEntries().get(0);
    String uncheckedUid = null;
    assertEntryCommonAttributes(entry, uncheckedUid, USER1_ENTRY_UUID, changeNumber, expectedCsn,
        "o=test:" + expectedCsn + ";");

    debugInfo(testName, "Ending search with success");
  }

  private void searchChangelogFromToChangeNumber(int firstChangeNumber, int lastChangeNumber) throws Exception
  {
    String testName = "searchFromToChangeNumber/" + firstChangeNumber + "/" + lastChangeNumber;
    debugInfo(testName, "Starting search\n\n");

    String filter = "(&(changenumber>=" + firstChangeNumber + ")" + "(changenumber<=" + lastChangeNumber + "))";
    final int expectedNbEntries = lastChangeNumber - firstChangeNumber + 1;
    searchChangelog(filter, expectedNbEntries, SUCCESS, testName);

    debugInfo(testName, "Ending search with success");
  }

  private InternalSearchOperation searchChangelogUsingCookie(String filterString,
      String cookie, ResultCode expectedResultCode, int expectedNbEntries, String testName)
      throws Exception
  {
    debugInfo(testName, "Search with cookie=[" + cookie + "] filter=[" + filterString + "]");
    SearchRequest request = newSearchRequest(filterString).addControl(createCookieControl(cookie));
    return searchChangelog(request, expectedNbEntries, expectedResultCode, testName);
  }

  private InternalSearchOperation searchChangelog(String filterString, int expectedNbEntries,
      ResultCode expectedResultCode, String testName) throws Exception
  {
    SearchRequest request = newSearchRequest(filterString);
    return searchChangelog(request, expectedNbEntries, expectedResultCode, testName);
  }

  private SearchRequest newSearchRequest(String filterString) throws DirectoryException
  {
    return Requests.newSearchRequest("cn=changelog", SearchScope.WHOLE_SUBTREE, filterString)
        .addAttribute("*", "+"); // all user and operational attributes
  }

  private InternalSearchOperation searchChangelog(SearchRequest request, int expectedNbEntries,
      ResultCode expectedResultCode, String testName) throws Exception
  {
    InternalSearchOperation searchOp = null;
    int count = 0;
    do
    {
      Thread.sleep(10);
      searchOp = connection.processSearch(request);
      count++;
    }
    while (count < 500 && searchOp.getSearchEntries().size() != expectedNbEntries);

    final List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertThat(entries).hasSize(expectedNbEntries);
    debugAndWriteEntries(getLDIFWriter(), entries, testName);
    waitForSearchOpResult(searchOp, expectedResultCode);
    return searchOp;
  }

  private InternalSearchOperation searchDNWithBaseScope(DN dn, Set<String> attributes) throws Exception
  {
    SearchRequest request = Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT)
        .addAttribute(attributes);
    final InternalSearchOperation searchOp = connection.processSearch(request);
    waitForSearchOpResult(searchOp, ResultCode.SUCCESS);
    return searchOp;
  }

  /** Build a list of controls including the cookie provided. */
  private List<Control> createCookieControl(String cookie) throws DirectoryException
  {
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    final Control cookieControl = new ExternalChangelogRequestControl(true, state);
    return newList(cookieControl);
  }

  private static LDIFWriter getLDIFWriter() throws Exception
  {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LDIFExportConfig exportConfig = new LDIFExportConfig(stream);
    return new LDIFWriter(exportConfig);
  }

  private CSN[] generateCSNs(int numberOfCsns, int serverId)
  {
    long startTime = TimeThread.getTime();

    CSN[] csns = new CSN[numberOfCsns];
    for (int i = 0; i < numberOfCsns; i++)
    {
      // seqNum must be greater than 0, so start at 1
      csns[i] = new CSN(startTime + i, i + 1, serverId);
    }
    return csns;
  }

  private UpdateMsg generateDeleteMsg(DN baseDn, CSN csn, String testName, int testIndex)
      throws Exception
  {
    String dn = "uid=" + testName + testIndex + "," + baseDn;
    return new DeleteMsg(DN.valueOf(dn), csn, testName + "uuid" + testIndex);
  }

  private UpdateMsg generateAddMsg(DN baseDn, CSN csn, String user1entryUUID, String testName)
      throws Exception
  {
    String baseUUID = "22222222-2222-2222-2222-222222222222";
    String dn = "uid=" + testName + "2," + baseDn;
    Entry entry = makeEntry(
        "dn: " + dn,
        "objectClass: top",
        "objectClass: domain",
        "entryUUID: "+ user1entryUUID);
    return new AddMsg(
        csn,
        DN.valueOf(dn),
        user1entryUUID,
        baseUUID,
        entry.getObjectClassAttribute(),
        entry.getAttributes(),
        Collections.<Attribute> emptyList());
  }

  private UpdateMsg generateModMsg(DN baseDn, CSN csn, String testName) throws Exception
  {
    DN baseDN = DN.valueOf("uid=" + testName + "3," + baseDn);
    List<Modification> mods = createAttributeModif("description", "new value");
    return new ModifyMsg(csn, baseDN, mods, testName + "uuid3");
  }

  private List<Modification> createAttributeModif(String attributeName, String valueString)
  {
    Attribute attr = Attributes.create(attributeName, valueString);
    return newArrayList(new Modification(ModificationType.REPLACE, attr));
  }

  private UpdateMsg generateModDNMsg(DN baseDn, CSN csn, String testName) throws Exception
  {
    final DN newSuperior = DN_OTEST2;
    ModifyDNOperation op = new ModifyDNOperationBasis(connection, 1, 1, null,
        DN.valueOf("uid=" + testName + "4," + baseDn), // entryDN
        RDN.decode("uid=" + testName + "new4"), // new rdn
        true,  // deleteoldrdn
        newSuperior);
    op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(csn, testName + "uuid4", "newparentId"));
    LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
    return new ModifyDNMsg(localOp);
  }

  /** TODO : share this code with other classes ? */
  private void waitForSearchOpResult(Operation operation, ResultCode expectedResult) throws Exception
  {
    int i = 0;
    while (operation.getResultCode() == ResultCode.UNDEFINED || operation.getResultCode() != expectedResult)
    {
      Thread.sleep(50);
      i++;
      if (i > 10)
      {
        assertEquals(operation.getResultCode(), expectedResult, operation.getErrorMessage().toString());
      }
    }
  }

  /** Verify that no entry contains the ChangeLogCookie control. */
  private void assertContainsNoControl(InternalSearchOperation searchOp)
  {
    for (SearchResultEntry entry : searchOp.getSearchEntries())
    {
      assertThat(entry.getControls())
          .as("result entry " + entry + " should contain no control(s)")
          .isEmpty();
    }
  }

  /** Verify that all entries contains the ChangeLogCookie control with the correct cookie value. */
  private void assertResultsContainCookieControl(InternalSearchOperation searchOp, String[] cookies) throws Exception
  {
    for (SearchResultEntry entry : searchOp.getSearchEntries())
    {
      boolean cookieControlFound = false;
      for (Control control : entry.getControls())
      {
        if (OID_ECL_COOKIE_EXCHANGE_CONTROL.equals(control.getOID()))
        {
          String cookieString =
              searchOp.getRequestControl(ExternalChangelogRequestControl.DECODER).getCookie().toString();
          assertThat(cookieString).isIn((Object[]) cookies);
          cookieControlFound = true;
        }
      }
      assertTrue(cookieControlFound, "result entry " + entry + " should contain the cookie control");
    }
  }

  /** Check the DEL entry has the right content. */
  private void assertDelEntry(SearchResultEntry entry, String uid, String entryUUID,
      long changeNumber, CSN csn, String cookie) throws Exception
  {
    assertAttributeValue(entry, "changetype", "delete");
    assertAttributeValue(entry, "targetuniqueid", entryUUID);
    assertAttributeValue(entry, "targetentryuuid", entryUUID);
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  /** Check the ADD entry has the right content. */
  private void assertAddEntry(SearchResultEntry entry, String uid, String entryUUID,
      long changeNumber, CSN csn, String cookie) throws Exception
  {
    assertAttributeValue(entry, "changetype", "add");
    assertEntryMatchesLDIF(entry, "changes",
        "objectClass: domain",
        "objectClass: top",
        "entryUUID: " + entryUUID);
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  private void assertModEntry(SearchResultEntry entry, String uid, String entryUUID,
      long changeNumber, CSN csn, String cookie) throws Exception
  {
    assertAttributeValue(entry, "changetype", "modify");
    assertEntryMatchesLDIF(entry, "changes",
        "replace: description",
        "description: new value",
        "-");
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  private void assertModDNEntry(SearchResultEntry entry, String uid, String newUid,
      String entryUUID, long changeNumber, CSN csn, String cookie) throws Exception
  {
    assertAttributeValue(entry, "changetype", "modrdn");
    assertAttributeValue(entry, "newrdn", "uid=" + newUid);
    assertAttributeValue(entry, "newsuperior", DN_OTEST2);
    assertAttributeValue(entry, "deleteoldrdn", "true");
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  private void assertEntryCommonAttributes(SearchResultEntry resultEntry,
      String uid, String entryUUID, long changeNumber, CSN csn, String cookie) throws Exception
  {
    if (changeNumber == 0)
    {
      assertDNWithCSN(resultEntry, csn);
    }
    else
    {
      assertDNWithChangeNumber(resultEntry, changeNumber);
      assertAttributeValue(resultEntry, "changenumber", changeNumber);
    }
    assertAttributeValue(resultEntry, "targetentryuuid", entryUUID);
    assertAttributeValue(resultEntry, "replicaidentifier", SERVER_ID_1);
    assertAttributeValue(resultEntry, "replicationcsn", csn);
    assertAttributeValue(resultEntry, "changelogcookie", cookie);
    // A null value can be provided for uid if it should not be checked
    if (uid != null)
    {
      final String targetDN = "uid=" + uid + "," + DN_OTEST;
      assertAttributeValue(resultEntry, "targetdn", targetDN);
    }
  }

  private void assertEntriesForEachOperationType(List<SearchResultEntry> entries, long firstChangeNumber,
      String testName, String entryUUID, CSN... csns) throws Exception
  {
    debugAndWriteEntries(getLDIFWriter(), entries, testName);

    assertThat(entries).hasSize(4);

    CSN csn = csns[0];
    assertDelEntry(entries.get(0), testName + "1", testName + "uuid1", firstChangeNumber, csn, "o=test:" + csn + ";");

    csn = csns[1];
    assertAddEntry(entries.get(1), testName + "2", entryUUID, firstChangeNumber+1, csn, "o=test:" + csn + ";");

    csn = csns[2];
    assertModEntry(entries.get(2), testName + "3", testName + "uuid3", firstChangeNumber+2, csn,
        "o=test:" + csn + ";");

    csn = csns[3];
    assertModDNEntry(entries.get(3), testName + "4", testName + "new4", testName + "uuid4", firstChangeNumber+3, csn,
        "o=test:" + csn + ";");
  }

  /** Asserts the attribute value as LDIF to ignore lines ordering. */
  private static void assertEntryMatchesLDIF(Entry entry, String attrName, String... expectedLDIFLines)
  {
    final String actualVal = getAttributeValue(entry, attrName);
    final Set<Set<String>> actual = toLDIFEntries(actualVal.split("\n"));
    final Set<Set<String>> expected = toLDIFEntries(expectedLDIFLines);
    assertThat(actual)
        .as("In entry " + entry + " incorrect value for attr '" + attrName + "'")
        .isEqualTo(expected);
  }

  private static void assertAttributeValues(Entry entry, String attrName, Set<String> expectedValues)
  {
    final Set<String> values = new HashSet<>();
    for (Attribute attr : entry.getAttribute(attrName))
    {
      for (ByteString value : attr)
      {
        values.add(value.toString());
      }
    }
    assertThat(values)
      .as("In entry " + entry + " incorrect values for attribute '" + attrName + "'")
      .isEqualTo(expectedValues);
  }

  private static void assertAttributeValue(Entry entry, String attrName, Object expectedValue)
  {
    String expectedValueString = String.valueOf(expectedValue);
    assertFalse(expectedValueString.contains("\n"),
        "You should use assertEntryMatchesLDIF() method for asserting on this value: \"" + expectedValueString + "\"");
    final String actualValue = getAttributeValue(entry, attrName);
    assertThat(actualValue)
        .as("In entry " + entry + " incorrect value for attr '" + attrName + "'")
        .isEqualToIgnoringCase(expectedValueString);
  }

  private void assertDNWithChangeNumber(SearchResultEntry resultEntry, long changeNumber) throws Exception
  {
    DN actualDN = resultEntry.getName();
    DN expectedDN = DN.valueOf("changenumber=" + changeNumber + ",cn=changelog");
    assertThat(actualDN).isEqualTo(expectedDN);
  }

  private void assertDNWithCSN(SearchResultEntry resultEntry, CSN csn) throws Exception
  {
    DN actualDN = resultEntry.getName();
    DN expectedDN = DN.valueOf("replicationcsn=" + csn + "," + DN_OTEST + ",cn=changelog");
    assertThat(actualDN).isEqualTo(expectedDN);
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
    final Set<Set<String>> results = new HashSet<>();
    Set<String> ldifEntryLines = new HashSet<>();
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
        ldifEntryLines = new HashSet<>();
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
    Attribute attr = attrs.iterator().next();
    ByteString value = attr.iterator().next();
    return value.toString();
  }

  private Entry parseIncludedAttributes(SearchResultEntry resultEntry, String targetdn) throws Exception
  {
    // Parse includedAttributes as an entry.
    String includedAttributes = getAttributeValue(resultEntry, "includedattributes");
    String[] ldifAttributeLines = includedAttributes.split("\\n");
    String[] ldif = new String[ldifAttributeLines.length + 1];
    System.arraycopy(ldifAttributeLines, 0, ldif, 1, ldifAttributeLines.length);
    ldif[0] = "dn: " + targetdn;
    return makeEntry(ldif);
  }

  private void debugAndWriteEntries(LDIFWriter ldifWriter,List<SearchResultEntry> entries, String tn) throws Exception
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

  /** Creates a memory backend, to be used as additional backend in tests. */
  private static Backend<?> initializeMemoryBackend(boolean createBaseEntry, String backendId) throws Exception
  {
    DN baseDN = DN.valueOf("o=" + backendId);

    //  Retrieve backend. Warning: it is important to perform this each time,
    //  because a test may have disabled then enabled the backend (i.e a test
    //  performing an import task). As it is a memory backend, when the backend
    //  is re-enabled, a new backend object is in fact created and old reference
    //  to memory backend must be invalidated. So to prevent this problem, we
    //  retrieve the memory backend reference each time before cleaning it.
    MemoryBackend memoryBackend = (MemoryBackend) DirectoryServer.getBackend(backendId);

    if (memoryBackend == null)
    {
      memoryBackend = new MemoryBackend();
      memoryBackend.setBackendID(backendId);
      memoryBackend.setBaseDNs(new DN[] {baseDN});
      memoryBackend.openBackend();
      DirectoryServer.registerBackend(memoryBackend);
    }

    memoryBackend.clearMemoryBackend();

    if (createBaseEntry)
    {
      memoryBackend.addEntry(createEntry(baseDN), null);
    }
    return memoryBackend;
  }

  private static void removeBackend(Backend<?>... backends)
  {
    for (Backend<?> backend : backends)
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
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String testName, String message)
  {
    logger.trace("** TEST %s ** %s", testName, message);
  }
}
