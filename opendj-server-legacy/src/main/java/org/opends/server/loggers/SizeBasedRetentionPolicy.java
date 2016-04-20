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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;
import static org.opends.messages.LoggerMessages.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.SizeLimitLogRetentionPolicyCfg;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;

/**
 * This class implements a retention policy based on the amount of
 * space taken by the log files.
 */
public class SizeBasedRetentionPolicy implements
    RetentionPolicy<SizeLimitLogRetentionPolicyCfg>,
    ConfigurationChangeListener<SizeLimitLogRetentionPolicyCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final File[] EMPTY_FILE_LIST = new File[0];

  private long size;
  private FileComparator comparator;
  private SizeLimitLogRetentionPolicyCfg config;

  @Override
  public void initializeLogRetentionPolicy(
      SizeLimitLogRetentionPolicyCfg config)
  {
    this.size = config.getDiskSpaceUsed();
    this.comparator = new FileComparator();
    this.config = config;

    config.addSizeLimitChangeListener(this);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRetentionPolicyCfg config,
      List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      SizeLimitLogRetentionPolicyCfg config)
  {
    this.size = config.getDiskSpaceUsed();
    this.config = config;

    return new ConfigChangeResult();
  }

  @Override
  public File[] deleteFiles(FileNamingPolicy fileNamingPolicy)
      throws DirectoryException
  {
    File[] files = fileNamingPolicy.listFiles();
    if(files == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_LOGGER_ERROR_LISTING_FILES.get(fileNamingPolicy.getInitialName()));
    }

    long totalLength = 0;
    for (File file : files)
    {
      totalLength += file.length();
    }

    logger.trace("Total size of files: %d, Max: %d", totalLength, size);

    if (totalLength <= size)
    {
      return EMPTY_FILE_LIST;
    }

    long freeSpaceNeeded = totalLength - size;

    // Sort files based on last modified time.
    Arrays.sort(files, comparator);

    long freedSpace = 0;
    int j;
    for (j = files.length - 1; j >= 0; j--)
    {
      freedSpace += files[j].length();
      if (freedSpace >= freeSpaceNeeded)
      {
        break;
      }
    }

    File[] filesToDelete = new File[files.length - j];
    System.arraycopy(files, j, filesToDelete, 0, filesToDelete.length);
    return filesToDelete;
  }

  @Override
  public String toString()
  {
    return "Size Based Retention Policy " + config.dn();
  }
}

