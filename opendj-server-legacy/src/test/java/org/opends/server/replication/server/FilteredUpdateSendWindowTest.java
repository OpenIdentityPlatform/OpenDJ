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
import static org.opends.messages.ReplicationMessages.WARN_IGNORING_UPDATE_TO_DS_BADGENID;
import static org.opends.messages.ReplicationMessages.WARN_IGNORING_UPDATE_TO_RS;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.util.TestTimer;
import org.testng.annotations.Test;

/**
 * An update the writer of a session drops - {@code ServerWriter.isUpdateMsgFiltered()} - has
 * been charged a permit of the send window of the session by {@code ServerHandler.take()}, and
 * the peer, which never receives it, never gives that permit back: the writer must. It must not
 * count the update as sent either.
 * <p>
 * {@code ReplicationServerDomain.put()} does not queue an update for a peer the writer would drop
 * it for, so what reaches the filter of the writer is what the catch-up of a peer reads from the
 * changelog: every change of the backlog of a peer which connects behind, and one the filter
 * applies to.
 */
@SuppressWarnings("javadoc")
public class FilteredUpdateSendWindowTest extends ReplicationTestCase
{
  private static final int SOCKET_TIMEOUT_MS = 30000;
  /** The window the peer under test advertises, and so the send window of its session. */
  private static final int WINDOW_SIZE = 10;
  /**
   * The window every other end advertises. A test broker takes the credit of its own send window
   * only while it receives, which the publishing ones never do, so the replication server they
   * publish to must leave them room for every change they publish.
   */
  private static final int LARGE_WINDOW_SIZE = 100;
  /** The generation id the peer holds while it disagrees with the replication server. */
  private static final long OTHER_GENID = EMPTY_DN_GENID + 1;

  private static final int WRITING_RS_ID = 8411;
  private static final int PEER_RS_ID = 8412;
  private static final int DS_RS_ID = 8413;
  /** The replica whose changes the peer is behind on. */
  private static final int PUBLISHING_DS_ID = 71;
  /** The replica whose generation id differs from the one of the replication server. */
  private static final int BAD_GENID_DS_ID = 72;

  /**
   * A peer replication server whose generation id differs is sent none of the backlog of the
   * changelog its catch-up reads, a whole window of it here. Once the two agree on the generation
   * id - the peer re-advertises it in a TopologyMsg, as a reset of the generation id does, and
   * the session is not re-established - the next change must reach it.
   * <p>
   * Without the permits of the dropped changes given back, the send window of the session is
   * empty by then and the writer waits for a credit the peer, which has received nothing, never
   * sends: the change never leaves the replication server, and nothing but a new session gets
   * it moving.
   */
  @Test
  public void aPeerWhichAgreesOnTheGenerationIdAfterItsBacklogWasDroppedIsSentTheNextChange()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer writingRS = null;
    ReplicationServer peerRS = null;
    ReplicationBroker broker = null;
    try
    {
      final int writingPort = TestCaseUtils.findFreePort();
      writingRS = newReplicationServer("filteredUpdateWritingDb", WRITING_RS_ID, writingPort,
          LARGE_WINDOW_SIZE);
      broker = openReplicationSession(baseDN, PUBLISHING_DS_ID, LARGE_WINDOW_SIZE, writingPort,
          5000, EMPTY_DN_GENID);
      final ReplicationServerDomain writingDomain =
          writingRS.getReplicationServerDomain(baseDN, true);

      // the backlog: a whole send window of changes the peer has not seen
      final CSNGenerator csns = new CSNGenerator(PUBLISHING_DS_ID, 0);
      CSN lastBacklogCSN = null;
      for (int i = 0; i < WINDOW_SIZE; i++)
      {
        final DeleteMsg change = newDeleteMsg(csns.newCSN());
        broker.publish(change);
        lastBacklogCSN = change.getCSN();
      }
      waitForCovered(writingDomain, lastBacklogCSN);

      // the peer takes its generation id before it connects, so that it does not adopt this one
      final int peerPort = TestCaseUtils.findFreePort();
      peerRS = newReplicationServer("filteredUpdatePeerDb", PEER_RS_ID, peerPort, WINDOW_SIZE,
          "127.0.0.1:" + writingPort);
      final ReplicationServerDomain peerDomain = peerRS.getReplicationServerDomain(baseDN, true);
      peerDomain.changeGenerationId(OTHER_GENID);

      final ReplicationServerHandler peerHandler = waitForConnectedPeer(writingDomain);
      assertThat(peerHandler.getGenerationId())
          .as("the peer was to hold a generation id of its own when it connected")
          .isEqualTo(OTHER_GENID);
      waitForDropped(WARN_IGNORING_UPDATE_TO_RS, lastBacklogCSN);

      // the peer comes to agree, over the session it already has
      peerDomain.changeGenerationId(EMPTY_DN_GENID);
      waitForGenerationId(peerHandler, EMPTY_DN_GENID);

      final DeleteMsg nextChange = newDeleteMsg(csns.newCSN());
      broker.publish(nextChange);
      waitForCovered(peerDomain, nextChange.getCSN(), () ->
          "the change published once the peer agreed on the generation id never reached it: "
          + "the send window of its session is at "
          + monitorValue(peerHandler, "current-send-window") + " of " + WINDOW_SIZE);

      assertThat(monitorValue(peerHandler, "sent-updates"))
          .as("the peer was counted as sent the changes of the backlog it was not sent")
          .isEqualTo(1);
      assertThat(monitorValue(peerHandler, "current-send-window"))
          .as("the send window of the session is short of the one change the peer was sent, "
              + "and of nothing else")
          .isEqualTo(WINDOW_SIZE - 1);
    }
    finally
    {
      stop(broker);
      removeQuietly(peerRS);
      removeQuietly(writingRS);
    }
  }

  /**
   * A directory server whose generation id differs from the one of the replication server is in
   * BAD_GEN_ID_STATUS and sent none of the changes of its catch-up. The send window of its
   * session is left whole and nothing is counted as sent to it.
   * <p>
   * The directory server leaves BAD_GEN_ID_STATUS on a new session, which comes with a new send
   * window, so what this pins is the accounting rather than a stall - the accounting the writer
   * keeps for every update it drops, whoever the peer.
   */
  @Test
  public void aDirectoryServerInBadGenerationIdStatusKeepsItsSendWindowAndIsSentNothing()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    ReplicationBroker broker = null;
    ReplicationBroker badGenIdBroker = null;
    try
    {
      final int replicationPort = TestCaseUtils.findFreePort();
      replicationServer =
          newReplicationServer("filteredUpdateBadGenIdDb", DS_RS_ID, replicationPort,
              LARGE_WINDOW_SIZE);
      broker = openReplicationSession(baseDN, PUBLISHING_DS_ID, LARGE_WINDOW_SIZE,
          replicationPort, 5000, EMPTY_DN_GENID);
      final ReplicationServerDomain domain =
          replicationServer.getReplicationServerDomain(baseDN, true);

      final CSNGenerator csns = new CSNGenerator(PUBLISHING_DS_ID, 0);
      final int backlog = 3;
      CSN lastCSN = null;
      for (int i = 0; i < backlog; i++)
      {
        final DeleteMsg change = newDeleteMsg(csns.newCSN());
        broker.publish(change);
        lastCSN = change.getCSN();
      }
      waitForCovered(domain, lastCSN);

      badGenIdBroker = openReplicationSession(baseDN, BAD_GENID_DS_ID, WINDOW_SIZE,
          replicationPort, 5000, OTHER_GENID);
      waitForDropped(WARN_IGNORING_UPDATE_TO_DS_BADGENID, lastCSN);

      final DataServerHandler dsHandler = domain.getConnectedDSs().get(BAD_GENID_DS_ID);
      assertThat(dsHandler).as("the directory server is not connected anymore").isNotNull();
      /*
       * The drop is logged before its permit is given back, so the last one is waited for rather
       * than read at once. Without it given back, the window stays short of every change of the
       * backlog.
       */
      newTimer().repeatUntilSuccess(new TestTimer.CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          assertThat(monitorValue(dsHandler, "current-send-window"))
              .as("the send window of the session is short of changes the directory server "
                  + "was never sent")
              .isEqualTo(WINDOW_SIZE);
        }
      });
      assertThat(monitorValue(dsHandler, "sent-updates"))
          .as("the directory server was counted as sent the changes it was not sent")
          .isEqualTo(0);
    }
    finally
    {
      stop(badGenIdBroker, broker);
      removeQuietly(replicationServer);
    }
  }

  private ReplicationServer newReplicationServer(String dbDirName, int serverId,
      int replicationPort, int windowSize, String... peers) throws Exception
  {
    return new ReplicationServer(new ReplServerFakeConfiguration(replicationPort, dbDirName, 0,
        serverId, 0, windowSize, new TreeSet<>(Arrays.asList(peers))));
  }

  private static DeleteMsg newDeleteMsg(CSN csn)
  {
    return new DeleteMsg(DN.valueOf("uid=" + csn + "," + TEST_ROOT_DN_STRING), csn,
        "entry-uuid-" + csn);
  }

  /** The value of an attribute of the monitor entry of the handler. */
  static int monitorValue(ServerHandler handler, String attributeName)
  {
    for (Attribute attribute : handler.getMonitorData())
    {
      if (attributeName.equals(attribute.getAttributeDescription().getNameOrOID()))
      {
        return Integer.parseInt(attribute.iterator().next().toString());
      }
    }
    throw new AssertionError("no " + attributeName + " on the monitor entry of " + handler);
  }

  private static void waitForCovered(ReplicationServerDomain domain, CSN csn) throws Exception
  {
    waitForCovered(domain, csn, () -> "the replication server never recorded " + csn);
  }

  private static void waitForCovered(final ReplicationServerDomain domain, final CSN csn,
      final Supplier<String> description) throws Exception
  {
    newTimer().repeatUntilSuccess(new TestTimer.CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        if (!domain.getLatestServerState().cover(csn))
        {
          throw new AssertionError(description.get());
        }
      }
    });
  }

  private static ReplicationServerHandler waitForConnectedPeer(final ReplicationServerDomain domain)
      throws Exception
  {
    return newTimer().repeatUntilSuccess(new Callable<ReplicationServerHandler>()
    {
      @Override
      public ReplicationServerHandler call() throws Exception
      {
        final ReplicationServerHandler handler = domain.getConnectedRSs().get(PEER_RS_ID);
        assertThat(handler).as("the peer replication server never connected").isNotNull();
        return handler;
      }
    });
  }

  private static void waitForGenerationId(final ServerHandler handler, final long generationId)
      throws Exception
  {
    newTimer().repeatUntilSuccess(new TestTimer.CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertThat(handler.getGenerationId())
            .as("the replication server never learnt the generation id the peer took")
            .isEqualTo(generationId);
      }
    });
  }

  /**
   * Waits for the writer to have logged the drop of the change: the changes of a catch-up are
   * taken in order, so every change before it has been dropped as well by then.
   */
  private static void waitForDropped(
      final LocalizableMessageDescriptor.Arg7<?, ?, ?, ?, ?, ?, ?> drop, final CSN csn)
      throws Exception
  {
    newTimer().repeatUntilSuccess(new TestTimer.CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        boolean logged = false;
        for (String record : TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
        {
          logged |= record.contains("msgID=" + drop.ordinal()) && record.contains(csn.toString());
        }
        assertThat(logged).as("the writer never dropped %s", csn).isTrue();
      }
    });
  }

  private static TestTimer newTimer()
  {
    return new TestTimer.Builder()
        .maxSleep(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .sleepTimes(10, TimeUnit.MILLISECONDS)
        .toTimer();
  }

  /** Teardown must never mask the primary assertion failure. */
  private void removeQuietly(ReplicationServer replicationServer)
  {
    if (replicationServer == null)
    {
      return;
    }
    try
    {
      remove(replicationServer);
    }
    catch (Exception ignored)
    {
    }
  }
}
