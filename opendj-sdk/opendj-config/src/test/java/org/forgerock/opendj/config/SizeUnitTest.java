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

import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SizeUnitTest extends ConfigTestCase {

    @DataProvider(name = "stringToSizeLimitData")
    public Object[][] createStringToSizeLimitData() {
        return new Object[][] {
            { "b", SizeUnit.BYTES },
            { "kb", SizeUnit.KILO_BYTES },
            { "kib", SizeUnit.KIBI_BYTES },
            { "mb", SizeUnit.MEGA_BYTES },
            { "mib", SizeUnit.MEBI_BYTES },
            { "gb", SizeUnit.GIGA_BYTES },
            { "gib", SizeUnit.GIBI_BYTES },
            { "tb", SizeUnit.TERA_BYTES },
            { "tib", SizeUnit.TEBI_BYTES },
        };
    }

    @Test(dataProvider = "stringToSizeLimitData")
    public void testGetUnit(String name, SizeUnit expectedUnit) {
        SizeUnit unit = SizeUnit.getUnit(name);
        assertEquals(unit, expectedUnit);
    }

    @DataProvider(name = "parseValueData")
    public Object[][] createParseValueData() {
        return new Object[][] {
            { "1.0 b", 1L },
            { "1.0 kb", 1000L },
            { "1.0 kib", 1024L },
            { "0b", 0L },
            { "0 b", 0L },
            { "0 bytes", 0L },
            { "0kb", 0L },
            { "0 kilobytes", 0L },
            { "0 KILOBYTES", 0L },
            { "0 KB", 0L },
            { "1b", 1L },
            { "1 b", 1L },
            { "1 bytes", 1L },
            { "1kb", 1000L },
            { "1 kilobytes", 1000L },
            { "1 KILOBYTES", 1000L },
            { "1 KB", 1000L },
            { "1000b", 1000L },
            { "1000 b", 1000L },
            { "1000 bytes", 1000L },
            { "1000kb", 1000000L },
            { "1000 kilobytes", 1000000L },
            { "1000 KILOBYTES", 1000000L },
            { "1000 KB", 1000000L },
        };
    }

    @Test(dataProvider = "parseValueData")
    public void testParseValue(String valueToParse, long expectedValue) {
        assertEquals(SizeUnit.parseValue(valueToParse), expectedValue);
    }

    @DataProvider(name = "parseValueIllegalData")
    public Object[][] createParseValueIllegalData() {
        return new Object[][] {
            { "a.0 b" },
            { "1.a kb" },
            { "1.0 xx" },
            { "" },
            { "hello" },
            { "-1" },
            { "-1b" },
            { "1" },
            { "1x" },
            { "1.1y" }
        };
    }


    @Test(dataProvider = "parseValueIllegalData", expectedExceptions = NumberFormatException.class)
    public void testParseValueIllegal(String value) {
        SizeUnit.parseValue(value);
    }

    @DataProvider(name = "valuesToKiloBytes")
    public Object[][] createConversionData() {
        return new Object[][] {
            { "1.0 b", 1L },
            { "1.0 kb", 1000L },
            { "1.0 kib", 1024L },
            { "1.0", 1000L },
            { "1000", 1000000L },
            { "1MB", 1000000L }
        };
    }

    @Test(dataProvider = "valuesToKiloBytes")
    public void testParseValueWithUnit(String value, long expectedValueInKB) {
        assertEquals(SizeUnit.parseValue(value, SizeUnit.KILO_BYTES), expectedValueInKB);
    }

    @DataProvider(name = "parseValueIllegalDataKB")
    public Object[][] createParseValueIllegalDataKB() {
        return new Object[][] {
            { "a.0 b" },
            { "1.a kb" },
            { "1.0 xx" },
            { "" },
            { "hello" },
            { "-1" },
            { "-1b" },
            { "1x" },
            { "1.1y" }
        };
    }

    @Test(dataProvider = "parseValueIllegalDataKB", expectedExceptions = NumberFormatException.class)
    public void testParseValueIllegalWithUnit(String value) {
        SizeUnit.parseValue(value, SizeUnit.KILO_BYTES);
    }

    @DataProvider(name = "fromBytesTestData")
    public Object[][] createFromBytesTestData() {
        return new Object[][] {
            { SizeUnit.BYTES, 1L, 1D }
            // TODO: more data
        };
    }

    @Test(dataProvider = "fromBytesTestData")
    public void testFromBytes(SizeUnit unit, long value, double expected) {
        assertEquals(unit.fromBytes(value), expected);
    }

    @DataProvider(name = "bestFitUnitExactData")
    public Object[][] createBestFitExactData() {
        return new Object[][] {
            { 0, SizeUnit.BYTES },
            { 999, SizeUnit.BYTES },
            { 1000, SizeUnit.KILO_BYTES },
            { 1024, SizeUnit.KIBI_BYTES },
            { 1025, SizeUnit.BYTES },
            { 999999, SizeUnit.BYTES },
            { 1000000, SizeUnit.MEGA_BYTES },
            { 1000001, SizeUnit.BYTES } };
    }


    @Test(dataProvider = "bestFitUnitExactData")
    public void testGetBestFitUnitExact(long valueForWhichBestFitSought, SizeUnit expectedUnit) {
        assertEquals(SizeUnit.getBestFitUnitExact(valueForWhichBestFitSought), expectedUnit);
    }

    @DataProvider(name = "bestFitUnitData")
    public Object[][] createBestFitData() {
        return new Object[][] {
            { 0, SizeUnit.BYTES },
            { 999, SizeUnit.BYTES },
            { 1000, SizeUnit.KILO_BYTES },
            { 1024, SizeUnit.KIBI_BYTES },
            { 1025, SizeUnit.KILO_BYTES },
            { 999999, SizeUnit.KILO_BYTES },
            { 1000000, SizeUnit.MEGA_BYTES },
            { 1000001, SizeUnit.MEGA_BYTES } };
    }

    @Test(dataProvider = "bestFitUnitData")
    public void testGetBestFitUnit(long valueForWhichBestFitSought, SizeUnit expectedUnit) {
        assertEquals(SizeUnit.getBestFitUnit(valueForWhichBestFitSought), expectedUnit);
    }

    @DataProvider(name = "longNameData")
    public Object[][] createLongNameData() {
        return new Object[][] {
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

    @Test(dataProvider = "longNameData")
    public void testGetLongName(SizeUnit unit, String expectedName) {
        assertEquals(unit.getLongName(), expectedName);
    }

    @DataProvider(name = "shortNameData")
    public Object[][] createShortNameData() {
        return new Object[][] {
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

    @Test(dataProvider = "shortNameData")
    public void testGetShortName(SizeUnit unit, String expectedShortName) {
        assertEquals(unit.getShortName(), expectedShortName);
    }

    @DataProvider(name = "sizeData")
    public Object[][] createSizeData() {
        return new Object[][] {
            { SizeUnit.BYTES, 1L },
            { SizeUnit.KILO_BYTES, 1000L },
            { SizeUnit.KIBI_BYTES, 1024L },
            { SizeUnit.MEGA_BYTES, 1000L * 1000 },
            { SizeUnit.MEBI_BYTES, 1024L * 1024 },
            { SizeUnit.GIGA_BYTES, 1000L * 1000 * 1000 },
            { SizeUnit.GIBI_BYTES, 1024L * 1024 * 1024 },
            { SizeUnit.TERA_BYTES, 1000L * 1000 * 1000 * 1000 },
            { SizeUnit.TEBI_BYTES, 1024L * 1024 * 1024 * 1024 },
        };
    }

    @Test(dataProvider = "sizeData")
    public void testGetSize(SizeUnit unit, long expectedSize) {
        assertEquals(unit.getSize(), expectedSize);
    }

    @DataProvider(name = "toBytesData")
    public Object[][] createToBytesData() {
        return new Object[][] {
            // unit to test, amount of unit in bytes, expected
            { SizeUnit.BYTES, 1D, 1L } };
    }

    @Test(dataProvider = "toBytesData")
    public void testToBytes(SizeUnit unit, double amountOfUnitInBytes, long expected) {
        assertEquals(unit.toBytes(amountOfUnitInBytes), expected);
    }

    @Test(dataProvider = "shortNameData")
    public void testToString(SizeUnit unit, String expected) {
        assertEquals(unit.toString(), expected);
    }

}
