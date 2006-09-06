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

import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test AttrInfo and AttrInfoWithOptions
 */
public class AttrInfoTest
    extends SynchronizationTestCase
{
  /**
   * Build some data for the AttrInfo test below.
   */
  @DataProvider(name = "attrInfo")
  public Object[][] createData()
  {
    AttributeType type = DirectoryServer.getAttributeType("description");

    AttributeValue att1 = new AttributeValue(type, "string");
    AttributeValue att2 = new AttributeValue(type, "value");
    AttributeValue att3 = new AttributeValue(null, "again");

    ChangeNumber del1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber del2 = new ChangeNumber(1, (short) 1, (short) 1);
    ChangeNumber del3 = new ChangeNumber(1, (short) 0, (short) 2);

    ChangeNumber upd1 = new ChangeNumber(TimeThread.getTime(),
        (short) 123, (short) 45);
    ChangeNumber upd2 = new ChangeNumber(TimeThread.getTime() + 1000,
        (short) 123, (short) 45);
    ChangeNumber upd3 = new ChangeNumber(TimeThread.getTime(),
        (short) 321, (short) 54);

    return new Object[][]
    {
    { att1, del1, upd1 },
    { att2, del2, upd2 },
    { att3, del3, upd3 },
    { att3, upd3, upd3 } };
  }

  /**
   * Create a AttrInfo and check the methods
   */
  @Test(dataProvider = "attrInfo")
  public void attrInfo(
      AttributeValue att, ChangeNumber deleteTime, ChangeNumber updateTime)
      throws Exception
  {
    // Create an empty AttrInfo
    AttrInfo attrInfo1 = new AttrInfo();

    // Check getLastUpdateTime setLastUpdateTime
    if (attrInfo1.getLastUpdateTime() != null)
    {
      assertTrue(false);
    }
    attrInfo1.setLastUpdateTime(updateTime);
    assertTrue(attrInfo1.getLastUpdateTime().compareTo(updateTime) == 0);

    // Check getDeleteTime setDeleteTime
    if (attrInfo1.getDeleteTime() != null)
    {
      assertTrue(false);
    }
    attrInfo1.setDeleteTime(deleteTime);
    assertTrue(attrInfo1.getDeleteTime().compareTo(deleteTime) == 0);

    // Check add(AttributeValue val, ChangeNumber CN)
    attrInfo1.add(att, updateTime);
    ArrayList<ValueInfo> values1 = attrInfo1.getValuesInfo();
    assertTrue(values1.size() == 1);
    ValueInfo valueInfo1 = new ValueInfo(att, updateTime, null);
    assertTrue(values1.get(0).equals(valueInfo1));

    // Check constructor with parameter
    ValueInfo valueInfo2 = new ValueInfo(att, updateTime, deleteTime);
    ArrayList<ValueInfo> values = new ArrayList<ValueInfo>();
    values.add(valueInfo2);
    AttrInfo attrInfo2 = new AttrInfo(deleteTime, updateTime, values);

    // Check equality
    assertTrue(attrInfo1.getLastUpdateTime().compareTo(
        attrInfo2.getLastUpdateTime()) == 0);
    assertTrue(attrInfo1.getDeleteTime().compareTo(attrInfo2.getDeleteTime())==0);

    //  Check constructor with time parameter and not Value
    AttrInfo attrInfo3 = new AttrInfo(deleteTime, updateTime, null);
    attrInfo3.add(att, updateTime);
    ArrayList<ValueInfo> values3 = attrInfo3.getValuesInfo();
    assertTrue(values3.size() == 1);
    valueInfo1 = new ValueInfo(att, updateTime, null);
    assertTrue(values3.get(0).equals(valueInfo1));

    // Check duplicate
    AttrInfo attrInfo4 = attrInfo3.duplicate();
    ArrayList<ValueInfo> values4 = attrInfo4.getValuesInfo();
    assertTrue(attrInfo4.getDeleteTime().compareTo(attrInfo3.getDeleteTime())==0);
    assertTrue(attrInfo4.getLastUpdateTime().compareTo(
        attrInfo3.getLastUpdateTime()) == 0);
    assertEquals(values4.size(), values3.size());

    // Check delete(AttributeValue val, ChangeNumber CN)
    attrInfo4.delete(att, updateTime);
    assertTrue(attrInfo4.getValuesInfo().size() == 1);

    // Check delete(LinkedHashSet<AttributeValue> values, ChangeNumber CN)
    LinkedHashSet<AttributeValue> attVals = new LinkedHashSet<AttributeValue>();
    attVals.add(att);
    attrInfo3.delete(attVals, updateTime) ;
    assertTrue(attrInfo3.getValuesInfo().size() == 1);

    // Check delete(ChangeNumber CN)
    attrInfo2.delete(updateTime) ;
    assertTrue(attrInfo2.getValuesInfo().size() == 0);

  }
}
