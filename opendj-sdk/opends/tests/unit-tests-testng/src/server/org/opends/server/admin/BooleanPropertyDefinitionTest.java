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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * BooleanPropertyDefinition Tester.
 */
public class BooleanPropertyDefinitionTest {

  BooleanPropertyDefinition.Builder builder = null;

  /**
   * Sets up tests.
   * 
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();

    builder = BooleanPropertyDefinition.createBuilder(
        RootCfgDefn.getInstance(), "test-property");
  }

  /**
   * Tests validateValue() with valid data
   */
  @Test
  public void testValidateValue1() {
    BooleanPropertyDefinition d = createPropertyDefinition();
    d.validateValue(Boolean.TRUE);
  }

  /**
   * Tests validateValue() with illegal data
   */
  @Test(expectedExceptions = AssertionError.class)
  public void testValidateValue2() {
    BooleanPropertyDefinition d = createPropertyDefinition();
    d.validateValue(null);
  }

  /**
   * @return data for testing
   */
  @DataProvider(name = "testDecodeValueData")
  public Object[][] createvalidateValueData() {
    return new Object[][]{
            {"false", Boolean.FALSE},
            {"true", Boolean.TRUE}
    };
  }

  /**
   * Tests decodeValue()
   * @param value to decode
   * @param expected value
   */
  @Test(dataProvider = "testDecodeValueData")
  public void testDecodeValue(String value, Boolean expected) {
    BooleanPropertyDefinition d = createPropertyDefinition();
    assertEquals(d.decodeValue(value), expected);
  }

  /**
   * @return data for testing illegal values
   */
  @DataProvider(name = "testDecodeValueData2")
  public Object[][] createvalidateValueData2() {
    return new Object[][]{
            {null},{"abc"}
    };
  }

  /**
   * Tests decodeValue() with illegal data
   * @param value to decode
   */
  @Test(dataProvider = "testDecodeValueData2",
          expectedExceptions = {AssertionError.class,IllegalPropertyValueStringException.class})
  public void testDecodeValue2(String value) {
    BooleanPropertyDefinition d = createPropertyDefinition();
    d.decodeValue(value);
  }

  private BooleanPropertyDefinition createPropertyDefinition() {
    return builder.getInstance();
  }

}
