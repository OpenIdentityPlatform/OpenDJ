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

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
@Test
public class CollationEqualityMatchingRuleTest extends MatchingRuleTest {

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
            { "passe", "passe", ConditionResult.TRUE },
            { "passe ", "passe", ConditionResult.TRUE },
            { "passE", "passe", ConditionResult.TRUE },
            { "pass\u00E9", "passe", ConditionResult.TRUE },
            { "pass\u00E9", "passE", ConditionResult.TRUE },
            { "pass\u00E9", "pass\u00C9", ConditionResult.TRUE },
            { "passe", "pass\u00E9", ConditionResult.TRUE },
            { "passE", "pass\u00E9", ConditionResult.TRUE },
            { "pass\u00C9", "pass\u00E9", ConditionResult.TRUE },
            { "abc", "abd", ConditionResult.FALSE },
            { "abc", "acc", ConditionResult.FALSE },
            { "abc", "bbc", ConditionResult.FALSE },
            { "abc", "abD", ConditionResult.FALSE },
            { "def", "d\u00E9g", ConditionResult.FALSE },
            { "def", "dEg", ConditionResult.FALSE },
            { "dEf", "deg", ConditionResult.FALSE },
            { "d\u00E9f", "dEg", ConditionResult.FALSE },
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
        return Schema.getCoreSchema().getMatchingRule("fr.eq");
    }

    @Test
    public void testMatchingRuleNames() throws Exception {
        // 'fr' and 'fr-FR' share the same oid (they are aliases)
        MatchingRule rule = Schema.getCoreSchema().getMatchingRule("fr.eq");
        assertThat(rule.getNames()).containsOnly("fr.eq", "fr.3", "fr-FR.eq", "fr-FR.3");

        MatchingRule rule2 = Schema.getCoreSchema().getMatchingRule("fr-FR.eq");
        assertThat(rule2.getNames()).containsOnly("fr.eq", "fr.3", "fr-FR.eq", "fr-FR.3");

        // 'ar' does not share oid with another locale
        MatchingRule rule3 = Schema.getCoreSchema().getMatchingRule("ar.3");
        assertThat(rule3.getNames()).containsOnly("ar.eq", "ar.3");

        // equality matching rule is also the default matching rule
        MatchingRule rule4 = Schema.getCoreSchema().getMatchingRule("ar");
        assertThat(rule4.getNames()).containsOnly("ar");
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        ByteString value = ByteString.valueOfUtf8("abc");
        MatchingRule matchingRule = getRule();
        Assertion assertion = matchingRule.getAssertion(value);

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));

        ByteString normalizedValue = matchingRule.normalizeAttributeValue(value);
        assertEquals(indexQuery, "exactMatch(fr.shared, value=='" + normalizedValue.toHexString() + "')");
    }

}
