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
import static org.opends.messages.ReplicationMessages.WARN_REPLICATION_SERVER_BADLY_DISCONNECTED;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.plugins.PausePreParsePlugin;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.HeartbeatMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StartSessionMsg;
import org.opends.server.replication.protocol.StopMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.protocol.WindowProbeMsg;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.OperationType;
import org.opends.server.util.TestTimer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
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
 * <p>
 * A replication server which reads nothing of the session holds the stop as well, once the socket
 * buffers are full: a write then blocks in the socket, with the locks it is made under, and only
 * the close of the socket ends it. A replication server which is gone altogether, heartbeats
 * included, holds that write with no stop at all: the heartbeat monitor has to close the session
 * the same way.
 * <p>
 * The stop gives the replication server the connection timeout of the replication provider to give
 * credit, and as long again to read what was written. The suite sets it to 30 seconds: the cases run
 * with {@link #SHORT_CONNECTION_TIMEOUT_MS}, and those which need another one set it for their own
 * run.
 */
@SuppressWarnings("javadoc")
public class ReplicationBrokerStopTest extends ReplicationTestCase
{
  private static final int DS_ID = 1;
  private static final int OTHER_DS_ID = 3;
  private static final DN PROVIDER_DN =
      DN.valueOf("cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");
  /** The connection timeout the cases run with, unless they set their own. */
  private static final long SHORT_CONNECTION_TIMEOUT_MS = 2000;
  /**
   * A connection timeout longer than the shipped default of 5 seconds, and far longer than what a
   * replication server which is only slow needs in these cases to give credit.
   */
  private static final long LONG_CONNECTION_TIMEOUT_MS = 6000;
  /** The window of a replication server whose cases need no wait for credit: the default one. */
  private static final int LARGE_WINDOW = 100000;
  /**
   * The size of the messages a case writes until the socket buffers are full, in bytes or in
   * characters: a few dozen of them fill the buffers a loopback connection gets on its own.
   */
  private static final int LARGE_MESSAGE_SIZE = 256 * 1024;
  /** How many of those messages a case writes at most before it reports that no write blocks. */
  private static final int MAX_LARGE_MESSAGES = 400;
  /**
   * The heartbeat interval of the case of a replication server which is gone: long enough for its
   * writes to fill the socket buffers before the heartbeat monitor gives up on the session.
   */
  private static final long HEARTBEAT_INTERVAL_MS = 4000;
  /**
   * The replication servers which answer no connection in the case of a connection under way: each
   * costs the connection the connection timeout, and together they hold it well past twice that, the
   * deadline of the watchdog of a stop.
   */
  private static final int SILENT_SERVERS = 4;
  /** A value which makes a change of the description one of those messages. */
  private static final String LARGE_VALUE = "x".repeat(LARGE_MESSAGE_SIZE);

  private final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
  private long connectionTimeoutOfTheSuiteMS;

  @BeforeClass
  public void shortenTheConnectionTimeout()
  {
    connectionTimeoutOfTheSuiteMS = MultimasterReplication.getConnectionTimeoutMS();
    setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
  }

  @AfterClass(alwaysRun = true)
  public void restoreTheConnectionTimeout()
  {
    setConnectionTimeout(connectionTimeoutOfTheSuiteMS);
  }

  /**
   * With the only credit of the window spent, the replica offline message stop() publishes finds
   * none: the disable has to end all the same, and the message is given up rather than written -
   * once the replication server had the connection timeout, as it is configured, to give credit.
   * The announcement of the message is withdrawn, and the session is ended in order: a wait for
   * credit, which gives up by itself, is no reason to close it as a failed one.
   */
  @Test(timeOut = 120_000)
  public void aDisableEndsWhenTheReplicationServerGivesNoCredit() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);

      // The only credit of the window goes to a change of our own.
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final long beforeTheDisable = System.nanoTime();
      disabling = startThread("disable", domain::disable);
      disabling.join(stopBoundMS());
      final long disableMS = NANOSECONDS.toMillis(System.nanoTime() - beforeTheDisable);
      assertFalse(disabling.isAlive(),
          "disable() waits for window credit a replication server which takes no update does not give");
      assertTrue(disableMS >= LONG_CONNECTION_TIMEOUT_MS, "disable() gave the replication server " + disableMS
          + " ms to give credit, not the connection timeout of " + LONG_CONNECTION_TIMEOUT_MS + " ms");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(ReplicaOfflineMsg.class), 0, "the replica offline message was written with no credit");
      assertEquals(rs.count(StopMsg.class), 1, "the session was closed as a failed one, not ended in order");

      /* The grace period of the announcement has run out by now, so whether the shutdown of a
       * collocated replication server would wait for it says nothing: what is left announced does.
       * An announcement older than the change is one of an earlier stop of the replica. */
      final CSN announced = offlineAnnouncementOfTheReplica();
      assertTrue(announced == null || !announced.isNewerThan(rs.receivedUpdateCSNs().get(0)),
          "the replica offline message given up is still announced: " + announced);
    }
    finally
    {
      cleanUp(rs, disabling);
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
    }
  }

  /**
   * With no connection timeout, a connection waits for its replication server for as long as it
   * takes, and so does a stop for credit: the replica offline message waits past its first wait for
   * credit and past the deadline a stop would have with the shipped default of 5 seconds, and it
   * goes out once the credit comes.
   */
  @Test(timeOut = 120_000)
  public void withNoConnectionTimeoutAStopWaitsForTheCredit() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    setConnectionTimeout(0);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final long beforeTheDisable = System.nanoTime();
      disabling = startThread("disable", domain::disable);
      // A probe follows each wait for credit which found none, unless the publish gave up after it.
      rs.waitForWindowProbes(2);
      Thread.sleep(Math.max(0,
          LONG_CONNECTION_TIMEOUT_MS + 1000 - NANOSECONDS.toMillis(System.nanoTime() - beforeTheDisable)));
      assertTrue(disabling.isAlive(), "with no connection timeout, the stop gave the replica offline message up");

      rs.giveCredit(1);
      disabling.join(stopBoundMS());
      assertFalse(disabling.isAlive(), "disable() did not end once the replication server gave credit");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(ReplicaOfflineMsg.class), 1, "the replica offline message was given up");
    }
    finally
    {
      cleanUp(rs, disabling);
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
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
    // The credit comes about a second into the stop: well within the time the stop gives.
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
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
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
    }
  }

  /**
   * Once the stop gave a change up, the broker writes nothing more: the replica offline message
   * carries the newest CSN of the replica, and written after a change it gave up it tells the
   * replication server that the replica has nothing more to send up to that CSN. A broker started
   * again finds the replica ahead of its replication server, and the recovery of its session
   * publishes the change given up; then it writes as before.
   */
  @Test(timeOut = 120_000)
  public void nothingIsWrittenAfterAChangeTheStopGaveUp() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    // The stop waits for the lock until the change gives up: long enough for the case to see it.
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
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

      /* That is over once the broker is started again. The replication server has only the first
       * change, and the domain the second as well: the recovery of the new session republishes it,
       * with the credit of the window of that session. */
      domain.enable();
      waitForConnection(domain);
      rs.waitForUpdates(2);
      final List<CSN> csns = rs.receivedUpdateCSNs();
      assertTrue(csns.get(1).isNewerThan(csns.get(0)) && csns.get(1).getServerId() == DS_ID,
          "the change the stop gave up was not republished: " + csns);
      waitForTheRecoveryToEnd();

      rs.giveCredit(1);
      assertEquals(replaceDescription("third"), ResultCode.SUCCESS);
      rs.waitForUpdates(3);
    }
    finally
    {
      cleanUp(rs, disabling, writer);
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
    }
  }

  /**
   * A restart of the session stops the broker as a disable does, and the change the stop gives up
   * is republished by the recovery of the next session. Unlike enable(), which loads the state of
   * the domain again and repairs it from the historical information of the entries, a restart
   * keeps the state in memory: the change is known to be missing only if that state took it
   * although the broker did not write it.
   */
  @Test(timeOut = 120_000)
  public void aSessionRestartRepublishesTheChangeTheStopGaveUp() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    Thread writer = null;
    Thread restarting = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      assertEquals(replaceDescription("first"), ResultCode.SUCCESS);
      rs.waitForUpdates(1);

      final AtomicReference<ResultCode> second = new AtomicReference<>();
      writer = startThread("writer", () -> second.set(replaceDescription("second")));
      rs.waitForWindowProbes(1);
      restarting = startThread("restart", domain::restartService);
      restarting.join(stopBoundMS());
      assertFalse(restarting.isAlive(), "the restart of the session waits for window credit");
      writer.join(stopBoundMS());
      assertEquals(second.get(), ResultCode.SUCCESS);

      waitForConnection(domain);
      rs.waitForUpdates(2);
      final List<CSN> csns = rs.receivedUpdateCSNs();
      assertTrue(csns.get(1).isNewerThan(csns.get(0)) && csns.get(1).getServerId() == DS_ID,
          "the change the stop gave up was not republished: " + csns);
      assertEquals(rs.count(ReplicaOfflineMsg.class), 0,
          "the replica offline message was written after a change the stop gave up");
      waitForTheRecoveryToEnd();

      rs.giveCredit(1);
      assertEquals(replaceDescription("third"), ResultCode.SUCCESS);
      rs.waitForUpdates(3);
    }
    finally
    {
      cleanUp(rs, restarting, writer);
    }
  }

  /**
   * A stop gives up only what finds no credit: with the window open, the replica offline message
   * still goes out. And once started again, the broker waits for credit as it did before it was ever
   * stopped: a stop does not leave behind a broker which gives a publish up as soon as the window is
   * closed, nor one whose session is closed once the stop is past the deadline it had.
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
      /* Nor past the time the stop gave the replication server to give credit and then to read: a
       * flag the stop left would end the wait by then, and a close the stop left due the session. */
      final long pastTheStopMS = 2 * MultimasterReplication.getConnectionTimeoutMS() + 1000
          - NANOSECONDS.toMillis(System.nanoTime() - stopped);
      Thread.sleep(Math.max(0, pastTheStopMS));
      assertTrue(writer.isAlive(), "a publish gave the change up while nobody stops the broker");
      assertEquals(rs.sessionsStarted(), 2, "the session of the broker started again was closed");

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

  /**
   * Once a publish gave its message up for the stop, the next one is refused even with the window
   * open: the stop publishes a change which finds no credit by its deadline, then waits for the
   * credit the replication server gives right after, and publishes a second change, which a broker
   * that only gave up the first would write. Nor do the messages of a total update, which need no
   * credit, go out: an import would be told it was done with an entry missing.
   */
  @Test(timeOut = 120_000)
  public void aStopWritesNothingAfterAGiveUpEvenWithCredit() throws Exception
  {
    // The stop waits for the case to read the credit: well before the session is closed for it.
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    rs.start();
    final CSNGenerator csns = new CSNGenerator(OTHER_DS_ID, 0);
    final AtomicReference<ReplicationBroker> brokerRef = new AtomicReference<>();
    final AtomicBoolean publishedWithCredit = new AtomicBoolean();
    final ReplicationBroker broker = new ReplicationBroker(new DummyReplicationDomain(getGenerationId(baseDN))
    {
      @Override
      public void publishReplicaOfflineMsg()
      {
        final ReplicationBroker stopping = brokerRef.get();
        stopping.publish(newDeleteMsg(csns));
        try
        {
          rs.giveCredit(1);
          readTheCredit(stopping);
        }
        catch (Exception e)
        {
          throw new IllegalStateException(e);
        }
        stopping.publish(newDeleteMsg(csns));
        stopping.publish(new EntryMsg(OTHER_DS_ID, WithholdingReplicationServer.RS_ID, new byte[16], 1));
        stopping.publish(new DoneMsg(OTHER_DS_ID, WithholdingReplicationServer.RS_ID));
        publishedWithCredit.set(true);
      }
    }, new ServerState(), newFakeCfg(baseDN, OTHER_DS_ID, rs.getPort()), getReplSessionSecurity());
    brokerRef.set(broker);
    Thread stopping = null;
    try
    {
      // A read of the session which finds nothing returns in a while rather than never.
      connect(broker, 200);
      // The only credit of the window goes to a first change.
      broker.publish(newDeleteMsg(csns));
      rs.waitForUpdates(1);

      stopping = startThread("stop", broker::stop);
      stopping.join(stopBoundMS());
      assertFalse(stopping.isAlive(), "stop() waits for window credit a replication server which takes no update does not give");
      assertTrue(publishedWithCredit.get(), "the stop did not get to publish a change with the window open");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(LDAPUpdateMsg.class), 1, "a change was written after one the stop gave up");
      assertEquals(rs.count(EntryMsg.class) + rs.count(DoneMsg.class), 0,
          "a message of a total update was written after a change the stop gave up");
    }
    finally
    {
      cleanUp(rs, stopping);
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
    }
  }

  /**
   * A stop which overlaps one in progress keeps what that one gave up: once the first stop gave a
   * change up, it writes nothing more, even with the window open again, while a second stop runs
   * beside it - which a stop of its own, with no give-up yet, would let out.
   */
  @Test(timeOut = 120_000)
  public void aStopWhichOverlapsOneInProgressKeepsItsGiveUp() throws Exception
  {
    // The first stop waits for the case to read the credit: well before the session is closed for it.
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    rs.start();
    final CSNGenerator csns = new CSNGenerator(OTHER_DS_ID, 0);
    final AtomicReference<ReplicationBroker> brokerRef = new AtomicReference<>();
    final AtomicInteger stops = new AtomicInteger();
    final CountDownLatch secondStopRuns = new CountDownLatch(1);
    final CountDownLatch firstStopIsDone = new CountDownLatch(1);
    final AtomicReference<Thread> secondStop = new AtomicReference<>();
    final AtomicBoolean publishedWithCredit = new AtomicBoolean();
    final ReplicationBroker broker = new ReplicationBroker(new DummyReplicationDomain(getGenerationId(baseDN))
    {
      @Override
      public void publishReplicaOfflineMsg()
      {
        if (stops.incrementAndGet() > 1)
        {
          // The second stop waits beside the first until that one is done.
          secondStopRuns.countDown();
          await(firstStopIsDone);
          return;
        }
        try
        {
          final ReplicationBroker stopping = brokerRef.get();
          stopping.publish(newDeleteMsg(csns));
          secondStop.set(startThread("second stop", stopping::stop));
          await(secondStopRuns);
          rs.giveCredit(1);
          readTheCredit(stopping);
          stopping.publish(newDeleteMsg(csns));
          publishedWithCredit.set(true);
        }
        catch (Exception e)
        {
          throw new IllegalStateException(e);
        }
        finally
        {
          firstStopIsDone.countDown();
        }
      }
    }, new ServerState(), newFakeCfg(baseDN, OTHER_DS_ID, rs.getPort()), getReplSessionSecurity());
    brokerRef.set(broker);
    Thread stopping = null;
    try
    {
      // A read of the session which finds nothing returns in a while rather than never.
      connect(broker, 200);
      // The only credit of the window goes to a first change.
      broker.publish(newDeleteMsg(csns));
      rs.waitForUpdates(1);

      stopping = startThread("first stop", broker::stop);
      stopping.join(stopBoundMS());
      assertFalse(stopping.isAlive(), "the first stop did not end");
      assertTrue(publishedWithCredit.get(), "the first stop did not get to publish a change with the window open");
      secondStop.get().join(stopBoundMS());
      assertFalse(secondStop.get().isAlive(), "the second stop did not end");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(LDAPUpdateMsg.class), 1,
          "a change was written after one the stop gave up, once a second stop had begun");
    }
    finally
    {
      cleanUp(rs, stopping, secondStop.get());
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
    }
  }

  /**
   * A replication server which keeps the session up but reads nothing of it holds the write of a
   * local change in the socket once the buffers are full, under the lock of the pending changes
   * stop() needs, and with credit to spare there is no wait for credit for the stop to give up: the
   * disable has to end the write by closing the session - once the replication server had the
   * connection timeout twice over - and the change comes back with the recovery of the next session.
   */
  @Test(timeOut = 120_000)
  public void aDisableEndsAWriteToAReplicationServerWhichReadsNothing() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    final AtomicBoolean keepWriting = new AtomicBoolean(true);
    final AtomicInteger changes = new AtomicInteger();
    final AtomicReference<ResultCode> failed = new AtomicReference<>();
    Thread writer = null;
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      rs.stopReading();
      writer = startLargeChanges(keepWriting, changes, failed);
      waitForAWriteToBlock(writer, changes);
      keepWriting.set(false);

      final long beforeTheDisable = System.nanoTime();
      disabling = startThread("disable", domain::disable);
      disabling.join(stopBoundMS());
      final long disableMS = NANOSECONDS.toMillis(System.nanoTime() - beforeTheDisable);
      assertFalse(disabling.isAlive(), "disable() waits for a write the replication server does not read");
      assertTrue(disableMS >= 2 * LONG_CONNECTION_TIMEOUT_MS, "disable() gave the replication server "
          + disableMS + " ms, not twice the connection timeout of " + LONG_CONNECTION_TIMEOUT_MS + " ms");
      // Half a connection timeout above: the bound of the join leaves room for three times it.
      assertTrue(disableMS < 2 * LONG_CONNECTION_TIMEOUT_MS + LONG_CONNECTION_TIMEOUT_MS / 2,
          "disable() gave the replication server " + disableMS
          + " ms, more than two and a half times the connection timeout of " + LONG_CONNECTION_TIMEOUT_MS + " ms");
      // The disable joined the listener, whose read the close of the session ended.
      assertEquals(badDisconnectionsLogged(), 0,
          "the close of the session by the stop was reported as one by the replication server");
      writer.join(stopBoundMS());
      assertFalse(writer.isAlive(), "the local change is still blocked in its write");
      assertNull(failed.get(), "a local change failed");

      // The change cut short ends what the replication server reads, and nothing comes after it.
      rs.resumeReadingTheSessionTheDomainClosed();
      rs.waitForTheSessionToEnd();
      final List<CSN> beforeTheEnable = rs.receivedUpdateCSNs();
      assertEquals(rs.count(ReplicaOfflineMsg.class), 0,
          "the replica offline message was written after a change the stop gave up");
      assertEquals(rs.sessionsStarted(), 1, "a session was opened again while the domain was being stopped");

      /* The disable left the state of the domain in its backend only, and the enable loads it: its
       * newest change of this replica is the one the stop gave up, as no other change came after it. */
      domain.enable();
      waitForConnection(domain);
      final CSN blocked = domain.getServerState().getCSN(DS_ID);
      assertNotNull(blocked, "the domain has no change of its own in its state");
      assertFalse(beforeTheEnable.contains(blocked), "the change blocked in its write was written: " + beforeTheEnable);
      newTimer().repeatUntilSuccess(() -> assertTrue(rs.receivedUpdateCSNs().contains(blocked),
          "the change the stop gave up, " + blocked + ", was not republished: the replication server has "
          + rs.receivedUpdateCSNs()));
    }
    finally
    {
      cleanUp(rs, disabling, writer);
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
    }
  }

  /**
   * The write which blocks can be one of the stop itself, with no lock of the pending changes in
   * the way: the stop ends it the same way. The close of the session wakes the listener, which then
   * waits for the end of the stop rather than opens a session again: the broker writes nothing more
   * once the stop gave up, and the stop would wait for that connection.
   */
  @Test(timeOut = 120_000)
  public void aStopEndsItsOwnWriteAndOpensNoSessionAgain() throws Exception
  {
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    rs.start();
    final CSNGenerator csns = new CSNGenerator(OTHER_DS_ID, 0);
    final AtomicReference<ReplicationBroker> brokerRef = new AtomicReference<>();
    final AtomicReference<Thread> listenerRef = new AtomicReference<>();
    final AtomicInteger entries = new AtomicInteger();
    final AtomicInteger sessionsWhenTheListenerWaits = new AtomicInteger(-1);
    final ReplicationBroker broker = new ReplicationBroker(new DummyReplicationDomain(getGenerationId(baseDN))
    {
      @Override
      public void publishReplicaOfflineMsg()
      {
        final ReplicationBroker stopping = brokerRef.get();
        // Entries need no credit: they are written until one blocks, and the stop ends that one.
        long publishMS;
        do
        {
          final long beforeThePublish = System.nanoTime();
          stopping.publish(new EntryMsg(OTHER_DS_ID, WithholdingReplicationServer.RS_ID,
              new byte[LARGE_MESSAGE_SIZE], entries.incrementAndGet()));
          publishMS = NANOSECONDS.toMillis(System.nanoTime() - beforeThePublish);
        }
        while (publishMS < SHORT_CONNECTION_TIMEOUT_MS && entries.get() < MAX_LARGE_MESSAGES);
        try
        {
          newTimer().repeatUntilSuccess(() -> assertTrue(rs.sessionsStarted() > 1
              || waitsIn(listenerRef.get(), ReplicationBroker.class, "reStart"),
              "the listener neither opened a session again nor waits for the end of the stop"));
        }
        catch (Exception e)
        {
          throw new IllegalStateException(e);
        }
        sessionsWhenTheListenerWaits.set(rs.sessionsStarted());
        stopping.publish(newDeleteMsg(csns));
      }
    }, new ServerState(), newFakeCfg(baseDN, OTHER_DS_ID, rs.getPort()), getReplSessionSecurity());
    brokerRef.set(broker);
    Thread listener = null;
    Thread stopping = null;
    try
    {
      connect(broker, 0);
      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      listener = startThread("listener", () -> {
        try
        {
          while (broker.receive() != null)
          {
            // What the replication server sends needs no answer here.
          }
        }
        catch (SocketTimeoutException e)
        {
          throw new IllegalStateException(e);
        }
      });
      listenerRef.set(listener);
      rs.stopReading();

      final long beforeTheStop = System.nanoTime();
      stopping = startThread("stop", broker::stop);
      stopping.join(stopBoundMS());
      final long stopMS = NANOSECONDS.toMillis(System.nanoTime() - beforeTheStop);
      assertFalse(stopping.isAlive(), "stop() waits for a write the replication server does not read");
      assertTrue(entries.get() < MAX_LARGE_MESSAGES, "no write blocked after " + entries.get() + " entries");
      assertTrue(stopMS >= 2 * SHORT_CONNECTION_TIMEOUT_MS, "stop() gave the replication server " + stopMS
          + " ms, not twice the connection timeout of " + SHORT_CONNECTION_TIMEOUT_MS + " ms");
      assertEquals(sessionsWhenTheListenerWaits.get(), 1,
          "the listener opened a session again while the broker was being stopped");
      listener.join(stopBoundMS());
      assertFalse(listener.isAlive(), "the listener did not end with the stop");
      assertEquals(badDisconnectionsLogged(), 0,
          "the close of the session by the stop was reported as one by the replication server");

      rs.resumeReadingTheSessionTheDomainClosed();
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(LDAPUpdateMsg.class), 0, "a change was written after the entry the stop gave up");
      assertFalse(broker.isConnected(), "the broker still runs its session after it was stopped");
    }
    finally
    {
      cleanUp(rs, stopping, listener);
    }
  }

  /**
   * A connection under way when the watchdog of the stop gives up leaves it no session to close: the
   * listener lost the session in the stop, and connects again holding the lock of the connection
   * phase, while replication servers which take a connection but answer none cost it the connection
   * timeout each. A publish of the stop which waited for that lock must not write to the session the
   * connection then opens: one which the replication server does not read would hold the stop, with
   * nothing left to end it. The entry here goes to a replication server which reads, and must not be
   * written at all.
   */
  @Test(timeOut = 120_000)
  public void aConnectionUnderWayWhenTheStopGivesUpGetsNothingWritten() throws Exception
  {
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    rs.start();
    final List<SilentReplicationServer> silentServers = new ArrayList<>();
    final SortedSet<String> urls = new TreeSet<>(Collections.singleton("127.0.0.1:" + rs.getPort()));
    for (int i = 0; i < SILENT_SERVERS; i++)
    {
      final SilentReplicationServer silent = new SilentReplicationServer();
      silent.start();
      silentServers.add(silent);
      urls.add("127.0.0.1:" + silent.getPort());
    }
    final DomainFakeCfg config = new DomainFakeCfg(baseDN, OTHER_DS_ID, urls);
    config.setHeartbeatInterval(100000);
    final AtomicReference<ReplicationBroker> brokerRef = new AtomicReference<>();
    final AtomicReference<Thread> listenerRef = new AtomicReference<>();
    final ReplicationBroker broker = new ReplicationBroker(new DummyReplicationDomain(getGenerationId(baseDN))
    {
      @Override
      public void publishReplicaOfflineMsg()
      {
        try
        {
          for (SilentReplicationServer silent : silentServers)
          {
            silent.answerNothing();
          }
          // The replication server ends the session in order, and the listener connects again.
          rs.closeTheSession();
          newTimer().repeatUntilSuccess(() -> assertTrue(
              isIn(listenerRef.get(), ReplicationBroker.class, "collectReplicationServersInfo"),
              "the listener does not connect again"));
        }
        catch (Exception e)
        {
          throw new IllegalStateException(e);
        }
        // Waits for the lock the connection holds.
        brokerRef.get().publish(new EntryMsg(OTHER_DS_ID, WithholdingReplicationServer.RS_ID, new byte[16], 1));
      }
    }, new ServerState(), config, getReplSessionSecurity());
    brokerRef.set(broker);
    Thread listener = null;
    Thread stopping = null;
    try
    {
      connect(broker, 0);
      listener = startThread("listener", () -> {
        try
        {
          while (broker.receive() != null)
          {
            // What the replication server sends needs no answer here.
          }
        }
        catch (SocketTimeoutException e)
        {
          throw new IllegalStateException(e);
        }
      });
      listenerRef.set(listener);

      final long beforeTheStop = System.nanoTime();
      final Thread stop = startThread("stop", broker::stop);
      stopping = stop;
      newTimer().repeatUntilSuccess(() -> assertTrue(
          stop.getState() == Thread.State.BLOCKED && isIn(stop, ReplicationBroker.class, "publish"),
          "the entry of the stop does not wait for the lock of the connection phase"));
      // Past the deadline of the watchdog, which then found the connection still under way.
      Thread.sleep(Math.max(0, 2 * SHORT_CONNECTION_TIMEOUT_MS + 500
          - NANOSECONDS.toMillis(System.nanoTime() - beforeTheStop)));
      assertFalse(watchdogRuns(), "the watchdog of the stop did not act at its deadline");
      assertTrue(isIn(listener, ReplicationBroker.class, "connectAsDataServer"),
          "the connection ended before the watchdog of the stop acted: the case tests nothing");

      stop.join(stopBoundMS());
      assertFalse(stop.isAlive(), "stop() did not end once the connection did");
      assertEquals(rs.sessionsStarted(), 2, "the connection under way did not open its session");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(EntryMsg.class), 0,
          "the stop wrote to the session a connection opened once the stop had given up");
      listener.join(stopBoundMS());
      assertFalse(listener.isAlive(), "the listener did not end with the stop");
    }
    finally
    {
      for (SilentReplicationServer silent : silentServers)
      {
        silent.shutdown();
      }
      cleanUp(rs, stopping, listener);
    }
  }

  /**
   * A give-up for credit closes nothing: a replication server which then drops the session is
   * reported as one which did, as it is with no stop at all. Only a close this server makes - by the
   * watchdog of a stop, or by the heartbeat monitor - is not reported, as the close is marked begun
   * before the sockets are closed.
   */
  @Test(timeOut = 120_000)
  public void aDropByTheReplicationServerAfterAGiveUpIsStillReported() throws Exception
  {
    final WithholdingReplicationServer rs = new WithholdingReplicationServer();
    rs.start();
    final CSNGenerator csns = new CSNGenerator(OTHER_DS_ID, 0);
    final AtomicReference<ReplicationBroker> brokerRef = new AtomicReference<>();
    final AtomicReference<Thread> listenerRef = new AtomicReference<>();
    final AtomicInteger reportedInTheStop = new AtomicInteger(-1);
    final ReplicationBroker broker = new ReplicationBroker(new DummyReplicationDomain(getGenerationId(baseDN))
    {
      @Override
      public void publishReplicaOfflineMsg()
      {
        // No credit: the change is given up at the credit deadline of the stop.
        brokerRef.get().publish(newDeleteMsg(csns));
        try
        {
          rs.dropTheSession();
          // The listener reports what ended its read before it waits for the end of the stop.
          newTimer().repeatUntilSuccess(() -> assertTrue(waitsIn(listenerRef.get(), ReplicationBroker.class, "reStart"),
              "the listener does not wait for the end of the stop"));
        }
        catch (Exception e)
        {
          throw new IllegalStateException(e);
        }
        reportedInTheStop.set(badDisconnectionsLogged());
      }
    }, new ServerState(), newFakeCfg(baseDN, OTHER_DS_ID, rs.getPort()), getReplSessionSecurity());
    brokerRef.set(broker);
    Thread listener = null;
    Thread stopping = null;
    try
    {
      connect(broker, 0);
      listener = startThread("listener", () -> {
        try
        {
          while (broker.receive() != null)
          {
            // What the replication server sends needs no answer here.
          }
        }
        catch (SocketTimeoutException e)
        {
          throw new IllegalStateException(e);
        }
      });
      listenerRef.set(listener);
      // The only credit of the window goes to a first change.
      broker.publish(newDeleteMsg(csns));
      rs.waitForUpdates(1);
      TestCaseUtils.ERROR_TEXT_WRITER.clear();

      stopping = startThread("stop", broker::stop);
      stopping.join(stopBoundMS());
      assertFalse(stopping.isAlive(), "stop() did not end");
      assertTrue(reportedInTheStop.get() > 0,
          "the replication server dropped the session after a give-up for credit, and nothing reported it");
      assertEquals(rs.count(LDAPUpdateMsg.class), 1, "the change was written with no credit");
    }
    finally
    {
      cleanUp(rs, stopping, listener);
    }
  }

  /**
   * A replication server which is gone without a reset reads nothing, sends no heartbeat and answers
   * no connection. Once a write to it blocks, the heartbeat monitor still has to close the session,
   * not wait for that write, which nothing but TCP would end: the change then ends, and once the
   * replication server is back, the domain connects again and its changes reach it, the one cut
   * short included.
   */
  @Test(timeOut = 120_000)
  public void theHeartbeatMonitorEndsAWriteToAReplicationServerWhichIsGone() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    final AtomicBoolean keepWriting = new AtomicBoolean(true);
    final AtomicInteger changes = new AtomicInteger();
    final AtomicReference<ResultCode> failed = new AtomicReference<>();
    Thread writer = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs, HEARTBEAT_INTERVAL_MS);
      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      rs.goAway();
      final long away = System.nanoTime();
      writer = startLargeChanges(keepWriting, changes, failed);
      waitForAWriteToBlock(writer, changes);
      keepWriting.set(false);
      final long blocked = System.nanoTime();

      // The monitor gives up two or three intervals after the last heartbeat.
      writer.join(4 * HEARTBEAT_INTERVAL_MS + 10000);
      final long released = System.nanoTime();
      assertFalse(writer.isAlive(), "the write still blocks once the heartbeats of the replication server stopped");
      assertTrue(NANOSECONDS.toMillis(released - away) >= 2 * HEARTBEAT_INTERVAL_MS, "the write of change "
          + changes.get() + ", blocked " + NANOSECONDS.toMillis(blocked - away) + " ms after the replication server went away, ended "
          + NANOSECONDS.toMillis(released - away) + " ms after it: before the heartbeat monitor could give the session up");
      assertNull(failed.get(), "a local change failed");

      rs.resumeReading();
      waitForConnection(domain);
      final CSN last = domain.getServerState().getCSN(DS_ID);
      assertNotNull(last, "the domain has no change of its own in its state");
      newTimer().repeatUntilSuccess(() -> assertTrue(rs.receivedUpdateCSNs().contains(last),
          "the change cut short, " + last + ", did not reach the replication server: it has "
          + rs.receivedUpdateCSNs()));
      assertTrue(rs.sessionsStarted() > 1, "the domain kept the session whose heartbeats stopped");
      // The listener connected again: it is past what it reports of the read the close ended.
      assertEquals(badDisconnectionsLogged(), 0,
          "the close of the session by the heartbeat monitor was reported as one by the replication server");
    }
    finally
    {
      cleanUp(rs, writer);
    }
  }

  @Test(timeOut = 120_000)
  public void failoverTriesAnotherDiscoveryResponderWhenTheFormerRsRefusesConnection() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer former = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer survivor = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    final AtomicInteger survivorStarts = new AtomicInteger();
    final CountDownLatch survivorRetry = new CountDownLatch(1);
    final CountDownLatch finishRetry = new CountDownLatch(1);
    try
    {
      survivor.differentGroup = true;
      survivor.start();
      final LDAPReplicationDomain domain = startDomain(former, 100000, survivor.getAddress());
      assertEquals(domain.getRsServerId(), former.serverId);
      newTimer().repeatUntilSuccess(() -> assertNotNull(former.session.get()));
      assertEquals(replaceDescription("already on both replication servers"), ResultCode.SUCCESS);
      former.waitForUpdates(1);
      survivor.state.update(former.receivedUpdateCSNs().get(0));
      // The former RS has the latest local change, so it wins discovery even with both RSs in the same group.
      assertEquals(replaceDescription("before failover"), ResultCode.SUCCESS);
      former.waitForUpdates(2);
      final CSN beforeFailover = former.receivedUpdateCSNs().get(1);

      survivor.differentGroup = false;
      former.beforeStartReply = () -> {
        // The accepted socket can answer discovery after the listener stops accepting connections.
        former.listenSocket.close();
        return null;
      };
      survivor.beforeStartReply = () -> {
        if (survivorStarts.incrementAndGet() == 2)
        {
          survivorRetry.countDown();
          await(finishRetry);
        }
        return null;
      };
      former.closeTheSession();

      await(survivorRetry);
      assertFalse(domain.hasConnectionError(), "the broker declared isolation before trying the discovery responder");
      finishRetry.countDown();
      newTimer().repeatUntilSuccess(() -> {
        assertEquals(domain.getRsServerId(), survivor.serverId);
        assertFalse(domain.hasConnectionError());
      });
      assertEquals(replaceDescription("after failover to the discovery responder"), ResultCode.SUCCESS);
      survivor.waitForUpdates(2);
      assertEquals(survivor.receivedUpdateCSNs(), List.of(beforeFailover, domain.getServerState().getCSN(DS_ID)));
      assertEquals(survivorStarts.get(), 2, "the survivor must receive one discovery probe and one session start");
    }
    finally
    {
      finishRetry.countDown();
      try
      {
        survivor.shutdown();
      }
      finally
      {
        cleanUp(former);
      }
    }
  }

  @Test(timeOut = 120_000)
  public void reconnectClosesTheSessionWhenTopologyTimesOut() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      newTimer().repeatUntilSuccess(() -> assertNotNull(rs.session.get()));
      final Session originalSession = rs.session.get();

      final CompletableFuture<ReplicationMsg> nextRead = new CompletableFuture<>();
      rs.readInsteadOfSendingTopology.set(nextRead);
      rs.dropTheSession();

      // Observe the broker closing the provisional socket before the fixture is shut down.
      final Throwable failure = expectThrows(ExecutionException.class, () -> nextRead.get(30, SECONDS)).getCause();
      assertTrue(failure instanceof EOFException
          || (failure instanceof SocketException && String.valueOf(failure.getMessage()).startsWith("Connection reset")),
          "expected EOF or connection reset on the provisional session, got " + failure);

      newTimer().repeatUntilSuccess(() -> {
        final Session reconnected = rs.session.get();
        assertNotNull(reconnected, "the replication server did not establish a later session");
        assertNotSame(reconnected, originalSession);
        assertTrue(domain.isConnected());
        assertFalse(domain.hasConnectionError());
      });
    }
    finally
    {
      cleanUp(rs);
    }
  }

  @DataProvider
  public Object[][] assuredReplaySessions()
  {
    // A null replacement leaves the original session open until the replay completes.
    return new Object[][] { { true, null }, { true, true }, { true, false }, { false, true } };
  }

  @Test(dataProvider = "assuredReplaySessions", timeOut = 120_000)
  public void assuredReplayAcknowledgesOnlyItsOriginalSession(boolean sourceInGroup, Boolean replacementInGroup)
      throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final DN dn = DN.valueOf("cn=assured-switch," + baseDN);
    TestCaseUtils.addEntry("dn: " + dn, "objectClass: person", "cn: assured-switch", "sn: assured-switch");
    final CSN csn = new CSNGenerator(OTHER_DS_ID, 0).newCSN();
    final DeleteMsg update = new DeleteMsg(dn, csn, getEntryUUID(dn));
    update.setAssured(true);
    update.setAssuredMode(AssuredMode.SAFE_READ_MODE);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    final AtomicReference<Runnable> switchSender = new AtomicReference<>();
    try
    {
      rs.differentGroup = !sourceInGroup;
      final LDAPReplicationDomain domain = startDomain(rs);
      final ReplicationBroker broker = brokerOf(domain);
      assertEquals(broker.getRsGroupId() == domain.getGroupId(), sourceInGroup);
      final Session original = broker.getSession();
      PausePreParsePlugin.pause(OperationType.DELETE, dn);
      newTimer().repeatUntilSuccess(() -> assertNotNull(rs.session.get()));
      rs.session.get().publish(update);
      assertTrue(PausePreParsePlugin.awaitPaused(OperationType.DELETE, 30, SECONDS), "replay did not reach the backend");
      assertTrue(original.closeForSwitch(switchSender::set));
      if (replacementInGroup != null)
      {
        runSwitchSender(switchSender);
        newTimer().repeatUntilSuccess(() -> assertEquals(rs.count(StopMsg.class), 1));
        rs.differentGroup = !replacementInGroup;
        rs.closeTheSession();
        // The same RS ID on a new socket must not inherit the old session's ack.
        newTimer().repeatUntilSuccess(() ->
            assertTrue(domain.isConnected() && broker.getSession() != original && rs.sessionsStarted() == 2));
        assertEquals(broker.getRsGroupId() == domain.getGroupId(), replacementInGroup.booleanValue());
      }

      PausePreParsePlugin.release(OperationType.DELETE);
      assertMonitorAttrValueEventually(baseDN, "replayed-updates", 1, "the assured replay did not finish");
      assertNull(DirectoryServer.getEntry(dn));
      if (replacementInGroup != null)
      {
        assertEquals(domain.getAssuredSrReceivedUpdatesAcked(), 0, "the undelivered ack was counted as sent");
        assertEquals(domain.getAssuredSrReceivedUpdatesNotAcked(), sourceInGroup ? 1 : 0,
            "only the source session's group decides whether an ack was due");
        domain.disable();
        rs.waitForTheSessionToEnd();
        assertEquals(rs.count(AckMsg.class), 0, "the replacement session received the previous session's ack");
      }
      else
      {
        newTimer().repeatUntilSuccess(() -> assertNotNull(rs.lastAck, "the old RS did not receive its ack"));
        assertEquals(rs.lastAck.getCSN(), csn);
        assertFalse(rs.lastAck.hasReplayError());
        assertEquals(domain.getAssuredSrReceivedUpdatesAcked(), 1);
        assertEquals(domain.getAssuredSrReceivedUpdatesNotAcked(), 0);
        runSwitchSender(switchSender);
        newTimer().repeatUntilSuccess(() -> assertEquals(rs.count(StopMsg.class), 1));
        assertEquals(rs.count(AckMsg.class), 1);
      }
    }
    finally
    {
      PausePreParsePlugin.release(OperationType.DELETE);
      runSwitchSender(switchSender);
      cleanUp(rs);
    }
  }

  @Test(timeOut = 120_000)
  public void gracefulStopSendsOfflineWhileTheSwitchSenderIsPending() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer rs = new WithholdingReplicationServer(LARGE_WINDOW);
    final AtomicReference<Runnable> switchSender = new AtomicReference<>();
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(rs);
      assertTrue(brokerOf(domain).getSession().closeForSwitch(switchSender::set));
      disabling = startThread("disable during planned switch", domain::disable);
      newTimer().repeatUntilSuccess(() -> assertNotNull(rs.lastOffline, "the healthy old RS did not receive offline"));
      assertEquals(rs.lastOffline.getCSN().getServerId(), DS_ID);
      runSwitchSender(switchSender);
      disabling.join(stopBoundMS());
      assertFalse(disabling.isAlive(), "the graceful stop did not finish");
      rs.waitForTheSessionToEnd();
      assertEquals(rs.count(ReplicaOfflineMsg.class), 1);
      assertEquals(rs.sessionsStarted(), 1);
    }
    finally
    {
      runSwitchSender(switchSender);
      cleanUp(rs, disabling);
    }
  }

  @Test(timeOut = 120_000)
  public void gracefulStopSendsOfflineOnTheReplacementAfterTheOldStop() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
    final WithholdingReplicationServer oldRS = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer nextRS = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    Thread disabling = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(oldRS, 100000, nextRS.getAddress());
      nextRS.start();
      oldRS.advertiseRebalance(nextRS, domain);
      newTimer().repeatUntilSuccess(() -> assertEquals(oldRS.count(StopMsg.class), 1));

      final CSN previous = offlineAnnouncementOfTheReplica();
      final Thread stop = startThread("disable after switch StopMsg", domain::disable);
      disabling = stop;
      // Answer the old StopMsg as soon as the concurrent disable tries to publish offline.
      newTimer().repeatUntilSuccess(() -> {
        final CSN announced = offlineAnnouncementOfTheReplica();
        assertTrue(!stop.isAlive() || announced != null && !announced.equals(previous));
      });
      oldRS.shutdown();

      stop.join(stopBoundMS());
      assertFalse(stop.isAlive(), "the graceful stop did not finish within its budget");
      assertEquals(oldRS.count(ReplicaOfflineMsg.class), 0, "offline was written after the old StopMsg");
      newTimer().repeatUntilSuccess(() -> assertNotNull(nextRS.lastOffline, "the replacement RS did not receive offline"));
      nextRS.waitForTheSessionToEnd();
      assertEquals(nextRS.lastOffline.getCSN().getServerId(), DS_ID);
      assertEquals(nextRS.lastOffline.getCSN(), offlineAnnouncementOfTheReplica());
      assertTrue(nextRS.received.indexOf(ReplicaOfflineMsg.class) < nextRS.received.indexOf(StopMsg.class),
          "offline must precede the replacement session's StopMsg");
    }
    finally
    {
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
      try
      {
        nextRS.shutdown();
      }
      finally
      {
        cleanUp(oldRS, disabling);
      }
    }
  }

  private enum ReplacementRecovery { COMPLETE, OLD_WINDOW_EXHAUSTED, OUT_OF_CREDIT, SEARCH_FAILED }

  @DataProvider
  public Object[][] replacementRecoveries()
  {
    return new Object[][] { { ReplacementRecovery.COMPLETE }, { ReplacementRecovery.OLD_WINDOW_EXHAUSTED },
      { ReplacementRecovery.OUT_OF_CREDIT }, { ReplacementRecovery.SEARCH_FAILED } };
  }

  @Test(dataProvider = "replacementRecoveries", timeOut = 120_000)
  public void shutdownOrdersOfflineAfterMissingReplacementHistory(ReplacementRecovery recovery) throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    setConnectionTimeout(LONG_CONNECTION_TIMEOUT_MS);
    final boolean exhaustedOldWindow = recovery == ReplacementRecovery.OLD_WINDOW_EXHAUSTED;
    final WithholdingReplicationServer oldRS = new WithholdingReplicationServer(exhaustedOldWindow ? 3 : LARGE_WINDOW);
    final boolean pauseRecovery = exhaustedOldWindow || recovery == ReplacementRecovery.OUT_OF_CREDIT;
    final WithholdingReplicationServer nextRS = new WithholdingReplicationServer(pauseRecovery ? 1 : LARGE_WINDOW, 4);
    Thread shutdown = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(oldRS, 100000, nextRS.getAddress());
      assertEquals(replaceDescription("shared"), ResultCode.SUCCESS);
      oldRS.waitForUpdates(1);
      final CSN shared = oldRS.receivedUpdateCSNs().get(0);
      final AtomicBoolean searchFailed = new AtomicBoolean();
      if (recovery == ReplacementRecovery.SEARCH_FAILED)
      {
        // This RS needs history older than the in-memory messages. Fail its backend search.
        nextRS.state.update(new CSN(shared.getTime() - 1, 1, DS_ID));
        domain.setMissingChangesSearchHook(search -> {
          searchFailed.set(true);
          search.setResultCode(ResultCode.OPERATIONS_ERROR);
        });
      }
      else
      {
        nextRS.state.update(shared);
      }
      assertEquals(replaceDescription("missing one"), ResultCode.SUCCESS);
      assertEquals(replaceDescription("missing two"), ResultCode.SUCCESS);
      oldRS.waitForUpdates(3);
      final List<CSN> missing = oldRS.receivedUpdateCSNs().subList(1, 3);

      nextRS.start();
      oldRS.advertiseRebalance(nextRS, domain);
      newTimer().repeatUntilSuccess(() -> assertEquals(oldRS.count(StopMsg.class), 1));
      final CSN previous = offlineAnnouncementOfTheReplica();
      shutdown = startThread("shutdown after switch StopMsg", domain::shutdown);
      newTimer().repeatUntilSuccess(() -> {
        final CSN announced = offlineAnnouncementOfTheReplica();
        assertTrue(announced != null && !announced.equals(previous));
      });
      final CSN marker = offlineAnnouncementOfTheReplica();
      oldRS.shutdown();
      if (exhaustedOldWindow)
      {
        // The replacement needs more credit for the second missing update. The announcement
        // must remain pending until it can send both updates and then the offline marker.
        newTimer().repeatUntilSuccess(() -> assertTrue(offlineAnnouncementOfTheReplica() == null
            || nextRS.count(ModifyMsg.class) == 1 && nextRS.count(WindowProbeMsg.class) > 0));
        assertEquals(offlineAnnouncementOfTheReplica(), marker, "shutdown withdrew the marker during recovery");
        assertNull(nextRS.lastOffline);
        nextRS.giveCredit(2);
      }

      shutdown.join(stopBoundMS());
      assertFalse(shutdown.isAlive(), "shutdown exceeded its budget");
      assertEquals(nextRS.sessionsStarted(), 1);
      nextRS.waitForTheSessionToEnd();
      assertEquals(oldRS.count(ReplicaOfflineMsg.class), 0);
      if (recovery == ReplacementRecovery.COMPLETE || exhaustedOldWindow)
      {
        assertEquals(nextRS.receivedUpdateCSNs(), missing);
        assertEquals(nextRS.count(ReplicaOfflineMsg.class), 1);
        assertEquals(nextRS.lastOffline.getCSN(), marker);
        assertTrue(nextRS.received.lastIndexOf(ModifyMsg.class) < nextRS.received.indexOf(ReplicaOfflineMsg.class));
      }
      else
      {
        assertNull(nextRS.lastOffline, "a replacement missing history received the offline marker");
        if (recovery == ReplacementRecovery.SEARCH_FAILED)
        {
          assertTrue(searchFailed.get(), "recovery did not reach the history search");
          assertTrue(nextRS.receivedUpdateCSNs().isEmpty());
        }
        else
        {
          assertEquals(nextRS.receivedUpdateCSNs(), missing.subList(0, 1));
        }
        assertNull(offlineAnnouncementOfTheReplica(), "the undelivered marker was not withdrawn");
      }
    }
    finally
    {
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
      try
      {
        nextRS.shutdown();
      }
      finally
      {
        cleanUp(oldRS, shutdown);
      }
    }
  }

  private static ReplicationBroker brokerOf(LDAPReplicationDomain domain) throws ReflectiveOperationException
  {
    final Field field = ReplicationDomain.class.getDeclaredField("broker");
    field.setAccessible(true);
    return (ReplicationBroker) field.get(domain);
  }

  private static void runSwitchSender(AtomicReference<Runnable> pending)
  {
    final Runnable sender = pending.getAndSet(null);
    if (sender != null)
    {
      sender.run();
    }
  }

  @Test(timeOut = 120_000)
  public void plannedRebalanceReplaysTheOldRsAndPublishesOnTheNewRs() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final DN deleteDN = DN.valueOf("cn=before-switch," + baseDN);
    TestCaseUtils.addEntry("dn: " + deleteDN, "objectClass: top", "objectClass: person",
        "cn: before-switch", "sn: before-switch");
    final String entryUUID = getEntryUUID(deleteDN);
    final WithholdingReplicationServer oldRS = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer nextRS = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    Thread writer = null;
    try
    {
      final LDAPReplicationDomain domain = startDomain(oldRS, 500, nextRS.getAddress());
      assertEquals(domain.getRsServerId(), oldRS.serverId);
      nextRS.start();
      oldRS.advertiseRebalance(nextRS, domain);

      newTimer().repeatUntilSuccess(() -> assertEquals(oldRS.count(StopMsg.class), 1));
      assertEquals(domain.getRsServerId(), oldRS.serverId, "the old RS has not replied yet");

      oldRS.goAway(); // A closing RS stops publishing heartbeats.
      // The RS is still draining its side. Replay this update before replacing its session.
      final CSN remoteCSN = new CSNGenerator(OTHER_DS_ID, 0).newCSN();
      oldRS.session.get().publish(new DeleteMsg(deleteDN, remoteCSN, entryUUID));
      newTimer().repeatUntilSuccess(() -> {
        assertNull(DirectoryServer.getEntry(deleteDN));
        assertEquals(domain.getServerState().getCSN(OTHER_DS_ID), remoteCSN);
      });

      final AtomicReference<ResultCode> result = new AtomicReference<>();
      final Thread localWrite = startThread("write during planned switch",
          () -> result.set(replaceDescription("during planned switch")));
      writer = localWrite;
      newTimer().repeatUntilSuccess(() -> assertTrue(Arrays.stream(localWrite.getStackTrace()).anyMatch(frame ->
          frame.getClassName().equals(ReplicationBroker.class.getName()) && frame.getMethodName().equals("publish")),
          "the local write did not wait for a session to publish on"));
      // Leave the peer draining past the configured connection timeout.
      localWrite.join(SHORT_CONNECTION_TIMEOUT_MS + 500);
      assertEquals(domain.getRsServerId(), oldRS.serverId, "the old RS is still finishing its sends");
      assertEquals(oldRS.receivedUpdateCSNs(), Collections.emptyList());
      assertNull(result.get(), "the write completed before the session could publish it");

      oldRS.closeTheSession();
      new TestTimer.Builder().maxSleep(30, SECONDS).sleepTimes(50, MILLISECONDS).toTimer()
          .repeatUntilSuccess(() -> assertEquals(domain.getRsServerId(), nextRS.serverId));
      localWrite.join(10000);
      assertEquals(result.get(), ResultCode.SUCCESS);
      final CSN localCSN = domain.getServerState().getCSN(DS_ID);
      assertNotNull(localCSN);
      nextRS.waitForUpdates(1);
      assertEquals(nextRS.receivedUpdateCSNs(), Collections.singletonList(localCSN));
    }
    finally
    {
      setConnectionTimeout(SHORT_CONNECTION_TIMEOUT_MS);
      try
      {
        nextRS.shutdown();
      }
      finally
      {
        cleanUp(oldRS, writer);
      }
    }
  }

  @Test(timeOut = 120_000)
  public void plannedRebalanceKeepsLdifDataAndReplaysUpdatesAfterAnInitializationHeader() throws Exception
  {
    final DN ldifBaseDN = DN.valueOf("o=switch-ldif");
    final DN retainedDN = DN.valueOf("cn=retain," + ldifBaseDN);
    final DN replayDN = DN.valueOf("cn=delete," + ldifBaseDN);
    final DN backendDN = DN.valueOf("ds-cfg-backend-id=switchLdif,cn=Backends,cn=config");
    final Path ldifFile = Path.of(TestCaseUtils.createTempFile(
        "dn: " + ldifBaseDN, "objectClass: top", "objectClass: organization", "o: switch-ldif", ""));
    TestCaseUtils.addEntry("dn: " + backendDN, "objectClass: top", "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-backend", "objectClass: ds-cfg-ldif-backend", "ds-cfg-backend-id: switchLdif",
        "ds-cfg-enabled: true", "ds-cfg-java-class: org.opends.server.backends.LDIFBackend",
        "ds-cfg-writability-mode: enabled", "ds-cfg-base-dn: " + ldifBaseDN, "ds-cfg-ldif-file: " + ldifFile);
    final WithholdingReplicationServer oldRS = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer nextRS = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    try
    {
      TestCaseUtils.addEntry("dn: " + retainedDN, "objectClass: top", "objectClass: person",
          "cn: retain", "sn: retain");
      TestCaseUtils.addEntry("dn: " + replayDN, "objectClass: top", "objectClass: person",
          "cn: delete", "sn: delete");
      final String replayUUID = getEntryUUID(replayDN);

      final LDAPReplicationDomain domain = startDomain(ldifBaseDN, oldRS, 100000, nextRS.getAddress());
      final AtomicBoolean importClaimed = new AtomicBoolean();
      domain.setImportClaimHook(() -> importClaimed.set(true));
      assertEquals(domain.getRsServerId(), oldRS.serverId);
      nextRS.start();
      oldRS.advertiseRebalance(nextRS, domain);
      newTimer().repeatUntilSuccess(() -> assertEquals(oldRS.count(StopMsg.class), 1));

      // Hold the stop reply while the DS deals with the remaining frames from the old RS.
      final Session retiring = oldRS.session.get();
      assertTrue(retiring.publish(new InitializeTargetMsg(ldifBaseDN, OTHER_DS_ID, DS_ID, OTHER_DS_ID, 1, 4), null));
      final CSN replayCSN = new CSNGenerator(OTHER_DS_ID, 0).newCSN();
      assertTrue(retiring.publish(new DeleteMsg(replayDN, replayCSN, replayUUID), null));
      newTimer().repeatUntilSuccess(() -> {
        assertFalse(importClaimed.get(), "the old session entered full initialization");
        assertEquals(domain.getServerState().getCSN(OTHER_DS_ID), replayCSN, "the update has not replayed");
      });

      assertEquals(domain.getRsServerId(), oldRS.serverId, "the replay must finish before the stop reply");
      assertNotNull(DirectoryServer.getEntry(retainedDN));
      assertNull(DirectoryServer.getEntry(replayDN));
      final String storedLdif = Files.readString(ldifFile);
      assertTrue(storedLdif.contains("dn: " + retainedDN), "the active LDIF lost an existing entry");
      assertFalse(storedLdif.contains("dn: " + replayDN), "the active LDIF did not record the incremental delete");

      oldRS.goAway();
      oldRS.closeTheSession();
      newTimer().repeatUntilSuccess(() -> assertEquals(domain.getRsServerId(), nextRS.serverId));
    }
    finally
    {
      try
      {
        nextRS.shutdown();
      }
      finally
      {
        try
        {
          oldRS.shutdown();
        }
        finally
        {
          MultimasterReplication.deleteDomain(ldifBaseDN);
          TestCaseUtils.deleteEntry(backendDN);
          Files.deleteIfExists(ldifFile);
          Files.deleteIfExists(Path.of(ldifFile + ".old"));
        }
      }
    }
  }

  @Test(timeOut = 120_000)
  public void plannedRebalanceAbortsOnlyTheOldSessionWhenItDoesNotReply() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer oldRS = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer nextRS = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    try
    {
      final LDAPReplicationDomain domain = startDomain(oldRS, 100000, nextRS.getAddress());
      assertEquals(domain.getRsServerId(), oldRS.serverId);
      nextRS.start();

      final List<String> records = errorLogRecordsOf(() -> {
        oldRS.advertiseRebalance(nextRS, domain);
        newTimer().repeatUntilSuccess(() -> assertEquals(oldRS.count(StopMsg.class), 1));
        // Keep the session open without answering the StopMsg or accepting a new connection.
        oldRS.goAway();
        new TestTimer.Builder().maxSleep(30, SECONDS).sleepTimes(50, MILLISECONDS).toTimer()
            .repeatUntilSuccess(() -> assertEquals(domain.getRsServerId(), nextRS.serverId));
        return null;
      });
      assertTrue(records.stream().anyMatch(record -> record.contains("Timed out switching from replication server")),
          "the switch completed without the expected timeout diagnostic");
      assertEquals(replaceDescription("after the old session timed out"), ResultCode.SUCCESS);
      nextRS.waitForUpdates(1);
    }
    finally
    {
      try
      {
        nextRS.shutdown();
      }
      finally
      {
        cleanUp(oldRS);
      }
    }
  }

  private enum FormerRsDuringFailover
  {
    OFFLINE,
    TIMES_OUT,
    DISAPPEARS_AFTER_PROBE
  }

  @DataProvider
  public Object[][] failoverStartReplyTimeouts()
  {
    return new Object[][] {
        { 0, 1, FormerRsDuringFailover.OFFLINE }, // The discovery probe times out.
        { 1, 1, FormerRsDuringFailover.OFFLINE }, // The elected RS times out after a successful probe.
        { 0, 2, FormerRsDuringFailover.OFFLINE }, // The discovery timeout is followed by another timeout.
        { 1, 2, FormerRsDuringFailover.OFFLINE }, // The elected RS times out again.
        { 0, 1, FormerRsDuringFailover.TIMES_OUT },
        { 0, 1, FormerRsDuringFailover.DISAPPEARS_AFTER_PROBE }
    };
  }

  @Test(dataProvider = "failoverStartReplyTimeouts", timeOut = 120_000)
  public void failoverAllowsOneRetryOfTheSurvivingRs(int successfulProbes, int timedOutReplies,
      FormerRsDuringFailover formerRs) throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer first = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer second = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    // The configured URLs are sorted, so put the original RS first.
    final boolean firstSortsFirst = first.getAddress().compareTo(second.getAddress()) < 0;
    final WithholdingReplicationServer oldRS = firstSortsFirst ? first : second;
    final WithholdingReplicationServer nextRS = firstSortsFirst ? second : first;
    final DelayedFailoverHandshake handshake = new DelayedFailoverHandshake(successfulProbes, timedOutReplies,
        formerRs == FormerRsDuringFailover.DISAPPEARS_AFTER_PROBE ? oldRS.listenSocket : null);
    final DelayedFailoverHandshake oldHandshake = new DelayedFailoverHandshake(0, 2);
    Thread writer = null;
    try
    {
      // Answer startup probes while ensuring the original RS wins the election.
      nextRS.differentGroup = true;
      nextRS.start();
      final LDAPReplicationDomain domain = startDomain(oldRS, 100000, nextRS.getAddress());
      assertEquals(domain.getRsServerId(), oldRS.serverId);
      nextRS.differentGroup = false;
      nextRS.delayedFailoverHandshake = handshake;
      if (formerRs == FormerRsDuringFailover.OFFLINE)
      {
        oldRS.shutdown();
      }
      else
      {
        newTimer().repeatUntilSuccess(() -> assertNotNull(oldRS.session.get()));
        if (formerRs == FormerRsDuringFailover.TIMES_OUT)
        {
          oldRS.delayedFailoverHandshake = oldHandshake;
        }
        oldRS.dropTheSession();
      }

      await(handshake.nextStartReceived);
      final AtomicReference<ResultCode> duringFailover = new AtomicReference<>();
      if (timedOutReplies == 1)
      {
        assertFalse(domain.hasConnectionError(), "the broker declared isolation before retrying the surviving RS");
        if (formerRs == FormerRsDuringFailover.TIMES_OUT)
        {
          assertEquals(oldHandshake.starts.get(), 1, "the broker retried the former RS before the survivor");
        }
        final Thread localWrite = startThread("write during failover retry",
            () -> duringFailover.set(replaceDescription("during failover retry")));
        writer = localWrite;
        newTimer().repeatUntilSuccess(() -> assertTrue(isIn(localWrite, ReplicationBroker.class, "publish"),
            "the write did not reach replication during failover"));
      }
      else
      {
        assertTrue(domain.hasConnectionError(), "the broker did not report isolation after the retry expired");
        assertEquals(replaceDescription("while isolated"), ResultCode.UNWILLING_TO_PERFORM);
      }
      handshake.sendNextReply.countDown();

      newTimer().repeatUntilSuccess(() -> {
        assertEquals(domain.getRsServerId(), nextRS.serverId);
        assertFalse(domain.hasConnectionError(), "the broker did not clear isolation after reconnecting");
      });
      if (writer != null)
      {
        assertEquals(handshake.starts.get(), successfulProbes + timedOutReplies + 1,
            "the surviving RS should have connected on the extra attempt");
        writer.join(10_000);
        assertEquals(duringFailover.get(), ResultCode.SUCCESS);
        final CSN firstCSN = domain.getServerState().getCSN(DS_ID);
        assertNotNull(firstCSN);
        assertEquals(replaceDescription("after failover"), ResultCode.SUCCESS);
        final CSN secondCSN = domain.getServerState().getCSN(DS_ID);
        assertTrue(firstCSN.isOlderThan(secondCSN));
        nextRS.waitForUpdates(2);
        assertEquals(nextRS.receivedUpdateCSNs(), Arrays.asList(firstCSN, secondCSN));
      }
      else
      {
        assertEquals(replaceDescription("after isolation"), ResultCode.SUCCESS);
        nextRS.waitForUpdates(1);
      }
    }
    finally
    {
      handshake.sendNextReply.countDown();
      oldHandshake.sendNextReply.countDown();
      try
      {
        nextRS.shutdown();
      }
      finally
      {
        cleanUp(oldRS, writer);
      }
    }
  }

  @Test(timeOut = 120_000)
  public void failoverRetriesElectedRsAfterStartReplyTimeout() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    final WithholdingReplicationServer oldRS = new WithholdingReplicationServer(LARGE_WINDOW);
    final WithholdingReplicationServer otherRS = new WithholdingReplicationServer(LARGE_WINDOW, 4);
    final DelayedFailoverHandshake oldHandshake = new DelayedFailoverHandshake(1, 1);
    final DelayedFailoverHandshake otherHandshake = new DelayedFailoverHandshake(0, 2);
    try
    {
      otherRS.differentGroup = true;
      otherRS.start();
      final LDAPReplicationDomain domain = startDomain(oldRS, 100000, otherRS.getAddress());
      assertEquals(domain.getRsServerId(), oldRS.serverId);
      newTimer().repeatUntilSuccess(() -> assertNotNull(oldRS.session.get()));

      otherRS.differentGroup = false;
      otherRS.delayedFailoverHandshake = otherHandshake;
      oldRS.delayedFailoverHandshake = oldHandshake;
      oldRS.dropTheSession();

      await(oldHandshake.nextStartReceived);
      assertFalse(domain.hasConnectionError(), "the broker declared isolation before retrying the elected RS");
      assertEquals(otherHandshake.starts.get(), 1, "the other RS should only receive the discovery probe");
      oldHandshake.sendNextReply.countDown();

      waitForConnection(domain);
      assertEquals(domain.getRsServerId(), oldRS.serverId);
      assertFalse(domain.hasConnectionError());
      assertEquals(replaceDescription("after retrying the elected RS"), ResultCode.SUCCESS);
      oldRS.waitForUpdates(1);
    }
    finally
    {
      oldHandshake.sendNextReply.countDown();
      otherHandshake.sendNextReply.countDown();
      try
      {
        otherRS.shutdown();
      }
      finally
      {
        cleanUp(oldRS);
      }
    }
  }

  private LDAPReplicationDomain startDomain(WithholdingReplicationServer rs) throws Exception
  {
    // Heartbeats far apart: the session stays up whatever the replication server reads.
    return startDomain(rs, 100000);
  }

  private LDAPReplicationDomain startDomain(WithholdingReplicationServer rs, long heartbeatIntervalMS,
      String... otherServers) throws Exception
  {
    return startDomain(baseDN, rs, heartbeatIntervalMS, otherServers);
  }

  private LDAPReplicationDomain startDomain(DN domainBaseDN, WithholdingReplicationServer rs, long heartbeatIntervalMS,
      String... otherServers) throws Exception
  {
    rs.start();
    final SortedSet<String> servers = new TreeSet<>(Arrays.asList(otherServers));
    servers.add(rs.getAddress());
    final DomainFakeCfg conf = new DomainFakeCfg(domainBaseDN, DS_ID, servers);
    conf.setHeartbeatInterval(heartbeatIntervalMS);
    final LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(conf);
    domain.start();
    waitForConnection(domain);
    return domain;
  }

  private void waitForConnection(final LDAPReplicationDomain domain) throws Exception
  {
    newTimer().repeatUntilSuccess(() -> assertTrue(domain.isConnected(), "the domain is not connected"));
  }

  /**
   * Waits for the thread which publishes the changes the replication server misses to end: until
   * then the domain does not push its own changes, but leaves them to that thread.
   */
  private static void waitForTheRecoveryToEnd() throws Exception
  {
    final String name = "Replica DS(" + DS_ID + ") missing change publisher";
    newTimer().repeatUntilSuccess(() -> {
      for (Thread thread : Thread.getAllStackTraces().keySet())
      {
        assertFalse(thread.isAlive() && thread.getName().startsWith(name), "the recovery still runs");
      }
    });
  }

  /** Sets the connection timeout of the replication provider, which the stop of a broker reads. */
  private static void setConnectionTimeout(long timeoutMS)
  {
    assertEquals(getRootConnection().processModify(
        modifyRequest(PROVIDER_DN, REPLACE, "ds-cfg-connection-timeout", timeoutMS + " ms")).getResultCode(),
        ResultCode.SUCCESS);
    assertEquals(MultimasterReplication.getConnectionTimeoutMS(), timeoutMS);
  }

  private DeleteMsg newDeleteMsg(CSNGenerator csns)
  {
    final CSN csn = csns.newCSN();
    return new DeleteMsg(DN.valueOf("cn=" + csn + "," + baseDN), csn, "entryUUID-" + csn);
  }

  /** No domain reads the session of the broker provided: the credit counts once the case reads it. */
  private static void readTheCredit(final ReplicationBroker broker) throws Exception
  {
    newTimer().repeatUntilSuccess(() -> {
      try
      {
        broker.receive();
      }
      catch (SocketTimeoutException e)
      {
        // Nothing more to read.
      }
      assertEquals(broker.getCurrentSendWindow(), 1, "the credit the replication server gave");
    });
  }

  private static void await(final CountDownLatch latch)
  {
    try
    {
      assertTrue(latch.await(30, SECONDS), "a thread of the case did not get where the case waits for it");
    }
    catch (InterruptedException e)
    {
      throw new IllegalStateException(e);
    }
  }

  /** Waits for stop() to wait for the lock of the pending changes, which a local change holds. */
  private static void waitForTheOfflineMessageToWaitForTheLock(final Thread disabling) throws Exception
  {
    newTimer().repeatUntilSuccess(() -> assertTrue(disabling.getState() == Thread.State.BLOCKED
        && isIn(disabling, PendingChanges.class, "putReplicaOfflineMsg"),
        "stop() does not wait for the lock of the pending changes"));
  }

  /**
   * Returns how many times the error log says, since it was cleared, that the session was closed by
   * the replication server rather than by this server: what the listener reports when a read fails
   * on a session whose close it does not find begun.
   */
  private static int badDisconnectionsLogged()
  {
    int count = 0;
    for (String record : TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
    {
      count += record.contains("msgID=" + WARN_REPLICATION_SERVER_BADLY_DISCONNECTED.ordinal() + " ") ? 1 : 0;
    }
    return count;
  }

  /**
   * Starts a thread which makes changes of {@link #LARGE_MESSAGE_SIZE} until it is told to stop,
   * counting them, and keeping the first result which is no success.
   */
  private Thread startLargeChanges(final AtomicBoolean keepWriting, final AtomicInteger changes,
      final AtomicReference<ResultCode> failed)
  {
    return startThread("writer", () -> {
      while (keepWriting.get() && changes.get() < MAX_LARGE_MESSAGES)
      {
        final ResultCode result = replaceDescription(changes.get() + LARGE_VALUE);
        if (result != ResultCode.SUCCESS)
        {
          failed.compareAndSet(null, result);
        }
        changes.incrementAndGet();
      }
    });
  }

  /**
   * Waits for the writer to stay in a write to the session, the number of the changes it made
   * standing still: the socket buffers are full, and the replication server reads nothing.
   */
  private static void waitForAWriteToBlock(final Thread writer, final AtomicInteger changes) throws Exception
  {
    // Two seconds in a row, so that a pause of the collector in the middle of a write is not taken for it.
    new TestTimer.Builder().maxSleep(20, SECONDS).sleepTimes(50, MILLISECONDS).toTimer().repeatUntilSuccess(() -> {
      assertTrue(writer.isAlive(), "no write blocked after " + changes.get() + " changes");
      final int before = changes.get();
      for (int second = 0; second < 2; second++)
      {
        Thread.sleep(1000);
        assertTrue(changes.get() == before && isIn(writer, Session.class, "send"),
            "no write blocks after " + changes.get() + " changes");
      }
    });
  }

  /** Tells whether the watchdog of a stop of the broker of {@link #OTHER_DS_ID} runs. */
  private static boolean watchdogRuns()
  {
    final String name = "Replica DS(" + OTHER_DS_ID + ") watchdog of the stop";
    for (Thread thread : Thread.getAllStackTraces().keySet())
    {
      if (thread.isAlive() && thread.getName().startsWith(name))
      {
        return true;
      }
    }
    return false;
  }

  /** Tells whether the thread waits, for a lock or for a time, inside the method provided. */
  private static boolean waitsIn(final Thread thread, final Class<?> type, final String method)
  {
    final Thread.State state = thread.getState();
    return (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) && isIn(thread, type, method);
  }

  private static boolean isIn(final Thread thread, final Class<?> type, final String method)
  {
    for (StackTraceElement frame : thread.getStackTrace())
    {
      if (frame.getClassName().equals(type.getName()) && frame.getMethodName().equals(method))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the CSN of the announcement that the replica of the domain went offline which the
   * shutdown of a collocated replication server would wait for the forward of, if there is one.
   */
  private CSN offlineAnnouncementOfTheReplica() throws Exception
  {
    final Field shutdownSync = MultimasterReplication.class.getDeclaredField("dsrsShutdownSync");
    shutdownSync.setAccessible(true);
    final Field announcements = DSRSShutdownSync.class.getDeclaredField("replicaOfflineMsgs");
    announcements.setAccessible(true);
    final Map<?, ?> byDomain = (Map<?, ?>) announcements.get(shutdownSync.get(null));
    final Map<?, ?> byReplica = (Map<?, ?>) byDomain.get(baseDN);
    final Object announcement = byReplica != null ? byReplica.get(DS_ID) : null;
    if (announcement == null)
    {
      return null;
    }
    final Field csn = announcement.getClass().getDeclaredField("csn");
    csn.setAccessible(true);
    return (CSN) csn.get(announcement);
  }

  private ResultCode replaceDescription(String value)
  {
    return getRootConnection().processModify(modifyRequest(baseDN, REPLACE, "description", value))
        .getResultCode();
  }

  /**
   * How long disabling the domain may take: the stop gives the replication server as long as a
   * connection to it to give credit, and as long again to read what was written, and the bound adds
   * a margin far beyond what the stop needs on top of that, and far below what a stop which waits
   * for the replication server would take: forever. It is also far below the 30 seconds of
   * connection timeout the suite runs with.
   */
  private static long stopBoundMS()
  {
    return 2 * MultimasterReplication.getConnectionTimeoutMS() + 10000;
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

  /** Withholds start replies until the broker's read times out, then lets the test release the next reply. */
  private static final class DelayedFailoverHandshake
  {
    private final int successfulProbes;
    private final int timedOutReplies;
    private final ServerSocket formerRsListener;
    private final AtomicInteger starts = new AtomicInteger();
    private final CountDownLatch nextStartReceived = new CountDownLatch(1);
    private final CountDownLatch sendNextReply = new CountDownLatch(1);

    DelayedFailoverHandshake(int successfulProbes, int timedOutReplies)
    {
      this(successfulProbes, timedOutReplies, null);
    }

    DelayedFailoverHandshake(int successfulProbes, int timedOutReplies, ServerSocket formerRsListener)
    {
      this.successfulProbes = successfulProbes;
      this.timedOutReplies = timedOutReplies;
      this.formerRsListener = formerRsListener;
    }

    boolean respond(Session session) throws Exception
    {
      final int startNumber = starts.incrementAndGet();
      if (startNumber == 1 && formerRsListener != null)
      {
        // Discovery has already probed the former RS, which sorts before this one.
        formerRsListener.close();
      }
      final int attempt = startNumber - successfulProbes;
      if (attempt >= 1 && attempt <= timedOutReplies)
      {
        session.setSoTimeout(2 * MultimasterReplication.getConnectionTimeoutMS());
        session.receive(); // The broker closes the session after its read deadline.
        return false;
      }
      if (attempt == timedOutReplies + 1)
      {
        nextStartReceived.countDown();
        await(sendNextReply);
      }
      return true;
    }
  }

  /**
   * A replication server which keeps the session of the domain up and reads every message of it,
   * but gives no credit beyond the window it opens the session with - of one update, unless the
   * test opens a larger one - unless the test asks it to. Like a replication server, it reports the
   * changes it received as its state: a domain which finds itself ahead of it republishes what it
   * misses. It sends the heartbeats the domain asks for, stops reading its sessions - or goes away
   * altogether, heartbeats included - when the test asks it to, and serves each connection on a
   * thread of its own, as a replication server does: a session it does not read does not keep the
   * next connection waiting.
   */
  private static final class WithholdingReplicationServer extends Thread
  {
    private static final int RS_ID = 2;

    private final int serverId;
    private final int window;
    private final ServerSocket listenSocket;
    /** The latest session started, until it ends. */
    private final AtomicReference<Session> session = new AtomicReference<>();
    /** Every session being served, the handshakes included, for the shutdown to close. */
    private final List<Session> sessions = new CopyOnWriteArrayList<>();
    private final List<Thread> servers = new CopyOnWriteArrayList<>();
    private final AtomicInteger sessionsStarted = new AtomicInteger();
    /** The kinds of the messages received on the sessions, in the order they came. */
    private final List<Class<?>> received = new CopyOnWriteArrayList<>();
    /** The CSNs of the changes received on the sessions, in the order they came. */
    private final List<CSN> updateCSNs = new CopyOnWriteArrayList<>();
    private volatile AckMsg lastAck;
    private volatile ReplicaOfflineMsg lastOffline;
    private volatile boolean differentGroup;
    private final ServerState state = new ServerState();
    /** Guards {@link #reading} and the changes of {@link #away}. */
    private final Object readingLock = new Object();
    /** Whether the sessions read what the domain writes: each reads one message more once it is not. */
    private boolean reading = true;
    /** Whether this server is away: it then sends no heartbeat, and answers no connection either. */
    private volatile boolean away;
    private volatile Callable<Void> beforeStartReply;
    /** If set, read from the next session after StartSessionMsg instead of sending its topology. */
    private final AtomicReference<CompletableFuture<ReplicationMsg>> readInsteadOfSendingTopology =
        new AtomicReference<>();
    private volatile DelayedFailoverHandshake delayedFailoverHandshake;

    WithholdingReplicationServer() throws IOException
    {
      this(1);
    }

    WithholdingReplicationServer(int window) throws IOException
    {
      this(window, RS_ID);
    }

    WithholdingReplicationServer(int window, int serverId) throws IOException
    {
      super(ReplicationBrokerStopTest.class.getSimpleName() + " replication server");
      setDaemon(true);
      this.serverId = serverId;
      this.window = window;
      listenSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    int getPort()
    {
      return listenSocket.getLocalPort();
    }

    String getAddress()
    {
      return "127.0.0.1:" + getPort();
    }

    void advertiseRebalance(WithholdingReplicationServer nextRS, LDAPReplicationDomain domain) throws IOException
    {
      final long generationId = domain.getGenerationID();
      final byte groupId = domain.getGroupId();
      final DSInfo otherDS = new DSInfo(OTHER_DS_ID, "", serverId, generationId, ServerStatus.NORMAL_STATUS,
          false, AssuredMode.SAFE_DATA_MODE, (byte) 0, groupId, Collections.emptyList(), Collections.emptyList(),
          Collections.emptyList(), ProtocolVersion.getCurrentVersion());
      final Session current = session.get();
      current.publish(new TopologyMsg(Collections.singletonList(otherDS), Arrays.asList(
          new RSInfo(serverId, getAddress(), generationId, groupId, 1),
          new RSInfo(nextRS.serverId, nextRS.getAddress(), generationId, groupId, 1))));
      final MonitorMsg monitor = new MonitorMsg(serverId, DS_ID);
      monitor.setReplServerDbState(state.duplicate());
      assertTrue(current.publish(monitor, null));
      assertTrue(current.publish(monitor, null));
    }

    @Override
    public void run()
    {
      while (!listenSocket.isClosed())
      {
        final Socket socket;
        try
        {
          socket = listenSocket.accept();
        }
        catch (IOException e)
        {
          // The test closed this server.
          continue;
        }
        final Thread server = new Thread(() -> serve(socket), getName() + " session");
        server.setDaemon(true);
        servers.add(server);
        server.start();
      }
    }

    /**
     * Answers both phases of the handshake: the first connection of the domain only asks for the
     * state of this server and ends with a StopMsg, the one it keeps goes on to start a session.
     */
    private void serve(Socket socket)
    {
      Session s = null;
      try
      {
        // A server which is away answers no connection: the connection waits for it to come back.
        waitToBeBack();
        s = new ReplSessionSecurity().createServerSession(socket, MultimasterReplication.getConnectionTimeoutMS());
        sessions.add(s);
        final ServerStartMsg start = (ServerStartMsg) s.receive();
        final Callable<Void> hook = beforeStartReply;
        if (hook != null)
        {
          hook.call();
        }
        final DelayedFailoverHandshake failoverHandshake = delayedFailoverHandshake;
        if (failoverHandshake != null && !failoverHandshake.respond(s))
        {
          return;
        }
        final String url = getAddress();
        final byte groupId = differentGroup ? (byte) (start.getGroupId() + 1) : start.getGroupId();
        s.publish(new ReplServerStartMsg(serverId, url, start.getBaseDN(), window, state.duplicate(),
            start.getGenerationId(), start.getSSLEncryption(), groupId, 5000));
        if (!start.getSSLEncryption())
        {
          s.stopEncryption();
        }
        if (!(s.receive() instanceof StartSessionMsg))
        {
          return;
        }
        sessionsStarted.incrementAndGet();
        final CompletableFuture<ReplicationMsg> nextRead = readInsteadOfSendingTopology.getAndSet(null);
        if (nextRead != null)
        {
          try
          {
            // Give the broker its handshake timeout plus time to close the socket.
            s.setSoTimeout(MultimasterReplication.getConnectionTimeoutMS() + 5_000);
            nextRead.complete(s.receive());
          }
          catch (Exception e)
          {
            nextRead.completeExceptionally(e);
          }
          return;
        }
        s.publish(new TopologyMsg(new ArrayList<DSInfo>(), Collections.singletonList(
            new RSInfo(serverId, url, start.getGenerationId(), groupId, 1))));
        s.setSoTimeout(0);
        session.set(s);
        startHeartbeats(s, start.getHeartbeatInterval());
        while (true)
        {
          waitToRead();
          final ReplicationMsg msg = s.receive();
          if (msg instanceof LDAPUpdateMsg)
          {
            final CSN csn = ((LDAPUpdateMsg) msg).getCSN();
            updateCSNs.add(csn);
            state.update(csn);
          }
          received.add(msg.getClass());
          if (msg instanceof AckMsg)
          {
            lastAck = (AckMsg) msg;
          }
          else if (msg instanceof ReplicaOfflineMsg)
          {
            lastOffline = (ReplicaOfflineMsg) msg;
          }
        }
      }
      catch (Exception e)
      {
        // The domain closed the session, or the test closed this server.
      }
      finally
      {
        if (s != null)
        {
          session.compareAndSet(s, null);
          s.close();
          sessions.remove(s);
        }
      }
    }

    private void waitToRead() throws InterruptedException
    {
      synchronized (readingLock)
      {
        while (!reading)
        {
          readingLock.wait();
        }
      }
    }

    private void waitToBeBack() throws InterruptedException
    {
      synchronized (readingLock)
      {
        while (away)
        {
          readingLock.wait();
        }
      }
    }

    /**
     * Sends heartbeats on the session at the interval the domain asked for, as a replication server
     * does from a thread of its own, until the session ends - and none while this server is away.
     */
    private void startHeartbeats(final Session s, final long intervalMS)
    {
      if (intervalMS <= 0)
      {
        return;
      }
      final Thread heartbeats = new Thread(() -> {
        try
        {
          while (!s.closeInitiated())
          {
            Thread.sleep(intervalMS / 2);
            if (!away)
            {
              s.publish(new HeartbeatMsg());
            }
          }
        }
        catch (IOException | InterruptedException e)
        {
          // The session ended.
        }
      }, getName() + " heartbeats");
      heartbeats.setDaemon(true);
      heartbeats.start();
    }

    /** Stops reading the sessions, after the message each of them is reading; the heartbeats go on. */
    void stopReading()
    {
      synchronized (readingLock)
      {
        reading = false;
      }
    }

    /**
     * Goes away as a server whose host is down does, with no reset of its connections: it reads
     * nothing more, sends no heartbeat, and answers no new connection.
     */
    void goAway()
    {
      synchronized (readingLock)
      {
        away = true;
        reading = false;
      }
    }

    /** Reads the sessions again - and, if it was away, sends heartbeats and answers connections again. */
    void resumeReading()
    {
      synchronized (readingLock)
      {
        away = false;
        reading = true;
        readingLock.notifyAll();
      }
    }

    /**
     * Reads the sessions again once the domain closed its session while this server read nothing,
     * and ends that session at once. What the domain left queued at its end goes out once its TCP
     * finds the window open again: from the update the reads here send, or else from its next window
     * probe - and after a stall of seconds the probes are seconds apart, 13 s after a stall of 14 s
     * on Linux, longer than the wait for the end of the session. A heartbeat the other way has the
     * closed end answer with a reset at once: this server reads what reached it, and the read ends.
     */
    void resumeReadingTheSessionTheDomainClosed()
    {
      final Session closed = session.get();
      resumeReading();
      if (closed != null)
      {
        try
        {
          closed.publish(new HeartbeatMsg());
        }
        catch (IOException e)
        {
          // The reset came already.
        }
      }
    }

    /** Ends the latest session in order, with a StopMsg, as a replication server which stops does. */
    void closeTheSession()
    {
      session.get().close();
    }

    /** Drops the latest session with no StopMsg, as a replication server which fails does. */
    void dropTheSession()
    {
      session.get().abort(new IOException("the replication server drops the session"));
    }

    /** Returns how many sessions were started, the connections which only ask for the state left out. */
    int sessionsStarted()
    {
      return sessionsStarted.get();
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

    List<CSN> receivedUpdateCSNs()
    {
      return new ArrayList<>(updateCSNs);
    }

    void giveCredit(int credit) throws IOException
    {
      session.get().publish(new WindowMsg(credit));
    }

    /** Gives credit unless the domain ended the session, before this reads it or while this writes. */
    void giveCreditIfTheSessionIsUp(int credit)
    {
      final Session s = session.get();
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

    /**
     * Waits for the session to end: what the domain wrote to it has all been read by then - or, once
     * the session was ended by a reset, what of it reached this server.
     */
    void waitForTheSessionToEnd() throws Exception
    {
      newTimer().repeatUntilSuccess(() -> assertNull(session.get(), "the session of the domain is still up"));
    }

    void shutdown() throws Exception
    {
      listenSocket.close();
      resumeReading();
      for (Session s : sessions)
      {
        s.close();
      }
      join(30000);
      for (Thread server : servers)
      {
        server.join(30000);
      }
    }
  }

  /**
   * A replication server which takes each connection and, once told to, answers nothing on it, as
   * one which hangs does: a connection to it waits for the connection timeout in its SSL
   * negotiation. Until then it closes each connection at once, which fails it at once.
   */
  private static final class SilentReplicationServer extends Thread
  {
    private final ServerSocket listenSocket;
    private final List<Socket> held = new CopyOnWriteArrayList<>();
    private volatile boolean silent;

    SilentReplicationServer() throws IOException
    {
      super(ReplicationBrokerStopTest.class.getSimpleName() + " silent replication server");
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
          final Socket socket = listenSocket.accept();
          if (silent)
          {
            held.add(socket);
          }
          else
          {
            socket.close();
          }
        }
        catch (IOException e)
        {
          // The test closed this server.
        }
      }
    }

    void answerNothing()
    {
      silent = true;
    }

    void shutdown() throws Exception
    {
      listenSocket.close();
      for (Socket socket : held)
      {
        socket.close();
      }
      join(30000);
    }
  }
}
