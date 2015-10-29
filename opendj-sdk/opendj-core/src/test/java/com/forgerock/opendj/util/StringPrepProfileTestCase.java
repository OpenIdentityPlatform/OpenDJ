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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */

package com.forgerock.opendj.util;

import static org.testng.Assert.assertEquals;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This Test Class tests various matching rules for their compability against
 * RFC 4517,4518 and 3454.
 */
@SuppressWarnings("javadoc")
public class StringPrepProfileTestCase extends UtilTestCase {
    /**
     * Tests the stringprep preparation sans any case folding. This is
     * applicable to case exact matching rules only.
     */
    @Test(dataProvider = "exactRuleData")
    public void testStringPrepNoCaseFold(final String value1, final String value2,
            final ConditionResult result) throws Exception {
        // Take any caseExact matching rule.
        final MatchingRule rule = Schema.getCoreSchema().getMatchingRule("2.5.13.5");
        final Assertion assertion = rule.getAssertion(ByteString.valueOfUtf8(value1));
        final ByteString normalizedValue2 =
                rule.normalizeAttributeValue(ByteString.valueOfUtf8(value2));
        final ConditionResult liveResult = assertion.matches(normalizedValue2);
        assertEquals(result, liveResult);
    }

    /**
     * Tests the stringprep preparation with case folding. This is applicable to
     * case ignore matching rules only.
     */
    @Test(dataProvider = "caseFoldRuleData")
    public void testStringPrepWithCaseFold(final String value1, final String value2,
            final ConditionResult result) throws Exception {
        // Take any caseExact matching rule.
        final MatchingRule rule = Schema.getCoreSchema().getMatchingRule("2.5.13.2");
        final Assertion assertion = rule.getAssertion(ByteString.valueOfUtf8(value1));
        final ByteString normalizedValue2 =
                rule.normalizeAttributeValue(ByteString.valueOfUtf8(value2));
        final ConditionResult liveResult = assertion.matches(normalizedValue2);
        assertEquals(result, liveResult);
    }

    /** Generates data for case exact matching rules. */
    @DataProvider(name = "exactRuleData")
    public Object[][] createExactRuleData() {
        return new Object[][] { { "12345678", "12345678", ConditionResult.TRUE },
            { "ABC45678", "ABC45678", ConditionResult.TRUE },
            { "ABC45678", "abc45678", ConditionResult.FALSE },
            { "\u0020foo\u0020bar\u0020\u0020", "foo bar", ConditionResult.TRUE },
            { "test\u00AD\u200D", "test", ConditionResult.TRUE },
            { "foo\u000Bbar", "foo\u0020bar", ConditionResult.TRUE },
            { "foo\u070Fbar", "foobar", ConditionResult.TRUE }, };
    }

    /** Generates data for case ignore matching rules. */
    @DataProvider(name = "caseFoldRuleData")
    public Object[][] createIgnoreRuleData() {
        return new Object[][] { { "12345678", "12345678", ConditionResult.TRUE },
            { "ABC45678", "abc45678", ConditionResult.TRUE },
            { "\u0020foo\u0020bar\u0020\u0020", "foo bar", ConditionResult.TRUE },
            { "test\u00AD\u200D", "test", ConditionResult.TRUE },
            { "foo\u000Bbar", "foo\u0020bar", ConditionResult.TRUE },
            { "foo\u070Fbar", "foobar", ConditionResult.TRUE },
            { "foo\u0149bar", "foo\u02BC\u006Ebar", ConditionResult.TRUE },
            { "foo\u017Bbar", "foo\u017Cbar", ConditionResult.TRUE },
            { "foo\u017BBAR", "foo\u017Cbar", ConditionResult.TRUE }, };
    }
}
