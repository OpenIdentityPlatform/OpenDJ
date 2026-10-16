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

import static java.util.concurrent.TimeUnit.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the states {@link ReplicationDomainMonitor} gathers for the replication servers of a
 * domain, which the monitoring messages hand to the directory servers.
 * <p>
 * A directory server connected to a replication server checks on those messages whether its
 * replication server still holds its newest change, and leaves it when another one holds it and
 * it does not (issue #1198). The changes of a directory server reach the other replication
 * servers through the one it is connected to only, so none of them can hold one of its changes
 * which that one does not - unless the states compared were not taken at the same moment.
 */
@SuppressWarnings("javadoc")
public class ReplicationDomainMonitorTest extends DirectoryServerTestCase
{
  private static final DN BASE_DN = DN.valueOf("dc=example,dc=com");
  private static final int LOCAL_RS_ID = 1;
  private static final int PEER_RS_ID = 2;
  private static final int SILENT_RS_ID = 3;
  private static final int DS_ID = 10;
  private static final CSN FIRST = new CSN(1000, 1, DS_ID);
  private static final CSN FORWARDED = new CSN(1000, 2, DS_ID);
  private static final CSN AHEAD = new CSN(1000, 5, DS_ID);
  private static final long STEP_TIMEOUT_MS = 10000;

  /** What the reader of the session to a peer does once the monitor request has been sent to it. */
  private interface PeerReader
  {
    void run(ReplicationDomainMonitor monitor, Thread requester) throws Exception;
  }

  /** The state of the changelog of this replication server, which the mocked domain reports. */
  private AtomicReference<ServerState> changelog;
  /** Counted down once the monitor data is published. */
  private CountDownLatch published;
  private Map<Integer, Thread> readers;
  private AtomicReference<Throwable> readerFailure;

  @BeforeMethod
  public void setUp()
  {
    changelog = new AtomicReference<>(stateWith(FIRST));
    published = new CountDownLatch(1);
    readers = new ConcurrentHashMap<>();
    readerFailure = new AtomicReference<>();
  }

  /**
   * A change of a directory server connected to this replication server comes in while the
   * answers are waited for, and this replication server forwards it to its peer before the peer
   * answers: the state reported for this replication server must not be older than the one of its
   * peer on the changes of that directory server.
   */
  @Test
  public void aPeerDoesNotLookAheadOnTheChangesItGetsFromThisReplicationServer() throws Exception
  {
    final Map<Integer, PeerReader> peers = new LinkedHashMap<>();
    peers.put(PEER_RS_ID, (monitor, requester) ->
    {
      awaitTheWaitForTheAnswers(requester);
      changelog.set(stateWith(FORWARDED));
      monitor.receiveMonitorDataResponse(answer(PEER_RS_ID, FORWARDED), PEER_RS_ID);
    });

    final ReplicationDomainMonitorData data = recompute(peers);

    final CSN local = data.getRSStates(LOCAL_RS_ID).getCSN(DS_ID);
    final CSN peer = stateOf(data, PEER_RS_ID).getCSN(DS_ID);
    assertFalse(local.isOlderThan(peer), "RS(" + LOCAL_RS_ID + ") is reported at " + local.toStringUI()
        + ", behind RS(" + PEER_RS_ID + ") at " + peer.toStringUI()
        + ", which got the changes of DS(" + DS_ID + ") from it");
  }

  /**
   * A peer can hold changes of a directory server which this replication server does not, when
   * the directory server published them to that peer on an earlier session: the peer must still
   * be reported ahead then.
   */
  @Test
  public void aPeerWhichHoldsChangesThisReplicationServerLacksIsReportedAhead() throws Exception
  {
    final Map<Integer, PeerReader> peers = new LinkedHashMap<>();
    peers.put(PEER_RS_ID,
        (monitor, requester) -> monitor.receiveMonitorDataResponse(answer(PEER_RS_ID, AHEAD), PEER_RS_ID));

    final ReplicationDomainMonitorData data = recompute(peers);

    assertEquals(data.getRSStates(LOCAL_RS_ID).getCSN(DS_ID), FIRST);
    assertEquals(stateOf(data, PEER_RS_ID).getCSN(DS_ID), AHEAD);
  }

  /**
   * One peer answers at once, another one only once the wait for the answers is over, and changes
   * come in meanwhile: the peer which answered is not shown missing the changes which came in
   * while the other one was waited for, and the late answer does not go into the published data.
   */
  @Test
  public void aPeerWhichAnswersAfterTheWaitLeavesTheOthersUpToDate() throws Exception
  {
    final Map<Integer, PeerReader> peers = new LinkedHashMap<>();
    peers.put(PEER_RS_ID,
        (monitor, requester) -> monitor.receiveMonitorDataResponse(answer(PEER_RS_ID, FIRST), PEER_RS_ID));
    peers.put(SILENT_RS_ID, (monitor, requester) ->
    {
      awaitTheWaitForTheAnswers(requester);
      readers.get(PEER_RS_ID).join(STEP_TIMEOUT_MS);
      changelog.set(stateWith(FORWARDED));
      assertTrue(published.await(STEP_TIMEOUT_MS, MILLISECONDS), "the monitor data was not published");
      monitor.receiveMonitorDataResponse(answer(SILENT_RS_ID, FORWARDED), SILENT_RS_ID);
    });

    final ReplicationDomainMonitorData data = recompute(peers);

    assertEquals(data.getMissingChangesRS(PEER_RS_ID), 0, "RS(" + PEER_RS_ID + ") answered with every "
        + "change there was then, and is shown missing the ones which came in while RS(" + SILENT_RS_ID
        + ") was waited for");
    assertNull(data.getRSStates(SILENT_RS_ID),
        "the answer of RS(" + SILENT_RS_ID + "), which came after the wait, went into the published data");
  }

  /**
   * Gathers the monitor data of a domain with the provided peers, in this order: the reader of
   * the session to each of them runs once the monitor request is sent to it. The readers are done
   * when this returns.
   */
  private ReplicationDomainMonitorData recompute(Map<Integer, PeerReader> peerReaders) throws Exception
  {
    final ReplicationServerDomain domain = mock(ReplicationServerDomain.class);
    when(domain.getBaseDN()).thenReturn(BASE_DN);
    when(domain.getLocalRSServerId()).thenReturn(LOCAL_RS_ID);
    when(domain.getConnectedDSs()).thenReturn(Collections.<Integer, DataServerHandler> emptyMap());
    when(domain.getLatestServerState()).thenAnswer(invocation -> changelog.get().duplicate());
    final ReplicationDomainMonitor monitor = new ReplicationDomainMonitor(domain);

    final Map<Integer, ReplicationServerHandler> peers = new LinkedHashMap<>();
    for (Map.Entry<Integer, PeerReader> entry : peerReaders.entrySet())
    {
      final int peerId = entry.getKey();
      final PeerReader peerReader = entry.getValue();
      final ReplicationServerHandler peer = mock(ReplicationServerHandler.class);
      when(peer.getServerId()).thenReturn(peerId);
      doAnswer(invocation ->
      {
        final Thread requester = Thread.currentThread();
        final Thread reader = new Thread(() ->
        {
          try
          {
            peerReader.run(monitor, requester);
          }
          catch (Throwable e)
          {
            readerFailure.compareAndSet(null, e);
          }
        }, "reader of the session to RS(" + peerId + ")");
        reader.setDaemon(true);
        readers.put(peerId, reader);
        reader.start();
        return null;
      }).when(peer).send(any(ReplicationMsg.class));
      peers.put(peerId, peer);
    }
    when(domain.getConnectedRSs()).thenReturn(peers);

    final ReplicationDomainMonitorData data = monitor.recomputeMonitorData();
    published.countDown();
    for (Map.Entry<Integer, Thread> reader : readers.entrySet())
    {
      reader.getValue().join(STEP_TIMEOUT_MS);
      assertFalse(reader.getValue().isAlive(), "the reader of the session to RS(" + reader.getKey() + ") is stuck");
    }
    if (readerFailure.get() != null)
    {
      throw new AssertionError("a reader failed", readerFailure.get());
    }
    return data;
  }

  /** Returns once the requests are all sent, and the requester waits for the answers. */
  private static void awaitTheWaitForTheAnswers(Thread requester) throws InterruptedException
  {
    final long deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS;
    while (requester.getState() != Thread.State.TIMED_WAITING)
    {
      assertTrue(System.currentTimeMillis() < deadline, "the requester did not wait for the answers");
      Thread.sleep(1);
    }
  }

  private static ServerState stateOf(ReplicationDomainMonitorData data, int rsId)
  {
    final ServerState state = data.getRSStates(rsId);
    assertNotNull(state, "the answer of RS(" + rsId + ") did not come within the wait");
    return state;
  }

  private static MonitorMsg answer(int rsId, CSN newest)
  {
    final MonitorMsg answer = new MonitorMsg(rsId, LOCAL_RS_ID);
    answer.setReplServerDbState(stateWith(newest));
    return answer;
  }

  private static ServerState stateWith(CSN csn)
  {
    final ServerState state = new ServerState();
    state.update(csn);
    return state;
  }
}
