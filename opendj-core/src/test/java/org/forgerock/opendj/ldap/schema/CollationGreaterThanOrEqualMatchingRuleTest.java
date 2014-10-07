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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Locale;

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

    /**
     * {@inheritDoc}
     */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] { };
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected MatchingRule getRule() {
        // Note that oid and names are not used by the test (ie, they could be any value, test should pass anyway)
        // Only the implementation class and the provided locale are really tested here.
        String oid = "1.3.6.1.4.1.42.2.27.9.4.76.1.4";
        Schema schema = new SchemaBuilder(Schema.getCoreSchema()).
            buildMatchingRule(oid).
                syntaxOID(SchemaConstants.SYNTAX_DIRECTORY_STRING_OID).
                names("fr.gt2").
                implementation(CollationMatchingRulesImpl.greaterThanOrEqualToMatchingRule(new Locale("fr"))).
                addToSchema().
            toSchema();
        return schema.getMatchingRule(oid);
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        ByteString value = ByteString.valueOf("abc");
        MatchingRule matchingRule = getRule();
        Assertion assertion = matchingRule.getAssertion(value);

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));

        ByteString normalizedValue = matchingRule.normalizeAttributeValue(value);
        assertEquals(indexQuery, "rangeMatch(fr.shared, '" + normalizedValue.toHexString() + "' <= value < '')");
    }
}
