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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server;

import static org.mockito.Mockito.*;

import java.io.File;

import org.forgerock.opendj.config.server.ServerManagementContext;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.InitializationException;

/**
 * A server context builder to be used in tests to minimize arrange phase.
 */
@SuppressWarnings("javadoc")
public class ServerContextBuilder
{
  private final ServerContext serverContext;
  private final DirectoryEnvironmentConfig env;

  public static ServerContextBuilder aServerContext() throws InitializationException
  {
    return new ServerContextBuilder();
  }

  public ServerContextBuilder() throws InitializationException
  {
    serverContext = mock(ServerContext.class);

    env = new DirectoryEnvironmentConfig(false);
    env.setMaintainConfigArchive(false);
    when(serverContext.getEnvironment()).thenReturn(env);
  }

  public ServerContext build()
  {
    return serverContext;
  }

  public ServerContextBuilder schemaDirectory(File path)
      throws InitializationException
  {
    env.setSchemaDirectory(path);
    return this;
  }

  public ServerContextBuilder schema(org.opends.server.types.Schema schema)
  {
    when(serverContext.getSchema()).thenReturn(schema);
    when(serverContext.getSchemaNG()).thenReturn(schema.getSchemaNG());
    return this;
  }

  public ServerContextBuilder configFile(File path)
      throws InitializationException
  {
    env.setConfigFile(path);
    return this;
  }

  /**
   * Ensure that configuration is fully bootstrapped. Only use when necessary as
   * it will impact test performance.
   */
  public ServerContextBuilder withConfigurationBootstrapped() throws InitializationException
  {
    final ConfigurationHandler configHandler = ConfigurationHandler.bootstrapConfiguration(serverContext);
    final ServerManagementContext serverManagementContext = new ServerManagementContext(configHandler);
    when(serverContext.getServerManagementContext()).thenReturn(serverManagementContext);
    when(serverContext.getRootConfig()).thenReturn(serverManagementContext.getRootConfiguration());
    return this;
  }

}
