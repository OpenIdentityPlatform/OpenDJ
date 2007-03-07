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

package org.opends.server.loggers.debug;

import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogCategory;

import java.util.Set;
import java.util.HashSet;

/**
 * This class encapsulates the trace settings in effect at a given traceing
 * scope.
 */
public class TraceSettings
{
  /** A TraceSettings object representing a fully disabled trace state. */
  static final TraceSettings DISABLED =
      new TraceSettings(DebugLogLevel.DISABLED);

  static final String STACK_DUMP_KEYWORD = "stack";
  static final String INCLUDE_CAUSE_KEYWORD = "cause";
  static final String SUPPRESS_ARG_KEYWORD = "noargs";
  static final String SUPPRESS_RETVAL_KEYWORD = "noretval";
  static final String INCLUDE_CATEGORY_KEYWORD = "category";
  static final String LEVEL_KEYWORD = "level";

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

  /**
   * Parse trace settings from the string representation.
   *
   * @param value the trace settings string to be parsed.
   * @return the trace settings parsed from the string.
   */
  protected static TraceSettings parseTraceSettings(String value)
  {
    TraceSettings settings = null;
    if(value != null)
    {
      //Touch DebugLogLevel and DebugLogCategory so they are statically
      //initialized or parse will not see all the levels/categories.
      LogLevel level = DebugLogLevel.ERROR;
      LogCategory categoryStub = DebugLogCategory.MESSAGE;

      Set<LogCategory> includeCategories = null;
      boolean noArgs = false;
      boolean noRetVal = false;
      int stackDepth = 0;
      boolean includeCause = false;

      String[] keywords = value.split(",");

      for(String keyword : keywords)
      {
        //See if stack dump keyword is included
        if(keyword.startsWith(STACK_DUMP_KEYWORD))
        {
          //See if a stack depth is included
          if(keyword.length() == STACK_DUMP_KEYWORD.length())
          {
            stackDepth = DebugStackTraceFormatter.COMPLETE_STACK;
          }
          else
          {
            int depthStart= keyword.indexOf("=", STACK_DUMP_KEYWORD.length());
            if (depthStart == STACK_DUMP_KEYWORD.length())
            {
              try
              {
                stackDepth = Integer.valueOf(keyword.substring(depthStart+1));
              }
              catch(NumberFormatException nfe)
              {
                System.err.println("The keyword " + STACK_DUMP_KEYWORD +
                    " contains an invalid depth value. The complete stack " +
                    "will be included.");
              }
            }
          }
        }
        //See if to include cause in exception messages.
        else if(keyword.equals(INCLUDE_CAUSE_KEYWORD))
        {
          includeCause = true;
        }
        //See if to supress method arguments.
        else if(keyword.equals(SUPPRESS_ARG_KEYWORD))
        {
          noArgs = true;
        }
        //See if to supress return values.
        else if(keyword.equals(SUPPRESS_RETVAL_KEYWORD))
        {
          noRetVal = true;
        }
        else if(keyword.startsWith(INCLUDE_CATEGORY_KEYWORD))
        {
          int categoryStart =
                keyword.indexOf("=", INCLUDE_CATEGORY_KEYWORD.length());

          if(keyword.length() == INCLUDE_CATEGORY_KEYWORD.length() ||
              categoryStart != INCLUDE_CATEGORY_KEYWORD.length())
          {
            System.err.println("The keyword " + INCLUDE_CATEGORY_KEYWORD +
                " does not contain an equal sign to define the set of " +
                "categories to include. All categories will be included.");
          }
          else
          {
            String[] categories =
                keyword.substring(categoryStart+1).split("[|]");
            includeCategories = new HashSet<LogCategory>();
            for(String category : categories)
            {
              try
              {
                includeCategories.add(DebugLogCategory.parse(category));
              }
              catch(IllegalArgumentException iae)
              {
                System.err.println("The keyword " + INCLUDE_CATEGORY_KEYWORD +
                    " contains an invalid debug log category: " +
                    iae.toString() + ". It will be ignored.");
              }
            }

          }
        }
        else if(keyword.startsWith(LEVEL_KEYWORD))
        {
          int levelStart =
                keyword.indexOf("=", LEVEL_KEYWORD.length());

          if(keyword.length() == LEVEL_KEYWORD.length() ||
              levelStart != LEVEL_KEYWORD.length())
          {
            System.err.println("The keyword " + LEVEL_KEYWORD +
                " does not contain an equal sign to specify the log level. " +
                "Default level of " + level.toString() + " will be used.");
          }
          else
          {
            try
            {
              level = LogLevel.parse(keyword.substring(levelStart+1));
            }
            catch(IllegalArgumentException iae)
            {
              System.err.println("The keyword " + LEVEL_KEYWORD +
                  " contains an invalid debug log level: " +
                  iae.toString() + ". Default level of " + level.toString() +
                  " will be used.");
            }
          }
        }

      }
      settings = new TraceSettings(level, includeCategories, noArgs, noRetVal,
                                   stackDepth, includeCause);
    }

    return settings;
  }
}
