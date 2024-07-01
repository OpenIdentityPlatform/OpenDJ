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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.CASE_SENSITIVE_STRINGS;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.IGNORE_WHITE_SPACE;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.INDEXED_FIELD_PATTERNS;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.newJsonQueryEqualityMatchingRuleImpl;
import static org.forgerock.util.Options.defaultOptions;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.rest2ldap.schema.JsonSchema;
import org.forgerock.opendj.server.config.server.JsonSchemaCfg;
import org.forgerock.util.Options;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.SchemaHandler.SchemaUpdater;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/** Allows users to configure custom JSON matching rules and indexing. */
public class JsonSchemaProvider implements SchemaProvider<JsonSchemaCfg>, ConfigurationChangeListener<JsonSchemaCfg>
{
  /** The current configuration of JSON schema. */
  private JsonSchemaCfg currentConfig;
  private ServerContext serverContext;

  @Override
  public void initialize(final ServerContext serverContext, final JsonSchemaCfg configuration,
                         final SchemaBuilder initialSchemaBuilder) throws ConfigException, InitializationException
  {
    this.serverContext = serverContext;
    this.currentConfig = configuration;

    addCustomJsonMatchingRule(initialSchemaBuilder, configuration);
    currentConfig.addJsonSchemaChangeListener(this);
  }

  private void addCustomJsonMatchingRule(final SchemaBuilder schemaBuilder, final JsonSchemaCfg configuration)
  {
    if (!configuration.isEnabled())
    {
      return;
    }

    final String nameOrOid = configuration.getMatchingRuleName() != null
            ? configuration.getMatchingRuleName() : configuration.getMatchingRuleOid();
    final Options options = defaultOptions().set(CASE_SENSITIVE_STRINGS, configuration.isCaseSensitiveStrings())
                                            .set(IGNORE_WHITE_SPACE, configuration.isIgnoreWhiteSpace())
                                            .set(INDEXED_FIELD_PATTERNS, configuration.getIndexedField());
    final MatchingRuleImpl matchingRuleImpl = newJsonQueryEqualityMatchingRuleImpl(nameOrOid, options);
    final MatchingRule.Builder builder = schemaBuilder.buildMatchingRule(configuration.getMatchingRuleOid())
                                                      .syntaxOID(JsonSchema.getJsonQuerySyntax().getOID())
                                                      .implementation(matchingRuleImpl);
    if (configuration.getMatchingRuleName() != null)
    {
      builder.names(configuration.getMatchingRuleName());
    }
    // Let users overwrite core matching rule definitions in order to control indexing.
    builder.addToSchemaOverwrite();
  }

  @Override
  public void finalizeProvider()
  {
    if (currentConfig.isEnabled())
    {
      try
      {
        serverContext.getSchemaHandler().updateSchema(new SchemaUpdater()
        {
          @Override
          public void update(SchemaBuilder builder)
          {
            builder.removeMatchingRule(currentConfig.getMatchingRuleOid());
          }
        });
      }
      catch (DirectoryException e)
      {
        // Ignore.
      }
    }
    currentConfig.removeJsonSchemaChangeListener(this);
  }

  @Override
  public boolean isConfigurationAcceptable(final JsonSchemaCfg configuration,
                                           final List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final JsonSchemaCfg configuration,
                                                 final List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final JsonSchemaCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      serverContext.getSchemaHandler().updateSchema(new SchemaUpdater()
      {
        @Override
        public void update(SchemaBuilder builder)
        {
          if (currentConfig.isEnabled())
          {
            builder.removeMatchingRule(currentConfig.getMatchingRuleOid());
          }
          addCustomJsonMatchingRule(builder, configuration);
        }
      });
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    finally
    {
      currentConfig = configuration;
    }
    return ccr;
  }
}
