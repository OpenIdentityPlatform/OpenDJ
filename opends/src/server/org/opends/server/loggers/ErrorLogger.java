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
package org.opends.server.loggers;



import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.messages.MessageHandler;
import org.opends.server.types.*;
import org.opends.server.admin.std.server.ErrorLogPublisherCfg;
import org.opends.server.admin.std.meta.ErrorLogPublisherCfgDefn;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.messages.MessageHandler.getMessage;

/**
 * This class defines the wrapper that will invoke all registered error loggers
 * for each type of request received or response sent. If no error log
 * publishers are registered, messages will be directed to standard out.
 */
public class ErrorLogger implements
    ConfigurationAddListener<ErrorLogPublisherCfg>,
    ConfigurationDeleteListener<ErrorLogPublisherCfg>,
    ConfigurationChangeListener<ErrorLogPublisherCfg>
{
  // The set of error loggers that have been registered with the server. It
  // will initially be empty.
  private static ConcurrentHashMap<DN, ErrorLogPublisher> errorPublishers =
      new ConcurrentHashMap<DN, ErrorLogPublisher>();

  // The singleton instance of this class for configuration purposes.
  private static final ErrorLogger instance = new ErrorLogger();

  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static ErrorLogger getInstance()
  {
    return instance;
  }

  /**
   * Add an error log publisher to the error logger.
   *
   * @param dn The DN of the configuration entry for the publisher.
   * @param publisher The error log publisher to add.
   */
  public synchronized static void addErrorLogPublisher(DN dn,
                                                 ErrorLogPublisher publisher)
  {
    errorPublishers.put(dn, publisher);
  }

  /**
   * Remove an error log publisher from the error logger.
   *
   * @param dn The DN of the publisher to remove.
   * @return The publisher that was removed or null if it was not found.
   */
  public synchronized static ErrorLogPublisher removeErrorLogPublisher(DN dn)
  {
    ErrorLogPublisher errorLogPublisher = errorPublishers.remove(dn);
    if(errorLogPublisher != null)
    {
      errorLogPublisher.close();
    }

    return errorLogPublisher;
  }

  /**
   * Removes all existing error log publishers from the logger.
   */
  public synchronized static void removeAllErrorLogPublishers()
  {
    for(ErrorLogPublisher publisher : errorPublishers.values())
    {
      publisher.close();
    }

    errorPublishers.clear();
  }

  /**
   * Initializes all the error log publishers.
   *
   * @param configs The error log publisher configurations.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeErrorLogger(List<ErrorLogPublisherCfg> configs)
      throws ConfigException, InitializationException
  {
    for(ErrorLogPublisherCfg config : configs)
    {
      config.addErrorChangeListener(this);

      if(config.isEnabled())
      {
        ErrorLogPublisher errorLogPublisher = getErrorPublisher(config);

        addErrorLogPublisher(config.dn(), errorLogPublisher);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(ErrorLogPublisherCfg config,
                                              List<String> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(ErrorLogPublisherCfg config,
                                               List<String> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(ErrorLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    config.addErrorChangeListener(this);

    if(config.isEnabled())
    {
      try
      {
        ErrorLogPublisher errorLogPublisher = getErrorPublisher(config);

        addErrorLogPublisher(config.dn(), errorLogPublisher);
      }
      catch(ConfigException e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        messages.add(e.getMessage());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        int msgID = MSGID_CONFIG_LOGGER_CANNOT_CREATE_LOGGER;
        messages.add(getMessage(msgID, String.valueOf(config.dn().toString()),
                                stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ErrorLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    DN dn = config.dn();
    ErrorLogPublisher errorLogPublisher = errorPublishers.get(dn);

    if(errorLogPublisher == null)
    {
      if(config.isEnabled())
      {
        // Needs to be added and enabled.
        return applyConfigurationAdd(config);
      }
    }
    else
    {
      if(config.isEnabled())
      {
        // The publisher is currently active, so we don't need to do anything.
        // Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = config.getJavaImplementationClass();
        if(!className.equals(errorLogPublisher.getClass().getName()))
        {
          adminActionRequired = true;
        }
      }
      else
      {
        // The publisher is being disabled so shut down and remove.
        removeErrorLogPublisher(config.dn());
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(ErrorLogPublisherCfg config,
                                               List<String> unacceptableReasons)
  {
    DN dn = config.dn();
    ErrorLogPublisher errorLogPublisher = errorPublishers.get(dn);
    return errorLogPublisher != null;

  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      ErrorLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    removeErrorLogPublisher(config.dn());

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  private boolean isJavaClassAcceptable(ErrorLogPublisherCfg config,
                                        List<String> unacceptableReasons)
  {
    String className = config.getJavaImplementationClass();
    ErrorLogPublisherCfgDefn d = ErrorLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaImplementationClassPropertyDefinition();
    // Load the class and cast it to a DebugLogPublisher.
    Class<? extends ErrorLogPublisher> theClass;
    try {
      theClass = pd.loadClass(className, ErrorLogPublisher.class);
      theClass.newInstance();
    } catch (Exception e) {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // Check that the implementation class implements the correct interface.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      theClass.getMethod("initializeErrorLogPublisher", config.definition()
          .getServerConfigurationClass());
    } catch (Exception e) {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // The class is valid as far as we can tell.
    return true;
  }

  private ErrorLogPublisher getErrorPublisher(ErrorLogPublisherCfg config)
      throws ConfigException {
    String className = config.getJavaImplementationClass();
    ErrorLogPublisherCfgDefn d = ErrorLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaImplementationClassPropertyDefinition();
    // Load the class and cast it to a ErrorLogPublisher.
    Class<? extends ErrorLogPublisher> theClass;
    ErrorLogPublisher errorLogPublisher;
    try {
      theClass = pd.loadClass(className, ErrorLogPublisher.class);
      errorLogPublisher = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("initializeErrorLogPublisher",
                             config.definition().getServerConfigurationClass());
      method.invoke(errorLogPublisher, config);
    }
    catch (InvocationTargetException ite)
    {
      // Rethrow the exceptions thrown be the invoked method.
      Throwable e = ite.getTargetException();
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    // The error publisher has been successfully initialized.
    return errorLogPublisher;
  }



  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  errorID   The error ID that uniquely identifies the provided format
   *                   string.
   */
  public static void logError(ErrorLogCategory category,
                              ErrorLogSeverity severity, int errorID)
  {
    String message = MessageHandler.getMessage(errorID);

    for (ErrorLogPublisher publisher : errorPublishers.values())
    {
      publisher.logError(category, severity, message, errorID);
    }
  }



  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  errorID   The error ID that uniquely identifies the provided format
   *                   string.
   * @param  args      The set of arguments to use for the provided format
   *                   string.
   */
  public static void logError(ErrorLogCategory category,
                              ErrorLogSeverity severity, int errorID,
                              Object... args)
  {
    String message = MessageHandler.getMessage(errorID, args);

    for (ErrorLogPublisher publisher : errorPublishers.values())
    {
      publisher.logError(category, severity, message, errorID);
    }
  }



  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  message   The message to be logged.
   * @param  errorID   The error ID that uniquely identifies the format string
   *                   used to generate the provided message.
   */
  public static void logError(ErrorLogCategory category,
                              ErrorLogSeverity severity, String message,
                              int errorID)
  {
    for (ErrorLogPublisher publisher : errorPublishers.values())
    {
      publisher.logError(category, severity, message, errorID);
    }
  }
}

