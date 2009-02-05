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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;

import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.AttrInfoMultiple;
import org.opends.server.replication.plugin.ValueInfo;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.AttributeValues;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test AttrInfo and AttrInfoWithOptions
 */
public class AttrInfoTest
    extends ReplicationTestCase
{
  /**
   * Build some data for the AttrInfo test below.
   */
  @DataProvider(name = "attrInfo")
  public Object[][] createData()
  {
    AttributeType type = DirectoryServer.getAttributeType("description");

    AttributeValue att1 = AttributeValues.create(type, "string");
    AttributeValue att2 = AttributeValues.create(type, "value");
    AttributeValue att3 = AttributeValues.create(type, "again");

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
    AttrInfoMultiple attrInfo1 = new AttrInfoMultiple();

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
    AttrInfoMultiple attrInfo2 = new AttrInfoMultiple(deleteTime, updateTime, values);

    // Check equality
    //assertTrue(attrInfo1.getDeleteTime().compareTo(attrInfo2.getDeleteTime())==0);

    //  Check constructor with time parameter and not Value
    AttrInfoMultiple attrInfo3 = new AttrInfoMultiple(deleteTime, updateTime, null);
    attrInfo3.add(att, updateTime);
    ArrayList<ValueInfo> values3 = attrInfo3.getValuesInfo();
    assertTrue(values3.size() == 1);
    valueInfo1 = new ValueInfo(att, updateTime, null);
    assertTrue(values3.get(0).equals(valueInfo1));

    // Check duplicate
    AttrInfoMultiple attrInfo4 = attrInfo3.duplicate();
    ArrayList<ValueInfo> values4 = attrInfo4.getValuesInfo();
    assertTrue(attrInfo4.getDeleteTime().compareTo(attrInfo3.getDeleteTime())==0);
    assertEquals(values4.size(), values3.size());

    // Check delete(AttributeValue val, ChangeNumber CN)
    attrInfo4.delete(att, updateTime);
    assertTrue(attrInfo4.getValuesInfo().size() == 1);

    // Check delete(LinkedHashSet<AttributeValue> values, ChangeNumber CN)
    AttributeType type = DirectoryServer.getAttributeType("description");
    attrInfo3.delete(Attributes.create(type, att), updateTime) ;
    assertTrue(attrInfo3.getValuesInfo().size() == 1);

    // Check delete(ChangeNumber CN)
    attrInfo2.delete(updateTime) ;
    assertTrue(attrInfo2.getValuesInfo().size() == 0);

  }
}
