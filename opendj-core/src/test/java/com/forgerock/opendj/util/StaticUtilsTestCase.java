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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package com.forgerock.opendj.util;

import java.lang.reflect.InvocationTargetException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.forgerock.opendj.util.StaticUtils.*;

import static org.fest.assertions.Assertions.*;

/**
 * Test {@code StaticUtils}.
 */
@SuppressWarnings("javadoc")
public final class StaticUtilsTestCase extends UtilTestCase {
    /**
     * Create data for format(...) tests.
     *
     * @return Returns test data.
     */
    @DataProvider(name = "createFormatData")
    public Object[][] createFormatData() {
        return new Object[][] {
            // Note that Calendar months run from 0-11,
            // and that there was no such year as year 0 (1 BC -> 1 AD).
            { 1, 0, 1, 0, 0, 0, 0, "00010101000000.000Z" },
            { 9, 0, 1, 0, 0, 0, 0, "00090101000000.000Z" },
            { 10, 0, 1, 0, 0, 0, 0, "00100101000000.000Z" },
            { 99, 0, 1, 0, 0, 0, 0, "00990101000000.000Z" },
            { 100, 0, 1, 0, 0, 0, 0, "01000101000000.000Z" },
            { 999, 0, 1, 0, 0, 0, 0, "09990101000000.000Z" },
            { 1000, 0, 1, 0, 0, 0, 0, "10000101000000.000Z" },
            { 2000, 0, 1, 0, 0, 0, 0, "20000101000000.000Z" },
            { 2099, 0, 1, 0, 0, 0, 0, "20990101000000.000Z" },
            { 2000, 8, 1, 0, 0, 0, 0, "20000901000000.000Z" },
            { 2000, 9, 1, 0, 0, 0, 0, "20001001000000.000Z" },
            { 2000, 10, 1, 0, 0, 0, 0, "20001101000000.000Z" },
            { 2000, 11, 1, 0, 0, 0, 0, "20001201000000.000Z" },
            { 2000, 0, 9, 0, 0, 0, 0, "20000109000000.000Z" },
            { 2000, 0, 10, 0, 0, 0, 0, "20000110000000.000Z" },
            { 2000, 0, 19, 0, 0, 0, 0, "20000119000000.000Z" },
            { 2000, 0, 20, 0, 0, 0, 0, "20000120000000.000Z" },
            { 2000, 0, 29, 0, 0, 0, 0, "20000129000000.000Z" },
            { 2000, 0, 30, 0, 0, 0, 0, "20000130000000.000Z" },
            { 2000, 0, 31, 0, 0, 0, 0, "20000131000000.000Z" },
            { 2000, 0, 1, 9, 0, 0, 0, "20000101090000.000Z" },
            { 2000, 0, 1, 10, 0, 0, 0, "20000101100000.000Z" },
            { 2000, 0, 1, 19, 0, 0, 0, "20000101190000.000Z" },
            { 2000, 0, 1, 20, 0, 0, 0, "20000101200000.000Z" },
            { 2000, 0, 1, 23, 0, 0, 0, "20000101230000.000Z" },
            { 2000, 0, 1, 0, 9, 0, 0, "20000101000900.000Z" },
            { 2000, 0, 1, 0, 10, 0, 0, "20000101001000.000Z" },
            { 2000, 0, 1, 0, 59, 0, 0, "20000101005900.000Z" },
            { 2000, 0, 1, 0, 0, 9, 0, "20000101000009.000Z" },
            { 2000, 0, 1, 0, 0, 10, 0, "20000101000010.000Z" },
            { 2000, 0, 1, 0, 0, 59, 0, "20000101000059.000Z" },
            { 2000, 0, 1, 0, 0, 0, 9, "20000101000000.009Z" },
            { 2000, 0, 1, 0, 0, 0, 10, "20000101000000.010Z" },
            { 2000, 0, 1, 0, 0, 0, 99, "20000101000000.099Z" },
            { 2000, 0, 1, 0, 0, 0, 100, "20000101000000.100Z" },
            { 2000, 0, 1, 0, 0, 0, 999, "20000101000000.999Z" }, };
    }

    @DataProvider(name = "dataForToLowerCase")
    public Object[][] dataForToLowerCase() {
        // Value, toLowerCase or null if identity
        return new Object[][] {
            { "", null },
            { " ", null },
            { "   ", null },
            { "12345", null },
            { "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./", null },
            { "Aabcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./",
                "aabcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./" },
            { "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./A",
                "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./a" },
            { "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz" },
            { "\u00c7edilla", "\u00e7edilla" }, { "ced\u00cdlla", "ced\u00edlla" }, };
    }

    /**
     * Tests {@link StaticUtils#formatAsGeneralizedTime(java.util.Date)}.
     *
     * @param yyyy
     *            The year.
     * @param months
     *            The month.
     * @param dd
     *            The day.
     * @param hours
     *            The hour.
     * @param mm
     *            The minute.
     * @param ss
     *            The second.
     * @param ms
     *            The milli-seconds.
     * @param expected
     *            The expected generalized time formatted string.
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(dataProvider = "createFormatData")
    public void testFormatDate(final int yyyy, final int months, final int dd, final int hours,
            final int mm, final int ss, final int ms, final String expected) throws Exception {
        final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(yyyy, months, dd, hours, mm, ss);
        calendar.set(Calendar.MILLISECOND, ms);
        final Date time = new Date(calendar.getTimeInMillis());
        final String actual = formatAsGeneralizedTime(time);
        Assert.assertEquals(actual, expected);
    }

    /**
     * Tests {@link StaticUtils#formatAsGeneralizedTime(long)} .
     *
     * @param yyyy
     *            The year.
     * @param months
     *            The month.
     * @param dd
     *            The day.
     * @param hours
     *            The hour.
     * @param mm
     *            The minute.
     * @param ss
     *            The second.
     * @param ms
     *            The milli-seconds.
     * @param expected
     *            The expected generalized time formatted string.
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(dataProvider = "createFormatData")
    public void testFormatLong(final int yyyy, final int months, final int dd, final int hours,
            final int mm, final int ss, final int ms, final String expected) throws Exception {
        final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(yyyy, months, dd, hours, mm, ss);
        calendar.set(Calendar.MILLISECOND, ms);
        final long time = calendar.getTimeInMillis();
        final String actual = formatAsGeneralizedTime(time);
        Assert.assertEquals(actual, expected);
    }

    @Test(dataProvider = "dataForToLowerCase")
    public void testToLowerCaseString(final String s, final String expected) {
        final String actual = toLowerCase(s);
        if (expected != null) {
            Assert.assertEquals(actual, expected);
        } else {
            Assert.assertSame(actual, s);
        }
    }

    @Test(dataProvider = "dataForToLowerCase")
    public void testToLowerCaseStringBuilder(final String s, final String expected) {
        final StringBuilder builder = new StringBuilder();
        final String actual = toLowerCase(s, builder).toString();
        if (expected != null) {
            Assert.assertEquals(actual, expected);
        } else {
            Assert.assertEquals(actual, s);
        }
    }

    @DataProvider
    public Object[][] stackTraceToSingleLineLimitedStackProvider() {
        final String noMessageTrace = "RuntimeException (StaticUtilsTestCase.java";
        final String messageTrace = "RuntimeException: message (StaticUtilsTestCase.java";
        return new Object[][] {
            { null, "" },
            { new RuntimeException(),   noMessageTrace },
            { new RuntimeException(""), noMessageTrace },
            { new RuntimeException("message"), messageTrace },
            { new InvocationTargetException(new RuntimeException()),   noMessageTrace },
            { new InvocationTargetException(new RuntimeException("")), noMessageTrace },
            { new InvocationTargetException(new RuntimeException("message")), messageTrace },
            {
                new RuntimeException(new RuntimeException("message")),
                "RuntimeException: java.lang.RuntimeException: message (StaticUtilsTestCase.java"
            },
            { new RuntimeException("message", new RuntimeException()), messageTrace },
            { new RuntimeException("message", new RuntimeException("message")), messageTrace },
        };
    }

    @Test(dataProvider = "stackTraceToSingleLineLimitedStackProvider")
    public void testStackTraceToSingleLineLimitedStack1(Throwable t, String expectedStartWith) {
        final String trace = stackTraceToSingleLineString(t, false);
        assertThat(trace).startsWith(expectedStartWith);
        if (t != null) {
            assertThat(trace).endsWith("...)");
        }
    }

    @Test(dataProvider = "getBytesTestData")
    public void testCharsToBytes(String inputString) throws Exception {
        Assert.assertEquals(StaticUtils.getBytes(inputString.toCharArray()), inputString.getBytes("UTF-8"));
    }

    @Test(dataProvider = "byteToHexTestData")
    public void testByteToASCII(byte b) throws Exception {
        if (b < 32 || b > 126) {
            Assert.assertEquals(byteToASCII(b), ' ');
        } else {
            Assert.assertEquals(byteToASCII(b), (char) b);
        }
    }

    @DataProvider
    public Object[][] stackTraceToSingleLineFullStackStackProvider() {
        return new Object[][] {
            { null, "", "" },
            { new RuntimeException(),   "java.lang.RuntimeException / StaticUtilsTestCase.java:", "" },
            { new RuntimeException(""), "java.lang.RuntimeException / StaticUtilsTestCase.java:", "" },
            {
                new RuntimeException("message"),
                "java.lang.RuntimeException: message / StaticUtilsTestCase.java:", "message"
            },
            {
                new InvocationTargetException(new RuntimeException("message")),
                "java.lang.reflect.InvocationTargetException / StaticUtilsTestCase.java:", "message"
            },
            {
                new RuntimeException(new RuntimeException()),
                "java.lang.RuntimeException: java.lang.RuntimeException / StaticUtilsTestCase.java:",
                "java.lang.RuntimeException "
            },
            {
                new RuntimeException(new RuntimeException("message")),
                "java.lang.RuntimeException: java.lang.RuntimeException: message / StaticUtilsTestCase.java:",
                "java.lang.RuntimeException: message"
            },
            {
                new RuntimeException("message", new RuntimeException()),
                "java.lang.RuntimeException: message / StaticUtilsTestCase.java:", "java.lang.RuntimeException "
            },
            {
                new RuntimeException("message", new RuntimeException("message")),
                "java.lang.RuntimeException: message / StaticUtilsTestCase.java:", "java.lang.RuntimeException: message"
            },
        };
    }

    @Test(dataProvider = "stackTraceToSingleLineFullStackStackProvider")
    public void testStackTraceToSingleLineFullStack1(Exception throwable, String expectedStartWith,
            String expectedContains) {
        String trace = stackTraceToSingleLineString(throwable, true);
        assertThat(trace).startsWith(expectedStartWith);
        assertThat(trace).contains(expectedContains);
        assertThat(trace).doesNotContain("...)");
    }

    @DataProvider(name = "byteToHexTestData")
    public Object[][] createByteToHexTestData() {
        Object[][] data = new Object[256][];

        for (int i = 0; i < 256; i++) {
            data[i] = new Object[] { new Byte((byte) i) };
        }

        return data;
    }

    @DataProvider(name = "getBytesTestData")
    public Object[][] createGetBytesTestData() {
        List<String> strings = new LinkedList<>();

        // Some simple strings.
        strings.add("");
        strings.add(" ");
        strings.add("an ascii string");

        // A string containing just UTF-8 1 byte sequences.
        StringBuilder builder = new StringBuilder();
        for (char c = '\u0000'; c < '\u0080'; c++) {
            builder.append(c);
        }
        strings.add(builder.toString());

        // A string containing UTF-8 1 and 2 byte sequences.
        builder = new StringBuilder();
        for (char c = '\u0000'; c < '\u0100'; c++) {
            builder.append(c);
        }
        strings.add(builder.toString());

        // A string containing UTF-8 1 and 6 byte sequences.
        builder = new StringBuilder();
        for (char c = '\u0000'; c < '\u0080'; c++) {
            builder.append(c);
        }
        for (char c = '\uff00'; c != '\u0000'; c++) {
            builder.append(c);
        }
        strings.add(builder.toString());

        // Construct the array.
        Object[][] data = new Object[strings.size()][];
        for (int i = 0; i < strings.size(); i++) {
            data[i] = new Object[] { strings.get(i) };
        }

        return data;
    }
}
