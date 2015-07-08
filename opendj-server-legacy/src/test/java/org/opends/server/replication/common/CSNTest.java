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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test {@link CSN} and {@link CSNGenerator}.
 */
@SuppressWarnings("javadoc")
public class CSNTest extends ReplicationTestCase
{

  /**
   * Create CSN Data.
   */
  @DataProvider(name = "csnData")
  public Object[][] createConstructorData() {
    long time = 0x12ABC;
    return new Object[][] {
       {1, 0, 1, "0000000000000001000100000000"},
       {time, 123, 45, "0000000000012abc002d0000007b"},
       {time, 123456789, 32767, "0000000000012abc7fff075bcd15"},
       {time, 123456789, 32768, "0000000000012abc8000075bcd15"},
       {time, 123456789, 65000, "0000000000012abcfde8075bcd15"},
       {time, 123, 45678, "0000000000012abcb26e0000007b"}
    };
  }

  /** Test CSN constructor. */
  @Test(dataProvider = "csnData")
  public void createCSN(long time, int seq, int id, String str) throws Exception
  {
    CSN csn = new CSN(time, seq, id);
    assertEquals(csn.toString(), str);

    new CSN(time,   seq,   id);
    new CSN(time+1, seq,   id);
    new CSN(time,   seq+1, id);
    new CSN(time,   seq,   id+1);
  }

  /**
   * Test toString and constructor from String.
   */
 @Test(dataProvider = "csnData")
 public void csnEncodeDecode(long time, int seq, int id, String str) throws Exception
 {
   // Create 2 CSN with the same data and check equality
    CSN csn = new CSN(time, seq, id);
    CSN csn2 = new CSN(csn.toString());

   assertEquals(csn, csn2,
       "The encoding/decoding is not reversible");
   assertEquals(csn2.toString(), str,
       "The encoding/decoding of CSN is not reversible for toString()");
 }

  /**
   * Create CSN.
   */
  @DataProvider(name = "createCSN")
  public Object[][] createCSNData()
  {
    long time[] = {1, TimeThread.getTime()};
    int seq[] = {0,  123};
    int id [] = {1,  45};

    Object[][] obj = new Object[time.length][5];
    for (int i=0; i<time.length; i++)
    {
      obj[i][0] = new CSN(time[i],   seq[i],   id[i]);
      obj[i][1] = new CSN(time[i],   seq[i],   id[i]);
      obj[i][2] = new CSN(time[i]+1, seq[i],   id[i]);
      obj[i][3] = new CSN(time[i],   seq[i]+1, id[i]);
      obj[i][4] = new CSN(time[i],   seq[i],   id[i]+1);
    }
    return obj;
  }

  /** Test {@link CSN#hashCode()} method. */
  @Test(dataProvider = "createCSN")
  public void csnHashCode(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertEquals(csn1.hashCode(), csn2.hashCode());
  }

  /** Test {@link CSN#equals(Object)} method. */
  @Test(dataProvider = "createCSN")
  public void csnEquals(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertFalse(csn1.equals(new Object()));
    assertEquals(csn1, csn1);
    assertEquals(csn1, csn2);
    assertFalse(csn1.equals(csn3));
    assertFalse(csn1.equals(csn4));
    assertFalse(csn1.equals(csn5));
  }

  /** Test {@link CSN#getTimeSec()} method. */
  @Test(dataProvider = "createCSN")
  public void csnGetTimeSec(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    // Check time in sec
    assertEquals(csn1.getTime()/1000, csn1.getTimeSec());
    assertEquals(csn2.getTime()/1000, csn2.getTimeSec());
    assertEquals(csn3.getTime()/1000, csn3.getTimeSec());
    assertEquals(csn4.getTime()/1000, csn4.getTimeSec());
    assertEquals(csn5.getTime()/1000, csn5.getTimeSec());
  }

  /** Test {@link CSN#compare(CSN, CSN)} method. */
  @Test(dataProvider = "createCSN")
  public void csnCompare(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertTrue(CSN.compare(null, null) == 0);
    assertTrue(CSN.compare(null, csn2) < 0);
    assertTrue(CSN.compare(csn1, null) > 0);
    assertTrue(CSN.compare(csn1, csn2) == 0);
    assertTrue(CSN.compare(csn1, csn3) < 0);
    assertTrue(CSN.compare(csn3, csn1) > 0);
    assertTrue(CSN.compare(csn1, csn4) < 0);
    assertTrue(CSN.compare(csn4, csn1) > 0);
    assertTrue(CSN.compare(csn1, csn5) < 0);
    assertTrue(CSN.compare(csn5, csn1) > 0);
  }

  /** Test {@link CSN#isOlderThan(CSN)} method. */
  @Test(dataProvider = "createCSN")
  public void csnOlder(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertFalse(csn1.isOlderThan(null));
    assertFalse(csn1.isOlderThan(csn1));
    assertTrue(csn1.isOlderThan(csn3));
    assertTrue(csn1.isOlderThan(csn4));
    assertTrue(csn1.isOlderThan(csn5));
  }

  /** Test {@link CSN#isOlderThanOrEqualTo(CSN)} method. */
  @Test(dataProvider = "createCSN")
  public void csnOlderOrEqual(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertFalse(csn1.isOlderThanOrEqualTo(null));
    assertTrue(csn1.isOlderThanOrEqualTo(csn1));
    assertTrue(csn1.isOlderThanOrEqualTo(csn3));
    assertTrue(csn1.isOlderThanOrEqualTo(csn4));
    assertTrue(csn1.isOlderThanOrEqualTo(csn5));
  }

  /** Test {@link CSN#isNewerThan(CSN)} method. */
  @Test(dataProvider = "createCSN")
  public void csnNewer(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertTrue(csn1.isNewerThan(null));
    assertFalse(csn1.isNewerThan(csn1));
    assertFalse(csn1.isNewerThan(csn3));
    assertFalse(csn1.isNewerThan(csn4));
    assertFalse(csn1.isNewerThan(csn5));
  }

  /** Test {@link CSN#isNewerThanOrEqualTo(CSN)} method. */
  @Test(dataProvider = "createCSN")
  public void csnNewerOrEquals(CSN csn1, CSN csn2, CSN csn3, CSN csn4, CSN csn5) throws Exception
  {
    assertTrue(csn1.isNewerThanOrEqualTo(null));
    assertTrue(csn1.isNewerThanOrEqualTo(csn1));
    assertFalse(csn1.isNewerThanOrEqualTo(csn3));
    assertFalse(csn1.isNewerThanOrEqualTo(csn4));
    assertFalse(csn1.isNewerThanOrEqualTo(csn5));
  }


  /**
   * Create a {@link CSNGenerator}, then call {@link CSNGenerator#newCSN()} and
   * {@link CSNGenerator#adjust()}
   * <p>
   * FIXME these tests are calling Thread.sleep() which makes them slow. We
   * should really have a way to control time (make it go slower or faster) for
   * the unit tests to avoid such waits.
   */
  @Test
  public void csnGenerator() throws Exception
  {
    CSN csn1;
    CSN csn2;

    CSNGenerator csng =
      new CSNGenerator( 0, TimeThread.getTime());

    // Generate 2 CSNs and check that they are different
    csn1 = csng.newCSN();
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0);

    // Generate a CSN separated by 10 milliseconds
		// and check that they are different
    Thread.sleep(10);
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0);

    // Generate a CSN separated by 300 milliseconds
		// and check that they are different
    Thread.sleep(300);
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0);

    // Adjust with the oldest CSN
    csng.adjust(csn1);
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0 );

    // Adjust with the newest generated
    csng.adjust(csn2);
    csn1 = csn2;
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0 );

    //  Adjust with the newest generated (time + 300)
    csn1 = new CSN(csn2.getTime() +300 ,csn2.getSeqnum(), csn2.getServerId());
    csng.adjust(csn1);
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0 );

    //  Adjust with the newest generated (seqmun + 10)
    csn1 = new CSN(csn2.getTime() ,csn2.getSeqnum() +10, csn2.getServerId());
    csng.adjust(csn1);
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0 );

    //  Adjust with the newest generated (seqmun = 0XFFFF)
    csn1 = new CSN(csn2.getTime() ,0XFFFF +10,csn2.getServerId());
    csng.adjust(csn1);
    csn2 = csng.newCSN();
    assertTrue(csn1.compareTo(csn2) != 0 );
  }

  /**
   * Test the difference in seq num between 2 CSNs.
   */
  @Test
  public void csnDiffSeqNum() throws Exception
  {
    CSN csn1;
    CSN csn2;

    csn1 = new CSN(0, 3, 0);

    // 3-0 = 3
    csn2 = new CSN(0, 0, 0);
    assertEquals(CSN.diffSeqNum(csn1, csn2), 3);

    // 3-1 = 2
    csn2 = new CSN(0, 1, 0);
    assertEquals(CSN.diffSeqNum(csn1, csn2), 2);

    // 3-3 = 0
    csn2 = new CSN(0, 3, 0);
    assertEquals(CSN.diffSeqNum(csn1, csn2), 0);

    // 3-4 = 0 (csn1 must be newer otherwise 0 should be returned)
    csn2 = new CSN(0, 4, 0);
    assertEquals(CSN.diffSeqNum(csn1, csn2), 0);

    csn1 = new CSN(0, 0, 0);

    // 0-0 = 0
    csn2 = new CSN(0, 0, 0);
    assertEquals(CSN.diffSeqNum(csn1, csn2), 0);

    // 0-1 = 0 (csn1 must be newer otherwise 0 should be returned)
    csn2 = new CSN(0, 1, 0);
    assertEquals(CSN.diffSeqNum(csn1, csn2), 0);

    csn1 = new CSN(0, 5, 0);
    csn2 = new CSN(0, 2, 0);

    // 5-null = 5
    assertEquals(CSN.diffSeqNum(csn1, null), 5);

    // null-2 = 0
    assertEquals(CSN.diffSeqNum(null, csn2), 0);

    // null-null = 0
    assertEquals(CSN.diffSeqNum(null, null), 0);

    csn1 = new CSN(1111111, 2, 0);
    csn2 = new CSN(3333333, 4, 0);

    // csn1 older than csn2 -> 0
    assertEquals(CSN.diffSeqNum(csn1, csn2), 0);

    csn1 = new CSN(3333333, 1, 0);
    csn2 = new CSN(1111111, Integer.MAX_VALUE-1, 0);

    // csn1 seqnum looped
    assertEquals(CSN.diffSeqNum(csn1, csn2), 3);
  }

  @DataProvider
  public Iterator<Object[]> createCSNPairsToCompare()
  {
    final List<Object> allCSNs = new ArrayList<>();
    for (Object[] csnData : createCSNData())
    {
      allCSNs.addAll(Arrays.asList(csnData));
    }

    final List<Object[]> results = new ArrayList<>();
    for (Object csn1 : allCSNs)
    {
      for (Object csn2 : allCSNs)
      {
        /*
         * it is ok to compare to the exact same CSN to ensure operations are
         * reflexive, and it is also ok to compare csn1 to csn2, and csn2 to
         * csn1 to ensure operations are symmetric
         */
        results.add(new Object[] { csn1, csn2 });
      }
    }
    return results.iterator();
  }

  @Test(dataProvider = "createCSNPairsToCompare")
  public void compareToEquivalentToEquals(CSN csn1, CSN csn2) throws Exception
  {
    assertEquals(csn1.compareTo(csn2) == 0, csn1.equals(csn2));
  }

  @Test(dataProvider = "createCSNPairsToCompare")
  public void hashCodesEqualWhenCSNsEqual(CSN csn1, CSN csn2) throws Exception
  {
    if (csn1.equals(csn2))
    {
      assertEquals(csn1.hashCode(), csn2.hashCode());
    }
  }

  @Test(dataProvider = "createCSNPairsToCompare")
  public void hashCodesEqualWhenCompareToEqual(CSN csn1, CSN csn2) throws Exception
  {
    if (csn1.compareTo(csn2) == 0)
    {
      assertEquals(csn1.hashCode(), csn2.hashCode());
    }
  }

}
