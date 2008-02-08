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

import org.opends.server.admin.std.server.FileCountLogRetentionPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DirectoryException;
import org.opends.server.core.DirectoryServer;


/**
 * This class implements a retention policy based on the number of files.
 * Files will be cleaned up based on the number of files on disk.
 */
public class FileNumberRetentionPolicy implements
    RetentionPolicy<FileCountLogRetentionPolicyCfg>,
    ConfigurationChangeListener<FileCountLogRetentionPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private int numFiles = 0;
  private FileCountLogRetentionPolicyCfg config;

  /**
   * {@inheritDoc}
   */
  public void initializeLogRetentionPolicy(
      FileCountLogRetentionPolicyCfg config)
  {
    this.numFiles = config.getNumberOfFiles();
    this.config = config;

    config.addFileCountChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FileCountLogRetentionPolicyCfg config,
      List<Message> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      FileCountLogRetentionPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    this.numFiles = config.getNumberOfFiles();
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

    if (files.length <= numFiles)
    {
      return new File[0];
    }

    // Sort files based on last modified time.
    Arrays.sort(files, new FileComparator());

    for (int j = numFiles; j < files.length; j++)
    {
      filesToDelete.add(files[j]);
    }

    return filesToDelete.toArray(new File[0]);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return "Free Number Retention Policy " + config.dn().toString();
  }
}

