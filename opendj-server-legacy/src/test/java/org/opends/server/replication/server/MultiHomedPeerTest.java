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
import static org.opends.server.util.StaticUtils.close;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

/**
 * Reproducer for issue #1017: a replication server which is reachable at more than one
 * address -- a multi homed host, or a NAT where the address a peer connects <i>from</i> is
 * not the address it is configured <i>as</i> -- used to be recognised by the address the
 * socket of one of its sessions happened to carry.
 * <p>
 * {@code ServerHandler.toServerAddressURL()} takes the host of a handler from
 * {@code session.getRemoteAddress()} and its port from the start message that handler
 * received, so the address a peer is registered under is an artefact of which interface its
 * connection used, not an identity. Two things followed, and the tests below drive both:
 * the already connected test of {@code runConnect()} compared a configured address against
 * one which never matches it, so the peer was dialled again on every pass, about once a
 * second, for as long as it stayed where it was; and the handshake offered to that same
 * peer ran into a handler holding its server id under another address URL, which is
 * {@code ERR_DUPLICATE_REPLICATION_SERVER_ID} -- an error logged with no throttle for a
 * topology which is merely multi homed.
 * <p>
 * The fixture is the mechanism itself rather than a stand in for it: the peer dials the
 * server under test over the loopback interface and names another address in its start
 * message, which is what a peer behind a NAT looks like to the server it dials, and the
 * session the server dials that other address on reports the address it dialled, which is
 * what reaching the same peer at its configured address gives. The addresses named are
 * documentation addresses (RFC 5737), so nothing the tests do can leave the machine.
 */
@SuppressWarnings("javadoc")
public class MultiHomedPeerTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /** How long the registration of the fake peer is waited for, in milliseconds. */
  private static final long REGISTRATION_TIMEOUT_MS = 30000;

  private static final int RS_ID = 8251;
  private static final int PEER_RS_ID = 8252;

  /** TEST-NET-1: the address the peer is configured under and answers on. */
  private static final byte[] PEER_ADDRESS = { (byte) 192, 0, 2, 1 };
  /** TEST-NET-2: the address of a second, genuinely different server. */
  private static final byte[] OTHER_ADDRESS = { (byte) 198, 51, 100, 1 };

  /**
   * Tests that a peer which dialled this server from an address it is not configured under
   * is still found by the address it <i>is</i> configured under.
   * <p>
   * This is what stops the connect thread from dialling it on every pass: the address the
   * handler is registered under is the loopback address the peer's own connection came
   * from, and the only address which can match the configured one is the address the peer
   * named in its start message.
   */
  @Test
  public void aPeerRegisteredUnderTheAddressItDialledFromIsFoundByItsConfiguredAddress()
      throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerSkipDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], peerAddress, baseDN);
      final ReplicationServerHandler registered = waitForRegistration(domain);

      // The precondition of the case rather than an assumption: a peer registered under the
      // address it is configured under is a peer this test says nothing about.
      assertThat(registered.getServerAddressURL())
          .as("the peer should be registered under the loopback address it dialled from")
          .isEqualTo(loopbackAt(ports[1]).toString());
      assertThat(registered.getServerURL())
          .as("the peer should name the address it is configured under in its start message")
          .isEqualTo(peerAddress.toString());

      assertThat(domain.isConnectedToServerAt(peerAddress))
          .as("the peer is connected, so its configured address must not be dialled again")
          .isTrue();
    }
    finally
    {
      close(inbound);
      removeQuietly(rs);
    }
  }

  /**
   * Tests that a second session with a peer already connected under another address is
   * dropped as the duplicate connection it is, and not reported as two servers sharing a
   * server id.
   * <p>
   * The server under test dials the peer at its configured address while holding the
   * session that same peer dialled it on. Both sessions are with one server, which names
   * one address in both of its start messages; only the addresses the two sockets carry
   * differ, which is what being reachable at more than one address means.
   */
  @Test
  public void aPeerReachedAtItsConfiguredAddressIsNotReportedAsADuplicateServerId()
      throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerDuplicateDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], peerAddress, baseDN);
      final ReplicationServerHandler registered = waitForRegistration(domain);

      final List<String> records =
          errorLogRecordsOfHandshakeWith(rs, baseDN, peerAddress, peerAddress);

      assertThat(duplicateServerIdRecords(records, rs, loopbackAt(ports[1]), peerAddress))
          .as("a peer reachable at more than one address is not two servers sharing a"
              + " server id, and every attempt to reach it would log this again")
          .isEmpty();
      assertThat(domain.getConnectedRSs().get(PEER_RS_ID))
          .as("the session the peer dialled must outlive the duplicate connection")
          .isSameAs(registered);
    }
    finally
    {
      close(inbound);
      removeQuietly(rs);
    }
  }

  /**
   * Tests that two genuinely different replication servers sharing a server id are still
   * reported, which is the misconfiguration the address comparison is there to catch.
   * <p>
   * Nothing is shared here: the connected peer names one address and dialled from the
   * loopback interface, and the server answering the handshake names, and is reached at,
   * another address altogether. The two are the same server only by their server id, which
   * is exactly what the message says.
   */
  @Test
  public void twoServersSharingAServerIdAreStillReported() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(3);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);
    final HostPort otherAddress = documentationAddress(OTHER_ADDRESS, ports[2]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerConflictDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], peerAddress, baseDN);
      waitForRegistration(domain);

      final List<String> records =
          errorLogRecordsOfHandshakeWith(rs, baseDN, otherAddress, otherAddress);

      assertThat(duplicateServerIdRecords(records, rs, loopbackAt(ports[1]), otherAddress))
          .as("two servers which share nothing but a server id must still be reported")
          .isNotEmpty();
    }
    finally
    {
      close(inbound);
      removeQuietly(rs);
    }
  }

  /**
   * Tests that a peer taken out of the configuration is disconnected even though the
   * session it is connected on came from another address.
   * <p>
   * {@code disconnectRemovedReplicationServers()} hands the addresses which were removed
   * from {@code ds-cfg-replication-server} to this domain, and a handler which does not
   * answer to any of them stays connected: the peer an administrator took out of the
   * topology keeps replicating with this server until one of the two is restarted.
   */
  @Test
  public void aPeerRemovedFromTheConfigurationIsDisconnectedByItsConfiguredAddress()
      throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerRemovedDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], peerAddress, baseDN);
      waitForRegistration(domain);

      domain.stopReplicationServers(Collections.singletonList(peerAddress));

      assertThat(domain.getConnectedRSs().keySet())
          .as("the peer removed from the configuration must be disconnected")
          .doesNotContain(PEER_RS_ID);
    }
    finally
    {
      close(inbound);
      removeQuietly(rs);
    }
  }

  /**
   * Starts a replication server whose only configured peer is at the provided address.
   * <p>
   * Nothing ever answers there -- the address is a documentation address -- which is what
   * the connect thread of the server does with it: it dials it, fails, and comes back to it
   * on the next pass. Whether it dials it at all is what the first case is about.
   */
  private ReplicationServer startServerWithPeerConfiguredAt(int port, String dbDirName,
      HostPort peerAddress) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        port, dbDirName, 0, RS_ID, 0, 100, newTreeSet(peerAddress.toString())));
  }

  /**
   * Has the fake peer dial the server under test over the loopback interface and complete
   * the handshake, which registers it under the address that connection came from while its
   * start message names the address it is configured under.
   *
   * @return the session the registration hangs on, closed by the caller
   */
  private Session registerPeerFrom(int port, HostPort registeredAs, DN baseDN) throws Exception
  {
    final ReplSessionSecurity security = getReplSessionSecurity();
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
   * Runs the handshake the server under test offers a peer it dials at the provided address
   * and returns the error log records that handshake wrote.
   * <p>
   * The session is dialled over the loopback interface and reports {@code dialledAt} as the
   * address it reaches the peer at, which is what dialling a peer at its configured address
   * gives whatever interface the peer's own connection to this server used. The answer is
   * a well formed start message naming {@code answersAs}: what ends the handshake is the
   * handler this server already holds for that server id, not what the peer answers here.
   *
   * @param listenPort
   *          the port the fake peer answers the handshake on
   * @param dialledAt
   *          the address the session reports the peer at
   * @param answersAs
   *          the address the peer names in the start message it answers with
   */
  private List<String> errorLogRecordsOfHandshakeWith(final ReplicationServer rs,
      final DN baseDN, final HostPort dialledAt, final HostPort answersAs)
      throws Exception
  {
    final ExecutorService peerThread = Executors.newSingleThreadExecutor();
    try (ServerSocket peerListen = TestCaseUtils.bindFreePort())
    {
      peerListen.setSoTimeout(SOCKET_TIMEOUT_MS);
      final ReplSessionSecurity security = getReplSessionSecurity();
      final int listenPort = peerListen.getLocalPort();

      final Future<Session> dialled = peerThread.submit(new Callable<Session>()
      {
        @Override
        public Session call() throws Exception
        {
          return dialPeerAt(security, dialledAt, listenPort);
        }
      });

      try (Session peerEnd =
          security.createServerSession(peerListen.accept(), SOCKET_TIMEOUT_MS);
          Session session = dialled.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
      {
        final Future<?> answer = peerThread.submit(new Callable<Void>()
        {
          @Override
          public Void call() throws Exception
          {
            peerEnd.receive();
            peerEnd.publish(peerStartMsg(answersAs, baseDN));
            return null;
          }
        });

        TestCaseUtils.ERROR_TEXT_WRITER.clear();
        new ReplicationServerHandler(session, 100, rs, 100).connect(baseDN, false);
        answer.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return TestCaseUtils.ERROR_TEXT_WRITER.getMessages();
      }
    }
    finally
    {
      peerThread.shutdownNow();
    }
  }

  /**
   * Connects to the provided local port with a socket which reports the provided address as
   * the one it is connected to, as dialling a peer at that address does.
   */
  private Session dialPeerAt(ReplSessionSecurity security, HostPort dialledAt, int port)
      throws Exception
  {
    // Named, so that the SSL socket factory asking the socket for its host name does not
    // send a reverse lookup of an address no name server knows anything about.
    final InetAddress reported = InetAddress.getByAddress("peer.example.com",
        InetAddress.getByName(dialledAt.getHost()).getAddress());
    final Socket socket = new Socket()
    {
      @Override
      public InetAddress getInetAddress()
      {
        return reported;
      }
    };
    try
    {
      socket.setTcpNoDelay(true);
      socket.connect(new InetSocketAddress("127.0.0.1", port), SOCKET_TIMEOUT_MS);
      return security.createClientSession(socket, SOCKET_TIMEOUT_MS);
    }
    catch (Exception e)
    {
      close(socket);
      throw e;
    }
  }

  /** Returns the start message of the fake peer, naming the provided address. */
  private ReplServerStartMsg peerStartMsg(HostPort address, DN baseDN)
  {
    return new ReplServerStartMsg(PEER_RS_ID, address.toString(), baseDN, 100,
        new ServerState(), -1, false, (byte) 1, 5000);
  }

  /** Returns an address of the documentation ranges, which nothing routes. */
  private HostPort documentationAddress(byte[] address, int port) throws Exception
  {
    return new HostPort(InetAddress.getByAddress(address).getHostAddress(), port);
  }

  /** Returns the loopback address a connection of these tests comes from. */
  private HostPort loopbackAt(int port)
  {
    return new HostPort("127.0.0.1", port);
  }

  /**
   * Waits for the domain to hold the handler of the fake peer, which the server registers
   * after it has sent the topology message the handshake above reads: the registration
   * lands just behind the thread which drove it.
   */
  private ReplicationServerHandler waitForRegistration(ReplicationServerDomain domain)
      throws Exception
  {
    final long deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MS;
    ReplicationServerHandler registered;
    while (true)
    {
      registered = domain.getConnectedRSs().get(PEER_RS_ID);
      if (registered != null || System.currentTimeMillis() > deadline)
      {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(registered).as("the fake peer should have registered with the domain").isNotNull();
    return registered;
  }

  /**
   * Returns the records of the provided log which report the two provided address URLs as
   * two replication servers sharing a server id.
   * <p>
   * The addresses are the ones the message names, so a record of the handshake next door
   * cannot be read as one of the handshake under test.
   */
  private List<String> duplicateServerIdRecords(List<String> records,
      ReplicationServer rs, HostPort connectedAs, HostPort reachedAt)
  {
    final String message = ERR_DUPLICATE_REPLICATION_SERVER_ID.get(
        rs.getMonitorInstanceName(), connectedAs, reachedAt, PEER_RS_ID).toString();
    final List<String> results = newArrayList();
    for (String record : records)
    {
      if (record.contains(message))
      {
        results.add(record);
      }
    }
    return results;
  }

  /** Teardown must never mask the primary assertion failure. */
  private void removeQuietly(ReplicationServer replicationServer)
  {
    try
    {
      if (replicationServer != null)
      {
        remove(replicationServer);
      }
    }
    catch (Exception ignored)
    {
    }
  }
}
