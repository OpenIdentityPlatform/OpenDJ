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
package org.opends.server.replication.plugin;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StartSessionMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.protocol.WindowProbeMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.util.TestTimer;
import org.testng.annotations.Test;

/**
 * Tests that the broker of a domain can be stopped while its replication server keeps the session
 * up and takes no update from it, and that the stop gives up no more than it has to.
 * <p>
 * The replication server returns window credit as its reader takes the updates of the session, and
 * the same reader answers a window probe, while its heartbeats come from a thread of their own: a
 * reader held up leaves the window closed on a session the domain sees alive. A publish then waits
 * for credit with nothing but the stop of the broker to end it, and stop() publishes the replica
 * offline message - which needs credit as well, and the lock of the pending changes a local change
 * is pushed under - before it sets the flag which ends that wait.
 */
@SuppressWarnings("javadoc")
public class ReplicationBrokerStopTest extends ReplicationTestCase
{
  private static final int DS_ID = 1;
  private static final int OTHER_DS_ID = 3;

  private final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);

  /**
   * With the only credit of the window spent, the replica offline message stop() publishes finds
   * none: the disable has to end all the same, and the message is given up rather than written.
   */
  @Test(timeOut = 120_000)
  public void aDisableEndsWhenTheReplicationServerGivesNoCredit() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);

      // The only credit of the window goes to a change of our own.
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      disabling = startThread("disable", domain::disable);
      disabling.join(stopBoundMS());
      assertFalse(disabling.isAlive(),
          "disable() waits for window credit a replication server which takes no update does not give");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(ReplicaOfflineMsg.class), 0, "the replica offline message was written with no credit");
    }
    finally
    {
      cleanUp(rs, disabling);
    }
  }

  /**
   * A local change which waits for credit holds the lock of the pending changes, which stop() needs
   * to publish the replica offline message: the disable has to end the wait rather than wait for
   * it, and the change ends with it.
   */
  @Test(timeOut = 120_000)
  public void aDisableEndsWhileALocalChangeWaitsForCredit() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread writer = null;
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final AtomicReference<ResultCode> second = new AtomicReference<>();
      writer = startThread("writer", () -> second.set(replaceDescription("second")));
      // A probe is sent only by a publish which waited for credit and found the window closed.
      rs.waitForWindowProbes(1);

      disabling = startThread("disable", domain::disable);
      disabling.join(stopBoundMS());
      assertFalse(disabling.isAlive(),
          "disable() waits for a local change which waits for window credit under the lock it needs");
      writer.join(stopBoundMS());
      assertFalse(writer.isAlive(), "the local change still waits for credit on a stopped broker");
      assertEquals(second.get(), ResultCode.SUCCESS);
    }
    finally
    {
      cleanUp(rs, disabling, writer);
    }
  }

  /**
   * A replication server which is only slow gives the credit before the stop gives up on it: the
   * change which waited for credit under the lock goes out, and the replica offline message after
   * it, as they did before a stop could give anything up.
   */
  @Test(timeOut = 120_000)
  public void aStopPublishesWhatTheReplicationServerTakesInTime() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread writer = null;
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final AtomicReference<ResultCode> second = new AtomicReference<>();
      writer = startThread("writer", () -> second.set(replaceDescription("second")));
      rs.waitForWindowProbes(1);
      disabling = startThread("disable", domain::disable);
      waitForTheOfflineMessageToWaitForTheLock(disabling);

      // The change keeps waiting for credit well into the stop: two more waits of it.
      rs.waitForWindowProbes(rs.count(WindowProbeMsg.class) + 2);
      rs.giveCredit(2);

      disabling.join(stopBoundMS());
      assertFalse(disabling.isAlive(), "disable() did not end once the replication server gave credit");
      writer.join(stopBoundMS());
      assertEquals(second.get(), ResultCode.SUCCESS);
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(LDAPUpdateMsg.class), 2, "the change which waited for credit was given up");
      assertEquals(rs.count(ReplicaOfflineMsg.class), 1, "the replica offline message was given up");
      final List<Class<?>> updates = rs.receivedUpdates();
      assertEquals(updates.get(updates.size() - 1), ReplicaOfflineMsg.class,
          "the replica offline message has to come after the changes before it: " + updates);
    }
    finally
    {
      cleanUp(rs, disabling, writer);
    }
  }

  /**
   * Once the stop gave a change up, the broker writes nothing more: the replica offline message
   * carries the newest CSN of the replica, and written after a change it gave up it tells the
   * replication server that the replica has nothing more to send up to that CSN. A broker started
   * again writes as before.
   */
  @Test(timeOut = 120_000)
  public void nothingIsWrittenAfterAChangeTheStopGaveUp() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread writer = null;
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final AtomicReference<ResultCode> second = new AtomicReference<>();
      writer = startThread("writer", () -> second.set(replaceDescription("second")));
      rs.waitForWindowProbes(1);
      disabling = startThread("disable", domain::disable);
      waitForTheOfflineMessageToWaitForTheLock(disabling);

      /* The change is given up, and credit comes right after it: a stop which went on writing would
       * still wait for it, while one which writes nothing may have ended the session already. */
      writer.join(stopBoundMS());
      assertFalse(writer.isAlive(), "the local change still waits for credit on a stopped broker");
      rs.giveCreditIfTheSessionIsUp(1);

      disabling.join(stopBoundMS());
      assertFalse(disabling.isAlive(), "disable() waits for window credit");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(LDAPUpdateMsg.class), 1);
      assertEquals(rs.count(ReplicaOfflineMsg.class), 0,
          "the replica offline message was written after a change the stop gave up");

      // That is over once the broker is started again.
      domain.enable();
      waitForConnection(domain);
      assertEquals(replaceDescription("third"), ResultCode.SUCCESS);
      rs.waitForUpdates(2);
    }
    finally
    {
      cleanUp(rs, disabling, writer);
    }
  }

  /**
   * A stop gives up only what finds no credit: with the window open, the replica offline message
   * still goes out. And once started again, the broker waits for credit as it did before it was ever
   * stopped: a stop does not leave behind a broker which gives a publish up as soon as the window is
   * closed.
   */
  @Test(timeOut = 120_000)
  public void aBrokerStartedAgainWaitsForCredit() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread writer = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      domain.disable();
      final long stopped = System.nanoTime();
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(ReplicaOfflineMsg.class), 1, "the replica offline message was given up");
      domain.enable();
      waitForConnection(domain);

      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final AtomicReference<ResultCode> second = new AtomicReference<>();
      writer = startThread("writer", () -> second.set(replaceDescription("second")));
      // Two probes: the publish waited for credit twice, and did not give up after the first wait.
      rs.waitForWindowProbes(2);
      // Nor past the time the stop gave the replication server, which a flag the stop left would end.
      final long pastTheStopMS = MultimasterReplication.getConnectionTimeoutMS() + 1000
          - NANOSECONDS.toMillis(System.nanoTime() - stopped);
      Thread.sleep(Math.max(0, pastTheStopMS));
      assertTrue(writer.isAlive(), "a publish gave the change up while nobody stops the broker");

      rs.giveCredit(1);
      rs.waitForUpdates(2);
      writer.join(stopBoundMS());
      assertFalse(writer.isAlive(), "the local change still waits once the credit came");
      assertEquals(second.get(), ResultCode.SUCCESS);
    }
    finally
    {
      cleanUp(rs, writer);
    }
  }

  /**
   * A stop ends with the broker stopped even when publishing the replica offline message fails:
   * a broker left running as a stopping one would give up every publish which waits for credit.
   */
  @Test(timeOut = 120_000)
  public void aStopWhichCannotPublishTheOfflineMessageStillStopsTheBroker() throws Exception
  {
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    rs.start();
    final RuntimeException failure = new IllegalStateException("cannot publish the replica offline message");
    final ReplicationBroker broker = new ReplicationBroker(new DummyReplicationDomain(getGenerationId(baseDN))
    {
      @Override
      public void publishReplicaOfflineMsg()
      {
        throw failure;
      }
    }, new ServerState(), newFakeCfg(baseDN, OTHER_DS_ID, rs.getPort()), getReplSessionSecurity());
    try
    {
      connect(broker, 0);
      try
      {
        broker.stop();
        fail("the failure of the replica offline message was swallowed");
      }
      catch (IllegalStateException e)
      {
        assertSame(e, failure);
      }
      assertFalse(broker.isConnected(), "the broker still runs its session after it was stopped");
    }
    finally
    {
      rs.shutdown();
    }
  }

  private LDAPReplicationDomain startDomain(WithholdingReplicationServer rs) throws Exception
  {
    rs.start();
    final DomainFakeCfg conf = new DomainFakeCfg(baseDN, DS_ID,
        new TreeSet<>(Collections.singleton("127.0.0.1:" + rs.getPort())));
    // The replication server sends no heartbeat: the session has to stay up all the same.
    conf.setHeartbeatInterval(100000);
    final LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(conf);
    domain.start();
    waitForConnection(domain);
    return domain;
  }

  private void waitForConnection(final LDAPReplicationDomain domain) throws Exception
  {
    newTimer().repeatUntilSuccess(() -> assertTrue(domain.isConnected(), "the domain is not connected"));
  }

  /** Waits for stop() to wait for the lock of the pending changes, which a local change holds. */
  private static void waitForTheOfflineMessageToWaitForTheLock(final Thread disabling) throws Exception
  {
    newTimer().repeatUntilSuccess(() -> {
      boolean inPutReplicaOfflineMsg = false;
      for (StackTraceElement frame : disabling.getStackTrace())
      {
        inPutReplicaOfflineMsg |= frame.getClassName().equals(PendingChanges.class.getName())
            && frame.getMethodName().equals("putReplicaOfflineMsg");
      }
      assertTrue(disabling.getState() == Thread.State.BLOCKED && inPutReplicaOfflineMsg,
          "stop() does not wait for the lock of the pending changes");
    });
  }

  private ResultCode replaceDescription(String value)
  {
    return getRootConnection().processModify(modifyRequest(baseDN, REPLACE, "description", value))
        .getResultCode();
  }

  /**
   * How long disabling the domain may take: the stop gives the replication server as long as a
   * connection to it to give credit, and the bound adds a margin far beyond what the stop needs on
   * top of that, and far below what a stop which waits for the replication server would take:
   * forever.
   */
  private static long stopBoundMS()
  {
    return MultimasterReplication.getConnectionTimeoutMS() + 10000;
  }

  private static Thread startThread(String name, Runnable runnable)
  {
    final Thread thread = new Thread(runnable, ReplicationBrokerStopTest.class.getSimpleName() + " " + name);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  /**
   * Closes the replication server and then waits for the threads: on a broker which waits for credit
   * whatever happens, only the end of the session ends their wait - the broker finds no replication
   * server to connect to again and gives the publish up.
   */
  private void cleanUp(WithholdingReplicationServer rs, Thread... threads) throws Exception
  {
    rs.shutdown();
    for (Thread thread : threads)
    {
      if (thread != null)
      {
        thread.join(30000);
      }
    }
    // A no-op when no domain was registered.
    MultimasterReplication.deleteDomain(baseDN);
  }

  private static TestTimer newTimer()
  {
    return new TestTimer.Builder().maxSleep(10, SECONDS).sleepTimes(50, MILLISECONDS).toTimer();
  }

  /**
   * A replication server which keeps the session of the domain up and reads every message of it,
   * but gives no credit beyond the window of one update it opens the session with, unless the test
   * asks it to.
   */
  private static final class WithholdingReplicationServer extends Thread
  {
    private static final int RS_ID = 2;
    private static final int WINDOW = 1;

    private final ServerSocket listenSocket;
    private volatile Session session;
    /** The kinds of the messages received on the sessions, in the order they came. */
    private final List<Class<?>> received = new CopyOnWriteArrayList<>();

    WithholdingReplicationServer() throws IOException
    {
      super(ReplicationBrokerStopTest.class.getSimpleName() + " replication server");
      setDaemon(true);
      listenSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    int getPort()
    {
      return listenSocket.getLocalPort();
    }

    @Override
    public void run()
    {
      while (!listenSocket.isClosed())
      {
        try
        {
          serve(listenSocket.accept());
        }
        catch (Exception e)
        {
          // The domain closed the session, or the test closed this server.
        }
      }
    }

    /**
     * Answers both phases of the handshake: the first connection of the domain only asks for the
     * state of this server and ends with a StopMsg, the one it keeps goes on to start a session.
     */
    private void serve(Socket socket) throws Exception
    {
      final Session s =
          new ReplSessionSecurity().createServerSession(socket, MultimasterReplication.getConnectionTimeoutMS());
      try
      {
        final ServerStartMsg start = (ServerStartMsg) s.receive();
        final String url = "127.0.0.1:" + getPort();
        s.publish(new ReplServerStartMsg(RS_ID, url, start.getBaseDN(), WINDOW, start.getServerState(),
            start.getGenerationId(), start.getSSLEncryption(), start.getGroupId(), 5000));
        if (!start.getSSLEncryption())
        {
          s.stopEncryption();
        }
        if (!(s.receive() instanceof StartSessionMsg))
        {
          return;
        }
        s.publish(new TopologyMsg(new ArrayList<DSInfo>(), Collections.singletonList(
            new RSInfo(RS_ID, url, start.getGenerationId(), start.getGroupId(), 1))));
        s.setSoTimeout(0);
        session = s;
        while (true)
        {
          received.add(s.receive().getClass());
        }
      }
      finally
      {
        session = null;
        s.close();
      }
    }

    int count(Class<? extends ReplicationMsg> kind)
    {
      int count = 0;
      for (Class<?> msg : received)
      {
        count += kind.isAssignableFrom(msg) ? 1 : 0;
      }
      return count;
    }

    /** Returns the kinds of the updates received, replica offline messages included, in order. */
    List<Class<?>> receivedUpdates()
    {
      final List<Class<?>> updates = new ArrayList<>();
      for (Class<?> msg : received)
      {
        if (LDAPUpdateMsg.class.isAssignableFrom(msg) || ReplicaOfflineMsg.class.isAssignableFrom(msg))
        {
          updates.add(msg);
        }
      }
      return updates;
    }

    void giveCredit(int credit) throws IOException
    {
      session.publish(new WindowMsg(credit));
    }

    /** Gives credit unless the domain ended the session, before this reads it or while this writes. */
    void giveCreditIfTheSessionIsUp(int credit)
    {
      final Session s = session;
      if (s != null)
      {
        try
        {
          s.publish(new WindowMsg(credit));
        }
        catch (IOException e)
        {
          // The domain closed the session in between.
        }
      }
    }

    void waitForUpdates(final int count) throws Exception
    {
      newTimer().repeatUntilSuccess(
          () -> assertEquals(count(LDAPUpdateMsg.class), count, "updates the replication server received"));
    }

    void waitForWindowProbes(final int count) throws Exception
    {
      newTimer().repeatUntilSuccess(() -> assertTrue(count(WindowProbeMsg.class) >= count,
          "window probes the replication server received"));
    }

    /** Waits for the session to end: what the domain wrote to it has all been read by then. */
    void waitForTheSessionToEnd() throws Exception
    {
      newTimer().repeatUntilSuccess(() -> assertNull(session, "the session of the domain is still up"));
    }

    void shutdown() throws Exception
    {
      listenSocket.close();
      final Session s = session;
      if (s != null)
      {
        s.close();
      }
      join(30000);
    }
  }
}
