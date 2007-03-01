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

import org.opends.server.api.ProtocolElement;
import org.opends.server.api.LogPublisher;
import org.opends.server.loggers.Logger;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogRecord;

import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;

import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Database;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.DatabaseEntry;

/**
 * A logger for debug and trace logging. DebugLogger provides a debugging
 * management access point. It is used to configure the Tracers, as well as
 * to register a per-class tracer.
 *
 * Various stub debug methods are provided to log different types of debug
 * messages. However, these methods do not contain any actual implementation.
 * Tracer aspects are later weaved to catch alls to these stub methods and
 * do the work of logging the message.
 *
 * DebugLogger is self-initializing.
 */
public class DebugLogger extends Logger
{
  /**
   * Whether the debug logger is enabled or disabled.
   */
  static boolean enabled = false;
  private static DebugLogger logger = null;

  private Map<String, Tracer> classTracers;
  private TraceConfiguration config;

  private DebugLogger()
  {
    super(new DebugErrorHandler());

    classTracers = new HashMap<String, Tracer>();
    config = new TraceConfiguration();
  }

  /**
   * Publish a record to all the registered publishers.
   *
   * @param record The log record to publish.
   */
  protected void publishRecord(LogRecord record)
  {
    for(LogPublisher p : publishers)
    {
      p.publish(record, handler);
    }
  }

  /**
   * Obtain the trace logger singleton.
   * @return the trace logger singleton.
   */
  public static synchronized DebugLogger getLogger()
  {
    if (logger == null) {
      logger= new DebugLogger();
    }

    return logger;
  }

  /**
   * Enable or disable the debug logger.
   *
   * @param enable if the debug logger should be enabled.
   */
  public static void enabled(boolean enable)
  {
    enabled = enable;
  }

  /**
   * Obtain the status of this logger singleton.
   *
   * @return the status of this logger.
   */
  public static boolean debugEnabled()
  {
    return enabled;
  }

  /**
   * Register a trace logger for the specified class.
   * @param className - the class for which to register the tracer under.
   * @param tracer - the tracer object to register.
   */
  public synchronized void registerTracer(String className,
                                           Tracer tracer)
  {
    Tracer traceLogger = classTracers.get(className);
    if (traceLogger == null) {
      classTracers.put(className, tracer);
    }
    else
    {
      //TODO: handle dup case!
    }
  }

  /**
   * Retrives the current tracing configuration of the debug logger.
   *
   * @return the current tracing configuration of the debug logger.
   */
  protected TraceConfiguration getConfiguration()
  {
    return config;
  }

  /**
   * Update the tracing configuration of the debug logger with the specified
   * trace configuration.
   *
   * @param config the new configuration to apply.
   */
  public void updateConfiguration(TraceConfiguration config)
  {
    this.config = config;

    for(Tracer tracer : classTracers.values())
    {
      tracer.updateSettings();
    }
  }

  /**
   * Stub method for logging an arbitrary event in a method at the INFO level.
   * Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugVerbose(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the INFO level.
   * Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugInfo(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the WARNING
   * level. Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugWarning(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the ERROR
   * level. Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugError(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the INFO
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugVerbose(String msg, Object... msgArgs) {}


  /**
   * Stub method for logging an arbitrary event in a method at the INFO
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugInfo(String msg, Object... msgArgs) {}


  /**
   * Stub method for logging an arbitrary event in a method at the WARNING
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugWarning(String msg, Object... msgArgs)
  {}


  /**
   * Stub method for logging an arbitrary event in a method at the ERROR
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugError(String msg, Object... msgArgs)
  {}


  /**
   * Stub method for logging an arbitrary event in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param msg   The message to be logged.
   */
  public static void debugMessage(LogLevel level, String msg)
  {}


  /**
   * Stub method for logging an arbitrary event in a method.
   * Implementation provided by AspectJ.
   *
   * @param level   The level of the message being logged.
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugMessage(LogLevel level, String msg,
                                  Object... msgArgs) {}


  /**
   * Stub method for logging a cought exception in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param t     The exception cought.
   */
  public static void debugCought(LogLevel level, Throwable t)
  {}

  /**
   * Stub method for logging a thrown exception in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param t     The exception being thrown.
   */
  public static void debugThrown(LogLevel level, Throwable t)
  {}


  /**
   * Stub method for logging an JE database access in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param status The JE return status code of the operation.
   * @param database The JE database handle operated on.
   * @param txn The JE transaction handle used in the operation.
   * @param key The database key operated on.
   * @param data The database value read or written.
   */
  public static void debugJEAccess(LogLevel level,
                                   OperationStatus status,
                                   Database database,
                                   Transaction txn,
                                   DatabaseEntry key, DatabaseEntry data) {}

  /**
   * Stub method for logging raw data in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param bytes The data to dump.
   */
  public static void debugData(LogLevel level, byte[] bytes) {}

  /**
   * Stub method for logging raw data in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param buffer The data to dump.
   */
  public static void debugData(LogLevel level, ByteBuffer buffer) {}

  /**
   * Stub method for logging a protocol element in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param element The protocol element to dump.
   */
  public static void debugProtocolElement(LogLevel level,
                                          ProtocolElement element) {}



}
