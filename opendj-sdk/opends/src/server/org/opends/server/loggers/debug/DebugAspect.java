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

import static
    org.opends.server.loggers.debug.DebugLogger.DEFAULT_CONSTRUCTOR_LEVEL;
import static
    org.opends.server.loggers.debug.DebugLogger.DEFAULT_ENTRY_EXIT_LEVEL;
import static
    org.opends.server.loggers.debug.DebugLogger.DEFAULT_THROWN_LEVEL;

/**
 * An aspect for source-code tracing at the method level.
 *
 * One DebugLogger aspect instance exists for each Java class using tracing.
 * Tracer must be registered with the DebugLogger.
 *
 * Logging is always done at a level basis, with debug log messages
 * exceeding the trace threshold being traced, others being discarded.
 */
@Aspect()
public class DebugAspect
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
   * Pointcut for matching the getDirectoryThreadGroup method.
   */
  @Pointcut("execution(public static ThreadGroup org.opends.server." +
      "core.DirectoryServer.getDirectoryThreadGroup(..))")
  private void getThreadGroupMethod()
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
   * Pointcut for matching all logging related classes.
   */
  @Pointcut("within(org.opends.server.loggers.*+ || " +
    "org.opends.server.loggers.debug..*+ || " +
    "org.opends.server.types.DebugLogLevel+ || " +
    "org.opends.server.types.DebugLogCategory+ || " +
    "org.opends.server.api.DebugLogPublisher+ || " +
    "org.opends.server.util.TimeThread+ ||" +
    "org.opends.server.util.MultiOutputStream+)")
  private void debugRelatedClasses()
  {
  }

  /**
   * Pointcut to exclude all pointcuts which should not be adviced by the
   * debug logger.
   */
  @Pointcut("toStringMethod() || getMessageMethod() || " +
      "getDebugPropertiesMethod() || debugRelatedClasses() || " +
      "getThreadGroupMethod()")
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

  /**
   * AspectJ Implementation.
   *
   * @param thisJoinPoint the JoinPoint reflection object.
   */
  @Before("shouldTrace() && tracedEntryConstructor()")
  public void traceConstructor(JoinPoint thisJoinPoint)
  {
    String signature =
        thisJoinPoint.getSignature().getDeclaringTypeName();
    Object[] args = thisJoinPoint.getArgs();
    DebugTracer tracer = DebugLogger.getTracer(signature);
    if(tracer != null)
    {
      tracer.debugConstructor(
          DEFAULT_CONSTRUCTOR_LEVEL, args);
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
    String signature =
        thisJoinPoint.getSignature().getDeclaringTypeName();
    Object[] args = thisJoinPoint.getArgs();
    Object callerInstance = thisJoinPoint.getThis();
    DebugTracer tracer = DebugLogger.getTracer(signature);
    if(tracer != null)
    {
      tracer.debugMethodEntry(
          DEFAULT_ENTRY_EXIT_LEVEL, callerInstance, args);
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
    String signature =
        thisJoinPoint.getSignature().getDeclaringTypeName();
    Object[] args = thisJoinPoint.getArgs();
    DebugTracer tracer = DebugLogger.getTracer(signature);
    if(tracer != null)
    {
      tracer.debugStaticMethodEntry(
          DEFAULT_ENTRY_EXIT_LEVEL, args);
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
    String signature =
        thisJoinPointStaticPart.getSignature().getDeclaringTypeName();
    DebugTracer tracer = DebugLogger.getTracer(signature);
    if(tracer != null)
    {
      tracer.debugReturn(
          DEFAULT_ENTRY_EXIT_LEVEL, ret);
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
    String signature =
        thisJoinPointStaticPart.getSignature().getDeclaringTypeName();
    DebugTracer tracer = DebugLogger.getTracer(signature);
    if(tracer != null)
    {
      tracer.debugThrown(
          DEFAULT_THROWN_LEVEL, ex);
    }
  }
}
