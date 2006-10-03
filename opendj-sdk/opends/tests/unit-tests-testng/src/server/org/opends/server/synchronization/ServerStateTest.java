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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import static org.testng.Assert.*;

import java.util.Set;

import org.opends.server.types.DN;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the ServerState
 */
public class ServerStateTest extends SynchronizationTestCase
{

  /**
   * Create ChangeNumber Data
   */
  @DataProvider(name = "changeNumberData")
  public Object[][] createChangeNumberData() {
    return new Object[][] {
       {new ChangeNumber(1, (short) 0, (short) 1)},
       {new ChangeNumber(TimeThread.getTime(), (short) 123, (short) 45)}
    };
  }

  /**
   * Create a new ServerState object
   */
  @Test(dataProvider = "changeNumberData")
  public void serverStateTest(ChangeNumber cn)
         throws Exception
  {
    // Check constructor
    DN dn = DN.decode("cn=com");
    ServerState serverState = new ServerState(dn) ;

    // Check Load
    // serverState.loadState() ;
    // TODO Check result

    // Check getServerStateDn()
    DN returned_DN = serverState.getServerStateDn();
    // Check that the returned DN stays below dn
    assertTrue(dn.isAncestorOf(returned_DN));

    // Check update
    assertFalse(serverState.update(null));
    assertTrue(serverState.update(cn));
    assertFalse(serverState.update(cn));
    ChangeNumber cn1, cn2, cn3;
    cn1 = new ChangeNumber(cn.getTime()+1,cn.getSeqnum(),cn.getServerId());
    cn2 = new ChangeNumber(cn1.getTime(),cn1.getSeqnum()+1,cn1.getServerId());
    cn3 = new ChangeNumber(cn2.getTime(),cn2.getSeqnum(),(short)(cn2.getServerId()+1));

    assertTrue(serverState.update(cn1)) ;
    assertTrue(serverState.update(cn2)) ;
    assertTrue(serverState.update(cn3)) ;

    // Check toStringSet
    ChangeNumber[] cns = {cn2,cn3};
    Set<String> stringSet = serverState.toStringSet();
    assertEquals(cns.length, stringSet.size());
    // TODO Check the value

    // Check getMaxChangeNumber
    assertEquals(cn2.compareTo(serverState.getMaxChangeNumber(cn2.getServerId())),0);
    assertEquals(cn3.compareTo(serverState.getMaxChangeNumber(cn3.getServerId())),0);

    // Check the toString
    String stringRep = serverState.toString();
    assertTrue(stringRep.contains(cn2.toString()));
    assertTrue(stringRep.contains(cn3.toString()));

    // Check getBytes
    byte[] b = serverState.getBytes();
    ServerState generatedServerState = new ServerState(b,0,b.length -1) ;
    assertEquals(b, generatedServerState.getBytes()) ;

  }
}
