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
 * Portions Copyrighted 2026 3A Systems, LLC.
 */
package org.opends.server.replication;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.backends.task.TaskState.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import org.opends.server.core.ModifyOperation;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
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
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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

  /** The group a replication server and a replication domain are in unless told otherwise. */
  protected static final int DEFAULT_GROUP_ID = 1;

  /**
   * The group of a broker which is in none. Assured replication does not cross group ids,
   * so such a broker is never waited for and never waits: it is all a broker which only
   * publishes and reads updates needs.
   */
  private static final int NO_GROUP_ID = -1;

  /** How many times {@link #assertMonitorAttrValueStays} reads a value by default. */
  private static final int MONITOR_ATTR_SAMPLES = 5;

  /** How long {@link #assertMonitorAttrValueStays} waits between two reads. */
  private static final long MONITOR_ATTR_SAMPLE_INTERVAL_IN_MS = 200;

  /**
   * How long {@link #assertMonitorAttrValueStays} waits for the monitor entry of a domain
   * to be registered again before it gives up on reading it: longer than the
   * {@code MAX_REPLAY_RETRY_DELAY_IN_MS} a session restart holds it down for.
   */
  private static final long MONITOR_ATTR_SAMPLE_GRACE_IN_MS = 30000;

  /**
   * How much longer than the samples it asks for {@link #assertMonitorAttrValueStays}
   * runs before it gives up: the samples of a domain which keeps restarting its session
   * are taken a restart apart, and waiting for all of them would outlast the fork.
   */
  private static final long MONITOR_ATTR_SAMPLES_DEADLINE_IN_MS = 60000;

  /**
   * How many samples it takes for {@link #assertMonitorAttrValueStays} to outlast the
   * session restart which brings a change back, so that a counter only that delivery
   * could bump a second time is watched while it arrives.
   * <p>
   * It covers a domain which restarts its session for the first time, which waits
   * {@code LDAPReplicationDomain.REPLAY_RETRY_DELAY_IN_MS} before reconnecting. The wait
   * of a domain which has been restarting its session in a row is longer - it climbs to
   * {@code MAX_REPLAY_RETRY_DELAY_IN_MS} - so a redelivery is outside this window there;
   * sampling for ten seconds at every call site to cover it would cost more than the
   * assertions are worth.
   */
  protected static final int MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY = 12;

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
    return openReplicationSession(
        newFakeCfg(baseDN, serverId, port), windowSize, timeout, generationId);
  }

  /**
   * Open a session to the local ReplicationServer which takes part in assured replication.
   * <p>
   * Assured replication does not cross group ids, so a broker whose updates are to be
   * acknowledged by the replicas of this server has to be in the group of the replication
   * server: an update published by a broker of another group is acknowledged on the spot,
   * by the replication server itself, and says nothing about what any replica did with it.
   * <p>
   * The group cuts both ways, and this broker does not acknowledge anything: the
   * replication server expects an ack from every replica of its group whatever that
   * replica is configured for, so a SAFE_READ update published by anyone else while this
   * broker is connected waits out the {@code assured-timeout} of the server. Publish the
   * assured updates from this broker, and open only one of them.
   *
   * @param baseDN the suffix the session is opened for
   * @param serverId the id this broker takes
   * @param windowSize the window size of the session
   * @param port the port of the local replication server
   * @param timeout the read timeout of the session, or 0 for none
   * @return the connected broker
   * @throws Exception if the session could not be opened
   */
  protected ReplicationBroker openAssuredReplicationSession(final DN baseDN,
      int serverId, int windowSize, int port, int timeout) throws Exception
  {
    return openReplicationSession(newFakeCfg(baseDN, serverId, port, DEFAULT_GROUP_ID),
        windowSize, timeout, getGenerationId(baseDN));
  }

  private ReplicationBroker openReplicationSession(final DomainFakeCfg config,
      int windowSize, int timeout, long generationId) throws Exception
  {
    config.setWindowSize(windowSize);

    final ReplicationBroker broker = new ReplicationBroker(
        new DummyReplicationDomain(generationId), new ServerState(),
        config, getReplSessionSecurity());
    connect(broker, timeout);
    return broker;
  }

  protected DomainFakeCfg newFakeCfg(final DN baseDN, int serverId, int port)
  {
    return newFakeCfg(baseDN, serverId, port, NO_GROUP_ID);
  }

  protected DomainFakeCfg newFakeCfg(final DN baseDN, int serverId, int port, int groupId)
  {
    DomainFakeCfg fakeCfg =
        new DomainFakeCfg(baseDN, serverId, newTreeSet("127.0.0.1:" + port), groupId);
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
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        if (rb.isConnected())
        {
          logger.trace("checkConnection: connection of broker " + rb.getServerId()
              + " to RS " + rb.getRsGroupId() + " obtained.");
          return;
        }

        rb.start();
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
    	try {
    		deleteEntry(dn);
    	}catch (Throwable e) {}
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

  /**
   * Sets the result code this server puts on an internal error, the way an administrator
   * would, and asserts that the change was applied.
   * <p>
   * The setting is server-wide, so a test which changes it owns putting it back - after
   * a failure of its own as well, which is why the caller's tearDown() is the place for
   * that rather than a finally in the test body: an assertion failure raised while
   * restoring would otherwise replace the failure the test was reporting.
   *
   * @param resultCode the numeric result code
   * @throws Exception if the modification could not be run at all
   */
  protected static void setServerErrorResultCode(int resultCode) throws Exception
  {
    assertEquals(TestCaseUtils.applyModifications(true,
        "dn: cn=config",
        "changetype: modify",
        "replace: ds-cfg-server-error-result-code",
        "ds-cfg-server-error-result-code: " + resultCode), 0,
        "the server error result code could not be changed");
  }

  private void addConfigEntry(Entry configEntry, String errorMessage) throws Exception
  {
    if (configEntry != null)
    {
      getServerContext().getConfigurationHandler().addEntry(Converters.from(configEntry));
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
        Long value = readMonitorAttrValue(baseDN, attr);
        Assertions.assertThat(value)
            .as("the monitor entry of %s is not registered", baseDN).isNotNull();
        return value;
      }
    });
  }

  /**
   * Reads a monitor attribute of a replication domain, once.
   *
   * @param baseDN the base DN of the domain whose monitor entry to read
   * @param attr the monitor attribute to read
   * @return the value of the attribute, or {@code null} when the monitor entry of the
   *         domain is not registered - which it is not for as long as its session to the
   *         replication server is down
   * @throws Exception if the monitor could not be searched, or if the entry is there and
   *                   does not publish the attribute, which is a wrong name rather than
   *                   something to wait for
   */
  private Long readMonitorAttrValue(final DN baseDN, final String attr) throws Exception
  {
    String monitorFilter = "(&(cn=Directory server*)(domain-name=" + baseDN + "))";
    InternalSearchOperation op =
        connection.processSearch(newSearchRequest("cn=replication,cn=monitor", WHOLE_SUBTREE, monitorFilter));
    if (op.getSearchEntries().isEmpty())
    {
      return null;
    }
    SearchResultEntry entry = op.getSearchEntries().getFirst();
    Long value = entry.parseAttribute(attr).asLong();
    Assertions.assertThat(value)
        .as("the monitor entry of %s does not publish %s", baseDN, attr).isNotNull();
    return value;
  }

  /**
   * Waits for a monitor attribute of a replication domain to reach the expected value.
   * <p>
   * The monitor entry of a domain is deregistered for as long as its session to the
   * replication server is down, which is what a replay failure does to it, and a counter
   * is bumped a moment after the change or the delivery it counts was dealt with:
   * reading the value once would be a race on both counts.
   * <p>
   * The read is deliberately {@link #readMonitorAttrValue(DN, String)} rather than the
   * retrying {@link #getMonitorAttrValue(DN, String)}: a {@link TestTimer} budget is a
   * number of steps rather than a deadline, so one timer waiting on another multiplies
   * them - 150 steps around a read which sleeps ten seconds of its own is 25 minutes,
   * long past the {@code org.opends.test.timeout} the fork is killed on. One timer owns
   * the deadline here, and a monitor entry which is not registered is one failed poll -
   * so the deadline has to be wide enough for a domain which is restarting its session to
   * register it again, which takes the backoff of that restart.
   *
   * @param baseDN the base DN of the domain whose monitor entry to read
   * @param attributeName the monitor attribute to read
   * @param expected the value it must reach
   * @param message what is being asserted
   * @throws Exception if the value was not reached in time
   */
  protected void assertMonitorAttrValueEventually(
      final DN baseDN, final String attributeName, final long expected, final String message)
      throws Exception
  {
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(60, SECONDS)
      .sleepTimes(200, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertEquals(readMonitorAttrValue(baseDN, attributeName), (Long) expected, message);
      }
    });
  }

  /**
   * Checks that a monitor attribute of a replication domain holds the expected value and
   * keeps holding it, over {@link #MONITOR_ATTR_SAMPLES} samples.
   *
   * @param baseDN the base DN of the domain whose monitor entry to read
   * @param attributeName the monitor attribute to read
   * @param expected the value it must hold
   * @param message what is being asserted
   * @throws Exception if the value changes, or if the monitor entry can not be read
   */
  protected void assertMonitorAttrValueStays(
      final DN baseDN, final String attributeName, final long expected, final String message)
      throws Exception
  {
    assertMonitorAttrValueStays(baseDN, attributeName, expected, MONITOR_ATTR_SAMPLES, message);
  }

  /**
   * Checks that a monitor attribute of a replication domain holds the expected value and
   * keeps holding it, over the provided number of samples.
   * <p>
   * Waiting for a value to be reached is not enough to tell that something happened only
   * once: a counter which is bumped a second time goes through the expected value on its
   * way, and the first poll which sees it passes.
   * <p>
   * The samples have to outlast whatever could bump the counter a second time, or the
   * assertion only reads like it is watching for it. The default is enough for a second
   * attempt of the same delivery, which is fifty milliseconds away; a counter which a
   * change delivered again could bump has to be watched for longer than the session
   * restart which brings that delivery, so those call sites pass
   * {@link #MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY}.
   * <p>
   * The monitor entry of a domain is gone for as long as its session is down, which a
   * session restart in the middle of the window does: a read which comes back with
   * nothing is not a sample rather than a failure, and the samples asked for are taken
   * once it is back. So a restart stretches the window rather than shortening it, which
   * is the right way round for what is being asserted, and the entry staying away for
   * {@link #MONITOR_ATTR_SAMPLE_GRACE_IN_MS} is what fails the assertion. The samples are
   * bounded all the same: a domain which restarts its session over and over would
   * otherwise have this wait for one readable moment per restart until the fork is killed
   * for taking too long, which says nothing about the value being watched.
   *
   * @param baseDN the base DN of the domain whose monitor entry to read
   * @param attributeName the monitor attribute to read
   * @param expected the value it must hold
   * @param samples how many times to read the value, at least
   *                {@link #MONITOR_ATTR_SAMPLE_INTERVAL_IN_MS} apart
   * @param message what is being asserted
   * @throws Exception if the value changes, or if the monitor entry can not be read
   */
  protected void assertMonitorAttrValueStays(final DN baseDN, final String attributeName,
      final long expected, final int samples, final String message) throws Exception
  {
    final long now = System.currentTimeMillis();
    final long deadline = now + samples * MONITOR_ATTR_SAMPLE_INTERVAL_IN_MS
        + MONITOR_ATTR_SAMPLES_DEADLINE_IN_MS;
    long readableBy = now + MONITOR_ATTR_SAMPLE_GRACE_IN_MS;
    int taken = 0;
    while (taken < samples)
    {
      final Long value = readMonitorAttrValue(baseDN, attributeName);
      if (value != null)
      {
        assertEquals(value, (Long) expected, message);
        taken++;
        if (taken == samples)
        {
          // Every sample which was asked for held the value: how long they took to take
          // is not what this is asserting.
          return;
        }
        readableBy = System.currentTimeMillis() + MONITOR_ATTR_SAMPLE_GRACE_IN_MS;
      }
      else if (System.currentTimeMillis() > readableBy)
      {
        fail("the monitor entry of " + baseDN + " was not registered again in "
            + MONITOR_ATTR_SAMPLE_GRACE_IN_MS + "ms: " + message);
      }
      if (System.currentTimeMillis() > deadline)
      {
        fail("only " + taken + " of " + samples + " samples of " + attributeName
            + " could be read before the deadline: " + message);
      }
      Thread.sleep(MONITOR_ATTR_SAMPLE_INTERVAL_IN_MS);
    }
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
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        final Entry newEntry = DirectoryServer.getEntry(dn);
        assertNotNull(newEntry);
        Iterable<Attribute> attrs = newEntry.getAllAttributes(attrTypeStr);
        Assertions.assertThat(attrs).isNotEmpty();
        Attribute attr = attrs.iterator().next();
        boolean foundAttributeValue = attr.contains(ByteString.valueOfUtf8(valueString));
        assertEquals(foundAttributeValue, expectedAttributeValueFound, foundMsg);
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
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertEquals(DirectoryServer.entryExists(dn), exist, "Expected entry with dn \"" + dn + "\" would exist");
      }
    });

    Entry entry = DirectoryServer.getEntry(dn);
    return entry != null ? entry.duplicate(true) : null;
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

  /**
   * Applies a modification to the configuration entry of a replication domain, the way an
   * administrator would, and checks that it was applied.
   * <p>
   * The domain reads its configuration for every decision it makes, so a property changed
   * here takes effect on what the domain is doing right now.
   *
   * @param domainConfigDN
   *          the DN of the configuration entry of the domain
   * @param modType
   *          the modification to apply, {@link ModificationType#DELETE} with no value
   *          taking an attribute away so that its property falls back to its default
   * @param attrName
   *          the configuration attribute to modify
   * @param values
   *          the values to set, none when the attribute is being deleted
   */
  protected static void modifyDomainConfig(
      DN domainConfigDN, ModificationType modType, String attrName, String... values)
  {
    final ModifyRequest request = Requests.newModifyRequest(domainConfigDN)
        .addModification(modType, attrName, (Object[]) values);
    final ModifyOperation modOp =
        InternalClientConnection.getRootConnection().processModify(request);
    if (modType == DELETE && modOp.getResultCode() == NO_SUCH_ATTRIBUTE)
    {
      /*
       * The attribute is already gone, which is what the delete was asking for. Said here
       * rather than at the call sites because a delete is how a test puts a property back
       * to its default in a finally: a cleanup which throws would replace the failure it
       * is cleaning up after, and say nothing about it.
       */
      return;
    }
    assertEquals(modOp.getResultCode(), SUCCESS,
        "Cannot " + modType + " " + attrName + " on " + domainConfigDN);
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

  /**
   * Runs the provided action and returns the records the error log received while it ran.
   * <p>
   * A publisher of its own is registered for the duration rather than reading the one the
   * test harness installs, whose contents span the whole test JVM.
   * <p>
   * It publishes every severity, so that a record a throttle kept out of the warnings is
   * captured too. That is server wide while the action runs, so the records of other
   * threads are captured as well and a caller has to pick out its own.
   *
   * @param action
   *          The action to run.
   * @return The error log records written while the action ran, in order.
   * @throws Exception
   *           Whatever the action throws.
   */
  protected static List<String> errorLogRecordsOf(final Callable<Void> action) throws Exception
  {
    return errorLogRecordsOf(unused -> action.call());
  }

  /**
   * Runs the provided action, which reads the error log records as they are written, and
   * returns the records the error log received while it ran.
   * <p>
   * The list handed to the action is the live one, so an action can wait for a server
   * thread to log something instead of waiting for a duration. It is synchronized, and
   * iterating it is not: a reader has to copy it, or hold its monitor.
   *
   * @param action
   *          The action to run, taking the records written so far.
   * @return The error log records written while the action ran, in order.
   * @throws Exception
   *           Whatever the action throws.
   */
  // The publisher is built raw and handed to a parameterized addLogPublisher: the
  // conversion is unchecked, and it is the one the test harness makes as well.
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected static List<String> errorLogRecordsOf(ErrorLogAction action) throws Exception
  {
    final List<String> records = Collections.synchronizedList(new ArrayList<String>());
    final ErrorLogPublisher capture =
        TextErrorLogPublisher.getToolStartupTextErrorPublisher(new TextWriter()
        {
          @Override
          public void writeRecord(String record)
          {
            records.add(record);
          }

          @Override
          public void flush()
          {
            // Nothing is buffered.
          }

          @Override
          public void shutdown()
          {
            // Nothing is buffered.
          }

          @Override
          public long getBytesWritten()
          {
            return 0;
          }
        });
    ErrorLogger.getInstance().addLogPublisher(capture);
    try
    {
      action.run(records);
    }
    finally
    {
      ErrorLogger.getInstance().removeLogPublisher(capture);
    }
    // Copied under the monitor: the publishers are iterated from a snapshot, so a thread
    // which is already inside it can still write a record after this one was removed, and
    // the caller must not have to synchronize to read what it got.
    return copyOf(records);
  }

  /**
   * Returns the one record of the provided error log which reports the provided message.
   * <p>
   * A record holds the severity it was published with next to the message, so this is how
   * a test reads the severity of a message it expects, rather than only its text.
   *
   * @param records
   *          The error log records to look in, as {@link #errorLogRecordsOf} returned them.
   * @param message
   *          The message the record is expected to report.
   * @return The record reporting the provided message.
   */
  protected static String recordOf(List<String> records, LocalizableMessage message)
  {
    String found = null;
    for (String record : records)
    {
      if (record.contains(message.toString()))
      {
        assertNull(found, "\"" + message + "\" should have been logged once, but the error log"
            + " holds it more than once: " + records);
        found = record;
      }
    }
    assertNotNull(found, "\"" + message + "\" should have been logged, but the error log holds: " + records);
    return found;
  }

  /**
   * Returns how many records of the provided error log hold the provided text.
   *
   * @param records
   *          The error log records to count in.
   * @param contained
   *          The text the counted records hold.
   * @return The number of records holding the provided text.
   */
  protected static int countRecordsOf(List<String> records, String contained)
  {
    int count = 0;
    synchronized (records)
    {
      for (String record : records)
      {
        if (record.contains(contained))
        {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Waits for a record holding the provided text to be written to the provided error log.
   * <p>
   * For what a server thread logs on its own schedule: the connect thread of a replication
   * server retries a peer every second, so what it reports is waited for rather than
   * expected to be there already.
   * <p>
   * Only the records which arrived since the previous poll are read: the capture publishes
   * every severity, which is every {@code logger.debug} of every thread of the server for
   * as long as it is installed, so rereading the whole list on each poll would cost the
   * square of what a slow wait captures.
   *
   * @param records
   *          The live error log records, as {@link ErrorLogAction} received them.
   * @param contained
   *          The text the awaited record holds.
   * @param timeoutMs
   *          How long to wait for it, in milliseconds.
   * @throws InterruptedException
   *           If the wait is interrupted.
   */
  protected static void waitForErrorLogRecord(List<String> records, String contained, long timeoutMs)
      throws InterruptedException
  {
    final long deadline = System.currentTimeMillis() + timeoutMs;
    int read = 0;
    while (true)
    {
      synchronized (records)
      {
        final int written = records.size();
        while (read < written)
        {
          if (records.get(read++).contains(contained))
          {
            return;
          }
        }
      }
      // Tested after the records are read and not after the sleep, so that what arrives
      // during the last sleep of the wait is still read: it arrived inside the timeout.
      if (System.currentTimeMillis() >= deadline)
      {
        break;
      }
      Thread.sleep(100);
    }
    fail("\"" + contained + "\" should have been logged within " + timeoutMs
        + " ms, but the error log received " + read + " records, the last of them: "
        + lastRecordsOf(records));
  }

  /** Copies the live error log records, which are synchronized but not safe to iterate. */
  private static List<String> copyOf(List<String> records)
  {
    synchronized (records)
    {
      return new ArrayList<>(records);
    }
  }

  /**
   * The tail of the live error log records, for a failure message: the capture holds every
   * severity, so the whole list is not something a report can carry.
   */
  private static List<String> lastRecordsOf(List<String> records)
  {
    final List<String> all = copyOf(records);
    return all.subList(Math.max(0, all.size() - 20), all.size());
  }

  /** An action which reads the error log records as they are written. */
  protected interface ErrorLogAction
  {
    /**
     * Runs the action.
     *
     * @param records
     *          The live error log records written since the action started.
     * @throws Exception
     *           Whatever the action throws.
     */
    void run(List<String> records) throws Exception;
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

    if (expectedTaskState == RUNNING
        && (taskState == COMPLETED_SUCCESSFULLY || taskState == STOPPED_BY_ERROR))
    {
      // We usually wait the running state after adding the task
      // and if the task is fast enough then it may already be done
      // (successfully or not) and we can go on: callers interested in the
      // final state wait for it explicitly afterwards.
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
        Attribute attribute = newEntry.getAllAttributes("entryuuid").iterator().next();
        ByteString found = attribute.iterator().next();
        assertNotNull(found, "Entry: " + dn + " Could not be found.");
        return found.toString();
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
        try {
          rdPort = rd.getReplicationServer().getPort();
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
