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
import static org.opends.server.replication.plugin.ReplicationBroker.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.*;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.DN;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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

  private void debugInfo(String message, Exception e)
  {
    debugInfo(message + stackTraceToSingleLineString(e));
  }

  /**
   * Set up the environment.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
  // Don't need server context in these tests
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
  // Don't need server context in these tests
  }

  /**
   * Test with one replication server, nobody has a change number (simulates)
   * very first connection.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with one replication server, only replication server has a non null
   * changenumber for ds server id
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with one replication server, only ds server has a non null
   * changenumber for ds server id but rs has a null one.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }

  /**
   * Test with one replication server, up to date.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }

  /**
   * Test with 2 replication servers, up to date.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER1, aState);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with 3 replication servers, up to date.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER1, aState);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(3L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(2L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER2, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with one replication server, late.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(1L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with 2 replication servers, late.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(0L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER1, aState);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with 3 replication servers, late.
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(1L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER1, aState);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(0L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(2L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER2, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
  
  /**
   * Test with 6 replication servers, some up, some late, one null
   *
   * @throws Exception If a problem occured
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

    // Create replication servers state list
    HashMap<String, ServerState> rsStates = new HashMap<String, ServerState>();

    // State for server 1
    ServerState aState = new ServerState();
    cn = new ChangeNumber(4L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER1, aState);

    // State for server 2
    aState = new ServerState();
    cn = new ChangeNumber(7L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(6L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(5L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER2, aState);

    // State for server 3
    aState = new ServerState();
    cn = new ChangeNumber(3L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(10L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER3, aState);
    
    // State for server 4
    aState = new ServerState();
    cn = new ChangeNumber(6L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(6L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(8L, 0, myId3);
    aState.update(cn);
    rsStates.put(WINNER, aState);
    
    // State for server 5 (null one for our serverid)
    aState = new ServerState();
    cn = new ChangeNumber(5L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(5L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER4, aState);
    
    // State for server 6
    aState = new ServerState();
    cn = new ChangeNumber(5L, 0, myId1);
    aState.update(cn);
    cn = new ChangeNumber(7L, 0, myId2);
    aState.update(cn);
    cn = new ChangeNumber(6L, 0, myId3);
    aState.update(cn);
    rsStates.put(LOOSER5, aState);

    String bestServer =
      computeBestReplicationServer(mySt, rsStates, myId1, new DN());

    assertEquals(bestServer, WINNER, "Wrong best replication server.");
  }
}
