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
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.IdentityMapperCfgDefn;
import org.forgerock.opendj.server.config.server.IdentityMapperCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.IdentityMapper;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated identity mappers. */
  private final ConcurrentHashMap<DN,IdentityMapper> identityMappers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this identity mapper config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public IdentityMapperConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    identityMappers = new ConcurrentHashMap<>();
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
    RootCfg rootConfiguration = serverContext.getRootConfig();
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
        String className = mapperConfiguration.getJavaClass();
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
          logger.error(ie.getMessageObject());
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
      logger.error(ERR_CONFIG_IDMAPPER_NO_PROXY_MAPPER_DN);
    }
    else if (! identityMappers.containsKey(mapperDN))
    {
      logger.error(ERR_CONFIG_IDMAPPER_INVALID_PROXY_MAPPER_DN, mapperDN);
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(
                      IdentityMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // identity mapper.
      String className = configuration.getJavaClass();
      try
      {
        loadMapper(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
                                 IdentityMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    IdentityMapper identityMapper = null;

    // Get the name of the class and make sure we can instantiate it as an
    // identity mapper.
    String className = configuration.getJavaClass();
    try
    {
      identityMapper = loadMapper(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      identityMappers.put(configuration.dn(), identityMapper);
      DirectoryServer.registerIdentityMapper(configuration.dn(), identityMapper);
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
                      IdentityMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // identity mapper is in use.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 IdentityMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    DirectoryServer.deregisterIdentityMapper(configuration.dn());

    IdentityMapper identityMapper = identityMappers.remove(configuration.dn());
    if (identityMapper != null)
    {
      identityMapper.finalizeIdentityMapper();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      IdentityMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // identity mapper.
      String className = configuration.getJavaClass();
      try
      {
        loadMapper(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 IdentityMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

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

      return ccr;
    }

    // Get the class for the identity mapper.  If the mapper is already enabled,
    // then we shouldn't do anything with it although if the class has changed
    // then we'll at least need to indicate that administrative action is
    // required.  If the mapper is disabled, then instantiate the class and
    // initialize and register it as an identity mapper.
    String className = configuration.getJavaClass();
    if (existingMapper != null)
    {
      if (! className.equals(existingMapper.getClass().getName()))
      {
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    IdentityMapper identityMapper = null;
    try
    {
      identityMapper = loadMapper(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      identityMappers.put(configuration.dn(), identityMapper);
      DirectoryServer.registerIdentityMapper(configuration.dn(), identityMapper);
    }

    return ccr;
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
           definition.getJavaClassPropertyDefinition();
      Class<? extends IdentityMapper> mapperClass =
           propertyDefinition.loadClass(className, IdentityMapper.class);
      IdentityMapper mapper = mapperClass.newInstance();

      if (initialize)
      {
        mapper.initializeIdentityMapper(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!mapper.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_IDMAPPER_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return mapper;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_IDMAPPER_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}
