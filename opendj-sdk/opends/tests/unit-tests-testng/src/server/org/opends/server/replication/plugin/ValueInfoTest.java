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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.ValueInfo;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


/**
 * Test of ValueInfo
 */
public class ValueInfoTest extends ReplicationTestCase
{
  /**
   * Build some data for the ValueInfo test below.
   */
  @DataProvider(name = "valueInfo")
  public Object[][] createData() {
    AttributeType type = DirectoryServer.getAttributeType("description");

    AttributeValue att1 = new AttributeValue(type, "string");
    AttributeValue att2 = new AttributeValue(type, "value");
    AttributeValue att3 = new AttributeValue(type, "again");

    ChangeNumber del1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber del2 = new ChangeNumber(1, (short) 1, (short) 1);
    ChangeNumber del3 = new ChangeNumber(1, (short) 0, (short) 2);

    ChangeNumber upd1 = new ChangeNumber(TimeThread.getTime(),
                                       (short) 123, (short) 45);
    ChangeNumber upd2 = new ChangeNumber(TimeThread.getTime()+ 1000,
        (short) 123, (short) 45);
    ChangeNumber upd3 = new ChangeNumber(TimeThread.getTime(),
        (short) 321, (short) 54);

    return new Object[][] {
        {att1,null,null},
        {att1,upd1,del1},
        {att2,upd2,del2},
        {att3,upd3,del3}};
  }

  /**
   * Create a ValueInfo and check the methods
   */
  @Test(dataProvider = "valueInfo")
  public void valueInfo(AttributeValue value,
      ChangeNumber CNupdate,
      ChangeNumber CNdelete)
         throws Exception
  {
    AttributeType type = DirectoryServer.getAttributeType("description");
    ValueInfo valInfo1 = new ValueInfo(value,CNupdate,CNdelete);
    ValueInfo valInfo2 = new ValueInfo(value,CNupdate,CNupdate);
    ValueInfo valInfo3 = new ValueInfo(new AttributeValue(type,"Test"),
        CNupdate,CNupdate);

    // Check equals
    assertFalse(valInfo1.equals(new Object())) ;
    assertTrue(valInfo1.equals(valInfo1)) ;
    assertTrue(valInfo1.equals(valInfo2)) ;
    assertFalse(valInfo1.equals(valInfo3)) ;

    // Check hashcode
    assertTrue(valInfo1.hashCode() == valInfo2.hashCode()) ;

    // Check getValueDeleteTime
    if (valInfo1.getValueDeleteTime() != null)
    {
      assertTrue(valInfo1.getValueDeleteTime().compareTo(CNdelete) == 0);
    }

    // Check getValueUpdateTime
    if (valInfo1.getValueUpdateTime() != null)
    {
      assertTrue(valInfo1.getValueUpdateTime().compareTo(CNupdate) == 0);
    }

    // Check getValue
    assertTrue(valInfo1.getValue().equals(value)) ;

    // Chek valueUpdateTime
    if (CNupdate == null)
    {
      assertFalse(valInfo1.isUpdate());
    }
    else
    {
      assertTrue(valInfo1.isUpdate());
    }
  }
}
