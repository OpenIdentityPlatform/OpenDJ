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
package org.opends.server.loggers;

import java.util.*;

import org.opends.server.util.TimeThread;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.admin.std.server.FixedTimeLogRotationPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;

/**
 * This class implements a rotation policy based on fixed
 * day/time of day.
 */
public class FixedTimeRotationPolicy implements
    RotationPolicy<FixedTimeLogRotationPolicyCfg>,
    ConfigurationChangeListener<FixedTimeLogRotationPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  private static final long MS_IN_DAY = 24 * 3600 * 1000;

  // The scheduled rotation times as ms offsets from the beginnging of the day.
  private long[] rotationTimes;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRotationPolicy(FixedTimeLogRotationPolicyCfg config)
  {
    rotationTimes = new long[config.getTimeOfDay().size()];

    int i = 0;
    for(String time : config.getTimeOfDay())
    {
      int hour = Integer.valueOf(time)/100;
      int min = Integer.valueOf(time) - hour*100;

      rotationTimes[i++] = hour*3600*1000 + min*60*1000;
    }

    Arrays.sort(rotationTimes);

    config.addFixedTimeChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FixedTimeLogRotationPolicyCfg config, List<String> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      FixedTimeLogRotationPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    rotationTimes = new long[config.getTimeOfDay().size()];

    int i = 0;
    for(String time : config.getTimeOfDay())
    {
      int hour = Integer.valueOf(time)/100;
      int min = Integer.valueOf(time) - hour*100;

      rotationTimes[i++] = hour*3600*1000 + min*60*1000;
    }

    Arrays.sort(rotationTimes);

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean rotateFile(MultifileTextWriter writer)
  {
    long currTime = TimeThread.getTime();
    long lastRotationTime = writer.getLastRotationTime();
    long dayOfLastRotation = MS_IN_DAY * (lastRotationTime / MS_IN_DAY);
    long hourOfLastRotation = lastRotationTime - dayOfLastRotation;

    // Find a scheduled rotation time thats right after the last rotation time.
    long hourOfNextRotation = 0;
    for(long time : rotationTimes)
    {
      if(time > hourOfLastRotation)
      {
        hourOfNextRotation = time;
        break;
      }
    }

    if(hourOfNextRotation <= 0)
    {
      // Rotation alrealy happened after the latest fixed time for that day.
      // Set it the first rotation time for the next day.
      hourOfNextRotation = rotationTimes[0] + MS_IN_DAY;
    }

    long nextRotationTime = dayOfLastRotation + hourOfNextRotation;

    if (debugEnabled())
    {
      TRACER.debugInfo("The next fixed rotation time in %ds",
                       (currTime - nextRotationTime)/1000);
    }

    return currTime > nextRotationTime;

  }
}

