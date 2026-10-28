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
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

import org.forgerock.i18n.LocalizableMessage;
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
 * up on its own timeout logs that timeout at WARNING itself). Both are warnings now, in
 * either phase of the handshake, and so is a peer which stays silent past the connection
 * timeout: this server gives up on it as it does on a peer which went away.
 * <p>
 * What this server rejects, a peer which breaks the protocol, a frame which does not decode
 * and a frame too large to allocate are still errors. Each call site picks the severity of its
 * own abort, so a case pins only the one it reaches: the cases which say so reach every
 * {@code abortStart} of a reason in the handshakes of a replication server and of a data
 * server but two, so that logging an abort as a warning is not a way to pass them. The two are
 * the {@code catch (Exception)} of {@code startFromRemoteRS()}, which a peer reaches only
 * through a failure of the JDK (a topology without the replication server which sent it fails
 * on {@code get(0)}), and the {@code catch (DirectoryException)} of
 * {@code startFromRemoteDS()}, which no peer reaches with a message.
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
  /** The connection timeout of a handshake whose peer stays silent: nothing comes before it. */
  private static final int SILENT_PEER_TIMEOUT_MS = 500;
  /** Longer than the longest wait of {@code lockDomainWithTimeout()}, which is 8 seconds. */
  private static final long LOCK_TIMEOUT_MS = 30000;
  private static final int REMOTE_DS_ID = 61;
  private static final int REMOTE_RS_ID = 62;
  private static final String REMOTE_RS_URL = "127.0.0.1:8989";
  /** What {@code Session.receive()} reports of the frame {@link #sendTooLargeFrameHeader} announces. */
  private static final LocalizableMessage PACKET_TOO_LARGE = LocalizableMessage.raw(
      "Packet too large, can't allocate " + Integer.MAX_VALUE + " bytes.");

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
    runCase("handshakeAbortSeverityCrossDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);
      final ReplServerStartMsg startMsg =
          (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      // The lock is held by a thread of its own, as the connect thread holds it: the
      // domain lock is reentrant, so the thread of the handshake would take it.
      final ExecutorService lockHolder = Executors.newSingleThreadExecutor();
      final List<String> records;
      try
      {
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
      }
      finally
      {
        lockHolder.shutdownNow();
      }

      assertThat(recordOf(records, WARN_TIMEOUT_WHEN_CROSS_CONNECTION.get(
              baseDN, REMOTE_RS_ID, session.getReadableRemoteAddress(), rsId)))
          .as("giving way to a cross connection is a warning, as its message says")
          .contains("severity=WARNING");
      assertThat(countRecordsOf(records,
              WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_RS_ID, rsId).toString()))
          .as("the handshake ends where this server gives way to the cross connection")
          .isZero();
    });
  }

  @Test
  public void aDataServerGoneDuringTheHandshakeIsAWarning() throws Exception
  {
    final int rsId = 8262;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityDSDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ServerStartMsg startMsg =
          (ServerStartMsg) sendThrough(remoteEnd, session, serverStartMsg(baseDN));
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
      assertAbortedHandshakeWentNoFurther(session);
    });
  }

  /**
   * A peer which stays connected and says nothing past the connection timeout is given up on
   * as one which went away.
   */
  @Test
  public void aDataServerSilentDuringTheHandshakeIsAWarning() throws Exception
  {
    final int rsId = 8274;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeveritySilentDSDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ServerStartMsg startMsg =
          (ServerStartMsg) sendThrough(remoteEnd, session, serverStartMsg(baseDN));
      session.setSoTimeout(SILENT_PEER_TIMEOUT_MS);
      final DataServerHandler handler =
          new DataServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        handler.startFromRemoteDS(startMsg);
        return null;
      });

      assertThat(recordOf(records, WARN_DS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_DS_ID, rsId)))
          .as("a directory server silent past the connection timeout is a warning")
          .contains("severity=WARNING");
      assertAbortedHandshakeWentNoFurther(session);
    });
  }

  /**
   * A directory server which sends something else where its start session message is due
   * breaks the protocol, and that is still an error.
   */
  @Test
  public void aDataServerWhichSendsNoStartSessionIsAnError() throws Exception
  {
    final int rsId = 8275;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityNoStartSessionDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ServerStartMsg startMsg =
          (ServerStartMsg) sendThrough(remoteEnd, session, serverStartMsg(baseDN));
      // Published ahead of the start message it answers: the session reads it all the same.
      remoteEnd.publish(serverStartMsg(baseDN));
      final DataServerHandler handler =
          new DataServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        handler.startFromRemoteDS(startMsg);
        return null;
      });

      assertThat(recordOf(records, LocalizableMessage.raw("Protocol error: StartSessionMsg required.")))
          .as("a directory server which breaks the protocol is an error")
          .contains("severity=ERROR");
      assertAbortedHandshakeWentNoFurther(session);
    });
  }

  @Test
  public void aReplicationServerGoneDuringTheHandshakeItStartedIsAWarning() throws Exception
  {
    final int rsId = 8263;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityFromRSDb", rsId, (replicationServer, remoteEnd, session) -> {
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
    });
  }

  @Test
  public void aReplicationServerGoneDuringTheHandshakeThisServerStartedIsAWarning() throws Exception
  {
    final int rsId = 8264;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityToRSDb", rsId, (replicationServer, remoteEnd, session) -> {
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
    });
  }

  /**
   * What the connect thread of a cross connection reads, when the listen thread of its peer
   * waits for the domain lock longer than the connection timeout: up to 8 seconds against 5 by
   * default.
   */
  @Test
  public void aReplicationServerSilentDuringTheHandshakeThisServerStartedIsAWarning()
      throws Exception
  {
    final int rsId = 8276;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeveritySilentToRSDb", rsId, (replicationServer, remoteEnd, session) -> {
      session.setSoTimeout(SILENT_PEER_TIMEOUT_MS);
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        assertFalse(handler.connect(baseDN, true), "the peer did not answer, so this is no session");
        return null;
      });

      assertThat(recordOf(records,
              WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(rsId, session.getReadableRemoteAddress())))
          .as("a replication server silent past the connection timeout is a warning")
          .contains("severity=WARNING");
    });
  }

  /**
   * The second phase of the handshake reads the topology of the peer through a method of its
   * own, which used to report every failure of the session as the error of a raw message.
   */
  @Test
  public void aReplicationServerGoneBeforeItsTopologyInTheHandshakeItStartedIsAWarning()
      throws Exception
  {
    final int rsId = 8266;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityFromRSTopoDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ReplServerStartMsg startMsg =
          (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        // Gone once it has read the start message of this server: that is sent, and the
        // handshake waits for the topology of the peer.
        goneAfterReading(1, remoteEnd, () -> {
          handler.startFromRemoteRS(startMsg);
          return null;
        });
        return null;
      });

      assertThat(recordOf(records, WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_RS_ID, rsId)))
          .as("a replication server which went away before it sent its topology is a warning")
          .contains("severity=WARNING");
    });
  }

  /**
   * The case above for a peer which stays silent: the second phase used to log the timeout
   * as the error of the raw "Read timed out".
   */
  @Test
  public void aReplicationServerSilentBeforeItsTopologyInTheHandshakeItStartedIsAWarning()
      throws Exception
  {
    final int rsId = 8277;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeveritySilentFromRSTopoDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ReplServerStartMsg startMsg =
          (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
      session.setSoTimeout(SILENT_PEER_TIMEOUT_MS);
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        handler.startFromRemoteRS(startMsg);
        return null;
      });

      assertThat(recordOf(records, WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_RS_ID, rsId)))
          .as("a replication server silent past the connection timeout before it sent its topology"
              + " is a warning")
          .contains("severity=WARNING");
    });
  }

  @Test
  public void aReplicationServerGoneBeforeItsTopologyInTheHandshakeThisServerStartedIsAWarning()
      throws Exception
  {
    final int rsId = 8267;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityToRSTopoDb", rsId, (replicationServer, remoteEnd, session) -> {
      // Published ahead of the start message it answers: the session reads it all the same.
      remoteEnd.publish(replServerStartMsg(baseDN));
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        // Gone once it has read the start message and the topology of this server: both
        // are sent, and the handshake waits for the topology of the peer.
        assertFalse(goneAfterReading(2, remoteEnd, () -> handler.connect(baseDN, true)),
            "the peer went away, so this is no session");
        return null;
      });

      assertThat(recordOf(records,
              WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(rsId, session.getReadableRemoteAddress())))
          .as("a replication server which went away before it sent its topology is a warning")
          .contains("severity=WARNING");
    });
  }

  /**
   * A peer which answers the start message of this server with something else breaks the
   * protocol, and that is still an error: of the aborts, only the peer going away and the
   * cross connection are warnings.
   */
  @Test
  public void aPeerWhichBreaksTheProtocolIsStillAnError() throws Exception
  {
    final int rsId = 8265;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityProtocolDb", rsId, (replicationServer, remoteEnd, session) -> {
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
    });
  }

  /**
   * The case above in the second phase, which is aborted from the {@code DirectoryException}
   * arm of the handshake: a peer which sends its start message again where its topology is
   * due.
   */
  @Test
  public void aPeerWhichSendsNoTopologyInTheHandshakeItStartedIsAnError() throws Exception
  {
    final int rsId = 8268;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityFromRSNoTopoDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ReplServerStartMsg startMsg =
          (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
      remoteEnd.publish(replServerStartMsg(baseDN));
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        handler.startFromRemoteRS(startMsg);
        return null;
      });

      assertThat(recordOf(records, ERR_REPLICATION_PROTOCOL_MESSAGE_TYPE.get(
              ReplServerStartMsg.class.getCanonicalName(), "TopologyMsg")))
          .as("a peer which breaks the protocol is an error")
          .contains("severity=ERROR");
    });
  }

  @Test
  public void aPeerWhichSendsNoTopologyInTheHandshakeThisServerStartedIsAnError() throws Exception
  {
    final int rsId = 8269;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityToRSNoTopoDb", rsId, (replicationServer, remoteEnd, session) -> {
      // Published ahead of the messages they answer: the session reads them all the same.
      remoteEnd.publish(replServerStartMsg(baseDN));
      remoteEnd.publish(replServerStartMsg(baseDN));
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        assertFalse(handler.connect(baseDN, true), "the peer broke the protocol, so this is no session");
        return null;
      });

      assertThat(recordOf(records, ERR_REPLICATION_PROTOCOL_MESSAGE_TYPE.get(
              ReplServerStartMsg.class.getCanonicalName(), "TopologyMsg")))
          .as("a peer which breaks the protocol is an error")
          .contains("severity=ERROR");
    });
  }

  /**
   * A rejection by this server: a peer which names the server id of a replication server the
   * domain already holds a session with, at another address.
   */
  @Test
  public void aServerIdAnotherReplicationServerHoldsIsAnError() throws Exception
  {
    final int rsId = 8270;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityDuplicateDb", rsId, (replicationServer, firstEnd, first) -> {
      // The first peer completes its handshake, so that the domain holds it: its topology is
      // published ahead of the start message of this server it answers.
      final ReplServerStartMsg firstStartMsg =
          (ReplServerStartMsg) sendThrough(firstEnd, first, replServerStartMsg(baseDN));
      firstEnd.publish(new TopologyMsg(Collections.<DSInfo> emptyList(),
          Collections.singletonList(new RSInfo(REMOTE_RS_ID, REMOTE_RS_URL, -1, (byte) 1, 1))));
      final ReplicationServerHandler registered =
          new ReplicationServerHandler(first, 100, replicationServer, 100);
      registered.startFromRemoteRS(firstStartMsg);
      // The precondition of the case rather than an assumption.
      assertThat(replicationServer.getReplicationServerDomain(baseDN).getConnectedRSs().get(REMOTE_RS_ID))
          .as("the first peer should hold its session")
          .isSameAs(registered);

      try (ServerSocket listen = TestCaseUtils.bindFreePort())
      {
        listen.setSoTimeout(SOCKET_TIMEOUT_MS);
        final Session[] sessionPair = connectSessionPair(listen, getReplSessionSecurity());
        try (Session remoteEnd = sessionPair[0];
            Session session = sessionPair[1])
        {
          final ReplServerStartMsg startMsg = (ReplServerStartMsg) sendThrough(remoteEnd, session,
              replServerStartMsg(baseDN, "127.0.0.1:8990"));
          final ReplicationServerHandler handler =
              new ReplicationServerHandler(session, 100, replicationServer, 100);

          final List<String> records = errorLogRecordsOf(() -> {
            handler.startFromRemoteRS(startMsg);
            return null;
          });

          assertThat(recordOf(records, ERR_DUPLICATE_REPLICATION_SERVER_ID.get(
                  replicationServer.getMonitorInstanceName(), registered.getServerAddressURL(),
                  handler.getServerAddressURL(), REMOTE_RS_ID)))
              .as("a peer this server rejects is an error")
              .contains("severity=ERROR");
        }
      }
    });
  }

  /**
   * A frame too large to allocate is not the peer going away, though the session reports it
   * as an {@code IOException}: either the peer breaks the protocol, a handshake message being
   * nowhere near that size, or this server has no heap to spare it, and both are errors.
   */
  @Test
  public void aFrameTooLargeForTheStartMessageOfTheHandshakeThisServerStartedIsAnError()
      throws Exception
  {
    final int rsId = 8271;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityToRSFrameDb", rsId, (replicationServer, remoteEnd, session) -> {
      sendTooLargeFrameHeader(remoteEnd);
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        assertFalse(handler.connect(baseDN, true), "the frame cannot be read, so this is no session");
        return null;
      });

      assertTooLargeFrameIsAnError(records, session,
          WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(rsId, session.getReadableRemoteAddress()));
    });
  }

  /**
   * A frame which does not decode fails in {@code Session.receive()} with neither an
   * {@code IOException} nor a {@code DirectoryException}, and is aborted on from the
   * {@code catch (Exception)} of the handshake, still as an error.
   */
  @Test
  public void aFrameWhichDoesNotDecodeInTheHandshakeThisServerStartedIsAnError()
      throws Exception
  {
    final int rsId = 8278;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityToRSUndecodableDb", rsId, (replicationServer, remoteEnd, session) -> {
      // A frame of one byte, a message type which no message has.
      sendRaw(remoteEnd, new byte[] { '0', '0', '0', '0', '0', '0', '0', '1', 0 });
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        assertFalse(handler.connect(baseDN, true), "the frame cannot be decoded, so this is no session");
        return null;
      });

      assertThat(recordOf(records, LocalizableMessage.raw("received message with unknown type")))
          .as("a frame which does not decode is an error")
          .contains("severity=ERROR");
      assertThat(countRecordsOf(records,
              WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(rsId, session.getReadableRemoteAddress())
                  .toString()))
          .as("a frame which does not decode is not the peer going away")
          .isZero();
    });
  }

  @Test
  public void aFrameTooLargeForTheTopologyInTheHandshakeItStartedIsAnError() throws Exception
  {
    final int rsId = 8272;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityFromRSFrameDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ReplServerStartMsg startMsg =
          (ReplServerStartMsg) sendThrough(remoteEnd, session, replServerStartMsg(baseDN));
      sendTooLargeFrameHeader(remoteEnd);
      final ReplicationServerHandler handler =
          new ReplicationServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        handler.startFromRemoteRS(startMsg);
        return null;
      });

      assertTooLargeFrameIsAnError(records, session,
          WARN_RS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_RS_ID, rsId));
    });
  }

  @Test
  public void aFrameTooLargeForTheStartSessionOfADataServerIsAnError() throws Exception
  {
    final int rsId = 8273;
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    runCase("handshakeAbortSeverityDSFrameDb", rsId, (replicationServer, remoteEnd, session) -> {
      final ServerStartMsg startMsg =
          (ServerStartMsg) sendThrough(remoteEnd, session, serverStartMsg(baseDN));
      sendTooLargeFrameHeader(remoteEnd);
      final DataServerHandler handler =
          new DataServerHandler(session, 100, replicationServer, 100);

      final List<String> records = errorLogRecordsOf(() -> {
        handler.startFromRemoteDS(startMsg);
        return null;
      });

      assertTooLargeFrameIsAnError(records, session,
          WARN_DS_DISCONNECTED_DURING_HANDSHAKE.get(REMOTE_DS_ID, rsId));
      assertAbortedHandshakeWentNoFurther(session);
    });
  }

  /** One case: the replication server under test, and a session pair to it. */
  private interface HandshakeCase
  {
    void run(ReplicationServer replicationServer, Session remoteEnd, Session session)
        throws Exception;
  }

  /**
   * Runs the provided case on a replication server of its own and a session pair to it,
   * which are torn down once it ran.
   */
  private void runCase(String dbName, int rsId, HandshakeCase handshakeCase) throws Exception
  {
    ReplicationServer replicationServer = null;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      replicationServer = newReplicationServer(dbName, rsId);

      final Session[] sessionPair =
          connectSessionPair(listen, getReplSessionSecurity());
      try (Session remoteEnd = sessionPair[0];
          Session session = sessionPair[1])
      {
        handshakeCase.run(replicationServer, remoteEnd, session);
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * Runs the provided handshake on a thread of its own, as the listen thread or the connect
   * thread of the server would, while the remote end reads the provided number of messages
   * and goes away: the handshake then fails on what it waits for next, rather than on a
   * message it sends.
   */
  private static <T> T goneAfterReading(int messages, Session remoteEnd, Callable<T> handshake)
      throws Exception
  {
    final ExecutorService handshakeThread = Executors.newSingleThreadExecutor();
    try
    {
      final Future<T> done = handshakeThread.submit(handshake);
      for (int i = 0; i < messages; i++)
      {
        remoteEnd.receive();
      }
      remoteEnd.abort(new IOException("the replication server went away"));
      return done.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    finally
    {
      handshakeThread.shutdownNow();
    }
  }

  /**
   * Writes the length header of a frame of {@link Integer#MAX_VALUE} bytes from the remote
   * end, and nothing after it: more than any array can hold, so the local end fails to
   * allocate it whatever its heap. The session writes no frame it did not encode itself, so
   * the header goes to the socket under it.
   */
  private static void sendTooLargeFrameHeader(Session remoteEnd) throws Exception
  {
    sendRaw(remoteEnd, String.format("%08x", Integer.MAX_VALUE).getBytes());
  }

  /** Writes the provided bytes from the remote end to the socket under it, as they are. */
  private static void sendRaw(Session remoteEnd, byte[] bytes) throws Exception
  {
    final Field secureSocket = Session.class.getDeclaredField("secureSocket");
    secureSocket.setAccessible(true);
    final OutputStream output = ((SSLSocket) secureSocket.get(remoteEnd)).getOutputStream();
    output.write(bytes);
    output.flush();
  }

  private static void assertTooLargeFrameIsAnError(List<String> records, Session session,
      LocalizableMessage disconnected)
  {
    final String record = recordOf(records, PACKET_TOO_LARGE);
    assertThat(record)
        .as("a frame too large to allocate is an error")
        .contains("severity=ERROR");
    assertThat(record)
        .as("the record of a frame too large to allocate names the peer which sent it")
        .contains("Peer: " + session.getReadableRemoteAddress());
    assertThat(countRecordsOf(records, disconnected.toString()))
        .as("a frame too large to allocate is not the peer going away")
        .isZero();
  }

  /**
   * Asserts that the handshake of a data server ended where it was aborted: only
   * {@code finalizeStart()} starts the thread of the session, once the handler is registered
   * with the domain. Read off the thread rather than off the domain, which the reader of a
   * handler started on a closed session may have unregistered it from already.
   */
  private static void assertAbortedHandshakeWentNoFurther(Session session)
  {
    assertThat(session.getState())
        .as("a handshake which was aborted starts no session thread")
        .isEqualTo(Thread.State.NEW);
  }

  private ReplicationServer newReplicationServer(String dbName, int rsId) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(
        TestCaseUtils.findFreePort(), dbName, 0, rsId, 0, 100, new TreeSet<String>()));
  }

  private ServerStartMsg serverStartMsg(DN baseDN)
  {
    return new ServerStartMsg(REMOTE_DS_ID, "127.0.0.1:1389", baseDN, 100, 0,
        new ServerState(), -1, true, (byte) 1);
  }

  private ReplServerStartMsg replServerStartMsg(DN baseDN)
  {
    return replServerStartMsg(baseDN, REMOTE_RS_URL);
  }

  /**
   * Returns the start message of a fake peer replication server.
   * <p>
   * A generation id of -1 leaves the one of the domain alone, and the session stays
   * encrypted, so that neither end has to leave the SSL session for the handshake to go on.
   */
  private ReplServerStartMsg replServerStartMsg(DN baseDN, String serverURL)
  {
    return new ReplServerStartMsg(REMOTE_RS_ID, serverURL, baseDN, 100,
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
