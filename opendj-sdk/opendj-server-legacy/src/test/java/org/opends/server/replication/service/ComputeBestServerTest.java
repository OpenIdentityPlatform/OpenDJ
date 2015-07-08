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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.service;

import java.util.*;
import java.util.Map.Entry;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.MapEntry;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker.RSEvaluations;
import org.opends.server.replication.service.ReplicationBroker.ReplicationServerInfo;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Collections.*;

import static org.assertj.core.data.MapEntry.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.service.ReplicationBroker.*;
import static org.testng.Assert.*;

/**
 * Test the algorithm for finding the best replication server among the
 * configured ones.
 */
@SuppressWarnings("javadoc")
public class ComputeBestServerTest extends ReplicationTestCase
{

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  // definitions for server ids
  private static final int myId1 = 1;
  private static final int myId2 = 2;
  private static final int myId3 = 3;

  // definitions for server names
  private static final String WINNER = "winner:389";
  private static final String LOOSER1 = "looser1:389";
  private static final String LOOSER2 = "looser2:389";

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  private ServerState newServerState(CSN... csns)
  {
    ServerState result = new ServerState();
    for (CSN csn : csns)
    {
      result.update(csn);
    }
    return result;
  }

  /**
   * Test with one replication server, nobody has a CSN (simulates) very first
   * connection.
   */
  @Test
  public void testNullCSNBoth() throws Exception
  {
    String testCase = "testNullCSNBoth";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState();
    ServerState aState = newServerState();

    Map<Integer, ReplicationServerInfo> rsInfos =
        newRSInfos(newRSInfo(11, WINNER, aState, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(), entry(11, NOTE_BEST_RS.ordinal()));
  }

  private Map<Integer, ReplicationServerInfo> newRSInfos(
      ReplicationServerInfo... rsInfos)
  {
    Map<Integer, ReplicationServerInfo> results = new HashMap<>();
    for (ReplicationServerInfo rsInfo : rsInfos)
    {
      results.put(rsInfo.getServerId(), rsInfo);
    }
    return results;
  }

  private ReplicationServerInfo newRSInfo(int serverId, String serverURL,
      ServerState state, long genId, int groupId)
  {
    return ReplicationServerInfo.newInstance(new ReplServerStartMsg(serverId,
        serverURL, null, 0, state, genId, false, (byte) groupId, 0));
  }

  /**
   * Test with one replication server, only replication server has a non null
   * CSN for ds server id.
   */
  @Test
  public void testNullCSNDS() throws Exception
  {
    String testCase = "testNullCSNDS";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState();
    ServerState aState = newServerState(new CSN(0, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos =
        newRSInfos(newRSInfo(11, WINNER, aState, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(), entry(11, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with one replication server, only ds server has a non null CSN for ds
   * server id but rs has a null one.
   */
  @Test
  public void testNullCSNRS() throws Exception
  {
    String testCase = "testNullCSNRS";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState = newServerState();

    Map<Integer, ReplicationServerInfo> rsInfos =
        newRSInfos(newRSInfo(11, WINNER, aState, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(), entry(11, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with one replication server, up to date.
   */
  @Test
  public void test1ServerUp() throws Exception
  {
    String testCase = "test1ServerUp";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState = newServerState(new CSN(1, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos =
        newRSInfos(newRSInfo(11, WINNER, aState, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(), entry(11, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with 2 replication servers, up to date.
   */
  @Test
  public void test2ServersUp() throws Exception
  {
    String testCase = "test2ServersUp";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState1 = newServerState(new CSN(1, 0, myId1));
    ServerState aState2 = newServerState(new CSN(2, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, 0, 1),
        newRSInfo(12, WINNER, aState2, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(12, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with 2 replication servers, up to date, but 2 different group ids.
   */
  @Test
  public void testDiffGroup2ServersUp() throws Exception
  {
    String testCase = "testDiffGroup2ServersUp";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState1 = newServerState(new CSN(1, 0, myId1));
    ServerState aState2 = newServerState(new CSN(2, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        // This server has less changes than the other one but it has the same
        // group id as us so he should be the winner
        newRSInfo(11, WINNER, aState1, 0, 1),
        newRSInfo(12, LOOSER1, aState2, 0, 2));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, NOTE_BEST_RS.ordinal()),
        entry(12, NOTE_RS_HAS_DIFFERENT_GROUP_ID_THAN_DS.ordinal()));
  }

  private void containsOnly(final Map<Integer, LocalizableMessage> evaluations,
      MapEntry... entries)
  {
    final List<MapEntry> notFound = new ArrayList<>(Arrays.asList(entries));
    for (Iterator<MapEntry> iter = notFound.iterator(); iter.hasNext();)
    {
      final MapEntry entry = iter.next();
      final LocalizableMessage reason = evaluations.get(entry.key);
      if (reason != null && reason.ordinal()==(Integer)entry.value)
      {
        iter.remove();
      }
    }
    if (!notFound.isEmpty())
    {
      final StringBuilder sb = new StringBuilder("expecting ordinals:\n");
      sb.append("  <").append(getOrdinal(evaluations)).append(">\n");
      sb.append("   to contain:\n");
      sb.append("  <").append(Arrays.asList(entries)).append(">\n");
      sb.append("   but could not find:\n");
      sb.append("  <").append(notFound).append(">");
      throw new AssertionError(sb.toString());
    }

    Assertions.assertThat(evaluations).hasSize(entries.length);
  }

  /** Contains ordinal for each message. */
  private Map<Integer, Integer> getOrdinal(Map<Integer, LocalizableMessage> evaluations)
  {
    final Map<Integer, Integer> result = new LinkedHashMap<>();
    for (Entry<Integer, LocalizableMessage> entry : evaluations.entrySet())
    {
      result.put(entry.getKey(), entry.getValue().ordinal());
    }
    return result;
  }

  /**
   * Test with 2 replication servers, none of them from our group id.
   */
  @Test
  public void testNotOurGroup() throws Exception
  {
    String testCase = "testNotOurGroup";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState1 = newServerState(new CSN(1, 0, myId1));
    ServerState aState2 = newServerState(new CSN(2, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, 0, 2),
        newRSInfo(12, WINNER, aState2, 0, 2));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(12, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with 3 replication servers, up to date.
   */
  @Test
  public void test3ServersUp() throws Exception
  {
    String testCase = "test3ServersUp";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState1 = newServerState(new CSN(1, 0, myId1));
    ServerState aState2 = newServerState(new CSN(2, 0, myId1));
    ServerState aState3 = newServerState(new CSN(3, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, 0, 1),
        newRSInfo(12, LOOSER2, aState2, 0, 1),
        newRSInfo(13, WINNER, aState3, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(12, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(13, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with 3 replication servers: 2 are up to date with the directory
   * server, 1 is more up to date than the directory server.
   */
  @Test
  public void test2ServersUpToDateAnd1EvenMoreUpToDate() throws Exception
  {
    String testCase = "test3ServersUp";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState1 = newServerState(new CSN(1, 0, myId1));
    ServerState aState2 = newServerState(new CSN(1, 0, myId1));
    ServerState aState3 = newServerState(new CSN(2, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, 0, 1),
        newRSInfo(12, LOOSER2, aState2, 0, 1),
        newRSInfo(13, WINNER, aState3, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(12, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(13, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with 3 replication servers, up to date, but 2 different group ids.
   */
  @Test
  public void testDiffGroup3ServersUp() throws Exception
  {
    String testCase = "testDiffGroup3ServersUp";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState1 = newServerState(new CSN(1, 0, myId1));
    ServerState aState2 = newServerState(new CSN(2, 0, myId1));
    ServerState aState3 = newServerState(new CSN(3, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, 0, 1),
        newRSInfo(12, LOOSER2, aState2, 0, 2),
        // This server has less changes than looser2 but it has the same
        // group id as us so he should be the winner
        newRSInfo(13, WINNER, aState3, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal()),
        entry(12, NOTE_RS_HAS_DIFFERENT_GROUP_ID_THAN_DS.ordinal()),
        entry(13, NOTE_BEST_RS.ordinal()));
  }

  /**
   * Test with one replication server, late.
   */
  @Test
  public void test1ServerLate() throws Exception
  {
    String testCase = "test1ServerLate";
    debugInfo("Starting " + testCase);

    ServerState mySt = newServerState(new CSN(1, 0, myId1));
    ServerState aState = newServerState(new CSN(0, 0, myId1));

    Map<Integer, ReplicationServerInfo> rsInfos =
        newRSInfos(newRSInfo(11, WINNER, aState, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte) 1, 0);

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(), entry(11, NOTE_BEST_RS.ordinal()));
  }

  @DataProvider(name = "create3ServersData")
  public Object[][] create3ServersData() {
    return new Object[][] {
        // first RS is up to date, the others are late none is local
        { 4, 2, 3, false,
          1, 2, 3, false,
          2, 3, 4, false},

        // test that the local RS  is chosen first when all up to date
        { 4, 2, 3, true,
          4, 2, 3, false,
          4, 2, 3, false},

        // test that the local ServerID is more important than the others
        { 4, 0, 0, false,
          2, 100, 100, false,
          1, 100, 100, false},

        // test that a remote RS is chosen first when up to date when the local
        // one is late
        { 4, 1, 1, false,
          3, 1, 1, true,
          3, 1, 1, false},

        // test that the local RS is not chosen first when it is missing
        // local changes
        { 4, 1, 1, false,
          3, 2, 3, false,
          1, 1, 1, true},

        // test that a RS which is more up to date than the DS is chosen
        { 5, 1, 1, false,
          2, 0, 0, false,
          1, 1, 1, false},

        // test that a RS which is more up to date than the DS is chosen even
        // is some RS with the same last change from the DS
        { 5, 1, 1, false,
          4, 0, 0, false,
          4, 1, 1, false},

        // test that the local RS is chosen first when it is missing
        // the same local changes as the other RSs
        { 3, 1, 1, true,
          2, 1, 1, false,
          3, 1, 1, false},
        };
  }

  /**
   * Test with 3 replication servers (see data provider).
   */
  @Test(dataProvider =  "create3ServersData")
  public void test3Servers(
      long winnerT1, long winnerT2, long winnerT3, boolean winnerIsLocal,
      long looser1T1, long looser1T2, long looser1T3, boolean looser1IsLocal,
      long looser2T1, long looser2T2, long looser2T3, boolean looser2IsLocal)
      throws Exception
  {
    String testCase = "test3ServersLate";
    debugInfo("Starting " + testCase);

    // definitions for server names
    final String WINNER  = "localhost:123";
    final String LOOSER1 = "localhost:456";
    final String LOOSER2 = "localhost:789";

    // Create my state
    ServerState mySt = newServerState(
        new CSN(4, 0, myId1),
        new CSN(2, 0, myId2),  // myId2 should not be used inside algo (same for the other ServerStates)
        new CSN(3, 0, myId3)); // myId3 should not be used inside algo (same for the other ServerStates)

    // State for server 1
    ServerState aState1 = newServerState(
        new CSN(looser1T1, 0, myId1),
        new CSN(looser1T2, 0, myId2),
        new CSN(looser1T3, 0, myId3));
    if (looser1IsLocal)
    {
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER1);
    }

    // State for server 2
    ServerState aState2 = newServerState(
        new CSN(winnerT1, 0, myId1),
        new CSN(winnerT2, 0, myId2),
        new CSN(winnerT3, 0, myId3));
    if (winnerIsLocal)
    {
      ReplicationServer.onlyForTestsAddlocalReplicationServer(WINNER);
    }

    // State for server 3
    ServerState aState3 = newServerState(
        new CSN(looser2T1, 0, myId1),
        new CSN(looser2T2, 0, myId2),
        new CSN(looser2T3, 0, myId3));
    if (looser2IsLocal)
    {
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER2);
    }

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, 0, 1),
        newRSInfo(12, WINNER, aState2, 0, 1),
        newRSInfo(13, LOOSER2, aState3, 0, 1));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte) 1, 0);

    ReplicationServer.onlyForTestsClearLocalReplicationServerList();

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    final boolean winnerIsLatestRS = winnerT1 > 4 && looser1T1 == 4 && looser2T1 == 4;
    containsOnly(evals.getEvaluations(),
        entry(11, getEval1(winnerIsLocal, looser1IsLocal, winnerIsLatestRS)),
        entry(12, NOTE_BEST_RS.ordinal()),
        entry(13, getEval1(winnerIsLocal, looser2IsLocal, winnerIsLatestRS)));
  }

  private Integer getEval1(boolean winnerIsLocal, boolean looserIsLocal, boolean winnerIsLatestRS)
  {
    if (winnerIsLocal && !looserIsLocal)
    {
      return NOTE_RS_ON_DIFFERENT_VM_THAN_DS.ordinal();
    }
    else if (winnerIsLatestRS)
    {
      return NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.ordinal();
    }
    return NOTE_RS_LATER_THAN_LOCAL_DS.ordinal();
  }

  @DataProvider(name = "test3ServersMoreCriteria")
  public Object[][] create3ServersMoreCriteriaData() {
    return new Object[][] {
        // Test that a RS is chosen if its group is ok whereas the other parameters
        // are not ok
        { 1L, 1L, 1, false,
          4L, 0L, 2, false,
          4L, 0L, 3, false},

        // Test that a RS is chosen if its genid is ok (all RS with same group)
        // and state is not ok
        { 1L, 0L, 1, false,
          4L, 1L, 1, false,
          4L, 2L, 1, false},

        // Test that a RS is chosen if all servers have wrong genid and group id
        // but it is local
        { 1L, 1L, 2, true,
          4L, 2L, 3, false,
          5L, 3L, 4, false}
        };
  }

  /**
   * Test with 3 replication servers (see data provider).
   */
  @Test(dataProvider =  "test3ServersMoreCriteria")
  public void test3ServersMoreCriteria(
      long winnerT1, long winnerGenId, int winnerGroupId, boolean winnerIsLocal,
      long looser1T1, long looser1GenId, int looser1GroupId, boolean looser1IsLocal,
      long looser2T1, long looser2GenId, int looser2GroupId, boolean looser2IsLocal)
      throws Exception
  {
    String testCase = "test3ServersMoreCriteria";
    debugInfo("Starting " + testCase);

    // definitions for server names
    final String WINNER  = "localhost:123";
    final String LOOSER1 = "localhost:456";
    final String LOOSER2 = "localhost:789";

    // Create my state
    ServerState mySt = newServerState(new CSN(4, 0, myId1));

    // State for server 1
    ServerState aState1 = newServerState(new CSN(looser1T1, 0, myId1));
    if (looser1IsLocal)
    {
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER1);
    }

    // State for server 2
    ServerState aState2 = newServerState(new CSN(winnerT1, 0, myId1));
    if (winnerIsLocal)
    {
      ReplicationServer.onlyForTestsAddlocalReplicationServer(WINNER);
    }

    // State for server 3
    ServerState aState3 = newServerState(new CSN(looser2T1, 0, myId1));
    if (looser2IsLocal)
    {
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER2);
    }

    Map<Integer, ReplicationServerInfo> rsInfos = newRSInfos(
        newRSInfo(11, LOOSER1, aState1, looser1GenId, looser1GroupId),
        newRSInfo(12, WINNER, aState2, winnerGenId, winnerGroupId),
        newRSInfo(13, LOOSER2, aState3, looser2GenId, looser2GroupId));
    RSEvaluations evals =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte) 1, 0);

    ReplicationServer.onlyForTestsClearLocalReplicationServerList();

    assertEquals(evals.getBestRS().getServerURL(), WINNER,
        "Wrong best replication server.");
    containsOnly(evals.getEvaluations(),
        entry(11, getEval2(winnerGroupId == looser1GroupId, winnerIsLocal, looser1IsLocal)),
        entry(12, NOTE_BEST_RS.ordinal()),
        entry(13, getEval2(winnerGroupId == looser2GroupId, winnerIsLocal, looser2IsLocal)));
  }

  private Integer getEval2(boolean sameGroupId, boolean winnerIsLocal, boolean looserIsLocal)
  {
    if (winnerIsLocal && !looserIsLocal)
    {
      return NOTE_RS_ON_DIFFERENT_VM_THAN_DS.ordinal();
    }
    else if (!sameGroupId)
    {
      return NOTE_RS_HAS_DIFFERENT_GROUP_ID_THAN_DS.ordinal();
    }
    return NOTE_RS_HAS_DIFFERENT_GENERATION_ID_THAN_DS.ordinal();
  }

  @SuppressWarnings("unchecked")
  @DataProvider(name = "testComputeBestServerForWeightProvider")
  public Object[][] testComputeBestServerForWeightProvider() {

    Object[][] testData = new Object[24][];
    int idx = 0;

    Map<Integer, ReplicationServerInfo> rsInfos = null;

    /************************
     * First connection tests
     ************************/

    /**
     * 1 RS, no connected DSs
     * Expected winner: the RS
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "AwinnerHost:123", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "AwinnerHost:123", // winner url
    };

    /**
     * 2 RSs with TL=0.5, no connected DSs
     * Excepted winner: first in the list
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "BwinnerHost:123", 0L, (byte)1, 1),
        EMPTY_SET);
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "BwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=1.0 - DS=1 ; RS2: TL=0.5 - CL=0 - DS=0
     * Excepted winner: R2 (still no connected DS)
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1));
    put(rsInfos,
        new RSInfo(12, "CwinnerHost:456", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "CwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=0.5 - DS=1 ; RS2: TL=0.5 - CL=0.5 - DS=1
     * Excepted winner: first in the list as both RSs reached TL
     * and have same weight
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "DwinnerHost:123", 0L, (byte)1, 1),
        newSet(1));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(101));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "DwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=2/3 - DS=2 ; RS2: TL=0.5 - CL=1/3 - DS=1
     * Excepted winner: RS2 -> 2 DSs on each RS
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "EwinnerHost:456", 0L, (byte)1, 1),
        newSet(101));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "EwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=0.5 - DS=1 ; RS2: TL=2/3 - CL=0.5 - DS=1
     * Excepted winner: RS2 -> go to perfect load balance
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1));
    put(rsInfos,
        new RSInfo(12, "FwinnerHost:456", 0L, (byte)1, 2),
        newSet(101));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "FwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=1/3 - DS=1 ; RS2: TL=2/3 - CL=2/3 - DS=2
     * Excepted winner: RS2 -> already load balanced so choose server with the
     * highest weight
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1));
    put(rsInfos,
        new RSInfo(12, "GwinnerHost:456", 0L, (byte)1, 2),
        newSet(101, 102));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "GwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=1/3 - DS=2 ; RS2: TL=2/3 - CL=2/3 - DS=4
     * Excepted winner: RS2 -> already load balanced so choose server with the
     * highest weight
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "HwinnerHost:456", 0L, (byte)1, 2),
        newSet(101, 102, 103, 104));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "HwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/6 - CL=1/6 - DS=1 ; RS2: TL=2/6 - CL=2/6 - DS=2 ; RS3: TL=3/6 - CL=3/6 - DS=3
     * Excepted winner: RS3 -> already load balanced so choose server with the
     * highest weight
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 2),
        newSet(101, 102));
    put(rsInfos,
        new RSInfo(13, "IwinnerHost:789", 0L, (byte)1, 3),
        newSet(201, 202, 203));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "IwinnerHost:789", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=5/10 - CL=3/9 - DS=3 ; RS2: TL=3/10 - CL=5/9 - DS=5 ; RS3: TL=2/10 - CL=1/9 - DS=1
     * Excepted winner: RS1 -> misses more DSs than RS3
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "JwinnerHost:123", 0L, (byte)1, 5),
        newSet(1, 2, 3));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 3),
        newSet(101, 102, 103, 104, 105));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 2),
        newSet(201));

    testData[idx++] = new Object[] {
      rsInfos,
      -1, // current RS id
      -1, // local DS id
      "JwinnerHost:123", // winner url
    };

    /*************************
     * Already connected tests
     *************************/

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=0.5 - DS=1 ; RS2: TL=0.5 - CL=0.5 - DS=1
     * Excepted winner: RS2 (stay connected to it as load correctly spread)
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1));
    put(rsInfos,
        new RSInfo(12, "KwinnerHost:456", 0L, (byte)1, 1),
        newSet(101));

    testData[idx++] = new Object[] {
      rsInfos,
      12, // current RS id
      101, // local DS id
      "KwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=1.0 - DS=2 ; RS2: TL=0.5 - CL=0.0 - DS=0
     * Excepted winner: RS2 (one must disconnect from RS1)
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "LwinnerHost:456", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      1, // local DS id
      null, // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=1.0 - DS=2 ; RS2: TL=0.5 - CL=0.0 - DS=0
     * Excepted winner: RS1 (one server must disconnect from RS1 but it is the
     * one with the lowest id so not DS with server id 2)
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "MwinnerHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      2, // local DS id
      "MwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.3 - DS=6 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.2 - DS=4
     * Excepted winner: RS2 no change as load correctly spread
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 3),
        newSet(1, 2, 3, 4, 5, 6));
    put(rsInfos,
        new RSInfo(12, "NwinnerHost:456", 0L, (byte)1, 4),
        newSet(101, 102, 103, 104, 105, 106, 107, 108));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(201, 202));
    put(rsInfos,
        new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2),
        newSet(301, 302, 303, 304));

    testData[idx++] = new Object[] {
      rsInfos,
      12, // current RS id
      101, // local DS id
      "NwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.2 - DS=4 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.3 - DS=6
     * Excepted winner: RS2: no change load ok on current server and there is the
     * possibility to arrange load for other servers with disconnection from
     * 2 DSs from RS4 and reconnect them to RS1 (we moved these 2 servers from
     * previous test where the loads were ok)
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 3),
        newSet(1, 2, 3, 4));
    put(rsInfos,
        new RSInfo(12, "OwinnerHost:456", 0L, (byte)1, 4),
        newSet(101, 102, 103, 104, 105, 106, 107, 108));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(201, 202));
    put(rsInfos,
        new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2),
        newSet(301, 302, 303, 304, 305, 306));

    testData[idx++] = new Object[] {
      rsInfos,
      12, // current RS id
      101, // local DS id
      "OwinnerHost:456", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.2 - DS=4 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.3 - DS=6
     * Excepted winner: RS4 : 2 DSs should go away from RS4 and server id 302
     * is one of the two lowest ids connected to RS4
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "PwinnerHost:123", 0L, (byte)1, 3),
        newSet(1, 2, 3, 4));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 4),
        newSet(101, 102, 103, 104, 105, 106, 107, 108));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(201, 202));
    put(rsInfos,
        new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2),
        newSet(306, 305, 304, 303, 302, 301));

    testData[idx++] = new Object[] {
      rsInfos,
      14, // current RS id
      302, // local DS id
      null, // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.2 - DS=4 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.3 - DS=6
     * Excepted winner: RS1 : 2 DSs should go away from RS4 but server id 303
     * is not one of the two lowest ids connected to RS4
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 3),
        newSet(1, 2, 3, 4));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 4),
        newSet(101, 102, 103, 104, 105, 106, 107, 108));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(201, 202));
    put(rsInfos,
        new RSInfo(14, "QwinnerHost:1011", 0L, (byte)1, 2),
        newSet(306, 305, 304, 303, 302, 301));

    testData[idx++] = new Object[] {
      rsInfos,
      14, // current RS id
      303, // local DS id
      "QwinnerHost:1011", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.2 - DS=4 ; RS2: TL=0.4 - CL=0.65 - DS=13 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.05 - DS=1
     * Excepted winner: RS2: no change load ok on current server and there is the
     * possibility to arrange load for other servers with disconnection from
     * 2 DSs from RS4 and reconnect them to RS1 (we moved these 2 servers from
     * previous test where the loads were ok)
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte) 1, 3),
        newSet(1, 2, 3, 4));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 4),
        newSet(113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(201, 202));
    put(rsInfos,
        new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2),
        newSet(301));

    testData[idx++] = new Object[] {
      rsInfos,
      12, // current RS id
      105, // local DS id
      null, // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=2/3 - DS=2 ; RS2: TL=0.5 - CL=1/3 - DS=1
     * Excepted winner: RS1. Local server should stay connected to current one
     * as the balance cannot be done. We already have the nearest possible
     * balance to the load goals: disconnection would cause a yoyo effect and
     * the local server would not stop going and coming back to/from the other
     * RS.
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "RwinnerHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(3));

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      1, // local DS id
      "RwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=2/3 - DS=2 ; RS2: TL=0.5 - CL=1/3 - DS=1
     * Excepted winner: RS1. Local server should stay connected to current one
     * as the balance cannot be done. We already have the nearest possible
     * balance to the load goals: disconnection would cause a yoyo effect and
     * the local server would not stop going and coming back to/from the other
     * RS.
     * Note: Same test as before, but not with the lowest local DS server id
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "SwinnerHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(3));

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      2, // local DS id
      "SwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=2/4 - DS=2 ; RS2: TL=1/3 - CL=1/4 - DS=1 ; RS3: TL=1/3 - CL=1/4 - DS=1
     * Excepted winner: RS1. Local server should stay connected to current one
     * as the balance cannot be done. We already have the nearest possible
     * balance to the load goals: disconnection would cause a yoyo effect and
     * the local server would not stop going and coming back between RSs.
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "TwinnerHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(3));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(4));

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      1, // local DS id
      "TwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=3/7 - DS=3 ; RS2: TL=1/3 - CL=2/7 - DS=2 ; RS3: TL=1/3 - CL=2/7 - DS=2
     * Excepted winner: RS1. Local server should stay connected to current one
     * as the balance cannot be done. We already have the nearest possible
     * balance to the load goals: disconnection would cause a yoyo effect and
     * the local server would not stop going and coming back between RSs.
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "UwinnerHost:123", 0L, (byte)1, 1),
        newSet(1, 2, 3));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(4, 5));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        newSet(6, 7));

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      1, // local DS id
      "UwinnerHost:123", // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=2/3 - DS=2 ; RS2: TL=1/3 - CL=1/3 - DS=1 ; RS3: TL=1/3 - CL=0 - DS=0
     * Excepted winner: RS3. Local server should disconnect for reconnection to
     * RS3
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "looserHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(3));
    put(rsInfos,
        new RSInfo(13, "VwinnerHost:789", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      1, // local DS id
      null, // winner url
    };

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=2/3 - DS=2 ; RS2: TL=1/3 - CL=1/3 - DS=1 ; RS3: TL=1/3 - CL=0 - DS=0
     * Excepted winner: RS3. Local server (2) should stay connected while
     * DS server id 1 should disconnect for reconnection to RS3
     */

    rsInfos = new HashMap<>();
    put(rsInfos,
        new RSInfo(11, "WwinnerHost:123", 0L, (byte)1, 1),
        newSet(1, 2));
    put(rsInfos,
        new RSInfo(12, "looserHost:456", 0L, (byte)1, 1),
        newSet(3));
    put(rsInfos,
        new RSInfo(13, "looserHost:789", 0L, (byte)1, 1),
        EMPTY_SET);

    testData[idx++] = new Object[] {
      rsInfos,
      11, // current RS id
      2, // local DS id
      "WwinnerHost:123", // winner url
    };

    return testData;
  }

  private void put(Map<Integer, ReplicationServerInfo> rsInfos, RSInfo rsInfo,
      Set<Integer> connectedDSs)
  {
    ReplicationServerInfo info = new ReplicationServerInfo(rsInfo, connectedDSs);
    rsInfos.put(info.getServerId(), info);
  }

  /**
   * Test the method that chooses the best RS using the RS weights.
   */
  @Test(dataProvider =  "testComputeBestServerForWeightProvider")
  public void testComputeBestServerForWeight(
      Map<Integer, ReplicationServerInfo> servers, int currentRsServerId,
      int localServerId, String winnerUrl)
      throws Exception
  {
    String testCase = "testComputeBestServerForWeight";
    debugInfo("Starting " + testCase);

    final RSEvaluations evals = new RSEvaluations(localServerId, servers);
    computeBestServerForWeight(evals, currentRsServerId, localServerId);
    final ReplicationServerInfo bestServer = evals.getBestRS();

    if (winnerUrl == null)
    {
      // We expect null
      String url = null;
      if (bestServer != null)
      {
        url = bestServer.getServerURL();
      }
      assertNull(bestServer, "The best server should be null but is: " + url);
    }
    else
    {
      assertNotNull(bestServer, "The best server should not be null");
      assertEquals(bestServer.getServerURL(),
        winnerUrl, "Wrong best replication server: " + bestServer.getServerURL());
    }
  }
}
