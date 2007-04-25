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

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.UUID;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.messages.TaskMessages;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.changelog.Changelog;
import org.opends.server.replication.changelog.ChangelogFakeConfiguration;
import org.opends.server.replication.plugin.ChangelogBroker;
import org.opends.server.replication.plugin.SynchronizationDomain;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.RoutableMessage;
import org.opends.server.replication.protocol.SocketSession;
import org.opends.server.replication.protocol.SynchronizationMessage;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.opends.server.messages.SynchronizationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

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

public class InitOnLineTest extends SynchronizationTestCase
{
  private static final int WINDOW_SIZE = 10;
  private static final int CHANGELOG_QUEUE_SIZE = 100;

  private static final String SYNCHRONIZATION_STRESS_TEST =
    "Synchronization Stress Test";

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
  short server1ID = 1;
  short server2ID = 2;
  short server3ID = 3;
  short changelog1ID = 12;
  short changelog2ID = 13;
  int changelogPort = 8989;

  private DN baseDn;
  ChangelogBroker server2 = null;
  Changelog changelog1 = null;
  Changelog changelog2 = null;
  boolean emptyOldChanges = true;
  SynchronizationDomain sd = null;

  private void log(String s)
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "InitOnLineTests/" + s, 1);
    if (debugEnabled())
    {
      debugInfo(s);
    }
  }
  protected void log(String message, Exception e)
  {
    log(message + stackTraceToSingleLineString(e));
  }

  /**
   * Set up the environment for performing the tests in this Class.
   * synchronization
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

    // Disable schema check
    schemaCheck = DirectoryServer.checkSchema();
    DirectoryServer.setCheckSchema(false);

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
    // Add the synchronization plugin: synchroPluginEntry & synchroPluginStringDN
    // Add synchroServerEntry
    // Add changeLogEntry
    configureSynchronization();

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

    // Change log
    String changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
    + "objectClass: top\n"
    + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
    + "cn: Changelog Server\n" + "ds-cfg-changelog-port: 8990\n"
    + "ds-cfg-changelog-server-id: 1\n"
    + "ds-cfg-window-size: " + WINDOW_SIZE + "\n"
    + "ds-cfg-changelog-max-queue-size: " + CHANGELOG_QUEUE_SIZE;
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);
    changeLogEntry = null;

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

  private void addTask(Entry taskEntry, ResultCode expectedResult,
      int errorMessageID)
  {
    try
    {
      log("AddTask/" + taskEntry);

      // Change config of DS to launch the total update task
      InternalClientConnection connection =
        InternalClientConnection.getRootConnection();

      // Add the task.

      AddOperation addOperation =
        connection.processAdd(taskEntry.getDN(),
            taskEntry.getObjectClasses(),
            taskEntry.getUserAttributes(),
            taskEntry.getOperationalAttributes());

      assertEquals(addOperation.getResultCode(), expectedResult,
          "Result of ADD operation of the task is: "
          + addOperation.getResultCode()
          + " Expected:"
          + expectedResult + " Details:" + addOperation.getErrorMessage()
          + addOperation.getAdditionalLogMessage());

      if (expectedResult != ResultCode.SUCCESS)
      {
        assertTrue(addOperation.getErrorMessage().toString().
            startsWith(getMessage(errorMessageID).toString()),
            "Error MsgID of the task <"
            + addOperation.getErrorMessage()
            + "> equals <"
            + getMessage(errorMessageID) + ">");
        log("Create config task: <"+ errorMessageID + addOperation.getErrorMessage() + ">");

      }
      else
      {
        waitTaskState(taskEntry, TaskState.RUNNING, -1);
      }

      // Entry will be removed at the end of the test
      entryList.addLast(taskEntry.getDN());

      log("AddedTask/" + taskEntry.getDN());
    }
    catch(Exception e)
    {
      fail("Exception when adding task:"+ e.getMessage());
    }
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

  private void waitTaskState(Entry taskEntry, TaskState expectedTaskState,
      int expectedMessage)
  {
    TaskState taskState = null;
    try
    {

      SearchFilter filter =
        SearchFilter.createFilterFromString("(objectclass=*)");
      Entry resultEntry = null;
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

        try
        {
          // Check that the task state is as expected.
          AttributeType taskStateType =
            DirectoryServer.getAttributeType(ATTR_TASK_STATE.toLowerCase());
          String stateString =
            resultEntry.getAttributeValue(taskStateType,
                DirectoryStringSyntax.DECODER);
          taskState = TaskState.fromString(stateString);
        }
        catch(Exception e)
        {
          fail("Exception"+ e.getMessage()+e.getStackTrace());
        }

        try
        {
          // Check that the left counter.
          AttributeType taskStateType =
            DirectoryServer.getAttributeType(ATTR_TASK_INITIALIZE_LEFT, true);
          String leftString =
            resultEntry.getAttributeValue(taskStateType,
                DirectoryStringSyntax.DECODER);

          // Check that the total counter.
          taskStateType =
           DirectoryServer.getAttributeType(ATTR_TASK_INITIALIZE_DONE, true);
          String totalString =
           resultEntry.getAttributeValue(taskStateType,
               DirectoryStringSyntax.DECODER);
        }
        catch(Exception e)
        {
          fail("Exception"+ e.getMessage()+e.getStackTrace());
        }

        Thread.sleep(2000);
      }
      while ((taskState != expectedTaskState) &&
          (taskState != TaskState.STOPPED_BY_ERROR));

      // Check that the task contains some log messages.
      AttributeType logMessagesType = DirectoryServer.getAttributeType(
          ATTR_TASK_LOG_MESSAGES.toLowerCase());
      ArrayList<String> logMessages = new ArrayList<String>();
      resultEntry.getAttributeValues(logMessagesType,
          DirectoryStringSyntax.DECODER,
          logMessages);

      if ((taskState != TaskState.COMPLETED_SUCCESSFULLY)
          && (taskState != TaskState.RUNNING))
      {
        if (logMessages.size() == 0)
        {
          fail("No log messages were written to the task entry on a failed task");
        }
        else
        {
          if (expectedMessage > 0)
          {
            log(logMessages.get(0));
            log(getMessage(expectedMessage));
            assertTrue(logMessages.get(0).indexOf(
                getMessage(expectedMessage))>0);
          }
        }
      }

      assertEquals(taskState, expectedTaskState, "Task State:" + taskState +
          " Expected task state:" + expectedTaskState);
    }
    catch(Exception e)
    {
      fail("waitTaskState Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
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
        AddOperation addOp = new AddOperation(connection,
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
    String[] entries =
    {
        "dn: dc=example,dc=com\n"
        + "objectClass: top\n"
        + "objectClass: domain\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
        + "\n",
          "dn: ou=People,dc=example,dc=com\n"
        + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
        + "\n",
          "dn: cn=Fiona Jensen,ou=people,dc=example,dc=com\n"
        + "objectclass: top\n"
        + "objectclass: person\n"
        + "objectclass: organizationalPerson\n"
        + "cn: Fiona Jensen\n"
        + "sn: Jensen\n"
        + "uid: fiona\n"
        + "telephonenumber: +1 408 555 1212\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111113\n"
        + "\n",
          "dn: cn=Robert Langman,ou=people,dc=example,dc=com\n"
        + "objectclass: top\n"
        + "objectclass: person\n"
        + "objectclass: organizationalPerson\n"
        + "cn: Robert Langman\n"
        + "sn: Langman\n"
        + "uid: robert\n"
        + "telephonenumber: +1 408 555 1213\n"
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
  private void makeBrokerPublishEntries(ChangelogBroker broker,
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

  void receiveUpdatedEntries(ChangelogBroker broker, short serverID,
      String[] updatedEntries)
  {
    // Expect the broker to receive the entries
    SynchronizationMessage msg;
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
              + getMessage(em.getMsgID())
              + " " + em.getDetails());
          break;
        }
        else
        {
          log("Broker " + serverID + " receives and trashes " + msg);
        }
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
   * Creates a new changelog server.
   * @param changelogId The serverID of the changelog to create.
   * @return The new changelog server.
   */
  private Changelog createChangelogServer(short changelogId)
  {
    try
    {
      if ((changelogId==changelog1ID)&&(changelog1!=null))
        return changelog1;

      if ((changelogId==changelog2ID)&&(changelog2!=null))
        return changelog2;

      {
        int chPort = getChangelogPort(changelogId);

        ChangelogFakeConfiguration conf =
          new ChangelogFakeConfiguration(chPort, null, 0, changelogId, 0, 100,
                                         null);
        Changelog changelog = new Changelog(conf);
        Thread.sleep(1000);

        return changelog;
      }
    }
    catch (Exception e)
    {
      fail("createChangelog" + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Create a synchronized suffix in the current server providing the
   * changelog serverID.
   * @param changelogID
   */
  private void connectServer1ToChangelog(short changelogID)
  {
    // Connect DS to the changelog
    try
    {
      // suffix synchronized
      String synchroServerStringDN = synchroPluginStringDN;
      String synchroServerLdif =
        "dn: cn=example, cn=domains" + synchroServerStringDN + "\n"
      + "objectClass: top\n"
      + "objectClass: ds-cfg-synchronization-provider-config\n"
      + "cn: example\n"
      + "ds-cfg-synchronization-dn: dc=example,dc=com\n"
      + "ds-cfg-changelog-server: localhost:"
      + getChangelogPort(changelogID)+"\n"
      + "ds-cfg-directory-server-id: " + server1ID + "\n"
      + "ds-cfg-receive-status: true\n"
//    + "ds-cfg-heartbeat-interval: 0 ms\n"
      + "ds-cfg-window-size: " + WINDOW_SIZE;

      if (synchroServerEntry == null)
      {
        synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
        DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
        assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
        entryList.add(synchroServerEntry.getDN());

        sd = SynchronizationDomain.retrievesSynchronizationDomain(baseDn);

        // Clear the backend
        SynchronizationDomain.clearJEBackend(false,
            sd.getBackend().getBackendID(),
            baseDn.toNormalizedString());

      }
      if (sd != null)
      {
         log("SynchronizationDomain: Import/Export is running ? " + sd.ieRunning());
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
    return (changelogPort+changelogID);
  }

  /**
   * Tests the import side of the Initialize task
   */
  @Test(enabled=false)
  public void InitializeImport() throws Exception
  {
    String testCase = "InitializeImport";

    log("Starting "+testCase);

    try
    {
      changelog1 = createChangelogServer(changelog1ID);

      // Connect DS to the changelog
      connectServer1ToChangelog(changelog1ID);

      if (server2 == null)
        server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      Thread.sleep(2000);

      // In S1 launch the total update
      addTask(taskInitFromS2, ResultCode.SUCCESS, 0);

      // S2 should receive init msg
      SynchronizationMessage msg;
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

      cleanEntries();

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
  @Test(enabled=false)
  public void InitializeExport() throws Exception
  {
    String testCase = "Synchronization/InitializeExport";

    log("Starting "+testCase);

    changelog1 = createChangelogServer(changelog1ID);

    // Connect DS to the changelog
    connectServer1ToChangelog(changelog1ID);

    addTestEntriesToDB();

    if (server2 == null)
      server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    Thread.sleep(3000);

    InitializeRequestMessage initMsg = new InitializeRequestMessage(baseDn,
        server2ID, server1ID);
    server2.publish(initMsg);

    receiveUpdatedEntries(server2, server2ID, updatedEntries);

    cleanEntries();

    log("Successfully ending "+testCase);
}

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=false)
  public void InitializeTargetExport() throws Exception
  {
    String testCase = "Synchronization/InitializeTargetExport";

    log("Starting " + testCase);

    changelog1 = createChangelogServer(changelog1ID);

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    // Add in S1 the entries to be exported
    addTestEntriesToDB();

    // S1 is the server we are running in, S2 is simulated by a broker
    if (server2 == null)
      server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    Thread.sleep(1000);

    // Launch in S1 the task that will initialize S2
    addTask(taskInitTargetS2, ResultCode.SUCCESS, 0);

    // Wait for task completion
    waitTaskState(taskInitTargetS2, TaskState.COMPLETED_SUCCESSFULLY, -1);

    // Tests that entries have been received by S2
    receiveUpdatedEntries(server2, server2ID, updatedEntries);

    cleanEntries();

    log("Successfully ending " + testCase);

  }

  /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=false)
  public void InitializeTargetExportAll() throws Exception
  {
    String testCase = "Synchronization/InitializeTargetExportAll";

    log("Starting " + testCase);

    changelog1 = createChangelogServer(changelog1ID);

    // Creates config to synchronize suffix
    connectServer1ToChangelog(changelog1ID);

    // Add in S1 the entries to be exported
    addTestEntriesToDB();

    // S1 is the server we are running in, S2 and S3 are simulated by brokers
    if (server2==null)
      server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    ChangelogBroker server3 = openChangelogSession(DN.decode("dc=example,dc=com"),
        server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

    Thread.sleep(1000);

    // Launch in S1 the task that will initialize S2
    addTask(taskInitTargetAll, ResultCode.SUCCESS, 0);

    // Wait for task completion
    waitTaskState(taskInitTargetAll, TaskState.COMPLETED_SUCCESSFULLY, -1);

    // Tests that entries have been received by S2
    receiveUpdatedEntries(server2, server2ID, updatedEntries);
    receiveUpdatedEntries(server3, server3ID, updatedEntries);

    cleanEntries();

    log("Successfully ending " + testCase);

  }

 /**
   * Tests the import side of the InitializeTarget task
   */
  @Test(enabled=false)
  public void InitializeTargetImport() throws Exception
  {
    String testCase = "InitializeTargetImport";

    try
    {
      log("Starting " + testCase + " debugEnabled:" + debugEnabled());

      // Start SS
      changelog1 = createChangelogServer(changelog1ID);

      // S1 is the server we are running in, S2 is simulated by a broker
      if (server2==null)
        server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges);

      // Creates config to synchronize suffix
      connectServer1ToChangelog(changelog1ID);

      // S2 publishes entries to S1
      makeBrokerPublishEntries(server2, server2ID, server1ID, server2ID);

      Thread.sleep(10000); // FIXME - how to know the import is done

      // Test that entries have been imported in S1
      testEntriesInDb();

      cleanEntries();

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
  public void InitializeTargetConfigErrors() throws Exception
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
          TaskMessages.MSGID_TASK_INITIALIZE_INVALID_DN);

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
          MSGID_NO_MATCHING_DOMAIN);

      // Invalid scope
      // createTask(taskInitTargetS2);

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      cleanEntries();

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
  public void InitializeConfigErrors() throws Exception
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
          "objectclass: ds-task-initialize",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: foo",
          "ds-task-initialize-source: " + server2ID);
      addTask(taskInit, ResultCode.INVALID_DN_SYNTAX,
          TaskMessages.MSGID_TASK_INITIALIZE_INVALID_DN);

      // Domain base dn not related to any domain
      taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: dc=foo",
          "ds-task-initialize-source: " + server2ID);
      addTask(taskInit, ResultCode.OTHER, MSGID_NO_MATCHING_DOMAIN);

      // Invalid Source
      taskInit = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-initialize",
          "ds-task-class-name: org.opends.server.tasks.InitializeTask",
          "ds-task-initialize-domain-dn: " + baseDn,
          "ds-task-initialize-source: -3");
      addTask(taskInit, ResultCode.OTHER,
          MSGID_INVALID_IMPORT_SOURCE);

      // Scope containing a serverID absent from the domain
      // createTask(taskInitTargetS2);

      cleanEntries();

      log("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    }
  }

  @Test(enabled=false)
  public void InitializeTargetBroken() throws Exception
  {
    String testCase = "InitializeTargetBroken";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void InitializeBroken() throws Exception
  {
    String testCase = "InitializeBroken";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void InitializeTargetExportMultiSS() throws Exception
  {
    String testCase = "Synchronization/InitializeTargetExportMultiSS";

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
      server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog2ID), 1000, emptyOldChanges);
    }

    Thread.sleep(1000);

    // Launch in S1 the task that will initialize S2
    addTask(taskInitTargetS2, ResultCode.SUCCESS, 0);

    // Wait for task completion
    waitTaskState(taskInitTargetS2, TaskState.COMPLETED_SUCCESSFULLY, -1);

    // Tests that entries have been received by S2
    receiveUpdatedEntries(server2, server2ID, updatedEntries);

    cleanEntries();

    changelog2.shutdown();
    changelog2 = null;

    log("Successfully ending " + testCase);

  }

  @Test(enabled=false)
  public void InitializeExportMultiSS() throws Exception
  {
    String testCase = "Synchronization/InitializeExportMultiSS";
    log("Starting "+testCase);

    // Create 2 changelogs
    changelog1 = createChangelogServer(changelog1ID);
    Thread.sleep(3000);

    changelog2 = createChangelogServer(changelog2ID);
    Thread.sleep(3000);

    // Connect DS to the changelog 1
    connectServer1ToChangelog(changelog1ID);

    // Put entries in DB
    addTestEntriesToDB();

    // Connect a broker acting as server 2 to changelog2
    if (server2 == null)
    {
      server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
        server2ID, 100, getChangelogPort(changelog2ID),
        1000, emptyOldChanges);
    }

    Thread.sleep(3000);

    // S2 sends init request
    InitializeRequestMessage initMsg =
      new InitializeRequestMessage(baseDn, server2ID, server1ID);
    server2.publish(initMsg);

    // S2 should receive target, entries & done
    receiveUpdatedEntries(server2, server2ID, updatedEntries);

    cleanEntries();

    changelog2.shutdown();
    changelog2 = null;

    log("Successfully ending "+testCase);
  }

  @Test(enabled=false)
  public void InitializeNoSource() throws Exception
  {
    String testCase = "InitializeNoSource";
    log("Starting "+testCase);

    // Start SS
    changelog1 = createChangelogServer(changelog1ID);

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
        "ds-task-initialize-replica-server-id: " + 20);

    addTask(taskInit, ResultCode.SUCCESS, 0);

    waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        MSGID_NO_REACHABLE_PEER_IN_THE_DOMAIN);

    if (sd != null)
    {
       log("SynchronizationDomain: Import/Export is running ? " + sd.ieRunning());
    }

    log("Successfully ending "+testCase);

  }

  @Test(enabled=false)
  public void InitializeTargetNoTarget() throws Exception
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
        "objectclass: ds-task-initialize-target",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-target-domain-dn: "+baseDn,
        "ds-task-initialize-target-scope: " + 10);

    addTask(taskInit, ResultCode.SUCCESS, 0);

    waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        MSGID_NO_REACHABLE_PEER_IN_THE_DOMAIN);

    if (sd != null)
    {
       log("SynchronizationDomain: Import/Export is running ? " + sd.ieRunning());
    }

    log("Successfully ending "+testCase);
  }

  @Test(enabled=false)
  public void InitializeStopped() throws Exception
  {
    String testCase = "InitializeStopped";
    fail(testCase + " NYI");
  }
  @Test(enabled=false)
  public void InitializeTargetStopped() throws Exception
  {
    String testCase = "InitializeTargetStopped";
    fail(testCase + " NYI");
  }
  @Test(enabled=false)
  public void InitializeCompressed() throws Exception
  {
    String testCase = "InitializeStopped";
    fail(testCase + " NYI");
  }
  @Test(enabled=false)
  public void InitializeTargetEncrypted() throws Exception
  {
    String testCase = "InitializeTargetCompressed";
    fail(testCase + " NYI");
  }

  @Test(enabled=false)
  public void InitializeSimultaneous() throws Exception
  {
    String testCase = "InitializeSimultaneous";

    // Start SS
    changelog1 = createChangelogServer(changelog1ID);

    // Connect a broker acting as server 2 to changelog2
    if (server2 == null)
    {
      server2 = openChangelogSession(DN.decode("dc=example,dc=com"),
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

    addTask(taskInit, ResultCode.SUCCESS, 0);

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
    addTask(taskInit2, ResultCode.SUCCESS, 0);

    waitTaskState(taskInit2, TaskState.STOPPED_BY_ERROR,
        MSGID_SIMULTANEOUS_IMPORT_EXPORT_REJECTED);

    // First task is stilll running
    waitTaskState(taskInit, TaskState.RUNNING, -1);

    // External request is supposed to be rejected

    // Now tests error in the middle of an import
    // S2 sends init request
    ErrorMessage msg =
      new ErrorMessage(server1ID, 1, "");
    server2.publish(msg);

    waitTaskState(taskInit, TaskState.STOPPED_BY_ERROR,
        1);

    cleanEntries();

    log("Successfully ending "+testCase);

  }

  /**
   * Disconnect broker and remove entries from the local DB
   * @throws Exception
   */
  protected void cleanEntries()
  {

    if (sd != null)
    {
       log("SynchronizationDomain: Import/Export is running ? " + sd.ieRunning());
    }

    // Clean brokers
    if (server2 != null)
    {
      server2.stop();

      TestCaseUtils.sleep(100); // give some time to the broker to disconnect
      // fromthe changelog server.
      server2 = null;
    }
    super.cleanRealEntries();
  }
}
