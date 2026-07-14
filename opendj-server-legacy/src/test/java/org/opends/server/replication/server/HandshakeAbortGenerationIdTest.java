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
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StopMsg;
import org.testng.annotations.Test;

/**
 * Deterministic reproducer for the generation id rollback race fixed in the
 * same change (issue #735): an outgoing RS to RS handshake that is aborted
 * (e.g. rejected by the peer on a simultaneous cross-connect) must not roll
 * the domain generation id back to the value it had when the handshake
 * started, wiping a value that was legitimately adopted in the meantime.
 * <p>
 * The test plays the remote peer itself, so it fully controls the interleaving:
 * the connecting RS snapshots the domain generation id before sending its start
 * message, the test then changes the generation id while the RS is blocked
 * waiting for the answer, and only then rejects the handshake with a StopMsg.
 * The generation id advertised on the next connection attempt is sent strictly
 * after the abort completed on the same connector thread, so it exposes a
 * rollback deterministically, without any timing assumptions.
 */
@SuppressWarnings("javadoc")
public class HandshakeAbortGenerationIdTest extends ReplicationTestCase
{
  private static final long ADOPTED_GEN_ID = 4801;
  private static final int SOCKET_TIMEOUT_MS = 30000;

  @Test
  public void abortedConnectMustNotRollBackConcurrentlyAdoptedGenId() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try (ServerSocket fakePeer = TestCaseUtils.bindFreePort())
    {
      fakePeer.setSoTimeout(SOCKET_TIMEOUT_MS);

      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          rsPort, "handshakeAbortGenIdTestDb", 0, 811, 0, 100,
          newTreeSet("127.0.0.1:" + fakePeer.getLocalPort())));
      // Creating the domain makes the RS connect thread dial the fake peer.
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);

      final ReplSessionSecurity security = getReplSessionSecurity();
      try (Session session = accept(fakePeer, security))
      {
        // The RS is now inside connect(): it snapshotted the domain generation
        // id (-1) before sending its start message.
        final ReplServerStartMsg startMsg =
            waitForSpecificMsg(session, ReplServerStartMsg.class);
        assertEquals(startMsg.getGenerationId(), -1);

        // While connect() is blocked waiting for our answer, the domain adopts
        // a generation id, as happens when gossip arrives from another peer RS.
        domain.changeGenerationId(ADOPTED_GEN_ID);

        // Reject the handshake as a peer does on a simultaneous cross-connect.
        session.publish(new StopMsg());
      }

      // The second connection attempt is made by the same connector thread
      // strictly after abortStart() returned, so the generation id it
      // advertises deterministically shows whether the abort rolled back the
      // concurrently adopted value.
      try (Session session = accept(fakePeer, security))
      {
        final ReplServerStartMsg startMsg =
            waitForSpecificMsg(session, ReplServerStartMsg.class);
        assertEquals(startMsg.getGenerationId(), ADOPTED_GEN_ID,
            "the aborted handshake rolled back a generation id it did not set");
        session.publish(new StopMsg());
      }
    }
    finally
    {
      removeQuietly(replicationServer);
    }
  }

  /**
   * The guards on the generation id primitives introduced for the abort
   * rollback: a compare-and-set never overwrites a value it did not expect,
   * and a rollback never clears a generation id that has been saved to the
   * changelog.
   */
  @Test
  public void generationIdPrimitivesHonorGuards() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          TestCaseUtils.findFreePort(), "handshakeAbortGenIdPrimitivesDb", 0,
          812, 0, 100, new TreeSet<String>()));
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);

      assertFalse(domain.changeGenerationIdIfUnchanged(123, 456),
          "CAS with a stale expected value must not fire");
      assertEquals(domain.getGenerationId(), -1);

      assertTrue(domain.changeGenerationIdIfUnchanged(-1, ADOPTED_GEN_ID));
      assertEquals(domain.getGenerationId(), ADOPTED_GEN_ID);

      domain.rollbackGenerationIdIfUnchanged(999, -1);
      assertEquals(domain.getGenerationId(), ADOPTED_GEN_ID,
          "rollback with a stale expected value must not fire");

      domain.rollbackGenerationIdIfUnchanged(ADOPTED_GEN_ID, -1);
      assertEquals(domain.getGenerationId(), -1,
          "rollback must fire when the value is unchanged and unsaved");

      domain.initGenerationID(ADOPTED_GEN_ID);
      domain.rollbackGenerationIdIfUnchanged(ADOPTED_GEN_ID, -1);
      assertEquals(domain.getGenerationId(), ADOPTED_GEN_ID,
          "rollback must never clear a saved generation id");
    }
    finally
    {
      removeQuietly(replicationServer);
    }
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

  private Session accept(ServerSocket listenSocket, ReplSessionSecurity security)
      throws Exception
  {
    final Socket socket = listenSocket.accept();
    socket.setTcpNoDelay(true);
    final Session session = security.createServerSession(socket, SOCKET_TIMEOUT_MS);
    assertNotNull(session, "could not create a session with the connecting RS");
    return session;
  }
}
