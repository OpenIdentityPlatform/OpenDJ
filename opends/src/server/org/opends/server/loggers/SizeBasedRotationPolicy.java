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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;
import org.opends.messages.Message;


import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.admin.std.server.SizeLimitLogRotationPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.config.ConfigException;


import java.util.List;
import java.util.ArrayList;

/**
 * This class implements a rotation policy based on the size of the
 * file.
 */
public class SizeBasedRotationPolicy implements
    RotationPolicy<SizeLimitLogRotationPolicyCfg>,
    ConfigurationChangeListener<SizeLimitLogRotationPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  private long sizeLimit;

  SizeLimitLogRotationPolicyCfg currentConfig;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRotationPolicy(SizeLimitLogRotationPolicyCfg config)
      throws ConfigException, InitializationException
  {
    sizeLimit = config.getFileSizeLimit();

    config.addSizeLimitChangeListener(this);
    currentConfig = config;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRotationPolicyCfg config, List<Message> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      SizeLimitLogRotationPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    sizeLimit = config.getFileSizeLimit();

    currentConfig = config;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * This method indicates if the log file should be
   * rotated or not.
   *
   * @param writer The multi file text writer writing the log file.
   * @return true if the file needs to be rotated, false otherwise.
  */
  public boolean rotateFile(MultifileTextWriter writer)
  {
    long fileSize = writer.getBytesWritten();

    if (debugEnabled())
    {
      TRACER.debugInfo("%d bytes written in current log file. " +
          "Next rotation occurs at %d bytes", writer.getBytesWritten(),
                                              sizeLimit);
    }

    return fileSize >= sizeLimit;
  }

}

