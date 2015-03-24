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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.server.loggers;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.DebugTargetCfg;
import org.forgerock.opendj.config.server.ConfigChangeResult;

/**
 * This class encapsulates the trace settings in effect at a given tracing scope.
 */
public class TraceSettings implements
    ConfigurationChangeListener<DebugTargetCfg>
{
  /** A TraceSettings object representing a fully disabled trace state. */
  public static final TraceSettings DISABLED =
      new TraceSettings(Level.DISABLED);

  private static final String STACK_DUMP_KEYWORD = "stack";
  private static final String INCLUDE_CAUSE_KEYWORD = "cause";
  private static final String SUPPRESS_ARG_KEYWORD = "noargs";
  private static final String SUPPRESS_RETVAL_KEYWORD = "noretval";
  private static final String ENABLED_KEYWORD = "enabled";
  private static final String EXCEPTIONS_ONLY_KEYWORD = "exceptionsonly";

  /**
   * Represents the level of trace.
   */
  enum Level
  {
    /** Log nothing. **/
    DISABLED,

    /** Log only exceptions. **/
    EXCEPTIONS_ONLY,

    /** Log everything. */
    ALL;

    /**
     * Returns the level corresponding to provided options.
     *
     * @param isEnabled
     *          Indicates if tracer is enabled.
     * @param isDebugExceptionsOnly
     *          Indicates if tracer should log only exceptions.
     * @return the level corresponding to options
     */
    static Level getLevel(boolean isEnabled, boolean isDebugExceptionsOnly)
    {
      if (isEnabled)
      {
        if (isDebugExceptionsOnly)
        {
          return Level.EXCEPTIONS_ONLY;
        }
        else
        {
          return Level.ALL;
        }
      }
      return Level.DISABLED;
    }

  }

  /**
   * The level of this setting.
   */
  private Level level;

  /**
   * Indicates if method arguments should be logged.
   */
  private boolean noArgs;

  /**
   * Indicates if method return values should be logged.
   */
  private boolean noRetVal;

  /**
   * The level of stack frames to include.
   */
  private int stackDepth;

  /**
   * Indicates if the cause exception is included in exception messages.
   */
  private boolean includeCause;

  private DebugTargetCfg currentConfig;

  /**
   * Construct new trace settings with default values.
   */
  public TraceSettings()
  {
    this(Level.ALL, false, false, 0, false);

  }

  /**
   * Construct new trace settings at provided level.
   *
   * @param level
   *          Level for this settings.
   */
  private TraceSettings(Level level)
  {
    this(level, false, false, 0, false);

  }

  /**
   * Construct new trace settings at the specified level. Optionally turn off
   * arguments, return value in entry and exit messages, and specifying the
   * depth of stack traces and whether to include the cause of exceptions.
   *
   * @param level
   *          the level for this setting.
   * @param noArgs
   *          whether to include arguments in the log messages.
   * @param noRetVal
   *          whether to include return values in the log messages.
   * @param stackDepth
   *          the stack depth to display in log messages.
   * @param includeCause
   *          whether to include the cause of exceptions.
   */
  TraceSettings(Level level, boolean noArgs,
      boolean noRetVal, int stackDepth, boolean includeCause)
  {
    this.level = level;
    this.noArgs = noArgs;
    this.noRetVal = noRetVal;
    this.stackDepth = stackDepth;
    this.includeCause = includeCause;
  }

  /**
   * Construct a new trace settings from the provided configuration.
   *
   * @param config
   *          The debug target configuration that contains the information to
   *          use to initialize this trace setting.
   */
  TraceSettings(DebugTargetCfg config)
  {
    this.level =
        Level.getLevel(config.isEnabled(), config.isDebugExceptionsOnly());
    this.noArgs = config.isOmitMethodEntryArguments();
    this.noRetVal = config.isOmitMethodReturnValue();
    this.stackDepth = config.getThrowableStackFrames();
    this.includeCause = config.isIncludeThrowableCause();

    currentConfig = config;
    config.addChangeListener(this);
  }

  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(DebugTargetCfg config,
      List<LocalizableMessage> unacceptableReasons)
  {
    // This should alwas be acceptable. We are assuing that the scope for this
    // trace setting is the same sine its part of the DN.
    return true;
  }

  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(DebugTargetCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // We can assume that the target scope did not change since its the
    // naming attribute. Changing it would result in a modify DN.

    this.level =
        Level.getLevel(config.isEnabled(), config.isDebugExceptionsOnly());
    this.noArgs = config.isOmitMethodEntryArguments();
    this.noRetVal = config.isOmitMethodReturnValue();
    this.stackDepth = config.getThrowableStackFrames();
    this.includeCause = config.isIncludeThrowableCause();

    this.currentConfig = config;

    return ccr;
  }

  /**
   * Parse trace settings from the string representation.
   *
   * @param value
   *          the trace settings string to be parsed.
   * @return the trace settings parsed from the string.
   */
  protected static TraceSettings parseTraceSettings(String value)
  {
    TraceSettings settings = null;
    if (value != null)
    {
      boolean enabled = false;
      boolean exceptionsOnly = false;
      boolean noArgs = false;
      boolean noRetVal = false;
      int stackDepth = 0;
      boolean includeCause = false;

      String[] keywords = value.split(",");

      for (String keyword : keywords)
      {
        //See if stack dump keyword is included
        if (keyword.startsWith(STACK_DUMP_KEYWORD))
        {
          //See if a stack depth is included
          if (keyword.length() == STACK_DUMP_KEYWORD.length())
          {
            stackDepth = DebugStackTraceFormatter.COMPLETE_STACK;
          }
          else
          {
            int depthStart = keyword.indexOf("=", STACK_DUMP_KEYWORD.length());
            if (depthStart == STACK_DUMP_KEYWORD.length())
            {
              try
              {
                stackDepth = Integer.valueOf(keyword.substring(depthStart + 1));
              }
              catch (NumberFormatException nfe)
              { // TODO: i18n
                System.err.println("The keyword " + STACK_DUMP_KEYWORD
                    + " contains an invalid depth value. The complete stack "
                    + "will be included.");
              }
            }
          }
        }
        //See if to include cause in exception messages.
        else if (keyword.equals(INCLUDE_CAUSE_KEYWORD))
        {
          includeCause = true;
        }
        //See if to supress method arguments.
        else if (keyword.equals(SUPPRESS_ARG_KEYWORD))
        {
          noArgs = true;
        }
        //See if to supress return values.
        else if (keyword.equals(SUPPRESS_RETVAL_KEYWORD))
        {
          noRetVal = true;
        }
        else if (keyword.equals(ENABLED_KEYWORD))
        {
          enabled = true;
        }
        else if (keyword.equals(EXCEPTIONS_ONLY_KEYWORD))
        {
          exceptionsOnly = true;
        }
      }
      settings =
          new TraceSettings(Level.getLevel(enabled, exceptionsOnly),
              noArgs, noRetVal, stackDepth, includeCause);
    }

    return settings;
  }

  /**
   * Get the level of this setting.
   *
   * @return the level of this setting.
   */
  public Level getLevel()
  {
    return level;
  }

  /**
   * Get whether method arguments should be logged.
   *
   * @return if method arguments should be logged.
   */
  public boolean isNoArgs()
  {
    return noArgs;
  }

  /**
   * Get whether method return values should be logged.
   *
   * @return if method return values should be logged.
   */
  public boolean isNoRetVal()
  {
    return noRetVal;
  }

  /**
   * Get the level of stack frames to include.
   *
   * @return the level of stack frames to include.
   */
  public int getStackDepth()
  {
    return stackDepth;
  }

  /**
   * Get whether the cause exception is included in exception messages.
   *
   * @return if the cause exception is included in exception messages.
   */
  public boolean isIncludeCause()
  {
    return includeCause;
  }
}
