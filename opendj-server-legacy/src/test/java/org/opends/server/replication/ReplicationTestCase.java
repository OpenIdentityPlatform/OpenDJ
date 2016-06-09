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
 */
package org.opends.server.replication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.DummyReplicationDomain;
import org.opends.server.replication.plugin.GenerationIdChecksum;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.file.FileChangelogDB;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.TestTimer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.backends.task.TaskState.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/** An abstract class that all Replication unit test should extend. */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "replication" }, sequential = true)
public abstract class ReplicationTestCase extends DirectoryServerTestCase
{

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * This is the generation id matching the memory test backend with its initial
   * root entry o=test created. This matches the backend obtained calling:
   * TestCaseUtils.initializeTestBackend(true). (using the default
   * TestCaseUtils.TEST_ROOT_DN_STRING suffix)
   */
  protected static final long TEST_DN_WITH_ROOT_ENTRY_GENID = 5055L;

  /** Generation id for a fully empty domain. */
  public static final long EMPTY_DN_GENID = GenerationIdChecksum.EMPTY_BACKEND_GENERATION_ID;

  /** The internal connection used for operation. */
  protected InternalClientConnection connection;

  /** Created entries that will be deleted on class cleanup. */
  protected final Set<DN> entriesToCleanup = new HashSet<>();
  /** Created config entries that will be deleted on class cleanup. */
  protected final Set<DN> configEntriesToCleanup = new HashSet<>();

  /** Replicated suffix (replication domain). */
  protected Entry synchroServerEntry;
  protected Entry replServerEntry;

  /** Replication monitor stats. */
  private DN monitorDN;
  private String monitorAttr;
  private long lastCount;

  /**
   * Call the paranoiaCheck at test cleanup or not.
   * <p>
   * Must not been touched except if sub class has its own clean up code, for
   * instance:
   *
   * <pre>
   * &#064;AfterClass
   * public void classCleanUp() throws Exception
   * {
   *   callParanoiaCheck = false;
   *   super.classCleanUp();
   *
   *   // Clear my own stuff that I have setup (in my own setup() method for instance)
   *   // This removes the replication changes backend
   *   myReplServerInstantiatedWithConstructor.remove();
   *
   *   // Now call paramoiaCheck myself
   *   paranoiaCheck();
   * }
   *
   * </pre>
   */
  protected boolean callParanoiaCheck = true;

  /** The replication plugin entry. */
  protected static final String SYNCHRO_PLUGIN_DN =
    "cn=Multimaster Synchronization, cn=Synchronization Providers,cn=config";

  /** Set up the environment for performing the tests in this suite. */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    // Initialize the test backend (TestCaseUtils.TEST_ROOT_DN_STRING)
    // (in case previous (non replication?) tests were run before...)
    TestCaseUtils.initializeTestBackend(true);

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    callParanoiaCheck = true;
  }

  /**
   * Retrieves the domain associated to the baseDN, and the value of the generationId
   * of this domain. If the domain does not exist, returns the default hard-coded\
   * value of the generationId corresponding to test backend with its default
   * initial o=test root root entry.
   *
   * @param baseDN The baseDN for which we want the generationId
   * @return The value of the generationId.
   */
  protected long getGenerationId(DN baseDN)
  {
    try
    {
      LDAPReplicationDomain replDomain = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
      return replDomain.getGenerationID();
    }
    catch(Exception e) {
      logger.traceException(e);

      // This is the value of the generationId computed by the server when the
      // test suffix (o=test) has only the root entry created.
      return TEST_DN_WITH_ROOT_ENTRY_GENID;
    }
  }

  /**
   * Open a replicationServer session to the local ReplicationServer.
   * The generation is read from the replicationDomain object. If it
   * does not exist, take the 'empty backend' generationID.
   */
  protected ReplicationBroker openReplicationSession(final DN baseDN,
      int serverId, int windowSize, int port, int timeout) throws Exception
  {
    return openReplicationSession(baseDN, serverId, windowSize,
        port, timeout, getGenerationId(baseDN));
  }

  /** Open a replicationServer session to the local ReplicationServer providing the generationId. */
  protected ReplicationBroker openReplicationSession(final DN baseDN,
      int serverId, int windowSize, int port, int timeout,
      long generationId) throws Exception
  {
    final DomainFakeCfg config = newFakeCfg(baseDN, serverId, port);
    config.setWindowSize(windowSize);

    final ReplicationBroker broker = new ReplicationBroker(
        new DummyReplicationDomain(generationId), new ServerState(),
        config, getReplSessionSecurity());
    connect(broker, timeout);
    return broker;
  }

  protected DomainFakeCfg newFakeCfg(final DN baseDN, int serverId, int port)
  {
    DomainFakeCfg fakeCfg = new DomainFakeCfg(baseDN, serverId, newTreeSet("localhost:" + port));
    fakeCfg.setHeartbeatInterval(100000);
    fakeCfg.setChangetimeHeartbeatInterval(500);
    return fakeCfg;
  }

  protected void connect(ReplicationBroker broker, int timeout) throws Exception
  {
    broker.start();
    // give some time to the broker to connect to the replicationServer.
    checkConnection(30, broker);

    if (timeout != 0)
    {
      broker.setSoTimeout(timeout);
    }
  }

  /**
   * Check connection of the provided ds to the replication server. Waits for connection to be ok up
   * to secTimeout seconds before failing.
   */
  protected void checkConnection(int secTimeout, final ReplicationBroker rb) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(secTimeout, SECONDS)
      .sleepTimes(1, SECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        if (rb.isConnected())
        {
          logger.trace("checkConnection: connection of broker " + rb.getServerId()
              + " to RS " + rb.getRsGroupId() + " obtained.");
          return null;
        }

        rb.start();
        return null;
      }
    });
  }

  protected void deleteEntry(DN dn) throws Exception
  {
    if ("cn=domains".equalsIgnoreCase(dn.parent().rdn().toString()))
    {
      deleteEntry(DN.valueOf("cn=external changelog," + dn));
    }

    DeleteOperation op = connection.processDelete(dn);
    assertTrue(op.getResultCode() == SUCCESS || op.getResultCode() == NO_SUCH_OBJECT,
        "Delete entry " + dn + " failed: " + op.getResultCode());
  }

  /** Suppress all the config entries created by the tests in this class. */
  protected void cleanConfigEntries() throws Exception
  {
    logger.error(LocalizableMessage.raw("ReplicationTestCase/Cleaning config entries"));

    for (DN dn : configEntriesToCleanup)
    {
      deleteEntry(dn);
    }
    configEntriesToCleanup.clear();

    synchroServerEntry = null;
    replServerEntry = null;
  }

  /** Suppress all the real entries created by the tests in this class. */
  protected void cleanRealEntries() throws Exception
  {
    logger.error(LocalizableMessage.raw("ReplicationTestCase/Cleaning entries"));

    for (DN dn : entriesToCleanup)
    {
      deleteEntry(dn);
    }
    entriesToCleanup.clear();
  }

  /**
   * Clean up the environment. return null;
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
  public void classCleanUp() throws Exception
  {
    logger.error(LocalizableMessage.raw(" ##### Calling ReplicationTestCase.classCleanUp ##### "));

    removeReplicationServerDB();

    cleanConfigEntries();
    cleanRealEntries();

    // Clear the test backend (TestCaseUtils.TEST_ROOT_DN_STRING)
    // (in case our test created some entries in it)
    TestCaseUtils.initializeTestBackend(true);

    // Check for unexpected replication config/objects left
    if (callParanoiaCheck)
    {
      paranoiaCheck();
    }
  }

  /**
   * After having run, each replication test should not leave any of the following:
   * - config entry for replication server
   * - config entry for a replication domain
   * - replication domain object
   * - config entry for a replication changes backend
   * - replication changes backend object
   * This method checks for existence of anything of that type.
   */
  protected void paranoiaCheck() throws Exception
  {
    logger.error(LocalizableMessage.raw("Performing paranoia check"));

    // Check for config entries for replication server
    assertNoConfigEntriesWithFilter("(objectclass=ds-cfg-replication-server)",
      "Found unexpected replication server config left");

    // Be sure that no replication server instance is left
    Assertions.assertThat(ReplicationServer.getAllInstances()).isEmpty();

    // Check for config entries for replication domain
    assertNoConfigEntriesWithFilter("(objectclass=ds-cfg-replication-domain)",
      "Found unexpected replication domain config left");

    // Check for left domain object
    assertEquals(MultimasterReplication.getNumberOfDomains(), 0, "Some replication domain objects left");
  }

  protected void clearChangelogDB(ReplicationServer rs) throws Exception
  {
    if (rs != null)
    {
      ((FileChangelogDB) rs.getChangelogDB()).clearDB();
    }
  }

  /** Cleanup databases of the currently instantiated replication servers in the VM. */
  protected void cleanUpReplicationServersDB() throws Exception
  {
    for (ReplicationServer rs : ReplicationServer.getAllInstances())
    {
      clearChangelogDB(rs);
    }
  }

  /** Remove trailing directories and databases of the currently instantiated replication servers. */
  protected void removeReplicationServerDB() throws Exception
  {
    // avoid ConcurrentModificationException
    remove(new ArrayList<ReplicationServer>(ReplicationServer.getAllInstances()));
  }

  protected void remove(ReplicationServer... replicationServers) throws Exception
  {
    remove(Arrays.asList(replicationServers));
  }

  protected void remove(Collection<ReplicationServer> replicationServers)
      throws Exception
  {
    for (ReplicationServer rs : replicationServers)
    {
      if (rs != null)
      {
        rs.remove();
        rs.getChangelogDB().removeDB();
      }
    }
  }

  protected void stop(ReplicationBroker... brokers)
  {
    if (brokers == null)
    {
      return;
    }
    for (ReplicationBroker broker : brokers)
    {
      if (broker != null)
      {
        broker.stop();
      }
    }
  }

  /**
   * Performs a search on the config backend with the specified filter.
   * Fails if a config entry is found.
   * @param filter The filter to apply for the search
   * @param errorMsg The error message to display if a config entry is found
   */
  private void assertNoConfigEntriesWithFilter(String filter, String errorMsg)
      throws Exception
  {
    // Search for matching entries in config backend
    InternalSearchOperation op = connection.processSearch(newSearchRequest("cn=config", WHOLE_SUBTREE, filter));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS, op.getErrorMessage() .toString());

    // Check that no entries have been found
    List<SearchResultEntry> entries = op.getSearchEntries();
    assertNotNull(entries);
    StringBuilder sb = new StringBuilder();
    for (SearchResultEntry entry : entries)
    {
      sb.append(entry.toLDIFString());
      sb.append(' ');
    }
    assertEquals(entries.size(), 0, errorMsg + ":\n" + sb);
  }

  /** Configure the replication for this test. */
  protected void configureReplication(String replServerEntryLdif,
      String synchroServerEntryLdif) throws Exception
  {
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerEntryLdif);
    addConfigEntry(replServerEntry, "Unable to add the replication server");
    addSynchroServerEntry(synchroServerEntryLdif);
  }

  protected void addSynchroServerEntry(String synchroServerEntryLdif)
      throws Exception
  {
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerEntryLdif);
    addConfigEntry(synchroServerEntry, "Unable to add the synchronized server");
  }

  private void addConfigEntry(Entry configEntry, String errorMessage) throws Exception
  {
    if (configEntry != null)
    {
      DirectoryServer.getConfigurationHandler().addEntry(Converters.from(configEntry));
      assertNotNull(DirectoryServer.getEntry(configEntry.getName()), errorMessage);
      configEntriesToCleanup.add(configEntry.getName());
    }
  }

  /**
   * Get the value of the specified attribute for a given replication
   * domain from the monitor entry.
   * @return The monitor value
   * @throws Exception If an error occurs.
   */
  protected long getMonitorAttrValue(final DN baseDN, final String attr) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(10, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    return timer.repeatUntilSuccess(new Callable<Long>()
    {
      @Override
      public Long call() throws Exception
      {
        String monitorFilter = "(&(cn=Directory server*)(domain-name=" + baseDN + "))";
        InternalSearchOperation op =
            connection.processSearch(newSearchRequest("cn=replication,cn=monitor", WHOLE_SUBTREE, monitorFilter));
        Assertions.assertThat(op.getSearchEntries()).as("Could not read monitoring information").isNotEmpty();

        SearchResultEntry entry = op.getSearchEntries().getFirst();
        return entry.parseAttribute(attr).asLong();
      }
    });
  }

  protected void checkEntryHasAttributeValue(final DN dn, final String attrTypeStr, final String valueString,
      int timeoutInSecs, String notFoundErrorMsg) throws Exception
  {
    checkEntryHasAttribute(dn, attrTypeStr, valueString, timeoutInSecs, true, notFoundErrorMsg);
  }

  protected void checkEntryHasNoSuchAttributeValue(final DN dn, final String attrTypeStr, final String valueString,
      int timeoutInSecs, String foundErrorMsg) throws Exception
  {
    checkEntryHasAttribute(dn, attrTypeStr, valueString, timeoutInSecs, false, foundErrorMsg);
  }

  protected boolean checkEntryHasAttribute(final DN dn, final String attrTypeStr, final String valueString,
      int timeout, final boolean expectedAttributeValueFound) throws Exception
  {
    checkEntryHasAttribute(dn, attrTypeStr, valueString, timeout / 1000, expectedAttributeValueFound, null);
    return expectedAttributeValueFound;
  }

  private void checkEntryHasAttribute(final DN dn, final String attrTypeStr, final String valueString,
      int timeoutInSecs, final boolean expectedAttributeValueFound, final String foundMsg) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(timeoutInSecs, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        final Entry newEntry = DirectoryServer.getEntry(dn);
        assertNotNull(newEntry);
        List<Attribute> attrList = newEntry.getAttribute(attrTypeStr);
        Assertions.assertThat(attrList).isNotEmpty();
        Attribute attr = attrList.get(0);
        boolean foundAttributeValue = attr.contains(ByteString.valueOfUtf8(valueString));
        assertEquals(foundAttributeValue, expectedAttributeValueFound, foundMsg);
        return null;
      }
    });
  }

  /**
   * Retrieves an entry from the local Directory Server.
   * @throws Exception When the entry cannot be locked.
   */
  protected Entry getEntry(final DN dn, int timeoutInMillis, final boolean exist) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(timeoutInMillis, MILLISECONDS)
      .sleepTimes(200, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertEquals(DirectoryServer.entryExists(dn), exist);
        return null;
      }
    });

    Entry entry = DirectoryServer.getEntry(dn);
    if (entry != null)
    {
      return entry.duplicate(true);
    }
    return null;
  }

  /** Update the monitor count for the specified monitor attribute. */
  protected void updateMonitorCount(DN baseDN, String attr) throws Exception
  {
    monitorDN = baseDN;
    monitorAttr = attr;
    lastCount = getMonitorAttrValue(baseDN, attr);
  }

  /**
   * Get the delta between the current / last monitor counts.
   * @return The delta between the current and last monitor count.
   */
  protected long getMonitorDelta() throws Exception
  {
    long currentCount = getMonitorAttrValue(monitorDN, monitorAttr);
    long delta = currentCount - lastCount;
    lastCount = currentCount;
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
    Attribute attr = Attributes.create(attrName, attrValue);
    return newArrayList(new Modification(REPLACE, attr));
  }

  protected static ModifyRequest modifyRequest(DN entryDN, ModificationType modType, String attrName, String attrValue)
  {
    return Requests.newModifyRequest(entryDN).addModification(modType, attrName, attrValue);
  }

  /** Utility method to create, run a task and check its result. */
  protected void task(String task) throws Exception
  {
    final Entry taskEntry = TestCaseUtils.addEntry(task);

    // Wait until the task completes.
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(30, SECONDS)
      .sleepTimes(20, MILLISECONDS)
      .toTimer();
    Entry resultEntry = timer.repeatUntilSuccess(new Callable<Entry>()
    {
      @Override
      public Entry call() throws Exception
      {
        final SearchRequest request = newSearchRequest(taskEntry.getName(), SearchScope.BASE_OBJECT);
        InternalSearchOperation searchOperation = connection.processSearch(request);
        Assertions.assertThat(searchOperation.getSearchEntries()).isNotEmpty();
        Entry resultEntry = searchOperation.getSearchEntries().get(0);
        String completionTime = resultEntry.parseAttribute(ATTR_TASK_COMPLETION_TIME).asString();
        assertNotNull(completionTime, "The task has not completed");
        return resultEntry;
      }
    });

    // Check that the task state is as expected.
    assertEquals(getTaskState(resultEntry), TaskState.COMPLETED_SUCCESSFULLY,
                 "The task completed in an unexpected state");
  }

  /**
   * Create a new replication session security object that can be used in unit tests.
   *
   * @return A new replication session security object.
   * @throws ConfigException If an error occurs.
   */
  protected static ReplSessionSecurity getReplSessionSecurity() throws ConfigException
  {
    return new ReplSessionSecurity(null, null, null, true);
  }

  protected void executeTask(Entry taskEntry, long maxWaitTimeInMillis) throws Exception
  {
    addTask(taskEntry, ResultCode.SUCCESS, null);
    waitTaskState(taskEntry, TaskState.COMPLETED_SUCCESSFULLY, maxWaitTimeInMillis, null);
  }

  /**
   * Add a task to the configuration of the current running DS.
   * @param taskEntry The task to add.
   * @param expectedResult The expected result code for the ADD.
   * @param errorMessage The expected error message when the expected
   * result code is not SUCCESS
   */
  protected void addTask(Entry taskEntry, ResultCode expectedResult,
      LocalizableMessage errorMessage) throws Exception
  {
    logger.trace("AddTask/" + taskEntry);

    // Change config of DS to launch the total update task
    AddOperation addOperation = connection.processAdd(taskEntry);
    assertEquals(addOperation.getResultCode(), expectedResult,
        "Result of ADD operation of the task is: "
            + addOperation.getResultCode() + " Expected:" + expectedResult
            + " Details:" + addOperation.getErrorMessage()
            + addOperation.getAdditionalLogItems());

    if (expectedResult != ResultCode.SUCCESS)
    {
      Assertions.assertThat(addOperation.getErrorMessage().toString())
          .startsWith(errorMessage.toString());
      logger.trace("Create config task: <"
          + errorMessage.resourceName() + "-" + errorMessage.ordinal()
          + addOperation.getErrorMessage() + ">");
    }
    else
    {
      waitTaskState(taskEntry, TaskState.RUNNING, 20000, null);
    }

    // Entry will be removed at the end of the test
    entriesToCleanup.add(taskEntry.getName());

    logger.trace("AddedTask/" + taskEntry.getName());
  }

  protected void waitTaskState(final Entry taskEntry, final TaskState expectedTaskState,
      long maxWaitTimeInMillis, LocalizableMessage expectedMessage) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(maxWaitTimeInMillis, MILLISECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    Entry resultEntry = timer.repeatUntilSuccess(new Callable<Entry>()
    {
      @Override
      public Entry call() throws Exception
      {
        final SearchRequest request = newSearchRequest(taskEntry.getName(), SearchScope.BASE_OBJECT);
        InternalSearchOperation searchOperation = connection.processSearch(request);
        Entry resultEntry = searchOperation.getSearchEntries().getFirst();

        TaskState taskState = getTaskState(resultEntry);
        Assertions.assertThat(taskState).isIn(expectedTaskState, STOPPED_BY_ERROR, COMPLETED_SUCCESSFULLY);
        return resultEntry;
      }
    });

    // Check that the task contains some log messages.
    Set<String> logMessages = resultEntry.parseAttribute(ATTR_TASK_LOG_MESSAGES).asSetOfString();

    TaskState taskState = getTaskState(resultEntry);
    if (taskState != COMPLETED_SUCCESSFULLY && taskState != RUNNING)
    {
      assertFalse(logMessages.isEmpty(),
          "No log messages were written to the task entry on a failed task");
    }
    if (!logMessages.isEmpty())
    {
      String firstLogMsg = logMessages.iterator().next();
      logger.trace(firstLogMsg);
      if (expectedMessage != null)
      {
        logger.trace(expectedMessage);
        assertTrue(firstLogMsg.indexOf(expectedMessage.toString()) > 0);
      }
    }

    if (expectedTaskState == RUNNING && taskState == COMPLETED_SUCCESSFULLY)
    {
      // We usually wait the running state after adding the task
      // and if the task is fast enough then it may be already done
      // and we can go on.
    }
    else
    {
      assertEquals(taskState, expectedTaskState, "Task State:" + taskState
          + " Expected task state:" + expectedTaskState);
    }
  }

  private TaskState getTaskState(Entry entry)
  {
    return TaskState.fromString(entry.parseAttribute(ATTR_TASK_STATE).asString());
  }

  /** Add to the current DB the entries necessary to the test. */
  protected void addTestEntriesToDB(String... ldifEntries) throws Exception
  {
    for (String ldifEntry : ldifEntries)
    {
      Entry entry = TestCaseUtils.entryFromLdifString(ldifEntry);
      AddOperation addOp = connection.processAdd(entry);
      if (addOp.getResultCode() != ResultCode.SUCCESS)
      {
        logger.trace("Failed to add entry " + entry.getName()
            + "Result code = : " + addOp.getResultCode());
      }
      else
      {
        logger.trace(entry.getName() + " added " + addOp.getResultCode());
      }
    }
  }

  /**
   *  Get the entryUUID for a given DN.
   *
   * @throws Exception if the entry does not exist or does not have
   *                   an entryUUID.
   */
  protected String getEntryUUID(final DN dn) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(1, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    return timer.repeatUntilSuccess(new Callable<String>()
    {
      @Override
      public String call() throws Exception
      {
        Entry newEntry = DirectoryServer.getEntry(dn);
        assertNotNull(newEntry);
        Attribute attribute = newEntry.getAttribute("entryuuid").get(0);
        String found = attribute.iterator().next().toString();
        assertNotNull(found, "Entry: " + dn + " Could not be found.");
        return found;
      }
    });
  }

  /** Utility method : removes a domain deleting the passed config entry */
  protected void removeDomain(Entry... domainCfgEntries) throws Exception
  {
    for (Entry entry : domainCfgEntries)
    {
      if (entry != null)
      {
        deleteEntry(entry.getName());
      }
    }
  }

  /**
   * Wait for the arrival of a specific message type on the provided session
   * before going in timeout and failing.
   * @param session Session from which we should receive the message.
   * @param msgType Class of the message we are waiting for.
   * @return The expected message if it comes in time or fails (assertion).
   */
  protected static <T extends ReplicationMsg> T waitForSpecificMsg(Session session, Class<T> msgType) throws Exception
  {
    return (T) waitForSpecificMsgs(session, (ReplicationBroker) null, msgType);
  }

  /**
   * Wait for the arrival of a specific message type on the provided broker
   * before going in timeout and failing.
   * @param broker Broker from which we should receive the message.
   * @param msgType Class of the message we are waiting for.
   * @return The expected message if it comes in time or fails (assertion).
   */
  protected static <T extends ReplicationMsg> T waitForSpecificMsg(ReplicationBroker broker, Class<T> msgType)
      throws Exception
  {
    return (T) waitForSpecificMsgs(null, broker, msgType);
  }

  protected static ReplicationMsg waitForSpecificMsgs(Session session, Class<?>... msgTypes) throws Exception
  {
    return waitForSpecificMsgs(session, null, msgTypes);
  }

  protected static ReplicationMsg waitForSpecificMsgs(ReplicationBroker broker, Class<?>... msgTypes) throws Exception
  {
    return waitForSpecificMsgs(null, broker, msgTypes);
  }

  private static ReplicationMsg waitForSpecificMsgs(Session session, ReplicationBroker broker, Class<?>... msgTypes)
      throws Exception
  {
    assertTrue(session != null || broker != null,
        "One of Session or ReplicationBroker parameter must not be null");
    assertTrue(session == null || broker == null,
        "Only one of Session or ReplicationBroker parameter must not be null");

    List<Class<?>> msgTypes2 = Arrays.asList(msgTypes);

    final int timeOut = 5000; // 5 seconds max to wait for the desired message
    final long startTime = System.currentTimeMillis();
    final List<ReplicationMsg> msgs = new ArrayList<>();
    boolean timedOut = false;
    while (!timedOut)
    {
      ReplicationMsg replMsg = null;
      if (session != null)
      {
        replMsg = session.receive();
      }
      else if (broker != null)
      {
        replMsg = broker.receive();
      }

      if (msgTypes2.contains(replMsg.getClass()))
      {
        // Ok, got it, let's return the expected message
        return replMsg;
      }
      logger.trace("waitForSpecificMsg received : " + replMsg);
      msgs.add(replMsg);
      timedOut = System.currentTimeMillis() - startTime > timeOut;
    }
    // Timeout
    fail("Failed to receive an expected " + msgTypes2 + " message after 5 seconds."
        + " Also received the following messages during wait time: " + msgs);
    return null;
  }

  /**
   * Performs an internal search, waiting for at most 3 seconds for expected result code and expected
   * number of entries.
   */
  protected InternalSearchOperation waitForSearchResult(final String dn, final SearchScope scope, final String filter,
      final ResultCode expectedResultCode, final int expectedNbEntries) throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(3, SECONDS)
      .sleepTimes(10, MILLISECONDS)
      .toTimer();
    return timer.repeatUntilSuccess(new Callable<InternalSearchOperation>()
    {
      @Override
      public InternalSearchOperation call() throws Exception
      {
        final SearchRequest request = newSearchRequest(dn, scope, filter).addAttribute("*", "+");
        InternalSearchOperation searchOp = connection.processSearch(request);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(searchOp.getResultCode()).isEqualTo(expectedResultCode);
        softly.assertThat(searchOp.getSearchEntries()).hasSize(expectedNbEntries);
        softly.assertAll();
        return searchOp;
      }
    });
  }

  protected void waitConnected(int dsId, int rsId, int rsPort, LDAPReplicationDomain rd, String msg)
      throws InterruptedException
  {
    final int secTimeout = 30;
    int nSec = 0;

    // Go out of the loop only if connection is verified or if timeout occurs
    while (true)
    {
      boolean connected = rd.isConnected();
      int rdPort = -1;
      boolean rightPort = false;
      if (connected)
      {
        String rsUrl = rd.getReplicationServer();
        try {
          rdPort = HostPort.valueOf(rsUrl).getPort();
          rightPort = rdPort == rsPort;
        }
        catch (IllegalArgumentException notConnectedYet)
        {
          // wait a bit more
        }
      }
      if (connected && rightPort)
      {
        // Connection verified
        String s = "checkConnection: connection from domain " + dsId
            + " to replication server " + rsId + " obtained after " + nSec + " seconds.";
        logger.error(LocalizableMessage.raw(s));
        if (logger.isTraceEnabled())
        {
          logger.trace("*** TEST *** " + s);
        }
        return;
      }

      Thread.sleep(1000);
      nSec++;

      if (nSec > secTimeout)
      {
        // Timeout reached, end with error
        fail("checkConnection: could not verify connection from domain " + dsId
            + " to replication server " + rsId + " after " + secTimeout + " seconds."
            + " Domain connected: " + connected + ", connection port: " + rdPort
            + " (should be: " + rsPort + "). [" + msg + "]");
      }
    }
  }
}
