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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.PasswordStorageSchemeCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



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
  // A mapping between the DNs of the config entries and the associated password
  // storage schemes.
  private ConcurrentHashMap<DN,PasswordStorageScheme> storageSchemes;


  /**
   * Creates a new instance of this password storage scheme config manager.
   */
  public PasswordStorageSchemeConfigManager()
  {
    storageSchemes = new ConcurrentHashMap<DN,PasswordStorageScheme>();
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
    // Get the root configuration object.
    ServerManagementContext managementContext =
      ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
      managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root configuration so we
    // can be notified if any entry cache entry is added or removed.
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



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      PasswordStorageSchemeCfg configuration,
      List<Message> unacceptableReasons
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



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      PasswordStorageSchemeCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

    // Get the configuration entry DN and the associated
    // password storage scheme class.
    DN configEntryDN = configuration.dn();
    PasswordStorageScheme storageScheme = storageSchemes.get(
        configEntryDN
        );

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
      boolean classIsNew = (! newClassName.equals (curClassName));
      if (classIsNew)
      {
        changeResult.setAdminActionRequired (true);
      }
      return changeResult;
    }

    // New entry cache is enabled and there were no previous one.
    // Instantiate the new class and initalize it.
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



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      PasswordStorageSchemeCfg configuration,
      List<Message> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    // Make sure that no entry already exists with the specified DN.
    DN configEntryDN = configuration.dn();
    if (storageSchemes.containsKey(configEntryDN))
    {
      Message message = ERR_CONFIG_PWSCHEME_EXISTS.get(
              String.valueOf(configEntryDN));
      unacceptableReasons.add (message);
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



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      PasswordStorageSchemeCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

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



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      PasswordStorageSchemeCfg configuration,
      List<Message> unacceptableReasons
      )
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      PasswordStorageSchemeCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

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
  private PasswordStorageScheme <? extends PasswordStorageSchemeCfg>
    loadPasswordStorageScheme(
       String className,
       PasswordStorageSchemeCfg configuration,
       boolean initialize)
       throws InitializationException
  {
    try
    {
      PasswordStorageSchemeCfgDefn definition;
      ClassPropertyDefinition propertyDefinition;
      Class<? extends PasswordStorageScheme> schemeClass;
      PasswordStorageScheme<? extends PasswordStorageSchemeCfg>
          passwordStorageScheme;

      definition = PasswordStorageSchemeCfgDefn.getInstance();
      propertyDefinition = definition.getJavaClassPropertyDefinition();
      schemeClass = propertyDefinition.loadClass(
          className,
          PasswordStorageScheme.class
          );
      passwordStorageScheme =
        (PasswordStorageScheme<? extends PasswordStorageSchemeCfg>)
            schemeClass.newInstance();

      if (initialize)
      {
        Method method = passwordStorageScheme.getClass().getMethod(
            "initializePasswordStorageScheme",
            configuration.configurationClass());
        method.invoke(passwordStorageScheme, configuration);
      }
      else
      {
        Method method = passwordStorageScheme.getClass().getMethod(
                             "isConfigurationAcceptable",
                             PasswordStorageSchemeCfg.class, List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(passwordStorageScheme,
                                                     configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<Message> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message = ERR_CONFIG_PWSCHEME_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return passwordStorageScheme;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_PWSCHEME_INITIALIZATION_FAILED.get(className,
          String.valueOf(configuration.dn()),
          stackTraceToSingleLineString(e)
          );
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

