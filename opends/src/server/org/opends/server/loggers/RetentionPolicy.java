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

import org.opends.server.admin.std.server.LogRetentionPolicyCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;


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
   * This method checks for whether files should be deleted or not.
   *
   * @param writer The multi file writer writing the files to be
   *        checked.
   *
   * @return number of files deleted, if any.
   */
  public int deleteFiles(MultifileTextWriter writer);

}

