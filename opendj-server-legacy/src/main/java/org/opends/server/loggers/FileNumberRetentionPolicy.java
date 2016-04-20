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
import static org.opends.messages.LoggerMessages.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.FileCountLogRetentionPolicyCfg;
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

  @Override
  public void initializeLogRetentionPolicy(
      FileCountLogRetentionPolicyCfg config)
  {
    this.numFiles = config.getNumberOfFiles();
    this.config = config;

    config.addFileCountChangeListener(this);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      FileCountLogRetentionPolicyCfg config,
      List<LocalizableMessage> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      FileCountLogRetentionPolicyCfg config)
  {
    this.numFiles = config.getNumberOfFiles();
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

  @Override
  public String toString()
  {
    return "Free Number Retention Policy " + config.dn();
  }
}

