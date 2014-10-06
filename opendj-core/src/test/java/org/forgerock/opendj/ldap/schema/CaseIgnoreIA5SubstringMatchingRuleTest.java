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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_IA5_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseIgnoreIA5SubstringMatchingRule.
 */
@SuppressWarnings("javadoc")
public class CaseIgnoreIA5SubstringMatchingRuleTest extends SubstringMatchingRuleTest {

    @DataProvider(name = "substringInvalidAssertionValues")
    public Object[][] createMatchingRuleInvalidAssertionValues() {
        return new Object[][] { { "12345678\uFFFD", new String[0], null },
            { null, strings("12345678\uFFFD"), null },
            { null, strings(), "12345678\uFFFD" }, };
    }

    @DataProvider(name = "substringInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] { { "12345678\uFFFD" }, };
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
            { "this is a value", "valu", ConditionResult.FALSE },
            { "this is a value", "THIS", ConditionResult.FALSE },
            { "this is a value", " ", ConditionResult.FALSE },
            { "this is a VALUE", "value", ConditionResult.TRUE },
            { "end with space    ", " ", ConditionResult.FALSE },
            { "end with space    ", "space", ConditionResult.TRUE },
            { "end with space    ", "SPACE", ConditionResult.TRUE }, };
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
            { "this is a value", " ", ConditionResult.FALSE },
            { "this is a value", "NOT", ConditionResult.FALSE },
            { "this is a value", "THIS", ConditionResult.TRUE }, };
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
            { "this is a value", strings("    "), ConditionResult.TRUE }, };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(SMR_CASE_IGNORE_IA5_OID);
    }
}
