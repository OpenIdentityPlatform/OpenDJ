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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test of AttrValueHistorical. */
@SuppressWarnings("javadoc")
public class AttrValueHistoricalTest extends ReplicationTestCase
{
  /** Build some data for the ValueInfo test below. */
  @DataProvider(name = "valueInfo")
  public Object[][] createData() {
    ByteString att1 = ByteString.valueOfUtf8("string");
    ByteString att2 = ByteString.valueOfUtf8("value");
    ByteString att3 = ByteString.valueOfUtf8("again");

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

  /** Create a ValueInfo and check the methods. */
  @Test(dataProvider = "valueInfo")
  public void valueInfo(ByteString value, CSN csnUpdate, CSN csnDelete) throws Exception
  {
    AttributeType attrType = CoreSchema.getDescriptionAttributeType();
    AttrValueHistorical valInfo1 = new AttrValueHistorical(value, attrType, csnUpdate, csnDelete);
    AttrValueHistorical valInfo2 = new AttrValueHistorical(value, attrType, csnUpdate, csnUpdate);
    AttrValueHistorical valInfo3 = new AttrValueHistorical(ByteString.valueOfUtf8("Test"),
            attrType, csnUpdate, csnUpdate);

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

  @Test
  public void testEqualsHashCode()
  {
    final AttrValueHistorical histVal1 = newAttrValueHistorical("description");
    final AttrValueHistorical histVal2 = newAttrValueHistorical("Description");
    final AttrValueHistorical histVal3 = newAttrValueHistorical("another description");

    assertThat(histVal1).isEqualTo(histVal1);
    assertThat(histVal1).isEqualTo(histVal2);
    assertThat(histVal2).isEqualTo(histVal1);

    assertThat(histVal1).isNotEqualTo(histVal3);

    assertThat(histVal1.hashCode()).isEqualTo(histVal2.hashCode());
    assertThat(histVal1.hashCode()).isNotEqualTo(histVal3.hashCode());
  }

  private AttrValueHistorical newAttrValueHistorical(String pp)
  {
    AttributeType attrType = CoreSchema.getDescriptionAttributeType();
    return new AttrValueHistorical(ByteString.valueOfUtf8(pp), attrType, null, null);
  }
}
