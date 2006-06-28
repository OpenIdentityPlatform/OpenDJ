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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
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
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of plugins defined in the Directory Server.  It will perform the
 * necessary initialization of those plugins when the server is first started,
 * and then will manage any changes to them while the server is running.  It
 * also provides methods for invoking all the plugins of a given type.
 */
public class PluginConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.PluginConfigManager";



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
  private ConcurrentHashMap<DN,DirectoryServerPlugin> registeredPlugins;

  // The lock that will provide threadsafe access to the sets of registered
  // plugins.
  private ReentrantLock pluginLock;



  /**
   * Creates a new instance of this plugin config manager.
   */
  public PluginConfigManager()
  {
    assert debugConstructor(CLASS_NAME);

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
    assert debugEnter(CLASS_NAME, "initializePluginConfig");


    registeredPlugins = new ConcurrentHashMap<DN,DirectoryServerPlugin>();


    // Get the configuration entry that is the root of all the plugins in the
    // server.
    ConfigEntry pluginRoot;
    try
    {
      DN pluginRootDN = DN.decode(DN_PLUGIN_BASE);
      pluginRoot = DirectoryServer.getConfigEntry(pluginRootDN);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePluginConfig", e);

      int    msgID   = MSGID_CONFIG_PLUGIN_CANNOT_GET_CONFIG_BASE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // If the configuration root entry is null, then assume it doesn't exist.
    // In that case, then fail.  At least that entry must exist in the
    // configuration, even if there are no plugins defined below it.
    if (pluginRoot == null)
    {
      int    msgID   = MSGID_CONFIG_PLUGIN_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners for the base entry so that we can be
    // notified of added or removed plugins.
    pluginRoot.registerAddListener(this);
    pluginRoot.registerDeleteListener(this);


    // Iterate through the set of immediate children below the plugin config
    // root.
parsePluginEntry:
    for (ConfigEntry entry : pluginRoot.getChildren().values())
    {
      DN entryDN = entry.getDN();


      // Register as a change listener for this backend entry so that we will
      // be notified of any changes that may be made to it.
      entry.registerChangeListener(this);


      // Check to see if this entry appears to contain a plugin configuration.
      // If not, then log a warning and skip it.
      try
      {
        SearchFilter filter =
             SearchFilter.createFilterFromString("(objectClass=" +
                                                 OC_PLUGIN + ")");
        if (! filter.matchesEntry(entry.getEntry()))
        {
          int msgID = MSGID_CONFIG_PLUGIN_ENTRY_DOES_NOT_HAVE_PLUGIN_CONFIG;
          String message = getMessage(msgID, String.valueOf(entryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializePluginConfig", e);

        int msgID = MSGID_CONFIG_PLUGIN_ERROR_INTERACTING_WITH_PLUGIN_ENTRY;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
      }


      // See if the entry contains an attribute that indicates whether the
      // plugin should be enabled.  If it does not, or if it is not set to
      // "true", then skip it.
      int msgID = MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_ENABLED;
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_PLUGIN_ENABLED, getMessage(msgID),
                                      false);
      try
      {
        BooleanConfigAttribute enabledAttr =
             (BooleanConfigAttribute) entry.getConfigAttribute(enabledStub);
        if (enabledAttr == null)
        {
          // The attribute is not present, so this plugin will be disabled.
          // Log a message and continue.
          msgID = MSGID_CONFIG_PLUGIN_NO_ENABLED_ATTR;
          String message = getMessage(msgID, String.valueOf(entryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
        else if (! enabledAttr.activeValue())
        {
          // The plugin is explicitly disabled.  Log a mild warning and
          // continue.
          msgID = MSGID_CONFIG_PLUGIN_DISABLED;
          String message = getMessage(msgID, String.valueOf(entryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.INFORMATIONAL, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializePluginConfig", e);

        msgID = MSGID_CONFIG_PLUGIN_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Get the set of plugin types for this entry.  There must be at least one
      // plugin type specified, and all plugin type names must be valid.
      HashSet<PluginType> types = new HashSet<PluginType>();
      msgID = MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_PLUGIN_TYPE;
      MultiChoiceConfigAttribute typesStub =
           new MultiChoiceConfigAttribute(ATTR_PLUGIN_TYPE, getMessage(msgID),
                                          true, true, true,
                                          PluginType.getPluginTypeNames());
      try
      {
        MultiChoiceConfigAttribute typesAttr =
             (MultiChoiceConfigAttribute) entry.getConfigAttribute(typesStub);
        if ((typesAttr == null) || typesAttr.activeValues().isEmpty())
        {
          msgID = MSGID_CONFIG_PLUGIN_NO_PLUGIN_TYPES;
          String message = getMessage(msgID, String.valueOf(entryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          for (String s : typesAttr.activeValues())
          {
            PluginType t = PluginType.forName(s.toLowerCase());
            if (t == null)
            {
              msgID = MSGID_CONFIG_PLUGIN_INVALID_PLUGIN_TYPE;
              String message = getMessage(msgID, String.valueOf(entryDN), s);
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_ERROR, message, msgID);
              continue parsePluginEntry;
            }
            else
            {
              if ((pluginTypes == null) || pluginTypes.contains(t))
              {
                types.add(t);
              }
            }
          }

          if (types.isEmpty())
          {
            // This means that the plugin doesn't have any of the types that
            // we are interested in so we don't need to initialize it.
            continue;
          }
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializePluginConfig", e);

        msgID = MSGID_CONFIG_PLUGIN_CANNOT_GET_PLUGIN_TYPES;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // FIXME -- We need configuration attributes that can be used to define
      // the order in which plugins are loaded and/or executed.


      // See if the entry contains an attribute that specifies the class name
      // for the plugin implementation.  If it does, then load it and make sure
      // that it's a valid plugin implementation.  If there is no such
      // attribute, the specified class cannot be loaded, or it does not contain
      // a valid plugin implementation, then log an error and skip it.
      String className;
      msgID = MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_CLASS;
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_PLUGIN_CLASS, getMessage(msgID),
                                     true, false, true);
      try
      {
        StringConfigAttribute classAttr =
             (StringConfigAttribute) entry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          msgID = MSGID_CONFIG_PLUGIN_NO_CLASS_ATTR;
          String message = getMessage(msgID, String.valueOf(entryDN));
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
        assert debugException(CLASS_NAME, "initializePluginConfig", e);

        msgID = MSGID_CONFIG_PLUGIN_CANNOT_GET_CLASS;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }

      DirectoryServerPlugin plugin;
      try
      {
        // FIXME --Should we use a custom class loader for this?
        Class pluginClass = Class.forName(className);
        plugin = (DirectoryServerPlugin) pluginClass.newInstance();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializePluginConfig", e);

        msgID = MSGID_CONFIG_PLUGIN_CANNOT_INSTANTIATE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(entryDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Perform the necessary initialization for the plugin entry.
      try
      {
        plugin.initializeInternal(entryDN, types);
        plugin.initializePlugin(DirectoryServer.getInstance(), types,
                                entry);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializePluginConfig", e);

        msgID = MSGID_CONFIG_PLUGIN_CANNOT_INITIALIZE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(entryDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Register the plugin with the server.
      registerPlugin(plugin, entryDN, types);
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
      Iterator<DirectoryServerPlugin> iterator =
           registeredPlugins.values().iterator();
      while (iterator.hasNext())
      {
        try
        {
          iterator.next().finalizePlugin();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "finalizePlugins", e);
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
    assert debugEnter(CLASS_NAME, "configChangeIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


    // Make sure that the entry has an appropriate objectclass for a plugin.
    if (! configEntry.hasObjectClass(OC_PLUGIN))
    {
      int    msgID   = MSGID_CONFIG_PLUGIN_ENTRY_DOES_NOT_HAVE_PLUGIN_CONFIG;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the plugin class name.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_PLUGIN_CLASS,
                    getMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_CLASS),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int    msgID   = MSGID_CONFIG_PLUGIN_NO_CLASS_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      int    msgID   = MSGID_CONFIG_PLUGIN_CANNOT_GET_CLASS;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class pluginClass;
    try
    {
      // FIXME -- Should this be done with a custom class loader?
      pluginClass = Class.forName(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      int msgID = MSGID_CONFIG_PLUGIN_CANNOT_GET_CLASS;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    try
    {
      DirectoryServerPlugin plugin =
           (DirectoryServerPlugin) pluginClass.newInstance();
    }
    catch(Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      int    msgID   = MSGID_CONFIG_PLUGIN_CANNOT_INSTANTIATE;
      String message = getMessage(msgID, pluginClass.getName(),
                                  String.valueOf(configEntry.getDN()),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // See if this plugin should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_PLUGIN_ENABLED,
                    getMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int    msgID   = MSGID_CONFIG_PLUGIN_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      int    msgID   = MSGID_CONFIG_PLUGIN_UNABLE_TO_DETERMINE_ENABLED_STATE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // See the plugin types specified for this plugin and make sure that they
    // are valid.
    MultiChoiceConfigAttribute typesAttr;
    try
    {
      MultiChoiceConfigAttribute typesStub =
           new MultiChoiceConfigAttribute(ATTR_PLUGIN_TYPE,
                getMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_PLUGIN_TYPE),
                true, true, true, PluginType.getPluginTypeNames());
      typesAttr = (MultiChoiceConfigAttribute)
                  configEntry.getConfigAttribute(typesStub);

      if (typesAttr == null)
      {
        int    msgID   = MSGID_CONFIG_PLUGIN_NO_PLUGIN_TYPES;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()));
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      int    msgID   = MSGID_CONFIG_PLUGIN_CANNOT_GET_PLUGIN_TYPES;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the plugin entry appears to be acceptable.
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
    assert debugEnter(CLASS_NAME, "applyConfigurationChange",
                      String.valueOf(configEntry));


    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that the entry has an appropriate objectclass for a plugin.
    if (! configEntry.hasObjectClass(OC_PLUGIN))
    {
      int    msgID   = MSGID_CONFIG_PLUGIN_ENTRY_DOES_NOT_HAVE_PLUGIN_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the corresponding plugin if it is active.
    DirectoryServerPlugin plugin = registeredPlugins.get(configEntryDN);


    // See if this plugin should be enabled or disabled.
    boolean needsEnabled = false;
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_PLUGIN_ENABLED,
                    getMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_PLUGIN_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      if (enabledAttr.activeValue())
      {
        if (plugin == null)
        {
          needsEnabled = true;
        }
        else
        {
          // The plugin is already active, so no action is required.
        }
      }
      else
      {
        if (plugin == null)
        {
          // The plugin is already disabled, so no action is required and we
          // can short-circuit out of this processing.
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // The plugin is active, so it needs to be disabled.  Do this and
          // return that we were successful.
          registeredPlugins.remove(configEntryDN);
          plugin.finalizePlugin();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      int msgID = MSGID_CONFIG_PLUGIN_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the set of plugin types.  If it has
    // changed, then we will not try to dynamically apply it.
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    try
    {
      MultiChoiceConfigAttribute typesStub =
           new MultiChoiceConfigAttribute(ATTR_PLUGIN_TYPE,
                getMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_PLUGIN_TYPE),
                true, true, true, PluginType.getPluginTypeNames());
      MultiChoiceConfigAttribute typesAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(typesStub);
      if (typesAttr == null)
      {
        int msgID = MSGID_CONFIG_PLUGIN_NO_PLUGIN_TYPES;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        for (String s : typesAttr.activeValues())
        {
          PluginType t = PluginType.forName(s.toLowerCase());
          if (t == null)
          {
            int msgID = MSGID_CONFIG_PLUGIN_INVALID_PLUGIN_TYPE;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN), s));
            resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
            return new ConfigChangeResult(resultCode, adminActionRequired,
                                          messages);
          }
          else
          {
            pluginTypes.add(t);
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      int msgID = MSGID_CONFIG_PLUGIN_CANNOT_GET_PLUGIN_TYPES;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the plugin class name.  If it has
    // changed, then we will not try to dynamically apply it.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_PLUGIN_CLASS,
                    getMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_CLASS),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_PLUGIN_NO_CLASS_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      int msgID = MSGID_CONFIG_PLUGIN_CANNOT_GET_CLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    boolean classChanged = false;
    String  oldClassName = null;
    if (plugin != null)
    {
      oldClassName = plugin.getClass().getName();
      classChanged = (! className.equals(oldClassName));
    }


    if (classChanged)
    {
      // This will not be applied dynamically.  Add a message to the response
      // and indicate that admin action is required.
      adminActionRequired = true;
      messages.add(getMessage(MSGID_CONFIG_PLUGIN_CLASS_ACTION_REQUIRED,
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
        Class pluginClass = Class.forName(className);
        plugin = (DirectoryServerPlugin) pluginClass.newInstance();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyConfigurationChange", e);

        int msgID = MSGID_CONFIG_PLUGIN_CANNOT_INSTANTIATE;
        messages.add(getMessage(msgID, className,
                                String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      try
      {
        plugin.initializePlugin(DirectoryServer.getInstance(), pluginTypes,
                                configEntry);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyConfigurationChange", e);

        int msgID = MSGID_CONFIG_PLUGIN_CANNOT_INITIALIZE;
        messages.add(getMessage(msgID, className,
                                String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      registeredPlugins.put(configEntryDN, plugin);
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
    assert debugEnter(CLASS_NAME, "configAddIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


    // NYI
    return false;
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
    assert debugEnter(CLASS_NAME, "applyConfigurationAdd",
                      String.valueOf(configEntry));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // NYI
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
    assert debugEnter(CLASS_NAME, "configDeleteIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


    // NYI
    return false;
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
    assert debugEnter(CLASS_NAME, "applyConfigurationDelete",
                      String.valueOf(configEntry));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // NYI
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
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
  private void registerPlugin(DirectoryServerPlugin plugin, DN pluginEntryDN,
                              HashSet<PluginType> pluginTypes)
  {
    assert debugEnter(CLASS_NAME, "registerPlugin",
                      String.valueOf(plugin), String.valueOf(pluginTypes));

    pluginLock.lock();

    try
    {
      registeredPlugins.put(pluginEntryDN, plugin);

      for (PluginType t : pluginTypes)
      {
        switch (t)
        {
          case STARTUP:
            startupPlugins = addPlugin(startupPlugins, plugin);
            break;
          case SHUTDOWN:
            shutdownPlugins = addPlugin(shutdownPlugins, plugin);
            break;
          case POST_CONNECT:
            postConnectPlugins = addPlugin(postConnectPlugins, plugin);
            break;
          case POST_DISCONNECT:
            postDisconnectPlugins = addPlugin(postDisconnectPlugins, plugin);
            break;
          case LDIF_IMPORT:
            ldifImportPlugins = addPlugin(ldifImportPlugins, plugin);
            break;
          case LDIF_EXPORT:
            ldifExportPlugins = addPlugin(ldifExportPlugins, plugin);
            break;
          case PRE_PARSE_ABANDON:
            preParseAbandonPlugins = addPlugin(preParseAbandonPlugins, plugin);
            break;
          case PRE_PARSE_ADD:
            preParseAddPlugins = addPlugin(preParseAddPlugins, plugin);
            break;
          case PRE_PARSE_BIND:
            preParseBindPlugins = addPlugin(preParseBindPlugins, plugin);
            break;
          case PRE_PARSE_COMPARE:
            preParseComparePlugins = addPlugin(preParseComparePlugins, plugin);
            break;
          case PRE_PARSE_DELETE:
            preParseDeletePlugins = addPlugin(preParseDeletePlugins, plugin);
            break;
          case PRE_PARSE_EXTENDED:
            preParseExtendedPlugins = addPlugin(preParseExtendedPlugins,
                                                plugin);
            break;
          case PRE_PARSE_MODIFY:
            preParseModifyPlugins = addPlugin(preParseModifyPlugins, plugin);
            break;
          case PRE_PARSE_MODIFY_DN:
            preParseModifyDNPlugins = addPlugin(preParseModifyDNPlugins,
                                                plugin);
            break;
          case PRE_PARSE_SEARCH:
            preParseSearchPlugins = addPlugin(preParseSearchPlugins, plugin);
            break;
          case PRE_PARSE_UNBIND:
            preParseUnbindPlugins = addPlugin(preParseUnbindPlugins, plugin);
            break;
          case PRE_OPERATION_ADD:
            preOperationAddPlugins = addPlugin(preOperationAddPlugins, plugin);
            break;
          case PRE_OPERATION_BIND:
            preOperationBindPlugins = addPlugin(preOperationBindPlugins,
                                                plugin);
            break;
          case PRE_OPERATION_COMPARE:
            preOperationComparePlugins = addPlugin(preOperationComparePlugins,
                                                   plugin);
            break;
          case PRE_OPERATION_DELETE:
            preOperationDeletePlugins = addPlugin(preOperationDeletePlugins,
                                                  plugin);
            break;
          case PRE_OPERATION_EXTENDED:
            preOperationExtendedPlugins = addPlugin(preOperationExtendedPlugins,
                                                    plugin);
            break;
          case PRE_OPERATION_MODIFY:
            preOperationModifyPlugins = addPlugin(preOperationModifyPlugins,
                                                  plugin);
            break;
          case PRE_OPERATION_MODIFY_DN:
            preOperationModifyDNPlugins = addPlugin(preOperationModifyDNPlugins,
                                                    plugin);
            break;
          case PRE_OPERATION_SEARCH:
            preOperationSearchPlugins = addPlugin(preOperationSearchPlugins,
                                                  plugin);
            break;
          case POST_OPERATION_ABANDON:
            postOperationAbandonPlugins = addPlugin(postOperationAbandonPlugins,
                                                    plugin);
            break;
          case POST_OPERATION_ADD:
            postOperationAddPlugins = addPlugin(postOperationAddPlugins,
                                                plugin);
            break;
          case POST_OPERATION_BIND:
            postOperationBindPlugins = addPlugin(postOperationBindPlugins,
                                                 plugin);
            break;
          case POST_OPERATION_COMPARE:
            postOperationComparePlugins = addPlugin(postOperationComparePlugins,
                                                    plugin);
            break;
          case POST_OPERATION_DELETE:
            postOperationDeletePlugins = addPlugin(postOperationDeletePlugins,
                                                   plugin);
            break;
          case POST_OPERATION_EXTENDED:
            postOperationExtendedPlugins =
                 addPlugin(postOperationExtendedPlugins, plugin);
            break;
          case POST_OPERATION_MODIFY:
            postOperationModifyPlugins = addPlugin(postOperationModifyPlugins,
                                                   plugin);
            break;
          case POST_OPERATION_MODIFY_DN:
            postOperationModifyDNPlugins =
                 addPlugin(postOperationModifyDNPlugins, plugin);
            break;
          case POST_OPERATION_SEARCH:
            postOperationSearchPlugins = addPlugin(postOperationSearchPlugins,
                                                   plugin);
            break;
          case POST_OPERATION_UNBIND:
            postOperationUnbindPlugins = addPlugin(postOperationUnbindPlugins,
                                                   plugin);
            break;
          case POST_RESPONSE_ADD:
            postResponseAddPlugins = addPlugin(postResponseAddPlugins, plugin);
            break;
          case POST_RESPONSE_BIND:
            postResponseBindPlugins = addPlugin(postResponseBindPlugins,
                                                plugin);
            break;
          case POST_RESPONSE_COMPARE:
            postResponseComparePlugins = addPlugin(postResponseComparePlugins,
                                                   plugin);
            break;
          case POST_RESPONSE_DELETE:
            postResponseDeletePlugins = addPlugin(postResponseDeletePlugins,
                                                  plugin);
            break;
          case POST_RESPONSE_EXTENDED:
            postResponseExtendedPlugins = addPlugin(postResponseExtendedPlugins,
                                                    plugin);
            break;
          case POST_RESPONSE_MODIFY:
            postResponseModifyPlugins = addPlugin(postResponseModifyPlugins,
                                                  plugin);
            break;
          case POST_RESPONSE_MODIFY_DN:
            postResponseModifyDNPlugins = addPlugin(postResponseModifyDNPlugins,
                                                    plugin);
            break;
          case POST_RESPONSE_SEARCH:
            postResponseSearchPlugins = addPlugin(postResponseSearchPlugins,
                                                  plugin);
            break;
          case SEARCH_RESULT_ENTRY:
            searchResultEntryPlugins = addPlugin(searchResultEntryPlugins,
                                                 plugin);
            break;
          case SEARCH_RESULT_REFERENCE:
            searchResultReferencePlugins =
                 addPlugin(searchResultReferencePlugins, plugin);
            break;
          case INTERMEDIATE_RESPONSE:
            intermediateResponsePlugins = addPlugin(intermediateResponsePlugins,
                                                    plugin);
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
   *
   * @param  pluginArray  The array containing the existing set of plugins.
   * @param  plugin       The plugin to be added to the array.
   *
   * @return  The new array containing the new set of plugins.
   */
  private DirectoryServerPlugin[] addPlugin(DirectoryServerPlugin[] pluginArray,
                                            DirectoryServerPlugin plugin)
  {
    assert debugEnter(CLASS_NAME, "addPlugin", String.valueOf(pluginArray),
                      String.valueOf(plugin));

    DirectoryServerPlugin[] newPlugins =
         new DirectoryServerPlugin[pluginArray.length+1];
    System.arraycopy(pluginArray, 0, newPlugins, 0, pluginArray.length);
    newPlugins[pluginArray.length] = plugin;

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
    assert debugEnter(CLASS_NAME, "invokeStartupPlugins");


    StartupPluginResult result = null;

    for (DirectoryServerPlugin p : startupPlugins)
    {
      try
      {
        result = p.doStartup();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokeStartupPlugins", e);

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
      result = new StartupPluginResult();
    }

    return result;
  }



  /**
   * Invokes the set of shutdown plugins that have been configured in the
   * Directory Server.
   */
  public void invokeShutdownPlugins()
  {
    assert debugEnter(CLASS_NAME, "invokeShutdownPlugins");


    for (DirectoryServerPlugin p : shutdownPlugins)
    {
      try
      {
        p.doShutdown();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokeShutdownPlugins", e);

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
    assert debugEnter(CLASS_NAME, "invokePostConnectPlugins",
                      String.valueOf(clientConnection));


    PostConnectPluginResult result = null;

    for (DirectoryServerPlugin p : postConnectPlugins)
    {
      try
      {
        result = p.doPostConnect(clientConnection);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostConnectPlugins", e);

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
          assert debugException(CLASS_NAME, "invokePostConnectPlugins", e2);
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
          assert debugException(CLASS_NAME, "invokePostConnectPlugins", e);
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
      result = new PostConnectPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostConnectPlugins",
                      String.valueOf(clientConnection));


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
        assert debugException(CLASS_NAME, "invokePostDisconnectPlugins", e);

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
      result = new PostDisconnectPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokeLDIFImportPlugins",
                      String.valueOf(importConfig), String.valueOf(entry));

    LDIFPluginResult result = null;

    for (DirectoryServerPlugin p : ldifImportPlugins)
    {
      try
      {
        result = p.doLDIFImport(importConfig, entry);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokeLDIFImportPlugins", e);

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
      result = new LDIFPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokeLDIFExportPlugins",
                      String.valueOf(exportConfig), String.valueOf(entry));

    LDIFPluginResult result = null;

    for (DirectoryServerPlugin p : ldifExportPlugins)
    {
      try
      {
        result = p.doLDIFExport(exportConfig, entry);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokeLDIFExportPlugins", e);

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
      result = new LDIFPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePreParseAbandonPlugins",
                      String.valueOf(abandonOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseAbandonPlugins)
    {
      try
      {
        result = p.doPreParse(abandonOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseAbandonPlugins", e);

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
      result = new PreParsePluginResult();
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
  public PreParsePluginResult invokePreParseAddPlugins(AddOperation
                                                            addOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreParseAddPlugins",
                      String.valueOf(addOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseAddPlugins)
    {
      try
      {
        result = p.doPreParse(addOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseAddPlugins", e);

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
      result = new PreParsePluginResult();
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
                                   BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreParseBindPlugins",
                      String.valueOf(bindOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseBindPlugins)
    {
      try
      {
        result = p.doPreParse(bindOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseBindPlugins", e);

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
      result = new PreParsePluginResult();
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
                                   CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreParseComparePlugins",
                      String.valueOf(compareOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseComparePlugins)
    {
      try
      {
        result = p.doPreParse(compareOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseComparePlugins", e);

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
      result = new PreParsePluginResult();
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
                                   DeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreParseDeletePlugins",
                      String.valueOf(deleteOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseDeletePlugins)
    {
      try
      {
        result = p.doPreParse(deleteOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseDeletePlugins", e);

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
      result = new PreParsePluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePreParseExtendedPlugins",
                      String.valueOf(extendedOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseExtendedPlugins)
    {
      try
      {
        result = p.doPreParse(extendedOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseExtendedPlugins", e);

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
      result = new PreParsePluginResult();
    }

    return result;
  }



  /**
   * Invokes the set of pre-parse modify plugins that have been configured in
   * the Directory Server.
   *
   * @param  modifyOperation  The modify operation for which to invoke the
   *                          pre-parse plugins.
   *
   * @return  The result of processing the pre-parse modify plugins.
   */
  public PreParsePluginResult invokePreParseModifyPlugins(
                                   ModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreParseModifyPlugins",
                      String.valueOf(modifyOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseModifyPlugins)
    {
      try
      {
        result = p.doPreParse(modifyOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseModifyPlugins", e);

        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION;
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

        return new PreParsePluginResult(false, false, true);
      }

      if (result == null)
      {
        int    msgID   = MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL;
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
      result = new PreParsePluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePreParseModifyDNPlugins",
                      String.valueOf(modifyDNOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseModifyDNPlugins)
    {
      try
      {
        result = p.doPreParse(modifyDNOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseModifyDNPlugins", e);

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
      result = new PreParsePluginResult();
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
                                   SearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreParseSearchPlugins",
                      String.valueOf(searchOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseSearchPlugins)
    {
      try
      {
        result = p.doPreParse(searchOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseSearchPlugins", e);

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
      result = new PreParsePluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePreParseUnbindPlugins",
                      String.valueOf(unbindOperation));

    PreParsePluginResult result = null;

    for (DirectoryServerPlugin p : preParseUnbindPlugins)
    {
      try
      {
        result = p.doPreParse(unbindOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreParseUnbindPlugins", e);

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
      result = new PreParsePluginResult();
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
                                       AddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreOperationAddPlugins",
                      String.valueOf(addOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationAddPlugins)
    {
      try
      {
        result = p.doPreOperation(addOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationAddPlugins", e);

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
      result = new PreOperationPluginResult();
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
                                       BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreOperationBindPlugins",
                      String.valueOf(bindOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationBindPlugins)
    {
      try
      {
        result = p.doPreOperation(bindOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationBindPlugins", e);

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
      result = new PreOperationPluginResult();
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
                                       CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreOperationComparePlugins",
                      String.valueOf(compareOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationComparePlugins)
    {
      try
      {
        result = p.doPreOperation(compareOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationComparePlugins",
                              e);

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
      result = new PreOperationPluginResult();
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
                                       DeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreOperationDeletePlugins",
                      String.valueOf(deleteOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationDeletePlugins)
    {
      try
      {
        result = p.doPreOperation(deleteOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationDeletePlugins", e);

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
      result = new PreOperationPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePreOperationExtendedPlugins",
                      String.valueOf(extendedOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationExtendedPlugins)
    {
      try
      {
        result = p.doPreOperation(extendedOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationExtendedPlugins",
                              e);

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
      result = new PreOperationPluginResult();
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
                                       ModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreOperationModifyPlugins",
                      String.valueOf(modifyOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationModifyPlugins)
    {
      try
      {
        result = p.doPreOperation(modifyOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationModifyPlugins", e);

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
      result = new PreOperationPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePreOperationModifyDNPlugins",
                      String.valueOf(modifyDNOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationModifyDNPlugins)
    {
      try
      {
        result = p.doPreOperation(modifyDNOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationModifyDNPlugins",
                              e);

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
      result = new PreOperationPluginResult();
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
                                       SearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePreOperationSearchPlugins",
                      String.valueOf(searchOperation));

    PreOperationPluginResult result = null;

    for (DirectoryServerPlugin p : preOperationSearchPlugins)
    {
      try
      {
        result = p.doPreOperation(searchOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePreOperationSearchPlugins",
                              e);

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
      result = new PreOperationPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostOperationAbandonPlugins",
                      String.valueOf(abandonOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationAbandonPlugins)
    {
      try
      {
        result = p.doPostOperation(abandonOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationAbandonPlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
                                        AddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostOperationAddPlugins",
                      String.valueOf(addOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationAddPlugins)
    {
      try
      {
        result = p.doPostOperation(addOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationAddPlugins", e);

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
      result = new PostOperationPluginResult();
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
                                        BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostOperationBindPlugins",
                      String.valueOf(bindOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationBindPlugins)
    {
      try
      {
        result = p.doPostOperation(bindOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationBindPlugins", e);

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
      result = new PostOperationPluginResult();
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
                                        CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostOperationComparePlugins",
                      String.valueOf(compareOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationComparePlugins)
    {
      try
      {
        result = p.doPostOperation(compareOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationComparePlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
                                        DeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostOperationDeletePlugins",
                      String.valueOf(deleteOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationDeletePlugins)
    {
      try
      {
        result = p.doPostOperation(deleteOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationDeletePlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostOperationExtendedPlugins",
                      String.valueOf(extendedOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationExtendedPlugins)
    {
      try
      {
        result = p.doPostOperation(extendedOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationExtendedPlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
                                        ModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostOperationModifyPlugins",
                      String.valueOf(modifyOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationModifyPlugins)
    {
      try
      {
        result = p.doPostOperation(modifyOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationModifyPlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostOperationModifyDNPlugins",
                      String.valueOf(modifyDNOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationModifyDNPlugins)
    {
      try
      {
        result = p.doPostOperation(modifyDNOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationModifyDNPlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
                                        SearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostOperationSearchPlugins",
                      String.valueOf(searchOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationSearchPlugins)
    {
      try
      {
        result = p.doPostOperation(searchOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationSearchPlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostOperationUnbindPlugins",
                      String.valueOf(unbindOperation));

    PostOperationPluginResult result = null;

    for (DirectoryServerPlugin p : postOperationUnbindPlugins)
    {
      try
      {
        result = p.doPostOperation(unbindOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostOperationUnbindPlugins",
                              e);

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
      result = new PostOperationPluginResult();
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
                                       AddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostResponseAddPlugins",
                      String.valueOf(addOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseAddPlugins)
    {
      try
      {
        result = p.doPostResponse(addOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseAddPlugins", e);

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
      result = new PostResponsePluginResult();
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
                                       BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostResponseBindPlugins",
                      String.valueOf(bindOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseBindPlugins)
    {
      try
      {
        result = p.doPostResponse(bindOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseBindPlugins", e);

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
      result = new PostResponsePluginResult();
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
                                       CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostResponseComparePlugins",
                      String.valueOf(compareOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseComparePlugins)
    {
      try
      {
        result = p.doPostResponse(compareOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseComparePlugins",
                              e);

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
      result = new PostResponsePluginResult();
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
                                       DeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostResponseDeletePlugins",
                      String.valueOf(deleteOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseDeletePlugins)
    {
      try
      {
        result = p.doPostResponse(deleteOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseDeletePlugins", e);

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
      result = new PostResponsePluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostResponseExtendedPlugins",
                      String.valueOf(extendedOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseExtendedPlugins)
    {
      try
      {
        result = p.doPostResponse(extendedOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseExtendedPlugins",
                              e);

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
      result = new PostResponsePluginResult();
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
                                       ModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostResponseModifyPlugins",
                      String.valueOf(modifyOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseModifyPlugins)
    {
      try
      {
        result = p.doPostResponse(modifyOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseModifyPlugins", e);

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
      result = new PostResponsePluginResult();
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
    assert debugEnter(CLASS_NAME, "invokePostResponseModifyDNPlugins",
                      String.valueOf(modifyDNOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseModifyDNPlugins)
    {
      try
      {
        result = p.doPostResponse(modifyDNOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseModifyDNPlugins",
                              e);

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
      result = new PostResponsePluginResult();
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
                                       SearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "invokePostResponseSearchPlugins",
                      String.valueOf(searchOperation));

    PostResponsePluginResult result = null;

    for (DirectoryServerPlugin p : postResponseSearchPlugins)
    {
      try
      {
        result = p.doPostResponse(searchOperation);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokePostResponseSearchPlugins", e);

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
      result = new PostResponsePluginResult();
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
                                      SearchOperation searchOperation,
                                      SearchResultEntry searchEntry)
  {
    assert debugEnter(CLASS_NAME, "invokeSearchResultEntryPlugins",
                      String.valueOf(searchOperation),
                      String.valueOf(searchEntry));

    SearchEntryPluginResult result = null;

    for (DirectoryServerPlugin p : searchResultEntryPlugins)
    {
      try
      {
        result = p.processSearchEntry(searchOperation, searchEntry);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokeSearchResultEntryPlugins", e);

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
      result = new SearchEntryPluginResult();
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
                                          SearchOperation searchOperation,
                                          SearchResultReference searchReference)
  {
    assert debugEnter(CLASS_NAME, "invokeSearchResultReferencePlugins",
                      String.valueOf(searchOperation),
                      String.valueOf(searchReference));

    SearchReferencePluginResult result = null;

    for (DirectoryServerPlugin p : searchResultReferencePlugins)
    {
      try
      {
        result = p.processSearchReference(searchOperation, searchReference);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "invokeSearchResultReferencePlugins",
                              e);

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
      result = new SearchReferencePluginResult();
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
    assert debugEnter(CLASS_NAME, "invokeIntermediateResponsePlugins",
                      String.valueOf(intermediateResponse));

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
        assert debugException(CLASS_NAME, "invokeIntermediateResponsePlugins",
                              e);

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
      result = new IntermediateResponsePluginResult();
    }

    return result;
  }
}

