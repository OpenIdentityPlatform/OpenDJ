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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_EXACT_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseExactSubstringMatchingRule class.
 */
@SuppressWarnings("javadoc")
public class CaseExactSubstringMatchingRuleTest extends SubstringMatchingRuleTest {

    @Override
    @DataProvider(name = "substringInvalidAssertionValues")
    public Object[][] createMatchingRuleInvalidAssertionValues() {
        return new Object[][] {};
    }

    @Override
    @DataProvider(name = "substringInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {};
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
            { "this is a value", " ", ConditionResult.FALSE },
            { "this is a value", "VALUE", ConditionResult.FALSE },
            { "this is a VALUE", "value", ConditionResult.FALSE },
            { "end with space    ", " ", ConditionResult.FALSE },
            { "end with space    ", "space", ConditionResult.TRUE }, };
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
            { "this is a value", "value", ConditionResult.FALSE },
            { "this is a value", " ", ConditionResult.FALSE },
            { "this is a value", "NOT", ConditionResult.FALSE },
            { "this is a value", "THIS", ConditionResult.FALSE }, };
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
            { "this is a value", strings(" "), ConditionResult.TRUE },
            { "this is a value", strings("this", "is", "a", "value"), ConditionResult.TRUE },
            // The matching rule requires ordered non overlapping substrings.
            // Issue #730 was not valid.
            { "this is a value", strings("value", "this"), ConditionResult.FALSE },
            { "this is a value", strings("this", "this is"), ConditionResult.FALSE },
            { "this is a value", strings("his is", "a val"), ConditionResult.TRUE },
            { "this is a value", strings("not"), ConditionResult.FALSE },
            { "this is a value", strings("THIS"), ConditionResult.FALSE },
            { "this is a value", strings("this", "not"), ConditionResult.FALSE },
            { "this is a value", strings("    "), ConditionResult.TRUE }, };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(SMR_CASE_EXACT_OID);
    }

}
