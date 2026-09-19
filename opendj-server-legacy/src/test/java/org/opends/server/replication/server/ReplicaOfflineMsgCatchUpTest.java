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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.DummyReplicationDomain;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.util.TestTimer;
import org.testng.annotations.Test;

/**
 * A directory server is never sent a ReplicaOfflineMsg. ReplicationServerDomain.put() does not
 * queue one for a directory server, but a directory server which is catching up reads its
 * updates from the changelog, where the cursor of a replica which went offline synthesizes one
 * from the offline CSN of that replica, and the writer used to publish it (issue #1029).
 * <p>
 * The message costs the session a permit of its send window for good: the replication server
 * takes one for every message it hands to the writer, and a directory server gives credit only
 * for the updates it replays - a ReplicaOfflineMsg is not one of them. The state of the handler
 * does not move past an offline CSN either, so every catch-up round read the same message
 * again, one permit each. A session which lost more than half its window that way was never
 * sent anything again.
 * <p>
 * The tests read the send window from the monitor entry of the handler once the directory
 * server holds a change published after the offline message: nothing the replication server
 * sends after that is left to account for, and the broker of the tests never gives credit, so
 * the window is the size the directory server announced less the messages it was sent.
 */
@SuppressWarnings("javadoc")
public class ReplicaOfflineMsgCatchUpTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  private static final int WINDOW_SIZE = 100;
  /** The replica which goes offline. */
  private static final int OFFLINE_DS_ID = 81;
  /** The directory server whose catch-up meets the offline CSN of {@link #OFFLINE_DS_ID}. */
  private static final int CATCHING_UP_DS_ID = 82;
  /** The replica whose change tells the tests the catch-up is over. */
  private static final int LATER_DS_ID = 83;

  /**
   * The catch-up round of a directory server which is behind the last change of the offline
   * replica holds that change and the offline message which follows it. The change is sent, the
   * message is not.
   */
  @Test
  public void aDirectoryServerBehindTheOfflineReplicaIsSentItsChangesButNotItsOfflineMessage()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker offlineBroker = null;
    ReplicationBroker broker = null;
    ReplicationBroker laterBroker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("replicaOfflineCatchUpBehindDb", 8301, replicationPort);
      final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);

      offlineBroker = openReplicationSession(baseDN, OFFLINE_DS_ID, WINDOW_SIZE, replicationPort,
          5000, EMPTY_DN_GENID);
      final CSNGenerator csns = new CSNGenerator(OFFLINE_DS_ID, 0);
      final DeleteMsg lastChange = newDeleteMsg(csns.newCSN());
      offlineBroker.publish(lastChange);
      offlineBroker.publish(new ReplicaOfflineMsg(csns.newCSN()));
      offlineBroker.stop();
      waitForDisconnectedDirectoryServer(domain, OFFLINE_DS_ID);

      // an empty state: the catch-up starts before the change of the offline replica
      broker = openReplicationSession(baseDN, CATCHING_UP_DS_ID, WINDOW_SIZE, replicationPort,
          5000, EMPTY_DN_GENID);
      laterBroker = openReplicationSession(baseDN, LATER_DS_ID, WINDOW_SIZE, replicationPort,
          5000, EMPTY_DN_GENID);
      final DeleteMsg laterChange = newDeleteMsg(new CSNGenerator(LATER_DS_ID, 0).newCSN());
      laterBroker.publish(laterChange);

      final List<ReplicationMsg> received = receiveUntil(broker, laterChange.getCSN());
      assertThat(currentSendWindow(domain.getConnectedDSs().get(CATCHING_UP_DS_ID)))
          .as("the send window of the session is short of the two changes the directory "
              + "server was sent, and of nothing else - it received: %s", received)
          .isEqualTo(WINDOW_SIZE - 2);
      assertThat(received)
          .as("the directory server was sent the offline message of the replica it caught up past")
          .noneMatch(ReplicaOfflineMsg.class::isInstance);
      assertThat(csnsOf(received)).contains(lastChange.getCSN());
    }
    finally
    {
      stop(laterBroker, broker, offlineBroker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * The catch-up round of a directory server which already holds the last change of the
   * offline replica holds nothing but the offline message. It is not sent, and the directory
   * server is left following the queue of the domain: the change published next comes from
   * there.
   */
  @Test
  public void aDirectoryServerUpToDateWithTheOfflineReplicaIsNotSentItsOfflineMessage()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker offlineBroker = null;
    ReplicationBroker broker = null;
    ReplicationBroker laterBroker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("replicaOfflineCatchUpUpToDateDb", 8302, replicationPort);
      final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);

      offlineBroker = openReplicationSession(baseDN, OFFLINE_DS_ID, WINDOW_SIZE, replicationPort,
          5000, EMPTY_DN_GENID);
      final CSNGenerator csns = new CSNGenerator(OFFLINE_DS_ID, 0);
      final DeleteMsg lastChange = newDeleteMsg(csns.newCSN());
      offlineBroker.publish(lastChange);
      offlineBroker.publish(new ReplicaOfflineMsg(csns.newCSN()));
      offlineBroker.stop();
      waitForDisconnectedDirectoryServer(domain, OFFLINE_DS_ID);

      // a state which holds the change: the catch-up starts between it and the offline CSN
      final ServerState state = new ServerState();
      state.update(lastChange.getCSN());
      broker = openReplicationSession(baseDN, CATCHING_UP_DS_ID, replicationPort, state);
      laterBroker = openReplicationSession(baseDN, LATER_DS_ID, WINDOW_SIZE, replicationPort,
          5000, EMPTY_DN_GENID);
      final DeleteMsg laterChange = newDeleteMsg(new CSNGenerator(LATER_DS_ID, 0).newCSN());
      laterBroker.publish(laterChange);

      final List<ReplicationMsg> received = receiveUntil(broker, laterChange.getCSN());
      assertThat(currentSendWindow(domain.getConnectedDSs().get(CATCHING_UP_DS_ID)))
          .as("the send window of the session is short of the one change the directory "
              + "server was sent, and of nothing else - it received: %s", received)
          .isEqualTo(WINDOW_SIZE - 1);
      assertThat(received)
          .as("the directory server was sent the offline message of a replica it was up to date with")
          .noneMatch(ReplicaOfflineMsg.class::isInstance);
      assertThat(csnsOf(received)).doesNotContain(lastChange.getCSN());
    }
    finally
    {
      stop(laterBroker, broker, offlineBroker);
      removeQuietly(replicationServer);
    }
  }

  private ReplicationServer newReplicationServer(String dbDirName, int serverId, int replicationPort)
      throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        replicationPort, dbDirName, 0, serverId, 0, WINDOW_SIZE, new TreeSet<String>()));
  }

  /** Opens a session announcing the given state rather than an empty one. */
  private ReplicationBroker openReplicationSession(DN baseDN, int serverId, int replicationPort,
      ServerState state) throws Exception
  {
    final DomainFakeCfg config = newFakeCfg(baseDN, serverId, replicationPort);
    config.setWindowSize(WINDOW_SIZE);
    final ReplicationBroker broker = new ReplicationBroker(
        new DummyReplicationDomain(EMPTY_DN_GENID), state, config, getReplSessionSecurity());
    connect(broker, 5000);
    return broker;
  }

  private static DeleteMsg newDeleteMsg(CSN csn)
  {
    return new DeleteMsg(DN.valueOf("uid=" + csn.getServerId() + "," + TEST_ROOT_DN_STRING), csn,
        "entry-uuid-" + csn.getServerId());
  }

  private static List<CSN> csnsOf(List<ReplicationMsg> msgs)
  {
    final List<CSN> csns = new ArrayList<>();
    for (ReplicationMsg msg : msgs)
    {
      if (msg instanceof UpdateMsg)
      {
        csns.add(((UpdateMsg) msg).getCSN());
      }
    }
    return csns;
  }

  /** The {@code current-send-window} attribute of the monitor entry of the handler. */
  private static int currentSendWindow(DataServerHandler dsHandler)
  {
    assertThat(dsHandler).as("the directory server is not connected anymore").isNotNull();
    for (Attribute attribute : dsHandler.getMonitorData())
    {
      if ("current-send-window".equals(attribute.getAttributeDescription().getNameOrOID()))
      {
        return Integer.parseInt(attribute.iterator().next().toString());
      }
    }
    throw new AssertionError("no current-send-window on the monitor entry of " + dsHandler);
  }

  /**
   * Waits for the reader of the directory server to be done: it processes what the session
   * received in order and stops the handler last, so a replica which is gone from the domain
   * has had its ReplicaOfflineMsg recorded in the changelog.
   */
  private static void waitForDisconnectedDirectoryServer(final ReplicationServerDomain domain,
      final int serverId) throws Exception
  {
    new TestTimer.Builder()
        .maxSleep(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .sleepTimes(10, TimeUnit.MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(new Callable<Void>()
        {
          @Override
          public Void call()
          {
            assertThat(domain.getConnectedDSs())
                .as("the replica which went offline never left the domain")
                .doesNotContainKey(serverId);
            return null;
          }
        });
  }

  /** Teardown must never mask the primary assertion failure. */
  private void removeQuietly(ReplicationServer replicationServer)
  {
    try
    {
      remove(replicationServer);
    }
    catch (Exception ignored)
    {
    }
  }
}
