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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.HashMap;
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
import org.opends.server.replication.common.ServerState;
import org.testng.annotations.Test;

/**
 * Test the algorithm for find the best replication server among the configured
 * ones.
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;

    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;

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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

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
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)2));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)2));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)2));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(3L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER2, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(3L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER2, new ServerInfo(aState, (byte)2));

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
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
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
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
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
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 2 replication servers, late.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test2ServersLate() throws Exception
  {
    String testCase = "test2ServersLate";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(2L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 3 replication servers, late.
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test3ServersLate() throws Exception
  {
    String testCase = "test3ServersLate";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;
    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";
    final String LOOSER2 = "looser2";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(4L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER2, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 6 replication servers, some up, some late, one null
   *
   * @throws Exception If a problem occurred
   */
  @Test
  public void test6ServersMixed() throws Exception
  {
    String testCase = "test6ServersMixed";

    debugInfo("Starting " + testCase);

    // definitions for server ids
    short myId1 = 1;
    short myId2 = 2;
    short myId3 = 3;

    // definitions for server names
    final String WINNER = "winner";
    final String LOOSER1 = "looser1";
    final String LOOSER2 = "looser2";
    final String LOOSER3 = "looser3";
    final String LOOSER4 = "looser4";
    final String LOOSER5 = "looser5";

    // Create my state
    ServerState mySt = new ServerState();
    ChangeNumber cn = new ChangeNumber(5L, 0, myId1);
    mySt.update(cn);
    cn = new ChangeNumber(2L, 0, myId2); // Should not be used inside algo
    mySt.update(cn);
    cn = new ChangeNumber(3L, 0, myId3); // Should not be used inside algo
    mySt.update(cn);

    // Create replication servers info list
    HashMap<String, ServerInfo> rsInfos = new HashMap<String, ServerInfo>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(4L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER1, new ServerInfo(aState, (byte)1));

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(7L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(6L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(5L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER2, new ServerInfo(aState, (byte)1));

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER3, new ServerInfo(aState, (byte)1));

    // State for server 4
    aState = new ServerState();
    cn = new ChangeNumber(6L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(6L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(8L, 0, myId3);
    aState.update(cn);
    rsInfos.put(WINNER, new ServerInfo(aState, (byte)1));

    // State for server 5 (null one for our serverid)
    aState = new ServerState();
    cn = new ChangeNumber(5L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(5L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER4, new ServerInfo(aState, (byte)1));

    // State for server 6
    aState = new ServerState();
    cn = new ChangeNumber(5L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(7L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(6L, 0, myId3);
    aState.update(cn);
    rsInfos.put(LOOSER5, new ServerInfo(aState, (byte)1));

    String bestServer =
      computeBestReplicationServer(mySt, rsInfos, myId1, " ", (byte)1);

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
}
