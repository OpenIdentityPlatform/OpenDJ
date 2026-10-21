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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.service.ReplicationDomain;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.CollectionUtils.newArrayList;
import static org.testng.Assert.*;

/**
 * Test the usage of the historical data of the replication.
 */
@SuppressWarnings("javadoc")
public class HistoricalCsnOrderingTest extends ReplicationTestCase
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  private final int serverId = 123;
  /** Another replica, whose changes the replica under test replays. */
  private static final int OTHER_SERVER_ID = 124;
  /**
   * How many rounds the missing change publisher is given to end a recovery while local changes
   * keep being done: one round which sees changes done while it ran, and the last one, which holds
   * them back.
   */
  private static final int MAX_ROUNDS_UNDER_LOAD = 20;
  /** How much time of changes a round of the missing change publisher searches at most. */
  private static final long SEARCH_WINDOW_MS = 10000;
  /** The server ID of the replication server of the tests, and of the one some of them add. */
  private static final int FIRST_RS_ID = 1;
  private static final int SECOND_RS_ID = 2;
  private final SortedSet<String> replServers = new TreeSet<>();
  private int rsPort;

  private static class TestBroker extends ReplicationBroker
  {
    private final List<ReplicationMsg> list;
    /** Whether this broker writes the messages to a session, or refuses them as one without any. */
    private final boolean writes;

    private TestBroker(List<ReplicationMsg> list)
    {
      this(list, true);
    }

    private TestBroker(List<ReplicationMsg> list, boolean writes)
    {
      super(new DummyReplicationDomain(0), null,
          new DomainFakeCfg(null, 0, new TreeSet<String>()), null);
      this.list = list;
      this.writes = writes;
    }

    @Override
    public boolean publishRecovery(ReplicationMsg msg, Session session)
    {
      if (writes)
      {
        list.add(msg);
      }
      return writes;
    }

  }

  /** Runs a test as the recovery keeps the local changes done while it runs, and as it keeps none. */
  @DataProvider
  public Object[][] keepingTheLocalChanges()
  {
    return new Object[][] { { true }, { false } };
  }

  /**
   * Forgets the replication server of the test: the next one, and the domains of the next tests,
   * would be configured with its port, which nothing listens on anymore.
   */
  @AfterMethod
  public void forgetReplicationServers()
  {
    replServers.clear();
  }

  private MatchingRule getRule()
  {
    String oid = "1.3.6.1.4.1.26027.1.4.4";
    return new SchemaBuilder(CoreSchema.getInstance())
        .buildMatchingRule(oid).names("historicalCsnOrderingMatch")
          .syntaxOID("1.3.6.1.4.1.1466.115.121.1.40")
          .implementation(new HistoricalCsnOrderingMatchingRuleImpl())
          .addToSchema()
        .toSchema().getMatchingRule(oid);
  }

  /**
   * Check the basic comparator on the HistoricalCsnOrderingMatchingRule.
   */
  @Test
  public void basicRuleTest() throws Exception
  {
    // Creates a rule
    MatchingRule rule = getRule();

    CSN del1 = new CSN(1,  0,  1);
    CSN del2 = new CSN(1,  1,  1);

    ByteString v1 = ByteString.valueOfUtf8("a:" + del1);
    ByteString v2 = ByteString.valueOfUtf8("a:" + del2);

    Assertion assert1 = rule.getAssertion(v2);
    assertEquals(assert1.matches(rule.normalizeAttributeValue(v1)), ConditionResult.TRUE);
    assertEquals(assert1.matches(rule.normalizeAttributeValue(v2)), ConditionResult.FALSE);

    Assertion assert2 = rule.getLessOrEqualAssertion(v2);
    assertEquals(assert2.matches(rule.normalizeAttributeValue(v1)), ConditionResult.TRUE);
    assertEquals(assert2.matches(rule.normalizeAttributeValue(v2)), ConditionResult.TRUE);

    Assertion assert3 = rule.getGreaterOrEqualAssertion(v2);
    assertEquals(assert3.matches(rule.normalizeAttributeValue(v1)), ConditionResult.FALSE);
    assertEquals(assert3.matches(rule.normalizeAttributeValue(v2)), ConditionResult.TRUE);

  }

  /**
   * Test that we can retrieve the entries that were missed by
   * a replication server and can  re-build operations from the historical
   * informations.
   */
  @Test
  public void buildAndPublishMissingChangesOneEntryTest() throws Exception
  {
    final int serverId = 123;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    // Create Replication Server and Domain
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);

    try
    {
      long startTime = TimeThread.getTime();
      final DN dn1 = DN.valueOf("cn=test1," + baseDN);
    final AttributeType histType =
      getServerContext().getSchema().getAttributeType(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);

    logger.error(LocalizableMessage.raw("Starting replication test : changesCmpTest"));

    // Add the first test entry.
    TestCaseUtils.addEntry(
        "dn: cn=test1," + baseDN,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Perform a first modification to update the historical attribute
    int resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDN,
        "changetype: modify",
        "add: description",
        "description: foo");
    assertEquals(resultCode, 0);

    // Read the entry back to get its historical and included CSN
    Entry entry = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs1 = entry.getAllAttributes(histType);
      Assertions.assertThat(attrs1).isNotEmpty();

    String histValue = attrs1.get(0).iterator().next().toString();

    logger.error(LocalizableMessage.raw("First historical value:" + histValue));

    // Perform a 2nd modification to update the hist attribute with
    // a second value
    resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDN,
        "changetype: modify",
        "add: description",
        "description: bar");
    assertEquals(resultCode, 0);

    Entry entry2 = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs2 = entry2.getAllAttributes(histType);
      Assertions.assertThat(attrs2).isNotEmpty();

    for (ByteString av : attrs2.get(0)) {
      logger.error(LocalizableMessage.raw("Second historical value: %s", av));
    }

    LinkedList<ReplicationMsg> opList = new LinkedList<>();
    TestBroker session = new TestBroker(opList);

      CSN csn = new CSN(startTime, 0, serverId);
      boolean result = rd1.buildAndPublishMissingChanges(csn, session);
    assertTrue(result, "buildAndPublishMissingChanges has failed");
    assertEquals(opList.size(), 3, "buildAndPublishMissingChanges should return 3 operations");
    assertEquals(opList.getFirst().getClass(), AddMsg.class);


    // Build a CSN from the first modification
    String hv[] = histValue.split(":");
    logger.error(LocalizableMessage.raw(hv[1]));
    CSN fromCSN = new CSN(hv[1]);

    opList = new LinkedList<>();
    session = new TestBroker(opList);

      result = rd1.buildAndPublishMissingChanges(fromCSN, session);
    assertTrue(result, "buildAndPublishMissingChanges has failed");
    assertEquals(opList.size(), 1, "buildAndPublishMissingChanges should return 1 operation");
    assertEquals(opList.getFirst().getClass(), ModifyMsg.class);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * Test that we can retrieve the entries that were missed by
   * a replication server and can  re-build operations from the historical
   * informations.
   */
  @Test
  public void buildAndPublishMissingChangesSeveralEntriesTest() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    // Create Replication Server and Domain
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    long startTime = TimeThread.getTime();

    try
    {
    logger.error(LocalizableMessage.raw("Starting replication test : changesCmpTest"));

    // Add 3 entries.
    DN dnTest1 = DN.valueOf("cn=test1," + baseDN);
    DN dnTest2 = DN.valueOf("cn=test2," + baseDN);
    DN dnTest3 = DN.valueOf("cn=test3," + baseDN);
    TestCaseUtils.addEntry(
        "dn: " + dnTest3,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );
    TestCaseUtils.addEntry(
        "dn: " + dnTest1,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );
    TestCaseUtils.deleteEntry(dnTest3);
    TestCaseUtils.addEntry(
        "dn: " + dnTest2,
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Perform modifications on the 2 entries
    int resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test2," + baseDN,
        "changetype: modify",
        "add: description",
        "description: foo");
    resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDN,
        "changetype: modify",
        "add: description",
        "description: foo");
    assertEquals(resultCode, 0);

    LinkedList<ReplicationMsg> opList = new LinkedList<>();
    TestBroker session = new TestBroker(opList);

      // Call the buildAndPublishMissingChanges and check that this method
      // correctly generates the 4 operations in the correct order.
      CSN csn = new CSN(startTime, 0, serverId);
      boolean result = rd1.buildAndPublishMissingChanges(csn, session);
    assertTrue(result, "buildAndPublishMissingChanges has failed");
    assertEquals(opList.size(), 5, "buildAndPublishMissingChanges should return 5 operations");
    ReplicationMsg msg = opList.removeFirst();
    assertEquals(msg.getClass(), AddMsg.class);
    assertEquals(((LDAPUpdateMsg) msg).getDN(), dnTest1);
    msg = opList.removeFirst();
    assertEquals(msg.getClass(), DeleteMsg.class);
    assertEquals(((LDAPUpdateMsg) msg).getDN(), dnTest3);
    msg = opList.removeFirst();
    assertEquals(msg.getClass(), AddMsg.class);
    assertEquals(((LDAPUpdateMsg) msg).getDN(), dnTest2);
    msg = opList.removeFirst();
    assertEquals(msg.getClass(), ModifyMsg.class);
    assertEquals(((LDAPUpdateMsg) msg).getDN(), dnTest2);
    msg = opList.removeFirst();
    assertEquals(msg.getClass(), ModifyMsg.class);
    assertEquals(((LDAPUpdateMsg) msg).getDN(), dnTest1);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      rs.remove();
    }
  }

  /**
   * A change done while a round of the missing change publisher searches is one its search did not
   * see. The round may not publish a later change instead: the next round searches from the last
   * change published, so the change in between would never be published by anyone. Only a
   * delete, which is kept aside as it is done, escaped this. A recovery which keeps the local
   * changes publishes it as it kept it, and one which keeps none finds it in a later round.
   */
  @Test(dataProvider = "keepingTheLocalChanges")
  public void aChangeDoneWhileARoundSearchesIsPublished(boolean keeps) throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "before the recovery");
      done.add(rd1.getLastLocalChange());
      // Started after the change, which the recovery then rebuilds from the historical information.
      startRecovery(rd1, keeps);
      rd1.setMissingChangesSearchHook(once(() ->
      {
        replace(dnB, "description", "while the first round searches");
        done.add(rd1.getLastLocalChange());
        TestCaseUtils.deleteEntry(dnC);
        done.add(rd1.getLastLocalChange());
      }));

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A round which has found nothing to publish may not end the recovery past the last local
   * change: a change done while it searched is left to the recovery all the same, and nothing else
   * would publish it. Only a recovery which searches for the local changes done while it runs - one
   * which kept too many of them - has such a round.
   */
  @Test
  public void aRoundWhichFoundNothingDoesNotEndTheRecoveryPastAChange() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1, false);
      final List<CSN> done = new ArrayList<>();
      rd1.setMissingChangesSearchHook(once(() ->
      {
        replace(dnA, "description", "while the first round searches");
        done.add(rd1.getLastLocalChange());
      }));

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * Once the missing change publisher has ended the recovery, a local change is published to the
   * replication server again: the broker, which refuses the changes left to the recovery, takes
   * them back as the pending changes stop leaving them to it, and not some time later.
   */
  @Test
  public void aChangeDoneOnceTheRecoveryEndedIsPublished() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      startRecovery(rd1);

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(published).isEmpty();

      replace(dnA, "description", "after the recovery");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The broker takes the local changes again under the lock they are pushed under, as the recovery
   * ends. Taken after it, a change pushed in between - which the pending changes publish again -
   * is refused, while the domain state records it as done and no recovery is left to publish it.
   */
  @Test
  public void theBrokerTakesChangesAgainUnderTheLockTheyArePushedUnder() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      startRecovery(rd1);

      final PendingChanges pendingChanges = pendingChangesOf(rd1);
      final ReplicationBroker realBroker = brokerOf(rd1);
      final AtomicBoolean clearedUnderTheLock = new AtomicBoolean();
      setBroker(rd1, new TestBroker(new ArrayList<ReplicationMsg>())
      {
        @Override
        public void setRecoveryRequired(boolean required)
        {
          if (!required)
          {
            clearedUnderTheLock.set(Thread.holdsLock(pendingChanges));
          }
        }
      });
      try
      {
        assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(new ArrayList<ReplicationMsg>())));
      }
      finally
      {
        setBroker(rd1, realBroker);
        realBroker.setRecoveryRequired(false);
      }
      assertTrue(clearedUnderTheLock.get(), "the broker takes the changes again under the lock they are pushed under");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The missing change publisher publishes the changes of its own replica only. A change of
   * another replica, which this one replayed, is in the historical information of an entry the
   * search finds all the same; the replication servers have it from that replica, and one they had
   * from here first would make them drop the older changes of that replica which reach them after
   * it.
   */
  @Test
  public void onlyTheChangesOfThisReplicaArePublished() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    ReplicationBroker other = null;
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      other = openReplicationSession(baseDN, OTHER_SERVER_ID, 100, rsPort, 1000);
      final CSN otherCSN = new CSNGenerator(OTHER_SERVER_ID, TimeThread.getTime()).newCSN();
      publishOtherReplicaChange(other, otherCSN, dnA, "from the other replica");
      waitUntilReplayed(rd1, otherCSN);

      replace(dnA, "displayName", "from this replica");
      final CSN localCSN = rd1.getLastLocalChange();
      assertTrue(otherCSN.isOlderThan(localCSN), "the change of the other replica comes first");
      // Started after the change, which the recovery then rebuilds from the historical information.
      startRecovery(rd1);

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactly(localCSN);
    }
    finally
    {
      stop(other);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * Local changes go on while a recovery which searches for them runs - one which kept too many of
   * them. The recovery still ends - its last round holds them back - and each change is published
   * either by the recovery or, once it has ended, by the broker: none is left to a recovery which is
   * over.
   */
  @Test
  public void aRecoveryEndsWhileLocalChangesGoOnAndLosesNone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    final ExecutorService writer = Executors.newSingleThreadExecutor();
    try
    {
      final List<DN> dns = new ArrayList<>();
      for (int i = 0; i <= MAX_ROUNDS_UNDER_LOAD; i++)
      {
        dns.add(addPerson("load" + i));
      }
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1, false);
      final List<CSN> done = new CopyOnWriteArrayList<>();
      final List<Future<?>> changes = new CopyOnWriteArrayList<>();
      final AtomicInteger rounds = new AtomicInteger();
      rd1.setMissingChangesSearchHook(search ->
      {
        final int round = rounds.incrementAndGet();
        if (round > MAX_ROUNDS_UNDER_LOAD)
        {
          throw new AssertionError("the recovery is still running after " + MAX_ROUNDS_UNDER_LOAD + " rounds");
        }
        doLocalChange(writer, changes, done, rd1, dns.get(round), round);
      });

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      rd1.setMissingChangesSearchHook(null);
      assertEachChangePublishedOnce(rs, baseDN, rsMaxCSN, published, changes, done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      writer.shutdownNow();
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A change done in the last millisecond of a search window is published. The end of a window is
   * compared with the historical information twice: by the index of ds-sync-hist, as text, and by
   * the round, as a CSN. A sequence number which reads as the greatest in one and as the least in
   * the other left such a change out of its window, and out of the next one, which starts after it.
   */
  @Test
  public void aChangeInTheLastMillisecondOfASearchWindowIsPublished() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    ReplicationBroker other = null;
    try
    {
      // Opened first: the change below has to be done less than a window after the ones before.
      other = openReplicationSession(baseDN, OTHER_SERVER_ID, 100, rsPort, 1000);
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      final long endOfFirstWindow = rsMaxCSN.getTime() + SEARCH_WINDOW_MS;

      // A replica numbers its changes after the newest one it replayed: a change done right after
      // one of the other replica from the millisecond before falls in the last one of the window.
      final CSN otherCSN = new CSN(endOfFirstWindow - 1, 1, OTHER_SERVER_ID);
      publishOtherReplicaChange(other, otherCSN, dnB, "a millisecond before the end of the window");
      waitUntilReplayed(rd1, otherCSN);

      replace(dnA, "description", "in the last millisecond of the window");
      final CSN localCSN = rd1.getLastLocalChange();
      assertEquals(localCSN.getTime(), endOfFirstWindow, "the change is in the last millisecond of the window");
      // Started after the change, which the recovery then rebuilds from the historical information.
      startRecovery(rd1);

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactly(localCSN);
    }
    finally
    {
      stop(other);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * Once the recovery holds the local changes back, it ends there, however far the changes done
   * meanwhile ran ahead. A held-back round which stopped at the end of its search window and let
   * the local changes go again left the next round as far behind as the one before it, and the
   * recovery went on for as long as the local changes did. Only a recovery which searches for the
   * local changes done while it runs - one which kept too many of them - holds them back for rounds.
   */
  @Test
  public void aRecoveryEndsOnceItHoldsTheLocalChangesBackHoweverFarTheyRanAhead() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    ReplicationBroker other = null;
    final ExecutorService writer = Executors.newSingleThreadExecutor();
    try
    {
      final DN dnOther = addPerson("other");
      final List<DN> dns = new ArrayList<>();
      for (int i = 0; i <= MAX_ROUNDS_UNDER_LOAD; i++)
      {
        dns.add(addPerson("load" + i));
      }
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      final ReplicationBroker otherReplica = other =
          openReplicationSession(baseDN, OTHER_SERVER_ID, 100, rsPort, 1000);

      startRecovery(rd1, false);
      final List<CSN> done = new CopyOnWriteArrayList<>();
      final List<Future<?>> changes = new CopyOnWriteArrayList<>();
      final AtomicInteger rounds = new AtomicInteger();
      rd1.setMissingChangesSearchHook(search ->
      {
        final int round = rounds.incrementAndGet();
        if (round > MAX_ROUNDS_UNDER_LOAD)
        {
          throw new AssertionError("the recovery is still running after " + MAX_ROUNDS_UNDER_LOAD + " rounds");
        }
        // The local change done after this change of the other replica is numbered after it, further
        // ahead of the last local change than a search window reaches.
        final CSN otherCSN = new CSN(
            rd1.getLastLocalChange().getTime() + SEARCH_WINDOW_MS + 1000, round, OTHER_SERVER_ID);
        unchecked(() ->
        {
          publishOtherReplicaChange(otherReplica, otherCSN, dnOther, "round " + round);
          waitUntilReplayed(rd1, otherCSN);
        }).run();
        doLocalChange(writer, changes, done, rd1, dns.get(round), round);
      });

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      rd1.setMissingChangesSearchHook(null);
      assertEachChangePublishedOnce(rs, baseDN, rsMaxCSN, published, changes, done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      writer.shutdownNow();
      stop(other);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A round counts as published only what the broker did not refuse. A round which counted the
   * changes refused by a broker which reached no replication server could end the recovery without
   * them, and the deletes kept aside for the recovery were dropped as it ended: the next recovery
   * has to find all of them still. The first change refused is a delete, which only the list it is
   * kept aside in, or the local changes the recovery keeps, can publish again: a modify, the search
   * of the next recovery finds again.
   */
  @Test(dataProvider = "keepingTheLocalChanges")
  public void changesTheBrokerRefusedAreLeftToTheNextRecovery(boolean keeps) throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1, keeps);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnB);
      done.add(rd1.getLastLocalChange());
      replace(dnA, "description", "while no session takes it");
      done.add(rd1.getLastLocalChange());

      assertFalse(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(new ArrayList<ReplicationMsg>(), false)),
          "a recovery whose changes the broker refused has failed");

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A session whose replication server misses no change of this replica ends all the same the
   * recovery a refused publisher left. Here the replication server had the change the broker
   * refused already - back on the one which had everything, for one - and no recovery of the
   * session ended the one left: every local change was recorded in the domain state, and
   * published by nobody.
   */
  @Test
  public void aSessionWhichNeedsNoRecoveryEndsTheOneARefusedPublisherLeft() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN beforeB = rd1.getLastLocalChange();
      addPerson("b");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());

      startRecovery(rd1);
      assertFalse(rd1.buildAndPublishMissingChanges(beforeB, new TestBroker(new ArrayList<ReplicationMsg>(), false)),
          "a recovery whose change the broker refused has failed");
      // as the missing change publisher does once it failed
      brokerOf(rd1).setRecoveryRequired(false);

      rd1.disable();
      rd1.enable();
      // The broker starts its session before enable() returns: a change done before would have
      // been ahead of the replication server, and the session would have recovered it.
      assertTrue(rd1.isConnected(), "the session is up");

      replace(dnA, "description", "in a session which needs no recovery");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /** A stopped broker refuses a recovery message: the recovery has to learn it wrote nothing. */
  @Test
  public void aStoppedBrokerRefusesARecoveryMessage() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    ReplicationBroker broker = null;
    try
    {
      broker = openReplicationSession(baseDN, OTHER_SERVER_ID, 100, rsPort, 1000);
      final Session session = broker.getSession();
      broker.stop();
      assertFalse(broker.publishRecovery(new WindowProbeMsg(), session), "a stopped broker writes nothing");
    }
    finally
    {
      stop(broker);
      remove(rs);
    }
  }

  /**
   * A broker refuses a recovery message for a session it no longer publishes on. The recovery of
   * that session publishes what its replication server misses, and the replication server of the
   * session which replaced it may miss older changes: one which got a later change first would take
   * none of them.
   */
  @Test
  public void aBrokerRefusesARecoveryMessageForASessionItReplaced() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    ReplicationBroker broker = null;
    try
    {
      broker = openReplicationSession(baseDN, OTHER_SERVER_ID, 100, rsPort, 1000);
      final Session replaced = broker.getSession();
      broker.stop();
      broker.start();
      final Session session = broker.getSession();
      assertNotNull(session, "the broker has a session again");
      assertNotSame(session, replaced);
      assertFalse(broker.publishRecovery(new WindowProbeMsg(), replaced), "a recovery message for a replaced session");
      assertTrue(broker.publishRecovery(new WindowProbeMsg(), session), "a recovery message for the session");
    }
    finally
    {
      stop(broker);
      remove(rs);
    }
  }

  /**
   * A round whose search did not go through publishes nothing. What the search found may be some
   * of the changes only: a replication server which took them would take none of the ones it
   * missed, which are older, and no later recovery could publish those. The next recovery publishes
   * them all, in order, and the delete kept aside with them.
   */
  @Test
  public void aRoundWhoseSearchFailedPublishesNothing() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "before the search fails");
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(dnB);
      done.add(rd1.getLastLocalChange());
      replace(dnC, "description", "before the search fails");
      done.add(rd1.getLastLocalChange());
      // Started after the changes, which the recovery then rebuilds from the historical information.
      startRecovery(rd1);

      rd1.setMissingChangesSearchHook(onTheFirstSearch(search -> search.setResultCode(ResultCode.UNAVAILABLE)));
      final List<ReplicationMsg> published = new ArrayList<>();
      Assertions.assertThatThrownBy(() -> rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)))
          .as("a recovery whose search failed")
          .hasMessageContaining(ResultCode.UNAVAILABLE.getName().toString());
      assertThat(published).as("what a round whose search failed published").isEmpty();

      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The deletes a round was to publish and did not, as it threw on the way, stay where they were:
   * they have nothing else to be published from, and the recovery of the session started again
   * publishes them.
   */
  @Test(dataProvider = "keepingTheLocalChanges")
  public void theDeletesARoundThrewBeforePublishingAreLeftToTheNextRecovery(boolean keeps) throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1, keeps);
      TestCaseUtils.deleteEntry(dnA);
      final CSN first = rd1.getLastLocalChange();
      TestCaseUtils.deleteEntry(dnB);
      final CSN second = rd1.getLastLocalChange();

      final List<ReplicationMsg> published = new ArrayList<>();
      final ReplicationBroker throwingOnTheSecond = new TestBroker(published)
      {
        @Override
        public boolean publishRecovery(ReplicationMsg msg, Session session)
        {
          if (!published.isEmpty())
          {
            throw new IllegalStateException("the publish threw, as a test asked");
          }
          return super.publishRecovery(msg, session);
        }
      };
      Assertions.assertThatThrownBy(() -> rd1.buildAndPublishMissingChanges(rsMaxCSN, throwingOnTheSecond))
          .isInstanceOf(IllegalStateException.class);
      assertThat(csns(published)).containsExactly(first);

      // The replication server of the session started again holds the delete which was published.
      final List<ReplicationMsg> republished = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(first, new TestBroker(republished)));
      assertThat(csns(republished)).containsExactly(second);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A recovery whose search fails on a session which stays up has the session started again, and
   * the recovery of the next session publishes the changes, in order. No next session came: the
   * local changes were left to a recovery nobody ran - recorded in the domain state and published by
   * nobody - until the session went down for some other reason.
   */
  @Test
  public void aRecoveryWhoseSearchFailedHasTheSessionStartedAgain() throws Exception
  {
    assertARecoveryWhichFailedHasTheSessionStartedAgain(search -> search.setResultCode(ResultCode.UNAVAILABLE));
  }

  /** A recovery which threw on a session which stays up has the session started again as well. */
  @Test
  public void aRecoveryWhichThrewHasTheSessionStartedAgain() throws Exception
  {
    assertARecoveryWhichFailedHasTheSessionStartedAgain(search ->
    {
      throw new IllegalStateException("the round threw, as a test asked");
    });
  }

  private void assertARecoveryWhichFailedHasTheSessionStartedAgain(Consumer<InternalSearchOperation> failure)
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);

      // The replication server misses the changes done from here on.
      refuseTheLocalChanges(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "missed by the replication server");
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(dnB);
      done.add(rd1.getLastLocalChange());

      // The session started again recovers them, and the first round of its recovery fails, with a
      // change done meanwhile: the recovery keeps it, and the next one publishes it.
      rd1.setMissingChangesSearchHook(onTheFirstSearch(search ->
      {
        unchecked(() ->
        {
          replace(dnA, "displayName", "while the failing round searches");
          done.add(rd1.getLastLocalChange());
        }).run();
        failure.accept(search);
      }));
      rd1.disable();
      rd1.enable();

      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(rs, baseDN, rsMaxCSN))
              .as("the changes the replication server holds after the ones it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);
      replace(dnA, "description", "once the recovery is over");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The search of a round which finds no base entry went through: the suffix is gone, and no entry
   * is left below it to hold a change. The deletes which removed it go out, the recovery ends, and
   * the base entry added back is published as any other change. A recovery which took that search
   * for a failed one would wait for the suffix to come back.
   */
  @Test
  public void theDeletesOfASuffixRemovedDuringARecoveryArePublished() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(baseDN);
      done.add(rd1.getLastLocalChange());
      // Started after the deletes, which the recovery then searches for: the deletes a recovery keeps
      // as they are done need no search.
      startRecovery(rd1);

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)),
          "a recovery whose search found no base entry is over");
      assertThat(csns(published)).containsExactlyElementsOf(done);

      TestCaseUtils.addEntry(StaticUtils.createEntry(baseDN));
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The deletes kept aside outlive a session whose replication server holds no change of this
   * replica. Its missing change publisher starts from the newest change of this replica and has
   * nothing older to publish to it, but a later session may reach a replication server which misses
   * some of those deletes, and they have nothing else to be published from. They were dropped -
   * trimmed as not newer than where that publisher started, and cleared as its recovery ended - and
   * the replication server of the later session never had the delete.
   */
  @Test
  public void theDeletesKeptAsideOutliveASessionToAReplicationServerWithNoChangeOfThisReplica()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    final SortedSet<String> towardsRs = new TreeSet<>(replServers);
    ReplicationServer empty = null;
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      addPerson("a");
      final DN dnB = addPerson("b");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());

      // As a publisher whose broker refused a change leaves it.
      startRecovery(rd1);
      brokerOf(rd1).setRecoveryRequired(false);
      TestCaseUtils.deleteEntry(dnB);
      final CSN delete = rd1.getLastLocalChange();

      // A session to a replication server which holds no change of this replica, and knows of no
      // other replication server: the publisher of the session ends the recovery.
      final int emptyPort = TestCaseUtils.findFreePort();
      empty = createIsolatedReplicationServer(emptyPort, "HistoricalCsnOrderingEmpty", SECOND_RS_ID);
      connectTo(rd1, towards(emptyPort), SECOND_RS_ID);
      waitUntilTheRecoveryIsOver(rd1);
      assertNull(empty.getReplicationServerDomain(baseDN, true).getLatestServerState().getCSN(serverId),
          "the replication server holding no change of this replica was sent none");

      // A session to the replication server which misses the delete.
      assertEquals(rd1.applyConfigurationChange(domainConfiguration(serverId, towardsRs)).getResultCode(),
          ResultCode.SUCCESS);
      assertInTheChangelog(rs, baseDN, delete);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs, empty);
    }
  }

  /**
   * A session whose replication server holds no change of this replica ends the recovery a failed
   * publisher left, on a replica whose own domain state holds none of its changes either: its
   * publisher starts from the current time.
   */
  @Test
  public void aRecoveryLeftOnAReplicaWithNoChangeOfItsOwnEnds() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      assertNull(rd1.getServerState().getCSN(serverId), "the domain state holds no change of this replica");
      // As a publisher whose broker refused a change leaves it.
      startRecovery(rd1);
      brokerOf(rd1).setRecoveryRequired(false);

      rd1.disable();
      rd1.enable();
      waitUntilTheRecoveryIsOver(rd1);

      addPerson("a");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A change numbered exactly the end of a search window is published. The search finds it - its
   * filter takes the end - but a round which took only the changes older than the end left it out,
   * and the next window starts after it.
   */
  @Test
  public void aChangeNumberedTheEndOfASearchWindowIsPublished() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      final CSN endOfFirstWindow =
          new CSN(rsMaxCSN.getTime() + SEARCH_WINDOW_MS, Integer.MAX_VALUE, serverId);

      // The next change is numbered right after this one, in the same millisecond: the clock has not
      // reached it yet, the change being done less than a window after the one before.
      rd1.getGenerator().adjust(new CSN(endOfFirstWindow.getTime() - 1, Integer.MAX_VALUE - 1, serverId));
      replace(dnA, "description", "numbered the end of the window");
      assertEquals(rd1.getLastLocalChange(), endOfFirstWindow, "the change is numbered the end of the window");
      // Started after the change, which the recovery then rebuilds from the historical information.
      startRecovery(rd1);

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactly(endOfFirstWindow);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A local change done during a recovery is published as it was done, even once a later change has
   * replaced its values: the historical information of the entry keeps the later one only, so a
   * recovery which rebuilt the local changes from it published the later change alone, and the other
   * replicas never had the first - the changelog had a gap in the sequence numbers of this replica
   * where it was.
   */
  @Test
  public void aChangeWhoseValuesALaterOneReplacedIsPublished() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "first");
      done.add(rd1.getLastLocalChange());
      replace(dnA, "description", "second");
      done.add(rd1.getLastLocalChange());

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The local changes are held back at the end of a recovery for as long as publishing the ones done
   * during the round before takes, and not for a search: the recovery keeps the changes done since it
   * began, and needs none for them. One which searched for them did so while every local write
   * waited for it.
   */
  @Test
  public void noSearchRunsWhileTheLocalChangesAreHeldBack() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "before the recovery");
      done.add(rd1.getLastLocalChange());
      // Started after the change, which the recovery then rebuilds from the historical information.
      startRecovery(rd1);
      final PendingChanges pendingChanges = pendingChangesOf(rd1);
      final AtomicInteger searches = new AtomicInteger();
      final AtomicInteger heldSearches = new AtomicInteger();
      rd1.setMissingChangesSearchHook(search ->
      {
        if (Thread.holdsLock(pendingChanges))
        {
          heldSearches.incrementAndGet();
        }
        if (searches.incrementAndGet() == 1)
        {
          unchecked(() ->
          {
            replace(dnB, "description", "while the first round searches");
            done.add(rd1.getLastLocalChange());
          }).run();
        }
      });

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
      assertThat(heldSearches.get()).as("the searches run while the local changes are held back").isZero();
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A recovery which kept too many of the local changes done while it runs keeps none, and searches
   * for them in the historical information instead, from where it has got to: none is lost, and the
   * recovery ends.
   */
  @Test
  public void aRecoveryWhichKeptTooManyChangesSearchesForThemAndLosesNone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      pendingChangesOf(rd1).setMaxKeptChanges(2);
      startRecovery(rd1);
      // Each change of other values than the ones before, which the historical information keeps.
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "kept");
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(dnB);
      done.add(rd1.getLastLocalChange());
      replace(dnC, "description", "one too many to keep");
      done.add(rd1.getLastLocalChange());
      replace(dnA, "displayName", "searched for");
      done.add(rd1.getLastLocalChange());

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * Local changes go on while a recovery which keeps them runs. The recovery publishes the ones it
   * kept, holds the local changes back only to publish the ones pushed while it published the others,
   * and ends; each change is published once, by the recovery or, once it has ended, by the broker.
   */
  @Test
  public void aRecoveryWhichKeepsTheLocalChangesEndsWhileTheyGoOnAndLosesNone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    final ExecutorService writer = Executors.newSingleThreadExecutor();
    try
    {
      final List<DN> dns = new ArrayList<>();
      for (int i = 0; i < 4 * MAX_ROUNDS_UNDER_LOAD; i++)
      {
        dns.add(addPerson("load" + i));
      }
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1);
      final List<CSN> done = new CopyOnWriteArrayList<>();
      final Future<?> changes = writer.submit(unchecked(() ->
      {
        for (int i = 0; i < dns.size(); i++)
        {
          replace(dns.get(i), "description", "change " + i);
          done.add(rd1.getLastLocalChange());
        }
      }));
      // Some changes are kept before the recovery begins to publish, and the others are done while it
      // runs: it publishes slowly enough for them to.
      new TestTimer.Builder()
          .maxSleep(10, SECONDS)
          .sleepTimes(10, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(done.size()).isGreaterThanOrEqualTo(MAX_ROUNDS_UNDER_LOAD / 4));

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)
      {
        @Override
        public boolean publishRecovery(ReplicationMsg msg, Session session)
        {
          unchecked(() -> Thread.sleep(5)).run();
          return super.publishRecovery(msg, session);
        }
      }));
      assertEachChangePublishedOnce(rs, baseDN, rsMaxCSN, published, Collections.<Future<?>> singletonList(changes), done);
    }
    finally
    {
      writer.shutdownNow();
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A session which starts while the missing change publisher of the previous one runs has its
   * replication server get every change of this replica it misses, in order. The publisher went on
   * from where it had got to, on the new session, and the replication server of the new one - which
   * held less than the one before - got the later changes first, and took none of the older ones it
   * missed afterwards (issue #1199).
   */
  @Test
  public void aSessionWhichStartsWhileARecoveryRunsGetsEveryChangeItsReplicationServerMisses()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    final SortedSet<String> towardsRs = new TreeSet<>(replServers);
    final int secondPort = TestCaseUtils.findFreePort();
    final SortedSet<String> towardsSecond = towards(secondPort);
    ReplicationServer second = null;
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    final CountDownLatch held = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    try
    {
      second = createIsolatedReplicationServer(secondPort, "HistoricalCsnOrderingSecond", SECOND_RS_ID);
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");
      final CSN onBoth = aChangeBothReplicationServersHold(rd1, dnA, rs, towardsRs, second, towardsSecond);

      // The replication servers miss the changes done from here on: the first one in the first search
      // window after the change they hold, the second one in the next. The first one is a delete,
      // which the first recovery publishes and the second one has to find kept aside still.
      refuseTheLocalChanges(rd1);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnC);
      done.add(rd1.getLastLocalChange());
      assertTrue(done.get(0).getTime() <= onBoth.getTime() + SEARCH_WINDOW_MS, "the first change is in the first window");
      rd1.getGenerator().adjust(new CSN(onBoth.getTime() + SEARCH_WINDOW_MS + 1000, 0, serverId));
      replace(dnB, "description", "in the second window");
      done.add(rd1.getLastLocalChange());

      // The session started again recovers them, and its publisher is held on the search of its second
      // round, once the first round has published the first change.
      rd1.setMissingChangesSearchHook(onTheSearch(2, search -> holdUntil(held, release).run()));
      rd1.disable();
      rd1.enable();
      assertTrue(held.await(30, SECONDS), "the second round of the recovery searches");
      assertInTheChangelog(rs, baseDN, done.get(0));

      // A session to the second replication server, which misses both, starts while the publisher runs.
      connectTo(rd1, towardsSecond, SECOND_RS_ID);
      release.countDown();
      final ReplicationServer secondRs = second;
      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(secondRs, baseDN, onBoth))
              .as("the changes the second replication server holds after the one it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);
      replace(dnA, "description", "once the recovery is over");
      assertInTheChangelog(second, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      release.countDown();
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs, second);
    }
  }

  /**
   * A session which starts once the missing change publisher of the previous one has ended its
   * recovery, but before the publisher has left, gets a recovery of its own. It found the publisher
   * still there and started none, the publisher then left, and the recovery the session needed was
   * run by nobody: its replication server never got the change it missed, nor any local change done
   * afterwards (issue #1199).
   */
  @Test
  public void aSessionWhichStartsAsThePublisherLeavesGetsARecoveryOfItsOwn() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    final SortedSet<String> towardsRs = new TreeSet<>(replServers);
    final int secondPort = TestCaseUtils.findFreePort();
    final SortedSet<String> towardsSecond = towards(secondPort);
    ReplicationServer second = null;
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    final CountDownLatch held = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    try
    {
      second = createIsolatedReplicationServer(secondPort, "HistoricalCsnOrderingSecond", SECOND_RS_ID);
      final DN dnA = addPerson("a");

      // A change on the second replication server, which holds none of this replica before.
      connectTo(rd1, towardsSecond, SECOND_RS_ID);
      replace(dnA, "description", "on the second replication server");
      final CSN onBoth = rd1.getLastLocalChange();
      assertInTheChangelog(second, baseDN, onBoth);

      // The session back to the first replication server recovers it, and its publisher is held once
      // its run has ended the recovery.
      final AtomicBoolean holdOnce = new AtomicBoolean(true);
      rd1.setMissingChangesPublisherRunHook(() ->
      {
        if (holdOnce.compareAndSet(true, false))
        {
          holdUntil(held, release).run();
        }
      });
      connectTo(rd1, towardsRs, FIRST_RS_ID);
      assertTrue(held.await(30, SECONDS), "the publisher has run");
      assertInTheChangelog(rs, baseDN, onBoth);
      assertFalse(pendingChangesOf(rd1).isRecovering(), "the recovery is over");
      replace(dnA, "description", "once the recovery is over");
      final CSN missed = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, missed);

      // A session to the second replication server, which misses that change, starts before the
      // publisher has left.
      connectTo(rd1, towardsSecond, SECOND_RS_ID);
      release.countDown();
      assertInTheChangelog(second, baseDN, missed);
      waitUntilTheRecoveryIsOver(rd1);
      replace(dnA, "description", "once the second recovery is over");
      assertInTheChangelog(second, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      release.countDown();
      rd1.setMissingChangesPublisherRunHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs, second);
    }
  }

  /**
   * A recovery is not ended while a session which started meanwhile needs it. The publisher had
   * nothing left to publish on the session before, ended the recovery, and the local changes went
   * out to the replication server of the new session ahead of the ones it missed, which it then
   * took none of (issue #1199).
   */
  @Test
  public void aRecoveryASessionWhichStartedMeanwhileNeedsIsNotEnded() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    final SortedSet<String> towardsRs = new TreeSet<>(replServers);
    final int secondPort = TestCaseUtils.findFreePort();
    final SortedSet<String> towardsSecond = towards(secondPort);
    ReplicationServer second = null;
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    final CountDownLatch held = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    try
    {
      second = createIsolatedReplicationServer(secondPort, "HistoricalCsnOrderingSecond", SECOND_RS_ID);
      final DN dnA = addPerson("a");
      final CSN onBoth = aChangeBothReplicationServersHold(rd1, dnA, rs, towardsRs, second, towardsSecond);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "on the first replication server only");
      done.add(rd1.getLastLocalChange());
      assertInTheChangelog(rs, baseDN, done.get(0));

      // As a publisher whose broker refused a change leaves it - one which searches for the local
      // changes. The session started again needs no recovery, and its publisher, which is to end this
      // one, is held on its search, which finds nothing.
      startRecovery(rd1, false);
      brokerOf(rd1).setRecoveryRequired(false);
      rd1.setMissingChangesSearchHook(onTheSearch(1, search -> holdUntil(held, release).run()));
      rd1.disable();
      rd1.enable();
      assertTrue(held.await(30, SECONDS), "the recovery searches");

      // A session to the second replication server, which misses the change, starts meanwhile; a local
      // change is done once the publisher has run on the session before.
      connectTo(rd1, towardsSecond, SECOND_RS_ID);
      final AtomicBoolean changeOnce = new AtomicBoolean(true);
      rd1.setMissingChangesPublisherRunHook(() ->
      {
        if (changeOnce.compareAndSet(true, false))
        {
          unchecked(() ->
          {
            replace(dnA, "displayName", "once the publisher ran on the session before");
            done.add(rd1.getLastLocalChange());
          }).run();
        }
      });
      release.countDown();

      final ReplicationServer secondRs = second;
      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(secondRs, baseDN, onBoth))
              .as("the changes the second replication server holds after the one it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);
    }
    finally
    {
      release.countDown();
      rd1.setMissingChangesSearchHook(null);
      rd1.setMissingChangesPublisherRunHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs, second);
    }
  }

  /**
   * A session which starts while a round of a recovery holds the local changes back to publish the ones
   * the recovery kept has its replication server get them all: the recovery goes on from its start with
   * what it kept. Taking the changes it kept for short of the last local change, as they are when one of
   * them was not kept, the round had the recovery keep none from then on, and the run for the new session
   * searched the historical information for them, which holds nothing of a change whose values a later
   * one replaced.
   */
  @Test
  public void aSessionWhichStartsWhileTheLocalChangesAreHeldBackGetsTheChangesTheRecoveryKept() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    final int secondPort = TestCaseUtils.findFreePort();
    final SortedSet<String> towardsSecond = towards(secondPort);
    // Created before the first one, the second replication server is the one which registers the
    // changelog backend of the server: the first one, which goes away during the test, would take that
    // backend with it, and the second one would fail on every change it is sent from then on. It is in
    // another group than the domain, which goes to the first one, knowing of both, for as long as it is
    // there.
    ReplicationServer second =
        createIsolatedReplicationServer(secondPort, "HistoricalCsnOrderingSecond", SECOND_RS_ID, 2);
    ReplicationServer rs = createReplicationServer();
    final SortedSet<String> towardsRs = new TreeSet<>(replServers);
    final SortedSet<String> towardsBoth = new TreeSet<>(towardsRs);
    towardsBoth.addAll(towardsSecond);
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");
      final CSN onBoth = aChangeBothReplicationServersHold(rd1, dnA, rs, towardsRs, second, towardsSecond);
      connectTo(rd1, towardsBoth, FIRST_RS_ID);

      // Both replication servers miss the changes done from here on. The recovery of the session started
      // again rebuilds the first one from the historical information, and keeps the others: the first
      // round keeps one done while it searches, the round which publishes it keeps two more, of the same
      // values, and the round after it holds the local changes back to publish them.
      refuseTheLocalChanges(rd1);
      final List<CSN> done = new CopyOnWriteArrayList<>();
      replace(dnB, "description", "missed by both");
      done.add(rd1.getLastLocalChange());
      rd1.setMissingChangesSearchHook(once(() ->
      {
        replace(dnC, "description", "kept while the first round searches");
        done.add(rd1.getLastLocalChange());
      }));
      final AtomicBoolean changeOnce = new AtomicBoolean(true);
      final AtomicBoolean startOnce = new AtomicBoolean(true);
      rd1.setKeptChangesPublishedHook(heldBack -> unchecked(() ->
      {
        if (!heldBack && changeOnce.compareAndSet(true, false))
        {
          replace(dnA, "description", "first");
          done.add(rd1.getLastLocalChange());
          replace(dnA, "description", "second");
          done.add(rd1.getLastLocalChange());
        }
        else if (heldBack && startOnce.compareAndSet(true, false))
        {
          // The first replication server goes away once the held round has published the changes to it.
          // The broker starts a session to the second one, which misses them, without the lock the round
          // holds, and the session hands its start over to the publisher before the round ends.
          rs.remove();
          waitUntilAStartIsHandedOver(rd1);
        }
      }).run());
      rd1.disable();
      rd1.enable();

      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(second, baseDN, onBoth))
              .as("the changes the second replication server holds after the one it had")
              .isEqualTo(done));
      assertFalse(startOnce.get(), "the round which held the local changes back saw the start handed over");
      waitUntilTheRecoveryIsOver(rd1);
      replace(dnA, "description", "once the recovery is over");
      assertInTheChangelog(second, baseDN, rd1.getLastLocalChange());
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      rd1.setKeptChangesPublishedHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs, second);
    }
  }

  /**
   * The local changes a recovery kept are not published once the data they were done on has been
   * replaced, as a restore or an import does while the domain is disabled: the replicas would get
   * changes this one does not hold. The recovery searches the data it finds then instead.
   */
  @Test
  public void theLocalChangesARecoveryKeptGoWithTheDataTheyWereDoneOn() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);
      waitUntilTheStateIsSaved(baseDN, rsMaxCSN);
      final List<Entry> backup = newArrayList(
          DirectoryServer.getEntry(baseDN).duplicate(false), DirectoryServer.getEntry(dnA).duplicate(false));

      // The change done then is kept. The broker refuses the announcement that the replica goes
      // offline as well, which would have the replication server hold a CSN past the change.
      startRecovery(rd1);
      replace(dnA, "description", "on the data which is replaced");

      // The data is restored while the domain is disabled, as it was before the change.
      rd1.disable();
      restore(backup);
      rd1.enable();
      waitUntilTheRecoveryIsOver(rd1);

      assertFalse(DirectoryServer.getEntry(dnA).hasAttribute(
          getServerContext().getSchema().getAttributeType("description")), "the change is not in the data");
      // A change published once the recovery is over comes after whatever it published, on the session.
      replace(dnA, "displayName", "once the data was restored");
      final CSN afterTheRestore = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, afterTheRestore);
      assertThat(changelogAfter(rs, baseDN, rsMaxCSN)).as("the changes published once the data was restored")
          .containsExactly(afterTheRestore);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * What the search of a round finds takes no room among the deletes kept aside. It was put in the
   * same capped list, and a local delete done while the round ran dropped the oldest entry of it - a
   * change the search had just found, or a delete the replication server lacked - which nothing
   * published afterwards (issue #1200).
   */
  @Test
  public void theChangesARoundFindsTakeNoRoomAmongTheDeletesKeptAside() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");
      final DN dnD = addPerson("d");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      rd1.setMaxDeletesKeptAside(2);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      replace(dnB, "description", "found by the search");
      done.add(rd1.getLastLocalChange());
      replace(dnC, "description", "found by the search");
      done.add(rd1.getLastLocalChange());
      // Started after the changes, which the recovery then searches for.
      startRecovery(rd1);
      rd1.setMissingChangesSearchHook(once(() ->
      {
        TestCaseUtils.deleteEntry(dnD);
        done.add(rd1.getLastLocalChange());
      }));

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A delete no replication server has taken is not dropped from the deletes kept aside without a
   * trace: nothing publishes it afterwards, and the replicas which miss it keep the entry. One a
   * replication server has taken is dropped quietly (issue #1200).
   */
  @Test
  public void aDeleteNoReplicationServerTookIsNotDroppedWithoutATrace() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final DN dnC = addPerson("c");

      rd1.setMaxDeletesKeptAside(1);
      TestCaseUtils.deleteEntry(dnA);
      final CSN taken = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, taken);

      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      refuseTheLocalChanges(rd1);
      TestCaseUtils.deleteEntry(dnB);
      final CSN notTaken = rd1.getLastLocalChange();
      TestCaseUtils.deleteEntry(dnC);

      final List<String> errors = TestCaseUtils.ERROR_TEXT_WRITER.getMessages();
      assertThat(errors).as("a delete no replication server took, dropped")
          .anyMatch(record -> record.contains(notTaken.toString()) && record.contains(dnB.toString()));
      assertThat(errors).as("a delete a replication server took, dropped")
          .noneMatch(record -> record.contains(taken.toString()));
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A local delete which did not go through is not published by a recovery. It was kept aside as its
   * conflicts were resolved, before the backend refused it, and a recovery published it as a delete
   * which was done: the replicas it reached deleted an entry this one keeps.
   */
  @Test
  public void aDeleteWhichFailedIsNotPublishedByARecovery() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN parent = DN.valueOf("ou=parent," + TEST_ROOT_DN_STRING);
      TestCaseUtils.addEntry(
          "dn: " + parent,
          "objectClass: top",
          "objectClass: organizationalUnit",
          "ou: parent");
      final DN child = DN.valueOf("cn=child," + parent);
      TestCaseUtils.addEntry(
          "dn: " + child,
          "objectClass: top",
          "objectClass: person",
          "cn: child",
          "sn: test");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      assertEquals(InternalClientConnection.getRootConnection().processDelete(parent).getResultCode(),
          ResultCode.NOT_ALLOWED_ON_NONLEAF, "the delete of an entry which has one below it");
      replace(child, "description", "after the delete which failed");
      final CSN change = rd1.getLastLocalChange();
      // Started after the changes, which the recovery then searches for.
      startRecovery(rd1);

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactly(change);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A broker which has a recovery to run refuses the changes of its replica only, which the recovery
   * publishes in order. It refused every other message as well - the acknowledgements of the changes
   * replayed here, the messages of a total update, its monitoring requests - for as long as the
   * recovery ran (issue #1220).
   */
  @Test
  public void aBrokerWithARecoveryToRunRefusesTheChangesOnly() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    // It sends the topology to the domain once, as it starts, and then only when it is asked to.
    ReplicationServer rs = createReplicationServer(MINUTES.toMillis(10));
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);
      refuseTheLocalChanges(rd1);

      // A message which is no change goes through: the monitoring request is answered at once, where
      // the broker waits 10 seconds for an answer before it gives up on it.
      final long asked = System.nanoTime();
      brokerOf(rd1).getReplicaStates();
      assertThat(NANOSECONDS.toMillis(System.nanoTime() - asked)).as("the time the monitoring request took")
          .isLessThan(5000);

      // A change does not, up to the end of the recovery.
      replace(dnA, "description", "while a recovery is required");
      brokerOf(rd1).setRecoveryRequired(false);
      replace(dnA, "description", "once no recovery is required");
      final CSN taken = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, taken);
      assertThat(changelogAfter(rs, baseDN, rsMaxCSN)).as("the changes the replication server holds after the ones it had")
          .containsExactly(taken);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A recovery whose kept changes do not reach the last local change - one which should have been
   * kept is not there - searches for the changes after them, and ends. It went round for ever while
   * it held the local changes back, every local write waiting for it.
   */
  @Test
  public void aRecoveryWhoseKeptChangesMissOneSearchesForIt() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "kept");
      done.add(rd1.getLastLocalChange());
      replace(dnB, "description", "not kept");
      done.add(rd1.getLastLocalChange());
      pendingChangesOf(rd1).dropKeptChanges(Collections.singletonList(done.get(1)));

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The local changes a recovery kept outlive a domain disabled and enabled back on its own data: the
   * state it saved on its way down covers them, and the change whose values a later one replaced is
   * published still.
   */
  @Test
  public void theLocalChangesARecoveryKeptOutliveADisable() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);

      // The broker refuses the announcement that the replica goes offline as well, which would have
      // the replication server hold a CSN past the changes.
      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "first");
      done.add(rd1.getLastLocalChange());
      replace(dnA, "description", "second");
      done.add(rd1.getLastLocalChange());

      rd1.disable();
      rd1.enable();
      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(rs, baseDN, rsMaxCSN))
              .as("the changes the replication server holds after the ones it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The local changes a recovery kept outlive a domain disabled and enabled back on its own data whose
   * save on its way down did not write the state - a save gives its turn up to the one in flight, and a
   * write may fail: the state read back is brought up to the changes the historical information of the
   * entries holds, so it covers them, and they are not taken for changes the data does not reflect.
   */
  @Test
  public void theLocalChangesARecoveryKeptOutliveADisableWhoseSaveDidNotWrite() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);

      // The broker refuses the announcement that the replica goes offline as well, which would have
      // the replication server hold a CSN past the changes.
      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "first");
      done.add(rd1.getLastLocalChange());
      replace(dnA, "description", "second");
      done.add(rd1.getLastLocalChange());

      // The base entry holds the state saved before the changes.
      rd1.disable();
      leaveTheSavedState(baseDN, rsMaxCSN);
      rd1.enable();
      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(rs, baseDN, rsMaxCSN))
              .as("the changes the replication server holds after the ones it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A suffix deleted and added back while a recovery runs outlives a domain disabled and enabled back
   * before the state could be saved into the base entry added back. The state read back held no change
   * of this replica, and the state is brought up to the changes of this replica the historical
   * information holds only past the newest one it read: the changes the recovery kept were all taken
   * for changes the data does not reflect, the add of the base entry was dropped, and the recovery
   * published the deletes which removed the suffix alone - the other replicas lost it.
   */
  @Test
  public void aSuffixAddedBackDuringARecoveryOutlivesAStateWhichCouldNotBeSavedInIt() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);

      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(baseDN);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.addEntry(StaticUtils.createEntry(baseDN));
      done.add(rd1.getLastLocalChange());

      // The base entry added back holds no state: none was saved into it.
      rd1.disable();
      leaveTheSavedState(baseDN);
      rd1.enable();
      new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(rs, baseDN, rsMaxCSN))
              .as("the changes the replication server holds after the ones it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A delete kept aside goes with the data it was done on: once the data is restored from a backup
   * taken before it, the entry is back, and a recovery which published the delete would have the
   * other replicas delete an entry this one holds (issue #1221).
   */
  @Test
  public void theDeletesKeptAsideGoWithTheDataTheyWereDoneOn() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final DN dnB = addPerson("b");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);
      waitUntilTheStateIsSaved(baseDN, rsMaxCSN);
      final List<Entry> backup = newArrayList(DirectoryServer.getEntry(baseDN).duplicate(false),
          DirectoryServer.getEntry(dnA).duplicate(false), DirectoryServer.getEntry(dnB).duplicate(false));
      TestCaseUtils.deleteEntry(dnB);
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());

      // The data is restored while the domain is disabled, as it was before the delete.
      rd1.disable();
      restore(backup);
      rd1.enable();
      assertNotNull(DirectoryServer.getEntry(dnB), "the entry is back");

      // A replication server which misses the changes after the backup: its recovery publishes the
      // change done since, and not the delete.
      replace(dnA, "description", "once the data was restored");
      final CSN afterTheRestore = rd1.getLastLocalChange();
      startRecovery(rd1);
      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactly(afterTheRestore);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A round whose search answers that the base entry is not there, while it is, has not gone
   * through: the base DN had no backend for the search - one an import took away - and the round
   * found nothing of what is there. Taken for a search which found everything, it moved the recovery
   * past the changes of its window, which no later session published.
   */
  @Test
  public void aSearchWhichFindsNoBackendFailsItsRound() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "while the backend is away");
      done.add(rd1.getLastLocalChange());
      // Started after the change, which the recovery then searches for.
      startRecovery(rd1);

      rd1.setMissingChangesSearchHook(onTheFirstSearch(search -> search.setResultCode(ResultCode.NO_SUCH_OBJECT)));
      final List<ReplicationMsg> published = new ArrayList<>();
      Assertions.assertThatThrownBy(() -> rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)))
          .as("a recovery whose search found no backend")
          .hasMessageContaining(ResultCode.NO_SUCH_OBJECT.getName().toString());
      assertThat(published).as("what a round whose search found no backend published").isEmpty();

      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A recovery which keeps failing waits longer before each session restart - one second more for
   * each failure in a row - and forgets its failures once it succeeds. A publisher which restarted
   * the session at once would keep the session restarting for as long as the backend fails, and one
   * which kept its count would start the next failure with the longest wait.
   */
  @Test
  public void aRecoveryWhichKeepsFailingWaitsLongerEachTimeAndForgetsItOnceItSucceeds() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);

      // The replication server misses the change done from here on.
      refuseTheLocalChanges(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "missed by the replication server");
      done.add(rd1.getLastLocalChange());

      // The search of the recovery fails twice in a row, each time on a session of its own.
      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      final List<Long> searches = new CopyOnWriteArrayList<>();
      rd1.setMissingChangesSearchHook(search ->
      {
        searches.add(System.nanoTime());
        if (searches.size() <= 2)
        {
          search.setResultCode(ResultCode.UNAVAILABLE);
        }
      });
      rd1.disable();
      rd1.enable();

      new TestTimer.Builder()
          .maxSleep(60, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer()
          .repeatUntilSuccess(() -> assertThat(changelogAfter(rs, baseDN, rsMaxCSN))
              .as("the changes the replication server holds after the ones it had")
              .isEqualTo(done));
      waitUntilTheRecoveryIsOver(rd1);

      final List<String> errors = TestCaseUtils.ERROR_TEXT_WRITER.getMessages();
      assertThat(errors).as("the first failure")
          .anyMatch(record -> record.contains("tries again within 1000 ms"));
      assertThat(errors).as("the second failure in a row")
          .anyMatch(record -> record.contains("tries again within 2000 ms"));
      assertThat(searches.size()).as("the searches of the recovery").isGreaterThanOrEqualTo(3);
      assertThat(NANOSECONDS.toMillis(searches.get(2) - searches.get(1)))
          .as("the time from the second failure to the next search").isGreaterThanOrEqualTo(2000);
      assertEquals(consecutiveRecoveryFailuresOf(rd1), 0, "the failures in a row, once the recovery succeeded");
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A recovery which threw an error - which no catch of an exception takes - has the session started
   * again as well. Its publisher left with the recovery on and asked for nothing: the local changes
   * were left to a recovery nobody ran.
   */
  @Test
  public void aRecoveryWhichThrewAnErrorHasTheSessionStartedAgain() throws Exception
  {
    assertARecoveryWhichFailedHasTheSessionStartedAgain(search ->
    {
      throw new AssertionError("the round threw an error, as a test asked");
    });
  }

  /**
   * The replica is not announced offline while a recovery has changes to publish. The announcement
   * carries the newest CSN of this replica, and the replication server takes the replica for offline
   * from there: its changelog index would have gone past the changes which reach it afterwards.
   */
  @Test
  public void theReplicaIsNotAnnouncedOfflineWhileARecoveryHasChangesToPublish() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());

      // As a publisher whose broker refused a change leaves it: the broker would take the announcement.
      startRecovery(rd1);
      brokerOf(rd1).setRecoveryRequired(false);
      replace(dnA, "description", "left to the recovery");

      assertNull(pendingChangesOf(rd1).putReplicaOfflineMsg(), "the announcement that the replica goes offline");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A session which starts while a failed recovery waits to have the session started again has its
   * changes recovered at once: it is the next session the recovery waited for. Its start was handed
   * over and left there until the wait was over - up to ten seconds - while the local changes piled
   * up for the recovery.
   */
  @Test
  public void aSessionWhichStartsWhileAFailedRecoveryWaitsIsRecoveredAtOnce() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    final SortedSet<String> towardsRs = new TreeSet<>(replServers);
    final int secondPort = TestCaseUtils.findFreePort();
    final SortedSet<String> towardsSecond = towards(secondPort);
    ReplicationServer second = null;
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      second = createIsolatedReplicationServer(secondPort, "HistoricalCsnOrderingSecond", SECOND_RS_ID);
      final DN dnA = addPerson("a");
      aChangeBothReplicationServersHold(rd1, dnA, rs, towardsRs, second, towardsSecond);

      // The replication servers miss the change done from here on.
      refuseTheLocalChanges(rd1);
      replace(dnA, "description", "missed by both replication servers");
      final CSN missed = rd1.getLastLocalChange();

      // The recovery of the session started again fails, after nine failures in a row: it waits ten
      // seconds to have the session started again.
      setConsecutiveRecoveryFailures(rd1, 9);
      final CountDownLatch failed = new CountDownLatch(1);
      rd1.setMissingChangesSearchHook(onTheFirstSearch(search ->
      {
        search.setResultCode(ResultCode.UNAVAILABLE);
        failed.countDown();
      }));
      rd1.disable();
      rd1.enable();
      assertTrue(failed.await(30, SECONDS), "the recovery failed");

      // A session to the second replication server starts while the recovery waits.
      final long started = System.nanoTime();
      connectTo(rd1, towardsSecond, SECOND_RS_ID);
      assertInTheChangelog(second, baseDN, missed);
      assertThat(NANOSECONDS.toMillis(System.nanoTime() - started))
          .as("the time the session took to get the change it missed").isLessThan(5000);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs, second);
    }
  }

  /**
   * The deletes of a suffix outlive a ServerState which could not be saved: the delete of the base
   * entry took the place the state is saved in with it, and the state read back is older than the
   * deletes. They are in the data all the same - their entries are gone - and a recovery publishes
   * them, where taking them for changes the data does not reflect dropped them, and the replicas kept
   * the suffix.
   */
  @Test
  public void theDeletesOfASuffixOutliveAStateWhichCouldNotBeSaved() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      assertInTheChangelog(rs, baseDN, rsMaxCSN);

      // The replication server misses the deletes.
      refuseTheLocalChanges(rd1);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(baseDN);
      done.add(rd1.getLastLocalChange());

      rd1.disable();
      rd1.enable();
      final CSN readBack = rd1.getLastLocalChange();
      assertTrue(readBack == null || readBack.isOlderThan(done.get(0)), "the state read back is older than the deletes");

      TestCaseUtils.addEntry(StaticUtils.createEntry(baseDN));
      done.add(rd1.getLastLocalChange());
      // Started after the changes, which the recovery then searches for.
      startRecovery(rd1);
      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * The deletes of a suffix a recovery kept outlive a ServerState which could not be saved as well:
   * their entries are gone, so the data reflects them.
   */
  @Test
  public void theDeletesOfASuffixARecoveryKeptOutliveAStateWhichCouldNotBeSaved() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(baseDN);
      done.add(rd1.getLastLocalChange());
      // No recovery runs as the domain is enabled back: what it kept is read as the domain leaves it.
      pendingChangesOf(rd1).setRecovering(false);
      brokerOf(rd1).setRecoveryRequired(false);

      rd1.disable();
      rd1.enable();
      final CSN readBack = rd1.getLastLocalChange();
      assertTrue(readBack == null || readBack.isOlderThan(done.get(0)), "the state read back is older than the deletes");

      assertThat(csns(new ArrayList<ReplicationMsg>(pendingChangesOf(rd1).keptChangesNewerThan(null))))
          .as("the deletes the recovery kept").containsExactlyElementsOf(done);
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A replica which reads back a ServerState with no change of its own numbers its next change after
   * the ones it numbered before (issue #1223). The CSN generator went back to the clock, and the
   * session started next took it up to what its replication server holds only: past a change of a
   * replica whose clock runs ahead, which this one replayed, and past the last change of this one the
   * replication server took. The deletes it missed came after that change, and the next change was
   * numbered as the first of them - a recovery would publish one of the two.
   */
  @Test
  public void aReplicaWhichReadsBackAStateWithNoChangeOfItsOwnNumbersItsNextChangeAfterItsLastOne()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    ReplicationBroker other = null;
    try
    {
      final DN dnA = addPerson("a");
      assertInTheChangelog(rs, baseDN, rd1.getLastLocalChange());

      // The changes of a replica whose clock runs an hour ahead have the generator follow it.
      other = openReplicationSession(baseDN, OTHER_SERVER_ID, 100, rsPort, 1000);
      final CSN otherCSN = new CSNGenerator(OTHER_SERVER_ID, TimeThread.getTime() + HOURS.toMillis(1)).newCSN();
      publishOtherReplicaChange(other, otherCSN, dnA, "from a replica whose clock runs ahead");
      waitUntilReplayed(rd1, otherCSN);

      // The replication server misses the deletes, and the delete of the base entry takes the saved
      // state with it.
      refuseTheLocalChanges(rd1);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(baseDN);
      done.add(rd1.getLastLocalChange());

      rd1.disable();
      rd1.enable();
      final CSN readBack = rd1.getLastLocalChange();
      assertTrue(readBack == null || readBack.isOlderThan(done.get(0)), "the state read back is older than the deletes");

      TestCaseUtils.addEntry(StaticUtils.createEntry(baseDN));
      final CSN next = rd1.getLastLocalChange();
      assertTrue(next.isNewerThan(done.get(1)), "the next change " + next + " is numbered after the deletes " + done);
    }
    finally
    {
      stop(other);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A local change an assured domain does during a recovery is published by it as not assured, as a
   * recovery publishes what it rebuilds: its operation, which may still be waiting for the
   * acknowledgement, gets none and times out.
   */
  @Test
  public void aKeptChangeIsPublishedNotAssured() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = MultimasterReplication.createNewDomain(
        new DomainFakeCfg(baseDN, serverId, replServers, AssuredType.SAFE_DATA, 1, 1, 500, null));
    rd1.start();
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();

      startRecovery(rd1);
      replace(dnA, "description", "assured");

      final List<ReplicationMsg> published = new ArrayList<>();
      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(published).hasSize(1);
      final UpdateMsg kept = (UpdateMsg) published.get(0);
      assertFalse(kept.isAssured(), "the kept change is assured");
      final short version = ProtocolVersion.getCurrentVersion();
      assertFalse(((UpdateMsg) ReplicationMsg.generateMsg(kept.getBytes(version), version)).isAssured(),
          "the kept change is assured as it is encoded");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * A recovery publishes nothing while a total update replaces the data of this replica: what it
   * would publish was done on the data the import replaces. The session started again after the
   * import recovers from the state the new data holds.
   */
  @Test
  public void aRecoveryPublishesNothingWhileATotalUpdateReplacesTheData() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    ReplicationServer rs = createReplicationServer();
    LDAPReplicationDomain rd1 = createReplicationDomain(serverId);
    try
    {
      final DN dnA = addPerson("a");
      final CSN rsMaxCSN = rd1.getLastLocalChange();
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "searched for");
      done.add(rd1.getLastLocalChange());
      // Started after the change, which the recovery then searches for, and before the next one, which
      // it keeps.
      startRecovery(rd1);
      replace(dnA, "displayName", "kept");
      done.add(rd1.getLastLocalChange());
      final List<ReplicationMsg> published = new ArrayList<>();

      // A total update which runs as the recovery starts: the recovery searches nothing, and publishes
      // nothing.
      final AtomicInteger searches = new AtomicInteger();
      rd1.setMissingChangesSearchHook(search -> searches.incrementAndGet());
      setImportingData(rd1, true);
      try
      {
        assertFalse(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)),
            "a recovery while a total update replaces the data");
        assertThat(published).as("what a recovery published while a total update replaces the data").isEmpty();
        assertThat(searches.get()).as("the searches of a recovery while a total update replaces the data").isZero();
      }
      finally
      {
        setImportingData(rd1, false);
      }

      // One which starts while a round searches: the round publishes nothing of what it found.
      rd1.setMissingChangesSearchHook(onTheFirstSearch(search -> unchecked(() -> setImportingData(rd1, true)).run()));
      try
      {
        assertFalse(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)),
            "a recovery during which a total update began");
        assertThat(published).as("what a round published once a total update began").isEmpty();
      }
      finally
      {
        setImportingData(rd1, false);
        rd1.setMissingChangesSearchHook(null);
      }

      assertTrue(rd1.buildAndPublishMissingChanges(rsMaxCSN, new TestBroker(published)));
      assertThat(csns(published)).containsExactlyElementsOf(done);
    }
    finally
    {
      rd1.setMissingChangesSearchHook(null);
      MultimasterReplication.deleteDomain(baseDN);
      remove(rs);
    }
  }

  /**
   * Does a local change on the writer, and waits for it for a while: a change the last round holds
   * back is done once the recovery is over.
   */
  private static void doLocalChange(ExecutorService writer, List<Future<?>> changes, List<CSN> done,
      LDAPReplicationDomain domain, DN dn, int round)
  {
    final Future<?> change = writer.submit(unchecked(() ->
    {
      replace(dn, "description", "done while round " + round + " searches");
      done.add(domain.getLastLocalChange());
    }));
    changes.add(change);
    try
    {
      change.get(500, MILLISECONDS);
    }
    catch (TimeoutException heldBackByTheLastRound)
    {
      // it is done once the recovery is over
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * Asserts that each local change was published once: the first ones by the recovery, in order,
   * and the others by the broker once the recovery was over - and none of the first ones by the
   * broker as well.
   * <p>
   * The changelog of the replication server holds the changes the broker published, the recovery
   * publishing to a broker of the test: after the last change it had, it has to hold the others,
   * and nothing else. One session delivers in order, so a change of the recovery the broker
   * published as well reaches it before the others and keeps the two from ever matching.
   */
  private static void assertEachChangePublishedOnce(ReplicationServer rs, DN baseDN, CSN rsMaxCSN,
      List<ReplicationMsg> published, List<Future<?>> changes, List<CSN> done) throws Exception
  {
    for (Future<?> change : changes)
    {
      change.get(10, SECONDS);
    }
    final List<CSN> recovered = csns(published);
    assertThat(recovered.size()).isLessThanOrEqualTo(done.size());
    assertThat(done.subList(0, recovered.size())).isEqualTo(recovered);
    final List<CSN> afterTheRecovery = done.subList(recovered.size(), done.size());
    new TestTimer.Builder()
        .maxSleep(10, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() -> assertThat(changelogAfter(rs, baseDN, rsMaxCSN))
            .as("the changes the broker published after the recovery")
            .isEqualTo(afterTheRecovery));
  }

  /**
   * Returns the changes of the replica of the provided CSN which the replication server holds after
   * it. The announcements that the replica went offline, which each stop of its broker publishes,
   * are no changes, and are left out.
   */
  private static List<CSN> changelogAfter(ReplicationServer rs, DN baseDN, CSN csn) throws Exception
  {
    final List<CSN> csns = new ArrayList<>();
    try (DBCursor<UpdateMsg> cursor = rs.getChangelogDB().getReplicationDomainDB().getCursorFrom(
        baseDN, csn.getServerId(), csn, new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY)))
    {
      while (cursor.next())
      {
        if (!(cursor.getRecord() instanceof ReplicaOfflineMsg))
        {
          csns.add(cursor.getRecord().getCSN());
        }
      }
    }
    return csns;
  }

  private static DN addPerson(String cn) throws Exception
  {
    final DN dn = DN.valueOf("cn=" + cn + "," + TEST_ROOT_DN_STRING);
    TestCaseUtils.addEntry(
        "dn: " + dn,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: " + cn,
        "sn: test");
    return dn;
  }

  private static void replace(DN dn, String attribute, String value) throws Exception
  {
    assertEquals(TestCaseUtils.applyModifications(false,
        "dn: " + dn,
        "changetype: modify",
        "replace: " + attribute,
        attribute + ": " + value), 0);
  }

  /** Has the other replica change the description of the entry, with the provided CSN. */
  private void publishOtherReplicaChange(ReplicationBroker other, CSN csn, DN dn, String description)
      throws Exception
  {
    final AttributeType type = getServerContext().getSchema().getAttributeType("description");
    other.publish(new ModifyMsg(csn, dn,
        newArrayList(new Modification(ModificationType.REPLACE, Attributes.create(type, description))),
        getEntryUUID(dn)));
  }

  /**
   * Puts the domain where a session start leaves it when its replication server misses some of
   * its changes, without the thread which would publish them: the tests call
   * {@link LDAPReplicationDomain#buildAndPublishMissingChanges(CSN, ReplicationBroker)} in its
   * place. The pending changes leave the local changes to the recovery, which keeps them, and the
   * broker refuses them. The changes done before are the ones the recovery rebuilds from the
   * historical information.
   */
  private static void startRecovery(LDAPReplicationDomain domain) throws Exception
  {
    startRecovery(domain, true);
  }

  /**
   * Puts the domain where a session start leaves it when its replication server misses some of
   * its changes, as {@link #startRecovery(LDAPReplicationDomain)} does, with a recovery which keeps
   * the local changes done while it runs, or - as one which kept too many of them - keeps none, and
   * searches for them in the historical information as well.
   */
  private static void startRecovery(LDAPReplicationDomain domain, boolean keepsTheLocalChanges) throws Exception
  {
    if (!keepsTheLocalChanges)
    {
      pendingChangesOf(domain).setMaxKeptChanges(0);
    }
    pendingChangesOf(domain).setRecovering(true);
    brokerOf(domain).setRecoveryRequired(true);
  }

  /**
   * Has the broker refuse the local changes, as it does from the start of a session whose replication
   * server misses some, up to the pending changes leaving them to the recovery: they are recorded in
   * the domain state only, and the replication server misses them. The recovery the next session
   * starts rebuilds them from the historical information.
   * <p>
   * Once the missing change publisher of an earlier session has left, under the lock it leaves under:
   * it has the broker take the local changes again as it leaves, after the end of its recovery - which
   * is all {@link #waitUntilTheRecoveryIsOver(LDAPReplicationDomain)} waits for - and the replication
   * server would get the changes the test has it miss.
   */
  private static void refuseTheLocalChanges(final LDAPReplicationDomain domain) throws Exception
  {
    final Field recoveryLockField = LDAPReplicationDomain.class.getDeclaredField("recoveryLock");
    recoveryLockField.setAccessible(true);
    final Object recoveryLock = recoveryLockField.get(domain);
    final Field publisherField = LDAPReplicationDomain.class.getDeclaredField("rsUpdater");
    publisherField.setAccessible(true);
    new TestTimer.Builder()
        .maxSleep(30, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() ->
        {
          synchronized (recoveryLock)
          {
            assertNull(publisherField.get(domain), "the missing change publisher of an earlier session has left");
            brokerOf(domain).setRecoveryRequired(true);
          }
        });
  }

  private static PendingChanges pendingChangesOf(LDAPReplicationDomain domain) throws Exception
  {
    final Field pendingChanges = LDAPReplicationDomain.class.getDeclaredField("pendingChanges");
    pendingChanges.setAccessible(true);
    return (PendingChanges) pendingChanges.get(domain);
  }

  private static ReplicationBroker brokerOf(ReplicationDomain domain) throws Exception
  {
    return (ReplicationBroker) brokerField().get(domain);
  }

  private static void setBroker(ReplicationDomain domain, ReplicationBroker broker) throws Exception
  {
    brokerField().set(domain, broker);
  }

  private static Field brokerField() throws Exception
  {
    final Field broker = ReplicationDomain.class.getDeclaredField("broker");
    broker.setAccessible(true);
    return broker;
  }

  private static List<CSN> csns(List<ReplicationMsg> msgs)
  {
    final List<CSN> csns = new ArrayList<>();
    for (ReplicationMsg msg : msgs)
    {
      csns.add(((UpdateMsg) msg).getCSN());
    }
    return csns;
  }

  private static void waitUntilReplayed(final LDAPReplicationDomain domain, final CSN csn) throws Exception
  {
    new TestTimer.Builder()
        .maxSleep(10, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() -> assertTrue(domain.getServerState().cover(csn), csn + " replayed"));
  }

  /**
   * Asserts that the change is in the changelog of the replication server. Its state would not
   * do: it holds the newest change of each replica, which covers a change the replication server
   * never received as soon as a later one reached it.
   */
  private static void assertInTheChangelog(final ReplicationServer rs, final DN baseDN, final CSN csn)
      throws Exception
  {
    new TestTimer.Builder()
        .maxSleep(10, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() ->
        {
          try (DBCursor<UpdateMsg> cursor = rs.getChangelogDB().getReplicationDomainDB().getCursorFrom(
              baseDN, csn.getServerId(), csn, new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY)))
          {
            assertTrue(cursor.next() && csn.equals(cursor.getRecord().getCSN()),
                csn + " is in the changelog of the replication server");
          }
        });
  }

  /** A step of a test which may throw, for where only a {@link Runnable} is taken. */
  private interface Step
  {
    void run() throws Exception;
  }

  private static Runnable unchecked(final Step step)
  {
    return () ->
    {
      try
      {
        step.run();
      }
      catch (Exception e)
      {
        throw new RuntimeException(e);
      }
    };
  }

  /** Runs the step the first time only: the rounds after the first are left alone. */
  private static Consumer<InternalSearchOperation> once(final Step step)
  {
    final Runnable run = unchecked(step);
    return onTheFirstSearch(search -> run.run());
  }

  /** Runs the step on the search of the first round only: the rounds after it are left alone. */
  private static Consumer<InternalSearchOperation> onTheFirstSearch(final Consumer<InternalSearchOperation> step)
  {
    final AtomicBoolean ran = new AtomicBoolean();
    return search ->
    {
      if (ran.compareAndSet(false, true))
      {
        step.accept(search);
      }
    };
  }

  private ReplicationServer createReplicationServer() throws Exception
  {
    return createReplicationServer(null);
  }

  /**
   * Creates the replication server of the tests, whose monitoring publisher has the provided period,
   * or the default one if it is {@code null}.
   */
  private ReplicationServer createReplicationServer(Long monitoringPeriod) throws Exception
  {
    rsPort = TestCaseUtils.findFreePort();
    replServers.add("localhost:" + rsPort);

    final ReplServerFakeConfiguration configuration =
        new ReplServerFakeConfiguration(rsPort, "HistoricalCsnOrdering", 0, FIRST_RS_ID, 0, 100, replServers, 1, 1000, 5000);
    if (monitoringPeriod != null)
    {
      configuration.setMonitoringPeriod(monitoringPeriod);
    }
    ReplicationServer replicationServer = new ReplicationServer(configuration);
    clearChangelogDB(replicationServer);
    return replicationServer;
  }

  private LDAPReplicationDomain createReplicationDomain(int dsId)
          throws DirectoryException, ConfigException
  {
    LDAPReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConfiguration(dsId, replServers));
    replicationDomain.start();
    return replicationDomain;
  }

  /** Returns the configuration of the domain of the tests, connected to the provided replication servers. */
  private static DomainFakeCfg domainConfiguration(int dsId, SortedSet<String> servers)
  {
    return new DomainFakeCfg(
        DN.valueOf(TEST_ROOT_DN_STRING), dsId, servers, AssuredType.NOT_ASSURED, 2, 1, 0, null);
  }

  /** Returns the configured replication servers of a domain connected to the one listening on the port only. */
  private static SortedSet<String> towards(int port)
  {
    return new TreeSet<>(Collections.singleton("localhost:" + port));
  }

  /**
   * Creates a replication server which knows of no other: what it holds of the domain of the tests,
   * it holds from the domain alone.
   */
  private ReplicationServer createIsolatedReplicationServer(int port, String dirName, int rsServerId)
      throws Exception
  {
    return createIsolatedReplicationServer(port, dirName, rsServerId, 1);
  }

  /** Creates a replication server which knows of no other, in the provided group. */
  private ReplicationServer createIsolatedReplicationServer(int port, String dirName, int rsServerId, int groupId)
      throws Exception
  {
    final ReplicationServer replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        port, dirName, 0, rsServerId, 0, 100, towards(port), groupId, 1000, 5000));
    clearChangelogDB(replicationServer);
    return replicationServer;
  }

  /** Has the domain connect to the provided replication servers, and waits for its session to the one expected. */
  private void connectTo(final LDAPReplicationDomain domain, SortedSet<String> servers, final int rsServerId)
      throws Exception
  {
    assertEquals(domain.applyConfigurationChange(domainConfiguration(serverId, servers)).getResultCode(),
        ResultCode.SUCCESS);
    new TestTimer.Builder()
        .maxSleep(30, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() ->
        {
          assertTrue(domain.isConnected(), "the domain has a session");
          assertEquals(domain.getRsServerId(), rsServerId, "the replication server of the session");
        });
  }

  /**
   * Has the domain do a change on the second replication server, which holds no change of this
   * replica before, and connect back to the first one, which the recovery of that session publishes
   * the change to. Both hold it then, as the newest change of this replica.
   *
   * @return the change
   */
  private CSN aChangeBothReplicationServersHold(LDAPReplicationDomain domain, DN dn, ReplicationServer first,
      SortedSet<String> towardsFirst, ReplicationServer second, SortedSet<String> towardsSecond) throws Exception
  {
    final DN baseDN = domain.getBaseDN();
    connectTo(domain, towardsSecond, SECOND_RS_ID);
    replace(dn, "description", "on both replication servers");
    final CSN csn = domain.getLastLocalChange();
    assertInTheChangelog(second, baseDN, csn);
    connectTo(domain, towardsFirst, FIRST_RS_ID);
    assertInTheChangelog(first, baseDN, csn);
    waitUntilTheRecoveryIsOver(domain);
    return csn;
  }

  /** Runs the step on the search of the provided round only: the rounds before and after it are left alone. */
  private static Consumer<InternalSearchOperation> onTheSearch(final int round,
      final Consumer<InternalSearchOperation> step)
  {
    final AtomicInteger searches = new AtomicInteger();
    return search ->
    {
      if (searches.incrementAndGet() == round)
      {
        step.accept(search);
      }
    };
  }

  /** Returns what tells that a thread is held, and holds it until it is released, for a while at most. */
  private static Runnable holdUntil(final CountDownLatch held, final CountDownLatch release)
  {
    return unchecked(() ->
    {
      held.countDown();
      assertTrue(release.await(30, SECONDS), "released");
    });
  }

  /**
   * Waits for the state checkpointer to have saved, in the base entry, a state which covers the
   * provided change: a backup taken before holds no state of this replica, where the tests restore one
   * which tells the changes the data reflects from the ones it does not.
   */
  private static void waitUntilTheStateIsSaved(final DN baseDN, final CSN csn) throws Exception
  {
    final AttributeType stateType = getServerContext().getSchema().getAttributeType("ds-sync-state");
    new TestTimer.Builder()
        .maxSleep(10, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() ->
        {
          boolean saved = false;
          for (Attribute attribute : DirectoryServer.getEntry(baseDN).getAllAttributes(stateType))
          {
            for (ByteString value : attribute)
            {
              final CSN savedCSN = new CSN(value.toString());
              saved |= savedCSN.getServerId() == csn.getServerId() && savedCSN.isNewerThanOrEqualTo(csn);
            }
          }
          assertTrue(saved, csn + " is in the state saved in the base entry");
        });
  }

  /**
   * Has the base entry hold the provided ServerState - none if no CSN is provided - as a save of the
   * state which did not write leaves an older one there.
   */
  private static void leaveTheSavedState(DN baseDN, CSN... csns) throws Exception
  {
    final MemoryBackend backend =
        (MemoryBackend) getServerContext().getBackendConfigManager().getLocalBackendById(TEST_BACKEND_ID);
    final AttributeType stateType = getServerContext().getSchema().getAttributeType("ds-sync-state");
    final Entry saved = backend.getEntry(baseDN);
    final Entry left = saved.duplicate(false);
    left.removeAttribute(stateType);
    if (csns.length > 0)
    {
      final AttributeBuilder state = new AttributeBuilder(stateType);
      for (CSN csn : csns)
      {
        state.add(csn.toString());
      }
      left.putAttribute(stateType, newArrayList(state.toAttribute()));
    }
    backend.replaceEntry(saved, left, null);
  }

  /** Replaces the data of the test backend with the provided entries, as a restore does. */
  private static void restore(List<Entry> backup) throws Exception
  {
    final MemoryBackend backend =
        (MemoryBackend) getServerContext().getBackendConfigManager().getLocalBackendById(TEST_BACKEND_ID);
    backend.clearMemoryBackend();
    for (Entry entry : backup)
    {
      backend.addEntry(entry, null);
    }
  }

  private static int consecutiveRecoveryFailuresOf(LDAPReplicationDomain domain) throws Exception
  {
    final Field failures = LDAPReplicationDomain.class.getDeclaredField("consecutiveRecoveryFailures");
    failures.setAccessible(true);
    return ((AtomicInteger) failures.get(domain)).get();
  }

  private static void setConsecutiveRecoveryFailures(LDAPReplicationDomain domain, int failures) throws Exception
  {
    final Field field = LDAPReplicationDomain.class.getDeclaredField("consecutiveRecoveryFailures");
    field.setAccessible(true);
    ((AtomicInteger) field.get(domain)).set(failures);
  }

  /** Has the domain behave as while a total update replaces its data, or no longer. */
  private static void setImportingData(LDAPReplicationDomain domain, boolean importing) throws Exception
  {
    final Field field = LDAPReplicationDomain.class.getDeclaredField("importingData");
    field.setAccessible(true);
    field.setBoolean(domain, importing);
  }

  /**
   * Waits for a session which started while the missing change publisher of the domain runs to hand it
   * its start - under the lock the session hands it over under, which a thread holding the lock of the
   * pending changes may take.
   */
  private static void waitUntilAStartIsHandedOver(final LDAPReplicationDomain domain) throws Exception
  {
    final Field recoveryLockField = LDAPReplicationDomain.class.getDeclaredField("recoveryLock");
    recoveryLockField.setAccessible(true);
    final Object recoveryLock = recoveryLockField.get(domain);
    final Field publisherField = LDAPReplicationDomain.class.getDeclaredField("rsUpdater");
    publisherField.setAccessible(true);
    new TestTimer.Builder()
        .maxSleep(30, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() ->
        {
          synchronized (recoveryLock)
          {
            final Object publisher = publisherField.get(domain);
            assertNotNull(publisher, "a missing change publisher runs");
            final Field nextStart = publisher.getClass().getDeclaredField("nextStart");
            nextStart.setAccessible(true);
            assertNotNull(nextStart.get(publisher), "a session handed its start over");
          }
        });
  }

  /** Waits for a missing change publisher of the domain to end the recovery. */
  private static void waitUntilTheRecoveryIsOver(final LDAPReplicationDomain domain) throws Exception
  {
    final PendingChanges pendingChanges = pendingChangesOf(domain);
    new TestTimer.Builder()
        .maxSleep(30, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(() -> assertFalse(pendingChanges.isRecovering(), "the recovery is over"));
  }
}
