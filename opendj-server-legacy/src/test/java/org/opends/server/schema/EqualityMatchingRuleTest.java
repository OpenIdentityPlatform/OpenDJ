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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.schema;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AcceptRejectWarn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test The equality matching rules and the equality matching rule api.
 */
public abstract class EqualityMatchingRuleTest extends SchemaTestCase
{
  /**
   * Generate data for the EqualityMatching Rule test.
   *
   * @return the data for the equality matching rule test.
   */
  @DataProvider(name="equalitymatchingrules")
  public abstract Object[][] createEqualityMatchingRuleTest();

  /**
   * Generate invalid data for the EqualityMatching Rule test.
   *
   * @return the data for the EqualityMatchingRulesInvalidValuestest.
   */
  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public abstract Object[][] createEqualityMatchingRuleInvalidValues();


  /**
   * Get an instance of the matching rule.
   *
   * @return An instance of the matching rule to test.
   */
  protected abstract MatchingRule getRule();

  /**
   * Generate data for the EqualityMatching Rule test in warn mode.
   *
   * @return the data for the equality matching rule test in warn mode.
   */
  @DataProvider(name="warnmodeEqualityMatchingRule")
  public Object[][] createWarnmodeEqualityMatchingRuleTest()
  {
    return new Object[][] {};
  }

  /**
   * Test the normalization and the comparison in the warning mode.
   */
  @Test(dataProvider= "warnmodeEqualityMatchingRule")
  public void warnmodeEqualityMatchingRules(
      String value1, String value2, Boolean result)
      throws Exception
  {
    AcceptRejectWarn accept = DirectoryServer.getSyntaxEnforcementPolicy();
    DirectoryServer.setSyntaxEnforcementPolicy(AcceptRejectWarn.WARN);
    try
    {
      testValuesMatch(value1, value2, result);
    }
    finally
    {
      DirectoryServer.setSyntaxEnforcementPolicy(accept);
    }
  }

  /**
   * Test that invalid values are rejected.
   */
  @Test(dataProvider= "equalityMatchingRuleInvalidValues")
  public void equalityMatchingRulesInvalidValues(String value) throws Exception
    {
    // Get the instance of the rule to be tested.
    MatchingRule rule = getRule();

    // normalize the 2 provided values
    try
    {
      rule.normalizeAttributeValue(ByteString.valueOf(value));
      fail("The matching rule : " + rule.getNameOrOID()
          + " should detect that value \"" + value + "\" is invalid");
    }
    catch (DecodeException ignored)
    {
    }
  }

  /**
   * Generate data for the EqualityMatching Rule test.
   *
   * @return the data for the equality matching rule test.
   */
  @DataProvider(name="valuesMatch")
  public Object[][] createValuesMatch()
  {
    return new Object[][] {};
  }

  /**
   * Test the valuesMatch method used for extensible filters.
   */
  @Test(dataProvider= "valuesMatch")
  public void testValuesMatch(String value1,
                             String value2, Boolean result) throws Exception
  {
    MatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue1 =
      rule.normalizeAttributeValue(ByteString.valueOf(value1));
    Assertion assertion = rule.getAssertion(ByteString.valueOf(value2));

    ConditionResult liveResult = assertion.matches(normalizedValue1);
    assertEquals(liveResult, ConditionResult.valueOf(result));
  }

}
