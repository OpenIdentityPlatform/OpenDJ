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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.IdentityMapperCfgDefn;
import org.opends.server.admin.std.server.IdentityMapperCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.IdentityMapper;
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
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of identity
 * mappers defined in the Directory Server.  It will initialize the identity
 * mappers when the server starts, and then will manage any additions, removals,
 * or modifications to any identity mappers while the server is running.
 */
public class IdentityMapperConfigManager
       implements ConfigurationChangeListener<IdentityMapperCfg>,
                  ConfigurationAddListener<IdentityMapperCfg>,
                  ConfigurationDeleteListener<IdentityMapperCfg>

{
  // A mapping between the DNs of the config entries and the associated identity
  // mappers.
  private ConcurrentHashMap<DN,IdentityMapper> identityMappers;



  /**
   * Creates a new instance of this identity mapper config manager.
   */
  public IdentityMapperConfigManager()
  {
    identityMappers = new ConcurrentHashMap<DN,IdentityMapper>();
  }



  /**
   * Initializes all identity mappers currently defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the identity
   *                           mapper initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the identity mappers that is not related
   *                                   to the server configuration.
   */
  public void initializeIdentityMappers()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any identity mapper entries are added or removed.
    rootConfiguration.addIdentityMapperAddListener(this);
    rootConfiguration.addIdentityMapperDeleteListener(this);


    //Initialize the existing identity mappers.
    for (String mapperName : rootConfiguration.listIdentityMappers())
    {
      IdentityMapperCfg mapperConfiguration =
           rootConfiguration.getIdentityMapper(mapperName);
      mapperConfiguration.addChangeListener(this);

      if (mapperConfiguration.isEnabled())
      {
        String className = mapperConfiguration.getMapperClass();
        try
        {
          IdentityMapper mapper = loadMapper(className, mapperConfiguration,
                                             true);
          identityMappers.put(mapperConfiguration.dn(), mapper);
          DirectoryServer.registerIdentityMapper(mapperConfiguration.dn(),
                                                 mapper);
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


    // Now that all of the identity mappers are defined, see if the Directory
    // Server's proxied auth mapper is valid.  If not, then log a warning
    // message.
    DN mapperDN = DirectoryServer.getProxiedAuthorizationIdentityMapperDN();
    if (mapperDN == null)
    {
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_WARNING,
               MSGID_CONFIG_IDMAPPER_NO_PROXY_MAPPER_DN);
    }
    else if (! identityMappers.containsKey(mapperDN))
    {
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_CONFIG_IDMAPPER_INVALID_PROXY_MAPPER_DN,
               String.valueOf(mapperDN));
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      IdentityMapperCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // identity mapper.
      String className = configuration.getMapperClass();
      try
      {
        loadMapper(className, configuration, false);
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
                                 IdentityMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    IdentityMapper identityMapper = null;

    // Get the name of the class and make sure we can instantiate it as an
    // identity mapper.
    String className = configuration.getMapperClass();
    try
    {
      identityMapper = loadMapper(className, configuration, true);
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
      identityMappers.put(configuration.dn(), identityMapper);
      DirectoryServer.registerIdentityMapper(configuration.dn(),
                                             identityMapper);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      IdentityMapperCfg configuration,
                      List<String> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // identity mapper is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 IdentityMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    DirectoryServer.deregisterIdentityMapper(configuration.dn());

    IdentityMapper identityMapper = identityMappers.remove(configuration.dn());
    if (identityMapper != null)
    {
      identityMapper.finalizeIdentityMapper();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      IdentityMapperCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // identity mapper.
      String className = configuration.getMapperClass();
      try
      {
        loadMapper(className, configuration, false);
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
                                 IdentityMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the existing mapper if it's already enabled.
    IdentityMapper existingMapper = identityMappers.get(configuration.dn());


    // If the new configuration has the mapper disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingMapper != null)
      {
        DirectoryServer.deregisterIdentityMapper(configuration.dn());

        IdentityMapper identityMapper =
             identityMappers.remove(configuration.dn());
        if (identityMapper != null)
        {
          identityMapper.finalizeIdentityMapper();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the identity mapper.  If the mapper is already enabled,
    // then we shouldn't do anything with it although if the class has changed
    // then we'll at least need to indicate that administrative action is
    // required.  If the mapper is disabled, then instantiate the class and
    // initialize and register it as an identity mapper.
    String className = configuration.getMapperClass();
    if (existingMapper != null)
    {
      if (! className.equals(existingMapper.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    IdentityMapper identityMapper = null;
    try
    {
      identityMapper = loadMapper(className, configuration, true);
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
      identityMappers.put(configuration.dn(), identityMapper);
      DirectoryServer.registerIdentityMapper(configuration.dn(),
                                             identityMapper);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as an identity mapper, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the identity mapper
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the identity
   *                        mapper.  It must not be {@code null}.
   * @param  initialize     Indicates whether the identity mapper instance
   *                        should be initialized.
   *
   * @return  The possibly initialized identity mapper.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the identity mapper.
   */
  private IdentityMapper loadMapper(String className,
                                    IdentityMapperCfg configuration,
                                    boolean initialize)
          throws InitializationException
  {
    try
    {
      IdentityMapperCfgDefn definition =
           IdentityMapperCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getMapperClassPropertyDefinition();
      Class<? extends IdentityMapper> mapperClass =
           propertyDefinition.loadClass(className, IdentityMapper.class);
      IdentityMapper mapper = mapperClass.newInstance();

      if (initialize)
      {
        Method method =
             mapper.getClass().getMethod("initializeIdentityMapper",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(mapper, configuration);
      }
      else
      {
        Method method = mapper.getClass().getMethod("isConfigurationAcceptable",
                                                    IdentityMapperCfg.class,
                                                    List.class);

        List<String> unacceptableReasons = new ArrayList<String>();
        Boolean acceptable = (Boolean) method.invoke(mapper, configuration,
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

          int    msgID   = MSGID_CONFIG_IDMAPPER_CONFIG_NOT_ACCEPTABLE;
          String message = getMessage(msgID, String.valueOf(configuration.dn()),
                                      buffer.toString());
          throw new InitializationException(msgID, message);
        }
      }

      return mapper;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_IDMAPPER_INITIALIZATION_FAILED;
      String message = getMessage(msgID, className,
                                  String.valueOf(configuration.dn()),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }
}

