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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.loggers.debug;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.SourceLocation;

import java.util.Map;
import java.nio.ByteBuffer;

import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.api.ProtocolElement;
import org.opends.server.loggers.*;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogLevel;
import com.sleepycat.je.*;

/**
 * An aspect for source-code tracing at the method level.
 *
 * One Tracer aspect instance exists for each Java class using tracing.
 * Tracer must be registered with the DebugLogger.
 *
 * Logging is always done at a level basis, with debug log messages
 * exceeding the trace threshold being traced, others being discarded.
 */
@Aspect("pertypewithin(*)")
public class Tracer
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
  @Pointcut("execution(String *..toString())")
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
   * Pointcut for matching debugCought() methods.
   */
  @Pointcut("call(public static void org.opends.server." +
      "loggers.debug.DebugLogger.debugCought(..))")
  private void logCoughtMethod()
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
      "logWarningMethod() || logErrorMethod() || logCoughtMethod() || " +
      "logJEAccessMethod() || logDataMethod() || logProtocolElementMethod()")
  private void logMethods()
  {
  }

  /**
   * Pointcut to exclude all pointcuts which should not be adviced by the
   * debug logger.
   */
  @Pointcut("within(Tracer+) || within(org.opends.server.loggers.debug..*) " +
      "|| toStringMethod() " +
      "|| getMessageMethod() || logMethods()")
  private void excluded()
  {
  }

  /**
   * Pointcut for matching the execution of all public methods.
   */
  @Pointcut("execution(public * *(..)) && !excluded()")
  void tracedMethod()
  {
  }

  /**
   * Pointcut for matching the execution of all constructors.
   */
  @Pointcut("execution(public new(..)) && !excluded()")
  void tracedConstructor()
  {
  }

  /**
   * Pointcut for matching only if tracing is enabled.
   *
   * @return if debug logging is enabled.
   */
  @Pointcut("if() && tracingScope()")
  public static boolean shouldTrace()
  {
    return DebugLogger.enabled;
  }

  /**
   * Pointcut for matching only within the scope of the server packages.
   */
  @Pointcut("within(org.opends.server..*)")
  protected void tracingScope()
  {
  }

  //The default level to log constructor exectuions.
  private static final LogLevel DEFAULT_CONSTRUCTOR_LEVEL =
      DebugLogLevel.VERBOSE;
  //The default level to log method entry and exit pointcuts.
  private static final LogLevel DEFAULT_ENTRY_EXIT_LEVEL =
      DebugLogLevel.VERBOSE;

  // The class this tracer traces.
  private String className;

  //The current settings for this tracer.
  private TraceSettings settings;
  private Map<String, TraceSettings> methodSettings;

  // The DebugLogger this tracer is genereated by.
  private DebugLogger logger;

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   */
  @Before("staticinitialization(*) && tracingScope()")
  public void initializeTracer(JoinPoint.StaticPart thisJoinPointStaticPart)
  {
    className = thisJoinPointStaticPart.getSignature().getDeclaringTypeName();
    logger = DebugLogger.getLogger();
    logger.registerTracer(className, this);
    updateSettings();
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   */
  @Before("shouldTrace() && tracedConstructor()")
  public void traceConstructor(JoinPoint thisJoinPoint)
  {
    LogCategory category = DebugLogCategory.CONSTRUCTOR;
    LogLevel level = DEFAULT_CONSTRUCTOR_LEVEL;
    Signature signature = thisJoinPoint.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >= getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisJoinPoint.getSourceLocation();
      Object[] args = thisJoinPoint.getArgs();
      publish(category, level, signature.toLongString(), sl.toString(), null,
              null, args, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   * @param obj the object this method operations on.
   */
  @Before("shouldTrace() && tracedMethod() && nonStaticContext(obj)")
  public void traceNonStaticMethodEntry(JoinPoint thisJoinPoint, Object obj)
  {
    LogCategory category = DebugLogCategory.ENTER;
    LogLevel level = DEFAULT_ENTRY_EXIT_LEVEL;
    Signature signature = thisJoinPoint.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >= getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisJoinPoint.getSourceLocation();
      Object[] args = thisJoinPoint.getArgs();
      publish(category, level, signature.toLongString(), sl.toString(), obj,
              null, args, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   */
  @Before("shouldTrace() && tracedMethod() && staticContext()")
  public void traceStaticMethodEntry(JoinPoint thisJoinPoint)
  {
    LogCategory category = DebugLogCategory.ENTER;
    LogLevel level = DEFAULT_ENTRY_EXIT_LEVEL;
    Signature signature = thisJoinPoint.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >= getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisJoinPoint.getSourceLocation();
      Object[] args = thisJoinPoint.getArgs();
      publish(category, level, signature.toLongString(), sl.toString(), null,
              null, args, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPointStaticPart the JoinPoint reflection object.
   * @param ret the return value of the method.
   */
  @AfterReturning(pointcut = "shouldTrace() && tracedMethod()",
                  returning = "ret")
  public void traceReturn(JoinPoint.StaticPart thisJoinPointStaticPart,
                          Object ret)
  {
    LogCategory category = DebugLogCategory.EXIT;
    LogLevel level = DEFAULT_ENTRY_EXIT_LEVEL;
    Signature signature = thisJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(), sl.toString(), null,
              null, new Object[]{ret}, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @Around("shouldTrace() && logVerboseMethod() && args(msg)")
  public void traceVerbose(JoinPoint.EnclosingStaticPart
                             thisEnclosingJoinPointStaticPart, String msg)
  {
    LogLevel level = DebugLogLevel.VERBOSE;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, null, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @Around("shouldTrace() && logVerboseMethod() && args(msg, msgArgs)")
  public void traceVerbose(JoinPoint.EnclosingStaticPart
                             thisEnclosingJoinPointStaticPart,
                           String msg, Object[] msgArgs)
  {
    LogLevel level = DebugLogLevel.VERBOSE;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, msgArgs, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @Around("shouldTrace() && logInfoMethod() && args(msg)")
  public void traceInfo(JoinPoint.EnclosingStaticPart
                          thisEnclosingJoinPointStaticPart, String msg)
  {
    LogLevel level = DebugLogLevel.INFO;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, null, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @Around("shouldTrace() && logInfoMethod() && args(msg, msgArgs)")
  public void traceInfo(JoinPoint.EnclosingStaticPart
                          thisEnclosingJoinPointStaticPart,
                        String msg, Object[] msgArgs)
  {
    LogLevel level = DebugLogLevel.INFO;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, msgArgs, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @Around("shouldTrace() && logWarningMethod() && args(msg)")
  public void traceWarning(JoinPoint.EnclosingStaticPart
                             thisEnclosingJoinPointStaticPart, String msg)

  {
    LogLevel level = DebugLogLevel.WARNING;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, null, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @Around("shouldTrace() && logWarningMethod() && args(msg, msgArgs)")
  public void traceWarning(JoinPoint.EnclosingStaticPart
                             thisEnclosingJoinPointStaticPart,
                           String msg, Object[] msgArgs)
  {
    LogLevel level = DebugLogLevel.WARNING;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, msgArgs, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   */
  @Around("shouldTrace() && logErrorMethod() && args(msg)")
  public void traceError(JoinPoint.EnclosingStaticPart
                           thisEnclosingJoinPointStaticPart, String msg)

  {
    LogLevel level = DebugLogLevel.ERROR;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, null, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @Around("shouldTrace() && logErrorMethod() && args(msg, msgArgs)")
  public void traceError(JoinPoint.EnclosingStaticPart
                           thisEnclosingJoinPointStaticPart,
                         String msg, Object[] msgArgs)
  {
    LogLevel level = DebugLogLevel.ERROR;
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(),
              sl.toString(), null, msg, msgArgs, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param msg message to format and log.
   */
  @Around("shouldTrace() && logMessageMethod() && args(level, msg)")
  public void traceMessage(JoinPoint.EnclosingStaticPart
                             thisEnclosingJoinPointStaticPart,
                           LogLevel level, String msg)
  {
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(), sl.toString(),
              null, msg, null, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param msg message to format and log.
   * @param msgArgs arguments to place into the format string.
   */
  @Around("shouldTrace() && logMessageMethod() && args(level, msg, msgArgs)")
  public void traceMessage(JoinPoint.EnclosingStaticPart
                             thisEnclosingJoinPointStaticPart,
                           LogLevel level, String msg, Object... msgArgs)
  {
    LogCategory category = DebugLogCategory.MESSAGE;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(), sl.toString(),
              null, msg, msgArgs, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param ex the exception thrown.
   */
  @Around("shouldTrace() && logThrownMethod() && args(level, ex)")
  public void traceThrown(JoinPoint.EnclosingStaticPart
                            thisEnclosingJoinPointStaticPart,
                          LogLevel level, Throwable ex)
  {
    LogCategory category = DebugLogCategory.THROWN;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(), sl.toString(),
              null, null , new Object[]{ex}, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param ex the exception cought.
   */
  @Around("shouldTrace() && logCoughtMethod() && args(level, ex)")
  public void traceCought(JoinPoint.EnclosingStaticPart
                            thisEnclosingJoinPointStaticPart,
                          LogLevel level, Throwable ex)
  {
    LogCategory category = DebugLogCategory.COUGHT;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(), sl.toString(),
              null, null , new Object[]{ex}, settings);
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param status status of the JE operation.
   * @param database the database handle.
   * @param txn  transaction handle (may be null).
   * @param key  the key to dump.
   * @param data the data to dump.
   */
  @Around("shouldTrace() && logJEAccessMethod() && args(level, status, " +
      "database, txn, key, data)")
  public void traceJEAccess(JoinPoint.EnclosingStaticPart
                              thisEnclosingJoinPointStaticPart,
                            LogLevel level, OperationStatus status,
                            Database database, Transaction txn,
                            DatabaseEntry key, DatabaseEntry data)
  {
    LogCategory category = DebugLogCategory.DATABASE_ACCESS;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {

      // Build the string that is common to category DATABASE_ACCESS.
      StringBuilder builder = new StringBuilder();
      builder.append(" (");
      builder.append(status.toString());
      builder.append(")");
      builder.append(" db=");
      try
      {
        builder.append(database.getDatabaseName());
      }
      catch(DatabaseException de)
      {
        builder.append(de.toString());
      }
      if (txn != null)
      {
        builder.append(" txnid=");
        try
        {
          builder.append(txn.getId());
        }
        catch(DatabaseException de)
        {
          builder.append(de.toString());
        }
      }
      else
      {
        builder.append(" txnid=none");
      }

      // If the operation was successful we log the same common information
      // plus the key and data under category DATABASE_READ or DATABASE_WRITE
      if (status == OperationStatus.SUCCESS)
      {
        builder.append(ServerConstants.EOL);
        builder.append(" key:");
        builder.append(ServerConstants.EOL);
        StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
        if (data != null)
        {
          builder.append("data(len=");
          builder.append(data.getSize());
          builder.append("):");
          builder.append(ServerConstants.EOL);
          StaticUtils.byteArrayToHexPlusAscii(builder, data.getData(), 4);
        }
      }


      SourceLocation sl = thisEnclosingJoinPointStaticPart.getSourceLocation();
      publish(category, level, signature.toLongString(), sl.toString(), null,
              builder.toString(), null, settings);
    }
  }

  /**
   * AspectJ Implementation.
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param data the data to dump.
   */
  @Around("shouldTrace() && logDataMethod() && args(level, data)")
  public void traceData(JoinPoint.EnclosingStaticPart
                          thisEnclosingJoinPointStaticPart,
                        LogLevel level, byte[] data)
  {
    LogCategory category = DebugLogCategory.DATA;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      if(data != null)
      {
        StringBuilder builder = new StringBuilder();
        builder.append(ServerConstants.EOL);
        builder.append("data(len=");
        builder.append(data.length);
        builder.append("):");
        builder.append(ServerConstants.EOL);
        StaticUtils.byteArrayToHexPlusAscii(builder, data, 4);
        SourceLocation sl =
            thisEnclosingJoinPointStaticPart.getSourceLocation();
        publish(category, level, signature.toLongString(), sl.toString(), null,
                builder.toString(), null, settings);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param element the protocol element to dump.
   */
  @Around("shouldTrace() && logProtocolElementMethod() && args(level, element)")
  public void traceProtocolElement(JoinPoint.EnclosingStaticPart
                                     thisEnclosingJoinPointStaticPart,
                                   LogLevel level, ProtocolElement element)
  {
    LogCategory category = DebugLogCategory.PROTOCOL;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      if(element != null)
      {
        StringBuilder builder = new StringBuilder();
        builder.append(ServerConstants.EOL);
        element.toString(builder, 4);
        SourceLocation sl =
            thisEnclosingJoinPointStaticPart.getSourceLocation();
        publish(category, level, signature.toLongString(), sl.toString(), null,
                builder.toString(), null, settings);
      }
    }
  }

  /**
   * AspectJ Implementation.
   *
   * @param thisEnclosingJoinPointStaticPart the JoinPoint reflection object.
   * @param level the level of the log message.
   * @param buffer the data to dump.
   */
  @Around("shouldTrace() && logDataMethod() && args(level, buffer)")
  public void traceData(JoinPoint.EnclosingStaticPart
                          thisEnclosingJoinPointStaticPart,
                        LogLevel level, ByteBuffer buffer)
  {
    LogCategory category = DebugLogCategory.DATA;
    Signature signature = thisEnclosingJoinPointStaticPart.getSignature();
    TraceSettings settings = getSettings(signature.getName());
    if (level.intValue() >=
        getEffectiveLevel(settings, category).intValue())
    {
      if(buffer != null)
      {
        byte[] data = buffer.array();
        StringBuilder builder = new StringBuilder();
        builder.append(ServerConstants.EOL);
        builder.append("data(len=");
        builder.append(data.length);
        builder.append("):");
        builder.append(ServerConstants.EOL);
        StaticUtils.byteArrayToHexPlusAscii(builder, data, 4);
        SourceLocation sl =
            thisEnclosingJoinPointStaticPart.getSourceLocation();
        publish(category, level, signature.toLongString(), sl.toString(), null,
                builder.toString(), null, settings);
      }
    }
  }

  // Publishes a record, optionally performing some "special" work:
  // - injecting a stack trace into the message
  // - format the message with argument values
  private void publish(LogCategory category, LogLevel level,
                       String method, String srcLocation, Object srcObject,
                       String msg, Object[] msgArgs, TraceSettings settings)
  {
    int stackDepth = 0;

    if(DebugLogCategory.ENTER.equals(category))
    {
      if (settings.noArgs)
      {
        msgArgs = null;
      }
      else if(msg == null)
      {
        msg = buildDefaultEntryMessage(msgArgs.length);
      }

      stackDepth = settings.stackDepth;
    }
    if(DebugLogCategory.EXIT.equals(category))
    {
      if (settings.noRetVal)
      {
        msgArgs = null;
      }
      else if(msg == null)
      {
        msg = "returned={%s}";
      }
    }
    if(DebugLogCategory.THROWN.equals(category))
    {
      if (msg == null)
      {
        msg = "threw={%s}";
      }
      stackDepth = settings.stackDepth;
    }

    if (msg != null && msgArgs != null)
    {
      msg = String.format(msg, msgArgs);
    }


    DebugLogRecord record = new DebugLogRecord(category, level, srcObject,
                                               logger, msg);
    record.setSignature(method);
    record.setSourceLocation(srcLocation);

    // Inject a stack trace if requested
    if (stackDepth > 0) {

      //Generate a dummy exception to get stack trace if necessary
      Throwable t;
      if(!DebugLogCategory.THROWN.equals(category) || msgArgs == null
          || msgArgs[0] == null)
      {
       t= new NullPointerException();
      }
      else
      {
        t = (Throwable)msgArgs[0];
      }

      String stack=
          DebugStackTraceFormatter.formatStackTrace(t,
                                   DebugStackTraceFormatter.SMART_FRAME_FILTER,
                                   stackDepth, settings.includeCause);
      if (stack != null) record.setStackTrace(stack);
    }

    logger.publishRecord(record);
  }

  // Publishes a record with a message ID
  private void publish(LogCategory category, LogLevel level,
                      String method, String srcLocation, Object srcObject,
                      int msgID, Object[] msgArgs, TraceSettings settings)
  {
    String msg = getMessage(msgID);

    publish(category, level, method, srcLocation, srcObject, msg,
            msgArgs, settings);
  }

  /**
   * Get the current trace settings in effect for the specified method.
   *
   * @param method - the method to get trace settings for
   * @return the current trace settings in effect
   */
  protected TraceSettings getSettings(String method)
  {
    TraceSettings settings = this.settings;

    if (methodSettings != null)
    {
      TraceSettings mSettings = methodSettings.get(method);

      if (mSettings == null)
      {
        // Try looking for an undecorated method name
        int idx = method.indexOf('(');
        if (idx != -1)
        {
          mSettings = methodSettings.get(method.substring(0, idx));
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
  protected LogLevel getEffectiveLevel(TraceSettings settings,
                                       LogCategory category)
  {
    LogLevel level = settings.level;

    if(settings.includeCategories != null &&
        !settings.includeCategories.contains(category))
    {
      level = DebugLogLevel.DISABLED;
    }

    return level;
  }

  private static String buildDefaultEntryMessage(int argCount)
  {
    StringBuffer format = new StringBuffer();
    for (int i = 0; i < argCount; i++)
    {
      if (i != 0) format.append(", ");
      format.append("arg");
      format.append(i + 1);
      format.append("={");
      format.append("%s");
      format.append("}");
    }

    return format.toString();
  }

  /**
   * Update the settings for this tracer.
   */
  protected void updateSettings()
  {
    synchronized (this)
    {
      this.settings =
          logger.getConfiguration().getTraceSettings(className);
      this.methodSettings =
          logger.getConfiguration().getMethodSettings(className);
    }
  }

  /**
   * Indicates whether a method is traced at the specified level.
   *
   * @param level  - the trace level to test.
   * @param category - the category to test.
   * @param method - the method to test.
   * @return <b>true</b> if the logger would trace a message for the
   *         method at the level, <b>false</b> otherwise.
   */
  public boolean isLogging(LogLevel level, LogCategory category, String method)
  {
    return level.intValue() >= getLevel(method, category).intValue();
  }

  /**
   * Get the current trace level for the specified method.
   *
   * @param method the method to get the trace level for.
   * @param category the category to get the trace level for.
   * @return the current trace level for the method.
   */
  protected LogLevel getLevel(String method, LogCategory category)
  {
    TraceSettings settings = getSettings(method);
    return getEffectiveLevel(settings, category);
  }

}
