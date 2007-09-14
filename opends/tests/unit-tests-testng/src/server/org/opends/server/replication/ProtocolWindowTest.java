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
import static org.testng.Assert.assertTrue;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import org.opends.messages.Category;
import org.opends.messages.Severity;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test the contructors, encoders and decoders of the Replication AckMsg,
 * ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class ProtocolWindowTest extends ReplicationTestCase
{
  private static final int WINDOW_SIZE = 10;
  private static final int REPLICATION_QUEUE_SIZE = 100;

  private static final String REPLICATION_STRESS_TEST =
    "Replication Stress Test";

  /**
   * A "person" entry
   */
  protected Entry personEntry;
  private int replServerPort;

  /**
   * Test the window mechanism by :
   *  - creating a ReplicationServer service client using the ReplicationBroker class.
   *  - set a small window size.
   *  - perform more than the window size operations.
   *  - check that the ReplicationServer has not sent more than window size operations.
   *  - receive all messages from the ReplicationBroker, check that
   *    the client receives the correct number of operations.
   */
  @Test(enabled=false, groups="slow")
  public void saturateQueueAndRestart() throws Exception
  {
    logError(Message.raw(
        Category.SYNC, Severity.INFORMATION,
        "Starting Replication ProtocolWindowTest : saturateAndRestart"));

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    ReplicationBroker broker = openReplicationSession(baseDn, (short) 13,
        WINDOW_SIZE, replServerPort, 1000, true);

    try {

      /* Test that replicationServer monitor and synchro plugin monitor informations
       * publish the correct window size.
       * This allows both the check the monitoring code and to test that
       * configuration is working.
       */
      Thread.sleep(1500);
      assertTrue(checkWindows(WINDOW_SIZE));
      assertTrue(checkChangelogQueueSize(REPLICATION_QUEUE_SIZE));

      // Create an Entry (add operation) that will be later used in the test.
      Entry tmp = personEntry.duplicate(false);
      AddOperationBasis addOp = new AddOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, tmp.getDN(),
          tmp.getObjectClasses(), tmp.getUserAttributes(),
          tmp.getOperationalAttributes());
      addOp.run();
      entryList.addLast(personEntry.getDN());
      assertTrue(DirectoryServer.entryExists(personEntry.getDN()),
        "The Add Entry operation failed");

      // Check if the client has received the msg
      ReplicationMessage msg = broker.receive();
      assertTrue(msg instanceof AddMsg,
        "The received Replication message is not an ADD msg");
      AddMsg addMsg =  (AddMsg) msg;

      Operation receivedOp = addMsg.createOperation(connection);
      assertTrue(OperationType.ADD.compareTo(receivedOp.getOperationType()) == 0,
        "The received Replication message is not an ADD msg");

      assertEquals(DN.decode(addMsg.getDn()),personEntry.getDN(),
        "The received ADD Replication message is not for the excepted DN");

      // send (2 * window + replicationServer queue) modify operations
      // so that window + replicationServer queue get stuck in the replicationServer queue
      int count = WINDOW_SIZE * 2 + REPLICATION_QUEUE_SIZE;
      processModify(count);

      // let some time to the message to reach the replicationServer client
      Thread.sleep(500);

      // check that the replicationServer only sent WINDOW_SIZE messages
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
      DirectoryServer.deregisterMonitorProvider(REPLICATION_STRESS_TEST);
    }
  }

  /**
   * Check that the ReplicationServer queue size has correctly been configured
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
   * Search that the replicationServer has stopped sending changes after
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
            (REPLICATION_QUEUE_SIZE + WINDOW_SIZE) + ")"));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);

    return (op.getEntriesSent() == 1);
  }

  /**
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
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
    Entry entry;
    for (int i = 0; i < topEntries.length; i++)
    {
      entry = TestCaseUtils.entryFromLdifString(topEntries[i]);
      AddOperationBasis addOp = new AddOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
              .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
          entry.getUserAttributes(), entry.getOperationalAttributes());
      addOp.setInternalOperation(true);
      addOp.run();
      entryList.addLast(entry.getDN());
    }

    // top level synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // Change log
    String replServerLdif =
      "dn: " + "cn=Replication Server, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server-config\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-server-port: " + replServerPort + "\n"
        + "ds-cfg-replication-server-id: 1\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE + "\n"
        + "ds-cfg-replication-max-queue-size: " + REPLICATION_QUEUE_SIZE;
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // suffix synchronized
    String synchroServerLdif =
      "dn: " + "cn=example, cn=domains, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain-config\n"
        + "cn: example\n"
        + "ds-cfg-replication-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
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

    configureReplication();
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

  @Test(enabled=true)
  public void protocolVersion() throws Exception
  {
    logError(Message.raw(
        Category.SYNC, Severity.INFORMATION,
        "Starting Replication ProtocolWindowTest : protocolVersion"));

    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");

    // Test : Make a broker degrade its version when connecting to an old
    // replication server.
    ProtocolVersion.setCurrentVersion((short)2);

    ReplicationBroker broker = new ReplicationBroker(
        new ServerState(),
        baseDn,
        (short) 13, 0, 0, 0, 0, 1000, 0,
        ReplicationTestCase.getGenerationId(baseDn),
        getReplSessionSecurity());


    // Check broker hard-coded version
    short pversion = broker.getProtocolVersion();
    assertEquals(pversion, 2);

    // Connect the broker to the replication server
    ProtocolVersion.setCurrentVersion((short)0);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + replServerPort);
    broker.start(servers);
    TestCaseUtils.sleep(100); // wait for connection established

    // Check broker negociated version
    pversion = broker.getProtocolVersion();
    assertEquals(pversion, 0);

    broker.stop();

    logError(Message.raw(
        Category.SYNC, Severity.INFORMATION,
        "Ending Replication ProtocolWindowTest : protocolVersion"));
  }
}
