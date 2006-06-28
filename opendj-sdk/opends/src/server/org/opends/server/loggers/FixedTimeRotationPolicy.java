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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.util.TimeThread;

import static org.opends.server.loggers.Debug.*;

/**
 * This class implements a rotation policy based on fixed
 * day/time of day.
 */
public class FixedTimeRotationPolicy implements RotationPolicy
{
  private static final String CLASS_NAME =
    "org.opends.server.loggers.FixedTimeRotationPolicy";

  private static final long NEXT_DAY = 24 * 3600 * 1000;

  private long[] rotationTimes;
  private long nextRotationTime = 0;

  /**
  * Time in HHmm format. Will be calculated as (hourOfDay*100) +
  * minuteOfHour.
  *
  * @param  timeOfDays  The times at which log rotation should occur.
  */

  public FixedTimeRotationPolicy(int[] timeOfDays)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(timeOfDays));

    Calendar cal = new GregorianCalendar();
    cal.set( Calendar.MILLISECOND, 0 );
    cal.set( Calendar.SECOND, 0 );
    cal.set( Calendar.MINUTE, 0 );
    cal.set( Calendar.HOUR_OF_DAY, 0 );
    long timeFromStartOfDay = cal.getTime().getTime();

    rotationTimes = new long[timeOfDays.length];

    for(int i = 0; i < timeOfDays.length; i++)
    {
      int hour = timeOfDays[i]/100;
      int min = timeOfDays[i] - hour*100;

      rotationTimes[i] = timeFromStartOfDay + hour*3600*1000 + min*60*1000;
    }

    long currTime = TimeThread.getTime();

    nextRotationTime = getNextRotationTime(currTime, 0);

  }

  /**
   * This method indicates if the log file should be
   * rotated or not.
   *
   * @return true if the file needs to be rotated, false otherwise.
   */
  public boolean rotateFile()
  {
    assert debugEnter(CLASS_NAME, "rotateFile");

    long currTime = TimeThread.getTime();
    assert debugMessage(DebugLogCategory.CORE_SERVER,
                        DebugLogSeverity.INFO, CLASS_NAME, "rotateFile",
                        "Rotation at fixed time:" + currTime +
                        " nextRotationTime:" + nextRotationTime);

    if(currTime > nextRotationTime)
    {
      nextRotationTime = getNextRotationTime(currTime, nextRotationTime);
      //System.out.println("Setting next rotation time to:" + nextRotationTime);
      return true;
    }
    return false;
  }

  /**
   * Get the next rotation time.
   *
   * @param  currTime          The current time.
   * @param  currRotationTime  The time we currently believe should be the next
   *                           rotation time.
   *
   * @return  The time that should be used for the next log file rotation.
   */
  private long getNextRotationTime(long currTime, long currRotationTime)
  {
    long prevRotationTime = currRotationTime;
    for(int j = 0; j < rotationTimes.length; j++)
    {
      if (currTime < rotationTimes[j])
      {
        currRotationTime = rotationTimes[j];
        break;
      }
    }

    if(currRotationTime == prevRotationTime)
    {
      for(int k = 0; k < rotationTimes.length; k++)
      {
        rotationTimes[k] += NEXT_DAY;
      }
      currRotationTime = rotationTimes[0];
    }

    return currRotationTime;
  }

}

