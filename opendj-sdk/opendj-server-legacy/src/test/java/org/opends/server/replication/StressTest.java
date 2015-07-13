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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Stress test for the synchronization code using the ReplicationBroker API.
 */
@SuppressWarnings("javadoc")
public class StressTest extends ReplicationTestCase
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  private static final String REPLICATION_STRESS_TEST =
    "Replication Stress Test";

  private BrokerReader reader;

  /** A "person" entry. */
  private Entry personEntry;

  private int replServerPort;


  /**
   * Stress test from LDAP server to client using the ReplicationBroker API.
   */
  @Test(enabled=false, groups="slow")
  public void fromServertoBroker() throws Exception
  {
    logger.error(LocalizableMessage.raw("Starting replication StressTest : fromServertoBroker"));

    final DN baseDN = DN.valueOf("ou=People," + TEST_ROOT_DN_STRING);
    final int TOTAL_MESSAGES = 1000;

    ReplicationBroker broker =
      openReplicationSession(baseDN, 18, 100, replServerPort, 5000);
    Monitor monitor = new Monitor();
    DirectoryServer.registerMonitorProvider(monitor);

    try {
      /*
       * Test that operations done on this server are sent to the
       * replicationServer and forwarded to our replicationServer broker session.
       */

      // Create an Entry (add operation) that will be later used in the test.
      Entry tmp = personEntry.duplicate(false);
      AddOperation addOp = connection.processAdd(tmp);
      assertTrue(DirectoryServer.entryExists(personEntry.getName()),
        "The Add Entry operation failed");
      if (ResultCode.SUCCESS == addOp.getResultCode())
      {
        // Check if the client has received the msg
        ReplicationMsg msg = broker.receive();
        Assertions.assertThat(msg).isInstanceOf(AddMsg.class);
        AddMsg addMsg =  (AddMsg) msg;

        Operation receivedOp = addMsg.createOperation(connection);
        assertEquals(receivedOp.getOperationType(), OperationType.ADD,
        "The received replication message is not an ADD msg");
        assertEquals(addMsg.getDN(), personEntry.getName(),
        "The received ADD replication message is not for the excepted DN");
      }

      reader = new BrokerReader(broker);
      reader.start();

      int count = TOTAL_MESSAGES;

      // Create a number of writer thread that will loop modifying the entry
      List<Thread> writerThreadList = new LinkedList<>();
      for (int n = 0; n < 1; n++)
      {
        BrokerWriter writer = new BrokerWriter(count);
        writerThreadList.add(writer);
      }
      for (Thread thread : writerThreadList)
      {
        thread.start();
      }
      // wait for all the threads to finish.
      for (Thread thread : writerThreadList)
      {
        thread.join();
      }

      assertEquals(reader.getCount(), TOTAL_MESSAGES, "some messages were lost");
    }
    finally {
      DirectoryServer.deregisterMonitorProvider(monitor);
      broker.stop();
    }
  }

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    // This test suite depends on having the schema available.

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    // Create necessary backend top level entry
    String topEntry = "dn: ou=People," + TEST_ROOT_DN_STRING + "\n"
        + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
    TestCaseUtils.addEntry(topEntry);

    replServerPort = TestCaseUtils.findFreePort();

    // Change log
    String replServerLdif =
      "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: StressTest\n"
        + "ds-cfg-replication-server-id: 106\n";

    // suffix synchronized
    String testName = "stressTest";
    String synchroServerLdif =
      "dn: cn=" + testName + ", cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: ou=People," + TEST_ROOT_DN_STRING + "\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-server-id: 1\n" + "ds-cfg-receive-status: true\n";

    String personLdif = "dn: uid=user.1,ou=People," + TEST_ROOT_DN_STRING + "\n"
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

    configureReplication(replServerLdif, synchroServerLdif);
  }

  private class BrokerWriter extends Thread
  {
    int count;

    /** Creates a new Stress Test Reader. */
    public BrokerWriter(int count)
    {
      this.count = count;
    }

    /** {@inheritDoc} */
    @Override
    public void run()
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
  }

  /**
   * Continuously reads messages from a replicationServer broker until there is nothing
   * left. Count the number of received messages.
   */
  private class BrokerReader extends Thread
  {
    private ReplicationBroker broker;
    private int count;
    private Boolean finished = false;

    /**
     * Creates a new Stress Test Reader.
     */
    public BrokerReader(ReplicationBroker broker)
    {
      this.broker = broker;
    }

    /** {@inheritDoc} */
    @Override
    public void run()
    {
      // loop receiving messages until either we get a timeout
      // because there is nothing left or an error condition happens.
      try
      {
        while (true)
        {
          ReplicationMsg msg = broker.receive();
          if (msg == null)
          {
            break;
          }
          count ++;
        }
      } catch (Exception e)
      {}
      finally
      {
        synchronized (this)
        {
          finished = true;
          notify();
        }
      }
    }

    /**
     * Wait until the thread has finished its job then return the number of
     * received messages.
     */
    public int getCount()
    {
      synchronized (this)
      {
        int i = 20;
        while ((finished != true) && (i-- >0))
        {
          try
          {
            wait(6000);
          } catch (InterruptedException e)
          {
            return -1;
          }
        }
        return count;
      }
    }

    public int getCurrentCount()
    {
      return count;
    }
  }

  private class Monitor extends MonitorProvider<MonitorProviderCfg>
  {
    @Override
    public List<Attribute> getMonitorData()
    {
      String value = reader != null ? String.valueOf(reader.getCurrentCount()) : "not yet started";
      List<Attribute> list = new LinkedList<>();
      list.add(Attributes.create("received-messages", value));
      list.add(Attributes.create("base-dn", "ou=People," + TEST_ROOT_DN_STRING));
      return list;
    }

    @Override
    public String getMonitorInstanceName()
    {
      return REPLICATION_STRESS_TEST;
    }

    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
    throws ConfigException, InitializationException
    {
      // nothing to do

    }



  }
}
