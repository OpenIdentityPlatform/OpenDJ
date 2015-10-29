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
public class CollationSubstringMatchingRuleTest extends SubstringMatchingRuleTest {

    @DataProvider(name = "substringInvalidAssertionValues")
    public Object[][] createMatchingRuleInvalidAssertionValues() {
        return new Object[][] { };
    }

    @DataProvider(name = "substringInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] { };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "substringFinalMatchData")
    public Object[][] createSubstringFinalMatchData() {
        return new Object[][] {
            { "this is a value", "value", ConditionResult.TRUE },
            { "this is a value", "alue", ConditionResult.TRUE },
            { "this is a value", "ue", ConditionResult.TRUE },
            { "this is a value", "e", ConditionResult.TRUE },
            { "this is a value", "valu", ConditionResult.FALSE },
            { "this is a value", "this", ConditionResult.FALSE },
            { "this is a value", "VALUE", ConditionResult.TRUE },
            { "this is a value", "AlUe", ConditionResult.TRUE },
            { "this is a value", "UE", ConditionResult.TRUE },
            { "this is a value", "E", ConditionResult.TRUE },
            { "this is a value", "THIS", ConditionResult.FALSE },
            { "this is a value", " ", ConditionResult.TRUE },
            { "this is a VALUE", "value", ConditionResult.TRUE },
            { "end with space    ", " ", ConditionResult.TRUE },
            { "end with space    ", "space", ConditionResult.TRUE },
            { "end with space    ", "SPACE", ConditionResult.TRUE },
            // \u00E9 = LATIN SMALL LETTER E WITH ACUTE
            // \u00C9 = LATIN CAPITAL LETTER E WITH ACUTE
            { "il est passe", "passE", ConditionResult.TRUE },
            { "il est passe", "pass\u00E9", ConditionResult.TRUE },
            { "il est passe", "pass\u00C9", ConditionResult.TRUE },
            { "il est pass\u00C9", "passe", ConditionResult.TRUE },
            { "il est pass\u00E9", "passE", ConditionResult.TRUE },
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "substringInitialMatchData")
    public Object[][] createSubstringInitialMatchData() {
        return new Object[][] {
            { "this is a value", "this", ConditionResult.TRUE },
            { "this is a value", "th", ConditionResult.TRUE },
            { "this is a value", "t", ConditionResult.TRUE },
            { "this is a value", "is", ConditionResult.FALSE },
            { "this is a value", "a", ConditionResult.FALSE },
            { "this is a value", "TH", ConditionResult.TRUE },
            { "this is a value", "T", ConditionResult.TRUE },
            { "this is a value", "IS", ConditionResult.FALSE },
            { "this is a value", "A", ConditionResult.FALSE },
            { "this is a value", "VALUE", ConditionResult.FALSE },
            { "this is a value", " ", ConditionResult.TRUE },
            { "this is a value", "NOT", ConditionResult.FALSE },
            { "this is a value", "THIS", ConditionResult.TRUE },
            // \u00E9 = LATIN SMALL LETTER E WITH ACUTE
            // \u00C9 = LATIN CAPITAL LETTER E WITH ACUTE
            { "il etait passe", "Il \u00E9", ConditionResult.TRUE },
            { "il etait passe", "Il \u00C9", ConditionResult.TRUE },
            { "il etait passe", "Il E", ConditionResult.TRUE },
            { "il \u00E9tait passe", "IL e", ConditionResult.TRUE },
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "substringMiddleMatchData")
    public Object[][] createSubstringMiddleMatchData() {
        return new Object[][] {
            { "this is a value", strings("this"), ConditionResult.TRUE },
            { "this is a value", strings("is"), ConditionResult.TRUE },
            { "this is a value", strings("a"), ConditionResult.TRUE },
            { "this is a value", strings("value"), ConditionResult.TRUE },
            { "this is a value", strings("THIS"), ConditionResult.TRUE },
            { "this is a value", strings("IS"), ConditionResult.TRUE },
            { "this is a value", strings("A"), ConditionResult.TRUE },
            { "this is a value", strings("VALUE"), ConditionResult.TRUE },
            { "this is a value", strings(" "), ConditionResult.TRUE },
            { "this is a value", strings("this", "is", "a", "value"), ConditionResult.TRUE },
            // The matching rule requires ordered non overlapping substrings.
            // Issue #730 was not valid.
            { "this is a value", strings("value", "this"), ConditionResult.FALSE },
            { "this is a value", strings("this", "this is"), ConditionResult.FALSE },
            { "this is a value", strings("this", "IS", "a", "VALue"), ConditionResult.TRUE },
            { "this is a value", strings("his IS", "A val"), ConditionResult.TRUE },
            { "this is a value", strings("not"), ConditionResult.FALSE },
            { "this is a value", strings("this", "not"), ConditionResult.FALSE },
            { "this is a value", strings("    "), ConditionResult.TRUE },
            // \u00E9 = LATIN SMALL LETTER E WITH ACUTE
            // \u00C9 = LATIN CAPITAL LETTER E WITH ACUTE
            { "il est passe par la", strings("est", "pass\u00E9"), ConditionResult.TRUE },
            { "il est passe par la", strings("pass\u00E9", "Par"), ConditionResult.TRUE },
            { "il est passe par la", strings("est", "pass\u00C9", "PAR", "La"), ConditionResult.TRUE },
            { "il est pass\u00E9 par la", strings("il", "Est", "pass\u00C9"), ConditionResult.TRUE },
            { "il est pass\u00C9 par la", strings("est", "passe"), ConditionResult.TRUE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule("fr.sub");
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        ByteString value = ByteString.valueOfUtf8("a*c");
        MatchingRule matchingRule = getRule();
        Assertion assertion = matchingRule.getAssertion(value);

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));

        ByteString binit = matchingRule.normalizeAttributeValue(ByteString.valueOfUtf8("a"));
        ByteString bfinal = matchingRule.normalizeAttributeValue(ByteString.valueOfUtf8("c"));
        assertEquals(indexQuery,
            "intersect["
            + "rangeMatch(fr.shared, '" + binit.toHexString() + "' <= value < '00 54'), "
            + "rangeMatch(fr.substring, '" + bfinal.toHexString() + "' <= value < '00 56'), "
            + "rangeMatch(fr.substring, '" + binit.toHexString() + "' <= value < '00 54')]"
        );
    }
}
