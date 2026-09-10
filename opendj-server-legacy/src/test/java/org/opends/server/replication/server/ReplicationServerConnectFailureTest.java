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
import static org.opends.server.util.CollectionUtils.newArrayList;
import static org.opends.server.util.CollectionUtils.newTreeSet;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StopMsg;
import org.opends.server.replication.protocol.TopologyMsg;
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
 * The tests which drive a peer of their own drive one the already connected branch of
 * {@code runConnect()} cannot report anything about, so that {@code connect()} is the only
 * place a recovery can come from: a peer registered with no domain at all, and one
 * registered under an address which is not the one it is configured under. Both shapes end
 * on a session {@code abortStart} closed and on a peer which is connected for no domain,
 * which is what makes them the outages this server has to close without reading either --
 * and, being aborts, the recoveries it must not report as connections.
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
   * Tests that a peer which stops the handshake it is offered closes the outage reported
   * for it, and is reported reachable without a session rather than connected.
   * <p>
   * The outage is a failure to connect, so what closes it is the peer answering:
   * {@code WARN_REPLICATION_SERVER_CONNECT_ERROR} is reported for the socket and for the
   * session built on it, the handshake throwing nothing of its own. Holding the outage open
   * across an abort silences the peer this server never sees connected under the address it
   * dialled -- the multi homed peer of
   * {@link #aPeerRegisteredUnderAnotherAddressStillClosesItsOutage}, and one protocol
   * version down a peer which negotiates V1, connected and never registered.
   * <p>
   * Reporting it connected instead is the other half of the same line.
   * {@code Session.close()} publishes a {@code StopMsg} for every abort its own end makes
   * and {@code abortStart(null)} logs nothing, so a peer which rejects this server -- its
   * own duplicate server id, a cross connect it resolves against this server, a shutdown
   * under way -- would have "connected" as the last thing this server ever says about it.
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
   * Tests that a peer already registered under an address other than the one it is
   * configured under still closes the outage reported for it.
   * <p>
   * This is the multi homed peer, and the reason the recovery can be read neither from the
   * address nor from the session. {@code ServerHandler.toServerAddressURL()} takes the host
   * of a handler from {@code session.getRemoteAddress()} and its port from the start message
   * that handler received, so a peer which dials this server from an address it is not
   * configured under is registered under that other address. Two things follow, and this
   * test drives both: the already connected branch of {@code runConnect()} compares the
   * configured address against one which never matches it, so it can close nothing; and the
   * handshake this server offers that same peer runs into a handler holding its server id
   * under another address URL, which is {@code ERR_DUPLICATE_REPLICATION_SERVER_ID}, an
   * abort of this server rather than of the peer, and a session closed at the end of
   * {@code connect()} for as long as the peer stays where it is.
   * <p>
   * Gating the recovery on either leaves the record of such a peer uncleared for good, and
   * {@code recordFailure()} returns false from then on: the next real outage of it -- the
   * second one counted below -- is not reported at all.
   */
  @Test
  public void aPeerRegisteredUnderAnotherAddressStillClosesItsOutage() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    /*
     * Three ports: the server under test, the address the peer is configured under and
     * answers on, and the address it registers itself under. The last is never bound --
     * what a multi homed peer costs is that the two addresses are not compared equal, and
     * a port nothing listens on is that, without a second address to bind.
     */
    final int[] ports = TestCaseUtils.findFreePorts(3);
    final HostPort peerAddress = HostPort.valueOf("127.0.0.1:" + ports[1]);
    final HostPort registeredAs = HostPort.valueOf("127.0.0.1:" + ports[2]);
    final String reachable =
        WARN_REPLICATION_SERVER_REACHABLE_NO_SESSION.get(RS_ID, peerAddress, baseDN).toString();
    final String connected =
        NOTE_REPLICATION_SERVER_CONNECT_RESTORED.get(RS_ID, peerAddress, baseDN).toString();

    final ReplicationServer[] servers = new ReplicationServer[1];
    // Held rather than returned: the registration lasts as long as the session does, and
    // whatever the capture below throws, this is what closes it.
    final Session[] inbound = new Session[1];
    final ExecutorService peerThread = Executors.newSingleThreadExecutor();
    try
    {
      final ReplSessionSecurity security = getReplSessionSecurity();
      final List<String> records = errorLogRecordsOf(live -> {
        servers[0] = new ReplicationServer(new ReplServerFakeConfiguration(
            ports[0], "replicationServerRegisteredAddressDb", 0, RS_ID, 0, 100,
            newTreeSet(peerAddress.toString())));
        final ReplicationServer rs = servers[0];
        rs.getReplicationServerDomain(baseDN, true);
        // The pass a record made from this thread has to outlive, as in
        // aPeerWhichAnswersClosesItsOutage.
        rs.waitConnections();

        assertThat(rs.connect(peerAddress, baseDN))
            .as("nothing listens on " + peerAddress).isFalse();
        waitForErrorLogRecord(live, connectErrorPrefix(peerAddress, baseDN), REPORT_TIMEOUT_MS);

        /*
         * The peer dials this server and completes the handshake from its own side, which
         * is what registers it -- under the address its start message names, not the one it
         * is configured under. Registered before it answers on the configured address, so
         * that every handshake this server offers it from then on, the ones its connect
         * thread offers included, runs into that registration: an attempt which got in
         * before it would abort on the handshake instead, which is the other test.
         */
        inbound[0] = registerPeerFrom(security, ports[0], registeredAs, baseDN);
        /*
         * Waited for rather than assumed: the server registers the handler after it has
         * sent the topology message the handshake above reads, so the registration lands
         * just behind this thread -- and a handshake which ends any other way registers
         * nothing at all, which would leave this case asserting the abort of the other one.
         */
        waitForRegistrationUnder(rs, baseDN, registeredAs, REPORT_TIMEOUT_MS);

        final AtomicBoolean serving = new AtomicBoolean(true);
        try (ServerSocket peerSocket = bindPeerPort(ports[1]))
        {
          peerSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
          // The peer answers every handshake with a well formed start message naming the
          // address it answers on: what aborts the handshake is the handler it already has
          // with this server, not what it answers here.
          peerThread.submit(() -> answerEveryHandshake(peerSocket, security, serving,
              (session, received) -> {
                session.publish(peerStartMsg(peerAddress, baseDN));
                stopEncryptionWith(session, received);
                session.receive();
              }));

          assertThat(rs.connect(peerAddress, baseDN))
              .as("the domain holds a handler for this server id under another address URL,"
                  + " so the handshake aborts on a duplicate server id instead of connecting")
              .isFalse();
          waitForErrorLogRecord(live, reachable, REPORT_TIMEOUT_MS);
        }
        finally
        {
          serving.set(false);
        }

        // And is gone again, which is a new outage: the previous one was closed.
        assertThat(rs.connect(peerAddress, baseDN))
            .as("nothing listens on " + peerAddress).isFalse();
      });

      assertThat(countRecordsOf(records, reachable))
          .as("the peer answered on the address it is configured under, which ends the outage"
              + " reported for it however it is registered with this server")
          .isEqualTo(1);
      assertThat(countRecordsOf(records, connected))
          .as("the handshake aborted on a duplicate server id, so this server has no session"
              + " with the peer over the address it dialled")
          .isEqualTo(0);
      assertThat(countRecordsOf(records, connectErrorPrefix(peerAddress, baseDN)))
          .as("the outage before the peer answered and the one after it went away again are"
              + " two outages, and the second is only reported because the first was closed:"
              + " a record left behind for a peer registered elsewhere silences it for good")
          .isEqualTo(2);
    }
    finally
    {
      close(inbound[0]);
      peerThread.shutdownNow();
      remove(servers[0]);
    }
  }

  /**
   * Connects to the replication port of the server under test and completes, from the side
   * of a peer replication server, the handshake which registers that peer with the domain.
   * <p>
   * The registration is what this is for, and the handshake has to reach its end to get it:
   * {@code ReplicationServerHandler.startFromRemoteRS()} registers the handler after the
   * second phase, so the topology message of that phase has to be sent and read back.
   *
   * @param security
   *          The session security to build the client session with.
   * @param port
   *          The replication port of the server under test.
   * @param registeredAs
   *          The address this peer names in its start message, and is therefore registered
   *          under, whatever address it dialled from.
   * @param baseDN
   *          The base DN of the domain to register with.
   * @return The session the registration hangs on, closed by the caller.
   */
  private Session registerPeerFrom(ReplSessionSecurity security, int port, HostPort registeredAs, DN baseDN)
      throws Exception
  {
    final Socket socket = new Socket();
    Session session = null;
    try
    {
      socket.setTcpNoDelay(true);
      socket.connect(new InetSocketAddress("127.0.0.1", port), SOCKET_TIMEOUT_MS);
      session = security.createClientSession(socket, SOCKET_TIMEOUT_MS);
      session.publish(peerStartMsg(registeredAs, baseDN));
      session.receive();
      // The initiator of a session decides whether it is encrypted, and the start message
      // above asked for it not to be: both ends leave the SSL session together, right after
      // the start messages have been exchanged.
      session.stopEncryption();
      /*
       * The second phase: the server reads this one before it sends its own, and registers
       * the handler once it has sent it. The list holds this peer and nothing else --
       * waitAndProcessTopoFromRemoteRS() reads rsInfos.get(0) above protocol version 4, so
       * an empty one ends the handshake on an IndexOutOfBoundsException instead, which is
       * an abort like any other and would leave the peer unregistered.
       */
      final RSInfo peerInfo = new RSInfo(PEER_RS_ID, registeredAs.toString(), -1, (byte) 1, 1);
      session.publish(new TopologyMsg(Collections.<DSInfo> emptyList(), newArrayList(peerInfo)));
      session.receive();
      return session;
    }
    catch (Exception e)
    {
      close(session);
      close(socket);
      throw e;
    }
  }

  /**
   * Waits for the domain of the provided server to hold a handler for the fake peer under
   * the provided address URL.
   * <p>
   * That handler is what makes the outbound handshake of the case reach
   * {@code ERR_DUPLICATE_REPLICATION_SERVER_ID}: the server ids match and the address URLs
   * do not. Without it the handshake ends on the second phase instead, which is an abort as
   * well and reports the same message -- the case would pass while pinning the wrong path.
   *
   * @param rs
   *          The server under test.
   * @param baseDN
   *          The base DN of the domain the peer registered with.
   * @param registeredAs
   *          The address URL the peer is expected to be registered under.
   * @param timeoutMs
   *          How long to wait for the registration, in milliseconds.
   */
  private void waitForRegistrationUnder(ReplicationServer rs, DN baseDN, HostPort registeredAs, long timeoutMs)
      throws Exception
  {
    final long deadline = System.currentTimeMillis() + timeoutMs;
    ReplicationServerHandler registered;
    while (true)
    {
      registered = rs.getReplicationServerDomain(baseDN).getConnectedRSs().get(PEER_RS_ID);
      if (registered != null || System.currentTimeMillis() > deadline)
      {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(registered).as("the peer should have registered with the domain").isNotNull();
    assertThat(registered.getServerAddressURL())
        .as("the peer should be registered under the address its start message named, which is"
            + " not the one it is configured under")
        .isEqualTo(registeredAs.toString());
  }

  /**
   * Returns the start message of a fake peer, naming the provided address.
   * <p>
   * A generation id of -1 leaves the one of the domain alone: a positive one would be
   * adopted by the handshake, which is a change none of these tests is about.
   */
  private ReplServerStartMsg peerStartMsg(HostPort address, DN baseDN)
  {
    return new ReplServerStartMsg(PEER_RS_ID, address.toString(), baseDN, 100,
        new ServerState(), -1, false, (byte) 1, 5000);
  }

  /**
   * Leaves the SSL session when the sender of the provided start message does.
   * <p>
   * Both ends leave it together, and what a start message asks for is what its sender does
   * itself: reading the next message on the other stream would be reading a stream nothing
   * is written to.
   */
  private void stopEncryptionWith(Session session, ReplicationMsg startMsg) throws Exception
  {
    if (!((ReplServerStartMsg) startMsg).getSSLEncryption())
    {
      session.stopEncryption();
    }
  }

  /**
   * Reports a peer which is down, has every handshake it is offered answered by the
   * provided answer, and asserts that the outage was reported once, closed once, and
   * reported again once the peer was gone -- the last of which is only reachable because
   * the first was closed.
   * <p>
   * {@code connect()} is driven from the test thread so that each pass is one the test
   * names, but the connect thread of the server drives it too, for the same peer. What
   * makes the counts exact is not that the other thread is kept out of them: it is that
   * {@link ReplicationServer.ConnectFailureReporter} is idempotent in both directions, so
   * while the record is held every failure the connect thread adds is one the record
   * already holds, and while it is cleared every recovery it reports is one already
   * reported. The pass waited for below is what the counts do need, so that the
   * {@code retainAll} at the end of a pass which started without the domain cannot erase
   * the record they are about. The blacklist {@code runConnect()} keeps is local to it and
   * written only from its own failures, so the calls made here do not feed it.
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
    final String reachable =
        WARN_REPLICATION_SERVER_REACHABLE_NO_SESSION.get(RS_ID, peerAddress, baseDN).toString();
    final String connected = NOTE_REPLICATION_SERVER_CONNECT_RESTORED.get(RS_ID, peerAddress, baseDN).toString();

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

        // The peer answers, and the handshake ends without a connection: the outage is over
        // and there is no session, which are two things rather than one.
        final AtomicBoolean serving = new AtomicBoolean(true);
        try (ServerSocket peerSocket = bindPeerPort(ports[1]))
        {
          peerSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
          // Every connection is answered, not just the one below: the connect thread dials
          // the same peer, and a handshake left unanswered would time out rather than abort.
          peerThread.submit(() -> answerEveryHandshake(peerSocket, security, serving, answer));
          assertThat(rs.connect(peerAddress, baseDN))
              .as("a handshake which ends without a connection is not a connection, and a peer"
                  + " which answers with an abort every second is left alone for a few passes"
                  + " like one which does not answer at all").isFalse();
          waitForErrorLogRecord(live, reachable, REPORT_TIMEOUT_MS);
        }
        finally
        {
          serving.set(false);
        }

        // And is gone again, which is a new outage: the previous one was closed.
        assertThat(rs.connect(peerAddress, baseDN))
            .as("nothing listens on " + peerAddress).isFalse();
      });

      assertThat(countRecordsOf(records, reachable))
          .as("a peer which answers on its replication port ends the outage reported for it,"
              + " and this peer registers with no domain: the already connected branch of"
              + " runConnect() can report nothing about it, so connect() is the only place"
              + " this can have come from")
          .isEqualTo(1);
      assertThat(countRecordsOf(records, connected))
          .as("this peer has no replication session with this server, and reporting it"
              + " connected would leave that as the last thing said about a domain which"
              + " replicates nothing over it")
          .isEqualTo(0);
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
