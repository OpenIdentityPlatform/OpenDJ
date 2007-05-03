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
package org.opends.server.api;

import org.opends.server.admin.std.server.ErrorLogPublisherCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

import java.util.HashSet;
import java.util.HashMap;

/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server error log publisher.
 *
 * @param <T> The type of error log publisher configuration handled
 *            by this log publisher implementation.
 */
public abstract class ErrorLogPublisher
    <T extends ErrorLogPublisherCfg>
{
  /**
   * The hash map that will be used to define specific log severities
   * for the various categories.
   */
  protected HashMap<ErrorLogCategory,HashSet<ErrorLogSeverity>>
      definedSeverities =
      new HashMap<ErrorLogCategory, HashSet<ErrorLogSeverity>>();

  /**
   * The set of default log severities that will be used if no custom
   * severities have been defined for the associated category.
   */
  protected HashSet<ErrorLogSeverity> defaultSeverities =
      new HashSet<ErrorLogSeverity>();
  /**
   * Initializes this access publisher provider based on the
   * information in the provided debug publisher configuration.
   *
   * @param config
   *          The error publisher configuration that contains the
   *          information to use to initialize this error publisher.
   * @throws org.opends.server.config.ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws org.opends.server.types.InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public abstract void initializeErrorLogPublisher(T config)
      throws ConfigException, InitializationException;

  /**
   * Close this publisher.
   */
  public abstract void close();

  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine
   *                   whether to actually log this message.
   * @param  severity  The severity that may be used to determine
   *                   whether to actually log this message.
   * @param  message   The message to be logged.
   * @param  errorID   The error ID that uniquely identifies the
   *                   format string used to generate the provided
   *                   message.
   */
  public abstract void logError(ErrorLogCategory category,
                                ErrorLogSeverity severity,
                                String message, int errorID);

}
