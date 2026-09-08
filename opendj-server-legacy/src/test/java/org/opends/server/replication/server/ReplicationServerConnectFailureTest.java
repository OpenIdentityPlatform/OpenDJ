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
import static org.opends.server.util.CollectionUtils.newTreeSet;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StopMsg;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

/**
 * Tests what a replication server logs about the replication servers it connects to,
 * through the replication server rather than through the bookkeeping alone.
 * <p>
 * What is reported is decided in two places -- {@code connect()} and the already connected
 * branch of {@code runConnect()} -- and the decision each of them makes is only correct
 * with respect to the other: a test of {@link ReplicationServer.ConnectFailureReporter} on
 * its own passes with either of them deleted, or with any of the conditions an outage used
 * to be closed on put back in front of it.
 * <p>
 * The two tests which drive a peer of their own drive one which registers with no domain,
 * so the already connected branch of {@code runConnect()} can report nothing about it and
 * {@code connect()} is the only place its recovery can come from. Both shapes they drive --
 * a peer which stops the handshake, and a peer which answers under an address it was not
 * dialled on -- reach the end of {@code connect()} on a session which {@code abortStart}
 * closed and on a peer which is registered nowhere, which is what makes them the outages
 * this server has to close without reading either.
 * <p>
 * Every test configures the peer it drives, and creates the domain it drives it for, before
 * anything is reported. The connect thread runs {@code retainAll} against the configured
 * peers and the domains of the pass on every pass, so a peer or a domain it does not see
 * has whatever was recorded for it cleared about once a second -- which is a record the
 * test is not allowed to rely on. Where a record is made from the test thread rather than
 * by the connect thread, one whole pass is waited for as well, so that no pass which
 * started before the domain existed is still holding a snapshot without it.
 */
@SuppressWarnings("javadoc")
public class ReplicationServerConnectFailureTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /** How long a peer which is down, or which came back, is waited to be reported within. */
  private static final long REPORT_TIMEOUT_MS = 30000;

  private static final int RS_ID = 8241;
  private static final int PEER_RS_ID = 8242;

  /**
   * Tests that a replication server reports a peer it cannot connect to once, and reports
   * the connection which ends that outage.
   * <p>
   * Both servers run their own connect thread, so this goes through {@code runConnect()}:
   * the peer is reported by the connect thread of the server under test, and the
   * connection which ends the outage is reported by whichever of {@code connect()} and the
   * already connected branch wins -- the peer dials back, and either end may connect first.
   */
  @Test
  public void aPeerWhichIsDownIsReportedOnceAndItsReturnIsReported() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = HostPort.valueOf("127.0.0.1:" + ports[1]);
    final String restored = NOTE_REPLICATION_SERVER_CONNECT_RESTORED.get(RS_ID, peerAddress, baseDN).toString();

    // Held rather than returned: whatever the capture below throws, these are what the
    // listen ports, the threads and the changelogs of both servers hang on.
    final ReplicationServer[] servers = new ReplicationServer[2];
    try
    {
      final List<String> records = errorLogRecordsOf(live -> {
        /*
         * Built inside the capture: the constructor starts the connect thread, and the
         * failure to reach a peer which is down is reported once for the whole outage, so
         * a report which precedes the capture is a report which never arrives.
         */
        servers[0] = new ReplicationServer(new ReplServerFakeConfiguration(
            ports[0], "replicationServerConnectFailureDb", 0, RS_ID, 0, 100,
            newTreeSet(peerAddress.toString())));
        // The connect thread iterates the domains of this server, so it has nothing to
        // connect for, and nothing to keep a record for, until there is one.
        servers[0].getReplicationServerDomain(baseDN, true);

        waitForErrorLogRecord(live, connectErrorPrefix(peerAddress, baseDN), REPORT_TIMEOUT_MS);

        servers[1] = new ReplicationServer(new ReplServerFakeConfiguration(
            ports[1], "replicationServerConnectFailurePeerDb", 0, PEER_RS_ID, 0, 100,
            newTreeSet(HostPort.localAddress(ports[0]).toString())));
        servers[1].getReplicationServerDomain(baseDN, true);

        waitForErrorLogRecord(live, restored, REPORT_TIMEOUT_MS);
      });

      /*
       * The connect thread retries a peer which is down every second, and it stayed down
       * for as long as the wait above took: one line for the outage, one for its end.
       */
      assertThat(countRecordsOf(records, connectErrorPrefix(peerAddress, baseDN)))
          .as("a peer which stays down should be reported once, not on every retry")
          .isEqualTo(1);
      assertThat(countRecordsOf(records, restored))
          .as("the end of the outage should be reported once")
          .isEqualTo(1);
    }
    finally
    {
      remove(servers[1], servers[0]);
    }
  }

  /**
   * Tests that a peer which stops the handshake it is offered still closes the outage
   * reported for it.
   * <p>
   * The outage is a failure to connect, so what closes it is a connection:
   * {@code WARN_REPLICATION_SERVER_CONNECT_ERROR} is reported for the socket and for the
   * session built on it, the handshake throwing nothing of its own, and a peer which
   * answers on its replication port is a peer this server can reach whatever the handshake
   * does next.
   * <p>
   * Holding the outage open across an abort silences the peer this server never sees
   * connected under the address it dialled: one which dials out from another address has
   * its inbound handler registered under the source address of its own connection, so every
   * handshake this server offers it afterwards aborts on a duplicate server id and an open
   * session is never seen at the end of {@code connect()} again. Nothing would clear the
   * record of such a peer, and its next real outage would not be reported at all. Reading
   * the registration with the domain instead has the same shape one protocol version down,
   * a peer which negotiates V1 being connected and never registered.
   */
  @Test
  public void aPeerWhichStopsTheHandshakeStillClosesItsOutage() throws Exception
  {
    aPeerWhichAnswersClosesItsOutage("replicationServerHandshakeAbortDb",
        (session, received) -> {
          session.publish(new StopMsg());
          // Read until the peer has read the StopMsg and closed its own end: closing this
          // one first would race that read, and turn the abort into a failed handshake.
          session.receive();
        });
  }

  /**
   * Tests that a peer which answers under an address it was not dialled on still closes the
   * outage reported for it.
   * <p>
   * This is the multi homed peer, and the reason the recovery can be read neither from the
   * address nor from the registration. {@code ServerHandler.toServerAddressURL()} takes the
   * host of a handler from {@code session.getRemoteAddress()}, so a peer configured under
   * one address and dialling out from another is registered under the address it dialled
   * out from: the already connected branch of {@code runConnect()} compares the configured
   * address against one which never matches it, and the handshake this server offers that
   * same peer aborts on a duplicate server id instead of registering anything.
   * <p>
   * The peer here answers with a well formed {@code ReplServerStartMsg} naming another
   * port, which is that mismatch without a second address to bind, and then leaves the
   * second phase of the handshake unanswered.
   */
  @Test
  public void aPeerWhichAdvertisesAnotherAddressStillClosesItsOutage() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    // Never bound: what the configured address misses is the port the peer names in its
    // start message, and naming one which is not the port it answered on is enough.
    final HostPort advertised = HostPort.valueOf("127.0.0.1:" + TestCaseUtils.findFreePorts(1)[0]);
    aPeerWhichAnswersClosesItsOutage("replicationServerAdvertisedAddressDb",
        (session, received) -> {
          // A generation id of -1 leaves the one of the domain alone: a positive one would
          // be adopted by the handshake, which is a change this test is not about.
          session.publish(new ReplServerStartMsg(PEER_RS_ID, advertised.toString(), baseDN, 100,
              new ServerState(), -1, false, (byte) 1, 5000));
          if (!((ReplServerStartMsg) received).getSSLEncryption())
          {
            // Both ends leave the SSL session together, and what the start message this
            // server sent asked for is what it does itself: reading the next message on the
            // other stream would be reading a stream nothing is written to.
            session.stopEncryption();
          }
          // The topology message of the second phase, which this peer reads and does not
          // answer. The handshake ends on the session closed under it, which is the abort a
          // multi homed peer really ends on.
          session.receive();
        });
  }

  /**
   * Reports a peer which is down, has every handshake it is offered answered by the
   * provided answer, and asserts that the outage was reported once, closed once, and
   * reported again once the peer was gone -- the last of which is only reachable because
   * the first was closed.
   * <p>
   * {@code connect()} is driven from the test thread so that each pass is one the test
   * names, but the connect thread of the server drives it too, for the same peer. That is
   * harmless in both directions: while the record is held, every failure it adds is one the
   * record already holds; while the record is cleared, the peer is answering, so it has
   * nothing to add. The failure which opens each of the two windows blacklists the peer for
   * six passes as well, which is longer than the window lasts.
   *
   * @param dbName
   *          The changelog directory of the server under test, which is its own.
   * @param answer
   *          How the peer answers the handshake this server offers it.
   */
  private void aPeerWhichAnswersClosesItsOutage(String dbName, PeerHandshake answer) throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = HostPort.valueOf("127.0.0.1:" + ports[1]);
    final String restored = NOTE_REPLICATION_SERVER_CONNECT_RESTORED.get(RS_ID, peerAddress, baseDN).toString();

    final ReplicationServer[] servers = new ReplicationServer[1];
    final ExecutorService peerThread = Executors.newSingleThreadExecutor();
    try
    {
      final ReplSessionSecurity security = getReplSessionSecurity();
      final List<String> records = errorLogRecordsOf(live -> {
        servers[0] = new ReplicationServer(new ReplServerFakeConfiguration(
            ports[0], dbName, 0, RS_ID, 0, 100, newTreeSet(peerAddress.toString())));
        final ReplicationServer rs = servers[0];
        rs.getReplicationServerDomain(baseDN, true);
        /*
         * Wait for one whole pass of the connect thread before recording anything from
         * here. runConnect() snapshots the domains at the top of a pass and runs retainAll
         * against that snapshot at the bottom, so a pass which started before the domain
         * existed ends by clearing every record held for it -- including one this thread
         * would have made in between, which is the record the assertions below are about.
         */
        rs.waitConnections();

        assertThat(rs.connect(peerAddress, baseDN))
            .as("nothing listens on " + peerAddress).isFalse();
        waitForErrorLogRecord(live, connectErrorPrefix(peerAddress, baseDN), REPORT_TIMEOUT_MS);

        // The peer answers, and the handshake ends without a connection: connect() returns
        // without throwing, which is this peer being reachable again.
        final AtomicBoolean serving = new AtomicBoolean(true);
        try (ServerSocket peerSocket = bindPeerPort(ports[1]))
        {
          peerSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
          // Every connection is answered, not just the one below: the connect thread dials
          // the same peer, and a handshake left unanswered would time out rather than abort.
          peerThread.submit(() -> answerEveryHandshake(peerSocket, security, serving, answer));
          assertThat(rs.connect(peerAddress, baseDN))
              .as("a handshake which ends without a connection returns from connect() without"
                  + " throwing").isTrue();
          waitForErrorLogRecord(live, restored, REPORT_TIMEOUT_MS);
        }
        finally
        {
          serving.set(false);
        }

        // And is gone again, which is a new outage: the previous one was closed.
        assertThat(rs.connect(peerAddress, baseDN))
            .as("nothing listens on " + peerAddress).isFalse();
      });

      assertThat(countRecordsOf(records, restored))
          .as("a peer which answers on its replication port ends the outage reported for it,"
              + " and this peer registers with no domain: the already connected branch of"
              + " runConnect() can report nothing about it, so connect() is the only place"
              + " this can have come from")
          .isEqualTo(1);
      assertThat(countRecordsOf(records, connectErrorPrefix(peerAddress, baseDN)))
          .as("the outage before the peer answered and the one after it went away again are"
              + " two outages, and the second is only reported because the first was closed:"
              + " a record left behind for a peer which answered silences it for good")
          .isEqualTo(2);
    }
    finally
    {
      peerThread.shutdownNow();
      remove(servers[0]);
    }
  }

  /**
   * Returns what every report of a failure to connect to the provided peer for the
   * provided domain starts with, which is the message up to the cause: the cause differs
   * between a peer which refuses a connection and one which resets it.
   */
  private String connectErrorPrefix(HostPort peer, DN baseDN)
  {
    final String message = WARN_REPLICATION_SERVER_CONNECT_ERROR.get(RS_ID, peer, baseDN, "").toString();
    return message.substring(0, message.indexOf(baseDN.toString()) + baseDN.toString().length());
  }

  /** Binds the provided port the way {@code TestCaseUtils} binds the ones it hands out. */
  private ServerSocket bindPeerPort(int port) throws Exception
  {
    final ServerSocket socket = new ServerSocket();
    socket.setReuseAddress(true);
    socket.bind(new InetSocketAddress(port));
    return socket;
  }

  /**
   * Answers the {@code ReplServerStartMsg} of every handshake offered to the provided
   * socket with the provided answer, until that socket is closed.
   * <p>
   * Every connection is answered rather than only the one a test drives: the connect thread
   * of the server dials the same peer, and a handshake left unanswered would end on the
   * connection timeout rather than on the answer, which is a different failure and a far
   * slower one.
   */
  private void answerEveryHandshake(ServerSocket peerSocket, ReplSessionSecurity security,
      AtomicBoolean serving, PeerHandshake answer)
  {
    while (serving.get() && !peerSocket.isClosed())
    {
      Socket accepted = null;
      Session session = null;
      try
      {
        accepted = peerSocket.accept();
        accepted.setTcpNoDelay(true);
        session = security.createServerSession(accepted, SOCKET_TIMEOUT_MS);
        answer.answer(session, session.receive());
      }
      catch (Exception ignored)
      {
        // Every answer ends by reading what the peer sends next, or does not send, and what
        // ends that read is the peer closing its own end. What ends this loop is the socket
        // being closed under the accept() above.
      }
      finally
      {
        close(session);
        close(accepted);
      }
    }
  }

  /** How a fake peer answers the {@code ReplServerStartMsg} a handshake starts with. */
  @FunctionalInterface
  private interface PeerHandshake
  {
    /**
     * Answers the provided start message on the provided session, and returns when the
     * handshake is over: the session is closed under it afterwards.
     *
     * @param session
     *          The session the handshake is running on.
     * @param received
     *          The {@code ReplServerStartMsg} the server under test sent.
     * @throws Exception
     *           When the session ends, which every answer here ends by waiting for.
     */
    void answer(Session session, ReplicationMsg received) throws Exception;
  }
}
