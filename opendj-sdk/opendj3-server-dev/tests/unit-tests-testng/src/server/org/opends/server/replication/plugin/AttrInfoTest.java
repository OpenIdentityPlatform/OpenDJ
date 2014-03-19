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
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Collections;
import java.util.Map;

import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Attributes;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test AttrInfo and AttrInfoWithOptions
 */
@SuppressWarnings("javadoc")
public class AttrInfoTest extends ReplicationTestCase
{

  /**
   * Build some data for the AttrInfo test below.
   */
  @DataProvider(name = "attrInfo")
  public Object[][] createData()
  {
    ByteString att1 = ByteString.valueOf("string");
    ByteString att2 = ByteString.valueOf("value");
    ByteString att3 = ByteString.valueOf("again");

    CSN del1 = new CSN(1,  0,  1);
    CSN del2 = new CSN(1,  1,  1);
    CSN del3 = new CSN(1,  0,  2);

    CSN upd1 = new CSN(TimeThread.getTime(), 123, 45);
    CSN upd2 = new CSN(TimeThread.getTime() + 1000, 123,  45);
    CSN upd3 = new CSN(TimeThread.getTime(), 321, 54);

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
  public void attrInfo(ByteString att, CSN deleteTime, CSN updateTime) throws Exception
  {
    // Create an empty AttrInfo
    AttrHistoricalMultiple attrInfo1 = new AttrHistoricalMultiple();

    // Check
    attrInfo1.add(att, updateTime);
    Map<AttrValueHistorical,AttrValueHistorical> values1 = attrInfo1.getValuesHistorical();
    assertEquals(values1.size(), 1);
    AttrValueHistorical valueInfo1 = new AttrValueHistorical(att, updateTime, null);
    assertTrue(values1.containsKey(valueInfo1));

    // Check constructor with parameter
    AttrValueHistorical valueInfo2 = new AttrValueHistorical(att, updateTime, deleteTime);
    AttrHistoricalMultiple attrInfo2 = new AttrHistoricalMultiple(
        deleteTime, updateTime, Collections.singletonMap(valueInfo2, valueInfo2));

    // Check equality
    //assertTrue(attrInfo1.getDeleteTime().compareTo(attrInfo2.getDeleteTime())==0);

    //  Check constructor with time parameter and not Value
    AttrHistoricalMultiple attrInfo3 = new AttrHistoricalMultiple(deleteTime, updateTime, null);
    attrInfo3.add(att, updateTime);
    Map<AttrValueHistorical,AttrValueHistorical> values3 = attrInfo3.getValuesHistorical();
    assertEquals(values3.size(), 1);
    valueInfo1 = new AttrValueHistorical(att, updateTime, null);
    assertTrue(values3.containsKey(valueInfo1));

    // Check duplicate
    AttrHistoricalMultiple attrInfo4 = attrInfo3.duplicate();
    Map<AttrValueHistorical,AttrValueHistorical> values4 = attrInfo4.getValuesHistorical();
    assertEquals(attrInfo4.getDeleteTime().compareTo(attrInfo3.getDeleteTime()), 0);
    assertEquals(values4.size(), values3.size());

    // Check
    attrInfo4.delete(att, updateTime);
    assertEquals(attrInfo4.getValuesHistorical().size(), 1);

    // Check
    AttributeType type = DirectoryServer.getAttributeType("description");
    attrInfo3.delete(Attributes.create(type, att), updateTime) ;
    assertEquals(attrInfo3.getValuesHistorical().size(), 1);

    // Check
    attrInfo2.delete(updateTime) ;
    assertEquals(attrInfo2.getValuesHistorical().size(), 0);
  }
}
