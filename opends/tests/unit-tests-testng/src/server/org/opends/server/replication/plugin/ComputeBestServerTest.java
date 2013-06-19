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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.opends.server.replication.service.ReplicationBroker.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.testng.Assert.*;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the algorithm for finding the best replication server among the
 * configured ones.
 */
public class ComputeBestServerTest extends ReplicationTestCase
{

  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }

  /**
   * Test with one replication server, nobody has a change number (simulates)
   * very first connection.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testNullCNBoth() throws Exception
  {
    String testCase = "testNullCNBoth";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;

    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with one replication server, only replication server has a non null
   * changenumber for ds server id
   * @throws Exception If a problem occurred
   */
  @Test
  public void testNullCNDS() throws Exception
  {
    String testCase = "testNullCNDS";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with one replication server, only ds server has a non null
   * changenumber for ds server id but rs has a null one.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testNullCNRS() throws Exception
  {
    String testCase = "testNullCNRS";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;

    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with one replication server, up to date.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test1ServerUp() throws Exception
  {
    String testCase = "test1ServerUp";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 2 replication servers, up to date.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test2ServersUp() throws Exception
  {
    String testCase = "test2ServersUp";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, LOOSER1, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 2 replication servers, up to date, but 2 different group ids.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testDiffGroup2ServersUp() throws Exception
  {
    String testCase = "testDiffGroup2ServersUp";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    // This server has less changes than the other one but it has the same
    // group id as us so he should be the winner
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, LOOSER1, null, 0, aState, 0L,
      false, (byte)2, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 2 replication servers, none of them from our group id.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testNotOurGroup() throws Exception
  {
    String testCase = "testNotOurGroup";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, LOOSER1, null, 0, aState, 0L,
      false, (byte)2, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, WINNER, null, 0, aState, 0L,
      false, (byte)2, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 3 replication servers, up to date.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test3ServersUp() throws Exception
  {
    String testCase = "test3ServersUp";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";
    final String LOOSER2 = "looser2";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, LOOSER1, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(4L, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, LOOSER2, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(13, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(13, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 3 replication servers, up to date, but 2 different group ids.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void testDiffGroup3ServersUp() throws Exception
  {
    String testCase = "testDiffGroup3ServersUp";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";
    final String LOOSER2 = "looser2";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, LOOSER1, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(3L, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, LOOSER2, null, 0, aState, 0L,
      false, (byte)2, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    // This server has less changes than looser2 but it has the same
    // group id as us so he should be the winner
    replServerStartMsg =
      new ReplServerStartMsg(13, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(13, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  /**
   * Test with one replication server, late.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test1ServerLate() throws Exception
  {
    String testCase = "test1ServerLate";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(1L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte)1, 0L);

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  @DataProvider(name = "create3ServersData")
  public Object[][] create3ServersData() {
    return new Object[][] {
        // first RS is up to date, the others are late none is local
        { 4, 2, 3, false, 1, 2, 3, false, 2, 3, 4, false},

        // test that the local RS  is chosen first when all up to date
        { 4, 2, 3, true, 4, 2, 3, false, 4, 2, 3, false},

        // test that the local ServerID is more important than the others
        { 4, 0, 0, false, 2, 100, 100, false, 1, 100, 100, false},

        // test that a remote RS is chosen first when up to date when the local
        // one is late
        { 4, 1, 1, false, 3, 1, 1, true, 3, 1, 1, false},

        // test that the local RS is not chosen first when it is missing
        // local changes
        { 4, 1, 1, false, 3, 2, 3, false, 1, 1, 1, true},

        // test that a RS which is more up to date than the DS is chosen
        { 5, 1, 1, false, 2, 0, 0, false, 1, 1, 1, false},

        // test that a RS which is more up to date than the DS is chosen even
        // is some RS with the same last change from the DS
        { 5, 1, 1, false, 4, 0, 0, false, 4, 1, 1, false},

        // test that the local RS is chosen first when it is missing
        // the same local changes as the other RSs
        { 3, 1, 1, true, 2, 1, 1, false, 3, 1, 1, false},
        };
  }

  /**
   * Test with 3 replication servers (see data provider)
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

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;

    // definitions for server names
    final String WINNER  = "localhost:123";
    final String LOOSER1 = "localhost:456";
    final String LOOSER2 = "localhost:789";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(4L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(looser1T1, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(looser1T2, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(looser1T3, 0, myId3);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, LOOSER1, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));
    if (looser1IsLocal)
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER1);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(winnerT1, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(winnerT2, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(winnerT3, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, WINNER, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));
    if (winnerIsLocal)
      ReplicationServer.onlyForTestsAddlocalReplicationServer(WINNER);

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(looser2T1, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(looser2T2, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(looser2T3, 0, myId3);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(13, LOOSER2, null, 0, aState, 0L,
      false, (byte)1, 0);
    rsInfos.put(13, ReplicationServerInfo.newInstance(replServerStartMsg));
    if (looser2IsLocal)
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER2);

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte) 1,
      0L);

    ReplicationServer.onlyForTestsClearLocalReplicationServerList();

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  @DataProvider(name = "test3ServersMoreCriteria")
  public Object[][] create3ServersMoreCriteriaData() {
    return new Object[][] {
        // Test that a RS is chosen if its group is ok whereas the other parameters
        // are not ok
        { 1L, 1L, (byte)1, false, 4L, 0L, (byte)2, false, 4L, 0L, (byte)3, false},

        // Test that a RS is chosen if its genid is ok (all RS with same group)
        // and state is not ok
        { 1L, 0L, (byte)1, false, 4L, 1L, (byte)1, false, 4L, 2L, (byte)1, false},

        // Test that a RS is chosen if all servers have wrong genid and group id
        // but it is local
        { 1L, 1L, (byte)2, true, 4L, 2L, (byte)3, false, 5L, 3L, (byte)4, false}
        };
  }

  /**
   * Test with 3 replication servers (see data provider)
   */
  @Test(dataProvider =  "test3ServersMoreCriteria")
  public void test3ServersMoreCriteria(
      long winnerT1, long winnerGenId, byte winnerGroupId, boolean winnerIsLocal,
      long looser1T1, long looser1GenId, byte looser1GroupId, boolean looser1IsLocal,
      long looser2T1, long looser2GenId, byte looser2GroupId, boolean looser2IsLocal)
      throws Exception
  {
    String testCase = "test3ServersMoreCriteria";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    int myId1 = 1;
    int myId2 = 2;
    int myId3 = 3;

    // definitions for server names
    final String WINNER  = "localhost:123";
    final String LOOSER1 = "localhost:456";
    final String LOOSER2 = "localhost:789";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(4L, 0, myId1);
    mySt.update(cn);

    // Create replication servers info list
    HashMap<Integer, ReplicationServerInfo> rsInfos =
      new HashMap<Integer, ReplicationServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(looser1T1, 0, myId1);
    aState.update(cn);
    ReplServerStartMsg replServerStartMsg =
      new ReplServerStartMsg(11, LOOSER1, null, 0, aState, looser1GenId,
      false, looser1GroupId, 0);
    rsInfos.put(11, ReplicationServerInfo.newInstance(replServerStartMsg));
    if (looser1IsLocal)
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER1);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(winnerT1, 0, myId1);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(12, WINNER, null, 0, aState, winnerGenId,
      false, winnerGroupId, 0);
    rsInfos.put(12, ReplicationServerInfo.newInstance(replServerStartMsg));
    if (winnerIsLocal)
      ReplicationServer.onlyForTestsAddlocalReplicationServer(WINNER);

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(looser2T1, 0, myId1);
    aState.update(cn);
    replServerStartMsg =
      new ReplServerStartMsg(13, LOOSER2, null, 0, aState, looser2GenId,
      false, looser2GroupId, 0);
    rsInfos.put(13, ReplicationServerInfo.newInstance(replServerStartMsg));
    if (looser2IsLocal)
      ReplicationServer.onlyForTestsAddlocalReplicationServer(LOOSER2);

    ReplicationServerInfo bestServer =
      computeBestReplicationServer(true, -1, mySt, rsInfos, myId1, (byte) 1,
      0L);

    ReplicationServer.onlyForTestsClearLocalReplicationServerList();

    assertEquals(bestServer.getServerURL(),
      WINNER, "Wrong best replication server.");
  }

  @DataProvider(name = "testComputeBestServerForWeightProvider")
  public Object[][] testComputeBestServerForWeightProvider() {

    Object[][] testData = new Object[24][];

    HashMap<Integer, ReplicationServerInfo> rsInfos = null;
      new HashMap<Integer, ReplicationServerInfo>();
    RSInfo rsInfo = null;
    List<Integer> connectedDSs = null;
    Object[] params = null;

    /************************
     * First connection tests
     ************************/

    /**
     * 1 RS, no connected DSs
     * Expected winner: the RS
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "AwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "AwinnerHost:123"; // winner url
    testData[0] = params;

    /**
     * 2 RSs with TL=0.5, no connected DSs
     * Excepted winner: first in the list
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "BwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = rsInfos.values().iterator().next().getServerURL(); // winner url
    testData[1] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=1.0 - DS=1 ; RS2: TL=0.5 - CL=0 - DS=0
     * Excepted winner: R2 (still no connected DS)
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "CwinnerHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "CwinnerHost:456"; // winner url
    testData[2] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=0.5 - DS=1 ; RS2: TL=0.5 - CL=0.5 - DS=1
     * Excepted winner: first in the list as both RSs reached TL
     * and have same weight
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "DwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = rsInfos.values().iterator().next().getServerURL(); // winner url
    testData[3] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=2/3 - DS=2 ; RS2: TL=0.5 - CL=1/3 - DS=1
     * Excepted winner: RS2 -> 2 DSs on each RS
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "EwinnerHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "EwinnerHost:456"; // winner url
    testData[4] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=0.5 - DS=1 ; RS2: TL=2/3 - CL=0.5 - DS=1
     * Excepted winner: RS2 -> go to perfect load balance
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "FwinnerHost:456", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "FwinnerHost:456"; // winner url
    testData[5] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=1/3 - DS=1 ; RS2: TL=2/3 - CL=2/3 - DS=2
     * Excepted winner: RS2 -> already load balanced so choose server with the
     * highest weight
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "GwinnerHost:456", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "GwinnerHost:456"; // winner url
    testData[6] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=1/3 - DS=2 ; RS2: TL=2/3 - CL=2/3 - DS=4
     * Excepted winner: RS2 -> already load balanced so choose server with the
     * highest weight
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "HwinnerHost:456", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    connectedDSs.add(103);
    connectedDSs.add(104);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "HwinnerHost:456"; // winner url
    testData[7] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/6 - CL=1/6 - DS=1 ; RS2: TL=2/6 - CL=2/6 - DS=2 ; RS3: TL=3/6 - CL=3/6 - DS=3
     * Excepted winner: RS3 -> already load balanced so choose server with the
     * highest weight
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "IwinnerHost:789", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    connectedDSs.add(202);
    connectedDSs.add(203);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "IwinnerHost:789"; // winner url
    testData[8] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=5/10 - CL=3/9 - DS=3 ; RS2: TL=3/10 - CL=5/9 - DS=5 ; RS3: TL=2/10 - CL=1/9 - DS=1
     * Excepted winner: RS1 -> misses more DSs than RS3
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "JwinnerHost:123", 0L, (byte)1, 5);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    connectedDSs.add(103);
    connectedDSs.add(104);
    connectedDSs.add(105);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = -1; // current RS id
    params[2] = -1; // local DS id
    params[3] = "JwinnerHost:123"; // winner url
    testData[9] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "KwinnerHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 12; // current RS id
    params[2] = 101; // local DS id
    params[3] = "KwinnerHost:456"; // winner url
    testData[10] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=1.0 - DS=2 ; RS2: TL=0.5 - CL=0.0 - DS=0
     * Excepted winner: RS2 (one must disconnect from RS1)
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "LwinnerHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 1; // local DS id
    params[3] = null; // winner url
    testData[11] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.5 - CL=1.0 - DS=2 ; RS2: TL=0.5 - CL=0.0 - DS=0
     * Excepted winner: RS1 (one server must disconnect from RS1 but it is the
     * one with the lowest id so not DS with server id 2)
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "MwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 2; // local DS id
    params[3] = "MwinnerHost:123"; // winner url
    testData[12] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.3 - DS=6 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.2 - DS=4
     * Excepted winner: RS2 no change as load correctly spread
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    connectedDSs.add(4);
    connectedDSs.add(5);
    connectedDSs.add(6);

    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "NwinnerHost:456", 0L, (byte)1, 4);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    connectedDSs.add(103);
    connectedDSs.add(104);
    connectedDSs.add(105);
    connectedDSs.add(106);
    connectedDSs.add(107);
    connectedDSs.add(108);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    connectedDSs.add(202);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(301);
    connectedDSs.add(302);
    connectedDSs.add(303);
    connectedDSs.add(304);
    rsInfos.put(14, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 12; // current RS id
    params[2] = 101; // local DS id
    params[3] = "NwinnerHost:456"; // winner url
    testData[13] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    connectedDSs.add(4);

    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "OwinnerHost:456", 0L, (byte)1, 4);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    connectedDSs.add(103);
    connectedDSs.add(104);
    connectedDSs.add(105);
    connectedDSs.add(106);
    connectedDSs.add(107);
    connectedDSs.add(108);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    connectedDSs.add(202);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(301);
    connectedDSs.add(302);
    connectedDSs.add(303);
    connectedDSs.add(304);
    connectedDSs.add(305);
    connectedDSs.add(306);
    rsInfos.put(14, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 12; // current RS id
    params[2] = 101; // local DS id
    params[3] = "OwinnerHost:456"; // winner url
    testData[14] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.2 - DS=4 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.3 - DS=6
     * Excepted winner: RS4 : 2 DSs should go away from RS4 and server id 302
     * is one of the two lowest ids connected to RS4
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "PwinnerHost:123", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    connectedDSs.add(4);

    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 4);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    connectedDSs.add(103);
    connectedDSs.add(104);
    connectedDSs.add(105);
    connectedDSs.add(106);
    connectedDSs.add(107);
    connectedDSs.add(108);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    connectedDSs.add(202);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(306);
    connectedDSs.add(305);
    connectedDSs.add(304);
    connectedDSs.add(303);
    connectedDSs.add(302);
    connectedDSs.add(301);

    rsInfos.put(14, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 14; // current RS id
    params[2] = 302; // local DS id
    params[3] = null; // winner url
    testData[15] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=0.3 - CL=0.2 - DS=4 ; RS2: TL=0.4 - CL=0.4 - DS=8 ;
     * RS3: TL=0.1 - CL=0.1 - DS=2 ; RS4: TL=0.2 - CL=0.3 - DS=6
     * Excepted winner: RS1 : 2 DSs should go away from RS4 but server id 303
     * is not one of the two lowest ids connected to RS4
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    connectedDSs.add(4);

    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 4);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(101);
    connectedDSs.add(102);
    connectedDSs.add(103);
    connectedDSs.add(104);
    connectedDSs.add(105);
    connectedDSs.add(106);
    connectedDSs.add(107);
    connectedDSs.add(108);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    connectedDSs.add(202);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(14, "QwinnerHost:1011", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(306);
    connectedDSs.add(305);
    connectedDSs.add(304);
    connectedDSs.add(303);
    connectedDSs.add(302);
    connectedDSs.add(301);

    rsInfos.put(14, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 14; // current RS id
    params[2] = 303; // local DS id
    params[3] = "QwinnerHost:1011"; // winner url
    testData[16] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 3);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    connectedDSs.add(4);

    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 4);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(113);
    connectedDSs.add(112);
    connectedDSs.add(111);
    connectedDSs.add(110);
    connectedDSs.add(109);
    connectedDSs.add(108);
    connectedDSs.add(107);
    connectedDSs.add(106);
    connectedDSs.add(105);
    connectedDSs.add(104);
    connectedDSs.add(103);
    connectedDSs.add(102);
    connectedDSs.add(101);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(201);
    connectedDSs.add(202);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(14, "looserHost:1011", 0L, (byte)1, 2);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(301);

    rsInfos.put(14, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 12; // current RS id
    params[2] = 105; // local DS id
    params[3] = null; // winner url
    testData[17] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "RwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(3);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 1; // local DS id
    params[3] = "RwinnerHost:123"; // winner url
    testData[18] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "SwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(3);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 2; // local DS id
    params[3] = "SwinnerHost:123"; // winner url
    testData[19] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "TwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(3);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(4);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 1; // local DS id
    params[3] = "TwinnerHost:123"; // winner url
    testData[20] = params;

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

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "UwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    connectedDSs.add(3);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(4);
    connectedDSs.add(5);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(6);
    connectedDSs.add(7);
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 1; // local DS id
    params[3] = "UwinnerHost:123"; // winner url
    testData[21] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=2/3 - DS=2 ; RS2: TL=1/3 - CL=1/3 - DS=1 ; RS3: TL=1/3 - CL=0 - DS=0
     * Excepted winner: RS3. Local server should disconnect for reconnection to
     * RS3
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "looserHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(3);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "VwinnerHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 1; // local DS id
    params[3] = null; // winner url
    testData[22] = params;

    /**
     * TL = target load
     * CL = current load
     * DS = connected DSs number
     * RS1: TL=1/3 - CL=2/3 - DS=2 ; RS2: TL=1/3 - CL=1/3 - DS=1 ; RS3: TL=1/3 - CL=0 - DS=0
     * Excepted winner: RS3. Local server (2) should stay connected while
     * DS server id 1 should disconnect for reconnection to RS3
     */

    rsInfos = new HashMap<Integer, ReplicationServerInfo>();

    rsInfo = new RSInfo(11, "WwinnerHost:123", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(1);
    connectedDSs.add(2);
    rsInfos.put(11, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(12, "looserHost:456", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    connectedDSs.add(3);
    rsInfos.put(12, new ReplicationServerInfo(rsInfo, connectedDSs));

    rsInfo = new RSInfo(13, "looserHost:789", 0L, (byte)1, 1);
    connectedDSs = new ArrayList<Integer>();
    rsInfos.put(13, new ReplicationServerInfo(rsInfo, connectedDSs));

    params = new Object[4];
    params[0] = rsInfos;
    params[1] = 11; // current RS id
    params[2] = 2; // local DS id
    params[3] = "WwinnerHost:123"; // winner url
    testData[23] = params;

    return testData;
  }

  /**
   * Test the method that chooses the best RS using the RS weights
   */
  @Test(dataProvider =  "testComputeBestServerForWeightProvider")
  public void testComputeBestServerForWeight(
      Map<Integer, ReplicationServerInfo> servers, int currentRsServerId,
      int localServerId, String winnerUrl)
      throws Exception
  {
    String testCase = "testComputeBestServerForWeight";

    debugInfo("Starting " + testCase);

    ReplicationServerInfo bestServer =
      computeBestServerForWeight(servers, currentRsServerId, localServerId);

    if (winnerUrl == null)
    {
      // We expect null
      String url = null;
      if (bestServer != null)
      {
        url = bestServer.getServerURL();
      }
      assertNull(bestServer, "The best server should be null but is: " + url);
    } else
    {
      assertNotNull(bestServer, "The best server should not be null");
      assertEquals(bestServer.getServerURL(),
        winnerUrl, "Wrong best replication server: " + bestServer.getServerURL());
    }
  }
}
