/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.PasswordStorageSchemeCfgDefn;
import org.forgerock.opendj.server.config.server.PasswordStorageSchemeCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the set of password
 * storage schemes defined in the Directory Server.  It will initialize the
 * storage schemes when the server starts, and then will manage any additions,
 * removals, or modifications to any schemes while the server is running.
 */
public class PasswordStorageSchemeConfigManager
       implements
          ConfigurationChangeListener <PasswordStorageSchemeCfg>,
          ConfigurationAddListener    <PasswordStorageSchemeCfg>,
          ConfigurationDeleteListener <PasswordStorageSchemeCfg>
{
  /** A mapping between the DNs of the config entries and the associated password storage schemes. */
  private final ConcurrentHashMap<DN,PasswordStorageScheme> storageSchemes;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this password storage scheme config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public PasswordStorageSchemeConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    storageSchemes = new ConcurrentHashMap<>();
  }

  /**
   * Initializes all password storage schemes currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the password
   *                           storage scheme initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the password storage scheme that is not
   *                                   related to the server configuration.
   */
  public void initializePasswordStorageSchemes()
         throws ConfigException, InitializationException
  {
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addPasswordStorageSchemeAddListener (this);
    rootConfiguration.addPasswordStorageSchemeDeleteListener (this);

    // Initialize existing password storage schemes.
    for (String schemeName: rootConfiguration.listPasswordStorageSchemes())
    {
      // Get the password storage scheme's configuration.
      PasswordStorageSchemeCfg config =
        rootConfiguration.getPasswordStorageScheme (schemeName);

      // Register as a change listener for this password storage scheme
      // entry so that we will be notified of any changes that may be
      // made to it.
      config.addChangeListener (this);

      // Ignore this password storage scheme if it is disabled.
      if (config.isEnabled())
      {
        // Load the password storage scheme implementation class.
        String className = config.getJavaClass();
        loadAndInstallPasswordStorageScheme (className, config);
      }
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      PasswordStorageSchemeCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // a password storage scheme.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadPasswordStorageScheme (className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        status = false;
      }
    }

    return status;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      PasswordStorageSchemeCfg configuration
      )
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // Get the configuration entry DN and the associated
    // password storage scheme class.
    DN configEntryDN = configuration.dn();
    PasswordStorageScheme storageScheme = storageSchemes.get(configEntryDN);

    // If the new configuration has the password storage scheme disabled,
    // then remove it from the mapping list and clean it.
    if (! configuration.isEnabled())
    {
      if (storageScheme != null)
      {
        uninstallPasswordStorageScheme (configEntryDN);
      }

      return changeResult;
    }

    // At this point, new configuration is enabled...
    // If the current password storage scheme is already enabled then we
    // don't do anything unless the class has changed in which case we
    // should indicate that administrative action is required.
    String newClassName = configuration.getJavaClass();
    if (storageScheme != null)
    {
      String curClassName = storageScheme.getClass().getName();
      boolean classIsNew = !newClassName.equals(curClassName);
      if (classIsNew)
      {
        changeResult.setAdminActionRequired (true);
      }
      return changeResult;
    }

    // New entry cache is enabled and there were no previous one.
    // Instantiate the new class and initialize it.
    try
    {
      loadAndInstallPasswordStorageScheme (newClassName, configuration);
    }
    catch (InitializationException ie)
    {
      changeResult.addMessage (ie.getMessageObject());
      changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
      return changeResult;
    }

    return changeResult;
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      PasswordStorageSchemeCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    // Make sure that no entry already exists with the specified DN.
    DN configEntryDN = configuration.dn();
    if (storageSchemes.containsKey(configEntryDN))
    {
      unacceptableReasons.add (ERR_CONFIG_PWSCHEME_EXISTS.get(configEntryDN));
      status = false;
    }
    // If configuration is enabled then check that password storage scheme
    // class can be instantiated.
    else if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // an entry cache.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadPasswordStorageScheme (className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        status = false;
      }
    }

    return status;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
      PasswordStorageSchemeCfg configuration
      )
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // Register a change listener with it so we can be notified of changes
    // to it over time.
    configuration.addChangeListener(this);

    if (configuration.isEnabled())
    {
      // Instantiate the class as password storage scheme
      // and initialize it.
      String className = configuration.getJavaClass();
      try
      {
        loadAndInstallPasswordStorageScheme (className, configuration);
      }
      catch (InitializationException ie)
      {
        changeResult.addMessage (ie.getMessageObject());
        changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
        return changeResult;
      }
    }

    return changeResult;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
      PasswordStorageSchemeCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
      PasswordStorageSchemeCfg configuration
      )
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();

    uninstallPasswordStorageScheme (configuration.dn());

    return changeResult;
  }

  /**
   * Loads the specified class, instantiates it as a password storage scheme,
   * and optionally initializes that instance. Any initialized password
   * storage scheme is registered in the server.
   *
   * @param  className      The fully-qualified name of the password storage
   *                        scheme class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        password storage scheme, or {@code null} if the
   *                        password storage scheme should not be initialized.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the class.
   */
  private void loadAndInstallPasswordStorageScheme(
       String className,
       PasswordStorageSchemeCfg configuration
       )
       throws InitializationException
  {
    // Load the password storage scheme class...
    PasswordStorageScheme
        <? extends PasswordStorageSchemeCfg> schemeClass;
    schemeClass = loadPasswordStorageScheme (className, configuration, true);

    // ... and install the password storage scheme in the server.
    DN configEntryDN = configuration.dn();
    storageSchemes.put (configEntryDN, schemeClass);
    DirectoryServer.registerPasswordStorageScheme (configEntryDN, schemeClass);
  }

  /**
   * Loads the specified class, instantiates it as a password storage scheme,
   * and optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the class
   *                        to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        class.  It must not be {@code null}.
   * @param  initialize     Indicates whether the password storage scheme
   *                        instance should be initialized.
   *
   * @return  The possibly initialized password storage scheme.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the class.
   */
  private <T extends PasswordStorageSchemeCfg> PasswordStorageScheme<T>
    loadPasswordStorageScheme(
       String className,
       T configuration,
       boolean initialize)
       throws InitializationException
  {
    try
    {
      ClassPropertyDefinition propertyDefinition;
      Class<? extends PasswordStorageScheme> schemeClass;

      PasswordStorageSchemeCfgDefn definition = PasswordStorageSchemeCfgDefn.getInstance();
      propertyDefinition = definition.getJavaClassPropertyDefinition();
      schemeClass = propertyDefinition.loadClass(className, PasswordStorageScheme.class);
      PasswordStorageScheme<T> passwordStorageScheme = schemeClass.newInstance();

      if (initialize)
      {
        passwordStorageScheme.initializePasswordStorageScheme(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!passwordStorageScheme.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_PWSCHEME_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return passwordStorageScheme;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_PWSCHEME_INITIALIZATION_FAILED.get(className,
          configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }

  /**
   * Remove a password storage that has been installed in the server.
   *
   * @param configEntryDN  the DN of the configuration enry associated to
   *                       the password storage scheme to remove
   */
  private void uninstallPasswordStorageScheme(
      DN configEntryDN
      )
  {
    PasswordStorageScheme scheme =
        storageSchemes.remove (configEntryDN);
    if (scheme != null)
    {
      DirectoryServer.deregisterPasswordStorageScheme(configEntryDN);
      scheme.finalizePasswordStorageScheme();
    }
  }
}
