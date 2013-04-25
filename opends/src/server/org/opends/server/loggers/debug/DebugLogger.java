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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.loggers.debug;

import static org.opends.messages.ConfigMessages.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.std.meta.DebugLogPublisherCfgDefn;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.loggers.AbstractLogger;
import org.opends.server.loggers.LogLevel;
import org.opends.server.types.DebugLogLevel;

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
public class DebugLogger extends AbstractLogger
    <DebugLogPublisher<DebugLogPublisherCfg>, DebugLogPublisherCfg>
{
  /** The default level to log constructor executions. */
  static final LogLevel DEFAULT_CONSTRUCTOR_LEVEL =
      DebugLogLevel.VERBOSE;
  /** The default level to log method entry and exit pointcuts. */
  static final LogLevel DEFAULT_ENTRY_EXIT_LEVEL =
      DebugLogLevel.VERBOSE;
  /** The default level to log method entry and exit pointcuts. */
  static final LogLevel DEFAULT_THROWN_LEVEL =
      DebugLogLevel.ERROR;

  /** The set of all DebugTracer instances. */
  private static Map<String, DebugTracer> classTracers =
      new ConcurrentHashMap<String, DebugTracer>();

  /**
   * Trace methods will use this static boolean to determine if debug is enabled
   * so to not incur the cost of calling debugPublishers.isEmpty().
   */
  static boolean enabled = false;

  private static final LoggerStorage
      <DebugLogPublisher<DebugLogPublisherCfg>, DebugLogPublisherCfg>
      loggerStorage = new LoggerStorage
      <DebugLogPublisher<DebugLogPublisherCfg>, DebugLogPublisherCfg>();

  /** The singleton instance of this class for configuration purposes. */
  static final DebugLogger instance = new DebugLogger();

  /**
   * The constructor for this class.
   */
  public DebugLogger()
  {
    super((Class) DebugLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS);
  }

  /** {@inheritDoc} */
  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return DebugLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

  /** {@inheritDoc} */
  @Override
  protected LoggerStorage<DebugLogPublisher<DebugLogPublisherCfg>,
      DebugLogPublisherCfg> getStorage()
  {
    return loggerStorage;
  }

  /**
   * Add an debug log publisher to the debug logger.
   *
   * @param publisher The debug log publisher to add.
   */
  public synchronized static void addDebugLogPublisher(
      DebugLogPublisher publisher)
  {
    loggerStorage.addLogPublisher(publisher);

    updateTracerSettings();

    enabled = true;
  }

  /**
   * Remove an debug log publisher from the debug logger.
   *
   * @param publisher The debug log publisher to remove.
   * @return The publisher that was removed or null if it was not found.
   */
  public synchronized static boolean removeDebugLogPublisher(
      DebugLogPublisher publisher)
  {
    boolean removed = loggerStorage.removeLogPublisher(publisher);

    updateTracerSettings();

    enabled = !loggerStorage.getLogPublishers().isEmpty();

    return removed;
  }

  /**
   * Removes all existing debug log publishers from the logger.
   */
  public synchronized static void removeAllDebugLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();

    updateTracerSettings();

    enabled = false;
  }

  /**
   * Update all debug tracers with the settings in the registered
   * publishers.
   */
  static void updateTracerSettings()
  {
    DebugLogPublisher<DebugLogPublisherCfg>[] publishers =
        loggerStorage.getLogPublishers().toArray(new DebugLogPublisher[0]);

    for(DebugTracer tracer : classTracers.values())
    {
      tracer.updateSettings(publishers);
    }
  }

  /**
   * Indicates if debug logging is enabled.
   *
   * @return True if debug logging is enabled. False otherwise.
   */
  public static boolean debugEnabled()
  {
    return enabled;
  }

  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static DebugLogger getInstance()
  {
    return instance;
  }

  /**
   * Creates a new Debug Tracer for the caller class and registers it
   * with the Debug Logger.
   *
   * @return The tracer created for the caller class.
   */
  public static DebugTracer getTracer()
  {
    DebugTracer tracer =
        new DebugTracer(loggerStorage.getLogPublishers().toArray(
            new DebugLogPublisher[0]));
    classTracers.put(tracer.getTracedClassName(), tracer);

    return tracer;
  }

  /**
   * Returns the registered Debug Tracer for a traced class.
   *
   * @param className The name of the class tracer to retrieve.
   * @return The tracer for the provided class or null if there are
   *         no tracers registered.
   */
  public static DebugTracer getTracer(String className)
  {
    return classTracers.get(className);
  }

  /**
   * Classes and methods annotated with @NoDebugTracing will not be weaved with
   * debug logging statements by AspectJ.
   */
  public @interface NoDebugTracing {}

  /**
   * Methods annotated with @NoEntryDebugTracing will not be weaved with
   * entry debug logging statements by AspectJ.
   */
  public @interface NoEntryDebugTracing {}

  /**
   * Methods annotated with @NoExitDebugTracing will not be weaved with
   * exit debug logging statements by AspectJ.
   */
  public @interface NoExitDebugTracing {}

  /**
   * Methods annotated with @TraceThrown will be weaved by AspectJ with
   * debug logging statements when an exception is thrown from the method.
   */
  public @interface TraceThrown {}

}
