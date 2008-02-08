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
import org.testng.annotations.*;
import org.opends.server.DirectoryServerTestCase;

/**
 * SizeUnit Tester.
 */
public class SizeUnitTest extends DirectoryServerTestCase {

  /**
   * Creates data for testing String to SizeUnit conversions
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider(name = "stringToSizeLimitData")
  public Object[][] createStringToSizeLimitData() {
    return new Object[][]{
            {"b", SizeUnit.BYTES},
            {"kb", SizeUnit.KILO_BYTES},
            {"kib", SizeUnit.KIBI_BYTES},
            {"mb", SizeUnit.MEGA_BYTES},
            {"mib", SizeUnit.MEBI_BYTES},
            {"gb", SizeUnit.GIGA_BYTES},
            {"gib", SizeUnit.GIBI_BYTES},
            {"tb", SizeUnit.TERA_BYTES},
            {"tib", SizeUnit.TEBI_BYTES}
    };
  }

  /**
   * Tests String to SizeUnit conversions
   * @param name of unit
   * @param expectedUnit for comparison
   */
  @Test(dataProvider = "stringToSizeLimitData")
  public void testGetUnit(String name, SizeUnit expectedUnit) {
    SizeUnit unit = SizeUnit.getUnit(name);
    assertEquals(unit, expectedUnit);
  }

  /**
   * Creates data for testing String to SizeUnit conversions
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider(name = "parseValue1Data")
  public Object[][] createParseValue1Data() {
    return new Object[][]{
            {"1.0 b", 1L},
            {"1.0 kb", 1000L},
            {"1.0 kib", 1024L},
            {"0b", 0L}, {"0 b", 0L},
            {"0 bytes", 0L}, {"0kb", 0L}, {"0 kilobytes", 0L},
            {"0 KILOBYTES", 0L}, {"0 KB", 0L}, {"1b", 1L},
            {"1 b", 1L}, {"1 bytes", 1L}, {"1kb", 1000L},
            {"1 kilobytes", 1000L}, {"1 KILOBYTES", 1000L},
            {"1 KB", 1000L}, {"1000b", 1000L}, {"1000 b", 1000L},
            {"1000 bytes", 1000L}, {"1000kb", 1000000L},
            {"1000 kilobytes", 1000000L}, {"1000 KILOBYTES", 1000000L},
            {"1000 KB", 1000000L}
    };
  }

  /**
   * Tests parsing of SizeUnits specified as String values
   * @param value to parse
   * @param expectedValue for comparison
   */
  @Test(dataProvider = "parseValue1Data")
  public void testParseValue1(String value, long expectedValue) {
    assertEquals(SizeUnit.parseValue(value), expectedValue);
  }

  /**
   * Creates illegal data for testing String to SizeUnit conversions
   *
   * @return The array of illegal test strings.
   */
  @DataProvider(name = "parseValue2Data")
  public Object[][] createParseValue2Data() {
    return new Object[][]{
            {"a.0 b"},
            {"1.a kb"},
            {"1.0 xx"},
            { "" },
            { "hello" },
            { "-1" },
            { "-1b" },
            { "1" },
            { "1x" },
            { "1.1y" }
    };
  }

  /**
   * Tests that illegal String specified SizeUnits throw exceptions
   * @param value to parse
   */
  @Test(dataProvider = "parseValue2Data", expectedExceptions = NumberFormatException.class)
  public void testParseValue2(String value) {
    SizeUnit.parseValue(value);
  }

  /**
   * Creates data for testing String to SizeUnit conversions
   *
   * @return The array of test strings.
   */
  @DataProvider(name = "parseValue3Data")
  public Object[][] createParseValue3Data() {
    return new Object[][]{
            {"1.0 b", 1L},
            {"1.0 kb", 1000L},
            {"1.0 kib", 1024L},
            {"1.0", 1000L},
            {"1000", 1000000L},
            {"1MB", 1000000L}
    };
  }

  /**
   * Tests parsing of SizeUnits specified as String values
   * @param value to parse
   * @param expectedValue for comparison
   */
  @Test(dataProvider = "parseValue3Data")
  public void testParseValue3(String value, long expectedValue) {
    assertEquals(SizeUnit.parseValue(value, SizeUnit.KILO_BYTES), expectedValue);
  }

  /**
   * Creates illegal data for testing String to SizeUnit conversions
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider(name = "parseValue4Data")
  public Object[][] createParseValue4Data() {
    return new Object[][]{
            {"a.0 b"},
            {"1.a kb"},
            {"1.0 xx"},
            { "" },
            { "hello" },
            { "-1" },
            { "-1b" },
            { "1x" },
            { "1.1y" }
    };
  }

  /**
   * Tests that illegal String specified SizeUnits throw exceptions
   * @param value to parse
   */
  @Test(dataProvider = "parseValue4Data", expectedExceptions = NumberFormatException.class)
  public void testParseValue4(String value) {
    SizeUnit.parseValue(value, SizeUnit.KILO_BYTES);
  }
  
  /**
   * Creates data for testing fromBytes
   *
   * @return data
   */
  @DataProvider(name = "fromBytesTestData")
  public Object[][] createFromBytesTestData() {
    return new Object[][]{
            { SizeUnit.BYTES, 1L, 1D }
            // TODO: more data
    };
  }

  /**
   * Tests conversion from byte value
   * @param unit to use
   * @param value of types
   * @param expected for comparison
   */
  @Test(dataProvider = "fromBytesTestData")
  public void testFromBytes(SizeUnit unit, long value, double expected) {
    assertEquals(unit.fromBytes(value), expected);
  }

  /**
   * Creates data for testing fromBytes
   *
   * @return data
   */
  @DataProvider(name = "bestFitUnitExactData")
  public Object[][] createBestFitExactData() {
    return new Object[][]{
            { 0, SizeUnit.BYTES },
            { 999, SizeUnit.BYTES },
            { 1000, SizeUnit.KILO_BYTES },
            { 1024, SizeUnit.KIBI_BYTES },
            { 1025, SizeUnit.BYTES },
            { 999999, SizeUnit.BYTES },
            { 1000000, SizeUnit.MEGA_BYTES },
            { 1000001, SizeUnit.BYTES }
    };
  }

  /**
   * Test best fit
   * @param value for which best fit sought
   * @param expectedUnit for comparison
   */
  @Test(dataProvider = "bestFitUnitExactData")
  public void testGetBestFitUnitExact(long value, SizeUnit expectedUnit) {
    assertEquals(SizeUnit.getBestFitUnitExact(value), expectedUnit);
  }

  /**
   * Creates data for testing fromBytes
   *
   * @return data
   */
  @DataProvider(name = "bestFitUnitData")
  public Object[][] createBestFitData() {
    return new Object[][]{
            { 0, SizeUnit.BYTES },
            { 999, SizeUnit.BYTES },
            { 1000, SizeUnit.KILO_BYTES },
            { 1024, SizeUnit.KIBI_BYTES },
            { 1025, SizeUnit.KILO_BYTES },
            { 999999, SizeUnit.KILO_BYTES },
            { 1000000, SizeUnit.MEGA_BYTES },
            { 1000001, SizeUnit.MEGA_BYTES }
    };
  }

  /**
   * Test best fit
   * @param value for which best fit sought
   * @param expectedUnit for comparison
   */
  @Test(dataProvider = "bestFitUnitData")
  public void testGetBestFitUnit(long value, SizeUnit expectedUnit) {
    assertEquals(SizeUnit.getBestFitUnit(value), expectedUnit);
  }

  /**
   * @return data for testGetLongName
   */
  @DataProvider(name = "longNameData")
  public Object[][] createLongNameData() {
    return new Object[][]{
            { SizeUnit.BYTES, "bytes" },
            { SizeUnit.KILO_BYTES, "kilobytes" },
            { SizeUnit.KIBI_BYTES, "kibibytes" },
            { SizeUnit.MEGA_BYTES, "megabytes" },
            { SizeUnit.MEBI_BYTES, "mebibytes" },
            { SizeUnit.GIGA_BYTES, "gigabytes" },
            { SizeUnit.GIBI_BYTES, "gibibytes" },
            { SizeUnit.TERA_BYTES, "terabytes" },
            { SizeUnit.TEBI_BYTES, "tebibytes" }
    };
  }

  /**
   * Tests getLongName()
   * @param unit to test
   * @param expectedName for comparison
   */
  @Test(dataProvider = "longNameData")
  public void testGetLongName(SizeUnit unit, String expectedName) {
    assertEquals(unit.getLongName(), expectedName);
  }

  /**
   * Creates data for testGetShortName
   * @return test data
   */
  @DataProvider(name = "shortNameData")
  public Object[][] createShortNameData() {
    return new Object[][]{
            { SizeUnit.BYTES, "b" },
            { SizeUnit.KILO_BYTES, "kb" },
            { SizeUnit.KIBI_BYTES, "kib" },
            { SizeUnit.MEGA_BYTES, "mb" },
            { SizeUnit.MEBI_BYTES, "mib" },
            { SizeUnit.GIGA_BYTES, "gb" },
            { SizeUnit.GIBI_BYTES, "gib" },
            { SizeUnit.TERA_BYTES, "tb" },
            { SizeUnit.TEBI_BYTES, "tib" }
    };
  }

  /**
   * Tests getShortName
   * @param unit to test
   * @param expectedShortName for comparison
   */
  @Test(dataProvider = "shortNameData")
  public void testGetShortName(SizeUnit unit, String expectedShortName) {
    assertEquals(unit.getShortName(), expectedShortName);
  }

  /**
   * Creates data for testGetShortName
   * @return test data
   */
  @DataProvider(name = "sizeData")
  public Object[][] createSizeData() {
    return new Object[][]{
            { SizeUnit.BYTES, 1L },
            { SizeUnit.KILO_BYTES, 1000L },
            { SizeUnit.KIBI_BYTES, 1024L },
            { SizeUnit.MEGA_BYTES, (long) 1000 * 1000 },
            { SizeUnit.MEBI_BYTES, (long) 1024 * 1024 },
            { SizeUnit.GIGA_BYTES, (long) 1000 * 1000 * 1000 },
            { SizeUnit.GIBI_BYTES, (long) 1024 * 1024 * 1024 },
            { SizeUnit.TERA_BYTES, (long) 1000 * 1000 * 1000 * 1000 },
            { SizeUnit.TEBI_BYTES, (long) 1024 * 1024 * 1024 * 1024 }
    };
  }

  /**
   * Tests getSize
   * @param unit to test
   * @param expectedSize for comparison
   */
  @Test(dataProvider = "sizeData")
  public void testGetSize(SizeUnit unit, long expectedSize) {
    assertEquals(unit.getSize(), expectedSize);
  }

  /**
   * Creates data for testGetShortName
   * @return test data
   */
  @DataProvider(name = "toBytesData")
  public Object[][] createToBytesData() {
    return new Object[][]{
            { SizeUnit.BYTES, 1D, 1L }
    };
  }

  /**
   * Tests toBytes
   * @param unit to test
   * @param amt of unit in bytes
   * @param expected for comparison
   */
  @Test(dataProvider = "toBytesData")
  public void testToBytes(SizeUnit unit, double amt, long expected) {
    assertEquals(unit.toBytes(amt), expected);
  }

  /**
   * Tests toString
   * @param unit to test
   * @param exprected for comparison
   */
  @Test(dataProvider = "shortNameData")
  public void testToString(SizeUnit unit, String exprected) {
    assertEquals(unit.toString(), exprected);
  }

}
