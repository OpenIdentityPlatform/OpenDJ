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
package org.opends.server.core;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.ServerContextBuilder.*;

import java.io.File;

import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.TestCaseUtils;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.schema.SchemaHandler;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SchemaHandlerTestCase extends CoreTestCase
{
  @Test
  public void testSchemaInitialization() throws Exception
  {
    SchemaHandler handler = initializeSchemaHandler();
    Schema schema = handler.getSchema();

    assertThat(schema.getMatchingRules()).isNotEmpty(); // some matching rules defined
    schema.getSyntax(SchemaConstants.SYNTAX_DIRECTORY_STRING_OID);
    schema.getAttributeType("javaClassName"); // from file 03-rfc2713
    schema.getAttributeType("nisNetIdUser"); // from file 5-solaris.ldif
    schema.getObjectClass("changeLogEntry"); // from file 03-changelog.ldif
  }

  private SchemaHandler initializeSchemaHandler() throws Exception
  {
    SchemaHandler schemaHandler = new SchemaHandler();
    final ServerContext serverContext = aServerContext()
        .schemaDirectory(new File(TestCaseUtils.getBuildRoot(), "resource/schema"))
        .configFile(TestCaseUtils.getTestResource("configForTests/config-small.ldif"))
        .withConfigurationBootstrapped()
        .schemaHandler(schemaHandler)
        .build();

    schemaHandler.initialize(serverContext);
    return schemaHandler;
  }

}
