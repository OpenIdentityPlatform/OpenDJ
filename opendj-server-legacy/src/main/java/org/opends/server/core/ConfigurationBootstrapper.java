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
 * Copyright 2014 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.opends.server.types.InitializationException;

/**
 * Bootstrap the server configuration.
 */
public class ConfigurationBootstrapper
{

  /**
   * Bootstrap the server configuration.
   * <p>
   * The returned server management context is fully initialized with
   * all configuration objects valued from configuration file.
   *
   * @param serverContext
   *            The server context.
   * @return the server management context
   * @throws InitializationException
   *            If an error occurs during bootstrapping.
   */
  public static ServerManagementContext bootstrap(ServerContext serverContext) throws InitializationException {
    final ConfigurationFramework configFramework = ConfigurationFramework.getInstance();
    try
    {
      if (!configFramework.isInitialized())
      {
        configFramework.initialize();
      }
    }
    catch (ConfigException e)
    {
      // TODO : fix the message
      throw new InitializationException(LocalizableMessage.raw("Cannot initialize config framework"), e);
    }

    final ConfigurationHandler configurationHandler = new ConfigurationHandler(serverContext);
    configurationHandler.initialize();

    return new ServerManagementContext(configurationHandler);
  }
}
