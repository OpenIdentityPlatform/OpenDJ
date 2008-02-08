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

import org.opends.server.admin.std.server.LogRetentionPolicyCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.DirectoryException;

import java.io.File;

/**
 * This interface describes the retention policy that should be used
 * for the logger. Supported policies include number of files and
 * disk utilization (for Java 6).
 *
 * @param <T> The type of retention policy configuration handled by
 *            this retention policy implementation.
 */
public interface RetentionPolicy<T extends LogRetentionPolicyCfg>
{
  /**
   * Initializes this log retention policy based on the
   * information in the provided retention policy configuration.
   *
   * @param config
   *          The retention policy configuration that contains the
   *          information to use to initialize this policy.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public abstract void initializeLogRetentionPolicy(T config)
      throws ConfigException, InitializationException;

  /**
   * Returns all files that should be deleted according to the policy.
   *
   * @param fileNamingPolicy The naming policy used generate the log file
   *                         names.
   *
   * @return An array of files that should be deleted according to the
   *         policy or <code>null</code> if an error occured while
   *         obtaining the file list.
   * @throws DirectoryException If an error occurs while obtaining a list
   *                            of files to delete.
   */
  public File[] deleteFiles(FileNamingPolicy fileNamingPolicy)
      throws DirectoryException;
}

