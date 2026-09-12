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
         * A reader on the *sending* end, which is what the server keeps running across a close:
         * ServerHandler.shutdown() closes the session (ServerHandler.java:946) and only joins its
         * ServerReader afterwards (:966). It is not decoration here.
         *
         * Without it this end reaches close() with inbound bytes nobody ever read, and a close in
         * that state ends the connection with a reset instead of a FIN - which discards what the
         * peer has not read yet, the whole of what the drain just wrote included. Measured with a
         * bare socket pair, 8 MiB written to a peer reading behind the writer, the only difference
         * between the runs being whether the closing side drained its own inbound:
         *
         *   linux 6.12 / jdk 11    no reader -> 53.4% arrived, "Connection reset"
         *                          reader    -> 100%   arrived, end of stream
         *   macos 15.7 / jdk 26    no reader -> 98.3% arrived, "Connection reset"
         *                          reader    -> 100%   arrived, end of stream
         *
         * Which is why a case without it measures the teardown rather than the drain, and measures
         * it differently per platform: three ubuntu legs of CI lost between 866 and 1694 of these
         * messages while every macos and windows leg passed. This case says so itself - with the
         * start() below commented out and the class run on linux/jdk11, it fails with "the peer
         * received 2873 of the 3000 messages published; the read ended by java.net.SocketException:
         * Connection reset", and passes with it in.
         *
         * Its soTimeout has to go, too: receive() hands a read timeout to setSessionError(), and a
         * session carrying an error skips the drain exactly as it skips the StopMsg.
         */
        sender.setSoTimeout(0);
        final Thread senderReader = newInboundReader(sender);
        senderReader.start();

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

        final Drained drained = drain(receiver);
        closed.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(drained.received)
            .as("the peer received %d of the %d messages published; the read ended by %s",
                drained.received.size(), MESSAGES_PUBLISHED, drained.endedBy)
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

  /**
   * Consumes whatever arrives on a session until it is closed, as {@code ServerReader} does for a
   * server handler.
   * <p>
   * It is what it reads at the socket rather than what it returns that matters: the peer of these
   * cases publishes nothing, so this returns no message at all, while the read it sits in is what
   * keeps the receive queue of this end empty - which is the condition a close needs to end the
   * connection in an orderly way.
   */
  private Thread newInboundReader(final Session session)
  {
    final Thread reader = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          while (true)
          {
            session.receive();
          }
        }
        catch (final Exception ignored)
        {
          // The close of the session ends the read, which is the end of this thread.
        }
      }
    }, "inbound reader of " + session.getName());
    reader.setDaemon(true);
    return reader;
  }

  /**
   * Reads until the session gives nothing back, and answers the CSNs of the changes it read
   * together with what ended the read.
   * <p>
   * Why the reason is carried rather than swallowed: a short read is exactly the failure this
   * suite is about, and "the peer received fewer than were sent" does not say whether the stream
   * ended orderly at a {@code StopMsg} or was cut off - which are different defects.
   */
  private static final class Drained
  {
    private final List<CSN> received = new CopyOnWriteArrayList<>();
    private String endedBy;

    @Override
    public String toString()
    {
      return received.size() + " change(s), ended by " + endedBy;
    }
  }

  private Drained drain(final Session session)
  {
    final Drained drained = new Drained();
    try
    {
      while (true)
      {
        final ReplicationMsg msg = session.receive();
        if (msg instanceof DeleteMsg)
        {
          drained.received.add(((DeleteMsg) msg).getCSN());
        }
        else if (msg instanceof StopMsg)
        {
          drained.endedBy = "a StopMsg";
          return drained;
        }
        else
        {
          drained.endedBy = "an unexpected " + msg.getClass().getSimpleName();
          return drained;
        }
      }
    }
    catch (final Exception e)
    {
      drained.endedBy = e.getClass().getName() + ": " + e.getMessage();
      return drained;
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
