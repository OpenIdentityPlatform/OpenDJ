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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test of ValueInfo.
 */
@SuppressWarnings("javadoc")
public class ValueInfoTest extends ReplicationTestCase
{

  /**
   * Build some data for the ValueInfo test below.
   */
  @DataProvider(name = "valueInfo")
  public Object[][] createData() {
    ByteString att1 = ByteString.valueOf("string");
    ByteString att2 = ByteString.valueOf("value");
    ByteString att3 = ByteString.valueOf("again");

    CSN del1 = new CSN(1,  0,  1);
    CSN del2 = new CSN(1,  1,  1);
    CSN del3 = new CSN(1,  0,  2);

    CSN upd1 = new CSN(TimeThread.getTime(), 123, 45);
    CSN upd2 = new CSN(TimeThread.getTime()+ 1000, 123, 45);
    CSN upd3 = new CSN(TimeThread.getTime(), 321, 54);

    return new Object[][] {
        {att1,null,null},
        {att1,upd1,del1},
        {att2,upd2,del2},
        {att3,upd3,del3}};
  }

  /**
   * Create a ValueInfo and check the methods.
   */
  @Test(dataProvider = "valueInfo")
  public void valueInfo(ByteString value, CSN csnUpdate, CSN csnDelete) throws Exception
  {
    AttrValueHistorical valInfo1 = new AttrValueHistorical(value, csnUpdate, csnDelete);
    AttrValueHistorical valInfo2 = new AttrValueHistorical(value, csnUpdate, csnUpdate);
    AttrValueHistorical valInfo3 = new AttrValueHistorical(ByteString.valueOf("Test"),
            csnUpdate, csnUpdate);

    // Check equals
    assertFalse(valInfo1.equals(new Object()));
    assertEquals(valInfo1, valInfo1);
    assertEquals(valInfo1, valInfo2);
    assertFalse(valInfo1.equals(valInfo3));

    assertEquals(valInfo1.hashCode(), valInfo2.hashCode());

    if (valInfo1.getValueDeleteTime() != null)
    {
      assertEquals(valInfo1.getValueDeleteTime(), csnDelete);
    }
    if (valInfo1.getValueUpdateTime() != null)
    {
      assertEquals(valInfo1.getValueUpdateTime(), csnUpdate);
    }

    assertEquals(valInfo1.getAttributeValue(), value);
    assertEquals(valInfo1.isUpdate(), csnUpdate != null);
  }
}
