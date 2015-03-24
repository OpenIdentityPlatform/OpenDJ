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


import org.opends.server.admin.std.server.SizeLimitLogRotationPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;

import java.util.List;

/**
 * This class implements a rotation policy based on the size of the file.
 */
public class SizeBasedRotationPolicy implements
    RotationPolicy<SizeLimitLogRotationPolicyCfg>,
    ConfigurationChangeListener<SizeLimitLogRotationPolicyCfg>
{
  private long sizeLimit;

  SizeLimitLogRotationPolicyCfg currentConfig;

  /** {@inheritDoc} */
  public void initializeLogRotationPolicy(SizeLimitLogRotationPolicyCfg config)
      throws ConfigException, InitializationException
  {
    sizeLimit = config.getFileSizeLimit();

    config.addSizeLimitChangeListener(this);
    currentConfig = config;
  }

  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRotationPolicyCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
      SizeLimitLogRotationPolicyCfg config)
  {
    sizeLimit = config.getFileSizeLimit();
    currentConfig = config;

    return new ConfigChangeResult();
  }

  /**
   * This method indicates if the log file should be
   * rotated or not.
   *
   * @param writer The multi file text writer writing the log file.
   * @return true if the file needs to be rotated, false otherwise.
  */
  public boolean rotateFile(RotatableLogFile writer)
  {
    long fileSize = writer.getBytesWritten();
    return fileSize >= sizeLimit;
  }

}
