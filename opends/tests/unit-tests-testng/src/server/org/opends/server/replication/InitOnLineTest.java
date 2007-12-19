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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import static org.opends.server.config.ConfigConstants.ATTR_TASK_COMPLETION_TIME;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_INITIALIZE_DONE;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_INITIALIZE_LEFT;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_LOG_MESSAGES;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_STATE;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.messages.TaskMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.net.SocketTimeoutException;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.RoutableMessage;
import org.opends.server.replication.protocol.SocketSession;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.Base64;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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

public class InitOnLineTest extends ReplicationTestCase
 {
  /**
   * The tracer object for the debug logger
   */
  private static final DebugTracer TRACER = getTracer();

  private static final int WINDOW_SIZE = 10;

  /**
   * A "person" entry
   */
  protected Entry personEntry;
  protected Entry taskInitFromS2;
  protected Entry taskInitTargetS2;
  protected Entry taskInitTargetAll;

  SocketSession ssSession = null;
  boolean ssShutdownRequested = false;
  protected String[] updatedEntries;
  boolean externalDS = false;
  private static final short server1ID = 1;
  private static final short server2ID = 2;
  private static final short server3ID = 3;
  private static final short changelog1ID =  8;
  private static final short changelog2ID =  9;
  private static final short changelog3ID = 10;

  private static int[] replServerPort = new int[20];
  
  private DN baseDn;
  ReplicationBroker server2 = null;
  ReplicationBroker server3 = null;
  ReplicationServer changelog1 = null;
  ReplicationServer changelog2 = null;
  ReplicationServer changelog3 = null;
  boolean emptyOldChanges = true;
  ReplicationDomain replDomain = null;

  private void log(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.INFORMATION, 
        "InitOnLineTests/" + s));
    if (debugEnabled())
    {
      TRACER.debugInfo(s);
    }
  }
  protected void log(String message, Exception e)
  {
    log(message + stackTraceToSingleLineString(e));
  }

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    log("Setup: debugEnabled:" + debugEnabled());

    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();
    baseDn = DN.decode("dc=example,dc=com");

    updatedEntries = newLDIFEntries();

    // Create an internal connection in order to provide operations
    // to DS to populate the db -
    connection = InternalClientConnection.getRootConnection();

    // Synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Synchro multi-master
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
      + synchroStringDN;

    // Synchro suffix
    synchroServerEntry = null;

    // Add config entries to the current DS server based on :
    // Add the replication plugin: synchroPluginEntry & synchroPluginStringDN
    // Add synchroServerEntry
    // Add replServerEntry
    configureReplication();

    taskInitFromS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: dc=example,dc=com",
        "ds-task-initialize-replica-server-id: " + server2ID);

    taskInitTargetS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: dc=example,dc=com",
        "ds-task-initialize-replica-server-id: " + server2ID);

    taskInitTargetAll = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: dc=example,dc=com",
        "ds-task-initialize-replica-server-id: all");

    replServerEntry = null;

  }

  // Tests that entries have been written in the db
  private void testEntriesInDb()
  {
    log("TestEntriesInDb");
    short found = 0;

    for (String entry : updatedEntries)
    {

      int dns = entry.indexOf("dn: ");
      int dne = entry.indexOf("dc=com");
      String dn = entry.substring(dns+4,dne+6);

      log("Search Entry: " + dn);

      DN entryDN = null;
      try
      {
        entryDN = DN.decode(dn);
      }
      catch(Exception e)
      {
        log("TestEntriesInDb/" + e);
      }

      try
      {
        Entry resultEntry = getEntry(entryDN, 1000, true);
        if (resultEntry==null)
        {
          log("Entry not found <" + dn + ">");
        }
        else
        {
          log("Entry found <" + dn + ">");
          found++;
        }
      }
      catch(Exception e)
      {
        log("TestEntriesInDb/", e);
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
   * @param expectedState The expected state fot this task.
   * @param expectedLeft The expected number of entries still to be processed.
   * @param expectedDone The expected numner of entries to be processed.
   */
  private void waitTaskCompleted(Entry taskEntry, TaskState expectedState,
      long expectedLeft, long expectedDone)
  {
    try
    {
      // FIXME - Factorize with TasksTestCase
      // Wait until the task completes.
      int timeout = 2000;

      AttributeType completionTimeType = DirectoryServer.getAttributeType(
          ATTR_TASK_COMPLETION_TIME.toLowerCase());
      SearchFilter filter =
        SearchFilter.createFilterFromString("(objectclass=*)");
      Entry resultEntry = null;
      String completionTime = null;
      long startMillisecs = System.currentTimeMillis();
      do
      {
        InternalSearchOperation searchOperation =
          connection.processSearch(taskEntry.getDN(),
              SearchScope.BASE_OBJECT,
              filter);
        try
        {
          resultEntry = searchOperation.getSearchEntries().getFirst();
        } catch (Exception e)
        {
          // FIXME How is this possible?  Must be issue 858.
          fail("Task entry was not returned from the search.");
          continue;
        }
        completionTime =
          resultEntry.getAttributeValue(completionTimeType,
              DirectoryStringSyntax.DECODER);

        if (completionTime == null)
        {
          if (System.currentTimeMillis() - startMillisecs > 1000*timeout)
          {
            break;
          }
          Thread.sleep(10);
        }
      } while (completionTime == null);

      if (completionTime == null)
      {
        fail("The task had not completed after " + timeout + " seconds.");
      }

      // Check that the task state is as expected.
      AttributeType taskStateType =
        DirectoryServer.getAttributeType(ATTR_TASK_STATE.toLowerCase());
      String stateString =
        resultEntry.getAttributeValue(taskStateType,
            DirectoryStringSyntax.DECODER);
      TaskState taskState = TaskState.fromString(stateString);
      assertEquals(taskState, expectedState,
          "The task completed in an unexpected state");

      // Check that the task contains some log messages.
      AttributeType logMessagesType = DirectoryServer.getAttributeType(
          ATTR_TASK_LOG_MESSAGES.toLowerCase());
      ArrayList<String> logMessages = new ArrayList<String>();
      resultEntry.getAttributeValues(logMessagesType,
          DirectoryStringSyntax.DECODER,
          logMessages);
      if (taskState != TaskState.COMPLETED_SUCCESSFULLY &&
          logMessages.size() == 0)
      {
        fail("No log messages were written to the task entry on a failed task");
      }

      try
      {
        // Check that the task state is as expected.
        taskStateType =
          DirectoryServer.getAttributeType(ATTR_TASK_INITIALIZE_LEFT, true);
        stateString =
          resultEntry.getAttributeValue(taskStateType,
              DirectoryStringSyntax.DECODER);

        assertEquals(Long.decode(stateString).longValue(),expectedLeft,
            "The number of entries to process is not correct.");

        // Check that the task state is as expected.
        taskStateType =
          DirectoryServer.getAttributeType(ATTR_TASK_INITIALIZE_DONE, true);
        stateString =
          resultEntry.getAttributeValue(taskStateType,
              DirectoryStringSyntax.DECODER);

        assertEquals(Long.decode(stateString).longValue(),expectedDone,
            "The number of entries processed is not correct.");

      }
      catch(Exception e)
      {
        fail("Exception"+ e.getMessage()+e.getStackTrace());
      }

    }
    catch(Exception e)
    {
      fail("Exception"+ e.getMessage()+e.getStackTrace());
    }
  }

  /**
   * Add to the current DB the entries necessary to the test
   */
  private void addTestEntriesToDB()
  {
    try
    {
      for (String ldifEntry : updatedEntries)
      {
        Entry entry = TestCaseUtils.entryFromLdifString(ldifEntry);
        AddOperationBasis addOp = new AddOperationBasis(connection,
            InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
            entry.getUserAttributes(), entry.getOperationalAttributes());
        addOp.setInternalOperation(true);
        addOp.run();
        if (addOp.getResultCode() != ResultCode.SUCCESS)
        {
          log("addEntry: Failed" + addOp.getResultCode());
        }

        // They will be removed at the end of the test
        entryList.addLast(entry.getDN());
      }
    }
    catch(Exception e)
    {
      fail("addEntries Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    }
  }

  /*
   * Creates entries necessary to the test.
   */
  private String[] newLDIFEntries()
  {
    // It is relevant to test ReplLDIFInputStream
    // and ReplLDIFOutputStream with big entries
    char bigAttributeValue[] = new char[30240];
    for (int i=0; i<bigAttributeValue.length; i++)
      bigAttributeValue[i] = Integer.toString(i).charAt(0);

    String[] entries =
    {
        "dn: dc=example,dc=com\n"
        + "objectClass: top\n"
        + "objectClass: domain\n"
        + "dc: example\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
        + "\n",
          "dn: ou=People,dc=example,dc=com\n"
        + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "ou: People\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
        + "\n",
          "dn: cn=Fiona Jensen,ou=people,dc=example,dc=com\n"
        + "objectclass: top\n"
        + "objectclass: person\n"
        + "objectclass: organizationalPerson\n"
        + "objectclass: inetOrgPerson\n"
        + "cn: Fiona Jensen\n"
        + "sn: Jensen\n"
        + "uid: fiona\n"
        + "telephonenumber:: "+ Base64.encode(
            new String(bigAttributeValue).getBytes())+"\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111113\n"
        + "\n",
          "dn: cn=Robert Langman,ou=people,dc=example,dc=com\n"
        + "objectclass: top\n"
        + "objectclass: person\n"
        + "objectclass: organizationalPerson\n"
        + "objectclass: inetOrgPerson\n"
        + "cn: Robert Langman\n"
        + "sn: Langman\n"
        + "uid: robert\n"
        + "telephonenumber: "+ new String(bigAttributeValue)+"\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111114\n"
        + "\n"
        };

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
      short senderID, short destinationServerID, short requestorID)
  {
    // Send entries
    try
    {
      RoutableMessage initTargetMessage = new InitializeTargetMessage(
          baseDn, server2ID, destinationServerID, requestorID, updatedEntries.length);
      broker.publish(initTargetMessage);

      for (String entry : updatedEntries)
      {
        log("Broker will pusblish 1 entry: bytes:"+ entry.length());

        EntryMessage entryMsg = new EntryMessage(senderID, destinationServerID,
            entry.getBytes());
        broker.publish(entryMsg);
      }

      DoneMessage doneMsg = new DoneMessage(senderID, destinationServerID);
      broker.publish(doneMsg);

      log("Broker " + senderID + " published entries");

    }
    catch(Exception e)
    {
      fail("makeBrokerPublishEntries Exception:"+ e.getMessage() + " "
          + stackTraceToSingleLineString(e));
    }
  }

  void receiveUpdatedEntries(ReplicationBroker broker, short serverID,
      String[] updatedEntries)
  {
    // Expect the broker to receive the entries
    ReplicationMessage msg;
    short entriesReceived = 0;
    while (true)
    {
      try
      {
        log("Broker " + serverID + " Wait for entry or done msg");
        msg = broker.receive();

        if (msg == null)
          break;

        if (msg instanceof InitializeTargetMessage)
        {
          log("Broker " + serverID + " receives InitializeTargetMessage ");
          entriesReceived = 0;
        }
        else if (msg instanceof EntryMessage)
        {
          EntryMessage em = (EntryMessage)msg;
          log("Broker " + serverID + " receives entry " + new String(em.getEntryBytes()));
          entriesReceived++;
        }
        else if (msg instanceof DoneMessage)
        {
          log("Broker " + serverID + "  receives done ");
          break;
        }
        else if (msg instanceof ErrorMessage)
        {
          ErrorMessage em = (ErrorMessage)msg;
          log("Broker " + serverID + "  receives ERROR "
              + " " + em.getDetails());
          break;
        }
        else
        {
          log("Broker " + serverID + " receives and trashes " + msg);
        }
      }
      catch (SocketTimeoutException e)
      {
        log("SocketTimeoutException while waiting fro entries" +
            stackTraceToSingleLineString(e));
      }
      catch(Exception e)
      {
        log("receiveUpdatedEntries" + stackTraceToSingleLineString(e));
      }
    }

    assertTrue(entriesReceived == updatedEntries.length,
        " Received entries("+entriesReceived +
        ") == Expected entries("+updatedEntries.length+")");
  }

  /**
   * Creates a new replicationServer.
   * @param changelogId The serverID of the replicationServer to create.
   * @return The new replicationServer.
   */
  private ReplicationServer createChangelogServer(short changelogId)
  {
    SortedSet<String> servers = null;
    servers = new TreeSet<String>();
    try
    {
      if (changelogId==changelog1ID)
      {
        if (changelog1!=null)
          return changelog1;
      }
      else if (changelogId==changelog2ID)
      {
        if (changelog2!=null)
          return changelog2;
      }
      else if (changelogId==changelog3ID)
      {
        if (changelog3!=null)
          return changelog3;
      }
      servers.add("localhost:" + getChangelogPort(changelog1ID));
      servers.add("localhost:" + getChangelogPort(changelog2ID));
      servers.add("localhost:" + getChangelogPort(changelog3ID));

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            getChangelogPort(changelogId), 
            "rsdbdirname" + getChangelogPort(changelogId), 
            0, 
            changelogId, 
            0, 
            100,
            servers);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      Thread.sleep(1000);

      return replicationServer;
    }
    catch (Exception e)
    {
      fail("createChangelog" + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Create a synchronized suffix in the current server providing the
   * replication Server ID.
   * @param changelogID
   */
  private void connectServer1ToChangelog(short changelogID)
  {
    // Connect DS to the replicationServer
    try
    {
      // suffix synchronized
      String synchroServerStringDN = synchroPluginStringDN;
      String synchroServerLdif =
        "dn: cn=example, cn=domains," + synchroServerStringDN + "\n"
      + "objectClass: top\n"
      + "objectClass: ds-cfg-synchronization-provider\n"
      + "objectClass: ds-cfg-replication-domain\n"
      + "cn: example\n"
      + "ds-cfg-base-dn: dc=example,dc=com\n"
      + "ds-cfg-replication-server: localhost:"
      + getChangelogPort(changelogID)+"\n"
      + "ds-cfg-server-id: " + server1ID + "\n"
      + "ds-cfg-receive-status: true\n"
//    + "ds-cfg-heartbeat-interval: 0 ms\n"
      + "ds-cfg-window-size: " + WINDOW_SIZE;

      if (synchroServerEntry == null)
      {
        synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
        DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
        assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
        super.configEntryList.add(synchroServerEntry.getDN());

        replDomain = ReplicationDomain.retrievesReplicationDomain(baseDn);

        // Clear the backend
        ReplicationDomain.clearJEBackend(false,
            replDomain.getBackend().getBackendID(),
            baseDn.toNormalizedString());

      }
      if (replDomain != null)
      {
         assertTrue(!replDomain.ieRunning(),
           "ReplicationDomain: Import/Export is not expected to be running");
      }
    }
    catch(Exception e)
    {
      log("connectServer1ToChangelog", e);
      fail("connectServer1ToChangelog", e);
    }
  }

  private int getChangelogPort(short changelogID)
  {
    if (replServerPort[changelogID] == 0)
    {
      try
      {
        // Find  a free port for the replicationServer
        ServerSocket socket = TestCaseUtils.bindFreePort();
        replServerPort[changelogID] = socket.getLocalPort();
        socket.close();
      }
      catch(Exception e)
      {
        fail("Cannot retrieve a free port for replication server."
          + e.getMessage());
      }
    }
    return replServerPort[changelogID];
  }

  /**
   * Tests the import side of the Initialize task
   */
  @Test(enabled=true)
  public void initializeImport() throws Exception
  {
    String testCase = "InitializeImport";

    log("Starting "+testCase);

    try
    {
      changelog1 = createChangelogServer(changelog1ID);

      // Connect DS to the replicationServer
      connectServer1ToChangelog(changelog1ID);

      if (server2 == null)
        server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      Thread.sleep(2000);

      // In S1 launch the total update
      addTask(taskInitFromS2, ResultCode.SUCCESS, null);

      // S2 should receive init msg
      ReplicationMessage msg;
      msg = server2.receive();
      if (!(msg instanceof InitializeRequestMessage))
      {
        fail(testCase + " Message received by S2 is of unexpected class" + msg);
      }
      InitializeRequestMessage initMsg = (InitializeRequestMessage)msg;

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, initMsg.getsenderID(),
          initMsg.getsenderID());

      // Wait for task (import) completion in S1
      waitTaskCompleted(taskInitFromS2, TaskState.COMPLETED_SUCCESSFULLY,
          0, updatedEntries.length);

      // Test import result in S1
      testEntriesInDb();

      afterTest();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    }
  }

  /**
   * Tests the export side of the Initialize task
   */
  @Test(enabled=true)
  public void initializeExport() throws Exception
  {
    String testCase = "Replication/InitializeExport";

    log("Starting "+testCase);

    changelog1 = createChangelogServer(changelog1ID);

    // Connect DS to the replicationServer
    connectServer1ToChangelog(changelog1ID);

    addTestEntriesToDB();

    if (server2 == null)
      server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    Thread.sleep(3000);

    InitializeRequestMessage initMsg = new InitializeRequestMessage(baseDn,
        server2ID, server1ID);
    server2.publish(initMsg);

    receiveUpdatedEntries(server2, server2ID, updatedEntries);

    afterTest();

    log("Successfully ending "+testCase);
}

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true)
  public void initializeTargetExport() throws Exception
  {
    String testCase = "Replication/InitializeTargetExport";

    log("Starting " + testCase);

    changelog1 = createChangelogServer(changelog1ID);

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    // Add in S1 the entries to be exported
    addTestEntriesToDB();

    // S1 is the server we are running in, S2 is simulated by a broker
    if (server2 == null)
      server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    Thread.sleep(1000);

    // Launch in S1 the task that will initialize S2
    addTask(taskInitTargetS2, ResultCode.SUCCESS, null);

    // Wait for task completion
    waitTaskState(taskInitTargetS2, TaskState.COMPLETED_SUCCESSFULLY, null);

    // Tests that entries have been received by S2
    receiveUpdatedEntries(server2, server2ID, updatedEntries);

    afterTest();

    log("Successfully ending " + testCase);

  }

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true)
  public void initializeTargetExportAll() throws Exception
  {
    String testCase = "Replication/InitializeTargetExportAll";

    log("Starting " + testCase);

    changelog1 = createChangelogServer(changelog1ID);

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    // Add in S1 the entries to be exported
    addTestEntriesToDB();

    // S1 is the server we are running in, S2 and S3 are simulated by brokers
    if (server2==null)
      server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    if (server3==null)
    server3 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    Thread.sleep(1000);

    // Launch in S1 the task that will initialize S2
    addTask(taskInitTargetAll, ResultCode.SUCCESS, null);

    // Wait for task completion
    waitTaskState(taskInitTargetAll, TaskState.COMPLETED_SUCCESSFULLY, null);

    // Tests that entries have been received by S2
    receiveUpdatedEntries(server2, server2ID, updatedEntries);
    receiveUpdatedEntries(server3, server3ID, updatedEntries);

    afterTest();

    log("Successfully ending " + testCase);

  }

 /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true)
  public void initializeTargetImport() throws Exception
  {
    String testCase = "InitializeTargetImport";

    try
    {
      log("Starting " + testCase + " debugEnabled:" + debugEnabled());

      // Start SS
      changelog1 = createChangelogServer(changelog1ID);

      // S1 is the server we are running in, S2 is simulated by a broker
      if (server2==null)
        server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, server1ID, server2ID);

      Thread.sleep(10000); // FIXME - how to know the import is done

      // Test that entries have been imported in S1
      testEntriesInDb();

      afterTest();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    }
  }

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=false)
  public void initializeTargetConfigErrors() throws Exception
  {
    String testCase = "InitializeTargetConfigErrors";

    try
    {
      log("Starting " + testCase);

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
          ERR_NO_MATCHING_DOMAIN.get());

      // Invalid scope
      // createTask(taskInitTargetS2);

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      afterTest();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    }
  }

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true)
  public void initializeConfigErrors() throws Exception
  {
    String testCase = "InitializeConfigErrors";

    try
    {
      log("Starting " + testCase);

      // Start SS
      changelog1 = createChangelogServer(changelog1ID);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // Invalid domain base dn
      Entry taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-from-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: foo",
          "ds-task-initialize-replica-server-id: " + server2ID);
      addTask(taskInit, ResultCode.INVALID_DN_SYNTAX,
          ERR_TASK_INITIALIZE_INVALID_DN.get());

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
      addTask(taskInit, ResultCode.OTHER, ERR_NO_MATCHING_DOMAIN.get());

      // Invalid Source
      taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize-from-remote-replica",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: " + baseDn,
          "ds-task-initialize-replica-server-id: -3");
      addTask(taskInit, ResultCode.OTHER,
          ERR_INVALID_IMPORT_SOURCE.get());

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      afterTest();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
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

  /*
   * TestReplServerInfos tests that in a topology with more
   * than one replication server, in each replication server
   * is stored the list of LDAP servers connected to each
   * replication server of the topology, thanks to the
   * ReplServerInfoMessage(s) exchanged by the replication
   * servers.
   */
  @Test(enabled=true)
  public void testReplServerInfos() throws Exception
  {
    String testCase = "Replication/TestReplServerInfos";

    log("Starting " + testCase);

    // Create the Repl Servers
    changelog1 = createChangelogServer(changelog1ID);
    changelog2 = createChangelogServer(changelog2ID);
    changelog3 = createChangelogServer(changelog3ID);

    // Connects lDAP1 to replServer1
    connectServer1ToChangelog(changelog1ID);
    
    // Connects lDAP2 to replServer2
    ReplicationBroker broker2 = 
      openReplicationSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);

    // Connects lDAP3 to replServer2
    ReplicationBroker broker3 =
      openReplicationSession(DN.decode("dc=example,dc=com"),
        server3ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);

    // Check that the list of connected LDAP servers is correct
    // in each replication servers
    List<String> l1 = changelog1.getReplicationServerDomain(baseDn, false).
      getConnectedLDAPservers();
    assertEquals(l1.size(), 1);
    assertEquals(l1.get(0), String.valueOf(server1ID));
    
    List<String> l2;
    l2 = changelog2.getReplicationServerDomain(baseDn, false).getConnectedLDAPservers();
    assertEquals(l2.size(), 2);
    assertTrue(l2.contains(String.valueOf(server2ID)));
    assertTrue(l2.contains(String.valueOf(server3ID)));
        
    List<String> l3;
    l3 = changelog3.getReplicationServerDomain(baseDn, false).getConnectedLDAPservers();
    assertEquals(l3.size(), 0);

    // Test updates
    broker3.stop();
    Thread.sleep(1000);
    l2 = changelog2.getReplicationServerDomain(baseDn, false).getConnectedLDAPservers();
    assertEquals(l2.size(), 1);
    assertEquals(l2.get(0), String.valueOf(server2ID));

    broker3 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server3ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);
    broker2.stop();
    Thread.sleep(1000);
    l2 = changelog2.getReplicationServerDomain(baseDn, false).getConnectedLDAPservers();
    assertEquals(l2.size(), 1);
    assertEquals(l2.get(0), String.valueOf(server3ID));

    // TODO Test ReplicationServerDomain.getDestinationServers method.

    broker2.stop();
    broker3.stop();

    afterTest();

    changelog3.shutdown();
    changelog3 = null;
    changelog2.shutdown();
    changelog2 = null;
    changelog1.shutdown();
    changelog1 = null;
  }
  
  @Test(enabled=true)
  public void initializeTargetExportMultiSS() throws Exception
  {
    try
    {
      String testCase = "Replication/InitializeTargetExportMultiSS";

      log("Starting " + testCase);

      // Create 2 changelogs
      changelog1 = createChangelogServer(changelog1ID);

      changelog2 = createChangelogServer(changelog2ID);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 is simulated by a broker
      // connected to changelog2
      if (server2 == null)
      {
        server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
            server2ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);
      }

      Thread.sleep(1000);

      // Launch in S1 the task that will initialize S2
      addTask(taskInitTargetS2, ResultCode.SUCCESS, null);

      // Wait for task completion
      waitTaskState(taskInitTargetS2, TaskState.COMPLETED_SUCCESSFULLY, null);

      // Tests that entries have been received by S2
      receiveUpdatedEntries(server2, server2ID, updatedEntries);

      log("Successfully ending " + testCase);
    }
    finally
    {
      afterTest();

      changelog2.shutdown();
      changelog2 = null;
    }
  }

  @Test(enabled=false)
  public void initializeExportMultiSS() throws Exception
  {
    String testCase = "Replication/InitializeExportMultiSS";
    log("Starting "+testCase);

    // Create 2 changelogs
    changelog1 = createChangelogServer(changelog1ID);
    Thread.sleep(1000);

    changelog2 = createChangelogServer(changelog2ID);
    Thread.sleep(1000);

    // Connect DS to the replicationServer 1
    connectServer1ToChangelog(changelog1ID);

    // Put entries in DB
    log(testCase + " Will add entries");
    addTestEntriesToDB();

    // Connect a broker acting as server 2 to Repl Server 2
    if (server2 == null)
    {
      log(testCase + " Will connect server 2 to " + changelog2ID);
      server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog2ID),
        1000, emptyOldChanges, changelog1.getGenerationId(baseDn));
    }

    // Connect a broker acting as server 3 to Repl Server 3
    log(testCase + " Will create replServer " + changelog3ID);
    changelog3 = createChangelogServer(changelog3ID);
    Thread.sleep(500);
    if (server3 == null)
    {
      log(testCase + " Will connect server 3 to " + changelog3ID);
      server3 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server3ID, 100, getChangelogPort(changelog3ID),
        1000, emptyOldChanges, changelog1.getGenerationId(baseDn));
    }

    Thread.sleep(500);

    // S3 sends init request
    log(testCase + " server 3 Will send reqinit to " + server1ID);
    InitializeRequestMessage initMsg =
      new InitializeRequestMessage(baseDn, server3ID, server1ID);
    server3.publish(initMsg);

    // S3 should receive target, entries & done
    log(testCase + " Will verify server 3 has received expected entries");
    receiveUpdatedEntries(server3, server3ID, updatedEntries);

    while(true)
    {
      try
      {
        ReplicationMessage msg = server3.receive();
        fail("Receive unexpected message " + msg);
      }
      catch(SocketTimeoutException e)
      {
        // Test is a success
        break;
      }
    }
    
    afterTest();

    changelog3.shutdown();
    changelog3 = null;

    changelog2.shutdown();
    changelog2 = null;

    log("Successfully ending "+testCase);
  }

  @Test(enabled=false)
  public void initializeNoSource() throws Exception
  {
    String testCase = "InitializeNoSource";
    log("Starting "+testCase);

    // Start Replication Server
    changelog1 = createChangelogServer(changelog1ID);

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    // Test 1
    Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: "+baseDn,
        "ds-task-initialize-replica-server-id: " + 20);

    addTask(taskInit, ResultCode.SUCCESS, null);

    waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get());

    // Test 2
    taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: "+baseDn,
        "ds-task-initialize-replica-server-id: " + server1ID);

    addTask(taskInit, ResultCode.OTHER, ERR_INVALID_IMPORT_SOURCE.get());

    if (replDomain != null)
    {
       assertTrue(!replDomain.ieRunning(),
         "ReplicationDomain: Import/Export is not expected to be running");
    }

    log("Successfully ending "+testCase);

  }

  @Test(enabled=false)
  public void initializeTargetNoTarget() throws Exception
  {
    String testCase = "InitializeTargetNoTarget"  + baseDn;
    log("Starting "+testCase);

    // Start SS
    changelog1 = createChangelogServer(changelog1ID);

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    // Put entries in DB
    addTestEntriesToDB();

    Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: "+baseDn,
        "ds-task-initialize-replica-server-id: " + 0);

    addTask(taskInit, ResultCode.SUCCESS, null);

    waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get());

    if (replDomain != null)
    {
       assertTrue(!replDomain.ieRunning(),
         "ReplicationDomain: Import/Export is not expected to be running");
    }

    log("Successfully ending "+testCase);
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

  @Test(enabled=false)
  public void initializeSimultaneous() throws Exception
  {
    String testCase = "InitializeSimultaneous";

    // Start SS
    changelog1 = createChangelogServer(changelog1ID);

    // Connect a broker acting as server 2 to changelog2
    if (server2 == null)
    {
      server2 = openReplicationSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID),
        1000, emptyOldChanges);
    }

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    Entry taskInit = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-from-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTask",
        "ds-task-initialize-domain-dn: "+baseDn,
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
        "ds-task-initialize-domain-dn: "+baseDn,
        "ds-task-initialize-replica-server-id: " + server2ID);

    // Second task is expected to be rejected
    addTask(taskInit2, ResultCode.SUCCESS, null);

    waitTaskState(taskInit2, TaskState.STOPPED_BY_ERROR,
        ERR_SIMULTANEOUS_IMPORT_EXPORT_REJECTED.get());

    // First task is stilll running
    waitTaskState(taskInit, TaskState.RUNNING, null);

    // External request is supposed to be rejected

    // Now tests error in the middle of an import
    // S2 sends init request
    ErrorMessage msg =
      new ErrorMessage(server1ID, (short) 1, Message.EMPTY);
    server2.publish(msg);

    waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        null);

    afterTest();

    log("Successfully ending "+testCase);

  }

  /**
   * Disconnect broker and remove entries from the local DB
   */
  protected void afterTest()
  {

    // Check that the domain hsa completed the import/export task.
    if (replDomain != null)
    {
      // race condition could cause the main thread to reach 
      // this code before the task is fully completed.
      // in those cases, loop for a while waiting for completion.
      for (int i = 0; i< 10; i++)
      {
        if (replDomain.ieRunning())
        {
          try
          {
            Thread.sleep(500);
          } catch (InterruptedException e)
          { }
        }
        else
        {
          break;
        }
      }
       assertTrue(!replDomain.ieRunning(),
         "ReplicationDomain: Import/Export is not expected to be running");
    }

    // Clean brokers
    if (server2 != null)
    {
      server2.stop();
      TestCaseUtils.sleep(100); // give some time to the broker to disconnect
      // from the replicationServer.
      server2 = null;
    }
    if (server3 != null)
    {
      server3.stop();
      TestCaseUtils.sleep(100); // give some time to the broker to disconnect
      // from the replicationServer.
      server3 = null;
    }
    super.cleanRealEntries();
  }
}
