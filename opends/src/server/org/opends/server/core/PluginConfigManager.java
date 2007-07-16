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



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.admin.std.server.PluginRootCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.IntermediateResponsePluginResult;
import org.opends.server.api.plugin.LDIFPluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PostConnectPluginResult;
import org.opends.server.api.plugin.PostDisconnectPluginResult;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PostResponsePluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.api.plugin.SearchEntryPluginResult;
import org.opends.server.api.plugin.SearchReferencePluginResult;
import org.opends.server.api.plugin.StartupPluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationBindOperation;
import org.opends.server.types.operation.PostOperationCompareOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseBindOperation;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseSearchOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationBindOperation;
import org.opends.server.types.operation.PreOperationCompareOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseBindOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import org.opends.server.workflowelement.localbackend.*;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of plugins defined in the Directory Server.  It will perform the
 * necessary initialization of those plugins when the server is first started,
 * and then will manage any changes to them while the server is running.  It
 * also provides methods for invoking all the plugins of a given type.
 */
public class PluginConfigManager
       implements ConfigurationAddListener<PluginCfg>,
                  ConfigurationDeleteListener<PluginCfg>,
                  ConfigurationChangeListener<PluginCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Arrays for holding the plugins of each type.
  private DirectoryServerPlugin[] startupPlugins;
  private DirectoryServerPlugin[] shutdownPlugins;
  private DirectoryServerPlugin[] postConnectPlugins;
  private DirectoryServerPlugin[] postDisconnectPlugins;
  private DirectoryServerPlugin[] ldifImportPlugins;
  private DirectoryServerPlugin[] ldifExportPlugins;
  private DirectoryServerPlugin[] preParseAbandonPlugins;
  private DirectoryServerPlugin[] preParseAddPlugins;
  private DirectoryServerPlugin[] preParseBindPlugins;
  private DirectoryServerPlugin[] preParseComparePlugins;
  private DirectoryServerPlugin[] preParseDeletePlugins;
  private DirectoryServerPlugin[] preParseExtendedPlugins;
  private DirectoryServerPlugin[] preParseModifyPlugins;
  private DirectoryServerPlugin[] preParseModifyDNPlugins;
  private DirectoryServerPlugin[] preParseSearchPlugins;
  private DirectoryServerPlugin[] preParseUnbindPlugins;
  private DirectoryServerPlugin[] preOperationAddPlugins;
  private DirectoryServerPlugin[] preOperationBindPlugins;
  private DirectoryServerPlugin[] preOperationComparePlugins;
  private DirectoryServerPlugin[] preOperationDeletePlugins;
  private DirectoryServerPlugin[] preOperationExtendedPlugins;
  private DirectoryServerPlugin[] preOperationModifyPlugins;
  private DirectoryServerPlugin[] preOperationModifyDNPlugins;
  private DirectoryServerPlugin[] preOperationSearchPlugins;
  private DirectoryServerPlugin[] postOperationAbandonPlugins;
  private DirectoryServerPlugin[] postOperationAddPlugins;
  private DirectoryServerPlugin[] postOperationBindPlugins;
  private DirectoryServerPlugin[] postOperationComparePlugins;
  private DirectoryServerPlugin[] postOperationDeletePlugins;
  private DirectoryServerPlugin[] postOperationExtendedPlugins;
  private DirectoryServerPlugin[] postOperationModifyPlugins;
  private DirectoryServerPlugin[] postOperationModifyDNPlugins;
  private DirectoryServerPlugin[] postOperationSearchPlugins;
  private DirectoryServerPlugin[] postOperationUnbindPlugins;
  private DirectoryServerPlugin[] postResponseAddPlugins;
  private DirectoryServerPlugin[] postResponseBindPlugins;
  private DirectoryServerPlugin[] postResponseComparePlugins;
  private DirectoryServerPlugin[] postResponseDeletePlugins;
  private DirectoryServerPlugin[] postResponseExtendedPlugins;
  private DirectoryServerPlugin[] postResponseModifyPlugins;
  private DirectoryServerPlugin[] postResponseModifyDNPlugins;
  private DirectoryServerPlugin[] postResponseSearchPlugins;
  private DirectoryServerPlugin[] searchResultEntryPlugins;
  private DirectoryServerPlugin[] searchResultReferencePlugins;
  private DirectoryServerPlugin[] intermediateResponsePlugins;


  // The mapping between the DN of a plugin entry and the plugin instance loaded
  // from that entry.
  private ConcurrentHashMap<DN,
               DirectoryServerPlugin<? extends PluginCfg>>
                    registeredPlugins;

  // The plugin root configuration read at server startup.
  private PluginRootCfg pluginRootConfig;

  // The lock that will provide threadsafe access to the sets of registered
  // plugins.
  private ReentrantLock pluginLock;



  /**
   * Creates a new instance of this plugin config manager.
   */
  public PluginConfigManager()
  {
    pluginLock = new ReentrantLock();

    startupPlugins               = new DirectoryServerPlugin[0];
    shutdownPlugins              = new DirectoryServerPlugin[0];
    postConnectPlugins           = new DirectoryServerPlugin[0];
    postDisconnectPlugins        = new DirectoryServerPlugin[0];
    ldifImportPlugins            = new DirectoryServerPlugin[0];
    ldifExportPlugins            = new DirectoryServerPlugin[0];
    preParseAbandonPlugins       = new DirectoryServerPlugin[0];
    preParseAddPlugins           = new DirectoryServerPlugin[0];
    preParseBindPlugins          = new DirectoryServerPlugin[0];
    preParseComparePlugins       = new DirectoryServerPlugin[0];
    preParseDeletePlugins        = new DirectoryServerPlugin[0];
    preParseExtendedPlugins      = new DirectoryServerPlugin[0];
    preParseModifyPlugins        = new DirectoryServerPlugin[0];
    preParseModifyDNPlugins      = new DirectoryServerPlugin[0];
    preParseSearchPlugins        = new DirectoryServerPlugin[0];
    preParseUnbindPlugins        = new DirectoryServerPlugin[0];
    preOperationAddPlugins       = new DirectoryServerPlugin[0];
    preOperationBindPlugins      = new DirectoryServerPlugin[0];
    preOperationComparePlugins   = new DirectoryServerPlugin[0];
    preOperationDeletePlugins    = new DirectoryServerPlugin[0];
    preOperationExtendedPlugins  = new DirectoryServerPlugin[0];
    preOperationModifyPlugins    = new DirectoryServerPlugin[0];
    preOperationModifyDNPlugins  = new DirectoryServerPlugin[0];
    preOperationSearchPlugins    = new DirectoryServerPlugin[0];
    postOperationAbandonPlugins  = new DirectoryServerPlugin[0];
    postOperationAddPlugins      = new DirectoryServerPlugin[0];
    postOperationBindPlugins     = new DirectoryServerPlugin[0];
    postOperationComparePlugins  = new DirectoryServerPlugin[0];
    postOperationDeletePlugins   = new DirectoryServerPlugin[0];
    postOperationExtendedPlugins = new DirectoryServerPlugin[0];
    postOperationModifyPlugins   = new DirectoryServerPlugin[0];
    postOperationModifyDNPlugins = new DirectoryServerPlugin[0];
    postOperationSearchPlugins   = new DirectoryServerPlugin[0];
    postOperationUnbindPlugins   = new DirectoryServerPlugin[0];
    postResponseAddPlugins       = new DirectoryServerPlugin[0];
    postResponseBindPlugins      = new DirectoryServerPlugin[0];
    postResponseComparePlugins   = new DirectoryServerPlugin[0];
    postResponseDeletePlugins    = new DirectoryServerPlugin[0];
    postResponseExtendedPlugins  = new DirectoryServerPlugin[0];
    postResponseModifyPlugins    = new DirectoryServerPlugin[0];
    postResponseModifyDNPlugins  = new DirectoryServerPlugin[0];
    postResponseSearchPlugins    = new DirectoryServerPlugin[0];
    searchResultEntryPlugins     = new DirectoryServerPlugin[0];
    searchResultReferencePlugins = new DirectoryServerPlugin[0];
    intermediateResponsePlugins  = new DirectoryServerPlugin[0];
    registeredPlugins            =
         new ConcurrentHashMap<DN,
                  DirectoryServerPlugin<? extends PluginCfg>>();
  }



  /**
   * Initializes the configuration associated with the Directory Server plugins.
   * This should only be called at Directory Server startup.
   *
   * @param  pluginTypes  The set of plugin types for the plugins to initialize,
   *                      or <CODE>null</CODE> to initialize all types of
   *                      plugins defined in the server configuration.  In
   *                      general, this should only be non-null for cases in
   *                      which the server is running in a special mode that
   *                      only uses a minimal set of plugins (e.g., LDIF import
   *                      or export).
   *
   * @throws  ConfigException  If a critical configuration problem prevents the
   *                           plugin initialization from succeeding.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the plugins that is not related to the
   *                                   server configuration.
   */
  public void initializePluginConfig(Set<PluginType> pluginTypes)
         throws ConfigException, InitializationException
  {
    registeredPlugins.clear();


    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Get the plugin root configuration and register with it as an add and
    // delete listener so we can be notified if any plugin entries are added or
    // removed.
    pluginRootConfig = rootConfiguration.getPluginRoot();
    pluginRootConfig.addPluginAddListener(this);
    pluginRootConfig.addPluginDeleteListener(this);


    //Initialize the existing plugins.
    for (String pluginName : pluginRootConfig.listPlugins())
    {
      PluginCfg pluginConfiguration = pluginRootConfig.getPlugin(pluginName);

      if (! pluginConfiguration.isEnabled())
      {
        continue;
      }

      // Create a set of plugin types for the plugin.
      HashSet<PluginType> initTypes = new HashSet<PluginType>();
      for (PluginCfgDefn.PluginType pluginType :
           pluginConfiguration.getPluginType())
      {
        PluginType t = getPluginType(pluginType);
        if ((pluginTypes == null) || pluginTypes.contains(t))
        {
          initTypes.add(t);
        }
      }

      if (initTypes.isEmpty())
      {
        continue;
      }

      pluginConfiguration.addChangeListener(this);

      try
      {
        DirectoryServerPlugin<? extends PluginCfg> plugin =
             loadPlugin(pluginConfiguration.getPluginClass(), initTypes,
                        pluginConfiguration, true);
        registerPlugin(plugin, pluginConfiguration.dn(), initTypes);
      }
      catch (InitializationException ie)
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 ie.getMessage(), ie.getMessageID());
        continue;
      }
    }
  }



  /**
   * Loads the specified class, instantiates it as a plugin, and optionally
   * initializes that plugin.
   *
   * @param  className      The fully-qualified name of the plugin class to
   *                        load, instantiate, and initialize.
   * @param  pluginTypes    The set of plugin types for the plugins to
   *                        initialize, or {@code null} to initialize all types
   *                        of plugins defined in the server configuration.  In
   *                        general, this should only be non-null for cases in
   *                        which the server is running in a special mode that
   *                        only uses a minimal set of plugins (e.g., LDIF
   *                        import or export).
   * @param  configuration  The configuration to use to initialize the plugin.
   *                        It must not be {@code null}.
   * @param  initialize     Indicates whether the plugin instance should be
   *                        initialized.
   *
   * @return  The possibly initialized plugin.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the plugin.
   */
  private DirectoryServerPlugin<? extends PluginCfg>
               loadPlugin(String className, Set<PluginType> pluginTypes,
                          PluginCfg configuration, boolean initialize)
          throws InitializationException
  {
    try
    {
      PluginCfgDefn definition =
           PluginCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getPluginClassPropertyDefinition();
      Class<? extends DirectoryServerPlugin> pluginClass =
           propertyDefinition.loadClass(className, DirectoryServerPlugin.class);
      DirectoryServerPlugin<? extends PluginCfg> plugin =
           (DirectoryServerPlugin<? extends PluginCfg>)
           pluginClass.newInstance();

      if (initialize)
      {
        Method method = plugin.getClass().getMethod("initializeInternal",
                                                    DN.class, Set.class);
        method.invoke(plugin, configuration.dn(), pluginTypes);

        method = plugin.getClass().getMethod("initializePlugin", Set.class,
                      configuration.definition().getServerConfigurationClass());
        method.invoke(plugin, pluginTypes, configuration);
      }
      else
      {
        Method method = plugin.getClass().getMethod("isConfigurationAcceptable",
                                                    PluginCfg.class,
                                                    List.class);

        List<String> unacceptableReasons = new ArrayList<String>();
        Boolean acceptable = (Boolean) method.invoke(plugin, configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<String> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          int    msgID   = MSGID_CONFIG_PLUGIN_CONFIG_NOT_ACCEPTABLE;
          String message = getMessage(msgID, String.valueOf(configuration.dn()),
                                      buffer.toString());
          throw new InitializationException(msgID, message);
        }
      }

      return plugin;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_PLUGIN_CANNOT_INITIALIZE;
      String message = getMessage(msgID, className,
                                  String.valueOf(configuration.dn()),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }



  /**
   * Gets the OpenDS plugin type object that corresponds to the configuration
   * counterpart.
   *
   * @param  configPluginType  The configuration plugin type for which to
   *                           retrieve the OpenDS plugin type.
   */
  private PluginType getPluginType(PluginCfgDefn.PluginType
                                        configPluginType)
  {
    switch (configPluginType)
    {
      case STARTUP:                return PluginType.STARTUP;
      case SHUTDOWN:               return PluginType.SHUTDOWN;
      case POSTCONNECT:            return PluginType.POST_CONNECT;
      case POSTDISCONNECT:         return PluginType.POST_DISCONNECT;
      case LDIFIMPORT:             return PluginType.LDIF_IMPORT;
      case LDIFEXPORT:             return PluginType.LDIF_EXPORT;
      case PREPARSEABANDON:        return PluginType.PRE_PARSE_ABANDON;
      case PREPARSEADD:            return PluginType.PRE_PARSE_ADD;
      case PREPARSEBIND:           return PluginType.PRE_PARSE_BIND;
      case PREPARSECOMPARE:        return PluginType.PRE_PARSE_COMPARE;
      case PREPARSEDELETE:         return PluginType.PRE_PARSE_DELETE;
      case PREPARSEEXTENDED:       return PluginType.PRE_PARSE_EXTENDED;
      case PREPARSEMODIFY:         return PluginType.PRE_PARSE_MODIFY;
      case PREPARSEMODIFYDN:       return PluginType.PRE_PARSE_MODIFY_DN;
      case PREPARSESEARCH:         return PluginType.PRE_PARSE_SEARCH;
      case PREPARSEUNBIND:         return PluginType.PRE_PARSE_UNBIND;
      case PREOPERATIONADD:        return PluginType.PRE_OPERATION_ADD;
      case PREOPERATIONBIND:       return PluginType.PRE_OPERATION_BIND;
      case PREOPERATIONCOMPARE:    return PluginType.PRE_OPERATION_COMPARE;
      case PREOPERATIONDELETE:     return PluginType.PRE_OPERATION_DELETE;
      case PREOPERATIONEXTENDED:   return PluginType.PRE_OPERATION_EXTENDED;
      case PREOPERATIONMODIFY:     return PluginType.PRE_OPERATION_MODIFY;
      case PREOPERATIONMODIFYDN:   return PluginType.PRE_OPERATION_MODIFY_DN;
      case PREOPERATIONSEARCH:     return PluginType.PRE_OPERATION_SEARCH;
      case POSTOPERATIONABANDON:   return PluginType.POST_OPERATION_ABANDON;
      case POSTOPERATIONADD:       return PluginType.POST_OPERATION_ADD;
      case POSTOPERATIONBIND:      return PluginType.POST_OPERATION_BIND;
      case POSTOPERATIONCOMPARE:   return PluginType.POST_OPERATION_COMPARE;
      case POSTOPERATIONDELETE:    return PluginType.POST_OPERATION_DELETE;
      case POSTOPERATIONEXTENDED:  return PluginType.POST_OPERATION_EXTENDED;
      case POSTOPERATIONMODIFY:    return PluginType.POST_OPERATION_MODIFY;
      case POSTOPERATIONMODIFYDN:  return PluginType.POST_OPERATION_MODIFY_DN;
      case POSTOPERATIONSEARCH:    return PluginType.POST_OPERATION_SEARCH;
      case POSTOPERATIONUNBIND:    return PluginType.POST_OPERATION_UNBIND;
      case POSTRESPONSEADD:        return PluginType.POST_RESPONSE_ADD;
      case POSTRESPONSEBIND:       return PluginType.POST_RESPONSE_BIND;
      case POSTRESPONSECOMPARE:    return PluginType.POST_RESPONSE_COMPARE;
      case POSTRESPONSEDELETE:     return PluginType.POST_RESPONSE_DELETE;
      case POSTRESPONSEEXTENDED:   return PluginType.POST_RESPONSE_EXTENDED;
      case POSTRESPONSEMODIFY:     return PluginType.POST_RESPONSE_MODIFY;
      case POSTRESPONSEMODIFYDN:   return PluginType.POST_RESPONSE_MODIFY_DN;
      case POSTRESPONSESEARCH:     return PluginType.POST_RESPONSE_SEARCH;
      case SEARCHRESULTENTRY:      return PluginType.SEARCH_RESULT_ENTRY;
      case SEARCHRESULTREFERENCE:  return PluginType.SEARCH_RESULT_REFERENCE;
      case INTERMEDIATERESPONSE:   return PluginType.INTERMEDIATE_RESPONSE;
      default:                     return null;
    }
  }



  /**
   * Finalizes all plugins that are registered with the Directory Server.
   */
  public void finalizePlugins()
  {
    pluginLock.lock();

    try
    {
      Iterator<DirectoryServerPlugin<? extends PluginCfg>> iterator =
           registeredPlugins.values().iterator();
      while (iterator.hasNext())
      {
        try
        {
          iterator.next().finalizePlugin();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }

      registeredPlugins.clear();
    }
    finally
    {
      pluginLock.unlock();
    }
  }



  /**
   * Retrieves the set of plugins that have been registered with the Directory
   * Server.
   *
   * @return  The set of plugins that have been registered with the Directory
   *          Server.
   */
  public ConcurrentHashMap<DN,
              DirectoryServerPlugin<? extends PluginCfg>>
                   getRegisteredPlugins()
  {
    return registeredPlugins;
  }



  /**
   * Retrieves the plugin with the specified configuration entry DN.
   *
   * @param  pluginDN  The DN of the configuration entry for the plugin to
   *                   retrieve.
   *
   * @return  The requested plugin, or <CODE>null</CODE> if there is no such
   *          plugin.
   */
  public DirectoryServerPlugin getRegisteredPlugin(DN pluginDN)
  {
    return registeredPlugins.get(pluginDN);
  }



  /**
   * Registers the provided plugin with this plugin config manager and ensures
   * that it will be invoked in the specified ways.
   *
   * @param  plugin         The plugin to register with the server.
   * @param  pluginEntryDN  The DN of the configuration entry for the provided
   *                        plugin.
   * @param  pluginTypes    The plugin types that will be used to control the
   *                        points at which the provided plugin is invoked.
   */
  private void registerPlugin(
                    DirectoryServerPlugin<? extends PluginCfg> plugin,
                    DN pluginEntryDN, Set<PluginType> pluginTypes)
  {
    pluginLock.lock();

    try
    {
      registeredPlugins.put(pluginEntryDN, plugin);

      for (PluginType t : pluginTypes)
      {
        switch (t)
        {
          case STARTUP:
            startupPlugins =
                 addPlugin(startupPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderStartup());
            break;
          case SHUTDOWN:
            shutdownPlugins =
                 addPlugin(shutdownPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderShutdown());
            break;
          case POST_CONNECT:
            postConnectPlugins =
                 addPlugin(postConnectPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostConnect());
            break;
          case POST_DISCONNECT:
            postDisconnectPlugins =
                 addPlugin(postDisconnectPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostDisconnect());
            break;
          case LDIF_IMPORT:
            ldifImportPlugins =
                 addPlugin(ldifImportPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderLDIFImport());
            break;
          case LDIF_EXPORT:
            ldifExportPlugins =
                 addPlugin(ldifExportPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderLDIFExport());
            break;
          case PRE_PARSE_ABANDON:
            preParseAbandonPlugins =
                 addPlugin(preParseAbandonPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseAbandon());
            break;
          case PRE_PARSE_ADD:
            preParseAddPlugins =
                 addPlugin(preParseAddPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseAdd());
            break;
          case PRE_PARSE_BIND:
            preParseBindPlugins =
                 addPlugin(preParseBindPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseBind());
            break;
          case PRE_PARSE_COMPARE:
            preParseComparePlugins =
                 addPlugin(preParseComparePlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseCompare());
            break;
          case PRE_PARSE_DELETE:
            preParseDeletePlugins =
                 addPlugin(preParseDeletePlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseDelete());
            break;
          case PRE_PARSE_EXTENDED:
            preParseExtendedPlugins =
                 addPlugin(preParseExtendedPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseExtended());
            break;
          case PRE_PARSE_MODIFY:
            preParseModifyPlugins =
                 addPlugin(preParseModifyPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseModify());
            break;
          case PRE_PARSE_MODIFY_DN:
            preParseModifyDNPlugins =
                 addPlugin(preParseModifyDNPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseModifyDN());
            break;
          case PRE_PARSE_SEARCH:
            preParseSearchPlugins =
                 addPlugin(preParseSearchPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseSearch());
            break;
          case PRE_PARSE_UNBIND:
            preParseUnbindPlugins =
                 addPlugin(preParseUnbindPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreParseUnbind());
            break;
          case PRE_OPERATION_ADD:
            preOperationAddPlugins =
                 addPlugin(preOperationAddPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreOperationAdd());
            break;
          case PRE_OPERATION_BIND:
            preOperationBindPlugins =
                 addPlugin(preOperationBindPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreOperationBind());
            break;
          case PRE_OPERATION_COMPARE:
            preOperationComparePlugins =
                 addPlugin(preOperationComparePlugins,plugin, t,
                      pluginRootConfig.getPluginOrderPreOperationCompare());
            break;
          case PRE_OPERATION_DELETE:
            preOperationDeletePlugins =
                 addPlugin(preOperationDeletePlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreOperationDelete());
            break;
          case PRE_OPERATION_EXTENDED:
            preOperationExtendedPlugins =
                 addPlugin(preOperationExtendedPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPreOperationExtended());
            break;
          case PRE_OPERATION_MODIFY:
            preOperationModifyPlugins =
                 addPlugin(preOperationModifyPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreOperationModify());
            break;
          case PRE_OPERATION_MODIFY_DN:
            preOperationModifyDNPlugins =
                 addPlugin(preOperationModifyDNPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPreOperationModifyDN());
            break;
          case PRE_OPERATION_SEARCH:
            preOperationSearchPlugins =
                 addPlugin(preOperationSearchPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPreOperationSearch());
            break;
          case POST_OPERATION_ABANDON:
            postOperationAbandonPlugins =
                 addPlugin(postOperationAbandonPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationAbandon());
            break;
          case POST_OPERATION_ADD:
            postOperationAddPlugins =
                 addPlugin(postOperationAddPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostOperationAdd());
            break;
          case POST_OPERATION_BIND:
            postOperationBindPlugins =
                 addPlugin(postOperationBindPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostOperationBind());
            break;
          case POST_OPERATION_COMPARE:
            postOperationComparePlugins =
                 addPlugin(postOperationComparePlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationCompare());
            break;
          case POST_OPERATION_DELETE:
            postOperationDeletePlugins =
                 addPlugin(postOperationDeletePlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationDelete());
            break;
          case POST_OPERATION_EXTENDED:
            postOperationExtendedPlugins =
                 addPlugin(postOperationExtendedPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationExtended());
            break;
          case POST_OPERATION_MODIFY:
            postOperationModifyPlugins =
                 addPlugin(postOperationModifyPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationModify());
            break;
          case POST_OPERATION_MODIFY_DN:
            postOperationModifyDNPlugins =
                 addPlugin(postOperationModifyDNPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationModifyDN());
            break;
          case POST_OPERATION_SEARCH:
            postOperationSearchPlugins =
                 addPlugin(postOperationSearchPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationSearch());
            break;
          case POST_OPERATION_UNBIND:
            postOperationUnbindPlugins =
                 addPlugin(postOperationUnbindPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostOperationUnbind());
            break;
          case POST_RESPONSE_ADD:
            postResponseAddPlugins =
                 addPlugin(postResponseAddPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostResponseAdd());
            break;
          case POST_RESPONSE_BIND:
            postResponseBindPlugins =
                 addPlugin(postResponseBindPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostResponseBind());
            break;
          case POST_RESPONSE_COMPARE:
            postResponseComparePlugins =
                 addPlugin(postResponseComparePlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostResponseCompare());
            break;
          case POST_RESPONSE_DELETE:
            postResponseDeletePlugins =
                 addPlugin(postResponseDeletePlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostResponseDelete());
            break;
          case POST_RESPONSE_EXTENDED:
            postResponseExtendedPlugins =
                 addPlugin(postResponseExtendedPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostResponseExtended());
            break;
          case POST_RESPONSE_MODIFY:
            postResponseModifyPlugins =
                 addPlugin(postResponseModifyPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostResponseModify());
            break;
          case POST_RESPONSE_MODIFY_DN:
            postResponseModifyDNPlugins =
                 addPlugin(postResponseModifyDNPlugins, plugin, t,
                      pluginRootConfig.getPluginOrderPostResponseModifyDN());
            break;
          case POST_RESPONSE_SEARCH:
            postResponseSearchPlugins =
                 addPlugin(postResponseSearchPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderPostResponseSearch());
            break;
          case SEARCH_RESULT_ENTRY:
            searchResultEntryPlugins =
                 addPlugin(searchResultEntryPlugins, plugin, t,
                           pluginRootConfig.getPluginOrderSearchResultEntry());
            break;
          case SEARCH_RESULT_REFERENCE:
            searchResultReferencePlugins =
                 addPlugin(searchResultReferencePlugins, plugin, t,
                      pluginRootConfig.getPluginOrderSearchResultReference());
            break;
          case INTERMEDIATE_RESPONSE:
            intermediateResponsePlugins =
                 addPlugin(intermediateResponsePlugins, plugin, t,
                      pluginRootConfig.getPluginOrderIntermediateResponse());
            break;
          default:
        }
      }
    }
    finally
    {
      pluginLock.unlock();
    }
  }



  /**
   * Adds the provided plugin to the given array.  The provided array will not
   * itself be modified, but rather a new array will be created with one
   * additional element.  The provided plugin will be the last element in the
   * new array.
   * <BR><BR>
   * Note that the only use of this method outside of this class should be for
   * testing purposes.
   *
   * @param  pluginArray  The array containing the existing set of plugins.
   * @param  plugin       The plugin to be added to the array.
   * @param  pluginType   The plugin type for the plugin being registered.
   * @param  pluginOrder  A string that represents the order in which plugins of
   *                      this type should be invoked, or {@code null} if the
   *                      order is not considered important.
   *
   * @return  The new array containing the new set of plugins.
   */
  static DirectoryServerPlugin[] addPlugin(DirectoryServerPlugin[] pluginArray,
                                           DirectoryServerPlugin plugin,
                                           PluginType pluginType,
                                           String pluginOrder)
  {
    // If the provided plugin order string is null, empty, or contains only a
    // wildcard, then simply add the new plugin to the end of the list.
    // Otherwise, parse the order string and figure out where to put the
    // provided plugin.
    if ((pluginOrder == null) ||
        ((pluginOrder = pluginOrder.trim()).length() == 0) ||
        pluginOrder.equals("*"))
    {
      DirectoryServerPlugin[] newPlugins =
           new DirectoryServerPlugin[pluginArray.length+1];
      System.arraycopy(pluginArray, 0, newPlugins, 0, pluginArray.length);
      newPlugins[pluginArray.length] = plugin;

      return newPlugins;
    }
    else
    {
      // Parse the plugin order into initial and final plugin names.
      boolean starFound = false;
      LinkedHashSet<String> initialPluginNames = new LinkedHashSet<String>();
      LinkedHashSet<String> finalPluginNames   = new LinkedHashSet<String>();

      StringTokenizer tokenizer = new StringTokenizer(pluginOrder, ",");
      while (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken().trim();
        if (token.length() == 0)
        {
          // Only log the warning once per plugin type.  The plugin array will
          // be empty the first time through, so we can use that to make the
          // determination.
          if (pluginArray.length == 0)
          {
            int    msgID   = MSGID_CONFIG_PLUGIN_EMPTY_ELEMENT_IN_ORDER;
            String message = getMessage(msgID, pluginType.getName());
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        else if (token.equals("*"))
        {
          if (starFound)
          {
            // Only log the warning once per plugin type.  The plugin array will
            // be empty the first time through, so we can use that to make the
            // determination.
            if (pluginArray.length == 0)
            {
              int    msgID   = MSGID_CONFIG_PLUGIN_MULTIPLE_WILDCARDS_IN_ORDER;
              String message = getMessage(msgID, pluginType.getName());
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }
          }
          else
          {
            starFound = true;
          }
        }
        else
        {
          String lowerName = toLowerCase(token);
          if (starFound)
          {
            if (initialPluginNames.contains(lowerName) ||
                finalPluginNames.contains(lowerName))
            {
              // Only log the warning once per plugin type.  The plugin array
              // will be empty the first time through, so we can use that to
              // make the determination.
              if (pluginArray.length == 0)
              {
                int    msgID   = MSGID_CONFIG_PLUGIN_LISTED_MULTIPLE_TIMES;
                String message = getMessage(msgID, pluginType.getName(), token);
                logError(ErrorLogCategory.CONFIGURATION,
                         ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              }
            }

            finalPluginNames.add(lowerName);
          }
          else
          {
            if (initialPluginNames.contains(lowerName))
            {
              // Only log the warning once per plugin type.  The plugin array
              // will be empty the first time through, so we can use that to
              // make the determination.
              if (pluginArray.length == 0)
              {
                int    msgID   = MSGID_CONFIG_PLUGIN_LISTED_MULTIPLE_TIMES;
                String message = getMessage(msgID, pluginType.getName(), token);
                logError(ErrorLogCategory.CONFIGURATION,
                         ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              }
            }

            initialPluginNames.add(lowerName);
          }
        }
      }

      if (! starFound)
      {
        // Only log the warning once per plugin type.  The plugin array will be
        // empty the first time through, so we can use that to make the
        // determination.
        if (pluginArray.length == 0)
        {
          int    msgID   = MSGID_CONFIG_PLUGIN_ORDER_NO_WILDCARD;
          String message = getMessage(msgID, pluginType.getName());
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }
      }


      // Parse the array of already registered plugins to sort them accordingly.
      HashMap<String,DirectoryServerPlugin> initialPlugins =
           new HashMap<String,DirectoryServerPlugin>(initialPluginNames.size());
      HashMap<String,DirectoryServerPlugin> finalPlugins =
           new HashMap<String,DirectoryServerPlugin>(finalPluginNames.size());
      ArrayList<DirectoryServerPlugin> otherPlugins =
           new ArrayList<DirectoryServerPlugin>();
      for (DirectoryServerPlugin p : pluginArray)
      {
        DN dn = p.getPluginEntryDN();
        String lowerName =
             toLowerCase(dn.getRDN().getAttributeValue(0).getStringValue());
        if (initialPluginNames.contains(lowerName))
        {
          initialPlugins.put(lowerName, p);
        }
        else if (finalPluginNames.contains(lowerName))
        {
          finalPlugins.put(lowerName, p);
        }
        else
        {
          otherPlugins.add(p);
        }
      }


      // Get the name of the provided plugin from its RDN value and put it in
      // the correct category.
      DN dn = plugin.getPluginEntryDN();
      String lowerName =
           toLowerCase(dn.getRDN().getAttributeValue(0).getStringValue());
      if (initialPluginNames.contains(lowerName))
      {
        initialPlugins.put(lowerName, plugin);
      }
      else if (finalPluginNames.contains(lowerName))
      {
        finalPlugins.put(lowerName, plugin);
      }
      else
      {
        otherPlugins.add(plugin);
      }


      // Compile a list of all the plugins in the correct order, convert it to
      // an array, and return it.
      ArrayList<DirectoryServerPlugin> newList =
           new ArrayList<DirectoryServerPlugin>(pluginArray.length+1);
      for (String name : initialPluginNames)
      {
        DirectoryServerPlugin p = initialPlugins.get(name);
        if (p != null)
        {
          newList.add(p);
        }
      }

      newList.addAll(otherPlugins);

      for (String name : finalPluginNames)
      {
        DirectoryServerPlugin p = finalPlugins.get(name);
        if (p != null)
        {
          newList.add(p);
        }
      }

      DirectoryServerPlugin[] newPlugins =
           new DirectoryServerPlugin[newList.size()];
      newList.toArray(newPlugins);
      return newPlugins;
    }
  }



  /**
   * Deregisters the plugin with the provided configuration entry DN.
   *
   * @param  configEntryDN  The DN of the configuration entry for the plugin to
   *                        deregister.
   */
  private void deregisterPlugin(DN configEntryDN)
  {
    pluginLock.lock();

    DirectoryServerPlugin<? extends PluginCfg> plugin;
    try
    {
      plugin = registeredPlugins.remove(configEntryDN);
      if (plugin == null)
      {
        return;
      }

      for (PluginType t : plugin.getPluginTypes())
      {
        switch (t)
        {
          case STARTUP:
            startupPlugins = removePlugin(startupPlugins, plugin);
            break;
          case SHUTDOWN:
            shutdownPlugins = removePlugin(shutdownPlugins, plugin);
            break;
          case POST_CONNECT:
            postConnectPlugins = removePlugin(postConnectPlugins, plugin);
            break;
          case POST_DISCONNECT:
            postDisconnectPlugins = removePlugin(postDisconnectPlugins, plugin);
            break;
          case LDIF_IMPORT:
            ldifImportPlugins = removePlugin(ldifImportPlugins, plugin);
            break;
          case LDIF_EXPORT:
            ldifExportPlugins = removePlugin(ldifExportPlugins, plugin);
            break;
          case PRE_PARSE_ABANDON:
            preParseAbandonPlugins = removePlugin(preParseAbandonPlugins,
                                                  plugin);
            break;
          case PRE_PARSE_ADD:
            preParseAddPlugins = removePlugin(preParseAddPlugins, plugin);
            break;
          case PRE_PARSE_BIND:
            preParseBindPlugins = removePlugin(preParseBindPlugins, plugin);
            break;
          case PRE_PARSE_COMPARE:
            preParseComparePlugins = removePlugin(preParseComparePlugins,
                                                  plugin);
            break;
          case PRE_PARSE_DELETE:
            preParseDeletePlugins = removePlugin(preParseDeletePlugins, plugin);
            break;
          case PRE_PARSE_EXTENDED:
            preParseExtendedPlugins = removePlugin(preParseExtendedPlugins,
                                                   plugin);
            break;
          case PRE_PARSE_MODIFY:
            preParseModifyPlugins = removePlugin(preParseModifyPlugins, plugin);
            break;
          case PRE_PARSE_MODIFY_DN:
            preParseModifyDNPlugins = removePlugin(preParseModifyDNPlugins,
                                                   plugin);
            break;
          case PRE_PARSE_SEARCH:
            preParseSearchPlugins = removePlugin(preParseSearchPlugins, plugin);
            break;
          case PRE_PARSE_UNBIND:
            preParseUnbindPlugins = removePlugin(preParseUnbindPlugins, plugin);
            break;
          case PRE_OPERATION_ADD:
            preOperationAddPlugins = removePlugin(preOperationAddPlugins,
                                                  plugin);
            break;
          case PRE_OPERATION_BIND:
            preOperationBindPlugins = removePlugin(preOperationBindPlugins,
                                                   plugin);
            break;
          case PRE_OPERATION_COMPARE:
            preOperationComparePlugins =
                 removePlugin(preOperationComparePlugins, plugin);
            break;
          case PRE_OPERATION_DELETE:
            preOperationDeletePlugins = removePlugin(preOperationDeletePlugins,
                                                     plugin);
            break;
          case PRE_OPERATION_EXTENDED:
            preOperationExtendedPlugins =
                 removePlugin(preOperationExtendedPlugins, plugin);
            break;
          case PRE_OPERATION_MODIFY:
            preOperationModifyPlugins = removePlugin(preOperationModifyPlugins,
                                                     plugin);
            break;
          case PRE_OPERATION_MODIFY_DN:
            preOperationModifyDNPlugins =
                 removePlugin(preOperationModifyDNPlugins, plugin);
            break;
          case PRE_OPERATION_SEARCH:
            preOperationSearchPlugins = removePlugin(preOperationSearchPlugins,
                                                     plugin);
            break;
          case POST_OPERATION_ABANDON:
            postOperationAbandonPlugins =
                 removePlugin(postOperationAbandonPlugins, plugin);
            break;
          case POST_OPERATION_ADD:
            postOperationAddPlugins = removePlugin(postOperationAddPlugins,
                                                   plugin);
            break;
          case POST_OPERATION_BIND:
            postOperationBindPlugins = removePlugin(postOperationBindPlugins,
                                                    plugin);
            break;
          case POST_OPERATION_COMPARE:
            postOperationComparePlugins =
                 removePlugin(postOperationComparePlugins, plugin);
            break;
          case POST_OPERATION_DELETE:
            postOperationDeletePlugins =
                 removePlugin(postOperationDeletePlugins, plugin);
            break;
          case POST_OPERATION_EXTENDED:
            postOperationExtendedPlugins =
                 removePlugin(postOperationExtendedPlugins, plugin);
            break;
          case POST_OPERATION_MODIFY:
            postOperationModifyPlugins =
                 removePlugin(postOperationModifyPlugins, plugin);
            break;
          case POST_OPERATION_MODIFY_DN:
            postOperationModifyDNPlugins =
                 removePlugin(postOperationModifyDNPlugins, plugin);
            break;
          case POST_OPERATION_SEARCH:
            postOperationSearchPlugins =
                 removePlugin(postOperationSearchPlugins, plugin);
            break;
          case POST_OPERATION_UNBIND:
            postOperationUnbindPlugins =
                 removePlugin(postOperationUnbindPlugins, plugin);
            break;
          case POST_RESPONSE_ADD:
            postResponseAddPlugins = removePlugin(postResponseAddPlugins,
                                                  plugin);
            break;
          case POST_RESPONSE_BIND:
            postResponseBindPlugins = removePlugin(postResponseBindPlugins,
                                                   plugin);
            break;
          case POST_RESPONSE_COMPARE:
            postResponseComparePlugins =
                 removePlugin(postResponseComparePlugins, plugin);
            break;
          case POST_RESPONSE_DELETE:
            postResponseDeletePlugins = removePlugin(postResponseDeletePlugins,
                                                     plugin);
            break;
          case POST_RESPONSE_EXTENDED:
            postResponseExtendedPlugins =
                 removePlugin(postResponseExtendedPlugins, plugin);
            break;
          case POST_RESPONSE_MODIFY:
            postResponseModifyPlugins = removePlugin(postResponseModifyPlugins,
                                                     plugin);
            break;
          case POST_RESPONSE_MODIFY_DN:
            postResponseModifyDNPlugins =
                 removePlugin(postResponseModifyDNPlugins, plugin);
            break;
          case POST_RESPONSE_SEARCH:
            postResponseSearchPlugins = removePlugin(postResponseSearchPlugins,
                                                     plugin);
            break;
          case SEARCH_RESULT_ENTRY:
            searchResultEntryPlugins = removePlugin(searchResultEntryPlugins,
                                                    plugin);
            break;
          case SEARCH_RESULT_REFERENCE:
            searchResultReferencePlugins =
                 removePlugin(searchResultReferencePlugins, plugin);
            break;
          case INTERMEDIATE_RESPONSE:
            intermediateResponsePlugins =
                 removePlugin(intermediateResponsePlugins, plugin);
            break;
          default:
        }
      }
    }
    finally
    {
      pluginLock.unlock();
    }

    plugin.finalizePlugin();
  }



  /**
   * Removes the provided plugin from the given array.  The provided array will
   * not itself be modified, but rather a new array will be created with one
   * fewer element (assuming that the specified plugin was found).
   *
   * @param  pluginArray  The array containing the existing set of plugins.
   * @param  plugin       The plugin to be removed from the array.
   *
   * @return  The new array containing the new set of plugins.
   */
  private DirectoryServerPlugin[]
               removePlugin(DirectoryServerPlugin[] pluginArray,
                            DirectoryServerPlugin plugin)
  {
    int slot   = -1;
    int length = pluginArray.length;
    for (int i=0; i < length; i++)
    {
      if (pluginArray[i].getPluginEntryDN().equals(plugin.getPluginEntryDN()))
      {
        slot = i;
        break;
      }
    }

    if (slot < 0)
    {
      // The plugin wasn't found in the list, so return the same list.
      return pluginArray;
    }


    // If it was the only element in the array, then return an empty array.
    if (length == 0)
    {
      return new DirectoryServerPlugin[0];
    }


    // Create an array that's one element smaller and copy the remaining "good"
    // elements into it.
    DirectoryServerPlugin[] newPlugins = new DirectoryServerPlugin[length-1];
    if (slot > 0)
    {
      System.arraycopy(pluginArray, 0, newPlugins, 0, slot);
    }

    if (slot < (length-1))
    {
      System.arraycopy(pluginArray, slot+1, newPlugins, slot, (length-slot-1));
    }

    return newPlugins;
  }



  /**
   * Invokes the set of startup plugins that have been registered with the
   * Directory Server.
   *
   * @return  The result of processing the startup plugins.
   */
  public StartupPluginResult invokeStartupPlugins()
  {
    StartupPluginResult result = null;

    for (DirectoryServerPlugin p : startupPlugins)
    {
      try
      {
        result = p.doStartup();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_STARTUP_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    stackTraceToSingleLineString(e));

        result = new StartupPluginResult(false, false, msgID, message);
        break;
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_STARTUP_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID,
                                    String.valueOf(p.getPluginEntryDN()));

        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.FATAL_ERROR,
                 message, msgID);
        return new StartupPluginResult(false, false, msgID,message);
      }
      else if (! result.completedSuccessfully())
      {
        if (result.continueStartup())
        {
          int    msgID   = MSGID_PLUGIN_STARTUP_PLUGIN_FAIL_CONTINUE;
          String message = getMessage(msgID,
                                      String.valueOf(p.getPluginEntryDN()),
                                      result.getErrorMessage(),
                                      result.getErrorID());
          logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
        else
        {
          int    msgID   = MSGID_PLUGIN_STARTUP_PLUGIN_FAIL_ABORT;
          String message = getMessage(msgID,
                                      String.valueOf(p.getPluginEntryDN()),
                                      result.getErrorMessage(),
                                      result.getErrorID());
          logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.FATAL_ERROR,
                   message, msgID);
          return result;
        }
      }
    }

    if (result == null)
    {
      // This should only happen if there were no startup plugins registered,
      // which is fine.
      result = StartupPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of shutdown plugins that have been configured in the
   * Directory Server.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  public void invokeShutdownPlugins(String reason)
  {
    for (DirectoryServerPlugin p : shutdownPlugins)
    {
      try
      {
        p.doShutdown(reason);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_SHUTDOWN_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
      }
    }
  }



  /**
   * Invokes the set of post-connect plugins that have been configured in the
   * Directory Server.
   *
   * @param  clientConnection  The client connection that has been established.
   *
   * @return  The result of processing the post-connect plugins.
   */
  public PostConnectPluginResult invokePostConnectPlugins(ClientConnection
                                                               clientConnection)
  {
    PostConnectPluginResult result = null;

    for (DirectoryServerPlugin p : postConnectPlugins)
    {
      try
      {
        result = p.doPostConnect(clientConnection);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_PLUGIN_POST_CONNECT_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    clientConnection.getConnectionID(),
                                    clientConnection.getClientAddress(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        try
        {
          clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                      message, msgID);
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }
        }

        return new PostConnectPluginResult(true, false);
      }


      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_CONNECT_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID,
                                    String.valueOf(p.getPluginEntryDN()),
                                    clientConnection.getConnectionID(),
                                    clientConnection.getClientAddress());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        try
        {
          clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                      message, msgID);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        return new PostConnectPluginResult(true, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-connect plugins
      // registered, which is fine.
      result = PostConnectPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-disconnect plugins that have been configured in the
   * Directory Server.
   *
   * @param  clientConnection  The client connection that has been closed.
   * @param  disconnectReason  The general reason that the connection was
   *                           closed.
   * @param  messageID         The unique ID for the closure message, or a
   *                           negative value if there was no message.
   * @param  message           A human-readable message that may provide
   *                           additional information about the closure.
   *
   * @return  The result of processing the post-connect plugins.
   */
  public PostDisconnectPluginResult invokePostDisconnectPlugins(
                                         ClientConnection clientConnection,
                                         DisconnectReason disconnectReason,
                                         int messageID, String message)
  {
    PostDisconnectPluginResult result = null;

    for (DirectoryServerPlugin p : postDisconnectPlugins)
    {
      try
      {
        result = p.doPostDisconnect(clientConnection, disconnectReason,
                                    messageID, message);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID = MSGID_PLUGIN_POST_DISCONNECT_PLUGIN_EXCEPTION;
        String msg   = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                  clientConnection.getConnectionID(),
                                  clientConnection.getClientAddress(),
                                  stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR, msg,
                 msgID);

        return new PostDisconnectPluginResult(false);
      }


      if (result == null)
      {
        int    msgID = MSGID_PLUGIN_POST_DISCONNECT_PLUGIN_RETURNED_NULL;
        String msg   = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                  clientConnection.getConnectionID(),
                                  clientConnection.getClientAddress());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR, msg,
                 msgID);

        return new PostDisconnectPluginResult(false);
      }
      else if (! result.continuePluginProcessing())
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-disconnect plugins
      // registered, which is fine.
      result = PostDisconnectPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of LDIF import plugins that have been configured in the
   * Directory Server.
   *
   * @param  importConfig  The LDIF import configuration used to read the
   *                       associated entry.
   * @param  entry         The entry that has been read from LDIF.
   *
   * @return  The result of processing the LDIF import plugins.
   */
  public LDIFPluginResult invokeLDIFImportPlugins(LDIFImportConfig importConfig,
                                                  Entry entry)
  {
    LDIFPluginResult result = null;

    for (DirectoryServerPlugin p : ldifImportPlugins)
    {
      try
      {
        result = p.doLDIFImport(importConfig, entry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_LDIF_IMPORT_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    String.valueOf(entry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new LDIFPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_LDIF_IMPORT_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    String.valueOf(entry.getDN()));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new LDIFPluginResult(false, false);
      }
      else if (! result.continuePluginProcessing())
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no LDIF import plugins
      // registered, which is fine.
      result = LDIFPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of LDIF export plugins that have been configured in the
   * Directory Server.
   *
   * @param  exportConfig  The LDIF export configuration used to read the
   *                       associated entry.
   * @param  entry         The entry that has been read from LDIF.
   *
   * @return  The result of processing the LDIF export plugins.
   */
  public LDIFPluginResult invokeLDIFExportPlugins(LDIFExportConfig exportConfig,
                                                  Entry entry)
  {
    LDIFPluginResult result = null;

    for (DirectoryServerPlugin p : ldifExportPlugins)
    {
      try
      {
        result = p.doLDIFExport(exportConfig, entry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_LDIF_EXPORT_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    String.valueOf(entry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new LDIFPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_LDIF_EXPORT_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    String.valueOf(entry.getDN()));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new LDIFPluginResult(false, false);
      }
      else if (! result.continuePluginProcessing())
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no LDIF export plugins
      // registered, which is fine.
      result = LDIFPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse abandon plugins that have been configured in
   * the Directory Server.
   *
   * @param  abandonOperation  The abandon operation for which to invoke the
   *                           pre-parse plugins.
   *
   * @return  The result of processing the pre-parse abandon plugins.
   */
  public PreParsePluginResult invokePreParseAbandonPlugins(
                                   AbandonOperation abandonOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseAbandonPlugins)
    {
      try
      {
        result = p.doPreParse(abandonOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        abandonOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        abandonOperation.getConnectionID(),
                        abandonOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        abandonOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        abandonOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        abandonOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        abandonOperation.getConnectionID(),
                        abandonOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        abandonOperation.setResultCode(
             DirectoryServer.getServerErrorResultCode());
        abandonOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse abandon plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse add plugins that have been configured in the
   * Directory Server.
   *
   * @param  addOperation  The add operation for which to invoke the pre-parse
   *                       plugins.
   *
   * @return  The result of processing the pre-parse add plugins.
   */
  public PreParsePluginResult invokePreParseAddPlugins(
      PreParseAddOperation addOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseAddPlugins)
    {
      try
      {
        result = p.doPreParse(addOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        addOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        addOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        addOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        addOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse add plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse bind plugins that have been configured in
   * the Directory Server.
   *
   * @param  bindOperation  The bind operation for which to invoke the pre-parse
   *                        plugins.
   *
   * @return  The result of processing the pre-parse bind plugins.
   */
  public PreParsePluginResult invokePreParseBindPlugins(
                                   PreParseBindOperation bindOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseBindPlugins)
    {
      try
      {
        result = p.doPreParse(bindOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        bindOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        bindOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse bind plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse compare plugins that have been configured in
   * the Directory Server.
   *
   * @param  compareOperation  The compare operation for which to invoke the
   *                           pre-parse plugins.
   *
   * @return  The result of processing the pre-parse compare plugins.
   */
  public PreParsePluginResult invokePreParseComparePlugins(
    PreParseCompareOperation compareOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseComparePlugins)
    {
      try
      {
        result = p.doPreParse(compareOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        compareOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        compareOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        compareOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        compareOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse compare plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse delete plugins that have been configured in
   * the Directory Server.
   *
   * @param  deleteOperation  The delete operation for which to invoke the
   *                          pre-parse plugins.
   *
   * @return  The result of processing the pre-parse delete plugins.
   */
  public PreParsePluginResult invokePreParseDeletePlugins(
                              PreParseDeleteOperation deleteOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseDeletePlugins)
    {
      try
      {
        result = p.doPreParse(deleteOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        deleteOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        deleteOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        deleteOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        deleteOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse delete plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse extended plugins that have been configured in
   * the Directory Server.
   *
   * @param  extendedOperation  The extended operation for which to invoke the
   *                            pre-parse plugins.
   *
   * @return  The result of processing the pre-parse extended plugins.
   */
  public PreParsePluginResult invokePreParseExtendedPlugins(
                                   ExtendedOperation extendedOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseExtendedPlugins)
    {
      try
      {
        result = p.doPreParse(extendedOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        extendedOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        extendedOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        extendedOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        extendedOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse extended plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse modify plugins that have been configured in
   * the Directory Server.
   *
   * @param  operation  The modify operation for which to invoke the
   *                          pre-parse plugins.
   *
   * @return  The result of processing the pre-parse modify plugins.
   */
  public PreParsePluginResult invokePreParseModifyPlugins(
                                   PreParseModifyOperation operation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseModifyPlugins)
    {
      try
      {
        result = p.doPreParse(operation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        operation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        operation.getConnectionID(),
                        operation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        operation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        operation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        operation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        operation.getConnectionID(),
                        operation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        operation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        operation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse modify plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse modify DN plugins that have been configured in
   * the Directory Server.
   *
   * @param  modifyDNOperation  The modify DN operation for which to invoke the
   *                            pre-parse plugins.
   *
   * @return  The result of processing the pre-parse modify DN plugins.
   */
  public PreParsePluginResult invokePreParseModifyDNPlugins(
                                   ModifyDNOperation modifyDNOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseModifyDNPlugins)
    {
      try
      {
        result = p.doPreParse(modifyDNOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyDNOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        modifyDNOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyDNOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        modifyDNOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse modify DN plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse search plugins that have been configured in
   * the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          pre-parse plugins.
   *
   * @return  The result of processing the pre-parse search plugins.
   */
  public PreParsePluginResult invokePreParseSearchPlugins(
                                   PreParseSearchOperation searchOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseSearchPlugins)
    {
      try
      {
        result = p.doPreParse(searchOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        searchOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        searchOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        searchOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        searchOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse search plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse unbind plugins that have been configured in
   * the Directory Server.
   *
   * @param  unbindOperation  The unbind operation for which to invoke the
   *                          pre-parse plugins.
   *
   * @return  The result of processing the pre-parse unbind plugins.
   */
  public PreParsePluginResult invokePreParseUnbindPlugins(
                                   UnbindOperation unbindOperation)
  {
    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseUnbindPlugins)
    {
      try
      {
        result = p.doPreParse(unbindOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        unbindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        unbindOperation.getConnectionID(),
                        unbindOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        unbindOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        unbindOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        unbindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        unbindOperation.getConnectionID(),
                        unbindOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        unbindOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        unbindOperation.appendErrorMessage(message);

        return new PreParsePluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-parse unbind plugins
      // registered, which is fine.
      result = PreParsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation add plugins that have been configured in
   * the Directory Server.
   *
   * @param  addOperation  The add operation for which to invoke the
   *                       pre-operation plugins.
   *
   * @return  The result of processing the pre-operation add plugins.
   */
  public PreOperationPluginResult invokePreOperationAddPlugins(
                                       PreOperationAddOperation addOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationAddPlugins)
    {
      try
      {
        result = p.doPreOperation(addOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        addOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        addOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        addOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        addOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation add plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation bind plugins that have been configured in
   * the Directory Server.
   *
   * @param  bindOperation  The bind operation for which to invoke the
   *                        pre-operation plugins.
   *
   * @return  The result of processing the pre-operation bind plugins.
   */
  public PreOperationPluginResult invokePreOperationBindPlugins(
                                       PreOperationBindOperation bindOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationBindPlugins)
    {
      try
      {
        result = p.doPreOperation(bindOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        bindOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        bindOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation bind plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation compare plugins that have been configured
   * in the Directory Server.
   *
   * @param  compareOperation  The compare operation for which to invoke the
   *                           pre-operation plugins.
   *
   * @return  The result of processing the pre-operation compare plugins.
   */
  public PreOperationPluginResult invokePreOperationComparePlugins(
     PreOperationCompareOperation compareOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationComparePlugins)
    {
      try
      {
        result = p.doPreOperation(compareOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        compareOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        compareOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        compareOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        compareOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation compare plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation delete plugins that have been configured
   * in the Directory Server.
   *
   * @param  deleteOperation  The delete operation for which to invoke the
   *                          pre-operation plugins.
   *
   * @return  The result of processing the pre-operation delete plugins.
   */
  public PreOperationPluginResult invokePreOperationDeletePlugins(
                                  PreOperationDeleteOperation deleteOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationDeletePlugins)
    {
      try
      {
        result = p.doPreOperation(deleteOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        deleteOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        deleteOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        deleteOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        deleteOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation delete plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation extended plugins that have been configured
   * in the Directory Server.
   *
   * @param  extendedOperation  The extended operation for which to invoke the
   *                            pre-operation plugins.
   *
   * @return  The result of processing the pre-operation extended plugins.
   */
  public PreOperationPluginResult invokePreOperationExtendedPlugins(
                                       ExtendedOperation extendedOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationExtendedPlugins)
    {
      try
      {
        result = p.doPreOperation(extendedOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        extendedOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        extendedOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        extendedOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        extendedOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation extended plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation modify plugins that have been configured
   * in the Directory Server.
   *
   * @param  modifyOperation  The modify operation for which to invoke the
   *                          pre-operation plugins.
   *
   * @return  The result of processing the pre-operation modify plugins.
   */
  public PreOperationPluginResult invokePreOperationModifyPlugins(
                                  PreOperationModifyOperation modifyOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationModifyPlugins)
    {
      try
      {
        result = p.doPreOperation(modifyOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyOperation.getConnectionID(),
                        modifyOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        modifyOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyOperation.getConnectionID(),
                        modifyOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        modifyOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation modify plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation modify DN plugins that have been
   * configured in the Directory Server.
   *
   * @param  modifyDNOperation  The modify DN operation for which to invoke the
   *                            pre-operation plugins.
   *
   * @return  The result of processing the pre-operation modify DN plugins.
   */
  public PreOperationPluginResult invokePreOperationModifyDNPlugins(
                                       ModifyDNOperation modifyDNOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationModifyDNPlugins)
    {
      try
      {
        result = p.doPreOperation(modifyDNOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyDNOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        modifyDNOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyDNOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        modifyDNOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation modify DN
      // plugins registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of pre-operation search plugins that have been configured
   * in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          pre-operation plugins.
   *
   * @return  The result of processing the pre-operation search plugins.
   */
  public PreOperationPluginResult invokePreOperationSearchPlugins(
                                  PreOperationSearchOperation searchOperation)
  {
    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationSearchPlugins)
    {
      try
      {
        result = p.doPreOperation(searchOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        searchOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        searchOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        searchOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        searchOperation.appendErrorMessage(message);

        return new PreOperationPluginResult(false, false, true);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no pre-operation search plugins
      // registered, which is fine.
      result = PreOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation abandon plugins that have been configured
   * in the Directory Server.
   *
   * @param  abandonOperation  The abandon operation for which to invoke the
   *                           post-operation plugins.
   *
   * @return  The result of processing the post-operation abandon plugins.
   */
  public PostOperationPluginResult invokePostOperationAbandonPlugins(
                                        AbandonOperation abandonOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationAbandonPlugins)
    {
      try
      {
        result = p.doPostOperation(abandonOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        abandonOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        abandonOperation.getConnectionID(),
                        abandonOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        abandonOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        abandonOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        abandonOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        abandonOperation.getConnectionID(),
                        abandonOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        abandonOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        abandonOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation abandon plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation add plugins that have been configured in
   * the Directory Server.
   *
   * @param  addOperation  The add operation for which to invoke the
   *                       post-operation plugins.
   *
   * @return  The result of processing the post-operation add plugins.
   */
  public PostOperationPluginResult invokePostOperationAddPlugins(
                                        PostOperationAddOperation addOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationAddPlugins)
    {
      try
      {
        result = p.doPostOperation(addOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        addOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        addOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        addOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        addOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation add plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation bind plugins that have been configured
   * in the Directory Server.
   *
   * @param  bindOperation  The bind operation for which to invoke the
   *                        post-operation plugins.
   *
   * @return  The result of processing the post-operation bind plugins.
   */
  public PostOperationPluginResult invokePostOperationBindPlugins(
                                   PostOperationBindOperation bindOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationBindPlugins)
    {
      try
      {
        result = p.doPostOperation(bindOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        bindOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());
        bindOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation bind plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation compare plugins that have been configured
   * in the Directory Server.
   *
   * @param  compareOperation  The compare operation for which to invoke the
   *                           post-operation plugins.
   *
   * @return  The result of processing the post-operation compare plugins.
   */
  public PostOperationPluginResult invokePostOperationComparePlugins(
      PostOperationCompareOperation compareOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationComparePlugins)
    {
      try
      {
        result = p.doPostOperation(compareOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        compareOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        compareOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        compareOperation.setResultCode(
                              DirectoryServer.getServerErrorResultCode());
        compareOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation compare plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation delete plugins that have been configured
   * in the Directory Server.
   *
   * @param  deleteOperation  The delete operation for which to invoke the
   *                          post-operation plugins.
   *
   * @return  The result of processing the post-operation delete plugins.
   */
  public PostOperationPluginResult invokePostOperationDeletePlugins(
                                   PostOperationDeleteOperation deleteOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationDeletePlugins)
    {
      try
      {
        result = p.doPostOperation(deleteOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        deleteOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        deleteOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        deleteOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        deleteOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation delete plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation extended plugins that have been
   * configured in the Directory Server.
   *
   * @param  extendedOperation  The extended operation for which to invoke the
   *                            post-operation plugins.
   *
   * @return  The result of processing the post-operation extended plugins.
   */
  public PostOperationPluginResult invokePostOperationExtendedPlugins(
                                        ExtendedOperation extendedOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationExtendedPlugins)
    {
      try
      {
        result = p.doPostOperation(extendedOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        extendedOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        extendedOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        extendedOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        extendedOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation extended
      // plugins registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation modify plugins that have been configured
   * in the Directory Server.
   *
   * @param  modifyOperation  The modify operation for which to invoke the
   *                          post-operation plugins.
   *
   * @return  The result of processing the post-operation modify plugins.
   */
  public PostOperationPluginResult invokePostOperationModifyPlugins(
                                   PostOperationModifyOperation modifyOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationModifyPlugins)
    {
      try
      {
        result = p.doPostOperation(modifyOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyOperation.getConnectionID(),
                        modifyOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        modifyOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyOperation.getConnectionID(),
                        modifyOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        modifyOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation modify plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation modify DN plugins that have been
   * configured in the Directory Server.
   *
   * @param  modifyDNOperation  The modify DN operation for which to invoke the
   *                            post-operation plugins.
   *
   * @return  The result of processing the post-operation modify DN plugins.
   */
  public PostOperationPluginResult invokePostOperationModifyDNPlugins(
                                        ModifyDNOperation modifyDNOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationModifyDNPlugins)
    {
      try
      {
        result = p.doPostOperation(modifyDNOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyDNOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        modifyDNOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        modifyDNOperation.setResultCode(
                               DirectoryServer.getServerErrorResultCode());
        modifyDNOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation modify DN
      // plugins registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation search plugins that have been configured
   * in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          post-operation plugins.
   *
   * @return  The result of processing the post-operation search plugins.
   */
  public PostOperationPluginResult invokePostOperationSearchPlugins(
                                   PostOperationSearchOperation searchOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationSearchPlugins)
    {
      try
      {
        result = p.doPostOperation(searchOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        searchOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        searchOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        searchOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        searchOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation search plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-operation unbind plugins that have been configured
   * in the Directory Server.
   *
   * @param  unbindOperation  The unbind operation for which to invoke the
   *                          post-operation plugins.
   *
   * @return  The result of processing the post-operation unbind plugins.
   */
  public PostOperationPluginResult invokePostOperationUnbindPlugins(
                                        UnbindOperation unbindOperation)
  {
    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationUnbindPlugins)
    {
      try
      {
        result = p.doPostOperation(unbindOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        unbindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        unbindOperation.getConnectionID(),
                        unbindOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        unbindOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        unbindOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        unbindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        unbindOperation.getConnectionID(),
                        unbindOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        unbindOperation.setResultCode(
                             DirectoryServer.getServerErrorResultCode());
        unbindOperation.appendErrorMessage(message);

        return new PostOperationPluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-operation unbind plugins
      // registered, which is fine.
      result = PostOperationPluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response add plugins that have been configured in
   * the Directory Server.
   *
   * @param  addOperation  The add operation for which to invoke the
   *                       post-response plugins.
   *
   * @return  The result of processing the post-response add plugins.
   */
  public PostResponsePluginResult invokePostResponseAddPlugins(
                                       PostResponseAddOperation addOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseAddPlugins)
    {
      try
      {
        result = p.doPostResponse(addOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        addOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        addOperation.getConnectionID(),
                        addOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response add plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response bind plugins that have been configured in
   * the Directory Server.
   *
   * @param  bindOperation  The bind operation for which to invoke the
   *                        post-response plugins.
   *
   * @return  The result of processing the post-response bind plugins.
   */
  public PostResponsePluginResult invokePostResponseBindPlugins(
                                       PostResponseBindOperation bindOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseBindPlugins)
    {
      try
      {
        result = p.doPostResponse(bindOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        bindOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        bindOperation.getConnectionID(),
                        bindOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response bind plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response compare plugins that have been configured
   * in the Directory Server.
   *
   * @param  compareOperation  The compare operation for which to invoke the
   *                           post-response plugins.
   *
   * @return  The result of processing the post-response compare plugins.
   */
  public PostResponsePluginResult invokePostResponseComparePlugins(
      PostResponseCompareOperation compareOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseComparePlugins)
    {
      try
      {
        result = p.doPostResponse(compareOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        compareOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        compareOperation.getConnectionID(),
                        compareOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response compare plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response delete plugins that have been configured
   * in the Directory Server.
   *
   * @param  deleteOperation  The delete operation for which to invoke the
   *                          post-response plugins.
   *
   * @return  The result of processing the post-response delete plugins.
   */
  public PostResponsePluginResult invokePostResponseDeletePlugins(
                          PostResponseDeleteOperation deleteOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseDeletePlugins)
    {
      try
      {
        result = p.doPostResponse(deleteOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        deleteOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        deleteOperation.getConnectionID(),
                        deleteOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response delete plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response extended plugins that have been configured
   * in the Directory Server.
   *
   * @param  extendedOperation  The extended operation for which to invoke the
   *                            post-response plugins.
   *
   * @return  The result of processing the post-response extended plugins.
   */
  public PostResponsePluginResult invokePostResponseExtendedPlugins(
                                       ExtendedOperation extendedOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseExtendedPlugins)
    {
      try
      {
        result = p.doPostResponse(extendedOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        extendedOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        extendedOperation.getConnectionID(),
                        extendedOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response extended plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response modify plugins that have been configured
   * in the Directory Server.
   *
   * @param  modifyOperation  The modify operation for which to invoke the
   *                          post-response plugins.
   *
   * @return  The result of processing the post-response modify plugins.
   */
  public PostResponsePluginResult invokePostResponseModifyPlugins(
                                  PostResponseModifyOperation modifyOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseModifyPlugins)
    {
      try
      {
        result = p.doPostResponse(modifyOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyOperation.getConnectionID(),
                        modifyOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyOperation.getConnectionID(),
                        modifyOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response modify plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response modify DN plugins that have been
   * configured in the Directory Server.
   *
   * @param  modifyDNOperation  The modify DN operation for which to invoke the
   *                            post-response plugins.
   *
   * @return  The result of processing the post-response modify DN plugins.
   */
  public PostResponsePluginResult invokePostResponseModifyDNPlugins(
                                       ModifyDNOperation modifyDNOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseModifyDNPlugins)
    {
      try
      {
        result = p.doPostResponse(modifyDNOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        modifyDNOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        modifyDNOperation.getConnectionID(),
                        modifyDNOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response modify DN
      // plugins registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of post-response search plugins that have been configured
   * in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          post-response plugins.
   *
   * @return  The result of processing the post-response search plugins.
   */
  public PostResponsePluginResult invokePostResponseSearchPlugins(
                                  PostResponseSearchOperation searchOperation)
  {
    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseSearchPlugins)
    {
      try
      {
        result = p.doPostResponse(searchOperation);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID(),
                        stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL;
        String message =
             getMessage(msgID,
                        searchOperation.getOperationType().getOperationName(),
                        String.valueOf(p.getPluginEntryDN()),
                        searchOperation.getConnectionID(),
                        searchOperation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new PostResponsePluginResult(false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no post-response search plugins
      // registered, which is fine.
      result = PostResponsePluginResult.SUCCESS;
    }

    return result;
  }

  /**
   * Invokes the set of search result entry plugins that have been configured
   * in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          search result entry plugins.
   * @param  searchEntry      The search result entry to be processed.
   *
   * @return  The result of processing the search result entry plugins.
   */
  public SearchEntryPluginResult invokeSearchResultEntryPlugins(
                                 LocalBackendSearchOperation searchOperation,
                                 SearchResultEntry searchEntry)
  {
    SearchEntryPluginResult result = null;

    for (DirectoryServerPlugin p : searchResultEntryPlugins)
    {
      try
      {
        result = p.processSearchEntry(searchOperation, searchEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    String.valueOf(searchEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchEntryPluginResult(false, false, false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    String.valueOf(searchEntry.getDN()));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchEntryPluginResult(false, false, false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no search result entry plugins
      // registered, which is fine.
      result = SearchEntryPluginResult.SUCCESS;
    }

    return result;
  }

  /**
   * Invokes the set of search result entry plugins that have been configured
   * in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          search result entry plugins.
   * @param  searchEntry      The search result entry to be processed.
   *
   * @return  The result of processing the search result entry plugins.
   */
  public SearchEntryPluginResult invokeSearchResultEntryPlugins(
                                      SearchOperationBasis searchOperation,
                                      SearchResultEntry searchEntry)
  {
    SearchEntryPluginResult result = null;

    for (DirectoryServerPlugin p : searchResultEntryPlugins)
    {
      try
      {
        result = p.processSearchEntry(searchOperation, searchEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    String.valueOf(searchEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchEntryPluginResult(false, false, false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    String.valueOf(searchEntry.getDN()));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchEntryPluginResult(false, false, false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no search result entry plugins
      // registered, which is fine.
      result = SearchEntryPluginResult.SUCCESS;
    }

    return result;
  }

  /**
   * Invokes the set of search result reference plugins that have been
   * configured in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          search result reference plugins.
   * @param  searchReference  The search result reference to be processed.
   *
   * @return  The result of processing the search result reference plugins.
   */
  public SearchReferencePluginResult invokeSearchResultReferencePlugins(
                                   LocalBackendSearchOperation searchOperation,
                                   SearchResultReference searchReference)
  {
    SearchReferencePluginResult result = null;

    for (DirectoryServerPlugin p : searchResultReferencePlugins)
    {
      try
      {
        result = p.processSearchReference(searchOperation, searchReference);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    searchReference.getReferralURLString(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchReferencePluginResult(false, false, false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    searchReference.getReferralURLString());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchReferencePluginResult(false, false, false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no search result reference
      // plugins registered, which is fine.
      result = SearchReferencePluginResult.SUCCESS;
    }

    return result;
  }

  /**
   * Invokes the set of search result reference plugins that have been
   * configured in the Directory Server.
   *
   * @param  searchOperation  The search operation for which to invoke the
   *                          search result reference plugins.
   * @param  searchReference  The search result reference to be processed.
   *
   * @return  The result of processing the search result reference plugins.
   */
  public SearchReferencePluginResult invokeSearchResultReferencePlugins(
                                          SearchOperationBasis searchOperation,
                                          SearchResultReference searchReference)
  {
    SearchReferencePluginResult result = null;

    for (DirectoryServerPlugin p : searchResultReferencePlugins)
    {
      try
      {
        result = p.processSearchReference(searchOperation, searchReference);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    searchReference.getReferralURLString(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchReferencePluginResult(false, false, false, false);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    searchOperation.getConnectionID(),
                                    searchOperation.getOperationID(),
                                    searchReference.getReferralURLString());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new SearchReferencePluginResult(false, false, false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no search result reference
      // plugins registered, which is fine.
      result = SearchReferencePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * Invokes the set of intermediate response plugins that have been configured
   * in the Directory Server.
   *
   * @param  intermediateResponse  The intermediate response for which to invoke
   *                               the intermediate response plugins.
   *
   * @return  The result of processing the intermediate response plugins.
   */
  public IntermediateResponsePluginResult
              invokeIntermediateResponsePlugins(
                   IntermediateResponse intermediateResponse)
  {
    IntermediateResponsePluginResult result = null;
    Operation operation = intermediateResponse.getOperation();

    for (DirectoryServerPlugin p : intermediateResponsePlugins)
    {
      try
      {
        result = p.processIntermediateResponse(intermediateResponse);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_PLUGIN_INTERMEDIATE_RESPONSE_PLUGIN_EXCEPTION;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    operation.getConnectionID(),
                                    operation.getOperationID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new IntermediateResponsePluginResult(false, false, false, false);
      }

      if (result == null)
      {
        int msgID = MSGID_PLUGIN_INTERMEDIATE_RESPONSE_PLUGIN_RETURNED_NULL;
        String message = getMessage(msgID, String.valueOf(p.getPluginEntryDN()),
                                    operation.getConnectionID(),
                                    operation.getOperationID());
        logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        return new IntermediateResponsePluginResult(false, false, false, false);
      }
      else if (result.connectionTerminated() ||
               (! result.continuePluginProcessing()))
      {
        return result;
      }
    }

    if (result == null)
    {
      // This should only happen if there were no intermediate response plugins
      // registered, which is fine.
      result = IntermediateResponsePluginResult.SUCCESS;
    }

    return result;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(PluginCfg configuration,
                                              List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Create a set of plugin types for the plugin.
      HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
      for (PluginCfgDefn.PluginType pluginType :
           configuration.getPluginType())
      {
        pluginTypes.add(getPluginType(pluginType));
      }

      // Get the name of the class and make sure we can instantiate it as a
      // plugin.
      String className = configuration.getPluginClass();
      try
      {
        loadPlugin(className, pluginTypes, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessage());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
                                 PluginCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    // Create a set of plugin types for the plugin.
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    for (PluginCfgDefn.PluginType pluginType :
         configuration.getPluginType())
    {
      pluginTypes.add(getPluginType(pluginType));
    }

    // Get the name of the class and make sure we can instantiate it as a
    // plugin.
    DirectoryServerPlugin<? extends PluginCfg> plugin = null;
    String className = configuration.getPluginClass();
    try
    {
      plugin = loadPlugin(className, pluginTypes, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessage());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      registerPlugin(plugin, configuration.dn(), pluginTypes);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      PluginCfg configuration,
                      List<String> unacceptableReasons)
  {
    // We will always allow plugins to be removed.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 PluginCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    deregisterPlugin(configuration.dn());

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      PluginCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Create a set of plugin types for the plugin.
      HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
      for (PluginCfgDefn.PluginType pluginType :
           configuration.getPluginType())
      {
        pluginTypes.add(getPluginType(pluginType));
      }

      // Get the name of the class and make sure we can instantiate it as a
      // plugin.
      String className = configuration.getPluginClass();
      try
      {
        loadPlugin(className, pluginTypes, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessage());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 PluginCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the existing plugin if it's already enabled.
    DirectoryServerPlugin existingPlugin =
         registeredPlugins.get(configuration.dn());


    // If the new configuration has the plugin disabled, then deregister it if
    // it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingPlugin != null)
      {
        deregisterPlugin(configuration.dn());
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the identity mapper.  If the mapper is already enabled,
    // then we shouldn't do anything with it although if the class has changed
    // then we'll at least need to indicate that administrative action is
    // required.  If the mapper is disabled, then instantiate the class and
    // initialize and register it as an identity mapper.
    String className = configuration.getPluginClass();
    if (existingPlugin != null)
    {
      if (! className.equals(existingPlugin.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    // Create a set of plugin types for the plugin.
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    for (PluginCfgDefn.PluginType pluginType :
         configuration.getPluginType())
    {
      pluginTypes.add(getPluginType(pluginType));
    }

    DirectoryServerPlugin<? extends PluginCfg> plugin = null;
    try
    {
      plugin = loadPlugin(className, pluginTypes, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessage());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      registerPlugin(plugin, configuration.dn(), pluginTypes);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

