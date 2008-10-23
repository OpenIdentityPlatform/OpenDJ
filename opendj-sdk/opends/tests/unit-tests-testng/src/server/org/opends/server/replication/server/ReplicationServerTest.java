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
package org.opends.server.replication.server;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.replication.protocol.OperationContext.SYNCHROCONTEXT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationServerListener;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.StartSessionMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.protocol.WindowProbeMsg;
import org.opends.server.types.*;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import static org.opends.server.util.ServerConstants.OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.LDAPSearch;
import static org.opends.server.TestCaseUtils.*;

/**
 * Tests for the replicationServer code.
 */

public class ReplicationServerTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();
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
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    // This test suite depends on having the schema available.
    configure();
  }

  /**
   * Start the server and configure a replicationServer.
   */
  protected void configure() throws Exception
  {
    //  find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replicationServerPort = socket.getLocalPort();
    socket.close();

    TestCaseUtils.dsconfig(
        "create-replication-server",
        "--provider-name", "Multimaster Synchronization",
        "--set", "replication-db-directory:" + "replicationServerTestConfigureDb",
        "--set", "replication-port:" + replicationServerPort,
        "--set", "replication-server-id:71");

    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider<?> provider : DirectoryServer
        .getSynchronizationProviders()) {
      if (provider instanceof MultimasterReplication) {
        MultimasterReplication mmp = (MultimasterReplication) provider;
        ReplicationServerListener list = mmp.getReplicationServerListener();
        if (list != null) {
          replicationServer = list.getReplicationServer();
          if (replicationServer != null) {
            break;
          }
        }
      }
    }
  }

  private void debugInfo(String s)
  {
    ErrorLogger.logError(Message.raw(Category.SYNC, Severity.NOTICE, "** TEST ** " + s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST ** " + s);
    }
  }

  /**
   * The tests in this class only works in a specific order.
   * This method is used to make sure that this order is always respected.
   * (Using testng dependency does not work)
   */
  @Test(enabled=true)
  public void replicationServerTest() throws Exception
  {
    replicationServer.clearDb();
    changelogBasic();
    multipleWriterMultipleReader();
    newClientLateServer1();
    newClient();
    newClientWithFirstChanges();
    newClientWithChangefromServer1();
    newClientWithChangefromServer2();
    newClientWithUnknownChanges();
    oneWriterMultipleReader();
    changelogChaining();
    stopChangelog();
    exportBackend();
    backupRestore();
    windowProbeTest();
    replicationServerConnected();
  }
  
  /**
   * This test allows to check the behavior of the Replication Server
   * when the DS disconnect and reconnect again.
   * In order to stress the protocol in such case, connection and
   * disconnection is done inside an infinite loop and therefore this
   * test is disabled and should only be enabled in workspaces but never
   * committed in the repository.
   */
  @Test(enabled=false)
  public void replicationServerTestLoop() throws Exception
  {
    replicationServer.clearDb();
    changelogBasic();
    int count = 0;
    while (true)
    {
      count ++;
      // System.out.println(count);
      newClient();
    }
  }

  /**
   * Basic test of the replicationServer code :
   *  Connect 2 clients to the replicationServer and exchange messages
   *  between the clients.
   *
   * Note : Other tests in this file depends on this test and may need to
   *        change if this test is modified.
   */
  private void changelogBasic() throws Exception
  {
    replicationServer.clearDb();
    debugInfo("Starting changelogBasic");
    ReplicationBroker server1 = null;
    ReplicationBroker server2 = null;

    try {
      /*
       * Open a sender session and a receiver session to the replicationServer
       */
      server1 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 1, 100, replicationServerPort,
          1000, true);
      server2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 2, 100, replicationServerPort,
          1000, true);
      
      assertTrue(server1.isConnected());
      assertTrue(server2.isConnected());

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
        new DeleteMsg("o=example," + TEST_ROOT_DN_STRING, firstChangeNumberServer1,
                      "uid");
      server1.publish(msg);
      ReplicationMsg msg2 = server2.receive();
      server2.updateWindowAfterReplay();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received: " +
          msg2.getClass().toString() + ": content: " + msg2.toString());

      /*
       * Send and receive a second Delete Msg
       */
      msg = new DeleteMsg(TEST_ROOT_DN_STRING, secondChangeNumberServer1, "uid");
      server1.publish(msg);
      msg2 = server2.receive();
      server2.updateWindowAfterReplay();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received: " +
          msg2.getClass().toString() + ": content: " + msg2.toString());

      /*
       * Send and receive a Delete Msg from server 2 to server 1
       */
      msg =
        new DeleteMsg("o=example," + TEST_ROOT_DN_STRING, firstChangeNumberServer2,
                      "other-uid");
      server2.publish(msg);
      msg2 = server1.receive();
      if (!(msg2 instanceof TopologyMsg))
        fail("ReplicationServer basic : incorrect message type received: " +
          msg2.getClass().toString() + ": content: " + msg2.toString());
      msg2 = server1.receive();
      server1.updateWindowAfterReplay();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received: " +
          msg2.getClass().toString() + ": content: " + msg2.toString());

      /*
       * Send and receive a second Delete Msg
       */
      msg = new DeleteMsg(TEST_ROOT_DN_STRING, secondChangeNumberServer2, "uid");
      server2.publish(msg);
      msg2 = server1.receive();
      server1.updateWindowAfterReplay();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "ReplicationServer basic : incorrect message body received.");
      }
      else
        fail("ReplicationServer basic : incorrect message type received: " +
          msg2.getClass().toString() + ": content: " + msg2.toString());
      
      debugInfo("Ending changelogBasic");
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
  private void newClient() throws Exception
  {
    debugInfo("Starting newClient");
    ReplicationBroker broker = null;

    try {
      broker =
        openReplicationSession(DN.decode(TEST_ROOT_DN_STRING), (short) 3,
                             100, replicationServerPort, 1000, false);

      assertTrue(broker.isConnected());

      ReplicationMsg msg2 = broker.receive();
      broker.updateWindowAfterReplay();
      if (!(msg2 instanceof DeleteMsg))
        fail("ReplicationServer basic transmission failed:" + msg2);
      else
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.getChangeNumber().equals(firstChangeNumberServer1),
            "The first message received by a new client was the wrong one : "
            + del.getChangeNumber() + " instead of " + firstChangeNumberServer1);
      }
      debugInfo("Ending newClient");
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
        openReplicationSession(DN.decode(TEST_ROOT_DN_STRING), (short) 3,
                             100, replicationServerPort, 5000, state);

      assertTrue(broker.isConnected());

      ReplicationMsg msg2 = broker.receive();
      broker.updateWindowAfterReplay();
      if (!(msg2 instanceof DeleteMsg))
      {
        fail("ReplicationServer basic transmission failed:" + msg2);
      }
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
  private void newClientWithFirstChanges() throws Exception
  {
    debugInfo("Starting newClientWithFirstChanges");
    /*
     * Create a ServerState updated with the first changes from both servers
     * done in test changelogBasic.
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer1);
    state.update(firstChangeNumberServer2);

    newClientWithChanges(state, secondChangeNumberServer1);
    debugInfo("Ending newClientWithFirstChanges");
  }

  /**
   * Test with a client that has already seen a Change that the
   * ReplicationServer has not seen.
   */
  private void newClientWithUnknownChanges() throws Exception
  {
    debugInfo("Starting newClientWithUnknownChanges");
    /*
     * Create a ServerState with wrongChangeNumberServer1
     */
    ServerState state = new ServerState();
    state.update(unknownChangeNumberServer1);
    state.update(secondChangeNumberServer2);

    newClientWithChanges(state, secondChangeNumberServer1);
    debugInfo("Ending newClientWithUnknownChanges");
  }

  /**
   * Test that a client that has already seen the first change from server 1
   * now see the first change from server 2
   */
  private void newClientWithChangefromServer1() throws Exception
  {
    debugInfo("Starting newClientWithChangefromServer1");
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer1);

    newClientWithChanges(state, firstChangeNumberServer2);
    debugInfo("Ending newClientWithChangefromServer1");
  }

  /**
   * Test that a client that has already seen the first chaneg from server 2
   * now see the first change from server 1
   */
  private void newClientWithChangefromServer2() throws Exception
  {
    debugInfo("Starting newClientWithChangefromServer2");
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer2);

    newClientWithChanges(state, firstChangeNumberServer1);
    debugInfo("Ending newClientWithChangefromServer2");
  }

  /**
   * Test that a client that has not seen the second change from server 1
   * now receive it.
   */
  private void newClientLateServer1() throws Exception
  {
    debugInfo("Starting newClientLateServer1");
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(secondChangeNumberServer2);
    state.update(firstChangeNumberServer1);

    newClientWithChanges(state, secondChangeNumberServer1);
    debugInfo("Ending newClientLateServer1");
  }

  /**
   * Test that newClient() and newClientWithFirstChange() still works
   * after stopping and restarting the replicationServer.
   */
  private void stopChangelog() throws Exception
  {
    debugInfo("Starting stopChangelog");
    shutdown();
    configure();
    newClient();
    newClientWithFirstChanges();
    newClientWithChangefromServer1();
    newClientWithChangefromServer2();
    debugInfo("Ending stopChangelog");
  }

  /**
   * Stress test from client using the ReplicationBroker API
   * to the replicationServer.
   * This test allow to investigate the behaviour of the
   * ReplicationServer when it needs to distribute the load of
   * updates from a single LDAP server to a number of LDAP servers.
   *
   * This test is configured by a relatively low stress
   * but can be changed using TOTAL_MSG and CLIENT_THREADS consts.
   */
  private void oneWriterMultipleReader() throws Exception
  {
    debugInfo("Starting oneWriterMultipleReader");
    ReplicationBroker server = null;
    BrokerReader reader = null;
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
          DN.decode(TEST_ROOT_DN_STRING), (short) 5, 100, replicationServerPort,
          1000, 1000, 0, true);

      assertTrue(server.isConnected());

      reader = new BrokerReader(server);

      /*
       * Start the client threads.
       */
      for (int i =0; i< CLIENT_THREADS; i++)
      {
        clientBroker[i] = openReplicationSession(
            DN.decode(TEST_ROOT_DN_STRING), (short) (100+i), 100, replicationServerPort,
            1000, true);
        assertTrue(clientBroker[i].isConnected());
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
          new DeleteMsg("o=example," + TEST_ROOT_DN_STRING, gen.newChangeNumber(),
          "uid");
        server.publish(msg);
      }
      debugInfo("Ending oneWriterMultipleReader");
    }
    finally
    {
      if (reader != null)
        reader.join();
      if (server != null)
        server.stop();
      for (int i =0; i< CLIENT_THREADS; i++)
      {
        if (client[i] != null)
          client[i].join();
      }
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
   * This test allow to investigate the behavior of the
   * ReplicationServer when it needs to distribute the load of
   * updates from multiple LDAP server to a number of LDAP servers.
   *
   * This test is configured for a relatively low stress
   * but can be changed using TOTAL_MSG and THREADS consts.
   */
  private void multipleWriterMultipleReader() throws Exception
  {
    debugInfo("Starting multipleWriterMultipleReader");
    final int TOTAL_MSG = 1000;   // number of messages to send during the test
    final int THREADS = 2;       // number of threads that will produce
                               // and read the messages.

    BrokerWriter producer[] = new BrokerWriter[THREADS];
    BrokerReader reader[] = new BrokerReader[THREADS];
    ReplicationBroker broker[] = new ReplicationBroker[THREADS];

    try
    {
      /*
       * Start the producer threads.
       */
      for (int i = 0; i< THREADS; i++)
      {
        short serverId = (short) (10+i);
        ChangeNumberGenerator gen =
          new ChangeNumberGenerator(serverId , (long) 0);
        broker[i] =
          openReplicationSession( DN.decode(TEST_ROOT_DN_STRING), serverId,
            100, replicationServerPort, 1000, 1000, 0, true);

        assertTrue(broker[i].isConnected());

        producer[i] = new BrokerWriter(broker[i], gen, TOTAL_MSG/THREADS);
        reader[i] = new BrokerReader(broker[i]);
      }

      for (int i =0; i< THREADS; i++)
      {
        producer[i].start();
      }

      for (int i =0; i< THREADS; i++)
      {
        reader[i].start();
      }
      debugInfo("Ending multipleWriterMultipleReader");
    }
    finally
    {
      for (int i = 0; i< THREADS; i++)
      {
        if (producer[i] != null)
          producer[i].join();
      }
      for (int i = 0; i< THREADS; i++)
      {
        if (reader[i] != null)
          reader[i].join();
      }
      for (int i = 0; i< THREADS; i++)
      {
        if (broker[i] != null)
          broker[i].stop();
      }
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
  private void changelogChaining() throws Exception
  {
    debugInfo("Starting changelogChaining");
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
        changelogIds[i] = i + 80;
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
          new ReplServerFakeConfiguration(changelogPorts[i], "replicationServerTestChangelogChainingDb"+i, 0,
                                         changelogIds[i], 0, 100, servers);
        changelogs[i] = new ReplicationServer(conf);
      }

      ReplicationBroker broker1 = null;

      try
      {
        // For itest=0, create and connect client1 to changelog1
        //              and client2 to changelog2
        // For itest=1, only create and connect client1 to changelog1
        //              client2 will be created later
        broker1 = openReplicationSession(DN.decode(TEST_ROOT_DN_STRING),
             brokerIds[0], 100, changelogPorts[0], 1000, !emptyOldChanges);
        
        assertTrue(broker1.isConnected());

        if (itest == 0)
        {
          broker2 = openReplicationSession(DN.decode(TEST_ROOT_DN_STRING),
             brokerIds[1], 100, changelogPorts[0], 1000, !emptyOldChanges);
          assertTrue(broker2.isConnected());
        }

        // - Test messages between clients by publishing now

        // - Delete
        long time = TimeThread.getTime();
        int ts = 1;
        ChangeNumber cn = new ChangeNumber(time, ts++, brokerIds[0]);

        DeleteMsg delMsg = new DeleteMsg("o=example" + itest + "," + TEST_ROOT_DN_STRING, cn, "uid");
        broker1.publish(delMsg);

        String user1entryUUID = "33333333-3333-3333-3333-333333333333";
        String baseUUID = "22222222-2222-2222-2222-222222222222";

        // - Add
        String lentry = new String("dn: o=example," + TEST_ROOT_DN_STRING + "\n"
            + "objectClass: top\n" + "objectClass: domain\n"
            + "entryUUID: 11111111-1111-1111-1111-111111111111\n");
        Entry entry = TestCaseUtils.entryFromLdifString(lentry);
        cn = new ChangeNumber(time, ts++, brokerIds[0]);
        AddMsg addMsg = new AddMsg(cn, "o=example," + TEST_ROOT_DN_STRING,
            user1entryUUID, baseUUID, entry.getObjectClassAttribute(), entry
            .getAttributes(), new ArrayList<Attribute>());
        broker1.publish(addMsg);

        // - Modify
        Attribute attr1 = Attributes.create("description", "new value");
        Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
        List<Modification> mods = new ArrayList<Modification>();
        mods.add(mod1);
        cn = new ChangeNumber(time, ts++, brokerIds[0]);
        ModifyMsg modMsg = new ModifyMsg(cn, DN
            .decode("o=example," + TEST_ROOT_DN_STRING), mods, "fakeuniqueid");
        broker1.publish(modMsg);

        // - ModifyDN
        cn = new ChangeNumber(time, ts++, brokerIds[0]);
        ModifyDNOperationBasis op = new ModifyDNOperationBasis(connection, 1, 1, null, DN
            .decode("o=example," + TEST_ROOT_DN_STRING), RDN.decode("o=example2"), true,
            null);
        op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(cn, "uniqueid",
        "newparentId"));
        LocalBackendModifyDNOperation localOp =
          new LocalBackendModifyDNOperation(op);
        ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
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
          broker2 = openReplicationSession(DN.decode(TEST_ROOT_DN_STRING),
              brokerIds[1], 100, changelogPorts[1], 2000, !emptyOldChanges);
          assertTrue(broker2.isConnected());
        }

        // - Check msg receives by broker, through changeLog2
        while (ts > 1)
        {
          ReplicationMsg msg2;
          try
          {
            msg2 = broker2.receive();
            if (msg2 == null)
              break;
            broker2.updateWindowAfterReplay();
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
          else if (msg2 instanceof TopologyMsg)
          {
            // Nothing to test here.
          }
          else
          {
            fail("ReplicationServer transmission failed: no expected message" +
              " class: " + msg2);
            break;
          }
        }
        // Check that everything expected has been received
        assertTrue(ts == 1, "Broker2 did not receive the complete set of"
            + " expected messages: #msg received " + ts);
        debugInfo("Ending changelogChaining");
      }
      finally
      {
        if (changelogs[0] != null)
          changelogs[0].remove();
        if (changelogs[1] != null)
          changelogs[1].remove();
        if (broker1 != null)
          broker1.stop();
        if (broker2 != null)
          broker2.stop();
      }
    }
  }

  /**
   * Test that the Replication sends back correctly WindowsUpdate
   * when we send a WindowProbeMsg.
   */
  private void windowProbeTest() throws Exception
  {
    debugInfo("Starting windowProbeTest");
    final int WINDOW = 10;
    /*
     * Open a session to the replication server.
     *
     * Some other tests may have been running before and therefore
     * may have pushed some changes to the Replication Server
     * When a new session is opened, the Replication Server is therefore
     * going to send all thoses old changes to this Replication Server.
     * To avoid this, this test open a first session, save the
     * ServerState from the ReplicationServer, close the session
     * and re-open a new connection providing the ServerState it just
     * received from the first session.
     * This should guarantee that old changes are not perturbing this test.
     */

    // open the first session to the replication server
    String serverURL = "localhost:" + replicationServerPort;
    InetSocketAddress ServerAddr = new InetSocketAddress(
        InetAddress.getByName("localhost"), replicationServerPort);
    Socket socket = new Socket();
    socket.setReceiveBufferSize(1000000);
    socket.setTcpNoDelay(true);
    socket.connect(ServerAddr, 500);
    ReplSessionSecurity replSessionSecurity = getReplSessionSecurity();
    ProtocolSession session =
         replSessionSecurity.createClientSession(serverURL, socket,
         ReplSessionSecurity.HANDSHAKE_TIMEOUT);

    boolean sslEncryption =
         DirectoryConfig.getCryptoManager().isSslEncryption();

    try
    {
      // send a ServerStartMsg with an empty ServerState.
      ServerStartMsg msg =
        new ServerStartMsg((short) 1723, DN.decode(TEST_ROOT_DN_STRING),
            0, 0, 0, 0, WINDOW, (long) 5000, new ServerState(),
            ProtocolVersion.getCurrentVersion(), 0, sslEncryption, (byte)-1);
      session.publish(msg);

      // Read the Replication Server state from the ReplServerStartMsg that
      // comes back.
      ReplServerStartMsg replStartMsg =
        (ReplServerStartMsg) session.receive();
      int serverwindow = replStartMsg.getWindowSize();
      ServerState replServerState = replStartMsg.getServerState();

      if (!sslEncryption)
      {
        session.stopEncryption();
      }

      // Send StartSessionMsg
      StartSessionMsg startSessionMsg =
        new StartSessionMsg(ServerStatus.NORMAL_STATUS,
        new ArrayList<String>());
      session.publish(startSessionMsg);

      // Read the TopologyMsg that should come back.
      ReplicationMsg repMsg = session.receive();
      assertTrue(repMsg instanceof TopologyMsg);

      // close the session
      session.close();

      // Sleep a while so the following connection is not perturbed by some
      // topo messages signalling first connection has been dropped: let
      // disocnnection fully happen before, connecting a second session
      Thread.sleep(2000);

      // open a new session to the replication Server
      socket = new Socket();
      socket.setReceiveBufferSize(1000000);
      socket.setTcpNoDelay(true);
      socket.connect(ServerAddr, 500);
      session = replSessionSecurity.createClientSession(serverURL, socket, 4000);

      // send a ServerStartMsg containing the ServerState that was just
      // received.
      DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
      msg = new ServerStartMsg(
          (short) 1724, baseDn,
          0, 0, 0, 0, WINDOW, (long) 5000, replServerState,
          ProtocolVersion.getCurrentVersion(),
          ReplicationTestCase.getGenerationId(baseDn),
          sslEncryption, (byte)10);
      session.publish(msg);

      // Read the ReplServerStartMsg that should come back.
      repMsg = session.receive();
      assertTrue(repMsg instanceof ReplServerStartMsg);

      if (!sslEncryption)
      {
        session.stopEncryption();
      }

      // Send StartSessionMsg
      startSessionMsg = new StartSessionMsg(ServerStatus.NORMAL_STATUS,
        new ArrayList<String>());
      session.publish(startSessionMsg);

      // Read the TopologyMsg that should come back.
      repMsg = session.receive();
      assertTrue(repMsg instanceof TopologyMsg);

      // Now comes the real test : check that the Replication Server
      // answers correctly to a WindowProbeMsg Message.
      session.publish(new WindowProbeMsg());

      WindowMsg windowMsg = (WindowMsg) session.receive();
      assertEquals(serverwindow, windowMsg.getNumAck());

      // check that this did not change the window by sending a probe again.
      session.publish(new WindowProbeMsg());

      windowMsg = (WindowMsg) session.receive();
      assertEquals(serverwindow, windowMsg.getNumAck());
      debugInfo("Ending windowProbeTest");
    }
    finally
    {
      session.close();
    }
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
 
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    shutdown();

    paranoiaCheck();
  }

  /**
   * After the tests stop the replicationServer.
   */
  protected void shutdown() throws Exception
  {
    TestCaseUtils.dsconfig(
        "delete-replication-server",
        "--provider-name", "Multimaster Synchronization");
    replicationServer = null;
  }

  /**
   * This class allows to create reader thread.
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
          ReplicationMsg msg = broker.receive();
          broker.updateWindowAfterReplay();
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
          new DeleteMsg("o=example," + TEST_ROOT_DN_STRING, gen.newChangeNumber(),
              "uid");
        broker.publish(msg);
      }
    }
  }


  /*
   * Test backup and restore of the Replication server backend
   */
   private void backupRestore() throws Exception
   {
     debugInfo("Starting backupRestore");

     Entry backupTask = createBackupTask();
     Entry restoreTask = createRestoreTask();

     addTask(backupTask, ResultCode.SUCCESS, null);
     waitTaskState(backupTask, TaskState.COMPLETED_SUCCESSFULLY, null);

     addTask(restoreTask, ResultCode.SUCCESS, null);
     waitTaskState(restoreTask, TaskState.COMPLETED_SUCCESSFULLY, null);

     debugInfo("Ending backupRestore");
   }

   /*
    * Test export of the Replication server backend
    * - Creates 2 brokers connecting to the replication for 2 differents baseDN
    * - Make these brokers publish changes to the replication server
    * - Launch a full export
    * - Launch a partial export on one of the 2 domains
    */
    private void exportBackend() throws Exception
    {
      debugInfo("Starting exportBackend");

      ReplicationBroker server1 = null;
      ReplicationBroker server2 = null;

      try
      {
        server1 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 1, 100,
          replicationServerPort,
          1000, true);
        server2 = openReplicationSession(
          DN.decode("dc=domain2,dc=com"), (short) 2, 100,
          replicationServerPort,
          1000, true);

        assertTrue(server1.isConnected());
        assertTrue(server2.isConnected());

        debugInfo("Publish changes");
        List<UpdateMsg> msgs = createChanges(TEST_ROOT_DN_STRING, (short) 1);
        for (UpdateMsg msg : msgs)
        {
          server1.publish(msg);
        }
        List<UpdateMsg> msgs2 = createChanges("dc=domain2,dc=com", (short) 2);
        for (UpdateMsg msg : msgs2)
        {
          server2.publish(msg);
        }

        debugInfo("Export all");
        Entry exportTask = createExportAllTask();
        addTask(exportTask, ResultCode.SUCCESS, null);
        waitTaskState(exportTask, TaskState.COMPLETED_SUCCESSFULLY, null);

        debugInfo("Export domain");
        exportTask = createExportDomainTask("dc=domain2,dc=com");
        addTask(exportTask, ResultCode.SUCCESS, null);
        waitTaskState(exportTask, TaskState.COMPLETED_SUCCESSFULLY, null);
      } finally {
      if (server1 != null)
        server1.stop();
      if (server2 != null)
        server2.stop();
      }

      debugInfo("Ending export");
    }

   private Entry createBackupTask()
   throws Exception
   {
     return TestCaseUtils.makeEntry(
     "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
     "objectclass: top",
     "objectclass: ds-task",
     "objectclass: ds-task-backup",
     "ds-task-class-name: org.opends.server.tasks.BackupTask",
     "ds-backup-directory-path: bak" + File.separator +
                        "replicationChanges",
     "ds-task-backup-backend-id: replicationChanges");

   }

   private Entry createRestoreTask()
   throws Exception
   {
     return TestCaseUtils.makeEntry(
     "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
     "objectclass: top",
     "objectclass: ds-task",
     "objectclass: ds-task-restore",
     "ds-task-class-name: org.opends.server.tasks.RestoreTask",
     "ds-backup-directory-path: bak" + File.separator +
                        "replicationChanges");
   }

   private Entry createExportAllTask()
   throws Exception
   {
     String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
     String path = buildRoot + File.separator + "build" +
                   File.separator + "unit-tests" + File.separator +
                   "package-instance"+ File.separator + "exportLDIF.ldif";
     return TestCaseUtils.makeEntry(
     "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
     "objectclass: top",
     "objectclass: ds-task",
     "objectclass: ds-task-export",
     "ds-task-class-name: org.opends.server.tasks.ExportTask",
     "ds-task-export-ldif-file: " + path,
     "ds-task-export-backend-id: replicationChanges",
     "ds-task-export-include-branch: dc=replicationChanges");
   }

   private Entry createExportDomainTask(String suffix)
   throws Exception
   {
     String root = suffix.substring(suffix.indexOf('=')+1, suffix.indexOf(','));
     String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
     String path = buildRoot + File.separator + "build" +
                   File.separator + "unit-tests" + File.separator +
                   "package-instance"+ File.separator + "exportLDIF" + root +".ldif";
     return TestCaseUtils.makeEntry(
     "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
     "objectclass: top",
     "objectclass: ds-task",
     "objectclass: ds-task-export",
     "ds-task-class-name: org.opends.server.tasks.ExportTask",
     "ds-task-export-ldif-file: " + path,
     "ds-task-export-backend-id: replicationChanges",
     "ds-task-export-include-branch: "+suffix+",dc=replicationChanges");
   }

   private List<UpdateMsg> createChanges(String suffix, short serverId)
   {
     List<UpdateMsg> l = new ArrayList<UpdateMsg>();
     long time = TimeThread.getTime();
     int ts = 1;
     ChangeNumber cn;

     try
     {
       String user1entryUUID = "33333333-3333-3333-3333-333333333333";
       String baseUUID       = "22222222-2222-2222-2222-222222222222";

       // - Add
       String lentry = new String("dn: "+suffix+"\n"
           + "objectClass: top\n"
           + "objectClass: domain\n"
           + "entryUUID: 11111111-1111-1111-1111-111111111111\n");
       Entry entry = TestCaseUtils.entryFromLdifString(lentry);
       cn = new ChangeNumber(time, ts++, serverId);
       AddMsg addMsg = new AddMsg(cn, "o=example,"+suffix,
           user1entryUUID, baseUUID, entry.getObjectClassAttribute(), entry
           .getAttributes(), new ArrayList<Attribute>());
       l.add(addMsg);

       // - Add
       String luentry = new String(
             "dn: cn=Fiona Jensen,ou=People,"+suffix+"\n"
           + "objectClass: top\n"
           + "objectclass: person\n"
           + "objectclass: organizationalPerson\n"
           + "objectclass: inetOrgPerson\n"
           + "cn: Fiona Jensen\n"
           + "sn: Jensen\n"
           + "givenName: fjensen\n"
           + "telephonenumber: +1 408 555 1212\n"
           + "entryUUID: " + user1entryUUID +"\n"
           + "userpassword: fjen$$en"+"\n");
       Entry uentry = TestCaseUtils.entryFromLdifString(luentry);
       cn = new ChangeNumber(time, ts++, serverId);
       AddMsg addMsg2 = new AddMsg(
           cn,
           "uid=new person,ou=People,"+suffix,
           user1entryUUID,
           baseUUID,
           uentry.getObjectClassAttribute(),
           uentry.getAttributes(),
           new ArrayList<Attribute>());
       l.add(addMsg2);

       // - Modify
       Attribute attr1 = Attributes.create("description", "new value");
       Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
       Attribute attr2 = Attributes.create("modifiersName", "cn=Directory Manager,cn=Root DNs,cn=config");
       Modification mod2 = new Modification(ModificationType.REPLACE, attr2);
       Attribute attr3 = Attributes.create("modifyTimestamp", "20070917172420Z");
       Modification mod3 = new Modification(ModificationType.REPLACE, attr3);
       List<Modification> mods = new ArrayList<Modification>();

       mods.add(mod1);
       mods.add(mod2);
       mods.add(mod3);

       cn = new ChangeNumber(time, ts++, serverId);
       DN dn = DN.decode("o=example,"+suffix);
       ModifyMsg modMsg = new ModifyMsg(cn, dn,
           mods, "fakeuniqueid");
       l.add(modMsg);

       // Modify DN
       cn = new ChangeNumber(time, ts++, serverId);
       ModifyDNMsg  modDnMsg = new ModifyDNMsg(
           "uid=new person,ou=People,"+suffix, cn,
           user1entryUUID, baseUUID, false,
           "uid=wrong, ou=people,"+suffix,
       "uid=newrdn");
       l.add(modDnMsg);

       // Del
       cn = new ChangeNumber(time, ts++, serverId);
       DeleteMsg delMsg = new DeleteMsg("o=example,"+suffix, cn, "uid");
       l.add(delMsg);
     }
     catch(Exception e) {};
     return l;
   }


   /**
    * Testing searches on the backend of the replication server.
    * @throws Exception
    */
   // TODO: this test disabled as testReplicationBackendACIs() is failing
   // : anonymous search returns entries from replication backend whereas it
   // should not. Probably a previous test in the nightlytests suite is
   // removing/modifying some ACIs...When problem foound, we have to re-enable
   // this test.
   @Test(enabled=false)
   public void searchBackend() throws Exception
   {
     debugInfo("Starting searchBackend");

     ReplicationBroker server1 = null;
       try
       {

       // General search
       InternalSearchOperation op2 = connection.processSearch(
           new ASN1OctetString("cn=monitor"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(objectclass=*)"));
       assertEquals(op2.getResultCode(), ResultCode.SUCCESS,
           op2.getErrorMessage().toString());

       replicationServer.clearDb();

       LDIFWriter ldifWriter = null;
       ByteArrayOutputStream stream = new ByteArrayOutputStream();
       LDIFExportConfig exportConfig = new LDIFExportConfig(stream);
       try
       {
         ldifWriter = new LDIFWriter(exportConfig);
       }
       catch (Exception e){}

       debugInfo("Create broker");

       server1 = openReplicationSession(
         DN.decode(TEST_ROOT_DN_STRING), (short) 1, 100, replicationServerPort,
         1000, true);

       assertTrue(server1.isConnected());

       debugInfo("Publish changes");
       List<UpdateMsg> msgs = createChanges(TEST_ROOT_DN_STRING, (short)1);
       for(UpdateMsg msg : msgs )
       {
         server1.publish(msg);
       }
       Thread.sleep(500);

       // Sets manually the association backend-replication server since
       // no config object exist for our replication server.
       ReplicationBackend b =
         (ReplicationBackend)DirectoryServer.getBackend("replicationChanges");
       b.setServer(replicationServer);
       assertEquals(b.getEntryCount(), msgs.size());
       assertTrue(b.entryExists(DN.decode("dc=replicationChanges")));
       SearchFilter filter=SearchFilter.createFilterFromString("(objectclass=*)");
       assertTrue(b.isIndexed(filter));
       InternalClientConnection conn =
       InternalClientConnection.getRootConnection();
       LinkedList<Control> requestControls = new LinkedList<Control>();
       requestControls.add(new Control(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE,
                                      false));
       DN baseDN=DN.decode("dc=replicationChanges");
       //Test the group membership control causes search to be skipped.
       InternalSearchOperation internalSearch =
               new InternalSearchOperation(
                   conn, InternalClientConnection.nextOperationID(),
                   InternalClientConnection.nextMessageID(), requestControls,
                   baseDN,
                   SearchScope.WHOLE_SUBTREE,
                   DereferencePolicy.NEVER_DEREF_ALIASES,
                   0, 0, false, filter, null, null);
       internalSearch.run();
       assertTrue(internalSearch.getResultCode() == ResultCode.SUCCESS);
       assertTrue(internalSearch.getSearchEntries().isEmpty());

       // General search
       InternalSearchOperation op = connection.processSearch(
           new ASN1OctetString("dc=oops"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(changetype=*)"));
       assertEquals(op.getResultCode(), ResultCode.NO_SUCH_OBJECT);

       testReplicationBackendACIs();

       // General search
       op = connection.processSearch(
           new ASN1OctetString("dc=replicationChanges"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(changetype=*)"));

       debugInfo("Search result");
       LinkedList<SearchResultEntry> entries = op.getSearchEntries();
       if (entries != null)
       {
         for (SearchResultEntry entry : entries)
         {
           debugInfo(entry.toLDIFString());
           ldifWriter.writeEntry(entry);
         }
       }
       debugInfo("\n" + stream.toString());

       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertEquals(op.getSearchEntries().size(), 5);

       debugInfo("Query / filter based on changetype");
       op = connection.processSearch(
           new ASN1OctetString("dc=replicationChanges"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(changetype=add)"));
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertTrue(op.getSearchEntries().size() == 2);

       op = connection.processSearch(
           new ASN1OctetString("dc=replicationChanges"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(changetype=modify)"));
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertTrue(op.getSearchEntries().size() == 1);

       op = connection.processSearch(
           new ASN1OctetString("dc=replicationChanges"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(changetype=moddn)"));
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertTrue(op.getSearchEntries().size() == 1);

       op = connection.processSearch(
           new ASN1OctetString("dc=replicationChanges"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(changetype=delete)"));
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertTrue(op.getSearchEntries().size() == 1);

       debugInfo("Query / filter based on objectclass");
       op = connection.processSearch(
           new ASN1OctetString("dc=replicationChanges"),
           SearchScope.WHOLE_SUBTREE,
           LDAPFilter.decode("(objectclass=person)"));
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertEquals(op.getSearchEntries().size(), 1);

       /*
        * It would be nice to be have the abilities to search for 
        * entries in the replication backend using the DN on which the 
        * operation was done as the search criteria.
        * This is not possible yet, this part of the test is therefore 
        * disabled.
        *
        * debugInfo("Query / searchBase");
        * op = connection.processSearch(
        *    new ASN1OctetString("uid=new person,ou=People,dc=example,dc=com,dc=replicationChanges"),
        *    SearchScope.WHOLE_SUBTREE,
        *    LDAPFilter.decode("(changetype=*)"));
        * assertEquals(op.getResultCode(), ResultCode.SUCCESS);
        * assertEquals(op.getSearchEntries().size(), 2);
        */

       debugInfo("Query / 1 attrib");

       LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
       attrs.add("newrdn");
       SearchFilter ALLMATCH;
       ALLMATCH = SearchFilter.createFilterFromString("(changetype=moddn)");
       op =
         connection.processSearch(DN.decode("dc=replicationChanges"),
             SearchScope.WHOLE_SUBTREE,
             DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, ALLMATCH,
             attrs);
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertEquals(op.getSearchEntries().size(), 1);
       entries = op.getSearchEntries();
       if (entries != null)
       {
         for (SearchResultEntry entry : entries)
         {
           debugInfo(entry.toLDIFString());
           ldifWriter.writeEntry(entry);
         }
       }

       debugInfo("Query / All attribs");
       LinkedHashSet<String> attrs2 = new LinkedHashSet<String>(1);
       attrs.add("*");
       ALLMATCH = SearchFilter.createFilterFromString("(changetype=*)");
       op =
         connection.processSearch(DN.decode("dc=replicationChanges"),
             SearchScope.WHOLE_SUBTREE,
             DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, ALLMATCH,
             attrs2);
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
       assertEquals(op.getSearchEntries().size(), 5);
       
       debugInfo("Successfully ending searchBackend");

     } finally {
         if (server1 != null)
           server1.stop();
     }
   }

   private static final ByteArrayOutputStream oStream =
     new ByteArrayOutputStream();
   private static final ByteArrayOutputStream eStream =
     new ByteArrayOutputStream();

   private void testReplicationBackendACIs()
   {
     // test search as anonymous
     String[] args =
     {
       "-h", "127.0.0.1",
       "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
       "-b", "dc=replicationChanges",
       "-s", "sub",
      "--noPropertiesFile",
       "(objectClass=*)"
     };

     oStream.reset();
     eStream.reset();
     int retVal =
       LDAPSearch.mainSearch(args, false, oStream, eStream);
     String entries = oStream.toString();

     debugInfo("Entries:" + entries);
     assertEquals(0, retVal,  "Returned error: " + eStream);
     assertEquals(entries, "",  "Returned entries: " + entries);

     // test search as directory manager returns content
     String[] args3 =
     {
       "-h", "127.0.0.1",
       "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
       "-D", "cn=Directory Manager",
       "-w", "password",
       "-b", "dc=replicationChanges",
       "-s", "sub",
       "--noPropertiesFile",
       "(objectClass=*)"
     };

     oStream.reset();
     eStream.reset();
     retVal =
       LDAPSearch.mainSearch(args3, false, oStream, eStream);
     entries = oStream.toString();

     debugInfo("Entries:" + entries);
     assertEquals(0, retVal,  "Returned error: " + eStream);
     assertTrue(!entries.equalsIgnoreCase(""), "Returned entries: " + entries);

     // test write fails : unwilling to perform
     try
     {
       String ldif = new String(
           "dn: dc=foo, dc=replicationchanges\n"
           + "objectclass: top\n"
           + "objectClass: domain\n"
           + "dc:foo\n");
       String path = TestCaseUtils.createTempFile(ldif);
       String[] args4 =
       {
           "-h", "127.0.0.1",
           "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
           "-D", "cn=Directory Manager",
           "-w", "password",
           "--noPropertiesFile",
           "-a",
           "-f", path
       };

       retVal =
         LDAPModify.mainModify(args4, false, oStream, eStream);
       assertEquals(retVal, 53, "Returned error: " + eStream);
     } catch(Exception e) {}
   }

   /**
    * Replication Server configuration test of the replication Server code with
    * 2 replication servers involved
    *
    * Test 1
    * - Create replication server 1
    * - Create replication server 2
    * - Connect replication server 1 to replication server 2
    * - Create and connect client 1 to replication server 1
    * - Create and connect client 2 to replication server 2
    * - Make client1 publish changes
    * - Check that client 2 receives the changes published by client 1
    * Then
    * - Change the config of replication server 1 to no more be connected
    * to server 2
    * - Make client 1 publish a change
    * - Check that client 2 does not receive the change
    */
   private void replicationServerConnected() throws Exception
   {
       debugInfo("Starting replicationServerConnected");
       ReplicationBroker broker1 = null;
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
         changelogIds[i] = i + 90;
         brokerIds[i] = (short) (100+i);
         socket.close();
       }

       for (int i = 0; i <= 1; i++)
       {
         changelogs[i] = null;
         // create the 2 replicationServer
         // and connect the first one to the other one
         SortedSet<String> servers = new TreeSet<String>();

         // Connect only replicationServer[0] to ReplicationServer[1]
         // and not the other way
         if (i==0)
           servers.add("localhost:" + changelogPorts[1]);
         ReplServerFakeConfiguration conf =
           new ReplServerFakeConfiguration(changelogPorts[i], "replicationServerTestReplicationServerConnectedDb"+i, 0,
                                          changelogIds[i], 0, 100, servers);
         changelogs[i] = new ReplicationServer(conf);
       }

       try
       {
         // Create and connect client1 to changelog1
         // and client2 to changelog2
         broker1 = openReplicationSession(DN.decode(TEST_ROOT_DN_STRING),
              brokerIds[0], 100, changelogPorts[0], 1000, emptyOldChanges);

         broker2 = openReplicationSession(DN.decode(TEST_ROOT_DN_STRING),
              brokerIds[1], 100, changelogPorts[1], 1000, emptyOldChanges);
         
         assertTrue(broker1.isConnected());
         assertTrue(broker2.isConnected());

         // - Test messages between clients by publishing now
         long time = TimeThread.getTime();
         int ts = 1;
         ChangeNumber cn;
         String user1entryUUID = "33333333-3333-3333-3333-333333333333";
         String baseUUID  = "22222222-2222-2222-2222-222222222222";

         // - Add
         String lentry = new String("dn: o=example," + TEST_ROOT_DN_STRING + "\n"
             + "objectClass: top\n" + "objectClass: domain\n"
             + "entryUUID: "+ user1entryUUID +"\n");
         Entry entry = TestCaseUtils.entryFromLdifString(lentry);
         cn = new ChangeNumber(time, ts++, brokerIds[0]);
         AddMsg addMsg = new AddMsg(cn, "o=example," + TEST_ROOT_DN_STRING,
             user1entryUUID, baseUUID, entry.getObjectClassAttribute(), entry
             .getAttributes(), new ArrayList<Attribute>());
         broker1.publish(addMsg);

         // - Modify
         Attribute attr1 = Attributes.create("description", "new value");
         Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
         List<Modification> mods = new ArrayList<Modification>();
         mods.add(mod1);
         cn = new ChangeNumber(time, ts++, brokerIds[0]);
         ModifyMsg modMsg = new ModifyMsg(cn, DN
             .decode("o=example," + TEST_ROOT_DN_STRING), mods, "fakeuniqueid");
         broker1.publish(modMsg);

         // - Check msg received by broker, through changeLog2

         while (ts > 1)
         {
           ReplicationMsg msg2;
           try
           {
             msg2 = broker2.receive();
             if (msg2 == null)
               break;
             broker2.updateWindowAfterReplay();
           }
           catch (Exception e)
           {
             fail("Broker receive failed: " + e.getMessage() + "#Msg: " + ts);
             break;
           }

           if (msg2 instanceof AddMsg)
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
           else
           {
             fail("ReplicationServer transmission failed: no expected message" +
               " class: " + msg2);
             break;
           }
         }
         // Check that everything expected has been received
         assertTrue(ts == 1, "Broker2 did not receive the complete set of"
             + " expected messages: #msg received " + ts);

         // Then change the config to remove replicationServer[1] from
         // the configuration of replicationServer[0]

         SortedSet<String> servers = new TreeSet<String>();
         // Configure replicationServer[0] to be disconnected from ReplicationServer[1]
         ReplServerFakeConfiguration conf =
           new ReplServerFakeConfiguration(changelogPorts[0], "changelogDb0", 0,
                                          changelogIds[0], 0, 100, servers);
         changelogs[0].applyConfigurationChange(conf) ;
         // Sleep a while to be sure disconnection occurs
         sleep(1000);

         // We expect the receive to end because of a timeout : the link between RS1 & RS2
         // should be distroyed by the new configuration

         // Send 1 update and check that RS[1] does not receive the message after the timeout
         try
         {
           // - Del
           cn = new ChangeNumber(time, ts++, brokerIds[0]);
           DeleteMsg delMsg = new DeleteMsg("o=example," + TEST_ROOT_DN_STRING, cn, user1entryUUID);
           broker1.publish(delMsg);
           // Should receive some TopologyMsg messages for disconnection
           // between the 2 RSs
           ReplicationMsg msg = null;
           while (true)
           {
             msg = broker2.receive();
             if (msg instanceof TopologyMsg)
             {
               debugInfo("Broker 2 received: " + msg);
             } else
             {
               fail("Broker: receive successed when it should fail. " +
                 "This broker was disconnected by configuration." +
                 " Received: " + msg);
             }
           }
         }
         catch (SocketTimeoutException soExc)
         {
         // the receive fail as expected
         debugInfo("Ending replicationServerConnected");
         return;
         }
       }
       finally
       {
         if (changelogs[0] != null)
           changelogs[0].remove();
         if (changelogs[1] != null)
           changelogs[1].remove();
         if (broker1 != null)
           broker1.stop();
         if (broker2 != null)
           broker2.stop();
       }
     }

  private void sleep(long time)
  {
    try
    {
      Thread.sleep(time);
    } catch (InterruptedException ex)
    {
      fail("Error sleeping " + ex.getMessage());
    }
  }
}
