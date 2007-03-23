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
import static org.testng.Assert.*;
import org.testng.annotations.*;

/**
 * DurationUnit Tester.
 */
public class DurationUnitTest {

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
    @DataProvider(name = "testGetBestFitUnit")
    public Object[][] createGetBestFitData() {
      return new Object[][]{
              { MINUTES, 0, MINUTES },
              { MINUTES, .5D, SECONDS },
              { MINUTES, 119D, MINUTES },
              { MINUTES, 120D, HOURS },
              { MINUTES, 121D, MINUTES },
              { MINUTES, Double.MAX_VALUE, MINUTES },
              { MINUTES, Double.MIN_VALUE, MINUTES }
      };
    }

    /**
     * @param unit of best fit value
     * @param value ordinal value
     * @param expectedValue for comparison
     */
    @Test(dataProvider = "testGetBestFitUnit")
    public void testGetBestFitUnit(DurationUnit unit, double value, DurationUnit expectedValue) {
        assertEquals(unit.getBestFitUnit(value), expectedValue);
    }

}
