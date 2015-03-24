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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.loggers;
import org.forgerock.i18n.LocalizableMessage;

import org.opends.server.util.TimeThread;
import org.opends.server.admin.std.server.TimeLimitLogRotationPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import java.util.List;

/**
 * This class implements a fixed time based rotation policy.
 * Rotation will happen N seconds since the last rotation.
 */
public class TimeLimitRotationPolicy implements
    RotationPolicy<TimeLimitLogRotationPolicyCfg>,
    ConfigurationChangeListener<TimeLimitLogRotationPolicyCfg>
{
  private long timeInterval;

  /** {@inheritDoc} */
  public void initializeLogRotationPolicy(TimeLimitLogRotationPolicyCfg config)
  {
    timeInterval = config.getRotationInterval();

    config.addTimeLimitChangeListener(this);
  }

  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
      TimeLimitLogRotationPolicyCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
      TimeLimitLogRotationPolicyCfg config)
  {
    timeInterval = config.getRotationInterval();
    return new ConfigChangeResult();
  }


  /**
   * This method indicates if the log file should be
   * rotated or not.
   *
   * @param writer The multi file text writer written the log file.
   * @return true if the file should be rotated, false otherwise.
   */
  public boolean rotateFile(RotatableLogFile writer)
  {
    long currInterval = TimeThread.getTime() -
        writer.getLastRotationTime().getTimeInMillis();

    return currInterval > timeInterval;
  }

}
