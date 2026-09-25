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
package org.opends.server.replication.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

/**
 * A message published to a running session is handed to the session thread, which writes it to
 * the socket later. A caller which needs to know when that happened - the replication server
 * forwarding a ReplicaOfflineMsg, whose shutdown must not close the session before the message
 * is on the wire - attaches a callback to the message, and the session runs it once, from the
 * thread which wrote it, only after the write returned.
 * <p>
 * The peer of each test reads nothing until the test lets it, and both ends of the connection
 * have socket buffers far smaller than {@link #BLOCKING_MESSAGE_SIZE}, so that a test which
 * publishes a message of that size holds the session thread of the end under test inside its
 * write for as long as it wants - the state in which a message published behind it is queued
 * and not written.
 */
@SuppressWarnings("javadoc")
public class SessionTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /**
   * Socket buffers small enough that {@link #BLOCKING_MESSAGE_SIZE} bytes cannot be written
   * through them: the write blocks until the peer reads. Set explicitly on both ends, since the
   * buffers the kernel picks on its own grow well beyond it on a loopback link - and the message
   * is larger by far than what they hold, since a kernel which does not honour the size asked
   * for on the receiving side, as macOS does not, must still be unable to take the whole of it.
   */
  private static final int SOCKET_BUFFER_SIZE = 8 * 1024;
  private static final int BLOCKING_MESSAGE_SIZE = 4 * 1024 * 1024;
  /** Time given to a callback which must not run, to see that it does not. */
  private static final long SETTLE_MS = 500;
  private static final int SENDER_ID = 1;
  private static final int PEER_ID = 2;

  @Test
  public void theCallbackRunsOnceTheMessageIsWrittenAndNotWhenItIsQueued() throws Exception
  {
    try (SessionPair pair = connectSessionPair())
    {
      pair.publisher.start();
      pair.publisher.waitForStartup();

      // The session thread is inside the write of this message until the peer reads it.
      pair.publisher.publish(newBlockingMsg());
      pair.awaitBytesReachedThePeer();

      final CountDownLatch written = new CountDownLatch(1);
      final boolean accepted = pair.publisher.publish(new HeartbeatMsg(), written::countDown);

      assertThat(accepted).as("the message was refused by a running session").isTrue();
      assertThat(written.await(SETTLE_MS, TimeUnit.MILLISECONDS))
          .as("the callback ran while the message was still queued behind a message the peer "
              + "had not read")
          .isFalse();

      assertThat(pair.peer.receive()).isInstanceOf(EntryMsg.class);
      assertThat(written.await(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .as("the callback did not run once the message had been written")
          .isTrue();
      assertThat(pair.peer.receive()).isInstanceOf(HeartbeatMsg.class);
    }
  }

  @Test
  public void aMessageWhichCannotBeEncodedForThePeerIsRefusedWithoutRunningTheCallback()
      throws Exception
  {
    try (SessionPair pair = connectSessionPair())
    {
      pair.publisher.start();
      pair.publisher.waitForStartup();
      // A ReplicaOfflineMsg has no encoding before protocol version 8.
      pair.publisher.setProtocolVersion(ProtocolVersion.REPLICATION_PROTOCOL_V7);

      final CountDownLatch written = new CountDownLatch(1);
      final boolean accepted =
          pair.publisher.publish(new ReplicaOfflineMsg(newCSN()), written::countDown);

      assertThat(accepted)
          .as("a message the peer cannot decode was reported as accepted")
          .isFalse();
      assertThat(written.await(SETTLE_MS, TimeUnit.MILLISECONDS))
          .as("the callback ran for a message which was never written")
          .isFalse();
    }
  }

  /**
   * Before its thread is started, and once that thread is gone, a session writes on the
   * publishing thread itself; the callback then runs on that same thread, after the write.
   */
  @Test
  public void theCallbackRunsAfterAMessageWrittenOnThePublishingThread() throws Exception
  {
    try (SessionPair pair = connectSessionPair())
    {
      final CountDownLatch written = new CountDownLatch(1);
      final boolean accepted = pair.publisher.publish(new HeartbeatMsg(), written::countDown);

      assertThat(accepted).as("the message was refused by a session with no thread").isTrue();
      assertThat(written.getCount())
          .as("the callback had not run when the publish which wrote the message returned")
          .isZero();
      assertThat(pair.peer.receive()).isInstanceOf(HeartbeatMsg.class);
    }
  }

  private static EntryMsg newBlockingMsg()
  {
    return new EntryMsg(SENDER_ID, PEER_ID, new byte[BLOCKING_MESSAGE_SIZE], 1);
  }

  private static CSN newCSN()
  {
    return new CSNGenerator(SENDER_ID, 0).newCSN();
  }

  /**
   * Connects the end under test, in the server role of the replication protocol, with a peer
   * which reads only when a test does. Both ends exchange one message under TLS and then drop the
   * security layer, as the replication handshake does when encryption is not required, so that
   * the socket buffers alone decide when a write blocks: a message read under TLS by each end is
   * what consumes the records TLS itself sends after its negotiation, which would otherwise be
   * read as the start of a replication message once the layer is gone.
   */
  private static SessionPair connectSessionPair() throws Exception
  {
    final ReplSessionSecurity security = getReplSessionSecurity();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Socket peerSocket = new Socket();
    Socket publisherSocket = null;
    Session publisher = null;
    boolean connected = false;
    try (ServerSocket listen = TestCaseUtils.bindFreePort())
    {
      listen.setSoTimeout(SOCKET_TIMEOUT_MS);
      peerSocket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      peerSocket.setTcpNoDelay(true);
      peerSocket.connect(new InetSocketAddress("127.0.0.1", listen.getLocalPort()), SOCKET_TIMEOUT_MS);
      // The TLS negotiation needs both ends handshaking at the same time.
      final Future<Session> peerEnd =
          executor.submit(() -> security.createClientSession(peerSocket, SOCKET_TIMEOUT_MS));

      publisherSocket = listen.accept();
      publisherSocket.setSendBufferSize(SOCKET_BUFFER_SIZE);
      publisherSocket.setTcpNoDelay(true);
      publisher = security.createServerSession(publisherSocket, SOCKET_TIMEOUT_MS);
      assertThat(publisher).as("could not create the session under test").isNotNull();
      final Session peer = peerEnd.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      publisher.publish(new HeartbeatMsg());
      assertThat(peer.receive()).isInstanceOf(HeartbeatMsg.class);
      peer.publish(new HeartbeatMsg());
      assertThat(publisher.receive()).isInstanceOf(HeartbeatMsg.class);
      publisher.stopEncryption();
      peer.stopEncryption();
      connected = true;
      return new SessionPair(publisher, peer, peerSocket);
    }
    finally
    {
      executor.shutdownNow();
      if (!connected)
      {
        if (publisher != null)
        {
          publisher.close();
        }
        StaticUtils.close(publisherSocket, peerSocket);
      }
    }
  }

  private static final class SessionPair implements Closeable
  {
    private final Session publisher;
    private final Session peer;
    private final Socket peerSocket;

    private SessionPair(Session publisher, Session peer, Socket peerSocket)
    {
      this.publisher = publisher;
      this.peer = peer;
      this.peerSocket = peerSocket;
    }

    /**
     * Waits for the first bytes of a message to reach the peer: the session thread of the end
     * under test is then inside the write of that message, and stays there until the peer
     * reads, since the message is larger than the buffers on both sides of the connection.
     */
    void awaitBytesReachedThePeer() throws Exception
    {
      final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SOCKET_TIMEOUT_MS);
      while (peerSocket.getInputStream().available() == 0)
      {
        assertThat(System.nanoTime() < deadline)
            .as("nothing was written to the peer")
            .isTrue();
        Thread.sleep(10);
      }
    }

    @Override
    public void close()
    {
      // The peer first: a session thread held inside a write is released by the peer going away,
      // and close() joins that thread.
      peer.close();
      publisher.close();
    }
  }
}
