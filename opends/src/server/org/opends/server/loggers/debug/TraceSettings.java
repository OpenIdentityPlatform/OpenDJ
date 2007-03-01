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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.loggers.debug;

import org.opends.server.types.DebugLogLevel;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogCategory;

import java.util.Set;

/**
 * This class encapsulates the trace settings in effect at a given traceing
 * scope.
 */
public class TraceSettings
{
  /** A TraceSettings object representing a fully disabled trace state. */
  static final TraceSettings DISABLED =
      new TraceSettings(DebugLogLevel.DISABLED);

  final LogLevel level;
  final Set<LogCategory> includeCategories;

  final boolean noArgs;
  final boolean noRetVal;
  final int stackDepth;
  final boolean includeCause;

  /**
   * Construct new trace settings at the specified log level.
   *
   * @param level the log level for this setting.
   */
  public TraceSettings(LogLevel level)
  {
    this(level, null, false, false, 0, false);

  }

  /**
   * Construct new trace settings at the specified log level and including
   * the categories.
   *
   * @param level the log level for this setting.
   * @param includeCategories the categories to include in this setting.
   */
  public TraceSettings(LogLevel level, Set<LogCategory> includeCategories)
  {
    this(level, includeCategories, false, false, 0, false);

  }

  /**
   * Construct new trace settings at the specified log level and including
   * the categories. Optionally turn off arguments and return value in entry
   * and exit messages.
   *
   * @param level the log level for this setting.
   * @param includeCategories the categories to include in this setting.
   * @param noArgs whether to include arguments in the log messages.
   * @param noRetVal whether to include return values in the log messages.
   */
  public TraceSettings(LogLevel level, Set<LogCategory> includeCategories,
                       boolean noArgs, boolean noRetVal)
  {
    this(level, includeCategories, noArgs, noRetVal, 0, false);
  }

  /**
   * Construct new trace settings at the specified log level and including
   * the categories. Optionally turn off arguments, return value in entry
   * and exit messages, and specifying the depth of stack traces and whether
   * to include the cause of exceptions.
   *
   * @param level the log level for this setting.
   * @param includeCategories the categories to include in this setting.
   * @param noArgs whether to include arguments in the log messages.
   * @param noRetVal whether to include return values in the log messages.
   * @param stackDepth the stack depth to display in log messages.
   * @param includeCause whether to include the cause of exceptions.
   */
  public TraceSettings(LogLevel level, Set<LogCategory> includeCategories,
                       boolean noArgs, boolean noRetVal, int stackDepth,
                       boolean includeCause)
  {
    this.level = level;
    this.includeCategories = includeCategories;
    this.noArgs = noArgs;
    this.noRetVal = noRetVal;
    this.stackDepth = stackDepth;
    this.includeCause = includeCause;
  }
}