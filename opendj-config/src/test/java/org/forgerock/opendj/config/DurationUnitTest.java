/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import static org.forgerock.opendj.config.DurationUnit.DAYS;
import static org.forgerock.opendj.config.DurationUnit.HOURS;
import static org.forgerock.opendj.config.DurationUnit.MILLI_SECONDS;
import static org.forgerock.opendj.config.DurationUnit.MINUTES;
import static org.forgerock.opendj.config.DurationUnit.SECONDS;
import static org.forgerock.opendj.config.DurationUnit.WEEKS;
import static org.forgerock.opendj.config.DurationUnit.getUnit;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DurationUnitTest extends ConfigTestCase {

    @DataProvider(name = "testGetUnitData")
    public Object[][] createStringToSizeLimitData() {
        return new Object[][] {
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
            { "weeks", WEEKS } };
    }

    @Test(dataProvider = "testGetUnitData")
    public void testGetUnit(String unitString, DurationUnit unit) {
        assertEquals(getUnit(unitString), unit);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetUnitWithIllegalString() {
        getUnit("xxx");
    }

    @DataProvider(name = "valueToStringData")
    public Object[][] createValueToStringData() {
        return new Object[][] {
            { 0L, "0 ms" },
            { 1L, "1 ms" },
            { 999L, "999 ms" },
            { 1000L, "1 s" },
            { 1001L, "1 s 1 ms" },
            { 59999L, "59 s 999 ms" },
            { 60000L, "1 m" },
            { 3599999L, "59 m 59 s 999 ms" },
            { 3600000L, "1 h" } };
    }

    @Test(dataProvider = "valueToStringData")
    public void testToString(long ordinalValue, String expectedString) {
        assertEquals(DurationUnit.toString(ordinalValue), expectedString);
    }

    @Test(dataProvider = "valueToStringData")
    public void testParseValue(long expectedOrdinal, String value) {
        assertEquals(DurationUnit.parseValue(value), expectedOrdinal);
    }

}
