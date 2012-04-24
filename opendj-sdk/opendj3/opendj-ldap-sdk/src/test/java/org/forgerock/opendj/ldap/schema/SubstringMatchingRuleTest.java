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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap.schema;

import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Abstract class for building test for the substring matching rules. This class
 * is intended to be extended by one class for each substring matching rules.
 */
@SuppressWarnings("javadoc")
public abstract class SubstringMatchingRuleTest extends SchemaTestCase {
    /**
     * Generate invalid assertion values for the Matching Rule test.
     *
     * @return the data for the EqualityMatchingRulesInvalidValuestest.
     */
    @DataProvider(name = "substringInvalidAssertionValues")
    public abstract Object[][] createMatchingRuleInvalidAssertionValues();

    /**
     * Generate invalid attribute values for the Matching Rule test.
     *
     * @return the data for the EqualityMatchingRulesInvalidValuestest.
     */
    @DataProvider(name = "substringInvalidAttributeValues")
    public abstract Object[][] createMatchingRuleInvalidAttributeValues();

    /**
     * Generate data for the test of the final string match.
     *
     * @return the data for the test of the final string match.
     */
    @DataProvider(name = "substringInitialMatchData")
    public abstract Object[][] createSubstringFinalMatchData();

    /**
     * Generate data for the test of the initial string match.
     *
     * @return the data for the test of the initial string match.
     */
    @DataProvider(name = "substringInitialMatchData")
    public abstract Object[][] createSubstringInitialMatchData();

    /**
     * Generate data for the test of the middle string match.
     *
     * @return the data for the test of the middle string match.
     */
    @DataProvider(name = "substringMiddleMatchData")
    public abstract Object[][] createSubstringMiddleMatchData();

    /**
     * Test the normalization and the final substring match.
     */
    @Test(dataProvider = "substringFinalMatchData")
    public void finalMatchingRules(final String value, final String finalValue,
            final ConditionResult result) throws Exception {
        final MatchingRule rule = getRule();

        // normalize the 2 provided values and check that they are equals
        final ByteString normalizedValue = rule.normalizeAttributeValue(ByteString.valueOf(value));

        if (rule.getAssertion(null, null, ByteString.valueOf(finalValue)).matches(normalizedValue) != result
                || rule.getAssertion(ByteString.valueOf("*" + finalValue)).matches(normalizedValue) != result) {
            fail("final substring matching rule " + rule + " does not give expected result ("
                    + result + ") for values : " + value + " and " + finalValue);
        }
    }

    /**
     * Test the normalization and the initial substring match.
     */
    @Test(dataProvider = "substringInitialMatchData")
    public void initialMatchingRules(final String value, final String initial,
            final ConditionResult result) throws Exception {
        final MatchingRule rule = getRule();

        // normalize the 2 provided values and check that they are equals
        final ByteString normalizedValue = rule.normalizeAttributeValue(ByteString.valueOf(value));

        if (rule.getAssertion(ByteString.valueOf(initial), null, null).matches(normalizedValue) != result
                || rule.getAssertion(ByteString.valueOf(initial + "*")).matches(normalizedValue) != result) {
            fail("initial substring matching rule " + rule + " does not give expected result ("
                    + result + ") for values : " + value + " and " + initial);
        }
    }

    /**
     * Test that invalid values are rejected.
     */
    @Test(expectedExceptions = DecodeException.class,
            dataProvider = "substringInvalidAssertionValues")
    public void matchingRulesInvalidAssertionValues(final String subInitial, final String[] anys,
            final String subFinal) throws Exception {
        // Get the instance of the rule to be tested.
        final MatchingRule rule = getRule();

        final List<ByteSequence> anyList = new ArrayList<ByteSequence>(anys.length);
        for (final String middleSub : anys) {
            anyList.add(ByteString.valueOf(middleSub));
        }
        rule.getAssertion(subInitial == null ? null : ByteString.valueOf(subInitial), anyList,
                subFinal == null ? null : ByteString.valueOf(subFinal));
    }

    /**
     * Test that invalid values are rejected.
     */
    @Test(expectedExceptions = DecodeException.class,
            dataProvider = "substringInvalidAssertionValues")
    public void matchingRulesInvalidAssertionValuesString(final String subInitial,
            final String[] anys, final String subFinal) throws Exception {
        // Get the instance of the rule to be tested.
        final MatchingRule rule = getRule();

        final StringBuilder assertionString = new StringBuilder();
        if (subInitial != null) {
            assertionString.append(subInitial);
        }
        assertionString.append("*");
        for (final String middleSub : anys) {
            assertionString.append(middleSub);
            assertionString.append("*");
        }
        if (subFinal != null) {
            assertionString.append(subFinal);
        }
        rule.getAssertion(ByteString.valueOf(assertionString.toString()));
    }

    /**
     * Test the normalization and the middle substring match.
     */
    @Test(dataProvider = "substringMiddleMatchData")
    public void middleMatchingRules(final String value, final String[] middleSubs,
            final ConditionResult result) throws Exception {
        final MatchingRule rule = getRule();

        // normalize the 2 provided values and check that they are equals
        final ByteString normalizedValue = rule.normalizeAttributeValue(ByteString.valueOf(value));

        final StringBuilder printableMiddleSubs = new StringBuilder();
        final List<ByteSequence> middleList = new ArrayList<ByteSequence>(middleSubs.length);
        printableMiddleSubs.append("*");
        for (final String middleSub : middleSubs) {
            printableMiddleSubs.append(middleSub);
            printableMiddleSubs.append("*");
            middleList.add(ByteString.valueOf(middleSub));
        }

        if (rule.getAssertion(null, middleList, null).matches(normalizedValue) != result
                || rule.getAssertion(ByteString.valueOf(printableMiddleSubs)).matches(
                        normalizedValue) != result) {
            fail("middle substring matching rule " + rule + " does not give expected result ("
                    + result + ") for values : " + value + " and " + printableMiddleSubs);
        }
    }

    /**
     * Test that invalid values are rejected.
     */
    @Test(expectedExceptions = DecodeException.class,
            dataProvider = "substringInvalidAttributeValues")
    public void substringInvalidAttributeValues(final String value) throws Exception {
        // Get the instance of the rule to be tested.
        final MatchingRule rule = getRule();

        rule.normalizeAttributeValue(ByteString.valueOf(value));
    }

    /**
     * Get an instance of the matching rule.
     *
     * @return An instance of the matching rule to test.
     */
    protected abstract MatchingRule getRule();
}
