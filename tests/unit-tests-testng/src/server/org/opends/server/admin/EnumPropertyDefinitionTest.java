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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin;

import static org.testng.Assert.*;

import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * EnumPropertyDefinition Tester.
 */
public class EnumPropertyDefinitionTest extends DirectoryServerTestCase {

  private enum TestEnum { ONE, TWO, THREE }

  private EnumPropertyDefinition.Builder<TestEnum> builder = null;

  @BeforeClass
  public void setUp() {
    builder = EnumPropertyDefinition.createBuilder(
        RootCfgDefn.getInstance(), "test-property");
    builder.setEnumClass(TestEnum.class);
  }

  @Test
  public void testCreateBuilder() {
    assertNotNull(builder);
  }

  /**
   * Tests that exception thrown when no enum class
   * specified by builder
   */
  @Test
  public void testBuildInstance() {
    EnumPropertyDefinition epd = builder.getInstance();
    assertEquals(epd.getEnumClass(), TestEnum.class);
  }

  /**
   * Tests that exception thrown when no enum class
   * specified by builder
   */
  @Test(expectedExceptions = {IllegalStateException.class})
  public void testBuildInstance2() {
    EnumPropertyDefinition.Builder<TestEnum> localBuilder =
            EnumPropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
    localBuilder.getInstance();
  }

  /**
   * Creates data decodeValue test
   * @return data
   */
  @DataProvider(name = "decodeValueData")
  public Object[][] createDecodeValueData() {
    return new Object[][]{
            { "ONE", TestEnum.ONE }
    };
  }

  /**
   * Tests decodeValue()
   * @param value to decode
   * @param expectedValue enum expected
   */
  @Test(dataProvider = "decodeValueData")
  public void testDecodeValue(String value, TestEnum expectedValue) {
    EnumPropertyDefinition epd = builder.getInstance();
    assertEquals(epd.decodeValue(value), expectedValue);
  }

  /**
   * Creates illegal data for decode value test
   * @return data
   */
  @DataProvider(name = "decodeValueData2")
  public Object[][] createDecodeValueData2() {
    return new Object[][]{
            { "xxx" },
            { null }
    };
  }

  /**
   * Tests decodeValue()
   * @param value to decode
   */
  @Test(dataProvider = "decodeValueData2",
          expectedExceptions = {AssertionError.class,
                  IllegalPropertyValueStringException.class} )
  public void testDecodeValue2(String value) {
    EnumPropertyDefinition epd = builder.getInstance();
    epd.decodeValue(value);
  }

  /**
   * Tests normalization
   */
  @Test
  public void testNormalizeValue() {
    EnumPropertyDefinition<TestEnum> epd = builder.getInstance();
    assertEquals(epd.normalizeValue(TestEnum.ONE), "one");
  }

  /**
   * Tests validation
   */
  @Test
  public void testValidateValue() {
    EnumPropertyDefinition<TestEnum> epd = builder.getInstance();
    epd.validateValue(TestEnum.ONE);
  }

}
