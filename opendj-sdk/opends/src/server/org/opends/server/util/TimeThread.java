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
package org.opends.server.util;



import static org.opends.server.loggers.debug.DebugLogger.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.DebugLogLevel;



/**
 * This class provides an application-wide timing service. It provides
 * the ability to retrieve the current time in various different formats
 * and resolutions.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = false,
    mayExtend = false,
    mayInvoke = true)
public final class TimeThread
{

  /**
   * Timer job.
   */
  private static final class TimeInfo implements Runnable
  {

    // The calendar holding the current time.
    private GregorianCalendar calendar;

    // The date for this time thread.
    private Date date;

    // The timestamp for this time thread in the generalized time
    // format.
    private String generalizedTime;

    // The timestamp for this time thread in GMT.
    private String gmtTimestamp;

    // The date formatter that will be used to obtain the GMT
    // timestamp.
    private final SimpleDateFormat gmtTimestampFormatter;

    // The current time in HHmm form as an integer.
    private int hourAndMinute;

    // The timestamp for this time thread in the local time zone.
    private String localTimestamp;

    // The date formatter that will be used to obtain the local
    // timestamp.
    private final SimpleDateFormat localTimestampFormatter;

    // The current time in nanoseconds.
    private volatile long nanoTime;

    // The current time in milliseconds since the epoch.
    private volatile long time;

    // A set of arbitrary formatters that should be maintained by this
    // time thread.
    private final List<SimpleDateFormat> userDefinedFormatters;

    // A set of abitrary formatted times, mapped from format string to
    // the corresponding formatted time representation.
    private final Map<String, String> userDefinedTimeStrings;



    /**
     * Create a new job with the specified delay.
     */
    public TimeInfo()
    {
      userDefinedFormatters =
          new CopyOnWriteArrayList<SimpleDateFormat>();
      userDefinedTimeStrings = new ConcurrentHashMap<String, String>();

      TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

      gmtTimestampFormatter = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
      gmtTimestampFormatter.setTimeZone(utcTimeZone);

      localTimestampFormatter =
          new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

      // Populate initial values.
      run();
    }



    /**
     * {@inheritDoc}
     */
    public void run()
    {
      try
      {
        calendar = new GregorianCalendar();
        date = calendar.getTime();
        time = date.getTime();
        nanoTime = System.nanoTime();
        generalizedTime = GeneralizedTimeSyntax.format(date);
        localTimestamp = localTimestampFormatter.format(date);
        gmtTimestamp = gmtTimestampFormatter.format(date);
        hourAndMinute =
            calendar.get(Calendar.HOUR_OF_DAY) * 100
                + calendar.get(Calendar.MINUTE);

        for (SimpleDateFormat format : userDefinedFormatters)
        {
          userDefinedTimeStrings.put(format.toPattern(), format
              .format(date));
        }
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
   * Thread factory used by the scheduled execution service.
   */
  private static final class TimeThreadFactory implements
      ThreadFactory
  {

    /**
     * {@inheritDoc}
     */
    public Thread newThread(Runnable r)
    {
      Thread t = new DirectoryThread(r, "Time Thread");
      t.setDaemon(true);
      return t;
    }

  }



  // The singleton instance.
  private static TimeThread INSTANCE = new TimeThread();

  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();



  /**
   * Retrieves a <CODE>Calendar</CODE> containing the time at the last
   * update.
   *
   * @return A <CODE>Calendar</CODE> containing the time at the last
   *         update.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static Calendar getCalendar() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.calendar;
  }



  /**
   * Retrieves a <CODE>Date</CODE> containing the time at the last
   * update.
   *
   * @return A <CODE>Date</CODE> containing the time at the last update.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static Date getDate() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.date;
  }



  /**
   * Retrieves a string containing a normalized representation of the
   * current time in a generalized time format. The timestamp will look
   * like "20050101000000.000Z".
   *
   * @return A string containing a normalized representation of the
   *         current time in a generalized time format.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static String getGeneralizedTime() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.generalizedTime;
  }



  /**
   * Retrieves a string containing the current time in GMT. The
   * timestamp will look like "20050101000000Z".
   *
   * @return A string containing the current time in GMT.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static String getGMTTime() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.gmtTimestamp;
  }



  /**
   * Retrieves an integer containing the time in HHmm format at the last
   * update. It will be calculated as "(hourOfDay*100) + minuteOfHour".
   *
   * @return An integer containing the time in HHmm format at the last
   *         update.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static int getHourAndMinute() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.hourAndMinute;
  }



  /**
   * Retrieves a string containing the current time in the local time
   * zone. The timestamp format will look like
   * "01/Jan/2005:00:00:00 -0600".
   *
   * @return A string containing the current time in the local time
   *         zone.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static String getLocalTime() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.localTimestamp;
  }



  /**
   * Retrieves the time in nanoseconds from the most precise available
   * system timer. The value retured represents nanoseconds since some
   * fixed but arbitrary time.
   *
   * @return The time in nanoseconds from some fixed but arbitrary time.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static long getNanoTime() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.nanoTime;
  }



  /**
   * Retrieves the time in milliseconds since the epoch at the last
   * update.
   *
   * @return The time in milliseconds since the epoch at the last
   *         update.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static long getTime() throws IllegalStateException
  {
    checkState();
    return INSTANCE.timeInfo.time;
  }



  /**
   * Retrieves the current time formatted using the given format string.
   * <p>
   * The first time this method is used with a given format string, it
   * will be used to create a formatter that will generate the time
   * string. That formatter will then be put into a list so that it will
   * be maintained automatically for future use.
   *
   * @param formatString
   *          The string that defines the format of the time string to
   *          retrieve.
   * @return The formatted time string.
   * @throws IllegalArgumentException
   *           If the provided format string is invalid.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static String getUserDefinedTime(String formatString)
      throws IllegalArgumentException, IllegalStateException
  {
    checkState();

    String timeString =
        INSTANCE.timeInfo.userDefinedTimeStrings.get(formatString);

    if (timeString == null)
    {
      SimpleDateFormat formatter = new SimpleDateFormat(formatString);
      timeString = formatter.format(INSTANCE.timeInfo.date);
      INSTANCE.timeInfo.userDefinedTimeStrings.put(formatString,
          timeString);
      INSTANCE.timeInfo.userDefinedFormatters.add(formatter);
    }

    return timeString;
  }



  /**
   * Removes the user-defined time formatter from this time thread so
   * that it will no longer be maintained. This is a safe operation
   * because if the same format string is used for multiple purposes
   * then it will be added back the next time a time is requested with
   * the given format.
   *
   * @param formatString
   *          The format string for the date formatter to remove.
   * @throws IllegalStateException
   *           If the time service has not been started.
   */
  public static void removeUserDefinedFormatter(String formatString)
    throws IllegalStateException
  {
    checkState();

    Iterator<SimpleDateFormat> iterator =
        INSTANCE.timeInfo.userDefinedFormatters.iterator();
    while (iterator.hasNext())
    {
      SimpleDateFormat format = iterator.next();
      if (format.toPattern().equals(formatString))
      {
        iterator.remove();
      }
    }

    INSTANCE.timeInfo.userDefinedTimeStrings.remove(formatString);
  }



  /**
   * Starts the time service if it has not already been started.
   */
  public static void start()
  {
    if (INSTANCE == null)
    {
      INSTANCE = new TimeThread();
    }
  }



  /**
   * Stops the time service if it has not already been stopped.
   */
  public static void stop()
  {
    if (INSTANCE != null)
    {
      INSTANCE.scheduler.shutdown();
      INSTANCE = null;
    }
  }



  // Ensures that the time service has been started.
  private static void checkState() throws IllegalStateException
  {
    if (INSTANCE == null)
    {
      throw new IllegalStateException("Time service not started");
    }
  }



  // The scheduler.
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(new TimeThreadFactory());

  // The current time information.
  private final TimeInfo timeInfo = new TimeInfo();



  /**
   * Creates a new instance of this time service and starts it.
   */
  private TimeThread()
  {
    this.scheduler.scheduleWithFixedDelay(timeInfo, 0, 200,
        TimeUnit.MILLISECONDS);
  }
}
