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
package org.opends.server.schema;

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.CoreSchemaCfg;
import org.opends.server.ConfigurationMock;
import org.opends.server.core.CoreTestCase;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.util.CollectionUtils.*;

@SuppressWarnings("javadoc")
public class CoreSchemaProviderTestCase extends CoreTestCase
{
  private static final String DIRECTORY_STRING_SYNTAX_OID = "1.3.6.1.4.1.1466.115.121.1.15";
  private static final String GENERALIZED_TIME_MATCHING_RULE_OID = "2.5.13.27";

  @Test
  public void testInitializeWithoutError() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(coreSchemaCfg, new SchemaBuilder(), mock(SchemaUpdater.class));

    verify(coreSchemaCfg).addCoreSchemaChangeListener(provider);
  }

  @Test
  public void testEnableZeroLengthDirectoryStrings() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);
    when(coreSchemaCfg.isAllowZeroLengthValuesDirectoryString()).thenReturn(true);
    SchemaBuilder schemaBuilder = new SchemaBuilder();

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(coreSchemaCfg, schemaBuilder, mock(SchemaUpdater.class));

    assertThat(schemaBuilder.toSchema().getOption(ALLOW_ZERO_LENGTH_DIRECTORY_STRINGS)).isTrue();
  }

  @Test
  public void testDisableSyntax() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);
    when(coreSchemaCfg.getDisabledSyntax()).thenReturn(newTreeSet(DIRECTORY_STRING_SYNTAX_OID));
    SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(coreSchemaCfg, schemaBuilder, mock(SchemaUpdater.class));

    assertThat(schemaBuilder.toSchema().hasSyntax(DIRECTORY_STRING_SYNTAX_OID)).isFalse();
  }

  @Test
  public void testDisableMatchingRule() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);
    when(coreSchemaCfg.getDisabledMatchingRule()).thenReturn(newTreeSet(GENERALIZED_TIME_MATCHING_RULE_OID));
    SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(coreSchemaCfg, schemaBuilder, mock(SchemaUpdater.class));

    assertThat(schemaBuilder.toSchema().hasMatchingRule(DIRECTORY_STRING_SYNTAX_OID)).isFalse();
  }
}
