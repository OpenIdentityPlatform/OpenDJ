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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.*;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Generalized time tests.
 */
@SuppressWarnings("javadoc")
public class GeneralizedTimeTest extends SdkTestCase {

    @DataProvider
    public Object[][] calendars() {
        // Test time zone.
        final GregorianCalendar europeWinter = new GregorianCalendar();
        europeWinter.setLenient(false);
        europeWinter.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        europeWinter.set(2013, 0, 1, 13, 0, 0);
        europeWinter.set(Calendar.MILLISECOND, 0);

        // Test daylight savings.
        final GregorianCalendar europeSummer = new GregorianCalendar();
        europeSummer.setLenient(false);
        europeSummer.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        europeSummer.set(2013, 5, 1, 13, 0, 0);
        europeSummer.set(Calendar.MILLISECOND, 0);

        return new Object[][] { { europeWinter }, { europeSummer } };
    }

    @Test(dataProvider = "calendars")
    public void fromToCalendary(final Calendar calendar) {
        final String s1 = GeneralizedTime.valueOf(calendar).toString();
        final Calendar reparsed1 = GeneralizedTime.valueOf(s1).toCalendar();
        assertThat(reparsed1.getTimeInMillis()).isEqualTo(calendar.getTimeInMillis());
    }

    @DataProvider
    public Object[][] invalidStrings() {
        return new Object[][] { {"20060906135030+3359" }, { "20060906135030+2389" },
            { "20060906135030+2361" }, { "20060906135030+" }, { "20060906135030+0" },
            { "20060906135030+010" }, { "20061200235959Z" }, { "2006121a235959Z" },
            { "2006122a235959Z" }, { "20060031235959Z" }, { "20061331235959Z" },
            { "20062231235959Z" }, { "20061232235959Z" }, { "2006123123595aZ" },
            { "200a1231235959Z" }, { "2006j231235959Z" }, { "200612-1235959Z" },
            { "20061231#35959Z" }, { "2006" }, };
    }

    @Test
    public void testCompareEquals() {
        final GeneralizedTime gt1 = GeneralizedTime.valueOf("20060906135030+01");
        final GeneralizedTime gt2 = GeneralizedTime.valueOf("20060906125030Z");
        assertThat(gt1.compareTo(gt2)).isEqualTo(0);
    }

    @Test
    public void testCompareGreaterThan() {
        final GeneralizedTime gt1 = GeneralizedTime.valueOf("20060906135030Z");
        final GeneralizedTime gt2 = GeneralizedTime.valueOf("20060906135030+01");
        assertThat(gt1.compareTo(gt2) > 0).isTrue();
    }

    @Test
    public void testCompareLessThan() {
        final GeneralizedTime gt1 = GeneralizedTime.valueOf("20060906135030+01");
        final GeneralizedTime gt2 = GeneralizedTime.valueOf("20060906135030Z");
        assertThat(gt1.compareTo(gt2) < 0).isTrue();
    }

    @Test
    public void testEqualsFalse() {
        final GeneralizedTime gt1 = GeneralizedTime.valueOf("20060906135030Z");
        final GeneralizedTime gt2 = GeneralizedTime.valueOf("20060906135030+01");
        assertThat(gt1).isNotEqualTo(gt2);
    }

    @Test
    public void testEqualsTrue() {
        final GeneralizedTime gt1 = GeneralizedTime.valueOf("20060906135030+01");
        final GeneralizedTime gt2 = GeneralizedTime.valueOf("20060906125030Z");
        assertThat(gt1).isEqualTo(gt2);
    }

    @Test
    public void testValueOfCalendar() {
        final Calendar calendar = Calendar.getInstance();
        final GeneralizedTime time = GeneralizedTime.valueOf(calendar);
        assertThat(time.getTimeInMillis()).isEqualTo(calendar.getTimeInMillis());
        assertThat(time.toCalendar()).isEqualTo(calendar);
        assertThat(time.toDate()).isEqualTo(calendar.getTime());
    }

    @Test
    public void testValueOfDate() {
        final Date date = new Date();
        final GeneralizedTime time = GeneralizedTime.valueOf(date);
        assertThat(time.getTimeInMillis()).isEqualTo(date.getTime());
        assertThat(time.toDate()).isEqualTo(date);
    }

    @Test(expectedExceptions = { LocalizedIllegalArgumentException.class },
            dataProvider = "invalidStrings")
    public void testValueOfInvalidString(final String s) {
        GeneralizedTime.valueOf(s);
    }

    @Test
    public void testValueOfLong() {
        final Date date = new Date();
        final GeneralizedTime time = GeneralizedTime.valueOf(date.getTime());
        assertThat(time.getTimeInMillis()).isEqualTo(date.getTime());
        assertThat(time.toDate()).isEqualTo(date);
    }

    @Test(dataProvider = "validStrings")
    public void testValueOfValidString(final String s) {
        assertThat(GeneralizedTime.valueOf(s).toString()).isEqualTo(s);
    }

    @DataProvider
    public Object[][] validStrings() {
        return new Object[][] { { "2006090613Z" }, { "20060906135030+01" }, { "200609061350Z" },
            { "20060906135030Z" }, { "20061116135030Z" }, { "20061126135030Z" },
            { "20061231235959Z" }, { "20060906135030+0101" }, { "20060906135030+2359" }, };
    }

    /**
     * Create data for toString test.
     *
     * @return Returns test data.
     */
    @DataProvider()
    public Object[][] createToStringData() {
        return new Object[][] {
            // Note that Calendar months run from 0-11,
            // and that there was no such year as year 0 (1 BC -> 1 AD).
            {   1,  0,  1,  0,  0,  0,   0, "00010101000000Z"},
            {   9,  0,  1,  0,  0,  0,   0, "00090101000000Z"},
            {  10,  0,  1,  0,  0,  0,   0, "00100101000000Z"},
            {  99,  0,  1,  0,  0,  0,   0, "00990101000000Z"},
            { 100,  0,  1,  0,  0,  0,   0, "01000101000000Z"},
            { 999,  0,  1,  0,  0,  0,   0, "09990101000000Z"},
            {1000,  0,  1,  0,  0,  0,   0, "10000101000000Z"},
            {2000,  0,  1,  0,  0,  0,   0, "20000101000000Z"},
            {2099,  0,  1,  0,  0,  0,   0, "20990101000000Z"},
            {2000,  8,  1,  0,  0,  0,   0, "20000901000000Z"},
            {2000,  9,  1,  0,  0,  0,   0, "20001001000000Z"},
            {2000, 10,  1,  0,  0,  0,   0, "20001101000000Z"},
            {2000, 11,  1,  0,  0,  0,   0, "20001201000000Z"},
            {2000,  0,  9,  0,  0,  0,   0, "20000109000000Z"},
            {2000,  0, 10,  0,  0,  0,   0, "20000110000000Z"},
            {2000,  0, 19,  0,  0,  0,   0, "20000119000000Z"},
            {2000,  0, 20,  0,  0,  0,   0, "20000120000000Z"},
            {2000,  0, 29,  0,  0,  0,   0, "20000129000000Z"},
            {2000,  0, 30,  0,  0,  0,   0, "20000130000000Z"},
            {2000,  0, 31,  0,  0,  0,   0, "20000131000000Z"},
            {2000,  0,  1,  9,  0,  0,   0, "20000101090000Z"},
            {2000,  0,  1, 10,  0,  0,   0, "20000101100000Z"},
            {2000,  0,  1, 19,  0,  0,   0, "20000101190000Z"},
            {2000,  0,  1, 20,  0,  0,   0, "20000101200000Z"},
            {2000,  0,  1, 23,  0,  0,   0, "20000101230000Z"},
            {2000,  0,  1,  0,  9,  0,   0, "20000101000900Z"},
            {2000,  0,  1,  0, 10,  0,   0, "20000101001000Z"},
            {2000,  0,  1,  0, 59,  0,   0, "20000101005900Z"},
            {2000,  0,  1,  0,  0,  9,   0, "20000101000009Z"},
            {2000,  0,  1,  0,  0, 10,   0, "20000101000010Z"},
            {2000,  0,  1,  0,  0, 59,   0, "20000101000059Z"},
            {2000,  0,  1,  0,  0,  0,   9, "20000101000000.009Z"},
            {2000,  0,  1,  0,  0,  0,  10, "20000101000000.010Z"},
            {2000,  0,  1,  0,  0,  0,  99, "20000101000000.099Z"},
            {2000,  0,  1,  0,  0,  0, 100, "20000101000000.100Z"},
            {2000,  0,  1,  0,  0,  0, 999, "20000101000000.999Z"},
        };
    }

    private static final String TIME_ZONE_UTC = "UTC";


    @Test(dataProvider = "createToStringData")
    public void testToString(int yyyy, int month, int dd, int hour, int mm, int ss, int millis, String expected)
            throws Exception {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(TIME_ZONE_UTC));
        calendar.set(yyyy, month, dd, hour, mm, ss);
        calendar.set(Calendar.MILLISECOND, millis);

        long time = calendar.getTimeInMillis();
        // test creation with long only if it is positive because negative values will be rejected
        if (time > 0) {
            assertThat(GeneralizedTime.valueOf(time).toString()).isEqualTo(expected);
        }

        Date date = new Date(time);
        assertThat(GeneralizedTime.valueOf(date).toString()).isEqualTo(expected);
    }

}
