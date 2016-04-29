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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.NoSuchElementException;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * Test the constructors, encoders and decoders of the Replication AckMsg,
 * ModifyMsg, ModifyDnMsg, AddMsg and Delete MSG.
 */
@SuppressWarnings("javadoc")
public class ProtocolWindowTest extends ReplicationTestCase
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int WINDOW_SIZE = 10;
  private static final int REPLICATION_QUEUE_SIZE = 100;

  /**
   * A "person" entry.
   */
  private Entry personEntry;
  private int replServerPort;


  /** The base DN used for this test. */
  private DN baseDN;
  private ReplicationServer replicationServer;

  /**
   * Test the window mechanism by :
   *  - creating a ReplicationServer service client using the ReplicationBroker class.
   *  - set a small window size.
   *  - perform more than the window size operations.
   *  - check that the ReplicationServer has not sent more than window size operations.
   *  - receive all messages from the ReplicationBroker, check that
   *    the client receives the correct number of operations.
   */
  @Test(enabled=true, groups="slow")
  public void saturateQueueAndRestart() throws Exception
  {
    logger.error(LocalizableMessage.raw("Starting Replication ProtocolWindowTest : saturateAndRestart"));

    // suffix synchronized
    String testName = "protocolWindowTest";
    // @formatter:off
    Entry repDomainEntry = TestCaseUtils.makeEntry(
        "dn: " + "cn=" + testName + ", cn=domains, " + SYNCHRO_PLUGIN_DN,
        "objectClass: top",
        "objectClass: ds-cfg-replication-domain",
        "cn: " + testName,
        "ds-cfg-base-dn: " + TEST_ROOT_DN_STRING,
        "ds-cfg-replication-server: localhost:" + replServerPort,
        "ds-cfg-server-id: 1",
        "ds-cfg-receive-status: true",
        "ds-cfg-window-size: " + WINDOW_SIZE);
    // @formatter:on

    // Configure replication domain
    DirectoryServer.getConfigurationHandler().addEntry(Converters.from(repDomainEntry));
    assertNotNull(DirectoryServer.getEntry(repDomainEntry.getName()),
          "Unable to add the synchronized server");
    configEntriesToCleanup.add(repDomainEntry.getName());

    ReplicationBroker broker = openReplicationSession(baseDN, 12,
        WINDOW_SIZE, replServerPort, 1000);

    try {

      /* Test that replicationServer monitor and synchro plugin monitor informations
       * publish the correct window size.
       * This allows both to check the monitoring code and to test that
       * configuration is working.
       */
      Thread.sleep(2000);
      assertEquals(checkWindows(WINDOW_SIZE), 3);
      assertEquals(checkChangelogQueueSize(REPLICATION_QUEUE_SIZE), 2);

      // Create an Entry (add operation) that will be later used in the test.
      Entry tmp = personEntry.duplicate(false);
      AddOperation addOp = connection.processAdd(tmp);
      assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);
      assertTrue(DirectoryServer.entryExists(personEntry.getName()),
        "The Add Entry operation failed");

      // Check if the client has received the MSG
      ReplicationMsg msg = broker.receive();
      Assertions.assertThat(msg).isInstanceOf(AddMsg.class);
      AddMsg addMsg =  (AddMsg) msg;

      Operation receivedOp = addMsg.createOperation(connection);
      assertEquals(OperationType.ADD.compareTo(receivedOp.getOperationType()), 0,
          "The received Replication message is not an ADD msg");

      assertEquals(addMsg.getDN(), personEntry.getName(),
        "The received ADD Replication message is not for the excepted DN");

      // send (2 * window + replicationServer queue) modify operations
      // so that window + replicationServer queue get stuck in the replicationServer queue
      int count = WINDOW_SIZE * 2 + REPLICATION_QUEUE_SIZE;
      processModify(count);

      // let some time to the message to reach the replicationServer client
      Thread.sleep(2000);

      // check that the replicationServer only sent WINDOW_SIZE messages
      searchUpdateSent();

      int rcvCount=0;
      try
      {
        while (true)
        {
          broker.receive();
          broker.updateWindowAfterReplay();
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
      // Clean domain
      DN dn = repDomainEntry.getName();
      try
      {
        DeleteOperation op = connection.processDelete(dn);
        if (op.getResultCode() != ResultCode.SUCCESS
            && op.getResultCode() != ResultCode.NO_SUCH_OBJECT)
        {
          logger.error(LocalizableMessage.raw("saturateQueueAndRestart: error cleaning config entry: " + dn));
        }
      } catch (NoSuchElementException e)
      {
        logger.error(LocalizableMessage.raw("saturateQueueAndRestart: error cleaning config entry: " + dn));
      }
      clearChangelogDB(replicationServer);
    }
  }

  private int searchNbMonitorEntries(String filterString) throws Exception
  {
    InternalSearchOperation op = connection.processSearch(newSearchRequest("cn=monitor", WHOLE_SUBTREE, filterString));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    return op.getEntriesSent();
  }

  /**
   * Check that the ReplicationServer queue size has correctly been configured
   * by reading the monitoring information.
   */
  private int checkChangelogQueueSize(int changelog_queue_size) throws Exception
  {
    return searchNbMonitorEntries("(max-waiting-changes=" + changelog_queue_size + ")");
  }

  /**
   * Check that the window configuration has been successful
   * by reading the monitoring information and checking
   * that we do have 2 entries with the configured max-rcv-window.
   */
  private int checkWindows(int windowSize) throws Exception
  {
    return searchNbMonitorEntries("(max-rcv-window=" + windowSize + ")");
  }

  /**
   * Search that the replicationServer has stopped sending changes after
   * having reach the limit of the window size.
   * And that the number of waiting changes is accurate.
   * Do this by checking the monitoring information.
   */
  private void searchUpdateSent() throws Exception
  {
    assertEquals(searchNbMonitorEntries("(sent-updates=" + WINDOW_SIZE + ")"), 1);

    final int nb = searchNbMonitorEntries(
        "(missing-changes=" + (REPLICATION_QUEUE_SIZE + WINDOW_SIZE) + ")");
    assertEquals(nb, 1);
  }

  /**
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    super.setUp();

    baseDN = DN.valueOf(TEST_ROOT_DN_STRING);

    replServerPort = TestCaseUtils.findFreePort();

    // configure the replication Server.
    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        replServerPort, "protocolWindowTestDb", 0, 1, REPLICATION_QUEUE_SIZE, WINDOW_SIZE, null));

    // @formatter:off
    personEntry = TestCaseUtils.makeEntry(
        "dn: uid=user.windowTest," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "homePhone: 951-245-7634",
        "description: This is the description for Aaccf Amar.",
        "st: NC",
        "mobile: 027-085-0537",
        "postalAddress: Aaccf Amar$17984 Thirteenth Street$Rockford, NC  85762",
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
        "initials: AA\n");
    // @formatter:on
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
        connection.processModify(personEntry.getName(), mods);
      assertEquals(modOp.getResultCode(), ResultCode.SUCCESS);
    }
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    replicationServer.remove();

    paranoiaCheck();
  }
}
