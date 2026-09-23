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
import java.util.SortedSet;
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
 * {@code ReplicationServerHandler.toServerAddressURL()}, which this change removes, took
 * the host of a handler from {@code session.getRemoteAddress()} and its port from the start
 * message that handler received, so the address a peer was registered under was an artefact
 * of which interface its connection used, not an identity. Two things followed, and the
 * tests below drive both:
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
 * documentation addresses (RFC 5737): nothing answers at them, which is what the connect
 * thread of the server under test finds when it dials one -- a host stack sends that SYN to
 * its default route, and the connect fails, once per six passes -- a failed dial is
 * blacklisted for the five passes after it -- until the peer has registered.
 */
@SuppressWarnings("javadoc")
public class MultiHomedPeerTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /** How long the registration of the fake peer is waited for, in milliseconds. */
  private static final long REGISTRATION_TIMEOUT_MS = 30000;

  private static final int RS_ID = 8251;
  private static final int PEER_RS_ID = 8252;
  /** The server id of the second fake peer, which only the removal case registers. */
  private static final int SECOND_PEER_RS_ID = 8253;

  /** TEST-NET-1: the address the peer is configured under and answers on. */
  private static final byte[] PEER_ADDRESS = { (byte) 192, 0, 2, 1 };
  /** TEST-NET-2: the address of a second, genuinely different server. */
  private static final byte[] OTHER_ADDRESS = { (byte) 198, 51, 100, 1 };
  /**
   * A name under the .invalid top level domain (RFC 6761), which resolvers answer does not
   * exist: the host name of a peer which this server has no way to resolve, and what
   * {@code ReplicationServer.setServerURL()} falls back to naming.
   */
  private static final String UNRESOLVABLE_HOST = "nonexistent.invalid";

  /**
   * Tests that a peer which dialled this server from an address it is not configured under
   * is still found by the address it <i>is</i> configured under.
   * <p>
   * This is what stops the connect thread from dialling it on every pass: the address the
   * handler is registered under is the loopback address the peer's own connection came
   * from, and the only address which can match the configured one is the address the peer
   * named in its start message.
   * <p>
   * Both halves are asserted, because the predicate and the call site which reads it fail
   * apart: what the connect thread does with a peer it finds connected is reported by the
   * outage it closes, and an outage is recorded here for it to close.
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

      /*
       * An outage for the configured address, which nothing but the already connected
       * branch of runConnect() closes: a peer that branch skips is dialled by no one, so a
       * connection is reported for it nowhere else. Recorded from this thread rather than
       * waited for, because whether the connect thread has dialled that address before the
       * peer registers is a race and what is asserted below is not.
       */
      assertThat(rs.connect(peerAddress, baseDN))
          .as("nothing answers at " + peerAddress).isFalse();

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
      assertThat(waitForConnectRestored(rs, peerAddress, baseDN))
          .as("the connect thread must find the peer at its configured address and close the"
              + " outage reported for it, rather than dial it again")
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
   * Tests that a peer which names an address this configuration does not use is found by
   * the address its own connection came from, which is the only one known of it.
   * <p>
   * The peer of the first case names the address it is configured under, and is found by
   * it. This one is the other way round: it is configured at the address it dials this
   * server from, and names one this topology does not configure it at -- the host name of
   * its machine under another of its addresses, which {@code setServerURL()} falls back to.
   * The address it names matches nothing here, so the address its session came from is what
   * has to match.
   */
  @Test
  public void aPeerWhichNamesAnAddressThisConfigurationDoesNotUseIsFoundByTheOneItDialledFrom()
      throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = loopbackAt(ports[1]);
    final HostPort namedAddress = documentationAddress(OTHER_ADDRESS, ports[1]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerNamedDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], namedAddress, baseDN);
      final ReplicationServerHandler registered = waitForRegistration(domain);

      // The precondition of the case rather than an assumption, as in the first case: a
      // peer which names the address it is configured under is the peer of that one.
      assertThat(registered.getServerURL())
          .as("the peer should name the address this configuration does not use")
          .isEqualTo(namedAddress.toString());
      assertThat(registered.getServerAddressURL())
          .as("the peer should be registered under the loopback address it dialled from")
          .isEqualTo(peerAddress.toString());

      assertThat(domain.isConnectedToServerAt(peerAddress))
          .as("the address the connection of such a peer came from is the only one which"
              + " can match the address it is configured at")
          .isTrue();
    }
    finally
    {
      close(inbound);
      removeQuietly(rs);
    }
  }

  /**
   * Tests that a peer which names a host this server cannot resolve is recognised by that
   * name all the same, rather than reported as two servers sharing a server id.
   * <p>
   * {@code setServerURL()} falls back to the host name of the machine when none of the
   * addresses a peer is configured with is local to it, and the rest of the topology has no
   * reason to resolve that name. Both handlers of such a peer name it, so it is what tells
   * this server they are one -- but a comparison which resolves both sides answers that a
   * name it cannot resolve is equivalent to nothing at all, not even to itself, and the
   * handshake this server offers such a peer would abort on a duplicate server id on every
   * pass which reaches it.
   */
  @Test
  public void aPeerWhichNamesAnUnresolvableHostIsNotReportedAsADuplicateServerId()
      throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);
    final HostPort namedAddress = new HostPort(UNRESOLVABLE_HOST, ports[1]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerUnresolvedDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], namedAddress, baseDN);
      final ReplicationServerHandler registered = waitForRegistration(domain);

      /*
       * The premise of this case rather than an assumption about the network it runs on:
       * HostPort reports a name it cannot resolve each time it builds an address from one,
       * so those records are what says this machine answers that the name below does not
       * exist. A resolver which synthesises an address for a name which does not -- consumer
       * ISPs, captive portals, some corporate DNS -- would have isEquivalentTo() answer this
       * case on its own, which leaves the arm the case is here for unread, the count below
       * vacuous and nothing red to say so.
       */
      assertThat(recordsContaining(TestCaseUtils.ERROR_TEXT_WRITER.getMessages(),
          ERR_COULD_NOT_SOLVE_HOSTNAME.get(UNRESOLVABLE_HOST).toString()))
          .as("the resolver of this machine must answer that " + UNRESOLVABLE_HOST
              + " does not exist, which is the premise of this case")
          .isNotEmpty();

      /*
       * What the addresses are built from is read once, when the start message names them:
       * a name which cannot be resolved is reported by HostPort each time one is built from
       * it, and the connect thread compares them on every pass. The comparison below is the
       * one that thread makes.
       */
      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      for (int pass = 0; pass < 3; pass++)
      {
        domain.isConnectedToServerAt(peerAddress);
      }
      assertThat(recordsContaining(TestCaseUtils.ERROR_TEXT_WRITER.getMessages(),
          ERR_COULD_NOT_SOLVE_HOSTNAME.get(UNRESOLVABLE_HOST).toString()))
          .as("comparing the addresses of a handler must not build them again")
          .isEmpty();

      final List<String> records =
          errorLogRecordsOfHandshakeWith(rs, baseDN, peerAddress, namedAddress);

      assertThat(duplicateServerIdRecords(records, rs, loopbackAt(ports[1]), peerAddress))
          .as("a peer whose name this server cannot resolve is one server all the same, and"
              + " every attempt to reach it would log this again")
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
   * another address on the same port. The port is the same one on purpose: it is the host
   * which tells the two servers apart, and a second port would answer the comparison before
   * any host of it is read.
   */
  @Test
  public void twoServersSharingAServerIdAreStillReported() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);
    final HostPort otherAddress = documentationAddress(OTHER_ADDRESS, ports[1]);

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
   * Tests that a second session which comes from the address a peer is already connected on
   * is read as that peer, whatever that session names.
   * <p>
   * This is the other arm of the comparison the handshake makes, and what it gives up:
   * a session from the address a peer is connected on is that peer, so two replication
   * servers which share a server id and reach this one from behind a single gateway are read
   * as one and the second of them is dropped in silence. The base did the same with them --
   * it compared the addresses the two sockets carried, which for such a pair is one string
   * twice -- and this is the arm which recognises the peer of the case above, whose named
   * address matches nothing in this configuration.
   */
  @Test
  public void aSecondSessionFromTheAddressAPeerIsConnectedOnIsReadAsThatPeer() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort peerAddress = documentationAddress(PEER_ADDRESS, ports[1]);
    final HostPort otherAddress = documentationAddress(OTHER_ADDRESS, ports[1]);

    ReplicationServer rs = null;
    Session inbound = null;
    try
    {
      rs = startServerWithPeerConfiguredAt(ports[0], "multiHomedPeerGatewayDb", peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      inbound = registerPeerFrom(ports[0], peerAddress, baseDN);
      final ReplicationServerHandler registered = waitForRegistration(domain);

      /*
       * The handshake is answered from the loopback address the session of the registered
       * peer came from, and what answers it names an address neither handler carries
       * otherwise: the named addresses of the two differ, so the address the two sessions
       * have in common is the only thing which can match them.
       */
      final List<String> records =
          errorLogRecordsOfHandshakeWith(rs, baseDN, loopbackAt(ports[1]), otherAddress);

      assertThat(
          duplicateServerIdRecords(records, rs, loopbackAt(ports[1]), loopbackAt(ports[1])))
          .as("a session from the address a peer is connected on is that peer, whatever that"
              + " session names")
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
   * Tests that taking the entry of this server out of {@code ds-cfg-replication-server}
   * stops no peer, while taking the entry of a peer out still stops that peer.
   * <p>
   * {@code HostPort} normalises every address local to this machine to {@code localhost}, so
   * the entry of this server and the loopback address a peer names for itself are one
   * address on this port: a peer whose own configuration lists {@code localhost:P} for
   * itself names exactly that in its start messages -- {@code setServerURL()} takes the
   * first configured entry which is local to it -- and on this server that name normalises
   * to what its own entry does. The connect thread skips the entry of this server before it
   * dials; the road which disconnects removed replication servers has to skip it as well, or
   * removing it -- a configuration which does not list this server is supported, see
   * {@code runConnect()} -- costs such a peer its session, which it then has to dial again.
   * <p>
   * Both entries go in one change, so what tells the two peers apart is that guard alone:
   * the peer which names the entry of this server stays, the peer whose own entry was
   * removed goes. What the in JVM fixture cannot give is the fold itself, which needs one
   * port on two machines; what it names is the address of this server, which normalises the
   * same way.
   */
  @Test
  public void removingTheEntryOfThisServerStopsNoPeer() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final int[] ports = TestCaseUtils.findFreePorts(2);
    final HostPort ownAddress = loopbackAt(ports[0]);
    final HostPort peerAddress = loopbackAt(ports[1]);
    final String dbDirName = "multiHomedPeerOwnEntryDb";

    ReplicationServer rs = null;
    Session namesThisServer = null;
    Session namesItsOwnEntry = null;
    try
    {
      rs = startServerWithPeersConfiguredAt(ports[0], dbDirName, ownAddress, peerAddress);
      final ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN, true);
      namesThisServer = registerPeerFrom(ports[0], PEER_RS_ID, ownAddress, baseDN);
      final ReplicationServerHandler namedThisServer = waitForRegistration(domain, PEER_RS_ID);
      namesItsOwnEntry = registerPeerFrom(ports[0], SECOND_PEER_RS_ID, peerAddress, baseDN);
      waitForRegistration(domain, SECOND_PEER_RS_ID);

      // The precondition of the case rather than an assumption: a peer which does not answer
      // to the entry of this server is a peer this case says nothing about.
      assertThat(namedThisServer.isServerAt(HostPort.localAddress(ports[0])))
          .as("the peer which names a loopback address on the port of this server should"
              + " answer to the entry of this server")
          .isTrue();

      // Both entries removed at once, which is what an administrator emptying the list does.
      rs.applyConfigurationChange(configurationWithPeersAt(ports[0], dbDirName));

      assertThat(domain.getConnectedRSs().keySet())
          .as("a peer which names the address this server listens on must outlive the removal"
              + " of the entry of this server")
          .contains(PEER_RS_ID);
      assertThat(domain.getConnectedRSs().keySet())
          .as("the peer whose own entry was removed must still be disconnected")
          .doesNotContain(SECOND_PEER_RS_ID);
    }
    finally
    {
      close(namesThisServer);
      close(namesItsOwnEntry);
      removeQuietly(rs);
    }
  }

  /**
   * Starts a replication server whose only configured peer is at the provided address.
   * <p>
   * Nothing ever answers there -- the address is a documentation address -- which is what
   * the connect thread of the server does with it: it dials it, fails, and comes back to it
   * six passes later, the failed dial being blacklisted for the five in between. Whether it
   * dials it at all is what the first case is about.
   */
  private ReplicationServer startServerWithPeerConfiguredAt(int port, String dbDirName,
      HostPort peerAddress) throws Exception
  {
    return startServerWithPeersConfiguredAt(port, dbDirName, peerAddress);
  }

  /** Starts a replication server whose configured peers are the provided addresses. */
  private ReplicationServer startServerWithPeersConfiguredAt(int port, String dbDirName,
      HostPort... peerAddresses) throws Exception
  {
    return new ReplicationServer(configurationWithPeersAt(port, dbDirName, peerAddresses));
  }

  /**
   * Returns the configuration of the server under test, listing the provided addresses as
   * the replication servers of its topology.
   */
  private ReplServerFakeConfiguration configurationWithPeersAt(int port, String dbDirName,
      HostPort... peerAddresses)
  {
    final SortedSet<String> configured = newTreeSet();
    for (HostPort peerAddress : peerAddresses)
    {
      configured.add(peerAddress.toString());
    }
    return new ReplServerFakeConfiguration(port, dbDirName, 0, RS_ID, 0, 100, configured);
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
    return registerPeerFrom(port, PEER_RS_ID, registeredAs, baseDN);
  }

  /**
   * Has a fake peer of the provided server id dial the server under test, as
   * {@link #registerPeerFrom(int, HostPort, DN)} does for the peer of the cases which need
   * one peer only.
   *
   * @return the session the registration hangs on, closed by the caller
   */
  private Session registerPeerFrom(int port, int peerServerId, HostPort registeredAs, DN baseDN)
      throws Exception
  {
    final ReplSessionSecurity security = getReplSessionSecurity();
    final Socket socket = new Socket();
    Session session = null;
    try
    {
      socket.setTcpNoDelay(true);
      socket.connect(new InetSocketAddress("127.0.0.1", port), SOCKET_TIMEOUT_MS);
      session = security.createClientSession(socket, SOCKET_TIMEOUT_MS);
      session.publish(peerStartMsg(peerServerId, registeredAs, baseDN));
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
      final RSInfo peerInfo = new RSInfo(peerServerId, registeredAs.toString(), -1, (byte) 1, 1);
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
    return peerStartMsg(PEER_RS_ID, address, baseDN);
  }

  /** Returns the start message of a fake peer of the provided server id. */
  private ReplServerStartMsg peerStartMsg(int peerServerId, HostPort address, DN baseDN)
  {
    return new ReplServerStartMsg(peerServerId, address.toString(), baseDN, 100,
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
    return waitForRegistration(domain, PEER_RS_ID);
  }

  /**
   * Waits for the domain to hold the handler of the fake peer of the provided server id, as
   * {@link #waitForRegistration(ReplicationServerDomain)} does for the peer of the cases
   * which need one peer only.
   */
  private ReplicationServerHandler waitForRegistration(ReplicationServerDomain domain,
      int peerServerId) throws Exception
  {
    final long deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MS;
    ReplicationServerHandler registered;
    while (true)
    {
      registered = domain.getConnectedRSs().get(peerServerId);
      if (registered != null || System.currentTimeMillis() > deadline)
      {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(registered)
        .as("the fake peer of server id " + peerServerId + " should have registered with the"
            + " domain")
        .isNotNull();
    return registered;
  }

  /**
   * Waits for the connect thread to report the peer at the provided address connected,
   * driving a pass rather than waiting one out.
   * <p>
   * That report is what the already connected branch of {@code runConnect()} does with a
   * peer it finds registered, and the only thing this server does with one: the branch
   * which dials a peer reports a connection only for a handshake it completed, which the
   * documentation address of these tests never gives.
   */
  private boolean waitForConnectRestored(ReplicationServer rs, HostPort peer, DN baseDN)
      throws Exception
  {
    final String message =
        NOTE_REPLICATION_SERVER_CONNECT_RESTORED.get(RS_ID, peer, baseDN).toString();
    final long deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MS;
    while (true)
    {
      if (!recordsContaining(TestCaseUtils.ERROR_TEXT_WRITER.getMessages(), message).isEmpty())
      {
        return true;
      }
      if (System.currentTimeMillis() > deadline)
      {
        return false;
      }
      rs.waitConnections();
    }
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
    return recordsContaining(records, ERR_DUPLICATE_REPLICATION_SERVER_ID.get(
        rs.getMonitorInstanceName(), connectedAs, reachedAt, PEER_RS_ID).toString());
  }

  /** Returns the records of the provided log which carry the provided message. */
  private List<String> recordsContaining(List<String> records, String message)
  {
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
