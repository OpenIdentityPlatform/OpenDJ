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
import static org.opends.messages.LoggerMessages.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.FileCountLogRetentionPolicyCfg;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;


/**
 * This class implements a retention policy based on the number of files.
 * Files will be cleaned up based on the number of files on disk.
 */
public class FileNumberRetentionPolicy implements
    RetentionPolicy<FileCountLogRetentionPolicyCfg>,
    ConfigurationChangeListener<FileCountLogRetentionPolicyCfg>
{

  private int numFiles;
  private FileCountLogRetentionPolicyCfg config;

  /** {@inheritDoc} */
  public void initializeLogRetentionPolicy(
      FileCountLogRetentionPolicyCfg config)
  {
    this.numFiles = config.getNumberOfFiles();
    this.config = config;

    config.addFileCountChangeListener(this);
  }

  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
      FileCountLogRetentionPolicyCfg config,
      List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
      FileCountLogRetentionPolicyCfg config)
  {
    this.numFiles = config.getNumberOfFiles();
    this.config = config;
    return new ConfigChangeResult();
  }

  /** {@inheritDoc} */
  public File[] deleteFiles(FileNamingPolicy fileNamingPolicy)
      throws DirectoryException
  {
    File[] files = fileNamingPolicy.listFiles();
    if(files == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_LOGGER_ERROR_LISTING_FILES.get(fileNamingPolicy.getInitialName()));
    }

    if (files.length <= numFiles)
    {
      return new File[0];
    }

    // Sort files based on last modified time.
    Arrays.sort(files, new FileComparator());

    ArrayList<File> filesToDelete = new ArrayList<>();
    for (int j = numFiles; j < files.length; j++)
    {
      filesToDelete.add(files[j]);
    }
    return filesToDelete.toArray(new File[0]);
  }

  /** {@inheritDoc} */
  public String toString()
  {
    return "Free Number Retention Policy " + config.dn();
  }
}

