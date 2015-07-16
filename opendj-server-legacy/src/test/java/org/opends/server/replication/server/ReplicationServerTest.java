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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.DummyReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.plugin.ReplicationServerListener;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Tests for the replicationServer code.
 */
@SuppressWarnings("javadoc")
public class ReplicationServerTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private DN TEST_ROOT_DN;
  private DN EXAMPLE_DN;
  /** The replicationServer that will be used in this test. */
  private ReplicationServer replicationServer;

  /** The port of the replicationServer. */
  private int replicationServerPort;

  private CSN firstCSNServer1;
  private CSN secondCSNServer1;
  private CSN firstCSNServer2;
  private CSN secondCSNServer2;

  private CSN unknownCSNServer1;

  /**
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
    TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
    EXAMPLE_DN = DN.valueOf("ou=example," + TEST_ROOT_DN_STRING);

    // This test suite depends on having the schema available.
    configure();
  }

  /**
   * Start the server and configure a replicationServer.
   */
  private void configure() throws Exception
  {
    replicationServerPort = TestCaseUtils.findFreePort();

    TestCaseUtils.dsconfig(
        "create-replication-server",
        "--provider-name", "Multimaster Synchronization",
        "--set", "replication-db-directory:" + "replicationServerTestConfigureDb",
        "--set", "replication-port:" + replicationServerPort,
        "--set", "replication-db-implementation:" + replicationDbImplementation,
        "--set", "replication-server-id:71");

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
    //logger.error(LocalizableMessage.raw("** TEST ** " + s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST ** " + s);
    }
  }

  /**
   * The tests in this class only works in a specific order.
   * This method is used to make sure that this order is always respected.
   * (Using testng dependency does not work)
   */
  @Test(enabled = true)
  public void replicationServerTest() throws Exception
  {
    ReplicationBroker[] brokers1And2 = null;
    try {
      brokers1And2 = changelogBasic();
      newClientLateServer1();
      newClient();
      newClientWithFirstChanges();
      newClientWithChangefromServer1();
      newClientWithChangefromServer2();
      newClientWithUnknownChanges();
    }
    finally
    {
      stop(brokers1And2);
    }
    stopChangelog();
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
    ReplicationBroker[] brokers1And2 = null;
    try
    {
      brokers1And2 = changelogBasic();
      while (true)
      {
        newClient();
      }
    }
    finally
    {
      stop(brokers1And2);
    }
  }

  /** Create two brokers: open for each a sender session and a receiver session to the replicationServer. */
  private ReplicationBroker[] createReplicationBrokers1And2() throws Exception {
    return new ReplicationBroker[] {
       openReplicationSession(TEST_ROOT_DN, 1, 100, replicationServerPort, 1000),
       openReplicationSession(TEST_ROOT_DN, 2, 100, replicationServerPort, 1000)
    };
  }

  @Override
  protected ReplicationBroker openReplicationSession(final DN baseDN,
      int serverId, int windowSize, int port, int timeout) throws Exception
  {
    ReplicationBroker broker = super.openReplicationSession(baseDN, serverId, windowSize, port, timeout);
    assertTrue(broker.isConnected());
    return broker;
  }

  /**
   * Basic test of the replicationServer code : Connect 2 clients to the
   * replicationServer and exchange messages between the clients. Note : Other
   * tests in this file depends on this test and may need to change if this test
   * is modified.
   *
   * @return the two brokers created to simulate 2 DS sending and receiving
   *         messages. They are returned to allow other tests to close the
   *         brokers themselves.
   */
  private ReplicationBroker[] changelogBasic() throws Exception
  {
    clearChangelogDB(replicationServer);
    debugInfo("Starting changelogBasic");
    ReplicationBroker[] brokers1And2 = createReplicationBrokers1And2();
    ReplicationBroker server1 = brokers1And2[0];
    ReplicationBroker server2 = brokers1And2[1];

    /*
     * Create CSNs for the messages sent from server 1 with current time
     * sequence 1 and with current time + 2 sequence 2
     */
    long time = TimeThread.getTime();
    firstCSNServer1 = new CSN(time, 1, 1);
    secondCSNServer1 = new CSN(time + 2, 2, 1);

    /*
     * Create CSNs for the messages sent from server 2 with current time
     * sequence 1 and with current time + 3 sequence 2
     */
    firstCSNServer2 = new CSN(time + 1, 1, 2);
    secondCSNServer2 = new CSN(time + 3, 2, 2);

    /*
     * Create a CSN between firstCSNServer1 and secondCSNServer1 that will not
     * be used to create a change sent to the replicationServer but that will
     * be used in the Server State when opening a connection to the
     * ReplicationServer to make sure that the ReplicationServer is able to
     * accept such clients.
     */
    unknownCSNServer1 = new CSN(time + 1, 1, 1);

    sendAndReceiveDeleteMsg(server1, server2, EXAMPLE_DN, firstCSNServer1, "uid");
    sendAndReceiveDeleteMsg(server1, server2, TEST_ROOT_DN, secondCSNServer1, "uid");

    // Send and receive a Delete Msg from server 2 to server 1
    sendAndReceiveDeleteMsg(server2, server1, EXAMPLE_DN, firstCSNServer2, "other-uid");
    sendAndReceiveDeleteMsg(server2, server1, TEST_ROOT_DN, secondCSNServer2, "uid");

    debugInfo("Ending changelogBasic");
    return new ReplicationBroker[] { server1, server2 };
  }

  private void sendAndReceiveDeleteMsg(ReplicationBroker sender, ReplicationBroker receiver,
      DN dn, CSN csn, String entryUUID) throws Exception
  {
    DeleteMsg sentMsg = new DeleteMsg(dn, csn, entryUUID);
    sender.publish(sentMsg);
    ReplicationMsg receivedMsg = receiver.receive();
    receiver.updateWindowAfterReplay();
    assertDeleteMsgBodyEquals(sentMsg, receivedMsg);
  }

  private void assertDeleteMsgBodyEquals(DeleteMsg sentMsg, ReplicationMsg receivedMsg)
  {
    Assertions.assertThat(receivedMsg).isInstanceOf(DeleteMsg.class);
    assertEquals(receivedMsg.toString(), sentMsg.toString(),
        "ReplicationServer basic : incorrect message body received. CSN is same as \""
            + getCSNFieldName(((DeleteMsg) receivedMsg).getCSN()) + "\" field.");
  }

  private String getCSNFieldName(CSN csn)
  {
    if (csn == null) {
      return "";
    }
    if (csn.equals(firstCSNServer1))
    {
      return "firstCSNServer1";
    }
    else if (csn.equals(secondCSNServer1))
    {
      return "secondCSNServer1";
    }
    else if (csn.equals(firstCSNServer2))
    {
      return "firstCSNServer2";
    }
    else if (csn.equals(secondCSNServer2))
    {
      return "secondCSNServer2";
    }
    else if (csn.equals(unknownCSNServer1))
    {
      return "unknownCSNServer1";
    }
    return null;
  }

  private ServerState newServerState(CSN... csns)
  {
    ServerState state = new ServerState();
    for (CSN csn : csns)
    {
      state.update(csn);
    }
    return state;
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
      broker = openReplicationSession(TEST_ROOT_DN, 3, 100, replicationServerPort, 1000);

      ReplicationMsg receivedMsg = broker.receive();
      broker.updateWindowAfterReplay();
      assertDeleteMsgCSNEquals(receivedMsg, firstCSNServer1, "first");
      debugInfo("Ending newClient");
    }
    finally
    {
      stop(broker);
    }
  }

  /**
   * Test that a client that has already seen some changes now receive
   * the correct next change.
   */
  private void newClientWithChanges(ServerState state, CSN nextCSN) throws Exception
  {
    ReplicationBroker broker = null;

    // Connect to the replicationServer using the state created above.
    try {
      final long generationId = getGenerationId(TEST_ROOT_DN);
      broker = new ReplicationBroker(new DummyReplicationDomain(generationId),
          state, newFakeCfg(TEST_ROOT_DN, 3, replicationServerPort),
          getReplSessionSecurity());
      connect(broker, replicationServerPort, 5000);

      ReplicationMsg receivedMsg = broker.receive();
      broker.updateWindowAfterReplay();
      assertDeleteMsgCSNEquals(receivedMsg, nextCSN, "second");
    }
    finally
    {
      stop(broker);
    }
  }

  /**
   * Asserts that the CSN for the passed in message matches the supplied CSN.
   */
  private void assertDeleteMsgCSNEquals(ReplicationMsg msg, CSN nextCSN, String msgNumber)
  {
    Assertions.assertThat(msg).isInstanceOf(DeleteMsg.class);
    DeleteMsg del = (DeleteMsg) msg;
    assertEquals(del.getCSN(), nextCSN, "The " + msgNumber
        + " message received by a new client was the wrong one.");
  }

  /**
   * Test that a client that has already seen the first change now see the
   * second change.
   */
  private void newClientWithFirstChanges() throws Exception
  {
    debugInfo("Starting newClientWithFirstChanges");
    /*
     * Create a ServerState updated with the first changes from both servers
     * done in test changelogBasic.
     */
    ServerState state = newServerState(firstCSNServer1, firstCSNServer2);
    newClientWithChanges(state, secondCSNServer1);
    debugInfo("Ending newClientWithFirstChanges");
  }

  /**
   * Test with a client that has already seen a Change that the
   * ReplicationServer has not seen.
   */
  private void newClientWithUnknownChanges() throws Exception
  {
    debugInfo("Starting newClientWithUnknownChanges");
    ServerState state = newServerState(unknownCSNServer1, secondCSNServer2);
    newClientWithChanges(state, secondCSNServer1);
    debugInfo("Ending newClientWithUnknownChanges");
  }

  /**
   * Test that a client that has already seen the first change from server 1
   * now see the first change from server 2.
   */
  private void newClientWithChangefromServer1() throws Exception
  {
    debugInfo("Starting newClientWithChangefromServer1");
    ServerState state = newServerState(firstCSNServer1);
    newClientWithChanges(state, firstCSNServer2);
    debugInfo("Ending newClientWithChangefromServer1");
  }

  /**
   * Test that a client that has already seen the first chaneg from server 2
   * now see the first change from server 1.
   */
  private void newClientWithChangefromServer2() throws Exception
  {
    debugInfo("Starting newClientWithChangefromServer2");
    ServerState state = newServerState(firstCSNServer2);
    newClientWithChanges(state, firstCSNServer1);
    debugInfo("Ending newClientWithChangefromServer2");
  }

  /**
   * Test that a client that has not seen the second change from server 1
   * now receive it.
   */
  private void newClientLateServer1() throws Exception
  {
    debugInfo("Starting newClientLateServer1");
    ServerState state = newServerState(secondCSNServer2, firstCSNServer1);
    newClientWithChanges(state, secondCSNServer1);
    debugInfo("Ending newClientLateServer1");
  }

  /**
   * Test that newClient() and newClientWithFirstChange() still works
   * after stopping and restarting the replicationServer.
   */
  private void stopChangelog() throws Exception
  {
    ReplicationBroker[] brokers1And2 = null;
    try {
      debugInfo("Starting stopChangelog");
      shutdown();
      configure();
      brokers1And2 = createReplicationBrokers1And2();
      newClient();
      newClientWithFirstChanges();
      newClientWithChangefromServer1();
      newClientWithChangefromServer2();
      debugInfo("Ending stopChangelog");
    }
    finally {
      stop(brokers1And2);
    }
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
  @Test(enabled = true)
  public void oneWriterMultipleReader() throws Exception
  {
    debugInfo("Starting oneWriterMultipleReader");

    clearChangelogDB(replicationServer);
    TestCaseUtils.initializeTestBackend(true);

    ReplicationBroker server = null;
    BrokerReader reader = null;
    int TOTAL_MSG = 1000;     // number of messages to send during the test
    int CLIENT_THREADS = 2;   // number of threads that will try to read
                              // the messages
    CSNGenerator gen = new CSNGenerator(5 , 0);

    BrokerReader client[] = new BrokerReader[CLIENT_THREADS];
    ReplicationBroker clientBroker[] = new ReplicationBroker[CLIENT_THREADS];

    try
    {
      /*
       * Open a sender session
       */
      server = openReplicationSession(TEST_ROOT_DN, 5, 100, replicationServerPort, 100000);
      reader = new BrokerReader(server, TOTAL_MSG);

      /*
       * Start the client threads.
       */
      for (int i =0; i< CLIENT_THREADS; i++)
      {
        clientBroker[i] = openReplicationSession(TEST_ROOT_DN, 100+i, 100, replicationServerPort, 1000);
        client[i] = new BrokerReader(clientBroker[i], TOTAL_MSG);
      }

      for (BrokerReader c : client)
      {
        c.start();
      }
      reader.start();

      /*
       * Simple loop creating changes and sending them
       * to the replicationServer.
       */
      for (int i = 0; i< TOTAL_MSG; i++)
      {
        server.publish(new DeleteMsg(EXAMPLE_DN, gen.newCSN(), "uid"));
      }
      debugInfo("Ending oneWriterMultipleReader");
    }
    finally
    {
      if (reader != null)
      {
        reader.join(10000);
      }
      stop(server);
      join(client);
      stop(clientBroker);

      if (reader != null)
      {
        assertNull(reader.errDetails, reader.exc + " " + reader.errDetails);
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
  @Test(enabled = true, groups = "opendj-256")
  public void multipleWriterMultipleReader() throws Exception
  {
    debugInfo("Starting multipleWriterMultipleReader");
    final int TOTAL_MSG = 1000;   // number of messages to send during the test
    final int THREADS = 2;       // number of threads that will produce
                               // and read the messages.

    BrokerWriter producer[] = new BrokerWriter[THREADS];
    BrokerReader reader[] = new BrokerReader[THREADS];
    ReplicationBroker broker[] = new ReplicationBroker[THREADS];

    clearChangelogDB(replicationServer);
    TestCaseUtils.initializeTestBackend(true);

    try
    {
      /*
       * Start the producer threads.
       */
      for (int i = 0; i< THREADS; i++)
      {
        int serverId = 10 + i;
        CSNGenerator gen = new CSNGenerator(serverId , 0);
        broker[i] = openReplicationSession(TEST_ROOT_DN,
            serverId, 100, replicationServerPort, 3000);

        producer[i] = new BrokerWriter(broker[i], gen, TOTAL_MSG/THREADS);
        reader[i] = new BrokerReader(broker[i], (TOTAL_MSG/THREADS)*(THREADS-1));
      }

      for (BrokerWriter p : producer)
      {
        p.start();
      }

      for (BrokerReader r : reader)
      {
        r.start();
      }
      debugInfo("multipleWriterMultipleReader produces and readers started");
    }
    finally
    {
      debugInfo("multipleWriterMultipleReader wait producers end");
      join(producer);
      debugInfo("multipleWriterMultipleReader producers ended, now wait readers end");
      join(reader);
      debugInfo("multipleWriterMultipleReader reader's ended, now stop brokers");
      stop(broker);
      debugInfo("multipleWriterMultipleReader brokers stopped");

      for (BrokerReader r : reader)
      {
        if (r != null)
        {
          assertNull(r.errDetails, r.exc + " " + r.errDetails);
        }
      }
    }
    debugInfo("Ending multipleWriterMultipleReader");
  }

  private void join(Thread[] threads) throws InterruptedException
  {
    for (Thread t : threads)
    {
      if (t != null)
      {
        t.join(10000);
        // kill the thread in case it is not yet stopped.
        t.interrupt();
      }
    }
  }

  /**
   * <ol>
   * <li>Create replication server 1</li>
   * <li>Create replication server 2 connected with replication server 1</li>
   * <li>Create and connect client 1 to replication server 1</li>
   * <li>Create and connect client 2 to replication server 2</li>
   * <li>Make client1 publish changes</li>
   * <li>Check that client 2 receives the changes published by client 1</li>
   * </ol>.
   */
  @Test(enabled = true)
  public void changelogChaining0() throws Exception
  {
    final String tn = "changelogChaining0";
    debugInfo("Starting " + tn);
    clearChangelogDB(replicationServer);
    TestCaseUtils.initializeTestBackend(true);

    {
      ReplicationBroker broker2 = null;

      // - Create 2 connected replicationServer
      ReplicationServer[] changelogs = new ReplicationServer[2];
      int[] changelogPorts = TestCaseUtils.findFreePorts(2);
      int[] changelogIds = new int[] { 80, 81 };
      int[] brokerIds = new int[] { 100, 101 };

      for (int i = 0; i < 2; i++)
      {
        changelogs[i] = null;

        // create the 2 connected replicationServer
        SortedSet<String> servers = new TreeSet<>();
        servers.add(
          "localhost:" + ((i == 0) ? changelogPorts[1] : changelogPorts[0]));
        ReplServerFakeConfiguration conf =
          new ReplServerFakeConfiguration(changelogPorts[i], "replicationServerTestChangelogChainingDb"+i,
              replicationDbImplementation, 0, changelogIds[i], 0, 100, servers);
        changelogs[i] = new ReplicationServer(conf);
      }

      ReplicationBroker broker1 = null;

      try
      {
        // create and connect client1 to changelog1 and client2 to changelog2
        broker1 = openReplicationSession(TEST_ROOT_DN, brokerIds[0], 100, changelogPorts[0], 1000);
        broker2 = openReplicationSession(TEST_ROOT_DN, brokerIds[1], 100, changelogPorts[0], 1000);

        // - Test messages between clients by publishing now

        // - Delete
        CSNGenerator csnGen = new CSNGenerator(brokerIds[0], TimeThread.getTime());
        DN dn = DN.valueOf("o=example" + 0 + "," + TEST_ROOT_DN_STRING);
        DeleteMsg delMsg = new DeleteMsg(dn, csnGen.newCSN(), "uid");
        broker1.publish(delMsg);

        String user1entryUUID = "33333333-3333-3333-3333-333333333333";
        String baseUUID = "22222222-2222-2222-2222-222222222222";

        // - Add
        Entry entry = TestCaseUtils.entryFromLdifString(
        "dn: o=example," + TEST_ROOT_DN_STRING + "\n"
            + "objectClass: top\n" + "objectClass: domain\n"
            + "entryUUID: 11111111-1111-1111-1111-111111111111\n");
        AddMsg addMsg = new AddMsg(csnGen.newCSN(), EXAMPLE_DN,
            user1entryUUID, baseUUID, entry.getObjectClassAttribute(),
            entry.getAttributes(), new ArrayList<Attribute>());
        broker1.publish(addMsg);

        // - Modify
        Attribute attr1 = Attributes.create("description", "new value");
        List<Modification> mods =
            Arrays.asList(new Modification(ModificationType.REPLACE, attr1));
        ModifyMsg modMsg = new ModifyMsg(csnGen.newCSN(), EXAMPLE_DN, mods, "fakeuniqueid");
        broker1.publish(modMsg);

        // - ModifyDN
        ModifyDNOperationBasis op = new ModifyDNOperationBasis(connection, 1, 1, null,
            EXAMPLE_DN, RDN.decode("o=example2"), true, null);
        op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(csnGen.newCSN(), "uniqueid", "newparentId"));
        LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
        ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
        broker1.publish(modDNMsg);

        // - Check msg receives by broker, through changeLog2
        List<ReplicationMsg> msgs = receiveReplicationMsgs(broker2, 4);
        Assertions.assertThat(msgs).containsExactly(delMsg, addMsg, modMsg, modDNMsg);
        debugInfo("Ending " + tn);
      }
      finally
      {
        remove(changelogs);
        stop(broker1, broker2);
      }
    }
  }

  /**
   * <ol>
   * <li>Create replication server 1</li>
   * <li>Create and connect client1 to replication server 1</li>
   * <li>Make client1 publish changes</li>
   * <li>Create replication server 2 connected with replication server 1</li>
   * <li>Create and connect client 2 to replication server 2</li>
   * <li>Check that client 2 receives the changes published by client 1</li>
   * <ol>.
   */
  @Test(enabled = true)
  public void changelogChaining1() throws Exception
  {
    final String tn = "changelogChaining1";
    debugInfo("Starting " + tn);
    clearChangelogDB(replicationServer);
    TestCaseUtils.initializeTestBackend(true);

    {
      ReplicationBroker broker2 = null;

      // - Create 2 connected replicationServer
      ReplicationServer[] changelogs = new ReplicationServer[2];
      int[] changelogPorts = TestCaseUtils.findFreePorts(2);
      int[] changelogIds = new int[] { 80, 81 };
      int[] brokerIds = new int[] { 100, 101 };

      {
        // create the 1rst replicationServer, the second one will be created later
        SortedSet<String> servers = new TreeSet<>();
        servers.add("localhost:" + changelogPorts[1]);
        ReplServerFakeConfiguration conf =
          new ReplServerFakeConfiguration(changelogPorts[0], "replicationServerTestChangelogChainingDb"+0, replicationDbImplementation,
                                         0, changelogIds[0], 0, 100, servers);
        changelogs[0] = new ReplicationServer(conf);
      }

      ReplicationBroker broker1 = null;

      try
      {
        // only create and connect client1 to changelog1 client2 will be created later
        broker1 = openReplicationSession(TEST_ROOT_DN, brokerIds[0], 100, changelogPorts[0], 1000);

        // - Test messages between clients by publishing now

        // - Delete
        CSNGenerator csnGen = new CSNGenerator(brokerIds[0], TimeThread.getTime());

        DN dn = DN.valueOf("o=example" + 1 + "," + TEST_ROOT_DN_STRING);
        DeleteMsg delMsg = new DeleteMsg(dn, csnGen.newCSN(), "uid");
        broker1.publish(delMsg);

        String user1entryUUID = "33333333-3333-3333-3333-333333333333";
        String baseUUID = "22222222-2222-2222-2222-222222222222";

        // - Add
        String lentry = "dn: o=example," + TEST_ROOT_DN_STRING + "\n"
            + "objectClass: top\n" + "objectClass: domain\n"
            + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
        Entry entry = TestCaseUtils.entryFromLdifString(lentry);
        AddMsg addMsg = new AddMsg(csnGen.newCSN(), EXAMPLE_DN,
            user1entryUUID, baseUUID, entry.getObjectClassAttribute(),
            entry.getAttributes(), new ArrayList<Attribute>());
        broker1.publish(addMsg);

        // - Modify
        Attribute attr1 = Attributes.create("description", "new value");
        List<Modification> mods =
            Arrays.asList(new Modification(ModificationType.REPLACE, attr1));
        ModifyMsg modMsg = new ModifyMsg(csnGen.newCSN(), EXAMPLE_DN, mods, "fakeuniqueid");
        broker1.publish(modMsg);

        // - ModifyDN
        ModifyDNOperationBasis op = new ModifyDNOperationBasis(connection, 1, 1, null,
            EXAMPLE_DN, RDN.decode("o=example2"), true, null);
        op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(csnGen.newCSN(), "uniqueid", "newparentId"));
        LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
        ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
        broker1.publish(modDNMsg);

        SortedSet<String> servers = new TreeSet<>();
        servers.add("localhost:"+changelogPorts[0]);
        ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
            changelogPorts[1], null, replicationDbImplementation, 0, changelogIds[1], 0, 100, null);
        changelogs[1] = new ReplicationServer(conf);

        // Connect broker 2 to changelog2
        broker2 = openReplicationSession(TEST_ROOT_DN,
            brokerIds[1], 100, changelogPorts[1], 2000);

        // - Check msg receives by broker, through changeLog2
        List<ReplicationMsg> msgs = receiveReplicationMsgs(broker2, 4);
        Assertions.assertThat(msgs).containsExactly(delMsg, addMsg, modMsg, modDNMsg);
        debugInfo("Ending " + tn);
      }
      finally
      {
        remove(changelogs);
        stop(broker1, broker2);
      }
    }
  }

  private List<ReplicationMsg> receiveReplicationMsgs(ReplicationBroker broker2, int nbMessagesExpected)
  {
    List<ReplicationMsg> msgs = new ArrayList<>(nbMessagesExpected);
    for (int i = 0; i < nbMessagesExpected; i++)
    {
      try
      {
        ReplicationMsg msg = broker2.receive();
        if (msg == null)
        {
          break;
        }
        if (msg instanceof TopologyMsg)
         {
          continue; // ignore
        }
        msgs.add(msg);

        broker2.updateWindowAfterReplay();
      }
      catch (SocketTimeoutException e)
      {
        fail("Broker receive failed: " + e.getMessage() + "#Msg:" + i);
      }
    }
    return msgs;
  }

  /**
   * Test that the Replication sends back correctly WindowsUpdate
   * when we send a WindowProbeMsg.
   */
  @Test(enabled = true)
  public void windowProbeTest() throws Exception
  {
    debugInfo("Starting windowProbeTest");
    final int WINDOW = 10;

    clearChangelogDB(replicationServer);
    TestCaseUtils.initializeTestBackend(true);

    /*
     * Open a session to the replication server.
     *
     * Some other tests may have been running before and therefore
     * may have pushed some changes to the Replication Server
     * When a new session is opened, the Replication Server is therefore
     * going to send all these old changes to this Replication Server.
     * To avoid this, this test open a first session, save the
     * ServerState from the ReplicationServer, close the session
     * and re-open a new connection providing the ServerState it just
     * received from the first session.
     * This should guarantee that old changes are not perturbing this test.
     */

    // open the first session to the replication server
    InetSocketAddress serverAddr =
        new HostPort("localhost", replicationServerPort).toInetSocketAddress();
    Socket socket = new Socket();
    socket.setReceiveBufferSize(1000000);
    socket.setTcpNoDelay(true);
    int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
    socket.connect(serverAddr, timeoutMS);
    ReplSessionSecurity replSessionSecurity = getReplSessionSecurity();
    Session session = replSessionSecurity.createClientSession(socket, timeoutMS);

    boolean sslEncryption = DirectoryConfig.getCryptoManager().isSslEncryption();

    try
    {
      // send a ServerStartMsg with an empty ServerState.
      String url = socket.getLocalAddress().getCanonicalHostName() + ":"
          + socket.getLocalPort();
      ServerStartMsg msg = new ServerStartMsg(1723, url, TEST_ROOT_DN,
            WINDOW, 5000, new ServerState(), 0, sslEncryption, (byte)-1);
      session.publish(msg);

      // Read the Replication Server state from the ReplServerStartDSMsg that
      // comes back.
      ReplServerStartDSMsg replStartDSMsg = (ReplServerStartDSMsg) session.receive();
      int serverwindow = replStartDSMsg.getWindowSize();
      if (!sslEncryption)
      {
        session.stopEncryption();
      }

      // Send StartSessionMsg
      StartSessionMsg startSessionMsg = new StartSessionMsg(
          ServerStatus.NORMAL_STATUS, new ArrayList<String>(),
          false, AssuredMode.SAFE_DATA_MODE, (byte) 1);
      session.publish(startSessionMsg);

      // Read the TopologyMsg that should come back.
      ReplicationMsg repMsg = session.receive();
      Assertions.assertThat(repMsg).isInstanceOf(TopologyMsg.class);

      // Now comes the real test : check that the Replication Server
      // answers correctly to a WindowProbeMsg LocalizableMessage.
      session.publish(new WindowProbeMsg());

      WindowMsg windowMsg = waitForSpecificMsg(session, WindowMsg.class);
      assertEquals(serverwindow, windowMsg.getNumAck());

      // check that this did not change the window by sending a probe again.
      session.publish(new WindowProbeMsg());

      // We may receive some MonitoringMsg so use filter method
      windowMsg = waitForSpecificMsg(session, WindowMsg.class);
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
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    replicationServer.getChangelogDB().removeDB();
    shutdown();

    paranoiaCheck();
  }

  /**
   * After the tests stop the replicationServer.
   */
  private void shutdown() throws Exception
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
    private int numMsgRcv;
    private final int numMsgExpected;
    private Exception exc;
    private String errDetails;

    /**
     * Creates a new Stress Test Reader.
     */
    public BrokerReader(ReplicationBroker broker, int numMsgExpected)
    {
      this.broker = broker;
      this.numMsgExpected = numMsgExpected;
    }

    /** {@inheritDoc} */
    @Override
    public void run()
    {
      debugInfo("BrokerReader " + broker.getServerId() + " starts");

      // loop receiving messages until either we get a timeout
      // because there is nothing left or an error condition happens.
      try
      {
        while (true)
        {
          ReplicationMsg msg = broker.receive();
          if (msg instanceof UpdateMsg)
          {
            numMsgRcv++;
            broker.updateWindowAfterReplay();
          }
          // if ((msg == null) || (numMsgRcv >= numMsgExpected))
          // Terminating this thread when the nb of msg received is reached
          // may prevent to process a WindowMsg that would unblock the dual
          // writer thread.
          if (msg == null)
          {
            break;
          }
        }
      } catch (SocketTimeoutException e)
      {
        if (numMsgRcv != numMsgExpected)
        {
          this.exc = e;
          this.errDetails =
            "BrokerReader " + broker.getServerId()
            + " did not received the expected message number : act="
            + numMsgRcv + " exp=" + numMsgExpected;
        }
      } catch (Exception e)
      {
        this.exc = e;
        this.errDetails =
            "a BrokerReader received an Exception" + e.getMessage()
            + stackTraceToSingleLineString(e);
      }
    }
  }

  /**
   * This class allows to create writer thread that can
   * be used as producers for the ReplicationServer stress tests.
   */
  private class BrokerWriter extends Thread
  {
    private int count;
    private ReplicationBroker broker;
    private CSNGenerator gen;

    public BrokerWriter(ReplicationBroker broker, CSNGenerator gen, int count)
    {
      this.broker = broker;
      this.count = count;
      this.gen = gen;
    }

    /** {@inheritDoc} */
    @Override
    public void run()
    {
      debugInfo("writer " + broker.getServerId() + " starts to produce " + count);
      int ccount = count;
      /*
       * Simple loop creating changes and sending them
       * to the replicationServer.
       */
      while (count>0)
      {
        count--;

        DeleteMsg msg = new DeleteMsg(EXAMPLE_DN, gen.newCSN(), "uid");
        broker.publish(msg);

        if ((count % 10) == 0)
        {
          debugInfo("writer " + broker.getServerId() + "  to send="+count);
        }
      }
      debugInfo("writer " + broker.getServerId() + " ends sent="+ccount);
    }
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
    * - Check that client 2 does not receive the change.
    */
  @Test(enabled = true, groups = "opendj-256")
  public void replicationServerConnected() throws Exception
  {
     clearChangelogDB(replicationServer);
      TestCaseUtils.initializeTestBackend(true);

      debugInfo("Starting replicationServerConnected");
      ReplicationBroker broker1 = null;
      ReplicationBroker broker2 = null;

       // - Create 2 connected replicationServer
       ReplicationServer[] changelogs = new ReplicationServer[2];
       int[] changelogPorts = TestCaseUtils.findFreePorts(2);
       int[] changelogIds = new int[] { 90, 91 };
       int[] brokerIds = new int[] { 100, 101 };

       for (int i = 0; i < 2; i++)
       {
         changelogs[i] = null;
         // create the 2 replicationServer
         // and connect the first one to the other one
         SortedSet<String> servers = new TreeSet<>();

         // Connect only replicationServer[0] to ReplicationServer[1]
         // and not the other way
         if (i==0)
         {
           servers.add("localhost:" + changelogPorts[1]);
         }
         ReplServerFakeConfiguration conf =
           new ReplServerFakeConfiguration(changelogPorts[i], "replicationServerTestReplicationServerConnectedDb"+i, replicationDbImplementation,
                                          0, changelogIds[i], 0, 100, servers);
         changelogs[i] = new ReplicationServer(conf);
       }

    try
    {
         // Create and connect client1 to changelog1
         // and client2 to changelog2
         broker1 = openReplicationSession(TEST_ROOT_DN, brokerIds[0], 100, changelogPorts[0], 1000);
         broker2 = openReplicationSession(TEST_ROOT_DN, brokerIds[1], 100, changelogPorts[1], 1000);

         // - Test messages between clients by publishing now
         CSNGenerator csnGen = new CSNGenerator(brokerIds[0], TimeThread.getTime());
         String user1entryUUID = "33333333-3333-3333-3333-333333333333";
         String baseUUID  = "22222222-2222-2222-2222-222222222222";

         // - Add
         String lentry = "dn: o=example," + TEST_ROOT_DN_STRING + "\n"
             + "objectClass: top\n" + "objectClass: domain\n"
             + "entryUUID: " + user1entryUUID + "\n";
         Entry entry = TestCaseUtils.entryFromLdifString(lentry);
         AddMsg addMsg = new AddMsg(csnGen.newCSN(), EXAMPLE_DN,
             user1entryUUID, baseUUID, entry.getObjectClassAttribute(),
             entry.getAttributes(), new ArrayList<Attribute>());
         broker1.publish(addMsg);

         // - Modify
         Attribute attr1 = Attributes.create("description", "new value");
      List<Modification> mods =
          Arrays.asList(new Modification(ModificationType.REPLACE, attr1));
         ModifyMsg modMsg = new ModifyMsg(csnGen.newCSN(), EXAMPLE_DN, mods, "fakeuniqueid");
         broker1.publish(modMsg);

         // - Check msg received by broker, through changeLog2
         List<ReplicationMsg> msgs = receiveReplicationMsgs(broker2, 2);
         Assertions.assertThat(msgs).containsExactly(addMsg, modMsg);

         // Then change the config to remove replicationServer[1] from
         // the configuration of replicationServer[0]

         SortedSet<String> servers = new TreeSet<>();
         // Configure replicationServer[0] to be disconnected from ReplicationServer[1]
         ReplServerFakeConfiguration conf =
           new ReplServerFakeConfiguration(changelogPorts[0], "changelogDb0", replicationDbImplementation,
                                          0, changelogIds[0], 0, 100, servers);
         changelogs[0].applyConfigurationChange(conf) ;

         // The link between RS[0] & RS[1] should be destroyed by the new configuration.
         // So we expect a timeout exception when calling receive on RS[1].
         // Send an update and check that RS[1] does not receive the message after the timeout

      // - Del
      DeleteMsg delMsg = new DeleteMsg(EXAMPLE_DN, csnGen.newCSN(), user1entryUUID);
      broker1.publish(delMsg);
      // Should receive some TopologyMsg messages for disconnection between the 2 RSs
      assertOnlyTopologyMsgsReceived(broker2);
    }
    finally
    {
      remove(changelogs);
      stop(broker1, broker2);
    }
  }

  private void assertOnlyTopologyMsgsReceived(ReplicationBroker broker2)
  {
    try
    {
      while (true)
      {
        ReplicationMsg msg = broker2.receive();
        if (msg instanceof TopologyMsg)
        {
          debugInfo("Broker 2 received: " + msg);
        }
        else
        {
          fail("Broker: receive successed when it should fail. "
              + "This broker was disconnected by configuration."
              + " Received: " + msg);
        }
      }
    }
    catch (SocketTimeoutException expected)
    {
      debugInfo("Ending replicationServerConnected");
    }
  }

}
