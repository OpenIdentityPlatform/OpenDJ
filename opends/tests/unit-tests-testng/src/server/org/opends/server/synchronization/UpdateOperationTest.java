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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;

import org.opends.server.protocols.internal.InternalClientConnection;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.OperationType;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;

/**
 * Test the contructors, encoders and decoders of the synchronization
 * AckMsg, ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class UpdateOperationTest
    extends SynchronizationTestCase
{

  /**
   * The internal connection used for operation
   */
  private InternalClientConnection connection;

  /**
   * Created entries that need to be deleted for cleanup
   */
  private ArrayList<Entry> entryList = new ArrayList<Entry>();

  /**
   * The Synchronization config manager entry
   */
  private String synchroStringDN;

  /**
   * The synchronization plugin entry
   */
  private String synchroPluginStringDN;
  private Entry synchroPluginEntry;

  /**
   * The Server synchro entry
   */
  private String synchroServerStringDN;
  private Entry synchroServerEntry;

  /**
   * The Change log entry
   */
  private String changeLogStringDN;
  private Entry changeLogEntry;

  /**
   * A "person" entry
   */
  private Entry personEntry;

  /**
   * schema check flag
   */
  private boolean schemaCheck;


  // WORKAROUND FOR BUG #639 - BEGIN -
  /**
   *
   */
  MultimasterSynchronization mms;

  // WORKAROUND FOR BUG #639 - END -


  /**
   * Set up the environment for performing the tests in this Class.
   * synchronization
   *
   * @throws Exception
   *         If the environment could not be set up.
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
    topEntries[1] = "dn: ou=People,dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: organizationalUnit\n";
    Entry entry ;
    for (int i = 0; i < topEntries.length; i++)
    {
      entry = TestCaseUtils.entryFromLdifString(topEntries[i]);
      AddOperation addOp = new AddOperation(connection,
          InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(), null, entry.getDN(),
          entry.getObjectClasses(), entry.getUserAttributes(), entry
              .getOperationalAttributes());
      addOp.setInternalOperation(true);
      addOp.run();
      entryList.add(entry);
    }

    // top level synchro provider
    synchroStringDN = "cn=Synchronization Providers,cn=config";

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
    changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
        + "cn: Changelog Server\n"
        + "ds-cfg-changelog-port: 8989\n"
        + "ds-cfg-changelog-server-id: 1\n";
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // suffix synchronized
    synchroServerStringDN = "cn=example, " + synchroPluginStringDN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
        + "ds-cfg-directory-server-id: 1\n"
        + "ds-cfg-receive-status: true\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    String personLdif = "dn: uid=user.1,ou=People,dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n"
        + "st: NC\n" + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
        + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
    personEntry = TestCaseUtils.entryFromLdifString(personLdif);
  }

  /**
   * Clean up the environment. return null;
   *
   * @throws Exception
   *         If the environment could not be set up.
   */
  @AfterClass
  public void classCleanUp() throws Exception
  {

    DirectoryServer.setCheckSchema(schemaCheck);
    DeleteOperation op;

    //  WORKAROUND FOR BUG #639 - BEGIN -
    DirectoryServer.deregisterSynchronizationProvider(mms);
    mms.finalizeSynchronizationProvider();
    //  WORKAROUND FOR BUG #639 - END -

    // Delete entries
    Entry entries[] = entryList.toArray(new Entry[0]) ;
    for (int i = entries.length -1 ; i != 0 ; i--)
    {
      try
      {
        op = new DeleteOperation(connection, InternalClientConnection
            .nextOperationID(), InternalClientConnection.nextMessageID(),
            null, entries[i].getDN());
        op.run();
      }
      catch (Exception e)
      {
      }
    }
  }

  /**
   * Tests that performed operation will generate synchronization messages
   */
  @Test()
  public void updateOperations() throws Exception
  {
    //
    // Add the Multimaster synchronization plugin
    DirectoryServer.getConfigHandler().addEntry(synchroPluginEntry, null);
    entryList.add(synchroPluginEntry);
    assertNotNull(DirectoryServer.getConfigEntry(DN
        .decode(synchroPluginStringDN)),
        "Unable to add the Multimaster synchronization plugin");

    // WORKAROUND FOR BUG #639 - BEGIN -
    DN dn = DN.decode(synchroPluginStringDN);
    ConfigEntry mmsConfigEntry = DirectoryServer.getConfigEntry(dn);
    mms = new MultimasterSynchronization();
    try
    {
      mms.initializeSynchronizationProvider(mmsConfigEntry);
    }
    catch (ConfigException e)
    {
      assertTrue(false,
          "Unable to initialize the Multimaster synchronization plugin");
    }
    DirectoryServer.registerSynchronizationProvider(mms);
    // WORKAROUND FOR BUG #639 - END -

    //
    // Add the changelog server
    DirectoryServer.getConfigHandler().addEntry(changeLogEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(changeLogEntry.getDN()),
        "Unable to add the changeLog server");
    entryList.add(changeLogEntry);

    //
    // We also have a replicated suffix (synchronization domain)
    DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the syncrhonized server");
    entryList.add(synchroServerEntry);

    //
    // Check Server state
    DN synchroServerDN = DN.decode("ou=People,dc=example,dc=com");
    ServerState ss = MultimasterSynchronization
        .getServerState(synchroServerDN);
    // TODO Check server state field

    //
    // Be Client of the Change log
    // We will check that the Chanlog server send us messages
    // suffix synchronized
    String synchroServerLdif2 = "dn: cn=example2, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
        + "ds-cfg-directory-server-id: 2\n"
        + "ds-cfg-receive-status: true\n";
    Entry synchroServerEntry2 = TestCaseUtils
        .entryFromLdifString(synchroServerLdif2);
    ConfigEntry newConfigEntry = new ConfigEntry(synchroServerEntry2,
        DirectoryServer.getConfigEntry(DN.decode(synchroPluginStringDN)));
    SynchronizationDomain syncDomain2 = new SynchronizationDomain(
        newConfigEntry);

    //
    // Create an Entry (add operation)
    AddOperation addOp = new AddOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, personEntry.getDN(), personEntry
            .getObjectClasses(), personEntry.getUserAttributes(), personEntry
            .getOperationalAttributes());
    addOp.run();
    entryList.add(personEntry);
    assertNotNull(DirectoryServer.getEntry(personEntry.getDN()),
        "The Add Entry operation fails");

    // Check if the client has receive the msg
    UpdateMessage msg = syncDomain2.receive() ;
    Operation receivedOp = msg.createOperation(connection);
    assertTrue(OperationType.ADD.compareTo(receivedOp.getOperationType()) == 0,
        "The received synchronization message is not an ADD msg");
    assertTrue(DN.decode(msg.getDn()).compareTo(personEntry.getDN()) == 0,
        "The received ADD synchronization message is not for the excepted DN");


    // Modify the entry
    String attrName = "telephoneNumber";
    AttributeType attrType = DirectoryServer.getAttributeType(attrName, true);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(attrType, "01 02 45"));
    Attribute attr = new Attribute(attrType, attrName, values);
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);

    ModifyOperation modOp = new ModifyOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, personEntry.getDN(), mods);
    modOp.setInternalOperation(true);
    modOp.run();
    // TODO Check the telephoneNumber attribute

    //  See if the client has receive the msg
    msg = syncDomain2.receive() ;
    receivedOp = msg.createOperation(connection);
    assertTrue(OperationType.MODIFY.compareTo(receivedOp.getOperationType()) == 0,
        "The received synchronization message is not a MODIFY msg");
    assertTrue(DN.decode(msg.getDn()).compareTo(personEntry.getDN()) == 0,
    "The received MODIFY synchronization message is not for the excepted DN");

    //
    // Modify the entry DN
    DN newDN = DN.decode("uid= new person,ou=People,dc=example,dc=com") ;
    ModifyDNOperation modDNOp = new ModifyDNOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, personEntry.getDN(), RDN
            .decode("uid=new person"), true, DN
            .decode("ou=People,dc=example,dc=com"));
    modDNOp.run();
    assertNotNull(DirectoryServer.getEntry(newDN),
        "The MOD_DN operation didn't create the new person entry");
    assertNull(DirectoryServer.getEntry(personEntry.getDN()),
        "The MOD_DN operation didn't delete the old person entry");
    entryList.add(DirectoryServer.getEntry(newDN));

    //  See if the client has receive the msg
    msg = syncDomain2.receive() ;
    receivedOp = msg.createOperation(connection);
    assertTrue(OperationType.MODIFY_DN.compareTo(receivedOp.getOperationType()) == 0,
        "The received synchronization message is not a MODIFY_DN msg");
    assertTrue(DN.decode(msg.getDn()).compareTo(personEntry.getDN()) == 0,
        "The received MODIFY_DN synchronization message is not for the excepted DN");


    // Delete the entry
    Entry newPersonEntry = DirectoryServer.getEntry(newDN) ;
    DeleteOperation delOp = new DeleteOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, DN
            .decode("uid= new person,ou=People,dc=example,dc=com"));
    delOp.run();
    assertNull(DirectoryServer.getEntry(newDN),
        "Unable to delete the new person Entry");
    entryList.remove(newPersonEntry);

    //  See if the client has receive the msg
    msg = syncDomain2.receive() ;
    receivedOp = msg.createOperation(connection);
    assertTrue(OperationType.DELETE.compareTo(receivedOp.getOperationType()) == 0,
        "The received synchronization message is not a DELETE msg");
    assertTrue(DN.decode(msg.getDn()).compareTo(DN
        .decode("uid= new person,ou=People,dc=example,dc=com")) == 0,
        "The received DELETE synchronization message is not for the excepted DN");

    // Purge the client
    syncDomain2.shutdown() ;

    //
    // Be new Client of the Change log
    // We will check that when we send message to the Chanlog server
    // the synchronization domain apply those changes.

    // Create a new synchronization domain
    String synchroServerLdif3 = "dn: cn=example3, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
        + "ds-cfg-directory-server-id: 3\n"
        + "ds-cfg-receive-status: true\n";
    Entry synchroServerEntry3 = TestCaseUtils
        .entryFromLdifString(synchroServerLdif3);
    newConfigEntry = new ConfigEntry(synchroServerEntry3, DirectoryServer
        .getConfigEntry(DN.decode(synchroPluginStringDN)));
    SynchronizationDomain syncDomain3 = new SynchronizationDomain(
        newConfigEntry);

    //
    // Message to Create the Entry
    addOp = new AddOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, personEntry.getDN(), personEntry
            .getObjectClasses(), personEntry.getUserAttributes(), personEntry
            .getOperationalAttributes());
    syncDomain3.doPreOperation(addOp);
    addOp.setResultCode(ResultCode.SUCCESS) ;
    syncDomain3.synchronize(addOp);

    // Wait no more than 1 second (synchro operation has to be sent,
    // received and replay)
    Entry newEntry = null ;
    int i = 10 ;
    while ((i> 0) && (newEntry == null))
    {
      Thread.sleep(100);
      newEntry = DirectoryServer.getEntry(personEntry.getDN());
      i-- ;
    }
    newEntry = DirectoryServer.getEntry(personEntry.getDN());
    assertNotNull(newEntry,
        "The send ADD synchronization message was not applied");
    entryList.add(newEntry);

    // Message to Modify the new created entry

    // Unable to test it: the doPreOperation operation use
    // modifyOperation.getModifiedEntry() which cannot be set the
    // the public level.
    // Otherwise, code will look like:
    /*
    modOp = new ModifyOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
            .nextMessageID(), null, personEntry.getDN(), mods);
    modOp.setModifiedEntry(...);
    mms.doPreOperation(modOp);
    modOp.setResultCode(ResultCode.SUCCESS) ;
    syncDomain3.synchronize(modOp);
    //  TODO Check the telephoneNumber attribute
     */

    //  Purge the message sender
    syncDomain3.shutdown() ;


    // Check synchronization monitoring
    SynchronizationDomain syncDomain = new SynchronizationDomain(
        DirectoryServer.getConfigEntry(synchroServerEntry.getDN()));
    SynchronizationMonitor mon = new SynchronizationMonitor(syncDomain);
    ArrayList<Attribute> monData = mon.getMonitorData();
    // TODO Check Monitoring Attributes
  }
}
