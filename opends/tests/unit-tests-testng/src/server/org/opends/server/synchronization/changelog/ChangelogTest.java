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
package org.opends.server.synchronization.changelog;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import static org.opends.server.synchronization.protocol.OperationContext.*;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.common.ChangeNumberGenerator;
import org.opends.server.synchronization.common.ServerState;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.protocol.AddMsg;
import org.opends.server.synchronization.protocol.DeleteMsg;
import org.opends.server.synchronization.protocol.ModifyDNMsg;
import org.opends.server.synchronization.protocol.ModifyDnContext;
import org.opends.server.synchronization.protocol.ModifyMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for the changelog service code.
 */

public class ChangelogTest extends SynchronizationTestCase
{
  /**
   * The changelog server that will be used in this test.
   */
  private Changelog changelog = null;

  /**
   * The port of the changelog server.
   */
  private int changelogPort;

  private ChangeNumber firstChangeNumberServer1 = null;
  private ChangeNumber secondChangeNumberServer1 = null;
  private ChangeNumber firstChangeNumberServer2 = null;
  private ChangeNumber secondChangeNumberServer2 = null;

  private ChangeNumber unknownChangeNumberServer1;


  /**
   * Before starting the tests, start the server and configure a
   * changelog server.
   */
  @BeforeClass()
  public void configure() throws Exception
  {
    TestCaseUtils.startServer();

    //  find  a free port for the changelog server
    ServerSocket socket = TestCaseUtils.bindFreePort();
    changelogPort = socket.getLocalPort();
    socket.close();

    ChangelogFakeConfiguration conf =
      new ChangelogFakeConfiguration(changelogPort, null, 0, 1, 0, 0, null); 
    changelog = new Changelog(conf);
  }

  /**
   * Basic test of the changelog code :
   *  Connect 2 clients to the changelog server and exchange messages
   *  between the clients.
   *
   * Note : Other tests in this file depends on this test and may need to
   *        change if this test is modified.
   */
  @Test()
  public void changelogBasic() throws Exception
  {
    ChangelogBroker server1 = null;
    ChangelogBroker server2 = null;

    try {
      /*
       * Open a sender session and a receiver session to the changelog
       */
      server1 = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 1, 100, changelogPort,
          1000, true);
      server2 = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 2, 100, changelogPort,
          1000, true);

      /*
       * Create change numbers for the messages sent from server 1
       * with current time  sequence 1 and with current time + 2 sequence 2
       */
      long time = TimeThread.getTime();
      firstChangeNumberServer1 = new ChangeNumber(time, 1, (short) 1);
      secondChangeNumberServer1 = new ChangeNumber(time + 2, 2, (short) 1);

      /*
       * Create change numbers for the messages sent from server 2
       * with current time  sequence 1 and with current time + 3 sequence 2
       */
      firstChangeNumberServer2 = new ChangeNumber(time+ 1, 1, (short) 2);
      secondChangeNumberServer2 = new ChangeNumber(time + 3, 2, (short) 2);
      
      /*
       * Create a ChangeNumber between firstChangeNumberServer1 and  
       * secondChangeNumberServer1 that will not be used to create a
       * change sent to the changelog server but that will be used
       * in the Server State when opening a connection to the 
       * Changelog Server to make sure that the Changelog server is 
       * able to accept such clients.
       */
      unknownChangeNumberServer1 = new ChangeNumber(time+1, 1, (short) 1);

      /*
       * Send and receive a Delete Msg from server 1 to server 2
       */
      DeleteMsg msg =
        new DeleteMsg("o=test,dc=example,dc=com", firstChangeNumberServer1,
                      "uid");
      server1.publish(msg);
      SynchronizationMessage msg2 = server2.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");

      /*
       * Send and receive a second Delete Msg
       */
      msg = new DeleteMsg("o=test", secondChangeNumberServer1, "uid");
      server1.publish(msg);
      msg2 = server2.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");

      /*
       * Send and receive a Delete Msg from server 1 to server 2
       */
      msg =
        new DeleteMsg("o=test,dc=example,dc=com", firstChangeNumberServer2,
                      "other-uid");
      server2.publish(msg);
      msg2 = server1.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");

      /*
       * Send and receive a second Delete Msg
       */
      msg = new DeleteMsg("o=test", secondChangeNumberServer2, "uid");
      server2.publish(msg);
      msg2 = server1.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");
    }
    finally
    {
      if (server1 != null)
        server1.stop();
      if (server2 != null)
        server2.stop();
    }
  }

  /**
   * Test that a new client see the change that was sent in the
   * previous test.
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void newClient() throws Exception
  {
    ChangelogBroker broker = null;

    try {
      broker =
        openChangelogSession(DN.decode("dc=example,dc=com"), (short) 3,
                             100, changelogPort, 1000, false);

      SynchronizationMessage msg2 = broker.receive();
      if (!(msg2 instanceof DeleteMsg))
        fail("Changelog basic transmission failed");
      else
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.getChangeNumber().equals(firstChangeNumberServer1),
            "The first message received by a new client was the wrong one."
            + del.getChangeNumber() + " " + firstChangeNumberServer1);
      }
    }
    finally
    {
      if (broker != null)
        broker.stop();
    }
  }



  /**
   * Test that a client that has already seen some changes now receive
   * the correct next change.
   */
  private void newClientWithChanges(
      ServerState state, ChangeNumber nextChangeNumber) throws Exception
  {
    ChangelogBroker broker = null;

    /*
     * Connect to the changelog server using the state created above.
     */
    try {
      broker =
        openChangelogSession(DN.decode("dc=example,dc=com"), (short) 3,
                             100, changelogPort, 1000, state);

      SynchronizationMessage msg2 = broker.receive();
      if (!(msg2 instanceof DeleteMsg))
        fail("Changelog basic transmission failed");
      else
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.getChangeNumber().equals(nextChangeNumber),
            "The second message received by a new client was the wrong one."
            + del.getChangeNumber() + " " + nextChangeNumber);
      }
    }
    finally
    {
      if (broker != null)
        broker.stop();
    }
  }

  /**
   * Test that a client that has already seen the first change now see the
   * second change
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void newClientWithFirstChanges() throws Exception
  {
    /*
     * Create a ServerState updated with the first changes from both servers
     * done in test changelogBasic.
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer1);
    state.update(firstChangeNumberServer2);

    newClientWithChanges(state, secondChangeNumberServer1);
  }
  
  /**
   * Test with a client that has already seen a Change that the
   * Changelog server has not seen.
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void newClientWithUnknownChanges() throws Exception
  {
    /*
     * Create a ServerState with wrongChangeNumberServer1
     */
    ServerState state = new ServerState();
    state.update(unknownChangeNumberServer1);
    state.update(secondChangeNumberServer2);

    newClientWithChanges(state, secondChangeNumberServer1);
  }

  /**
   * Test that a client that has already seen the first change from server 1
   * now see the first change from server 2
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void newClientWithChangefromServer1() throws Exception
  {
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer1);

    newClientWithChanges(state, firstChangeNumberServer2);
  }

  /**
   * Test that a client that has already seen the first chaneg from server 2
   * now see the first change from server 1
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void newClientWithChangefromServer2() throws Exception
  {
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer2);

    newClientWithChanges(state, firstChangeNumberServer1);
  }

  /**
   * Test that a client that has not seen the second change from server 1
   * now receive it.
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void newClientLateServer1() throws Exception
  {
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(secondChangeNumberServer2);
    state.update(firstChangeNumberServer1);

    newClientWithChanges(state, secondChangeNumberServer1);
  }

  /**
   * Test that newClient() and newClientWithFirstChange() still works
   * after stopping and restarting the changelog server.
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void stopChangelog() throws Exception
  {
    changelog.shutdown();
    configure();
    newClient();
    newClientWithFirstChanges();
    newClientWithChangefromServer1();
    newClientWithChangefromServer2();
  }

  /**
   * Stress test from client using the ChangelogBroker API
   * to the changelog server.
   * This test allow to investigate the behaviour of the
   * Changelog server when it needs to distribute the load of
   * updates from a single LDAP server to a number of LDAP servers.
   *
   * This test i sconfigured by a relatively low stress
   * but can be changed using TOTAL_MSG and CLIENT_THREADS consts.
   */
  @Test(enabled=true, groups="slow")
  public void oneWriterMultipleReader() throws Exception
  {
    ChangelogBroker server = null;
    int TOTAL_MSG = 1000;     // number of messages to send during the test
    int CLIENT_THREADS = 2;   // number of threads that will try to read
                              // the messages
    ChangeNumberGenerator gen =
      new ChangeNumberGenerator((short)5 , (long) 0);

    BrokerReader client[] = new BrokerReader[CLIENT_THREADS];
    ChangelogBroker clientBroker[] = new ChangelogBroker[CLIENT_THREADS];

    try
    {
      /*
       * Open a sender session
       */
      server = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 5, 100, changelogPort,
          1000, 1000, 0, true);

      BrokerReader reader = new BrokerReader(server);

      /*
       * Start the client threads.
       */
      for (int i =0; i< CLIENT_THREADS; i++)
      {
        clientBroker[i] = openChangelogSession(
            DN.decode("dc=example,dc=com"), (short) (100+i), 100, changelogPort,
            1000, true);
        client[i] = new BrokerReader(clientBroker[i]);
      }

      for (int i =0; i< CLIENT_THREADS; i++)
      {
        client[i].start();
      }
      reader.start();

      /*
       * Simple loop creating changes and sending them
       * to the changelog server.
       */
      for (int i = 0; i< TOTAL_MSG; i++)
      {
        DeleteMsg msg =
          new DeleteMsg("o=test,dc=example,dc=com", gen.NewChangeNumber(),
          "uid");
        server.publish(msg);
      }

      for (int i =0; i< CLIENT_THREADS; i++)
      {
        client[i].join();
        reader.join();
      }
    }
    finally
    {
      if (server != null)
        server.stop();
      for (int i =0; i< CLIENT_THREADS; i++)
      {
        clientBroker[i].stop();
      }
    }
  }

  /**
   * Stress test from client using the ChangelogBroker API
   * to the changelog server.
   *
   * This test allow to investigate the behaviour of the
   * Changelog server when it needs to distribute the load of
   * updates from multiple LDAP server to a number of LDAP servers.
   *
   * This test is sconfigured for a relatively low stress
   * but can be changed using TOTAL_MSG and THREADS consts.
   */
  @Test(enabled=true, groups="slow")
  public void multipleWriterMultipleReader() throws Exception
  {
    ChangelogBroker server = null;
    final int TOTAL_MSG = 1000;   // number of messages to send during the test
    final int THREADS = 2;       // number of threads that will produce
                               // and read the messages.

    BrokerWriter producer[] = new BrokerWriter[THREADS];
    BrokerReader reader[] = new BrokerReader[THREADS];

    try
    {
      /*
       * Start the producer threads.
       */
      for (int i =0; i< THREADS; i++)
      {
        short serverId = (short) (10+i);
        ChangeNumberGenerator gen =
          new ChangeNumberGenerator(serverId , (long) 0);
        ChangelogBroker broker =
          openChangelogSession( DN.decode("dc=example,dc=com"), serverId,
            100, changelogPort, 1000, 1000, 0, true);

        producer[i] = new BrokerWriter(broker, gen, TOTAL_MSG/THREADS);
        reader[i] = new BrokerReader(broker);

      }

      for (int i =0; i< THREADS; i++)
      {
        producer[i].start();
      }

      for (int i =0; i< THREADS; i++)
      {
        reader[i].start();
      }

      for (int i =0; i< THREADS; i++)
      {
        producer[i].join();
        reader[i].join();
      }
    }
    finally
    {
      if (server != null)
        server.stop();
    }
  }


  /**
   * Chaining tests of the changelog code with 2 changelog servers involved
   * 2 tests are done here (itest=0 or itest=1)
   *
   * Test 1
   * - Create changelog server 1
   * - Create changelog server 2 connected with changelog server 1
   * - Create and connect client 1 to changelog server 1
   * - Create and connect client 2 to changelog server 2
   * - Make client1 publish changes
   * - Check that client 2 receives the changes published by client 1
   *
   * Test 2
   * - Create changelog server 1
   * - Create and connect client1 to changelog server 1
   * - Make client1 publish changes
   * - Create changelog server 2 connected with changelog server 1
   * - Create and connect client 2 to changelog server 2
   * - Check that client 2 receives the changes published by client 1
   *
   */
  @Test(enabled=true)
  public void changelogChaining() throws Exception
  {
    for (int itest = 0; itest <2; itest++)
    {
      ChangelogBroker broker2 = null;
      boolean emptyOldChanges = true;

      // - Create 2 connected changelog servers
      Changelog[] changelogs = new Changelog[2];
      int[] changelogPorts = new int[2];
      int[] changelogIds = new int[2];
      short[] brokerIds = new short[2];
      ServerSocket socket = null;

      // Find 2 free ports
      for (int i = 0; i <= 1; i++)
      {
        // find  a free port
        socket = TestCaseUtils.bindFreePort();
        changelogPorts[i] = socket.getLocalPort();
        changelogIds[i] = i + 10;
        brokerIds[i] = (short) (100+i);
        if ((itest==0) || (i ==0))
          socket.close();
      }

      for (int i = 0; i <= ((itest == 0) ? 1 : 0); i++)
      {
        changelogs[i] = null;

        // for itest=0, create the 2 connected changelog servers
        // for itest=1, create the 1rst changelog server, the second
        // one will be created later
        SortedSet<String> servers = new TreeSet<String>();
        servers.add(
          "localhost:" + ((i == 0) ? changelogPorts[1] : changelogPorts[0]));
        ChangelogFakeConfiguration conf =
          new ChangelogFakeConfiguration(changelogPorts[i], "changelogDb"+i, 0,
                                         changelogIds[i], 0, 100, servers); 
        changelog = new Changelog(conf);
      }

      ChangelogBroker broker1 = null;

      try
      {
        // For itest=0, create and connect client1 to changelog1
        //              and client2 to changelog2
        // For itest=1, only create and connect client1 to changelog1
        //              client2 will be created later
        broker1 = openChangelogSession(DN.decode("dc=example,dc=com"),
             brokerIds[0], 100, changelogPorts[0], 1000, !emptyOldChanges);

        if (itest == 0)
        {
          broker2 = openChangelogSession(DN.decode("dc=example,dc=com"),
             brokerIds[1], 100, changelogPorts[0], 1000, !emptyOldChanges);
        }

        // - Test messages between clients by publishing now

        // - Delete
        long time = TimeThread.getTime();
        int ts = 1;
        ChangeNumber cn = new ChangeNumber(time, ts++, brokerIds[0]);

        DeleteMsg delMsg = new DeleteMsg("o=test"+itest+",dc=example,dc=com", cn, "uid");
        broker1.publish(delMsg);

        String user1entryUUID = "33333333-3333-3333-3333-333333333333";
        String baseUUID = "22222222-2222-2222-2222-222222222222";

        // - Add
        String lentry = new String("dn: dc=example,dc=com\n"
            + "objectClass: top\n" + "objectClass: domain\n"
            + "entryUUID: 11111111-1111-1111-1111-111111111111\n");
        Entry entry = TestCaseUtils.entryFromLdifString(lentry);
        cn = new ChangeNumber(time, ts++, brokerIds[0]);
        AddMsg addMsg = new AddMsg(cn, "o=test,dc=example,dc=com",
            user1entryUUID, baseUUID, entry.getObjectClassAttribute(), entry
            .getAttributes(), new ArrayList<Attribute>());
        broker1.publish(addMsg);

        // - Modify
        Attribute attr1 = new Attribute("description", "new value");
        Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
        List<Modification> mods = new ArrayList<Modification>();
        mods.add(mod1);
        cn = new ChangeNumber(time, ts++, brokerIds[0]);
        ModifyMsg modMsg = new ModifyMsg(cn, DN
            .decode("o=test,dc=example,dc=com"), mods, "fakeuniqueid");
        broker1.publish(modMsg);

        // - ModifyDN
        cn = new ChangeNumber(time, ts++, brokerIds[0]);
        ModifyDNOperation op = new ModifyDNOperation(connection, 1, 1, null, DN
            .decode("o=test,dc=example,dc=com"), RDN.decode("o=test2"), true,
            null);
        op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(cn, "uniqueid",
        "newparentId"));
        ModifyDNMsg modDNMsg = new ModifyDNMsg(op);
        broker1.publish(modDNMsg);

        if (itest > 0)
        {
          socket.close();
          
          SortedSet<String> servers = new TreeSet<String>();
          servers.add("localhost:"+changelogPorts[0]);
          ChangelogFakeConfiguration conf =
            new ChangelogFakeConfiguration(changelogPorts[1], null, 0,
                                           changelogIds[1], 0, 0, null); 
          changelogs[1] = new Changelog(conf);

          // Connect broker 2 to changelog2
          broker2 = openChangelogSession(DN.decode("dc=example,dc=com"),
              brokerIds[1], 100, changelogPorts[1], 2000, !emptyOldChanges);
        }

        // - Check msg receives by broker, through changeLog2
        while (ts > 1)
        {
          SynchronizationMessage msg2;
          try
          {
            msg2 = broker2.receive();
            if (msg2 == null)
              break;
          }
          catch (Exception e)
          {
            fail("Broker receive failed: " + e.getMessage() + "#Msg:" + ts + "#itest:" + itest);
            break;
          }

          if (msg2 instanceof DeleteMsg)
          {
            DeleteMsg delMsg2 = (DeleteMsg) msg2;
            if (delMsg2.toString().equals(delMsg.toString()))
              ts--;
          }
          else if (msg2 instanceof AddMsg)
          {
            AddMsg addMsg2 = (AddMsg) msg2;
            if (addMsg2.toString().equals(addMsg.toString()))
              ts--;
          }
          else if (msg2 instanceof ModifyMsg)
          {
            ModifyMsg modMsg2 = (ModifyMsg) msg2;
            if (modMsg.equals(modMsg2))
              ts--;
          }
          else if (msg2 instanceof ModifyDNMsg)
          {
            ModifyDNMsg modDNMsg2 = (ModifyDNMsg) msg2;
            if (modDNMsg.equals(modDNMsg2))
              ts--;
          }
          else
          {
            fail("Changelog transmission failed: no expected message class.");
            break;
          }
        }
        // Check that everything expected has been received
        assertTrue(ts == 1, "Broker2 did not receive the complete set of"
            + " expected messages: #msg received " + ts);
      }
      finally
      {
        if (changelogs[0] != null)
          changelogs[0].shutdown();
        if (changelogs[1] != null)
          changelogs[1].shutdown();
        if (broker1 != null)
          broker1.stop();
        if (broker2 != null)
          broker2.stop();
      }
    }
  }

  /**
   * After the tests stop the changelog server.
   */
  @AfterClass()
  public void shutdown() throws Exception
  {
    if (changelog != null)
      changelog.shutdown();
  }

  /**
   * This class allows to creater reader thread.
   * They continuously reads messages from a changelog broker until
   * there is nothing left.
   * They Count the number of received messages.
   */
  private class BrokerReader extends Thread
  {
    private ChangelogBroker broker;

    /**
     * Creates a new Stress Test Reader
     * @param broker
     */
    public BrokerReader(ChangelogBroker broker)
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
          SynchronizationMessage msg = broker.receive();
          if (msg == null)
            break;
          }
      } catch (Exception e) {
      }
    }
  }

  /**
   * This class allows to create writer thread that can
   * be used as producers for the Changelog stress tests.
   */
  private class BrokerWriter extends Thread
  {
    int count;
    private ChangelogBroker broker;
    ChangeNumberGenerator gen;

    public BrokerWriter(ChangelogBroker broker, ChangeNumberGenerator gen,
        int count)
    {
      this.broker = broker;
      this.count = count;
      this.gen = gen;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
      /*
       * Simple loop creating changes and sending them
       * to the changelog server.
       */
      while (count>0)
      {
        count--;

        DeleteMsg msg =
          new DeleteMsg("o=test,dc=example,dc=com", gen.NewChangeNumber(),
              "uid");
        broker.publish(msg);
      }
    }
  }
}
