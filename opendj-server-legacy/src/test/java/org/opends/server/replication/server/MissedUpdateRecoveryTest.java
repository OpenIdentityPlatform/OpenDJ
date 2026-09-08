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

import java.net.SocketTimeoutException;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

/**
 * A replica which is served from the in-memory message queue of its handler - it is "following" -
 * is given every change newer than its state by {@code ReplicationServerDomain.put()}. When that
 * does not happen, the handler must read the changelog again instead of waiting forever for a
 * delivery which is not coming: a change missed once would otherwise never be sent again, and the
 * replication server would consider the replica up to date - see issue #963.
 */
@SuppressWarnings("javadoc")
public class MissedUpdateRecoveryTest extends ReplicationTestCase
{
  private static final int RS_ID = 105;
  private static final int DS_ID = 106;
  /** The replica whose generation id does not match the one of the domain. */
  private static final int BAD_GENID_DS_ID = 107;
  /** The replica the changes of this test come from. */
  private static final int PUBLISHER_DS_ID = 108;
  private static final int WINDOW_SIZE = 100;
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /**
   * How long a change published straight into the changelog is given to reach the replica. The
   * handler notices it is behind on the next tick of its 500 ms wait on an empty queue.
   */
  private static final long DELIVERY_TIMEOUT_MS = 10000;
  /** How long the handler of a replica the domain does not feed is watched for. */
  private static final long NO_DELIVERY_WATCH_MS = 3000;

  /**
   * The regression this test pins: the change is in the changelog and the state of the replica is
   * behind it, so the replica must be sent the change, whatever kept it out of the message queue
   * of its handler.
   */
  @Test
  public void aFollowingReplicaIsSentAChangeItsQueueNeverReceived() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final ExecutorService receiver = Executors.newSingleThreadExecutor();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateRecoveryDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForFollowingDirectoryServer(domain, DS_ID);
      final Future<UpdateMsg> received = receiver.submit(nextUpdate(broker));

      final CSN csn = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, csn);

      final UpdateMsg receivedMsg = getQuietly(received, DELIVERY_TIMEOUT_MS);
      assertThat(receivedMsg)
          .as("the replica was never sent the change the changelog holds for it").isNotNull();
      assertThat(receivedMsg.getCSN()).isEqualTo(csn);
    }
    finally
    {
      stop(broker);
      receiver.shutdownNow();
      removeQuietly(replicationServer);
    }
  }

  /**
   * The domain does not hand its updates to a replica whose generation id does not match, and the
   * changelog must not be read on behalf of such a replica either: the writer would drop every
   * change it read while the state of the handler moved past it, and the changes would then be
   * missing from the delivery which follows the reinitialization of the replica.
   */
  @Test
  public void aReplicaTheDomainDoesNotFeedIsLeftAlone() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    ReplicationBroker badGenIdBroker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = newReplicationServer("missedUpdateBadGenIdDb", replicationPort);
      broker = openReplicationSession(
          baseDN, DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);
      // the domain takes the generation id of the first change it is sent
      broker.publish(new DeleteMsg(
          DN.valueOf("cn=first," + baseDN), new CSN(TimeThread.getTime(), 1, DS_ID), "uuid"));
      badGenIdBroker = openReplicationSession(
          baseDN, BAD_GENID_DS_ID, WINDOW_SIZE, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID + 1);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      waitForFollowingDirectoryServer(domain, DS_ID);
      final DataServerHandler badGenIdHandler = waitForFollowingDirectoryServer(domain, BAD_GENID_DS_ID);
      assertThat(badGenIdHandler.getStatus())
          .as("the second replica was expected to be refused the changes of the domain")
          .isEqualTo(ServerStatus.BAD_GEN_ID_STATUS);

      final CSN csn = new CSN(TimeThread.getTime(), 1, PUBLISHER_DS_ID);
      publishToChangelogOnly(replicationServer, baseDN, csn);

      final long deadline = System.currentTimeMillis() + NO_DELIVERY_WATCH_MS;
      while (System.currentTimeMillis() < deadline)
      {
        assertThat(badGenIdHandler.getServerState().cover(csn))
            .as("the change was recorded as sent to a replica which is not being sent anything")
            .isFalse();
        Thread.sleep(100);
      }
    }
    finally
    {
      stop(badGenIdBroker);
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  /**
   * Writes a change into the changelog the way {@code ReplicationServerDomain.put()} does, minus
   * the copy it hands to the message queue of every connected handler. This is the state the
   * replication server is left in by a change which reached the changelog but not the queue of a
   * handler.
   */
  private void publishToChangelogOnly(ReplicationServer replicationServer, DN baseDN, CSN csn)
      throws Exception
  {
    replicationServer.getChangelogDB().getReplicationDomainDB()
        .publishUpdateMsg(baseDN, new DeleteMsg(DN.valueOf("cn=missed," + baseDN), csn, "uuid"));
  }

  /** Reads from the broker until it is sent an update message, ignoring heartbeats and the like. */
  private Callable<UpdateMsg> nextUpdate(final ReplicationBroker broker)
  {
    return new Callable<UpdateMsg>()
    {
      @Override
      public UpdateMsg call() throws Exception
      {
        while (true)
        {
          try
          {
            final ReplicationMsg msg = broker.receive();
            if (msg instanceof UpdateMsg)
            {
              return (UpdateMsg) msg;
            }
          }
          catch (SocketTimeoutException ignored)
          {
            // nothing was sent to this replica yet, the caller decides how long to wait
          }
        }
      }
    };
  }

  /** Returns what the future holds, or null when it holds nothing yet. */
  private UpdateMsg getQuietly(Future<UpdateMsg> received, long timeoutInMillis) throws Exception
  {
    try
    {
      return received.get(timeoutInMillis, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException e)
    {
      return null;
    }
  }

  private DataServerHandler waitForFollowingDirectoryServer(
      final ReplicationServerDomain domain, final int serverId) throws Exception
  {
    return new TestTimer.Builder()
        .maxSleep(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .sleepTimes(10, TimeUnit.MILLISECONDS)
        .toTimer()
        .repeatUntilSuccess(new Callable<DataServerHandler>()
        {
          @Override
          public DataServerHandler call() throws Exception
          {
            final DataServerHandler handler = domain.getConnectedDSs().get(serverId);
            assertThat(handler).as("the directory server never connected").isNotNull();
            assertThat(handler.isFollowing())
                .as("the directory server never caught up with the changelog").isTrue();
            return handler;
          }
        });
  }

  private ReplicationServer newReplicationServer(String dbDirName, int replicationPort)
      throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        replicationPort, dbDirName, 0, RS_ID, 0, WINDOW_SIZE, new TreeSet<String>()));
  }

  private void removeQuietly(ReplicationServer replicationServer)
  {
    try
    {
      remove(replicationServer);
    }
    catch (Exception ignored)
    {
      // the test has already reported what matters
    }
  }
}
