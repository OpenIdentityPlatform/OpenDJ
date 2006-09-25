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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
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
  public abstract EqualityMatchingRule getRule();

  /**
   * Test the normalization and the comparison of valid values.
   */
  @Test(dataProvider= "equalitymatchingrules")
  public void equalityMatchingRules(String value1,
                             String value2, Boolean result) throws Exception
  {
    EqualityMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue1 =
      rule.normalizeValue(new ASN1OctetString(value1));
    ByteString normalizedValue2 =
      rule.normalizeValue(new ASN1OctetString(value2));

    Boolean liveResult = rule.areEqual(normalizedValue1, normalizedValue2);
    assertEquals(result, liveResult);
  }

  
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
   * Test the normalization and the comparison in the warning mode 
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
      equalityMatchingRules(value1, value2, result);
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
    EqualityMatchingRule rule = getRule();

    // normalize the 2 provided values
    boolean success = false;
    try
    {
      rule.normalizeValue(new ASN1OctetString(value));
    } catch (DirectoryException e) {
      success = true;
    }

    if (!success)
    {
      fail("The matching rule : " + rule.getName() +
           " should detect that value \"" + value + "\" is invalid");
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
    EqualityMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue1 =
      rule.normalizeValue(new ASN1OctetString(value1));
    ByteString normalizedValue2 =
      rule.normalizeValue(new ASN1OctetString(value2));

    ConditionResult liveResult =
      rule.valuesMatch(normalizedValue1, normalizedValue2);
    if (result == true)
      assertEquals(ConditionResult.TRUE, liveResult);
    else
      assertEquals(ConditionResult.FALSE, liveResult);
    
  }

}
