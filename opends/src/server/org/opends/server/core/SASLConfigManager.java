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

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of SASL
 * mechanism handlers defined in the Directory Server.  It will initialize the
 * handlers when the server starts, and then will manage any additions,
 * removals, or modifications of any SASL mechanism handlers while the server is
 * running.
 */
public class SASLConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{



  // A mapping between the DNs of the config entries and the associated SASL
  // mechanism handlers.
  private ConcurrentHashMap<DN,SASLMechanismHandler> handlers;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;



  /**
   * Creates a new instance of this SASL mechanism config manager.
   */
  public SASLConfigManager()
  {
    configHandler = DirectoryServer.getConfigHandler();
    handlers      = new ConcurrentHashMap<DN,SASLMechanismHandler>();
  }



  /**
   * Initializes all SASL mechanism handlers currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the SASL
   *                           mechanism handler initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the SASL mechanism handler that is
   *                                   not related to the server configuration.
   */
  public void initializeSASLMechanismHandlers()
         throws ConfigException, InitializationException
  {
    // First, get the configuration base entry.
    ConfigEntry baseEntry;
    try
    {
      DN saslBase = DN.decode(DN_SASL_CONFIG_BASE);
      baseEntry = configHandler.getConfigEntry(saslBase);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SASL_CANNOT_GET_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (baseEntry == null)
    {
      // The SASL mechanism handler base entry does not exist.  This is not
      // acceptable, so throw an exception.
      int    msgID   = MSGID_CONFIG_SASL_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners with the SASL mechanism base entry.
    // We don't care about modifications to it.
    baseEntry.registerAddListener(this);
    baseEntry.registerDeleteListener(this);


    // See if the base entry has any children.  If not, then we don't need to do
    // anything else.
    if (! baseEntry.hasChildren())
    {
      return;
    }


    // Iterate through the child entries and process them as SASL mechanism
    // handler configuration entries.
    for (ConfigEntry childEntry : baseEntry.getChildren().values())
    {
      childEntry.registerChangeListener(this);

      StringBuilder unacceptableReason = new StringBuilder();
      if (! configAddIsAcceptable(childEntry, unacceptableReason))
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_SASL_ENTRY_UNACCEPTABLE,
                 childEntry.getDN().toString(), unacceptableReason.toString());
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
            buffer.append(getMessage(MSGID_CONFIG_UNKNOWN_UNACCEPTABLE_REASON));
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
                   MSGID_CONFIG_SASL_CANNOT_CREATE_HANDLER,
                   childEntry.getDN().toString(), buffer.toString());
        }
      }
      catch (Exception e)
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_SASL_CANNOT_CREATE_HANDLER,
                 childEntry.getDN().toString(), String.valueOf(e));
      }
    }
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
    // Make sure that the entry has an appropriate objectclass for a SASL
    // mechanism handler.
    if (! configEntry.hasObjectClass(OC_SASL_MECHANISM_HANDLER))
    {
      int    msgID   = MSGID_CONFIG_SASL_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the handler class name.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_SASL_CLASS,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int    msgID   = MSGID_CONFIG_SASL_NO_CLASS_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SASL_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class handlerClass;
    try
    {
      // FIXME -- Should this be done with a custom class loader?
      handlerClass = Class.forName(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SASL_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    try
    {
      SASLMechanismHandler handler =
           (SASLMechanismHandler) handlerClass.newInstance();
    }
    catch(Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SASL_INVALID_CLASS;
      String message = getMessage(msgID, handlerClass.getName(),
                                  String.valueOf(configEntry.getDN()),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // See if this SASL mechanism handler should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_SASL_ENABLED,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int    msgID   = MSGID_CONFIG_SASL_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_ENABLED_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the SASL mechanism handler entry appears to be
    // acceptable.
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


    // Make sure that the entry has an appropriate objectclass for a SASL
    // mechanism handler.
    if (! configEntry.hasObjectClass(OC_SASL_MECHANISM_HANDLER))
    {
      int msgID = MSGID_CONFIG_SASL_INVALID_OBJECTCLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the corresponding SASL mechanism handler if it is active.
    SASLMechanismHandler handler = handlers.get(configEntryDN);


    // See if this handler should be enabled or disabled.
    boolean needsEnabled = false;
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_SASL_ENABLED,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_ENABLED), false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_SASL_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      if (enabledAttr.activeValue())
      {
        if (handler == null)
        {
          needsEnabled = true;
        }
        else
        {
          // The handler is already active, so no action is required.
        }
      }
      else
      {
        if (handler == null)
        {
          // The handler is already disabled, so no action is required and we
          // can short-circuit out of this processing.
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // The handler is active, so it needs to be disabled.  Do this and
          // return that we were successful.
          handlers.remove(configEntryDN);
          handler.finalizeSASLMechanismHandler();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_ENABLED_VALUE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the handler class name.  If it has
    // changed, then we will not try to dynamically apply it.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_SASL_CLASS,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_SASL_NO_CLASS_NAME;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_CLASS_NAME;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    boolean classChanged = false;
    String  oldClassName = null;
    if (handler != null)
    {
      oldClassName = handler.getClass().getName();
      classChanged = (! className.equals(oldClassName));
    }


    if (classChanged)
    {
      // This will not be applied dynamically.  Add a message to the response
      // and indicate that admin action is required.
      adminActionRequired = true;
      messages.add(getMessage(MSGID_CONFIG_SASL_CLASS_ACTION_REQUIRED,
                              String.valueOf(oldClassName),
                              String.valueOf(className),
                              String.valueOf(configEntryDN)));
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    if (needsEnabled)
    {
      try
      {
        // FIXME -- Should this be done with a dynamic class loader?
        Class handlerClass = Class.forName(className);
        handler = (SASLMechanismHandler) handlerClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_SASL_INVALID_CLASS;
        messages.add(getMessage(msgID, className,
                                String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      try
      {
        handler.initializeSASLMechanismHandler(configEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_SASL_INITIALIZATION_FAILED;
        messages.add(getMessage(msgID, className,
                                String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      handlers.put(configEntryDN, handler);
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
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
    if (handlers.containsKey(configEntryDN))
    {
      int    msgID   = MSGID_CONFIG_SASL_EXISTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry has an appropriate objectclass for a SASL
    // mechanism handler.
    if (! configEntry.hasObjectClass(OC_SASL_MECHANISM_HANDLER))
    {
      int    msgID   = MSGID_CONFIG_SASL_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the SASL mechanism handler class.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_SASL_CLASS,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_SASL_NO_CLASS_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class handlerClass;
    try
    {
      // FIXME -- Should this be done with a custom class loader?
      handlerClass = Class.forName(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    SASLMechanismHandler handler;
    try
    {
      handler = (SASLMechanismHandler) handlerClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_CLASS;
      String message = getMessage(msgID, handlerClass.getName(),
                                  String.valueOf(configEntryDN),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If the handler is a configurable component, then make sure that its
    // configuration is valid.
    if (handler instanceof ConfigurableComponent)
    {
      ConfigurableComponent cc = (ConfigurableComponent) handler;
      LinkedList<String> errorMessages = new LinkedList<String>();
      if (! cc.hasAcceptableConfiguration(configEntry, errorMessages))
      {
        if (errorMessages.isEmpty())
        {
          int msgID = MSGID_CONFIG_SASL_UNACCEPTABLE_CONFIG;
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


    // See if this handler should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_SASL_ENABLED,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_SASL_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_ENABLED_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the handler entry appears to be acceptable.
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


    // Make sure that the entry has an appropriate objectclass for a SASL
    // mechanism handler.
    if (! configEntry.hasObjectClass(OC_SASL_MECHANISM_HANDLER))
    {
      int    msgID   = MSGID_CONFIG_SASL_INVALID_OBJECTCLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if this handler should be enabled or disabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_SASL_ENABLED,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        // The attribute doesn't exist, so it will be disabled by default.
        int msgID = MSGID_CONFIG_SASL_NO_ENABLED_ATTR;
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
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_ENABLED_VALUE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the handler class name.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_SASL_CLASS,
                    getMessage(MSGID_CONFIG_SASL_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_SASL_NO_CLASS_NAME;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_CLASS_NAME;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Load and initialize the handler class, and register it with the Directory
    // Server.
    SASLMechanismHandler handler;
    try
    {
      // FIXME -- Should this be done with a dynamic class loader?
      Class handlerClass = Class.forName(className);
      handler = (SASLMechanismHandler) handlerClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INVALID_CLASS;
      messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    try
    {
      handler.initializeSASLMechanismHandler(configEntry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SASL_INITIALIZATION_FAILED;
      messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    handlers.put(configEntryDN, handler);
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
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


    // See if the entry is registered as a SASL mechanism handler.  If so,
    // deregister it and stop the handler.
    SASLMechanismHandler handler = handlers.remove(configEntryDN);
    if (handler != null)
    {
      handler.finalizeSASLMechanismHandler();
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
  }
}

