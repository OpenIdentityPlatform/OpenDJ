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
package org.opends.server.types;

import static org.opends.server.util.ServerConstants.*;
import org.opends.server.loggers.LogLevel;

/**
 * Logging levels for the debug log messages.
 */
public class DebugLogLevel extends LogLevel
{

  /**
   * The log level that will be used for verbose messages.
   */
  public static final LogLevel VERBOSE = new DebugLogLevel(
      DEBUG_SEVERITY_VERBOSE, 100);



  /**
   * The log level that will be used for informational messages.
   */
  public static final LogLevel INFO = new DebugLogLevel(
      DEBUG_SEVERITY_INFO, 200);



  /**
   * The log level that will be used for warning messages.
   */
  public static final LogLevel WARNING = new DebugLogLevel(
      DEBUG_SEVERITY_WARNING, 300);



  /**
   * The log level that will be used for error messages.
   */
  public static final LogLevel ERROR = new DebugLogLevel(
      DEBUG_SEVERITY_ERROR, 400);



  /**
   * Constructor for the DebugLogLevel class.
   *
   * @param  name  The name of the level.
   * @param  value The value of the level.
   */
  protected DebugLogLevel(String name, int value)
  {
    super(name, value);
  }
}

