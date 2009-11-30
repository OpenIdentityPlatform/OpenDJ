/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.sdk.schema;



import static org.opends.server.schema.SchemaConstants.SYNTAX_IA5_STRING_OID;

import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.sdk.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Substitution syntax tests.
 */
public class SubstitutionSyntaxTestCase extends SyntaxTestCase
{
  /**
   * {@inheritDoc}
   */
  @Override
  protected Syntax getRule() throws SchemaException, DecodeException
  {
    // Use IA5String syntax as our substitute.
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSubstitutionSyntax("9.9.9", "Unimplemented Syntax",
        SYNTAX_IA5_STRING_OID, false);
    return builder.toSchema().getSyntax("9.9.9");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name = "acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object[][] { { "12345678", true },
        { "12345678\u2163", false }, };
  }



  public void testSelfSubstitute1() throws SchemaException,
      DecodeException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.15 "
        + " DESC 'Replacing DirectorySyntax'  "
        + " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )", true);
    List<Message> warnings = new LinkedList<Message>();
    builder.toSchema(warnings);
    Assert.assertFalse(warnings.isEmpty());
  }



  public void testSelfSubstitute2() throws SchemaException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.15",
        "Replacing DirectorySyntax", "1.3.6.1.4.1.1466.115.121.1.15",
        true);
    List<Message> warnings = new LinkedList<Message>();
    builder.toSchema(warnings);
    Assert.assertFalse(warnings.isEmpty());
  }



  public void testUndefinedSubstitute1() throws SchemaException,
      DecodeException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.15 "
        + " DESC 'Replacing DirectorySyntax'  " + " X-SUBST '1.1.1' )",
        true);
    List<Message> warnings = new LinkedList<Message>();
    builder.toSchema(warnings);
    Assert.assertFalse(warnings.isEmpty());
  }



  public void testUndefinedSubstitute2() throws SchemaException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.15",
        "Replacing DirectorySyntax", "1.1.1", true);
    List<Message> warnings = new LinkedList<Message>();
    builder.toSchema(warnings);
    Assert.assertFalse(warnings.isEmpty());
  }



  @Test(expectedExceptions = SchemaException.class)
  public void testSubstituteCore1() throws SchemaException,
      DecodeException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.26 "
        + " DESC 'Replacing DirectorySyntax'  " + " X-SUBST '9.9.9' )",
        true);
  }



  @Test(expectedExceptions = SchemaException.class)
  public void testSubstituteCore2() throws SchemaException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.26",
        "Replacing DirectorySyntax", "9.9.9", true);
  }
}
