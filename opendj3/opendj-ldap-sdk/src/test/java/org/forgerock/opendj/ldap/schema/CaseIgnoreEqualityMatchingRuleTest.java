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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseIgnoreEqualityMatchingRule.
 */
public class CaseIgnoreEqualityMatchingRuleTest extends MatchingRuleTest {
    /**
     * {@inheritDoc}
     */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            { " string ", "string", ConditionResult.TRUE },
            { "string ", "string", ConditionResult.TRUE },
            { " string", "string", ConditionResult.TRUE },
            { "    ", " ", ConditionResult.TRUE },
            { "Z", "z", ConditionResult.TRUE },
            { "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890", "abcdefghijklmnopqrstuvwxyz1234567890",
                ConditionResult.TRUE },
            { "foo\u0020bar\u0020\u0020", "foo bar", ConditionResult.TRUE },
            { "test\u00AD\u200D", "test", ConditionResult.TRUE },
            { "foo\u070Fbar", "foobar", ConditionResult.TRUE },
            // Case-folding data below.
            { "foo\u0149bar", "foo\u02BC\u006Ebar", ConditionResult.TRUE },
            { "foo\u017Bbar", "foo\u017Cbar", ConditionResult.TRUE },

        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_CASE_IGNORE_OID);
    }
}
