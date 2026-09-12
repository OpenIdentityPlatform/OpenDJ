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
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

/**
 * Which end of a replication session has a publisher thread, and what a close of the session
 * does to the messages that thread has not sent yet.
 * <p>
 * {@link Session#publish(ReplicationMsg)} has two branches, and which one a message takes used
 * to decide whether a close could lose it. With a publisher thread running the call is an enqueue
 * onto {@code sendQueue}; without one it is a synchronous write of the socket. {@link
 * Session#close()} used to drain neither: it set the flag the publisher loops on, interrupted it
 * and joined, so anything still queued was dropped while the {@code StopMsg} published afterwards
 * still went out - leaving the peer with an orderly close and no sign that something was lost.
 * That is the limitation PR #919 recorded, and the last test here is what holds the close to
 * sending that queue instead.
 * <p>
 * The other two pin which end could ever pay it. Only {@code ServerHandler} starts a session's
 * publisher, so it is the replication-server end of a session which has one; the broker of a
 * directory server never starts its own. A change a directory server publishes is therefore on
 * the wire by the time {@code publish()} returns, and no close of that session could drop it -
 * which is what rules the send queue out as the explanation of #963.
 *
 * @see <a href="https://github.com/OpenIdentityPlatform/OpenDJ/issues/963">issue #963</a>
 */
@SuppressWarnings("javadoc")
public class SessionPublisherDrainTest extends ReplicationTestCase
{
  private static final int DS_ID = 123;
  private static final int RS_ID = 104;
  private static final int SOCKET_TIMEOUT_MS = 5000;

  /**
   * The number of messages the queued-message test publishes. It has to outrun what the socket
   * buffers of a loopback pair can swallow while nothing reads them, and stay under the 4000 the
   * send queue holds, past which {@code publish()} would block instead of queueing.
   */
  private static final int MESSAGES_PUBLISHED = 3000;

  /**
   * The session a directory server publishes its changes on has no publisher thread: nothing
   * calls {@link Session#start()} on it, so the thread is still {@code NEW} once the broker is
   * connected and has completed its handshake.
   */
  @Test
  public void theSessionOfADirectoryServerBrokerHasNoPublisherThread() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          replicationPort, "sessionPublisherDrainDb", 0, RS_ID, 0, 100, new TreeSet<String>()));
      broker = openReplicationSession(
          baseDN, DS_ID, 100, replicationPort, SOCKET_TIMEOUT_MS, EMPTY_DN_GENID);

      final Session session = sessionOf(broker);
      assertThat(session)
          .as("the broker reported itself connected without a session")
          .isNotNull();
      assertThat(session.isAlive())
          .as("the publisher thread of the session a directory server publishes on is running, "
              + "so publish() enqueues and a close of that session can drop what is queued")
          .isFalse();
      assertThat(session.getState())
          .as("the publisher thread of a broker session was started at some point")
          .isEqualTo(Thread.State.NEW);
    }
    finally
    {
      stop(broker);
      remove(replicationServer);
    }
  }

  /**
   * With no publisher thread, {@code publish()} has written the message to the socket by the
   * time it returns: an immediate close cannot drop it, and the peer reads it after the close.
   */
  @Test
  public void aSessionWithNoPublisherThreadHasReachedTheWireWhenPublishReturns() throws Exception
  {
    final CSNGenerator csns = new CSNGenerator(DS_ID, 0);
    final CSN csn = csns.newCSN();
    try (ServerSocket listen = new ServerSocket(0))
    {
      final Session[] pair = connectSessionPair(listen);
      final Session sender = pair[0];
      final Session receiver = pair[1];
      try
      {
        sender.publish(new DeleteMsg(DN.valueOf("uid=onthewire," + TEST_ROOT_DN_STRING),
            csn, "00000000-0000-0000-0000-000000000000"));
        /*
         * No drain, no flush and no wait in between - the close of the restore path of #963 is
         * this close. What publish() already wrote stays readable by the peer: a close sends a
         * FIN, it does not unsend the bytes ahead of it.
         */
        sender.close();

        final ReplicationMsg received = receiver.receive();
        assertThat(received)
            .as("the peer did not receive the change published just before the session was closed")
            .isInstanceOf(DeleteMsg.class);
        assertThat(((DeleteMsg) received).getCSN()).isEqualTo(csn);
        /*
         * What makes this the synchronous branch rather than a race won by a publisher thread:
         * there was no publisher thread to win it. The message reached the peer and the session's
         * own thread never ran, so publish() is what wrote it.
         */
        assertThat(sender.getState())
            .as("the session had a publisher thread after all, so the delivery above says only "
                + "that it outran the close, not that publish() wrote the message itself")
            .isEqualTo(Thread.State.NEW);
      }
      finally
      {
        StaticUtils.close(sender, receiver);
      }
    }
  }

  /**
   * With a publisher thread running, a close sends what that thread had not sent yet rather than
   * dropping it. This is the replication-server end of a session - the end {@code ServerHandler}
   * starts - and the limitation PR #919 recorded.
   * <p>
   * The peer reads the changes and then the {@code StopMsg}, which is what {@link #drain(Session)}
   * stops on: a queue sent after that message would not be counted, so the size below pins the
   * order as well as the delivery.
   */
  @Test
  public void aSessionWithAPublisherThreadSendsWhatIsStillQueuedWhenItIsClosed() throws Exception
  {
    final CSNGenerator csns = new CSNGenerator(RS_ID, 0);
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try (ServerSocket listen = new ServerSocket(0))
    {
      final Session[] pair = connectSessionPair(listen);
      final Session sender = pair[0];
      final Session receiver = pair[1];
      try
      {
        sender.start();
        sender.waitForStartup();

        /*
         * Nothing reads the peer end while these are published, so the socket buffers fill and
         * the publisher thread is left inside its write with the rest of them still queued.
         */
        for (int i = 0; i < MESSAGES_PUBLISHED; i++)
        {
          sender.publish(new DeleteMsg(DN.valueOf("uid=queued" + i + "," + TEST_ROOT_DN_STRING),
              csns.newCSN(), "00000000-0000-0000-0000-000000000000"));
        }

        final Future<?> closed = executor.submit(new Callable<Void>()
        {
          @Override
          public Void call()
          {
            sender.close();
            return null;
          }
        });

        final List<CSN> received = drain(receiver);
        closed.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(received)
            .as("the close dropped %d of the %d messages published: the publisher thread stopped "
                + "with them still queued and nothing sent them",
                MESSAGES_PUBLISHED - received.size(), MESSAGES_PUBLISHED)
            .hasSize(MESSAGES_PUBLISHED);
      }
      finally
      {
        StaticUtils.close(sender, receiver);
      }
    }
    finally
    {
      executor.shutdownNow();
    }
  }

  /** Reads until the session gives nothing back, and answers the CSNs of the changes it read. */
  private List<CSN> drain(final Session session)
  {
    final List<CSN> received = new CopyOnWriteArrayList<>();
    try
    {
      while (true)
      {
        final ReplicationMsg msg = session.receive();
        if (msg instanceof DeleteMsg)
        {
          received.add(((DeleteMsg) msg).getCSN());
        }
        else if (msg instanceof StopMsg)
        {
          return received;
        }
      }
    }
    catch (final Exception ignored)
    {
      // The close of the far end ends the read, which is the end of the drain.
      return received;
    }
  }

  /** The session a broker publishes on, which it keeps to itself. */
  private Session sessionOf(final ReplicationBroker broker) throws Exception
  {
    final Field connectedRSField = ReplicationBroker.class.getDeclaredField("connectedRS");
    connectedRSField.setAccessible(true);
    final Object connectedRS = ((AtomicReference<?>) connectedRSField.get(broker)).get();
    final Field sessionField = connectedRS.getClass().getDeclaredField("session");
    sessionField.setAccessible(true);
    return (Session) sessionField.get(connectedRS);
  }

  /**
   * A connected pair of sessions over the loopback, the client end first. Neither end is
   * started: a session which publishes synchronously is what a broker has, and the test which
   * needs a publisher thread starts the end it needs.
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
