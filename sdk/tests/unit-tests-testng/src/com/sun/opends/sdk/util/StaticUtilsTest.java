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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.util;



import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.opends.sdk.OpenDSTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Test {@code StaticUtils}.
 */
@Test(groups = { "precommit", "types", "sdk" }, sequential = true)
public final class StaticUtilsTest extends OpenDSTestCase
{
  @DataProvider(name = "dataForToLowerCase")
  public Object[][] dataForToLowerCase()
  {
    // Value, toLowerCase or null if identity
    return new Object[][] {
        { "", null },
        { " ", null },
        { "   ", null },
        { "12345", null },
        {
            "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./",
            null },
        {
            "Aabcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./",
            "aabcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./" },
        {
            "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./A",
            "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./a" },
        { "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz" },
        { "\u00c7edilla", "\u00e7edilla" },
        { "ced\u00cdlla", "ced\u00edlla" }, };
  }



  @Test(dataProvider = "dataForToLowerCase")
  public void testToLowerCaseString(String s, String expected)
  {
    String actual = StaticUtils.toLowerCase(s);
    if (expected == null)
    {
      Assert.assertSame(actual, s);
    }
    else
    {
      Assert.assertEquals(actual, expected);
    }
  }



  @Test(dataProvider = "dataForToLowerCase")
  public void testToLowerCaseStringBuilder(String s, String expected)
  {
    StringBuilder builder = new StringBuilder();
    String actual = StaticUtils.toLowerCase(s, builder).toString();
    if (expected == null)
    {
      Assert.assertEquals(actual, s);
    }
    else
    {
      Assert.assertEquals(actual, expected);
    }
  }



  /**
   * Create data for format(...) tests.
   *
   * @return Returns test data.
   */
  @DataProvider(name = "createFormatData")
  public Object[][] createFormatData()
  {
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



  /**
   * Tests {@link StaticUtils#formatAsGeneralizedTime(long)} .
   *
   * @param yyyy
   *          The year.
   * @param MM
   *          The month.
   * @param dd
   *          The day.
   * @param HH
   *          The hour.
   * @param mm
   *          The minute.
   * @param ss
   *          The second.
   * @param SSS
   *          The milli-seconds.
   * @param expected
   *          The expected generalized time formatted string.
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test(dataProvider = "createFormatData")
  public void testFormatLong(int yyyy, int MM, int dd, int HH, int mm,
      int ss, int SSS, String expected) throws Exception
  {
    Calendar calendar = new GregorianCalendar(TimeZone
        .getTimeZone("UTC"));
    calendar.set(yyyy, MM, dd, HH, mm, ss);
    calendar.set(Calendar.MILLISECOND, SSS);
    long time = calendar.getTimeInMillis();
    String actual = StaticUtils.formatAsGeneralizedTime(time);
    Assert.assertEquals(actual, expected);
  }



  /**
   * Tests {@link GeneralizedTimeSyntax#format(java.util.Date)}.
   *
   * @param yyyy
   *          The year.
   * @param MM
   *          The month.
   * @param dd
   *          The day.
   * @param HH
   *          The hour.
   * @param mm
   *          The minute.
   * @param ss
   *          The second.
   * @param SSS
   *          The milli-seconds.
   * @param expected
   *          The expected generalized time formatted string.
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test(dataProvider = "createFormatData")
  public void testFormatDate(int yyyy, int MM, int dd, int HH, int mm,
      int ss, int SSS, String expected) throws Exception
  {
    Calendar calendar = new GregorianCalendar(TimeZone
        .getTimeZone("UTC"));
    calendar.set(yyyy, MM, dd, HH, mm, ss);
    calendar.set(Calendar.MILLISECOND, SSS);
    Date time = new Date(calendar.getTimeInMillis());
    String actual = StaticUtils.formatAsGeneralizedTime(time);
    Assert.assertEquals(actual, expected);
  }
}
