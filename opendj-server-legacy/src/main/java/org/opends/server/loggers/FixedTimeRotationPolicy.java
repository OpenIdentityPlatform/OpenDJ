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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.FixedTimeLogRotationPolicyCfg;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.util.TimeThread;

/** This class implements a rotation policy based on fixed day/time of day. */
public class FixedTimeRotationPolicy implements
    RotationPolicy<FixedTimeLogRotationPolicyCfg>,
    ConfigurationChangeListener<FixedTimeLogRotationPolicyCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The scheduled rotation times as ms offsets from the beginning of the day. */
  private int[] rotationTimes;

  @Override
  public void initializeLogRotationPolicy(FixedTimeLogRotationPolicyCfg config)
  {
    rotationTimes = new int[config.getTimeOfDay().size()];

    int i = 0;
    for(String time : config.getTimeOfDay())
    {
      rotationTimes[i++] = Integer.valueOf(time);
    }

    Arrays.sort(rotationTimes);

    config.addFixedTimeChangeListener(this);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      FixedTimeLogRotationPolicyCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      FixedTimeLogRotationPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    rotationTimes = new int[config.getTimeOfDay().size()];

    int i = 0;
    for(String time : config.getTimeOfDay())
    {
      rotationTimes[i++] = Integer.valueOf(time);
    }

    Arrays.sort(rotationTimes);

    return ccr;
  }

  @Override
  public boolean rotateFile(RotatableLogFile writer)
  {
    Calendar lastRotationTime = writer.getLastRotationTime();

    Calendar nextRotationTime = (Calendar)lastRotationTime.clone();
    int i = 0;
    nextRotationTime.set(Calendar.HOUR_OF_DAY, rotationTimes[i] / 100);
    nextRotationTime.set(Calendar.MINUTE, rotationTimes[i] % 100);
    nextRotationTime.set(Calendar.SECOND, 0);
    while(lastRotationTime.after(nextRotationTime))
    {
      if(i == rotationTimes.length - 1)
      {
        nextRotationTime.add(Calendar.DATE, 1);
        i = 0;
      }
      else
      {
        i++;
      }

      nextRotationTime.set(Calendar.HOUR_OF_DAY, rotationTimes[i] / 100);
      nextRotationTime.set(Calendar.MINUTE, rotationTimes[i] % 100);
    }

    logger.trace("The next fixed rotation time is %s", rotationTimes[i]);

    return TimeThread.getCalendar().after(nextRotationTime);
  }
}

