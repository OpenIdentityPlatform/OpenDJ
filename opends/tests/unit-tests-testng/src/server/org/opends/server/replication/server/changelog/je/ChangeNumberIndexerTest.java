/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
      return "csn=" + getCSN() + ", baseDN=" + baseDN;
    }
  }

  private static DN BASE_DN;
  private static final int serverId1 = 101;
  private static final int serverId2 = 102;

  private ChangelogDB changelogDB;
  private ChangeNumberIndexDB cnIndexDB;
  private ReplicationDomainDB domainDB;
  private Map<Pair<DN, Integer>, SequentialDBCursor> cursors =
      new HashMap<Pair<DN, Integer>, SequentialDBCursor>();
  private ChangelogState initialState;
  private ChangeNumberIndexer indexer;
  private MultiDomainServerState initialCookie;

  @BeforeClass
  public static void classSetup() throws Exception
  {
    TestCaseUtils.startFakeServer();
    BASE_DN = DN.decode("dc=example,dc=com");
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


  private static final String EMPTY_DB_NO_DS = "emptyDBNoDS";

  @Test
  public void emptyDBNoDS() throws Exception
  {
    startIndexer();
    verify(cnIndexDB, never()).addRecord(any(ChangeNumberIndexRecord.class));
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBOneInitialDS() throws Exception
  {
    addReplica(BASE_DN, serverId1);
    startIndexer();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    publishUpdateMsg(msg1);
    assertAddedRecords(msg1);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void nonEmptyDBOneInitialDS() throws Exception
  {
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    addReplica(BASE_DN, serverId1);
    setDBInitialRecords(msg1);
    startIndexer();

    final ReplicatedUpdateMsg msg2 = msg(BASE_DN, serverId1, 2);
    publishUpdateMsg(msg2);
    assertAddedRecords(msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoInitialDSs() throws Exception
  {
    addReplica(BASE_DN, serverId1);
    addReplica(BASE_DN, serverId2);
    startIndexer();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN, serverId2, 2);
    publishUpdateMsg(msg2, msg1);
    assertAddedRecords(msg1);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void nonEmptyDBTwoInitialDSs() throws Exception
  {
    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN, serverId2, 2);
    addReplica(BASE_DN, serverId1);
    addReplica(BASE_DN, serverId2);
    setDBInitialRecords(msg1, msg2);
    startIndexer();

    final ReplicatedUpdateMsg msg3 = msg(BASE_DN, serverId2, 3);
    final ReplicatedUpdateMsg msg4 = msg(BASE_DN, serverId1, 4);
    publishUpdateMsg(msg3, msg4);
    assertAddedRecords(msg3);

    final ReplicatedUpdateMsg msg5 = msg(BASE_DN, serverId1, 5);
    publishUpdateMsg(msg5);
    assertAddedRecords(msg3);

    final ReplicatedUpdateMsg msg6 = msg(BASE_DN, serverId2, 6);
    publishUpdateMsg(msg6);
    assertAddedRecords(msg3, msg4, msg5);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoDSsOneSendsNoUpdatesForSomeTime() throws Exception
  {
    addReplica(BASE_DN, serverId1);
    addReplica(BASE_DN, serverId2);
    startIndexer();

    final ReplicatedUpdateMsg msg1Sid2 = msg(BASE_DN, serverId2, 1);
    final ReplicatedUpdateMsg emptySid2 = emptyCursor(BASE_DN, serverId2);
    final ReplicatedUpdateMsg msg2Sid1 = msg(BASE_DN, serverId1, 2);
    final ReplicatedUpdateMsg msg3Sid2 = msg(BASE_DN, serverId2, 3);
    // simulate no messages received during some time for replica 2
    publishUpdateMsg(msg1Sid2, emptySid2, emptySid2, emptySid2, msg3Sid2, msg2Sid1);
    assertAddedRecords(msg1Sid2, msg2Sid1);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBOneInitialDSAnotherDSJoining() throws Exception
  {
    addReplica(BASE_DN, serverId1);
    startIndexer();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    publishUpdateMsg(msg1);

    addReplica(BASE_DN, serverId2);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN, serverId2, 2);
    publishUpdateMsg(msg2);
    assertAddedRecords(msg1);

    final ReplicatedUpdateMsg msg3 = msg(BASE_DN, serverId1, 3);
    publishUpdateMsg(msg3);
    assertAddedRecords(msg1, msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoInitialDSsOneSendingHeartbeats() throws Exception
  {
    addReplica(BASE_DN, serverId1);
    addReplica(BASE_DN, serverId2);
    startIndexer();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN, serverId2, 2);
    publishUpdateMsg(msg1, msg2);
    assertAddedRecords(msg1);

    sendHeartbeat(BASE_DN, serverId1, 3);
    assertAddedRecords(msg1, msg2);
  }

  @Test(dependsOnMethods = { EMPTY_DB_NO_DS })
  public void emptyDBTwoInitialDSsOneGoingOffline() throws Exception
  {
    addReplica(BASE_DN, serverId1);
    addReplica(BASE_DN, serverId2);
    startIndexer();

    final ReplicatedUpdateMsg msg1 = msg(BASE_DN, serverId1, 1);
    final ReplicatedUpdateMsg msg2 = msg(BASE_DN, serverId2, 2);
    publishUpdateMsg(msg1, msg2);
    assertAddedRecords(msg1);

    replicaOffline(BASE_DN, serverId2, 3);
    // MCP cannot move forward since no new updates from serverId1
    assertAddedRecords(msg1);

    final ReplicatedUpdateMsg msg4 = msg(BASE_DN, serverId1, 4);
    publishUpdateMsg(msg4);
    // MCP moved forward after receiving update from serverId1
    // (last replica in the domain)
    assertAddedRecords(msg1, msg2, msg4);
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

  private void startIndexer()
  {
    indexer = new ChangeNumberIndexer(changelogDB, initialState);
    indexer.start();
    waitForWaitingState(indexer);
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
        indexer.publishUpdateMsg(msg.getBaseDN(), msg);
      }
    }
    waitForWaitingState(indexer);
  }

  private void sendHeartbeat(DN baseDN, int serverId, int time) throws Exception
  {
    indexer.publishHeartbeat(baseDN, new CSN(time, 0, serverId));
    waitForWaitingState(indexer);
  }

  private void replicaOffline(DN baseDN, int serverId, int time) throws Exception
  {
    indexer.replicaOffline(baseDN, new CSN(time, 0, serverId));
    waitForWaitingState(indexer);
  }

  private void waitForWaitingState(final Thread t)
  {
    State state = t.getState();
    while (!state.equals(State.WAITING) && !state.equals(State.TERMINATED))
    {
      Thread.yield();
      state = t.getState();
    }
    assertThat(state).isEqualTo(State.WAITING);
  }

  /**
   * Asserts which records have been added to the CNIndexDB since starting the
   * {@link ChangeNumberIndexer} thread.
   */
  private void assertAddedRecords(ReplicatedUpdateMsg... msgs) throws Exception
  {
    final ArgumentCaptor<ChangeNumberIndexRecord> arg =
        ArgumentCaptor.forClass(ChangeNumberIndexRecord.class);
    verify(cnIndexDB, atLeast(0)).addRecord(arg.capture());
    final List<ChangeNumberIndexRecord> allValues = arg.getAllValues();

    // clone initial state to avoid modifying it
    final MultiDomainServerState previousCookie =
        new MultiDomainServerState(initialCookie.toString());
    // check it was not called more than expected
    String desc1 = "actual was:<" + allValues + ">, but expected was:<" + Arrays.toString(msgs) + ">";
    assertThat(allValues.size()).as(desc1).isEqualTo(msgs.length);
    for (int i = 0; i < msgs.length; i++)
    {
      final ReplicatedUpdateMsg msg = msgs[i];
      final ChangeNumberIndexRecord record = allValues.get(i);
      // check content in order
      String desc2 = "actual was:<" + record + ">, but expected was:<" + msg + ">";
      assertThat(record.getBaseDN()).as(desc2).isEqualTo(msg.getBaseDN());
      assertThat(record.getCSN()).as(desc2).isEqualTo(msg.getCSN());
      assertThat(record.getPreviousCookie()).as(desc2).isEqualTo(previousCookie.toString());
      previousCookie.update(msg.getBaseDN(), msg.getCSN());
    }
  }
}
