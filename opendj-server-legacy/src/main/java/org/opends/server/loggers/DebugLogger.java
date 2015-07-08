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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.std.meta.DebugLogPublisherCfgDefn;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;

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

  /** The set of all DebugTracer instances. */
  private static Map<String, DebugTracer> classTracers = new ConcurrentHashMap<>();

  /**
   * Trace methods will use this static boolean to determine if debug is enabled
   * so to not incur the cost of calling debugPublishers.isEmpty().
   */
  static boolean enabled;

  private static final LoggerStorage
      <DebugLogPublisher<DebugLogPublisherCfg>, DebugLogPublisherCfg>
      loggerStorage = new LoggerStorage<>();

  /** The singleton instance of this class. */
  static final DebugLogger instance = new DebugLogger();

  /**
   * The constructor for this class.
   */
  private DebugLogger()
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
  protected Collection<DebugLogPublisher<DebugLogPublisherCfg>> getLogPublishers()
  {
    return loggerStorage.getLogPublishers();
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
  static boolean debugEnabled()
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
   * Returns the registered Debug Tracer for a traced class.
   *
   * @param className The name of the class tracer to retrieve.
   * @return The tracer for the provided class or null if there are
   *         no tracers registered.
   */
  static DebugTracer getTracer(final String className)
  {
    DebugTracer tracer = classTracers.get(className);
    if (tracer == null)
    {
      tracer =
          new DebugTracer(className, loggerStorage.getLogPublishers().toArray(
              new DebugLogPublisher[0]));
      classTracers.put(tracer.getTracedClassName(), tracer);
    }
    return tracer;
  }

  /**
   * Adds a text debug log publisher that will print all messages to the
   * provided writer, based on debug target(s) defined through system
   * properties.
   * <p>
   * It is expected that one or more system properties beginning with
   * {@code PROPERTY_DEBUG_TARGET} are set to define the properties of the debug
   * targets used by the publisher, otherwise no publisher is added.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @return the publisher. It may be {@code null} if no publisher is added.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public final TextDebugLogPublisher addPublisherIfRequired(TextWriter writer)
  {
    final List<String> debugTargets = getDebugTargetsFromSystemProperties();
    TextDebugLogPublisher publisher = null;
    if (!debugTargets.isEmpty())
    {
      publisher = TextDebugLogPublisher.getStartupTextDebugPublisher(debugTargets, writer);
      if (publisher != null) {
        addLogPublisher((DebugLogPublisher) publisher);
      }
    }
    return publisher;
  }

  private List<String> getDebugTargetsFromSystemProperties()
  {
    final List<String> targets = new ArrayList<>();
    for (Map.Entry<Object, Object> entry : System.getProperties().entrySet())
    {
      if (((String) entry.getKey()).startsWith(PROPERTY_DEBUG_TARGET))
      {
        targets.add((String)entry.getValue());
      }
    }
    return targets;
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void addLogPublisher(
      DebugLogPublisher<DebugLogPublisherCfg> publisher)
  {
    loggerStorage.addLogPublisher(publisher);
    updateTracerSettings();
    enabled = true;
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized boolean removeLogPublisher(
      DebugLogPublisher<DebugLogPublisherCfg> publisher)
  {
    boolean removed = loggerStorage.removeLogPublisher(publisher);
    updateTracerSettings();
    enabled = !loggerStorage.getLogPublishers().isEmpty();
    return removed;
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void removeAllLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
    updateTracerSettings();
    enabled = false;
  }

}
