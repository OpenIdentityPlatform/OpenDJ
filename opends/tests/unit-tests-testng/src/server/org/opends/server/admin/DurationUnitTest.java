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

import static org.opends.server.admin.DurationUnit.*;
import org.opends.server.DirectoryServerTestCase;
import static org.testng.Assert.*;
import org.testng.annotations.*;

/**
 * DurationUnit Tester.
 */
public class DurationUnitTest extends DirectoryServerTestCase {

  /**
   * @return test data for testing getUnit
   */
  @DataProvider(name = "testGetUnitData")
  public Object[][] createStringToSizeLimitData() {
    return new Object[][]{
            { "ms", MILLI_SECONDS },
            { "milliseconds", MILLI_SECONDS },
            { "s", SECONDS },
            { "seconds", SECONDS },
            { "m", MINUTES },
            { "minutes", MINUTES },
            { "h", HOURS },
            { "hours", HOURS },
            { "d", DAYS },
            { "days", DAYS },
            { "w", WEEKS },
            { "weeks", WEEKS }
    };
  }


    /**
     * Tests getUnit()
     * @param unitString for creating a duration unit
     * @param unit for comparison
     */
    @Test(dataProvider = "testGetUnitData")
    public void testGetUnit1(String unitString, DurationUnit unit) {
        assertEquals(getUnit(unitString), unit);
    }

    /**
     * Tests that getUnit() throws an exception of non-duration unit strings
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetUnit2() {
        getUnit("xxx");
    }

    /**
     * @return test data for testing getUnit
     */
    @DataProvider(name = "testToString")
    public Object[][] createToStringData() {
      return new Object[][]{
              { 0L, "0 ms" },
              { 1L, "1 ms" },
              { 999L, "999 ms" },
              { 1000L, "1 s" },
              { 1001L, "1 s 1 ms" },
              { 59999L, "59 s 999 ms" },
              { 60000L, "1 m" },
              { 3599999L, "59 m 59 s 999 ms" },
              { 3600000L, "1 h" }
      };
    }

    /**
     * @param value ordinal value
     * @param expected for comparison
     */
    @Test(dataProvider = "testToString")
    public void testToString(long value, String expected) {
        assertEquals(DurationUnit.toString(value), expected);
    }

    /**
     * @param expected for comparison
     * @param value for parsing
     */
    @Test(dataProvider = "testToString")
    public void testParseValue(long expected, String value) {
        assertEquals(DurationUnit.parseValue(value), expected);
    }

}
