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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;



/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server error logger.
 */
public abstract class ErrorLogger
{
  /**
   * Initializes this error logger based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this error
   *                      logger.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeErrorLogger(ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Closes this error logger and releases any resources it might have
   * held.
   */
  public abstract void closeErrorLogger();



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



  /**
   * Indicates whether the provided object is equal to this error
   * logger.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is determined
   *          to be equal to this error logger, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean equals(Object o);



  /**
   * Retrieves the hash code for this error logger.
   *
   * @return  The hash code for this error logger.
   */
  public abstract int hashCode();
}

