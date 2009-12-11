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



import static org.opends.server.schema.SchemaConstants.OMR_OID_GENERIC_ENUM;

import org.opends.sdk.ConditionResult;
import org.opends.sdk.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.sdk.ByteString;



/**
 * Enum syntax tests.
 */
public class EnumSyntaxTestCase extends SyntaxTestCase
{
  /**
   * {@inheritDoc}
   */
  @Override
  protected Syntax getRule() throws SchemaException, DecodeException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder.addEnumerationSyntax("3.3.3", "Day Of The Week", false, "monday",
        "tuesday", "wednesday", "thursday", "friday", "saturday",
        "sunday");
    return builder.toSchema().getSyntax("3.3.3");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name = "acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object[][] { { "arbit-day", false },
        { "wednesday", true }, };
  }



  @Test
  public void testDuplicateEnum() throws SchemaException,
      DecodeException
  {
    // This should be handled silently.
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder
        .addSyntax(
            "( 3.3.3  DESC 'Day Of The Week' "
                + " X-ENUM  ( 'monday' 'tuesday'   'wednesday'  'thursday'  'friday' "
                + " 'saturday' 'monday') )", true);
    builder.toSchema();
  }



  @Test
  public void testDecode() throws SchemaException, DecodeException
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder
        .addSyntax(
            "( 3.3.3  DESC 'Day Of The Week' "
                + " X-ENUM  ( 'monday' 'tuesday'   'wednesday'  'thursday'  'friday' "
                + " 'saturday' 'sunday') )", true);
    Schema schema = builder.toSchema();
    Syntax syntax = schema.getSyntax("3.3.3");
    MatchingRule rule = syntax.getOrderingMatchingRule();
    Assert.assertEquals(rule.getGreaterOrEqualAssertion(
        ByteString.valueOf("monday")).matches(
        rule.normalizeAttributeValue(ByteString.valueOf("thursday"))),
        ConditionResult.TRUE);
    Assert.assertEquals(rule.getLessOrEqualAssertion(
        ByteString.valueOf("monday")).matches(
        rule.normalizeAttributeValue(ByteString.valueOf("thursday"))),
        ConditionResult.FALSE);
    Assert.assertEquals(rule.getGreaterOrEqualAssertion(
        ByteString.valueOf("tuesday")).matches(
        rule.normalizeAttributeValue(ByteString.valueOf("monday"))),
        ConditionResult.FALSE);
    Assert.assertEquals(rule.getLessOrEqualAssertion(
        ByteString.valueOf("tuesday")).matches(
        rule.normalizeAttributeValue(ByteString.valueOf("monday"))),
        ConditionResult.TRUE);
    Assert.assertEquals(rule.getGreaterOrEqualAssertion(
        ByteString.valueOf("tuesday")).matches(
        rule.normalizeAttributeValue(ByteString.valueOf("tuesday"))),
        ConditionResult.TRUE);
    Assert.assertEquals(rule.getLessOrEqualAssertion(
        ByteString.valueOf("tuesday")).matches(
        rule.normalizeAttributeValue(ByteString.valueOf("tuesday"))),
        ConditionResult.TRUE);
    Assert
        .assertEquals(rule.getAssertion(ByteString.valueOf("tuesday"))
            .matches(
                rule.normalizeAttributeValue(ByteString
                    .valueOf("monday"))), ConditionResult.TRUE);
    Assert.assertEquals(
        rule.getAssertion(ByteString.valueOf("monday"))
            .matches(
                rule.normalizeAttributeValue(ByteString
                    .valueOf("thursday"))), ConditionResult.FALSE);
    Assert.assertEquals(
        rule.getAssertion(ByteString.valueOf("tuesday"))
            .matches(
                rule.normalizeAttributeValue(ByteString
                    .valueOf("tuesday"))), ConditionResult.FALSE);
    Assert.assertNotNull(schema.getMatchingRule(OMR_OID_GENERIC_ENUM
        + ".3.3.3"));
  }
}
