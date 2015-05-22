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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server;

import static org.mockito.Mockito.*;

import java.io.File;

import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.server.core.ConfigurationBootstrapper;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.SchemaUpdater;
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

  public static ServerContextBuilder aServerContext()
  {
    return new ServerContextBuilder();
  }

  public ServerContextBuilder()
  {
    serverContext = mock(ServerContext.class);

    env = new DirectoryEnvironmentConfig(false);
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

  public ServerContextBuilder schemaNG(Schema schema)
  {
    when(serverContext.getSchemaNG()).thenReturn(schema);
    return this;
  }

  public ServerContextBuilder configFile(File path)
      throws InitializationException
  {
    env.setConfigFile(path);
    return this;
  }

  public ServerContextBuilder schemaUpdater(SchemaUpdater updater)
  {
    when(serverContext.getSchemaUpdater()).thenReturn(updater);
    return this;
  }

  /**
   * Ensure that configuration is fully bootstrapped. Only use when necessary as
   * it will impact test performance.
   */
  public ServerContextBuilder withConfigurationBootstrapped()
      throws InitializationException
  {
    if (serverContext.getSchemaUpdater() == null) {
      throw new RuntimeException("You must set a non-null schema updater to bootstrap configuration.");
    }
    final ServerManagementContext serverManagementContext =
        ConfigurationBootstrapper.bootstrap(serverContext);
    when(serverContext.getServerManagementContext()).thenReturn(
        serverManagementContext);
    return this;
  }

  /** A mock for schema updater. */
  public static final class MockSchemaUpdater implements SchemaUpdater
  {
    private Schema schema;

    public MockSchemaUpdater(Schema schema)
    {
      this.schema = schema;
    }

    @Override
    public boolean updateSchema(Schema schema)
    {
      this.schema = schema;
      return true;
    }

    @Override
    public SchemaBuilder getSchemaBuilder()
    {
      return new SchemaBuilder(schema);
    }
  }

}
