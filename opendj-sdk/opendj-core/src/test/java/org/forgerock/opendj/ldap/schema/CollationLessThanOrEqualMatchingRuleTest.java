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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.FakeIndexQueryFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
@Test
public class CollationLessThanOrEqualMatchingRuleTest extends MatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] { };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            // \u00E9 = LATIN SMALL LETTER E WITH ACUTE
            // \u00C9 = LATIN CAPITAL LETTER E WITH ACUTE
            { "abc", "abd", ConditionResult.TRUE },
            { "abc", "acc", ConditionResult.TRUE },
            { "abc", "bbc", ConditionResult.TRUE },
            { "abc", "abD", ConditionResult.TRUE },
            { "def", "d\u00E9g", ConditionResult.TRUE },
            { "def", "dEg", ConditionResult.TRUE },
            { "dEf", "deg", ConditionResult.TRUE },
            { "d\u00E9f", "dEg", ConditionResult.TRUE },
            { "passe", "passe", ConditionResult.TRUE },
            { "passe ", "passe", ConditionResult.TRUE },
            { "passE", "passe", ConditionResult.TRUE },
            { "pass\u00E9", "passe", ConditionResult.TRUE },
            { "pass\u00E9", "passE", ConditionResult.TRUE },
            { "pass\u00E9", "pass\u00C9", ConditionResult.TRUE },
            { "passe", "pass\u00E9", ConditionResult.TRUE },
            { "passE", "pass\u00E9", ConditionResult.TRUE },
            { "pass\u00C9", "pass\u00E9", ConditionResult.TRUE },
            { "abd", "abc", ConditionResult.FALSE },
            { "acc", "abc", ConditionResult.FALSE },
            { "bbc", "abc", ConditionResult.FALSE },
            { "abD", "abc", ConditionResult.FALSE },
            { "d\u00E9g", "def", ConditionResult.FALSE },
            { "dEg", "def", ConditionResult.FALSE },
            { "deg", "dEf", ConditionResult.FALSE },
            { "dEg", "d\u00E9f", ConditionResult.FALSE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule("fr.lte");
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        ByteString value = ByteString.valueOfUtf8("abc");
        MatchingRule matchingRule = getRule();
        Assertion assertion = matchingRule.getAssertion(value);

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));

        ByteString normalizedValue = matchingRule.normalizeAttributeValue(value);
        assertEquals(indexQuery, "rangeMatch(fr.shared, '' < value <= '" + normalizedValue.toHexString() + "')");
    }
}
