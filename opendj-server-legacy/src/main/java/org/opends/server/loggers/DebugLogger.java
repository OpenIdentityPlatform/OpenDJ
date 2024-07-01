/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.server.config.meta.DebugLogPublisherCfgDefn;
import org.forgerock.opendj.server.config.server.DebugLogPublisherCfg;

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
  private static boolean enabled;

  private static final LoggerStorage
      <DebugLogPublisher<DebugLogPublisherCfg>, DebugLogPublisherCfg>
      loggerStorage = new LoggerStorage<>();

  /** The singleton instance of this class. */
  static final DebugLogger instance = new DebugLogger();

  /** The constructor for this class. */
  private DebugLogger()
  {
    super((Class) DebugLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS);
  }

  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return DebugLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

  @Override
  protected Collection<DebugLogPublisher<DebugLogPublisherCfg>> getLogPublishers()
  {
    return loggerStorage.getLogPublishers();
  }

  /** Update all debug tracers with the settings in the registered publishers. */
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
   * Returns the registered Debug Tracer for a traced class.
   *
   * @param className The name of the class tracer to retrieve.
   * @return The tracer for the provided class or null if there are
   *         no tracers registered.
   */
  public static DebugTracer getTracer(final String className)
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

  @Override
  public final synchronized void addLogPublisher(final DebugLogPublisher<DebugLogPublisherCfg> publisher)
  {
    loggerStorage.addLogPublisher(publisher);
    updateTracerSettings();
    enabled = true;
    adjustJulLevel();
  }

  @Override
  public final synchronized boolean removeLogPublisher(final DebugLogPublisher<DebugLogPublisherCfg> publisher)
  {
    boolean removed = loggerStorage.removeLogPublisher(publisher);
    updateTracerSettings();
    enabled = !loggerStorage.getLogPublishers().isEmpty();
    adjustJulLevel();
    return removed;
  }

  @Override
  public final synchronized void removeAllLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
    updateTracerSettings();
    enabled = false;
    adjustJulLevel();
  }

  private void adjustJulLevel()
  {
    final ServerContext serverContext = getServerContext();
    if (serverContext != null)
    {
      serverContext.getLoggerConfigManager().adjustJulLevel();
    }
  }

  /**
   * Returns whether there is at least one debug log publisher enabled.
   * @return whether there is at least one debug log publisher enabled.
   */
  public boolean isEnabled()
  {
    return enabled;
  }
}
