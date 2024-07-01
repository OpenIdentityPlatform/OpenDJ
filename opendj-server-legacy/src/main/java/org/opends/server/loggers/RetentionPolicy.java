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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.File;

import org.forgerock.opendj.server.config.server.LogRetentionPolicyCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This interface describes the retention policy that should be used
 * for the logger. Supported policies include number of files and
 * disk utilization.
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
  void initializeLogRetentionPolicy(T config)
      throws ConfigException, InitializationException;

  /**
   * Returns all files that should be deleted according to the policy.
   *
   * @param fileNamingPolicy The naming policy used generate the log file
   *                         names.
   *
   * @return An array of files that should be deleted according to the
   *         policy or <code>null</code> if an error occurred while
   *         obtaining the file list.
   * @throws DirectoryException If an error occurs while obtaining a list
   *                            of files to delete.
   */
  File[] deleteFiles(FileNamingPolicy fileNamingPolicy)
      throws DirectoryException;
}

