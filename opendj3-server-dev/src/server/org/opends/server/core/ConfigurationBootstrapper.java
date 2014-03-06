/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
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
