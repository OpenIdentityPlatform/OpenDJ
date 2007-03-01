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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.EntryCache;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.extensions.DefaultEntryCache;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the Directory Server entry cache.  Only a single entry cache may be
 * defined, but if it is absent or disabled, then a default cache will be used.
 */
public class EntryCacheConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{



  /**
   * Creates a new instance of this entry cache config manager.
   */
  public EntryCacheConfigManager()
  {

    // No implementation is required.
  }



  /**
   * Initializes the configuration associated with the Directory Server entry
   * cache.  This should only be called at Directory Server startup.  If an
   * error occurs, then a message will be logged and the default entry cache
   * will be installed.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   install the default entry cache.
   */
  public void initializeEntryCache()
         throws InitializationException
  {


    // First, install a default entry cache so that there will be one even if
    // we encounter a problem later.
    try
    {
      DefaultEntryCache defaultCache = new DefaultEntryCache();
      defaultCache.initializeEntryCache(null);
      DirectoryServer.setEntryCache(defaultCache);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTALL_DEFAULT_CACHE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the entry cache configuration entry.  If it is not present, then
    // register an add listener and install the default cache.
    DN configEntryDN;
    ConfigEntry configEntry;
    try
    {
      configEntryDN = DN.decode(DN_ENTRY_CACHE_CONFIG);
      configEntry   = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_ENTRYCACHE_CANNOT_GET_CONFIG_ENTRY,
               stackTraceToSingleLineString(e));
      return;
    }

    if (configEntry == null)
    {
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_ENTRYCACHE_NO_CONFIG_ENTRY);

      try
      {
        ConfigEntry parentEntry = DirectoryServer
            .getConfigEntry(configEntryDN.getParentDNInSuffix());
        if (parentEntry != null)
        {
          parentEntry.registerAddListener(this);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_ENTRYCACHE_CANNOT_REGISTER_ADD_LISTENER,
                 stackTraceToSingleLineString(e));
      }

      return;
    }


    // At this point, we have a configuration entry.  Register a change listener
    // with it so we can be notified of changes to it over time.  We will also
    // want to register a delete listener with its parent to allow us to
    // determine if the entry is deleted.
    configEntry.registerChangeListener(this);
    try
    {
      DN parentDN = configEntryDN.getParentDNInSuffix();
      ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
      if (parentEntry != null)
      {
        parentEntry.registerDeleteListener(this);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_ENTRYCACHE_CANNOT_REGISTER_DELETE_LISTENER,
               stackTraceToSingleLineString(e));
    }


    // See if the entry indicates whether the cache should be enabled.
    int msgID = MSGID_CONFIG_ENTRYCACHE_DESCRIPTION_CACHE_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_ENTRYCACHE_ENABLED, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present, so the entry cache will be disabled.
        // Log a warning message and return.
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING,
                 MSGID_CONFIG_ENTRYCACHE_NO_ENABLED_ATTR);
        return;
      }
      else if (! enabledAttr.activeValue())
      {
        // The entry cache is explicitly disabled.  Log a mild warning and
        // return.
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_WARNING,
                 MSGID_CONFIG_ENTRYCACHE_DISABLED);
        return;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_ENTRYCACHE_UNABLE_TO_DETERMINE_ENABLED_STATE,
               stackTraceToSingleLineString(e));
      return;
    }


    // See if it specifies the class name for the entry cache implementation.
    String className;
    msgID = MSGID_CONFIG_ENTRYCACHE_DESCRIPTION_CACHE_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_ENTRYCACHE_CLASS, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_ENTRYCACHE_NO_CLASS_ATTR);
        return;
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
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_ENTRYCACHE_CANNOT_DETERMINE_CLASS,
               stackTraceToSingleLineString(e));
      return;
    }


    // Try to load the class and instantiate it as an entry cache.
    Class cacheClass;
    try
    {
      // FIXME -- Should we use a custom class loader for this?
      cacheClass = Class.forName(className);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_ENTRYCACHE_CANNOT_LOAD_CLASS,
               String.valueOf(className), stackTraceToSingleLineString(e));
      return;
    }

    EntryCache entryCache;
    try
    {
      entryCache = (EntryCache) cacheClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTANTIATE_CLASS,
               String.valueOf(className), stackTraceToSingleLineString(e));
      return;
    }


    // Try to initialize the cache with the contents of the configuration entry.
    try
    {
      entryCache.initializeEntryCache(configEntry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_ENTRYCACHE_CANNOT_INITIALIZE_CACHE,
               String.valueOf(className), stackTraceToSingleLineString(e));
      return;
    }


    // Install the new cache with the server.  We don't need to do anything to
    // get rid of the previous default cache since it doesn't consume any
    // resources.
    DirectoryServer.setEntryCache(entryCache);
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


    // NYI


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration, then
    // the entry cache itself will make that determination.
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

    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // NYI


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


    // NYI


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration, then
    // the entry cache itself will make that determination.
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


    // NYI


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration, then
    // the entry cache itself will make that determination.
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

    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // NYI


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

