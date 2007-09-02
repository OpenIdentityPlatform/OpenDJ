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

import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * IntegerPropertyDefinition Tester.
 */
public class IntegerPropertyDefinitionTest extends DirectoryServerTestCase {

  /**
   * Tests creation of builder succeeds
   */
  @Test
  public void testCreateBuilder() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    assertNotNull(builder);
  }

  /**
   * Tests setting/getting of lower limit as long
   */
  @Test
  public void testLowerLimit1() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(1);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getLowerLimit() == 1;
  }

  /**
   * Creates data for testing string-based limit values
   * @return data
   */
  @DataProvider(name = "limitData")
  public Object[][] createlimitData() {
    return new Object[][]{
            {1, 1},
            // { null, 0 }
    };
  }

  /**
   * Creates data for testing limit values
   * @return data
   */
  @DataProvider(name = "illegalLimitData")
  public Object[][] createIllegalLimitData() {
    return new Object[][]{
            {-1, 0, true}, // lower, upper, lower first
            {0, -1, false},
            {2, 1, true},
            {2, 1, false}
    };
  }


  /**
   * Tests setting/getting of lower limit as String
   * @param limit unit limit
   * @param expectedValue to compare
   */
  @Test(dataProvider = "limitData")
  public void testLowerLimit2(int limit, int expectedValue) {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(limit);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getLowerLimit() == expectedValue;
  }

  /**
   * Tests setting/getting of lower limit as long
   */
  @Test
  public void testUpperLimit1() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(1);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getLowerLimit() == 1;
  }

  /**
   * Tests setting/getting of lower limit as String
   * @param limit upper limit
   * @param expectedValue to compare
   */
  @Test(dataProvider = "limitData")
  public void testUpperLimit2(int limit, int expectedValue) {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setUpperLimit(limit);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getUpperLimit().equals(expectedValue);
  }

  /**
   * Tests setting/getting of lower limit as String
   * @param upper upper limit
   * @param lower lower limit
   * @param lowerFirst when true sets the lower limit property first
   */
  @Test(dataProvider = "illegalLimitData", expectedExceptions = IllegalArgumentException.class)
  public void testIllegalLimits(int lower, int upper, boolean lowerFirst) {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    if (lowerFirst) {
      builder.setLowerLimit(lower);
      builder.setUpperLimit(upper);
    } else {
      builder.setUpperLimit(upper);
      builder.setLowerLimit(lower);
    }
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test
  public void testIsAllowUnlimited1() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue("unlimited");
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test(expectedExceptions = IllegalPropertyValueStringException.class)
  public void testIsAllowUnlimited2() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue("unlimited");
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test(expectedExceptions = IllegalPropertyValueException.class)
  public void testIsAllowUnlimited3() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(-1);
  }

  /**
   * Creates illegal data for validate value
   * @return data
   */
  @DataProvider(name = "validateValueData")
  public Object[][] createvalidateValueData() {
    return new Object[][]{
            {5, 10, false, 7},
            {5, null, true, -1},
            {5, 10, true, -1},
    };
  }

  /**
   * Tests that validateValue works
   * @param value to validate
   * @param allowUnlimited when true allows unlimited
   * @param high upper limit
   * @param low lower limit
   */
  @Test(dataProvider = "validateValueData")
  public void testValidateValue1(Integer low, Integer high, boolean allowUnlimited, Integer value) {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(low);
    builder.setUpperLimit(high);
    builder.setAllowUnlimited(allowUnlimited);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(value);
  }

  /**
   * Creates illegal data for validate value
   * @return data
   */
  @DataProvider(name = "illegalValidateValueData")
  public Object[][] createIllegalValidateValueData() {
    return new Object[][]{
            {5, 10, false, null},
            {5, 10, false, 1},
            {5, 10, false, 11},
            {5, 10, false, -1},
            {5, 10, true, 2},
            {5, 10, true, 11}
    };
  }

  /**
   * Tests that validateValue throws exceptions
   * @param value to validate
   * @param low lower limit
   * @param high upper limit
   * @param allowUnlimited when true allows unlimited
   */
  @Test(dataProvider = "illegalValidateValueData",
          expectedExceptions = {AssertionError.class,NullPointerException.class,IllegalPropertyValueException.class})
  public void testValidateValue2(Integer low, Integer high, boolean allowUnlimited, Integer value) {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(low);
    builder.setUpperLimit(high);
    builder.setAllowUnlimited(allowUnlimited);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(value);
  }

  /**
   * Creates encode test data
   * @return data
   */
  @DataProvider(name = "encodeValueData")
  public Object[][] createEncodeValueData() {
    return new Object[][]{
            {-1, "unlimited"},
            {1, "1"},
    };
  }

  /**
   * Tests encode value
   * @param value to encode
   * @param expectedValue to compare
   */
  @Test(dataProvider = "encodeValueData")
  public void testEncodeValue(Integer value, String expectedValue) {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.encodeValue(value), expectedValue);
  }

  /**
   * Test that accept doesn't throw and exception
   */
  @Test
  public void testAccept() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    PropertyDefinitionVisitor<Boolean, Void> v = new PropertyDefinitionVisitor<Boolean, Void>() {

      public Boolean visitInteger(IntegerPropertyDefinition d,
          Void o) {
        return true;
      }

      public Boolean visitUnknown(PropertyDefinition d, Void o)
          throws UnknownPropertyDefinitionException {
        return false;
      }

    };

    assertEquals((boolean) spd.accept(v, null), true);
  }

  /**
   * Make sure toString doesn't barf
   */
  @Test
  public void testToString() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.toString();
  }

  @Test
  public void testCompare() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    IntegerPropertyDefinition spd = buildTestDefinition(builder);
    spd.compare(1, 2);
  }

  @Test
  public void testSetDefaultBehaviorProvider() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    builder.setDefaultBehaviorProvider(new DefaultBehaviorProvider<Integer>() {
      public <R, P> R accept(DefaultBehaviorProviderVisitor<Integer, R, P> v, P p) {
        return null;
      }
    });
  }

  @Test
  public void testSetOption() {
    IntegerPropertyDefinition.Builder builder = createTestBuilder();
    builder.setOption(PropertyOption.HIDDEN);
  }

  private IntegerPropertyDefinition.Builder createTestBuilder() {
    return IntegerPropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property-name");
  }

  private IntegerPropertyDefinition buildTestDefinition(IntegerPropertyDefinition.Builder builder) {
    builder.setDefaultBehaviorProvider(new DefinedDefaultBehaviorProvider<Integer>("0"));
    return builder.getInstance();
  }

}
