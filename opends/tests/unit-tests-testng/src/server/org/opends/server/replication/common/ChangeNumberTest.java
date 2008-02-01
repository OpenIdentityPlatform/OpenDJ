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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.common;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.util.TimeThread;


/**
 * Test ChangeNumber and ChangeNumberGenerator
 */
public class ChangeNumberTest extends ReplicationTestCase
{

  /**
   * Create ChangeNumber Data
   */
  @DataProvider(name = "changeNumberData")
  public Object[][] createConstructorData() {
    return new Object[][] {
       {1, (short) 0, (short) 1},
       {TimeThread.getTime(), (short) 123, (short) 45}
    };
  }

  /**
   * Test ChangeNumber constructor
   */
  @Test(dataProvider = "changeNumberData")
  public void CreateChangeNumber(long time, int seq, short id)
         throws Exception
  {
    // Create 2 ChangeNumber with the same data and check equality
    new ChangeNumber(time,seq,id);
    new ChangeNumber(time,seq,id);
    new ChangeNumber(time+1,seq,id) ;
    new ChangeNumber(time,seq+1,id) ;
    new ChangeNumber(time,seq,(short)(id+1));
    assertTrue(true);
  }

  /**
   * Test toString and constructor from String
   */
 @Test(dataProvider = "changeNumberData")
 public void ChangeNumberEncodeDecode(long time, int seq, short id)
        throws Exception
 {
   // Create 2 ChangeNumber with the same data and check equality
   ChangeNumber cn = new ChangeNumber(time,seq,id);
   ChangeNumber cn2 = new ChangeNumber(cn.toString());

   assertEquals(cn, cn2,
       "The encoding/decoding of ChangeNumber is not reversible");
 }

  /**
   * Create ChangeNumber
   */
  @DataProvider(name = "createChangeNumber")
  public Object[][] createChangeNumberData() {

    long time[] = {1, TimeThread.getTime()} ;
    short seq[] = {(short)0,  (short) 123} ;
    short id [] = {(short)1,  (short) 45} ;

    Object[][] obj = new Object[time.length][5];
    for (int i=0; i<time.length; i++)
    {
      obj[i][0] = new ChangeNumber(time[i],seq[i],id[i]);
      obj[i][1] = new ChangeNumber(time[i],seq[i],id[i]);
      obj[i][2] = new ChangeNumber(time[i]+1,seq[i],id[i]);
      obj[i][3] = new ChangeNumber(time[i],seq[i]+1,id[i]);
      obj[i][4] = new ChangeNumber(time[i],seq[i],(short)(id[i]+1));
    }
    return obj;
  }

  /**
   * Test ChangeNumber hashCode method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumberhashCode(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {

    // Check hashCode
    assertTrue (cn1.hashCode() == cn2.hashCode());
  }
  /**
   * Test ChangeNumber equals method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumberEquals(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {

    // Check equals
    assertFalse(cn1.equals(new Object()));
    assertTrue (cn1.equals(cn1));
    assertTrue (cn1.equals(cn2));
    assertFalse(cn1.equals(cn3));
    assertFalse(cn1.equals(cn4));
    assertFalse(cn1.equals(cn5));
  }

  /**
   * Test ChangeNumbergetTimeSec method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumbergetTimeSec(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {
    // Check time in sec
    assertEquals(cn1.getTime()/1000, cn1.getTimeSec()) ;
    assertEquals(cn2.getTime()/1000, cn2.getTimeSec()) ;
    assertEquals(cn3.getTime()/1000, cn3.getTimeSec()) ;
    assertEquals(cn4.getTime()/1000, cn4.getTimeSec()) ;
    assertEquals(cn5.getTime()/1000, cn5.getTimeSec()) ;
  }

  /**
   * Test ChangeNumber compare method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumbercompare(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {
    // Check compare
    assertTrue((ChangeNumber.compare(null, null) == 0));
    assertTrue((ChangeNumber.compare(null, cn2) < 0));
    assertTrue((ChangeNumber.compare(cn1, null) > 0));
    assertTrue((ChangeNumber.compare(cn1, cn2) == 0));
    assertTrue((ChangeNumber.compare(cn1, cn3) < 0));
    assertTrue((ChangeNumber.compare(cn3, cn1) > 0));
    assertTrue((ChangeNumber.compare(cn1, cn4) < 0));
    assertTrue((ChangeNumber.compare(cn4, cn1) > 0));
    assertTrue((ChangeNumber.compare(cn1, cn5) < 0));
    assertTrue((ChangeNumber.compare(cn5, cn1) > 0));
  }

  /**
   * Test ChangeNumber older method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumberolder(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {
    // Check older
    assertFalse(cn1.older(null));
    assertFalse(cn1.older(cn1));
    assertTrue(cn1.older(cn3));
    assertTrue(cn1.older(cn4));
    assertTrue(cn1.older(cn5));
  }

  /**
   * Test ChangeNumber olderOrEqual method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumberolderOrEqual(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {
    // Check olderOrEqual
    assertFalse(cn1.olderOrEqual(null));
    assertTrue(cn1.olderOrEqual(cn1));
    assertTrue(cn1.olderOrEqual(cn3));
    assertTrue(cn1.olderOrEqual(cn4));
    assertTrue(cn1.olderOrEqual(cn5));
  }

  /**
   * Test ChangeNumber newer method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumbernewer(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {

    // Check newer
    assertTrue(cn1.newer(null));
    assertFalse(cn1.newer(cn1));
    assertFalse(cn1.newer(cn3));
    assertFalse(cn1.newer(cn4));
    assertFalse(cn1.newer(cn5));
  }

  /**
   * Test ChangeNumber newerOrEquals method
   */
  @Test(dataProvider = "createChangeNumber")
  public void ChangeNumbernewerOrEquals(
      ChangeNumber cn1, ChangeNumber cn2, ChangeNumber cn3, ChangeNumber cn4,
      ChangeNumber cn5) throws Exception
  {

    // Check newerOrEquals
    assertTrue(cn1.newerOrEquals(null));
    assertTrue(cn1.newerOrEquals(cn1));
    assertFalse(cn1.newerOrEquals(cn3));
    assertFalse(cn1.newerOrEquals(cn4));
    assertFalse(cn1.newerOrEquals(cn5));
  }


  /**
   * Create a ChangeNumberGenerator The call NewChangeNumber() and adjust()
   */
  @Test
  public void changeNumberGenerator()
         throws Exception
  {
    ChangeNumber CN1;
    ChangeNumber CN2;

    // Generated the ChangeNumberGenerator object
    ChangeNumberGenerator cng =
      new ChangeNumberGenerator((short) 0, TimeThread.getTime());

    // Generate 2 changeNumbers and check that they are differents
    CN1 = cng.newChangeNumber();
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0);

    // Generate a changeNumber separates by 10 milliseconds
    // and check that they are differents
    Thread.sleep(10);
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0);

    // Generate a changeNumber separates by 300 milliseconds
    // and check that they are differents
    Thread.sleep(300);
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0);

    // Adjust with the oldest CN
    cng.adjust(CN1) ;
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0 );

    // Adjust with the newest generated
    cng.adjust(CN2) ;
    CN1 = CN2;
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0 );

    //  Adjust with the newest generated (time + 300)
    CN1 = new ChangeNumber(CN2.getTime() +300 ,CN2.getSeqnum(),
        CN2.getServerId()) ;
    cng.adjust(CN1) ;
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0 );

    //  Adjust with the newest generated (seqmun + 10)
    CN1 = new ChangeNumber(CN2.getTime() ,CN2.getSeqnum() +10,
        CN2.getServerId()) ;
    cng.adjust(CN1) ;
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0 );

    //  Adjust with the newest generated (seqmun = 0XFFFF)
    CN1 = new ChangeNumber(CN2.getTime() ,0XFFFF +10,CN2.getServerId()) ;
    cng.adjust(CN1) ;
    CN2 = cng.newChangeNumber();
    assertTrue(CN1.compareTo(CN2) != 0 );
  }

  /**
   * Test the difference in seq num between 2 change numbers.
   */
  @Test
  public void changeNumberDiffSeqNum()
         throws Exception
  {
    ChangeNumber CN1;
    ChangeNumber CN2;

    CN1 = new ChangeNumber((long)0, 3, (short)0);

    // 3-0 = 3
    CN2 = new ChangeNumber((long)0, 0, (short)0);
    assertEquals(ChangeNumber.diffSeqNum(CN1, CN2), 3);
    
    // 3-1 = 2
    CN2 = new ChangeNumber((long)0, 1, (short)0);
    assertEquals(ChangeNumber.diffSeqNum(CN1, CN2), 2);
    
    // 3-3 = 0
    CN2 = new ChangeNumber((long)0, 3, (short)0);
    assertEquals(ChangeNumber.diffSeqNum(CN1, CN2), 0);

    // 3-4 == MAXINT (modulo)
    CN2 = new ChangeNumber((long)0, 4, (short)0);
    assertEquals(ChangeNumber.diffSeqNum(CN1, CN2), Integer.MAX_VALUE);

    CN1 = new ChangeNumber((long)0, 0, (short)0);

    // 0-0 = 0
    CN2 = new ChangeNumber((long)0, 0, (short)0);
    assertEquals(ChangeNumber.diffSeqNum(CN1, CN2), 0);
    
    // 0-1 = MAXINT(modulo)
    CN2 = new ChangeNumber((long)0, 1, (short)0);
    assertEquals(ChangeNumber.diffSeqNum(CN1, CN2), Integer.MAX_VALUE);
  }
}
