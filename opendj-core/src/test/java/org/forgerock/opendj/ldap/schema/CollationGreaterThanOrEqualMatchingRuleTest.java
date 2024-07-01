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
 * Copyright 2014-2016 ForgeRock AS.
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
public class CollationGreaterThanOrEqualMatchingRuleTest extends MatchingRuleTest {
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] { };
    }

    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            // \u00E9 = LATIN SMALL LETTER E WITH ACUTE
            // \u00C9 = LATIN CAPITAL LETTER E WITH ACUTE
            { "abc", "abd", ConditionResult.FALSE },
            { "abc", "acc", ConditionResult.FALSE },
            { "abc", "bbc", ConditionResult.FALSE },
            { "abc", "abD", ConditionResult.FALSE },
            { "def", "d\u00E9g", ConditionResult.FALSE },
            { "def", "dEg", ConditionResult.FALSE },
            { "dEf", "deg", ConditionResult.FALSE },
            { "d\u00E9f", "dEg", ConditionResult.FALSE },
            { "passe", "passe", ConditionResult.TRUE },
            { "passe ", "passe", ConditionResult.TRUE },
            { "passE", "passe", ConditionResult.TRUE },
            { "pass\u00E9", "passe", ConditionResult.TRUE },
            { "pass\u00E9", "passE", ConditionResult.TRUE },
            { "pass\u00E9", "pass\u00C9", ConditionResult.TRUE },
            { "passe", "pass\u00E9", ConditionResult.TRUE },
            { "passE", "pass\u00E9", ConditionResult.TRUE },
            { "pass\u00C9", "pass\u00E9", ConditionResult.TRUE },
            { "abd", "abc", ConditionResult.TRUE },
            { "acc", "abc", ConditionResult.TRUE },
            { "bbc", "abc", ConditionResult.TRUE },
            { "abD", "abc", ConditionResult.TRUE },
            { "d\u00E9g", "def", ConditionResult.TRUE },
            { "dEg", "def", ConditionResult.TRUE },
            { "deg", "dEf", ConditionResult.TRUE },
            { "dEg", "d\u00E9f", ConditionResult.TRUE },
        };
    }

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule("fr.gte");
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        ByteString value = ByteString.valueOfUtf8("abc");
        MatchingRule matchingRule = getRule();
        Assertion assertion = matchingRule.getAssertion(value);

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));

        ByteString normalizedValue = matchingRule.normalizeAttributeValue(value);
        assertEquals(indexQuery, "rangeMatch(fr.shared, '" + normalizedValue.toHexString() + "' <= value < '')");
    }
}
