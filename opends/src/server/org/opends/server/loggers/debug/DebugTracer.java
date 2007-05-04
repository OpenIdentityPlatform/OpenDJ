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

import org.aspectj.lang.annotation.*;
import org.aspectj.lang.JoinPoint;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.ProtocolElement;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogCategory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.nio.ByteBuffer;

import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Database;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.DatabaseEntry;

import static
    org.opends.server.loggers.debug.DebugLogger.DEFAULT_CONSTRUCTOR_LEVEL;
import static
    org.opends.server.loggers.debug.DebugLogger.DEFAULT_ENTRY_EXIT_LEVEL;
import static
    org.opends.server.loggers.debug.DebugLogger.DEFAULT_THROWN_LEVEL;
import static
    org.opends.server.loggers.debug.DebugLogger.debugPublishers;
import static
    org.opends.server.loggers.debug.DebugLogger.classTracers;

/**
 * An aspect for source-code tracing at the method level.
 *
 * One DebugLogger aspect instance exists for each Java class using tracing.
 * Tracer must be registered with the DebugLogger.
 *
 * Logging is always done at a level basis, with debug log messages
 * exceeding the trace threshold being traced, others being discarded.
 */
@Aspect("pertypewithin(!@DebugLogger.NoDebugTracing org.opends.server..*+ && " +
    "!org.opends.server.loggers.*+ && " +
    "!org.opends.server.loggers.debug..*+ &&" +
    "!org.opends.server.types.DebugLogLevel+ && " +
    "!org.opends.server.types.DebugLogCategory+ && " +
    "!org.opends.server.api.DebugLogPublisher+ &&" +
    "!org.opends.server.util.TimeThread+)")
public class DebugTracer
{
    /**
   * Pointcut for matching static context events.
   */
  @Pointcut("!this(Object)")
  private void staticContext()
  {
  }

  /**
   * Pointcut for matching non static context events.
   * @param obj The object being operated on.
   */
  @Pointcut("this(obj)")
  private void nonStaticContext(Object obj)
  {
  }

  /**
   * Pointcut for matching all toString() methods.
   */
  @Pointcut("execution(* *..toString(..))")
  private void toStringMethod()
  {
  }

  /**
   * Pointcut for matching all getMessage() methods.
   */
  @Pointcut("execution(String org.opends.server." +
      "messages.MessageHandler.getMessage(..))")
  private void getMessageMethod()
  {
  }

  /**
   * Pointcut for matching all getDebugProperties() methods.
   * TODO: Make this less general. Find out if pointcut matches
   * subclass methods.
   */
  @Pointcut("execution(* *..getDebugProperties(..))")
  private void getDebugPropertiesMethod()
  {
  }

  /**
   * Pointcut for matching debugMessage() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugMessage(..))")
  private void logMessageMethod()
  {
  }

  /**
   * Pointcut for matching debugVerbose() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugVerbose(..))")
  private void logVerboseMethod()
  {
  }

  /**
   * Pointcut for matching debugInfo() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugInfo(..))")
  private void logInfoMethod()
  {
  }

  /**
   * Pointcut for matching debugWarning() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugWarning(..))")
  private void logWarningMethod()
  {
  }

  /**
   * Pointcut for matching debugError() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugError(..))")
  private void logErrorMethod()
  {
  }

  /**
   * Pointcut for matching debugThrown() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugThrown(..))")
  private void logThrownMethod()
  {
  }

  /**
   * Pointcut for matching debugCaught() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugCaught(..))")
  private void logCaughtMethod()
  {
  }

  /**
   * Pointcut for matching debugJEAccess() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugJEAccess(..))")
  private void logJEAccessMethod()
  {
  }

  /**
   * Pointcut for matching debugData() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugData(..))")
  private void logDataMethod()
  {
  }

  /**
   * Pointcut for matching debugProtocolElement() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugProtocolElement(..))")
  private void logProtocolElementMethod()
  {
  }

  /**
   * Pointcut for matching all debug logging methods.
   */
  @Pointcut("logMessageMethod() || logVerboseMethod() || logInfoMethod() || " +
      "logWarningMethod() || logErrorMethod() || logCaughtMethod() || " +
      "logJEAccessMethod() || logDataMethod() || logProtocolElementMethod()")
  private void logMethods()
  {
  }

  /**
   * Pointcut to exclude all pointcuts which should not be adviced by the
   * debug logger.
   */
  @Pointcut("toStringMethod() || getMessageMethod() || " +
      "getDebugPropertiesMethod() || logMethods()")
  private void excluded()
  {
  }

  /**
   * Pointcut for matching the execution of all public methods.
   */
  @Pointcut("execution(!@(DebugLogger.NoDebugTracing || " +
      "DebugLogger.NoEntryDebugTracing) public * *(..)) && " +
      "!excluded()")
  void tracedEntryMethod()
  {
  }

  /**
   * Pointcut for matching the execution of all public methods.
   */
  @Pointcut("execution(!@(DebugLogger.NoDebugTracing || " +
      "DebugLogger.NoExitDebugTracing) public * *(..)) && " +
      "!excluded()")
  void tracedExitMethod()
  {
  }

  /**
   * Pointcut for matching the execution of all public methods.
   */
  @Pointcut("execution(@DebugLogger.TraceThrown public * *(..)) && " +
      "!excluded()")
  void tracedThrownMethod()
  {
  }

  /**
   * Pointcut for matching the execution of all constructors.
   */
  @Pointcut("execution(!@(DebugLogger.NoDebugTracing || " +
      "DebugLogger.NoEntryDebugTracing) public new(..)) && !excluded()")
  void tracedEntryConstructor()
  {
  }

  /**
   * Pointcut for matching the execution of all constructors.
   */
  @Pointcut("execution(!@(DebugLogger.NoDebugTracing || " +
      "DebugLogger.NoExitDebugTracing) public new(..)) && !excluded()")
  void tracedExitConstructor()
  {
  }

  /**
   * Pointcut for matching only if there are publishers.
   *
   * @return if debug logging is enabled.
   */
  @Pointcut("if()")
  public static boolean shouldTrace()
  {
    return DebugLogger.enabled;
  }

  // The class this aspect traces.
  String className;

  //The current class level trace settings.
  ConcurrentHashMap<DebugLogPublisher, TraceSettings> classSettings =
      new ConcurrentHashMap<DebugLogPublisher, TraceSettings>();

  //The current method level trace settings.
  ConcurrentHashMap<DebugLogPublisher, Map<String, TraceSettings>>
      methodSettings = new ConcurrentHashMap<DebugLogPublisher,
                           Map<String, TraceSettings>>();


  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   */
  @SuppressWarnings("unchecked")
  @Before("staticinitialization(*)")
  public void initializeTracer(JoinPoint.StaticPart thisJoinPointStaticPart)
  {
    classTracers.add(this);
    className = thisJoinPointStaticPart.getSignature().getDeclaringTypeName();

    // Get the settings from all publishers.
    for(DebugLogPublisher logPublisher :
        debugPublishers.values())
    {
      TraceSettings cSettings = logPublisher.getClassSettings(className);
      classSettings.put(logPublisher, cSettings);

      // For some reason, the compiler doesn't see that
      // debugLogPublihser.getMethodSettings returns a parameterized Map.
      // This problem goes away if a parameterized verson of DebugLogPublisher
      // is used. However, we can't not use reflection to instantiate a generic
      // DebugLogPublisher<? extends DebugLogPublisherCfg> type. The only thing
      // we can do is to just supress the compiler warnings.
      Map<String, TraceSettings> mSettings =
          logPublisher.getMethodSettings(className);
      if(mSettings != null)
      {
        methodSettings.put(logPublisher, mSettings);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   */
  @Before("shouldTrace() && tracedEntryConstructor()")
  public void traceConstructor(JoinPoint thisJoinPoint)
  {
    String signature = thisJoinPoint.getSignature().getName();
    String sl = thisJoinPoint.getSourceLocation().toString();
    Object[] args = thisJoinPoint.getArgs();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DEFAULT_CONSTRUCTOR_LEVEL.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.CONSTRUCTOR).intValue())
      {
        logPublisher.traceConstructor(DebugLogger.DEFAULT_CONSTRUCTOR_LEVEL,
                                   settings, signature, sl, args);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   * @param obj the object this method operations on.
   */
  @Before("shouldTrace() && tracedEntryMethod() && nonStaticContext(obj)")
  public void traceNonStaticMethodEntry(JoinPoint thisJoinPoint, Object obj)
  {
    String signature = thisJoinPoint.getSignature().getName();
    String sl = thisJoinPoint.getSourceLocation().toString();
    Object[] args = thisJoinPoint.getArgs();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DEFAULT_ENTRY_EXIT_LEVEL.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.ENTER).intValue())
      {
        logPublisher.traceNonStaticMethodEntry(DEFAULT_ENTRY_EXIT_LEVEL,
                                               settings, signature, sl, obj,
                                               args);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   */
  @Before("shouldTrace() && tracedEntryMethod() && staticContext()")
  public void traceStaticMethodEntry(JoinPoint thisJoinPoint)
  {
    String signature = thisJoinPoint.getSignature().getName();
    String sl = thisJoinPoint.getSourceLocation().toString();
    Object[] args = thisJoinPoint.getArgs();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DEFAULT_ENTRY_EXIT_LEVEL.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.ENTER).intValue())
      {
        logPublisher.traceStaticMethodEntry(DEFAULT_ENTRY_EXIT_LEVEL, settings,
                                         signature, sl, args);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param ret the return value of the method.
   */
  @AfterReturning(pointcut = "shouldTrace() && " +
      "(tracedExitMethod() || tracedExitConstructor())",
                  returning = "ret")
  public void traceReturn(JoinPoint.StaticPart thisJoinPointStaticPart,
                          Object ret)
  {
    String signature = thisJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DEFAULT_ENTRY_EXIT_LEVEL.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.EXIT).intValue())
      {
        logPublisher.traceReturn(DEFAULT_ENTRY_EXIT_LEVEL, settings, signature,
                                 sl, ret);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param ex the exception thrown.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @AfterThrowing(pointcut = "shouldTrace() && tracedThrownMethod()",
                 throwing = "ex")
  public void traceThrown(JoinPoint.StaticPart thisJoinPointStaticPart,
                          Throwable ex)
  {
    String signature = thisJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DEFAULT_THROWN_LEVEL.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.THROWN).intValue())
      {
        logPublisher.traceThrown(DEFAULT_THROWN_LEVEL, settings, signature, sl,
                              ex);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param ex the exception thrown.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logThrownMethod() && args(level, ex)")
  public void traceThrown(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                          JoinPoint.StaticPart
                              thisJoinPointStaticPart,
                          LogLevel level, Throwable ex)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.THROWN).intValue())
      {
        logPublisher.traceThrown(level, settings, signature, sl, ex);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logVerboseMethod() && args(msg)")
  public void traceVerbose(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                           JoinPoint.StaticPart
                               thisJoinPointStaticPart,
                           String msg)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.VERBOSE.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.VERBOSE, settings, signature,
                                  sl, msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logVerboseMethod() && args(msg, msgArgs)")
  public void traceVerbose(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                           JoinPoint.StaticPart
                               thisJoinPointStaticPart,
                           String msg, Object[] msgArgs)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    msg = DebugMessageFormatter.format(msg, msgArgs);
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.VERBOSE.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.VERBOSE, settings, signature,
                                  sl, msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logInfoMethod() && args(msg)")
  public void traceInfo(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                        JoinPoint.StaticPart
                            thisJoinPointStaticPart,
                        String msg)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.INFO.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.INFO, settings, signature, sl,
                               msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logInfoMethod() && args(msg, msgArgs)")
  public void traceInfo(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                        JoinPoint.StaticPart
                            thisJoinPointStaticPart,
                        String msg, Object[] msgArgs)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    msg = DebugMessageFormatter.format(msg, msgArgs);
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.INFO.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.INFO, settings, signature, sl,
                               msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logWarningMethod() && args(msg)")
  public void traceWarning(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                           JoinPoint.StaticPart
                               thisJoinPointStaticPart,
                           String msg)

  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.WARNING.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.WARNING, settings, signature,
                                  sl, msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logWarningMethod() && args(msg, msgArgs)")
  public void traceWarning(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                           JoinPoint.StaticPart
                               thisJoinPointStaticPart,
                           String msg, Object[] msgArgs)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    msg = DebugMessageFormatter.format(msg, msgArgs);
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.WARNING.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.WARNING, settings, signature,
                                  sl, msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logErrorMethod() && args(msg)")
  public void traceError(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                         JoinPoint.StaticPart
                             thisJoinPointStaticPart,
                         String msg)

  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.ERROR.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.ERROR, settings, signature, sl,
                               msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logErrorMethod() && args(msg, msgArgs)")
  public void traceError(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                         JoinPoint.StaticPart
                             thisJoinPointStaticPart,
                         String msg, Object[] msgArgs)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    msg = DebugMessageFormatter.format(msg, msgArgs);
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (DebugLogLevel.ERROR.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(DebugLogLevel.ERROR, settings, signature, sl,
                                  msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param msg message to format and log.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logMessageMethod() && args(level, msg)")
  public void traceMessage(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                           JoinPoint.StaticPart
                               thisJoinPointStaticPart,
                           LogLevel level, String msg)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(level, settings, signature, sl, msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logMessageMethod() && args(level, msg, msgArgs)")
  public void traceMessage(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                           JoinPoint.StaticPart
                               thisJoinPointStaticPart,
                           LogLevel level, String msg, Object... msgArgs)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    msg = DebugMessageFormatter.format(msg, msgArgs);
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.MESSAGE).intValue())
      {
        logPublisher.traceMessage(level, settings, signature, sl, msg);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param ex the exception caught.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logCaughtMethod() && args(level, ex)")
  public void traceCaught(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                          JoinPoint.StaticPart
                              thisJoinPointStaticPart,
                          LogLevel level, Throwable ex)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.CAUGHT).intValue())
      {
        logPublisher.traceCaught(level, settings, signature, sl, ex);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param status status of the JE operation.
   * @param database the database handle.
   * @param txn  transaction handle (may be null).
   * @param key  the key to dump.
   * @param data the data to dump.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logJEAccessMethod() && args(level, status, " +
      "database, txn, key, data)")
  public void traceJEAccess(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                            JoinPoint.StaticPart
                                thisJoinPointStaticPart,
                            LogLevel level, OperationStatus status,
                            Database database, Transaction txn,
                            DatabaseEntry key, DatabaseEntry data)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings,
                            DebugLogCategory.DATABASE_ACCESS).intValue())
      {
        logPublisher.traceJEAccess(level, settings, signature, sl, status,
                                database, txn, key, data);
      }
    }
  }

  /**
   * AspectJ Implementation.
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param data the data to dump.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logDataMethod() && args(level, data)")
  public void traceData(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                        JoinPoint.StaticPart
                            thisJoinPointStaticPart,
                        LogLevel level, byte[] data)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.DATA).intValue())
      {
        logPublisher.traceData(level, settings, signature, sl, data);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param element the protocol element to dump.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logProtocolElementMethod() && args(level, element)")
  public void traceProtocolElement(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                                   JoinPoint.StaticPart
                                       thisJoinPointStaticPart,
                                   LogLevel level, ProtocolElement element)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.PROTOCOL).intValue())
      {
        logPublisher.traceProtocolElement(level, settings, signature, sl,
                                          element);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object
   *                                         of the code that contains the
   *                                         debug call.
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param buffer the data to dump.
   */
  @SuppressAjWarnings({"adviceDidNotMatch"})
  @Around("shouldTrace() && logDataMethod() && args(level, buffer)")
  public void traceData(JoinPoint.EnclosingStaticPart
      thisEnclosingJoinPointStaticPart,
                        JoinPoint.StaticPart
                            thisJoinPointStaticPart,
                        LogLevel level, ByteBuffer buffer)
  {
    String signature =
        thisEnclosingJoinPointStaticPart.getSignature().getName();
    String sl = thisJoinPointStaticPart.getSourceLocation().toString();
    for(DebugLogPublisher logPublisher : debugPublishers.values())
    {
      TraceSettings settings = getSettings(logPublisher, signature);
      if (level.intValue() >=
          getEffectiveLevel(settings, DebugLogCategory.DATA).intValue())
      {
        logPublisher.traceData(level, settings, signature, sl, buffer.array());
      }
    }
  }

  /**
   * Get the current trace settings in effect for the specified method.
   *
   * @param debugLogPublisher - the debug publisher to get the trace settings
   *                            from.
   * @param method - the method to get trace settings for
   * @return the current trace settings in effect
   */
  protected final TraceSettings getSettings(DebugLogPublisher debugLogPublisher,
                                            String method)
  {
    TraceSettings settings = this.classSettings.get(debugLogPublisher);

    Map<String, TraceSettings> methodSettings =
        this.methodSettings.get(debugLogPublisher);
    if (methodSettings != null)
    {
      TraceSettings mSettings = methodSettings.get(method);

      if (mSettings == null)
      {
        // Try looking for an undecorated method name
        int idx = method.indexOf('(');
        if (idx != -1)
        {
          mSettings =
              methodSettings.get(method.substring(0, idx));
        }
      }

      if (mSettings != null) settings = mSettings;
    }

    return settings;
  }

  /**
   * Retrieve the current log level given the trace settings and log category.
   *
   * @param settings the trace settings to test from.
   * @param category the log category to test.
   * @return the effective log level.
   */
  protected final LogLevel getEffectiveLevel(TraceSettings settings,
                                             LogCategory category)
  {
    if (settings == null)
    {
      return DebugLogLevel.DISABLED;
    }

    LogLevel level = settings.level;
    Set<LogCategory> includedCategories = settings.includeCategories;

    if(includedCategories != null &&
        !includedCategories.contains(category))
    {
      level = DebugLogLevel.DISABLED;
    }

    return level;
  }
}
