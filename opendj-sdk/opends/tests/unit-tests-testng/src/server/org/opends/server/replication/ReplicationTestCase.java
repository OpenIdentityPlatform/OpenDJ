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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.Severity;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.PersistentServerState;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.schema.IntegerSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * An abstract class that all Replication unit test should extend.
 */
@Test(groups = { "precommit", "replication" }, sequential = true)
public abstract class ReplicationTestCase extends DirectoryServerTestCase
{

  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  /**
  * The internal connection used for operation
  */
  protected InternalClientConnection connection;

  /**
   * Created entries that need to be deleted for cleanup
   */
  protected LinkedList<DN> entryList = new LinkedList<DN>();
  protected LinkedList<DN> configEntryList = new LinkedList<DN>();

  protected Entry synchroServerEntry;

  protected Entry replServerEntry;

  /**
   * Replication monitor stats
   */
  private DN monitorDn;
  private String monitorAttr;
  private long lastCount;

  /**
   * schema check flag
   */
  protected boolean schemaCheck;

  /**
   * The replication plugin entry
   */
  protected String synchroPluginStringDN =
    "cn=Multimaster Synchronization, cn=Synchronization Providers,cn=config";

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *         If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.restartServer();

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();
  }

  /**
   * Retrieves the domain associated to the baseDn, and the value of the generationId
   * of this domain. If the domain does not exist, returns the default hard-coded\
   * value of the generationId corresponding to 'no entry'.
   *
   * @param baseDn The baseDn for which we want the generationId
   * @return The value of the generationId.
   */
  static protected long getGenerationId(DN baseDn)
  {
    // This is the value of the generationId computed by the server when the
    // suffix is empty.
    long genId = 3276850;
    try
    {
      ReplicationDomain replDomain = ReplicationDomain.retrievesReplicationDomain(baseDn);
      genId = replDomain.getGenerationId();
    }
    catch(Exception e) {}  
    return genId;
  }

  /**
   * Open a replicationServer session to the local ReplicationServer.
   * The generation is read from the replicationDomain object. If it
   * does not exist, take the 'empty backend' generationID.
   */
  protected ReplicationBroker openReplicationSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, boolean emptyOldChanges)
          throws Exception, SocketException
  {    
    return openReplicationSession(baseDn, serverId, window_size,
        port, timeout, emptyOldChanges, getGenerationId(baseDn));
  }
  
  /**
   * Open a replicationServer session to the local ReplicationServer
   * providing the generationId.
   */
  protected ReplicationBroker openReplicationSession(
        final DN baseDn, short serverId, int window_size,
        int port, int timeout, boolean emptyOldChanges,
        long generationId)
  throws Exception, SocketException
  {
    ServerState state;
    if (emptyOldChanges)
       state = new PersistentServerState(baseDn);
    else
       state = new ServerState();

    ReplicationBroker broker = new ReplicationBroker(
        state, baseDn, serverId, 0, 0, 0, 0, 
        window_size, 0, generationId, getReplSessionSecurity());
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);
    TestCaseUtils.sleep(100); // give some time to the broker to connect
                              // to the replicationServer.
    if (emptyOldChanges)
    {
      /*
       * loop receiving update until there is nothing left
       * to make sure that message from previous tests have been consumed.
       */
      try
      {
        while (true)
        {
          ReplicationMessage rMsg = broker.receive();
          if (rMsg instanceof ErrorMessage)
          {
            ErrorMessage eMsg = (ErrorMessage)rMsg;
            logError(new MessageBuilder(
                "ReplicationTestCase/openReplicationSession ").append(
                " received ErrorMessage when emptying old changes ").append(
                eMsg.getDetails()).toMessage());
          }
        }
      }
      catch (Exception e)
      {
        logError(new MessageBuilder(
            "ReplicationTestCase/openChangelogSession ").append(e.getMessage())
            .append(" when emptying old changes").toMessage());
      }
    }
    return broker;
  }

  /**
   * Open a replicationServer session to the local ReplicationServer
   * with a default value generationId.
   *
   */
  protected ReplicationBroker openReplicationSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, ServerState state)
    throws Exception, SocketException
  {   
    return openReplicationSession(baseDn, serverId, window_size,
        port, timeout, state, getGenerationId(baseDn));
  }
  
  /**
   * Open a new session to the ReplicationServer
   * starting with a given ServerState.
   */
  protected ReplicationBroker openReplicationSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, ServerState state, long generationId)
          throws Exception, SocketException
  {
    ReplicationBroker broker = new ReplicationBroker(
        state, baseDn, serverId, 0, 0, 0, 0, window_size, 0, generationId,
        getReplSessionSecurity());
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);

    return broker;
  }

  /**
   * Open a replicationServer session with flow control to the local
   * ReplicationServer.
   *
   */
  protected ReplicationBroker openReplicationSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, int maxSendQueue, int maxRcvQueue,
      boolean emptyOldChanges)
      throws Exception, SocketException
  {
    return openReplicationSession(baseDn, serverId, window_size,
        port, timeout, maxSendQueue, maxRcvQueue, emptyOldChanges, 
        getGenerationId(baseDn));
  }
  
  protected ReplicationBroker openReplicationSession(
      final DN baseDn, short serverId, int window_size,
        int port, int timeout, int maxSendQueue, int maxRcvQueue,
        boolean emptyOldChanges, long generationId)
            throws Exception, SocketException
  {
    ServerState state;
    if (emptyOldChanges)
       state = new PersistentServerState(baseDn);
    else
       state = new ServerState();

    ReplicationBroker broker = new ReplicationBroker(
        state, baseDn, serverId, maxRcvQueue, 0,
        maxSendQueue, 0, window_size, 0, generationId,
        getReplSessionSecurity());
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);
    if (emptyOldChanges)
    {
      /*
       * loop receiving update until there is nothing left
       * to make sure that message from previous tests have been consumed.
       */
      try
      {
        while (true)
        {
          ReplicationMessage rMsg = broker.receive();
          if (rMsg instanceof ErrorMessage)
          {
            ErrorMessage eMsg = (ErrorMessage)rMsg;
            logError(new MessageBuilder(
                "ReplicationTestCase/openReplicationSession ").append(
                " received ErrorMessage when emptying old changes ").append(
                eMsg.getDetails()).toMessage());
          }
        }
      }
      catch (Exception e)
      { }
    }
    return broker;
  }

  /**
   * suppress all the config entries created by the tests in this class
   */
  protected void cleanConfigEntries()
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE,
        "ReplicationTestCase/Cleaning config entries"));

    DeleteOperationBasis op;
    // Delete entries
    try
    {
      while (true)
      {
        DN dn = configEntryList.removeLast();
        
        logError(Message.raw(Category.SYNC, Severity.NOTICE,
                 "cleaning config entry " + dn));

        op = new DeleteOperationBasis(connection, InternalClientConnection
            .nextOperationID(), InternalClientConnection.nextMessageID(), null,
            dn);
        op.run();
        if ((op.getResultCode() != ResultCode.SUCCESS) &&
            (op.getResultCode() != ResultCode.NO_SUCH_OBJECT))
        {
          logError(Message.raw(Category.SYNC, Severity.NOTICE,
                   "ReplicationTestCase/Cleaning config entries" +
                   "DEL " + dn +
                   " failed " + op.getResultCode().getResultCodeName()));
        }
      }
    }
    catch (NoSuchElementException e) {
      // done
    }
    synchroServerEntry = null;
  }

  /**
   * suppress all the real entries created by the tests in this class
   */
  protected void cleanRealEntries()
  {
  	logError(Message.raw(Category.SYNC, Severity.NOTICE,
        "ReplicationTestCase/Cleaning entries"));

    DeleteOperationBasis op;
    // Delete entries
    try
    {
      while (true)
      {
        DN dn = entryList.removeLast();

        op = new DeleteOperationBasis(connection, 
               InternalClientConnection.nextOperationID(), 
               InternalClientConnection.nextMessageID(), 
               null,
               dn);

        op.run();
        
        if ((op.getResultCode() != ResultCode.SUCCESS) &&
            (op.getResultCode() != ResultCode.NO_SUCH_OBJECT))
        {
          logError(Message.raw(Category.SYNC, Severity.NOTICE,
                   "ReplicationTestCase/Cleaning entries" +
                   "DEL " + dn +
                   " failed " + op.getResultCode().getResultCodeName()));
        }
      }
    }
    catch (NoSuchElementException e) {
      // done
    }
  }

  /**
   * Clean up the environment. return null;
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  public void classCleanUp() throws Exception
  {
    cleanConfigEntries();
    cleanRealEntries();

    entryList = null;
    configEntryList = null;
  }

  /**
   * Configure the replication for this test.
   */
  protected void configureReplication() throws Exception
  {
    if (replServerEntry != null)
    {
      // Add the replication server
      DirectoryServer.getConfigHandler().addEntry(replServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(replServerEntry.getDN()),
       "Unable to add the replication server");
      configEntryList.add(replServerEntry.getDN());
    }

    if (synchroServerEntry != null)
    {
      // We also have a replicated suffix (replication domain)
      DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
          "Unable to add the synchronized server");
      configEntryList.add(synchroServerEntry.getDN());
    }
  }


  /**
   * Get the value of the specified attribute for a given replication
   * domain from the monitor entry.
   * @return The monitor value
   * @throws Exception If an error occurs.
   */
  protected long getMonitorAttrValue(DN baseDn, String attr) throws Exception
  {
    String monitorFilter =
         "(&(cn=replication plugin*)(base-dn=" + baseDn + "))";

    InternalSearchOperation op;
    int count = 0;
    do
    {
      if (count++>0)
        TimeThread.sleep(100);
      op = connection.processSearch(
                                    ByteStringFactory.create("cn=monitor"),
                                    SearchScope.SINGLE_LEVEL,
                                    LDAPFilter.decode(monitorFilter));
    }
    while (op.getSearchEntries().isEmpty() && (count<100));
    if (op.getSearchEntries().isEmpty())
      throw new Exception("Could not read monitoring information");

    SearchResultEntry entry = op.getSearchEntries().getFirst();
    AttributeType attrType =
         DirectoryServer.getDefaultAttributeType(attr);
    return entry.getAttributeValue(attrType, IntegerSyntax.DECODER).longValue();
  }

  /**
   * Check that the entry with the given dn has the given valueString value
   * for the given attrTypeStr attribute type.
   */
  protected boolean checkEntryHasAttribute(DN dn, String attrTypeStr,
      String valueString, int timeout, boolean hasAttribute) throws Exception
  {
    boolean found = false;
    int count = timeout/100;
    if (count<1)
      count=1;

    do
    {
      Entry newEntry;
      Lock lock = null;
      for (int j=0; j < 3; j++)
      {
        lock = LockManager.lockRead(dn);
        if (lock != null)
        {
          break;
        }
      }

      if (lock == null)
      {
        throw new Exception("could not lock entry " + dn);
      }

      try
      {
        newEntry = DirectoryServer.getEntry(dn);


        if (newEntry != null)
        {
          List<Attribute> tmpAttrList = newEntry.getAttribute(attrTypeStr);
          if ((tmpAttrList != null) && (!tmpAttrList.isEmpty()))
          {
            Attribute tmpAttr = tmpAttrList.get(0);

            AttributeType attrType =
              DirectoryServer.getAttributeType(attrTypeStr, true);
            found = tmpAttr.hasValue(new AttributeValue(attrType, valueString));
          }
        }

      }
      finally
      {
        LockManager.unlock(dn, lock);
      }

      if (found != hasAttribute)
        Thread.sleep(100);
    } while ((--count > 0) && (found != hasAttribute));
    return found;
  }

  /**
   * Retrieves an entry from the local Directory Server.
   * @throws Exception When the entry cannot be locked.
   */
  protected Entry getEntry(DN dn, int timeout, boolean exist) throws Exception
  {
    int count = timeout/200;
    if (count<1)
      count=1;
    Thread.sleep(50);
    boolean found = DirectoryServer.entryExists(dn);
    while ((count> 0) && (found != exist))
    {
      Thread.sleep(200);

      found = DirectoryServer.entryExists(dn);
      count--;
    }

    Lock lock = null;
    for (int i=0; i < 3; i++)
    {
      lock = LockManager.lockRead(dn);
      if (lock != null)
      {
        break;
      }
    }

    if (lock == null)
    {
      throw new Exception("could not lock entry " + dn);
    }

    try
    {
      Entry entry = DirectoryServer.getEntry(dn);
      if (entry == null)
        return null;
      else
        return entry.duplicate(true);
    }
    finally
    {
      LockManager.unlock(dn, lock);
    }
  }

  /**
   * Update the monitor count for the specified monitor attribute.
   */
  protected void updateMonitorCount(DN baseDn, String attr) {
    monitorDn = baseDn;
    monitorAttr = attr;
    try
    {
      lastCount = getMonitorAttrValue(baseDn, attr);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      assertTrue(false);
    }
  }

  /**
   * Get the delta between the current / last monitor counts.
   * @return The delta between the current and last monitor count.
   */
  protected long getMonitorDelta() {
    long delta = 0;
    try {
      long currentCount = getMonitorAttrValue(monitorDn, monitorAttr);
      delta = (currentCount - lastCount);
      lastCount = currentCount;
    } catch (Exception ex) {
      ex.printStackTrace();
      assertTrue(false);
    }
    return delta;
  }
  /**
   * Generate a new modification replace with the given information.
   *
   * @param attrName The attribute to replace.
   * @param attrValue The new value for the attribute
   *
   * @return The modification replace.
   */
  protected List<Modification> generatemods(String attrName, String attrValue)
  {
    AttributeType attrType =
      DirectoryServer.getAttributeType(attrName.toLowerCase(), true);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(attrType, attrValue));
    Attribute attr = new Attribute(attrType, attrName, values);
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    return mods;
  }

  /**
   * Utility method to create, run a task and check its result.
   */
  protected void task(String task) throws Exception
  {
    Entry taskEntry = TestCaseUtils.makeEntry(task);

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add the task.
    AddOperation addOperation =
         connection.processAdd(taskEntry.getDN(),
                               taskEntry.getObjectClasses(),
                               taskEntry.getUserAttributes(),
                               taskEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
                 "Add of the task definition was not successful");

    // Wait until the task completes.
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
        continue;
      }
      completionTime =
           resultEntry.getAttributeValue(completionTimeType,
                                         DirectoryStringSyntax.DECODER);

      if (completionTime == null)
      {
        if (System.currentTimeMillis() - startMillisecs > 1000*30)
        {
          break;
        }
        Thread.sleep(10);
      }
    } while (completionTime == null);

    if (completionTime == null)
    {
      fail("The task has not completed after 30 seconds.");
    }

    // Check that the task state is as expected.
    AttributeType taskStateType =
         DirectoryServer.getAttributeType(ATTR_TASK_STATE.toLowerCase());
    String stateString =
         resultEntry.getAttributeValue(taskStateType,
                                       DirectoryStringSyntax.DECODER);
    TaskState taskState = TaskState.fromString(stateString);
    assertEquals(taskState, TaskState.COMPLETED_SUCCESSFULLY,
                 "The task completed in an unexpected state");
  }

  /**
   * Create a new replication session security object that can be used in
   * unit tests.
   *
   * @return A new replication session security object.
   * @throws ConfigException If an error occurs.
   */
  protected static ReplSessionSecurity getReplSessionSecurity()
       throws ConfigException
  {
    return new ReplSessionSecurity(null, null, null, true);
  }

  /**
   * Add a task to the configuration of the current running DS.
   * @param taskEntry The task to add.
   * @param expectedResult The expected result code for the ADD.
   * @param errorMessageID The expected error messageID when the expected
   * result code is not SUCCESS
   */
  protected void addTask(Entry taskEntry, ResultCode expectedResult,
      Message errorMessage)
  {
    try
    {
      TRACER.debugInfo("AddTask/" + taskEntry);

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
            startsWith(errorMessage.toString()),
            "Error MsgID of the task <"
            + addOperation.getErrorMessage()
            + "> equals <"
            + errorMessage + ">");
        TRACER.debugInfo("Create config task: <"+ errorMessage.getDescriptor().getId()
                + addOperation.getErrorMessage() + ">");

      }
      else
      {
        waitTaskState(taskEntry, TaskState.RUNNING, null);
      }

      // Entry will be removed at the end of the test
      entryList.addLast(taskEntry.getDN());

      TRACER.debugInfo("AddedTask/" + taskEntry.getDN());
    }
    catch(Exception e)
    {
      fail("Exception when adding task:"+ e.getMessage());
    }
  }

  protected void waitTaskState(Entry taskEntry, TaskState expectedTaskState,
      Message expectedMessage)
  {
    TaskState taskState = null;
    int cpt=10;
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
        Thread.sleep(500);
        cpt--;
      }
      while ((taskState != expectedTaskState) &&
             (taskState != TaskState.STOPPED_BY_ERROR) &&
             (taskState != TaskState.COMPLETED_SUCCESSFULLY) &&
             (cpt > 0));

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
      }
      if (logMessages.size() != 0)
      {
          TRACER.debugInfo(logMessages.get(0));
          if (expectedMessage != null)
          {
            TRACER.debugInfo(expectedMessage.toString());
            assertTrue(logMessages.get(0).indexOf(
                expectedMessage.toString())>0);
          }
      }

      if ((expectedTaskState == TaskState.RUNNING)
          && (taskState == TaskState.COMPLETED_SUCCESSFULLY))
      {
        // We usually wait the running state after adding the task
        // and if the task is fast enough then it may be already done
        // and we can go on.
      }
      else
      {
        assertEquals(taskState, expectedTaskState, "Task State:" + taskState +
          " Expected task state:" + expectedTaskState);
      }
    }
    catch(Exception e)
    {
      fail("waitTaskState Exception:"+ e.getMessage() + " " + stackTraceToSingleLineString(e));
    }
  }
  
  /**
   * Add to the current DB the entries necessary to the test
   */
  protected void addTestEntriesToDB(String[] ldifEntries)
  {
    try
    {
      // Change config of DS to launch the total update task
      InternalClientConnection connection =
        InternalClientConnection.getRootConnection();

      for (String ldifEntry : ldifEntries)
      {
        Entry entry = TestCaseUtils.entryFromLdifString(ldifEntry);
        AddOperationBasis addOp = new AddOperationBasis(
            connection,
            InternalClientConnection.nextOperationID(), 
            InternalClientConnection.nextMessageID(), 
            null, 
            entry.getDN(), 
            entry.getObjectClasses(),
            entry.getUserAttributes(), 
            entry.getOperationalAttributes());
        addOp.setInternalOperation(true);
        addOp.run();
        if (addOp.getResultCode() != ResultCode.SUCCESS)
        {
          TRACER.debugInfo("Failed to add entry " + entry.getDN() +
              "Result code = : " + addOp.getResultCode());
        }
        else
        {
          TRACER.debugInfo(entry.getDN() +
              " added " + addOp.getResultCode());          
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
}
