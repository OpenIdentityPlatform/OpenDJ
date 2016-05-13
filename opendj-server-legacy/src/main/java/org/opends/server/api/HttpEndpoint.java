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
package org.opends.server.api;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.List;

import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.server.HTTPEndpointCfg;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;

/**
 * Endpoint attach an {@link HttpApplication} to an URI.
 *
 * @param <C>
 *          Type of the configuration used by this {@link HttpEndpoint}
 */
public abstract class HttpEndpoint<C extends HTTPEndpointCfg>
{
  /** Configuration of this endpoint. */
  protected final C configuration;

  /** Context of this LDAP server. */
  protected final ServerContext serverContext;

  /**
   * Create a new {@link HttpEndpoint} with the given configuration.
   *
   * @param configuration
   *          Configuration of this {@link HttpEndpoint}
   * @param serverContext
   *          Context of this LDAP server
   */
  public HttpEndpoint(C configuration, ServerContext serverContext)
  {
    this.configuration = configuration;
    this.serverContext = serverContext;
  }

  /**
   * Check that the configuration of this {@link HttpEndpoint} is valid. This
   * default implementation try to instantiate and start the underlying
   * {@link HttpApplication}.
   *
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          configuration is not acceptable.
   * @return true if the configuration is valid.
   */
  public boolean isConfigurationValid(List<LocalizableMessage> unacceptableReasons)
  {
    HttpApplication dummyApplication = null;
    try
    {
      dummyApplication = newHttpApplication();
      dummyApplication.start();
      return true;
    }
    catch (HttpApplicationException e)
    {
      if (e instanceof LocalizableException)
      {
        unacceptableReasons.add(((LocalizableException) e).getMessageObject());
        return false;
      }
      unacceptableReasons.add(ERR_CONFIG_HTTPENDPOINT_INVALID_CONFIGURATION
          .get(configuration.dn(), stackTraceToSingleLineString(e)));
      return false;
    }
    catch (InitializationException ie)
    {
      unacceptableReasons.add(ie.getMessageObject());
      return false;
    }
    finally
    {
      if (dummyApplication != null)
      {
        dummyApplication.stop();
      }
    }
  }

  /**
   * Create a new HttpApplication.
   *
   * @return an {@link HttpApplication} configured and ready to be started.
   * @throws InitializationException
   *           If the application cannot be created.
   */
  public abstract HttpApplication newHttpApplication() throws InitializationException;

}
