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

import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newAuthorizationFilter;
import static org.forgerock.util.Reject.checkNotNull;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_HTTPENDPOINT_CONFLICTING_AUTHZ_DN;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_HTTPENDPOINT_INITIALIZATION_FAILED;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_HTTPENDPOINT_INVALID_AUTHZ_DN;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_HTTPENDPOINT_UNABLE_TO_START;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
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
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.opendj.server.config.meta.HTTPEndpointCfgDefn;
import org.forgerock.opendj.server.config.server.HTTPAuthorizationMechanismCfg;
import org.forgerock.opendj.server.config.server.HTTPEndpointCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.services.context.Context;
import org.forgerock.services.routing.RouteMatcher;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.protocols.http.authz.HttpAuthorizationMechanism;
import org.opends.server.protocols.http.authz.HttpAuthorizationMechanismFactory;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the set of HTTP
 * endpoints defined in the Directory Server. It will initialize the HTTP
 * endpoints when the server starts, and then will manage any additions,
 * removals, or modifications to any HTTP endpoints while the server is running.
 */
public class HttpEndpointConfigManager implements ConfigurationChangeListener<HTTPEndpointCfg>,
                                                  ConfigurationAddListener<HTTPEndpointCfg>,
                                                  ConfigurationDeleteListener<HTTPEndpointCfg>
{
  private static final LocalizedLogger LOGGER = LocalizedLogger.getLoggerForThisClass();

  private final AuthorizationMechanismManager auhtzFilterManager;
  private final ServerContext serverContext;
  private final Router router;
  private final Map<DN, Pair<HttpApplication, Handler>> startedApplications;

  /**
   * Creates a new instance of this HTTP endpoint config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public HttpEndpointConfigManager(ServerContext serverContext)
  {
    this.serverContext = checkNotNull(serverContext, "serverContext cannot be null");
    this.auhtzFilterManager = new AuthorizationMechanismManager();
    this.router = serverContext.getHTTPRouter();
    this.startedApplications = new HashMap<>();
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
    auhtzFilterManager.registerTo(rootConfiguration);

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
      // Check that endpoint's authorization filters are valid.
      auhtzFilterManager.getFilters(configuration.dn(), configuration.getAuthorizationMechanismDNs());
      return loadEndpoint(configuration).isConfigurationValid(unacceptableReasons);
    }
    catch (InitializationException | ConfigException e)
    {
      unacceptableReasons.add(e.getMessageObject());
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

    final RouteMatcher<Request> route = newRoute(configuration.getBasePath());
    try
    {
      final HttpApplication application = loadEndpoint(configuration).newHttpApplication();
      final Handler handler = application.start();
      startedApplications.put(configuration.dn(), Pair.of(application, handler));
      bindApplication(auhtzFilterManager.getFilters(configuration.dn(), configuration.getAuthorizationMechanismDNs()),
                      handler, configuration.getBasePath());
    }
    catch (HttpApplicationException e)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_HTTPENDPOINT_UNABLE_TO_START.get(configuration.dn(), stackTraceToSingleLineString(e)));
      router.addRoute(route, ErrorHandler.INTERNAL_SERVER_ERROR);
    }
    catch (InitializationException | ConfigException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
      router.addRoute(route, ErrorHandler.INTERNAL_SERVER_ERROR);
    }
    return ccr;
  }

  private void bindApplication(final Iterable<? extends ConditionalFilter> conditionalAuthorizationFilters,
      final Handler applicationHandler, final String basePath)
  {
    router.addRoute(newRoute(basePath),
                    Handlers.chainOf(applicationHandler, newAuthorizationFilter(conditionalAuthorizationFilters)));
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
    router.removeRoute(newRoute(configuration.getBasePath()));
    final Pair<HttpApplication, Handler> startedApplication = startedApplications.remove(configuration.dn());
    if (startedApplication != null)
    {
      startedApplication.getFirst().stop();
    }
    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationChangeAcceptable(HTTPEndpointCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationAddAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(HTTPEndpointCfg configuration)
  {
    final Pair<HttpApplication, Handler> startedApplication = startedApplications.remove(configuration.dn());
    if (startedApplication != null)
    {
      router.addRoute(newRoute(configuration.getBasePath()), ErrorHandler.SERVICE_UNAVAILABLE);
      startedApplication.getFirst().stop();
    }
    return applyConfigurationAdd(configuration);
  }

  @SuppressWarnings("unchecked")
  private HttpEndpoint<?> loadEndpoint(HTTPEndpointCfg configuration) throws InitializationException
  {
    try
    {
      final Class<? extends HttpEndpoint<?>> endpointClass =
          (Class<? extends HttpEndpoint<?>>) HTTPEndpointCfgDefn.getInstance().getJavaClassPropertyDefinition()
              .loadClass(configuration.getJavaClass(), HttpEndpoint.class);
      return endpointClass.getDeclaredConstructor(configuration.configurationClass(), ServerContext.class)
                          .newInstance(configuration, serverContext);
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

  private static RouteMatcher<Request> newRoute(String basePath) {
    return requestUriMatcher(RoutingMode.STARTS_WITH, removeLeadingAndTrailingSlashes(basePath));
  }

  /**
   * Manages the {@link AuthorizationMechanism}. When a configuration is updated, all the {@link HttpEndpoint}s
   * referencing the updated {@link AuthorizationMechanism} will be removed and re-added to the {@link Router} once the
   * filter chain has been reconfigured.
   */
  private final class AuthorizationMechanismManager implements
      ConfigurationChangeListener<HTTPAuthorizationMechanismCfg>,
      ConfigurationAddListener<HTTPAuthorizationMechanismCfg>,
      ConfigurationDeleteListener<HTTPAuthorizationMechanismCfg>
  {
    private final HttpAuthorizationMechanismFactory authzFilterFactory =
        new HttpAuthorizationMechanismFactory(serverContext);
    private final Map<DN, HttpAuthorizationMechanism<?>> authzFilters = new HashMap<>();

    public void registerTo(RootCfg rootConfiguration) throws ConfigException
    {
      rootConfiguration.addHTTPAuthorizationMechanismAddListener(this);
      rootConfiguration.addHTTPAuthorizationMechanismDeleteListener(this);

      for (String authorizationName : rootConfiguration.listHTTPAuthorizationMechanisms())
      {
        final HTTPAuthorizationMechanismCfg configuration =
            rootConfiguration.getHTTPAuthorizationMechanism(authorizationName);
        configuration.addChangeListener(this);

        final ConfigChangeResult result = applyConfigurationAdd(configuration);
        if (!result.getResultCode().equals(ResultCode.SUCCESS))
        {
          throw new ConfigException(result.getMessages().get(0));
        }
      }
    }

    Collection<? extends ConditionalFilter> getFilters(DN endpointConfigDN, Set<DN> authzFilterDNs)
        throws ConfigException
    {
      final SortedSet<HttpAuthorizationMechanism<?>> endpointAuthzMechanisms = new TreeSet<>();
      for (DN dn : authzFilterDNs)
      {
        final HttpAuthorizationMechanism<?> authzMechanism = authzFilters.get(dn);
        if (authzMechanism == null)
        {
          throw new ConfigException(ERR_CONFIG_HTTPENDPOINT_INVALID_AUTHZ_DN.get(endpointConfigDN, dn));
        }
        if (!endpointAuthzMechanisms.add(authzMechanism))
        {
          throw new ConfigException(ERR_CONFIG_HTTPENDPOINT_CONFLICTING_AUTHZ_DN.get(
              endpointConfigDN, dn.rdn(0), endpointAuthzMechanisms.tailSet(authzMechanism).first()));
        }
      }
      return endpointAuthzMechanisms;
    }

    private void rebindStartedApplications(DN authorizationFilterDN, ConfigChangeResult ccr)
    {
      final RootCfg rootConfiguration = serverContext.getRootConfig();
      for (String endpointName : rootConfiguration.listHTTPEndpoints())
      {
        try
        {
          final HTTPEndpointCfg configuration = rootConfiguration.getHTTPEndpoint(endpointName);
          if (configuration.getAuthorizationMechanismDNs().contains(authorizationFilterDN))
          {
            final Pair<HttpApplication, Handler> startedApplication = startedApplications.get(configuration.dn());
            if (startedApplication != null)
            {
              bindApplication(getFilters(configuration.dn(), configuration.getAuthorizationMechanismDNs()),
                              startedApplication.getSecond(),
                              configuration.getBasePath());
            }
          }
        }
        catch (ConfigException e)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_CONFIG_HTTPENDPOINT_UNABLE_TO_START.get(endpointName, stackTraceToSingleLineString(e)));
          continue;
        }
      }
    }

    @Override
    public boolean isConfigurationDeleteAcceptable(HTTPAuthorizationMechanismCfg configuration,
        List<LocalizableMessage> unacceptableReasons)
    {
      return true;
    }

    @Override
    public ConfigChangeResult applyConfigurationDelete(HTTPAuthorizationMechanismCfg configuration)
    {
      doConfigurationDelete(configuration);
      final ConfigChangeResult ccr = new ConfigChangeResult();
      rebindStartedApplications(configuration.dn(), ccr);
      return ccr;
    }

    private void doConfigurationDelete(HTTPAuthorizationMechanismCfg configuration)
    {
      authzFilters.remove(configuration.dn());
    }

    @Override
    public boolean isConfigurationAddAcceptable(HTTPAuthorizationMechanismCfg configuration,
        List<LocalizableMessage> unacceptableReasons)
    {
      try
      {
        return authzFilterFactory.newInstance(configuration) != null;
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    @Override
    public ConfigChangeResult applyConfigurationAdd(HTTPAuthorizationMechanismCfg configuration)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      if (!configuration.isEnabled())
      {
        return ccr;
      }
      try
      {
        authzFilters.put(configuration.dn(), authzFilterFactory.newInstance(configuration));
        rebindStartedApplications(configuration.dn(), ccr);
      }
      catch (InitializationException ie)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ie.getMessageObject());
      }
      return ccr;
    }

    @Override
    public boolean isConfigurationChangeAcceptable(HTTPAuthorizationMechanismCfg configuration,
        List<LocalizableMessage> unacceptableReasons)
    {
      return !configuration.isEnabled() ||
             ( isConfigurationDeleteAcceptable(configuration, unacceptableReasons)
             && isConfigurationAddAcceptable(configuration, unacceptableReasons) );
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(HTTPAuthorizationMechanismCfg configuration)
    {
      doConfigurationDelete(configuration);
      return applyConfigurationAdd(configuration);
    }
  }

  /**
   * {@link Handler} returning error status. This is used when {@link HttpApplication} failed to start or while a
   * configuration is updated.
   */
  private static final class ErrorHandler implements Handler
  {
    private final static Handler SERVICE_UNAVAILABLE = new ErrorHandler(Status.SERVICE_UNAVAILABLE);
    private final static Handler INTERNAL_SERVER_ERROR = new ErrorHandler(Status.INTERNAL_SERVER_ERROR);

    private final Status status;

    ErrorHandler(Status status)
    {
      this.status = status;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(Context context, Request request)
    {
      return Response.newResponsePromise(new Response(status));
    }
  }
}
