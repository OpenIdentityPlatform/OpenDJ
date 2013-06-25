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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;

import java.util.ArrayList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.opends.server.admin.std.server.ErrorLogPublisherCfg;
import org.opends.server.admin.std.server.HTTPAccessLogPublisherCfg;
import org.opends.server.admin.std.server.LogPublisherCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.AbstractLogger;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.HTTPAccessLogger;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;


/**
 * This class defines a utility that will be used to manage the set of loggers
 * used in the Directory Server.  It will perform the logger initialization when
 * the server is starting, and then will manage any additions, removals, or
 * modifications of any loggers while the server is running.
 */
public class LoggerConfigManager implements
    ConfigurationAddListener<LogPublisherCfg>,
    ConfigurationDeleteListener<LogPublisherCfg>
{

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
  public void initializeLoggerConfig()
      throws ConfigException, InitializationException
  {
    // Create an internal server management context and retrieve
    // the root configuration which has the log publisher relation.
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();

    root.addLogPublisherAddListener(this);
    root.addLogPublisherDeleteListener(this);

    List<DebugLogPublisherCfg> debugPublisherCfgs =
        new ArrayList<DebugLogPublisherCfg>();

    List<AccessLogPublisherCfg> accessPublisherCfgs =
        new ArrayList<AccessLogPublisherCfg>();

    List<HTTPAccessLogPublisherCfg> httpAccessPublisherCfgs =
        new ArrayList<HTTPAccessLogPublisherCfg>();

    List<ErrorLogPublisherCfg> errorPublisherCfgs =
        new ArrayList<ErrorLogPublisherCfg>();

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
        Message message = ERR_CONFIG_LOGGER_INVALID_OBJECTCLASS.get(
            String.valueOf(config.dn()));
        throw new ConfigException(message);
      }
    }

    // See if there are active loggers in all categories.  If not, then log a
    // message.
    // Do not output warn message for debug loggers because it is valid to fully
    // disable all debug loggers.
    if (accessPublisherCfgs.isEmpty())
    {
      logError(WARN_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS.get());
    }
    if (errorPublisherCfgs.isEmpty())
    {
      logError(WARN_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS.get());
    }

    DebugLogger.getInstance().initializeLogger(debugPublisherCfgs);
    AccessLogger.getInstance().initializeLogger(accessPublisherCfgs);
    HTTPAccessLogger.getInstance().initializeLogger(httpAccessPublisherCfgs);
    ErrorLogger.getInstance().initializeLogger(errorPublisherCfgs);
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
  private AbstractLogger getLoggerInstance(LogPublisherCfg config,
      List<Message> messages)
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
    else
    {
      messages.add(ERR_CONFIG_LOGGER_INVALID_OBJECTCLASS.get(String
          .valueOf(config.dn())));
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAddAcceptable(LogPublisherCfg config,
                                              List<Message> unacceptableReasons)
  {
    AbstractLogger instance = getLoggerInstance(config, unacceptableReasons);
    if (instance != null)
    {
      return instance.isConfigurationAddAcceptable(config, unacceptableReasons);
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationAdd(LogPublisherCfg config)
  {
    List<Message> messages = new ArrayList<Message>(1);
    AbstractLogger instance = getLoggerInstance(config, messages);
    if (instance != null)
    {
      return instance.applyConfigurationAdd(config);
    }
    else
    {
      boolean adminActionRequired = false;
      ResultCode resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationDeleteAcceptable(LogPublisherCfg config,
                                              List<Message> unacceptableReasons)
  {
    AbstractLogger instance = getLoggerInstance(config, unacceptableReasons);
    if (instance != null)
    {
      return instance.isConfigurationDeleteAcceptable(config,
          unacceptableReasons);
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationDelete(LogPublisherCfg config)
  {
    List<Message> messages = new ArrayList<Message>(1);
    AbstractLogger instance = getLoggerInstance(config, messages);
    if (instance != null)
    {
      return instance.applyConfigurationDelete(config);
    }
    else
    {
      boolean           adminActionRequired = false;
      ResultCode resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }
}
