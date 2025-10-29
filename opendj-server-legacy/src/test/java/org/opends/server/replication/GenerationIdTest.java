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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2023-2025 3A Systems, LLC.
 */
package org.opends.server.replication;

import static java.util.concurrent.TimeUnit.*;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests contained here:
 *
 * - testSingleRS : test generation ID setting with different servers and one
 *   Replication server.
 *
 * - testMultiRS : tests generation ID propagation with more than one
 *   Replication server.
 */
@SuppressWarnings("javadoc")
public class GenerationIdTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String baseDnStr = TEST_ROOT_DN_STRING;
  private static final String testName = "generationIdTest";
  private static final String REPLICATION_GENERATION_ID = "ds-sync-generation-id";

  private static final int WINDOW_SIZE = 10;
  private static final int server1ID = 901;
  private static final int server2ID = 902;
  private static final int server3ID = 903;
  private static final int replServerId1 = 911;
  private static final int replServerId2 = 912;
  private static final int replServerId3 = 913;

  private DN baseDN;
  private ReplicationBroker broker2;
  private ReplicationBroker broker3;
  private ReplicationServer replServer1;
  private ReplicationServer replServer2;
  private ReplicationServer replServer3;
  private Entry taskInitRemoteS2;
  private String[] updatedEntries;
  private static int[] replServerPort;

  /** A makeldif template used to create some test entries. */
  private static String diff = "";
  private static String[] template = new String[] {
    "define suffix=" + baseDnStr,
    "define maildomain=example.com",
    "define numusers=11",
    "",
    "branch: [suffix]",
    "",
    "branch: ou=People,[suffix]",
    "subordinateTemplate: person:[numusers]",
    "",
    "template: person",
    "rdnAttr: uid",
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "givenName: <first>",
    "sn: <last>",
    "cn: {givenName} {sn}",
    "initials: {givenName:1}<random:chars:" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
    "employeeNumber: <sequential:0>",
    "uid: user.{employeeNumber}",
    "mail: {uid}@[maildomain]",
    "userPassword: password",
    "telephoneNumber: <random:telephone>",
    "homePhone: <random:telephone>",
    "pager: <random:telephone>",
    "mobile: <random:telephone>",
    "street: <random:numeric:5> <file:streets> Street",
    "l: <file:cities>",
    "st: <file:states>",
    "postalCode: <random:numeric:5>",
    "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
    "description: This is the description for {cn} " + diff,
  ""};

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw("** TEST **" + s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  private void debugInfo(String message, Exception e)
  {
    debugInfo(message + " " + stackTraceToSingleLineString(e));
  }

  /** Set up the environment for performing the tests in this Class. */
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    replServerPort = TestCaseUtils.findFreePorts(3);
    baseDN = DN.valueOf(baseDnStr);

    updatedEntries = newLDIFEntries();

    // Synchro suffix
    synchroServerEntry = null;

    taskInitRemoteS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + server2ID);
  }

  /** Tests that entries have been written in the db. */
  private int countUpdatedEntriesInDb() throws Exception
  {
    debugInfo("countUpdatedEntriesInDb");
    int found = 0;

    for (String entry : updatedEntries)
    {
      int dns = entry.indexOf("dn: ");
      int dne = entry.indexOf(TEST_ROOT_DN_STRING);
      String dn = entry.substring(dns + 4, dne + TEST_ROOT_DN_STRING.length());

      debugInfo("Search Entry: " + dn);

      DN entryDN = DN.valueOf(dn);

      try
      {
        Entry resultEntry = getEntry(entryDN, 1000, true);
        if (resultEntry==null)
        {
          debugInfo("Entry not found <" + dn + ">");
        }
        else
        {
          debugInfo("Entry found <" + dn + ">");
          found++;
        }
      }
      catch(Exception e)
      {
        debugInfo("TestEntriesInDb/", e);
      }
    }
    return found;
  }

  /** Creates entries necessary to the test. */
  private String[] newLDIFEntries()
  {
    return new String[]{
        "dn: " + baseDN + "\n"
            + "objectClass: top\n"
            + "objectClass: organization\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
            + "\n",
        "dn: ou=People," + baseDN + "\n"
            + "objectClass: top\n"
            + "objectClass: organizationalUnit\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
            + "\n",
        "dn: cn=Fiona Jensen,ou=people," + baseDN + "\n"
            + "objectclass: top\n"
            + "objectclass: person\n"
            + "objectclass: organizationalPerson\n"
            + "objectclass: inetOrgPerson\n"
            + "cn: Fiona Jensen\n"
            + "sn: Jensen\n"
            + "uid: fiona\n"
            + "telephonenumber: +1 408 555 1212\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111113\n"
            + "\n",
        "dn: cn=Robert Langman,ou=people," + baseDN + "\n"
            + "objectclass: top\n"
            + "objectclass: person\n"
            + "objectclass: organizationalPerson\n"
            + "objectclass: inetOrgPerson\n"
            + "cn: Robert Langman\n"
            + "sn: Langman\n"
            + "uid: robert\n"
            + "telephonenumber: +1 408 555 1213\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111114\n"
            + "\n"
    };
  }

  private int receiveImport(ReplicationBroker broker, int serverID, String[] updatedEntries) throws Exception
  {
    // Expect the broker to receive the entries
    int entriesReceived = -100;
    while (true)
    {
      debugInfo("Broker " + serverID + " Wait for entry or done msg");
      ReplicationMsg msg = broker.receive();
      if (msg == null)
      {
        break;
      }

      if (msg instanceof InitializeTargetMsg)
      {
        debugInfo("Broker " + serverID + " receives InitializeTargetMessage ");
        entriesReceived = 0;
      }
      else if (msg instanceof EntryMsg)
      {
        EntryMsg em = (EntryMsg)msg;
        debugInfo("Broker " + serverID + " receives entry " + new String(em.getEntryBytes()));
        entriesReceived++;
      }
      else if (msg instanceof DoneMsg)
      {
        debugInfo("Broker " + serverID + " receives done ");
        break;
      }
      else if (msg instanceof ErrorMsg)
      {
        debugInfo("Broker " + serverID + " receives ERROR " + msg);
        break;
      }
      else
      {
        debugInfo("Broker " + serverID + " receives and trashes " + msg);
      }
    }

    if (updatedEntries != null)
    {
      Assertions.assertThat(updatedEntries).hasSize(entriesReceived);
    }

    return entriesReceived;
  }

  /**
   * Creates a new replicationServer.
   * @param replServerId The serverID of the replicationServer to create.
   * @param all         Specifies whether to connect the created replication
   *                    server to the other replication servers in the test.
   * @return The new created replication server.
   */
  private ReplicationServer createReplicationServer(int replServerId,
      boolean all, String testCase) throws Exception
  {
    SortedSet<String> servers = new TreeSet<>();
    if (all)
    {
      if (replServerId != replServerId1)
      {
        servers.add("127.0.0.1:" + getRSPort(replServerId1));
      }
      if (replServerId != replServerId2)
      {
        servers.add("127.0.0.1:" + getRSPort(replServerId2));
      }
      if (replServerId != replServerId3)
      {
        servers.add("127.0.0.1:" + getRSPort(replServerId3));
      }
    }
    int rsPort = getRSPort(replServerId);
    String rsDir = "generationIdTest" + replServerId + testCase + "Db";
    ReplicationServer replicationServer = new ReplicationServer(
        new ReplServerFakeConfiguration(rsPort, rsDir, 0, replServerId, 0, 1000, servers));
    Thread.sleep(3000);
    return replicationServer;
  }

  /** Create a synchronized suffix in the current server providing the replication Server. */
  private void connectServer1ToReplServer(ReplicationServer rs) throws Exception
  {
    debugInfo("Connecting DS1 to replicate to RS" + getRSNumber(rs) + "(" + rs.getServerId() + ")");
    {
      // suffix synchronized
      String synchroServerLdif =
        "dn: cn=" + testName + ", cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: " + baseDnStr + "\n"
        + "ds-cfg-replication-server: 127.0.0.1:" + rs.getReplicationPort() + "\n"
        + "ds-cfg-server-id: " + server1ID + "\n"
        + "ds-cfg-receive-status: true\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE;

      // Must be no connection already done or disconnectFromReplServer should
      // have been called
      assertNull(synchroServerEntry);

      addSynchroServerEntry(synchroServerLdif);


      TestTimer timer = new TestTimer.Builder()
        .maxSleep(1, MINUTES)
        .sleepTimes(200, MILLISECONDS)
        .toTimer();
      timer.repeatUntilSuccess(new CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          LDAPReplicationDomain doToco = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
          assertNotNull(doToco);
          assertTrue(doToco.isConnected(), "not connected");
          debugInfo("ReplicationDomain: Import/Export is running ? " + doToco.ieRunning());
        }
      });
    }
  }

  private String getRSNumber(ReplicationServer rs)
  {
    if (rs == replServer1)
    {
      return "1";
    }
    else if (rs == replServer2)
    {
      return "2";
    }
    else if (rs == replServer3)
    {
      return "3";
    }
    fail("Unknown RS");
    return null;
  }

  /**
   * Disconnect DS from the provided replicationServer.
   *
   * @see #connectServer1ToReplServer(ReplicationServer) should have been called previously
   */
  private void disconnectFromReplServer(ReplicationServer rs) throws Exception
  {
    debugInfo("Disconnect DS1 from RS" + getRSNumber(rs) + "(" + rs.getServerId() + ")");
    {
      // suffix synchronized
      String synchroServerStringDN = "cn=" + testName + ", cn=domains," + SYNCHRO_PLUGIN_DN;
      assertNotNull(synchroServerEntry);

      DN synchroServerDN = DN.valueOf(synchroServerStringDN);

      Entry ecle = DirectoryServer.getEntry(DN.valueOf("cn=external changelog," + synchroServerStringDN));
      if (ecle!=null)
      {
        getServerContext().getConfigurationHandler().deleteEntry(ecle.getName());
      }
      getServerContext().getConfigurationHandler().deleteEntry(synchroServerDN);
      assertNull(DirectoryServer.getEntry(synchroServerEntry.getName()),
        "Unable to delete the synchronized domain");
      synchroServerEntry = null;

      configEntriesToCleanup.remove(synchroServerDN);


      try
      {
        // check replication domain gets disconnected
        TestTimer timer = new TestTimer.Builder()
          .maxSleep(10, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer();
        timer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertNull(LDAPReplicationDomain.retrievesReplicationDomain(baseDN));
          }
        });
      }
      catch (DirectoryException e)
      {
        // success
        debugInfo("disconnectFromReplServer: " + rs.getServerId(), e);
      }
    }
  }

  private int getRSPort(int replServerId) throws Exception
  {
    return replServerPort[replServerId - replServerId1];
  }

  private long readGenIdFromSuffixRootEntry(boolean shouldExist) throws Exception
  {
    Entry resultEntry = getEntry(baseDN, 1000, shouldExist);
    if (resultEntry == null)
    {
      debugInfo("Entry not found <" + baseDN + ">");
    }
    else
    {
      debugInfo("Entry found <" + baseDN + ">");

      Attribute attr = resultEntry.getAttribute(AttributeDescription.valueOf(REPLICATION_GENERATION_ID));
      return Long.valueOf(attr.iterator().next().toString());
    }
    return -1;
  }

  private void performLdifImport() throws Exception
  {
    // Create a temporary test LDIF file.
    // A temporary LDIF file containing some test entries.
    File ldifFile = File.createTempFile("import-test", ".ldif");
    String resourcePath =
        DirectoryServer.getInstanceRoot() + File.separator + "config"
            + File.separator + "MakeLDIF";
    LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, template);

    // Launch import of the Ldif file on the memory test backend
    // Note: we do not use a task here as import task does not work on memory
    // backend: it disables then re-enables backend which leads to backend
    // object instance lost and this is not acceptable for a backend with
    // non persistent data
    LDIFImportConfig importConfig = new LDIFImportConfig(ldifFile.getAbsolutePath());

    MemoryBackend memoryBackend =
        (MemoryBackend) getServerContext().getBackendConfigManager().getLocalBackendById(TEST_BACKEND_ID);
    memoryBackend.importLDIF(importConfig, getServerContext());
  }

  private String createEntry(UUID uid)
  {
    String userDN = "uid=user" + uid + ",ou=People," + baseDnStr;
    return "dn: " + userDN + "\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar2\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 2\n"
        + "sn: Amar2\n" + "givenName: Aaccf2\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
  }

  private static AddMsg createAddMsg() throws Exception
  {
    /*
     * Create a CSN generator to generate new CSNs when we need to send
     * operation messages to the replicationServer.
     */
    CSNGenerator gen = new CSNGenerator(2, 0);

    String user1entryUUID = "33333333-3333-3333-3333-333333333333";
    String user1dn = "uid=user1,ou=People," + baseDnStr;
    // @formatter:off
    Entry personWithUUIDEntry = TestCaseUtils.makeEntry(
        "dn: "+ user1dn,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "homePhone: 951-245-7634",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "mobile: 027-085-0537",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street$Rockford, NC  85762",
        "mail: user.1@example.com",
        "cn: Aaccf Amar",
        "l: Rockford",
        "pager: 508-763-4246",
        "street: 17984 Thirteenth Street",
        "telephoneNumber: 216-564-6748",
        "employeeNumber: 1",
        "sn: Amar",
        "givenName: Aaccf",
        "postalCode: 85762",
        "userPassword: password",
        "initials: AA",
        "entryUUID: " + user1entryUUID);
    // @formatter:on

    // Create and publish an update message to add an entry.
    return new AddMsg(gen.newCSN(),
        personWithUUIDEntry.getName(),
        user1entryUUID,
        null,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAllAttributes(), null);
  }

  /** Check that the expected number of changes are in the replication server database. */
  private void checkChangelogSize(int expectedCount) throws Exception
  {
    // TODO : commented this throw because test is executed through a slow test
    //throw new RuntimeException("Dead code. Should we remove this method and the test calling it?");
  }

  /** SingleRS tests basic features of generationID with one single Replication Server. */
  @Test
  public void testSingleRS() throws Exception
  {
    String testCase = "testSingleRS";
    debugInfo("Starting " + testCase + " debugEnabled:" + logger.isTraceEnabled());

    debugInfo(testCase + " Clearing DS1 backend");
    // Special test were we want to test with an empty backend. So free it
    // for this special test.
    TestCaseUtils.initializeTestBackend(false);

    try
    {
      long rsGenId;
      long dsGenId;

      replServer1 = createReplicationServer(replServerId1, false, testCase);

      // To search the replication server db later in these tests, we need
      // to attach the search backend to the replication server just created.

      //===========================================================
      debugInfo(testCase + " ** TEST ** Empty backend");

      connectServer1ToReplServer(replServer1);

      debugInfo(testCase
          + " Expect genId to be not retrievable from suffix root entry");
      dsGenId = readGenIdFromSuffixRootEntry(false);
      assertEquals(dsGenId,-1);

      debugInfo(testCase
          + " Expect genId to be set in memory on the replication "
          + " server side (not wrote on disk/db since no change occurred).");
      rsGenId = replServer1.getGenerationId(baseDN);
      assertEquals(rsGenId, EMPTY_DN_GENID);

      // Clean for next test
      disconnectFromReplServer(replServer1);


      // ===========================================================
      debugInfo(testCase + " ** TEST ** Non empty backend");

      debugInfo(testCase + " Adding test entries to DS");
      addTestEntriesToDB(updatedEntries);

      connectServer1ToReplServer(replServer1);

      debugInfo(testCase
          + " Test that the generationId is written in the DB in the root entry on DS1");
      dsGenId = readGenIdFromSuffixRootEntry(true);
      Assertions.assertThat(dsGenId).isNotIn(-1, EMPTY_DN_GENID);

      debugInfo(testCase + " Test that the generationId is set on RS1");
      rsGenId = replServer1.getGenerationId(baseDN);
      assertEquals(dsGenId, rsGenId);

      //===========================================================
      debugInfo(testCase + " ** TEST ** DS2 connection to RS1 with bad genID");

      broker2 = openReplicationSession(server2ID, replServer1, dsGenId + 1);

      // ===========================================================
      debugInfo(testCase + " ** TEST ** DS3 connection to RS1 with good genID");
      broker3 = openReplicationSession(server3ID, replServer1, dsGenId);

      // ===========================================================
      debugInfo(testCase
          + " ** TEST ** DS2 (bad genID) changes must be ignored.");

      broker2.publish(createAddMsg());
      assertNoMessageReceived(
          broker3,
          "broker3",
          "Note that timeout should be lower than RS monitoring publisher period so that timeout occurs");

      // ===========================================================
      debugInfo(testCase
          + " ** TEST ** The part of the topology with the right gen ID should work well");

      // Now create a change that must be replicated
      waitConnectionToReplicationDomain(baseDN, 3000);
      addTestEntriesToDB(createEntry(UUID.randomUUID()));

      // Verify that RS1 does contain the change related to this ADD.
      checkChangelogSize(1);

      // Verify that DS3 receives this change
      ReplicationMsg msg = broker3.receive();
      Assertions.assertThat(msg).isInstanceOf(AddMsg.class);
      debugInfo("Broker 3 received expected update msg" + msg);

      //===========================================================
      debugInfo(testCase + " ** TEST ** Persistence of the generation ID in RS1");

      final long genIdBeforeShut = replServer1.getGenerationId(baseDN);

      debugInfo("Shutdown replServer1");
      stop(broker2, broker3);
      broker2 = broker3 = null;
      replServer1.remove();
      replServer1 = null;

      debugInfo("Create again replServer1");
      replServer1 = createReplicationServer(replServerId1, false, testCase);

      // To search the replication server db later in these tests, we need
      // to attach the search backend to the replication server just created.
      debugInfo("Delay to allow DS to reconnect to replServer1");

      final long genIdAfterRestart = replServer1.getGenerationId(baseDN);
      debugInfo("Aft restart / replServer.genId=" + genIdAfterRestart);
      assertNotNull(replServer1, "Replication server creation failed.");
      assertEquals(genIdAfterRestart, genIdBeforeShut,
        "generationId is expected to have the same value" +
        " after replServer1 restart. Before : " + genIdBeforeShut +
        " after : " + genIdAfterRestart);

      // By the way also verify that no change occurred on the replication server db
      // and still contain the ADD submitted initially.
      checkChangelogSize(1);

      //===============================================================
      debugInfo(testCase + " ** TEST ** Import with new data set + reset will"+
          " spread a new gen ID on the topology, verify DS1 and RS1");
      debugInfo("Create again broker2");
      broker2 = openReplicationSession(server2ID, replServer1, dsGenId);
      assertTrue(broker2.isConnected(), "Broker2 failed to connect to replication server");

      debugInfo("Create again broker3");
      broker3 = openReplicationSession(server3ID, replServer1, dsGenId);
      assertTrue(broker3.isConnected(), "Broker3 failed to connect to replication server");


      debugInfo("Launch on-line import on DS1");
      long oldGenId = dsGenId;
      dsGenId=-1;
      disconnectFromReplServer(replServer1);
      performLdifImport();
      connectServer1ToReplServer(replServer1);

      debugInfo("Create Reset task on DS1 to propagate the new gen ID as the reference");
      executeTask(createSetGenerationIdTask(dsGenId, ""), 20000);

      // Broker 2 and 3 should receive 1 change status message to order them
      // to enter the bad gen id status
      ChangeStatusMsg csMsg = waitForSpecificMsg(broker2, ChangeStatusMsg.class);
      assertEquals(csMsg.getRequestedStatus(), ServerStatus.BAD_GEN_ID_STATUS,
          "Broker 2 connection is expected to receive 1 ChangeStatusMsg"
              + " to enter the bad gen id status" + csMsg);
      csMsg = waitForSpecificMsg(broker3, ChangeStatusMsg.class);
      assertEquals(csMsg.getRequestedStatus(), ServerStatus.BAD_GEN_ID_STATUS,
          "Broker 2 connection is expected to receive 1 ChangeStatusMsg"
              + " to enter the bad gen id status" + csMsg);

      debugInfo("DS1 root entry must contain the new gen ID");
      dsGenId = readGenIdFromSuffixRootEntry(true);
      Assertions.assertThat(dsGenId)
        .as("DS is expected to have a new genID computed after on-line import")
        .isNotIn(-1);
      Assertions.assertThat(dsGenId)
        .as("The new genID after import and reset of genID is expected to be diffrent from previous one")
        .isNotIn(oldGenId);

      debugInfo("RS1 must have the new gen ID");
      rsGenId = replServer1.getGenerationId(baseDN);
      assertEquals(dsGenId, rsGenId, "DS and replServer are expected to have same genId.");

      debugInfo("RS1 must have been cleared since it has not the proper generation ID");
      checkChangelogSize(0);

      assertFalse(isDegradedDueToGenerationId(replServer1, server1ID),
          "Expecting that DS1 status in RS1 is : not in bad gen id.");

      //===============================================================
      debugInfo(testCase + " ** TEST ** Previous test set a new gen ID on the "+
          "topology, verify degradation of DS2 and DS3");

      assertTrue(isDegradedDueToGenerationId(replServer1, server2ID),
          "Expecting that DS2 with old gen ID is in bad gen id from RS1");
      assertTrue(isDegradedDueToGenerationId(replServer1, server3ID),
          "Expecting that DS3 with old gen ID is in bad gen id from RS1");

      debugInfo("Add entries to DS1, update should not be sent to DS2 and DS3 that are in bad gen id");
      waitConnectionToReplicationDomain(baseDN, 3000);
      addTestEntriesToDB(createEntry(UUID.randomUUID()));

      debugInfo("RS1 must have stored that update.");
      checkChangelogSize(1);

      assertNoMessageReceived(broker2, "broker2", "bad gen id");
      assertNoMessageReceived(broker3, "broker3", "bad gen id");

      debugInfo("DS2 is publishing a change and RS1 must ignore this change, DS3 must not receive it.");
      final AddMsg emsg = createAddMsg();
      broker2.publish(emsg);

      // Updates count in RS1 must stay unchanged = to 1
      checkChangelogSize(1);

      assertNoMessageReceived(broker3, "broker3", "bad gen id");


      //===============================================================
      debugInfo(testCase + " ** TEST ** Previous test put DS2 and DS3 in bad gen id, "+
          " now simulates \"dsreplication initialize \"by doing a TU+reset " +
          " from DS1 to DS2 and DS3, verify NON degradation of DS2 and DS3");

      // In S1 launch the total update to initialize S2
      addTask(taskInitRemoteS2, ResultCode.SUCCESS, null);

      // S2 should be re-initialized and have a new valid genId

      // Signal that we just entered the full update status
      broker2.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      int receivedEntriesNb = receiveImport(broker2, server2ID, null);
      debugInfo("broker2 has been initialized from DS with #entries=" + receivedEntriesNb);

      // Simulates the broker restart at the end of the import
      broker2.stop();
      broker2 = openReplicationSession(server2ID, replServer1, dsGenId);

      // Simulates the broker restart at the end of the import
      broker3.stop();
      broker3 = openReplicationSession(server3ID, replServer1, dsGenId);

      debugInfo("Adding reset task to DS1");
      executeTask(createSetGenerationIdTask(dsGenId, ""), 20000);

      debugInfo("Verify that RS1 has still the right genID");
      assertEquals(replServer1.getGenerationId(baseDN), rsGenId);

      // Updates count in RS1 must stay unchanged = to 1
      checkChangelogSize(1);

      debugInfo("Verifying that DS2 is not in bad gen id any more");
      assertFalse(isDegradedDueToGenerationId(replServer1, server2ID),
          "Expecting that DS2 is not in bad gen id from RS1");

      debugInfo("Verifying that DS3 is not in bad gen id any more");
      assertFalse(isDegradedDueToGenerationId(replServer1, server3ID),
          "Expecting that DS3 is not in bad gen id from RS1");

      debugInfo("Verify that DS2 receives the add message stored in RS1 DB");
      msg = broker2.receive();
      Assertions.assertThat(msg).isInstanceOf(AddMsg.class);

      debugInfo("Verify that DS3 receives the add message stored in RS1 DB");
      msg = broker3.receive();
      Assertions.assertThat(msg).isInstanceOf(AddMsg.class);

      debugInfo("DS2 is publishing a change and RS1 must store this change, DS3 must receive it.");
      final AddMsg emsg2 = createAddMsg();
      broker2.publish(emsg2);

      checkChangelogSize(2);

      /* expected */
      msg = broker3.receive();
      Assertions.assertThat(msg).isInstanceOf(AddMsg.class);
      AddMsg rcvmsg = (AddMsg)msg;
      assertEquals(rcvmsg.getCSN(), emsg2.getCSN());

      //===============================================================
      debugInfo(testCase + " ** TEST ** General cleaning");

      debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(replServer1);

      debugInfo("Successfully ending " + testCase);
    } finally
    {
      postTest();
    }
  }

  /**
   * Waits for the connection from server1 to the replication domain to
   * establish itself up automatically.
   */
  private void waitConnectionToReplicationDomain(final DN baseDN, int timeout) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(timeout, MILLISECONDS)
      .sleepTimes(50, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
        assertNotNull(domain);
        assertTrue(domain.isConnected(), "server should have been connected to replication domain" + baseDN);
      }
    });
  }

  private Entry createSetGenerationIdTask(Long genId, String additionalAttribute) throws Exception
  {
    String genIdString = genId != null ? genId.toString() : "";
    return TestCaseUtils.makeEntry(
        "dn: ds-task-id=resetgenid-" + genIdString+"_"+UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top", "objectclass: ds-task",
        "objectclass: ds-task-reset-generation-id",
        "ds-task-class-name: org.opends.server.tasks.SetGenerationIdTask",
        "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr,
        additionalAttribute);
  }

  private void assertNoMessageReceived(ReplicationBroker broker,
      String brokerName, String reason)
  {
    try
    {
      ReplicationMsg msg = broker.receive();
      fail("No update message is supposed to be received by " + brokerName
          + " reason='" + reason + "'. msg=" + msg);
    }
    catch (SocketTimeoutException expected)
    { /* expected */
    }
  }

  /**
   * Tests basic features of generationID with more than one Replication Server.
   * The following test focus on:
   * - genId checking across multiple starting RS (replication servers)
   * - genId setting propagation from one RS to the others
   * - genId reset propagation from one RS to the others
   */
  @Test(dependsOnMethods = { "testSingleRS" })
  public void testMultiRS() throws Exception
  {
    String testCase = "testMultiRS";
    long genId;

    debugInfo("Starting " + testCase);

    try
    {
      // Special test were we want to test with an empty backend. So free it
      // for this special test.
      TestCaseUtils.initializeTestBackend(false);

      debugInfo("Creating 3 RSs");
      replServer1 = createReplicationServer(replServerId1, true, testCase);
      replServer2 = createReplicationServer(replServerId2, true, testCase);
      replServer3 = createReplicationServer(replServerId3, true, testCase);

      connectServer1ToReplServer(replServer1);
      Thread.sleep(2000); //wait for all RS handshakes to complete

      debugInfo("Expect genId are set in all replServers.");
      waitForStableGenerationId(EMPTY_DN_GENID);
      disconnectFromReplServer(replServer1);

      debugInfo("Expect genIds to be resetted in all servers to -1 as no more DS in topo - after 10 sec");
      waitForStableGenerationId(-1);

      debugInfo("Add entries to DS");
      addTestEntriesToDB(updatedEntries);

      connectServer1ToReplServer(replServer2);

      debugInfo("Expect genIds to be set in all servers based on the added entries.");
      genId = readGenIdFromSuffixRootEntry(true);
      Assertions.assertThat(genId).isNotEqualTo(-1);
      waitForStableGenerationId(genId);

      debugInfo("Connecting broker2 to replServer3 with a good genId");
      broker2 = openReplicationSession(server2ID, replServer3, genId);
      Thread.sleep(3000);

      debugInfo("Expecting that broker2 is not in bad gen id since it has a correct genId");
      assertFalse(isDegradedDueToGenerationId(replServer1, server2ID));

      disconnectFromReplServer(replServer1);

      debugInfo("Verifying that all replservers genIds have been reset.");

      debugInfo("Expect all genIds to keep their value since broker2 is still connected.");
      waitForStableGenerationId(genId);

      debugInfo("Connecting broker3 to replServer1 with a bad genId");
      long badGenId = 1;
      broker3 = openReplicationSession(server3ID, replServer1, badGenId);
      Thread.sleep(3000);

      debugInfo("Expecting that broker3 is in bad gen id since it has a bad genId");
      assertTrue(isDegradedDueToGenerationId(replServer1, server3ID));
      assertEquals(countUpdatedEntriesInDb(), updatedEntries.length);

      connectServer1ToReplServer(replServer1);


      debugInfo("Adding reset task to DS.");
      executeTask(createSetGenerationIdTask(genId, ""), 90000);

      debugInfo("Verifying that all replservers genIds have been reset.");
      genId = readGenIdFromSuffixRootEntry(true);
      assertGenIdEquals(genId);

      Thread.sleep(3000);
      debugInfo("Adding reset task to DS." + genId);
      executeTask(createSetGenerationIdTask(genId, "ds-task-reset-generation-id-new-value: -1"), 90000);

      debugInfo("Verifying that all replservers genIds have been reset.");
      waitForStableGenerationId(-1);

      debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(replServer1);

      debugInfo("Successfully ending " + testCase);
    } finally
    {
      postTest();
    }
  }

  private void waitForStableGenerationId(final long expectedGenId) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(30, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertGenIdEquals(expectedGenId);
      }
    });
  }

  private void assertGenIdEquals(long expectedGenId)
  {
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(replServer1.getGenerationId(baseDN)).as("in replServer1").isEqualTo(expectedGenId);
    softly.assertThat(replServer2.getGenerationId(baseDN)).as("in replServer2").isEqualTo(expectedGenId);
    softly.assertThat(replServer3.getGenerationId(baseDN)).as("in replServer3").isEqualTo(expectedGenId);
    softly.assertAll();
  }

  private boolean isDegradedDueToGenerationId(ReplicationServer rs, int serverId)
  {
    ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN);
    return domain.isDegradedDueToGenerationId(serverId);
  }

  /** Disconnect broker and remove entries from the local DB. */
  private void postTest() throws Exception
  {
    debugInfo("Post test cleaning.");

    stop(broker2, broker3);
    broker2 = broker3 = null;
    remove(replServer1, replServer2, replServer3);
    replServer1 = replServer2 = replServer3 = null;
    try {
    	super.cleanRealEntries();
    }catch(Exception e) {}
    replServerPort = TestCaseUtils.findFreePorts(3);

    debugInfo("Clearing DJ backend");
    TestCaseUtils.initializeTestBackend(false);
  }

  /**
   * Test generationID saving when the root entry does not exist
   * at the moment when the replication is enabled.
   */
  @Test(dependsOnMethods = { "testMultiRS" }, groups = "slow")
  public void testServerStop() throws Exception
  {
    String testCase = "testServerStop";
    debugInfo("Starting "+ testCase + " debugEnabled:" + logger.isTraceEnabled());

    debugInfo(testCase + " Clearing DS1 backend");
    // Special test were we want to test with an empty backend. So free it
    // for this special test.
    TestCaseUtils.initializeTestBackend(false);

    try
    {
      replServer1 = createReplicationServer(replServerId1, false, testCase);

      /*
       * Test  : empty replicated backend
       * Check : nothing is broken - no generationId generated
       */
      connectServer1ToReplServer(replServer1);
      assertEquals(readGenIdFromSuffixRootEntry(false), -1,
          "genId attribute should not be retrievable since there are NO entry in the backend");

      waitConnectionToReplicationDomain(baseDN, 3000);
      addTestEntriesToDB(updatedEntries);
      assertEquals(readGenIdFromSuffixRootEntry(true), EMPTY_DN_GENID,
          "genId attribute should be retrievable since there IS one entry in the backend");

      disconnectFromReplServer(replServer1);
    }
    finally
    {
      postTest();
      debugInfo("Successfully ending " + testCase);
    }
  }

  /**
   * Loop opening sessions to the Replication Server
   * to check that it handle correctly disconnection and reconnection.
   */
  @Test(dependsOnMethods = { "testServerStop" }, groups = "slow")
  public void testLoop() throws Exception
  {
    String testCase = "testLoop";
    debugInfo("Starting "+ testCase + " debugEnabled:" + logger.isTraceEnabled());
    long rsGenId;

    // Special test were we want to test with an empty backend. So free it
    // for this special test.
    TestCaseUtils.initializeTestBackend(false);

    replServer1 = createReplicationServer(replServerId1, false, testCase);
    clearChangelogDB(replServer1);

    ReplicationBroker broker = null;
    try
    {
      for (int i=0; i< 5; i++)
      {
        long generationId = 1000+i;
        broker = openReplicationSession(server2ID, replServer1, generationId);
        debugInfo(testCase + " Expect genId to be set in memory on the replication " +
          " server side even if not wrote on disk/db since no change occurred.");
        rsGenId = replServer1.getGenerationId(baseDN);
        assertEquals(rsGenId, generationId);
        broker.stop();
        broker = null;
        Thread.sleep(2000); // Let time to RS to clear info about previous connection
      }
    } finally
    {
      stop(broker);
      postTest();
      debugInfo("Successfully ending " + testCase);
    }
  }

  protected ReplicationBroker openReplicationSession(int serverId, ReplicationServer replServer, long generationId)
      throws Exception
  {
    return openReplicationSession(baseDN, serverId, 100, replServer.getReplicationPort(), 1900, generationId);
  }
}
