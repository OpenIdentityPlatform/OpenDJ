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

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

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
import org.testng.annotations.Test;

/**
 * Reproducer for issue #821: a handshake that registered its handler in the
 * replication server domain and failed afterwards (the only failure left in
 * that window is {@code finalizeStart()}, before the reader and writer threads
 * are running) used to leave the dead handler registered forever. Nothing else
 * ever removes it: the normal cleanup is the reader or writer noticing the
 * dead session and calling {@code stopServer()}, and neither thread was
 * started. The stale entry inflates the connected DS count advertised to every
 * connecting DS, is published to the whole topology, keeps the generation id
 * from ever being reset, and permanently refuses reconnection of the same
 * server id with ERR_DUPLICATE_SERVER_ID.
 * <p>
 * The tests drive {@code abortStart()} directly on handlers registered exactly
 * as the handshake code registers them, so they pin the contract without any
 * timing assumptions: an abort after registration must unregister the handler
 * and run the same cleanup as {@code stopServer()}, while an abort before
 * registration (e.g. the duplicate server id rejection) must leave the already
 * connected handler untouched.
 */
@SuppressWarnings("javadoc")
public class HandshakeAbortRegistrationTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  private static final long ADOPTED_GEN_ID = 4801;
  private static final int REMOTE_DS_ID = 41;
  private static final int REMOTE_RS_ID = 42;

  @Test
  public void abortAfterRegisterMustUnregisterDataServer() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          TestCaseUtils.findFreePort(), "handshakeAbortRegistrationDSDb", 0,
          8211, 0, 100, new TreeSet<String>()));
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final ReplSessionSecurity security = getReplSessionSecurity();

      final Session[] sessionPair = connectSessionPair(listen, security);
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        final DataServerHandler dsHandler =
            new DataServerHandler(session, 100, replicationServer, 100);
        initializeFromHandshake(dsHandler, baseDN, REMOTE_DS_ID, true);

        try
        {
          // The handshake registers the handler while holding the domain lock.
          domain.lock();
          domain.register(dsHandler);
          assertSame(domain.getConnectedDSs().get(REMOTE_DS_ID), dsHandler);

          // A generation id adopted while the doomed DS was the only connected
          // one must be reset by the abort, as a regular disconnection would.
          domain.changeGenerationId(ADOPTED_GEN_ID);

          dsHandler.abortStart(null);

          assertFalse(domain.getConnectedDSs().containsKey(REMOTE_DS_ID),
              "the aborted handshake left a dead DataServerHandler registered");
          assertFalse(domain.hasLock(),
              "abortStart must release the domain lock");
          assertEquals(domain.getGenerationId(), -1,
              "unregistering the last DS must reset the unsaved generation id");

          final DataServerHandler reconnecting =
              new DataServerHandler(session, 100, replicationServer, 100);
          reconnecting.serverId = REMOTE_DS_ID;
          assertFalse(domain.isAlreadyConnectedToDS(reconnecting),
              "the dead handler still refuses reconnection of its server id");
        }
        finally
        {
          if (domain.hasLock())
          {
            domain.release();
          }
        }
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void abortAfterRegisterMustUnregisterReplicationServer() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          TestCaseUtils.findFreePort(), "handshakeAbortRegistrationRSDb", 0,
          8212, 0, 100, new TreeSet<String>()));
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final ReplSessionSecurity security = getReplSessionSecurity();

      final Session[] sessionPair = connectSessionPair(listen, security);
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        final ReplicationServerHandler rsHandler =
            new ReplicationServerHandler(session, 100, replicationServer, 100);
        initializeFromHandshake(rsHandler, baseDN, REMOTE_RS_ID, false);

        try
        {
          domain.lock();
          domain.register(rsHandler);
          assertSame(domain.getConnectedRSs().get(REMOTE_RS_ID), rsHandler);

          rsHandler.abortStart(null);

          assertFalse(domain.getConnectedRSs().containsKey(REMOTE_RS_ID),
              "the aborted handshake left a dead ReplicationServerHandler registered");
          assertFalse(domain.hasLock(),
              "abortStart must release the domain lock");
        }
        finally
        {
          if (domain.hasLock())
          {
            domain.release();
          }
        }
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The handshake also aborts before registering, e.g. when rejecting a second
   * connection with an already connected server id. Such an abort must never
   * evict the legitimately connected handler owning that server id.
   */
  @Test
  public void abortBeforeRegisterMustNotEvictConnectedDataServer() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          TestCaseUtils.findFreePort(), "handshakeAbortRegistrationDupDb", 0,
          8213, 0, 100, new TreeSet<String>()));
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final ReplSessionSecurity security = getReplSessionSecurity();

      final Session[] connectedPair = connectSessionPair(listen, security);
      final Session[] duplicatePair = connectSessionPair(listen, security);
      try (Session connectedRemoteEnd = connectedPair[0];
          Session connectedSession = connectedPair[1];
          Session duplicateRemoteEnd = duplicatePair[0];
          Session duplicateSession = duplicatePair[1])
      {
        final DataServerHandler connected =
            new DataServerHandler(connectedSession, 100, replicationServer, 100);
        initializeFromHandshake(connected, baseDN, REMOTE_DS_ID, true);
        try
        {
          domain.lock();
          domain.register(connected);
        }
        finally
        {
          domain.release();
        }

        // Second handshake with the same server id: rejected and aborted
        // before it ever registered.
        final DataServerHandler duplicate =
            new DataServerHandler(duplicateSession, 100, replicationServer, 100);
        initializeFromHandshake(duplicate, baseDN, REMOTE_DS_ID, true);
        assertTrue(domain.isAlreadyConnectedToDS(duplicate));

        duplicate.abortStart(null);

        assertSame(domain.getConnectedDSs().get(REMOTE_DS_ID), connected,
            "aborting an unregistered handshake evicted the connected DS");
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /** Puts the handler in the state it has when the handshake registers it. */
  private void initializeFromHandshake(ServerHandler handler, DN baseDN,
      int remoteServerId, boolean isDataServer) throws Exception
  {
    handler.serverId = remoteServerId;
    handler.serverURL = "127.0.0.1:1636";
    handler.setBaseDNAndDomain(baseDN, isDataServer);
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
   * Establishes a connected session pair over the given listen socket, as a
   * remote server connecting to the RS would. The TLS negotiation performed by
   * the session factories needs both ends handshaking at the same time, so the
   * client end runs on its own thread.
   *
   * @return the two sessions: the remote (client) end first, then the local
   *         (server) end to hand to the handler under test
   */
  private Session[] connectSessionPair(ServerSocket listenSocket,
      final ReplSessionSecurity security) throws Exception
  {
    final Socket clientSocket =
        new Socket("127.0.0.1", listenSocket.getLocalPort());
    clientSocket.setTcpNoDelay(true);
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try
    {
      final Future<Session> clientEnd = executor.submit(new Callable<Session>()
      {
        @Override
        public Session call() throws Exception
        {
          return security.createClientSession(clientSocket, SOCKET_TIMEOUT_MS);
        }
      });

      final Socket serverSocket = listenSocket.accept();
      serverSocket.setTcpNoDelay(true);
      final Session serverEnd =
          security.createServerSession(serverSocket, SOCKET_TIMEOUT_MS);
      assertNotNull(serverEnd,
          "could not create a session for the handler under test");

      return new Session[] {
        clientEnd.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS), serverEnd };
    }
    finally
    {
      executor.shutdown();
    }
  }
}
