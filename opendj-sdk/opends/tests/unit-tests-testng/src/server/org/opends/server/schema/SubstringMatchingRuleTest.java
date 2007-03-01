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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Abstract class for building test for the substring matching rules.
 * This class is intended to be extended by one class for each substring
 * matching rules.
 */
public abstract class SubstringMatchingRuleTest extends SchemaTestCase
{
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
  public abstract SubstringMatchingRule getRule();

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
      rule.normalizeValue(new ASN1OctetString(value));

    StringBuilder printableMiddleSubs = new StringBuilder();
    List<ByteString> middleList = new ArrayList<ByteString>(middleSubs.length);
    for (int i=0; i<middleSubs.length; i++)
    {
      printableMiddleSubs.append(middleSubs[i]);
      printableMiddleSubs.append(",");
      middleList.add(
          rule.normalizeSubstring(new ASN1OctetString(middleSubs[i])));
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
  public void initialMatchingRules(
      String value, String initial, Boolean result) throws Exception
  {
    SubstringMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue =
      rule.normalizeValue(new ASN1OctetString(value));

    ByteString normalizedInitial =
      rule.normalizeValue(new ASN1OctetString(initial));
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
  public void finalMatchingRules(
      String value, String finalValue, Boolean result) throws Exception
  {
    SubstringMatchingRule rule = getRule();

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue =
      rule.normalizeValue(new ASN1OctetString(value));

    ByteString normalizedFinal =
      rule.normalizeValue(new ASN1OctetString(finalValue));
    Boolean liveResult = rule.valueMatchesSubstring(
        normalizedValue, null, null, normalizedFinal);
    if (result != liveResult)
    {
      fail("final substring matching rule " + rule +
          " does not give expected result (" + result + ") for values : " +
          value + " and " + finalValue);
    }
  }
}
