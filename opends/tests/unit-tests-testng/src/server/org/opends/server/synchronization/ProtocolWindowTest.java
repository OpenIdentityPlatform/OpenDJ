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

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.protocol.AddMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test the contructors, encoders and decoders of the synchronization AckMsg,
 * ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class ProtocolWindowTest extends SynchronizationTestCase
{
  private static final int WINDOW_SIZE = 10;
  private static final int CHANGELOG_QUEUE_SIZE = 100;

  private static final String SYNCHRONIZATION_STRESS_TEST =
    "Synchronization Stress Test";

  /**
   * A "person" entry
   */
  protected Entry personEntry;

  /**
   * Test the window mechanism by :
   *  - creating a Changelog service client using the ChangelogBroker class.
   *  - set a small window size.
   *  - perform more than the window size operations.
   *  - check that the Changelog has not sent more than window size operations.
   *  - receive all messages from the ChangelogBroker, check that
   *    the client receives the correct number of operations.
   */
  @Test(enabled=true, groups="slow")
  public void saturateQueueAndRestart() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization ProtocolWindowTest : saturateAndRestart" , 1);
    
    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    ChangelogBroker broker = openChangelogSession(baseDn, (short) 13,
        WINDOW_SIZE, 8989, 1000);

    try {
      
      /* Test that changelog monitor and synchro plugin monitor informations
       * publish the correct window size.
       * This allows both the check the monitoring code and to test that
       * configuration is working.
       */
      Thread.sleep(1500);
      assertTrue(checkWindows(WINDOW_SIZE));
      assertTrue(checkChangelogQueueSize(CHANGELOG_QUEUE_SIZE));
      
      // Create an Entry (add operation) that will be later used in the test.
      Entry tmp = personEntry.duplicate();
      AddOperation addOp = new AddOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, tmp.getDN(),
          tmp.getObjectClasses(), tmp.getUserAttributes(),
          tmp.getOperationalAttributes());
      addOp.run();
      entryList.addLast(personEntry.getDN());
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

      // send (2 * window + changelog queue) modify operations
      // so that window + changelog queue get stuck in the changelog queue 
      int count = WINDOW_SIZE * 2 + CHANGELOG_QUEUE_SIZE;
      processModify(count);

      // let some time to the message to reach the changelog client
      Thread.sleep(500);

      // check that the changelog only sent WINDOW_SIZE messages
      assertTrue(searchUpdateSent());

      int rcvCount=0;
      try
      {
        while (true)
        {
          broker.receive();
          rcvCount++;
        }
      }
      catch (SocketTimeoutException e)
      {}
      /*
       * check that we received all updates
       */
      assertEquals(rcvCount, count);
    }
    finally {
      broker.stop();
      DirectoryServer.deregisterMonitorProvider(SYNCHRONIZATION_STRESS_TEST);
    }
  }

  /**
   * Check that the Changelog queue size has correctly been configured
   * by reading the monitoring information.
   * @throws LDAPException 
   */
  private boolean checkChangelogQueueSize(int changelog_queue_size)
          throws LDAPException
  {
    InternalSearchOperation op = connection.processSearch(
        new ASN1OctetString("cn=monitor"),
        SearchScope.WHOLE_SUBTREE, LDAPFilter.decode(
            "(max-waiting-changes=" +  changelog_queue_size + ")"));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    return (op.getEntriesSent() == 2);
  }

  /**
   * Check that the window configuration has been successfull
   * by reading the monitoring information and checking 
   * that we do have 2 entries with the configured max-rcv-window.
   */
  private boolean checkWindows(int windowSize) throws LDAPException
  {
    InternalSearchOperation op = connection.processSearch(
        new ASN1OctetString("cn=monitor"),
        SearchScope.WHOLE_SUBTREE,
        LDAPFilter.decode("(max-rcv-window=" + windowSize + ")"));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    return (op.getEntriesSent() == 3);
  }
  
  /**
   * Search that the changelog has stopped sending changes after
   * having reach the limit of the window size.
   * And that the number of waiting changes is accurate.
   * Do this by checking the monitoring information.
   */
  private boolean searchUpdateSent() throws Exception
  {
    InternalSearchOperation op = connection.processSearch(
        new ASN1OctetString("cn=monitor"),
        SearchScope.WHOLE_SUBTREE,
        LDAPFilter.decode("(update-sent=" + WINDOW_SIZE + ")"));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    if (op.getEntriesSent() != 1)
      return false;
    
    op = connection.processSearch(
        new ASN1OctetString("cn=monitor"),
        SearchScope.WHOLE_SUBTREE,
        LDAPFilter.decode("(waiting-changes=" +
            (CHANGELOG_QUEUE_SIZE + WINDOW_SIZE) + ")"));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);

    return (op.getEntriesSent() == 1);
  }

  /**
   * Set up the environment for performing the tests in this Class.
   * synchronization
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
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
      System.out.println("adding " + entry.getDN());
      entryList.addLast(entry.getDN());
    }

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
        + "ds-cfg-changelog-server-id: 1\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE + "\n"
        + "ds-cfg-changelog-max-queue-size: " + CHANGELOG_QUEUE_SIZE;
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // suffix synchronized
    String synchroServerStringDN = "cn=example, " + synchroPluginStringDN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
        + "ds-cfg-directory-server-id: 1\n"
        + "ds-cfg-receive-status: true\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE;
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

    configureSynchronization();
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

  private void processModify(int count)
  {
    while (count>0)
    {
      count--;
      // must generate the mods for every operation because they are modified
      // by processModify.
      List<Modification> mods = generatemods("telephonenumber", "01 02 45");

      ModifyOperation modOp =
        connection.processModify(personEntry.getDN(), mods);
      assertEquals(modOp.getResultCode(), ResultCode.SUCCESS);
    }
  }
}
