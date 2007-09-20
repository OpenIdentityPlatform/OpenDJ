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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.api.DirectoryThread;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines a thread that will wake up periodically, get the current
 * time, and store various representations of it.  Note that only limited
 * debugging will be performed in this class due to the frequency with which it
 * will be called.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class TimeThread
       extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The singleton instance for this time thread.
   */
  private static final TimeThread threadInstance = new TimeThread();



  // The calendar holding the current time.
  private static GregorianCalendar calendar;

  // A set of arbitrary formatters that should be maintained by this time
  // thread.
  private static CopyOnWriteArrayList<SimpleDateFormat> userDefinedFormatters;

  // A set of abitrary formatted times, mapped from format string to the
  // corresponding formatted time representation.
  private static ConcurrentHashMap<String,String> userDefinedTimeStrings;

  // The date for this time thread.
  private static Date date;

  // The current time in HHmm form as an integer.
  private static int hourAndMinute;

  // The current time in milliseconds since the epoch.
  private static volatile long time;

  // The date formatter that will be used to obtain the generalized time.
  private static SimpleDateFormat generalizedTimeFormatter;

  // The date formatter that will be used to obtain the local timestamp.
  private static SimpleDateFormat localTimestampFormatter;

  // The date formatter that will be used to obtain the GMT timestamp.
  private static SimpleDateFormat gmtTimestampFormatter;

  // The timestamp for this time thread in the generalized time format.
  private static String generalizedTime;

  // The timestamp for this time thread in the local time zone.
  private static String localTimestamp;

  // The timestamp for this time thread in GMT.
  private static String gmtTimestamp;



  /**
   * Creates a new instance of this time thread and starts it.
   */
  private TimeThread()
  {
    super("Time Thread");

    setDaemon(true);

    userDefinedFormatters  = new CopyOnWriteArrayList<SimpleDateFormat>();
    userDefinedTimeStrings = new ConcurrentHashMap<String,String>();

    TimeZone utcTimeZone = TimeZone.getTimeZone(TIME_ZONE_UTC);

    generalizedTimeFormatter =
         new SimpleDateFormat(DATE_FORMAT_GENERALIZED_TIME);
    generalizedTimeFormatter.setTimeZone(utcTimeZone);

    gmtTimestampFormatter = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    gmtTimestampFormatter.setTimeZone(utcTimeZone);

    localTimestampFormatter = new SimpleDateFormat(DATE_FORMAT_LOCAL_TIME);

    calendar        = new GregorianCalendar();
    date            = calendar.getTime();
    time            = date.getTime();
    generalizedTime = generalizedTimeFormatter.format(date);
    localTimestamp  = localTimestampFormatter.format(date);
    gmtTimestamp    = gmtTimestampFormatter.format(date);
    hourAndMinute   = (calendar.get(Calendar.HOUR_OF_DAY) * 100) +
                      calendar.get(Calendar.MINUTE);

    start();
  }



  /**
   * Operates in a loop, getting the current time and then sleeping briefly
   * before checking again.
   */
  public void run()
  {
    while (true)
    {
      try
      {
        calendar        = new GregorianCalendar();
        date            = calendar.getTime();
        time            = date.getTime();
        generalizedTime = generalizedTimeFormatter.format(date);
        localTimestamp  = localTimestampFormatter.format(date);
        gmtTimestamp    = gmtTimestampFormatter.format(date);
        hourAndMinute   = (calendar.get(Calendar.HOUR_OF_DAY) * 100) +
                          calendar.get(Calendar.MINUTE);

        for (SimpleDateFormat format : userDefinedFormatters)
        {
          userDefinedTimeStrings.put(format.toPattern(),
                                     format.format(date));
        }

        Thread.sleep(200);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Retrieves a <CODE>Calendar</CODE> containing the time at the last update.
   *
   * @return  A <CODE>Calendar</CODE> containing the time at the last update.
   */
  public static Calendar getCalendar()
  {
    return calendar;
  }



  /**
   * Retrieves a <CODE>Date</CODE> containing the time at the last update.
   *
   * @return  A <CODE>Date</CODE> containing the time at the last update.
   */
  public static Date getDate()
  {
    return date;
  }



  /**
   * Retrieves the time in milliseconds since the epoch at the last update.
   *
   * @return  The time in milliseconds since the epoch at the last update.
   */
  public static long getTime()
  {
    return time;
  }



  /**
   * Retrieves a string containing a normalized representation of the current
   * time in a generalized time format.  The timestamp will look like
   * "20050101000000.000Z".
   *
   * @return  A string containing a normalized representation of the current
   *          time in a generalized time format.
   */
  public static String getGeneralizedTime()
  {
    return generalizedTime;
  }



  /**
   * Retrieves a string containing the current time in the local time zone.  The
   * timestamp format will look like "01/Jan/2005:00:00:00 -0600".
   *
   * @return  A string containing the current time in the local time zone.
   */
  public static String getLocalTime()
  {
    return localTimestamp;
  }



  /**
   * Retrieves a string containing the current time in GMT.  The timestamp will
   * look like "20050101000000Z".
   *
   * @return  A string containing the current time in GMT.
   */
  public static String getGMTTime()
  {
    return gmtTimestamp;
  }



  /**
   * Retrieves an integer containing the time in HHmm format at the last
   * update.  It will be calculated as "(hourOfDay*100) + minuteOfHour".
   *
   * @return  An integer containing the time in HHmm format at the last update.
   */
  public static int getHourAndMinute()
  {
    return hourAndMinute;
  }



  /**
   * Retrieves the current time formatted using the given format string.  The
   * first time this method is used with a given format string, it will be used
   * to create a formatter that will generate the time string.  That formatter
   * will then be put into a list so that it will be maintained automatically
   * for future use.
   *
   * @param  formatString  The string that defines the format of the time string
   *                       to retrieve.
   *
   * @return  The formatted time string.
   *
   * @throws  IllegalArgumentException  If the provided format string is
   *                                    invalid.
   */
  public static String getUserDefinedTime(String formatString)
         throws IllegalArgumentException
  {
    String timeString = userDefinedTimeStrings.get(formatString);

    if (timeString == null)
    {
      SimpleDateFormat formatter = new SimpleDateFormat(formatString);
      timeString = formatter.format(date);
      userDefinedTimeStrings.put(formatString, timeString);
      userDefinedFormatters.add(formatter);
    }

    return timeString;
  }



  /**
   * Removes the user-defined time formatter from this time thread so that it
   * will no longer be maintained.  This is a safe operation because if the
   * same format string is used for multiple purposes then it will be added back
   * the next time a time is requested with the given format.
   *
   * @param  formatString  The format string for the date formatter to remove.
   */
  public static void removeUserDefinedFormatter(String formatString)
  {
    Iterator<SimpleDateFormat> iterator = userDefinedFormatters.iterator();
    while (iterator.hasNext())
    {
      SimpleDateFormat format = iterator.next();
      if (format.toPattern().equals(formatString))
      {
        iterator.remove();
      }
    }

    userDefinedTimeStrings.remove(formatString);
  }
}

