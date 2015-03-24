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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.Set;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test the ServerState.
 */
@SuppressWarnings("javadoc")
public class ServerStateTest extends ReplicationTestCase
{

  /**
   * Create CSN Data.
   */
  @DataProvider(name = "csnData")
  public Object[][] createCSNData()
  {
    return new Object[][] {
       {new CSN(1, 0, 1)},
       {new CSN(TimeThread.getTime(),  123,  45)}
    };
  }

  /**
   * Create a new ServerState object.
   */
  @Test(dataProvider = "csnData")
  public void serverStateTest(CSN csn) throws Exception
  {
    // Check constructor
    ServerState serverState = new ServerState() ;

    // Check Load
    // serverState.loadState() ;
    // TODO Check result;

    // Check update
    assertFalse(serverState.update((CSN)null));
    assertTrue(serverState.update(csn));
    assertFalse(serverState.update(csn));
    CSN csn1 = new CSN(csn.getTime() + 1, csn.getSeqnum(), csn.getServerId());
    CSN csn2 = new CSN(csn1.getTime(), csn1.getSeqnum() + 1, csn1.getServerId());
    CSN csn3 = new CSN(csn2.getTime(), csn2.getSeqnum(), (csn2.getServerId() + 1));

    assertTrue(serverState.update(csn1));
    assertTrue(serverState.update(csn2));
    assertTrue(serverState.update(csn3));

    // Check toStringSet
    CSN[] csns = { csn2, csn3 };
    Set<String> stringSet = serverState.toStringSet();
    assertEquals(csns.length, stringSet.size());
    // TODO Check the value

    // Check getMaxCSN
    assertEquals(csn2.compareTo(serverState.getCSN(csn2.getServerId())), 0);
    assertEquals(csn3.compareTo(serverState.getCSN(csn3.getServerId())), 0);

    // Check the toString
    String stringRep = serverState.toString();
    assertTrue(stringRep.contains(csn2.toString()));
    assertTrue(stringRep.contains(csn3.toString()));
  }

  /**
   * Create a new ServerState object.
   */
  @Test(dataProvider = "csnData")
  public void serverStateReloadTest(CSN csn) throws Exception
  {
    CSN csn1 = new CSN(csn.getTime() + 1, csn.getSeqnum(), csn.getServerId());
    CSN csn3 = new CSN(csn1.getTime(), csn1.getSeqnum(), (csn1.getServerId() + 1));

    ServerState state1 = new ServerState();
    state1.update(csn1);
    state1.update(csn3);

    ServerState state2 = new ServerState();
    state2.reload(state1);

    assertEquals(state1.toString(), state2.toString()) ;
  }

  @Test
  public void testCover() throws Exception
  {
    final CSN csn1Server1 = new CSN(1, 0, 1);
    final CSN csn2Server1 = new CSN(2, 0, 1);
    final CSN csn1Server2 = new CSN(1, 0, 2);
    final CSN csn0Server3 = new CSN(0, 0, 3);

    final ServerState state = new ServerState();
    assertFalse(state.cover(csn1Server1));
    assertFalse(state.cover(csn2Server1));
    assertFalse(state.cover(csn1Server2));
    assertFalse(state.cover(csn0Server3));

    state.update(csn1Server1);
    assertTrue(state.cover(csn1Server1));
    assertFalse(state.cover(csn2Server1));
    assertFalse(state.cover(csn1Server2));
    assertFalse(state.cover(csn0Server3));

    state.update(csn1Server2);
    assertTrue(state.cover(csn1Server1));
    assertFalse(state.cover(csn2Server1));
    assertTrue(state.cover(csn1Server2));
    assertFalse(state.cover(csn0Server3));

    state.update(csn2Server1);
    assertTrue(state.cover(csn1Server1));
    assertTrue(state.cover(csn2Server1));
    assertTrue(state.cover(csn1Server2));
    assertFalse(state.cover(csn0Server3));
  }

  @Test
  public void testRemoveCSN() throws Exception
  {
    final CSN csn1Server1 = new CSN(1, 0, 1);
    final CSN csn2Server1 = new CSN(2, 0, 1);
    final CSN csn1Server2 = new CSN(1, 0, 2);

    final ServerState state = new ServerState();
    assertTrue(state.update(csn1Server1));

    // test 1
    assertFalse(state.removeCSN(null));

    // test 2
    assertEquals(csn1Server1, state.getCSN(1));
    assertFalse(state.removeCSN(csn2Server1));
    assertEquals(csn1Server1, state.getCSN(1));

    // test 3
    assertNull(state.getCSN(2));
    assertFalse(state.removeCSN(csn1Server2));
    assertNull(state.getCSN(2));

    // test 4
    assertEquals(csn1Server1, state.getCSN(1));
    assertTrue(state.removeCSN(csn1Server1));
    assertNull(state.getCSN(1));
  }
}
