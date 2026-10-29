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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.messages.ReplicationMessages.ERR_DS_BADLY_DISCONNECTED;
import static org.opends.messages.ReplicationMessages.ERR_WRITER_UNEXPECTED_EXCEPTION;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

/**
 * A {@code ServerWriter} reports a connection which broke under it as badly disconnected - msgID
 * 211 for a directory server, 181 for a replication server, both at ERROR - but not a write which
 * fails on a session this side has closed itself: {@code ServerReader} closes the session once
 * the peer has sent its {@code StopMsg}, and a writer which had an update for that peer at that
 * moment then fails on the closed socket. Under load that is every peer which leaves in order: a
 * directory server stopped, or moving to another replication server, a replication server stopped
 * - the restart rows of the replication convergence run (#1208) logged it for each of them.
 * <p>
 * The writer runs on the thread of the test, against a session over the loopback and a handler
 * which hands it one update: what is under test is only what the writer logs when the write of
 * that update fails.
 */
@SuppressWarnings("javadoc")
public class ServerWriterDisconnectTest extends ReplicationTestCase
{
  private static final int RS_ID = 9301;
  private static final int SOCKET_TIMEOUT_MS = 5000;

  /**
   * The session runs its publisher thread, as {@code ServerHandler} starts it, and is closed as
   * {@code ServerReader} closes it on the {@code StopMsg} of the peer. The update the writer takes
   * next is written by {@code publish()} itself - the close took the session off the queueing
   * branch - and fails on the closed socket. That is no disconnection of the peer.
   */
  @Test
  public void aWriteFailingOnASessionThisSideClosedIsNotReported() throws Exception
  {
    final int dsId = 9311;
    try (ServerSocket listen = new ServerSocket(0))
    {
      final Session[] pair = connectSessionPair(listen);
      final Session session = pair[1];
      try
      {
        session.start();
        session.waitForStartup();
        session.close();

        final List<String> logged = runWriterWithOneUpdate(session, dsId, false);

        assertThat(badlyDisconnected(logged, dsId))
            .as("a write which failed on a session closed on this side was reported as a peer "
                + "badly disconnected")
            .isEmpty();
      }
      finally
      {
        StaticUtils.close(pair);
      }
    }
  }

  /**
   * The sockets of the session are closed under it, nothing on this side having closed the
   * session: the write fails on a connection which broke, and the writer reports it.
   */
  @Test
  public void aWriteFailingOnASessionNothingClosedIsReported() throws Exception
  {
    final int dsId = 9312;
    try (ServerSocket listen = new ServerSocket(0))
    {
      final Session[] pair = connectSessionPair(listen);
      final Session session = pair[1];
      try
      {
        closeTheSocketsUnder(session);

        final List<String> logged = runWriterWithOneUpdate(session, dsId, false);

        assertThat(badlyDisconnected(logged, dsId))
            .as("a write which failed on a connection which broke under the session was not "
                + "reported")
            .hasSize(1);
      }
      finally
      {
        StaticUtils.close(pair);
      }
    }
  }

  /**
   * The same broken connection while the handler is being shut down: what fails then is the end
   * of a session the shutdown is taking down anyway, which {@code ServerReader} does not report
   * either.
   */
  @Test
  public void aWriteFailingWhileTheHandlerShutsDownIsNotReported() throws Exception
  {
    final int dsId = 9313;
    try (ServerSocket listen = new ServerSocket(0))
    {
      final Session[] pair = connectSessionPair(listen);
      final Session session = pair[1];
      try
      {
        closeTheSocketsUnder(session);

        final List<String> logged = runWriterWithOneUpdate(session, dsId, true);

        assertThat(badlyDisconnected(logged, dsId))
            .as("a write which failed while the handler was shutting down was reported as a peer "
                + "badly disconnected")
            .isEmpty();
      }
      finally
      {
        StaticUtils.close(pair);
      }
    }
  }

  /**
   * Runs a writer for a directory server on the thread of the test, its handler handing it one
   * update, and answers what was logged meanwhile, each record once - the error log of the tests
   * holds every record twice. It checks that the writer got as far as the write, and that the
   * write failed on the socket rather than on anything else.
   */
  private List<String> runWriterWithOneUpdate(final Session session, final int dsId,
      final boolean shuttingDown) throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final UpdateMsg update = new DeleteMsg(DN.valueOf("uid=writer," + TEST_ROOT_DN_STRING),
        new CSNGenerator(dsId, 0).newCSN(), "00000000-0000-0000-0000-000000000000");

    final DataServerHandler handler = mock(DataServerHandler.class);
    /*
     * The handler reaches getReplicationServerId() of the package-private MessageHandler through a
     * bridge method, which the mock does not intercept: the replication server it reads is set.
     */
    final ReplicationServer replicationServer = mock(ReplicationServer.class);
    when(replicationServer.getServerId()).thenReturn(RS_ID);
    final Field replicationServerField = MessageHandler.class.getDeclaredField("replicationServer");
    replicationServerField.setAccessible(true);
    replicationServerField.set(handler, replicationServer);
    when(handler.take()).thenReturn(update, (UpdateMsg) null);
    when(handler.isDataServer()).thenReturn(true);
    when(handler.getServerId()).thenReturn(dsId);
    when(handler.getProtocolVersion()).thenReturn(ProtocolVersion.getCurrentVersion());
    when(handler.shuttingDown()).thenReturn(shuttingDown);
    when(handler.getBadlyDisconnectedErrorMessage()).thenReturn(ERR_DS_BADLY_DISCONNECTED.get(
        RS_ID, dsId, session.getReadableRemoteAddress(), baseDN));
    final ReplicationServerDomain domain = mock(ReplicationServerDomain.class);
    when(domain.getBaseDN()).thenReturn(baseDN);

    final int before = TestCaseUtils.ERROR_TEXT_WRITER.getMessages().size();
    new ServerWriter(session, handler, domain, new DSRSShutdownSync()).run();
    final List<String> all = TestCaseUtils.ERROR_TEXT_WRITER.getMessages();
    final List<String> logged = new ArrayList<>(new LinkedHashSet<>(all.subList(before, all.size())));

    verify(handler).countSentUpdate(update);
    verify(domain).stopServer(handler, false);
    assertThat(withMsgID(logged, ERR_WRITER_UNEXPECTED_EXCEPTION.ordinal()))
        .as("the write of the update failed on something else than the socket")
        .isEmpty();
    return logged;
  }

  private static Set<String> badlyDisconnected(final List<String> logged, final int dsId)
  {
    final Set<String> records = withMsgID(logged, ERR_DS_BADLY_DISCONNECTED.ordinal());
    records.removeIf(record -> !record.contains("DS(" + dsId + ")"));
    return records;
  }

  private static Set<String> withMsgID(final List<String> logged, final int msgID)
  {
    final Set<String> records = new LinkedHashSet<>();
    for (final String record : logged)
    {
      if (record.contains("msgID=" + msgID + " "))
      {
        records.add(record);
      }
    }
    return records;
  }

  /**
   * Closes the sockets a session writes through while leaving the session unaware of it, so that
   * its next write fails - the state a connection which broke leaves it in.
   */
  private static void closeTheSocketsUnder(final Session session) throws Exception
  {
    for (final String name : new String[] { "secureSocket", "plainSocket" })
    {
      final Field socket = Session.class.getDeclaredField(name);
      socket.setAccessible(true);
      StaticUtils.close((Closeable) socket.get(session));
    }
  }

  /**
   * A connected pair of sessions over the loopback, the client end first, neither started. The
   * replication server end is the second.
   */
  private Session[] connectSessionPair(final ServerSocket listen) throws Exception
  {
    final ReplSessionSecurity security = getReplSessionSecurity();
    final Socket clientSocket = new Socket("127.0.0.1", listen.getLocalPort());
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
      final Socket serverSocket = listen.accept();
      serverSocket.setTcpNoDelay(true);
      final Session serverEnd = security.createServerSession(serverSocket, SOCKET_TIMEOUT_MS);
      return new Session[] { clientEnd.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS), serverEnd };
    }
    finally
    {
      executor.shutdown();
    }
  }
}
