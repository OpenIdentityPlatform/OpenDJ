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

import static org.opends.server.loggers.TraceSettings.Level.*;

import java.util.Map;

import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * Class for source-code tracing at the method level.
 *
 * One DebugTracer instance exists for each Java class using tracing.
 * Tracer must be registered with the DebugLogger.
 *
 * Logging is always done at a level basis, with debug log messages
 * exceeding the trace threshold being traced, others being discarded.
 */
public class DebugTracer
{
  /**
   *  We have to hardcode this value because we cannot import
   *  {@code org.opends.server.loggers.slf4j.OpenDJLoggerAdapter.class.getName()}
   *  to avoid OSGI split package issues.
   *  @see OPENDJ-2226
   */
  private static final String OPENDJ_LOGGER_ADAPTER_CLASS_NAME = "org.opends.server.loggers.slf4j.OpenDJLoggerAdapter";

  /** The class this aspect traces. */
  private String className;

  /** A class that represents a settings cache entry. */
  private class PublisherSettings
  {
    private final DebugLogPublisher<?> debugPublisher;
    private final TraceSettings classSettings;
    private final Map<String, TraceSettings> methodSettings;

    private PublisherSettings(String className, DebugLogPublisher<?> publisher)
    {
      debugPublisher = publisher;
      classSettings = publisher.getClassSettings(className);
      methodSettings = publisher.getMethodSettings(className);
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "("
          + "className=" + className
          + ", classSettings=" + classSettings
          + ", methodSettings=" + methodSettings
          + ")";
    }
  }

  private PublisherSettings[] publisherSettings;

  /**
   * Construct a new DebugTracer object with cached settings obtained from
   * the provided array of publishers.
   *
   * @param className The class name to use as category for logging.
   * @param publishers The array of publishers to obtain the settings from.
   */
  DebugTracer(String className, DebugLogPublisher<?>[] publishers)
  {
    this.className = className;
    publisherSettings = toPublisherSettings(publishers);
  }

  /**
   * Log the provided message.
   *
   * @param msg
   *          message to log.
   */
  public void trace(String msg)
  {
    traceException(msg, null);
  }

  /**
   * Log the provided message and exception.
   *
   * @param msg
   *          the message
   * @param exception
   *          the exception caught. May be {@code null}.
   */
  public void traceException(String msg, Throwable exception)
  {
    StackTraceElement[] stackTrace = null;
    StackTraceElement[] filteredStackTrace = null;
    StackTraceElement callerFrame = null;
    final boolean hasException = exception != null;
    for (PublisherSettings settings : publisherSettings)
    {
      TraceSettings activeSettings = settings.classSettings;
      Map<String, TraceSettings> methodSettings = settings.methodSettings;

      if (shouldLog(activeSettings, hasException) || methodSettings != null)
      {
        if (stackTrace == null)
        {
          stackTrace = Thread.currentThread().getStackTrace();
        }
        if (callerFrame == null)
        {
          callerFrame = getCallerFrame(stackTrace);
        }

        String signature = callerFrame.getMethodName();

        // Specific method settings still could exist. Try getting
        // the settings for this method.
        if (methodSettings != null)
        {
          TraceSettings mSettings = methodSettings.get(signature);
          if (mSettings == null)
          {
            // Try looking for an undecorated method name
            int idx = signature.indexOf('(');
            if (idx != -1)
            {
              mSettings = methodSettings.get(signature.substring(0, idx));
            }
          }

          // If this method does have a specific setting
          // and it is not supposed to be logged, continue.
          if (mSettings != null)
          {
            if (!shouldLog(mSettings, hasException))
            {
              continue;
            }
            else
            {
              activeSettings = mSettings;
            }
          }
        }

        String sourceLocation = callerFrame.getFileName() + ":" + callerFrame.getLineNumber();

        if (filteredStackTrace == null && activeSettings.getStackDepth() > 0)
        {
          StackTraceElement[] trace = hasException ? exception.getStackTrace() : stackTrace;
          filteredStackTrace = DebugStackTraceFormatter.SMART_FRAME_FILTER.getFilteredStackTrace(trace);
        }

        if (hasException)
        {
          settings.debugPublisher.traceException(activeSettings, signature,
              sourceLocation, msg, exception, filteredStackTrace);
        }
        else
        {
          settings.debugPublisher.trace(activeSettings, signature,
              sourceLocation, msg, filteredStackTrace);
        }
      }
    }
  }

  /**
   * Gets the name of the class this tracer traces.
   *
   * @return The name of the class this tracer traces.
   */
  String getTracedClassName()
  {
    return className;
  }

  /**
   * Indicates if logging is enabled for at least one category
   * in a publisher.
   *
   * @return {@code true} if logging is enabled, false otherwise.
   */
  public boolean enabled()
  {
    for (PublisherSettings settings : publisherSettings)
    {
      if (shouldLog(settings.classSettings) || settings.methodSettings != null)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Update the cached settings of the tracer with the settings from the
   * provided publishers.
   *
   * @param publishers The array of publishers to obtain the settings from.
   */
  void updateSettings(DebugLogPublisher<?>[] publishers)
  {
    publisherSettings = toPublisherSettings(publishers);
  }

  private PublisherSettings[] toPublisherSettings(DebugLogPublisher<?>[] publishers)
  {
    // Get the settings from all publishers.
    PublisherSettings[] newSettings = new PublisherSettings[publishers.length];
    for(int i = 0; i < publishers.length; i++)
    {
      newSettings[i] = new PublisherSettings(className, publishers[i]);
    }
    return newSettings;
  }

  /**
   * Return the caller stack frame.
   *
   * @param stackTrace
   *          The stack trace frames of the caller.
   * @return the caller stack frame or null if none is found on the stack trace.
   */
  private StackTraceElement getCallerFrame(StackTraceElement[] stackTrace)
  {
    if (stackTrace != null && stackTrace.length > 0)
    {
      // Skip all logging related classes
      for (StackTraceElement aStackTrace : stackTrace)
      {
        if(!isLoggingStackTraceElement(aStackTrace))
        {
          return aStackTrace;
        }
      }
    }

    return null;
  }

  /**
   * Checks if element belongs to a class responsible for logging
   * (includes the Thread class that may be used to get the stack trace).
   *
   * @param trace
   *            the trace element to check.
   * @return {@code true} if element corresponds to logging
   */
  static boolean isLoggingStackTraceElement(StackTraceElement trace)
  {
    String name = trace.getClassName();
    return name.startsWith(Thread.class.getName())
        || name.startsWith(DebugTracer.class.getName())
        || name.startsWith(OPENDJ_LOGGER_ADAPTER_CLASS_NAME)
        || name.startsWith(LocalizedLogger.class.getName());
  }

  /** Indicates if there is something to log. */
  private boolean shouldLog(TraceSettings settings, boolean hasException)
  {
    return settings.getLevel() == ALL
        || (hasException && settings.getLevel() == EXCEPTIONS_ONLY);
  }

  /** Indicates if there is something to log. */
  private boolean shouldLog(TraceSettings settings)
  {
    return settings.getLevel() != DISABLED;
  }
}
