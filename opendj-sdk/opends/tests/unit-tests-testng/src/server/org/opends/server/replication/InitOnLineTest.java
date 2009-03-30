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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
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
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.RoutableMsg;
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
import org.testng.annotations.AfterClass;
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

  private static final String EXAMPLE_DN = "dc=example,dc=com";

  private static int[] replServerPort = new int[20];

  private DN baseDn;
  ReplicationBroker server2 = null;
  ReplicationBroker server3 = null;
  ReplicationServer changelog1 = null;
  ReplicationServer changelog2 = null;
  ReplicationServer changelog3 = null;
  boolean emptyOldChanges = true;
  LDAPReplicationDomain replDomain = null;

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
    super.setUp();

    log("Setup: debugEnabled:" + debugEnabled());

    // This test suite depends on having the schema available.
    baseDn = DN.decode(EXAMPLE_DN);

    // This test uses import tasks which do not work with memory backend
    // (like the test backend we use in every tests): backend is disabled then
    // re-enabled and this clears the backend reference and thus the underlying
    // data. So for this particular test, we use a classical backend. Let's
    // clear it.
    LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);

    updatedEntries = newLDIFEntries();

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

  // Tests that entries have been written in the db
  private void testEntriesInDb()
  {
    log("TestEntriesInDb");
    short found = 0;

    for (String entry : updatedEntries)
    {

      int dns = entry.indexOf("dn: ");
      int dne = entry.indexOf(EXAMPLE_DN);
      String dn = entry.substring(dns+4,dne+EXAMPLE_DN.length());

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
   * @param expectedState The expected state for this task.
   * @param expectedLeft The expected number of entries still to be processed.
   * @param expectedDone The expected number of entries to be processed.
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
          Thread.sleep(100);
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
   * Add to the current DB the entries necessary to the test.
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
        "dn: " + EXAMPLE_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: domain\n"
        + "dc: example\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
        + "\n",
          "dn: ou=People," + EXAMPLE_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "ou: People\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
        + "\n",
          "dn: cn=Fiona Jensen,ou=people," + EXAMPLE_DN + "\n"
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
          "dn: cn=Robert Langman,ou=people," + EXAMPLE_DN + "\n"
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
      RoutableMsg initTargetMessage =
        new InitializeTargetMsg(
          EXAMPLE_DN, server2ID, destinationServerID, requestorID,
          updatedEntries.length);
      broker.publish(initTargetMessage);

      for (String entry : updatedEntries)
      {
        log("Broker will pusblish 1 entry: bytes:"+ entry.length());

        EntryMsg entryMsg = new EntryMsg(senderID, destinationServerID,
            entry.getBytes());
        broker.publish(entryMsg);
      }

      DoneMsg doneMsg = new DoneMsg(senderID, destinationServerID);
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
    ReplicationMsg msg;
    short entriesReceived = 0;
    while (true)
    {
      try
      {
        log("Broker " + serverID + " Wait for entry or done msg");
        msg = broker.receive();

        if (msg == null)
          break;

        if (msg instanceof InitializeTargetMsg)
        {
          log("Broker " + serverID + " receives InitializeTargetMessage ");
          entriesReceived = 0;
        }
        else if (msg instanceof EntryMsg)
        {
          EntryMsg em = (EntryMsg)msg;
          log("Broker " + serverID + " receives entry " + new String(em.getEntryBytes()));
          entriesReceived+=countEntryLimits(em.getEntryBytes());
        }
        else if (msg instanceof DoneMsg)
        {
          log("Broker " + serverID + "  receives done ");
          break;
        }
        else if (msg instanceof ErrorMsg)
        {
          ErrorMsg em = (ErrorMsg)msg;
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
   * @param changelogId The serverID of the replicationServer to create.
   * @return The new replicationServer.
   */
  private ReplicationServer createChangelogServer(short changelogId, String testCase)
  {
    SortedSet<String> servers = null;
    servers = new TreeSet<String>();
    try
    {
      if (changelogId != changelog1ID)
          servers.add("localhost:" + getChangelogPort(changelog1ID));
      if (changelogId != changelog2ID)
          servers.add("localhost:" + getChangelogPort(changelog2ID));
      if (changelogId != changelog3ID)
          servers.add("localhost:" + getChangelogPort(changelog3ID));

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            getChangelogPort(changelogId),
            "initOnlineTest" + getChangelogPort(changelogId) + testCase + "Db",
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
      String testName = "initOnLineTest";
      String synchroServerLdif =
        "dn: cn=" + testName + ", cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
      + "objectClass: top\n"
      + "objectC7lass: ds-cfg-synchronization-provider\n"
      + "objectClass: ds-cfg-replication-domain\n"
      + "cn: " + testName + "\n"
      + "ds-cfg-base-dn: " + EXAMPLE_DN + "\n"
      + "ds-cfg-replication-server: localhost:"
      + getChangelogPort(changelogID)+"\n"
      + "ds-cfg-server-id: " + server1ID + "\n"
      + "ds-cfg-receive-status: true\n"
//    + "ds-cfg-heartbeat-interval: 0 ms\n"
      + "ds-cfg-window-size: " + WINDOW_SIZE;


      // Clear the backend
      LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);

      synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
      DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
      configEntryList.add(synchroServerEntry.getDN());

      replDomain = LDAPReplicationDomain.retrievesReplicationDomain(baseDn);

      assertTrue(!replDomain.ieRunning(),
        "ReplicationDomain: Import/Export is not expected to be running");
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
  @Test(enabled=true, groups="slow")
  public void initializeImport() throws Exception
  {
    String testCase = "initializeImport";

    log("Starting "+testCase);

    try
    {
      changelog1 = createChangelogServer(changelog1ID, testCase);

      // Connect DS to the replicationServer
      connectServer1ToChangelog(changelog1ID);

      if (server2 == null)
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Thread.sleep(2000);

      // In S1 launch the total update
      addTask(taskInitFromS2, ResultCode.SUCCESS, null);

      // S2 should receive init msg
      ReplicationMsg msg;
      msg = server2.receive();
      if (!(msg instanceof InitializeRequestMsg))
      {
        fail(testCase + " Message received by S2 is of unexpected class" + msg);
      }
      InitializeRequestMsg initMsg = (InitializeRequestMsg)msg;

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, initMsg.getsenderID(),
          initMsg.getsenderID());

      // Wait for task (import) completion in S1
      waitTaskCompleted(taskInitFromS2, TaskState.COMPLETED_SUCCESSFULLY,
          0, updatedEntries.length);

      // Test import result in S1
      testEntriesInDb();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    } finally
    {
      afterTest();
    }
  }

  /**
   * Tests the export side of the Initialize task
   */
  @Test(enabled=true, groups="slow")
  public void initializeExport() throws Exception
  {
    String testCase = "initializeExport";

    log("Starting "+testCase);

    try
    {
      changelog1 = createChangelogServer(changelog1ID, testCase);

      // Connect DS to the replicationServer
      connectServer1ToChangelog(changelog1ID);

      addTestEntriesToDB();

      if (server2 == null)
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Not needed anymore since OpenReplicationSession
      // checks for session establishment ?
      // Thread.sleep(3000);

      InitializeRequestMsg initMsg = new InitializeRequestMsg(EXAMPLE_DN,
        server2ID, server1ID);
      server2.publish(initMsg);

      receiveUpdatedEntries(server2, server2ID, updatedEntries);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
    }
}

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true, groups="slow")
  public void initializeTargetExport() throws Exception
  {
    String testCase = "initializeTargetExport";

    log("Starting " + testCase);

    try
    {

      changelog1 = createChangelogServer(changelog1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 is simulated by a broker
      if (server2 == null)
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Thread.sleep(1000);

      // Launch in S1 the task that will initialize S2
      addTask(taskInitTargetS2, ResultCode.SUCCESS, null);

      // Wait for task completion
      waitTaskState(taskInitTargetS2, TaskState.COMPLETED_SUCCESSFULLY, null);

      // Tests that entries have been received by S2
      receiveUpdatedEntries(server2, server2ID, updatedEntries);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
    }
  }

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true, groups="slow")
  public void initializeTargetExportAll() throws Exception
  {
    String testCase = "initializeTargetExportAll";

    log("Starting " + testCase);

    try
    {
      changelog1 = createChangelogServer(changelog1ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 and S3 are simulated by brokers
      if (server2 == null)
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      if (server3 == null)
        server3 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Thread.sleep(1000);

      // Launch in S1 the task that will initialize S2
      addTask(taskInitTargetAll, ResultCode.SUCCESS, null);

      // Wait for task completion
      waitTaskState(taskInitTargetAll, TaskState.COMPLETED_SUCCESSFULLY, null);

      // Tests that entries have been received by S2
      receiveUpdatedEntries(server2, server2ID, updatedEntries);
      receiveUpdatedEntries(server3, server3ID, updatedEntries);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
    }
  }

 /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true, groups="slow")
  public void initializeTargetImport() throws Exception
  {
    String testCase = "initializeTargetImport";

    try
    {
      log("Starting " + testCase + " debugEnabled:" + debugEnabled());

      // Start SS
      changelog1 = createChangelogServer(changelog1ID, testCase);

      // S1 is the server we are running in, S2 is simulated by a broker
      if (server2==null)
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, server1ID, server2ID);

      // wait until the replication domain has expected generationID
      // this should indicate that the import occured correctly.
      for (int count = 0; count < 100; count++)
      {
        if (replDomain.getGenerationID() == 56869)
          break;
        Thread.sleep(200);
      }

      // Test that entries have been imported in S1
      testEntriesInDb();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    } finally
    {
      afterTest();
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
          ERR_NO_MATCHING_DOMAIN.get(""));

      // Invalid scope
      // createTask(taskInitTargetS2);

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    } finally
    {
      afterTest();
    }
  }

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=true)
  public void initializeConfigErrors() throws Exception
  {
    String testCase = "initializeConfigErrors";

    try
    {
      log("Starting " + testCase);

      // Start SS
      changelog1 = createChangelogServer(changelog1ID, testCase);

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
          "ds-task-initialize-domain-dn: " + baseDn,
          "ds-task-initialize-replica-server-id: -3");
      addTask(taskInit, ResultCode.OTHER,
          ERR_INVALID_IMPORT_SOURCE.get());

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    } finally
    {
      afterTest();
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
  @Test(enabled=true, groups="slow")
  public void testReplServerInfos() throws Exception
  {
    String testCase = "testReplServerInfos";

    log("Starting " + testCase);

    ReplicationBroker broker2 = null;
    ReplicationBroker broker3 = null;
    try
    {
      // Create the Repl Servers
      changelog1 = createChangelogServer(changelog1ID, testCase);
      changelog2 = createChangelogServer(changelog2ID, testCase);
      changelog3 = createChangelogServer(changelog3ID, testCase);

      // Connects lDAP1 to replServer1
      connectServer1ToChangelog(changelog1ID);

      // Connects lDAP2 to replServer2
      broker2 = openReplicationSession(DN.decode(EXAMPLE_DN),
        server2ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);

      // Connects lDAP3 to replServer2
      broker3 = openReplicationSession(DN.decode(EXAMPLE_DN),
        server3ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);

      // Check that the list of connected LDAP servers is correct
      // in each replication servers
      List<String> l1 = changelog1.getReplicationServerDomain(
          baseDn.toNormalizedString(), false).
        getConnectedLDAPservers();
      assertEquals(l1.size(), 1);
      assertEquals(l1.get(0), String.valueOf(server1ID));

      List<String> l2;
    l2 = changelog2.getReplicationServerDomain(
        baseDn.toNormalizedString(), false).getConnectedLDAPservers();
      assertEquals(l2.size(), 2);
      assertTrue(l2.contains(String.valueOf(server2ID)));
      assertTrue(l2.contains(String.valueOf(server3ID)));

      List<String> l3;
    l3 = changelog3.getReplicationServerDomain(
        baseDn.toNormalizedString(), false).getConnectedLDAPservers();
      assertEquals(l3.size(), 0);

      // Test updates
      broker3.stop();
      Thread.sleep(1000);
    l2 = changelog2.getReplicationServerDomain(
        baseDn.toNormalizedString(), false).getConnectedLDAPservers();
      assertEquals(l2.size(), 1);
      assertEquals(l2.get(0), String.valueOf(server2ID));

      broker3 = openReplicationSession(DN.decode(EXAMPLE_DN),
        server3ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);
      broker2.stop();
      Thread.sleep(1000);
    l2 = changelog2.getReplicationServerDomain(
        baseDn.toNormalizedString(), false).getConnectedLDAPservers();
      assertEquals(l2.size(), 1);
      assertEquals(l2.get(0), String.valueOf(server3ID));

    // TODO Test ReplicationServerDomain.getDestinationServers method.

    } finally
    {
      if (broker2 != null)
        broker2.stop();
      if (broker3 != null)
        broker3.stop();
      afterTest();
    }
  }

  @Test(enabled=true, groups="slow")
  public void initializeTargetExportMultiSS() throws Exception
  {
    try
    {
      String testCase = "initializeTargetExportMultiSS";

      log("Starting " + testCase);

      // Create 2 changelogs
      changelog1 = createChangelogServer(changelog1ID, testCase);

      changelog2 = createChangelogServer(changelog2ID, testCase);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // Add in S1 the entries to be exported
      addTestEntriesToDB();

      // S1 is the server we are running in, S2 is simulated by a broker
      // connected to changelog2
      if (server2 == null)
      {
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
            server2ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);
      }

     // Thread.sleep(1000);

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
    }
  }

  @Test(enabled=true, groups="slow")
  public void initializeExportMultiSS() throws Exception
  {
    String testCase = "initializeExportMultiSS";
    log("Starting "+testCase);

    try
    {
      // Create 2 changelogs
      changelog1 = createChangelogServer(changelog1ID, testCase);
      Thread.sleep(1000);

      changelog2 = createChangelogServer(changelog2ID, testCase);
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
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server2ID, 100, getChangelogPort(changelog2ID),
          1000, emptyOldChanges,
          changelog1.getGenerationId(baseDn.toNormalizedString()));
      }

      // Connect a broker acting as server 3 to Repl Server 3
      log(testCase + " Will create replServer " + changelog3ID);
      changelog3 = createChangelogServer(changelog3ID, testCase);
      Thread.sleep(500);
      if (server3 == null)
      {
        log(testCase + " Will connect server 3 to " + changelog3ID);
        server3 = openReplicationSession(DN.decode(EXAMPLE_DN),
          server3ID, 100, getChangelogPort(changelog3ID),
          1000, emptyOldChanges,
          changelog1.getGenerationId(baseDn.toNormalizedString()));
      }

      // Thread.sleep(500);

      // S3 sends init request
      log(testCase + " server 3 Will send reqinit to " + server1ID);
      InitializeRequestMsg initMsg =
        new InitializeRequestMsg(EXAMPLE_DN, server3ID, server1ID);
      server3.publish(initMsg);

      // S3 should receive target, entries & done
      log(testCase + " Will verify server 3 has received expected entries");
      receiveUpdatedEntries(server3, server3ID, updatedEntries);

      while (true)
      {
        try
        {
          ReplicationMsg msg = server3.receive();
          fail("Receive unexpected message " + msg);
        } catch (SocketTimeoutException e)
        {
          // Test is a success
          break;
        }
      }

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
    }
  }

  @Test(enabled=false)
  public void initializeNoSource() throws Exception
  {
    String testCase = "initializeNoSource";
    log("Starting "+testCase);

    try
    {
      // Start Replication Server
      changelog1 = createChangelogServer(changelog1ID, testCase);

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
        "ds-task-initialize-domain-dn: " + baseDn,
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
        "ds-task-initialize-domain-dn: " + baseDn,
        "ds-task-initialize-replica-server-id: " + server1ID);

      addTask(taskInit, ResultCode.OTHER, ERR_INVALID_IMPORT_SOURCE.get());

      if (replDomain != null)
      {
        assertTrue(!replDomain.ieRunning(),
          "ReplicationDomain: Import/Export is not expected to be running");
      }

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
    }
  }

  @Test(enabled=false)
  public void initializeTargetNoTarget() throws Exception
  {
    String testCase = "initializeTargetNoTarget"  + baseDn;
    log("Starting "+testCase);

    try
    {
      // Start SS
      changelog1 = createChangelogServer(changelog1ID, testCase);

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
        "ds-task-initialize-domain-dn: " + baseDn,
        "ds-task-initialize-replica-server-id: " + 0);

      addTask(taskInit, ResultCode.SUCCESS, null);

      waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get());

      if (replDomain != null)
      {
        assertTrue(!replDomain.ieRunning(),
          "ReplicationDomain: Import/Export is not expected to be running");
      }

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
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
      // Start SS
      changelog1 = createChangelogServer(changelog1ID, testCase);

      // Connect a broker acting as server 2 to changelog2
      if (server2 == null)
      {
        server2 = openReplicationSession(DN.decode(EXAMPLE_DN),
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
        "ds-task-initialize-domain-dn: " + baseDn,
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
        "ds-task-initialize-domain-dn: " + baseDn,
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
      ErrorMsg msg =
        new ErrorMsg(server1ID, (short) 1, Message.EMPTY);
      server2.publish(msg);

      waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        null);

      log("Successfully ending " + testCase);
    } finally
    {
      afterTest();
    }
  }

  /**
   * Disconnect broker and remove entries from the local DB
   */
  protected void afterTest()
  {

    // Check that the domain has completed the import/export task.
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
    // Remove domain config
    super.cleanConfigEntries();
    replDomain = null;

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

    if (changelog1 != null)
    {
        changelog1.clearDb();
        changelog1.remove();
        changelog1 = null;
    }

    if (changelog2 != null)
    {
        changelog2.clearDb();
        changelog2.remove();
        changelog2 = null;
    }

    if (changelog3 != null)
    {
        changelog3.clearDb();
        changelog3.remove();
        changelog3 = null;
    }

    // Clean replication server ports
    for (int i = 0; i < replServerPort.length; i++)
    {
      replServerPort[i] = 0;
    }
  }

    /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    // Clear the backend
    LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);

    paranoiaCheck();
  }
}
