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
import org.opends.server.admin.std.meta.CertificateMapperCfgDefn;
import org.opends.server.admin.std.server.CertificateMapperCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.CertificateMapper;
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
  // A mapping between the DNs of the config entries and the associated
  // certificate mappers.
  private ConcurrentHashMap<DN,CertificateMapper> certificateMappers;



  /**
   * Creates a new instance of this certificate mapper config manager.
   */
  public CertificateMapperConfigManager()
  {
    certificateMappers = new ConcurrentHashMap<DN,CertificateMapper>();
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
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any certificate mapper entries are added or removed.
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
        String className = mapperConfiguration.getMapperClass();
        try
        {
          CertificateMapper mapper = loadMapper(className, mapperConfiguration);
          certificateMappers.put(mapperConfiguration.dn(), mapper);
          DirectoryServer.registerCertificateMapper(mapperConfiguration.dn(),
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
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      CertificateMapperCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // certificate mapper.
      String className = configuration.getMapperClass();
      try
      {
        loadMapper(className, null);
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
                                 CertificateMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    CertificateMapper certificateMapper = null;

    // Get the name of the class and make sure we can instantiate it as a
    // certificate mapper.
    String className = configuration.getMapperClass();
    try
    {
      certificateMapper = loadMapper(className, configuration);
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
      certificateMappers.put(configuration.dn(), certificateMapper);
      DirectoryServer.registerCertificateMapper(configuration.dn(),
                                                certificateMapper);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      CertificateMapperCfg configuration,
                      List<String> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // certificate mapper is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 CertificateMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    DirectoryServer.deregisterCertificateMapper(configuration.dn());

    CertificateMapper certificateMapper =
         certificateMappers.remove(configuration.dn());
    if (certificateMapper != null)
    {
      certificateMapper.finalizeCertificateMapper();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      CertificateMapperCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // certificate mapper.
      String className = configuration.getMapperClass();
      try
      {
        loadMapper(className, null);
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
                                 CertificateMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the existing mapper if it's already enabled.
    CertificateMapper existingMapper =
         certificateMappers.get(configuration.dn());


    // If the new configuration has the mapper disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingMapper != null)
      {
        DirectoryServer.deregisterCertificateMapper(configuration.dn());

        CertificateMapper certificateMapper =
             certificateMappers.remove(configuration.dn());
        if (certificateMapper != null)
        {
          certificateMapper.finalizeCertificateMapper();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the certificate mapper.  If the mapper is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the mapper is disabled, then instantiate the class and
    // initialize and register it as a certificate mapper.
    String className = configuration.getMapperClass();
    if (existingMapper != null)
    {
      if (! className.equals(existingMapper.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    CertificateMapper certificateMapper = null;
    try
    {
      certificateMapper = loadMapper(className, configuration);
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
      certificateMappers.put(configuration.dn(), certificateMapper);
      DirectoryServer.registerCertificateMapper(configuration.dn(),
                                                certificateMapper);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a certificate mapper, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the certificate mapper
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        certificate mapper, or {@code null} if the
   *                        certificate mapper should not be initialized.
   *
   * @return  The possibly initialized certificate mapper.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the certificate mapper.
   */
  private CertificateMapper loadMapper(String className,
                                 CertificateMapperCfg configuration)
          throws InitializationException
  {
    try
    {
      CertificateMapperCfgDefn definition =
           CertificateMapperCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getMapperClassPropertyDefinition();
      Class<? extends CertificateMapper> mapperClass =
           propertyDefinition.loadClass(className, CertificateMapper.class);
      CertificateMapper mapper = mapperClass.newInstance();

      if (configuration != null)
      {
        Method method =
             mapper.getClass().getMethod("initializeCertificateMapper",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(mapper, configuration);
      }

      return mapper;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_CERTMAPPER_INITIALIZATION_FAILED;
      String message = getMessage(msgID, className,
                                  String.valueOf(configuration.dn()),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }
}

