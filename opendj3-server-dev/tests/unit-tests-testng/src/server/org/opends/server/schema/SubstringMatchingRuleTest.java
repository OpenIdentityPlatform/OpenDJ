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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.api.SubstringMatchingRule;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Abstract class for building test for the substring matching rules.
 * This class is intended to be extended by one class for each substring
 * matching rules.
 */
@SuppressWarnings("javadoc")
public abstract class SubstringMatchingRuleTest extends SchemaTestCase
{
  /**
   * Generate data for the test of the assertion match.
   */
  @DataProvider(name="substringMatchData")
  public abstract Object[][] createSubstringMatchData();

  /**
   * Generate data for the test of the middle string match.
   *
   * @return the data for the test of the middle string match.
   */
  @DataProvider(name="substringMiddleMatchData")
  public abstract Object[][] createSubstringMiddleMatchData();

  /**
   * Generate data for the test of the initial string match.
   *
   * @return the data for the test of the initial string match.
   */
  @DataProvider(name="substringInitialMatchData")
  public abstract Object[][] createSubstringInitialMatchData();

  /**
   * Generate data for the test of the final string match.
   *
   * @return the data for the test of the final string match.
   */
  @DataProvider(name="substringInitialMatchData")
  public abstract Object[][] createSubstringFinalMatchData();

  /**
   * Get an instance of the matching rule.
   *
   * @return An instance of the matching rule to test.
   */
  protected abstract SubstringMatchingRule getRule();

  /**
   * Test the normalization and the middle substring match.
   */
  @Test(dataProvider= "substringMiddleMatchData")
  public void middleMatchingRules(
      String value, String[] middleSubs, Boolean result) throws Exception
  {
    SubstringMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue =
      rule.normalizeAttributeValue(ByteString.valueOf(value));

    StringBuilder printableMiddleSubs = new StringBuilder();
    List<ByteSequence> middleList =
        new ArrayList<ByteSequence>(middleSubs.length);
    for (int i=0; i<middleSubs.length; i++)
    {
      printableMiddleSubs.append(middleSubs[i]);
      printableMiddleSubs.append(",");
      middleList.add(
          rule.normalizeSubstring(ByteString.valueOf(middleSubs[i])));
    }

    Boolean liveResult =
      rule.valueMatchesSubstring(normalizedValue, null, middleList, null);

    if (result != liveResult)
    {
      fail("middle substring matching rule " + rule +
          " does not give expected result (" + result + ") for values : " +
          value + " and " + printableMiddleSubs);
    }
  }

  /**
   * Test the normalization and the initial substring match.
   */
  @Test(dataProvider= "substringInitialMatchData")
  public void initialMatchingRules(String value, String initial, Boolean result) throws Exception
  {
    SubstringMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue =
      rule.normalizeAttributeValue(ByteString.valueOf(value));

    ByteString normalizedInitial =
      rule.normalizeAttributeValue(ByteString.valueOf(initial));
    Boolean liveResult = rule.valueMatchesSubstring(
        normalizedValue, normalizedInitial, null, null);
    if (result != liveResult)
    {
      fail("initial substring matching rule " + rule +
          " does not give expected result (" + result + ") for values : " +
          value + " and " + initial);
    }
    assertEquals(result, liveResult);
  }

  /**
   * Test the normalization and the final substring match.
   */
  @Test(dataProvider= "substringFinalMatchData")
  public void finalMatchingRules(String value, String finalValue, Boolean result) throws Exception
  {
    SubstringMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue =
      rule.normalizeAttributeValue(ByteString.valueOf(value));

    ByteString normalizedFinal =
      rule.normalizeAttributeValue(ByteString.valueOf(finalValue));
    Boolean liveResult = rule.valueMatchesSubstring(
        normalizedValue, null, null, normalizedFinal);
    if (result != liveResult)
    {
      fail("final substring matching rule " + rule +
          " does not give expected result (" + result + ") for values : " +
          value + " and " + finalValue);
    }
  }

  @Test(dataProvider= "substringMatchData")
  public void testSubstringAssertion(String value, String initialSub, String[] middleSubs, String finalSub,
      Boolean expectedResult) throws Exception
  {
    SubstringMatchingRule rule = getRule();
    ByteString normalizedValue = rule.normalizeAttributeValue(ByteString.valueOf(value));
    ArrayList<ByteSequence> anySubs = new ArrayList<ByteSequence>(middleSubs.length);
    for (String sub : middleSubs)
    {
      anySubs.add(ByteString.valueOf(sub));
    }
    Assertion assertion = rule.getSubstringAssertion(
        ByteString.valueOf(initialSub),
        anySubs,
        ByteString.valueOf(finalSub));

    Boolean result = assertion.matches(normalizedValue).toBoolean();
    assertEquals(result,  expectedResult);

  }
}
