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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import static org.testng.Assert.*;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.types.ByteString;
import org.opends.messages.MessageBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
   * Get an instance of the attribute syntax that muste be tested.
   * 
   * @return An instance of the attribute syntax that muste be tested.
   */
  protected abstract AttributeSyntax getRule();

  /**
   * Test the normalization and the approximate comparison.
   */
  @Test(dataProvider= "acceptableValues")
  public void testAcceptableValues(String value, Boolean result)
         throws Exception
  {
    // Make sure that the specified class can be instantiated as a task.
    AttributeSyntax syntax = getRule();

    MessageBuilder reason = new MessageBuilder();
    // test the valueIsAcceptable method
    Boolean liveResult =
      syntax.valueIsAcceptable(ByteString.valueOf(value), reason);
    
    if (liveResult != result)
      fail(syntax + ".valueIsAcceptable gave bad result for " + value + 
          "reason : " + reason);

    // call the getters
    syntax.getApproximateMatchingRule();
    syntax.getDescription();
    syntax.getEqualityMatchingRule();
    syntax.getOID();
    syntax.getOrderingMatchingRule();
    syntax.getSubstringMatchingRule();
    syntax.getSyntaxName();
    syntax.toString();
  }
}
