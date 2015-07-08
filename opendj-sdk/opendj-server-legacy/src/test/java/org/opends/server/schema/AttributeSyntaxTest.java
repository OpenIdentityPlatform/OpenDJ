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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
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
   */
  protected abstract AttributeSyntax<?> getRule();

  /**
   * Test the normalization and the approximate comparison.
   */
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
    Boolean liveResult =
      syntax.valueIsAcceptable(ByteString.valueOf(value), reason);

    if (liveResult != result)
    {
      fail(syntax + ".valueIsAcceptable gave bad result for " + value + " reason : " + reason);
    }
  }
}
