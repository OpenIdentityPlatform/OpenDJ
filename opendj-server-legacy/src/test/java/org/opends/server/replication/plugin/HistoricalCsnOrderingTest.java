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
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.core.DirectoryServer;
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
    public boolean publishRecovery(ReplicationMsg msg)
    {
      if (writes)
      {
        list.add(msg);
      }
      return writes;
    }

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
   * delete, which is kept aside as it is done, escaped this.
   */
  @Test
  public void aChangeDoneWhileARoundSearchesIsPublished() throws Exception
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

      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "before the recovery");
      done.add(rd1.getLastLocalChange());
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
   * would publish it.
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

      startRecovery(rd1);
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

      startRecovery(rd1);
      replace(dnA, "displayName", "from this replica");
      final CSN localCSN = rd1.getLastLocalChange();
      assertTrue(otherCSN.isOlderThan(localCSN), "the change of the other replica comes first");

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
   * Local changes go on while a recovery runs. The recovery still ends - its last round holds them
   * back - and each change is published either by the recovery or, once it has ended, by the
   * broker: none is left to a recovery which is over.
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

      startRecovery(rd1);
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

      startRecovery(rd1);
      replace(dnA, "description", "in the last millisecond of the window");
      final CSN localCSN = rd1.getLastLocalChange();
      assertEquals(localCSN.getTime(), endOfFirstWindow, "the change is in the last millisecond of the window");

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
   * recovery went on for as long as the local changes did.
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

      startRecovery(rd1);
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
   * kept aside in can publish again: a modify, the search of the next recovery finds again.
   */
  @Test
  public void changesTheBrokerRefusedAreLeftToTheNextRecovery() throws Exception
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
      broker.stop();
      assertFalse(broker.publishRecovery(new WindowProbeMsg()), "a stopped broker writes nothing");
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

      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "before the search fails");
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(dnB);
      done.add(rd1.getLastLocalChange());
      replace(dnC, "description", "before the search fails");
      done.add(rd1.getLastLocalChange());

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
   * The deletes a round took to publish and did not, as it threw on the way, are put back: they
   * have nothing else to be published from, and the recovery of the session started again
   * publishes them.
   */
  @Test
  public void theDeletesARoundThrewBeforePublishingAreLeftToTheNextRecovery() throws Exception
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
      TestCaseUtils.deleteEntry(dnA);
      final CSN first = rd1.getLastLocalChange();
      TestCaseUtils.deleteEntry(dnB);
      final CSN second = rd1.getLastLocalChange();

      final List<ReplicationMsg> published = new ArrayList<>();
      final ReplicationBroker throwingOnTheSecond = new TestBroker(published)
      {
        @Override
        public boolean publishRecovery(ReplicationMsg msg)
        {
          if (!published.isEmpty())
          {
            throw new IllegalStateException("the publish threw, as a test asked");
          }
          return super.publishRecovery(msg);
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
      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      replace(dnA, "description", "missed by the replication server");
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(dnB);
      done.add(rd1.getLastLocalChange());

      // The session started again recovers them, and the first round of its recovery fails.
      rd1.setMissingChangesSearchHook(onTheFirstSearch(failure));
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

      startRecovery(rd1);
      final List<CSN> done = new ArrayList<>();
      TestCaseUtils.deleteEntry(dnA);
      done.add(rd1.getLastLocalChange());
      TestCaseUtils.deleteEntry(baseDN);
      done.add(rd1.getLastLocalChange());

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
      final SortedSet<String> towardsEmpty = new TreeSet<>(Collections.singleton("localhost:" + emptyPort));
      empty = new ReplicationServer(new ReplServerFakeConfiguration(
          emptyPort, "HistoricalCsnOrderingEmpty", 0, 2, 0, 100, towardsEmpty, 1, 1000, 5000));
      clearChangelogDB(empty);
      assertEquals(rd1.applyConfigurationChange(domainConfiguration(serverId, towardsEmpty)).getResultCode(),
          ResultCode.SUCCESS);
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

      startRecovery(rd1);
      // The next change is numbered right after this one, in the same millisecond: the clock has not
      // reached it yet, the change being done less than a window after the one before.
      rd1.getGenerator().adjust(new CSN(endOfFirstWindow.getTime() - 1, Integer.MAX_VALUE - 1, serverId));
      replace(dnA, "description", "numbered the end of the window");
      assertEquals(rd1.getLastLocalChange(), endOfFirstWindow, "the change is numbered the end of the window");

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
   * place. The pending changes leave the local changes to the recovery, and the broker refuses
   * them.
   */
  private static void startRecovery(LDAPReplicationDomain domain) throws Exception
  {
    pendingChangesOf(domain).setRecovering(true);
    brokerOf(domain).setRecoveryRequired(true);
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
    rsPort = TestCaseUtils.findFreePort();
    replServers.add("localhost:" + rsPort);

    ReplicationServer replicationServer = new ReplicationServer(
        new ReplServerFakeConfiguration(rsPort, "HistoricalCsnOrdering", 0, 1, 0, 100, replServers, 1, 1000, 5000));
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
