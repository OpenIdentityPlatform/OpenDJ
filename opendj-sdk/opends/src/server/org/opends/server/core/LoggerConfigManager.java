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



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.AccessLogger;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ErrorLogger;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.loggers.StartupErrorLogger;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a utility that will be used to manage the set of loggers
 * used in the Directory Server.  It will perform the logger initialization when
 * the server is starting, and then will manage any additions, removals, or
 * modifications of any loggers while the server is running.
 */
public class LoggerConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.LoggerConfigManager";



  // A mapping between the DNs of the logger configuration entries and the
  // associated active access loggers.
  private ConcurrentHashMap<DN,AccessLogger> activeAccessLoggers;

  // A mapping between the DNs of the logger configuration entries and the
  // associated active error loggers.
  private ConcurrentHashMap<DN,ErrorLogger> activeErrorLoggers;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;



  /**
   * Creates a new instance of this logger config manager.
   */
  public LoggerConfigManager()
  {
    configHandler = DirectoryServer.getConfigHandler();

    activeAccessLoggers = new ConcurrentHashMap<DN,AccessLogger>();
    activeErrorLoggers  = new ConcurrentHashMap<DN,ErrorLogger>();
  }



  /**
   * Initializes all loggers currently defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the monitor
   *                           initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the monitors that is not related to the
   *                                   server configuration.
   */
  public void initializeLoggers()
         throws ConfigException, InitializationException
  {
    // First, get the logger configuration base entry.
    ConfigEntry loggerBaseEntry;
    try
    {
      DN loggerBaseDN = DN.decode(DN_LOGGER_BASE);
      loggerBaseEntry = configHandler.getConfigEntry(loggerBaseDN);
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_LOGGER_CANNOT_GET_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (loggerBaseEntry == null)
    {
      // The logger base entry does not exist.  This is not acceptable, so throw
      // an exception.
      int    msgID   = MSGID_CONFIG_LOGGER_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners with the logger base entry.  We don't
    // care about modifications to it.
    loggerBaseEntry.registerAddListener(this);
    loggerBaseEntry.registerDeleteListener(this);


    // See if the logger base has any children.  If not, then log a warning.
    if (! loggerBaseEntry.hasChildren())
    {
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS);
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS);
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_WARNING,
               MSGID_CONFIG_LOGGER_NO_ACTIVE_DEBUG_LOGGERS);
      return;
    }


    // Iterate through the child entries and process them as logger
    // configuration entries.
    for (ConfigEntry childEntry : loggerBaseEntry.getChildren().values())
    {
      if(!childEntry.hasObjectClass(OC_DEBUG_LOGGER))
      {
        childEntry.registerChangeListener(this);

        StringBuilder unacceptableReason = new StringBuilder();
        if (! configAddIsAcceptable(childEntry, unacceptableReason))
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   MSGID_CONFIG_LOGGER_ENTRY_UNACCEPTABLE,
                   childEntry.getDN().toString(),
                   unacceptableReason.toString());
          continue;
        }

        try
        {
          ConfigChangeResult result = applyConfigurationAdd(childEntry);
          if (result.getResultCode() != ResultCode.SUCCESS)
          {
            StringBuilder buffer = new StringBuilder();

            List<String> resultMessages = result.getMessages();
            if ((resultMessages == null) || (resultMessages.isEmpty()))
            {
              buffer.append(
                  getMessage(MSGID_CONFIG_UNKNOWN_UNACCEPTABLE_REASON));
            }
            else
            {
              Iterator<String> iterator = resultMessages.iterator();

              buffer.append(iterator.next());
              while (iterator.hasNext())
              {
                buffer.append(EOL);
                buffer.append(iterator.next());
              }
            }

            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_CONFIG_LOGGER_CANNOT_CREATE_LOGGER,
                     childEntry.getDN().toString(), buffer.toString());
          }
        }
        catch (Exception e)
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   MSGID_CONFIG_LOGGER_CANNOT_CREATE_LOGGER,
                   childEntry.getDN().toString(), String.valueOf(e));
        }
      }
    }


    // See if there are active loggers in all categories.  If not, then log a
    // message.
    if (activeAccessLoggers.isEmpty())
    {
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS);
    }

    if (activeErrorLoggers.isEmpty())
    {
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS);
    }
  }



  /**
   * Stops and closes all loggers associated with the Directory Server.  It will
   * replace them with startup error and debug loggers so that any final
   * messages logged by the server after this call may still be captured on
   * standard error.
   */
  public void stopLoggers()
  {
    StartupErrorLogger errorLogger = new StartupErrorLogger();
    errorLogger.initializeErrorLogger(null);

    removeAllErrorLoggers(true);
    addErrorLogger(errorLogger);

  }



  /**
   * Indicates whether the configuration entry that will result from a proposed
   * modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested update.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed change is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    // Make sure that the entry has an appropriate objectclass for an access,
    // error, or debug logger.
    boolean isAccessLogger = false;
    boolean isErrorLogger  = false;
    boolean isDebugLogger  = false;
    if (configEntry.hasObjectClass(OC_ACCESS_LOGGER))
    {
      isAccessLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_ERROR_LOGGER))
    {
      isErrorLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_DEBUG_LOGGER))
    {
      isDebugLogger = true;
    }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the logger class name.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_LOGGER_CLASS,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_CLASS_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class loggerClass;
    try
    {
      // FIXME -- Should this be done with a custom class loader?
      loggerClass = Class.forName(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    if (isAccessLogger)
    {
      try
      {
        AccessLogger logger = (AccessLogger) loggerClass.newInstance();
      }
      catch (Exception e)
      {
        if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

        int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS;
        String message = getMessage(msgID, loggerClass.getName(),
                                    configEntry.getDN().toString(),
                                    String.valueOf(e));
        unacceptableReason.append(message);
        return false;
      }
    }
    else if (isErrorLogger)
    {
      try
      {
        ErrorLogger logger = (ErrorLogger) loggerClass.newInstance();
      }
      catch (Exception e)
      {
        if(debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
        String message = getMessage(msgID, loggerClass.getName(),
                                    configEntry.getDN().toString(),
                                    String.valueOf(e));
        unacceptableReason.append(message);
        return false;
      }
    }
    else if (isDebugLogger)
    {
    }


    // See if this logger entry should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_LOGGER_ENABLED,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_ENABLED), false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_ENABLED_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the logger entry appears to be acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration to this Directory Server component
   * based on the provided changed entry.
   *
   * @param  configEntry  The configuration entry that containing the updated
   *                      configuration for this component.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry)
  {
    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that the entry has an appropriate objectclass for an access,
    // error, or debug logger.
    boolean isAccessLogger = false;
    boolean isErrorLogger  = false;
    boolean isDebugLogger  = false;
    if (configEntry.hasObjectClass(OC_ACCESS_LOGGER))
    {
      isAccessLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_ERROR_LOGGER))
    {
      isErrorLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_DEBUG_LOGGER))
    {
      isDebugLogger = true;
    }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the corresponding logger if it is active.
    boolean      isActive     = false;
    AccessLogger accessLogger = null;
    ErrorLogger  errorLogger  = null;
    if (isAccessLogger)
    {
      accessLogger = activeAccessLoggers.get(configEntryDN);
      isActive = (accessLogger != null);
    }
    else if (isErrorLogger)
    {
      errorLogger = activeErrorLoggers.get(configEntryDN);
      isActive = (errorLogger != null);
    }


    // See if this logger should be enabled or disabled.
    boolean needsEnabled = false;
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_LOGGER_ENABLED,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_ENABLED), false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      if (enabledAttr.activeValue())
      {
        if (isActive)
        {
          // The logger is already active, so no action is required.
        }
        else
        {
          needsEnabled = true;
        }
      }
      else
      {
        if (isActive)
        {
          // The logger is active, so it needs to be disabled.  Do this and
          // return that we were successful.
          if (isAccessLogger)
          {
            activeAccessLoggers.remove(configEntryDN);
            accessLogger.closeAccessLogger();
          }
          else if (isErrorLogger)
          {
            activeErrorLoggers.remove(configEntryDN);
            errorLogger.closeErrorLogger();
          }

          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // The logger is already disabled, so no action is required and we
          // can short-circuit out of this processing.
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_ENABLED_VALUE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the logger class name.  If it has
    // changed, then we will not try to dynamically apply it.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_LOGGER_CLASS,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_CLASS_NAME;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    boolean classChanged = false;
    String  oldClassName = null;
    if (! (needsEnabled || (accessLogger == null) && (errorLogger == null) ))
    {
      if (isAccessLogger)
      {
        oldClassName = accessLogger.getClass().getName();
        classChanged = (! className.equals(oldClassName));
      }
      else if (isErrorLogger)
      {
        oldClassName = errorLogger.getClass().getName();
        classChanged = (! className.equals(oldClassName));
      }
    }


    if (classChanged)
    {
      // This will not be applied dynamically.  Add a message to the response
      // and indicate that admin action is required.
      adminActionRequired = true;
      messages.add(getMessage(MSGID_CONFIG_LOGGER_CLASS_ACTION_REQUIRED,
                              String.valueOf(oldClassName),
                              String.valueOf(className),
                              String.valueOf(configEntryDN)));
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // If the monitor needs to be enabled, then create it and register it with
    // the Directory Server.
    if (needsEnabled)
    {
      if (isAccessLogger)
      {
        try
        {
          // FIXME -- Should this be done with a dynamic class loader?
          Class loggerClass = Class.forName(className);
          accessLogger = (AccessLogger) loggerClass.newInstance();
        }
        catch (Exception e)
        {
          if(debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS;
          messages.add(getMessage(msgID, className,
                                  String.valueOf(configEntryDN),
                                  String.valueOf(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }

        try
        {
          accessLogger.initializeAccessLogger(configEntry);
        }
        catch (Exception e)
        {
          if(debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_LOGGER_ACCESS_INITIALIZATION_FAILED;
          messages.add(getMessage(msgID, className,
                                  String.valueOf(configEntryDN),
                                  String.valueOf(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }

        addAccessLogger(accessLogger);
        activeAccessLoggers.put(configEntryDN, accessLogger);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (isErrorLogger)
      {
        try
        {
          // FIXME -- Should this be done with a dynamic class loader?
          Class loggerClass = Class.forName(className);
          errorLogger = (ErrorLogger) loggerClass.newInstance();
        }
        catch (Exception e)
        {
          if(debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
          messages.add(getMessage(msgID, className,
                                  String.valueOf(configEntryDN),
                                  String.valueOf(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }

        try
        {
          errorLogger.initializeErrorLogger(configEntry);
        }
        catch (Exception e)
        {
          if(debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_LOGGER_ERROR_INITIALIZATION_FAILED;
          messages.add(getMessage(msgID, className,
                                  String.valueOf(configEntryDN),
                                  String.valueOf(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }

        addErrorLogger(errorLogger);
        activeErrorLoggers.put(configEntryDN, errorLogger);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }


    // If we've gotten here, then there haven't been any changes to anything
    // that we care about.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether the configuration entry that will result from a proposed
   * add is acceptable to this add listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested add.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed entry is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
                                       StringBuilder unacceptableReason)
  {
    // Make sure that no entry already exists with the specified DN.
    DN configEntryDN = configEntry.getDN();
    if (activeAccessLoggers.containsKey(configEntryDN) ||
        activeErrorLoggers.containsKey(configEntryDN) )
    {
      int    msgID   = MSGID_CONFIG_LOGGER_EXISTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry has an appropriate objectclass for an access,
    // error, or debug logger.
    boolean isAccessLogger = false;
    boolean isErrorLogger  = false;
    boolean isDebugLogger  = false;
    if (configEntry.hasObjectClass(OC_ACCESS_LOGGER))
    {
      isAccessLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_ERROR_LOGGER))
    {
      isErrorLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_DEBUG_LOGGER))
    {
      isDebugLogger = true;
    }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the logger class name.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_LOGGER_CLASS,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_CLASS_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class loggerClass;
    try
    {
      // FIXME -- Should this be done with a custom class loader?
      loggerClass = Class.forName(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    Object logger = null;
    if (isAccessLogger)
    {
      try
      {
        logger = (AccessLogger) loggerClass.newInstance();
      }
      catch (Exception e)
      {
        if(debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS;
        String message = getMessage(msgID, loggerClass.getName(),
                                    configEntry.getDN().toString(),
                                    String.valueOf(e));
        unacceptableReason.append(message);
        return false;
      }
    }
    else if (isErrorLogger)
    {
      try
      {
        logger = (ErrorLogger) loggerClass.newInstance();
      }
      catch (Exception e)
      {
        if(debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
        String message = getMessage(msgID, loggerClass.getName(),
                                    configEntry.getDN().toString(),
                                    String.valueOf(e));
        unacceptableReason.append(message);
        return false;
      }
    }
    else if (isDebugLogger)
    {
    }


    // If the logger is a configurable component, then make sure that its
    // configuration is valid.
    if (logger instanceof ConfigurableComponent)
    {
      ConfigurableComponent cc = (ConfigurableComponent) logger;
      LinkedList<String> errorMessages = new LinkedList<String>();
      if (! cc.hasAcceptableConfiguration(configEntry, errorMessages))
      {
        if (errorMessages.isEmpty())
        {
          int msgID = MSGID_CONFIG_LOGGER_UNACCEPTABLE_CONFIG;
          unacceptableReason.append(getMessage(msgID,
                                               String.valueOf(configEntryDN)));
        }
        else
        {
          Iterator<String> iterator = errorMessages.iterator();
          unacceptableReason.append(iterator.next());
          while (iterator.hasNext())
          {
            unacceptableReason.append("  ");
            unacceptableReason.append(iterator.next());
          }
        }

        return false;
      }
    }


    // See if this logger entry should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_LOGGER_ENABLED,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_ENABLED), false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_ENABLED_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the logger entry appears to be acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided added entry.
   *
   * @param  configEntry  The new configuration entry that contains the
   *                      configuration to apply.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {
    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that the entry has an appropriate objectclass for an access,
    // error, or debug logger.
    boolean isAccessLogger = false;
    boolean isErrorLogger  = false;
    boolean isDebugLogger  = false;
    if (configEntry.hasObjectClass(OC_ACCESS_LOGGER))
    {
      isAccessLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_ERROR_LOGGER))
    {
      isErrorLogger = true;
    }
    else if (configEntry.hasObjectClass(OC_DEBUG_LOGGER))
    {
      isDebugLogger = true;
    }
    else
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if this logger should be enabled or disabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_LOGGER_ENABLED,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_ENABLED), false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        // The attribute doesn't exist, so it will be disabled by default.
        int msgID = MSGID_CONFIG_LOGGER_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.SUCCESS;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (! enabledAttr.activeValue())
      {
        // It is explicitly configured as disabled, so we don't need to do
        // anything.
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_ENABLED_VALUE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the logger class name.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_LOGGER_CLASS,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_CLASS_NAME;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // If this is supposed to be an access logger, then load and initialize the
    // class, and register it with the Directory Server.
    if (isAccessLogger)
    {
      AccessLogger accessLogger;

      try
      {
        // FIXME -- Should this be done with a dynamic class loader?
        Class loggerClass = Class.forName(className);
        accessLogger = (AccessLogger) loggerClass.newInstance();
      }
      catch (Exception e)
      {
        if(debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

        int msgID = MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS;
        messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      try
      {
        accessLogger.initializeAccessLogger(configEntry);
      }
      catch (Exception e)
      {
        if(debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_LOGGER_ACCESS_INITIALIZATION_FAILED;
        messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      addAccessLogger(accessLogger);
      activeAccessLoggers.put(configEntryDN, accessLogger);
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // If this is supposed to be an error logger, then load and initialize the
    // class, and register it with the Directory Server.
    else if (isErrorLogger)
    {
      ErrorLogger errorLogger;

      try
      {
        // FIXME -- Should this be done with a dynamic class loader?
        Class loggerClass = Class.forName(className);
        errorLogger = (ErrorLogger) loggerClass.newInstance();
      }
      catch (Exception e)
      {
        if(debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
        messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      try
      {
        errorLogger.initializeErrorLogger(configEntry);
      }
      catch (Exception e)
      {
        if(debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_LOGGER_ERROR_INITIALIZATION_FAILED;
        messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      addErrorLogger(errorLogger);
      activeErrorLoggers.put(configEntryDN, errorLogger);
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // If this is supposed to be a debug logger, then load and initialize the
    // class, and register it with the Directory Server.
    else
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }



  /**
   * Indicates whether it is acceptable to remove the provided configuration
   * entry.
   *
   * @param  configEntry         The configuration entry that will be removed
   *                             from the configuration.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed delete is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry may be removed from the
   *          configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided deleted entry.
   *
   * @param  configEntry  The new configuration entry that has been deleted.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {
    DN         configEntryDN       = configEntry.getDN();
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean    adminActionRequired = false;


    // See if the entry is registered as an access logger.  If so, deregister it
    // and stop the logger.
    AccessLogger accessLogger = activeAccessLoggers.remove(configEntryDN);
    if (accessLogger != null)
    {
      removeAccessLogger(accessLogger);
      accessLogger.closeAccessLogger();
      return new ConfigChangeResult(resultCode, adminActionRequired);
    }


    // See if the entry is registered as an error logger.  If so, deregister it
    // and stop the logger.
    ErrorLogger errorLogger = activeErrorLoggers.remove(configEntryDN);
    if (errorLogger != null)
    {
      removeErrorLogger(errorLogger);
      errorLogger.closeErrorLogger();
      return new ConfigChangeResult(resultCode, adminActionRequired);
    }




    // If we've gotten here, then it wasn't an active logger so we can just
    // return without doing anything.
    return new ConfigChangeResult(resultCode, adminActionRequired);
  }
}

