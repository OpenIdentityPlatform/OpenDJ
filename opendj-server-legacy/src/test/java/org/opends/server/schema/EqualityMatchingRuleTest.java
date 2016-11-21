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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.server.config.client.GlobalCfgClient;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AcceptRejectWarn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.getServer;
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
    AcceptRejectWarn accept = DirectoryServer.getCoreConfigManager().getSyntaxEnforcementPolicy();
    setSyntaxEnforcementPolicy(GlobalCfgDefn.InvalidAttributeSyntaxBehavior.WARN);
    try
    {
      testValuesMatch(value1, value2, result);
    }
    finally
    {
      setSyntaxEnforcementPolicy(convertAccept(accept));
    }
  }

  private static GlobalCfgDefn.InvalidAttributeSyntaxBehavior convertAccept(AcceptRejectWarn accept)
  {
    switch (accept)
    {
    case ACCEPT:
      return GlobalCfgDefn.InvalidAttributeSyntaxBehavior.ACCEPT;
    case WARN:
      return GlobalCfgDefn.InvalidAttributeSyntaxBehavior.WARN;
    case REJECT:
    default:
      return GlobalCfgDefn.InvalidAttributeSyntaxBehavior.REJECT;
    }
  }

  private void setSyntaxEnforcementPolicy(GlobalCfgDefn.InvalidAttributeSyntaxBehavior value) throws Exception
  {
    try (ManagementContext conf = getServer().getConfiguration())
    {
      GlobalCfgClient globalCfg = conf.getRootConfiguration().getGlobalConfiguration();
      globalCfg.setInvalidAttributeSyntaxBehavior(value);
      globalCfg.commit();
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
      rule.normalizeAttributeValue(ByteString.valueOfUtf8(value));
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
      rule.normalizeAttributeValue(ByteString.valueOfUtf8(value1));
    Assertion assertion = rule.getAssertion(ByteString.valueOfUtf8(value2));

    ConditionResult liveResult = assertion.matches(normalizedValue1);
    assertEquals(liveResult, ConditionResult.valueOf(result));
  }

}
