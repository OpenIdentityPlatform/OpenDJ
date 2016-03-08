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

import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.CoreSchemaCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Schema.SchemaUpdater;

/** Provides the core schema, which includes core matching rules and syntaxes. */
public class CoreSchemaProvider implements SchemaProvider<CoreSchemaCfg>,
  ConfigurationChangeListener<CoreSchemaCfg>
{
  private static final String NONE_ELEMENT = "NONE";

  /** The current configuration of core schema. */
  private CoreSchemaCfg currentConfig;
  private ServerContext serverContext;

  @Override
  public void initialize(final ServerContext serverContext, final CoreSchemaCfg configuration,
      final SchemaBuilder initialSchemaBuilder) throws ConfigException, InitializationException
  {
    this.serverContext = serverContext;
    this.currentConfig = configuration;

    updateSchemaFromConfiguration(initialSchemaBuilder, configuration);

    currentConfig.addCoreSchemaChangeListener(this);
  }

  /**
   * Update the provided schema builder with the provided configuration.
   *
   * @param schemaBuilder
   *          The schema builder to update.
   * @param configuration
   *          The configuration to use for update.
   */
  private void updateSchemaFromConfiguration(final SchemaBuilder schemaBuilder, final CoreSchemaCfg configuration)
  {
    schemaBuilder
      .setOption(ALLOW_ZERO_LENGTH_DIRECTORY_STRINGS, configuration.isAllowZeroLengthValuesDirectoryString())
      .setOption(STRICT_FORMAT_FOR_COUNTRY_STRINGS, configuration.isStrictFormatCountryString())
      .setOption(STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE,
          configuration.isStripSyntaxMinUpperBoundAttributeTypeDescription());
    // TODO : add the missing methods in schema builder for those properties
    // schemaBuilder.allowMalformedJPEGPhotos(configuration.)
    // schemaBuilder.allowMalformedNamesAndOptions(configuration.)
    // ...

    for (final String oid : configuration.getDisabledMatchingRule())
    {
      if (!oid.equals(NONE_ELEMENT))
      {
        schemaBuilder.removeMatchingRule(oid);
      }
    }

    for (final String oid : configuration.getDisabledSyntax())
    {
      if (!oid.equals(NONE_ELEMENT))
      {
        schemaBuilder.removeSyntax(oid);
      }
    }
  }

  @Override
  public void finalizeProvider()
  {
    currentConfig.removeCoreSchemaChangeListener(this);
  }

  @Override
  public boolean isConfigurationAcceptable(final CoreSchemaCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    // TODO : check that elements to disable are present in the schema ?
    return true;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final CoreSchemaCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    if (!configuration.isEnabled())
    {
      // TODO : fix message
      unacceptableReasons.add(LocalizableMessage.raw("The core schema must always be enabled"));
      return false;
    }
    // TODO : check that elements to disable are present in the schema ?
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final CoreSchemaCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    // TODO : the server schema should probably be renamed to something like ServerSchema
    // Even after migration to SDK schema, it will be probably be kept
    try
    {
      serverContext.getSchema().updateSchema(new SchemaUpdater()
      {
        @Override
        public Schema update(SchemaBuilder builder)
        {
          updateSchemaFromConfiguration(builder, configuration);
          return builder.toSchema();
        }
      });
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }
}
