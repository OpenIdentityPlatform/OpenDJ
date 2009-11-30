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



import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.opends.messages.Message;
import org.opends.sdk.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Regex syntax tests.
 */
public class RegexSyntaxTestCase extends SyntaxTestCase
{
  /**
   * {@inheritDoc}
   */
  @Override
  protected Syntax getRule() throws SchemaException, DecodeException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addPatternSyntax("1.1.1",
        "Host and Port in the format of HOST:PORT", Pattern
            .compile("^[a-z-A-Z]+:[0-9.]+\\d$"), false);
    return builder.toSchema().getSyntax("1.1.1");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name = "acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object[][] { { "invalid regex", false },
        { "host:0.0.0", true }, };
  }



  public void testInvalidPattern() throws SchemaException,
      DecodeException
  {
    // This should fail due to invalid pattern.
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSyntax(
        "( 1.1.1 DESC 'Host and Port in the format of HOST:PORT' "
            + " X-PATTERN '^[a-z-A-Z+:[0-@.]+\\d$' )", true);
    List<Message> warnings = new LinkedList<Message>();
    builder.toSchema(warnings);
    Assert.assertFalse(warnings.isEmpty());
  }



  @Test
  public void testDecode() throws SchemaException, DecodeException
  {
    // This should fail due to invalid pattern.
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addSyntax(
        "( 1.1.1 DESC 'Host and Port in the format of HOST:PORT' "
            + " X-PATTERN '^[a-z-A-Z]+:[0-9.]+\\d$' )", true);
    builder.toSchema();
  }

}
