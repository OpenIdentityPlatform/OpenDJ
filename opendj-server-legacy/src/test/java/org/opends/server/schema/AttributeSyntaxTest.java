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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.testng.Assert.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaOptions;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@RemoveOnceSDKSchemaIsUsed
public abstract class AttributeSyntaxTest extends SchemaTestCase
{
  /**
   * Create data for the testAcceptableValues test.
   * This should be a table of tables with 2 elements.
   * The first one should be the value to test, the second the expected
   * result of the test.
   *
   * @return a table containing data for the testAcceptableValues Test.
   */
  @DataProvider(name="acceptableValues")
  public abstract Object[][] createAcceptableValues();

  /**
   * Get an instance of the attribute syntax that must be tested.
   *
   * @return An instance of the attribute syntax that must be tested.
   * @throws Exception
   *           if a problem occurs
   */
  protected abstract AttributeSyntax<?> getRule() throws Exception;

  /** Test the normalization and the approximate comparison. */
  @Test(dataProvider= "acceptableValues")
  public void testAcceptableValues(String value, Boolean result) throws Exception
  {
    // Make sure that the specified class can be instantiated as a task.

    SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());
    schemaBuilder.setOption(SchemaOptions.STRICT_FORMAT_FOR_COUNTRY_STRINGS, true);
    Schema schema = schemaBuilder.toSchema();
    Syntax syntax = getRule().getSDKSyntax(schema);

    LocalizableMessageBuilder reason = new LocalizableMessageBuilder();
    // test the valueIsAcceptable method
    boolean liveResult =
      syntax.valueIsAcceptable(ByteString.valueOfUtf8(value), reason);

    assertSame(liveResult, result, syntax + ".valueIsAcceptable gave bad result for " + value + " reason : " + reason);
  }
}
