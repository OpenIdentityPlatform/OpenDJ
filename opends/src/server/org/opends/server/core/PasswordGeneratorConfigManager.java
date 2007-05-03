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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.PasswordGeneratorCfgDefn;
import org.opends.server.admin.std.server.PasswordGeneratorCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;



/**
 * This class defines a utility that will be used to manage the set of password
 * generators defined in the Directory Server.  It will initialize the
 * generators when the server starts, and then will manage any additions,
 * removals, or modifications to any password generators while the server is
 * running.
 */
public class PasswordGeneratorConfigManager
       implements ConfigurationAddListener<PasswordGeneratorCfg>,
       ConfigurationDeleteListener<PasswordGeneratorCfg>,
       ConfigurationChangeListener<PasswordGeneratorCfg>
{

  // A mapping between the DNs of the config entries and the associated password
  // generators.
  private ConcurrentHashMap<DN,PasswordGenerator> passwordGenerators;


  /**
   * Creates a new instance of this password generator config manager.
   */
  public PasswordGeneratorConfigManager()
  {
    passwordGenerators = new ConcurrentHashMap<DN,PasswordGenerator>();
  }



  /**
   * Initializes all password generators currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the password
   *                           generator initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the password generators that is not
   *                                   related to the server configuration.
   */
  public void initializePasswordGenerators()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root configuration so we
    // can be notified if any password generator entries are added or removed.
    rootConfiguration.addPasswordGeneratorAddListener(this);
    rootConfiguration.addPasswordGeneratorDeleteListener(this);


    //Initialize the existing password generators.
    for (String generatorName : rootConfiguration.listPasswordGenerators())
    {
      PasswordGeneratorCfg generatorConfiguration =
           rootConfiguration.getPasswordGenerator(generatorName);
      generatorConfiguration.addChangeListener(this);

      if (generatorConfiguration.isEnabled())
      {
        String className = generatorConfiguration.getGeneratorClass();
        try
        {
          PasswordGenerator<? extends PasswordGeneratorCfg>
               generator = loadGenerator(className, generatorConfiguration);
          passwordGenerators.put(generatorConfiguration.dn(), generator);
          DirectoryServer.registerPasswordGenerator(generatorConfiguration.dn(),
              generator);
        }
        catch (InitializationException ie)
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   ie.getMessage(), ie.getMessageID());
          continue;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      PasswordGeneratorCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // password generator.
      String className = configuration.getGeneratorClass();
      try
      {
        loadGenerator(className, null);
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
                                 PasswordGeneratorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the existing generator if it's already enabled.
    PasswordGenerator existingGenerator =
         passwordGenerators.get(configuration.dn());


    // If the new configuration has the generator disabled, then disable it if
    // it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingGenerator != null)
      {
        DirectoryServer.deregisterPasswordGenerator(configuration.dn());

        PasswordGenerator passwordGenerator =
             passwordGenerators.remove(configuration.dn());
        if (passwordGenerator != null)
        {
          passwordGenerator.finalizePasswordGenerator();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the password generator.  If the generator is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the generator is disabled, then instantiate the class
    // and initialize and register it as a password generator.
    String className = configuration.getGeneratorClass();
    if (existingGenerator != null)
    {
      if (! className.equals(existingGenerator.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    PasswordGenerator<? extends PasswordGeneratorCfg>
         passwordGenerator = null;
    try
    {
      passwordGenerator = loadGenerator(className, configuration);
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
      passwordGenerators.put(configuration.dn(), passwordGenerator);
      DirectoryServer.registerPasswordGenerator(configuration.dn(),
                                                passwordGenerator);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      PasswordGeneratorCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // password generator.
      String className = configuration.getGeneratorClass();
      try
      {
        loadGenerator(className, null);
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
                                 PasswordGeneratorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    PasswordGenerator<? extends PasswordGeneratorCfg>
         passwordGenerator = null;

    // Get the name of the class and make sure we can instantiate it as a
    // password generator.
    String className = configuration.getGeneratorClass();
    try
    {
      passwordGenerator = loadGenerator(className, configuration);
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
      passwordGenerators.put(configuration.dn(), passwordGenerator);
      DirectoryServer.registerPasswordGenerator(configuration.dn(),
                                                passwordGenerator);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      PasswordGeneratorCfg configuration, List<String> unacceptableReasons)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      PasswordGeneratorCfg configuration)
  {
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean    adminActionRequired = false;


    // See if the entry is registered as a password generator.  If so,
    // deregister it and stop the generator.
    PasswordGenerator generator = passwordGenerators.remove(configuration.dn());
    if (generator != null)
    {
      DirectoryServer.deregisterPasswordGenerator(configuration.dn());

      generator.finalizePasswordGenerator();
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  /**
   * Loads the specified class, instantiates it as a password generator, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the password generator
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        password generator, or {@code null} if the
   *                        password generator should not be initialized.
   *
   * @return  The possibly initialized password generator.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the password generator.
   */
  private PasswordGenerator<? extends PasswordGeneratorCfg>
               loadGenerator(String className,
                             PasswordGeneratorCfg configuration)
          throws InitializationException
  {
    try
    {
      PasswordGeneratorCfgDefn definition =
           PasswordGeneratorCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getGeneratorClassPropertyDefinition();
      Class<? extends PasswordGenerator> generatorClass =
           propertyDefinition.loadClass(className, PasswordGenerator.class);
      PasswordGenerator<? extends PasswordGeneratorCfg> generator =
           (PasswordGenerator<? extends PasswordGeneratorCfg>)
           generatorClass.newInstance();

      if (configuration != null)
      {
        Method method =
          generator.getClass().getMethod("initializePasswordGenerator",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(generator, configuration);
      }

      return generator;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_PWGENERATOR_INITIALIZATION_FAILED;
      String message = getMessage(msgID, className,
                                  String.valueOf(configuration.dn()),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }
}

