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
import org.opends.messages.Message;

import org.opends.server.util.TimeThread;
import org.opends.server.admin.std.server.TimeLimitLogRotationPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;


import java.util.List;
import java.util.ArrayList;

/**
 * This class implements a fixed time based rotation policy.
 * Rotation will happen N seconds since the last rotation.
 */
public class TimeLimitRotationPolicy implements
    RotationPolicy<TimeLimitLogRotationPolicyCfg>,
    ConfigurationChangeListener<TimeLimitLogRotationPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private long timeInterval = 0;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRotationPolicy(TimeLimitLogRotationPolicyCfg config)
  {
    timeInterval = config.getRotationInterval();

    config.addTimeLimitChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      TimeLimitLogRotationPolicyCfg config, List<Message> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      TimeLimitLogRotationPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    timeInterval = config.getRotationInterval();

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }


  /**
   * This method indicates if the log file should be
   * rotated or not.
   *
   * @param writer The mutli file text writer written the log file.
   * @return true if the file should be rotated, false otherwise.
   */
  public boolean rotateFile(MultifileTextWriter writer)
  {
    long currTime = TimeThread.getTime();
    long currInterval = currTime - writer.getLastRotationTime();

    if (debugEnabled())
    {
      TRACER.debugInfo("Last rotation occurred %ds ago. " +
          "Next rotation in %ds", currInterval / 1000,
                                   (timeInterval - currInterval)/1000);
    }

    return currInterval > timeInterval;
  }

}

