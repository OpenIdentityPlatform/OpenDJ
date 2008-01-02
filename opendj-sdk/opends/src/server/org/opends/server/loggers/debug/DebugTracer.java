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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.loggers.debug;

import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.ProtocolElement;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogCategory;

import java.util.Map;
import java.nio.ByteBuffer;

import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Database;

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
  // The class this aspect traces.
  private String className;

  /**
   * A class that represents a settings cache entry.
   */
  private class PublisherSettings
  {
    DebugLogPublisher debugPublisher;
    TraceSettings classSettings;
    Map<String, TraceSettings> methodSettings;
  }

  PublisherSettings[] publisherSettings;

  /**
   * Construct a new DebugTracer object with cached settings obtained from
   * the provided array of publishers.
   *
   * @param publishers The array of publishers to obtain the settings from.
   */
  @SuppressWarnings("unchecked")
  DebugTracer(DebugLogPublisher[] publishers)
  {
    // Trim off the debug logging and non OpenDS frames.
    StackTraceElement callerFrame =
        getCallerFrame(Thread.currentThread().getStackTrace());

    // TODO: What if this is null or 0 length?
    if(callerFrame != null)
    {
      // The caller should be the first item on the stack.
      className = callerFrame.getClassName();
    }

    publisherSettings = new PublisherSettings[publishers.length];

    // Get the settings from all publishers.
    for(int i = 0; i < publishers.length; i++)
    {
      DebugLogPublisher publisher = publishers[i];
      PublisherSettings settings = new PublisherSettings();

      settings.debugPublisher = publisher;
      settings.classSettings = publisher.getClassSettings(className);

      // For some reason, the compiler doesn't see that
      // debugLogPublihser.getMethodSettings returns a parameterized Map.
      // This problem goes away if a parameterized verson of DebugLogPublisher
      // is used. However, we can't not use reflection to instantiate a generic
      // DebugLogPublisher<? extends DebugLogPublisherCfg> type. The only thing
      // we can do is to just supress the compiler warnings.
      settings.methodSettings = publisher.getMethodSettings(className);

      publisherSettings[i] = settings;
    }
  }

  /**
   * Log an constructor execution event.
   *
   * @param level The level of the message being logged.
   * @param args The arguments passed to the constructor.
   */
  public void debugConstructor(LogLevel level, Object... args)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.CONSTRUCTOR,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.CONSTRUCTOR,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (activeSettings.noArgs)
          {
            args = null;
          }

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceConstructor(level,
                                                   activeSettings, signature,
                                                   sl, args,
                                                   filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log an non static method entry event.
   *
   * @param level The level of the message being logged.
   * @param obj The object type instance the method is a member of.
   * @param args The arguments passed to the method.
   */
  public void debugMethodEntry(LogLevel level, Object obj, Object... args)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.ENTER,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.ENTER,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (activeSettings.noArgs)
          {
            args = null;
          }

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceMethodEntry(level,
                                                   activeSettings, signature,
                                                   sl, obj, args,
                                                   filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log an static method entry event.
   *
   * @param level The level of the message being logged.
   * @param args The arguments passed to the method.
   */
  public void debugStaticMethodEntry(LogLevel level, Object... args)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.ENTER,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.ENTER,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (activeSettings.noArgs)
          {
            args = null;
          }

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceStaticMethodEntry(level,
                                                         activeSettings,
                                                         signature, sl, args,
                                                         filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log a return from a method call event.
   *
   * @param level The level of the message being logged.
   * @param ret The value being returned from the method.
   */
  public void debugReturn(LogLevel level, Object ret)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.ENTER,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.ENTER,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (activeSettings.noRetVal)
          {
            ret = null;
          }

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceReturn(level,
                                              activeSettings, signature,
                                              sl, ret,
                                              filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log an exception thrown from a method.
   *
   * @param level The level of the message being logged.
   * @param ex The exception being thrown.
   */
  public void debugThrown(LogLevel level, Throwable ex)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.THROWN,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.THROWN,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(ex.getStackTrace());
          }

          settings.debugPublisher.traceThrown(level, activeSettings, signature,
                                              sl, ex, filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log an arbitrary event at the verbose level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg)
   *
   * @param msg message to format and log.
   */
  public void debugVerbose(String msg)
  {
    debugMessage(DebugLogLevel.VERBOSE, msg, new Object[]{});
  }

  /**
   * Log an arbitrary event at the verbose level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg, msgArgs...)
   *
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  public void debugVerbose(String msg, Object... msgArgs)
  {
    debugMessage(DebugLogLevel.VERBOSE, msg, msgArgs);
  }

  /**
   * Log an arbitrary event at the info level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg)
   *
   * @param msg message to format and log.
   */
  public void debugInfo(String msg)
  {
    debugMessage(DebugLogLevel.INFO, msg, new Object[]{});
  }

  /**
   * Log an arbitrary event at the info level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg, msgArgs...)
   *
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  public void debugInfo(String msg, Object... msgArgs)
  {
    debugMessage(DebugLogLevel.INFO, msg, msgArgs);
  }

  /**
   * Log an arbitrary event at the warning level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg)
   *
   * @param msg message to format and log.
   */
  public void debugWarning(String msg)

  {
    debugMessage(DebugLogLevel.WARNING, msg, new Object[]{});
  }

  /**
   * Log an arbitrary event at the warning level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg, msgArgs...)
   *
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  public void debugWarning(String msg, Object... msgArgs)
  {
    debugMessage(DebugLogLevel.WARNING, msg, msgArgs);
  }

  /**
   * Log an arbitrary event at the error level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg)
   *
   * @param msg message to format and log.
   */
  public void debugError(String msg)

  {
    debugMessage(DebugLogLevel.ERROR, msg, new Object[]{});
  }

  /**
   * Log an arbitrary event at the error level.
   * Same as debugMessage(DebugLogLevel.ERROR, msg, msgArgs...)
   *
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  public void debugError(String msg, Object... msgArgs)
  {
    debugMessage(DebugLogLevel.ERROR, msg, msgArgs);
  }

  /**
   * Log an arbitrary event.
   *
   * @param level the level of the log message.
   * @param msg message to format and log.
   */
  public void debugMessage(LogLevel level, String msg)
  {
    debugMessage(level, msg, new Object[]{});
  }

  /**
   * Log an arbitrary event.
   *
   * @param level the level of the log message.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  public void debugMessage(LogLevel level, String msg, Object... msgArgs)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.MESSAGE,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.MESSAGE,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if(msgArgs != null && msgArgs.length > 0)
          {
            msg = String.format(msg, msgArgs);
          }

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceMessage(level, activeSettings, signature,
                                               sl, msg, filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log an cought exception.
   *
   * @param level the level of the log message.
   * @param ex the exception caught.
   */
  public void debugCaught(LogLevel level, Throwable ex)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.CAUGHT,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.CAUGHT,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(ex.getStackTrace());
          }

          settings.debugPublisher.traceCaught(level, activeSettings, signature,
                                              sl, ex, filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log a JE database access event.
   *
   * @param level the level of the log message.
   * @param status status of the JE operation.
   * @param database the database handle.
   * @param txn  transaction handle (may be null).
   * @param key  the key to dump.
   * @param data the data to dump.
   */
  public void debugJEAccess(LogLevel level, OperationStatus status,
                            Database database, Transaction txn,
                            DatabaseEntry key, DatabaseEntry data)
  {
    if(DebugLogger.debugEnabled())
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.DATABASE_ACCESS,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.DATABASE_ACCESS,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceJEAccess(level, activeSettings,
                                                signature, sl, status, database,
                                                txn, key, data,
                                                filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log raw data in the form of a byte array.
   *
   * @param level the level of the log message.
   * @param data the data to dump.
   */
  public void debugData(LogLevel level, byte[] data)
  {
    if(DebugLogger.debugEnabled() && data != null)
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.DATA,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.DATA,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceData(level, activeSettings, signature,
                                            sl, data, filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log a protocol element.
   *
   * @param level the level of the log message.
   * @param element the protocol element to dump.
   */
  public void debugProtocolElement(LogLevel level, ProtocolElement element)
  {
    if(DebugLogger.debugEnabled() && element != null)
    {
      StackTraceElement[] stackTrace = null;
      StackTraceElement[] filteredStackTrace = null;
      StackTraceElement callerFrame = null;
      for (PublisherSettings settings : publisherSettings)
      {
        TraceSettings activeSettings = settings.classSettings;
        Map<String, TraceSettings> methodSettings = settings.methodSettings;

        if (shouldLog(level, DebugLogCategory.PROTOCOL,
                      activeSettings) || methodSettings != null)
        {
          if(stackTrace == null)
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
          if(methodSettings != null)
          {
            TraceSettings mSettings = methodSettings.get(signature);

            if (mSettings == null)
            {
              // Try looking for an undecorated method name
              int idx = signature.indexOf('(');
              if (idx != -1)
              {
                mSettings =
                    methodSettings.get(signature.substring(0, idx));
              }
            }

            // If this method does have a specific setting and it is not
            // suppose to be logged, continue.
            if (mSettings != null)
            {
              if(!shouldLog(level, DebugLogCategory.PROTOCOL,
                            mSettings))
              {
                continue;
              }
              else
              {
                activeSettings = mSettings;
              }
            }
          }

          String sl = callerFrame.getFileName() + ":" +
              callerFrame.getLineNumber();

          if (filteredStackTrace == null && activeSettings.stackDepth > 0)
          {
            filteredStackTrace =
                DebugStackTraceFormatter.SMART_FRAME_FILTER.
                    getFilteredStackTrace(stackTrace);
          }

          settings.debugPublisher.traceProtocolElement(level, activeSettings,
                                                       signature, sl, element,
                                                       filteredStackTrace);
        }
      }
    }
  }

  /**
   * Log raw data in the form of a ByteBuffer.
   *
   * @param level the level of the log message.
   * @param buffer the data to dump.
   */
  public void debugData(LogLevel level, ByteBuffer buffer)
  {
    debugData(level, buffer.array());
  }

  /**
   * Gets the name of the class this tracer traces.
   *
   * @return The name of the class this tracer traces.
   */
  public String getTracedClassName()
  {
    return className;
  }

  /**
   * Update the cached settings of the tracer with the settings from the
   * provided publishers.
   *
   * @param publishers The array of publishers to obtain the settings from.
   */
  @SuppressWarnings("unchecked")
  void updateSettings(DebugLogPublisher[] publishers)
  {
    PublisherSettings[] newSettings =
        new PublisherSettings[publishers.length];

    // Get the settings from all publishers.
    for(int i = 0; i < publishers.length; i++)
    {
      DebugLogPublisher publisher = publishers[i];
      PublisherSettings settings = new PublisherSettings();

      settings.debugPublisher = publisher;
      settings.classSettings = publisher.getClassSettings(className);

      // For some reason, the compiler doesn't see that
      // debugLogPublihser.getMethodSettings returns a parameterized Map.
      // This problem goes away if a parameterized verson of DebugLogPublisher
      // is used. However, we can't not use reflection to instantiate a generic
      // DebugLogPublisher<? extends DebugLogPublisherCfg> type. The only thing
      // we can do is to just supress the compiler warnings.
      settings.methodSettings = publisher.getMethodSettings(className);

      newSettings[i] = settings;
    }

    publisherSettings = newSettings;
  }

  /**
   * Return the caller stack frame.
   *
   * @param stackTrace The entrie stack trace frames.
   * @return the caller stack frame or null if none is found on the
   * stack trace.
   */
  private StackTraceElement getCallerFrame(StackTraceElement[] stackTrace)
  {
    if (stackTrace != null && stackTrace.length > 0)
    {
      // Skip leading frames debug logging classes and getStackTrace
      // method call frame if any.
      for (int i = 0; i < stackTrace.length; i++)
      {
        StackTraceElement aStackTrace = stackTrace[i];
        if(aStackTrace.getClassName().startsWith("java.lang.Thread"))
        {
          continue;
        }

        if (!aStackTrace.getClassName().startsWith(
            "org.opends.server.loggers.debug"))
        {
          return aStackTrace;
        }
      }
    }

    return null;
  }

  private boolean shouldLog(LogLevel messageLevel, LogCategory messageCategory,
                            TraceSettings activeSettings)
  {
    return !(activeSettings.includeCategories != null &&
        !activeSettings.includeCategories.contains(messageCategory)) &&
        messageLevel.intValue() >= activeSettings.level.intValue();

  }
}
