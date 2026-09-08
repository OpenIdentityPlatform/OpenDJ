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
 * Portions Copyright 2023-2026 3A Systems, LLC
 */
package org.opends.server.replication;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.mockito.Mockito.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.replication.plugin.LDAPReplicationDomain.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.extensions.DummyAlertHandler;
import org.opends.server.plugins.PausePreParsePlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.plugins.ShortCircuitPlugin.ParkedReplay;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.HeartbeatThread;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawModification;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.opends.server.util.TimeThread;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test synchronization of update operations on the directory server and through
 * the replication server broker interface.
 */
@SuppressWarnings("javadoc")
public class UpdateOperationTest extends ReplicationTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * How long a change is retried in the tests which check that this replica gives up on
   * a change it can never apply: long enough for the change to be delivered again a
   * couple of times, short enough not to make the test wait out a real backend outage.
   * In the duration syntax of the {@code replay-give-up-delay} property.
   */
  private static final String TEST_GIVE_UP_DELAY = "2000ms";

  /** The configuration attribute which carries the replay give-up budget of a domain. */
  private static final String ATTR_REPLAY_GIVE_UP_DELAY = "ds-cfg-replay-give-up-delay";

  /**
   * How long a replay parked by {@link PausePreParsePlugin} is held after the domain cut its
   * session, in the test which checks that a change being applied is recorded in the
   * ServerState a domain going down saves.
   * <p>
   * It has to be long enough for a domain which does not wait for the replay to have saved
   * its ServerState by the time the change is applied - that is the failure the test
   * reports - and well under the time a domain which does wait gives the replay, which that
   * test leaves at its default. Spent inside that wait, so what it costs the test is itself
   * and nothing more.
   * <p>
   * Counted from the moment the session was cut rather than from the wait, because that is
   * the only moment this test can see: {@code ReplicationBroker.stop()} is the first
   * statement of {@code disableService()}, and it stops the domain being connected before
   * the listener thread is asked to stop and joined - a join with no bound on it - and
   * before the ServerState is saved. So what this delay has to outlast is that whole
   * remainder of {@code disable()} and not the save alone. The remainder is a millisecond
   * on an idle machine and hundreds of them on a loaded one, and a delay of the same order
   * would hand the released replay a race against the save rather than a loss to it: the
   * change would be recorded whether or not anything waited for it, and the test would stop
   * saying anything without ever failing.
   */
  private static final long SETTLE_BEFORE_RELEASE_IN_MS = 2000;

  /**
   * How long a domain is told to wait for a replay it can not drain, in the test which
   * checks that it gives up rather than hold the task which is taking it down. Long enough
   * to be told apart from not waiting at all, short enough for a test to spend.
   * <p>
   * Told apart from the rest of {@code disable()}, to be exact: the test times that call as
   * a whole rather than the wait inside it, so this budget is what has to dominate cutting
   * the session, joining the listener thread with no bound on the join, and saving the
   * ServerState with an internal modify. That remainder is a millisecond on an idle machine
   * and hundreds of them on a loaded one, and a budget of the same order would have the
   * assertion satisfied by the remainder alone - the wait taken out of the domain and
   * nothing reporting it.
   */
  private static final long TEST_REPLAY_DRAIN_TIMEOUT_IN_MS = 2000;

  /** An entry with a entryUUID. */
  private Entry personWithUUIDEntry;
  private Entry personWithSecondUniqueID;

  private Entry  user3Entry;
  private DN user3dn;
  private String user3UUID;

  private String baseUUID;

  private DN user1dn;
  private String user1entrysecondUUID;
  private String user1entryUUID;

  /** A "person" entry. */
  private Entry personEntry;
  private int replServerPort;
  private String domain1uid;
  private String domain2uid;
  private String domain3uid;
  private DN domain1dn;
  private DN domain2dn;
  private DN domain3dn;
  private Entry domain1;
  private Entry domain2;
  private Entry domain3;

  private int domainSid = 55;
  private DN baseDN;

  /** Set up the environment for performing the tests in this Class. */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    baseDN = DN.valueOf("ou=People," + TEST_ROOT_DN_STRING);

    // Create necessary backend top level entry
    TestCaseUtils.addEntry(
        "dn: " + baseDN,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "entryUUID: 11111111-1111-1111-1111-111111111111");

    baseUUID = getEntryUUID(baseDN);

    replServerPort = TestCaseUtils.findFreePort();

    // replication server
    String replServerLdif =
      "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: UpdateOperationTest\n"
        + "ds-cfg-replication-server-id: 107\n"
        /*
         * Long enough for a delivery which a test stops on its way through the replay:
         * the acks of an assured update are waited for from the moment it is published,
         * and the default second is spent long before a test which parks that delivery
         * has let go of it. Nothing waits it out - no test here leaves an assured update
         * unacknowledged - so it only bounds a failure.
         */
        + "ds-cfg-assured-timeout: 120000ms\n";

    // suffix synchronized
    String testName = "updateOperationTest";
    String synchroServerLdif =
      "dn: cn=" + testName + ", cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: " + baseDN + "\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-server-id: "+ domainSid +"\n"
        + "ds-cfg-receive-status: true\n";

    configureReplication(replServerLdif, synchroServerLdif);
  }

  private void testSetUp(String tc) throws Exception
  {
    personEntry = TestCaseUtils.makeEntry(
        "dn: uid=user.1." + tc + "," + baseDN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "homePhone: 951-245-7634",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "mobile: 027-085-0537",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street $Rockford, NC  85762",
        "mail: user.1@example.com",
        "cn: Aaccf Amar",
        "l: Rockford",
        "pager: 508-763-4246",
        "street: 17984 Thirteenth Street",
        "telephoneNumber: 216-564-6748",
        "employeeNumber: 1",
        "sn: Amar",
        "givenName: Aaccf",
        "postalCode: 85762",
        "userPassword: password",
        "initials: AA");

    /*
     * The 2 entries defined in the following code are used for the naming
     * conflict resolution test (called namingConflicts)
     * They must have the same DN but different entryUUID.
     */
    user1entryUUID = "33333333-3333-3333-3333-333333333333";
    user1entrysecondUUID = "22222222-2222-2222-2222-222222222222";
    user1dn = DN.valueOf("uid=user1" + tc + "," + baseDN);
    personWithUUIDEntry = TestCaseUtils.makeEntry(
        "dn: " + user1dn,
        "objectClass: top", "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson", "uid: user.1",
        "homePhone: 951-245-7634",
        "description: This is the description for Aaccf Amar.", "st: NC",
        "mobile: 027-085-0537",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street $Rockford, NC  85762", "mail: user.1@example.com",
        "cn: Aaccf Amar", "l: Rockford", "pager: 508-763-4246",
        "street: 17984 Thirteenth Street",
        "telephoneNumber: 216-564-6748", "employeeNumber: 1",
        "sn: Amar", "givenName: Aaccf", "postalCode: 85762",
        "userPassword: password", "initials: AA",
        "entryUUID: " + user1entryUUID + "\n");

    personWithSecondUniqueID = TestCaseUtils.makeEntry(
        "dn: "+ user1dn,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "homePhone: 951-245-7634",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "mobile: 027-085-0537",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street $Rockford, NC  85762",
        "mail: user.1@example.com",
        "cn: Aaccf Amar",
        "l: Rockford",
        "pager: 508-763-4246",
        "street: 17984 Thirteenth Street",
        "telephoneNumber: 216-564-6748",
        "employeeNumber: 1",
        "sn: Amar",
        "givenName: Aaccf",
        "postalCode: 85762",
        "userPassword: password",
        "initials: AA",
        "entryUUID: "+ user1entrysecondUUID);

    user3UUID = "44444444-4444-4444-4444-444444444444";
    user3dn = DN.valueOf("uid=user3" + tc + "," + baseDN);
    user3Entry = TestCaseUtils.makeEntry("dn: "+ user3dn,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "homePhone: 951-245-7634",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "mobile: 027-085-0537",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street $Rockford, NC  85762",
        "mail: user.3@example.com",
        "cn: Aaccf Amar",
        "l: Rockford",
        "pager: 508-763-4246",
        "street: 17984 Thirteenth Street",
        "telephoneNumber: 216-564-6748",
        "employeeNumber: 1",
        "sn: Amar",
        "givenName: Aaccf",
        "postalCode: 85762",
        "userPassword: password",
        "initials: AA",
        "entryUUID: " + user3UUID);

    domain1dn = DN.valueOf("dc=domain1," + baseDN);
    domain2dn = DN.valueOf("dc=domain2,dc=domain1," + baseDN);
    domain3dn = DN.valueOf("dc=domain3,dc=domain1," + baseDN);
    domain1 = TestCaseUtils.makeEntry(
        "dn:" + domain1dn,
        "objectClass:domain",
        "dc:domain1");
    domain2 = TestCaseUtils.makeEntry(
        "dn:" + domain2dn,
        "objectClass:domain",
        "dc:domain2");
    domain3 = TestCaseUtils.makeEntry(
        "dn:" + domain3dn,
        "objectClass:domain",
        "dc:domain3");
  }

  /** Add an entry in the database. */
  private CSN addEntry(Entry entry) throws Exception
  {
    AddOperation addOp = connection.processAdd(entry);
    assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(getEntry(entry.getName(), 1000, true));
    return OperationContext.getCSN(addOp);
  }

  /** Delete an entry in the database. */
  private void delEntry(DN dn) throws Exception
  {
    connection.processDelete(dn);
    assertNull(getEntry(dn, 1000, false));
  }

  /**
   * Tests whether the synchronization provider receive status can be disabled
   * then re-enabled.
   * FIXME Enable this test when broker suspend/resume receive are implemented.
   */
  @Test(enabled=false)
  public void toggleReceiveStatus() throws Exception
  {
    testSetUp("toggleReceiveStatus");
    logger.error(LocalizableMessage.raw("Starting synchronization test : toggleReceiveStatus"));

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    final int serverId = 2;
    ReplicationBroker broker =
      openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);

    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      // Disable the directory server receive status.
      setReceiveStatus(synchroServerEntry.getName(), false);

      // Create and publish an update message to add an entry.
      broker.publish(addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID));

      assertNull(getEntry(personWithUUIDEntry.getName(), 1000, true),
          "The replication message was replayed while it should not have been: "
              + "the server receive status was disabled");

      // Enable the directory server receive status.
      setReceiveStatus(synchroServerEntry.getName(), true);

      broker.publish(addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID));

      assertNotNull(getEntry(personWithUUIDEntry.getName(), 10000, true),
          "The replication message was not replayed while it should have been: "
              + "the server receive status was reenabled");

      // Delete the entries to clean the database.
      broker.publish(
          new DeleteMsg(personWithUUIDEntry.getName(), gen.newCSN(), user1entryUUID));

      assertNull(getEntry(personWithUUIDEntry.getName(), 10000, false),
          "The DELETE replication message was not replayed");
    }
    finally
    {
      broker.stop();
    }
  }

  private AddMsg addMsg(CSNGenerator gen, Entry entry, String uniqueId, String parentId)
  {
    return new AddMsg(gen.newCSN(), entry.getName(), uniqueId, parentId,
        entry.getObjectClassAttribute(), entry.getAllAttributes(), null);
  }

  /**
   * Tests whether the synchronization provider fails over when it loses
   * the heartbeat from the replication server.
   */
  @Test
  public void lostHeartbeatFailover() throws Exception
  {
    testSetUp("lostHeartbeatFailover");
    logger.error(LocalizableMessage.raw("Starting replication test : lostHeartbeatFailover"));

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    int serverId = 2;
    ReplicationBroker broker =
      openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);

    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      // Create and publish an update message to add an entry.
      broker.publish(addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID));

      assertNotNull(getEntry(personWithUUIDEntry.getName(), 30000, true),
          "The ADD replication message was not replayed");

      // Send a first modify operation message.
      List<Modification> mods = generatemods("telephonenumber", "01 02 45");
      ModifyMsg modMsg = new ModifyMsg(gen.newCSN(),
          personWithUUIDEntry.getName(), mods, user1entryUUID);
      broker.publish(modMsg);

      // Check that the modify has been replayed.
      checkEntryHasAttributeValue(personWithUUIDEntry.getName(), "telephonenumber", "01 02 45", 10,
          "The first modification was not replayed.");

      // Simulate loss of heartbeats.
      HeartbeatThread.setHeartbeatsDisabled(true);
      Thread.sleep(3000);
      HeartbeatThread.setHeartbeatsDisabled(false);

      // Send a second modify operation message.
      mods = generatemods("description", "Description was changed");
      modMsg = new ModifyMsg(gen.newCSN(),
          personWithUUIDEntry.getName(), mods, user1entryUUID);
      broker.publish(modMsg);

      // Check that the modify has been replayed.
      checkEntryHasAttributeValue(personWithUUIDEntry.getName(), "description", "Description was changed", 10,
          "The second modification was not replayed.");

      // Delete the entries to clean the database.
      broker.publish(
          new DeleteMsg(personWithUUIDEntry.getName(), gen.newCSN(), user1entryUUID));
      assertNull(getEntry(personWithUUIDEntry.getName(), 10000, false),
          "The DELETE replication message was not replayed");
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Tests the modify conflict resolution code.
   * In this test, the local server acts both as an LDAP server and
   * a replicationServer that are inter-connected.
   *
   * The test creates an other session to the replicationServer using
   * directly the ReplicationBroker API.
   * It then uses this session to simulate conflicts and therefore
   * test the modify conflict resolution code.
   */
  @Test(enabled=true)
  public void modifyConflicts() throws Exception
  {
    testSetUp("modifyConflicts");
    final DN dn1 = DN.valueOf("cn=test1," + baseDN);
    final AttributeType attrType = getServerContext().getSchema().getAttributeType("displayname");
    final AttributeType entryuuidType = getEntryUUIDAttributeType();
    String monitorAttr = "resolved-modify-conflicts";

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ReplicationBroker broker =
        openReplicationSession(baseDN, 2, 100, replServerPort, 1000);

    try
    {
      // Add the first test entry.
      TestCaseUtils.addEntry(
          "dn: cn=test1," + baseDN,
          "displayname: Test1",
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "cn: test1",
          "sn: test");

      // Read the entry back to get its UUID.
      Entry entry = DirectoryServer.getEntry(dn1);
      List<Attribute> attrs = entry.getAllAttributes(entryuuidType);
      String entryuuid = attrs.get(0).iterator().next().toString();

      // A change on a first server.
      long changeTime = TimeThread.getTime();
      CSN t1 = new CSN(changeTime, 0, 3);

      // A change on a second server.
      changeTime++;
      CSN t2 = new CSN(changeTime, 0, 4);

      // Simulate the ordering t2:replace:B followed by t1:add:A that
      updateMonitorCount(baseDN, monitorAttr);

      // Replay a replace of a value B at time t2 on a second server.
      Attribute attr = Attributes.create(attrType, "B");
      List<Modification> mods = newArrayList(new Modification(ModificationType.REPLACE, attr));
      ModifyMsg modMsg = new ModifyMsg(t2, dn1, mods, entryuuid);
      broker.publish(modMsg);

      Thread.sleep(2000);

      // Replay an add of a value A at time t1 on a first server.
      attr = Attributes.create(attrType, "A");
      mods = newArrayList(new Modification(ModificationType.ADD, attr));
      modMsg = new ModifyMsg(t1, dn1, mods, entryuuid);
      broker.publish(modMsg);

      Thread.sleep(2000);

      // Read the entry to see how the conflict was resolved.
      entry = DirectoryServer.getEntry(dn1);
      attrs = entry.getAllAttributes(attrType);
      String attrValue1 = attrs.get(0).iterator().next().toString();

      // the value should be the last (time t2) value added
      assertEquals(attrValue1, "B");
      assertEquals(getMonitorDelta(), 1);

      // Simulate the ordering t2:delete:displayname followed by
      // t1:replace:displayname
      // A change on a first server.
      changeTime++;
      t1 = new CSN(changeTime, 0, 3);

      // A change on a second server.
      changeTime++;
      t2 = new CSN(changeTime, 0, 4);

      // Simulate the ordering t2:delete:displayname followed by t1:replace:A
      updateMonitorCount(baseDN, monitorAttr);

      // Replay an delete of attribute displayname at time t2 on a second server.
      attr = Attributes.empty(attrType);
      mods = newArrayList(new Modification(ModificationType.DELETE, attr));
      modMsg = new ModifyMsg(t2, dn1, mods, entryuuid);
      broker.publish(modMsg);

      Thread.sleep(2000);

      // Replay a replace of a value A at time t1 on a first server.
      attr = Attributes.create(attrType, "A");
      mods = newArrayList(new Modification(ModificationType.REPLACE, attr));
      modMsg = new ModifyMsg(t1, dn1, mods, entryuuid);
      broker.publish(modMsg);

      Thread.sleep(2000);

      // Read the entry to see how the conflict was resolved.
      entry = DirectoryServer.getEntry(dn1);
      attrs = entry.getAllAttributes(attrType);

      // there should not be a value (delete at time t2)
      Assertions.assertThat(attrs).isEmpty();
      assertEquals(getMonitorDelta(), 1);
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Waits for the replay thread to update the monitored counter: reading the
   * monitor immediately after publishing a message races with the replay.
   */
  private void waitForMonitorDelta(final long expectedDelta) throws Exception
  {
    final long[] total = { 0 };
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(30, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        total[0] += getMonitorDelta();
        assertEquals(total[0], expectedDelta);
      }
    });
  }

  /**
   * Tests the naming conflict resolution code.
   * In this test, the local server act both as an LDAP server and
   * a replicationServer that are inter-connected.
   *
   * The test creates an other session to the replicationServer using
   * directly the ReplicationBroker API.
   * It then uses this session to simulate conflicts and therefore
   * test the naming conflict resolution code.
   */
  @Test(enabled=true)
  public void namingConflicts() throws Exception
  {
    testSetUp("namingConflicts");
    logger.error(LocalizableMessage.raw("Starting replication test : namingConflicts"));

    String resolvedMonitorAttr = "resolved-naming-conflicts";
    String unresolvedMonitorAttr = "unresolved-naming-conflicts";

    /*
     * Open a session to the replicationServer using the ReplicationServer broker API.
     * This must use a serverId different from the LDAP server ID
     */
    final int serverId = 2;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

    /*
     * Test that the conflict resolution code is able to find entries
     * that have been renamed by an other master.
     * To simulate this, create an entry with a given UUID and a given DN
     * then send a modify operation using another DN but the same UUID.
     * Finally check that the modify operation has been applied.
     */
      // create the entry with a given DN
      broker.publish(addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID));

      // Check that the entry has been created in the local DS.
      assertNotNull(getEntry(personWithUUIDEntry.getName(), 10000, true),
        "The send ADD replication message was not applied");

    // send a modify operation with the correct unique ID but another DN
    List<Modification> mods = generatemods("telephonenumber", "01 02 45");
    ModifyMsg modMsg = new ModifyMsg(gen.newCSN(),
        DN.valueOf("cn=something," + baseDN), mods, user1entryUUID);
    updateMonitorCount(baseDN, resolvedMonitorAttr);
      int alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(modMsg);

    // check that the modify has been applied as if the entry had been renamed.
      checkEntryHasAttributeValue(personWithUUIDEntry.getName(), "telephonenumber", "01 02 45", 10,
          "The modification has not been correctly replayed.");
    assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);

    /*
     * Test that modify conflict resolution is able to detect that
     * because there is a conflict between a MODIFYDN and a MODIFY,
     * when a MODIFY is replayed the attribute that is being modified is
     * now the RDN of the entry and therefore should not be deleted.
     */
    // send a modify operation attempting to replace the RDN entry
    // with a new value
    mods = generatemods("uid", "AnotherUid");
    modMsg = new ModifyMsg(gen.newCSN(),
        personWithUUIDEntry.getName(), mods, user1entryUUID);

    updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(modMsg);

    // check that the modify has been applied.
      checkEntryHasAttributeValue(personWithUUIDEntry.getName(), "uid", "AnotherUid", 10,
          "The modification has not been correctly replayed.");
    assertEquals(getMonitorDelta(), 1);

    /*
     * Test that the conflict resolution code is able to detect
     * that an entry has been renamed and that a new entry has
     * been created with the same DN but another entry UUID
     * To simulate this, create and entry with a given UUID and a given DN
     * then send a modify operation using the same DN but another UUID.
     * Finally check that the modify operation has not been applied to the
     * entry with the given DN.
     */

    //  create the entry with a given DN and unique ID
      broker.publish(addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID));

    // Check that the entry has been created in the local DS.
    assertNotNull(getEntry(personWithUUIDEntry.getName(), 10000, true),
        "The ADD replication message was not applied");

    // send a modify operation with a wrong unique ID but the same DN
    mods = generatemods("telephonenumber", "02 01 03 05");
    modMsg = new ModifyMsg(gen.newCSN(),
        user1dn, mods, "10000000-9abc-def0-1234-1234567890ab");
    updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(modMsg);

    // check that the modify has not been applied
    Thread.sleep(2000);
      checkEntryHasNoSuchAttributeValue(personWithUUIDEntry.getName(), "telephonenumber", "02 01 03 05", 10,
          "The modification has been replayed while it should not.");
    assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);


    /*
     * Test that the conflict resolution code is able to find entries
     * that have been renamed by an other master.
     * To simulate this, send a delete operation using another DN but
     * the same UUID has the entry that has been used in the tests above.
     * Finally check that the delete operation has been applied.
     */
      // send a delete operation with a wrong dn but the unique ID of the entry
      // used above
      updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
      DN delDN = DN.valueOf("cn=anotherdn," + baseDN);
      broker.publish(new DeleteMsg(delDN, gen.newCSN(), user1entryUUID));

      // check that the delete operation has been applied
      assertNull(getEntry(personWithUUIDEntry.getName(), 10000, false),
          "The DELETE replication message was not replayed");
      assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);

    /*
     * Test that two adds with the same DN but a different unique ID result
     * cause a conflict and result in the second entry to be renamed.
     */

    //  create an entry with a given DN and unique ID
      broker.publish(addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID));

    //  Check that the entry has been created in the local DS.
    assertNotNull(getEntry(personWithUUIDEntry.getName(), 10000, true),
        "The ADD replication message was not applied");

    //  create an entry with the same DN and another unique ID
    updateMonitorCount(baseDN, unresolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
      broker.publish(addMsg(gen, personWithSecondUniqueID, user1entrysecondUUID, baseUUID));

      // Check that the entry has been renamed and created in the local DS.
      DN dn2 = DN.valueOf("entryuuid=" + user1entrysecondUUID + " + " + user1dn);
      final Entry entryAfterAdd = getEntry(dn2, 10000, true);
      assertNotNull(entryAfterAdd, "The ADD replication message was not applied");
      assertEquals(getMonitorDelta(), 1);
      assertConflictAttributeExists(entryAfterAdd);
      assertNewAlertsGenerated(alertCount, 1);

    //  delete the entries to clean the database.
    broker.publish(
        new DeleteMsg(personWithUUIDEntry.getName(), gen.newCSN(), user1entryUUID));
    broker.publish(
        new DeleteMsg(personWithSecondUniqueID.getName(), gen.newCSN(), user1entrysecondUUID));

    assertNull(getEntry(personWithUUIDEntry.getName(), 10000, false),
        "The DELETE replication message was not replayed");
    // The second entry was created with the same DN as the first one, so waiting
    // on that DN again returns as soon as the first delete is replayed. Wait on
    // the DN the naming conflict renamed it to instead: the second delete then
    // has resolved its conflict, and counted it, before the monitor is read below.
    assertNull(getEntry(dn2, 10000, false),
        "The DELETE replication message was not replayed");
    /*
     * Check that and added entry is correctly added below it's
     * parent entry when this parent entry has been renamed.
     *
     * Simulate this by trying to add an entry below a DN that does not
     * exist but with a parent ID that exist.
     */
      String addDN = "uid=new person,o=nothere,o=below," + baseDN;
    AddMsg addMsg = new AddMsg(gen.newCSN(),
        DN.valueOf(addDN),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAllAttributes(), null);
    updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(addMsg);

    //  Check that the entry has been created in the local DS.
      DN newPersonDN = DN.valueOf("uid=new person," + baseDN);
      assertNotNull(getEntry(newPersonDN, 10000, true),
          "The ADD replication message was not applied");
    assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);


    /*
     * Check that when replaying delete the naming conflict code
     * verify that the unique ID op the replayed operation is
     * the same as the unique ID of the entry with the given DN
     *
     * To achieve this send a delete operation with a correct DN
     * but a wrong unique ID.
     */
      updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
      broker.publish(
          new DeleteMsg(newPersonDN, gen.newCSN(), "11111111-9abc-def0-1234-1234567890ab"));

      // check that the delete operation has not been applied
      assertNotNull(getEntry(newPersonDN, 10000, true),
          "The DELETE replication message was replayed when it should not");
      // The entry already exists, so the getEntry() call above does not wait
      // for the replay: poll the monitor until the replay thread has resolved
      // the conflict.
      waitForMonitorDelta(1);
      assertConflictAutomaticallyResolved(alertCount);


    /*
     * Check that when replaying modify dn operations, the conflict
     * resolution code is able to find the new DN of the parent entry
     * if it has been renamed on another master.
     *
     * To simulate this try to rename an entry below an entry that does
     * not exist but giving the unique ID of an existing entry.
     */
    ModifyDNMsg  modDnMsg = new ModifyDNMsg(
        newPersonDN, gen.newCSN(),
        user1entryUUID, baseUUID, false,
        "uid=wrong, " + baseDN,
        "uid=newrdn");
    updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(modDnMsg);

      // check that the operation has been correctly relayed
      assertNotNull(getEntry(DN.valueOf("uid=newrdn," + baseDN), 10000, true),
          "The modify dn was not or badly replayed");
      assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);

      /* same test but by giving a bad entry DN */
      DN modDN = DN.valueOf("uid=wrong," + baseDN);
    modDnMsg = new ModifyDNMsg(modDN, gen.newCSN(),
        user1entryUUID, null, false, null, "uid=reallynewrdn");
    updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(modDnMsg);

      DN reallyNewDN = DN.valueOf("uid=reallynewrdn," + baseDN);

      // check that the operation has been correctly relayed
      assertNotNull(getEntry(reallyNewDN, 10000, true),
          "The modify dn was not or badly replayed");
      assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);


    /*
     * Check that conflicting entries are renamed when a
     * modifyDN is done with the same DN as an entry added on another server.
     */

    // add a second entry
      broker.publish(addMsg(gen, personWithSecondUniqueID, user1entrysecondUUID, baseUUID));

    //  check that the second entry has been added
      assertNotNull(getEntry(user1dn, 10000, true),
          "The add operation was not replayed");

    // try to rename the first entry
    modDnMsg = new ModifyDNMsg(user1dn, gen.newCSN(),
                               user1entrysecondUUID, baseUUID, false,
                               baseDN.toString(), "uid=reallynewrdn");
    updateMonitorCount(baseDN, unresolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
      broker.publish(modDnMsg);

      // check that the second entry has been renamed
      DN dn = DN.valueOf("entryUUID = " + user1entrysecondUUID + "+uid=reallynewrdn," + baseDN);
      final Entry entryAfterModDN = getEntry(dn, 10000, true);
      assertNotNull(entryAfterModDN, "The modifyDN was not or incorrectly replayed");
      assertEquals(getMonitorDelta(), 1);
      assertConflictAttributeExists(entryAfterModDN);
      assertNewAlertsGenerated(alertCount, 1);


      // delete the entries to clean the database
      DN delDN2 = DN.valueOf(
          "entryUUID = " + user1entrysecondUUID + "+" + user1dn.rdn() + "," + baseDN);
      broker.publish(new DeleteMsg(delDN2, gen.newCSN(), user1entrysecondUUID));
      assertNull(getEntry(delDN2, 10000, false),
          "The DELETE replication message was not replayed");

      broker.publish(new DeleteMsg(reallyNewDN, gen.newCSN(), user1entryUUID));
      assertNull(getEntry(reallyNewDN, 10000, false),
          "The DELETE replication message was not replayed");

    /*
     * When replaying add operations it is possible that the parent entry has
     * been renamed before and that another entry have taken the former dn of
     * the parent entry. In such case the replication replay code should
     * detect that the parent has been renamed and should add the entry below
     * the new dn of the parent (thus changing the original dn with which the
     * entry had been created)
     *
     * Steps
     * - create parent entry 1 with baseDn1
     * - create Add Msg for user1 with parent entry 1 UUID
     * - MODDN parent entry 1 to baseDn2 in the LDAP server
     * - add new parent entry 2 with baseDn1
     * - publish msg
     * - check that the Dn has been changed to baseDn2 in the msg received
     */
      DN baseDN1 = DN.valueOf("ou=baseDn1," + baseDN);
      DN baseDN2 = DN.valueOf("ou=baseDn2," + baseDN);

      // - create parent entry 1 with baseDn1
      connection.processAdd(TestCaseUtils.makeEntry(
          "dn: " + baseDN1,
          "objectClass: top",
          "objectClass: organizationalUnit",
          "entryUUID: 55555555-5555-5555-5555-555555555555"));
      assertNotNull(getEntry(baseDN1, 10000, true),
          "Entry not added: " + baseDN1);

    // - create Add Msg for user1 with parent entry 1 UUID
    DN newPersonDN2 = DN.valueOf("uid=new person," + baseDN1);
    addMsg = new AddMsg(gen.newCSN(),
        newPersonDN2,
        user1entryUUID,
        getEntryUUID(baseDN1),
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAllAttributes(), null);

    // - MODDN parent entry 1 to baseDn2 in the LDAP server
    ModifyDNRequest modifyDNRequest = newModifyDNRequest(baseDN1.toString(), "ou=baseDn2")
        .setDeleteOldRDN(true)
        .setNewSuperior(baseDN.toString());
    connection.processModifyDN(modifyDNRequest);
      assertNotNull(getEntry(baseDN2, 10000, true),
          "Entry not moved from " + baseDN1 + " to " + baseDN2);

      // - add new parent entry 2 with baseDn1
      connection.processAdd(TestCaseUtils.makeEntry(
          "dn: " + baseDN1,
          "objectClass: top",
          "objectClass: organizationalUnit",
          "entryUUID: 66666666-6666-6666-6666-666666666666"));

      // - publish msg
      updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
      broker.publish(addMsg);

      // - check that the DN has been changed to baseDn2
      assertNull(getEntry(newPersonDN2, 10000, false),
          "The ADD replication message was applied under " + baseDN1);
      assertNotNull(getEntry(DN.valueOf("uid=new person," + baseDN2), 10000, true),
          "The ADD replication message was NOT applied under " + baseDN2);
      assertEquals(getMonitorDelta(), 1);
      assertConflictAutomaticallyResolved(alertCount);


    // Check that when a delete is conflicting with Add of some entries
    // below the deleted entries, the child entry that have been added
    // before the deleted is replayed gets renamed correctly.

    // add domain1 entry with 2 children : domain2 and domain3
    addEntry(domain1);
    CSN olderCSN = gen.newCSN();
    Thread.sleep(1000);
    domain1uid = getEntryUUID(domain1dn);
    addEntry(domain2);
    domain2uid = getEntryUUID(domain2dn);
    addEntry(domain3);
    domain3uid = getEntryUUID(domain3dn);
    DN conflictDomain2dn = DN.valueOf(
        "entryUUID = " + domain2uid + "+dc=domain2," + baseDN);
    DN conflictDomain3dn = DN.valueOf(
        "entryUUID = " + domain3uid + "+dc=domain3," + baseDN);

      updateMonitorCount(baseDN, unresolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();

      // delete domain1
      broker.publish(new DeleteMsg(domain1dn, olderCSN, domain1uid));

    // check that the domain1 has correctly been deleted
    assertNull(getEntry(domain1dn, 10000, false),
        "The DELETE replication message was not replayed");

    // check that domain2 and domain3 have been renamed
    assertNotNull(getEntry(conflictDomain2dn, 1000, true),
        "The conflicting entries were not created");
    assertNotNull(getEntry(conflictDomain3dn, 1000, true),
        "The conflicting entries were not created");

    // check that the 2 conflicting entries have been correctly marked
      checkEntryHasAttributeValue(conflictDomain2dn, DS_SYNC_CONFLICT, domain2dn.toString(), 1, null);
      checkEntryHasAttributeValue(conflictDomain3dn, DS_SYNC_CONFLICT, domain3dn.toString(), 1, null);

    // check that unresolved conflict count has been incremented
    assertEquals(getMonitorDelta(), 1);
      assertNewAlertsGenerated(alertCount, 2);

    // delete the resulting entries for the next test
    delEntry(conflictDomain2dn);
    delEntry(conflictDomain3dn);


    // Check that when a delete is replayed over an entry which has child
    // those child are also deleted

    // add domain1 entry with 2 children : domain2 and domain3
    addEntry(domain1);
    domain1uid = getEntryUUID(domain1dn);
    addEntry(domain2);
    domain2uid = getEntryUUID(domain2dn);
    CSN addCSN = addEntry(domain3);
    gen.adjust(addCSN);
    domain3uid = getEntryUUID(domain3dn);

      updateMonitorCount(baseDN, unresolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();

      // delete domain1
      broker.publish(new DeleteMsg(domain1dn, gen.newCSN(), domain1uid));

    // check that the domain1 has correctly been deleted
    assertNull(getEntry(domain1dn, 10000, false),
        "The DELETE replication message was not replayed");

    // check that domain2 and domain3 have been renamed as conflicting
    assertTrue(DirectoryServer.entryExists(conflictDomain2dn),
          "The conflicting entry exist for domain2" + conflictDomain2dn);
    assertTrue(DirectoryServer.entryExists(conflictDomain3dn),
          "The conflicting entry exist for domain3" + conflictDomain3dn);
    // check that unresolved conflict count has been incremented
    assertEquals(getMonitorDelta(), 1);

    delEntry(conflictDomain2dn);
    delEntry(conflictDomain3dn);

    // Check that when an entry is added on one master below an entry
    // that is currently deleted on another master, the replay of the
    // add on the second master cause the added entry to be renamed
      broker.publish(addMsg(gen, domain2, domain2uid, domain1uid));

    // check that conflict entry was created
    assertNotNull(getEntry(conflictDomain2dn, 1000, true),
      "The conflicting entries were not created");

    // check that the entry have been correctly marked as conflicting.
      checkEntryHasAttributeValue(conflictDomain2dn, DS_SYNC_CONFLICT, domain2dn.toString(), 1, null);

    // check that unresolved conflict count has been incremented
    assertEquals(getMonitorDelta(), 1);

    // Check that when an entry is deleted on a first master and
    // renamed on a second master and the rename is replayed last
    // this is correctly detected as a resolved conflict.
    // To simulate this simply try a modifyDN on a non existent uid.
    modDnMsg = new ModifyDNMsg(
        newPersonDN, gen.newCSN(),
        "33343333-3533-3633-3373-333333833333", baseUUID, false,
        "uid=wrong, " + baseDN,
        "uid=newrdn");
    updateMonitorCount(baseDN, resolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    broker.publish(modDnMsg);
    // unfortunately it is difficult to check that the operation
    // did not do anything.
    // The only thing we can check is that resolved naming conflict counter
    // has correctly been incremented.
    waitForNonZeroMonitorDelta();
      assertConflictAutomaticallyResolved(alertCount);

    /*
     * Check that a conflict is detected when an entry is moved below an entry that does not exist.
     */
    updateMonitorCount(baseDN, unresolvedMonitorAttr);
      alertCount = DummyAlertHandler.getAlertCount();
    modDnMsg = new ModifyDNMsg(
        newPersonDN, gen.newCSN(),
        "33333333-3333-3333-3333-333333333333",
        "12343333-3533-3633-3333-333333833333" , false,
        "uid=wrong, " + baseDN,
        "uid=newrdn");
    broker.publish(modDnMsg);

      waitForNonZeroMonitorDelta();

      // check that the entry have been correctly marked as conflicting.
      checkEntryHasAttributeValue(
          DN.valueOf("uid=new person," + baseDN2), DS_SYNC_CONFLICT, "uid=newrdn," + baseDN2, 1, null);
    }
    finally
    {
      broker.stop();
    }
  }

  private void waitForNonZeroMonitorDelta() throws Exception, InterruptedException
  {
    // if the monitor counter did not get incremented after 200sec
    // then something got wrong.
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(200, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertNotEquals(getMonitorDelta() , 0);
      }
    });
  }

  /**
   * Check that there was an administrative alert generated because the conflict
   * has not been automatically resolved.
   */
  private void assertNewAlertsGenerated(int oldAlertCount, int expectedNbNewAlerts)
  {
    assertEquals(DummyAlertHandler.getAlertCount(), oldAlertCount + expectedNbNewAlerts,
        "An alert was not generated when resolving conflicts");
  }

  /**
   * Check that there was no administrative alert generated because the conflict
   * has been automatically resolved.
   */
  private void assertConflictAutomaticallyResolved(int expectedAlertCount)
  {
    assertEquals(DummyAlertHandler.getAlertCount(), expectedAlertCount,
        "Expected no new alert to be generated when automatically resolving conflicts");
  }

  /**
   * Check that the given entry does contain the attribute that mark the
   * entry as conflicting.
   *
   * @param entry The entry that needs to be asserted.
   * @return A boolean indicating if the entry is correctly marked.
   */
  private boolean assertConflictAttributeExists(Entry entry)
  {
    return !isEmpty(entry.getAllAttributes("ds-sync-confict"));
  }

  @DataProvider(name="assured")
  public Object[][] getAssuredFlag()
  {
    return new Object[][] { { false }, {true} };
  }

  private void cleanupTest() throws Exception
  {
    classCleanUp();
    setUp();
  }

  /** Tests done using directly the ReplicationBroker interface. */
  @Test(enabled=true, dataProvider="assured")
  public void updateOperations(boolean assured) throws Exception
  {
    testSetUp("updateOperations");
    logger.error(LocalizableMessage.raw("Starting replication test : updateOperations " + assured));

    // Cleanup from previous run
    cleanupTest();

    final int serverId = 27;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 2000);
    try {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      /*
       * Test that operations done on this server are sent to the
       * replicationServer and forwarded to our replicationServer broker session.
       */

      // Create an Entry (add operation)
      Entry tmp = personEntry.duplicate(false);
      AddOperation addOp = connection.processAdd(tmp);
      assertTrue(DirectoryServer.entryExists(personEntry.getName()),
      "The Add Entry operation failed");
      assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);
      assertClientReceivesExpectedMsg(broker, AddMsg.class, personEntry.getName());

      // Modify the entry
      connection.processModify(modifyRequest(personEntry.getName(), REPLACE, "telephonenumber", "01 02 45"));
      assertClientReceivesExpectedMsg(broker, ModifyMsg.class, personEntry.getName());

      // Modify the entry DN
      ModifyDNRequest modifyDNRequest = newModifyDNRequest(personEntry.getName().toString(), "uid=new person")
          .setDeleteOldRDN(true)
          .setNewSuperior(baseDN.toString());
      connection.processModifyDN(modifyDNRequest);
      DN newDN = DN.valueOf("uid= new person," + baseDN);
      assertTrue(DirectoryServer.entryExists(newDN),
      "The MOD_DN operation didn't create the new person entry");
      assertFalse(DirectoryServer.entryExists(personEntry.getName()),
      "The MOD_DN operation didn't delete the old person entry");
      assertClientReceivesExpectedMsg(broker, ModifyDNMsg.class, personEntry.getName());

      // Delete the entry
      connection.processDelete(newDN);
      assertFalse(DirectoryServer.entryExists(newDN),
          "Unable to delete the new person Entry");
      assertClientReceivesExpectedMsg(broker, DeleteMsg.class, newDN);

      /*
       * Now check that when we send message to the ReplicationServer
       * and that they are received and correctly replayed by the server.
       *
       * Start by testing the Add message reception
       */
      AddMsg addMsg = addMsg(gen, personWithUUIDEntry, user1entryUUID, baseUUID);
      addMsg.setAssured(assured);
      broker.publish(addMsg);

      /*
       * Check that the entry has been created in the local DS.
       */
      Entry resultEntry = getEntry(personWithUUIDEntry.getName(), 10000, true);
      assertNotNull(resultEntry,
      "The send ADD replication message was not applied for "+personWithUUIDEntry.getName());

      /*
       * Test the reception of Modify Msg
       */
      ModifyMsg modMsg = new ModifyMsg(gen.newCSN(), personWithUUIDEntry.getName(),
          generatemods("telephonenumber", "01 02 45"), user1entryUUID);
      modMsg.setAssured(assured);
      broker.publish(modMsg);

      checkEntryHasAttributeValue(personWithUUIDEntry.getName(), "telephonenumber", "01 02 45", 10,
          "The modification has not been correctly replayed.");

      // Test that replication is able to add attribute that do
      // not exist in the schema.
      List<Modification> invalidMods = generatemods("badattribute", "value");
      modMsg = new ModifyMsg(gen.newCSN(), personWithUUIDEntry.getName(),
          invalidMods, user1entryUUID);
      modMsg.setAssured(assured);
      broker.publish(modMsg);

      checkEntryHasAttributeValue(personWithUUIDEntry.getName(), "badattribute", "value", 10,
          "The modification has not been correctly replayed.");

      /*
       * Test the Reception of Modify Dn Msg
       */
      ModifyDNMsg moddnMsg = new ModifyDNMsg(personWithUUIDEntry.getName(),
          gen.newCSN(),
          user1entryUUID, null,
          true, null, "uid= new person");
      moddnMsg.setAssured(assured);
      broker.publish(moddnMsg);

      assertNotNull(getEntry(newDN, 10000, true),
          "The modify DN replication message was not applied");

      /*
       * Test the Reception of Delete Msg
       */
      DeleteMsg delMsg = new DeleteMsg(newDN, gen.newCSN(), user1entryUUID);
      delMsg.setAssured(assured);
      broker.publish(delMsg);

      assertNull(getEntry(newDN, 10000, false),
          "The DELETE replication message was not replayed");
    }
    finally
    {
      broker.stop();
    }
  }

  private void assertClientReceivesExpectedMsg(ReplicationBroker broker,
      Class<? extends LDAPUpdateMsg> type, DN expectedDN) throws Exception
  {
    final ReplicationMsg msg = broker.receive();
    Assertions.assertThat(msg).isInstanceOf(type);
    final LDAPUpdateMsg opMsg = (LDAPUpdateMsg) msg;
    final OperationType opType = getOperationType(opMsg);
    final Operation receivedOp = opMsg.createOperation(connection);
    assertEquals(receivedOp.getOperationType(), opType,
        "The received replication message is not of corrct type. msg : " + opMsg);
    assertEquals(opMsg.getDN(), expectedDN, "The received " + opType
        + " replication message is not for the expected DN : " + opMsg);
  }

  private OperationType getOperationType(LDAPUpdateMsg msg)
  {
    if (msg instanceof AddMsg)
    {
      return OperationType.ADD;
    }
    else if (msg instanceof DeleteMsg)
    {
      return OperationType.DELETE;
    }
    else if (msg instanceof ModifyMsg)
    {
      return OperationType.MODIFY;
    }
    else if (msg instanceof ModifyDNMsg)
    {
      return OperationType.MODIFY_DN;
    }
    throw new RuntimeException("Unhandled type: " + msg.getClass());
  }

  /** Test case for [Issue 635] NullPointerException when trying to access non existing entry. */
  @Test(enabled=true)
  public void deleteNoSuchObject() throws Exception
  {
    testSetUp("deleteNoSuchObject");
    logger.error(LocalizableMessage.raw("Starting replication test : deleteNoSuchObject"));

    DeleteOperation op = connection.processDelete("cn=No Such Object," + baseDN);
    assertEquals(op.getResultCode(), ResultCode.NO_SUCH_OBJECT);
  }

  /** Test case for [Issue 798] break infinite loop when problems with naming resolution conflict. */
  @Test(enabled=true)
  public void infiniteReplayLoop() throws Exception
  {
    testSetUp("infiniteReplayLoop");
    logger.error(LocalizableMessage.raw("Starting replication test : infiniteReplayLoop"));

    int serverId = 11;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      // Create a test entry.
      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.2," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.2",
          "homePhone: 951-245-7634",
          "description: This is the description for Aaccf Amar.",
          "st: NC",
          "mobile: 027-085-0537",
          "postalAddress: Aaccf Amar$17984 Thirteenth Street $Rockford, NC  85762",
          "mail: user.1@example.com",
          "cn: Aaccf Amar",
          "l: Rockford",
          "pager: 508-763-4246",
          "street: 17984 Thirteenth Street",
          "telephoneNumber: 216-564-6748",
          "employeeNumber: 1",
          "sn: Amar",
          "givenName: Aaccf",
          "postalCode: 85762",
          "userPassword: password",
          "initials: AA");

      final long initialCount = getMonitorAttrValue(baseDN, "replayed-updates");

      // Get the UUID of the test entry.
      Entry resultEntry = getEntry(tmp.getName(), 1, true);
      String uuid = resultEntry.parseAttribute("entryuuid").asString();

      // Register a short circuit that will fake a no-such-object result code
      // on a delete.  This will cause a replication replay loop.
      ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE, "PreParse", 32);
      try
      {
        // Publish a delete message for this test entry.
        broker.publish(new DeleteMsg(tmp.getName(), gen.newCSN(), uuid));

        // Wait for the operation to be replayed.
        TestTimer timer = new TestTimer.Builder()
          .maxSleep(5, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer();
        timer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertNotEquals(getMonitorAttrValue(baseDN, "replayed-updates"), initialCount);
          }
        });
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Test case for [Issue 889]: a change whose replay failed on the server itself must
   * not be recorded as replayed. Recording it would advance the ServerState past the
   * change, so the replication server would never send it again while this replica
   * reports itself up to date.
   */
  @Test
  public void failedReplayIsNotRecordedAsReplayed() throws Exception
  {
    testSetUp("failedReplayIsNotRecordedAsReplayed");
    logger.error(LocalizableMessage.raw("Starting replication test : failedReplayIsNotRecordedAsReplayed"));

    final int serverId = 12;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.889," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.889",
          "cn: Aaccf Amar",
          "sn: Amar");
      String uuid = getEntry(tmp.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
      domain.resetUnreplayedChangeAlertThrottle();
      final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);
      // A backend which is down for maintenance is waited out for minutes: this test can
      // not, so the change is given up on after a couple of deliveries instead. The short
      // circuit below is the server's, so it is registered inside the try which takes it
      // back.
      setReplayGiveUpDelay(TEST_GIVE_UP_DELAY);
      try
      {
        /*
         * Fail the replay the way a storage failure does: the backend reports it with the
         * server-error-result-code, 80 by default. The short circuit has to be set at the
         * pre-parse plugin point, the pre-operation ones are not invoked for
         * synchronization operations.
         */
        ShortCircuitPlugin.registerShortCircuit(
            OperationType.DELETE, "PreParse", ResultCode.OTHER.intValue());

        final CSN csn = gen.newCSN();
        broker.publish(new DeleteMsg(tmp.getName(), csn, uuid));

        /*
         * The replication server resumes from the ServerState of this replica, so it only
         * sends the change again as long as the state does not cover it: seeing the same
         * change delivered more than once is what tells that it was not recorded as
         * replayed.
         *
         * One delivery is retried in place IN_PLACE_REPLAY_ATTEMPTS times before the
         * session is restarted, so it takes more than that many short circuits to prove
         * that the change was delivered a second time.
         */
        TestTimer timer = new TestTimer.Builder()
          .maxSleep(60, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer();
        timer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertTrue(ShortCircuitPlugin.getShortCircuitCount(OperationType.DELETE, "PreParse")
                    > IN_PLACE_REPLAY_ATTEMPTS,
                "the change was not sent again after its replay failed");
          }
        });
        assertNotNull(getEntry(tmp.getName(), 1, true), "the entry must not have been deleted");

        /*
         * The change can never be applied here, so the replica eventually gives up on it
         * rather than stopping for good: it then warns that it has diverged.
         */
        TestTimer giveUpTimer = new TestTimer.Builder()
          .maxSleep(120, SECONDS)
          .sleepTimes(200, MILLISECONDS)
          .toTimer();
        giveUpTimer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertTrue(domain.getServerState().cover(csn),
                "the replica did not give up on a change it can never replay");
          }
        });
        assertMonitorAttrValueEventually(baseDN, "replayed-updates-failed", initialFailures + 1,
            "a change which could not be replayed must be counted once, not once per attempt");
        /*
         * A counter bumped once per attempt rather than once per change goes through the
         * expected value on its way, so the value has to be seen to stay put rather than
         * to be reached once.
         */
        assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures + 1,
            "a change which could not be replayed must be counted once, not once per attempt");
        Assertions.assertThat(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE))
            .as("the administrator must be told that this replica now diverges")
            .isGreaterThan(initialAlerts);
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
        resetReplayGiveUpDelay();
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Test case for [Issue 909]: a replay thread which is stopped while it holds a change -
   * the number of replay threads is changed on a live server - must hand that change back
   * to the replication server instead of leaving it listed as owned by a thread which is
   * gone.
   * <p>
   * The change is parked inside the operation it is replayed by, so the thread is caught
   * while it still owns it rather than raced for: a change which is released by the
   * ordinary recovery instead ends the same way - delivered again and applied - so a test
   * which only watched the end state would pass whether or not the hand-back happened.
   * <p>
   * What tells them apart is which thread replays the change next. The thread which held
   * it is gone, and the change is replayed by one of the threads which replaced it, so
   * the delivery it is replayed from can only be a new one: an attempt which the same
   * thread made again would be the retry in place, and a change nobody handed back is
   * never delivered again at all - it stays listed as owned by a thread which is gone,
   * with the ServerState of this domain stopped behind it for good.
   */
  @Test
  public void aChangeAStoppedReplayThreadHeldIsGivenBackAndDeliveredAgain() throws Exception
  {
    testSetUp("aChangeAStoppedReplayThreadHeldIsGivenBackAndDeliveredAgain");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeAStoppedReplayThreadHeldIsGivenBackAndDeliveredAgain"));

    final int serverId = 14;
    /*
     * In the group of the replication server, so that the delete published below is one
     * this domain has to acknowledge: an assured update from a broker of another group is
     * acknowledged by the replication server itself, and says nothing about the replay.
     */
    ReplicationBroker broker =
        openAssuredReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.909," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.909",
          "cn: Aaccf Amar",
          "sn: Amar");
      String uuid = getEntry(tmp.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      domain.resetUnreplayedChangeAlertThrottle();
      final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);
      /*
       * Only the counters of the replay are read: a session restart takes this domain
       * through NOT_CONNECTED, which resets every monitoring counter of the replication
       * service - the updates it received and processed, and the assured acks it sent -
       * and handing a change back is a session restart.
       */
      final long initialApplied = getMonitorAttrValue(baseDN, "replayed-updates-ok");
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");

      /*
       * Hold the replayed deletes where they are. The park is taken at the pre-parse
       * plugin point, which runs inside op.run() and before anything of the backend was
       * taken: the replay thread stops there while it still owns the change, and the
       * pre-operation plugins are not invoked for synchronization operations at all.
       */
      final CSN csn = gen.newCSN();
      final ParkedReplay parked = ShortCircuitPlugin.parkReplayedOperations(
          OperationType.DELETE, "PreParse", op -> csn.equals(OperationContext.getCSN(op)));
      final AtomicReference<Throwable> reconfigurationFailure = new AtomicReference<>();
      Thread reconfiguration = null;
      boolean reconfigurationFinished = true;
      try
      {
        final DeleteMsg delete = new DeleteMsg(tmp.getName(), csn, uuid);
        /*
         * Published assured in SAFE_READ mode, so that the delivery which is abandoned
         * has to say what it did. The ack is the one thing the counters cannot report
         * afterwards - the session restart the hand-back performs resets every one of
         * them - so it is read off this broker rather than counted.
         */
        delete.setAssured(true);
        delete.setAssuredMode(AssuredMode.SAFE_READ_MODE);
        broker.publish(delete);

        // A replay thread now owns the change and is stopped inside its operation.
        final Thread abandoningThread = parked.awaitParked(60, SECONDS);

        /*
         * Change the number of replay threads while that thread holds the change. It runs
         * on a thread of its own because stopping the replay threads joins them: it can
         * not return before the parked thread is let go, which is exactly the ordering
         * this test is about.
         */
        reconfiguration = startReplayThreadReconfiguration(2, reconfigurationFailure);
        awaitStoppingTheReplayThreads(reconfiguration, reconfigurationFailure);

        /*
         * The parked thread has been asked to stop by now, so its attempt comes back on a
         * storage which did not serve the operation - which is retried in place - and the
         * attempt which follows is where it finds out that it is going away and gives the
         * change back instead.
         */
        parked.release(ResultCode.UNAVAILABLE.intValue());

        /*
         * The change was given back, so the replication server owns it again and delivers
         * it once more. This second park is where the state of the domain is read: the
         * change is held inside its operation, so nothing can be recording it while the
         * assertions below run.
         */
        final Thread replayingThread =
            awaitParkedOrReportReconfigurationFailure(parked, reconfigurationFailure);
        reconfiguration.join(SECONDS.toMillis(60));
        assertFalse(reconfiguration.isAlive(),
            "the replay threads were reconfigured, but applyConfigurationChange never returned");
        if (reconfigurationFailure.get() != null)
        {
          throw new AssertionError("the replay threads could not be reconfigured",
              reconfigurationFailure.get());
        }

        /*
         * The change is being replayed by another thread, and the thread which held it is
         * gone: it gave the change back on its way out rather than take it with it. An
         * attempt made by the thread which held it would be the retry in place instead,
         * and a change which was never handed back is not replayed by anyone.
         */
        assertFalse(abandoningThread.isAlive(),
            "the replay thread which held the change must have stopped");
        Assertions.assertThat(replayingThread)
            .as("the change must be replayed by a thread which did not hold it")
            .isNotSameAs(abandoningThread);

        assertFalse(domain.getServerState().cover(csn),
            "a change a stopped replay thread never applied must not be in the ServerState");
        assertEquals(getMonitorAttrValue(baseDN, "replayed-updates-failed"), initialFailures,
            "a change which was handed back must not be counted as one this replica gave up on");
        assertNotNull(getEntry(tmp.getName(), 1, true),
            "the entry must not have been deleted by the delivery which was abandoned");

        /*
         * The delivery which was abandoned said what it did: an assured write which is
         * waiting on this replica must not be told that the change is in the data here,
         * because it is a change this replica is still asking for.
         *
         * What is read is the ack, not when it was published. The hand-back gives this
         * domain a new broker and the replication server waits for an ack by CSN rather
         * than by session, so an ack published after the hand-back reaches it all the
         * same: the order of the two is not what this asserts.
         */
        final AckMsg ack = awaitAck(broker, csn);
        assertTrue(ack.hasReplayError(),
            "the ack of the abandoned delivery must report the replay error rather than"
                + " be the plain ack a master would take for a durable write");
        assertFalse(ack.hasTimeout(),
            "the ack must be the one the abandoned delivery published, not the one the"
                + " replication server makes up when it gives up waiting for it");
        Assertions.assertThat(ack.getFailedServers())
            .as("the replica which abandoned the delivery must be the one it names")
            .containsExactly(domainSid);

        /*
         * Let the delivery which took over apply the change, and stop parking: an attempt
         * of that delivery which came back on a lock it could not take would be parked
         * again otherwise, with nothing left to release it.
         */
        parked.deregister();

        assertMonitorAttrValueEventually(baseDN, "replayed-updates-ok", initialApplied + 1,
            "the change must be applied by the delivery which took over from the abandoned one");
        /*
         * A change applied twice goes through the expected count on its way up, so the
         * value has to be seen to stay put rather than to be reached once - and for
         * longer than the session restart which would bring that second delivery, or the
         * assertion stops looking before what it is looking for could arrive.
         */
        assertMonitorAttrValueStays(baseDN, "replayed-updates-ok", initialApplied + 1,
            MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY,
            "the change must be applied exactly once");
        assertNull(DirectoryServer.getEntry(tmp.getName()), "the entry must have been deleted");

        /*
         * Abandoning a change is not giving up on it: the change is applied moments later,
         * so nothing is counted as failed and the administrator is not told that this
         * replica diverges.
         */
        assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures,
            MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY,
            "a change which was handed back must not be counted as one this replica gave up on");
        assertEquals(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE),
            initialAlerts,
            "a change which was handed back must not have this replica report a divergence");
      }
      finally
      {
        /*
         * Whatever happened above: no replay thread of this server may be left parked,
         * and the reconfiguration has to be over before the next one is started. The two
         * would otherwise race for the pool of replay threads, which belongs to the
         * server rather than to this test, and the one which creates its threads last
         * drops the ones the other had just started without ever stopping them.
         */
        parked.deregister();
        if (reconfiguration != null)
        {
          reconfiguration.join(SECONDS.toMillis(60));
          reconfigurationFinished = !reconfiguration.isAlive();
        }
        if (reconfigurationFinished)
        {
          setNumberOfReplayThreads(null);
        }
      }
      /*
       * Read here rather than asserted while cleaning up, where a failure would replace
       * the one the test was reporting. join() with a timeout returns the same whether
       * the thread is over or not, and the pool of replay threads belongs to the server:
       * a reconfiguration still inside stopReplayThreads() holds the monitor of
       * MultimasterReplication, which restoring the pool would wait on for as long as an
       * untimed join() takes, and one caught between stopping and creating orphans the
       * threads the restore had just started. So the pool is left alone and this says so.
       */
      assertTrue(reconfigurationFinished,
          "the replay thread reconfiguration never finished; the pool was left alone");
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Starts changing the number of replay threads of this server on a thread of its own.
   * <p>
   * It cannot run on the thread of the test: stopping the replay threads joins them, so it
   * does not return while one of them is parked in a change, which is the whole point of
   * this fixture.
   *
   * @param replayThreads how many replay threads the server must run, or {@code null} for
   *          as many as it computes on its own
   * @param failure where the reconfiguration reports what it ran into, if anything
   * @return the thread which is doing the reconfiguration
   */
  private static Thread startReplayThreadReconfiguration(
      final Integer replayThreads, final AtomicReference<Throwable> failure)
  {
    final Thread reconfiguration = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          setNumberOfReplayThreads(replayThreads);
        }
        catch (Throwable t)
        {
          failure.set(t);
        }
      }
    }, "replay thread reconfiguration");
    // A reconfiguration which never returns holds the monitor of MultimasterReplication:
    // let the fork end on it rather than have it kept alive by a thread of this test.
    reconfiguration.setDaemon(true);
    reconfiguration.start();
    return reconfiguration;
  }

  /**
   * Reads the acknowledgement of the provided change off the broker it was published on.
   * <p>
   * The messages which come first are discarded: this broker is told about everything the
   * replication server has for it, and the ack of one change is what is being looked for.
   *
   * @param broker the broker the change was published on
   * @param csn the change the ack is expected for
   * @return the ack of that change
   * @throws Exception if it never arrived
   */
  private static AckMsg awaitAck(final ReplicationBroker broker, final CSN csn) throws Exception
  {
    final long deadline = System.nanoTime() + SECONDS.toNanos(60);
    while (deadline - System.nanoTime() > 0)
    {
      final ReplicationMsg msg;
      try
      {
        msg = broker.receive();
      }
      catch (SocketTimeoutException e)
      {
        // The broker reads under a timeout of its own, which is far shorter than the
        // budget here: a quiet second is not an answer.
        continue;
      }
      if (msg == null)
      {
        // The broker stopped rather than timed out: there is nothing left to read from,
        // and reading it again would spin a core for the rest of the budget.
        throw new AssertionError("the session " + csn + " was published on is gone,"
            + " so the ack of that change can no longer arrive");
      }
      if (msg instanceof AckMsg && csn.equals(((AckMsg) msg).getCSN()))
      {
        return (AckMsg) msg;
      }
    }
    throw new AssertionError("the delivery of " + csn + " was never acknowledged");
  }

  /**
   * Waits for the delivery which took over from the abandoned one to be parked, reporting
   * what the reconfiguration ran into when that is why nothing was parked.
   * <p>
   * A reconfiguration which throws once it has stopped the replay threads leaves this
   * server with no replay thread at all: nothing can be parked then, and the timeout of
   * the wait would be reported in place of the failure which brought it about.
   *
   * @param parked the park the delivery is expected to be caught in
   * @param failure where the reconfiguration reports what it ran into, if anything
   * @return the thread which is replaying the parked operation
   * @throws Exception if no operation was parked in time
   */
  private static Thread awaitParkedOrReportReconfigurationFailure(
      final ParkedReplay parked, final AtomicReference<Throwable> failure) throws Exception
  {
    try
    {
      return parked.awaitParked(60, SECONDS);
    }
    catch (TimeoutException e)
    {
      final Throwable cause = failure.get();
      if (cause == null)
      {
        throw e;
      }
      final AssertionError error = new AssertionError(
          "the replay threads could not be reconfigured, so nothing was left to replay"
              + " the change which was handed back", cause);
      error.addSuppressed(e);
      throw error;
    }
  }

  /**
   * Changes the number of replay threads of this server, the way a change of
   * {@code num-update-replay-threads} does on a live server.
   *
   * @param replayThreads how many replay threads the server must run, or {@code null} for
   *          as many as it computes on its own
   */
  private static void setNumberOfReplayThreads(final Integer replayThreads)
  {
    final ReplicationSynchronizationProviderCfg cfg =
        mock(ReplicationSynchronizationProviderCfg.class);
    /*
     * An unstubbed mock hands out 0 replay threads and no connection timeout, and both
     * would outlive this test: the whole server shares one pool of replay threads. The
     * number of them is what this test is changing; the connection timeout is read back
     * from the running server, so that it is restored rather than restated.
     */
    when(cfg.getNumUpdateReplayThreads()).thenReturn(replayThreads);
    when(cfg.getConnectionTimeout())
        .thenReturn((long) MultimasterReplication.getConnectionTimeoutMS());
    multimasterReplication().applyConfigurationChange(cfg);
  }

  /** Returns the replication synchronization provider of the running server. */
  private static MultimasterReplication multimasterReplication()
  {
    // Read as an Object: the provider is declared with the configuration of its own type,
    // which is not the one the registry lists.
    for (Object provider : DirectoryServer.getSynchronizationProviders())
    {
      if (provider instanceof MultimasterReplication)
      {
        return (MultimasterReplication) provider;
      }
    }
    throw new AssertionError("this server runs no replication synchronization provider");
  }

  /**
   * Waits for the provided thread to be inside the {@code join()} of the replay threads it
   * is stopping.
   * <p>
   * Waiting for that, rather than for the thread to be started, is what puts the change in
   * the hands of a thread which has already been asked to stop: every replay thread is
   * asked to stop before the first of them is joined, so a thread which is joining has
   * asked the parked one. Where the thread waits is checked as well as that it waits: the
   * state on its own would be satisfied by any wait at all, including one taken before the
   * replay threads were asked to stop.
   *
   * @param reconfiguration the thread which is changing the number of replay threads
   * @param failure where that thread reports what it ran into, if anything
   * @throws Exception if it never reached the join
   */
  private static void awaitStoppingTheReplayThreads(
      final Thread reconfiguration, final AtomicReference<Throwable> failure) throws Exception
  {
    final long deadline = System.nanoTime() + SECONDS.toNanos(60);
    while (deadline - System.nanoTime() > 0)
    {
      if (reconfiguration.getState() == Thread.State.WAITING
          && isJoiningTheReplayThreads(reconfiguration))
      {
        return;
      }
      if (!reconfiguration.isAlive())
      {
        if (failure.get() != null)
        {
          throw new AssertionError(
              "the replay threads could not be reconfigured", failure.get());
        }
        fail("the replay threads were stopped without joining the one which holds a change");
      }
      // Sampling the stack of a running thread costs a handshake with it, so it is done
      // often enough to open the gate promptly and not so often as to slow the server
      // this test is watching: the thread it waits for is not going anywhere.
      Thread.sleep(10);
    }
    fail("the reconfiguration never reached the join() of the replay threads");
  }

  /** Returns whether the provided thread is waiting on the replay threads it stopped. */
  private static boolean isJoiningTheReplayThreads(final Thread reconfiguration)
  {
    for (final StackTraceElement frame : reconfiguration.getStackTrace())
    {
      if ("stopReplayThreads".equals(frame.getMethodName())
          && MultimasterReplication.class.getName().equals(frame.getClassName()))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Test case for [Issue 889]: every change which can not be replayed must be given up
   * on, not only the one which fails on its own.
   * <p>
   * A backend which is failing fails every change in flight, which is what this test
   * reproduces with two changes. A count kept for the last failed change only is reset
   * by each of them in turn, so the give up would never be reached and this replica
   * would restart its session to the replication server without end.
   */
  @Test
  public void everyChangeWhichCanNotBeReplayedIsGivenUpOn() throws Exception
  {
    testSetUp("everyChangeWhichCanNotBeReplayedIsGivenUpOn");
    logger.error(LocalizableMessage.raw("Starting replication test : everyChangeWhichCanNotBeReplayedIsGivenUpOn"));

    final int serverId = 13;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry first = TestCaseUtils.addEntry(
          "dn: uid=user.889.1," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.889.1",
          "cn: Aaccf Amar",
          "sn: Amar");
      Entry second = TestCaseUtils.addEntry(
          "dn: uid=user.889.2," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.889.2",
          "cn: Aaccf Amar",
          "sn: Amar");
      String firstUuid = getEntry(first.getName(), 1, true).parseAttribute("entryuuid").asString();
      String secondUuid = getEntry(second.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
      // Two changes have to be given up on here, so the budget is shortened the same way.
      setReplayGiveUpDelay(TEST_GIVE_UP_DELAY);
      try
      {
        ShortCircuitPlugin.registerShortCircuit(
            OperationType.DELETE, "PreParse", ResultCode.OTHER.intValue());

        final CSN firstCSN = gen.newCSN();
        final CSN secondCSN = gen.newCSN();
        broker.publish(new DeleteMsg(first.getName(), firstCSN, firstUuid));
        broker.publish(new DeleteMsg(second.getName(), secondCSN, secondUuid));

        TestTimer giveUpTimer = new TestTimer.Builder()
          .maxSleep(120, SECONDS)
          .sleepTimes(200, MILLISECONDS)
          .toTimer();
        giveUpTimer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertTrue(domain.getServerState().cover(firstCSN),
                "the replica did not give up on the first change it can never replay");
            assertTrue(domain.getServerState().cover(secondCSN),
                "the replica did not give up on the second change it can never replay");
          }
        });
        assertMonitorAttrValueEventually(baseDN, "replayed-updates-failed", initialFailures + 2,
            "both changes must be counted as failed, once each");
        /*
         * Two changes counted more than once each climb past +2, and the poll which lands
         * on it would pass: the value has to be seen to stay put.
         */
        assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures + 2,
            "both changes must be counted as failed, once each");
        assertNotNull(getEntry(first.getName(), 1, true), "the first entry must not have been deleted");
        assertNotNull(getEntry(second.getName(), 1, true), "the second entry must not have been deleted");
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
        resetReplayGiveUpDelay();
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Test case for [Issue 901]: a replica whose replay give-up budget is unlimited keeps
   * asking for a change it can not replay instead of ever recording it as replayed, and
   * the budget is read from the configuration for every decision - so lowering it takes
   * effect on the change which is failing right now, without this server being restarted.
   */
  @Test
  public void anUnlimitedReplayGiveUpDelayIsNeverSpent() throws Exception
  {
    testSetUp("anUnlimitedReplayGiveUpDelayIsNeverSpent");
    logger.error(LocalizableMessage.raw("Starting replication test : anUnlimitedReplayGiveUpDelayIsNeverSpent"));

    final int serverId = 18;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.901," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.901",
          "cn: Aaccf Amar",
          "sn: Amar");
      String uuid = getEntry(tmp.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
      // An operator who would rather have this domain stop than have it diverge: the
      // change is retried for as long as it keeps failing.
      setReplayGiveUpDelay("unlimited");
      try
      {
        ShortCircuitPlugin.registerShortCircuit(
            OperationType.DELETE, "PreParse", ResultCode.OTHER.intValue());

        final CSN csn = gen.newCSN();
        broker.publish(new DeleteMsg(tmp.getName(), csn, uuid));

        /*
         * One delivery burns IN_PLACE_REPLAY_ATTEMPTS short circuits before it is handed
         * back and the session is restarted, so twice that many of them is a change which
         * was delivered, given back and delivered again - the loop this replica is
         * deliberately left in.
         */
        TestTimer timer = new TestTimer.Builder()
          .maxSleep(60, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer();
        timer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertTrue(ShortCircuitPlugin.getShortCircuitCount(OperationType.DELETE, "PreParse")
                    > 2 * IN_PLACE_REPLAY_ATTEMPTS,
                "the change was not asked for again while the budget was unlimited");
          }
        });
        assertFalse(domain.getServerState().cover(csn),
            "a replica which never gives up must not record a change it did not apply");
        /*
         * The monitor attribute is the only signal this domain has left: a budget which
         * is never spent raises no alert and counts no failed replay, so the change this
         * replica keeps asking for is visible here and nowhere else. It counts changes
         * rather than deliveries, so the redeliveries above leave it at one.
         */
        assertMonitorAttrValueEventually(baseDN, "changes-with-failed-replay", 1,
            "the change which keeps failing must be counted, once, as a failing change");
        /*
         * The change was delivered at least twice by now, so a counter which was bumped
         * per delivery rather than per change would already be past the value which is
         * being watched: what this asserts is carried by the value itself rather than by
         * how long the window is, which is why the default number of samples is enough
         * here - a window covering a redelivery would have to outlast a backoff which has
         * been climbing since the first failure.
         */
        assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures,
            "no change may be counted as failed while the budget is unlimited");

        /*
         * The budget is read for every decision, so the failure which comes next spends
         * this one: the change this replica was holding on to is given up on without the
         * server, or the domain, being restarted for the new value to be seen.
         */
        setReplayGiveUpDelay("0ms");
        TestTimer giveUpTimer = new TestTimer.Builder()
          .maxSleep(120, SECONDS)
          .sleepTimes(200, MILLISECONDS)
          .toTimer();
        giveUpTimer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertTrue(domain.getServerState().cover(csn),
                "a budget which was lowered must be spent by the change which is failing");
          }
        });
        assertMonitorAttrValueEventually(baseDN, "replayed-updates-failed", initialFailures + 1,
            "the change which was given up on must be counted once");
        assertMonitorAttrValueEventually(baseDN, "changes-with-failed-replay", 0,
            "a change which was given up on leaves the pending changes and stops being counted");
        assertNotNull(getEntry(tmp.getName(), 1, true), "the entry must not have been deleted");
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
        resetReplayGiveUpDelay();
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * The result codes a replay is retried on rather than skipped: the storage failing to
   * serve the operation, and a lock which could not be taken (OPENDJ-885) - the ten
   * in-place attempts only yield to the thread holding it, so a lock held for a while
   * burns every one of them and the change is as absent from the data as after a storage
   * failure.
   */
  @DataProvider(name = "transientReplayFailures")
  public Object[][] transientReplayFailures()
  {
    return new Object[][] {
      { ResultCode.UNAVAILABLE, 14, "user.889.3" },
      { ResultCode.BUSY, 15, "user.889.4" },
    };
  }

  /**
   * Test case for [Issue 889]: a replay which fails on the server itself has the session
   * restarted and the change delivered again, and a failure which clears in the meantime
   * has the change applied exactly once, without the change being given up on and without
   * it being reported as failed.
   */
  @Test(dataProvider = "transientReplayFailures")
  public void transientReplayFailureIsRetriedAndTheChangeApplied(
      final ResultCode transientFailure, final int serverId, final String uid) throws Exception
  {
    testSetUp("transientReplayFailureIsRetriedAndTheChangeApplied." + uid);
    logger.error(LocalizableMessage.raw(
        "Starting replication test : transientReplayFailureIsRetriedAndTheChangeApplied "
            + transientFailure));

    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=" + uid + "," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: " + uid,
          "cn: Aaccf Amar",
          "sn: Amar");
      String uuid = getEntry(tmp.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
      final long initialReplayed = getMonitorAttrValue(baseDN, "replayed-updates-ok");
      final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);

      /*
       * The backend is unavailable the way it is while a rebuild is performed or while it
       * is offline (OPENDJ-49), and it stays unavailable for longer than the replay is
       * retried in place: the change is only applied if the session is restarted and the
       * replication server delivers it a second time.
       */
      try
      {
        // Registered inside the try which deregisters it: the plugin is consulted for
        // every delete in this server, so one left behind fails the tests which follow.
        ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE, "PreParse",
            transientFailure.intValue(), IN_PLACE_REPLAY_ATTEMPTS + 2);

        final CSN csn = gen.newCSN();
        broker.publish(new DeleteMsg(tmp.getName(), csn, uuid));

        assertNull(getEntry(tmp.getName(), 30000, false),
            "the change was not replayed once the backend served the operation again");
        Assertions.assertThat(ShortCircuitPlugin.getShortCircuitCount(OperationType.DELETE, "PreParse"))
            .as("the change must have been delivered again after the session was restarted")
            .isGreaterThan(IN_PLACE_REPLAY_ATTEMPTS);

        TestTimer timer = new TestTimer.Builder()
          .maxSleep(30, SECONDS)
          .sleepTimes(100, MILLISECONDS)
          .toTimer();
        timer.repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertTrue(domain.getServerState().cover(csn),
                "a change which was replayed must be recorded as replayed");
          }
        });
        assertMonitorAttrValueEventually(baseDN, "replayed-updates-ok", initialReplayed + 1,
            "the change must be recorded as replayed");
        /*
         * A change applied twice - the delivery which failed and the one which took over
         * from it, the OPENDJ-1115 regression the takeover is there to prevent - takes the
         * counter through +1 on its way to +2, so the value has to be seen to stay put
         * rather than to be reached once. It has to be watched for longer than the
         * session restart which brings that second delivery, too, or the assertion stops
         * looking before the delivery it is looking for could arrive.
         */
        assertMonitorAttrValueStays(baseDN, "replayed-updates-ok", initialReplayed + 1,
            MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY,
            "a change which was delivered again must be applied exactly once");
        assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures,
            MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY,
            "a change which was replayed after a transient failure must not count as failed");
        assertEquals(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE), initialAlerts,
            "a transient failure must not tell the administrator that this replica diverged");
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Test case for [Issue 889]: the result code the server puts on an internal error is
   * configurable and is not validated as a result code, so it can be set to one conflict
   * resolution knows how to solve. Such a change is left to conflict resolution, and when
   * that can not solve it either the change is retried as the storage failure it is -
   * recording it as replayed after one attempt would be issue #889 again.
   */
  @Test
  public void changeConflictResolutionCanNotSolveOnTheServerErrorCodeIsRetried() throws Exception
  {
    testSetUp("changeConflictResolutionCanNotSolveOnTheServerErrorCodeIsRetried");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : changeConflictResolutionCanNotSolveOnTheServerErrorCodeIsRetried"));

    final int serverId = 16;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.889.5," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.889.5",
          "cn: Aaccf Amar",
          "sn: Amar");
      String uuid = getEntry(tmp.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
      domain.resetUnreplayedChangeAlertThrottle();
      final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);

      /*
       * UNWILLING_TO_PERFORM is one of the codes solveNamingConflict(ModifyDNOperation)
       * solves, so it must not be treated as a failure of the server before conflict
       * resolution had its chance - and it is what the storage reports here.
       */
      // Put back whatever was configured, not the default: a suite which runs with
      // another server-error-result-code must not be rewritten by this test.
      final int previousServerErrorResultCode =
          getServerContext().getCoreConfigManager().getServerErrorResultCode().intValue();
      try
      {
        /*
         * Changed inside the try which puts it back: the result code this server reports
         * an internal error with is server-wide, so one left behind would change which
         * road every later replay of this suite takes.
         */
        setServerErrorResultCode(ResultCode.UNWILLING_TO_PERFORM.intValue());
        /*
         * The failure lasts longer than the attempts made in place, so the change is only
         * applied if it was left out of the ServerState and delivered again rather than
         * recorded as replayed once conflict resolution reported it could not be solved.
         */
        ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE, "PreParse",
            ResultCode.UNWILLING_TO_PERFORM.intValue(), IN_PLACE_REPLAY_ATTEMPTS + 2);

        final CSN csn = gen.newCSN();
        broker.publish(new DeleteMsg(tmp.getName(), csn, uuid));

        assertNull(getEntry(tmp.getName(), 120000, false),
            "the change was skipped rather than retried once the storage served the operation");
        Assertions.assertThat(ShortCircuitPlugin.getShortCircuitCount(OperationType.DELETE, "PreParse"))
            .as("the change must have been delivered again rather than recorded as replayed")
            .isGreaterThan(IN_PLACE_REPLAY_ATTEMPTS);
        assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures,
            MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY,
            "a change which was replayed in the end must not be counted as given up on");
        assertEquals(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE), initialAlerts,
            "a change which was replayed in the end must not tell the administrator that this replica diverged");
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
        setServerErrorResultCode(previousServerErrorResultCode);
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Test case for [Issue 889]: a change whose message can not be turned into an operation
   * must not hold this replica's ServerState back for good.
   * <p>
   * There is no operation to retry and no delivery which would decode any better, so the
   * change has to be skipped rather than left listed as the barrier: a change which stays
   * uncommitted holds back the ServerState - and every change which follows it, from
   * every master - and the delivery which would replace it is turned down while a replay
   * thread still owns it, so nothing would ever move it again.
   */
  @Test
  public void aChangeWhichCanNotBeDecodedIsNotLeftHoldingTheServerStateBack() throws Exception
  {
    testSetUp("aChangeWhichCanNotBeDecodedIsNotLeftHoldingTheServerStateBack");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeWhichCanNotBeDecodedIsNotLeftHoldingTheServerStateBack"));

    final int serverId = 17;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      CSNGenerator gen = new CSNGenerator(serverId, 0);

      Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.889.6," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.889.6",
          "cn: Aaccf Amar",
          "sn: Amar");
      String uuid = getEntry(tmp.getName(), 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
      domain.resetUnreplayedChangeAlertThrottle();
      final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);

      final CSN csn = gen.newCSN();
      broker.publish(undecodableModifyMsg(csn, tmp.getName(), uuid));

      TestTimer timer = new TestTimer.Builder()
        .maxSleep(60, SECONDS)
        .sleepTimes(200, MILLISECONDS)
        .toTimer();
      timer.repeatUntilSuccess(new CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          assertTrue(domain.getServerState().cover(csn),
              "a change which can never be decoded must not hold the ServerState back");
        }
      });
      assertMonitorAttrValueEventually(baseDN, "replayed-updates-failed", initialFailures + 1,
          "a change which could not be decoded must be counted as failed");
      assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures + 1,
          "a change which could not be decoded must be counted once");
      Assertions.assertThat(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE))
          .as("the administrator must be told that this replica now diverges")
          .isGreaterThan(initialAlerts);
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Builds a ModifyMsg which travels the protocol intact and can not be turned into an
   * operation.
   * <p>
   * The encoded modifications are carried as an opaque byte array and are only read by
   * {@code createOperation()}, so a message whose modifications are corrupt is decoded,
   * listed as pending and handed to a replay thread before it fails - which is the point
   * of this test.
   *
   * @param csn the CSN to give the change
   * @param dn the entry the change is on
   * @param entryUUID the UUID of that entry
   * @return a message whose replay can not build an operation
   * @throws Exception if the message could not be built
   */
  private ModifyMsg undecodableModifyMsg(CSN csn, DN dn, String entryUUID) throws Exception
  {
    final List<Modification> mods = generatemods("description", "the decoding must fail here");
    final byte[] bytes =
        new ModifyMsg(csn, dn, mods, entryUUID).getBytes(ProtocolVersion.getCurrentVersion());

    /*
     * Break the length of the attribute description inside the encoded modifications, so
     * that the ASN.1 reader runs past the end of them. The attribute name only appears
     * there, and the byte before it is the length it is read with.
     */
    final int attributeName = indexOf(bytes, "description".getBytes("UTF-8"));
    assertTrue(attributeName > 0, "the encoded modifications must carry the attribute name");
    bytes[attributeName - 1] = (byte) 0x7F;

    final ModifyMsg corrupted =
        (ModifyMsg) ReplicationMsg.generateMsg(bytes, ProtocolVersion.getCurrentVersion());
    try
    {
      corrupted.createOperation(getRootConnection());
      fail("this test needs a message which can not be turned into an operation");
    }
    catch (LDAPException | DecodeException expected)
    {
      /*
       * Which is what the replay of this message hits: the ASN.1 reader reports a
       * DecodeException, which RawModification.decode() reports as an LDAPException and
       * ModifyCommonMsg.decodeRawMods() lets through as it is when the over-read lands
       * between two modifications rather than inside one. The two are named rather than
       * caught as an Exception so that this test says what the message does, but neither
       * is what decides its fate: this change is given up on because no operation could
       * be built from it, and a failure of an operation which was built takes the other
       * road whatever it was thrown as, which
       * aChangeWhoseOperationWasBuiltIsNotGivenUpOnWhereItFailed pins.
       */
    }
    return corrupted;
  }

  /**
   * Test case for [Issue 889]: a change whose operation was built is delivered again
   * rather than recorded as replayed when the replay fails before that operation could
   * tell which change it carries.
   * <p>
   * Which of the two roads a failure takes is decided by the operation rather than by
   * its CSN: a message no operation could be built from will not build one on the next
   * delivery either, so it is given up on where it is reported, while an operation which
   * was built may well have reached the backend - so its change is kept out of the
   * ServerState and asked for again, wherever in the replay the failure happened. An
   * operation which can not be given the ManageDsaIT control the replay adds to every one
   * of them is that case: it throws before the CSN of the operation is read, so a give-up
   * keyed off that CSN would record a change which never reached the backend as replayed,
   * which is this issue by another route.
   */
  @Test
  public void aChangeWhoseOperationWasBuiltIsNotGivenUpOnWhereItFailed() throws Exception
  {
    testSetUp("aChangeWhoseOperationWasBuiltIsNotGivenUpOnWhereItFailed");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeWhoseOperationWasBuiltIsNotGivenUpOnWhereItFailed"));

    assertChangeIsDeliveredAgainAfter(ModifyMsgWhoseOperationRefusesAControl::new,
        18, "user.889.7", "the replay must fail once the operation is built");
  }

  /**
   * The errors a replay meets which say nothing about the changes which follow: a class
   * which could not be linked, and a stack which ran out on the entry being replayed. The
   * second is an error of the JVM, but the thread which met it is whole again once the
   * stack has unwound and the entry is what raised it, so ending the thread would have one
   * change this replica can not replay cost it one replay thread per delivery.
   * <p>
   * Each row builds its error where it is thrown rather than here, so that the stack trace
   * it carries is the one of the replay it unwound.
   */
  @DataProvider(name = "recoverableReplayErrors")
  public Object[][] recoverableReplayErrors()
  {
    return new Object[][] {
      { (Supplier<Error>) () -> new LinkageError("the replay of this change meets an Error"),
        19, "user.922.1", "a LinkageError" },
      { (Supplier<Error>) StackOverflowError::new, 23, "user.922.5", "a StackOverflowError" },
    };
  }

  /**
   * Test case for [Issue 922] and [Issue 923]: a change whose replay threw an Error is
   * given back, and the thread which was replaying it is still there to take the changes
   * which follow.
   * <p>
   * A change is owned by the replay thread which took it, and that ownership is what
   * keeps a change being replayed from being replayed a second time (OPENDJ-1115): every
   * later delivery of it is refused as a duplicate. Ownership is given back on the roads
   * which run to their end, so an Error - which unwinds the replay out of every one of
   * them - would leave the change listed, uncommitted and owned by a thread which is not
   * replaying it anymore: nobody could replay it, and this domain's ServerState would
   * never move past it again. The thread itself is not replaced once it ends, so the
   * shared replay queue would have one consumer fewer as well.
   */
  @Test(dataProvider = "recoverableReplayErrors")
  public void aChangeWhoseReplayThrewAnErrorIsDeliveredAgain(
      final Supplier<Error> error, int serverId, String uid, String what) throws Exception
  {
    testSetUp("aChangeWhoseReplayThrewAnErrorIsDeliveredAgain." + uid);
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeWhoseReplayThrewAnErrorIsDeliveredAgain " + uid));

    final int replayThreads = liveReplayThreads();
    assertChangeIsDeliveredAgainAfter(
        (csn, dn, mods, entryUUID) ->
            new ModifyMsgWhoseReplayThrows(csn, dn, mods, entryUUID, error),
        serverId, uid, "the replay of this change throws " + what);
    assertEquals(liveReplayThreads(), replayThreads,
        "an Error met in a replay must not end the thread which met it (issue #923)");
  }

  /**
   * Test case for [Issue 922] and [Issue 923]: the change a replay thread was replaying
   * when the JVM ran out of memory is given back before the error is left to end that
   * thread.
   * <p>
   * An OutOfMemoryError is not turned into a failed replay and reported the way the other
   * errors are - building the report asks for more of what the JVM has run out of - and
   * the thread it unwinds is not replaced. The change it was replaying must not go with
   * it: it is handed back so that the delivery which follows can replay it.
   */
  @Test
  public void aChangeWhoseReplayRanOutOfMemoryIsDeliveredAgain() throws Exception
  {
    testSetUp("aChangeWhoseReplayRanOutOfMemoryIsDeliveredAgain");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeWhoseReplayRanOutOfMemoryIsDeliveredAgain"));

    /*
     * The replay thread this ends is not replaced - which is what makes the give-back
     * matter - and the pool it belongs to keeps the other threads it was created with, so
     * the changes of the tests which follow are replayed by those.
     */
    final int replayThreads = liveReplayThreads();
    assertChangeIsDeliveredAgainAfter(
        (csn, dn, mods, entryUUID) -> new ModifyMsgWhoseReplayThrows(csn, dn, mods, entryUUID,
            () -> new OutOfMemoryError("the JVM is out of memory")),
        20, "user.922.2", "the replay of this change meets an OutOfMemoryError");
    /*
     * The change is given back, and so replayed by another thread, while the thread which
     * met the error is still unwinding - through the session restart, and then through the
     * uncaught exception handler which raises the alert - so its end is waited for rather
     * than read off the pool the moment the change lands.
     */
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(30, SECONDS)
      .sleepTimes(200, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertEquals(liveReplayThreads(), replayThreads - 1,
            "an OutOfMemoryError must end the thread which met it, and nothing replaces it");
      }
    });
  }

  /**
   * Test case for [Issue 922]: a change whose ack threw on the way out of the replay is
   * given back.
   * <p>
   * The ack of a delivery is published in a finally which every road out of the replay
   * runs through, the roads which give the change back among them, and it is published on
   * the session that delivery came over - which is being torn down when the replay of the
   * change failed. A throw there steps over the give-back which follows it, and leaves the
   * change owned by a thread which is not replaying it anymore.
   * <p>
   * The throw is an Error here, so this also pins the widened catch of the replay thread
   * (issue #923): a thread which ends on it is one the pool never replaces.
   */
  @Test
  public void aChangeWhoseAckThrewOnTheWayOutIsDeliveredAgain() throws Exception
  {
    testSetUp("aChangeWhoseAckThrewOnTheWayOutIsDeliveredAgain");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeWhoseAckThrewOnTheWayOutIsDeliveredAgain"));

    final int replayThreads = liveReplayThreads();
    assertChangeIsDeliveredAgainAfter(ModifyMsgWhoseAckThrows::new,
        21, "user.922.3", "the ack of this change throws on the way out of the replay");
    assertEquals(liveReplayThreads(), replayThreads,
        "an exception on the way to the ack must not end the thread which met it");
  }

  /**
   * Test case for [Issue 922]: a change whose replay keeps throwing an Error is given up
   * on rather than asked for forever.
   * <p>
   * An Error takes the road every other failed replay takes, so the failures it leaves
   * behind count against the give-up budget of the change: a change this replica can never
   * apply must not hold its ServerState - and every change which follows it, from every
   * master - back for good, whether its replay reported the failure or threw it.
   */
  @Test
  public void aChangeWhoseReplayKeepsThrowingAnErrorIsGivenUpOn() throws Exception
  {
    testSetUp("aChangeWhoseReplayKeepsThrowingAnErrorIsGivenUpOn");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeWhoseReplayKeepsThrowingAnErrorIsGivenUpOn"));

    Entry tmp = TestCaseUtils.addEntry(
        "dn: uid=user.922.4," + baseDN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.922.4",
        "cn: Aaccf Amar",
        "sn: Amar");
    final DN dn = tmp.getName();
    final String uuid = getEntry(dn, 1, true).parseAttribute("entryuuid").asString();

    final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
    final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
    domain.resetUnreplayedChangeAlertThrottle();
    final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);
    final long giveUpDelay = domain.getReplayGiveUpDelay();
    try
    {
      domain.setReplayGiveUpDelay(TEST_GIVE_UP_DELAY_IN_MS);

      final CSN csn = new CSNGenerator(22, TimeThread.getTime()).newCSN();
      final List<Modification> mods =
          generatemods("description", "the replay of this change keeps throwing");

      /*
       * Nothing sends this change again - it never travelled a session - so every delivery
       * of it is made here, until this replica gives up on the change it can not replay.
       */
      TestTimer timer = new TestTimer.Builder()
        .maxSleep(120, SECONDS)
        .sleepTimes(200, MILLISECONDS)
        .toTimer();
      timer.repeatUntilSuccess(new CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          if (!domain.getServerState().cover(csn))
          {
            domain.processUpdate(new ModifyMsgWhoseReplayThrows(csn, dn, mods, uuid,
                () -> new LinkageError("the replay of this change meets an Error")));
          }
          assertTrue(domain.getServerState().cover(csn),
              "a change whose replay keeps throwing must be given up on");
        }
      });
      assertMonitorAttrValueEventually(baseDN, "replayed-updates-failed", initialFailures + 1,
          "the change which was given up on must be counted as failed, once");
      Assertions.assertThat(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE))
          .as("the administrator must be told that this replica now diverges")
          .isGreaterThan(initialAlerts);
    }
    finally
    {
      domain.setReplayGiveUpDelay(giveUpDelay);
    }
  }

  /** A delivery of a change whose replay does not run to its end. */
  private interface FailingDelivery
  {
    ModifyMsg newDelivery(CSN csn, DN dn, List<Modification> mods, String entryUUID);
  }

  /**
   * Delivers a change whose replay does not run to its end, then checks that the change is
   * neither recorded as replayed nor lost: the delivery which follows must be able to
   * replay it.
   *
   * @param delivery the delivery whose replay is to be unwound
   * @param serverId the replica the change comes from, one per test so that the CSNs of
   *                 one are never covered by the ServerState another left behind
   * @param uid the entry the change is made on
   * @param description the value the change writes, once it is replayed
   */
  private void assertChangeIsDeliveredAgainAfter(
      FailingDelivery delivery, int serverId, String uid, final String description) throws Exception
  {
    Entry tmp = TestCaseUtils.addEntry(
        "dn: uid=" + uid + "," + baseDN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: " + uid,
        "cn: Aaccf Amar",
        "sn: Amar");
    final DN dn = tmp.getName();
    final String uuid = getEntry(dn, 1, true).parseAttribute("entryuuid").asString();

    final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
    final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
    domain.resetUnreplayedChangeAlertThrottle();
    final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);

    final CSN csn = new CSNGenerator(serverId, TimeThread.getTime()).newCSN();
    final List<Modification> mods = generatemods("description", description);

    domain.processUpdate(delivery.newDelivery(csn, dn, mods, uuid));

    /*
     * Long enough to outlast the session restart the failure asks for: a change which is
     * being asked for again is not in the data at any point of it.
     */
    for (int i = 0; i < MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY; i++)
    {
      assertFalse(domain.getServerState().cover(csn),
          "a change whose replay was unwound must be asked for again, not recorded as replayed");
      Thread.sleep(200);
    }
    assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures,
        "a change which is still to be delivered again must not be counted as given up on");
    assertEquals(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE),
        initialAlerts, "a change which is still to be delivered again must not be alerted on"
            + " as a divergence");

    /*
     * Nothing sends this change again - it never travelled a session - so the delivery
     * which takes over from the one which was unwound is made here, and it is made until
     * it is taken: a delivery is dropped rather than queued while the listener thread is
     * down, which it is for as long as the recovery is restarting the session. A delivery
     * of a change a replay thread owns is refused as the duplicate it is, so this is where
     * a change left owned by a thread which is gone never comes back.
     */
    TestTimer timer = new TestTimer.Builder()
      .maxSleep(60, SECONDS)
      .sleepTimes(200, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        if (!domain.getServerState().cover(csn))
        {
          domain.processUpdate(new ModifyMsg(csn, dn, mods, uuid));
        }
        assertTrue(domain.getServerState().cover(csn),
            "the change must be recorded as replayed once it has been delivered again");
      }
    });
    checkEntryHasAttributeValue(dn, "description", description, 30,
        "the change must be applied by the delivery which took over from the one which failed");
  }

  /**
   * Returns how many replay threads are running.
   * <p>
   * They are created when the first replication domain of this server is, and on a change
   * of their number: a thread which ends is not replaced, so the shared replay queue would
   * have one consumer fewer for every domain of the server for as long as it is up.
   */
  private static int liveReplayThreads()
  {
    int live = 0;
    for (Thread thread : Thread.getAllStackTraces().keySet())
    {
      if (thread.isAlive() && thread.getName().startsWith("Replica replay thread "))
      {
        live++;
      }
    }
    return live;
  }

  /**
   * A ModifyMsg whose replay throws an Error.
   * <p>
   * It is thrown where the operation is built, which is inside the replay and past the
   * point where the change was marked as being replayed by the thread which took it: what
   * this pins is the road out of a replay which no {@code catch} of the replay itself used
   * to run on.
   */
  private static final class ModifyMsgWhoseReplayThrows extends ModifyMsg
  {
    private final Supplier<Error> error;

    private ModifyMsgWhoseReplayThrows(
        CSN csn, DN dn, List<Modification> mods, String entryUUID, Supplier<Error> error)
    {
      super(csn, dn, mods, entryUUID);
      this.error = error;
    }

    @Override
    public ModifyOperation createOperation(InternalClientConnection connection, DN newDN)
    {
      throw error.get();
    }
  }

  /**
   * A ModifyMsg whose replay fails and whose ack throws on the way out of it.
   * <p>
   * Its operation is built and can not be prepared for its replay, the way
   * {@code ModifyMsgWhoseOperationRefusesAControl} has it, so the replay fails with the
   * change owned by the replay thread. The ack of the delivery is then published in the
   * finally every road out of the replay runs through, and this one throws there - which is
   * what a session being torn down does.
   */
  private static final class ModifyMsgWhoseAckThrows
      extends ModifyMsgWhoseOperationRefusesAControl
  {
    private ModifyMsgWhoseAckThrows(CSN csn, DN dn, List<Modification> mods, String entryUUID)
    {
      super(csn, dn, mods, entryUUID);
    }

    @Override
    public boolean isAssured()
    {
      /*
       * An Error rather than the exception a session being torn down raises: the two take
       * the same road out of the replay, and an Error is also the throw a replay thread
       * only lives through because its catch was widened to Throwable (issue #923).
       */
      throw new LinkageError("the ack of this delivery can not be published");
    }
  }

  /**
   * Test case for [Issue 928]: a modify whose entry DN does not parse is reported once
   * and stepped over rather than thrown on.
   * <p>
   * {@code ModifyOperationBasis.getEntryDN()} reports INVALID_DN_SYNTAX and returns null
   * when the raw DN of the operation does not parse, and the permissive-modify check the
   * replay makes before running the operation reads it. Comparing the DN this check looks
   * for with what that returns, rather than the other way round, is what keeps this from
   * being a NullPointerException thrown before the operation ran: the operation runs,
   * reports the syntax of its DN, and the change is stepped over like any other change
   * this replica can not apply. Thrown on instead, the change is kept out of the
   * ServerState and asked for again for the whole give-up window, with a stack trace per
   * delivery, where the syntax of its DN is a verdict on every delivery of it.
   */
  @Test
  public void aModifyWhoseEntryDNDoesNotParseIsReportedRatherThanThrownOn() throws Exception
  {
    testSetUp("aModifyWhoseEntryDNDoesNotParseIsReportedRatherThanThrownOn");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aModifyWhoseEntryDNDoesNotParseIsReportedRatherThanThrownOn"));

    Entry tmp = TestCaseUtils.addEntry(
        "dn: uid=user.928.1," + baseDN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.928.1",
        "cn: Aaccf Amar",
        "sn: Amar");
    final DN dn = tmp.getName();
    final String uuid = getEntry(dn, 1, true).parseAttribute("entryuuid").asString();

    final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
    final long initialFailures = getMonitorAttrValue(baseDN, "replayed-updates-failed");
    domain.resetUnreplayedChangeAlertThrottle();
    final int initialAlerts = DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE);

    final CSNGenerator gen = new CSNGenerator(19, TimeThread.getTime());
    final CSN csn = gen.newCSN();
    final List<Modification> mods =
        generatemods("description", "the entry DN of this change does not parse");

    domain.processUpdate(new ModifyMsgWithAnUnparseableOperationDN(csn, dn, mods, uuid));

    TestTimer timer = new TestTimer.Builder()
      .maxSleep(60, SECONDS)
      .sleepTimes(200, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertTrue(domain.getServerState().cover(csn),
            "a change whose entry DN does not parse must be stepped over,"
                + " not thrown on and asked for again");
      }
    });
    assertMonitorAttrValueEventually(baseDN, "replayed-updates-failed", initialFailures + 1,
        "a change this replica can not apply must be counted as failed");
    assertMonitorAttrValueStays(baseDN, "replayed-updates-failed", initialFailures + 1,
        MONITOR_ATTR_SAMPLES_ACROSS_A_REDELIVERY,
        "a change which was stepped over must be counted once rather than delivered again");
    Assertions.assertThat(DummyAlertHandler.getAlertCount(ALERT_TYPE_REPLICATION_UNREPLAYED_CHANGE))
        .as("the administrator must be told that this replica now diverges")
        .isGreaterThan(initialAlerts);
  }

  /**
   * Test case for [Issue 908]: a domain being disabled - for an LDIF import, a restore, or
   * a backend being taken offline - must not save its ServerState while a replay thread is
   * half way through applying one of its changes.
   * <p>
   * The change reaches the backend, so a ServerState which excludes it records nowhere
   * that it was applied: the replication server sends it again when the domain is enabled
   * back, and a change which is already in the data is replayed a second time - resolved
   * as a conflict, or left as a conflict entry when the changes around it were resent with
   * it and their dependency ordering was forgotten along with the pending changes.
   */
  @Test
  public void aChangeBeingAppliedIsRecordedBeforeTheDomainIsDisabled() throws Exception
  {
    testSetUp("aChangeBeingAppliedIsRecordedBeforeTheDomainIsDisabled");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : aChangeBeingAppliedIsRecordedBeforeTheDomainIsDisabled"));

    final int serverId = 19;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      final CSNGenerator gen = new CSNGenerator(serverId, 0);

      final Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.908," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.908",
          "cn: Aaccf Amar",
          "sn: Amar");
      final DN dn = tmp.getName();
      final String uuid = getEntry(dn, 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final CSN csn = gen.newCSN();
      boolean disableAttempted = false;
      try
      {
        /*
         * Park the replay inside op.run(): the pre-parse plugin point is reached once the
         * replay thread started applying the change and before the change reaches the
         * backend, which is the window this issue is about. The pre-operation point would
         * not do - it is not invoked for synchronization operations.
         */
        PausePreParsePlugin.pause(OperationType.DELETE, dn);
        broker.publish(new DeleteMsg(dn, csn, uuid));
        assertTrue(PausePreParsePlugin.awaitPaused(OperationType.DELETE, 60, SECONDS),
            "the replay thread never started applying the change");
        assertTrue(domain.isConnected(),
            "this test needs a domain which is still up when the change is being applied");

        /*
         * Let the parked replay finish once the domain is inside the wait for it, so that
         * the change reaches the backend while the ServerState is about to be saved. The
         * session is cut after the flag is set and immediately before that wait, and well
         * before the state is saved, so a domain which is not connected anymore is one which
         * is about to wait for this very change.
         *
         * Released a moment after that rather than on the disconnection itself, and this is
         * what makes the test decide rather than guess: a domain which does not wait - the
         * lock taken out of the replay, or the state saved before the wait as it was before
         * this fix - has saved its ServerState long before the delay is out, so the change
         * lands after that save and the assertion below reports it. Releasing on the
         * disconnection instead handed the replay the join of the listener thread as a head
         * start, which is enough for it to be recorded by a domain which never waited.
         *
         * The delay is spent inside the wait, so it costs this test nothing and holds
         * whatever budget it needs to be well under REPLAY_DRAIN_TIMEOUT_IN_MS.
         */
        final Thread releaser =
            releaseWhenDisconnected(domain, OperationType.DELETE, SETTLE_BEFORE_RELEASE_IN_MS);
        disableAttempted = true;
        try
        {
          domain.disable();
        }
        finally
        {
          releaser.join(SECONDS.toMillis(60));
        }

        /*
         * The entry is gone, so the change did reach the backend: getEntry() waits for it
         * and reports it, since a domain which did not wait for the replay lets it finish
         * a moment later rather than not at all.
         */
        getEntry(dn, 30000, false);
        /*
         * Read the ServerState which was saved rather than the one in memory: disable()
         * clears the in-memory one, and the saved one is what the domain reads back when
         * it is enabled again - and what the replication server resumes this replica from.
         * Read it before the domain is enabled back, or the change being sent again and
         * replayed a second time would make the state cover it either way, which is the
         * very outcome this test is about.
         */
        assertTrue(persistedServerState().cover(csn),
            "a change which reached the backend must be recorded in the saved ServerState");
      }
      finally
      {
        PausePreParsePlugin.release(OperationType.DELETE);
        if (disableAttempted)
        {
          /*
           * Only when disable() was reached, and whether or not it got to the end: setting
           * the flag is its first act, so a disable() which threw half way through still
           * left a domain which has to be enabled back. Enabling one which was never
           * disabled is what must not happen - it would reload the ServerState and start a
           * broker which is already running, behind the back of the tests which follow.
           */
          domain.enable();
        }
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Test case for [Issue 908]: a domain which can not get the replay of its changes to
   * finish goes down anyway rather than holding the administrative task which is taking it
   * down - an import, a restore, a backend being taken offline - for as long as a backend
   * which stopped answering takes to answer.
   * <p>
   * The change may then reach the backend without being recorded in the ServerState, which
   * is what the warning in the log says: the replication server sends it again once the
   * domain is enabled back, which this test also checks, since a domain which gave up on
   * the wait must still end up consistent.
   */
  @Test
  public void theDomainStopsWaitingForAReplayWhichDoesNotFinish() throws Exception
  {
    testSetUp("theDomainStopsWaitingForAReplayWhichDoesNotFinish");
    logger.error(LocalizableMessage.raw(
        "Starting replication test : theDomainStopsWaitingForAReplayWhichDoesNotFinish"));

    final int serverId = 20;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    try
    {
      final CSNGenerator gen = new CSNGenerator(serverId, 0);

      final Entry tmp = TestCaseUtils.addEntry(
          "dn: uid=user.908.2," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: user.908.2",
          "cn: Aaccf Amar",
          "sn: Amar");
      final DN dn = tmp.getName();
      final String uuid = getEntry(dn, 1, true).parseAttribute("entryuuid").asString();

      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      final CSN csn = gen.newCSN();
      final long drainTimeout = domain.getReplayDrainTimeout();
      boolean disableAttempted = false;
      try
      {
        /*
         * A replay which does not finish is waited out for as long as an operation can be
         * waiting for the entry it is on - the best part of twenty seconds: this test can
         * not, so the domain gives up on the wait after a moment instead. Set inside the
         * try which puts it back, like the pause below: both are the domain's and the
         * server's for as long as they are left behind.
         */
        domain.setReplayDrainTimeout(TEST_REPLAY_DRAIN_TIMEOUT_IN_MS);
        PausePreParsePlugin.pause(OperationType.DELETE, dn);
        broker.publish(new DeleteMsg(dn, csn, uuid));
        assertTrue(PausePreParsePlugin.awaitPaused(OperationType.DELETE, 60, SECONDS),
            "the replay thread never started applying the change");

        // The replay is parked and stays parked: the domain has to come down all the same.
        final long startedAt = System.nanoTime();
        disableAttempted = true;
        domain.disable();
        final long waitedMs = NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        /*
         * Only this test releases the pause, and it has not done so yet, so an operation
         * still parked here is one the domain came down without waiting for - which is what
         * the give-up is. Read before the finally below releases it.
         */
        Assertions.assertThat(PausePreParsePlugin.parkedCount(OperationType.DELETE))
            .as("the domain must have come down while the replay was still being applied")
            .isEqualTo(1);
        /*
          * Measured against the default this test overrode rather than against a copy of
          * its value: an override which stopped taking effect would have the domain wait
          * the whole default out, and that is what this has to catch.
          */
        assertTrue(waitedMs < drainTimeout,
            "the domain waited " + waitedMs + " ms for a replay it can not drain,"
                + " which is not short of the " + drainTimeout + " ms it waits by default");
        /*
         * And the wait was taken rather than skipped: the replay is parked for good, so a
         * domain which really waits for it spends the whole budget it was given. Without
         * this the test reports the same thing whether the domain waited for the changes in
         * flight or never waited for anything - the give-up is only half of what a bounded
         * wait is.
         */
        assertTrue(waitedMs >= TEST_REPLAY_DRAIN_TIMEOUT_IN_MS,
            "the domain came down in " + waitedMs + " ms, so it did not wait the "
                + TEST_REPLAY_DRAIN_TIMEOUT_IN_MS + " ms it was given for the replay of a"
                + " change which was still being applied");
      }
      finally
      {
        PausePreParsePlugin.release(OperationType.DELETE);
        domain.setReplayDrainTimeout(drainTimeout);
        if (disableAttempted)
        {
          domain.enable();
        }
      }

      /*
       * The entry goes away: the replay the domain gave up on was released by the finally
       * above and finished after the ServerState had been saved, which is what the give-up
       * costs. This says the change is in the data - not that it was delivered again, since
       * the delete which does it is the first replay rather than the second.
       */
      getEntry(dn, 30000, false);
      /*
       * The change is in the data and in no ServerState, so the replication server owns it
       * still and sends it again over the session which the domain being enabled back
       * brought up. Replaying it a second time is the cost of the wait running out, and
       * conflict resolution absorbs it - what must not happen is the replica staying behind
       * for good. The state coming to cover the CSN is what evidences that delivery: the
       * domain forgot the change with its pending changes, so nothing else records it.
       */
      TestTimer timer = new TestTimer.Builder()
        .maxSleep(60, SECONDS)
        .sleepTimes(200, MILLISECONDS)
        .toTimer();
      timer.repeatUntilSuccess(new CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          assertTrue(domain.getServerState().cover(csn),
              "the change must be recorded once it has been delivered again");
        }
      });
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Starts a thread which releases the operations parked by
   * {@link PausePreParsePlugin} once the domain has cut its session - which it does on its
   * way down, immediately before it waits for the replay of the changes in flight - plus a
   * delay which puts the release inside that wait rather than ahead of it.
   *
   * @param domain the domain which is about to be taken down
   * @param operation the type of operation the pause was registered for
   * @param settleInMs how long to wait after the session was cut before the parked
   *                   operations are released, which has to be well under the time the
   *                   domain waits for them and longer than the rest of {@code disable()} -
   *                   the session being cut is its first act, so the listener thread being
   *                   joined and the ServerState being saved are both inside this delay
   * @return the thread, already started
   */
  private Thread releaseWhenDisconnected(
      final LDAPReplicationDomain domain, final OperationType operation, final long settleInMs)
  {
    final Thread releaser = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        /*
         * Bounded, and a daemon: a domain which never goes down - because taking it down
         * threw - must not leave a thread spinning for the rest of the run. The pause has
         * a bound of its own, so the parked operation is released either way.
         */
        final long deadline = System.nanoTime() + SECONDS.toNanos(60);
        try
        {
          while (domain.isConnected() && System.nanoTime() - deadline < 0)
          {
            Thread.sleep(1);
          }
          /*
           * The session is cut, so the domain is on its way to the wait for the replay:
           * give it that long to get there and, if it is not waiting for anything, to save
           * the ServerState this change must be in.
           */
          Thread.sleep(settleInMs);
          PausePreParsePlugin.release(operation);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
        }
      }
    }, "issue 908 replay releaser");
    releaser.setDaemon(true);
    releaser.start();
    return releaser;
  }

  /**
   * Returns the ServerState of the test domain as it is saved in the backend.
   * <p>
   * That is the one the domain reads back when it is enabled again, and the one the
   * replication server resumes this replica from - the in-memory one is cleared by
   * {@code disable()}.
   *
   * @return the ServerState read from the base entry of the domain
   * @throws Exception if the base entry could not be read
   */
  private ServerState persistedServerState() throws Exception
  {
    final ServerState persisted = new ServerState();
    for (String value : getEntry(baseDN, 1, true).parseAttribute("ds-sync-state").asSetOfString())
    {
      persisted.update(new CSN(value));
    }
    return persisted;
  }

  /**
   * A ModifyMsg whose operation can not be prepared for its replay.
   * <p>
   * The operation is built - so the replay is past the point where a message is given up
   * on - and the list of request controls it carries can not be added to, so the
   * ManageDsaIT control the replay puts on every operation throws before
   * {@code OperationContext.getCSN(op)} is reached. Such a message can not travel the
   * protocol: {@code ModifyMsg.createOperation()} builds an operation whose controls can
   * be added to, so this one is handed to the domain rather than published.
   */
  private static class ModifyMsgWhoseOperationRefusesAControl extends ModifyMsg
  {
    private ModifyMsgWhoseOperationRefusesAControl(
        CSN csn, DN dn, List<Modification> mods, String entryUUID)
    {
      super(csn, dn, mods, entryUUID);
    }

    @Override
    public ModifyOperation createOperation(InternalClientConnection connection, DN newDN)
    {
      final ModifyOperation op = new ModifyOperationBasis(connection, nextOperationID(),
          nextMessageID(), Collections.<Control>emptyList(),
          ByteString.valueOfUtf8(getDN().toString()), new ArrayList<RawModification>());
      op.setAttachment(OperationContext.SYNCHROCONTEXT,
          new ModifyContext(getCSN(), getEntryUUID()));
      return op;
    }
  }

  /**
   * A ModifyMsg whose operation carries an entry DN which does not parse.
   * <p>
   * {@code ModifyOperationBasis.getEntryDN()} reports INVALID_DN_SYNTAX and returns null
   * for such an operation, which is what the permissive-modify check of the replay reads.
   * Such a message can not travel the protocol: the DN of a ModifyMsg is parsed on the
   * way in and the operation is built from its {@code toString()}, so this one is handed
   * to the domain rather than published.
   */
  private static final class ModifyMsgWithAnUnparseableOperationDN extends ModifyMsg
  {
    private ModifyMsgWithAnUnparseableOperationDN(
        CSN csn, DN dn, List<Modification> mods, String entryUUID)
    {
      super(csn, dn, mods, entryUUID);
    }

    @Override
    public ModifyOperation createOperation(InternalClientConnection connection, DN newDN)
    {
      final ModifyOperation op = new ModifyOperationBasis(connection, nextOperationID(),
          nextMessageID(), null, ByteString.valueOfUtf8("this is not a DN"),
          new ArrayList<RawModification>());
      op.setAttachment(OperationContext.SYNCHROCONTEXT,
          new ModifyContext(getCSN(), getEntryUUID()));
      return op;
    }
  }

  /**
   * Returns the offset of the first occurrence of {@code needle} in {@code haystack}, or
   * -1 when it does not occur.
   */
  private static int indexOf(byte[] haystack, byte[] needle)
  {
    for (int i = 0; i <= haystack.length - needle.length; i++)
    {
      int j = 0;
      while (j < needle.length && haystack[i + j] == needle[j])
      {
        j++;
      }
      if (j == needle.length)
      {
        return i;
      }
    }
    return -1;
  }

  /**
   * Sets how long the domain of this test retries the replay of a change before it gives
   * up on it, the way an administrator would.
   * <p>
   * A backend under maintenance is waited out for minutes, which no test can afford: the
   * budget is spent over a couple of deliveries instead. The domain reads the property for
   * every decision it makes, so this takes effect on a change which is failing right now,
   * and none of the values it takes stops or starts the session.
   * <p>
   * The domain outlives the test methods, so a test which shortens the budget puts it back
   * with {@link #resetReplayGiveUpDelay()} in a finally, and calls this one before that
   * try: the reset then only ever runs on an attribute which is there to be removed.
   *
   * @param delay
   *          the budget in the duration syntax of the property: {@code 2000ms},
   *          {@code 0ms} to give up as soon as one delivery of the change failed,
   *          {@code unlimited} never to give up on it
   */
  private void setReplayGiveUpDelay(String delay)
  {
    modifyDomainConfig(synchroServerEntry.getName(), REPLACE, ATTR_REPLAY_GIVE_UP_DELAY, delay);
  }

  /**
   * Puts the configured replay give-up budget of the domain of this test back, that is the
   * default of the property: the domain outlives the test methods, so a shortened budget
   * which is left behind is the next test's too.
   */
  private void resetReplayGiveUpDelay()
  {
    modifyDomainConfig(synchroServerEntry.getName(), DELETE, ATTR_REPLAY_GIVE_UP_DELAY);
  }

  /**
   * Enable or disable the receive status of a synchronization provider.
   *
   * @param syncConfigDN The DN of the synchronization provider configuration
   * entry.
   * @param enable Specifies whether the receive status should be enabled
   * or disabled.
   */
  private static void setReceiveStatus(DN syncConfigDN, boolean enable)
  {
    modifyDomainConfig(syncConfigDN, REPLACE, "ds-cfg-receive-status", enable ? "TRUE" : "FALSE");
  }

  /**
   * Test that the ReplicationDomain (plugin inside LDAP server) adjust
   * its internal CSN generator to the last CSN received. Steps:
   * - create a domain with the current date in the CSN generator
   * - make it receive an update with a CSN in the future
   * - do a local operation replicated on that domain
   * - check that the update generated for that operation has a CSN in the future.
   */
  @Test(enabled=true)
  public void csnGeneratorAdjust() throws Exception
  {
    testSetUp("csnGeneratorAdjust");
    logger.error(LocalizableMessage.raw("Starting synchronization test : CSNGeneratorAdjust"));

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    final int serverId = 88;
    ReplicationBroker broker =
        openReplicationSession(baseDN, serverId, 100, replServerPort, 1000);
    consumeAllMessages(broker); // clean leftover messages from lostHeartbeatFailover()
    try
    {
      final long inTheFuture = System.currentTimeMillis() + (3600 * 1000);
      CSNGenerator gen = new CSNGenerator(serverId, inTheFuture);

      // Create and publish an update message to add an entry.
      AddMsg addMsg = addMsg(gen, user3Entry, user3UUID, baseUUID);
      broker.publish(addMsg);

      // Check that the entry has not been created in the directory server.
      assertNotNull(getEntry(user3Entry.getName(), 1000, true),
          "The entry has not been created");

      // Modify the entry
      connection.processModify(modifyRequest(user3Entry.getName(), REPLACE, "telephonenumber", "01 02 45"));

      // See if the client has received the msg
      ReplicationMsg msg = broker.receive();
      Assertions.assertThat(msg).isInstanceOf(ModifyMsg.class);
      ModifyMsg modMsg = (ModifyMsg) msg;
      assertTrue(modMsg.getCSN().getTimeSec()-addMsg.getCSN().getTimeSec()<=1,
          "The MOD timestamp should have been adjusted to the ADD one");

      // Delete the entries to clean the database.
      broker.publish(
          new DeleteMsg(user3Entry.getName(), gen.newCSN(), user3UUID));

      // Check that the delete operation has been applied.
      assertNull(getEntry(user3Entry.getName(), 10000, false),
          "The DELETE replication message was not replayed");
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Consumes all the messages sent to this broker. This is useful at the start
   * of a test to avoid leftover messages from previous test runs.
   */
  private void consumeAllMessages(ReplicationBroker broker)
  {
    final List<ReplicationMsg> msgs = new ArrayList<>();
    try
    {
      while (true)
      {
        msgs.add(broker.receive());
      }
    }
    catch (SocketTimeoutException expectedAtSomeStage)
    {
      // this is expected to happen when there will not be any more messages to
      // consume from the socket
    }

    if (!msgs.isEmpty())
    {
      logger.error(LocalizableMessage.raw("Leftover messages from previous test runs " + msgs));
    }
  }
}
