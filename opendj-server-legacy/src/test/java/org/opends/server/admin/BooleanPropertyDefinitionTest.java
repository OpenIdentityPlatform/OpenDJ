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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */

package org.opends.server.admin;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * BooleanPropertyDefinition Tester.
 */
public class BooleanPropertyDefinitionTest extends DirectoryServerTestCase {

  BooleanPropertyDefinition.Builder builder;

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
   * Tests validateValue() with valid data.
   */
  @Test
  public void testValidateValue1() {
    BooleanPropertyDefinition d = createPropertyDefinition();
    d.validateValue(Boolean.TRUE);
  }

  /**
   * Tests validateValue() with illegal data.
   */
  @Test(expectedExceptions = NullPointerException.class)
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
   * Tests decodeValue().
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
   * Tests decodeValue() with illegal data.
   * @param value to decode
   */
  @Test(dataProvider = "testDecodeValueData2",
          expectedExceptions = {NullPointerException.class,PropertyException.class})
  public void testDecodeValue2(String value) {
    BooleanPropertyDefinition d = createPropertyDefinition();
    d.decodeValue(value);
  }

  private BooleanPropertyDefinition createPropertyDefinition() {
    return builder.getInstance();
  }

}
