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
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * DurationPropertyDefinition Tester.
 */
public class DurationPropertyDefinitionTest extends DirectoryServerTestCase {

  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
  }

  /**
   * Tests creation of builder succeeds
   */
  @Test
  public void testCreateBuilder() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    assertNotNull(builder);
  }

  /**
   * Tests setting/getting of lower limit as long
   */
  @Test
  public void testLowerLimit1() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit((long) 1);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.getLowerLimit(), 1);
  }

  /**
   * Creates data for testing string-based limit values
   * @return data
   */
  @DataProvider(name = "longLimitData")
  public Object[][] createLongLimitData() {
    return new Object[][]{
            {1L, 1L},
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
            {-1L, 0L, true}, // lower, upper, lower first
            {0L, -1L, false},
            {2L, 1L, true},
            {2L, 1L, false}
    };
  }


  /**
   * Tests setting/getting of lower limit as String
   * @param limit unit limit
   * @param expectedValue to compare
   */
  @Test(dataProvider = "longLimitData")
  public void testLowerLimit2(long limit, long expectedValue) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(limit);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.getLowerLimit(), expectedValue);
  }

  /**
   * Creates data for testing string-based limit values
   *
   * @return data
   */
  @DataProvider(name = "stringLimitData")
  public Object[][] createStringLimitData() {
    return new Object[][] {
        { "ms", "123", 123 },
        { "ms", "123s", 123000 },
        { "s", "123", 123000 },
        { "s", "123s", 123000 },
        { "m", "10", 600000 },
        { "m", "10s", 10000 }
    };
  }

  /**
   * Tests setting/getting of lower limit as String.
   *
   * @param unit
   *          The unit.
   * @param value
   *          The limit value.
   * @param expected
   *          The expected limit in ms.
   */
  @Test(dataProvider = "stringLimitData")
  public void testLowerLimit3(String unit, String value, long expected) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setBaseUnit(DurationUnit.getUnit(unit));
    builder.setLowerLimit(value);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.getLowerLimit(), expected);
  }

  /**
   * Tests setting/getting of lower limit as long
   */
  @Test
  public void testUpperLimit1() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit((long) 1);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.getLowerLimit(), 1);
  }

  /**
   * Tests setting/getting of lower limit as String
   * @param limit upper limit
   * @param expectedValue to compare
   */
  @Test(dataProvider = "longLimitData")
  public void testUpperLimit2(long limit, long expectedValue) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setUpperLimit(limit);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals((long) spd.getUpperLimit(), expectedValue);
  }

  /**
   * Tests setting/getting of lower limit as String
   * @param upper upper limit
   * @param lower lower limit
   * @param lowerFirst when true sets the lower limit property first
   */
  @Test(dataProvider = "illegalLimitData", expectedExceptions = IllegalArgumentException.class)
  public void testIllegalLimits(long lower, long upper, boolean lowerFirst) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
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
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue("unlimited");
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test(expectedExceptions = IllegalPropertyValueStringException.class)
  public void testIsAllowUnlimited2() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue("unlimited");
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test(expectedExceptions = IllegalPropertyValueException.class)
  public void testIsAllowUnlimited3() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(-1L);
  }

  /**
   * Creates illegal data for validate value
   * @return data
   */
  @DataProvider(name = "validateValueData")
  public Object[][] createValidateValueData() {
    return new Object[][]{
            {5000L, 10000L, false, 7L},
            {5000L, null, true, -1L},
            {5000L, 10000L, false, 5L},
            {5000L, 10000L, false, 10L},
            {5000L, null, false, 10000L}
    };
  }

  /**
   * Tests that validateValue works
   * @param allowUnlimited when true allows unlimited
   * @param high upper limit
   * @param low lower limit
   * @param value to validate
   */
  @Test(dataProvider = "validateValueData")
  public void testValidateValue1(Long low, Long high, boolean allowUnlimited, Long value) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(low);
    builder.setUpperLimit(high);
    builder.setAllowUnlimited(allowUnlimited);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(value);
  }

  /**
   * Creates illegal data for validate value
   * @return data
   */
  @DataProvider(name = "illegalValidateValueData")
  public Object[][] createIllegalValidateValueData() {
    return new Object[][]{
            {5000L, 10000L, false, null},
            {5000L, 10000L, false, 1L},
            {5000L, 10000L, false, 11L},
            {5000L, 10000L, false, -1L}
    };
  }

  /**
   * Tests that validateValue throws exceptions
   * @param low lower limit
   * @param high upper limit
   * @param allowUnlimited when true allows unlimited
   * @param value to validate
   */
  @Test(dataProvider = "illegalValidateValueData",
          expectedExceptions = {AssertionError.class,NullPointerException.class,IllegalPropertyValueException.class})
  public void testValidateValue2(Long low, Long high, boolean allowUnlimited, Long value) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(low);
    builder.setUpperLimit(high);
    builder.setAllowUnlimited(allowUnlimited);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(value);
  }

  /**
   * Creates encode test data
   * @return data
   */
  @DataProvider(name = "encodeValueData")
  public Object[][] createEncodeValueData() {
    return new Object[][]{
            {-1L, "unlimited"},
            {0L, "0 s"},
            {1L, "1 s"},
            {2L, "2 s"},
            {999L, "999 s"},
            {1000L, "1000 s"},
            {1001L, "1001 s"},
            {1023L, "1023 s"},
            {1024L, "1024 s"},
            {1025L, "1025 s"},
            {1000L * 1000L, "1000000 s"},
    };
  }

  /**
   * Tests encode value
   * @param value to encode
   * @param expectedValue to compare
   */
  @Test(dataProvider = "encodeValueData")
  public void testEncodeValue(Long value, String expectedValue) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.encodeValue(value), expectedValue);
  }

  /**
   * Test that accept doesn't throw and exception
   */
  @Test
  public void testAccept() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    DurationPropertyDefinition spd = buildTestDefinition(builder);

    PropertyDefinitionVisitor<Boolean, Void> v = new PropertyDefinitionVisitor<Boolean, Void>() {

      public Boolean visitDuration(DurationPropertyDefinition d,
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
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.toString();
  }

  /**
   * Make sure toString doesn't barf
   */
  @Test
  public void testToString2() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setUpperLimit(10L);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.toString();
  }

  /**
   * Test value comparisons.
   */
  @Test
  public void testCompare() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.compare(1L, 2L);
  }

  /**
   * Test setting a default behavior provider.
   */
  @Test
  public void testSetDefaultBehaviorProvider() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    builder.setDefaultBehaviorProvider(new DefaultBehaviorProvider<Long>() {
      public <R, P> R accept(DefaultBehaviorProviderVisitor<Long, R, P> v, P p) {
        return null;
      }
    });
  }

  /**
   * Test setting a property option.
   */
  @Test
  public void testSetOption() {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setOption(PropertyOption.HIDDEN);
  }

  /**
   * Creates encode test data
   * @return data
   */
  @DataProvider(name = "decodeValueData")
  public Object[][] createDecodeValueData() {
    return new Object[][]{
            // syntax tests
            {"unlimited", -1L},
            {"0h", 0L},
            {"0.0h", 0L},
            {"0.00h", 0L},
            {"0 h", 0L},
            {"0.00 h", 0L},
            {"1h", 1L},
            {"1 h", 1L},
            { "0ms", 0L },
            { "1h60m", 2L },
            { "1d10h", 34L },
            { "4d600m", 106L },

            // conversion tests
            {"1 d", 24L},
            {"2 d", 48L},
            {"0.5 d", 12L}
    };
  }

  /**
   * Tests decodeValue()
   * @param value to decode
   * @param expectedValue for comparison
   */
  @Test(dataProvider = "decodeValueData")
  public void testDecodeValue(String value, Long expectedValue) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    builder.setBaseUnit(DurationUnit.HOURS);
    builder.setMaximumUnit(DurationUnit.DAYS);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
//    if (spd.decodeValue(value) != expectedValue) {
//      System.out.println(spd.decodeValue(value) + "!=" + expectedValue);
//    }
    assertEquals(spd.decodeValue(value), expectedValue);
  }

  /**
   * Creates encode test data
   * @return data
   */
  @DataProvider(name = "decodeValueData2")
  public Object[][] createDecodeValueData2() {
    return new Object[][]{
            {""},
            {"0"}, // no unit
            {"123"}, // no unit
            {"a s"},
            {"1 x"},
            {"0.h"},
            {"0. h"},
            {"1.h"},
            {"1. h"},
            {"1.1 h"}, // too granular
            {"30 m"}, // unit too small violation
            {"60 m"}, // unit too small violation
            {"1 w"},  // unit too big violation
            {"7 w"},  // unit too big violation
            {"1 x"},
            {"1 d"}, // upper limit violation
            {"2 h"}, // lower limit violation
            {"-1 h"} // unlimited violation
    };
  }

  /**
   * Tests decodeValue()
   * @param value to decode
   */
  @Test(dataProvider = "decodeValueData2",
          expectedExceptions = {IllegalPropertyValueStringException.class})
  public void testDecodeValue(String value) {
    DurationPropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    builder.setBaseUnit(DurationUnit.HOURS);
    builder.setMaximumUnit(DurationUnit.DAYS);
    builder.setLowerLimit(5L);
    builder.setUpperLimit(10L);
    DurationPropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue(value);
  }

  private DurationPropertyDefinition.Builder createTestBuilder() {
    return DurationPropertyDefinition.createBuilder(
        RootCfgDefn.getInstance(), "test-property-name");
  }

  private DurationPropertyDefinition buildTestDefinition(DurationPropertyDefinition.Builder builder) {
    builder.setDefaultBehaviorProvider(new DefinedDefaultBehaviorProvider<Long>("0"));
    return builder.getInstance();
  }

}
