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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.types;

import static org.testng.Assert.*;

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
    assertNull(item.getValue());
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
