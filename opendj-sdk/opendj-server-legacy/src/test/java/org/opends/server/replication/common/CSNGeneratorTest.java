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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
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
