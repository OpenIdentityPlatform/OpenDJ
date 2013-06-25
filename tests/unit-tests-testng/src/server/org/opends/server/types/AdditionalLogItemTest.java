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
 *      Copyright 2011 ForgeRock AS
 */
package org.opends.server.types;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;



/**
 * Tests for {@link AdditionalLogItem}.
 */
public class AdditionalLogItemTest extends TypesTestCase
{

  /**
   * Tests {@link AdditionalLogItem#keyOnly(Class, String)}.
   */
  @Test
  public void testKeyOnly()
  {
    AdditionalLogItem item = AdditionalLogItem.keyOnly(getClass(), "testKey");
    assertEquals(item.getSource(), getClass());
    assertEquals(item.getKey(), "testKey");
    assertEquals(item.getValue(), null);
    assertEquals(item.toString(), "testKey");
    assertEquals(item.toString(new StringBuilder()).toString(), "testKey");
  }



  /**
   * Tests {@link AdditionalLogItem#quotedKeyValue(Class, String, Object)}.
   */
  @Test
  public void testQuotedKeyValue()
  {
    AdditionalLogItem item = AdditionalLogItem.quotedKeyValue(getClass(),
        "testKey", "testValue");
    assertEquals(item.getSource(), getClass());
    assertEquals(item.getKey(), "testKey");
    assertEquals(item.getValue(), "testValue");
    assertTrue(item.isQuoted());
    assertEquals(item.toString(), "testKey='testValue'");
    assertEquals(item.toString(new StringBuilder()).toString(),
        "testKey='testValue'");
  }



  /**
   * Tests {@link AdditionalLogItem#unquotedKeyValue(Class, String, Object)}.
   */
  @Test
  public void testUnquotedKeyValue()
  {
    AdditionalLogItem item = AdditionalLogItem.unquotedKeyValue(getClass(),
        "testKey", "testValue");
    assertEquals(item.getSource(), getClass());
    assertEquals(item.getKey(), "testKey");
    assertEquals(item.getValue(), "testValue");
    assertFalse(item.isQuoted());
    assertEquals(item.toString(), "testKey=testValue");
    assertEquals(item.toString(new StringBuilder()).toString(),
        "testKey=testValue");
  }
}
