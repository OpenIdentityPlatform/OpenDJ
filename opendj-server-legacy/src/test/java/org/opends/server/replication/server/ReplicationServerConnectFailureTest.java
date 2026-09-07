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
import org.opends.server.replication.protocol.ReplSessionSecurity;
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
 * its own passes with either of them deleted, or with the two halves of the condition
 * which closes an outage in either order.
 * <p>
 * Both tests configure the peer they drive, and create the domain they drive it for, before
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
   * Tests that a handshake the peer aborts leaves the reported outage open.
   * <p>
   * {@code ReplicationServerHandler.connect()} aborts a handshake it cannot complete by
   * closing the session and returning, without throwing, so {@code connect()} returns
   * {@code true} for a peer it did not connect to. Clearing the record of the outage there
   * would report it open and never report it closed: the connection which really ends it
   * reaches the already connected branch of {@code runConnect()} a pass later, and finds
   * nothing left to report.
   * <p>
   * {@code connect()} is driven from here so that each pass is one the test names, but the
   * connect thread of the server drives it too, for the same peer. That is harmless in both
   * directions: every failure it adds is one the record already holds, and the peer here
   * never completes a handshake, so it can register nothing for either of them to report a
   * recovery from.
   */
  @Test
  public void aHandshakeThePeerAbortsLeavesTheOutageOpen() throws Exception
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
            ports[0], "replicationServerHandshakeAbortDb", 0, RS_ID, 0, 100,
            newTreeSet(peerAddress.toString())));
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

        // The peer answers, and stops the handshake: connect() returns without connecting.
        final AtomicBoolean serving = new AtomicBoolean(true);
        try (ServerSocket peerSocket = bindPeerPort(ports[1]))
        {
          peerSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
          // Every connection is answered, not just the one below: the connect thread dials
          // the same peer, and a handshake left unanswered would time out rather than abort.
          peerThread.submit(() -> stopEveryHandshake(peerSocket, security, serving));
          assertThat(rs.connect(peerAddress, baseDN))
              .as("a handshake the peer stops returns from connect() without throwing").isTrue();
        }
        finally
        {
          serving.set(false);
        }

        // And is gone again, which is the same outage as the one already reported.
        assertThat(rs.connect(peerAddress, baseDN))
            .as("nothing listens on " + peerAddress).isFalse();
      });

      assertThat(countRecordsOf(records, connectErrorPrefix(peerAddress, baseDN)))
          .as("the outage was reported before the peer stopped the handshake, and stopping a"
              + " handshake is not connecting: reporting it again means the record of the outage"
              + " was cleared, and the connection which ends it will find nothing to report")
          .isEqualTo(1);
      assertThat(countRecordsOf(records, restored))
          .as("no connection was established, so no recovery is to be reported")
          .isZero();
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
   * socket with a {@code StopMsg}, which is what a replication server which is shutting
   * down, or which detected a simultaneous cross connect, answers.
   */
  private void stopEveryHandshake(ServerSocket peerSocket, ReplSessionSecurity security, AtomicBoolean serving)
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
        session.receive();
        session.publish(new StopMsg());
        // Read until the peer has read the StopMsg and closed its own end: closing this
        // one first would race that read, and turn the abort into a failed handshake.
        session.receive();
      }
      catch (Exception ignored)
      {
        // The peer closes the session as soon as it has read the StopMsg, which is what
        // the receive() above is waiting for, and the socket is closed to end this loop.
      }
      finally
      {
        close(session);
        close(accepted);
      }
    }
  }
}
