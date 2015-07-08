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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication;

import java.util.*;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.util.Base64;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.messages.TaskMessages.*;
import static org.opends.server.backends.task.TaskState.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Tests contained here:
 *
 * Initialize Test Cases <=> Pull entries
 * ---------------------
 * InitializeImport : Tests the import in the target DS.
 * Creates a task on current DS and makes a broker simulates DS2 sending entries.
 * InitializeExport : Tests the export from the source DS
 * A broker simulates DS2 pulling entries from current DS.
 *
 * Initialize Target Test Cases <=> Push entries
 * ----------------------------
 * InitializeTargetExport : Tests the export from the source DS
 * Creates a task on current DS and makes broker simulates DS2 receiving entries
 * InitializeTargetImport : Test the import in the target DS
 * A broker simulates DS2 receiving entries from current DS.
 *
 * InitializeTargetConfigErrors : Tests configuration errors of the
 * InitializeTarget task
 */
@SuppressWarnings("javadoc")
public class InitOnLineTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final int WINDOW_SIZE = 10;

  private Entry taskInitFromS2;
  private Entry taskInitTargetS2;
  private Entry taskInitTargetAll;

  private String[] updatedEntries;
  private static final int server1ID = 1;
  private static final int server2ID = 2;
  private static final int server3ID = 3;
  private static final int replServer1ID = 8;
  private static final int replServer2ID = 9;
  private static final int replServer3ID = 10;

  private static final String EXAMPLE_DN = "dc=example,dc=com";
  private static int[] replServerPort = new int[20];

  private DN baseDN;
  private ReplicationBroker server2;
  private ReplicationBroker server3;
  private ReplicationServer replServer1;
  private ReplicationServer replServer2;
  private ReplicationServer replServer3;
  private LDAPReplicationDomain replDomain;
  private int initWindow = 100;

  private void log(String s)
  {
    logger.error(LocalizableMessage.raw("InitOnLineTests/" + s));
    logger.trace(s);
  }

  /**
   * Set up the environment for performing the tests in this Class.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    log("Setup: debugEnabled:" + logger.isTraceEnabled());

    // This test suite depends on having the schema available.
    baseDN = DN.valueOf(EXAMPLE_DN);

    // This test uses import tasks which do not work with memory backend
    // (like the test backend we use in every tests): backend is disabled then
    // re-enabled and this clears the backend reference and thus the underlying
    // data. So for this particular test, we use a classical backend.
    TestCaseUtils.clearJEBackend("userRoot");

    // For most tests, a limited number of entries is enough
    updatedEntries = newLDIFEntries(2);

    // Create an internal connection in order to provide operations
    // to DS to populate the db -
    connection = InternalClientConnection.getRootConnection();

    synchroServerEntry = null;
    replServerEntry = null;

    taskInitFromS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: " + EXAMPLE_DN,
        "ds-task-initialize-replica-server-id: " + server2ID);

    taskInitTargetS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + EXAMPLE_DN,
        "ds-task-initialize-replica-server-id: " + server2ID);

    taskInitTargetAll = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + EXAMPLE_DN,
        "ds-task-initialize-replica-server-id: all");
  }

  /** Tests that entries have been written in the db. */
  private void testEntriesInDb() throws Exception
  {
    log("TestEntriesInDb");
    short found = 0;

    for (String entry : updatedEntries)
    {
      int dns = entry.indexOf("dn: ");
      int dne = entry.indexOf(EXAMPLE_DN);
      String dn = entry.substring(dns+4,dne+EXAMPLE_DN.length());

      log("Search Entry: " + dn);

      DN entryDN = DN.valueOf(dn);

      Entry resultEntry = getEntry(entryDN, 1000, true);
      if (resultEntry == null)
      {
        log("Entry not found <" + dn + ">");
      }
      else
      {
        log("Entry found <" + dn + ">");
        found++;
      }
    }

    assertEquals(found, updatedEntries.length,
        " Entries present in DB :" + found +
        " Expected entries :" + updatedEntries.length);
  }

  /**
   * Wait a task to be completed and check the expected state and expected
   * stats.
   * @param taskEntry The task to process.
   * @param expectedState The expected state for this task.
   * @param expectedLeft The expected number of entries still to be processed.
   * @param expectedDone The expected number of entries to be processed.
   */
  private void waitTaskCompleted(Entry taskEntry, TaskState expectedState,
      long expectedLeft, long expectedDone) throws Exception
  {
    log("waitTaskCompleted " + taskEntry.toLDIFString());

    {
      Entry resultEntry = getCompletionTime(taskEntry);

      // Check that the task state is as expected.
      String stateString =
          resultEntry.parseAttribute(ATTR_TASK_STATE.toLowerCase()).asString();
      TaskState taskState = TaskState.fromString(stateString);
      assertEquals(taskState, expectedState,
          "The task completed in an unexpected state");

      // Check that the task contains some log messages.
      Set<String> logMessages = resultEntry.parseAttribute(
          ATTR_TASK_LOG_MESSAGES.toLowerCase()).asSetOfString();
      if (taskState != TaskState.COMPLETED_SUCCESSFULLY &&
          logMessages.isEmpty())
      {
        fail("No log messages were written to the task entry on a failed task");
      }

      // Check that the task state is as expected.
      assertAttributeValue(resultEntry, ATTR_TASK_INITIALIZE_LEFT,
          expectedLeft, "The number of entries to process is not correct.");

      // Check that the task state is as expected.
      assertAttributeValue(resultEntry, ATTR_TASK_INITIALIZE_DONE,
          expectedDone, "The number of entries processed is not correct.");
    }
  }

  private Entry getCompletionTime(Entry taskEntry) throws Exception
  {
    // FIXME - Factorize with TasksTestCase
    // Wait until the task completes.
    int timeout = 2000;

    long startMillisecs = System.currentTimeMillis();
    do
    {
      final SearchRequest request = newSearchRequest(taskEntry.getName(), SearchScope.BASE_OBJECT);
      InternalSearchOperation searchOperation = connection.processSearch(request);
      Entry resultEntry = searchOperation.getSearchEntries().getFirst();

      String completionTime = resultEntry.parseAttribute(
          ATTR_TASK_COMPLETION_TIME.toLowerCase()).asString();
      if (completionTime != null)
      {
        return resultEntry;
      }

      if (System.currentTimeMillis() - startMillisecs > 1000 * timeout)
      {
        fail("The task had not completed after " + timeout + " seconds.");
      }
      Thread.sleep(100);
    }
    while (true);
  }

  private void assertAttributeValue(Entry resultEntry, String lowerAttrName,
      long expected, String message) throws DirectoryException
  {
    String value = resultEntry.parseAttribute(lowerAttrName).asString();
    assertEquals(Long.decode(value).longValue(), expected, message);
  }

  /**
   * Add to the current DB the entries necessary to the test.
   */
  private void addTestEntriesToDB() throws Exception
  {
    for (String ldifEntry : updatedEntries)
    {
      addTestEntryToDB(TestCaseUtils.entryFromLdifString(ldifEntry));
    }
    log("addTestEntriesToDB : " + updatedEntries.length
        + " successfully added to DB");
  }

  private void addTestEntryToDB(final Entry entry)
  {
    AddOperation addOp = connection.processAdd(entry);
    if (addOp.getResultCode() != ResultCode.SUCCESS)
    {
      log("addEntry: Failed" + addOp.getResultCode());
    }

    entriesToCleanup.add(entry.getName());
  }

  /**
   * Creates entries necessary to the test.
   */
  private String[] newLDIFEntries(int entriesCnt)
  {
    // It is relevant to test ReplLDIFInputStream
    // and ReplLDIFOutputStream with big entries
    char bigAttributeValue[] = new char[30240];
    for (int i=0; i<bigAttributeValue.length; i++)
    {
      bigAttributeValue[i] = Integer.toString(i).charAt(0);
    }

    String[] entries = new String[entriesCnt + 2];
    entries[0] = "dn: " + EXAMPLE_DN + "\n"
                 + "objectClass: top\n"
                 + "objectClass: domain\n"
                 + "dc: example\n"
                 + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
                 + "\n";
    entries[1] = "dn: ou=People," + EXAMPLE_DN + "\n"
               + "objectClass: top\n"
               + "objectClass: organizationalUnit\n"
               + "ou: People\n"
               + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
               + "\n";

    String filler = "000000000000000000000000000000000000";
    for (int i=0; i<entriesCnt; i++)
    {
      String useri="0000"+i;
      entries[i+2] = "dn: cn="+useri+",ou=people," + EXAMPLE_DN + "\n"
                   + "objectclass: top\n"
                   + "objectclass: person\n"
                   + "objectclass: organizationalPerson\n"
                   + "objectclass: inetOrgPerson\n"
                   + "cn: "+useri+"_cn"+"\n"
                   + "sn: "+useri+"_sn"+"\n"
                   + "uid: "+useri+"_uid"+"\n"
                   + "description:: "+ Base64.encode(
                       new String(bigAttributeValue).getBytes())+"\n"
                   + "entryUUID: 21111111-1111-1111-1111-"+useri+
                   filler.substring(0, 12-useri.length())+"\n"
                   + "\n";
    }

    return entries;
  }

  /**
   * Broker will send the entries to a server.
   * @param broker The broker that will send the entries.
   * @param senderID The serverID of this broker.
   * @param destinationServerID The target server.
   * @param requestorID The initiator server.
   */
  private void makeBrokerPublishEntries(ReplicationBroker broker,
      int senderID, int destinationServerID, int requestorID)
  {
    RoutableMsg initTargetMessage =
        new InitializeTargetMsg(baseDN, server2ID, destinationServerID,
            requestorID, updatedEntries.length, initWindow);
    broker.publish(initTargetMessage);

    int cnt = 0;
    for (String entry : updatedEntries)
    {
      log("Broker will publish 1 entry: bytes:" + entry.length());

      EntryMsg entryMsg =
          new EntryMsg(senderID, destinationServerID, entry.getBytes(), ++cnt);
      broker.publish(entryMsg);
    }

    DoneMsg doneMsg = new DoneMsg(senderID, destinationServerID);
    broker.publish(doneMsg);

    log("Broker " + senderID + " published entries");
  }

  private void receiveUpdatedEntries(ReplicationBroker broker) throws Exception
  {
    final int serverId = broker.getServerId();
    // Expect the broker to receive the entries
    int entriesReceived = 0;
    while (true)
    {
      log("Broker " + serverId + " Wait for entry or done msg");
      ReplicationMsg msg = broker.receive();
      if (msg == null)
      {
        break;
      }

      if (msg instanceof InitializeTargetMsg)
      {
        log("Broker " + serverId + " receives InitializeTargetMessage ");
        entriesReceived = 0;
      }
      else if (msg instanceof EntryMsg)
      {
        EntryMsg em = (EntryMsg) msg;
        log("Broker " + serverId + " receives entry " + new String(em.getEntryBytes()));
        entriesReceived += countEntryLimits(em.getEntryBytes());
      }
      else if (msg instanceof DoneMsg)
      {
        log("Broker " + serverId + "  receives done ");
        break;
      }
      else if (msg instanceof ErrorMsg)
      {
        ErrorMsg em = (ErrorMsg) msg;
        log("Broker " + serverId + "  receives ERROR " + " " + em.getDetails());
        break;
      }
      else
      {
        log("Broker " + serverId + " receives and trashes " + msg);
      }
    }

    assertEquals(entriesReceived, updatedEntries.length);

    broker.setGenerationID(EMPTY_DN_GENID);
    broker.reStart(true);
    sleep(500);
  }

  /**
   * Count the number of entries in the provided byte[].
   * This is based on the hypothesis that the entries are separated
   * by a "\n\n" String.
   *
   * @param   entryBytes
   * @return  The number of entries in the provided byte[].
   */
  private int countEntryLimits(byte[] entryBytes)
  {
    int entryCount = 0;
    int count = 0;
    while (count<=entryBytes.length-2)
    {
      if ((entryBytes[count] == '\n') && (entryBytes[count+1] == '\n'))
      {
        entryCount++;
        count++;
      }
      count++;
    }
    return entryCount;
  }

  /**
   * Creates a new replicationServer.
   * @param replServerId The serverID of the replicationServer to create.
   * @return The new replicationServer.
   */
  private ReplicationServer createReplicationServer(int replServerId,
      String testCase) throws Exception
  {
    SortedSet<String> servers = new TreeSet<>();
    if (replServerId != replServer1ID)
    {
      servers.add("localhost:" + getReplServerPort(replServer1ID));
    }
    if (replServerId != replServer2ID)
    {
      servers.add("localhost:" + getReplServerPort(replServer2ID));
    }
    if (replServerId != replServer3ID)
    {
      servers.add("localhost:" + getReplServerPort(replServer3ID));
    }

    final int port = getReplServerPort(replServerId);
    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            port,
            "initOnlineTest" + port + testCase + "Db",
            replicationDbImplementation,
            0, replServerId, 0, 100, servers);
    ReplicationServer replicationServer = new ReplicationServer(conf);
    Thread.sleep(1000);
    return replicationServer;
  }

  private void connectServer1ToReplServer(int replServerId) throws Exception
  {
    // suffix synchronized
    String testName = "initOnLineTest";
    String synchroServerLdif =
      "dn: cn=" + testName + ", cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
    + "objectClass: top\n"
    + "objectClass: ds-cfg-synchronization-provider\n"
    + "objectClass: ds-cfg-replication-domain\n"
    + "cn: " + testName + "\n"
    + "ds-cfg-base-dn: " + EXAMPLE_DN + "\n"
    + "ds-cfg-replication-server: localhost:"
    + getReplServerPort(replServerId)+"\n"
    + "ds-cfg-server-id: " + server1ID + "\n"
    + "ds-cfg-receive-status: true\n"
    + "ds-cfg-window-size: " + WINDOW_SIZE;

    TestCaseUtils.clearJEBackend("userRoot");

    addSynchroServerEntry(synchroServerLdif);

    replDomain = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
    assertTrue(!replDomain.ieRunning(),
        "ReplicationDomain: Import/Export is not expected to be running");
  }

  private int getReplServerPort(int replServerId) throws Exception
  {
    if (replServerPort[replServerId] == 0)
    {
      replServerPort[replServerId] = TestCaseUtils.findFreePort();
    }
    return replServerPort[replServerId];
  }

  /**
   * Tests the import side of the Initialize task
   * Test steps :
   * - create a task 'InitFromS2' in S1
   * - make S2 export its entries
   * - test that S1 has successfully imported the entries and completed the task.
   *
   * TODO: Error case: make S2 crash/disconnect in the middle of the export
   * and test that, on S1 side, the task ends with an error.
   * State of the backend on S1 partially initialized: ?
   */
  @Test(enabled=true, groups="slow")
  public void initializeImport() throws Exception
  {
    String testCase = "initializeImport ";
    log("Starting "+testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Connect DS to the replicationServer
      connectServer1ToReplServer(replServer1ID);

      if (server2 == null)
      {
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      // In S1 launch the total update
      addTask(taskInitFromS2, ResultCode.SUCCESS, null);

      // S2 should receive init msg
      ReplicationMsg msg = server2.receive();
      Assertions.assertThat(msg).isInstanceOf(InitializeRequestMsg.class);
      InitializeRequestMsg initMsg = (InitializeRequestMsg)msg;

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, initMsg.getSenderID(),
          initMsg.getSenderID());

      // Wait for task (import) completion in S1
      waitTaskCompleted(taskInitFromS2, TaskState.COMPLETED_SUCCESSFULLY,
          0, updatedEntries.length);

      // Test import result in S1
      testEntriesInDb();

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  /**
   * Tests the export side of the Initialize task
   * Test steps :
   * - add entries in S1, make S2 publish InitRequest
   * - test that S1 has successfully exported the entries (by receiving them
   *   on S2 side).
   */
  @Test(enabled=true, groups="slow")
  public void initializeExport() throws Exception
  {
    String testCase = "initializeExport";
    log("Starting "+testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Connect DS to the replicationServer
      connectServer1ToReplServer(replServer1ID);

      addTestEntriesToDB();

      if (server2 == null)
      {
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      InitializeRequestMsg initMsg = new InitializeRequestMsg(baseDN, server2ID, server1ID, 100);
      server2.publish(initMsg);

      // Signal RS we just entered the full update status
      server2.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      receiveUpdatedEntries(server2);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
}

  /**
   * Tests the import side of the InitializeTarget task
   * Test steps :
   * - add entries in S1 and create a task 'InitTargetS2' in S1
   * - wait task completed
   * - test that S2 has successfully received the entries
   */
  @Test(enabled=true, groups="slow")
  public void initializeTargetExport() throws Exception
  {
    String testCase = "initializeTargetExport";
    log("Starting " + testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 is simulated by a broker
      if (server2 == null)
      {
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      // Launch in S1 the task that will initialize S2
      addTask(taskInitTargetS2, ResultCode.SUCCESS, null);

      // Signal RS we just entered the full update status
      server2.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      // Tests that entries have been received by S2
      receiveUpdatedEntries(server2);

      waitTaskState(taskInitTargetS2, COMPLETED_SUCCESSFULLY, 20000, null);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  /**
   * Tests the import side of the InitializeTarget task
   * Test steps :
   * - addEntries in S1, create a task 'InitAll' in S1
   * - wait task completed on S1
   * - test that S2 and S3 have successfully imported the entries.
   *
   * TODO: Error case: make S1 crash in the middle of the export and test that
   * the task ends with an error. State of the backend on both S2 and S3: ?
   *
   * TODO: Error case: make S2 crash in the middle of the import and test what??
   */
  @Test(enabled=true, groups="slow")
  public void initializeTargetExportAll() throws Exception
  {
    String testCase = "initializeTargetExportAll";
    log("Starting " + testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 and S3 are simulated by brokers
      if (server2 == null)
      {
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      if (server3 == null)
      {
        server3 = openReplicationSession(baseDN,
          server3ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      // Launch in S1 the task that will initialize S2
      addTask(taskInitTargetAll, ResultCode.SUCCESS, null);

      // Tests that entries have been received by S2

      // Signal RS we just entered the full update status
      server2.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);
      server3.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      receiveUpdatedEntries(server2);
      receiveUpdatedEntries(server3);

      waitTaskState(taskInitTargetAll, COMPLETED_SUCCESSFULLY, 20000, null);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  /**
   * Tests the import side of the InitializeTarget task.
   */
  @Test(enabled=true, groups="slow")
  public void initializeTargetImport() throws Exception
  {
    String testCase = "initializeTargetImport";
    log("Starting " + testCase + " debugEnabled:" + logger.isTraceEnabled());
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // S1 is the server we are running in, S2 is simulated by a broker
      if (server2==null)
      {
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, server1ID, server2ID);

      // wait until the replication domain has expected generationID
      // this should indicate that the import occurred correctly.
      final long EXPECTED_GENERATION_ID = 52955L;
      long readGenerationId = -1L;
      for (int count = 0; count < 120; count++)
      {
        readGenerationId = replDomain.getGenerationID();
        if (readGenerationId == EXPECTED_GENERATION_ID)
        {
          break;
        }
        log(testCase + " genId=" + readGenerationId);
        Thread.sleep(1000);
      }

      assertEquals(readGenerationId, EXPECTED_GENERATION_ID,
          testCase + " Import success waited longer than expected");

      // Test that entries have been imported in S1
      testEntriesInDb();

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  /**
   * Tests the import side of the InitializeTarget task.
   */
  @Test(enabled=false)
  public void initializeTargetConfigErrors() throws Exception
  {
    String testCase = "InitializeTargetConfigErrors";
    log("Starting " + testCase);
    try
    {
      // Invalid domain base dn
      Entry taskInitTarget = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
          "ds-task-initialize-domain-dn: foo",
          "ds-task-initialize-remote-replica-server-id: " + server2ID);
      addTask(taskInitTarget, ResultCode.INVALID_DN_SYNTAX,
          ERR_TASK_INITIALIZE_INVALID_DN.get());

      // Domain base dn not related to any domain
      taskInitTarget = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
          "ds-task-initialize-domain-dn: dc=foo",
          "ds-task-initialize-remote-replica-server-id: " + server2ID);
      addTask(taskInitTarget, ResultCode.OTHER,
          ERR_NO_MATCHING_DOMAIN.get(""));

      // Invalid scope
      // createTask(taskInitTargetS2);

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  /**
   * Tests the import side of the InitializeTarget task.
   */
  @Test(enabled=true)
  public void initializeConfigErrors() throws Exception
  {
    String testCase = "initializeConfigErrors";
    log("Starting " + testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // Invalid domain base dn
      Entry taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-from-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: dc=foo",
          "ds-task-initialize-replica-server-id: " + server2ID);
      addTask(taskInit, ResultCode.INVALID_DN_SYNTAX,
          ERR_NO_MATCHING_DOMAIN.get("dc=foo"));

      // Domain base dn not related to any domain
      taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-from-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: dc=foo",
          "ds-task-initialize-replica-server-id: " + server2ID);
      addTask(taskInit, ResultCode.INVALID_DN_SYNTAX,
          ERR_NO_MATCHING_DOMAIN.get("dc=foo"));

      // Invalid Source
      taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-from-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: " + baseDN,
          "ds-task-initialize-replica-server-id: -3");
      addTask(taskInit, ResultCode.OTHER,
          ERR_INVALID_IMPORT_SOURCE.get(baseDN.toString(), server1ID,"-3",""));

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  @Test(enabled=false)
  public void initializeTargetBroken() throws Exception
  {
    String testCase = "InitializeTargetBroken";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void initializeBroken() throws Exception
  {
    String testCase = "InitializeBroken";
    fail(testCase + " NYI");
  }

  /**
   * TestReplServerInfos tests that in a topology with more than one replication
   * server, in each replication server is stored the list of LDAP servers
   * connected to each replication server of the topology, thanks to the
   * ReplServerInfoMessage(s) exchanged by the replication servers.
   */
  @Test(enabled=true, groups="slow")
  public void testReplServerInfos() throws Exception
  {
    String testCase = "testReplServerInfos";
    log("Starting " + testCase);

    ReplicationBroker broker2 = null;
    ReplicationBroker broker3 = null;
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);
      replServer2 = createReplicationServer(replServer2ID, testCase);
      replServer3 = createReplicationServer(replServer3ID, testCase);

      // Connects lDAP1 to replServer1
      connectServer1ToReplServer(replServer1ID);

      // Connects lDAP2 to replServer2
      broker2 = openReplicationSession(baseDN,
        server2ID, 100, getReplServerPort(replServer2ID), 1000);

      // Connects lDAP3 to replServer2
      broker3 = openReplicationSession(baseDN,
        server3ID, 100, getReplServerPort(replServer2ID), 1000);

      // Check that the list of connected LDAP servers is correct in each replication servers
      Assertions.assertThat(getConnectedDSServerIds(replServer1)).containsExactly(server1ID);
      Assertions.assertThat(getConnectedDSServerIds(replServer2)).containsExactly(server2ID, server3ID);
      Assertions.assertThat(getConnectedDSServerIds(replServer3)).isEmpty();

      // Test updates
      broker3.stop();
      Thread.sleep(1000);
      Assertions.assertThat(getConnectedDSServerIds(replServer2)).containsExactly(server2ID);

      broker3 = openReplicationSession(baseDN,
        server3ID, 100, getReplServerPort(replServer2ID), 1000);
      broker2.stop();
      Thread.sleep(1000);
      Assertions.assertThat(getConnectedDSServerIds(replServer2)).containsExactly(server3ID);

    // TODO Test ReplicationServerDomain.getDestinationServers method.

      log("Successfully ending " + testCase);
    } finally
    {
      stop(broker2, broker3);
      afterTest(testCase);
    }
  }

  private Set<Integer> getConnectedDSServerIds(ReplicationServer rs)
  {
    ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN);
    return domain.getConnectedDSs().keySet();
  }

  @Test(enabled=true, groups="slow")
  public void initializeTargetExportMultiSS() throws Exception
  {
    String testCase = "initializeTargetExportMultiSS";
    log("Starting " + testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);
      replServer2 = createReplicationServer(replServer2ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 is simulated by a broker
      // connected to replServer2
      if (server2 == null)
      {
        log(testCase + " Will connect server 2 to " + replServer2ID);
        server2 = openReplicationSession(baseDN,
            server2ID, 100, getReplServerPort(replServer2ID), 1000);
      }

      // Launch in S1 the task that will initialize S2
      log(testCase + " add task " + Thread.currentThread());
      addTask(taskInitTargetS2, ResultCode.SUCCESS, null);

      log(testCase + " " + server2.getServerId() + " wait target " + Thread.currentThread());
      waitForInitializeTargetMsg(testCase, server2);

      // Signal RS we just entered the full update status
      log(testCase + " change status");
      server2.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      // Tests that entries have been received by S2
      log(testCase + " receive entries");
      receiveUpdatedEntries(server2);

      waitTaskState(taskInitTargetS2, COMPLETED_SUCCESSFULLY, 20000, null);

      log("Successfully ending " + testCase);
    }
    finally
    {
      afterTest(testCase);
    }
  }

  private void waitForInitializeTargetMsg(String testCase,
      ReplicationBroker server) throws Exception
  {
    ReplicationMsg msgrcv;
    do
    {
      msgrcv = server.receive();
      log(testCase + " " + server.getServerId() + " receives " + msgrcv);
    }
    while (!(msgrcv instanceof InitializeTargetMsg));
    Assertions.assertThat(msgrcv).isInstanceOf(InitializeTargetMsg.class);
  }

  @Test(enabled=true, groups="slow")
  public void initializeExportMultiSS() throws Exception
  {
    String testCase = "initializeExportMultiSS";
    log("Starting "+testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);
      replServer2 = createReplicationServer(replServer2ID, testCase);
      Thread.sleep(1000);

      // Connect DS to the replicationServer 1
      connectServer1ToReplServer(replServer1ID);

      // Put entries in DB
      log(testCase + " Will add entries");
      addTestEntriesToDB();

      // Connect a broker acting as server 2 to Repl Server 2
      if (server2 == null)
      {
        log(testCase + " Will connect server 2 to " + replServer2ID);
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer2ID),
          1000, replServer1.getGenerationId(baseDN));
      }

      // Connect a broker acting as server 3 to Repl Server 3
      log(testCase + " Will create replServer " + replServer3ID);
      replServer3 = createReplicationServer(replServer3ID, testCase);
      Thread.sleep(500);
      if (server3 == null)
      {
        log(testCase + " Will connect server 3 to " + replServer3ID);
        server3 = openReplicationSession(baseDN,
          server3ID, 100, getReplServerPort(replServer3ID),
          1000, replServer1.getGenerationId(baseDN));
      }

      // S3 sends init request
      log(testCase + " server 3 Will send reqinit to " + server1ID);
      InitializeRequestMsg initMsg = new InitializeRequestMsg(baseDN, server3ID, server1ID, 100);
      server3.publish(initMsg);

      // S3 should receive target, entries & done
      waitForInitializeTargetMsg(testCase, server3);

      // Signal RS we just entered the full update status
      server3.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      log(testCase + " Will verify server 3 has received expected entries");
      receiveUpdatedEntries(server3);

      log("Successfully ending " + testCase);
    }
    finally
    {
      afterTest(testCase);
    }
  }

  @Test(enabled=false)
  public void initializeNoSource() throws Exception
  {
    String testCase = "initializeNoSource";
    log("Starting "+testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // Test 1
      Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + 20);

      addTask(taskInit, ResultCode.SUCCESS, null);
      waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR, 20000,
          ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(baseDN, "20"));

      // Test 2
      taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + server1ID);

      addTask(taskInit, ResultCode.OTHER, ERR_INVALID_IMPORT_SOURCE.get(
          baseDN.toString(), server1ID, "20", ""));

      if (replDomain != null)
      {
        assertTrue(!replDomain.ieRunning(),
          "ReplicationDomain: Import/Export is not expected to be running");
      }

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  @Test(enabled=false)
  public void initializeTargetNoTarget() throws Exception
  {
    String testCase = "initializeTargetNoTarget"  + baseDN;
    log("Starting "+testCase);
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      // Put entries in DB
      addTestEntriesToDB();

      Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + 0);

      addTask(taskInit, ResultCode.SUCCESS, null);
      waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR, 20000,
          ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(baseDN, "0"));

      if (replDomain != null)
      {
        assertTrue(!replDomain.ieRunning(),
          "ReplicationDomain: Import/Export is not expected to be running");
      }

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  @Test(enabled=false)
  public void initializeStopped() throws Exception
  {
    String testCase = "InitializeStopped";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void initializeTargetStopped() throws Exception
  {
    String testCase = "InitializeTargetStopped";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void initializeCompressed() throws Exception
  {
    String testCase = "InitializeStopped";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void initializeTargetEncrypted() throws Exception
  {
    String testCase = "InitializeTargetCompressed";
    fail(testCase + " NYI");
  }

  @Test(enabled = false)
  public void initializeSimultaneous() throws Exception
  {
    String testCase = "initializeSimultaneous";
    try
    {
      replServer1 = createReplicationServer(replServer1ID, testCase);

      // Connect a broker acting as server 2 to replServer2
      if (server2 == null)
      {
        server2 = openReplicationSession(baseDN,
          server2ID, 100, getReplServerPort(replServer1ID), 1000);
      }

      // Creates config to synchronize suffix
      connectServer1ToReplServer(replServer1ID);

      Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + server2ID);

      addTask(taskInit, ResultCode.SUCCESS, null);

      Thread.sleep(3000);

      Entry taskInit2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + server2ID);

      // Second task is expected to be rejected
      addTask(taskInit2, ResultCode.SUCCESS, null);
      waitTaskState(taskInit2, STOPPED_BY_ERROR, 20000,
          ERR_SIMULTANEOUS_IMPORT_EXPORT_REJECTED.get());

      // First task is still running
      waitTaskState(taskInit, RUNNING, 20000, null);

      // External request is supposed to be rejected

      // Now tests error in the middle of an import
      // S2 sends init request
      ErrorMsg msg = new ErrorMsg(server1ID, 1, LocalizableMessage.EMPTY);
      server2.publish(msg);

      waitTaskState(taskInit, STOPPED_BY_ERROR, 20000, null);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest(testCase);
    }
  }

  /**
   * Disconnect broker and remove entries from the local DB
   * @param testCase The name of the test case.
   */
  private void afterTest(String testCase) throws Exception
  {
    // Check that the domain has completed the import/export task.
    if (replDomain != null)
    {
      // race condition could cause the main thread to reach
      // this code before the task is fully completed.
      // in those cases, loop for a while waiting for completion.
      for (int i = 0; i< 10; i++)
      {
        if (!replDomain.ieRunning())
        {
          break;
        }
        sleep(500);
      }
       assertTrue(!replDomain.ieRunning(),
         "ReplicationDomain: Import/Export is not expected to be running");
    }
    // Remove domain config
    super.cleanConfigEntries();
    replDomain = null;

    stop(server2, server3);
    sleep(100); // give some time to the brokers to disconnect from the replicationServer.
    server2 = server3 = null;

    // do not try to remove non-leaves
    entriesToCleanup.removeAll(Arrays.asList(
        baseDN,
        DN.valueOf("ou=people," + EXAMPLE_DN)));
    super.cleanRealEntries();

    remove(replServer1, replServer2, replServer3);
    replServer1 = replServer2 = replServer3 = null;

    Arrays.fill(replServerPort, 0);
    log("Successfully cleaned " + testCase);
  }

  /**
   * Clean up the environment.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    TestCaseUtils.clearJEBackend("userRoot");

    paranoiaCheck();
  }
}
