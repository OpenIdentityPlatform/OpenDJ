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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.server.AccessLogPublisherCfg;
import org.forgerock.opendj.server.config.server.DebugLogPublisherCfg;
import org.forgerock.opendj.server.config.server.ErrorLogPublisherCfg;
import org.forgerock.opendj.server.config.server.HTTPAccessLogPublisherCfg;
import org.forgerock.opendj.server.config.server.LogPublisherCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.messages.Severity;
import org.opends.server.loggers.AbstractLogger;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.HTTPAccessLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * This class defines a utility that will be used to manage the set of loggers
 * used in the Directory Server.  It will perform the logger initialization when
 * the server is starting, and then will manage any additions, removals, or
 * modifications of any loggers while the server is running.
 */
public class LoggerConfigManager implements ConfigurationAddListener<LogPublisherCfg>,
                                            ConfigurationDeleteListener<LogPublisherCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Class to manage java.util.logging to slf4j bridge.
   * Main purpose of this class is to adapt the j.u.l log level when a debug/error log publisher change is detected.
   * <p>
   * @ThreadSafe
   */
  private static class JulToSlf4jLogManager
  {
    private Level currentJulLogLevel = Level.OFF;
    private final Object lock = new Object();

    private Level computeJulLogLevel()
    {
      if (DebugLogger.getInstance().isEnabled())
      {
        return Level.FINEST;
      }

      for (final Severity severity : Severity.values())
      {
        if (ErrorLogger.isEnabledFor("", severity))
        {
          return errorLoggerSeverityToJulLevel(severity);
        }
      }
      return Level.OFF;
    }

    private void adjustJulLevel()
    {
      final Level newLevel1 = computeJulLogLevel();
      if (isMoreDetailedThanCurrentLevel(newLevel1))
      {
        synchronized (lock)
        {
          final Level newLevel2 = computeJulLogLevel();
          if (isMoreDetailedThanCurrentLevel(newLevel2))
          {
            changeJulLogLevel(newLevel2);
          }
        }
      }
    }

    private void changeJulLogLevel(final Level newLevel)
    {
      try
      {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        // This is needed to avoid major performance issue. See: http://www.slf4j.org/legacy.html#jul-to-slf4j
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream((".level=" + newLevel).getBytes()));
        SLF4JBridgeHandler.install();
        currentJulLogLevel = newLevel;
      }
      catch (IOException | SecurityException e)
      {
        logger.error(ERR_CONFIG_CANNOT_CONFIGURE_JUL_LOGGER.get(e.getMessage()), e);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
      }
    }

    private boolean isMoreDetailedThanCurrentLevel(final Level challenger)
    {
      return challenger.intValue() < currentJulLogLevel.intValue();
    }

    /** Convert OpenDJ error log severity to JUL Severity. */
    private Level errorLoggerSeverityToJulLevel(Severity severity)
    {
      switch (severity)
      {
      case DEBUG:
      case INFORMATION:
      case NOTICE:
        return Level.INFO;
      case WARNING:
        return Level.WARNING;
      case ERROR:
        return Level.SEVERE;
      default:
        return Level.OFF;
      }
    }
  }

  private final ServerContext serverContext;

  private final JulToSlf4jLogManager julToSlf4jManager = new JulToSlf4jLogManager();

  /**
   * Create the logger config manager with the provided
   * server context.
   *
   * @param context
   *            The server context.
   */
  public LoggerConfigManager(final ServerContext context)
  {
    this.serverContext = context;
  }

  /**
   * Initializes all the log publishers.
   *
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeLoggerConfig() throws ConfigException, InitializationException
  {
    RootCfg root = serverContext.getRootConfig();
    root.addLogPublisherAddListener(this);
    root.addLogPublisherDeleteListener(this);

    List<DebugLogPublisherCfg> debugPublisherCfgs = new ArrayList<>();
    List<AccessLogPublisherCfg> accessPublisherCfgs = new ArrayList<>();
    List<HTTPAccessLogPublisherCfg> httpAccessPublisherCfgs = new ArrayList<>();
    List<ErrorLogPublisherCfg> errorPublisherCfgs = new ArrayList<>();

    for (String name : root.listLogPublishers())
    {
      LogPublisherCfg config = root.getLogPublisher(name);

      if(config instanceof DebugLogPublisherCfg)
      {
        debugPublisherCfgs.add((DebugLogPublisherCfg)config);
      }
      else if(config instanceof AccessLogPublisherCfg)
      {
        accessPublisherCfgs.add((AccessLogPublisherCfg)config);
      }
      else if (config instanceof HTTPAccessLogPublisherCfg)
      {
        httpAccessPublisherCfgs.add((HTTPAccessLogPublisherCfg) config);
      }
      else if(config instanceof ErrorLogPublisherCfg)
      {
        errorPublisherCfgs.add((ErrorLogPublisherCfg)config);
      }
      else
      {
        throw new ConfigException(ERR_CONFIG_LOGGER_INVALID_OBJECTCLASS.get(config.dn()));
      }
    }

    // See if there are active loggers in all categories.  If not, then log a
    // message.
    // Do not output warn message for debug loggers because it is valid to fully
    // disable all debug loggers.
    if (accessPublisherCfgs.isEmpty())
    {
      logger.warn(WARN_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS);
    }
    if (errorPublisherCfgs.isEmpty())
    {
      logger.warn(WARN_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS);
    }

    DebugLogger.getInstance().initializeLogger(debugPublisherCfgs, serverContext);
    AccessLogger.getInstance().initializeLogger(accessPublisherCfgs, serverContext);
    HTTPAccessLogger.getInstance().initializeLogger(httpAccessPublisherCfgs, serverContext);
    ErrorLogger.getInstance().initializeLogger(errorPublisherCfgs, serverContext);
    julToSlf4jManager.adjustJulLevel();
  }

  /**
   * Returns the logger instance corresponding to the provided config. If no
   * logger corresponds to it, null will be returned and a message will be added
   * to the provided messages list.
   *
   * @param config
   *          the config for which to return the logger instance
   * @param messages
   *          where the error message will be output if no logger correspond to
   *          the provided config.
   * @return the logger corresponding to the provided config, null if no logger
   *         corresponds.
   */
  private AbstractLogger getLoggerInstance(LogPublisherCfg config, List<LocalizableMessage> messages)
  {
    if (config instanceof DebugLogPublisherCfg)
    {
      return DebugLogger.getInstance();
    }
    else if (config instanceof AccessLogPublisherCfg)
    {
      return AccessLogger.getInstance();
    }
    else if (config instanceof HTTPAccessLogPublisherCfg)
    {
      return HTTPAccessLogger.getInstance();
    }
    else if (config instanceof ErrorLogPublisherCfg)
    {
      return ErrorLogger.getInstance();
    }

    messages.add(ERR_CONFIG_LOGGER_INVALID_OBJECTCLASS.get(config.dn()));
    return null;
  }

  @Override
  public boolean isConfigurationAddAcceptable(LogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    AbstractLogger instance = getLoggerInstance(config, unacceptableReasons);
    return instance != null
        && instance.isConfigurationAddAcceptable(config, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(LogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final AbstractLogger instance = getLoggerInstance(config, ccr.getMessages());
    if (instance != null)
    {
      return instance.applyConfigurationAdd(config);
    }

    ccr.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(LogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    final AbstractLogger instance = getLoggerInstance(config, unacceptableReasons);
    return instance != null
        && instance.isConfigurationDeleteAcceptable(config, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(LogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final AbstractLogger instance = getLoggerInstance(config, ccr.getMessages());
    if (instance != null)
    {
      return instance.applyConfigurationDelete(config);
    }
    ccr.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    return ccr;
  }

  /**
   * Update the current java.util.logging.Level.
   * This level is used to filter logs from third party libraries which use j.u.l to our slf4j logger.
   */
  public void adjustJulLevel()
  {
    julToSlf4jManager.adjustJulLevel();
  }
}
