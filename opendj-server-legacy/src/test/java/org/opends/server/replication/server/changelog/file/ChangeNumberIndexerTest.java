/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013-2015 ForgeRock AS
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.lang.Thread.State;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.server.changelog.api.ReplicaId;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.file.ChangeNumberIndexer;
import org.opends.server.replication.server.changelog.file.DomainDBCursor;
import org.opends.server.replication.server.changelog.file.ECLEnabledDomainPredicate;
import org.opends.server.replication.server.changelog.file.MultiDomainDBCursor;
import org.opends.server.types.DN;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;

/**
 * Test for ChangeNumberIndexer class. All dependencies to the changelog DB
 * interfaces are mocked. The ChangeNumberIndexer class simulates what the RS
 * does to compute a changeNumber. The tests setup various topologies with their
 * replicas.
 * <p>
 * All tests are written with this layout:
 * <ul>
 * <li>Initial setup where RS is stopped. Data are set into the changelog state
 * DB, the replica DBs and the change number index DB.</li>
 * <li>Simulate RS startup by calling {@link #startCNIndexer(DN...)}. This will
 * start the change number indexer thread that will start computing change
 * numbers and inserting them in the change number index db.</li>
 * <li>Send events to the change number indexer thread by publishing update
 * messages, sending heartbeat messages or replica offline messages.</li>
 * </ul>
 */
@SuppressWarnings("javadoc")
public class ChangeNumberIndexerTest extends DirectoryServerTestCase
{

  private static final class ReplicatedUpdateMsg extends UpdateMsg
  {
    private final DN baseDN;
    private final boolean emptyCursor;

    public ReplicatedUpdateMsg(DN baseDN, CSN csn)
    {
      this(baseDN, csn, false);
    }

    public ReplicatedUpdateMsg(DN baseDN, CSN csn, boolean emptyCursor)
    {
      super(csn, null);
      this.baseDN = baseDN;
      this.emptyCursor = emptyCursor;
    }

    public DN getBaseDN()
    {
      return baseDN;
    }

    public boolean isEmptyCursor()
    {
      return emptyCursor;
    }

    @Override
    public String toString()
    {
      return "UpdateMsg("
          + "\"" + baseDN + " " + getCSN().getServerId() + "\""
          + ", csn=" + getCSN().toStringUI()
          + ")";
    }
  }

  private static DN BASE_DN1;
  private static DN BASE_DN2;
  private static DN ADMIN_DATA_DN;
  private static final int serverId1 = 101;
  private static final int serverId2 = 102;
  private static final int serverId3 = 103;

  @Mock
  private ChangelogDB changelogDB;
  @Mock
  private ChangeNumberIndexDB cnIndexDB;
  @Mock
  private ReplicationDomainDB domainDB;

  private List<DN> eclEnabledDomains;
  private MultiDomainDBCursor multiDomainCursor;
  private Map<ReplicaId, SequentialDBCursor> replicaDBCursors;
  private Map<DN, DomainDBCursor> domainDBCursors;
  private ChangelogState initialState;
  private Map<DN, ServerState> domainNewestCSNs;
  private ECLEnabledDomainPredicate predicate;
  private ChangeNumberIndexer cnIndexer;

  @BeforeClass
  public static void classSetup() throws Exception
  {
    TestCaseUtils.startServer();
    BASE_DN1 = DN.valueOf("dc=example,dc=com");
    BASE_DN2 = DN.valueOf("dc=world,dc=company");
    ADMIN_DATA_DN = DN.valueOf("cn=admin data");
  }

  @BeforeMethod
  public void setup() throws Exception
  {
    MockitoAnnotations.initMocks(this);

    CursorOptions options = new CursorOptions(LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, null);
    multiDomainCursor = new MultiDomainDBCursor(domainDB, options);
    initialState = new ChangelogState();
    replicaDBCursors = new HashMap<>();
    domainDBCursors = new HashMap<>();
    domainNewestCSNs = new HashMap<>();

    when(changelogDB.getChangeNumberIndexDB()).thenReturn(cnIndexDB);
    when(changelogDB.getReplicationDomainDB()).thenReturn(domainDB);
    when(domainDB.getCursorFrom(any(MultiDomainServerState.class), eq(options))).thenReturn(multiDomainCursor);
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    stopCNIndexer();
  }

  private static final String NO_DS = "noDS";

  @Test
  public void noDS() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    startCNIndexer();
    assertExternalChangelogContent();
  }

  @Test(dependsOnMethods = { NO_DS })
  public void oneDS() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);
    assertExternalChangelogContent(msg1);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSs() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer();
    assertExternalChangelogContent();

    // simulate messages received out of order
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    // do not start publishing to the changelog until we hear from serverId1
    assertExternalChangelogContent();
    publishUpdateMsg(msg1);
    assertExternalChangelogContent(msg1);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsDifferentDomains() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1, BASE_DN2);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN2, serverId2);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN2, serverId2, 2);
    publishUpdateMsg(msg1, msg2);
    assertExternalChangelogContent(msg1);
    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg3);
    assertExternalChangelogContent(msg1, msg2);
  }

  /**
   * This test tries to reproduce a very subtle implementation bug where:
   * <ol>
   * <li>the change number indexer has no more records to proceed, because all
   * cursors are exhausted, so it calls wait()<li>
   * <li>a new change Upd1 comes in for an exhausted cursor,
   * medium consistency cannot move<li>
   * <li>a new change Upd2 comes in for a cursor that is not already opened,
   * medium consistency can move, so wake up the change number indexer<li>
   * <li>on wake up, the change number indexer calls next(),
   * advancing the CompositeDBCursor, which recycles the exhausted cursor,
   * then calls next() on it, making it lose its change.
   * CompositeDBCursor currentRecord == Upd1.<li>
   * <li>on the next iteration of the loop in run(), a new cursor is created,
   * triggering the creation of a new CompositeDBCursor => Upd1 is lost.
   * CompositeDBCursor currentRecord == Upd2.<li>
   * </ol>
   */
  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsDoesNotLoseChanges() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);
    assertExternalChangelogContent(msg1);

    addReplica(BASE_DN1, serverId2);
    sendHeartbeat(BASE_DN1, serverId2, 2);
    assertExternalChangelogContent(msg1);
    // publish change that will not trigger a wake up of change number indexer,
    // but will make it open a cursor on next wake up
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    assertExternalChangelogContent(msg1);
    // wake up change number indexer
    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg3);
    assertExternalChangelogContent(msg1, msg2);
    sendHeartbeat(BASE_DN1, serverId2, 4);
    // assert no changes have been lost
    assertExternalChangelogContent(msg1, msg2, msg3);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneSendsNoUpdatesForSomeTime() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1Sid2 = msg(BASE_DN1, serverId2, 1);
    final ReplicatedUpdateMsg emptySid2 = emptyCursor(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg2Sid1 = msg(BASE_DN1, serverId1, 2);
    final ReplicatedUpdateMsg msg3Sid2 = msg(BASE_DN1, serverId2, 3);
    // simulate no messages received during some time for replica 2
    publishUpdateMsg(msg1Sid2, emptySid2, emptySid2, emptySid2, msg3Sid2, msg2Sid1);
    assertExternalChangelogContent(msg1Sid2, msg2Sid1);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void threeDSsOneIsNotECLEnabledDomain() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(ADMIN_DATA_DN, serverId1);
    addReplica(BASE_DN1, serverId2);
    addReplica(BASE_DN1, serverId3);
    startCNIndexer();
    assertExternalChangelogContent();

    // cn=admin data will does not participate in the external changelog
    // so it cannot add to it
    final ReplicatedUpdateMsg msg1 = msg(ADMIN_DATA_DN, serverId1, 1);
    publishUpdateMsg(msg1);
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId3, 3);
    publishUpdateMsg(msg2, msg3);
    assertExternalChangelogContent(msg2);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void oneInitialDSAnotherDSJoining() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);
    assertExternalChangelogContent(msg1);

    addReplica(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    assertExternalChangelogContent(msg1);

    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg3);
    assertExternalChangelogContent(msg1, msg2);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void oneInitialDSAnotherDSJoining2() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);

    addReplica(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    assertExternalChangelogContent(msg1);

    sendHeartbeat(BASE_DN1, serverId1, 3);
    assertExternalChangelogContent(msg1, msg2);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneSendingHeartbeats() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg1, msg2);
    assertExternalChangelogContent(msg1);

    sendHeartbeat(BASE_DN1, serverId1, 3);
    assertExternalChangelogContent(msg1, msg2);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneGoingOffline() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg1, msg2);
    assertExternalChangelogContent(msg1);

    replicaOffline(BASE_DN1, serverId2, 3);
    // MCP cannot move forward since no new updates from serverId1
    assertExternalChangelogContent(msg1);

    final ReplicatedUpdateMsg msg4 = msg(BASE_DN1, serverId1, 4);
    publishUpdateMsg(msg4);
    // MCP moves forward after receiving update from serverId1
    // (last replica in the domain)
    assertExternalChangelogContent(msg1, msg2, msg4);

    // serverId2 comes online again
    final ReplicatedUpdateMsg msg5 = msg(BASE_DN1, serverId2, 5);
    publishUpdateMsg(msg5);
    // MCP does not move until it knows what happens to serverId1
    assertExternalChangelogContent(msg1, msg2, msg4);
    sendHeartbeat(BASE_DN1, serverId1, 6);
    // MCP moves forward
    assertExternalChangelogContent(msg1, msg2, msg4, msg5);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneInitiallyOffline() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    initialState.addOfflineReplica(BASE_DN1, new CSN(1, 1, serverId1));
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    // MCP does not wait for temporarily offline serverId1
    assertExternalChangelogContent(msg2);

    // serverId1 is back online, wait for changes from serverId2
    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg3);
    assertExternalChangelogContent(msg2);
    final ReplicatedUpdateMsg msg4 = msg(BASE_DN1, serverId2, 4);
    publishUpdateMsg(msg4);
    // MCP moves forward
    assertExternalChangelogContent(msg2, msg3);
  }

  /**
   * Scenario:
   * <ol>
   * <li>Replica 1 publishes one change</li>
   * <li>Replica 1 sends offline message</li>
   * <li>RS stops</li>
   * <li>RS starts</li>
   * </ol>
   */
  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneInitiallyWithChangesThenOffline() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);
    initialState.addOfflineReplica(BASE_DN1, new CSN(2, 1, serverId1));
    startCNIndexer();

    // blocked until we receive info for serverId2
    assertExternalChangelogContent();

    sendHeartbeat(BASE_DN1, serverId2, 3);
    // MCP moves forward
    assertExternalChangelogContent(msg1);

    // do not wait for temporarily offline serverId1
    final ReplicatedUpdateMsg msg4 = msg(BASE_DN1, serverId2, 4);
    publishUpdateMsg(msg4);
    assertExternalChangelogContent(msg1, msg4);

    // serverId1 is back online, wait for changes from serverId2
    final ReplicatedUpdateMsg msg5 = msg(BASE_DN1, serverId1, 5);
    publishUpdateMsg(msg5);
    assertExternalChangelogContent(msg1, msg4);

    final ReplicatedUpdateMsg msg6 = msg(BASE_DN1, serverId2, 6);
    publishUpdateMsg(msg6);
    // MCP moves forward
    assertExternalChangelogContent(msg1, msg4, msg5);
  }

  /**
   * Scenario:
   * <ol>
   * <li>Replica 1 sends offline message</li>
   * <li>Replica 1 starts</li>
   * <li>Replica 1 publishes one change</li>
   * <li>Replica 1 publishes a second change</li>
   * <li>RS stops</li>
   * <li>RS starts</li>
   * </ol>
   */
  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneInitiallyPersistedOfflineThenChanges() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    initialState.addOfflineReplica(BASE_DN1, new CSN(1, 1, serverId1));
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId1, 2);
    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg2, msg3);
    startCNIndexer();
    assertExternalChangelogContent();

    // MCP moves forward because serverId1 is not really offline
    // since we received a message from it newer than the offline replica msg
    final ReplicatedUpdateMsg msg4 = msg(BASE_DN1, serverId2, 4);
    publishUpdateMsg(msg4);
    assertExternalChangelogContent(msg2, msg3);

    // back to normal operations
    sendHeartbeat(BASE_DN1, serverId1, 5);
    assertExternalChangelogContent(msg2, msg3, msg4);
  }

  @Test(dependsOnMethods = { NO_DS })
  public void twoDSsOneKilled() throws Exception
  {
    eclEnabledDomains = Arrays.asList(BASE_DN1);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer();
    assertExternalChangelogContent();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);
    // MCP cannot move forward: no news yet from serverId2
    assertExternalChangelogContent();

    sendHeartbeat(BASE_DN1, serverId2, 2);
    // MCP moves forward: we know what serverId2 is at
    assertExternalChangelogContent(msg1);

    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg3);
    // MCP cannot move forward: serverId2 is the oldest CSN
    assertExternalChangelogContent(msg1);
  }

  private void addReplica(DN baseDN, int serverId) throws Exception
  {
    final SequentialDBCursor replicaDBCursor = new SequentialDBCursor();
    replicaDBCursors.put(ReplicaId.of(baseDN, serverId), replicaDBCursor);

    if (predicate.isECLEnabledDomain(baseDN))
    {
      CursorOptions options = new CursorOptions(LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, null);
      DomainDBCursor domainDBCursor = domainDBCursors.get(baseDN);
      if (domainDBCursor == null)
      {
        domainDBCursor = new DomainDBCursor(baseDN, domainDB, options);
        domainDBCursors.put(baseDN, domainDBCursor);

        multiDomainCursor.addDomain(baseDN, null);
        when(domainDB.getCursorFrom(eq(baseDN), any(ServerState.class), eq(options))).thenReturn(domainDBCursor);
      }
      domainDBCursor.addReplicaDB(serverId, null);
      when(domainDB.getCursorFrom(eq(baseDN), eq(serverId), any(CSN.class), eq(options))).thenReturn(replicaDBCursor);
    }

    when(domainDB.getDomainNewestCSNs(baseDN)).thenReturn(getDomainNewestCSNs(baseDN));
    initialState.addServerIdToDomain(serverId, baseDN);
  }

  private ServerState getDomainNewestCSNs(final DN baseDN)
  {
    ServerState serverState = domainNewestCSNs.get(baseDN);
    if (serverState == null)
    {
      serverState = new ServerState();
      domainNewestCSNs.put(baseDN, serverState);
    }
    return serverState;
  }

  private void startCNIndexer()
  {
    predicate = new ECLEnabledDomainPredicate()
    {
      @Override
      public boolean isECLEnabledDomain(DN baseDN)
      {
        return eclEnabledDomains.contains(baseDN);
      }
    };
    cnIndexer = new ChangeNumberIndexer(changelogDB, initialState, predicate)
    {
      /** {@inheritDoc} */
      @Override
      protected void notifyEntryAddedToChangelog(DN baseDN, long changeNumber,
          MultiDomainServerState previousCookie, UpdateMsg msg) throws ChangelogException
      {
        // avoid problems with ChangelogBackend initialization
      }
    };
    cnIndexer.start();
    waitForWaitingState(cnIndexer);
  }

  private void stopCNIndexer() throws Exception
  {
    if (cnIndexer != null)
    {
      cnIndexer.initiateShutdown();
      cnIndexer.join();
      cnIndexer = null;
    }
  }

  private ReplicatedUpdateMsg msg(DN baseDN, int serverId, long time)
  {
    return new ReplicatedUpdateMsg(baseDN, new CSN(time, 0, serverId));
  }

  private ReplicatedUpdateMsg emptyCursor(DN baseDN, int serverId)
  {
    return new ReplicatedUpdateMsg(baseDN, new CSN(0, 0, serverId), true);
  }

  private void publishUpdateMsg(ReplicatedUpdateMsg... msgs) throws Exception
  {
    for (ReplicatedUpdateMsg msg : msgs)
    {
      final SequentialDBCursor cursor =
          replicaDBCursors.get(ReplicaId.of(msg.getBaseDN(), msg.getCSN().getServerId()));
      if (msg.isEmptyCursor())
      {
        cursor.add(null);
      }
      else
      {
        cursor.add(msg);
      }
    }
    for (ReplicatedUpdateMsg msg : msgs)
    {
      if (!msg.isEmptyCursor())
      {
        if (cnIndexer != null)
        {
          // indexer is running
          cnIndexer.publishUpdateMsg(msg.getBaseDN(), msg);
        }
        else
        {
          // we are only setting up initial state, update the domain newest CSNs
          getDomainNewestCSNs(msg.getBaseDN()).update(msg.getCSN());
        }
      }
    }
    waitForWaitingState(cnIndexer);
  }

  private void sendHeartbeat(DN baseDN, int serverId, int time) throws Exception
  {
    cnIndexer.publishHeartbeat(baseDN, new CSN(time, 0, serverId));
    waitForWaitingState(cnIndexer);
  }

  private void replicaOffline(DN baseDN, int serverId, int time) throws Exception
  {
    cnIndexer.replicaOffline(baseDN, new CSN(time, 0, serverId));
    waitForWaitingState(cnIndexer);
  }

  private void waitForWaitingState(final Thread t)
  {
    if (t == null)
    { // not started yet, do not wait
      return;
    }
    State state = t.getState();
    while (!state.equals(State.WAITING)
        && !state.equals(State.TIMED_WAITING)
        && !state.equals(State.TERMINATED))
    {
      Thread.yield();
      state = t.getState();
    }
    assertThat(state).isIn(State.WAITING, State.TIMED_WAITING);
  }

  /**
   * Asserts which records have been added to the CNIndexDB since starting the
   * {@link ChangeNumberIndexer} thread.
   */
  private void assertExternalChangelogContent(ReplicatedUpdateMsg... expectedMsgs)
      throws Exception
  {
    final ArgumentCaptor<ChangeNumberIndexRecord> arg =
        ArgumentCaptor.forClass(ChangeNumberIndexRecord.class);
    verify(cnIndexDB, atLeast(0)).addRecord(arg.capture());
    final List<ChangeNumberIndexRecord> allValues = arg.getAllValues();

    // check it was not called more than expected
    String desc1 = "actual was:<" + allValues + ">, but expected was:<" + Arrays.toString(expectedMsgs) + ">";
    assertThat(allValues).as(desc1).hasSize(expectedMsgs.length);
    for (int i = 0; i < expectedMsgs.length; i++)
    {
      final ReplicatedUpdateMsg expectedMsg = expectedMsgs[i];
      final ChangeNumberIndexRecord record = allValues.get(i);
      // check content in order
      String desc2 = "actual was:<" + record + ">, but expected was:<" + expectedMsg + ">";
      assertThat(record.getBaseDN()).as(desc2).isEqualTo(expectedMsg.getBaseDN());
      assertThat(record.getCSN()).as(desc2).isEqualTo(expectedMsg.getCSN());
    }
  }

  @DataProvider
  public Object[][] precedingCSNDataProvider()
  {
    final int serverId = 42;
    final int t = 1000;
    return new Object[][] {
      // @formatter:off
      { null, null, },
      { new CSN(t, 1, serverId), new CSN(t, 0, serverId), },
      { new CSN(t, 0, serverId), new CSN(t - 1, Integer.MAX_VALUE, serverId), },
      // @formatter:on
    };
  }

}
