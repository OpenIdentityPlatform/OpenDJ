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

import org.opends.server.admin.std.server.LogRotationPolicyCfg;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.config.server.ConfigException;


/**
 * This interface describes the rotation policy that should be used
 * for the logger. Supported policies include size based and time
 * based.
 *
 * @param <T> The type of rotation policy configuration handled by
 *            this retention policy implementation.
 */
public interface RotationPolicy<T extends LogRotationPolicyCfg>
{
  /**
   * Initializes this log rotation policy based on the
   * information in the provided rotation policy configuration.
   *
   * @param config
   *          The rotation policy configuration that contains the
   *          information to use to initialize this policy.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  void initializeLogRotationPolicy(T config)
      throws ConfigException, InitializationException;


  /**
   * This method indicates if the log file should be rotated or not.
   *
   * @param writer
   *          the file writer to be checked.
   * @return true if the log file should be rotated, false otherwise.
   */
  boolean rotateFile(RotatableLogFile writer);


}

