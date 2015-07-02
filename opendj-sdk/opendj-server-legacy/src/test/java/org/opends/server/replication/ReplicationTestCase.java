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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationServerCfgDefn.ReplicationDBImplementation;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
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
import org.opends.server.replication.server.changelog.je.JEChangelogDB;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * An abstract class that all Replication unit test should extend.
 */
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

  /**
   * Generation id for a fully empty domain.
   */
  public static final long EMPTY_DN_GENID = GenerationIdChecksum.EMPTY_BACKEND_GENERATION_ID;

  /**
   * The internal connection used for operation.
   */
  protected InternalClientConnection connection;

  /** Created entries that will be deleted on class cleanup. */
  protected final Set<DN> entriesToCleanup = new HashSet<DN>();
  /** Created config entries that will be deleted on class cleanup. */
  protected final Set<DN> configEntriesToCleanup = new HashSet<DN>();

  /** Replicated suffix (replication domain). */
  protected Entry synchroServerEntry;
  protected Entry replServerEntry;

  private static final String REPLICATION_DB_IMPL_PROPERTY = "org.opends.test.replicationDbImpl";

  public static ReplicationDBImplementation replicationDbImplementation = ReplicationDBImplementation.valueOf(
      System.getProperty(REPLICATION_DB_IMPL_PROPERTY, ReplicationDBImplementation.LOG.name()));

  /**
   * Replication monitor stats.
   */
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

  /**
   * The replication plugin entry.
   */
  protected static final String SYNCHRO_PLUGIN_DN =
    "cn=Multimaster Synchronization, cn=Synchronization Providers,cn=config";

  /**
   * Set up the environment for performing the tests in this suite.
   */
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
    }
    // This is the value of the generationId computed by the server when the
    // test suffix (o=test) has only the root entry created.
    return TEST_DN_WITH_ROOT_ENTRY_GENID;
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

  /**
   * Open a replicationServer session to the local ReplicationServer
   * providing the generationId.
   */
  protected ReplicationBroker openReplicationSession(final DN baseDN,
      int serverId, int windowSize, int port, int timeout,
      long generationId) throws Exception
  {
    DomainFakeCfg config = newFakeCfg(baseDN, serverId, port);
    config.setWindowSize(windowSize);
    return openReplicationSession(config, port, timeout, generationId);
  }

  protected ReplicationBroker openReplicationSession(ReplicationDomainCfg config,
      int port, int timeout, long generationId) throws Exception
  {
    final ReplicationBroker broker = new ReplicationBroker(
        new DummyReplicationDomain(generationId), new ServerState(),
        config, getReplSessionSecurity());
    connect(broker, port, timeout);
    return broker;
  }

  protected DomainFakeCfg newFakeCfg(final DN baseDN, int serverId, int port)
  {
    DomainFakeCfg fakeCfg = new DomainFakeCfg(baseDN, serverId, newSortedSet("localhost:" + port));
    fakeCfg.setHeartbeatInterval(100000);
    fakeCfg.setChangetimeHeartbeatInterval(500);
    return fakeCfg;
  }

  protected void connect(ReplicationBroker broker, int port, int timeout) throws Exception
  {
    broker.start();
    // give some time to the broker to connect to the replicationServer.
    checkConnection(30, broker, port);

    if (timeout != 0)
      broker.setSoTimeout(timeout);
  }

  /**
   * Check connection of the provided ds to the
   * replication server. Waits for connection to be ok up to secTimeout seconds
   * before failing.
   */
  protected void checkConnection(int secTimeout, ReplicationBroker rb, int rsPort) throws Exception
  {
    int nSec = 0;

    // Go out of the loop only if connection is verified or if timeout occurs
    while (true)
    {
      if (rb.isConnected())
      {
        logger.trace("checkConnection: connection of broker "
          + rb.getServerId() + " to RS " + rb.getRsGroupId()
          + " obtained after " + nSec + " seconds.");
        return;
      }

      Thread.sleep(1000);
      rb.start();
      nSec++;

      assertTrue(nSec <= secTimeout,
          "checkConnection: DS " + rb.getServerId() + " is not connected to "
              + "the RS port " + rsPort + " after " + secTimeout + " seconds.");
    }
  }

  protected void deleteEntry(DN dn) throws Exception
  {
    if (dn.parent().rdn().toString().equalsIgnoreCase("cn=domains"))
      deleteEntry(DN.valueOf("cn=external changelog," + dn));

    DeleteOperation op = connection.processDelete(dn);
    assertTrue(op.getResultCode() == SUCCESS || op.getResultCode() == NO_SUCH_OBJECT,
        "Delete entry " + dn + " failed: " + op.getResultCode());
  }

  /**
   * Suppress all the config entries created by the tests in this class.
   */
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

  /**
   * Suppress all the real entries created by the tests in this class.
   */
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
      paranoiaCheck();
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
    if (replicationDbImplementation == ReplicationDBImplementation.JE)
    {
      ((JEChangelogDB) rs.getChangelogDB()).clearDB();
    }
    else
    {
      ((FileChangelogDB) rs.getChangelogDB()).clearDB();
    }
  }

  /**
   * Cleanup databases of the currently instantiated replication servers in the
   * VM.
   */
  protected void cleanUpReplicationServersDB() throws Exception
  {
    for (ReplicationServer rs : ReplicationServer.getAllInstances())
    {
      clearChangelogDB(rs);
    }
  }

  /**
   * Remove trailing directories and databases of the currently instantiated
   * replication servers.
   */
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

  /**
   * Configure the replication for this test.
   */
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
      DirectoryServer.getConfigHandler().addEntry(configEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(configEntry.getName()), errorMessage);
      configEntriesToCleanup.add(configEntry.getName());
    }
  }

  /**
   * Get the value of the specified attribute for a given replication
   * domain from the monitor entry.
   * @return The monitor value
   * @throws Exception If an error occurs.
   */
  protected long getMonitorAttrValue(DN baseDN, String attr) throws Exception
  {
    String monitorFilter = "(&(cn=Directory server*)(domain-name=" + baseDN + "))";

    InternalSearchOperation op;
    int count = 0;
    do
    {
      if (count++>0)
        Thread.sleep(100);
      op = connection.processSearch(newSearchRequest("cn=replication,cn=monitor", WHOLE_SUBTREE, monitorFilter));
    }
    while (op.getSearchEntries().isEmpty() && (count<100));
    assertFalse(op.getSearchEntries().isEmpty(), "Could not read monitoring information");

    SearchResultEntry entry = op.getSearchEntries().getFirst();
    return entry.parseAttribute(attr).asLong();
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
      final Entry newEntry = DirectoryServer.getEntry(dn);
      if (newEntry != null)
      {
        List<Attribute> tmpAttrList = newEntry.getAttribute(attrTypeStr);
        if ((tmpAttrList != null) && (!tmpAttrList.isEmpty()))
        {
          Attribute tmpAttr = tmpAttrList.get(0);
          found = tmpAttr.contains(ByteString.valueOf(valueString));
        }
      }

      if (found != hasAttribute)
      {
        Thread.sleep(100);
      }
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

    Entry entry = DirectoryServer.getEntry(dn);
    if (entry != null)
    {
      return entry.duplicate(true);
    }
    return null;
  }

  /**
   * Update the monitor count for the specified monitor attribute.
   */
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
    long delta = (currentCount - lastCount);
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
    Entry taskEntry = TestCaseUtils.addEntry(task);

    // Wait until the task completes.
    Entry resultEntry = null;
    String completionTime = null;
    long startMillisecs = System.currentTimeMillis();
    do
    {
      final SearchRequest request = newSearchRequest(taskEntry.getName(), SearchScope.BASE_OBJECT);
      InternalSearchOperation searchOperation = connection.processSearch(request);
      if (searchOperation.getSearchEntries().isEmpty())
      {
        continue;
      }
      resultEntry = searchOperation.getSearchEntries().get(0);
      completionTime = resultEntry.parseAttribute(
          ATTR_TASK_COMPLETION_TIME.toLowerCase()).asString();
      if (completionTime == null)
      {
        if (System.currentTimeMillis() - startMillisecs > 1000*30)
        {
          break;
        }
        Thread.sleep(10);
      }
    } while (completionTime == null);

    assertNotNull(completionTime, "The task has not completed after 30 seconds.");

    // Check that the task state is as expected.
    String stateString = resultEntry.parseAttribute(
        ATTR_TASK_STATE.toLowerCase()).asString();
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

  protected void waitTaskState(Entry taskEntry, TaskState expectedTaskState,
      long maxWaitTimeInMillis, LocalizableMessage expectedMessage) throws Exception
  {
    long startTime = System.currentTimeMillis();

    Entry resultEntry = null;
    TaskState taskState = null;
    do
    {
      final SearchRequest request = newSearchRequest(taskEntry.getName(), SearchScope.BASE_OBJECT);
      InternalSearchOperation searchOperation = connection.processSearch(request);
      resultEntry = searchOperation.getSearchEntries().getFirst();

      // Check that the task state is as expected.
      String stateString = resultEntry.parseAttribute(
          ATTR_TASK_STATE.toLowerCase()).asString();
      taskState = TaskState.fromString(stateString);

      Thread.sleep(100);
    }
    while (taskState != expectedTaskState
        && taskState != TaskState.STOPPED_BY_ERROR
        && taskState != TaskState.COMPLETED_SUCCESSFULLY
        && (System.currentTimeMillis() - startTime < maxWaitTimeInMillis));

    // Check that the task contains some log messages.
    Set<String> logMessages = resultEntry.parseAttribute(
        ATTR_TASK_LOG_MESSAGES.toLowerCase()).asSetOfString();

    if (taskState != TaskState.COMPLETED_SUCCESSFULLY
        && taskState != TaskState.RUNNING)
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

    if (expectedTaskState == TaskState.RUNNING
        && taskState == TaskState.COMPLETED_SUCCESSFULLY)
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

  /**
   * Add to the current DB the entries necessary to the test.
   */
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
  protected String getEntryUUID(DN dn) throws Exception
  {
    int count = 10;
    String found = null;
    while (count > 0 && found == null)
    {
      Thread.sleep(100);

      Entry newEntry = DirectoryServer.getEntry(dn);
      if (newEntry != null)
      {
        Attribute attribute = newEntry.getAttribute("entryuuid").get(0);
        for (ByteString val : attribute)
        {
          found = val.toString();
          break;
        }
      }
      count --;
    }
    assertNotNull(found, "Entry: " + dn + " Could not be found.");
    return found;
  }

  /**
   * Utility method : removes a domain deleting the passed config entry
   */
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
  protected static <T extends ReplicationMsg> T waitForSpecificMsg(Session session, Class<T> msgType) {
    return waitForSpecificMsg(session, null, msgType);
  }

  /**
   * Wait for the arrival of a specific message type on the provided broker
   * before going in timeout and failing.
   * @param broker Broker from which we should receive the message.
   * @param msgType Class of the message we are waiting for.
   * @return The expected message if it comes in time or fails (assertion).
   */
  protected static <T extends ReplicationMsg> T waitForSpecificMsg(ReplicationBroker broker, Class<T> msgType) {
    return waitForSpecificMsg(null, broker, msgType);
  }

  protected static <T extends ReplicationMsg> T waitForSpecificMsg(Session session, ReplicationBroker broker, Class<T> msgType)
  {
    assertTrue(session != null || broker != null, "One of Session or ReplicationBroker parameter must not be null");
    assertTrue(session == null || broker == null, "Only one of Session or ReplicationBroker parameter must not be null");

    final int timeOut = 5000; // 5 seconds max to wait for the desired message
    final long startTime = System.currentTimeMillis();
    final List<ReplicationMsg> msgs = new ArrayList<ReplicationMsg>();
    boolean timedOut = false;
    while (!timedOut)
    {
      ReplicationMsg replMsg = null;
      try
      {
        if (session != null)
        {
          replMsg = session.receive();
        }
        else if (broker != null)
        {
          replMsg = broker.receive();
        }
      }
      catch (Exception ex)
      {
        fail("Exception waiting for " + msgType + " message : "
            + ex.getClass().getName() + " : " + ex.getMessage());
      }

      if (replMsg.getClass().equals(msgType))
      {
        // Ok, got it, let's return the expected message
        return (T) replMsg;
      }
      logger.trace("waitForSpecificMsg received : " + replMsg);
      msgs.add(replMsg);
      timedOut = (System.currentTimeMillis() - startTime) > timeOut;
    }
    // Timeout
    fail("Failed to receive an expected " + msgType + " message after 5 seconds."
        + " Also received the following messages during wait time: " + msgs);
    return null;
  }

  /**
   * Performs an internal search, waiting for at most 3 seconds for expected result code and expected
   * number of entries.
   */
  protected InternalSearchOperation waitForSearchResult(String dn, SearchScope scope, String filter,
      ResultCode expectedResultCode, int expectedNbEntries) throws Exception
  {
    InternalSearchOperation searchOp = null;
    int count = 0;
    do
    {
      Thread.sleep(10);
      final SearchRequest request = newSearchRequest(dn, scope, filter).addAttribute("*", "+");
      searchOp = connection.processSearch(request);
      count++;
    }
    while (count < 300
        && searchOp.getResultCode() != expectedResultCode
        && searchOp.getSearchEntries().size() != expectedNbEntries);

    final List<SearchResultEntry> entries = searchOp.getSearchEntries();
    Assertions.assertThat(entries).hasSize(expectedNbEntries);
    return searchOp;
  }

  protected static void setReplicationDBImplementation(ReplicationDBImplementation impl)
  {
    replicationDbImplementation = impl;
  }
}
