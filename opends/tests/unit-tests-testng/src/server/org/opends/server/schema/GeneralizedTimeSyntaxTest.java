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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import static org.opends.server.util.ServerConstants.*;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.opends.server.api.AttributeSyntax;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the GeneralizedTimeSyntax.
 */
public class GeneralizedTimeSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  protected AttributeSyntax getRule()
  {
    return new GeneralizedTimeSyntax();
  }

  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"2006090613Z", true},
        {"20060906135030+01", true},
        {"200609061350Z", true},
        {"20060906135030Z", true},
        {"20061116135030Z", true},
        {"20061126135030Z", true},
        {"20061231235959Z", true},
        {"20060906135030+0101", true},
        {"20060906135030+2359", true},
        {"20060906135030+3359", false},
        {"20060906135030+2389", false},
        {"20060906135030+2361", false},
        {"20060906135030+", false},
        {"20060906135030+0", false},
        {"20060906135030+010", false},
        {"20061200235959Z", false},
        {"2006121a235959Z", false},
        {"2006122a235959Z", false},
        {"20060031235959Z", false},
        {"20061331235959Z", false},
        {"20062231235959Z", false},
        {"20061232235959Z", false},
        {"2006123123595aZ", false},
        {"200a1231235959Z", false},
        {"2006j231235959Z", false},
        {"200612-1235959Z", false},
        {"20061231#35959Z", false},
        {"2006", false},
    };
  }



  /**
   * Create data for format(...) tests.
   *
   * @return Returns test data.
   */
  @DataProvider(name="createFormatData")
  public Object[][] createFormatData()
  {
    return new Object [][] {
        // Note that Calendar months run from 0-11,
        // and that there was no such year as year 0 (1 BC -> 1 AD).
        {   1,  0,  1,  0,  0,  0,   0, "00010101000000.000Z"},
        {   9,  0,  1,  0,  0,  0,   0, "00090101000000.000Z"},
        {  10,  0,  1,  0,  0,  0,   0, "00100101000000.000Z"},
        {  99,  0,  1,  0,  0,  0,   0, "00990101000000.000Z"},
        { 100,  0,  1,  0,  0,  0,   0, "01000101000000.000Z"},
        { 999,  0,  1,  0,  0,  0,   0, "09990101000000.000Z"},
        {1000,  0,  1,  0,  0,  0,   0, "10000101000000.000Z"},
        {2000,  0,  1,  0,  0,  0,   0, "20000101000000.000Z"},
        {2099,  0,  1,  0,  0,  0,   0, "20990101000000.000Z"},
        {2000,  8,  1,  0,  0,  0,   0, "20000901000000.000Z"},
        {2000,  9,  1,  0,  0,  0,   0, "20001001000000.000Z"},
        {2000, 10,  1,  0,  0,  0,   0, "20001101000000.000Z"},
        {2000, 11,  1,  0,  0,  0,   0, "20001201000000.000Z"},
        {2000,  0,  9,  0,  0,  0,   0, "20000109000000.000Z"},
        {2000,  0, 10,  0,  0,  0,   0, "20000110000000.000Z"},
        {2000,  0, 19,  0,  0,  0,   0, "20000119000000.000Z"},
        {2000,  0, 20,  0,  0,  0,   0, "20000120000000.000Z"},
        {2000,  0, 29,  0,  0,  0,   0, "20000129000000.000Z"},
        {2000,  0, 30,  0,  0,  0,   0, "20000130000000.000Z"},
        {2000,  0, 31,  0,  0,  0,   0, "20000131000000.000Z"},
        {2000,  0,  1,  9,  0,  0,   0, "20000101090000.000Z"},
        {2000,  0,  1, 10,  0,  0,   0, "20000101100000.000Z"},
        {2000,  0,  1, 19,  0,  0,   0, "20000101190000.000Z"},
        {2000,  0,  1, 20,  0,  0,   0, "20000101200000.000Z"},
        {2000,  0,  1, 23,  0,  0,   0, "20000101230000.000Z"},
        {2000,  0,  1,  0,  9,  0,   0, "20000101000900.000Z"},
        {2000,  0,  1,  0, 10,  0,   0, "20000101001000.000Z"},
        {2000,  0,  1,  0, 59,  0,   0, "20000101005900.000Z"},
        {2000,  0,  1,  0,  0,  9,   0, "20000101000009.000Z"},
        {2000,  0,  1,  0,  0, 10,   0, "20000101000010.000Z"},
        {2000,  0,  1,  0,  0, 59,   0, "20000101000059.000Z"},
        {2000,  0,  1,  0,  0,  0,   9, "20000101000000.009Z"},
        {2000,  0,  1,  0,  0,  0,  10, "20000101000000.010Z"},
        {2000,  0,  1,  0,  0,  0,  99, "20000101000000.099Z"},
        {2000,  0,  1,  0,  0,  0, 100, "20000101000000.100Z"},
        {2000,  0,  1,  0,  0,  0, 999, "20000101000000.999Z"},
    };
  }



  /**
   * Tests {@link GeneralizedTimeSyntax#format(long)}.
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
  @Test(dataProvider="createFormatData")
  public void testFormatLong(int yyyy, int MM, int dd, int HH, int mm,
      int ss, int SSS, String expected) throws Exception
  {
    Calendar calendar =
        new GregorianCalendar(TimeZone.getTimeZone(TIME_ZONE_UTC));
    calendar.set(yyyy, MM, dd, HH, mm, ss);
    calendar.set(Calendar.MILLISECOND, SSS);
    long time = calendar.getTimeInMillis();
    String actual = GeneralizedTimeSyntax.format(time);
    Assert.assertEquals(actual, expected);
  }



  /**
   * Tests {@link GeneralizedTimeSyntax#format(Date)}.
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
  @Test(dataProvider="createFormatData")
  public void testFormatDate(int yyyy, int MM, int dd, int HH, int mm,
      int ss, int SSS, String expected) throws Exception
  {
    Calendar calendar =
        new GregorianCalendar(TimeZone.getTimeZone(TIME_ZONE_UTC));
    calendar.set(yyyy, MM, dd, HH, mm, ss);
    calendar.set(Calendar.MILLISECOND, SSS);
    Date time = new Date(calendar.getTimeInMillis());
    String actual = GeneralizedTimeSyntax.format(time);
    Assert.assertEquals(actual, expected);
  }
}
