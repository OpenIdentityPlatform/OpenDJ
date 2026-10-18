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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2015 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.common;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class CSNGeneratorTest extends ReplicationTestCase
{
  /**
   * Test the adjust method of CSNGenerator.
   */
  @Test
  public void adjustTest()
  {
    CSNGenerator generator = new CSNGenerator(5, TimeThread.getTime());
    CSN csn = generator.newCSN();

    CSN csn1 = new CSN(csn.getTime() + 5000, csn.getSeqnum(), 6);
    generator.adjust(csn1);

    CSN csn2 = generator.newCSN();
    assertTrue(csn2.compareTo(csn1) > 0,
        "CSNGenerator generated an earlier CSN after calling the adjust method.");
  }

  @Test
  public void adjustSameMilliTest()
  {
    CSNGenerator generator = new CSNGenerator(5, TimeThread.getTime());
    CSN csn = generator.newCSN();

    CSN csn1 = new CSN(csn.getTime(), csn.getSeqnum() + 10, 6);
    generator.adjust(csn1);

    CSN csn2 = generator.newCSN();
    assertTrue(csn2.compareTo(csn1) > 0,
        "CSNGenerator generated an earlier CSN after calling the adjust method.");
  }

  /**
   * An adjust with no CSN - a domain reads back a ServerState which holds no change of its own -
   * leaves the generator where it is: moved back to the clock, it numbered the next change as one it
   * had numbered already, or before it.
   */
  @Test
  public void adjustWithNoCSNDoesNotNumberBeforeTheCSNsGenerated()
  {
    // Ahead of the clock, as a generator is once it numbered changes within the current tick of the
    // clock, or followed the CSNs of a server whose clock runs ahead.
    CSNGenerator generator = new CSNGenerator(5, TimeThread.getTime() + 3600 * 1000);
    CSN csn1 = generator.newCSN();

    generator.adjust((CSN) null);

    CSN csn2 = generator.newCSN();
    assertTrue(csn2.compareTo(csn1) > 0,
        "CSNGenerator generated an earlier CSN after an adjust with no CSN.");
  }

  /**
   * An adjust with no CSN does not take the generator back past the CSNs received from the other
   * servers either: a local change numbered before a change of another server it follows would lose
   * the conflict resolution to it on the other servers.
   */
  @Test
  public void adjustWithNoCSNDoesNotNumberBeforeTheCSNsReceived()
  {
    CSNGenerator generator = new CSNGenerator(5, TimeThread.getTime());
    // From a server whose clock runs an hour ahead.
    CSN received = new CSN(TimeThread.getTime() + 3600 * 1000, 0, 6);
    generator.adjust(received);

    generator.adjust((CSN) null);

    CSN csn = generator.newCSN();
    assertTrue(csn.compareTo(received) > 0,
        "CSNGenerator generated a CSN earlier than one it received after an adjust with no CSN.");
  }

  /**
   * Test the correct behavior of the CSNGenerator when
   * the seqnum is rolling over its limit.
   */
  @Test
  public void adjustRollingSeqnum()
  {
    ServerState state = new ServerState();
    CSN csn1 = new CSN(TimeThread.getTime(), Integer.MAX_VALUE, 5);
    state.update(csn1);

    CSNGenerator generator = new CSNGenerator(5, state);
    CSN csn2 = generator.newCSN();

    assertEquals(csn2.getSeqnum(), 0);
    assertTrue(csn2.getTime() > csn1.getTime());
  }

  @Test
  public void newCSN()
  {
    CSNGenerator generator = new CSNGenerator(5, TimeThread.getTime());
    CSN[] csns = new CSN[] {
      generator.newCSN(),
      generator.newCSN(),
      generator.newCSN(),
      generator.newCSN(),
      generator.newCSN()
    };
    assertNotNull(csns);
    for (int i = 0; i + 1 < csns.length; i++)
    {
      assertEquals(csns[i + 1].getTime(), csns[i].getTime());
      assertEquals(csns[i + 1].getSeqnum(), csns[i].getSeqnum() + 1);
      assertEquals(csns[i + 1].getServerId(), csns[i].getServerId());
      assertTrue(csns[i + 1].compareTo(csns[0]) > 0);
    }
  }
}
