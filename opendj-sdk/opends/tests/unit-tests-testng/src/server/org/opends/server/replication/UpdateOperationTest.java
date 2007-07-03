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

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.HeartbeatThread;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.RDN;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test synchronization of update operations on the directory server and through
 * the replication server broker interface.
 */
public class UpdateOperationTest extends ReplicationTestCase
{
  /**
   * An entry with a entryUUID
   */
  private Entry personWithUUIDEntry;
  private Entry personWithSecondUniqueID;

  private String baseUUID;

  private String user1dn;

  private String user1entrysecondUUID;

  private String user1entryUUID;

  /**
   * A "person" entry
   */
  protected Entry personEntry;
  private int replServerPort;
  private String domain1uid;
  private String domain2uid;
  private String domain3uid;
  private String domain1dn;
  private String domain2dn;
  private String domain3dn;
  private Entry domain1;
  private Entry domain2;
  private Entry domain3;

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    // Create backend top level entries
    String[] topEntries = new String[2];
    topEntries[0] = "dn: dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: domain\n";
    topEntries[1] = "dn: ou=People,dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
    for (String entryStr : topEntries)
    {
      addEntry(TestCaseUtils.entryFromLdifString(entryStr));
    }

    baseUUID = getEntryUUID(DN.decode("ou=People,dc=example,dc=com"));

    // top level synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // replication server
    String replServerLdif =
      "dn: cn=Replication Server, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server-config\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-server-port: " + replServerPort + "\n"
        + "ds-cfg-replication-server-id: 1\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // suffix synchronized
    String synchroServerLdif =
      "dn: cn=example, cn=domains, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-directory-server-id: 1\n" + "ds-cfg-receive-status: true\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    String personLdif = "dn: uid=user.1,ou=People,dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
        + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
    personEntry = TestCaseUtils.entryFromLdifString(personLdif);

    /*
     * The 2 entries defined in the following code are used for the naming
     * conflict resolution test (called namingConflicts)
     * They must have the same DN but different entryUUID.
     */
    user1entryUUID = "33333333-3333-3333-3333-333333333333";
    user1entrysecondUUID = "22222222-2222-2222-2222-222222222222";
    user1dn = "uid=user1,ou=People,dc=example,dc=com";
    String entryWithUUIDldif = "dn: "+ user1dn + "\n"
      + "objectClass: top\n" + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
      + "homePhone: 951-245-7634\n"
      + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
      + "mobile: 027-085-0537\n"
      + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
      + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
      + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
      + "street: 17984 Thirteenth Street\n"
      + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
      + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
      + "userPassword: password\n" + "initials: AA\n"
      + "entryUUID: " + user1entryUUID + "\n";
    personWithUUIDEntry = TestCaseUtils.entryFromLdifString(entryWithUUIDldif);

    String entryWithSecondUUID = "dn: "+ user1dn + "\n"
      + "objectClass: top\n" + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
      + "homePhone: 951-245-7634\n"
      + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
      + "mobile: 027-085-0537\n"
      + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
      + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
      + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
      + "street: 17984 Thirteenth Street\n"
      + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
      + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
      + "userPassword: password\n" + "initials: AA\n"
      + "entryUUID: "+ user1entrysecondUUID + "\n";
    personWithSecondUniqueID =
      TestCaseUtils.entryFromLdifString(entryWithSecondUUID);
    
    
    domain1dn = "dc=domain1,ou=People,dc=example,dc=com";
    domain2dn = "dc=domain2,dc=domain1,ou=People,dc=example,dc=com";
    domain3dn = "dc=domain3,dc=domain1,ou=People,dc=example,dc=com";
    domain1 = TestCaseUtils.entryFromLdifString(
        "dn:" + domain1dn + "\n" 
        + "objectClass:domain\n"
        + "dc:domain1");
    domain2 = TestCaseUtils.entryFromLdifString(
        "dn:" + domain2dn + "\n" 
        + "objectClass:domain\n"
        + "dc:domain2");
    domain3 = TestCaseUtils.entryFromLdifString(
        "dn:" + domain3dn + "\n" 
        + "objectClass:domain\n"
        + "dc:domain3");

    configureReplication();
  }

  /**
   * Add an entry in the datatbase
   *
   */
  private void addEntry(Entry entry) throws Exception
  {
    AddOperationBasis addOp = new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }
  
  /**
   * Delete an entry in the datatbase
   *
   */
  private void delEntry(DN dn) throws Exception
  {
    connection.processDelete(dn);
    assertNull(getEntry(dn, 1000, true));
  }

  /**
   * Tests whether the synchronization provider receive status can be disabled
   * then re-enabled.
   * FIXME Enable this test when broker suspend/resume receive are implemented.
   * @throws Exception
   */
  @Test(enabled=false)
  public void toggleReceiveStatus() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization test : toggleReceiveStatus" , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ReplicationBroker broker =
      openReplicationSession(baseDn, (short)2, 100, replServerPort, 1000, true);


    /*
     * Create a Change number generator to generate new changenumbers
     * when we need to send operation messages to the replicationServer.
     */
    ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 2, 0);


    // Disable the directory server receive status.
    setReceiveStatus(synchroServerEntry.getDN().toString(), false);


    // Create and publish an update message to add an entry.
    AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    entryList.add(personWithUUIDEntry.getDN());
    Entry resultEntry;

    // Check that the entry has not been created in the directory server.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 1000, true);
    assertNull(resultEntry,
        "The replication message was replayed while the server " +
             "receive status was disabled");

    // Enable the directory server receive status.
    setReceiveStatus(synchroServerEntry.getDN().toString(), true);

    // Create and publish another update message to add an entry.
    addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    // Check that the entry has been created in the directory server.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The replication message was not replayed after the server " +
             "receive status was enabled");

    // Delete the entries to clean the database.
    DeleteMsg delMsg =
      new DeleteMsg(personWithUUIDEntry.getDN().toString(),
          gen.newChangeNumber(), user1entryUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, false);

    // Check that the delete operation has been applied.
    assertNull(resultEntry,
        "The DELETE replication message was not replayed");
    broker.stop();
  }

  /**
   * Tests whether the synchronization provider fails over when it loses
   * the heartbeat from the replication server.
   */
  @Test(groups = "slow")
  public void lostHeartbeatFailover() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : lostHeartbeatFailover" , 1);

    cleanRealEntries();

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ReplicationBroker broker =
      openReplicationSession(baseDn, (short)2, 100, replServerPort, 1000, true);


    /*
     * Create a Change number generator to generate new changenumbers
     * when we need to send operation messages to the replicationServer.
     */
    ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 2, 0);


    // Create and publish an update message to add an entry.
    AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    entryList.add(personWithUUIDEntry.getDN());
    Entry resultEntry;

    // Check that the entry has been created in the directory server.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 30000, true);
    assertNotNull(resultEntry,
        "The ADD replication message was not replayed");

    // Send a first modify operation message.
    List<Modification> mods = generatemods("telephonenumber", "01 02 45");
    ModifyMsg modMsg = new ModifyMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN(), mods,
        user1entryUUID);
    broker.publish(modMsg);

    // Check that the modify has been replayed.
    boolean found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
                           "telephonenumber", "01 02 45", 10000, true);
    if (!found)
    {
      fail("The first modification was not replayed.");
    }

    // Simulate loss of heartbeats.
    HeartbeatThread.setHeartbeatsDisabled(true);
    Thread.sleep(3000);
    HeartbeatThread.setHeartbeatsDisabled(false);

    // Send a second modify operation message.
    mods = generatemods("description", "Description was changed");
    modMsg = new ModifyMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN(), mods,
        user1entryUUID);
    broker.publish(modMsg);

    // Check that the modify has been replayed.
    found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
                                   "description", "Description was changed",
                                   10000, true);
    if (!found)
    {
      fail("The second modification was not replayed.");
    }

    // Delete the entries to clean the database.
    DeleteMsg delMsg =
      new DeleteMsg(personWithUUIDEntry.getDN().toString(),
          gen.newChangeNumber(), user1entryUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, false);

    // Check that the delete operation has been applied.
    assertNull(resultEntry,
        "The DELETE replication message was not replayed");
    broker.stop();
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
  public void modifyConflicts()
       throws Exception
  {
    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");
    final DN dn1 = DN.decode("cn=test1," + baseDn.toString());
    final AttributeType attrType =
         DirectoryServer.getAttributeType("displayname");
    final AttributeType entryuuidType =
         DirectoryServer.getAttributeType("entryuuid");
    String monitorAttr = "resolved-modify-conflicts";

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ReplicationBroker broker =
      openReplicationSession(baseDn, (short)2, 100, replServerPort, 1000, true);

    // Add the first test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test1," + baseDn.toString(),
         "displayname: Test1",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test1",
         "sn: test"
       );

    // Read the entry back to get its UUID.
    Entry entry = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs = entry.getAttribute(entryuuidType);
    String entryuuid =
         attrs.get(0).getValues().iterator().next().getStringValue();

    // A change on a first server.
    ChangeNumber t1 = new ChangeNumber(1, (short) 0, (short) 3);

    // A change on a second server.
    ChangeNumber t2 = new ChangeNumber(2, (short) 0, (short) 4);

    // Simulate the ordering t2:replace:B followed by t1:add:A that
    updateMonitorCount(baseDn, monitorAttr);

    // Replay a replace of a value B at time t2 on a second server.
    Attribute attr = new Attribute(attrType.getNormalizedPrimaryName(), "B");
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    List<Modification> mods = new ArrayList<Modification>(1);
    mods.add(mod);
    ModifyMsg modMsg = new ModifyMsg(t2, dn1, mods, entryuuid);
    broker.publish(modMsg);

    Thread.sleep(2000);

    // Replay an add of a value A at time t1 on a first server.
    attr = new Attribute(attrType.getNormalizedPrimaryName(), "A");
    mod = new Modification(ModificationType.ADD, attr);
    mods = new ArrayList<Modification>(1);
    mods.add(mod);
    modMsg = new ModifyMsg(t1, dn1, mods, entryuuid);
    broker.publish(modMsg);

    Thread.sleep(2000);

    // Read the entry to see how the conflict was resolved.
    entry = DirectoryServer.getEntry(dn1);
    attrs = entry.getAttribute(attrType);
    String attrValue1 =
         attrs.get(0).getValues().iterator().next().getStringValue();

    // the value should be the last (time t2) value added
    assertEquals(attrValue1, "B");
    assertEquals(getMonitorDelta(), 1);

    // Simulate the ordering t2:delete:displayname followed by
    // t1:replace:displayname
    // A change on a first server.
    t1 = new ChangeNumber(3, (short) 0, (short) 3);

    // A change on a second server.
    t2 = new ChangeNumber(4, (short) 0, (short) 4);

    // Simulate the ordering t2:delete:displayname followed by t1:replace:A
    updateMonitorCount(baseDn, monitorAttr);

    // Replay an delete of attribute displayname at time t2 on a second server.
    attr = new Attribute(attrType);
    mod = new Modification(ModificationType.DELETE, attr);
    mods = new ArrayList<Modification>(1);
    mods.add(mod);
    modMsg = new ModifyMsg(t2, dn1, mods, entryuuid);
    broker.publish(modMsg);

    Thread.sleep(2000);

    // Replay a replace of a value A at time t1 on a first server.
    attr = new Attribute(attrType.getNormalizedPrimaryName(), "A");
    mod = new Modification(ModificationType.REPLACE, attr);
    mods = new ArrayList<Modification>(1);
    mods.add(mod);
    modMsg = new ModifyMsg(t1, dn1, mods, entryuuid);
    broker.publish(modMsg);

    Thread.sleep(2000);

    // Read the entry to see how the conflict was resolved.
    entry = DirectoryServer.getEntry(dn1);
    attrs = entry.getAttribute(attrType);

    // there should not be a value (delete at time t1)
    assertNull(attrs);
    assertEquals(getMonitorDelta(), 1);

    broker.stop();
  }

  /**
   * Tests the naming conflict resolution code.
   * In this test, the local server act both as an LDAP server and
   * a replicationServer that are inter-connected.
   *
   * The test creates an other session to the replicationServer using
   * directly the ReplicationBroker API.
   * It then uses this session to siomulate conflicts and therefore
   * test the naming conflict resolution code.
   */
  @Test(enabled=true, groups="slow")
  public void namingConflicts() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : namingConflicts" , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");
    String resolvedMonitorAttr = "resolved-naming-conflicts";
    String unresolvedMonitorAttr = "unresolved-naming-conflicts";

    /*
     * Open a session to the replicationServer using the ReplicationServer broker API.
     * This must use a serverId different from the LDAP server ID
     */
    ReplicationBroker broker =
      openReplicationSession(baseDn, (short)2, 100, replServerPort, 1000, true);

    /*
     * Create a Change number generator to generate new changenumbers
     * when we need to send operations messages to the replicationServer.
     */
    ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 2, 0);


    /*
     * Test that the conflict resolution code is able to find entries
     * that have been renamed by an other master.
     * To simulate this, create an entry with a given UUID and a given DN
     * then send a modify operation using another DN but the same UUID.
     * Finally check that the modify operation has been applied.
     */
    // create the entry with a given DN
    AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    // Check that the entry has been created in the local DS.
    Entry resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The send ADD replication message was not applied");
    entryList.add(resultEntry.getDN());

    // send a modify operation with the correct unique ID but another DN
    List<Modification> mods = generatemods("telephonenumber", "01 02 45");
    ModifyMsg modMsg = new ModifyMsg(gen.newChangeNumber(),
        DN.decode("cn=something,ou=People,dc=example,dc=com"), mods,
        user1entryUUID);
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(modMsg);

    // check that the modify has been applied as if the entry had been renamed.
    boolean found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
                           "telephonenumber", "01 02 45", 10000, true);
    if (found == false)
     fail("The modification has not been correctly replayed.");
    assertEquals(getMonitorDelta(), 1);

    /*
     * Test that the conflict resolution code is able to detect
     * that and entry have been renamed and that a new entry has
     * been created with the same DN but another entry UUID
     * To simulate this, create and entry with a given UUID and a given DN
     * then send a modify operation using the same DN but another UUID.
     * Finally check that the modify operation has not been applied to the
     * entry with the given DN.
     */

    //  create the entry with a given DN and unique ID
    addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID, baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    // Check that the entry has been created in the local DS.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The ADD replication message was not applied");
    entryList.add(resultEntry.getDN());

    // send a modify operation with a wrong unique ID but the same DN
    mods = generatemods("telephonenumber", "02 01 03 05");
    modMsg = new ModifyMsg(gen.newChangeNumber(),
        DN.decode(user1dn), mods, "10000000-9abc-def0-1234-1234567890ab");
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(modMsg);

    // check that the modify has not been applied
    found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
                           "telephonenumber", "02 01 03 05", 10000, false);
    if (found == true)
     fail("The modification has been replayed while it should not.");
    assertEquals(getMonitorDelta(), 1);


    /*
     * Test that the conflict resolution code is able to find entries
     * that have been renamed by an other master.
     * To simulate this, send a delete operation using another DN but
     * the same UUID has the entry that has been used in the tests above.
     * Finally check that the delete operation has been applied.
     */
    // send a delete operation with a wrong dn but the unique ID of the entry
    // used above
    DeleteMsg delMsg =
      new DeleteMsg("cn=anotherdn,ou=People,dc=example,dc=com",
          gen.newChangeNumber(), user1entryUUID);
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(delMsg);

    // check that the delete operation has been applied
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, false);

    assertNull(resultEntry,
        "The DELETE replication message was not replayed");
    assertEquals(getMonitorDelta(), 1);

    /*
     * Test that two adds with the same DN but a different unique ID result
     * cause a conflict and result in the second entry to be renamed.
     */

    //  create an entry with a given DN and unique ID
    addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID, baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    //  Check that the entry has been created in the local DS.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The ADD replication message was not applied");
    entryList.add(resultEntry.getDN());

    //  create an entry with the same DN and another unique ID
    addMsg = new AddMsg(gen.newChangeNumber(),
        personWithSecondUniqueID.getDN().toString(),
        user1entrysecondUUID, baseUUID,
        personWithSecondUniqueID.getObjectClassAttribute(),
        personWithSecondUniqueID.getAttributes(), new ArrayList<Attribute>());
    updateMonitorCount(baseDn, unresolvedMonitorAttr);
    broker.publish(addMsg);

    //  Check that the entry has been renamed and created in the local DS.
    resultEntry = getEntry(
        DN.decode("entryuuid=" + user1entrysecondUUID +" + " + user1dn),
        10000, true);
    assertNotNull(resultEntry,
        "The ADD replication message was not applied");
    assertEquals(getMonitorDelta(), 1);
    assertConflictAttribute(resultEntry);

    //  delete the entries to clean the database.
    delMsg =
      new DeleteMsg(personWithUUIDEntry.getDN().toString(),
          gen.newChangeNumber(), user1entryUUID);
    broker.publish(delMsg);
    delMsg =
      new DeleteMsg(personWithSecondUniqueID.getDN().toString(),
          gen.newChangeNumber(), user1entrysecondUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, false);

    // check that the delete operation has been applied
    assertNull(resultEntry,
        "The DELETE replication message was not replayed");
    /*
     * Check that and added entry is correctly added below it's
     * parent entry when this parent entry has been renamed.
     *
     * Simulate this by trying to add an entry below a DN that does not
     * exist but with a parent ID that exist.
     */
    addMsg = new AddMsg(gen.newChangeNumber(),
        "uid=new person,o=nothere,o=below,ou=People,dc=example,dc=com",
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(addMsg);

    //  Check that the entry has been renamed and created in the local DS.
    resultEntry = getEntry(
        DN.decode("uid=new person,ou=People,dc=example,dc=com"), 10000, true);
    assertNotNull(resultEntry,
        "The ADD replication message was not applied");
    assertEquals(getMonitorDelta(), 1);

    /*
     * Check that when replaying delete the naming conflict code
     * verify that the unique ID op the replayed operation is
     * the same as the unique ID of the entry with the given DN
     *
     * To achieve this send a delete operation with a correct DN
     * but a wrong unique ID.
     */

    delMsg =
      new DeleteMsg("uid=new person,ou=People,dc=example,dc=com",
          gen.newChangeNumber(), "11111111-9abc-def0-1234-1234567890ab");
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(delMsg);
    resultEntry = getEntry(
          DN.decode("uid=new person,ou=People,dc=example,dc=com"), 10000, true);

    // check that the delete operation has not been applied
    assertNotNull(resultEntry,
        "The DELETE replication message was replayed when it should not");
    assertEquals(getMonitorDelta(), 1);


    /*
     * Check that when replaying modify dn operations, the conflict
     * resolution code is able to find the new DN of the parent entry
     * if it has been renamed on another master.
     *
     * To simulate this try to rename an entry below an entry that does
     * not exist but giving the unique ID of an existing entry.
     */

    ModifyDNMsg  modDnMsg = new ModifyDNMsg(
        "uid=new person,ou=People,dc=example,dc=com", gen.newChangeNumber(),
        user1entryUUID, baseUUID, false,
        "uid=wrong, ou=people,dc=example,dc=com",
        "uid=newrdn");
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(modDnMsg);

    resultEntry = getEntry(
        DN.decode("uid=newrdn,ou=People,dc=example,dc=com"), 10000, true);

    // check that the operation has been correctly relayed
    assertNotNull(resultEntry,
      "The modify dn was not or badly replayed");
    assertEquals(getMonitorDelta(), 1);

    /*
     * same test but by giving a bad entry DN
     */

     modDnMsg = new ModifyDNMsg(
        "uid=wrong,ou=People,dc=example,dc=com", gen.newChangeNumber(),
        user1entryUUID, baseUUID, false, null, "uid=reallynewrdn");
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(modDnMsg);

    resultEntry = getEntry(
        DN.decode("uid=reallynewrdn,ou=People,dc=example,dc=com"), 10000, true);

    // check that the operation has been correctly relayed
    assertNotNull(resultEntry,
      "The modify dn was not or badly replayed");
    assertEquals(getMonitorDelta(), 1);

    /*
     * Check that conflicting entries are renamed when a
     * modifyDN is done with the same DN as an entry added on another server.
     */

    // add a second entry
    addMsg = new AddMsg(gen.newChangeNumber(),
        user1dn,
        user1entrysecondUUID,
        baseUUID,
        personWithSecondUniqueID.getObjectClassAttribute(),
        personWithSecondUniqueID.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    //  check that the second entry has been added
    resultEntry = getEntry(DN.decode(user1dn), 10000, true);

    // check that the add operation has been applied
    assertNotNull(resultEntry, "The add operation was not replayed");

    // try to rename the first entry
    modDnMsg = new ModifyDNMsg(user1dn, gen.newChangeNumber(),
                               user1entrysecondUUID, baseUUID, false,
                               baseDn.toString(), "uid=reallynewrdn");
    updateMonitorCount(baseDn, unresolvedMonitorAttr);
    broker.publish(modDnMsg);

   // check that the second entry has been renamed
    resultEntry = getEntry(
        DN.decode("entryUUID = " + user1entrysecondUUID + "+uid=reallynewrdn," +
            "ou=People,dc=example,dc=com"), 10000, true);
    assertNotNull(resultEntry, "The modifyDN was not or incorrectly replayed");
    assertEquals(getMonitorDelta(), 1);
    assertConflictAttribute(resultEntry);

    // delete the entries to clean the database
    delMsg =
      new DeleteMsg("uid=reallynewrdn,ou=People,dc=example,dc=com",
          gen.newChangeNumber(), user1entryUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(
        DN.decode("uid=reallynewrdn,ou=People,dc=example,dc=com"), 10000, false);

    //  check that the delete operation has been applied
    assertNull(resultEntry,
        "The DELETE replication message was not replayed");

    delMsg =
      new DeleteMsg("entryUUID = " + user1entrysecondUUID + "+" +
          DN.decode(user1dn).getRDN().toString() +
          ",ou=People,dc=example,dc=com",
          gen.newChangeNumber(), user1entrysecondUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(
          DN.decode("entryUUID = " + user1entrysecondUUID + "+" +
              DN.decode(user1dn).getRDN().toString() +
              ",ou=People,dc=example,dc=com"), 10000, false);

    // check that the delete operation has been applied
    assertNull(resultEntry,
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

    // - create parent entry 1 with baseDn1
    String[] topEntries = new String[1];
    topEntries[0] = "dn: ou=baseDn1,"+baseDn+"\n" + "objectClass: top\n"
    + "objectClass: organizationalUnit\n"
    + "entryUUID: 55555555-5555-5555-5555-555555555555\n";
    Entry entry;
    for (String entryStr : topEntries)
    {
      entry = TestCaseUtils.entryFromLdifString(entryStr);
      AddOperationBasis addOp = new AddOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
          entry.getUserAttributes(), entry.getOperationalAttributes());
      addOp.setInternalOperation(true);
      addOp.run();
      entryList.add(entry.getDN());
    }
    resultEntry = getEntry(
        DN.decode("ou=baseDn1,"+baseDn), 10000, true);
    assertNotNull(resultEntry,
        "Entry not added: ou=baseDn1,"+baseDn);

    // - create Add Msg for user1 with parent entry 1 UUID
    addMsg = new AddMsg(gen.newChangeNumber(),
        "uid=new person,ou=baseDn1,"+baseDn,
        user1entryUUID,
        getEntryUUID(DN.decode("ou=baseDn1,"+baseDn)),
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());

    // - MODDN parent entry 1 to baseDn2 in the LDAP server
    ModifyDNOperation modDNOp = new ModifyDNOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
        .nextMessageID(), null,
        DN.decode("ou=baseDn1,"+baseDn),
        RDN.decode("ou=baseDn2"), true,
        baseDn);
    modDNOp.run();
    entryList.add(DN.decode("ou=baseDn2,"+baseDn));

    resultEntry = getEntry(
        DN.decode("ou=baseDn2,"+baseDn), 10000, true);
    assertNotNull(resultEntry,
        "Entry not moved from ou=baseDn1,"+baseDn+" to ou=baseDn2,"+baseDn);

    // - add new parent entry 2 with baseDn1
    String p2 = "dn: ou=baseDn1,"+baseDn+"\n" + "objectClass: top\n"
         + "objectClass: organizationalUnit\n"
         + "entryUUID: 66666666-6666-6666-6666-666666666666\n";
    entry = TestCaseUtils.entryFromLdifString(p2);
    AddOperationBasis addOp = new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
        .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    entryList.add(entry.getDN());


    // - publish msg
    updateMonitorCount(baseDn, resolvedMonitorAttr);
    broker.publish(addMsg);

    // - check that the Dn has been changed to baseDn2
    resultEntry = getEntry(
        DN.decode("uid=new person,ou=baseDn1,"+baseDn), 10000, false);
    assertNull(resultEntry,
        "The ADD replication message was applied under ou=baseDn1,"+baseDn);

    resultEntry = getEntry(
        DN.decode("uid=new person,ou=baseDn2,"+baseDn), 10000, true);
    assertNotNull(resultEntry,
        "The ADD replication message was NOT applied under ou=baseDn2,"+baseDn);
    entryList.add(resultEntry.getDN());
    assertEquals(getMonitorDelta(), 1);
    
    //
    // Check that when a delete is conflicting with Add of some entries
    // below the deleted entries, the child entry that have been added
    // before the deleted is replayed gets renamed correctly.
    //
    
    // add domain1 entry with 2 childs domain2 and domain3
    addEntry(domain1);
    domain1uid = getEntryUUID(DN.decode(domain1dn));
    addEntry(domain2);
    domain2uid = getEntryUUID(DN.decode(domain2dn));
    addEntry(domain3);
    domain3uid = getEntryUUID(DN.decode(domain3dn));
    DN conflictDomain2dn = DN.decode(
        "entryUUID = " + domain2uid + "+dc=domain2,ou=people,dc=example,dc=com");
    DN conflictDomain3dn = DN.decode(
        "entryUUID = " + domain3uid + "+dc=domain3,ou=people,dc=example,dc=com");
 
    updateMonitorCount(baseDn, unresolvedMonitorAttr);
    
    // delete domain1
    delMsg = new DeleteMsg(domain1dn, gen.newChangeNumber(), domain1uid);
    broker.publish(delMsg);

    // check that the domain1 has correctly been deleted
    assertNull(getEntry(DN.decode(domain1dn), 10000, false),
        "The DELETE replication message was not replayed");
    
    // check that domain2 and domain3 have been renamed
    assertNotNull(getEntry(conflictDomain2dn, 1000, true),
        "The conflicting entries were not created");
    assertNotNull(getEntry(conflictDomain3dn, 1000, true),
        "The conflicting entries were not created");

    // check that the 2 conflicting entries have been correctly marked
    assertTrue(checkEntryHasAttribute(conflictDomain2dn,
        ReplicationDomain.DS_SYNC_CONFLICT, domain1dn, 1000, true));
    assertTrue(checkEntryHasAttribute(conflictDomain3dn,
        ReplicationDomain.DS_SYNC_CONFLICT, domain1dn, 1000, true));
    
    // check that unresolved conflict count has been incremented
    assertEquals(getMonitorDelta(), 1);
    
    // delete the resulting entries for the next test
    delEntry(conflictDomain2dn);
    delEntry(conflictDomain3dn);
    
    //
    // Check that when an entry is added on one master below an entry
    // that is currently deleted on another master, the replay of the
    // add on the second master cause the added entry to be renamed
    //
    addMsg = new AddMsg(gen.newChangeNumber(), domain2dn, domain2uid,
        domain1uid,
        domain2.getObjectClassAttribute(),
        domain2.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);
    
    // check that conflict entry was created
    assertNotNull(getEntry(conflictDomain2dn, 1000, true),
      "The conflicting entries were not created");
    
    // check that the entry have been correctly marked as conflicting.
    assertTrue(checkEntryHasAttribute(conflictDomain2dn,
        ReplicationDomain.DS_SYNC_CONFLICT, domain2dn, 1000, true));
    
    // check that unresolved conflict count has been incremented
    assertEquals(getMonitorDelta(), 1);
    
    broker.stop();
  }

  /**
   * Check that the given entry does contain the attribute that mark the
   * entry as conflicting.
   *
   * @param entry The entry that needs to be asserted.
   *
   * @return A boolean indicating if the entry is correctly marked.
   */
  private boolean assertConflictAttribute(Entry entry)
  {
    List<Attribute> attrs = entry.getAttribute("ds-sync-confict");

    if (attrs == null)
      return false;
    else
      return true;
  }

  @DataProvider(name="assured")
  public Object[][] getAssuredFlag()
  {
    return new Object[][] { { false }, {true} };
  }

  /**
   * Tests done using directly the ReplicationBroker interface.
   */
  @Test(enabled=false, dataProvider="assured")
  public void updateOperations(boolean assured) throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : updateOperations " + assured , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    cleanRealEntries();

    ReplicationBroker broker =
      openReplicationSession(baseDn, (short) 27, 100, replServerPort, 1000, true);
    try {
      ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 27, 0);

      /*
       * Test that operations done on this server are sent to the
       * replicationServer and forwarded to our replicationServer broker session.
       */

      // Create an Entry (add operation)
      Entry tmp = personEntry.duplicate(false);
      AddOperationBasis addOp = new AddOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, tmp.getDN(),
          tmp.getObjectClasses(), tmp.getUserAttributes(),
          tmp.getOperationalAttributes());
      addOp.run();
      entryList.add(personEntry.getDN());
      assertTrue(DirectoryServer.entryExists(personEntry.getDN()),
      "The Add Entry operation failed");

      if (ResultCode.SUCCESS.equals(addOp.getResultCode()))
      {
        // Check if the client has received the msg
        ReplicationMessage msg = broker.receive();
        assertTrue(msg instanceof AddMsg,
        "The received replication message is not an ADD msg");
        AddMsg addMsg =  (AddMsg) msg;

        Operation receivedOp = addMsg.createOperation(connection);
        assertTrue(OperationType.ADD.compareTo(receivedOp.getOperationType()) == 0,
        "The received replication message is not an ADD msg");

        assertEquals(DN.decode(addMsg.getDn()),personEntry.getDN(),
        "The received ADD replication message is not for the excepted DN");
      }

      // Modify the entry
      List<Modification> mods = generatemods("telephonenumber", "01 02 45");

      ModifyOperationBasis modOp = new ModifyOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, personEntry.getDN(), mods);
      modOp.setInternalOperation(true);
      modOp.run();

      // See if the client has received the msg
      ReplicationMessage msg = broker.receive();
      assertTrue(msg instanceof ModifyMsg,
      "The received replication message is not a MODIFY msg");
      ModifyMsg modMsg = (ModifyMsg) msg;

      modMsg.createOperation(connection);
      assertTrue(DN.decode(modMsg.getDn()).compareTo(personEntry.getDN()) == 0,
      "The received MODIFY replication message is not for the excepted DN");

      // Modify the entry DN
      DN newDN = DN.decode("uid= new person,ou=People,dc=example,dc=com") ;
      ModifyDNOperation modDNOp = new ModifyDNOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, personEntry.getDN(), RDN
          .decode("uid=new person"), true, DN
          .decode("ou=People,dc=example,dc=com"));
      modDNOp.run();
      assertTrue(DirectoryServer.entryExists(newDN),
      "The MOD_DN operation didn't create the new person entry");
      assertFalse(DirectoryServer.entryExists(personEntry.getDN()),
      "The MOD_DN operation didn't delete the old person entry");

      // See if the client has received the msg
      msg = broker.receive();
      assertTrue(msg instanceof ModifyDNMsg,
      "The received replication message is not a MODIFY DN msg");
      ModifyDNMsg moddnMsg = (ModifyDNMsg) msg;
      moddnMsg.createOperation(connection);

      assertTrue(DN.decode(moddnMsg.getDn()).compareTo(personEntry.getDN()) == 0,
      "The received MODIFY_DN message is not for the excepted DN");

      // Delete the entry
      DeleteOperationBasis delOp = new DeleteOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, DN
          .decode("uid= new person,ou=People,dc=example,dc=com"));
      delOp.run();
      assertFalse(DirectoryServer.entryExists(newDN),
      "Unable to delete the new person Entry");

      // See if the client has received the msg
      msg = broker.receive();
      assertTrue(msg instanceof DeleteMsg,
      "The received replication message is not a MODIFY DN msg");
      DeleteMsg delMsg = (DeleteMsg) msg;
      delMsg.createOperation(connection);
      assertTrue(DN.decode(delMsg.getDn()).compareTo(DN
          .decode("uid= new person,ou=People,dc=example,dc=com")) == 0,
      "The received DELETE message is not for the excepted DN");

      /*
       * Now check that when we send message to the ReplicationServer
       * and that they are received and correctly replayed by the server.
       *
       * Start by testing the Add message reception
       */
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
          personWithUUIDEntry.getDN().toString(),
          user1entryUUID, baseUUID,
          personWithUUIDEntry.getObjectClassAttribute(),
          personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
      if (assured)
        addMsg.setAssured();
      broker.publish(addMsg);

      /*
       * Check that the entry has been created in the local DS.
       */
      Entry resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
      assertNotNull(resultEntry,
      "The send ADD replication message was not applied for "+personWithUUIDEntry.getDN().toString());
      entryList.add(resultEntry.getDN());

      /*
       * Test the reception of Modify Msg
       */
      modMsg = new ModifyMsg(gen.newChangeNumber(), personWithUUIDEntry.getDN(),
          mods, user1entryUUID);
      if (assured)
        modMsg.setAssured();
      broker.publish(modMsg);

      boolean found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
          "telephonenumber", "01 02 45", 10000, true);

      if (found == false)
        fail("The modification has not been correctly replayed.");

      /*
       * Test the Reception of Modify Dn Msg
       */
      moddnMsg = new ModifyDNMsg(personWithUUIDEntry.getDN().toString(),
          gen.newChangeNumber(),
          user1entryUUID, null,
          true, null, "uid= new person");
      if (assured)
        moddnMsg.setAssured();
      broker.publish(moddnMsg);

      resultEntry = getEntry(
          DN.decode("uid= new person,ou=People,dc=example,dc=com"), 10000, true);

      assertNotNull(resultEntry,
      "The modify DN replication message was not applied");

      /*
       * Test the Reception of Delete Msg
       */
      delMsg = new DeleteMsg("uid= new person,ou=People,dc=example,dc=com",
          gen.newChangeNumber(), user1entryUUID);
      if (assured)
        delMsg.setAssured();
      broker.publish(delMsg);
      resultEntry = getEntry(
          DN.decode("uid= new person,ou=People,dc=example,dc=com"), 10000, false);

      assertNull(resultEntry,
      "The DELETE replication message was not replayed");
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   *  Get the entryUUID for a given DN.
   *
   * @throws Exception if the entry does not exist or does not have
   *                   an entryUUID.
   */
  private String getEntryUUID(DN dn) throws Exception
  {
    Entry newEntry;
    int count = 10;
    if (count<1)
      count=1;
    String found = null;
    while ((count> 0) && (found == null))
    {
      Thread.sleep(100);

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
        newEntry = DirectoryServer.getEntry(dn);

        if (newEntry != null)
        {
          List<Attribute> tmpAttrList = newEntry.getAttribute("entryuuid");
          Attribute tmpAttr = tmpAttrList.get(0);

          LinkedHashSet<AttributeValue> vals = tmpAttr.getValues();

          for (AttributeValue val : vals)
          {
            found = val.getStringValue();
            break;
          }
        }
      }
      finally
      {
        LockManager.unlock(dn, lock);
      }
      count --;
    }
    if (found == null)
      throw new Exception("Entry: " + dn + " Could not be found.");
    return found;
  }

  /**
   * Test case for
   * [Issue 635] NullPointerException when trying to access non existing entry.
   */
  @Test(enabled=true)
  public void deleteNoSuchObject() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : deleteNoSuchObject" , 1);

    DN dn = DN.decode("cn=No Such Object,ou=People,dc=example,dc=com");
    DeleteOperationBasis op =
         new DeleteOperationBasis(connection,
                             InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(), null,
                             dn);
    op.run();
    assertEquals(op.getResultCode(), ResultCode.NO_SUCH_OBJECT);
  }

  /**
   * Test case for
   * [Issue 798]  break infinite loop when problems with naming resolution
   *              conflict.
   */
  @Test(enabled=false)
  public void infiniteReplayLoop() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : infiniteReplayLoop" , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    Thread.sleep(2000);
    ReplicationBroker broker =
      openReplicationSession(baseDn, (short) 11, 100, replServerPort, 1000, true);
    try
    {
      ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 11, 0);

      // Create a test entry.
      String personLdif = "dn: uid=user.2,ou=People,dc=example,dc=com\n"
          + "objectClass: top\n" + "objectClass: person\n"
          + "objectClass: organizationalPerson\n"
          + "objectClass: inetOrgPerson\n" + "uid: user.2\n"
          + "homePhone: 951-245-7634\n"
          + "description: This is the description for Aaccf Amar.\n"
          + "st: NC\n"
          + "mobile: 027-085-0537\n"
          + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
          + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
          + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
          + "street: 17984 Thirteenth Street\n"
          + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
          + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
          + "userPassword: password\n" + "initials: AA\n";
      Entry tmp = TestCaseUtils.entryFromLdifString(personLdif);
      AddOperationBasis addOp =
           new AddOperationBasis(connection,
                            InternalClientConnection.nextOperationID(),
                            InternalClientConnection.nextMessageID(),
                            null, tmp.getDN(), tmp.getObjectClasses(),
                            tmp.getUserAttributes(),
                            tmp.getOperationalAttributes());
      addOp.run();
      assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);
      entryList.add(tmp.getDN());

      long initialCount = getMonitorAttrValue(baseDn, "replayed-updates");

      // Get the UUID of the test entry.
      Entry resultEntry = getEntry(tmp.getDN(), 1, true);
      AttributeType uuidType = DirectoryServer.getAttributeType("entryuuid");
      String uuid =
           resultEntry.getAttributeValue(uuidType,
                                         DirectoryStringSyntax.DECODER);

      // Register a short circuit that will fake a no-such-object result code
      // on a delete.  This will cause a replication replay loop.
      ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE,
                                              "PreParse", 32);
      try
      {
        // Publish a delete message for this test entry.
        DeleteMsg delMsg = new DeleteMsg(tmp.getDN().toString(),
                                         gen.newChangeNumber(),
                                         uuid);
        broker.publish(delMsg);

        // Wait for the operation to be replayed.
        long endTime = System.currentTimeMillis() + 5000;
        while (getMonitorAttrValue(baseDn, "replayed-updates") == initialCount &&
             System.currentTimeMillis() < endTime)
        {
          Thread.sleep(100);
        }
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE,
                                                  "PreParse");
      }

      // If the replication replay loop was detected and broken then the
      // counter will still be updated even though the replay was unsuccessful.
      if (getMonitorAttrValue(baseDn, "replayed-updates") == initialCount)
      {
        fail("Operation was not replayed");
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
  private static void setReceiveStatus(String syncConfigDN, boolean enable)
  {
    ArrayList<ASN1OctetString> valueList = new ArrayList<ASN1OctetString>(1);
    if (enable)
    {
      valueList.add(new ASN1OctetString("TRUE"));
    }
    else
    {
      valueList.add(new ASN1OctetString("FALSE"));
    }
    LDAPAttribute a = new LDAPAttribute("ds-cfg-receive-status", valueList);

    LDAPModification m = new LDAPModification(ModificationType.REPLACE, a);

    ArrayList<RawModification> modList = new ArrayList<RawModification>(1);
    modList.add(m);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ASN1OctetString rawEntryDN =
         new ASN1OctetString(syncConfigDN);
    ModifyOperation internalModify = conn.processModify(rawEntryDN, modList);

    ResultCode resultCode = internalModify.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      throw new RuntimeException("Cannot set receive status");
    }
  }
}
