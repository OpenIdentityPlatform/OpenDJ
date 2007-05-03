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
package org.opends.server.core;



import java.util.*;

import org.opends.server.config.ConfigException;
import org.opends.server.types.*;

import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.AccessLogger;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import org.opends.server.admin.std.server.*;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;


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
    root.addLogPublisherAddListener(this);

    List<DebugLogPublisherCfg> debugPublisherCfgs =
        new ArrayList<DebugLogPublisherCfg>();

    List<AccessLogPublisherCfg> accessPublisherCfgs =
        new ArrayList<AccessLogPublisherCfg>();

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
      else if(config instanceof ErrorLogPublisherCfg)
      {
        errorPublisherCfgs.add((ErrorLogPublisherCfg)config);
      }
      else
      {
        int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
        String message = getMessage(msgID, String.valueOf(config.dn()));
        throw new ConfigException(msgID, message);
      }
    }

    // See if there are active loggers in all categories.  If not, then log a
    // message.
    if (accessPublisherCfgs.isEmpty())
    {
      ErrorLogger.logError(ErrorLogCategory.CONFIGURATION,
                           ErrorLogSeverity.SEVERE_WARNING,
                           MSGID_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS);
    }
    if (errorPublisherCfgs.isEmpty())
    {
      ErrorLogger.logError(ErrorLogCategory.CONFIGURATION,
                           ErrorLogSeverity.SEVERE_WARNING,
                           MSGID_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS);
    }

    DebugLogger.getInstance().initializeDebugLogger(debugPublisherCfgs);
    AccessLogger.getInstance().initializeAccessLogger(accessPublisherCfgs);
    ErrorLogger.getInstance().initializeErrorLogger(errorPublisherCfgs);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(LogPublisherCfg config,
                                              List<String> unacceptableReasons)
  {
    if(config instanceof DebugLogPublisherCfg)
    {
      return DebugLogger.getInstance().isConfigurationAddAcceptable(
          (DebugLogPublisherCfg)config, unacceptableReasons);
    }
   else if(config instanceof AccessLogPublisherCfg)
   {
     return AccessLogger.getInstance().isConfigurationAddAcceptable(
         (AccessLogPublisherCfg)config, unacceptableReasons);
   }
   else if(config instanceof ErrorLogPublisherCfg)
   {
     return ErrorLogger.getInstance().isConfigurationAddAcceptable(
         (ErrorLogPublisherCfg)config, unacceptableReasons);
   }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(config.dn())));
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(LogPublisherCfg config)
  {
    if(config instanceof DebugLogPublisherCfg)
    {
      return DebugLogger.getInstance().applyConfigurationAdd(
          (DebugLogPublisherCfg)config);
    }
   else if(config instanceof AccessLogPublisherCfg)
   {
     return AccessLogger.getInstance().applyConfigurationAdd(
         (AccessLogPublisherCfg)config);
   }
   else if(config instanceof ErrorLogPublisherCfg)
   {
     return ErrorLogger.getInstance().applyConfigurationAdd(
         (ErrorLogPublisherCfg)config);
   }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      ArrayList<String> messages            = new ArrayList<String>();
      boolean           adminActionRequired = false;
      messages.add(getMessage(msgID, String.valueOf(config.dn())));
      ResultCode resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(LogPublisherCfg config,
                                               List<String> unacceptableReasons)
  {
    if(config instanceof DebugLogPublisherCfg)
    {
      return DebugLogger.getInstance().isConfigurationDeleteAcceptable(
          (DebugLogPublisherCfg)config, unacceptableReasons);
    }
   else if(config instanceof AccessLogPublisherCfg)
   {
     return AccessLogger.getInstance().isConfigurationDeleteAcceptable(
         (AccessLogPublisherCfg)config, unacceptableReasons);
   }
   else if(config instanceof ErrorLogPublisherCfg)
   {
     return ErrorLogger.getInstance().isConfigurationDeleteAcceptable(
         (ErrorLogPublisherCfg)config, unacceptableReasons);
   }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(config.dn())));
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(LogPublisherCfg config)
  {
    if(config instanceof DebugLogPublisherCfg)
    {
      return DebugLogger.getInstance().applyConfigurationDelete(
          (DebugLogPublisherCfg)config);
    }
   else if(config instanceof AccessLogPublisherCfg)
   {
     return AccessLogger.getInstance().applyConfigurationDelete(
         (AccessLogPublisherCfg)config);
   }
   else if(config instanceof ErrorLogPublisherCfg)
   {
     return ErrorLogger.getInstance().applyConfigurationDelete(
         (ErrorLogPublisherCfg)config);
   }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      ArrayList<String> messages            = new ArrayList<String>();
      boolean           adminActionRequired = false;
      messages.add(getMessage(msgID, String.valueOf(config.dn())));
      ResultCode resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }
}

