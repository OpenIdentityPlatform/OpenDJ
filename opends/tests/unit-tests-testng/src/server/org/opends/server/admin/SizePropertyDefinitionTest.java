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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.EnumSet;

/**
 * SizePropertyDefinition Tester.
 */
public class SizePropertyDefinitionTest {

  /**
   * Tests creation of builder succeeds
   */
  @Test
  public void testCreateBuilder() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    assertNotNull(builder);
  }

  /**
   * Tests setting/getting of lower limit as long
   */
  @Test
  public void testLowerLimit1() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit((long) 1);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getLowerLimit() == 1;
  }

  /**
   * Creates data for testing string-based limit values
   * @return data
   */
  @DataProvider(name = "stringLimitData")
  public Object[][] createStringLimitData() {
    return new Object[][]{
            {"1 b", 1L},
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
  @Test(dataProvider = "stringLimitData")
  public void testLowerLimit2(String limit, Long expectedValue) {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(limit);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getLowerLimit() == expectedValue;
  }

  /**
   * Tests setting/getting of lower limit as long
   */
  @Test
  public void testUpperLimit1() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit((long) 1);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getLowerLimit() == 1;
  }

  /**
   * Tests setting/getting of lower limit as String
   * @param limit upper limit
   * @param expectedValue to compare
   */
  @Test(dataProvider = "stringLimitData")
  public void testUpperLimit2(String limit, long expectedValue) {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setUpperLimit(limit);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    assert spd.getUpperLimit().equals(expectedValue);
  }

  /**
   * Tests setting/getting of lower limit as String
   * @param upper upper limit
   * @param lower lower limit
   * @param lowerFirst when true sets the lower limit property first
   */
  @Test(dataProvider = "illegalLimitData", expectedExceptions = IllegalArgumentException.class)
  public void testIllegalLimits(long lower, long upper, boolean lowerFirst) {
    SizePropertyDefinition.Builder builder = createTestBuilder();
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
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue("unlimited");
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test(expectedExceptions = IllegalPropertyValueStringException.class)
  public void testIsAllowUnlimited2() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    spd.decodeValue("unlimited");
  }

  /**
   * Tests the allowUnlimited property
   */
  @Test(expectedExceptions = IllegalPropertyValueException.class)
  public void testIsAllowUnlimited3() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(false);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(-1L);
  }

  /**
   * Creates illegal data for validate value
   * @return data
   */
  @DataProvider(name = "validateValueData")
  public Object[][] createvalidateValueData() {
    return new Object[][]{
            {5L, 10L, false, 7L},
            {5L, null, true, -1L},
            {5L, 10L, true, -1L},
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
  public void testValidateValue1(Long low, Long high, boolean allowUnlimited, Long value) {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(low);
    builder.setUpperLimit(high);
    builder.setAllowUnlimited(allowUnlimited);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    spd.validateValue(value);
  }

  /**
   * Creates illegal data for validate value
   * @return data
   */
  @DataProvider(name = "illegalValidateValueData")
  public Object[][] createIllegalValidateValueData() {
    return new Object[][]{
            {5L, 10L, false, null},
            {5L, 10L, false, 1L},
            {5L, 10L, false, 11L},
            {5L, 10L, false, -1L},
            {5L, 10L, true, 2L},
            {5L, 10L, true, 11L}
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
  public void testValidateValue2(Long low, Long high, boolean allowUnlimited, Long value) {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setLowerLimit(low);
    builder.setUpperLimit(high);
    builder.setAllowUnlimited(allowUnlimited);
    SizePropertyDefinition spd = buildTestDefinition(builder);
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
            {0L, "0b"},
            {1L, "1b"},
            {2L, "2b"},
            {999L, "999b"},
            {1000L, "1kb"},
            {1001L, "1001b"},
            {1023L, "1023b"},
            {1024L, "1024b"},
            {1025L, "1025b"},
            {1000L * 1000L, "1mb"},
            {1000L * 1000L * 1000L, "1gb"},
            {1000L * 1000L * 1000L * 1000L, "1tb"}

    };
  }

  /**
   * Tests encode value
   * @param value to encode
   * @param expectedValue to compare
   */
  @Test(dataProvider = "encodeValueData")
  public void testEncodeValue(Long value, String expectedValue) {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    assertEquals(spd.encodeValue(value), expectedValue);
  }

  /**
   * Test that accept doesn't throw and exception
   */
  @Test
  public void testAccept() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    PropertyDefinitionVisitor<Boolean, Void> v = new AbstractPropertyDefinitionVisitor<Boolean, Void>() {

      public Boolean visitSize(SizePropertyDefinition d,
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
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    spd.toString();
  }

  @Test
  public void testCompare() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    SizePropertyDefinition spd = buildTestDefinition(builder);
    spd.compare(1L, 2L);
  }

  @Test
  public void testSetDefaultBehaviorProvider() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setAllowUnlimited(true);
    builder.setDefaultBehaviorProvider(new DefaultBehaviorProvider<Long>() {
      public <R, P> R accept(DefaultBehaviorProviderVisitor<Long, R, P> v, P p) {
        return null;
      }
    });
  }

  @Test
  public void testSetOption() {
    SizePropertyDefinition.Builder builder = createTestBuilder();
    builder.setOption(PropertyOption.HIDDEN);
  }

  private SizePropertyDefinition.Builder createTestBuilder() {
    return SizePropertyDefinition.createBuilder("test-property-name");
  }

  private SizePropertyDefinition buildTestDefinition(SizePropertyDefinition.Builder builder) {
    return builder.buildInstance("test-prop",
            EnumSet.noneOf(PropertyOption.class),
            new DefinedDefaultBehaviorProvider<Long>("0"));
  }

}
