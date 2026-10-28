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
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.TopologyMsg;
import org.testng.annotations.Test;

/**
 * Tests for issue #1241: the severity an aborted replication handshake is logged with.
 * <p>
 * {@code abortStart} used to log the reason of every abort at ERROR, so a peer which
 * restarts under load reads as an error of every server it was handshaking with: the
 * listen thread which gives way to a cross connection
 * ({@code WARN_TIMEOUT_WHEN_CROSS_CONNECTION}, a warning by its own name), and the
 * handshake its peer left ({@code *_DISCONNECTED_DURING_HANDSHAKE}, where a peer which gave
 * up on its own timeout logs that timeout at WARNING itself). Both are warnings now; what
 * this server rejects, and a peer which breaks the protocol, are still errors -- the last
 * test pins that, so that logging every abort as a warning is not a way to pass the others.
 * <p>
 * Each test drives one handler on a session pair of its own, as the listen thread or the
 * connect thread of the server would, and reads the severity off the record of the reason.
 * The peer which goes away aborts its end of the session rather than closing it:
 * {@code Session.close()} publishes a {@code StopMsg}, which is a peer ending the handshake
 * in order, and is aborted on silently.
 */
@SuppressWarnings("javadoc")
public class HandshakeAbortSeverityTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /** Longer than the longest wait of {@code lockDomainWithTimeout()}, which is 8 seconds. */
  private static final long LOCK_TIMEOUT_MS = 30000;
  private static final int REMOTE_DS_ID = 61;
  private static final int REMOTE_RS_ID = 62;

  /**
   * The listen thread waits a random 3 to 8 seconds for the domain lock, so that of two
   * replication servers connecting to each other at the same time one gives way: the
   * connect thread holding the lock here is the one which goes on.
   */
  @Test
  public void aCrossConnectionGivenWayToIsAWarning() throws Exception
  {
    final int rsId = 8261;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final ExecutorService lockHolder = Executors.newSingleThreadExecutor();
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer("handshakeAbortSeverityCrossDb", rsId);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);

      final Session[] sessionPair =
          connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        final ReplServerStartMsg startMsg =
            (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
        final ReplicationServerHandler handler =
            new ReplicationServerHandler(session, 100, replicationServer, 100);

        // The lock is held by a thread of its own, as the connect thread holds it: the
        // domain lock is reentrant, so the thread of the handshake would take it.
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch handshakeDone = new CountDownLatch(1);
        final Future<Void> holding = lockHolder.submit(() -> {
          domain.lock();
          try
          {
            locked.countDown();
            handshakeDone.await();
          }
          finally
          {
            domain.release();
          }
          return null;
        });
        assertTrue(locked.await(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "the domain lock should have been taken");

        final List<String> records;
        try
        {
          records = errorLogRecordsOf(() -> {
            handler.startFromRemoteRS(startMsg);
            return null;
          });
        }
        finally
        {
          handshakeDone.countDown();
        }
        holding.get(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(recordOf(records, WARN_TIMEOUT_WHEN_CROSS_CONNECTION.get(
                baseDN, REMOTE_RS_ID, session.getReadableRemoteAddress(), rsId)))
            .as("giving way to a cross connection is a warning, as its message says")
            .contains("severity=WARNING");
      }
    }
    finally
    {
      lockHolder.shutdownNow();
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void aDataServerGoneDuringTheHandshakeIsAWarning() throws Exception
  {
    final int rsId = 8262;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer("handshakeAbortSeverityDSDb", rsId);

      final Session[] sessionPair =
          connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        final ServerStartMsg startMsg = (ServerStartMsg) sendThrough(remoteEnd, session,
            new ServerStartMsg(REMOTE_DS_ID, "127.0.0.1:1389", baseDN, 100, 0,
                new ServerState(), -1, true, (byte) 1));
        remoteEnd.abort(new IOException("the directory server gave up on the handshake"));
        final DataServerHandler handler =
            new DataServerHandler(session, 100, replicationServer, 100);

        final List<String> records = errorLogRecordsOf(() -> {
          handler.startFromRemoteDS(startMsg);
          return null;
        });

        assertThat(recordOf(records, WARN_DS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_DS_ID, rsId)))
            .as("a directory server which went away during the handshake is a warning")
            .contains("severity=WARNING");
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void aReplicationServerGoneDuringTheHandshakeItStartedIsAWarning() throws Exception
  {
    final int rsId = 8263;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer("handshakeAbortSeverityFromRSDb", rsId);

      final Session[] sessionPair =
          connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        final ReplServerStartMsg startMsg =
            (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
        remoteEnd.abort(new IOException("the replication server gave up on the handshake"));
        final ReplicationServerHandler handler =
            new ReplicationServerHandler(session, 100, replicationServer, 100);

        final List<String> records = errorLogRecordsOf(() -> {
          handler.startFromRemoteRS(startMsg);
          return null;
        });

        assertThat(recordOf(records, WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_RS_ID, rsId)))
            .as("a replication server which went away during the handshake it started is a warning")
            .contains("severity=WARNING");
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  @Test
  public void aReplicationServerGoneDuringTheHandshakeThisServerStartedIsAWarning() throws Exception
  {
    final int rsId = 8264;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer("handshakeAbortSeverityToRSDb", rsId);

      final Session[] sessionPair =
          connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        remoteEnd.abort(new IOException("the replication server went away"));
        final ReplicationServerHandler handler =
            new ReplicationServerHandler(session, 100, replicationServer, 100);

        final List<String> records = errorLogRecordsOf(() -> {
          assertFalse(handler.connect(baseDN, true), "the peer went away, so this is no session");
          return null;
        });

        assertThat(recordOf(records,
                WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(rsId, session.getReadableRemoteAddress())))
            .as("a replication server which went away during the handshake this server started"
                + " is a warning")
            .contains("severity=WARNING");
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * A peer which answers the start message of this server with something else breaks the
   * protocol, and that is still an error: of the aborts, only the two above are warnings.
   */
  @Test
  public void aPeerWhichBreaksTheProtocolIsStillAnError() throws Exception
  {
    final int rsId = 8265;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer("handshakeAbortSeverityProtocolDb", rsId);

      final Session[] sessionPair =
          connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        // Published ahead of the start message it answers: the session reads it all the same.
        remoteEnd.publish(new TopologyMsg(
            Collections.<DSInfo> emptyList(), Collections.<RSInfo> emptyList()));
        final ReplicationServerHandler handler =
            new ReplicationServerHandler(session, 100, replicationServer, 100);

        final List<String> records = errorLogRecordsOf(() -> {
          assertFalse(handler.connect(baseDN, true), "the peer broke the protocol, so this is no session");
          return null;
        });

        assertThat(recordOf(records, ERR_REPLICATION_PROTOCOL_MESSAGE_TYPE.get(
                TopologyMsg.class.getCanonicalName(), "ReplServerStartMsg")))
            .as("a peer which breaks the protocol is an error")
            .contains("severity=ERROR");
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  private ReplicationServer newReplicationServer(String dbName, int rsId) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        TestCaseUtils.findFreePort(), dbName, 0, rsId, 0, 100, new TreeSet<String>()));
  }

  /**
   * Returns the start message of a fake peer replication server.
   * <p>
   * A generation id of -1 leaves the one of the domain alone, and the session stays
   * encrypted, so that neither end has to leave the SSL session for the handshake to go on.
   */
  private ReplServerStartMsg replServerStartMsg(DN baseDN)
  {
    return new ReplServerStartMsg(REMOTE_RS_ID, "127.0.0.1:8989", baseDN, 100,
        new ServerState(), -1, true, (byte) 1, 5000);
  }

  /**
   * Publishes the provided start message from the remote end and returns it as the local
   * end reads it, which is the message the listen thread hands to the handler: it carries
   * the protocol version of the session, which a message built here does not.
   */
  private ReplicationMsg sendThrough(Session remoteEnd, Session session, ReplicationMsg startMsg)
      throws Exception
  {
    remoteEnd.publish(startMsg);
    return session.receive();
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
      final Future<Session> clientEnd = executor.submit(
          () -> security.createClientSession(clientSocket, SOCKET_TIMEOUT_MS));

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
