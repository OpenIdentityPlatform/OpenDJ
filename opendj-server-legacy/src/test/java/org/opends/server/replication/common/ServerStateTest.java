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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.common;

import java.util.Set;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/** Test the ServerState. */
@SuppressWarnings("javadoc")
public class ServerStateTest extends ReplicationTestCase
{
  /** Create CSN Data. */
  @DataProvider(name = "csnData")
  public Object[][] createCSNData()
  {
    return new Object[][] {
       {new CSN(1, 0, 1)},
       {new CSN(TimeThread.getTime(),  123,  45)}
    };
  }

  /** Create a new ServerState object. */
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

  /**
   * An update that does not change the state must leave the saved status alone:
   * the status is only cleared for a change that has actually been applied.
   */
  @Test
  public void updateThatChangesNothingKeepsTheStateSaved() throws Exception
  {
    final ServerState state = new ServerState();
    final CSN csn = new CSN(TimeThread.getTime(), 1, 1);
    assertTrue(state.update(csn));

    state.setSaved(true);
    assertFalse(state.update(csn), "the very same CSN is not a meaningful update");
    assertTrue(state.isSaved(), "a duplicate CSN must not clear the saved status");

    final CSN olderCSN = new CSN(csn.getTime() - 1, csn.getSeqnum(), csn.getServerId());
    assertFalse(state.update(olderCSN), "an older CSN is not a meaningful update");
    assertTrue(state.isSaved(), "an older CSN must not clear the saved status");

    assertFalse(state.update((CSN) null));
    assertTrue(state.isSaved(), "a null CSN must not clear the saved status");
  }

  /**
   * Emptying the state is a change like any other: it must not leave the state
   * looking like what persistent storage holds. Emptying one that is already
   * empty changes nothing, and must leave the saved status alone.
   */
  @Test
  public void clearMarksTheStateUnsaved() throws Exception
  {
    final ServerState state = new ServerState();
    assertTrue(state.update(new CSN(TimeThread.getTime(), 1, 1)));
    state.setSaved(true);

    state.clear();
    assertFalse(state.isSaved(), "clearing the state must not leave it marked as saved");

    state.setSaved(true);
    state.clear();
    assertTrue(state.isSaved(), "clearing an already empty state must not clear the saved status");
  }
}
