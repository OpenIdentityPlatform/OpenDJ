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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Test the usage of the historical data of the replication.
 */
@SuppressWarnings("javadoc")
public class HistoricalCsnOrderingTest extends ReplicationTestCase
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  private final int serverId = 123;
  private final SortedSet<String> replServers = new TreeSet<>();

  private static class TestBroker extends ReplicationBroker
  {
    private final List<ReplicationMsg> list;

    private TestBroker(List<ReplicationMsg> list)
    {
      super(new DummyReplicationDomain(0), null,
          new DomainFakeCfg(null, 0, new TreeSet<String>()), null);
      this.list = list;
    }

    @Override
    public void publishRecovery(ReplicationMsg msg)
    {
      list.add(msg);
    }

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

    ByteString v1 = ByteString.valueOf("a:" + del1);
    ByteString v2 = ByteString.valueOf("a:" + del2);

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
      DirectoryServer.getAttributeType(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);

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
    List<Attribute> attrs1 = entry.getAttribute(histType);
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
    List<Attribute> attrs2 = entry2.getAttribute(histType);
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

  private ReplicationServer createReplicationServer() throws Exception
  {
    int rsPort = TestCaseUtils.findFreePort();
    replServers.add("localhost:" + rsPort);

    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(rsPort, "HistoricalCsnOrdering", replicationDbImplementation, 0,
            1, 0, 100, replServers, 1, 1000, 5000);
    ReplicationServer replicationServer = new ReplicationServer(conf);
    clearChangelogDB(replicationServer);
    return replicationServer;
  }

  private LDAPReplicationDomain createReplicationDomain(int dsId)
          throws DirectoryException, ConfigException
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DomainFakeCfg domainConf = new DomainFakeCfg(
        baseDN, dsId, replServers, AssuredType.NOT_ASSURED, 2, 1, 0, null);
    LDAPReplicationDomain replicationDomain = MultimasterReplication.createNewDomain(domainConf);
    replicationDomain.start();
    return replicationDomain;
  }
}
