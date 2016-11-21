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
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.VALIDATION_POLICY;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.DISABLED;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.LENIENT;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.STRICT;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.CoreSchemaCfg;
import org.opends.messages.ConfigMessages;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.SchemaHandler.SchemaUpdater;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * Provides the core schema, which includes core matching rules and syntaxes.
 */
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
          configuration.isStripSyntaxMinUpperBoundAttributeTypeDescription())
      .setOption(ALLOW_MALFORMED_JPEG_PHOTOS, !configuration.isStrictFormatJPEGPhotos())
      .setOption(ALLOW_MALFORMED_CERTIFICATES, !configuration.isStrictFormatCertificates())
      .setOption(ALLOW_NON_STANDARD_TELEPHONE_NUMBERS, !configuration.isStrictFormatTelephoneNumbers())
      .setOption(ALLOW_ATTRIBUTE_TYPES_WITH_NO_SUP_OR_SYNTAX, configuration.isAllowAttributeTypesWithNoSupOrSyntax());

    switch (configuration.getJsonValidationPolicy())
    {
    case DISABLED:
      schemaBuilder.setOption(VALIDATION_POLICY, DISABLED);
      break;
    case LENIENT:
      schemaBuilder.setOption(VALIDATION_POLICY, LENIENT);
      break;
    case STRICT:
      schemaBuilder.setOption(VALIDATION_POLICY, STRICT);
      break;
    }

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
    return true;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final CoreSchemaCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    if (!configuration.isEnabled())
    {
      unacceptableReasons.add(ConfigMessages.ERR_CONFIG_CORE_SCHEMA_PROVIDER_DISABLED.get(configuration.dn()));
      return false;
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final CoreSchemaCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      serverContext.getSchemaHandler().updateSchema(new SchemaUpdater()
      {
        @Override
        public void update(SchemaBuilder builder)
        {
          updateSchemaFromConfiguration(builder, configuration);
        }
      });
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }
}
