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
import static org.opends.messages.LoggerMessages.ERR_LOGGER_ERROR_LISTING_FILES;

import org.opends.server.admin.std.server.SizeLimitLogRetentionPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DirectoryException;
import org.opends.server.core.DirectoryServer;


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
  private SizeLimitLogRetentionPolicyCfg config;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRetentionPolicy(
      SizeLimitLogRetentionPolicyCfg config)
  {
    this.size = config.getDiskSpaceUsed();
    this.config = config;

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

    this.size = config.getDiskSpaceUsed();
    this.config = config;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public File[] deleteFiles(FileNamingPolicy fileNamingPolicy)
      throws DirectoryException
  {
    File[] files = fileNamingPolicy.listFiles();
    if(files == null)
    {
      Message message =
          ERR_LOGGER_ERROR_LISTING_FILES.get(
              fileNamingPolicy.getInitialName().toString());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    ArrayList<File> filesToDelete = new ArrayList<File>();

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
      return new File[0];
    }

    long freeSpaceNeeded = totalLength - size;

    // Sort files based on last modified time.
    Arrays.sort(files, new FileComparator());

    long freedSpace = 0;
    for (int j = files.length - 1; j < 1; j--)
    {
      freedSpace += files[j].length();
      filesToDelete.add(files[j]);
      if (freedSpace >= freeSpaceNeeded)
      {
        break;
      }
    }

    return filesToDelete.toArray(new File[0]);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return "Size Based Retention Policy " + config.dn().toString();
  }
}

