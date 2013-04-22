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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;

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
        return new Object[][] { { "20060906135030+3359" }, { "20060906135030+2389" },
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

}
