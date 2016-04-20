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
import org.forgerock.i18n.LocalizableMessage;


import org.forgerock.opendj.server.config.server.SizeLimitLogRotationPolicyCfg;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;

import java.util.List;

/** This class implements a rotation policy based on the size of the file. */
public class SizeBasedRotationPolicy implements
    RotationPolicy<SizeLimitLogRotationPolicyCfg>,
    ConfigurationChangeListener<SizeLimitLogRotationPolicyCfg>
{
  private long sizeLimit;

  SizeLimitLogRotationPolicyCfg currentConfig;

  @Override
  public void initializeLogRotationPolicy(SizeLimitLogRotationPolicyCfg config)
      throws ConfigException, InitializationException
  {
    sizeLimit = config.getFileSizeLimit();

    config.addSizeLimitChangeListener(this);
    currentConfig = config;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRotationPolicyCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  @Override
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
  @Override
  public boolean rotateFile(RotatableLogFile writer)
  {
    long fileSize = writer.getBytesWritten();
    return fileSize >= sizeLimit;
  }

}
