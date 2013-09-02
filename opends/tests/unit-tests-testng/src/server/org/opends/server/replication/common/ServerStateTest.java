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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.Set;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test the ServerState
 */
@SuppressWarnings("javadoc")
public class ServerStateTest extends ReplicationTestCase
{

  /**
   * Create CSN Data
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
   * Create a new ServerState object
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
    CSN csn1, csn2, csn3;
    csn1 = new CSN(csn.getTime() + 1, csn.getSeqnum(), csn.getServerId());
    csn2 = new CSN(csn1.getTime(), csn1.getSeqnum() + 1, csn1.getServerId());
    csn3 = new CSN(csn2.getTime(), csn2.getSeqnum(), (csn2.getServerId() + 1));

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

    // Check getBytes
    byte[] b = serverState.getBytes();
    ServerState generatedServerState = new ServerState(b,0,b.length -1) ;
    assertEquals(b, generatedServerState.getBytes()) ;
  }

  /**
   * Create a new ServerState object
   */
  @Test(dataProvider = "csnData")
  public void serverStateReloadTest(CSN csn)
  throws Exception
  {
    CSN csn1, csn3;
    csn1 = new CSN(csn.getTime() + 1, csn.getSeqnum(), csn.getServerId());
    csn3 = new CSN(csn1.getTime(), csn1.getSeqnum(), (csn1.getServerId() + 1));

    ServerState state1 = new ServerState();
    state1.update(csn1);
    state1.update(csn3);

    ServerState state2 = new ServerState();
    state2.reload(state1);

    assertEquals(state1.toString(), state2.toString()) ;

  }

}
