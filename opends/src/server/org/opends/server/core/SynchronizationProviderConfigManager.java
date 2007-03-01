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
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.SynchronizationProvider;
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
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of synchronization providers configured in the Directory Server.
 * It will perform the necessary initialization of those synchronization
 * providers when the server is first started, and then will manage any changes
 * to them while the server is running.
 */
public class SynchronizationProviderConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{



  // The mapping between configuration entry DNs and their corresponding
  // synchronization provider implementations.
  private ConcurrentHashMap<DN,SynchronizationProvider> registeredProviders;

  // The DN of the associated configuration entry.
  private DN configEntryDN;



  /**
   * Creates a new instance of this synchronization provider config manager.
   */
  public SynchronizationProviderConfigManager()
  {
    // No implementation is required.
  }



  /**
   * Initializes the configuration associated with the Directory Server
   * synchronization providers.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a critical configuration problem prevents any
   *                           of the synchronization providers from starting
   *                           properly.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the synchronization providers that
   *                                   is not related to the Directory Server
   *                                   configuration.
   */
  public void initializeSynchronizationProviders()
         throws ConfigException, InitializationException
  {
    registeredProviders = new ConcurrentHashMap<DN,SynchronizationProvider>();


    // Get the configuration entry that is the parent for all synchronization
    // providers in the server.
    ConfigEntry providerRoot;
    try
    {
      configEntryDN = DN.decode(DN_SYNCHRONIZATION_PROVIDER_BASE);
      providerRoot  = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SYNCH_CANNOT_GET_CONFIG_BASE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // If the configuration root entry is null, then assume it doesn't exist.
    // In that case, then fail.  At least that entry must exist in the
    // configuration, even if there are no synchronization providers defined
    // below it.
    if (providerRoot == null)
    {
      int    msgID   = MSGID_CONFIG_SYNCH_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register as an add and delete listener for the base entry so that we can
    // be notified if new providers are added or existing providers are removed.
    providerRoot.registerAddListener(this);
    providerRoot.registerDeleteListener(this);


    // Iterate through the set of immediate children below the provider root
    // entry and register those providers.
    for (ConfigEntry providerEntry : providerRoot.getChildren().values())
    {
      DN providerDN = providerEntry.getDN();


      // Register as a change listener for this provider entry so that we will
      // be notified of any changes that may be made to it.
      providerEntry.registerChangeListener(this);


      // Check to see if this entry appears to contain a synchronization
      // provider configuration.  If not, then fail.
      try
      {
        SearchFilter providerFilter =
          SearchFilter.createFilterFromString("(objectClass=" +
                                              OC_SYNCHRONIZATION_PROVIDER +
                                              ")");

        if (! providerFilter.matchesEntry(providerEntry.getEntry()))
        {
          int msgID = MSGID_CONFIG_SYNCH_ENTRY_DOES_NOT_HAVE_PROVIDER_CONFIG;
          String message = getMessage(msgID, String.valueOf(providerDN));
          throw new ConfigException(msgID, message);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_SYNCH_CANNOT_CHECK_FOR_PROVIDER_CONFIG_OC;
        String message = getMessage(msgID, String.valueOf(providerDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }


      // See if the entry contains an attribute that indicates whether the
      // synchronization provider should be enabled.  If it does not, then fail.
      // If it is present but set to false, then log a warning and skip it.
      int msgID = MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_ENABLED;
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_SYNCHRONIZATION_PROVIDER_ENABLED,
                                      getMessage(msgID), true);
      try
      {
        BooleanConfigAttribute enabledAttr =
             (BooleanConfigAttribute)
             providerEntry.getConfigAttribute(enabledStub);
        if (enabledAttr == null)
        {
          msgID = MSGID_CONFIG_SYNCH_PROVIDER_NO_ENABLED_ATTR;
          String message = getMessage(msgID, String.valueOf(providerDN));
          throw new ConfigException(msgID, message);
        }
        else if (! enabledAttr.activeValue())
        {
          msgID = MSGID_CONFIG_SYNCH_PROVIDER_DISABLED;
          String message = getMessage(msgID, String.valueOf(providerDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, String.valueOf(providerDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }


      // See if the entry contains an attribute that specifies the class name
      // for the synchronization provider implementation.  If there  is no such
      // attribute, then fail.
      String providerClassName;
      msgID = MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_CLASS;
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_SYNCHRONIZATION_PROVIDER_CLASS,
                                     getMessage(msgID), true, false, true);
      try
      {
        StringConfigAttribute classAttr =
             (StringConfigAttribute)
             providerEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          msgID = MSGID_CONFIG_SYNCH_NO_CLASS_ATTR;
          String message = getMessage(msgID, String.valueOf(providerDN));
          throw new ConfigException(msgID, message);
        }
        else
        {
          providerClassName = classAttr.activeValue();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_CLASS;
        String message = getMessage(msgID, String.valueOf(providerDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }


      // Load the specified provider class.  If an error occurs, then fail.
      Class providerClass;
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        providerClass = Class.forName(providerClassName);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS;
        String message = getMessage(msgID, String.valueOf(providerClassName),
                                    String.valueOf(providerDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }


      // Make sure that the specified class is a valid synchronization provider.
      // If not, then fail.
      SynchronizationProvider provider;
      try
      {
        provider = (SynchronizationProvider) providerClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER;
        String message = getMessage(msgID, String.valueOf(providerClassName),
                                    String.valueOf(providerDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }


      // Perform the necessary initialization for the synchronization provider.
      // If a problem occurs, then fail.
      try
      {
        provider.initializeSynchronizationProvider(providerEntry);
      }
      catch (ConfigException ce)
      {
        msgID = MSGID_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER;
        String message = getMessage(msgID, String.valueOf(providerDN),
                                    ce.getMessage());
        throw new ConfigException(msgID, message, ce);
      }
      catch (InitializationException ie)
      {
        msgID = MSGID_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER;
        String message = getMessage(msgID, String.valueOf(providerDN),
                                    ie.getMessage());
        throw new InitializationException(msgID, message, ie);
      }
      catch (Exception e)
      {
        msgID = MSGID_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER;
        String message = getMessage(msgID, String.valueOf(providerDN),
                                    stackTraceToSingleLineString(e));
        throw new ConfigException(msgID, message, e);
      }


      // Register the synchronization provider with the Directory Server.
      DirectoryServer.registerSynchronizationProvider(provider);


      // Put this provider in the hash so that we will be able to find it if it
      // is altered.
      registeredProviders.put(providerDN, provider);
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
    DN providerDN = configEntry.getDN();
    SynchronizationProvider provider = registeredProviders.get(providerDN);


    // Check to see if this entry appears to contain a backend configuration.
    // If not, then reject it.
    try
    {
      SearchFilter providerFilter =
           SearchFilter.createFilterFromString("(objectClass=" +
                                               OC_SYNCHRONIZATION_PROVIDER +
                                               ")");
      if (! providerFilter.matchesEntry(configEntry.getEntry()))
      {
        int msgID = MSGID_CONFIG_SYNCH_ENTRY_DOES_NOT_HAVE_PROVIDER_CONFIG;
        unacceptableReason.append(getMessage(msgID,
                                             String.valueOf(providerDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SYNCH_CANNOT_CHECK_FOR_PROVIDER_CONFIG_OC;
      unacceptableReason.append(getMessage(msgID, String.valueOf(providerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that indicates whether the
    // provider should be enabled.  If it does not, then reject it.
    int msgID = MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_SYNCHRONIZATION_PROVIDER_ENABLED,
                                    getMessage(msgID), true);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute) configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        msgID = MSGID_CONFIG_SYNCH_PROVIDER_NO_ENABLED_ATTR;
        unacceptableReason.append(getMessage(msgID,
                                             String.valueOf(providerDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_ENABLED_STATE;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(providerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the provider class.
    // If it does not, then fail.
    String className;
    msgID = MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_SYNCHRONIZATION_PROVIDER_CLASS,
                                   getMessage(msgID), true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_SYNCH_NO_CLASS_ATTR;
        unacceptableReason.append(getMessage(msgID,
                                             String.valueOf(providerDN)));
        return false;
      }
      else
      {
        className = classAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_CLASS;
      unacceptableReason.append(getMessage(msgID,
                                           String.valueOf(providerDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // If the provider is currently disabled, or if the class is different from
    // the one used by the running provider, then make sure that it is
    // acceptable.
    if ((provider == null) ||
        (! className.equals(provider.getClass().getName())))
    {
      Class providerClass;
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        providerClass = Class.forName(className);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS;
        unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                             String.valueOf(providerDN),
                                             stackTraceToSingleLineString(e)));
        return false;
      }

      try
      {
        SynchronizationProvider newProvider =
             (SynchronizationProvider) providerClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER;
        unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                             String.valueOf(providerDN),
                                             stackTraceToSingleLineString(e)));
        return false;
      }
    }


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // synchronization provider, then the provider itself will need to make that
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
    DN providerDN = configEntry.getDN();
    SynchronizationProvider provider = registeredProviders.get(providerDN);
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();


    // Check to see if this entry appears to contain a synchronization provider
    // configuration.  If not, then fail.
    try
    {
      SearchFilter providerFilter =
           SearchFilter.createFilterFromString("(objectClass=" +
                                               OC_SYNCHRONIZATION_PROVIDER +
                                               ")");
      if (! providerFilter.matchesEntry(configEntry.getEntry()))
      {
        int msgID = MSGID_CONFIG_SYNCH_ENTRY_DOES_NOT_HAVE_PROVIDER_CONFIG;
        messages.add(getMessage(msgID, String.valueOf(providerDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_SYNCH_CANNOT_CHECK_FOR_PROVIDER_CONFIG_OC;
      messages.add(getMessage(msgID, String.valueOf(providerDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // See if the entry contains an attribute that indicates whether the
    // provider should be enabled.  If it does not, then reject it.
    boolean shouldEnable = false;
    int msgID = MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_SYNCHRONIZATION_PROVIDER_ENABLED,
                                    getMessage(msgID), true);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute) configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        msgID = MSGID_CONFIG_SYNCH_PROVIDER_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(providerDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }
      }
      else
      {
        shouldEnable = enabledAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(providerDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // See if the entry contains an attribute that specifies the provider class.
    // If it does not, then reject it.
    String className = null;
    msgID = MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_SYNCHRONIZATION_PROVIDER_CLASS,
                                   getMessage(msgID), true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_SYNCH_NO_CLASS_ATTR;
        messages.add(getMessage(msgID, String.valueOf(providerDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }
      }
      else
      {
        className = classAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_CLASS;
      messages.add(getMessage(msgID, String.valueOf(providerDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // If the provider is currently disabled, or if the class is different from
    // the one used by the running provider, then make sure that it is
    // acceptable.
    SynchronizationProvider newProvider = null;
    if ((resultCode == ResultCode.SUCCESS) &&
        ((provider == null) ||
         (! provider.getClass().getName().equals(className))))
    {
      Class providerClass = null;
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        providerClass = Class.forName(className);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(providerDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }

      try
      {
        if (providerClass != null)
        {
          newProvider = (SynchronizationProvider) providerClass.newInstance();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(providerDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    }


    // If everything looks OK, then process the configuration change.
    if (resultCode == ResultCode.SUCCESS)
    {
      // If the provider is currently disabled but should be enabled, then do
      // so now.
      if (provider == null)
      {
        if (shouldEnable && (newProvider != null))
        {
          try
          {
            newProvider.initializeSynchronizationProvider(configEntry);
            registeredProviders.put(configEntryDN, newProvider);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            msgID = MSGID_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e)));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = DirectoryServer.getServerErrorResultCode();
            }
          }
        }
      }


      // Otherwise, see if the enabled flag or class name changed and indicate
      // that it will require a restart to take effect.
      else
      {
        if (! shouldEnable)
        {
          msgID = MSGID_CONFIG_SYNCH_PROVIDER_HAS_BEEN_DISABLED;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
          adminActionRequired = true;
        }

        if (! provider.getClass().getName().equals(className))
        {
          msgID = MSGID_CONFIG_SYNCH_PROVIDER_CLASS_CHANGED;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(provider.getClass().getName()),
                                  String.valueOf(className)));
          adminActionRequired = true;
        }
      }
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
    // NYI
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
    // NYI
    return null;
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
    // NYI
    return null;
  }
}

