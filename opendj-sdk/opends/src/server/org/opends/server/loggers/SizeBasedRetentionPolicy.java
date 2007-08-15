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

import org.opends.server.admin.std.server.SizeLimitLogRetentionPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;


import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * This class implements a retention policy based on the amount of
 * space taken by the log files.
 */
public class SizeBasedRetentionPolicy implements
    RetentionPolicy<SizeLimitLogRetentionPolicyCfg>,
    ConfigurationChangeListener<SizeLimitLogRetentionPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  private long size = 0;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRetentionPolicy(
      SizeLimitLogRetentionPolicyCfg config)
  {
    size = config.getDiskSpaceUsed();

    config.addSizeLimitChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRetentionPolicyCfg config,
      List<Message> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      SizeLimitLogRetentionPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    size = config.getDiskSpaceUsed();

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public int deleteFiles(MultifileTextWriter writer)
  {
    File[] files = writer.getNamingPolicy().listFiles();
    int count = 0;

    long totalLength = 0;
    for (File file : files)
    {
      totalLength += file.length();
    }

    if(debugEnabled())
    {
      TRACER.debugInfo("Total size of files: %d, Max: %d", totalLength, size);
    }

    if (totalLength <= size)
    {
      return 0;
    }

    long freeSpaceNeeded = totalLength - size;

    // Sort files based on last modified time.
    Arrays.sort(files, new FileComparator());

    long freedSpace = 0;
    for (int j = files.length - 1; j < 1; j--)
    {
      freedSpace += files[j].length();
      if(debugEnabled())
      {
        TRACER.debugInfo("Deleting log file:", files[j]);
      }
      files[j].delete();
      if (freedSpace >= freeSpaceNeeded)
      {
        break;
      }

      count++;
    }

    return count;
  }

}

