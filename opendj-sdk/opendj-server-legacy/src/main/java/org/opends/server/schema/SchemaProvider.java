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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.SchemaProviderCfg;
import org.forgerock.opendj.config.server.ConfigException;
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
   * @param configuration
   *          Configuration of the provider.
   * @param initialSchemaBuilder
   *          Schema builder to update during initialization phase.
   * @param schemaUpdater
   *          The updater to call when applying a configuration change.
   * @throws ConfigException
   *           If a configuration problem arises in the process of performing
   *           the initialization.
   * @throws InitializationException
   *           If a problem that is not configuration-related occurs during
   *           initialization.
   */
  void initialize(T configuration, SchemaBuilder initialSchemaBuilder, SchemaUpdater schemaUpdater)
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

  /**
   * Returns the schema builder, as updated by this provider.
   *
   * @return the schema builder resulting from this provider.
   */
  SchemaBuilder getSchema();
}
