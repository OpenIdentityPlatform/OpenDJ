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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.server.synchronization;

import static org.opends.server.loggers.Error.logError;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.schema.IntegerSyntax;
import org.opends.server.synchronization.common.ChangeNumberGenerator;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.protocol.AddMsg;
import org.opends.server.synchronization.protocol.DeleteMsg;
import org.opends.server.synchronization.protocol.ModifyDNMsg;
import org.opends.server.synchronization.protocol.ModifyMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the contructors, encoders and decoders of the synchronization AckMsg,
 * ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class UpdateOperationTest extends SynchronizationTestCase
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

  /**
   * Set up the environment for performing the tests in this Class.
   * synchronization
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

    // Disable schema check
    schemaCheck = DirectoryServer.checkSchema();
    DirectoryServer.setCheckSchema(false);

    // Create an internal connection
    connection = new InternalClientConnection();

    // Create backend top level entries
    String[] topEntries = new String[2];
    topEntries[0] = "dn: dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: domain\n";
    topEntries[1] = "dn: ou=People,dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
    Entry entry;
    for (int i = 0; i < topEntries.length; i++)
    {
      entry = TestCaseUtils.entryFromLdifString(topEntries[i]);
      AddOperation addOp = new AddOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
              .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
          entry.getUserAttributes(), entry.getOperationalAttributes());
      addOp.setInternalOperation(true);
      addOp.run();
      entryList.add(entry.getDN());
    }

    baseUUID = getEntryUUID(DN.decode("ou=People,dc=example,dc=com"));

    // top level synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;
    String synchroPluginLdif = "dn: "
        + synchroPluginStringDN
        + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider\n"
        + "ds-cfg-synchronization-provider-enabled: true\n"
        + "ds-cfg-synchronization-provider-class: org.opends.server.synchronization.MultimasterSynchronization\n";
    synchroPluginEntry = TestCaseUtils.entryFromLdifString(synchroPluginLdif);

    // Change log
    String changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
        + "cn: Changelog Server\n" + "ds-cfg-changelog-port: 8989\n"
        + "ds-cfg-changelog-server-id: 1\n";
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // suffix synchronized
    String synchroServerStringDN = "cn=example, " + synchroPluginStringDN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
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

    configureSynchronization();
  }

  /**
   * Tests the naming conflict resolution code.
   * In this test, the local server act both as an LDAP server and
   * a changelog server that are inter-connected.
   *
   * The test creates an other session to the changelog server using
   * directly the ChangelogBroker API.
   * It then uses this session to siomulate conflicts and therefore
   * test the naming conflict resolution code.
   */
  @Test(enabled=true)
  public void namingConflicts() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization test : namingConflicts" , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    /*
     * Open a session to the changelog server using the Changelog broker API.
     * This must use a serverId different from the LDAP server ID
     */
    ChangelogBroker broker =
      openChangelogSession(baseDn, (short) 2, 100, 8989, 1000, true);

    /*
     * Create a Change number generator to generate new changenumbers
     * when we need to send operations messages to the changelog server.
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
    AddMsg addMsg = new AddMsg(gen.NewChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    // Check that the entry has been created in the local DS.
    Entry resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The send ADD synchronization message was not applied");
    entryList.add(resultEntry.getDN());

    // send a modify operation with the correct unique ID but another DN
    List<Modification> mods = generatemods("telephonenumber", "01 02 45");
    ModifyMsg modMsg = new ModifyMsg(gen.NewChangeNumber(),
        DN.decode("cn=something,ou=People,dc=example,dc=com"), mods,
        user1entryUUID);
    broker.publish(modMsg);

    // check that the modify has been applied as if the entry had been renamed.
    boolean found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
                           "telephonenumber", "01 02 45", 10000, true);
    if (found == false)
     fail("The modification has not been correctly replayed.");

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
    addMsg = new AddMsg(gen.NewChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID, baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    // Check that the entry has been created in the local DS.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The ADD synchronization message was not applied");
    entryList.add(resultEntry.getDN());

    // send a modify operation with a wrong unique ID but the same DN
    mods = generatemods("telephonenumber", "02 01 03 05");
    modMsg = new ModifyMsg(gen.NewChangeNumber(),
        DN.decode(user1dn), mods, "10000000-9abc-def0-1234-1234567890ab");
    broker.publish(modMsg);

    // check that the modify has not been applied
    found = checkEntryHasAttribute(personWithUUIDEntry.getDN(),
                           "telephonenumber", "02 01 03 05", 10000, false);
    if (found == true)
     fail("The modification has been replayed while it should not.");


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
          gen.NewChangeNumber(), user1entryUUID);
    broker.publish(delMsg);

    // check that the delete operation has been applied
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, false);

    assertNull(resultEntry,
        "The DELETE synchronization message was not replayed");

    /*
     * Test that two adds with the same DN but a different unique ID result
     * cause a conflict and result in the second entry to be renamed.
     */

    //  create an entry with a given DN and unique ID
    addMsg = new AddMsg(gen.NewChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID, baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    //  Check that the entry has been created in the local DS.
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, true);
    assertNotNull(resultEntry,
        "The ADD synchronization message was not applied");
    entryList.add(resultEntry.getDN());

    //  create an entry with the same DN and another unique ID
    addMsg = new AddMsg(gen.NewChangeNumber(),
        personWithSecondUniqueID.getDN().toString(),
        user1entrysecondUUID, baseUUID,
        personWithSecondUniqueID.getObjectClassAttribute(),
        personWithSecondUniqueID.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    //  Check that the entry has been renamed and created in the local DS.
    resultEntry = getEntry(
        DN.decode("entryuuid=" + user1entrysecondUUID +" + " + user1dn),
        10000, true);
    assertNotNull(resultEntry,
        "The ADD synchronization message was not applied");

    //  delete the entries to clean the database.
    delMsg =
      new DeleteMsg(personWithUUIDEntry.getDN().toString(),
          gen.NewChangeNumber(), user1entryUUID);
    broker.publish(delMsg);
    delMsg =
      new DeleteMsg(personWithSecondUniqueID.getDN().toString(),
          gen.NewChangeNumber(), user1entrysecondUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(personWithUUIDEntry.getDN(), 10000, false);

    // check that the delete operation has been applied
    assertNull(resultEntry,
        "The DELETE synchronization message was not replayed");
    /*
     * Check that and added entry is correctly added below it's
     * parent entry when this parent entry has been renamed.
     *
     * Simulate this by trying to add an entry below a DN that does not
     * exist but with a parent ID that exist.
     */
    addMsg = new AddMsg(gen.NewChangeNumber(),
        "uid=new person,o=nothere,o=below,ou=People,dc=example,dc=com",
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
    broker.publish(addMsg);

    //  Check that the entry has been renamed and created in the local DS.
    resultEntry = getEntry(
        DN.decode("uid=new person,ou=People,dc=example,dc=com"), 10000, true);
    assertNotNull(resultEntry,
        "The ADD synchronization message was not applied");

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
          gen.NewChangeNumber(), "11111111-9abc-def0-1234-1234567890ab");
    broker.publish(delMsg);
    resultEntry = getEntry(
          DN.decode("uid=new person,ou=People,dc=example,dc=com"), 10000, true);

    // check that the delete operation has not been applied
    assertNotNull(resultEntry,
        "The DELETE synchronization message was replayed when it should not");


    /*
     * Check that when replaying modify dn operations, the conflict
     * resolution code is able to find the new DN of the parent entry
     * if it has been renamed on another master.
     *
     * To simulate this try to rename an entry below an entry that does
     * not exist but giving the unique ID of an existing entry.
     */

    ModifyDNMsg  modDnMsg = new ModifyDNMsg(
        "uid=new person,ou=People,dc=example,dc=com", gen.NewChangeNumber(),
        user1entryUUID, baseUUID, false,
        "uid=wrong, ou=people,dc=example,dc=com",
        "uid=newrdn");
    broker.publish(modDnMsg);

    resultEntry = getEntry(
        DN.decode("uid=newrdn,ou=People,dc=example,dc=com"), 10000, true);

    // check that the operation has been correctly relayed
    assertNotNull(resultEntry,
      "The modify dn was not or badly replayed");

    /*
     * same test but by giving a bad entry DN
     */

     modDnMsg = new ModifyDNMsg(
        "uid=wrong,ou=People,dc=example,dc=com", gen.NewChangeNumber(),
        user1entryUUID, baseUUID, false, null, "uid=reallynewrdn");
    broker.publish(modDnMsg);

    resultEntry = getEntry(
        DN.decode("uid=reallynewrdn,ou=People,dc=example,dc=com"), 10000, true);

    // check that the operation has been correctly relayed
    assertNotNull(resultEntry,
      "The modify dn was not or badly replayed");

    /*
     * Check that conflicting entries are renamed when a
     * modifyDN is done with the same DN as an entry added on another server.
     */

    // add a second entry
    addMsg = new AddMsg(gen.NewChangeNumber(),
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
    modDnMsg = new ModifyDNMsg(user1dn, gen.NewChangeNumber(),
                               user1entrysecondUUID, baseUUID, false,
                               baseDn.toString(), "uid=reallynewrdn");
    broker.publish(modDnMsg);

   // check that the second entry has been renamed
    resultEntry = getEntry(
        DN.decode("entryUUID = " + user1entrysecondUUID + "+uid=reallynewrdn," +
            "ou=People,dc=example,dc=com"), 10000, true);

    // check that the delete operation has been applied
    assertNotNull(resultEntry, "The modifyDN was not or incorrectly replayed");

    // delete the entries to clean the database
    delMsg =
      new DeleteMsg("uid=reallynewrdn,ou=People,dc=example,dc=com",
          gen.NewChangeNumber(), user1entryUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(
        DN.decode("uid=reallynewrdn,ou=People,dc=example,dc=com"), 10000, false);

    //  check that the delete operation has been applied
    assertNull(resultEntry,
        "The DELETE synchronization message was not replayed");

    delMsg =
      new DeleteMsg("entryUUID = " + user1entrysecondUUID + "+" +
          DN.decode(user1dn).getRDN().toString() +
          "ou=People,dc=example,dc=com",
          gen.NewChangeNumber(), user1entrysecondUUID);
    broker.publish(delMsg);
    resultEntry = getEntry(
          DN.decode("entryUUID = " + user1entrysecondUUID + "+" +
              DN.decode(user1dn).getRDN().toString() +
              "ou=People,dc=example,dc=com"), 10000, false);

    // check that the delete operation has been applied
    assertNull(resultEntry,
        "The DELETE synchronization message was not replayed");
    broker.stop();
  }

  @DataProvider(name="assured")
  public Object[][] getAssuredFlag()
  {
    return new Object[][] { { false }, {true} };
  }
  /**
   * Tests done using directly the ChangelogBroker interface.
   */
  @Test(enabled=true, dataProvider="assured")
  public void updateOperations(boolean assured) throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization test : updateOperations " + assured , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    cleanEntries();

    ChangelogBroker broker =
      openChangelogSession(baseDn, (short) 27, 100, 8989, 1000, true);
    try {
      ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 27, 0);

      /*
       * loop receiving update until there is nothing left
       * to make sure that message from previous tests have been consumed.
       */
      try
      {
        while (true)
        {
          broker.receive();
        }
      }
      catch (Exception e)
      {}
      /*
       * Test that operations done on this server are sent to the
       * changelog server and forwarded to our changelog broker session.
       */

      // Create an Entry (add operation)
      Entry tmp = personEntry.duplicate();
      AddOperation addOp = new AddOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, tmp.getDN(),
          tmp.getObjectClasses(), tmp.getUserAttributes(),
          tmp.getOperationalAttributes());
      addOp.run();
      entryList.add(personEntry.getDN());
      assertTrue(DirectoryServer.entryExists(personEntry.getDN()),
      "The Add Entry operation failed");

      // Check if the client has received the msg
      SynchronizationMessage msg = broker.receive();
      assertTrue(msg instanceof AddMsg,
      "The received synchronization message is not an ADD msg");
      AddMsg addMsg =  (AddMsg) msg;

      Operation receivedOp = addMsg.createOperation(connection);
      assertTrue(OperationType.ADD.compareTo(receivedOp.getOperationType()) == 0,
      "The received synchronization message is not an ADD msg");

      assertEquals(DN.decode(addMsg.getDn()),personEntry.getDN(),
      "The received ADD synchronization message is not for the excepted DN");

      // Modify the entry
      List<Modification> mods = generatemods("telephonenumber", "01 02 45");

      ModifyOperation modOp = new ModifyOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, personEntry.getDN(), mods);
      modOp.setInternalOperation(true);
      modOp.run();

      // See if the client has received the msg
      msg = broker.receive();
      assertTrue(msg instanceof ModifyMsg,
      "The received synchronization message is not a MODIFY msg");
      ModifyMsg modMsg = (ModifyMsg) msg;

      receivedOp = modMsg.createOperation(connection);
      assertTrue(DN.decode(modMsg.getDn()).compareTo(personEntry.getDN()) == 0,
      "The received MODIFY synchronization message is not for the excepted DN");

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
      "The received synchronization message is not a MODIFY DN msg");
      ModifyDNMsg moddnMsg = (ModifyDNMsg) msg;
      receivedOp = moddnMsg.createOperation(connection);

      assertTrue(DN.decode(moddnMsg.getDn()).compareTo(personEntry.getDN()) == 0,
      "The received MODIFY_DN message is not for the excepted DN");

      // Delete the entry
      DeleteOperation delOp = new DeleteOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, DN
          .decode("uid= new person,ou=People,dc=example,dc=com"));
      delOp.run();
      assertFalse(DirectoryServer.entryExists(newDN),
      "Unable to delete the new person Entry");

      // See if the client has received the msg
      msg = broker.receive();
      assertTrue(msg instanceof DeleteMsg,
      "The received synchronization message is not a MODIFY DN msg");
      DeleteMsg delMsg = (DeleteMsg) msg;
      receivedOp = delMsg.createOperation(connection);
      assertTrue(DN.decode(delMsg.getDn()).compareTo(DN
          .decode("uid= new person,ou=People,dc=example,dc=com")) == 0,
      "The received DELETE message is not for the excepted DN");

      /*
       * Now check that when we send message to the Changelog server
       * and that they are received and correctly replayed by the server.
       *
       * Start by testing the Add message reception
       */
      addMsg = new AddMsg(gen.NewChangeNumber(),
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
      "The send ADD synchronization message was not applied");
      entryList.add(resultEntry.getDN());

      /*
       * Test the reception of Modify Msg
       */
      modMsg = new ModifyMsg(gen.NewChangeNumber(), personWithUUIDEntry.getDN(),
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
          gen.NewChangeNumber(),
          user1entryUUID, null,
          true, null, "uid= new person");
      if (assured)
        moddnMsg.setAssured();
      broker.publish(moddnMsg);

      resultEntry = getEntry(
          DN.decode("uid= new person,ou=People,dc=example,dc=com"), 10000, true);

      assertNotNull(resultEntry,
      "The modify DN synchronization message was not applied");

      /*
       * Test the Reception of Delete Msg
       */
      delMsg = new DeleteMsg("uid= new person,ou=People,dc=example,dc=com",
          gen.NewChangeNumber(), user1entryUUID);
      if (assured)
        delMsg.setAssured();
      broker.publish(delMsg);
      resultEntry = getEntry(
          DN.decode("uid= new person,ou=People,dc=example,dc=com"), 10000, false);

      assertNull(resultEntry,
      "The DELETE synchronization message was not replayed");
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * @return
   */
  private List<Modification> generatemods(String attrName, String attrValue)
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
   * Check that the entry with the given dn has the given valueString value
   * for the given attrTypeStr attribute type.
   */
  private boolean checkEntryHasAttribute(DN dn, String attrTypeStr,
      String valueString, int timeout, boolean hasAttribute) throws Exception
  {
    boolean found;
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
        newEntry = DirectoryServer.getEntry(personWithUUIDEntry.getDN());
      
     
        if (newEntry == null)
          fail("The entry " + personWithUUIDEntry.getDN() +
          " has incorrectly been deleted from the database.");
        List<Attribute> tmpAttrList = newEntry.getAttribute(attrTypeStr);
        Attribute tmpAttr = tmpAttrList.get(0);

        AttributeType attrType =
          DirectoryServer.getAttributeType(attrTypeStr, true);
        found = tmpAttr.hasValue(new AttributeValue(attrType, valueString));
       
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
   * Retrieves an entry from the local Directory Server.
   * @throws Exception When the entry cannot be locked.
   */
  private Entry getEntry(DN dn, int timeout, boolean exist)
               throws Exception
  {
    int count = timeout/200;
    if (count<1)
      count=1;
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
        return entry.duplicate();
    }
    finally
    {
      LockManager.unlock(dn, lock);
    }
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
        "Starting synchronization test : deleteNoSuchObject" , 1);

    DN dn = DN.decode("cn=No Such Object,ou=People,dc=example,dc=com");
    Operation op =
         new DeleteOperation(connection,
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
  @Test(enabled=true)
  public void infiniteReplayLoop() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization test : infiniteReplayLoop" , 1);

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    Thread.sleep(2000);
    ChangelogBroker broker =
      openChangelogSession(baseDn, (short) 11, 100, 8989, 1000, true);
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
      AddOperation addOp =
           new AddOperation(connection,
                            InternalClientConnection.nextOperationID(),
                            InternalClientConnection.nextMessageID(),
                            null, tmp.getDN(), tmp.getObjectClasses(),
                            tmp.getUserAttributes(),
                            tmp.getOperationalAttributes());
      addOp.run();
      assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);
      entryList.add(tmp.getDN());

      long initialCount = getReplayedUpdatesCount();

      // Get the UUID of the test entry.
      Entry resultEntry = getEntry(tmp.getDN(), 1, true);
      AttributeType uuidType = DirectoryServer.getAttributeType("entryuuid");
      String uuid =
           resultEntry.getAttributeValue(uuidType,
                                         DirectoryStringSyntax.DECODER);

      // Register a short circuit that will fake a no-such-object result code
      // on a delete.  This will cause a synchronization replay loop.
      ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE,
                                              "PreParse", 32);
      try
      {
        // Publish a delete message for this test entry.
        DeleteMsg delMsg = new DeleteMsg(tmp.getDN().toString(),
                                         gen.NewChangeNumber(),
                                         uuid);
        broker.publish(delMsg);

        // Wait for the operation to be replayed.
        long endTime = System.currentTimeMillis() + 5000;
        while (getReplayedUpdatesCount() == initialCount &&
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

      // If the synchronization replay loop was detected and broken then the
      // counter will still be updated even though the replay was unsuccessful.
      if (getReplayedUpdatesCount() == initialCount)
      {
        fail("Synchronization operation was not replayed");
      }
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Retrieve the number of replayed updates from the monitor entry.
   * @return The number of replayed updates.
   * @throws Exception If an error occurs.
   */
  private long getReplayedUpdatesCount() throws Exception
  {
    String monitorFilter =
         "(&(cn=synchronization*)(base-dn=ou=People,dc=example,dc=com))";

    InternalSearchOperation op;
    op = connection.processSearch(
         ByteStringFactory.create("cn=monitor"),
         SearchScope.SINGLE_LEVEL,
         LDAPFilter.decode(monitorFilter));
    SearchResultEntry entry = op.getSearchEntries().getFirst();

    AttributeType attrType =
         DirectoryServer.getDefaultAttributeType("replayed-updates");
    return entry.getAttributeValue(attrType, IntegerSyntax.DECODER).longValue();
  }
}
