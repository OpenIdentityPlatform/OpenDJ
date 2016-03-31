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
package org.opends.server.core;

import static org.forgerock.http.routing.RouteMatchers.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.routing.Router;
import org.forgerock.http.routing.RoutingMode;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.HTTPEndpointCfgDefn;
import org.forgerock.opendj.server.config.server.HTTPEndpointCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the set of HTTP
 * endpoints defined in the Directory Server. It will initialize the HTTP
 * endpoints when the server starts, and then will manage any additions,
 * removals, or modifications to any HTTP endpoints while the server is running.
 */
public class HttpEndpointConfigManager implements ConfigurationChangeListener<HTTPEndpointCfg>,
    ConfigurationAddListener<HTTPEndpointCfg>, ConfigurationDeleteListener<HTTPEndpointCfg>
{
  private static final LocalizedLogger LOGGER = LocalizedLogger.getLoggerForThisClass();

  private final Router router;
  private final Map<DN, HttpApplication> applications;

  /**
   * Creates a new instance of this HTTP endpoint config manager.
   *
   * @param router
   *          The {@link Router} where to register configured {@link HttpEndpoint}
   */
  public HttpEndpointConfigManager(Router router)
  {
    this.router = router;
    this.applications = new HashMap<>();
  }

  /**
   * Initializes all HTTP endpoints currently defined in the Directory Server
   * configuration. This should only be called at Directory Server startup.
   *
   * @param rootConfiguration
   *          The root configuration containing the {@link HttpEndpoint}
   *          configurations.
   * @throws ConfigException
   *           If a configuration problem causes the {@link HttpEndpoint}
   *           initialization process to fail.
   */
  public void registerTo(RootCfg rootConfiguration) throws ConfigException
  {
    rootConfiguration.addHTTPEndpointAddListener(this);
    rootConfiguration.addHTTPEndpointDeleteListener(this);

    for (String endpointName : rootConfiguration.listHTTPEndpoints())
    {
      final HTTPEndpointCfg configuration = rootConfiguration.getHTTPEndpoint(endpointName);
      configuration.addChangeListener(this);

      if (configuration.isEnabled())
      {
        final ConfigChangeResult result = applyConfigurationAdd(configuration);
        if (!result.getResultCode().equals(ResultCode.SUCCESS))
        {
          LOGGER.error(result.getMessages().get(0));
        }
      }
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(HTTPEndpointCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      return loadEndpoint(configuration).isConfigurationValid(unacceptableReasons);
    }
    catch (InitializationException ie)
    {
      unacceptableReasons.add(ie.getMessageObject());
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(HTTPEndpointCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    configuration.addChangeListener(this);
    if (!configuration.isEnabled())
    {
      return ccr;
    }

    try
    {
      final HttpApplication application = loadEndpoint(configuration).newHttpApplication();
      router.addRoute(
          requestUriMatcher(RoutingMode.STARTS_WITH, removeLeadingAndTrailingSlashes(configuration.getBasePath())),
          application.start());
      applications.put(configuration.dn(), application);
    }
    catch (HttpApplicationException e)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_HTTPENDPOINT_UNABLE_TO_START.get(configuration.dn(), stackTraceToSingleLineString(e)));
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(HTTPEndpointCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(HTTPEndpointCfg configuration)
  {
    router.removeRoute(
        requestUriMatcher(RoutingMode.STARTS_WITH, removeLeadingAndTrailingSlashes(configuration.getBasePath())));
    final HttpApplication application = applications.remove(configuration.dn());
    if (application != null)
    {
      application.stop();
    }
    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationChangeAcceptable(HTTPEndpointCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      return loadEndpoint(configuration).isConfigurationValid(unacceptableReasons);
    }
    catch (InitializationException e)
    {
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(HTTPEndpointCfg configuration)
  {
    applyConfigurationDelete(configuration);
    applyConfigurationAdd(configuration);
    return new ConfigChangeResult();
  }

  @SuppressWarnings("unchecked")
  private HttpEndpoint<?> loadEndpoint(HTTPEndpointCfg configuration) throws InitializationException
  {
    try
    {
      final Class<? extends HttpEndpoint<?>> endpointClass =
          (Class<? extends HttpEndpoint<?>>) HTTPEndpointCfgDefn.getInstance().getJavaClassPropertyDefinition()
              .loadClass(configuration.getJavaClass(), HttpEndpoint.class);
      return endpointClass.getDeclaredConstructor(configuration.configurationClass()).newInstance(configuration);
    }
    catch (Exception e)
    {
      throw new InitializationException(ERR_CONFIG_HTTPENDPOINT_INITIALIZATION_FAILED.get(configuration.getJavaClass(),
          configuration.dn(), stackTraceToSingleLineString(e)), e);
    }
  }

  private static String removeLeadingAndTrailingSlashes(String path)
  {
    // Remove leading /
    int start = 0;
    while (path.charAt(start) == '/')
    {
      start++;
    }

    // Remove trailing /
    int end = path.length();
    while (path.charAt(end - 1) == '/')
    {
      end--;
    }

    return path.substring(start, end);
  }
}
