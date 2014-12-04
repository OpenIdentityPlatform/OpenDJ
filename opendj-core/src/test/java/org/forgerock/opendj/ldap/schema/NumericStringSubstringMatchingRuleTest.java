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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_NUMERIC_STRING_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the NumericStringSubstringMatchingRule.
 */
@SuppressWarnings("javadoc")
@Test
public class NumericStringSubstringMatchingRuleTest extends SubstringMatchingRuleTest {

    @Override
    @DataProvider(name = "substringInvalidAssertionValues")
    public Object[][] createMatchingRuleInvalidAssertionValues() {
        return new Object[][] {
        };
    }

    @Override
    @DataProvider(name = "substringInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "substringFinalMatchData")
    public Object[][] createSubstringFinalMatchData() {
        return new Object[][] {
            {"123456789",  "123456789", ConditionResult.TRUE },
            {"12 345 6789",  "123456789", ConditionResult.TRUE },
            {"123456789",  "456789", ConditionResult.TRUE },
            {"123456789",  "567", ConditionResult.FALSE },
            {"123456789",  "123", ConditionResult.FALSE },
            {"123456789",  " ", ConditionResult.TRUE },
            {"123456789",  "0789", ConditionResult.FALSE },
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "substringInitialMatchData")
    public Object[][] createSubstringInitialMatchData() {
        return new Object[][] {
            { "123456789",  "12345678",   ConditionResult.TRUE },
            { "123456789",  "2345678",    ConditionResult.FALSE },
            { "123456789",  "1234",       ConditionResult.TRUE },
            { "123456789",  "1",          ConditionResult.TRUE },
            { "123456789",  "678",        ConditionResult.FALSE },
            { "123456789",  "2",          ConditionResult.FALSE },
            { "123456789",  " ",          ConditionResult.TRUE },
            { "123456789",  "123456789",  ConditionResult.TRUE },
            { "123456789",  "1234567890", ConditionResult.FALSE },
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "substringMiddleMatchData")
    public Object[][] createSubstringMiddleMatchData() {
        return new Object[][] {
            // The matching rule requires ordered non overlapping substrings.
            // Issue #730 was not valid.
            { "123456789", new String[] {"123", "234", "567", "789"}, ConditionResult.FALSE },
            { "123456789", new String[] {"123", "234"}, ConditionResult.FALSE },
            { "123456789", new String[] {"567", "234"}, ConditionResult.FALSE },
            { "123456789", new String[] {"123", "456"}, ConditionResult.TRUE },
            { "123456789", new String[] {"123"}, ConditionResult.TRUE },
            { "123456789", new String[] {"456"}, ConditionResult.TRUE },
            { "123456789", new String[] {"789"}, ConditionResult.TRUE },
            { "123456789", new String[] {"123456789"}, ConditionResult.TRUE },
            { "123456789", new String[] {"1234567890"}, ConditionResult.FALSE },
            { "123456789", new String[] {"9"}, ConditionResult.TRUE },
            { "123456789", new String[] {"1"}, ConditionResult.TRUE },
            { "123456789", new String[] {"0"}, ConditionResult.FALSE },
            { "123456789", new String[] {"    "}, ConditionResult.TRUE },
            { "123456789", new String[] {"0123"}, ConditionResult.FALSE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(SMR_NUMERIC_STRING_OID);
    }
}
