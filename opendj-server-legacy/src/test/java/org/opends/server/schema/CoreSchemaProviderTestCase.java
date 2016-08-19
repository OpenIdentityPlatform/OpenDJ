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

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.CoreSchemaCfg;
import org.forgerock.opendj.config.ConfigurationMock;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.CoreTestCase;
import org.opends.server.core.ServerContext;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.ServerContextBuilder.*;
import static org.opends.server.util.CollectionUtils.*;

import java.io.File;

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
    provider.initialize(getServerContext(), coreSchemaCfg, new SchemaBuilder());

    verify(coreSchemaCfg).addCoreSchemaChangeListener(provider);
  }

  private ServerContext getServerContext() throws Exception
  {
    return aServerContext()
        .schemaDirectory(new File(TestCaseUtils.getBuildRoot(), "resource/schema"))
        .configFile(TestCaseUtils.getTestResource("configForTests/config-small.ldif"))
        .withConfigurationBootstrapped()
        .schemaHandler(new SchemaHandler())
        .build();
  }

  @Test
  public void testEnableZeroLengthDirectoryStrings() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);
    when(coreSchemaCfg.isAllowZeroLengthValuesDirectoryString()).thenReturn(true);
    SchemaBuilder schemaBuilder = new SchemaBuilder();

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(getServerContext(), coreSchemaCfg, schemaBuilder);

    assertThat(schemaBuilder.toSchema().getOption(ALLOW_ZERO_LENGTH_DIRECTORY_STRINGS)).isTrue();
  }

  @Test
  public void testDisableSyntax() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);
    when(coreSchemaCfg.getDisabledSyntax()).thenReturn(newTreeSet(DIRECTORY_STRING_SYNTAX_OID));
    SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(getServerContext(), coreSchemaCfg, schemaBuilder);

    assertThat(schemaBuilder.toSchema().hasSyntax(DIRECTORY_STRING_SYNTAX_OID)).isFalse();
  }

  @Test
  public void testDisableMatchingRule() throws Exception
  {
    CoreSchemaCfg coreSchemaCfg = ConfigurationMock.mockCfg(CoreSchemaCfg.class);
    when(coreSchemaCfg.getDisabledMatchingRule()).thenReturn(newTreeSet(GENERALIZED_TIME_MATCHING_RULE_OID));
    SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());

    CoreSchemaProvider provider = new CoreSchemaProvider();
    provider.initialize(getServerContext(), coreSchemaCfg, schemaBuilder);

    assertThat(schemaBuilder.toSchema().hasMatchingRule(DIRECTORY_STRING_SYNTAX_OID)).isFalse();
  }
}
