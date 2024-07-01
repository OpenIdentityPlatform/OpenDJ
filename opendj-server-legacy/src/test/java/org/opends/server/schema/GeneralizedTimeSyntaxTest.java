/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.util.ServerConstants.TIME_ZONE_UTC;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.opends.server.DirectoryServerTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the GeneralizedTimeSyntax.
 */
public class GeneralizedTimeSyntaxTest extends DirectoryServerTestCase
{
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
