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

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.extensions.DummyAlertHandler;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.HeartbeatThread;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TimeThread;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.replication.plugin.LDAPReplicationDomain.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/**
 * Test synchronization of update operations on the directory server and through
 * the replication server broker interface.
 */
@SuppressWarnings("javadoc")
public class UpdateOperationTest extends ReplicationTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
        + "ds-cfg-replication-server-id: 107\n";

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
        entry.getObjectClassAttribute(), entry.getAttributes(),
        new ArrayList<Attribute>());
  }

  /**
   * Tests whether the synchronization provider fails over when it loses
   * the heartbeat from the replication server.
   */
  @Test(groups = "slow")
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
  @Test(enabled=true, groups="slow")
  public void modifyConflicts() throws Exception
  {
    testSetUp("modifyConflicts");
    final DN dn1 = DN.valueOf("cn=test1," + baseDN);
    final AttributeType attrType = DirectoryServer.getSchema().getAttributeType("displayname");
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
      List<Attribute> attrs = entry.getAttribute(entryuuidType);
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
      attrs = entry.getAttribute(attrType);
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
      attrs = entry.getAttribute(attrType);

      // there should not be a value (delete at time t2)
      assertNull(attrs);
      assertEquals(getMonitorDelta(), 1);
    }
    finally
    {
      broker.stop();
    }
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
  @Test(enabled=true, groups="slow")
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
    assertNull(getEntry(personWithSecondUniqueID.getName(), 10000, false),
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
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
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
      assertEquals(getMonitorDelta(), 1);
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
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());

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
    timer.repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        assertNotEquals(getMonitorDelta() , 0);
        return null;
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
    return !entry.getAttribute("ds-sync-confict").isEmpty();
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
        timer.repeatUntilSuccess(new Callable<Void>()
        {
          @Override
          public Void call() throws Exception
          {
            assertNotEquals(getMonitorAttrValue(baseDN, "replayed-updates"), initialCount);
            return null;
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
   * Enable or disable the receive status of a synchronization provider.
   *
   * @param syncConfigDN The DN of the synchronization provider configuration
   * entry.
   * @param enable Specifies whether the receive status should be enabled
   * or disabled.
   */
  private static void setReceiveStatus(DN syncConfigDN, boolean enable)
  {
    String attrValue = enable ? "TRUE" : "FALSE";
    ModifyRequest request = modifyRequest(syncConfigDN, REPLACE, "ds-cfg-receive-status", attrValue);
    ModifyOperation modOp = getRootConnection().processModify(request);
    assertEquals(modOp.getResultCode(), ResultCode.SUCCESS, "Cannot set receive status");
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
      assertEquals(addMsg.getCSN().getTimeSec(),
          modMsg.getCSN().getTimeSec(),
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
