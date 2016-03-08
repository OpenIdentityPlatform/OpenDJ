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
package org.opends.server.schema;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.SchemaProviderCfg;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;

/**
 * Provides some schema elements to load at startup.
 * <p>
 * A schema provider must be able to update the provided {@code SchemaBuilder}
 * at initialization and to use the provided {@code SchemaUpdater} to update the
 * schema on further configuration changes.
 *
 * @param <T>
 *          The type of provider configuration.
 */
public interface SchemaProvider<T extends SchemaProviderCfg>
{
  /**
   * Initialize the schema provider from provided configuration and schema
   * builder.
   *
   * @param serverContext
   *            The server context.
   * @param configuration
   *          Configuration of the provider.
   * @param initialSchemaBuilder
   *          Schema builder to update during initialization phase.
   * @throws ConfigException
   *           If a configuration problem arises in the process of performing
   *           the initialization.
   * @throws InitializationException
   *           If a problem that is not configuration-related occurs during
   *           initialization.
   */
  void initialize(ServerContext serverContext, T configuration, SchemaBuilder initialSchemaBuilder)
      throws ConfigException, InitializationException;

  /**
   * Finalize the provider.
   */
  void finalizeProvider();

  /**
   * Indicates whether the provided configuration is acceptable for this
   * provider.
   *
   * @param configuration
   *          The provider configuration for which to make the determination.
   * @param unacceptableReasons
   *          A list that may be used to hold the reasons that the provided
   *          configuration is not acceptable.
   * @return {@code true} if the provided configuration is acceptable for this
   *         provider, or {@code false} if not.
   */
  boolean isConfigurationAcceptable(T configuration, List<LocalizableMessage> unacceptableReasons);

}
