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
package org.opends.server.replication.server;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import static org.opends.server.replication.protocol.OperationContext.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplServerStartMessage;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.ServerStartMessage;
import org.opends.server.replication.protocol.SocketSession;
import org.opends.server.replication.protocol.WindowMessage;
import org.opends.server.replication.protocol.WindowProbe;
import org.opends.server.replication.server.ReplicationServer;
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
 * Tests for the replicationServer code.
 */

public class ReplicationServerTest extends ReplicationTestCase
{
  /**
   * The replicationServer that will be used in this test.
   */
  private ReplicationServer replicationServer = null;

  /**
   * The port of the replicationServer.
   */
  private int replicationServerPort;

  private ChangeNumber firstChangeNumberServer1 = null;
  private ChangeNumber secondChangeNumberServer1 = null;
  private ChangeNumber firstChangeNumberServer2 = null;
  private ChangeNumber secondChangeNumberServer2 = null;

  private ChangeNumber unknownChangeNumberServer1;


  /**
   * Before starting the tests, start the server and configure a
   * replicationServer.
   */
  @BeforeClass()
  public void configure() throws Exception
  {
    TestCaseUtils.startServer();

    //  find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replicationServerPort = socket.getLocalPort();
    socket.close();

    ReplServerFakeConfiguration conf =
      new ReplServerFakeConfiguration(replicationServerPort, null, 0, 1, 0, 0, null);
    replicationServer = new ReplicationServer(conf);
  }

  /**
   * Basic test of the replicationServer code :
   *  Connect 2 clients to the replicationServer and exchange messages
   *  between the clients.
   *
   * Note : Other tests in this file depends on this test and may need to
   *        change if this test is modified.
   */
  @Test()
  public void changelogBasic() throws Exception
  {
    ReplicationBroker server1 = null;
    ReplicationBroker server2 = null;

    try {
      /*
       * Open a sender session and a receiver session to the replicationServer
       */
      server1 = openReplicationSession(
          DN.decode("dc=example,dc=com"), (short) 1, 100, replicationServerPort,
          1000, true);
      server2 = openReplicationSession(
          DN.decode("dc=example,dc=com"), (short) 2, 100, replicationServerPort,
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
       * change sent to the replicationServer but that will be used
       * in the Server State when opening a connection to the
       * ReplicationServer to make sure that the ReplicationServer is
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
      ReplicationMessage msg2 = server2.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received.");

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
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received.");

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
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received.");

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
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received.");
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
    ReplicationBroker broker = null;

    try {
      broker =
        openReplicationSession(DN.decode("dc=example,dc=com"), (short) 3,
                             100, replicationServerPort, 1000, false);

      ReplicationMessage msg2 = broker.receive();
      if (!(msg2 instanceof DeleteMsg))
        fail("ReplicationServer basic transmission failed");
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
    ReplicationBroker broker = null;

    /*
     * Connect to the replicationServer using the state created above.
     */
    try {
      broker =
        openReplicationSession(DN.decode("dc=example,dc=com"), (short) 3,
                             100, replicationServerPort, 1000, state);

      ReplicationMessage msg2 = broker.receive();
      if (!(msg2 instanceof DeleteMsg))
        fail("ReplicationServer basic transmission failed");
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
   * ReplicationServer has not seen.
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
   * after stopping and restarting the replicationServer.
   */
  @Test(enabled=true, dependsOnMethods = { "changelogBasic" })
  public void stopChangelog() throws Exception
  {
    replicationServer.shutdown();
    configure();
    newClient();
    newClientWithFirstChanges();
    newClientWithChangefromServer1();
    newClientWithChangefromServer2();
  }

  /**
   * Stress test from client using the ReplicationBroker API
   * to the replicationServer.
   * This test allow to investigate the behaviour of the
   * ReplicationServer when it needs to distribute the load of
   * updates from a single LDAP server to a number of LDAP servers.
   *
   * This test i sconfigured by a relatively low stress
   * but can be changed using TOTAL_MSG and CLIENT_THREADS consts.
   */
  @Test(enabled=true, groups="slow")
  public void oneWriterMultipleReader() throws Exception
  {
    ReplicationBroker server = null;
    int TOTAL_MSG = 1000;     // number of messages to send during the test
    int CLIENT_THREADS = 2;   // number of threads that will try to read
                              // the messages
    ChangeNumberGenerator gen =
      new ChangeNumberGenerator((short)5 , (long) 0);

    BrokerReader client[] = new BrokerReader[CLIENT_THREADS];
    ReplicationBroker clientBroker[] = new ReplicationBroker[CLIENT_THREADS];

    try
    {
      /*
       * Open a sender session
       */
      server = openReplicationSession(
          DN.decode("dc=example,dc=com"), (short) 5, 100, replicationServerPort,
          1000, 1000, 0, true);

      BrokerReader reader = new BrokerReader(server);

      /*
       * Start the client threads.
       */
      for (int i =0; i< CLIENT_THREADS; i++)
      {
        clientBroker[i] = openReplicationSession(
            DN.decode("dc=example,dc=com"), (short) (100+i), 100, replicationServerPort,
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
       * to the replicationServer.
       */
      for (int i = 0; i< TOTAL_MSG; i++)
      {
        DeleteMsg msg =
          new DeleteMsg("o=test,dc=example,dc=com", gen.newChangeNumber(),
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
        if (clientBroker[i] != null)
          clientBroker[i].stop();
      }
    }
  }

  /**
   * Stress test from client using the ReplicationBroker API
   * to the replicationServer.
   *
   * This test allow to investigate the behaviour of the
   * ReplicationServer when it needs to distribute the load of
   * updates from multiple LDAP server to a number of LDAP servers.
   *
   * This test is sconfigured for a relatively low stress
   * but can be changed using TOTAL_MSG and THREADS consts.
   */
  @Test(enabled=true, groups="slow")
  public void multipleWriterMultipleReader() throws Exception
  {
    ReplicationBroker server = null;
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
        ReplicationBroker broker =
          openReplicationSession( DN.decode("dc=example,dc=com"), serverId,
            100, replicationServerPort, 1000, 1000, 0, true);

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
   * Chaining tests of the replication Server code with 2 replication servers involved
   * 2 tests are done here (itest=0 or itest=1)
   *
   * Test 1
   * - Create replication server 1
   * - Create replication server 2 connected with replication server 1
   * - Create and connect client 1 to replication server 1
   * - Create and connect client 2 to replication server 2
   * - Make client1 publish changes
   * - Check that client 2 receives the changes published by client 1
   *
   * Test 2
   * - Create replication server 1
   * - Create and connect client1 to replication server 1
   * - Make client1 publish changes
   * - Create replication server 2 connected with replication server 1
   * - Create and connect client 2 to replication server 2
   * - Check that client 2 receives the changes published by client 1
   *
   */
  @Test(enabled=true)
  public void changelogChaining() throws Exception
  {
    for (int itest = 0; itest <2; itest++)
    {
      ReplicationBroker broker2 = null;
      boolean emptyOldChanges = true;

      // - Create 2 connected replicationServer
      ReplicationServer[] changelogs = new ReplicationServer[2];
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

        // for itest=0, create the 2 connected replicationServer
        // for itest=1, create the 1rst replicationServer, the second
        // one will be created later
        SortedSet<String> servers = new TreeSet<String>();
        servers.add(
          "localhost:" + ((i == 0) ? changelogPorts[1] : changelogPorts[0]));
        ReplServerFakeConfiguration conf =
          new ReplServerFakeConfiguration(changelogPorts[i], "changelogDb"+i, 0,
                                         changelogIds[i], 0, 100, servers);
        replicationServer = new ReplicationServer(conf);
      }

      ReplicationBroker broker1 = null;

      try
      {
        // For itest=0, create and connect client1 to changelog1
        //              and client2 to changelog2
        // For itest=1, only create and connect client1 to changelog1
        //              client2 will be created later
        broker1 = openReplicationSession(DN.decode("dc=example,dc=com"),
             brokerIds[0], 100, changelogPorts[0], 1000, !emptyOldChanges);

        if (itest == 0)
        {
          broker2 = openReplicationSession(DN.decode("dc=example,dc=com"),
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
          ReplServerFakeConfiguration conf =
            new ReplServerFakeConfiguration(changelogPorts[1], null, 0,
                                           changelogIds[1], 0, 0, null);
          changelogs[1] = new ReplicationServer(conf);

          // Connect broker 2 to changelog2
          broker2 = openReplicationSession(DN.decode("dc=example,dc=com"),
              brokerIds[1], 100, changelogPorts[1], 2000, !emptyOldChanges);
        }

        // - Check msg receives by broker, through changeLog2
        while (ts > 1)
        {
          ReplicationMessage msg2;
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
            fail("ReplicationServer transmission failed: no expected message class.");
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
   * Test that the Replication sends back correctly WindowsUpdate
   * when we send a WindowProbe.
   */
  @Test(enabled=false)
  public void windowProbeTest() throws Exception
  {
    final int WINDOW = 10;
    /*
     * Open a socket connection to the replication server
     */
    InetSocketAddress ServerAddr = new InetSocketAddress(
        InetAddress.getByName("localhost"), replicationServerPort);
    Socket socket = new Socket();
    socket.setReceiveBufferSize(1000000);
    socket.setTcpNoDelay(true);
    socket.connect(ServerAddr, 500);
    SocketSession session = new SocketSession(socket);

    /*
     * Send our ServerStartMessage.
     */
    ServerStartMessage msg =
      new ServerStartMessage((short) 1723, DN.decode("dc=example,dc=com"),
          0, 0, 0, 0, WINDOW, (long) 5000, new ServerState(),
          ProtocolVersion.currentVersion());
    session.publish(msg);

    /*
     * Read the ReplServerStartMessage that should come back.
     */
    session.setSoTimeout(10000);
    ReplServerStartMessage replStartMsg =
      (ReplServerStartMessage) session.receive();
    int serverwindow = replStartMsg.getWindowSize();

    // push a WindowProbe message 
    session.publish(new WindowProbe());
    
    WindowMessage windowMsg = (WindowMessage) session.receive();
    assertEquals(serverwindow, windowMsg.getNumAck());
    
    // check that this did not change the window by sending a probe again.
    session.publish(new WindowProbe());
    
    windowMsg = (WindowMessage) session.receive();
    assertEquals(serverwindow, windowMsg.getNumAck());
  }


  /**
   * After the tests stop the replicationServer.
   */
  @AfterClass()
  public void shutdown() throws Exception
  {
    if (replicationServer != null)
      replicationServer.shutdown();
  }

  /**
   * This class allows to creater reader thread.
   * They continuously reads messages from a replication broker until
   * there is nothing left.
   * They Count the number of received messages.
   */
  private class BrokerReader extends Thread
  {
    private ReplicationBroker broker;

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
          ReplicationMessage msg = broker.receive();
          if (msg == null)
            break;
          }
      } catch (Exception e) {
      }
    }
  }

  /**
   * This class allows to create writer thread that can
   * be used as producers for the ReplicationServer stress tests.
   */
  private class BrokerWriter extends Thread
  {
    int count;
    private ReplicationBroker broker;
    ChangeNumberGenerator gen;

    public BrokerWriter(ReplicationBroker broker, ChangeNumberGenerator gen,
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
       * to the replicationServer.
       */
      while (count>0)
      {
        count--;

        DeleteMsg msg =
          new DeleteMsg("o=test,dc=example,dc=com", gen.newChangeNumber(),
              "uid");
        broker.publish(msg);
      }
    }
  }
}
