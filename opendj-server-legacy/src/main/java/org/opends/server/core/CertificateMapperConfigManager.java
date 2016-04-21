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
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.CertificateMapperCfgDefn;
import org.forgerock.opendj.server.config.server.CertificateMapperCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.CertificateMapper;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the set of
 * certificate mappers defined in the Directory Server.  It will initialize the
 * certificate mappers when the server starts, and then will manage any
 * additions, removals, or modifications to any certificate mappers while the
 * server is running.
 */
public class CertificateMapperConfigManager
       implements ConfigurationChangeListener<CertificateMapperCfg>,
                  ConfigurationAddListener<CertificateMapperCfg>,
                  ConfigurationDeleteListener<CertificateMapperCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated certificate mappers. */
  private ConcurrentHashMap<DN, CertificateMapper<?>> certificateMappers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this certificate mapper config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public CertificateMapperConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    certificateMappers = new ConcurrentHashMap<>();
  }

  /**
   * Initializes all certificate mappers currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the certificate
   *                           mapper initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the certificate mappers that is not
   *                                   related to the server configuration.
   */
  public void initializeCertificateMappers()
         throws ConfigException, InitializationException
  {
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addCertificateMapperAddListener(this);
    rootConfiguration.addCertificateMapperDeleteListener(this);

    //Initialize the existing certificate mappers.
    for (String mapperName : rootConfiguration.listCertificateMappers())
    {
      CertificateMapperCfg mapperConfiguration =
           rootConfiguration.getCertificateMapper(mapperName);
      mapperConfiguration.addChangeListener(this);

      if (mapperConfiguration.isEnabled())
      {
        String className = mapperConfiguration.getJavaClass();
        try
        {
          CertificateMapper<?> mapper = loadMapper(className, mapperConfiguration, true);
          certificateMappers.put(mapperConfiguration.dn(), mapper);
          DirectoryServer.registerCertificateMapper(mapperConfiguration.dn(), mapper);
        }
        catch (InitializationException ie)
        {
          logger.error(ie.getMessageObject());
          continue;
        }
      }
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(
                      CertificateMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // certificate mapper.
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
                                 CertificateMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    CertificateMapper<?> certificateMapper = null;

    // Get the name of the class and make sure we can instantiate it as a
    // certificate mapper.
    String className = configuration.getJavaClass();
    try
    {
      certificateMapper = loadMapper(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      certificateMappers.put(configuration.dn(), certificateMapper);
      DirectoryServer.registerCertificateMapper(configuration.dn(), certificateMapper);
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
                      CertificateMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // certificate mapper is in use.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 CertificateMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    DirectoryServer.deregisterCertificateMapper(configuration.dn());

    CertificateMapper<?> certificateMapper = certificateMappers.remove(configuration.dn());
    if (certificateMapper != null)
    {
      certificateMapper.finalizeCertificateMapper();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      CertificateMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // certificate mapper.
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
                                 CertificateMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Get the existing mapper if it's already enabled.
    CertificateMapper<?> existingMapper = certificateMappers.get(configuration.dn());

    // If the new configuration has the mapper disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingMapper != null)
      {
        DirectoryServer.deregisterCertificateMapper(configuration.dn());

        CertificateMapper<?> certificateMapper = certificateMappers.remove(configuration.dn());
        if (certificateMapper != null)
        {
          certificateMapper.finalizeCertificateMapper();
        }
      }

      return ccr;
    }

    // Get the class for the certificate mapper.  If the mapper is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the mapper is disabled, then instantiate the class and
    // initialize and register it as a certificate mapper.
    String className = configuration.getJavaClass();
    if (existingMapper != null)
    {
      if (! className.equals(existingMapper.getClass().getName()))
      {
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    CertificateMapper<?> certificateMapper = null;
    try
    {
      certificateMapper = loadMapper(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      certificateMappers.put(configuration.dn(), certificateMapper);
      DirectoryServer.registerCertificateMapper(configuration.dn(), certificateMapper);
    }

    return ccr;
  }

  /**
   * Loads the specified class, instantiates it as a certificate mapper, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the certificate mapper
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        certificate mapper.  It must not be {@code null}.
   * @param  initialize     Indicates whether the certificate mapper instance
   *                        should be initialized.
   *
   * @return  The possibly initialized certificate mapper.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the certificate mapper.
   */
  private CertificateMapper<?> loadMapper(String className,
                                       CertificateMapperCfg configuration,
                                       boolean initialize)
          throws InitializationException
  {
    try
    {
      CertificateMapperCfgDefn definition =
           CertificateMapperCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends CertificateMapper> mapperClass =
           propertyDefinition.loadClass(className, CertificateMapper.class);
      CertificateMapper mapper = mapperClass.newInstance();

      if (initialize)
      {
        mapper.initializeCertificateMapper(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!mapper.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_CERTMAPPER_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return mapper;
    }
    catch (InitializationException e) {
      // Avoid re-wrapping the initialization exception.
      throw e;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_CERTMAPPER_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}
