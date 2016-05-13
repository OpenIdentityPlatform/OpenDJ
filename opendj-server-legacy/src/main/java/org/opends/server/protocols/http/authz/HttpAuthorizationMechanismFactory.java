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
package org.opends.server.protocols.http.authz;

import static org.forgerock.util.Reject.checkNotNull;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_HTTPENDPOINT_INITIALIZATION_FAILED;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.opendj.server.config.meta.HTTPAuthorizationMechanismCfgDefn;
import org.forgerock.opendj.server.config.server.HTTPAuthorizationMechanismCfg;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;

/**
 * Creates {@link HttpAuthorizationMechanism} performing the authentication/authorization of incoming {@link Request}.
 */
public final class HttpAuthorizationMechanismFactory
{
  private final ServerContext serverContext;

  /**
   * Creates a new authorization mechanism factory.
   *
   * @param serverContext
   *          {@link ServerContext} of this directory server.
   * @throws NullPointerException
   *           if serverContext is null
   */
  public HttpAuthorizationMechanismFactory(ServerContext serverContext)
  {
    this.serverContext = checkNotNull(serverContext, "serverContext cannot be null");
  }

  /**
   * Creates a new {@link HttpAuthorizationMechanism} based on the configuration.
   *
   * @param config
   *          The configuration used to build this authorization mechanism.
   * @return a new {@link HttpAuthorizationMechanism}
   * @throws InitializationException
   *           If the filter cannot be created.
   */
  public HttpAuthorizationMechanism<?> newInstance(HTTPAuthorizationMechanismCfg config) throws InitializationException
  {
    try
    {
      @SuppressWarnings("unchecked")
      final Class<? extends HttpAuthorizationMechanism<?>> endpointClass =
          (Class<? extends HttpAuthorizationMechanism<?>>)
            HTTPAuthorizationMechanismCfgDefn.getInstance().getJavaClassPropertyDefinition().loadClass(
                config.getJavaClass(), HttpAuthorizationMechanism.class);

      return (HttpAuthorizationMechanism<?>) endpointClass
                          .getDeclaredConstructor(config.configurationClass(), ServerContext.class)
                          .newInstance(config, serverContext);
    }
    catch (Exception e)
    {
      if (e instanceof LocalizableException) {
        throw new InitializationException(((LocalizableException) e).getMessageObject());
      }
      if (e.getCause() != null && e.getCause() instanceof LocalizableException)
      {
        throw new InitializationException(((LocalizableException) e.getCause()).getMessageObject());
      }
      throw new InitializationException(ERR_CONFIG_HTTPENDPOINT_INITIALIZATION_FAILED.get(
          config.getJavaClass(), config.dn(), stackTraceToSingleLineString(e)), e);
    }
  }
}
