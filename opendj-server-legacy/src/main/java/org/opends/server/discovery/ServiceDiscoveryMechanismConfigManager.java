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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.discovery;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.ServiceDiscoveryMechanismCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;

/**
 * Manages configuration additions and deletions of service discovery mechanisms in the server configuration.
 */
public class ServiceDiscoveryMechanismConfigManager implements
    ConfigurationAddListener<ServiceDiscoveryMechanismCfg>,
    ConfigurationDeleteListener<ServiceDiscoveryMechanismCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final Map<String, ServiceDiscoveryMechanism<?>> serviceDiscoveryMechanisms = new ConcurrentHashMap<>();
  private final ServerContext serverContext;

  /**
   * Declares a new Configuration Manager for this Directory Server.
   *
   * @param serverContext the current directory server context
   */
  public ServiceDiscoveryMechanismConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes the Mechanism Configuration Manager and its configuration at startup.
   * Registers itself as a service in the directory server instance.
   */
  public void initializeServiceDiscoveryMechanismConfigManager()
  {
    RootCfg root = serverContext.getRootConfig();
    for (String mechanism : root.listServiceDiscoveryMechanisms())
    {
      try
      {
        ServiceDiscoveryMechanismCfg configuration = root.getServiceDiscoveryMechanism(mechanism);
        serviceDiscoveryMechanisms.put(configuration.name(), loadAndInitializeMechanism(configuration));
      }
      catch (Exception e)
      {
        logger.error(ERR_SERVICE_DISCOVERY_CONFIG_MANAGER_INIT_MECHANISM.get(
            mechanism, stackTraceToSingleLineString(e)));
      }
    }
    try
    {
      root.addServiceDiscoveryMechanismAddListener(this);
      root.addServiceDiscoveryMechanismDeleteListener(this);
    }
    catch (ConfigException e)
    {
      logger.error(ERR_SERVICE_DISCOVERY_CONFIG_MANAGER_LISTENER.get(stackTraceToSingleLineString(e)));
    }
  }

  ServiceDiscoveryMechanism<?> getMechanism(String name)
  {
    return serviceDiscoveryMechanisms.get(name);
  }

  @Override
  public boolean isConfigurationAddAcceptable(ServiceDiscoveryMechanismCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      ServiceDiscoveryMechanism newMechanism = loadAndInitializeMechanism(configuration);
      return newMechanism.isConfigurationAcceptable(configuration, unacceptableReasons, serverContext);
    }
    catch (Exception e)
    {
      unacceptableReasons.add(ERR_SERVICE_DISCOVERY_CONFIG_MANAGER_ADD_MECHANISM.get(
          configuration.name(), stackTraceToSingleLineString(e)));
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(ServiceDiscoveryMechanismCfg configuration)
  {
    ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      serviceDiscoveryMechanisms.put(configuration.name(), loadAndInitializeMechanism(configuration));
    }
    catch (Exception e)
    {
      ccr.setResultCode(ResultCode.OTHER);
      ccr.addMessage(ERR_SERVICE_DISCOVERY_CONFIG_MANAGER_ADD_MECHANISM.get(
          configuration.name(), stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private ServiceDiscoveryMechanism loadAndInitializeMechanism(ServiceDiscoveryMechanismCfg cfg) throws Exception
  {
    final ServiceDiscoveryMechanism newMechanism =
        ((Class<ServiceDiscoveryMechanism>) DirectoryServer.loadClass(cfg.getJavaClass())).newInstance();
    newMechanism.initializeMechanism(cfg, serverContext);
    return newMechanism;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(ServiceDiscoveryMechanismCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(ServiceDiscoveryMechanismCfg configuration)
  {
    serviceDiscoveryMechanisms.remove(configuration.name()).finalizeMechanism();
    return new ConfigChangeResult();
  }

  /**
   * Finalize all service discovery mechanism for shutdown.
   */
  public void finalize()
  {
    for (ServiceDiscoveryMechanism<?> service : serviceDiscoveryMechanisms.values())
    {
      service.finalizeMechanism();
    }
    serverContext.getRootConfig().removeServiceDiscoveryMechanismAddListener(this);
    serverContext.getRootConfig().removeServiceDiscoveryMechanismDeleteListener(this);
  }
}
