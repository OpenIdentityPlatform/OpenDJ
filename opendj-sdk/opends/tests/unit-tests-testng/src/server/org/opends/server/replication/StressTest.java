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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.server.replication;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.messages.Category;
import org.opends.messages.Severity;
import org.opends.messages.Message;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Stress test for the synchronization code using the ReplicationBroker API.
 */
public class StressTest extends ReplicationTestCase
{
  private static final String REPLICATION_STRESS_TEST =
    "Replication Stress Test";

  private BrokerReader reader = null;

  /**
   * A "person" entry
   */
  protected Entry personEntry;

  private int replServerPort;


  /**
   * Stress test from LDAP server to client using the ReplicationBroker API.
   */
  @Test(enabled=false, groups="slow")
  public void fromServertoBroker() throws Exception
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE,
        "Starting replication StressTest : fromServertoBroker"));

    final DN baseDn = DN.decode("ou=People," + TEST_ROOT_DN_STRING);
    final int TOTAL_MESSAGES = 1000;

    ReplicationBroker broker =
      openReplicationSession(baseDn, (short) 18, 100, replServerPort, 5000, true);
    Monitor monitor = new Monitor("stress test monitor");
    DirectoryServer.registerMonitorProvider(monitor);

    try {
      /*
       * Test that operations done on this server are sent to the
       * replicationServer and forwarded to our replicationServer broker session.
       */

      // Create an Entry (add operation) that will be later used in the test.
      Entry tmp = personEntry.duplicate(false);
      AddOperationBasis addOp = new AddOperationBasis(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, tmp.getDN(),
          tmp.getObjectClasses(), tmp.getUserAttributes(),
          tmp.getOperationalAttributes());
      addOp.run();
      assertTrue(DirectoryServer.entryExists(personEntry.getDN()),
        "The Add Entry operation failed");
      if (ResultCode.SUCCESS == addOp.getResultCode())
      {
        // Check if the client has received the msg
        ReplicationMsg msg = broker.receive();

        assertTrue(msg instanceof AddMsg,
        "The received replication message is not an ADD msg");
        AddMsg addMsg =  (AddMsg) msg;

        Operation receivedOp = addMsg.createOperation(connection);
        assertTrue(OperationType.ADD.compareTo(receivedOp.getOperationType()) == 0,
        "The received replication message is not an ADD msg");

        assertEquals(DN.decode(addMsg.getDn()),personEntry.getDN(),
        "The received ADD replication message is not for the excepted DN");
      }

      reader = new BrokerReader(broker);
      reader.start();

      int count = TOTAL_MESSAGES;

      // Create a number of writer thread that will loop modifying the entry
      List<Thread> writerThreadList = new LinkedList<Thread>();
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

      int rcvCount = reader.getCount();

      if (rcvCount != TOTAL_MESSAGES)
      {
        fail("some messages were lost : expected : " +TOTAL_MESSAGES +
            " received : " + rcvCount);
      }

    }
    finally {
      DirectoryServer.deregisterMonitorProvider(REPLICATION_STRESS_TEST);
      broker.stop();
    }
  }

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
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

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // Change log
    String replServerLdif =
      "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: StressTest\n"    
        + "ds-cfg-replication-server-id: 106\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

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
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

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

    configureReplication();
  }

  private class BrokerWriter extends Thread
  {
    int count;

    /**
     * Creates a new Stress Test Reader
     * @param count
     */
    public BrokerWriter(int count)
    {
      this.count = count;
    }

    /**
     * {@inheritDoc}
     */
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
          connection.processModify(personEntry.getDN(), mods);
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
    private int count = 0;
    private Boolean finished = false;

    /**
     * Creates a new Stress Test Reader
     * @param broker
     */
    public BrokerReader(ReplicationBroker broker)
    {
      this.broker = broker;
    }

    /**
     * {@inheritDoc}
     */
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
            break;
          count ++;
        }
      } catch (Exception e)
      {}
      finally
      {
        synchronized (this)
        {
          finished = true;
          this.notify();
        }
      }
    }

    /**
     * wait until the thread has finished its job then return the number of
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
            this.wait(6000);
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
    protected Monitor(String threadName)
    {
      super(threadName);
    }

    @Override
    public List<Attribute> getMonitorData()
    {
      Attribute attr;
      if (reader == null)
        attr = Attributes.create("received-messages", "not yet started");
      else
        attr = Attributes.create("received-messages", String
            .valueOf(reader.getCurrentCount()));
      List<Attribute> list = new LinkedList<Attribute>();
      list.add(attr);
      attr = Attributes.create("base-dn", "ou=People," + TEST_ROOT_DN_STRING);
      list.add(attr);
      return list;
    }

    @Override
    public String getMonitorInstanceName()
    {
      return REPLICATION_STRESS_TEST;
    }

    @Override
    public void updateMonitorData()
    {
      // nothing to do

    }

    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
    throws ConfigException, InitializationException
    {
      // nothing to do

    }

    @Override
    public long getUpdateInterval()
    {
      // we don't wont to do polling on this monitor
      return 0;
    }



  }
}
