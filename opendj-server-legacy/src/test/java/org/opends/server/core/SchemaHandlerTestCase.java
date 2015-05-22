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
package org.opends.server.core;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.ServerContextBuilder.*;

import java.io.File;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.ServerContextBuilder.MockSchemaUpdater;
import org.opends.server.TestCaseUtils;
import org.opends.server.schema.SchemaUpdater;
import org.opends.server.types.InitializationException;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SchemaHandlerTestCase extends CoreTestCase
{

  private static final String DIRECTORY_STRING_SYNTAX_OID = "1.3.6.1.4.1.1466.115.121.1.15";

  @Test
  public void testSchemaInitialization() throws Exception
  {
    MockSchemaUpdater schemaUpdater = new MockSchemaUpdater(Schema.getEmptySchema());
    initializeSchemaHandler(schemaUpdater);

    assertThat(schemaUpdater.getSchemaBuilder()).isNotNull();

    Schema schema = schemaUpdater.getSchemaBuilder().toSchema();

    assertThat(schema.getMatchingRules()).isNotEmpty(); // some matching rules defined
    schema.getSyntax(DIRECTORY_STRING_SYNTAX_OID);
    schema.getAttributeType("javaClassName"); // from file 03-rfc2713
    schema.getAttributeType("nisNetIdUser"); // from file 5-solaris.ldif
    schema.getObjectClass("changeLogEntry"); // from file 03-changelog.ldif
  }


  private void initializeSchemaHandler(SchemaUpdater updater) throws InitializationException, ConfigException
  {
    final ServerContext serverContext = aServerContext().
        schemaDirectory(new File(TestCaseUtils.getBuildRoot(), "resource/schema")).
        configFile(TestCaseUtils.getTestResource("config-small.ldif")).
        schemaUpdater(updater).
        withConfigurationBootstrapped().
        build();

    SchemaHandler schemaHandler = new SchemaHandler();
    schemaHandler.initialize(serverContext);
  }

}
