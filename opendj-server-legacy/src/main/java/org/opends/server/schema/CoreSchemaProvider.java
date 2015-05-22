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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.schema;

import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.CoreSchemaCfg;
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

  /** The current schema builder. */
  private SchemaBuilder currentSchemaBuilder;

  /** Updater to notify schema update when configuration changes. */
  private SchemaUpdater schemaUpdater;

  /** {@inheritDoc} */
  @Override
  public void initialize(final CoreSchemaCfg configuration, final SchemaBuilder initialSchemaBuilder,
      final SchemaUpdater schemaUpdater) throws ConfigException, InitializationException
  {
    this.currentConfig = configuration;
    this.currentSchemaBuilder = initialSchemaBuilder;
    this.schemaUpdater = schemaUpdater;

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

  /** {@inheritDoc} */
  @Override
  public void finalizeProvider()
  {
    currentConfig.removeCoreSchemaChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(final CoreSchemaCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    // TODO : check that elements to disable are present in the schema ?
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public SchemaBuilder getSchema()
  {
    return currentSchemaBuilder;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(final CoreSchemaCfg configuration)
  {
    currentSchemaBuilder = schemaUpdater.getSchemaBuilder();

    updateSchemaFromConfiguration(currentSchemaBuilder, configuration);

    final boolean isUpdated = schemaUpdater.updateSchema(currentSchemaBuilder.toSchema());

    // TODO : fix result code + log an error in case of failure
    final ConfigChangeResult result = new ConfigChangeResult();
    result.setResultCode(isUpdated ? ResultCode.SUCCESS : ResultCode.OTHER);
    return result;
  }

}
