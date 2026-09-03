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

import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

/**
 * The shutdown of a replication server must let a ReplicaOfflineMsg sent by a collocated
 * directory server be forwarded to the other replication servers of the topology before the
 * server handlers are stopped - stopping them deactivates their consumer, clears their message
 * queue and closes their session, after which the message can no longer be sent.
 * <p>
 * The tests drive {@link DSRSShutdownSync} directly rather than through a collocated directory
 * server: the contract they pin is when the shutdown of the replication server waits, and how
 * long, without depending on the timing of a real session.
 */
@SuppressWarnings("javadoc")
public class ReplicationServerShutdownSyncTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  private static final int REMOTE_RS_ID = 92;
  private static final int REMOTE_DS_ID = 93;
  /** The collocated replica whose ReplicaOfflineMsg the shutdown waits for. */
  private static final int LOCAL_DS_ID = 94;
  /** Time given to the forwarding thread before it releases the shutdown. */
  private static final long FORWARD_DELAY = 500;

  @Test
  public void shutdownWaitsForTheReplicaOfflineMsgToBeForwarded() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncWaitDb", 8221);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN, session);

        final long startTime = System.currentTimeMillis();
        shutdownSync.replicaOfflineMsgSent(baseDN, LOCAL_DS_ID);
        replicationServer.shutdown();
        final long elapsed = System.currentTimeMillis() - startTime;

        assertThat(elapsed).isGreaterThanOrEqualTo(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void shutdownResumesAsSoonAsTheReplicaOfflineMsgIsForwarded() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    final Thread forwarder = newForwarderThread(shutdownSync, baseDN);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncForwardDb", 8222);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN, session);
        shutdownSync.replicaOfflineMsgSent(baseDN, LOCAL_DS_ID);

        final long startTime = System.currentTimeMillis();
        forwarder.start();
        replicationServer.shutdown();
        final long elapsed = System.currentTimeMillis() - startTime;

        assertThat(elapsed).isGreaterThanOrEqualTo(FORWARD_DELAY)
            .isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      forwarder.join();
      removeQuietly(replicationServer);
    }
  }

  /**
   * With no other replication server connected there is nobody to forward the message to, so
   * waiting would only delay the shutdown of a standalone server by the whole grace period.
   */
  @Test
  public void shutdownIsNotDelayedWhenNoOtherReplicationServerCanForwardTheMessage() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncAloneDb", 8223);
      replicationServer.getReplicationServerDomain(baseDN, true);

      final long startTime = System.currentTimeMillis();
      shutdownSync.replicaOfflineMsgSent(baseDN, LOCAL_DS_ID);
      replicationServer.shutdown();
      final long elapsed = System.currentTimeMillis() - startTime;

      assertThat(elapsed).isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The writer serving a directory server must not hold back the shutdown either: it used to loop
   * on the pending message until the grace period expired, although its handler had already been
   * shut down and a ReplicaOfflineMsg is never sent to a directory server anyway.
   */
  @Test
  public void shutdownIsNotDelayedByTheWriterServingADirectoryServer() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer(shutdownSync, "shutdownSyncDataServerDb", 8225, replicationPort);
      broker = openReplicationSession(baseDN, REMOTE_DS_ID, 100, replicationPort, 5000, EMPTY_DN_GENID);

      final long startTime = System.currentTimeMillis();
      shutdownSync.replicaOfflineMsgSent(baseDN, LOCAL_DS_ID);
      replicationServer.shutdown();
      final long elapsed = System.currentTimeMillis() - startTime;

      assertThat(elapsed).isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
    }
    finally
    {
      stop(broker);
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void shutdownIsNotDelayedWhenNoReplicaOfflineMsgIsPending() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync();
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(shutdownSync, "shutdownSyncNoMsgDb", 8224);
      final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        registerConnectedReplicationServer(replicationServer, baseDN, session);

        final long startTime = System.currentTimeMillis();
        replicationServer.shutdown();
        final long elapsed = System.currentTimeMillis() - startTime;

        assertThat(elapsed).isLessThan(DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD);
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  private ReplicationServer newReplicationServer(DSRSShutdownSync shutdownSync, String dbDirName,
      int serverId) throws Exception
  {
    return newReplicationServer(shutdownSync, dbDirName, serverId, TestCaseUtils.findFreePort());
  }

  private ReplicationServer newReplicationServer(DSRSShutdownSync shutdownSync, String dbDirName,
      int serverId, int replicationPort) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        replicationPort, dbDirName, 0, serverId, 0, 100, new TreeSet<String>()), shutdownSync);
  }

  /** Registers a peer replication server on the domain, exactly as the handshake does. */
  private void registerConnectedReplicationServer(ReplicationServer replicationServer, DN baseDN,
      Session session) throws Exception
  {
    final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);
    final ReplicationServerHandler rsHandler =
        new ReplicationServerHandler(session, 100, replicationServer, 100);
    rsHandler.serverId = REMOTE_RS_ID;
    rsHandler.serverURL = "127.0.0.1:1636";
    rsHandler.setBaseDNAndDomain(baseDN, false);
    try
    {
      domain.lock();
      domain.register(rsHandler);
    }
    finally
    {
      domain.release();
    }
  }

  private Thread newForwarderThread(final DSRSShutdownSync shutdownSync, final DN baseDN)
  {
    return new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          Thread.sleep(FORWARD_DELAY);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          return;
        }
        shutdownSync.replicaOfflineMsgForwarded(baseDN, LOCAL_DS_ID);
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

  /**
   * Establishes a connected session pair over the given listen socket, as a remote server
   * connecting to the RS would. The TLS negotiation performed by the session factories needs both
   * ends handshaking at the same time, so the client end runs on its own thread.
   *
   * @return the two sessions: the remote (client) end first, then the local (server) end to hand
   *         to the handler under test
   */
  private Session[] connectSessionPair(ServerSocket listenSocket, final ReplSessionSecurity security)
      throws Exception
  {
    final Socket clientSocket = new Socket("127.0.0.1", listenSocket.getLocalPort());
    clientSocket.setTcpNoDelay(true);
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Session> clientEnd = null;
    boolean connected = false;
    try
    {
      clientEnd = executor.submit(new Callable<Session>()
      {
        @Override
        public Session call() throws Exception
        {
          return security.createClientSession(clientSocket, SOCKET_TIMEOUT_MS);
        }
      });

      final Socket serverSocket = listenSocket.accept();
      serverSocket.setTcpNoDelay(true);
      final Session serverEnd = security.createServerSession(serverSocket, SOCKET_TIMEOUT_MS);
      assertThat(serverEnd).as("could not create a session for the handler under test").isNotNull();

      final Session[] sessionPair =
          new Session[] { clientEnd.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS), serverEnd };
      connected = true;
      return sessionPair;
    }
    finally
    {
      if (!connected)
      {
        // Nobody owns the client end yet: close whatever it managed to create.
        closeClientEndQuietly(clientEnd, clientSocket);
      }
      executor.shutdown();
    }
  }

  private void closeClientEndQuietly(Future<Session> clientEnd, Socket clientSocket)
  {
    if (clientEnd != null)
    {
      try
      {
        final Session session = clientEnd.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (session != null)
        {
          session.close();
        }
      }
      catch (Exception ignored)
      {
        clientEnd.cancel(true);
      }
    }
    StaticUtils.close(clientSocket);
  }
}
