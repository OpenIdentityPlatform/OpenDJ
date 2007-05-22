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

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ResultCode;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.admin.std.server.FreeDiskSpaceLogRetentionPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;

/**
 * This class implements a retention policy based on the free disk
 * space available expressed as a percentage. This policy is only
 * available on Java 6.
 */
public class FreeDiskSpaceRetentionPolicy implements
    RetentionPolicy<FreeDiskSpaceLogRetentionPolicyCfg>,
    ConfigurationChangeListener<FreeDiskSpaceLogRetentionPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  private long freeDiskSpace = 0;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRetentionPolicy(
      FreeDiskSpaceLogRetentionPolicyCfg config)
  {
    freeDiskSpace = config.getFreeDiskSpace();

    config.addFreeDiskSpaceChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FreeDiskSpaceLogRetentionPolicyCfg config,
      List<String> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      FreeDiskSpaceLogRetentionPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    freeDiskSpace = config.getFreeDiskSpace();

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * This method deletes files based on the policy.
   *
   * @param writer the multi file text writer writing the log files.
   * @return number of files deleted.
   */
  public int deleteFiles(MultifileTextWriter writer)
  {
    File[] files = writer.getNamingPolicy().listFiles();

    if(files.length <= 0)
    {
      return 0;
    }

    int count = 0;
    long freeSpace = 0;

    try
    {
      // Use reflection to see use the getFreeSpace method if available.
      // this method is only available on Java 6.
      Method meth = File.class.getMethod("getFreeSpace", new Class[0]);
      Object value = meth.invoke(files[0]);
      freeSpace = ((Long) value).longValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return 0;
    }

          if(debugEnabled())
      {
        TRACER.debugInfo("Current free disk space: %d, Required: %d", freeSpace,
                  freeDiskSpace);
      }

    if (freeSpace > freeDiskSpace)
    {
      // No cleaning needed.
      return 0;
    }

    long freeSpaceNeeded = freeDiskSpace - freeSpace;

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

