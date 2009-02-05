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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */

package org.opends.server.loggers.debug;
import org.opends.messages.Message;

import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogCategory;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.DebugTargetCfgDefn;
import org.opends.server.admin.std.server.DebugTargetCfg;


import java.util.*;

/**
 * This class encapsulates the trace settings in effect at a given traceing
 * scope.
 */
public class TraceSettings
    implements ConfigurationChangeListener<DebugTargetCfg>
{
  /** A TraceSettings object representing a fully disabled trace state. */
  public static final TraceSettings DISABLED =
      new TraceSettings(DebugLogLevel.DISABLED);

  private static final String STACK_DUMP_KEYWORD = "stack";
  private static final String INCLUDE_CAUSE_KEYWORD = "cause";
  private static final String SUPPRESS_ARG_KEYWORD = "noargs";
  private static final String SUPPRESS_RETVAL_KEYWORD = "noretval";
  private static final String INCLUDE_CATEGORY_KEYWORD = "category";
  private static final String LEVEL_KEYWORD = "level";

  /**
   * The log level of this setting.
   */
  LogLevel level;

  /**
   * The log categories for this setting.
   */
  Set<LogCategory> includeCategories;

  /**
   * Indicates if method arguments should be logged.
   */
  boolean noArgs;

  /**
   * Indicates if method return values should be logged.
   */
  boolean noRetVal;

  /**
   * The level of stack frames to include.
   */
  int stackDepth;

  /**
   * Indicates if the cause exception is included in exception messages.
   */
  boolean includeCause;

  private DebugTargetCfg currentConfig;

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
   * Construct a new trace settings from the provided configuration.
   *
   * @param config The debug target configuration that contains the information
   *               to use to initialize this trace setting.
   */
  public TraceSettings(DebugTargetCfg config)
  {
    this.level =
        DebugLogLevel.parse(config.getDebugLevel().toString());

    Set<LogCategory> logCategories = null;
    if(!config.getDebugCategory().isEmpty())
    {
      logCategories =
          new HashSet<LogCategory>(config.getDebugCategory().size());
      for(DebugTargetCfgDefn.DebugCategory category :
          config.getDebugCategory())
      {
        logCategories.add(DebugLogCategory.parse(category.toString()));
      }
    }

    this.includeCategories = logCategories;
    this.noArgs = config.isOmitMethodEntryArguments();
    this.noRetVal = config.isOmitMethodReturnValue();
    this.stackDepth = config.getThrowableStackFrames();
    this.includeCause = config.isIncludeThrowableCause();

    currentConfig = config;
    config.addChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
          DebugTargetCfg config,
          List<Message> unacceptableReasons)
  {
    // This should alwas be acceptable. We are assuing that the scope for this
    // trace setting is the same sine its part of the DN.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(DebugTargetCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    // We can assume that the target scope did not change since its the
    // naming attribute. Changing it would result in a modify DN.

    this.level =
        DebugLogLevel.parse(config.getDebugLevel().toString());

    Set<LogCategory> logCategories = null;
    if(!config.getDebugCategory().isEmpty())
    {
      logCategories =
          new HashSet<LogCategory>(config.getDebugCategory().size());
      for(DebugTargetCfgDefn.DebugCategory category :
          config.getDebugCategory())
      {
        logCategories.add(DebugLogCategory.parse(category.toString()));
      }
    }

    this.includeCategories = logCategories;
    this.noArgs = config.isOmitMethodEntryArguments();
    this.noRetVal = config.isOmitMethodReturnValue();
    this.stackDepth = config.getThrowableStackFrames();
    this.includeCause = config.isIncludeThrowableCause();

    this.currentConfig = config;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
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
              { // TODO: i18n
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
          { // TODO: i18n
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
              { // TODO: i18n
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
          { // TODO: i18n
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
            {  // TODO: i18n
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

  /**
   * Get the log level of this setting.
   * @return the log level of this setting.
   */
  public LogLevel getLevel() {
    return level;
  }

  /**
   * Get the log categories for this setting.
   * @return the log categories for this setting.
   */
  public Set<LogCategory> getIncludeCategories() {
    return Collections.unmodifiableSet(includeCategories);
  }

  /**
   * Get whether method arguments should be logged.
   * @return if method arguments should be logged.
   */
  public boolean isNoArgs() {
    return noArgs;
  }

  /**
   * Get whether method return values should be logged.
   * @return if method return values should be logged.
   */
  public boolean isNoRetVal() {
    return noRetVal;
  }

  /**
   * Get the level of stack frames to include.
   * @return the level of stack frames to include.
   */
  public int getStackDepth() {
    return stackDepth;
  }

  /**
   * Get whether the cause exception is included in exception messages.
   * @return if the cause exception is included in exception messages.
   */
  public boolean isIncludeCause() {
    return includeCause;
  }
}
