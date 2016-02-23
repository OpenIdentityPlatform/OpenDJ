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
 * Portions Copyright 2014-2015 ForgeRock AS.
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
