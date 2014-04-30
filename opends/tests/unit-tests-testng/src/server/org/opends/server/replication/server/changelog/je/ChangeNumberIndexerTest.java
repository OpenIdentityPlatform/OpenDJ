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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.lang.Thread.State;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
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
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.types.DN;
import org.testng.annotations.*;

import com.forgerock.opendj.util.Pair;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

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

    /** {@inheritDoc} */
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

  private ChangelogDB changelogDB;
  private ChangeNumberIndexDB cnIndexDB;
  private ReplicationDomainDB domainDB;
  private Map<Pair<DN, Integer>, SequentialDBCursor> cursors =
      new HashMap<Pair<DN, Integer>, SequentialDBCursor>();
  private ChangelogState initialState;
  private ChangeNumberIndexer cnIndexer;
  private MultiDomainServerState initialCookie;

  @BeforeClass
  public static void classSetup() throws Exception
  {
    TestCaseUtils.startFakeServer();
    BASE_DN1 = DN.decode("dc=example,dc=com");
    BASE_DN2 = DN.decode("dc=world,dc=company");
    ADMIN_DATA_DN = DN.decode("cn=admin data");
  }

  @AfterClass
  public static void classTearDown() throws Exception
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @BeforeMethod
  public void setup() throws Exception
  {
    changelogDB = mock(ChangelogDB.class);
    cnIndexDB = mock(ChangeNumberIndexDB.class);
    domainDB = mock(ReplicationDomainDB.class);
    when(changelogDB.getChangeNumberIndexDB()).thenReturn(cnIndexDB);
    when(changelogDB.getReplicationDomainDB()).thenReturn(domainDB);

    initialState = new ChangelogState();
    initialCookie = new MultiDomainServerState();
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    stopCNIndexer();
  }

  private static final String EMPTY_DB_NO_DS = "emptyDBNoDS";

  @Test
  public void emptyDBNoDS() throws Exception
  {
    startCNIndexer(BASE_DN1);
    assertExternalChangelogContent();
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBOneDS() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);
    assertExternalChangelogContent(msg1);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void nonEmptyDBOneDS() throws Exception
  {
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    addReplica(BASE_DN1, serverId1);
    setDBInitialRecords(msg1);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId1, 2);
    publishUpdateMsg(msg2);
    assertExternalChangelogContent(msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSs() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer(BASE_DN1);

    // simulate messages received out of order
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    // do not start publishing to the changelog until we hear from serverId1
    assertExternalChangelogContent();
    publishUpdateMsg(msg1);
    assertExternalChangelogContent(msg1);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSsDifferentDomains() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN2, serverId2);
    startCNIndexer(BASE_DN1, BASE_DN2);

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
  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSsDoesNotLoseChanges() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    startCNIndexer(BASE_DN1);

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

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void nonEmptyDBTwoDSs() throws Exception
  {
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    setDBInitialRecords(msg1, msg2);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId2, 3);
    final ReplicatedUpdateMsg msg4 = msg(BASE_DN1, serverId1, 4);
    publishUpdateMsg(msg3, msg4);
    assertExternalChangelogContent(msg3);

    final ReplicatedUpdateMsg msg5 = msg(BASE_DN1, serverId1, 5);
    publishUpdateMsg(msg5);
    assertExternalChangelogContent(msg3);

    final ReplicatedUpdateMsg msg6 = msg(BASE_DN1, serverId2, 6);
    publishUpdateMsg(msg6);
    assertExternalChangelogContent(msg3, msg4, msg5);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSsOneSendsNoUpdatesForSomeTime() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg1Sid2 = msg(BASE_DN1, serverId2, 1);
    final ReplicatedUpdateMsg emptySid2 = emptyCursor(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg2Sid1 = msg(BASE_DN1, serverId1, 2);
    final ReplicatedUpdateMsg msg3Sid2 = msg(BASE_DN1, serverId2, 3);
    // simulate no messages received during some time for replica 2
    publishUpdateMsg(msg1Sid2, emptySid2, emptySid2, emptySid2, msg3Sid2, msg2Sid1);
    assertExternalChangelogContent(msg1Sid2, msg2Sid1);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBThreeDSsOneIsNotECLEnabledDomain() throws Exception
  {
    addReplica(ADMIN_DATA_DN, serverId1);
    addReplica(BASE_DN1, serverId2);
    addReplica(BASE_DN1, serverId3);
    startCNIndexer(BASE_DN1);

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

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBOneInitialDSAnotherDSJoining() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);

    addReplica(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    assertExternalChangelogContent(msg1);

    final ReplicatedUpdateMsg msg3 = msg(BASE_DN1, serverId1, 3);
    publishUpdateMsg(msg3);
    assertExternalChangelogContent(msg1, msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBOneInitialDSAnotherDSJoining2() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    publishUpdateMsg(msg1);

    addReplica(BASE_DN1, serverId2);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg2);
    assertExternalChangelogContent(msg1);

    sendHeartbeat(BASE_DN1, serverId1, 3);
    assertExternalChangelogContent(msg1, msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSsOneSendingHeartbeats() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer(BASE_DN1);

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN1, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN1, serverId2, 2);
    publishUpdateMsg(msg1, msg2);
    assertExternalChangelogContent(msg1);

    sendHeartbeat(BASE_DN1, serverId1, 3);
    assertExternalChangelogContent(msg1, msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSsOneGoingOffline() throws Exception
  {
    addReplica(BASE_DN1, serverId1);
    addReplica(BASE_DN1, serverId2);
    startCNIndexer(BASE_DN1);

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

  private void addReplica(DN baseDN, int serverId) throws Exception
  {
    final SequentialDBCursor cursor = new SequentialDBCursor();
    cursors.put(Pair.of(baseDN, serverId), cursor);
    when(domainDB.getCursorFrom(eq(baseDN), eq(serverId), any(CSN.class)))
        .thenReturn(cursor);
    when(domainDB.getDomainNewestCSNs(baseDN)).thenReturn(new ServerState());
    initialState.addServerIdToDomain(serverId, baseDN);
  }

  private void startCNIndexer(DN... eclEnabledDomains)
  {
    final List<DN> eclEnabledDomainList = Arrays.asList(eclEnabledDomains);
    cnIndexer = new ChangeNumberIndexer(changelogDB, initialState)
    {
      @Override
      protected boolean isECLEnabledDomain(DN baseDN)
      {
        return eclEnabledDomainList.contains(baseDN);
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
      cnIndexer.interrupt();
      cnIndexer.join();
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

  private void setDBInitialRecords(ReplicatedUpdateMsg... msgs) throws Exception
  {
    // Initialize the previous cookie that will be used to compare the records
    // added to the CNIndexDB at the end of this test
    for (int i = 0; i < msgs.length; i++)
    {
      ReplicatedUpdateMsg msg = msgs[i];
      if (i + 1 == msgs.length)
      {
        final ReplicatedUpdateMsg newestMsg = msg;
        final DN baseDN = newestMsg.getBaseDN();
        final CSN csn = newestMsg.getCSN();
        when(cnIndexDB.getNewestRecord()).thenReturn(
            new ChangeNumberIndexRecord(initialCookie.toString(), baseDN, csn));
        final SequentialDBCursor cursor =
            cursors.get(Pair.of(baseDN, csn.getServerId()));
        cursor.add(newestMsg);
        cursor.next(); // simulate the cursor had been initialized with this change
      }
      initialCookie.update(msg.getBaseDN(), msg.getCSN());
    }
  }

  private void publishUpdateMsg(ReplicatedUpdateMsg... msgs) throws Exception
  {
    for (ReplicatedUpdateMsg msg : msgs)
    {
      final SequentialDBCursor cursor =
          cursors.get(Pair.of(msg.getBaseDN(), msg.getCSN().getServerId()));
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
        cnIndexer.publishUpdateMsg(msg.getBaseDN(), msg);
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

    // clone initial state to avoid modifying it
    final MultiDomainServerState previousCookie =
        new MultiDomainServerState(initialCookie.toString());
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
      assertThat(record.getPreviousCookie()).as(desc2).isEqualTo(previousCookie.toString());
      previousCookie.update(expectedMsg.getBaseDN(), expectedMsg.getCSN());
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

  @Test(dataProvider = "precedingCSNDataProvider")
  public void getPrecedingCSN(CSN start, CSN expected)
  {
    CSN precedingCSN = this.cnIndexer.getPrecedingCSN(start);
    assertThat(precedingCSN).isEqualTo(expected);
  }
}
