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
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ConnectionHandler;
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
import org.opends.server.types.SearchFilter;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of connection handlers defined in the Directory Server.  It will
 * perform the necessary initialization of those connection handlers when the
 * server is first started, and then will manage any changes to them while the
 * server is running.
 */
public class ConnectionHandlerConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{



  // The mapping between configuration entry DNs and their corresponding
  // connection handler implementations.
  private ConcurrentHashMap<DN,ConnectionHandler> connectionHandlers;

  // The DN of the associated configuration entry.
  private DN configEntryDN;



  /**
   * Creates a new instance of this connection handler config manager.
   */
  public ConnectionHandlerConfigManager()
  {
    // No implementation is required.
  }



  /**
   * Initializes the configuration associated with the Directory Server
   * connection handlers.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a critical configuration problem prevents the
   *                           connection handler initialization from
   *                           succeeding.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the connection handlers that is not
   *                                   related to the server configuration.
   */
  public void initializeConnectionHandlerConfig()
         throws ConfigException, InitializationException
  {
    connectionHandlers = new ConcurrentHashMap<DN,ConnectionHandler>();



    // Get the configuration entry that is at the root of all the connection
    // handlers in the server.
    ConfigEntry connectionHandlerRoot;
    try
    {
      configEntryDN         = DN.decode(DN_CONNHANDLER_BASE);
      connectionHandlerRoot = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CONFIG_BASE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // If the configuration root entry is null, then assume it doesn't exist.
    // In that case, then fail.  At least that entry must exist in the
    // configuration, even if there are no connection handlers defined below it.
    if (connectionHandlerRoot == null)
    {
      int    msgID   = MSGID_CONFIG_CONNHANDLER_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register as an add and delete listener for the base entry so that we can
    // be notified if new connection handlers are added or existing connection
    // handlers are removed.
    connectionHandlerRoot.registerAddListener(this);
    connectionHandlerRoot.registerDeleteListener(this);


    // Iterate through the set of immediate children below the connection
    // handler config root.
    for (ConfigEntry connectionHandlerEntry :
         connectionHandlerRoot.getChildren().values())
    {
      DN connectionHandlerDN = connectionHandlerEntry.getDN();


      // Register as a change listener for this connection handler entry so that
      // we will be notified of any changes that may be made to it.
      connectionHandlerEntry.registerChangeListener(this);


      // Check to see if this entry appears to contain a connection handler
      // configuration.  If not, log a warning and skip it.
      if (! connectionHandlerEntry.hasObjectClass(OC_CONNECTION_HANDLER))
      {
        int msgID =
             MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        continue;
      }


      // See if the entry contains an attribute that indicates whether the
      // connection handler should be enabled.  If it does not, or if it is not
      // set to "true", then skip it.
      int msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED;
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                                      getMessage(msgID), false);
      try
      {
        BooleanConfigAttribute enabledAttr =
             (BooleanConfigAttribute)
             connectionHandlerEntry.getConfigAttribute(enabledStub);
        if (enabledAttr == null)
        {
          // The attribute is not present, so this connection handler will be
          // disabled.  Log a message and continue.
          msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
          String message = getMessage(msgID,
                                      String.valueOf(connectionHandlerDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
        else if (! enabledAttr.activeValue())
        {
          // The connection handler is explicitly disabled.  Log a mild warning
          // and continue.
          msgID = MSGID_CONFIG_CONNHANDLER_DISABLED;
          String message = getMessage(msgID,
                                      String.valueOf(connectionHandlerDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.INFORMATIONAL, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, String.valueOf(connectionHandlerDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that specifies the class name
      // for the connection handler implementation.  If it does, then load it
      // and make sure that it's a valid connection handler implementation.
      // If there is no such attribute, the specified class cannot be loaded, or
      // it does not contain a valid connection handler implementation, then log
      // an error and skip it.
      String className;
      msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS;
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_CONNECTION_HANDLER_CLASS,
                                     getMessage(msgID), true, false, true);
      try
      {
        StringConfigAttribute classAttr =
             (StringConfigAttribute)
             connectionHandlerEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          msgID = MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR;
          String message = getMessage(msgID,
                                      String.valueOf(connectionHandlerDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          className = classAttr.activeValue();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS;
        String message = getMessage(msgID, String.valueOf(connectionHandlerDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }

      ConnectionHandler connectionHandler;
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        Class connectionHandlerClass = Class.forName(className);
        connectionHandler =
             (ConnectionHandler) connectionHandlerClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INSTANTIATE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(connectionHandlerDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Perform the necessary initialization for the connection handler.
      try
      {
        connectionHandler.initializeConnectionHandler(connectionHandlerEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INITIALIZE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(connectionHandlerDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Put this connection handler in the hash so that we will be able to find
      // it if it is altered.
      connectionHandlers.put(connectionHandlerDN, connectionHandler);


      // Register the connection handler with the Directory Server.
      DirectoryServer.registerConnectionHandler(connectionHandler);


      // Note that we don't want to start the connection handler because we're
      // still in the startup process.  Therefore, we will not do so and allow
      // the server to start it at the very end of the initialization process.
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
    DN connectionHandlerDN = configEntry.getDN();


    // Register as a change listener for this connection handler entry so that
    // we will be notified of any changes that may be made to it.
    configEntry.registerChangeListener(this);


    // Check to see if this entry appears to contain a connection handler
    // configuration.  If not, then it is unacceptable.
    try
    {
      SearchFilter connectionHandlerFilter =
           SearchFilter.createFilterFromString("(objectClass=" +
                                               OC_CONNECTION_HANDLER + ")");
      if (! connectionHandlerFilter.matchesEntry(
                                         configEntry.getEntry()))
      {
        int msgID =
             MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
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

      int msgID =
           MSGID_CONFIG_CONNHANDLER_ERROR_INTERACTING_WITH_CONNHANDLER_ENTRY;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that indicates whether the
    // connection handler should be enabled.  If it does not, then reject it.
    int msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present, which is not acceptable.
        msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
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

      msgID = MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the class name for
    // the connection handler implementation.  If it does, then load it and make
    // sure that it's a valid connection handler implementation.  If there is no
    // such attribute, the specified class cannot be loaded, or it does not
    // contain a valid connection handler implementation, then it is
    // unacceptable.
    String className;
    msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_CONNECTION_HANDLER_CLASS,
                                   getMessage(msgID), true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
        unacceptableReason.append(message);
        return false;
      }
      else
      {
        className = classAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }

    try
    {
      // FIXME -- Should we use a custom class loader for this?
      Class connectionHandlerClass = Class.forName(className);
      ConnectionHandler connectionHandler =
           (ConnectionHandler) connectionHandlerClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INSTANTIATE;
      unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // connection handler, then the handler itself will need to make that
    // determination.
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
    DN connectionHandlerDN = configEntry.getDN();
    ConnectionHandler connectionHandler =
         connectionHandlers.get(connectionHandlerDN);

    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Check to see if this entry appears to contain a connection handler
    // configuration.  If not, then skip it.
    if (! configEntry.hasObjectClass(OC_CONNECTION_HANDLER))
    {
        int msgID =
             MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG;
        messages.add(getMessage(msgID, String.valueOf(connectionHandlerDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
    }


    // See if the entry contains an attribute that indicates whether the
    // connection handler should be enabled.
    boolean needToEnable = false;
    int msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present.  We won't allow this.
        msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(connectionHandlerDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (enabledAttr.activeValue())
      {
        // The connection handler is marked as enabled.  See if that is already
        // true.
        if (connectionHandler == null)
        {
          needToEnable = true;
        }
        else
        {
          // It's already enabled, so we don't need to do anything.
        }
      }
      else
      {
        // The connection handler is marked as disabled.  See if that is already
        // true.
        if (connectionHandler != null)
        {
          // It isn't disabled, so we will do so now and deregister it from the
          // server.  We'll try to preserve existing connections if possible.
          DirectoryServer.deregisterConnectionHandler(connectionHandler);
          connectionHandlers.remove(connectionHandlerDN);

          int id = MSGID_CONNHANDLER_CLOSED_BY_DISABLE;
          connectionHandler.finalizeConnectionHandler(getMessage(id), false);

          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // It's already disabled, so we don't need to do anything.
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

      msgID = MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(connectionHandlerDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the class name
    // for the connection handler implementation.  If it does, then load it and
    // make sure that it's a valid connection handler.  If there is no such
    // attribute, the specified class cannot be loaded, or it does not contain a
    // valid connection handler implementation, then flag an error and skip it.
    String className;
    msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_CONNECTION_HANDLER_CLASS,
                                   getMessage(msgID), true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR;
        messages.add(getMessage(msgID, String.valueOf(connectionHandlerDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        className = classAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS;
      messages.add(getMessage(msgID, String.valueOf(connectionHandlerDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // If the connection handler is currently active, then we don't need to do
    // anything.  Changes to the class name cannot be applied dynamically, so
    // if the class name did change then indicate that administrative action
    // is required for that change to take effect.
    if (connectionHandler != null)
    {
      if (! className.equals(connectionHandler.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // If we've gotten here, then that should mean that we need to enable the
    // connection handler.  Try to do so.
    if (needToEnable)
    {
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        Class handlerClass = Class.forName(className);
        connectionHandler = (ConnectionHandler) handlerClass.newInstance();
      }
      catch (Exception e)
      {
        // It is not a valid connection handler class.  This is an error.
        msgID = MSGID_CONFIG_CONNHANDLER_CLASS_NOT_CONNHANDLER;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(connectionHandlerDN)));
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      try
      {
        connectionHandler.initializeConnectionHandler(configEntry);
        connectionHandler.start();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INITIALIZE;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(connectionHandlerDN),
                                stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      connectionHandlers.put(connectionHandlerDN, connectionHandler);
      DirectoryServer.registerConnectionHandler(connectionHandler);
    }


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
    DN connectionHandlerDN = configEntry.getDN();


    // Register as a change listener for this connection handler entry so that
    // we will be notified of any changes that may be made to it.
    configEntry.registerChangeListener(this);


    // Check to see if this entry appears to contain a connection handler
    // configuration.  If not, then it is unacceptable.
    try
    {
      SearchFilter connectionHandlerFilter =
           SearchFilter.createFilterFromString("(objectClass=" +
                                               OC_CONNECTION_HANDLER + ")");
      if (! connectionHandlerFilter.matchesEntry(
                                         configEntry.getEntry()))
      {
        int msgID =
             MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
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

      int msgID =
           MSGID_CONFIG_CONNHANDLER_ERROR_INTERACTING_WITH_CONNHANDLER_ENTRY;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that indicates whether the
    // connection handler should be enabled.  If it does not, then reject it.
    int msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present, which is not acceptable.
        msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
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

      msgID = MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the class name for
    // the connection handler implementation.  If it does, then load it and make
    // sure that it's a valid connection handler implementation.  If there is no
    // such attribute, the specified class cannot be loaded, or it does not
    // contain a valid connection handler implementation, then it is
    // unacceptable.
    String className;
    msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_CONNECTION_HANDLER_CLASS,
                                   getMessage(msgID), true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR;
        String message = getMessage(msgID,
                                    String.valueOf(connectionHandlerDN));
        unacceptableReason.append(message);
        return false;
      }
      else
      {
        className = classAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }

    ConnectionHandler connectionHandler;
    try
    {
      // FIXME -- Should we use a custom class loader for this?
      Class connectionHandlerClass = Class.forName(className);
      connectionHandler =
           (ConnectionHandler) connectionHandlerClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INSTANTIATE;
      unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                           String.valueOf(connectionHandlerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // If the connection handler is a configurable component, then make sure
    // that its configuration is valid.
    if (connectionHandler instanceof ConfigurableComponent)
    {
      ConfigurableComponent cc = (ConfigurableComponent) connectionHandler;
      LinkedList<String> errorMessages = new LinkedList<String>();
      if (! cc.hasAcceptableConfiguration(configEntry, errorMessages))
      {
        if (errorMessages.isEmpty())
        {
          msgID = MSGID_CONFIG_CONNHANDLER_UNACCEPTABLE_CONFIG;
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


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // connection handler, then the handler itself will need to make that
    // determination.
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


    // Register as a change listener for this connection handler entry so that
    // we will be notified of any changes that may be made to it.
    configEntry.registerChangeListener(this);


    // Check to see if this entry appears to contain a connection handler
    // configuration.  If not, log a warning and skip it.
    if (! configEntry.hasObjectClass(OC_CONNECTION_HANDLER))
    {
      int msgID =
           MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.CONSTRAINT_VIOLATION;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that indicates whether the
    // connection handler should be enabled.  If it does not, or if it is not
    // set to "true", then skip it.
    int msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present, so this connection handler will be
        // disabled.  We don't need to do anything else with this entry.
        msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (! enabledAttr.activeValue())
      {
        // The connection handler is explicitly disabled.  We don't need to do
        // anything else with this entry.
        msgID = MSGID_CONFIG_CONNHANDLER_DISABLED;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
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

      msgID = MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the class name
    // for the connection handler implementation.  If it does, then load it
    // and make sure that it's a valid connection handler implementation.
    // If there is no such attribute, the specified class cannot be loaded, or
    // it does not contain a valid connection handler implementation, then log
    // an error and skip it.
    String className;
    msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_CONNECTION_HANDLER_CLASS,
                                   getMessage(msgID), true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        className = classAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    ConnectionHandler connectionHandler;
    try
    {
      // FIXME -- Should we use a custom class loader for this?
      Class connectionHandlerClass = Class.forName(className);
      connectionHandler =
           (ConnectionHandler) connectionHandlerClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INSTANTIATE;
      messages.add(getMessage(msgID, String.valueOf(className),
                              String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Perform the necessary initialization for the connection handler.
    try
    {
      connectionHandler.initializeConnectionHandler(configEntry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_CANNOT_INITIALIZE;
      messages.add(getMessage(msgID, String.valueOf(className),
                              String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Put this connection handler in the hash so that we will be able to find
    // it if it is altered.
    connectionHandlers.put(configEntryDN, connectionHandler);


    // Register the connection handler with the Directory Server.
    DirectoryServer.registerConnectionHandler(connectionHandler);


    // Since this method should only be called if the directory server is
    // online, then we will want to actually start the connection handler.  Do
    // so now.
    connectionHandler.start();


    // At this point, everything should be done so return success.
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


    // See if the entry is registered as a connection handler.  If so,
    // deregister and stop it.  We'll try to leave any established connections
    // alone if possible.
    ConnectionHandler connectionHandler =
         connectionHandlers.get(configEntryDN);
    if (connectionHandler != null)
    {
      DirectoryServer.deregisterConnectionHandler(connectionHandler);

      int id = MSGID_CONNHANDLER_CLOSED_BY_DELETE;
      connectionHandler.finalizeConnectionHandler(getMessage(id), false);
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
  }
}

